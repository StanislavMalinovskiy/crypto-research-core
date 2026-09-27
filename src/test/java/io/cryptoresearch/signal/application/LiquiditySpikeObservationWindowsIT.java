package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.FinalizeDatasetRequest;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedDataset;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedSwapInput;
import io.cryptoresearch.risk.api.RiskApi.AssetLifecycle;
import io.cryptoresearch.risk.api.RiskApi.RiskFacts;
import io.cryptoresearch.signal.api.SignalApi;
import io.cryptoresearch.signal.api.SignalApi.DetectionRequest;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LiquiditySpikeObservationWindowsIT {
	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	@Autowired MarketDataApi market;
	@Autowired SignalApi signal;
	@Autowired JdbcClient jdbc;

	private static final String CONFIG = "sha256:" + "b".repeat(64);

	@Test
	void missingAndStaleEndpointsAndInvalidRequestsLeaveNoSignalRows() {
		clear();
		var fixture = new io.cryptoresearch.FirstSignalScenarioFixture().load();
		var baseline = fixture.dataset().observations().get(0);
		var current = fixture.dataset().observations().get(1);
		var staleBaseline = copy(baseline, "stale-baseline", fixture.decisionCutoff().minusSeconds(3900).minusNanos(1_000));
		var staleCurrent = copy(current, "stale-current", fixture.decisionCutoff().minusSeconds(60).minusNanos(1_000));
		var validBaseline = copy(baseline, "valid-baseline", fixture.decisionCutoff().minusSeconds(3900));
		var validCurrent = copy(current, "valid-current", fixture.decisionCutoff().minusSeconds(60));
		for (var inputs : List.of(List.of(staleBaseline, validCurrent), List.of(validBaseline, staleCurrent),
				List.of(validBaseline), List.of(validCurrent))) {
			var fingerprint = snapshot(inputs, fixture.evaluationCutoff());
			assertThat(signal.detect(request(fixture.asset(), fingerprint, fixture.decisionCutoff(),
						"liquidity-spike-v2")).candidate()).isEmpty();
		}
		var fingerprint = snapshot(List.of(validBaseline, validCurrent), fixture.evaluationCutoff());
		assertThatThrownBy(() -> signal.detect(request(fixture.asset(), fingerprint, fixture.decisionCutoff(),
					"liquidity-spike-v1"))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> signal.detect(new DetectionRequest(fingerprint, fixture.asset(),
					fixture.decisionCutoff().minusSeconds(3600).plusNanos(1_000), fixture.decisionCutoff(),
					facts(fixture.asset(), fixture.decisionCutoff()), "liquidity-spike-v2", "liquidity-score-v1", CONFIG)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(count("signal.signal_candidates")).isZero();
		assertThat(count("signal.accepted_signals")).isZero();
	}

	@Test
	void postgresBytewiseTieAndBoundaryEvidenceRemainImmutableOnReplay() {
		clear();
		var fixture = new io.cryptoresearch.FirstSignalScenarioFixture().load();
		var baseline = copy(fixture.dataset().observations().get(0), "baseline", fixture.decisionCutoff().minusSeconds(3900));
		var lower = copy(fixture.dataset().observations().get(1), "tie-\uE000", fixture.decisionCutoff());
		var higher = copy(fixture.dataset().observations().get(1), "tie-\uD83D\uDE00", fixture.decisionCutoff());
		var fingerprint = snapshot(List.of(higher, lower, baseline), fixture.evaluationCutoff());
		var request = request(fixture.asset(), fingerprint, fixture.decisionCutoff(), "liquidity-spike-v2");
		var result = signal.detect(request);
		var accepted = result.acceptedSignal().orElseThrow();
		assertThat(accepted.sourceObservations()).extracting(id -> id.transactionId().value())
				.containsExactly("baseline", "tie-\uD83D\uDE00");
		assertThat(accepted.detectorVersion()).isEqualTo("liquidity-spike-v2");
		assertThat(accepted.configurationFingerprint()).isEqualTo(CONFIG);
		assertThat(accepted.score()).isEqualTo(70);
		assertThat(accepted.confidence()).isEqualByComparingTo("1.0000");
		assertThat(signal.detect(request)).isEqualTo(result);
		assertThat(signal.acceptedSignal(accepted.signalId())).contains(accepted);
		assertThat(count("signal.signal_candidates")).isEqualTo(1);
		assertThat(count("signal.accepted_signals")).isEqualTo(1);
	}

	@Test
	void postgresLocatorTiesProduceIdenticalSnapshotsAcrossIndependentSubmissionOrders() {
		clear();
		var fixture = new io.cryptoresearch.FirstSignalScenarioFixture().load();
		var baseline = copy(fixture.dataset().observations().get(0), "locator-baseline",
				fixture.decisionCutoff().minusSeconds(3900));
		var lower = copy(fixture.dataset().observations().get(1), "locator-tie", "event-\uE000",
				fixture.decisionCutoff());
		var higher = copy(fixture.dataset().observations().get(1), "locator-tie", "event-\uD83D\uDE00",
				fixture.decisionCutoff());
		var fingerprint = snapshot(List.of(higher, baseline, lower), fixture.evaluationCutoff());
		var first = signal.detect(request(fixture.asset(), fingerprint, fixture.decisionCutoff(), "liquidity-spike-v2"));
		var accepted = first.acceptedSignal().orElseThrow();
		var expectedBaseline = new MarketDataApi.NormalizedSwapIdentity(
				baseline.chain(), baseline.transactionId(), baseline.eventId());
		var expectedCurrent = new MarketDataApi.NormalizedSwapIdentity(
				higher.chain(), higher.transactionId(), higher.eventId());
		assertThat(accepted.sourceObservations()).containsExactly(expectedBaseline, expectedCurrent);
		assertThat(signal.acceptedSignal(accepted.signalId())).contains(accepted);
		assertThat(count("marketdata.raw_chain_events")).isEqualTo(3);
		assertThat(count("marketdata.normalized_swaps")).isEqualTo(3);
		assertThat(count("marketdata.dataset_snapshot_members")).isEqualTo(3);
		assertThat(count("marketdata.dataset_snapshots")).isEqualTo(1);
		assertThat(count("signal.signal_candidates")).isEqualTo(1);
		assertThat(count("signal.accepted_signals")).isEqualTo(1);

		// Rebuild the equivalent dataset from empty storage, not an idempotent replay of its first insertion.
		clear();
		assertThat(count("marketdata.raw_chain_events")).isZero();
		assertThat(count("marketdata.normalized_swaps")).isZero();
		assertThat(count("signal.signal_candidates")).isZero();
		assertThat(count("signal.accepted_signals")).isZero();
		var reorderedFingerprint = snapshot(List.of(lower, baseline, higher), fixture.evaluationCutoff());
		assertThat(reorderedFingerprint).isEqualTo(fingerprint);
		var reorderedRequest = request(fixture.asset(), reorderedFingerprint, fixture.decisionCutoff(), "liquidity-spike-v2");
		var reordered = signal.detect(reorderedRequest);
		assertThat(reordered).isEqualTo(first);
		assertThat(reordered.acceptedSignal().orElseThrow().sourceObservations())
				.containsExactly(expectedBaseline, expectedCurrent);
		assertThat(signal.acceptedSignal(accepted.signalId())).contains(accepted);
		assertThat(signal.detect(reorderedRequest)).isEqualTo(first);
		assertThat(count("marketdata.raw_chain_events")).isEqualTo(3);
		assertThat(count("marketdata.normalized_swaps")).isEqualTo(3);
		assertThat(count("marketdata.dataset_snapshot_members")).isEqualTo(3);
		assertThat(count("marketdata.dataset_snapshots")).isEqualTo(1);
		assertThat(count("signal.signal_candidates")).isEqualTo(1);
		assertThat(count("signal.accepted_signals")).isEqualTo(1);
	}

	@Test
	void readsPersistedLegacySnapshotWithoutRunningLegacyDetector() {
		clear();
		var fixture = new io.cryptoresearch.FirstSignalScenarioFixture().load();
		var fingerprint = snapshot(fixture.dataset().observations().subList(0, 2), fixture.evaluationCutoff());
		var current = signal.detect(request(fixture.asset(), fingerprint, fixture.decisionCutoff(), "liquidity-spike-v2"))
				.acceptedSignal().orElseThrow();
		// Signal-owned fixture setup simulates a snapshot written by the prior detector revision.
		jdbc.sql("UPDATE signal.signal_candidates SET detector_version = 'liquidity-spike-v1' WHERE candidate_id = :id")
				.param("id", current.candidateId()).update();
		jdbc.sql("UPDATE signal.accepted_signals SET detector_version = 'liquidity-spike-v1' WHERE signal_id = :id")
				.param("id", current.signalId()).update();
		var historical = signal.acceptedSignal(current.signalId()).orElseThrow();
		assertThat(historical).isEqualTo(new SignalApi.AcceptedSignalSnapshot(
				current.signalId(), current.candidateId(), current.family(), current.position(), current.asset(),
				current.availableAt(), current.datasetFingerprint(), current.sourceObservations(), current.riskAssessment(),
				"liquidity-spike-v1", current.scorerVersion(), current.configurationFingerprint(), current.score(),
				current.grade(), current.confidence(), current.reasoning()));
		assertThat(count("signal.signal_candidates")).isEqualTo(1);
		assertThat(count("signal.accepted_signals")).isEqualTo(1);
	}

	private String snapshot(List<RecordedSwapInput> inputs, Instant cutoff) {
		var replay = market.replay(new RecordedDataset("observation-window-v2", inputs));
		assertThat(replay.items()).allMatch(item -> item.failure().isEmpty());
		return market.finalizeDataset(new FinalizeDatasetRequest("length-prefixed-v1", cutoff,
				replay.items().stream().map(item -> item.identity()).toList())).fingerprint();
	}

	private RecordedSwapInput copy(RecordedSwapInput source, String txValue, Instant observedAt) {
		return copy(source, txValue, source.eventId().locator(), observedAt);
	}

	private RecordedSwapInput copy(RecordedSwapInput source, String txValue, String locator, Instant observedAt) {
		var tx = new TransactionId(source.chain(), txValue);
		return new RecordedSwapInput(source.chain(), tx, new EventId(tx, locator),
				source.provider(), new BlockPosition(source.chain(), source.blockPosition().value()), source.blockHash(),
				source.sourceEventTime(), observedAt, source.payload(), source.transformationVersion());
	}

	private DetectionRequest request(AssetId asset, String fingerprint, Instant cutoff, String version) {
		return new DetectionRequest(fingerprint, asset, cutoff.minusSeconds(3600), cutoff, facts(asset, cutoff),
				version, "liquidity-score-v1", CONFIG);
	}

	private RiskFacts facts(AssetId asset, Instant cutoff) {
		return new RiskFacts(asset, cutoff, 0, AssetLifecycle.DISCOVERY,
				new BigDecimal("80000.00000000"), "first-slice-risk-v1");
	}

	private int count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
	}

	private void clear() {
		jdbc.sql("DELETE FROM signal.accepted_signals").update();
		jdbc.sql("DELETE FROM signal.signal_candidates").update();
		jdbc.sql("DELETE FROM marketdata.dataset_snapshot_members").update();
		jdbc.sql("DELETE FROM marketdata.dataset_snapshots").update();
		jdbc.sql("DELETE FROM marketdata.normalized_swaps").update();
		jdbc.sql("DELETE FROM marketdata.raw_chain_events").update();
	}
}
