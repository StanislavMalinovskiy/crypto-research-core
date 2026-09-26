# Verification evidence

## Preconditions and scope

Main confirmed four real no-op native dispatches: Sol medium, Sol high, Luna xhigh and Luna max. The official custom-agent settings precedence and model effort references are recorded in design.md; pinned settings require separate Luna role files. The migration remains CORE_RISK with the reproducibility/integrity trigger. No product, database, DEFAULT semantic, mode-switch, hook, IDE/MCP or repository-log changes were made.

## Behavioral RED and frozen tests

Before implementation, `.\mvnw.cmd -Dtest=RepositoryConventionsTest test` exited 1: 27 tests, 3 failures, zero errors or skips. The executed closure-order assertion expected true and returned false; the lean policy assertion found missing risk, review, repair, escalation and restoration obligations; role/status assertions also failed. Missing renamed files are not relied on as behavioral RED.

Before logger implementation, `pwsh -NoProfile -File .codex/scripts/log-agent-activity.tests.ps1` exited 1. Architect REVIEW expected exit 0 and returned 2; Escalation challenge ESCALATE return expected 0 and returned 2; Architect DOCS_CLOSE with PLAN_READY expected rejection exit 2 and returned 0. These failures establish behavior independently of new argument-validation cases.

Frozen SHA-256 values, rechecked unchanged through GREEN and documentation closure:

| Test path | SHA-256 |
|---|---|
| `src/test/java/io/cryptoresearch/RepositoryConventionsTest.java` | `7F22AA29A66695BEE263A0D297301D742A03AD5A5017CB6364C3DEF1846FB12D` |
| `.codex/scripts/log-agent-activity.tests.ps1` | `5C494E9368B2164593ECE00D9F1C3DB85A5C097324BD06F97F863A7785E40082` |

Main retains the pre-implementation diff and Builder RED/GREEN handoffs in the task's external safety directory. No `.codex-logs` write was used; logger tests create and clean temporary logs. Main released documentation implementation only after both valid RED results.

## Targeted GREEN and independent review

- `.\mvnw.cmd -Dtest=RepositoryConventionsTest test`: exit 0; 27 tests, zero failures, errors or skips.
- `pwsh -NoProfile -File .codex/scripts/log-agent-activity.tests.ps1`: exit 0; focused scenarios including 72 role/phase/status matrix cases, duration/token preservation and legacy export passed.
- `openspec validate align-lean-multiagent-workflow --strict --no-interactive`: passed.
- `git diff --check`: passed.

Fresh Reviewer `lean_fresh_reviewer` returned APPROVE with no blockers, `red_suspect = false` and `tests_changed_after_red = false`. CI-01 and CI-15 passed; remaining invariants were not applicable to unchanged product behavior. Reviewer verified substantive plan consistency. No PLAN_READY artifact hash was recorded, so the evidence does not claim a cryptographic timeline proof of planning immutability. Policy checks test named clauses and mutations; independent review remains responsible for broader semantic contradictions.

Architect DOCS_CLOSE marked only verified tasks and recorded this evidence; requirements, design decisions, roles and tests were not changed in this phase.

## Complete gate

Main independently reported all required checks passed:

| Command | Result |
|---|---|
| `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1` | Exit 0 |
| `docker version` in the host context | Client/server 29.8 and Docker Desktop 4.91 available |
| `.\mvnw.cmd clean verify` | Exit 0, BUILD SUCCESS, 57.326 seconds; Surefire 69 and Failsafe 44, all with zero failures, errors or skips |
| `openspec validate --all --strict --no-interactive` | Exit 0, 15/15 items valid |
| `openspec doctor` | Exit 0 |
| `git diff --check` | Exit 0 |

Root guidance and configuration already complied and remain unchanged. The owner's earlier hook deletions and MCP removal are committed baseline state, not changes introduced by this migration.

## First archive attempt and scoped recovery

After the first complete-gate PASS, Main recorded checkpoint `ca008960ed842ad8b79b75074057f4ac05f288d4` and proved raw-byte Git restoration. Architect checked the checkpoint ref, HEAD, real index, all seven pre-archive hashes and absent archive destination, then ran `openspec archive align-lean-multiagent-workflow --yes` successfully. The CLI updated two requirements, added two and created the dated archive copy.

