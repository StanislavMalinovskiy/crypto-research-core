## Why

Stage 1 is locally complete and has a pushed reproducible checkpoint, but the Delivery Plan still reports it as current and the remote GitHub quality-gate/branch-protection state has not been independently confirmed. The plan also shows stages without a concise work-package route, making it difficult to see what is done, current, next and still expected inside each stage. Add that navigation while keeping the Stage 1 exit truthful.

## What Changes

- Verify that the latest primary-branch checkpoint has a successful remote `quality-gate` run and that `quality-gate` is configured as a required status check; report an explicit external blocker instead of claiming success when access or platform support is unavailable.
- Expand the Delivery Plan with short status-bearing work packages for every stage, links to authoritative detail where it exists, and an explicit statement that the map evolves when evidence reveals new necessary work.
- After the remote gate is confirmed, update the Delivery Plan so Stage 1 is `Done`, Stage 2 is `Current`, and `build-first-signal-evaluation-skeleton` is the current/next business work.
- Re-run the existing local Maven and OpenSpec gates and independently review the final repository state.

Non-goals:

- No business functionality, provider integration, worker, migration, production dependency, module or dependency-DAG change.
- No redesign of CI, deployment, observability, persistence or the six-module architecture.
- No detailed implementation backlog, percentage tracking or duplication of OpenSpec task checklists in the Delivery Plan.
- No modification of the multi-agent configuration currently being developed under `.codex/` or `docs/AGENT_WORKFLOW.md`.
- No commit, push or GitHub administrative mutation without the user's separately available authorization and credentials.

Affected application modules: none.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ci-quality-gate`: require evidence that the Stage 1 checkpoint passed the remote gate and that the stable job is enforced on the primary branch, or record the exact external blocker.
- `repository-conventions`: make the Delivery Plan a concise, adaptable work-package map and define the transition from a completed stage to the next current stage after exit evidence is satisfied.

## Impact

Documentation and repository governance only: `docs/DELIVERY_PLAN.md`, remote GitHub Actions evidence, and the primary-branch required-check setting. The work-package map may be improved without claiming the current stage is complete; the Stage 1/2 status transition remains gated by remote evidence. Production code, Maven configuration, database schema, module contracts and the in-progress agent-workflow configuration remain unchanged.
