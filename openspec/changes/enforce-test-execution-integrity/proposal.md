## Why

The independent-test workflow can still be bypassed without editing an assertion: tests may be disabled or excluded, Maven or CI may narrow discovery, and a Developer has no explicit arbitration path when a test appears wrong. Add one small automated convention gate and finish the role-routing contract before relying on the workflow for business changes.

## What Changes

- Make Maven verification reject explicit disabled/ignored Java tests and unconditional false assumptions in test sources.
- Make repository verification reject default Maven or quality-gate settings that skip, exclude, select or retag only part of the required test suite.
- Complete the agent workflow so Developer returns `TEST_SUSPECT`, Reviewer alone decides `TEST_WRONG`, `CODE_WRONG` or `SPEC_AMBIGUOUS`, and Architect owns the single three-round repair limit.
- Keep the guard deliberately mechanical: it checks known execution bypasses, not semantic adequacy of every test configuration.

Non-goals:

- No production code, dependency, module, schema, migration or runtime behavior change.
- No attribution of a file edit to a particular agent, mandatory worktrees, mutation testing or holdout suite.
- No blanket ban on legitimate production build configuration that an approved future change may require.
- No modification of `complete-stage-one-exit-gate`; that change remains independently scoped.

Affected application modules: none.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: make common test-disabling and test-selection bypasses fail the existing Maven/CI verification lifecycle.

## Impact

Test-side repository convention checks, project-scoped Codex role configuration, and the concise agent workflow documentation. The existing GitHub `quality-gate` already runs `./mvnw clean verify`, so no new CI service, workflow job or production dependency is introduced.
