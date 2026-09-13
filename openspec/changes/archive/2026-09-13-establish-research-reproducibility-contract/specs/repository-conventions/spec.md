## ADDED Requirements

### Requirement: Implicit nondeterminism guard
Repository verification SHALL reject unapproved implicit wall-clock reads and unseeded randomness entry points in production module source code.

#### Scenario: Production source verification
- **WHEN** the Maven repository-convention tests inspect production sources beneath the application module root
- **THEN** direct machine-clock and unseeded-randomness entry points SHALL fail verification
- **AND** explicitly supplied time sources, reference instants and seeded random sources SHALL remain allowed.
