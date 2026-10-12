package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties="spring.flyway.target=14")
class PumpSwapContinuousCaptureMigrationIT {
	@Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
	@Autowired JdbcClient jdbc;
	@Autowired DataSource dataSource;
	@Autowired MarketDataApi api;
	@Test void absentV13CapabilityIsNotCachedAndSameStoreSeesForwardV15() {
		jdbc.sql("create database continuous_fence_v13").update();
		var isolated=new org.springframework.jdbc.datasource.DriverManagerDataSource(postgres.getJdbcUrl().replace("/"+postgres.getDatabaseName(),"/continuous_fence_v13"),postgres.getUsername(),postgres.getPassword());
		Flyway.configure().dataSource(isolated).locations("classpath:db/migration").target("13").load().migrate();
		var count=new java.util.concurrent.atomic.AtomicInteger();var capture=PumpSwapContinuousCaptureIT.countedStore(isolated,count);
		var id=UUID.randomUUID();var at=java.time.Instant.parse("2026-10-07T10:00:00Z");
		for(int i=0;i<2;i++) {
			count.set(0);assertThatThrownBy(()->capture.fence(id,"absent",at,at.plusSeconds(60))).hasMessage("CAPTURE_LEASE_LOST");
			assertThat(count.get()).as("absent V13 probes each time then executes legacy SQL without referencing V15").isEqualTo(2);
		}
		Flyway.configure().dataSource(isolated).locations("classpath:db/migration").load().migrate();
		count.set(0);assertThatThrownBy(()->capture.fence(id,"absent",at,at.plusSeconds(60))).hasMessage("CAPTURE_LEASE_LOST");
		assertThat(count.get()).as("same store detects forward migration without restart").isEqualTo(2);
		count.set(0);assertThatThrownBy(()->capture.fence(id,"absent",at,at.plusSeconds(60))).hasMessage("CAPTURE_LEASE_LOST");
		assertThat(count.get()).as("positive capability alone is memoized").isEqualTo(1);
	}
	@Test void populatedV14ForwardMigrationPreservesEveryHistoricalRowAndExactStoredRevision() {
		var source=PumpSwapRawOperationDecoderTest.source(PumpSwapRawOperationDecoderTest.fixture("1","buy",true,1),"v14-original-clocks");
		jdbc.sql("""
			insert into marketdata.raw_transactions(chain_id,transaction_value,provider,payload,payload_hash,observed_block_position,source_event_time,received_at,admitted_at,ingested_at)
			values(:chain,:signature,:provider,:payload,:digest,:slot,:source,:received,:admitted,:ingested)
			""").param("chain",source.chain().value()).param("signature",source.signature()).param("provider",source.provider())
			.param("payload",source.rawResult()).param("digest",source.rawDigest()).param("slot",source.slot())
			.param("source",source.sourceTime().map(t->t.atOffset(java.time.ZoneOffset.UTC)).orElse(null),java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
			.param("received",source.receivedAt().atOffset(java.time.ZoneOffset.UTC)).param("admitted",source.admittedAt().atOffset(java.time.ZoneOffset.UTC))
			.param("ingested",source.ingestedAt().atOffset(java.time.ZoneOffset.UTC)).update();
		var batch=api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(source),List.of("V14_GAPS_UNKNOWN")));
		jdbc.sql("insert into marketdata.capture_periods(account_period,authority_json,ceiling_hundredths,reserved_hundredths,known_overage_hundredths,period_end) values('historical-v14','{\"literal\":\"immutable authority\"}',1000,700,13,'2026-11-01T00:00:00Z')").update();
		jdbc.sql("""
			insert into marketdata.capture_sessions(session_id,configuration_json,configuration_digest,account_period,owner_authorization,holder,locked_until,started_at,ends_at,state,reason,reserved_hundredths)
			values('11111111-1111-1111-1111-111111111111','{"literal":"unknown historical dimensions"}',:digest,'historical-v14','old-authority','old-holder','2026-10-01T06:00:00Z','2026-10-01T00:00:00.123456Z','2026-10-01T06:00:00Z','STOPPED','OWNER_STOP',700)
			""").param("digest","sha256:"+"1".repeat(64)).update();
		var historical=tables();var before=rows(historical);
		var migrated=Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		assertThat(migrated.migrationsExecuted).isEqualTo(3);assertThat(rows(historical)).isEqualTo(before);
		assertThat(api.persistedPumpSwapOperations(batch.batchKey()).orElseThrow()).isEqualTo(batch);
		assertThat(tables()).containsExactlyInAnyOrderElementsOf(java.util.stream.Stream.concat(historical.stream(),java.util.stream.Stream.of("capture_authority_amendments","capture_deployments","capture_period_continuations","capture_segments")).toList());
		assertThat(jdbc.sql("select count(*) from marketdata.capture_deployments").query(Long.class).single()).isZero();
		assertThat(jdbc.sql("select count(*) from marketdata.capture_segments").query(Long.class).single()).isZero();
		assertThat(jdbc.sql("select count(*) from pg_constraint where conrelid in ('marketdata.capture_deployments'::regclass,'marketdata.capture_segments'::regclass) and contype='f'").query(Integer.class).single()).isEqualTo(4);
		assertThat(jdbc.sql("select count(*) from pg_constraint where conrelid in ('marketdata.capture_deployments'::regclass,'marketdata.capture_segments'::regclass) and contype='f' and (confdeltype<>'a' or confupdtype<>'a')").query(Integer.class).single()).isZero();
		assertThat(jdbc.sql("select count(*) from information_schema.columns where table_schema='marketdata' and table_name in ('capture_deployments','capture_segments') and column_name in ('deployment_key','provider','account_period','owner_authorization','holder','cursor_signature','last_batch') and collation_name is distinct from 'C'").query(Integer.class).single()).isZero();
		assertThat(jdbc.sql("select version from flyway_schema_history where success order by installed_rank desc limit 1").query(String.class).single()).isEqualTo("17");
		assertThat(rows(historical)).isEqualTo(before);
	}
	List<String> tables() {return jdbc.sql("select tablename from pg_tables where schemaname='marketdata' order by tablename collate \"C\"").query(String.class).list();}
	Map<String,List<String>> rows(List<String> names) {
		var rows=new TreeMap<String,List<String>>();for(var name:names)rows.put(name,jdbc.sql("select row_to_json(t)::text from marketdata."+name+" t order by row_to_json(t)::text collate \"C\"").query(String.class).list());return rows;
	}
}
