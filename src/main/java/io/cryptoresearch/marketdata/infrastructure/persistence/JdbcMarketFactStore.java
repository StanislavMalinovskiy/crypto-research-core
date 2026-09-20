package io.cryptoresearch.marketdata.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.application.LiquidityObservation;
import io.cryptoresearch.marketdata.application.MarketFactConflictException;
import io.cryptoresearch.marketdata.application.MarketFactStore;
import io.cryptoresearch.marketdata.application.ObservationQuery;
import io.cryptoresearch.marketdata.application.PriceObservation;
import io.cryptoresearch.marketdata.application.UsdConversionFact;

@Repository
public class JdbcMarketFactStore implements MarketFactStore {

	private static final String PRICE_COLUMNS = """
			chain_id, transaction_value, event_locator, asset_address, quote_asset_address, venue,
			price, trade_notional_quote, observed_block_position, observed_at, confidence, provider,
			source_event_time
			""";

	private static final String LIQUIDITY_COLUMNS = """
			chain_id, transaction_value, event_locator, asset_address, pool_address, quote_asset_address,
			liquidity_usd, base_reserve, quote_reserve, observed_block_position, observed_at, confidence,
			provider, source_event_time
			""";

	private static final String USD_COLUMNS = """
			chain_id, transaction_value, event_locator, asset_address,
			usd_quote_transaction_value, usd_quote_event_locator, price_usd, method_version, computed_at
			""";

	private final JdbcClient jdbcClient;

