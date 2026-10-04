package io.cryptoresearch.marketdata.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.SelectionScope;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedSnapshot;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedSnapshotEvidence;

public interface VersionedSnapshotStore {

	record CanonicalFactKey(FactKind kind, String chain, String transaction, String locator,
			String asset, String dimension) { }

	record Fact(FactKind kind, String chain, String transaction, String locator, String asset,
			String dimension, String revisionKey, String contentDigest, Instant observedAt,
			String availabilityStatus, Optional<Instant> availableAt) {
		public CanonicalFactKey canonicalKey() {
			return new CanonicalFactKey(kind, chain, transaction, locator, asset, dimension);
		}

		/** Compatibility encoding for saved v1 fingerprints, never a tuple equality key. */
		public String legacyFingerprintKey() {
			return kind + "|" + chain + "|" + transaction + "|" + locator + "|" + asset + "|" + dimension;
		}
	}

	List<Fact> visible(SelectionScope scope, Instant knowledgeCutoff, Fact after, int limit);

	Instant databaseFreezeInstant();

	boolean containsSnapshot(String fingerprint);

	Optional<VersionedSnapshotEvidence> findEvidence(String fingerprint);

	VersionedSnapshot store(VersionedSnapshot snapshot, String canonicalizationVersion,
			String selectionVersion, String policyVersion, List<Fact> members,
			List<io.cryptoresearch.marketdata.api.MarketDataApi.ExcludedFact> exclusions);

	List<MarketObservation> swapObservations(String snapshotFingerprint,
			io.cryptoresearch.marketdata.api.MarketDataApi.PointInTimeQuery query);
}
