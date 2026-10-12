package io.cryptoresearch.marketdata.infrastructure;

import io.cryptoresearch.marketdata.api.PumpSwapOperations;
import io.cryptoresearch.marketdata.api.PumpSwapPoolLegs;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Pure source proof for a single own pool event, never a complete aggregator route. */
@Component
public class PumpSwapMultiHopPoolLegParser {
    private static final String PUMP="pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA";
    private static final String TOKEN="TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
    private static final String TOKEN2022="TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb";
    private static final String SYSTEM="11111111111111111111111111111111";
    private static final String AUTH="GS4CU59F31iL7aR2Q8zVS8DRrcRnXX1yjQ66TqNVQnaR";
    private static final String WSOL="So11111111111111111111111111111111111111112";
    private static final String ALPHABET="123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger U64=BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private final JsonMapper json=JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private record Instruction(String program,List<String> accounts,List<Integer> path,byte[] data) { }
    private record State(String mint,String owner,String program,int decimals,BigInteger amount) { }
    private record Transfer(Instruction instruction,String from,String to,String authority,BigInteger amount) { }
    private record Ledger(State before,State after,BigInteger delta,List<Transfer> transfers) { }
    private record Event(Instruction instruction,boolean sell,String pool,String user,String baseAccount,
            String quoteAccount,BigInteger base,BigInteger gross,BigInteger userQuote,BigInteger buyback) { }
    private static class Unproved extends RuntimeException { Unproved(String reason){super(reason);} }
    public record Overlay(PumpSwapOperations.TransactionResult transaction,List<PumpSwapPoolLegs.PoolLeg> legs) { }
    private static void require(boolean condition,String reason){if(!condition)throw new Unproved(reason);}
    private static void limit(boolean condition){if(!condition)throw new IllegalArgumentException("DECODER_LIMIT");}

    public Overlay parse(PumpSwapOperations.TransactionResult old,List<PumpSwapPoolLegs.PoolBinding> pools) {
        if(old.status()==PumpSwapOperations.Status.FAILED||old.status()==PumpSwapOperations.Status.INVALID
                ||old.invocations().stream().noneMatch(i->i.variant().equals("multi_hop_swap")&&i.reason().equals("UNSUPPORTED_VARIANT")))
            return new Overlay(old,List.of());
        var source=old.source();var legs=new ArrayList<PumpSwapPoolLegs.PoolLeg>();
        var outcomes=new ArrayList<PumpSwapOperations.Invocation>();
        try {
            var root=json.readTree(source.rawResult());var keys=keys(root);var all=instructions(root,keys);
            var pre=states(root.path("meta").path("preTokenBalances"),keys);
            var post=states(root.path("meta").path("postTokenBalances"),keys);
            for(var previous:old.invocations()) {
                if(!previous.variant().equals("multi_hop_swap")||!previous.reason().equals("UNSUPPORTED_VARIANT")){outcomes.add(previous);continue;}
                try {
                    var path=new ArrayList<Integer>();path.add(previous.position().outerIndex());path.addAll(previous.position().innerPath());
                    var found=all.stream().filter(i->i.path.equals(path)&&i.program.equals(PUMP)&&hex(i.data,0,8).equals("2b644913e9f66f94")).toList();
                    require(found.size()==1,"INVOCATION_ASSOCIATION_UNPROVED");
                    var leg=leg(source,found.getFirst(),pools,all,pre,post);legs.add(leg);
                    outcomes.add(new PumpSwapOperations.Invocation(previous.position(),PumpSwapOperations.Status.DECODED,"CONFIRMED_POOL_LEG","multi_hop_swap"));
                }catch(Unproved e){outcomes.add(new PumpSwapOperations.Invocation(previous.position(),PumpSwapOperations.Status.UNSUPPORTED,e.getMessage(),"multi_hop_swap"));}
            }
        }catch(Unproved e){return new Overlay(new PumpSwapOperations.TransactionResult(source,old.transactionVersion(),PumpSwapOperations.Status.UNSUPPORTED,e.getMessage(),old.networkFee(),old.invocations()),List.of());}
        boolean resolved=outcomes.stream().allMatch(i->i.status()==PumpSwapOperations.Status.DECODED||i.status()==PumpSwapOperations.Status.NON_OPERATION);
        boolean economic=outcomes.stream().anyMatch(i->i.status()==PumpSwapOperations.Status.DECODED);
        var status=resolved?(economic?PumpSwapOperations.Status.DECODED:PumpSwapOperations.Status.NON_OPERATION)
                :economic?PumpSwapOperations.Status.PARTIAL:PumpSwapOperations.Status.UNSUPPORTED;
        var reason=outcomes.stream().filter(i->i.status()!=PumpSwapOperations.Status.DECODED&&i.status()!=PumpSwapOperations.Status.NON_OPERATION)
                .map(PumpSwapOperations.Invocation::reason).distinct().sorted().reduce((a,b)->a+","+b).orElse("CONFIRMED_POOL_LEG");
        return new Overlay(new PumpSwapOperations.TransactionResult(source,old.transactionVersion(),status,reason,old.networkFee(),outcomes),List.copyOf(legs));
    }

