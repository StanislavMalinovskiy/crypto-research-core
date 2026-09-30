## ADDED Requirements

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
