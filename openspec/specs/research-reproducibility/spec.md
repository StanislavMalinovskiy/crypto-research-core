# Research Reproducibility Specification

## Purpose

Defines the evidence and deterministic semantics required to reproduce research, signal and evaluation results from the same point-in-time inputs.

## Requirements

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

### Requirement: Reproducible decision and later evaluation selections

For new dual-evidence runs, the system SHALL resolve an immutable decision-selection identity and an immutable evaluation-selection identity separately. Each identity SHALL resolve its declared scope, exact ordered fact revisions, explicit exclusions with reasons/evidence, complete coverage counts, content fingerprint, selection/admissibility/exclusion and canonicalization versions, declared knowledge cutoff, observation/event cutoff and availability status. Those scope and coverage values SHALL participate in the fingerprint and immutable retry comparison. The evaluation provenance SHALL include the accepted signal's decision identity unchanged as well as its own later evaluation identity. Replaying either selection SHALL use the frozen manifest and recorded rules rather than a present-day table range, unqualified admission timestamp, or implicit newest-revision choice. Reports SHALL identify the declared scope and exclusions rather than claim unproven knowledge of all provider or chain facts.

#### Scenario: Replay after concurrent publication
- **WHEN** another transaction assigned a fact an earlier timestamp but committed after a decision selection was frozen
- **THEN** replay of that decision identity SHALL retain exactly the pre-commit ordered membership and fingerprint
- **AND** the late fact SHALL appear only in a newly finalized selection that explicitly includes it.

#### Scenario: Distinguish changed selection inputs
- **WHEN** the selected revision, canonicalization version, scope, inclusion/exclusion disposition or evidence, selection rule, admissibility/exclusion policy, cutoff or selected content changes
- **THEN** the new selection SHALL have distinguishable versioned lineage and fingerprint
- **AND** an existing identity SHALL remain resolvable without mutation.

#### Scenario: Report both evidence timelines
- **WHEN** a `1h` evaluation is published from a prior signal and later market facts
- **THEN** its provenance SHALL resolve both the immutable decision evidence and the immutable evaluation evidence with their respective cutoffs
- **AND** the report SHALL not claim that facts first available after the signal decision were decision-time knowledge.

#### Scenario: Preserve historical digest rules
- **WHEN** a legacy snapshot or run is reproduced after forward migration
- **THEN** its recorded canonical encoding and digest version SHALL yield the original fingerprint and ordered result
- **AND** new revision or dual-selection fields SHALL NOT be implicitly injected into the legacy digest.
