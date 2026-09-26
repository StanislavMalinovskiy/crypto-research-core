## Context

Baseline observed during planning: `2bce4979413afc1f2d67fbe79737e4d935ca74f1`. Main reported four successful no-op native dispatches for Sol medium/high and Luna xhigh/max and matching callable-tool metadata. Earlier supplied revisions were superseded by owner commits during read-only preflight. Existing `adopt-gpt6-agent-routing` is preserved, whether tracked or untracked; it is not part of this change.

The accepted repository-conventions spec currently requires fresh review for every risk, forbids Architect REVIEW dispatch, and permits optional Luna max selection. Workflow, role files, convention assertions and logger implement those rules. This change intentionally migrates them through its delta; accepted main specs remain unchanged until archive.

No business modules, Java APIs, transaction boundaries, schema objects, dependencies or infrastructure components change. Applicable architectural sources remain unchanged. There is no module-level implementation work requiring new module guidance.

## Goals / Non-Goals

Produce one internally consistent workflow with explicit owner decisions, Architect risk ownership and deterministic routing. Preserve the existing mode switch, DEFAULT behavior, native-only transport, test freeze and full-gate obligations.

The change does not redesign telemetry, touch `.codex-logs`, add agents beyond declared roles, modify historical records or archive another change. Main performs checkpoint operations during closure; this design does not add a general checkpoint tool.

## Decisions

### Fixed PLAN contract

`risk = CORE_RISK` is fixed by the owner for this migration; Architect independently records `risk_triggers = reproducibility/integrity` because evidence integrity, review authority and archive preservation change. `test_mode = RED_REQUIRED`: executable conventions and logger routing behavior change. Main has no preliminary risk classifier role.

The sole active trigger definition belongs in the MULTIAGENT workflow; CORE_INVARIANTS, roles, root guidance and main specification reference it. The owner-supplied list covers persistence semantics; transactions; concurrency/locking; idempotency/retry; migrations/data loss; point-in-time correctness; financial/exact arithmetic; identity/ordering; provider gaps/reconnect/recovery; module boundaries; reproducibility/integrity; security/secrets. This planning record describes the migration input and is not a second active policy source. New risk/scope evidence reopens Architect PLAN using CONTRACT_CHANGED/RISK_CHANGED. Repairs never recalculate risk. Only the owner may authorize a downgrade.

### Explicit model roles

Main uses Sol medium; Architect and Escalation use Sol high; Reviewer uses Sol medium. ROUTINE maps only to Luna xhigh, STANDARD only to Luna max, CORE_RISK only to Sol medium. Rename `builder_luna.toml` to `builder_luna_xhigh.toml`; retain the distinct max role.

