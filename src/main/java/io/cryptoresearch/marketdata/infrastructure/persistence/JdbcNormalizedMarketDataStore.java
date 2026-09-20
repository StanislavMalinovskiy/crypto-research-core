package io.cryptoresearch.marketdata.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.kernel.api.WalletAddress;
import io.cryptoresearch.marketdata.api.MarketDataApi.DatasetSnapshot;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.api.MarketDataApi.PointInTimeQuery;
import io.cryptoresearch.marketdata.api.MarketDataApi.TradeSide;
import io.cryptoresearch.marketdata.application.NormalizationConflictException;
import io.cryptoresearch.marketdata.application.NormalizedMarketDataStore;

@Repository
public class JdbcNormalizedMarketDataStore implements NormalizedMarketDataStore {

	private static final String COLUMNS = """
			chain_id, transaction_value, event_locator, asset_address, wallet_address, side,
			token_quantity, native_quantity, price_usd, liquidity_usd, confidence, venue,
			observed_block_position, observed_block_hash, source_event_time, observed_at,
			provider, raw_payload_hash, transformation_version
			""";

	private static final String INSERT = """
			INSERT INTO marketdata.normalized_swaps (
			    chain_id, transaction_value, event_locator, asset_address, wallet_address, side,
			    token_quantity, native_quantity, price_usd, liquidity_usd, confidence, venue,
			    observed_block_position, observed_block_hash, source_event_time, observed_at,
			    provider, raw_payload_hash, transformation_version
			) VALUES (
			    :chainId, :transactionValue, :eventLocator, :asset, :wallet, :side,
			    :tokenQuantity, :nativeQuantity, :priceUsd, :liquidityUsd, :confidence, :venue,
			    :blockPosition, :blockHash, :sourceEventTime, :observedAt,
			    :provider, :rawPayloadHash, :transformationVersion
			)
			ON CONFLICT (chain_id, transaction_value, event_locator) DO NOTHING
			RETURNING 1
			""";

	private static final String FIND = "SELECT " + COLUMNS + """
			 FROM marketdata.normalized_swaps
			 WHERE chain_id = :chainId AND transaction_value = :transactionValue AND event_locator = :eventLocator
			""";

	private static final String QUERY_BASE = "SELECT " + COLUMNS + """
			 FROM marketdata.normalized_swaps normalized
			 WHERE normalized.chain_id = :chainId
			   AND normalized.asset_address = :asset
			   AND normalized.observed_at >= :fromInclusive
			   AND normalized.observed_at <= :toInclusive
			   AND normalized.observed_at <= :cutoff
			""";

	private static final String QUERY_MEMBER = """
			   AND EXISTS (
			       SELECT 1 FROM marketdata.dataset_snapshots snapshot
			       JOIN marketdata.dataset_snapshot_members member ON member.snapshot_id = snapshot.snapshot_id
			       WHERE snapshot.fingerprint = :datasetFingerprint
			         AND member.chain_id = normalized.chain_id
			         AND member.transaction_value = normalized.transaction_value
			         AND member.event_locator = normalized.event_locator
			   )
			""";

	private static final String QUERY_ORDER = """
			 ORDER BY normalized.observed_at, normalized.chain_id COLLATE "C",
			          normalized.transaction_value COLLATE "C", normalized.event_locator COLLATE "C"
			""";

	private static final String QUERY_ORDER_PLAIN = """
			 ORDER BY observed_at, chain_id COLLATE "C", transaction_value COLLATE "C", event_locator COLLATE "C"
			""";

	private final JdbcClient jdbcClient;
	private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	private static final int BATCH_CHUNK_SIZE = 1000;

