## Context

See [proposal.md](proposal.md) for motivation. The Roadmap is comprehensive but mixes long-term product direction with an operational sequence that is difficult to scan. README and AGENTS already route readers to authoritative sources, but neither provides a durable stage-level view. All prior foundation changes, including idempotent market-data storage, are archived; Stage 1 still needs this delivery map and an explicitly authorized reproducible Git checkpoint before transition to the first business slice.

No application module owns this change. It has no transaction boundary, runtime behavior, dependency or infrastructure impact.

## Goals / Non-Goals

**Goals:**

- Make the current position and route to the research decision understandable in one short document.
- Preserve one source of truth for each level of planning.
- Keep maintenance cheap enough that Current and Next remain trustworthy.

**Non-Goals:**

- Specify implementation details or replace the Roadmap, ADRs, main specs or OpenSpec changes.
- Turn stage exit results into additional CI blockers.
- Enforce document length or structure mechanically.

## Decisions

### Seven outcome-oriented stages

`docs/DELIVERY_PLAN.md` will use seven stages:

1. Architecture foundation.
2. First vertical slice.
3. Real Solana data.
4. Intelligence.
5. Signal evaluation.
6. Research decision.
7. Execution.

This is small enough to scan while preserving the important difference between proving the data path, adding real data, adding intelligence, evaluating signals and deciding whether execution is justified. Execution is `Deferred`, not an implied continuation.

Alternative considered: mirror every Roadmap phase. Rejected because it reproduces the navigation problem and couples the plan to speculative details.

### Outcome-level content only

Each stage may include Status, Goal, Outcome, Major changes, Exit signal, Next and Deferred, but empty fields are omitted. Major changes are two to four meaningful workstreams or representative change groups, not a limit on how many small OpenSpec changes may be used. The target size is roughly 90–120 lines; exceeding about 150 lines triggers editorial review, not an automated failure.

Alternative considered: a uniform mandatory template. Rejected because empty boilerplate makes a short plan less useful.

### Explicit source boundaries

- Delivery Plan owns stage sequence, current/next position and stage outcomes.
- Roadmap owns product hypotheses, long-term capabilities and directional priorities.
- OpenSpec changes own scoped requirements and task progress.
- ADRs own durable architectural choices.
- Main specs own accepted observable contracts.
- Code and tests provide implementation evidence.

The Roadmap keeps its product content but replaces its duplicate operational sequence with a link to the Delivery Plan. README links the new document, and AGENTS assigns its responsibility in one line.

### Low-churn current position

Current and Next are updated when a change is archived, a stage changes or priority is explicitly redirected. Task-level percentages are never copied into the plan. The initial position keeps Stage 1 `Current` until the delivery-plan change is archived and the separately authorized foundation Git checkpoint exists; `build-first-signal-evaluation-skeleton` is identified as the next business change.

## Risks / Trade-offs

- [The plan can become stale] → Review Current and Next at archive and explicit reprioritization boundaries.
- [The plan can duplicate the Roadmap] → Keep product detail in the Roadmap and use links from the Delivery Plan.
- [A stage can encourage oversized changes] → Treat listed major changes as workstreams; every implementation remains independently scoped through OpenSpec.
- [The line target can become bureaucracy] → Keep it editorial and add no test or CI gate.

## Migration Plan

1. Create the delivery plan from accepted repository state and the post-storage handoff.
2. Add durable links and source responsibility, then remove only duplicated operational sequencing from the Roadmap.
3. Validate Markdown links and all OpenSpec artifacts; no Maven run is required by behavior, but the existing required repository gate remains authoritative before archive.
4. On archive or stage transition, review Current and Next and record the separately authorized Git checkpoint without staging, commit or push during apply.

Rollback is deletion of the new document and restoration of the small navigation edits; no runtime or data migration exists.
