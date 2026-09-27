## Context

See `proposal.md` for motivation. Baseline inspected: `86fd6eed8fe3c1f36e732078c46a3831c6acaff6`; worktree was clean before this planning change. Selected mode is MULTIAGENT (exact enabled Boolean true); configuration is read-only for this task.

Current active guidance requires Architect planning/OpenSpec, three fixed Builder routes, fresh review for either CORE_RISK or accepted normative-spec changes, hash-backed RED evidence, a broad invariant report, ordinary assignment logging and unconditional approval closure. `RepositoryConventionsTest` mechanically enforces those clauses. The integrity preflight independently checks disabled Java tests, Maven selectors and the exact CI gate; it does not inspect RED hashes or pre-implementation diffs.

Affected ownership: repository governance and its existing convention-test class; no product module or module-level guide is affected. No transaction boundary, persistence schema, dependency, infrastructure or accepted ADR changes. Existing closure details are not ordinary PLAN input: this plan changes their tier applicability and output presentation only, and preserves their established safety contract through the accepted spec and current convention guards.

### Classification of this migration

- `risk = CORE_RISK`; `risk_triggers = TR-11`: normative workflow and executable test-integrity policy affect reproducibility/integrity.
- Other triggers assessed: TR-01/02/03/04/05 do not apply because storage, transactions, concurrency, retry and data migration semantics are unchanged; TR-06/07/08 do not apply because product time, arithmetic and identity/ordering are unchanged; TR-09/10/12 do not apply because provider recovery, module boundaries and secrets/security behavior are unchanged.
- `test_mode = RED_REQUIRED`: convention enforcement behavior changes. Route remains Builder Sol medium and a fresh Reviewer under the current workflow. Proposed cheaper routes cannot exempt this migration from current protection.
- CI-01 applies to truthful evidence and failure visibility; CI-13 to preventing a weaker route from bypassing required correctness; CI-15 to existing bounded repair/gate handling. CI-02 through CI-12, and CI-14, have no changed product behavior; review must explicitly justify these non-applicabilities in the full matrix. Their definitions and TR-to-CI map remain unchanged.
- Budget: five standard change artifacts, fourteen existing implementation files and one accepted spec updated only by archive; zero production files, scripts, new infrastructure files or additional test classes. No broad workflow rewrite beyond the ledger below.
- Overlap: `openspec list` shows only `define-solana-data-provider-contract` besides this change. It owns `solana-data-contract`, not `repository-conventions`: same-spec overlaps `none`; safe to proceed. Its implementation and checkboxes are untouched.

## Goals / Non-Goals

Preserve a small root router, real-path references, conditional domain reads, conditional closure loading, model metadata and all existing unaffected safeguards. Add procedural tier selection inside the existing planning/routing responsibility, not another role, phase, state machine or evidence artifact. No estimated token savings are claimed as measured outcomes.

DEFAULT retains Control/Developer, its existing OpenSpec policy, RED rules, complete local gate, review and archive ownership. New tier exemptions apply to MULTIAGENT only. Existing archived changes and `docs/agents/instruction-diet-audit.md` remain historical evidence; this active design records explicit supersession instead of rewriting that audit.

## Decisions

### Exact future files

