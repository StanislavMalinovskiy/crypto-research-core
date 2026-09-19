package io.cryptoresearch.signal.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.PointInTimeQuery;
import io.cryptoresearch.risk.api.RiskApi;
import io.cryptoresearch.risk.api.RiskApi.RiskDecision;
import io.cryptoresearch.risk.api.RiskApi.RiskFacts;
import io.cryptoresearch.signal.api.SignalApi;
import io.cryptoresearch.signal.infrastructure.persistence.JdbcSignalPersistence;

@Service
public class LiquiditySpikeSignalService implements SignalApi {

	private static final String FAMILY = "LIQUIDITY_SPIKE";
	private static final BigDecimal RELATIVE_MULTIPLIER = new BigDecimal("1.50");
	private static final BigDecimal ABSOLUTE_INCREASE = new BigDecimal("10000.00000000");

	private final MarketDataApi marketData;
	private final RiskApi risk;
	private final SignalCandidateTransitions transitions;
	private final JdbcSignalPersistence persistence;

	public LiquiditySpikeSignalService(
			MarketDataApi marketData,
			RiskApi risk,
			SignalCandidateTransitions transitions,
			JdbcSignalPersistence persistence) {
		this.marketData = marketData;
		this.risk = risk;
		this.transitions = transitions;
		this.persistence = persistence;
	}

	@Override
	public DetectionResult detect(DetectionRequest request) {
		request = normalize(request);
		validate(request);
		var dataset = Optional.of(request.datasetFingerprint());
		var baseline = latest(marketData.observations(new PointInTimeQuery(
				request.asset(), Instant.EPOCH, request.windowStart(), request.decisionCutoff(), dataset)));
		var current = latest(marketData.observations(new PointInTimeQuery(
				request.asset(), request.windowStart(), request.decisionCutoff(), request.decisionCutoff(), dataset)));
		if (baseline.isEmpty() || current.isEmpty() || baseline.get().identity().equals(current.get().identity())) {
			return new DetectionResult(Optional.empty(), Optional.empty());
		}
		if (!meetsThresholds(baseline.get().liquidityUsd(), current.get().liquidityUsd())) {
			return new DetectionResult(Optional.empty(), Optional.empty());
		}
		if (!request.asset().equals(request.riskFacts().asset())
				|| !request.decisionCutoff().equals(request.riskFacts().cutoff())) {
			throw new IllegalArgumentException("risk facts must match asset and decision cutoff");
		}
		if (request.riskFacts().liquidityUsd().compareTo(current.get().liquidityUsd()) != 0) {
			throw new IllegalArgumentException("risk liquidity must match decision-time market liquidity");
		}

		var candidateId = candidateId(request);
		var candidate = new CandidateSnapshot(
				candidateId, FAMILY, request.asset(), request.windowStart(), request.decisionCutoff(),
				request.datasetFingerprint(), request.detectorVersion(), request.configurationFingerprint(),
				CandidateStatus.DETECTED, Optional.empty());
		transitions.record(candidate);

		var assessment = risk.assess(request.riskFacts());
		Optional<AcceptedSignalSnapshot> accepted = Optional.empty();
		if (assessment.decision() == RiskDecision.ALLOW) {
			var source = List.of(baseline.get().identity(), current.get().identity());
			var reasons = List.of(
					new ScoreReason("base", 20, "complete first-slice evidence"),
					new ScoreReason("relative-liquidity-growth", 30, "liquidity grew by at least 50 percent"),
					new ScoreReason("absolute-liquidity-growth", 20, "liquidity grew by at least USD 10,000"));
			accepted = Optional.of(new AcceptedSignalSnapshot(
					signalId(candidateId, request, assessment, source),
					candidateId,
					FAMILY,
					"ENTRY",
					request.asset(),
					request.decisionCutoff(),
					request.datasetFingerprint(),
					source,
					assessment,
					request.detectorVersion(),
					request.scorerVersion(),
					request.configurationFingerprint(),
					70,
					"B",
					new BigDecimal("1.0000"),
					reasons));
		}
		return transitions.complete(candidate, assessment, accepted);
	}

	@Override
	public Optional<AcceptedSignalSnapshot> acceptedSignal(String signalId) {
		if (signalId == null || signalId.isBlank()) {
			throw new IllegalArgumentException("signalId must not be blank");
		}
		return persistence.findAccepted(signalId);
	}

