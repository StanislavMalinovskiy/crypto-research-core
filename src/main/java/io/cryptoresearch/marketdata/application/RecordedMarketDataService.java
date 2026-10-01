package io.cryptoresearch.marketdata.application;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.cryptoresearch.marketdata.api.MarketDataApi;

@Service
public class RecordedMarketDataService implements MarketDataApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(RecordedMarketDataService.class);
	private static final String ITEM_COUNTER = "crypto.research.marketdata.replay.items";
	private static final String INVOCATION_COUNTER = "crypto.research.marketdata.replay.invocations";
	private static final String SUMMARY_MESSAGE = "Recorded replay summary";

	private static final Comparator<MarketObservation> SNAPSHOT_ORDER =
			Comparator.comparing(MarketObservation::identity);

	private final StoreRawObservationUseCase rawStore;
	private final NormalizeRecordedSwapUseCase normalizer;
	private final NormalizedMarketDataStore normalizedStore;
	private final ObjectProvider<VersionedSwapUseCase> versionedSwaps;
	private final ObjectProvider<VersionedSnapshotFinalizer> versionedSnapshots;
	private final ObjectProvider<VersionedSnapshotStore> versionedSnapshotStore;
	private final ObjectProvider<VersionedFactStore> versionedFactStore;
	private final MeterRegistry meterRegistry;

	public RecordedMarketDataService(
			StoreRawObservationUseCase rawStore,
			NormalizeRecordedSwapUseCase normalizer,
			NormalizedMarketDataStore normalizedStore,
			ObjectProvider<VersionedSwapUseCase> versionedSwaps,
			ObjectProvider<VersionedSnapshotFinalizer> versionedSnapshots,
			ObjectProvider<VersionedSnapshotStore> versionedSnapshotStore,
			ObjectProvider<VersionedFactStore> versionedFactStore,
			MeterRegistry meterRegistry) {
		this.rawStore = rawStore;
		this.normalizer = normalizer;
		this.normalizedStore = normalizedStore;
		this.versionedSwaps = versionedSwaps;
		this.versionedSnapshots = versionedSnapshots;
		this.versionedSnapshotStore = versionedSnapshotStore;
		this.versionedFactStore = versionedFactStore;
		this.meterRegistry = meterRegistry;
	}

	@Override
	public ReplayResult replay(RecordedDataset dataset) {
		Objects.requireNonNull(dataset, "dataset must not be null");
		var results = new ArrayList<ReplayItem>();
		var attempted = 0;
		var normalized = 0;
		var normalizationFailed = 0;
		try {
			for (var input : dataset.observations()) {
				attempted++;
				var raw = new RawChainObservation(
						input.chain(), input.transactionId(), input.eventId(), input.provider(), input.blockPosition(),
						input.blockHash(), input.sourceEventTime(), input.observedAt(), input.payload(), input.transformationVersion());
				rawStore.store(raw);
				var fingerprint = PayloadFingerprint.sha256(input.payload());
				try {
					normalizer.normalize(input, fingerprint);
					results.add(new ReplayItem(
							new NormalizedSwapIdentity(input.chain(), input.transactionId(), input.eventId()),
							fingerprint, ReplayStatus.NORMALIZED, java.util.Optional.empty()));
					normalized++;
					recordItem("normalized");
				}
				catch (IllegalArgumentException | NormalizationConflictException exception) {
					results.add(new ReplayItem(
							new NormalizedSwapIdentity(input.chain(), input.transactionId(), input.eventId()),
							fingerprint, ReplayStatus.NORMALIZATION_FAILED, java.util.Optional.of(exception.getMessage())));
					normalizationFailed++;
					recordItem("normalization_failed");
				}
			}
			var replayResult = new ReplayResult(results);
			publishSummary("completed", attempted, normalized, normalizationFailed);
			return replayResult;
		}
		catch (RuntimeException exception) {
			publishSummary("aborted", attempted, normalized, normalizationFailed);
			throw exception;
		}
	}

	@Override
	public VersionedReplayResult replayVersioned(RecordedDataset dataset) {
		Objects.requireNonNull(dataset, "dataset must not be null");
		var items = new ArrayList<VersionedReplayItem>();
		for (var input : dataset.observations()) {
			var source = new RawChainObservation(input.chain(), input.transactionId(), input.eventId(),
					input.provider(), input.blockPosition(), input.blockHash(), input.sourceEventTime(),
					input.observedAt(), input.payload(), input.transformationVersion());
			rawStore.store(source);
			var rawHash = PayloadFingerprint.sha256(input.payload());
			var identity = new NormalizedSwapIdentity(input.chain(), input.transactionId(), input.eventId());
			try {
				var revision = versionedSwaps.getObject().normalizeAndStore(input, rawHash);
				items.add(new VersionedReplayItem(new ReplayItem(identity, rawHash,
						ReplayStatus.NORMALIZED, java.util.Optional.empty()), revision));
			}
			catch (IllegalArgumentException | MarketFactConflictException exception) {
				items.add(new VersionedReplayItem(new ReplayItem(identity, rawHash,
						ReplayStatus.NORMALIZATION_FAILED, java.util.Optional.of(exception.getMessage())), null));
			}
		}
		return new VersionedReplayResult(items, "EXPLICIT_REVISION_V1");
	}

	@Override
	public VersionedSnapshot finalizeVersioned(VersionedFinalizeRequest request) {
		for (var attempt = 0; attempt < 2; attempt++) {
			try {
				return versionedSnapshots.getObject().finalizeSnapshot(request);
			}
			catch (CannotAcquireLockException exception) {
				if (attempt == 1 || !serializationAbort(exception)) {
					throw exception;
				}
			}
		}
		throw new IllegalStateException("Unreachable snapshot retry state");
	}

	private boolean serializationAbort(Throwable failure) {
		for (var cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && "40001".equals(sql.getSQLState())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public List<MarketObservation> versionedObservations(PointInTimeQuery query) {
		return versionedSnapshotStore.getObject().swapObservations(
				query.datasetFingerprint().orElseThrow(() -> new IllegalArgumentException("Versioned snapshot fingerprint required")), query);
	}

	@Override
	public java.util.Optional<VersionedFactEvidence> versionedFact(RevisionReference reference) {
		return versionedFactStore.getObject().findRevision(Objects.requireNonNull(reference, "reference must not be null"));
	}

	@Override
	public java.util.Optional<VersionedSnapshotEvidence> versionedSnapshotEvidence(String fingerprint) {
		return versionedSnapshotStore.getObject().findEvidence(Objects.requireNonNull(fingerprint, "fingerprint must not be null"));
	}

	private void recordItem(String status) {
		try {
			meterRegistry.counter(ITEM_COUNTER, Tags.of("status", status)).increment();
		}
		catch (RuntimeException ignored) {
			// Operational telemetry must not change replay behavior.
		}
	}

	private void publishSummary(String outcome, int attempted, int normalized, int normalizationFailed) {
		try {
			meterRegistry.counter(INVOCATION_COUNTER, Tags.of("outcome", outcome)).increment();
		}
		catch (RuntimeException ignored) {
			// Keep the summary attempt independent from counter availability.
		}
		try {
			LOGGER.atInfo()
					.addKeyValue("operation", "recorded_replay")
					.addKeyValue("outcome", outcome)
					.addKeyValue("attempted", attempted)
					.addKeyValue("normalized", normalized)
					.addKeyValue("normalization_failed", normalizationFailed)
					.addKeyValue("unclassified", attempted - normalized - normalizationFailed)
					.log(SUMMARY_MESSAGE);
		}
		catch (RuntimeException ignored) {
			// Logging is best-effort and must not replace a replay result or failure.
		}
	}

	@Override
	@Transactional
	public DatasetSnapshot finalizeDataset(FinalizeDatasetRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		if (request.canonicalizationVersion() == null || request.canonicalizationVersion().isBlank()) {
			throw new IllegalArgumentException("canonicalizationVersion must not be blank");
		}
		var cutoff = microseconds(request.cutoff());
		var distinctMembers = request.members().stream().distinct().toList();
		var byIdentity = new java.util.HashMap<
				io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity,
				io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation>();
		for (var observation : normalizedStore.findAll(distinctMembers)) {
			byIdentity.put(observation.identity(), observation);
		}
		var observations = distinctMembers.stream()
				.map(identity -> java.util.Optional.ofNullable(byIdentity.get(identity))
						.orElseThrow(() -> new IllegalArgumentException("Unknown normalized member: " + identity)))
				.filter(observation -> !observation.observedAt().isAfter(cutoff))
				.sorted(SNAPSHOT_ORDER)
				.toList();
		if (observations.size() != request.members().stream().distinct().count()) {
			throw new IllegalArgumentException("Dataset member observation is after the declared cutoff");
		}
		var fingerprint = datasetFingerprint(request.canonicalizationVersion(), cutoff, observations);
		return normalizedStore.storeSnapshot(new DatasetSnapshot(
				fingerprint, fingerprint, request.canonicalizationVersion(), cutoff, observations));
	}

	@Override
	public List<MarketObservation> observations(PointInTimeQuery query) {
		Objects.requireNonNull(query, "query must not be null");
		if (query.fromInclusive().isAfter(query.toInclusive())) {
			throw new IllegalArgumentException("fromInclusive must not be after toInclusive");
		}
		if (query.toInclusive().isAfter(query.cutoff())) {
			throw new IllegalArgumentException("toInclusive must not be after cutoff");
		}
		var normalized = new PointInTimeQuery(
				query.asset(), microseconds(query.fromInclusive()), microseconds(query.toInclusive()),
				microseconds(query.cutoff()), query.datasetFingerprint());
		var versionedStore = versionedSnapshotStore.getIfAvailable();
		if (normalized.datasetFingerprint().isPresent() && versionedStore != null
				&& versionedStore.containsSnapshot(normalized.datasetFingerprint().get())) {
			return versionedStore.swapObservations(normalized.datasetFingerprint().get(), normalized);
		}
		return normalizedStore.observations(normalized);
	}

	private String datasetFingerprint(String version, Instant cutoff, List<MarketObservation> observations) {
		var fingerprint = new CanonicalFingerprint()
				.field("contract", "marketdata-dataset")
				.field("canonicalizationVersion", version)
				.field("cutoff", cutoff.toString())
				.field("memberCount", Integer.toString(observations.size()));
		for (var observation : observations) {
			fingerprint
					.field("chainId", observation.identity().chain().value())
					.field("transactionValue", observation.identity().transactionId().value())
					.field("eventLocator", observation.identity().eventId().locator())
					.field("asset", observation.asset().value())
					.field("wallet", observation.wallet().value())
					.field("side", observation.side().name())
					.field("tokenQuantity", observation.tokenQuantity().toString())
					.field("nativeQuantity", observation.nativeQuantity().toString())
					.field("priceUsd", observation.priceUsd().setScale(18, RoundingMode.UNNECESSARY).toPlainString())
					.field("liquidityUsd", observation.liquidityUsd().setScale(8, RoundingMode.UNNECESSARY).toPlainString())
					.field("confidence", observation.confidence().setScale(4, RoundingMode.UNNECESSARY).toPlainString())
					.field("venue", observation.venue())
					.field("blockPosition", Long.toString(observation.blockPosition().value()))
					.field("blockHash", observation.blockHash().orElse(""))
					.field("sourceEventTime", observation.sourceEventTime().map(Instant::toString).orElse(""))
					.field("observedAt", observation.observedAt().toString())
					.field("provider", observation.provider())
					.field("rawPayloadFingerprint", observation.rawPayloadFingerprint())
					.field("transformationVersion", observation.transformationVersion());
		}
		return fingerprint.finish();
	}

	private Instant microseconds(Instant instant) {
		return Objects.requireNonNull(instant, "instant must not be null").truncatedTo(ChronoUnit.MICROS);
	}
}
