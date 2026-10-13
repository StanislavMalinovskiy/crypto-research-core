package io.cryptoresearch.marketdata.api;

import java.time.Instant;
import java.util.*;

/** Complete DB-visible received scope, not a seal or chain coverage assertion. */
public final class PumpSwapRetainedWindow {
    private PumpSwapRetainedWindow() { }
    public record Request(UUID session,Instant receivedFromInclusive,Instant receivedToExclusive,
            Instant eventFromInclusive,Instant eventToExclusive) { }
    public enum Qualification { EXACT,AT_LEAST,UNKNOWN }
    public record Observation(Optional<Long> value,Qualification qualification) {
        public Observation { Objects.requireNonNull(value);Objects.requireNonNull(qualification);
            if(value.isPresent() && value.get()<0 || value.isEmpty()!= (qualification==Qualification.UNKNOWN)) throw new IllegalArgumentException("RETAINED_INVALID_OBSERVATION"); }
        public static Observation unknown(){return new Observation(Optional.empty(),Qualification.UNKNOWN);}
        public static Observation exact(long n){return new Observation(Optional.of(n),Qualification.EXACT);}
        public static Observation atLeast(long n){return new Observation(Optional.of(n),Qualification.AT_LEAST);}
    }
    public record Diagnostic(Observation observedDistinctSignatures,Observation observedSourceProofBytes) { }
    public static final class ReadFailure extends IllegalStateException {
        private final Diagnostic diagnostic;
        public ReadFailure(String reason,Diagnostic diagnostic){super(reason);this.diagnostic=Objects.requireNonNull(diagnostic);}
        public Diagnostic diagnostic(){return diagnostic;}
    }
    public record Disposition(String signature,String pool,long slot,String status,Optional<String> rawDigest,
            String notificationDigest,String responseDigest,Instant receivedAt,Optional<Instant> admittedAt,long duplicates,long conflicts) { }
    public record Proof(String kind,String digest,Instant receivedAt) { }
    public record Gap(long id,String pool,String kind,String reason,Optional<Long> fromSlot,Optional<Long> toSlot,
            Instant openedAt,Optional<Instant> closedAt,Optional<String> boundarySignature,String status) { }
    public record Manifest(List<Disposition> dispositions,List<Proof> proofs,List<Gap> gaps,
            Map<String,Long> statuses,long distinctSignatures,long sourceProofBytes,String fingerprint,List<String> limitations) {
        public Manifest {dispositions=List.copyOf(dispositions);proofs=List.copyOf(proofs);gaps=List.copyOf(gaps);statuses=Map.copyOf(statuses);limitations=List.copyOf(limitations);}
    }
    public record Snapshot(String version,Request request,String configurationDigest,Instant readObservedAt,
            PumpSwapOperations.RecordedBatch batch,List<PumpSwapPoolLegs.PoolBinding> pools,Manifest manifest,String sourceFingerprint) {
        public Snapshot {pools=List.copyOf(pools);}
    }
}
