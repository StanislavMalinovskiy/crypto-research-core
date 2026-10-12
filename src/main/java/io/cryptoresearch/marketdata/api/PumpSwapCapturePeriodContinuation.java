package io.cryptoresearch.marketdata.api;

import java.time.Instant;

/** Explicit separate-period enrollment, not an automatic calendar reset. */
public record PumpSwapCapturePeriodContinuation(String version,String predecessorDeploymentKey,String predecessorConfigurationDigest,
        Instant periodStart,PumpSwapContinuousCapture.Configuration successor,String ownerDecision,Baseline baseline) {
    public PumpSwapCapturePeriodContinuation {
        if(periodStart!=null)periodStart=periodStart.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public record Baseline(String source,Instant observedAt,Long openingHundredths,Long otherActivityHundredths) {
        public Baseline {
            if(source==null || !java.util.Set.of("OWNER_REPORTED","INDEPENDENTLY_OBSERVED").contains(source) || observedAt==null
                || openingHundredths==null || otherActivityHundredths==null || openingHundredths<0 || otherActivityHundredths<0)
                throw new IllegalArgumentException("UNKNOWN_PERIOD_BASELINE");
            observedAt=observedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            Math.addExact(openingHundredths,otherActivityHundredths);
        }
    }
    public record Receipt(String digest,Instant appliedAt,String successorDeploymentKey,long retainedOpeningBytes) { }
}
