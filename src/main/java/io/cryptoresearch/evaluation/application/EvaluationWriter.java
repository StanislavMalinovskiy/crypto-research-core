package io.cryptoresearch.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cryptoresearch.evaluation.api.EvaluationApi.EvaluationReport;
import io.cryptoresearch.evaluation.infrastructure.persistence.JdbcEvaluationPersistence;

@Service
public class EvaluationWriter {

	private final JdbcEvaluationPersistence persistence;

	public EvaluationWriter(JdbcEvaluationPersistence persistence) {
		this.persistence = persistence;
	}

	@Transactional
	public EvaluationReport persist(EvaluationReport report) {
		return persistence.store(report);
	}
}
