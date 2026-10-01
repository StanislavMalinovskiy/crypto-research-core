package io.cryptoresearch.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cryptoresearch.evaluation.api.EvaluationApi.VersionedEvaluationReport;
import io.cryptoresearch.evaluation.infrastructure.persistence.JdbcVersionedEvaluationPersistence;

@Service
public class VersionedEvaluationWriter {

	private final JdbcVersionedEvaluationPersistence persistence;

	public VersionedEvaluationWriter(JdbcVersionedEvaluationPersistence persistence) {
		this.persistence = persistence;
	}

	@Transactional
	public VersionedEvaluationReport persist(VersionedEvaluationReport report,
			String entryRevision, String horizonRevision) {
		return persistence.store(report, entryRevision, horizonRevision);
	}
}
