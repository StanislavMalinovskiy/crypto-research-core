package io.cryptoresearch.marketdata.application;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cryptoresearch.marketdata.api.MarketDataApi;

@Service
public class RecordedMarketDataService implements MarketDataApi {

	private static final Comparator<MarketObservation> SNAPSHOT_ORDER =
			Comparator.comparing(MarketObservation::identity);

	private final StoreRawObservationUseCase rawStore;
	private final NormalizeRecordedSwapUseCase normalizer;
	private final NormalizedMarketDataStore normalizedStore;

	public RecordedMarketDataService(
			StoreRawObservationUseCase rawStore,
			NormalizeRecordedSwapUseCase normalizer,
			NormalizedMarketDataStore normalizedStore) {
		this.rawStore = rawStore;
		this.normalizer = normalizer;
		this.normalizedStore = normalizedStore;
	}

	@Override
	public ReplayResult replay(RecordedDataset dataset) {
		Objects.requireNonNull(dataset, "dataset must not be null");
		var results = new ArrayList<ReplayItem>();
		for (var input : dataset.observations()) {
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
			}
			catch (IllegalArgumentException | NormalizationConflictException exception) {
				results.add(new ReplayItem(
						new NormalizedSwapIdentity(input.chain(), input.transactionId(), input.eventId()),
						fingerprint, ReplayStatus.NORMALIZATION_FAILED, java.util.Optional.of(exception.getMessage())));
			}
		}
		return new ReplayResult(results);
	}

	@Override
	@Transactional
	public DatasetSnapshot finalizeDataset(FinalizeDatasetRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		if (request.canonicalizationVersion() == null || request.canonicalizationVersion().isBlank()) {
			throw new IllegalArgumentException("canonicalizationVersion must not be blank");
		}
		var cutoff = microseconds(request.cutoff());
		var observations = request.members().stream()
				.distinct()
				.map(identity -> normalizedStore.find(identity)
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
		return normalizedStore.observations(new PointInTimeQuery(
				query.asset(), microseconds(query.fromInclusive()), microseconds(query.toInclusive()),
				microseconds(query.cutoff()), query.datasetFingerprint()));
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
