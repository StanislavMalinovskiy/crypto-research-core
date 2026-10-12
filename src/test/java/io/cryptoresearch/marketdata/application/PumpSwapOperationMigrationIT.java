package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties="spring.flyway.target=13")
class PumpSwapOperationMigrationIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired JdbcClient jdbc;
    @Autowired DataSource dataSource;
    @Autowired io.cryptoresearch.marketdata.api.MarketDataApi api;
    @Test void populatedV13ForwardMigrationPreservesLiteralEvidenceAndEnforcesOwningShape() {
        jdbc.sql("""
            insert into marketdata.raw_transactions(chain_id,transaction_value,provider,payload,payload_hash,observed_block_position,source_event_time,received_at,admitted_at,ingested_at,parser_version)
            values('solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp','literal-v13-raw','literal-provider',' {"literal":"raw bytes unchanged"} ',
            'sha256:f458e64504e7210d3c9ce49643acc919f0c2b0ec1b0612ce6f26e9047c66324b',42,'2026-10-01T00:00:00.123456Z','2026-10-01T00:00:01.123456Z','2026-10-01T00:00:02.123456Z','2026-10-01T00:00:03.123456Z','legacy-parser')
            """).update();
        var snapshot=api.finalizeDataset(new io.cryptoresearch.marketdata.api.MarketDataApi.FinalizeDatasetRequest(
            "length-prefixed-v1",java.time.Instant.parse("2026-09-01T02:00:00Z"),List.of()));
        assertThat(snapshot.fingerprint()).isEqualTo("sha256:2a37888abef600233502fd570d39b2617b7776ab728a64746cfb52dfd3d3822d");
        jdbc.sql("""
            insert into marketdata.capture_periods(account_period,authority_json,ceiling_hundredths,reserved_hundredths,period_end)
            values('literal-period','{"authority":"unchanged"}',1000,700,'2026-11-01T00:00:00Z')
            """).update();
        jdbc.sql("""
            insert into marketdata.capture_sessions(session_id,configuration_json,configuration_digest,account_period,owner_authorization,holder,locked_until,started_at,ends_at,state,reserved_hundredths)
            values('484bec54-c92b-438f-9fe0-89f1c8150844','{"literal":"capture unchanged"}','sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef','literal-period','literal-authority','literal-holder','2026-10-01T06:00:00Z','2026-10-01T00:00:00Z','2026-10-01T06:00:00Z','STOPPED',700)
            """).update();
        jdbc.sql("""
            insert into marketdata.capture_dispositions(session_id,pool,signature,slot,status,raw_digest,notification,response,received_at,admitted_at)
            values('484bec54-c92b-438f-9fe0-89f1c8150844','literal-pool','literal-v13-raw',42,'CAPTURED','sha256:f458e64504e7210d3c9ce49643acc919f0c2b0ec1b0612ce6f26e9047c66324b','{"literal":"notification"}',' {"literal":"raw bytes unchanged"} ','2026-10-01T00:00:01.123456Z','2026-10-01T00:00:02.123456Z')
            """).update();
        var before=legacy();
        assertThat(jdbc.sql("select payload from marketdata.raw_transactions").query(String.class).single()).isEqualTo(" {\"literal\":\"raw bytes unchanged\"} ");
        assertThat(jdbc.sql("select fingerprint from marketdata.dataset_snapshots").query(String.class).single()).isEqualTo("sha256:2a37888abef600233502fd570d39b2617b7776ab728a64746cfb52dfd3d3822d");
        var migrated=Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        assertThat(migrated.migrationsExecuted).isEqualTo(3);assertThat(legacy()).isEqualTo(before);
        assertThat(jdbc.sql("select version from flyway_schema_history where success order by installed_rank desc limit 1").query(String.class).single()).isEqualTo("16");
        assertThat(jdbc.sql("select version()").query(String.class).single()).contains("PostgreSQL 18.6");
        assertThat(jdbc.sql("select count(*) from pg_tables where schemaname='marketdata' and tablename like 'pumpswap_%'").query(Integer.class).single()).isEqualTo(4);
        assertThat(jdbc.sql("select count(*) from information_schema.columns where table_schema='marketdata' and table_name like 'pumpswap_%' and column_name in('revision_key','canonical_key','transaction_revision_key','batch_key','chain_id','transaction_value','provider','raw_digest','content_digest') and collation_name is distinct from 'C'").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from pg_constraint where connamespace='marketdata'::regnamespace and conrelid::regclass::text like 'marketdata.pumpswap_%' and contype='f'").query(Integer.class).single()).isEqualTo(4);
        assertThat(jdbc.sql("select count(*) from pg_constraint where connamespace='marketdata'::regnamespace and conrelid::regclass::text like 'marketdata.pumpswap_%' and contype='f' and (confdeltype<>'a' or confupdtype<>'a')").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select indexname from pg_indexes where schemaname='marketdata' and tablename='pumpswap_operation_derivations' order by indexname").query(String.class).list()).hasSize(3).contains("pumpswap_operation_parent_idx");
        assertThatThrownBy(()->jdbc.sql("insert into marketdata.pumpswap_transaction_derivations(revision_key,chain_id,transaction_value,provider,raw_digest,content_digest,content,operation_count) values(:key,'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp','missing','missing',:key,:key,'{}',0)").param("key","sha256:"+"a".repeat(64)).update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.sql("insert into marketdata.pumpswap_decode_batches(batch_key,content_digest,content,transaction_count,operation_count) values('bad','bad','{}',0,0)").update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(legacy()).isEqualTo(before);
    }
    Map<String,List<String>> legacy() {
        var result=new TreeMap<String,List<String>>();
        for(var table:List.of("raw_transactions","dataset_snapshots","capture_periods","capture_sessions","capture_dispositions"))
            result.put(table,jdbc.sql("select row_to_json(t)::text from marketdata."+table+" t order by row_to_json(t)::text collate \"C\"").query(String.class).list());
        return result;
    }
}