    private PumpSwapPoolLegs.PoolLeg leg(PumpSwapOperations.RecordedTransaction source,Instruction call,
            List<PumpSwapPoolLegs.PoolBinding> pools,List<Instruction> all,Map<String,State> pre,Map<String,State> post) {
        var a=call.accounts;
        require(call.data.length==24&&a.size()>=21&&(a.size()-16)%5==0,"INVOCATION_LAYOUT");
        require(a.get(7).equals(TOKEN)&&a.get(8).equals(TOKEN2022)&&a.get(9).equals(SYSTEM)
                &&a.get(10).equals(AUTH)&&a.get(11).equals(PUMP)
                &&a.get(12).equals("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P")
                &&a.get(3).equals("ADyA8hdefvWN2dbGGWFotbzWxrAvLW83WG6QCVXvJKqw")
                &&a.get(4).equals("5PHirr8joyTMp9JMm6nW7hNDVyEYdkzDqazxPD7RaTjx")
                &&a.get(13).equals("4wTV1YmiEkRvAtNtsSGPtUrqRYQMe5SKy2uB4Jjaxnjf")
                &&a.get(14).equals("8Wf5TiAheLUqBrKXeYg2JtAFFMWtKdG2BSFgqUcPVwTt")
                &&a.get(15).equals("Ce6TQqeHC9p8KetsN6JsjHK7UTZk7nasjjnr7XxXp9F1"),"INVOCATION_ROLE_MISMATCH");
        var frames=all.stream().filter(i->i.program.equals(PUMP)&&parent(i.path).equals(call.path)
                &&hex(i.data,0,8).equals("e445a52e51cb9a1d")).toList();
        require(frames.size()<=1,"MULTIPLE_OWN_EVENTS");require(frames.size()==1,"MISSING_OWN_EVENT");
        var event=event(frames.getFirst());
        require(event.user.equals(a.get(0)),"EVENT_ROLE_MISMATCH");
        var matching=pools.stream().filter(p->p.pool().equals(event.pool)).toList();require(matching.size()==1,"NON_TARGET_POOL");
        var binding=matching.getFirst();int matches=0;
        for(int n=16;n<a.size();n+=5)if(a.subList(n,n+5).equals(List.of(binding.baseMint(),binding.quoteMint(),binding.pool(),binding.baseVault(),binding.quoteVault())))matches++;
        require(matches==1&&!binding.baseVault().equals(binding.quoteVault()),"POOL_ROLE_UNPROVED");
        var base=ledger(binding.baseVault(),binding.baseMint(),binding.baseProgram(),binding.pool(),call,all,pre,post,"BASE_BALANCE_UNPROVED");
        var expectedBase=event.sell?event.base:event.base.negate();
        require(event.base.signum()>0&&base.delta.equals(expectedBase),"BASE_BALANCE_UNPROVED");
        require(base.transfers.size()==1,"BASE_EVENT_FLOW_UNPROVED");
        for(var transfer:base.transfers) {
            String counterpart=event.sell?transfer.from:transfer.to;
            require(event.sell?transfer.to.equals(binding.baseVault()):transfer.from.equals(binding.baseVault())&&transfer.authority.equals(binding.pool()),"BASE_EVENT_FLOW_UNPROVED");
            if(event.baseAccount.equals(SYSTEM)) {
                // The pinned multi-hop event reports no user base account. Prove the actual
                // intermediate vault from an observed hop tuple instead of inventing a trader ATA.
                int counterparts=0;
                for(int n=16;n<a.size();n+=5)for(int role:List.of(0,1))if(a.get(n+3+role).equals(counterpart)&&a.get(n+role).equals(binding.baseMint())) {
                    var state=pre.get(counterpart);if(state==null)state=post.get(counterpart);
                    require(state!=null&&state.owner.equals(a.get(n+2))&&state.mint.equals(binding.baseMint())&&state.program.equals(binding.baseProgram())&&state.decimals==base.before.decimals,"BASE_COUNTERPART_UNPROVED");
                    if(event.sell)require(transfer.authority.equals(state.owner),"BASE_EVENT_FLOW_UNPROVED");counterparts++;
                }
                require(counterparts==1,"BASE_COUNTERPART_UNPROVED");
            }else require(counterpart.equals(event.baseAccount)&&(!event.sell||transfer.authority.equals(event.user)),"BASE_EVENT_FLOW_UNPROVED");
        }
        // Quote proof failure is explicit degradation; no quote amount substitutes for the vault proof.
        Optional<BigInteger> net=Optional.empty();Optional<PumpSwapPoolLegs.ExactRatio> price=Optional.empty();String priceReason="";
        var refs=new ArrayList<List<Integer>>();base.transfers.forEach(t->refs.add(t.instruction.path));
        try {
            require(binding.quoteMint().equals(WSOL)&&binding.quoteProgram().equals(TOKEN),"UNSUPPORTED_QUOTE");
            var quote=ledger(binding.quoteVault(),binding.quoteMint(),binding.quoteProgram(),binding.pool(),call,all,pre,post,"QUOTE_BALANCE_UNPROVED");
            require(quote.before.decimals==9&&quote.delta.signum()==(event.sell?-1:1),"QUOTE_UNITS_UNPROVED");
            var main=quote.transfers.stream().filter(t->event.sell?t.from.equals(binding.quoteVault())&&t.to.equals(event.quoteAccount)&&t.authority.equals(binding.pool())
                    :t.from.equals(event.quoteAccount)&&t.to.equals(binding.quoteVault())&&t.authority.equals(event.user)).toList();
            var deductions=quote.transfers.stream().filter(t->t.from.equals(binding.quoteVault())&&t.to.equals(a.get(6))&&t.authority.equals(binding.pool())).toList();
            require(main.size()==1&&main.getFirst().amount.equals(event.sell?event.userQuote:event.gross)
                    &&deductions.stream().map(Transfer::amount).reduce(BigInteger.ZERO,BigInteger::add).equals(event.buyback)
                    &&quote.transfers.size()==main.size()+deductions.size(),"QUOTE_EVENT_FLOW_UNPROVED");
            var q=quote.delta.abs();require(q.signum()>0,"QUOTE_BALANCE_UNPROVED");
            var numerator=q.multiply(BigInteger.TEN.pow(base.before.decimals));var denominator=event.base.multiply(BigInteger.TEN.pow(9));var gcd=numerator.gcd(denominator);
            net=Optional.of(q);price=Optional.of(new PumpSwapPoolLegs.ExactRatio(numerator.divide(gcd),denominator.divide(gcd),"POOL_NET_SOL_PER_BASE"));
            quote.transfers.forEach(t->refs.add(t.instruction.path));
        }catch(Unproved e){priceReason=e.getMessage();}
        refs.sort(PumpSwapRawOperationParser::comparePaths);
        var eventDigest=PumpSwapRawOperationParser.sha256(event.instruction.data);
        var proof=new TreeMap<String,Object>();proof.put("baseBefore",base.before);proof.put("baseAfter",base.after);
        proof.put("binding",binding);proof.put("eventDigest",eventDigest);proof.put("rawDigest",source.rawDigest());
        proof.put("position",call.path);proof.put("transfers",refs);proof.put("priceReason",priceReason);proof.put("net",net);
        String proofDigest=PumpSwapRawOperationParser.sha256(json.writeValueAsBytes(proof));
        return new PumpSwapPoolLegs.PoolLeg(source,new PumpSwapOperations.Position(call.path.getFirst(),call.path.subList(1,call.path.size()),0),
                event.sell?"SELL":"BUY",binding,a.get(0),event.user,Optional.empty(),"UNDETERMINED",all.stream().filter(i->i.path.equals(List.of(call.path.getFirst()))).findFirst().orElseThrow().program,
                event.base,base.before.decimals,event.gross,event.userQuote,net,price,price.isPresent()?"PRICE_CONFIRMED":"PRICE_NOT_CONFIRMED",priceReason,event.instruction.path,refs,eventDigest,proofDigest);
    }

