## Context

See [proposal.md](proposal.md) for motivation. The repository is clean, all prior foundation changes are archived, `HEAD` equals `origin/master`, and the complete local Maven/OpenSpec gate passes. The remaining uncertainty is external: the latest GitHub Actions result and primary-branch required-check setting cannot be inferred from repository files. The Delivery Plan must not advance until those conditions are confirmed.

No application module owns this change. It has no runtime transaction, database migration, production dependency or module-boundary impact.

## Goals / Non-Goals

**Goals:**

- Close Stage 1 using evidence for the exact pushed commit rather than a nearby workflow run.
- Make the route inside every stage visible through short, human-readable work packages without creating a second implementation backlog.
- Keep the Delivery Plan truthful when control passes to Stage 2.
- Provide a small, separable workload suitable for exercising the configured multi-agent roles.

**Non-Goals:**

- Change the CI workflow, application, architecture or multi-agent configuration.
- Implement `build-first-signal-evaluation-skeleton`.
- Treat missing GitHub access as permission to bypass the remote gate.

## Decisions

### Remote evidence precedes the stage transition

The verification records the exact local `HEAD`, confirms it equals the primary remote branch, and matches that SHA to a successful remote `quality-gate` run. It then inspects the primary-branch ruleset or protection settings and confirms the stable `quality-gate` job is required.

If either external condition is false or inaccessible, the Delivery Plan remains unchanged and the apply handoff reports the exact blocker. A local build is supporting evidence but does not substitute for the accepted remote gate.

Alternative considered: mark Stage 1 done from local verification alone. Rejected because the accepted CI contract explicitly separates repository workflow configuration from GitHub enforcement.

### The work-package map can improve before the stage transition

The Delivery Plan may add or refine short work packages while Stage 1 remains `Current`. This improves navigation without claiming exit evidence that does not exist. Each package describes an outcome or likely OpenSpec-sized change in one or two lines and uses only the existing plan statuses. Detailed checkboxes, percentages and implementation decisions remain in OpenSpec.

The map is deliberately adaptable. New evidence may add, split, reorder or defer packages, provided the plan is updated explicitly and continues to point to authoritative sources rather than silently rewriting accepted behavior.

Alternative considered: create a separate backlog document. Rejected because it would duplicate the Delivery Plan and create competing current/next status.

### The stage transition happens only after the gate passes

After remote evidence is complete, update the current-position and stage-status statements needed to mark Stage 1 `Done`, Stage 2 `Current`, and `build-first-signal-evaluation-skeleton` as the current/next business work. Product detail remains in the Roadmap and future implementation detail remains in the next OpenSpec change.

Alternative considered: create the Stage 2 business change in the same apply. Rejected because this change is only an exit gate and stage handoff.

### Multi-agent execution remains orchestration, not repository architecture

Recommended handoff for testing the user's multi-agent setup:

1. Tester verifies the exact local checkpoint and required local gates.
2. Researcher or Tester inspects the remote workflow run and branch protection using available authenticated access.
3. Developer updates the Delivery Plan only after both gates pass.
4. Reviewer independently checks evidence, scope and final documentation.
5. Architect coordinates credentials or administrative approval and returns the result to the user.

These role labels are advisory. Apply must use only the roles that help and must not edit `.codex/` or `docs/AGENT_WORKFLOW.md`.

## Risks / Trade-offs

- [The repository is private or GitHub tooling is unavailable] → Report the inaccessible remote condition and keep Stage 1 current; do not fabricate evidence.
- [The workflow is green but not required] → Request separate authorization/administrative access to configure protection, then verify the resulting setting.
- [The primary branch advances during verification] → Re-read the remote SHA and verify the final exact checkpoint before updating the plan.
- [Parallel agents edit shared files] → Only Developer edits the Delivery Plan; Tester, Researcher and Reviewer remain read-only for this change.

## Migration Plan

1. Capture the exact local and remote primary-branch SHA and run the required local verification commands.
2. Verify the GitHub Actions conclusion and required-check setting for that checkpoint; stop with a blocker if either cannot be proved.
3. Add the concise adaptable work-package map while preserving truthful current stage statuses.
4. After remote evidence is confirmed, update the Delivery Plan stage transition without changing Roadmap product content or the next business scope.
5. Re-run Maven, strict OpenSpec validation, doctor and Git checks; independently review the result.
6. After approval, sync the two delta specs and archive this change. Commit and push remain separate user-authorized actions.

Rollback before a checkpoint is limited to reverting the Delivery Plan status edit. No runtime or data rollback exists.
