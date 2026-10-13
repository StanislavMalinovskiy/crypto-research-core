package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static io.cryptoresearch.marketdata.application.PumpSwapPoolCaptureTest.*;
import static io.cryptoresearch.marketdata.application.CaptureRpcReuseTest.policy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.*;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(PumpSwapDurableInboxIT.Clocks.class)
class PumpSwapDurableInboxIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired CaptureLedgerUseCase ledger;
    @Autowired CaptureEvidenceUseCase evidence;
    @Autowired ContinuousCaptureLedgerUseCase continuous;
    @Autowired PumpSwapPoolCollector collector;
    @Autowired PumpSwapCaptureSettings settings;
    @Autowired MutableUtcClock clock;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @Autowired javax.sql.DataSource datasource;
    @BeforeEach void isolate() {
        settings.setEnabled(false);
        jdbc.sql("TRUNCATE marketdata.capture_sessions,marketdata.capture_periods,marketdata.raw_transactions CASCADE").update();
        clock.set(START);
    }
    static Configuration config(int items,long bytes) {
        var c=policy(configuration(UUID.randomUUID(),0L),"capture-durable-inbox-v7","synthetic-inbox-period","0.0002",20000000);
        var l=c.limits();
        return new Configuration(c.sessionId(),c.version(),c.provider(),c.startsAt(),c.pools(),
            new Limits(l.windowSeconds(),l.requests(),l.receivedBytes(),l.retainedBytes(),l.subscriptionBytes(),l.rpcHundredths(),l.messageBytes(),l.responseBytes(),items,bytes,l.timeoutSeconds(),l.retries(),l.reconnects(),l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds()),c.authority());
    }
    static String signature(int i) {
        var alphabet="123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        var encoded=i<58?i:256+i-58;
        return "1".repeat(62)+alphabet.charAt(encoded/58)+alphabet.charAt(encoded%58);
    }
    static String tx(long subscription,int i) {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"logsNotification\",\"params\":{\"subscription\":"+subscription+",\"result\":{\"context\":{\"slot\":"+(101+i)+"},\"value\":{\"signature\":\""+signature(i)+"\",\"err\":"+(i%2==0?"null":"{\"InstructionError\":[0,\"InvalidArgument\"]}")+",\"logs\":[]}}}}";
    }
    private long pending(UUID id) {
        return jdbc.sql("SELECT count(*) FROM marketdata.capture_dispositions WHERE session_id=:id AND status='PENDING'").param("id",id).query(Long.class).single();
    }
    private void await(java.util.function.BooleanSupplier condition,long millis) throws Exception {
        var end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(millis);
        while(!condition.getAsBoolean() && System.nanoTime()<end) Thread.sleep(5);
    }
    private com.sun.net.httpserver.HttpServer http(Configuration c,CountDownLatch fetched,CountDownLatch release,AtomicInteger calls) throws Exception {
        return http(c,fetched,release,calls,null);
    }
    private com.sun.net.httpserver.HttpServer http(Configuration c,CountDownLatch fetched,CountDownLatch release,AtomicInteger calls,AtomicInteger connected) throws Exception {
        var http=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        http.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        http.createContext("/",exchange->{
            var request=JsonMapper.builder().build().readTree(exchange.getRequestBody().readAllBytes());
            var method=request.path("method").asText(); String result;
            if(method.equals("getAccountInfo")) {
                var account=request.path("params").path(0).asText(); var p=c.pools().stream().filter(pool->pool.accounts().contains(account)).findFirst().orElseThrow();
                var base=account.equals(p.baseVault()) || account.equals(p.baseMint());
                var data=account.equals(p.address())?"[\"AA==\",\"base64\"]":account.equals(p.baseVault()) || account.equals(p.quoteVault())?
                    "{\"parsed\":{\"type\":\"account\",\"info\":{\"mint\":\""+(base?p.baseMint():p.quoteMint())+"\",\"owner\":\""+p.address()+"\"}}}":"{\"parsed\":{\"type\":\"mint\",\"info\":{\"decimals\":6}}}";
                result="{\"context\":{\"slot\":100},\"value\":{\"owner\":\""+(account.equals(p.address())?p.program():TOKEN)+"\",\"data\":"+data+"}}";
            } else if(method.equals("getTransaction")) {
                calls.incrementAndGet(); fetched.countDown();
                try { if(!release.await(9500,TimeUnit.MILLISECONDS)) throw new AssertionError("test fetch latch expired before observation"); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted); }
                var sig=request.path("params").path(0).asText(); var encoded="123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".indexOf(sig.charAt(62))*58+"123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".indexOf(sig.charAt(63)); var index=encoded<58?encoded:encoded-256+58;
                result=CaptureRpcReuseIT.full(sig,101+index,"1",index%2!=0);
            } else if(method.equals("getTransactionsForAddress") && connected!=null) {
                var prior=connected.get()-1;
                result="{\"data\":["+CaptureRpcReuseIT.full(signature(prior),101+prior,"1",prior%2!=0)+"],\"paginationToken\":null}";
            } else throw new AssertionError("history must not be required for inbox progress");
            var body=("{\"jsonrpc\":\"2.0\",\"id\":"+request.path("id").asText()+",\"result\":"+result+"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length); try(var output=exchange.getResponseBody()) { output.write(body); }
        }); http.start(); return http;
    }
    private void launch(Configuration c,com.sun.net.httpserver.HttpServer http,LocalCaptureWebSocket ws) throws Exception {
        settings.setEnabled(true); settings.setSecret("synthetic-inbox-secret"); settings.setRpc(java.net.URI.create("http://127.0.0.1:"+http.getAddress().getPort()+"/")); settings.setWebsocket(ws.uri());
        assertThat(collector.start(c).state()).as("setup admitted under genuine v7 policy").isEqualTo("RUNNING"); ws.awaitSent();
    }
    @Test void committedNotificationsBeyondMailboxCapacityAreDurableBeforeFetchCompletes() throws Exception {
        var c=config(8,16000000); var fetched=new CountDownLatch(1); var release=new CountDownLatch(1); var calls=new AtomicInteger();
        var http=http(c,fetched,release,calls);
        try(var ws=new LocalCaptureWebSocket(subscription->List.of(),false)) {
            try {
                launch(c,http,ws); var subscription=ws.accountSubscription()-1;
                ws.send(List.of(tx(subscription,0))); assertThat(fetched.await(2,TimeUnit.SECONDS)).as("setup reached real finite blocked transaction fetch").isTrue();
                for(var i=1;i<8;i++) { ws.send(List.of(tx(subscription,i))); Thread.sleep(12); }
                await(()->pending(c.sessionId())==8,1000);
                assertThat(pending(c.sessionId())).as("all 8 admitted notifications commit PENDING before the blocked fetch completes").isEqualTo(8);
                for(var i=8;i<25;i++) { ws.send(List.of(tx(subscription,i))); Thread.sleep(12); }
                await(()->pending(c.sessionId())==25,1000);
                assertThat(pending(c.sessionId())).as("all 25 admitted notifications commit PENDING while fetch is blocked; mailbox holds only 8").isEqualTo(25);
                var envelopes=ledger.replay(c.sessionId()).envelopes().stream().filter(e->e.kind().equals("WEBSOCKET") && e.payload().contains("logsNotification")).toList();
                assertThat(envelopes).hasSize(25);
                for(var i=0;i<25;i++) { var exact=tx(subscription,i); assertThat(envelopes).anySatisfy(e->{assertThat(e.payload()).isEqualTo(exact); assertThat(e.receivedAt()).isEqualTo(START);}); }
                assertThat(calls.get()).isEqualTo(1);
                assertThat(ledger.status(c.sessionId()).state()).isEqualTo("RUNNING");
                var active=(java.util.concurrent.atomic.AtomicReference<?>)field(collector,"active"); var run=active.get();
                await(()->{try{return ((java.util.Collection<?>)field(run,"writes")).isEmpty();}catch(Exception failure){throw new AssertionError(failure);}},1000);
                assertThat((java.util.Collection<?>)field(run,"queue")).as("real-flow committed notifications have no second resident queue").isEmpty();
                assertThat((java.util.Collection<?>)field(field(run,"transport"),"admitted")).as("committed callback futures released before fetch").isEmpty();
                release.countDown(); await(()->ledger.status(c.sessionId()).transactions()>0,2000);
                assertThat(ledger.status(c.sessionId()).transactions()).isPositive();
            } finally { release.countDown(); collector.stop(c.sessionId()); settings.setEnabled(false); http.stop(0); }
        }
    }
    @TestConfiguration static class Clocks { @Bean @Primary MutableUtcClock inboxClock() { return new MutableUtcClock(START); } }

    private void awaitHealthyPong(Object run,Object transport,long deadline) throws Exception {
        var fixed=clock.instant();
        while(true) {
            var pending=field(transport,"expectedPong");
            var next=(Instant)field(transport,"nextPing");
            if(pending==null && next!=null && fixed.isBefore(next) && field(transport,"expectedPong")==null) return;
            assertThat(System.nanoTime()).as("actual loopback pong completes at frozen time before the next clock step").isLessThan(deadline);
            assertThat(((java.util.concurrent.atomic.AtomicReference<?>)field(run,"interruption")).get()).as("healthy-clock fixture must not trigger a transport interruption").isNull();
            Thread.sleep(5);
        }
    }
    private void advanceHealthyWithActualLease(UUID session,Instant target) throws Exception {
        var deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        var run=((java.util.concurrent.atomic.AtomicReference<?>)field(collector,"active")).get();
        var transport=field(run,"transport");
        awaitHealthyPong(run,transport,deadline);
        while(clock.instant().isBefore(target)) {
            assertThat(System.nanoTime()).as("controlled clock advances only inside the actual renewable session lease").isLessThan(deadline);
            var until=jdbc.sql("select locked_until from marketdata.capture_sessions where session_id=:id").param("id",session).query(java.time.OffsetDateTime.class).single().toInstant();
            boolean advanced=false;
            synchronized(clock) {
                var now=clock.instant(); var step=Math.min(9000,java.time.Duration.between(now,until).toMillis()-2000);
                if(step>=1000) { var next=now.plusMillis(step); if(next.isAfter(target))next=target; if(next.isBefore(until)) {clock.set(next);advanced=true;} }
            }
            if(advanced) {
                // Lease renewal alone is not a healthy heartbeat: never advance virtual time
                // through the real ten-second pong deadline while loopback I/O is still pending.
                awaitHealthyPong(run,transport,deadline);
            }
            Thread.sleep(advanced?20:50);
        }
        // Let the real coordinator observe the healthy boundary and process its pong, not a helper call.
        Thread.sleep(150);
    }
    @Test void genuineV7LoopResetsConsecutiveFailuresAfterHealthyFiveMinutesWithoutResettingTotalAttempts() throws Exception {
        var source=config(256,16000000);var l=source.limits();
        var c=new Configuration(source.sessionId(),source.version(),source.provider(),source.startsAt(),source.pools(),
            new Limits(l.windowSeconds(),l.requests(),l.receivedBytes(),l.retainedBytes(),l.subscriptionBytes(),l.rpcHundredths(),l.messageBytes(),l.responseBytes(),l.queueItems(),l.queueBytes(),l.timeoutSeconds(),l.retries(),20,l.recoveryPages(),300,l.requestsPerSecond(),l.leaseSeconds()),source.authority());
        var connected=new AtomicInteger(-1);var fetched=new CountDownLatch(1);var http=http(c,fetched,new CountDownLatch(0),new AtomicInteger(),connected);
        try(var ws=new LocalCaptureWebSocket(5,(index,account)->List.of(tx(account-1,index)),connected::set)) {
            try {
                launch(c,http,ws);
                await(()->ledger.status(c.sessionId()).transactions()==1,2000);
                assertThat(ledger.status(c.sessionId()).transactions()).isEqualTo(1);
                for(var episode=1;episode<=4;episode++) {
                    advanceHealthyWithActualLease(c.sessionId(),clock.instant().plusSeconds(301));
                    ws.disconnect(1011,"synthetic-stable-inbox");var expected=episode;
                    await(()->connected.get()==expected || ledger.status(c.sessionId()).state().equals("STOPPED"),20000);
                    assertThat(connected.get()).as("real v7 loop admits recoverable reconnect %s after >300s healthy; lifetime total cap stays20",episode).isEqualTo(expected);
                    await(()->ledger.status(c.sessionId()).transactions()==expected+1 && ledger.gaps(c.sessionId()).stream().filter(g->g.status().equals("RECOVERED_TRANSACTIONS")).count()==expected,4000);
                    assertThat(ledger.status(c.sessionId()).transactions()).isEqualTo(expected+1);
                    assertThat(ledger.gaps(c.sessionId()).stream().filter(g->g.status().equals("RECOVERED_TRANSACTIONS")).count()).isEqualTo(expected);
                }
                assertThat(ledger.status(c.sessionId()).state()).isEqualTo("RUNNING");
                var run=((java.util.concurrent.atomic.AtomicReference<?>)field(collector,"active")).get();
                assertThat((int)field(run,"reconnectAttempts")).as("healthy reset never refunds the lifetime reconnect total").isEqualTo(4);
                assertThat((int)field(run,"reconnectSuccesses")).isEqualTo(4);
            } finally {collector.stop(c.sessionId());settings.setEnabled(false);http.stop(0);}
        }
    }

    @Test void observedShape940BurstWithAccountPressureIsDurableBeforeFiniteFetchRelease() throws Exception {
        var c=config(256,16000000); var fetched=new CountDownLatch(1); var release=new CountDownLatch(1); var calls=new AtomicInteger(); var http=http(c,fetched,release,calls);
        try(var ws=new LocalCaptureWebSocket(subscription->List.of(),false)) {
            try {
                launch(c,http,ws); var subscription=ws.accountSubscription()-1;
                ws.send(List.of(tx(subscription,0))); assertThat(fetched.await(2,TimeUnit.SECONDS)).isTrue();
                var began=System.nanoTime();
                for(var i=1;i<940;i++) {
                    CaptureBudget.signature(signature(i));
                    var offset=i<132?i*1000000000L/132:1000000000L+(i-132)*7515498000L/807;
                    var wait=began+offset-System.nanoTime(); if(wait>0) TimeUnit.NANOSECONDS.sleep(wait);
                    ws.send(List.of(tx(subscription,i)));
                    if(i%100==0) ws.send(List.of("{\"params\":{\"subscription\":"+ws.accountSubscription()+",\"result\":{\"context\":{\"slot\":"+(2000+i)+"},\"value\":{\"data\":[\"AQ==\",\"base64\"]}}}}"));
                    if(i%250==0) ping(ws);
                }
                await(()->pending(c.sessionId())==940,Math.max(1,9200-TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)));
                var count=pending(c.sessionId()); var elapsed=System.nanoTime()-began;
                release.countDown();
                assertThat(elapsed).as("test-owned latch remains below original10s transport timeout").isLessThan(TimeUnit.MILLISECONDS.toNanos(9500));
                assertThat(count).as("940 exact durable PENDING before first fetch completion, history absent").isEqualTo(940);
                assertThat(calls.get()).isEqualTo(1);
                var replay=ledger.replay(c.sessionId());
                assertThat(replay.envelopes().stream().filter(e->e.kind().equals("WEBSOCKET") && e.payload().contains("logsNotification"))).hasSize(940);
                assertThat(replay.dispositions()).hasSize(940).allSatisfy(d->assertThat(d.receivedAt()).isEqualTo(START));
                assertThat(replay.states()).hasSize(24);
                assertThat(replay.status().reason()).isEmpty();
                await(()->ledger.status(c.sessionId()).transactions()>0,2000); assertThat(ledger.status(c.sessionId()).transactions()).isPositive();
                System.out.println("INBOX_940 actualNanos="+elapsed+" shapedSendNanos=8515498000 peakPerSecond=132 mailboxItems=256 historyCalls=0 durablePendingBeforeFetch="+count);
            } finally { release.countDown(); collector.stop(c.sessionId()); settings.setEnabled(false); http.stop(0); }
        }
    }
    private io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification notification(int i,boolean account) {
        return new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(address(0),account?address(1):null,account?null:signature(i),101+i,account?"{\"data\":[\"AQ==\",\"base64\"]}":"{}","{\"notification\":"+i+"}",START);
    }
    private static Object field(Object target,String name) throws Exception { var field=target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void ping(LocalCaptureWebSocket ws) throws Exception {
        var connection=(java.net.Socket)field(ws,"connection"); var frame=LocalCaptureWebSocket.class.getDeclaredMethod("frame",java.io.OutputStream.class,String.class,int.class,boolean.class); frame.setAccessible(true); frame.invoke(null,connection.getOutputStream(),"test-ping",9,true);
    }
    @Test void invalidTypedNotificationStillRetainsItsSafeExactEnvelopeBeforeTerminal() throws Exception {
        var c=config(64,16000000); var release=new CountDownLatch(0); var calls=new AtomicInteger(); var http=http(c,new CountDownLatch(1),release,calls);
        try(var ws=new LocalCaptureWebSocket(subscription->List.of(),false)) {
            try {
                launch(c,http,ws); var bad=tx(ws.accountSubscription()-1,0).replace(signature(0),"not-base58"); ws.send(List.of(bad));
                await(()->ledger.status(c.sessionId()).state().equals("STOPPED"),2000);
                var replay=ledger.replay(c.sessionId()); assertThat(replay.status().reason()).isEqualTo("INVALID_NOTIFICATION_SIGNATURE");
                assertThat(replay.envelopes()).anySatisfy(e->{assertThat(e.kind()).isEqualTo("WEBSOCKET");assertThat(e.payload()).isEqualTo(bad);});
                assertThat(replay.dispositions()).isEmpty(); assertThat(calls.get()).isZero();
            } finally { collector.stop(c.sessionId()); settings.setEnabled(false); http.stop(0); }
        }
    }
    private CaptureLedgerUseCase.InboxBacklog admit(Configuration c,List<io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification> rows,Runnable guard,boolean closing) {
        var batch=ledger.inboxBatch(c,rows); var bytes=rows.stream().mapToLong(n->n.envelope().getBytes(StandardCharsets.UTF_8).length).sum();
        var retained=rows.stream().mapToLong(n->n.envelope().getBytes(StandardCharsets.UTF_8).length+(n.account()==null?0:n.envelope().getBytes(StandardCharsets.UTF_8).length+n.payload().getBytes(StandardCharsets.UTF_8).length)).sum();
        return ledger.admitInbox(c,"writer",batch,0,bytes,bytes,retained,0,closing,guard);
    }
    @Test void mixedBatchRollsBackEnvelopePendingStateAndCountersOnMidwriteFailure() {
        var c=config(1000,16000000); ledger.open(c,"writer"); var before=ledger.replay(c.sessionId()); var steps=new AtomicInteger();
        assertThatThrownBy(()->admit(c,List.of(notification(0,false),notification(1,true)),()->{if(steps.incrementAndGet()==8) throw new IllegalStateException("SYNTHETIC_MIDWRITE_FAILURE");},false)).hasMessage("SYNTHETIC_MIDWRITE_FAILURE");
        assertThat(ledger.replay(c.sessionId())).isEqualTo(before);
    }
    @Test void equalBatchesKeepFirstClocksConflictingSlotsRollbackAndAccountVariantsRemainAmbiguous() {
        var c=config(1000,16000000); ledger.open(c,"writer"); var n=notification(0,false); var state=notification(1,true);
        admit(c,List.of(n,state),()->{},false); var original=ledger.dispositions(c.sessionId()); var first=ledger.states(c.sessionId());
        clock.set(START.plusSeconds(1)); admit(c,List.of(n,state),()->{},false);
        var initial=original.getFirst();
        assertThat(ledger.dispositions(c.sessionId())).containsExactly(new Disposition(initial.pool(),initial.signature(),initial.slot(),initial.status(),initial.rawDigest(),initial.notification(),initial.response(),initial.receivedAt(),initial.admittedAt(),initial.duplicates()+1,initial.conflicts()));
        assertThat(ledger.states(c.sessionId()).get(0).receivedAt()).isEqualTo(first.get(0).receivedAt());
        var bad=new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(n.pool(),null,n.signature(),999,n.payload(),"{\"conflict\":true}",START);
        var before=ledger.replay(c.sessionId()); assertThatThrownBy(()->admit(c,List.of(notification(3,false),bad),()->{},false)).hasMessage("CAPTURE_INBOX_CONTENT_CONFLICT"); assertThat(ledger.replay(c.sessionId())).isEqualTo(before);
        var variant=new io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification(state.pool(),state.account(),null,state.slot(),"{\"data\":[\"Ag==\",\"base64\"]}","{\"variant\":true}",START.plusSeconds(1));
        admit(c,List.of(variant),()->{},false); assertThat(ledger.states(c.sessionId())).hasSize(2).allSatisfy(s->assertThat(s.ambiguous()).isTrue());
    }
    @Test void expiredOrStolenFenceAndResourceRejectionLeaveNoPartialBatch() {
        var c=config(1000,16000000); ledger.open(c,"writer"); var before=ledger.replay(c.sessionId());
        clock.set(START.plusSeconds(31)); assertThatThrownBy(()->admit(c,List.of(notification(0,false)),()->{},false)).hasMessage("CAPTURE_LEASE_LOST"); assertThat(ledger.replay(c.sessionId())).isEqualTo(before);
        assertThat(ledger.claim(c.sessionId(),"thief")).isTrue(); assertThatThrownBy(()->admit(c,List.of(notification(0,false)),()->{},false)).hasMessage("CAPTURE_LEASE_LOST");
        assertThat(pending(c.sessionId())).isZero(); assertThat(ledger.replay(c.sessionId()).envelopes()).isEmpty();
    }
    @Test void pendingHydrationRejectsOversizeFirstAndPreservesTotalTupleOrder() {
        var c=config(1000,1000); ledger.open(c,"writer"); ledger.pendingAt(c.sessionId(),"writer",address(0),signature(0),101,"x".repeat(1001),START);
        assertThatThrownBy(()->ledger.pending(c)).hasMessage("CAPTURE_PENDING_PAYLOAD_LIMIT"); assertThat(pending(c.sessionId())).isEqualTo(1);
    }
    @Test void originalAgeAndCountThresholdDoNotPreventAlreadyAdmittedClosingTail() {
        var c=config(1000,16000000); ledger.open(c,"writer");
        var alphabet="123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        jdbc.sql("INSERT INTO marketdata.capture_dispositions(session_id,pool,signature,slot,status,notification,received_at) SELECT :id,:pool,repeat('1',62)||substr(:alphabet,(n+3364)/3364+1,1)||substr(:alphabet,((n+3364)%3364)/58+1,1)||substr(:alphabet,(n+3364)%58+1,1),n,'PENDING','{}',:at FROM generate_series(1,5000) n")
            .param("id",c.sessionId()).param("pool",address(0)).param("alphabet",alphabet).param("at",START.atOffset(java.time.ZoneOffset.UTC)).update();
        assertThat(ledger.inboxBacklog(c).problem(START)).isEqualTo("CAPTURE_PENDING_COUNT_LIMIT");
        var result=admit(c,List.of(notification(0,false)),()->{},true); assertThat(result.count()).isEqualTo(5001);
        jdbc.sql("DELETE FROM marketdata.capture_dispositions WHERE session_id=:id AND length(signature)=65").param("id",c.sessionId()).update();
        clock.set(START.plusSeconds(1799)); assertThat(ledger.claim(c.sessionId(),"writer")).isTrue(); clock.set(START.plusSeconds(1800));
        assertThat(ledger.inboxBacklog(c).problem(clock.instant())).isEqualTo("CAPTURE_PENDING_AGE_LIMIT");
        admit(c,List.of(notification(1,false)),()->{},true); assertThat(ledger.inboxBacklog(c).oldest()).isEqualTo(START);
    }
    @Test void blockedWriterOverflowRetainsExactFirstFragmentItemCauseAndAdmittedTail() throws Exception {
        var c=config(2,16000000); var calls=new AtomicInteger(); var fetched=new CountDownLatch(1); var release=new CountDownLatch(1); var http=http(c,fetched,release,calls);
        try(var ws=new LocalCaptureWebSocket(subscription->List.of(),false)) {
            try {
                launch(c,http,ws);
                ws.send(List.of(tx(ws.accountSubscription()-1,0))); assertThat(fetched.await(2,TimeUnit.SECONDS)).as("setup has no resident resolver command while finite fetch is blocked").isTrue();
                try(var connection=datasource.getConnection()) {
                    connection.setAutoCommit(false);
                    try(var lock=connection.prepareStatement("SELECT session_id FROM marketdata.capture_sessions WHERE session_id=? FOR UPDATE")) { lock.setObject(1,c.sessionId()); lock.executeQuery().close(); }
                    ws.send(List.of(tx(ws.accountSubscription()-1,1),tx(ws.accountSubscription()-1,2),tx(ws.accountSubscription()-1,3)));
                    Thread.sleep(100); connection.commit();
                }
                await(()->ledger.status(c.sessionId()).state().equals("STOPPED"),2000);
                var replay=ledger.replay(c.sessionId()); assertThat(replay.status().reason()).isEqualTo("QUEUE_OVERFLOW");
                var receipt=replay.envelopes().stream().filter(e->e.kind().equals("CAPTURE_TERMINAL")).findFirst().orElseThrow(); var node=JsonMapper.builder().build().readTree(receipt.payload());
                assertThat(node.path("version").asText()).isEqualTo("capture-terminal-v2"); assertThat(node.path("firstCause").asText()).isEqualTo("QUEUE_OVERFLOW");
                assertThat(node.path("overflow").path("site").asText()).isEqualTo("FRAGMENT"); assertThat(node.path("overflow").path("condition").asText()).isEqualTo("ITEM"); assertThat(node.path("overflow").path("items").asInt()).isEqualTo(2);
                assertThat(receipt.payload().getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(8192); assertThat(receipt.payload()).doesNotContain(settings.getSecret());
                assertThat(replay.dispositions()).hasSize(3).allSatisfy(d->assertThat(d.status()).isEqualTo("PENDING")); assertThat(calls.get()).isEqualTo(1);
            } finally { release.countDown(); collector.stop(c.sessionId()); settings.setEnabled(false); http.stop(0); }
        }
    }
    @Test void resourceFailureAndCrossedClosingGuardRollbackExactBatch() {
        var c=config(1000,16000000); ledger.open(c,"writer"); var before=ledger.replay(c.sessionId()); var batch=ledger.inboxBatch(c,List.of(notification(0,false)));
        assertThatThrownBy(()->ledger.admitInbox(c,"writer",batch,0,c.limits().receivedBytes()+1,0,0,0,false,()->{})).hasMessage("CAPTURE_RESOURCE_LIMIT"); assertThat(ledger.replay(c.sessionId())).isEqualTo(before);
        var steps=new AtomicInteger(); assertThatThrownBy(()->admit(c,List.of(notification(0,false)),()->{if(steps.incrementAndGet()==9) throw new IllegalStateException("CAPTURE_WRITER_DRAIN_TIMEOUT");},true)).hasMessage("CAPTURE_WRITER_DRAIN_TIMEOUT"); assertThat(ledger.replay(c.sessionId())).isEqualTo(before);
    }
    @Test void originalWindowCrossingDuringBatchRollsBackAndClosingDoesNotReopenAdmission() {
        var c=config(1000,16000000); ledger.open(c,"writer"); var before=ledger.replay(c.sessionId()); var steps=new AtomicInteger();
        assertThatThrownBy(()->admit(c,List.of(notification(0,false)),()->{if(steps.incrementAndGet()==8) clock.set(CaptureLedgerUseCase.end(c));},false)).hasMessage("CAPTURE_TIME_LIMIT"); assertThat(ledger.replay(c.sessionId())).isEqualTo(before);
        clock.set(START); admit(c,List.of(notification(0,false)),()->{},false); clock.set(CaptureLedgerUseCase.end(c));
        assertThat(ledger.claim(c.sessionId(),"new-writer")).isFalse();
    }
    @Test void duplicateNotificationsRemainCountedWithinBatchWhilePendingAndAfterRawCompletion() {
        var c=config(1000,16000000); ledger.open(c,"writer"); var n=notification(0,false);
        admit(c,List.of(n,n,n),()->{},false);
        var first=ledger.dispositions(c.sessionId()).getFirst();
        assertThat(first.duplicates()).as("three same-identity admitted frames retain two duplicate facts").isEqualTo(2);
        admit(c,List.of(n,n,n),()->{},false);
        assertThat(ledger.dispositions(c.sessionId()).getFirst().duplicates()).isEqualTo(5);
        assertThat(evidence.transaction(c.sessionId(),"writer",n.pool(),n.signature(),CaptureRpcReuseIT.full(n.signature(),n.slot(),"1",false),"{}",START,true)).isEqualTo("CAPTURED");
        clock.set(START.plusSeconds(1)); admit(c,List.of(n),()->{},false);
        var completed=ledger.dispositions(c.sessionId()).getFirst(); assertThat(completed.status()).isEqualTo("CAPTURED"); assertThat(completed.duplicates()).isEqualTo(6);
        assertThat(completed.receivedAt()).isEqualTo(first.receivedAt()); assertThat(completed.notification()).isEqualTo(first.notification());
        assertThat(jdbc.sql("SELECT count(*) FROM marketdata.raw_transactions").query(Long.class).single()).isEqualTo(1);
    }
    @Test void concurrentDuplicateBatchesOwnOnePendingIdentityAndCountAllAdmittedFrames() throws Exception {
        var c=config(1000,16000000); ledger.open(c,"writer"); var n=notification(0,false); var gate=new CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->{gate.await();return admit(c,List.of(n,n,n),()->{},false);}); var second=executor.submit(()->{gate.await();return admit(c,List.of(n,n,n),()->{},false);}); gate.countDown(); first.get(10,TimeUnit.SECONDS); second.get(10,TimeUnit.SECONDS);
        }
        assertThat(ledger.dispositions(c.sessionId())).singleElement().satisfies(d->assertThat(d.duplicates()).as("six admitted equal concurrent frames count five duplicates").isEqualTo(5));
    }
    @Test void actualDeploymentTakeoverRejectsOldBatchWithAllOriginalFactsUntouched() {
        var template=config(1000,16000000); var one=ContinuousCaptureLedgerUseCase.resources(template); var zero=new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Resources(0,0,0,0,0,0,0);
        var deployment=new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration("synthetic-inbox-fence","continuous-capture-v1",template,one.plus(one),zero,START.plusSeconds(300),2,100,2,60,2048,100);
        var admitted=continuous.admit(deployment,"original"); clock.set(START.plusSeconds(31)); var takeover=continuous.admit(deployment,"next"); assertThat(takeover.fence().generation()).isGreaterThan(admitted.fence().generation());
        var c=admitted.segment().configuration(); var before=ledger.replay(c.sessionId());
        assertThatThrownBy(()->ledger.admitInbox(c,admitted.fence().sessionHolder(),ledger.inboxBatch(c,List.of(notification(0,false))),0,20,20,20,0,false,()->{})).hasMessage("CONTINUOUS_CAPTURE_FENCE_LOST");
        assertThat(ledger.replay(c.sessionId())).isEqualTo(before);
    }
}
