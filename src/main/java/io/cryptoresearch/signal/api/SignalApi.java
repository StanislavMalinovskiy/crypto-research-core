package io.cryptoresearch.signal.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.risk.api.RiskApi.RiskAssessment;
import io.cryptoresearch.risk.api.RiskApi.RiskFacts;

/** Synchronous signal detection and immutable signal-snapshot boundary. */
public interface SignalApi {

	DetectionResult detect(DetectionRequest request);

	Optional<AcceptedSignalSnapshot> acceptedSignal(String signalId);

	record DetectionRequest(
			String datasetFingerprint,
			AssetId asset,
			Instant windowStart,
			Instant decisionCutoff,
			RiskFacts riskFacts,
			String detectorVersion,
			String scorerVersion,
			String configurationFingerprint) {
	}

	enum CandidateStatus {
		DETECTED,
		REJECTED,
		ACCEPTED
	}

	record CandidateSnapshot(
			String candidateId,
			String family,
			AssetId asset,
			Instant windowStart,
			Instant decisionCutoff,
			String datasetFingerprint,
			String detectorVersion,
			String configurationFingerprint,
			CandidateStatus status,
			Optional<RiskAssessment> riskAssessment) {
		public CandidateSnapshot {
			riskAssessment = Optional.ofNullable(riskAssessment).orElseGet(Optional::empty);
		}
	}

	record ScoreReason(String factor, int points, String explanation) {
	}

	record AcceptedSignalSnapshot(
			String signalId,
			String candidateId,
			String family,
			String position,
			AssetId asset,
			Instant availableAt,
			String datasetFingerprint,
			List<NormalizedSwapIdentity> sourceObservations,
			RiskAssessment riskAssessment,
			String detectorVersion,
			String scorerVersion,
			String configurationFingerprint,
			int score,
			String grade,
			BigDecimal confidence,
			List<ScoreReason> reasoning) {
		public AcceptedSignalSnapshot {
			sourceObservations = List.copyOf(sourceObservations);
			reasoning = List.copyOf(reasoning);
		}
	}

	record DetectionResult(
			Optional<CandidateSnapshot> candidate,
			Optional<AcceptedSignalSnapshot> acceptedSignal) {
		public DetectionResult {
			candidate = Optional.ofNullable(candidate).orElseGet(Optional::empty);
			acceptedSignal = Optional.ofNullable(acceptedSignal).orElseGet(Optional::empty);
		}
	}
}
