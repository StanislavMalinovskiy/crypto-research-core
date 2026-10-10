package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static io.cryptoresearch.marketdata.application.PumpSwapPoolCaptureTest.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import io.cryptoresearch.kernel.api.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PumpSwapPoolCaptureIT.Clocks.class)
class PumpSwapPoolCaptureIT {
	@Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
	@Autowired CaptureLedgerUseCase ledger;
	@Autowired CaptureEvidenceUseCase capture;
	@Autowired StoreRawTransactionUseCase raw;
	@Autowired RawTransactionStore rawStore;
	@Autowired MutableUtcClock clock;
	@Autowired PumpSwapPoolCollector collector;
	@Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
	@Autowired io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings settings;
	@Autowired io.cryptoresearch.marketdata.api.MarketDataApi api;
	// Controlled internal execution uses the actual PostgreSQL ledger, not provider evidence.
	private Object controlledRun(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c) throws Exception {
		var type=java.util.Arrays.stream(PumpSwapPoolCollector.class.getDeclaredClasses()).filter(t->t.getSimpleName().equals("Run")).findFirst().orElseThrow();
		var constructor=type.getDeclaredConstructors()[0]; constructor.setAccessible(true); return constructor.newInstance(collector,c,"writer");
	}
	private void runField(Object run,String name,Object value) throws Exception { var field=run.getClass().getDeclaredField(name); field.setAccessible(true); field.set(run,value); }
	private void runCall(Object run,String name,Class<?>[] types,Object... args) throws Exception {
		var method=run.getClass().getDeclaredMethod(name,types); method.setAccessible(true);
		try { method.invoke(run,args); } catch(java.lang.reflect.InvocationTargetException failure) { if(failure.getCause() instanceof RuntimeException runtime) throw runtime; throw failure; }
	}
	private io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration provenanceConfiguration() {
		var c=configuration(SESSION,0L); return new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(c.sessionId(),"capture-provenance-v5",c.provider(),c.startsAt(),c.pools(),c.limits(),c.authority());
	}
	@Test void recoveryPageIsRetainedOnceWithCompactExactRowProvenanceWithoutPayloadScans() throws Exception {
		recoveryPageProvenance(13,false);
	}
	@Test void seventeenIdentitiesBeforePinnedBoundaryFitRecoveryWithoutPageAmplification() throws Exception { recoveryPageProvenance(17,true); }
	private void recoveryPageProvenance(int identities,boolean delayed) throws Exception {
		recoveryPageProvenance(identities,delayed,false);
	}
	@Test void repeatedExactHistoryPageAcrossEpisodesKeepsOriginalReceiveTime() throws Exception { recoveryPageProvenance(2,false,true); }
	private void recoveryPageProvenance(int identities,boolean delayed,boolean replay) throws Exception {
		clock.set(START); var c=provenanceConfiguration(); ledger.open(c,"writer"); var boundary="1".repeat(62)+"bA";
		ledger.pending(SESSION,"writer",address(0),boundary,100,"{}"); capture.transaction(SESSION,"writer",address(0),boundary,syntheticV1(boundary,100,false),"{}",START,true);
		var run=controlledRun(c); runField(run,"episode",ledger.episode(SESSION,"writer","DISCONNECT",START)); runField(run,"recoveryDeadline",START.plusSeconds(300)); runField(run,"databaseWriter",Thread.currentThread());
		var signatures=java.util.stream.IntStream.range(0,identities).mapToObj(i->"1".repeat(62)+"c"+"ABCDEFGHJKLMNPQRSTUVWXYZ".charAt(i)).toList(); var rows=new java.util.ArrayList<String>();
		for(var i=0;i<identities;i++) rows.add("{ \"slot\" : "+(200-i)+", \"transaction\":{\"signatures\":[\""+signatures.get(i)+"\"]},\"note\":\"escaped\\n雪\" }"); rows.add("{\"slot\":100,\"transaction\":{\"signatures\":[\""+boundary+"\"]}}");
		var result="{\"data\":["+String.join(",",rows)+"],\"padding\":\""+"x".repeat(delayed?2540000:1000000)+"\",\"paginationToken\":null}"; var page="{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":"+result+"}";
		var transport=org.mockito.Mockito.mock(io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.class);
		org.mockito.Mockito.when(transport.rpc(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation->{ var method=(String)invocation.getArgument(0); if(method.equals("getTransactionsForAddress")) { var observed=replay?clock.instant():START; runCall(run,"evidence",new Class<?>[]{String.class,String.class,java.time.Instant.class},"HTTP_getTransactionsForAddress",page,observed); return new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Response(result,page,observed); } var params=tools.jackson.databind.json.JsonMapper.builder().build().readTree((String)invocation.getArgument(1)); var sig=params.path(0).asText(); return new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Response(syntheticV1(sig,200-signatures.indexOf(sig),false),"{}",START); }); runField(run,"transport",transport);
		var writer=Thread.ofVirtual().start(()->{ try { runCall(run,"writerLoop",new Class<?>[]{}); } catch(Exception failure) { throw new AssertionError(failure); } }); runField(run,"databaseWriter",writer);
		if(delayed) modeledSqlRoundTrips(run,true); org.mockito.Mockito.clearInvocations(captureStore); try { runCall(run,"recover",new Class<?>[]{}); if(replay) { clock.set(START.plusSeconds(1)); runField(run,"episode",ledger.episode(SESSION,"writer","DISCONNECT",clock.instant())); runField(run,"recoveryDeadline",clock.instant().plusSeconds(300)); org.assertj.core.api.Assertions.assertThatCode(()->runCall(run,"recover",new Class<?>[]{})).as("same retained page observed later must retain its original receive time").doesNotThrowAnyException(); assertThat(ledger.gaps(SESSION)).filteredOn(g->g.pool().equals(address(0)) && g.reason().equals("DISCONNECT") && g.kind().equals("TRANSACTION")).hasSize(2).allSatisfy(g->assertThat(g.status()).isEqualTo("RECOVERED_TRANSACTIONS")); } assertThat(clock.instant()).isBefore(START.plusSeconds(300)); } finally { runCall(run,"terminal",new Class<?>[]{String.class},"OWNER_STOP"); writer.join(java.time.Duration.ofSeconds(11)); org.mockito.Mockito.reset(dataSource); assertThat(writer.isAlive()).isFalse(); }
		var notifications=jdbc.sql("SELECT notification FROM marketdata.capture_dispositions WHERE session_id=:id AND signature<>:boundary").param("id",SESSION).param("boundary",boundary).query(String.class).list();
		org.assertj.core.api.SoftAssertions.assertSoftly(softly->{ softly.assertThat(notifications).hasSize(identities); softly.assertThat(notifications.stream().map(n->n.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).toList()).allSatisfy(length->softly.assertThat(length).as("row reference must not copy the full history page").isLessThan(2048)); softly.assertThat(org.mockito.Mockito.mockingDetails(captureStore).getInvocations().stream().filter(i->i.getMethod().getName().equals("dispositions")).count()).as("recovery needs page keys, not successful notification/response payloads").isZero(); });
		for(var i=0;i<identities;i++) { var ref=tools.jackson.databind.json.JsonMapper.builder().build().readTree(notifications.get(i)); assertThat(ref.path("version").asText()).isEqualTo("capture-history-row-v1"); var index=ref.path("rowIndex").intValue(); assertThat(ref.path("rowDigest").asText()).isEqualTo(PayloadFingerprint.sha256(rows.get(index))); assertThat(ref.path("pageDigest").asText()).isEqualTo(PayloadFingerprint.sha256(page)); }
		assertThat(ledger.replay(SESSION).envelopes()).filteredOn(e->e.kind().equals("HTTP_getTransactionsForAddress")).singleElement().satisfies(e->{ assertThat(e.payload()).isEqualTo(page); assertThat(e.receivedAt()).isEqualTo(START); }); assertThat(ledger.gaps(SESSION)).filteredOn(g->g.pool().equals(address(0)) && g.kind().equals("TRANSACTION") && g.reason().equals("DISCONNECT")).hasSize(replay?2:1).allSatisfy(g->assertThat(g.status()).isEqualTo("RECOVERED_TRANSACTIONS"));
	}
	private java.util.concurrent.atomic.AtomicInteger modeledSqlRoundTrips(Object run,boolean actualOneDelay) throws Exception {
		var calls=new java.util.concurrent.atomic.AtomicInteger();
		org.mockito.Mockito.doAnswer(invocation->{ var connection=(java.sql.Connection)invocation.callRealMethod(); return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Connection.class},(proxy,method,args)->{
			try { var value=method.invoke(connection,args); if(value instanceof java.sql.PreparedStatement statement) return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.PreparedStatement.class},(p,m,a)->{ try { var owned=run instanceof java.util.concurrent.atomic.AtomicReference<?> holder?holder.get():run; var observer=run instanceof java.util.concurrent.atomic.AtomicReference<?> && owned!=null && !Thread.currentThread().getName().startsWith("pumpswap"); if(m.getName().startsWith("execute") && !observer) { if(calls.incrementAndGet()==1 && actualOneDelay) Thread.sleep(500); synchronized(clock) { clock.set(clock.instant().plusMillis(500)); } if(owned!=null) { var deadline=owned.getClass().getDeclaredField("drainDeadline"); deadline.setAccessible(true); var d=deadline.getLong(owned); if(d!=Long.MAX_VALUE) { System.out.println("SOAK_CLOSING_SQL call="+calls.get()+" actor="+Thread.currentThread().getName()+" clock="+clock.instant()+" remainingMillis="+java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(d-System.nanoTime())+" operation="+StackWalker.getInstance().walk(frames->frames.filter(f->f.getClassName().endsWith("JdbcCaptureStore")).map(f->f.getMethodName()).findFirst().orElse("OTHER"))); deadline.setLong(owned,d-java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(500)); } } } return m.invoke(statement,a); } catch(java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); } }); return value; } catch(java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
		}); }).when(dataSource).getConnection(); return calls;
	}
	@Test void fourteenAdmittedMixedTailItemsCloseAtomicallyWithinOriginalDrainWithHalfSecondSql() throws Exception {
		clock.set(START); var c=provenanceConfiguration(); ledger.open(c,"writer"); var run=controlledRun(c);
		for(var i=0;i<14;i++) { var n=i%3==0?new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(address(0),null,"1".repeat(62)+"d"+"ABCDEFGHJKLMNP".charAt(i),104+i,"","{\"notification\":"+i+"}",START):new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(address(0),address(1),null,104+i,"{\"data\":[\"AQ==\",\"base64\"]}","{\"notification\":"+i+"}",START); runCall(run,"enqueue",new Class<?>[]{n.getClass()},n); }
		runField(run,"requests",3L); runField(run,"rpc",4000L); runField(run,"received",71L); runField(run,"subscription",71L); runField(run,"writerClosing",true); runField(run,"drainDeadline",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11)); var field=run.getClass().getDeclaredField("interruption"); field.setAccessible(true); ((java.util.concurrent.atomic.AtomicReference<String>)field.get(run)).set("OWNER_STOP");
		var sql=modeledSqlRoundTrips(run,true); runCall(run,"writerLoop",new Class<?>[]{}); var trips=sql.get(); org.mockito.Mockito.reset(dataSource); var status=ledger.status(SESSION);
		assertThat(status.state()).as("14 admitted items must fit original11s, not per-item fence/config/SQL amplification").isEqualTo("STOPPED"); assertThat(status.reason()).isEqualTo("OWNER_STOP"); assertThat(status.states()).isEqualTo(9); assertThat(ledger.dispositions(SESSION)).hasSize(5); assertThat(status.requests()).isEqualTo(3); assertThat(status.rpcHundredths()).isEqualTo(4000); assertThat(status.receivedBytes()).isEqualTo(71); assertThat(trips).isLessThanOrEqualTo(20); assertThat(ledger.states(SESSION)).allSatisfy(s->assertThat(s.receivedAt()).isEqualTo(START));
	}
	@Test void pendingRetryPreservesOpaqueLegacyNotificationWithoutSuccessfulPayloadReads() throws Exception {
		legacyPendingRetry("opaque legacy notification");
	}
	@Test void pendingRetryDoesNotInterpretPlainLegacyMarkerTextAsReference() throws Exception { legacyPendingRetry("old opaque capture-history-row-v1 notification with pageDigest text"); }
	@Test void pendingRetryDoesNotInterpretNestedLegacyLogMarkerAsReference() throws Exception { legacyPendingRetry("{\"params\":{\"result\":{\"logs\":[\"capture-history-row-v1\",\"\\\"pageDigest\\\"\"]}}}"); }
	private void legacyPendingRetry(String notification) throws Exception {
		clock.set(START); var c=provenanceConfiguration(); ledger.open(c,"writer"); var sig="1".repeat(62)+"eA"; ledger.pending(SESSION,"writer",address(0),sig,123,notification); var run=controlledRun(c);
		var transport=org.mockito.Mockito.mock(io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.class); org.mockito.Mockito.when(transport.rpc(org.mockito.ArgumentMatchers.eq("getTransaction"),org.mockito.ArgumentMatchers.anyString())).thenReturn(new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Response(syntheticV1(sig,123,false),"{}",START)); runField(run,"transport",transport);
		var writer=Thread.ofVirtual().start(()->{ try { runCall(run,"writerLoop",new Class<?>[]{}); } catch(Exception failure) { throw new AssertionError(failure); } }); runField(run,"databaseWriter",writer); org.mockito.Mockito.clearInvocations(captureStore);
		try { org.assertj.core.api.Assertions.assertThatCode(()->runCall(run,"retryPending",new Class<?>[]{})).as("opaque legacy notification needs no new JSON provenance format").doesNotThrowAnyException(); } finally { runCall(run,"terminal",new Class<?>[]{String.class},"OWNER_STOP"); writer.join(java.time.Duration.ofSeconds(11)); }
		org.mockito.Mockito.verify(captureStore,org.mockito.Mockito.never()).dispositions(SESSION); assertThat(ledger.dispositions(SESSION)).singleElement().satisfies(d->{ assertThat(d.status()).isEqualTo("CAPTURED"); assertThat(d.notification()).isEqualTo(notification); });
	}
	private java.util.Map<String,Object> historyContext(String page,java.time.Instant received) { var fields=new java.util.LinkedHashMap<String,Object>(); fields.put("version","capture-history-context-v1"); fields.put("session",SESSION.toString()); fields.put("method","getTransactionsForAddress"); fields.put("pool",address(0)); fields.put("cursor",""); fields.put("pageDigest",PayloadFingerprint.sha256(page)); fields.put("receivedAt",received.truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString()); return fields; }
	private java.util.Map<String,Object> historyReference(String page,String row,String sig,long slot,java.time.Instant received) { var ref=new java.util.LinkedHashMap<>(historyContext(page,received)); ref.put("version","capture-history-row-v1"); ref.put("rowIndex",0); ref.put("rowDigest",PayloadFingerprint.sha256(row)); ref.put("signature",sig); ref.put("slot",slot); return ref; }
	@Test void pendingRetryResolvesSamePageInTwoPoolContextsWithoutPayloadAliasing() throws Exception {
		clock.set(START); var c=provenanceConfiguration(); ledger.open(c,"writer"); var sig="1".repeat(62)+"fF"; var row="{\"slot\":123,\"transaction\":{\"signatures\":[\""+sig+"\"]}}"; var page="{\"result\":{\"data\":["+row+"]}}"; var mapper=tools.jackson.databind.json.JsonMapper.builder().build(); ledger.envelopeAt(SESSION,"writer","HTTP_getTransactionsForAddress",page,START);
		for(var pool:java.util.List.of(c.pools().get(0).address(),c.pools().get(1).address())) { var context=historyContext(page,START); context.put("pool",pool); var proof=ledger.historyPage(c,"writer",mapper.writeValueAsString(context)); var ref=historyReference(page,row,sig,123,START); ref.put("pool",pool); ledger.pendingHistory(c,"writer",pool,sig,123,mapper.writeValueAsString(ref),START,proof); }
		var run=controlledRun(c); var transport=org.mockito.Mockito.mock(io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.class); org.mockito.Mockito.when(transport.rpc(org.mockito.ArgumentMatchers.eq("getTransaction"),org.mockito.ArgumentMatchers.anyString())).thenReturn(new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Response(syntheticV1(sig,123,false),"{}",START)); runField(run,"transport",transport); var writer=Thread.ofVirtual().start(()->{ try { runCall(run,"writerLoop",new Class<?>[]{}); } catch(Exception failure) { throw new AssertionError(failure); } }); runField(run,"databaseWriter",writer);
		org.mockito.Mockito.clearInvocations(captureStore); try { org.assertj.core.api.Assertions.assertThatCode(()->runCall(run,"retryPending",new Class<?>[]{})).as("same exact page has two valid pool contexts").doesNotThrowAnyException(); org.mockito.Mockito.verify(captureStore,org.mockito.Mockito.times(1)).historyPage(SESSION,PayloadFingerprint.sha256(page)); for(var pool:java.util.List.of(c.pools().get(0).address(),c.pools().get(1).address())) org.mockito.Mockito.verify(captureStore).historyContext(SESSION,PayloadFingerprint.sha256(page),pool,""); } finally { runCall(run,"terminal",new Class<?>[]{String.class},"OWNER_STOP"); writer.join(java.time.Duration.ofSeconds(11)); }
		assertThat(ledger.dispositions(SESSION)).hasSize(2).allSatisfy(d->assertThat(d.status()).isEqualTo("CAPTURED")); assertThat(ledger.replay(SESSION).envelopes()).filteredOn(e->e.kind().equals("HTTP_getTransactionsForAddress")).hasSize(1); org.mockito.Mockito.verify(transport,org.mockito.Mockito.times(2)).rpc(org.mockito.ArgumentMatchers.eq("getTransaction"),org.mockito.ArgumentMatchers.anyString());
	}
	@Test void historyReferencesVerifyExactSpanSessionKindIdentityAndNormalizedFirstTime() {
		clock.set(START.plusSeconds(1)); var c=provenanceConfiguration(); ledger.open(c,"writer"); var sig="1".repeat(62)+"fA"; var received=START.plusNanos(123456789); var row="{ \"slot\" : 123, \"transaction\":{\"signatures\":[\""+sig+"\"]}, \"note\":\"雪\\n\\\"quoted\\\"\" }"; var page="{\"result\":{\"data\":["+row+"]}}"; var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
		ledger.envelopeAt(SESSION,"writer","HTTP_getTransactionsForAddress",page,received); var proof=ledger.historyPage(c,"writer",mapper.writeValueAsString(historyContext(page,received))); var ref=historyReference(page,row,sig,123,received);
		ledger.pendingHistory(c,"writer",address(0),sig,123,mapper.writeValueAsString(ref),received,proof); assertThat(ledger.dispositions(SESSION)).singleElement().satisfies(d->assertThat(d.receivedAt()).isEqualTo(received.truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
		for(var field:java.util.Map.<String,Object>ofEntries(java.util.Map.entry("version","capture-history-row-v9"),java.util.Map.entry("session",UUID.randomUUID().toString()),java.util.Map.entry("method","getTransaction"),java.util.Map.entry("pool",address(5)),java.util.Map.entry("cursor","wrong"),java.util.Map.entry("pageDigest","bad"),java.util.Map.entry("rowIndex",100),java.util.Map.entry("rowDigest","bad"),java.util.Map.entry("signature","1".repeat(62)+"fB"),java.util.Map.entry("slot",124),java.util.Map.entry("receivedAt",START.toString())).entrySet()) {
			var bad=new java.util.LinkedHashMap<>(ref); bad.put(field.getKey(),field.getValue()); org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.pendingHistory(c,"writer",address(0),sig,123,mapper.writeValueAsString(bad),received,proof)).as(field.getKey()).hasMessage("RECOVERY_PROVENANCE_INVALID");
		}
		var missing=historyContext(page,received); missing.put("pageDigest","sha256:"+"0".repeat(64)); org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.historyPage(c,"writer",mapper.writeValueAsString(missing))).hasMessage("RECOVERY_PROVENANCE_MISSING");
		var other=historyContext(page,received); other.put("session",UUID.randomUUID().toString()); org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.historyPage(c,"writer",mapper.writeValueAsString(other))).hasMessage("RECOVERY_PROVENANCE_INVALID");
		var wrongPage="{\"result\":{\"data\":["+row+"]},\"wrongKind\":true}"; ledger.envelopeAt(SESSION,"writer","HTTP_getTransaction",wrongPage,received); org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.historyPage(c,"writer",mapper.writeValueAsString(historyContext(wrongPage,received)))).hasMessage("RECOVERY_PROVENANCE_MISSING");
		jdbc.sql("UPDATE marketdata.capture_envelopes SET payload='{}' WHERE session_id=:id AND kind='HTTP_getTransactionsForAddress'").param("id",SESSION).update(); org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.historyPage(c,"writer",mapper.writeValueAsString(historyContext(page,received)))).hasMessage("RECOVERY_PROVENANCE_INVALID"); assertThat(ledger.dispositions(SESSION)).hasSize(1);
	}
	@Test void historyReferenceLargeIntegerCannotWrapIntoValidIndexOrSlot() {
		clock.set(START); var c=provenanceConfiguration(); ledger.open(c,"writer"); var sig="1".repeat(62)+"fC"; var row="{\"slot\":123,\"transaction\":{\"signatures\":[\""+sig+"\"]}}"; var page="{\"result\":{\"data\":["+row+"]}}"; var mapper=tools.jackson.databind.json.JsonMapper.builder().build(); ledger.envelopeAt(SESSION,"writer","HTTP_getTransactionsForAddress",page,START); var proof=ledger.historyPage(c,"writer",mapper.writeValueAsString(historyContext(page,START))); var ref=historyReference(page,row,sig,123,START);
		org.assertj.core.api.SoftAssertions.assertSoftly(softly->{ for(var field:java.util.Map.of("rowIndex",new java.math.BigInteger("18446744073709551616"),"slot",new java.math.BigInteger("18446744073709551739")).entrySet()) { var bad=new java.util.LinkedHashMap<>(ref); bad.put(field.getKey(),field.getValue()); softly.assertThatThrownBy(()->ledger.pendingHistory(c,"writer",address(0),sig,123,mapper.writeValueAsString(bad),START,proof)).as("overflow "+field.getKey()+" cannot alias original identity").hasMessage("RECOVERY_PROVENANCE_INVALID"); } });
		assertThat(ledger.dispositions(SESSION)).isEmpty();
	}
	@Test void historyProvenanceRejectsLiteralPathAliasButAcceptsActualExactRow() {
		clock.set(START); var c=provenanceConfiguration(); ledger.open(c,"writer"); var sig="1".repeat(62)+"fD"; var real="{ \"slot\":123,\"transaction\":{\"signatures\":[\""+sig+"\"]},\"text\":\"雪\\n\" }"; var fake="{\"slot\":123,\"transaction\":{\"signatures\":[\""+sig+"\"]},\"text\":\"not the actual array row\"}"; var page="{\"result/data\":{\"0\":"+fake+"},\"result\":{\"data\":["+real+"]}}"; var mapper=tools.jackson.databind.json.JsonMapper.builder().build(); ledger.envelopeAt(SESSION,"writer","HTTP_getTransactionsForAddress",page,START); var proof=ledger.historyPage(c,"writer",mapper.writeValueAsString(historyContext(page,START)));
		org.assertj.core.api.SoftAssertions.assertSoftly(softly->{ softly.assertThatThrownBy(()->ledger.pendingHistory(c,"writer",address(0),sig,123,mapper.writeValueAsString(historyReference(page,fake,sig,123,START)),START,proof)).hasMessage("RECOVERY_PROVENANCE_INVALID"); softly.assertThatCode(()->ledger.pendingHistory(c,"writer",address(0),sig,123,mapper.writeValueAsString(historyReference(page,real,sig,123,START)),START,proof)).doesNotThrowAnyException(); });
	}
	@Test void historyContextAndReferenceRequireExplicitTextualEmptyCursor() {
		clock.set(START); var c=provenanceConfiguration(); ledger.open(c,"writer"); var sig="1".repeat(62)+"fG"; var row="{\"slot\":123,\"transaction\":{\"signatures\":[\""+sig+"\"]}}"; var page="{\"result\":{\"data\":["+row+"]}}"; var mapper=tools.jackson.databind.json.JsonMapper.builder().build(); ledger.envelopeAt(SESSION,"writer","HTTP_getTransactionsForAddress",page,START); var proof=ledger.historyPage(c,"writer",mapper.writeValueAsString(historyContext(page,START)));
		org.assertj.core.api.SoftAssertions.assertSoftly(softly->{ for(var missing:java.util.List.of(true,false)) { var context=historyContext(page,START); var ref=historyReference(page,row,sig,123,START); if(missing) { context.remove("cursor"); ref.remove("cursor"); } else { context.put("cursor",null); ref.put("cursor",null); } softly.assertThatThrownBy(()->ledger.historyPage(c,"writer",mapper.writeValueAsString(context))).as("context cursor "+(missing?"missing":"null")).hasMessage("RECOVERY_PROVENANCE_INVALID"); softly.assertThatThrownBy(()->ledger.pendingHistory(c,"writer",address(0),sig,123,mapper.writeValueAsString(ref),START,proof)).as("row reference cursor "+(missing?"missing":"null")).hasMessage("RECOVERY_PROVENANCE_INVALID"); } });
		assertThat(ledger.dispositions(SESSION)).isEmpty();
	}
	@Test void pendingRetryRejectsDeclaredWrongVersionMissingFieldsAndMalformedReferenceBeforeRpc() throws Exception {
		clock.set(START); var c=provenanceConfiguration(); ledger.open(c,"writer"); var sig="1".repeat(62)+"fE"; var row="{\"slot\":123,\"transaction\":{\"signatures\":[\""+sig+"\"]}}"; var page="{\"result\":{\"data\":["+row+"]}}"; var mapper=tools.jackson.databind.json.JsonMapper.builder().build(); ledger.envelopeAt(SESSION,"writer","HTTP_getTransactionsForAddress",page,START); ledger.historyPage(c,"writer",mapper.writeValueAsString(historyContext(page,START))); ledger.pending(SESSION,"writer",address(0),sig,123,"{}");
		var run=controlledRun(c); var transport=org.mockito.Mockito.mock(io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.class); runField(run,"transport",transport);
		var variants=new java.util.ArrayList<String>(); for(var field:java.util.List.of("version","rowDigest","receivedAt")) { var ref=historyReference(page,row,sig,123,START); if(field.equals("version")) ref.put("version","wrong-version"); else ref.remove(field); variants.add(mapper.writeValueAsString(ref)); } var absent=historyReference(page,row,sig,123,START); absent.remove("version"); variants.add(mapper.writeValueAsString(absent)); variants.add("{\"version\":\"capture-history-row-v1\"");
		for(var reference:variants) { jdbc.sql("UPDATE marketdata.capture_dispositions SET notification=:text WHERE session_id=:id").param("text",reference).param("id",SESSION).update(); org.assertj.core.api.Assertions.assertThatThrownBy(()->runCall(run,"retryPending",new Class<?>[]{})).hasMessage("RECOVERY_PROVENANCE_INVALID"); }
		org.mockito.Mockito.verify(transport,org.mockito.Mockito.never()).rpc(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()); assertThat(ledger.dispositions(SESSION)).singleElement().satisfies(d->assertThat(d.status()).isEqualTo("PENDING"));
	}
	private java.util.List<io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification> mixedBatch() { return java.util.List.of(new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(address(0),address(1),null,123,"{\"data\":[\"AQ==\",\"base64\"]}","{\"account\":1}",START),new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(address(0),null,"1".repeat(62)+"gA",124,"","{\"transaction\":1}",START)); }
	@Test void admittedTailBatchRollsBackCountersAndRowsOnMidBatchFailure() {
		clock.set(START); var c=provenanceConfiguration(); ledger.open(c,"writer"); org.mockito.Mockito.doAnswer(invocation->{ invocation.callRealMethod(); throw new IllegalStateException("CONTROLLED_BATCH_FAILURE"); }).when(captureStore).tail(org.mockito.ArgumentMatchers.eq(SESSION),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
		org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.closingTail(c,"writer",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11),mixedBatch(),3,77,11,99,4000,()->{})).hasMessage("CONTROLLED_BATCH_FAILURE"); assertThat(ledger.status(SESSION).requests()).isZero(); assertThat(ledger.status(SESSION).receivedBytes()).isZero(); assertThat(ledger.status(SESSION).states()).isZero(); assertThat(ledger.dispositions(SESSION)).isEmpty(); assertThat(ledger.status(SESSION).state()).isEqualTo("RUNNING"); assertThat(ledger.reserved(c.authority().accountPeriod())).isEqualTo(95000000);
	}
	@Test void admittedTailBatchRejectsConcurrentImmutableConflictBeforeMembershipCommit() throws Exception {
		clock.set(START); var c=provenanceConfiguration(); ledger.open(c,"writer"); var rows=new java.util.ArrayList<>(mixedBatch()); var original=rows.getFirst(); var row=new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(original.pool(),original.account(),original.signature(),99123,original.payload(),original.envelope(),original.receivedAt()); rows.set(0,row); var injected=new java.util.concurrent.atomic.AtomicBoolean();
		try(var concurrent=dataSource.getConnection()) {
			org.mockito.Mockito.doAnswer(invocation->{ var connection=(java.sql.Connection)invocation.callRealMethod(); return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Connection.class},(proxy,method,args)->{ try { var value=method.invoke(connection,args); if(method.getName().equals("prepareStatement") && ((String)args[0]).startsWith("INSERT INTO marketdata.raw_account_snapshots") && value instanceof java.sql.PreparedStatement statement) return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.PreparedStatement.class},(p,m,a)->{ try { if(m.getName().equals("executeUpdate") && injected.compareAndSet(false,true)) try(var insert=concurrent.prepareStatement("INSERT INTO marketdata.raw_account_snapshots VALUES ('solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp',?,?,?,?,?,?,?,?,?,?)")) { insert.setString(1,c.provider()); insert.setString(2,row.account()); insert.setLong(3,row.slot()); insert.setString(4,PayloadFingerprint.sha256(row.payload())); insert.setString(5,"base64"); insert.setString(6,"{\"different\":true}"); insert.setString(7,"{}"); for(var i=8;i<=10;i++) insert.setObject(i,START.atOffset(java.time.ZoneOffset.UTC)); insert.executeUpdate(); } return m.invoke(statement,a); } catch(java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); } }); return value; } catch(java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); } }); }).when(dataSource).getConnection();
			try { org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.closingTail(c,"writer",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11),rows,3,77,11,99,4000,()->{})).hasMessage("ACCOUNT_CONTENT_CONFLICT"); } finally { org.mockito.Mockito.reset(dataSource); }
		}
		assertThat(injected.get()).isTrue(); assertThat(ledger.status(SESSION).states()).isZero(); assertThat(ledger.status(SESSION).requests()).isZero(); assertThat(ledger.dispositions(SESSION)).isEmpty();
	}
	@Test void admittedTailBatchRetainsEqualityAmbiguityAndRejectsCapOrLostFence() {
		clock.set(START.plusSeconds(1)); var c=provenanceConfiguration(); ledger.open(c,"writer"); var rows=new java.util.ArrayList<>(mixedBatch()); rows.add(rows.getFirst()); rows.add(new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(address(0),address(1),null,123,"{\"data\":[\"Ag==\",\"base64\"]}","{\"account\":2}",START.plusNanos(123456789)));
		ledger.closingTail(c,"writer",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11),rows,3,77,11,99,4000,()->{}); assertThat(ledger.states(SESSION)).hasSize(2).allSatisfy(s->assertThat(s.ambiguous()).isTrue()); assertThat(jdbc.sql("SELECT sum(duplicates) FROM marketdata.capture_state_members WHERE session_id=:id").param("id",SESSION).query(Long.class).single()).isEqualTo(1); assertThat(ledger.dispositions(SESSION)).hasSize(1);
		var before=ledger.status(SESSION); org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.closingTail(c,"writer",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11),mixedBatch(),c.limits().requests(),77,11,99,4000,()->{})).hasMessage("CAPTURE_RESOURCE_LIMIT"); assertThat(ledger.status(SESSION).requests()).isEqualTo(before.requests()); assertThat(ledger.status(SESSION).receivedBytes()).isEqualTo(before.receivedBytes());
		jdbc.sql("UPDATE marketdata.capture_sessions SET holder='stolen' WHERE session_id=:id").param("id",SESSION).update(); org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.closingTail(c,"writer",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11),mixedBatch(),0,0,0,0,0,()->{})).hasMessage("CAPTURE_LEASE_LOST"); assertThat(ledger.status(SESSION).state()).isEqualTo("RUNNING");
	}
	private void awaitLocal(java.util.function.BooleanSupplier condition,String description) throws Exception { var deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(60); while(!condition.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(20); assertThat(condition.getAsBoolean()).as(description).isTrue(); }
	private String soakSignature(int connection,int kind) { return "1".repeat(61)+"s"+"123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".charAt(connection)+"ABCDEFGHJKLMNPQRSTUVWXYZ".charAt(kind); }
	private void advanceWithActualLease(java.time.Instant target) throws Exception {
		var deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
		while(clock.instant().isBefore(target)) {
			if(System.nanoTime()>=deadline) {
				var status=ledger.status(SESSION); System.err.println("SOAK_ADVANCE_DIAGNOSTIC target="+target+" clock="+clock.instant()+" lease="+jdbc.sql("SELECT locked_until FROM marketdata.capture_sessions WHERE session_id=:id").param("id",SESSION).query(java.time.OffsetDateTime.class).single()+" holder="+jdbc.sql("SELECT holder FROM marketdata.capture_sessions WHERE session_id=:id").param("id",SESSION).query(String.class).single()+" state="+status.state()+" reason="+status.reason()+" requests="+status.requests()+" transactions="+status.transactions()+" states="+status.states()+" gaps="+status.gaps().size()+" recovered="+status.gaps().stream().filter(g->g.status().equals("RECOVERED_TRANSACTIONS")).count());
				var active=PumpSwapPoolCollector.class.getDeclaredField("active"); active.setAccessible(true); var run=((java.util.concurrent.atomic.AtomicReference<?>)active.get(collector)).get(); if(run!=null) { for(var name:java.util.List.of("interruption","stopped","writerFailed","writerDone","coordinatorDone","worker","databaseWriter")) { var field=run.getClass().getDeclaredField(name); field.setAccessible(true); var value=field.get(run); System.err.println("SOAK_ACTOR "+name+"="+value); if(value instanceof Thread actor) { System.err.println("SOAK_ACTOR_STATE "+actor.getState()); java.util.Arrays.stream(actor.getStackTrace()).limit(12).forEach(frame->System.err.println("SOAK_STACK "+frame)); } } }
			}
			assertThat(System.nanoTime()).as("bounded controlled-clock advance requires an actual renewable live lease").isLessThan(deadline);
			var until=jdbc.sql("SELECT locked_until FROM marketdata.capture_sessions WHERE session_id=:id").param("id",SESSION).query(java.time.OffsetDateTime.class).single().toInstant();
			boolean advanced=false;
			synchronized(clock) { var now=clock.instant(); var step=Math.min(15000,java.time.Duration.between(now,until).toMillis()-2000); if(step>=1000) { var next=now.plusMillis(step); if(next.isAfter(target)) next=target; if(next.isBefore(until)) { clock.set(next); advanced=true; } } }
			Thread.sleep(advanced?20:50);
		}
	}
	@Test void localSyntheticSixHourCollectorSoakRecoversEighteenDisconnectsAndClosesAdmittedTail() throws Exception {
		clock.set(START); var source=provenanceConfiguration(); var l=source.limits(); var c=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(SESSION,source.version(),source.provider(),START,source.pools(),new io.cryptoresearch.marketdata.api.PumpSwapCapture.Limits(21600,3750,500000000,600000000,10000000,7500000,l.messageBytes(),l.responseBytes(),l.queueItems(),l.queueBytes(),10,2,20,3,300,2,30),source.authority());
		var owned=new java.util.concurrent.atomic.AtomicReference<Object>(); var sql=modeledSqlRoundTrips(owned,true); var connected=new java.util.concurrent.atomic.AtomicInteger(-1); var pages=new java.util.concurrent.atomic.AtomicInteger(); var requests=new java.util.concurrent.atomic.AtomicInteger(); var finalFetch=new java.util.concurrent.CountDownLatch(1); var finishFetch=new java.util.concurrent.CountDownLatch(1); var payloads=new java.util.concurrent.ConcurrentHashMap<String,String>();
		for(var i=0;i<20;i++) { payloads.put(soakSignature(i,0),syntheticV1(soakSignature(i,0),101+i*10,false)); for(var p=0;p<2;p++) payloads.put(soakSignature(i,p+1),syntheticV1(soakSignature(i,p+1),104+i*10-p,p==1)); }
		var http=mockAccounts(c,soakSignature(0,0),false,null,null,(method,result)->result,200,(request,body)->{
			requests.incrementAndGet(); var method=request.path("method").asText(); if(method.equals("getTransaction")) { var sig=request.path("params").path(0).asText(); if(sig.equals(soakSignature(19,0))) { finalFetch.countDown(); try { if(!finishFetch.await(8,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("final admitted fetch not released"); } catch(InterruptedException failure) { throw new AssertionError(failure); } } return "{\"jsonrpc\":\"2.0\",\"id\":"+request.path("id").longValue()+",\"result\":"+payloads.get(sig)+"}"; }
			if(method.equals("getTransactionsForAddress")) { var number=pages.getAndIncrement(); var episode=number/3+1; var page=number%3; var sig=page<2?soakSignature(episode-1,page+1):soakSignature(episode-1,0); var row=payloads.get(sig).replace("\"err\":", "\"logMessages\":[\""+"x".repeat(1000000)+"\"],\"err\":"); return "{\"jsonrpc\":\"2.0\",\"id\":"+request.path("id").longValue()+",\"result\":{\"data\":["+row+"],\"paginationToken\":"+(page==2?"null":"\"soak-"+episode+"-"+(page+1)+"\"")+"}}"; }
			return body;
		});
		try(var websocket=new LocalCaptureWebSocket(19,(index,account)->{
			var notifications=new java.util.ArrayList<String>(); notifications.add("{\"params\":{\"subscription\":"+(account-1)+",\"result\":{\"context\":{\"slot\":"+(101+index*10)+"},\"value\":{\"signature\":\""+soakSignature(index,0)+"\"}}}}");
			notifications.add("{\"params\":{\"subscription\":"+account+",\"result\":{\"context\":{\"slot\":"+(1000+index)+"},\"value\":{\"data\":[\"AQ==\",\"base64\"]}}}}"); return notifications;
		},connected::set)) {
			try {
				launch(c,http,websocket); var active=PumpSwapPoolCollector.class.getDeclaredField("active"); active.setAccessible(true); owned.set(((java.util.concurrent.atomic.AtomicReference<?>)active.get(collector)).get()); awaitLocal(()->connected.get()==0 && jdbc.sql("SELECT count(*) FROM marketdata.capture_dispositions WHERE session_id=:id AND raw_digest IS NOT NULL").param("id",SESSION).query(Long.class).single()==1,"first actual live notification durable");
				for(var episode=1;episode<=18;episode++) {
					advanceWithActualLease(START.plusSeconds(episode*900L)); websocket.disconnect(1011,"synthetic-soak"); var expected=episode;
					awaitLocal(()->connected.get()==expected && jdbc.sql("SELECT count(*) FROM marketdata.capture_gaps WHERE session_id=:id AND status='RECOVERED_TRANSACTIONS'").param("id",SESSION).query(Long.class).single()==expected,"actual reconnect and exact transaction-gap closure episode"+episode);
					awaitLocal(()->jdbc.sql("SELECT count(*) FROM marketdata.capture_dispositions WHERE session_id=:id AND signature=:sig AND raw_digest IS NOT NULL").param("id",SESSION).param("sig",soakSignature(expected,0)).query(Long.class).single()==1,"continued live notification after recovery");
				}
				advanceWithActualLease(CaptureLedgerUseCase.end(c).minusSeconds(60)); var account=websocket.accountSubscription(); var burst=new java.util.ArrayList<String>(); burst.add("{\"params\":{\"subscription\":"+(account-1)+",\"result\":{\"context\":{\"slot\":291},\"value\":{\"signature\":\""+soakSignature(19,0)+"\"}}}}"); for(var i=0;i<14;i++) burst.add(i%3==0?"{\"params\":{\"subscription\":"+(account-1)+",\"result\":{\"context\":{\"slot\":"+(2000+i)+"},\"value\":{\"signature\":\""+soakSignature(18,i+2)+"\"}}}}":"{\"params\":{\"subscription\":"+account+",\"result\":{\"context\":{\"slot\":"+(2000+i)+"},\"value\":{\"data\":[\"AQ==\",\"base64\"]}}}}"); websocket.send(burst);
				assertThat(finalFetch.await(8,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); awaitLocal(()->jdbc.sql("SELECT count(*) FROM marketdata.capture_envelopes WHERE session_id=:id AND kind='WEBSOCKET' AND jsonb_exists(payload::jsonb,'params')").param("id",SESSION).query(Long.class).single()==53,"all14 final callback frames admitted before natural end"); advanceWithActualLease(CaptureLedgerUseCase.end(c)); var before=requests.get(); finishFetch.countDown(); awaitLocal(()->jdbc.sql("SELECT state FROM marketdata.capture_sessions WHERE session_id=:id").param("id",SESSION).query(String.class).single().equals("STOPPED"),"natural configured6h fenced closing"); org.mockito.Mockito.reset(dataSource);
				var status=ledger.status(SESSION); assertThat(status.reason()).isEqualTo("CAPTURE_TIME_LIMIT"); assertThat(requests.get()).isEqualTo(before); assertThat(pages.get()).isEqualTo(54); assertThat(status.requests()).isLessThanOrEqualTo(3750); assertThat(status.receivedBytes()).isLessThanOrEqualTo(500000000); assertThat(status.retainedBytes()).isLessThanOrEqualTo(600000000); assertThat(status.subscriptionBytes()).isLessThanOrEqualTo(10000000); assertThat(status.rpcHundredths()).isLessThanOrEqualTo(7500000); assertThat(clock.instant()).isAfterOrEqualTo(START.plusSeconds(21600));
				assertThat(ledger.dispositions(SESSION)).hasSize(61).filteredOn(d->d.rawDigest()!=null).hasSize(55); assertThat(status.states()).isEqualTo(43); assertThat(ledger.gaps(SESSION)).filteredOn(g->g.status().equals("RECOVERED_TRANSACTIONS")).hasSize(18); assertThat(ledger.gaps(SESSION)).anyMatch(g->g.kind().equals("STATE") && g.status().equals("UNRESOLVED_STATE_GAP")); assertThat(jdbc.sql("SELECT max(octet_length(notification)) FROM marketdata.capture_dispositions WHERE session_id=:id AND notification LIKE '%capture-history-row-v1%'").param("id",SESSION).query(Integer.class).single()).isLessThan(2048); assertThat(jdbc.sql("SELECT count(*) FROM marketdata.capture_envelopes WHERE session_id=:id AND kind='HTTP_getTransactionsForAddress'").param("id",SESSION).query(Long.class).single()).isEqualTo(54); assertThat(sql.get()).isPositive();
				System.out.println("LOCAL_SYNTHETIC_SIX_HOUR_SOAK connections="+(connected.get()+1)+" reconnects=18 pages="+pages.get()+" raw=55 dispositions=61 pending=6 states="+status.states()+" recoveredTransactionGaps=18 requests="+status.requests()+" receivedBytes="+status.receivedBytes()+" retainedBytes="+status.retainedBytes()+" subscriptionBytes="+status.subscriptionBytes()+" rpcHundredths="+status.rpcHundredths()+" modeledSqlRoundTrips="+sql.get()+" endedAt="+clock.instant()+" reason="+status.reason());
			} finally {
				org.mockito.Mockito.mockingDetails(captureStore).getInvocations().stream().filter(i->i.getMethod().getName().equals("captureAdvance") && i.getArguments().length==9 && Boolean.TRUE.equals(i.getArgument(8))).limit(8).forEach(i->System.out.println("SOAK_CLOSING_ACCOUNTING requests="+i.getArgument(3)+" received="+i.getArgument(4)+" subscription="+i.getArgument(5)+" retained="+i.getArgument(6)+" rpc="+i.getArgument(7)));
				finishFetch.countDown(); collector.stop(SESSION); org.mockito.Mockito.reset(dataSource); settings.setEnabled(false); http.stop(0);
			}
		}
	}
	@Test void compactSummaryUsesDurableProjectionWithoutPayloadBearingReads() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer");
		for(var i=0;i<68;i++) ledger.stateAt(SESSION,"writer",address(1),100+i,"{\"data\":[\"AQ==\",\"base64\"]}","{}",START);
		for(var i=0;i<10;i++) { var sig="1".repeat(62)+"R"+"ABCDEFGHJK".charAt(i); ledger.pending(SESSION,"writer",address(0),sig,200+i,"{}"); capture.transaction(SESSION,"writer",address(0),sig,syntheticV1(sig,200+i,false),"{}",START,true); }
		var run=controlledRun(c); org.mockito.Mockito.clearInvocations(captureStore);
		// 500ms per selected DB read is represented by clock increments, not wall sleeps.
		org.mockito.Mockito.doAnswer(invocation->{ clock.set(clock.instant().plusMillis(500)); return invocation.callRealMethod(); }).when(captureStore).dispositions(SESSION);
		org.mockito.Mockito.doAnswer(invocation->{ clock.set(clock.instant().plusMillis(500)); return invocation.callRealMethod(); }).when(captureStore).boundary(org.mockito.ArgumentMatchers.eq(SESSION),org.mockito.ArgumentMatchers.anyString());
		// Execute on the owning writer so the summary's commit is real and synchronous.
		runField(run,"databaseWriter",Thread.currentThread()); runCall(run,"summary",new Class<?>[]{String.class},"CONTROLLED_COMPACT");
		org.mockito.Mockito.verify(captureStore,org.mockito.Mockito.never()).dispositions(SESSION);
		org.mockito.Mockito.verify(captureStore,org.mockito.Mockito.never()).boundary(org.mockito.ArgumentMatchers.eq(SESSION),org.mockito.ArgumentMatchers.anyString());
		var summary=ledger.replay(SESSION).envelopes().stream().filter(e->e.kind().equals("CAPTURE_SUMMARY")).findFirst().orElseThrow();
		assertThat(summary.payload()).contains("\"CAPTURED\":10","\"slot\":209").doesNotContain("transactionConfig"); assertThat(summary.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(8192); assertThat(ledger.status(SESSION).states()).isEqualTo(68);
	}
	@Test void unknownBoundarySummaryCrossingDeadlineCannotCompleteRecovery() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer"); var gaps=ledger.episode(SESSION,"writer","DISCONNECT",START);
		jdbc.sql("UPDATE marketdata.capture_sessions SET locked_until=:until WHERE session_id=:id").param("until",START.plusSeconds(600).atOffset(java.time.ZoneOffset.UTC)).param("id",SESSION).update();
		var run=controlledRun(c); runField(run,"episode",java.util.List.of(gaps.getFirst())); runField(run,"recoveryDeadline",START.plusSeconds(300)); runField(run,"databaseWriter",Thread.currentThread());
		org.mockito.Mockito.doAnswer(invocation->{ var result=invocation.callRealMethod(); clock.set(START.plusSeconds(301)); return result; }).when(captureStore).envelope(org.mockito.ArgumentMatchers.eq(SESSION),org.mockito.ArgumentMatchers.eq("CAPTURE_SUMMARY"),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
		org.assertj.core.api.Assertions.assertThatThrownBy(()->runCall(run,"recover",new Class<?>[]{})).hasMessage("RECOVERY_TIME_LIMIT");
		assertThat(ledger.gaps(SESSION)).noneMatch(g->g.status().equals("RECOVERED_TRANSACTIONS"));
	}
	@Test void latchedInterruptionCannotCompleteUnknownBoundaryRecovery() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer"); var run=controlledRun(c); runField(run,"episode",ledger.episode(SESSION,"writer","DISCONNECT",START)); runField(run,"recoveryDeadline",START.plusSeconds(300));
		var field=run.getClass().getDeclaredField("interruption"); field.setAccessible(true); ((java.util.concurrent.atomic.AtomicReference<String>)field.get(run)).set("RECOVERY_TIME_LIMIT"); runField(run,"databaseWriter",Thread.currentThread());
		org.assertj.core.api.Assertions.assertThatThrownBy(()->runCall(run,"recover",new Class<?>[]{})).hasMessage("RECOVERY_TIME_LIMIT");
		assertThat(ledger.replay(SESSION).envelopes()).noneMatch(e->e.kind().equals("CAPTURE_SUMMARY"));
	}
	@Test void admittedWriterEvidenceAtNaturalEndEntersClosingBeforeAccounting() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer"); var run=controlledRun(c);
		var n=new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(address(0),address(1),null,105,"{\"data\":[\"AQ==\",\"base64\"]}","{}",START);
		runCall(run,"enqueue",new Class<?>[]{n.getClass()},n); runField(run,"requests",3L); runField(run,"rpc",4000L); runField(run,"received",11L); runField(run,"subscription",7L); runField(run,"retained",13L);
		var end=CaptureLedgerUseCase.end(c); jdbc.sql("UPDATE marketdata.capture_sessions SET locked_until=:until WHERE session_id=:id").param("until",end.plusSeconds(30).atOffset(java.time.ZoneOffset.UTC)).param("id",SESSION).update();
		var write=run.getClass().getDeclaredMethod("write",long.class,boolean.class,Runnable.class); write.setAccessible(true);
		var accepted=(java.util.concurrent.CompletableFuture<?>)write.invoke(run,2L,false,(Runnable)()->{ try { runCall(run,"flush",new Class<?>[]{String.class,String.class,java.time.Instant.class},"HTTP_getTransaction","{}",end); } catch(RuntimeException failure) { throw failure; } catch(Exception failure) { throw new IllegalStateException(failure); } });
		clock.set(end); var began=System.nanoTime(); var writer=Thread.ofVirtual().start(()->{ try { runCall(run,"writerLoop",new Class<?>[]{}); } catch(Exception failure) { throw new AssertionError(failure); } }); runField(run,"databaseWriter",writer);
		try {
			org.assertj.core.api.Assertions.assertThatCode(()->accepted.get(2,java.util.concurrent.TimeUnit.SECONDS)).as("already admitted HTTP evidence at natural end uses bounded fenced closing, not resource rejection").doesNotThrowAnyException();
			var deadline=run.getClass().getDeclaredField("drainDeadline"); deadline.setAccessible(true); assertThat(deadline.getLong(run)).isBetween(began,began+java.util.concurrent.TimeUnit.SECONDS.toNanos(12));
			var field=run.getClass().getDeclaredField("interruption"); field.setAccessible(true); assertThat(((java.util.concurrent.atomic.AtomicReference<?>)field.get(run)).get()).isEqualTo("CAPTURE_TIME_LIMIT");
		} finally { runCall(run,"terminal",new Class<?>[]{String.class},"CAPTURE_TIME_LIMIT"); writer.join(java.time.Duration.ofSeconds(11)); }
		var status=ledger.status(SESSION); assertThat(status.state()).isEqualTo("STOPPED"); assertThat(status.reason()).isEqualTo("CAPTURE_TIME_LIMIT"); assertThat(status.requests()).isEqualTo(3); assertThat(status.rpcHundredths()).isEqualTo(4000); assertThat(status.receivedBytes()).isEqualTo(11); assertThat(status.states()).isEqualTo(1);
		org.mockito.Mockito.verify(captureStore,org.mockito.Mockito.never()).claim(org.mockito.ArgumentMatchers.eq(SESSION),org.mockito.ArgumentMatchers.eq("writer"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
	}
	@Test void currentFenceUsesOneAtomicStatementAndPreservesRejectAndRollbackRules() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer"); var run=controlledRun(c); var sql=modeledSqlRoundTrips(run,false);
		try { captureStore.fence(SESSION,"writer",START,START.plusSeconds(40)); } finally { org.mockito.Mockito.reset(dataSource); }
		var queries=sql.get(); clock.set(START);
		org.assertj.core.api.Assertions.assertThatThrownBy(()->captureStore.fence(SESSION,"writer",START.plusSeconds(41),START.plusSeconds(71))).hasMessage("CAPTURE_LEASE_LOST");
		org.assertj.core.api.Assertions.assertThatThrownBy(()->captureStore.fence(SESSION,"stolen",START,START.plusSeconds(30))).hasMessage("CAPTURE_LEASE_LOST");
		jdbc.sql("UPDATE marketdata.capture_sessions SET state='STOPPED' WHERE session_id=:id").param("id",SESSION).update();
		org.assertj.core.api.Assertions.assertThatThrownBy(()->captureStore.fence(SESSION,"writer",START,START.plusSeconds(30))).hasMessage("CAPTURE_LEASE_LOST");
		jdbc.sql("UPDATE marketdata.capture_sessions SET state='RUNNING' WHERE session_id=:id").param("id",SESSION).update();
		org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.closingWork(c,"writer",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11),()->{ throw new IllegalStateException("CONTROLLED_FENCE_ROLLBACK"); },()->{})).hasMessage("CONTROLLED_FENCE_ROLLBACK");
		assertThat(jdbc.sql("SELECT locked_until FROM marketdata.capture_sessions WHERE session_id=:id").param("id",SESSION).query(java.time.OffsetDateTime.class).single().toInstant()).isEqualTo(START.plusSeconds(40));
		try(var owner=dataSource.getConnection(); var contender=java.util.concurrent.Executors.newSingleThreadExecutor()) {
			owner.setAutoCommit(false); try(var lock=owner.prepareStatement("SELECT session_id FROM marketdata.capture_sessions WHERE session_id=? FOR UPDATE")) { lock.setObject(1,SESSION); try(var result=lock.executeQuery()) { assertThat(result.next()).isTrue(); } }
			var waiting=contender.submit(()->captureStore.fence(SESSION,"writer",START,START.plusSeconds(45)));
			try { awaitLocal(()->jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%capture_sessions%' AND pid<>pg_backend_pid()").query(Long.class).single()>0,"same ownership fence must respect the existing row lock"); assertThat(waiting.isDone()).isFalse(); } finally { owner.rollback(); }
			waiting.get(2,java.util.concurrent.TimeUnit.SECONDS);
		}
		assertThat(jdbc.sql("SELECT locked_until FROM marketdata.capture_sessions WHERE session_id=:id").param("id",SESSION).query(java.time.OffsetDateTime.class).single().toInstant()).isEqualTo(START.plusSeconds(45));
		assertThat(queries).as("one conditional statement must both verify current ownership and renew the same locked row").isEqualTo(1);
	}
	@Test void naturalEndClosingRetainsAdmittedCountersAndAccountTailWithoutClaim() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer"); var run=controlledRun(c);
		var n=new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(address(0),address(1),null,104,"{\"data\":[\"AQ==\",\"base64\"]}","{}",START);
		runCall(run,"enqueue",new Class<?>[]{n.getClass()},n); runField(run,"requests",3L); runField(run,"rpc",4000L); runField(run,"received",11L); runField(run,"subscription",7L); runField(run,"retained",13L);
		var end=CaptureLedgerUseCase.end(c); jdbc.sql("UPDATE marketdata.capture_sessions SET locked_until=:until WHERE session_id=:id").param("until",end.plusSeconds(30).atOffset(java.time.ZoneOffset.UTC)).param("id",SESSION).update(); clock.set(end);
		runField(run,"writerClosing",true); runField(run,"drainDeadline",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11)); var field=run.getClass().getDeclaredField("interruption"); field.setAccessible(true); ((java.util.concurrent.atomic.AtomicReference<String>)field.get(run)).set("CAPTURE_TIME_LIMIT");
		runCall(run,"writerLoop",new Class<?>[]{}); var status=ledger.status(SESSION);
		assertThat(status.state()).isEqualTo("STOPPED"); assertThat(status.reason()).isEqualTo("CAPTURE_TIME_LIMIT"); assertThat(status.requests()).isEqualTo(3); assertThat(status.rpcHundredths()).isEqualTo(4000); assertThat(status.receivedBytes()).isEqualTo(11); assertThat(status.states()).isEqualTo(1);
		assertThat(ledger.states(SESSION)).singleElement().satisfies(s->assertThat(s.receivedAt()).isEqualTo(START));
	}
	@Test void optionalTerminalSummaryCannotExpireLeaseBeforeStop() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer"); var run=controlledRun(c); runField(run,"writerClosing",true); runField(run,"drainDeadline",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11));
		runField(run,"databaseWriter",Thread.currentThread());
		var field=run.getClass().getDeclaredField("interruption"); field.setAccessible(true); ((java.util.concurrent.atomic.AtomicReference<String>)field.get(run)).set("RECOVERY_TIME_LIMIT");
		org.mockito.Mockito.doAnswer(invocation->{ var result=invocation.callRealMethod(); clock.set(START.plusSeconds(31)); return result; }).when(captureStore).envelope(org.mockito.ArgumentMatchers.eq(SESSION),org.mockito.ArgumentMatchers.eq("CAPTURE_SUMMARY"),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
		runCall(run,"writerLoop",new Class<?>[]{}); assertThat(ledger.status(SESSION).state()).isEqualTo("STOPPED"); assertThat(ledger.status(SESSION).reason()).isEqualTo("RECOVERY_TIME_LIMIT");
	}
	@Test void queuedRecoveredGapCrossingDeadlineRollsBackItsTransition() {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer"); var sig="1".repeat(62)+"TA"; ledger.pending(SESSION,"writer",address(0),sig,100,"{}"); capture.transaction(SESSION,"writer",address(0),sig,syntheticV1(sig,100,false),"{}",START,true);
		var gap=ledger.episode(SESSION,"writer","DISCONNECT",START).getFirst();
		org.mockito.Mockito.doAnswer(invocation->{ var result=invocation.callRealMethod(); clock.set(START.plusSeconds(301)); return result; }).when(captureStore).recoveredGap(org.mockito.ArgumentMatchers.eq(SESSION),org.mockito.ArgumentMatchers.eq(gap.id()),org.mockito.ArgumentMatchers.eq(sig),org.mockito.ArgumentMatchers.any());
		org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.recoveredGap(c,"writer",gap,()->{ if(!clock.instant().isBefore(START.plusSeconds(300))) throw new IllegalStateException("RECOVERY_TIME_LIMIT"); })).hasMessage("RECOVERY_TIME_LIMIT");
		assertThat(ledger.gaps(SESSION)).filteredOn(g->g.id()==gap.id()).singleElement().satisfies(g->assertThat(g.status()).isEqualTo("UNRESOLVED_TRANSACTION_GAP"));
	}
	@Test void closingCannotReacquireExpiredFenceOrCommitPastDrain() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer"); clock.set(START.plusSeconds(31));
		org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.close(c,"writer","RECOVERY_TIME_LIMIT",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11),"{}",()->{})).hasMessage("CAPTURE_LEASE_LOST");
		assertThat(ledger.status(SESSION).state()).isEqualTo("RUNNING"); assertThat(ledger.gaps(SESSION)).isEmpty();
		clock.set(START); var run=controlledRun(c); runField(run,"drainDeadline",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11));
		org.mockito.Mockito.doAnswer(invocation->{ var result=invocation.callRealMethod(); runField(run,"drainDeadline",System.nanoTime()-1); return result; }).when(captureStore).stop(SESSION,"RECOVERY_TIME_LIMIT");
		org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.close(c,"writer","RECOVERY_TIME_LIMIT",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11),"{}",()->{ try { runCall(run,"closingTime",new Class<?>[]{}); } catch(RuntimeException failure) { throw failure; } catch(Exception failure) { throw new AssertionError(failure); } })).hasMessage("CAPTURE_WRITER_DRAIN_TIMEOUT");
		assertThat(ledger.status(SESSION).state()).isEqualTo("RUNNING"); assertThat(ledger.gaps(SESSION)).isEmpty(); assertThat(ledger.replay(SESSION).envelopes()).isEmpty();
	}
	@Test void threePageTenIdentityRecoveryWithControlledRoundTripsUsesCompactSnapshots() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer"); var boundary="1".repeat(62)+"UA"; ledger.pending(SESSION,"writer",address(0),boundary,100,"{}"); capture.transaction(SESSION,"writer",address(0),boundary,syntheticV1(boundary,100,false),"{}",START,true);
		for(var i=0;i<68;i++) ledger.stateAt(SESSION,"writer",address(1),300+i,"{\"data\":[\"AQ==\",\"base64\"]}","{}",START);
		var run=controlledRun(c); runField(run,"episode",ledger.episode(SESSION,"writer","DISCONNECT",START)); runField(run,"recoveryDeadline",START.plusSeconds(300));
		var pages=new java.util.concurrent.atomic.AtomicInteger(); var starts=new java.util.concurrent.atomic.AtomicInteger(); var transport=org.mockito.Mockito.mock(io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.class);
		var signatures=java.util.stream.IntStream.range(0,10).mapToObj(i->"1".repeat(62)+"V"+"ABCDEFGHJK".charAt(i)).toList();
		org.mockito.Mockito.when(transport.rpc(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation->{
			clock.set(clock.instant().plusMillis(500)); var method=(String)invocation.getArgument(0); String result;
			if(method.equals("getTransactionsForAddress")) { var page=pages.getAndIncrement(); var rows=new java.util.ArrayList<String>(); for(var i=page*4;i<Math.min(10,page*4+4);i++) rows.add("{\"slot\":"+(200-i)+",\"transaction\":{\"signatures\":[\""+signatures.get(i)+"\"]}}"); if(page==2) rows.add("{\"slot\":100,\"transaction\":{\"signatures\":[\""+boundary+"\"]}}"); result="{\"data\":["+String.join(",",rows)+"],\"paginationToken\":"+(page==2?"null":"\"page-"+(page+1)+"\"")+"}"; }
			else { var params=tools.jackson.databind.json.JsonMapper.builder().build().readTree((String)invocation.getArgument(1)); var sig=params.path(0).asText(); starts.incrementAndGet(); result=syntheticV1(sig,200-signatures.indexOf(sig),false); }
			return new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Response(result,"{}",clock.instant());
		}); runField(run,"transport",transport);
		org.mockito.Mockito.doAnswer(invocation->{ clock.set(clock.instant().plusMillis(500)); return invocation.callRealMethod(); }).when(captureStore).summarySnapshot(SESSION);
		var writer=Thread.ofVirtual().start(()->{ try { runCall(run,"writerLoop",new Class<?>[]{}); } catch(Exception failure) { throw new AssertionError(failure); } }); runField(run,"databaseWriter",writer);
		try { runCall(run,"recover",new Class<?>[]{}); assertThat(pages.get()).isEqualTo(3); assertThat(starts.get()).isEqualTo(10); assertThat(ledger.status(SESSION).states()).isEqualTo(68); assertThat(ledger.dispositions(SESSION)).hasSize(11); assertThat(ledger.gaps(SESSION)).filteredOn(g->g.pool().equals(address(0)) && g.kind().equals("TRANSACTION")).singleElement().satisfies(g->assertThat(g.status()).isEqualTo("RECOVERED_TRANSACTIONS")); org.mockito.Mockito.verify(captureStore,org.mockito.Mockito.times(3)).summarySnapshot(SESSION); }
		finally { runCall(run,"terminal",new Class<?>[]{String.class},"OWNER_STOP"); writer.join(java.time.Duration.ofSeconds(11)); assertThat(writer.isAlive()).isFalse(); }
	}
	@Test void concurrentTerminalCallsShareOneAdmissionDrainAndOriginalDeadline() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer"); var run=controlledRun(c); var transport=org.mockito.Mockito.mock(io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.class); runField(run,"transport",transport);
		var firstEntered=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1); var calls=new java.util.concurrent.atomic.AtomicInteger();
		org.mockito.Mockito.doAnswer(invocation->{ if(calls.incrementAndGet()==1) { firstEntered.countDown(); if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("controlled drain was not released"); } return null; }).when(transport).awaitAdmitted();
		try(var threads=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var first=threads.submit(()->{ runCall(run,"terminal",new Class<?>[]{String.class},"RECOVERY_TIME_LIMIT"); return true; });
			try { assertThat(firstEntered.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); var deadline=run.getClass().getDeclaredField("drainDeadline"); deadline.setAccessible(true); var original=deadline.getLong(run); var second=threads.submit(()->{ runCall(run,"terminal",new Class<?>[]{String.class},"OWNER_STOP"); return true; }); assertThat(second.get(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); assertThat(calls.get()).as("only the first terminal caller may wait for admitted frames").isEqualTo(1); assertThat(deadline.getLong(run)).isEqualTo(original); }
			finally { release.countDown(); first.get(2,java.util.concurrent.TimeUnit.SECONDS); }
		}
	}
	@Test void exhaustedRetainedAllowanceCannotBlockPrimaryStopOrDropRaw() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer"); var sig="1".repeat(62)+"WA"; var payload=syntheticV1(sig,100,false); ledger.pending(SESSION,"writer",address(0),sig,100,"{}"); capture.transaction(SESSION,"writer",address(0),sig,payload,"{}",START,true);
		jdbc.sql("UPDATE marketdata.capture_sessions SET retained_bytes=:retained,received_bytes=13 WHERE session_id=:id").param("retained",c.limits().retainedBytes()).param("id",SESSION).update(); var run=controlledRun(c); runField(run,"writerClosing",true); runField(run,"drainDeadline",System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(11)); var field=run.getClass().getDeclaredField("interruption"); field.setAccessible(true); ((java.util.concurrent.atomic.AtomicReference<String>)field.get(run)).set("CAPTURE_RESOURCE_LIMIT");
		runCall(run,"writerLoop",new Class<?>[]{}); var status=ledger.status(SESSION); assertThat(status.state()).isEqualTo("STOPPED"); assertThat(status.reason()).isEqualTo("CAPTURE_RESOURCE_LIMIT"); assertThat(status.retainedBytes()).isEqualTo(c.limits().retainedBytes()); assertThat(status.receivedBytes()).isEqualTo(13);
		assertThat(rawStore.find(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,sig),"alchemy").orElseThrow().payload()).isEqualTo(payload); assertThat(ledger.dispositions(SESSION)).singleElement().satisfies(d->assertThat(d.status()).isEqualTo("CAPTURED"));
	}
	@Test void renewalAtNaturalSixHourEndUsesValidFenceNotPostEndClaim() throws Exception {
		clock.set(START); var source=configuration(SESSION,0L); var l=source.limits(); var c=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(source.sessionId(),source.version(),source.provider(),source.startsAt(),source.pools(),new io.cryptoresearch.marketdata.api.PumpSwapCapture.Limits(21600,l.requests(),l.receivedBytes(),l.retainedBytes(),l.subscriptionBytes(),l.rpcHundredths(),l.messageBytes(),l.responseBytes(),l.queueItems(),l.queueBytes(),l.timeoutSeconds(),l.retries(),l.reconnects(),l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds()),source.authority()); ledger.open(c,"writer"); var run=controlledRun(c); runField(run,"requests",3L); runField(run,"rpc",4000L);
		var end=CaptureLedgerUseCase.end(c); jdbc.sql("UPDATE marketdata.capture_sessions SET locked_until=:until WHERE session_id=:id").param("until",end.plusSeconds(30).atOffset(java.time.ZoneOffset.UTC)).param("id",SESSION).update(); var ready=new java.util.concurrent.CountDownLatch(1); var write=run.getClass().getDeclaredMethod("write",long.class,boolean.class,Runnable.class); write.setAccessible(true); write.invoke(run,0L,false,(Runnable)ready::countDown);
		var writer=Thread.ofVirtual().start(()->{ try { runCall(run,"writerLoop",new Class<?>[]{}); } catch(Exception failure) { throw new AssertionError(failure); } }); runField(run,"databaseWriter",writer);
		try { assertThat(ready.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); clock.set(end.plusSeconds(11)); var field=run.getClass().getDeclaredField("interruption"); field.setAccessible(true); var reason=(java.util.concurrent.atomic.AtomicReference<String>)field.get(run); for(var i=0;i<200 && reason.get()==null;i++) Thread.sleep(10); assertThat(reason.get()).as("the existing valid fence survives the original21600s end").isEqualTo("CAPTURE_TIME_LIMIT"); }
		finally { runCall(run,"terminal",new Class<?>[]{String.class},"CAPTURE_TIME_LIMIT"); writer.join(java.time.Duration.ofSeconds(11)); }
		assertThat(ledger.status(SESSION).state()).isEqualTo("STOPPED"); assertThat(ledger.status(SESSION).requests()).isEqualTo(3); assertThat(ledger.status(SESSION).rpcHundredths()).isEqualTo(4000); org.mockito.Mockito.verify(captureStore,org.mockito.Mockito.never()).claim(org.mockito.ArgumentMatchers.eq(SESSION),org.mockito.ArgumentMatchers.eq("writer"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
	}
	// Synthetic raw v1 JSON based on official Solana format/introspection docs, not captured mainnet evidence.
	static String syntheticV1(String signature,long slot,boolean failed) {
		return "{ \"version\" : 1, \"slot\" : "+slot+", \"transaction\":{\"signatures\":[\""+signature+"\"],\"message\":{\"transactionConfig\":{\"computeUnitLimit\":123456},\"syntheticUnknown\":true}}, \"meta\":{\"err\":"+(failed?"{\"InstructionError\":[0,\"Custom\"]}":"null")+"}, \"syntheticUnknown\" : [1, 2] }";
	}
	@Test void syntheticV1SuccessAndOnChainFailureRetainExactRawWithoutAltFields() {
		clock.set(START); ledger.open(configuration(SESSION,0L),"writer");
		for(var failed:java.util.List.of(false,true)) {
			var signature="1".repeat(62)+"Y"+(failed?"B":"A"); var payload=syntheticV1(signature,failed?102:101,failed);
			ledger.pending(SESSION,"writer",address(0),signature,failed?102:101,"{}");
			assertThat(capture.transaction(SESSION,"writer",address(0),signature,payload,"{}",START,true)).isEqualTo(failed?"FAILED_TRANSACTION":"CAPTURED");
			var stored=rawStore.find(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,signature),"alchemy").orElseThrow();
			assertThat(stored.payload()).isEqualTo(payload); assertThat(stored.payloadHash()).isEqualTo(PayloadFingerprint.sha256(payload)); assertThat(stored.payload()).doesNotContain("addressTableLookups","loadedAddresses");
		}
	}
	@Test void liveCeilingOneAndUnsupportedFutureVersionContinueSameConnection() throws Exception { versionContinuation(false,false); }
	@Test void historyCeilingOneAndUnsupportedFutureVersionLeaveRecoveryPartial() throws Exception { versionContinuation(true,false); }
	@Test void unsupportedGapWriterFailureRollsBackDispositionAndStops() throws Exception { versionContinuation(false,true); }
	private void versionContinuation(boolean recovery,boolean failGap) throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var boundary="1".repeat(62)+"ZD"; var future="1".repeat(62)+"ZE"; var supported="1".repeat(62)+"ZF";
		if(recovery) seedRecovery(c,boundary);
		if(failGap) org.mockito.Mockito.doThrow(new IllegalStateException("CAPTURE_WRITER_FAILURE")).when(captureStore).gap(org.mockito.ArgumentMatchers.eq(SESSION),org.mockito.ArgumentMatchers.eq(address(0)),org.mockito.ArgumentMatchers.eq("TRANSACTION"),org.mockito.ArgumentMatchers.eq("UNSUPPORTED_TRANSACTION_VERSION"),org.mockito.ArgumentMatchers.any());
		var transactionStarts=new java.util.concurrent.atomic.AtomicInteger(); var historyStarts=new java.util.concurrent.atomic.AtomicInteger(); var ceilings=new java.util.concurrent.CopyOnWriteArrayList<Integer>(); var errors=new java.util.concurrent.CopyOnWriteArrayList<String>();
		try(var websocket=new LocalCaptureWebSocket(subscription->{
			var notifications=new java.util.ArrayList<String>(); if(!recovery) for(var signature:java.util.List.of(future,supported)) notifications.add("{\"params\":{\"subscription\":"+(subscription-1)+",\"result\":{\"context\":{\"slot\":"+(signature.equals(future)?101:102)+"},\"value\":{\"signature\":\""+signature+"\"}}}}");
			notifications.add("{\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":103},\"value\":{\"data\":[\"AQ==\",\"base64\"]}}}}"); return notifications;
		},false)) {
			var http=mockAccounts(c,boundary,false,null,null,(method,result)->result,200,(request,body)->{
				var method=request.path("method").asText();
				if(method.equals("getTransactionsForAddress")) { historyStarts.incrementAndGet(); ceilings.add(request.path("params").path(1).path("maxSupportedTransactionVersion").intValue()); return "{\"jsonrpc\":\"2.0\",\"id\":"+request.path("id").longValue()+",\"result\":{\"data\":[{\"slot\":101,\"transaction\":{\"signatures\":[\""+future+"\"]}},{\"slot\":100,\"transaction\":{\"signatures\":[\""+boundary+"\"]}}],\"paginationToken\":null}}"; }
				if(method.equals("getTransaction")) { transactionStarts.incrementAndGet(); ceilings.add(request.path("params").path(1).path("maxSupportedTransactionVersion").intValue()); if(request.path("params").path(0).asText().equals(future)) { var error=rpcVersionError(request.path("id").longValue(),"-32015",unsupportedVersionMessage("2","2")); errors.add(error); return error; } return "{\"jsonrpc\":\"2.0\",\"id\":"+request.path("id").longValue()+",\"result\":"+syntheticV1(supported,102,false)+"}"; }
				return body;
			});
			try {
				launch(c,http,websocket); websocket.awaitSent();
				for(var i=0;i<300;i++) { var done=ledger.dispositions(SESSION).stream().anyMatch(d->d.signature().equals(failGap?future:recovery?future:supported) && !d.status().equals("PENDING")); if(failGap?api.captureStatus(SESSION).state().equals("STOPPED"):done && api.captureStatus(SESSION).states()==16) break; Thread.sleep(30); }
				if(failGap) { assertThat(api.captureStatus(SESSION).state()).isEqualTo("STOPPED"); assertThat(ledger.dispositions(SESSION)).filteredOn(d->d.signature().equals(future)).singleElement().satisfies(d->assertThat(d.status()).isEqualTo("PENDING")); assertThat(api.captureStatus(SESSION).gaps()).noneMatch(g->g.reason().equals("UNSUPPORTED_TRANSACTION_VERSION")); return; }
				org.assertj.core.api.SoftAssertions.assertSoftly(softly->{ softly.assertThat(ceilings).containsOnly(1); softly.assertThat(api.captureStatus(SESSION).state()).as("recognized future version cannot stop unrelated same-connection work").isEqualTo("RUNNING"); });
				assertThat(transactionStarts.get()).isEqualTo(recovery?1:2); assertThat(historyStarts.get()).isEqualTo(recovery?1:0); assertThat(errors).hasSize(1);
				assertThat(ledger.dispositions(SESSION)).filteredOn(d->d.signature().equals(future)).singleElement().satisfies(d->{ assertThat(d.status()).isEqualTo("FETCH_FAILURE"); assertThat(d.rawDigest()).isNull(); assertThat(d.response()).isEqualTo(errors.getFirst()); });
				assertThat(rawStore.find(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,future),"alchemy")).isEmpty(); assertThat(ledger.boundary(SESSION,address(0))).contains(recovery?boundary:supported);
				assertThat(api.captureStatus(SESSION).gaps()).anySatisfy(g->{ assertThat(g.reason()).isEqualTo("UNSUPPORTED_TRANSACTION_VERSION"); assertThat(g.status()).isEqualTo("UNRESOLVED_TRANSACTION_GAP"); });
				assertThat(api.captureStatus(SESSION).states()).isEqualTo(16); assertThat(api.replayCapture(SESSION).envelopes()).anySatisfy(e->{ assertThat(e.kind()).isEqualTo("CAPTURE_DIAGNOSTIC"); assertThat(e.payload()).contains("UNSUPPORTED_TRANSACTION_VERSION",future); });
				if(recovery) assertThat(api.captureStatus(SESSION).gaps()).noneMatch(g->g.status().equals("RECOVERED_TRANSACTIONS"));
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}
	@Test void legacyUnsuccessfulCheckpointResolvesOnlyMatchingSuccessfulRawEvidenceWithoutMutation() {
		clock.set(START); ledger.open(configuration(SESSION,0L),"writer");
		var good="1".repeat(63)+"U"; var bad="1".repeat(63)+"V";
		ledger.pending(SESSION,"writer",address(0),good,100,"{}");
		capture.transaction(SESSION,"writer",address(0),good,"{\"slot\":100,\"transaction\":{\"signatures\":[\""+good+"\"],\"message\":{}},\"meta\":{\"err\":null}}","{}",START,true);
		for(var pool:java.util.List.of(address(0),address(5))) {
			ledger.pending(SESSION,"writer",pool,bad,101,"{}");
			jdbc.sql("UPDATE marketdata.capture_dispositions SET status='UNAVAILABLE' WHERE session_id=:id AND pool=:pool AND signature=:sig").param("id",SESSION).param("pool",pool).param("sig",bad).update();
			jdbc.sql("INSERT INTO marketdata.capture_checkpoints VALUES (:id,:pool,:sig) ON CONFLICT(session_id,pool) DO UPDATE SET signature=EXCLUDED.signature").param("id",SESSION).param("pool",pool).param("sig",bad).update();
		}
		assertThat(ledger.boundary(SESSION,address(0))).as("legacy null checkpoint cannot replace successful raw boundary").contains(good);
		assertThat(ledger.boundary(SESSION,address(5))).as("no successful raw evidence remains UNKNOWN").isEmpty();
		var gaps=ledger.episode(SESSION,"writer","DISCONNECT",START);
		assertThat(gaps).filteredOn(g->g.pool().equals(address(0))).allSatisfy(g->{ assertThat(g.boundarySignature()).isEqualTo(good); assertThat(g.fromSlot()).isEqualTo(100L); });
		assertThat(gaps).filteredOn(g->g.pool().equals(address(5))).allSatisfy(g->{ assertThat(g.boundarySignature()).isNull(); assertThat(g.fromSlot()).isNull(); });
		assertThat(jdbc.sql("SELECT signature FROM marketdata.capture_checkpoints WHERE session_id=:id").param("id",SESSION).query(String.class).list()).containsOnly(bad);
		jdbc.sql("UPDATE marketdata.capture_dispositions SET raw_digest='sha256:'||repeat('0',64) WHERE session_id=:id AND signature=:sig").param("id",SESSION).param("sig",good).update();
		assertThat(ledger.boundary(SESSION,address(0))).as("successful disposition must match persisted raw digest").isEmpty();
	}
	@Test void mismatchingRawPositionCannotBeFrozenOrRecoveredAsTransactionBoundary() {
		clock.set(START); ledger.open(configuration(SESSION,0L),"writer"); var signature="1".repeat(63)+"T";
		ledger.pending(SESSION,"writer",address(0),signature,100,"{}");
		capture.transaction(SESSION,"writer",address(0),signature,"{\"slot\":101,\"transaction\":{\"signatures\":[\""+signature+"\"],\"message\":{}},\"meta\":{\"err\":null}}","{}",START,true);
		assertThat(rawStore.find(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,signature),"alchemy").orElseThrow().blockPosition().value()).isEqualTo(101L);
		assertThat(ledger.boundary(SESSION,address(0))).as("immutable raw slot101 cannot prove disposition slot100").isEmpty();
		var gap=ledger.episode(SESSION,"writer","DISCONNECT",START).stream().filter(g->g.pool().equals(address(0)) && g.kind().equals("TRANSACTION")).findFirst().orElseThrow();
		assertThat(gap.boundarySignature()).isNull(); assertThat(gap.fromSlot()).isNull(); ledger.recoveredGap(SESSION,"writer",gap);
		assertThat(ledger.gaps(SESSION)).filteredOn(g->g.id()==gap.id()).singleElement().satisfies(g->assertThat(g.status()).isEqualTo("UNRESOLVED_TRANSACTION_GAP"));
		assertThat(ledger.dispositions(SESSION)).singleElement().satisfies(d->assertThat(d.slot()).isEqualTo(100L));
	}
	@Test void twoReconnectEpisodesRetainSeparateUpperPositionsAndImmutableSummaries() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var first="1".repeat(63)+"W"; var second="1".repeat(63)+"X";
		var connection=new java.util.concurrent.atomic.AtomicInteger(); var fixtureRef=new java.util.concurrent.atomic.AtomicReference<LocalCaptureWebSocket>();
		try(var websocket=new LocalCaptureWebSocket(3,(index,subscription)->{
			connection.set(index+1); return index<2?java.util.List.of("{\"params\":{\"subscription\":"+(subscription-1)+",\"result\":{\"context\":{\"slot\":"+(101+index)+"},\"value\":{\"signature\":\""+(index==0?first:second)+"\"}}}}"):
				java.util.List.of("{\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":103},\"value\":{\"data\":[\"AQ==\",\"base64\"]}}}}");
		},index->{ if(index<2) {
			try { for(var i=0;i<400;i++) { var ready=ledger.boundary(SESSION,address(0)).filter(s->s.equals(index==0?first:second)).isPresent(); var recovered=index==0 || api.replayCapture(SESSION).envelopes().stream().anyMatch(e->e.kind().equals("CAPTURE_SUMMARY") && e.payload().contains("RECOVERY_COMPLETE_WITH_EXPLICIT_GAPS")); if(ready && recovered) break; Thread.sleep(30); } }
			catch(InterruptedException failure) { throw new AssertionError(failure); }
			fixtureRef.get().disconnect(1001,"going away");
		} })) {
			fixtureRef.set(websocket);
			var http=mockAccounts(c,first,false,null,null,(method,result)->{
				if(method.equals("getTransactionsForAddress")) { var later=connection.get()>=3; return "{\"data\":[{\"slot\":"+(later?102:101)+",\"transaction\":{\"signatures\":[\""+(later?second:first)+"\"]}}],\"paginationToken\":null}"; }
				if(method.equals("getTransaction") && connection.get()>=2) return result.replace(first,second).replace("\"slot\":101","\"slot\":102"); return result;
			},200);
			try {
				launch(c,http,websocket);
				java.util.List<tools.jackson.databind.JsonNode> summaries=java.util.List.of(); var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
				for(var i=0;i<3000;i++) { summaries=api.replayCapture(SESSION).envelopes().stream().filter(e->e.kind().equals("CAPTURE_SUMMARY") && e.payload().contains("RECOVERY_COMPLETE_WITH_EXPLICIT_GAPS")).map(e->mapper.readTree(e.payload())).toList(); if(summaries.size()==2 && api.captureStatus(SESSION).states()==16) break; Thread.sleep(30); }
				assertThat(connection.get()).isEqualTo(3); assertThat(summaries).hasSize(2);
				var ordered=summaries.stream().sorted(java.util.Comparator.comparingInt(n->n.path("attempts").intValue())).toList();
				assertThat(ordered.get(0).path("recoveryUpper").path(address(0)).path("signature").asText()).isEqualTo(first);
				assertThat(ordered.get(1).path("recoveryUpper").path(address(0)).path("signature").asText()).as("second episode must not reuse first upper position").isEqualTo(second);
				assertThat(ordered.get(0).path("episodeGaps")).isNotEqualTo(ordered.get(1).path("episodeGaps"));
				assertThat(ordered.get(1).path("recoveryPages").intValue()).isGreaterThan(ordered.get(0).path("recoveryPages").intValue());
				assertThat(api.captureStatus(SESSION).states()).isEqualTo(16); assertThat(api.captureStatus(SESSION).state()).isEqualTo("RUNNING");
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}
	@Test void unavailableDispositionCannotAdvanceCommittedTransactionBoundary() {
		clock.set(START); ledger.open(configuration(SESSION,0L),"writer");
		var good="1".repeat(63)+"U"; var missing="1".repeat(63)+"V";
		ledger.pending(SESSION,"writer",address(0),good,100,"{}");
		capture.transaction(SESSION,"writer",address(0),good,"{\"slot\":100,\"transaction\":{\"signatures\":[\""+good+"\"],\"message\":{}},\"meta\":{\"err\":null}}","{}",START,true);
		ledger.pending(SESSION,"writer",address(0),missing,101,"{}"); capture.transaction(SESSION,"writer",address(0),missing,"null","{\"result\":null}",START,true);
		assertThat(ledger.boundary(SESSION,address(0))).as("only committed finalized raw evidence is a recovery boundary").contains(good);
	}
	@Test void exactEpisodeClosureLeavesOlderSameBoundaryUnavailableGapOpen() {
		clock.set(START); var c=configuration(SESSION,0L); var boundary="1".repeat(63)+"Y"; var missing="1".repeat(63)+"Z";
		ledger.open(c,"writer"); ledger.pending(SESSION,"writer",address(0),boundary,100,"{}");
		capture.transaction(SESSION,"writer",address(0),boundary,"{\"slot\":100,\"transaction\":{\"signatures\":[\""+boundary+"\"],\"message\":{}},\"meta\":{\"err\":null}}","{}",START,true);
		ledger.pending(SESSION,"writer",address(0),missing,99,"{}"); capture.transaction(SESSION,"writer",address(0),missing,"null","{\"result\":null}",START,true);
		var old=ledger.gaps(SESSION).getFirst(); var first=ledger.episode(SESSION,"writer","DISCONNECT",START); var second=ledger.episode(SESSION,"writer","DISCONNECT",START.plusSeconds(1));
		var verified=second.stream().filter(g->g.kind().equals("TRANSACTION") && g.pool().equals(address(0))).findFirst().orElseThrow(); ledger.recoveredGap(SESSION,"writer",verified);
		assertThat(ledger.gaps(SESSION)).filteredOn(g->g.id()==old.id()).singleElement().satisfies(g->{ assertThat(g.boundarySignature()).isEqualTo(boundary); assertThat(g.status()).isEqualTo("UNRESOLVED_TRANSACTION_GAP"); });
		assertThat(ledger.gaps(SESSION)).filteredOn(g->first.stream().anyMatch(v->v.id()==g.id())).noneMatch(g->g.status().equals("RECOVERED_TRANSACTIONS"));
		assertThat(ledger.gaps(SESSION)).filteredOn(g->g.id()==verified.id()).singleElement().satisfies(g->assertThat(g.status()).isEqualTo("RECOVERED_TRANSACTIONS"));
	}
	@Test void actualReconnectCommitsEpisodeGapsAndContinuesAfterExactHistory() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var boundary="1".repeat(63)+"W";
		var connections=new java.util.concurrent.atomic.AtomicInteger(); var fixtureRef=new java.util.concurrent.atomic.AtomicReference<LocalCaptureWebSocket>();
		try(var websocket=new LocalCaptureWebSocket(2,(index,subscription)->{
			connections.incrementAndGet(); return index==0?java.util.List.of("{\"params\":{\"subscription\":"+(subscription-1)+",\"result\":{\"context\":{\"slot\":101},\"value\":{\"signature\":\""+boundary+"\"}}}}"):
				java.util.List.of("{\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":102},\"value\":{\"data\":[\"AQ==\",\"base64\"]}}}}");
		},index->{ if(index==0) {
			try { for(var i=0;i<200 && ledger.dispositions(SESSION).isEmpty();i++) Thread.sleep(30); for(var i=0;i<200 && ledger.boundary(SESSION,address(0)).isEmpty();i++) Thread.sleep(30); }
			catch(InterruptedException failure) { throw new AssertionError(failure); }
			fixtureRef.get().disconnect(1001,"going away");
		} })) {
			fixtureRef.set(websocket); var history=new java.util.concurrent.atomic.AtomicInteger();
			var http=mockAccounts(c,boundary,false,null,null,(method,result)->{ if(method.equals("getTransactionsForAddress")) { history.incrementAndGet(); return "{\"data\":[{\"slot\":101,\"transaction\":{\"signatures\":[\""+boundary+"\"]}}],\"paginationToken\":null}"; } return result; },200);
			try {
				launch(c,http,websocket);
				for(var i=0;i<2000 && (connections.get()<2 || api.captureStatus(SESSION).states()<16);i++) Thread.sleep(30);
				var status=api.captureStatus(SESSION);
				assertThat(connections.get()).as("actual second finalized subscription connection after transient close").isEqualTo(2);
				assertThat(status.state()).isEqualTo("RUNNING"); assertThat(status.states()).isEqualTo(16);
				assertThat(history.get()).isPositive(); assertThat(status.gaps()).anySatisfy(g->{ assertThat(g.pool()).isEqualTo(address(0)); assertThat(g.status()).isEqualTo("RECOVERED_TRANSACTIONS"); });
				assertThat(status.gaps()).anySatisfy(g->assertThat(g.status()).isEqualTo("UNRESOLVED_STATE_GAP"));
				assertThat(ledger.reserved(c.authority().accountPeriod())).isEqualTo(95000000); assertThat(ledger.configuration(SESSION).startsAt()).isEqualTo(START);
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}
	@org.springframework.test.context.bean.override.mockito.MockitoSpyBean javax.sql.DataSource dataSource;
	@org.springframework.test.context.bean.override.mockito.MockitoSpyBean io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore captureStore;

	@Test void ackArrivalBehindNotificationDoesNotWaitForDatabaseWriter() throws Exception {
		clock.set(START); var source=configuration(SESSION,0L); var l=shortAckLimits();
		var limits=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Limits(l.windowSeconds(),l.requests(),l.receivedBytes(),l.retainedBytes(),l.subscriptionBytes(),l.rpcHundredths(),l.messageBytes(),l.responseBytes(),l.queueItems(),l.queueBytes(),1,0,0,l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds());
		var c=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(source.sessionId(),source.version(),source.provider(),source.startsAt(),source.pools(),limits,source.authority());
		var held=new java.util.concurrent.CountDownLatch(1); var requests=new java.util.concurrent.atomic.AtomicInteger(); var account=new java.util.concurrent.atomic.AtomicLong();
		try(var lock=dataSource.getConnection()) {
			lock.setAutoCommit(false);
			try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of(),false,id->{
				var number=requests.incrementAndGet(); if(number==2) account.set(10000+id);
				if(number==3) try(var statement=lock.createStatement()) { statement.setQueryTimeout(3); statement.execute("LOCK TABLE marketdata.capture_envelopes IN ACCESS EXCLUSIVE MODE"); held.countDown(); }
				catch(java.sql.SQLException failure) { throw new AssertionError("synthetic database lock setup failed",failure); }
				return "{\"id\":"+id+",\"result\":"+(10000+id)+"}";
			},1,id->requests.get()==3?java.util.List.of("{\"params\":{\"subscription\":"+account.get()+",\"result\":{\"context\":{\"slot\":101},\"value\":{\"data\":[\"AQ==\",\"base64\"]}}}}"):java.util.List.of())) {
				var http=mockAccounts(c,"1".repeat(63)+"B",false);
				try {
					launch(c,http,websocket); assertThat(held.await(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
					Thread.sleep(1500); lock.rollback();
					for(var i=0;i<100 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(30);
					var status=api.captureStatus(SESSION);
					assertThat(status.reason()).as("ACK arrived while notification persistence was blocked, but did not commit before the shared deadline").isEqualTo("WS_DURABLE_ACK_TIMEOUT");
					assertThat(status.state()).isEqualTo("STOPPED");
					assertThat(status.states()).isEqualTo(16);
					assertThat(api.replayCapture(SESSION).envelopes()).anySatisfy(e->assertThat(e.payload()).contains("\"id\":18"));
					assertThat(status.gaps()).hasSize(6);
				} finally { lock.rollback(); collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
			}
		}
	}

	@BeforeEach void isolateSessionAuthority() {
		// Each scenario owns its first-window authority; do not reuse it across unrelated tests.
		if (jdbc.sql("SELECT to_regclass('marketdata.capture_sessions') IS NOT NULL").query(Boolean.class).single()) {
			jdbc.sql("TRUNCATE marketdata.capture_sessions, marketdata.capture_periods CASCADE").update();
		}
	}

	@Test void crashAfterRawCommitRedeliveryUsesFirstTimesAndCompletesPending() {
		var id = UUID.fromString("00000000-0000-0000-0000-000000000011");
		clock.set(START);
		ledger.open(configuration(id, 0L), "writer");
		var signature = "1".repeat(64);
		var result = "{\"slot\":100,\"blockTime\":null,\"transaction\":{\"signatures\":[\"" + signature + "\"],\"message\":{}},\"meta\":{\"err\":null}}";
		ledger.pending(id, "writer", address(0), signature, 100, "{\"notification\":true}");
		var original = raw.store(new RawTransactionPayload(ChainId.SOLANA_MAINNET,
				new TransactionId(ChainId.SOLANA_MAINNET, signature), "alchemy", new BlockPosition(ChainId.SOLANA_MAINNET, 100),
				Optional.empty(), Optional.empty(), START, START, result, Optional.of("pumpswap-capture-v1")));
		clock.set(START.plusSeconds(5));
		assertThat(capture.transaction(id, "writer", address(0), signature, result,
				"{\"id\":2}", clock.instant(), true)).isEqualTo("CAPTURED");
		assertThat(rawStore.find(ChainId.SOLANA_MAINNET, original.transactionId(), "alchemy")).contains(original);
		assertThat(ledger.dispositions(id)).singleElement().satisfies(row -> assertThat(row.status()).isEqualTo("CAPTURED"));
	}
	@Test void slowDurableAckEvidenceDoesNotConsumeLaterSubscriptionsTimeout() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L);
		assertThat(ledger.open(c,"writer")).isTrue();
		var persisted=new java.util.concurrent.atomic.AtomicInteger(); var bytes=new java.util.concurrent.atomic.AtomicLong();
		var writer=new Object(); var failure=new java.util.concurrent.atomic.AtomicReference<String>();
		try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of(),false);
			var adapter=new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport(java.net.URI.create("http://127.0.0.1/"),websocket.uri(),"synthetic-provider-secret",shortAckLimits(),
					new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Accounting() {
						public java.time.Instant now() { return clock.instant(); }
						public void start(long cu) { synchronized(writer) { assertThat(ledger.consume(SESSION,"writer",1,0,0,0,cu)).isTrue(); } }
						public void received(long count,boolean subscription) { synchronized(writer) { bytes.addAndGet(count); assertThat(ledger.received(SESSION,"writer",count,subscription)).isTrue(); } }
						public void nextMessage() { }
						public void evidence(String kind,String text,java.time.Instant receivedAt) {
							// Model slow durable ACK evidence, not delayed provider response or an invented ACK.
							try { Thread.sleep(150); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("CAPTURE_INTERRUPTED"); }
							synchronized(writer) {
								assertThat(ledger.consume(SESSION,"writer",0,0,0,text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,0)).isTrue();
								ledger.envelopeAt(SESSION,"writer",kind,text,receivedAt); persisted.incrementAndGet();
							}
						}
					})) {
			try { adapter.connect(c.pools(),n->{ throw new AssertionError("ACK is not a notification"); },failure::set); }
			catch(IllegalStateException stopped) { failure.set(stopped.getMessage()); }
			assertThat(failure.get()).as("each ACK durability delay fits its own bounded subscription request").isNull();
			assertThat(persisted.get()).isEqualTo(18);
			assertThat(ledger.replay(SESSION).envelopes()).hasSize(18);
			assertThat(ledger.status(SESSION).requests()).isEqualTo(18);
			assertThat(ledger.status(SESSION).subscriptionBytes()).isEqualTo(bytes.get()).isPositive();
			assertThat(ledger.status(SESSION).retainedBytes()).isEqualTo(bytes.get());
			assertThat(ledger.reserved(c.authority().accountPeriod())).isEqualTo(95000000);
		}
	}

	@Test void sameSlotStateVariantsRemainAmbiguousAndKeepFirstTimes() {
		var id = UUID.fromString("00000000-0000-0000-0000-000000000012");
		clock.set(START);
		ledger.open(configuration(id, 0L), "writer");
		ledger.state(id, "writer", address(1), 100, "{\"data\":[\"AA==\",\"base64\"]}", "{\"id\":1}");
		clock.set(START.plusSeconds(5));
		ledger.state(id, "writer", address(1), 100, "{\"data\":[\"AA==\",\"base64\"]}", "{\"id\":2}");
		ledger.state(id, "writer", address(1), 100, "{\"data\":[\"AQ==\",\"base64\"]}", "{\"id\":3}");
		assertThat(ledger.states(id)).hasSize(2).allSatisfy(row -> {
			assertThat(row.ambiguous()).isTrue(); assertThat(row.positionStatus()).isEqualTo("UNKNOWN_TRANSACTION_POSITION");
		});
		assertThat(ledger.states(id).stream().filter(row -> row.payload().contains("AA==")).findFirst().orElseThrow().receivedAt()).isEqualTo(START);
	}

	@Test void concurrentLeaseContenderCannotClaimUnexpiredSession() {
		var id = UUID.fromString("00000000-0000-0000-0000-000000000013");
		clock.set(START); ledger.open(configuration(id, 0L), "writer");
		assertThat(ledger.claim(id, "contender")).isFalse();
	}

	@Test void replacementSessionsCannotRefundWholeWindowReservation() {
		var id = UUID.fromString("00000000-0000-0000-0000-000000000014");
		clock.set(START); ledger.open(configuration(id, 100L), "writer");
		assertThat(ledger.reserved("alchemy-2026-10")).isGreaterThanOrEqualTo(95000100);
	}

	@Test void recoveredTransactionsDoNotCloseMissingHistoricalState() {
		var id = UUID.fromString("00000000-0000-0000-0000-000000000015");
		clock.set(START); ledger.open(configuration(id, 0L), "writer");
		ledger.interrupt(id, "writer", "DISCONNECT");
		ledger.recovered(id, "writer", address(0), "known-boundary");
		assertThat(ledger.gaps(id)).anySatisfy(row -> assertThat(row.status()).isEqualTo("UNRESOLVED_STATE_GAP"));
	}

	@Test void disabledCollectorNeverOpensTransportOrLosesLaunchReason() {
		var status=collector.start(configuration(SESSION,null));
		assertThat(status.state()).isEqualTo("BLOCKED_FOR_LAUNCH");
		assertThat(status.reason()).isEqualTo("COLLECTOR_DISABLED");
	}

	@Test void alreadyReceivedBillingBytesRemainCountedWhenLimitExceeded() {
		clock.set(START); ledger.open(configuration(SESSION,0L),"writer");
		assertThat(ledger.consume(SESSION,"writer",0,20000001,20000001,0,0)).isFalse();
		assertThat(ledger.status(SESSION).subscriptionBytes()).isEqualTo(20000001);
		assertThat(ledger.status(SESSION).reason()).isEqualTo("SUBSCRIPTION_BYTE_LIMIT");
	}

	@Test void twoAuthorizationsAtomicallyConsumeOneMonthWithoutRefundOnStop() {
		clock.set(START); var c=configuration(SESSION,0L);
		ledger.open(c,"writer"); ledger.stop(SESSION,"writer","OWNER_STOP");
		var next=configuration(UUID.fromString("00000000-0000-0000-0000-000000000099"),0L);
		var a=next.authority();
		var different=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Authority(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),
				a.otherActivityHundredths(),a.ceilingHundredths(),a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),"second-explicit-authority",a.retentionAllowed(),a.exclusiveAllocation());
		var second=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(next.sessionId(),next.version(),next.provider(),next.startsAt(),next.pools(),next.limits(),different);
		boolean accepted;
		try { accepted=ledger.open(second,"other"); }
		catch(IllegalStateException rejected) { accepted=false; }
		assertThat(accepted).as("separate owner authorization shares monthly authority").isTrue();
		assertThat(ledger.reserved(a.accountPeriod())).isEqualTo(190000000);
	}

	@Test void provisionalUnavailableFailedAndConflictingTransactionsHaveDurableDispositions() {
		clock.set(START); ledger.open(configuration(SESSION,0L),"writer");
		var pool=address(0); var signature="1".repeat(63)+"2";
		ledger.pending(SESSION,"writer",pool,signature,100,"{\"notification\":true}");
		assertThat(capture.transaction(SESSION,"writer",pool,signature,null,"{\"result\":null}",START,true)).isEqualTo("UNAVAILABLE");
		assertThat(capture.transaction(SESSION,"writer",pool,signature,"{}","{\"confirmed\":true}",START,false)).isEqualTo("UNVERIFIED_FINALITY");
		assertThat(rawStore.find(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,signature),"alchemy")).isEmpty();
		var result="{\"slot\":100,\"blockTime\":null,\"transaction\":{\"signatures\":[\""+signature+"\"],\"message\":{}},\"meta\":{\"err\":\"AccountInUse\"}}";
		assertThat(capture.transaction(SESSION,"writer",pool,signature,result,"{\"id\":1}",START,true)).isEqualTo("FAILED_TRANSACTION");
		clock.set(START.plusSeconds(1));
		assertThat(capture.transaction(SESSION,"writer",pool,signature,result.replace("AccountInUse","ChangedError"),"{\"id\":2}",clock.instant(),true)).isEqualTo("CONFLICT");
		assertThat(rawStore.find(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,signature),"alchemy").orElseThrow().payload()).isEqualTo(result);
		assertThat(ledger.dispositions(SESSION)).singleElement().satisfies(d->assertThat(d.conflicts()).isEqualTo(1));
	}

	@Test void expiredWriterCannotAdvanceCheckpointAfterAnotherHolderClaims() {
		clock.set(START); ledger.open(configuration(SESSION,0L),"writer");
		clock.set(START.plusSeconds(31)); assertThat(ledger.claim(SESSION,"next")).isTrue();
		org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.pending(SESSION,"writer",address(0),"1".repeat(64),100,"{}"))
				.isInstanceOf(IllegalStateException.class).hasMessage("CAPTURE_LEASE_LOST");
		assertThat(ledger.dispositions(SESSION)).isEmpty();
	}
	@Test void requestCapTerminatesWithoutRestartReset() {
		clock.set(START); ledger.open(configuration(SESSION,0L),"writer");
		assertThat(ledger.consume(SESSION,"writer",3750,0,0,0,0)).isTrue();
		assertThat(ledger.consume(SESSION,"writer",1,0,0,0,4000)).isFalse();
		assertThat(ledger.status(SESSION).reason()).isEqualTo("REQUEST_LIMIT");
		assertThat(ledger.status(SESSION).requests()).isEqualTo(3750);
		assertThat(ledger.open(configuration(SESSION,0L),"new-holder")).isFalse();
	}
	@Test void rpcCuCapIncludesEveryStartAndRetry() {
		clock.set(START); ledger.open(configuration(SESSION,0L),"writer");
		assertThat(ledger.consume(SESSION,"writer",1,0,0,0,15000000)).isTrue();
		assertThat(ledger.consume(SESSION,"writer",1,0,0,0,1000)).isFalse();
		assertThat(ledger.status(SESSION).reason()).isEqualTo("RPC_CU_LIMIT");
		assertThat(ledger.status(SESSION).rpcHundredths()).isEqualTo(15000000);
	}
	@Test void retainedByteCapLeavesUnfinishedWorkExplicit() {
		clock.set(START); ledger.open(configuration(SESSION,0L),"writer");
		assertThat(ledger.consume(SESSION,"writer",0,0,0,1200000000,0)).isTrue();
		assertThat(ledger.consume(SESSION,"writer",0,0,0,1,0)).isFalse();
		assertThat(ledger.status(SESSION).reason()).isEqualTo("RETAINED_BYTE_LIMIT");
		assertThat(ledger.status(SESSION).gaps()).anySatisfy(g->assertThat(g.status()).isEqualTo("UNRESOLVED_STATE_GAP"));
	}
	@Test void elapsedWindowCannotBeResetByAClaimOrStart() {
		clock.set(START); ledger.open(configuration(SESSION,0L),"writer");
		clock.set(START.plusSeconds(86400));
		assertThat(ledger.claim(SESSION,"writer")).isFalse();
		assertThat(ledger.open(configuration(SESSION,0L),"replacement")).isFalse();
		assertThat(ledger.reserved("alchemy-2026-10")).isEqualTo(95000000);
	}

	@Test void populatedV12RawAndSnapshotReadbackSurvivesV13() throws Exception {
		try(var legacy=new PostgreSQLContainer("postgres:18.6-alpine")) {
			legacy.start();
			org.flywaydb.core.Flyway.configure().dataSource(legacy.getJdbcUrl(),legacy.getUsername(),legacy.getPassword())
					.locations("classpath:db/migration").target("12").load().migrate();
			try(var connection=java.sql.DriverManager.getConnection(legacy.getJdbcUrl(),legacy.getUsername(),legacy.getPassword()); var statement=connection.createStatement()) {
				statement.executeUpdate("INSERT INTO marketdata.raw_transactions VALUES ('solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp','literal-v12','legacy','{\"literal\":true}','sha256:"+"a".repeat(64)+"',123,NULL,NULL,'2026-09-01T00:00:00Z','2026-09-01T00:00:01Z','2026-09-01T00:00:02Z','legacy-v1')");
				statement.executeUpdate("INSERT INTO marketdata.dataset_snapshots VALUES ('sha256:"+"b".repeat(64)+"','sha256:"+"b".repeat(64)+"','literal-v12','2026-09-01T00:00:00Z')");
				org.flywaydb.core.Flyway.configure().dataSource(legacy.getJdbcUrl(),legacy.getUsername(),legacy.getPassword()).locations("classpath:db/migration").load().migrate();
				try(var rows=statement.executeQuery("SELECT payload,parser_version,received_at FROM marketdata.raw_transactions WHERE transaction_value='literal-v12'")) {
					assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("{\"literal\":true}"); assertThat(rows.getString(2)).isEqualTo("legacy-v1");
					assertThat(rows.getObject(3,java.time.OffsetDateTime.class).toInstant()).isEqualTo(java.time.Instant.parse("2026-09-01T00:00:00Z"));
				}
				try(var rows=statement.executeQuery("SELECT fingerprint,canonicalization_version FROM marketdata.dataset_snapshots")) {
					assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("sha256:"+"b".repeat(64)); assertThat(rows.getString(2)).isEqualTo("literal-v12");
				}
			}
		}
	}

	@Test void publicApiCapturesRealMockHttpAndWebSocketThenReplaysOffline() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var signature="1".repeat(63)+"3";
		var payload="{\"data\":[\"AQ==\",\"base64\"]}";
		try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of(
				"{\"method\":\"accountNotification\",\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":101},\"value\":"+payload+"}}}",
				"{\"method\":\"logsNotification\",\"params\":{\"subscription\":"+(subscription-1)+",\"result\":{\"context\":{\"slot\":101},\"value\":{\"signature\":\""+signature+"\",\"err\":null,\"logs\":[]}}}}"),true)) {
			var http=mockAccounts(c,signature,false);
			try {
				settings.setEnabled(true); settings.setSecret("synthetic-provider-secret"); settings.setRpc(java.net.URI.create("http://127.0.0.1:"+http.getAddress().getPort()+"/")); settings.setWebsocket(websocket.uri());
				assertThat(api.startCapture(c).state()).isEqualTo("RUNNING"); websocket.awaitSent();
				for(var i=0;i<150 && api.captureStatus(SESSION).transactions()!=1;i++) Thread.sleep(100);
				assertThat(api.captureStatus(SESSION).transactions()).isEqualTo(1);
				assertThat(api.captureStatus(SESSION).states()).isEqualTo(16);
				assertThat(api.stopCapture(SESSION).state()).isEqualTo("STOPPED");
				var replay=api.replayCapture(SESSION);
				assertThat(replay.envelopes()).isNotEmpty().allSatisfy(e->assertThat(e.digest()).isEqualTo(PayloadFingerprint.sha256(e.payload())));
				assertThat(replay.dispositions()).singleElement().satisfies(d->assertThat(d.status()).isEqualTo("CAPTURED"));
				assertThat(replay.status().actualBilling()).isEqualTo("UNKNOWN");
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}

	@Test void inconsistentCurrentOwnerBlocksBeforeWebSocketSubscriptions() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var http=mockAccounts(c,"1".repeat(63)+"4",true);
		try {
			settings.setEnabled(true); settings.setSecret("synthetic-provider-secret"); settings.setRpc(java.net.URI.create("http://127.0.0.1:"+http.getAddress().getPort()+"/")); settings.setWebsocket(java.net.URI.create("ws://127.0.0.1:1/"));
			assertThat(api.startCapture(c).state()).isEqualTo("RUNNING");
			for(var i=0;i<100 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(20);
			assertThat(api.captureStatus(SESSION).reason()).isEqualTo("LINKAGE_OWNER_MISMATCH");
			assertThat(api.captureStatus(SESSION).requests()).isEqualTo(1);
			assertThat(api.captureStatus(SESSION).gaps()).isNotEmpty();
		} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
	}
	@Test void acknowledgedSubscriptionsPrecedeOverlapHistoryAndStateGapRemainsUnresolved() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var signature="1".repeat(63)+"6";
		ledger.open(c,"previous"); ledger.pending(SESSION,"previous",address(0),signature,100,"{}");
		var result="{\"slot\":100,\"blockTime\":null,\"transaction\":{\"signatures\":[\""+signature+"\"],\"message\":{}},\"meta\":{\"err\":null}}";
		capture.transaction(SESSION,"previous",address(0),signature,result,"{\"id\":0}",START,true);
		ledger.interrupt(SESSION,"previous","DISCONNECT"); clock.set(START.plusSeconds(31));
		var subscriptions=new java.util.concurrent.atomic.AtomicBoolean(); var historyAfterSubscriptions=new java.util.concurrent.atomic.AtomicBoolean();
		try(var websocket=new LocalCaptureWebSocket(subscription->{ subscriptions.set(true); return java.util.List.of(); },false)) {
			var http=mockAccounts(c,signature,false,subscriptions,historyAfterSubscriptions);
			try {
				settings.setEnabled(true); settings.setSecret("synthetic-provider-secret"); settings.setRpc(java.net.URI.create("http://127.0.0.1:"+http.getAddress().getPort()+"/")); settings.setWebsocket(websocket.uri());
				assertThat(api.startCapture(c).state()).isEqualTo("RUNNING"); websocket.awaitSent();
				for(var i=0;i<100 && api.captureStatus(SESSION).gaps().stream().noneMatch(g->g.status().equals("RECOVERED_TRANSACTIONS"));i++) Thread.sleep(50);
				assertThat(historyAfterSubscriptions.get()).as("all subscription acknowledgements before history fetch").isTrue();
				assertThat(api.captureStatus(SESSION).gaps()).anySatisfy(g->assertThat(g.status()).isEqualTo("RECOVERED_TRANSACTIONS"));
				assertThat(api.captureStatus(SESSION).gaps()).anySatisfy(g->assertThat(g.status()).isEqualTo("UNRESOLVED_STATE_GAP"));
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}

	@Test void simultaneousAuthorizationsCannotOverReserveSameAccountPeriod() throws Exception {
		clock.set(START); var gate=new java.util.concurrent.CountDownLatch(1);
		try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var futures=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
			for(var i=0;i<2;i++) {
				var index=i; var c=configuration(UUID.randomUUID(),0L); var a=c.authority();
				var authority=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Authority(a.accountPeriod(),a.periodEnd(),a.source(),0L,0L,
						140000000L,a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),"concurrent-authority-"+i,true,true);
				var candidate=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(c.sessionId(),c.version(),c.provider(),c.startsAt(),c.pools(),c.limits(),authority);
				futures.add(executor.submit(()->{ gate.await(); try { return ledger.open(candidate,"writer-"+index); }
					catch(IllegalStateException rejected) { assertThat(rejected).hasMessage("ACCOUNT_PERIOD_RESERVATION_REJECTED"); return false; } }));
			}
			gate.countDown(); var results=new java.util.ArrayList<Boolean>();
			for(var future:futures) results.add(future.get(10,java.util.concurrent.TimeUnit.SECONDS));
			assertThat(results).containsExactlyInAnyOrder(true,false);
			assertThat(ledger.reserved("alchemy-2026-10")).isEqualTo(95000000L);
		}
	}

	@Test void actualWebSocketQueueOverflowStopsWithDurableTransactionAndStateGaps() throws Exception {
		clock.set(START); var source=configuration(SESSION,0L); var l=source.limits();
		var limits=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Limits(l.windowSeconds(),l.requests(),l.receivedBytes(),l.retainedBytes(),l.subscriptionBytes(),
				l.rpcHundredths(),l.messageBytes(),l.responseBytes(),1,1,l.timeoutSeconds(),l.retries(),l.reconnects(),l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds());
		var c=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(source.sessionId(),source.version(),source.provider(),source.startsAt(),source.pools(),limits,source.authority());
		try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of(
				"{\"method\":\"accountNotification\",\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":101},\"value\":{\"data\":[\"AQ==\",\"base64\"]}}}}"),false)) {
			var http=mockAccounts(c,"1".repeat(63)+"7",false);
			try {
				settings.setEnabled(true); settings.setSecret("synthetic-provider-secret"); settings.setRpc(java.net.URI.create("http://127.0.0.1:"+http.getAddress().getPort()+"/")); settings.setWebsocket(websocket.uri());
				// The aggregate mailbox now bounds ACKs too: a one-byte capacity cannot establish all subscriptions.
				assertThat(api.startCapture(c).state()).isEqualTo("RUNNING");
				for(var i=0;i<100 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(50);
				var status=api.captureStatus(SESSION);
				assertThat(status.state()).isEqualTo("STOPPED"); assertThat(status.reason()).isEqualTo("QUEUE_OVERFLOW");
				assertThat(status.gaps()).anySatisfy(g->assertThat(g.status()).isEqualTo("UNRESOLVED_TRANSACTION_GAP"));
				assertThat(status.gaps()).anySatisfy(g->assertThat(g.status()).isEqualTo("UNRESOLVED_STATE_GAP"));
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}

	@Test void exhaustedSharedPeriodRejectsRequestBeforeAnyTransactionHttpSend() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var signature="1".repeat(63)+"C";
		var sent=new java.util.concurrent.atomic.AtomicInteger(); var logSubscription=new java.util.concurrent.atomic.AtomicLong();
		org.mockito.Mockito.doAnswer(invocation->{
			if((long)invocation.getArgument(7)==4000L) jdbc.sql("UPDATE marketdata.capture_periods SET known_overage_hundredths=ceiling_hundredths WHERE account_period=:period").param("period",c.authority().accountPeriod()).update();
			return invocation.callRealMethod();
		}).when(captureStore).captureAdvance(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong());
		try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of("{\"params\":{\"subscription\":"+logSubscription.get()+",\"result\":{\"context\":{\"slot\":101},\"value\":{\"signature\":\""+signature+"\"}}}}"),false,id->{ logSubscription.compareAndSet(0,10000+id); return "{\"id\":"+id+",\"result\":"+(10000+id)+"}"; })) {
			var http=mockAccounts(c,signature,false,null,null,(method,result)->{ if(method.equals("getTransaction")) sent.incrementAndGet(); return result; },200);
			try {
				launch(c,http,websocket); websocket.awaitSent();
				for(var i=0;i<200 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(30);
				assertThat(api.captureStatus(SESSION).state()).isEqualTo("STOPPED");
				assertThat(api.captureStatus(SESSION).reason()).isEqualTo("CAPTURE_RESOURCE_LIMIT");
				assertThat(sent.get()).as("durable shared-period refusal must throw before provider send").isZero();
				assertThat(api.captureStatus(SESSION).requests()).as("denied getTransaction is not a sent request").isEqualTo(33);
				assertThat(api.captureStatus(SESSION).rpcHundredths()).as("denied getTransaction is not billed RPC usage").isEqualTo(15000);
				assertThat(api.captureStatus(SESSION).receivedBytes()).isPositive();
				assertThat(api.captureStatus(SESSION).subscriptionBytes()).isPositive();
				assertThat(jdbc.sql("SELECT reserved_hundredths FROM marketdata.capture_periods WHERE account_period=:period")
						.param("period",c.authority().accountPeriod()).query(Long.class).single()).isEqualTo(95000000);
				assertThat(jdbc.sql("SELECT known_overage_hundredths=ceiling_hundredths FROM marketdata.capture_periods WHERE account_period=:period")
						.param("period",c.authority().accountPeriod()).query(Boolean.class).single()).isTrue();
				assertThat(api.captureStatus(SESSION).gaps()).hasSize(6);
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}

	@Test void writerFailureIsTerminalWithDurableGapsAndNoSuccessfulAck() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var acknowledgements=new java.util.concurrent.atomic.AtomicInteger();
		org.mockito.Mockito.doAnswer(invocation->{ if("WEBSOCKET".equals(invocation.getArgument(1))) throw new IllegalStateException("CAPTURE_WRITER_FAILURE"); return invocation.callRealMethod(); })
			.when(captureStore).envelope(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
		try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of(),false,id->{ acknowledgements.incrementAndGet(); return "{\"id\":"+id+",\"result\":"+(10000+id)+"}"; })) {
			var http=mockAccounts(c,"1".repeat(63)+"D",false);
			try {
				launch(c,http,websocket);
				for(var i=0;i<600 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(30);
				var status=api.captureStatus(SESSION); assertThat(status.state()).isEqualTo("STOPPED");
				assertThat(status.reason()).isEqualTo("CAPTURE_WRITER_FAILURE"); assertThat(status.gaps()).hasSize(6);
				assertThat(acknowledgements.get()).isEqualTo(1);
				assertThat(api.replayCapture(SESSION).envelopes()).noneMatch(e->e.kind().equals("WEBSOCKET"));
				assertThat(ledger.reserved(c.authority().accountPeriod())).isEqualTo(95000000);
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}

	@Test void loggedFullWriterPathHasDatabaseFreeCallbacksAndExactBurstAccounting() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var callbackConnections=new java.util.concurrent.atomic.AtomicInteger();
		org.mockito.Mockito.doAnswer(invocation->{ if(Thread.currentThread().getName().startsWith("HttpClient-")) callbackConnections.incrementAndGet(); return invocation.callRealMethod(); }).when(dataSource).getConnection();
		var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(PumpSwapPoolCollector.class); var oldLevel=logger.getLevel();
		var events=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>(); events.start(); logger.addAppender(events); logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
		var notifications=new java.util.concurrent.atomic.AtomicLong(); var burstStart=new java.util.concurrent.atomic.AtomicLong();
		try(var websocket=new LocalCaptureWebSocket(subscription->{ burstStart.set(System.nanoTime()); return java.util.stream.IntStream.range(0,20).mapToObj(i->{
			var text="{\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":"+(101+i)+"},\"value\":{\"data\":[\"AQ==\",\"base64\"],\"syntheticIndex\":"+i+"}}}}";
			notifications.addAndGet(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length); return text;
		}).toList(); },false)) {
			var http=mockAccounts(c,"1".repeat(63)+"E",false);
			try {
				launch(c,http,websocket); websocket.awaitSent();
				for(var i=0;i<200 && api.captureStatus(SESSION).states()!=35;i++) Thread.sleep(30);
				assertThat(api.captureStatus(SESSION).states()).isEqualTo(35); var burstElapsed=System.nanoTime()-burstStart.get(); var stopStart=System.nanoTime(); collector.stop(SESSION); var stopElapsed=System.nanoTime()-stopStart;
				var replay=api.replayCapture(SESSION); var ws=replay.envelopes().stream().filter(e->e.kind().equals("WEBSOCKET")).toList();
				assertThat(ws).hasSize(38); assertThat(replay.status().reason()).isEqualTo("OWNER_STOP");
				assertThat(replay.status().subscriptionBytes()).isEqualTo(ws.stream().mapToLong(e->e.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum());
				assertThat(replay.status().subscriptionBytes()).isGreaterThan(notifications.get());
				var storedBytes=replay.envelopes().stream().mapToLong(e->e.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum()
					+replay.states().stream().mapToLong(s->s.envelope().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+(long)s.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();
				assertThat(replay.status().retainedBytes()).as("retained accounting counts each actual envelope and snapshot representation, not mailbox helper copies").isEqualTo(storedBytes);
				assertThat(callbackConnections.get()).as("all real JDK HTTP/WS callbacks must be JDBC-free").isZero();
				var nanos=events.list.stream().filter(e->e.getMessage().startsWith("PumpSwap writer commit")).mapToLong(e->((Number)e.getArgumentArray()[0]).longValue()).sorted().toArray();
				assertThat(nanos.length).isGreaterThanOrEqualTo(38);
				System.out.printf(java.util.Locale.ROOT,"LOGGED_COLLECTOR_BENCHMARK environment=isolated-postgres18.6-alpine burst=20 snapshotRows=20 websocketEnvelopes=38 mixedLogicalWriterCommits=%d mixed_writer_p50_ms=%.3f mixed_writer_p95_ms=%.3f emit_to_all_committed_ms=%.3f committedSnapshotsPerSecond=%.3f stop_ms=%.3f attribution=isolated_not_managed_capacity%n",nanos.length,nanos[(int)Math.ceil(nanos.length*.5)-1]/1e6,nanos[(int)Math.ceil(nanos.length*.95)-1]/1e6,burstElapsed/1e6,20e9/burstElapsed,stopElapsed/1e6);
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		} finally { logger.detachAppender(events); events.stop(); logger.setLevel(oldLevel); }
	}

	@Test void partialFragmentAndInflightWriterShareOneAggregateByteBound() throws Exception {
		clock.set(START); var source=configuration(SESSION,0L); var l=source.limits();
		var limits=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Limits(l.windowSeconds(),l.requests(),l.receivedBytes(),l.retainedBytes(),l.subscriptionBytes(),l.rpcHundredths(),l.messageBytes(),l.responseBytes(),1000,1000,l.timeoutSeconds(),0,0,l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds());
		var c=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(source.sessionId(),source.version(),source.provider(),source.startsAt(),source.pools(),limits,source.authority());
		var partial=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1); var fragments=new java.util.concurrent.atomic.AtomicInteger();
		try(var lock=dataSource.getConnection()) {
			lock.setAutoCommit(false);
			try(var websocket=new LocalCaptureWebSocket(subscription->{
				try(var statement=lock.createStatement()) { statement.setQueryTimeout(3); statement.execute("LOCK TABLE marketdata.capture_envelopes IN ACCESS EXCLUSIVE MODE"); } catch(java.sql.SQLException failure) { throw new AssertionError(failure); }
				return java.util.List.of("{\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":101},\"value\":{\"padding\":\""+"x".repeat(300)+"\"}}}}",
					"{\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":102},\"value\":{\"padding\":\""+"x".repeat(1200)+"\"}}}}");
			},true,id->"{\"id\":"+id+",\"result\":"+(10000+id)+"}",1,id->java.util.List.of(),()->{
				if(fragments.incrementAndGet()==2) { partial.countDown(); try { if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("fixture partial release timed out"); } catch(InterruptedException failure) { throw new AssertionError(failure); } }
			})) {
				var http=mockAccounts(c,"1".repeat(63)+"F",false);
				try {
					launch(c,http,websocket); assertThat(partial.await(25,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); Thread.sleep(250); lock.rollback();
					for(var i=0;i<50 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(20);
					assertThat(api.captureStatus(SESSION).reason()).as("partial frame plus already-admitted inflight envelope exceeds the single 1000-byte bound before the final fragment").isEqualTo("QUEUE_OVERFLOW");
					assertThat(api.captureStatus(SESSION).gaps()).hasSize(6);
				} finally { release.countDown(); lock.rollback(); collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
			}
		}
	}

	@Test void releasedWriterAllowsInterleavedBurstAndDurableAckBeforeDeadline() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var held=new java.util.concurrent.CountDownLatch(1); var requests=new java.util.concurrent.atomic.AtomicInteger(); var account=new java.util.concurrent.atomic.AtomicLong();
		try(var lock=dataSource.getConnection()) {
			lock.setAutoCommit(false);
			try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of(),false,id->{
				var count=requests.incrementAndGet(); if(count==2) account.set(10000+id);
				if(count==3) try(var statement=lock.createStatement()) { statement.setQueryTimeout(3); statement.execute("LOCK TABLE marketdata.capture_envelopes IN ACCESS EXCLUSIVE MODE"); held.countDown(); } catch(java.sql.SQLException failure) { throw new AssertionError(failure); }
				return "{\"id\":"+id+",\"result\":"+(10000+id)+"}";
			},1,id->requests.get()==3?java.util.stream.IntStream.range(0,5).mapToObj(i->"{\"params\":{\"subscription\":"+account.get()+",\"result\":{\"context\":{\"slot\":"+(101+i)+"},\"value\":{\"data\":[\"AQ==\",\"base64\"]}}}}").toList():java.util.List.of())) {
				var http=mockAccounts(c,"1".repeat(63)+"G",false);
				try {
					launch(c,http,websocket); assertThat(held.await(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); Thread.sleep(200);
					assertThat(api.captureStatus(SESSION).states()).isEqualTo(15); lock.rollback(); websocket.awaitSent();
					for(var i=0;i<100 && api.captureStatus(SESSION).states()!=20;i++) Thread.sleep(30);
					assertThat(api.captureStatus(SESSION).states()).isEqualTo(20); assertThat(requests.get()).isEqualTo(18);
					assertThat(api.captureStatus(SESSION).state()).isEqualTo("RUNNING"); assertThat(api.captureStatus(SESSION).gaps()).isEmpty();
					collector.stop(SESSION); var ws=api.replayCapture(SESSION).envelopes().stream().filter(e->e.kind().equals("WEBSOCKET")).toList();
					assertThat(ws).hasSize(23); assertThat(api.captureStatus(SESSION).subscriptionBytes()).isEqualTo(ws.stream().mapToLong(e->e.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum());
				} finally { lock.rollback(); collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
			}
		}
	}

	@Test void leaseLossCannotReconnectOrPublishAckThroughDeadWriter() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var lost=new java.util.concurrent.CountDownLatch(1); var acknowledgements=new java.util.concurrent.atomic.AtomicInteger();
		org.mockito.Mockito.doAnswer(invocation->{
			if((long)invocation.getArgument(5)>0 && lost.getCount()>0) {
				try(var connection=dataSource.getConnection(); var statement=connection.prepareStatement("UPDATE marketdata.capture_sessions SET holder='other-live-holder' WHERE session_id=?")) { statement.setObject(1,SESSION); statement.executeUpdate(); }
				lost.countDown();
			}
			return invocation.callRealMethod();
		}).when(captureStore).captureAdvance(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong());
		try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of(),false,id->{ acknowledgements.incrementAndGet(); return "{\"id\":"+id+",\"result\":"+(10000+id)+"}"; })) {
			var http=mockAccounts(c,"1".repeat(63)+"H",false);
			try {
				launch(c,http,websocket); assertThat(lost.await(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); Thread.sleep(3500);
				assertThat(acknowledgements.get()).as("configured three reconnects do not revive a dead writer").isEqualTo(1);
				assertThat(api.captureStatus(SESSION).requests()).isEqualTo(16);
				assertThat(api.captureStatus(SESSION).state()).as("stale holder cannot invent a committed STOP/gap").isEqualTo("RUNNING");
				assertThat(api.replayCapture(SESSION).envelopes()).noneMatch(e->e.kind().equals("WEBSOCKET"));
				assertThat(ledger.reserved(c.authority().accountPeriod())).isEqualTo(95000000);
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}

	@Test void boundedStopDoesNotReleaseSingleWriterOwnershipWhileDatabaseWriteIsBlocked() throws Exception {
		clock.set(START); var source=configuration(SESSION,0L); var l=shortAckLimits();
		var c=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(source.sessionId(),source.version(),source.provider(),source.startsAt(),source.pools(),l,source.authority());
		var held=new java.util.concurrent.CountDownLatch(1);
		try(var lock=dataSource.getConnection()) {
			lock.setAutoCommit(false);
			try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of(),false,id->{
				try(var statement=lock.createStatement()) { statement.setQueryTimeout(3); statement.execute("LOCK TABLE marketdata.capture_envelopes IN ACCESS EXCLUSIVE MODE"); held.countDown(); } catch(java.sql.SQLException failure) { throw new AssertionError(failure); }
				return "{\"id\":"+id+",\"result\":"+(10000+id)+"}";
			})) {
				var http=mockAccounts(c,"1".repeat(63)+"J",false);
				try {
					launch(c,http,websocket); assertThat(held.await(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
					long waiting=0; for(var i=0;i<100 && waiting==0;i++) {
						waiting=jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%INSERT INTO marketdata.capture_envelopes%'").query(Long.class).single(); Thread.sleep(10);
					}
					assertThat(waiting).as("table lock alone does not establish that the actual writer entered its blocked INSERT").isPositive(); collector.stop(SESSION);
					var next=configuration(UUID.fromString("00000000-0000-0000-0000-0000000000b1"),0L);
					assertThat(api.startCapture(next).reason()).as("bounded join is not evidence that a blocked writer exited").isEqualTo("COLLECTOR_ALREADY_RUNNING");
					lock.rollback(); var activeField=PumpSwapPoolCollector.class.getDeclaredField("active"); activeField.setAccessible(true); var active=(java.util.concurrent.atomic.AtomicReference<?>)activeField.get(collector);
					for(var i=0;i<100 && active.get()!=null;i++) Thread.sleep(30); assertThat(active.get()).as("ownership is released only after actual coordinator and writer exit").isNull();
					assertThat(api.captureStatus(SESSION).state()).as("the2s drain expired before the lock was released; no postdeadline STOP may be fabricated").isEqualTo("RUNNING");
					assertThat(api.replayCapture(SESSION).envelopes()).noneMatch(e->e.kind().equals("CAPTURE_TERMINAL") || e.kind().equals("CAPTURE_SUMMARY") && e.payload().contains("RECOVERY_COMPLETE")); assertThat(ledger.reserved(c.authority().accountPeriod())).isEqualTo(95000000);
					clock.set(START.plusSeconds(31));
					var contender=api.startCapture(next);
					try { assertThat(contender.reason()).as("an unfinished expired historical row is not live local worker ownership; other admission constraints still apply").isNotEqualTo("COLLECTOR_ALREADY_RUNNING"); }
					finally { if(contender.state().equals("RUNNING")) collector.stop(next.sessionId()); }
				} finally { lock.rollback(); collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
			}
		}
	}

	@Test void idleWriterRenewsLeaseWithoutStartingProviderWork() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L);
		try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of(),false)) {
			var http=mockAccounts(c,"1".repeat(63)+"L",false);
			try {
				launch(c,http,websocket); websocket.awaitSent(); var requests=api.captureStatus(SESSION).requests(); clock.set(START.plusSeconds(c.limits().leaseSeconds()/3+1));
				var until=START.plusSeconds(c.limits().leaseSeconds());
				for(var i=0;i<100 && !until.isAfter(START.plusSeconds(c.limits().leaseSeconds()));i++) { until=jdbc.sql("SELECT locked_until FROM marketdata.capture_sessions WHERE session_id=:id").param("id",SESSION).query(java.time.OffsetDateTime.class).single().toInstant(); Thread.sleep(20); }
				assertThat(until).isAfter(START.plusSeconds(c.limits().leaseSeconds())); assertThat(api.captureStatus(SESSION).requests()).isEqualTo(requests);
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}

	@Test void inflightWriterCountsAgainstItemCapacityAndTerminalSignalCannotBeDropped() throws Exception {
		clock.set(START); var source=configuration(SESSION,0L); var l=source.limits();
		var limits=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Limits(l.windowSeconds(),l.requests(),l.receivedBytes(),l.retainedBytes(),l.subscriptionBytes(),l.rpcHundredths(),l.messageBytes(),l.responseBytes(),3,l.queueBytes(),l.timeoutSeconds(),0,0,l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds());
		var c=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(source.sessionId(),source.version(),source.provider(),source.startsAt(),source.pools(),limits,source.authority()); var held=new java.util.concurrent.CountDownLatch(1);
		try(var lock=dataSource.getConnection()) {
			lock.setAutoCommit(false);
			try(var websocket=new LocalCaptureWebSocket(subscription->{
				try(var statement=lock.createStatement()) { statement.setQueryTimeout(3); statement.execute("LOCK TABLE marketdata.capture_envelopes IN ACCESS EXCLUSIVE MODE"); held.countDown(); } catch(java.sql.SQLException failure) { throw new AssertionError(failure); }
				return java.util.stream.IntStream.range(0,10).mapToObj(i->"{\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":"+(101+i)+"},\"value\":{\"data\":[\"AQ==\",\"base64\"]}}}}").toList();
			},false)) {
				var http=mockAccounts(c,"1".repeat(63)+"P",false);
				try {
					launch(c,http,websocket); assertThat(held.await(25,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); Thread.sleep(250); lock.rollback();
					for(var i=0;i<100 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(20);
					var replay=api.replayCapture(SESSION); assertThat(replay.status().state()).isEqualTo("STOPPED"); assertThat(replay.status().reason()).isEqualTo("QUEUE_OVERFLOW"); assertThat(replay.status().gaps()).hasSize(6);
					var ws=replay.envelopes().stream().filter(e->e.kind().equals("WEBSOCKET")).toList(); assertThat(ws.size()).isBetween(19,21);
					assertThat(replay.status().subscriptionBytes()).as("known rejected-frame bytes remain counted outside the full payload queue").isGreaterThan(ws.stream().mapToLong(e->e.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum());
				} finally { lock.rollback(); collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
			}
		}
	}

	@Test void rawCommitRemainsIndependentWhenCheckpointTransitionFails() {
		clock.set(START); var c=configuration(SESSION,0L); var signature="1".repeat(63)+"M"; assertThat(ledger.open(c,"writer")).isTrue(); ledger.pending(SESSION,"writer",address(0),signature,100,"{}");
		org.mockito.Mockito.doThrow(new IllegalStateException("CAPTURE_CHECKPOINT_FAILURE")).when(captureStore).finish(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyBoolean());
		var result="{\"slot\":100,\"transaction\":{\"signatures\":[\""+signature+"\"],\"message\":{}},\"meta\":{\"err\":null}}";
		org.assertj.core.api.Assertions.assertThatThrownBy(()->capture.transaction(SESSION,"writer",address(0),signature,result,"{}",START,true)).hasMessage("CAPTURE_CHECKPOINT_FAILURE");
		assertThat(rawStore.find(ChainId.SOLANA_MAINNET,new TransactionId(ChainId.SOLANA_MAINNET,signature),c.provider())).isPresent().get().satisfies(row->assertThat(row.payload()).isEqualTo(result));
		assertThat(ledger.dispositions(SESSION)).singleElement().satisfies(row->assertThat(row.status()).isEqualTo("PENDING"));
	}

	@Test void captureRawReadUsesFiniteTransactionWithoutChangingOriginalStorageContract() throws Exception {
		clock.set(START); var source=configuration(SESSION,0L); var c=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(source.sessionId(),source.version(),source.provider(),source.startsAt(),source.pools(),shortAckLimits(),source.authority());
		assertThat(ledger.open(c,"writer")).isTrue(); var signature="1".repeat(63)+"N"; ledger.pending(SESSION,"writer",address(0),signature,100,"{}");
		try(var lock=dataSource.getConnection(); var statement=lock.createStatement()) {
			lock.setAutoCommit(false); statement.execute("LOCK TABLE marketdata.raw_transactions IN ACCESS EXCLUSIVE MODE");
			var result="{\"slot\":100,\"transaction\":{\"signatures\":[\""+signature+"\"],\"message\":{}},\"meta\":{\"err\":null}}";
			try { org.assertj.core.api.Assertions.assertThatThrownBy(()->capture.transaction(SESSION,"writer",address(0),signature,result,"{}",START,true)).isInstanceOf(org.springframework.dao.DataAccessException.class); }
			finally { lock.rollback(); }
			assertThat(ledger.dispositions(SESSION)).singleElement().satisfies(row->assertThat(row.status()).isEqualTo("PENDING"));
		}
	}

	@Test void knownDeliveredWssOverageReducesSharedMonthWithoutDoubleChargeOrRefund() {
		clock.set(START); var c=authority(configuration(SESSION,0L),"first",190000000);
		ledger.open(c,"writer");
		assertThat(ledger.received(SESSION,"writer",20000001,true)).isFalse();
		assertThat(ledger.received(SESSION,"writer",17,true)).isFalse();
		boolean accepted;
		try { accepted=ledger.open(authority(configuration(UUID.randomUUID(),0L),"second",190000000),"second"); }
		catch(IllegalStateException rejected) { accepted=false; }
		var acceptedReservation=accepted;
		org.assertj.core.api.SoftAssertions.assertSoftly(softly->{
			softly.assertThat(ledger.reserved(c.authority().accountPeriod())).isEqualTo(95000072L);
			softly.assertThat(acceptedReservation).as("known overage consumes shared remaining month").isFalse();
		});
	}
	@Test void freshUuidCannotResetSameAuthorizationWindowOrReservation() {
		clock.set(START); var c=configuration(SESSION,0L); ledger.open(c,"writer");
		clock.set(START.plusSeconds(31)); var next=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(UUID.randomUUID(),c.version(),c.provider(),clock.instant(),c.pools(),c.limits(),c.authority());
		org.assertj.core.api.Assertions.assertThatThrownBy(()->ledger.open(next,"replacement")).isInstanceOf(RuntimeException.class);
		assertThat(ledger.reserved(c.authority().accountPeriod())).isEqualTo(95000000L);
		assertThat(ledger.configuration(SESSION).startsAt()).isEqualTo(START);
		assertThat(jdbc.sql("SELECT count(*) FROM marketdata.capture_sessions").query(Long.class).single()).isEqualTo(1);
	}
	@Test void concurrentKnownOverageAndNewReservationKeepOneSharedLedger() throws Exception {
		clock.set(START); var c=authority(configuration(SESSION,0L),"first",190000000); ledger.open(c,"writer"); ledger.received(SESSION,"writer",20000001,true);
		var gate=new java.util.concurrent.CountDownLatch(1);
		try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var first=executor.submit(()->{ gate.await(); return ledger.received(SESSION,"writer",17,true); });
			var second=executor.submit(()->{ gate.await(); return ledger.received(SESSION,"writer",23,true); });
			var reservation=executor.submit(()->{ gate.await(); try { return ledger.open(authority(configuration(UUID.randomUUID(),0L),"second",190000000),"second"); } catch(IllegalStateException rejected) { return false; } });
			gate.countDown(); assertThat(first.get(10,java.util.concurrent.TimeUnit.SECONDS)).isFalse(); assertThat(second.get(10,java.util.concurrent.TimeUnit.SECONDS)).isFalse();
			assertThat(reservation.get(10,java.util.concurrent.TimeUnit.SECONDS)).isFalse();
			assertThat(ledger.reserved(c.authority().accountPeriod())).isEqualTo(95000164L);
		}
	}
	@Test void knownAccountExcessStopsOtherAlreadyReservedSessionBeforeNextRequest() {
		clock.set(START); var c=authority(configuration(SESSION,0L),"first",190000000); var second=authority(configuration(UUID.randomUUID(),0L),"second",190000000);
		ledger.open(c,"first"); ledger.open(second,"second"); ledger.received(SESSION,"first",20000001,true);
		assertThat(ledger.consume(second.sessionId(),"second",1,0,0,0,4000)).isFalse();
		assertThat(ledger.status(second.sessionId()).reason()).isEqualTo("ACCOUNT_COST_LIMIT");
		assertThat(ledger.status(second.sessionId()).requests()).isZero(); assertThat(ledger.reserved(c.authority().accountPeriod())).isEqualTo(190000004L);
	}
	@Test void exhaustedActualHttpRetriesPersistFetchFailureAndAvailableProvenance() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var signature="1".repeat(63)+"8";
		try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of("{\"method\":\"logsNotification\",\"params\":{\"subscription\":"+(subscription-1)+",\"result\":{\"context\":{\"slot\":101},\"value\":{\"signature\":\""+signature+"\"}}}}"),false)) {
			var http=mockAccounts(c,signature,false,null,null,(method,result)->method.equals("getTransaction")?"null":result,503);
			try {
				settings.setEnabled(true); settings.setSecret("synthetic-provider-secret"); settings.setRpc(java.net.URI.create("http://127.0.0.1:"+http.getAddress().getPort()+"/")); settings.setWebsocket(websocket.uri());
				api.startCapture(c); websocket.awaitSent();
				for(var i=0;i<150 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(100);
				org.assertj.core.api.SoftAssertions.assertSoftly(softly->{
					softly.assertThat(ledger.dispositions(SESSION).getFirst().status()).isEqualTo("FETCH_FAILURE");
					softly.assertThat(api.captureStatus(SESSION).failures()).isEqualTo(1);
				});
				assertThat(api.replayCapture(SESSION).envelopes().stream().filter(e->e.kind().equals("HTTP_getTransaction")).count()).isEqualTo(3);
				assertThat(api.captureStatus(SESSION).gaps()).anySatisfy(g->assertThat(g.reason()).isEqualTo("FETCH_FAILURE"));
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}
	@Test void recoveryDeadlineStopsWithinOnePageBeforeFurtherTransactionOperations() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var boundary="1".repeat(63)+"9";
		seedRecovery(c,boundary); var reads=new java.util.concurrent.atomic.AtomicInteger();
		var signatures=java.util.stream.IntStream.range(0,20).mapToObj(i->"1".repeat(62)+"9"+"ABCDEFGHJKLMNPQRSTUVWX".charAt(i)).toList();
		var rows=java.util.stream.IntStream.range(0,20).mapToObj(i->"{\"slot\":"+(200-i)+",\"transaction\":{\"signatures\":[\""+signatures.get(i)+"\"]}}").toList();
		try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of(),false)) {
			var http=mockAccounts(c,boundary,false,null,null,(method,result)->{
				if(method.equals("getTransactionsForAddress")) return "{\"data\":["+String.join(",",rows)+",{\"slot\":100,\"transaction\":{\"signatures\":[\""+boundary+"\"]}}],\"paginationToken\":null}";
				if(method.equals("getTransaction")) { var index=reads.getAndIncrement(); clock.set(clock.instant().plusSeconds(20)); return result.replace(boundary,signatures.get(index)); }
				return result;
			},200);
			try {
				launch(c,http,websocket); websocket.awaitSent();
				for(var i=0;i<100 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(50);
				org.assertj.core.api.SoftAssertions.assertSoftly(softly->{
					softly.assertThat(api.captureStatus(SESSION).reason()).isEqualTo("RECOVERY_TIME_LIMIT");
					softly.assertThat(reads.get()).as("one-page transaction operations share 300-second recovery deadline").isLessThanOrEqualTo(15);
				});
				assertThat(api.captureStatus(SESSION).gaps()).noneMatch(g->g.status().equals("RECOVERED_TRANSACTIONS"));
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}
	@Test void vaultMintMismatchBlocksBeforeSubscription() throws Exception { metadataBlock("mint","LINKAGE_MINT_MISMATCH"); }
	@Test void recoveryRetryCannotStartWhenItsFullTimeoutNoLongerFitsDeadline() throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var boundary="1".repeat(63)+"E"; seedRecovery(c,boundary);
		var reads=new java.util.concurrent.atomic.AtomicInteger(); var signatures=java.util.stream.IntStream.range(0,15).mapToObj(i->"1".repeat(62)+"9"+"abcdefghijkmnopq".charAt(i)).toList();
		var rows=java.util.stream.IntStream.range(0,15).mapToObj(i->"{\"slot\":"+(200-i)+",\"transaction\":{\"signatures\":[\""+signatures.get(i)+"\"]}}").toList();
		try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of(),false)) {
			var http=mockAccounts(c,boundary,false,null,null,(method,result)->{
				if(method.equals("getTransactionsForAddress")) return "{\"data\":["+String.join(",",rows)+"],\"paginationToken\":null}";
				if(method.equals("getTransaction")) { var index=reads.getAndIncrement(); clock.set(clock.instant().plusSeconds(index<14?20:11));
					return index==14?"{\"retry\":true}":result.replace(boundary,signatures.get(index)); }
				return result;
			},0);
			try {
				launch(c,http,websocket); websocket.awaitSent();
				for(var i=0;i<100 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(50);
				assertThat(api.captureStatus(SESSION).reason()).isEqualTo("RECOVERY_TIME_LIMIT");
				assertThat(reads.get()).as("no second retry start with only eight seconds remaining").isEqualTo(15);
				assertThat(api.captureStatus(SESSION).gaps()).noneMatch(g->g.status().equals("RECOVERED_TRANSACTIONS"));
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}
	@Test void vaultAuthorityMismatchBlocksBeforeSubscription() throws Exception { metadataBlock("authority","LINKAGE_AUTHORITY_MISMATCH"); }
	@Test void unsupportedVaultMetadataBlocksBeforeSubscription() throws Exception { metadataBlock("unsupported","UNSUPPORTED_VAULT_METADATA"); }
	private void metadataBlock(String variant,String expected) throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var p=c.pools().getFirst();
		var http=mockAccounts(c,"1".repeat(63)+"A",false,null,null,(method,result)->{
			if(!method.equals("getAccountInfo")) return result;
			return switch(variant) { case "mint" -> result.replace("\"mint\":\""+p.baseMint()+"\"","\"mint\":\""+address(20)+"\"");
				case "authority" -> result.replace("\"owner\":\""+p.address()+"\"","\"owner\":\""+address(20)+"\"");
				default -> result.replace("\"type\":\"account\"","\"type\":\"unsupported\""); };
		},200);
		try {
			settings.setEnabled(true); settings.setSecret("synthetic-provider-secret"); settings.setRpc(java.net.URI.create("http://127.0.0.1:"+http.getAddress().getPort()+"/")); settings.setWebsocket(java.net.URI.create("ws://127.0.0.1:1/"));
			api.startCapture(c); for(var i=0;i<100 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(50);
			assertThat(api.captureStatus(SESSION).reason()).isEqualTo(expected);
			assertThat(api.captureStatus(SESSION).requests()).isEqualTo(2);
		} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
	}
	@Test void missingHistoryBoundaryCannotBeClaimedRecovered() throws Exception { historyBlock(false); }
	@Test void nonadvancingHistoryCursorCannotBeClaimedRecovered() throws Exception { historyBlock(true); }
	private void historyBlock(boolean cursor) throws Exception {
		clock.set(START); var c=configuration(SESSION,0L); var boundary="1".repeat(63)+(cursor?"C":"B"); seedRecovery(c,boundary);
		try(var websocket=new LocalCaptureWebSocket(subscription->java.util.List.of(),false)) {
			var pages=new java.util.concurrent.atomic.AtomicInteger();
			var http=mockAccounts(c,boundary,false,null,null,(method,result)->{
				if(method.equals("getTransactionsForAddress")) { pages.incrementAndGet(); return "{\"data\":[],\"paginationToken\":"+(cursor?"\"same-cursor\"":"null")+"}"; }
				return result;
			},200);
			try {
				launch(c,http,websocket); websocket.awaitSent();
				for(var i=0;i<100 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(50);
				assertThat(api.captureStatus(SESSION).reason()).isEqualTo("RECOVERY_CONTINUITY_UNPROVED");
				assertThat(api.captureStatus(SESSION).gaps()).noneMatch(g->g.status().equals("RECOVERED_TRANSACTIONS"));
				assertThat(pages.get()).isEqualTo(cursor?2:1);
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
		}
	}
	private void seedRecovery(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,String signature) {
		ledger.open(c,"previous"); ledger.pending(SESSION,"previous",address(0),signature,100,"{}");
		capture.transaction(SESSION,"previous",address(0),signature,"{\"slot\":100,\"transaction\":{\"signatures\":[\""+signature+"\"],\"message\":{}},\"meta\":{\"err\":null}}","{\"id\":0}",START,true);
		ledger.interrupt(SESSION,"previous","DISCONNECT"); clock.set(START.plusSeconds(31));
	}
	private void launch(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,com.sun.net.httpserver.HttpServer http,LocalCaptureWebSocket websocket) {
		settings.setEnabled(true); settings.setSecret("synthetic-provider-secret"); settings.setRpc(java.net.URI.create("http://127.0.0.1:"+http.getAddress().getPort()+"/")); settings.setWebsocket(websocket.uri()); api.startCapture(c);
	}
	private static io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration authority(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,String token,long ceiling) {
		var a=c.authority(); return new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(c.sessionId(),c.version(),c.provider(),c.startsAt(),c.pools(),c.limits(),
				new io.cryptoresearch.marketdata.api.PumpSwapCapture.Authority(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),ceiling,a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),token,a.retentionAllowed(),a.exclusiveAllocation()));
	}
	private static com.sun.net.httpserver.HttpServer mockAccounts(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,String signature,boolean badOwner) throws Exception {
		return mockAccounts(c,signature,badOwner,null,null);
	}
	private static com.sun.net.httpserver.HttpServer mockAccounts(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,String signature,boolean badOwner,
			java.util.concurrent.atomic.AtomicBoolean subscriptions,java.util.concurrent.atomic.AtomicBoolean historyAfterSubscriptions) throws Exception {
		return mockAccounts(c,signature,badOwner,subscriptions,historyAfterSubscriptions,(method,result)->result,200);
	}
	private static com.sun.net.httpserver.HttpServer mockAccounts(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,String signature,boolean badOwner,
			java.util.concurrent.atomic.AtomicBoolean subscriptions,java.util.concurrent.atomic.AtomicBoolean historyAfterSubscriptions,
			java.util.function.BiFunction<String,String,String> override,int transactionHttpStatus) throws Exception {
		return mockAccounts(c,signature,badOwner,subscriptions,historyAfterSubscriptions,override,transactionHttpStatus,(request,body)->body);
	}
	private static com.sun.net.httpserver.HttpServer mockAccounts(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,String signature,boolean badOwner,
			java.util.concurrent.atomic.AtomicBoolean subscriptions,java.util.concurrent.atomic.AtomicBoolean historyAfterSubscriptions,
			java.util.function.BiFunction<String,String,String> override,int transactionHttpStatus,java.util.function.BiFunction<tools.jackson.databind.JsonNode,String,String> responseOverride) throws Exception {
		var http=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
		http.createContext("/",exchange->{
			var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
			var request=mapper.readTree(exchange.getRequestBody().readAllBytes()); var id=request.path("id").longValue();
			String result;
			if("getAccountInfo".equals(request.path("method").asText())) {
				var account=request.path("params").path(0).asText();
				var p=c.pools().stream().filter(pool->pool.accounts().contains(account)).findFirst().orElseThrow();
				var owner=account.equals(p.address())?p.program():TOKEN;
				var base=account.equals(p.baseVault()) || account.equals(p.baseMint());
				var data=account.equals(p.address())?"[\"AA==\",\"base64\"]":
						account.equals(p.baseVault()) || account.equals(p.quoteVault())?
								"{\"parsed\":{\"type\":\"account\",\"info\":{\"mint\":\""+(base?p.baseMint():p.quoteMint())+"\",\"owner\":\""+p.address()+"\"}}}":
								"{\"parsed\":{\"type\":\"mint\",\"info\":{\"decimals\":6}}}";
				result="{\"context\":{\"slot\":100},\"value\":{\"owner\":\""+(badOwner?address(20):owner)+"\",\"data\":"+data+"}}";
			} else if("getTransactionsForAddress".equals(request.path("method").asText())) {
				if(historyAfterSubscriptions!=null) historyAfterSubscriptions.set(subscriptions.get());
				result="{\"data\":[{\"slot\":100,\"transaction\":{\"signatures\":[\""+signature+"\"]}}],\"paginationToken\":null}";
			} else result="{\"slot\":101,\"blockTime\":null,\"transaction\":{\"signatures\":[\""+signature+"\"],\"message\":{}},\"meta\":{\"err\":null}}";
			var method=request.path("method").asText(); result=override.apply(method,result);
			var bytes=responseOverride.apply(request,"{\"jsonrpc\":\"2.0\",\"id\":"+id+",\"result\":"+result+"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
			var status=method.equals("getTransaction")?transactionHttpStatus:200;
			if(status==0) status=result.contains("\"retry\":true")?503:200;
			exchange.sendResponseHeaders(status,bytes.length); try(var body=exchange.getResponseBody()) { body.write(bytes); }
		}); http.start(); return http;
	}

	@TestConfiguration static class Clocks {
		@Bean @Primary MutableUtcClock captureClock() { return new MutableUtcClock(START); }
	}
}
