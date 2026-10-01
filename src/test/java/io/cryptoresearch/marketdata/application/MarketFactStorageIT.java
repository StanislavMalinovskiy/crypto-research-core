package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
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
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;
import io.cryptoresearch.marketdata.api.MarketDataApi.RevisionReference;

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
	private final StoreRawObservationUseCase rawStore;
	private final StoreRawTransactionUseCase rawTransactionStore;
	private final JdbcClient jdbcClient;
	private final MarketDataApi marketData;

	@Autowired
	MarketFactStorageIT(RecordMarketFactUseCase useCase, StoreRawObservationUseCase rawStore,
			StoreRawTransactionUseCase rawTransactionStore,
			JdbcClient jdbcClient, MarketDataApi marketData) {
		this.useCase = useCase;
		this.rawStore = rawStore;
		this.rawTransactionStore = rawTransactionStore;
		this.jdbcClient = jdbcClient;
		this.marketData = marketData;
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
	void versionedPriceRetainsTwoValidDerivationsOfOneCanonicalEvent() {
		var source = identity(1);
		rawStore.store(new RawChainObservation(CHAIN, source.transactionId(), source.eventId(),
				"provider-a", new BlockPosition(CHAIN, 101), Optional.empty(), Optional.empty(), T1,
				"{\"recordedPrice\":\"1.5\"}", "recorded-price-v1"));
		var original = new RecordMarketFactUseCase.VersionedPriceRequest(
				price(1, T1, "1.500000000000000000"),
				RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", "price-v1");
		var corrected = new RecordMarketFactUseCase.VersionedPriceRequest(
				price(1, T1, "1.600000000000000000"),
				RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", "price-v2");
		var first = useCase.storeVersionedPrice(original);
		var second = new AtomicReference<RecordMarketFactUseCase.VersionedPriceResult>();
		assertThatCode(() -> second.set(useCase.storeVersionedPrice(corrected)))
				.as("a different valid derivation remains addressable alongside the first")
				.doesNotThrowAnyException();
		assertThat(useCase.storeVersionedPrice(original).revisionKey()).isEqualTo(first.revisionKey());
		assertThat(second.get().revisionKey()).isNotEqualTo(first.revisionKey());
		assertThat(jdbcClient.sql("SELECT price FROM marketdata.price_revisions WHERE revision_key = :revision")
				.param("revision", first.revisionKey()).query(BigDecimal.class).single())
				.isEqualByComparingTo("1.500000000000000000");
		assertThat(jdbcClient.sql("SELECT price FROM marketdata.price_revisions WHERE revision_key = :revision")
				.param("revision", second.get().revisionKey()).query(BigDecimal.class).single())
				.isEqualByComparingTo("1.600000000000000000");
		assertThat(jdbcClient.sql("SELECT count(*) FROM marketdata.price_revisions")
				.query(Integer.class).single()).isEqualTo(2);
		assertThat(priceCount()).isZero();
	}

	@Test
	void usdRevisionPinsBothExactPriceRevisionsAndRejectsConflictingContent() {
		try {
		storeRawPriceSource(3);
		storeRawPriceSource(4);
		var converted = useCase.storeVersionedPrice(new RecordMarketFactUseCase.VersionedPriceRequest(
				price(3, T1, "1.500000000000000000"),
				RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", "price-v1"));
		var quote = useCase.storeVersionedPrice(new RecordMarketFactUseCase.VersionedPriceRequest(
				price(4, T1, "2.000000000000000000"),
				RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", "price-v1"));
		var request = new RecordMarketFactUseCase.VersionedUsdRequest(
				usd(3, 4, "1.800000000000000000"), converted.revisionKey(), quote.revisionKey());
		var first = useCase.storeVersionedUsd(request);
		assertThat(useCase.storeVersionedUsd(request).revisionKey()).isEqualTo(first.revisionKey());
		assertThat(jdbcClient.sql("SELECT converted_price_revision_key || '|' || quote_price_revision_key FROM marketdata.usd_revisions")
				.query(String.class).single()).isEqualTo(converted.revisionKey() + "|" + quote.revisionKey());
		assertThatThrownBy(() -> useCase.storeVersionedUsd(new RecordMarketFactUseCase.VersionedUsdRequest(
				usd(3, 4, "9.900000000000000000"), converted.revisionKey(), quote.revisionKey())))
				.isInstanceOf(MarketFactConflictException.class);
		assertThatThrownBy(() -> useCase.storeVersionedUsd(new RecordMarketFactUseCase.VersionedUsdRequest(
				usd(3, 4, "1.800000000000000000"), quote.revisionKey(), converted.revisionKey())))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("exact source price revisions");
		assertThat(jdbcClient.sql("SELECT count(*) FROM marketdata.usd_revisions").query(Integer.class).single()).isOne();
		}
		finally {
			jdbcClient.sql("DELETE FROM marketdata.usd_revisions").update();
			jdbcClient.sql("DELETE FROM marketdata.price_revisions WHERE transaction_value IN ('fixture-tx-3', 'fixture-tx-4')").update();
		}
	}

	@Test
	void concurrentEqualPriceRevisionRetryPublishesOneCompleteRow() throws Exception {
		storeRawPriceSource(5);
		var request = new RecordMarketFactUseCase.VersionedPriceRequest(
				price(5, T1, "1.500000000000000000"),
				RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", "price-v1");
		var release = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> {
				if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
				return useCase.storeVersionedPrice(request);
			});
			var second = executor.submit(() -> {
				if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
				return useCase.storeVersionedPrice(request);
			});
			release.countDown();
			try {
				assertThat(first.get(20, TimeUnit.SECONDS).revisionKey())
						.isEqualTo(second.get(20, TimeUnit.SECONDS).revisionKey());
				assertThat(jdbcClient.sql("SELECT count(*) FROM marketdata.price_revisions WHERE transaction_value = 'fixture-tx-5'")
						.query(Integer.class).single()).isOne();
				assertThatThrownBy(() -> useCase.storeVersionedPrice(new RecordMarketFactUseCase.VersionedPriceRequest(
						price(5, T1, "1.600000000000000000"),
						RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", "price-v1")))
						.isInstanceOf(MarketFactConflictException.class);
			}
			finally {
				release.countDown();
			}
		}
		finally {
			jdbcClient.sql("DELETE FROM marketdata.price_revisions WHERE transaction_value = 'fixture-tx-5'").update();
		}
	}

	private void storeRawPriceSource(int index) {
		var source = identity(index);
		rawStore.store(new RawChainObservation(CHAIN, source.transactionId(), source.eventId(),
				"provider-a", new BlockPosition(CHAIN, 100 + index), Optional.empty(), Optional.empty(), T1,
				"{\"priceSource\":\"" + index + "\"}", "recorded-price-v1"));
	}

	@Test
	void versionedFactReadReturnsExactRevisionAndTypedLineage() {
		storeRawPriceSource(6);
		storeRawPriceSource(7);
		try {
			var converted = useCase.storeVersionedPrice(new RecordMarketFactUseCase.VersionedPriceRequest(
					price(6, T1, "1.500000000000000000"),
					RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", "price-v1"));
			var quote = useCase.storeVersionedPrice(new RecordMarketFactUseCase.VersionedPriceRequest(
					price(7, T1, "2.000000000000000000"),
					RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", "price-v1"));
			var usd = useCase.storeVersionedUsd(new RecordMarketFactUseCase.VersionedUsdRequest(
					usd(6, 7, "1.800000000000000000"), converted.revisionKey(), quote.revisionKey()));
			var priceRef = new RevisionReference(FactKind.PRICE, identity(6), converted.revisionKey());
			assertThat(marketData.versionedFact(priceRef)).isPresent().get().satisfies(evidence -> {
				assertThat(evidence.reference()).isEqualTo(priceRef);
				assertThat(evidence.contentDigest()).startsWith("sha256:");
				assertThat(evidence.sourceKind()).isEqualTo("RAW_CHAIN_EVENT");
				assertThat(evidence.provider()).isEqualTo("provider-a");
				assertThat(evidence.rawPayloadHash()).startsWith("sha256:");
				assertThat(evidence.derivationVersion()).isEqualTo("price-v1");
			});
			var usdRef = new RevisionReference(FactKind.USD, identity(6), usd.revisionKey());
			assertThat(marketData.versionedFact(usdRef)).isPresent().get().satisfies(evidence -> {
				assertThat(evidence.sourceKind()).isEqualTo("PRICE_REVISION_PAIR");
				assertThat(evidence.convertedPriceRevisionKey()).isEqualTo(converted.revisionKey());
				assertThat(evidence.quotePriceRevisionKey()).isEqualTo(quote.revisionKey());
			});
			assertThat(marketData.versionedFact(new RevisionReference(FactKind.PRICE, identity(7), converted.revisionKey())))
					.isEmpty();
		}
		finally {
			jdbcClient.sql("DELETE FROM marketdata.usd_revisions WHERE transaction_value = 'fixture-tx-6'").update();
			jdbcClient.sql("DELETE FROM marketdata.price_revisions WHERE transaction_value IN ('fixture-tx-6', 'fixture-tx-7')").update();
		}
	}

	@Test
	void versionedPriceRetainsRawTransactionSourceLineage() {
		var source = identity(8);
		rawTransactionStore.store(new RawTransactionPayload(CHAIN, source.transactionId(),
				"provider-a", new BlockPosition(CHAIN, 108), Optional.empty(), Optional.of(T1),
				T1, T1, "{\"transactionSource\":\"eight\"}", Optional.of("raw-tx-v1")));
		try {
			var saved = useCase.storeVersionedPrice(new RecordMarketFactUseCase.VersionedPriceRequest(
					price(8, T1, "1.500000000000000000"),
					RecordMarketFactUseCase.RawSourceKind.RAW_TRANSACTION, "provider-a", "price-v1"));
			var evidence = marketData.versionedFact(new RevisionReference(FactKind.PRICE, source, saved.revisionKey()));
			assertThat(evidence).isPresent().get().satisfies(fact -> {
				assertThat(fact.sourceKind()).isEqualTo("RAW_TRANSACTION");
				assertThat(fact.sourceIdentity()).startsWith("sha256:");
				assertThat(fact.rawPayloadHash()).isEqualTo(jdbcClient.sql(
						"SELECT payload_hash FROM marketdata.raw_transactions WHERE transaction_value = 'fixture-tx-8'")
						.query(String.class).single());
			});
		}
		finally {
			jdbcClient.sql("DELETE FROM marketdata.price_revisions WHERE transaction_value = 'fixture-tx-8'").update();
			jdbcClient.sql("DELETE FROM marketdata.raw_transactions WHERE transaction_value = 'fixture-tx-8'").update();
		}
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
		useCase.storePrice(price(1, T1, "1.500000000000000000"));
		useCase.storePrice(price(2, T1, "2.000000000000000000"));
		useCase.storeUsdConversion(usd(2, 1, "2.500000000000000000"));
		var fact = usd(1, 1, "1.800000000000000000");
		assertThat(useCase.storeUsdConversion(fact)).isEqualTo(fact);
		assertThat(useCase.storeUsdConversion(fact)).isEqualTo(fact);
		assertThat(usdCount()).isEqualTo(2);

		assertThatThrownBy(() -> useCase.storeUsdConversion(usd(1, 1, "9.900000000000000000")))
				.isInstanceOf(MarketFactConflictException.class);
		assertThat(usdCount()).isEqualTo(2);
	}

	@Test
	void usdConversionRejectsMissingConvertedPriceObservation() {
		useCase.storePrice(price(2, T1, "2.000000000000000000"));

		assertThatThrownBy(() -> useCase.storeUsdConversion(usd(1, 2, "1.800000000000000000")))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(usdCount()).isZero();
		assertThat(priceCount()).isEqualTo(1);
	}

	@Test
	void usdConversionRejectsMissingUsdQuotePriceObservation() {
		useCase.storePrice(price(1, T1, "1.500000000000000000"));

		assertThatThrownBy(() -> useCase.storeUsdConversion(usd(1, 2, "1.800000000000000000")))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(usdCount()).isZero();
		assertThat(priceCount()).isEqualTo(1);
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
