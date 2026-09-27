# recorded-market-replay Specification

## Purpose

Defines a deterministic recorded-input path that converts durable raw Solana evidence into normalized, point-in-time market facts and an immutable dataset identity without contacting a live provider.

## Requirements

### Requirement: Durable-first recorded replay
The system SHALL accept a finite recorded Solana dataset through a synchronous replay operation, persist every valid raw observation through the existing append-only raw-storage contract before normalization, and process no external network data during the replay.

#### Scenario: Replay a valid recorded dataset
- **WHEN** a recorded dataset containing stable-inclusion Solana observations is replayed
- **THEN** each raw observation SHALL be durably present before its normalized fact is committed
- **AND** the replay SHALL complete without contacting a live provider
- **AND** its result SHALL identify every accepted raw observation and normalized fact.

#### Scenario: Preserve unparseable evidence
- **WHEN** a raw observation is valid for append-only storage but its declared transformation cannot parse the payload
- **THEN** the exact raw observation SHALL remain stored for diagnosis and later replay
- **AND** no fabricated or partial normalized market fact SHALL be created
- **AND** the replay result SHALL expose the normalization failure explicitly.

### Requirement: Provider-independent normalized swap identity
The system SHALL normalize a supported recorded Solana swap into one provider-independent event identified by exact chain, transaction value and canonical event locator, while retaining raw-observation identity, provider, transformation version, block position, source time, observation time and exact numeric market evidence as lineage.

#### Scenario: Normalize one recorded swap
- **WHEN** a supported raw swap payload is replayed
- **THEN** the normalized event SHALL preserve its chain-scoped asset and wallet identities, side, exact quantities, USD price, USD liquidity, venue and authoritative times
- **AND** its normalized identity SHALL omit provider while its lineage SHALL retain the contributing provider and raw identity.

#### Scenario: Replay equal normalized evidence
- **WHEN** the same raw dataset and transformation version are replayed again
- **THEN** the normalized facts SHALL resolve to the same identities and values
- **AND** no duplicate normalized row SHALL be created.

#### Scenario: Detect conflicting normalization
- **WHEN** one normalized identity is produced with immutable normalized evidence different from its existing value
- **THEN** the replay SHALL fail that observation with an explicit conflict
- **AND** the existing normalized fact SHALL remain unchanged.

### Requirement: Point-in-time market observation access
The system SHALL expose normalized price and liquidity observations in a deterministic total order and SHALL restrict every point-in-time query to observations that were available at or before the caller's declared cutoff.

#### Scenario: Query a decision-time window
- **WHEN** a caller requests an asset's market observations for a bounded window and decision cutoff
- **THEN** only facts whose observation time is at or before that cutoff SHALL be returned
- **AND** equal-time facts SHALL use immutable normalized identity as the stable tie-break.

#### Scenario: Hide later observations from detection
- **WHEN** the immutable dataset also contains an observation recorded after the signal decision cutoff
- **THEN** that observation SHALL be excluded from the decision-time query
- **AND** it SHALL remain available to a later evaluation query whose cutoff admits it.

### Requirement: Immutable dataset fingerprint
The system SHALL create an immutable dataset snapshot whose algorithm-qualified fingerprint is derived from the canonical ordered content of the selected normalized facts, their raw lineage and transformation version rather than from a mutable query or database range.

#### Scenario: Rebuild the same snapshot
- **WHEN** equal recorded inputs are normalized with the same transformation and canonicalization versions in a different submission order
- **THEN** the dataset fingerprint and canonical ordered facts SHALL be identical
- **AND** incidental processing order SHALL not affect the snapshot.

#### Scenario: Change snapshot evidence
- **WHEN** a raw payload, immutable normalized value, transformation version or canonicalization version changes
- **THEN** the resulting dataset SHALL have distinguishable lineage and fingerprint
- **AND** the earlier snapshot SHALL not be overwritten.

### Requirement: Fixed-cardinality recorded replay attempt counters

The system SHALL maintain non-authoritative process-local counters named `crypto.research.marketdata.replay.items` and `crypto.research.marketdata.replay.invocations`. Items SHALL have exactly one application-defined tag, `status`, with values `normalized` or `normalization_failed`; invocations SHALL have exactly one application-defined tag, `outcome`, with values `completed` or `aborted`. The change SHALL add no other metric names or tag values. Registry-wide baseline tags are outside this change.

For each item that obtains an existing `NORMALIZED` or `NORMALIZATION_FAILED` replay result, the matching item counter SHALL increase by one. A non-null replay call SHALL increase exactly one invocation counter on normal return (`completed`) or propagation of a runtime exception (`aborted`). Counts describe processing attempts, not unique persisted observations. A null dataset SHALL retain its existing rejection before any replay telemetry. An item interrupted before obtaining either existing result SHALL not increment an item counter. All counter guarantees assume an operational telemetry sink; sink-failure behavior is defined below.

#### Scenario: R1 Count successful and failed normalization attempts
- **WHEN** a non-null replay processes three observations that produce two `NORMALIZED` results and one `NORMALIZATION_FAILED` result
- **THEN** the item counter deltas SHALL be `normalized=2` and `normalization_failed=1`
- **AND** invocation counter deltas SHALL be `completed=1` and `aborted=0`
- **AND** the original ordered results and failure details SHALL remain unchanged.

