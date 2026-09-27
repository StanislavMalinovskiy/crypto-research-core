## Why

The lean MULTIAGENT workflow needs three small safeguards against overlapping specification changes, freezing tests for an unrelated failure, and losing repair continuity when a session disappears.

## What Changes

- Require Architect to check active changes touching the same specs before PLAN_READY.
- Require Builder to verify and record each RED reason before freezing tests; preserve the existing post-freeze TEST_SPEC_ERROR procedure.
- Allow Main to replace an unavailable Builder/Reviewer session without changing its role, model, effort or repair accounting.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: three added MULTIAGENT safeguard requirements, one scenario each.

## Impact

Only workflow/testing documentation, Architect and Builder role instructions, and mechanical checks in RepositoryConventionsTest change. No application module, API, transaction boundary, dependency, infrastructure, script or new tooling is affected. DEFAULT semantics, models, routing, repair limits, other active changes and historical records are non-goals.

For execution of this change only, the owner explicitly requested one agent with no subagents; self-review replaces independent review and is not represented as fresh Reviewer approval. This exception does not change the permanent workflow.
