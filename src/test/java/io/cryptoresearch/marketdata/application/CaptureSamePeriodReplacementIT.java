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
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.*;
import io.cryptoresearch.marketdata.infrastructure.persistence.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(PumpSwapContinuousCaptureIT.Clocks.class)
class CaptureSamePeriodReplacementIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired EnrollCaptureSamePeriodReplacementUseCase enrollment;
    @Autowired EnrollCapturePeriodContinuationUseCase monthly;
    @Autowired AmendCapturePeriodAuthorityUseCase amendments;
    @Autowired ContinuousCaptureLedgerUseCase ledger;
    @Autowired CaptureLedgerUseCase legacy;
    @Autowired CaptureEvidenceUseCase evidence;
    @Autowired JdbcCaptureStore capture;
    @Autowired JdbcContinuousCaptureStore deployments;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired javax.sql.DataSource datasource;
    @Autowired MutableUtcClock clock;
    @BeforeEach void clean(){jdbc.sql("truncate marketdata.capture_periods,marketdata.raw_transactions cascade").update();}
    Instant dbNow(){return jdbc.sql("select clock_timestamp() as t").query((r,n)->r.getTimestamp("t").toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)).single();}
    static Resources zero(){return new Resources(0,0,0,0,0,0,0);}
    static Configuration child(Configuration root,String key,Resources carry,int consumed,Instant start) {
        var t=root.template();var template=new PumpSwapCapture.Configuration(UUID.randomUUID(),"capture-durable-inbox-v7",t.provider(),start,t.pools(),t.limits(),t.authority());
        return new Configuration(key,root.version(),template,root.allowances(),carry,root.globalEnd(),root.maxSegments()-consumed,root.segmentSeconds(),root.maxPages(),root.decodeSeconds(),root.receiptBytes(),root.pollMillis());
    }
    String digest(Configuration c){return new CanonicalFingerprint().field("version","continuous-configuration-v1").field("configuration",deployments.encode(c)).finish();}
    PumpSwapCaptureSamePeriodReplacement seed(boolean pending940) {
        var db=dbNow();clock.set(db);var base=PumpSwapContinuousCaptureIT.deployment("replacement-root",4,2);var t=base.template();var l=t.limits();
        var oldAuthority=new PumpSwapCapture.Authority("replacement-period",db.plusSeconds(7200),"OWNER_REPORTED",100L,20,2_860_000_000L,new BigDecimal("15"),new BigDecimal("0.525"),new BigDecimal("0.04"),"original-owner",true,true);
        var authority=new PumpSwapCapture.Authority(oldAuthority.accountPeriod(),oldAuthority.periodEnd(),oldAuthority.source(),oldAuthority.openingHundredths(),oldAuthority.otherActivityHundredths(),9_523_809_500L,new BigDecimal("50"),oldAuthority.usdPerMillionCu(),new BigDecimal("0.0002"),"amended-owner",true,true);
        var limits=new PumpSwapCapture.Limits(180,l.requests(),l.receivedBytes(),l.retainedBytes(),l.subscriptionBytes(),l.rpcHundredths(),l.messageBytes(),l.responseBytes(),l.queueItems(),l.queueBytes(),l.timeoutSeconds(),l.retries(),l.reconnects(),l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds());
        var oldTemplate=new PumpSwapCapture.Configuration(t.sessionId(),"capture-rpc-reuse-v6","alchemy",db,t.pools(),limits,oldAuthority);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->capture.period(oldTemplate,CaptureBudget.ceiling(oldTemplate),120));
        jdbc.sql("update marketdata.capture_periods set known_overage_hundredths=13").update();
        amendments.amend(new PumpSwapCaptureAuthorityAmendment("capture-authority-amendment-v1","alchemy",oldAuthority,authority,"synthetic-authorized-amendment"));
        var template=new PumpSwapCapture.Configuration(t.sessionId(),oldTemplate.version(),"alchemy",db,t.pools(),limits,authority);
        var one=ContinuousCaptureLedgerUseCase.resources(template);var caps=zero();for(var i=0;i<4;i++)caps=caps.plus(one);
        caps=new Resources(caps.requests(),caps.receivedBytes(),caps.subscriptionBytes(),70_000_000_000L,caps.rpcHundredths(),caps.reservedHundredths()+133,caps.seconds());
        var root=new Configuration("replacement-root",base.version(),template,caps,new Resources(0,0,0,0,0,133,0),db.plusSeconds(3600),4,180,2,60,2048,100);
        var admitted=ledger.admit(root,"old-worker");var session=admitted.segment().configuration();var holder=admitted.fence().sessionHolder();
        legacy.pendingAt(session.sessionId(),holder,session.pools().getFirst().address(),PumpSwapDurableInboxIT.signature(0),101,"{\"old\":0}",db);
        evidence.transaction(session.sessionId(),holder,session.pools().getFirst().address(),PumpSwapDurableInboxIT.signature(0),CaptureRpcReuseIT.full(PumpSwapDurableInboxIT.signature(0),101,"1",false),"{}",db,true);
        if(pending940)for(var i=1;i<=940;i++)legacy.pendingAt(session.sessionId(),holder,session.pools().getFirst().address(),PumpSwapDurableInboxIT.signature(i),101+i,"{\"oldPending\":"+i+"}",db);
        legacy.interrupt(session.sessionId(),holder,"QUEUE_OVERFLOW");
        ledger.terminal(admitted,"QUEUE_OVERFLOW");
        jdbc.sql("update marketdata.capture_sessions set state='STOPPED',reason='QUEUE_OVERFLOW',locked_until=clock_timestamp()-interval '1 second'").update();
        jdbc.sql("update marketdata.capture_deployments set locked_until=clock_timestamp()-interval '1 second'").update();
        var carry=deployments.lockDeployment("alchemy",root.deploymentKey()).reserved();
        return new PumpSwapCaptureSamePeriodReplacement("continuous-same-period-replacement-v1",root.deploymentKey(),digest(root),"QUEUE_OVERFLOW",root.deploymentKey(),digest(root),child(root,"replacement-child",carry,1,db),carry,1,"owner-decision-v1","synthetic-explicit-owner");
    }
    Map<String,List<String>> history() {
        var result=new TreeMap<String,List<String>>();
        for(var table:jdbc.sql("select tablename from pg_tables where schemaname='marketdata' order by tablename").query(String.class).list())
            result.put(table,jdbc.sql("select row_to_json(t)::text from marketdata."+table+" t order by row_to_json(t)::text collate \"C\"").query(String.class).list());
        return result;
    }
    static Resources vector(Resources r,int dimension,long value) {
        var a=new long[]{r.requests(),r.receivedBytes(),r.subscriptionBytes(),r.retainedBytes(),r.rpcHundredths(),r.reservedHundredths(),r.seconds()};a[dimension]=value;
        return new Resources(a[0],a[1],a[2],a[3],a[4],a[5],a[6]);
    }
    static long component(Resources r,int dimension) {return new long[]{r.requests(),r.receivedBytes(),r.subscriptionBytes(),r.retainedBytes(),r.rpcHundredths(),r.reservedHundredths(),r.seconds()}[dimension];}
    static PumpSwapCaptureSamePeriodReplacement next(PumpSwapCaptureSamePeriodReplacement r,Configuration c,Resources carry,int consumed) {
        return new PumpSwapCaptureSamePeriodReplacement(r.version(),r.predecessorDeploymentKey(),r.predecessorConfigurationDigest(),r.predecessorReason(),r.allocationRootKey(),r.allocationRootConfigurationDigest(),c,carry,consumed,r.decisionVersion(),r.ownerDecision());
    }
    PumpSwapCaptureSamePeriodReplacement rootCaps(PumpSwapCaptureSamePeriodReplacement r,Resources caps) {
        var old=deployments.lockDeployment("alchemy",r.allocationRootKey()).configuration();
        var root=new Configuration(old.deploymentKey(),old.version(),old.template(),caps,old.opening(),old.globalEnd(),old.maxSegments(),old.segmentSeconds(),old.maxPages(),old.decodeSeconds(),old.receiptBytes(),old.pollMillis());
        jdbc.sql("update marketdata.capture_deployments set configuration_json=:json,configuration_digest=:digest,allowances=cast(:caps as bigint[]) where deployment_key=:key")
            .param("json",deployments.encode(root)).param("digest",digest(root)).param("caps",array(caps)).param("key",root.deploymentKey()).update();
        return new PumpSwapCaptureSamePeriodReplacement(r.version(),r.predecessorDeploymentKey(),digest(root),r.predecessorReason(),r.allocationRootKey(),digest(root),child(root,r.successor().deploymentKey(),r.carried(),r.consumedSegments(),r.successor().template().startsAt()),r.carried(),r.consumedSegments(),r.decisionVersion(),r.ownerDecision());
    }
    static String array(Resources r){return "{"+r.requests()+","+r.receivedBytes()+","+r.subscriptionBytes()+","+r.retainedBytes()+","+r.rpcHundredths()+","+r.reservedHundredths()+","+r.seconds()+"}";}
    void rejectedUnchanged(PumpSwapCaptureSamePeriodReplacement r) {
        var before=history();assertThatThrownBy(()->enrollment.enroll(r)).isInstanceOf(RuntimeException.class);assertThat(history()).isEqualTo(before);
    }
    long receipts(){return jdbc.sql("select count(*) from marketdata.capture_same_period_replacements").query(Long.class).single();}
    @Test void allSevenReducedCarryAndExactWholeFirstSegmentHeadroomBoundariesAreIndependent() {
        for(var dimension=0;dimension<7;dimension++) {
            clean();var r=seed(false);var reduced=vector(r.carried(),dimension,component(r.carried(),dimension)-1);
            rejectedUnchanged(next(r,child(r.successor(),r.successor().deploymentKey(),reduced,0,r.successor().template().startsAt()),reduced,r.consumedSegments()));
            var first=ContinuousCaptureLedgerUseCase.resources(r.successor().template());var exact=r.carried().plus(first);
            var insufficient=vector(exact,dimension,component(exact,dimension)-1);rejectedUnchanged(rootCaps(r,insufficient));
            var boundary=rootCaps(r,exact);var held=capture.reserved(boundary.successor().template().authority().accountPeriod());
            assertThat(enrollment.enroll(boundary).carried()).isEqualTo(boundary.carried());assertThat(capture.reserved(boundary.successor().template().authority().accountPeriod())).isEqualTo(held);
            clock.set(dbNow());var admitted=ledger.admit(boundary.successor(),"boundary-worker");assertThat(admitted.segment()).isNotNull();
            assertThat(deployments.lockDeployment("alchemy",boundary.successor().deploymentKey()).reserved()).isEqualTo(exact);
        }
    }
    @Test void activeDeploymentLiveSessionLeaseAndOriginalDatabaseWindowRejectWithoutAnyWrite() {
        for(var mode:List.of("DEPLOYMENT","PARENT_LEASE","SESSION_LEASE","SESSION_RUNNING","PERIOD_END","GLOBAL_END")) {
            clean();var r=seed(false);
            if(mode.equals("DEPLOYMENT"))jdbc.sql("update marketdata.capture_deployments set state='RUNNING',reason='' where deployment_key=:key").param("key",r.predecessorDeploymentKey()).update();
            if(mode.equals("PARENT_LEASE"))jdbc.sql("update marketdata.capture_deployments set locked_until=clock_timestamp()+interval '1 hour'").update();
            if(mode.equals("SESSION_LEASE"))jdbc.sql("update marketdata.capture_sessions set locked_until=clock_timestamp()+interval '1 hour'").update();
            if(mode.equals("SESSION_RUNNING"))jdbc.sql("update marketdata.capture_sessions set state='RUNNING',locked_until=clock_timestamp()-interval '1 hour',ends_at=clock_timestamp()-interval '1 hour'").update();
            if(mode.equals("PERIOD_END"))jdbc.sql("update marketdata.capture_periods set period_end=clock_timestamp()-interval '1 second'").update();
            if(mode.equals("GLOBAL_END")) {
                var old=deployments.lockDeployment("alchemy",r.allocationRootKey()).configuration();var end=dbNow().minusSeconds(1);
                var t=old.template();var earlier=new PumpSwapCapture.Configuration(t.sessionId(),t.version(),t.provider(),end.minusSeconds(10),t.pools(),t.limits(),t.authority());
                var expired=new Configuration(old.deploymentKey(),old.version(),earlier,old.allowances(),old.opening(),end,old.maxSegments(),old.segmentSeconds(),old.maxPages(),old.decodeSeconds(),old.receiptBytes(),old.pollMillis());
                jdbc.sql("update marketdata.capture_deployments set configuration_json=:json,configuration_digest=:digest,global_end=:end")
                    .param("json",deployments.encode(expired)).param("digest",digest(expired)).param("end",java.sql.Timestamp.from(end)).update();
                r=new PumpSwapCaptureSamePeriodReplacement(r.version(),r.predecessorDeploymentKey(),digest(expired),r.predecessorReason(),r.allocationRootKey(),digest(expired),child(expired,r.successor().deploymentKey(),r.carried(),1,end.minusSeconds(1)),r.carried(),1,r.decisionVersion(),r.ownerDecision());
            }
            rejectedUnchanged(r);
        }
    }
    @Test void unknownConfiguredDimensionKnownOverrunAndArithmeticOverflowNeverBecomeZeroOrRefund() {
        for(var mode:List.of("UNKNOWN","REQUEST_OVERRUN","RECEIVED_OVERRUN","WSS_OVERRUN","RETAINED_OVERRUN","RPC_OVERRUN","CU_OVERRUN","DURATION_OVERRUN","CU_OVERFLOW")) {
            clean();var r=seed(false);
            if(mode.equals("UNKNOWN"))jdbc.sql("update marketdata.capture_sessions set configuration_json=(configuration_json::jsonb #- '{limits,windowSeconds}')::text").update();
            if(mode.equals("REQUEST_OVERRUN"))jdbc.sql("update marketdata.capture_sessions set requests=:n").param("n",r.carried().requests()+1).update();
            if(mode.equals("RECEIVED_OVERRUN"))jdbc.sql("update marketdata.capture_sessions set received_bytes=:n").param("n",r.carried().receivedBytes()+1).update();
            if(mode.equals("WSS_OVERRUN"))jdbc.sql("update marketdata.capture_sessions set subscription_bytes=:n").param("n",r.carried().subscriptionBytes()+1).update();
            if(mode.equals("RETAINED_OVERRUN"))jdbc.sql("update marketdata.capture_sessions set retained_bytes=:n").param("n",r.carried().retainedBytes()+1).update();
            if(mode.equals("RPC_OVERRUN"))jdbc.sql("update marketdata.capture_sessions set rpc_hundredths=:n").param("n",r.carried().rpcHundredths()+1).update();
            if(mode.equals("CU_OVERRUN"))jdbc.sql("update marketdata.capture_periods set known_overage_hundredths=known_overage_hundredths+1").update();
            if(mode.equals("DURATION_OVERRUN"))jdbc.sql("update marketdata.capture_sessions set configuration_json=jsonb_set(configuration_json::jsonb,'{limits,windowSeconds}',to_jsonb(181))::text").update();
            if(mode.equals("CU_OVERFLOW"))jdbc.sql("update marketdata.capture_periods set known_overage_hundredths=9223372036854775807").update();
            rejectedUnchanged(r);
        }
    }
    @Test void exactReceiptReplaySurvivesActiveAndExpiredChildWhileChangedDecisionConflicts() {
        var r=seed(false);var receipt=enrollment.enroll(r);clock.set(dbNow());ledger.admit(r.successor(),"active-child");var before=history();
        assertThat(enrollment.enroll(r)).isEqualTo(receipt);assertThat(history()).isEqualTo(before);
        var changed=CaptureSamePeriodReplacementTest.decision(r,"other-owner-decision");rejectedUnchanged(changed);
        jdbc.sql("update marketdata.capture_periods set period_end=clock_timestamp()-interval '1 second'").update();before=history();
        assertThat(enrollment.enroll(r)).isEqualTo(receipt);assertThat(history()).isEqualTo(before);
    }
    @Test void concurrentExactRetryAndDistinctSuccessorsConsumeTheirParentOnlyOnce() throws Exception {
        var r=seed(false);var equalRequest=r;var start=new java.util.concurrent.CountDownLatch(1);
        var a=java.util.concurrent.CompletableFuture.supplyAsync(()->{await(start);return enrollment.enroll(equalRequest);});var b=java.util.concurrent.CompletableFuture.supplyAsync(()->{await(start);return enrollment.enroll(equalRequest);});start.countDown();
        assertThat(a.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(b.get(10,java.util.concurrent.TimeUnit.SECONDS));assertThat(receipts()).isEqualTo(1);
        clean();r=seed(false);var one=r;var other=next(r,child(r.successor(),"other-child",r.carried(),0,r.successor().template().startsAt()),r.carried(),1);var go=new java.util.concurrent.CountDownLatch(1);
        var first=java.util.concurrent.CompletableFuture.supplyAsync(()->{await(go);try{enrollment.enroll(one);return "OK";}catch(IllegalStateException rejected){return rejected.getMessage();}});
        var second=java.util.concurrent.CompletableFuture.supplyAsync(()->{await(go);try{enrollment.enroll(other);return "OK";}catch(IllegalStateException rejected){return rejected.getMessage();}});go.countDown();
        assertThat(List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","REPLACEMENT_FORK_CONFLICT");assertThat(receipts()).isEqualTo(1);
    }
    private static void await(java.util.concurrent.CountDownLatch latch){try{if(!latch.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("bounded race latch");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}}
    void stop(ContinuousCaptureLedgerUseCase.Admission a) {
        ledger.terminal(a,"QUEUE_OVERFLOW");
        jdbc.sql("update marketdata.capture_sessions set state='STOPPED',reason='QUEUE_OVERFLOW',locked_until=clock_timestamp()-interval '1 second' where session_id=:id").param("id",a.segment().configuration().sessionId()).update();
        jdbc.sql("update marketdata.capture_deployments set locked_until=clock_timestamp()-interval '1 second' where deployment_key=:key").param("key",a.configuration().deploymentKey()).update();
    }
    @Test void twoSuccessiveChildrenCarryUniqueSessionsAndDurationOnceUnderOriginalRootCountCapsAndEnd() {
        var r=seed(false);var root=deployments.lockDeployment("alchemy",r.allocationRootKey()).configuration();var old=history();
        enrollment.enroll(r);clock.set(dbNow());var one=ledger.admit(r.successor(),"first-child-worker");stop(one);
        var carry=deployments.lockDeployment("alchemy",r.successor().deploymentKey()).reserved();
        assertThat(carry).isEqualTo(r.carried().plus(ContinuousCaptureLedgerUseCase.resources(one.segment().configuration())));
        var next=child(root,"second-replacement-child",carry,2,dbNow());
        var second=new PumpSwapCaptureSamePeriodReplacement(r.version(),r.successor().deploymentKey(),digest(r.successor()),"QUEUE_OVERFLOW",root.deploymentKey(),digest(root),next,carry,2,r.decisionVersion(),r.ownerDecision());
        var held=capture.reserved(root.template().authority().accountPeriod());assertThat(enrollment.enroll(second).carried()).isEqualTo(carry);assertThat(capture.reserved(root.template().authority().accountPeriod())).isEqualTo(held);
        clock.set(dbNow());var admitted=ledger.admit(next,"second-child-worker");assertThat(admitted.segment()).isNotNull();
        assertThat(deployments.lockDeployment("alchemy",next.deploymentKey()).reserved()).isEqualTo(carry.plus(ContinuousCaptureLedgerUseCase.resources(admitted.segment().configuration())));
        assertThat(next.maxSegments()).isEqualTo(2);assertThat(next.globalEnd()).isEqualTo(root.globalEnd());assertThat(next.allowances()).isEqualTo(root.allowances());
        assertThat(deployments.lockDeployment("alchemy",next.deploymentKey()).reserved().seconds()).isEqualTo(540);
        assertThat(receipts()).isEqualTo(2);assertThat(jdbc.sql("select count(distinct session_id) from marketdata.capture_segments").query(Long.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("select row_to_json(d)::text from marketdata.capture_deployments d where deployment_key=:key").param("key",root.deploymentKey()).query(String.class).list()).isEqualTo(old.get("capture_deployments"));
        rejectedUnchanged(next(r,child(root,"stale-root-sibling",r.carried(),1,dbNow()),r.carried(),1));
    }
    @Test void stoppedBeforeFirstReservationCannotCreateAnUnboundedZeroSegmentLineage() {
        var r=seed(false);var root=deployments.lockDeployment("alchemy",r.allocationRootKey()).configuration();
        for(var transition=1;transition<=4;transition++) {
            enrollment.enroll(r);
            jdbc.sql("update marketdata.capture_deployments set state='STOPPED',reason='QUEUE_OVERFLOW',locked_until=clock_timestamp()-interval '1 second' where deployment_key=:key").param("key",r.successor().deploymentKey()).update();
            var next=child(root,"zero-segment-child-"+transition,r.carried(),1,dbNow());
            r=new PumpSwapCaptureSamePeriodReplacement(r.version(),r.successor().deploymentKey(),digest(r.successor()),"QUEUE_OVERFLOW",root.deploymentKey(),digest(root),next,r.carried(),1,r.decisionVersion(),r.ownerDecision());
        }
        assertThat(receipts()).isEqualTo(4);assertThat(jdbc.sql("select count(*) from marketdata.capture_segments").query(Long.class).single()).isEqualTo(1);
        rejectedUnchanged(r);
    }
    @Test void staleRootSessionFenceManualAndUnreceiptedOrdinaryAliasesRemainRejected() {
        var r=seed(false);var root=deployments.lockDeployment("alchemy",r.allocationRootKey()).configuration();var original=deployments.latest(root.deploymentKey()).orElseThrow();
        rejectedUnchanged(next(r,r.successor(),r.carried(),0));
        assertThatThrownBy(()->ledger.admit(r.successor(),"unreceipted-alias")).hasMessage("CONTINUOUS_CONFIGURATION_CONFLICT");enrollment.enroll(r);
        var before=history();assertThatThrownBy(()->capture.claim(original.configuration().sessionId(),"old-worker:1",dbNow(),dbNow().plusSeconds(60))).hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
        assertThatThrownBy(()->capture.fence(original.configuration().sessionId(),"old-worker:1",dbNow(),dbNow().plusSeconds(60))).hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
        assertThatThrownBy(()->ledger.heartbeat(new ContinuousCaptureLedgerUseCase.Admission(root,deployments.lockDeployment("alchemy",root.deploymentKey()).fence(),original,false,""))).hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
        assertThatThrownBy(()->capture.manualStartGuard("alchemy")).hasMessage("ENROLLED_PROVIDER_REQUIRES_ADMISSION");
        assertThat(history()).isEqualTo(before);
    }
    @Test void replacementEligibilityUsesPostlockDatabaseTimeNotTheCallerControlledClock() {
        var r=seed(false);clock.set(r.successor().globalEnd().plusSeconds(86400));var before=dbNow();
        var receipt=enrollment.enroll(r);assertThat(receipt.appliedAt()).isBetween(before,dbNow());
        assertThat(receipts()).isEqualTo(1);assertThat(capture.reserved(r.successor().template().authority().accountPeriod())).isEqualTo(r.carried().reservedHundredths());
    }
    static Configuration policy(Configuration c,String version) {
        var t=c.template();return new Configuration(c.deploymentKey(),c.version(),new PumpSwapCapture.Configuration(t.sessionId(),version,t.provider(),t.startsAt(),t.pools(),t.limits(),t.authority()),c.allowances(),c.opening(),c.globalEnd(),c.maxSegments(),c.segmentSeconds(),c.maxPages(),c.decodeSeconds(),c.receiptBytes(),c.pollMillis());
    }
    PumpSwapCapturePeriodContinuation monthlyRequest() {
        var db=dbNow();var old=policy(CapturePeriodContinuationIT.configuration("monthly-parent",db.minusSeconds(3600),db.minusSeconds(600),100,1000),"capture-rpc-reuse-v6");
        var a=old.template().authority();
        var original=new PumpSwapCapture.Authority(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),2_860_000_000L,new BigDecimal("15"),a.usdPerMillionCu(),new BigDecimal("0.04"),"historical-original-owner",a.retentionAllowed(),a.exclusiveAllocation());
        var t=old.template();var oldTemplate=new PumpSwapCapture.Configuration(t.sessionId(),t.version(),t.provider(),t.startsAt(),t.pools(),t.limits(),original);clock.set(t.startsAt());
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->capture.period(oldTemplate,CaptureBudget.ceiling(oldTemplate),120));
        amendments.amend(new PumpSwapCaptureAuthorityAmendment("capture-authority-amendment-v1","alchemy",original,a,"historical-approved-owner"));
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->deployments.insert(old,digest(old),"historical-owner",db.minusSeconds(60)));
        jdbc.sql("update marketdata.capture_deployments set state='STOPPED',reason='DEPLOYMENT_GLOBAL_END'").update();clock.set(db);
        var root=policy(CapturePeriodContinuationIT.configuration("monthly-root",db,db.plusSeconds(7200),200,1000),"capture-rpc-reuse-v6");
        return new PumpSwapCapturePeriodContinuation("continuous-period-continuation-v1",old.deploymentKey(),digest(old),a.periodEnd(),root,"explicit-monthly-owner",new PumpSwapCapturePeriodContinuation.Baseline("OWNER_REPORTED",db,200L,20L));
    }
    @Test void replacementOfMonthlyCreatedRootUsesOnlyExactDescendantAuthorityAndKeepsOldMonthMoneySeparate() {
        var month=monthlyRequest();monthly.enroll(month);clock.set(dbNow());var first=ledger.admit(month.successor(),"monthly-root-worker");stop(first);
        var root=month.successor();var carry=deployments.lockDeployment("alchemy",root.deploymentKey()).reserved();var successor=child(root,"monthly-root-replacement",carry,1,dbNow());
        var request=new PumpSwapCaptureSamePeriodReplacement("continuous-same-period-replacement-v1",root.deploymentKey(),digest(root),"QUEUE_OVERFLOW",root.deploymentKey(),digest(root),successor,carry,1,"owner-decision-v1","explicit-monthly-descendant");
        var history=history();var oldMonth=capture.reserved("period-monthly-parent");var currentMonth=capture.reserved("period-monthly-root");
        enrollment.enroll(request);assertThat(capture.reserved("period-monthly-root")).isEqualTo(currentMonth);clock.set(dbNow());
        var admitted=ledger.admit(successor,"replacement-worker");assertThat(admitted.segment()).isNotNull();
        assertThat(capture.reserved("period-monthly-parent")).isEqualTo(oldMonth);
        assertThat(capture.reserved("period-monthly-root")).isEqualTo(currentMonth+ContinuousCaptureLedgerUseCase.resources(admitted.segment().configuration()).reservedHundredths());
        assertThat(jdbc.sql("select row_to_json(t)::text from marketdata.capture_period_continuations t").query(String.class).list()).isEqualTo(history.get("capture_period_continuations"));
        assertThat(jdbc.sql("select row_to_json(t)::text from marketdata.capture_authority_amendments t").query(String.class).list()).isEqualTo(history.get("capture_authority_amendments"));
        assertThat(receipts()).isEqualTo(1);
    }
    @Test void twoMonthlyRootDescendantsWithEqualLoadedTemplateStillAdmitTheirExactDistinctReceiptedAllocations() {
        var month=monthlyRequest();monthly.enroll(month);clock.set(dbNow());var original=ledger.admit(month.successor(),"monthly-initial-worker");stop(original);
        var root=month.successor();var carry=deployments.lockDeployment("alchemy",root.deploymentKey()).reserved();var first=child(root,"equal-template-first",carry,1,dbNow());
        var r=new PumpSwapCaptureSamePeriodReplacement("continuous-same-period-replacement-v1",root.deploymentKey(),digest(root),"QUEUE_OVERFLOW",root.deploymentKey(),digest(root),first,carry,1,"owner-decision-v1","first-explicit-owner");
        enrollment.enroll(r);clock.set(dbNow());var admitted=ledger.admit(first,"first-worker");stop(admitted);
        carry=deployments.lockDeployment("alchemy",first.deploymentKey()).reserved();
        var second=new Configuration("equal-template-second",root.version(),first.template(),root.allowances(),carry,root.globalEnd(),2,root.segmentSeconds(),root.maxPages(),root.decodeSeconds(),root.receiptBytes(),root.pollMillis());
        var next=new PumpSwapCaptureSamePeriodReplacement(r.version(),first.deploymentKey(),digest(first),"QUEUE_OVERFLOW",root.deploymentKey(),digest(root),second,carry,2,r.decisionVersion(),"second-explicit-owner");
        assertThat(second.template()).isEqualTo(first.template());enrollment.enroll(next);clock.set(dbNow());var oldMoney=capture.reserved("period-monthly-parent");
        assertThatCode(()->ledger.admit(second,"second-worker")).as("two exact eligible receipt templates do not turn one valid descendant into an ambiguous authority lookup").doesNotThrowAnyException();
        assertThat(ledger.admit(second,"second-worker").segment()).isNotNull();assertThat(capture.reserved("period-monthly-parent")).isEqualTo(oldMoney);
        assertThat(receipts()).isEqualTo(2);assertThat(jdbc.sql("select count(*) from marketdata.capture_segments").query(Long.class).single()).isEqualTo(3);
    }
    @Test void monthlyFirstAndReplacementFirstBothPreventAnySecondCrossTableChild() {
        var month=monthlyRequest();monthly.enroll(month);var old=deployments.lockDeployment("alchemy",month.predecessorDeploymentKey()).configuration();
        var historicalChild=child(old,"cross-kind-sibling",old.opening(),0,old.template().startsAt());
        var afterMonthly=new PumpSwapCaptureSamePeriodReplacement("continuous-same-period-replacement-v1",old.deploymentKey(),digest(old),"DEPLOYMENT_GLOBAL_END",old.deploymentKey(),digest(old),historicalChild,old.opening(),0,"owner-decision-v1","other-child");
        var before=history();assertThatThrownBy(()->enrollment.enroll(afterMonthly)).hasMessage("REPLACEMENT_FORK_CONFLICT");assertThat(history()).isEqualTo(before);
        clean();month=monthlyRequest();old=deployments.lockDeployment("alchemy",month.predecessorDeploymentKey()).configuration();historicalChild=child(old,"historical-replacement",old.opening(),0,old.template().startsAt());
        var existing=new PumpSwapCaptureSamePeriodReplacement("continuous-same-period-replacement-v1",old.deploymentKey(),digest(old),"DEPLOYMENT_GLOBAL_END",old.deploymentKey(),digest(old),historicalChild,old.opening(),0,"owner-decision-v1","earlier-same-period-owner");
        var parent=old;var childConfiguration=historicalChild;
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->{
            capture.providerAdmissionLock("alchemy");deployments.insert(childConfiguration,digest(childConfiguration),"historical-child",parent.globalEnd().minusSeconds(1));
            jdbc.sql("update marketdata.capture_deployments set state='STOPPED',reason='QUEUE_OVERFLOW' where deployment_key=:key").param("key",childConfiguration.deploymentKey()).update();
            jdbc.sql("""
                insert into marketdata.capture_same_period_replacements values(:next,:parent,:parent,'alchemy',:period,'continuous-same-period-replacement-v1',:parentDigest,:parentDigest,:nextDigest,'DEPLOYMENT_GLOBAL_END','owner-decision-v1','earlier-same-period-owner',:request,:digest,:at,:end,cast(:caps as bigint[]),:maximum,cast(:carry as bigint[]),0)
                """).param("next",childConfiguration.deploymentKey()).param("parent",parent.deploymentKey()).param("period",parent.template().authority().accountPeriod()).param("parentDigest",digest(parent)).param("nextDigest",digest(childConfiguration))
                .param("request",deployments.encode(existing)).param("digest",new CanonicalFingerprint().field("version",existing.version()).field("request",deployments.encode(existing)).finish())
                .param("at",java.sql.Timestamp.from(parent.template().startsAt())).param("end",java.sql.Timestamp.from(parent.globalEnd())).param("caps",array(parent.allowances())).param("maximum",parent.maxSegments()).param("carry",array(parent.opening())).update();
        });
        before=history();var request=month;assertThatThrownBy(()->monthly.enroll(request)).hasMessage("CONTINUATION_FORK_OR_PERIOD_CONFLICT");assertThat(history()).isEqualTo(before);
    }
    @Test void lowerFiniteChildAllowsNoRepricingRefundOrOriginalDurationAndCountReset() {
        var r=seed(false);var old=history();var c=r.successor();var t=c.template();var l=t.limits();
        var limits=new PumpSwapCapture.Limits(90,1000,500_000_000,100_000_000,1_000_000,1_000_000,l.messageBytes(),l.responseBytes(),l.queueItems(),l.queueBytes(),l.timeoutSeconds(),l.retries(),l.reconnects(),l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds());
        var template=new PumpSwapCapture.Configuration(t.sessionId(),t.version(),t.provider(),t.startsAt(),t.pools(),limits,t.authority());
        var smaller=new Configuration(c.deploymentKey(),c.version(),template,c.allowances(),c.opening(),c.globalEnd(),c.maxSegments(),90,c.maxPages(),c.decodeSeconds(),c.receiptBytes(),c.pollMillis());
        var request=next(r,smaller,r.carried(),1);var held=capture.reserved(t.authority().accountPeriod());enrollment.enroll(request);assertThat(capture.reserved(t.authority().accountPeriod())).isEqualTo(held);
        clock.set(dbNow());var admitted=ledger.admit(smaller,"smaller-segment-worker");assertThat(admitted.segment().configuration().limits().windowSeconds()).isEqualTo(90);
        var reserved=deployments.lockDeployment("alchemy",smaller.deploymentKey()).reserved();assertThat(reserved).isEqualTo(r.carried().plus(ContinuousCaptureLedgerUseCase.resources(admitted.segment().configuration())));
        assertThat(reserved.seconds()).isEqualTo(270);assertThat(capture.reserved(t.authority().accountPeriod())).isEqualTo(held+ContinuousCaptureLedgerUseCase.resources(admitted.segment().configuration()).reservedHundredths());
        assertThat(jdbc.sql("select row_to_json(d)::text from marketdata.capture_deployments d where deployment_key=:root").param("root",r.allocationRootKey()).query(String.class).list()).isEqualTo(old.get("capture_deployments"));
    }
    @Test void providerRetentionAcrossPeriodsIncludesKnownOverrunOnceWithoutTransferringOldMonthlyCu() {
        var r=seed(false);var root=deployments.lockDeployment("alchemy",r.allocationRootKey()).configuration();var t=root.template();var db=dbNow();var a=t.authority();
        var authority=new PumpSwapCapture.Authority("historical-retained-period",db.minusSeconds(600),a.source(),0L,0,a.ceilingHundredths(),a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),"old-period-owner",true,true);
        var historical=new PumpSwapCapture.Configuration(UUID.randomUUID(),t.version(),t.provider(),db.minusSeconds(3600),t.pools(),t.limits(),authority);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->{
            capture.bootstrapContinuationPeriod(historical,CaptureBudget.ceiling(historical),0);capture.insertSession(historical,"sha256:"+"e".repeat(64),"historical-only",db.minusSeconds(600),db.minusSeconds(600),100);
        });
        jdbc.sql("update marketdata.capture_sessions set state='STOPPED',reason='OLD_FAILURE',retained_bytes=2000000000 where session_id=:id").param("id",historical.sessionId()).update();
        rejectedUnchanged(r);var carry=vector(r.carried(),3,r.carried().retainedBytes()+2_000_000_000L);var next=child(root,r.successor().deploymentKey(),carry,1,db);
        var request=next(r,next,carry,1);var oldPeriod=jdbc.sql("select row_to_json(p)::text from marketdata.capture_periods p where account_period='historical-retained-period'").query(String.class).single();
        assertThat(enrollment.enroll(request).carried().retainedBytes()).isEqualTo(carry.retainedBytes());
        assertThat(jdbc.sql("select row_to_json(p)::text from marketdata.capture_periods p where account_period='historical-retained-period'").query(String.class).single()).isEqualTo(oldPeriod);
        clock.set(dbNow());var admitted=ledger.admit(next,"retention-child-worker");assertThat(admitted.segment()).isNotNull();
        assertThat(deployments.lockDeployment("alchemy",next.deploymentKey()).reserved().retainedBytes()).isEqualTo(carry.retainedBytes()+next.template().limits().retainedBytes());
    }
    @Test void rootAndParentDigestsReasonCapsRatesProofAndEndMustRemainExact() {
        for(var mode:List.of("DIGEST","REASON","ROOT","SCALAR_CAPS","CEILING","END","PROOF","RATE")) {
            clean();var r=seed(false);
            if(mode.equals("DIGEST")) {
                var forged="sha256:"+"0".repeat(64);jdbc.sql("update marketdata.capture_deployments set configuration_digest=:digest").param("digest",forged).update();
                r=new PumpSwapCaptureSamePeriodReplacement(r.version(),r.predecessorDeploymentKey(),forged,r.predecessorReason(),r.allocationRootKey(),forged,r.successor(),r.carried(),1,r.decisionVersion(),r.ownerDecision());
            }
            if(mode.equals("REASON"))r=new PumpSwapCaptureSamePeriodReplacement(r.version(),r.predecessorDeploymentKey(),r.predecessorConfigurationDigest(),"OTHER_REASON",r.allocationRootKey(),r.allocationRootConfigurationDigest(),r.successor(),r.carried(),1,r.decisionVersion(),r.ownerDecision());
            if(mode.equals("ROOT"))r=new PumpSwapCaptureSamePeriodReplacement(r.version(),r.predecessorDeploymentKey(),r.predecessorConfigurationDigest(),r.predecessorReason(),"unknown-original-root",r.allocationRootConfigurationDigest(),r.successor(),r.carried(),1,r.decisionVersion(),r.ownerDecision());
            if(mode.equals("SCALAR_CAPS"))jdbc.sql("update marketdata.capture_deployments set allowances[1]=allowances[1]+1").update();
            if(mode.equals("CEILING"))jdbc.sql("update marketdata.capture_periods set ceiling_hundredths=ceiling_hundredths+1").update();
            if(mode.equals("END") || mode.equals("PROOF") || mode.equals("RATE")) {
                var c=r.successor();var t=c.template();var pools=t.pools();var authority=t.authority();
                if(mode.equals("PROOF")) {var changed=new ArrayList<>(pools);var p=pools.getFirst();var proof=p.proof();changed.set(0,new PumpSwapCapture.Pool(p.address(),p.program(),p.baseVault(),p.quoteVault(),p.baseMint(),p.quoteMint(),p.baseTokenProgram(),p.quoteTokenProgram(),new PumpSwapCapture.LinkageProof(proof.contextSlot(),proof.commitment(),proof.sourceVersion(),"changed-proof-provenance",proof.payload(),proof.digest())));pools=changed;}
                if(mode.equals("RATE")) {var a=authority;authority=new PumpSwapCapture.Authority(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),a.ceilingHundredths(),a.ceilingUsd(),new BigDecimal("0.526"),a.subscriptionCuPerByte(),a.ownerAuthorization(),a.retentionAllowed(),a.exclusiveAllocation());}
                var template=new PumpSwapCapture.Configuration(t.sessionId(),t.version(),t.provider(),t.startsAt(),pools,t.limits(),authority);
                var changed=new Configuration(c.deploymentKey(),c.version(),template,c.allowances(),c.opening(),mode.equals("END")?c.globalEnd().plusSeconds(1):c.globalEnd(),c.maxSegments(),c.segmentSeconds(),c.maxPages(),c.decodeSeconds(),c.receiptBytes(),c.pollMillis());r=next(r,changed,r.carried(),1);
            }
            rejectedUnchanged(r);
        }
    }
    @Test void populatedV17ForwardMigrationPreservesAllOldRowsAndRunningIndexWhileNewReceiptConstraintsAreReal() {
        var request=seed(false);jdbc.sql("create database replacement_populated_v17").update();
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(postgres.getJdbcUrl().replace("/"+postgres.getDatabaseName(),"/replacement_populated_v17"),postgres.getUsername(),postgres.getPassword());
        org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("17").load().migrate();
        var sql=org.springframework.jdbc.core.simple.JdbcClient.create(ds);var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var captured=new JdbcCaptureStore(sql,json);var stored=new JdbcContinuousCaptureStore(sql,json,captured);
        assertThat(stored.replacementSchemaPresent()).isFalse();
        for(var table:List.of("capture_periods","capture_deployments","capture_sessions","raw_transactions","raw_account_snapshots","capture_segments","capture_dispositions","capture_envelopes","capture_gaps","capture_state_members","capture_authority_amendments","capture_period_continuations")) {
            for(var row:jdbc.sql("select row_to_json(t)::text from marketdata."+table+" t").query(String.class).list())
                sql.sql("insert into marketdata."+table+" overriding system value select * from jsonb_populate_record(null::marketdata."+table+",cast(:row as jsonb))").param("row",row).update();
        }
        var names=sql.sql("select tablename from pg_tables where schemaname='marketdata' order by tablename").query(String.class).list();var before=new TreeMap<String,List<String>>();
        for(var name:names)before.put(name,sql.sql("select row_to_json(t)::text from marketdata."+name+" t order by row_to_json(t)::text collate \"C\"").query(String.class).list());
        var index=sql.sql("select indexdef from pg_indexes where schemaname='marketdata' and indexname='capture_deployments_running_provider_idx'").query(String.class).single();
        assertThat(org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate().migrationsExecuted).isEqualTo(1);
        for(var name:names)assertThat(sql.sql("select row_to_json(t)::text from marketdata."+name+" t order by row_to_json(t)::text collate \"C\"").query(String.class).list()).isEqualTo(before.get(name));
        assertThat(stored.replacementSchemaPresent()).isTrue();
        assertThat(sql.sql("select indexdef from pg_indexes where schemaname='marketdata' and indexname='capture_deployments_running_provider_idx'").query(String.class).single()).isEqualTo(index);
        assertThat(sql.sql("select count(*) from pg_constraint where conname='capture_deployments_provider_period_key'").query(Long.class).single()).isZero();
        assertThat(sql.sql("select count(*) from pg_constraint where conrelid='marketdata.capture_same_period_replacements'::regclass and contype='f'").query(Long.class).single()).isEqualTo(4);
        assertThat(sql.sql("select count(*) from pg_constraint where conrelid='marketdata.capture_same_period_replacements'::regclass and contype='f' and (confdeltype<>'a' or confupdtype<>'a')").query(Long.class).single()).isZero();
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        var useCase=new EnrollCaptureSamePeriodReplacementUseCase(stored,captured);assertThat(tx.execute(s->useCase.enroll(request)).successorDeploymentKey()).isEqualTo(request.successor().deploymentKey());
        assertThat(sql.sql("select count(*) from marketdata.capture_same_period_replacements").query(Long.class).single()).isEqualTo(1);
        var immutable=sql.sql("select row_to_json(t)::text from marketdata.capture_same_period_replacements t").query(String.class).single();
        for(var statement:List.of("update marketdata.capture_same_period_replacements set allocation_root_key='missing-root'",
            "update marketdata.capture_same_period_replacements set predecessor_deployment_key='missing-parent'",
            "update marketdata.capture_same_period_replacements set account_period='missing-period'",
            "update marketdata.capture_same_period_replacements set version='unqualified-version'",
            "update marketdata.capture_same_period_replacements set request_json=repeat('x',1000001)",
            "update marketdata.capture_same_period_replacements set carried=ARRAY[0,0,0,0,0,0,NULL]::bigint[]",
            "update marketdata.capture_same_period_replacements set consumed_segments=original_max_segments")) {
            assertThatThrownBy(()->sql.sql(statement).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(sql.sql("select row_to_json(t)::text from marketdata.capture_same_period_replacements t").query(String.class).single()).isEqualTo(immutable);
        }
        var clone=child(request.successor(),"duplicate-parent-fixture",request.carried(),0,request.successor().template().startsAt());
        var cloneInsert="insert into marketdata.capture_deployments select :key,provider,account_period,owner_authorization,:json,:digest,allowances,reserved,global_end,max_segments,:state,'constraint-fixture',holder,generation,locked_until from marketdata.capture_deployments where deployment_key=:source";
        assertThatThrownBy(()->sql.sql(cloneInsert).param("key",clone.deploymentKey()).param("json",stored.encode(clone)).param("digest",digest(clone)).param("state","RUNNING").param("source",request.successor().deploymentKey()).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        sql.sql(cloneInsert).param("key",clone.deploymentKey()).param("json",stored.encode(clone)).param("digest",digest(clone)).param("state","STOPPED").param("source",request.successor().deploymentKey()).update();
        assertThatThrownBy(()->sql.sql("insert into marketdata.capture_same_period_replacements select :child,predecessor_deployment_key,allocation_root_key,provider,account_period,version,predecessor_digest,root_digest,successor_digest,predecessor_reason,decision_version,owner_decision,request_json,request_digest,applied_at,original_global_end,original_allowances,original_max_segments,carried,consumed_segments from marketdata.capture_same_period_replacements")
            .param("child",clone.deploymentKey()).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(sql.sql("select count(*) from marketdata.capture_same_period_replacements").query(Long.class).single()).isEqualTo(1);
    }
    private void awaitAdvisoryWaiters(long expected) throws Exception {
        var deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while(jdbc.sql("select count(*) from pg_locks where locktype='advisory' and classid=1129464656 and not granted").query(Long.class).single()<expected && System.nanoTime()<deadline)Thread.sleep(10);
        assertThat(jdbc.sql("select count(*) from pg_locks where locktype='advisory' and classid=1129464656 and not granted").query(Long.class).single()).isGreaterThanOrEqualTo(expected);
    }
    @Test void realCrossTableForkRacesInBothProviderLockOrderingsNeverCreateTwoChildren() throws Exception {
        for(var monthlyReady:List.of(true,false))for(var monthlyQueuedFirst:List.of(true,false)) {
            clean();PumpSwapCapturePeriodContinuation periodRequest;PumpSwapCaptureSamePeriodReplacement replacementRequest;
            if(monthlyReady) {
                periodRequest=monthlyRequest();var old=deployments.lockDeployment("alchemy",periodRequest.predecessorDeploymentKey()).configuration();
                var next=child(old,"racing-historical-child",old.opening(),0,old.template().startsAt());
                replacementRequest=new PumpSwapCaptureSamePeriodReplacement("continuous-same-period-replacement-v1",old.deploymentKey(),digest(old),"DEPLOYMENT_GLOBAL_END",old.deploymentKey(),digest(old),next,old.opening(),0,"owner-decision-v1","racing-same-period-owner");
            } else {
                replacementRequest=seed(false);var root=deployments.lockDeployment("alchemy",replacementRequest.allocationRootKey()).configuration();var start=root.template().authority().periodEnd();
                var next=policy(CapturePeriodContinuationIT.configuration("racing-future-month",start,start.plusSeconds(7200),200,replacementRequest.carried().retainedBytes()),root.template().version());
                periodRequest=new PumpSwapCapturePeriodContinuation("continuous-period-continuation-v1",root.deploymentKey(),digest(root),start,next,"racing-monthly-owner",new PumpSwapCapturePeriodContinuation.Baseline("OWNER_REPORTED",start,200L,20L));
            }
            var month=periodRequest;var replacement=replacementRequest;var before=history();
            java.util.function.Supplier<String> monthlyOperation=()->{try{monthly.enroll(month);return "MONTHLY_OK";}catch(IllegalStateException rejected){return rejected.getMessage();}};
            java.util.function.Supplier<String> replacementOperation=()->{try{enrollment.enroll(replacement);return "REPLACEMENT_OK";}catch(IllegalStateException rejected){return rejected.getMessage();}};
            java.util.concurrent.CompletableFuture<String> first,second;
            try(var lock=datasource.getConnection()) {
                lock.setAutoCommit(false);try(var statement=lock.prepareStatement("select pg_advisory_xact_lock(1129464656,hashtext('alchemy'))")){statement.execute();}
                first=java.util.concurrent.CompletableFuture.supplyAsync(monthlyQueuedFirst?monthlyOperation:replacementOperation);awaitAdvisoryWaiters(1);
                second=java.util.concurrent.CompletableFuture.supplyAsync(monthlyQueuedFirst?replacementOperation:monthlyOperation);awaitAdvisoryWaiters(2);lock.commit();
            }
            var outcomes=List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(o->o.endsWith("_OK")).containsExactly(monthlyReady?"MONTHLY_OK":"REPLACEMENT_OK");
            assertThat(jdbc.sql("select (select count(*) from marketdata.capture_same_period_replacements)+(select count(*) from marketdata.capture_period_continuations)").query(Long.class).single()).isEqualTo(1);
            for(var entry:before.entrySet())assertThat(history().get(entry.getKey())).containsAll(entry.getValue());
        }
    }
    @Test void validStoppedAmendedRootEnrollsOneImmutableChildThenReservesOneWholeSegmentWithoutChangingOld940OrHolds() {
        var r=seed(true);var before=history();var period=capture.reserved(r.successor().template().authority().accountPeriod());
        assertThatCode(()->enrollment.enroll(r)).as("explicit valid stopped same-period parent enrolls its one immutable child before provider work").doesNotThrowAnyException();
        var receipt=enrollment.enroll(r);assertThat(receipt.successorDeploymentKey()).isEqualTo(r.successor().deploymentKey());
        assertThat(capture.reserved(r.successor().template().authority().accountPeriod())).isEqualTo(period);
        for(var e:before.entrySet())assertThat(history().get(e.getKey())).containsAll(e.getValue());
        assertThat(jdbc.sql("select count(*) from marketdata.capture_deployments").query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("select count(*) from marketdata.capture_same_period_replacements").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from marketdata.capture_dispositions where status='PENDING'").query(Long.class).single()).isEqualTo(940);
        clock.set(dbNow());
        var admitted=ledger.admit(r.successor(),"new-worker");assertThat(admitted.segment()).isNotNull();
        var reservation=ContinuousCaptureLedgerUseCase.resources(admitted.segment().configuration());
        assertThat(capture.reserved(r.successor().template().authority().accountPeriod())).isEqualTo(period+reservation.reservedHundredths());
        assertThat(deployments.lockDeployment("alchemy",r.successor().deploymentKey()).reserved()).isEqualTo(r.carried().plus(reservation));
        assertThat(enrollment.enroll(r)).isEqualTo(receipt);
        assertThat(ledger.admit(r.successor(),"new-worker").segment()).isEqualTo(admitted.segment());
        assertThat(capture.reserved(r.successor().template().authority().accountPeriod())).isEqualTo(period+reservation.reservedHundredths());
        assertThat(jdbc.sql("select count(*) from marketdata.capture_gaps where session_id=:id and kind in ('TRANSACTION','STATE') and boundary_signature=:sig and from_slot=101 and reason like 'SAME_PERIOD_BOUNDARY:%'")
            .param("id",admitted.segment().configuration().sessionId()).param("sig",PumpSwapDurableInboxIT.signature(0)).query(Long.class).single()).isEqualTo(2);
        for(var e:before.entrySet()) {
            if(!e.getKey().equals("capture_periods"))assertThat(history().get(e.getKey())).containsAll(e.getValue());
        }
    }
}
