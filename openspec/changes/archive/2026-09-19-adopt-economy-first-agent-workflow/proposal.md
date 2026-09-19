## Why

The unconditional supervised workflow spends too much context and too many hand-offs on ordinary development. Make a lightweight two-session workflow the default while keeping the existing specialized subagent protocol available only when the user deliberately enables it.

## What Changes

- Define `DEFAULT` whenever `.codex/config.toml` has `[agents].enabled = false`, or when that setting is missing or invalid.
- In `DEFAULT`, use two user-controlled top-level sessions: Control owns planning, documentation and final review; Developer owns meaningful tests and implementation in one pass.
- Keep only essential DEFAULT safeguards: a real behavioral red for changed behavior, no weakening or bypassing tests, no test-specific production behavior, targeted green, the complete local gate and a concise evidence handoff.
- Remove specialized role statuses, skeleton/tester phases, phase manifests, task capsules, threat checks, assignment telemetry and automatic multi-round routing from DEFAULT.
- Define `MULTIAGENT` only when the user manually sets `[agents].enabled = true`; retain the existing supervised Developer, Tester, Reviewer and Researcher protocol in a separate document loaded only for that mode.
- Use one consolidated Developer repair by default in DEFAULT; any further repair iteration requires the user's explicit decision.
- Keep application architecture rules, OpenSpec, repository test-integrity checks and Definition of Done unchanged in both modes.

Non-goals:

- No production behavior, dependency, API, database schema, migration, module boundary or dependency-DAG change.
- No removal of MULTIAGENT role configurations, phase tools, logs or telemetry used by that opt-in mode.
- No automatic edit of `[agents].enabled` and no automatic switch to MULTIAGENT based on task risk.
- No commit, push, GitHub administration or Stage 2 business implementation.

Affected application modules: none.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: define DEFAULT and MULTIAGENT selection, a minimal test-first DEFAULT contract, and conditional loading of the detailed MULTIAGENT protocol.

## Impact

Repository governance and development tooling only: `AGENTS.md`, `docs/AGENT_WORKFLOW.md`, a new `docs/AGENT_WORKFLOW_MULTIAGENT.md`, `docs/TESTING.md`, `README.md`, `.codex/config.toml`, descriptions under `.codex/agents/`, and repository-convention tests. Existing integrity scripts and the CI quality gate remain. Application modules, production sources, Maven dependencies and PostgreSQL are unchanged.
