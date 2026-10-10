package io.cryptoresearch.marketdata.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Committed diagnostic facts, never process health, completeness or research admission. */
public final class PumpSwapCaptureMonitor {
    private PumpSwapCaptureMonitor() { }
    public record Counters(long requests,long receivedBytes,long subscriptionBytes,long retainedBytes,
            long rpcHundredths,long reservedHundredths,long transactions,long states,long duplicates,long conflicts,long failures) { }
    public record DispositionCount(String status,long count) { }
    public record Position(String signature,long slot) { }
    public record PoolPosition(String pool,Optional<Position> position) {
        public PoolPosition { position=java.util.Objects.requireNonNull(position); }
    }
    public record GapCount(String pool,String kind,String status,long count) { }
    public record Snapshot(UUID sessionId,String configurationDigest,String recordedState,String reason,
            Instant observedAt,Instant startedAt,Instant endsAt,Instant lockedUntil,boolean leaseValid,boolean windowOpen,
            Counters counters,List<DispositionCount> dispositionCounts,List<PoolPosition> committedPositions,
            List<GapCount> unresolvedCounts,long totalUnresolved,boolean inconsistentGapStatus,
            List<PumpSwapCapture.Gap> intervals,boolean detailsTruncated,
            String processActivity,String transportActivity,String coverage,String unreceivedLoss,String actualBilling) {
        public Snapshot {
            dispositionCounts=List.copyOf(dispositionCounts);committedPositions=List.copyOf(committedPositions);
            unresolvedCounts=List.copyOf(unresolvedCounts);intervals=List.copyOf(intervals);
        }
    }
}