| Path | Planned change |
|---|---|
| `AGENTS.md` | Route MULTIAGENT through tiers; narrowly qualify OpenSpec/full-gate obligations; preserve DEFAULT and 150-line router |
| `docs/AGENT_WORKFLOW_MULTIAGENT.md` | Tier precedence, routes, Main permissions, review and RED policy, semantic freeze, optional assignment logging; retain existing states/repairs |
| `docs/TESTING.md` | Tier-specific test-mode/semantic-freeze rules and TRIVIAL gate exception; retain all test levels, integrity and DEFAULT rules |
| `docs/CORE_INVARIANTS.md` | Full matrix only for CORE_RISK; touched CI for NORMAL/CONTRACT; tier-aware review routing, definitions/map unchanged |
| `docs/agents/close-archive.md` | NORMAL full-gate-only branch and CONTRACT/CORE_RISK full closure scope; compact output guidance only; no checkpoint/recovery weakening |
| `.codex/agents/architect.toml` | Non-TRIVIAL trigger/tier planning, NORMAL contract without OpenSpec, conditional overlap, eligible review/touched CI, no RED hash requirement |
| `.codex/agents/builder_sol.toml` | Read bounded contract plus OpenSpec where applicable; semantic freeze and compact evidence |
| `.codex/agents/builder_luna_xhigh.toml` | Identical normative Builder body; metadata unchanged |
| `.codex/agents/builder_luna_max.toml` | Identical normative Builder body; metadata unchanged |
| `.codex/agents/reviewer.toml` | CORE_RISK fresh review only; full matrix and semantic test verification; no RED hash/pre-diff requirement |
| `.agents/skills/openspec-propose/SKILL.md` | Brief entry routing: MULTIAGENT TRIVIAL/NORMAL do not create an OpenSpec change; retained actual OpenSpec procedure |
| `.agents/skills/openspec-apply-change/SKILL.md` | Brief entry routing: NORMAL uses existing Builder contract outside this change-based skill; no inferred/made-up change |
| `openspec/config.yaml` | Context reads affected change when the selected route has one; artifact/operation rules otherwise unchanged |
| `src/test/java/io/cryptoresearch/RepositoryConventionsTest.java` | Minimal tier-aware positive/negative convention guards; update obsolete clauses before RED |
| `openspec/specs/repository-conventions/spec.md` | Resulting accepted behavior, updated only by authorized archive |

Planning files created now: `openspec/changes/workflow-simplification/.openspec.yaml` (CLI metadata), `proposal.md`, `design.md`, `tasks.md`, `specs/repository-conventions/spec.md`. They are the only writes in PLAN. Archive later moves these exact artifacts to the CLI's dated archive directory; no additional process file is introduced.

No edits to `.codex/config.toml`, `.codex/agents/escalation.toml`, `docs/AGENT_WORKFLOW.md`, integrity/logging scripts, `.github/workflows/quality-gate.yml`, Maven files, other five local skill bodies or explore examples. Their current bounded responsibilities remain valid. The propose/apply entry notes prevent generic change selection from recreating removed NORMAL overhead; archive/verify/sync/update remain inherently change-scoped, and explore remains optional investigation rather than a mandatory NORMAL overlap gate.

### Tier precedence and unchanged Builder routing

Main answers only `strictly TRIVIAL` or `not TRIVIAL`. This is a narrow permission check, not a TR assessment. Any exclusion or uncertainty sends the task to Architect, without Main choosing NORMAL/CONTRACT/CORE_RISK or ROUTINE/STANDARD.

Architect evaluates every unchanged TR-01..TR-12 first. A match, or unresolved credible trigger uncertainty, yields CORE_RISK. With no triggers, an observable/public or accepted behavior change is CONTRACT; unchanged accepted behavior with internal implementation work is NORMAL. Uncertainty between those two chooses CONTRACT. Mixed scope takes the highest applicable tier; splitting labels must not hide a coupled trigger.

The procedural tier is recorded in the existing contract/handoff, not a new file or canonical status. Keep existing `risk = ROUTINE | STANDARD | CORE_RISK` for routing: any trigger gives both tier and risk CORE_RISK. With none, Architect still chooses ROUTINE for small well-understood bounded implementation, STANDARD for broader reasoning, independent of NORMAL versus CONTRACT. Thus either NORMAL or CONTRACT can use either existing Luna route. No automatic NORMAL=xhigh or CONTRACT=max mapping and no model/effort change. Tier and risk remain fixed at PLAN_READY; risk downgrades require owner direction. New scope returns with CONTRACT_CHANGED, with RISK_CHANGED when applicable. Discovering an observable-contract change during NORMAL requires Architect replan, required OpenSpec artifacts and a new PLAN_READY before dependent work, never silent continuation as NORMAL.

Examples are conditional, not allowlists: a logging edit touching secrets matches TR-12; a telemetry change affecting authoritative evidence matches TR-11; test-only changes to point-in-time/integrity or persistence requirements match their TRs; a config change affecting concurrency/retry matches TR-03/04. These are CORE_RISK despite their file type. A new externally promised metric/log contract without a trigger is CONTRACT, not NORMAL merely because it is telemetry. A bugfix restoring an already accepted contract can be NORMAL; changing that accepted contract is CONTRACT; either becomes CORE_RISK on any trigger. Source comments are not TRIVIAL. Workflow/skills/normative policy changes require Architect assessment and integrity-policy changes such as this migration match TR-11.

### Main's exact TRIVIAL permission

