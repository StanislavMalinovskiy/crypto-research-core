package io.cryptoresearch.risk.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.cryptoresearch.kernel.api.AssetId;

/** Pure synchronous point-in-time risk assessment boundary. */
public interface RiskApi {

	RiskAssessment assess(RiskFacts facts);

	enum AssetLifecycle {
		PRE_LAUNCH,
		EARLY,
		DISCOVERY,
		ESTABLISHED
	}

	enum RiskDecision {
		BLOCK,
		WATCH_ONLY,
		ALLOW
	}

	record RiskFacts(
			AssetId asset,
			Instant cutoff,
			int manipulationFlags,
			AssetLifecycle lifecycle,
			BigDecimal liquidityUsd,
			String evidenceVersion) {
	}

	record RiskAssessment(RiskDecision decision, RiskFacts facts, List<String> reasons) {
		public RiskAssessment {
			reasons = List.copyOf(reasons);
		}
	}
}