    private Event event(Instruction frame) {
        var b=frame.data;var discriminator=hex(b,8,16);
        require(discriminator.equals("67f4521f2cf57777")||discriminator.equals("3e2f370aa503dc2a"),"EVENT_LAYOUT");
        boolean sell=discriminator.equals("3e2f370aa503dc2a");
        require(frame.accounts.equals(List.of(AUTH)),"EVENT_ROLE_MISMATCH");
        int prefix;
        if(sell){prefix=400;require(b.length==449,"EVENT_LAYOUT");}
        else {
            require(b.length>=462,"EVENT_LAYOUT");require(b[368]==0||b[368]==1,"EVENT_LAYOUT");
            int length=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(409);
            require(length==14&&b.length==508&&new String(b,413,length,StandardCharsets.UTF_8).equals("multi_hop_swap"),"EVENT_LAYOUT");prefix=459;
        }
        require(b[prefix+16]==0||b[prefix+16]==1,"EVENT_LAYOUT");
        // Every byte belongs to the pinned layout: timestamp/i128 are signed, amounts unsigned.
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getLong(16);
        var signed=Arrays.copyOfRange(b,prefix,prefix+16);reverse(signed);new BigInteger(signed);
        for(int n=0;n<4;n++)u64(b,prefix+17+n*8);
        int tail=sell?368:427;var cashback=u64(b,tail+8);var buyback=u64(b,tail+24);
        var lp=u64(b,88);var protocol=u64(b,104);var creator=u64(b,360);
        var gross=u64(b,sell?112:72);var user=u64(b,120);
        require(cashback.signum()==0&&gross.equals(user.add(lp).add(protocol).add(creator))&&buyback.compareTo(protocol)<=0,"EVENT_AMOUNT_UNPROVED");
        return new Event(frame,sell,address(b,128),address(b,160),address(b,192),address(b,224),u64(b,24),gross,user,buyback);
    }

