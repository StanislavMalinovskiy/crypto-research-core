package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import io.cryptoresearch.marketdata.api.PumpSwapCaptureMonitor.*;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore.*;

class CaptureMonitorTest {
    static final Instant AT=Instant.parse("2026-10-11T00:00:00Z");
    static MonitorData data(String state,Instant start,Instant end,Instant lease,List<GapCount> counts,List<io.cryptoresearch.marketdata.api.PumpSwapCapture.Gap> details) {
        return new MonitorData(new MonitorHeader(UUID.randomUUID(),"sha256:"+"1".repeat(64),state,"recorded reason",start,end,lease),
            new Counters(1,2,3,4,5,6,7,8,9,10,11),List.of(),List.of(),counts,details);
    }
    @Test void validLeaseAndOpenWindowAreFactsButNeverHealthyActivity() {
        var d=data("RUNNING",AT,AT.plusSeconds(10),AT.plusSeconds(1),List.of(),List.of());
        var snapshot=ReadCaptureMonitorUseCase.project(d,AT);
        assertThat(snapshot.leaseValid()).as("observation strictly before retained expiry is a valid lease fact").isTrue();
        assertThat(snapshot.windowOpen()).isTrue();assertThat(snapshot.recordedState()).isEqualTo("RUNNING");
        assertThat(List.of(snapshot.processActivity(),snapshot.transportActivity(),snapshot.coverage())).containsOnly("UNVERIFIED");
        assertThat(List.of(snapshot.actualBilling(),snapshot.unreceivedLoss())).containsOnly("UNKNOWN");
    }
    @Test void strictMicrosecondBoundariesPreserveExpiredRunningAndStoppedIndependently() {
        for(var state:List.of("RUNNING","STOPPED")) {
            var d=data(state,AT,AT.plusSeconds(10),AT.plusSeconds(1),List.of(),List.of());
            var before=ReadCaptureMonitorUseCase.project(d,AT.minusNanos(1000));assertThat(before.windowOpen()).isFalse();assertThat(before.leaseValid()).isTrue();
            var expiry=ReadCaptureMonitorUseCase.project(d,AT.plusSeconds(1));assertThat(expiry.leaseValid()).isFalse();assertThat(expiry.windowOpen()).isTrue();assertThat(expiry.recordedState()).isEqualTo(state);
            var closed=ReadCaptureMonitorUseCase.project(data(state,AT,AT.plusSeconds(10),AT.plusSeconds(11),List.of(),List.of()),AT.plusSeconds(10).plusNanos(999));
            assertThat(closed.windowOpen()).isFalse();assertThat(closed.leaseValid()).isTrue();assertThat(closed.observedAt()).isEqualTo(AT.plusSeconds(10));assertThat(closed.reason()).isEqualTo("recorded reason");
        }
    }
    @Test void sortedImmutableDetailsTruncateWithoutLosingFullGroupedDenominatorOrContradiction() {
        var details=new ArrayList<io.cryptoresearch.marketdata.api.PumpSwapCapture.Gap>();
        for(int i=101;i>0;i--)details.add(new io.cryptoresearch.marketdata.api.PumpSwapCapture.Gap(i,"pool","STATE","original",null,null,AT,null,null,"UNKNOWN"));
        var groups=new ArrayList<>(List.of(new GapCount("z","STATE","RECOVERED_TRANSACTIONS",1),new GapCount("a","STATE","UNKNOWN",100)));
        var snapshot=ReadCaptureMonitorUseCase.project(data("RUNNING",AT,AT.plusSeconds(10),AT,groups,details),AT);
        groups.clear();details.clear();assertThat(snapshot.totalUnresolved()).isEqualTo(101);assertThat(snapshot.inconsistentGapStatus()).isTrue();assertThat(snapshot.detailsTruncated()).isTrue();
        assertThat(snapshot.intervals()).hasSize(100).extracting(io.cryptoresearch.marketdata.api.PumpSwapCapture.Gap::id).containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1,100).boxed().toList());
        assertThat(snapshot.unresolvedCounts()).extracting(GapCount::pool).containsExactly("a","z");
        assertThatThrownBy(()->snapshot.intervals().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void cardinalityAndUtf8EncodedProjectionOverflowFailWithoutPartialSnapshot() {
        var d=data("RUNNING",AT,AT.plusSeconds(10),AT,List.of(),List.of());
        for(var excessive:List.of(new MonitorData(d.header(),d.counters(),Collections.nCopies(33,new DispositionCount("X",1)),List.of(),List.of(),List.of()),
                new MonitorData(d.header(),d.counters(),List.of(),Collections.nCopies(4,new PoolPosition("p",Optional.empty())),List.of(),List.of()),
                data("RUNNING",AT,AT.plusSeconds(10),AT,Collections.nCopies(65,new GapCount("p","STATE","X",1)),List.of()),
                data("RUNNING",AT,AT.plusSeconds(10),AT,List.of(),Collections.nCopies(102,new io.cryptoresearch.marketdata.api.PumpSwapCapture.Gap(1,"p","STATE","",null,null,AT,null,null,"X"))),
                new MonitorData(new MonitorHeader(d.header().sessionId(),d.header().configurationDigest(),"RUNNING","雪".repeat(44000),AT,AT.plusSeconds(10),AT),d.counters(),List.of(),List.of(),List.of(),List.of()))) {
            assertThatThrownBy(()->ReadCaptureMonitorUseCase.project(excessive,AT)).hasMessage("CAPTURE_MONITOR_RESULT_LIMIT");
        }
    }
    @Test void exactlyOneClockSampleAndNullSessionNeverTouchTheStore() {
        var store=org.mockito.Mockito.mock(io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore.class);
        var clock=org.mockito.Mockito.mock(java.time.Clock.class);org.mockito.Mockito.when(clock.instant()).thenReturn(AT.plusNanos(999));
        org.mockito.Mockito.when(store.monitorHeader(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        var reader=new ReadCaptureMonitorUseCase(store,clock);assertThat(reader.read(UUID.randomUUID())).isEmpty();org.mockito.Mockito.verify(clock).instant();
        org.mockito.Mockito.clearInvocations(store,clock);assertThatThrownBy(()->reader.read(null)).isInstanceOf(NullPointerException.class);org.mockito.Mockito.verifyNoInteractions(store,clock);
    }
}
