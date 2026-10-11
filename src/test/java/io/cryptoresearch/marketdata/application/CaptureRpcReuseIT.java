package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static io.cryptoresearch.marketdata.application.PumpSwapPoolCaptureTest.*;
import static io.cryptoresearch.marketdata.application.CaptureRpcReuseTest.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.*;
import io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport;
import io.cryptoresearch.kernel.api.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(CaptureRpcReuseIT.Clocks.class)
class CaptureRpcReuseIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired CaptureLedgerUseCase ledger;
    @Autowired CaptureEvidenceUseCase evidence;
    @Autowired PumpSwapPoolCollector collector;
    @Autowired RawTransactionStore raw;
    @Autowired StoreRawTransactionUseCase storage;
    @Autowired MutableUtcClock clock;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
    private Object activeRun;
    private Thread writer;
    static final String SIG="1".repeat(63)+"2";
    static final String V1_CONFIG="\"transactionConfig\":{\"computeUnitLimit\":null,\"heapSize\":null,\"loadedAccountsDataSizeLimit\":null,\"priorityFee\":null},";
    static String full(String signature,long slot,String version,boolean failed) {
        return "{ \"slot\" : "+slot+",\"blockTime\":null,\"version\":"+version+",\"transaction\":{\"signatures\":[\""+signature+"\"],\"message\":{"+("1".equals(version)?V1_CONFIG:"")+"\"header\":{\"numRequiredSignatures\":1,\"numReadonlySignedAccounts\":0,\"numReadonlyUnsignedAccounts\":0},\"accountKeys\":[\""+address(0)+"\"],\"recentBlockhash\":\""+address(0)+"\",\"instructions\":[]}},\"meta\":{\"err\":"+(failed?"{\"InstructionError\":[0,\"InvalidArgument\"]}":"null")+",\"fee\":5000,\"preBalances\":[10000],\"postBalances\":[5000],\"preTokenBalances\":[],\"postTokenBalances\":[],\"innerInstructions\":[],\"logMessages\":[]}}";
    }
    @BeforeEach void isolate() throws Exception {
        stopWriter();
        jdbc.sql("TRUNCATE marketdata.capture_sessions,marketdata.capture_periods,marketdata.raw_transactions CASCADE").update(); clock.set(START);
    }
    @AfterEach void stopWriter() throws Exception {
        if(writer==null) return;
        var terminal=activeRun.getClass().getDeclaredMethod("terminal",String.class); terminal.setAccessible(true); terminal.invoke(activeRun,"OWNER_STOP");
        writer.join(java.time.Duration.ofSeconds(11)); assertThat(writer.isAlive()).as("owned synthetic writer exits").isFalse(); writer=null; activeRun=null;
    }
    private Configuration config() { return policy(configuration(SESSION,0L),"capture-rpc-reuse-v6","synthetic-reuse-period","0.04",20000000); }
    private Object run(Configuration c,AtomicInteger fetches,String payload) throws Exception {
        ledger.open(c,"writer");
        var type=java.util.Arrays.stream(PumpSwapPoolCollector.class.getDeclaredClasses()).filter(t->t.getSimpleName().equals("Run")).findFirst().orElseThrow();
        var constructor=type.getDeclaredConstructors()[0]; constructor.setAccessible(true); var run=constructor.newInstance(collector,c,"writer");
        var transport=org.mockito.Mockito.mock(JdkPumpSwapTransport.class);
        org.mockito.Mockito.when(transport.rpc(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(call->{ fetches.incrementAndGet(); return new JdkPumpSwapTransport.Response(payload,"{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":"+payload+"}",clock.instant()); });
        field(run,"transport",transport); activeRun=run;
        var loop=type.getDeclaredMethod("writerLoop"); loop.setAccessible(true);
        writer=Thread.ofVirtual().name("synthetic-reuse-writer").unstarted(()->{ try { loop.invoke(run); } catch(Exception failure) { throw new AssertionError(failure); } });
        field(run,"databaseWriter",writer); writer.start(); return run;
    }
    private static void field(Object run,String name,Object value) throws Exception { var f=run.getClass().getDeclaredField(name); f.setAccessible(true); f.set(run,value); }
    private String acquire(Object run,String pool,String signature,long slot,String notification,CaptureLedgerUseCase.HistoryPage page) throws Exception {
        var method=run.getClass().getDeclaredMethod("transaction",String.class,String.class,long.class,String.class,Instant.class,CaptureLedgerUseCase.HistoryPage.class); method.setAccessible(true);
        try { return (String)method.invoke(run,pool,signature,slot,notification,clock.instant(),page); }
        catch(java.lang.reflect.InvocationTargetException failed) { if(failed.getCause() instanceof RuntimeException cause) throw cause; throw failed; }
    }
    private StoredRawTransaction saved(String signature) { return raw.find(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,signature),"alchemy").orElseThrow(); }
    @Test void repeatedAndCrossPoolSuccessAndFailureUseOneFetchAndRetainEveryNotification() throws Exception {
        for(var failed:List.of(false,true)) {
            isolate(); var c=config(); var fetches=new AtomicInteger(); var run=run(c,fetches,full(SIG,101,"1",failed));
            var status=failed?"FAILED_TRANSACTION":"CAPTURED";
            assertThat(acquire(run,address(0),SIG,101,"{\"notification\":1}",null)).isEqualTo(status); var first=saved(SIG); clock.set(START.plusSeconds(1));
            assertThat(acquire(run,address(0),SIG,101,"{\"notification\":2}",null)).isEqualTo(status);
            assertThat(acquire(run,address(5),SIG,101,"{\"notification\":3}",null)).isEqualTo(status);
            assertThat(fetches.get()).as("trusted repeated/cross-pool %s raw avoids paid refetch",status).isEqualTo(1);
            assertThat(saved(SIG)).isEqualTo(first); assertThat(ledger.dispositions(SESSION)).hasSize(2).allSatisfy(d->assertThat(d.status()).isEqualTo(status));
            assertThat(ledger.boundary(SESSION,address(5))).contains(SIG);
            assertThat(ledger.replay(SESSION).envelopes()).filteredOn(e->e.kind().equals("CAPTURE_TRANSACTION_NOTIFICATION")).hasSize(3);
        }
    }
    private record Source(CaptureLedgerUseCase.HistoryPage page,String reference) { }
    private Source source(Configuration c,String row) {
        var page="{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"data\":["+row+"],\"paginationToken\":null}}";
        ledger.envelopeAt(SESSION,"writer","HTTP_getTransactionsForAddress",page,clock.instant());
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build(); var values=new java.util.LinkedHashMap<String,Object>();
        values.put("version","capture-history-context-v1"); values.put("session",SESSION.toString()); values.put("method","getTransactionsForAddress"); values.put("pool",address(0)); values.put("cursor",""); values.put("pageDigest",PayloadFingerprint.sha256(page)); values.put("receivedAt",clock.instant().toString());
        values.put("sourcePolicy",c.version()); values.put("commitment","finalized"); values.put("encoding","json"); values.put("transactionDetails","full"); values.put("maxSupportedTransactionVersion",1);
        var verified=ledger.historyPage(c,"writer",mapper.writeValueAsString(values));
        values.put("version","capture-history-row-v1"); values.put("rowIndex",0); values.put("rowDigest",PayloadFingerprint.sha256(row)); values.put("signature",SIG); values.put("slot",101);
        return new Source(verified,mapper.writeValueAsString(values));
    }
    @Test void exactFullHistoryLegacyV0V1AndFailuresAvoidIndividualFetch() throws Exception {
        for(var version:List.of("\"legacy\"","0","1")) for(var failed:List.of(false,true)) {
            isolate(); var c=config(); var fetches=new AtomicInteger(); var row=full(SIG,101,version,failed); var run=run(c,fetches,row); var source=source(c,row);
            assertThat(acquire(run,address(0),SIG,101,source.reference(),source.page())).isEqualTo(failed?"FAILED_TRANSACTION":"CAPTURED");
            assertThat(fetches.get()).as("qualified full %s history is already fetched",version).isZero();
            assertThat(saved(SIG).payload()).isEqualTo(row); assertThat(saved(SIG).receivedAt()).isEqualTo(START);
            assertThat(ledger.dispositions(SESSION)).singleElement().satisfies(d->assertThat(d.response()).contains("capture-transaction-source-v2","getTransactionsForAddress",PayloadFingerprint.sha256(row)));
            assertThat(ledger.replay(SESSION).envelopes()).noneMatch(e->e.kind().equals("HTTP_getTransaction"));
        }
    }
    @Test void semanticAlternateHistoryPreservesExactFirstRawButChangedContentConflicts() throws Exception {
        var c=config(); var fetches=new AtomicInteger(); var row=full(SIG,101,"1",false); var run=run(c,fetches,row);
        acquire(run,address(0),SIG,101,"{}",null); var first=saved(SIG);
        var alternate=row.replace("{ \"slot\" : ","{\"slot\":"); var source=source(c,alternate);
        // Independently verified source under the original pool compares full content before reuse.
        acquire(run,address(0),SIG,101,source.reference(),source.page());
        assertThat(fetches.get()).as("alternate full history remains source proof, not a paid fetch").isEqualTo(1); assertThat(saved(SIG)).isEqualTo(first);
        var changed=source(c,row.replace("\"fee\":5000","\"fee\":5001"));
        assertThatThrownBy(()->acquire(run,address(0),SIG,101,changed.reference(),changed.page())).hasMessageContaining("CONFLICT");
        assertThat(saved(SIG)).isEqualTo(first);
    }
    @Test void incompleteHistoryFallsBackAndTamperedReferenceNeverFetches() throws Exception {
        var c=config(); var fetches=new AtomicInteger(); var row=full(SIG,101,"1",false); var run=run(c,fetches,row);
        var incomplete=source(c,"{\"slot\":101,\"transaction\":{\"signatures\":[\""+SIG+"\"]}}");
        acquire(run,address(0),SIG,101,incomplete.reference(),incomplete.page()); assertThat(fetches.get()).isEqualTo(1);
        assertThat(ledger.replay(SESSION).envelopes()).anyMatch(e->e.payload().contains("capture-transaction-source-v2") && e.payload().contains("INCOMPLETE"));
        assertThatThrownBy(()->acquire(run,address(0),SIG,101,incomplete.reference().replace("\"rowIndex\":0","\"rowIndex\":9"),incomplete.page())).hasMessage("RECOVERY_PROVENANCE_INVALID");
        assertThat(fetches.get()).isEqualTo(1);
    }
    @Test void corruptedSuccessfulProofAndSlotContradictionAreTerminalWithoutFetch() throws Exception {
        var c=config(); var fetches=new AtomicInteger(); var run=run(c,fetches,full(SIG,101,"1",false)); acquire(run,address(0),SIG,101,"{}",null);
        assertThatThrownBy(()->acquire(run,address(5),SIG,102,"{}",null)).hasMessageContaining("CONFLICT"); assertThat(fetches.get()).isEqualTo(1);
        // Contradictions are terminal; a second independent corruption needs a fresh owned run.
        isolate(); var secondConfig=config(); var secondFetches=new AtomicInteger(); var secondRun=run(secondConfig,secondFetches,full(SIG,101,"1",false)); acquire(secondRun,address(0),SIG,101,"{}",null);
        jdbc.sql("UPDATE marketdata.capture_dispositions SET raw_digest='sha256:'||repeat('0',64) WHERE session_id=:id").param("id",SESSION).update();
        assertThatThrownBy(()->acquire(secondRun,address(5),SIG,101,"{}",null)).hasMessageContaining("CONFLICT"); assertThat(secondFetches.get()).isEqualTo(1);
    }
    @Test void rawWithoutSuccessfulCaptureProofStillFetchesAndKeepsFirstTimes() throws Exception {
        var c=config(); var fetches=new AtomicInteger(); var row=full(SIG,101,"1",false); var run=run(c,fetches,row);
        storage.store(new RawTransactionPayload(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,SIG),"alchemy",new BlockPosition(ChainId.SOLANA_MAINNET,101),java.util.Optional.empty(),java.util.Optional.empty(),START,START,row,java.util.Optional.of("pumpswap-capture-v1")));
        var first=saved(SIG); clock.set(START.plusSeconds(1)); acquire(run,address(0),SIG,101,"{}",null);
        assertThat(fetches.get()).isEqualTo(1); assertThat(saved(SIG)).isEqualTo(first);
    }
    @Test void cumulativeOverageSplitDeliveryAndClosingNeverRoundEachFragment() {
        var c=reuse("0.0002",1); assertThatCode(()->ledger.open(c,"writer")).as("fresh prospective fractional authority admits conservatively").doesNotThrowAnyException(); var reserved=ledger.reserved(c.authority().accountPeriod());
        ledger.captureWrite(c,"writer",0,1,1,0,0,null,null,null);
        for(var i=0;i<49;i++) ledger.captureWrite(c,"writer",0,1,1,0,0,null,null,null,true,()->{});
        assertThat(jdbc.sql("SELECT known_overage_hundredths FROM marketdata.capture_periods").query(Long.class).single()).as("50 total bytes already fit the single reserved hundredth").isZero();
        ledger.received(SESSION,"writer",1,true);
        assertThat(jdbc.sql("SELECT known_overage_hundredths FROM marketdata.capture_periods").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT reserved_hundredths FROM marketdata.capture_periods").query(Long.class).single()).isEqualTo(reserved);
    }
    @Test void oldAuthorityRejectsNewRateAndSameRatePolicyPreservesReservations() {
        var old=configuration(SESSION,0L); ledger.open(old,"writer"); var reserved=ledger.reserved(old.authority().accountPeriod());
        var a=old.authority(); var authority=new Authority(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),a.ceilingHundredths(),a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),"separate-synthetic-session-authority",a.retentionAllowed(),a.exclusiveAllocation());
        var next=new Configuration(UUID.randomUUID(),old.version(),old.provider(),old.startsAt(),old.pools(),old.limits(),authority);
        var repriced=policy(next,"capture-rpc-reuse-v6",old.authority().accountPeriod(),"0.0002",20000000);
        assertThatThrownBy(()->ledger.open(repriced,"other")).hasMessage("ACCOUNT_PERIOD_RESERVATION_REJECTED"); assertThat(ledger.reserved(old.authority().accountPeriod())).isEqualTo(reserved);
        assertThat(ledger.open(policy(next,"capture-rpc-reuse-v6",old.authority().accountPeriod(),"0.04",20000000),"other")).isTrue();
        assertThat(ledger.reserved(old.authority().accountPeriod())).isEqualTo(reserved*2);
    }
    @Test void provisionalAndUnsupportedHistoryRequireIndependentFinalizedFetch() throws Exception {
        for(var modification:List.of("PROVISIONAL","UNSUPPORTED")) {
            isolate(); var c=config(); var fetches=new AtomicInteger(); var complete=full(SIG,101,"1",false); var run=run(c,fetches,complete);
            var row=modification.equals("PROVISIONAL")?complete.replace("\"version\":1","\"version\":1,\"confirmationStatus\":\"confirmed\""):complete.replace("\"version\":1","\"version\":2");
            var proof=source(c,row); acquire(run,address(0),SIG,101,proof.reference(),proof.page());
            assertThat(fetches.get()).isEqualTo(1); assertThat(saved(SIG).payload()).isEqualTo(complete);
            assertThat(ledger.replay(SESSION).envelopes()).anyMatch(e->e.kind().equals("CAPTURE_TRANSACTION_SOURCE") && e.payload().contains(modification));
        }
    }
    @Test void proofStatusPayloadAndFutureClocksContradictionsNeverFetch() throws Exception {
        for(var mutation:List.of("status","payload","clock","provider")) {
            isolate(); var c=config(); var fetches=new AtomicInteger(); var run=run(c,fetches,full(SIG,101,"1",false)); acquire(run,address(0),SIG,101,"{}",null);
            switch(mutation) {
                case "status" -> jdbc.sql("UPDATE marketdata.capture_dispositions SET status='FAILED_TRANSACTION' WHERE session_id=:id").param("id",SESSION).update();
                case "payload" -> jdbc.sql("UPDATE marketdata.raw_transactions SET payload=replace(payload,'5000','5001')").update();
                case "clock" -> jdbc.sql("UPDATE marketdata.raw_transactions SET received_at=received_at+interval '1 day',admitted_at=admitted_at+interval '1 day',ingested_at=ingested_at+interval '1 day'").update();
                case "provider" -> jdbc.sql("UPDATE marketdata.capture_sessions SET configuration_json=jsonb_set(configuration_json::jsonb,'{provider}','\"other\"')::text WHERE session_id=:id").param("id",SESSION).update();
            }
            assertThatThrownBy(()->acquire(run,address(5),SIG,101,"{}",null)).hasMessageContaining("CONFLICT"); assertThat(fetches.get()).isEqualTo(1);
            assertThat(ledger.dispositions(SESSION)).filteredOn(d->d.pool().equals(address(5))).allSatisfy(d->assertThat(d.rawDigest()).isNull());
        }
    }
    @Test void pendingOrUnavailableProofDoesNotGrantRawReuse() throws Exception {
        for(var status:List.of("PENDING","UNAVAILABLE","UNVERIFIED_FINALITY","FETCH_FAILURE")) {
            isolate(); var c=config(); var fetches=new AtomicInteger(); var run=run(c,fetches,full(SIG,101,"1",false)); acquire(run,address(0),SIG,101,"{}",null);
            jdbc.sql("UPDATE marketdata.capture_dispositions SET status=:status,raw_digest=null WHERE session_id=:id").param("status",status).param("id",SESSION).update();
            acquire(run,address(5),SIG,101,"{\"next\":1}",null); assertThat(fetches.get()).isEqualTo(2);
        }
    }
    @Test void corruptCaptureResponseAndDispositionClocksNeverAuthorizeReuse() throws Exception {
        for(var mutation:List.of("response","clock")) {
            isolate(); var c=config(); var fetches=new AtomicInteger(); var run=run(c,fetches,full(SIG,101,"1",false)); acquire(run,address(0),SIG,101,"{}",null);
            if(mutation.equals("response")) jdbc.sql("UPDATE marketdata.capture_dispositions SET response='{\"fabricated\":true}' WHERE session_id=:id").param("id",SESSION).update();
            else jdbc.sql("UPDATE marketdata.capture_dispositions SET received_at=received_at+interval '1 day',admitted_at=admitted_at+interval '1 day' WHERE session_id=:id").param("id",SESSION).update();
            assertThatThrownBy(()->acquire(run,address(5),SIG,101,"{}",null)).as("successful raw key alone cannot hide corrupt %s proof",mutation).hasMessageContaining("CONFLICT"); assertThat(fetches.get()).isEqualTo(1);
        }
    }
    @Test void matchingResultWithRpcErrorCannotAuthorizeReuse() throws Exception {
        var c=config(); var fetches=new AtomicInteger(); var run=run(c,fetches,full(SIG,101,"1",false)); acquire(run,address(0),SIG,101,"{}",null);
        jdbc.sql("UPDATE marketdata.capture_dispositions SET response=jsonb_set(response::jsonb,'{error}','{\"code\":-32000,\"message\":\"synthetic contradictory error\"}')::text WHERE session_id=:id").param("id",SESSION).update();
        assertThatThrownBy(()->acquire(run,address(5),SIG,101,"{}",null)).as("matching raw result plus RPC error is not a successful provenance anchor").hasMessageContaining("CONFLICT");
        assertThat(fetches.get()).isEqualTo(1);
        assertThat(ledger.dispositions(SESSION)).filteredOn(d->d.pool().equals(address(5))).singleElement().satisfies(d->{assertThat(d.rawDigest()).isNull();assertThat(d.status()).isNotIn("CAPTURED","FAILED_TRANSACTION");});
        assertThat(ledger.boundary(SESSION,address(5))).isEmpty();
    }
    @Test void concurrentConflictBetweenProofAndFencedFinishCannotReturnSuccess() throws Exception {
        var c=config(); var fetches=new AtomicInteger(); var run=run(c,fetches,full(SIG,101,"1",false)); acquire(run,address(0),SIG,101,"{}",null);
        var changed=new java.util.concurrent.atomic.AtomicBoolean();
        assertThatThrownBy(()->evidence.reuse(c,"writer",address(0),SIG,101,"{}",START,null,receipt->{ if(changed.compareAndSet(false,true)) jdbc.sql("UPDATE marketdata.capture_dispositions SET status='CONFLICT' WHERE session_id=:id").param("id",SESSION).update(); })).as("a concurrent terminal conflict cannot be credited from stale pre-fetch proof").hasMessageContaining("CONFLICT");
        assertThat(ledger.dispositions(SESSION)).singleElement().satisfies(d->assertThat(d.status()).isEqualTo("CONFLICT")); assertThat(fetches.get()).isEqualTo(1);
    }
    @Test void finalizedFetchCannotContradictIncomingSlot() throws Exception {
        var c=config(); var fetches=new AtomicInteger(); var run=run(c,fetches,full(SIG,101,"1",false));
        assertThatThrownBy(()->acquire(run,address(0),SIG,102,"{}",null)).as("finalized response slot must match retained incoming identity").hasMessageContaining("CONFLICT");
        assertThat(fetches.get()).isEqualTo(1); assertThat(raw.find(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,SIG),"alchemy")).isEmpty();
    }
    @Test void missingOrWrongFinalizedHistoryContextIsTerminal() throws Exception {
        var c=config(); var fetches=new AtomicInteger(); var row=full(SIG,101,"1",false); var run=run(c,fetches,row); var proof=source(c,row);
        assertThatThrownBy(()->ledger.historyPage(c,"writer",proof.page().context().replace("\"commitment\":\"finalized\"","\"commitment\":\"confirmed\""))).hasMessage("RECOVERY_PROVENANCE_INVALID");
        jdbc.sql("DELETE FROM marketdata.capture_envelopes WHERE kind='CAPTURE_HISTORY_CONTEXT'").update();
        assertThatThrownBy(()->acquire(run,address(0),SIG,101,proof.reference(),proof.page())).isInstanceOf(IllegalStateException.class); assertThat(fetches.get()).isZero(); assertThat(raw.find(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,SIG),"alchemy")).isEmpty();
    }
    @Test void sourceFinishRollbackKeepsCommittedRawThenRetryKeepsFirstTimes() throws Exception {
        var c=config(); var fetches=new AtomicInteger(); run(c,fetches,full(SIG,101,"1",false)); var proof=source(c,full(SIG,101,"1",false));
        ledger.pendingHistory(c,"writer",address(0),SIG,101,proof.reference(),START,proof.page());
        jdbc.sql("CREATE FUNCTION marketdata.fail_reuse_finish() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic source finish failure'; END $$").update();
        jdbc.sql("CREATE TRIGGER fail_reuse_finish BEFORE UPDATE ON marketdata.capture_dispositions FOR EACH ROW EXECUTE FUNCTION marketdata.fail_reuse_finish()").update();
        try { assertThatThrownBy(()->evidence.reuse(c,"writer",address(0),SIG,101,proof.reference(),START,proof.page(),ignored->{})).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally { jdbc.sql("DROP TRIGGER fail_reuse_finish ON marketdata.capture_dispositions").update(); jdbc.sql("DROP FUNCTION marketdata.fail_reuse_finish()").update(); }
        var first=saved(SIG); assertThat(ledger.dispositions(SESSION)).singleElement().satisfies(d->{ assertThat(d.status()).isEqualTo("PENDING"); assertThat(d.rawDigest()).isNull(); });
        assertThat(ledger.replay(SESSION).envelopes()).noneMatch(e->e.kind().equals("CAPTURE_TRANSACTION_SOURCE")); clock.set(START.plusSeconds(1));
        assertThat(evidence.reuse(c,"writer",address(0),SIG,101,proof.reference(),START,proof.page(),ignored->{})).contains("CAPTURED"); assertThat(saved(SIG)).isEqualTo(first); assertThat(fetches.get()).isZero();
    }
    @Test void stolenAndExpiredLeaseCannotCreditNewHistorySource() throws Exception {
        for(var stolen:List.of(false,true)) {
            isolate(); var c=config(); var fetches=new AtomicInteger(); run(c,fetches,full(SIG,101,"1",false)); var proof=source(c,full(SIG,101,"1",false)); ledger.pendingHistory(c,"writer",address(0),SIG,101,proof.reference(),START,proof.page());
            if(stolen) jdbc.sql("UPDATE marketdata.capture_sessions SET holder='other' WHERE session_id=:id").param("id",SESSION).update();
            else jdbc.sql("UPDATE marketdata.capture_sessions SET locked_until=started_at WHERE session_id=:id").param("id",SESSION).update();
            assertThatThrownBy(()->evidence.reuse(c,"writer",address(0),SIG,101,proof.reference(),START,proof.page(),ignored->{})).hasMessage("CAPTURE_LEASE_LOST");
            assertThat(ledger.dispositions(SESSION)).singleElement().satisfies(d->{assertThat(d.status()).isEqualTo("PENDING");assertThat(d.rawDigest()).isNull();}); assertThat(ledger.replay(SESSION).envelopes()).noneMatch(e->e.kind().equals("CAPTURE_TRANSACTION_SOURCE")); assertThat(fetches.get()).isZero();
        }
    }
    @Test void concurrentCrossPoolReusePreservesOneRawAndPerPoolBoundaries() throws Exception {
        var c=config(); var fetches=new AtomicInteger(); var run=run(c,fetches,full(SIG,101,"1",false)); acquire(run,address(0),SIG,101,"{}",null); var first=saved(SIG);
        var failures=new java.util.concurrent.ConcurrentLinkedQueue<Throwable>(); var start=new java.util.concurrent.CountDownLatch(1); var threads=new java.util.ArrayList<Thread>();
        for(var pool:List.of(address(5),address(10))) { ledger.pendingAt(SESSION,"writer",pool,SIG,101,"{\"pool\":\""+pool+"\"}",START); threads.add(Thread.ofVirtual().start(()->{ try { start.await(); assertThat(evidence.reuse(c,"writer",pool,SIG,101,"{}",START,null,ignored->{})).contains("CAPTURED"); } catch(Throwable failure) { failures.add(failure); } })); }
        start.countDown(); for(var thread:threads) { thread.join(java.time.Duration.ofSeconds(10)); assertThat(thread.isAlive()).isFalse(); } assertThat(failures).isEmpty();
        assertThat(ledger.dispositions(SESSION)).hasSize(3); assertThat(saved(SIG)).isEqualTo(first); assertThat(fetches.get()).isEqualTo(1); for(var pool:c.pools()) assertThat(ledger.boundary(SESSION,pool.address())).contains(SIG);
    }
    @Test void exactHistoryRawRunsThroughUnchangedDecoderAndPreservesExplicitStateGaps() throws Exception {
        for(var failed:List.of(false,true)) {
            isolate(); var c=config(); var fetches=new AtomicInteger(); var row=full(SIG,101,"1",failed); var run=run(c,fetches,row); ledger.interrupt(SESSION,"writer","DISCONNECT"); var before=ledger.gaps(SESSION); var proof=source(c,row); acquire(run,address(0),SIG,101,proof.reference(),proof.page());
            var raw=saved(SIG); var source=new io.cryptoresearch.marketdata.api.PumpSwapOperations.RecordedTransaction(SESSION,raw.chain(),SIG,raw.provider(),101,"finalized",failed?"FAILED_TRANSACTION":"CAPTURED",proof.reference(),raw.sourceEventTime(),raw.receivedAt(),raw.admittedAt(),raw.ingestedAt(),raw.payload(),raw.payloadHash());
            var decoded=new DecodePumpSwapOperationsUseCase().decode(new io.cryptoresearch.marketdata.api.PumpSwapOperations.RecordedBatch(List.of(source),List.of("SYNTHETIC")));
            assertThat(decoded.transactions()).singleElement().satisfies(tx->{assertThat(tx.source().rawResult()).isEqualTo(row);assertThat(tx.source().rawDigest()).isEqualTo(raw.payloadHash());assertThat(tx.transactionVersion()).isEqualTo("1");assertThat(tx.status()).isEqualTo(failed?io.cryptoresearch.marketdata.api.PumpSwapOperations.Status.FAILED:io.cryptoresearch.marketdata.api.PumpSwapOperations.Status.NO_PUMPSWAP);}); assertThat(decoded.operations()).isEmpty(); assertThat(ledger.gaps(SESSION)).isEqualTo(before); assertThat(fetches.get()).isZero();
        }
    }
    @TestConfiguration static class Clocks { @Bean @Primary MutableUtcClock captureClock() { return new MutableUtcClock(START); } }
}
