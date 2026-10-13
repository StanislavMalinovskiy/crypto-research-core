package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static io.cryptoresearch.marketdata.application.PumpSwapPoolCaptureTest.*;
import static io.cryptoresearch.marketdata.application.CaptureRpcReuseTest.policy;
import org.junit.jupiter.api.Test;

class PumpSwapDurableInboxTest {
    @Test void v7RetainsTrustedReuseAndConservativeFractionalAccounting() {
        var c=policy(configuration(SESSION,0L),"capture-durable-inbox-v7","synthetic-inbox-period","0.0002",51);
        assertThat(CaptureBudget.reuse(c)).as("v7 includes the exact v6 trusted acquisition policy").isTrue();
        assertThat(CaptureBudget.subscriptionHundredths(c,51)).isEqualTo(2);
        assertThat(CaptureBudget.subscriptionHundredths(c,7778076)).isEqualTo(155562);
        assertThat(CaptureBudget.launchProblem(c)).isEmpty();
        assertThat(CaptureBudget.reuse(configuration(SESSION,0L))).isFalse();
    }
    @Test void backlogLimitsRetainOriginalReceiveTimeAtExactBoundaries() {
        assertThat(new CaptureLedgerUseCase.InboxBacklog(4999,START).problem(START.plusSeconds(1799))).isEmpty();
        assertThat(new CaptureLedgerUseCase.InboxBacklog(5000,START).problem(START)).isEqualTo("CAPTURE_PENDING_COUNT_LIMIT");
        assertThat(new CaptureLedgerUseCase.InboxBacklog(1,START).problem(START.plusSeconds(1800))).isEqualTo("CAPTURE_PENDING_AGE_LIMIT");
        assertThat(new CaptureLedgerUseCase.InboxBacklog(0,null).problem(START.plusSeconds(1800))).isEmpty();
    }
    @Test void workspaceAccountsEscapingAndSingletonCannotBeTruncated() {
        var c=PumpSwapDurableInboxIT.config(1000,16000000); var ledger=new CaptureLedgerUseCase(null,java.time.Clock.fixed(START,java.time.ZoneOffset.UTC),null);
        var n=new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(address(0),null,PumpSwapDurableInboxIT.signature(0),101,"{}","{\"text\":\""+"\\\"".repeat(450000)+"\"}",START);
        assertThatThrownBy(()->ledger.inboxBatch(c,java.util.List.of(n))).hasMessage("CAPTURE_INBOX_WORKSPACE_LIMIT");
        var small=new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(address(0),null,PumpSwapDurableInboxIT.signature(0),101,"{}","{}",START);
        assertThat(ledger.inboxBatch(c,java.util.Collections.nCopies(128,small)).frames()).isEqualTo(128);
        assertThatThrownBy(()->ledger.inboxBatch(c,java.util.Collections.nCopies(129,small))).hasMessage("CAPTURE_INBOX_FRAME_LIMIT");
    }
    private io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Accounting run(int items,long bytes) throws Exception {
        var c=PumpSwapDurableInboxIT.config(items,bytes); var ledger=org.mockito.Mockito.mock(CaptureLedgerUseCase.class);
        org.mockito.Mockito.when(ledger.status(c.sessionId())).thenReturn(org.mockito.Mockito.mock(io.cryptoresearch.marketdata.api.PumpSwapCapture.Status.class));
        org.mockito.Mockito.when(ledger.now()).thenReturn(START); org.mockito.Mockito.when(ledger.inboxBacklog(c)).thenReturn(new CaptureLedgerUseCase.InboxBacklog(0,null));
        var collector=new PumpSwapPoolCollector(new io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings(),ledger,null,tools.jackson.databind.json.JsonMapper.builder().build());
        var type=java.util.Arrays.stream(PumpSwapPoolCollector.class.getDeclaredClasses()).filter(t->t.getSimpleName().equals("Run")).findFirst().orElseThrow(); var constructor=type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        return (io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Accounting)constructor.newInstance(collector,c,"writer");
    }
    private java.util.Map<?,?> overflow(Object run) throws Exception { var field=run.getClass().getDeclaredField("overflow"); field.setAccessible(true); return (java.util.Map<?,?>)field.get(run); }
    @Test void durableWriteItemRefusalReportsItsMeasuredOccupancy() throws Exception {
        var write=run(1,256); write.controlStarted(); write.controlStarted();
        assertThat(overflow(write)).isEqualTo(java.util.Map.of("site","DURABLE_WRITE","condition","ITEM","items",1,"bytes",0L,"itemLimit",1,"byteLimit",256L));
    }
    @Test void durableWriteByteRefusalReportsItsMeasuredOccupancy() throws Exception {
        var write=run(8,16);
        assertThatThrownBy(()->write.evidence("TEST","x".repeat(17))).hasMessage("QUEUE_OVERFLOW");
        assertThat(overflow(write)).isEqualTo(java.util.Map.of("site","DURABLE_WRITE","condition","BYTE","items",0,"bytes",0L,"itemLimit",8,"byteLimit",16L));
    }
    @Test void capacitySitesUseMeasuredBoundsAndNeverReplaceTheFirstCause() throws Exception {
        var fragmentItem=run(1,256); fragmentItem.controlStarted(); assertThatThrownBy(()->fragmentItem.websocketFragment(1)).hasMessage("QUEUE_OVERFLOW");
        assertThat(overflow(fragmentItem).get("site")).isEqualTo("FRAGMENT"); assertThat(overflow(fragmentItem).get("condition")).isEqualTo("ITEM"); assertThat(overflow(fragmentItem).get("items")).isEqualTo(1);
        var fragmentByte=run(8,16); assertThatThrownBy(()->fragmentByte.websocketFragment(17)).hasMessage("QUEUE_OVERFLOW"); assertThat(overflow(fragmentByte).get("condition")).isEqualTo("BYTE");
        var write=run(1,256); write.controlStarted(); write.controlStarted(); var first=overflow(write); assertThat(first.get("site")).isEqualTo("DURABLE_WRITE"); assertThat(first.get("condition")).isEqualTo("ITEM");
        assertThatThrownBy(()->write.websocketFragment(300)).hasMessage("QUEUE_OVERFLOW"); assertThat(overflow(write)).isEqualTo(first);
        var transfer=run(8,256); transfer.websocketFragment(2);
        var n=new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(address(0),address(1),null,101,"x".repeat(300),"{}",START);
        assertThatThrownBy(()->transfer.websocketEvidence(n,java.util.concurrent.CompletableFuture.completedFuture(null))).hasMessage("QUEUE_OVERFLOW");
        assertThat(overflow(transfer).get("site")).isEqualTo("IN_FLIGHT_TRANSFER"); assertThat(overflow(transfer).get("condition")).isEqualTo("BYTE");
    }
}
