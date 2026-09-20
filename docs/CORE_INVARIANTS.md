# Core invariants

## Purpose and authority

This file is a compact cross-cutting review checklist derived from existing project rules. It does not replace
accepted ADRs, `docs/ARCHITECTURE.md`, `docs/REPRODUCIBILITY.md`, module documentation, or accepted OpenSpec
requirements. Those sources remain authoritative for their stated responsibilities. A conflict between this
checklist and a controlling source is a documentation defect that must be resolved before approval.

Architects, builders, and reviewers apply only the invariants affected by a change, but they must not silently
omit an applicable invariant. Before approval, the reviewing role reports:

```text
Invariant | Applicable | Evidence | Verdict
```

`Not applicable` requires a short reason. A blanket statement that all invariants were checked is not evidence.

## Integrity and durability

### CI-01: No silent outcome or evidence loss

An accepted input, candidate, outcome, failure, or other required fact must remain measurable or have an
explicit terminal status. The system must not silently discard inconvenient or incomplete results.

Controlling sources: [root agent guide](../AGENTS.md), accepted OpenSpec capabilities, and owning module docs.

### CI-02: Gaps and recovery remain explicit

Reconnect, retry, backfill, degradation, and recovery must not silently create or conceal a data gap. Gap or
incomplete-evidence semantics must be observable wherever the controlling contract requires them.

Controlling sources: [architecture](ARCHITECTURE.md), [operations](OPERATIONS.md), and accepted provider or
market-data OpenSpec capabilities.

### CI-03: Equal reprocessing is idempotent

Reprocessing the same canonical input or retrying the same immutable operation must reproduce the same durable
effect without additional domain rows or changed evidence.

Controlling sources: [reproducibility](REPRODUCIBILITY.md), accepted OpenSpec capabilities, and owning module
docs.

### CI-04: Immutable conflict is not equal retry

Matching an identity or uniqueness key is insufficient to prove equality. A retry whose immutable persisted
content differs must fail explicitly and must not be reported as a successful idempotent replay.

Controlling sources: [reproducibility](REPRODUCIBILITY.md), accepted OpenSpec capabilities, and owning module
docs.

### CI-05: Retry creates no duplicate domain outcome

Sequential and concurrent retry must resolve through the declared domain identity and database uniqueness
contract. It must not create duplicate candidates, signals, outcomes, reports, or equivalent owned facts.

Controlling sources: accepted OpenSpec capabilities, owning module docs, and [testing strategy](TESTING.md).

### CI-06: Owned transitions are atomic

One owned domain transition commits as one short application transaction. Failure must leave no partial durable
aggregate, and retry must begin from a valid committed state.

Controlling sources: [root agent guide](../AGENTS.md), [reproducibility](REPRODUCIBILITY.md), and owning module
docs.

## Reproducibility and time

### CI-07: Behavioral parameters participate in reproducible identity

Every parameter capable of changing a persisted or published result must be represented directly in canonical
identity or through an immutable version or configuration fingerprint defined by the controlling contract.

Controlling source: [reproducibility](REPRODUCIBILITY.md).

### CI-08: Historical and realtime semantics remain equivalent

Given the same admitted evidence, cutoff, versions, configuration, and seed, historical replay and realtime or
forward processing must preserve the same domain semantics unless an accepted contract explicitly distinguishes
them.

Controlling sources: [reproducibility](REPRODUCIBILITY.md), accepted OpenSpec capabilities, and owning module
docs.

### CI-09: Point-in-time decisions use no future information

Detection, risk, evaluation, and reporting may use only evidence admissible at the declared decision or
evaluation cutoff. Later corrections or observations must not silently rewrite the historical decision.

Controlling sources: [reproducibility](REPRODUCIBILITY.md) and accepted OpenSpec capabilities.

### CI-10: Ordering is total and deterministic

Every order-sensitive operation defines a total order with an immutable tie-break. Input iteration order,
concurrent completion, database plan choice, or provider batch order must not change the result.

Controlling source: [reproducibility](REPRODUCIBILITY.md).

### CI-11: Concurrency changes throughput, not meaning

Concurrency may affect completion time but must not change domain values, identities, ordering, counts, or
published evidence.

Controlling sources: [reproducibility](REPRODUCIBILITY.md) and [testing strategy](TESTING.md).

## Boundaries and degradation

### CI-12: Provider behavior stays behind owning boundaries

Provider-specific payloads, clients, pagination, retry details, and fallback quirks must not leak into domain
logic or another module's implementation. Cross-module use goes through the owning module API.

Controlling sources: [architecture](ARCHITECTURE.md), [root agent guide](../AGENTS.md), and accepted ADRs.

### CI-13: No fallback silently reduces correctness

A fallback must not fabricate evidence, weaken freshness or finality, hide a failure, or silently change the
meaning of a result. Any accepted degradation must be explicit in the controlling contract and provenance.

Controlling sources: [root agent guide](../AGENTS.md), accepted OpenSpec capabilities, and owning module docs.

### CI-14: Transactions respect ownership and exclude provider I/O

The owning module's application use case defines the transaction. Controllers and repositories do not own the
business transaction, provider calls do not occur inside it, and one module does not mutate another module's
tables.

Controlling sources: [architecture](ARCHITECTURE.md), [root agent guide](../AGENTS.md), and
[reproducibility](REPRODUCIBILITY.md).

### CI-15: Work and waits remain bounded

Concurrency, database access, provider rate, retries, backoff, batches, queues, polling, and waits must have
explicit bounds appropriate to the owning contract. A failure path must not introduce an unbounded retry or
wait.

Controlling sources: [root agent guide](../AGENTS.md), [architecture](ARCHITECTURE.md), and
[operations](OPERATIONS.md).
