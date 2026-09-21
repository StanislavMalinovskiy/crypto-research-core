## 1. Establish persistence behavior with a valid red

- [x] 1.1 Add a focused `EvaluationReportPersistenceIT` using PostgreSQL 18.6 Testcontainers and the proxied evaluation application writer; construct valid immutable reports and assert sequential equality, changed same-run content, late outcome collision, late report collision, and exact durable row snapshots without mocking persistence.
- [x] 1.2 Add bounded two-thread, separate-transaction equal and conflicting persistence scenarios; assert one stable complete aggregate for equal callers and exactly one complete winner plus one explicit immutable-retry conflict for conflicting callers, then run `./mvnw.cmd -Dit.test=EvaluationReportPersistenceIT verify` before production edits and record a behavioral RED at the named assertion, test hash, and pre-implementation diff.

## 2. Make evaluation aggregate retry resolution atomic

- [x] 2.1 Modify only evaluation application/persistence implementation to retain insert-first PostgreSQL behavior while resolving every run, outcome, and report uniqueness key to the complete owned aggregate and comparing all immutable durable values after the existing normalizations; verify no fingerprint algorithm, public API, schema, migration, dependency, or cross-module access changes.
- [x] 2.2 Make any unequal aggregate, late outcome/report collision, or pre-existing incomplete aggregate fail explicitly through an unchecked immutable-retry conflict/integrity failure so the existing application transaction rolls back all rows inserted by the failed attempt; verify the focused test proves no partial rows remain and no stored variant is mutated.

## 3. Verify and hand off

- [x] 3.1 Run `./mvnw.cmd -Dit.test=EvaluationReportPersistenceIT verify` to green and verify the establishing test hash is unchanged after RED; also run `./mvnw.cmd -Dit.test=FirstSignalEvaluationIT verify` to preserve the first-slice evaluation behavior.
- [x] 3.2 Record the focused RED/GREEN evidence, changed paths, separate-transaction concurrency evidence, exact durable row-count/snapshot assertions, and invariant evidence in the Builder handoff; keep the active change artifacts and current-state documentation truthful until approval.
- [x] 3.3 Run `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, and `git diff --check`; report any unavailable Docker or other blocker with the exact command and cause before marking tasks complete.

## Completion evidence

- Builder supplied valid original behavioral RED/GREEN evidence for the production change. The user-authorized final test-harness repair was `RED_NOT_REQUIRED` and did not change production behavior.
- Targeted green: `./mvnw.cmd -Dit.test=EvaluationReportPersistenceIT verify` — 8/8 passed.
- Regression green: `./mvnw.cmd -Dit.test=FirstSignalEvaluationIT verify` — 3/3 passed earlier in the approved run.
- Stable-diff check: `git diff --check` passed.
- Fresh CORE_RISK review approved CI-01, CI-03, CI-04, CI-05, CI-06, CI-07, CI-10, CI-11, CI-14, and CI-15.
- Main independently ran `mvnw.cmd clean verify` with escalated Windows host access after confirming that the
  earlier named-pipe failure was restricted-sandbox denial rather than Docker downtime; the complete gate passed.
