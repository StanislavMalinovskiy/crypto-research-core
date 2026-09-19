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
import io.cryptoresearch.risk.api.RiskApi.AssetLifecycle;
import io.cryptoresearch.risk.api.RiskApi.RiskAssessment;
import io.cryptoresearch.risk.api.RiskApi.RiskDecision;
import io.cryptoresearch.risk.api.RiskApi.RiskFacts;
import io.cryptoresearch.signal.api.SignalApi.AcceptedSignalSnapshot;
import io.cryptoresearch.signal.api.SignalApi.CandidateSnapshot;
import io.cryptoresearch.signal.api.SignalApi.CandidateStatus;
import io.cryptoresearch.signal.api.SignalApi.DetectionResult;
import io.cryptoresearch.signal.api.SignalApi.ScoreReason;
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
