package io.cryptoresearch.marketdata.application;

import java.util.List;
import java.util.Optional;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;

public interface MarketFactStore {

	PriceObservation storePrice(PriceObservation observation);

	Optional<PriceObservation> findPrice(NormalizedSwapIdentity source);

	List<PriceObservation> priceObservations(ObservationQuery query);

	LiquidityObservation storeLiquidity(LiquidityObservation observation);

	Optional<LiquidityObservation> findLiquidity(NormalizedSwapIdentity source, AssetId asset, String poolAddress);

	List<LiquidityObservation> liquidityObservations(ObservationQuery query);

	UsdConversionFact storeUsdConversion(UsdConversionFact fact);

	Optional<UsdConversionFact> findUsdConversion(NormalizedSwapIdentity source);
}
