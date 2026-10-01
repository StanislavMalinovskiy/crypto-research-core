package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.cryptoresearch.evaluation.api.EvaluationApi;
import io.cryptoresearch.evaluation.api.EvaluationApi.EvaluationRequest;
import io.cryptoresearch.evaluation.api.EvaluationApi.PricingStatus;
import io.cryptoresearch.evaluation.api.EvaluationApi.RunProvenance;
import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.FinalizeDatasetRequest;
import io.cryptoresearch.marketdata.api.MarketDataApi.PointInTimeQuery;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedDataset;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedSwapInput;
import io.cryptoresearch.marketdata.api.MarketDataApi.ReplayStatus;
import io.cryptoresearch.marketdata.api.MarketDataApi.AvailabilityStatus;
import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;
import io.cryptoresearch.marketdata.api.MarketDataApi.RevisionReference;
import io.cryptoresearch.marketdata.api.MarketDataApi.SelectionScope;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedFinalizeRequest;
import io.cryptoresearch.marketdata.application.LiquidityObservation;
import io.cryptoresearch.marketdata.application.RecordMarketFactUseCase;
import io.cryptoresearch.risk.api.RiskApi;
import io.cryptoresearch.risk.api.RiskApi.AssetLifecycle;
import io.cryptoresearch.risk.api.RiskApi.RiskDecision;
import io.cryptoresearch.risk.api.RiskApi.RiskFacts;
import io.cryptoresearch.signal.api.SignalApi;
import io.cryptoresearch.signal.api.SignalApi.CandidateStatus;
import io.cryptoresearch.signal.api.SignalApi.DetectionRequest;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FirstSignalEvaluationIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private final ApplicationContext applicationContext;
	private final JdbcClient jdbcClient;

	@Autowired
	FirstSignalEvaluationIT(ApplicationContext applicationContext, JdbcClient jdbcClient) {
		this.applicationContext = applicationContext;
		this.jdbcClient = jdbcClient;
	}

	@Test
	void laterEvaluationSnapshotPricesAnAlreadyAcceptedDecision() {
		var marketData = requiredApi(MarketDataApi.class);
		var signal = requiredApi(SignalApi.class);
		var evaluation = requiredApi(EvaluationApi.class);
		resetBusinessTables();
		var fixture = new FirstSignalScenarioFixture().load();
		var firstTwo = new RecordedDataset("decision-history-v1", fixture.dataset().observations().subList(0, 2));
		var decisionReplay = marketData.replayVersioned(firstTwo);
		assertThat(decisionReplay.items()).allMatch(item -> item.legacyItem().status() == ReplayStatus.NORMALIZED);
		var decisionRefs = decisionReplay.items().stream().map(item -> item.revision()).toList();
		var decision = marketData.finalizeVersioned(versionedRequest(fixture,
				fixture.decisionCutoff(), decisionRefs));
		var detection = signal.detectVersioned(new SignalApi.VersionedDetectionRequest(decision.fingerprint(),
				detectionRequest(fixture, decision.fingerprint(), fixture.riskFacts(), fixture.configurationFingerprint())));
		var accepted = detection.legacyResult().acceptedSignal().orElseThrow();
		var acceptedIdBeforeFutureFacts = accepted.signalId();

		var laterTwo = new RecordedDataset("evaluation-history-v1", fixture.dataset().observations().subList(2, 4));
		var laterReplay = marketData.replayVersioned(laterTwo);
		assertThat(laterReplay.items()).allMatch(item -> item.legacyItem().status() == ReplayStatus.NORMALIZED);
		var allRefs = new ArrayList<>(decisionRefs);
		allRefs.addAll(laterReplay.items().stream().map(item -> item.revision()).toList());
		var evaluationSnapshot = marketData.finalizeVersioned(versionedRequest(fixture,
				fixture.evaluationCutoff(), allRefs));
		assertThat(evaluationSnapshot.fingerprint()).isNotEqualTo(decision.fingerprint());
		var report = evaluation.evaluateVersioned(new EvaluationApi.VersionedEvaluationRequest(
				accepted.signalId(), "1h", fixture.provenance(decision.fingerprint(), fixture.evaluationCutoff()),
				decision.fingerprint(), evaluationSnapshot.fingerprint()));
		assertThat(report.legacyReport().outcomes()).singleElement().satisfies(outcome -> {
			assertThat(outcome.status()).as("later evaluation facts price the existing signal").isEqualTo(PricingStatus.PRICED);
			assertThat(outcome.grossReturn()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("0.20000000"));
			assertThat(outcome.friction()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("0.04000000"));
			assertThat(outcome.netReturn()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("0.16000000"));
		});
		assertThat(signal.acceptedSignal(acceptedIdBeforeFutureFacts)).contains(accepted);
		assertThat(report.decisionDatasetFingerprint()).isEqualTo(decision.fingerprint());
		assertThat(report.evaluationDatasetFingerprint()).isEqualTo(evaluationSnapshot.fingerprint());
	}

	@Test
	void versionedReplayRetainsTwoProviderRevisionsOfOneCanonicalSwap() {
		var marketData = requiredApi(MarketDataApi.class);
		resetBusinessTables();
		var source = new FirstSignalScenarioFixture().load().dataset().observations().getFirst();
		var alternative = changedRaw(source, source.transactionId().value(), "provider-b", source.payload());
		var first = marketData.replayVersioned(new RecordedDataset("swap-revisions-v1", List.of(source)));
		var second = marketData.replayVersioned(new RecordedDataset("swap-revisions-v1", List.of(alternative)));
		assertThat(first.items()).singleElement().satisfies(item ->
				assertThat(item.legacyItem().status()).isEqualTo(ReplayStatus.NORMALIZED));
		assertThat(second.items()).singleElement().satisfies(item ->
				assertThat(item.legacyItem().status())
						.as("second provider's valid swap revision must coexist")
						.isEqualTo(ReplayStatus.NORMALIZED));
		assertThat(second.items().getFirst().revision().revisionKey())
				.isNotEqualTo(first.items().getFirst().revision().revisionKey());
	}

	@Test
	void finalizationRejectsAnOmittedVisibleLiquidityKeyWithoutPublishingASnapshot() {
		var marketData = requiredApi(MarketDataApi.class);
		var facts = requiredApi(RecordMarketFactUseCase.class);
		jdbcClient.sql("DELETE FROM marketdata.liquidity_observations").update();
		resetBusinessTables();
		var fixture = new FirstSignalScenarioFixture().load();
		var replay = marketData.replayVersioned(new RecordedDataset("liquidity-coverage-v1",
				fixture.dataset().observations().subList(0, 2)));
		assertThat(replay.items()).allMatch(item -> item.legacyItem().status() == ReplayStatus.NORMALIZED);
		var older = replay.items().get(0).revision();
		var newer = replay.items().get(1).revision();
		var olderLiquidity = facts.storeVersionedLiquidity(new RecordMarketFactUseCase.VersionedLiquidityRequest(
				liquidityFact(older, fixture.decisionCutoff().minusSeconds(3600), "50000.00000000"),
				RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "recorded-fixture", "liquidity-v1"));
		facts.storeVersionedLiquidity(new RecordMarketFactUseCase.VersionedLiquidityRequest(
				liquidityFact(newer, fixture.decisionCutoff(), "80000.00000000"),
				RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "recorded-fixture", "liquidity-v1"));
		var before = rowCount("marketdata", "v2_dataset_snapshots");
		var scope = new SelectionScope(fixture.asset().chain(), List.of(fixture.asset()),
				fixture.decisionCutoff().minusSeconds(3600), fixture.decisionCutoff(),
				List.of(FactKind.LIQUIDITY), List.of("fixture-pool"), List.of());
		var request = new VersionedFinalizeRequest("length-prefixed-v2", fixture.decisionCutoff(),
				scope, List.of(new RevisionReference(FactKind.LIQUIDITY, older.canonicalIdentity(), olderLiquidity.revisionKey())),
				List.of(), "explicit-revisions-v1", "modeled-history-v1", AvailabilityStatus.HISTORICAL_MODEL);
		assertThatThrownBy(() -> marketData.finalizeVersioned(request))
				.as("the newer visible liquidity key has no disposition")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("incomplete");
		assertThat(rowCount("marketdata", "v2_dataset_snapshots")).isEqualTo(before);
		assertThat(rowCount("marketdata", "v2_dataset_snapshot_members")).isZero();
		assertThat(rowCount("marketdata", "v2_dataset_snapshot_exclusions")).isZero();
	}

	private LiquidityObservation liquidityFact(RevisionReference revision, Instant observedAt, String value) {
		var chain = revision.canonicalIdentity().chain();
		return new LiquidityObservation(revision.canonicalIdentity(),
				new AssetId(chain, "FixtureAsset1111111111111111111111111111111"), "fixture-pool",
				new AssetId(chain, "FixtureQuote1111111111111111111111111111111"),
				new BigDecimal(value), java.math.BigInteger.valueOf(1000), java.math.BigInteger.valueOf(2000),
				new BlockPosition(chain, 100), observedAt, new BigDecimal("1.0000"), "recorded-fixture", Optional.empty());
	}

	private VersionedFinalizeRequest versionedRequest(FirstSignalScenarioFixture.Scenario fixture,
			Instant cutoff, List<RevisionReference> refs) {
		return new VersionedFinalizeRequest("length-prefixed-v2", cutoff,
				new SelectionScope(fixture.asset().chain(), List.of(fixture.asset()),
						fixture.decisionCutoff().minusSeconds(3600), cutoff, List.of(FactKind.SWAP),
						List.of(), List.of()), refs, List.of(), "explicit-revisions-v1", "modeled-history-v1",
				AvailabilityStatus.HISTORICAL_MODEL);
	}

	@Test
	void replaysAndEvaluatesTheRecordedScenarioWithoutLookAheadOrDuplicateEffects() {
		var marketData = requiredApi(MarketDataApi.class);
		var risk = requiredApi(RiskApi.class);
		var signal = requiredApi(SignalApi.class);
		var evaluation = requiredApi(EvaluationApi.class);
		resetBusinessTables();

		var fixture = new FirstSignalScenarioFixture().load();
		var firstReplay = marketData.replay(fixture.dataset());
		assertThat(firstReplay.items()).hasSize(4).allSatisfy(item -> {
			assertThat(item.status()).isEqualTo(ReplayStatus.NORMALIZED);
			assertThat(item.failure()).isEmpty();
			assertThat(item.rawPayloadFingerprint()).matches("sha256:[0-9a-f]{64}");
		});
		assertThat(rowCount("marketdata", "raw_chain_events")).isEqualTo(4);
		assertThat(rowCount("marketdata", "normalized_swaps")).isEqualTo(4);

		var identities = firstReplay.items().stream().map(item -> item.identity()).toList();
		var snapshot = marketData.finalizeDataset(new FinalizeDatasetRequest(
				fixture.canonicalizationVersion(), fixture.evaluationCutoff(), identities));
		assertThat(snapshot.observations()).hasSize(4);
		assertThat(snapshot.fingerprint()).matches("sha256:[0-9a-f]{64}");

		var reversedIdentities = new ArrayList<>(identities);
		Collections.reverse(reversedIdentities);
		var permutedSnapshot = marketData.finalizeDataset(new FinalizeDatasetRequest(
				fixture.canonicalizationVersion(), fixture.evaluationCutoff(), reversedIdentities));
		assertThat(permutedSnapshot).isEqualTo(snapshot);
		var changedCanonicalization = marketData.finalizeDataset(new FinalizeDatasetRequest(
				"length-prefixed-v2", fixture.evaluationCutoff(), identities));
		assertThat(changedCanonicalization.fingerprint()).isNotEqualTo(snapshot.fingerprint());

		var decisionObservations = marketData.observations(new PointInTimeQuery(
				fixture.asset(), fixture.decisionCutoff().minusSeconds(3600), fixture.decisionCutoff(),
				fixture.decisionCutoff(), Optional.of(snapshot.fingerprint())));
		assertThat(decisionObservations).hasSize(2);
		assertThat(decisionObservations).extracting(observation -> observation.observedAt())
				.containsExactly(fixture.decisionCutoff().minusSeconds(3600), fixture.decisionCutoff());
		assertThat(decisionObservations).noneMatch(observation -> observation.observedAt().isAfter(fixture.decisionCutoff()));

		var equalReplay = marketData.replay(new RecordedDataset(
				fixture.dataset().fixtureVersion(), reversed(fixture.dataset().observations())));
		assertThat(equalReplay.items()).hasSize(4).allMatch(item -> item.status() == ReplayStatus.NORMALIZED);
		assertThat(rowCount("marketdata", "raw_chain_events")).isEqualTo(4);
		assertThat(rowCount("marketdata", "normalized_swaps")).isEqualTo(4);

		var invalid = changedRaw(fixture.dataset().observations().get(0), "fixture-invalid-tx", "other-provider", "not-json");
		var parseFailure = marketData.replay(new RecordedDataset("negative-parse-v1", List.of(invalid)));
		assertThat(parseFailure.items()).singleElement().satisfies(item -> {
			assertThat(item.status()).isEqualTo(ReplayStatus.NORMALIZATION_FAILED);
			assertThat(item.failure()).hasValueSatisfying(message -> assertThat(message).containsIgnoringCase("parse"));
		});
		assertThat(rowCount("marketdata", "raw_chain_events")).isEqualTo(5);
		assertThat(rowCount("marketdata", "normalized_swaps")).isEqualTo(4);

		var baseline = fixture.dataset().observations().get(0);
		var conflictingPayload = baseline.payload().replace("50000.00000000", "51000.00000000");
		var conflict = changedRaw(baseline, baseline.transactionId().value(), "conflicting-provider", conflictingPayload);
		var conflictResult = marketData.replay(new RecordedDataset("negative-conflict-v1", List.of(conflict)));
		assertThat(conflictResult.items()).singleElement().satisfies(item -> {
			assertThat(item.status()).isEqualTo(ReplayStatus.NORMALIZATION_FAILED);
			assertThat(item.failure()).hasValueSatisfying(message -> assertThat(message).containsIgnoringCase("conflict"));
		});
		assertThat(rowCount("marketdata", "raw_chain_events")).isEqualTo(6);
		assertThat(jdbcClient.sql("SELECT liquidity_usd FROM marketdata.normalized_swaps WHERE transaction_value = 'fixture-baseline-tx'")
				.query(BigDecimal.class).single()).isEqualByComparingTo("50000.00000000");

		assertThat(risk.assess(fixture.riskFacts()).decision()).isEqualTo(RiskDecision.ALLOW);
		assertThat(risk.assess(riskFacts(fixture, 1, AssetLifecycle.DISCOVERY, "80000.00000000")).decision())
				.isEqualTo(RiskDecision.WATCH_ONLY);
		assertThat(risk.assess(riskFacts(fixture, 2, AssetLifecycle.DISCOVERY, "80000.00000000")).decision())
				.isEqualTo(RiskDecision.BLOCK);
		assertThat(risk.assess(riskFacts(fixture, 0, AssetLifecycle.EARLY, "80000.00000000")).decision())
				.isEqualTo(RiskDecision.BLOCK);
		assertThat(risk.assess(riskFacts(fixture, 0, AssetLifecycle.DISCOVERY, "29999.99999999")).decision())
				.isEqualTo(RiskDecision.BLOCK);

		var detectionRequest = detectionRequest(fixture, snapshot.fingerprint(), fixture.riskFacts(),
				fixture.configurationFingerprint());
		var detection = signal.detect(detectionRequest);
		assertThat(detection.candidate()).hasValueSatisfying(candidate -> {
			assertThat(candidate.status()).isEqualTo(CandidateStatus.ACCEPTED);
			assertThat(candidate.riskAssessment()).hasValueSatisfying(assessment ->
					assertThat(assessment.decision()).isEqualTo(RiskDecision.ALLOW));
		});
		assertThat(detection.acceptedSignal()).hasValueSatisfying(accepted -> {
			assertThat(accepted.family()).isEqualTo("LIQUIDITY_SPIKE");
			assertThat(accepted.position()).isEqualTo("ENTRY");
			assertThat(accepted.availableAt()).isEqualTo(fixture.decisionCutoff());
			assertThat(accepted.sourceObservations()).containsExactly(
					decisionObservations.get(0).identity(), decisionObservations.get(1).identity());
			assertThat(accepted.score()).isEqualTo(70);
			assertThat(accepted.grade()).isEqualTo("B");
			assertThat(accepted.confidence()).isEqualByComparingTo("1.0000");
			assertThat(accepted.reasoning()).extracting(reason -> reason.points()).containsExactly(20, 30, 20);
		});
		var accepted = detection.acceptedSignal().orElseThrow();
		assertThat(signal.detect(detectionRequest)).isEqualTo(detection);
		assertThat(signal.acceptedSignal(accepted.signalId())).contains(accepted);
		assertThat(rowCount("signal", "signal_candidates")).isEqualTo(1);
		assertThat(rowCount("signal", "accepted_signals")).isEqualTo(1);

		var rejectedConfig = "sha256:" + "3".repeat(64);
		var rejected = signal.detect(detectionRequest(
				fixture, snapshot.fingerprint(), riskFacts(fixture, 1, AssetLifecycle.DISCOVERY, "80000.00000000"), rejectedConfig));
		assertThat(rejected.candidate()).hasValueSatisfying(candidate -> {
			assertThat(candidate.status()).isEqualTo(CandidateStatus.REJECTED);
			assertThat(candidate.riskAssessment()).hasValueSatisfying(assessment ->
					assertThat(assessment.decision()).isEqualTo(RiskDecision.WATCH_ONLY));
		});
		assertThat(rejected.acceptedSignal()).isEmpty();
		assertThat(rowCount("signal", "signal_candidates")).isEqualTo(2);
		assertThat(rowCount("signal", "accepted_signals")).isEqualTo(1);

		var noEvidence = signal.detect(new DetectionRequest(
				snapshot.fingerprint(), new AssetId(ChainId.SOLANA_MAINNET, "AbsentAsset11111111111111111111111111111111"),
				fixture.decisionCutoff().minusSeconds(3600), fixture.decisionCutoff(), fixture.riskFacts(),
				"liquidity-spike-v2", "liquidity-score-v1", "sha256:" + "4".repeat(64)));
		assertThat(noEvidence.candidate()).isEmpty();
		assertThat(noEvidence.acceptedSignal()).isEmpty();

		var pricedReport = evaluation.evaluate(new EvaluationRequest(
				accepted.signalId(), "1h", fixture.provenance(snapshot.fingerprint(), fixture.evaluationCutoff())));
		assertThat(pricedReport.family()).isEqualTo("LIQUIDITY_SPIKE");
		assertThat(pricedReport.signalCount()).isOne();
		assertThat(pricedReport.pricedOutcomeCount()).isOne();
		assertThat(pricedReport.unpricedOutcomeCount()).isZero();
		assertThat(pricedReport.averageNetReturn()).hasValueSatisfying(value ->
				assertThat(value).isEqualByComparingTo("0.16000000"));
		assertThat(pricedReport.reportFingerprint()).matches("sha256:[0-9a-f]{64}");
		assertThat(pricedReport.outcomes()).singleElement().satisfies(outcome -> {
			assertThat(outcome.status()).isEqualTo(PricingStatus.PRICED);
			assertThat(outcome.entryPrice()).hasValueSatisfying(price -> {
				assertThat(price.observedAt()).isEqualTo(fixture.decisionCutoff().plusSeconds(1));
				assertThat(price.priceUsd()).isEqualByComparingTo("1.000000000000000000");
			});
			assertThat(outcome.horizonPrice()).hasValueSatisfying(price ->
					assertThat(price.priceUsd()).isEqualByComparingTo("1.200000000000000000"));
			assertThat(outcome.grossReturn()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("0.20000000"));
			assertThat(outcome.friction()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("0.04000000"));
			assertThat(outcome.netReturn()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("0.16000000"));
		});
		assertThat(evaluation.evaluate(new EvaluationRequest(
				accepted.signalId(), "1h", fixture.provenance(snapshot.fingerprint(), fixture.evaluationCutoff()))))
				.isEqualTo(pricedReport);
		var subMicrosecondReport = evaluation.evaluate(new EvaluationRequest(
				accepted.signalId(), "1h",
				fixture.provenance(snapshot.fingerprint(), fixture.evaluationCutoff().plusNanos(123))));
		assertThat(subMicrosecondReport)
				.as("equivalent microsecond cutoffs must identify the same evaluation evidence")
				.isEqualTo(pricedReport);

		var unpriced = evaluation.evaluate(new EvaluationRequest(
				accepted.signalId(), "1h", fixture.provenance(snapshot.fingerprint(), fixture.decisionCutoff())));
		assertThat(unpriced.pricedOutcomeCount()).isZero();
		assertThat(unpriced.unpricedOutcomeCount()).isOne();
		assertThat(unpriced.averageNetReturn()).isEmpty();
		assertThat(unpriced.outcomes()).singleElement().satisfies(outcome -> {
			assertThat(outcome.status()).isEqualTo(PricingStatus.UNPRICED);
			assertThat(outcome.missingPriceReason()).hasValue("NO_ADMISSIBLE_ENTRY_PRICE");
		});

		var beforeIncomplete = rowCount("evaluation", "evaluation_runs");
		var incomplete = new RunProvenance(
				"", fixture.sourceRevision(), false, fixture.evaluationAlgorithmVersion(),
				fixture.configurationFingerprint(), snapshot.fingerprint(), fixture.evaluationCutoff(), null);
		assertThatThrownBy(() -> evaluation.evaluate(new EvaluationRequest(accepted.signalId(), "1h", incomplete)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("buildIdentity");
		assertThat(rowCount("evaluation", "evaluation_runs")).isEqualTo(beforeIncomplete);
		assertThat(rowCount("evaluation", "evaluation_runs")).isEqualTo(2);
		assertThat(rowCount("evaluation", "entry_outcomes")).isEqualTo(2);
		assertThat(rowCount("evaluation", "evaluation_reports")).isEqualTo(2);
		assertDatabaseContract();
	}

	@Test
	void normalizesSubMicrosecondSourceTimeBeforeEqualReplayComparison() {
		var marketData = requiredApi(MarketDataApi.class);
		resetBusinessTables();
		var fixture = new FirstSignalScenarioFixture().load();
		var source = fixture.dataset().observations().getFirst();
		var sourceTime = source.sourceEventTime().orElseThrow().plusNanos(123);
		var input = new RecordedSwapInput(
				source.chain(), source.transactionId(), source.eventId(), source.provider(), source.blockPosition(),
				source.blockHash(), Optional.of(sourceTime), source.observedAt(), source.payload(),
				source.transformationVersion());
		var dataset = new RecordedDataset("sub-microsecond-source-v1", List.of(input));

		assertThat(marketData.replay(dataset).items()).singleElement().satisfies(item ->
				assertThat(item.status()).isEqualTo(ReplayStatus.NORMALIZED));
		assertThat(marketData.replay(dataset).items()).singleElement().satisfies(item ->
				assertThat(item.status())
						.as("equal replay must compare source time at PostgreSQL microsecond precision")
						.isEqualTo(ReplayStatus.NORMALIZED));
		assertThat(marketData.observations(new PointInTimeQuery(
				fixture.asset(), source.observedAt(), source.observedAt(), source.observedAt(), Optional.empty())))
				.singleElement().satisfies(observation ->
						assertThat(observation.sourceEventTime()).contains(
								sourceTime.truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
	}

	@Test
	void normalizesSubMicrosecondSignalCutoffsBeforeIdentityAndCompletion() {
		var marketData = requiredApi(MarketDataApi.class);
		var signal = requiredApi(SignalApi.class);
		resetBusinessTables();
		var fixture = new FirstSignalScenarioFixture().load();
		var replay = marketData.replay(fixture.dataset());
		var snapshot = marketData.finalizeDataset(new FinalizeDatasetRequest(
				fixture.canonicalizationVersion(), fixture.evaluationCutoff(),
				replay.items().stream().map(item -> item.identity()).toList()));
		var cutoff = fixture.decisionCutoff().plusNanos(123);
		var request = new DetectionRequest(
				snapshot.fingerprint(), fixture.asset(), cutoff.minusSeconds(3600), cutoff,
				new RiskFacts(fixture.asset(), cutoff, 0, AssetLifecycle.DISCOVERY,
						new BigDecimal("80000.00000000"), "first-slice-risk-v1"),
				"liquidity-spike-v2", "liquidity-score-v1", fixture.configurationFingerprint());
		var captured = new AtomicReference<SignalApi.DetectionResult>();

		assertThatCode(() -> captured.set(signal.detect(request)))
				.as("first completion must use the same microsecond cutoff as its committed candidate")
				.doesNotThrowAnyException();
		var first = captured.get();
		assertThat(first.acceptedSignal()).hasValueSatisfying(accepted -> {
			assertThat(accepted.availableAt()).isEqualTo(fixture.decisionCutoff());
			assertThat(accepted.riskAssessment().facts().cutoff()).isEqualTo(fixture.decisionCutoff());
		});
		assertThat(signal.detect(request)).isEqualTo(first);
	}

	private <T> T requiredApi(Class<T> type) {
		var api = applicationContext.getBeanProvider(type).getIfAvailable();
		assertThat(api)
				.as("functional %s bean required by the first signal-evaluation behavior", type.getSimpleName())
				.isNotNull();
		return api;
	}

	private void resetBusinessTables() {
		for (var table : List.of(
				"marketdata.v2_dataset_snapshot_exclusions", "marketdata.v2_dataset_snapshot_members",
				"marketdata.v2_dataset_snapshots", "marketdata.usd_revisions",
				"marketdata.liquidity_revisions", "marketdata.price_revisions", "marketdata.swap_revisions",
				"evaluation.evaluation_reports", "evaluation.entry_outcomes", "evaluation.evaluation_runs",
				"signal.accepted_signals", "signal.signal_candidates",
				"marketdata.dataset_snapshot_members", "marketdata.dataset_snapshots",
				"marketdata.normalized_swaps", "marketdata.raw_chain_events")) {
			jdbcClient.sql("DELETE FROM " + table).update();
		}
	}

	private int rowCount(String schema, String table) {
		return jdbcClient.sql("SELECT count(*) FROM " + schema + "." + table).query(Integer.class).single();
	}

	private List<RecordedSwapInput> reversed(List<RecordedSwapInput> values) {
		var result = new ArrayList<>(values);
		Collections.reverse(result);
		return result;
	}

	private RecordedSwapInput changedRaw(RecordedSwapInput source, String transactionValue, String provider, String payload) {
		var transactionId = new TransactionId(source.chain(), transactionValue);
		return new RecordedSwapInput(
				source.chain(), transactionId, new EventId(transactionId, source.eventId().locator()), provider,
				new BlockPosition(source.chain(), source.blockPosition().value() + 100), source.blockHash(),
				source.sourceEventTime(), source.observedAt(), payload, source.transformationVersion());
	}

	private RiskFacts riskFacts(
			FirstSignalScenarioFixture.Scenario fixture, int flags, AssetLifecycle lifecycle, String liquidity) {
		return new RiskFacts(
				fixture.asset(), fixture.decisionCutoff(), flags, lifecycle, new BigDecimal(liquidity), "first-slice-risk-v1");
	}

	private DetectionRequest detectionRequest(
			FirstSignalScenarioFixture.Scenario fixture,
			String datasetFingerprint,
			RiskFacts riskFacts,
			String configurationFingerprint) {
		return new DetectionRequest(
				datasetFingerprint,
				fixture.asset(),
				fixture.decisionCutoff().minusSeconds(3600),
				fixture.decisionCutoff(),
				riskFacts,
				"liquidity-spike-v2",
				"liquidity-score-v1",
				configurationFingerprint);
	}

	private void assertDatabaseContract() {
		assertThat(jdbcClient.sql("SHOW server_version").query(String.class).single()).isEqualTo("18.6");
		assertThat(jdbcClient.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
				.query(String.class).list()).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
		assertThat(jdbcClient.sql("SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('marketdata','signal','evaluation','risk') ORDER BY schema_name")
				.query(String.class).list()).containsExactly("evaluation", "marketdata", "signal");
		assertThat(jdbcClient.sql("SELECT table_schema || '.' || table_name FROM information_schema.tables WHERE table_schema IN ('marketdata','signal','evaluation') ORDER BY table_schema, table_name")
				.query(String.class).list()).containsExactly(
						"evaluation.entry_outcomes", "evaluation.evaluation_reports", "evaluation.evaluation_runs",
						"evaluation.v2_entry_outcomes", "evaluation.v2_evaluation_reports", "evaluation.v2_evaluation_runs",
						"marketdata.dataset_snapshot_members", "marketdata.dataset_snapshots",
						"marketdata.liquidity_observations", "marketdata.liquidity_revisions",
						"marketdata.normalized_swaps", "marketdata.price_observations",
						"marketdata.price_revisions", "marketdata.raw_chain_events",
						"marketdata.raw_transactions", "marketdata.swap_revisions",
						"marketdata.universe_members", "marketdata.universe_snapshots",
						"marketdata.usd_conversion_facts", "marketdata.usd_revisions",
						"marketdata.v2_dataset_snapshot_exclusions", "marketdata.v2_dataset_snapshot_members",
						"marketdata.v2_dataset_snapshots",
						"signal.accepted_signals", "signal.signal_candidates",
						"signal.v2_accepted_signals", "signal.v2_signal_candidates");
		assertThat(jdbcClient.sql("SELECT indexname FROM pg_indexes WHERE schemaname='marketdata' AND tablename='normalized_swaps' ORDER BY indexname")
				.query(String.class).list()).containsExactly("normalized_swaps_pk", "normalized_swaps_point_in_time_idx");
		assertThat(jdbcClient.sql("SELECT numeric_precision || ':' || numeric_scale FROM information_schema.columns WHERE table_schema='marketdata' AND table_name='normalized_swaps' AND column_name IN ('price_usd','liquidity_usd') ORDER BY column_name")
				.query(String.class).list()).containsExactly("38:8", "38:18");
	}
}
