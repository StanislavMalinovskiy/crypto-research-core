package io.cryptoresearch.marketdata.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;

public record StoredRawChainEvent(
		ChainId chain,
		TransactionId transactionId,
		EventId eventId,
		String provider,
		BlockPosition blockPosition,
		Optional<String> blockHash,
		Optional<Instant> sourceEventTime,
		Instant observedAt,
		String payload,
		String payloadHash,
		String parserVersion,
		Instant ingestedAt) {

	public StoredRawChainEvent {
		var validated = new RawChainObservation(
				chain, transactionId, eventId, provider, blockPosition, blockHash,
				sourceEventTime, observedAt, payload, parserVersion);
		chain = validated.chain();
		transactionId = validated.transactionId();
		eventId = validated.eventId();
		provider = validated.provider();
		blockPosition = validated.blockPosition();
		blockHash = validated.blockHash();
		sourceEventTime = validated.sourceEventTime();
		observedAt = validated.observedAt();
		payload = validated.payload();
		parserVersion = validated.parserVersion();
		payloadHash = RawObservationValues.requirePayloadHash(payloadHash);
		ingestedAt = RawObservationValues.toMicroseconds(ingestedAt, "ingestedAt");

		if (!payloadHash.equals(PayloadFingerprint.sha256(payload))) {
			throw new IllegalArgumentException("payloadHash must match the exact payload bytes");
		}
	}

	public boolean hasSameImmutableEvidence(StoredRawChainEvent other) {
		Objects.requireNonNull(other, "other must not be null");
		return blockPosition.equals(other.blockPosition)
				&& blockHash.equals(other.blockHash)
				&& sourceEventTime.equals(other.sourceEventTime)
				&& payload.equals(other.payload)
				&& payloadHash.equals(other.payloadHash)
				&& parserVersion.equals(other.parserVersion);
	}
}
