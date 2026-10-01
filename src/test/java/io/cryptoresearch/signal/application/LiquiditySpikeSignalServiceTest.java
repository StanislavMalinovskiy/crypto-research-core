package io.cryptoresearch.signal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.kernel.api.WalletAddress;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.PointInTimeQuery;
import io.cryptoresearch.risk.api.RiskApi;
import io.cryptoresearch.risk.api.RiskApi.AssetLifecycle;
import io.cryptoresearch.risk.api.RiskApi.RiskAssessment;
import io.cryptoresearch.risk.api.RiskApi.RiskDecision;
import io.cryptoresearch.risk.api.RiskApi.RiskFacts;
import io.cryptoresearch.signal.api.SignalApi.DetectionRequest;
import io.cryptoresearch.signal.api.SignalApi.DetectionResult;
import io.cryptoresearch.signal.infrastructure.persistence.JdbcSignalPersistence;

class LiquiditySpikeSignalServiceTest {
	private static final Instant C = Instant.parse("2026-01-01T12:00:00Z");
	private static final AssetId ASSET = new AssetId(ChainId.SOLANA_MAINNET, "UnitAsset111");
	private static final String DATASET = "sha256:" + "a".repeat(64);
	private static final String CONFIG = "sha256:" + "b".repeat(64);

	@Test
	void appliesBothExactLiquidityThresholds() {
		var service = new LiquiditySpikeSignalService(null, null, null, null, null);

		assertThat(service.meetsThresholds(new BigDecimal("50000.00000000"), new BigDecimal("75000.00000000")))
				.isTrue();
		assertThat(service.meetsThresholds(new BigDecimal("50000.00000000"), new BigDecimal("74999.99999999")))
				.isFalse();
		assertThat(service.meetsThresholds(new BigDecimal("10000.00000000"), new BigDecimal("15000.00000000")))
				.isFalse();
	}

	@Test
	void queriesInclusiveEndpointWindowsAndSelectsLastOrderedIdentity() {
		var baseline = observation("base-left", C.minusSeconds(3900), "50000.00000000");
		var laterBaseline = observation("base-right", C.minusSeconds(3600), "50000.00000000");
		var current = observation("current-left", C.minusSeconds(60), "80000.00000000");
		var laterCurrent = observation("current-right", C, "80000.00000000");
		var harness = new Harness(List.of(baseline, laterBaseline, current, laterCurrent));
		var result = harness.service.detect(request("liquidity-spike-v2", C.minusSeconds(3600), C));
		assertThat(harness.queries).containsExactly(
				new PointInTimeQuery(ASSET, C.minusSeconds(3900), C.minusSeconds(3600), C, Optional.of(DATASET)),
				new PointInTimeQuery(ASSET, C.minusSeconds(60), C, C, Optional.of(DATASET)));
		assertThat(result.acceptedSignal().orElseThrow().sourceObservations())
				.containsExactly(laterBaseline.identity(), laterCurrent.identity());
	}

	@Test
	void excludesOneMicrosecondOutsideEveryBoundAndRejectsStaleEndpoints() {
		var micro = 1_000L;
		var baselineOutsideLeft = observation("old-base", C.minusSeconds(3900).minusNanos(micro), "50000.00000000");
		var baselineOutsideRight = observation("late-base", C.minusSeconds(3600).plusNanos(micro), "50000.00000000");
		var currentOutsideLeft = observation("old-current", C.minusSeconds(60).minusNanos(micro), "80000.00000000");
		var currentOutsideRight = observation("future-current", C.plusNanos(micro), "80000.00000000");
		for (var evidence : List.of(
				List.of(baselineOutsideLeft, currentOutsideRight, currentOutsideLeft),
				List.of(baselineOutsideRight, currentOutsideLeft, currentOutsideRight),
				List.of(observation("valid-base", C.minusSeconds(3600), "50000.00000000"), currentOutsideLeft),
				List.of(baselineOutsideLeft, observation("valid-current", C, "80000.00000000")))) {
			var harness = new Harness(evidence);
			assertThat(harness.service.detect(request("liquidity-spike-v2", C.minusSeconds(3600), C)).candidate()).isEmpty();
			verify(harness.transitions, never()).record(any());
		}
	}

