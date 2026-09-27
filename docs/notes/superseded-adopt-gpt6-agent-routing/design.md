## Context

The task starts in native Codex MULTIAGENT because `.codex/config.toml` contains exact Boolean `enabled = true`. Existing user changes to this switch, deleted hooks and deleted archive pages must be preserved. Current role files and `RepositoryConventionsTest` encode GPT-5.6 routing. The activity logger validates role/phase/status combinations and still accepts Architect REVIEW. The main repository-conventions specification predates the combined Builder protocol; this change explicitly reconciles that tracked inconsistency.

## Goals / Non-Goals

Establish one current routing contract across executable configuration, current documentation, accepted specifications after sync, and the user's Notion guidance. Keep native transport, task-fixed mode, compact behavioral evidence, independent review, bounded repairs and complete verification.

No module is affected. No transaction boundaries, financial arithmetic, dataset identity, provider logic, migrations, dependencies or infrastructure are added or changed. Product tests remain intact. Historical OpenSpec archives are not rewritten merely to replace dated model names.

## Decisions

### Model and role mapping

| Responsibility | Role | Model | Reasoning |
|---|---|---|---|
| Main and project default | top-level | `gpt-6-sol` | `medium` |
| Architect | `architect` | `gpt-6-sol` | `high` |
| Ordinary Builder | `builder_luna` | `gpt-6-luna` | `xhigh` |
| Explicit ordinary max option | `builder_luna_max` | `gpt-6-luna` | `max` |
| CORE_RISK Builder | `builder_sol` | `gpt-6-sol` | `medium` |
| Reviewer | `reviewer` | `gpt-6-sol` | `medium` |
| Escalation | `escalation` | `gpt-6-sol` | `high` |

Main selects Builder only after Architect sets effective risk. Ordinary work defaults to xhigh; an explicit max selection and reason are recorded in the capsule. A separate max role makes the effective configuration inspectable. CORE_RISK always uses Sol medium. Keeping the old benchmark-specific Builder name would conceal the new route and is rejected.

Every task receives a fresh Reviewer, including documentation-only work. The same Reviewer may review bounded repairs. Architect has PLAN and DOCS_CLOSE only. This implements the user's explicit Reviewer model instead of retaining Architect high as an implicit reviewer. Review duties and repair authorization remain otherwise unchanged.

### Evidence and validation

Effective risk is `CORE_RISK` because agent integrity and review routing are affected. `test_mode = RED_REQUIRED` applies to observable executable protocol validation: accepting the new Builder roles, rejecting Architect REVIEW, and enforcing the configured routing map. Builder derives focused cases before changing the logger/configuration, observes assertion failure, records the test hash and pre-implementation diff, then implements and proves unchanged-test GREEN. Missing files, compilation failures and unknown CLI parameters alone are not behavioral RED; use a scenario that executes and asserts the rejected/accepted routing result.

TOML value replacement and documentation edits individually require no artificial behavioral test. Update existing repository governance tests to the new contract while preserving test-integrity coverage and all application tests. Focused checks are `pwsh -NoProfile -File .codex/scripts/log-agent-activity.tests.ps1` and `mvnw.cmd -Dtest=RepositoryConventionsTest test`. Main owns the complete gate after review.

### Bounded ownership and change budget

- Architect: this active change, `AGENTS.md`, `README.md`, `docs/AGENT_WORKFLOW.md`, `docs/AGENT_WORKFLOW_MULTIAGENT.md`, and directly affected current documentation links. Root-guide edits are explicitly within the requested all-documentation migration.
- Builder: `.codex/config.toml` model/default fields only; `.codex/agents/*.toml`; `.codex/scripts/log-agent-activity.ps1`; `.codex/scripts/log-agent-activity.tests.ps1`; relevant agent-policy portions of `src/test/java/io/cryptoresearch/RepositoryConventionsTest.java`. Export logging may change only if required to preserve new-role output; unrelated exporter behavior is excluded.
- Main: verify/update only model defaults in the workstation configuration, synchronize relevant Notion guidance, inspect and remove only authorized old-model benchmark artifacts, run final checks, and sync/archive the approved change.
- No writes to application source, product test scenarios, dependencies, migrations, accepted ADRs, unrelated active changes or the mode switch.

Two ordinary implementation repairs and at most one Reviewer-authorized third repair remain available. A surviving blocker escalates; protocol correction and infrastructure context retry do not consume a repair.

### Applicable invariants

CI-15 applies to bounded agent work and repair: root guidance and the workflow retain the two-plus-one limit and bounded escalation. CI-01 through CI-14 concern domain evidence, persistence, deterministic research or module/provider boundaries; they are not affected because this change does not alter those systems. Test execution integrity and native transport are additionally controlled by root guidance and the repository-conventions delta, not invented domain invariants.

## Risks / Trade-offs

- Current repository guidance and accepted main specs differ during implementation: retain this explicit migration record and synchronize only after implementation and verification.
- Lower Reviewer reasoning may affect review depth: preserve the same invariant/evidence obligations and Sol high escalation triggers; do not claim benchmark-proven quality.
- Model availability can differ by session: use the explicit configured models when available and report unavailable models rather than silently substituting old ones.
- Old benchmark cleanup can erase useful product evidence if scoped broadly: Main resolves exact targets and retains application regression tests and historical accepted changes.
- Running sessions retain their loaded roles: configuration changes govern newly launched sessions; Main reports this boundary.

## Migration Plan

Record the active delta, observe focused routing RED, change configurations and logger, then run focused GREEN. Update current repository and Notion guidance and remove the agreed obsolete artifacts. A fresh Sol medium Reviewer reviews the stable diff and evidence. Architect closes documentation after approval; Main runs the independent preflight, complete Maven lifecycle, strict OpenSpec validation, doctor and diff checks. Synchronize and archive only after the required verification is complete.

Rollback restores only this change's model/role/routing/documentation edits; preserve all pre-existing user modifications and application work. No database or runtime rollback exists because neither changes.
