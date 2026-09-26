## 1. Establish behavioral evidence

- [x] 1.1 Builder adds scenario-derived repository-convention tests for T1–T6 and executes `.\mvnw.cmd -Dtest=RepositoryConventionsTest test`; record the expected behavioral failures against unchanged docs/roles, establishing test hash and pre-implementation diff outside the repository.
- [x] 1.2 Builder updates the existing logger routing tests for T7 and executes `pwsh -NoProfile -File .codex/scripts/log-agent-activity.tests.ps1`; record expected routing rejection before logger edits and freeze the tests. Notify Main and Architect that behavioral RED is established before any docs/role implementation.

## 2. Align implementation and guidance

- [x] 2.1 Builder aligns the six role configurations, renames the xhigh Luna role and minimally adjusts logger role/phase/status allowlists; verify T1/T3/T4/T5/T7 with the two targeted commands and preserve the mode switch, event schema and real log directory.
- [x] 2.2 After RED, Architect updates MULTIAGENT workflow, invariant cross-references and testing guidance with the single trigger authority, exact review rule, stable risk, author-owned repair, escalation verdicts and full archive closure; verify T2–T6 and inspect the owner's complete Mermaid diagram against every required return edge.
- [x] 2.3 Builder runs `.\mvnw.cmd -Dtest=RepositoryConventionsTest test` and `pwsh -NoProfile -File .codex/scripts/log-agent-activity.tests.ps1` to GREEN, checks frozen test hashes, and returns BUILD_DONE with file scope and compact evidence.

## 3. Review and approval closure

- [x] 3.1 Fresh Reviewer reviews the stable diff, active contract, T1–T7 evidence, change budget and applicable CI-01/CI-15 invariants; record one verdict and explicit reasons for unaffected invariants, with bounded author-specific repair if needed.
- [x] 3.2 After APPROVE, Architect updates only completion status, task checkboxes and non-semantic evidence; verify no contract changes and no archive yet.

## 4. Full gate and safe archive

- [x] 4.1 Main runs `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, same-context `docker version`, `.\mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`; record each exit and route any failure by ownership without narrowing checks.
- [x] 4.2 Main records the pre-archive porcelain status, exact allowlist, raw-byte hashes/blob IDs, temporary-index Git checkpoint and unchanged real-index identity; verify unrelated owner changes are excluded and exact restoration is provable.
- [x] 4.3 Architect runs `openspec archive align-lean-multiagent-workflow --yes` only after gate PASS and supplied checkpoint; Main verifies only the authorized active-change, archive-copy and repository-conventions spec paths changed.
- [x] 4.4 Main runs `openspec validate --all --strict --no-interactive`, `openspec doctor`, `.\mvnw.cmd -Dtest=RepositoryConventionsTest test` and `git diff --check` after archive; on failure restore only recorded archive mutations and reenter the bounded correction/full-gate/archive cycle, otherwise report DONE.
