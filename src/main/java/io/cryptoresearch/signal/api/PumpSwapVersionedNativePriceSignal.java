package io.cryptoresearch.signal.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import io.cryptoresearch.marketdata.api.PumpSwapVersionedPoolCandles;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics.Pool;

public final class PumpSwapVersionedNativePriceSignal {
    private PumpSwapVersionedNativePriceSignal() { }
    public enum Status { TRIGGER,NO_TRIGGER,INSUFFICIENT }
    public record Request(PumpSwapVersionedPoolCandles.Request source,Pool pool,Instant eventEnd) { }
    public record Result(String algorithmVersion,String configurationVersion,Pool pool,Instant eventEnd,
            Instant decisionKnowledgeAt,Instant derivationObservedAt,String selectedInputFingerprint,
            Status status,List<String> reasons,PumpSwapVersionedPoolCandles.Result candles,
            Optional<PumpSwapVersionedPoolCandles.Candle> prior,Optional<PumpSwapVersionedPoolCandles.Candle> current,
            String analysis,String historicalAtEventEnd,String readiness,String fingerprint) {
        public Result {reasons=List.copyOf(reasons);}
    }
}
