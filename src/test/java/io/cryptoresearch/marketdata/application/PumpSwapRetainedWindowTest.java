package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import io.cryptoresearch.marketdata.api.PumpSwapRetainedWindow.*;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcPumpSwapRetainedWindowStore;

class PumpSwapRetainedWindowTest {
    static final Instant FROM=Instant.parse("2026-10-12T10:00:00Z");
    static Request request(){return new Request(UUID.randomUUID(),FROM.plusSeconds(17),FROM.plusSeconds(471),FROM,FROM.plusSeconds(600));}
    @Test void arbitraryReceivedSubintervalIsNotForcedOntoEventBuckets(){
        var q=request();assertThat(ReadPumpSwapRetainedWindowUseCase.normalize(q)).isEqualTo(q);
        var reader=mock(PumpSwapRetainedWindowReader.class);new ReadPumpSwapRetainedWindowUseCase(reader).read(q);verify(reader).read(q);
        assertThatThrownBy(()->ReadPumpSwapRetainedWindowUseCase.normalize(new Request(q.session(),FROM,FROM.plusSeconds(86401),FROM,FROM.plusSeconds(600)))).hasMessage("RETAINED_INVALID_REQUEST");
        assertThatThrownBy(()->ReadPumpSwapRetainedWindowUseCase.normalize(new Request(q.session(),FROM,FROM.plusSeconds(1),FROM.plusSeconds(1),FROM.plusSeconds(600)))).hasMessage("RETAINED_INVALID_REQUEST");
    }
    @Test void earlyTimeoutPreservesAbsentUnknownObservations(){
        var store=mock(JdbcPumpSwapRetainedWindowStore.class);when(store.header(any())).thenThrow(new org.springframework.dao.QueryTimeoutException("synthetic"));
        var reader=new PumpSwapRetainedWindowReader(store,mock(CaptureLedgerUseCase.class));
        var failure=catchThrowableOfType(ReadFailure.class,()->reader.read(request()));
        assertThat(failure.getMessage()).isEqualTo("RETAINED_READ_FAILED_OR_TIMEOUT");assertThat(failure.diagnostic()).isEqualTo(new Diagnostic(Observation.unknown(),Observation.unknown()));verify(store,never()).configuration(any());
    }
    @Test void observationsCannotInventExactZeroForUnknownWork(){
        assertThat(Observation.atLeast(4097).qualification()).isEqualTo(Qualification.AT_LEAST);
        assertThatThrownBy(()->new Observation(Optional.empty(),Qualification.EXACT)).hasMessage("RETAINED_INVALID_OBSERVATION");
        assertThatThrownBy(()->new Observation(Optional.of(0L),Qualification.UNKNOWN)).hasMessage("RETAINED_INVALID_OBSERVATION");
    }
    @Test void callerTransactionRejectsBeforeReaderResolution(){
        var reader=mock(PumpSwapRetainedWindowReader.class);org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try{assertThatThrownBy(()->new ReadPumpSwapRetainedWindowUseCase(reader).read(request())).hasMessage("RETAINED_REQUIRES_NO_TRANSACTION");verifyNoInteractions(reader);}
        finally{org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
    }
    @Test void incompletePageRejectsInsteadOfPublishingSelectedMetadataAsComplete(){
        var store=mock(JdbcPumpSwapRetainedWindowStore.class);var c=PumpSwapPoolCaptureTest.configuration(UUID.randomUUID(),0L);var q=new Request(c.sessionId(),FROM,FROM.plusSeconds(1),FROM,FROM.plusSeconds(600));var text=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(c);var digest=new CanonicalFingerprint().field("contract","pumpswap-capture-configuration-v1").field("configuration",text).finish();
        when(store.header(c.sessionId())).thenReturn(new JdbcPumpSwapRetainedWindowStore.Header(FROM.plusSeconds(2),digest,text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));when(store.configuration(c.sessionId())).thenReturn(text);var sig="1".repeat(63)+"2";when(store.signatures(q)).thenReturn(List.of(sig));when(store.metadata(any(),any(),anyList())).thenReturn(List.of(new JdbcPumpSwapRetainedWindowStore.Meta(sig,c.pools().getFirst().address(),"PENDING",null,101,2,0,0,true)));
        assertThatThrownBy(()->new PumpSwapRetainedWindowReader(store,mock(CaptureLedgerUseCase.class)).read(q)).hasMessage("RETAINED_PAGE_INCOMPLETE");verify(store,never()).raw(any(),anyList());
    }
    @Test void canonicalPublicOutputHasIndependentStreaming64MbLimit(){
        var store=mock(JdbcPumpSwapRetainedWindowStore.class);var c=PumpSwapPoolCaptureTest.configuration(UUID.randomUUID(),0L);var q=new Request(c.sessionId(),FROM,FROM.plusSeconds(1),FROM,FROM.plusSeconds(600));var text=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(c);var digest=new CanonicalFingerprint().field("contract","pumpswap-capture-configuration-v1").field("configuration",text).finish();
        when(store.header(c.sessionId())).thenReturn(new JdbcPumpSwapRetainedWindowStore.Header(FROM.plusSeconds(2),digest,text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));when(store.configuration(c.sessionId())).thenReturn(text);
        var gaps=java.util.stream.IntStream.range(0,1000).mapToObj(i->new Gap(i,c.pools().getFirst().address(),"STATE","x".repeat(64000),Optional.empty(),Optional.empty(),FROM,Optional.empty(),Optional.empty(),"UNRESOLVED")).toList();when(store.gaps(c.sessionId())).thenReturn(gaps);
        var error=catchThrowableOfType(ReadFailure.class,()->new PumpSwapRetainedWindowReader(store,mock(CaptureLedgerUseCase.class)).read(q));assertThat(error.getMessage()).isEqualTo("RETAINED_OUTPUT_LIMIT");assertThat(error.diagnostic()).isEqualTo(new Diagnostic(Observation.exact(0),Observation.exact(0)));
    }
}