Post-archive strict all-item validation returned 13 passes and one failure: untouched active change `adopt-gpt6-agent-routing` replaces the shared `Executable agent guidance` requirement and does not carry the newly added `Bounded technical escalation` scenario. Doctor, diff checking and all 27 repository conventions passed. Completion was blocked by the required strict check.

Main restored all seven pre-archive files byte-for-byte from the Git checkpoint and removed only the validated six-file archive copy. HEAD and the real index remained identical. The external restoration proof passed; no main spec or other change was manually edited.

Architect documentation/spec repair round 1 moves the exact technical-escalation scenario, without changing any clause, into its own ADDED requirement. Its new requirement sentence summarizes the same existing obligations. This changes spec organization only: fixed risk, observable behavior, scenario text, review rules, repair budget, implementation and frozen tests are unchanged. The shared MODIFIED requirement now retains its existing scenario-name set, allowing the older active change to remain untouched after archive. The revised delta adds three requirements and modifies two.

## Repair round 1 re-review

The same fresh Reviewer returned APPROVE after verifying that the relocated scenario is identical, the structural conflict is resolved and frozen test hashes are unchanged. Reviewer reported `red_suspect = false` and `requires_new_red = false`. Independent strict all-item validation passed 15/15 and diff checking passed. This DOCS_CLOSE records only that evidence and changes no contract semantics.

## Renewed complete gate

After repair round 1 re-review and DOCS_CLOSE, Main reran the entire gate: integrity preflight exited 0; host Docker client/server 29.8 was available; `.\mvnw.cmd clean verify` exited 0 with BUILD SUCCESS in 58.381 seconds. Surefire ran 69 tests and Failsafe ran 44, all with zero failures, errors or skips. Strict all-item validation passed 15/15, doctor exited 0 and diff checking exited 0. Task 4.1 is complete again on this renewed evidence.

## Successful second archive and post-checks

Main created fresh checkpoint `1fa191caeaa72a3796737bc585a1e7ce5e721edb` at `refs/checkpoints/align-lean-multiagent-workflow-20260927-r1` and proved raw-byte restoration. Architect rechecked its reference, HEAD, real index, all seven pre-archive hashes and absent archive destination immediately before `openspec archive align-lean-multiagent-workflow --yes`. The command exited 0, applying three added and two modified requirements and creating this archive.

Main's post-archive checks passed: strict all-item validation 14/14, doctor exit 0, `.\mvnw.cmd -Dtest=RepositoryConventionsTest test` exit 0 with 27 tests and zero failures, errors or skips in 2.849 seconds, and diff check exit 0. An informational message about the older `adopt-gpt6-agent-routing` change's already-existing GPT-6 requirement remains; that older change and its pre-existing archive compatibility concern were left untouched.

Main proved the 13 changed paths exactly matched the archive allowlist, with no unexpected paths. All six archived files matched checkpoint bytes before this authorized bookkeeping update; HEAD and the real index matched the checkpoint. Programmatic comparison confirmed all five delta requirements equal the resulting main spec and all eleven unaffected main requirements remain unchanged.

Scoped restoration was necessary after the first attempt and succeeded. The second attempt required no restoration. Both checkpoint refs are retained; the first is `refs/checkpoints/align-lean-multiagent-workflow-20260927` at `ca008960ed842ad8b79b75074057f4ac05f288d4`. Repair accounting remains at round 1 with no reset.

## Final scope and handoff

The implementation changes six role configurations, renaming `builder_luna` to `builder_luna_xhigh`; `builder_sol` already existed in the baseline. It updates three workflow/invariant/testing documents, the Java repository-conventions tests, and the existing logger and its focused tests. This new archived change and the CLI-synchronized repository-conventions main spec complete the scope. Root guidance and configuration already complied and remain unchanged.

The owner's `.codex/hooks.json` and `.codex/hooks/log-subagent.ps1` were already absent at baseline, and the earlier removal of the IDE MCP block remains unchanged. Those are committed baseline facts, not current task hunks. Other active changes, historical records, product code and `.codex-logs` remain untouched. Existing staged content and the real index are preserved. No branch commit or push was made; only raw checkpoint commits and their refs were created.

Tasks 4.2–4.4 are marked complete on actual checkpoint, archive and post-check evidence. This final update changes only this archive's tasks and evidence; no accepted spec or contract semantics were edited. Main will rerun post-checks after this bookkeeping before declaring DONE. No owner decision remains pending.
