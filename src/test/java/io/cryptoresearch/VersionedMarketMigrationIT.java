package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.cryptoresearch.evaluation.api.EvaluationApi;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.signal.api.SignalApi;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.flyway.target=9")
class VersionedMarketMigrationIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	@Autowired JdbcClient jdbc;
	@Autowired DataSource dataSource;
	@Autowired MarketDataApi marketData;
	@Autowired SignalApi signal;
	@Autowired EvaluationApi evaluation;

	@Test
	void populatedV9MigratesForwardWithoutChangingLegacyEvidence() {
		LegacyMarketEvidenceFixture.populate(marketData, signal, evaluation);
		LegacyMarketEvidenceFixture.assertDurableEvidence(jdbc);
		var migrated = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		assertThat(migrated.migrationsExecuted).isEqualTo(3);
		assertThat(jdbc.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")
				.query(String.class).single()).isEqualTo("12");
		LegacyMarketEvidenceFixture.assertDurableEvidence(jdbc);
		LegacyMarketEvidenceFixture.populate(marketData, signal, evaluation);
		LegacyMarketEvidenceFixture.assertDurableEvidence(jdbc);
		for (var table : java.util.List.of("marketdata.v2_dataset_snapshots", "signal.v2_accepted_signals",
				"evaluation.v2_evaluation_runs", "evaluation.v2_entry_outcomes",
				"evaluation.v2_evaluation_reports")) {
			assertThat(jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single()).isZero();
		}
		assertThat(jdbc.sql("SELECT table_schema FROM information_schema.tables WHERE table_name = 'swap_revisions'")
				.query(String.class).single()).isEqualTo("marketdata");
		assertThat(jdbc.sql("SELECT count(*) FROM marketdata.swap_revisions").query(Integer.class).single()).isZero();
		usdRevisionRequiresBothExactPriceRevisionForeignKeys();
	}

	private void usdRevisionRequiresBothExactPriceRevisionForeignKeys() {
		var digest = "sha256:" + "1".repeat(64);
		assertThatThrownBy(() -> jdbc.sql("""
				INSERT INTO marketdata.usd_revisions
				(revision_key, chain_id, transaction_value, event_locator, asset_address,
				 converted_price_revision_key, quote_price_revision_key, method_version, price_usd,
				 computed_at, content_digest, availability_status)
				VALUES (:digest, 'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp', 'tx', 'i:1', 'asset',
				 :converted, :quote, 'method-v1', 1.000000000000000000,
				 '2026-09-01T00:00:00Z', :digest, 'HISTORICAL_MODEL')
				""").param("digest", digest).param("converted", "sha256:" + "2".repeat(64))
				.param("quote", "sha256:" + "3".repeat(64)).update())
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.sql("SELECT count(*) FROM marketdata.usd_revisions").query(Integer.class).single()).isZero();
	}
}
