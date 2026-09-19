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
