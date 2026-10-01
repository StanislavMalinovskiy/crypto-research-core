package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.cryptoresearch.evaluation.api.EvaluationApi;
import io.cryptoresearch.evaluation.api.EvaluationApi.EvaluationRequest;
import io.cryptoresearch.evaluation.api.EvaluationApi.PricingStatus;
import io.cryptoresearch.evaluation.api.EvaluationApi.RunProvenance;
import io.cryptoresearch.evaluation.api.EvaluationApi.VersionedEvaluationRequest;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.AvailabilityStatus;
import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedDataset;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedSwapInput;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.marketdata.api.MarketDataApi.RevisionReference;
import io.cryptoresearch.marketdata.api.MarketDataApi.SelectionScope;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedFinalizeRequest;
import io.cryptoresearch.signal.api.SignalApi;
import io.cryptoresearch.signal.api.SignalApi.DetectionRequest;
import io.cryptoresearch.signal.api.SignalApi.VersionedDetectionRequest;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VersionedEvaluationStorageIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private final MarketDataApi marketData;
	private final SignalApi signals;
	private final EvaluationApi evaluations;
	private final JdbcClient jdbc;

	@Autowired
	VersionedEvaluationStorageIT(MarketDataApi marketData, SignalApi signals,
			EvaluationApi evaluations, JdbcClient jdbc) {
		this.marketData = marketData;
		this.signals = signals;
		this.evaluations = evaluations;
		this.jdbc = jdbc;
	}

	@Test
	void separateEvidencePersistsExactPriceRevisionsAndRetriesAtomically() throws Exception {
		var fixture = new FirstSignalScenarioFixture().load();
		var decisionRefs = replay(fixture.dataset().observations().subList(0, 2));
		var decision = snapshot(fixture, fixture.decisionCutoff(), decisionRefs);
		var accepted = signals.detectVersioned(new VersionedDetectionRequest(decision.fingerprint(),
				new DetectionRequest(decision.fingerprint(), fixture.asset(),
						fixture.decisionCutoff().minusSeconds(3600), fixture.decisionCutoff(), fixture.riskFacts(),
						"liquidity-spike-v2", "liquidity-score-v1", fixture.configurationFingerprint())))
				.legacyResult().acceptedSignal().orElseThrow();
		var laterRefs = replay(fixture.dataset().observations().subList(2, 4));
		var allRefs = new ArrayList<>(decisionRefs);
		allRefs.addAll(laterRefs);
		var evaluationSnapshot = snapshot(fixture, fixture.evaluationCutoff(), allRefs);
		var source = fixture.dataset().observations().getLast();
		var futureTransaction = new TransactionId(source.chain(), "fixture-after-evaluation-cutoff");
		var future = new RecordedSwapInput(source.chain(), futureTransaction,
				new EventId(futureTransaction, source.eventId().locator()), source.provider(),
				source.blockPosition(), source.blockHash(), Optional.of(fixture.evaluationCutoff().plusSeconds(10)),
				fixture.evaluationCutoff().plusSeconds(10), source.payload(), source.transformationVersion());
		marketData.replayVersioned(new RecordedDataset("after-cutoff-v2", List.of(future)));
		var request = new VersionedEvaluationRequest(accepted.signalId(), "1h",
				fixture.provenance(decision.fingerprint(), fixture.evaluationCutoff()),
				decision.fingerprint(), evaluationSnapshot.fingerprint());
		assertThatThrownBy(() -> evaluations.evaluate(new EvaluationRequest(accepted.signalId(), "1h",
				request.provenance()))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("separate decision and evaluation evidence");

		var release = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> {
				if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
				return evaluations.evaluateVersioned(request);
			});
			var second = executor.submit(() -> {
				if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
				return evaluations.evaluateVersioned(request);
			});
			release.countDown();
			var result = first.get(20, TimeUnit.SECONDS);
			assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(result);
			assertThat(result.legacyReport().outcomes()).singleElement().satisfies(outcome -> {
				assertThat(outcome.status()).isEqualTo(PricingStatus.PRICED);
				assertThat(outcome.netReturn()).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.16000000"));
			});
			assertThat(jdbc.sql("""
					SELECT decision_dataset_fingerprint || '|' || evaluation_dataset_fingerprint
					FROM evaluation.v2_evaluation_runs WHERE run_id = :run
					""").param("run", result.legacyReport().runId()).query(String.class).single())
					.isEqualTo(decision.fingerprint() + "|" + evaluationSnapshot.fingerprint());
			assertThat(jdbc.sql("""
					SELECT entry_revision_key || '|' || horizon_revision_key FROM evaluation.v2_entry_outcomes
					WHERE run_id = :run
					""").param("run", result.legacyReport().runId()).query(String.class).single())
					.isEqualTo(laterRefs.get(0).revisionKey() + "|" + laterRefs.get(1).revisionKey());
			assertThat(jdbc.sql("SELECT ordered_content::text FROM evaluation.v2_evaluation_reports")
					.query(String.class).single()).contains(decision.fingerprint(), evaluationSnapshot.fingerprint(),
							"decisionScope", "evaluationScope", "decisionExclusions", "evaluationExclusions",
							"decisionCoveredKeyCount", "evaluationCoveredKeyCount");
			assertThat(jdbc.sql("SELECT count(*) FROM evaluation.evaluation_runs").query(Integer.class).single()).isZero();
			assertThat(jdbc.sql("SELECT count(*) FROM evaluation.v2_evaluation_runs").query(Integer.class).single()).isOne();
			assertThat(jdbc.sql("SELECT count(*) FROM evaluation.v2_entry_outcomes").query(Integer.class).single()).isOne();
			assertThat(jdbc.sql("SELECT count(*) FROM evaluation.v2_evaluation_reports").query(Integer.class).single()).isOne();
			assertThat(evaluations.evaluateVersioned(request)).isEqualTo(result);
			assertThatThrownBy(() -> jdbc.sql("""
					INSERT INTO evaluation.v2_evaluation_runs
					SELECT 'sha256:' || repeat('0', 64), signal_id, horizon, build_identity,
					 source_revision, source_dirty, algorithm_version, configuration_fingerprint,
					 NULL, evaluation_dataset_fingerprint, evaluation_cutoff, random_seed,
					 evidence_fingerprint FROM evaluation.v2_evaluation_runs WHERE run_id = :run
					""").param("run", result.legacyReport().runId()).update())
					.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> jdbc.sql("""
					INSERT INTO evaluation.v2_evaluation_runs
					SELECT 'sha256:' || repeat('1', 64), signal_id, horizon, build_identity,
					 source_revision, source_dirty, algorithm_version, configuration_fingerprint,
					 decision_dataset_fingerprint, NULL, evaluation_cutoff, random_seed,
					 evidence_fingerprint FROM evaluation.v2_evaluation_runs WHERE run_id = :run
					""").param("run", result.legacyReport().runId()).update())
					.isInstanceOf(DataIntegrityViolationException.class);

			jdbc.sql("UPDATE evaluation.v2_entry_outcomes SET net_return = 0.17000000").update();
			assertThatThrownBy(() -> evaluations.evaluateVersioned(request))
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("immutable aggregate conflict");
			jdbc.sql("UPDATE evaluation.v2_entry_outcomes SET net_return = 0.16000000").update();
			assertThat(evaluations.evaluateVersioned(request)).isEqualTo(result);

			var changedProvenance = new RunProvenance(request.provenance().buildIdentity(),
					"different-source-revision", request.provenance().sourceDirty(),
					request.provenance().algorithmVersion(), request.provenance().configurationFingerprint(),
					request.provenance().datasetFingerprint(), request.provenance().evaluationCutoff(),
					request.provenance().seed());
			var changed = new VersionedEvaluationRequest(request.signalId(), request.horizon(),
					changedProvenance, request.decisionDatasetFingerprint(), request.evaluationDatasetFingerprint());
			jdbc.sql("""
					CREATE FUNCTION evaluation.fail_v2_report() RETURNS trigger LANGUAGE plpgsql AS $$
					BEGIN RAISE EXCEPTION 'late-v2-report-test'; END $$
					""").update();
			jdbc.sql("""
					CREATE TRIGGER fail_v2_report BEFORE INSERT ON evaluation.v2_evaluation_reports
					FOR EACH ROW EXECUTE FUNCTION evaluation.fail_v2_report()
					""").update();
			try {
				assertThatThrownBy(() -> evaluations.evaluateVersioned(changed))
						.isInstanceOf(RuntimeException.class).hasMessageContaining("late-v2-report-test");
				assertThat(jdbc.sql("SELECT count(*) FROM evaluation.v2_evaluation_runs")
						.query(Integer.class).single()).isOne();
				assertThat(jdbc.sql("SELECT count(*) FROM evaluation.v2_entry_outcomes")
						.query(Integer.class).single()).isOne();
			}
			finally {
				jdbc.sql("DROP TRIGGER fail_v2_report ON evaluation.v2_evaluation_reports").update();
				jdbc.sql("DROP FUNCTION evaluation.fail_v2_report()").update();
			}
			assertThat(evaluations.evaluateVersioned(changed).legacyReport().runId())
					.isNotEqualTo(result.legacyReport().runId());
			var unpriced = evaluations.evaluateVersioned(new VersionedEvaluationRequest(
					accepted.signalId(), "1h",
					fixture.provenance(decision.fingerprint(), fixture.decisionCutoff()),
					decision.fingerprint(), decision.fingerprint()));
			assertThat(unpriced.legacyReport().outcomes()).singleElement().satisfies(outcome -> {
				assertThat(outcome.status()).isEqualTo(PricingStatus.UNPRICED);
				assertThat(outcome.missingPriceReason()).hasValue("NO_ADMISSIBLE_ENTRY_PRICE");
				assertThat(outcome.entryPrice()).isEmpty();
				assertThat(outcome.horizonPrice()).isEmpty();
			});
			assertThat(jdbc.sql("SELECT entry_revision_key IS NULL AND horizon_revision_key IS NULL "
					+ "FROM evaluation.v2_entry_outcomes WHERE outcome_id = :id")
					.param("id", unpriced.legacyReport().outcomes().getFirst().outcomeId())
					.query(Boolean.class).single()).isTrue();
		}
	}

	@Test
	void versionedRunSchemaRejectsMissingDecisionOrEvaluationFingerprint() {
		// Insert an otherwise complete row by copying a published run in the integration scenario above.
		// The direct schema constraints are also verified independently by the migration test.
		assertThat(jdbc.sql("""
				SELECT is_nullable FROM information_schema.columns WHERE table_schema = 'evaluation'
				AND table_name = 'v2_evaluation_runs' AND column_name = 'decision_dataset_fingerprint'
				""").query(String.class).single()).isEqualTo("NO");
		assertThat(jdbc.sql("""
				SELECT is_nullable FROM information_schema.columns WHERE table_schema = 'evaluation'
				AND table_name = 'v2_evaluation_runs' AND column_name = 'evaluation_dataset_fingerprint'
				""").query(String.class).single()).isEqualTo("NO");
	}

	private List<RevisionReference> replay(List<MarketDataApi.RecordedSwapInput> inputs) {
		return marketData.replayVersioned(new RecordedDataset("evaluation-v2", inputs)).items().stream()
				.map(MarketDataApi.VersionedReplayItem::revision).toList();
	}

	private MarketDataApi.VersionedSnapshot snapshot(FirstSignalScenarioFixture.Scenario fixture,
			java.time.Instant cutoff, List<RevisionReference> refs) {
		return marketData.finalizeVersioned(new VersionedFinalizeRequest("length-prefixed-v2", cutoff,
				new SelectionScope(fixture.asset().chain(), List.of(fixture.asset()),
						fixture.decisionCutoff().minusSeconds(3600), cutoff, List.of(FactKind.SWAP),
						List.of(), List.of()), refs, List.of(), "explicit-revisions-v1",
				"modeled-history-v1", AvailabilityStatus.HISTORICAL_MODEL));
	}
}
