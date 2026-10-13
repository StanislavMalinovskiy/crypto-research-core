package io.cryptoresearch;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.signal.api.*;
import tools.jackson.databind.json.JsonMapper;

/** Real loopback HTTP, unchanged public composition and complete isolated V18 facts. */
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "server.address=127.0.0.1","crypto.research.signal.operator-screen.enabled=true",
    "crypto.research.marketdata.capture.enabled=false","crypto.research.marketdata.capture.continuous-enabled=false",
    "crypto.research.marketdata.capture.configuration-file=disabled-must-not-read.json"})
@Import({RetainedPoolDiagnosticIT.Clocks.class,PumpSwapLocalOperatorScreenIT.Gates.class})
class PumpSwapLocalOperatorScreenIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired JdbcClient jdbc;
    @Autowired MarketDataApi marketdata;
    @Autowired SignalGate gate;
    @LocalServerPort int port;
    static final JsonMapper JSON=JsonMapper.builder().build();
    final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @TestConfiguration static class Gates {
        @Bean static SignalGate signalGate(){return new SignalGate();}
    }
    /** An ordinary public API barrier, only in the isolated test composition. */
    static class SignalGate implements BeanPostProcessor {
        final AtomicInteger calls=new AtomicInteger();
        volatile CountDownLatch entered;
        volatile CountDownLatch release;
        public Object postProcessAfterInitialization(Object bean,String name) {
            if(!(bean instanceof SignalApi))return bean;
            return java.lang.reflect.Proxy.newProxyInstance(SignalApi.class.getClassLoader(),new Class<?>[]{SignalApi.class},(proxy,method,args)->{
                if(method.getName().equals("diagnoseRetainedPumpSwapNativePriceRise")) {
                    calls.incrementAndGet();var observed=entered;var barrier=release;
                    if(observed!=null&&barrier!=null){observed.countDown();if(!barrier.await(5,TimeUnit.SECONDS))throw new AssertionError("finite HTTP fixture barrier");}
                }
                try{return method.invoke(bean,args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
            });
        }
    }
    HttpResponse<String> request(int selectedPort,String route,boolean post,boolean manual)throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+selectedPort+route)).timeout(Duration.ofSeconds(15));
        if(manual)builder.header("X-Operator-Request","local-manual-v1");
        if(post)builder.POST(HttpRequest.BodyPublishers.noBody());else builder.GET();
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    String query(UUID session) {
        var fields=new LinkedHashMap<String,String>();fields.put("session",session.toString());fields.put("receivedFrom",RetainedPoolDiagnosticIT.F.plusSeconds(7).toString());fields.put("receivedTo",RetainedPoolDiagnosticIT.T.minusSeconds(11).toString());fields.put("eventFrom",RetainedPoolDiagnosticIT.F.toString());fields.put("eventTo",RetainedPoolDiagnosticIT.T.toString());fields.put("chain","solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp");fields.put("pool",RetainedPoolDiagnosticIT.BINDING.pool());fields.put("baseMint",RetainedPoolDiagnosticIT.BINDING.baseMint());fields.put("quoteMint",RetainedPoolDiagnosticIT.BINDING.quoteMint());fields.put("eventEnd",RetainedPoolDiagnosticIT.E.toString());
        return fields.entrySet().stream().map(e->e.getKey()+"="+java.net.URLEncoder.encode(e.getValue(),java.nio.charset.StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    }
    @Test void realManualInspectionBusyFailuresAndDisabledStartupPreserveEveryRow() throws Exception {
        var fixture=new RetainedPoolDiagnosticIT();fixture.jdbc=jdbc;
        var a=fixture.source("/fixtures/pumpswap-versioned-candles/ordinary-sell.json");var b=fixture.source("/fixtures/pumpswap-pool-legs/term.json");fixture.seed(List.of(a,b));
        var publication=marketdata.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(a,b),List.of("SAVED_SYNTHETIC_COMPATIBILITY")));
        jdbc.sql("insert into marketdata.capture_gaps(session_id,pool,kind,reason,opened_at,status) values(:id,:pool,'STATE','DISCONNECT',:at,'UNRESOLVED')").param("id",a.session()).param("pool",RetainedPoolDiagnosticIT.BINDING.pool()).param("at",a.receivedAt().atOffset(ZoneOffset.UTC)).update();
        var before=fixture.rows();assertThat(jdbc.sql("show server_version").query(String.class).single()).startsWith("18.6");assertThat(jdbc.sql("select count(*) from flyway_schema_history where success").query(Integer.class).single()).isEqualTo(18);
        var monitorRoute="/operator/api/capture?session="+a.session();var diagnosticRoute="/operator/api/diagnostic?"+query(a.session());
        assertThat(request(port,"/operator",false,false).statusCode()).isEqualTo(200);
        assertThat(request(port,"/operator/operator.js",false,false).statusCode()).isEqualTo(200);
        assertThat(request(port,monitorRoute,false,false).statusCode()).isEqualTo(403);
        for(int repeat=0;repeat<2;repeat++) {
            var monitor=request(port,monitorRoute,false,true);assertThat(monitor.statusCode()).isEqualTo(200);assertThat(monitor.body()).contains("MONITOR_PRESENT","UNVERIFIED","UNKNOWN","INDEPENDENT_PANELS").doesNotContain("reader-fixture","configuration_json","rawResult");
            int prior=gate.calls.get();var response=request(port,diagnosticRoute,true,true);assertThat(response.statusCode()).isEqualTo(200);assertThat(gate.calls.get()).isEqualTo(prior+1);
            var result=JSON.readTree(response.body()).path("diagnostic");assertThat(result.path("effectiveStatus").asText()).isEqualTo("INSUFFICIENT");assertThat(result.path("effectiveReasons").toString()).contains("UNRESOLVED_STATE_GAPS:1");assertThat(result.path("R").asText()).isNotEqualTo(result.path("D").asText());assertThat(result.path("K").asText()).isEqualTo(result.path("D").asText());assertThat(result.path("reconciliation").path("operations").asText()).isEqualTo("3");assertThat(result.path("readiness").asText()).isEqualTo("DIAGNOSTIC_ONLY_NOT_ADMITTED");assertThat(fixture.rows()).isEqualTo(before);
        }
        gate.entered=new CountDownLatch(1);gate.release=new CountDownLatch(1);int prior=gate.calls.get();
        try(var executor=Executors.newSingleThreadExecutor()) {
            var first=executor.submit(()->request(port,diagnosticRoute,true,true));
            try{assertThat(gate.entered.await(5,TimeUnit.SECONDS)).isTrue();var busy=request(port,diagnosticRoute,true,true);assertThat(busy.statusCode()).isEqualTo(429);assertThat(busy.body()).contains("BUSY");assertThat(gate.calls.get()).isEqualTo(prior+1);}
            finally{gate.release.countDown();}
            assertThat(first.get(15,TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }finally{gate.entered=null;gate.release=null;}
        assertThat(request(port,diagnosticRoute,true,true).statusCode()).isEqualTo(200);assertThat(fixture.rows()).isEqualTo(before);
        var absent=UUID.fromString("00000000-0000-0000-0000-000000000099");assertThat(request(port,"/operator/api/capture?session="+absent,false,true).statusCode()).isEqualTo(404);
        var missing=request(port,"/operator/api/diagnostic?"+query(absent),true,true);assertThat(missing.statusCode()).isEqualTo(422);assertThat(missing.body()).contains("UNKNOWN","RETAINED_READ_FAILURE");assertThat(fixture.rows()).isEqualTo(before);
        assertThat(marketdata.persistedPumpSwapOperations(publication.batchKey())).contains(publication);
        assertThat(jdbc.sql("select count(*) from marketdata.capture_deployments").query(Integer.class).single()).isZero();assertThat(jdbc.sql("select count(*) from marketdata.capture_segments").query(Integer.class).single()).isZero();
        // Default-off application owns the same isolated fixture, never a persistent/managed database.
        try(var disabled=new SpringApplicationBuilder(CryptoResearchApplication.class).run(contextArguments(false,"127.0.0.1"))) {
            int disabledPort=((WebServerApplicationContext)disabled).getWebServer().getPort();
            for(var route:List.of("/operator","/operator/operator.js",monitorRoute,diagnosticRoute))assertThat(request(disabledPort,route,route.equals(diagnosticRoute),true).statusCode()).isEqualTo(404);
            assertThat(fixture.rows()).isEqualTo(before);
        }
        assertThatThrownBy(()->{try(var unsafe=new SpringApplicationBuilder(CryptoResearchApplication.class).run(contextArguments(true,"0.0.0.0"))){throw new AssertionError("unsafe screen started");}}).hasStackTraceContaining("OPERATOR_UNSAFE_STARTUP");assertThat(fixture.rows()).isEqualTo(before);
        jdbc.sql("insert into marketdata.capture_dispositions(session_id,pool,signature,slot,status,notification,received_at) select :id,:pool,'overflow-'||n,0,'PENDING','{}',:at from generate_series(1,4097) n")
            .param("id",a.session()).param("pool",RetainedPoolDiagnosticIT.BINDING.pool()).param("at",RetainedPoolDiagnosticIT.F.plusSeconds(8).atOffset(ZoneOffset.UTC)).update();
        var overflowBefore=fixture.rows();var limited=request(port,diagnosticRoute,true,true);assertThat(limited.statusCode()).isEqualTo(422);var observations=JSON.readTree(limited.body()).path("observations");assertThat(observations.path("signatures").path("value").asText()).isEqualTo("4097");assertThat(observations.path("signatures").path("qualification").asText()).isEqualTo("AT_LEAST");assertThat(observations.path("sourceProofBytes").path("value").isNull()).isTrue();assertThat(observations.path("sourceProofBytes").path("qualification").asText()).isEqualTo("UNKNOWN");assertThat(fixture.rows()).isEqualTo(overflowBefore);
    }
    String[] contextArguments(boolean enabled,String address) {
        var arguments=new ArrayList<>(List.of("--server.port=0","--server.address="+address,
            "--crypto.research.marketdata.capture.enabled=false","--crypto.research.marketdata.capture.continuous-enabled=false",
            "--spring.datasource.url="+postgres.getJdbcUrl(),"--spring.datasource.username="+postgres.getUsername(),"--spring.datasource.password="+postgres.getPassword(),"--spring.main.banner-mode=off"));
        if(enabled)arguments.add("--crypto.research.signal.operator-screen.enabled=true");
        return arguments.toArray(String[]::new);
    }
}
