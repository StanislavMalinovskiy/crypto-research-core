## ADDED Requirements

### Requirement: Versioned boundary microsecond normalization and immutable readback
Authoritative instants in new versioned snapshot scope, event scope, knowledge cutoff and signal detection or risk requests SHALL be truncated to UTC PostgreSQL microsecond precision before validation, identity computation and querying. Persisted older manifests SHALL remain readable with their original recorded values and fingerprints; normalization SHALL NOT silently rewrite or re-fingerprint them.

#### Scenario: Equivalent sub-microsecond request instants
- **WHEN** two new versioned finalization or detection requests differ only below microsecond precision
- **THEN** their normalized validation, queries and reproducible identities SHALL be equal
- **AND** a scope ending at the normalized cutoff SHALL not be rejected only because its original instant contained nanoseconds.

#### Scenario: Read an older finer-precision manifest
- **WHEN** a saved older manifest contains finer-than-microsecond scope values
- **THEN** readback SHALL return its original immutable manifest and recorded fingerprint
- **AND** new-request normalization SHALL not mutate the historical row.
