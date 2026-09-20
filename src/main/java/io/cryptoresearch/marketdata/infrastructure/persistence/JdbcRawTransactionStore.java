package io.cryptoresearch.marketdata.infrastructure.persistence;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.marketdata.application.RawTransactionConflictException;
import io.cryptoresearch.marketdata.application.RawTransactionStore;
import io.cryptoresearch.marketdata.application.StoredRawTransaction;

@Repository
public class JdbcRawTransactionStore implements RawTransactionStore {

	private static final int BATCH_CHUNK_SIZE = 1000;

	private static final String COLUMNS = """
			chain_id, transaction_value, provider, payload, payload_hash, observed_block_position,
			observed_block_hash, source_event_time, received_at, admitted_at, ingested_at, parser_version
			""";

	private static final String INSERT = """
			INSERT INTO marketdata.raw_transactions (
			    chain_id, transaction_value, provider, payload, payload_hash, observed_block_position,
			    observed_block_hash, source_event_time, received_at, admitted_at, ingested_at, parser_version
			) VALUES (
			    :chainId, :transactionValue, :provider, :payload, :payloadHash, :blockPosition,
			    :blockHash, :sourceEventTime, :receivedAt, :admittedAt, :ingestedAt, :parserVersion
			)
			ON CONFLICT (chain_id, transaction_value, provider) DO NOTHING
			RETURNING 1
			""";

	private static final String BATCH_INSERT = """
			INSERT INTO marketdata.raw_transactions (
			    chain_id, transaction_value, provider, payload, payload_hash, observed_block_position,
			    observed_block_hash, source_event_time, received_at, admitted_at, ingested_at, parser_version
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (chain_id, transaction_value, provider) DO NOTHING
			""";

	private final JdbcClient jdbcClient;
	private final JdbcTemplate jdbcTemplate;

	public JdbcRawTransactionStore(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
		this.jdbcClient = jdbcClient;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public StoredRawTransaction store(StoredRawTransaction transaction) {
		var inserted = bind(jdbcClient.sql(INSERT), transaction).query(Integer.class).optional().isPresent();
		if (inserted) {
			return transaction;
		}
		var existing = find(transaction.chain(), transaction.transactionId(), transaction.provider())
				.orElseThrow(() -> new IllegalStateException(
						"Raw transaction disappeared after uniqueness resolution"));
		if (!sameEvidence(existing, transaction)) {
			throw new RawTransactionConflictException(transaction);
		}
		return existing;
	}

	@Override
	public List<StoredRawTransaction> storeBatch(List<StoredRawTransaction> batch) {
		var stored = new ArrayList<StoredRawTransaction>(batch.size());
		for (var start = 0; start < batch.size(); start += BATCH_CHUNK_SIZE) {
			var chunk = batch.subList(start, Math.min(start + BATCH_CHUNK_SIZE, batch.size()));
			jdbcTemplate.batchUpdate(BATCH_INSERT, chunk.stream().map(this::bindParameters).toList());
			stored.addAll(verifyChunk(chunk));
		}
		return stored;
	}

	@Override
	public Optional<StoredRawTransaction> find(ChainId chain, TransactionId transactionId, String provider) {
		return jdbcClient.sql("SELECT " + COLUMNS + """
					 FROM marketdata.raw_transactions
					 WHERE chain_id = :chainId AND transaction_value = :transactionValue AND provider = :provider
					""")
				.param("chainId", chain.value())
				.param("transactionValue", transactionId.value())
				.param("provider", provider)
				.query(this::map)
				.optional();
	}

	private List<StoredRawTransaction> verifyChunk(List<StoredRawTransaction> chunk) {
		if (chunk.isEmpty()) {
			return List.of();
		}
		var values = new StringBuilder();
		var statement = jdbcClient.sql("SELECT " + COLUMNS + """
				 FROM marketdata.raw_transactions
				 WHERE (chain_id, transaction_value, provider) IN (VALUES """ + appendValues(chunk, values) + ")"
		);
		for (var index = 0; index < chunk.size(); index++) {
			var transaction = chunk.get(index);
			statement = statement
					.param("chainId" + index, transaction.chain().value())
					.param("transactionValue" + index, transaction.transactionId().value())
					.param("provider" + index, transaction.provider());
		}
		var byIdentity = new HashMap<String, StoredRawTransaction>();
		statement.query(this::map).list().forEach(stored -> byIdentity.put(key(stored), stored));

		var verified = new ArrayList<StoredRawTransaction>(chunk.size());
		for (var transaction : chunk) {
			var existing = Optional.ofNullable(byIdentity.get(key(transaction)))
					.orElseThrow(() -> new IllegalStateException(
							"Raw transaction disappeared after batch uniqueness resolution"));
			if (!sameEvidence(existing, transaction)) {
				throw new RawTransactionConflictException(transaction);
			}
			verified.add(existing);
		}
		return verified;
	}

	private String appendValues(List<StoredRawTransaction> chunk, StringBuilder values) {
		for (var index = 0; index < chunk.size(); index++) {
			if (index > 0) {
				values.append(", ");
			}
			values.append("(:chainId").append(index).append(", :transactionValue").append(index)
					.append(", :provider").append(index).append(')');
		}
		return values.toString();
	}

	private Object[] bindParameters(StoredRawTransaction transaction) {
		return new Object[] {
				transaction.chain().value(),
				transaction.transactionId().value(),
				transaction.provider(),
				transaction.payload(),
				transaction.payloadHash(),
				transaction.blockPosition().value(),
				transaction.blockHash().orElse(null),
				transaction.sourceEventTime().map(this::timestamp).orElse(null),
				timestamp(transaction.receivedAt()),
				timestamp(transaction.admittedAt()),
				timestamp(transaction.ingestedAt()),
				transaction.parserVersion().orElse(null) };
	}

	private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, StoredRawTransaction transaction) {
		return statement
				.param("chainId", transaction.chain().value())
				.param("transactionValue", transaction.transactionId().value())
				.param("provider", transaction.provider())
				.param("payload", transaction.payload())
				.param("payloadHash", transaction.payloadHash())
				.param("blockPosition", transaction.blockPosition().value())
				.param("blockHash", transaction.blockHash().orElse(null), Types.VARCHAR)
				.param("sourceEventTime", transaction.sourceEventTime().map(this::timestamp).orElse(null),
						Types.TIMESTAMP_WITH_TIMEZONE)
				.param("receivedAt", timestamp(transaction.receivedAt()))
				.param("admittedAt", timestamp(transaction.admittedAt()))
				.param("ingestedAt", timestamp(transaction.ingestedAt()))
				.param("parserVersion", transaction.parserVersion().orElse(null), Types.VARCHAR);
	}

