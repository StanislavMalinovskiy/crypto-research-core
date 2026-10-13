package io.cryptoresearch.marketdata.application;

import io.cryptoresearch.marketdata.api.PumpSwapCaptureSamePeriodReplacement;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcContinuousCaptureStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Owning manual enrollment transaction; no coordinator or provider work occurs here. */
@Service
public class EnrollCaptureSamePeriodReplacementUseCase {
    private final JdbcContinuousCaptureStore store;
    private final JdbcCaptureStore capture;
    public EnrollCaptureSamePeriodReplacementUseCase(JdbcContinuousCaptureStore store,JdbcCaptureStore capture) {this.store=store;this.capture=capture;}
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,timeout=10)
    public PumpSwapCaptureSamePeriodReplacement.Receipt enroll(PumpSwapCaptureSamePeriodReplacement request) {
        validate(request);store.timeout();capture.providerAdmissionLock(request.successor().template().provider());
        for(var key:new java.util.TreeSet<>(java.util.List.of(request.allocationRootKey(),request.predecessorDeploymentKey()))) {
            var deployment=store.lockDeployment(request.successor().template().provider(),key);
            var recorded=new CanonicalFingerprint().field("version","continuous-configuration-v1").field("configuration",store.encode(deployment.configuration())).finish();
            if(!recorded.equals(deployment.digest()))throw new IllegalStateException("REPLACEMENT_PREDECESSOR_CONFLICT");
        }
        var text=store.encode(request);if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1000000)throw new IllegalArgumentException("CAPTURE_CONFIGURATION_BYTE_LIMIT");
        var digest=new CanonicalFingerprint().field("version",request.version()).field("request",text).finish();
        var configurationDigest=new CanonicalFingerprint().field("version","continuous-configuration-v1").field("configuration",store.encode(request.successor())).finish();
        var full=ContinuousCaptureLedgerUseCase.resources(request.successor().template());
        var first=new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Resources(full.requests(),full.receivedBytes(),full.subscriptionBytes(),full.retainedBytes(),full.rpcHundredths(),full.reservedHundredths(),request.successor().segmentSeconds());
        return store.enrollReplacement(request,digest,configurationDigest,first,CaptureBudget.ceiling(request.successor().template()));
    }
    public static void validate(PumpSwapCaptureSamePeriodReplacement r) {
        if(r==null || !"continuous-same-period-replacement-v1".equals(r.version()) || !bounded(r.predecessorDeploymentKey(),4096)
            || !bounded(r.allocationRootKey(),4096) || !digest(r.predecessorConfigurationDigest()) || !digest(r.allocationRootConfigurationDigest())
            || !bounded(r.predecessorReason(),65536) || !bounded(r.decisionVersion(),128) || !bounded(r.ownerDecision(),4096)
            || r.successor()==null || r.carried()==null || r.consumedSegments()<0 || !r.successor().opening().equals(r.carried()))
            throw new IllegalArgumentException("INVALID_SAME_PERIOD_REPLACEMENT");
        var c=r.successor();var problem=CaptureBudget.launchProblem(c.template());if(!problem.isEmpty())throw new IllegalArgumentException(problem);
        if(!CaptureBudget.inbox(c.template()) || c.template().authority().ceilingUsd().compareTo(new java.math.BigDecimal("50"))>0
            || c.template().authority().subscriptionCuPerByte().compareTo(new java.math.BigDecimal("0.0002"))!=0)
            throw new IllegalArgumentException("INVALID_REPLACEMENT_POLICY");
    }
    private static boolean bounded(String value,int bytes) {return value!=null&&!value.isBlank()&&value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=bytes;}
    private static boolean digest(String value) {return value!=null&&value.matches("sha256:[0-9a-f]{64}");}
}
