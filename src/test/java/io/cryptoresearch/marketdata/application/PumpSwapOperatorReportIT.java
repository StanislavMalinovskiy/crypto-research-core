package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static io.cryptoresearch.marketdata.application.PumpSwapRawOperationDecoderTest.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.*;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(PumpSwapOperatorReportIT.Clocks.class)
class PumpSwapOperatorReportIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MarketDataApi api;
    @Autowired CaptureLedgerUseCase ledger;
    @Autowired JdbcClient jdbc;
    @Autowired ReportClock clock;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ReadCaptureMonitorUseCase monitor;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ReadPumpSwapPoolMetricsUseCase metrics;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PersistPumpSwapOperationsUseCase persistence;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean DecodePumpSwapOperationsUseCase decoder;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PublishPumpSwapOperationsUseCase publisher;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PumpSwapPoolCollector collector;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore store;
    @Test void publicReportComposesExactlyNamedRealPanelsWithoutInventingTheirAssociation() {
        var session=seedSession();var s=source(fixture("1","buy",true,1),"report-first-red");seedRaw(s);
        var batch=api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(s),List.of("GAPS_UNKNOWN")));
        var expectedMonitor=api.captureMonitor(session).orElseThrow();var expectedMetrics=api.pumpSwapPoolMetrics(metricsRequest(batch,s.receivedAt())).orElseThrow();
        var request=new PumpSwapOperatorReport.Request(session,expectedMetrics.request());
        assertThatCode(()->api.pumpSwapOperatorReport(request)).as("public report must expose the two explicitly named real independent panels").doesNotThrowAnyException();
        var report=api.pumpSwapOperatorReport(request);var json=JsonMapper.builder().build().readTree(report.json());
        assertThat(json.path("monitor").path("snapshot").path("sessionId").asText()).isEqualTo(session.toString());
        assertThat(json.path("monitor").path("snapshot").path("observedAt").asText()).isEqualTo(expectedMonitor.observedAt().toString());
        assertThat(json.path("metrics").path("result").path("fingerprint").asText()).isEqualTo(expectedMetrics.fingerprint());
        assertThat(json.path("metrics").path("result").path("selectedOperations").get(0).path("revisionKey").asText()).isEqualTo(batch.operations().getFirst().revisionKey());
        assertThat(report.association()).isEqualTo("REQUESTED_TOGETHER_UNVERIFIED");assertThat(report.consistency()).isEqualTo("INDEPENDENT_PANELS");
    }
    record Inputs(UUID session,PumpSwapOperationPersistence.StoredBatch batch,PumpSwapOperatorReport.Request request) {}
    Inputs inputs(String signature) {
        var session=seedSession();var s=source(fixture("1","buy",true,1),signature);seedRaw(s);var batch=api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(s),List.of("GAPS_UNKNOWN")));
        return new Inputs(session,batch,new PumpSwapOperatorReport.Request(session,metricsRequest(batch,s.receivedAt())));
    }
    void resetReadInvocations(){org.mockito.Mockito.clearInvocations(monitor,metrics,persistence,decoder,publisher,collector,store);}
    void oneEach(Inputs in) {
        var order=org.mockito.Mockito.inOrder(monitor,metrics);order.verify(monitor).read(in.session());order.verify(metrics).read(in.request().metrics());
        org.mockito.Mockito.verify(monitor,org.mockito.Mockito.times(1)).read(in.session());org.mockito.Mockito.verify(metrics,org.mockito.Mockito.times(1)).read(in.request().metrics());
        org.mockito.Mockito.verify(persistence,org.mockito.Mockito.times(1)).find(in.batch().batchKey());org.mockito.Mockito.verify(persistence,org.mockito.Mockito.never()).persist(org.mockito.Mockito.any());
        org.mockito.Mockito.verifyNoInteractions(decoder,publisher,collector);
    }
    @Test void exactTwoReadsCompletePanelEqualityAndAllTableFieldsRemainUnchanged() {
        var in=inputs("report-two-read");var expectedMonitor=api.captureMonitor(in.session()).orElseThrow();var expectedMetrics=api.pumpSwapPoolMetrics(in.request().metrics()).orElseThrow();
        var expected=new PumpSwapOperatorReportFormatter().format(in.request(),Optional.of(expectedMonitor),Optional.of(expectedMetrics));var before=databaseFingerprint();resetReadInvocations();
        var report=api.pumpSwapOperatorReport(in.request());assertThat(report).isEqualTo(expected);oneEach(in);assertThat(databaseFingerprint()).isEqualTo(before);
        assertThat(report.json()).doesNotContain("test-only-holder","owner-first-window","rawResult","configuration_json","LinkageProof");
    }
    @Test void independentUnknownPanelsStillReadExactlyOnceEachAndMalformedInputReadsNothing() {
        var in=inputs("report-absence");var unknown=new PumpSwapPoolMetrics.Request("sha256:"+"0".repeat(64),in.request().metrics().fromInclusive(),in.request().metrics().toExclusive(),in.request().metrics().observationCutoff(),60);
        for(boolean capture:List.of(false,true))for(boolean activity:List.of(false,true)) {
            var request=new PumpSwapOperatorReport.Request(capture?in.session():UUID.randomUUID(),activity?in.request().metrics():unknown);resetReadInvocations();
            var report=api.pumpSwapOperatorReport(request);var tree=JsonMapper.builder().build().readTree(report.json());
            assertThat(tree.path("monitor").path("snapshot").isNull()).isEqualTo(!capture);assertThat(tree.path("metrics").path("result").isNull()).isEqualTo(!activity);
            var order=org.mockito.Mockito.inOrder(monitor,metrics);order.verify(monitor).read(request.sessionId());order.verify(metrics).read(request.metrics());
            org.mockito.Mockito.verify(persistence,org.mockito.Mockito.times(1)).find(request.metrics().batchKey());org.mockito.Mockito.verifyNoInteractions(decoder,publisher,collector);
        }
        for(var bad:Arrays.asList(null,new PumpSwapOperatorReport.Request(null,in.request().metrics()),new PumpSwapOperatorReport.Request(in.session(),null),new PumpSwapOperatorReport.Request(in.session(),new PumpSwapPoolMetrics.Request("bad",Instant.EPOCH,Instant.EPOCH.plusSeconds(60),Instant.EPOCH.plusSeconds(60),60)))) {
            resetReadInvocations();assertThatThrownBy(()->api.pumpSwapOperatorReport(bad)).hasMessage("OPERATOR_REPORT_INVALID_REQUEST").hasNoCause();org.mockito.Mockito.verifyNoInteractions(monitor,metrics,persistence,decoder,publisher,collector,store);
        }
    }
    @Test void existingPhysicalIndependentReadsExcludeCallerPrivateWritesAndResumeCallerRollback() {
        var in=inputs("report-ambient");var expected=api.pumpSwapOperatorReport(in.request());var before=databaseFingerprint();
        var readConnections=new ArrayList<java.sql.Connection>();
        org.mockito.Mockito.doAnswer(call->{var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);assertReadOnlySnapshot(connection);readConnections.add(connection);return call.callRealMethod();}).when(store).monitorHeader(in.session());
        org.mockito.Mockito.doAnswer(call->{var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);assertReadOnlySnapshot(connection);readConnections.add(connection);return call.callRealMethod();}).when(persistence).find(in.batch().batchKey());
        resetReadInvocations();var caller=new org.springframework.transaction.support.TransactionTemplate(transactionManager);caller.setIsolationLevel(java.sql.Connection.TRANSACTION_READ_COMMITTED);
        caller.executeWithoutResult(status->{
            var original=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
            jdbc.sql("update marketdata.capture_sessions set reason='CALLER_PRIVATE' where session_id=:id").param("id",in.session()).update();
            jdbc.sql("delete from marketdata.pumpswap_decode_batch_members where batch_key=:key").param("key",in.batch().batchKey()).update();
            jdbc.sql("delete from marketdata.pumpswap_decode_batches where batch_key=:key").param("key",in.batch().batchKey()).update();
            assertThat(api.pumpSwapOperatorReport(in.request())).isEqualTo(expected);assertThat(org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource)).isSameAs(original);
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isEqualTo(java.sql.Connection.TRANSACTION_READ_COMMITTED);
            assertThat(readConnections).hasSize(2).allSatisfy(connection->assertThat(connection).isNotSameAs(original));
            assertThat(readConnections.get(0)).as("source acquisitions are separate owned transactions, not one outer snapshot").isNotSameAs(readConnections.get(1));
            assertThat(jdbc.sql("select reason from marketdata.capture_sessions where session_id=:id").param("id",in.session()).query(String.class).single()).isEqualTo("CALLER_PRIVATE");status.setRollbackOnly();
        });
        oneEach(in);assertThat(databaseFingerprint()).isEqualTo(before);
    }
    static void assertReadOnlySnapshot(java.sql.Connection connection) throws java.sql.SQLException {
        assertThat(connection.getTransactionIsolation()).isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);assertThat(connection.isReadOnly()).isTrue();assertThat(connection.getAutoCommit()).isFalse();
    }
    @Test void committedTransitionBetweenPanelsDoesNotRewriteAlreadyObservedMonitorOrFrozenMetrics() {
        var in=inputs("report-between-panels");var first=api.pumpSwapOperatorReport(in.request());var once=new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(call->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).as("composer owns no outer transaction").isFalse();
            if(once.compareAndSet(false,true)){jdbc.sql("update marketdata.capture_sessions set reason='BETWEEN_PANELS',state='STOPPED' where session_id=:id").param("id",in.session()).update();clock.set(PumpSwapPoolCaptureTest.START.plusSeconds(10));}
            return call.callRealMethod();
        }).when(metrics).read(in.request().metrics());
        resetReadInvocations();var paired=api.pumpSwapOperatorReport(in.request());oneEach(in);assertThat(paired).isEqualTo(first);
        var advanced=api.pumpSwapOperatorReport(in.request());var mapper=JsonMapper.builder().build();var prior=mapper.readTree(first.json());var current=mapper.readTree(advanced.json());
        assertThat(current.path("monitor").path("snapshot").path("reason").asText()).isEqualTo("BETWEEN_PANELS");assertThat(current.path("monitor").path("snapshot").path("observedAt").asText()).isEqualTo(PumpSwapPoolCaptureTest.START.plusSeconds(10).toString());
        assertThat(current.path("metrics")).isEqualTo(prior.path("metrics"));assertThat(advanced.association()).isEqualTo("REQUESTED_TOGETHER_UNVERIFIED");assertThat(advanced.consistency()).isEqualTo("INDEPENDENT_PANELS");
    }
    @Test void componentFailureStopsCompositionAndNeverLeaksExceptionOrReturnsPartialReport() {
        var in=inputs("report-failure");var before=databaseFingerprint();
        org.mockito.Mockito.doThrow(new IllegalStateException("jdbc:SECRET_ENDPOINT SECRET_KEY")).when(monitor).read(in.session());resetReadInvocations();
        assertThatThrownBy(()->api.pumpSwapOperatorReport(in.request())).hasMessage("OPERATOR_REPORT_READ_FAILED").hasNoCause().satisfies(failure->{var out=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(out));assertThat(out.toString()).doesNotContain("SECRET_");});
        org.mockito.Mockito.verify(monitor,org.mockito.Mockito.times(1)).read(in.session());org.mockito.Mockito.verifyNoInteractions(metrics,persistence,decoder,publisher,collector);assertThat(databaseFingerprint()).isEqualTo(before);
    }
    @Test void realUnderlyingBlockedMonitorReadIsFiniteWithoutRetryAndSecondReadRecoversAfterUnlock() throws Exception {
        var in=inputs("report-blocked");var before=databaseFingerprint();resetReadInvocations();long start=System.nanoTime();
        try(var lock=dataSource.getConnection();var statement=lock.createStatement()) {
            lock.setAutoCommit(false);statement.execute("lock table marketdata.capture_sessions in access exclusive mode");
            try{assertThatThrownBy(()->api.pumpSwapOperatorReport(in.request())).hasMessage("OPERATOR_REPORT_READ_FAILED").hasNoCause();}finally{lock.rollback();}
        }
        var elapsed=Duration.ofNanos(System.nanoTime()-start);assertThat(elapsed).isGreaterThan(Duration.ofSeconds(8)).isLessThan(Duration.ofSeconds(15));
        org.mockito.Mockito.verify(monitor,org.mockito.Mockito.times(1)).read(in.session());org.mockito.Mockito.verifyNoInteractions(metrics,persistence,decoder,publisher,collector);assertThat(databaseFingerprint()).isEqualTo(before);
        assertThat(api.pumpSwapOperatorReport(in.request()).metricsStatus()).isEqualTo("METRICS_PRESENT");
    }
    @Test void corruptMetricsSourceFailsWholeReportAfterMonitorWithoutMutationOrPartialSuccess() {
        var in=inputs("report-corrupt");var key=in.batch().batchKey();var content=jdbc.sql("select content from marketdata.pumpswap_decode_batches where batch_key=:key").param("key",key).query(String.class).single();
        jdbc.sql("update marketdata.pumpswap_decode_batches set content='{}' where batch_key=:key").param("key",key).update();
        try{var before=databaseFingerprint();resetReadInvocations();assertThatThrownBy(()->api.pumpSwapOperatorReport(in.request())).hasMessage("OPERATOR_REPORT_READ_FAILED").hasNoCause();oneEach(in);assertThat(databaseFingerprint()).isEqualTo(before);}
        finally{jdbc.sql("update marketdata.pumpswap_decode_batches set content=:content where batch_key=:key").param("key",key).param("content",content).update();}
        assertThat(api.pumpSwapOperatorReport(in.request()).metricsStatus()).isEqualTo("METRICS_PRESENT");
    }
    String databaseFingerprint() {
        var fields=new TreeMap<String,String>();for(var schema:List.of("marketdata","signal","evaluation"))
            for(var table:jdbc.sql("select tablename from pg_tables where schemaname=:schema order by tablename").param("schema",schema).query(String.class).list())
                fields.put(schema+"."+table,jdbc.sql("select md5(coalesce(string_agg(row::text,'|' order by row::text),'')) from (select to_jsonb(t) as row from "+schema+"."+table+" t) rows").query(String.class).single());
        return PayloadFingerprint.sha256(fields.toString());
    }
    UUID seedSession() {
        var id=UUID.randomUUID();clock.set(PumpSwapPoolCaptureTest.START);var c=PumpSwapPoolCaptureTest.configuration(id,0L);var a=c.authority();
        var authority=new PumpSwapCapture.Authority(id.toString(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),a.ceilingHundredths(),a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),id.toString(),true,true);
        ledger.open(new PumpSwapCapture.Configuration(id,c.version(),c.provider(),c.startsAt(),c.pools(),c.limits(),authority),"test-only-holder");return id;
    }
    static PumpSwapPoolMetrics.Request metricsRequest(PumpSwapOperationPersistence.StoredBatch batch,Instant received) {
        var from=Instant.ofEpochSecond(Math.floorDiv(received.getEpochSecond(),60)*60);
        return new PumpSwapPoolMetrics.Request(batch.batchKey(),from,from.plusSeconds(60),from.plusSeconds(60),60);
    }
    void seedRaw(PumpSwapOperations.RecordedTransaction s) {
        jdbc.sql("""
            insert into marketdata.raw_transactions(chain_id,transaction_value,provider,payload,payload_hash,observed_block_position,source_event_time,received_at,admitted_at,ingested_at)
            values(:chain,:signature,:provider,:payload,:digest,:slot,:sourceTime,:received,:admitted,:ingested)
            """).param("chain",s.chain().value()).param("signature",s.signature()).param("provider",s.provider()).param("payload",s.rawResult()).param("digest",s.rawDigest()).param("slot",s.slot())
            .param("sourceTime",s.sourceTime().map(t->t.atOffset(ZoneOffset.UTC)).orElse(null),java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
            .param("received",s.receivedAt().atOffset(ZoneOffset.UTC)).param("admitted",s.admittedAt().atOffset(ZoneOffset.UTC)).param("ingested",s.ingestedAt().atOffset(ZoneOffset.UTC)).update();
    }
    static class ReportClock extends Clock {
        private final java.util.concurrent.atomic.AtomicReference<Instant> at=new java.util.concurrent.atomic.AtomicReference<>(PumpSwapPoolCaptureTest.START);
        void set(Instant value){at.set(value);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){if(!zone.equals(ZoneOffset.UTC))throw new IllegalArgumentException();return this;}
        @Override public Instant instant(){return at.get();}
    }
    @TestConfiguration static class Clocks {@Bean @Primary ReportClock reportClock(){return new ReportClock();}}
}
