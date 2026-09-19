package io.cryptoresearch.risk.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.risk.api.RiskApi.AssetLifecycle;
import io.cryptoresearch.risk.api.RiskApi.RiskDecision;
import io.cryptoresearch.risk.api.RiskApi.RiskFacts;

class FirstSliceRiskAssessmentServiceTest {

	private final FirstSliceRiskAssessmentService service = new FirstSliceRiskAssessmentService();
	private final AssetId asset = new AssetId(ChainId.SOLANA_MAINNET, "asset");
	private final Instant cutoff = Instant.parse("2026-09-01T01:00:00Z");

	@Test
	void allowsTheExactFirstSliceBoundaryWithoutReadingMachineTime() {
		assertThat(service.assess(facts(0, AssetLifecycle.DISCOVERY, "30000.00000000")).decision())
				.isEqualTo(RiskDecision.ALLOW);
	}

	@Test
	void appliesManipulationLifecycleAndLiquidityBoundaries() {
		assertThat(service.assess(facts(1, AssetLifecycle.DISCOVERY, "80000.00000000")).decision())
				.isEqualTo(RiskDecision.WATCH_ONLY);
		assertThat(service.assess(facts(2, AssetLifecycle.DISCOVERY, "80000.00000000")).decision())
				.isEqualTo(RiskDecision.BLOCK);
		assertThat(service.assess(facts(0, AssetLifecycle.EARLY, "80000.00000000")).decision())
				.isEqualTo(RiskDecision.BLOCK);
		assertThat(service.assess(facts(0, AssetLifecycle.DISCOVERY, "29999.99999999")).decision())
				.isEqualTo(RiskDecision.BLOCK);
	}

	private RiskFacts facts(int flags, AssetLifecycle lifecycle, String liquidity) {
		return new RiskFacts(asset, cutoff, flags, lifecycle, new BigDecimal(liquidity), "first-slice-risk-v1");
	}
}
