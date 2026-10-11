package io.cryptoresearch.signal.application;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.PumpSwapChainTimeCandles;
import io.cryptoresearch.signal.api.PumpSwapNativePriceSignal.*;

@Service
public class NativeSolPriceRiseDiagnostic {
    private final MarketDataApi marketdata;
    public NativeSolPriceRiseDiagnostic(MarketDataApi marketdata) { this.marketdata=marketdata; }
    public Result diagnose(Request input) {
        var request=normalize(input);var candleRequest=new PumpSwapChainTimeCandles.Request(request.batchKey(),request.eventEnd().minusSeconds(600),request.eventEnd(),request.knowledgeCutoff());
        var source=marketdata.pumpSwapChainTimeCandles(candleRequest);var reasons=new TreeSet<String>();
        Optional<PumpSwapChainTimeCandles.Candle> prior=Optional.empty(),current=Optional.empty();String availability="UNAVAILABLE";
        if(source.isEmpty())reasons.add("UNKNOWN_BATCH");
        else {
            var report=source.get();if(!report.request().equals(candleRequest)||!report.source().batchKey().equals(request.batchKey())||!"pumpswap-chain-time-candles-v1".equals(report.algorithmVersion()))throw new IllegalArgumentException("NATIVE_DIAGNOSTIC_SOURCE_MISMATCH");
            var bars=report.candles().stream().filter(c->c.pool().equals(request.pool())).toList();
            prior=bar(bars,request.eventEnd().minusSeconds(600));current=bar(bars,request.eventEnd().minusSeconds(300));
            check(prior,"MISSING_PRIOR_BUCKET",reasons);check(current,"MISSING_CURRENT_BUCKET",reasons);
            for(var op:report.operations())if(op.pool().equals(request.pool())) {
                if(op.disposition().equals("MISSING_EVENT_TIME"))reasons.add("MISSING_EVENT_TIME");
                if(op.disposition().equals("FUTURE_KNOWLEDGE"))reasons.add("FUTURE_KNOWLEDGE");
            }
            boolean late=report.batchPersistedAt().isAfter(request.eventEnd())||report.operations().stream().filter(o->o.pool().equals(request.pool())&&!o.disposition().equals("OUTSIDE_EVENT_WINDOW")).anyMatch(o->o.clocks().knownAt().isAfter(request.eventEnd()));
            availability=late?"UNAVAILABLE":"AVAILABLE_UNDER_CONSERVATIVE_RULE_NOT_ADMITTED";
        }
        Status status=Status.INSUFFICIENT;
        if(reasons.isEmpty()) {
            var p=prior.orElseThrow().ohlc().orElseThrow().close().value();var c=current.orElseThrow().ohlc().orElseThrow().close().value();
            // c/p >= 105/100; no rendered decimal enters the decision.
            boolean met=c.numerator().multiply(p.denominator()).multiply(BigInteger.valueOf(100))
                .compareTo(p.numerator().multiply(c.denominator()).multiply(BigInteger.valueOf(105)))>=0;
            status=met?Status.TRIGGER:Status.NO_TRIGGER;reasons.add(met?"OBSERVED_CLOSE_RISE_AT_LEAST_5_PERCENT":"OBSERVED_CLOSE_RISE_BELOW_5_PERCENT");
        }
        var analysis=request.knowledgeCutoff().isAfter(request.eventEnd())?"RETROSPECTIVE_AT_K":"AT_E_UNDER_CONSERVATIVE_RULE";
        var identity=new SignalFingerprint().field("contract","native-sol-price-rise-v1").field("canonicalization","length-prefixed-v1")
            .field("configuration","adjacent-300s-min1-priced-inclusive-105-over-100-v1").field("batch",request.batchKey())
            .field("chain",request.pool().chain()).field("pool",request.pool().pool()).field("base",request.pool().baseMint()).field("quote",request.pool().quoteMint())
            .field("E",request.eventEnd().toString()).field("K",request.knowledgeCutoff().toString()).field("candles",source.map(PumpSwapChainTimeCandles.Result::fingerprint).orElse("absent"))
            .field("status",status.name()).field("analysis",analysis).field("historicalAtE",availability).field("readiness","DIAGNOSTIC_ONLY_NOT_ADMITTED");
        for(var reason:reasons)identity.field("reason",reason);
        return new Result("native-sol-price-rise-v1","adjacent-300s-min1-priced-inclusive-105-over-100-v1",request,request.knowledgeCutoff(),status,List.copyOf(reasons),source,prior,current,analysis,availability,"DIAGNOSTIC_ONLY_NOT_ADMITTED",identity.finish());
    }
    private static Request normalize(Request r) {
        try {
            var e=Objects.requireNonNull(r.eventEnd()).truncatedTo(ChronoUnit.MICROS);var k=Objects.requireNonNull(r.knowledgeCutoff()).truncatedTo(ChronoUnit.MICROS);
            if(r.batchKey()==null||!r.batchKey().matches("sha256:[0-9a-f]{64}")||e.getNano()!=0||Math.floorMod(e.getEpochSecond(),300)!=0||e.isAfter(k))throw new IllegalArgumentException();
            e.minusSeconds(600);var pool=Objects.requireNonNull(r.pool());
            for(var field:List.of(pool.chain(),pool.pool(),pool.baseMint(),pool.quoteMint()))if(field.isBlank())throw new IllegalArgumentException();
            return new Request(r.batchKey(),pool,e,k);
        }catch(RuntimeException invalid){throw new IllegalArgumentException("NATIVE_DIAGNOSTIC_INVALID_REQUEST");}
    }
    private static Optional<PumpSwapChainTimeCandles.Candle> bar(List<PumpSwapChainTimeCandles.Candle> bars,Instant start) {
        var matching=bars.stream().filter(c->c.fromInclusive().equals(start)&&c.toExclusive().equals(start.plusSeconds(300))).toList();
        if(matching.size()>1)throw new IllegalArgumentException("NATIVE_DIAGNOSTIC_SOURCE_MISMATCH");return matching.stream().findFirst();
    }
    private static void check(Optional<PumpSwapChainTimeCandles.Candle> candle,String missing,Set<String> reasons) {
        if(candle.isEmpty()){reasons.add(missing);return;}var c=candle.get();
        if(c.ohlc().isEmpty()||c.activity().priceEligibleCount()<1)reasons.add("MISSING_PRICE");
        if(c.activity().unpricedCount()>0)reasons.add("UNPRICED_OPERATION");
        if(c.activity().missingIndexCount()>0)reasons.add("MISSING_TRANSACTION_INDEX");
    }
}
