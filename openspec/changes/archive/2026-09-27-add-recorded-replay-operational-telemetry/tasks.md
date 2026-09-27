## 1. Behavioral tests

- [x] 1.1 Add focused unit tests derived from R1–R10, including fixed counter dimensions, summary fields, completed/aborted prefix accounting, empty/null input, repeated attempts and sink-failure isolation; verify a baseline-executable new telemetry assertion fails under `mvnw.cmd -Dtest=*Replay*Telemetry*Test test`, and record exact test names, assertion, unchanged production diff and test SHA-256 hashes before implementation. Compilation or setup failure is not RED.

## 2. Bounded implementation

- [x] 2.1 Implement counters and structured summaries only within the five-file Java budget in design; verify frozen tests pass with `mvnw.cmd -Dtest=*Replay*Telemetry*Test test`, original replay results/exceptions/call order remain intact, and metric/log runtime failures neither retry nor mask business work.
- [x] 2.2 Produce the implementation handoff with exact changed paths, RED/GREEN commands and results, frozen-test hash comparison and known limitations; verify `git diff --check`, unchanged existing tests/fixtures, no prohibited path changes, and actual registry/log assertions rather than source-text assertions.

## 3. Independent review and production completion

- [x] 3.1 Obtain fresh review of the normative delta, stable implementation diff, test evidence, all applicable invariants and change budget; verify one consolidated APPROVE or complete authorized bounded repairs with review. Existing task-wide repair limits remain in force.
- [x] 3.2 After approval, close this change's task/evidence documentation without semantic changes; verify every completed checkbox has evidence and no broader F3 completion is claimed.
- [x] 3.3 Main runs `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, same-host `docker version`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, and `git diff --check`; verify all required checks pass or record the exact blocker without claiming completion.
- [x] 3.4 Confirm archive readiness after the complete gate: implementation and review evidence are complete, all preceding tasks are verified, and Main's scoped pre-archive checkpoint is recorded. Mark this task complete only when those pre-archive conditions are satisfied; it does not assert that archive or post-checks have run.

After all readiness tasks are verified, archive only this change with `openspec archive add-recorded-replay-operational-telemetry --yes`. Main then verifies scoped mutations, `openspec validate --all --strict --no-interactive`, `openspec doctor`, `mvnw.cmd -Dtest=RepositoryConventionsTest test`, and `git diff --check` under the normal recovery protocol. Record archive and post-check outcomes in the closure handoff only after execution; do not treat readiness checkboxes as evidence of those later actions or claim completion before they pass.
