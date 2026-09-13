package io.cryptoresearch.marketdata.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.marketdata.application.RawChainEventStore;
import io.cryptoresearch.marketdata.application.RawObservationConflictException;
import io.cryptoresearch.marketdata.application.StoreRawObservationOutcome;
import io.cryptoresearch.marketdata.application.StoredRawChainEvent;

@Repository
public class JdbcRawChainEventStore implements RawChainEventStore {

	private static final String INSERT = """
			INSERT INTO marketdata.raw_chain_events (
			    chain_id, transaction_value, event_locator, provider,
			    observed_block_position, observed_block_hash,
			    source_event_time, observed_at, payload, payload_hash, parser_version, ingested_at
			) VALUES (
			    :chainId, :transactionValue, :eventLocator, :provider,
			    :observedBlockPosition, :observedBlockHash,
			    :sourceEventTime, :observedAt, :payload, :payloadHash, :parserVersion, :ingestedAt
			)
			ON CONFLICT (chain_id, transaction_value, event_locator, provider) DO NOTHING
			RETURNING 1
			""";

	private static final String FIND_BY_IDENTITY = """
			SELECT chain_id, transaction_value, event_locator, provider,
			       observed_block_position, observed_block_hash,
			       source_event_time, observed_at, payload, payload_hash, parser_version, ingested_at
			FROM marketdata.raw_chain_events
			WHERE chain_id = :chainId
			  AND transaction_value = :transactionValue
			  AND event_locator = :eventLocator
			  AND provider = :provider
			""";

	private final JdbcClient jdbcClient;

	public JdbcRawChainEventStore(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public StoreRawObservationOutcome store(StoredRawChainEvent observation) {
		var inserted = bindEvidence(jdbcClient.sql(INSERT), observation)
				.query(Integer.class)
				.optional()
				.isPresent();
		if (inserted) {
			return StoreRawObservationOutcome.INSERTED;
		}

		var existing = bindIdentity(jdbcClient.sql(FIND_BY_IDENTITY), observation)
				.query(this::map)
				.optional()
				.orElseThrow(() -> new IllegalStateException(
						"Conflicting raw observation was not visible after PostgreSQL uniqueness resolution"));
		if (!existing.hasSameImmutableEvidence(observation)) {
			throw new RawObservationConflictException(observation);
		}
		return StoreRawObservationOutcome.ALREADY_PRESENT;
	}

	private JdbcClient.StatementSpec bindEvidence(
			JdbcClient.StatementSpec statement, StoredRawChainEvent observation) {
		return bindIdentity(statement, observation)
				.param("observedBlockPosition", observation.blockPosition().value())
				.param("observedBlockHash", observation.blockHash().orElse(null), Types.VARCHAR)
				.param("sourceEventTime", offsetDateTime(observation.sourceEventTime()), Types.TIMESTAMP_WITH_TIMEZONE)
				.param("observedAt", offsetDateTime(observation.observedAt()))
				.param("payload", observation.payload())
				.param("payloadHash", observation.payloadHash())
				.param("parserVersion", observation.parserVersion())
				.param("ingestedAt", offsetDateTime(observation.ingestedAt()));
	}

	private JdbcClient.StatementSpec bindIdentity(
			JdbcClient.StatementSpec statement, StoredRawChainEvent observation) {
		return statement
				.param("chainId", observation.chain().value())
				.param("transactionValue", observation.transactionId().value())
				.param("eventLocator", observation.eventId().locator())
				.param("provider", observation.provider());
	}

	private StoredRawChainEvent map(ResultSet resultSet, int rowNumber) throws SQLException {
		var chain = new ChainId(resultSet.getString("chain_id"));
		var transactionId = new TransactionId(chain, resultSet.getString("transaction_value"));
		return new StoredRawChainEvent(
				chain,
				transactionId,
				new EventId(transactionId, resultSet.getString("event_locator")),
				resultSet.getString("provider"),
				new BlockPosition(chain, resultSet.getLong("observed_block_position")),
				Optional.ofNullable(resultSet.getString("observed_block_hash")),
				readInstant(resultSet, "source_event_time"),
				resultSet.getObject("observed_at", OffsetDateTime.class).toInstant(),
				resultSet.getString("payload"),
				resultSet.getString("payload_hash"),
				resultSet.getString("parser_version"),
				resultSet.getObject("ingested_at", OffsetDateTime.class).toInstant());
	}

	private Optional<java.time.Instant> readInstant(ResultSet resultSet, String column) throws SQLException {
		return Optional.ofNullable(resultSet.getObject(column, OffsetDateTime.class)).map(OffsetDateTime::toInstant);
	}

	private OffsetDateTime offsetDateTime(java.time.Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private OffsetDateTime offsetDateTime(Optional<java.time.Instant> instant) {
		return instant.map(this::offsetDateTime).orElse(null);
	}
}
