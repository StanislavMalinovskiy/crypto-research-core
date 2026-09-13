package io.cryptoresearch.kernel.api;

import java.util.Objects;

/** An event identity scoped to a transaction by a canonical opaque locator. */
public record EventId(TransactionId transactionId, String locator) implements Comparable<EventId> {

	public EventId {
		transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
		locator = IdentityValues.requireOpaque(locator, "event locator");
	}

	@Override
	public int compareTo(EventId other) {
		var transactionComparison = transactionId.compareTo(other.transactionId);
		return transactionComparison != 0 ? transactionComparison : locator.compareTo(other.locator);
	}
}
