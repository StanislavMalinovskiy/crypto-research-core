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
			"\\b(?:PLAN_READY|BUILD_DONE|REPAIR|APPROVE|BLOCKED|ESCALATE|DONE)\\b");
	private static final Map<String, Set<String>> ROLE_OUTPUT_STATUSES = Map.of(
			"architect", Set.of("PLAN_READY", "APPROVE", "REPAIR", "ESCALATE", "BLOCKED"),
			"builder_sol", Set.of("BUILD_DONE", "BLOCKED"),
			"builder_luna_xhigh", Set.of("BUILD_DONE", "BLOCKED"),
			"builder_luna_max", Set.of("BUILD_DONE", "BLOCKED"),
			"reviewer", Set.of("APPROVE", "REPAIR", "ESCALATE", "BLOCKED"),
			"escalation", Set.of("APPROVE", "REPAIR", "ESCALATE", "BLOCKED"));
	private static final Set<String> MAVEN_SELECTOR_ELEMENTS = Set.of(
			"includes", "include", "excludes", "exclude", "groups", "excludedGroups",
			"includeTags", "excludeTags", "test", "itTest", "it.test", "suiteXmlFiles");
	private static final Set<String> MAVEN_SKIP_ELEMENTS = Set.of(
			"skip", "skipTests", "skipITs", "maven.test.skip");
	private static final List<String> BUILDER_ROLES = List.of("builder_sol", "builder_luna_xhigh", "builder_luna_max");
	private static final String CLOSURE_PATH = "docs/agents/close-archive.md";

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
	void multiagentWorkflowRequiresLeanApprovalClosureAndFinalGateRouting() throws IOException {
		var valid = "APPROVE\nDOCS_CLOSE\ncomplete final gate\ncumulative resources\nindependent diagnosis\n"
				+ "implementation or test issue\ndocumentation or contract issue\ninfrastructure issue";
		var invalid = "DOCS_CLOSE\nAPPROVE\ncomplete final gate";

		assertThat(workflowLifecycleViolations(valid)).isEmpty();
		assertThat(workflowLifecycleViolations(invalid))
				.contains("workflow must close documentation after APPROVE and before the final gate",
						"workflow must route final-gate failures by ownership");
		assertThat(workflowLifecycleViolations(reachableWorkflow())).isEmpty();
	}

	@Test
	void workflowModeSelectionFailsClosedUnlessAgentsEnabledIsExactBooleanTrue() {
		assertThat(selectedAgentWorkflowMode("[agents]\nenabled = true\n"))
				.isEqualTo(AgentWorkflowMode.MULTIAGENT);
		assertThat(selectedAgentWorkflowMode("[agents]\nenabled = false\n"))
				.isEqualTo(AgentWorkflowMode.DEFAULT);
		assertThat(selectedAgentWorkflowMode("[agents]\nenabled = \"true\"\n"))
				.isEqualTo(AgentWorkflowMode.DEFAULT);
		assertThat(selectedAgentWorkflowMode("[agents]\nenabled = yes\n"))
				.isEqualTo(AgentWorkflowMode.DEFAULT);
		assertThat(selectedAgentWorkflowMode("[model]\nenabled = true\n"))
				.isEqualTo(AgentWorkflowMode.DEFAULT);
		assertThat(selectedAgentWorkflowMode(""))
				.isEqualTo(AgentWorkflowMode.DEFAULT);
	}

	@Test
	void workflowGuidanceInspectionRequiresConditionalLoadingAndMinimalDefaultSafeguards() {
		var rootGuide = """
				DEFAULT is selected unless `.codex/config.toml` contains exact `[agents].enabled = true`.
				When `[agents].enabled = true`, use MULTIAGENT and load [guide](docs/AGENT_WORKFLOW_MULTIAGENT.md).
				Otherwise load [DEFAULT workflow](docs/AGENT_WORKFLOW.md).
				""";
		var defaultWorkflow = """
				Control owns the active contract and final review. Developer owns tests and implementation.
				Changed behavior requires a targeted behavioral red before implementation. Compilation, discovery,
				configuration or infrastructure failure is not red. After red, do not weaken, disable, skip or narrow
				the test and do not add production behavior for a test artifact. Run relevant targeted green checks.
				Run verify-test-integrity.ps1 before clean verify, then OpenSpec validation and doctor.
				The handoff names changed files, red and green evidence, verification results and remaining risks.
				Control uses one consolidated repair by default; a further repair requires the user's explicit decision.
				""";
		var multiagentWorkflow = "Specialized supervised protocol";

		assertThat(agentWorkflowGuidanceViolations(
				"[agents]\nenabled = false", rootGuide, defaultWorkflow, multiagentWorkflow)).isEmpty();
		assertThat(agentWorkflowGuidanceViolations(
				"[agents]\nenabled = true", rootGuide, defaultWorkflow, multiagentWorkflow)).isEmpty();
		assertThat(agentWorkflowGuidanceViolations(
				"[agents]\nenabled = false", rootGuide.replace("enabled = true", "enabled = false"),
				defaultWorkflow, multiagentWorkflow))
				.contains("root guidance must load the MULTIAGENT guide only for exact enabled = true");
		assertThat(agentWorkflowGuidanceViolations(
				"[agents]\nenabled = false", rootGuide,
				defaultWorkflow + "\nReviewer(THREAT_CHECK) phase-manifest.ps1 task capsule", multiagentWorkflow))
				.contains("DEFAULT guidance must not require specialized MULTIAGENT protocol details");
	}

	@Test
	void committedAgentGuidanceMatchesSelectedWorkflowMode() throws IOException {
		var multiagentWorkflow = repositoryRoot.resolve("docs/AGENT_WORKFLOW_MULTIAGENT.md");
		var violations = agentWorkflowGuidanceViolations(
				Files.readString(repositoryRoot.resolve(".codex/config.toml")),
				Files.readString(repositoryRoot.resolve("AGENTS.md")),
				Files.readString(repositoryRoot.resolve("docs/AGENT_WORKFLOW.md")),
				Files.exists(multiagentWorkflow) ? Files.readString(multiagentWorkflow) : "");

		assertThat(violations).isEmpty();
		assertThat(selectedAgentWorkflowMode(Files.readString(repositoryRoot.resolve(".codex/config.toml"))))
				.isEqualTo(AgentWorkflowMode.MULTIAGENT);
		assertThat(Files.isRegularFile(repositoryRoot.resolve(".codex/scripts/log-agent-activity.ps1"))).isTrue();
		assertThat(Files.isRegularFile(repositoryRoot.resolve(".codex/scripts/export-subagent-log.ps1"))).isTrue();
		assertThat(Files.isRegularFile(repositoryRoot.resolve(".codex/scripts/log-agent-activity.tests.ps1"))).isTrue();
	}

	@Test
	void projectAgentConfigurationMatchesClosedRoutingProtocol() throws IOException {
		var roleConfigurations = new LinkedHashMap<String, String>();
		for (var role : List.of("architect", "builder_sol", "builder_luna_xhigh", "builder_luna_max", "reviewer", "escalation")) {
			var rolePath = repositoryRoot.resolve(".codex/agents/" + role + ".toml");
			roleConfigurations.put(role, Files.exists(rolePath) ? Files.readString(rolePath) : "");
		}

		var violations = agentConfigurationViolations(
				Files.readString(repositoryRoot.resolve(".codex/config.toml")),
				roleConfigurations,
				Files.readString(repositoryRoot.resolve("AGENTS.md")),
				reachableWorkflow());

		assertThat(violations).isEmpty();
		assertThat(repositoryRoot.resolve(".codex/agents/builder_terra.toml")).doesNotExist();
		assertThat(repositoryRoot.resolve(".codex/agents/builder_luna.toml")).doesNotExist();
		assertThat(repositoryRoot.resolve(".codex/agents/developer.toml")).doesNotExist();
		assertThat(repositoryRoot.resolve(".codex/agents/tester.toml")).doesNotExist();
		assertThat(repositoryRoot.resolve(".codex/agents/researcher.toml")).doesNotExist();
		assertThat(repositoryRoot.resolve(".codex/scripts/phase-manifest.ps1")).doesNotExist();
		assertThat(repositoryRoot.resolve(".codex/scripts/log-repair-routing.ps1")).doesNotExist();
	}

	@Test
	void configurationInspectorRejectsModelPermissionAndReviewDrift() throws IOException {
		var roles = new LinkedHashMap<String, String>();
		for (var role : ROLE_OUTPUT_STATUSES.keySet()) {
			roles.put(role, Files.readString(repositoryRoot.resolve(".codex/agents/" + role + ".toml")));
		}
		var config = Files.readString(repositoryRoot.resolve(".codex/config.toml"));
		var root = Files.readString(repositoryRoot.resolve("AGENTS.md"));
		var workflow = reachableWorkflow();
		assertThat(agentConfigurationViolations(config, roles, root, workflow)).isEmpty();
		assertThat(agentConfigurationViolations(config.replace("max_depth = 1", "max_depth = 2"), roles, root, workflow))
				.anyMatch(message -> message.contains("max_depth"));
		var changed = new LinkedHashMap<>(roles);
		changed.put("builder_sol", roles.get("builder_sol").replace("model = \"gpt-6.1-sol\"", "model = \"other\""));
		assertThat(agentConfigurationViolations(config, changed, root, workflow))
				.anyMatch(message -> message.contains("builder_sol must use"));
		changed.put("builder_sol", roles.get("builder_sol"));
		changed.put("reviewer", roles.get("reviewer").replace("sandbox_mode = \"read-only\"", "sandbox_mode = \"workspace-write\""));
		assertThat(agentConfigurationViolations(config, changed, root, workflow))
				.anyMatch(message -> message.contains("reviewer must set sandbox_mode"));
		changed.put("reviewer", roles.get("reviewer") + "\nReviewer may approve its own CORE_RISK changes.");
		assertThat(agentConfigurationViolations(config, changed, root, workflow))
				.contains("critical changes must not allow author self-approval");
	}

	@Test
	void relativeLinkInspectionRejectsMissingTarget() {
		var path = repositoryRoot.resolve("AGENTS.md");
		var violations = new ArrayList<String>();
		checkRelativeLinks(path, "[Testing](docs/TESTING.md)", violations);
		assertThat(violations).isEmpty();
		checkRelativeLinks(path, "[Missing](docs/missing-convention-fixture.md)", violations);
		assertThat(violations).containsExactly("AGENTS.md has broken link: docs/missing-convention-fixture.md");
	}

	@Test
	void closedStatusComparisonRejectsDriftedRoleVocabulary() {
		assertThat(statusSetViolations(
				"builder_sol", Set.of("BUILD_DONE", "BLOCKED"),
				ROLE_OUTPUT_STATUSES.get("builder_sol")))
				.isEmpty();
		assertThat(statusSetViolations(
				"builder_sol", Set.of("BUILD_DONE", "APPROVE", "BLOCKED"),
				ROLE_OUTPUT_STATUSES.get("builder_sol")))
				.containsExactly("builder_sol status set differs from the canonical routing contract: "
						+ "[APPROVE, BLOCKED, BUILD_DONE]");
	}

	@Test
	void builderBodiesIgnoreOnlyLineEndingsAndMetadata() {
		var roles = new LinkedHashMap<String, String>();
		for (var role : BUILDER_ROLES) {
			roles.put(role, "name = \"" + role + "\"\ndeveloper_instructions = \"\"\"\nKeep every safeguard.\n\"\"\"\n");
		}
		assertThat(builderBodyViolations(roles)).isEmpty();
		for (var role : BUILDER_ROLES) {
			var changed = new LinkedHashMap<>(roles);
			changed.put(role, roles.get(role).replace("name =", "description =").replace("\n", "\r\n"));
			assertThat(builderBodyViolations(changed)).as("metadata and line endings: %s", role).isEmpty();
			for (var replacement : List.of("Drop every safeguard.", "Keep  every safeguard.", "")) {
				changed.put(role, roles.get(role).replace("Keep every safeguard.", replacement));
				assertThat(builderBodyViolations(changed)).as("body drift: %s", role).isNotEmpty();
			}
			changed.put(role, "name = \"" + role + "\"");
			assertThat(builderBodyViolations(changed)).as("missing body: %s", role).isNotEmpty();
			changed.remove(role);
			assertThat(builderBodyViolations(changed)).as("missing role: %s", role).isNotEmpty();
		}
	}

	private List<String> builderBodyViolations(Map<String, String> roles) {
		var violations = new ArrayList<String>();
		var bodies = new ArrayList<String>();
		var pattern = Pattern.compile("(?ms)^developer_instructions\\s*=\\s*\"\"\"(.*?)\"\"\"");
		for (var role : BUILDER_ROLES) {
			var matcher = pattern.matcher(roles.getOrDefault(role, "").replace("\r\n", "\n").replace('\r', '\n'));
			if (!matcher.find() || matcher.group(1).isBlank()) {
				violations.add(role + " must have a nonempty developer_instructions body");
			} else {
				bodies.add(matcher.group(1));
			}
		}
		if (bodies.stream().distinct().count() > 1) {
			violations.add("Builder instruction bodies must be equal, normalizing only line endings");
		}
		return violations;
	}

	@Test
	void instructionRoutingIsConditional() throws IOException {
		var root = Files.readString(repositoryRoot.resolve("AGENTS.md"));
		assertThat(summaryReadingIsConditional(root))
				.as("PROJECT_SUMMARY.md must be conditional on onboarding, product orientation or task relevance")
				.isTrue();
		assertThat(root.lines().count()).as("root router line budget").isLessThanOrEqualTo(150);
		var workflow = Files.readString(repositoryRoot.resolve("docs/AGENT_WORKFLOW_MULTIAGENT.md"));
		assertThat(closureRoutingViolations(workflow, reachableClosure(workflow))).isEmpty();
		assertThat(Files.readString(repositoryRoot.resolve("docs/AGENT_WORKFLOW.md"))).doesNotContain("close-archive.md");
	}

	@Test
	void conditionalClosureGuardsRejectBrokenRoutesAndSafeguards() {
		var workflow = """
				Load [closure](agents/close-archive.md) only before DOCS_CLOSE, complete final gate, archive,
				archive recovery or post-archive work. Ordinary PLAN, BUILD and REVIEW do not load it.
				## End-to-end flow
				APPROVE → Architect DOCS_CLOSE → Main complete final gate → Main checkpoint → Architect CLI archive → Main post-checks → Main DONE
				""";
		var closure = String.join("\n", CLOSURE_POLICY_CLAUSES) + "\n" + """
				Main runs after DOCS_CLOSE:
				pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1
				mvnw.cmd clean verify
				openspec validate --all --strict --no-interactive
				openspec doctor
				git diff --check
				Never narrow or skip a required check.
				""";
		assertThat(closureRoutingViolations(workflow, closure)).isEmpty();
		assertThat(closureRoutingViolations(workflow.replace("agents/close-archive.md", "elsewhere.md"), closure))
				.contains("mandatory conditional closure route is missing");
		assertThat(closureRoutingViolations(workflow.replace("Load [closure]", "Optional reference [closure]"), closure))
				.contains("mandatory conditional closure route is missing");
		assertThat(closureRoutingViolations(workflow, "")).contains("closure target is missing or empty");
		assertThat(closureRoutingViolations(workflow.replace("only before", "at task start before"), closure))
				.contains("mandatory conditional closure route is missing");
		assertThat(closureRoutingViolations(workflow + "\nAlways read agents/close-archive.md before PLAN, BUILD and REVIEW.\n", closure))
				.contains("ordinary phases must not eagerly load closure");
		for (var clause : CLOSURE_POLICY_CLAUSES) {
			assertThat(closureRoutingViolations(workflow, closure.replace(clause, "omitted")))
					.as("relocated safeguard: %s", clause).contains(clause);
		}
		assertThat(closureRoutingViolations(workflow.replace("Main checkpoint → Architect CLI archive", "Architect CLI archive → Main checkpoint"), closure))
				.contains("closure order must preserve approval, documentation, gate, checkpoint, archive and post-checks");
		for (var command : List.of("verify-test-integrity.ps1", "mvnw.cmd clean verify", "openspec validate --all --strict --no-interactive", "openspec doctor", "git diff --check")) {
			assertThat(closureRoutingViolations(workflow, closure.replace(command, "omitted"))).isNotEmpty();
		}
		assertThat(closureRoutingViolations(workflow, closure.replace("mvnw.cmd clean verify", "mvnw.cmd -DskipTests clean verify"))).isNotEmpty();
		assertThat(closureRoutingViolations(workflow, closure.replace("mvnw.cmd clean verify", "mvnw.cmd -Dtest=OnlyThisTest clean verify"))).isNotEmpty();
		assertThat(summaryReadingIsConditional("Read AGENTS.md and docs/PROJECT_SUMMARY.md before changes.")).isFalse();
		assertThat(summaryReadingIsConditional("Read docs/PROJECT_SUMMARY.md only for onboarding, product orientation or task relevance.")).isTrue();
	}

	private boolean summaryReadingIsConditional(String root) {
		return root.lines().anyMatch(line -> line.contains("PROJECT_SUMMARY.md")
				&& Pattern.compile("(?i)\\b(only|optional)\\b").matcher(line).find());
	}

	private boolean hasConditionalClosureRoute(String workflow) {
		return Stream.of(workflow.replace("\r\n", "\n").split("\\n\\s*\\n"))
				.anyMatch(paragraph -> paragraph.contains("agents/close-archive.md")
						&& containsAll(paragraph, "DOCS_CLOSE", "archive", "before")
						&& Pattern.compile("(?i)\\b(load|read)\\b")
								.matcher(paragraph.substring(0, paragraph.indexOf("agents/close-archive.md"))).find()
						&& Pattern.compile("(?i)\\bonly\\b").matcher(paragraph).find());
	}

	private List<String> closureRoutingViolations(String workflow, String closure) {
		var violations = new ArrayList<String>();
		if (!hasConditionalClosureRoute(workflow)) { violations.add("mandatory conditional closure route is missing"); }
		if (closure.isBlank()) { violations.add("closure target is missing or empty"); }
		if (workflow.lines().anyMatch(line -> line.contains("close-archive.md")
				&& Pattern.compile("(?i)always|at task start|before (?:ordinary )?PLAN").matcher(line).find())) {
			violations.add("ordinary phases must not eagerly load closure");
		}
		violations.addAll(missingClauses(closure, CLOSURE_POLICY_CLAUSES));
		if (!orderedClosure(archiveFlow(workflow),
				List.of("APPROVE", "DOCS_CLOSE", "complete final gate", "checkpoint", "archive", "post-checks", "DONE"))) {
			violations.add("closure order must preserve approval, documentation, gate, checkpoint, archive and post-checks");
		}
		if (!hasOrderedPreflight(closure) || !containsAll(closure, "mvnw.cmd clean verify",
				"openspec validate --all --strict --no-interactive", "openspec doctor", "git diff --check")) {
			violations.add("closure must retain the complete ordered gate without narrowing or skipping checks");
		}
		return violations;
	}

	private String reachableClosure(String workflow) throws IOException {
		var closure = repositoryRoot.resolve(CLOSURE_PATH);
		return hasConditionalClosureRoute(workflow) && Files.isRegularFile(closure) ? Files.readString(closure) : "";
	}

	private String reachableWorkflow() throws IOException {
		var workflow = Files.readString(repositoryRoot.resolve("docs/AGENT_WORKFLOW_MULTIAGENT.md"));
		return workflow + "\n" + reachableClosure(workflow);
	}

	private static final List<String> CLOSURE_POLICY_CLAUSES = List.of(
			"Any nonzero required check blocks completion", "never narrow or skip it",
			"pre-archive", "temporary Git index", "raw-byte", "hash-object -w --no-filters",
			"post-archive", "exact paths", "unchanged real index",
			"Never reset-hard", "whole-tree checkout", "git clean", "stash owner work",
			"BLOCKED before destructive action");

	@Test
	void workflowInspectorRejectsCriticalAuthorSelfApprovalEvenWhenOtherSafeguardsRemain() throws IOException {
		var guidance = Files.readString(repositoryRoot.resolve("docs/AGENT_WORKFLOW_MULTIAGENT.md"));
		assertThat(leanPolicyViolations(guidance + "\nMain may approve its own CORE_RISK changes."))
				.contains("critical changes must not allow author self-approval");
	}

	@Test
	void activeWorkflowRequiresIndependentCriticalReviewWithoutExactWording() throws IOException {
		var workflow = Files.readString(repositoryRoot.resolve("docs/AGENT_WORKFLOW_MULTIAGENT.md"));
		assertThat(leanPolicyViolations(workflow)).isEmpty();
		assertThat(leanPolicyViolations("Every CORE_RISK change requires an independent Reviewer.")).isEmpty();
		assertThat(leanPolicyViolations("Critical changes require a Reviewer independent of their author.")).isEmpty();
		assertThat(leanPolicyViolations("Main reviews all changes."))
				.contains("critical changes require independent review");
		assertThat(missingClauses(reachableClosure(workflow), CLOSURE_POLICY_CLAUSES)).isEmpty();
	}

	@Test
	void builderInstructionsMatchAcrossCodexAndClaudeExceptHostTransport() throws IOException {
		var bodies = new ArrayList<String>();
		for (var role : BUILDER_ROLES) {
			var content = Files.readString(repositoryRoot.resolve(".codex/agents/" + role + ".toml"));
			bodies.add(content.substring(content.indexOf("\"\"\"") + 3, content.lastIndexOf("\"\"\"")).strip()
					.replace("\r\n", "\n").replace("under Codex", "under HOST"));
		}
		for (var role : List.of("builder_opus", "builder_sonnet_routine", "builder_sonnet_standard")) {
			var content = Files.readString(repositoryRoot.resolve(".claude/agents/" + role + ".md")).replace("\r\n", "\n");
			bodies.add(content.substring(content.indexOf("\n---", 4) + 4).strip()
					.replace("under Claude Code", "under HOST")
					.replace(" (Bash with dangerouslyDisableSandbox when sandboxing is enabled)", ""));
		}
		assertThat(bodies.stream().distinct().toList()).hasSize(1);
	}

	@Test
	void archiveClosureMustFollowApprovalGateAndCheckpointInOrder() throws IOException {
		var stages = List.of("APPROVE", "DOCS_CLOSE", "complete final gate", "checkpoint", "archive", "post-checks", "DONE");
		var valid = String.join(" → ", stages);
		assertThat(orderedClosure(valid, stages)).isTrue();
		for (var index = 0; index < stages.size() - 1; index++) {
			var reordered = new ArrayList<>(stages);
			java.util.Collections.swap(reordered, index, index + 1);
			assertThat(orderedClosure(String.join(" → ", reordered), stages)).isFalse();
		}
		var workflow = Files.readString(repositoryRoot.resolve("docs/AGENT_WORKFLOW_MULTIAGENT.md"));
		assertThat(orderedClosure(archiveFlow(workflow), stages)).isTrue();
	}

	private String archiveFlow(String workflow) {
		return workflow.lines().filter(line -> line.contains("APPROVE") && line.contains("DOCS_CLOSE"))
				.filter(line -> line.contains("checkpoint") && line.contains("archive"))
				.findFirst().orElse("");
	}

	private boolean orderedClosure(String guidance, List<String> stages) {
		var previous = -1;
		for (var stage : stages) {
			var current = guidance.indexOf(stage);
			if (current <= previous) { return false; }
			previous = current;
		}
		return true;
	}

	@Test
	void activeRoutingRejectsLegacyNamesButIgnoresHistoricalEvidence() throws IOException {
		var active = new LinkedHashMap<String, String>();
		for (var file : List.of("AGENTS.md", "docs/AGENT_WORKFLOW.md", "docs/AGENT_WORKFLOW_MULTIAGENT.md", ".codex/config.toml")) {
			active.put(file, Files.readString(repositoryRoot.resolve(file)));
		}
		try (var paths = Files.list(repositoryRoot.resolve(".codex/agents"))) {
			paths.filter(Files::isRegularFile).forEach(path -> active.put(relative(path).replace('\\', '/'), readString(path)));
		}
		assertThat(obsoleteRoutingViolations(active)).isEmpty();
		assertThat(obsoleteRoutingViolations(Map.of("docs/archive/old.md", "builder_terra gpt-5.6-terra"))).isEmpty();
		for (var path : active.keySet()) {
			for (var obsolete : List.of("builder_terra", "gpt-5.6-terra")) {
				assertThat(obsoleteRoutingViolations(Map.of(path, obsolete))).containsExactly(path);
			}
		}
	}

	@Test
	void riskTriggerDefinitionHasOneActiveAuthority() throws IOException {
		var sources = new LinkedHashMap<String, String>();
		for (var file : List.of("AGENTS.md", "docs/AGENT_WORKFLOW.md", "docs/AGENT_WORKFLOW_MULTIAGENT.md", "docs/CORE_INVARIANTS.md", "docs/TESTING.md")) {
			sources.put(file, Files.readString(repositoryRoot.resolve(file)));
		}
		try (var paths = Files.list(repositoryRoot.resolve(".codex/agents"))) {
			paths.filter(Files::isRegularFile).forEach(path -> sources.put(relative(path), readString(path)));
		}
		assertThat(triggerAuthorities(sources)).containsExactly("docs/AGENT_WORKFLOW_MULTIAGENT.md");
		var valid = Map.of("docs/AGENT_WORKFLOW_MULTIAGENT.md", "CORE_RISK triggers: persistence; transactions; concurrency; security");
		assertThat(triggerAuthorities(valid)).containsExactly("docs/AGENT_WORKFLOW_MULTIAGENT.md");
		var duplicate = new LinkedHashMap<>(valid);
		duplicate.put("docs/CORE_INVARIANTS.md", "CORE_RISK includes persistence, transactions, concurrency, security.");
		assertThat(triggerAuthorities(duplicate)).hasSize(2);
	}

	private List<String> leanPolicyViolations(String guidance) {
		var violations = new ArrayList<String>();
		var lower = guidance.toLowerCase().replaceAll("\\s+", " ");
		if (!Pattern.compile("(core_risk|critical).{0,100}(independent|fresh reviewer)|(independent|fresh reviewer).{0,100}(core_risk|critical)")
				.matcher(lower).find()) {
			violations.add("critical changes require independent review");
		}
		if (Pattern.compile("(?:may|can|allowed to) approve (?:its|their|his|her|my) own (?:core_risk|critical)")
				.matcher(lower).find()) {
			violations.add("critical changes must not allow author self-approval");
		}
		return violations;
	}

	private List<String> missingClauses(String guidance, List<String> clauses) {
		var normalized = guidance.replace("`", "").replaceAll("\\s+", " ");
		return clauses.stream().filter(clause -> !normalized.contains(clause)).toList();
	}

	private List<String> obsoleteRoutingViolations(Map<String, String> sources) {
		return sources.entrySet().stream()
				.filter(entry -> entry.getKey().equals("AGENTS.md") || entry.getKey().matches("docs/AGENT_WORKFLOW[^/]*\\.md")
						|| entry.getKey().equals(".codex/config.toml") || entry.getKey().startsWith(".codex/agents/"))
				.filter(entry -> entry.getValue().contains("builder_terra") || entry.getValue().contains("gpt-5.6-terra"))
				.map(Map.Entry::getKey).toList();
	}

	private List<String> triggerAuthorities(Map<String, String> sources) {
		return sources.entrySet().stream().filter(entry -> Pattern.compile(
				"(?is)CORE_RISK(?: triggers)?(?: includes|:)[^\\n]*(?:persistence|transactions)")
				.matcher(entry.getValue().replace("`", "")).find()).map(Map.Entry::getKey).toList();
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

	private AgentWorkflowMode selectedAgentWorkflowMode(String rootConfig) {
		var inAgents = false;
		var enabledSeen = false;
		var multiagent = false;
		for (var rawLine : rootConfig.lines().toList()) {
			var comment = rawLine.indexOf('#');
			var line = (comment >= 0 ? rawLine.substring(0, comment) : rawLine).trim();
			if (line.matches("\\[[^]]+]")) {
				inAgents = line.equals("[agents]");
				continue;
			}
			if (!inAgents || !line.matches("enabled\\s*=.*")) {
				continue;
			}
			if (enabledSeen) {
				return AgentWorkflowMode.DEFAULT;
			}
			enabledSeen = true;
			multiagent = line.matches("enabled\\s*=\\s*true");
		}
		return enabledSeen && multiagent ? AgentWorkflowMode.MULTIAGENT : AgentWorkflowMode.DEFAULT;
	}

	private List<String> agentWorkflowGuidanceViolations(
			String rootConfig,
			String rootGuide,
			String defaultWorkflow,
			String multiagentWorkflow) {
		var violations = new ArrayList<String>();
		var conditionalMultiagentGuide = rootGuide.lines()
				.anyMatch(line -> line.contains("enabled = true")
						&& line.contains("AGENT_WORKFLOW_MULTIAGENT.md"));
		if (!conditionalMultiagentGuide) {
			violations.add("root guidance must load the MULTIAGENT guide only for exact enabled = true");
		}
		if (!rootGuide.contains("AGENT_WORKFLOW.md") || !rootGuide.contains("DEFAULT")) {
			violations.add("root guidance must identify the DEFAULT workflow guide");
		}
		if (multiagentWorkflow.isBlank()) {
			violations.add("the separate MULTIAGENT workflow guide must exist");
		}

		if (selectedAgentWorkflowMode(rootConfig) == AgentWorkflowMode.DEFAULT) {
			var lower = defaultWorkflow.toLowerCase();
			if (!containsAll(lower, "control", "developer", "tests", "implementation")) {
				violations.add("DEFAULT guidance must split Control and Developer responsibilities");
			}
			if (!containsAll(lower, "behavioral red", "compilation", "discovery", "configuration", "infrastructure")) {
				violations.add("DEFAULT guidance must distinguish behavioral red from non-behavioral failure");
			}
			if (!containsAll(lower, "weaken", "disable", "skip", "narrow", "test artifact")) {
				violations.add("DEFAULT guidance must prohibit test weakening and test-specific production behavior");
			}
			if (!hasOrderedPreflight(defaultWorkflow)
					|| !containsAll(lower, "openspec", "doctor", "targeted green")) {
				violations.add("DEFAULT guidance must require targeted green and the complete local gate");
			}
			if (!containsAll(lower, "changed files", "red", "green", "verification results", "remaining risks")) {
				violations.add("DEFAULT guidance must require a concise evidence handoff");
			}
			if (!containsAll(lower, "one consolidated repair", "further repair", "user")) {
				violations.add("DEFAULT guidance must bound repair to one consolidated handoff by default");
			}
			if (Stream.of(
					"RED_CANDIDATE", "EVIDENCE_CANDIDATE", "THREAT_CHECK", "phase-manifest.ps1",
					"task capsule", "log-agent-activity.ps1", "log-repair-routing.ps1", "subagents-readable.log")
					.anyMatch(defaultWorkflow::contains)) {
				violations.add("DEFAULT guidance must not require specialized MULTIAGENT protocol details");
			}
		}
		return violations;
	}

	private boolean containsAll(String content, String... fragments) {
		return Stream.of(fragments).allMatch(content::contains);
	}

	private List<String> workflowLifecycleViolations(String workflow) {
		var violations = new ArrayList<String>();
		var flowStart = workflow.indexOf("## End-to-end flow");
		var flow = flowStart >= 0 ? workflow.substring(flowStart) : workflow;
		var approve = flow.indexOf("APPROVE");
		var documentation = flow.indexOf("DOCS_CLOSE");
		var finalGate = flow.indexOf("complete final gate");
		if (approve < 0 || documentation < 0 || finalGate < 0
				|| approve > documentation || documentation > finalGate) {
			violations.add("workflow must close documentation after APPROVE and before the final gate");
		}
		if (!containsAll(workflow,
				"implementation", "test issue", "documentation", "contract issue", "infrastructure issue")) {
			violations.add("workflow must route final-gate failures by ownership");
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
		violations.addAll(builderBodyViolations(roleConfigurations));
		if (!rootConfig.contains("model = \"gpt-6.1-sol\"")
				|| !"medium".equals(configuredReasoningEffort(rootConfig))) {
			violations.add("Main must use gpt-6.1-sol with medium reasoning");
		}
		if (!rootConfig.contains("default_subagent_model = \"gpt-6.1-sol\"")
				|| !rootConfig.contains("default_subagent_reasoning_effort = \"medium\"")) {
			violations.add("default subagents must use gpt-6.1-sol with medium reasoning");
		}
		if (!Pattern.compile("(?m)^\\s*max_depth\\s*=\\s*1\\s*$").matcher(rootConfig).find()) {
			violations.add("root config must set max_depth = 1");
		}
		if (selectedAgentWorkflowMode(rootConfig) != AgentWorkflowMode.MULTIAGENT) {
			violations.add("root config must enable MULTIAGENT for the committed lean workflow");
		}

		var expectedModels = Map.of(
				"architect", "gpt-6.1-sol",
				"builder_sol", "gpt-6.1-sol",
				"builder_luna_xhigh", "gpt-6-luna",
				"builder_luna_max", "gpt-6-luna",
				"reviewer", "gpt-6.1-sol",
				"escalation", "gpt-6.1-sol");
		var expectedEfforts = Map.of(
				"architect", "high",
				"builder_sol", "medium",
				"builder_luna_xhigh", "xhigh",
				"builder_luna_max", "max",
				"reviewer", "medium",
				"escalation", "high");
		for (var entry : roleConfigurations.entrySet()) {
			var role = entry.getKey();
			var content = entry.getValue();
			checkReasoningEffort(role, content, violations);
			if (!content.contains("name = \"" + role + "\"")) {
				violations.add(role + " must declare its exact role name");
			}
			if (!content.contains("model = \"" + expectedModels.get(role) + "\"")) {
				violations.add(role + " must use " + expectedModels.get(role));
			}
			if (!expectedEfforts.get(role).equals(configuredReasoningEffort(content))) {
				violations.add(role + " must use " + expectedEfforts.get(role) + " reasoning");
			}
			if (!content.contains("Do not spawn subagents.")) {
				violations.add(role + " must prohibit nested subagents");
			}
		}

		var reviewer = roleConfigurations.getOrDefault("reviewer", "");
		if (!Pattern.compile("(?m)^\\s*sandbox_mode\\s*=\\s*\"read-only\"\\s*$").matcher(reviewer).find()) {
			violations.add("reviewer must set sandbox_mode = \"read-only\"");
		}
		violations.addAll(leanPolicyViolations(workflow));
		violations.addAll(leanPolicyViolations(reviewer));
		var escalation = roleConfigurations.getOrDefault("escalation", "");
		if (!Pattern.compile("(?m)^\\s*sandbox_mode\\s*=\\s*\"read-only\"\\s*$").matcher(escalation).find()) {
			violations.add("escalation must set sandbox_mode = \"read-only\"");
		}

		for (var entry : ROLE_OUTPUT_STATUSES.entrySet()) {
			var actual = declaredStatuses(roleConfigurations.getOrDefault(entry.getKey(), ""));
			violations.addAll(statusSetViolations(entry.getKey(), actual, entry.getValue()));
		}

		var guidance = rootGuide + System.lineSeparator() + workflow;
		if (!containsAll(guidance, "native Codex", "codex queue") || !guidance.contains("explicitly requests Orca")) {
			violations.add("guidance must require native Codex and forbid implicit Orca routing");
		}
		if (!containsAll(guidance, "Docker Desktop named pipes", "docker version", "escalated host access",
				"restricted sandbox")) {
			violations.add("guidance must distinguish Windows sandbox denial from Docker unavailability");
		}
		if (!containsAll(workflow,
				"PLAN_READY", "BUILD_DONE", "REPAIR", "APPROVE", "BLOCKED", "ESCALATE", "DONE",
				"red_suspect = true")) {
			violations.add("workflow must preserve the lean state, repair, and RED rerun contract");
		}
		if (!rootGuide.contains("docs/AGENT_WORKFLOW_MULTIAGENT.md") || !hasOrderedPreflight(workflow)) {
			violations.add("root must route to workflow requiring verify-test-integrity.ps1 before clean verify");
		}
		violations.addAll(workflowLifecycleViolations(workflow));

		return violations;
	}

	private boolean hasOrderedPreflight(String content) {
		var preflight = content.indexOf("verify-test-integrity.ps1");
		return preflight >= 0 && content.indexOf("clean verify", preflight) > preflight;
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

	private Set<String> declaredStatuses(String content) {
		return content.lines()
				.filter(line -> line.contains("Allowed output states:"))
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

	private enum AgentWorkflowMode {
		DEFAULT,
		MULTIAGENT
	}
}
