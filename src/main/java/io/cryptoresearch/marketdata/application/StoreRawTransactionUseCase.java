package io.cryptoresearch.marketdata.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreRawTransactionUseCase {

	private final RawTransactionStore store;
	private final Clock clock;

	public StoreRawTransactionUseCase(RawTransactionStore store, Clock clock) {
		this.store = Objects.requireNonNull(store, "store must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
	}

	@Transactional
	public StoredRawTransaction store(RawTransactionPayload payload) {
		Objects.requireNonNull(payload, "payload must not be null");
		return store.store(toStored(payload));
	}

	@Transactional
	public List<StoredRawTransaction> storeBatch(List<RawTransactionPayload> payloads) {
		Objects.requireNonNull(payloads, "payloads must not be null");
		return store.storeBatch(payloads.stream().map(this::toStored).toList());
	}

	private StoredRawTransaction toStored(RawTransactionPayload payload) {
		return new StoredRawTransaction(
				payload.chain(),
				payload.transactionId(),
				payload.provider(),
				payload.blockPosition(),
				payload.blockHash(),
				payload.sourceEventTime(),
				payload.receivedAt(),
				payload.admittedAt(),
				payload.payload(),
				PayloadFingerprint.sha256(payload.payload()),
				payload.parserVersion(),
				RawObservationValues.toMicroseconds(clock.instant(), "clock instant"));
	}
}
