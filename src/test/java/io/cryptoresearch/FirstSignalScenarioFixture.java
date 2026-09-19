package io.cryptoresearch;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.cryptoresearch.evaluation.api.EvaluationApi.RunProvenance;
import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedDataset;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedSwapInput;
import io.cryptoresearch.risk.api.RiskApi.AssetLifecycle;
import io.cryptoresearch.risk.api.RiskApi.RiskFacts;

final class FirstSignalScenarioFixture {

	static final String RESOURCE = "/fixtures/first-signal-evaluation.json";
	static final List<String> ROLES = List.of("BASELINE", "DECISION", "ENTRY", "HORIZON");
	private static final Set<String> ROOT_FIELDS = Set.of(
			"fixtureVersion", "canonicalizationVersion", "decisionCutoff", "evaluationCutoff",
			"riskFacts", "provenance", "observations");
	private static final Set<String> RISK_FIELDS = Set.of(
			"manipulationFlags", "lifecycle", "liquidityUsd", "evidenceVersion");
	private static final Set<String> PROVENANCE_FIELDS = Set.of(
			"buildIdentity", "sourceRevision", "sourceDirty", "algorithmVersion", "configurationFingerprint");
	private static final Set<String> OBSERVATION_FIELDS = Set.of(
			"role", "chainId", "transactionValue", "eventLocator", "provider", "blockPosition", "blockHash",
			"sourceEventTime", "observedAt", "transformationVersion", "payload");
	private static final Set<String> PAYLOAD_FIELDS = Set.of(
			"schemaVersion", "asset", "wallet", "side", "tokenQuantity", "nativeQuantity",
			"priceUsd", "liquidityUsd", "confidence", "venue");

	private final ObjectMapper objectMapper = new ObjectMapper();