	private StoredRawTransaction map(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		var chain = new ChainId(resultSet.getString("chain_id"));
		return new StoredRawTransaction(
				chain,
				new TransactionId(chain, resultSet.getString("transaction_value")),
				resultSet.getString("provider"),
				new io.cryptoresearch.kernel.api.BlockPosition(chain, resultSet.getLong("observed_block_position")),
				Optional.ofNullable(resultSet.getString("observed_block_hash")),
				Optional.ofNullable(resultSet.getObject("source_event_time", OffsetDateTime.class))
						.map(OffsetDateTime::toInstant),
				resultSet.getObject("received_at", OffsetDateTime.class).toInstant(),
				resultSet.getObject("admitted_at", OffsetDateTime.class).toInstant(),
				resultSet.getString("payload"),
				resultSet.getString("payload_hash"),
				Optional.ofNullable(resultSet.getString("parser_version")),
				resultSet.getObject("ingested_at", OffsetDateTime.class).toInstant());
	}

	private boolean sameEvidence(StoredRawTransaction first, StoredRawTransaction second) {
		return first.chain().equals(second.chain())
				&& first.transactionId().equals(second.transactionId())
				&& first.provider().equals(second.provider())
				&& first.payloadHash().equals(second.payloadHash())
				&& first.blockPosition().equals(second.blockPosition())
				&& first.blockHash().equals(second.blockHash())
				&& first.sourceEventTime().equals(second.sourceEventTime())
				&& first.receivedAt().equals(second.receivedAt())
				&& first.admittedAt().equals(second.admittedAt())
				&& first.parserVersion().equals(second.parserVersion());
	}

	private String key(StoredRawTransaction transaction) {
		return transaction.chain().value() + "/" + transaction.transactionId().value() + "/"
				+ transaction.provider();
	}

	private OffsetDateTime timestamp(java.time.Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