Main may directly edit only clearly non-normative prose/formatting in already identified documentation/text, with no behavioral meaning change. It may fix a typo, punctuation or purely editorial layout in non-normative notes. It must preserve factual claims, historical results, link/path targets, scope and requirements. A non-normative location is necessary but not sufficient: changing research conclusions, task scope or operational guidance is not automatically trivial.

Excluded: any source code (including comments), configuration, script, OpenSpec artifact, ADR, workflow or skill, normative document, API/path/code rename or behavioral meaning. Mixed normative/non-normative files are not presumed safe. Main must not create a new exemption, reclassify a trigger, edit code after a failed lightweight test or use TRIVIAL to repair convention policy. If scope grows or cannot be proved strictly textual/non-normative, stop direct editing and route to Architect before dependent work. Preserve already made safe edits and owner changes; no destructive rollback is implied.

TRIVIAL runs `git diff --check` and `mvnw.cmd -Dtest=RepositoryConventionsTest test` with no subagent, OpenSpec, overlap check, review, DOCS_CLOSE or archive. Both must pass and actual test execution/counts must be visible. Failure means no DONE: Main may correct its own still-strictly-TRIVIAL text; implementation/policy failures go to Architect, infrastructure/pre-existing failures remain blocked. No new repair budget or escalation route is invented.

### Routes

| Work | Planning and implementation | Review | Completion |
|---|---|---|---|
| TRIVIAL | Main permission check and direct text edit | None | Main lightweight gate, DONE |
| NORMAL | Architect assesses all TRs, fixed bounded accepted-behavior contract and useful tests; PLAN_READY; risk-routed Builder | Same Architect, touched CI only | Main full gate, DONE; no OpenSpec/overlap/DOCS_CLOSE/archive |
| NORMAL bugfix | Same NORMAL route, `RED_REQUIRED` before fixing defect against accepted behavior | Same Architect verifies RED and final tests; touched CI | Same NORMAL full gate and DONE |
| CONTRACT | Architect assesses all TRs, active OpenSpec and overlap check; selects RED mode with reason (bugfix always RED); risk-routed Builder | Same Architect even for accepted normative-spec delta; touched CI | APPROVE, Architect DOCS_CLOSE, Main full gate/checkpoint, Architect CLI archive, Main post-checks, DONE |
| CORE_RISK | Architect active OpenSpec/overlap, fixed triggers and `RED_REQUIRED`; Builder Sol medium, valid RED and semantic freeze | Fresh Reviewer, full CI-01..CI-15 matrix with justified non-applicability | Same protected approval/closure/full-gate/checkpoint/CLI archive/post-checks/DONE |

The abbreviated owner flows ending `full gate -> DONE` do not remove CONTRACT/CORE_RISK archive. Documentation-only work still belongs to Architect without a Builder. Its tier governs review and closure; CORE_RISK RED_REQUIRED has no documentation-only waiver. If a CORE_RISK docs-only task has no honest executable behavioral RED, return the concrete blocker for contract clarification rather than fabricate failure or downgrade it. This migration has meaningful convention-test RED.

NORMAL's contract is the existing Main-to-Architect/Builder handoff with source paths to accepted requirements, scope, test mode, bounded files and verification; use code/tests and existing evidence for durable facts. No replacement proposal, state file or second task registry is introduced. Builder still does not write docs/OpenSpec; if necessary nonsemantic docs accompany NORMAL, Architect authors them in the existing DOCS responsibility before review, not a new DOCS_CLOSE phase.

### RED and semantic freeze

Precedence: CORE_RISK and every bugfix require RED. Other CONTRACT gets Architect's explicit RED_REQUIRED or RED_NOT_REQUIRED with reason; other NORMAL gets useful appropriate tests and a reason in the existing test-mode field (RED_NOT_REQUIRED unless Architect selects RED). Selecting RED voluntarily still invokes the same freeze. RED_NOT_REQUIRED removes only test-first sequencing, not verification or relevant coverage.

Keep named executed test, exact command, requirement/acceptance criterion, failing assertion and expected/actual for every establishing failure. Builder verifies that the failure is behavioral before freeze; wrong target/assertion/setup is corrected before freeze without reviewer permission. After valid RED, establishing tests and their expectations, fixtures, discovery and runtime behavior cannot be weakened, skipped, narrowed or changed. Report `tests_changed_after_red` honestly, including authorized changes; false is not permission to omit evidence.

