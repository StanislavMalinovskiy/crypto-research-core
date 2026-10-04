package io.cryptoresearch.signal.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.PointInTimeQuery;
import io.cryptoresearch.marketdata.api.MarketDataApi.RevisionReference;
import io.cryptoresearch.risk.api.RiskApi;
import io.cryptoresearch.risk.api.RiskApi.RiskDecision;
import io.cryptoresearch.risk.api.RiskApi.RiskFacts;
import io.cryptoresearch.signal.api.SignalApi;
import io.cryptoresearch.signal.api.SignalApi.AcceptedSignalSnapshot;
import io.cryptoresearch.signal.api.SignalApi.CandidateSnapshot;
import io.cryptoresearch.signal.api.SignalApi.CandidateStatus;
import io.cryptoresearch.signal.api.SignalApi.DetectionResult;
import io.cryptoresearch.signal.api.SignalApi.ScoreReason;
import io.cryptoresearch.signal.api.SignalApi.VersionedAcceptedSignal;
import io.cryptoresearch.signal.api.SignalApi.VersionedDetectionRequest;
import io.cryptoresearch.signal.api.SignalApi.VersionedDetectionResult;

@Service
public class VersionedLiquiditySpikeSignalService {

	private final MarketDataApi marketData;
	private final RiskApi risk;
	private final SignalCandidateTransitions transitions;

	public VersionedLiquiditySpikeSignalService(MarketDataApi marketData, RiskApi risk,
			SignalCandidateTransitions transitions) {
		this.marketData = marketData;
		this.risk = risk;
		this.transitions = transitions;
	}

	public VersionedDetectionResult detect(VersionedDetectionRequest request) {
		var detection = normalizedDetection(request.detection());
		if (!request.decisionDatasetFingerprint().equals(detection.datasetFingerprint())) {
			throw new IllegalArgumentException("Decision fingerprint must match detection evidence");
		}
		var snapshot = marketData.versionedSnapshotEvidence(request.decisionDatasetFingerprint())
				.orElseThrow(() -> new IllegalArgumentException("Unknown versioned decision snapshot"));
		if (!snapshot.knowledgeCutoff().equals(detection.decisionCutoff())) {
			throw new IllegalArgumentException("Decision cutoff must match frozen snapshot");
		}
		if (!detection.asset().equals(detection.riskFacts().asset())
				|| !detection.decisionCutoff().equals(detection.riskFacts().cutoff())) {
			throw new IllegalArgumentException("Risk facts must match decision evidence");
		}
		if (!"liquidity-spike-v2".equals(detection.detectorVersion())
				|| !detection.windowStart().equals(detection.decisionCutoff().minusSeconds(3600))) {
			throw new IllegalArgumentException("Unsupported versioned detector or decision window");
		}
		var fingerprint = Optional.of(snapshot.fingerprint());
		var baseline = latest(marketData.versionedObservations(new PointInTimeQuery(detection.asset(),
				detection.decisionCutoff().minusSeconds(3900), detection.windowStart(),
				detection.decisionCutoff(), fingerprint)));
		var current = latest(marketData.versionedObservations(new PointInTimeQuery(detection.asset(),
				detection.decisionCutoff().minusSeconds(60), detection.decisionCutoff(),
				detection.decisionCutoff(), fingerprint)));
		if (baseline.isEmpty() || current.isEmpty()
				|| baseline.get().identity().equals(current.get().identity())
				|| baseline.get().observedAt().equals(current.get().observedAt())
				|| !meetsThresholds(baseline.get().liquidityUsd(), current.get().liquidityUsd())) {
			return new VersionedDetectionResult(new DetectionResult(Optional.empty(), Optional.empty()),
					snapshot.fingerprint(), "EXPLICIT_REVISION_V1");
		}
		if (detection.riskFacts().liquidityUsd().compareTo(current.get().liquidityUsd()) != 0) {
			throw new IllegalArgumentException("Risk liquidity must match decision-time market liquidity");
		}
		var baselineRef = selectedRef(snapshot.members(), baseline.get());
		var currentRef = selectedRef(snapshot.members(), current.get());
		var sources = List.of(baselineRef, currentRef);
		var candidateId = candidateId(detection, snapshot.fingerprint());
		var candidate = new CandidateSnapshot(candidateId, "LIQUIDITY_SPIKE", detection.asset(),
				detection.windowStart(), detection.decisionCutoff(), snapshot.fingerprint(),
				detection.detectorVersion(), detection.configurationFingerprint(),
				CandidateStatus.DETECTED, Optional.empty());
		transitions.recordVersioned(candidate);
		var assessment = risk.assess(detection.riskFacts());
		var completed = new CandidateSnapshot(candidate.candidateId(), candidate.family(), candidate.asset(),
				candidate.windowStart(), candidate.decisionCutoff(), candidate.datasetFingerprint(),
				candidate.detectorVersion(), candidate.configurationFingerprint(),
				assessment.decision() == RiskDecision.ALLOW ? CandidateStatus.ACCEPTED : CandidateStatus.REJECTED,
				Optional.of(assessment));
		Optional<VersionedAcceptedSignal> accepted = Optional.empty();
		if (assessment.decision() == RiskDecision.ALLOW) {
			var reasons = List.of(new ScoreReason("base", 20, "complete first-slice evidence"),
					new ScoreReason("relative-liquidity-growth", 30, "liquidity grew by at least 50 percent"),
					new ScoreReason("absolute-liquidity-growth", 20, "liquidity grew by at least USD 10,000"));
			var signal = new AcceptedSignalSnapshot(signalId(candidateId, detection, sources,
					assessment.decision(), assessment.facts().liquidityUsd()), candidateId,
					"LIQUIDITY_SPIKE", "ENTRY", detection.asset(), detection.decisionCutoff(),
					snapshot.fingerprint(), List.of(baseline.get().identity(), current.get().identity()),
					assessment, detection.detectorVersion(), detection.scorerVersion(),
					detection.configurationFingerprint(), 70, "B", new BigDecimal("1.0000"), reasons);
			accepted = Optional.of(new VersionedAcceptedSignal(signal, snapshot.fingerprint(),
					snapshot.knowledgeCutoff(), sources));
		}
		transitions.completeVersioned(candidate, assessment, accepted);
		return new VersionedDetectionResult(new DetectionResult(Optional.of(completed),
				accepted.map(VersionedAcceptedSignal::signal)), snapshot.fingerprint(), "EXPLICIT_REVISION_V1");
	}

