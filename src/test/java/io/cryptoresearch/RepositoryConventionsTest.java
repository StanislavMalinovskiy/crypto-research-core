package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class RepositoryConventionsTest {

	private static final Pattern MARKDOWN_LINK = Pattern.compile("!?\\[[^]]*]\\(([^)\\s]+)[^)]*\\)");
	private static final List<String> EXPORT_MARKERS = List.of("app.notion.com", "Exported from Notion", "Экспортировано из Notion");
	private static final List<String> LEGACY_MODULE_NAMES = List.of(
			"provider-api", "provider-helius", "provider-bitquery", "ingest-solana", "strategy-plugins");
	private static final List<String> LEGACY_ACTIVE_TERMS = List.of("venue-gate");
	private static final List<String> LEGACY_ENTITY_NAMES = List.of(
			"paper_trades", "paper_fills", "owner_clusters", "risk_filter_decisions",
			"execution_simulations", "venue_policy_decisions");
	private static final Set<String> EXPECTED_MODULES = Set.of(
			"kernel", "marketdata", "risk", "wallet", "signal", "evaluation");
	private static final List<String> REMOVED_MODULE_REFERENCES = List.of(
			"`governance`", "`strategy`", "`measurement`", "`research`");
	private static final List<String> TOPOLOGY_DOCUMENTS = List.of(
			"AGENTS.md", "README.md", "docs/PROJECT_SUMMARY.md", "docs/ARCHITECTURE.md",
			"docs/TECH_STACK.md", "docs/modules/README.md");

	private final Path repositoryRoot = Path.of("").toAbsolutePath().normalize();
	private final Path moduleRoot = repositoryRoot.resolve("src/main/java/io/cryptoresearch");

	@Test
	void markdownHasNoExportMarkersOrBrokenRelativeLinks() throws IOException {
		var violations = new ArrayList<String>();

		for (var markdown : markdownFiles()) {
			var content = Files.readString(markdown);
			checkExportMarkers(markdown, content, violations);
			checkRelativeLinks(markdown, content, violations);
		}

		assertThat(violations).isEmpty();
	}

	@Test
	void glossaryDoesNotPresentLegacyArchitectureAsCurrent() throws IOException {
		var violations = new ArrayList<String>();
		var glossary = repositoryRoot.resolve("docs/GLOSSARY.md");
		var content = Files.readString(glossary);

		LEGACY_MODULE_NAMES.stream()
				.filter(content::contains)
				.map(name -> "docs/GLOSSARY.md contains legacy module name: " + name)
				.forEach(violations::add);
		LEGACY_ENTITY_NAMES.forEach(name -> checkLegacyEntity(content, name, violations));

		assertThat(violations).isEmpty();
	}

	@Test
	void activeDocumentationDoesNotUseLegacyArchitectureTerms() throws IOException {
		var violations = new ArrayList<String>();

		for (var markdown : activeDocumentationFiles()) {
			var content = Files.readString(markdown).toLowerCase();
			LEGACY_ACTIVE_TERMS.stream()
					.filter(content::contains)
					.map(term -> relative(markdown) + " contains legacy architecture term: " + term)
					.forEach(violations::add);
		}

		assertThat(violations).isEmpty();
	}

	@Test
	void qualityGateWorkflowHasDurableContract() throws IOException {
		var workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml");
		var content = Files.readString(workflow);

		assertThat(content)
				.contains("name: quality-gate", "  quality-gate:", "    name: quality-gate")
				.contains("runs-on: ubuntu-24.04")
				.contains("./mvnw clean verify")
				.contains("openspec validate --all --strict --no-interactive", "openspec doctor")
				.contains("actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02")
				.contains("target/surefire-reports/**", "target/failsafe-reports/**")
				.doesNotContain("run: mvn ", "run: mvn.cmd ");
	}

	@Test
	void engineeringOperatingContractsAreDiscoverable() throws IOException {
		var architecture = Files.readString(repositoryRoot.resolve("docs/ARCHITECTURE.md"));
		var operations = Files.readString(repositoryRoot.resolve("docs/OPERATIONS.md"));
		var testing = Files.readString(repositoryRoot.resolve("docs/TESTING.md"));

		assertThat(architecture)
				.contains("| Simple aggregate CRUD |", "| High-volume write |", "Cross-module SQL")
				.contains("[Operating contract](OPERATIONS.md)", "[Testing strategy](TESTING.md)");
		assertThat(operations)
				.contains("## Configuration and secrets", "## Health semantics", "## Resource budgets")
				.contains("`livenessState` only", "`readinessState`, `db`");
		assertThat(testing)
				.contains("## Test levels", "PostgreSQL integration", "## Maven lifecycle");
	}

	@Test
	void publicApiRootsMatchExpectedModules() throws IOException {
		try (Stream<Path> modules = Files.list(moduleRoot)) {
			var moduleDirectories = modules.filter(Files::isDirectory).toList();
			var moduleNames = moduleDirectories.stream()
					.map(module -> module.getFileName().toString())
					.collect(Collectors.toUnmodifiableSet());
			var apiRoots = moduleDirectories.stream()
					.map(module -> module.resolve("api"))
					.filter(Files::isDirectory)
					.toList();

			assertThat(moduleNames).containsExactlyInAnyOrderElementsOf(EXPECTED_MODULES);
			assertThat(apiRoots).hasSize(EXPECTED_MODULES.size());
			assertThat(apiRoots).allSatisfy(this::containsJavaSource);
		}
	}

	@Test
	void activeTopologyDocumentationUsesExactlyCurrentModules() throws IOException {
		var violations = new ArrayList<String>();

		for (var relativePath : TOPOLOGY_DOCUMENTS) {
			var document = repositoryRoot.resolve(relativePath);
			var content = Files.readString(document);
			EXPECTED_MODULES.stream()
					.map(module -> "`" + module + "`")
					.filter(module -> !content.contains(module))
					.map(module -> relativePath + " does not name current module: " + module)
					.forEach(violations::add);
			REMOVED_MODULE_REFERENCES.stream()
					.filter(content::contains)
					.map(module -> relativePath + " presents removed module as current: " + module)
					.forEach(violations::add);
		}

		try (Stream<Path> pages = Files.list(repositoryRoot.resolve("docs/modules"))) {
			var documentedModules = pages.filter(Files::isRegularFile)
					.map(path -> path.getFileName().toString())
					.filter(name -> name.endsWith(".md"))
					.filter(name -> !name.equals("README.md"))
					.map(name -> name.substring(0, name.length() - 3))
					.collect(Collectors.toUnmodifiableSet());
			assertThat(documentedModules).containsExactlyInAnyOrderElementsOf(EXPECTED_MODULES);
		}

		assertThat(violations).isEmpty();
	}

	private List<Path> markdownFiles() throws IOException {
		try (Stream<Path> paths = Files.walk(repositoryRoot)) {
			return paths.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".md"))
					.filter(path -> !path.startsWith(repositoryRoot.resolve("target")))
					.toList();
		}
	}

	private List<Path> activeDocumentationFiles() throws IOException {
		var docs = repositoryRoot.resolve("docs");
		try (Stream<Path> paths = Files.walk(docs)) {
			return paths.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".md"))
					.filter(path -> !path.startsWith(docs.resolve("archive")))
					.filter(path -> !path.startsWith(docs.resolve("notes")))
					.toList();
		}
	}

	private void checkExportMarkers(Path path, String content, List<String> violations) {
		EXPORT_MARKERS.stream()
				.filter(content::contains)
				.map(marker -> relative(path) + " contains export marker: " + marker)
				.forEach(violations::add);
	}

	private void checkRelativeLinks(Path markdown, String content, List<String> violations) {
		var matcher = MARKDOWN_LINK.matcher(content);

		while (matcher.find()) {
			var target = matcher.group(1).replace("<", "").replace(">", "");
			if (isRelativeFileLink(target) && !resolves(markdown, target)) {
				violations.add(relative(markdown) + " has broken link: " + target);
			}
		}
	}

	private boolean isRelativeFileLink(String target) {
		return !target.startsWith("#") && !URI.create(target).isAbsolute();
	}

	private boolean resolves(Path markdown, String target) {
		var withoutFragment = target.split("[#?]", 2)[0];
		var decoded = URLDecoder.decode(withoutFragment, StandardCharsets.UTF_8);
		return Files.exists(markdown.getParent().resolve(decoded).normalize());
	}

	private void checkLegacyEntity(String content, String name, List<String> violations) {
		content.lines()
				.filter(line -> line.contains("`" + name + "`"))
				.filter(line -> !line.contains("Historical/deferred"))
				.map(line -> "docs/GLOSSARY.md does not locally mark legacy entity: " + name)
				.forEach(violations::add);
	}

	private String relative(Path path) {
		return repositoryRoot.relativize(path).toString();
	}

	private void containsJavaSource(Path apiRoot) {
		try (Stream<Path> paths = Files.walk(apiRoot)) {
			assertThat(paths).anyMatch(path -> path.getFileName().toString().endsWith(".java"));
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot inspect " + relative(apiRoot), exception);
		}
	}
}
