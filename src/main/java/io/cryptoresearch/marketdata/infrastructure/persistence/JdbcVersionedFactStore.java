package io.cryptoresearch.marketdata.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.api.MarketDataApi.AvailabilityStatus;
import io.cryptoresearch.marketdata.api.MarketDataApi.RevisionReference;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedFactEvidence;
import io.cryptoresearch.marketdata.application.LiquidityObservation;
import io.cryptoresearch.marketdata.application.MarketFactConflictException;
import io.cryptoresearch.marketdata.application.PriceObservation;
import io.cryptoresearch.marketdata.application.RecordMarketFactUseCase.RawSourceKind;
import io.cryptoresearch.marketdata.application.VersionedFactStore;
import io.cryptoresearch.marketdata.application.UsdConversionFact;

@Repository
public class JdbcVersionedFactStore implements VersionedFactStore {

	private final JdbcClient jdbc;

	public JdbcVersionedFactStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<RevisionIdentity> findRevisionIdentity(FactKind kind, String revisionKey) {
		var table = switch (kind) {
			case PRICE -> "price_revisions";
			case LIQUIDITY -> "liquidity_revisions";
			default -> throw new IllegalArgumentException("Identity recognition supports price and liquidity only");
		};
		var dimension = kind == FactKind.PRICE ? "venue" : "pool_address";
		return jdbc.sql("SELECT chain_id, transaction_value, event_locator, asset_address, " + dimension
				+ " AS dimension, source_identity, raw_payload_hash, derivation_version FROM marketdata."
				+ table + " WHERE revision_key = :key")
				.param("key", revisionKey).query((row, index) -> {
					var chain = new ChainId(row.getString("chain_id"));
					var transaction = new TransactionId(chain, row.getString("transaction_value"));
					return new RevisionIdentity(revisionKey, kind, new NormalizedSwapIdentity(chain, transaction,
							new EventId(transaction, row.getString("event_locator"))), row.getString("asset_address"),
							row.getString("dimension"), row.getString("source_identity"), row.getString("raw_payload_hash"),
							row.getString("derivation_version"));
				}).optional();
	}

	@Override
	public Optional<VersionedFactEvidence> findRevision(RevisionReference reference) {
		var table = switch (reference.kind()) {
			case SWAP -> "swap_revisions";
			case PRICE -> "price_revisions";
			case LIQUIDITY -> "liquidity_revisions";
			case USD -> "usd_revisions";
		};
		var usd = reference.kind() == io.cryptoresearch.marketdata.api.MarketDataApi.FactKind.USD;
		var columns = usd
				? "content_digest, 'PRICE_REVISION_PAIR' AS source_kind, "
						+ "(converted_price_revision_key || '|' || quote_price_revision_key) AS source_identity, "
						+ "NULL::text AS provider, NULL::text AS raw_payload_hash, method_version AS derivation_version, "
						+ "converted_price_revision_key, quote_price_revision_key, availability_status, available_at"
				: "content_digest, source_kind, source_identity, provider, raw_payload_hash, derivation_version, "
						+ "NULL::text AS converted_price_revision_key, NULL::text AS quote_price_revision_key, "
						+ "availability_status, available_at";
		return jdbc.sql("SELECT " + columns + " FROM marketdata." + table + " WHERE revision_key = :key "
				+ "AND chain_id = :chain AND transaction_value = :transaction AND event_locator = :locator")
				.param("key", reference.revisionKey()).param("chain", reference.canonicalIdentity().chain().value())
				.param("transaction", reference.canonicalIdentity().transactionId().value())
				.param("locator", reference.canonicalIdentity().eventId().locator())
				.query((row, index) -> new VersionedFactEvidence(reference, row.getString("content_digest"),
						row.getString("source_kind"), row.getString("source_identity"), row.getString("provider"),
						row.getString("raw_payload_hash"), row.getString("derivation_version"),
						row.getString("converted_price_revision_key"), row.getString("quote_price_revision_key"),
						AvailabilityStatus.valueOf(row.getString("availability_status")),
						Optional.ofNullable(row.getObject("available_at", OffsetDateTime.class))
								.map(OffsetDateTime::toInstant))).optional();
	}

