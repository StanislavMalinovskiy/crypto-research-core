package io.cryptoresearch.risk.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Objects;

import org.springframework.stereotype.Service;

import io.cryptoresearch.risk.api.RiskApi;

@Service
public class FirstSliceRiskAssessmentService implements RiskApi {

	private static final BigDecimal MINIMUM_LIQUIDITY = new BigDecimal("30000.00000000");

	@Override
	public RiskAssessment assess(RiskFacts facts) {
		Objects.requireNonNull(facts, "facts must not be null");
		Objects.requireNonNull(facts.asset(), "asset must not be null");
		var cutoff = Objects.requireNonNull(facts.cutoff(), "cutoff must not be null")
				.truncatedTo(ChronoUnit.MICROS);
		Objects.requireNonNull(facts.lifecycle(), "lifecycle must not be null");
		if (facts.manipulationFlags() < 0) {
			throw new IllegalArgumentException("manipulationFlags must not be negative");
		}
		var liquidity = Objects.requireNonNull(facts.liquidityUsd(), "liquidityUsd must not be null")
				.setScale(8, RoundingMode.UNNECESSARY);
		if (liquidity.signum() < 0) {
			throw new IllegalArgumentException("liquidityUsd must not be negative");
		}
		if (facts.evidenceVersion() == null || facts.evidenceVersion().isBlank()) {
			throw new IllegalArgumentException("evidenceVersion must not be blank");
		}

		var reasons = new ArrayList<String>();
		RiskDecision decision;
		if (facts.manipulationFlags() >= 2) {
			decision = RiskDecision.BLOCK;
			reasons.add("two-or-more-manipulation-flags");
		}
		else if (facts.lifecycle() == AssetLifecycle.PRE_LAUNCH || facts.lifecycle() == AssetLifecycle.EARLY) {
			decision = RiskDecision.BLOCK;
			reasons.add("lifecycle-before-discovery");
		}
		else if (liquidity.compareTo(MINIMUM_LIQUIDITY) < 0) {
			decision = RiskDecision.BLOCK;
			reasons.add("liquidity-below-30000-usd");
		}
		else if (facts.manipulationFlags() == 1) {
			decision = RiskDecision.WATCH_ONLY;
			reasons.add("one-manipulation-flag");
		}
		else {
			decision = RiskDecision.ALLOW;
			reasons.add("validated-discovery-or-later-with-sufficient-liquidity");
		}
		return new RiskAssessment(decision, new RiskFacts(
				facts.asset(), cutoff, facts.manipulationFlags(), facts.lifecycle(), liquidity,
				facts.evidenceVersion()), reasons);
	}
}
