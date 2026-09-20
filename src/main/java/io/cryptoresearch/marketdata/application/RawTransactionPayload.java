package io.cryptoresearch.marketdata.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.TransactionId;

public record RawTransactionPayload(
		ChainId chain,
		TransactionId transactionId,
		String provider,
		BlockPosition blockPosition,
		Optional<String> blockHash,
		Optional<Instant> sourceEventTime,
		Instant receivedAt,
		Instant admittedAt,
		String payload,
		Optional<String> parserVersion) {

	public RawTransactionPayload {
		chain = Objects.requireNonNull(chain, "chain must not be null");
		transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
		blockPosition = Objects.requireNonNull(blockPosition, "blockPosition must not be null");
		provider = RawObservationValues.requireOpaque(
				provider, "provider", RawObservationValues.MAX_PROVIDER_UTF8_BYTES);
		blockHash = Objects.requireNonNull(blockHash, "blockHash must not be null")
				.map(value -> RawObservationValues.requireOpaque(
						value, "observedBlockHash", RawObservationValues.MAX_OBSERVED_BLOCK_HASH_UTF8_BYTES));
		sourceEventTime = Objects.requireNonNull(sourceEventTime, "sourceEventTime must not be null")
				.map(value -> RawObservationValues.toMicroseconds(value, "sourceEventTime"));
		receivedAt = RawObservationValues.toMicroseconds(receivedAt, "receivedAt");
		admittedAt = RawObservationValues.toMicroseconds(admittedAt, "admittedAt");
		payload = RawObservationValues.requirePayload(payload);
		parserVersion = Objects.requireNonNull(parserVersion, "parserVersion must not be null")
				.map(value -> RawObservationValues.requireOpaque(
						value, "parserVersion", RawObservationValues.MAX_PARSER_VERSION_UTF8_BYTES));
		if (!transactionId.chain().equals(chain)) {
			throw new IllegalArgumentException("transactionId chain must match payload chain");
		}
		if (!blockPosition.chain().equals(chain)) {
			throw new IllegalArgumentException("blockPosition chain must match payload chain");
		}
		if (receivedAt.isAfter(admittedAt)) {
			throw new IllegalArgumentException("receivedAt must not be after admittedAt");
		}
	}
}
