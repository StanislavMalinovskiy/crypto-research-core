package io.cryptoresearch.signal.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cryptoresearch.risk.api.RiskApi.RiskAssessment;
import io.cryptoresearch.signal.api.SignalApi.AcceptedSignalSnapshot;
import io.cryptoresearch.signal.api.SignalApi.CandidateSnapshot;
import io.cryptoresearch.signal.api.SignalApi.DetectionResult;
import io.cryptoresearch.signal.infrastructure.persistence.JdbcSignalPersistence;

@Service
public class SignalCandidateTransitions {

	private final JdbcSignalPersistence persistence;

	public SignalCandidateTransitions(JdbcSignalPersistence persistence) {
		this.persistence = persistence;
	}

	@Transactional
	public CandidateSnapshot record(CandidateSnapshot candidate) {
		return persistence.recordCandidate(candidate);
	}

	@Transactional
	public DetectionResult complete(
			CandidateSnapshot candidate,
			RiskAssessment assessment,
			Optional<AcceptedSignalSnapshot> acceptedSignal) {
		return persistence.completeCandidate(candidate, assessment, acceptedSignal);
	}
}
