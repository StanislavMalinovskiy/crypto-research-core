package io.cryptoresearch.marketdata.application;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;

@Service
public class RecordMarketFactUseCase {
	public enum RawSourceKind { RAW_CHAIN_EVENT, RAW_TRANSACTION }

	public record VersionedPriceRequest(PriceObservation observation, RawSourceKind sourceKind,
			String sourceProvider, String derivationVersion) { }

	public record VersionedPriceResult(PriceObservation observation, String revisionKey, String evidenceVersion) { }

	public record VersionedLiquidityRequest(LiquidityObservation observation, RawSourceKind sourceKind,
			String sourceProvider, String derivationVersion) { }

	public record VersionedLiquidityResult(LiquidityObservation observation, String revisionKey, String evidenceVersion) { }

	public record VersionedUsdRequest(UsdConversionFact fact, String convertedPriceRevisionKey,
			String quotePriceRevisionKey) { }

	public record VersionedUsdResult(UsdConversionFact fact, String revisionKey, String evidenceVersion) { }

	private final MarketFactStore store;
	private final VersionedFactStore versionedStore;

	public RecordMarketFactUseCase(MarketFactStore store, VersionedFactStore versionedStore) {
		this.store = Objects.requireNonNull(store, "store must not be null");
		this.versionedStore = Objects.requireNonNull(versionedStore, "versionedStore must not be null");
	}

	@Transactional
	public PriceObservation storePrice(PriceObservation observation) {
		Objects.requireNonNull(observation, "observation must not be null");
		return store.storePrice(observation);
	}

	@Transactional
	public VersionedPriceResult storeVersionedPrice(VersionedPriceRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		var observation = Objects.requireNonNull(request.observation(), "observation must not be null");
		if (!observation.provider().equals(request.sourceProvider())) {
			throw new IllegalArgumentException("Source provider must match price provider");
		}
		var rawHash = versionedStore.rawPayloadHash(request.sourceKind(), observation.source(), request.sourceProvider());
		var sourceIdentity = VersionedFactFingerprint.sourceIdentity(request.sourceKind(),
				observation.source(), request.sourceProvider());
		var derivationVersion = Objects.requireNonNull(request.derivationVersion(),
				"derivationVersion must not be null");
		rejectLegacyRevision(FactKind.PRICE, observation.source(), observation.asset().value(), observation.venue(),
				sourceIdentity, rawHash, derivationVersion);
		var key = VersionedFactFingerprint.revisionV2("PRICE", observation.source(), sourceIdentity, rawHash,
				derivationVersion, observation.asset().value(), "venue", observation.venue());
		var digest = VersionedFactFingerprint.priceContent(observation, key);
		versionedStore.storePrice(observation, key, digest, sourceIdentity, rawHash,
				derivationVersion, request.sourceKind());
		return new VersionedPriceResult(observation, key, "EXPLICIT_REVISION_V2");
	}

	@Transactional
	public VersionedLiquidityResult storeVersionedLiquidity(VersionedLiquidityRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		var observation = Objects.requireNonNull(request.observation(), "observation must not be null");
		if (!observation.provider().equals(request.sourceProvider())) {
			throw new IllegalArgumentException("Source provider must match liquidity provider");
		}
		var rawHash = versionedStore.rawPayloadHash(request.sourceKind(), observation.source(), request.sourceProvider());
		var sourceIdentity = VersionedFactFingerprint.sourceIdentity(request.sourceKind(),
				observation.source(), request.sourceProvider());
		var derivationVersion = Objects.requireNonNull(request.derivationVersion(),
				"derivationVersion must not be null");
		rejectLegacyRevision(FactKind.LIQUIDITY, observation.source(), observation.asset().value(), observation.poolAddress(),
				sourceIdentity, rawHash, derivationVersion);
		var key = VersionedFactFingerprint.revisionV2("LIQUIDITY", observation.source(), sourceIdentity, rawHash,
				derivationVersion, observation.asset().value(), "pool", observation.poolAddress());
		var digest = VersionedFactFingerprint.liquidityContent(observation, key);
		versionedStore.storeLiquidity(observation, key, digest, sourceIdentity, rawHash,
				derivationVersion, request.sourceKind());
		return new VersionedLiquidityResult(observation, key, "EXPLICIT_REVISION_V2");
	}

	private void rejectLegacyRevision(FactKind kind, NormalizedSwapIdentity source, String asset, String dimension,
			String sourceIdentity, String rawHash, String derivationVersion) {
		var legacyKey = VersionedFactFingerprint.revision(kind.name(), source, sourceIdentity, rawHash,
				derivationVersion, asset + "|" + dimension);
		var expected = new VersionedFactStore.RevisionIdentity(legacyKey, kind, source, asset, dimension,
				sourceIdentity, rawHash, derivationVersion);
		versionedStore.findRevisionIdentity(kind, legacyKey).ifPresent(saved -> {
			if (VersionedFactFingerprint.revisionVersion(saved) == VersionedFactFingerprint.RevisionVersion.V1
					&& saved.equals(expected)) {
				throw new IllegalStateException("Legacy revision version conflict for " + source);
			}
		});
	}

	@Transactional
	public VersionedUsdResult storeVersionedUsd(VersionedUsdRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		var fact = Objects.requireNonNull(request.fact(), "fact must not be null");
		if (!versionedStore.priceRevisionMatches(request.convertedPriceRevisionKey(), fact.source())
				|| !versionedStore.priceRevisionMatches(request.quotePriceRevisionKey(), fact.usdQuoteSource())) {
			throw new IllegalArgumentException("USD inputs must resolve to exact source price revisions");
		}
		var key = VersionedFactFingerprint.usdRevision(
				fact, request.convertedPriceRevisionKey(), request.quotePriceRevisionKey());
		versionedStore.storeUsd(fact, key, VersionedFactFingerprint.usdContent(fact, key),
				request.convertedPriceRevisionKey(), request.quotePriceRevisionKey());
		return new VersionedUsdResult(fact, key, "EXPLICIT_REVISION_V1");
	}

	@Transactional
	public LiquidityObservation storeLiquidity(LiquidityObservation observation) {
		Objects.requireNonNull(observation, "observation must not be null");
		return store.storeLiquidity(observation);
	}

	@Transactional
	public UsdConversionFact storeUsdConversion(UsdConversionFact fact) {
		Objects.requireNonNull(fact, "fact must not be null");
		return store.storeUsdConversion(fact);
	}

	public List<PriceObservation> priceObservations(ObservationQuery query) {
		return store.priceObservations(Objects.requireNonNull(query, "query must not be null"));
	}

	public List<LiquidityObservation> liquidityObservations(ObservationQuery query) {
		return store.liquidityObservations(Objects.requireNonNull(query, "query must not be null"));
	}

	public java.util.Optional<PriceObservation> findPrice(NormalizedSwapIdentity source) {
		return store.findPrice(source);
	}

	public java.util.Optional<LiquidityObservation> findLiquidity(
			NormalizedSwapIdentity source, AssetId asset, String poolAddress) {
		return store.findLiquidity(source, asset, poolAddress);
	}

	public java.util.Optional<UsdConversionFact> findUsdConversion(NormalizedSwapIdentity source) {
		return store.findUsdConversion(source);
	}
}
