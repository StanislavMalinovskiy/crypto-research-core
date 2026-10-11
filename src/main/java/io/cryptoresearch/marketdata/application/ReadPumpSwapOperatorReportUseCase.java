package io.cryptoresearch.marketdata.application;

import org.springframework.stereotype.Service;
import io.cryptoresearch.marketdata.api.PumpSwapOperatorReport;

@Service
public class ReadPumpSwapOperatorReportUseCase {
    private final ReadCaptureMonitorUseCase monitor;
    private final ReadPumpSwapPoolMetricsUseCase metrics;
    private final PumpSwapOperatorReportFormatter formatter;
    public ReadPumpSwapOperatorReportUseCase(ReadCaptureMonitorUseCase monitor,ReadPumpSwapPoolMetricsUseCase metrics,PumpSwapOperatorReportFormatter formatter) {
        this.monitor=monitor;this.metrics=metrics;this.formatter=formatter;
    }
    public PumpSwapOperatorReport.Result read(PumpSwapOperatorReport.Request request) {
        PumpSwapOperatorReport.Request normalized;
        try {normalized=new PumpSwapOperatorReport.Request(java.util.Objects.requireNonNull(request.sessionId()),PumpSwapPoolMetricsCalculator.normalize(request.metrics()));}
        catch(RuntimeException invalid){throw new IllegalArgumentException("OPERATOR_REPORT_INVALID_REQUEST");}
        java.util.Optional<io.cryptoresearch.marketdata.api.PumpSwapCaptureMonitor.Snapshot> capture;
        java.util.Optional<io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics.Result> activity;
        try {capture=monitor.read(normalized.sessionId());activity=metrics.read(normalized.metrics());}
        catch(RuntimeException failure){throw new IllegalStateException("OPERATOR_REPORT_READ_FAILED");}
        return formatter.format(normalized,capture,activity);
    }
}
