package io.cryptoresearch.marketdata.application;

import io.cryptoresearch.marketdata.api.PumpSwapCaptureAuthorityAmendment;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.Authority;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore;
import java.math.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** One explicit prospective same-period transition; no historical repricing or rollover. */
@Service
public class AmendCapturePeriodAuthorityUseCase {
    private final JdbcCaptureStore store;
    private final Clock clock;
    public AmendCapturePeriodAuthorityUseCase(JdbcCaptureStore store,Clock clock) {this.store=store;this.clock=clock;}
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,timeout=10)
    public PumpSwapCaptureAuthorityAmendment.Receipt amend(PumpSwapCaptureAuthorityAmendment amendment) {
        validate(amendment);store.providerAdmissionLock(amendment.provider());
        var request=store.encode(amendment);
        var digest=new CanonicalFingerprint().field("version",amendment.version()).field("request",request).finish();
        return store.amendAuthority(amendment,ceiling(amendment.predecessor()),ceiling(amendment.successor()),digest,clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
    public static void validate(PumpSwapCaptureAuthorityAmendment amendment) {
        if(amendment==null || !"capture-authority-amendment-v1".equals(amendment.version()) || !"alchemy".equals(amendment.provider())
            || amendment.ownerDecision()==null || amendment.ownerDecision().isBlank() || amendment.ownerDecision().length()>4096
            || amendment.predecessor()==null || amendment.successor()==null)throw invalid();
        var p=amendment.predecessor();var s=amendment.successor();
        if(!java.util.Objects.equals(p.accountPeriod(),s.accountPeriod()) || !java.util.Objects.equals(p.periodEnd(),s.periodEnd())
            || !java.util.Objects.equals(p.source(),s.source()) || !java.util.Objects.equals(p.openingHundredths(),s.openingHundredths())
            || p.otherActivityHundredths()!=s.otherActivityHundredths() || p.exclusiveAllocation()!=s.exclusiveAllocation()
            || p.retentionAllowed()!=s.retentionAllowed() || !java.util.Objects.equals(p.usdPerMillionCu(),s.usdPerMillionCu()))
            throw new IllegalArgumentException("CAPTURE_AUTHORITY_PREDECESSOR_CONFLICT");
        if(p.accountPeriod()==null || p.accountPeriod().isBlank() || p.accountPeriod().length()>4096 || p.periodEnd()==null
            || p.source()==null || !java.util.Set.of("OWNER_REPORTED","INDEPENDENTLY_OBSERVED").contains(p.source())
            || p.openingHundredths()==null || p.openingHundredths()<0 || p.otherActivityHundredths()<0
            || !p.retentionAllowed() || (!p.exclusiveAllocation() && p.otherActivityHundredths()==0)
            || p.ceilingUsd()==null || p.ceilingUsd().signum()<=0 || p.usdPerMillionCu()==null || p.usdPerMillionCu().signum()<=0
            || p.subscriptionCuPerByte()==null || p.subscriptionCuPerByte().signum()<=0 || p.ceilingHundredths()<=0
            || s.ceilingUsd()==null || s.ceilingUsd().signum()<=0 || s.ceilingUsd().compareTo(new BigDecimal("50"))>0
            || s.ceilingUsd().compareTo(p.ceilingUsd())<0 || s.ceilingHundredths()<p.ceilingHundredths()
            || s.subscriptionCuPerByte()==null || s.subscriptionCuPerByte().compareTo(new BigDecimal("0.0002"))!=0
            || s.ownerAuthorization()==null || s.ownerAuthorization().isBlank() || p.ownerAuthorization()==null || p.ownerAuthorization().isBlank())throw invalid();
        Math.addExact(s.openingHundredths(),s.otherActivityHundredths());
        if(ceiling(s)<ceiling(p))throw invalid();
    }
    public static long ceiling(Authority a) {
        return Math.min(a.ceilingHundredths(),Math.multiplyExact(a.ceilingUsd().movePointRight(6).divide(a.usdPerMillionCu(),0,RoundingMode.FLOOR).longValueExact(),100));
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("INVALID_PROSPECTIVE_AUTHORITY");}
}