A necessary establishing-test change remains TEST_SPEC_ERROR: preserve evidence, current reviewer authorizes `requires_new_red=true`, obtain new valid behavioral RED and then GREEN. Reviewer checks the final tests still encode the intended requirement, not just a claimed unchanged digest. Remove only content hashes and pre-implementation-diff snapshots collected solely for RED/freeze. Stable final diffs, ordinary code review, session-recovery evidence, integrity preflight, archive raw-byte hashes and scoped checkpoints remain. Do not add a freeze tool or new evidence file.

### Rule-change ledger

The rows below are the complete intended normative changes. Source locations are repository paths and headings; accepted-spec names identify exact blocks. Repeated renderings in listed roles/tests are reconciled together, not silently deleted. The earlier `docs/agents/instruction-diet-audit.md` ledger remains the historical relocation map; only rows concerning all-task OpenSpec, Main non-implementation, broad fresh review/CI, hash-backed RED, unconditional closure and mandatory telemetry are superseded here. All other obligations remain reachable unchanged.

| ID | Current source | Resulting rule / location | Reason |
|---|---|---|---|
| W01 | `AGENTS.md` Required reading: active change before any behavior/dependency/schema/architecture implementation | MULTIAGENT NORMAL/TRIVIAL exceptions; CONTRACT/CORE_RISK and DEFAULT retain OpenSpec; root routes to workflow | Owner tier scope, no global DEFAULT relaxation |
| W02 | `AGENTS.md` Required reading; `openspec/config.yaml` context; spec Task-scoped instruction disclosure / Routing before editing | Read affected change when that route has one; always retain applicable accepted sources; same locations | NORMAL cannot require a nonexistent artifact |
| W03 | Workflow Purpose: Main does not implement/classify | Main may only classify strictly TRIVIAL/not and edit strict TRIVIAL text; all other risk remains Architect; root/workflow | Narrow direct-edit permission |
| W04 | Workflow Architect planning; architect role; spec Architect-owned stable risk classification | Evaluate all TRs first; then NORMAL/CONTRACT, higher on doubt; fixed existing risk separately; same locations | Avoid tier labels bypassing TR protection or changing model routing |
| W05 | Workflow PLAN_READY requires active strictly valid artifacts; architect/Builder active-artifact reading | NORMAL uses bounded accepted-behavior handoff; CONTRACT/CORE_RISK keep valid artifacts; workflow and roles | Remove low-risk OpenSpec overhead without removing planning |
| W06 | Workflow/architect role/spec Active-change overlap check | Required only for CONTRACT/CORE_RISK, no overlap check for NORMAL/TRIVIAL | Owner exemption |
| W07 | Workflow/test docs/spec Executable agent guidance: RED for all observable changes | CORE_RISK and all bugfixes RED_REQUIRED; other CONTRACT Architect choice with reason; NORMAL useful tests | Owner test-mode policy |
| W08 | Workflow Builder evidence; all three Builder roles; Testing; reviewer role; Executable agent guidance | Keep requirement, command, test/assertion and expected/actual, remove only RED content hash and pre-implementation-diff requirement | Remove mechanical freeze overhead |
| W09 | Same freeze/TEST_SPEC_ERROR sources plus architect role | Preserve semantic freeze, reviewer authorization and new valid RED; no new hash; final tests reviewed, tests_changed_after_red reported | Integrity remains semantic and explicit |
| W10 | Workflow/Testing/Core invariants/architect/reviewer roles/spec Executable agent guidance review rule | NORMAL/CONTRACT same Architect; CORE_RISK fresh Reviewer; remove fresh review solely for normative spec | Explicitly authorized review simplification |
| W11 | Workflow review, Core invariants purpose, architect role | NORMAL/CONTRACT touched CI only; CORE_RISK full matrix including reasoned N/A; definitions unchanged | Reduce repetitive irrelevant review evidence |
| W12 | Workflow stable write/end-to-end; root Completion; spec Executable agent guidance | NORMAL skips DOCS_CLOSE; applicable Architect docs occur before review; CONTRACT/CORE_RISK retain approved closure | No artificial closing work for no-change tasks |
| W13 | Workflow end-to-end/diagram; spec Verified and recoverable archive closure; Conditional closure procedure | Tier-specific closure; NORMAL full gate only; TRIVIAL light gate; CONTRACT/CORE_RISK all existing archive protections | Separate no-archive routes without weakening archives |
| W14 | Root Completion and Testing general complete-gate statements | Explicit strictly TRIVIAL two-check exception; NORMAL/CONTRACT/CORE_RISK full gate unchanged; DEFAULT unchanged | Honor lightweight gate without accidental general skip permission |
| W15 | Workflow Assignment telemetry | Existing script only benchmark/debug/explicit measurement, not ordinary development | Owner context/cost simplification |
| W16 | Workflow Main context; closure gate presentation; spec Bounded Main context | Compact PASS/FAIL contract and full captured output with truthful counts/failures/skips | Keep Main context small while preserving failures |
| W17 | Propose skill entry routes new plan to OpenSpec; apply skill entry selects an active change | Scope entry notes to change-requiring routes, return NORMAL to existing Architect/Builder handoff; descriptions/actual skills unchanged | No unwanted dummy change or unrelated change auto-selection |
| W18 | Spec Task-scoped instruction disclosure / Rule-preservation review and Builder entry-point equivalence / Relocated safeguard verification | Preserve existing rules unless this explicit authorized ledger changes their semantics; retain route and safety checks for applicable tiers | Instruction-diet preservation cannot prohibit this authorized simplification |
| W19 | Convention LEAN_POLICY_CLAUSES, agentConfigurationViolations reviewer condition, planning overlap and closure helpers | Tier-aware mechanical positive/negative guards replace obsolete unconditional fresh-spec/new-hash/all-task closure assertions | Executable policy must test new contract, not old strings |
| W20 | Spec Test execution integrity / Guard cannot disable itself; Testing preflight statement | Full gates and CI keep independent preflight; explicitly authorized local TRIVIAL targeted gate is not complete Maven verification | No script/CI change and no contradiction about lightweight checks |

