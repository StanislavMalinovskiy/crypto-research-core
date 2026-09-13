## Why

The repository has detailed product, architecture and OpenSpec documentation, but no short operational map showing the current stage, the next major outcome and the route to the Evidence Report. This makes handoff between human and AI sessions harder and encourages operational sequencing to be duplicated inside the long-term Roadmap.

## What Changes

- Add a concise `docs/DELIVERY_PLAN.md` with seven outcome-oriented stages, explicit `Done`/`Current`/`Next`/`Planned`/`Deferred` statuses, and a clear current position.
- Keep the plan at the level of goals, outcomes, major change groups and exit signals; exclude Java types, database details, libraries and algorithms.
- Add durable navigation from `README.md` and assign ownership of delivery sequencing to the new document in `AGENTS.md`.
- Replace the Roadmap's duplicate operational sequence with a link to the Delivery Plan while preserving product hypotheses and long-term direction.
- Define a lightweight maintenance rule: update current and next work only when a change is archived or priority explicitly changes; do not add a line-count test or new CI gate.

Non-goals:

- No production code, dependencies, Maven configuration, CI workflow or database changes.
- No module-boundary, DAG, API, schema or main architecture changes.
- No business functionality, provider selection or implementation detail.
- No rewrite of the product Roadmap or historical OpenSpec artifacts.

Affected application modules: none.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: require a concise top-level delivery map with explicit source boundaries, current/next work, stage outcomes and maintenance semantics.

## Impact

Documentation only: `docs/DELIVERY_PLAN.md`, `README.md`, `AGENTS.md` and the operational-sequence portion of `docs/ROADMAP.md`. The repository-conventions delta records the durable documentation contract; no automated line-count or content-shape test is introduced.
