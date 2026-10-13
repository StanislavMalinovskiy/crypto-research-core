package io.cryptoresearch.signal.api;

import java.time.Instant;
import java.util.List;
import io.cryptoresearch.marketdata.api.PumpSwapRetainedWindow;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics;

public final class PumpSwapRetainedNativePriceSignal {
    private PumpSwapRetainedNativePriceSignal() { }
    public record Request(PumpSwapRetainedWindow.Request source,PumpSwapPoolMetrics.Pool pool,Instant eventEnd) { }
    public record Result(String version,PumpSwapRetainedWindow.Request request,String configurationDigest,
            Instant readObservedAt,Instant derivationObservedAt,PumpSwapRetainedWindow.Manifest manifest,
            String sourceFingerprint,PumpSwapVersionedNativePriceSignal.Result pure,
            PumpSwapVersionedNativePriceSignal.Status effectiveStatus,List<String> reasons,List<String> limitations,String fingerprint) {
        public Result {reasons=List.copyOf(reasons);limitations=List.copyOf(limitations);}
    }
}
