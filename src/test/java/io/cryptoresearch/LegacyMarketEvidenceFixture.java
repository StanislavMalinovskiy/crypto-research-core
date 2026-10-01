package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;

import io.cryptoresearch.evaluation.api.EvaluationApi;
import io.cryptoresearch.evaluation.api.EvaluationApi.EvaluationRequest;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.FinalizeDatasetRequest;
import io.cryptoresearch.signal.api.SignalApi;
import io.cryptoresearch.signal.api.SignalApi.DetectionRequest;

/** Literal V1–V9 evidence from the recorded first-signal scenario; reusable after forward migration. */
final class LegacyMarketEvidenceFixture {

	static final String DATASET = "sha256:453478688fa1ef28562e56e08f79bcc6e148b2869af00ab7538415fdf699159e";
	static final String SIGNAL = "sha256:d35413e3174b3d98c71bde5aad66312706e43646f6323e2a0faa781406b8ddfd";
	static final String PRICED_RUN = "sha256:5cdced70021749ec89359f1913a1b489e06dba040dd38469fa3b4adb43f08be7";
	static final String UNPRICED_RUN = "sha256:51c706b73c2b5763a6c6104ee80fd7087e5e4d37f81f6d3c3f411b780b615758";
	static final String PRICED_OUTCOME = "sha256:538319ece19048903ae8725162ebc958fd66d43798ee0ad975eae9438f983346";
	static final String UNPRICED_OUTCOME = "sha256:8779d4dd0b99cefb794caa4c22c490da378cf0ed14367c4696a1e3ac6807e9fc";
	static final String PRICED_REPORT = "sha256:6d0aab630cb212c8329b925e97e587606cc14f34860321b2fe92f57c4bb884e6";
	static final String UNPRICED_REPORT = "sha256:0947742f8bdb709d761c4479328ac13ea6387edb6ae009d9126964d715a183ab";

	private LegacyMarketEvidenceFixture() {
	}

	static void populate(MarketDataApi marketData, SignalApi signal, EvaluationApi evaluation) {
		var fixture = new FirstSignalScenarioFixture().load();
		var replay = marketData.replay(fixture.dataset());
		var snapshot = marketData.finalizeDataset(new FinalizeDatasetRequest(
				fixture.canonicalizationVersion(), fixture.evaluationCutoff(),
				replay.items().stream().map(item -> item.identity()).toList()));
		assertThat(snapshot.fingerprint()).isEqualTo(DATASET);
		var accepted = signal.detect(new DetectionRequest(
				snapshot.fingerprint(), fixture.asset(), fixture.decisionCutoff().minusSeconds(3600),
				fixture.decisionCutoff(), fixture.riskFacts(), "liquidity-spike-v2", "liquidity-score-v1",
				fixture.configurationFingerprint())).acceptedSignal().orElseThrow();
		assertThat(accepted.signalId()).isEqualTo(SIGNAL);
		evaluation.evaluate(new EvaluationRequest(accepted.signalId(), "1h",
				fixture.provenance(snapshot.fingerprint(), fixture.evaluationCutoff())));
		evaluation.evaluate(new EvaluationRequest(accepted.signalId(), "1h",
				fixture.provenance(snapshot.fingerprint(), fixture.decisionCutoff())));
	}