Preserved explicitly: DEFAULT mode selection/ownership/RED/full gate; single TR definitions; model/effort and three equivalent Builder bodies; docs-only Architect ownership; source/module/ADR responsibility and impact reads; valid behavioral RED reason checks; post-freeze current-reviewer authorization; no test-artifact production behavior; all states/reasons/repair budgets/escalation/session recovery; independent integrity preflight and full Maven/CI; owner-change protection; raw-byte archive checkpoint, hashes, before/after mutation accounting, CLI-only archive, post-checks and safe Git recovery; native transport; optional skill examples; 60% config unchanged.

### Compact gate output without new tooling

Main keeps command ownership and executes unchanged commands for every non-TRIVIAL full gate:

```powershell
pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1
mvnw.cmd clean verify
openspec validate --all --strict --no-interactive
openspec doctor
git diff --check
```

Docker preflight and host-context retry remain mandatory before Docker-dependent commands. Post-archive commands remain strict all validation, doctor, targeted RepositoryConventionsTest and diff check. TRIVIAL uses exactly its two authorized local checks, not a reduced `clean verify`. GitHub CI is unchanged and still runs its full gate.

Use plain shell redirection into an existing suitable log location or an ephemeral OS-temp directory, outside a Maven-cleaned target directory. Capture stdout/stderr, then save the command's exit code immediately before formatting or searching logs. No pipeline whose status comes only from a formatter, no selection/skip flags in the full gate and no new wrapper, committed evidence format or infrastructure. The optional location is output retention, not a new source of workflow state.

PASS: exact command/check, exit code 0, compact test/check totals including failures/errors/skips where relevant, PASS. Non-test checks say checks passed or count unavailable; never invent counts. A success marker without test execution is insufficient for a required test. A required skipped check is not PASS even if the process exited 0.

FAIL: exact command, captured nonzero exit code (or exit 0 plus explicit skipped/missing-required-check diagnosis), failed test/check/plugin, bounded relevant excerpt, full-output path. State remaining unrun checks rather than implying success. Keep required-failure ownership and infra retry unchanged. A bounded summary is not permission to hide failure, skipped tests, plugin errors or missing reports.

### Verification and migration sequence

