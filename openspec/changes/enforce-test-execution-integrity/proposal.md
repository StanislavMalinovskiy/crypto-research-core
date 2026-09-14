## Why

The independent-test workflow still lets the same role author and certify its own gate, permits test artifacts to change after red, and relies on prose-only routing that can drift between role files. Common committed test-disabling mechanisms also remain able to narrow Maven verification without failing the repository convention gate.

## What Changes

- Make Architect mechanically execute the expected red command and the complete Maven green gate instead of accepting Tester self-attestation.
- Freeze the repository state at phase boundaries with a path-and-content manifest that detects additions, changes and deletions outside the writer's explicit allowlist.
- Require every new behavioral test to identify its OpenSpec requirement and scenario and the expected red assertion.
- Split Reviewer into parent-selected `ADJUDICATE` and `AUDIT` modes with closed status sets and binding verdicts that Architect cannot replace.
- Close the status vocabulary for all roles, route `SPEC_INCOMPLETE`, retain one logged three-round repair budget and prohibit `ultra` for deterministic project-agent runs.
- Make Reviewer mechanically read-only and use high reasoning for Tester because this role is reserved for behavioral, persistence, financial and point-in-time work.
- Make Maven verification reject explicit disabled/ignored Java tests, unconditional false assumptions, and committed Maven or CI settings that skip, select, exclude or retag required tests.
- Run that integrity inspection as an independent pre-Maven gate so the Maven configuration under inspection cannot suppress the guard itself.

Non-goals:

- No production code, dependency, module, schema, migration or runtime behavior change.
- No OS-level attribution of edits to a particular process, mandatory worktrees, mutation testing or holdout suite.
- No claim that a successful red gate alone proves semantic alignment with the specification; final Reviewer audit remains responsible for that judgment.
- No blanket ban on legitimate production build configuration introduced by a separately approved change.
- No modification of `complete-stage-one-exit-gate`; that change remains independently scoped.

Affected application modules: none.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: make agent phase ownership, verdict routing and common test-execution bypasses independently verifiable.

## Impact

Root agent guidance, project-scoped Codex role configuration, agent workflow documentation, small phase-manifest, repair-routing log and test-integrity preflight tools, test-side repository convention checks, and one preflight step in the existing GitHub `quality-gate`. The workflow still runs `./mvnw clean verify`; no new CI service, workflow job or production dependency is introduced.
