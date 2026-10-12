package io.cryptoresearch.marketdata.application;

import io.cryptoresearch.marketdata.api.PumpSwapCapturePeriodContinuation;
import org.springframework.stereotype.Service;

/** Separate explicit operator enrollment; provider work remains in the existing coordinator. */
@Service
public class EnrollCapturePeriodContinuationUseCase {
    private final io.cryptoresearch.marketdata.infrastructure.persistence.JdbcContinuousCaptureStore store;
    private final io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore capture;
    public EnrollCapturePeriodContinuationUseCase(io.cryptoresearch.marketdata.infrastructure.persistence.JdbcContinuousCaptureStore store,
            io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore capture) {this.store=store;this.capture=capture;}
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
        isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED,timeout=10)
    public PumpSwapCapturePeriodContinuation.Receipt enroll(PumpSwapCapturePeriodContinuation request) {
        validate(request);store.timeout();capture.providerAdmissionLock(request.successor().template().provider());
        var predecessor=store.lockDeployment(request.successor().template().provider(),request.predecessorDeploymentKey());
        var recorded=new CanonicalFingerprint().field("version","continuous-configuration-v1").field("configuration",store.encode(predecessor.configuration())).finish();
        if(!recorded.equals(predecessor.digest()))throw new IllegalStateException("CONTINUATION_PREDECESSOR_CONFLICT");
        var text=store.encode(request);if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1000000)throw new IllegalArgumentException("CAPTURE_CONFIGURATION_BYTE_LIMIT");
        var digest=new CanonicalFingerprint().field("version",request.version()).field("request",text).finish();
        var successorDigest=new CanonicalFingerprint().field("version","continuous-configuration-v1").field("configuration",store.encode(request.successor())).finish();
        return store.enrollContinuation(request,digest,successorDigest,ContinuousCaptureLedgerUseCase.resources(request.successor().template()),CaptureBudget.ceiling(request.successor().template()));
    }
    public static void validate(PumpSwapCapturePeriodContinuation r) {
        if(r==null || !"continuous-period-continuation-v1".equals(r.version()) || r.periodStart()==null || r.baseline()==null || r.successor()==null
            || r.predecessorDeploymentKey()==null || r.predecessorDeploymentKey().isBlank() || r.predecessorConfigurationDigest()==null
            || !r.predecessorConfigurationDigest().matches("sha256:[0-9a-f]{64}") || r.ownerDecision()==null || r.ownerDecision().isBlank() || r.ownerDecision().length()>4096)
            throw new IllegalArgumentException("INVALID_PERIOD_CONTINUATION");
        var c=r.successor();var a=c.template().authority();var b=r.baseline();
        var problem=CaptureBudget.launchProblem(c.template());if(!problem.isEmpty())throw new IllegalArgumentException(problem);
        if(!"alchemy".equals(c.template().provider()) || c.template().pools().size()!=3 || a.ceilingUsd().compareTo(new java.math.BigDecimal("50"))>0
            || a.subscriptionCuPerByte().compareTo(new java.math.BigDecimal("0.0002"))!=0 || !b.source().equals(a.source())
            || !b.openingHundredths().equals(a.openingHundredths()) || b.otherActivityHundredths()!=a.otherActivityHundredths()
            || b.observedAt().isBefore(r.periodStart()) || !b.observedAt().isBefore(a.periodEnd()) || c.template().startsAt().isBefore(r.periodStart())
            || !a.periodEnd().isAfter(r.periodStart()) || c.opening().reservedHundredths()!=Math.addExact(b.openingHundredths(),b.otherActivityHundredths())
            || c.allowances().reservedHundredths()>CaptureBudget.ceiling(c.template()))throw new IllegalArgumentException("INVALID_PERIOD_BASELINE_OR_AUTHORITY");
        if(!c.allowances().contains(c.opening().plus(ContinuousCaptureLedgerUseCase.resources(c.template()))))throw new IllegalArgumentException("CONTINUATION_RESOURCE_LIMIT");
    }
}
