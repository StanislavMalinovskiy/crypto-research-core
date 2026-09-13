## Context

The repository already runs `RepositoryConventionsTest` in Maven and the GitHub quality gate already executes `./mvnw clean verify`. The role files now separate API skeleton, independent test creation, implementation, review and full verification, but one Tester verdict and the known mechanical test-bypass paths remain inconsistent. See [proposal.md](proposal.md) for motivation and [repository-conventions](specs/repository-conventions/spec.md) for the required behavior.

This is repository tooling only. It owns no application module, database transaction or runtime resource.

## Goals / Non-Goals

**Goals:**

- Fail the existing Maven lifecycle for a small, explicit set of committed test-bypass mechanisms.
- Keep Developer, Tester, Reviewer and Architect verdict ownership unambiguous.
- Reuse the existing test and CI infrastructure without another dependency or workflow job.

**Non-Goals:**

- Prove that every test assertion or test configuration is semantically strong.
- Attribute a change to a particular human or agent.
- Introduce worktree enforcement, mutation testing, holdout tests or runtime observability.

## Decisions

### Extend the existing repository convention test

Add the checks to the test-side repository convention suite so they run locally and in the existing CI lifecycle. This is smaller and more portable than a new script, Maven plugin or GitHub-only action. No production package or dependency changes.

The scanner will inspect repository files, while focused helper-level assertions will prove representative allowed and forbidden inputs. The scanner's own source must not match its forbidden tokens accidentally; tokens can be composed in the test implementation and the checks must operate on parsed or narrowly scoped content rather than unrestricted substring matching.

### Check Java test bypasses mechanically

Inspect Java test sources for explicit JUnit disabling/ignoring annotations and unconditional literal false assumptions. Do not try to infer whether arbitrary application conditions, tags or configuration values make a test ineffective; Reviewer remains responsible for semantic weakening.

This narrow allowlist of known bypasses avoids a fragile general-purpose Java parser while covering the concrete escape routes in scope.

### Inspect Maven configuration structurally

Parse `pom.xml` as XML and inspect Surefire/Failsafe configuration, profiles and test-related properties for committed skip or selection settings. Scope `includes`, `excludes`, groups, tags and selectors to test-execution plugins so legitimate Maven Enforcer dependency exclusions remain valid.

A raw text search was rejected because the current Enforcer configuration legitimately contains `<excludes>` and would create false positives.

### Keep the quality-gate command exact

Retain the current assertion that the workflow contains the exact `./mvnw clean verify` command and additionally reject test-skip or test-selection flags and step-level environment settings around Maven verification. Targeted local Developer commands are not committed CI configuration and remain allowed.

### Reviewer owns ambiguity; Architect owns the counter

Tester may report `TEST_SUSPECT`, but not a final `SPEC_AMBIGUOUS`. Reviewer alone classifies a code/test/spec conflict. Architect owns one task-level repair counter capped at three routings; individual roles do not maintain competing retry budgets. Infrastructure retries that do not produce a code/test/spec verdict do not consume a repair round.

Developer owns no documentation. Architect remains responsible for required OpenSpec, ADR and status consistency before completion; optional prose can be produced outside the implementation loop.

## Risks / Trade-offs

- **[False positives from broad matching]** → Parse Maven XML structurally and keep Java patterns explicit and narrow.
- **[A novel or semantic bypass remains possible]** → State the mechanical boundary in the spec and preserve independent Reviewer inspection.
- **[The guard blocks a future legitimate selective CI design]** → Require that future behavior to be approved through its own OpenSpec change before changing the convention.
- **[The workflow text and role TOML drift]** → Cover verdict ownership in repository guidance and verify the concrete role output lists during implementation.

## Migration Plan

1. Correct the remaining Tester verdict output and confirm the role documents agree.
2. Add focused repository-convention checks and their representative cases.
3. Run the targeted convention test, then the complete Maven and OpenSpec gates.

Rollback is removal of the added test-side checks and role-guidance delta; there is no production or data migration.
