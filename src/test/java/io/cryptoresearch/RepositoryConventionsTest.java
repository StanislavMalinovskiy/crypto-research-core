package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

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
	private static final Pattern DISABLED_TEST_ANNOTATION = Pattern.compile(
			"@\\s*(?:(?:org\\.junit(?:\\.jupiter\\.api)?|junit\\.framework)\\.)?(?:Disabled|Ignore)\\b");
	private static final Pattern LITERAL_FALSE_ASSUMPTION = Pattern.compile(
			"\\b(?:[A-Za-z_$][\\w$]*\\s*\\.\\s*)?assumeTrue\\s*\\(\\s*(?:\\(\\s*)*false\\b"
					+ "|\\b(?:[A-Za-z_$][\\w$]*\\s*\\.\\s*)?assumeFalse\\s*\\(\\s*(?:\\(\\s*)*true\\b");
	private static final Pattern MODEL_REASONING_EFFORT = Pattern.compile(
			"(?m)^\\s*model_reasoning_effort\\s*=\\s*\"([^\"]+)\"\\s*$");
	private static final Pattern STATUS_TOKEN = Pattern.compile(
			"\\b(?:SKELETON_READY|IMPL_DONE|TEST_SUSPECT|BLOCKED|RED_CANDIDATE|EVIDENCE_CANDIDATE|SPEC_INCOMPLETE|"
					+ "RESEARCH_DONE|INCONCLUSIVE|CODE_WRONG|TEST_WRONG|SPEC_AMBIGUOUS|AUDIT_FAILED|APPROVE|"
					+ "TESTS_RED_CONFIRMED|TESTS_GREEN_CONFIRMED)\\b");
	private static final Set<String> LEGACY_TESTER_STATUSES = Set.of(
			"TESTS_RED_CONFIRMED", "TESTS_GREEN_CONFIRMED");
	private static final Map<String, Set<String>> ROLE_OUTPUT_STATUSES = Map.of(
			"developer", Set.of("SKELETON_READY", "IMPL_DONE", "TEST_SUSPECT", "BLOCKED"),
			"tester", Set.of("RED_CANDIDATE", "EVIDENCE_CANDIDATE", "SPEC_INCOMPLETE", "TEST_SUSPECT", "BLOCKED"),
			"researcher", Set.of("RESEARCH_DONE", "INCONCLUSIVE", "BLOCKED"));
	private static final Set<String> REVIEWER_ADJUDICATE_STATUSES =
			Set.of("CODE_WRONG", "TEST_WRONG", "SPEC_AMBIGUOUS");
	private static final Set<String> REVIEWER_AUDIT_STATUSES = Set.of("AUDIT_FAILED", "APPROVE");
	private static final Set<String> MAVEN_SELECTOR_ELEMENTS = Set.of(
			"includes", "include", "excludes", "exclude", "groups", "excludedGroups",
			"includeTags", "excludeTags", "test", "itTest", "it.test", "suiteXmlFiles");
	private static final Set<String> MAVEN_SKIP_ELEMENTS = Set.of(
			"skip", "skipTests", "skipITs", "maven.test.skip");

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
				.contains("openspec validate --all --strict --no-interactive", "openspec doctor")
				.contains("actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02")
				.contains("target/surefire-reports/**", "target/failsafe-reports/**")
				.doesNotContain("run: mvn ", "run: mvn.cmd ");
		assertThat(qualityGateViolations(content)).isEmpty();
	}

	@Test
	void qualityGateInspectionAcceptsOnlyTheCompleteCommandWithoutNarrowingEnvironment() {
		var complete = """
				steps:
				  - name: Verify test integrity
				    run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1
				  - name: Verify Maven build
				    run: ./mvnw clean verify
				""";
		var skipFlag = complete.replace("./mvnw clean verify", "./mvnw -DskipTests clean verify");
		var selector = complete.replace("./mvnw clean verify", "./mvnw clean verify -Dtest=OnlyThisTest");
		var narrowingEnvironment = complete.replace(
				"    run:", "    env:\n      MAVEN_ARGS: -DskipITs\n    run:");
		var foldedNarrowingEnvironment = complete.replace(
				"    run:", "    env:\n      MAVEN_ARGS: >-\n        -DskipTests\n    run:");
                var quotedNarrowingEnvironment = complete.replace(
                                "    run: pwsh", "    env:\n      MAVEN_ARGS: \"-DskipTests\"\n    run: pwsh");
                var conditionalPreflight = complete.replace(
                                "    run: pwsh", "    if: false\n    run: pwsh");
                var ignoredPreflightFailure = complete.replace(
                                "    run: pwsh", "    continue-on-error: true\n    run: pwsh");
                var unconditionalMaven = complete.replace(
                                "    run: ./mvnw", "    if: ${{ always() }}\n    run: ./mvnw");

		assertThat(qualityGateViolations(complete)).isEmpty();
		assertThat(qualityGateViolations(skipFlag)).anyMatch(message -> message.contains("exactly"));
		assertThat(qualityGateViolations(selector)).anyMatch(message -> message.contains("exactly"));
		assertThat(qualityGateViolations(narrowingEnvironment))
				.anyMatch(message -> message.contains("environment"));
		assertThat(qualityGateViolations(foldedNarrowingEnvironment))
				.anyMatch(message -> message.contains("environment"));
                assertThat(qualityGateViolations(quotedNarrowingEnvironment))
                                .anyMatch(message -> message.contains("environment"));
                assertThat(qualityGateViolations(conditionalPreflight))
                                .contains("quality-gate preflight step must not be conditional");
                assertThat(qualityGateViolations(ignoredPreflightFailure))
                                .contains("quality-gate preflight step must not continue on error");
                assertThat(qualityGateViolations(unconditionalMaven))
                                .contains("quality-gate Maven step must not bypass a failed preflight with always()");
	}

	@Test
	void testerDescriptionInspectionRejectsPostImplementationValidationClaim() {
		var beforeOnly = "description = \"Use before implementation to author spec-derived tests.\"";
		var beforeAndAfter = "description = \"Use before implementation and after implementation for validation.\"";

		assertThat(testerDescriptionViolations(beforeOnly)).isEmpty();
		assertThat(testerDescriptionViolations(beforeAndAfter))
				.containsExactly("tester description must not claim post-implementation validation");
	}

	@Test
	void workflowInspectionRequiresDocumentationBeforeAuditAndRepairLogger() throws IOException {
		var valid = "Architect -> OpenSpec/docs\nArchitect -> Reviewer(AUDIT)\nlog-repair-routing.ps1";
		var auditFirst = "Architect -> Reviewer(AUDIT)\nArchitect -> OpenSpec/docs";

		assertThat(workflowLifecycleViolations(valid)).isEmpty();
		assertThat(workflowLifecycleViolations(auditFirst))
				.contains("workflow must update OpenSpec/docs before final Reviewer(AUDIT)",
						"workflow must document log-repair-routing.ps1");
		assertThat(workflowLifecycleViolations(
				Files.readString(repositoryRoot.resolve("docs/AGENT_WORKFLOW.md")))).isEmpty();
	}

	@Test
	void projectAgentConfigurationMatchesClosedRoutingProtocol() throws IOException {
		var roleConfigurations = new LinkedHashMap<String, String>();
		for (var role : List.of("developer", "tester", "reviewer", "researcher")) {
			roleConfigurations.put(role, Files.readString(repositoryRoot.resolve(".codex/agents/" + role + ".toml")));
		}

		var violations = agentConfigurationViolations(
				Files.readString(repositoryRoot.resolve(".codex/config.toml")),
				roleConfigurations,
				Files.readString(repositoryRoot.resolve("AGENTS.md")),
				Files.readString(repositoryRoot.resolve("docs/AGENT_WORKFLOW.md")));

		assertThat(violations).isEmpty();
	}

	@Test
	void closedStatusComparisonRejectsDriftedRoleVocabulary() {
		assertThat(statusSetViolations(
				"tester", Set.of("RED_CANDIDATE", "EVIDENCE_CANDIDATE", "SPEC_INCOMPLETE", "TEST_SUSPECT", "BLOCKED"),
				ROLE_OUTPUT_STATUSES.get("tester")))
				.isEmpty();
		assertThat(statusSetViolations(
				"tester", Set.of("RED_CANDIDATE", "TESTS_GREEN_CONFIRMED", "BLOCKED"),
				ROLE_OUTPUT_STATUSES.get("tester")))
				.containsExactly("tester status set differs from the canonical routing contract: "
						+ "[BLOCKED, RED_CANDIDATE, TESTS_GREEN_CONFIRMED]");
	}

	@Test
	void javaTestIntegrityInspectionAcceptsOrdinarySource() {
		var source = "class SampleTest { @Test void runs() { assumeTrue(featureAvailable()); } }";

		assertThat(javaTestSourceViolations("SampleTest.java", source)).isEmpty();
	}

	@Test
	void javaTestIntegrityInspectionRejectsDisabledIgnoredAndLiteralFalseAssumptions() {
		var disabled = "class DisabledTest { @" + "Disabled @Test void neverRuns() {} }";
		var ignored = "class IgnoredTest { @org.junit." + "Ignore @Test void neverRuns() {} }";
		var falseAssumption = "class AssumedTest { @Test void neverRuns() { Assumptions.assumeTrue("
				+ "false); } }";
		var inverseFalseAssumption = "class AssumedTest { @Test void neverRuns() { assumeFalse("
				+ "true); } }";
		var parenthesizedFalse = "class AssumedTest { @Test void neverRuns() { assumeTrue((("
				+ "false))); } }";
		var parenthesizedTrue = "class AssumedTest { @Test void neverRuns() { assumeFalse(("
				+ "true)); } }";

		assertThat(javaTestSourceViolations("DisabledTest.java", disabled))
				.containsExactly("DisabledTest.java explicitly disables or ignores a JUnit test");
		assertThat(javaTestSourceViolations("IgnoredTest.java", ignored))
				.containsExactly("IgnoredTest.java explicitly disables or ignores a JUnit test");
		assertThat(javaTestSourceViolations("AssumedTest.java", falseAssumption))
				.containsExactly("AssumedTest.java contains an unconditional literal false assumption");
		assertThat(javaTestSourceViolations("AssumedTest.java", inverseFalseAssumption))
				.containsExactly("AssumedTest.java contains an unconditional literal false assumption");
		assertThat(javaTestSourceViolations("AssumedTest.java", parenthesizedFalse))
				.containsExactly("AssumedTest.java contains an unconditional literal false assumption");
		assertThat(javaTestSourceViolations("AssumedTest.java", parenthesizedTrue))
				.containsExactly("AssumedTest.java contains an unconditional literal false assumption");
	}

	@Test
	void committedJavaTestSourcesDoNotDisableExecution() throws IOException {
		var violations = new ArrayList<String>();
		try (Stream<Path> sources = Files.walk(repositoryRoot.resolve("src/test"))) {
			sources.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".java"))
					.forEach(path -> violations.addAll(javaTestSourceViolations(
							relative(path), readString(path))));
		}

		assertThat(violations).isEmpty();
	}

	@Test
	void mavenInspectionRejectsSkipAndSelectionConfigurationAcrossProfilesAndTestPlugins() {
		var skipProperty = minimalPom("<properties><skipTests>true</skipTests></properties>");
		var profileSelector = minimalPom("""
				<profiles><profile><id>narrow</id><properties><test>OnlyThisTest</test></properties></profile></profiles>
				""");
		var surefireIncludes = minimalPom(testPlugin("maven-surefire-plugin", "<includes><include>**/Fast*</include></includes>"));
		var failsafeTags = minimalPom(testPlugin("maven-failsafe-plugin", "<excludedGroups>slow</excludedGroups>"));
		var integrationTestSelector = minimalPom("<properties><it.test>OnlyThisIT</it.test></properties>");
		var executionConfiguration = minimalPom("""
				<build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId><executions><execution>
				  <configuration><excludes><exclude>**/SlowTest.java</exclude></excludes></configuration>
				</execution></executions></plugin></plugins></build>
				""");

		assertThat(mavenTestSelectionViolations(skipProperty))
				.anyMatch(message -> message.contains("skipTests"));
		assertThat(mavenTestSelectionViolations(profileSelector))
				.anyMatch(message -> message.contains("test"));
		assertThat(mavenTestSelectionViolations(surefireIncludes))
				.anyMatch(message -> message.contains("includes"));
		assertThat(mavenTestSelectionViolations(failsafeTags))
				.anyMatch(message -> message.contains("excludedGroups"));
		assertThat(mavenTestSelectionViolations(integrationTestSelector))
				.anyMatch(message -> message.contains("it.test"));
		assertThat(mavenTestSelectionViolations(executionConfiguration))
				.anyMatch(message -> message.contains("excludes"));
	}

	@Test
	void mavenInspectionAllowsEnforcerDependencyExclusions() {
		var enforcerConfiguration = minimalPom("""
				<build><plugins><plugin>
				  <artifactId>maven-enforcer-plugin</artifactId>
				  <configuration><rules><bannedDependencies><excludes>
				    <exclude>org.hibernate:*</exclude>
				  </excludes></bannedDependencies></rules></configuration>
				</plugin></plugins></build>
				""");

		assertThat(mavenTestSelectionViolations(enforcerConfiguration)).isEmpty();
	}

	@Test
	void mavenInspectionAllowsUnrelatedPluginLocalProperties() {
		var unrelatedPlugin = minimalPom("""
				<build><plugins><plugin>
				  <artifactId>example-report-plugin</artifactId>
				  <configuration><properties><test>report-label</test></properties></configuration>
				</plugin></plugins></build>
				""");

		assertThat(mavenTestSelectionViolations(unrelatedPlugin)).isEmpty();
	}

	@Test
	void mavenConfigInspectionRejectsSkipAndSelectionFlags() {
		assertThat(mavenConfigViolations("-T1C\n-Dstyle.color=always\n")).isEmpty();
		assertThat(mavenConfigViolations("-DskipTests\n"))
				.containsExactly(".mvn/maven.config narrows test execution: -DskipTests");
		assertThat(mavenConfigViolations("-Dit.test=OnlyThisIT\n"))
				.containsExactly(".mvn/maven.config narrows test execution: -Dit.test=OnlyThisIT");
		assertThat(mavenConfigViolations("--define skipTests=true\n"))
				.containsExactly(".mvn/maven.config narrows test execution: --define skipTests=true");
		assertThat(mavenConfigViolations("--define=it.test=OnlyThisIT\n"))
				.containsExactly(".mvn/maven.config narrows test execution: --define=it.test=OnlyThisIT");
	}

	@Test
        void evidenceCandidateInspectionLimitsStatusToPostAuditEvidenceRepair() {
                var valid = """
                                Phase: skeleton | tests-red | tests-evidence | implementation | adjudicate | audit
                                AUDIT_FAILED missing test evidence -> tests-evidence -> EVIDENCE_CANDIDATE after green implementation.
                                TEST_WRONG -> tests-red -> RED_CANDIDATE.
                                """;
                var invalid = """
                                Phase: skeleton | tests-red | implementation | adjudicate | audit
                                AUDIT_FAILED missing test evidence -> tests-red -> EVIDENCE_CANDIDATE after green implementation.
                                TEST_WRONG -> tests-red -> RED_CANDIDATE.
                                """;

                assertThat(evidenceCandidateGuidanceViolations("sample", valid)).isEmpty();
                assertThat(evidenceCandidateGuidanceViolations("sample", invalid))
                                .contains(
                                                "sample must route AUDIT_FAILED missing evidence to tests-evidence",
                                                "sample task capsule must include tests-evidence");
        }

	@Test
	void committedMavenConfigurationDoesNotNarrowDefaultTestLifecycle() throws IOException {
		assertThat(mavenTestSelectionViolations(Files.readString(repositoryRoot.resolve("pom.xml")))).isEmpty();
		var mavenConfig = repositoryRoot.resolve(".mvn/maven.config");
		assertThat(mavenConfigViolations(Files.exists(mavenConfig) ? Files.readString(mavenConfig) : "")).isEmpty();
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

	private List<String> qualityGateViolations(String content) {
		var violations = new ArrayList<String>();
		var preflightCommand = "run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1";
		var preflightCommands = content.lines()
				.map(String::trim)
				.filter(preflightCommand::equals)
				.count();
		var exactCommands = content.lines()
				.map(String::trim)
				.filter("run: ./mvnw clean verify"::equals)
				.count();
		if (exactCommands != 1) {
			violations.add("quality-gate must contain exactly one `run: ./mvnw clean verify` command");
		}
		if (preflightCommands != 1 || content.indexOf(preflightCommand) > content.indexOf("run: ./mvnw clean verify")) {
			violations.add("quality-gate must run verify-test-integrity.ps1 exactly once before clean verify");
		}

		content.lines()
				.map(String::trim)
				.filter(line -> line.startsWith("run:") && line.contains("mvnw"))
				.filter(line -> !line.equals("run: ./mvnw clean verify"))
				.map(line -> "quality-gate Maven command is not exactly the complete lifecycle: " + line)
				.forEach(violations::add);

                for (var environmentValue : yamlMavenEnvironmentValues(content)) {
                        if (!mavenConfigViolations(environmentValue).isEmpty()) {
                                violations.add("quality-gate environment narrows Maven test execution");
                        }
                }

                var preflightStep = yamlStepLines(content, preflightCommand);
                if (preflightStep.stream().map(String::trim).anyMatch(line -> line.startsWith("if:"))) {
                        violations.add("quality-gate preflight step must not be conditional");
                }
                if (preflightStep.stream().map(String::trim)
                                .anyMatch(line -> line.equalsIgnoreCase("continue-on-error: true"))) {
                        violations.add("quality-gate preflight step must not continue on error");
                }
                var mavenStep = yamlStepLines(content, "run: ./mvnw clean verify");
                if (mavenStep.stream().map(String::trim)
                                .anyMatch(line -> line.startsWith("if:") && line.contains("always()"))) {
                        violations.add("quality-gate Maven step must not bypass a failed preflight with always()");
                }

                return violations;
        }

        private List<String> yamlStepLines(String content, String command) {
                var lines = content.split("\\R", -1);
                var commandIndex = -1;
                for (var index = 0; index < lines.length; index++) {
                        if (lines[index].trim().equals(command)) {
                                commandIndex = index;
                                break;
                        }
                }
                if (commandIndex < 0) {
                        return List.of();
                }

                var stepStart = commandIndex;
                while (stepStart >= 0 && !lines[stepStart].trim().startsWith("- ")) {
                        stepStart--;
                }
                if (stepStart < 0) {
                        return List.of();
                }
                var stepIndent = leadingWhitespace(lines[stepStart]);
                var stepEnd = lines.length;
                for (var index = commandIndex + 1; index < lines.length; index++) {
                        if (leadingWhitespace(lines[index]) == stepIndent && lines[index].trim().startsWith("- ")) {
                                stepEnd = index;
                                break;
                        }
                }
                return List.of(lines).subList(stepStart, stepEnd);
        }

	private List<String> yamlMavenEnvironmentValues(String content) {
		var values = new ArrayList<String>();
		var lines = content.split("\\R", -1);
		var environment = Pattern.compile("^(\\s*)(?:MAVEN_ARGS|MAVEN_OPTS|MAVEN_CLI_OPTS)\\s*:\\s*(.*)$");
		for (var index = 0; index < lines.length; index++) {
			var matcher = environment.matcher(lines[index]);
			if (!matcher.matches()) {
				continue;
			}

			var baseIndent = matcher.group(1).length();
			var scalar = matcher.group(2).trim();
			var value = new StringBuilder();
			if (!scalar.isBlank() && !scalar.matches("[>|][+-]?")) {
				value.append(scalar);
			}
			if (scalar.isBlank() || scalar.matches("[>|][+-]?")) {
				while (index + 1 < lines.length) {
					var nextLine = lines[index + 1];
					if (!nextLine.isBlank() && leadingWhitespace(nextLine) <= baseIndent) {
						break;
					}
					index++;
					if (!nextLine.isBlank()) {
						value.append(' ').append(nextLine.trim());
					}
				}
			}
			values.add(value.toString());
		}
		return values;
	}

	private int leadingWhitespace(String value) {
		var length = 0;
		while (length < value.length() && Character.isWhitespace(value.charAt(length))) {
			length++;
		}
		return length;
	}

	private List<String> testerDescriptionViolations(String testerConfig) {
		var matcher = Pattern.compile("(?m)^\\s*description\\s*=\\s*\"([^\"]*)\"\\s*$")
				.matcher(testerConfig);
		if (matcher.find() && matcher.group(1).toLowerCase().contains("after implementation")) {
			return List.of("tester description must not claim post-implementation validation");
		}
		return List.of();
	}

	private List<String> workflowLifecycleViolations(String workflow) {
		var violations = new ArrayList<String>();
		var documentation = workflow.indexOf("OpenSpec/docs");
		var audit = workflow.indexOf("Reviewer(AUDIT)");
		if (documentation < 0 || audit < 0 || documentation > audit) {
			violations.add("workflow must update OpenSpec/docs before final Reviewer(AUDIT)");
		}
		if (!workflow.contains("log-repair-routing.ps1")) {
			violations.add("workflow must document log-repair-routing.ps1");
		}
		return violations;
	}

	private List<String> agentConfigurationViolations(
			String rootConfig,
			Map<String, String> roleConfigurations,
			String rootGuide,
			String workflow) {
		var violations = new ArrayList<String>();
		checkReasoningEffort("root", rootConfig, violations);
		if (!Pattern.compile("(?m)^\\s*max_depth\\s*=\\s*1\\s*$").matcher(rootConfig).find()) {
			violations.add("root config must set max_depth = 1");
		}

		for (var entry : roleConfigurations.entrySet()) {
			var role = entry.getKey();
			var content = entry.getValue();
			checkReasoningEffort(role, content, violations);
			if (!content.contains("model = \"gpt-5.6-sol\"")) {
				violations.add(role + " must use gpt-5.6-sol");
			}
			if (!content.contains("Do not spawn subagents.")) {
				violations.add(role + " must prohibit nested subagents");
			}
		}

		var tester = roleConfigurations.getOrDefault("tester", "");
		if (!"high".equals(configuredReasoningEffort(tester))) {
			violations.add("tester must use high reasoning");
		}
		violations.addAll(testerDescriptionViolations(tester));
		violations.addAll(evidenceCandidateGuidanceViolations("tester", tester));
		var reviewer = roleConfigurations.getOrDefault("reviewer", "");
		if (!Pattern.compile("(?m)^\\s*sandbox_mode\\s*=\\s*\"read-only\"\\s*$").matcher(reviewer).find()) {
			violations.add("reviewer must set sandbox_mode = \"read-only\"");
		}

		for (var entry : ROLE_OUTPUT_STATUSES.entrySet()) {
			var actual = declaredStatuses(roleConfigurations.getOrDefault(entry.getKey(), ""), null);
			violations.addAll(statusSetViolations(entry.getKey(), actual, entry.getValue()));
		}
		violations.addAll(statusSetViolations(
				"reviewer ADJUDICATE",
				declaredStatuses(reviewer, "ADJUDICATE"),
				REVIEWER_ADJUDICATE_STATUSES));
		violations.addAll(statusSetViolations(
				"reviewer AUDIT",
				declaredStatuses(reviewer, "AUDIT"),
				REVIEWER_AUDIT_STATUSES));

		var guidance = rootGuide + System.lineSeparator() + workflow;
		for (var legacyStatus : LEGACY_TESTER_STATUSES) {
			if (guidance.contains(legacyStatus) || tester.contains(legacyStatus)) {
				violations.add("obsolete Tester status remains committed: " + legacyStatus);
			}
		}
		if (!rootGuide.contains("RED_CANDIDATE") || !workflow.contains("RED_CANDIDATE")) {
			violations.add("root and workflow guidance must route RED_CANDIDATE to Architect");
		}
		if (!rootGuide.contains("phase-manifest.ps1") || !workflow.contains("phase-manifest.ps1")) {
			violations.add("root and workflow guidance must name the Architect-owned phase manifest gate");
		}
		if (!rootGuide.contains("clean verify") || !workflow.contains("clean verify")) {
			violations.add("root and workflow guidance must name the Architect-owned complete Maven gate");
		}
		if (!hasOrderedPreflight(rootGuide) || !hasOrderedPreflight(workflow)) {
			violations.add("root and workflow guidance must run verify-test-integrity.ps1 before clean verify");
		}
		violations.addAll(evidenceCandidateGuidanceViolations("root guidance", rootGuide));
		violations.addAll(evidenceCandidateGuidanceViolations("workflow guidance", workflow));

		return violations;
	}

	private boolean hasOrderedPreflight(String content) {
		var preflight = content.indexOf("verify-test-integrity.ps1");
		return preflight >= 0 && content.indexOf("clean verify", preflight) > preflight;
	}

        private List<String> evidenceCandidateGuidanceViolations(String owner, String content) {
                var lower = content.toLowerCase();
                var scoped = content.contains("EVIDENCE_CANDIDATE")
                                && content.contains("AUDIT_FAILED")
                                && content.contains("tests-evidence")
                                && content.contains("RED_CANDIDATE")
                                && lower.contains("green");
                var violations = new ArrayList<String>();
                if (!scoped) {
                        violations.add(owner + " must confine EVIDENCE_CANDIDATE to post-AUDIT test-evidence repair");
                }
                if (!nearbyInEitherOrder(content, "AUDIT_FAILED", "tests-evidence", 240)) {
                        violations.add(owner + " must route AUDIT_FAILED missing evidence to tests-evidence");
                }
                if (!nearbyInEitherOrder(content, "TEST_WRONG", "tests-red", 240)) {
                        violations.add(owner + " must route TEST_WRONG to tests-red");
                }
                if (!nearbyInEitherOrder(content, "EVIDENCE_CANDIDATE", "tests-evidence", 240)) {
                        violations.add(owner + " must use EVIDENCE_CANDIDATE only in tests-evidence");
                }
                if (content.contains("Phase:")
                                && !Pattern.compile("(?m)^\\s*(?:-\\s*)?Phase:.*tests-red.*tests-evidence")
                                                .matcher(content).find()) {
                        violations.add(owner + " task capsule must include tests-evidence");
                }
                return violations;
        }

        private boolean nearbyInEitherOrder(String content, String left, String right, int distance) {
                var quotedLeft = Pattern.quote(left);
                var quotedRight = Pattern.quote(right);
                return Pattern.compile(
                                "(?s)(?:" + quotedLeft + ".{0," + distance + "}" + quotedRight
                                                + "|" + quotedRight + ".{0," + distance + "}" + quotedLeft + ")")
                                .matcher(content)
                                .find();
        }

	private void checkReasoningEffort(String owner, String content, List<String> violations) {
		var effort = configuredReasoningEffort(content);
		if (effort == null) {
			violations.add(owner + " must configure model_reasoning_effort");
		} else if (effort.equalsIgnoreCase("ultra")) {
			violations.add(owner + " must not use ultra reasoning");
		}
	}

	private String configuredReasoningEffort(String content) {
		var matcher = MODEL_REASONING_EFFORT.matcher(content);
		return matcher.find() ? matcher.group(1) : null;
	}

	private Set<String> declaredStatuses(String content, String mode) {
		return content.lines()
				.filter(line -> line.toLowerCase().contains("return exactly one"))
				.filter(line -> mode == null || line.contains(mode))
				.flatMap(line -> {
					var matcher = STATUS_TOKEN.matcher(line);
					var statuses = new ArrayList<String>();
					while (matcher.find()) {
						statuses.add(matcher.group());
					}
					return statuses.stream();
				})
				.collect(Collectors.toUnmodifiableSet());
	}

	private List<String> statusSetViolations(String owner, Set<String> actual, Set<String> expected) {
		if (actual.equals(expected)) {
			return List.of();
		}
		return List.of(owner + " status set differs from the canonical routing contract: "
				+ actual.stream().sorted().toList());
	}

	private List<String> javaTestSourceViolations(String fileName, String source) {
		var violations = new ArrayList<String>();
		var code = maskJavaCommentsAndLiterals(source);
		if (DISABLED_TEST_ANNOTATION.matcher(code).find()) {
			violations.add(fileName + " explicitly disables or ignores a JUnit test");
		}
		if (LITERAL_FALSE_ASSUMPTION.matcher(code).find()) {
			violations.add(fileName + " contains an unconditional literal false assumption");
		}
		return violations;
	}

	private String maskJavaCommentsAndLiterals(String source) {
		var masked = new StringBuilder(source.length());
		var state = JavaLexicalState.CODE;
		for (var index = 0; index < source.length(); index++) {
			var current = source.charAt(index);
			var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
			switch (state) {
				case CODE -> {
					if (current == '/' && next == '/') {
						masked.append("  ");
						index++;
						state = JavaLexicalState.LINE_COMMENT;
					} else if (current == '/' && next == '*') {
						masked.append("  ");
						index++;
						state = JavaLexicalState.BLOCK_COMMENT;
					} else if (current == '\"' && next == '\"'
							&& index + 2 < source.length() && source.charAt(index + 2) == '\"') {
						masked.append("   ");
						index += 2;
						state = JavaLexicalState.TEXT_BLOCK;
					} else if (current == '\"') {
						masked.append(' ');
						state = JavaLexicalState.STRING;
					} else if (current == '\'') {
						masked.append(' ');
						state = JavaLexicalState.CHARACTER;
					} else {
						masked.append(current);
					}
				}
				case LINE_COMMENT -> {
					masked.append(current == '\n' ? '\n' : ' ');
					if (current == '\n') {
						state = JavaLexicalState.CODE;
					}
				}
				case BLOCK_COMMENT -> {
					if (current == '*' && next == '/') {
						masked.append("  ");
						index++;
						state = JavaLexicalState.CODE;
					} else {
						masked.append(current == '\n' ? '\n' : ' ');
					}
				}
				case STRING, CHARACTER -> {
					var terminator = state == JavaLexicalState.STRING ? '\"' : '\'';
					masked.append(current == '\n' ? '\n' : ' ');
					if (current == '\\' && next != '\0') {
						masked.append(next == '\n' ? '\n' : ' ');
						index++;
					} else if (current == terminator) {
						state = JavaLexicalState.CODE;
					}
				}
				case TEXT_BLOCK -> {
					if (current == '\"' && next == '\"'
							&& index + 2 < source.length() && source.charAt(index + 2) == '\"') {
						masked.append("   ");
						index += 2;
						state = JavaLexicalState.CODE;
					} else {
						masked.append(current == '\n' ? '\n' : ' ');
					}
				}
			}
		}
		return masked.toString();
	}

	private List<String> mavenTestSelectionViolations(String xml) {
		var violations = new ArrayList<String>();
		var document = parseXml(xml);
		for (var properties : projectPropertyContainers(document.getDocumentElement())) {
			for (var child = properties.getFirstChild(); child != null; child = child.getNextSibling()) {
				if (child.getNodeType() != Node.ELEMENT_NODE) {
					continue;
				}
				var name = elementName(child);
				var value = child.getTextContent().trim();
				if (MAVEN_SKIP_ELEMENTS.contains(name) && !value.equalsIgnoreCase("false")) {
					violations.add("Maven property narrows test execution: " + name);
				} else if (MAVEN_SELECTOR_ELEMENTS.contains(name) && !value.isBlank()) {
					violations.add("Maven property selects part of the test suite: " + name);
				}
			}
		}

		for (var plugin : elements(document.getDocumentElement(), "plugin")) {
			var artifactId = directChildText(plugin, "artifactId");
			if (!Set.of("maven-surefire-plugin", "maven-failsafe-plugin").contains(artifactId)) {
				continue;
			}
			for (var configuration : elements(plugin, "configuration")) {
				for (var element : descendantElements(configuration)) {
					var name = elementName(element);
					var value = element.getTextContent().trim();
					if (MAVEN_SKIP_ELEMENTS.contains(name) && !value.equalsIgnoreCase("false")) {
						violations.add(artifactId + " skips test execution with " + name);
					} else if (MAVEN_SELECTOR_ELEMENTS.contains(name) && !value.isBlank()) {
						violations.add(artifactId + " selects part of the test suite with " + name);
					}
				}
			}
		}
		return violations;
	}

	private List<Element> projectPropertyContainers(Element project) {
		var containers = new ArrayList<Element>();
		containers.addAll(directChildren(project, "properties"));
		for (var profiles : directChildren(project, "profiles")) {
			for (var profile : directChildren(profiles, "profile")) {
				containers.addAll(directChildren(profile, "properties"));
			}
		}
		return containers;
	}

	private List<String> mavenConfigViolations(String content) {
		var violations = new ArrayList<String>();
		var normalized = content.lines()
				.map(line -> line.contains("#") ? line.substring(0, line.indexOf('#')) : line)
				.collect(Collectors.joining(" "));
		var tokens = normalized.trim().isEmpty() ? List.<String>of() : List.of(normalized.trim().split("\\s+"));
		for (var index = 0; index < tokens.size(); index++) {
			var token = stripMatchingQuotes(tokens.get(index));
			if (token.startsWith("-D") && token.length() > 2) {
				addMavenPropertyViolation(violations, token, token.substring(2));
			} else if (token.equals("--define") && index + 1 < tokens.size()) {
				var assignment = stripMatchingQuotes(tokens.get(++index));
				addMavenPropertyViolation(violations, "--define " + assignment, assignment);
			} else if (token.startsWith("--define=") && token.length() > "--define=".length()) {
				addMavenPropertyViolation(
						violations, token, token.substring("--define=".length()));
			}
		}
		return violations;
	}

	private void addMavenPropertyViolation(List<String> violations, String display, String assignment) {
		var separator = assignment.indexOf('=');
		var name = (separator < 0 ? assignment : assignment.substring(0, separator)).toLowerCase();
		var value = separator < 0 ? "" : assignment.substring(separator + 1);
		var skipProperty = Set.of("maven.test.skip", "skiptests", "skipits").contains(name);
		var selectorProperty = Set.of(
				"test", "it.test", "groups", "excludedgroups", "includetags", "excludetags",
				"surefire.includes", "surefire.excludes", "failsafe.includes", "failsafe.excludes")
				.contains(name);
		if ((skipProperty && !value.equalsIgnoreCase("false")) || selectorProperty) {
			violations.add(".mvn/maven.config narrows test execution: " + display);
		}
	}

	private String stripMatchingQuotes(String value) {
		if (value.length() >= 2
				&& ((value.startsWith("\"") && value.endsWith("\""))
						|| (value.startsWith("'") && value.endsWith("'")))) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	private org.w3c.dom.Document parseXml(String xml) {
		try {
			var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
		} catch (Exception exception) {
			throw new IllegalArgumentException("Cannot parse Maven XML", exception);
		}
	}

	private List<Element> elements(Element root, String name) {
		var matches = new ArrayList<Element>();
		if (elementName(root).equals(name)) {
			matches.add(root);
		}
		for (var child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				matches.addAll(elements((Element) child, name));
			}
		}
		return matches;
	}

	private List<Element> directChildren(Element parent, String name) {
		var matches = new ArrayList<Element>();
		for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child.getNodeType() == Node.ELEMENT_NODE && elementName(child).equals(name)) {
				matches.add((Element) child);
			}
		}
		return matches;
	}

	private List<Element> descendantElements(Element parent) {
		var descendants = new ArrayList<Element>();
		for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				descendants.add((Element) child);
				descendants.addAll(descendantElements((Element) child));
			}
		}
		return descendants;
	}

	private String directChildText(Element parent, String name) {
		return directChildren(parent, name).stream()
				.findFirst()
				.map(Element::getTextContent)
				.map(String::trim)
				.orElse("");
	}

	private String elementName(Node node) {
		return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
	}

	private String minimalPom(String body) {
		return "<project><modelVersion>4.0.0</modelVersion>" + body + "</project>";
	}

	private String testPlugin(String artifactId, String configuration) {
		return "<build><plugins><plugin><artifactId>" + artifactId + "</artifactId><configuration>"
				+ configuration + "</configuration></plugin></plugins></build>";
	}

	private String readString(Path path) {
		try {
			return Files.readString(path);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot inspect " + relative(path), exception);
		}
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

	private enum JavaLexicalState {
		CODE,
		LINE_COMMENT,
		BLOCK_COMMENT,
		STRING,
		CHARACTER,
		TEXT_BLOCK
	}
}
