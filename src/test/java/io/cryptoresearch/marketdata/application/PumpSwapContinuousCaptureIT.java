package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static io.cryptoresearch.marketdata.application.PumpSwapPoolCaptureTest.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(PumpSwapContinuousCaptureIT.Clocks.class)
class PumpSwapContinuousCaptureIT {
	@Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
	@Autowired CaptureLedgerUseCase legacy;
	@Autowired javax.sql.DataSource dataSource;
	@org.springframework.test.context.bean.override.mockito.MockitoSpyBean JdbcCaptureStore captureStore;
	@org.springframework.test.context.bean.override.mockito.MockitoSpyBean org.springframework.jdbc.core.simple.JdbcClient jdbc;
	@Autowired org.springframework.transaction.PlatformTransactionManager transactions;
	@Autowired MutableUtcClock clock;
	@Autowired ContinuousCaptureLedgerUseCase continuous;
	@Autowired io.cryptoresearch.marketdata.infrastructure.persistence.JdbcContinuousCaptureStore store;
	@Autowired DeriveSealedCaptureUseCase derive;
	@Autowired CaptureEvidenceUseCase evidence;
	@Autowired PumpSwapPoolCollector collector;
	@Autowired PumpSwapContinuousCaptureCoordinator coordinator;
	@Autowired io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings settings;
	@Autowired PersistPumpSwapOperationsUseCase persistence;
	@Autowired PumpSwapOperationCanonicalizer canonicalizer;
	@org.springframework.test.context.bean.override.mockito.MockitoSpyBean DecodePumpSwapOperationsUseCase decoder;
	@org.springframework.test.context.bean.override.mockito.MockitoSpyBean PublishPumpSwapOperationsUseCase publisher;
	@org.junit.jupiter.api.BeforeEach void cleanOwnedDeploymentMetadata() {
		jdbc.sql("delete from marketdata.capture_segments").update();jdbc.sql("delete from marketdata.capture_deployments").update();clock.set(START);
	}
	static PumpSwapContinuousCapture.Configuration deployment(String key,int count,int pages) {
		var c=configuration(UUID.randomUUID(),0L);var a=c.authority();
		c=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(c.sessionId(),"capture-provenance-v5",c.provider(),START,c.pools(),c.limits(),
			new io.cryptoresearch.marketdata.api.PumpSwapCapture.Authority("local-"+key,a.periodEnd(),a.source(),0L,0,a.ceilingHundredths(),a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),"authority-"+key,true,true));
		var single=ContinuousCaptureLedgerUseCase.resources(c);var caps=new PumpSwapContinuousCapture.Resources(0,0,0,0,0,0,0);
		for(int i=0;i<count;i++)caps=caps.plus(single);
		return new PumpSwapContinuousCapture.Configuration(key,"continuous-capture-v1",c,caps,new PumpSwapContinuousCapture.Resources(0,0,0,0,0,0,0),START.plusSeconds(600),count,180,pages,60,1024L*pages,100);
	}
	String reserved(String key) {return jdbc.sql("select reserved::text from marketdata.capture_deployments where deployment_key=:key").param("key",key).query(String.class).single();}
	@Test void warmedManualAndEnrolledAccountingExecuteOnlyOneFencedStatement() {
		var counter=new java.util.concurrent.atomic.AtomicInteger();var counted=countedStore(dataSource,counter);
		var manual=deployment("manual-counter-isolated",1,1).template();legacy.open(manual,"manual-counter");
		var writer=new CaptureLedgerUseCase(counted,clock,transactions);
		var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
		tx.executeWithoutResult(s->writer.captureWrite(manual,"manual-counter",0,0,0,0,0,null,null,null));
		assertThat(counter.get()).as("first positive schema probe plus existing fence statement").isEqualTo(2);
		counter.set(0);tx.executeWithoutResult(s->writer.captureWrite(manual,"manual-counter",0,0,0,0,0,null,null,null));
		assertThat(counter.get()).as("warmed manual fence has zero extra SQL round trips").isEqualTo(1);
		var a=continuous.admit(deployment("fused-counter",1,1),"counter-holder");var c=a.segment().configuration();
		counter.set(0);tx.executeWithoutResult(s->counted.fence(c.sessionId(),a.fence().sessionHolder(),START,START.plusSeconds(60)));
		assertThat(counter.get()).as("warmed enrolled fence includes authoritative row locks in one execute").isEqualTo(1);
		counter.set(0);tx.executeWithoutResult(s->assertThat(counted.captureAdvance(c,a.fence().sessionHolder(),START,0,0,0,0,0)).isTrue());
		assertThat(counter.get()).as("warmed enrolled accounting includes its fence in one execute").isEqualTo(1);
		for(var entry:java.util.Map.of(manual,"manual-counter",c,a.fence().sessionHolder()).entrySet()) {
			var configuration=entry.getKey();var holder=entry.getValue();
			counter.set(0);assertThat(counted.claim(configuration.sessionId(),holder,START,START.plusSeconds(60))).isTrue();
			assertThat(counter.get()).as("warmed claim uses only its original statement").isEqualTo(1);
			counter.set(0);counted.observe(configuration.sessionId(),holder,0,0,START);
			assertThat(counter.get()).as("warmed observation keeps original update and configuration read only").isEqualTo(2);
		}
		var firstEnrolled=new java.util.concurrent.atomic.AtomicInteger();var fresh=countedStore(dataSource,firstEnrolled);
		fresh.fence(c.sessionId(),a.fence().sessionHolder(),START,START.plusSeconds(60));
		assertThat(firstEnrolled.get()).as("first enrolled operation lazily probes once").isEqualTo(2);
	}
	static JdbcCaptureStore countedStore(javax.sql.DataSource source,java.util.concurrent.atomic.AtomicInteger count) {
		var counted=new org.springframework.jdbc.datasource.DelegatingDataSource(source) {
			@Override public java.sql.Connection getConnection() throws java.sql.SQLException {
				var connection=super.getConnection();return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Connection.class},(p,m,args)->{
					try {var value=m.invoke(connection,args);if(value instanceof java.sql.PreparedStatement statement)return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.PreparedStatement.class},(pp,mm,aa)->{
						try {if(mm.getName().startsWith("execute"))count.incrementAndGet();return mm.invoke(statement,aa);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
					});return value;}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
				});
			}
		};
		return new JdbcCaptureStore(org.springframework.jdbc.core.simple.JdbcClient.create(counted),tools.jackson.databind.json.JsonMapper.builder().build());
	}
	@Test void takeoverCommittedDuringFusedAccountingRejectsOldHolderBeforeSessionMutation() throws Exception {
		var a=continuous.admit(deployment("fused-takeover",1,1),"first-holder");var c=a.segment().configuration();
		var counted=countedStore(dataSource,new java.util.concurrent.atomic.AtomicInteger());
		counted.continuousFence(c.sessionId(),a.fence().sessionHolder(),START);
		try(var lock=dataSource.getConnection()) {
			lock.setAutoCommit(false);
			try(var statement=lock.prepareStatement("update marketdata.capture_deployments set holder='replacement',generation=generation+1 where deployment_key=?")) {
				statement.setString(1,a.configuration().deploymentKey());statement.executeUpdate();
			}
			var write=java.util.concurrent.CompletableFuture.supplyAsync(()->counted.captureAdvance(c,a.fence().sessionHolder(),START,1,0,0,0,0));
			await(()->write.isDone() || jdbc.sql("select count(*) from pg_stat_activity where wait_event_type='Lock' and query like '%capture_sessions%' and query like '%capture_deployments%'").query(Long.class).single()>0,"fused operator either returns or actually waits on deployment fence");
			assertThat(write.isDone()).as("operator must lock authoritative deployment before touching capture session").isFalse();
			lock.commit();
			assertThatThrownBy(()->write.get(10,java.util.concurrent.TimeUnit.SECONDS)).hasRootCauseMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
			assertThat(legacy.status(c.sessionId()).requests()).isZero();
		}
	}
	ContinuousCaptureLedgerUseCase.Admission sealed(String key,int sources,int pages) {
		var d=deployment(key,2,pages);var a=continuous.admit(d,"synthetic-writer");var c=a.segment().configuration();
		for(int i=sources-1;i>=0;i--) {
			var signature=LocalContinuousCaptureProvider.signature(i);var slot=100+i;
			legacy.pending(c.sessionId(),a.fence().sessionHolder(),address(0),signature,slot,"{}");
			evidence.transaction(c.sessionId(),a.fence().sessionHolder(),address(0),signature,LocalContinuousCaptureProvider.transaction(signature,slot,i%3==0),"{}",START,true);
			if(i%2==0) {
				legacy.pending(c.sessionId(),a.fence().sessionHolder(),address(5),signature,slot,"{\"proof\":\"duplicate-pool\"}");
				evidence.transaction(c.sessionId(),a.fence().sessionHolder(),address(5),signature,LocalContinuousCaptureProvider.transaction(signature,slot,i%3==0),"{}",START,true);
			}
		}
		// Isolated finalized-source fixture: paging assertions do not claim this setup proves actual worker exit.
		jdbc.sql("update marketdata.capture_sessions set state='STOPPED',reason='CAPTURE_TIME_LIMIT' where session_id=:id").param("id",c.sessionId()).update();
		jdbc.sql("update marketdata.capture_segments set state='DERIVING',reason='CAPTURE_TIME_LIMIT',sealed_at=:at,decode_end=:end,source_count=:count where session_id=:id")
			.param("at",START.atOffset(java.time.ZoneOffset.UTC)).param("end",START.plusSeconds(60).atOffset(java.time.ZoneOffset.UTC)).param("count",sources).param("id",c.sessionId()).update();
		return a;
	}
	@Test void sealedKeysetPagesIncludeLateLowerSlotsFailedInputsAndAllPoolBindingsWithOriginalClocks() {
		var a=sealed("sealed-pages",130,3);var s=continuous.current(a);var before=jdbc.sql("select md5(string_agg(row_to_json(r)::text,'' order by chain_id,transaction_value,provider)) from marketdata.raw_transactions r").query(String.class).single();
		var first=continuous.page(a,s,"");assertThat(first.raw()).hasSize(128);assertThat(first.bindings()).hasSize(192);
		var recorded=derive.recorded(s,first);
		assertThat(recorded.transactions()).allSatisfy(t->{assertThat(t.receivedAt()).isEqualTo(START);assertThat(t.admittedAt()).isEqualTo(START);assertThat(t.ingestedAt()).isEqualTo(START);assertThat(t.rawDigest()).isEqualTo(PayloadFingerprint.sha256(t.rawResult()));});
		var expectedFirst=new DecodePumpSwapOperationsUseCase().decode(recorded);var published=persistence.persist(recorded);
		assertThat(published.result()).isEqualTo(expectedFirst);
		// Publication succeeded, cursor still empty: a process replacement must replay exactly this immutable page.
		assertThat(continuous.current(a).cursor()).isEmpty();
		derive.derive(a);s=continuous.current(a);assertThat(s.state()).isEqualTo("COMPLETE");assertThat(s.processed()).isEqualTo(130);assertThat(s.pages()).isEqualTo(2);
		assertThat(persistence.find(published.batchKey()).orElseThrow()).isEqualTo(published);
		var all=new java.util.ArrayList<io.cryptoresearch.marketdata.api.PumpSwapOperations.TransactionResult>();
		for(int i=0;i<s.pages();i++)all.addAll(persistence.find(continuous.receipt(s,i).batch()).orElseThrow().result().transactions());
		assertThat(all).hasSize(130);assertThat(all.stream().map(t->t.source().signature()).distinct().count()).isEqualTo(130);
		assertThat(all.stream().filter(t->t.status()==io.cryptoresearch.marketdata.api.PumpSwapOperations.Status.FAILED).count()).isEqualTo(44);
		assertThat(all.stream().filter(t->t.status()==io.cryptoresearch.marketdata.api.PumpSwapOperations.Status.DECODED).count()).isEqualTo(86);
		assertThat(jdbc.sql("select md5(string_agg(row_to_json(r)::text,'' order by chain_id,transaction_value,provider)) from marketdata.raw_transactions r").query(String.class).single()).isEqualTo(before);
		assertThatThrownBy(()->legacy.pending(a.segment().configuration().sessionId(),a.fence().sessionHolder(),address(0),LocalContinuousCaptureProvider.signature(131),99,"{}"))
			.hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
	}
	@Test void sourceSizeAggregationEvaluatesOnlyBoundedPerPoolKeysetInputs() {
		var a=sealed("bounded-plan",600,8);var s=continuous.current(a);var query=new java.util.concurrent.atomic.AtomicReference<String>();
		org.mockito.Mockito.doAnswer(invocation->{var sql=invocation.getArgument(0,String.class);if(sql.contains("octet_length(r.payload)") && sql.contains("capture_dispositions"))query.set(sql);return invocation.callRealMethod();})
			.when(jdbc).sql(org.mockito.ArgumentMatchers.anyString());
		var page=continuous.admitPage(a,s);assertThat(page.raw()).hasSize(128);assertThat(page.bindings()).hasSize(192);
		var plan=new org.springframework.transaction.support.TransactionTemplate(transactions).execute(tx->{
			// A finite bound must hold under valid hash/scan choices, not accidentally depend on one favorable optimizer plan.
			jdbc.sql("set local enable_indexscan=off").update();jdbc.sql("set local enable_bitmapscan=off").update();jdbc.sql("set local enable_sort=off").update();
			return jdbc.sql("explain (analyze,format json) "+query.get()).param("provider",s.configuration().provider()).param("session",s.configuration().sessionId()).param("cursor","")
				.param("pools","{"+s.configuration().pools().stream().map(io.cryptoresearch.marketdata.api.PumpSwapCapture.Pool::address).collect(java.util.stream.Collectors.joining(","))+"}").query(String.class).single();
		});
		var json=tools.jackson.databind.json.JsonMapper.builder().build().readTree(plan).get(0).path("Plan");var aggregateInputs=new java.util.ArrayList<Double>();aggregateInputs(json,aggregateInputs);
		assertThat(aggregateInputs).as("observed real PostgreSQL aggregate input work, not exact optimizer plan text").isNotEmpty().allSatisfy(rows->
			assertThat(rows).as("at most three configured pools times129 projected identities may reach a size/identity aggregate").isLessThanOrEqualTo(387d));
		var recorded=derive.recorded(s,page);assertThat(recorded.transactions().stream().map(io.cryptoresearch.marketdata.api.PumpSwapOperations.RecordedTransaction::signature).toList()).isSorted();
		assertThat(persistence.persist(recorded).result()).isEqualTo(new DecodePumpSwapOperationsUseCase().decode(recorded));
	}
	static void aggregateInputs(tools.jackson.databind.JsonNode plan,java.util.List<Double> rows) {
		if(plan.path("Node Type").asText().contains("Aggregate"))for(var input:plan.path("Plans")) {
			rows.add(input.path("Actual Rows").asDouble()*input.path("Actual Loops").asDouble());
			// A LIMIT can stop a sorted aggregate early while its eager input sort already consumed every parent.
			if(input.path("Node Type").asText().contains("Sort"))for(var sorted:input.path("Plans"))rows.add(sorted.path("Actual Rows").asDouble()*sorted.path("Actual Loops").asDouble());
		}
		for(var child:plan.path("Plans"))aggregateInputs(child,rows);
	}
	@Test void sourceCorruptionOrFinitePageDeadlineAllowanceCannotAdvanceOverMissingEvidence() {
		var a=sealed("page-backlog",129,1);
		assertThatThrownBy(()->derive.derive(a)).hasMessage("DECODE_BACKLOG");
		var s=continuous.current(a);assertThat(s.processed()).isEqualTo(128);assertThat(s.pages()).isEqualTo(1);assertThat(s.sources()-s.processed()).isEqualTo(1);
		assertThat(s.reason()).as("finite allowance failure remains a durable explicit backlog, not silently DERIVING").isEqualTo("DECODE_BACKLOG");
		var receipt=continuous.receipt(s,0);var first=persistence.find(receipt.batch()).orElseThrow();
		clock.set(START.plusSeconds(61));assertThatThrownBy(()->continuous.page(a,s,s.cursor())).hasMessage("DECODE_BACKLOG");
		assertThat(persistence.find(receipt.batch()).orElseThrow()).isEqualTo(first);
	}
	@Test void syntheticHistoricalRevisionCannotEstablishCurrentReceiptProgressAndRemainsImmutable() {
		historicalRevisionFixture("historical-revision",current->new io.cryptoresearch.marketdata.api.PumpSwapOperations.Result("synthetic-historical-v3",current.schemaVersion(),current.canonicalizationVersion(),current.pinnedSourceHashes(),current.transactions(),current.operations(),current.limitations(),current.fingerprint()));
	}
	@Test void sameVersionWrongPinnedProofCannotEstablishCurrentReceiptProgress() {
		historicalRevisionFixture("historical-pin",current->{
			var pins=new java.util.HashMap<>(current.pinnedSourceHashes());pins.put(pins.keySet().iterator().next(),"sha256:"+"0".repeat(64));
			return new io.cryptoresearch.marketdata.api.PumpSwapOperations.Result(current.decoderVersion(),current.schemaVersion(),current.canonicalizationVersion(),pins,current.transactions(),current.operations(),current.limitations(),current.fingerprint());
		});
	}
	void historicalRevisionFixture(String key,java.util.function.UnaryOperator<io.cryptoresearch.marketdata.api.PumpSwapOperations.Result> revision) {
		var a=sealed(key,2,3);var s=continuous.current(a);var recorded=derive.recorded(s,continuous.page(a,s,""));
		var current=new DecodePumpSwapOperationsUseCase().decode(recorded);
		var old=revision.apply(current);
		var prepared=canonicalizer.prepare(old);
		// Synthetic historical fixture only: real V14 constraints remain enabled; no publisher/parser hook.
		new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx->{
			for(var t:prepared.transactions())jdbc.sql("insert into marketdata.pumpswap_transaction_derivations(revision_key,chain_id,transaction_value,provider,raw_digest,content_digest,content,operation_count) values(:key,:chain,:signature,:provider,:raw,:digest,:content,:count)")
				.param("key",t.key()).param("chain",t.transaction().source().chain().value()).param("signature",t.transaction().source().signature()).param("provider",t.transaction().source().provider())
				.param("raw",t.transaction().source().rawDigest()).param("digest",t.digest()).param("content",t.content()).param("count",t.operationKeys().size()).update();
			for(var o:prepared.operations())jdbc.sql("insert into marketdata.pumpswap_operation_derivations(revision_key,canonical_key,transaction_revision_key,chain_id,transaction_value,outer_index,inner_path,leg_ordinal,content_digest,content) values(:key,:canonical,:parent,:chain,:signature,:outer,cast(:path as integer[]),:leg,:digest,:content)")
				.param("key",o.key()).param("canonical",o.canonicalKey()).param("parent",o.transactionKey()).param("chain",o.operation().chain().value()).param("signature",o.operation().signature())
				.param("outer",o.operation().position().outerIndex()).param("path","{"+o.operation().position().innerPath().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+"}")
				.param("leg",o.operation().position().legOrdinal()).param("digest",o.digest()).param("content",o.content()).update();
			jdbc.sql("insert into marketdata.pumpswap_decode_batches(batch_key,content_digest,content,transaction_count,operation_count) values(:key,:digest,:content,:transactions,:operations)")
				.param("key",prepared.key()).param("digest",prepared.digest()).param("content",prepared.content()).param("transactions",prepared.transactions().size()).param("operations",prepared.operations().size()).update();
			for(int i=0;i<prepared.transactions().size();i++)jdbc.sql("insert into marketdata.pumpswap_decode_batch_members(batch_key,ordinal,transaction_revision_key) values(:batch,:ordinal,:transaction)")
				.param("batch",prepared.key()).param("ordinal",i).param("transaction",prepared.transactions().get(i).key()).update();
		});
		var historical=persistence.find(prepared.key()).orElseThrow();
		var receipt=new io.cryptoresearch.marketdata.infrastructure.persistence.JdbcContinuousCaptureStore.Receipt("",recorded.transactions().getLast().signature(),prepared.key(),2);
		jdbc.sql("update marketdata.capture_segments set cursor_signature=:cursor,last_batch=:batch,processed_count=2,page_count=1,receipts_json=:receipt where session_id=:session")
			.param("cursor",receipt.through()).param("batch",receipt.batch()).param("receipt","["+store.encode(receipt)+"]").param("session",s.configuration().sessionId()).update();
		assertThatThrownBy(()->derive.derive(a)).hasMessage("DECODE_MEMBERSHIP_CONFLICT");
		assertThat(continuous.current(a).state()).isNotEqualTo("COMPLETE");assertThat(persistence.find(prepared.key()).orElseThrow()).isEqualTo(historical);
		// Restore only the deliberately forged owning synthetic progress fixture, never historical evidence.
		jdbc.sql("update marketdata.capture_segments set state='DERIVING',reason='CAPTURE_TIME_LIMIT',cursor_signature='',last_batch=null,processed_count=0,page_count=0,receipts_json='[]' where session_id=:session").param("session",s.configuration().sessionId()).update();
		jdbc.sql("update marketdata.capture_deployments set state='RUNNING',reason='' where deployment_key=:key").param("key",s.deployment()).update();
		derive.derive(a);var completed=continuous.current(a);assertThat(completed.state()).isEqualTo("COMPLETE");
		var fixed=persistence.find(continuous.receipt(completed,0).batch()).orElseThrow();assertThat(fixed.batchKey()).isNotEqualTo(historical.batchKey());
		assertThat(fixed.result()).isEqualTo(current);assertThat(persistence.find(prepared.key()).orElseThrow()).isEqualTo(historical);
	}
	@Test void rawSlotDigestAndUnsealedSourceCorruptionFailBeforeAnyPublication() {
		var a=sealed("bad-raw",1,1);var s=continuous.current(a);var id=a.segment().configuration().sessionId();
		jdbc.sql("update marketdata.capture_dispositions set slot=slot+1 where session_id=:id").param("id",id).update();
		assertThatThrownBy(()->continuous.page(a,s,"")).hasMessage("SEALED_SOURCE_PROOF_OR_BYTE_LIMIT");
		assertThat(continuous.current(a).processed()).isZero();
		jdbc.sql("update marketdata.capture_dispositions set slot=slot-1,raw_digest=:digest where session_id=:id").param("digest","sha256:"+"0".repeat(64)).param("id",id).update();
		assertThatThrownBy(()->continuous.page(a,s,"")).hasMessage("SEALED_SOURCE_PROOF_OR_BYTE_LIMIT");
		jdbc.sql("update marketdata.capture_segments set sealed_at=null,state='CAPTURING' where session_id=:id").param("id",id).update();
		var unsealed=continuous.current(a);
		assertThatThrownBy(()->continuous.page(a,unsealed,"")).hasMessage("DECODE_BACKLOG");
	}
	@Test void expiredDerivationHolderCannotAdmitAnotherSealedPageOrAdvanceAnyAuthority() {
		var old=sealed("expired-page",1,2);var s=continuous.current(old);var frozen=reserved(old.configuration().deploymentKey());
		clock.set(START.plusSeconds(31));var replacement=continuous.admit(old.configuration(),"new-decoder");
		assertThat(replacement.segment()).isEqualTo(s);
		assertThatThrownBy(()->continuous.page(old,s,s.cursor())).as("new page requires current generation and unexpired deployment lease")
			.hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
		assertThatThrownBy(()->continuous.progress(old,new io.cryptoresearch.marketdata.infrastructure.persistence.JdbcContinuousCaptureStore.Receipt("","next","sha256:"+"0".repeat(64),1))).hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
		assertThatThrownBy(()->continuous.complete(old,s)).hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
		assertThatThrownBy(()->continuous.terminal(old,"OWNER_STOP")).hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
		assertThat(continuous.current(replacement).processed()).isZero();assertThat(reserved(old.configuration().deploymentKey())).isEqualTo(frozen);
		assertThat(continuous.page(replacement,s,"").raw()).hasSize(1);
	}
	@Test void leaseTakeoverDuringDecodeAllowsOnlyIdenticalUncreditedOrphanPublication() throws Exception {
		orphanPublication(false);
	}
	@Test void leaseTakeoverDuringPublisherPreservesConcurrentExactPageFirstTimesAndMembership() throws Exception {
		orphanPublication(true);
	}
	void orphanPublication(boolean blockPublisher) throws Exception {
		var old=sealed(blockPublisher?"publisher-orphan":"decoder-orphan",2,2);var sealed=continuous.current(old);
		var expected=derive.recorded(sealed,continuous.page(old,sealed,""));
		var entered=new java.util.concurrent.CountDownLatch(1);var secondPublisher=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
		var first=new java.util.concurrent.atomic.AtomicBoolean(true);
		org.mockito.stubbing.Answer<Object> blocked=invocation->{
			if(first.compareAndSet(true,false)) {entered.countDown();if(!release.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("TEST_BARRIER_TIMEOUT");}
			else if(blockPublisher) {secondPublisher.countDown();if(!release.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("TEST_BARRIER_TIMEOUT");}
			return invocation.callRealMethod();
		};
		if(blockPublisher)org.mockito.Mockito.doAnswer(blocked).when(publisher).publish(org.mockito.ArgumentMatchers.any());
		else org.mockito.Mockito.doAnswer(blocked).when(decoder).decode(org.mockito.ArgumentMatchers.any());
		try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
			var stale=executor.submit(()->{try{derive.derive(old);return "unexpected success";}catch(IllegalStateException denied){return denied.getMessage();}});
			try {
				assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).as("real fixed decoder/publication boundary reached after precise page admission").isTrue();
				clock.set(START.plusSeconds(31));var current=continuous.admit(old.configuration(),"current-publisher");
				assertThat(continuous.current(current).processed()).isZero();
				assertThatThrownBy(()->continuous.admitPage(old,sealed)).hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
				// Current holder reconstructs and republishes through real PostgreSQL; no fabricated result import.
				if(blockPublisher) {
					var owned=executor.submit(()->derive.derive(current));
					assertThat(secondPublisher.await(10,java.util.concurrent.TimeUnit.SECONDS)).as("both real publisher transactions reached exact page concurrently").isTrue();
					release.countDown();owned.get(15,java.util.concurrent.TimeUnit.SECONDS);
				} else derive.derive(current);
				var completed=continuous.current(current);assertThat(completed.state()).isEqualTo("COMPLETE");
				var receipt=continuous.receipt(completed,0);var before=persistence.find(receipt.batch()).orElseThrow();
				assertThat(before.result()).isEqualTo(new DecodePumpSwapOperationsUseCase().decode(expected));
				release.countDown();assertThat(stale.get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("CONTINUOUS_CAPTURE_FENCE_LOST");
				assertThat(persistence.find(receipt.batch()).orElseThrow()).isEqualTo(before);
				assertThat(continuous.current(current)).isEqualTo(completed);
				assertThat(jdbc.sql("select count(*) from marketdata.pumpswap_decode_batches where batch_key=:key").param("key",receipt.batch()).query(Long.class).single()).isEqualTo(1);
				assertThat(before.result().transactions()).allSatisfy(t->{assertThat(t.source().receivedAt()).isEqualTo(START);assertThat(t.source().admittedAt()).isEqualTo(START);assertThat(t.source().ingestedAt()).isEqualTo(START);});
			} finally {release.countDown();}
		}
	}
	@Test void concurrentAdmissionEqualRetryCrashTakeoverAndStaleWriterNeverDoubleReserve() throws Exception {
		var d=deployment("concurrent",2,2);
		var start=new java.util.concurrent.CountDownLatch(1);
		try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
			var one=executor.submit(()->{start.await();try{return continuous.admit(d,"one");}catch(IllegalStateException rejected){return rejected;}});
			var two=executor.submit(()->{start.await();try{return continuous.admit(d,"two");}catch(IllegalStateException rejected){return rejected;}});
			start.countDown();var results=java.util.List.of(one.get(15,java.util.concurrent.TimeUnit.SECONDS),two.get(15,java.util.concurrent.TimeUnit.SECONDS));
			assertThat(results.stream().filter(ContinuousCaptureLedgerUseCase.Admission.class::isInstance).count()).isEqualTo(1);
			var admitted=(ContinuousCaptureLedgerUseCase.Admission)results.stream().filter(ContinuousCaptureLedgerUseCase.Admission.class::isInstance).findFirst().orElseThrow();
			assertThat(results.stream().filter(IllegalStateException.class::isInstance).map(e->((Exception)e).getMessage()).toList()).containsExactly("CONTINUOUS_CAPTURE_LEASE_HELD");
			var frozen=reserved(d.deploymentKey());var period=captureStore.reserved(d.template().authority().accountPeriod());
			assertThat(continuous.admit(d,admitted.fence().holder()).segment().configuration()).isEqualTo(admitted.segment().configuration());
			assertThat(reserved(d.deploymentKey())).isEqualTo(frozen);assertThat(captureStore.reserved(d.template().authority().accountPeriod())).isEqualTo(period);
			assertThat(legacy.gaps(admitted.segment().configuration().sessionId())).isEmpty();
			clock.set(START.plusSeconds(31));var replacement=continuous.admit(d,"replacement");
			assertThat(replacement.fence().generation()).isEqualTo(2);assertThat(replacement.segment().configuration()).isEqualTo(admitted.segment().configuration());
			assertThat(reserved(d.deploymentKey())).isEqualTo(frozen);assertThat(captureStore.reserved(d.template().authority().accountPeriod())).isEqualTo(period);
			assertThat(legacy.gaps(replacement.segment().configuration().sessionId())).hasSize(6).allSatisfy(g->{assertThat(g.reason()).isEqualTo("RESTART_UNFINISHED_SESSION");assertThat(g.toSlot()).isNull();});
			assertThatThrownBy(()->legacy.pending(admitted.segment().configuration().sessionId(),admitted.fence().sessionHolder(),address(0),"1".repeat(64),100,"{}"))
				.hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
			assertThatThrownBy(()->continuous.heartbeat(admitted)).hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
		}
	}
	@Test void concurrentEnrollmentCommitCannotLetEarlierAbsentBindingManualCheckOpenAfterward() throws Exception {
		var d=deployment("manual-enrollment-race",2,2);var c=d.template();var authority=c.authority();
		var manual=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(UUID.randomUUID(),c.version(),c.provider(),c.startsAt(),c.pools(),c.limits(),
			new io.cryptoresearch.marketdata.api.PumpSwapCapture.Authority(authority.accountPeriod(),authority.periodEnd(),authority.source(),0L,0,authority.ceilingHundredths(),authority.ceilingUsd(),authority.usdPerMillionCu(),authority.subscriptionCuPerByte(),"race-manual-authority",true,true));
		var inserted=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var first=new java.util.concurrent.atomic.AtomicBoolean(true);
		org.mockito.Mockito.doAnswer(invocation->{invocation.callRealMethod();if(first.compareAndSet(true,false)){inserted.countDown();if(!release.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("TEST_BARRIER_TIMEOUT");}return null;})
			.when(captureStore).insertSession(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyLong());
		try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
			var enrollment=executor.submit(()->continuous.admit(d,"enrollment"));
			try {
				assertThat(inserted.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
				var legacyStart=executor.submit(()->{try{legacy.open(manual,"manual");return "OPENED";}catch(IllegalStateException denied){return denied.getMessage();}});
				await(()->jdbc.sql("select count(*) from pg_stat_activity where pid<>pg_backend_pid() and wait_event_type='Lock' and (query like '%capture_periods%' or query like '%pg_advisory_xact_lock%')").query(Long.class).single()>0,"real PostgreSQL concurrent manual/enrollment waiter reached");
				assertThat(legacyStart.isDone()).isFalse();release.countDown();var a=enrollment.get(15,java.util.concurrent.TimeUnit.SECONDS);
				assertThat(legacyStart.get(15,java.util.concurrent.TimeUnit.SECONDS)).as("manual authority cannot open after concurrent provider enrollment committed").isEqualTo("ENROLLED_PROVIDER_REQUIRES_ADMISSION");
				assertThat(captureStore.exists(manual.sessionId())).isFalse();assertThat(captureStore.reserved(c.authority().accountPeriod())).isEqualTo(ContinuousCaptureLedgerUseCase.resources(a.segment().configuration()).reservedHundredths());
			} finally {release.countDown();}
		}
	}
	@Test void manualFirstCommitIsIncludedInEnrollmentOpeningWithoutResetOrDoubleReservation() throws Exception {
		var original=deployment("manual-first",2,2);var opening=ContinuousCaptureLedgerUseCase.resources(original.template());
		var d=new PumpSwapContinuousCapture.Configuration(original.deploymentKey(),original.version(),original.template(),original.allowances().plus(opening),opening,
			original.globalEnd(),original.maxSegments(),original.segmentSeconds(),original.maxPages(),original.decodeSeconds(),original.receiptBytes(),original.pollMillis());
		var inserted=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var first=new java.util.concurrent.atomic.AtomicBoolean(true);
		org.mockito.Mockito.doAnswer(invocation->{invocation.callRealMethod();if(first.compareAndSet(true,false)){inserted.countDown();if(!release.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("TEST_BARRIER_TIMEOUT");}return null;})
			.when(captureStore).insertSession(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyLong());
		try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
			var manual=executor.submit(()->legacy.open(d.template(),"manual-first"));
			try {
				assertThat(inserted.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();var enrollment=executor.submit(()->continuous.admit(d,"after-manual"));
				await(()->jdbc.sql("select count(*) from pg_stat_activity where pid<>pg_backend_pid() and wait_event_type='Lock' and query like '%pg_advisory_xact_lock%'").query(Long.class).single()>0,"enrollment waits behind actual manual provider lock");
				assertThat(enrollment.isDone()).isFalse();release.countDown();assertThat(manual.get(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
				var a=enrollment.get(15,java.util.concurrent.TimeUnit.SECONDS);var segment=ContinuousCaptureLedgerUseCase.resources(a.segment().configuration());
				assertThat(captureStore.reserved(d.template().authority().accountPeriod())).isEqualTo(Math.addExact(opening.reservedHundredths(),segment.reservedHundredths()));
				assertThat(store.lockProvider(d.template().provider()).reserved()).isEqualTo(opening.plus(segment));
				assertThat(captureStore.exists(d.template().sessionId())).isTrue();
				assertThat(continuous.admit(d,"after-manual").segment()).isEqualTo(a.segment());
				assertThat(captureStore.reserved(d.template().authority().accountPeriod())).isEqualTo(Math.addExact(opening.reservedHundredths(),segment.reservedHundredths()));
			} finally {release.countDown();}
		}
	}
	@Test void ambientPublicAndEnrolledStartsRejectWithoutChangingCallerRollbackOrAdmission() throws Exception {
		var d=deployment("ambient-public",2,2);var a=continuous.admit(d,"owned");
		var frozen=reserved(d.deploymentKey());var before=captureStore.reserved(d.template().authority().accountPeriod());
		try(var rpc=new LocalContinuousCaptureProvider(d.template())) {
		settings.setEnabled(true);settings.setSecret("synthetic-ambient-secret");settings.setRpc(rpc.uri());settings.setWebsocket(java.net.URI.create("ws://127.0.0.1:1/"));
		try {
		for(var isolation:java.util.List.of(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED,org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ)) {
			var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);tx.setIsolationLevel(isolation);
			tx.executeWithoutResult(status->{
				jdbc.sql("update marketdata.capture_periods set known_overage_hundredths=7 where account_period=:period").param("period",d.template().authority().accountPeriod()).update();
				org.mockito.Mockito.clearInvocations(captureStore);
				assertThat(collector.start(d.template()).reason()).as("caller transaction is rejected before launch validation or ledger").isEqualTo("CAPTURE_REQUIRES_NO_TRANSACTION");
				assertThat(collector.startEnrolled(a).reason()).isEqualTo("CAPTURE_REQUIRES_NO_TRANSACTION");
				org.mockito.Mockito.verifyNoInteractions(captureStore);
				assertThat(jdbc.sql("show transaction_isolation").query(String.class).single()).isEqualTo(isolation==2?"read committed":"repeatable read");
				assertThat(jdbc.sql("select known_overage_hundredths from marketdata.capture_periods where account_period=:period").param("period",d.template().authority().accountPeriod()).query(Long.class).single()).isEqualTo(7);
				status.setRollbackOnly();
			});
		}
		assertThat(jdbc.sql("select known_overage_hundredths from marketdata.capture_periods where account_period=:period").param("period",d.template().authority().accountPeriod()).query(Long.class).single()).isZero();
		assertThat(reserved(d.deploymentKey())).isEqualTo(frozen);assertThat(captureStore.reserved(d.template().authority().accountPeriod())).isEqualTo(before);
		assertThat(captureStore.exists(d.template().sessionId())).isFalse();assertThatThrownBy(()->collector.lifecycle(d.template().sessionId())).hasMessage("CAPTURE_LIFECYCLE_UNAVAILABLE");
		assertThatThrownBy(()->collector.lifecycle(a.segment().configuration().sessionId())).hasMessage("CAPTURE_LIFECYCLE_UNAVAILABLE");
		assertThat(rpc.requests.get()).as("enabled local provider receives no I/O from rejected caller transactions").isZero();
		} finally {settings.setEnabled(false);}
		}
	}
	@Test void directRepeatableReadManualAdmissionCannotUseStaleBindingSnapshot() {
		var d=deployment("direct-rr",1,1);var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
		tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
		tx.executeWithoutResult(status->{assertThatThrownBy(()->legacy.open(d.template(),"rr")).hasMessage("CAPTURE_ADMISSION_REQUIRES_READ_COMMITTED");status.setRollbackOnly();});
		assertThat(captureStore.exists(d.template().sessionId())).isFalse();
		assertThat(jdbc.sql("select count(*) from marketdata.capture_periods where account_period=:period").param("period",d.template().authority().accountPeriod()).query(Long.class).single()).isZero();
	}
	@Test void providerAdmissionWaitTimesOutWithinFiniteBoundWithoutReservationAndThenRecovers() throws Exception {
		var d=deployment("provider-lock-timeout",1,1);var held=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
		try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
			var blocker=executor.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
				jdbc.sql("select pg_advisory_xact_lock(1129464656,hashtext(:provider))").param("provider",d.template().provider()).query((r,n)->0).single();held.countDown();
				try{if(!release.await(20,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("TEST_BARRIER_TIMEOUT");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return null;
			}));
			try {
				assertThat(held.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();long started=System.nanoTime();
				assertThatThrownBy(()->continuous.admit(d,"waiter")).as("provider admission cannot pass the held shared lock").isInstanceOf(org.springframework.dao.DataAccessException.class);
				assertThat(java.time.Duration.ofNanos(System.nanoTime()-started)).isBetween(java.time.Duration.ofSeconds(9),java.time.Duration.ofSeconds(15));
				assertThat(jdbc.sql("select count(*) from marketdata.capture_deployments where deployment_key=:key").param("key",d.deploymentKey()).query(Long.class).single()).isZero();
				assertThat(jdbc.sql("select count(*) from marketdata.capture_periods where account_period=:period").param("period",d.template().authority().accountPeriod()).query(Long.class).single()).isZero();
			} finally {release.countDown();blocker.get(5,java.util.concurrent.TimeUnit.SECONDS);}
		}
		var a=continuous.admit(d,"after-release");assertThat(a.segment().ordinal()).isEqualTo(1);
		assertThat(captureStore.reserved(d.template().authority().accountPeriod())).isEqualTo(ContinuousCaptureLedgerUseCase.resources(a.segment().configuration()).reservedHundredths());
	}
	@Test void knownPreviousBoundaryPositionsRemainExplicitWithoutInventingUpperBounds() {
		var old=sealed("known-boundary",3,2);derive.derive(old);clock.set(START.plusSeconds(181));
		var next=continuous.admit(old.configuration(),"successor");var gaps=legacy.gaps(next.segment().configuration().sessionId());
		assertThat(gaps).hasSize(6).allSatisfy(g->{assertThat(g.toSlot()).isNull();assertThat(g.closedAt()).isNull();assertThat(g.status()).startsWith("UNRESOLVED_");});
		assertThat(gaps).filteredOn(g->g.pool().equals(address(0)) || g.pool().equals(address(5))).hasSize(4)
			.allSatisfy(g->{assertThat(g.fromSlot()).as("verified preceding raw-backed checkpoint must remain a known lower boundary").isEqualTo(102L);assertThat(g.boundarySignature()).isEqualTo(LocalContinuousCaptureProvider.signature(2));});
		assertThat(gaps).filteredOn(g->g.pool().equals(address(10))).hasSize(2).allSatisfy(g->{assertThat(g.fromSlot()).isNull();assertThat(g.boundarySignature()).isNull();});
	}
	@Test void renamedDeploymentPeriodAuthorityAndManualSessionCannotReopenTerminalAllocation() {
		var d=deployment("terminal",1,1);var a=continuous.admit(d,"owner");var frozen=reserved(d.deploymentKey());
		continuous.terminal(a,"DEPLOYMENT_RESOURCE_LIMIT");
		assertThat(continuous.admit(d,"owner").terminal()).isEqualTo("DEPLOYMENT_RESOURCE_LIMIT");assertThat(reserved(d.deploymentKey())).isEqualTo(frozen);
		var alias=new PumpSwapContinuousCapture.Configuration("new-name",d.version(),d.template(),d.allowances(),d.opening(),d.globalEnd(),d.maxSegments(),d.segmentSeconds(),d.maxPages(),d.decodeSeconds(),d.receiptBytes(),d.pollMillis());
		assertThatThrownBy(()->continuous.admit(alias,"new-holder")).hasMessage("CONTINUOUS_CONFIGURATION_CONFLICT");
		var c=d.template();var old=c.authority();var other=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(UUID.randomUUID(),c.version(),c.provider(),c.startsAt(),c.pools(),c.limits(),
			new io.cryptoresearch.marketdata.api.PumpSwapCapture.Authority("alias-period",old.periodEnd(),old.source(),0L,0,old.ceilingHundredths(),old.ceilingUsd(),old.usdPerMillionCu(),old.subscriptionCuPerByte(),"alias-owner",true,true));
		assertThatThrownBy(()->legacy.open(other,"manual")).hasMessage("ENROLLED_PROVIDER_REQUIRES_ADMISSION");
		assertThat(captureStore.exists(other.sessionId())).isFalse();
		var periodAlias=new PumpSwapContinuousCapture.Configuration(d.deploymentKey(),d.version(),other,d.allowances(),d.opening(),d.globalEnd(),d.maxSegments(),d.segmentSeconds(),d.maxPages(),d.decodeSeconds(),d.receiptBytes(),d.pollMillis());
		assertThatThrownBy(()->continuous.admit(periodAlias,"new-holder")).hasMessage("CONTINUOUS_CONFIGURATION_CONFLICT");
		assertThat(jdbc.sql("select count(*) from marketdata.capture_periods where account_period='alias-period'").query(Long.class).single()).isZero();
	}
	@Test void unknownHistoricalOpeningRollsBackEnrollmentAndWholeReservation() {
		var d=deployment("historical",1,1);legacy.open(d.template(),"old-writer");var before=captureStore.reserved(d.template().authority().accountPeriod());
		assertThatThrownBy(()->continuous.admit(d,"owner")).hasMessage("UNKNOWN_DEPLOYMENT_OPENING_USAGE");
		assertThat(jdbc.sql("select count(*) from marketdata.capture_deployments").query(Long.class).single()).isZero();
		assertThat(captureStore.reserved(d.template().authority().accountPeriod())).isEqualTo(before);
	}
	@Test void unknownHistoricalDimensionCannotBeSilentlyCoalescedToZero() {
		var d=deployment("unknown-dimension",2,1);legacy.open(d.template(),"old-writer");
		jdbc.sql("update marketdata.capture_sessions set configuration_json=(configuration_json::jsonb #- '{limits,requests}')::text where session_id=:id").param("id",d.template().sessionId()).update();
		var known=ContinuousCaptureLedgerUseCase.resources(d.template());
		var enrollment=new PumpSwapContinuousCapture.Configuration(d.deploymentKey(),d.version(),d.template(),d.allowances(),known,d.globalEnd(),d.maxSegments(),d.segmentSeconds(),d.maxPages(),d.decodeSeconds(),d.receiptBytes(),d.pollMillis());
		assertThatThrownBy(()->continuous.admit(enrollment,"owner")).hasMessage("UNKNOWN_DEPLOYMENT_OPENING_USAGE");
		assertThat(jdbc.sql("select count(*) from marketdata.capture_deployments").query(Long.class).single()).isZero();
	}
	@Test void everyReservationDimensionMustBePresentInDatabase() {
		var a=continuous.admit(deployment("null-dimension",1,1),"holder");
		assertThatThrownBy(()->jdbc.sql("update marketdata.capture_deployments set reserved[1]=null where deployment_key=:key").param("key",a.configuration().deploymentKey()).update())
			.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}
	@Test void expiredDeploymentCannotWriteDeliveredBytesThroughLegacyAccountingSeam() {
		var a=continuous.admit(deployment("old-accounting",1,1),"holder");var id=a.segment().configuration().sessionId();
		jdbc.sql("update marketdata.capture_sessions set locked_until=:until where session_id=:id").param("until",START.plusSeconds(600).atOffset(java.time.ZoneOffset.UTC)).param("id",id).update();
		clock.set(START.plusSeconds(31));var before=legacy.status(id);
		assertThatThrownBy(()->legacy.received(id,a.fence().sessionHolder(),100,true)).hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
		assertThat(legacy.status(id)).isEqualTo(before);
	}
	@Test void unsealedPastEndCannotInventNaturalSuccessOrReserveSuccessor() {
		var d=deployment("past-end",2,1);var a=continuous.admit(d,"crashed");var frozen=reserved(d.deploymentKey());clock.set(START.plusSeconds(181));
		var resumed=continuous.admit(d,"replacement");assertThat(resumed.terminal()).isEqualTo("INCOMPLETE_TERMINAL");
		assertThat(reserved(d.deploymentKey())).isEqualTo(frozen);assertThat(store.segment(d.deploymentKey(),1).sealedAt()).isNull();
		assertThat(jdbc.sql("select count(*) from marketdata.capture_segments").query(Long.class).single()).isEqualTo(1);
	}

	@Test void enrolledProviderCannotBypassAllocationThroughOrdinaryManualStart() {
		clock.set(START);
		var c=configuration(UUID.randomUUID(),0L);
		var resources=new PumpSwapContinuousCapture.Resources(10000,3000000000L,60000000,3600000000L,45000000,285000000,259200);
		var opening=new PumpSwapContinuousCapture.Resources(0,0,0,0,0,0,0);
		var deployment=new PumpSwapContinuousCapture.Configuration("manual-guard","continuous-capture-v1",c,resources,opening,START.plusSeconds(1000),3,180,10,60,10240,1000);
		new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx->{
			captureStore.period(c,CaptureBudget.ceiling(c),0);
			jdbc.sql("""
				insert into marketdata.capture_deployments(deployment_key,provider,account_period,owner_authorization,
				configuration_json,configuration_digest,allowances,reserved,global_end,max_segments,holder,generation,locked_until)
				values('manual-guard',:provider,:period,:authorization,:config,:digest,
				array[10000,3000000000,60000000,3600000000,45000000,285000000,259200]::bigint[],
				array[0,0,0,0,0,0,0]::bigint[],:end,3,'deployment-holder',1,:until)
				""").param("provider",c.provider()).param("period",c.authority().accountPeriod()).param("authorization",c.authority().ownerAuthorization())
				.param("config",captureStore.encode(deployment)).param("digest",PayloadFingerprint.sha256(captureStore.encode(deployment)))
				.param("end",START.plusSeconds(1000).atOffset(java.time.ZoneOffset.UTC))
				.param("until",START.plusSeconds(30).atOffset(java.time.ZoneOffset.UTC)).update();
		});
		try {
			assertThatThrownBy(()->legacy.open(c,"manual-holder"))
					.as("enrolled provider must reject even an exact copied session authority without internal fenced admission")
					.isInstanceOf(IllegalStateException.class).hasMessage("ENROLLED_PROVIDER_REQUIRES_ADMISSION");
			assertThat(captureStore.exists(c.sessionId())).isFalse();
			assertThat(captureStore.reserved(c.authority().accountPeriod())).isZero();
		} finally {
			jdbc.sql("delete from marketdata.capture_deployments where deployment_key='manual-guard'").update();
		}
	}
	@Test void actualWorkerExitNaturalSealAndDerivationPrecedeClippedSuccessorWithExplicitBoundaryGaps() throws Exception {
		var original=deployment("actual-natural",2,2);
		var d=new PumpSwapContinuousCapture.Configuration(original.deploymentKey(),original.version(),original.template(),original.allowances(),original.opening(),START.plusSeconds(210),2,180,2,60,2048,100);
		// Separate immutable V5 identity from the 0..599 source-size fixture in this owning database.
		var signature=LocalContinuousCaptureProvider.signature(1500);
		try(var rpc=new LocalContinuousCaptureProvider(d.template());var websocket=new LocalCaptureWebSocket(2,(index,id)->java.util.List.of(LocalContinuousCaptureProvider.notification(id,signature,100)),index->{})) {
			rpc.transactions.put(signature,LocalContinuousCaptureProvider.transaction(signature,100,false));
			settings.setEnabled(true);settings.setContinuousEnabled(true);settings.setSecret("synthetic-continuous-secret");settings.setRpc(rpc.uri());settings.setWebsocket(websocket.uri());
			var worker=Thread.ofVirtual().start(()->coordinator.run(d));
			try {
				websocket.awaitSent();var id=ContinuousCaptureLedgerUseCase.sessionId(d.deploymentKey(),1);
				await(()->legacy.status(id).transactions()==1,"first real finalized capture committed");
				var holder=jdbc.sql("select holder from marketdata.capture_deployments where deployment_key=:key").param("key",d.deploymentKey()).query(String.class).single();
				var a=continuous.admit(d,holder);var before=reserved(d.deploymentKey());
				assertThat(collector.lifecycle(id).workersExited()).isFalse();
				assertThatThrownBy(()->continuous.seal(a,collector.lifecycle(id))).hasMessage("INCOMPLETE_TERMINAL");
				assertThat(store.segment(d.deploymentKey(),1).sealedAt()).isNull();assertThat(reserved(d.deploymentKey())).isEqualTo(before);
				for(int second=10;second<=180;second+=10){clock.set(START.plusSeconds(second));Thread.sleep(200);}
				await(()->jdbc.sql("select count(*) from marketdata.capture_segments where deployment_key=:key and ordinal=2").param("key",d.deploymentKey()).query(Long.class).single()==1,"verified natural completion admitted successor");
				var first=store.segment(d.deploymentKey(),1);var next=store.segment(d.deploymentKey(),2);
				assertThat(first.state()).isEqualTo("COMPLETE");assertThat(first.reason()).isEqualTo("CAPTURE_TIME_LIMIT");assertThat(first.processed()).isEqualTo(1);
				assertThat(next.configuration().limits().windowSeconds()).isEqualTo(30);assertThat(CaptureLedgerUseCase.end(next.configuration())).isEqualTo(d.globalEnd());
				assertThat(legacy.gaps(next.configuration().sessionId())).filteredOn(g->g.reason().startsWith("SEGMENT_BOUNDARY:")).hasSize(6)
					.allSatisfy(g->{assertThat(g.reason()).contains(id.toString(),next.configuration().sessionId().toString());assertThat(g.toSlot()).isNull();assertThat(g.status()).startsWith("UNRESOLVED_");});
				await(()->legacy.status(next.configuration().sessionId()).transactions()==1,"successor real finalized capture committed");
				for(int second=190;second<=210;second+=10){clock.set(START.plusSeconds(second));Thread.sleep(200);}
				worker.join(java.time.Duration.ofSeconds(15));assertThat(worker.isAlive()).isFalse();
				assertThat(collector.lifecycle(next.configuration().sessionId()).workersExited()).isTrue();
				assertThat(collector.lifecycle(next.configuration().sessionId()).terminalCommitted()).isTrue();
				assertThat(store.segment(d.deploymentKey(),2).state()).isEqualTo("COMPLETE");
				assertThat(jdbc.sql("select reason from marketdata.capture_deployments where deployment_key=:key").param("key",d.deploymentKey()).query(String.class).single()).isEqualTo("DEPLOYMENT_GLOBAL_END");
				var expected=ContinuousCaptureLedgerUseCase.resources(first.configuration()).plus(ContinuousCaptureLedgerUseCase.resources(next.configuration()));
				assertThat(legacy.reserved(d.template().authority().accountPeriod())).isEqualTo(expected.reservedHundredths());
				var batch=persistence.find(continuous.receipt(store.segment(d.deploymentKey(),2),0).batch()).orElseThrow();
				assertThat(batch.result().transactions().getFirst().source().receivedAt()).isEqualTo(START);
			} finally {worker.interrupt();worker.join(java.time.Duration.ofSeconds(12));settings.setEnabled(false);settings.setContinuousEnabled(false);}
		}
	}
	@Test void coordinatorShutdownDrainsOwnedCollectorBeforePersistingOwnerStop() throws Exception {
		var d=deployment("shutdown-coordinator",2,2);var id=ContinuousCaptureLedgerUseCase.sessionId(d.deploymentKey(),1);
		try(var rpc=new LocalContinuousCaptureProvider(d.template());var ws=new LocalCaptureWebSocket(2,(index,subscription)->java.util.List.of(),index->{})) {
			enableCoordinator(rpc,ws);coordinator.start(d);
			try {
				ws.awaitSent();var frozen=reserved(d.deploymentKey());var period=legacy.reserved(d.template().authority().accountPeriod());
				coordinator.shutdown();
				await(()->collector.lifecycle(id).workersExited(),"shutdown waits for actual collector and writer exit");
				assertThat(collector.lifecycle(id).terminalCommitted()).as("valid-fence shutdown drains the admitted writer before deployment becomes terminal").isTrue();
				assertThat(legacy.status(id).reason()).isEqualTo("OWNER_STOP");
				await(()->deploymentReason(d).equals("OWNER_STOP"),"owner stop is durable");
				assertTerminalWithoutSuccessor(d,frozen,period,"OWNER_STOP");
			} finally {coordinator.shutdown();collector.stop(id);disableCoordinator();}
		}
	}
	@Test void coordinatorTerminalProtocolFailureRetainsCauseAndCannotReserveSuccessor() throws Exception {
		var d=deployment("protocol-coordinator",2,2);var id=ContinuousCaptureLedgerUseCase.sessionId(d.deploymentKey(),1);
		try(var rpc=new LocalContinuousCaptureProvider(d.template());var ws=new LocalCaptureWebSocket(2,(index,subscription)->java.util.List.of(),index->{})) {
			enableCoordinator(rpc,ws);coordinator.start(d);
			try {
				ws.awaitSent();var frozen=reserved(d.deploymentKey());var period=legacy.reserved(d.template().authority().accountPeriod());
				ws.disconnect(1008,"synthetic policy refusal");
				await(()->collector.lifecycle(id).workersExited(),"terminal policy failure exits collector and writer");
				var cause=legacy.status(id).reason();assertThat(cause).isEqualTo("DISCONNECT");
				assertThat(collector.lifecycle(id).terminalCommitted()).isTrue();assertThat(collector.lifecycle(id).writerFailed()).isFalse();
				await(()->deploymentReason(d).equals(cause),"coordinator retains the collector primary cause");
				assertTerminalWithoutSuccessor(d,frozen,period,cause);
			} finally {coordinator.shutdown();collector.stop(id);disableCoordinator();}
		}
	}
	@Test void coordinatorBlockedWriterShutdownRetainsOwnershipAndIncompleteEvidenceWithoutSuccessor() throws Exception {
		var original=deployment("blocked-writer-coordinator",2,2);var c=original.template();var l=c.limits();
		// The normal captureWrite SQL timeout is10s; this fixture's original finite drain is2s.
		// Thus an actually blocked writer outlives cleanup without altering production deadlines.
		var limits=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Limits(l.windowSeconds(),l.requests(),l.receivedBytes(),l.retainedBytes(),l.subscriptionBytes(),l.rpcHundredths(),l.messageBytes(),l.responseBytes(),l.queueItems(),l.queueBytes(),1,l.retries(),l.reconnects(),l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds());
		var template=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(c.sessionId(),c.version(),c.provider(),c.startsAt(),c.pools(),limits,c.authority());
		var d=new PumpSwapContinuousCapture.Configuration(original.deploymentKey(),original.version(),template,original.allowances(),original.opening(),original.globalEnd(),original.maxSegments(),original.segmentSeconds(),original.maxPages(),original.decodeSeconds(),original.receiptBytes(),original.pollMillis());
		var id=ContinuousCaptureLedgerUseCase.sessionId(d.deploymentKey(),1);
		try(var rpc=new LocalContinuousCaptureProvider(d.template());var ws=new LocalCaptureWebSocket(2,(index,subscription)->java.util.List.of(),index->{});var lock=dataSource.getConnection()) {
			enableCoordinator(rpc,ws);coordinator.start(d);lock.setAutoCommit(false);
			try {
				ws.awaitSent();var frozen=reserved(d.deploymentKey());var period=legacy.reserved(d.template().authority().accountPeriod());
				var holder=jdbc.sql("select holder from marketdata.capture_deployments where deployment_key=:key").param("key",d.deploymentKey()).query(String.class).single();
				var owned=continuous.admit(d,holder);
				try(var statement=lock.createStatement()){statement.setQueryTimeout(3);statement.execute("LOCK TABLE marketdata.capture_envelopes IN ACCESS EXCLUSIVE MODE");}
				ws.send(java.util.List.of(LocalContinuousCaptureProvider.notification(ws.accountSubscription(),LocalContinuousCaptureProvider.signature(1600),100)));
				await(()->jdbc.sql("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%capture_envelopes%'").query(Long.class).single()>0,"actual PostgreSQL writer INSERT is blocked");
				coordinator.shutdown();Thread.sleep(200);
				assertThat(collector.lifecycle(id).workersExited()).as("interrupting coordinator is not proof blocked writer exited").isFalse();
				assertThat(collector.lifecycle(id).terminalCommitted()).isFalse();assertThat(store.segment(d.deploymentKey(),1).sealedAt()).isNull();
				Thread.sleep(java.util.concurrent.TimeUnit.SECONDS.toMillis(limits.timeoutSeconds()+2L));
				assertThat(jdbc.sql("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%capture_envelopes%'").query(Long.class).single())
					.as("actual INSERT remains blocked beyond the original finite drain").isPositive();
				assertThat(collector.lifecycle(id).workersExited()).as("expired finite join still cannot release a PostgreSQL-blocked writer").isFalse();
				assertThat(collector.startEnrolled(owned).reason()).as("collector ownership survives incomplete writer shutdown").isEqualTo("COLLECTOR_ALREADY_RUNNING");
				assertThat(legacy.status(id).state()).as("uncommitted terminal capture remains explicitly incomplete").isEqualTo("RUNNING");
				lock.rollback();await(()->collector.lifecycle(id).workersExited(),"blocked writer eventually exits without false success");
				assertThat(legacy.replay(id).envelopes()).noneMatch(e->e.kind().equals("CAPTURE_TERMINAL"));
				assertThat(collector.lifecycle(id).terminalCommitted()).isFalse();assertThat(collector.lifecycle(id).writerFailed()).isTrue();
				assertThat(collector.lifecycle(id).reason()).isEqualTo("OWNER_STOP");
				assertThatThrownBy(()->legacy.pending(id,owned.fence().sessionHolder(),address(0),LocalContinuousCaptureProvider.signature(1601),101,"{}"))
					.as("deployment terminal fence prohibits late admitted capture writes").hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
				await(()->!deploymentReason(d).isEmpty(),"incomplete shutdown records terminal deployment");
				assertTerminalWithoutSuccessor(d,frozen,period,"OWNER_STOP");
			} finally {lock.rollback();coordinator.shutdown();collector.stop(id);disableCoordinator();}
		}
	}
	void enableCoordinator(LocalContinuousCaptureProvider rpc,LocalCaptureWebSocket ws) {
		settings.setEnabled(true);settings.setContinuousEnabled(true);settings.setSecret("synthetic-repair-secret");settings.setRpc(rpc.uri());settings.setWebsocket(ws.uri());
	}
	void disableCoordinator(){settings.setEnabled(false);settings.setContinuousEnabled(false);}
	String deploymentReason(PumpSwapContinuousCapture.Configuration d){return jdbc.sql("select reason from marketdata.capture_deployments where deployment_key=:key").param("key",d.deploymentKey()).query(String.class).single();}
	void assertTerminalWithoutSuccessor(PumpSwapContinuousCapture.Configuration d,String frozen,long period,String cause) {
		assertThat(deploymentReason(d)).isEqualTo(cause);var segment=store.segment(d.deploymentKey(),1);
		assertThat(segment.state()).isEqualTo("TERMINAL");assertThat(segment.reason()).isEqualTo(cause);assertThat(segment.sealedAt()).isNull();assertThat(segment.pages()).isZero();
		assertThat(jdbc.sql("select count(*) from marketdata.capture_segments where deployment_key=:key").param("key",d.deploymentKey()).query(Long.class).single()).isEqualTo(1);
		assertThat(reserved(d.deploymentKey())).isEqualTo(frozen);assertThat(legacy.reserved(d.template().authority().accountPeriod())).isEqualTo(period);
		assertThat(continuous.admit(d,"must-not-relaunch").terminal()).isEqualTo(cause);
		assertThat(reserved(d.deploymentKey())).isEqualTo(frozen);
	}
	static void await(java.util.function.BooleanSupplier condition,String reason) throws Exception {
		var deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(25);
		while(!condition.getAsBoolean() && System.nanoTime()<deadline)Thread.sleep(25);
		assertThat(condition.getAsBoolean()).as(reason).isTrue();
	}
	@TestConfiguration static class Clocks {
		@Bean @Primary MutableUtcClock testClock() {return new MutableUtcClock(START);}
	}
}
