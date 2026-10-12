package io.cryptoresearch.signal.application;

import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.signal.api.PumpSwapVersionedNativePriceSignal.*;
import java.util.*;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import io.cryptoresearch.marketdata.api.PumpSwapVersionedPoolCandles;

/** Same adjacent-bar exact rule, applied only to the new comparable pool-net source. */
@Service
public class VersionedNativeSolPriceRiseDiagnostic {
    private final MarketDataApi marketdata;
    public VersionedNativeSolPriceRiseDiagnostic(MarketDataApi marketdata){this.marketdata=marketdata;}
    public Result diagnose(Request request) {
        Instant e,k,d;PumpSwapVersionedPoolCandles.Request query;
        try {
            e=Objects.requireNonNull(request.eventEnd()).truncatedTo(ChronoUnit.MICROS);k=Objects.requireNonNull(request.source().knowledgeCutoff()).truncatedTo(ChronoUnit.MICROS);d=Objects.requireNonNull(request.source().derivationObservedAt()).truncatedTo(ChronoUnit.MICROS);
            if(e.getNano()!=0||Math.floorMod(e.getEpochSecond(),300)!=0||e.isAfter(k)||!"pumpswap-pool-legs-v5".equals(request.source().decoderVersion()))throw new IllegalArgumentException();
            var pool=Objects.requireNonNull(request.pool());if(request.source().pools().stream().noneMatch(p->p.pool().equals(pool.pool())&&p.baseMint().equals(pool.baseMint())&&p.quoteMint().equals(pool.quoteMint())))throw new IllegalArgumentException();
            query=new PumpSwapVersionedPoolCandles.Request(request.source().batch(),request.source().pools(),request.source().decoderVersion(),e.minusSeconds(600),e,d,k);
        }catch(RuntimeException invalid){throw new IllegalArgumentException("VERSIONED_NATIVE_INVALID_REQUEST");}
        var report=marketdata.pumpSwapVersionedPoolCandles(query);
        var expected=new PumpSwapVersionedPoolCandles.Descriptor(query.decoderVersion(),query.pools().stream().sorted(Comparator.comparing(p->p.pool())).toList(),query.fromInclusive(),query.toExclusive(),d,k);
        if(report==null||!report.algorithmVersion().equals("pumpswap-pool-net-candles-v1")||!report.request().equals(expected)
                ||!report.fingerprint().matches("sha256:[0-9a-f]{64}")||!report.selectedInputFingerprint().matches("sha256:[0-9a-f]{64}"))throw new IllegalArgumentException("VERSIONED_NATIVE_SOURCE_MISMATCH");
        var sources=new HashMap<List<String>,io.cryptoresearch.marketdata.api.PumpSwapOperations.RecordedTransaction>();
        for(var source:query.batch().transactions()){var key=List.of(source.chain().value(),source.signature());var previous=sources.putIfAbsent(key,source);if(previous!=null&&!previous.equals(source))throw new IllegalArgumentException("VERSIONED_NATIVE_SOURCE_MISMATCH");}
        if(sources.size()!=report.transactions().size())throw new IllegalArgumentException("VERSIONED_NATIVE_SOURCE_MISMATCH");
        var seen=new HashSet<List<String>>();for(var tx:report.transactions()){var key=List.of(tx.chain(),tx.signature());var source=sources.get(key);if(source==null||!seen.add(key)||!source.rawDigest().equals(tx.rawDigest())||!source.provider().equals(tx.provider())||!source.session().toString().equals(tx.session())||source.slot()!=tx.slot()||!source.sourceReference().equals(tx.sourceReference())||!source.receivedAt().equals(tx.clocks().receivedAt())||!source.admittedAt().equals(tx.clocks().admittedAt())||!source.ingestedAt().equals(tx.clocks().ingestedAt())||!tx.clocks().derivationObservedAt().equals(d))throw new IllegalArgumentException("VERSIONED_NATIVE_SOURCE_MISMATCH");}
        var bars=report.candles().stream().filter(c->c.pool().equals(request.pool())).toList();var prior=bar(bars,e.minusSeconds(600));var current=bar(bars,e.minusSeconds(300));var reasons=new TreeSet<String>();
        adequate(prior,"MISSING_PRIOR_BUCKET",reasons);adequate(current,"MISSING_CURRENT_BUCKET",reasons);
        for(var uncertainty:report.uncertainties())if(uncertainty.pool().equals(request.pool())&&(uncertainty.eventTime().isEmpty()||!uncertainty.eventTime().get().isBefore(e.minusSeconds(600))&&uncertainty.eventTime().get().isBefore(e)))reasons.add(uncertainty.reason());
        Status status=Status.INSUFFICIENT;
        if(reasons.isEmpty()) {
            var p=prior.orElseThrow().ohlc().orElseThrow().close().value();var c=current.orElseThrow().ohlc().orElseThrow().close().value();
            boolean met=c.numerator().multiply(p.denominator()).multiply(BigInteger.valueOf(100))
                .compareTo(p.numerator().multiply(c.denominator()).multiply(BigInteger.valueOf(105)))>=0;
            status=met?Status.TRIGGER:Status.NO_TRIGGER;reasons.add(met?"OBSERVED_CLOSE_RISE_AT_LEAST_5_PERCENT":"OBSERVED_CLOSE_RISE_BELOW_5_PERCENT");
        }
        var analysis=k.isAfter(e)?"RETROSPECTIVE_AT_K":"AT_E_UNDER_CONSERVATIVE_RULE";var availability=d.isAfter(e)||report.operations().stream().anyMatch(o->o.pool().equals(request.pool())&&!o.disposition().equals("OUTSIDE_EVENT_WINDOW")&&o.clocks().knownAt().isAfter(e))?"UNAVAILABLE":"AVAILABLE_UNDER_CONSERVATIVE_RULE_NOT_ADMITTED";
        var hash=new SignalFingerprint().field("contract","native-sol-pool-net-price-rise-v1").field("canonicalization","length-prefixed-v1").field("configuration","adjacent-300s-min1-priced-inclusive-105-over-100-v1")
            .field("source",report.selectedInputFingerprint()).field("candles",report.fingerprint()).field("chain",request.pool().chain()).field("pool",request.pool().pool()).field("base",request.pool().baseMint()).field("quote",request.pool().quoteMint())
            .field("E",e.toString()).field("K",k.toString()).field("derivation",d.toString()).field("status",status.name()).field("analysis",analysis).field("historicalAtE",availability).field("readiness","DIAGNOSTIC_ONLY_NOT_ADMITTED");for(var reason:reasons)hash.field("reason",reason);
        return new Result("native-sol-pool-net-price-rise-v1","adjacent-300s-min1-priced-inclusive-105-over-100-v1",request.pool(),e,k,d,report.selectedInputFingerprint(),status,List.copyOf(reasons),report,prior,current,analysis,availability,"DIAGNOSTIC_ONLY_NOT_ADMITTED",hash.finish());
    }
    private static Optional<PumpSwapVersionedPoolCandles.Candle> bar(List<PumpSwapVersionedPoolCandles.Candle> bars,Instant start){var found=bars.stream().filter(c->c.fromInclusive().equals(start)&&c.toExclusive().equals(start.plusSeconds(300))).toList();if(found.size()>1)throw new IllegalArgumentException("VERSIONED_NATIVE_SOURCE_MISMATCH");return found.stream().findFirst();}
    private static void adequate(Optional<PumpSwapVersionedPoolCandles.Candle> bar,String missing,Set<String> reasons){if(bar.isEmpty()){reasons.add(missing);return;}var candle=bar.get();if(candle.ohlc().isEmpty()||candle.pricedCount()<1)reasons.add("MISSING_PRICE");if(candle.unpricedCount()>0)reasons.add("UNPRICED_OPERATION");if(candle.missingIndexCount()>0)reasons.add("MISSING_TRANSACTION_INDEX");}
}
