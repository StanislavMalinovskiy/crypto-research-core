package io.cryptoresearch.evaluation.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.cryptoresearch.evaluation.api.EvaluationApi;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.PointInTimeQuery;
import io.cryptoresearch.signal.api.SignalApi;
import io.cryptoresearch.signal.api.SignalApi.AcceptedSignalSnapshot;

@Service
public class FirstSignalEvaluationService implements EvaluationApi {

	private final MarketDataApi marketData;
	private final SignalApi signalApi;
	private final EntryValuation valuation;
	private final EvaluationWriter writer;

	public FirstSignalEvaluationService(
			MarketDataApi marketData,
			SignalApi signalApi,
			EntryValuation valuation,
			EvaluationWriter writer) {
		this.marketData = marketData;
		this.signalApi = signalApi;
		this.valuation = valuation;
		this.writer = writer;
	}

	@Override
	public EvaluationReport evaluate(EvaluationRequest request) {
		request = normalize(request);
		validate(request);
		var signalId = request.signalId();
		var signal = signalApi.acceptedSignal(signalId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown accepted signal: " + signalId));
		if (!signal.datasetFingerprint().equals(request.provenance().datasetFingerprint())) {
			throw new IllegalArgumentException("run dataset fingerprint must match the immutable signal snapshot");
		}

		var runId = runId(request, signal);
		var entryCandidates = entryCandidates(signal, request.provenance());
		var horizonCandidates = horizonCandidates(signal, request.provenance());
		var provisional = valuation.evaluate(
				"pending", signal, request.horizon(), entryCandidates, horizonCandidates);
		var outcomeId = outcomeFingerprint(runId, provisional);
		var outcome = new EntryOutcome(
				outcomeId, provisional.signalId(), provisional.horizon(), provisional.status(),
				provisional.missingPriceReason(), provisional.entryPrice(), provisional.horizonPrice(),
				provisional.grossReturn(), provisional.friction(), provisional.netReturn());

		var priced = outcome.status() == PricingStatus.PRICED ? 1 : 0;
		var unpriced = priced == 0 ? 1 : 0;
		var average = outcome.netReturn().map(value -> value.setScale(8, RoundingMode.HALF_EVEN));
		var reportFingerprint = reportFingerprint(runId, request.provenance(), signal, outcome, average);
		return writer.persist(new EvaluationReport(
				runId,
				reportFingerprint,
				request.provenance(),
				signal.family(),
				1,
				priced,
				unpriced,
				average,
				List.of(outcome)));
	}

	private List<MarketObservation> entryCandidates(AcceptedSignalSnapshot signal, RunProvenance provenance) {
		if (!provenance.evaluationCutoff().isAfter(signal.availableAt())) {
			return List.of();
		}
		var horizonStart = signal.availableAt().plus(Duration.ofHours(1));
		var entryEnd = provenance.evaluationCutoff().isBefore(horizonStart)
				? provenance.evaluationCutoff() : horizonStart;
		return marketData.observations(new PointInTimeQuery(
				signal.asset(), signal.availableAt(), entryEnd, provenance.evaluationCutoff(),
				Optional.of(provenance.datasetFingerprint())));
	}

	private List<MarketObservation> horizonCandidates(AcceptedSignalSnapshot signal, RunProvenance provenance) {
		var horizonStart = signal.availableAt().plus(Duration.ofHours(1));
		if (provenance.evaluationCutoff().isBefore(horizonStart)) {
			return List.of();
		}
		return marketData.observations(new PointInTimeQuery(
				signal.asset(), horizonStart, provenance.evaluationCutoff(), provenance.evaluationCutoff(),
				Optional.of(provenance.datasetFingerprint())));
	}

	private String runId(EvaluationRequest request, AcceptedSignalSnapshot signal) {
		var provenance = request.provenance();
		return new EvaluationFingerprint()
				.field("contract", "first-signal-evaluation-run")
				.field("signalId", signal.signalId())
				.field("horizon", request.horizon())
				.field("buildIdentity", provenance.buildIdentity())
				.field("sourceRevision", provenance.sourceRevision())
				.field("sourceDirty", Boolean.toString(provenance.sourceDirty()))
				.field("algorithmVersion", provenance.algorithmVersion())
				.field("configurationFingerprint", provenance.configurationFingerprint())
				.field("datasetFingerprint", provenance.datasetFingerprint())
				.field("evaluationCutoff", provenance.evaluationCutoff().toString())
				.field("seed", provenance.seed().isPresent() ? Long.toString(provenance.seed().getAsLong()) : "")
				.finish();
	}

	private String outcomeFingerprint(String runId, EntryOutcome outcome) {
		var fingerprint = new EvaluationFingerprint()
				.field("contract", "entry-outcome-v1")
				.field("runId", runId)
				.field("signalId", outcome.signalId())
				.field("horizon", outcome.horizon())
				.field("status", outcome.status().name())
				.field("missingReason", outcome.missingPriceReason().orElse(""));
		appendPrice(fingerprint, "entry", outcome.entryPrice());
		appendPrice(fingerprint, "horizon", outcome.horizonPrice());
		fingerprint
				.field("grossReturn", decimal(outcome.grossReturn()))
				.field("friction", decimal(outcome.friction()))
				.field("netReturn", decimal(outcome.netReturn()));
		return fingerprint.finish();
	}

	private String reportFingerprint(
			String runId,
			RunProvenance provenance,
			AcceptedSignalSnapshot signal,
			EntryOutcome outcome,
			Optional<BigDecimal> average) {
		return new EvaluationFingerprint()
				.field("contract", "first-evidence-report-v1")
				.field("runId", runId)
				.field("buildIdentity", provenance.buildIdentity())
				.field("sourceRevision", provenance.sourceRevision())
				.field("sourceDirty", Boolean.toString(provenance.sourceDirty()))
				.field("algorithmVersion", provenance.algorithmVersion())
				.field("configurationFingerprint", provenance.configurationFingerprint())
				.field("datasetFingerprint", provenance.datasetFingerprint())
				.field("evaluationCutoff", provenance.evaluationCutoff().toString())
				.field("family", signal.family())
				.field("signalScore", Integer.toString(signal.score()))
				.field("outcomeId", outcome.outcomeId())
				.field("pricedCount", outcome.status() == PricingStatus.PRICED ? "1" : "0")
				.field("unpricedCount", outcome.status() == PricingStatus.UNPRICED ? "1" : "0")
				.field("averageNetReturn", decimal(average))
				.finish();
	}

	private void appendPrice(
			EvaluationFingerprint fingerprint, String prefix, Optional<MarketObservation> observation) {
		if (observation.isEmpty()) {
			fingerprint.field(prefix, "");
			return;
		}
		var value = observation.orElseThrow();
		fingerprint
				.field(prefix + "Identity", value.identity().chain().value() + "/"
						+ value.identity().transactionId().value() + "/" + value.identity().eventId().locator())
				.field(prefix + "PriceUsd", value.priceUsd().setScale(18, RoundingMode.UNNECESSARY).toPlainString())
				.field(prefix + "LiquidityUsd", value.liquidityUsd().setScale(8, RoundingMode.UNNECESSARY).toPlainString())
				.field(prefix + "Confidence", value.confidence().setScale(4, RoundingMode.UNNECESSARY).toPlainString())
				.field(prefix + "Provider", value.provider())
				.field(prefix + "ObservedAt", value.observedAt().toString());
	}

	private String decimal(Optional<BigDecimal> value) {
		return value.map(decimal -> decimal.setScale(8, RoundingMode.UNNECESSARY).toPlainString()).orElse("");
	}

	private EvaluationRequest normalize(EvaluationRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		var provenance = Objects.requireNonNull(request.provenance(), "provenance must not be null");
		var normalizedProvenance = new RunProvenance(
				provenance.buildIdentity(), provenance.sourceRevision(), provenance.sourceDirty(),
				provenance.algorithmVersion(), provenance.configurationFingerprint(), provenance.datasetFingerprint(),
				Objects.requireNonNull(provenance.evaluationCutoff(), "evaluationCutoff must not be null")
						.truncatedTo(ChronoUnit.MICROS),
				provenance.seed());
		return new EvaluationRequest(request.signalId(), request.horizon(), normalizedProvenance);
	}

	private void validate(EvaluationRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		if (request.signalId() == null || request.signalId().isBlank()) {
			throw new IllegalArgumentException("signalId must not be blank");
		}
		if (!"1h".equals(request.horizon())) {
			throw new IllegalArgumentException("Only the 1h horizon is supported by the first slice");
		}
		var provenance = Objects.requireNonNull(request.provenance(), "provenance must not be null");
		requireText(provenance.buildIdentity(), "buildIdentity");
		requireText(provenance.sourceRevision(), "sourceRevision");
		requireText(provenance.algorithmVersion(), "algorithmVersion");
		requireFingerprint(provenance.configurationFingerprint(), "configurationFingerprint");
		requireFingerprint(provenance.datasetFingerprint(), "datasetFingerprint");
		Objects.requireNonNull(provenance.evaluationCutoff(), "evaluationCutoff must not be null");
	}

	private void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
	}

	private void requireFingerprint(String value, String field) {
		if (value == null || !value.matches("sha256:[0-9a-f]{64}")) {
			throw new IllegalArgumentException(field + " must be an algorithm-qualified SHA-256 fingerprint");
		}
	}
}
