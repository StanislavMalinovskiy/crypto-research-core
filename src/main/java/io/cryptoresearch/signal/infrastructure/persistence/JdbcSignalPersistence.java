package io.cryptoresearch.signal.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;
import io.cryptoresearch.marketdata.api.MarketDataApi.RevisionReference;
import io.cryptoresearch.risk.api.RiskApi.AssetLifecycle;
import io.cryptoresearch.risk.api.RiskApi.RiskAssessment;
import io.cryptoresearch.risk.api.RiskApi.RiskDecision;
import io.cryptoresearch.risk.api.RiskApi.RiskFacts;
import io.cryptoresearch.signal.api.SignalApi.AcceptedSignalSnapshot;
import io.cryptoresearch.signal.api.SignalApi.CandidateSnapshot;
import io.cryptoresearch.signal.api.SignalApi.CandidateStatus;
import io.cryptoresearch.signal.api.SignalApi.DetectionResult;
import io.cryptoresearch.signal.api.SignalApi.ScoreReason;
import io.cryptoresearch.signal.api.SignalApi.VersionedAcceptedSignal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcSignalPersistence {

	private static final String CANDIDATE_COLUMNS = """
			candidate_id, chain_id, asset_address, family, detector_version,
			configuration_fingerprint, dataset_fingerprint, window_start, decision_cutoff,
			status, risk_decision, risk_manipulation_flags, risk_lifecycle,
			risk_liquidity_usd, risk_evidence_version, risk_evidence
			""";

	private static final String ACCEPTED_COLUMNS = """
			signal_id, candidate_id, chain_id, asset_address, family, position_type, available_at,
			dataset_fingerprint, baseline_transaction_value, baseline_event_locator,
			current_transaction_value, current_event_locator, risk_decision,
			risk_manipulation_flags, risk_lifecycle, risk_liquidity_usd, risk_evidence_version,
			risk_evidence, detector_version, scorer_version, configuration_fingerprint,
			score, grade, confidence, reasoning
			""";

	private final JdbcClient jdbcClient;
	private final ObjectMapper objectMapper;

	public JdbcSignalPersistence(JdbcClient jdbcClient, ObjectMapper objectMapper) {
		this.jdbcClient = jdbcClient;
		this.objectMapper = objectMapper;
	}

	public CandidateSnapshot recordCandidate(CandidateSnapshot candidate) {
		var inserted = jdbcClient.sql("""
				INSERT INTO signal.signal_candidates (
				    candidate_id, chain_id, asset_address, family, detector_version,
				    configuration_fingerprint, dataset_fingerprint, window_start, decision_cutoff,
				    status, evidence_fingerprint
				) VALUES (
				    :candidateId, :chainId, :asset, :family, :detectorVersion,
				    :configurationFingerprint, :datasetFingerprint, :windowStart, :decisionCutoff,
				    'DETECTED', :evidenceFingerprint
				)
				ON CONFLICT (candidate_id) DO NOTHING
				RETURNING 1
				""")
				.param("candidateId", candidate.candidateId())
				.param("chainId", candidate.asset().chain().value())
				.param("asset", candidate.asset().value())
				.param("family", candidate.family())
				.param("detectorVersion", candidate.detectorVersion())
				.param("configurationFingerprint", candidate.configurationFingerprint())
				.param("datasetFingerprint", candidate.datasetFingerprint())
				.param("windowStart", timestamp(candidate.windowStart()))
				.param("decisionCutoff", timestamp(candidate.decisionCutoff()))
				.param("evidenceFingerprint", candidate.candidateId())
				.query(Integer.class).optional().isPresent();
		if (inserted) {
			return candidate;
		}
		var existing = findCandidate(candidate.candidateId()).orElseThrow(() ->
				new IllegalStateException("Signal candidate disappeared after uniqueness resolution"));
		if (!sameCandidateIdentity(existing, candidate)) {
			throw new IllegalStateException("Signal candidate conflict for " + candidate.candidateId());
		}
		return existing;
	}

	public DetectionResult completeCandidate(
			CandidateSnapshot candidate,
			RiskAssessment assessment,
			Optional<AcceptedSignalSnapshot> acceptedSignal) {
		var expectedStatus = assessment.decision() == RiskDecision.ALLOW
				? CandidateStatus.ACCEPTED : CandidateStatus.REJECTED;
		if ((expectedStatus == CandidateStatus.ACCEPTED) != acceptedSignal.isPresent()) {
			throw new IllegalArgumentException("accepted signal presence must match ALLOW risk decision");
		}
		var riskEvidence = riskEvidence(assessment);
		jdbcClient.sql("""
				UPDATE signal.signal_candidates
				SET status = :status,
				    risk_decision = :riskDecision,
				    risk_manipulation_flags = :riskFlags,
				    risk_lifecycle = :riskLifecycle,
				    risk_liquidity_usd = :riskLiquidity,
				    risk_evidence_version = :riskEvidenceVersion,
				    risk_evidence = CAST(:riskEvidence AS JSONB)
				WHERE candidate_id = :candidateId AND status = 'DETECTED'
				""")
				.param("status", expectedStatus.name())
				.param("riskDecision", assessment.decision().name())
				.param("riskFlags", assessment.facts().manipulationFlags())
				.param("riskLifecycle", assessment.facts().lifecycle().name())
				.param("riskLiquidity", assessment.facts().liquidityUsd())
				.param("riskEvidenceVersion", assessment.facts().evidenceVersion())
				.param("riskEvidence", riskEvidence)
				.param("candidateId", candidate.candidateId())
				.update();

		var expectedCandidate = new CandidateSnapshot(
				candidate.candidateId(), candidate.family(), candidate.asset(), candidate.windowStart(),
				candidate.decisionCutoff(), candidate.datasetFingerprint(), candidate.detectorVersion(),
				candidate.configurationFingerprint(), expectedStatus, Optional.of(assessment));
		var persistedCandidate = findCandidate(candidate.candidateId()).orElseThrow();
		if (!sameCompletedCandidate(persistedCandidate, expectedCandidate)) {
			throw new IllegalStateException("Signal candidate immutable retry conflict for " + candidate.candidateId());
		}

		Optional<AcceptedSignalSnapshot> persistedSignal = Optional.empty();
		if (acceptedSignal.isPresent()) {
			persistedSignal = Optional.of(storeAccepted(acceptedSignal.orElseThrow()));
		}
		return new DetectionResult(Optional.of(persistedCandidate), persistedSignal);
	}

	public Optional<AcceptedSignalSnapshot> findAccepted(String signalId) {
		return jdbcClient.sql("SELECT " + ACCEPTED_COLUMNS + " FROM signal.accepted_signals WHERE signal_id = :signalId")
				.param("signalId", signalId)
				.query(this::mapAccepted).optional();
	}

	public void recordVersionedCandidate(CandidateSnapshot candidate) {
		var inserted = jdbcClient.sql("""
				INSERT INTO signal.v2_signal_candidates
				(candidate_id, chain_id, asset_address, family, detector_version,
				 configuration_fingerprint, decision_dataset_fingerprint, window_start,
				 decision_cutoff, status)
				VALUES (:id, :chain, :asset, :family, :detector, :configuration, :decision,
				 :windowStart, :cutoff, 'DETECTED')
				ON CONFLICT (candidate_id) DO NOTHING RETURNING 1
				""").param("id", candidate.candidateId()).param("chain", candidate.asset().chain().value())
				.param("asset", candidate.asset().value()).param("family", candidate.family())
				.param("detector", candidate.detectorVersion())
				.param("configuration", candidate.configurationFingerprint())
				.param("decision", candidate.datasetFingerprint())
				.param("windowStart", timestamp(candidate.windowStart()))
				.param("cutoff", timestamp(candidate.decisionCutoff()))
				.query(Integer.class).optional().isPresent();
		if (!inserted && !jdbcClient.sql("""
				SELECT 1 FROM signal.v2_signal_candidates WHERE candidate_id = :id
				AND chain_id = :chain AND asset_address = :asset AND family = :family
				AND detector_version = :detector AND configuration_fingerprint = :configuration
				AND decision_dataset_fingerprint = :decision AND window_start = :windowStart
				AND decision_cutoff = :cutoff
				""").param("id", candidate.candidateId()).param("chain", candidate.asset().chain().value())
				.param("asset", candidate.asset().value()).param("family", candidate.family())
				.param("detector", candidate.detectorVersion())
				.param("configuration", candidate.configurationFingerprint())
				.param("decision", candidate.datasetFingerprint())
				.param("windowStart", timestamp(candidate.windowStart()))
				.param("cutoff", timestamp(candidate.decisionCutoff()))
				.query(Integer.class).optional().isPresent()) {
			throw new IllegalStateException("Versioned candidate immutable retry conflict");
		}
	}

	public void completeVersionedCandidate(CandidateSnapshot candidate, RiskAssessment assessment,
			Optional<VersionedAcceptedSignal> accepted) {
		var status = assessment.decision() == RiskDecision.ALLOW ? "ACCEPTED" : "REJECTED";
		jdbcClient.sql("""
				UPDATE signal.v2_signal_candidates SET status = :status, risk_decision = :decision,
				 risk_manipulation_flags = :flags, risk_lifecycle = :lifecycle,
				 risk_liquidity_usd = :liquidity, risk_evidence_version = :version,
				 risk_evidence = CAST(:evidence AS JSONB)
				WHERE candidate_id = :id AND status = 'DETECTED'
				""").param("status", status).param("decision", assessment.decision().name())
				.param("flags", assessment.facts().manipulationFlags())
				.param("lifecycle", assessment.facts().lifecycle().name())
				.param("liquidity", assessment.facts().liquidityUsd())
				.param("version", assessment.facts().evidenceVersion())
				.param("evidence", riskEvidence(assessment)).param("id", candidate.candidateId()).update();
		var matching = jdbcClient.sql("""
				SELECT 1 FROM signal.v2_signal_candidates WHERE candidate_id = :id
				AND status = :status AND risk_decision = :decision
				AND risk_manipulation_flags = :flags AND risk_lifecycle = :lifecycle
				AND risk_liquidity_usd = :liquidity AND risk_evidence_version = :version
				AND risk_evidence = CAST(:evidence AS JSONB)
				""").param("id", candidate.candidateId()).param("status", status)
				.param("decision", assessment.decision().name())
				.param("flags", assessment.facts().manipulationFlags())
				.param("lifecycle", assessment.facts().lifecycle().name())
				.param("liquidity", assessment.facts().liquidityUsd())
				.param("version", assessment.facts().evidenceVersion())
				.param("evidence", riskEvidence(assessment)).query(Integer.class).optional().isPresent();
		if (!matching) {
			throw new IllegalStateException("Versioned candidate completion conflict");
		}
		accepted.ifPresent(this::storeVersionedAccepted);
	}

	private void storeVersionedAccepted(VersionedAcceptedSignal value) {
		var signal = value.signal();
		var refs = value.sourceRevisions();
		if (refs.size() != 2) {
			throw new IllegalArgumentException("Versioned signal requires two exact source revisions");
		}
		var inserted = jdbcClient.sql("""
				INSERT INTO signal.v2_accepted_signals
				(signal_id, candidate_id, chain_id, asset_address, family, position_type, available_at, decision_cutoff,
				 decision_dataset_fingerprint, baseline_transaction_value, baseline_event_locator,
				 baseline_revision_key, current_transaction_value, current_event_locator, current_revision_key,
				 risk_decision, risk_manipulation_flags, risk_lifecycle, risk_liquidity_usd,
				 risk_evidence_version, risk_evidence, detector_version, scorer_version,
				 configuration_fingerprint, score, grade, confidence, reasoning)
				VALUES (:id, :candidate, :chain, :asset, :family, :position, :available, :decisionCutoff,
				 :decision, :baselineTx, :baselineLocator, :baselineRevision,
				 :currentTx, :currentLocator, :currentRevision,
				 :riskDecision, :flags, :lifecycle, :liquidity, :riskVersion,
				 CAST(:riskEvidence AS JSONB), :detector, :scorer, :configuration,
				 :score, :grade, :confidence, CAST(:reasoning AS JSONB))
				ON CONFLICT (signal_id) DO NOTHING RETURNING 1
				""").param("id", signal.signalId()).param("candidate", signal.candidateId())
				.param("chain", signal.asset().chain().value()).param("asset", signal.asset().value())
				.param("family", signal.family()).param("position", signal.position())
				.param("available", timestamp(signal.availableAt()))
				.param("decisionCutoff", timestamp(value.decisionCutoff()))
				.param("decision", value.decisionDatasetFingerprint())
				.param("baselineTx", refs.get(0).canonicalIdentity().transactionId().value())
				.param("baselineLocator", refs.get(0).canonicalIdentity().eventId().locator())
				.param("baselineRevision", refs.get(0).revisionKey())
				.param("currentTx", refs.get(1).canonicalIdentity().transactionId().value())
				.param("currentLocator", refs.get(1).canonicalIdentity().eventId().locator())
				.param("currentRevision", refs.get(1).revisionKey())
				.param("riskDecision", signal.riskAssessment().decision().name())
				.param("flags", signal.riskAssessment().facts().manipulationFlags())
				.param("lifecycle", signal.riskAssessment().facts().lifecycle().name())
				.param("liquidity", signal.riskAssessment().facts().liquidityUsd())
				.param("riskVersion", signal.riskAssessment().facts().evidenceVersion())
				.param("riskEvidence", riskEvidence(signal.riskAssessment()))
				.param("detector", signal.detectorVersion()).param("scorer", signal.scorerVersion())
				.param("configuration", signal.configurationFingerprint())
				.param("score", signal.score()).param("grade", signal.grade())
				.param("confidence", signal.confidence()).param("reasoning", reasoning(signal.reasoning()))
				.query(Integer.class).optional().isPresent();
		if (!inserted && !findVersionedAccepted(signal.signalId()).orElseThrow().equals(value)) {
			throw new IllegalStateException("Versioned accepted signal immutable retry conflict");
		}
	}

	public Optional<VersionedAcceptedSignal> findVersionedAccepted(String signalId) {
		if (!jdbcClient.sql("SELECT to_regclass('signal.v2_accepted_signals') IS NOT NULL")
				.query(Boolean.class).single()) {
			return Optional.empty();
		}
		return jdbcClient.sql("""
				SELECT signal_id, candidate_id, chain_id, asset_address, family, position_type, available_at, decision_cutoff,
				 decision_dataset_fingerprint AS dataset_fingerprint,
				 baseline_transaction_value, baseline_event_locator, baseline_revision_key,
				 current_transaction_value, current_event_locator, current_revision_key,
				 risk_decision, risk_manipulation_flags, risk_lifecycle, risk_liquidity_usd,
				 risk_evidence_version, risk_evidence, detector_version, scorer_version,
				 configuration_fingerprint, score, grade, confidence, reasoning
				FROM signal.v2_accepted_signals WHERE signal_id = :id
				""").param("id", signalId).query((row, index) -> {
			var signal = mapAccepted(row, index);
			var sources = signal.sourceObservations();
			return new VersionedAcceptedSignal(signal, signal.datasetFingerprint(),
					row.getObject("decision_cutoff", OffsetDateTime.class).toInstant(),
					List.of(new RevisionReference(FactKind.SWAP, sources.get(0), row.getString("baseline_revision_key")),
							new RevisionReference(FactKind.SWAP, sources.get(1), row.getString("current_revision_key"))));
		}).optional();
	}

	private AcceptedSignalSnapshot storeAccepted(AcceptedSignalSnapshot signal) {
		if (signal.sourceObservations().size() != 2) {
			throw new IllegalArgumentException("LIQUIDITY_SPIKE signal requires baseline and current observations");
		}
		var baseline = signal.sourceObservations().get(0);
		var current = signal.sourceObservations().get(1);
		var assessment = signal.riskAssessment();
		var inserted = jdbcClient.sql("""
				INSERT INTO signal.accepted_signals (
				    signal_id, candidate_id, chain_id, asset_address, family, position_type, available_at,
				    dataset_fingerprint, baseline_transaction_value, baseline_event_locator,
				    current_transaction_value, current_event_locator, risk_decision,
				    risk_manipulation_flags, risk_lifecycle, risk_liquidity_usd, risk_evidence_version,
				    risk_evidence, detector_version, scorer_version, configuration_fingerprint,
				    score, grade, confidence, reasoning, evidence_fingerprint
				) VALUES (
				    :signalId, :candidateId, :chainId, :asset, :family, :positionType, :availableAt,
				    :datasetFingerprint, :baselineTransaction, :baselineLocator,
				    :currentTransaction, :currentLocator, :riskDecision,
				    :riskFlags, :riskLifecycle, :riskLiquidity, :riskEvidenceVersion,
				    CAST(:riskEvidence AS JSONB), :detectorVersion, :scorerVersion, :configurationFingerprint,
				    :score, :grade, :confidence, CAST(:reasoning AS JSONB), :evidenceFingerprint
				)
				ON CONFLICT (signal_id) DO NOTHING
				RETURNING 1
				""")
				.param("signalId", signal.signalId())
				.param("candidateId", signal.candidateId())
				.param("chainId", signal.asset().chain().value())
				.param("asset", signal.asset().value())
				.param("family", signal.family())
				.param("positionType", signal.position())
				.param("availableAt", timestamp(signal.availableAt()))
				.param("datasetFingerprint", signal.datasetFingerprint())
				.param("baselineTransaction", baseline.transactionId().value())
				.param("baselineLocator", baseline.eventId().locator())
				.param("currentTransaction", current.transactionId().value())
				.param("currentLocator", current.eventId().locator())
				.param("riskDecision", assessment.decision().name())
				.param("riskFlags", assessment.facts().manipulationFlags())
				.param("riskLifecycle", assessment.facts().lifecycle().name())
				.param("riskLiquidity", assessment.facts().liquidityUsd())
				.param("riskEvidenceVersion", assessment.facts().evidenceVersion())
				.param("riskEvidence", riskEvidence(assessment))
				.param("detectorVersion", signal.detectorVersion())
				.param("scorerVersion", signal.scorerVersion())
				.param("configurationFingerprint", signal.configurationFingerprint())
				.param("score", signal.score())
				.param("grade", signal.grade())
				.param("confidence", signal.confidence())
				.param("reasoning", reasoning(signal.reasoning()))
				.param("evidenceFingerprint", signal.signalId())
				.query(Integer.class).optional().isPresent();
		if (inserted) {
			return signal;
		}
		var existing = findAccepted(signal.signalId()).orElseThrow(() ->
				new IllegalStateException("Accepted signal disappeared after uniqueness resolution"));
		if (!existing.equals(signal)) {
			throw new IllegalStateException("Accepted signal immutable retry conflict for " + signal.signalId());
		}
		return existing;
	}

	private Optional<CandidateSnapshot> findCandidate(String candidateId) {
		return jdbcClient.sql("SELECT " + CANDIDATE_COLUMNS + " FROM signal.signal_candidates WHERE candidate_id = :candidateId")
				.param("candidateId", candidateId)
				.query(this::mapCandidate).optional();
	}

	private CandidateSnapshot mapCandidate(ResultSet resultSet, int rowNumber) throws SQLException {
		var chain = new ChainId(resultSet.getString("chain_id"));
		var asset = new AssetId(chain, resultSet.getString("asset_address"));
		var decision = resultSet.getString("risk_decision");
		Optional<RiskAssessment> assessment = decision == null ? Optional.empty() : Optional.of(new RiskAssessment(
				RiskDecision.valueOf(decision),
				new RiskFacts(
						asset,
						resultSet.getObject("decision_cutoff", OffsetDateTime.class).toInstant(),
						resultSet.getInt("risk_manipulation_flags"),
						AssetLifecycle.valueOf(resultSet.getString("risk_lifecycle")),
						resultSet.getBigDecimal("risk_liquidity_usd"),
						resultSet.getString("risk_evidence_version")),
				riskReasons(resultSet.getString("risk_evidence"))));
		return new CandidateSnapshot(
				resultSet.getString("candidate_id"), resultSet.getString("family"), asset,
				resultSet.getObject("window_start", OffsetDateTime.class).toInstant(),
				resultSet.getObject("decision_cutoff", OffsetDateTime.class).toInstant(),
				resultSet.getString("dataset_fingerprint"), resultSet.getString("detector_version"),
				resultSet.getString("configuration_fingerprint"),
				CandidateStatus.valueOf(resultSet.getString("status")), assessment);
	}

	private AcceptedSignalSnapshot mapAccepted(ResultSet resultSet, int rowNumber) throws SQLException {
		var chain = new ChainId(resultSet.getString("chain_id"));
		var asset = new AssetId(chain, resultSet.getString("asset_address"));
		var availableAt = resultSet.getObject("available_at", OffsetDateTime.class).toInstant();
		var assessment = new RiskAssessment(
				RiskDecision.valueOf(resultSet.getString("risk_decision")),
				new RiskFacts(
						asset, availableAt, resultSet.getInt("risk_manipulation_flags"),
						AssetLifecycle.valueOf(resultSet.getString("risk_lifecycle")),
						resultSet.getBigDecimal("risk_liquidity_usd"), resultSet.getString("risk_evidence_version")),
				riskReasons(resultSet.getString("risk_evidence")));
		var baselineTransaction = new TransactionId(chain, resultSet.getString("baseline_transaction_value"));
		var currentTransaction = new TransactionId(chain, resultSet.getString("current_transaction_value"));
		return new AcceptedSignalSnapshot(
				resultSet.getString("signal_id"), resultSet.getString("candidate_id"),
				resultSet.getString("family"), resultSet.getString("position_type"), asset, availableAt,
				resultSet.getString("dataset_fingerprint"),
				List.of(
						new NormalizedSwapIdentity(chain, baselineTransaction,
								new EventId(baselineTransaction, resultSet.getString("baseline_event_locator"))),
						new NormalizedSwapIdentity(chain, currentTransaction,
								new EventId(currentTransaction, resultSet.getString("current_event_locator")))),
				assessment,
				resultSet.getString("detector_version"), resultSet.getString("scorer_version"),
				resultSet.getString("configuration_fingerprint"), resultSet.getInt("score"),
				resultSet.getString("grade"), resultSet.getBigDecimal("confidence"),
				reasons(resultSet.getString("reasoning")));
	}

	private String riskEvidence(RiskAssessment assessment) {
		var root = objectMapper.createObjectNode();
		root.put("schemaVersion", assessment.facts().evidenceVersion());
		var reasons = root.putArray("reasons");
		assessment.reasons().forEach(reasons::add);
		return objectMapper.writeValueAsString(root);
	}

	private String reasoning(List<ScoreReason> reasons) {
		var root = objectMapper.createObjectNode();
		root.put("schemaVersion", "liquidity-score-reasoning-v1");
		var factors = root.putArray("factors");
		for (var reason : reasons) {
			var factor = factors.addObject();
			factor.put("factor", reason.factor());
			factor.put("points", reason.points());
			factor.put("explanation", reason.explanation());
		}
		return objectMapper.writeValueAsString(root);
	}

	private List<String> riskReasons(String json) {
		var result = new ArrayList<String>();
		for (var node : objectMapper.readTree(json).required("reasons")) {
			result.add(node.textValue());
		}
		return List.copyOf(result);
	}

	private List<ScoreReason> reasons(String json) {
		var result = new ArrayList<ScoreReason>();
		for (JsonNode node : objectMapper.readTree(json).required("factors")) {
			result.add(new ScoreReason(
					node.required("factor").textValue(), node.required("points").intValue(),
					node.required("explanation").textValue()));
		}
		return List.copyOf(result);
	}

	private boolean sameCandidateIdentity(CandidateSnapshot first, CandidateSnapshot second) {
		return first.candidateId().equals(second.candidateId())
				&& first.family().equals(second.family())
				&& first.asset().equals(second.asset())
				&& first.windowStart().equals(second.windowStart())
				&& first.decisionCutoff().equals(second.decisionCutoff())
				&& first.datasetFingerprint().equals(second.datasetFingerprint())
				&& first.detectorVersion().equals(second.detectorVersion())
				&& first.configurationFingerprint().equals(second.configurationFingerprint());
	}

	private boolean sameCompletedCandidate(CandidateSnapshot first, CandidateSnapshot second) {
		return sameCandidateIdentity(first, second)
				&& first.status() == second.status()
				&& first.riskAssessment().equals(second.riskAssessment());
	}

	private OffsetDateTime timestamp(java.time.Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
