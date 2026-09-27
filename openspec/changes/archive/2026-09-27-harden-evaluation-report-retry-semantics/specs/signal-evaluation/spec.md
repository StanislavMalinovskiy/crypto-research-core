## MODIFIED Requirements

### Requirement: Idempotent reproducible evaluation report
The system SHALL persist one complete immutable evaluation aggregate comprising its run manifest, signal-horizon outcome, and one-family evidence report. The aggregate SHALL be identified by its declared run identity and SHALL retain a complete provenance manifest containing build identity, source revision and dirty state, evaluation algorithm version, canonical configuration fingerprint, dataset fingerprint, and evaluation cutoff; no seed is required because this slice uses no randomness. A retry SHALL be an idempotent success only when every immutable value durably represented by that aggregate is equal after the established persistence normalizations. A retry or uniqueness collision with any differing immutable run, outcome, or report value SHALL fail with an explicit immutable-retry conflict and SHALL not mutate, replace, or supplement the accepted aggregate.

#### Scenario: Produce the first evidence report
- **WHEN** the recorded allowed signal and priced 1h outcome are evaluated with complete provenance
- **THEN** the report SHALL contain one `LIQUIDITY_SPIKE` ENTRY signal, one priced outcome and exact average net return `0.16000000`
- **AND** it SHALL distinguish signal score from measured return
- **AND** every report value SHALL resolve to the immutable signal, outcome, dataset and run evidence.

#### Scenario: Repeat the evaluation run
- **WHEN** the same immutable dataset, signal snapshot, cutoff, build/source identity, algorithm version and canonical configuration are evaluated again
- **THEN** the retry SHALL return the one stable durable run, outcome, ordered report content and report fingerprint
- **AND** no duplicate durable effect SHALL be created.

#### Scenario: Reject different immutable content for an existing aggregate
- **WHEN** a retry resolves to an existing run identity but any immutable persisted run, outcome, or report content differs
- **THEN** the evaluation SHALL fail with an explicit immutable-retry conflict
- **AND** the originally accepted run, outcome, and report SHALL remain byte-for-byte-equivalent in their durable fields
- **AND** no additional report or outcome SHALL be created.

#### Scenario: Roll back a late outcome or report collision
- **WHEN** a new persistence attempt first inserts a run or outcome but subsequently encounters an immutable outcome or report uniqueness collision with different content
- **THEN** the evaluation SHALL fail with an explicit immutable-retry conflict
- **AND** the failed attempt SHALL leave no newly committed run, outcome, report, or partial aggregate
- **AND** the pre-existing colliding aggregate SHALL remain complete and unchanged.

#### Scenario: Concurrent equal report persistence
- **WHEN** two separate database transactions concurrently persist equal immutable evaluation-report content
- **THEN** both operations SHALL resolve successfully to one stable aggregate result
- **AND** PostgreSQL SHALL contain exactly one run, one outcome, and one report for that aggregate identity.

#### Scenario: Concurrent conflicting report persistence
- **WHEN** two separate database transactions concurrently persist the same aggregate identity with different immutable content
- **THEN** exactly one operation SHALL establish one complete immutable aggregate and the other SHALL receive an explicit immutable-retry conflict
- **AND** PostgreSQL SHALL contain no partial or mixed variant and no duplicate run, outcome, or report.

#### Scenario: Reject incomplete provenance
- **WHEN** mandatory run provenance is missing
- **THEN** the evaluation SHALL fail before publishing a comparable report
- **AND** no incomplete run SHALL be silently grouped with reproducible results.
