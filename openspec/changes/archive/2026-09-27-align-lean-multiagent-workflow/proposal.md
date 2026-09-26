## Why

Current GPT-6 guidance splits risk classification between Main and Architect, sends every review to a fresh agent, and ends before a recoverable archive closure. The owner requests one lean, closed workflow with Architect-owned risk, deterministic routing, author-owned repair and verified archive recovery.

## What Changes

- Make Architect the sole risk classifier; freeze risk and matched trigger evidence at `PLAN_READY` and reopen planning for new risk or scope.
- Route ROUTINE to Luna xhigh, STANDARD to Luna max and CORE_RISK to Sol medium using explicit role files.
- Route ordinary implementation and non-normative documentation review to the same Architect; require fresh Reviewer for every CORE_RISK change and accepted normative spec change.
- Close author-specific repair, bounded technical escalation, independent full verification, scoped checkpoint, Architect archive and post-archive verification into one flow.
- Align executable conventions and the existing assignment logger's routing allowlists without redesigning telemetry.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: lean responsibility, risk, routing, review, repair, escalation and archive lifecycle contracts, with executable checks of active guidance.

## Impact

Affected areas are agent guidance, role configurations, repository convention tests and minimal assignment-logger role/phase/status validation. No business module, production dependency, database schema, transaction boundary or application API changes.

Non-goals: changing DEFAULT semantics or the mode switch; product code, migrations or domain tests; telemetry redesign or repository log writes; hooks, IDE/MCP configuration; historical notes, archives or benchmarks; modifying or archiving any other active change; direct main-spec editing before the authorized archive.
