package io.cryptoresearch.marketdata.application;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;

public record LiquidityObservation(
		NormalizedSwapIdentity source,
		AssetId asset,
		String poolAddress,
		AssetId quoteAsset,
		BigDecimal liquidityUsd,
		BigInteger baseReserve,
		BigInteger quoteReserve,
		BlockPosition blockPosition,
		Instant observedAt,
		BigDecimal confidence,
		String provider,
		Optional<Instant> sourceEventTime) {

	public LiquidityObservation {
		source = Objects.requireNonNull(source, "source must not be null");
		asset = Objects.requireNonNull(asset, "asset must not be null");
		poolAddress = RawObservationValues.requireOpaque(
				poolAddress, "poolAddress", RawObservationValues.MAX_POOL_ADDRESS_UTF8_BYTES);
		quoteAsset = Objects.requireNonNull(quoteAsset, "quoteAsset must not be null");
		liquidityUsd = Objects.requireNonNull(liquidityUsd, "liquidityUsd must not be null");
		if (liquidityUsd.signum() < 0) {
			throw new IllegalArgumentException("liquidityUsd must not be negative");
		}
		liquidityUsd = RawObservationValues.requireScale(liquidityUsd, "liquidityUsd", 8);
		baseReserve = Objects.requireNonNull(baseReserve, "baseReserve must not be null");
		quoteReserve = Objects.requireNonNull(quoteReserve, "quoteReserve must not be null");
		if (baseReserve.signum() < 0 || quoteReserve.signum() < 0) {
			throw new IllegalArgumentException("reserves must not be negative");
		}
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
