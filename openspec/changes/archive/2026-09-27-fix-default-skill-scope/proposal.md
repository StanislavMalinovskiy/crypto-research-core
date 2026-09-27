## Why

Two scenarios under `Local skill routing and project authority` accidentally apply MULTIAGENT closure and planning terminology to DEFAULT. Align their scope with the existing mode-specific workflows and local skills.

## What Changes

- Clarify common archive CLI, approval and verification obligations versus MULTIAGENT-only closure/checkpoint obligations.
- Scope Main and PLAN_READY to MULTIAGENT; preserve DEFAULT Control ownership and the planning-only stop in both modes.
- Remove only the extra EOF blank line from the archived instruction-diet delta; preserve its historical semantics.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: correct the two mode-scope scenarios in the existing local skill requirement.

## Impact

Documentation only; no application module, API, transaction, dependency or infrastructure changes. Non-goals: new tests/frameworks, skills/configuration/governance edits, broader audits and historical semantic rewrites. Accepted spec synchronization occurs only through the authorized CLI archive of this change.
