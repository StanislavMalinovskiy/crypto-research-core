package io.cryptoresearch.signal.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import io.cryptoresearch.marketdata.api.PumpSwapChainTimeCandles;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics.Pool;

/** Separate native pattern evidence with no accepted-signal identity. */
public final class PumpSwapNativePriceSignal {
    private PumpSwapNativePriceSignal() {}
    public enum Status { TRIGGER, NO_TRIGGER, INSUFFICIENT }
    public record Request(String batchKey, Pool pool, Instant eventEnd, Instant knowledgeCutoff) {}
    public record Result(String algorithmVersion, String configurationVersion, Request request,
            Instant decisionKnowledgeAt, Status status, List<String> reasons,
            Optional<PumpSwapChainTimeCandles.Result> candles, Optional<PumpSwapChainTimeCandles.Candle> prior,
            Optional<PumpSwapChainTimeCandles.Candle> current, String analysis,
            String historicalAtEventEnd, String readiness, String fingerprint) {
        public Result { reasons=List.copyOf(reasons); }
    }
}