	boolean meetsThresholds(BigDecimal baseline, BigDecimal current) {
		var baselineValue = baseline.setScale(8, RoundingMode.HALF_EVEN);
		var currentValue = current.setScale(8, RoundingMode.HALF_EVEN);
		return currentValue.compareTo(baselineValue.multiply(RELATIVE_MULTIPLIER)) >= 0
				&& currentValue.subtract(baselineValue).compareTo(ABSOLUTE_INCREASE) >= 0;
	}

	private Optional<MarketObservation> latest(List<MarketObservation> observations) {
		return observations.isEmpty() ? Optional.empty() : Optional.of(observations.getLast());
	}

	private String candidateId(DetectionRequest request) {
		return new SignalFingerprint()
				.field("contract", "liquidity-spike-candidate")
				.field("chainId", request.asset().chain().value())
				.field("asset", request.asset().value())
				.field("family", FAMILY)
				.field("detectorVersion", request.detectorVersion())
				.field("configurationFingerprint", request.configurationFingerprint())
				.field("datasetFingerprint", request.datasetFingerprint())
				.field("windowStart", request.windowStart().toString())
				.field("decisionCutoff", request.decisionCutoff().toString())
				.finish();
	}

	private String signalId(
			String candidateId,
			DetectionRequest request,
			io.cryptoresearch.risk.api.RiskApi.RiskAssessment assessment,
			List<MarketDataApi.NormalizedSwapIdentity> source) {
		var fingerprint = new SignalFingerprint()
				.field("contract", "accepted-liquidity-spike")
				.field("candidateId", candidateId)
				.field("scorerVersion", request.scorerVersion())
				.field("riskDecision", assessment.decision().name())
				.field("riskFlags", Integer.toString(assessment.facts().manipulationFlags()))
				.field("riskLifecycle", assessment.facts().lifecycle().name())
				.field("riskLiquidityUsd", assessment.facts().liquidityUsd().setScale(8, RoundingMode.UNNECESSARY).toPlainString())
				.field("score", "70")
				.field("grade", "B")
				.field("confidence", "1.0000");
		for (var identity : source) {
			fingerprint.field("source", identity.chain().value() + "/" + identity.transactionId().value()
					+ "/" + identity.eventId().locator());
		}
		return fingerprint.finish();
	}

	private void validate(DetectionRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		Objects.requireNonNull(request.asset(), "asset must not be null");
		Objects.requireNonNull(request.windowStart(), "windowStart must not be null");
		Objects.requireNonNull(request.decisionCutoff(), "decisionCutoff must not be null");
		Objects.requireNonNull(request.riskFacts(), "riskFacts must not be null");
		if (request.windowStart().isAfter(request.decisionCutoff())) {
			throw new IllegalArgumentException("windowStart must not be after decisionCutoff");
		}
		for (var value : List.of(request.datasetFingerprint(), request.configurationFingerprint())) {
			if (value == null || !value.matches("sha256:[0-9a-f]{64}")) {
				throw new IllegalArgumentException("fingerprints must be algorithm-qualified SHA-256 values");
			}
		}
		for (var value : List.of(request.detectorVersion(), request.scorerVersion())) {
			if (value == null || value.isBlank()) {
				throw new IllegalArgumentException("algorithm versions must not be blank");
			}
		}
	}

	private DetectionRequest normalize(DetectionRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		var facts = Objects.requireNonNull(request.riskFacts(), "riskFacts must not be null");
		var normalizedFacts = new RiskFacts(
				facts.asset(), microseconds(facts.cutoff(), "riskFacts.cutoff"), facts.manipulationFlags(),
				facts.lifecycle(), facts.liquidityUsd(), facts.evidenceVersion());
		return new DetectionRequest(
				request.datasetFingerprint(), request.asset(),
				microseconds(request.windowStart(), "windowStart"),
				microseconds(request.decisionCutoff(), "decisionCutoff"),
				normalizedFacts, request.detectorVersion(), request.scorerVersion(),
				request.configurationFingerprint());
	}

	private Instant microseconds(Instant value, String field) {
		return Objects.requireNonNull(value, field + " must not be null").truncatedTo(ChronoUnit.MICROS);
	}
}
