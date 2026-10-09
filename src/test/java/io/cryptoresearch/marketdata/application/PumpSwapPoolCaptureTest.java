package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.*;
import io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

class PumpSwapPoolCaptureTest {
	static final Instant START = Instant.parse("2026-10-07T10:00:00Z");
	static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000001");
	static final String TOKEN = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
	static final String PROGRAM = "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA";

	static String address(int number) {
		return "1".repeat(31) + "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".charAt(number);
	}

	static Configuration configuration(UUID session, Long opening) {
		var pools = java.util.stream.IntStream.range(0, 3).mapToObj(index -> {
			var offset = index * 5;
			var payload = "{\"chain\":\"solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp\",\"contextSlot\":100,\"commitment\":\"finalized\",\"pool\":\"" + address(offset) + "\",\"program\":\"" + PROGRAM
					+ "\",\"baseVault\":\"" + address(offset + 1) + "\",\"quoteVault\":\"" + address(offset + 2)
					+ "\",\"baseMint\":\"" + address(offset + 3) + "\",\"quoteMint\":\"" + address(offset + 4)
					+ "\",\"baseTokenProgram\":\"" + TOKEN + "\",\"quoteTokenProgram\":\"" + TOKEN + "\"}";
			return new Pool(address(offset), PROGRAM, address(offset + 1), address(offset + 2),
					address(offset + 3), address(offset + 4), TOKEN, TOKEN,
					new LinkageProof(100, "finalized", "operator-proof-v1", "OWNER_REPORTED", payload, PayloadFingerprint.sha256(payload)));
		}).toList();
		return new Configuration(session, "capture-v1", "alchemy", START, pools,
				new Limits(86400, 3750, 1000000000, 1200000000, 20000000, 15000000,
						2000000, 8000000, 1000, 16000000, 10, 2, 3, 10, 300, 2, 30),
				new Authority("alchemy-2026-10", Instant.parse("2026-11-01T00:00:00Z"), "OWNER_REPORTED",
						opening, 0, 2860000000L, new BigDecimal("15"), new BigDecimal("0.525"),
						new BigDecimal("0.04"), "owner-first-window-2026-10-07", true,true));
	}

