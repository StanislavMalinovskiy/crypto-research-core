package io.cryptoresearch.signal.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.signal.api.PumpSwapRetainedNativePriceSignal.*;

class RetainedNativeSolPriceRiseDiagnosticTest {
    static final Instant R=VersionedNativeSolPriceRiseDiagnosticTest.D,E=VersionedNativeSolPriceRiseDiagnosticTest.E;
    static final String HASH=VersionedNativeSolPriceRiseDiagnosticTest.KEY;
    static class MutableClock extends Clock {
        Instant value;MutableClock(Instant value){this.value=value;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return value;}
    }
    static PumpSwapRetainedWindow.Request window(){return new PumpSwapRetainedWindow.Request(UUID.fromString("00000000-0000-0000-0000-000000000001"),R.minusSeconds(3600),R,E.minusSeconds(600),E);}
    static PumpSwapRetainedWindow.Snapshot snapshot(String status,List<PumpSwapRetainedWindow.Gap> gaps){
        var dispositions=status==null?List.<PumpSwapRetainedWindow.Disposition>of():List.of(new PumpSwapRetainedWindow.Disposition("pending",VersionedNativeSolPriceRiseDiagnosticTest.BINDING.pool(),1,status,Optional.empty(),HASH,HASH,R.minusSeconds(1),Optional.empty(),0,0));
        return new PumpSwapRetainedWindow.Snapshot("pumpswap-retained-window-v1",window(),HASH,R,new PumpSwapOperations.RecordedBatch(List.of(),List.of()),List.of(VersionedNativeSolPriceRiseDiagnosticTest.BINDING),new PumpSwapRetainedWindow.Manifest(dispositions,List.of(),gaps,status==null?Map.of():Map.of(status,1L),status==null?0:1,0,HASH,List.of()),HASH);
    }
    static Request request(){return new Request(window(),VersionedNativeSolPriceRiseDiagnosticTest.POOL,E);}
    static PumpSwapVersionedPoolCandles.Result triggering(PumpSwapVersionedPoolCandles.Request q){
        var old=VersionedNativeSolPriceRiseDiagnosticTest.report(List.of(VersionedNativeSolPriceRiseDiagnosticTest.candle(E.minusSeconds(600),java.math.BigInteger.ONE,java.math.BigInteger.ONE,0,0),VersionedNativeSolPriceRiseDiagnosticTest.candle(E.minusSeconds(300),java.math.BigInteger.TWO,java.math.BigInteger.ONE,0,0)),List.of());
        return new PumpSwapVersionedPoolCandles.Result(old.algorithmVersion(),old.canonicalizationVersion(),new PumpSwapVersionedPoolCandles.Descriptor(q.decoderVersion(),q.pools(),q.fromInclusive(),q.toExclusive(),q.derivationObservedAt(),q.knowledgeCutoff()),old.selectedInputFingerprint(),old.pinnedSourceHashes(),old.transactions(),old.operations(),old.candles(),old.uncertainties(),old.reconciliation(),old.limitations(),old.fingerprint());
    }
    @Test void otherwiseTriggeringPureResultCannotConcealPending(){
        var api=mock(MarketDataApi.class);when(api.retainedPumpSwapWindow(any())).thenReturn(snapshot("PENDING",List.of()));when(api.pumpSwapVersionedPoolCandles(any())).thenAnswer(i->triggering(i.getArgument(0)));
        var result=new RetainedNativeSolPriceRiseDiagnostic(api,new VersionedNativeSolPriceRiseDiagnostic(api),new MutableClock(R.plusSeconds(1))).diagnose(request());
        assertThat(result.pure().status()).as("positive pure-rule control executes").isEqualTo(io.cryptoresearch.signal.api.PumpSwapVersionedNativePriceSignal.Status.TRIGGER);
        assertThat(result.effectiveStatus()).as("capture PENDING cannot be published as clean trigger").isEqualTo(io.cryptoresearch.signal.api.PumpSwapVersionedNativePriceSignal.Status.INSUFFICIENT);
        verify(api,times(1)).retainedPumpSwapWindow(any());assertThat(result.reasons()).contains("NON_RAW_LINKED_DISPOSITIONS:1");
    }
    @Test void unresolvedTransactionAndEvenRecoveredStateRemainConservative(){
        for(var kind:List.of("TRANSACTION","STATE")){
            var gap=new PumpSwapRetainedWindow.Gap(1,VersionedNativeSolPriceRiseDiagnosticTest.BINDING.pool(),kind,"DISCONNECT",Optional.empty(),Optional.empty(),R.minusSeconds(1),Optional.empty(),Optional.empty(),kind.equals("STATE")?"RECOVERED_TRANSACTIONS":"UNRESOLVED");
            var api=mock(MarketDataApi.class);when(api.retainedPumpSwapWindow(any())).thenReturn(snapshot(null,List.of(gap)));when(api.pumpSwapVersionedPoolCandles(any())).thenAnswer(i->triggering(i.getArgument(0)));
            var result=new RetainedNativeSolPriceRiseDiagnostic(api,new VersionedNativeSolPriceRiseDiagnostic(api),new MutableClock(R.plusSeconds(1))).diagnose(request());
            assertThat(result.pure().status()).isEqualTo(io.cryptoresearch.signal.api.PumpSwapVersionedNativePriceSignal.Status.TRIGGER);
            assertThat(result.effectiveStatus()).as("unresolved %s context",kind).isEqualTo(io.cryptoresearch.signal.api.PumpSwapVersionedNativePriceSignal.Status.INSUFFICIENT);
        }
    }
    @Test void clockIsObservedAfterPreliminaryCalculationAndSourceReadOnlyOnce(){
        var api=mock(MarketDataApi.class);var clock=new MutableClock(R.plusSeconds(1));when(api.retainedPumpSwapWindow(any())).thenReturn(snapshot(null,List.of()));
        when(api.pumpSwapVersionedPoolCandles(any())).thenAnswer(i->{clock.value=R.plusSeconds(9);return triggering(i.getArgument(0));});
        var result=new RetainedNativeSolPriceRiseDiagnostic(api,new VersionedNativeSolPriceRiseDiagnostic(api),clock).diagnose(request());
        assertThat(result.derivationObservedAt()).as("D observed after successful bounded preliminary calculation").isEqualTo(R.plusSeconds(9));
        assertThat(result.pure().decisionKnowledgeAt()).isEqualTo(R.plusSeconds(9));verify(api,times(1)).retainedPumpSwapWindow(any());verify(api,times(2)).pumpSwapVersionedPoolCandles(any());
    }
    @Test void clockBehindReadObservationRejectsRatherThanInventingD(){
        var api=mock(MarketDataApi.class);when(api.retainedPumpSwapWindow(any())).thenReturn(snapshot(null,List.of()));when(api.pumpSwapVersionedPoolCandles(any())).thenAnswer(i->triggering(i.getArgument(0)));
        assertThatThrownBy(()->new RetainedNativeSolPriceRiseDiagnostic(api,new VersionedNativeSolPriceRiseDiagnostic(api),new MutableClock(R.minusSeconds(1))).diagnose(request())).hasMessage("RETAINED_DIAGNOSTIC_CLOCK_BEHIND_SOURCE");
    }
    @Test void invalidScopeAndExactPoolMismatchRejectBeforeAnyCalculation(){
        var api=mock(MarketDataApi.class);var diagnostic=new RetainedNativeSolPriceRiseDiagnostic(api,new VersionedNativeSolPriceRiseDiagnostic(api),new MutableClock(R));
        assertThatThrownBy(()->diagnostic.diagnose(new Request(window(),request().pool(),E.plusSeconds(1)))).hasMessage("RETAINED_DIAGNOSTIC_INVALID_REQUEST");verifyNoInteractions(api);
        when(api.retainedPumpSwapWindow(any())).thenReturn(snapshot(null,List.of()));var p=request().pool();
        assertThatThrownBy(()->diagnostic.diagnose(new Request(window(),new PumpSwapPoolMetrics.Pool(p.chain(),p.pool(),"wrong",p.quoteMint()),E))).hasMessage("RETAINED_DIAGNOSTIC_SOURCE_MISMATCH");verify(api,never()).pumpSwapVersionedPoolCandles(any());
    }
    @Test void recoveredTransactionGapAloneDoesNotEraseAdequatePureDiagnostic(){
        var gap=new PumpSwapRetainedWindow.Gap(1,request().pool().pool(),"TRANSACTION","DISCONNECT",Optional.empty(),Optional.empty(),R.minusSeconds(2),Optional.of(R.minusSeconds(1)),Optional.empty(),"RECOVERED_TRANSACTIONS");
        var api=mock(MarketDataApi.class);when(api.retainedPumpSwapWindow(any())).thenReturn(snapshot(null,List.of(gap)));when(api.pumpSwapVersionedPoolCandles(any())).thenAnswer(i->triggering(i.getArgument(0)));var result=new RetainedNativeSolPriceRiseDiagnostic(api,new VersionedNativeSolPriceRiseDiagnostic(api),new MutableClock(R.plusSeconds(1))).diagnose(request());assertThat(result.effectiveStatus()).isEqualTo(result.pure().status());assertThat(result.reasons()).isEmpty();
    }
    @Test void callerTransactionAndEventAfterReadRejectBeforePureCalculation(){
        var api=mock(MarketDataApi.class);var diagnostic=new RetainedNativeSolPriceRiseDiagnostic(api,new VersionedNativeSolPriceRiseDiagnostic(api),new MutableClock(R.plusSeconds(1)));org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try{assertThatThrownBy(()->diagnostic.diagnose(request())).hasMessage("RETAINED_DIAGNOSTIC_REQUIRES_NO_TRANSACTION");verifyNoInteractions(api);}finally{org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
        var s=snapshot(null,List.of());when(api.retainedPumpSwapWindow(any())).thenReturn(new PumpSwapRetainedWindow.Snapshot(s.version(),s.request(),s.configurationDigest(),E.minusSeconds(1),s.batch(),s.pools(),s.manifest(),s.sourceFingerprint()));assertThatThrownBy(()->diagnostic.diagnose(request())).hasMessage("RETAINED_DIAGNOSTIC_EVENT_AFTER_READ");verify(api,never()).pumpSwapVersionedPoolCandles(any());
    }
}
