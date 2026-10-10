package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static io.cryptoresearch.marketdata.application.PumpSwapPoolCaptureTest.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(CaptureMonitorIT.Clocks.class)
class CaptureMonitorIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MarketDataApi api;
    @Autowired CaptureLedgerUseCase ledger;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @Autowired MutableUtcClock clock;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired CaptureEvidenceUseCase capture;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore store;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PumpSwapPoolCollector collector;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean DecodePumpSwapOperationsUseCase decoder;
    UUID seed() {
        var id=UUID.randomUUID();clock.set(START);var c=configuration(id,0L);var a=c.authority();
        var authority=new PumpSwapCapture.Authority(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),a.ceilingHundredths(),a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),id.toString(),true,true);
        ledger.open(new PumpSwapCapture.Configuration(id,c.version(),c.provider(),c.startsAt(),c.pools(),c.limits(),authority),"synthetic-holder");return id;
    }
    void gap(UUID id,String kind,String status,String reason) {
        jdbc.sql("insert into marketdata.capture_gaps(session_id,pool,kind,reason,opened_at,status) values(:id,:pool,:kind,:reason,:at,:status)")
            .param("id",id).param("pool",address(0)).param("kind",kind).param("reason",reason).param("at",START.atOffset(java.time.ZoneOffset.UTC)).param("status",status).update();
    }
    @Test void contradictoryStateRecoveryBeyondDetailsRemainsCountedWithoutWrites() {
        var id=seed();for(int i=0;i<100;i++)gap(id,"TRANSACTION","UNRECOGNIZED_STATUS","gap "+i);
        gap(id,"STATE","RECOVERED_TRANSACTIONS","contradiction");gap(id,"TRANSACTION","RECOVERED_TRANSACTIONS","recovered");
        var before=jdbc.sql("select row_to_json(s)::text from marketdata.capture_sessions s where session_id=:id").param("id",id).query(String.class).single();
        var found=api.captureMonitor(id);
        assertThat(found).as("retained session must have a coherent monitor projection without collector access").isPresent();
        var snapshot=found.orElseThrow();assertThat(snapshot.totalUnresolved()).isEqualTo(101);assertThat(snapshot.inconsistentGapStatus()).isTrue();
        assertThat(snapshot.intervals()).hasSize(100);assertThat(snapshot.detailsTruncated()).isTrue();
        assertThat(snapshot.intervals()).allSatisfy(g->{assertThat(g.fromSlot()).isNull();assertThat(g.boundarySignature()).isNull();assertThat(g.status()).isEqualTo("UNRECOGNIZED_STATUS");});
        assertThat(jdbc.sql("select row_to_json(s)::text from marketdata.capture_sessions s where session_id=:id").param("id",id).query(String.class).single()).isEqualTo(before);
    }
    @Test void ambientReadCommittedCallerCannotMixConcurrentCommittedTransition() throws Exception {
        var id=seed();var headerRead=new java.util.concurrent.CountDownLatch(1);var committed=new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call->{var header=call.callRealMethod();headerRead.countDown();assertThat(committed.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();return header;}).when(store).monitorHeader(id);
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var future=executor.submit(()->{
                var caller=new org.springframework.transaction.support.TransactionTemplate(transactionManager);caller.setIsolationLevel(java.sql.Connection.TRANSACTION_READ_COMMITTED);
                return caller.execute(status->api.captureMonitor(id).orElseThrow());
            });
            try {
                assertThat(headerRead.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
                    jdbc.sql("update marketdata.capture_sessions set state='STOPPED',requests=1 where session_id=:id").param("id",id).update();gap(id,"STATE","UNRESOLVED_STATE_GAP","concurrent transition");
                });
            } finally {committed.countDown();}
            var snapshot=future.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(snapshot.recordedState()).isEqualTo("RUNNING");
            assertThat(snapshot.counters().requests()).as("later component reads must retain the first committed snapshot under an ambient READ_COMMITTED caller").isZero();
            assertThat(snapshot.totalUnresolved()).isZero();assertThat(snapshot.intervals()).isEmpty();
        }
    }
    @Test void callerPrivateWritesAreExcludedAndOriginalTransactionResumesUnchanged() {
        var id=seed();var caller=new org.springframework.transaction.support.TransactionTemplate(transactionManager);caller.setIsolationLevel(java.sql.Connection.TRANSACTION_READ_COMMITTED);
        caller.executeWithoutResult(status->{
            var original=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
            jdbc.sql("update marketdata.capture_sessions set reason='PRIVATE_UNCOMMITTED',requests=111 where session_id=:id").param("id",id).update();
            var snapshot=api.captureMonitor(id).orElseThrow();
            assertThat(snapshot.reason()).as("caller-private data is not committed monitor evidence").isEmpty();assertThat(snapshot.counters().requests()).isZero();
            assertThat(org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource)).isSameAs(original);
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isEqualTo(java.sql.Connection.TRANSACTION_READ_COMMITTED);
            assertThat(jdbc.sql("select reason from marketdata.capture_sessions where session_id=:id").param("id",id).query(String.class).single()).isEqualTo("PRIVATE_UNCOMMITTED");status.setRollbackOnly();
        });
        assertThat(jdbc.sql("select reason from marketdata.capture_sessions where session_id=:id").param("id",id).query(String.class).single()).isEmpty();
    }
    @Test void physicalReadOnlyRepeatableReadUsesSevenPayloadFreeStatementsAndDoesNotMutateEvidence() {
        var id=seed();gap(id,"STATE","UNRESOLVED_STATE_GAP","kept");var before=databaseFingerprint();
        org.mockito.Mockito.doAnswer(call->{
            var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
            assertThat(connection.getTransactionIsolation()).isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
            assertThat(connection.isReadOnly()).isTrue();assertThat(connection.getAutoCommit()).isFalse();return call.callRealMethod();
        }).when(store).monitorHeader(id);
        org.mockito.Mockito.clearInvocations(jdbc,collector,decoder);
        var first=api.captureMonitor(id).orElseThrow();
        var statements=org.mockito.Mockito.mockingDetails(jdbc).getInvocations().stream().filter(i->i.getMethod().getName().equals("sql")).map(i->(String)i.getArgument(0)).toList();
        assertThat(statements).hasSize(7).allSatisfy(sql->{
            assertThat(sql.toLowerCase()).doesNotContain("select *","notification","response","envelope","owner_authorization","holder","for update","advisory");
            assertThat(sql.toLowerCase().replace("payload_hash","")).doesNotContain("payload");
        });
        assertThat(statements.getFirst()).isEqualTo("set local statement_timeout='10s'");
        org.mockito.Mockito.verifyNoInteractions(collector,decoder);
        assertThat(api.captureMonitor(id).orElseThrow()).isEqualTo(first);assertThat(databaseFingerprint()).isEqualTo(before);
        assertThat(first.committedPositions()).hasSize(3).allSatisfy(p->assertThat(p.position()).isEmpty());
    }
    String databaseFingerprint() {
        var facts=new TreeMap<String,String>();
        for(var table:jdbc.sql("select tablename from pg_tables where schemaname='marketdata' order by tablename").query(String.class).list()) {
            facts.put(table,jdbc.sql("select md5(coalesce(string_agg(row::text,'|' order by row::text),'')) from (select to_jsonb(t) as row from marketdata."+table+" t) rows").query(String.class).single());
        }
        return PayloadFingerprint.sha256(facts.toString());
    }
    @Test void onlyExactRetainedRawProviderSlotAndDigestCanEstablishCommittedPositions() {
        var id=seed();var signature="1".repeat(63)+"A";var second="1".repeat(63)+"B";
        ledger.pending(id,"synthetic-holder",address(0),signature,100,"SECRET_NOTIFICATION");
        capture.transaction(id,"synthetic-holder",address(0),signature,"{\"slot\":100,\"transaction\":{\"signatures\":[\""+signature+"\"],\"message\":{}},\"meta\":{\"err\":null}}","SECRET_RESPONSE",START,true);
        ledger.pending(id,"synthetic-holder",address(0),second,101,"SECRET_NOTIFICATION");
        jdbc.sql("update marketdata.capture_dispositions set status='CAPTURED',raw_digest='sha256:'||repeat('0',64) where session_id=:id and signature=:signature").param("id",id).param("signature",second).update();
        var snapshot=api.captureMonitor(id).orElseThrow();
        assertThat(snapshot.committedPositions().stream().filter(p->p.pool().equals(address(0))).findFirst().orElseThrow().position()).contains(new PumpSwapCaptureMonitor.Position(signature,100));
        assertThat(snapshot.counters().transactions()).isEqualTo(2); // Existing counter semantics, not raw verification/coverage.
        var text=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(snapshot);assertThat(text).doesNotContain("SECRET_","synthetic-holder","owner-first-window","rawResult","configuration_json");
        jdbc.sql("update marketdata.capture_dispositions set slot=102 where session_id=:id and signature=:signature").param("id",id).param("signature",signature).update();
        assertThat(api.captureMonitor(id).orElseThrow().committedPositions()).allSatisfy(p->assertThat(p.position()).isEmpty());
    }
    @Test void groupedGapAndDispositionOverflowAndOversizedMetadataFailExplicitly() {
        var gapId=seed();for(int i=0;i<65;i++)gap(gapId,"STATE","STATUS_"+i,"kept");
        assertThatThrownBy(()->api.captureMonitor(gapId)).hasMessage("CAPTURE_MONITOR_RESULT_LIMIT");
        var dispositionId=seed();
        jdbc.sql("insert into marketdata.capture_dispositions(session_id,pool,signature,slot,status,notification,received_at) select :id,:pool,'sig-'||i,i,'status-'||i,'HIDDEN_NOTIFICATION',:at from generate_series(1,33) i")
            .param("id",dispositionId).param("pool",address(0)).param("at",START.atOffset(java.time.ZoneOffset.UTC)).update();
        assertThatThrownBy(()->api.captureMonitor(dispositionId)).hasMessage("CAPTURE_MONITOR_RESULT_LIMIT");
        var oversizedId=seed();gap(oversizedId,"STATE","UNKNOWN","雪".repeat(44000));
        assertThatThrownBy(()->api.captureMonitor(oversizedId)).hasMessage("CAPTURE_MONITOR_RESULT_LIMIT");
        jdbc.sql("update marketdata.capture_sessions set reason=repeat('雪',44000) where session_id=:id").param("id",oversizedId).update();
        assertThatThrownBy(()->api.captureMonitor(oversizedId)).hasMessage("CAPTURE_MONITOR_RESULT_LIMIT");
    }
    @Test void absentSessionAndSafeDatabaseFailureCannotBecomeTheSameResult() {
        var unknown=UUID.randomUUID();assertThat(api.captureMonitor(unknown)).isEmpty();assertThatThrownBy(()->api.captureMonitor(null)).isInstanceOf(NullPointerException.class);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("jdbc:SECRET_ENDPOINT password=SECRET_PROVIDER_KEY")).when(store).monitorHeader(unknown);
        assertThatThrownBy(()->api.captureMonitor(unknown)).isInstanceOf(IllegalStateException.class).hasMessage("CAPTURE_MONITOR_READ_FAILED").hasNoCause().satisfies(failure->{
            var trace=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(trace));assertThat(trace.toString()).doesNotContain("SECRET_");
        });
    }
    @Test void realPostgreSqlLockTimeoutFailsSafelyWithinFiniteReadDeadlineWithoutRetry() throws Exception {
        var id=seed();var start=System.nanoTime();
        try(var lock=dataSource.getConnection();var statement=lock.createStatement()) {
            lock.setAutoCommit(false);statement.execute("lock table marketdata.capture_gaps in access exclusive mode");
            org.mockito.Mockito.clearInvocations(store);
            try {assertThatThrownBy(()->api.captureMonitor(id)).isInstanceOf(IllegalStateException.class).hasMessage("CAPTURE_MONITOR_READ_FAILED").hasNoCause();}
            finally {lock.rollback();}
            org.mockito.Mockito.verify(store,org.mockito.Mockito.times(1)).monitorGapCounts(id);
        }
        assertThat(java.time.Duration.ofNanos(System.nanoTime()-start)).isLessThan(java.time.Duration.ofSeconds(15));
        assertThat(api.captureMonitor(id)).isPresent();
    }
    @TestConfiguration static class Clocks {@Bean @Primary MutableUtcClock monitorClock() {return new MutableUtcClock(START);}}
}
