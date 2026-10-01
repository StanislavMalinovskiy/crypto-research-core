package io.cryptoresearch.evaluation.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.cryptoresearch.evaluation.api.EvaluationApi.EntryOutcome;
import io.cryptoresearch.evaluation.api.EvaluationApi.EvaluationReport;
import io.cryptoresearch.evaluation.api.EvaluationApi.PricingStatus;
import io.cryptoresearch.evaluation.api.EvaluationApi.VersionedEvaluationReport;
import io.cryptoresearch.evaluation.api.EvaluationApi.VersionedEvaluationRequest;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.PointInTimeQuery;
import io.cryptoresearch.marketdata.api.MarketDataApi.RevisionReference;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedSnapshotEvidence;
import io.cryptoresearch.signal.api.SignalApi;
import io.cryptoresearch.signal.api.SignalApi.AcceptedSignalSnapshot;

@Service
public class VersionedFirstSignalEvaluationService {

	private final MarketDataApi marketData;
	private final SignalApi signals;
	private final EntryValuation valuation;
	private final VersionedEvaluationWriter writer;

	public VersionedFirstSignalEvaluationService(MarketDataApi marketData, SignalApi signals,
			EntryValuation valuation, VersionedEvaluationWriter writer) {
		this.marketData = marketData;
		this.signals = signals;
		this.valuation = valuation;
		this.writer = writer;
	}

	public VersionedEvaluationReport evaluate(VersionedEvaluationRequest request) {
		if (request == null || !"1h".equals(request.horizon())) {
			throw new IllegalArgumentException("Versioned first-slice evaluation requires 1h horizon");
		}
		var signal = signals.versionedAcceptedSignal(request.signalId())
				.orElseThrow(() -> new IllegalArgumentException("Unknown versioned accepted signal"));
		if (!signal.decisionDatasetFingerprint().equals(request.decisionDatasetFingerprint())
				|| !request.decisionDatasetFingerprint().equals(request.provenance().datasetFingerprint())) {
			throw new IllegalArgumentException("Decision fingerprint must match the accepted signal");
		}
		var decision = marketData.versionedSnapshotEvidence(request.decisionDatasetFingerprint())
				.orElseThrow(() -> new IllegalArgumentException("Unknown versioned decision snapshot"));
		if (!decision.knowledgeCutoff().equals(signal.decisionCutoff())) {
			throw new IllegalArgumentException("Decision cutoff must match accepted signal");
		}
		var evaluation = marketData.versionedSnapshotEvidence(request.evaluationDatasetFingerprint())
				.orElseThrow(() -> new IllegalArgumentException("Unknown versioned evaluation snapshot"));
		var cutoff = request.provenance().evaluationCutoff().truncatedTo(ChronoUnit.MICROS);
		if (!evaluation.knowledgeCutoff().equals(cutoff)) {
			throw new IllegalArgumentException("Evaluation cutoff must match frozen snapshot");
		}
		var provenance = new io.cryptoresearch.evaluation.api.EvaluationApi.RunProvenance(
				request.provenance().buildIdentity(), request.provenance().sourceRevision(),
				request.provenance().sourceDirty(), request.provenance().algorithmVersion(),
				request.provenance().configurationFingerprint(), request.decisionDatasetFingerprint(),
				cutoff, request.provenance().seed());
		var accepted = signal.signal();
		var entry = entryCandidates(accepted, evaluation.fingerprint(), cutoff);
		var horizon = horizonCandidates(accepted, evaluation.fingerprint(), cutoff);
		var provisional = valuation.evaluate("pending", accepted, request.horizon(), entry, horizon);
		var entryRevision = revision(evaluation, provisional.entryPrice());
		var horizonRevision = revision(evaluation, provisional.horizonPrice());
		var runId = runId(request, provenance, signal.signal().signalId());
		var outcomeId = outcomeId(runId, provisional, entryRevision, horizonRevision);
		var outcome = new EntryOutcome(outcomeId, provisional.signalId(), provisional.horizon(),
				provisional.status(), provisional.missingPriceReason(), provisional.entryPrice(),
				provisional.horizonPrice(), provisional.grossReturn(), provisional.friction(), provisional.netReturn());
		var priced = outcome.status() == PricingStatus.PRICED ? 1 : 0;
		var average = outcome.netReturn().map(v -> v.setScale(8, RoundingMode.HALF_EVEN));
		var reportId = new EvaluationFingerprint().field("contract", "versioned-first-report-v1")
				.field("run", runId).field("decisionDataset", request.decisionDatasetFingerprint())
				.field("evaluationDataset", request.evaluationDatasetFingerprint())
				.field("outcome", outcomeId).field("family", accepted.family())
				.field("pricedCount", Integer.toString(priced))
				.field("unpricedCount", Integer.toString(1 - priced))
				.field("averageNetReturn", average.map(BigDecimal::toPlainString).orElse(""))
				.finish();
		var report = new EvaluationReport(runId, reportId, provenance, accepted.family(), 1,
				priced, 1 - priced, average, List.of(outcome));
		return writer.persist(new VersionedEvaluationReport(report, request.decisionDatasetFingerprint(),
				request.evaluationDatasetFingerprint(), "DUAL_EVIDENCE_V1", decision, evaluation),
				entryRevision, horizonRevision);
	}

