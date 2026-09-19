## 1. Agent Routing Contract

- [x] 1.1 Reconcile `AGENTS.md` and `docs/AGENT_WORKFLOW.md` around Architect-owned red, phase-integrity and full Maven gates; verify the workflow contains the canonical role/mode status table, binding Reviewer rule, `SPEC_INCOMPLETE` routing and logged shared repair counter.
- [x] 1.2 Update Developer, Tester, Reviewer and Researcher TOML files to match the closed status protocol; verify Tester no longer owns green verification, Reviewer has distinct modes plus a read-only sandbox, Tester uses high reasoning and every project subagent forbids nested delegation.
- [x] 1.3 Add repository-convention coverage for the committed role configuration; verify forbidden `ultra`, missing Reviewer read-only mode, drifted role status sets and obsolete Tester self-attestation are rejected.
- [x] 1.4 Add focused executable tests for repair-routing log validation, JSONL fields and concurrent append behavior; run them against the behavior-free skeleton and confirm an expected red result.
- [x] 1.5 Implement the repair-routing log command and record the current repair with loop, source status, owner and shared round number.
- [x] 1.6 Add the parent-confirmed `EVIDENCE_CANDIDATE` path for missing-test audit repairs and verify it cannot substitute for initial red or implementation green gates.
- [x] 1.7 Make `tests-evidence` a distinct parent-selected phase and remove every instruction that routes a green evidence repair through `tests-red`.

## 2. Phase Integrity Tool

- [x] 2.1 Add focused executable tests for the phase manifest covering unchanged, allowed, modified, added, deleted, already-dirty and nested `target` files while excluding only the repository Maven output directory; run the tests before implementation and confirm an expected behavioral red result.
- [x] 2.2 Implement the phase-manifest tool with path plus SHA-256 snapshots, precise generated-output exclusions and positive allowlist verification; rerun its focused tests and confirm they pass.
- [x] 2.3 Document the exact snapshot, verification and repair-routing log commands in the workflow and verify snapshot output remains outside the repository.
- [x] 2.4 Make root generated-output exclusions platform-case-correct and verify differently cased root paths remain protected on case-sensitive filesystems.

## 3. Mechanical Test-Integrity Gate

- [x] 3.1 Add focused convention-test cases for allowed Java test source and disabled, ignored and parenthesized unconditional-false-assumption examples; verify every forbidden example is rejected and the allowed example passes.
- [x] 3.2 Extend Java test-source inspection with the proven narrow checks and verify `mvnw.cmd test -Dtest=RepositoryConventionsTest` passes on the repository.
- [x] 3.3 Add XML-scoped checks for root and execution-level Surefire/Failsafe configuration, project test properties and `.mvn/maven.config`; verify skip and selection settings including `it.test` are rejected while unrelated plugin properties and Maven Enforcer dependency exclusions remain allowed.
- [x] 3.4 Strengthen quality-gate inspection against committed Maven skip, selection and narrowing flags or environment settings, including folded YAML values; verify the current exact `./mvnw clean verify` workflow passes.
- [x] 3.5 Add a behavior-free independent pre-Maven integrity-check skeleton and focused executable tests proving skip, selection, self-exclusion, quoted YAML and `--define` variants fail before Maven.
- [x] 3.6 Implement the independent preflight, run its red/green harness, and place the preflight before `clean verify` in Architect guidance and the existing CI quality-gate job.
- [x] 3.7 Reject conditional or continue-on-error preflight steps and verify a detected bypass cannot proceed to Maven in the stable quality-gate job.

## 4. Verification

- [x] 4.1 Run the independent preflight followed by `mvnw.cmd clean verify` from the repository root and record successful complete unit, integration and repository-convention results.
- [x] 4.2 Run `openspec validate --all --strict --no-interactive` and `openspec doctor`, then confirm the implementation diff contains no production, dependency, module, schema, migration or unrelated changes.
- [x] 4.3 Obtain a read-only Reviewer `AUDIT` verdict for the stable diff and verify `APPROVE` is present before marking the change implementation complete.

## 5. Context Isolation and Early Review

- [x] 5.1 Require `fork_turns: "none"` and a self-contained 200-400 word capsule for every new project role; add convention coverage for the required fields and forbidden inherited history.
- [x] 5.2 Add Reviewer `THREAT_CHECK` with its closed statuses and narrow bypass checklist; place it before Tester red and Developer implementation for CI, security/integrity and agent-workflow changes.

## 6. Assignment Telemetry

- [x] 6.1 Add focused PowerShell tests for logical dispatch/return correlation, local timestamp formatting, duration, role/phase token fields, unavailable-token handling and legacy readable export.
- [x] 6.2 Implement logical assignment logging into `.codex-logs/subagents.jsonl` and one-line completed records in `.codex-logs/subagents-readable.log` without copying prompts, responses or transcript paths.
- [x] 6.3 Export the existing lifecycle history to the readable file, preserving exact paired durations and explicitly marking unavailable legacy data.

## 7. Re-verification

- [x] 7.1 Run the assignment-logger harness and targeted repository-convention tests.
- [x] 7.2 Run the independent preflight, complete Maven verify, strict OpenSpec validation and OpenSpec doctor; confirm no production or unrelated user changes were introduced.
