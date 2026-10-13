package io.cryptoresearch.signal.infrastructure.web;

import static org.assertj.core.api.Assertions.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.signal.api.*;
import tools.jackson.databind.json.JsonMapper;

class PumpSwapOperatorProjectionTest {
    static final Instant TIME = Instant.parse("2026-10-13T00:00:00Z");
    static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final PumpSwapPoolMetrics.Pool POOL = new PumpSwapPoolMetrics.Pool("solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp", "pool", "base", "quote");
    static PumpSwapCaptureMonitor.Snapshot monitor(String reason) {
        long large = 9007199254740993L;
        return new PumpSwapCaptureMonitor.Snapshot(SESSION,"configuration-digest","RUNNING",reason,TIME,TIME.minusSeconds(1),TIME.plusSeconds(1),TIME.plusSeconds(1),true,true,
            new PumpSwapCaptureMonitor.Counters(large,2,3,4,5,6,7,8,9,10,11),List.of(new PumpSwapCaptureMonitor.DispositionCount("PENDING",large)),List.of(new PumpSwapCaptureMonitor.PoolPosition("pool",Optional.of(new PumpSwapCaptureMonitor.Position("signature",large)))),
            List.of(new PumpSwapCaptureMonitor.GapCount("pool","STATE","UNRESOLVED",large)),large,true,List.of(new PumpSwapCapture.Gap(large,"pool","STATE",reason,large,null,TIME,null,null,"UNRESOLVED")),true,"UNVERIFIED","UNVERIFIED","UNVERIFIED","UNKNOWN","UNKNOWN");
    }
    static PumpSwapRetainedNativePriceSignal.Result diagnostic() {
        var rational = new PumpSwapPoolMetrics.Rational(new BigInteger("90071992547409931234567891"),BigInteger.valueOf(17));
        var price = new PumpSwapVersionedPoolCandles.Price(rational,"original-operation");
        var candle = new PumpSwapVersionedPoolCandles.Candle(POOL,TIME.minusSeconds(300),TIME,1,1,0,1,0,0,new BigInteger("90071992547409931234567891"),Optional.of(BigInteger.TEN),List.of("original-operation"),Optional.of(new PumpSwapVersionedPoolCandles.Ohlc(price,price,price,price)));
        var descriptor = new PumpSwapVersionedPoolCandles.Descriptor("pumpswap-pool-legs-v5",List.of(),TIME.minusSeconds(600),TIME,TIME,TIME);
        var candles = new PumpSwapVersionedPoolCandles.Result("candle-version","canonical-version",descriptor,"selected-input",Map.of("do-not-export","SECRET_PIN_CONTENT"),List.of(),List.of(),List.of(candle),List.of(),new PumpSwapVersionedPoolCandles.Reconciliation(1,1,1,0,1,0,Map.of("DECODED",1)),List.of("COVERAGE_UNKNOWN"),"candle-fingerprint");
        var pure = new PumpSwapVersionedNativePriceSignal.Result("pure-version","rule-version",POOL,TIME,TIME,TIME,"selected-input",PumpSwapVersionedNativePriceSignal.Status.TRIGGER,List.of("PURE_REASON"),candles,Optional.of(candle),Optional.of(candle),"RETROSPECTIVE_AT_K","UNAVAILABLE","DIAGNOSTIC_ONLY_NOT_ADMITTED","pure-fingerprint");
        var manifest = new PumpSwapRetainedWindow.Manifest(List.of(),List.of(),List.of(new PumpSwapRetainedWindow.Gap(1,"pool","STATE","DISCONNECT",Optional.empty(),Optional.empty(),TIME,Optional.empty(),Optional.empty(),"UNRESOLVED")),Map.of("PENDING",1L),1,23,"manifest-fingerprint",List.of("CAPTURE_COVERAGE_UNKNOWN"));
        return new PumpSwapRetainedNativePriceSignal.Result("bridge-version",new PumpSwapRetainedWindow.Request(SESSION,TIME.minusSeconds(7),TIME,TIME.minusSeconds(600),TIME),"configuration-digest",TIME.minusSeconds(1),TIME,manifest,"source-fingerprint",pure,PumpSwapVersionedNativePriceSignal.Status.INSUFFICIENT,List.of("UNRESOLVED_STATE_GAPS:1"),List.of("DIAGNOSTIC_ONLY_NOT_ADMITTED"),"wrapper-fingerprint");
    }
    @Test void exactLargeIntegersAndRationalsRemainStringsAndEffectiveStatusIsPrimary() {
        var projection = new PumpSwapOperatorProjection();
        var monitor = JsonMapper.builder().build().readTree(projection.encode(projection.monitor(SESSION,Optional.of(monitor("<script>literal Ж</script>")))));
        assertThat(monitor.path("monitor").path("counters").path("requests").asText()).isEqualTo("9007199254740993");
        assertThat(monitor.path("monitor").path("counters").path("requests").isString()).isTrue();
        assertThat(monitor.path("monitor").path("reason").asText()).isEqualTo("<script>literal Ж</script>");
        var text = new String(projection.encode(projection.diagnostic(diagnostic())),java.nio.charset.StandardCharsets.UTF_8);
        var result = JsonMapper.builder().build().readTree(text);
        assertThat(result.path("diagnostic").path("effectiveStatus").asText()).isEqualTo("INSUFFICIENT");
        assertThat(result.path("diagnostic").path("pureStatus").asText()).isEqualTo("TRIGGER");
        assertThat(result.path("diagnostic").path("candles").path(0).path("baseAmount").asText()).isEqualTo("90071992547409931234567891");
        assertThat(result.path("diagnostic").path("candles").path(0).path("ohlc").path("close").path("value").path("denominator").asText()).isEqualTo("17");
        assertThat(text).doesNotContain("SECRET_PIN_CONTENT","pinnedSourceHashes","rawResult","notification","sourceReference","leaseHolder");
    }
    @Test void QualifiedErrorsAndAbsenceNeverInventZero() {
        var projection = new PumpSwapOperatorProjection();
        var failure = new PumpSwapRetainedWindow.Diagnostic(PumpSwapRetainedWindow.Observation.atLeast(4097),PumpSwapRetainedWindow.Observation.unknown());
        var n = JsonMapper.builder().build().readTree(projection.encode(projection.error("RETAINED_READ_FAILURE",failure)));
        assertThat(n.path("observations").path("signatures").path("value").asText()).isEqualTo("4097");
        assertThat(n.path("observations").path("signatures").path("qualification").asText()).isEqualTo("AT_LEAST");
        assertThat(n.path("observations").path("sourceProofBytes").path("value").isNull()).isTrue();
        assertThat(n.path("observations").path("sourceProofBytes").path("qualification").asText()).isEqualTo("UNKNOWN");
        assertThat(projection.monitor(SESSION,Optional.empty())).containsEntry("status","MONITOR_ABSENT").containsEntry("monitor",null);
    }
    @Test void StringAndCompleteUtf8OutputBoundsRejectWithoutClipping() {
        var projection = new PumpSwapOperatorProjection();
        assertThatThrownBy(() -> projection.encode(projection.monitor(SESSION,Optional.of(monitor("Ж".repeat(2049)))))).hasMessage("OUTPUT_LIMIT");
        var many = Collections.nCopies(2100,"x".repeat(4096));
        assertThatThrownBy(() -> projection.encode(Map.of("boundedTestValues",many))).hasMessage("OUTPUT_LIMIT");
    }
}