	public JdbcNormalizedMarketDataStore(
			JdbcClient jdbcClient,
			org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
		this.jdbcClient = jdbcClient;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public MarketObservation store(MarketObservation observation) {
		var inserted = bind(jdbcClient.sql(INSERT), observation).query(Integer.class).optional().isPresent();
		if (inserted) {
			return observation;
		}
		var existing = find(observation.identity()).orElseThrow(() ->
				new IllegalStateException("Normalized row disappeared after uniqueness resolution"));
		if (!sameEvidence(existing, observation)) {
			throw new NormalizationConflictException(observation.identity());
		}
		return existing;
	}

	@Override
	public Optional<MarketObservation> find(NormalizedSwapIdentity identity) {
		return bindIdentity(jdbcClient.sql(FIND), identity).query(this::map).optional();
	}

	@Override
	public List<MarketObservation> observations(PointInTimeQuery query) {
		var sql = QUERY_BASE + (query.datasetFingerprint().isPresent() ? QUERY_MEMBER : "") + QUERY_ORDER;
		var statement = jdbcClient.sql(sql)
				.param("chainId", query.asset().chain().value())
				.param("asset", query.asset().value())
				.param("fromInclusive", timestamp(query.fromInclusive()))
				.param("toInclusive", timestamp(query.toInclusive()))
				.param("cutoff", timestamp(query.cutoff()));
		if (query.datasetFingerprint().isPresent()) {
			statement = statement.param("datasetFingerprint", query.datasetFingerprint().orElseThrow());
		}
		return statement.query(this::map).list();
	}

	@Override
	public List<MarketObservation> findAll(List<NormalizedSwapIdentity> identities) {
		if (identities.isEmpty()) {
			return List.of();
		}
		var found = new ArrayList<MarketObservation>(identities.size());
		for (var start = 0; start < identities.size(); start += BATCH_CHUNK_SIZE) {
			var chunk = identities.subList(start, Math.min(start + BATCH_CHUNK_SIZE, identities.size()));
			var values = new StringBuilder();
			var statement = jdbcClient.sql("SELECT " + COLUMNS + """
						 FROM marketdata.normalized_swaps
						 WHERE (chain_id, transaction_value, event_locator) IN (VALUES """
					+ appendIdentityValues(chunk, values) + ")"
					+ QUERY_ORDER_PLAIN);
			for (var index = 0; index < chunk.size(); index++) {
				var identity = chunk.get(index);
				statement = bindIdentity(statement, identity, index);
			}
			found.addAll(statement.query(this::map).list());
		}
		return found;
	}

	private String appendIdentityValues(List<NormalizedSwapIdentity> chunk, StringBuilder values) {
		for (var index = 0; index < chunk.size(); index++) {
			if (index > 0) {
				values.append(", ");
			}
			values.append("(:chainId").append(index).append(", :transactionValue").append(index)
					.append(", :eventLocator").append(index).append(')');
		}
		return values.toString();
	}

	private JdbcClient.StatementSpec bindIdentity(
			JdbcClient.StatementSpec statement, NormalizedSwapIdentity identity, int index) {
		return statement
				.param("chainId" + index, identity.chain().value())
				.param("transactionValue" + index, identity.transactionId().value())
				.param("eventLocator" + index, identity.eventId().locator());
	}

	@Override
	public DatasetSnapshot storeSnapshot(DatasetSnapshot snapshot) {
		var inserted = jdbcClient.sql("""
				INSERT INTO marketdata.dataset_snapshots (
				    snapshot_id, fingerprint, canonicalization_version, cutoff
				) VALUES (:snapshotId, :fingerprint, :canonicalizationVersion, :cutoff)
				ON CONFLICT (snapshot_id) DO NOTHING
				RETURNING 1
				""")
				.param("snapshotId", snapshot.snapshotId())
				.param("fingerprint", snapshot.fingerprint())
				.param("canonicalizationVersion", snapshot.canonicalizationVersion())
				.param("cutoff", timestamp(snapshot.cutoff()))
				.query(Integer.class).optional().isPresent();
		if (inserted) {
			for (var start = 0; start < snapshot.observations().size(); start += BATCH_CHUNK_SIZE) {
				var chunk = snapshot.observations().subList(
						start, Math.min(start + BATCH_CHUNK_SIZE, snapshot.observations().size()));
				var parameters = new java.util.ArrayList<Object[]>(chunk.size());
				for (var index = 0; index < chunk.size(); index++) {
					var identity = chunk.get(index).identity();
					parameters.add(new Object[] {
							snapshot.snapshotId(),
							start + index,
							identity.chain().value(),
							identity.transactionId().value(),
							identity.eventId().locator() });
				}
				jdbcTemplate.batchUpdate("""
						INSERT INTO marketdata.dataset_snapshot_members (
						    snapshot_id, member_ordinal, chain_id, transaction_value, event_locator
						) VALUES (?, ?, ?, ?, ?)
						""", parameters);
			}
			return snapshot;
		}
		var existing = findSnapshot(snapshot.snapshotId()).orElseThrow(() ->
				new IllegalStateException("Dataset snapshot disappeared after uniqueness resolution"));
		if (!existing.equals(snapshot)) {
			throw new IllegalStateException("Dataset snapshot conflict for " + snapshot.snapshotId());
		}
		return existing;
	}

	private Optional<DatasetSnapshot> findSnapshot(String snapshotId) {
		var header = jdbcClient.sql("""
				SELECT snapshot_id, fingerprint, canonicalization_version, cutoff
				FROM marketdata.dataset_snapshots WHERE snapshot_id = :snapshotId
				""")
				.param("snapshotId", snapshotId)
				.query((resultSet, rowNumber) -> new SnapshotHeader(
						resultSet.getString("snapshot_id"), resultSet.getString("fingerprint"),
						resultSet.getString("canonicalization_version"),
						resultSet.getObject("cutoff", OffsetDateTime.class).toInstant()))
				.optional();
		return header.map(value -> new DatasetSnapshot(
				value.snapshotId(), value.fingerprint(), value.canonicalizationVersion(), value.cutoff(),
				jdbcClient.sql("SELECT " + prefixColumns("normalized") + """
						 FROM marketdata.dataset_snapshot_members member
						 JOIN marketdata.normalized_swaps normalized
						   ON normalized.chain_id = member.chain_id
						  AND normalized.transaction_value = member.transaction_value
						  AND normalized.event_locator = member.event_locator
						 WHERE member.snapshot_id = :snapshotId
						 ORDER BY member.member_ordinal
						""")
						.param("snapshotId", snapshotId)
						.query(this::map).list()));
	}

	private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, MarketObservation observation) {
		return bindIdentity(statement, observation.identity())
				.param("asset", observation.asset().value())
				.param("wallet", observation.wallet().value())
				.param("side", observation.side().name())
				.param("tokenQuantity", new BigDecimal(observation.tokenQuantity()))
				.param("nativeQuantity", new BigDecimal(observation.nativeQuantity()))
				.param("priceUsd", observation.priceUsd())
				.param("liquidityUsd", observation.liquidityUsd())
				.param("confidence", observation.confidence())
				.param("venue", observation.venue())
				.param("blockPosition", observation.blockPosition().value())
				.param("blockHash", observation.blockHash().orElse(null), Types.VARCHAR)
				.param("sourceEventTime", observation.sourceEventTime().map(this::timestamp).orElse(null), Types.TIMESTAMP_WITH_TIMEZONE)
				.param("observedAt", timestamp(observation.observedAt()))
				.param("provider", observation.provider())
				.param("rawPayloadHash", observation.rawPayloadFingerprint())
				.param("transformationVersion", observation.transformationVersion());
	}