	public JdbcMarketFactStore(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public PriceObservation storePrice(PriceObservation observation) {
		var inserted = bindPrice(jdbcClient.sql("""
				INSERT INTO marketdata.price_observations (
				    chain_id, transaction_value, event_locator, asset_address, quote_asset_address, venue,
				    price, trade_notional_quote, observed_block_position, observed_at, confidence, provider,
				    source_event_time
				) VALUES (
				    :chainId, :transactionValue, :eventLocator, :asset, :quoteAsset, :venue,
				    :price, :tradeNotionalQuote, :blockPosition, :observedAt, :confidence, :provider,
				    :sourceEventTime
				)
				ON CONFLICT (chain_id, transaction_value, event_locator) DO NOTHING
				RETURNING 1
				"""), observation).query(Integer.class).optional().isPresent();
		if (inserted) {
			return observation;
		}
		var existing = findPrice(observation.source())
				.orElseThrow(() -> new IllegalStateException("Price observation disappeared after uniqueness resolution"));
		if (!samePrice(existing, observation)) {
			throw new MarketFactConflictException(observation.source());
		}
		return existing;
	}

	@Override
	public Optional<PriceObservation> findPrice(NormalizedSwapIdentity source) {
		return bindSource(jdbcClient.sql("SELECT " + PRICE_COLUMNS + """
					 FROM marketdata.price_observations
					 WHERE chain_id = :chainId AND transaction_value = :transactionValue
					   AND event_locator = :eventLocator
					"""), source).query(this::mapPrice).optional();
	}

	@Override
	public List<PriceObservation> priceObservations(ObservationQuery query) {
		return jdbcClient.sql("SELECT " + PRICE_COLUMNS + """
				 FROM marketdata.price_observations
				 WHERE chain_id = :chainId AND asset_address = :asset
				   AND observed_at >= :fromInclusive AND observed_at <= :toInclusive
				   AND observed_at <= :cutoff
				 ORDER BY observed_at, transaction_value, event_locator
				""")
				.param("chainId", query.asset().chain().value())
				.param("asset", query.asset().value())
				.param("fromInclusive", timestamp(query.fromInclusive()))
				.param("toInclusive", timestamp(query.toInclusive()))
				.param("cutoff", timestamp(query.cutoff()))
				.query(this::mapPrice)
				.list();
	}

	@Override
	public LiquidityObservation storeLiquidity(LiquidityObservation observation) {
		var inserted = bindLiquidity(jdbcClient.sql("""
				INSERT INTO marketdata.liquidity_observations (
				    chain_id, transaction_value, event_locator, asset_address, pool_address, quote_asset_address,
				    liquidity_usd, base_reserve, quote_reserve, observed_block_position, observed_at, confidence,
				    provider, source_event_time
				) VALUES (
				    :chainId, :transactionValue, :eventLocator, :asset, :poolAddress, :quoteAsset,
				    :liquidityUsd, :baseReserve, :quoteReserve, :blockPosition, :observedAt, :confidence,
				    :provider, :sourceEventTime
				)
				ON CONFLICT (chain_id, asset_address, pool_address, transaction_value, event_locator) DO NOTHING
				RETURNING 1
				"""), observation).query(Integer.class).optional().isPresent();
		if (inserted) {
			return observation;
		}
		var existing = findLiquidity(observation.source(), observation.asset(), observation.poolAddress())
				.orElseThrow(() -> new IllegalStateException(
						"Liquidity observation disappeared after uniqueness resolution"));
		if (!sameLiquidity(existing, observation)) {
			throw new MarketFactConflictException(observation.source());
		}
		return existing;
	}

	@Override
	public Optional<LiquidityObservation> findLiquidity(NormalizedSwapIdentity source, AssetId asset, String poolAddress) {
		return bindSource(jdbcClient.sql("SELECT " + LIQUIDITY_COLUMNS + """
					 FROM marketdata.liquidity_observations
					 WHERE asset_address = :asset AND pool_address = :poolAddress
					   AND chain_id = :chainId AND transaction_value = :transactionValue
					   AND event_locator = :eventLocator
					"""), source)
				.param("asset", asset.value())
				.param("poolAddress", poolAddress)
				.query(this::mapLiquidity)
				.optional();
	}

	@Override
	public List<LiquidityObservation> liquidityObservations(ObservationQuery query) {
		return jdbcClient.sql("SELECT " + LIQUIDITY_COLUMNS + """
				 FROM marketdata.liquidity_observations
				 WHERE chain_id = :chainId AND asset_address = :asset
				   AND observed_at >= :fromInclusive AND observed_at <= :toInclusive
				   AND observed_at <= :cutoff
				 ORDER BY observed_at, pool_address, transaction_value, event_locator
				""")
				.param("chainId", query.asset().chain().value())
				.param("asset", query.asset().value())
				.param("fromInclusive", timestamp(query.fromInclusive()))
				.param("toInclusive", timestamp(query.toInclusive()))
				.param("cutoff", timestamp(query.cutoff()))
				.query(this::mapLiquidity)
				.list();
	}

	@Override
	public UsdConversionFact storeUsdConversion(UsdConversionFact fact) {
		var inserted = bindUsd(jdbcClient.sql("""
				INSERT INTO marketdata.usd_conversion_facts (
				    chain_id, transaction_value, event_locator, asset_address,
				    usd_quote_transaction_value, usd_quote_event_locator, price_usd, method_version, computed_at
				) VALUES (
				    :chainId, :transactionValue, :eventLocator, :asset,
				    :usdQuoteTransactionValue, :usdQuoteEventLocator, :priceUsd, :methodVersion, :computedAt
				)
				ON CONFLICT (chain_id, transaction_value, event_locator) DO NOTHING
				RETURNING 1
				"""), fact).query(Integer.class).optional().isPresent();
		if (inserted) {
			return fact;
		}
		var existing = findUsdConversion(fact.source())
				.orElseThrow(() -> new IllegalStateException("USD conversion disappeared after uniqueness resolution"));
		if (!sameUsd(existing, fact)) {
			throw new MarketFactConflictException(fact.source());
		}
		return existing;
	}

	@Override
	public Optional<UsdConversionFact> findUsdConversion(NormalizedSwapIdentity source) {
		return bindSource(jdbcClient.sql("SELECT " + USD_COLUMNS + """
					 FROM marketdata.usd_conversion_facts
					 WHERE chain_id = :chainId AND transaction_value = :transactionValue
					   AND event_locator = :eventLocator
					"""), source).query(this::mapUsd).optional();
	}

	private JdbcClient.StatementSpec bindSource(
			JdbcClient.StatementSpec statement, NormalizedSwapIdentity source) {
		return statement
				.param("chainId", source.chain().value())
				.param("transactionValue", source.transactionId().value())
				.param("eventLocator", source.eventId().locator());
	}

	private JdbcClient.StatementSpec bindPrice(
			JdbcClient.StatementSpec statement, PriceObservation observation) {
		return bindSource(statement, observation.source())
				.param("asset", observation.asset().value())
				.param("quoteAsset", observation.quoteAsset().value())
				.param("venue", observation.venue())
				.param("price", observation.price())
				.param("tradeNotionalQuote", observation.tradeNotionalQuote())
				.param("blockPosition", observation.blockPosition().value())
				.param("observedAt", timestamp(observation.observedAt()))
				.param("confidence", observation.confidence())
				.param("provider", observation.provider())
				.param("sourceEventTime", observation.sourceEventTime().map(this::timestamp).orElse(null),
						Types.TIMESTAMP_WITH_TIMEZONE);
	}

	private JdbcClient.StatementSpec bindLiquidity(
			JdbcClient.StatementSpec statement, LiquidityObservation observation) {
		return bindSource(statement, observation.source())
				.param("asset", observation.asset().value())
				.param("poolAddress", observation.poolAddress())
				.param("quoteAsset", observation.quoteAsset().value())
				.param("liquidityUsd", observation.liquidityUsd())
				.param("baseReserve", new java.math.BigDecimal(observation.baseReserve()))
				.param("quoteReserve", new java.math.BigDecimal(observation.quoteReserve()))
				.param("blockPosition", observation.blockPosition().value())
				.param("observedAt", timestamp(observation.observedAt()))
				.param("confidence", observation.confidence())
				.param("provider", observation.provider())
				.param("sourceEventTime", observation.sourceEventTime().map(this::timestamp).orElse(null),
						Types.TIMESTAMP_WITH_TIMEZONE);
	}

	private JdbcClient.StatementSpec bindUsd(JdbcClient.StatementSpec statement, UsdConversionFact fact) {
		return bindSource(statement, fact.source())
				.param("asset", fact.asset().value())
				.param("usdQuoteTransactionValue", fact.usdQuoteSource().transactionId().value())
				.param("usdQuoteEventLocator", fact.usdQuoteSource().eventId().locator())
				.param("priceUsd", fact.priceUsd())
				.param("methodVersion", fact.methodVersion())
				.param("computedAt", timestamp(fact.computedAt()));
	}

	private PriceObservation mapPrice(ResultSet resultSet, int rowNumber) throws SQLException {
		return new PriceObservation(
				identity(resultSet),
				new AssetId(new ChainId(resultSet.getString("chain_id")), resultSet.getString("asset_address")),
				new AssetId(new ChainId(resultSet.getString("chain_id")), resultSet.getString("quote_asset_address")),
				resultSet.getString("venue"),
				resultSet.getBigDecimal("price"),
				resultSet.getBigDecimal("trade_notional_quote"),
				blockPosition(resultSet),
				resultSet.getObject("observed_at", OffsetDateTime.class).toInstant(),
				resultSet.getBigDecimal("confidence"),
				resultSet.getString("provider"),
				Optional.ofNullable(resultSet.getObject("source_event_time", OffsetDateTime.class))
						.map(OffsetDateTime::toInstant));
	}

	private LiquidityObservation mapLiquidity(ResultSet resultSet, int rowNumber) throws SQLException {
		return new LiquidityObservation(
				identity(resultSet),
				new AssetId(new ChainId(resultSet.getString("chain_id")), resultSet.getString("asset_address")),
				resultSet.getString("pool_address"),
				new AssetId(new ChainId(resultSet.getString("chain_id")), resultSet.getString("quote_asset_address")),
				resultSet.getBigDecimal("liquidity_usd"),
				resultSet.getBigDecimal("base_reserve").toBigIntegerExact(),
				resultSet.getBigDecimal("quote_reserve").toBigIntegerExact(),
				blockPosition(resultSet),
				resultSet.getObject("observed_at", OffsetDateTime.class).toInstant(),
				resultSet.getBigDecimal("confidence"),
				resultSet.getString("provider"),
				Optional.ofNullable(resultSet.getObject("source_event_time", OffsetDateTime.class))
						.map(OffsetDateTime::toInstant));
	}

	private UsdConversionFact mapUsd(ResultSet resultSet, int rowNumber) throws SQLException {
		var chain = new ChainId(resultSet.getString("chain_id"));
		return new UsdConversionFact(
				identity(resultSet),
				new AssetId(chain, resultSet.getString("asset_address")),
				new NormalizedSwapIdentity(
						chain,
						new TransactionId(chain, resultSet.getString("usd_quote_transaction_value")),
						new EventId(new TransactionId(chain, resultSet.getString("usd_quote_transaction_value")),
								resultSet.getString("usd_quote_event_locator"))),
				resultSet.getBigDecimal("price_usd"),
				resultSet.getString("method_version"),
				resultSet.getObject("computed_at", OffsetDateTime.class).toInstant());
	}

	private NormalizedSwapIdentity identity(ResultSet resultSet) throws SQLException {
		var chain = new ChainId(resultSet.getString("chain_id"));
		var transaction = new TransactionId(chain, resultSet.getString("transaction_value"));
		return new NormalizedSwapIdentity(
				chain, transaction, new EventId(transaction, resultSet.getString("event_locator")));
	}

	private io.cryptoresearch.kernel.api.BlockPosition blockPosition(ResultSet resultSet) throws SQLException {
		return new io.cryptoresearch.kernel.api.BlockPosition(
				new ChainId(resultSet.getString("chain_id")), resultSet.getLong("observed_block_position"));
	}

	private boolean samePrice(PriceObservation first, PriceObservation second) {
		return first.source().equals(second.source())
				&& first.asset().equals(second.asset())
				&& first.quoteAsset().equals(second.quoteAsset())
				&& first.venue().equals(second.venue())
				&& first.price().compareTo(second.price()) == 0
				&& first.tradeNotionalQuote().compareTo(second.tradeNotionalQuote()) == 0
				&& first.blockPosition().equals(second.blockPosition())
				&& first.observedAt().equals(second.observedAt())
				&& first.confidence().compareTo(second.confidence()) == 0
				&& first.provider().equals(second.provider())
				&& first.sourceEventTime().equals(second.sourceEventTime());
	}

	private boolean sameLiquidity(LiquidityObservation first, LiquidityObservation second) {
		return first.source().equals(second.source())
				&& first.asset().equals(second.asset())
				&& first.poolAddress().equals(second.poolAddress())
				&& first.quoteAsset().equals(second.quoteAsset())
				&& first.liquidityUsd().compareTo(second.liquidityUsd()) == 0
				&& first.baseReserve().equals(second.baseReserve())
				&& first.quoteReserve().equals(second.quoteReserve())
				&& first.blockPosition().equals(second.blockPosition())
				&& first.observedAt().equals(second.observedAt())
				&& first.confidence().compareTo(second.confidence()) == 0
				&& first.provider().equals(second.provider())
				&& first.sourceEventTime().equals(second.sourceEventTime());
	}

	private boolean sameUsd(UsdConversionFact first, UsdConversionFact second) {
		return first.source().equals(second.source())
				&& first.asset().equals(second.asset())
				&& first.usdQuoteSource().equals(second.usdQuoteSource())
				&& first.priceUsd().compareTo(second.priceUsd()) == 0
				&& first.methodVersion().equals(second.methodVersion())
				&& first.computedAt().equals(second.computedAt());
	}

	private OffsetDateTime timestamp(java.time.Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
