package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.*;
import io.cryptoresearch.marketdata.api.PumpSwapCaptureAuthorityAmendment;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(PumpSwapContinuousCaptureIT.Clocks.class)
class CaptureAuthorityAmendmentIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired AmendCapturePeriodAuthorityUseCase amendments;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired JdbcCaptureStore store;
    @Autowired MutableUtcClock clock;
    @Autowired ContinuousCaptureLedgerUseCase continuous;
    @Autowired CaptureLedgerUseCase legacy;
    @Autowired javax.sql.DataSource dataSource;
    @org.junit.jupiter.api.BeforeEach void isolate() {
        jdbc.sql("truncate marketdata.capture_periods cascade").update();clock.set(PumpSwapPoolCaptureTest.START);
    }
    private Configuration seed(String period) {
        clock.set(PumpSwapPoolCaptureTest.START);var c=CaptureAuthorityAmendmentTest.old(period);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->store.period(c,CaptureBudget.ceiling(c),120));
        jdbc.sql("update marketdata.capture_periods set known_overage_hundredths=13 where account_period=:period").param("period",period).update();return c;
    }
    private Configuration amended(Configuration c) {return new Configuration(UUID.randomUUID(),c.version(),c.provider(),c.startsAt(),c.pools(),c.limits(),CaptureAuthorityAmendmentTest.successor(c.authority()));}
    @Test void exactSamePeriodAmendmentEnablesNewReservationWithoutRefundOrRepricing() {
        var period="actual-amendment";var old=seed(period);var before=jdbc.sql("select authority_json from marketdata.capture_periods where account_period=:p").param("p",period).query(String.class).single();
        assertThatCode(()->amendments.amend(CaptureAuthorityAmendmentTest.amendment(period))).as("authorized amendment commits before future reservation").doesNotThrowAnyException();
        assertThat(jdbc.sql("select authority_json from marketdata.capture_periods where account_period=:p").param("p",period).query(String.class).single()).isEqualTo(before);
        assertThat(store.reserved(period)).isEqualTo(133);
        Boolean newReserved=new org.springframework.transaction.support.TransactionTemplate(transactions).execute(s->store.reservePeriod(amended(old),10));
        Boolean oldReserved=new org.springframework.transaction.support.TransactionTemplate(transactions).execute(s->store.reservePeriod(old,10));
        assertThat(newReserved).isTrue();assertThat(oldReserved).isFalse();
        assertThat(store.reserved(period)).isEqualTo(143);
    }
    @Test void equalRepeatKeepsOneImmutableReceiptAndConflictCannotChangeCounters() {
        var period="repeat-amendment";seed(period);var request=CaptureAuthorityAmendmentTest.amendment(period);
        assertThatCode(()->amendments.amend(request)).doesNotThrowAnyException();var first=amendments.amend(request);
        assertThat(amendments.amend(request)).isEqualTo(first);
        assertThat(jdbc.sql("select count(*) from marketdata.capture_authority_amendments where account_period=:p").param("p",period).query(Long.class).single()).isEqualTo(1);
        assertThatThrownBy(()->amendments.amend(new PumpSwapCaptureAuthorityAmendment(request.version(),request.provider(),request.predecessor(),request.successor(),"different-decision"))).hasMessage("CAPTURE_AUTHORITY_AMENDMENT_CONFLICT");
        assertThat(store.reserved(period)).isEqualTo(133);
    }
    @Test void durableTerminalProjectionDoesNotReportCachedAdmissionAsSuccessfulEmptyStop() {
        var d=PumpSwapContinuousCaptureIT.deployment("amendment-observation",1,1);
        clock.set(PumpSwapPoolCaptureTest.START);var a=continuous.admit(d,"observation-owner");
        continuous.terminal(a,"DEPLOYMENT_RESOURCE_LIMIT");
        assertThat(a.terminal()).isEmpty();
        assertThatCode(()->continuous.terminalObservation(a)).as("durable stop remains visible after cached admission").doesNotThrowAnyException();
        var observed=continuous.terminalObservation(a);
        assertThat(observed.state()).isEqualTo("STOPPED");assertThat(observed.reason()).isEqualTo("DEPLOYMENT_RESOURCE_LIMIT");
        assertThat(observed.reserved()).isEqualTo(ContinuousCaptureLedgerUseCase.resources(a.segment().configuration()));
        assertThat(continuous.admit(d,"cannot-reopen").terminal()).isEqualTo(observed.reason());
    }
    @Test void newAmendmentRejectsActiveSessionExpiredPeriodAndWrongPredecessorAtomically() {
        var old=seed("active-new");
        var dbNow=dbNow();store.insertSession(old,"sha256:"+"a".repeat(64),"synthetic-owner",dbNow.plusSeconds(60),dbNow.plusSeconds(600),100);
        assertThatThrownBy(()->amendments.amend(CaptureAuthorityAmendmentTest.amendment("active-new"))).hasMessage("CAPTURE_AUTHORITY_ACTIVE_WORKLOAD");
        assertThat(receipts()).isZero();assertThat(store.reserved("active-new")).isEqualTo(133);
        jdbc.sql("update marketdata.capture_sessions set state='STOPPED',reason='OWNER_STOP'").update();
        clock.set(old.authority().periodEnd());
        assertThatThrownBy(()->amendments.amend(CaptureAuthorityAmendmentTest.amendment("active-new"))).hasMessage("CAPTURE_AUTHORITY_PERIOD_EXPIRED");
        clock.set(PumpSwapPoolCaptureTest.START);
        jdbc.sql("update marketdata.capture_periods set authority_json='immutable-contradiction' where account_period='active-new'").update();
        assertThatThrownBy(()->amendments.amend(CaptureAuthorityAmendmentTest.amendment("active-new"))).hasMessage("CAPTURE_AUTHORITY_PREDECESSOR_CONFLICT");
        assertThat(receipts()).isZero();assertThat(store.reserved("active-new")).isEqualTo(133);
    }
    @Test void equalReceiptOnRebootDoesNotGrantNewAuthorityWhenActiveOrExpired() {
        var old=seed("repeat-active");var request=CaptureAuthorityAmendmentTest.amendment("repeat-active");var receipt=amendments.amend(request);
        store.insertSession(amended(old),"sha256:"+"b".repeat(64),"current-owner",clock.instant().plusSeconds(60),clock.instant().plusSeconds(600),100);
        assertThat(amendments.amend(request)).isEqualTo(receipt);
        clock.set(old.authority().periodEnd());assertThat(amendments.amend(request)).isEqualTo(receipt);
        assertThat(receipts()).isEqualTo(1);assertThat(store.reserved("repeat-active")).isEqualTo(133);
    }
    @Test void activeDeploymentAndRenamedPeriodCannotBypassSameAllocation() {
        var old=seed("deployment-amendment");var original=PumpSwapContinuousCaptureIT.deployment("active-amendment",1,1);
        var opening=new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Resources(0,0,0,0,0,133,0);
        var d=new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration(original.deploymentKey(),original.version(),old,original.allowances().plus(opening),
            opening,original.globalEnd(),original.maxSegments(),original.segmentSeconds(),original.maxPages(),original.decodeSeconds(),original.receiptBytes(),original.pollMillis());
        continuous.admit(d,"current-holder");
        assertThatThrownBy(()->amendments.amend(CaptureAuthorityAmendmentTest.amendment(old.authority().accountPeriod()))).hasMessage("CAPTURE_AUTHORITY_ACTIVE_WORKLOAD");
        assertThat(receipts()).isZero();
    }
    @Test void renamedPeriodAndPreEffectiveReservationFailWithoutCreatingNewAllocation() {
        var old=seed("alias-protection");clock.set(clock.instant().plusSeconds(1));amendments.amend(CaptureAuthorityAmendmentTest.amendment("alias-protection"));
        Boolean before=new org.springframework.transaction.support.TransactionTemplate(transactions).execute(s->store.reservePeriod(amended(old),10));
        assertThat(before).as("new price does not apply before actual amendment instant").isFalse();
        var alias=CaptureAuthorityAmendmentTest.old("renamed-period");
        assertThatThrownBy(()->legacy.open(alias,"alias-owner")).hasMessage("CAPTURE_AUTHORITY_PERIOD_ALIAS_REJECTED");
        assertThat(jdbc.sql("select count(*) from marketdata.capture_periods").query(Long.class).single()).isEqualTo(1);
        assertThat(store.reserved("alias-protection")).isEqualTo(133);
    }
    @Test void twoConcurrentEqualAmendmentsCommitOneReceiptAndSameTime() throws Exception {
        seed("concurrent-equal");var a=CaptureAuthorityAmendmentTest.amendment("concurrent-equal");var go=new java.util.concurrent.CountDownLatch(1);
        var first=java.util.concurrent.CompletableFuture.supplyAsync(()->{await(go);return amendments.amend(a);});
        var second=java.util.concurrent.CompletableFuture.supplyAsync(()->{await(go);return amendments.amend(a);});go.countDown();
        assertThat(first.get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(second.get(15,java.util.concurrent.TimeUnit.SECONDS));
        assertThat(receipts()).isEqualTo(1);assertThat(store.reserved("concurrent-equal")).isEqualTo(133);
    }
    @Test void concurrentOldReservationAndAmendmentSerializeWithoutLostHolds() throws Exception {
        var old=seed("concurrent-reserve");var go=new java.util.concurrent.CountDownLatch(1);
        var amended=java.util.concurrent.CompletableFuture.supplyAsync(()->{await(go);return amendments.amend(CaptureAuthorityAmendmentTest.amendment("concurrent-reserve"));});
        var reserved=java.util.concurrent.CompletableFuture.supplyAsync(()->{await(go);return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(s->{store.providerAdmissionLock("alchemy");return store.reservePeriod(old,10);});});
        go.countDown();assertThat(amended.get(15,java.util.concurrent.TimeUnit.SECONDS)).isNotNull();var admitted=reserved.get(15,java.util.concurrent.TimeUnit.SECONDS);
        assertThat(store.reserved("concurrent-reserve")).isEqualTo(admitted?143:133);assertThat(receipts()).isEqualTo(1);
    }
    @Test void arithmeticOverflowCannotLeaveReceiptOrHigherCeiling() {
        seed("overflow");jdbc.sql("update marketdata.capture_periods set known_overage_hundredths=9223372036854775807 where account_period='overflow'").update();
        assertThatThrownBy(()->amendments.amend(CaptureAuthorityAmendmentTest.amendment("overflow"))).isInstanceOf(ArithmeticException.class);
        assertThat(receipts()).isZero();assertThat(jdbc.sql("select ceiling_hundredths from marketdata.capture_periods where account_period='overflow'").query(Long.class).single()).isEqualTo(2_857_142_800L);
    }
    @Test void populatedV15ForwardMigrationAndAmendmentPreserveAllHistoricalContent() throws Exception {
        jdbc.sql("create database amendment_populated_v15").update();
        var isolated=new org.springframework.jdbc.datasource.DriverManagerDataSource(postgres.getJdbcUrl().replace("/"+postgres.getDatabaseName(),"/amendment_populated_v15"),postgres.getUsername(),postgres.getPassword());
        org.flywaydb.core.Flyway.configure().dataSource(isolated).locations("classpath:db/migration").target("15").load().migrate();
        var sql=org.springframework.jdbc.core.simple.JdbcClient.create(isolated);var oldStore=new JdbcCaptureStore(sql,tools.jackson.databind.json.JsonMapper.builder().build());
        var old=CaptureAuthorityAmendmentTest.old("populated");var manager=new org.springframework.jdbc.datasource.DataSourceTransactionManager(isolated);var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
        tx.executeWithoutResult(s->{oldStore.period(old,CaptureBudget.ceiling(old),120);oldStore.insertSession(old,"sha256:"+"c".repeat(64),"historical-holder",clock.instant().plusSeconds(60),clock.instant().plusSeconds(600),100);});
        sql.sql("update marketdata.capture_sessions set state='STOPPED',reason='OLD_FAILURE'").update();sql.sql("update marketdata.capture_periods set known_overage_hundredths=13").update();
        var historical=sql.sql("select tablename from pg_tables where schemaname='marketdata' order by tablename collate \"C\"").query(String.class).list();
        var before=rows(sql,historical);
        assertThat(org.flywaydb.core.Flyway.configure().dataSource(isolated).locations("classpath:db/migration").load().migrate().migrationsExecuted).isEqualTo(2);
        assertThat(rows(sql,historical)).isEqualTo(before);
        var request=CaptureAuthorityAmendmentTest.amendment("populated");
        tx.executeWithoutResult(s->new AmendCapturePeriodAuthorityUseCase(oldStore,clock).amend(request));
        var after=rows(sql,historical);after.remove("capture_periods");var expected=new TreeMap<>(before);expected.remove("capture_periods");assertThat(after).isEqualTo(expected);
        assertThat(sql.sql("select authority_json from marketdata.capture_periods").query(String.class).single()).isEqualTo(oldStore.encode(java.util.List.of(old.authority().accountPeriod(),old.authority().periodEnd(),old.authority().source(),0L,0,true,old.authority().ceilingHundredths(),"15","0.525","0.04")));
        assertThat(oldStore.reserved("populated")).isEqualTo(133);
        Boolean newAllowed=tx.execute(s->oldStore.reservePeriod(amended(old),10));assertThat(newAllowed).isTrue();
    }
    @Test void v13OnlyLegacyReservationStillWorksAndSameStoreDetectsForwardAmendmentSchema() {
        jdbc.sql("create database amendment_legacy_v13").update();
        var isolated=new org.springframework.jdbc.datasource.DriverManagerDataSource(postgres.getJdbcUrl().replace("/"+postgres.getDatabaseName(),"/amendment_legacy_v13"),postgres.getUsername(),postgres.getPassword());
        org.flywaydb.core.Flyway.configure().dataSource(isolated).locations("classpath:db/migration").target("13").load().migrate();
        var sql=org.springframework.jdbc.core.simple.JdbcClient.create(isolated);var originalStore=new JdbcCaptureStore(sql,tools.jackson.databind.json.JsonMapper.builder().build());
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(isolated));var old=CaptureAuthorityAmendmentTest.old("legacy-v13");
        assertThatCode(()->tx.execute(s->{originalStore.period(old,CaptureBudget.ceiling(old),120);return originalStore.reservePeriod(old,10);})).as("V13-only legacy reservation must execute unchanged without amendment SQL dependency").doesNotThrowAnyException();
        assertThat(originalStore.reserved("legacy-v13")).isEqualTo(130);
        org.flywaydb.core.Flyway.configure().dataSource(isolated).locations("classpath:db/migration").load().migrate();
        tx.executeWithoutResult(s->new AmendCapturePeriodAuthorityUseCase(originalStore,clock).amend(CaptureAuthorityAmendmentTest.amendment("legacy-v13")));
        Boolean historicalRejected=tx.execute(s->originalStore.reservePeriod(old,10));assertThat(historicalRejected).isFalse();
        Boolean futureAllowed=tx.execute(s->originalStore.reservePeriod(amended(old),10));assertThat(futureAllowed).isTrue();assertThat(originalStore.reserved("legacy-v13")).isEqualTo(140);
    }
    @Test void expiredUnleasedUnlinkedLegacyRowsDoNotBlockOrLoseHistoricalEvidence() {
        var old=seed("expired-legacy");var db=dbNow();
        store.insertSession(old,"sha256:"+"d".repeat(64),"old-holder",db.minusSeconds(600),db.minusSeconds(300),100);
        var before=rows(jdbc,List.of("capture_sessions","capture_dispositions","capture_envelopes","capture_gaps"));
        assertThatCode(()->amendments.amend(CaptureAuthorityAmendmentTest.amendment("expired-legacy")))
            .as("expired unleased unlinked manual session is inactive without rewriting history").doesNotThrowAnyException();
        assertThat(rows(jdbc,List.of("capture_sessions","capture_dispositions","capture_envelopes","capture_gaps"))).isEqualTo(before);
        assertThat(store.reserved("expired-legacy")).isEqualTo(133);
        assertThat(store.claim(old.sessionId(),"replacement",dbNow(),dbNow().plusSeconds(60))).isFalse();
        assertThatThrownBy(()->store.fence(old.sessionId(),"old-holder",dbNow(),dbNow().plusSeconds(60))).hasMessage("CAPTURE_LEASE_LOST");
        assertThat(rows(jdbc,List.of("capture_sessions","capture_dispositions","capture_envelopes","capture_gaps"))).isEqualTo(before);
        Boolean future=new org.springframework.transaction.support.TransactionTemplate(transactions).execute(s->store.reservePeriod(amended(old),10));
        assertThat(future).isTrue();assertThat(store.reserved("expired-legacy")).isEqualTo(143);
    }
    @Test void databaseTimeNotApplicationTimeClassifiesFutureEndAndLiveLease() {
        for(boolean futureEnd:List.of(true,false)) {
            jdbc.sql("truncate marketdata.capture_periods cascade").update();var period="db-clock-"+futureEnd;var db=dbNow();
            var template=CaptureAuthorityAmendmentTest.old(period);var a=template.authority();
            var authority=new Authority(a.accountPeriod(),db.plusSeconds(172800),a.source(),a.openingHundredths(),a.otherActivityHundredths(),a.ceilingHundredths(),a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),a.ownerAuthorization(),a.retentionAllowed(),a.exclusiveAllocation());
            var old=new Configuration(template.sessionId(),template.version(),template.provider(),template.startsAt(),template.pools(),template.limits(),authority);
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->store.period(old,CaptureBudget.ceiling(old),120));
            jdbc.sql("update marketdata.capture_periods set known_overage_hundredths=13 where account_period=:p").param("p",period).update();
            var request=new PumpSwapCaptureAuthorityAmendment("capture-authority-amendment-v1","alchemy",authority,CaptureAuthorityAmendmentTest.successor(authority),"owner-db-clock");
            store.insertSession(old,"sha256:"+"e".repeat(64),"holder",futureEnd?db.minusSeconds(60):db.plusSeconds(600),futureEnd?db.plusSeconds(600):db.minusSeconds(60),100);
            clock.set(db.plusSeconds(3600));
            assertThat(request.predecessor().periodEnd()).as("finite DB-relative period stays open at the deliberately offset application clock").isAfter(clock.instant());
            var before=rows(jdbc,List.of("capture_sessions","capture_periods"));
            assertThatThrownBy(()->amendments.amend(request)).hasMessage("CAPTURE_AUTHORITY_ACTIVE_WORKLOAD");
            assertThat(rows(jdbc,List.of("capture_sessions","capture_periods"))).isEqualTo(before);assertThat(receipts()).isZero();
        }
    }
    @Test void expiredLinkedContinuousSessionStillBlocksAfterDeploymentStopped() {
        var old=seed("linked-expired");var original=PumpSwapContinuousCaptureIT.deployment("linked-old",1,1);
        var opening=new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Resources(0,0,0,0,0,133,0);
        var d=new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration(original.deploymentKey(),original.version(),old,original.allowances().plus(opening),opening,original.globalEnd(),original.maxSegments(),original.segmentSeconds(),original.maxPages(),original.decodeSeconds(),original.receiptBytes(),original.pollMillis());
        var admitted=continuous.admit(d,"linked-holder");continuous.terminal(admitted,"OWNER_STOP");
        var db=dbNow();jdbc.sql("update marketdata.capture_sessions set state='RUNNING',ends_at=:end,locked_until=:lease")
            .param("end",java.sql.Timestamp.from(db.minusSeconds(60))).param("lease",java.sql.Timestamp.from(db.minusSeconds(60))).update();
        var before=rows(jdbc,List.of("capture_sessions","capture_segments","capture_deployments","capture_periods"));
        assertThatThrownBy(()->amendments.amend(CaptureAuthorityAmendmentTest.amendment("linked-expired"))).hasMessage("CAPTURE_AUTHORITY_ACTIVE_WORKLOAD");
        assertThat(rows(jdbc,List.of("capture_sessions","capture_segments","capture_deployments","capture_periods"))).isEqualTo(before);assertThat(receipts()).isZero();
    }
    @Test void renewalCommittedWhileAmendmentWaitsForSessionLockIsObserved() throws Exception {
        var old=seed("renewal-race");var db=dbNow();
        store.insertSession(old,"sha256:"+"f".repeat(64),"renewing-holder",db.plusSeconds(60),db.minusSeconds(60),100);
        var locked=new java.util.concurrent.CountDownLatch(1);var renew=new java.util.concurrent.CountDownLatch(1);
        var writer=java.util.concurrent.CompletableFuture.runAsync(()->new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->{
            jdbc.sql("select session_id from marketdata.capture_sessions where session_id=:id for update").param("id",old.sessionId()).query(UUID.class).single();locked.countDown();await(renew);
            var actual=dbNow();store.fence(old.sessionId(),"renewing-holder",actual,actual.plusSeconds(120));
        }));
        await(locked);var attempt=java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try{amendments.amend(CaptureAuthorityAmendmentTest.amendment("renewal-race"));return "COMMITTED";}catch(IllegalStateException e){return e.getMessage();}
        });
        try {
            var deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
            while(System.nanoTime()<deadline&&!waiting) {
                waiting=jdbc.sql("select exists(select 1 from pg_stat_activity where wait_event_type='Lock' and query like '%order by session_id for update%')").query(Boolean.class).single();
                if(!waiting)Thread.sleep(10);
            }
            assertThat(waiting).as("amendment actually waits for the session row lock").isTrue();
        } finally {renew.countDown();}
        writer.get(10,java.util.concurrent.TimeUnit.SECONDS);
        var afterRenewal=rows(jdbc,List.of("capture_sessions","capture_periods"));
        assertThat(attempt.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("CAPTURE_AUTHORITY_ACTIVE_WORKLOAD");
        assertThat(rows(jdbc,List.of("capture_sessions","capture_periods"))).isEqualTo(afterRenewal);assertThat(receipts()).isZero();assertThat(store.reserved("renewal-race")).isEqualTo(133);
    }
    private java.time.Instant dbNow(){return jdbc.sql("select clock_timestamp() as now").query((r,n)->r.getTimestamp("now").toInstant()).single();}
    private long receipts(){return jdbc.sql("select count(*) from marketdata.capture_authority_amendments").query(Long.class).single();}
    private static void await(java.util.concurrent.CountDownLatch go){try{if(!go.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("bounded start");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    private static Map<String,List<String>> rows(org.springframework.jdbc.core.simple.JdbcClient sql,List<String> names){var result=new TreeMap<String,List<String>>();for(var name:names)result.put(name,sql.sql("select row_to_json(t)::text from marketdata."+name+" t order by row_to_json(t)::text collate \"C\"").query(String.class).list());return result;}
}
