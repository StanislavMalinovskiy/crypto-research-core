package io.cryptoresearch.evaluation.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.cryptoresearch.evaluation.api.EvaluationApi.EntryOutcome;
import io.cryptoresearch.evaluation.api.EvaluationApi.PricingStatus;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.signal.api.SignalApi.AcceptedSignalSnapshot;

@Component
public class EntryValuation {

	private static final int RETURN_SCALE = 8;
	private static final Comparator<MarketObservation> PRICE_ORDER = Comparator
			.comparing(MarketObservation::observedAt)
			.thenComparing(MarketObservation::identity);

	public EntryOutcome evaluate(
			String outcomeId,
			AcceptedSignalSnapshot signal,
			String horizon,
			List<MarketObservation> entryCandidates,
			List<MarketObservation> horizonCandidates) {
		if (!"1h".equals(horizon)) {
			throw new IllegalArgumentException("Only the 1h horizon is supported by the first slice");
		}
		var horizonStart = signal.availableAt().plus(Duration.ofHours(1));
		var entry = entryCandidates.stream()
				.filter(observation -> observation.observedAt().isAfter(signal.availableAt()))
				.filter(observation -> observation.observedAt().isBefore(horizonStart))
				.min(PRICE_ORDER);
		if (entry.isEmpty()) {
			return unpriced(outcomeId, signal.signalId(), horizon, "NO_ADMISSIBLE_ENTRY_PRICE");
		}
		var horizonPrice = horizonCandidates.stream().min(PRICE_ORDER);
		if (horizonPrice.isEmpty()) {
			return unpriced(outcomeId, signal.signalId(), horizon, "NO_ADMISSIBLE_HORIZON_PRICE");
		}
		var gross = horizonPrice.orElseThrow().priceUsd()
				.subtract(entry.orElseThrow().priceUsd())
				.divide(entry.orElseThrow().priceUsd(), RETURN_SCALE, RoundingMode.HALF_EVEN);
		var friction = friction(entry.orElseThrow().liquidityUsd());
		var net = gross.subtract(friction).setScale(RETURN_SCALE, RoundingMode.HALF_EVEN);
		return new EntryOutcome(
				outcomeId, signal.signalId(), horizon, PricingStatus.PRICED, Optional.empty(), entry, horizonPrice,
				Optional.of(gross), Optional.of(friction), Optional.of(net));
	}

	BigDecimal friction(BigDecimal liquidityUsd) {
		if (liquidityUsd.compareTo(new BigDecimal("200000.00000000")) >= 0) {
			return new BigDecimal("0.03000000");
		}
		if (liquidityUsd.compareTo(new BigDecimal("50000.00000000")) >= 0) {
			return new BigDecimal("0.04000000");
		}
		return new BigDecimal("0.05000000");
	}

	private EntryOutcome unpriced(String outcomeId, String signalId, String horizon, String reason) {
		return new EntryOutcome(
				outcomeId, signalId, horizon, PricingStatus.UNPRICED, Optional.of(reason),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}
}
