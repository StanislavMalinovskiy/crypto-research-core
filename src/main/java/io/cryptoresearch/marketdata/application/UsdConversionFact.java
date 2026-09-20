package io.cryptoresearch.marketdata.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;

public record UsdConversionFact(
		NormalizedSwapIdentity source,
		AssetId asset,
		NormalizedSwapIdentity usdQuoteSource,
		BigDecimal priceUsd,
		String methodVersion,
		Instant computedAt) {

	public UsdConversionFact {
		source = Objects.requireNonNull(source, "source must not be null");
		asset = Objects.requireNonNull(asset, "asset must not be null");
		usdQuoteSource = Objects.requireNonNull(usdQuoteSource, "usdQuoteSource must not be null");
		priceUsd = Objects.requireNonNull(priceUsd, "priceUsd must not be null");
		if (priceUsd.signum() <= 0) {
			throw new IllegalArgumentException("priceUsd must be positive");
		}
		priceUsd = RawObservationValues.requireScale(priceUsd, "priceUsd", 18);
		methodVersion = RawObservationValues.requireOpaque(
				methodVersion, "methodVersion", RawObservationValues.MAX_PARSER_VERSION_UTF8_BYTES);
		computedAt = RawObservationValues.toMicroseconds(computedAt, "computedAt");
		if (!asset.chain().equals(source.chain())) {
			throw new IllegalArgumentException("asset chain must match source chain");
		}
		if (!usdQuoteSource.chain().equals(source.chain())) {
			throw new IllegalArgumentException("usdQuoteSource chain must match source chain");
		}
	}
}
