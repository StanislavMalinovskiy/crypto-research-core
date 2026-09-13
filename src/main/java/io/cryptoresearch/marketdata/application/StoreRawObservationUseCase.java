package io.cryptoresearch.marketdata.application;

import java.time.Clock;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreRawObservationUseCase {

	private final RawChainEventStore store;
	private final Clock clock;

	public StoreRawObservationUseCase(RawChainEventStore store, Clock clock) {
		this.store = Objects.requireNonNull(store, "store must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
	}

	@Transactional
	public StoreRawObservationOutcome store(RawChainObservation observation) {
		Objects.requireNonNull(observation, "observation must not be null");
		var stored = new StoredRawChainEvent(
				observation.chain(),
				observation.transactionId(),
				observation.eventId(),
				observation.provider(),
				observation.blockPosition(),
				observation.blockHash(),
				observation.sourceEventTime(),
				observation.observedAt(),
				observation.payload(),
				PayloadFingerprint.sha256(observation.payload()),
				observation.parserVersion(),
				RawObservationValues.toMicroseconds(clock.instant(), "clock instant"));
		return store.store(stored);
	}
}