	@Override
	public String rawPayloadHash(RawSourceKind kind, NormalizedSwapIdentity source, String provider) {
		var sql = kind == RawSourceKind.RAW_CHAIN_EVENT ? """
				SELECT payload_hash FROM marketdata.raw_chain_events
				WHERE chain_id = :chain AND transaction_value = :transaction
				AND event_locator = :locator AND provider = :provider
				""" : """
				SELECT payload_hash FROM marketdata.raw_transactions
				WHERE chain_id = :chain AND transaction_value = :transaction AND provider = :provider
				""";
		return jdbc.sql(sql).param("chain", source.chain().value())
				.param("transaction", source.transactionId().value())
				.param("locator", source.eventId().locator()).param("provider", provider)
				.query(String.class).optional()
				.orElseThrow(() -> new IllegalArgumentException("Missing typed raw source for " + source));
	}

	@Override
	public void storeSwap(MarketObservation value, String key, String digest, String sourceIdentity,
			RawSourceKind sourceKind) {
		var inserted = jdbc.sql("""
				INSERT INTO marketdata.swap_revisions
				(revision_key, chain_id, transaction_value, event_locator, source_kind, source_identity,
				 provider, raw_payload_hash, derivation_version, content_digest, availability_status,
				 asset_address, wallet_address, side, token_quantity, native_quantity, price_usd,
				 liquidity_usd, confidence, venue, observed_block_position, observed_block_hash,
				 source_event_time, observed_at)
				VALUES (:key, :chain, :transaction, :locator, :sourceKind, :sourceIdentity,
				 :provider, :rawHash, :derivation, :digest, 'HISTORICAL_MODEL', :asset, :wallet, :side,
				 :tokenQuantity, :nativeQuantity, :price, :liquidity, :confidence, :venue, :blockPosition,
				 :blockHash, :sourceEventTime, :observedAt)
				ON CONFLICT (revision_key) DO NOTHING RETURNING 1
				""").param("key", key).param("sourceKind", sourceKind.name())
				.param("chain", value.identity().chain().value())
				.param("transaction", value.identity().transactionId().value())
				.param("locator", value.identity().eventId().locator())
				.param("sourceIdentity", sourceIdentity).param("provider", value.provider())
				.param("rawHash", value.rawPayloadFingerprint())
				.param("derivation", value.transformationVersion()).param("digest", digest)
				.param("asset", value.asset().value()).param("wallet", value.wallet().value())
				.param("side", value.side().name()).param("tokenQuantity", value.tokenQuantity())
				.param("nativeQuantity", value.nativeQuantity()).param("price", value.priceUsd())
				.param("liquidity", value.liquidityUsd()).param("confidence", value.confidence())
				.param("venue", value.venue()).param("blockPosition", value.blockPosition().value())
				.param("blockHash", value.blockHash().orElse(null))
				.param("sourceEventTime", value.sourceEventTime().map(this::timestamp).orElse(null))
				.param("observedAt", timestamp(value.observedAt()))
				.query(Integer.class).optional().isPresent();
		if (!inserted) {
			verifyDigest("swap_revisions", key, digest, value.identity());
		}
	}

	@Override
	public void storePrice(PriceObservation value, String key, String digest,
			String sourceIdentity, String rawHash, String derivation, RawSourceKind kind) {
		var inserted = jdbc.sql("""
				INSERT INTO marketdata.price_revisions
				(revision_key, chain_id, transaction_value, event_locator, source_kind, source_identity,
				 provider, raw_payload_hash, derivation_version, content_digest, availability_status,
				 asset_address, quote_asset_address, venue, price, trade_notional_quote,
				 observed_block_position, observed_at, confidence, source_event_time)
				VALUES (:key, :chain, :transaction, :locator, :sourceKind, :sourceIdentity,
				 :provider, :rawHash, :derivation, :digest, 'HISTORICAL_MODEL', :asset, :quote, :venue,
				 :price, :notional, :blockPosition, :observedAt, :confidence, :sourceEventTime)
				ON CONFLICT (revision_key) DO NOTHING RETURNING 1
				""").param("key", key).param("chain", value.source().chain().value())
				.param("transaction", value.source().transactionId().value())
				.param("locator", value.source().eventId().locator())
				.param("sourceKind", kind.name()).param("sourceIdentity", sourceIdentity)
				.param("provider", value.provider()).param("rawHash", rawHash)
				.param("derivation", derivation).param("digest", digest)
				.param("asset", value.asset().value()).param("quote", value.quoteAsset().value())
				.param("venue", value.venue()).param("price", value.price())
				.param("notional", value.tradeNotionalQuote())
				.param("blockPosition", value.blockPosition().value())
				.param("observedAt", timestamp(value.observedAt())).param("confidence", value.confidence())
				.param("sourceEventTime", value.sourceEventTime().map(this::timestamp).orElse(null))
				.query(Integer.class).optional().isPresent();
		if (!inserted) {
			verifyDigest("price_revisions", key, digest, value.source());
		}
	}