    private Ledger ledger(String vault,String mint,String program,String owner,Instruction call,List<Instruction> all,
            Map<String,State> pre,Map<String,State> post,String balanceReason) {
        var before=pre.get(vault);var after=post.get(vault);
        require(before!=null&&after!=null,"VAULT_ENDPOINT_UNPROVED");
        require(token(program)&&before.mint.equals(mint)&&after.mint.equals(mint)&&before.owner.equals(owner)&&after.owner.equals(owner)
                &&before.program.equals(program)&&after.program.equals(program)&&before.decimals==after.decimals,"VAULT_ROLE_UNPROVED");
        var transfers=new ArrayList<Transfer>();
        for(var i:all)if(i.accounts.contains(vault)) {
            if(token(i.program)) {
                require(i.program.equals(program)&&i.data.length>0&&(i.data[0]==12||i.data[0]==3),"VAULT_TOUCH_UNPROVED");
                require(all.stream().noneMatch(c->ancestor(i.path,c.path)),"VAULT_HOOK_UNPROVED");
                require(ancestor(call.path,i.path)&&all.stream().noneMatch(c->c.program.equals(PUMP)&&!c.equals(call)&&ancestor(call.path,c.path)&&ancestor(c.path,i.path)&&!hex(c.data,0,8).equals("e445a52e51cb9a1d")),"VAULT_ATTRIBUTION_UNPROVED");
                boolean checked=i.data[0]==12;
                require(i.data.length==(checked?10:9)&&i.accounts.size()==(checked?4:3),"VAULT_TRANSFER_LAYOUT");
                String from=i.accounts.get(0),to=i.accounts.get(checked?2:1);require(!from.equals(to)&&(from.equals(vault)||to.equals(vault)),"VAULT_TRANSFER_LAYOUT");
                if(checked)require(i.accounts.get(1).equals(mint)&&Byte.toUnsignedInt(i.data[9])==before.decimals,"VAULT_UNITS_UNPROVED");
                else for(String account:List.of(from,to)){var s=pre.get(account);if(s==null)s=post.get(account);require(s!=null&&s.mint.equals(mint)&&s.program.equals(program)&&s.decimals==before.decimals,"VAULT_UNITS_UNPROVED");}
                for(String account:List.of(from,to))for(var s:new State[]{pre.get(account),post.get(account)})if(s!=null)
                    require(s.mint.equals(mint)&&s.program.equals(program)&&s.decimals==before.decimals,"VAULT_UNITS_UNPROVED");
                transfers.add(new Transfer(i,from,to,i.accounts.get(checked?3:2),u64(i.data,1)));
            }else {
                // A router forwarding a token-owned account is not itself a token mutation.
                // Every actual token CPI is examined above (including outside this invocation);
                // unknown System funding/assignment or token lifecycle effects remain rejected.
                require(!i.program.equals(SYSTEM),"VAULT_TOUCH_UNPROVED");
            }
        }
        require(!transfers.isEmpty(),"VAULT_TRANSFER_UNPROVED");
        transfers.sort(Comparator.comparing(t->t.instruction.path,PumpSwapRawOperationParser::comparePaths));
        var running=before.amount;var flow=BigInteger.ZERO;
        for(var t:transfers){var delta=t.from.equals(vault)?t.amount.negate():t.amount;flow=flow.add(delta);running=running.add(delta);require(running.signum()>=0&&running.compareTo(U64)<=0,balanceReason);}
        var delta=after.amount.subtract(before.amount);require(delta.equals(flow),balanceReason);
        return new Ledger(before,after,delta,List.copyOf(transfers));
    }

