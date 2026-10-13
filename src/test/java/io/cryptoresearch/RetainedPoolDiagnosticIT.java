package io.cryptoresearch;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.core.env.Environment;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.signal.api.*;
import tools.jackson.databind.json.JsonMapper;

/** Public retained/diagnostic APIs against unchanged real recorded raw text in isolated V18. */
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(RetainedPoolDiagnosticIT.Clocks.class)
class RetainedPoolDiagnosticIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MarketDataApi marketdata;
    @Autowired SignalApi signal;
    @Autowired JdbcClient jdbc;
    @Autowired Environment environment;
    @TestConfiguration static class Clocks{@Bean @Primary Clock diagnosticClock(){return Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC);}}
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final PumpSwapPoolLegs.PoolBinding BINDING=new PumpSwapPoolLegs.PoolBinding("5iSVs31h7HMdGGmyUvzmHDsYwV7cydD6CZJTbqzCtLii","wSqnGS6eb9AdNiGJVstrgkh1Am1oA35AovnPrCg61FL","9Dx7EA7h6Bp2YmiEDVXtJvYrArtEWoBe5qPr3sgW6R2W","BsbsB3WLq7vbY5En3MBCAsTrcwaCxNKY2Mp5pGuLpump","So11111111111111111111111111111111111111112","TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb","TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
    static final Instant F=Instant.parse("2026-10-11T11:10:00Z"),T=Instant.parse("2026-10-11T11:35:00Z"),E=Instant.parse("2026-10-11T11:20:00Z");
    @Test void populatedV18RunningPrefixAndStoppedReadAreNonmutatingAndPureReplayUsesCapturedD() throws Exception {
        var a=source("/fixtures/pumpswap-versioned-candles/ordinary-sell.json");var b=source("/fixtures/pumpswap-pool-legs/term.json");assertThat(a.session()).isEqualTo(b.session());seed(List.of(a,b));
        var published=marketdata.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(a,b),List.of("SAVED_SYNTHETIC_COMPATIBILITY")));
        var window=new PumpSwapRetainedWindow.Request(a.session(),F.plusSeconds(7),T.minusSeconds(11),F,T);var before=rows();var snapshot=marketdata.retainedPumpSwapWindow(window);
        assertThat(snapshot.batch().transactions()).hasSize(2);for(var original:List.of(a,b))assertThat(snapshot.batch().transactions()).anySatisfy(actual->{assertThat(actual.rawResult()).isEqualTo(original.rawResult());assertThat(actual.rawDigest()).isEqualTo(original.rawDigest());assertThat(actual.receivedAt()).isEqualTo(original.receivedAt());assertThat(actual.admittedAt()).isEqualTo(original.admittedAt());assertThat(actual.ingestedAt()).isEqualTo(original.ingestedAt());assertThat(actual.sourceTime()).isEqualTo(original.sourceTime());assertThat(actual.session()).isEqualTo(original.session());});
        assertThat(snapshot.readObservedAt()).isAfter(a.ingestedAt());assertThat(snapshot.manifest().distinctSignatures()).isEqualTo(2);
        var pool=new PumpSwapPoolMetrics.Pool(ChainId.SOLANA_MAINNET.value(),BINDING.pool(),BINDING.baseMint(),BINDING.quoteMint());
        var result=signal.diagnoseRetainedPumpSwapNativePriceRise(new PumpSwapRetainedNativePriceSignal.Request(window,pool,E));
        assertThat(result.derivationObservedAt()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));assertThat(result.pure().decisionKnowledgeAt()).isEqualTo(result.derivationObservedAt());assertThat(result.pure().derivationObservedAt()).isEqualTo(result.derivationObservedAt());assertThat(result.readObservedAt()).isBefore(result.derivationObservedAt());
        assertThat(result.pure().candles().transactions()).hasSize(2);assertThat(result.pure().candles().operations()).hasSize(3);assertThat(result.pure().readiness()).isEqualTo("DIAGNOSTIC_ONLY_NOT_ADMITTED");
        var replayRequest=new PumpSwapVersionedPoolCandles.Request(snapshot.batch(),snapshot.pools(),"pumpswap-pool-legs-v5",F,T,result.derivationObservedAt(),result.derivationObservedAt());
        var pure=signal.diagnosePumpSwapVersionedNativePriceRise(new PumpSwapVersionedNativePriceSignal.Request(replayRequest,pool,E));assertThat(result.pure()).isEqualTo(pure);assertThat(rows()).isEqualTo(before);
        jdbc.sql("insert into marketdata.capture_gaps(session_id,pool,kind,reason,opened_at,status) values(:id,:pool,'STATE','DISCONNECT',:at,'UNRESOLVED')").param("id",a.session()).param("pool",BINDING.pool()).param("at",a.receivedAt().atOffset(ZoneOffset.UTC)).update();var withGap=rows();var tainted=signal.diagnoseRetainedPumpSwapNativePriceRise(new PumpSwapRetainedNativePriceSignal.Request(window,pool,E));assertThat(tainted.effectiveStatus()).isEqualTo(PumpSwapVersionedNativePriceSignal.Status.INSUFFICIENT);assertThat(tainted.reasons()).contains("UNRESOLVED_STATE_GAPS:1");assertThat(tainted.pure()).isEqualTo(pure);assertThat(rows()).isEqualTo(withGap);
        jdbc.sql("update marketdata.capture_sessions set state='STOPPED',reason='OWNER_STOP' where session_id=:id").param("id",a.session()).update();var stopped=rows();assertThat(marketdata.retainedPumpSwapWindow(window).batch()).isEqualTo(snapshot.batch());assertThat(rows()).isEqualTo(stopped);
        assertThat(environment.getProperty("crypto.research.marketdata.capture.enabled",Boolean.class,false)).isFalse();assertThat(environment.getProperty("crypto.research.marketdata.capture.continuous-enabled",Boolean.class,false)).isFalse();assertThat(jdbc.sql("select count(*) from flyway_schema_history where success").query(Integer.class).single()).isEqualTo(18);assertThat(marketdata.persistedPumpSwapOperations(published.batchKey())).contains(published);
    }
    void seed(List<PumpSwapOperations.RecordedTransaction> sources)throws Exception{
        var first=sources.getFirst();var proofValues=new LinkedHashMap<String,Object>();proofValues.put("chain",ChainId.SOLANA_MAINNET.value());proofValues.put("contextSlot",100);proofValues.put("commitment","finalized");proofValues.put("pool",BINDING.pool());proofValues.put("program","pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA");proofValues.put("baseVault",BINDING.baseVault());proofValues.put("quoteVault",BINDING.quoteVault());proofValues.put("baseMint",BINDING.baseMint());proofValues.put("quoteMint",BINDING.quoteMint());proofValues.put("baseTokenProgram",BINDING.baseProgram());proofValues.put("quoteTokenProgram",BINDING.quoteProgram());var proof=JSON.writeValueAsString(proofValues);
        var pool=new PumpSwapCapture.Pool(BINDING.pool(),"pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA",BINDING.baseVault(),BINDING.quoteVault(),BINDING.baseMint(),BINDING.quoteMint(),BINDING.baseProgram(),BINDING.quoteProgram(),new PumpSwapCapture.LinkageProof(100,"finalized","operator-proof-v1","SYNTHETIC",proof,sha(proof)));
        var authority=new PumpSwapCapture.Authority("isolated-retained-period",Instant.parse("2026-11-01T00:00:00Z"),"SYNTHETIC",0L,0,10000000,new java.math.BigDecimal("50"),new java.math.BigDecimal("0.525"),new java.math.BigDecimal("0.0002"),"synthetic-retained",true,true);
        var config=new PumpSwapCapture.Configuration(first.session(),"capture-durable-inbox-v7",first.provider(),F,List.of(pool),new PumpSwapCapture.Limits(86400,10000,10000000,10000000,10000000,10000000,2000000,8000000,256,16000000,10,2,20,10,300,2,30),authority);var encoded=JSON.writeValueAsString(config);
        jdbc.sql("insert into marketdata.capture_periods(account_period,authority_json,ceiling_hundredths,reserved_hundredths,period_end) values(:p,:a,10000000,1000,:end)").param("p",authority.accountPeriod()).param("a",JSON.writeValueAsString(authority)).param("end",authority.periodEnd().atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("insert into marketdata.capture_sessions(session_id,configuration_json,configuration_digest,account_period,owner_authorization,holder,locked_until,started_at,ends_at,state,reserved_hundredths) values(:id,:c,:d,:p,'synthetic-retained','reader-fixture',:at,:at,:end,'RUNNING',1000)")
            .param("id",first.session()).param("c",encoded).param("d",configurationHash(encoded)).param("p",authority.accountPeriod()).param("at",F.atOffset(ZoneOffset.UTC)).param("end",T.atOffset(ZoneOffset.UTC)).update();
        for(var s:sources){assertThat(sha(s.rawResult())).isEqualTo(s.rawDigest());jdbc.sql("insert into marketdata.raw_transactions(chain_id,transaction_value,provider,payload,payload_hash,observed_block_position,source_event_time,received_at,admitted_at,ingested_at,parser_version) values(:chain,:sig,:provider,:raw,:digest,:slot,:source,:received,:admitted,:ingested,'pumpswap-capture-v1')")
            .param("chain",s.chain().value()).param("sig",s.signature()).param("provider",s.provider()).param("raw",s.rawResult()).param("digest",s.rawDigest()).param("slot",s.slot()).param("source",s.sourceTime().orElseThrow().atOffset(ZoneOffset.UTC)).param("received",s.receivedAt().atOffset(ZoneOffset.UTC)).param("admitted",s.admittedAt().atOffset(ZoneOffset.UTC)).param("ingested",s.ingestedAt().atOffset(ZoneOffset.UTC)).update();
            var notification="{\"method\":\"logsNotification\",\"params\":{\"result\":{\"context\":{\"slot\":"+s.slot()+"},\"value\":{\"signature\":\""+s.signature()+"\",\"err\":null}}}}";
            jdbc.sql("insert into marketdata.capture_dispositions(session_id,pool,signature,slot,status,raw_digest,notification,response,received_at,admitted_at) values(:id,:pool,:sig,:slot,'CAPTURED',:digest,:notification,:response,:received,:admitted)")
                .param("id",s.session()).param("pool",BINDING.pool()).param("sig",s.signature()).param("slot",s.slot()).param("digest",s.rawDigest()).param("notification",notification).param("response","{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":"+s.rawResult()+"}").param("received",s.receivedAt().atOffset(ZoneOffset.UTC)).param("admitted",s.admittedAt().atOffset(ZoneOffset.UTC)).update();
        }
    }
    static String sha(String s)throws Exception{return "sha256:"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    static String configurationHash(String text)throws Exception{var d=java.security.MessageDigest.getInstance("SHA-256");for(var field:List.of("contract","pumpswap-capture-configuration-v1","configuration",text)){var bytes=field.getBytes(java.nio.charset.StandardCharsets.UTF_8);d.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());d.update(bytes);}return "sha256:"+HexFormat.of().formatHex(d.digest());}
    Map<String,String> rows(){var result=new TreeMap<String,String>();for(var table:jdbc.sql("select schemaname||'.'||tablename from pg_tables where schemaname in ('marketdata','signal','evaluation') order by schemaname,tablename").query(String.class).list())result.put(table,jdbc.sql("select coalesce(jsonb_agg(x order by x::text)::text,'[]') from (select to_jsonb(t) x from "+table+" t) a").query(String.class).single());return result;}
    PumpSwapOperations.RecordedTransaction source(String path)throws Exception{try(var stream=getClass().getResourceAsStream(path)){var n=JSON.readTree(stream);return new PumpSwapOperations.RecordedTransaction(UUID.fromString(n.path("session").asText()),new ChainId(n.path("chain").asText()),n.path("signature").asText(),n.path("provider").asText(),n.path("slot").longValue(),n.path("finality").asText(),n.path("captureDisposition").asText(),n.path("sourceReference").asText(),Optional.of(Instant.parse(n.path("sourceTime").asText())),Instant.parse(n.path("receivedAt").asText()),Instant.parse(n.path("admittedAt").asText()),Instant.parse(n.path("ingestedAt").asText()),n.path("rawResult").asText(),n.path("rawDigest").asText());}}
}
