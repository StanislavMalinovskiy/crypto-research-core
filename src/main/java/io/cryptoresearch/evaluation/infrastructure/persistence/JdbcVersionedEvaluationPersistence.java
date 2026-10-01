package io.cryptoresearch.evaluation.infrastructure.persistence;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.cryptoresearch.evaluation.api.EvaluationApi.EntryOutcome;
import io.cryptoresearch.evaluation.api.EvaluationApi.EvaluationReport;
import io.cryptoresearch.evaluation.api.EvaluationApi.VersionedEvaluationReport;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcVersionedEvaluationPersistence {

	private final JdbcClient jdbc;
	private final ObjectMapper json;

	public JdbcVersionedEvaluationPersistence(JdbcClient jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public VersionedEvaluationReport store(VersionedEvaluationReport result,
			String entryRevision, String horizonRevision) {
		var report = result.legacyReport();
		if (report.outcomes().size() != 1) {
			throw new IllegalArgumentException("First-slice versioned report requires one outcome");
		}
		var outcome = report.outcomes().getFirst();
		var provenance = report.provenance();
		var outcomeEvidence = outcomeEvidence(result, outcome, entryRevision, horizonRevision);
		var reportContent = reportContent(result);
		jdbc.sql("""
				INSERT INTO evaluation.v2_evaluation_runs
				(run_id, signal_id, horizon, build_identity, source_revision, source_dirty,
				 algorithm_version, configuration_fingerprint, decision_dataset_fingerprint,
				 evaluation_dataset_fingerprint, evaluation_cutoff, random_seed, evidence_fingerprint)
				VALUES (:run, :signal, :horizon, :build, :source, :dirty, :algorithm, :configuration,
				 :decision, :evaluation, :cutoff, :seed, :run)
				ON CONFLICT (run_id) DO NOTHING
				""").param("run", report.runId()).param("signal", outcome.signalId())
				.param("horizon", outcome.horizon()).param("build", provenance.buildIdentity())
				.param("source", provenance.sourceRevision()).param("dirty", provenance.sourceDirty())
				.param("algorithm", provenance.algorithmVersion())
				.param("configuration", provenance.configurationFingerprint())
				.param("decision", result.decisionDatasetFingerprint())
				.param("evaluation", result.evaluationDatasetFingerprint())
				.param("cutoff", timestamp(provenance.evaluationCutoff()))
				.param("seed", provenance.seed().isPresent() ? provenance.seed().getAsLong() : null, Types.BIGINT)
				.update();
		assertMatch("run", jdbc.sql("""
				SELECT 1 FROM evaluation.v2_evaluation_runs WHERE run_id = :run
				AND signal_id = :signal AND horizon = :horizon AND build_identity = :build
				AND source_revision = :source AND source_dirty = :dirty AND algorithm_version = :algorithm
				AND configuration_fingerprint = :configuration
				AND decision_dataset_fingerprint = :decision
				AND evaluation_dataset_fingerprint = :evaluation AND evaluation_cutoff = :cutoff
				AND random_seed IS NOT DISTINCT FROM :seed AND evidence_fingerprint = :run
				""").param("run", report.runId()).param("signal", outcome.signalId())
				.param("horizon", outcome.horizon()).param("build", provenance.buildIdentity())
				.param("source", provenance.sourceRevision()).param("dirty", provenance.sourceDirty())
				.param("algorithm", provenance.algorithmVersion())
				.param("configuration", provenance.configurationFingerprint())
				.param("decision", result.decisionDatasetFingerprint())
				.param("evaluation", result.evaluationDatasetFingerprint())
				.param("cutoff", timestamp(provenance.evaluationCutoff()))
				.param("seed", provenance.seed().isPresent() ? provenance.seed().getAsLong() : null, Types.BIGINT));

		jdbc.sql("""
				INSERT INTO evaluation.v2_entry_outcomes
				(outcome_id, run_id, signal_id, horizon, pricing_status, missing_price_reason,
				 entry_revision_key, horizon_revision_key, entry_price_usd, entry_liquidity_usd,
				 entry_confidence, entry_observed_at, horizon_price_usd, horizon_liquidity_usd,
				 horizon_confidence, horizon_observed_at, gross_return, friction, net_return,
				 evidence, evidence_fingerprint)
				VALUES (:outcome, :run, :signal, :horizon, :status, :reason,
				 :entryRevision, :horizonRevision, :entryPrice, :entryLiquidity, :entryConfidence,
				 :entryObserved, :horizonPrice, :horizonLiquidity, :horizonConfidence,
				 :horizonObserved, :gross, :friction, :net, CAST(:evidence AS JSONB), :outcome)
				ON CONFLICT (outcome_id) DO NOTHING
				""").param("outcome", outcome.outcomeId()).param("run", report.runId())
				.param("signal", outcome.signalId()).param("horizon", outcome.horizon())
				.param("status", outcome.status().name())
				.param("reason", outcome.missingPriceReason().orElse(null), Types.VARCHAR)
				.param("entryRevision", entryRevision, Types.VARCHAR)
				.param("horizonRevision", horizonRevision, Types.VARCHAR)
				.param("entryPrice", outcome.entryPrice().map(p -> p.priceUsd()).orElse(null), Types.NUMERIC)
				.param("entryLiquidity", outcome.entryPrice().map(p -> p.liquidityUsd()).orElse(null), Types.NUMERIC)
				.param("entryConfidence", outcome.entryPrice().map(p -> p.confidence()).orElse(null), Types.NUMERIC)
				.param("entryObserved", outcome.entryPrice().map(p -> timestamp(p.observedAt())).orElse(null), Types.TIMESTAMP_WITH_TIMEZONE)
				.param("horizonPrice", outcome.horizonPrice().map(p -> p.priceUsd()).orElse(null), Types.NUMERIC)
				.param("horizonLiquidity", outcome.horizonPrice().map(p -> p.liquidityUsd()).orElse(null), Types.NUMERIC)
				.param("horizonConfidence", outcome.horizonPrice().map(p -> p.confidence()).orElse(null), Types.NUMERIC)
				.param("horizonObserved", outcome.horizonPrice().map(p -> timestamp(p.observedAt())).orElse(null), Types.TIMESTAMP_WITH_TIMEZONE)
				.param("gross", outcome.grossReturn().orElse(null), Types.NUMERIC)
				.param("friction", outcome.friction().orElse(null), Types.NUMERIC)
				.param("net", outcome.netReturn().orElse(null), Types.NUMERIC)
				.param("evidence", outcomeEvidence).update();
		assertMatch("outcome", jdbc.sql("""
				SELECT 1 FROM evaluation.v2_entry_outcomes WHERE outcome_id = :outcome
				AND run_id = :run AND signal_id = :signal AND horizon = :horizon
				AND pricing_status = :status AND missing_price_reason IS NOT DISTINCT FROM :reason
				AND entry_revision_key IS NOT DISTINCT FROM :entryRevision
				AND horizon_revision_key IS NOT DISTINCT FROM :horizonRevision
				AND entry_price_usd IS NOT DISTINCT FROM :entryPrice
				AND entry_liquidity_usd IS NOT DISTINCT FROM :entryLiquidity
				AND entry_confidence IS NOT DISTINCT FROM :entryConfidence
				AND entry_observed_at IS NOT DISTINCT FROM :entryObserved
				AND horizon_price_usd IS NOT DISTINCT FROM :horizonPrice
				AND horizon_liquidity_usd IS NOT DISTINCT FROM :horizonLiquidity
				AND horizon_confidence IS NOT DISTINCT FROM :horizonConfidence
				AND horizon_observed_at IS NOT DISTINCT FROM :horizonObserved
				AND gross_return IS NOT DISTINCT FROM :gross
				AND friction IS NOT DISTINCT FROM :friction AND net_return IS NOT DISTINCT FROM :net
				AND evidence = CAST(:evidence AS JSONB) AND evidence_fingerprint = :outcome
				""").param("outcome", outcome.outcomeId()).param("run", report.runId())
				.param("signal", outcome.signalId()).param("horizon", outcome.horizon())
				.param("status", outcome.status().name())
				.param("reason", outcome.missingPriceReason().orElse(null), Types.VARCHAR)
				.param("entryRevision", entryRevision, Types.VARCHAR)
				.param("horizonRevision", horizonRevision, Types.VARCHAR)
				.param("entryPrice", outcome.entryPrice().map(p -> p.priceUsd()).orElse(null), Types.NUMERIC)
				.param("entryLiquidity", outcome.entryPrice().map(p -> p.liquidityUsd()).orElse(null), Types.NUMERIC)
				.param("entryConfidence", outcome.entryPrice().map(p -> p.confidence()).orElse(null), Types.NUMERIC)
				.param("entryObserved", outcome.entryPrice().map(p -> timestamp(p.observedAt())).orElse(null), Types.TIMESTAMP_WITH_TIMEZONE)
				.param("horizonPrice", outcome.horizonPrice().map(p -> p.priceUsd()).orElse(null), Types.NUMERIC)
				.param("horizonLiquidity", outcome.horizonPrice().map(p -> p.liquidityUsd()).orElse(null), Types.NUMERIC)
				.param("horizonConfidence", outcome.horizonPrice().map(p -> p.confidence()).orElse(null), Types.NUMERIC)
				.param("horizonObserved", outcome.horizonPrice().map(p -> timestamp(p.observedAt())).orElse(null), Types.TIMESTAMP_WITH_TIMEZONE)
				.param("gross", outcome.grossReturn().orElse(null), Types.NUMERIC)
				.param("friction", outcome.friction().orElse(null), Types.NUMERIC)
				.param("net", outcome.netReturn().orElse(null), Types.NUMERIC)
				.param("evidence", outcomeEvidence));

		jdbc.sql("""
				INSERT INTO evaluation.v2_evaluation_reports
				(report_id, run_id, family, signal_count, priced_outcome_count, unpriced_outcome_count,
				 average_net_return, ordered_content, report_fingerprint)
				VALUES (:report, :run, :family, :signals, :priced, :unpriced, :average,
				 CAST(:content AS JSONB), :report)
				ON CONFLICT (report_id) DO NOTHING
				""").param("report", report.reportFingerprint()).param("run", report.runId())
				.param("family", report.family()).param("signals", report.signalCount())
				.param("priced", report.pricedOutcomeCount()).param("unpriced", report.unpricedOutcomeCount())
				.param("average", report.averageNetReturn().orElse(null), Types.NUMERIC)
				.param("content", reportContent).update();
		assertMatch("report", jdbc.sql("""
				SELECT 1 FROM evaluation.v2_evaluation_reports WHERE report_id = :report
				AND run_id = :run AND family = :family AND signal_count = :signals
				AND priced_outcome_count = :priced AND unpriced_outcome_count = :unpriced
				AND average_net_return IS NOT DISTINCT FROM :average
				AND ordered_content = CAST(:content AS JSONB) AND report_fingerprint = :report
				""").param("report", report.reportFingerprint()).param("run", report.runId())
				.param("family", report.family()).param("signals", report.signalCount())
				.param("priced", report.pricedOutcomeCount()).param("unpriced", report.unpricedOutcomeCount())
				.param("average", report.averageNetReturn().orElse(null), Types.NUMERIC)
				.param("content", reportContent));
		return result;
	}

	private String outcomeEvidence(VersionedEvaluationReport result, EntryOutcome outcome,
			String entryRevision, String horizonRevision) {
		var root = json.createObjectNode();
		root.put("schemaVersion", "versioned-entry-outcome-v1");
		root.put("decisionDatasetFingerprint", result.decisionDatasetFingerprint());
		root.put("evaluationDatasetFingerprint", result.evaluationDatasetFingerprint());
		root.put("status", outcome.status().name());
		root.put("missingReason", outcome.missingPriceReason().orElse(""));
		root.put("entryRevision", entryRevision == null ? "" : entryRevision);
		root.put("horizonRevision", horizonRevision == null ? "" : horizonRevision);
		root.put("entryPriceUsd", outcome.entryPrice().map(p -> p.priceUsd().toPlainString()).orElse(""));
		root.put("horizonPriceUsd", outcome.horizonPrice().map(p -> p.priceUsd().toPlainString()).orElse(""));
		root.put("grossReturn", outcome.grossReturn().map(v -> v.toPlainString()).orElse(""));
		root.put("friction", outcome.friction().map(v -> v.toPlainString()).orElse(""));
		root.put("netReturn", outcome.netReturn().map(v -> v.toPlainString()).orElse(""));
		return json.writeValueAsString(root);
	}

	private String reportContent(VersionedEvaluationReport result) {
		var report = result.legacyReport();
		var root = json.createObjectNode();
		root.put("schemaVersion", "versioned-first-evidence-report-v1");
		root.put("decisionDatasetFingerprint", result.decisionDatasetFingerprint());
		root.put("evaluationDatasetFingerprint", result.evaluationDatasetFingerprint());
		root.set("decisionScope", json.valueToTree(result.decisionSnapshot().scope()));
		root.set("evaluationScope", json.valueToTree(result.evaluationSnapshot().scope()));
		root.set("decisionExclusions", json.valueToTree(result.decisionSnapshot().excluded()));
		root.set("evaluationExclusions", json.valueToTree(result.evaluationSnapshot().excluded()));
		root.put("decisionCoveredKeyCount", result.decisionSnapshot().coveredKeyCount());
		root.put("evaluationCoveredKeyCount", result.evaluationSnapshot().coveredKeyCount());
		root.put("family", report.family());
		root.put("signalCount", report.signalCount());
		root.put("pricedOutcomeCount", report.pricedOutcomeCount());
		root.put("unpricedOutcomeCount", report.unpricedOutcomeCount());
		root.put("averageNetReturn", report.averageNetReturn().map(v -> v.toPlainString()).orElse(""));
		var outcomes = root.putArray("orderedOutcomes");
		report.outcomes().forEach(outcome -> outcomes.add(outcome.outcomeId()));
		return json.writeValueAsString(root);
	}

	private void assertMatch(String kind, JdbcClient.StatementSpec statement) {
		if (!statement.query(Integer.class).optional().isPresent()) {
			throw new IllegalStateException("Versioned " + kind + " immutable aggregate conflict");
		}
	}

	private OffsetDateTime timestamp(java.time.Instant value) {
		return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
	}
}
