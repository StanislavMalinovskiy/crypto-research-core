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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
	@Autowired VersionedFactStore versionedStore;

	@ParameterizedTest
	@CsvSource({"PRICE,117", "PRICE,128", "LIQUIDITY,117", "LIQUIDITY,128"})
	void fullDerivationCapacityPersistsOriginalValueAndRetries(FactKind kind, int length) {
		int index = 51 + (kind == FactKind.PRICE ? 0 : 2) + (length == 128 ? 1 : 0);
		storeRawPriceSource(index);
		var version = "v".repeat(length - 4) + "|é/1";
		var key = new AtomicReference<String>();
		try {
			assertThatCode(() -> {
				if (kind == FactKind.PRICE) {
					var request = new RecordMarketFactUseCase.VersionedPriceRequest(price(index, T1, "1.500000000000000000"),
							RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", version);
					var saved = useCase.storeVersionedPrice(request);
					key.set(saved.revisionKey());
					assertThat(saved.evidenceVersion()).isEqualTo("EXPLICIT_REVISION_V2");
					assertThat(useCase.storeVersionedPrice(request)).isEqualTo(saved);
				}
				else {
					var request = new RecordMarketFactUseCase.VersionedLiquidityRequest(liquidity(index, T1, "50000.00000000"),
							RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", version);
					var saved = useCase.storeVersionedLiquidity(request);
					key.set(saved.revisionKey());
					assertThat(saved.evidenceVersion()).isEqualTo("EXPLICIT_REVISION_V2");
					assertThat(useCase.storeVersionedLiquidity(request)).isEqualTo(saved);
				}
			}).as("the existing %s-character derivation capacity must remain usable for %s", length, kind)
					.doesNotThrowAnyException();
			assertThat(marketData.versionedFact(new RevisionReference(kind, identity(index), key.get()))
					.orElseThrow().derivationVersion()).isEqualTo(version);
			assertThat(jdbcClient.sql("SELECT derivation_version FROM marketdata." + revisionTable(kind)
					+ " WHERE revision_key = :key").param("key", key.get()).query(String.class).single())
					.isEqualTo(version).hasSize(length);
			assertThat(revisionCount(kind, index)).isOne();
		}
		finally {
			deleteRevisions(kind, index);
		}
	}

	@ParameterizedTest
	@CsvSource({"PRICE,false", "PRICE,true", "LIQUIDITY,false", "LIQUIDITY,true"})
	void sameLegacyFactResubmissionConflictsWithoutDuplicating(FactKind kind, boolean changedContent) {
		int index = kind == FactKind.PRICE ? 55 : 56;
		storeRawPriceSource(index);
		try {
			var oldKey = seedLegacyDimensionFact(kind, index, "a|b", "c");
			var oldRow = revisionRow(revisionTable(kind), oldKey);
			assertThatThrownBy(() -> {
				if (kind == FactKind.PRICE) {
					useCase.storeVersionedPrice(priceRequest(dimensionPrice(index, "a|b", "c",
							changedContent ? "9.000000000000000000" : "1.500000000000000000")));
				}
				else {
					useCase.storeVersionedLiquidity(liquidityRequest(dimensionLiquidity(index, "a|b", "c",
							changedContent ? "90000.00000000" : "50000.00000000")));
				}
			}).as("same legacy tuple and lineage must conflict before any v2 insertion")
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("Legacy revision version conflict");
			assertThat(revisionCount(kind, index)).isOne();
			assertThat(revisionRow(revisionTable(kind), oldKey)).isEqualTo(oldRow);
		}
		finally {
			deleteRevisions(kind, index);
		}
	}

	@ParameterizedTest
	@EnumSource(value = FactKind.class, names = {"PRICE", "LIQUIDITY"})
	void differentlySplitLegacyCollisionAllowsDistinctV2Fact(FactKind kind) {
		int index = kind == FactKind.PRICE ? 59 : 60;
		storeRawPriceSource(index);
		try {
			var oldKey = seedLegacyDimensionFact(kind, index, "a|b", "c");
			var oldRow = revisionRow(revisionTable(kind), oldKey);
			var key = kind == FactKind.PRICE
					? useCase.storeVersionedPrice(priceRequest(dimensionPrice(index, "a", "b|c", "1.500000000000000000"))).revisionKey()
					: useCase.storeVersionedLiquidity(liquidityRequest(dimensionLiquidity(index, "a", "b|c", "50000.00000000"))).revisionKey();
			assertThat(key).isNotEqualTo(oldKey);
			assertThat(revisionCount(kind, index)).isEqualTo(2);
			assertThat(revisionRow(revisionTable(kind), oldKey)).isEqualTo(oldRow);
			assertThat(marketData.versionedFact(new RevisionReference(kind, identity(index), key)))
					.isPresent().get().satisfies(fact -> assertThat(fact.derivationVersion())
							.isEqualTo(kind == FactKind.PRICE ? "price-v1" : "liquidity-v1"));
		}
		finally {
			deleteRevisions(kind, index);
		}
	}

	@ParameterizedTest
	@EnumSource(value = FactKind.class, names = {"PRICE", "LIQUIDITY"})
	void nullDerivationRemainsRejectedWithoutWriting(FactKind kind) {
		int index = kind == FactKind.PRICE ? 57 : 58;
		storeRawPriceSource(index);
		assertThatThrownBy(() -> {
			if (kind == FactKind.PRICE) {
				useCase.storeVersionedPrice(new RecordMarketFactUseCase.VersionedPriceRequest(
						price(index, T1, "1.500000000000000000"),
						RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", null));
			}
			else {
				useCase.storeVersionedLiquidity(new RecordMarketFactUseCase.VersionedLiquidityRequest(
						liquidity(index, T1, "50000.00000000"),
						RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", null));
			}
		}).isInstanceOf(NullPointerException.class);
		assertThat(revisionCount(kind, index)).isZero();
	}

	private String seedLegacyDimensionFact(FactKind kind, int index, String asset, String dimension) {
		return seedLegacyDimensionFact(kind, index, asset, dimension,
				kind == FactKind.PRICE ? "price-v1" : "liquidity-v1");
	}

	private String seedLegacyDimensionFact(FactKind kind, int index, String asset, String dimension, String derivation) {
		var source = identity(index);
		var sourceKind = RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT;
		var sourceIdentity = VersionedFactFingerprint.sourceIdentity(sourceKind, source, "provider-a");
		var rawHash = versionedStore.rawPayloadHash(sourceKind, source, "provider-a");
		var key = VersionedFactFingerprint.revision(kind.name(), source, sourceIdentity, rawHash,
				derivation, asset + "|" + dimension);
		if (kind == FactKind.PRICE) {
			var observation = dimensionPrice(index, asset, dimension, "1.500000000000000000");
			versionedStore.storePrice(observation, key, VersionedFactFingerprint.priceContent(observation, key),
					sourceIdentity, rawHash, derivation, sourceKind);
		}
		else {
			var observation = dimensionLiquidity(index, asset, dimension, "50000.00000000");
			versionedStore.storeLiquidity(observation, key, VersionedFactFingerprint.liquidityContent(observation, key),
					sourceIdentity, rawHash, derivation, sourceKind);
		}
		return key;
	}

	@ParameterizedTest
	@EnumSource(value = FactKind.class, names = {"PRICE", "LIQUIDITY"})
	void recognizesSavedV1AndV2FieldsAndRejectsUnmatchedEvidence(FactKind kind) {
		int index = kind == FactKind.PRICE ? 61 : 62;
		storeRawPriceSource(index);
		// Derivation text is original provenance, even when it happens to start with this text.
		var originalVersion = "identity-v2/original|版本";
		try {
			var oldKey = seedLegacyDimensionFact(kind, index, "a|b", "c", originalVersion);
			var savedOld = versionedStore.findRevisionIdentity(kind, oldKey).orElseThrow();
			assertThat(savedOld.canonicalIdentity()).isEqualTo(identity(index));
			assertThat(savedOld.asset()).isEqualTo("a|b");
			assertThat(savedOld.dimension()).isEqualTo("c");
			assertThat(savedOld.derivationVersion()).isEqualTo(originalVersion);
			assertThat(VersionedFactFingerprint.revisionVersion(savedOld))
					.isEqualTo(VersionedFactFingerprint.RevisionVersion.V1);
			var newVersion = originalVersion + "/new";
			var newKey = writeDimensionFact(kind, index, newVersion);
			var savedNew = versionedStore.findRevisionIdentity(kind, newKey).orElseThrow();
			assertThat(savedNew.derivationVersion()).isEqualTo(newVersion);
			assertThat(savedNew.sourceIdentity()).isEqualTo(savedOld.sourceIdentity());
			assertThat(savedNew.rawPayloadHash()).isEqualTo(savedOld.rawPayloadHash());
			assertThat(VersionedFactFingerprint.revisionVersion(savedNew))
					.isEqualTo(VersionedFactFingerprint.RevisionVersion.V2);
			assertThat(versionedStore.findRevisionIdentity(kind, "sha256:" + "0".repeat(64))).isEmpty();
			for (var key : List.of(oldKey, newKey)) {
				// Corrupt isolated fixture provenance to prove that unmatched saved evidence is explicit.
				jdbcClient.sql("UPDATE marketdata." + revisionTable(kind)
						+ " SET derivation_version = 'unmatched-original' WHERE revision_key = :key")
						.param("key", key).update();
				var unmatched = versionedStore.findRevisionIdentity(kind, key).orElseThrow();
				assertThatThrownBy(() -> VersionedFactFingerprint.revisionVersion(unmatched))
						.isInstanceOf(IllegalStateException.class).hasMessageContaining("Invalid revision identity version");
			}
			assertThatThrownBy(() -> writeDimensionFact(kind, index, originalVersion))
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("Invalid revision identity version");
			assertThat(revisionCount(kind, index)).isEqualTo(2);
		}
		finally {
			deleteRevisions(kind, index);
		}
	}

	private String writeDimensionFact(FactKind kind, int index, String derivation) {
		var sourceKind = RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT;
		return kind == FactKind.PRICE
				? useCase.storeVersionedPrice(new RecordMarketFactUseCase.VersionedPriceRequest(
						dimensionPrice(index, "a|b", "c", "1.500000000000000000"), sourceKind, "provider-a", derivation)).revisionKey()
				: useCase.storeVersionedLiquidity(new RecordMarketFactUseCase.VersionedLiquidityRequest(
						dimensionLiquidity(index, "a|b", "c", "50000.00000000"), sourceKind, "provider-a", derivation)).revisionKey();
	}

	@Test
	void identityVersionRejectsNeitherOrBothCandidateMatches() {
		var key = "sha256:" + "1".repeat(64);
		var other = "sha256:" + "2".repeat(64);
		assertThatThrownBy(() -> VersionedFactFingerprint.revisionVersion(key, other, other))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("exactly one v1/v2 match");
		// Exercise the exactly-one comparison branch; this does not claim a real SHA-256 collision.
		assertThatThrownBy(() -> VersionedFactFingerprint.revisionVersion(key, key, key))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("exactly one v1/v2 match");
	}

	private String revisionTable(FactKind kind) {
		return kind == FactKind.PRICE ? "price_revisions" : "liquidity_revisions";
	}

	private int revisionCount(FactKind kind, int index) {
		return jdbcClient.sql("SELECT count(*) FROM marketdata." + revisionTable(kind) + " WHERE transaction_value = :transaction")
				.param("transaction", identity(index).transactionId().value()).query(Integer.class).single();
	}

	private void deleteRevisions(FactKind kind, int index) {
		jdbcClient.sql("DELETE FROM marketdata." + revisionTable(kind) + " WHERE transaction_value = :transaction")
				.param("transaction", identity(index).transactionId().value()).update();
	}

	@Test
	void priceRevisionSeparatesOpaqueDimensionsAndRetainsEqualRetryAndConflict() {
		storeRawPriceSource(41);
		try {
			var left = dimensionPrice(41, "a|b", "c", "1.500000000000000000");
			var right = dimensionPrice(41, "a", "b|c", "1.500000000000000000");
			var first = useCase.storeVersionedPrice(priceRequest(left));
			var second = new AtomicReference<RecordMarketFactUseCase.VersionedPriceResult>();
			assertThatCode(() -> second.set(useCase.storeVersionedPrice(priceRequest(right))))
					.as("distinct asset/venue tuples must coexist under distinct revision keys")
					.doesNotThrowAnyException();
			assertThat(second.get().revisionKey()).isNotEqualTo(first.revisionKey());
			assertThat(useCase.storeVersionedPrice(priceRequest(left))).isEqualTo(first);
			assertThat(first.evidenceVersion()).isEqualTo("EXPLICIT_REVISION_V2");
			assertThat(second.get().evidenceVersion()).isEqualTo("EXPLICIT_REVISION_V2");
			for (var result : List.of(first, second.get())) {
				var evidence = marketData.versionedFact(new RevisionReference(FactKind.PRICE,
						identity(41), result.revisionKey())).orElseThrow();
				assertThat(evidence.derivationVersion()).isEqualTo("price-v1");
				assertThat(evidence.sourceIdentity()).isEqualTo(VersionedFactFingerprint.sourceIdentity(
						RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, identity(41), "provider-a"));
				assertThat(evidence.rawPayloadHash()).isEqualTo(versionedStore.rawPayloadHash(
						RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, identity(41), "provider-a"));
				assertThat(jdbcClient.sql("SELECT asset_address, venue FROM marketdata.price_revisions WHERE revision_key = :key")
						.param("key", result.revisionKey()).query((row, index) -> List.of(
								row.getString("asset_address"), row.getString("venue"))).single())
						.containsExactly(result.observation().asset().value(), result.observation().venue());
			}
			assertThatThrownBy(() -> useCase.storeVersionedPrice(priceRequest(
					dimensionPrice(41, "a|b", "c", "9.000000000000000000"))))
					.isInstanceOf(MarketFactConflictException.class);
			assertThat(jdbcClient.sql("SELECT count(*) FROM marketdata.price_revisions WHERE transaction_value = 'fixture-tx-41'")
					.query(Integer.class).single()).isEqualTo(2);
			assertThat(jdbcClient.sql("SELECT price FROM marketdata.price_revisions WHERE revision_key = :key")
					.param("key", first.revisionKey()).query(BigDecimal.class).single()).isEqualByComparingTo(left.price());
		}
		finally {
			jdbcClient.sql("DELETE FROM marketdata.price_revisions WHERE transaction_value = 'fixture-tx-41'").update();
		}
	}

	@Test
	void liquidityRevisionSeparatesOpaqueDimensionsAndRetainsEqualRetryAndConflict() {
		storeRawPriceSource(42);
		try {
			var left = dimensionLiquidity(42, "a|b", "c", "50000.00000000");
			var right = dimensionLiquidity(42, "a", "b|c", "50000.00000000");
			var first = useCase.storeVersionedLiquidity(liquidityRequest(left));
			var second = new AtomicReference<RecordMarketFactUseCase.VersionedLiquidityResult>();
			assertThatCode(() -> second.set(useCase.storeVersionedLiquidity(liquidityRequest(right))))
					.as("distinct asset/pool tuples must coexist under distinct revision keys")
					.doesNotThrowAnyException();
			assertThat(second.get().revisionKey()).isNotEqualTo(first.revisionKey());
			assertThat(useCase.storeVersionedLiquidity(liquidityRequest(left))).isEqualTo(first);
			assertThat(first.evidenceVersion()).isEqualTo("EXPLICIT_REVISION_V2");
			assertThat(second.get().evidenceVersion()).isEqualTo("EXPLICIT_REVISION_V2");
			for (var result : List.of(first, second.get())) {
				var evidence = marketData.versionedFact(new RevisionReference(FactKind.LIQUIDITY,
						identity(42), result.revisionKey())).orElseThrow();
				assertThat(evidence.derivationVersion()).isEqualTo("liquidity-v1");
				assertThat(jdbcClient.sql("SELECT asset_address, pool_address FROM marketdata.liquidity_revisions WHERE revision_key = :key")
						.param("key", result.revisionKey()).query((row, index) -> List.of(
								row.getString("asset_address"), row.getString("pool_address"))).single())
						.containsExactly(result.observation().asset().value(), result.observation().poolAddress());
			}
			assertThatThrownBy(() -> useCase.storeVersionedLiquidity(liquidityRequest(
					dimensionLiquidity(42, "a|b", "c", "90000.00000000"))))
					.isInstanceOf(MarketFactConflictException.class);
			assertThat(jdbcClient.sql("SELECT count(*) FROM marketdata.liquidity_revisions WHERE transaction_value = 'fixture-tx-42'")
					.query(Integer.class).single()).isEqualTo(2);
			assertThat(jdbcClient.sql("SELECT liquidity_usd FROM marketdata.liquidity_revisions WHERE revision_key = :key")
					.param("key", first.revisionKey()).query(BigDecimal.class).single()).isEqualByComparingTo(left.liquidityUsd());
		}
		finally {
			jdbcClient.sql("DELETE FROM marketdata.liquidity_revisions WHERE transaction_value = 'fixture-tx-42'").update();
		}
	}

	private PriceObservation dimensionPrice(int index, String asset, String venue, String value) {
		var original = price(index, T1, value);
		return new PriceObservation(original.source(), new AssetId(CHAIN, asset), original.quoteAsset(), venue,
				original.price(), original.tradeNotionalQuote(), original.blockPosition(), original.observedAt(),
				original.confidence(), original.provider(), original.sourceEventTime());
	}

	private LiquidityObservation dimensionLiquidity(int index, String asset, String pool, String value) {
		var original = liquidity(index, T1, value);
		return new LiquidityObservation(original.source(), new AssetId(CHAIN, asset), pool, original.quoteAsset(),
				original.liquidityUsd(), original.baseReserve(), original.quoteReserve(), original.blockPosition(),
				original.observedAt(), original.confidence(), original.provider(), original.sourceEventTime());
	}

	private RecordMarketFactUseCase.VersionedPriceRequest priceRequest(PriceObservation observation) {
		return new RecordMarketFactUseCase.VersionedPriceRequest(observation,
				RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", "price-v1");
	}

	private RecordMarketFactUseCase.VersionedLiquidityRequest liquidityRequest(LiquidityObservation observation) {
		return new RecordMarketFactUseCase.VersionedLiquidityRequest(observation,
				RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT, "provider-a", "liquidity-v1");
	}

	@Test
	void oldRevisionKeysAndFrozenReferencesRemainExactAfterV2Writes() {
		storeRawPriceSource(43);
		var source = identity(43);
		var kind = RecordMarketFactUseCase.RawSourceKind.RAW_CHAIN_EVENT;
		var sourceIdentity = VersionedFactFingerprint.sourceIdentity(kind, source, "provider-a");
		var rawHash = versionedStore.rawPayloadHash(kind, source, "provider-a");
		var oldPrice = price(43, T1, "1.500000000000000000");
		var oldLiquidity = liquidity(43, T1, "50000.00000000");
		// Literal historical keys are independent of the new write-boundary encoder.
		var oldPriceKey = "sha256:76b529945543b3f2cfa10e7268d934dd08e02686144a0108489edc1d95b7d0b1";
		var oldLiquidityKey = "sha256:b5eba154c94a18827aa9f40f3971b30ea6dc2c2ab544c288b0a574c67125d439";
		assertThat(VersionedFactFingerprint.revision("PRICE", source, sourceIdentity, rawHash,
				"price-v1", oldPrice.asset().value() + "|" + oldPrice.venue())).isEqualTo(oldPriceKey);
		assertThat(VersionedFactFingerprint.revision("LIQUIDITY", source, sourceIdentity, rawHash,
				"liquidity-v1", oldLiquidity.asset().value() + "|" + oldLiquidity.poolAddress())).isEqualTo(oldLiquidityKey);
		String snapshotId = null;
		try {
			versionedStore.storePrice(oldPrice, oldPriceKey, VersionedFactFingerprint.priceContent(oldPrice, oldPriceKey),
					sourceIdentity, rawHash, "price-v1", kind);
			versionedStore.storeLiquidity(oldLiquidity, oldLiquidityKey,
					VersionedFactFingerprint.liquidityContent(oldLiquidity, oldLiquidityKey),
					sourceIdentity, rawHash, "liquidity-v1", kind);
			var priceRef = new RevisionReference(FactKind.PRICE, source, oldPriceKey);
			var liquidityRef = new RevisionReference(FactKind.LIQUIDITY, source, oldLiquidityKey);
			var oldPriceEvidence = marketData.versionedFact(priceRef).orElseThrow();
			var oldLiquidityEvidence = marketData.versionedFact(liquidityRef).orElseThrow();
			assertThat(oldPriceEvidence.derivationVersion()).isEqualTo("price-v1");
			assertThat(oldLiquidityEvidence.derivationVersion()).isEqualTo("liquidity-v1");
			var scope = new MarketDataApi.SelectionScope(CHAIN, List.of(ASSET), T1, T1,
					List.of(FactKind.PRICE, FactKind.LIQUIDITY), List.of(), List.of(), T1.minusSeconds(1), T1);
			var snapshot = marketData.finalizeVersioned(new MarketDataApi.VersionedFinalizeRequest(
					"length-prefixed-v2", T1, scope, List.of(priceRef, liquidityRef), List.of(),
					"explicit-revisions-v1", "modeled-history-v1", MarketDataApi.AvailabilityStatus.HISTORICAL_MODEL));
			snapshotId = snapshot.snapshotId();
			var frozen = marketData.versionedSnapshotEvidence(snapshot.fingerprint()).orElseThrow();
			var priceRow = revisionRow("price_revisions", oldPriceKey);
			var liquidityRow = revisionRow("liquidity_revisions", oldLiquidityKey);
			var usd = useCase.storeVersionedUsd(new RecordMarketFactUseCase.VersionedUsdRequest(
					usd(43, 43, "1.800000000000000000"), oldPriceKey, oldPriceKey));
			var newPrice = useCase.storeVersionedPrice(new RecordMarketFactUseCase.VersionedPriceRequest(
					oldPrice, kind, "provider-a", "price-v2"));
			var newLiquidity = useCase.storeVersionedLiquidity(new RecordMarketFactUseCase.VersionedLiquidityRequest(
					oldLiquidity, kind, "provider-a", "liquidity-v2"));
			assertThat(newPrice.revisionKey()).as("v2 writes must not reuse a recorded v1 key").isNotEqualTo(oldPriceKey);
			assertThat(newLiquidity.revisionKey()).isNotEqualTo(oldLiquidityKey);
			assertThat(marketData.versionedFact(priceRef)).contains(oldPriceEvidence);
			assertThat(marketData.versionedFact(liquidityRef)).contains(oldLiquidityEvidence);
			assertThat(marketData.versionedFact(new RevisionReference(FactKind.PRICE, identity(44), oldPriceKey))).isEmpty();
			assertThat(marketData.versionedSnapshotEvidence(snapshot.fingerprint())).contains(frozen);
			assertThat(revisionRow("price_revisions", oldPriceKey)).isEqualTo(priceRow);
			assertThat(revisionRow("liquidity_revisions", oldLiquidityKey)).isEqualTo(liquidityRow);
			assertThat(marketData.versionedFact(new RevisionReference(FactKind.USD, source, usd.revisionKey())))
					.isPresent().get().satisfies(evidence -> {
						assertThat(evidence.convertedPriceRevisionKey()).isEqualTo(oldPriceKey);
						assertThat(evidence.quotePriceRevisionKey()).isEqualTo(oldPriceKey);
					});
			assertThat(usd.evidenceVersion()).isEqualTo("EXPLICIT_REVISION_V1");
		}
		finally {
			if (snapshotId != null) {
				jdbcClient.sql("DELETE FROM marketdata.v2_dataset_snapshot_members WHERE snapshot_id = :id").param("id", snapshotId).update();
				jdbcClient.sql("DELETE FROM marketdata.v2_dataset_snapshots WHERE snapshot_id = :id").param("id", snapshotId).update();
			}
			jdbcClient.sql("DELETE FROM marketdata.usd_revisions WHERE transaction_value = 'fixture-tx-43'").update();
			jdbcClient.sql("DELETE FROM marketdata.price_revisions WHERE transaction_value = 'fixture-tx-43'").update();
			jdbcClient.sql("DELETE FROM marketdata.liquidity_revisions WHERE transaction_value = 'fixture-tx-43'").update();
		}
	}

	private String revisionRow(String table, String key) {
		return jdbcClient.sql("SELECT row_to_json(r)::text FROM marketdata." + table + " r WHERE revision_key = :key")
				.param("key", key).query(String.class).single();
	}

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
