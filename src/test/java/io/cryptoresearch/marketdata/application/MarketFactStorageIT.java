package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MarketFactStorageIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private static final ChainId CHAIN = ChainId.SOLANA_MAINNET;
	private static final AssetId ASSET = new AssetId(CHAIN, "Asset1111111111111111111111111111111111111111");
	private static final AssetId QUOTE = new AssetId(CHAIN, "Quote1111111111111111111111111111111111111111");
	private static final Instant T1 = Instant.parse("2026-09-20T10:00:00Z");
	private static final Instant T2 = Instant.parse("2026-09-20T10:00:30Z");
	private static final Instant T3 = Instant.parse("2026-09-20T10:01:00Z");

	private final RecordMarketFactUseCase useCase;
	private final JdbcClient jdbcClient;

	@Autowired
	MarketFactStorageIT(RecordMarketFactUseCase useCase, JdbcClient jdbcClient) {
		this.useCase = useCase;
		this.jdbcClient = jdbcClient;
	}

	@BeforeEach
	void resetTables() {
		jdbcClient.sql("DELETE FROM marketdata.usd_conversion_facts").update();
		jdbcClient.sql("DELETE FROM marketdata.liquidity_observations").update();
		jdbcClient.sql("DELETE FROM marketdata.price_observations").update();
	}

	@Test
	void priceObservationRetryIsIdempotentAgainstPopulatedTable() {
		useCase.storePrice(price(2, T2, "2.000000000000000000"));
		var first = useCase.storePrice(price(1, T1, "1.500000000000000000"));
		var retry = useCase.storePrice(price(1, T1, "1.500000000000000000"));

		assertThat(retry).isEqualTo(first);
		assertThat(retry.price().compareTo(first.price())).isZero();
		assertThat(priceCount()).isEqualTo(2);
		assertThat(useCase.findPrice(identity(1))).contains(first);
		assertThat(useCase.findPrice(identity(9))).isEmpty();
	}

	@Test
	void priceConflictIsRejectedAgainstPopulatedTable() {
		useCase.storePrice(price(1, T1, "1.500000000000000000"));
		useCase.storePrice(price(2, T2, "2.000000000000000000"));
		assertThatThrownBy(() -> useCase.storePrice(price(1, T1, "3.000000000000000000")))
				.isInstanceOf(MarketFactConflictException.class);
		assertThat(priceCount()).isEqualTo(2);
	}

	@Test
	void pricePointInTimeWindowExcludesLaterObservations() {
		useCase.storePrice(price(1, T1, "1.100000000000000000"));
		useCase.storePrice(price(2, T2, "1.200000000000000000"));
		useCase.storePrice(price(3, T3, "1.300000000000000000"));

		var result = useCase.priceObservations(new ObservationQuery(ASSET, T1, T2, T2));

		assertThat(result).hasSize(2);
		assertThat(result).extracting(observation -> observation.price().toPlainString())
				.containsExactly("1.100000000000000000", "1.200000000000000000");
	}

	@Test
	void equalTimePriceObservationsAreOrderedBySourceIdentity() {
		useCase.storePrice(price(2, T1, "2.000000000000000000"));
		useCase.storePrice(price(10, T1, "1.900000000000000000"));
		useCase.storePrice(price(1, T1, "1.100000000000000000"));

		var result = useCase.priceObservations(new ObservationQuery(ASSET, T1, T1, T1));

		assertThat(result).extracting(observation -> observation.source().transactionId().value())
				.containsExactly("fixture-tx-1", "fixture-tx-10", "fixture-tx-2");
	}

	@Test
	void liquidityObservationRetryIsIdempotentAgainstPopulatedTableAndPointInTime() {
		useCase.storeLiquidity(liquidity(2, T1, "60000.00000000"));
		var observation = liquidity(1, T1, "50000.00000000");
		assertThat(useCase.storeLiquidity(observation)).isEqualTo(observation);
		assertThat(useCase.storeLiquidity(observation)).isEqualTo(observation);
		assertThat(liquidityCount()).isEqualTo(2);

		useCase.storeLiquidity(liquidity(3, T2, "70000.00000000"));
		useCase.storeLiquidity(liquidity(4, T3, "80000.00000000"));
		var result = useCase.liquidityObservations(new ObservationQuery(ASSET, T1, T2, T2));
		assertThat(result).hasSize(3);
		assertThat(result).extracting(value -> value.liquidityUsd().toPlainString())
				.containsExactly("50000.00000000", "60000.00000000", "70000.00000000");
	}

	@Test
	void usdConversionRetryIsIdempotentAndConflictRejectedAgainstPopulatedTable() {
		useCase.storeUsdConversion(usd(2, 2, "2.500000000000000000"));
		var fact = usd(1, 1, "1.800000000000000000");
		assertThat(useCase.storeUsdConversion(fact)).isEqualTo(fact);
		assertThat(useCase.storeUsdConversion(fact)).isEqualTo(fact);
		assertThat(usdCount()).isEqualTo(2);

		assertThatThrownBy(() -> useCase.storeUsdConversion(usd(1, 1, "9.900000000000000000")))
				.isInstanceOf(MarketFactConflictException.class);
		assertThat(usdCount()).isEqualTo(2);
	}

	private PriceObservation price(int index, Instant observedAt, String price) {
		return new PriceObservation(
				identity(index), ASSET, QUOTE, "fixture-venue",
				new BigDecimal(price), new BigDecimal("120.00000000"),
				new BlockPosition(CHAIN, 100 + index), observedAt,
				new BigDecimal("1.0000"), "provider-a", Optional.of(observedAt.minusSeconds(1)));
	}

	private LiquidityObservation liquidity(int index, Instant observedAt, String liquidityUsd) {
		return new LiquidityObservation(
				identity(index), ASSET, "Pool11111111111111111111111111111111111111111", QUOTE,
				new BigDecimal(liquidityUsd), java.math.BigInteger.valueOf(1_000),
				java.math.BigInteger.valueOf(2_000), new BlockPosition(CHAIN, 100 + index),
				observedAt, new BigDecimal("1.0000"), "provider-a", Optional.empty());
	}

	private UsdConversionFact usd(int index, int quoteIndex, String priceUsd) {
		return new UsdConversionFact(
				identity(index), ASSET, identity(quoteIndex), new BigDecimal(priceUsd),
				"usd-conversion-v1", T1);
	}

	private NormalizedSwapIdentity identity(int index) {
		var transaction = new TransactionId(CHAIN, "fixture-tx-" + index);
		return new NormalizedSwapIdentity(CHAIN, transaction, new EventId(transaction, "i:1"));
	}

	private int priceCount() {
		return jdbcClient.sql("SELECT count(*) FROM marketdata.price_observations").query(Integer.class).single();
	}

	private int liquidityCount() {
		return jdbcClient.sql("SELECT count(*) FROM marketdata.liquidity_observations")
				.query(Integer.class).single();
	}

	private int usdCount() {
		return jdbcClient.sql("SELECT count(*) FROM marketdata.usd_conversion_facts").query(Integer.class).single();
	}
}
