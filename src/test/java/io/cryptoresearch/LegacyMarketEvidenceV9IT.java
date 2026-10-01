package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.cryptoresearch.evaluation.api.EvaluationApi;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.signal.api.SignalApi;

/** Populated PostgreSQL compatibility baseline, deliberately stopped before any forward migration. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.flyway.target=9")
class LegacyMarketEvidenceV9IT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	@Autowired
	JdbcClient jdbc;

	@Autowired
	MarketDataApi marketData;

	@Autowired
	SignalApi signal;

	@Autowired
	EvaluationApi evaluation;

	@Test
	void freezesPopulatedLegacySnapshotSignalPricedAndUnpricedEvidence() {
		assertThat(jdbc.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
				.query(String.class).list()).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
		for (var table : List.of("evaluation.evaluation_reports", "evaluation.entry_outcomes", "evaluation.evaluation_runs",
				"signal.accepted_signals", "signal.signal_candidates", "marketdata.dataset_snapshot_members",
				"marketdata.dataset_snapshots", "marketdata.normalized_swaps", "marketdata.raw_chain_events")) {
			jdbc.sql("DELETE FROM " + table).update();
		}
		LegacyMarketEvidenceFixture.populate(marketData, signal, evaluation);
		LegacyMarketEvidenceFixture.assertDurableEvidence(jdbc);
	}
}
