package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static io.cryptoresearch.marketdata.application.PumpSwapRawOperationDecoderTest.*;
import static io.cryptoresearch.marketdata.application.PumpSwapChainTimeCandlesTest.*;
import java.util.*;
import java.time.Instant;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class PumpSwapChainTimeCandlesIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MarketDataApi api;
    @Autowired JdbcClient jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired io.cryptoresearch.marketdata.infrastructure.persistence.JdbcPumpSwapOperationStore store;
    @Autowired PumpSwapOperationCanonicalizer canonicalizer;
    @Autowired PublishPumpSwapOperationsUseCase publisher;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PersistPumpSwapOperationsUseCase persistence;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PumpSwapChainTimeCandleCalculator calculator;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean DecodePumpSwapOperationsUseCase decoder;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PumpSwapPoolCollector collector;
    @Test void physicalReadOwnsIndependentReadOnlyRepeatableReadDespiteAmbientCaller() {
        var s=nativeSource("metrics-pg-red",FROM.plusSeconds(1),4L,100);seed(s);
        var batch=api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(s),List.of("GAPS_UNKNOWN")));
        var request=request(batch,s.receivedAt());var caller=new TransactionTemplate(transactionManager);caller.setIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);
        org.mockito.Mockito.doAnswer(call->{
            var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
            assertThat(connection.getTransactionIsolation()).as("the actual batch read must establish its own RR snapshot").isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
            assertThat(connection.isReadOnly()).isTrue();assertThat(connection.getAutoCommit()).isFalse();return call.callRealMethod();
        }).when(persistence).find(batch.batchKey());
        assertThatCode(()->caller.executeWithoutResult(status->{
            var original=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
            assertThat(api.pumpSwapChainTimeCandles(request)).isPresent();
            assertThat(org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource)).isSameAs(original);
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            status.setRollbackOnly();
        })).as("public metrics API must suspend the ambient caller and complete a coherent read-only snapshot").doesNotThrowAnyException();
    }
    @Test void actualV5FixedV4V14ReadbackIsDeterministicAndDoesNotWriteAnyOwnedTables() throws Exception {
        var a=nativeSource("chain-durable-a",FROM.plusSeconds(1),1L,100);var b=nativeSource("chain-durable-b",FROM.plusSeconds(301),2L,101);seed(a);seed(b);
        var batch=api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(a,b),List.of("GAPS_UNKNOWN","FEES_UNKNOWN")));
        var request=request(batch,a.receivedAt());var before=databaseFingerprint();
        org.mockito.Mockito.clearInvocations(decoder,collector);
        org.mockito.Mockito.doAnswer(call->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).as("pure calculator runs after the owned read transaction has ended").isFalse();return call.callRealMethod();
        }).when(calculator).calculate(org.mockito.Mockito.any(),org.mockito.Mockito.any());
        var result=api.pumpSwapChainTimeCandles(request).orElseThrow();
        assertThat(result.source().resultFingerprint()).isEqualTo(batch.result().fingerprint());assertThat(result.operations()).hasSize(2);
        assertThat(result.operations()).extracting(PumpSwapChainTimeCandles.OperationReference::revisionKey).containsExactlyInAnyOrderElementsOf(batch.operations().stream().map(PumpSwapOperationPersistence.OperationRevision::revisionKey).toList());
        assertThat(result.transactions()).allSatisfy(tx->{assertThat(tx.receivedAt()).isEqualTo(a.receivedAt());assertThat(tx.status()).isEqualTo(PumpSwapOperations.Status.DECODED);});
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->api.pumpSwapChainTimeCandles(request).orElseThrow());var second=executor.submit(()->api.pumpSwapChainTimeCandles(request).orElseThrow());
            assertThat(first.get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(result);assertThat(second.get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(result);
        }
        assertThat(api.pumpSwapChainTimeCandles(request)).contains(result);assertThat(databaseFingerprint()).isEqualTo(before);
        org.mockito.Mockito.verifyNoInteractions(decoder,collector);
    }
    @Test void callerPrivateDeletionIsExcludedAndRollbackResumesOriginalConnection() {
        var s=nativeSource("metrics-private",FROM.plusSeconds(1),4L,100);seed(s);var batch=api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(s),List.of()));
        var q=request(batch,s.receivedAt());var expected=api.pumpSwapChainTimeCandles(q).orElseThrow();var before=databaseFingerprint();
        var caller=new TransactionTemplate(transactionManager);caller.setIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);
        caller.executeWithoutResult(status->{
            var original=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
            jdbc.sql("delete from marketdata.pumpswap_decode_batch_members where batch_key=:key").param("key",batch.batchKey()).update();
            jdbc.sql("delete from marketdata.pumpswap_decode_batches where batch_key=:key").param("key",batch.batchKey()).update();
            assertThat(api.pumpSwapChainTimeCandles(q)).as("uncommitted caller deletion is not committed source evidence").contains(expected);
            assertThat(org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource)).isSameAs(original);
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            assertThat(jdbc.sql("select count(*) from marketdata.pumpswap_decode_batches where batch_key=:key").param("key",batch.batchKey()).query(Integer.class).single()).isZero();status.setRollbackOnly();
        });
        assertThat(api.pumpSwapChainTimeCandles(q)).contains(expected);assertThat(databaseFingerprint()).isEqualTo(before);
    }
    @Test void independentSnapshotRetainsExactMembershipAcrossConcurrentCommittedBatchDeletion() throws Exception {
        var s=nativeSource("metrics-coherence",FROM.plusSeconds(1),4L,100);seed(s);var batch=api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(s),List.of()));var q=request(batch,s.receivedAt());
        var expected=api.pumpSwapChainTimeCandles(q).orElseThrow();var headerRead=new java.util.concurrent.CountDownLatch(1);var committed=new java.util.concurrent.CountDownLatch(1);var once=new java.util.concurrent.atomic.AtomicBoolean();
        var original=(org.springframework.jdbc.core.JdbcTemplate)org.springframework.test.util.ReflectionTestUtils.getField(store,"jdbc");
        var instrumented=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class,org.mockito.Mockito.withSettings().spiedInstance(original).defaultAnswer(call->{
            var value=call.callRealMethod();var args=call.getArguments();
            if(call.getMethod().getName().equals("query") && args.length>0 && args[0] instanceof String sql && sql.startsWith("select * from marketdata.pumpswap_decode_batches") && once.compareAndSet(false,true)) {
                headerRead.countDown();assertThat(committed.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }return value;
        }));
        org.springframework.test.util.ReflectionTestUtils.setField(store,"jdbc",instrumented);
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var future=executor.submit(()->{var caller=new TransactionTemplate(transactionManager);caller.setIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);return caller.execute(status->api.pumpSwapChainTimeCandles(q).orElseThrow());});
            try {assertThat(headerRead.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();new TransactionTemplate(transactionManager).executeWithoutResult(status->{
                jdbc.sql("delete from marketdata.pumpswap_decode_batch_members where batch_key=:key").param("key",batch.batchKey()).update();
                jdbc.sql("delete from marketdata.pumpswap_decode_batches where batch_key=:key").param("key",batch.batchKey()).update();
            });}
            finally {committed.countDown();}
            assertThat(future.get(15,java.util.concurrent.TimeUnit.SECONDS)).as("all source rows must come from the first physical RR snapshot").isEqualTo(expected);
        } finally {committed.countDown();org.springframework.test.util.ReflectionTestUtils.setField(store,"jdbc",original);}
        assertThat(api.pumpSwapChainTimeCandles(q)).isEmpty();
    }
    @Test void unknownKeyUnsupportedVersionAndCorruptedSourceNeverBecomePartialMetrics() {
        var s=nativeSource("metrics-fail-closed",FROM.plusSeconds(1),4L,100);seed(s);var batch=api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(s),List.of()));var q=request(batch,s.receivedAt());
        assertThat(api.pumpSwapChainTimeCandles(new PumpSwapChainTimeCandles.Request("sha256:"+"0".repeat(64),q.fromInclusive(),q.toExclusive(),q.knowledgeCutoff()))).isEmpty();
        var r=batch.result();var future=new PumpSwapOperations.Result("synthetic-future",r.schemaVersion(),r.canonicalizationVersion(),r.pinnedSourceHashes(),r.transactions(),r.operations(),r.limitations(),r.fingerprint());var old=publisher.publish(canonicalizer.prepare(future));
        var before=databaseFingerprint();assertThatThrownBy(()->api.pumpSwapChainTimeCandles(request(old,s.receivedAt()))).hasMessage("CHAIN_TIME_UNSUPPORTED_SOURCE_VERSION");assertThat(databaseFingerprint()).isEqualTo(before);
        var key=batch.operations().getFirst().revisionKey();var content=jdbc.sql("select content from marketdata.pumpswap_operation_derivations where revision_key=:key").param("key",key).query(String.class).single();
        jdbc.sql("update marketdata.pumpswap_operation_derivations set content='{}' where revision_key=:key").param("key",key).update();
        try {var corrupt=databaseFingerprint();assertThatThrownBy(()->api.pumpSwapChainTimeCandles(q)).isInstanceOf(RuntimeException.class);assertThat(databaseFingerprint()).isEqualTo(corrupt);}
        finally {jdbc.sql("update marketdata.pumpswap_operation_derivations set content=:content where revision_key=:key").param("content",content).param("key",key).update();}
        assertThat(api.pumpSwapChainTimeCandles(q)).isPresent();
    }
    @Test void actualBlockedPostgreSqlQueryFailsWithinTenSecondDeadlineWithoutRetryOrWrites() throws Exception {
        var s=nativeSource("metrics-deadline",FROM.plusSeconds(1),4L,100);seed(s);var batch=api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(s),List.of()));var q=request(batch,s.receivedAt());var before=databaseFingerprint();
        org.mockito.Mockito.clearInvocations(persistence);long start=System.nanoTime();
        try(var lock=dataSource.getConnection();var statement=lock.createStatement()) {
            lock.setAutoCommit(false);statement.execute("lock table marketdata.pumpswap_decode_batches in access exclusive mode");
            try {assertThatThrownBy(()->api.pumpSwapChainTimeCandles(q)).hasMessage("CHAIN_TIME_READ_FAILED");}finally {lock.rollback();}
        }
        var elapsed=java.time.Duration.ofNanos(System.nanoTime()-start);assertThat(elapsed).isGreaterThan(java.time.Duration.ofSeconds(8)).isLessThan(java.time.Duration.ofSeconds(15));
        org.mockito.Mockito.verify(persistence,org.mockito.Mockito.times(1)).find(batch.batchKey());assertThat(databaseFingerprint()).isEqualTo(before);assertThat(api.pumpSwapChainTimeCandles(q)).isPresent();
    }
    @Test void malformedRequestRejectsBeforeAnyBatchOrProviderAccess() {
        org.mockito.Mockito.clearInvocations(persistence,decoder,collector);
        assertThatThrownBy(()->api.pumpSwapChainTimeCandles(new PumpSwapChainTimeCandles.Request("invalid",Instant.EPOCH,Instant.EPOCH.plusSeconds(300),Instant.EPOCH.plusSeconds(300))))
            .hasMessage("CHAIN_TIME_INVALID_REQUEST");
        org.mockito.Mockito.verifyNoInteractions(persistence,decoder,collector);
    }
    String databaseFingerprint() {
        var fields=new TreeMap<String,String>();
        for(var schema:List.of("marketdata","signal","evaluation"))
            for(var table:jdbc.sql("select tablename from pg_tables where schemaname=:schema order by tablename").param("schema",schema).query(String.class).list())
                fields.put(schema+"."+table,jdbc.sql("select md5(coalesce(string_agg(row::text,'|' order by row::text),'')) from (select to_jsonb(t) as row from "+schema+"."+table+" t) rows").query(String.class).single());
        return PayloadFingerprint.sha256(fields.toString());
    }
    static PumpSwapChainTimeCandles.Request request(PumpSwapOperationPersistence.StoredBatch batch,Instant received) {
        return new PumpSwapChainTimeCandles.Request(batch.batchKey(),FROM,FROM.plusSeconds(600),Instant.parse("2027-01-01T00:00:00Z"));
    }
    void seed(PumpSwapOperations.RecordedTransaction s) {
        jdbc.sql("""
            insert into marketdata.raw_transactions(chain_id,transaction_value,provider,payload,payload_hash,observed_block_position,source_event_time,received_at,admitted_at,ingested_at)
            values(:chain,:signature,:provider,:payload,:digest,:slot,:sourceTime,:received,:admitted,:ingested)
            """).param("chain",s.chain().value()).param("signature",s.signature()).param("provider",s.provider())
            .param("payload",s.rawResult()).param("digest",s.rawDigest()).param("slot",s.slot())
            .param("sourceTime",s.sourceTime().map(t->t.atOffset(java.time.ZoneOffset.UTC)).orElse(null),java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
            .param("received",s.receivedAt().atOffset(java.time.ZoneOffset.UTC)).param("admitted",s.admittedAt().atOffset(java.time.ZoneOffset.UTC))
            .param("ingested",s.ingestedAt().atOffset(java.time.ZoneOffset.UTC)).update();
    }
}
