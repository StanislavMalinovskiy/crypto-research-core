## 1. Establish specification-derived behavioral RED

- [x] 1.1 Add focused unit coverage for both inclusive windows and one-microsecond exclusions, missing/stale endpoints, deterministic ordered selection, both growth thresholds, request normalization, invalid target/bounds and unsupported versions before collaborator calls; verify the cases map to the delta and stay within the design's test-file budget.
- [x] 1.2 Add PostgreSQL coverage in `LiquiditySpikeObservationWindowsIT` for durable zero-effect rejection, exact selected evidence, Unicode timestamp ties, immutable v2 retry and historical v1 lookup; migrate existing `FirstSignalEvaluationIT` detection calls to v2 before RED while preserving financial/risk/report assertions and the unchanged raw JSON fixture; verify the test diff contains no weakened existing expectations.
- [x] 1.3 After preparing all planned unit and PostgreSQL tests, run `./mvnw.cmd -Dtest=LiquiditySpikeSignalServiceTest test` against unchanged production code and record a named expected unit behavioral assertion failure, exact command, expected/actual values, hashes of all establishing test files and the production-free diff. Verify infrastructure or compilation failure is not substituted for RED, then freeze those tests. Integration RED is not required; PostgreSQL execution belongs to GREEN in task 3.1, because an expected unit failure prevents Maven `verify` from reaching Failsafe.

## 2. Implement the fixed observation-window policy

- [x] 2.1 Enforce exact `liquidity-spike-v2`, normalized one-hour target and representable derived bounds before queries or effects, preserving request shape and existing versioned identity encoding; verify the targeted unit tests reject old/future/blank versions and invalid windows with no collaborator calls.
- [x] 2.2 Query baseline `[C - 3900s, C - 3600s]` and current `[C - 60s, C]` through the existing asset/dataset/cutoff API, select the last item in its declared total order and require distinct endpoints; verify stale/missing evidence creates no signal rows while thresholds, risk gating, scoring and historical lookup retain their declared behavior.

## 3. Verify the bounded implementation and review evidence

- [x] 3.1 Run `./mvnw.cmd -Dtest=LiquiditySpikeSignalServiceTest test` and, after same-context `docker version`, `./mvnw.cmd '-Dit.test=LiquiditySpikeObservationWindowsIT,FirstSignalEvaluationIT' verify` to GREEN; verify establishing test hashes and raw fixture bytes are unchanged after RED and report exact results.
- [x] 3.2 Deliver a stable scoped diff, RED/GREEN evidence, changed-path budget and an invariant evidence table for fresh review; verify no public API shape, persistence algorithm, migration, dependency or unrelated module changed, and obtain the current reviewer's consolidated verdict without self-approval.

## 4. Documentation and ordinary completion gates

- [x] 4.1 In the assigned documentation phase, update `docs/modules/signal.md` with the v2 windows, compatibility transition and endpoint-only limitations; verify relative links and scope statements do not mark F1, F3 or all production F6.2 complete, then obtain applicable review before non-semantic DOCS_CLOSE updates.
- [x] 4.2 Main runs `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, same-host-context `docker version` followed by `./mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, and `git diff --check`; verify every required check passes or record its exact blocker without claiming completion.
- [x] 4.3 Only after approval, DOCS_CLOSE and full-gate PASS, record Main's scoped pre-archive checkpoint and authorize `openspec archive bound-liquidity-spike-observation-windows --yes`; verify Main's exact mutation inspection and post-archive `openspec validate --all --strict --no-interactive`, `openspec doctor`, `./mvnw.cmd -Dtest=RepositoryConventionsTest test`, and `git diff --check` all pass before DONE, following scoped recovery on failure.

## Completion evidence

All 10 tasks are verified: see [verification.md](verification.md). Full gate, CLI archive, exact mutation inspection and post-archive checks passed.