#### Scenario: R2 Repeated processing counts attempts
- **WHEN** the same dataset with two successful items is replayed twice
- **THEN** aggregate item counter deltas SHALL be `normalized=4` and `normalization_failed=0`
- **AND** the completed invocation delta SHALL be two
- **AND** existing durable idempotency behavior SHALL remain unchanged.

#### Scenario: R3 Empty and null inputs are distinct
- **WHEN** an empty non-null dataset is replayed and a separate null dataset call is rejected
- **THEN** the empty call SHALL produce an unchanged empty result and one completed invocation increment with no item increments
- **AND** the null call SHALL produce no counter increments or summary and SHALL retain its existing exception.

#### Scenario: R4 An abort retains completed-prefix accounting
- **WHEN** the first item normalizes, the second returns an existing normalization failure, the third raises a raw-storage runtime exception, and a fourth item remains
- **THEN** counter deltas SHALL be `normalized=1`, `normalization_failed=1`, `completed=0`, and `aborted=1`
- **AND** the third item SHALL add no item-counter result, the fourth SHALL not be processed, and the original exception object SHALL propagate.

### Requirement: Bounded structured recorded replay summary

With an operational enabled INFO log sink, the system SHALL emit exactly one summary per non-null replay call that returns normally or aborts with a runtime exception. It SHALL emit no per-item telemetry log. The summary SHALL use the constant message `Recorded replay summary` and exactly these application-defined structured fields: `operation=recorded_replay`, `outcome=completed|aborted`, and non-negative integer fields `attempted`, `normalized`, `normalization_failed`, `unclassified`. `attempted` counts observations whose processing has begun, including an observation whose raw construction or storage fails. The two result fields count only obtained existing replay results; `unclassified=attempted-normalized-normalization_failed`. A completed call has `unclassified=0`; an aborted sequential call has at most one unclassified item. Logger metadata supplied by the logging framework is not part of this field schema.

The summary SHALL add no input-derived strings, identity, provider, fixture label, payload, failure message, exception object, financial value, authoritative timestamp, or additional application field. The system SHALL preserve the caller's result and exception behavior independently of whether INFO logging is enabled. Metrics and summaries SHALL not become research evidence, database state, admission decisions or completeness guarantees.

#### Scenario: R5 Summarize completed and empty calls
- **WHEN** an enabled INFO sink observes the mixed call in R1 and a separate empty call
- **THEN** it SHALL receive one structured summary per call with the constant message and operation
- **AND** the mixed call SHALL have `outcome=completed`, `attempted=3`, `normalized=2`, `normalization_failed=1`, `unclassified=0`
- **AND** the empty call SHALL have `outcome=completed` and all four counts zero.

#### Scenario: R6 Summarize aborted prefix without domain conversion
- **WHEN** the call in R4 aborts
- **THEN** its single summary SHALL have `outcome=aborted`, `attempted=3`, `normalized=1`, `normalization_failed=1`, `unclassified=1`
- **AND** the summary SHALL contain no throwable or exception text
- **AND** no new domain replay status SHALL be introduced.

#### Scenario: R7 Input variation does not create diagnostic dimensions
- **WHEN** replay calls contain differing provider names, fixture labels, transaction identities, payload strings and existing failure details
- **THEN** their metric dimensions SHALL remain within the fixed two item and two invocation series
- **AND** their summary messages and string fields SHALL remain constant except for the declared invocation outcome
- **AND** diagnostics SHALL contain only the declared application fields.

### Requirement: Operational telemetry preserves replay behavior

Instrumentation SHALL leave raw-before-normalization calls, their transaction boundaries, original result content and order, existing caught normalization exceptions, and propagation of other exceptions unchanged. Runtime exceptions raised specifically by metric registration/increment or summary logging SHALL be contained at that diagnostic boundary without retry, fallback domain behavior, or replacement of a business result or exception. A metric-sink failure SHALL not prevent the summary attempt, and a log-sink failure SHALL not undo metrics or domain work. The system SHALL not catch fatal JVM errors to satisfy this requirement. No guarantee of complete diagnostics is made when a diagnostic sink fails or INFO is disabled.

#### Scenario: R8 Diagnostic failures cannot fail successful replay
- **WHEN** otherwise successful replay encounters a runtime failure from its metric sink or its summary log sink
- **THEN** it SHALL return the same successful replay results
- **AND** metric-sink failure SHALL not prevent the summary attempt
- **AND** it SHALL not retry domain operations or diagnostics.

#### Scenario: R9 Diagnostic failures cannot mask an abort
- **WHEN** raw storage or an unexpected normalization runtime exception aborts replay and a diagnostic sink also fails
- **THEN** the exact original domain exception SHALL propagate unchanged
- **AND** subsequent observations SHALL not be processed
- **AND** no partial replay result SHALL be returned instead.

#### Scenario: R10 Existing normalization failures remain ordinary results
- **WHEN** normalization raises an exception already handled as `NORMALIZATION_FAILED` by the accepted replay behavior
- **THEN** replay SHALL preserve that item's existing failure details and continue to later observations
- **AND** the call SHALL count and summarize as completed if no other runtime exception aborts it.