	private List<MarketObservation> entryCandidates(AcceptedSignalSnapshot signal,
			String fingerprint, Instant cutoff) {
		if (!cutoff.isAfter(signal.availableAt())) {
			return List.of();
		}
		var horizonStart = signal.availableAt().plus(Duration.ofHours(1));
		var end = cutoff.isBefore(horizonStart) ? cutoff : horizonStart;
		return marketData.versionedObservations(new PointInTimeQuery(signal.asset(), signal.availableAt(),
				end, cutoff, Optional.of(fingerprint)));
	}

	private List<MarketObservation> horizonCandidates(AcceptedSignalSnapshot signal,
			String fingerprint, Instant cutoff) {
		var start = signal.availableAt().plus(Duration.ofHours(1));
		if (cutoff.isBefore(start)) {
			return List.of();
		}
		return marketData.versionedObservations(new PointInTimeQuery(signal.asset(), start,
				cutoff, cutoff, Optional.of(fingerprint)));
	}

	private String revision(VersionedSnapshotEvidence snapshot, Optional<MarketObservation> observation) {
		if (observation.isEmpty()) {
			return null;
		}
		return snapshot.members().stream().filter(ref -> ref.kind() == FactKind.SWAP
				&& ref.canonicalIdentity().equals(observation.orElseThrow().identity()))
				.map(RevisionReference::revisionKey).findFirst()
				.orElseThrow(() -> new IllegalStateException("Valuation observation lacks exact snapshot revision"));
	}

	private String runId(VersionedEvaluationRequest request,
			io.cryptoresearch.evaluation.api.EvaluationApi.RunProvenance provenance, String signalId) {
		return new EvaluationFingerprint().field("contract", "versioned-first-signal-evaluation-run-v1")
				.field("signal", signalId).field("horizon", request.horizon())
				.field("build", provenance.buildIdentity()).field("source", provenance.sourceRevision())
				.field("dirty", Boolean.toString(provenance.sourceDirty()))
				.field("algorithm", provenance.algorithmVersion())
				.field("configuration", provenance.configurationFingerprint())
				.field("decisionDataset", request.decisionDatasetFingerprint())
				.field("evaluationDataset", request.evaluationDatasetFingerprint())
				.field("evaluationCutoff", provenance.evaluationCutoff().toString())
				.field("seed", provenance.seed().isPresent() ? Long.toString(provenance.seed().getAsLong()) : "")
				.finish();
	}

	private String outcomeId(String runId, EntryOutcome outcome,
			String entryRevision, String horizonRevision) {
		var hash = new EvaluationFingerprint().field("contract", "versioned-entry-outcome-v1")
				.field("run", runId).field("signal", outcome.signalId())
				.field("horizon", outcome.horizon()).field("status", outcome.status().name())
				.field("missing", outcome.missingPriceReason().orElse(""))
				.field("entryRevision", entryRevision == null ? "" : entryRevision)
				.field("horizonRevision", horizonRevision == null ? "" : horizonRevision);
		appendPrice(hash, "entry", outcome.entryPrice());
		appendPrice(hash, "horizon", outcome.horizonPrice());
		return hash.field("gross", outcome.grossReturn().map(BigDecimal::toPlainString).orElse(""))
				.field("friction", outcome.friction().map(BigDecimal::toPlainString).orElse(""))
				.field("net", outcome.netReturn().map(BigDecimal::toPlainString).orElse(""))
				.finish();
	}

	private void appendPrice(EvaluationFingerprint hash, String prefix, Optional<MarketObservation> observation) {
		if (observation.isEmpty()) {
			hash.field(prefix, "");
			return;
		}
		var value = observation.orElseThrow();
		hash.field(prefix + "Source", value.identity().chain().value() + "/"
				+ value.identity().transactionId().value() + "/" + value.identity().eventId().locator())
				.field(prefix + "PriceUsd", value.priceUsd().toPlainString())
				.field(prefix + "LiquidityUsd", value.liquidityUsd().toPlainString())
				.field(prefix + "Confidence", value.confidence().toPlainString())
				.field(prefix + "Provider", value.provider())
				.field(prefix + "ObservedAt", value.observedAt().toString());
	}
}
