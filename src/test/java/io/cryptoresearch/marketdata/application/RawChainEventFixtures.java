package io.cryptoresearch.marketdata.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;

import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;

final class RawChainEventFixtures {

	static final Instant FIRST_INGESTION = Instant.parse("2026-09-13T03:00:00.111222333Z");
	static final Instant SECOND_INGESTION = Instant.parse("2026-09-13T04:00:00.444555666Z");
	static final Instant OBSERVED_AT = Instant.parse("2026-09-13T01:00:00.123456789Z");
	static final Instant SOURCE_TIME = Instant.parse("2026-09-13T00:59:59.987654321Z");
	static final String PAYLOAD = "{\"event\":1}";

	private RawChainEventFixtures() {
	}

	static RawChainObservation observation(
			String provider,
			long blockPosition,
			Optional<String> blockHash,
			Optional<Instant> sourceEventTime,
			Instant observedAt,
			String payload,
			String parserVersion) {
		return observation("tx-1", "event-1", provider, blockPosition, blockHash,
				sourceEventTime, observedAt, payload, parserVersion);
	}

	static RawChainObservation observation(
			String transactionValue,
			String eventLocator,
			String provider,
			long blockPosition,
			Optional<String> blockHash,
			Optional<Instant> sourceEventTime,
			Instant observedAt,
			String payload,
			String parserVersion) {
		return observation(ChainId.SOLANA_MAINNET, transactionValue, eventLocator, provider,
				blockPosition, blockHash, sourceEventTime, observedAt, payload, parserVersion);
	}

	static RawChainObservation observation(
			ChainId chain,
			String transactionValue,
			String eventLocator,
			String provider,
			long blockPosition,
			Optional<String> blockHash,
			Optional<Instant> sourceEventTime,
			Instant observedAt,
			String payload,
			String parserVersion) {
		var transactionId = new TransactionId(chain, transactionValue);
		return new RawChainObservation(
				chain,
				transactionId,
				new EventId(transactionId, eventLocator),
				provider,
				new BlockPosition(chain, blockPosition),
				blockHash,
				sourceEventTime,
				observedAt,
				payload,
				parserVersion);
	}

	static StoredRow readSingleRow(JdbcClient jdbcClient) {
		return jdbcClient.sql("""
				SELECT chain_id, transaction_value, event_locator, provider,
				       observed_block_position, observed_block_hash,
				       source_event_time, observed_at, payload, payload_hash, parser_version, ingested_at
				FROM marketdata.raw_chain_events
				""").query((resultSet, rowNumber) -> new StoredRow(
				resultSet.getString("chain_id"),
				resultSet.getString("transaction_value"),
				resultSet.getString("event_locator"),
				resultSet.getString("provider"),
				resultSet.getLong("observed_block_position"),
				resultSet.getString("observed_block_hash"),
				instant(resultSet.getObject("source_event_time", OffsetDateTime.class)),
				instant(resultSet.getObject("observed_at", OffsetDateTime.class)),
				resultSet.getString("payload"),
				resultSet.getString("payload_hash"),
				resultSet.getString("parser_version"),
				instant(resultSet.getObject("ingested_at", OffsetDateTime.class))))
				.single();
	}

	static int rowCount(JdbcClient jdbcClient) {
		return jdbcClient.sql("SELECT count(*) FROM marketdata.raw_chain_events")
				.query(Integer.class)
				.single();
	}

	private static Instant instant(OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}

	record StoredRow(
			String chainId,
			String transactionValue,
			String eventLocator,
			String provider,
			long observedBlockPosition,
			String observedBlockHash,
			Instant sourceEventTime,
			Instant observedAt,
			String payload,
			String payloadHash,
			String parserVersion,
			Instant ingestedAt) {
	}
}
