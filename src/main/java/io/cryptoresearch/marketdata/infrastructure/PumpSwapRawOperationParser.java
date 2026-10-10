package io.cryptoresearch.marketdata.infrastructure;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.cryptoresearch.marketdata.api.PumpSwapOperations.*;

/** Pure, bounded parser of exact recorded result text. Schema pins are documentary, not deployment proof. */
@Component
public class PumpSwapRawOperationParser {
    public static final String IDL_HASH="sha256:cd5c58a5dded23632aedb993b08cc0d2ba91bda2bb084560913464274d3bee50";
    public static final String OCTOBER_IDL_HASH="sha256:b7d8c57a4d9c4dd0109a9ab893052352333252d4eedced10ac091d4d66cab89b";
    public static final String SDK_HASH="sha256:1ef56832347f06653acf154d8e2072f1bfc9343f0e6c203cf4517bb1948e8865";
    public static final String TOKEN2022_HASH="sha256:672b3890cb58e5700a67f202491069ed44dffcbfa0e7d0b83a0cfbbb05b3a287";
    private static final String PUMP="pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA", TOKEN="TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
        TOKEN2022="TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb", SYSTEM="11111111111111111111111111111111",
        ATA="ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL", AUTH="GS4CU59F31iL7aR2Q8zVS8DRrcRnXX1yjQ66TqNVQnaR",
        WSOL="So11111111111111111111111111111111111111112", ALPHABET="123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger U64=BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private final JsonMapper json=JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private record Instruction(String program,List<String> accounts,List<Integer> path,byte[] data) { }
    private record TokenState(String mint,String owner,String program,int decimals,BigInteger amount) { }
    private record Transfer(Instruction instruction,String source,String destination,String authority,String mint,int decimals,BigInteger amount) { }
    private record Event(Instruction instruction,BigInteger base,BigInteger quote,BigInteger poolQuote,Map<String,BigInteger> fees,Map<String,BigInteger> facts) { }
    public record Parsed(TransactionResult transaction,List<Operation> operations) { }
    private static class Unsupported extends RuntimeException { Unsupported(String reason){super(reason);} }
    private static void require(boolean condition,String reason){if(!condition)throw new Unsupported(reason);}
    private static void limit(boolean condition){if(!condition)throw new IllegalArgumentException("DECODER_LIMIT");}

