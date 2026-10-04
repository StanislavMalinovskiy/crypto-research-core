package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.AvailabilityStatus;
import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedDataset;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedSwapInput;
import io.cryptoresearch.marketdata.api.MarketDataApi.SelectionScope;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedFinalizeRequest;
import io.cryptoresearch.signal.api.SignalApi;
import io.cryptoresearch.signal.api.SignalApi.DetectionRequest;
import io.cryptoresearch.signal.api.SignalApi.VersionedDetectionRequest;
import io.cryptoresearch.risk.api.RiskApi;
import io.cryptoresearch.risk.api.RiskApi.RiskAssessment;
import io.cryptoresearch.risk.api.RiskApi.RiskDecision;
import io.cryptoresearch.risk.api.RiskApi.RiskFacts;
import io.cryptoresearch.signal.application.VersionedLiquiditySpikeSignalService;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VersionedSignalEvidenceIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private final MarketDataApi marketData;
	private final SignalApi signals;
	private final JdbcClient jdbc;

	@Autowired
	VersionedSignalEvidenceIT(MarketDataApi marketData, SignalApi signals, JdbcClient jdbc) {
		this.marketData = marketData;
		this.signals = signals;
		this.jdbc = jdbc;
	}

	@Test
	void equivalentNanosecondDetectionWindowAndRiskCutoffsResolveTheSameSignal() {
		var fixture = new FirstSignalScenarioFixture().load();
		var replay = marketData.replayVersioned(new RecordedDataset("decision-micros",
				fixture.dataset().observations().subList(0, 2)));
		var refs = replay.items().stream().map(MarketDataApi.VersionedReplayItem::revision).toList();
		var cutoff = fixture.decisionCutoff();
		var scope = new SelectionScope(fixture.asset().chain(), List.of(fixture.asset()),
				cutoff.minusSeconds(3600), cutoff, List.of(FactKind.SWAP), List.of(), List.of());
		var snapshot = marketData.finalizeVersioned(new VersionedFinalizeRequest("length-prefixed-v2",
				cutoff, scope, refs, List.of(), "explicit-revisions-v1", "modeled-history-v1",
				AvailabilityStatus.HISTORICAL_MODEL));
		var detection = new DetectionRequest(snapshot.fingerprint(), fixture.asset(), cutoff.minusSeconds(3600),
				cutoff, fixture.riskFacts(), "liquidity-spike-v2", "liquidity-score-v1", fixture.configurationFingerprint());
		var canonical = signals.detectVersioned(new VersionedDetectionRequest(snapshot.fingerprint(), detection));
		assertThat(canonical.legacyResult().acceptedSignal()).isPresent();
		var originalRisk = fixture.riskFacts();
		var nanosRisk = new RiskFacts(originalRisk.asset(), cutoff.plusNanos(987), originalRisk.manipulationFlags(),
				originalRisk.lifecycle(), originalRisk.liquidityUsd(), originalRisk.evidenceVersion());
		var nanos = new DetectionRequest(snapshot.fingerprint(), fixture.asset(), cutoff.minusSeconds(3600).plusNanos(456),
				cutoff.plusNanos(123), nanosRisk, detection.detectorVersion(), detection.scorerVersion(),
				detection.configurationFingerprint());
		assertThatCode(() -> assertThat(signals.detectVersioned(new VersionedDetectionRequest(snapshot.fingerprint(), nanos)))
				.isEqualTo(canonical)).doesNotThrowAnyException();
		var accepted = canonical.legacyResult().acceptedSignal().orElseThrow();
		assertThat(accepted.riskAssessment().facts().cutoff()).isEqualTo(cutoff);
		assertThat(signals.versionedAcceptedSignal(accepted.signalId()).orElseThrow().signal()).isEqualTo(accepted);
		assertThat(jdbc.sql("SELECT count(*) FROM signal.v2_accepted_signals WHERE signal_id = :id")
				.param("id", accepted.signalId()).query(Integer.class).single()).isOne();
	}

	@Test
	void acceptedSignalIdentityIncludesRiskEvidenceVersionAndReasons() {
		var fixture = new FirstSignalScenarioFixture().load();
		var original = fixture.riskFacts();
		var changedVersion = new RiskFacts(original.asset(), original.cutoff(), original.manipulationFlags(),
				original.lifecycle(), original.liquidityUsd(), "changed-risk-evidence-v2");
		var request = new DetectionRequest("decision-test", fixture.asset(),
				fixture.decisionCutoff().minusSeconds(3600), fixture.decisionCutoff(), original,
				"liquidity-spike-v2", "liquidity-score-v1", fixture.configurationFingerprint());
		var versionRequest = new DetectionRequest(request.datasetFingerprint(), request.asset(),
				request.windowStart(), request.decisionCutoff(), changedVersion,
				request.detectorVersion(), request.scorerVersion(), request.configurationFingerprint());
		RiskApi reasonA = facts -> new RiskAssessment(RiskDecision.ALLOW, facts, List.of("reason-a"));
		RiskApi reasonB = facts -> new RiskAssessment(RiskDecision.ALLOW, facts, List.of("reason-b"));
		var first = new VersionedLiquiditySpikeSignalService(null, reasonA, null);
		var second = new VersionedLiquiditySpikeSignalService(null, reasonB, null);
		String baseline = ReflectionTestUtils.invokeMethod(first, "signalId", "candidate-test", request,
				List.of(), RiskDecision.ALLOW, new BigDecimal("50000.00000000"));
		String version = ReflectionTestUtils.invokeMethod(first, "signalId", "candidate-test", versionRequest,
				List.of(), RiskDecision.ALLOW, new BigDecimal("50000.00000000"));
		String reasons = ReflectionTestUtils.invokeMethod(second, "signalId", "candidate-test", request,
				List.of(), RiskDecision.ALLOW, new BigDecimal("50000.00000000"));
		assertThat(version).isNotEqualTo(baseline);
		assertThat(reasons).isNotEqualTo(baseline);
	}

	@Test
	void acceptedDecisionPinsExactRevisionsAcrossFutureFactsAndLateBackfill() {
		var fixture = new FirstSignalScenarioFixture().load();
		var replay = marketData.replayVersioned(new RecordedDataset("decision-v2",
				fixture.dataset().observations().subList(0, 2)));
		var refs = replay.items().stream().map(MarketDataApi.VersionedReplayItem::revision).toList();
		var scope = new SelectionScope(fixture.asset().chain(), List.of(fixture.asset()),
				fixture.decisionCutoff().minusSeconds(3600), fixture.decisionCutoff(),
				List.of(FactKind.SWAP), List.of(), List.of());
		var snapshot = marketData.finalizeVersioned(new VersionedFinalizeRequest("length-prefixed-v2",
				fixture.decisionCutoff(), scope, refs, List.of(), "explicit-revisions-v1",
				"modeled-history-v1", AvailabilityStatus.HISTORICAL_MODEL));
		var detection = new DetectionRequest(snapshot.fingerprint(), fixture.asset(),
				fixture.decisionCutoff().minusSeconds(3600), fixture.decisionCutoff(), fixture.riskFacts(),
				"liquidity-spike-v2", "liquidity-score-v1", fixture.configurationFingerprint());
		var accepted = signals.detectVersioned(new VersionedDetectionRequest(snapshot.fingerprint(), detection))
				.legacyResult().acceptedSignal().orElseThrow();
		var pinned = signals.versionedAcceptedSignal(accepted.signalId()).orElseThrow();
		assertThat(pinned.sourceRevisions()).containsExactlyElementsOf(refs);
		assertThat(pinned.decisionCutoff()).isEqualTo(fixture.decisionCutoff());
		assertThat(jdbc.sql("""
				SELECT decision_dataset_fingerprint || '|' || baseline_revision_key || '|' || current_revision_key
				FROM signal.v2_accepted_signals WHERE signal_id = :id
				""").param("id", accepted.signalId()).query(String.class).single())
				.isEqualTo(snapshot.fingerprint() + "|" + refs.get(0).revisionKey() + "|" + refs.get(1).revisionKey());
		assertThat(jdbc.sql("SELECT decision_cutoff FROM signal.v2_accepted_signals WHERE signal_id = :id")
				.param("id", accepted.signalId()).query(java.time.OffsetDateTime.class).single().toInstant())
				.isEqualTo(fixture.decisionCutoff());
		assertThat(jdbc.sql("SELECT count(*) FROM signal.accepted_signals").query(Integer.class).single()).isZero();

		marketData.replayVersioned(new RecordedDataset("future-v2",
				fixture.dataset().observations().subList(2, 4)));
		var baseline = fixture.dataset().observations().getFirst();
		var late = new RecordedSwapInput(baseline.chain(), baseline.transactionId(), baseline.eventId(),
				"late-provider", baseline.blockPosition(), baseline.blockHash(), baseline.sourceEventTime(),
				baseline.observedAt(), baseline.payload(), baseline.transformationVersion());
		marketData.replayVersioned(new RecordedDataset("late-backfill-v2", List.of(late)));
		assertThat(signals.acceptedSignal(accepted.signalId())).contains(accepted);
		assertThat(signals.versionedAcceptedSignal(accepted.signalId())).contains(pinned);
		assertThat(signals.detectVersioned(new VersionedDetectionRequest(snapshot.fingerprint(), detection))
				.legacyResult().acceptedSignal()).contains(accepted);
		assertThat(jdbc.sql("SELECT count(*) FROM signal.v2_accepted_signals")
				.query(Integer.class).single()).isOne();
	}
}
