## Context

The repository already runs `RepositoryConventionsTest` in Maven and the GitHub quality gate already executes `./mvnw clean verify`. The existing role files separate API skeleton, test creation, implementation and review, but Tester currently self-attests both red and green, may edit tests while validating green, and owns the full Maven gate. Reviewer also combines adjudication and audit in a single ordered pass. See [proposal.md](proposal.md) for motivation and [repository-conventions](specs/repository-conventions/spec.md) for required behavior.

This is repository tooling only. It owns no application module, database transaction or runtime resource.

## Goals / Non-Goals

**Goals:**

- Make red, phase integrity and full green verification facts observed by Architect rather than claims made by a writing role.
- Make every role response mechanically routable and every Reviewer verdict binding within a parent-selected mode.
- Fail the Maven lifecycle for a small explicit set of committed test-bypass mechanisms.
- Reuse current Maven, Git, PowerShell and CI infrastructure without a production dependency or new workflow job.

**Non-Goals:**

- Prove at red time that every assertion is a complete semantic expression of its requirement.
- Attribute a file edit to a specific OS process or defend against a malicious process outside the supervised sequential workflow.
- Introduce worktrees, mutation testing, holdout tests, another Maven module or runtime observability.

## Decisions

### Architect owns mechanical gates

Tester returns `RED_CANDIDATE` with a requirement/scenario/test/assertion trace and the exact targeted command. Architect runs it against unchanged implementation sources and distinguishes expected assertion failure from compilation, discovery, configuration and infrastructure failures. After implementation Architect verifies the phase manifest, runs targeted tests, and runs `mvnw.cmd clean verify` (or `./mvnw clean verify`). `TESTS_RED_CONFIRMED` and `TESTS_GREEN_CONFIRMED` are removed because they conflate a role report with an independently observed gate.

The happy path does not call Tester again after red. A green failure returns to Developer unless Developer raises `TEST_SUSPECT`; a final Reviewer audit checks test adequacy and requirement alignment.

When `AUDIT_FAILED` explicitly identifies missing test evidence and the existing implementation is already correct, Architect may open a distinct `tests-evidence` repair phase. It is not a red phase. Tester returns `EVIDENCE_CANDIDATE` instead of fabricating a red result; Architect independently runs the named targeted command and accepts the evidence only when it passes and the phase manifest proves that no implementation path changed. `TEST_WRONG` alone routes back to `tests-red`.

### Freeze phases with a complete manifest and positive allowlist

A small PowerShell tool records sorted repository-relative paths and SHA-256 content hashes for tracked and untracked files. In this single-module repository it excludes exactly `.git`, the repository-root Maven `target` directory and `.codex-logs` by default; a nested directory merely named `target` remains protected. Exclusion comparison follows the platform's path case semantics so distinct paths on a case-sensitive filesystem are not collapsed. The snapshot path must remain outside the repository. Verification compares names and hashes, so additions, modifications and deletions are all visible even when a path was already dirty before the phase.

Architect supplies a positive allowlist from the task capsule. This is stronger than a test-only denylist because it also catches changes to `pom.xml`, test profiles, CI configuration or an unexpected new path. Sequential writer phases are required; the manifest is not OS-level authorship attribution.

### Use one canonical routing protocol

`docs/AGENT_WORKFLOW.md` is the canonical status and routing table; root and role guidance repeat only the exact subset needed locally and must remain consistent with it.

- Developer: `SKELETON_READY`, `IMPL_DONE`, `TEST_SUSPECT`, `BLOCKED`.
- Tester: `RED_CANDIDATE`, `EVIDENCE_CANDIDATE`, `SPEC_INCOMPLETE`, `TEST_SUSPECT`, `BLOCKED`.
- Researcher: `RESEARCH_DONE`, `INCONCLUSIVE`, `BLOCKED`.
- Reviewer `ADJUDICATE`: `CODE_WRONG`, `TEST_WRONG`, `SPEC_AMBIGUOUS`.
- Reviewer `AUDIT`: `AUDIT_FAILED`, `APPROVE`.

Every response starts with `STATUS: <value>`. Unknown, missing or mode-incompatible values are protocol errors and are not interpreted into a different verdict.

### Separate Reviewer modes and make valid verdicts binding

Architect names exactly one Reviewer mode in the task capsule. `ADJUDICATE` answers only the cited specification/test/code conflict and cannot be preempted by an unrelated invariant audit. `AUDIT` inspects the stable diff after mechanical gates pass.