	private SignalApi.DetectionRequest normalizedDetection(SignalApi.DetectionRequest request) {
		var facts = request.riskFacts();
		var normalizedRisk = new RiskFacts(facts.asset(), facts.cutoff().truncatedTo(ChronoUnit.MICROS),
				facts.manipulationFlags(), facts.lifecycle(), facts.liquidityUsd(), facts.evidenceVersion());
		return new SignalApi.DetectionRequest(request.datasetFingerprint(), request.asset(),
				request.windowStart().truncatedTo(ChronoUnit.MICROS),
				request.decisionCutoff().truncatedTo(ChronoUnit.MICROS), normalizedRisk,
				request.detectorVersion(), request.scorerVersion(), request.configurationFingerprint());
	}

	private Optional<MarketObservation> latest(List<MarketObservation> observations) {
		return observations.isEmpty() ? Optional.empty() : Optional.of(observations.getLast());
	}

	private boolean meetsThresholds(BigDecimal baseline, BigDecimal current) {
		var prior = baseline.setScale(8, RoundingMode.HALF_EVEN);
		var present = current.setScale(8, RoundingMode.HALF_EVEN);
		return present.compareTo(prior.multiply(new BigDecimal("1.50"))) >= 0
				&& present.subtract(prior).compareTo(new BigDecimal("10000.00000000")) >= 0;
	}

	private RevisionReference selectedRef(List<RevisionReference> members, MarketObservation observation) {
		return members.stream().filter(ref -> ref.kind() == FactKind.SWAP
				&& ref.canonicalIdentity().equals(observation.identity()))
				.findFirst().orElseThrow(() -> new IllegalStateException("Snapshot observation lacks exact revision"));
	}

	private String candidateId(SignalApi.DetectionRequest request, String decisionFingerprint) {
		return new SignalFingerprint().field("contract", "versioned-liquidity-spike-candidate-v1")
				.field("chain", request.asset().chain().value()).field("asset", request.asset().value())
				.field("detector", request.detectorVersion()).field("configuration", request.configurationFingerprint())
				.field("decisionDataset", decisionFingerprint).field("windowStart", request.windowStart().toString())
				.field("decisionCutoff", request.decisionCutoff().toString()).finish();
	}

	private String signalId(String candidateId, SignalApi.DetectionRequest request,
			List<RevisionReference> sources, RiskDecision decision, BigDecimal liquidity) {
		var riskEvidence = risk.assess(request.riskFacts());
		var hash = new SignalFingerprint().field("contract", "versioned-accepted-liquidity-spike-v1")
				.field("candidateId", candidateId).field("decisionDataset", request.datasetFingerprint())
				.field("decisionCutoff", request.decisionCutoff().toString())
				.field("scorer", request.scorerVersion()).field("riskDecision", decision.name())
				.field("riskFlags", Integer.toString(request.riskFacts().manipulationFlags()))
				.field("riskLifecycle", request.riskFacts().lifecycle().name())
				.field("riskLiquidity", liquidity.toPlainString())
				.field("riskEvidenceVersion", riskEvidence.facts().evidenceVersion())
				.field("score", "70")
				.field("grade", "B").field("confidence", "1.0000");
		for (var reason : riskEvidence.reasons()) {
			hash.field("riskReason", reason);
		}
		for (var source : sources) {
			hash.field("sourceRevision", source.revisionKey());
		}
		return hash.finish();
	}
}
