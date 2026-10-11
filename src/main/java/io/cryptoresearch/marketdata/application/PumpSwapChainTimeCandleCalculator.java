package io.cryptoresearch.marketdata.application;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.cryptoresearch.marketdata.api.PumpSwapChainTimeCandles;
import io.cryptoresearch.marketdata.api.PumpSwapChainTimeCandles.*;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics.Pool;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics.Rational;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;
import io.cryptoresearch.marketdata.api.PumpSwapOperationPersistence.StoredBatch;

@Component
public class PumpSwapChainTimeCandleCalculator {
    private static final String VERSION="pumpswap-chain-time-candles-v1";
    private static final Comparator<String> TEXT=(a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    private static final Comparator<Pool> POOL=Comparator.comparing(Pool::chain,TEXT).thenComparing(Pool::pool,TEXT).thenComparing(Pool::baseMint,TEXT).thenComparing(Pool::quoteMint,TEXT);
    private static final Comparator<OperationReference> ORDER=Comparator.comparing((OperationReference r)->r.eventTime().orElse(Instant.MAX))
        .thenComparingLong(OperationReference::slot).thenComparingInt(r->r.transactionIndex().isPresent()?0:1)
        .thenComparing(r->r.transactionIndex().orElse(null),Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(OperationReference::signature,TEXT).thenComparingInt(r->r.position().outerIndex())
        .thenComparing(r->r.position().innerPath(),PumpSwapChainTimeCandleCalculator::paths)
        .thenComparingInt(r->r.position().legOrdinal()).thenComparing(OperationReference::revisionKey,TEXT);
    private final PumpSwapOperationCanonicalizer canonicalizer=new PumpSwapOperationCanonicalizer();
    private final JsonMapper json=JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private record Raw(Optional<Instant> eventTime,Optional<Long> index,JsonNode json) {}
    static Request normalize(Request input) {
        try {
            var from=Objects.requireNonNull(input.fromInclusive()).truncatedTo(ChronoUnit.MICROS);
            var to=Objects.requireNonNull(input.toExclusive()).truncatedTo(ChronoUnit.MICROS);
            var k=Objects.requireNonNull(input.knowledgeCutoff()).truncatedTo(ChronoUnit.MICROS);
            if(input.batchKey()==null||!input.batchKey().matches("sha256:[0-9a-f]{64}")||from.getNano()!=0||to.getNano()!=0
                ||Math.floorMod(from.getEpochSecond(),300)!=0||Math.floorMod(to.getEpochSecond(),300)!=0
                ||!from.isBefore(to)||to.isAfter(k)||Math.subtractExact(to.getEpochSecond(),from.getEpochSecond())/300>1000)fail("CHAIN_TIME_INVALID_REQUEST");
            return new Request(input.batchKey(),from,to,k);
        } catch(RuntimeException invalid) {throw new IllegalArgumentException("CHAIN_TIME_INVALID_REQUEST");}
    }
    Result calculate(Request input,StoredBatch batch) {
        var request=normalize(input);var result=batch.result();
        if(!request.batchKey().equals(batch.batchKey()))fail("CHAIN_TIME_SOURCE_MISMATCH");
        if(!"pumpswap-storage-v1".equals(batch.storageVersion())||!"pumpswap-operations-v4".equals(result.decoderVersion())
            ||!"may18-oct07-endpoint-proof-v4".equals(result.schemaVersion())||!"length-prefixed-v1".equals(result.canonicalizationVersion()))fail("CHAIN_TIME_UNSUPPORTED_SOURCE_VERSION");
        if(result.transactions().size()>128||batch.transactions().size()>128||result.operations().size()>10000||batch.operations().size()>10000)fail("CHAIN_TIME_LIMIT");
        var canonicalRevisions=new HashMap<String,String>();
        for(var rev:batch.operations()) {var old=canonicalRevisions.putIfAbsent(rev.canonicalKey(),rev.revisionKey());if(old!=null&&!old.equals(rev.revisionKey()))fail("CHAIN_TIME_AMBIGUOUS_OPERATION_REVISION");}
        // Reuse the storage contract's bounded content/membership verification; never re-decode.
        var prepared=canonicalizer.prepare(result);
        var parents=new HashMap<String,PumpSwapOperations.TransactionResult>();var raw=new HashMap<String,Raw>();
        var txRevisions=new HashMap<String,io.cryptoresearch.marketdata.api.PumpSwapOperationPersistence.TransactionRevision>();
        for(var rev:batch.transactions())if(txRevisions.put(rev.revisionKey(),rev)!=null)fail("CHAIN_TIME_SOURCE_MISMATCH");
        var signatures=new HashMap<List<Object>,List<Object>>();var positions=new HashMap<List<Object>,String>();
        for(var row:prepared.transactions()) {
            var rev=txRevisions.get(row.key());if(rev==null||!rev.contentDigest().equals(row.digest())||!rev.operationRevisionKeys().equals(row.operationKeys())||parents.put(row.key(),row.transaction())!=null)fail("CHAIN_TIME_SOURCE_MISMATCH");
            var s=row.transaction().source();var metadata=raw(s);raw.put(row.key(),metadata);
            var facts=Arrays.<Object>asList(s.slot(),metadata.index(),metadata.eventTime());
            var old=signatures.putIfAbsent(List.of(s.chain(),s.signature()),facts);if(old!=null&&!old.equals(facts))fail("CHAIN_TIME_SIGNATURE_CONFLICT");
            if(metadata.index().isPresent()) {
                var prior=positions.putIfAbsent(List.of(s.chain(),s.slot(),metadata.index().get()),s.signature());
                if(prior!=null&&!prior.equals(s.signature()))fail("CHAIN_TIME_POSITION_CONFLICT");
            }
        }
        if(parents.size()!=txRevisions.size()||prepared.operations().size()!=batch.operations().size())fail("CHAIN_TIME_SOURCE_MISMATCH");
        var rows=new HashMap<String,PumpSwapOperationCanonicalizer.OperationRow>();for(var row:prepared.operations())rows.put(row.key(),row);
        var refs=new ArrayList<OperationReference>();var seen=new HashSet<String>();
        for(var rev:batch.operations()) {
            var row=rows.get(rev.revisionKey());if(row==null||!seen.add(rev.revisionKey())||!row.canonicalKey().equals(rev.canonicalKey())||!row.transactionKey().equals(rev.transactionRevisionKey())||!row.digest().equals(rev.contentDigest()))fail("CHAIN_TIME_SOURCE_MISMATCH");
            var parent=parents.get(rev.transactionRevisionKey());var s=parent.source();var o=row.operation();var metadata=raw.get(rev.transactionRevisionKey());
            if(!Set.of(PumpSwapOperations.Status.DECODED,PumpSwapOperations.Status.PARTIAL).contains(parent.status())||!Set.of("buy","buy_exact_quote_in","buy_v2","buy_exact_quote_in_v2","sell","sell_v2").contains(o.variant())||o.baseAmount().signum()<=0||o.quoteAmount().signum()<=0)fail("CHAIN_TIME_INVALID_OPERATION");
            for(var decimals:List.of(o.baseDecimals(),o.quoteDecimals()))if(decimals.isPresent()&&(decimals.get()<0||decimals.get()>255))fail("CHAIN_TIME_DECIMALS_CONFLICT");
            var tx=txRevisions.get(rev.transactionRevisionKey());var known=max(s.receivedAt(),s.admittedAt(),s.ingestedAt(),tx.persistedAt(),rev.persistedAt(),batch.persistedAt());
            var clocks=new Clocks(s.receivedAt(),s.admittedAt(),s.ingestedAt(),micro(tx.persistedAt()),micro(rev.persistedAt()),micro(batch.persistedAt()),known);
            String disposition=metadata.eventTime().isEmpty()?"MISSING_EVENT_TIME":!inside(metadata.eventTime().get(),request)?"OUTSIDE_EVENT_WINDOW":known.isAfter(request.knowledgeCutoff())?"FUTURE_KNOWLEDGE":"INCLUDED";
            String priceReason=priceReason(o,metadata.json());
            refs.add(new OperationReference(rev.canonicalKey(),rev.revisionKey(),rev.transactionRevisionKey(),rev.contentDigest(),s.rawDigest(),s.sourceReference(),new Pool(o.chain().value(),o.pool(),o.baseMint(),o.quoteMint()),o.signature(),s.slot(),metadata.index(),metadata.eventTime(),o.position(),clocks,o.variant(),o.baseAmount(),o.quoteAmount(),o.baseDecimals(),o.quoteDecimals(),new PumpSwapPoolMetrics.FeeFacts(o.eventDeclaredFees(),o.attributedFees(),o.eventFacts(),o.feeStatus(),o.feeReasons()),disposition,priceReason,new ExecutionProof(result.schemaVersion(),o.eventDigest(),o.eventPath(),o.transferPaths(),o.lifecyclePaths())));
        }
        refs.sort(Comparator.comparing((OperationReference r)->r.pool().chain(),TEXT).thenComparing(ORDER));
        var selected=refs.stream().filter(r->r.disposition().equals("INCLUDED")).toList();
        var groups=new TreeMap<Pool,TreeMap<Instant,List<OperationReference>>>(POOL);var decimals=new HashMap<Pool,Integer[]>();
        for(var r:selected) {
            var known=decimals.computeIfAbsent(r.pool(),ignored->new Integer[2]);var ds=List.of(r.baseDecimals(),r.quoteDecimals());
            for(int i=0;i<2;i++)if(ds.get(i).isPresent()){int d=ds.get(i).get();if(known[i]!=null&&known[i]!=d)fail("CHAIN_TIME_DECIMALS_CONFLICT");known[i]=d;}
            var start=Instant.ofEpochSecond(Math.multiplyExact(Math.floorDiv(r.eventTime().orElseThrow().getEpochSecond(),300),300));
            groups.computeIfAbsent(r.pool(),ignored->new TreeMap<>()).computeIfAbsent(start,ignored->new ArrayList<>()).add(r);
        }
        var candles=new ArrayList<Candle>();
        for(var group:groups.entrySet())for(var bucket:group.getValue().entrySet()) {
            if(candles.size()>=10000)fail("CHAIN_TIME_LIMIT");var members=bucket.getValue();members.sort(ORDER);
            var prices=members.stream().filter(r->r.priceReason().equals("PRICED")).map(PumpSwapChainTimeCandleCalculator::price).toList();Optional<Ohlc> ohlc=Optional.empty();
            if(!prices.isEmpty()) {var high=prices.getFirst();var low=high;for(var p:prices){if(compare(p.value(),high.value())>0)high=p;if(compare(p.value(),low.value())<0)low=p;}ohlc=Optional.of(new Ohlc(prices.getFirst(),high,low,prices.getLast()));}
            candles.add(new Candle(group.getKey(),bucket.getKey(),bucket.getKey().plusSeconds(300),activity(members),ohlc));
        }
        var txs=new ArrayList<TransactionDisposition>();var statuses=new EnumMap<PumpSwapOperations.Status,Integer>(PumpSwapOperations.Status.class);
        for(var entry:parents.entrySet()) {var tx=entry.getValue();var s=tx.source();var rev=txRevisions.get(entry.getKey());var m=raw.get(entry.getKey());statuses.merge(tx.status(),1,Integer::sum);
            txs.add(new TransactionDisposition(rev.revisionKey(),rev.contentDigest(),s.chain().value(),s.signature(),s.provider(),s.slot(),m.index(),m.eventTime(),s.sourceTime(),s.receivedAt(),s.admittedAt(),s.ingestedAt(),micro(rev.persistedAt()),tx.status(),tx.reason(),tx.networkFee(),rev.operationRevisionKeys().stream().sorted(TEXT).toList()));}
        txs.sort(Comparator.comparing(TransactionDisposition::chain,TEXT).thenComparing(TransactionDisposition::signature,TEXT).thenComparing(TransactionDisposition::revisionKey,TEXT));
        int priced=(int)selected.stream().filter(r->r.priceReason().equals("PRICED")).count();
        var reconciliation=new Reconciliation(txs.size(),refs.size(),selected.size(),refs.size()-selected.size(),priced,selected.size()-priced,Arrays.stream(PumpSwapOperations.Status.values()).map(s->new PumpSwapPoolMetrics.StatusCount(s,statuses.getOrDefault(s,0))).toList());
        var source=new PumpSwapPoolMetrics.Source(batch.batchKey(),batch.storageVersion(),result.decoderVersion(),result.schemaVersion(),result.canonicalizationVersion(),result.fingerprint(),result.pinnedSourceHashes(),result.limitations().stream().distinct().sorted(TEXT).toList());
        var interpretation=new Interpretation("OBSERVED_BATCH_ONLY","INCOMPLETE","UNKNOWN","INCOMPLETE","DIAGNOSTIC_ONLY_NOT_ADMITTED","GAP_COVERAGE_NOT_BOUND","PROVIDER_REPORTED_INDEX_NOT_INDEPENDENTLY_VERIFIED_MISSING_INDEX_UNCERTAIN","SPARSE_POPULATED_ONLY_NO_CARRY_FORWARD","CONSERVATIVE_KNOWN_AT_NOT_COMMIT_OR_ADMISSION_PROOF");
        var report=new Result(VERSION,"length-prefixed-v1",request,source,micro(batch.persistedAt()),candles,txs,refs,reconciliation,interpretation,"");var encoding=new Encoding();encoding.value("report",report);
        return new Result(VERSION,"length-prefixed-v1",request,source,micro(batch.persistedAt()),candles,txs,refs,reconciliation,interpretation,encoding.hash.finish());
    }
    private Raw raw(PumpSwapOperations.RecordedTransaction s) {
        if(!PayloadFingerprint.sha256(s.rawResult()).equals(s.rawDigest()))fail("CHAIN_TIME_RAW_DIGEST_MISMATCH");
        JsonNode root;try {root=json.readTree(s.rawResult());}catch(RuntimeException invalid){throw new IllegalArgumentException("CHAIN_TIME_INVALID_RAW_JSON");}
        var slot=root.path("slot");var signatures=root.path("transaction").path("signatures");
        if(!root.isObject()||!slot.isIntegralNumber()||!slot.canConvertToLong()||slot.longValue()!=s.slot()||s.slot()<0||!signatures.isArray()||signatures.isEmpty()||!signatures.get(0).isTextual()||!signatures.get(0).asText().equals(s.signature()))fail("CHAIN_TIME_RAW_IDENTITY_MISMATCH");
        Optional<Instant> event=Optional.empty();var block=root.path("blockTime");
        if(!block.isMissingNode()&&!block.isNull()) {
            if(!block.isIntegralNumber()||!block.canConvertToLong())fail("CHAIN_TIME_INVALID_EVENT_TIME");
            try {event=Optional.of(Instant.ofEpochSecond(block.longValue()));}catch(RuntimeException invalid){throw new IllegalArgumentException("CHAIN_TIME_INVALID_EVENT_TIME");}
        }
        if(s.sourceTime().isPresent()&&!s.sourceTime().equals(event))fail("CHAIN_TIME_SOURCE_TIME_MISMATCH");
        Optional<Long> index=Optional.empty();var position=root.path("transactionIndex");
        if(!position.isMissingNode()&&!position.isNull()) {if(!position.isIntegralNumber()||!position.canConvertToLong()||position.longValue()<0)fail("CHAIN_TIME_INVALID_INDEX");index=Optional.of(position.longValue());}
        return new Raw(event,index,root);
    }
    private String priceReason(PumpSwapOperations.Operation o,JsonNode root) {
        if(!PumpSwapChainTimeCandles.WSOL.equals(o.quoteMint()))return "UNSUPPORTED_QUOTE";
        if(o.quoteDecimals().isPresent()&&o.quoteDecimals().get()!=9)fail("CHAIN_TIME_DECIMALS_CONFLICT");
        for(var side:List.of("preTokenBalances","postTokenBalances"))for(var row:root.path("meta").path(side)) {
            var d=row.path("uiTokenAmount").path("decimals");
            if(row.path("mint").asText().equals(o.baseMint())&&o.baseDecimals().isPresent()&&(!d.isIntegralNumber()||!d.canConvertToInt()||d.intValue()!=o.baseDecimals().get()))fail("CHAIN_TIME_DECIMALS_CONFLICT");
            if(row.path("mint").asText().equals(o.quoteMint())&&o.quoteDecimals().isPresent()&&(!d.isIntegralNumber()||!d.canConvertToInt()||d.intValue()!=o.quoteDecimals().get()))fail("CHAIN_TIME_DECIMALS_CONFLICT");
        }
        if(o.baseDecimals().isEmpty())return o.quoteDecimals().isEmpty()?"MISSING_BOTH_DECIMALS":"MISSING_BASE_DECIMALS";
        if(o.quoteDecimals().isEmpty())return "MISSING_QUOTE_DECIMALS";
        // Fixed-v4 requires observed pre/post vault endpoints. These retained mint/decimal
        // rows corroborate its immutable same-invocation transfer/lifecycle proof.
        if(!hasMetadata(root,o.baseMint(),o.baseDecimals().get())||!hasMetadata(root,o.quoteMint(),9)||o.transferPaths().isEmpty()||o.eventPath().isEmpty())return "UNPROVED_DECIMALS";
        return "PRICED";
    }
    private boolean hasMetadata(JsonNode root,String mint,int decimals) {
        for(var side:List.of("preTokenBalances","postTokenBalances"))for(var row:root.path("meta").path(side))if(row.path("mint").asText().equals(mint)&&row.path("uiTokenAmount").path("decimals").isIntegralNumber()&&row.path("uiTokenAmount").path("decimals").intValue()==decimals)return true;
        return false;
    }
    private static Activity activity(List<OperationReference> members) {
        int buys=0,priced=0,missing=0;var base=BigInteger.ZERO;var quote=BigInteger.ZERO;boolean provenSol=true;
        for(var r:members){if(r.variant().startsWith("buy"))buys++;if(r.priceReason().equals("PRICED"))priced++;if(r.transactionIndex().isEmpty())missing++;base=base.add(r.baseRawUnits());quote=quote.add(r.quoteRawUnits());if(!PumpSwapChainTimeCandles.WSOL.equals(r.pool().quoteMint())||r.quoteDecimals().isEmpty()||!Set.of("PRICED","MISSING_BASE_DECIMALS").contains(r.priceReason()))provenSol=false;}
        return new Activity(members.size(),buys,members.size()-buys,priced,members.size()-priced,missing,base,quote,provenSol?Optional.of(new BigDecimal(quote).divide(BigDecimal.TEN.pow(9),9,RoundingMode.UNNECESSARY)):Optional.empty(),members.stream().map(OperationReference::revisionKey).toList());
    }
    private static Price price(OperationReference r){return new Price(new Rational(r.quoteRawUnits().multiply(BigInteger.TEN.pow(r.baseDecimals().orElseThrow())),r.baseRawUnits().multiply(BigInteger.TEN.pow(r.quoteDecimals().orElseThrow()))),r);}
    private static int compare(Rational a,Rational b){return a.numerator().multiply(b.denominator()).compareTo(b.numerator().multiply(a.denominator()));}
    private static boolean inside(Instant t,Request q){return !t.isBefore(q.fromInclusive())&&t.isBefore(q.toExclusive());}
    private static Instant micro(Instant t){return Objects.requireNonNull(t).truncatedTo(ChronoUnit.MICROS);}
    private static Instant max(Instant... times){return Arrays.stream(times).map(PumpSwapChainTimeCandleCalculator::micro).max(Comparator.naturalOrder()).orElseThrow();}
    private static int paths(List<Integer>a,List<Integer>b){for(int i=0;i<Math.min(a.size(),b.size());i++){int c=Integer.compare(a.get(i),b.get(i));if(c!=0)return c;}return Integer.compare(a.size(),b.size());}
    private static void fail(String reason){throw new IllegalArgumentException(reason);}
    private static final class Encoding {
        final CanonicalFingerprint hash=new CanonicalFingerprint();long bytes;
        void field(String name,String value){long length=8L+name.getBytes(StandardCharsets.UTF_8).length+value.getBytes(StandardCharsets.UTF_8).length;if(bytes+length+71>64_000_000)fail("CHAIN_TIME_OUTPUT_LIMIT");bytes+=length;hash.field(name,value);}
        void value(String path,Object v){
            if(v instanceof Optional<?>o){field(path,o.isPresent()?"present":"absent");o.ifPresent(x->value(path+"/value",x));}
            else if(v instanceof Map<?,?>m){field(path,"map:"+m.size());var keys=m.keySet().stream().map(Object::toString).sorted(TEXT).toList();for(int i=0;i<keys.size();i++){field(path+"/"+i+"/key",keys.get(i));value(path+"/"+i+"/value",m.get(keys.get(i)));}}
            else if(v instanceof List<?>l){field(path,"list:"+l.size());for(int i=0;i<l.size();i++)value(path+"/"+i,l.get(i));}
            else if(v!=null&&v.getClass().isRecord()){field(path,"record:"+v.getClass().getSimpleName());for(var c:v.getClass().getRecordComponents())try{value(path+"/"+c.getName(),c.getAccessor().invoke(v));}catch(ReflectiveOperationException failure){throw new IllegalStateException("CHAIN_TIME_ENCODING_FAILED",failure);}}
            else field(path,v==null?"null":v instanceof BigDecimal decimal?decimal.toPlainString():v.toString());
        }
    }
}
