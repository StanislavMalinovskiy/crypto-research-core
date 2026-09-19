package io.cryptoresearch.evaluation.infrastructure.persistence;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.cryptoresearch.evaluation.api.EvaluationApi.EntryOutcome;
import io.cryptoresearch.evaluation.api.EvaluationApi.EvaluationReport;
import io.cryptoresearch.evaluation.api.EvaluationApi.PricingStatus;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcEvaluationPersistence {

	private final JdbcClient jdbcClient;
	private final ObjectMapper objectMapper;

	public JdbcEvaluationPersistence(JdbcClient jdbcClient, ObjectMapper objectMapper) {
		this.jdbcClient = jdbcClient;
		this.objectMapper = objectMapper;
	}

	public EvaluationReport store(EvaluationReport report) {
		if (report.outcomes().size() != 1) {
			throw new IllegalArgumentException("first-slice report must contain exactly one outcome");
		}
		var outcome = report.outcomes().getFirst();
		storeRun(report, outcome);
		storeOutcome(report.runId(), outcome);
		storeReport(report);
		return report;
	}

	private void storeRun(EvaluationReport report, EntryOutcome outcome) {
		var provenance = report.provenance();
		var inserted = jdbcClient.sql("""
				INSERT INTO evaluation.evaluation_runs (
				    run_id, signal_id, horizon, build_identity, source_revision, source_dirty,
				    algorithm_version, configuration_fingerprint, dataset_fingerprint,
				    evaluation_cutoff, random_seed, evidence_fingerprint
				) VALUES (
				    :runId, :signalId, :horizon, :buildIdentity, :sourceRevision, :sourceDirty,
				    :algorithmVersion, :configurationFingerprint, :datasetFingerprint,
				    :evaluationCutoff, :randomSeed, :evidenceFingerprint
				)
				ON CONFLICT (run_id) DO NOTHING
				RETURNING 1
				""")
				.param("runId", report.runId())
				.param("signalId", outcome.signalId())
				.param("horizon", outcome.horizon())
				.param("buildIdentity", provenance.buildIdentity())
				.param("sourceRevision", provenance.sourceRevision())
				.param("sourceDirty", provenance.sourceDirty())
				.param("algorithmVersion", provenance.algorithmVersion())
				.param("configurationFingerprint", provenance.configurationFingerprint())
				.param("datasetFingerprint", provenance.datasetFingerprint())
				.param("evaluationCutoff", timestamp(provenance.evaluationCutoff()))
				.param("randomSeed", provenance.seed().isPresent() ? provenance.seed().getAsLong() : null, Types.BIGINT)
				.param("evidenceFingerprint", report.runId())
				.query(Integer.class).optional().isPresent();
		if (!inserted) {
			assertFingerprint("evaluation.evaluation_runs", "run_id", report.runId(),
					"evidence_fingerprint", report.runId(), "evaluation run");
		}
	}

	private void storeOutcome(String runId, EntryOutcome outcome) {
		var entry = outcome.entryPrice().orElse(null);
		var horizon = outcome.horizonPrice().orElse(null);
		var statement = jdbcClient.sql("""
				INSERT INTO evaluation.entry_outcomes (
				    outcome_id, run_id, signal_id, horizon, pricing_status, missing_price_reason,
				    entry_chain_id, entry_transaction_value, entry_event_locator,
				    entry_price_usd, entry_liquidity_usd, entry_confidence, entry_provider, entry_observed_at,
				    horizon_chain_id, horizon_transaction_value, horizon_event_locator,
				    horizon_price_usd, horizon_liquidity_usd, horizon_confidence, horizon_provider, horizon_observed_at,
				    gross_return, friction, net_return, evidence, evidence_fingerprint
				) VALUES (
				    :outcomeId, :runId, :signalId, :horizon, :pricingStatus, :missingPriceReason,
				    :entryChainId, :entryTransaction, :entryLocator,
				    :entryPrice, :entryLiquidity, :entryConfidence, :entryProvider, :entryObservedAt,
				    :horizonChainId, :horizonTransaction, :horizonLocator,
				    :horizonPrice, :horizonLiquidity, :horizonConfidence, :horizonProvider, :horizonObservedAt,
				    :grossReturn, :friction, :netReturn, CAST(:evidence AS JSONB), :evidenceFingerprint
				)
				ON CONFLICT (outcome_id) DO NOTHING
				RETURNING 1
				""")
				.param("outcomeId", outcome.outcomeId())
				.param("runId", runId)
				.param("signalId", outcome.signalId())
				.param("horizon", outcome.horizon())
				.param("pricingStatus", outcome.status().name())
				.param("missingPriceReason", outcome.missingPriceReason().orElse(null), Types.VARCHAR)
				.param("grossReturn", outcome.grossReturn().orElse(null), Types.NUMERIC)
				.param("friction", outcome.friction().orElse(null), Types.NUMERIC)
				.param("netReturn", outcome.netReturn().orElse(null), Types.NUMERIC)
				.param("evidence", outcomeEvidence(outcome))
				.param("evidenceFingerprint", outcome.outcomeId());
		statement = bindPrice(statement, "entry", entry);
		statement = bindPrice(statement, "horizon", horizon);
		var inserted = statement.query(Integer.class).optional().isPresent();
		if (!inserted) {
			assertFingerprint("evaluation.entry_outcomes", "outcome_id", outcome.outcomeId(),
					"evidence_fingerprint", outcome.outcomeId(), "entry outcome");
		}
	}

	private void storeReport(EvaluationReport report) {
		var inserted = jdbcClient.sql("""
				INSERT INTO evaluation.evaluation_reports (
				    report_id, run_id, family, signal_count, priced_outcome_count,
				    unpriced_outcome_count, average_net_return, ordered_content, report_fingerprint
				) VALUES (
				    :reportId, :runId, :family, :signalCount, :pricedCount,
				    :unpricedCount, :averageNetReturn, CAST(:orderedContent AS JSONB), :reportFingerprint
				)
				ON CONFLICT (report_id) DO NOTHING
				RETURNING 1
				""")
				.param("reportId", report.reportFingerprint())
				.param("runId", report.runId())
				.param("family", report.family())
				.param("signalCount", report.signalCount())
				.param("pricedCount", report.pricedOutcomeCount())
				.param("unpricedCount", report.unpricedOutcomeCount())
				.param("averageNetReturn", report.averageNetReturn().orElse(null), Types.NUMERIC)
				.param("orderedContent", reportContent(report))
				.param("reportFingerprint", report.reportFingerprint())
				.query(Integer.class).optional().isPresent();
		if (!inserted) {
			assertFingerprint("evaluation.evaluation_reports", "report_id", report.reportFingerprint(),
					"report_fingerprint", report.reportFingerprint(), "evaluation report");
		}
	}

	private JdbcClient.StatementSpec bindPrice(
			JdbcClient.StatementSpec statement, String prefix, MarketObservation observation) {
		return statement
				.param(prefix + "ChainId", observation == null ? null : observation.identity().chain().value(), Types.VARCHAR)
				.param(prefix + "Transaction", observation == null ? null : observation.identity().transactionId().value(), Types.VARCHAR)
				.param(prefix + "Locator", observation == null ? null : observation.identity().eventId().locator(), Types.VARCHAR)
				.param(prefix + "Price", observation == null ? null : observation.priceUsd(), Types.NUMERIC)
				.param(prefix + "Liquidity", observation == null ? null : observation.liquidityUsd(), Types.NUMERIC)
				.param(prefix + "Confidence", observation == null ? null : observation.confidence(), Types.NUMERIC)
				.param(prefix + "Provider", observation == null ? null : observation.provider(), Types.VARCHAR)
				.param(prefix + "ObservedAt", observation == null ? null : timestamp(observation.observedAt()), Types.TIMESTAMP_WITH_TIMEZONE);
	}

	private void assertFingerprint(
			String table, String idColumn, String id, String fingerprintColumn, String expected, String description) {
		var actual = jdbcClient.sql("SELECT " + fingerprintColumn + " FROM " + table + " WHERE " + idColumn + " = :id")
				.param("id", id).query(String.class).optional().orElseThrow(() ->
						new IllegalStateException(description + " disappeared after uniqueness resolution"));
		if (!actual.equals(expected)) {
			throw new IllegalStateException(description + " immutable retry conflict for " + id);
		}
	}

	private String outcomeEvidence(EntryOutcome outcome) {
		var root = objectMapper.createObjectNode();
		root.put("schemaVersion", "entry-outcome-evidence-v1");
		root.put("pricingStatus", outcome.status().name());
		root.put("missingPriceReason", outcome.missingPriceReason().orElse(""));
		return objectMapper.writeValueAsString(root);
	}

	private String reportContent(EvaluationReport report) {
		var root = objectMapper.createObjectNode();
		root.put("schemaVersion", "first-evidence-report-v1");
		root.put("family", report.family());
		root.put("signalCount", report.signalCount());
		root.put("pricedOutcomeCount", report.pricedOutcomeCount());
		root.put("unpricedOutcomeCount", report.unpricedOutcomeCount());
		root.put("averageNetReturn", report.averageNetReturn().map(java.math.BigDecimal::toPlainString).orElse(""));
		var outcomes = root.putArray("orderedOutcomes");
		report.outcomes().forEach(outcome -> outcomes.add(outcome.outcomeId()));
		return objectMapper.writeValueAsString(root);
	}

	private OffsetDateTime timestamp(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
