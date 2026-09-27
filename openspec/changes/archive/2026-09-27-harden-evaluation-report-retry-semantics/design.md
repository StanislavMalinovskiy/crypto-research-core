## Context

See [proposal.md](proposal.md) for motivation and the delta [signal-evaluation specification](specs/signal-evaluation/spec.md) for required behavior.  `evaluation` owns the V4 run, outcome, and report tables.  `EvaluationWriter` already supplies the short `@Transactional` application boundary, but `JdbcEvaluationPersistence` currently resolves each `ON CONFLICT DO NOTHING` independently and compares only the corresponding fingerprint value.  In particular, the run stores its own ID as its evidence fingerprint, and a report primary-key collision can be treated as equal without proving its `run_id` and immutable content match.  That can acknowledge a conflict while committing earlier rows in the same attempted aggregate.

The existing schema already has the aggregate keys needed for resolution: `evaluation_runs.run_id`, `entry_outcomes.outcome_id` plus `(run_id, signal_id, horizon)`, and `evaluation_reports.report_id` plus unique `run_id`.  This change does not alter V4.

## Goals / Non-Goals

**Goals:**

- Make the `EvaluationReport` one transactionally owned immutable aggregate for retry and collision resolution.
- Define equality from the complete immutable values already persisted in its run, one outcome, and report rows, after the established PostgreSQL/time/numeric normalizations.
- Ensure any detected difference raises one explicit persistence-conflict failure and rolls back the whole attempted transaction.
- Prove equal and conflicting concurrent behavior against PostgreSQL from separate transactional callers.

**Non-Goals:**

- Do not change schema, migrations, constraints, query indexes, fingerprints, report calculation, the `EvaluationApi`, module dependencies, or dependencies.
- Do not repair incomplete aggregates created outside the transaction contract; an unexpectedly incomplete existing aggregate is an explicit integrity failure and is never supplemented by a retry.

## Decisions

### Resolve and compare the whole owned aggregate

The persistence adapter SHALL keep insert-first SQL and never use `ON CONFLICT DO UPDATE`.  On an already-present run or a uniqueness collision at either child row, it SHALL resolve the existing aggregate using the declared key that caused the collision, read the run/outcome/report rows owned by `evaluation`, and compare every immutable durable field to the candidate's corresponding normalized persistence value.  The report's unique `run_id` is checked as well as its primary key, and the outcome's run/signal/horizon uniqueness is checked as well as its primary key.

An exact aggregate match is the only idempotent path.  A different value, a collision pointing to a different aggregate identity, or a missing expected companion row raises one internal explicit immutable-retry conflict (or integrity failure for a pre-existing incomplete aggregate); neither outcome is reported as success.  The public `EvaluationApi` remains unchanged.

Alternative considered: trust the run, outcome, and report fingerprints alone.  Rejected because this adapter accepts an immutable `EvaluationReport` object and must protect persisted evidence even when a caller presents mismatched values under a colliding key; a primary-key check alone cannot prove aggregate equivalence.  Alternative considered: add an aggregate digest column or a deferred database trigger.  Rejected because V4 already stores all needed immutable values and the approved budget forbids schema work.

### Preserve the single short evaluation transaction

`EvaluationWriter.persist` remains the sole `@Transactional` business boundary.  A new aggregate inserts run, outcome, and report only within that transaction.  Any conflict helper throws an unchecked explicit conflict exception, so Spring rolls back every earlier insertion from that attempt.  Reads used to resolve a PostgreSQL uniqueness result occur in the same transaction after the conflict statement; no provider or cross-module access is added.

At PostgreSQL `READ COMMITTED`, a competing insert waits on the unique key and the following resolution query observes the committed winner.  Equal competitors converge to the complete winner; conflicting competitors compare against it and the loser rolls back.  The implementation must not add retry loops, locks, sleeps, or polling.

Alternative considered: select before insert.  Rejected as race-prone.  Alternative considered: permit child rows to be repaired after a conflict.  Rejected because a retry must not supplement a partial or mismatched immutable aggregate.

### Test the persistence boundary directly with real transaction separation

Add focused Failsafe PostgreSQL/Testcontainers coverage that calls the proxied evaluation application writer from separate executor threads.  The test supplies valid immutable report values without changing `EvaluationApi`; each call enters its own application transaction.  A bounded start barrier and bounded futures make overlap deterministic without production synchronization.

The behavioral RED is required before production changes because each new scenario changes observable durable behavior.  It must demonstrate the current defect at the expected assertion: equal writes do not yet return the established stable aggregate and/or a report/outcome collision either succeeds or leaves a partial attempted aggregate.  A Docker or Spring startup failure is not a valid RED.

The final tests SHALL cover: sequential equal retry; same-run changed immutable content; both late outcome and late report collisions with row-count and exact-row snapshots proving rollback; concurrent equal writes; and concurrent conflicting writes with one complete durable winner and one explicit conflict.  All assertions inspect rows in PostgreSQL, not repository mocks.  Existing evaluation behavior remains covered by `FirstSignalEvaluationIT`.

## Risks / Trade-offs

- [A full-row equality query omits a durable immutable column] → Build comparison values from every V4 immutable run/outcome/report column and have integration tests vary representative values in each row category.
- [A uniqueness resolution uses the wrong natural key] → Exercise both report primary-key/run-key and outcome primary-key/run-signal-horizon collisions, and assert no surviving partial rows.
- [Concurrent test threads share one transaction or merely execute sequentially] → Call the proxied writer from independent executor threads, require a bounded coordinated start, and assert the two terminal outcomes plus durable row counts.
- [An application failure becomes an untyped database detail] → Surface a named explicit immutable-retry conflict with a stable message/cause while preserving the unchanged public API.

## Migration Plan

1. Add the focused Testcontainers scenarios and capture a valid targeted behavioral RED before production edits.
2. Implement aggregate-aware insert-first resolution and explicit rollback-producing conflict behavior inside the existing evaluation persistence boundary.
3. Run the focused integration test to green, then the repository integrity, Maven, OpenSpec, doctor, and diff checks.

No Flyway or deployment migration is required.  Rollback is a code rollback only; no persisted evidence is rewritten.  If production already contains an incomplete aggregate inconsistent with this contract, fail explicitly and investigate it rather than silently repairing it.
