package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class FirstSignalScenarioFixtureTest {

	private final FirstSignalScenarioFixture fixture = new FirstSignalScenarioFixture();

	@Test
	void loadsExactFourObservationScenarioAndCompleteProvenance() {
		var scenario = fixture.load();

		assertThat(scenario.dataset().observations()).hasSize(4);
		assertThat(scenario.dataset().observations())
				.extracting(observation -> observation.observedAt())
				.containsExactly(
						scenario.decisionCutoff().minusSeconds(3600),
						scenario.decisionCutoff(),
						scenario.decisionCutoff().plusSeconds(1),
						scenario.evaluationCutoff());
		assertThat(scenario.riskFacts().liquidityUsd()).isEqualByComparingTo("80000.00000000");
		assertThat(scenario.provenance("sha256:" + "2".repeat(64), scenario.evaluationCutoff()))
				.extracting("buildIdentity", "sourceRevision", "algorithmVersion", "configurationFingerprint")
				.doesNotContainNull();
	}

	@Test
	void rejectsMissingExtraReorderedSemanticAndInexactNumericEvidence() {
		var missing = (ObjectNode) fixture.rawTree();
		missing.remove("riskFacts");
		assertThatThrownBy(() -> fixture.parse(missing)).isInstanceOf(IllegalArgumentException.class);

		var extra = (ObjectNode) fixture.rawTree();
		((ObjectNode) extra.withArray("observations").get(0).get("payload")).put("fixtureShortcut", true);
		assertThatThrownBy(() -> fixture.parse(extra)).isInstanceOf(IllegalArgumentException.class);

		var reordered = (ObjectNode) fixture.rawTree();
		var observations = (ArrayNode) reordered.withArray("observations");
		var values = StreamSupport.stream(observations.spliterator(), false).map(node -> node.deepCopy()).toList();
		observations.removeAll().add(values.get(1)).add(values.get(0)).add(values.get(2)).add(values.get(3));
		assertThatThrownBy(() -> fixture.parse(reordered))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("semantic order");

		var inexact = (ObjectNode) fixture.rawTree();
		((ObjectNode) inexact.withArray("observations").get(2).get("payload")).put("priceUsd", "1.0");
		assertThatThrownBy(() -> fixture.parse(inexact))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("exact scale 18");
	}
}
