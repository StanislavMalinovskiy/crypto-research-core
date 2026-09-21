## Why

The V4 evaluation persistence path resolves duplicate run, outcome, and report rows independently.  It therefore does not prove that a retry resolves to one complete immutable evaluation aggregate, and a late outcome/report uniqueness collision can otherwise commit a new partial run.  This must be corrected before later evaluation work can rely on report evidence.

## What Changes

- Strengthen immutable retry semantics for the persisted `EvaluationReport` aggregate: an equal retry returns the single stable durable result, while any difference in its persisted immutable run, outcome, or report content fails explicitly without mutation.
- Make collision resolution atomic at the evaluation application boundary so an outcome or report conflict cannot leave a newly inserted run or outcome behind.
- Add real PostgreSQL/Testcontainers coverage with independently transacted concurrent callers for equal and conflicting report persistence.

Non-goals:

- No schema, Flyway migration, table/index/constraint, public `EvaluationApi`, fingerprint-canonicalization algorithm, module-boundary, dependency, provider, scheduling, or configuration change.
- No change to valuation, point-in-time selection, report rendering, numeric/time normalization, or the scope of the first signal-evaluation slice.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `signal-evaluation`: strengthen the existing immutable evaluation-report requirement with aggregate retry, conflict, rollback, and concurrency behavior.

## Impact

Only the `evaluation` module's application-owned persistence semantics and its PostgreSQL integration tests are affected.  The implementation may change `EvaluationWriter`, `JdbcEvaluationPersistence`, and the existing evaluation integration test or a focused sibling integration test.  It uses the existing Spring transaction, JdbcClient, PostgreSQL 18.6, Flyway V4 schema, and Testcontainers baseline; no API or dependency changes are allowed.
