package io.cryptoresearch.marketdata.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

final class MutableUtcClock extends Clock {

	private final AtomicReference<Instant> instant;

	MutableUtcClock(Instant instant) {
		this.instant = new AtomicReference<>(Objects.requireNonNull(instant, "instant must not be null"));
	}

	void set(Instant value) {
		instant.set(Objects.requireNonNull(value, "value must not be null"));
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		if (!ZoneOffset.UTC.equals(zone)) {
			throw new IllegalArgumentException("Test clock is fixed to UTC");
		}
		return this;
	}

	@Override
	public Instant instant() {
		return instant.get();
	}
}
