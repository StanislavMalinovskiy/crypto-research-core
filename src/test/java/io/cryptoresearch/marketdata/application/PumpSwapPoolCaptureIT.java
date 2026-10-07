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
				assertThat(api.startCapture(c).state()).isEqualTo("RUNNING"); websocket.awaitSent();
				for(var i=0;i<100 && api.captureStatus(SESSION).state().equals("RUNNING");i++) Thread.sleep(50);
				var status=api.captureStatus(SESSION);
				assertThat(status.state()).isEqualTo("STOPPED"); assertThat(status.reason()).isEqualTo("QUEUE_OVERFLOW");
				assertThat(status.gaps()).anySatisfy(g->assertThat(g.status()).isEqualTo("UNRESOLVED_TRANSACTION_GAP"));
				assertThat(status.gaps()).anySatisfy(g->assertThat(g.status()).isEqualTo("UNRESOLVED_STATE_GAP"));
			} finally { collector.stop(SESSION); settings.setEnabled(false); http.stop(0); }
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
			var bytes=("{\"jsonrpc\":\"2.0\",\"id\":"+id+",\"result\":"+result+"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
			var status=method.equals("getTransaction")?transactionHttpStatus:200;
			if(status==0) status=result.contains("\"retry\":true")?503:200;
			exchange.sendResponseHeaders(status,bytes.length); try(var body=exchange.getResponseBody()) { body.write(bytes); }
		}); http.start(); return http;
	}

	@TestConfiguration static class Clocks {
		@Bean @Primary MutableUtcClock captureClock() { return new MutableUtcClock(START); }
	}
}