	@Override
	public void storeLiquidity(LiquidityObservation value, String key, String digest,
			String sourceIdentity, String rawHash, String derivation, RawSourceKind kind) {
		var inserted = jdbc.sql("""
				INSERT INTO marketdata.liquidity_revisions
				(revision_key, chain_id, transaction_value, event_locator, source_kind, source_identity,
				 provider, raw_payload_hash, derivation_version, content_digest, availability_status,
				 asset_address, pool_address, quote_asset_address, liquidity_usd, base_reserve, quote_reserve,
				 observed_block_position, observed_at, confidence, source_event_time)
				VALUES (:key, :chain, :transaction, :locator, :sourceKind, :sourceIdentity,
				 :provider, :rawHash, :derivation, :digest, 'HISTORICAL_MODEL', :asset, :pool, :quote,
				 :liquidity, :baseReserve, :quoteReserve, :blockPosition, :observedAt, :confidence, :sourceEventTime)
				ON CONFLICT (revision_key) DO NOTHING RETURNING 1
				""").param("key", key).param("chain", value.source().chain().value())
				.param("transaction", value.source().transactionId().value())
				.param("locator", value.source().eventId().locator())
				.param("sourceKind", kind.name()).param("sourceIdentity", sourceIdentity)
				.param("provider", value.provider()).param("rawHash", rawHash)
				.param("derivation", derivation).param("digest", digest)
				.param("asset", value.asset().value()).param("pool", value.poolAddress())
				.param("quote", value.quoteAsset().value()).param("liquidity", value.liquidityUsd())
				.param("baseReserve", value.baseReserve()).param("quoteReserve", value.quoteReserve())
				.param("blockPosition", value.blockPosition().value())
				.param("observedAt", timestamp(value.observedAt())).param("confidence", value.confidence())
				.param("sourceEventTime", value.sourceEventTime().map(this::timestamp).orElse(null))
				.query(Integer.class).optional().isPresent();
		if (!inserted) {
			verifyDigest("liquidity_revisions", key, digest, value.source());
		}
	}

	@Override
	public boolean priceRevisionMatches(String key, NormalizedSwapIdentity source) {
		return jdbc.sql("""
				SELECT 1 FROM marketdata.price_revisions WHERE revision_key = :key
				AND chain_id = :chain AND transaction_value = :transaction AND event_locator = :locator
				""").param("key", key).param("chain", source.chain().value())
				.param("transaction", source.transactionId().value())
				.param("locator", source.eventId().locator()).query(Integer.class).optional().isPresent();
	}

	@Override
	public void storeUsd(UsdConversionFact fact, String key, String digest,
			String convertedPriceRevision, String quotePriceRevision) {
		var inserted = jdbc.sql("""
				INSERT INTO marketdata.usd_revisions
				(revision_key, chain_id, transaction_value, event_locator, asset_address,
				 converted_price_revision_key, quote_price_revision_key, method_version,
				 price_usd, computed_at, content_digest, availability_status)
				VALUES (:key, :chain, :transaction, :locator, :asset,
				 :convertedPrice, :quotePrice, :method, :price, :computedAt, :digest, 'HISTORICAL_MODEL')
				ON CONFLICT (revision_key) DO NOTHING RETURNING 1
				""").param("key", key).param("chain", fact.source().chain().value())
				.param("transaction", fact.source().transactionId().value())
				.param("locator", fact.source().eventId().locator()).param("asset", fact.asset().value())
				.param("convertedPrice", convertedPriceRevision).param("quotePrice", quotePriceRevision)
				.param("method", fact.methodVersion()).param("price", fact.priceUsd())
				.param("computedAt", timestamp(fact.computedAt())).param("digest", digest)
				.query(Integer.class).optional().isPresent();
		if (!inserted) {
			verifyDigest("usd_revisions", key, digest, fact.source());
		}
	}

	private void verifyDigest(String table, String key, String digest, NormalizedSwapIdentity source) {
		var previous = jdbc.sql("SELECT content_digest FROM marketdata." + table + " WHERE revision_key = :key")
				.param("key", key).query(String.class).single();
		if (!previous.equals(digest)) {
			throw new MarketFactConflictException(source);
		}
	}

	private OffsetDateTime timestamp(Instant value) {
		return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
	}
}
