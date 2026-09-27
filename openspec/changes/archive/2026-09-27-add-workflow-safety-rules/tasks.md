## 1. Test-first safeguards

- [x] 1.1 Add three mechanical presence checks in RepositoryConventionsTest; run `mvnw.cmd -Dtest=RepositoryConventionsTest test`, verify each expected missing-rule assertion, record requirement/expected/actual and freeze the test hash before docs/role edits.
- [x] 1.2 Add the overlap and session recovery rules to MULTIAGENT, the RED reason rule to TESTING, and short Architect/Builder instructions; verify targeted GREEN and unchanged frozen hash.

## 2. Review and verification

- [x] 2.1 Self-review the stable diff under the explicit owner exception; verify exactly three added requirements/scenarios, unchanged routing/DEFAULT/repair limits, and no unrelated edits.
- [x] 2.2 Run `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, same-context `docker version`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, and `git diff --check`; record results before the scoped Git checkpoint and archive.

## Closing procedure

After all implementation tasks pass: record a raw-byte pre-archive checkpoint using a temporary Git index; run `openspec archive add-workflow-safety-rules --yes`; then run `openspec validate --all --strict --no-interactive`, `openspec doctor`, `mvnw.cmd -Dtest=RepositoryConventionsTest test`, and `git diff --check`. Inspect archive mutations and preserve the real index. On failure use only path-scoped Git restoration from the checkpoint plus removal of this created archive copy; never reset-hard. Report actual archive/post-check outcomes in the final handoff.

## Verified RED evidence (before freeze)

- Command: `mvnw.cmd -Dtest=RepositoryConventionsTest test`.
- Run completed 2026-09-27 15:38:00 +05:00: 30 tests, 3 assertion failures, 0 errors, 0 skipped; compilation and discovery succeeded. Existing 27 tests passed.
- Path frozen after inspecting all three reasons: `src/test/java/io/cryptoresearch/RepositoryConventionsTest.java`.
- SHA-256: `89E3B86D740536D8141A85A398669C7F37E5BDDB99486893A72F1C25D0372531`.
- Pre-implementation `git diff` captured in the execution transcript: only 38 added lines in this test; status showed no docs/role modifications, only this test and the active change.

| Failing test | Requirement / acceptance criterion | Expected | Actual |
|---|---|---|---|
| planningRequiresActiveChangeOverlapEvidence | Active-change overlap check before PLAN_READY | No missing overlap markers in workflow and Architect role | Assertion `missing workflow safeguard markers`: absent `openspec list`, `same specs`, `resolve first`, `safe to proceed` and associated handoff markers in those exact paths |
| testFreezeRequiresVerifiedRedReason | Verified RED reason before test freeze | No missing pre-freeze reason markers in TESTING and all three Builder roles | Same assertion: absent `before freeze`, `each failing test`, `requirement/acceptance-criterion`, wrong-target/assertion/setup correction markers and associated evidence markers |
| lostSessionsPreserveRoleEvidenceAndRepairBudget | Lost Builder or Reviewer session recovery | No missing recovery markers in workflow | Same assertion: absent `cannot be resumed`, `Main starts a fresh session`, `same role`, `configured model/effort`, replacement disclosure and budget/review continuity markers |

Each failure is the expected absence of the newly required policy in its intended file, not a wrong target, wrong assertion, setup or infrastructure error. No test correction was needed before freeze.

## BUILD_DONE and self-review handoff

The three per-test requirement/expected/actual rows above are repeated here by reference as the BUILD_DONE RED reason evidence: overlap markers absent in workflow/Architect; verified-RED markers absent in TESTING/all Builders; session-recovery markers absent in workflow. Expected in every case: no missing required markers. Actual RED in every case: the named missing-marker assertion, with no execution error.

Targeted GREEN: the same command passed 30/30 at 15:39:26 +05:00. Frozen SHA-256 remained `89E3B86D740536D8141A85A398669C7F37E5BDDB99486893A72F1C25D0372531`; tests_changed_after_red=false. No repairs or session replacements were needed.

Changed implementation paths: docs/AGENT_WORKFLOW_MULTIAGENT.md, docs/TESTING.md, .codex/agents/architect.toml, .codex/agents/builder_sol.toml, .codex/agents/builder_luna_xhigh.toml, .codex/agents/builder_luna_max.toml, src/test/java/io/cryptoresearch/RepositoryConventionsTest.java. This active change adds only its CLI metadata, proposal, design, tasks and delta. Archive is authorized to add the three requirements to openspec/specs/repository-conventions/spec.md and move this change to its dated archive directory.

Self-review: APPROVE under the owner's single-agent exception, not an independent Reviewer verdict. Exactly three ADDED requirements with one scenario each; pre-freeze exception explicit, post-freeze TEST_SPEC_ERROR unchanged; Main-only replacement retains independence/accounting. Models, configured workflow switch, DEFAULT, routing, repair limits and product code unchanged. Initial worktree clean; no unrelated owner changes touched.

| Invariant | Applicable | Evidence | Verdict |
|---|---|---|---|
| CI-01 | Yes, workflow evidence | Per-test RED reason recorded before freeze and supplied in BUILD_DONE; replacement retains evidence | PASS |
| CI-15 | Yes, repair bounds | Recovery explicitly keeps repair count and remaining budget; existing two-plus-one rule unchanged | PASS |
| CI-02 through CI-14 | No product behavior change | No ingestion, storage, arithmetic, temporal, ordering, concurrency, module API, transaction or provider implementation changes | Not applicable |

Full pre-archive gate: integrity preflight exit 0; Docker client/server reachable in the unrestricted host context; `mvnw.cmd clean verify` BUILD SUCCESS at 15:40:42 +05:00, 78 Surefire + 48 Failsafe tests, 0 failures/errors/skips. Strict OpenSpec validation 16/16; doctor exit 0; diff check exit 0. The existing informational warning that adopt-gpt6-agent-routing cannot archive its already-present GPT-6 requirement remains out of scope, as documented in the overlap disposition.
