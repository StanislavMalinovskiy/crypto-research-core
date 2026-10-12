package io.cryptoresearch.marketdata.infrastructure;

import io.cryptoresearch.marketdata.api.PumpSwapOperations;
import io.cryptoresearch.marketdata.api.PumpSwapPoolLegs;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics.Rational;
import java.math.BigInteger;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Independent complete vault conservation, followed by exclusive invocation attribution. */
@Component
public class PumpSwapOrdinaryPoolNetProof {
    public record Proof(Optional<BigInteger> quote,Optional<Rational> price,String reason,String digest) { }
    private static final String PUMP="pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA",TOKEN="TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",TOKEN2022="TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",WSOL="So11111111111111111111111111111111111111112",SYSTEM="11111111111111111111111111111111";
    private static final String ALPHABET="123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger U64=BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private final JsonMapper json=JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private record Instruction(String program,List<String> accounts,List<Integer> path,byte[] data) { }
    private record State(String mint,String program,String owner,int decimals,BigInteger amount) { }
    private record Transfer(Instruction instruction,String from,String to,String authority,BigInteger amount) { }
    private record Ledger(State before,State after,List<Transfer> transfers) { }
    private static class Unproved extends RuntimeException {Unproved(String reason){super(reason);}}
    public Proof prove(PumpSwapOperations.RecordedTransaction source,PumpSwapOperations.Operation operation,
            PumpSwapPoolLegs.PoolBinding binding) {
        if(!source.rawDigest().equals(operation.rawDigest())||!source.signature().equals(operation.signature())||!source.chain().equals(operation.chain())
                ||!PumpSwapRawOperationParser.sha256(source.rawResult().getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(source.rawDigest()))throw new IllegalArgumentException("ORDINARY_POOL_NET_SOURCE_MISMATCH");
        try {
            require(binding.pool().equals(operation.pool())&&binding.baseMint().equals(operation.baseMint())&&binding.quoteMint().equals(operation.quoteMint()),"POOL_BINDING_MISMATCH");
            var root=json.readTree(source.rawResult());var keys=keys(root);var all=instructions(root,keys);
            var pre=states(root.path("meta").path("preTokenBalances"),keys);var post=states(root.path("meta").path("postTokenBalances"),keys);
            var path=new ArrayList<Integer>();path.add(operation.position().outerIndex());path.addAll(operation.position().innerPath());
            var invocations=all.stream().filter(i->i.path.equals(path)&&i.program.equals(PUMP)).toList();require(invocations.size()==1,"INVOCATION_UNPROVED");var call=invocations.getFirst();
            var a=call.accounts;boolean v2=operation.variant().endsWith("_v2");
            require(a.size()>=(v2?17:21)&&a.get(0).equals(binding.pool())&&a.get(3).equals(binding.baseMint())&&a.get(4).equals(binding.quoteMint())
                    &&a.get(7).equals(binding.baseVault())&&a.get(8).equals(binding.quoteVault())&&a.get(v2?9:11).equals(binding.baseProgram())&&a.get(v2?10:12).equals(binding.quoteProgram()),"INVOCATION_ROLE_UNPROVED");
            var frames=all.stream().filter(i->i.path.equals(operation.eventPath())&&i.program.equals(PUMP)&&parent(i.path).equals(call.path)).toList();
            require(frames.size()==1&&PumpSwapRawOperationParser.sha256(frames.getFirst().data).equals(operation.eventDigest()),"EVENT_REFERENCE_UNPROVED");
            var own=new HashSet<>(operation.transferPaths());require(own.size()==operation.transferPaths().size()&&!own.isEmpty(),"TRANSFER_REFERENCE_AMBIGUOUS");
            for(var ref:own){var match=all.stream().filter(i->i.path.equals(ref)).toList();require(match.size()==1&&token(match.getFirst().program)&&ancestor(call.path,ref)
                    &&all.stream().noneMatch(i->i.program.equals(PUMP)&&!i.equals(call)&&ancestor(call.path,i.path)&&ancestor(i.path,ref)&&!hex(i.data,0,8).equals("e445a52e51cb9a1d")),"TRANSFER_REFERENCE_AMBIGUOUS");}
            var base=ledger(binding.baseVault(),binding.baseMint(),binding.baseProgram(),binding.pool(),all,pre,post);
            var baseFlow=flow(base.transfers.stream().filter(t->own.contains(t.instruction.path)).toList(),binding.baseVault());
            boolean sell=operation.variant().startsWith("sell");
            require(baseFlow.equals(sell?operation.baseAmount():operation.baseAmount().negate())&&operation.baseAmount().signum()>0,"BASE_ATTRIBUTION_UNPROVED");
            require(operation.baseDecimals().isPresent()&&operation.baseDecimals().get()==base.before.decimals,"BASE_UNITS_UNPROVED");
            require(binding.quoteMint().equals(WSOL)&&binding.quoteProgram().equals(TOKEN),"UNSUPPORTED_QUOTE");
            var quote=ledger(binding.quoteVault(),binding.quoteMint(),binding.quoteProgram(),binding.pool(),all,pre,post);
            require(quote.before.decimals==9&&operation.quoteDecimals().isPresent()&&operation.quoteDecimals().get()==9,"QUOTE_UNITS_UNPROVED");
            var selected=quote.transfers.stream().filter(t->own.contains(t.instruction.path)).toList();require(!selected.isEmpty(),"QUOTE_ATTRIBUTION_UNPROVED");
            var net=flow(selected,binding.quoteVault());require(net.signum()==(sell?-1:1),"QUOTE_ATTRIBUTION_UNPROVED");
            var value=net.abs();var ratio=new Rational(value.multiply(BigInteger.TEN.pow(base.before.decimals)),operation.baseAmount().multiply(BigInteger.TEN.pow(9)));
            var facts=new TreeMap<String,Object>();facts.put("proofVersion","ordinary-pool-net-proof-v1");facts.put("binding",binding);facts.put("rawDigest",source.rawDigest());facts.put("eventDigest",operation.eventDigest());facts.put("position",operation.position());
            facts.put("transferPaths",operation.transferPaths());facts.put("baseBefore",base.before);facts.put("baseAfter",base.after);facts.put("quoteBefore",quote.before);facts.put("quoteAfter",quote.after);facts.put("ownBaseFlow",baseFlow);facts.put("ownQuoteFlow",net);
            return new Proof(Optional.of(value),Optional.of(ratio),"PRICED",PumpSwapRawOperationParser.sha256(json.writeValueAsBytes(facts)));
        }catch(Unproved e){return new Proof(Optional.empty(),Optional.empty(),e.getMessage(),source.rawDigest());}
    }
    private Ledger ledger(String vault,String mint,String program,String owner,List<Instruction> all,Map<String,State> pre,Map<String,State> post) {
        var before=pre.get(vault);var after=post.get(vault);require(before!=null&&after!=null,"VAULT_ENDPOINT_UNPROVED");
        require(before.mint.equals(mint)&&after.mint.equals(mint)&&before.program.equals(program)&&after.program.equals(program)&&before.owner.equals(owner)&&after.owner.equals(owner)&&before.decimals==after.decimals,"VAULT_ROLE_UNPROVED");
        var transfers=new ArrayList<Transfer>();
        for(var i:all)if(i.accounts.contains(vault)) {
            if(token(i.program)) {
                require(i.program.equals(program)&&i.data.length>0&&(i.data[0]==3||i.data[0]==12),"VAULT_TOUCH_UNPROVED");
                require(all.stream().noneMatch(child->ancestor(i.path,child.path)),"VAULT_HOOK_UNPROVED");
                boolean checked=i.data[0]==12;var a=i.accounts;require(i.data.length==(checked?10:9)&&a.size()==(checked?4:3),"TRANSFER_LAYOUT_UNPROVED");
                String from=a.get(0),to=a.get(checked?2:1);require(!from.equals(to)&&(from.equals(vault)||to.equals(vault)),"VAULT_TOUCH_UNPROVED");
                if(checked)require(a.get(1).equals(mint)&&Byte.toUnsignedInt(i.data[9])==before.decimals,"TRANSFER_UNITS_UNPROVED");
                for(var account:List.of(from,to)){var present=pre.get(account);if(present==null)present=post.get(account);if(!checked)require(present!=null,"TRANSFER_UNITS_UNPROVED");
                    for(var state:new State[]{pre.get(account),post.get(account)})if(state!=null)require(state.mint.equals(mint)&&state.program.equals(program)&&state.decimals==before.decimals,"TRANSFER_UNITS_UNPROVED");}
                var authority=a.get(checked?3:2);if(from.equals(vault))require(authority.equals(owner),"VAULT_AUTHORITY_UNPROVED");
                transfers.add(new Transfer(i,from,to,authority,u64(i.data,1)));
            }else require(!i.program.equals(SYSTEM),"VAULT_SYSTEM_TOUCH_UNPROVED");
        }
        require(!transfers.isEmpty(),"VAULT_TRANSFER_UNPROVED");transfers.sort(Comparator.comparing(t->t.instruction.path,PumpSwapRawOperationParser::comparePaths));
        var running=before.amount;for(var t:transfers){running=running.add(t.from.equals(vault)?t.amount.negate():t.amount);require(running.signum()>=0&&running.compareTo(U64)<=0,"VAULT_BALANCE_UNPROVED");}
        require(after.amount.equals(running),"VAULT_BALANCE_UNPROVED");return new Ledger(before,after,List.copyOf(transfers));
    }
    private List<String> keys(JsonNode root) {var msg=root.path("transaction").path("message");var rows=msg.path("accountKeys");require(rows.isArray(),"MISSING_ACCOUNT_KEYS");limit(rows.size()<=2048);var keys=new ArrayList<String>();boolean parsed=false;
        for(var row:rows){parsed|=row.isObject();var key=row.isTextual()?row.asText():row.path("pubkey").asText();PumpSwapMultiHopPoolLegParser.validAddress(key);keys.add(key);}
        var loaded=root.path("meta").path("loadedAddresses");if(loaded.isObject()){require(!parsed,"AMBIGUOUS_LOADED_KEYS");for(var side:List.of("writable","readonly")){var extra=loaded.path(side);require(extra.isArray(),"MISSING_LOADED_KEYS");limit(keys.size()+extra.size()<=2048);for(var row:extra){PumpSwapMultiHopPoolLegParser.validAddress(row.asText());keys.add(row.asText());}}}
        else require(parsed||!msg.path("addressTableLookups").isArray()||msg.path("addressTableLookups").isEmpty(),"MISSING_LOADED_KEYS");require(new HashSet<>(keys).size()==keys.size(),"DUPLICATE_ACCOUNT_KEYS");return keys;}
    private List<Instruction> instructions(JsonNode root,List<String> keys) {var rows=root.path("transaction").path("message").path("instructions");require(rows.isArray(),"MISSING_INSTRUCTIONS");limit(rows.size()<=10000);var all=new ArrayList<Instruction>();for(int n=0;n<rows.size();n++)all.add(instruction(rows.get(n),List.of(n),keys));
        var groups=root.path("meta").path("innerInstructions");require(groups.isArray(),"MISSING_INSTRUCTIONS");var seen=new HashSet<Integer>();for(var group:groups){int outer=index(group.path("index"),rows.size());require(seen.add(outer),"DUPLICATE_INNER_GROUP");var extra=group.path("instructions");require(extra.isArray(),"MISSING_INSTRUCTIONS");limit(all.size()+extra.size()<=10000);var stack=new ArrayList<List<Integer>>();stack.add(List.of(outer));var children=new HashMap<List<Integer>,Integer>();
            for(var row:extra){var h=row.path("stackHeight");require(h.isIntegralNumber()&&h.canConvertToInt(),"PATH_UNKNOWN");int height=h.intValue();limit(height<=64);require(height>=2&&height<=stack.size()+1,"PATH_UNKNOWN");while(stack.size()>height-1)stack.removeLast();var p=stack.getLast();var path=new ArrayList<>(p);path.add(children.getOrDefault(p,0));children.merge(p,1,Integer::sum);stack.add(path);all.add(instruction(row,path,keys));}}return all;}
    private Instruction instruction(JsonNode row,List<Integer> path,List<String> keys) {String program=row.has("programIdIndex")?keys.get(index(row.path("programIdIndex"),keys.size())):row.path("programId").asText();PumpSwapMultiHopPoolLegParser.validAddress(program);var a=row.path("accounts");require(a.isArray(),"INSTRUCTION_ACCOUNTS_UNPROVED");limit(a.size()<=2048);var accounts=new ArrayList<String>();for(var n:a){var key=n.isIntegralNumber()?keys.get(index(n,keys.size())):n.asText();PumpSwapMultiHopPoolLegParser.validAddress(key);accounts.add(key);}require(row.path("data").isTextual(),"INSTRUCTION_DATA_UNPROVED");return new Instruction(program,List.copyOf(accounts),List.copyOf(path),decode(row.path("data").asText()));}
    private Map<String,State> states(JsonNode rows,List<String> keys) {require(rows.isArray(),"TOKEN_BALANCES_UNPROVED");limit(rows.size()<=10000);var result=new HashMap<String,State>();for(var row:rows){var ui=row.path("uiTokenAmount");var d=ui.path("decimals");require(d.isIntegralNumber()&&d.canConvertToInt()&&d.intValue()>=0&&d.intValue()<=255,"TOKEN_UNITS_UNPROVED");var s=ui.path("amount").asText();require(s.matches("0|[1-9][0-9]{0,19}"),"TOKEN_AMOUNT_UNPROVED");var amount=new BigInteger(s);require(amount.compareTo(U64)<=0,"TOKEN_AMOUNT_UNPROVED");require(result.putIfAbsent(keys.get(index(row.path("accountIndex"),keys.size())),new State(row.path("mint").asText(),row.path("programId").asText(),row.path("owner").asText(),d.intValue(),amount))==null,"TOKEN_BALANCE_CONFLICT");}return result;}
    private static BigInteger flow(List<Transfer> transfers,String vault){var value=BigInteger.ZERO;for(var t:transfers){if(t.from.equals(vault))value=value.subtract(t.amount);if(t.to.equals(vault))value=value.add(t.amount);}return value;}
    private static boolean ancestor(List<Integer> a,List<Integer> b){return a.size()<b.size()&&b.subList(0,a.size()).equals(a);}
    private static List<Integer> parent(List<Integer> p){return p.subList(0,p.size()-1);}
    private static boolean token(String p){return p.equals(TOKEN)||p.equals(TOKEN2022);}
    private static String hex(byte[] b,int from,int to){return b.length>=to?HexFormat.of().formatHex(b,from,to):"";}
    private static int index(JsonNode n,int bound){require(n.isIntegralNumber()&&n.canConvertToInt()&&n.intValue()>=0&&n.intValue()<bound,"ACCOUNT_INDEX_UNPROVED");return n.intValue();}
    private static BigInteger u64(byte[] b,int start){require(start+8<=b.length,"TRANSFER_LAYOUT_UNPROVED");var n=BigInteger.ZERO;for(int j=7;j>=0;j--)n=n.shiftLeft(8).add(BigInteger.valueOf(Byte.toUnsignedInt(b[start+j])));return n;}
    private static byte[] decode(String s){limit(s.length()<=8192);var value=BigInteger.ZERO;for(char c:s.toCharArray()){int n=ALPHABET.indexOf(c);require(n>=0,"INVALID_BASE58");value=value.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(n));}var bytes=value.signum()==0?new byte[0]:value.toByteArray();if(bytes.length>0&&bytes[0]==0)bytes=Arrays.copyOfRange(bytes,1,bytes.length);int zero=0;while(zero<s.length()&&s.charAt(zero)=='1')zero++;limit(zero+bytes.length<=4096);var result=new byte[zero+bytes.length];System.arraycopy(bytes,0,result,zero,bytes.length);return result;}
    private static void require(boolean yes,String reason){if(!yes)throw new Unproved(reason);}
    private static void limit(boolean yes){if(!yes)throw new IllegalArgumentException("VERSIONED_CANDLES_LIMIT");}
}
