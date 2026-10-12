package io.cryptoresearch;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.core.env.Environment;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.signal.api.*;

/** Real owning synchronous APIs; isolated PG exists only to prove the complete absence of writes. */
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class VersionedPoolCandleDiagnosticIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MarketDataApi marketdata;
    @Autowired SignalApi signal;
    @Autowired JdbcClient jdbc;
    @Autowired Environment environment;
    @Test void newReadOnlyPublicRoutesPreserveEveryOwnedTableAndOldDecoderAndRuntimeDefault() throws Exception {
        var before=tableCounts();var a=source("/fixtures/pumpswap-versioned-candles/ordinary-sell.json");var b=source("/fixtures/pumpswap-pool-legs/term.json");
        var binding=new PumpSwapPoolLegs.PoolBinding("5iSVs31h7HMdGGmyUvzmHDsYwV7cydD6CZJTbqzCtLii","wSqnGS6eb9AdNiGJVstrgkh1Am1oA35AovnPrCg61FL","9Dx7EA7h6Bp2YmiEDVXtJvYrArtEWoBe5qPr3sgW6R2W","BsbsB3WLq7vbY5En3MBCAsTrcwaCxNKY2Mp5pGuLpump","So11111111111111111111111111111111111111112","TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb","TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
        var batch=new PumpSwapOperations.RecordedBatch(List.of(a,b),List.of("INCOMPLETE","NOT_ADMITTED"));var old=marketdata.decodePumpSwapOperations(batch);
        var q=new PumpSwapVersionedPoolCandles.Request(batch,List.of(binding),"pumpswap-pool-legs-v5",Instant.parse("2026-10-11T11:10:00Z"),Instant.parse("2026-10-11T11:35:00Z"),Instant.parse("2026-10-12T14:00:00Z"),Instant.parse("2030-01-01T00:00:00Z"));
        var report=marketdata.pumpSwapVersionedPoolCandles(q);assertThat(report.transactions()).hasSize(2);assertThat(report.operations()).hasSize(3);
        assertThat(marketdata.pumpSwapVersionedPoolCandles(q)).isEqualTo(report);
        var pool=new PumpSwapPoolMetrics.Pool(ChainId.SOLANA_MAINNET.value(),binding.pool(),binding.baseMint(),binding.quoteMint());
        var signalRequest=new PumpSwapVersionedNativePriceSignal.Request(q,pool,Instant.parse("2026-10-11T11:20:00Z"));
        var diagnosis=signal.diagnosePumpSwapVersionedNativePriceRise(signalRequest);
        assertThat(signal.diagnosePumpSwapVersionedNativePriceRise(signalRequest)).isEqualTo(diagnosis);
        assertThat(diagnosis.readiness()).isEqualTo("DIAGNOSTIC_ONLY_NOT_ADMITTED");
        assertThat(diagnosis.candles().transactions()).hasSize(2);assertThat(diagnosis.analysis()).isEqualTo("RETROSPECTIVE_AT_K");
        assertThat(marketdata.decodePumpSwapOperations(batch)).isEqualTo(old);assertThat(tableCounts()).isEqualTo(before);
        assertThat(environment.getProperty("crypto.research.marketdata.capture.enabled",Boolean.class,false)).isFalse();
        assertThat(environment.getProperty("crypto.research.marketdata.capture.continuous-enabled",Boolean.class,false)).isFalse();
        assertThat(jdbc.sql("select count(*) from marketdata.capture_sessions").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from marketdata.capture_deployments").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select version()").query(String.class).single()).contains("PostgreSQL 18.6");
        assertThat(jdbc.sql("select count(*) from flyway_schema_history where success").query(Integer.class).single()).isEqualTo(16);
    }
    private Map<String,Long> tableCounts(){var result=new TreeMap<String,Long>();for(var table:jdbc.sql("select schemaname||'.'||tablename from pg_tables where schemaname in ('marketdata','signal','evaluation') order by schemaname,tablename").query(String.class).list())result.put(table,jdbc.sql("select count(*) from "+table).query(Long.class).single());return result;}
    private PumpSwapOperations.RecordedTransaction source(String path) throws Exception {
        try(var stream=getClass().getResourceAsStream(path)){JsonNode n=JsonMapper.builder().build().readTree(stream);return new PumpSwapOperations.RecordedTransaction(UUID.fromString(n.path("session").asText()),new ChainId(n.path("chain").asText()),n.path("signature").asText(),n.path("provider").asText(),n.path("slot").longValue(),n.path("finality").asText(),n.path("captureDisposition").asText(),n.path("sourceReference").asText(),Optional.of(Instant.parse(n.path("sourceTime").asText())),Instant.parse(n.path("receivedAt").asText()),Instant.parse(n.path("admittedAt").asText()),Instant.parse(n.path("ingestedAt").asText()),n.path("rawResult").asText(),n.path("rawDigest").asText());}
    }
}
