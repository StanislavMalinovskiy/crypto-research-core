## 1. Establish the migration baseline

- [x] 1.1 Verify `enforce-test-execution-integrity` with `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`; synchronize and archive it, then confirm its requirements are present in the main `repository-conventions` specification.
- [x] 1.2 Run strict validation for this change against the archived baseline and confirm `.codex/config.toml` still contains `[agents].enabled = false` so implementation proceeds in DEFAULT without project subagents.

## 2. Prove the minimal mode contract

- [x] 2.1 Add focused `RepositoryConventionsTest` coverage for fail-closed DEFAULT selection, exact-true MULTIAGENT opt-in, conditional loading of the separate MULTIAGENT guide and the minimal DEFAULT test safeguards; run `mvnw.cmd -Dtest=RepositoryConventionsTest test` before implementation and record the named test failing at the expected behavioral assertion.
- [x] 2.2 Implement the narrow convention inspection required by those cases and rerun `mvnw.cmd -Dtest=RepositoryConventionsTest test`; verify the accepted red cases pass without weakening the independent test-integrity or CI checks.

## 3. Separate DEFAULT from MULTIAGENT guidance

- [x] 3.1 Condense `AGENTS.md` and rewrite `docs/AGENT_WORKFLOW.md` for DEFAULT: two top-level sessions, Developer-owned tests and implementation, real red when behavior changes, full local gate, concise evidence handoff, Control review and one consolidated repair by default; verify no specialized subagent status, manifest, capsule, telemetry or routing step is required.
- [x] 3.2 Move the existing supervised role protocol to `docs/AGENT_WORKFLOW_MULTIAGENT.md`, update `.codex/agents/*.toml` descriptions to identify MULTIAGENT-only roles, and verify root guidance loads that document only when `[agents].enabled = true`.
- [x] 3.3 Update `docs/TESTING.md`, `README.md` and the comment around `.codex/config.toml` without changing `enabled = false`; verify DEFAULT/MULTIAGENT terminology, test anti-gaming rules and relative links with `mvnw.cmd -Dtest=RepositoryConventionsTest test`.

## 4. Verify and publish the workflow

- [x] 4.1 Run `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1` followed by `mvnw.cmd clean verify`; verify all Surefire and Failsafe tests, including PostgreSQL/Testcontainers suites, execute successfully without skip or selection flags.
- [x] 4.2 Run `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`; review the stable diff tests-first and confirm no production, dependency, module, schema, migration, CI-job or unrelated user change exists.
- [x] 4.3 Synchronize the `repository-conventions` delta, archive `adopt-economy-first-agent-workflow`, and confirm accepted documentation identifies DEFAULT as the fail-closed normal mode and MULTIAGENT as manual opt-in before resuming `complete-stage-one-exit-gate`.
