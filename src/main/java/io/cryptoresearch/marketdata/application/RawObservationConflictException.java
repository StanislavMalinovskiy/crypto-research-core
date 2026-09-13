package io.cryptoresearch.marketdata.application;

public final class RawObservationConflictException extends RuntimeException {

	public RawObservationConflictException(StoredRawChainEvent observation) {
		super("Conflicting immutable evidence for raw observation "
				+ observation.chain().value() + "/"
				+ observation.transactionId().value() + "/"
				+ observation.eventId().locator() + "/"
				+ observation.provider());
	}
}