	@Test void wholeWindowReservationIsExactAndIncludesRpcControl() {
		assertThat(CaptureBudget.reservation(configuration(SESSION, 0L))).isEqualTo(95000000);
		assertThat(CaptureBudget.cost(95000000, new BigDecimal("0.525"))).isEqualByComparingTo("0.49875");
		assertThat(CaptureBudget.cost(4, new BigDecimal("0.525"))).isEqualByComparingTo("0.000000021");
	}
	@Test void resourceLimitCloseNeverHoldsAccountingLockWhileWaitingForCallbackAdmission() throws Exception {
		var ledger=org.mockito.Mockito.mock(CaptureLedgerUseCase.class);
		org.mockito.Mockito.when(ledger.status(SESSION)).thenReturn(org.mockito.Mockito.mock(Status.class));
		var closeEntered=new java.util.concurrent.CountDownLatch(1);
		org.mockito.Mockito.when(ledger.now()).thenAnswer(call->{ closeEntered.countDown(); return START; });
		var collector=new PumpSwapPoolCollector(new io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings(),ledger,null,tools.jackson.databind.json.JsonMapper.builder().build());
		var type=java.util.Arrays.stream(PumpSwapPoolCollector.class.getDeclaredClasses()).filter(t->t.getSimpleName().equals("Run")).findFirst().orElseThrow();
		var constructor=type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
		var run=constructor.newInstance(collector,configuration(SESSION,0L),"synthetic-holder");
		var localField=type.getDeclaredField("local"); localField.setAccessible(true); var local=localField.get(run);
		try(var adapter=wsAdapter(URI.create("ws://127.0.0.1/"))) {
			var transport=type.getDeclaredField("transport"); transport.setAccessible(true); transport.set(run,adapter);
			var admissionField=JdkPumpSwapTransport.class.getDeclaredField("callbackAdmission"); admissionField.setAccessible(true); var admission=admissionField.get(adapter);
			var received=type.getDeclaredMethod("received",long.class,boolean.class); received.setAccessible(true);
			var accountingAvailable=new java.util.concurrent.CountDownLatch(1);
			Thread limitThread;
			synchronized(admission) {
				limitThread=Thread.ofVirtual().start(()->{ try { received.invoke(run,configuration(SESSION,0L).limits().receivedBytes()+1,false); } catch(java.lang.reflect.InvocationTargetException expected) { assertThat(expected.getCause()).isInstanceOf(IllegalStateException.class).hasMessage("CAPTURE_RESOURCE_LIMIT"); } catch(Exception failure) { throw new AssertionError(failure); } });
				assertThat(closeEntered.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
				Thread.ofVirtual().start(()->{ synchronized(local) { accountingAvailable.countDown(); } });
				assertThat(accountingAvailable.await(2,java.util.concurrent.TimeUnit.SECONDS)).as("callback accounting remains available while budget close waits for admission").isTrue();
			}
			limitThread.join(java.time.Duration.ofSeconds(2));
		}
	}
	@Test void accountCuCeilingNeverRoundsUpFromFifteenDollars() {
		assertThat(CaptureBudget.ceiling(configuration(SESSION,0L))).isEqualTo(2857142800L);
	}
	@Test void twentyReconnectsAreFiniteAndTwentyOneIsRejected() {
		var c=configuration(SESSION,0L); var l=c.limits();
		for(var count:List.of(20,21)) {
			var limits=new Limits(l.windowSeconds(),l.requests(),l.receivedBytes(),l.retainedBytes(),l.subscriptionBytes(),l.rpcHundredths(),l.messageBytes(),l.responseBytes(),l.queueItems(),l.queueBytes(),l.timeoutSeconds(),l.retries(),count,l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds());
			assertThat(CaptureBudget.launchProblem(new Configuration(c.sessionId(),c.version(),c.provider(),c.startsAt(),c.pools(),limits,c.authority()))).isEqualTo(count==20?"":"INVALID_FINITE_LIMITS");
		}
	}
	@Test void reconnectTotalNeverResetsAndOnlyFiveStableMinutesResetConsecutiveFailures() {
		assertThat(PumpSwapPoolCollector.reconnectAllowed(19,2,20)).isTrue(); assertThat(PumpSwapPoolCollector.reconnectAllowed(20,0,20)).isFalse(); assertThat(PumpSwapPoolCollector.reconnectAllowed(1,3,20)).isFalse();
		assertThat(PumpSwapPoolCollector.stableFailures(3,START,START.plusSeconds(299),true)).isEqualTo(3); assertThat(PumpSwapPoolCollector.stableFailures(3,START,START.plusSeconds(300),false)).isEqualTo(3);
		assertThat(PumpSwapPoolCollector.stableFailures(3,START,START.plusSeconds(300),true)).isZero(); assertThat(PumpSwapPoolCollector.reconnectAllowed(20,0,20)).isFalse();
	}

	@Test void unknownOpeningBalanceCannotBecomeZero() {
		assertThat(CaptureBudget.launchProblem(configuration(SESSION, null))).isEqualTo("UNKNOWN_OPENING_USAGE");
	}
	@Test
	void applicationLauncherIsIdleByDefaultAndMissingConfigurationFailsClosed() {
		var settings=new io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings();
		var launcher=new PumpSwapCaptureLauncher(settings,null,null);
		var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(PumpSwapCaptureLauncher.class);
		var priorLevel=logger.getLevel(); var priorAdditivity=logger.isAdditive();
		var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
		appender.setContext(logger.getLoggerContext()); appender.start();
		logger.setLevel(ch.qos.logback.classic.Level.INFO); logger.setAdditive(false); logger.addAppender(appender);
		try {
			assertThat(settings.isEnabled()).isFalse();
			launcher.run(null);
			assertThat(appender.list).extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
					.noneMatch(message->message.contains("PumpSwap capture startup"));
			settings.setEnabled(true); launcher.run(null);
			assertThat(appender.list).extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
					.anyMatch(message->message.contains("BLOCKED_FOR_LAUNCH:MISSING_CAPTURE_CONFIGURATION_FILE"));
		} finally { logger.detachAppender(appender); logger.setLevel(priorLevel); logger.setAdditive(priorAdditivity); appender.stop(); }
	}
	@Test void unknownOtherActivityRequiresHeadroomOrExplicitExclusiveAllocation() {
		var c=configuration(SESSION,0L); var a=c.authority();
		var unknown=new Authority(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),0,a.ceilingHundredths(),
				a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),a.ownerAuthorization(),a.retentionAllowed(),false);
		assertThat(CaptureBudget.launchProblem(new Configuration(c.sessionId(),c.version(),c.provider(),c.startsAt(),c.pools(),c.limits(),unknown)))
				.isEqualTo("UNKNOWN_OTHER_ACCOUNT_USAGE");
	}

	@Test void mismatchedProofDigestBlocksLaunch() {
		var good = configuration(SESSION, 0L);
		var pool = good.pools().getFirst();
		var bad = new Pool(pool.address(), pool.program(), pool.baseVault(), pool.quoteVault(), pool.baseMint(),
				pool.quoteMint(), pool.baseTokenProgram(), pool.quoteTokenProgram(),
				new LinkageProof(100, "finalized", "proof-v1", "OWNER_REPORTED", pool.proof().payload(), "sha256:" + "0".repeat(64)));
		assertThat(CaptureBudget.launchProblem(new Configuration(good.sessionId(), good.version(), good.provider(),
				good.startsAt(), List.of(bad, good.pools().get(1), good.pools().get(2)), good.limits(), good.authority())))
				.isEqualTo("LINKAGE_PROOF_DIGEST");
	}
	@Test void proofChainContextAndFinalityMustMatchDeclaredFinalizedMainnetEvidence() {
		var c=configuration(SESSION,0L); var p=c.pools().getFirst(); var proof=p.proof();
		org.assertj.core.api.SoftAssertions.assertSoftly(softly->{
		for(var payload:List.of(proof.payload().replace("solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp","solana:devnet"),
				proof.payload().replace("\"contextSlot\":100","\"contextSlot\":99"),proof.payload().replace("finalized","confirmed"))) {
			var invalid=new Pool(p.address(),p.program(),p.baseVault(),p.quoteVault(),p.baseMint(),p.quoteMint(),p.baseTokenProgram(),p.quoteTokenProgram(),
					new LinkageProof(proof.contextSlot(),proof.commitment(),proof.sourceVersion(),proof.provenance(),payload,PayloadFingerprint.sha256(payload)));
			softly.assertThat(CaptureBudget.launchProblem(new Configuration(c.sessionId(),c.version(),c.provider(),c.startsAt(),List.of(invalid,c.pools().get(1),c.pools().get(2)),c.limits(),c.authority())))
					.isEqualTo("LINKAGE_PROOF_CHAIN_CONTEXT_OR_FINALITY");
		}
		});
	}

	@Test void actualHttpAdapterRequestsFinalizedAndPreservesExactResultText() throws Exception {
		var request=new AtomicReference<String>();
		var result="{ \"slot\" : 100, \"transaction\":{\"signatures\":[\""+"1".repeat(64)+"\"],\"message\":{}},\"meta\":{\"err\":{\"InstructionError\":[0,\"Custom\"]}} }";
		var envelope="{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":"+result+"}";
		var server=server(envelope,request);
		try(var adapter=adapter(server,new AtomicLong())) {
			var response=adapter.rpc("getTransaction","[\""+"1".repeat(64)+"\",{\"commitment\":\"finalized\",\"encoding\":\"json\",\"maxSupportedTransactionVersion\":0}]");
			assertThat(response.result()).isEqualTo(result);
			assertThat(response.envelope()).isEqualTo(envelope);
			assertThat(request.get()).contains("\"commitment\":\"finalized\"");
		} finally { server.stop(0); }
	}

	@Test void actualHttpAdapterAccountsNullResponseBytes() throws Exception {
		var envelope="{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}";
		var bytes=new AtomicLong(); var server=server(envelope,new AtomicReference<>());
		try(var adapter=adapter(server,bytes)) {
			assertThat(adapter.rpc("getTransaction","[]").result()).isEqualTo("null");
			assertThat(bytes.get()).isEqualTo(3L*envelope.getBytes(StandardCharsets.UTF_8).length);
		} finally { server.stop(0); }
	}

	@Test void actualHttpAdapterRejectsSecretEchoWithoutLeakingIt() throws Exception {
		var server=server("{\"error\":\"synthetic-provider-secret\"}",new AtomicReference<>());
		try(var adapter=adapter(server,new AtomicLong())) {
			assertThatThrownBy(()->adapter.rpc("getTransaction","[]"))
					.isInstanceOf(IllegalStateException.class).hasMessage("PROVIDER_SECRET_ECHO");
		} finally { server.stop(0); }
	}

	@Test void actualHttpAdapterRejectsTruncatedJson() throws Exception {
		var server=server("{\"result\":{",new AtomicReference<>());
		try(var adapter=adapter(server,new AtomicLong())) {
			assertThatThrownBy(()->adapter.rpc("getTransaction","[]"))
					.isInstanceOf(IllegalStateException.class).hasMessage("MALFORMED_PROVIDER_JSON");
		} finally { server.stop(0); }
	}
	@Test void alchemyHistoryUsesDataRowsAndExactOpaqueCursor() {
		assertThat(PumpSwapPoolCollector.historyRows("{\"data\":[{\"slot\":100}],\"paginationToken\":\"100:0\"}").size()).isEqualTo(1);
	}
	@Test void actualHttpAdapterRejectsEscapedSecretEcho() throws Exception {
		var server=server("{\"result\":\"synthetic\\u002dprovider\\u002dsecret\"}",new AtomicReference<>());
		try(var adapter=adapter(server,new AtomicLong())) {
			assertThatThrownBy(()->adapter.rpc("getTransaction","[]")).isInstanceOf(IllegalStateException.class).hasMessage("PROVIDER_SECRET_ECHO");
		} finally { server.stop(0); }
	}
	@Test void actualWebSocketSubscribesOnlyFinalizedAndReassemblesExactFragments() throws Exception {
		var payload="{\"data\" : [\"AA==\",\"base64\"]}";
		var received=new java.util.concurrent.CopyOnWriteArrayList<JdkPumpSwapTransport.Notification>();
		var interruption=new AtomicReference<String>(); var bytes=new AtomicLong();
		try(var fixture=new LocalCaptureWebSocket(subscription->List.of("{\"method\":\"accountNotification\",\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":100},\"value\":"+payload+"}}}"),true);
				var adapter=new JdkPumpSwapTransport(URI.create("http://127.0.0.1/"),fixture.uri(),"synthetic-provider-secret",configuration(SESSION,0L).limits(),
						new JdkPumpSwapTransport.Accounting() {
							public Instant now() { return START; }
							public void start(long cu) { }
							public void received(long size,boolean subscription) { bytes.addAndGet(size); }
							public void nextMessage() { }
						})) {
			adapter.connect(configuration(SESSION,0L).pools(),received::add,interruption::set); fixture.awaitSent();
			for(var i=0;i<100 && received.isEmpty();i++) Thread.sleep(10);
			assertThat(received).singleElement().satisfies(n->{ assertThat(n.payload()).isEqualTo(payload); assertThat(n.account()).isEqualTo(address(0)); assertThat(n.slot()).isEqualTo(100); });
			assertThat(interruption.get()).isNull(); assertThat(bytes.get()).isPositive();
		}
	}
	@Test void actualHttpAdapterRetainsUnavailableErrorEnvelope() throws Exception {
		var envelope="{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"message\":\"unavailable\"}}";
		var saved=new AtomicReference<String>(); var server=server(envelope,new AtomicReference<>());
		try(var adapter=new JdkPumpSwapTransport(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"),URI.create("ws://127.0.0.1/"),"synthetic-provider-secret",configuration(SESSION,0L).limits(),
				new JdkPumpSwapTransport.Accounting() {
					public Instant now() { return START; }
					public void start(long cu) { }
					public void received(long bytes,boolean subscription) { }
					public void nextMessage() { }
					public void evidence(String kind,String text) { if(kind.equals("HTTP_getTransaction")) saved.set(text); }
				})) {
			assertThatThrownBy(()->adapter.rpc("getTransaction","[]")).hasMessage("PROVIDER_RPC_FAILURE");
			assertThat(saved.get()).isEqualTo(envelope);
		} finally { server.stop(0); }
	}
	@Test void admittedNotificationCompletesAfterTransportClose() throws Exception {
		var admitted=new java.util.concurrent.CountDownLatch(1); var commit=new java.util.concurrent.CompletableFuture<Void>();
		var received=new java.util.concurrent.CopyOnWriteArrayList<JdkPumpSwapTransport.Notification>();
		var materialized=new java.util.concurrent.CountDownLatch(1);
		try(var fixture=new LocalCaptureWebSocket(subscription->List.of("{\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":101},\"value\":{\"data\":[\"AA==\",\"base64\"]}}}}"),false);
			var adapter=wsAdapter(fixture.uri(),new JdkPumpSwapTransport.Accounting() {
				public Instant now() { return START; } public void start(long cu) { } public void received(long bytes,boolean subscription) { } public void nextMessage() { }
				public java.util.concurrent.CompletableFuture<Void> websocketEvidence(String text,Instant at) {
					if(text.contains("params")) { admitted.countDown(); return commit; } return java.util.concurrent.CompletableFuture.completedFuture(null);
				}
			})) {
			adapter.connect(configuration(SESSION,0L).pools(),n->{ received.add(n); materialized.countDown(); },r->{ });
			assertThat(admitted.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); adapter.close(); commit.complete(null);
			materialized.await(5,java.util.concurrent.TimeUnit.SECONDS);
			assertThat(received).as("an admitted exact frame is materialized despite subsequent connection closure").hasSize(1);
		}
	}
	@Test void remoteCloseRetainsActualCodeAndRedactsUntrustedReason() throws Exception {
		var saved=new java.util.concurrent.CopyOnWriteArrayList<String>(); var interrupted=new AtomicReference<String>();
		try(var fixture=new LocalCaptureWebSocket(subscription->List.of(),false); var adapter=wsAdapter(fixture.uri(),new JdkPumpSwapTransport.Accounting() {
			public Instant now() { return START; } public void start(long cu) { } public void received(long bytes,boolean subscription) { } public void nextMessage() { }
			public void evidence(String kind,String text) { if(kind.equals("CAPTURE_DIAGNOSTIC")) saved.add(text); }
		})) {
			adapter.connect(configuration(SESSION,0L).pools(),n->{ },interrupted::set); fixture.awaitSent(); fixture.disconnect(1008,"https://private.example/provider-key");
			for(var i=0;i<100 && interrupted.get()==null;i++) Thread.sleep(10);
			assertThat(saved).as("actual peer close metadata is safe and replayable").anySatisfy(text->{ assertThat(text).contains("1008","REDACTED_UNTRUSTED_TEXT","WS_LISTENER").doesNotContain("private.example","provider-key"); assertThat(text.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(8192); });
		}
	}
	@Test void heartbeatChargesControlAndRequiresPongWithinOriginalTenSeconds() throws Exception {
		var time=new AtomicReference<>(START); var starts=new AtomicLong(); var interrupted=new AtomicReference<String>();
		try(var fixture=new LocalCaptureWebSocket(subscription->List.of(),false); var adapter=wsAdapter(fixture.uri(),new JdkPumpSwapTransport.Accounting() {
			public Instant now() { return time.get(); } public void start(long cu) { starts.incrementAndGet(); } public void received(long bytes,boolean subscription) { } public void nextMessage() { }
		})) {
			adapter.connect(configuration(SESSION,0L).pools(),n->{ },interrupted::set); fixture.awaitSent(); assertThat(starts.get()).isEqualTo(18);
			time.set(START.plusSeconds(29)); adapter.heartbeat(); assertThat(starts.get()).isEqualTo(18);
			time.set(START.plusSeconds(30)); adapter.heartbeat(); assertThat(starts.get()).isEqualTo(19);
			time.set(START.plusSeconds(39)); adapter.heartbeat(); assertThat(interrupted.get()).isNull();
			time.set(START.plusSeconds(40)); adapter.heartbeat(); assertThat(interrupted.get()).isEqualTo("WS_PONG_TIMEOUT"); assertThat(adapter.reconnectable()).isTrue();
		}
	}
	@Test void heartbeatSendFailurePreservesPermanentUnknownAndTransientClassification() throws Exception {
		for(var failure:List.of(new SecurityException("synthetic"),new javax.net.ssl.SSLException("synthetic"),new java.net.ProtocolException("synthetic"),new IllegalStateException("synthetic"),new java.io.IOException("synthetic"),new java.util.concurrent.TimeoutException("synthetic"))) {
			var interrupted=new AtomicReference<String>();
			try(var adapter=wsAdapter(URI.create("ws://127.0.0.1/"))) {
				var socket=org.mockito.Mockito.mock(java.net.http.WebSocket.class); org.mockito.Mockito.when(socket.sendPing(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.concurrent.CompletableFuture.failedFuture(failure));
				for(var entry:java.util.Map.of("socket",socket,"nextPing",START,"interruption",(java.util.function.Consumer<String>)interrupted::set).entrySet()) { var field=JdkPumpSwapTransport.class.getDeclaredField(entry.getKey()); field.setAccessible(true); field.set(adapter,entry.getValue()); }
				adapter.heartbeat(); assertThat(interrupted.get()).isEqualTo("WS_SEND_FAILURE");
				assertThat(adapter.reconnectable()).as("heartbeat must not grant another connection for %s",failure.getClass().getSimpleName()).isEqualTo(failure instanceof java.io.IOException && !(failure instanceof javax.net.ssl.SSLException) && !(failure instanceof java.net.ProtocolException) || failure instanceof java.util.concurrent.TimeoutException);
			}
		}
	}
	@Test void forbiddenNestedCauseOverridesEligibleHandshakeStatus() throws Exception {
		var response=org.mockito.Mockito.mock(java.net.http.HttpResponse.class); org.mockito.Mockito.when(response.statusCode()).thenReturn(503);
		var handshake=new java.net.http.WebSocketHandshakeException(response); handshake.initCause(new javax.net.ssl.SSLException("synthetic"));
		var classify=JdkPumpSwapTransport.class.getDeclaredMethod("transientNetwork",Throwable.class); classify.setAccessible(true);
		assertThat(classify.invoke(null,handshake)).as("TLS cause remains terminal even after observed eligible HTTP503").isEqualTo(false);
	}
	@Test void authenticationHandshakeDenialCannotBeOverriddenByNestedTransientHandshake() throws Exception {
		var classify=JdkPumpSwapTransport.class.getDeclaredMethod("transientNetwork",Throwable.class); classify.setAccessible(true);
		for(var status:List.of(401,403)) {
			var rejected=org.mockito.Mockito.mock(java.net.http.HttpResponse.class); org.mockito.Mockito.when(rejected.statusCode()).thenReturn(status);
			var eligible=org.mockito.Mockito.mock(java.net.http.HttpResponse.class); org.mockito.Mockito.when(eligible.statusCode()).thenReturn(503);
			var outer=new java.net.http.WebSocketHandshakeException(rejected); outer.initCause(new java.net.http.WebSocketHandshakeException(eligible));
			assertThat(classify.invoke(null,outer)).as("observed HTTP%s denial remains terminal despite inner HTTP503",status).isEqualTo(false);
		}
	}
	@Test void nestedDiagnosticRetainsOnlyBoundedSafeCauseAndStackMetadata() {
		Throwable cause=null; for(var i=0;i<12;i++) cause=new IllegalStateException("https://database-user:database-password@private-host/key?provider-secret",cause);
		var text=JdkPumpSwapTransport.diagnostic("TX_FETCH","PROVIDER_TRANSPORT_FAILURE",cause,503,null,null,"untrusted-password",START);
		var node=tools.jackson.databind.json.JsonMapper.builder().build().readTree(text); int frames=0; for(var item:node.path("causes")) frames+=item.path("stack").size();
		assertThat(node.path("causes").size()).isEqualTo(8); assertThat(frames).isLessThanOrEqualTo(32); assertThat(node.path("truncated").booleanValue()).isTrue();
		assertThat(text.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(8192); assertThat(text).doesNotContain("database-user","database-password","private-host","provider-secret","untrusted-password").contains("IllegalStateException","REDACTED_UNTRUSTED_TEXT");
	}
	@Test void authenticationHandshakeFailureIsNeverReconnectable() throws Exception {
		var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); server.createContext("/",exchange->{ exchange.sendResponseHeaders(401,-1); exchange.close(); }); server.start();
		try(var adapter=wsAdapter(URI.create("ws://127.0.0.1:"+server.getAddress().getPort()+"/"))) {
			assertThatThrownBy(()->adapter.connect(configuration(SESSION,0L).pools(),n->{ },r->{ })).hasMessage("WS_CONNECT_FAILURE");
			assertThat(adapter.reconnectable()).as("observed HTTP401 authentication rejection cannot grant paid reconnect").isFalse();
		} finally { server.stop(0); }
	}
	@Test void rpcResponseIdentityAndExclusiveShapeAreRequired() throws Exception {
		for(var text:List.of("{\"jsonrpc\":\"2.0\",\"id\":9,\"result\":null}","{\"jsonrpc\":\"2.0\",\"id\":1}","{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null,\"error\":{\"code\":-1}}")) {
			var server=server(text,new AtomicReference<>());
			try(var adapter=adapter(server,new AtomicLong())) { assertThatThrownBy(()->adapter.rpc("getTransaction","[]")).hasMessage("MALFORMED_RPC_RESPONSE"); }
			finally { server.stop(0); }
		}
	}

	@Test void actualHttpAdapterRejectsOversizedBodyAndCountsAlreadyDeliveredBytes() throws Exception {
		var received=new AtomicLong(); var server=server("{\"result\":\""+"x".repeat(8000001)+"\"}",new AtomicReference<>());
		try(var adapter=adapter(server,received)) {
			assertThatThrownBy(()->adapter.rpc("getTransaction","[]")).hasMessage("HTTP_RESPONSE_BYTE_LIMIT");
			assertThat(received.get()).isGreaterThan(8000000L);
		} finally { server.stop(0); }
	}
	@Test void actualWebSocketRejectsOversizedFrameAndCountsAlreadyDeliveredBytes() throws Exception {
		var received=new AtomicLong(); var reason=new AtomicReference<String>();
		try(var websocket=new LocalCaptureWebSocket(subscription->List.of("{\"method\":\"accountNotification\",\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":100},\"value\":{\"data\":\""+"x".repeat(2000001)+"\"}}}}"),false);
			var adapter=new JdkPumpSwapTransport(URI.create("http://127.0.0.1/"),websocket.uri(),"synthetic-provider-secret",configuration(SESSION,0L).limits(),
				new JdkPumpSwapTransport.Accounting() {
					public Instant now() { return START; }
					public void start(long cu) { }
					public void received(long bytes,boolean subscription) { received.addAndGet(bytes); }
					public void nextMessage() { }
				})) {
			adapter.connect(configuration(SESSION,0L).pools(),notification->{ throw new AssertionError("oversized notification cannot be admitted"); },reason::set);
			for(var i=0;i<100 && reason.get()==null;i++) Thread.sleep(20);
			assertThat(reason.get()).isEqualTo("WS_MESSAGE_BYTE_LIMIT");
			assertThat(received.get()).isGreaterThan(2000000L);
		}
	}
	private static HttpServer server(String response,AtomicReference<String> request) throws Exception {
		var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
		server.createContext("/",exchange->{
			request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
			var id=tools.jackson.databind.json.JsonMapper.builder().build().readTree(request.get()).path("id").longValue();
			var bytes=response.replace("\"id\":1","\"id\":"+id).getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,bytes.length);
			try(var body=exchange.getResponseBody()) { body.write(bytes); }
		}); server.start(); return server;
	}
	static Limits shortAckLimits() {
		var l=configuration(SESSION,0L).limits();
		return new Limits(l.windowSeconds(),l.requests(),l.receivedBytes(),l.retainedBytes(),l.subscriptionBytes(),l.rpcHundredths(),
				l.messageBytes(),l.responseBytes(),l.queueItems(),l.queueBytes(),1,l.retries(),l.reconnects(),l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds());
	}
	private static JdkPumpSwapTransport wsAdapter(URI websocket) {
		return wsAdapter(websocket,new JdkPumpSwapTransport.Accounting() {
					public Instant now() { return START; }
					public void start(long cu) { }
					public void received(long bytes,boolean subscription) { }
					public void nextMessage() { }
				});
	}
	private static JdkPumpSwapTransport wsAdapter(URI websocket,JdkPumpSwapTransport.Accounting accounting) {
		return new JdkPumpSwapTransport(URI.create("http://127.0.0.1/"),websocket,"synthetic-provider-secret",shortAckLimits(),accounting);
	}
	@Test void webSocketHeadroomFailureBeforeFirstDemandPreservesLimitReason() throws Exception {
		try(var fixture=new LocalCaptureWebSocket(subscription->List.of(),false); var adapter=wsAdapter(fixture.uri(),new JdkPumpSwapTransport.Accounting() {
			public Instant now() { return START; }
			public void start(long cu) { throw new AssertionError("no request may start after demand headroom failure"); }
			public void received(long bytes,boolean subscription) { }
			public void nextMessage() { throw new IllegalStateException("SUBSCRIPTION_BYTE_HEADROOM_LIMIT"); }
		})) {
			assertThatThrownBy(()->adapter.connect(configuration(SESSION,0L).pools(),n->{ },r->{ }))
					.hasMessage("SUBSCRIPTION_BYTE_HEADROOM_LIMIT").hasNoCause();
		}
	}
	@Test void actualWebSocketAckCannotSucceedBeforeEvidenceCommits() throws Exception {
		try(var fixture=new LocalCaptureWebSocket(subscription->List.of(),false); var adapter=wsAdapter(fixture.uri(),new JdkPumpSwapTransport.Accounting() {
			public Instant now() { return START; }
			public void start(long cu) { }
			public void received(long bytes,boolean subscription) { }
			public void nextMessage() { }
			public void evidence(String kind,String envelope) { throw new IllegalStateException("CAPTURE_LEASE_LOST"); }
		})) {
			assertThatThrownBy(()->adapter.connect(configuration(SESSION,0L).pools(),n->{ },r->{ }))
					.hasMessage("CAPTURE_LEASE_LOST").hasNoCause();
		}
	}
	@Test void actualWebSocketSecretAckIsRejectedBeforeEvidence() throws Exception {
		var saved=new AtomicLong();
		try(var fixture=new LocalCaptureWebSocket(subscription->List.of(),false,id->"{\"id\":"+id+",\"error\":\"synthetic\\u002dprovider\\u002dsecret\"}"); var adapter=wsAdapter(fixture.uri(),new JdkPumpSwapTransport.Accounting() {
			public Instant now() { return START; }
			public void start(long cu) { }
			public void received(long bytes,boolean subscription) { }
			public void nextMessage() { }
			public void evidence(String kind,String envelope) { if(kind.equals("WEBSOCKET")) saved.incrementAndGet(); }
		})) {
			assertThatThrownBy(()->adapter.connect(configuration(SESSION,0L).pools(),n->{ },r->{ }))
					.hasMessage("PROVIDER_SECRET_ECHO").hasNoCause();
			assertThat(saved.get()).isZero();
		}
	}
	@Test void actualWebSocketMissingAckHasSpecificBoundedTimeoutReason() throws Exception {
		try(var fixture=new LocalCaptureWebSocket(subscription->List.of(),false,id->null); var adapter=wsAdapter(fixture.uri())) {
			assertThatThrownBy(()->adapter.connect(configuration(SESSION,0L).pools(),n->{ },r->{ }))
					.hasMessage("WS_ACK_TIMEOUT");
		}
	}
	@Test void actualWebSocketRejectedAckPreservesSanitizedSpecificReason() throws Exception {
		try(var fixture=new LocalCaptureWebSocket(subscription->List.of(),false,id->"{\"jsonrpc\":\"2.0\",\"id\":"+id+",\"error\":{\"code\":-1,\"message\":\"wss://untrusted.example/private-token\"}}"); var adapter=wsAdapter(fixture.uri())) {
			assertThatThrownBy(()->adapter.connect(configuration(SESSION,0L).pools(),n->{ },r->{ }))
					.hasMessage("SUBSCRIPTION_REJECTED").hasNoCause();
		}
	}
	@Test void actualWebSocketConnectFailureCannotBeMislabelledAckOrProviderError() throws Exception {
		URI absent;
		try(var server=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))) {
			absent=URI.create("ws://127.0.0.1:"+server.getLocalPort()+"/private-token");
		}
		try(var adapter=wsAdapter(absent)) {
			assertThatThrownBy(()->adapter.connect(configuration(SESSION,0L).pools(),n->{ },r->{ }))
					.hasMessage("WS_CONNECT_FAILURE").hasNoCause();
		}
	}
	@Test void actualWebSocketProtocolErrorReportsOnlySanitizedListenerCategory() throws Exception {
		var interrupted=new AtomicReference<String>();
		try(var fixture=new LocalCaptureWebSocket(subscription->List.of("wss://untrusted.example/private-token"),false,
				id->"{\"id\":"+id+",\"result\":"+(10000+id)+"}",3); var adapter=wsAdapter(fixture.uri())) {
			// Reserved opcode exercises the actual JDK onError path, not a fabricated callback.
			try { adapter.connect(configuration(SESSION,0L).pools(),n->{ },interrupted::set); }
			catch(IllegalStateException failure) { assertThat(failure).hasMessage("WS_LISTENER_FAILURE").hasNoCause(); }
			for(var i=0;i<100 && interrupted.get()==null;i++) Thread.sleep(10);
			assertThat(interrupted.get()).isEqualTo("WS_LISTENER_FAILURE");
		}
	}
	@Test void sequentialAckWaitsDoNotRenewOriginalCaptureTimeAllowance() throws Exception {
		var time=new AtomicReference<>(START); var starts=new AtomicLong(); var saved=new AtomicLong();
		try(var fixture=new LocalCaptureWebSocket(subscription->List.of(),false); var adapter=wsAdapter(fixture.uri(),new JdkPumpSwapTransport.Accounting() {
			public Instant now() { return time.get(); }
			public void waitSeconds(long seconds) { if(!now().plusSeconds(seconds).isBefore(START.plusSeconds(2))) throw new IllegalStateException("CAPTURE_TIME_LIMIT"); }
			public void start(long cu) { starts.incrementAndGet(); }
			public void received(long bytes,boolean subscription) { }
			public void nextMessage() { }
			public void evidence(String kind,String envelope) { if(kind.equals("WEBSOCKET")) { saved.incrementAndGet(); time.set(START.plusSeconds(1)); } }
		})) {
			assertThatThrownBy(()->adapter.connect(configuration(SESSION,0L).pools(),n->{ },r->{ })).hasMessage("CAPTURE_TIME_LIMIT");
			assertThat(starts.get()).isEqualTo(1); assertThat(saved.get()).isEqualTo(1);
		}
	}

	private static JdkPumpSwapTransport adapter(HttpServer server,AtomicLong received) {
		var uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
		return new JdkPumpSwapTransport(uri,URI.create("ws://127.0.0.1/"),"synthetic-provider-secret",configuration(SESSION,0L).limits(),
				new JdkPumpSwapTransport.Accounting() {
					public Instant now() { return START; }
					public void start(long cu) { }
					public void received(long bytes,boolean subscription) { received.addAndGet(bytes); }
					public void nextMessage() { }
				});
	}
}