    public Parsed parse(RecordedTransaction source) {
        JsonNode root;
        try {root=json.readTree(source.rawResult());}catch(RuntimeException e){return unsupported(source,"unknown","INVALID_JSON",Status.INVALID);}
        requireIdentity(root,source);
        String version=root.path("version").isTextual()?root.path("version").asText():root.path("version").isIntegralNumber()?root.path("version").asString():"unknown";
        if(!List.of("legacy","0","1").contains(version))return unsupported(source,version,"UNSUPPORTED_VERSION",Status.UNSUPPORTED);
        var meta=root.path("meta");
        if(!meta.isObject()||!meta.has("err"))return unsupported(source,version,"MISSING_ERROR",Status.INVALID);
        if(!validError(meta.path("err")))return unsupported(source,version,"MALFORMED_ERROR",Status.INVALID);
        var fee=optionalU64(meta.path("fee"));
        if(!meta.path("err").isNull())return new Parsed(new TransactionResult(source,version,Status.FAILED,"FAILED_TRANSACTION",fee,List.of()),List.of());
        if(!"finalized".equals(source.finality())||!"CAPTURED".equals(source.captureDisposition()))return unsupported(source,version,"UNPROVED_FINALIZED_CAPTURE",Status.UNSUPPORTED);
        try {
            var keys=keys(root); var instructions=instructions(root,keys);
            var pre=states(meta.path("preTokenBalances"),keys); var post=states(meta.path("postTokenBalances"),keys);
            var swaps=instructions.stream().filter(i->i.program.equals(PUMP)&&!hex(i.data,0,8).equals("e445a52e51cb9a1d")).toList();
            var invocations=new ArrayList<Invocation>();var operations=new ArrayList<Operation>();
            for(var swap:swaps) {
                String variant=variant(swap);var position=position(swap.path);
                try {if(ancillary(variant)){nonOperation(swap,variant,instructions,swaps,pre,post);invocations.add(new Invocation(position,Status.NON_OPERATION,"PROVEN_ANCILLARY",variant));}
                    else {operations.add(operation(source,swap,variant,instructions,swaps,pre,post));invocations.add(new Invocation(position,Status.DECODED,"CONFIRMED_PRINCIPAL",variant));}}
                catch(Unsupported e){invocations.add(new Invocation(position,Status.UNSUPPORTED,e.getMessage(),variant));}
            }
            for(var frame:instructions)if(frame.program.equals(PUMP)&&hex(frame.data,0,8).equals("e445a52e51cb9a1d")&&swaps.stream().noneMatch(s->s.path.equals(parent(frame.path))))
                invocations.add(new Invocation(position(frame.path),Status.UNSUPPORTED,"ORPHAN_EVENT","event_transport"));
            invocations.sort(Comparator.comparingInt((Invocation i)->i.position().outerIndex()).thenComparing(i->i.position().innerPath(),PumpSwapRawOperationParser::comparePaths));
            boolean resolved=invocations.stream().allMatch(i->i.status()==Status.DECODED||i.status()==Status.NON_OPERATION);
            Status status=invocations.isEmpty()?Status.NO_PUMPSWAP:resolved?(operations.isEmpty()?Status.NON_OPERATION:Status.DECODED):operations.isEmpty()?Status.UNSUPPORTED:Status.PARTIAL;
            String reason=invocations.isEmpty()?"NO_PUMPSWAP":invocations.stream().filter(i->i.status()!=Status.DECODED&&i.status()!=Status.NON_OPERATION).map(Invocation::reason).distinct().sorted().reduce((a,b)->a+","+b).orElse(operations.isEmpty()?"PROVEN_ANCILLARY":"CONFIRMED_PRINCIPAL");
            return new Parsed(new TransactionResult(source,version,status,reason,fee,invocations),operations);
        }catch(Unsupported e){return unsupported(source,version,e.getMessage(),Status.UNSUPPORTED);}
    }
    private Parsed unsupported(RecordedTransaction s,String v,String reason,Status status){return new Parsed(new TransactionResult(s,v,status,reason,Optional.empty(),List.of()),List.of());}
    private boolean validError(JsonNode err){if(err.isNull())return true;
        if(err.isTextual())return List.of("AccountInUse","AccountNotFound","InsufficientFundsForFee","BlockhashNotFound","AlreadyProcessed","InvalidAccountForFee","SanitizeFailure","InvalidProgramForExecution","ProgramAccountNotFound","SignatureFailure").contains(err.asText());
        if(!err.isObject()||err.size()!=1)return false;var tuple=err.path("InstructionError");
        if(!tuple.isArray()||tuple.size()!=2||!tuple.get(0).isIntegralNumber()||!tuple.get(0).canConvertToInt()||tuple.get(0).intValue()<0)return false;
        var code=tuple.get(1);return code.isTextual()&&!code.asText().isBlank()||code.isObject()&&code.size()==1&&code.path("Custom").isIntegralNumber()&&code.path("Custom").bigIntegerValue().signum()>=0&&code.path("Custom").bigIntegerValue().bitLength()<=32;}
    private void requireIdentity(JsonNode root,RecordedTransaction s) {
        if(!root.isObject()||!root.path("slot").isIntegralNumber()||root.path("slot").longValue()!=s.slot()
                ||!root.path("transaction").path("signatures").isArray()||root.path("transaction").path("signatures").isEmpty()
                ||!s.signature().equals(root.path("transaction").path("signatures").get(0).asText()))throw new IllegalArgumentException("RAW_IDENTITY_MISMATCH");
    }
    private List<String> keys(JsonNode root) {
        var msg=root.path("transaction").path("message");var staticKeys=msg.path("accountKeys");require(staticKeys.isArray(),"MISSING_ACCOUNT_KEYS");
        limit(staticKeys.size()<=2048);var keys=new ArrayList<String>();boolean parsed=false;
        for(var node:staticKeys){parsed|=node.isObject();var key=node.isTextual()?node.asText():node.path("pubkey").asText();address(key);keys.add(key);}
        var loaded=root.path("meta").path("loadedAddresses");
        if(loaded.isObject()) {require(!parsed,"AMBIGUOUS_LOADED_KEYS");for(String side:List.of("writable","readonly")){var rows=loaded.path(side);require(rows.isArray(),"MISSING_LOADED_KEYS");limit(keys.size()+rows.size()<=2048);for(var n:rows){address(n.asText());keys.add(n.asText());}}}
        else if(msg.path("addressTableLookups").isArray()&&!msg.path("addressTableLookups").isEmpty()&&!parsed)throw new Unsupported("MISSING_LOADED_KEYS");
        return keys;
    }
    private List<Instruction> instructions(JsonNode root,List<String> keys) {
        var rows=root.path("transaction").path("message").path("instructions");require(rows.isArray(),"MISSING_INSTRUCTIONS");limit(rows.size()<=10000);
        var result=new ArrayList<Instruction>();for(int n=0;n<rows.size();n++)result.add(instruction(rows.get(n),List.of(n),keys));
        var groups=root.path("meta").path("innerInstructions");require(groups.isArray(),"MISSING_INNER_INSTRUCTIONS");var seen=new HashSet<Integer>();
        for(var group:groups){int outer=index(group.path("index"),rows.size());require(seen.add(outer),"DUPLICATE_INNER_GROUP");var inner=group.path("instructions");require(inner.isArray(),"MISSING_INSTRUCTIONS");limit(result.size()+inner.size()<=10000);
            var stack=new ArrayList<List<Integer>>();stack.add(List.of(outer));var children=new HashMap<List<Integer>,Integer>();
            for(var row:inner){var height=row.path("stackHeight");require(height.isIntegralNumber(),"PATH_UNKNOWN");int h=height.intValue();limit(h<=64);require(h>=2&&h<=stack.size()+1,"PATH_UNKNOWN");
                while(stack.size()>h-1)stack.removeLast();var parent=stack.getLast();var path=new ArrayList<>(parent);int child=children.getOrDefault(parent,0);children.put(parent,child+1);path.add(child);stack.add(path);result.add(instruction(row,path,keys));}
        }
        result.sort((a,b)->comparePaths(a.path,b.path));return result;
    }
    private Instruction instruction(JsonNode row,List<Integer> path,List<String> keys) {
        String program=row.has("programIdIndex")?keys.get(index(row.path("programIdIndex"),keys.size())):row.path("programId").asText();address(program);
        var accounts=row.path("accounts");require(accounts.isArray(),"UNSUPPORTED_PARSED_INSTRUCTION");limit(accounts.size()<=2048);var resolved=new ArrayList<String>();
        for(var n:accounts){String key=n.isIntegralNumber()?keys.get(index(n,keys.size())):n.asText();address(key);resolved.add(key);}
        require(row.path("data").isTextual(),"UNSUPPORTED_PARSED_INSTRUCTION");return new Instruction(program,resolved,List.copyOf(path),decode(row.path("data").asText(),4096));
    }
    private Map<String,TokenState> states(JsonNode rows,List<String> keys){require(rows.isArray(),"MISSING_TOKEN_BALANCES");limit(rows.size()<=10000);var result=new HashMap<String,TokenState>();
        for(var row:rows){var key=keys.get(index(row.path("accountIndex"),keys.size()));var amount=row.path("uiTokenAmount");require(amount.path("decimals").isIntegralNumber(),"TOKEN_BALANCE_INVALID");int decimals=amount.path("decimals").intValue();require(decimals>=0&&decimals<=255,"TOKEN_BALANCE_INVALID");
            String mint=row.path("mint").asText(),owner=row.path("owner").asText(),program=row.path("programId").asText();address(mint);address(owner);if(!program.isEmpty())address(program);
            require(result.putIfAbsent(key,new TokenState(mint,owner,program,decimals,u64(amount.path("amount"))))==null,"TOKEN_BALANCE_CONFLICT");}return result;}
    private Operation operation(RecordedTransaction source,Instruction swap,String variant,List<Instruction> all,List<Instruction> swaps,Map<String,TokenState> pre,Map<String,TokenState> post) {
        var a=swap.accounts;boolean sell=variant.startsWith("sell"),v2=variant.endsWith("_v2");require(!variant.equals("unknown")&&!variant.equals("multi_hop_swap"),"UNSUPPORTED_VARIANT");
        require(swap.data.length==(sell||v2?24:25)&&a.size()>=(v2?17:sell?21:23)&& (sell||v2||swap.data[24]==0||swap.data[24]==1),"INVOCATION_LAYOUT");
        require((v2?a.size()==17&&token(a.get(9))&&token(a.get(10))&&a.get(11).equals(SYSTEM):token(a.get(11))&&token(a.get(12))&&a.get(13).equals(SYSTEM)&&a.get(14).equals(ATA))&&a.get(15).equals(AUTH)&&a.get(16).equals(PUMP)
            &&!a.get(3).equals(a.get(4))&&new HashSet<>(a.subList(5,9)).size()==4,"INVOCATION_LAYOUT");
        var frames=all.stream().filter(i->i.program.equals(PUMP)&&parent(i.path).equals(swap.path)&&hex(i.data,0,8).equals("e445a52e51cb9a1d")).toList();require(!frames.isEmpty(),"MISSING_EVENT");
        Event event=null;
        for(var frame:frames){var candidate=event(frame,swap,variant);if(event!=null)require(Arrays.equals(event.instruction.data,candidate.instruction.data),"EVENT_CONFLICT");else event=candidate;}
        if(!v2)remainingRoles(swap,event,variant,pre,post);
        var transfers=new ArrayList<Transfer>();var lifecycle=new ArrayList<List<Integer>>();
        for(var i:all){if(!ancestor(swap.path,i.path)||!token(i.program))continue;if(!controlling(i,swaps).equals(swap))continue;
            if(i.data.length>0&&(i.data[0]==3||i.data[0]==12)){transfers.add(transfer(i,pre,post,all,lifecycle));}
            else {require(i.data.length>0&&List.of(1,9,16,17,18).contains((int)i.data[0]),"UNSUPPORTED_TOKEN_INSTRUCTION");
                int tag=i.data[0];require(switch(tag){case 1->i.data.length==1&&i.accounts.size()==4;case 9->i.data.length==1&&i.accounts.size()==3;case 16->i.data.length==33&&i.accounts.size()==3;case 17->i.data.length==1&&i.accounts.size()==1;case 18->i.data.length==33&&i.accounts.size()==2;default->false;},"TOKEN_LIFECYCLE_LAYOUT");}}
        var endpoints=new HashMap<String,TokenState>();
        for(int role:List.of(5,6,7,8)){String mint=a.get(role==5||role==7?3:4),owner=a.get(role<7?1:0),program=a.get(role==5||role==7?(v2?9:11):(v2?10:12));
            if(role>=7)require(pre.containsKey(a.get(role))&&post.containsKey(a.get(role)),"VAULT_ENDPOINT_UNPROVED");
            var state=endpoint(a.get(role),mint,owner,program,pre,post,all,transfers,lifecycle);require(state!=null,"TOKEN_ENDPOINT_UNPROVED");
            endpoints.put(a.get(role),state);}
        var baseProgram=a.get(v2?9:11);var quoteProgram=a.get(v2?10:12);BigInteger base=event.base,quote=event.quote;
        require(base.signum()>0&&quote.signum()>0,"PRINCIPAL_UNPROVED");
        var baseTransfers=transfers.stream().filter(t->t.instruction.program.equals(baseProgram)&&t.mint.equals(a.get(3))).toList();
        var quoteTransfers=transfers.stream().filter(t->t.instruction.program.equals(quoteProgram)&&t.mint.equals(a.get(4))).toList();
        if(v2)require(event.fees.get("protocol").compareTo(event.fees.get("buyback"))>=0,"FEE_LAYOUT");
        var retained=v2?event.fees.get("protocol").subtract(event.fees.get("buyback")).add(event.fees.get("creator")):BigInteger.ZERO;
        var vaultQuote=sell?event.poolQuote.subtract(retained).negate():event.poolQuote.add(retained);
        require(flow(baseTransfers,a.get(5)).equals(sell?base.negate():base)&&flow(baseTransfers,a.get(7)).equals(sell?base:base.negate())
                &&flow(quoteTransfers,a.get(6)).equals(sell?quote:quote.negate())&&flow(quoteTransfers,a.get(8)).equals(vaultQuote),"PRINCIPAL_UNPROVED");
        for(int role:List.of(5,6,7,8)){var state=endpoints.get(a.get(role));corroborateEndpoint(a.get(role),state.mint,a.get(role==5||role==7?(v2?9:11):(v2?10:12)),state.decimals,pre,post,all,lifecycle);}
        if(v2)require(flow(quoteTransfers,a.get(14)).equals(event.fees.get("buyback"))&&quoteTransfers.stream().filter(t->t.destination.equals(a.get(14))).allMatch(t->t.source.equals(a.get(sell?8:6))),"BUYBACK_FLOW_UNPROVED");
        if(variant.equals("buy_exact_quote_in")&&event.facts.containsKey("curve_quote_input")){
            var buyback=event.fees.get("buyback");require(event.fees.get("protocol").compareTo(buyback)>=0&&flow(quoteTransfers,a.get(10)).equals(event.fees.get("protocol").subtract(buyback))&&flow(quoteTransfers,a.get(17)).equals(event.fees.get("creator")),"EXACT_INPUT_FEE_FLOW_UNPROVED");
            if(buyback.signum()>0)require(a.size()>23&&flow(quoteTransfers,a.getLast()).equals(buyback)&&quoteTransfers.stream().filter(t->t.destination.equals(a.getLast())).allMatch(t->t.source.equals(a.get(6))),"BUYBACK_FLOW_UNPROVED");
        }
        for(var t:transfers){if(t.source.equals(a.get(5))||t.destination.equals(a.get(5))||t.source.equals(a.get(7))||t.destination.equals(a.get(7)))require(t.mint.equals(a.get(3))&&t.instruction.program.equals(baseProgram),"TRANSFER_MISMATCH");
            if(t.source.equals(a.get(6))||t.destination.equals(a.get(6))||t.source.equals(a.get(8))||t.destination.equals(a.get(8)))require(t.mint.equals(a.get(4))&&t.instruction.program.equals(quoteProgram),"TRANSFER_MISMATCH");
            if(t.source.equals(a.get(5))||t.source.equals(a.get(6)))require(t.authority.equals(a.get(1)),"TRANSFER_AUTHORITY_MISMATCH");
            if(t.source.equals(a.get(7))||t.source.equals(a.get(8)))require(t.authority.equals(a.get(0)),"TRANSFER_AUTHORITY_MISMATCH");}
        var attributed=new TreeMap<String,BigInteger>();if(!v2)for(var name:List.of("protocol","creator")){String account=a.get(name.equals("protocol")?10:17);if(quoteTransfers.stream().anyMatch(t->t.destination.equals(account))&&flow(quoteTransfers,account).equals(event.fees.get(name)))attributed.put(name,event.fees.get(name));}
        var reasons=new ArrayList<>(List.of("NETWORK_FEE_TRANSACTION_SCOPED","TIPS_UNKNOWN","CASHBACK_ROUTE_UNKNOWN","BUYBACK_ROUTE_UNKNOWN"));
        if(!attributed.containsKey("protocol"))reasons.add("PROTOCOL_ROUTE_UNKNOWN");if(!attributed.containsKey("creator"))reasons.add("CREATOR_ROUTE_UNKNOWN");
        if(v2){reasons.remove("PROTOCOL_ROUTE_UNKNOWN");reasons.remove("CREATOR_ROUTE_UNKNOWN");reasons.add("PROTOCOL_RETAINED_IN_POOL");reasons.add("CREATOR_RETAINED_IN_POOL");}
        return new Operation(source.chain(),source.signature(),position(swap.path),variant,a.get(1),a.get(0),a.get(3),a.get(4),base,quote,
            Optional.of(baseTransfers.getFirst().decimals),Optional.of(quoteTransfers.getFirst().decimals),event.instruction.path,
            transfers.stream().map(t->t.instruction.path).toList(),lifecycle.stream().distinct().sorted(PumpSwapRawOperationParser::comparePaths).toList(),event.fees,attributed,event.facts,"INCOMPLETE",reasons,source.rawDigest(),sha256(event.instruction.data),source.sourceReference());
    }
    private Event event(Instruction frame,Instruction swap,String variant){var b=frame.data;boolean sell=variant.startsWith("sell"),v2=variant.endsWith("_v2");var a=swap.accounts;
        require(frame.accounts.size()==1&&frame.accounts.getFirst().equals(a.get(15)),"EVENT_ROLE_MISMATCH");
        require(hex(b,8,16).equals(sell?"3e2f370aa503dc2a":"67f4521f2cf57777"),"EVENT_LAYOUT");
        int prefix=sell?400:445+variant.length();require((!v2&&b.length==prefix)||b.length==prefix+49,"EVENT_LAYOUT");
        if(!sell){require((b[368]==0||b[368]==1)&&ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(409)==variant.length()
            &&new String(b,413,variant.length(),StandardCharsets.UTF_8).equals(variant),"EVENT_LAYOUT");}
        int[] roles={0,1,5,6,9,10};for(int n=0;n<roles.length;n++)require(encode(Arrays.copyOfRange(b,128+n*32,160+n*32)).equals(v2&&n>=4?SYSTEM:a.get(roles[n])),"EVENT_ROLE_MISMATCH");
        int tail=sell?368:413+variant.length();var fees=new TreeMap<String,BigInteger>();fees.put("lp",readU64(b,88));fees.put("protocol",readU64(b,104));fees.put("creator",readU64(b,360));fees.put("cashback",readU64(b,tail+8));fees.put("buyback",readU64(b,tail+24));
        // Signed i64 timestamp is decoded without treating it as an amount; original source times remain authoritative lineage.
        var facts=new TreeMap<String,BigInteger>();facts.put("timestamp",BigInteger.valueOf(ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getLong(16)));
        if(b.length==prefix+49){require(b[prefix+16]==0||b[prefix+16]==1,"EVENT_LAYOUT");
            var signed=Arrays.copyOfRange(b,prefix,prefix+16);for(int n=0;n<8;n++){byte temp=signed[n];signed[n]=signed[15-n];signed[15-n]=temp;}
            facts.put("virtual_quote_reserves",new BigInteger(signed));facts.put("can_boost",BigInteger.valueOf(b[prefix+16]));
            String[] names={"base_supply","holder_rewards_bps","holder_rewards","creator_fee_unclaimed"};for(int n=0;n<names.length;n++)facts.put(names[n],readU64(b,prefix+17+n*8));}
        var quote=readU64(b,120);var poolQuote=readU64(b,112);
        if(b.length==prefix+49&&variant.startsWith("buy_exact_quote_in")){
            facts.put("curve_quote_input",quote);var debit=quote.add(fees.get("lp")).add(fees.get("protocol")).add(fees.get("creator"));
            require(fees.get("cashback").signum()==0&&readU64(b,72).equals(debit)&&poolQuote.equals(quote.add(fees.get("lp"))),"EXACT_INPUT_EVENT_UNPROVED");quote=debit;
        }
        return new Event(frame,readU64(b,24),quote,poolQuote,fees,facts);}
    private Transfer transfer(Instruction i,Map<String,TokenState> pre,Map<String,TokenState> post,List<Instruction> all,List<List<Integer>> lifecycle){boolean checked=i.data[0]==12;
        require(i.data.length==(checked?10:9)&&i.accounts.size()==(checked?4:3),"TRANSFER_LAYOUT");var a=i.accounts;String mint;int decimals;
        if(checked){mint=a.get(1);decimals=Byte.toUnsignedInt(i.data[9]);}
        else {var left=pre.get(a.get(0));if(left==null)left=post.get(a.get(0));var right=pre.get(a.get(1));if(right==null)right=post.get(a.get(1));require(left!=null&&right!=null&&left.mint.equals(right.mint)&&left.decimals==right.decimals,"TRANSFER_MINT_UNPROVED");mint=left.mint;decimals=left.decimals;}
        for(String account:List.of(a.get(0),a.get(checked?2:1))){var state=pre.get(account);if(state==null)state=post.get(account);if(state!=null)require(state.mint.equals(mint)&&state.decimals==decimals&&(state.program.isEmpty()||state.program.equals(i.program)),"TRANSFER_MISMATCH");}
        return new Transfer(i,a.get(0),a.get(checked?2:1),a.get(checked?3:2),mint,decimals,readU64(i.data,1));}
    private TokenState endpoint(String account,String mint,String owner,String program,Map<String,TokenState> pre,Map<String,TokenState> post,List<Instruction> all,List<Transfer> transfers,List<List<Integer>> refs){
        var before=pre.get(account);var after=post.get(account);for(var s:new TokenState[]{before,after})if(s!=null)require(s.mint.equals(mint)&&s.owner.equals(owner)&&(s.program.isEmpty()||s.program.equals(program)),"TOKEN_ENDPOINT_MISMATCH");
        if(all.stream().anyMatch(i->i.accounts.contains(account)&&((i.program.equals(TOKEN2022)&&i.data.length>0&&i.data[0]==22)||(i.program.equals(SYSTEM)&&i.accounts.size()==2&&i.accounts.get(1).equals(account)&&i.data.length==52&&readU64(i.data,12).equals(BigInteger.valueOf(170))))))return immutableEndpoint(account,mint,owner,program,before,after,pre,post,all,refs);
        if(before!=null&&after!=null){require(before.decimals==after.decimals,"TOKEN_ENDPOINT_MISMATCH");return before;}
        var uses=all.stream().filter(i->token(i.program)&&i.data.length>0&&(i.data[0]==3||i.data[0]==12)&&i.accounts.contains(account)).toList();require(!uses.isEmpty(),"TOKEN_ENDPOINT_UNPROVED");
        var creates=all.stream().filter(i->i.program.equals(SYSTEM)&&i.accounts.size()==2&&i.accounts.get(1).equals(account)&&i.data.length==52&&ByteBuffer.wrap(i.data).order(ByteOrder.LITTLE_ENDIAN).getInt(0)==0&&encode(Arrays.copyOfRange(i.data,20,52)).equals(program)&&readU64(i.data,12).equals(BigInteger.valueOf(165))).toList();
        var inits=all.stream().filter(i->i.program.equals(program)&&!i.accounts.isEmpty()&&i.accounts.getFirst().equals(account)&&i.data.length>0&&List.of(1,16,18).contains((int)i.data[0])).toList();
        var closes=all.stream().filter(i->i.program.equals(program)&&!i.accounts.isEmpty()&&i.accounts.getFirst().equals(account)&&i.data.length>0&&i.data[0]==9).toList();
        if(before==null){require(creates.size()==1&&inits.size()==1,"TOKEN_ENDPOINT_UNPROVED");var init=inits.getFirst();
            var ata=all.stream().filter(i->i.program.equals(ATA)&&i.data.length==1&&(i.data[0]==0||i.data[0]==1)&&(i.accounts.size()==6||i.accounts.size()==7)
                &&i.accounts.get(1).equals(account)&&i.accounts.get(2).equals(owner)&&i.accounts.get(3).equals(mint)&&i.accounts.get(4).equals(SYSTEM)&&i.accounts.get(5).equals(program)
                &&parent(init.path).equals(i.path)&&parent(creates.getFirst().path).equals(i.path)).toList();require(ata.size()==1,"ATA_CREATION_UNPROVED");
            boolean valid=init.data[0]==1?init.data.length==1&&init.accounts.size()==4&&init.accounts.get(2).equals(owner):init.data.length==33&&init.accounts.size()==(init.data[0]==16?3:2)&&encode(Arrays.copyOfRange(init.data,1,33)).equals(owner);
            require(valid&&init.accounts.get(1).equals(mint)&&comparePaths(creates.getFirst().path,init.path)<0&&comparePaths(init.path,uses.getFirst().path)<0,"LIFECYCLE_UNPROVED");refs.add(creates.getFirst().path);refs.add(init.path);
            if(program.equals(TOKEN)) {
                // Classic InitializeImmutableOwner only checks uninitialized state; it does not install an extension.
                var markers=all.stream().filter(i->i.program.equals(TOKEN)&&i.accounts.contains(account)&&i.data.length>0&&i.data[0]==22).toList();require(markers.size()<=1,"LIFECYCLE_UNPROVED");
                for(var marker:markers){require(Arrays.equals(marker.data,new byte[]{22})&&marker.accounts.equals(List.of(account))&&parent(marker.path).equals(ata.getFirst().path)&&comparePaths(creates.getFirst().path,marker.path)<0&&comparePaths(marker.path,init.path)<0,"LIFECYCLE_UNPROVED");refs.add(marker.path);}
            }
            if(mint.equals(WSOL)){
                // Initialization and token transfers already synchronize native amounts. Only later system funding needs SyncNative.
                var funding=all.stream().filter(i->i.program.equals(SYSTEM)&&i.accounts.contains(account)&&comparePaths(init.path,i.path)<0&&comparePaths(i.path,uses.getFirst().path)<0).toList();
                require(funding.stream().allMatch(i->i.data.length==12&&i.accounts.size()==2&&i.accounts.get(1).equals(account)&&ByteBuffer.wrap(i.data).order(ByteOrder.LITTLE_ENDIAN).getInt(0)==2),"NATIVE_FUNDING_UNPROVED");
                var sync=all.stream().filter(i->i.program.equals(program)&&i.accounts.equals(List.of(account))&&Arrays.equals(i.data,new byte[]{17})&&comparePaths(init.path,i.path)<0&&comparePaths(i.path,uses.getFirst().path)<0).toList();
                if(!funding.isEmpty())require(sync.stream().anyMatch(i->comparePaths(funding.getLast().path,i.path)<0),"SYNC_NATIVE_UNPROVED");
                funding.forEach(i->refs.add(i.path));sync.forEach(i->refs.add(i.path));}}
        if(after==null){require(closes.size()==1,"TOKEN_ENDPOINT_UNPROVED");var close=closes.getFirst();require(close.data.length==1&&close.accounts.size()==3&&close.accounts.get(2).equals(owner)&&comparePaths(uses.getLast().path,close.path)<0,"LIFECYCLE_UNPROVED");refs.add(close.path);}
        if(before!=null)return before;if(after!=null)return after;var checked=transfers.stream().filter(t->(t.source.equals(account)||t.destination.equals(account))&&t.mint.equals(mint)&&t.instruction.program.equals(program)).toList();require(!checked.isEmpty()&&checked.stream().map(Transfer::decimals).distinct().count()==1,"LIFECYCLE_DECIMALS_UNPROVED");return new TokenState(mint,owner,program,checked.getFirst().decimals,BigInteger.ZERO);}
    private Instruction controlling(Instruction instruction,List<Instruction> invocations) {
        return invocations.stream().filter(s->ancestor(s.path,instruction.path)).max(Comparator.comparingInt(s->s.path.size())).orElseThrow(()->new Unsupported("AMBIGUOUS_TRANSFER_ASSOCIATION"));
    }
    /** Complete-transaction conservation corroborates actual endpoints; it never allocates invocation principal. */
    private void corroborateEndpoint(String account,String mint,String program,int decimals,Map<String,TokenState> pre,Map<String,TokenState> post,List<Instruction> all,List<List<Integer>> refs) {
        var global=new ArrayList<Transfer>();
        for(var i:all)if(token(i.program)&&i.accounts.contains(account)) {
            require(i.program.equals(program)&&i.data.length>0,"ENDPOINT_TOUCH_UNPROVED");int tag=Byte.toUnsignedInt(i.data[0]);
            if(tag==3||tag==12) {
                require(all.stream().noneMatch(child->ancestor(i.path,child.path)),"ENDPOINT_HOOK_UNPROVED");
                var t=endpointTransfer(i,account,mint,decimals,pre,post,all,refs);require((t.source.equals(account)||t.destination.equals(account))&&t.mint.equals(mint)&&t.decimals==decimals,"ENDPOINT_TRANSFER_UNPROVED");global.add(t);
            } else require(refs.contains(i.path),"ENDPOINT_LIFECYCLE_UNPROVED");
        }
        require(!global.isEmpty(),"ENDPOINT_TRANSFER_UNPROVED");var before=pre.get(account);var after=post.get(account);
        if(mint.equals(WSOL)&&(before==null||after==null)) {
            // Classic native transfers move the same lamports/raw amount, without Token2022 deductions.
            // Missing native close-state is not zero: CloseAccount may release a residual native amount.
            require(program.equals(TOKEN)&&after==null,"NATIVE_SEMANTICS_UNPROVED");
            if(before!=null)corroborateTransferPrefixes(before.amount,global,account);
        } else {
            var initial=before==null?BigInteger.ZERO:before.amount;var terminal=after==null?BigInteger.ZERO:after.amount;
            require(terminal.subtract(initial).equals(flow(global,account)),"ENDPOINT_BALANCE_UNPROVED");
            corroborateTransferPrefixes(initial,global,account);
        }
        for(var i:all)if(i.program.equals(SYSTEM)&&i.accounts.contains(account))require(refs.contains(i.path),"ENDPOINT_FUNDING_UNPROVED");
    }
    private void corroborateTransferPrefixes(BigInteger initial,List<Transfer> transfers,String account) {
        var running=initial;
        for(var transfer:transfers){if(transfer.source.equals(account))running=running.subtract(transfer.amount);if(transfer.destination.equals(account))running=running.add(transfer.amount);require(running.signum()>=0&&running.compareTo(U64)<=0,"ENDPOINT_BALANCE_UNPROVED");}
    }
    private Transfer endpointTransfer(Instruction i,String account,String mint,int decimals,Map<String,TokenState> pre,Map<String,TokenState> post,List<Instruction> all,List<List<Integer>> refs) {
        if(i.data[0]==12)return transfer(i,pre,post,all,refs);
        require(i.data.length==9&&i.accounts.size()==3,"TRANSFER_LAYOUT");var a=i.accounts;
        require(a.get(0).equals(account)||a.get(1).equals(account),"ENDPOINT_TRANSFER_UNPROVED");
        var candidate=new Transfer(i,a.get(0),a.get(1),a.get(2),mint,decimals,readU64(i.data,1));
        String other=a.get(a.get(0).equals(account)?1:0);var observed=pre.get(other);if(observed==null)observed=post.get(other);
        String owner;
        if(observed!=null)owner=observed.owner;
        else {
            var inits=all.stream().filter(n->n.program.equals(i.program)&&!n.accounts.isEmpty()&&n.accounts.getFirst().equals(other)&&n.data.length>0&&List.of(1,16,18).contains((int)n.data[0])).toList();require(inits.size()==1,"TRANSFER_MINT_UNPROVED");var init=inits.getFirst();
            require(init.accounts.size()>=2&&init.accounts.get(1).equals(mint),"TRANSFER_MINT_UNPROVED");
            if(init.data[0]==1){require(init.data.length==1&&init.accounts.size()==4,"TRANSFER_MINT_UNPROVED");owner=init.accounts.get(2);}
            else {require(init.data.length==33,"TRANSFER_MINT_UNPROVED");owner=eventAddress(init.data,1);}
        }
        var state=endpoint(other,mint,owner,i.program,pre,post,all,List.of(candidate),refs);require(state!=null&&state.decimals==decimals,"TRANSFER_MINT_UNPROVED");
        for(var touch:all)if(touch.accounts.contains(other)) {
            if(token(touch.program))require(touch.program.equals(i.program)&&touch.data.length>0&&((touch.data[0]==3||touch.data[0]==12)||refs.contains(touch.path)),"TRANSFER_COUNTERPARTY_UNPROVED");
            if(touch.program.equals(SYSTEM))require(refs.contains(touch.path),"TRANSFER_COUNTERPARTY_UNPROVED");
        }
        return candidate;
    }
    private TokenState immutableEndpoint(String account,String mint,String owner,String program,TokenState before,TokenState after,Map<String,TokenState> pre,Map<String,TokenState> post,List<Instruction> all,List<List<Integer>> refs){
        require(program.equals(TOKEN2022)&&!mint.equals(WSOL)&&before==null,"IMMUTABLE_ENDPOINT_UNPROVED");
        var creates=all.stream().filter(i->i.program.equals(SYSTEM)&&i.accounts.contains(account)).toList();require(creates.size()==1,"IMMUTABLE_CREATE_UNPROVED");var create=creates.getFirst();
        require(create.accounts.size()==2&&create.accounts.get(1).equals(account)&&create.data.length==52&&ByteBuffer.wrap(create.data).order(ByteOrder.LITTLE_ENDIAN).getInt(0)==0&&readU64(create.data,12).equals(BigInteger.valueOf(170))&&eventAddress(create.data,20).equals(program),"IMMUTABLE_CREATE_UNPROVED");
        var touches=all.stream().filter(i->token(i.program)&&i.accounts.contains(account)).toList();var immutable=touches.stream().filter(i->i.data.length>0&&i.data[0]==22).toList();var inits=touches.stream().filter(i->i.data.length>0&&List.of(1,16,18).contains((int)i.data[0])).toList();var closes=touches.stream().filter(i->i.data.length>0&&i.data[0]==9).toList();
        require(immutable.size()==1&&inits.size()==1&&closes.size()==(after==null?1:0),"IMMUTABLE_LIFECYCLE_UNPROVED");var marker=immutable.getFirst();var init=inits.getFirst();
        require(marker.program.equals(program)&&Arrays.equals(marker.data,new byte[]{22})&&marker.accounts.equals(List.of(account)),"IMMUTABLE_LAYOUT");
        boolean valid=init.data[0]==1?init.data.length==1&&init.accounts.size()==4&&init.accounts.get(2).equals(owner):init.data.length==33&&init.accounts.size()==(init.data[0]==16?3:2)&&eventAddress(init.data,1).equals(owner);
        require(init.program.equals(program)&&valid&&init.accounts.getFirst().equals(account)&&init.accounts.get(1).equals(mint)&&parent(create.path).equals(parent(marker.path))&&parent(create.path).equals(parent(init.path))&&comparePaths(create.path,marker.path)<0&&comparePaths(marker.path,init.path)<0,"IMMUTABLE_LIFECYCLE_UNPROVED");
        var atas=all.stream().filter(i->i.path.equals(parent(create.path))&&i.program.equals(ATA)&&i.data.length==1&&(i.data[0]==0||i.data[0]==1)&&(i.accounts.size()==6||i.accounts.size()==7)&&i.accounts.get(1).equals(account)&&i.accounts.get(2).equals(owner)&&i.accounts.get(3).equals(mint)&&i.accounts.get(4).equals(SYSTEM)&&i.accounts.get(5).equals(program)).toList();require(atas.size()==1,"ATA_CREATION_UNPROVED");
        var global=new ArrayList<Transfer>();for(var i:touches){require(i.program.equals(program)&&i.data.length>0,"IMMUTABLE_TOUCH_UNPROVED");int tag=Byte.toUnsignedInt(i.data[0]);if(tag==3||tag==12){require(all.stream().noneMatch(child->ancestor(i.path,child.path)),"IMMUTABLE_HOOK_UNPROVED");var transfer=transfer(i,pre,post,all,refs);require(transfer.mint.equals(mint)&&comparePaths(init.path,i.path)<0,"IMMUTABLE_TRANSFER_UNPROVED");global.add(transfer);}else require(i.equals(marker)||i.equals(init)||closes.contains(i),"IMMUTABLE_TOUCH_UNPROVED");}
        require(!global.isEmpty()&&global.stream().map(Transfer::decimals).distinct().count()==1,"IMMUTABLE_TRANSFER_UNPROVED");int decimals=global.getFirst().decimals;
        if(after!=null)require(after.decimals==decimals,"TOKEN_ENDPOINT_MISMATCH");
        if(after==null){var close=closes.getFirst();require(close.data.length==1&&close.accounts.size()==3&&close.accounts.getFirst().equals(account)&&close.accounts.get(2).equals(owner)&&global.stream().allMatch(t->comparePaths(t.instruction.path,close.path)<0),"IMMUTABLE_CLOSE_UNPROVED");refs.add(close.path);}
        require(flow(global,account).equals(after==null?BigInteger.ZERO:after.amount),"IMMUTABLE_BALANCE_UNPROVED");refs.add(atas.getFirst().path);refs.add(create.path);refs.add(marker.path);refs.add(init.path);
        return after!=null?after:new TokenState(mint,owner,program,decimals,BigInteger.ZERO);
    }
    private void remainingRoles(Instruction swap,Event event,String variant,Map<String,TokenState> pre,Map<String,TokenState> post) {
        var a=swap.accounts;int core=variant.equals("sell")?21:23;
        if(a.size()==core)return; // Original complete May core remains supported.
        require(event.facts.containsKey("virtual_quote_reserves"),"UNPROVED_REMAINING_ROLES");
        String volume=pda(PUMP,List.of("user_volume_accumulator".getBytes(StandardCharsets.UTF_8),decode(a.get(1),32)));
        String volumeAta=ata(volume,a.get(4),a.get(12));int next=core;
        if(a.get(next).equals(volumeAta)) {
            observedRole(volumeAta,a.get(4),volume,a.get(12),pre,post);next++;
            if(variant.equals("sell")){require(next<a.size()&&a.get(next).equals(volume),"UNPROVED_REMAINING_ROLES");next++;}
        }
        String creator=encode(Arrays.copyOfRange(event.instruction.data,320,352));
        if(!creator.equals(SYSTEM)){require(next<a.size()&&a.get(next).equals(pda(PUMP,List.of("pool-v2".getBytes(StandardCharsets.UTF_8),decode(a.get(3),32)))),"UNPROVED_REMAINING_ROLES");next++;}
        require(next+2==a.size(),"UNPROVED_REMAINING_ROLES");String wallet=a.get(next),account=a.get(next+1);
        require(account.equals(ata(wallet,a.get(4),a.get(12))),"UNPROVED_REMAINING_ROLES");observedRole(account,a.get(4),wallet,a.get(12),pre,post);
    }
    private void observedRole(String account,String mint,String owner,String program,Map<String,TokenState> pre,Map<String,TokenState> post) {
        var before=pre.get(account);var after=post.get(account);require(before!=null||after!=null,"TOKEN_ROLE_UNPROVED");
        for(var s:new TokenState[]{before,after})if(s!=null)require(s.mint.equals(mint)&&s.owner.equals(owner)&&(s.program.isEmpty()||s.program.equals(program)),"TOKEN_ROLE_MISMATCH");
    }
    private static boolean ancillary(String variant){return List.of("init_user_volume_accumulator","close_user_volume_accumulator","sweep_protocol_fee","sweep_creator_fee").contains(variant);}
    private void nonOperation(Instruction invocation,String variant,List<Instruction> all,List<Instruction> invocations,Map<String,TokenState> pre,Map<String,TokenState> post) {
        var a=invocation.accounts;require(invocation.data.length==8,"ANCILLARY_LAYOUT");boolean init=variant.equals("init_user_volume_accumulator"),close=variant.equals("close_user_volume_accumulator");
        require(a.size()==(init?6:close?4:12),"ANCILLARY_LAYOUT");int authority=init?4:close?2:10;
        require(a.get(authority).equals(AUTH)&&a.get(authority+1).equals(PUMP),"ANCILLARY_ROLE_MISMATCH");
        if(init||close){String user=a.get(init?1:0),volume=a.get(init?2:1);require(volume.equals(pda(PUMP,List.of("user_volume_accumulator".getBytes(StandardCharsets.UTF_8),decode(user,32)))),"ANCILLARY_ROLE_MISMATCH");if(init)require(a.get(3).equals(SYSTEM),"ANCILLARY_ROLE_MISMATCH");}
        else require(token(a.get(4))&&a.get(8).equals(SYSTEM)&&a.get(9).equals(ATA)&&a.get(7).equals(ata(a.get(6),a.get(3),a.get(4)))&&!a.get(5).equals(a.get(7)),"ANCILLARY_ROLE_MISMATCH");
        var frames=all.stream().filter(i->i.program.equals(PUMP)&&parent(i.path).equals(invocation.path)&&hex(i.data,0,8).equals("e445a52e51cb9a1d")).toList();require(!frames.isEmpty(),"MISSING_EVENT");
        Instruction event=null;String discriminator=init?"86240d48e86582d8":close?"929fbdac925838f4":"82a42461e48287a5";
        for(var frame:frames){require(frame.accounts.equals(List.of(AUTH)),"EVENT_ROLE_MISMATCH");require(frame.data.length==(init||close?88:193)&&hex(frame.data,8,16).equals(discriminator),"ANCILLARY_EVENT_LAYOUT");if(event!=null)require(Arrays.equals(event.data,frame.data),"EVENT_CONFLICT");else event=frame;}
        var b=event.data;
        if(init){require(eventAddress(b,16).equals(a.get(0))&&eventAddress(b,48).equals(a.get(1)),"ANCILLARY_ROLE_MISMATCH");
            var creates=all.stream().filter(i->i.program.equals(SYSTEM)&&parent(i.path).equals(invocation.path)&&i.accounts.equals(List.of(a.get(0),a.get(2)))&&i.data.length==52&&ByteBuffer.wrap(i.data).order(ByteOrder.LITTLE_ENDIAN).getInt(0)==0&&readU64(i.data,12).equals(BigInteger.valueOf(137))&&eventAddress(i.data,20).equals(PUMP)&&comparePaths(i.path,eventPath(frames))<0).toList();require(creates.size()==1,"VOLUME_CREATE_UNPROVED");
        }else if(close)require(eventAddress(b,16).equals(a.get(0)),"ANCILLARY_ROLE_MISMATCH");
        else {
            require(eventAddress(b,24).equals(a.get(2))&&eventAddress(b,88).equals(a.get(3))&&eventAddress(b,120).equals(a.get(6))&&eventAddress(b,152).equals(a.get(0))&&Byte.toUnsignedInt(b[192])==(variant.equals("sweep_protocol_fee")?0:1),"ANCILLARY_ROLE_MISMATCH");
            address(eventAddress(b,56));var transfers=new ArrayList<Transfer>();var refs=new ArrayList<List<Integer>>();
            for(var i:all)if(token(i.program)&&ancestor(invocation.path,i.path)&&controlling(i,invocations).equals(invocation)) {
                require(i.data.length>0&&(i.data[0]==3||i.data[0]==12),"UNSUPPORTED_ANCILLARY_TOKEN_INSTRUCTION");transfers.add(transfer(i,pre,post,all,refs));
            }
            var amount=readU64(b,184);require(transfers.stream().allMatch(t->t.instruction.program.equals(a.get(4))&&t.mint.equals(a.get(3))&&t.source.equals(a.get(5))&&t.destination.equals(a.get(7))&&t.authority.equals(a.get(2)))&&flow(transfers,a.get(5)).equals(amount.negate())&&flow(transfers,a.get(7)).equals(amount),"SWEEP_TRANSFER_UNPROVED");
            observedRole(a.get(5),a.get(3),a.get(2),a.get(4),pre,post);observedRole(a.get(7),a.get(3),a.get(6),a.get(4),pre,post);
        }
        if(init||close)require(all.stream().noneMatch(i->token(i.program)&&ancestor(invocation.path,i.path)&&controlling(i,invocations).equals(invocation)),"UNEXPECTED_VOLUME_TOKEN_FLOW");
    }
    private static List<Integer> eventPath(List<Instruction> frames){return frames.getFirst().path;}
    private static String eventAddress(byte[] bytes,int offset){return encode(Arrays.copyOfRange(bytes,offset,offset+32));}
    private static String ata(String owner,String mint,String program){return pda(ATA,List.of(decode(owner,32),decode(program,32),decode(mint,32)));}
    // SDK-pinned Solana findProgramAddress: finite bump search; SHA-256 candidate must not decompress on Edwards25519.
    private static String pda(String program,List<byte[]> seeds) {
        try{for(int bump=255;bump>=0;bump--){var digest=MessageDigest.getInstance("SHA-256");for(var seed:seeds){require(seed.length<=32,"PDA_SEED_INVALID");digest.update(seed);}digest.update((byte)bump);digest.update(decode(program,32));digest.update("ProgramDerivedAddress".getBytes(StandardCharsets.UTF_8));var candidate=digest.digest();if(!onCurve(candidate))return encode(candidate);}throw new Unsupported("PDA_UNPROVED");}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static boolean onCurve(byte[] compressed) {
        var bytes=compressed.clone();int sign=Byte.toUnsignedInt(bytes[31])>>>7;bytes[31]&=127;for(int n=0;n<16;n++){byte t=bytes[n];bytes[n]=bytes[31-n];bytes[31-n]=t;}
        var p=BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));var y=new BigInteger(1,bytes);if(y.compareTo(p)>=0)return false;
        var y2=y.multiply(y).mod(p);var d=BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(p)).mod(p);var denominator=d.multiply(y2).add(BigInteger.ONE).mod(p);if(denominator.signum()==0)return false;
        var x2=y2.subtract(BigInteger.ONE).multiply(denominator.modInverse(p)).mod(p);if(x2.signum()==0)return sign==0;
        return x2.modPow(p.subtract(BigInteger.ONE).shiftRight(1),p).equals(BigInteger.ONE);
    }
    private static BigInteger flow(List<Transfer> ts,String account){var result=BigInteger.ZERO;for(var t:ts){if(t.destination.equals(account))result=result.add(t.amount);if(t.source.equals(account))result=result.subtract(t.amount);}return result;}
    private static boolean token(String s){return s.equals(TOKEN)||s.equals(TOKEN2022);}
    private static String variant(Instruction i){return switch(hex(i.data,0,8)){case "33e685a4017f83ad"->"sell";case "66063d1201daebea"->"buy";case "c62e1552b4d9e870"->"buy_exact_quote_in";case "5df6823ce7e940b2"->"sell_v2";case "b817ee6167c5d33d"->"buy_v2";case "c2ab1c46684d5b2f"->"buy_exact_quote_in_v2";case "5e06ca73ff60e8b7"->"init_user_volume_accumulator";case "f945a4da9667548a"->"close_user_volume_accumulator";case "0830be07b644b7e5"->"sweep_protocol_fee";case "20f6bf3408c949ba"->"sweep_creator_fee";case "2b644913e9f66f94"->"multi_hop_swap";default->"unknown";};}
    private static String hex(byte[] b,int from,int to){return b.length>=to?HexFormat.of().formatHex(b,from,to):"";}
    private static int index(JsonNode n,int bound){require(n.isIntegralNumber()&&n.canConvertToInt()&&n.intValue()>=0&&n.intValue()<bound,"ACCOUNT_INDEX_INVALID");return n.intValue();}
    private static BigInteger u64(JsonNode n){String s=n.isTextual()?n.asText():n.isIntegralNumber()?n.asString():"";require(s.matches("0|[1-9][0-9]{0,19}"),"INVALID_U64");var v=new BigInteger(s);require(v.compareTo(U64)<=0,"INVALID_U64");return v;}
    private static Optional<BigInteger> optionalU64(JsonNode n){if(n.isMissingNode()||n.isNull())return Optional.empty();try{return Optional.of(u64(n));}catch(Unsupported e){return Optional.empty();}}
    private static BigInteger readU64(byte[] b,int offset){var v=BigInteger.ZERO;for(int n=7;n>=0;n--)v=v.shiftLeft(8).add(BigInteger.valueOf(Byte.toUnsignedInt(b[offset+n])));return v;}
    private static void address(String s){require(decode(s,32).length==32,"INVALID_ADDRESS");}
    private static byte[] decode(String s,int max){limit(s.length()<=max*2);var n=BigInteger.ZERO;for(char c:s.toCharArray()){int d=ALPHABET.indexOf(c);require(d>=0,"INVALID_BASE58");n=n.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(d));}var body=n.signum()==0?new byte[0]:n.toByteArray();if(body.length>0&&body[0]==0)body=Arrays.copyOfRange(body,1,body.length);int z=0;while(z<s.length()&&s.charAt(z)=='1')z++;limit(z+body.length<=max);var out=new byte[z+body.length];System.arraycopy(body,0,out,z,body.length);return out;}
    private static String encode(byte[] bytes){var n=new BigInteger(1,bytes);var s=new StringBuilder();while(n.signum()>0){var q=n.divideAndRemainder(BigInteger.valueOf(58));s.append(ALPHABET.charAt(q[1].intValue()));n=q[0];}for(byte b:bytes){if(b!=0)break;s.append('1');}return s.reverse().toString();}
    public static String sha256(byte[] bytes){try{return "sha256:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    public static int comparePaths(List<Integer> a,List<Integer> b){for(int n=0;n<Math.min(a.size(),b.size());n++){int c=Integer.compare(a.get(n),b.get(n));if(c!=0)return c;}return Integer.compare(a.size(),b.size());}
    private static List<Integer> parent(List<Integer> p){return p.subList(0,p.size()-1);}
    private static boolean ancestor(List<Integer> a,List<Integer> b){return a.size()<b.size()&&b.subList(0,a.size()).equals(a);}
    private static Position position(List<Integer> p){return new Position(p.getFirst(),p.subList(1,p.size()),0);}
}