Main verified the [official custom-agent precedence documentation](https://learn.chatgpt.com/docs/agent-configuration/subagents): custom-file model and reasoning settings override spawn arguments. The [Sol model reference](https://developers.openai.com/api/docs/models/gpt-6-sol) and [Luna model reference](https://developers.openai.com/api/docs/models/gpt-6-luna) support the selected efforts. Together with the four native dispatch checks, this supports separate pinned Luna roles. An effort override on a single pinned role was rejected because it cannot reliably change the effective configuration.

### Review and author routing

Use this exact rule wherever routing is reproduced:

- ROUTINE / STANDARD implementation or non-normative docs → same Architect thread.
- Any CORE_RISK change → fresh Reviewer (new thread).
- Any change to an accepted normative OpenSpec spec → fresh Reviewer, regardless of risk.

Either fresh-review rule overrides the ordinary path. This task requires fresh Reviewer. Architect phases become PLAN, DOCS, REVIEW, REPAIR, DOCS_CLOSE and ARCHIVE; REVIEW is eligible only under the rule above. Documentation-only work skips Builder. Builder repairs return BUILD_DONE and review; Architect documentation repairs return for review. Current reviewer means Architect or Reviewer as determined by the rule. Each owns authorization of the third repair, confirmed TEST_SPEC_ERROR corrections and red_suspect.

Canonical workflow states remain PLAN_READY, BUILD_DONE, REPAIR, APPROVE, BLOCKED, ESCALATE, DONE. Escalation emits one verdict attribute: REPAIR maps to REPAIR, REPLAN maps to ESCALATE with CONTRACT_CHANGED/ESCALATION_REPLAN as a request to Architect, APPROVE maps to APPROVE, OWNER_DECISION maps to BLOCKED. Only Architect may issue the new PLAN_READY after replanning. Escalation cannot grant new repair capacity or reset it; exhaustion requiring another repair becomes owner decision. Owner intent ambiguity goes directly to the owner.

### Minimal logger adaptation

Observed `.codex/scripts/log-agent-activity.ps1` rejects Architect review and names `builder_luna`. Adapt only role, phase and status allowlists to the new routing, including Architect DOCS/REPAIR/ARCHIVE and Escalation ESCALATE for a REPLAN handoff. Preserve event format, timing, tokens, output paths, export behavior and aggregation. Update existing focused routing tests, keeping all test logs under their temporary directory. This is a necessary compatibility edit, not a telemetry redesign.

### Invariants

| IDs | Applicability and evidence obligation |
|---|---|
| CI-01 | Applicable to preserving accepted specs, active-change bytes and owner work during archive failure. Review the scoped checkpoint and restoration protocol. |
| CI-15 | Applicable to bounded repair, one escalation verdict and recovery loops with no reset of repair accounting. Verify all return edges. |
| CI-02–CI-06 | No provider or database behavior changes. Preserve their text and map corresponding trigger categories to these invariants. |
| CI-07–CI-11 | No computational identity, time, ordering or concurrency behavior changes. Preserve their text and connect workflow trigger references without weakening them. |
| CI-12–CI-14 | No module, provider or transaction boundary changes. Preserve their text and controlling sources. |

Trigger-to-invariant reconciliation: persistence and migration/data-loss concerns map to CI-01/03/04/05/06; transactions to CI-06/14; concurrency/locking and idempotency/retry to CI-03/04/05/11/15; point-in-time to CI-08/09; identity/ordering to CI-07/10/11; provider gaps/recovery to CI-01/02/12/13/15; boundaries to CI-12/14; reproducibility/integrity to CI-01/07/08/09/10/11. Financial/exact arithmetic and security/secrets remain independently mandatory via REPRODUCIBILITY and OPERATIONS; absence of a dedicated CI number cannot make those triggers inapplicable.

### Bounded change budget and ownership

Architect owns this change's four planning artifacts and task-scoped `docs/AGENT_WORKFLOW_MULTIAGENT.md`, `docs/CORE_INVARIANTS.md`, `docs/TESTING.md` guidance updates. Main may supply the owner's complete Mermaid flow verbatim; Architect must ensure every repair, escalation, risk-change, gate-failure and archive-recovery edge is closed. Architect edits docs only after Builder's valid behavioral RED.

Builder owns `src/test/java/io/cryptoresearch/RepositoryConventionsTest.java`, `.codex/scripts/log-agent-activity.tests.ps1`, the minimal logger allowlist implementation, and six role files (Architect, two named Luna Builders, Sol Builder, Reviewer, Escalation), including removal of the superseded Luna filename. `AGENTS.md` is allowed only for concise MULTIAGENT source/routing references if necessary. `.codex/config.toml` is inspected but needs no edit: its existing model and mode settings already comply. DEFAULT guide is read-only unless a purely non-semantic model reference correction is demonstrably necessary.

Maximum scope: three guidance docs, one optional root-guide edit, six role files plus the old-name removal, one Java convention test, two existing logger files, this active change and the single main spec plus archive tree generated by CLI closure. No extra script, dependency, production source, domain test, migration, `.codex-logs`, hook, IDE/MCP file or other change artifact is authorized. Report any actual expansion to Main before editing.

### Behavioral test contract

Builder derives tests from scenario groups T1–T7 below. Observe repository assertions fail against the old active docs/roles and logger before any implementation edit. Record exact expected/actual assertion, test hash and pre-implementation diff. Freeze the establishing tests through GREEN.

| ID | Required meaningful evidence |
|---|---|
| T1 | Exact model/effort/name mapping; reject former Luna name and obsolete Terra routing in active locations only. Preserve false/missing/invalid mode cases. |
| T2 | Architect sole risk ownership, risk_triggers/none capsule, one authoritative trigger definition, no repair reclassification, risk-change replan and owner-only downgrade. |
| T3 | Exact three-line review rule and precedence, Architect review eligibility, read-only fresh Reviewer for CORE_RISK and accepted normative spec changes. |
| T4 | Canonical states versus reason/verdict attributes, same-author repair, current-reviewer third-round permission, test-correction RED renewal and no budget reset. |
| T5 | Escalation four verdicts and all return paths; owner ambiguity direct to owner; REPLAN returns to Architect before a valid plan. |
| T6 | APPROVE → DOCS_CLOSE → full gate → checkpoint → archive → post-checks → DONE; ownership-specific failure routes and scoped restoration safety. |
| T7 | Real logger subprocess accepts Architect review/docs/repair/archive and renamed Luna build/repair, returns compatible states and rejects incompatible phases/statuses; temp logs only. |

Use dependency-free convention tests and focused PowerShell tests. Include valid and mutated synthetic guidance cases so assertions prove policy rejection, not only tautological source strings. Do not require accepted main specs to match before archive. Test rollback wording and lifecycle policy here; actual closure additionally checks exact paths and bytes. No artificial database tests are justified.

Targeted commands: `.\mvnw.cmd -Dtest=RepositoryConventionsTest test` and `pwsh -NoProfile -File .codex/scripts/log-agent-activity.tests.ps1`. Main full gate: `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `docker version`, `.\mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, `git diff --check`. Use the available unrestricted host context for Docker; do not supply unavailable escalation flags.

### Archive checkpoint and exact restoration

After approval, Architect performs only non-semantic DOCS_CLOSE. Main runs the full gate, records `git status --porcelain=v1` and the exact task allowlist, then creates a pre-archive checkpoint through a temporary Git index initialized from the observed HEAD and a `commit-tree` object held by a task-specific ref. The real index and branch tip remain unchanged. Only task-owned paths and the accepted repository-conventions spec needed for archive are added to that temporary index. Do not stage owner hunks, hooks or IDE/MCP files. Recheck concurrent edits and HEAD before archive; if task and owner hunks cannot be separated safely, BLOCKED is required.

Git clean/smudge or end-of-line conversion can change working-tree bytes, so a commit alone is not sufficient evidence of exact restoration. Record SHA-256 plus existence and raw-byte Git blob IDs (`hash-object -w --no-filters`) for the active-change files and accepted spec before archive; preserve the raw blobs in the checkpoint tree or an associated task-specific Git tree/ref. The checkpoint manifest records tracked/untracked/index state and original paths. Restoration uses these recorded bytes, not manual semantic reversal or checkout that may transform line endings. Index identity is checked before and after.

Architect runs `openspec archive align-lean-multiagent-workflow --yes` only after Main supplies the checkpoint. Expected mutations are removal of that active change, creation of its dated archive copy and update of `openspec/specs/repository-conventions/spec.md`. Main compares before/after porcelain status and actual hashes against this exact allowlist, then runs strict all-item validation, doctor, targeted repository conventions and diff checking.

On failure, verify no concurrent edits to affected paths, restore only the archive-modified exact paths and their original existence from recorded raw blobs, remove only the new archive copy, and verify pre-archive hashes and unchanged real index. Never reset-hard, checkout a whole tree, git clean, stash owner work or hand-reverse accepted-spec changes. If proof fails, stop BLOCKED before destructive action. Return the restored active change to Architect; apply the ownership-specific repair and existing budget, rerun the full gate, create a fresh checkpoint and archive again. The recovery mechanism creates no unlimited retry permission.

## Risks / Trade-offs

- Active docs will temporarily differ from accepted specs during implementation → the active delta explicitly tracks the migration; sync only during verified archive.
- Literal protocol checks may become brittle → use scenario-focused predicates and mutation tests, retain exact text only where the owner requires a verbatim rule.
- Archive may transform bytes or encounter owner edits → raw-byte checkpoint evidence, exact allowlist and fail-closed restoration.
- Logger's policy must grow to permit new phases → restrict edits to allowlists and focused regressions; no event schema change.

## Migration Plan

Strictly validate PLAN, obtain behavioral RED, implement scoped docs/roles/logger, run targeted GREEN, receive fresh Reviewer approval, close docs, run the full gate, checkpoint, archive through Architect and run post-archive checks. Existing owner files and other active changes remain untouched. No product rollout or database migration is involved.