1. Builder prepares every convention assertion and any helper/mutation fixture in the one existing test class before RED. Cover TRIVIAL boundaries, tier/trigger precedence, orthogonal routing, conditional OpenSpec/overlap/closure, review/matrix rules, RED precedence/freeze, optional logging and truthful gate output. Retain negative cases for missing CORE_RISK fresh review, omitted TR authority, skip/narrow flags, broken archive order and missing raw-byte recovery clauses.
2. Execute `mvnw.cmd -Dtest=RepositoryConventionsTest test` against unchanged active instructions. Require expected behavioral assertion failures; no compile/discovery/infrastructure failure counts. Record every failure's requirement/assertion/expected/actual and freeze only after verifying the reason. Prepare all helper changes before this run.
3. This migration follows the existing approved workflow until acceptance, including its current RED hash/diff evidence. Removing those obligations is the future behavior, not a retroactive waiver during this implementation. After RED, edit only authorized guidance/roles/skill entries/config context; do not alter frozen test helpers without TEST_SPEC_ERROR authorization and renewed valid RED.
4. Targeted GREEN; inspect all active guidance for obsolete unqualified obligations; test all three Builder normative bodies remain equal while model/effort metadata stays unchanged. Spec requirements are verified through this delta and strict validation before archive, never by expecting main-spec changes early.
5. Fresh Reviewer examines stable complete diff, ledger coverage, RED/GREEN, full CI matrix and budget. Architect DOCS_CLOSE after APPROVE; Main complete gate. Load the existing closure procedure only then, record its exact scoped checkpoint and use CLI archive. Main post-checks; failure restores exact archive-mutated paths from Git and removes only the newly created archive copy, preserving unrelated owner data, then active-change repair and full gate again. Never manually patch accepted specs to pass post-checks.

## Risks / Trade-offs

- Misclassifying text or telemetry as harmless -> strict Main exclusion list, Architect TR-first assessment and higher tier on doubt; mixed scope takes higher protection.
- Two labels look like two protocols -> tier only chooses existing procedure; unchanged risk only routes existing Builder. Use the existing contract/handoff, no new capsule/state file.
- No hash makes dishonest evidence harder to detect mechanically -> preserve actual assertion evidence and final semantic review; report changed tests honestly, require reviewer-authorized new RED for test changes. Mechanical integrity checks still reject skips and selectors.
- Same Architect reviews CONTRACT they planned -> explicit owner trade-off for no-trigger work; any integrity/persistence/etc. trigger overrides to independent CORE_RISK review.
- Optional OpenSpec could lose NORMAL intent after compaction -> source-linked accepted behavior, bounded existing handoff, code/tests and retained ordinary evidence; reopen planning on uncertainty, no invented memory-only requirements.
- Removing unconditional closure could remove archive protection -> no-archive branches named separately; CONTRACT/CORE_RISK retain all commands, checkpoints, raw bytes, concurrent-edit checks and restore protections; convention negative cases remain.
- Quiet gate output could mask skip or plugin failure -> capture exit code immediately, show executed totals and skipped status, preserve full logs and disclose checks not run. CI and integrity preflight scripts remain untouched.
- This migration could relax its own controls before review -> perform it under current CORE_RISK route, current RED evidence and fresh review. Future semantic changes require a new plan; do not use this proposal as permission to bypass current guards.

No unresolved owner choice is required for this bounded plan. If implementation discovers a need to change a model, script, TR definition, DEFAULT behavior or archive safety, that is outside this contract and returns to planning instead of being absorbed silently.

## Implementation evidence and DOCS_CLOSE

The initial PLAN-only boundary was respected; the owner subsequently authorized apply. On 2026-09-27, Builder completed convention tests, Architect updated the thirteen planned guidance/role/skill/config-context files, and a fresh Reviewer returned APPROVE. The contract did not change during implementation. Repair count is zero; `red_suspect=false`; `tests_changed_after_red=false`. The same Architect and Builder sessions were retained.

Both RED and GREEN used `mvnw.cmd -Dtest=RepositoryConventionsTest test`. RED against unchanged active instructions ran 37 tests: 6 expected assertion failures, 0 errors, 0 skipped. GREEN after guidance changes ran 37 tests: 0 failures, 0 errors, 0 skipped. These six RED failures were checked against the intended requirement rather than merely accepted because the run failed:

