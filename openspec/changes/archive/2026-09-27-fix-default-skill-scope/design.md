## Context

See proposal.md. Baseline: `e7593814afef6ce47dc46c41e20628eebe50a85c`. Root guidance, `docs/AGENT_WORKFLOW.md`, `docs/AGENT_WORKFLOW_MULTIAGENT.md`, OpenSpec config and the propose skill already distinguish DEFAULT ownership from MULTIAGENT protocol. The accepted local-skill requirement contains two unqualified scenarios.

## Goals / Non-Goals

Make only those two scenarios mode-explicit, preserving the entire remaining requirement. No module, transaction, dependency, infrastructure, executable policy, test or framework changes; no applicable architectural ADR or module document changes.

## Decisions

- Use one complete MODIFIED requirement so CLI archive preserves all five scenarios. A direct accepted-spec edit is excluded by the project contract.
- Architect owns documentation. DOCS removes only the last empty line in `openspec/changes/archive/2026-09-27-instruction-diet-phase-1/specs/repository-conventions/spec.md`; historical wording remains unchanged. Archive later synchronizes the accepted spec through the CLI.
- Fixed `risk = CORE_RISK`, `risk_triggers = TR-11`: normative approval/verification integrity and mode authority. TR-01–TR-10 and TR-12 do not apply: no runtime, data, identity, provider, module or security changes.
- `test_mode = RED_NOT_REQUIRED`: documentation clarification matching existing guidance, with no executable change. Preserve existing tests; use `RepositoryConventionsTest`, strict delta validation, exact scenario comparison and EOF inspection. Main retains the complete gate.
- Core invariants: CI-01 applies to retaining truthful approval/verification evidence under root guidance and accepted repository conventions. CI-02–CI-06 do not apply (no gaps, retry, durable effects or transactions); CI-07–CI-11 do not apply (no result identity, replay, cutoff, ordering or concurrency); CI-12–CI-15 do not apply (no provider boundaries, fallback, transaction I/O or runtime work/waits). Definitions remain in `docs/CORE_INVARIANTS.md`.
- Change budget: four authored active artifacts plus CLI-generated metadata; one EOF-only historical edit; one accepted requirement synchronized at archive. Only the archive and explicit-authorization scenarios may change semantically. No other authored paths. Two ordinary repair rounds, third only with current-reviewer authorization; replanning does not reset accounting.
- Overlap: none. `openspec list` found `add-recorded-replay-operational-telemetry` (recorded-market-replay) and `define-solana-data-provider-contract` (solana-data-contract), neither touching repository-conventions.

## Risks / Trade-offs

Normative text could accidentally broaden DEFAULT obligations or discard scenarios. Mitigate through complete requirement comparison and fresh independent Reviewer; both CORE_RISK and accepted normative delta mandate fresh review. Existing convention tests do not prove natural-language meaning, so review must verify the two scenarios directly.

## Migration Plan

DOCS completes the EOF edit and targeted checks; fresh Reviewer evaluates the bounded diff. After approval, DOCS_CLOSE records verified tasks; Main runs the complete gate and checkpoint; authorized CLI archive synchronizes the single accepted requirement, then Main performs post-checks under the existing closure procedure. No product deployment or data migration.

## Verification evidence

DOCS checks passed on 2026-09-27: independent test-integrity preflight; `mvnw.cmd -Dtest=RepositoryConventionsTest test` (33 tests, no failures/errors/skips); strict change validation; `git diff --check`; explicit EOF/trailing-whitespace inspection of all six authored/generated files. Exact comparison confirmed all five scenarios remain, with only the two authorized scenarios differing. Historical diff removes one empty line only. Main reported fresh Reviewer APPROVE with zero repair rounds before DOCS_CLOSE. The complete gate, checkpoint, CLI archive and post-checks remain Main-coordinated closure work.
