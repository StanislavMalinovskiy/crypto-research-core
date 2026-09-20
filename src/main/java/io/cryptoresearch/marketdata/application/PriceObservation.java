package io.cryptoresearch.marketdata.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;

public record PriceObservation(
		NormalizedSwapIdentity source,
		AssetId asset,
		AssetId quoteAsset,
		String venue,
		BigDecimal price,
		BigDecimal tradeNotionalQuote,
		BlockPosition blockPosition,
		Instant observedAt,
		BigDecimal confidence,
		String provider,
		Optional<Instant> sourceEventTime) {

	public PriceObservation {
		source = Objects.requireNonNull(source, "source must not be null");
		asset = Objects.requireNonNull(asset, "asset must not be null");
		quoteAsset = Objects.requireNonNull(quoteAsset, "quoteAsset must not be null");
		venue = RawObservationValues.requireOpaque(venue, "venue", RawObservationValues.MAX_VENUE_UTF8_BYTES);
		price = Objects.requireNonNull(price, "price must not be null");
		if (price.signum() <= 0) {
			throw new IllegalArgumentException("price must be positive");
		}
		price = RawObservationValues.requireScale(price, "price", 18);
		tradeNotionalQuote = Objects.requireNonNull(tradeNotionalQuote, "tradeNotionalQuote must not be null");
		if (tradeNotionalQuote.signum() < 0) {
			throw new IllegalArgumentException("tradeNotionalQuote must not be negative");
		}
		tradeNotionalQuote = RawObservationValues.requireScale(tradeNotionalQuote, "tradeNotionalQuote", 8);
		blockPosition = Objects.requireNonNull(blockPosition, "blockPosition must not be null");
		observedAt = RawObservationValues.toMicroseconds(observedAt, "observedAt");
		confidence = Objects.requireNonNull(confidence, "confidence must not be null");
		if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("confidence must be between 0 and 1");
		}
		confidence = RawObservationValues.requireScale(confidence, "confidence", 4);
		provider = RawObservationValues.requireOpaque(
				provider, "provider", RawObservationValues.MAX_PROVIDER_UTF8_BYTES);
		sourceEventTime = Objects.requireNonNull(sourceEventTime, "sourceEventTime must not be null")
				.map(value -> RawObservationValues.toMicroseconds(value, "sourceEventTime"));
		if (!asset.chain().equals(source.chain())) {
			throw new IllegalArgumentException("asset chain must match source chain");
		}
		if (!quoteAsset.chain().equals(source.chain())) {
			throw new IllegalArgumentException("quoteAsset chain must match source chain");
		}
		if (!blockPosition.chain().equals(source.chain())) {
			throw new IllegalArgumentException("blockPosition chain must match source chain");
		}
	}
}
