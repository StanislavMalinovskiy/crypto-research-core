## Context

See [proposal.md](proposal.md) for motivation and [repository-conventions](specs/repository-conventions/spec.md) for required behavior. The current root guidance and `docs/AGENT_WORKFLOW.md` make every task carry the complete supervised role graph even though `.codex/config.toml` already has `[agents].enabled = false`. The detailed protocol is useful when explicitly requested, but it is unnecessary context and process for ordinary work.

`enforce-test-execution-integrity` is complete but not archived. It must become the accepted baseline before this change removes its universal agent-orchestration requirements. `complete-stage-one-exit-gate` remains independently scoped and resumes afterward.

This change has no owning application module, transaction boundary, database impact, production dependency or infrastructure component.

## Goals / Non-Goals

**Goals:**

- Make DEFAULT small enough to use routinely without specialized agent orchestration.
- Keep test-first evidence and final verification strong enough to catch ordinary implementation mistakes and test gaming.
- Keep the existing supervised workflow available as MULTIAGENT without loading it into DEFAULT sessions.
- Make the selected mode deterministic from one user-controlled configuration value.

**Non-Goals:**

- Guarantee a particular token count or subscription usage.
- Automatically choose MULTIAGENT from task risk.
- Redesign the MULTIAGENT protocol or delete its tools.
- Change application architecture, runtime behavior or CI job structure.

## Decisions

### Exact boolean true is the only MULTIAGENT opt-in

At task start, exact `[agents].enabled = true` selects MULTIAGENT. `false`, a missing setting or an invalid value selects DEFAULT. Agents may recommend MULTIAGENT but never edit the switch or treat a recommendation as permission. The mode remains fixed for the current task unless the user explicitly restarts or continues it under the new setting.

Alternative considered: automatic risk-based escalation. Rejected because it makes cost and delegation unpredictable and removes the user's direct control.

### Separate common, DEFAULT and MULTIAGENT guidance physically

`AGENTS.md` retains common project invariants, required reading and Definition of Done, plus a short mode selector and DEFAULT summary. `docs/AGENT_WORKFLOW.md` contains the complete but concise DEFAULT workflow. A new `docs/AGENT_WORKFLOW_MULTIAGENT.md` receives the existing specialized roles, statuses, phases, manifests, task capsules, threat checks, routing budget, logs and telemetry.

Root guidance instructs agents to read the MULTIAGENT document only when the flag is true. All `.codex/agents/*.toml` files are MULTIAGENT role configurations; the top-level DEFAULT Developer is a separate user session and does not use the subagent Developer configuration.

Alternative considered: keep both modes in one workflow document. Rejected because DEFAULT sessions would continue paying the context cost of the full protocol.

### DEFAULT is outcome-oriented, not phase-oriented

DEFAULT has two user-controlled top-level sessions:

- Control owns the active OpenSpec contract, final documentation, test-first review of the stable diff, completion gates and archive.
- Developer owns specification-derived tests and implementation in one pass, then returns changed files, red/green evidence when applicable, full-gate results and risks.

DEFAULT has no required subagent statuses, behavior-free skeleton phase, separate Tester, manifest allowlist, 200-400 word capsule, threat-check mode, assignment telemetry or automatic three-round routing. One consolidated repair is allowed by default; a further repair pass is a user decision.

Alternative considered: retain the phase protocol but run every role in the Developer session. Rejected because it preserves overhead without adding independent judgment.

### Keep a small non-negotiable test contract

For changed observable behavior, Developer must run a targeted test before implementation and observe the expected behavioral assertion fail. Compilation, discovery, configuration, startup or infrastructure failure is not red. After valid red, the establishing test cannot be weakened, disabled, skipped or narrowed, and production code cannot recognize fixtures, profiles or known test values.

Documentation, comments, formatting, mechanical configuration, pure renames and internally covered refactoring may record that no new behavioral test is needed. Existing applicable checks and the complete gate still run. SQL, migrations, persistence, locking and idempotency continue to use PostgreSQL Testcontainers under the shared testing strategy.

The independent pre-Maven integrity script, Maven lifecycle, CI quality gate and semantic stable-diff review remain common safeguards. Repository convention tests verify only durable mode-selection and anti-bypass invariants; they do not encode DEFAULT phase bureaucracy or exact editorial prose.

Alternative considered: remove all workflow constraints from DEFAULT. Rejected because a Developer who owns both tests and code still needs a small verifiable boundary against initially green, weakened or test-specific solutions.

### Preserve MULTIAGENT as an opt-in operational protocol

The existing specialized workflow moves without substantive redesign to `docs/AGENT_WORKFLOW_MULTIAGENT.md`. Role configuration descriptions say they are MULTIAGENT-only. Phase scripts, repair logs and assignment telemetry remain available and mechanically tested where useful, but DEFAULT guidance does not reference them as required steps.

Alternative considered: delete the roles and tools. Rejected because independent test authorship and adversarial review can still justify their cost for selected work.

### Apply as an ordered governance migration

First verify, synchronize and archive `enforce-test-execution-integrity`. Then implement this change against that accepted main specification: repository tests go red first, documentation and configuration are reorganized, tests go green, and full gates run. After synchronization and archive, resume `complete-stage-one-exit-gate`.

The current disabled flag remains unchanged throughout the migration. No subagent is enabled merely to review the rule that subagents require manual opt-in.

## Risks / Trade-offs

- **[Developer writes a weak test]** -> Require a real behavioral red, active-requirement traceability, semantic Control review and prohibition of test-specific production behavior.
- **[DEFAULT loses useful safeguards]** -> Keep architecture, OpenSpec, testing strategy, independent preflight, full Maven/OpenSpec gates and Definition of Done common to both modes.
- **[MULTIAGENT rules drift after moving]** -> Move the existing protocol as one document, retain role/config convention coverage and load it only when the flag is true.
- **[Invalid configuration enables agents]** -> Fail closed; only exact boolean `true` selects MULTIAGENT.
- **[Two top-level sessions inspect different states]** -> Developer reports the exact working-tree evidence and Control reviews the final stable diff before completion.
- **[Active changes conflict during migration]** -> Archive the completed integrity change first, then validate and apply this delta before Stage 1 exit work resumes.

## Migration Plan

1. Run the required local gates for `enforce-test-execution-integrity`, synchronize its delta and archive it.
2. Validate this change against the resulting accepted `repository-conventions` baseline.
3. Add focused convention tests for DEFAULT selection, manual MULTIAGENT opt-in, conditional guidance loading and the minimal DEFAULT test contract; record targeted behavioral red.
4. Condense `AGENTS.md` and `docs/AGENT_WORKFLOW.md`, move the existing supervised protocol to `docs/AGENT_WORKFLOW_MULTIAGENT.md`, and update testing, README, configuration comments and role descriptions.
5. Run targeted green, the independent preflight, `mvnw.cmd clean verify`, strict OpenSpec validation, OpenSpec doctor and `git diff --check`; review the stable diff tests-first.
6. Synchronize and archive this change, then resume the two tasks in `complete-stage-one-exit-gate`.

Rollback restores the previous root/workflow documents and convention checks as one coherent set. There is no runtime or data rollback.
