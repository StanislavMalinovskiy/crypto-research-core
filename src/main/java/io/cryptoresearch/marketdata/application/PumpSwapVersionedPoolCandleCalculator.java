package io.cryptoresearch.marketdata.application;

import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.marketdata.api.PumpSwapVersionedPoolCandles.*;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics.Pool;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics.Rational;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapOrdinaryPoolNetProof;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Whole selected population and sparse event bars; no stored-batch fabrication. */
@Component
public class PumpSwapVersionedPoolCandleCalculator {
    static final Comparator<String> TEXT=(a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    private static final Comparator<Pool> POOLS=Comparator.comparing(Pool::chain,TEXT).thenComparing(Pool::pool,TEXT).thenComparing(Pool::baseMint,TEXT).thenComparing(Pool::quoteMint,TEXT);
    private static final Comparator<Operation> ORDER=Comparator.comparing((Operation o)->o.eventTime().orElse(Instant.MAX)).thenComparingLong(Operation::slot)
        .thenComparingInt(o->o.transactionIndex().isPresent()?0:1).thenComparing(o->o.transactionIndex().orElse(null),Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(Operation::signature,TEXT).thenComparingInt(o->o.position().outerIndex()).thenComparing(o->o.position().innerPath(),PumpSwapVersionedPoolCandleCalculator::paths)
        .thenComparingInt(o->o.position().legOrdinal()).thenComparing(Operation::sourceReference,TEXT).thenComparing(Operation::identity,TEXT);
    private final JsonMapper json=JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    public record OperationInput(PumpSwapOperations.RecordedTransaction source,PumpSwapOperations.Operation ordinary,PumpSwapPoolLegs.PoolLeg multi) { }
    private record Raw(Optional<Instant> eventTime,Optional<Long> index,JsonNode json) { }
    static Descriptor normalize(Request request) {
        Objects.requireNonNull(request,"request");
        if(!"pumpswap-pool-legs-v5".equals(request.decoderVersion()))fail("VERSIONED_CANDLES_UNSUPPORTED_VERSION");
        var from=micro(request.fromInclusive());var to=micro(request.toExclusive());var k=micro(request.knowledgeCutoff());var d=micro(request.derivationObservedAt());
        try {if(from.getNano()!=0||to.getNano()!=0||Math.floorMod(from.getEpochSecond(),300)!=0||Math.floorMod(to.getEpochSecond(),300)!=0||!from.isBefore(to)||to.isAfter(k))fail("VERSIONED_CANDLES_INVALID_REQUEST");
            if(Math.subtractExact(to.getEpochSecond(),from.getEpochSecond())/300>1000)fail("VERSIONED_CANDLES_LIMIT");}
        catch(ArithmeticException ex){throw new IllegalArgumentException("VERSIONED_CANDLES_INVALID_REQUEST");}
        if(request.pools().isEmpty()||request.pools().size()>3||request.pools().stream().map(PumpSwapPoolLegs.PoolBinding::pool).distinct().count()!=request.pools().size())fail("VERSIONED_CANDLES_INVALID_BINDINGS");
        return new Descriptor(request.decoderVersion(),request.pools().stream().sorted(Comparator.comparing(PumpSwapPoolLegs.PoolBinding::pool,TEXT)).toList(),from,to,d,k);
    }
    public Result calculate(Descriptor request,List<PumpSwapOperations.TransactionResult> selected,List<OperationInput> inputs,
            Map<String,String> pins,List<String> limitations,PumpSwapOrdinaryPoolNetProof ordinaryProof) {
        var metadata=new HashMap<List<String>,Raw>();var txs=new ArrayList<Transaction>();var positions=new HashMap<List<Object>,String>();
        var sourceCounts=new HashMap<List<String>,Integer>();for(var input:inputs)sourceCounts.merge(sourceKey(input.source()),1,Integer::sum);
        var uncertainties=new ArrayList<Uncertainty>();var statusCounts=new TreeMap<String,Integer>();
        for(var tx:selected) {
            var s=tx.source();var raw=raw(s);metadata.put(sourceKey(s),raw);
            if(raw.index.isPresent()){var key=List.<Object>of(s.chain().value(),s.slot(),raw.index.get());var old=positions.putIfAbsent(key,s.signature());if(old!=null&&!old.equals(s.signature()))fail("VERSIONED_CANDLES_POSITION_CONFLICT");}
            var clocks=clocks(s,request.derivationObservedAt());
            txs.add(new Transaction(s.chain().value(),s.signature(),s.provider(),s.session().toString(),s.rawDigest(),s.sourceReference(),s.slot(),raw.index,raw.eventTime,s.sourceTime(),clocks,tx.status(),tx.reason(),sourceCounts.getOrDefault(sourceKey(s),0)));
            statusCounts.merge(tx.status().name(),1,Integer::sum);
            if(tx.status()==PumpSwapOperations.Status.UNSUPPORTED||tx.status()==PumpSwapOperations.Status.PARTIAL||tx.status()==PumpSwapOperations.Status.INVALID) {
                var membership=membership(raw.json,request.pools());
                for(var p:request.pools())if(membership==null||membership.contains(p.pool()))
                    if(raw.eventTime.isEmpty()||inside(raw.eventTime.get(),request))uncertainties.add(new Uncertainty(pool(s,p),raw.eventTime,s.signature(),membership==null?"POOL_MEMBERSHIP_UNKNOWN":raw.eventTime.isEmpty()?"MISSING_EVENT_TIME":"UNRESOLVED_POOL_EVIDENCE"));
            }
        }
        txs.sort(Comparator.comparing(Transaction::chain,TEXT).thenComparing(Transaction::signature,TEXT));
        var refs=new ArrayList<Operation>();var unique=new HashSet<List<Object>>();var claimedTransfers=new HashMap<List<Object>,List<Object>>();
        for(var input:inputs) {
            var s=input.source();var raw=metadata.get(sourceKey(s));if(raw==null)fail("VERSIONED_CANDLES_SOURCE_MISMATCH");
            boolean ordinary=input.ordinary()!=null;var o=input.ordinary();var m=input.multi();
            var position=ordinary?o.position():m.position();var key=List.<Object>of(s.chain().value(),s.signature(),position.outerIndex(),position.innerPath(),position.legOrdinal());
            if(!unique.add(key))fail("VERSIONED_CANDLES_DUPLICATE_OPERATION");
            var target=ordinary?new Pool(s.chain().value(),o.pool(),o.baseMint(),o.quoteMint()):pool(s,m.binding());
            var matching=request.pools().stream().filter(p->p.pool().equals(target.pool())).toList();boolean inScope=!matching.isEmpty();
            if(inScope&&(!matching.getFirst().baseMint().equals(target.baseMint())||!matching.getFirst().quoteMint().equals(target.quoteMint())))fail("VERSIONED_CANDLES_BINDING_CONFLICT");
            Optional<BigInteger> quote=Optional.empty();Optional<Rational> price=Optional.empty();String reason="NON_TARGET_POOL";String proofDigest=s.rawDigest();
            if(inScope) {
                if(ordinary) {var proof=ordinaryProof.prove(s,o,matching.getFirst());quote=proof.quote();price=proof.price();reason=proof.reason();proofDigest=proof.digest();}
                else {quote=m.poolNetQuoteLamports();price=m.price().map(p->new Rational(p.numerator(),p.denominator()));reason=price.isPresent()?"PRICED":m.priceReason();proofDigest=m.proofDigest();}
            }
            var paths=ordinary?o.transferPaths():m.transferPaths();
            for(var path:paths){var claim=List.<Object>of(s.chain().value(),s.signature(),path);var prior=claimedTransfers.putIfAbsent(claim,key);if(prior!=null&&!prior.equals(key))fail("VERSIONED_CANDLES_AMBIGUOUS_TRANSFER");}
            var clock=clocks(s,request.derivationObservedAt());String disposition=!inScope?"OUTSIDE_TARGET":raw.eventTime.isEmpty()?"MISSING_EVENT_TIME":!inside(raw.eventTime.get(),request)?"OUTSIDE_EVENT_WINDOW":clock.knownAt().isAfter(request.knowledgeCutoff())?"FUTURE_KNOWLEDGE":"INCLUDED";
            var identity=new CanonicalFingerprint().field("domain","pool-exchange-position-v1").field("chain",s.chain().value()).field("signature",s.signature()).field("outer",Integer.toString(position.outerIndex()));
            for(var path:position.innerPath())identity.field("inner",Integer.toString(path));identity.field("ordinal",Integer.toString(position.legOrdinal()));
            var base=ordinary?o.baseAmount():m.baseAmount();var decimals=ordinary?o.baseDecimals():Optional.of(m.baseDecimals());
            refs.add(new Operation(identity.finish(),s.chain().value(),s.signature(),s.rawDigest(),s.sourceReference(),position,target,ordinary?"ORDINARY":"MULTI_HOP",ordinary?(o.variant().startsWith("sell")?"SELL":"BUY"):m.direction(),base,decimals,quote,price,"POOL_NET_SOL_PER_BASE",reason,ordinary?Optional.of(o.quoteAmount()):Optional.of(m.eventUserQuote()),s.slot(),raw.index,raw.eventTime,clock,disposition,ordinary?o.eventDigest():m.eventDigest(),ordinary?o.eventPath():m.eventPath(),paths,proofDigest));
            if(inScope&&(disposition.equals("MISSING_EVENT_TIME")||disposition.equals("FUTURE_KNOWLEDGE")))uncertainties.add(new Uncertainty(target,raw.eventTime,s.signature(),disposition));
        }
        refs.sort(Comparator.comparing(Operation::chain,TEXT).thenComparing(ORDER));
        var groups=new TreeMap<Pool,TreeMap<Instant,List<Operation>>>(POOLS);var decimals=new HashMap<Pool,Integer>();var included=refs.stream().filter(o->o.disposition().equals("INCLUDED")).toList();
        for(var o:included) {
            if(o.baseDecimals().isPresent()){var old=decimals.putIfAbsent(o.pool(),o.baseDecimals().get());if(old!=null&&!old.equals(o.baseDecimals().get()))fail("VERSIONED_CANDLES_DECIMALS_CONFLICT");}
            var start=Instant.ofEpochSecond(Math.floorDiv(o.eventTime().orElseThrow().getEpochSecond(),300)*300);
            groups.computeIfAbsent(o.pool(),ignored->new TreeMap<>()).computeIfAbsent(start,ignored->new ArrayList<>()).add(o);
        }
        var candles=new ArrayList<Candle>();
        for(var group:groups.entrySet())for(var bucket:group.getValue().entrySet()) {
            if(candles.size()>=10000)fail("VERSIONED_CANDLES_LIMIT");var members=bucket.getValue();members.sort(ORDER);
            var priced=members.stream().filter(o->o.price().isPresent()).map(o->new Price(o.price().orElseThrow(),o.identity())).toList();Optional<Ohlc> ohlc=Optional.empty();
            if(!priced.isEmpty()){var high=priced.getFirst();var low=high;for(var p:priced){if(compare(p.value(),high.value())>0)high=p;if(compare(p.value(),low.value())<0)low=p;}ohlc=Optional.of(new Ohlc(priced.getFirst(),high,low,priced.getLast()));}
            int ordinaryCount=(int)members.stream().filter(o->o.kind().equals("ORDINARY")).count();int missingIndex=(int)members.stream().filter(o->o.transactionIndex().isEmpty()).count();var base=members.stream().map(Operation::baseAmount).reduce(BigInteger.ZERO,BigInteger::add);
            Optional<BigInteger> quote=members.stream().allMatch(o->o.poolNetQuoteLamports().isPresent())?Optional.of(members.stream().map(o->o.poolNetQuoteLamports().orElseThrow()).reduce(BigInteger.ZERO,BigInteger::add)):Optional.empty();
            candles.add(new Candle(group.getKey(),bucket.getKey(),bucket.getKey().plusSeconds(300),members.size(),ordinaryCount,members.size()-ordinaryCount,priced.size(),members.size()-priced.size(),missingIndex,base,quote,members.stream().map(Operation::identity).toList(),ohlc));
        }
        uncertainties.sort(Comparator.comparing(Uncertainty::pool,POOLS).thenComparing(u->u.eventTime().orElse(Instant.MAX)).thenComparing(Uncertainty::signature,TEXT).thenComparing(Uncertainty::reason,TEXT));
        var uniqueUncertainty=uncertainties.stream().distinct().toList();int priceCount=(int)included.stream().filter(o->o.price().isPresent()).count();
        var labels=new TreeSet<String>(TEXT);labels.addAll(limitations);labels.addAll(List.of("OBSERVED_RECORDED_WINDOW_ONLY","COVERAGE_INCOMPLETE","LOSS_UNKNOWN","FEES_INCOMPLETE","DIAGNOSTIC_ONLY_NOT_ADMITTED","DERIVATION_KNOWN_AT_NOT_PERSISTED_OR_ADMITTED","PROVIDER_REPORTED_INDEX_NOT_INDEPENDENTLY_VERIFIED","SPARSE_NO_CARRY_FORWARD"));
        var sourceFingerprint=fingerprint("pumpswap-selected-window-input-v1",List.of(request.decoderVersion(),request.pools(),pins,txs,List.copyOf(labels)));
        var report=new Result("pumpswap-pool-net-candles-v1","length-prefixed-v1",request,sourceFingerprint,pins,txs,refs,candles,uniqueUncertainty,new Reconciliation(txs.size(),refs.size(),included.size(),refs.size()-included.size(),priceCount,included.size()-priceCount,statusCounts),List.copyOf(labels),"");
        return new Result(report.algorithmVersion(),report.canonicalizationVersion(),request,sourceFingerprint,pins,txs,refs,candles,uniqueUncertainty,report.reconciliation(),report.limitations(),fingerprint("pumpswap-pool-net-candles-v1",report));
    }
    private Raw raw(PumpSwapOperations.RecordedTransaction s) {
        var root=json.readTree(s.rawResult());var slot=root.path("slot");var signatures=root.path("transaction").path("signatures");
        if(!slot.isIntegralNumber()||!slot.canConvertToLong()||slot.longValue()!=s.slot()||!signatures.isArray()||signatures.isEmpty()||!signatures.get(0).asText().equals(s.signature()))fail("VERSIONED_CANDLES_SOURCE_MISMATCH");
        Optional<Instant> event=Optional.empty();var time=root.path("blockTime");
        if(!time.isMissingNode()&&!time.isNull()){if(!time.isIntegralNumber()||!time.canConvertToLong())fail("VERSIONED_CANDLES_INVALID_EVENT_TIME");try{event=Optional.of(Instant.ofEpochSecond(time.longValue()));}catch(RuntimeException ex){throw new IllegalArgumentException("VERSIONED_CANDLES_INVALID_EVENT_TIME");}}
        if(s.sourceTime().isPresent()&&!s.sourceTime().equals(event))fail("VERSIONED_CANDLES_SOURCE_TIME_MISMATCH");
        Optional<Long> index=Optional.empty();var position=root.path("transactionIndex");if(!position.isMissingNode()&&!position.isNull()){if(!position.isIntegralNumber()||!position.canConvertToLong()||position.longValue()<0)fail("VERSIONED_CANDLES_INVALID_INDEX");index=Optional.of(position.longValue());}
        return new Raw(event,index,root);
    }
    private Set<String> membership(JsonNode root,List<PumpSwapPoolLegs.PoolBinding> pools) {
        var msg=root.path("transaction").path("message");var rows=msg.path("accountKeys");if(!rows.isArray())return null;var keys=new HashSet<String>();boolean parsed=false;
        for(var row:rows){parsed|=row.isObject();var key=row.isTextual()?row.asText():row.path("pubkey").asText();if(key.isBlank())return null;keys.add(key);}
        var loaded=root.path("meta").path("loadedAddresses");if(loaded.isObject()){if(parsed)return null;for(var side:List.of("writable","readonly")){var extra=loaded.path(side);if(!extra.isArray())return null;for(var key:extra){if(!key.isTextual())return null;keys.add(key.asText());}}}
        else if(!parsed&&msg.path("addressTableLookups").isArray()&&!msg.path("addressTableLookups").isEmpty())return null;
        var matched=new HashSet<String>();for(var p:pools)if(keys.contains(p.pool())||keys.contains(p.baseVault())||keys.contains(p.quoteVault()))matched.add(p.pool());return matched;
    }
    static String fingerprint(String domain,Object value) {var json=JsonMapper.builder().build();var hash=new CanonicalFingerprint();hash.field("domain",domain);hash.field("canonicalization","length-prefixed-v1");var node=json.valueToTree(value);long[] count={0};encode(hash,"value",node,count);return hash.finish();}
    private static void encode(CanonicalFingerprint hash,String path,JsonNode node,long[] count) {
        if(node.isObject()){var names=new ArrayList<>(node.propertyNames());names.sort(TEXT);field(hash,path,"object:"+names.size(),count);for(var name:names)encode(hash,path+"/"+name,node.get(name),count);}
        else if(node.isArray()){field(hash,path,"array:"+node.size(),count);for(int n=0;n<node.size();n++)encode(hash,path+"/"+n,node.get(n),count);}
        else field(hash,path,node.isTextual()?"text:"+node.asText():node.toString(),count);
    }
    private static void field(CanonicalFingerprint hash,String name,String value,long[] count){count[0]+=8L+name.getBytes(StandardCharsets.UTF_8).length+value.getBytes(StandardCharsets.UTF_8).length;if(count[0]>64_000_000)fail("VERSIONED_CANDLES_OUTPUT_LIMIT");hash.field(name,value);}
    static List<String> sourceKey(PumpSwapOperations.RecordedTransaction s){return List.of(s.chain().value(),s.signature());}
    private static Pool pool(PumpSwapOperations.RecordedTransaction s,PumpSwapPoolLegs.PoolBinding p){return new Pool(s.chain().value(),p.pool(),p.baseMint(),p.quoteMint());}
    private static Clocks clocks(PumpSwapOperations.RecordedTransaction s,Instant d){var known=Collections.max(List.of(s.receivedAt(),s.admittedAt(),s.ingestedAt(),d));return new Clocks(s.receivedAt(),s.admittedAt(),s.ingestedAt(),d,known);}
    private static int compare(Rational a,Rational b){return a.numerator().multiply(b.denominator()).compareTo(b.numerator().multiply(a.denominator()));}
    private static int paths(List<Integer>a,List<Integer>b){for(int n=0;n<Math.min(a.size(),b.size());n++){int c=Integer.compare(a.get(n),b.get(n));if(c!=0)return c;}return Integer.compare(a.size(),b.size());}
    private static boolean inside(Instant t,Descriptor r){return !t.isBefore(r.fromInclusive())&&t.isBefore(r.toExclusive());}
    private static Instant micro(Instant t){return Objects.requireNonNull(t).truncatedTo(ChronoUnit.MICROS);}
    private static void fail(String reason){throw new IllegalArgumentException(reason);}
}
