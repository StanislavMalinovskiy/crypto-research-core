package io.cryptoresearch.marketdata.application;

import java.time.Instant;
import java.util.Objects;

import io.cryptoresearch.kernel.api.AssetId;

public record ObservationQuery(
		AssetId asset,
		Instant fromInclusive,
		Instant toInclusive,
		Instant cutoff) {

	public ObservationQuery {
		asset = Objects.requireNonNull(asset, "asset must not be null");
		fromInclusive = RawObservationValues.toMicroseconds(fromInclusive, "fromInclusive");
		toInclusive = RawObservationValues.toMicroseconds(toInclusive, "toInclusive");
		cutoff = RawObservationValues.toMicroseconds(cutoff, "cutoff");
		if (fromInclusive.isAfter(toInclusive)) {
			throw new IllegalArgumentException("fromInclusive must not be after toInclusive");
		}
		if (toInclusive.isAfter(cutoff)) {
			throw new IllegalArgumentException("toInclusive must not be after cutoff");
		}
	}
}
