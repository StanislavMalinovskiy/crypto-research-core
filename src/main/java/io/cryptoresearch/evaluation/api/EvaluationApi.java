package io.cryptoresearch.evaluation.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;

/** Synchronous boundary for reproducible signal evaluation and evidence reports. */
public interface EvaluationApi {

	EvaluationReport evaluate(EvaluationRequest request);

	record RunProvenance(
			String buildIdentity,
			String sourceRevision,
			boolean sourceDirty,
			String algorithmVersion,
			String configurationFingerprint,
			String datasetFingerprint,
			Instant evaluationCutoff,
			OptionalLong seed) {
		public RunProvenance {
			seed = seed == null ? OptionalLong.empty() : seed;
		}
	}

	record EvaluationRequest(String signalId, String horizon, RunProvenance provenance) {
	}

	enum PricingStatus {
		PRICED,
		UNPRICED
	}

	record EntryOutcome(
			String outcomeId,
			String signalId,
			String horizon,
			PricingStatus status,
			Optional<String> missingPriceReason,
			Optional<MarketObservation> entryPrice,
			Optional<MarketObservation> horizonPrice,
			Optional<BigDecimal> grossReturn,
			Optional<BigDecimal> friction,
			Optional<BigDecimal> netReturn) {
		public EntryOutcome {
			missingPriceReason = Optional.ofNullable(missingPriceReason).orElseGet(Optional::empty);
			entryPrice = Optional.ofNullable(entryPrice).orElseGet(Optional::empty);
			horizonPrice = Optional.ofNullable(horizonPrice).orElseGet(Optional::empty);
			grossReturn = Optional.ofNullable(grossReturn).orElseGet(Optional::empty);
			friction = Optional.ofNullable(friction).orElseGet(Optional::empty);
			netReturn = Optional.ofNullable(netReturn).orElseGet(Optional::empty);
		}
	}

	record EvaluationReport(
			String runId,
			String reportFingerprint,
			RunProvenance provenance,
			String family,
			int signalCount,
			int pricedOutcomeCount,
			int unpricedOutcomeCount,
			Optional<BigDecimal> averageNetReturn,
			List<EntryOutcome> outcomes) {
		public EvaluationReport {
			averageNetReturn = Optional.ofNullable(averageNetReturn).orElseGet(Optional::empty);
			outcomes = List.copyOf(outcomes);
		}
	}
}