	static void assertDurableEvidence(JdbcClient jdbc) {
		assertThat(strings(jdbc, "SELECT fingerprint FROM marketdata.dataset_snapshots ORDER BY fingerprint"))
				.containsExactly(DATASET);
		assertThat(strings(jdbc, """
				SELECT member_ordinal || '|' || chain_id || '|' || transaction_value || '|' || event_locator
				FROM marketdata.dataset_snapshot_members WHERE snapshot_id = :dataset ORDER BY member_ordinal
				""", DATASET)).containsExactly(
				"0|solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp|fixture-baseline-tx|instruction:1",
				"1|solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp|fixture-decision-tx|instruction:1",
				"2|solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp|fixture-entry-tx|instruction:1",
				"3|solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp|fixture-horizon-tx|instruction:1");
		assertThat(strings(jdbc, "SELECT canonicalization_version || '|' || (cutoff AT TIME ZONE 'UTC')::text FROM marketdata.dataset_snapshots"))
				.containsExactly("length-prefixed-v1|2026-09-01 02:00:00");

		assertThat(strings(jdbc, "SELECT signal_id FROM signal.accepted_signals"))
				.containsExactly(SIGNAL);
		assertThat(strings(jdbc, "SELECT candidate_id || '|' || dataset_fingerprint || '|' || baseline_transaction_value || '|' || current_transaction_value || '|' || evidence_fingerprint FROM signal.accepted_signals"))
				.containsExactly("sha256:cebc8d0623a259343ea2c94646ab8fd25044f1fafb908bd4bf070fb3bf9ac536|" + DATASET
						+ "|fixture-baseline-tx|fixture-decision-tx|" + SIGNAL);
		assertThat(strings(jdbc, "SELECT run_id FROM evaluation.evaluation_runs ORDER BY evaluation_cutoff"))
				.containsExactly(UNPRICED_RUN, PRICED_RUN);
		assertThat(strings(jdbc, "SELECT signal_id || '|' || dataset_fingerprint || '|' || evidence_fingerprint FROM evaluation.evaluation_runs ORDER BY evaluation_cutoff"))
				.containsExactly(SIGNAL + "|" + DATASET + "|" + UNPRICED_RUN,
						SIGNAL + "|" + DATASET + "|" + PRICED_RUN);

		assertThat(strings(jdbc, "SELECT outcome_id FROM evaluation.entry_outcomes ORDER BY pricing_status"))
				.containsExactly(PRICED_OUTCOME, UNPRICED_OUTCOME);
		assertThat(strings(jdbc, """
				SELECT pricing_status || '|' || coalesce(missing_price_reason, '') || '|' ||
				 coalesce(entry_transaction_value, '') || '|' || coalesce(horizon_transaction_value, '') || '|' ||
				 coalesce(entry_price_usd::text, '') || '|' || coalesce(horizon_price_usd::text, '') || '|' ||
				 coalesce(gross_return::text, '') || '|' || coalesce(friction::text, '') || '|' || coalesce(net_return::text, '')
				FROM evaluation.entry_outcomes ORDER BY pricing_status
				""")).containsExactly(
				"PRICED||fixture-entry-tx|fixture-horizon-tx|1.000000000000000000|1.200000000000000000|0.20000000|0.04000000|0.16000000",
				"UNPRICED|NO_ADMISSIBLE_ENTRY_PRICE|||||||");
		assertThat(strings(jdbc, "SELECT outcome_id || '|' || evidence_fingerprint FROM evaluation.entry_outcomes ORDER BY pricing_status"))
				.containsExactly(PRICED_OUTCOME + "|" + PRICED_OUTCOME, UNPRICED_OUTCOME + "|" + UNPRICED_OUTCOME);

		assertThat(strings(jdbc, "SELECT report_fingerprint FROM evaluation.evaluation_reports ORDER BY priced_outcome_count DESC"))
				.containsExactly(PRICED_REPORT, UNPRICED_REPORT);
		assertThat(strings(jdbc, "SELECT ordered_content::text FROM evaluation.evaluation_reports ORDER BY priced_outcome_count DESC"))
				.containsExactly(
						"{\"family\": \"LIQUIDITY_SPIKE\", \"signalCount\": 1, \"schemaVersion\": \"first-evidence-report-v1\", \"orderedOutcomes\": [\""
								+ PRICED_OUTCOME + "\"], \"averageNetReturn\": \"0.16000000\", \"pricedOutcomeCount\": 1, \"unpricedOutcomeCount\": 0}",
						"{\"family\": \"LIQUIDITY_SPIKE\", \"signalCount\": 1, \"schemaVersion\": \"first-evidence-report-v1\", \"orderedOutcomes\": [\""
								+ UNPRICED_OUTCOME + "\"], \"averageNetReturn\": \"\", \"pricedOutcomeCount\": 0, \"unpricedOutcomeCount\": 1}");
		assertThat(strings(jdbc, "SELECT report_id || '|' || run_id FROM evaluation.evaluation_reports ORDER BY priced_outcome_count DESC"))
				.containsExactly(PRICED_REPORT + "|" + PRICED_RUN, UNPRICED_REPORT + "|" + UNPRICED_RUN);
	}

	private static List<String> strings(JdbcClient jdbc, String sql) {
		return jdbc.sql(sql).query(String.class).list();
	}

	private static List<String> strings(JdbcClient jdbc, String sql, String dataset) {
		return jdbc.sql(sql).param("dataset", dataset).query(String.class).list();
	}
}
