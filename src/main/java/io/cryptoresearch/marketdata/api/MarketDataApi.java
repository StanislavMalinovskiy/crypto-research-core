package io.cryptoresearch.marketdata.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.kernel.api.WalletAddress;

/** Synchronous public boundary for recorded replay and point-in-time market data. */
public interface MarketDataApi {

	ReplayResult replay(RecordedDataset dataset);

	DatasetSnapshot finalizeDataset(FinalizeDatasetRequest request);

	List<MarketObservation> observations(PointInTimeQuery query);

	/** Modeled historical v2 route. Its initial implementation delegates to legacy evidence. */
	VersionedReplayResult replayVersioned(RecordedDataset dataset);

	VersionedSnapshot finalizeVersioned(VersionedFinalizeRequest request);

	List<MarketObservation> versionedObservations(PointInTimeQuery query);

	Optional<VersionedFactEvidence> versionedFact(RevisionReference reference);

	Optional<VersionedSnapshotEvidence> versionedSnapshotEvidence(String fingerprint);

	enum FactKind { SWAP, PRICE, LIQUIDITY, USD }

	enum AvailabilityStatus { HISTORICAL_MODEL, VERIFIED_REALTIME }

	record RevisionReference(FactKind kind, NormalizedSwapIdentity canonicalIdentity, String revisionKey) { }

	record VersionedFactEvidence(RevisionReference reference, String contentDigest, String sourceKind,
			String sourceIdentity, String provider, String rawPayloadHash, String derivationVersion,
			String convertedPriceRevisionKey, String quotePriceRevisionKey,
			AvailabilityStatus availabilityStatus, Optional<Instant> availableAt) {
		public VersionedFactEvidence {
			availableAt = Optional.ofNullable(availableAt).orElseGet(Optional::empty);
		}
	}

	record VersionedSnapshotEvidence(String fingerprint, Instant knowledgeCutoff,
			AvailabilityStatus availabilityStatus, SelectionScope scope, List<RevisionReference> members,
			List<ExcludedFact> excluded, int coveredKeyCount,
			String selectionVersion, String canonicalizationVersion) {
		public VersionedSnapshotEvidence {
			members = List.copyOf(members);
			excluded = List.copyOf(excluded);
		}
	}

	record SelectionScope(ChainId chain, List<AssetId> assets, Instant fromInclusive, Instant toInclusive,
			List<FactKind> factKinds, List<String> pools, List<String> venues,
			Instant eventFromInclusive, Instant eventToInclusive) {
		public SelectionScope(ChainId chain, List<AssetId> assets, Instant fromInclusive, Instant toInclusive,
				List<FactKind> factKinds, List<String> pools, List<String> venues) {
			this(chain, assets, fromInclusive, toInclusive, factKinds, pools, venues, fromInclusive, toInclusive);
		}
		public SelectionScope {
			assets = List.copyOf(assets);
			factKinds = List.copyOf(factKinds);
			pools = List.copyOf(pools);
			venues = List.copyOf(venues);
		}
	}

	record ExcludedFact(FactKind kind, NormalizedSwapIdentity canonicalIdentity, String assetAddress,
			String scopeDimension, String reason, String evidenceFingerprint) { }

	record VersionedFinalizeRequest(String canonicalizationVersion, Instant knowledgeCutoff,
			SelectionScope scope, List<RevisionReference> included, List<ExcludedFact> excluded,
			String selectionVersion, String policyVersion, AvailabilityStatus availabilityStatus) {
		public VersionedFinalizeRequest {
			included = List.copyOf(included);
			excluded = List.copyOf(excluded);
		}
	}

	record VersionedSnapshot(String fingerprint, String snapshotId, Instant knowledgeCutoff,
			SelectionScope scope, List<RevisionReference> included, List<ExcludedFact> excluded,
			AvailabilityStatus availabilityStatus, String evidenceVersion) {
		public VersionedSnapshot {
			included = List.copyOf(included);
			excluded = List.copyOf(excluded);
		}
	}

	record VersionedReplayItem(ReplayItem legacyItem, RevisionReference revision) { }

	record VersionedReplayResult(List<VersionedReplayItem> items, String evidenceVersion) {
		public VersionedReplayResult {
			items = List.copyOf(items);
		}
	}

	record RecordedDataset(String fixtureVersion, List<RecordedSwapInput> observations) {
		public RecordedDataset {
			observations = List.copyOf(observations);
		}
	}

	record RecordedSwapInput(
			ChainId chain,
			TransactionId transactionId,
			EventId eventId,
			String provider,
			BlockPosition blockPosition,
			Optional<String> blockHash,
			Optional<Instant> sourceEventTime,
			Instant observedAt,
			String payload,
			String transformationVersion) {
		public RecordedSwapInput {
			blockHash = Optional.ofNullable(blockHash).orElseGet(Optional::empty);
			sourceEventTime = Optional.ofNullable(sourceEventTime).orElseGet(Optional::empty);
		}
	}

	record NormalizedSwapIdentity(ChainId chain, TransactionId transactionId, EventId eventId)
			implements Comparable<NormalizedSwapIdentity> {
		@Override
		public int compareTo(NormalizedSwapIdentity other) {
			var chainOrder = chain.compareTo(other.chain);
			if (chainOrder != 0) {
				return chainOrder;
			}
			var transactionOrder = transactionId.compareTo(other.transactionId);
			return transactionOrder != 0 ? transactionOrder : eventId.compareTo(other.eventId);
		}
	}

	enum ReplayStatus {
		NORMALIZED,
		NORMALIZATION_FAILED
	}

	record ReplayItem(
			NormalizedSwapIdentity identity,
			String rawPayloadFingerprint,
			ReplayStatus status,
			Optional<String> failure) {
		public ReplayItem {
			failure = Optional.ofNullable(failure).orElseGet(Optional::empty);
		}
	}

	record ReplayResult(List<ReplayItem> items) {
		public ReplayResult {
			items = List.copyOf(items);
		}
	}

	enum TradeSide {
		BUY,
		SELL
	}

	record MarketObservation(
			NormalizedSwapIdentity identity,
			AssetId asset,
			WalletAddress wallet,
			TradeSide side,
			BigInteger tokenQuantity,
			BigInteger nativeQuantity,
			BigDecimal priceUsd,
			BigDecimal liquidityUsd,
			BigDecimal confidence,
			String venue,
			BlockPosition blockPosition,
			Optional<String> blockHash,
			Optional<Instant> sourceEventTime,
			Instant observedAt,
			String provider,
			String rawPayloadFingerprint,
			String transformationVersion) {
		public MarketObservation {
			blockHash = Optional.ofNullable(blockHash).orElseGet(Optional::empty);
			sourceEventTime = Optional.ofNullable(sourceEventTime).orElseGet(Optional::empty);
		}
	}

	record FinalizeDatasetRequest(
			String canonicalizationVersion,
			Instant cutoff,
			List<NormalizedSwapIdentity> members) {
		public FinalizeDatasetRequest {
			members = List.copyOf(members);
		}
	}

	record DatasetSnapshot(
			String snapshotId,
			String fingerprint,
			String canonicalizationVersion,
			Instant cutoff,
			List<MarketObservation> observations) {
		public DatasetSnapshot {
			observations = List.copyOf(observations);
		}
	}

	record PointInTimeQuery(
			AssetId asset,
			Instant fromInclusive,
			Instant toInclusive,
			Instant cutoff,
			Optional<String> datasetFingerprint) {
		public PointInTimeQuery {
			datasetFingerprint = Optional.ofNullable(datasetFingerprint).orElseGet(Optional::empty);
		}
	}
}
