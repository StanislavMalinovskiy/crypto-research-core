## Purpose

Defines the evidence and deterministic semantics required to reproduce research, signal and evaluation results from the same point-in-time inputs.

## ADDED Requirements

### Requirement: Reproducible computational result
The system SHALL produce the same computational result when the dataset snapshot, code identity, algorithm version, canonical configuration and random seed are identical.

#### Scenario: Repeat an evaluation run
- **WHEN** an evaluation is repeated with the same recorded dataset fingerprint, code/build identity, algorithm version, canonical configuration and seed
- **THEN** its ordered domain results SHALL be identical
- **AND** incidental execution details such as worker count or completion order SHALL not change the result.

### Requirement: Explicit UTC time semantics
Persisted event, decision, cutoff and evaluation timestamps SHALL represent UTC instants, and domain/application calculations SHALL receive their current-time source explicitly.

#### Scenario: Time-dependent calculation
- **WHEN** a rule depends on the current instant or an elapsed duration
- **THEN** the caller SHALL supply the time source or reference instant
- **AND** the calculation SHALL be testable without reading the machine clock implicitly.

#### Scenario: Point-in-time cutoff
- **WHEN** a signal or evaluation is computed for a declared cutoff
- **THEN** only observations available at or before that cutoff SHALL participate
- **AND** source event time and system observation time SHALL remain distinguishable where both exist.

### Requirement: Exact financial arithmetic
Authoritative token quantities, prices, costs, PnL, ratios and scores SHALL avoid binary floating-point arithmetic and SHALL use an exact representation with explicit precision, scale and rounding at every lossy boundary.

#### Scenario: Numeric transformation
- **WHEN** a calculation divides, converts units, rounds or persists a financial value
- **THEN** its precision, scale and rounding rule SHALL be explicit and versioned with the algorithm or schema contract
- **AND** the persisted or published result SHALL be independent of platform-default numeric behavior.

### Requirement: Run provenance manifest
Every persisted research or evaluation run SHALL identify the code/build and source revision, algorithm version, canonical configuration fingerprint, dataset fingerprint, data cutoff and any random seed needed to reproduce its result.

#### Scenario: Inspect result provenance
- **WHEN** an operator or test inspects a persisted result set or report
- **THEN** it SHALL be possible to resolve the exact run provenance manifest
- **AND** missing mandatory provenance SHALL make the run incomplete rather than silently comparable with reproducible runs.

### Requirement: Dataset and normalization lineage
Every reproducible dataset snapshot SHALL have a stable fingerprint, and normalized observations SHALL preserve their provider/source identity and transformation version.

#### Scenario: Rebuild a dataset snapshot
- **WHEN** recorded raw inputs are normalized again with the same transformation version and canonical ordering
- **THEN** the resulting dataset fingerprint SHALL match the original snapshot
- **AND** a changed raw input or transformation version SHALL produce distinguishable lineage.

### Requirement: Deterministic ordering and randomness
Any result affected by iteration order, equal-ranked values or randomness SHALL define a total ordering, deterministic tie-break rules and an explicit seed.

#### Scenario: Concurrent or unordered input processing
- **WHEN** logically identical inputs arrive or complete in a different order
- **THEN** canonical ordering and tie-break rules SHALL yield the same ordered result
- **AND** unseeded randomness SHALL not influence a persisted research result.