	private JdbcClient.StatementSpec bindIdentity(
			JdbcClient.StatementSpec statement, NormalizedSwapIdentity identity) {
		return statement
				.param("chainId", identity.chain().value())
				.param("transactionValue", identity.transactionId().value())
				.param("eventLocator", identity.eventId().locator());
	}

	private MarketObservation map(ResultSet resultSet, int rowNumber) throws SQLException {
		var chain = new ChainId(resultSet.getString("chain_id"));
		var transaction = new TransactionId(chain, resultSet.getString("transaction_value"));
		var identity = new NormalizedSwapIdentity(
				chain, transaction, new EventId(transaction, resultSet.getString("event_locator")));
		return new MarketObservation(
				identity,
				new AssetId(chain, resultSet.getString("asset_address")),
				new WalletAddress(chain, resultSet.getString("wallet_address")),
				TradeSide.valueOf(resultSet.getString("side")),
				resultSet.getBigDecimal("token_quantity").toBigIntegerExact(),
				resultSet.getBigDecimal("native_quantity").toBigIntegerExact(),
				resultSet.getBigDecimal("price_usd"),
				resultSet.getBigDecimal("liquidity_usd"),
				resultSet.getBigDecimal("confidence"),
				resultSet.getString("venue"),
				new BlockPosition(chain, resultSet.getLong("observed_block_position")),
				Optional.ofNullable(resultSet.getString("observed_block_hash")),
				Optional.ofNullable(resultSet.getObject("source_event_time", OffsetDateTime.class)).map(OffsetDateTime::toInstant),
				resultSet.getObject("observed_at", OffsetDateTime.class).toInstant(),
				resultSet.getString("provider"),
				resultSet.getString("raw_payload_hash"),
				resultSet.getString("transformation_version"));
	}

	private boolean sameEvidence(MarketObservation first, MarketObservation second) {
		return first.identity().equals(second.identity())
				&& first.asset().equals(second.asset())
				&& first.wallet().equals(second.wallet())
				&& first.side() == second.side()
				&& first.tokenQuantity().equals(second.tokenQuantity())
				&& first.nativeQuantity().equals(second.nativeQuantity())
				&& first.priceUsd().compareTo(second.priceUsd()) == 0
				&& first.liquidityUsd().compareTo(second.liquidityUsd()) == 0
				&& first.confidence().compareTo(second.confidence()) == 0
				&& first.venue().equals(second.venue())
				&& first.blockPosition().equals(second.blockPosition())
				&& first.blockHash().equals(second.blockHash())
				&& first.sourceEventTime().equals(second.sourceEventTime())
				&& first.observedAt().equals(second.observedAt())
				&& first.provider().equals(second.provider())
				&& first.rawPayloadFingerprint().equals(second.rawPayloadFingerprint())
				&& first.transformationVersion().equals(second.transformationVersion());
	}

	private OffsetDateTime timestamp(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private String prefixColumns(String alias) {
		return COLUMNS.lines().map(String::trim).filter(line -> !line.isEmpty())
				.map(line -> java.util.Arrays.stream(line.split(","))
						.map(String::trim).filter(value -> !value.isEmpty())
						.map(value -> alias + "." + value).toList())
				.flatMap(List::stream).collect(java.util.stream.Collectors.joining(", "));
	}

	private record SnapshotHeader(String snapshotId, String fingerprint, String canonicalizationVersion, Instant cutoff) {
	}
}
