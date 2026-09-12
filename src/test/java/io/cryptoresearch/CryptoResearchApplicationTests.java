package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class CryptoResearchApplicationTests {

	private static final Map<String, Set<String>> EXPECTED_DEPENDENCIES = Map.of(
			"kernel", Set.of(),
			"governance", Set.of("kernel::api"),
			"marketdata", Set.of("kernel::api"),
			"risk", Set.of("kernel::api", "marketdata::api"),
			"wallet", Set.of("kernel::api", "marketdata::api"),
			"strategy", Set.of("kernel::api", "marketdata::api", "risk::api", "wallet::api"),
			"measurement", Set.of("kernel::api", "marketdata::api", "strategy::api", "governance::api"),
			"research", Set.of("marketdata::api", "risk::api", "wallet::api", "strategy::api", "measurement::api"));

	@Test
	void verifiesApplicationModuleBoundaries() {
		var modules = ApplicationModules.of(CryptoResearchApplication.class);

		modules.verify();
		assertThat(moduleNames(modules)).containsExactlyInAnyOrder(
				"kernel", "governance", "marketdata", "risk",
				"wallet", "strategy", "measurement", "research");
	}

	@Test
	void moduleDescriptorsDeclareDocumentedDependencyGraph() throws ClassNotFoundException {
		for (var entry : EXPECTED_DEPENDENCIES.entrySet()) {
			var modulePackage = Class.forName("io.cryptoresearch." + entry.getKey() + ".package-info").getPackage();
			var descriptor = modulePackage.getAnnotation(ApplicationModule.class);

			assertThat(descriptor).as("descriptor for %s", entry.getKey()).isNotNull();
			assertThat(Set.of(descriptor.allowedDependencies()))
					.as("allowed dependencies for %s", entry.getKey())
					.isEqualTo(entry.getValue());
		}
	}

	private Set<String> moduleNames(ApplicationModules modules) {
		return modules.stream()
				.map(module -> module.getIdentifier().toString())
				.collect(Collectors.toUnmodifiableSet());
	}

}
