package io.cryptoresearch.marketdata.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;

public record RawChainObservation(
		ChainId chain,
		TransactionId transactionId,
		EventId eventId,
		String provider,
		BlockPosition blockPosition,
		Optional<String> blockHash,
		Optional<Instant> sourceEventTime,
		Instant observedAt,
		String payload,
		String parserVersion) {

	public RawChainObservation {
		chain = Objects.requireNonNull(chain, "chain must not be null");
		transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
		eventId = Objects.requireNonNull(eventId, "eventId must not be null");
		blockPosition = Objects.requireNonNull(blockPosition, "blockPosition must not be null");

		RawObservationValues.requireOpaque(
				chain.value(), "chainId", RawObservationValues.MAX_CHAIN_ID_UTF8_BYTES);
		RawObservationValues.requireOpaque(
				transactionId.value(), "transactionValue", RawObservationValues.MAX_TRANSACTION_VALUE_UTF8_BYTES);
		RawObservationValues.requireOpaque(
				eventId.locator(), "eventLocator", RawObservationValues.MAX_EVENT_LOCATOR_UTF8_BYTES);
		provider = RawObservationValues.requireOpaque(provider, "provider", RawObservationValues.MAX_PROVIDER_UTF8_BYTES);
		parserVersion = RawObservationValues.requireOpaque(
				parserVersion, "parserVersion", RawObservationValues.MAX_PARSER_VERSION_UTF8_BYTES);

		blockHash = Objects.requireNonNull(blockHash, "blockHash must not be null")
				.map(value -> RawObservationValues.requireOpaque(
						value, "observedBlockHash", RawObservationValues.MAX_OBSERVED_BLOCK_HASH_UTF8_BYTES));
		sourceEventTime = Objects.requireNonNull(sourceEventTime, "sourceEventTime must not be null")
				.map(value -> RawObservationValues.toMicroseconds(value, "sourceEventTime"));
		observedAt = RawObservationValues.toMicroseconds(observedAt, "observedAt");
		payload = RawObservationValues.requirePayload(payload);

		if (!transactionId.chain().equals(chain)) {
			throw new IllegalArgumentException("transactionId chain must match observation chain");
		}
		if (!eventId.transactionId().equals(transactionId)) {
			throw new IllegalArgumentException("eventId transaction must match observation transactionId");
		}
		if (!blockPosition.chain().equals(chain)) {
			throw new IllegalArgumentException("blockPosition chain must match observation chain");
		}
	}
}
