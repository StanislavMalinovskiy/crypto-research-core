package io.cryptoresearch.marketdata.application;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;
import io.cryptoresearch.marketdata.api.PumpSwapOperationPersistence;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics.*;

@Component
public class PumpSwapPoolMetricsCalculator {
    private static final String VERSION = "pumpswap-pool-metrics-v1";
    private static final Comparator<String> TEXT = (a,b) -> Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    private static final Comparator<Pool> POOL = Comparator.comparing(Pool::chain,TEXT).thenComparing(Pool::pool,TEXT).thenComparing(Pool::baseMint,TEXT).thenComparing(Pool::quoteMint,TEXT);
    private static final Comparator<OperationReference> ORDER = Comparator.comparing(OperationReference::receivedAt)
        .thenComparing(OperationReference::chain,TEXT).thenComparing(OperationReference::signature,TEXT)
        .thenComparingInt(r->r.position().outerIndex()).thenComparing(r->r.position().innerPath(),PumpSwapPoolMetricsCalculator::paths)
        .thenComparingInt(r->r.position().legOrdinal()).thenComparing(OperationReference::revisionKey,TEXT);
    private final PumpSwapOperationCanonicalizer canonicalizer = new PumpSwapOperationCanonicalizer();
    static Request normalize(Request r) {
        try {
            Objects.requireNonNull(r);Objects.requireNonNull(r.fromInclusive());Objects.requireNonNull(r.toExclusive());
            var cutoff=Objects.requireNonNull(r.observationCutoff()).truncatedTo(ChronoUnit.MICROS);
            if(r.batchKey()==null || !r.batchKey().matches("sha256:[0-9a-f]{64}") || !Set.of(60,300,3600).contains(r.intervalSeconds())
                || r.fromInclusive().getNano()!=0 || r.toExclusive().getNano()!=0
                || Math.floorMod(r.fromInclusive().getEpochSecond(),r.intervalSeconds())!=0 || Math.floorMod(r.toExclusive().getEpochSecond(),r.intervalSeconds())!=0
                || !r.fromInclusive().isBefore(r.toExclusive()) || r.toExclusive().isAfter(cutoff)) throw new IllegalArgumentException();
            long width=Math.subtractExact(r.toExclusive().getEpochSecond(),r.fromInclusive().getEpochSecond());
            if(width/r.intervalSeconds()>1000)throw new IllegalArgumentException();
            return new Request(r.batchKey(),r.fromInclusive(),r.toExclusive(),cutoff,r.intervalSeconds());
        } catch(RuntimeException invalid) { throw new IllegalArgumentException("METRICS_INVALID_REQUEST"); }
    }
    Result calculate(Request input, PumpSwapOperationPersistence.StoredBatch batch) {
        var request=normalize(input);var result=batch.result();
        if(!request.batchKey().equals(batch.batchKey()))fail("METRICS_SOURCE_MISMATCH");
        if(!"pumpswap-storage-v1".equals(batch.storageVersion()) || !"pumpswap-operations-v4".equals(result.decoderVersion())
            || !"may18-oct07-endpoint-proof-v4".equals(result.schemaVersion()) || !"length-prefixed-v1".equals(result.canonicalizationVersion()))fail("METRICS_UNSUPPORTED_SOURCE_VERSION");
        if(result.transactions().size()>128 || batch.transactions().size()>128 || result.operations().size()>10000 || batch.operations().size()>10000)fail("METRICS_LIMIT");
        var parents=new HashMap<String,PumpSwapOperations.TransactionResult>();
        for(var tx:result.transactions())if(parents.put(canonicalizer.transactionKey(result,tx),tx)!=null)fail("METRICS_SOURCE_MISMATCH");
        var revisions=new HashMap<String,PumpSwapOperationPersistence.TransactionRevision>();
        for(var tx:batch.transactions())if(revisions.put(tx.revisionKey(),tx)!=null)fail("METRICS_SOURCE_MISMATCH");
        if(!parents.keySet().equals(revisions.keySet()) || batch.operations().size()!=result.operations().size())fail("METRICS_SOURCE_MISMATCH");
        var canonicalRevisions=new HashMap<String,String>();var seen=new HashSet<String>();var refs=new ArrayList<OperationReference>();
        var poolsByRevision=new HashMap<String,Pool>();var actualMembership=new HashMap<String,Set<String>>();
        for(int n=0;n<result.operations().size();n++) {
            var op=result.operations().get(n);var rev=batch.operations().get(n);var canonical=canonicalizer.operationKey(op);
            var old=canonicalRevisions.putIfAbsent(canonical,rev.revisionKey());
            if(old!=null && !old.equals(rev.revisionKey()))fail("METRICS_AMBIGUOUS_OPERATION_REVISION");
            var parent=parents.get(rev.transactionRevisionKey());
            if(parent==null || !seen.add(rev.revisionKey()) || !canonical.equals(rev.canonicalKey())
                || !canonicalizer.operationRevision(canonical,rev.transactionRevisionKey()).equals(rev.revisionKey())
                || !op.chain().equals(parent.source().chain()) || !op.signature().equals(parent.source().signature())
                || !op.rawDigest().equals(parent.source().rawDigest()) || !op.sourceReference().equals(parent.source().sourceReference())
                || !Set.of(PumpSwapOperations.Status.DECODED,PumpSwapOperations.Status.PARTIAL).contains(parent.status()))fail("METRICS_SOURCE_MISMATCH");
            if(!Set.of("buy","buy_exact_quote_in","buy_v2","buy_exact_quote_in_v2","sell","sell_v2").contains(op.variant())
                || op.baseAmount().signum()<=0 || op.quoteAmount().signum()<=0 || op.position().outerIndex()<0 || op.position().legOrdinal()<0
                || op.position().innerPath().stream().anyMatch(i->i<0))fail("METRICS_INVALID_OPERATION");
            for(var d:List.of(op.baseDecimals(),op.quoteDecimals()))if(d.isPresent() && (d.get()<0 || d.get()>255))fail("METRICS_INVALID_OPERATION");
            actualMembership.computeIfAbsent(rev.transactionRevisionKey(),k->new HashSet<>()).add(rev.revisionKey());
            var ref=new OperationReference(canonical,rev.revisionKey(),rev.transactionRevisionKey(),rev.contentDigest(),op.chain().value(),op.signature(),op.position(),parent.source().receivedAt(),
                op.variant(),op.trader(),op.baseAmount(),op.quoteAmount(),op.baseDecimals(),op.quoteDecimals(),new FeeFacts(op.eventDeclaredFees(),op.attributedFees(),op.eventFacts(),op.feeStatus(),op.feeReasons()));
            refs.add(ref);poolsByRevision.put(ref.revisionKey(),new Pool(op.chain().value(),op.pool(),op.baseMint(),op.quoteMint()));
        }
        for(var tx:batch.transactions())if(tx.operationRevisionKeys().size()!=new HashSet<>(tx.operationRevisionKeys()).size()
            || !new HashSet<>(tx.operationRevisionKeys()).equals(actualMembership.getOrDefault(tx.revisionKey(),Set.of())))fail("METRICS_SOURCE_MISMATCH");
        refs.sort(ORDER);var selected=refs.stream().filter(r->inside(r.receivedAt(),request)).toList();var outside=refs.stream().filter(r->!inside(r.receivedAt(),request)).toList();
        var grouped=new TreeMap<Pool,List<OperationReference>>(POOL);var decimals=new HashMap<Pool,Integer[]>();
        for(var r:selected) {
            var pool=poolsByRevision.get(r.revisionKey());grouped.computeIfAbsent(pool,k->new ArrayList<>()).add(r);
            var known=decimals.computeIfAbsent(pool,k->new Integer[2]);var observed=List.of(r.baseDecimals(),r.quoteDecimals());
            for(int i=0;i<2;i++)if(observed.get(i).isPresent()) {int d=observed.get(i).get();if(known[i]!=null && known[i]!=d)fail("METRICS_DECIMALS_CONFLICT");known[i]=d;}
        }
        var totals=new ArrayList<PoolTotal>();var candles=new ArrayList<Candle>();
        for(var entry:grouped.entrySet()) {
            totals.add(new PoolTotal(entry.getKey(),activity(entry.getValue())));var buckets=new TreeMap<Instant,List<OperationReference>>();
            for(var r:entry.getValue()) {var start=Instant.ofEpochSecond(Math.multiplyExact(Math.floorDiv(r.receivedAt().getEpochSecond(),request.intervalSeconds()),request.intervalSeconds()));buckets.computeIfAbsent(start,k->new ArrayList<>()).add(r);}
            for(var bucket:buckets.entrySet()) {
                if(candles.size()>=10000)fail("METRICS_LIMIT");var prices=bucket.getValue().stream().filter(PumpSwapPoolMetricsCalculator::eligible).map(PumpSwapPoolMetricsCalculator::price).toList();Optional<Ohlc> ohlc=Optional.empty();
                if(!prices.isEmpty()) {var high=prices.getFirst();var low=high;for(var p:prices){if(compare(p.value(),high.value())>0)high=p;if(compare(p.value(),low.value())<0)low=p;}ohlc=Optional.of(new Ohlc(prices.getFirst(),high,low,prices.getLast()));}
                candles.add(new Candle(entry.getKey(),bucket.getKey(),bucket.getKey().plusSeconds(request.intervalSeconds()),activity(bucket.getValue()),ohlc));
            }
        }
        var txs=new ArrayList<TransactionDisposition>();var statuses=new EnumMap<PumpSwapOperations.Status,Integer>(PumpSwapOperations.Status.class);
        for(var entry:parents.entrySet()) {
            var tx=entry.getValue();var s=tx.source();var rev=revisions.get(entry.getKey());statuses.merge(tx.status(),1,Integer::sum);
            txs.add(new TransactionDisposition(rev.revisionKey(),rev.contentDigest(),s.chain().value(),s.signature(),s.provider(),s.slot(),s.sourceTime(),s.receivedAt(),s.admittedAt(),s.ingestedAt(),tx.status(),tx.reason(),tx.networkFee(),inside(s.receivedAt(),request),rev.operationRevisionKeys().stream().sorted(TEXT).toList()));
        }
        txs.sort(Comparator.comparing(TransactionDisposition::chain,TEXT).thenComparing(TransactionDisposition::signature,TEXT).thenComparing(TransactionDisposition::revisionKey,TEXT));
        int in=(int)txs.stream().filter(TransactionDisposition::insideWindow).count();var all=activity(selected);
        var reconciliation=new Reconciliation(txs.size(),in,txs.size()-in,refs.size(),selected.size(),outside.size(),all.buyCount(),all.sellCount(),all.priceEligibleCount(),all.unpricedCount(),Arrays.stream(PumpSwapOperations.Status.values()).map(s->new StatusCount(s,statuses.getOrDefault(s,0))).toList());
        var source=new Source(batch.batchKey(),batch.storageVersion(),result.decoderVersion(),result.schemaVersion(),result.canonicalizationVersion(),result.fingerprint(),result.pinnedSourceHashes(),result.limitations().stream().distinct().sorted(TEXT).toList());
        var interpretation=new Interpretation("HISTORICAL_DIAGNOSTIC","OBSERVED_BATCH_ONLY","INCOMPLETE","UNKNOWN","INCOMPLETE","NOT_ESTABLISHED","SPARSE_POPULATED_ONLY_NO_CARRY_FORWARD","DETERMINISTIC_NOT_ONCHAIN_CHRONOLOGY","HISTORICAL_AVAILABILITY_NOT_ESTABLISHED");
        var report=new Result(VERSION,"length-prefixed-v1",request,source,totals,candles,txs,selected,outside,reconciliation,interpretation,"");var encoding=new Encoding();encoding.value("report",report);
        return new Result(VERSION,"length-prefixed-v1",request,source,totals,candles,txs,selected,outside,reconciliation,interpretation,encoding.hash.finish());
    }
    private static boolean inside(Instant t,Request r){return !t.isBefore(r.fromInclusive()) && t.isBefore(r.toExclusive()) && !t.isAfter(r.observationCutoff());}
    private static boolean eligible(OperationReference r){return r.baseDecimals().isPresent() && r.quoteDecimals().isPresent();}
    private static Price price(OperationReference r){return new Price(new Rational(r.quoteRawUnits().multiply(BigInteger.TEN.pow(r.baseDecimals().orElseThrow())),r.baseRawUnits().multiply(BigInteger.TEN.pow(r.quoteDecimals().orElseThrow()))),r);}
    private static int compare(Rational a,Rational b){return a.numerator().multiply(b.denominator()).compareTo(b.numerator().multiply(a.denominator()));}
    private static Activity activity(List<OperationReference> refs){
        int buys=0;var base=BigInteger.ZERO;var quote=BigInteger.ZERO;var unpriced=new ArrayList<UnpricedOperation>();
        for(var r:refs){if(r.variant().startsWith("buy"))buys++;base=base.add(r.baseRawUnits());quote=quote.add(r.quoteRawUnits());if(!eligible(r))unpriced.add(new UnpricedOperation(r.revisionKey(),r.baseDecimals().isEmpty()?(r.quoteDecimals().isEmpty()?"MISSING_BOTH_DECIMALS":"MISSING_BASE_DECIMALS"):"MISSING_QUOTE_DECIMALS"));}
        return new Activity(refs.size(),buys,refs.size()-buys,refs.size()-unpriced.size(),unpriced.size(),base,quote,refs.stream().map(OperationReference::revisionKey).toList(),unpriced);
    }
    private static int paths(List<Integer>a,List<Integer>b){for(int i=0;i<Math.min(a.size(),b.size());i++){int c=Integer.compare(a.get(i),b.get(i));if(c!=0)return c;}return Integer.compare(a.size(),b.size());}
    private static void fail(String reason){throw new IllegalArgumentException(reason);}
    private static final class Encoding {
        final CanonicalFingerprint hash=new CanonicalFingerprint();long bytes;
        void field(String name,String value){long length=8L+name.getBytes(StandardCharsets.UTF_8).length+value.getBytes(StandardCharsets.UTF_8).length;if(bytes+length+71>64_000_000)fail("METRICS_OUTPUT_LIMIT");bytes+=length;hash.field(name,value);}
        void value(String path,Object v){
            if(v instanceof Optional<?>o){field(path,o.isPresent()?"present":"absent");o.ifPresent(x->value(path+"/value",x));}
            else if(v instanceof Map<?,?>m){field(path,"map:"+m.size());var keys=m.keySet().stream().map(Object::toString).sorted(TEXT).toList();for(int i=0;i<keys.size();i++){field(path+"/"+i+"/key",keys.get(i));value(path+"/"+i+"/value",m.get(keys.get(i)));}}
            else if(v instanceof List<?>l){field(path,"list:"+l.size());for(int i=0;i<l.size();i++)value(path+"/"+i,l.get(i));}
            else if(v!=null && v.getClass().isRecord()){field(path,"record:"+v.getClass().getSimpleName());for(var c:v.getClass().getRecordComponents())try{value(path+"/"+c.getName(),c.getAccessor().invoke(v));}catch(ReflectiveOperationException failure){throw new IllegalStateException("METRICS_ENCODING_FAILED",failure);}}
            else field(path,v==null?"null":v.toString());
        }
    }
}
