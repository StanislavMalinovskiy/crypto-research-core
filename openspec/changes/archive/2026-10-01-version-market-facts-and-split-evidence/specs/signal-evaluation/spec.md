## ADDED Requirements

### Requirement: Separate immutable decision and evaluation evidence

For new versioned signals, the system SHALL identify the accepted decision from a finalized, revision-pinned decision snapshot whose admissible evidence was available at the declared decision knowledge cutoff. The accepted signal SHALL retain that decision snapshot identity, cutoff, selected source revisions, detector/scorer versions, risk evidence and reasoning without later mutation. A new `1h` evaluation SHALL retain the signal's exact decision identity and a separately finalized revision-pinned evaluation snapshot identity and cutoff. It SHALL select entry and horizon observations only from the evaluation snapshot under the existing `1h` entry, horizon and exact-friction rules. The run, outcome and report identities SHALL include or resolve both evidence identities and exact selected observation revisions. A later fact SHALL NOT change an already published signal, run, outcome or report.

#### Scenario: Publish signal before future outcome facts
- **WHEN** a decision snapshot is finalized without entry or horizon facts and a signal is accepted from it
- **AND** admissible entry and horizon facts are committed later and included in a distinct evaluation snapshot
- **THEN** a `1h` evaluation SHALL produce the priced outcome under the existing valuation rules
- **AND** the accepted signal identity and decision fingerprint SHALL equal their pre-arrival values.

#### Scenario: Exclude future decision evidence
- **WHEN** a fact observed or available after the decision cutoff appears in the evaluation snapshot
- **THEN** it SHALL NOT participate in the earlier signal decision
- **AND** replaying the signal by its decision snapshot SHALL return the same source revisions and identity.

#### Scenario: Late historical backfill cannot rewrite a decision
- **WHEN** a fact with an event or observation time before the decision cutoff is admitted after its decision snapshot was finalized
- **THEN** the frozen decision snapshot and accepted signal SHALL remain unchanged
- **AND** a later selection including that fact SHALL have a distinct evidence identity rather than silently revising the old one.

#### Scenario: Evaluation respects its own cutoff
- **WHEN** an entry or horizon fact is outside the evaluation snapshot or after its declared evaluation cutoff
- **THEN** it SHALL NOT be selected for that run
- **AND** missing admissible entry or horizon evidence SHALL produce a counted `UNPRICED` outcome with the established missing-price reason.

#### Scenario: Retry an immutable dual-evidence run
- **WHEN** an evaluation is repeated with the same accepted signal, decision snapshot, evaluation snapshot, cutoffs, build/source identity, algorithm version and canonical configuration
- **THEN** it SHALL return the same run, exact selected observation revisions, outcome, ordered report and fingerprints without duplicates
- **AND** conflicting immutable content for an existing run identity SHALL fail explicitly with no partial aggregate.

### Requirement: Dedicated decision and evaluation fingerprint storage

New versioned signal persistence SHALL store its decision snapshot fingerprint in a dedicated required `decision_dataset_fingerprint` column. New versioned evaluation persistence SHALL store that decision fingerprint and its later evaluation fingerprint in separate required `decision_dataset_fingerprint` and `evaluation_dataset_fingerprint` columns. New rows SHALL use module-owned v2 tables and SHALL NOT encode either identity in a legacy `dataset_fingerprint` column. Legacy tables, column meaning, constraints and immutable values SHALL remain unchanged. New outcomes and reports SHALL resolve both fingerprints through their v2 run and expose them separately in provenance.

#### Scenario: Preserve the legacy column meaning
- **WHEN** a new dual-evidence signal and run are published after forward migration
- **THEN** their dedicated columns SHALL retain the exact decision and evaluation fingerprints separately
- **AND** no new row or value SHALL be written into the legacy single-dataset tables or their `dataset_fingerprint` columns.

#### Scenario: Reject incomplete v2 evidence storage
- **WHEN** a v2 run lacks either required decision or evaluation fingerprint
- **THEN** the owning persistence boundary and database constraints SHALL reject publication atomically
- **AND** the reader SHALL NOT infer either fingerprint from a legacy column or alias one fingerprint as both.

### Requirement: Exact historical first-slice evidence compatibility

Forward migration to the dual-evidence contract SHALL leave persisted legacy dataset snapshots, signals, runs, outcomes and reports readable and exactly reproducible under their recorded legacy canonicalization and algorithm versions. A legacy fingerprint or run ID SHALL NOT be replaced by a recalculation under the new rules. New dual-evidence publication SHALL use distinct versioned identity rules; legacy evidence SHALL NOT be silently presented as if it had proven revision or realtime-availability metadata.

#### Scenario: Reproduce persisted pre-migration evidence
- **WHEN** a database populated by the old migrations with an immutable dataset snapshot, accepted signal, priced and unpriced `1h` runs, outcomes and reports is migrated forward
- **THEN** reads and replay under the recorded legacy versions SHALL return the same durable fields, ordered content, fingerprints and identities as captured before migration
- **AND** the old rows and their original source lineage SHALL remain unchanged.

#### Scenario: Keep new and old identities separate
- **WHEN** equivalent market values are evaluated once through a legacy snapshot and once through the new dual-evidence contract
- **THEN** their versions and provenance SHALL remain distinguishable
- **AND** neither path SHALL reinterpret or overwrite the other's immutable evidence.
