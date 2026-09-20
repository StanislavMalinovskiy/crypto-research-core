package io.cryptoresearch.marketdata.application;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;

@Service
public class RecordMarketFactUseCase {

	private final MarketFactStore store;

	public RecordMarketFactUseCase(MarketFactStore store) {
		this.store = Objects.requireNonNull(store, "store must not be null");
	}

	@Transactional
	public PriceObservation storePrice(PriceObservation observation) {
		Objects.requireNonNull(observation, "observation must not be null");
		return store.storePrice(observation);
	}

	@Transactional
	public LiquidityObservation storeLiquidity(LiquidityObservation observation) {
		Objects.requireNonNull(observation, "observation must not be null");
		return store.storeLiquidity(observation);
	}

	@Transactional
	public UsdConversionFact storeUsdConversion(UsdConversionFact fact) {
		Objects.requireNonNull(fact, "fact must not be null");
		return store.storeUsdConversion(fact);
	}

	public List<PriceObservation> priceObservations(ObservationQuery query) {
		return store.priceObservations(Objects.requireNonNull(query, "query must not be null"));
	}

	public List<LiquidityObservation> liquidityObservations(ObservationQuery query) {
		return store.liquidityObservations(Objects.requireNonNull(query, "query must not be null"));
	}

	public java.util.Optional<PriceObservation> findPrice(NormalizedSwapIdentity source) {
		return store.findPrice(source);
	}

	public java.util.Optional<LiquidityObservation> findLiquidity(
			NormalizedSwapIdentity source, AssetId asset, String poolAddress) {
		return store.findLiquidity(source, asset, poolAddress);
	}

	public java.util.Optional<UsdConversionFact> findUsdConversion(NormalizedSwapIdentity source) {
		return store.findUsdConversion(source);
	}
}