	@Test
	void rejectsUnsupportedVersionsAndInvalidTargetsBeforeCollaborators() {
		for (var version : new String[] {"liquidity-spike-v1", "liquidity-spike-v3", "", " ", " liquidity-spike-v2", "liquidity-spike-v2 "}) {
			var harness = new Harness(List.of());
			assertThatThrownBy(() -> harness.service.detect(request(version, C.minusSeconds(3600), C)))
					.as("unsupported detector version: %s", version).isInstanceOf(IllegalArgumentException.class);
			assertThat(harness.queries).isEmpty();
			verify(harness.transitions, never()).record(any());
			harness.assertNoCollaboratorInteractions();
		}
		for (var start : List.of(C.minusSeconds(3600).minusNanos(1_000), C.minusSeconds(3600).plusNanos(1_000))) {
			var harness = new Harness(List.of());
			assertThatThrownBy(() -> harness.service.detect(request("liquidity-spike-v2", start, C)))
					.isInstanceOf(IllegalArgumentException.class);
			assertThat(harness.queries).isEmpty();
			harness.assertNoCollaboratorInteractions();
		}
		var harness = new Harness(List.of());
		assertThatThrownBy(() -> harness.service.detect(request("liquidity-spike-v2", Instant.MIN, Instant.MIN)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(harness.queries).isEmpty();
		harness.assertNoCollaboratorInteractions();
	}

	@Test
	void rejectsNullDetectorVersionBeforeAnyCollaboratorInteraction() {
		var harness = new Harness(List.of());
		assertThatThrownBy(() -> harness.service.detect(request(null, C.minusSeconds(3600), C)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(harness.queries).isEmpty();
		harness.assertNoCollaboratorInteractions();
	}

	@Test
	void rejectsUnrepresentableBaselineLowerBoundWithRepresentableOneHourTarget() {
		var cutoff = Instant.MIN.plusSeconds(3600);
		var target = cutoff.minusSeconds(3600);
		assertThat(target).isEqualTo(Instant.MIN);
		var harness = new Harness(List.of());
		assertThatThrownBy(() -> harness.service.detect(request("liquidity-spike-v2", target, cutoff)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(harness.queries).isEmpty();
		harness.assertNoCollaboratorInteractions();
	}

	@Test
	void normalizesTargetAndUsesObservationTimeDespiteSourceTime() {
		var baseline = observation("base", C.minusSeconds(3600), "50000.00000000");
		var current = observation("current", C, "80000.00000000");
		var harness = new Harness(List.of(baseline, current));
		var result = harness.service.detect(request("liquidity-spike-v2", C.minusSeconds(3600).plusNanos(123), C.plusNanos(456)));
		assertThat(result.acceptedSignal().orElseThrow().availableAt()).isEqualTo(C);
		assertThat(harness.queries.getFirst().fromInclusive()).isEqualTo(C.minusSeconds(3900));
	}

	private static DetectionRequest request(String version, Instant start, Instant cutoff) {
		return new DetectionRequest(DATASET, ASSET, start, cutoff,
				new RiskFacts(ASSET, cutoff, 0, AssetLifecycle.DISCOVERY,
						new BigDecimal("80000.00000000"), "risk-v1"), version, "score-v1", CONFIG);
	}

	private static MarketObservation observation(String transaction, Instant observed, String liquidity) {
		var tx = new TransactionId(ASSET.chain(), transaction);
		return new MarketObservation(new MarketDataApi.NormalizedSwapIdentity(ASSET.chain(), tx, new EventId(tx, "0")),
				ASSET, new WalletAddress(ASSET.chain(), "UnitWallet111"), MarketDataApi.TradeSide.BUY,
				BigInteger.ONE, BigInteger.ONE, new BigDecimal("1.000000000000000000"), new BigDecimal(liquidity),
				new BigDecimal("1.0000"), "unit-venue", new BlockPosition(ASSET.chain(), 1), Optional.empty(),
				Optional.of(C.minusSeconds(7200)), observed, "provider", DATASET, "normalizer-v1");
	}

	private static final class Harness {
		final MarketDataApi market = mock(MarketDataApi.class);
		final RiskApi risk = mock(RiskApi.class);
		final SignalCandidateTransitions transitions = mock(SignalCandidateTransitions.class);
		final JdbcSignalPersistence persistence = mock(JdbcSignalPersistence.class);
		final List<PointInTimeQuery> queries = new ArrayList<>();
		final LiquiditySpikeSignalService service = new LiquiditySpikeSignalService(
				market, risk, transitions, persistence, null);

		void assertNoCollaboratorInteractions() {
			verifyNoInteractions(market, risk, transitions, persistence);
		}

		Harness(List<MarketObservation> evidence) {
			when(market.observations(any())).thenAnswer(invocation -> {
				PointInTimeQuery query = invocation.getArgument(0);
				queries.add(query);
				return evidence.stream().filter(item -> item.asset().equals(query.asset()))
						.filter(item -> !item.observedAt().isBefore(query.fromInclusive())
								&& !item.observedAt().isAfter(query.toInclusive())
								&& !item.observedAt().isAfter(query.cutoff()))
						.toList();
			});
			when(risk.assess(any())).thenAnswer(invocation -> new RiskAssessment(
					RiskDecision.ALLOW, invocation.getArgument(0), List.of()));
			when(transitions.complete(any(), any(), any())).thenAnswer(invocation -> new DetectionResult(
					Optional.of(invocation.getArgument(0)), invocation.getArgument(2)));
		}
	}
}
