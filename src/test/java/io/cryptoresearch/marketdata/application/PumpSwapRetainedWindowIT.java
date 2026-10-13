package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static io.cryptoresearch.marketdata.application.PumpSwapPoolCaptureTest.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.marketdata.api.PumpSwapRetainedWindow.*;
import io.cryptoresearch.kernel.api.*;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcPumpSwapRetainedWindowStore;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(PumpSwapRetainedWindowIT.Clocks.class)
class PumpSwapRetainedWindowIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MarketDataApi api;
    @Autowired CaptureLedgerUseCase ledger;
    @Autowired StoreRawTransactionUseCase raw;
    @Autowired MutableUtcClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean JdbcPumpSwapRetainedWindowStore store;
    PumpSwapCapture.Configuration c;
    boolean fullRaw;
    @TestConfiguration static class Clocks{@Bean @Primary MutableUtcClock retainedClock(){return new MutableUtcClock(START);}}
    @BeforeEach void prepare(){reset(store);fullRaw=false;jdbc.sql("truncate marketdata.capture_sessions,marketdata.capture_periods,marketdata.raw_transactions cascade").update();clock.set(START);c=configuration(UUID.randomUUID(),0L);assertThat(ledger.open(c,"reader-fixture")).isTrue();clock.set(START.plusSeconds(20));}
    Request request(){return new Request(c.sessionId(),START.plusSeconds(17),START.plusSeconds(471),START,START.plusSeconds(600));}
    String signature(int i){var bytes=new byte[64];java.nio.ByteBuffer.wrap(bytes).position(60).putInt(i+1);var number=new java.math.BigInteger(1,bytes);var encoded=new StringBuilder();var alphabet="123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";while(number.signum()!=0){var parts=number.divideAndRemainder(java.math.BigInteger.valueOf(58));encoded.append(alphabet.charAt(parts[1].intValue()));number=parts[0];}int zero=0;while(zero<bytes.length&&bytes[zero]==0)zero++;return "1".repeat(zero)+encoded.reverse();}
    String payload(int i){if(fullRaw)return CaptureRpcReuseIT.full(signature(i),101+i,"1",i%2!=0);return "{\"slot\":"+(101+i)+",\"blockTime\":null,\"transaction\":{\"signatures\":[\""+signature(i)+"\"],\"message\":{}},\"meta\":{\"err\":"+(i%2==0?"null":"{\"InstructionError\":[0,\"InvalidArgument\"]}")+"}}";}
    void pending(int i,String pool,Instant at){jdbc.sql("insert into marketdata.capture_dispositions(session_id,pool,signature,slot,status,notification,received_at) values(:id,:pool,:sig,:slot,'PENDING',:n,:at)")
        .param("id",c.sessionId()).param("pool",pool).param("sig",signature(i)).param("slot",101+i).param("n","{\"method\":\"logsNotification\",\"params\":{\"subscription\":100,\"result\":{\"context\":{\"slot\":"+(101+i)+"},\"value\":{\"signature\":\""+signature(i)+"\",\"err\":"+(i%2==0?"null":"{}")+"}}}}") .param("at",at.atOffset(ZoneOffset.UTC)).update();}
    void seed(int count){
        var inputs=new ArrayList<RawTransactionPayload>();for(int i=0;i<count;i++)inputs.add(new RawTransactionPayload(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,signature(i)),c.provider(),new BlockPosition(ChainId.SOLANA_MAINNET,101+i),Optional.empty(),Optional.empty(),START.plusSeconds(20),START.plusSeconds(20),payload(i),Optional.of("pumpswap-capture-v1")));
        raw.storeBatch(inputs);for(int i=0;i<count;i++){pending(i,c.pools().getFirst().address(),START.plusSeconds(20));resolve(i);}
    }
    void resolve(int i){jdbc.sql("update marketdata.capture_dispositions set status=:status,raw_digest=:digest,response=:response,admitted_at=:at where session_id=:id and signature=:sig")
        .param("status",i%2==0?"CAPTURED":"FAILED_TRANSACTION").param("digest",PayloadFingerprint.sha256(payload(i))).param("response","{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":"+payload(i)+"}").param("at",START.plusSeconds(20).atOffset(ZoneOffset.UTC)).param("id",c.sessionId()).param("sig",signature(i)).update();}
    Map<String,String> rows(){var result=new TreeMap<String,String>();for(var table:jdbc.sql("select schemaname||'.'||tablename from pg_tables where schemaname in ('marketdata','signal','evaluation') order by schemaname,tablename").query(String.class).list())result.put(table,jdbc.sql("select coalesce(jsonb_agg(x order by x::text)::text,'[]') from (select to_jsonb(t) x from "+table+" t) a").query(String.class).single());return result;}
    @Test void complete129FailedAndUnknownTimeSourcesWithAllBindingsAndPendingRemainUnchanged(){
        seed(129);pending(129,c.pools().getFirst().address(),START.plusSeconds(20));pending(0,c.pools().get(1).address(),START.plusSeconds(500));resolve(0);jdbc.sql("update marketdata.capture_dispositions set admitted_at=received_at where session_id=:id and pool=:pool").param("id",c.sessionId()).param("pool",c.pools().get(1).address()).update();var before=rows();
        var observed=new java.util.concurrent.atomic.AtomicReference<Snapshot>();assertThatCode(()->observed.set(api.retainedPumpSwapWindow(request()))).as("complete129 raw sources plus valid PENDING scope must remain readable").doesNotThrowAnyException();var result=observed.get();assertThat(result.batch().transactions()).hasSize(129);assertThat(result.manifest().distinctSignatures()).isEqualTo(130);assertThat(result.manifest().dispositions()).hasSize(131);
        assertThat(result.batch().transactions().stream().filter(t->t.captureDisposition().equals("FAILED_TRANSACTION"))).hasSize(64);assertThat(result.batch().transactions()).allSatisfy(t->assertThat(t.sourceTime()).isEmpty());
        assertThat(result.manifest().statuses()).containsEntry("PENDING",1L);assertThat(result.manifest().dispositions().stream().filter(d->d.signature().equals(signature(0)))).hasSize(2);
        assertThat(result.request()).isEqualTo(request());assertThat(result.pools()).hasSize(3);assertThat(rows()).isEqualTo(before);verify(store,times(2)).bindings(any(),anyList());verify(store,times(2)).raw(any(),anyList());
        jdbc.sql("update marketdata.capture_sessions set state='STOPPED',reason='OWNER_STOP' where session_id=:id").param("id",c.sessionId()).update();var stopped=rows();assertThat(api.retainedPumpSwapWindow(request()).batch()).isEqualTo(result.batch());assertThat(rows()).isEqualTo(stopped);
    }
    @Test void halfOpenReceivedSelectionIncludesAllBindingsAndKeepsExplicitEmpty(){
        seed(3);jdbc.sql("update marketdata.capture_dispositions set received_at=:at where session_id=:id and signature=:sig").param("id",c.sessionId()).param("sig",signature(0)).param("at",START.plusSeconds(17).atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("update marketdata.capture_dispositions set received_at=:at where session_id=:id and signature=:sig").param("id",c.sessionId()).param("sig",signature(1)).param("at",START.plusSeconds(471).atOffset(ZoneOffset.UTC)).update();
        var result=api.retainedPumpSwapWindow(request());assertThat(result.batch().transactions().stream().map(PumpSwapOperations.RecordedTransaction::signature)).containsExactly(signature(0),signature(2));
        var empty=api.retainedPumpSwapWindow(new Request(c.sessionId(),START.plusSeconds(800),START.plusSeconds(801),START,START.plusSeconds(600)));assertThat(empty.batch().transactions()).isEmpty();assertThat(empty.manifest().distinctSignatures()).isZero();assertThat(empty.manifest().sourceProofBytes()).isZero();
    }
    @Test void rawLinkedPoolAndPendingOtherPoolForOneSignatureRemainCompleteRatherThanRejected(){
        seed(1);pending(0,c.pools().get(1).address(),START.plusSeconds(20));var before=rows();var observed=new java.util.concurrent.atomic.AtomicReference<Snapshot>();
        assertThatCode(()->observed.set(api.retainedPumpSwapWindow(request()))).as("a committed raw proof in one pool does not erase another pool's still-pending binding").doesNotThrowAnyException();
        var result=observed.get();assertThat(result.batch().transactions()).hasSize(1);assertThat(result.manifest().distinctSignatures()).isEqualTo(1);assertThat(result.manifest().dispositions()).hasSize(2);assertThat(result.manifest().statuses()).containsEntry("CAPTURED",1L).containsEntry("PENDING",1L);assertThat(rows()).isEqualTo(before);
    }
    @Test void bounded4097ProbeReportsAtLeastNotFullCountAndNeverHydrates(){
        for(int i=0;i<4100;i++)pending(i,c.pools().getFirst().address(),START.plusSeconds(20));
        var error=catchThrowableOfType(ReadFailure.class,()->api.retainedPumpSwapWindow(request()));assertThat(error.getMessage()).isEqualTo("RETAINED_SIGNATURE_LIMIT");assertThat(error.diagnostic()).isEqualTo(new Diagnostic(Observation.atLeast(4097),Observation.unknown()));verify(store,never()).bindings(any(),anyList());verify(store,never()).raw(any(),anyList());
    }
    @Test void payloadBytesRejectBeforeHydrationWithQualifiedObservation(){
        pending(0,c.pools().getFirst().address(),START.plusSeconds(20));jdbc.sql("update marketdata.capture_dispositions set notification=repeat('x',8000001) where session_id=:id").param("id",c.sessionId()).update();
        var error=catchThrowableOfType(ReadFailure.class,()->api.retainedPumpSwapWindow(request()));assertThat(error.getMessage()).isEqualTo("RETAINED_BYTE_LIMIT");assertThat(error.diagnostic()).isEqualTo(new Diagnostic(Observation.exact(1),Observation.atLeast(8000001)));verify(store,never()).bindings(any(),anyList());
    }
    @Test void wholeCombinedBytesRejectBeforeHydrationWithoutScopeTotalFabrication(){
        for(int i=0;i<9;i++)pending(i,c.pools().getFirst().address(),START.plusSeconds(20));jdbc.sql("update marketdata.capture_dispositions set notification=repeat('x',7999999) where session_id=:id").param("id",c.sessionId()).update();
        var error=catchThrowableOfType(ReadFailure.class,()->api.retainedPumpSwapWindow(request()));assertThat(error.getMessage()).isEqualTo("RETAINED_BYTE_LIMIT");assertThat(error.diagnostic().observedDistinctSignatures()).isEqualTo(Observation.exact(9));assertThat(error.diagnostic().observedSourceProofBytes()).isEqualTo(Observation.atLeast(71999991));verify(store,never()).bindings(any(),anyList());
    }
    @Test void configurationAndGapBoundsFailWithoutPartialResult(){
        for(int i=0;i<1001;i++)jdbc.sql("insert into marketdata.capture_gaps(session_id,pool,kind,reason,opened_at,status) values(:id,:pool,'STATE','TEST',:at,'UNRESOLVED')").param("id",c.sessionId()).param("pool",c.pools().getFirst().address()).param("at",START.atOffset(ZoneOffset.UTC)).update();
        assertThatThrownBy(()->api.retainedPumpSwapWindow(request())).hasMessage("RETAINED_GAP_LIMIT");verify(store,never()).bindings(any(),anyList());
        jdbc.sql("update marketdata.capture_sessions set configuration_json=repeat('x',1000001) where session_id=:id").param("id",c.sessionId()).update();assertThatThrownBy(()->api.retainedPumpSwapWindow(request())).hasMessage("RETAINED_CONFIGURATION_LIMIT");
    }
    @Test void configurationLinkageProofCannotContradictTheEnrolledPoolEvenForEmptyScope(){
        var original=c.pools().getFirst();var wrong=new PumpSwapCapture.Pool(original.address(),original.program(),original.baseVault(),original.quoteVault(),c.pools().get(1).baseMint(),original.quoteMint(),original.baseTokenProgram(),original.quoteTokenProgram(),original.proof());
        var pools=new ArrayList<>(c.pools());pools.set(0,wrong);var changed=new PumpSwapCapture.Configuration(c.sessionId(),c.version(),c.provider(),c.startsAt(),pools,c.limits(),c.authority());var text=JsonMapper.builder().build().writeValueAsString(changed);var digest=new CanonicalFingerprint().field("contract","pumpswap-capture-configuration-v1").field("configuration",text).finish();
        jdbc.sql("update marketdata.capture_sessions set configuration_json=:c,configuration_digest=:d where session_id=:id").param("c",text).param("d",digest).param("id",c.sessionId()).update();var before=rows();
        assertThatThrownBy(()->api.retainedPumpSwapWindow(request())).as("configuration digest does not replace exact pool/linkage proof equality").hasMessage("RETAINED_CONFIGURATION_CONFLICT");assertThat(rows()).isEqualTo(before);
    }
    @Test void contradictoryRawSlotDigestAndOriginalClocksRejectWithoutWrites(){
        seed(1);var before=rows();jdbc.sql("update marketdata.capture_dispositions set raw_digest=:d where session_id=:id").param("id",c.sessionId()).param("d","sha256:"+"f".repeat(64)).update();var corrupt=rows();assertThatThrownBy(()->api.retainedPumpSwapWindow(request())).hasMessage("RETAINED_BINDING_CONFLICT");assertThat(rows()).isEqualTo(corrupt);
        jdbc.sql("update marketdata.capture_dispositions set raw_digest=:d,slot=999 where session_id=:id").param("id",c.sessionId()).param("d",PayloadFingerprint.sha256(payload(0))).update();assertThatThrownBy(()->api.retainedPumpSwapWindow(request())).hasMessage("RETAINED_BINDING_CONFLICT");
        jdbc.sql("update marketdata.capture_dispositions set slot=101 where session_id=:id").param("id",c.sessionId()).update();jdbc.sql("update marketdata.raw_transactions set received_at='2090-01-01',admitted_at='2090-01-01',ingested_at='2090-01-01' where transaction_value=:sig").param("sig",signature(0)).update();assertThatThrownBy(()->api.retainedPumpSwapWindow(request())).hasMessage("RETAINED_RAW_CONFLICT");assertThat(before).isNotEqualTo(rows());
    }
    @Test void concurrentInsertAndPendingResolutionAfterFirstReadCannotAlterSnapshot() throws Exception {
        seed(1);pending(1,c.pools().getFirst().address(),START.plusSeconds(20));var once=new java.util.concurrent.atomic.AtomicBoolean();var orchestrationFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        doAnswer(invocation->{try{var header=invocation.callRealMethod();if(once.compareAndSet(false,true)){
            assertThat(jdbc.sql("show transaction_isolation").query(String.class).single()).isEqualTo("repeatable read");assertThat(jdbc.sql("show transaction_read_only").query(String.class).single()).isEqualTo("on");
            try(var executor=Executors.newSingleThreadExecutor()){executor.submit(()->{
                raw.store(new RawTransactionPayload(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,signature(1)),c.provider(),new BlockPosition(ChainId.SOLANA_MAINNET,102),Optional.empty(),Optional.empty(),START.plusSeconds(20),START.plusSeconds(20),payload(1),Optional.of("pumpswap-capture-v1")));resolve(1);pending(2,c.pools().getFirst().address(),START.plusSeconds(20));
            }).get(2,TimeUnit.SECONDS);}
        }return header;}catch(Throwable failure){orchestrationFailure.set(failure);throw failure;}}).when(store).header(any());
        Snapshot result;try{result=api.retainedPumpSwapWindow(request());}catch(ReadFailure failure){if(orchestrationFailure.get()!=null)throw new AssertionError("real concurrent fixture orchestration failed",orchestrationFailure.get());throw failure;}assertThat(result.manifest().distinctSignatures()).isEqualTo(2);assertThat(result.manifest().statuses()).containsEntry("PENDING",1L).doesNotContainKey("FAILED_TRANSACTION");assertThat(result.manifest().dispositions().stream().map(Disposition::signature)).doesNotContain(signature(2));
        assertThat(api.retainedPumpSwapWindow(request()).manifest().distinctSignatures()).isEqualTo(3);
    }
    @Test void ambientTransactionRejectsWithoutReadingOrLeakingPrivateRows(){
        var tx=new org.springframework.transaction.support.TransactionTemplate(manager);tx.executeWithoutResult(status->{pending(0,c.pools().getFirst().address(),START.plusSeconds(20));assertThatThrownBy(()->api.retainedPumpSwapWindow(request())).hasMessage("RETAINED_REQUIRES_NO_TRANSACTION");verify(store,never()).header(any());status.setRollbackOnly();});assertThat(jdbc.sql("select count(*) from marketdata.capture_dispositions").query(Long.class).single()).isZero();
    }
    Map<String,Object> installHistory(boolean receipt,boolean modern){
        if(modern){jdbc.sql("truncate marketdata.capture_sessions,marketdata.capture_periods,marketdata.raw_transactions cascade").update();clock.set(START);c=CaptureRpcReuseTest.policy(configuration(UUID.randomUUID(),0L),"capture-rpc-reuse-v6","isolated-retained-history","0.0002",20000000);assertThat(ledger.open(c,"reader-fixture")).isTrue();clock.set(START.plusSeconds(20));fullRaw=true;}
        seed(1);var json=JsonMapper.builder().build();var at=START.plusSeconds(20);var pool=c.pools().getFirst().address();var envelope="{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"data\":["+payload(0)+"],\"paginationToken\":null}}";var digest=PayloadFingerprint.sha256(envelope);
        var context=new LinkedHashMap<String,Object>();context.put("version","capture-history-context-v1");context.put("session",c.sessionId().toString());context.put("method","getTransactionsForAddress");context.put("pool",pool);context.put("cursor","");context.put("pageDigest",digest);context.put("receivedAt",at.toString());if(modern){context.put("sourcePolicy",c.version());context.put("commitment","finalized");context.put("encoding","json");context.put("transactionDetails","full");context.put("maxSupportedTransactionVersion",1);}
        var reference=new LinkedHashMap<String,Object>();reference.put("version","capture-history-row-v1");reference.put("session",c.sessionId().toString());reference.put("method","getTransactionsForAddress");reference.put("pool",pool);reference.put("cursor","");reference.put("pageDigest",digest);reference.put("receivedAt",at.toString());reference.put("rowIndex",0);reference.put("rowDigest",PayloadFingerprint.sha256(payload(0)));reference.put("signature",signature(0));reference.put("slot",101);
        for(var entry:Map.of("HTTP_getTransactionsForAddress",envelope,"CAPTURE_HISTORY_CONTEXT",json.writeValueAsString(context)).entrySet())jdbc.sql("insert into marketdata.capture_envelopes(session_id,kind,digest,payload,received_at) values(:id,:kind,:digest,:payload,:at)").param("id",c.sessionId()).param("kind",entry.getKey()).param("digest",PayloadFingerprint.sha256(entry.getValue())).param("payload",entry.getValue()).param("at",at.atOffset(ZoneOffset.UTC)).update();
        var response=new LinkedHashMap<String,Object>();response.put("version","capture-transaction-source-v2");response.put("sourcePolicy",c.version());response.put("session",c.sessionId().toString());response.put("provider",c.provider());response.put("pool",pool);response.put("signature",signature(0));response.put("slot",101);response.put("rawDigest",PayloadFingerprint.sha256(payload(0)));response.put("result","REUSED");response.put("method","getTransactionsForAddress");response.put("reference",reference);response.put("sourceContext",context);response.put("receivedAt",at.toString());
        jdbc.sql("update marketdata.capture_dispositions set notification=:n"+(receipt?",response=:response":"")+" where session_id=:id").param("n",json.writeValueAsString(reference)).param("id",c.sessionId()).param("response",json.writeValueAsString(response)).update();return response;
    }
    @Test void originalHistoryAndV2ReferencesKeepExactRowsAndFirstClocksWithinOneRead(){
        for(boolean modern:List.of(false,true)){prepare();installHistory(modern,modern);var before=rows();var result=api.retainedPumpSwapWindow(request());assertThat(result.batch().transactions()).hasSize(1);assertThat(result.batch().transactions().getFirst().rawResult()).isEqualTo(payload(0));assertThat(result.batch().transactions().getFirst().receivedAt()).isEqualTo(START.plusSeconds(20));assertThat(result.manifest().proofs()).hasSize(2);assertThat(rows()).isEqualTo(before);
            jdbc.sql("update marketdata.capture_dispositions set notification=jsonb_set(notification::jsonb,'{rowDigest}',to_jsonb(cast(:bad as text)))::text where session_id=:id").param("bad","sha256:"+"f".repeat(64)).param("id",c.sessionId()).update();var corrupt=rows();assertThatThrownBy(()->api.retainedPumpSwapWindow(request())).hasMessage("RECOVERY_PROVENANCE_INVALID");assertThat(rows()).isEqualTo(corrupt);
        }
    }
    @Test void modernHistoryContextCannotMasqueradeAsAnotherSourcePolicy(){
        var response=installHistory(true,true);var context=(Map<String,Object>)response.get("sourceContext");context.put("sourcePolicy","capture-v1");var json=JsonMapper.builder().build();var text=json.writeValueAsString(context);
        jdbc.sql("delete from marketdata.capture_envelopes where session_id=:id and kind='CAPTURE_HISTORY_CONTEXT'").param("id",c.sessionId()).update();jdbc.sql("insert into marketdata.capture_envelopes(session_id,kind,digest,payload,received_at) values(:id,'CAPTURE_HISTORY_CONTEXT',:digest,:payload,:at)").param("id",c.sessionId()).param("digest",PayloadFingerprint.sha256(text)).param("payload",text).param("at",START.plusSeconds(20).atOffset(ZoneOffset.UTC)).update();jdbc.sql("update marketdata.capture_dispositions set response=:response where session_id=:id").param("response",json.writeValueAsString(response)).param("id",c.sessionId()).update();var before=rows();
        assertThatThrownBy(()->api.retainedPumpSwapWindow(request())).as("matching raw/reference digests do not replace v6 finalized-full history policy proof").hasMessage("RECOVERY_PROVENANCE_INVALID");assertThat(rows()).isEqualTo(before);
    }
    @Test void perPageProofOverflowIsDetectedBeforeEnvelopePayloadHydration(){
        var response=installHistory(false,false);var reference=(Map<String,Object>)response.get("reference");var pageDigest=(String)reference.get("pageDigest");
        for(int i=0;i<768;i++){var text="{\"pageDigest\":\""+pageDigest+"\",\"extra\":"+i+"}";jdbc.sql("insert into marketdata.capture_envelopes(session_id,kind,digest,payload,received_at) values(:id,'CAPTURE_HISTORY_CONTEXT',:digest,:payload,:at)").param("id",c.sessionId()).param("digest",PayloadFingerprint.sha256(text)).param("payload",text).param("at",START.plusSeconds(20).atOffset(ZoneOffset.UTC)).update();}
        var error=catchThrowableOfType(ReadFailure.class,()->api.retainedPumpSwapWindow(request()));assertThat(error.getMessage()).isEqualTo("RETAINED_PROOF_LIMIT");assertThat(error.diagnostic().observedDistinctSignatures()).isEqualTo(Observation.exact(1));assertThat(error.diagnostic().observedSourceProofBytes().qualification()).isEqualTo(Qualification.AT_LEAST);verify(store,never()).proofs(any(),anyList());
    }
    @Test void actualBlockedPostgresQueryTimesOutWithAbsentObservations() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newSingleThreadExecutor()){
            var holder=executor.submit(()->{new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status->{jdbc.sql("lock table marketdata.capture_dispositions in access exclusive mode").update();entered.countDown();try{release.await(15,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}});});
            try{assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();var start=System.nanoTime();var failure=catchThrowableOfType(ReadFailure.class,()->api.retainedPumpSwapWindow(request()));assertThat(failure.getMessage()).isEqualTo("RETAINED_READ_FAILED_OR_TIMEOUT");assertThat(failure.diagnostic()).isEqualTo(new Diagnostic(Observation.unknown(),Observation.unknown()));assertThat(java.time.Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(12));}
            finally{release.countDown();holder.get(2,TimeUnit.SECONDS);}
        }
    }
    @Test void oversizedStatusAndGapMetadataNeverHydrateTheirUnboundedText(){
        pending(0,c.pools().getFirst().address(),START.plusSeconds(20));jdbc.sql("update marketdata.capture_dispositions set status=repeat('x',131073) where session_id=:id").param("id",c.sessionId()).update();assertThatThrownBy(()->api.retainedPumpSwapWindow(request())).hasMessage("RETAINED_METADATA_LIMIT");verify(store,never()).bindings(any(),anyList());
        jdbc.sql("delete from marketdata.capture_dispositions where session_id=:id").param("id",c.sessionId()).update();jdbc.sql("insert into marketdata.capture_gaps(session_id,pool,kind,reason,opened_at,status) values(:id,:pool,'STATE',repeat('x',8000001),:at,'UNRESOLVED')").param("id",c.sessionId()).param("pool",c.pools().getFirst().address()).param("at",START.atOffset(ZoneOffset.UTC)).update();assertThatThrownBy(()->api.retainedPumpSwapWindow(request())).hasMessage("RETAINED_GAP_LIMIT");
    }
}
