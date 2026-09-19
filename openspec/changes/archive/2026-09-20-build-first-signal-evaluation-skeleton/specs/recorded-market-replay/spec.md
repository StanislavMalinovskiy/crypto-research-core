## Purpose

Defines a deterministic recorded-input path that converts durable raw Solana evidence into normalized, point-in-time market facts and an immutable dataset identity without contacting a live provider.

## ADDED Requirements

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

