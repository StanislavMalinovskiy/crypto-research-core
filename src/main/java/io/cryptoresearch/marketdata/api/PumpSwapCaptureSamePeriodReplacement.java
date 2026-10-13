package io.cryptoresearch.marketdata.api;

import java.time.Instant;

/** Explicit manual child authority, never an automatic terminal retry or reservation refund. */
public record PumpSwapCaptureSamePeriodReplacement(String version,String predecessorDeploymentKey,String predecessorConfigurationDigest,
        String predecessorReason,String allocationRootKey,String allocationRootConfigurationDigest,
        PumpSwapContinuousCapture.Configuration successor,PumpSwapContinuousCapture.Resources carried,int consumedSegments,
        String decisionVersion,String ownerDecision) {
    public record Receipt(String digest,Instant appliedAt,String successorDeploymentKey,
            PumpSwapContinuousCapture.Resources carried,int consumedSegments) { }
}
