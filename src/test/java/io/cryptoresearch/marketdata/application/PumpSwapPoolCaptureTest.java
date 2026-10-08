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
	@Test void accountCuCeilingNeverRoundsUpFromFifteenDollars() {
		assertThat(CaptureBudget.ceiling(configuration(SESSION,0L))).isEqualTo(2857142800L);
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
			assertThat(bytes.get()).isEqualTo(envelope.getBytes(StandardCharsets.UTF_8).length);
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
					public void evidence(String kind,String text) { saved.set(text); }
				})) {
			adapter.rpc("getTransaction","[]");
			assertThat(saved.get()).isEqualTo(envelope);
		} finally { server.stop(0); }
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
			var bytes=response.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,bytes.length);
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
			public void evidence(String kind,String envelope) { saved.incrementAndGet(); }
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
			public void evidence(String kind,String envelope) { saved.incrementAndGet(); time.set(START.plusSeconds(1)); }
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