	Scenario load() {
		try (var stream = FirstSignalScenarioFixture.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException("Missing fixture " + RESOURCE);
			}
			return parse(objectMapper.readTree(stream));
		}
		catch (IOException exception) {
			throw new IllegalArgumentException("Cannot read fixture", exception);
		}
	}

	Scenario parse(JsonNode root) {
		requireFields(root, ROOT_FIELDS, "root");
		requireText(root, "fixtureVersion");
		requireText(root, "canonicalizationVersion");
		var decisionCutoff = Instant.parse(requireText(root, "decisionCutoff"));
		var evaluationCutoff = Instant.parse(requireText(root, "evaluationCutoff"));
		if (!evaluationCutoff.isAfter(decisionCutoff)) {
			throw new IllegalArgumentException("evaluationCutoff must be after decisionCutoff");
		}

		var riskNode = root.required("riskFacts");
		requireFields(riskNode, RISK_FIELDS, "riskFacts");
		var provenanceNode = root.required("provenance");
		requireFields(provenanceNode, PROVENANCE_FIELDS, "provenance");
		var observationsNode = root.required("observations");
		if (!observationsNode.isArray() || observationsNode.size() != ROLES.size()) {
			throw new IllegalArgumentException("observations must contain exactly four entries");
		}

		var observations = new ArrayList<RecordedSwapInput>();
		AssetId asset = null;
		for (var index = 0; index < observationsNode.size(); index++) {
			var observation = observationsNode.get(index);
			requireFields(observation, OBSERVATION_FIELDS, "observation");
			if (!ROLES.get(index).equals(requireText(observation, "role"))) {
				throw new IllegalArgumentException("observation roles must retain semantic order " + ROLES);
			}
			var payload = observation.required("payload");
			requireFields(payload, PAYLOAD_FIELDS, "payload");
			requireScale(payload, "priceUsd", 18);
			requireScale(payload, "liquidityUsd", 8);
			requireScale(payload, "confidence", 4);
			requireInteger(payload, "tokenQuantity");
			requireInteger(payload, "nativeQuantity");

			var chain = new ChainId(requireText(observation, "chainId"));
			var transactionId = new TransactionId(chain, requireText(observation, "transactionValue"));
			var eventId = new EventId(transactionId, requireText(observation, "eventLocator"));
			var currentAsset = new AssetId(chain, requireText(payload, "asset"));
			if (asset != null && !asset.equals(currentAsset)) {
				throw new IllegalArgumentException("all observations must describe the same asset");
			}
			asset = currentAsset;
			observations.add(new RecordedSwapInput(
					chain,
					transactionId,
					eventId,
					requireText(observation, "provider"),
					new BlockPosition(chain, exactLong(observation.required("blockPosition"), "blockPosition")),
					Optional.of(requireText(observation, "blockHash")),
					Optional.of(Instant.parse(requireText(observation, "sourceEventTime"))),
					Instant.parse(requireText(observation, "observedAt")),
					payload.toString(),
					requireText(observation, "transformationVersion")));
		}

		var riskFacts = new RiskFacts(
				asset,
				decisionCutoff,
				exactInt(riskNode.required("manipulationFlags"), "manipulationFlags"),
				AssetLifecycle.valueOf(requireText(riskNode, "lifecycle")),
				requireScale(riskNode, "liquidityUsd", 8),
				requireText(riskNode, "evidenceVersion"));
		return new Scenario(
				new RecordedDataset(requireText(root, "fixtureVersion"), observations),
				asset,
				decisionCutoff,
				evaluationCutoff,
				requireText(root, "canonicalizationVersion"),
				riskFacts,
				requireText(provenanceNode, "buildIdentity"),
				requireText(provenanceNode, "sourceRevision"),
				provenanceNode.required("sourceDirty").booleanValue(),
				requireText(provenanceNode, "algorithmVersion"),
				requireFingerprint(provenanceNode, "configurationFingerprint"));
	}

	JsonNode rawTree() {
		try (var stream = FirstSignalScenarioFixture.class.getResourceAsStream(RESOURCE)) {
			return objectMapper.readTree(stream);
		}
		catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private void requireFields(JsonNode node, Set<String> expected, String description) {
		var actual = new LinkedHashSet<String>();
		actual.addAll(node.propertyNames());
		if (!actual.equals(expected)) {
			throw new IllegalArgumentException(description + " fields differ; expected=" + expected + ", actual=" + actual);
		}
	}

	private String requireText(JsonNode node, String field) {
		var value = node.required(field);
		if (!value.isTextual() || value.textValue().isBlank()) {
			throw new IllegalArgumentException(field + " must be non-blank text");
		}
		return value.textValue();
	}

	private BigDecimal requireScale(JsonNode node, String field, int scale) {
		var text = requireText(node, field);
		var value = new BigDecimal(text);
		if (value.scale() != scale || !value.toPlainString().equals(text)) {
			throw new IllegalArgumentException(field + " must use exact scale " + scale);
		}
		return value;
	}

	private void requireInteger(JsonNode node, String field) {
		var text = requireText(node, field);
		if (!text.matches("0|[1-9][0-9]*")) {
			throw new IllegalArgumentException(field + " must be an exact non-negative integer string");
		}
	}

	private long exactLong(JsonNode node, String field) {
		if (!node.isIntegralNumber() || !node.canConvertToLong()) {
			throw new IllegalArgumentException(field + " must be an exact 64-bit integer");
		}
		return node.longValue();
	}

	private int exactInt(JsonNode node, String field) {
		if (!node.isIntegralNumber() || !node.canConvertToInt()) {
			throw new IllegalArgumentException(field + " must be an exact 32-bit integer");
		}
		return node.intValue();
	}

	private String requireFingerprint(JsonNode node, String field) {
		var value = requireText(node, field);
		if (!value.matches("sha256:[0-9a-f]{64}")) {
			throw new IllegalArgumentException(field + " must be an algorithm-qualified SHA-256 fingerprint");
		}
		return value;
	}

	record Scenario(
			RecordedDataset dataset,
			AssetId asset,
			Instant decisionCutoff,
			Instant evaluationCutoff,
			String canonicalizationVersion,
			RiskFacts riskFacts,
			String buildIdentity,
			String sourceRevision,
			boolean sourceDirty,
			String evaluationAlgorithmVersion,
			String configurationFingerprint) {

		RunProvenance provenance(String datasetFingerprint, Instant cutoff) {
			return new RunProvenance(
					buildIdentity,
					sourceRevision,
					sourceDirty,
					evaluationAlgorithmVersion,
					configurationFingerprint,
					datasetFingerprint,
					cutoff,
					OptionalLong.empty());
		}
	}
}