    private List<String> keys(JsonNode root) {
        var msg=root.path("transaction").path("message");var rows=msg.path("accountKeys");require(rows.isArray(),"MISSING_ACCOUNT_KEYS");limit(rows.size()<=2048);
        var result=new ArrayList<String>();boolean parsed=false;
        for(var row:rows){parsed|=row.isObject();var key=row.isTextual()?row.asText():row.path("pubkey").asText();validAddress(key);result.add(key);}
        var loaded=root.path("meta").path("loadedAddresses");
        if(loaded.isObject()){require(!parsed,"AMBIGUOUS_LOADED_KEYS");for(String side:List.of("writable","readonly")){var added=loaded.path(side);require(added.isArray(),"MISSING_LOADED_KEYS");limit(result.size()+added.size()<=2048);for(var row:added){validAddress(row.asText());result.add(row.asText());}}}
        else require(parsed||!msg.path("addressTableLookups").isArray()||msg.path("addressTableLookups").isEmpty(),"MISSING_LOADED_KEYS");
        require(new HashSet<>(result).size()==result.size(),"DUPLICATE_ACCOUNT_KEYS");return result;
    }
    private List<Instruction> instructions(JsonNode root,List<String> keys) {
        var outer=root.path("transaction").path("message").path("instructions");require(outer.isArray(),"MISSING_INSTRUCTIONS");limit(outer.size()<=10000);
        var all=new ArrayList<Instruction>();for(int n=0;n<outer.size();n++)all.add(instruction(outer.get(n),List.of(n),keys));
        var groups=root.path("meta").path("innerInstructions");require(groups.isArray(),"MISSING_INNER_INSTRUCTIONS");var seen=new HashSet<Integer>();
        for(var group:groups){int index=index(group.path("index"),outer.size());require(seen.add(index),"DUPLICATE_INNER_GROUP");var rows=group.path("instructions");require(rows.isArray(),"MISSING_INSTRUCTIONS");limit(all.size()+rows.size()<=10000);
            var stack=new ArrayList<List<Integer>>();stack.add(List.of(index));var children=new HashMap<List<Integer>,Integer>();
            for(var row:rows){var node=row.path("stackHeight");require(node.isIntegralNumber()&&node.canConvertToInt(),"PATH_UNKNOWN");int h=node.intValue();limit(h<=64);require(h>=2&&h<=stack.size()+1,"PATH_UNKNOWN");while(stack.size()>h-1)stack.removeLast();var p=stack.getLast();var path=new ArrayList<>(p);path.add(children.getOrDefault(p,0));children.merge(p,1,Integer::sum);stack.add(path);all.add(instruction(row,path,keys));}}
        all.sort(Comparator.comparing(Instruction::path,PumpSwapRawOperationParser::comparePaths));return all;
    }
    private Instruction instruction(JsonNode row,List<Integer> path,List<String> keys) {
        String program=row.has("programIdIndex")?keys.get(index(row.path("programIdIndex"),keys.size())):row.path("programId").asText();validAddress(program);
        var rows=row.path("accounts");require(rows.isArray(),"UNSUPPORTED_PARSED_INSTRUCTION");limit(rows.size()<=2048);var accounts=new ArrayList<String>();
        for(var n:rows){var key=n.isIntegralNumber()?keys.get(index(n,keys.size())):n.asText();validAddress(key);accounts.add(key);}
        require(row.path("data").isTextual(),"UNSUPPORTED_PARSED_INSTRUCTION");return new Instruction(program,List.copyOf(accounts),List.copyOf(path),decode58(row.path("data").asText(),4096));
    }
    private Map<String,State> states(JsonNode rows,List<String> keys){require(rows.isArray(),"MISSING_TOKEN_BALANCES");limit(rows.size()<=10000);var result=new HashMap<String,State>();
        for(var row:rows){var key=keys.get(index(row.path("accountIndex"),keys.size()));var ui=row.path("uiTokenAmount");require(ui.path("decimals").isIntegralNumber()&&ui.path("decimals").canConvertToInt(),"TOKEN_BALANCE_INVALID");int decimals=ui.path("decimals").intValue();require(decimals>=0&&decimals<=255,"TOKEN_BALANCE_INVALID");
            var mint=row.path("mint").asText();var owner=row.path("owner").asText();var program=row.path("programId").asText();validAddress(mint);validAddress(owner);if(!program.isEmpty())validAddress(program);
            var value=ui.path("amount").asText();require(value.matches("0|[1-9][0-9]{0,19}"),"TOKEN_BALANCE_INVALID");var amount=new BigInteger(value);require(amount.compareTo(U64)<=0,"TOKEN_BALANCE_INVALID");
            require(result.putIfAbsent(key,new State(mint,owner,program,decimals,amount))==null,"TOKEN_BALANCE_CONFLICT");}return result;}
    private static int index(JsonNode n,int bound){require(n.isIntegralNumber()&&n.canConvertToInt()&&n.intValue()>=0&&n.intValue()<bound,"ACCOUNT_INDEX_INVALID");return n.intValue();}
    private static boolean token(String p){return p.equals(TOKEN)||p.equals(TOKEN2022);}
    private static List<Integer> parent(List<Integer> p){return p.subList(0,p.size()-1);}
    private static boolean ancestor(List<Integer> a,List<Integer> b){return a.size()<b.size()&&b.subList(0,a.size()).equals(a);}
    private static String hex(byte[] b,int from,int to){return b.length>=to?HexFormat.of().formatHex(b,from,to):"";}
    private static BigInteger u64(byte[] bytes,int start){require(start>=0&&start+8<=bytes.length,"EVENT_LAYOUT");var result=BigInteger.ZERO;for(int n=7;n>=0;n--)result=result.shiftLeft(8).add(BigInteger.valueOf(Byte.toUnsignedInt(bytes[start+n])));return result;}
    private static void reverse(byte[] b){for(int n=0;n<b.length/2;n++){byte t=b[n];b[n]=b[b.length-n-1];b[b.length-n-1]=t;}}
    private static String address(byte[] b,int start){require(start+32<=b.length,"EVENT_LAYOUT");return encode58(Arrays.copyOfRange(b,start,start+32));}
    public static void validAddress(String s){require(decode58(s,32).length==32,"INVALID_ADDRESS");}
    private static byte[] decode58(String s,int max){limit(s.length()<=max*2);var n=BigInteger.ZERO;for(char c:s.toCharArray()){int d=ALPHABET.indexOf(c);require(d>=0,"INVALID_BASE58");n=n.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(d));}var bytes=n.signum()==0?new byte[0]:n.toByteArray();if(bytes.length>0&&bytes[0]==0)bytes=Arrays.copyOfRange(bytes,1,bytes.length);int z=0;while(z<s.length()&&s.charAt(z)=='1')z++;limit(z+bytes.length<=max);var out=new byte[z+bytes.length];System.arraycopy(bytes,0,out,z,bytes.length);return out;}
    private static String encode58(byte[] bytes){var n=new BigInteger(1,bytes);var s=new StringBuilder();while(n.signum()>0){var q=n.divideAndRemainder(BigInteger.valueOf(58));s.append(ALPHABET.charAt(q[1].intValue()));n=q[0];}for(byte b:bytes){if(b!=0)break;s.append('1');}return s.reverse().toString();}
}
