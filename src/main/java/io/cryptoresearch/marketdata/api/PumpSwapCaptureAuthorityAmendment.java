package io.cryptoresearch.marketdata.api;

import java.time.Instant;

/** Separate prospective authority contract; legacy configurations remain unchanged. */
public record PumpSwapCaptureAuthorityAmendment(String version,String provider,
        PumpSwapCapture.Authority predecessor,PumpSwapCapture.Authority successor,String ownerDecision) {
    public record Receipt(String digest,Instant appliedAt,long effectiveCeilingHundredths) { }
}