A protocol-valid Reviewer verdict is binding: Architect may route it or escalate disagreement to the user, but may not substitute another substantive verdict. An `APPROVE` result cannot override an independently failed mechanical gate or incomplete Definition of Done. Reviewer uses `sandbox_mode = "read-only"` so its no-write rule is not prose-only.

### Route incomplete specifications without an autonomous loop

Tester uses `SPEC_INCOMPLETE` only when a testable contract cannot be derived. Architect may resolve it from an unambiguous accepted source and update the active change, consuming one repair routing, or must ask the user when a product choice is missing. A repeated unresolved result or the shared three-routing limit ends autonomous work. Each repair log entry identifies the loop, source verdict, repair owner and round.

A small PowerShell command appends those fields plus a UTC timestamp to `.codex-logs/repair-routings.jsonl` with exclusive append-and-retry semantics. It records routing metadata only and never prompt, response or transcript content.

### Keep project-agent execution deterministic

All project roles explicitly use `gpt-5.6-sol`; Tester, Reviewer and Researcher use high reasoning and Developer uses medium. Root and roles must not use `ultra`, because automatic delegation conflicts with the supervised role graph. `max_depth = 1` remains the structural nested-subagent limit, and every role instruction retains the no-spawn rule.

### Extend the existing repository convention test

Add checks to the test-side repository convention suite so they run in the existing Maven lifecycle. Java checks cover explicit JUnit disabling/ignoring annotations and unconditional literal false assumptions, including redundant parentheses. Maven checks parse `pom.xml` structurally, inspect root and execution-level test-plugin configuration and project-scoped test properties, and inspect `.mvn/maven.config` without confusing unrelated plugin properties or Maven Enforcer dependency exclusions with test selection. Quality-gate checks retain the exact complete command and reject adjacent flags or environment settings, including folded YAML values, that narrow it.

The scanner stays intentionally narrow. Reviewer remains responsible for semantic weakening that cannot be recognized mechanically.

### Run test-integrity inspection outside Maven first

A small PowerShell preflight inspects the committed Java test sources, `pom.xml`, optional `.mvn/maven.config` and the quality-gate workflow before Maven starts. The existing GitHub `quality-gate` and Architect's local full-gate sequence run this command before `clean verify`. This is necessary because a Maven-hosted guard can be suppressed by the Maven skip or selection configuration it is meant to reject.

The preflight recognizes the same narrow mechanisms as the repository convention fixtures, including quoted YAML scalars and Maven `--define` syntax. It also rejects a quality-gate preflight step that is conditional or allowed to fail, so Maven cannot continue after a detected bypass. The Java convention test remains useful for focused counterexamples and drift detection, but is no longer the independent enforcement boundary.

## Risks / Trade-offs

- **[A writer modifies an already-dirty file and a name-only diff misses it]** -> Compare full path-and-content manifests, not lists of changed names.
- **[Generated output creates false phase violations]** -> Exclude only exact root `.git`, `target` and lifecycle-log paths with platform-correct case comparison; all other exclusions require review.
- **[A test traces to a requirement but asserts the wrong semantics]** -> Require the trace for auditability and keep semantic validation in final Reviewer `AUDIT`.
- **[A novel test bypass remains possible]** -> State the mechanical boundary and preserve independent review.
- **[Architect repeatedly retries a malformed or inconvenient verdict]** -> Treat statuses as closed, keep Reviewer verdicts binding and escalate disagreement.
- **[Interactive configuration selects Ultra]** -> Record the effective mode at task start and stop the supervised workflow when it is Ultra; static repository checks cover only committed configuration.
- **[Bootstrap change updates the test-side guard before the new freeze rule exists]** -> Treat this change as the migration boundary, keep one writer for test files and require independent final review before the new contract becomes current.

## Migration Plan

1. Reconcile the OpenSpec artifacts, root guidance, workflow and role configuration.
2. Add and prove the phase-manifest and repair-routing log tools with focused red and green checks.
3. Add focused repository-convention cases, then implement the narrow Java, Maven and CI checks.
4. Add and prove the independent test-integrity preflight, then place it before Maven in local guidance and the existing CI job.
5. Update required OpenSpec and documentation artifacts before the final stable-diff audit.
6. Run the complete preflight, Maven and OpenSpec gates and obtain Reviewer `APPROVE`; after approval, change only the audit-result task marker.

Rollback removes the new repository tooling and restores the previous guidance together; there is no production or data migration.
