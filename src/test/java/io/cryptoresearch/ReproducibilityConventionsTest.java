package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ReproducibilityConventionsTest {

	private static final List<ForbiddenSourcePattern> FORBIDDEN_PATTERNS = List.of(
			forbid("Instant.now()", "\\bInstant\\s*\\.\\s*now\\s*\\(\\s*\\)"),
			forbid("LocalDate.now()", "\\bLocalDate\\s*\\.\\s*now\\s*\\(\\s*\\)"),
			forbid("LocalDateTime.now()", "\\bLocalDateTime\\s*\\.\\s*now\\s*\\(\\s*\\)"),
			forbid("LocalTime.now()", "\\bLocalTime\\s*\\.\\s*now\\s*\\(\\s*\\)"),
			forbid("OffsetDateTime.now()", "\\bOffsetDateTime\\s*\\.\\s*now\\s*\\(\\s*\\)"),
			forbid("OffsetTime.now()", "\\bOffsetTime\\s*\\.\\s*now\\s*\\(\\s*\\)"),
			forbid("ZonedDateTime.now()", "\\bZonedDateTime\\s*\\.\\s*now\\s*\\(\\s*\\)"),
			forbid("System.currentTimeMillis()", "\\bSystem\\s*\\.\\s*currentTimeMillis\\s*\\(\\s*\\)"),
			forbid("Math.random()", "\\bMath\\s*\\.\\s*random\\s*\\(\\s*\\)"),
			forbid("ThreadLocalRandom.current()", "\\bThreadLocalRandom\\s*\\.\\s*current\\s*\\(\\s*\\)"),
			forbid("RandomGenerator.getDefault()", "\\bRandomGenerator\\s*\\.\\s*getDefault\\s*\\(\\s*\\)"),
			forbid("new Random()", "\\bnew\\s+Random\\s*\\(\\s*\\)"));

	private final Path repositoryRoot = Path.of("").toAbsolutePath().normalize();
	private final Path productionRoot = repositoryRoot.resolve("src/main/java/io/cryptoresearch");

	@Test
	void productionSourcesAvoidImplicitNondeterminism() throws IOException {
		var violations = new ArrayList<String>();

		for (var source : productionJavaFiles()) {
			violations.addAll(violations(source, Files.readString(source)));
		}

		assertThat(violations).isEmpty();
	}

	@Test
	void guardAllowsExplicitTimeAndRandomnessInputs() {
		var source = """
				final class StableRule {
					Instant evaluate(Clock clock, RandomGenerator random) {
						var reference = clock.instant();
						random.nextLong();
						return Instant.now(clock);
					}
				}
				""";

		assertThat(violations(Path.of("StableRule.java"), source)).isEmpty();
	}

	@Test
	void guardRejectsEveryConfiguredPattern() {
		var source = FORBIDDEN_PATTERNS.stream()
				.map(ForbiddenSourcePattern::example)
				.reduce((left, right) -> left + System.lineSeparator() + right)
				.orElseThrow();

		assertThat(violations(Path.of("UnstableRule.java"), source))
				.containsExactlyElementsOf(FORBIDDEN_PATTERNS.stream()
						.map(pattern -> "UnstableRule.java uses implicit nondeterminism: " + pattern.label())
						.toList());
	}

	@Test
	void reproducibilityContractIsDiscoverable() throws IOException {
		var contract = Files.readString(repositoryRoot.resolve("docs/REPRODUCIBILITY.md"));
		var architecture = Files.readString(repositoryRoot.resolve("docs/ARCHITECTURE.md"));
		var adrIndex = Files.readString(repositoryRoot.resolve("docs/adr/README.md"));

		assertThat(contract).contains(
				"## Reproducibility definition", "## UTC and time sources", "## Exact numeric policy",
				"## Run provenance manifest", "## Dataset fingerprint and lineage",
				"## Deterministic ordering and randomness", "## Module ownership");
		assertThat(architecture).contains("[Research reproducibility](REPRODUCIBILITY.md)");
		assertThat(adrIndex).contains("[0009](0009-research-reproducibility.md)");
	}

	private List<Path> productionJavaFiles() throws IOException {
		try (Stream<Path> paths = Files.walk(productionRoot)) {
			return paths.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".java"))
					.toList();
		}
	}

	private List<String> violations(Path source, String content) {
		var displayPath = source.isAbsolute()
				? repositoryRoot.relativize(source).toString()
				: source.toString();

		return FORBIDDEN_PATTERNS.stream()
				.filter(pattern -> pattern.pattern().matcher(content).find())
				.map(pattern -> displayPath + " uses implicit nondeterminism: " + pattern.label())
				.toList();
	}

	private static ForbiddenSourcePattern forbid(String example, String regex) {
		return new ForbiddenSourcePattern(example, Pattern.compile(regex), example);
	}

	private record ForbiddenSourcePattern(String label, Pattern pattern, String example) {
	}
}
