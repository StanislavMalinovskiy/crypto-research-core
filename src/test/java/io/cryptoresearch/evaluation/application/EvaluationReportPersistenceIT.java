package io.cryptoresearch.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.cryptoresearch.evaluation.api.EvaluationApi.EntryOutcome;
import io.cryptoresearch.evaluation.api.EvaluationApi.EvaluationReport;
import io.cryptoresearch.evaluation.api.EvaluationApi.PricingStatus;
import io.cryptoresearch.evaluation.api.EvaluationApi.RunProvenance;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EvaluationReportPersistenceIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private final EvaluationWriter writer;
	private final JdbcClient jdbcClient;

	@Autowired
	EvaluationReportPersistenceIT(EvaluationWriter writer, JdbcClient jdbcClient) {
		this.writer = writer;
		this.jdbcClient = jdbcClient;
	}

	@AfterEach
	void clearEvaluationTables() {
		jdbcClient.sql("DELETE FROM evaluation.evaluation_reports").update();
		jdbcClient.sql("DELETE FROM evaluation.entry_outcomes").update();
		jdbcClient.sql("DELETE FROM evaluation.evaluation_runs").update();
	}

	@Test
	void persistsAnEqualRetryAsOneCompleteStableAggregate() {
		var report = report('1', '2', '3', "build-a");

		assertThat(writer.persist(report)).isEqualTo(report);
		assertThat(writer.persist(report)).isEqualTo(report);

		assertCompleteAggregate(report.runId(), report.outcomes().getFirst().outcomeId(), report.reportFingerprint());
	}

	@Test
	void rejectsChangedImmutableContentForTheSameRunWithoutMutation() {
		var accepted = report('1', '2', '3', "build-a");
		var conflicting = report('1', '2', '3', "build-b");
		writer.persist(accepted);
		var snapshot = snapshot();

		assertThatThrownBy(() -> writer.persist(conflicting))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("immutable retry conflict");

		assertCompleteAggregate(accepted.runId(), accepted.outcomes().getFirst().outcomeId(), accepted.reportFingerprint());
		assertThat(snapshot()).isEqualTo(snapshot);
	}

	@Test
	void rollsBackANewRunWhenALateOutcomeCollisionDiffers() {
		var accepted = report('1', '2', '3', "build-a");
		var conflicting = report('4', '2', '5', "build-b");
		writer.persist(accepted);
		var snapshot = snapshot();

		assertThatThrownBy(() -> writer.persist(conflicting))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("immutable retry conflict");

		assertCompleteAggregate(accepted.runId(), accepted.outcomes().getFirst().outcomeId(), accepted.reportFingerprint());
		assertThat(snapshot()).isEqualTo(snapshot);
		assertAbsent(conflicting.runId());
	}

	@Test
	void rollsBackANewRunAndOutcomeWhenALateReportCollisionDiffers() {
		var accepted = report('1', '2', '3', "build-a");
		var conflicting = report('4', '5', '3', "build-b");
		writer.persist(accepted);
		var snapshot = snapshot();

		assertThatThrownBy(() -> writer.persist(conflicting))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("immutable retry conflict");

		assertCompleteAggregate(accepted.runId(), accepted.outcomes().getFirst().outcomeId(), accepted.reportFingerprint());
		assertThat(snapshot()).isEqualTo(snapshot);
		assertAbsent(conflicting.runId());
	}

	@Test
	void rejectsAnIncompleteAggregateBeforeSupplementingIt() {
		var accepted = report('1', '2', '3', "build-a");
		writer.persist(accepted);
		jdbcClient.sql("DELETE FROM evaluation.evaluation_reports WHERE run_id = :runId")
				.param("runId", accepted.runId()).update();
		jdbcClient.sql("DELETE FROM evaluation.entry_outcomes WHERE run_id = :runId")
				.param("runId", accepted.runId()).update();
		var incompleteSnapshot = snapshot();

		assertThatThrownBy(() -> writer.persist(accepted))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("immutable retry conflict");

		assertThat(snapshot()).isEqualTo(incompleteSnapshot);
	}

	@Test
	void rejectsIncompleteOutcomeNaturalKeyCollisionWithoutSupplementingIt() {
		var accepted = report('1', '2', '3', "build-a");
		writer.persist(accepted);
		jdbcClient.sql("DELETE FROM evaluation.evaluation_reports WHERE run_id = :runId")
				.param("runId", accepted.runId()).update();
		var conflictingOutcomeId = report('1', '4', '3', "build-a");
		var incompleteSnapshot = snapshot();

		assertThatThrownBy(() -> writer.persist(conflictingOutcomeId))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("immutable retry conflict");

		assertThat(snapshot()).isEqualTo(incompleteSnapshot);
	}

	@Test
	void rejectsIncompleteReportRunNaturalKeyCollisionWithoutSupplementingIt() {
		var accepted = report('1', '2', '3', "build-a");
		writer.persist(accepted);
		jdbcClient.sql("DELETE FROM evaluation.evaluation_reports WHERE run_id = :runId")
				.param("runId", accepted.runId()).update();
		var conflictingReportId = report('1', '2', '4', "build-a");
		var incompleteSnapshot = snapshot();

		assertThatThrownBy(() -> writer.persist(conflictingReportId))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("immutable retry conflict");

		assertThat(snapshot()).isEqualTo(incompleteSnapshot);
	}

	@Test
	void concurrentEqualAndConflictingCallersResolveToOneDurableWinner() throws Exception {
		var equal = report('1', '2', '3', "build-a");
		var equalResults = concurrently(equal, equal);
		for (var result : equalResults) {
			assertThat(result.failure()).isNull();
			assertThat(result.report()).isEqualTo(equal);
		}
		assertCompleteAggregate(equal.runId(), equal.outcomes().getFirst().outcomeId(), equal.reportFingerprint());
		clearEvaluationTables();

		var winner = report('1', '2', '3', "build-a");
		var loser = report('1', '2', '3', "build-b");
		var conflictingResults = concurrently(winner, loser);
		var failures = conflictingResults.stream().filter(result -> result.failure() != null).toList();
		assertThat(failures).singleElement().satisfies(result ->
				assertThat(result.failure().getCause()).isInstanceOf(IllegalStateException.class)
						.hasMessageContaining("immutable retry conflict"));
		assertCompleteAggregate(winner.runId(), winner.outcomes().getFirst().outcomeId(), winner.reportFingerprint());
	}

	private List<ConcurrentResult> concurrently(EvaluationReport first, EvaluationReport second) {
		var barrier = new CyclicBarrier(2);
		var executor = Executors.newVirtualThreadPerTaskExecutor();
		Future<EvaluationReport> firstFuture = null;
		Future<EvaluationReport> secondFuture = null;
		try {
			firstFuture = executor.submit(() -> {
				barrier.await(10, TimeUnit.SECONDS);
				return writer.persist(first);
			});
			secondFuture = executor.submit(() -> {
				barrier.await(10, TimeUnit.SECONDS);
				return writer.persist(second);
			});
			return List.of(observe(firstFuture), observe(secondFuture));
		}
		finally {
			if (firstFuture != null) {
				firstFuture.cancel(true);
			}
			if (secondFuture != null) {
				secondFuture.cancel(true);
			}
			executor.shutdownNow();
			awaitTermination(executor);
		}
	}

	private ConcurrentResult observe(Future<EvaluationReport> future) {
		try {
			return new ConcurrentResult(future.get(10, TimeUnit.SECONDS), null);
		}
		catch (Exception exception) {
			return new ConcurrentResult(null, exception);
		}
	}

	private void awaitTermination(java.util.concurrent.ExecutorService executor) {
		try {
			if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
				throw new AssertionError("concurrent persistence executor did not terminate within 10 seconds");
			}
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError("interrupted while awaiting concurrent persistence executor cleanup", exception);
		}
	}

	private EvaluationReport report(char run, char outcome, char report, String build) {
		var runId = fingerprint(run);
		var outcomeValue = new EntryOutcome(fingerprint(outcome), fingerprint('a'), "1h", PricingStatus.UNPRICED,
				Optional.of("NO_ADMISSIBLE_ENTRY_PRICE"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		return new EvaluationReport(runId, fingerprint(report), new RunProvenance(
				build, "revision-a", false, "evaluation-v1", fingerprint('b'), fingerprint('c'),
				Instant.parse("2026-09-20T12:00:00Z"), OptionalLong.empty()), "LIQUIDITY_SPIKE", 1, 0, 1,
				Optional.<BigDecimal>empty(), List.of(outcomeValue));
	}

	private String fingerprint(char value) {
		return "sha256:" + String.valueOf(value).repeat(64);
	}

	private void assertCompleteAggregate(String runId, String outcomeId, String reportId) {
		assertThat(count("evaluation_runs", runId)).isOne();
		assertThat(count("entry_outcomes", outcomeId)).isOne();
		assertThat(count("evaluation_reports", reportId)).isOne();
		assertThat(jdbcClient.sql("SELECT run_id FROM evaluation.entry_outcomes WHERE outcome_id = :id")
				.param("id", outcomeId).query(String.class).single()).isEqualTo(runId);
		assertThat(jdbcClient.sql("SELECT run_id FROM evaluation.evaluation_reports WHERE report_id = :id")
				.param("id", reportId).query(String.class).single()).isEqualTo(runId);
	}

	private void assertAbsent(String runId) {
		assertThat(count("evaluation_runs", runId)).isZero();
		assertThat(jdbcClient.sql("SELECT count(*) FROM evaluation.entry_outcomes WHERE run_id = :id")
				.param("id", runId).query(Integer.class).single()).isZero();
		assertThat(jdbcClient.sql("SELECT count(*) FROM evaluation.evaluation_reports WHERE run_id = :id")
				.param("id", runId).query(Integer.class).single()).isZero();
	}

	private int count(String table, String id) {
		var idColumn = table.equals("evaluation_runs") ? "run_id" : table.equals("entry_outcomes") ? "outcome_id" : "report_id";
		return jdbcClient.sql("SELECT count(*) FROM evaluation." + table + " WHERE " + idColumn + " = :id")
				.param("id", id).query(Integer.class).single();
	}

	private DurableSnapshot snapshot() {
		return new DurableSnapshot(
				jdbcClient.sql("SELECT to_jsonb(e)::text FROM evaluation.evaluation_runs e ORDER BY run_id")
						.query(String.class).list(),
				jdbcClient.sql("SELECT to_jsonb(e)::text FROM evaluation.entry_outcomes e ORDER BY outcome_id")
						.query(String.class).list(),
				jdbcClient.sql("SELECT to_jsonb(e)::text FROM evaluation.evaluation_reports e ORDER BY report_id")
						.query(String.class).list());
	}

	private record DurableSnapshot(List<String> runs, List<String> outcomes, List<String> reports) {
	}

	private record ConcurrentResult(EvaluationReport report, Exception failure) {
	}
}
