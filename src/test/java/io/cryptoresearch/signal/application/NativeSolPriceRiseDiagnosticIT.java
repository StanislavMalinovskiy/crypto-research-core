package io.cryptoresearch;

import static org.assertj.core.api.Assertions.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.signal.api.*;

/** Cross-module public-API qualification, like the existing first signal integration fixture. */
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class NativeSolPriceRiseDiagnosticIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MarketDataApi marketdata;
    @Autowired SignalApi signal;
    @Autowired JdbcClient jdbc;
    static final Instant K=Instant.parse("2030-01-01T00:00:00Z"), FROM=Instant.parse("2026-10-10T05:50:00Z"), TO=Instant.parse("2026-10-10T11:50:00Z");
    static final String SOURCE_SHA="2a934f9fecf1e8a8dab1ccfbe8c34e96ed1d51148d2b4b02080f688cdcdf1f00";
    @Test void unknownBatchIsReadOnlyInsufficientThroughPublicSignalBoundary() {
        var before=databaseFingerprint();var pool=new PumpSwapPoolMetrics.Pool(ChainId.SOLANA_MAINNET.value(),"unknown-pool","unknown-base",PumpSwapChainTimeCandles.WSOL);
        var q=new PumpSwapNativePriceSignal.Request("sha256:"+"0".repeat(64),pool,TO,K);
        var result=signal.diagnosePumpSwapNativePriceRise(q);assertThat(result.status()).isEqualTo(PumpSwapNativePriceSignal.Status.INSUFFICIENT);
        assertThat(result.reasons()).containsExactly("UNKNOWN_BATCH");assertThat(result.candles()).isEmpty();assertThat(result.readiness()).isEqualTo("DIAGNOSTIC_ONLY_NOT_ADMITTED");
        assertThat(signal.diagnosePumpSwapNativePriceRise(q)).isEqualTo(result);assertThat(databaseFingerprint()).isEqualTo(before);
    }
    @Test void configuredImmutableSavedSourceOrEmptyFixtureReconcilesWithoutAdmissionWrites() throws Exception {
        var json=JsonMapper.builder().build();var configured=System.getProperty("chainTime.savedInput");
        var sources=new ArrayList<PumpSwapOperations.RecordedTransaction>();var limitations=new ArrayList<String>();
        if(configured!=null) {
            var bytes=Files.readAllBytes(Path.of(configured));var sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));assertThat(sha).isEqualTo(SOURCE_SHA);
            var root=json.readTree(bytes);for(var row:root.path("transactions")) {
                var s=new PumpSwapOperations.RecordedTransaction(UUID.fromString(row.path("session").asText()),new ChainId(row.path("chain").asText()),row.path("signature").asText(),row.path("provider").asText(),row.path("slot").longValue(),row.path("finality").asText(),row.path("captureDisposition").asText(),row.path("sourceReference").asText(),row.path("sourceTime").isNull()?Optional.empty():Optional.of(Instant.parse(row.path("sourceTime").asText())),Instant.parse(row.path("receivedAt").asText()),Instant.parse(row.path("admittedAt").asText()),Instant.parse(row.path("ingestedAt").asText()),row.path("rawResult").asText(),row.path("rawDigest").asText());
                sources.add(s);seed(s);
            }
            for(var l:root.path("limitations"))limitations.add(l.asText());assertThat(sources).hasSize(124);
        } else limitations.add("EXPLICIT_EMPTY_SYNTHETIC_FIXTURE_NOT_SAVED_QUALIFICATION");
        var originalExportLimitations=List.copyOf(limitations);
        // Same label set as the sealed prior metrics qualification; labels participate in batch identity.
        if(configured!=null) limitations=new ArrayList<>(List.of("CAPTURE_GAPS_UNRECEIVED_LOSS_UNKNOWN","NO_MARKET_ADMISSION","FEES_MAY_BE_UNKNOWN"));
        var batch=marketdata.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(sources,limitations));
        var counts=allCounts();var before=databaseFingerprint();var stored=marketdata.persistedPumpSwapOperations(batch.batchKey()).orElseThrow();
        var observationRequest=new PumpSwapPoolMetrics.Request(batch.batchKey(),Instant.parse("2026-10-10T05:55:00Z"),Instant.parse("2026-10-10T11:55:00Z"),Instant.parse("2026-10-10T11:55:00Z"),300);
        var old=marketdata.pumpSwapPoolMetrics(observationRequest).orElseThrow();
        var candles=marketdata.pumpSwapChainTimeCandles(new PumpSwapChainTimeCandles.Request(batch.batchKey(),FROM,TO,K)).orElseThrow();
        assertThat(candles.reconciliation().transactionCount()).isEqualTo(sources.size());
        assertThat(candles.reconciliation().includedOperations()+candles.reconciliation().excludedOperations()).isEqualTo(batch.operations().size());
        assertThat(candles.transactions()).extracting(PumpSwapChainTimeCandles.TransactionDisposition::revisionKey).containsExactlyInAnyOrderElementsOf(batch.transactions().stream().map(PumpSwapOperationPersistence.TransactionRevision::revisionKey).toList());
        assertThat(candles.operations()).allSatisfy(o->{assertThat(o.clocks().knownAt()).isBeforeOrEqualTo(K);assertThat(o.clocks().knownAt()).isAfterOrEqualTo(batch.persistedAt());});
        var pools=candles.operations().stream().map(PumpSwapChainTimeCandles.OperationReference::pool).distinct().toList();
        var statuses=new EnumMap<PumpSwapNativePriceSignal.Status,Integer>(PumpSwapNativePriceSignal.Status.class);var reasons=new TreeMap<String,Integer>();var pairs=new ArrayList<Map<String,Object>>();
        for(var pool:pools)for(var e=FROM.plusSeconds(600);!e.isAfter(TO);e=e.plusSeconds(300)) {
            var q=new PumpSwapNativePriceSignal.Request(batch.batchKey(),pool,e,K);var r=signal.diagnosePumpSwapNativePriceRise(q);
            var pair=new LinkedHashMap<String,Object>();pair.put("request",q);pair.put("status",r.status());pair.put("reasons",r.reasons());pair.put("fingerprint",r.fingerprint());pair.put("candleFingerprint",r.candles().orElseThrow().fingerprint());pair.put("prior",r.prior());pair.put("current",r.current());pairs.add(pair);
            statuses.merge(r.status(),1,Integer::sum);for(var reason:r.reasons())reasons.merge(reason,1,Integer::sum);
            assertThat(r.readiness()).isEqualTo("DIAGNOSTIC_ONLY_NOT_ADMITTED");assertThat(r.analysis()).isEqualTo("RETROSPECTIVE_AT_K");assertThat(r.historicalAtEventEnd()).isEqualTo("UNAVAILABLE");
            assertThat(r.candles().orElseThrow().request()).isEqualTo(new PumpSwapChainTimeCandles.Request(batch.batchKey(),e.minusSeconds(600),e,K));
        }
        assertThat(marketdata.pumpSwapChainTimeCandles(candles.request())).contains(candles);assertThat(marketdata.pumpSwapPoolMetrics(observationRequest)).contains(old);
        assertThat(marketdata.persistedPumpSwapOperations(batch.batchKey())).contains(stored);assertThat(allCounts()).isEqualTo(counts);assertThat(databaseFingerprint()).isEqualTo(before);
        if(configured!=null) {
            assertThat(batch.batchKey()).isEqualTo("sha256:dd523aed890f0ce8903c27543514f327c49d22ae754184ccd030c358a2cf1a45");
            assertThat(old.fingerprint()).isEqualTo("sha256:1b8b76a8bcef35d9eca12f22d9dbe21864ce45130ca2d70fdf2edbc7624899c2");
            assertThat(batch.result().operations()).hasSize(68);assertThat(old.candles()).hasSize(51);assertThat(old.reconciliation().selectedOperations()).isEqualTo(68);
            assertThat(candles.reconciliation().includedOperations()).isEqualTo(68);assertThat(candles.reconciliation().excludedOperations()).isZero();assertThat(candles.reconciliation().priceEligibleOperations()).isEqualTo(54);
            assertThat(candles.reconciliation().unpricedOperations()).isEqualTo(14);assertThat(candles.operations()).filteredOn(o->o.priceReason().equals("UNSUPPORTED_QUOTE")).hasSize(14);
            assertThat(candles.transactions()).filteredOn(t->t.status()==PumpSwapOperations.Status.FAILED).hasSize(49);
            var output=System.getProperty("chainTime.qualificationOutput");if(output!=null) {
                var facts=new LinkedHashMap<String,Object>();facts.put("sourceSha256",SOURCE_SHA);facts.put("originalExportLimitations",originalExportLimitations);facts.put("qualificationBatchLimitations",limitations);facts.put("request",candles.request());facts.put("batch",stored);facts.put("oldObservationCandles",old.candles().size());facts.put("oldObservationFingerprint",old.fingerprint());facts.put("chainTime",candles);facts.put("signalStatuses",statuses);facts.put("signalReasons",reasons);facts.put("pairs",pairs);facts.put("unchangedRowCounts",counts);facts.put("unchangedDatabaseFingerprint",before);
                // Qualification artifact only, outside target; never raw-source or repository writes.
                json.writeValue(Path.of(output).toFile(),facts);
            }
            System.out.println("CHAIN_TIME_SAVED_QUALIFICATION sourceSha="+SOURCE_SHA+" batch="+batch.batchKey()+" transactions="+sources.size()+" operations="+batch.operations().size()+" oldCandles="+old.candles().size()+" eventCandles="+candles.candles().size()+" included="+candles.reconciliation().includedOperations()+" excluded="+candles.reconciliation().excludedOperations()+" priced="+candles.reconciliation().priceEligibleOperations()+" statuses="+statuses+" reasons="+reasons+" pairs="+pairs.size()+" unchangedRows=true unchangedRawAndFirstClocks=true");
            assertThat(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(configured))))).isEqualTo(SOURCE_SHA);
        } else assertThat(candles.candles()).isEmpty();
    }
    Map<String,Long> allCounts() {
        var counts=new TreeMap<String,Long>();for(var schema:List.of("marketdata","signal","evaluation"))for(var table:jdbc.sql("select tablename from pg_tables where schemaname=:schema order by tablename").param("schema",schema).query(String.class).list())counts.put(schema+"."+table,jdbc.sql("select count(*) from "+schema+"."+table).query(Long.class).single());return counts;
    }
    String databaseFingerprint() {
        var fields=new TreeMap<String,String>();for(var schema:List.of("marketdata","signal","evaluation"))for(var table:jdbc.sql("select tablename from pg_tables where schemaname=:schema order by tablename").param("schema",schema).query(String.class).list())fields.put(schema+"."+table,jdbc.sql("select md5(coalesce(string_agg(row::text,'|' order by row::text),'')) from (select to_jsonb(t) as row from "+schema+"."+table+" t) rows").query(String.class).single());
        try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(fields.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
    }
    void seed(PumpSwapOperations.RecordedTransaction s) {
        jdbc.sql("""
            insert into marketdata.raw_transactions(chain_id,transaction_value,provider,payload,payload_hash,observed_block_position,source_event_time,received_at,admitted_at,ingested_at)
            values(:chain,:signature,:provider,:payload,:digest,:slot,:sourceTime,:received,:admitted,:ingested)
            """).param("chain",s.chain().value()).param("signature",s.signature()).param("provider",s.provider()).param("payload",s.rawResult()).param("digest",s.rawDigest()).param("slot",s.slot())
            .param("sourceTime",s.sourceTime().map(t->t.atOffset(java.time.ZoneOffset.UTC)).orElse(null),java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
            .param("received",s.receivedAt().atOffset(java.time.ZoneOffset.UTC)).param("admitted",s.admittedAt().atOffset(java.time.ZoneOffset.UTC)).param("ingested",s.ingestedAt().atOffset(java.time.ZoneOffset.UTC)).update();
    }
}
