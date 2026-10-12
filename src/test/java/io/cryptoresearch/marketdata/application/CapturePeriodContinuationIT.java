package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.PumpSwapCapture;
import io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture;
import io.cryptoresearch.marketdata.api.PumpSwapCapturePeriodContinuation;
import io.cryptoresearch.marketdata.infrastructure.persistence.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(PumpSwapContinuousCaptureIT.Clocks.class)
class CapturePeriodContinuationIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired EnrollCapturePeriodContinuationUseCase enrollment;
    @Autowired ContinuousCaptureLedgerUseCase ledger;
    @Autowired JdbcCaptureStore capture;
    @Autowired JdbcContinuousCaptureStore deployments;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired MutableUtcClock clock;
    @BeforeEach void clean(){jdbc.sql("truncate marketdata.capture_periods cascade").update();}
    Instant dbNow(){return jdbc.sql("select clock_timestamp() as t").query((r,n)->r.getTimestamp("t").toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)).single();}
    static PumpSwapContinuousCapture.Configuration configuration(String key,Instant start,Instant end,long openingCu,long retainedOpening) {
        var original=PumpSwapContinuousCaptureIT.deployment(key,4,2);var t=original.template();
        var authority=new PumpSwapCapture.Authority("period-"+key,end,"OWNER_REPORTED",openingCu,20,9_523_809_500L,new BigDecimal("50"),new BigDecimal("0.525"),new BigDecimal("0.0002"),"owner-month-"+key,true,true);
        var template=new PumpSwapCapture.Configuration(t.sessionId(),t.version(),t.provider(),start,t.pools(),t.limits(),authority);
        var o=new PumpSwapContinuousCapture.Resources(0,0,0,retainedOpening,0,openingCu+20,0);
        var a=original.allowances();
        var caps=new PumpSwapContinuousCapture.Resources(a.requests(),a.receivedBytes(),a.subscriptionBytes(),70_000_000_000L,a.rpcHundredths(),a.reservedHundredths()+openingCu+20,a.seconds());
        return new PumpSwapContinuousCapture.Configuration(key,original.version(),template,caps,o,start.plusSeconds(600),4,180,2,60,2048,100);
    }
    PumpSwapCapturePeriodContinuation seed() {
        var db=dbNow();clock.set(db);var old=configuration("old-month",db.minusSeconds(3600),db.minusSeconds(600),100,1000);
        var digest=new CanonicalFingerprint().field("version","continuous-configuration-v1").field("configuration",deployments.encode(old)).finish();
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->{
            capture.providerAdmissionLock("alchemy");capture.period(old.template(),CaptureBudget.ceiling(old.template()),120);deployments.insert(old,digest,"old-holder",db.minusSeconds(60));
        });
        jdbc.sql("update marketdata.capture_deployments set state='STOPPED',reason='DEPLOYMENT_GLOBAL_END' where deployment_key='old-month'").update();
        var next=configuration("next-month",db,db.plusSeconds(86400),200,1000);
        return new PumpSwapCapturePeriodContinuation("continuous-period-continuation-v1",old.deploymentKey(),digest,old.template().authority().periodEnd(),next,"owner-standing-50",new PumpSwapCapturePeriodContinuation.Baseline("OWNER_REPORTED",db,200L,20L));
    }
    String oldRows(){return jdbc.sql("select row_to_json(d)::text from marketdata.capture_deployments d where deployment_key='old-month'").query(String.class).single();}
    @Test void endedPredecessorAdmitsOneSeparateMonthWithoutChangingHistoryOrOpeningTwice() {
        var request=seed();var before=oldRows();
        assertThatCode(()->enrollment.enroll(request)).as("explicit ended terminal predecessor admits its distinct receipted successor").doesNotThrowAnyException();
        var receipt=enrollment.enroll(request);assertThat(receipt.successorDeploymentKey()).isEqualTo("next-month");
        assertThat(oldRows()).isEqualTo(before);assertThat(capture.reserved("period-old-month")).isEqualTo(120);
        assertThat(capture.reserved("period-next-month")).isEqualTo(220);
        var admitted=ledger.admit(request.successor(),"successor-holder");assertThat(admitted.segment()).isNotNull();
        assertThat(capture.reserved("period-next-month")).isEqualTo(220+ContinuousCaptureLedgerUseCase.resources(admitted.segment().configuration()).reservedHundredths());
        assertThat(enrollment.enroll(request)).isEqualTo(receipt);assertThat(oldRows()).isEqualTo(before);
        assertThat(jdbc.sql("select count(*) from marketdata.capture_gaps where session_id=:id and reason like 'PERIOD_BOUNDARY:%' and from_slot is null and boundary_signature is null")
            .param("id",admitted.segment().configuration().sessionId()).query(Long.class).single()).isEqualTo(6);
    }
    PumpSwapCapturePeriodContinuation withSuccessor(PumpSwapCapturePeriodContinuation r,PumpSwapContinuousCapture.Configuration next) {
        return new PumpSwapCapturePeriodContinuation(r.version(),r.predecessorDeploymentKey(),r.predecessorConfigurationDigest(),r.periodStart(),next,r.ownerDecision(),r.baseline());
    }
    long receipts(){return jdbc.sql("select count(*) from marketdata.capture_period_continuations").query(Long.class).single();}
    @Test void earlyStopLiveLeaseAndFutureBaselineCannotOpenAnotherAllocation() {
        for(var mode:List.of("RUNNING","LEASE","PERIOD","BASELINE")) {
            clean();var r=seed();var before=oldRows();
            if(mode.equals("RUNNING"))jdbc.sql("update marketdata.capture_deployments set state='RUNNING',reason='' where deployment_key='old-month'").update();
            if(mode.equals("LEASE"))jdbc.sql("update marketdata.capture_deployments set locked_until=clock_timestamp()+interval '1 hour' where deployment_key='old-month'").update();
            if(mode.equals("PERIOD"))jdbc.sql("update marketdata.capture_periods set period_end=clock_timestamp()+interval '1 hour' where account_period='period-old-month'").update();
            if(mode.equals("BASELINE"))r=new PumpSwapCapturePeriodContinuation(r.version(),r.predecessorDeploymentKey(),r.predecessorConfigurationDigest(),r.periodStart(),r.successor(),r.ownerDecision(),new PumpSwapCapturePeriodContinuation.Baseline("OWNER_REPORTED",dbNow().plusSeconds(3600),200L,20L));
            var request=r;var actual=oldRows();assertThatThrownBy(()->enrollment.enroll(request)).isInstanceOf(IllegalStateException.class);
            assertThat(oldRows()).isEqualTo(actual);assertThat(receipts()).isZero();assertThat(capture.reserved("period-old-month")).isEqualTo(120);
        }
    }
    @Test void concurrentEqualRetryAndNamedForksSerializeExactlyOneChild() throws Exception {
        var r=seed();var first=java.util.concurrent.CompletableFuture.supplyAsync(()->enrollment.enroll(r));var second=java.util.concurrent.CompletableFuture.supplyAsync(()->enrollment.enroll(r));
        assertThat(first.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(second.get(10,java.util.concurrent.TimeUnit.SECONDS));assertThat(receipts()).isEqualTo(1);
        var named=configuration("other-child",r.successor().template().startsAt(),r.successor().template().authority().periodEnd(),200,1000);
        assertThatThrownBy(()->enrollment.enroll(withSuccessor(r,named))).hasMessage("CONTINUATION_ACTIVE_OR_PERIOD_NOT_READY");assertThat(receipts()).isEqualTo(1);
    }
    @Test void ordinaryRenamedHistoryCannotBypassReceiptAndManualGuardRemains() {
        var r=seed();var before=oldRows();
        assertThatThrownBy(()->ledger.admit(r.successor(),"unreceipted")).hasMessage("CONTINUOUS_CONFIGURATION_CONFLICT");
        assertThat(oldRows()).isEqualTo(before);assertThat(jdbc.sql("select count(*) from marketdata.capture_periods").query(Long.class).single()).isEqualTo(1);
        enrollment.enroll(r);
        assertThatThrownBy(()->capture.manualStartGuard("alchemy")).isInstanceOf(IllegalStateException.class);
    }
    @Test void insufficientGlobalStorageKnownOverrunAndMissingDimensionFailClosed() {
        for(var mode:List.of("OVERRUN","UNKNOWN","EXPANSION")) {
            clean();var r=seed();var old=deployments.lockProvider("alchemy").configuration();
            capture.insertSession(old.template(),"sha256:"+"a".repeat(64),"old-session-holder",dbNow().minusSeconds(60),dbNow().minusSeconds(60),1);
            jdbc.sql("update marketdata.capture_sessions set state='STOPPED',reason='CAPTURE_TIME_LIMIT',retained_bytes=:bytes")
                .param("bytes",mode.equals("OVERRUN")?70_000_000_000L:0L).update();
            if(mode.equals("UNKNOWN"))jdbc.sql("update marketdata.capture_sessions set configuration_json=(configuration_json::jsonb #- '{limits,retainedBytes}')::text").update();
            var request=r;
            if(mode.equals("EXPANSION")) {
                var c=r.successor();var a=c.allowances();var raised=new PumpSwapContinuousCapture.Resources(a.requests(),a.receivedBytes(),a.subscriptionBytes(),a.retainedBytes()+1,a.rpcHundredths(),a.reservedHundredths(),a.seconds());
                request=withSuccessor(r,new PumpSwapContinuousCapture.Configuration(c.deploymentKey(),c.version(),c.template(),raised,c.opening(),c.globalEnd(),c.maxSegments(),c.segmentSeconds(),c.maxPages(),c.decodeSeconds(),c.receiptBytes(),c.pollMillis()));
            }
            var frozen=request;var before=oldRows();assertThatThrownBy(()->enrollment.enroll(frozen)).isInstanceOf(IllegalStateException.class);assertThat(oldRows()).isEqualTo(before);assertThat(receipts()).isZero();
        }
    }
    @Test void activeOrLinkedProviderSessionBlocksAndExpiredUnlinkedRemainsUntouched() {
        for(boolean live:List.of(true,false)) {
            clean();var r=seed();var old=deployments.lockProvider("alchemy").configuration();var db=dbNow();
            capture.insertSession(old.template(),"sha256:"+"b".repeat(64),"manual-holder",live?db.plusSeconds(60):db.minusSeconds(60),db.minusSeconds(60),1);
            var before=jdbc.sql("select row_to_json(s)::text from marketdata.capture_sessions s").query(String.class).single();
            if(live)assertThatThrownBy(()->enrollment.enroll(r)).hasMessage("CONTINUATION_ACTIVE_WORKLOAD");
            else {
                var c=r.successor();var o=c.opening();var retained=new PumpSwapContinuousCapture.Resources(o.requests(),o.receivedBytes(),o.subscriptionBytes(),old.template().limits().retainedBytes(),o.rpcHundredths(),o.reservedHundredths(),o.seconds());
                var next=new PumpSwapContinuousCapture.Configuration(c.deploymentKey(),c.version(),c.template(),c.allowances(),retained,c.globalEnd(),c.maxSegments(),c.segmentSeconds(),c.maxPages(),c.decodeSeconds(),c.receiptBytes(),c.pollMillis());
                assertThat(enrollment.enroll(withSuccessor(r,next)).retainedOpeningBytes()).isEqualTo(old.template().limits().retainedBytes());
            }
            assertThat(jdbc.sql("select row_to_json(s)::text from marketdata.capture_sessions s").query(String.class).single()).isEqualTo(before);
        }
    }
    @Test void missingAndTooOldBaselineSamePeriodAndAlteredDigestRejectWithoutWrites() {
        var r=seed();var before=oldRows();
        var absent=new PumpSwapCapturePeriodContinuation(r.version(),r.predecessorDeploymentKey(),r.predecessorConfigurationDigest(),r.periodStart(),r.successor(),r.ownerDecision(),null);
        var stale=new PumpSwapCapturePeriodContinuation(r.version(),r.predecessorDeploymentKey(),r.predecessorConfigurationDigest(),r.periodStart(),r.successor(),r.ownerDecision(),new PumpSwapCapturePeriodContinuation.Baseline("OWNER_REPORTED",r.periodStart().minusSeconds(1),200L,20L));
        var wrong=new PumpSwapCapturePeriodContinuation(r.version(),r.predecessorDeploymentKey(),"sha256:"+"0".repeat(64),r.periodStart(),r.successor(),r.ownerDecision(),r.baseline());
        for(var bad:List.of(absent,stale,wrong))assertThatThrownBy(()->enrollment.enroll(bad)).isInstanceOf(RuntimeException.class);
        assertThat(receipts()).isZero();assertThat(oldRows()).isEqualTo(before);
    }
    @Test void populatedEarlierReceiptAndGenuineNextAdmissionCarryRetentionOnceWithSeparateMonthlyCu() {
        var db=dbNow();clock.set(db);
        var grand=configuration("grand-month",db.minusSeconds(7200),db.minusSeconds(4000),100,3_000_000_000L);
        var old=configuration("old-month",db.minusSeconds(3600),db.minusSeconds(600),150,3_000_000_000_000L/1000);
        var grandDigest=new CanonicalFingerprint().field("version","continuous-configuration-v1").field("configuration",deployments.encode(grand)).finish();
        var oldDigest=new CanonicalFingerprint().field("version","continuous-configuration-v1").field("configuration",deployments.encode(old)).finish();
        var historical=new PumpSwapCapturePeriodContinuation("continuous-period-continuation-v1",grand.deploymentKey(),grandDigest,grand.template().authority().periodEnd(),old,"earlier-owner",new PumpSwapCapturePeriodContinuation.Baseline("OWNER_REPORTED",old.template().startsAt(),150L,20L));
        var historicalDigest=new CanonicalFingerprint().field("version",historical.version()).field("request",deployments.encode(historical)).finish();
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->{
            capture.providerAdmissionLock("alchemy");capture.period(grand.template(),CaptureBudget.ceiling(grand.template()),120);deployments.insert(grand,grandDigest,"historic-grand",db.minusSeconds(60));
            jdbc.sql("update marketdata.capture_deployments set state='STOPPED',reason='DEPLOYMENT_GLOBAL_END' where deployment_key=:key").param("key",grand.deploymentKey()).update();
            capture.bootstrapContinuationPeriod(old.template(),CaptureBudget.ceiling(old.template()),170);deployments.insert(old,oldDigest,"historic-old",db.minusSeconds(60));
        });
        jdbc.sql("update marketdata.capture_deployments set state='STOPPED',reason='DEPLOYMENT_GLOBAL_END'").update();
        for(var c:List.of(grand,old))capture.insertSession(c.template(),"sha256:"+"c".repeat(64),"historic-session",db.minusSeconds(60),db.minusSeconds(60),100);
        jdbc.sql("update marketdata.capture_sessions set state='STOPPED',reason='CAPTURE_TIME_LIMIT',retained_bytes=1500000000 where account_period='period-old-month'").update();
        jdbc.sql("update marketdata.capture_sessions set state='STOPPED',reason='CAPTURE_TIME_LIMIT' where account_period='period-grand-month'").update();
        jdbc.sql("""
            insert into marketdata.capture_period_continuations values(:next,:parent,'alchemy',:period,'continuous-period-continuation-v1',:start,:end,:parentDigest,:request,:digest,:at,'OWNER_REPORTED',:at,150,20,3000000000,70000000000)
            """).param("next",old.deploymentKey()).param("parent",grand.deploymentKey()).param("period",old.template().authority().accountPeriod())
            .param("start",java.sql.Timestamp.from(historical.periodStart())).param("end",java.sql.Timestamp.from(old.template().authority().periodEnd()))
            .param("parentDigest",grandDigest).param("request",deployments.encode(historical)).param("digest",historicalDigest).param("at",java.sql.Timestamp.from(old.template().startsAt())).update();
        var oldHistory=jdbc.sql("select row_to_json(t)::text from marketdata.capture_period_continuations t").query(String.class).list();var before=oldRows();
        var sessions=jdbc.sql("select row_to_json(t)::text from marketdata.capture_sessions t order by session_id").query(String.class).list();
        var next=configuration("third-month",db,db.plusSeconds(86400),200,3_000_000_000L);
        var request=new PumpSwapCapturePeriodContinuation(historical.version(),old.deploymentKey(),oldDigest,old.template().authority().periodEnd(),next,"current-owner",new PumpSwapCapturePeriodContinuation.Baseline("OWNER_REPORTED",db,200L,20L));
        assertThat(enrollment.enroll(request).retainedOpeningBytes()).isEqualTo(3_000_000_000L);
        assertThat(receipts()).isEqualTo(2);assertThat(oldRows()).isEqualTo(before);assertThat(jdbc.sql("select row_to_json(t)::text from marketdata.capture_sessions t order by session_id").query(String.class).list()).isEqualTo(sessions);
        assertThat(jdbc.sql("select row_to_json(t)::text from marketdata.capture_period_continuations t where successor_deployment_key='old-month'").query(String.class).list()).isEqualTo(oldHistory);
        assertThat(capture.reserved("period-grand-month")).isEqualTo(120);assertThat(capture.reserved("period-old-month")).isEqualTo(170);assertThat(capture.reserved("period-third-month")).isEqualTo(220);
    }
    @Test void populatedV16MigrationAndSameStoreCapabilityKeepOldRowsExactly() {
        jdbc.sql("create database continuation_populated_v16").update();
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(postgres.getJdbcUrl().replace("/"+postgres.getDatabaseName(),"/continuation_populated_v16"),postgres.getUsername(),postgres.getPassword());
        org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("16").load().migrate();
        var sql=org.springframework.jdbc.core.simple.JdbcClient.create(ds);var json=tools.jackson.databind.json.JsonMapper.builder().build();var c=new JdbcCaptureStore(sql,json);var d=new JdbcContinuousCaptureStore(sql,json,c);
        assertThat(d.continuationSchemaPresent()).isFalse();var db=dbNow();var old=configuration("migration-old",db.minusSeconds(3600),db.minusSeconds(600),100,1000);
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        tx.executeWithoutResult(s->{c.period(old.template(),CaptureBudget.ceiling(old.template()),120);d.insert(old,"sha256:"+"d".repeat(64),"historical",db.minusSeconds(60));c.insertSession(old.template(),"sha256:"+"e".repeat(64),"old",db.minusSeconds(60),db.minusSeconds(60),100);});
        var names=sql.sql("select tablename from pg_tables where schemaname='marketdata' order by tablename").query(String.class).list();
        var before=new TreeMap<String,List<String>>();for(var name:names)before.put(name,sql.sql("select row_to_json(t)::text from marketdata."+name+" t order by row_to_json(t)::text collate \"C\"").query(String.class).list());
        assertThat(org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate().migrationsExecuted).isEqualTo(1);
        for(var name:names)assertThat(sql.sql("select row_to_json(t)::text from marketdata."+name+" t order by row_to_json(t)::text collate \"C\"").query(String.class).list()).isEqualTo(before.get(name));
        assertThat(d.continuationSchemaPresent()).isTrue();
        assertThat(sql.sql("select count(*) from pg_constraint where conrelid='marketdata.capture_period_continuations'::regclass and contype='f'").query(Integer.class).single()).isEqualTo(3);
    }
    @Test void oldTerminalClaimFenceAndAdmissionNeverReopenAfterSuccessorEnrollment() {
        var r=seed();var old=deployments.lockProvider("alchemy").configuration();var db=dbNow();
        capture.insertSession(old.template(),"sha256:"+"f".repeat(64),"old-owner",db.minusSeconds(60),db.minusSeconds(60),100);
        jdbc.sql("update marketdata.capture_sessions set state='STOPPED',reason='CAPTURE_TIME_LIMIT'").update();
        var c=r.successor();var o=c.opening();var retained=new PumpSwapContinuousCapture.Resources(0,0,0,old.template().limits().retainedBytes(),0,o.reservedHundredths(),0);
        var next=new PumpSwapContinuousCapture.Configuration(c.deploymentKey(),c.version(),c.template(),c.allowances(),retained,c.globalEnd(),c.maxSegments(),c.segmentSeconds(),c.maxPages(),c.decodeSeconds(),c.receiptBytes(),c.pollMillis());
        enrollment.enroll(withSuccessor(r,next));
        var before=oldRows();var sessions=jdbc.sql("select row_to_json(s)::text from marketdata.capture_sessions s").query(String.class).list();
        assertThat(capture.claim(old.template().sessionId(),"old-owner",dbNow(),dbNow().plusSeconds(60))).isFalse();
        assertThatThrownBy(()->capture.fence(old.template().sessionId(),"old-owner",dbNow(),dbNow().plusSeconds(60))).hasMessage("CAPTURE_LEASE_LOST");
        assertThat(ledger.admit(old,"old-owner").terminal()).isEqualTo("DEPLOYMENT_GLOBAL_END");
        assertThat(oldRows()).isEqualTo(before);assertThat(jdbc.sql("select row_to_json(s)::text from marketdata.capture_sessions s").query(String.class).list()).isEqualTo(sessions);
        assertThat(capture.reserved("period-old-month")).isEqualTo(120);assertThat(capture.reserved("period-next-month")).isEqualTo(220);
    }
    @Test void ordinaryAdmissionRacingContinuationCannotCreateAnUnreceiptedAllocation() throws Exception {
        var r=seed();var go=new java.util.concurrent.CountDownLatch(1);
        var enrolled=java.util.concurrent.CompletableFuture.supplyAsync(()->{await(go);return enrollment.enroll(r);});
        var admitted=java.util.concurrent.CompletableFuture.supplyAsync(()->{await(go);try{return ledger.admit(r.successor(),"racing-worker").terminal();}catch(IllegalStateException e){return e.getMessage();}});
        go.countDown();assertThat(enrolled.get(10,java.util.concurrent.TimeUnit.SECONDS).successorDeploymentKey()).isEqualTo("next-month");
        assertThat(admitted.get(10,java.util.concurrent.TimeUnit.SECONDS)).isIn("","CONTINUOUS_CONFIGURATION_CONFLICT");
        assertThat(receipts()).isEqualTo(1);assertThat(jdbc.sql("select count(*) from marketdata.capture_deployments where state='RUNNING'").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from marketdata.capture_periods").query(Long.class).single()).isEqualTo(2);assertThat(capture.reserved("period-old-month")).isEqualTo(120);
    }
    private static void await(java.util.concurrent.CountDownLatch latch){try{if(!latch.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("bounded race latch");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    @Test void recordedDigestCannotHideAContradictoryPredecessorConfiguration() {
        var r=seed();var forged="sha256:"+"0".repeat(64);
        jdbc.sql("update marketdata.capture_deployments set configuration_digest=:digest where deployment_key='old-month'").param("digest",forged).update();
        var before=oldRows();var request=new PumpSwapCapturePeriodContinuation(r.version(),r.predecessorDeploymentKey(),forged,r.periodStart(),r.successor(),r.ownerDecision(),r.baseline());
        assertThatThrownBy(()->enrollment.enroll(request)).hasMessage("CONTINUATION_PREDECESSOR_CONFLICT");
        assertThat(oldRows()).isEqualTo(before);assertThat(receipts()).isZero();assertThat(capture.reserved("period-old-month")).isEqualTo(120);
    }
    Map<String,List<String>> history() {
        var result=new TreeMap<String,List<String>>();
        for(var table:jdbc.sql("select tablename from pg_tables where schemaname='marketdata' order by tablename").query(String.class).list())
            result.put(table,jdbc.sql("select row_to_json(t)::text from marketdata."+table+" t order by row_to_json(t)::text collate \"C\"").query(String.class).list());
        return result;
    }
    @Test void consistentEarlyResourceStopBeforeActualPeriodEndFailsReadinessNotParentBinding() {
        var db=dbNow();clock.set(db);var old=configuration("early-resource",db.minusSeconds(3600),db.plusSeconds(600),100,1000);
        var digest=new CanonicalFingerprint().field("version","continuous-configuration-v1").field("configuration",deployments.encode(old)).finish();
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->{
            capture.providerAdmissionLock("alchemy");capture.period(old.template(),CaptureBudget.ceiling(old.template()),120);deployments.insert(old,digest,"expired-owner",db.minusSeconds(60));
        });
        jdbc.sql("update marketdata.capture_deployments set state='STOPPED',reason='DEPLOYMENT_RESOURCE_LIMIT' where deployment_key=:key").param("key",old.deploymentKey()).update();
        var start=old.template().authority().periodEnd();var next=configuration("too-early-month",start,start.plusSeconds(86400),200,1000);
        var r=new PumpSwapCapturePeriodContinuation("continuous-period-continuation-v1",old.deploymentKey(),digest,start,next,"owner",new PumpSwapCapturePeriodContinuation.Baseline("OWNER_REPORTED",start,200L,20L));
        assertThatCode(()->EnrollCapturePeriodContinuationUseCase.validate(r)).as("prospective request has valid static binding, not an invalid baseline window").doesNotThrowAnyException();
        assertThat(jdbc.sql("select period_end from marketdata.capture_periods where account_period=:p").param("p",old.template().authority().accountPeriod()).query((q,n)->q.getTimestamp("period_end").toInstant()).single()).isEqualTo(start);
        assertThat(start).isAfter(dbNow());var before=history();
        assertThatThrownBy(()->enrollment.enroll(r)).hasMessage("CONTINUATION_ACTIVE_OR_PERIOD_NOT_READY");assertThat(history()).isEqualTo(before);
    }
    @Test void genuinelyLinkedExpiredRunningSessionCannotBeIgnoredAsLegacyManual() {
        var r=seed();var old=deployments.lockProvider("alchemy").configuration();var db=dbNow();var c=old.template();
        capture.insertSession(c,"sha256:"+"9".repeat(64),"expired-holder",db.minusSeconds(60),db.minusSeconds(60),100);
        deployments.insertSegment(old,1,c,"sha256:"+"9".repeat(64),ContinuousCaptureLedgerUseCase.resources(c));
        assertThat(jdbc.sql("select count(*) from marketdata.capture_sessions s join marketdata.capture_segments g using(session_id) where s.state='RUNNING' and s.ends_at<clock_timestamp() and s.locked_until<clock_timestamp()").query(Long.class).single()).isEqualTo(1);
        var before=history();assertThatThrownBy(()->enrollment.enroll(r)).hasMessage("CONTINUATION_ACTIVE_WORKLOAD");assertThat(history()).isEqualTo(before);
    }
    PumpSwapContinuousCapture.Configuration withTemplate(PumpSwapContinuousCapture.Configuration c,PumpSwapCapture.Configuration t) {
        return new PumpSwapContinuousCapture.Configuration(c.deploymentKey(),c.version(),t,c.allowances(),c.opening(),c.globalEnd(),c.maxSegments(),c.segmentSeconds(),c.maxPages(),c.decodeSeconds(),c.receiptBytes(),c.pollMillis());
    }
    @Test void samePeriodProofScopeAndRateChangesRejectOtherwiseValidSuccessorsWithoutEffects() {
        var r=seed();var c=r.successor();var t=c.template();var a=t.authority();
        var same=new PumpSwapCapture.Authority("period-old-month",a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),a.ceilingHundredths(),a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),a.ownerAuthorization(),true,true);
        var rate=new PumpSwapCapture.Authority(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),a.ceilingHundredths(),a.ceilingUsd(),new BigDecimal("0.526"),a.subscriptionCuPerByte(),a.ownerAuthorization(),true,true);
        var pools=new ArrayList<>(t.pools());var p=pools.getFirst();var proof=p.proof();
        pools.set(0,new PumpSwapCapture.Pool(p.address(),p.program(),p.baseVault(),p.quoteVault(),p.baseMint(),p.quoteMint(),p.baseTokenProgram(),p.quoteTokenProgram(),
            new PumpSwapCapture.LinkageProof(proof.contextSlot(),proof.commitment(),"different-valid-proof-version",proof.provenance(),proof.payload(),proof.digest())));
        var changedPools=new ArrayList<>(t.pools());var changedAddress=PumpSwapPoolCaptureTest.address(15);var payload=proof.payload().replace(p.address(),changedAddress);
        changedPools.set(0,new PumpSwapCapture.Pool(changedAddress,p.program(),p.baseVault(),p.quoteVault(),p.baseMint(),p.quoteMint(),p.baseTokenProgram(),p.quoteTokenProgram(),
            new PumpSwapCapture.LinkageProof(proof.contextSlot(),proof.commitment(),proof.sourceVersion(),proof.provenance(),payload,PayloadFingerprint.sha256(payload))));
        var alternatives=List.of(new PumpSwapCapture.Configuration(t.sessionId(),t.version(),t.provider(),t.startsAt(),t.pools(),t.limits(),same),
            new PumpSwapCapture.Configuration(t.sessionId(),t.version(),t.provider(),t.startsAt(),t.pools(),t.limits(),rate),
            new PumpSwapCapture.Configuration(t.sessionId(),t.version(),t.provider(),t.startsAt(),pools,t.limits(),a),
            new PumpSwapCapture.Configuration(t.sessionId(),t.version(),t.provider(),t.startsAt(),changedPools,t.limits(),a));
        var before=history();
        for(var candidate:alternatives) {
            assertThat(CaptureBudget.launchProblem(candidate)).isEmpty();var request=withSuccessor(r,withTemplate(c,candidate));
            assertThatCode(()->EnrollCapturePeriodContinuationUseCase.validate(request)).doesNotThrowAnyException();
            assertThatThrownBy(()->enrollment.enroll(request)).hasMessage("CONTINUATION_PREDECESSOR_CONFLICT");assertThat(history()).isEqualTo(before);
        }
    }
    @Test void twoDifferentValidChildrenRaceToOneLinearReceiptWithoutChangingOldHolds() throws Exception {
        var r=seed();var other=configuration("different-valid-child",r.successor().template().startsAt(),r.successor().template().authority().periodEnd(),200,1000);
        var competing=withSuccessor(r,other);assertThatCode(()->EnrollCapturePeriodContinuationUseCase.validate(competing)).doesNotThrowAnyException();
        var before=oldRows();var oldPeriod=jdbc.sql("select row_to_json(p)::text from marketdata.capture_periods p where account_period='period-old-month'").query(String.class).single();
        var go=new java.util.concurrent.CountDownLatch(1);
        var first=java.util.concurrent.CompletableFuture.supplyAsync(()->raceEnrollment(r,go));var second=java.util.concurrent.CompletableFuture.supplyAsync(()->raceEnrollment(competing,go));go.countDown();
        var outcomes=List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS));
        assertThat(outcomes.stream().filter(s->s.startsWith("WIN:"))).hasSize(1);assertThat(outcomes.stream().filter(s->s.equals("CONTINUATION_ACTIVE_OR_PERIOD_NOT_READY"))).hasSize(1);
        assertThat(receipts()).isEqualTo(1);assertThat(jdbc.sql("select count(*) from marketdata.capture_deployments").query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("select count(*) from marketdata.capture_periods").query(Long.class).single()).isEqualTo(2);
        assertThat(oldRows()).isEqualTo(before);assertThat(jdbc.sql("select row_to_json(p)::text from marketdata.capture_periods p where account_period='period-old-month'").query(String.class).single()).isEqualTo(oldPeriod);
    }
    String raceEnrollment(PumpSwapCapturePeriodContinuation r,java.util.concurrent.CountDownLatch go) {
        await(go);try{return "WIN:"+enrollment.enroll(r).successorDeploymentKey();}catch(IllegalStateException e){return e.getMessage();}
    }
}
