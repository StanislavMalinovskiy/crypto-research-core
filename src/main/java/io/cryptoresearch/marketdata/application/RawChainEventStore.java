package io.cryptoresearch.marketdata.application;

@FunctionalInterface
public interface RawChainEventStore {

	StoreRawObservationOutcome store(StoredRawChainEvent observation);
}