| Failing test | Requirement / expected | Actual on old instructions |
|---|---|---|
| `conditionalChangeRoutingDoesNotInventLowRiskArtifacts` | W01/W02/W17: conditional change routing and no dummy NORMAL artifacts; no missing policy markers | Root/config/skill entry guidance lacked the tier-specific routing markers |
| `leanWorkflowPolicyCoversRiskReviewRepairEscalationAndRecovery` | W03-W16: tier boundaries, review, RED and optional measurement rules; no missing clauses | Current workflow lacked the newly required tier and semantic-freeze clauses |
| `planningRequiresActiveChangeOverlapEvidenceOnlyForContractAndCoreRisk` | W06: overlap check limited to CONTRACT/CORE_RISK; no missing markers | Workflow/Architect still described an unconditional overlap check |
| `projectAgentConfigurationMatchesClosedRoutingProtocol` | W10/W19: CORE_RISK fresh review without unconditional normative-spec fresh routing; no configuration violations | Current role/guidance still enforced the old review route |
| `reviewAndSemanticFreezeAreConsistentAcrossActiveGuidance` | W07-W11: tier-aware review, touched/full CI and semantic freeze; no missing markers | Testing/Core invariants/Architect lacked new tier and reporting rules |
| `tieredClosureAndGateOutputKeepCompleteTruthfulVerification` | W13/W14/W16: conditional closure and truthful compact output; no missing markers | Existing closure lacked NORMAL/TRIVIAL applicability and no-archive clauses |

Existing local evidence paths (temporary output retention, not new repository infrastructure):

- `C:/Users/stasm/AppData/Local/Temp/workflow-simplification-red.log`
- `C:/Users/stasm/AppData/Local/Temp/workflow-simplification-green.log`
- `C:/Users/stasm/AppData/Local/Temp/workflow-simplification-preimplementation.diff`

This migration retained the old freeze evidence while changing the future rule: the test-file SHA-256 remained `F9260E94622E429BF2602759AA0C6358087B9CFDF3BE874216FCAB551C4BB1E6` through GREEN and DOCS_CLOSE, and its current diff matched the recorded pre-implementation diff. All test helpers were prepared before RED; no post-freeze test edits occurred.

Fresh Reviewer covered all 13 delta requirements, 43 scenarios and ledger W01-W20; CI-01/CI-13/CI-15 passed and the remaining CI entries were justified as not applicable. Three Builder instruction bodies remain identical; model/effort metadata, DEFAULT, TR definitions, archive hashes/safety, scripts, CI and product code remain unchanged. Strict change validation and diff checking passed during the implementation handoff.

The two skill edits used skill-creator guidance and changed entry routing only. Its local `quick_validate.py` invocation could not run because Python lacked `yaml` (`ModuleNotFoundError`); no dependency was installed. Unchanged frontmatter and bounded entry-routing checks passed, followed by the complete 37-test targeted convention GREEN. This limitation is disclosed and is not represented as a successful validator run.

Initial DOCS_CLOSE marked tasks 1.1, 2.1-2.4 and 3.1 complete. Main then completed the full gate and checkpoint-readiness checks below; final DOCS_CLOSE marks 3.2 and 3.3 complete (8/8). No archive has been performed; post-archive results and DONE are not yet claimed.

### Full gate and pre-archive readiness

Main reported every required command passed with exit code 0:

| Check | Result |
|---|---|
| `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1` | PASS |
| `docker version` in the host execution context | PASS |
| `./mvnw.cmd clean verify` | PASS: 96 Surefire tests and 48 Failsafe tests; 0 failures, 0 errors, 0 skipped |
| `openspec validate --all --strict --no-interactive` | PASS: 13/13 items |
| `openspec doctor` | PASS |
| `git diff --check` | PASS |

Full output is retained in `C:/Users/stasm/AppData/Local/Temp/workflow-simplification-3e5e1b32a73d44c79d3a2b3cc15d84c8/`: `integrity.log`, `docker.log`, `maven-full.log`, `openspec-full.log`, `doctor-full.log` and `diff-full.log`.

Pre-archive readiness checkpoint: `435a055eb1624bd7a3069d5efd1065966717b1e8`, held at `refs/codex/checkpoints/workflow-simplification-ready-20260927`. The checkpoint covered 20 task paths; raw-byte restoration proof passed. Main also verified the normalized temporary-index whitespace check for the complete task, including untracked artifacts. HEAD remained `86fd6eed8fe3c1f36e732078c46a3831c6acaff6`; real-index SHA-256 remained `33AED39100A523522075E8A5E6425E5F4DBF04D70CF6A95EF30DE2E3691479EF`.

This checkpoint proves readiness before the final status write; Main must refresh the final raw-byte snapshot afterward and provide that reference for the authorized archive. It is not a claim that archive or post-archive checks already ran. No semantic or reviewed-guidance changes accompany this status update.
