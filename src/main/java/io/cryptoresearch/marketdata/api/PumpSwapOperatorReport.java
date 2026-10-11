package io.cryptoresearch.marketdata.api;

import java.util.UUID;

/** Read-only packaging of independently requested diagnostic panels; never a lineage or admission claim. */
public final class PumpSwapOperatorReport {
    private PumpSwapOperatorReport() {}
    public record Request(UUID sessionId, PumpSwapPoolMetrics.Request metrics) {}
    public record Result(String formatVersion, Request request, String association, String consistency,
            String monitorStatus, String metricsStatus, String json, String summary) {}
}
