# DEFAULT agent workflow

DEFAULT is the normal, fail-closed workflow. It uses two user-controlled top-level sessions and no project subagents:

- **Control** owns the active OpenSpec contract, planning decisions, final documentation, test-first review of the stable diff, completion gates and archive.
- **Developer** owns specification-derived tests and implementation in one bounded pass, including targeted checks and the complete local gate.

The mode is selected at task start from `.codex/config.toml` as described in [root guidance](../AGENTS.md). It remains fixed for the task unless the user explicitly restarts or continues the task after changing the setting.

## Test impact

Changed observable behavior requires a meaningful test derived from the active requirement and a targeted behavioral red before implementation. The named test must execute and fail at the expected assertion. Compilation, discovery, configuration, startup or infrastructure failure is not red.

After valid red, Developer must not weaken, disable, skip or narrow the establishing test, alter its expected result to fit the implementation, or add production behavior that exists only for a test artifact. SQL, migrations, persistence, locking and idempotency use real PostgreSQL through Testcontainers as required by [Testing strategy](TESTING.md).

Documentation, comments, formatting, mechanical configuration, a pure rename or an internally covered refactoring may record that no new behavioral test is needed. Existing applicable checks and the complete gate still run.

## Development pass

1. Control confirms the active change, accepted sources and testable contract.
2. Developer reads the required project sources, assesses test impact and adds all needed test evidence before implementation.
3. When behavior changes, Developer runs the targeted test and records behavioral red evidence.
4. Developer implements the smallest coherent change without changing the accepted red expectation.
5. Developer runs relevant targeted green checks and the complete local gate.
6. Developer returns a concise evidence handoff naming changed files, red and green evidence when applicable, verification results and remaining risks.
7. Control reviews test meaning before implementation details, reviews the stable diff and independently runs the required completion gates before updating final documentation and archiving the change.

## Complete local gate

Run from the repository root without test skip or selection flags:

```powershell
pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1
mvnw.cmd clean verify
openspec validate --all --strict --no-interactive
openspec doctor
git diff --check
```

The independent preflight must pass before `clean verify`. On Windows under Codex, run `docker version` and
every Docker/Testcontainers Maven command with escalated host access, outside the restricted sandbox. A sandbox
`permission denied`, `docker_engine is not listening`, or discovery timeout requires one escalated retry and is
not yet an unavailable-Docker blocker or an artifact repair. Report Docker unavailable only when escalated
`docker version` in the same execution context fails. On Unix, `./mvnw clean verify` is equivalent. Report a
confirmed environmental blocker exactly; do not hide it by narrowing the lifecycle.

## Review and repair

Control checks requirement alignment, point-in-time integrity, idempotency, measurement bias and test meaning before style. A failed mechanical gate cannot be overridden by editorial approval.

When Control finds repairable defects after the first pass, it sends one consolidated repair to Developer by default. A further repair requires the user's explicit decision. This bound does not turn an infrastructure retry into an artifact repair, and it never permits weakening required behavior or verification.

For the manually enabled supervised protocol, use [MULTIAGENT workflow](AGENT_WORKFLOW_MULTIAGENT.md) only when root guidance selects that mode.
