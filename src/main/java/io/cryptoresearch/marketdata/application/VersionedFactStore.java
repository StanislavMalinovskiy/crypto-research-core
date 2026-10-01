package io.cryptoresearch.marketdata.application;

import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.api.MarketDataApi.RevisionReference;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedFactEvidence;
import java.util.Optional;
import io.cryptoresearch.marketdata.application.RecordMarketFactUseCase.RawSourceKind;

/** Owned persistence boundary for immutable, exact fact revisions. */
public interface VersionedFactStore {

	Optional<VersionedFactEvidence> findRevision(RevisionReference reference);

	String rawPayloadHash(RawSourceKind kind, NormalizedSwapIdentity source, String provider);

	void storeSwap(MarketObservation observation, String revisionKey, String contentDigest,
			String sourceIdentity, RawSourceKind sourceKind);

	void storePrice(PriceObservation observation, String revisionKey, String contentDigest,
			String sourceIdentity, String rawPayloadHash, String derivationVersion, RawSourceKind sourceKind);

	void storeLiquidity(LiquidityObservation observation, String revisionKey, String contentDigest,
			String sourceIdentity, String rawPayloadHash, String derivationVersion, RawSourceKind sourceKind);

	boolean priceRevisionMatches(String revisionKey, NormalizedSwapIdentity source);

	void storeUsd(UsdConversionFact fact, String revisionKey, String contentDigest,
			String convertedPriceRevisionKey, String quotePriceRevisionKey);
}
