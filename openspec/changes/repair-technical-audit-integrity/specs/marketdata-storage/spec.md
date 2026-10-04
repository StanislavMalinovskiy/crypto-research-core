## ADDED Requirements

### Requirement: Component-safe snapshot dispositions with historical fingerprint compatibility
New versioned snapshot finalization SHALL distinguish complete canonical fact tuples, revision references and exclusion tuples by their individual components even when valid opaque components contain separators. It SHALL reject any missing or contradictory disposition and SHALL retain the recorded existing fingerprint serialization and unchanged readback of already saved snapshots.

#### Scenario: Distinct opaque identities share a separator representation
- **WHEN** two visible facts differ as transaction `a|b` with locator `c` versus transaction `a` with locator `b|c`
- **THEN** omitting either fact SHALL reject publication as incomplete
- **AND** selecting both distinct admissible revisions SHALL succeed with two covered facts.

#### Scenario: Retain a historical snapshot fingerprint
- **WHEN** unchanged previously valid snapshot content is finalized after the comparison repair
- **THEN** its fingerprint SHALL match its pre-repair literal fingerprint
- **AND** saved snapshot reads SHALL retain their original manifest and fingerprint without rewriting.

### Requirement: Independently versioned unambiguous fact revision dimensions
New price and liquidity revision identities SHALL encode each asset and venue or pool dimension separately under the canonical hash domain `market-fact-revision-v2`, with write results explicitly identifying `EXPLICIT_REVISION_V2`. The original non-null derivation version SHALL remain unchanged in hash inputs and persisted provenance, preserving the existing full 128-character capacity without an identity-version prefix or schema change. Stored identity-version recognition SHALL recompute v1 and v2 candidates from exact saved identity fields and require exactly one candidate to match the recorded key, rejecting ambiguous or unmatched evidence explicitly. A resubmission of the same recorded v1 fact through the upgraded write path SHALL produce an explicit legacy-version conflict before insertion rather than silently create a second revision. A different component tuple with the same legacy separator representation SHALL NOT be treated as the same fact. Existing revision identities and snapshots referring to them SHALL remain addressable unchanged. Immutable snapshot exclusion retry comparisons SHALL compare complete persisted component tuples rather than separator concatenations.

#### Scenario: Distinguish alternative dimension boundaries
- **WHEN** valid dimensions are asset `a|b` with venue or pool `c` versus asset `a` with venue or pool `b|c` under otherwise equal source lineage
- **THEN** the new recorded revision encoding SHALL create distinct identities
- **AND** previously stored old-version revisions SHALL remain readable by their exact original keys.

#### Scenario: Conflicting exclusion cannot equal a retry
- **WHEN** a snapshot retry changes opaque exclusion components but preserves their separator-concatenated representation
- **THEN** immutable retry verification SHALL reject the conflict
- **AND** the stored exclusion SHALL remain unchanged.

#### Scenario: Preserve full derivation provenance capacity
- **WHEN** a new price or liquidity write has a valid original derivation version of 117 or 128 characters
- **THEN** it SHALL persist and read back that exact derivation value and return `EXPLICIT_REVISION_V2`
- **AND** an equal retry SHALL retain the same revision identity without adding or shortening provenance
- **AND** a null derivation version SHALL remain rejected.

#### Scenario: Recognize saved identity versions without a new column
- **WHEN** recognition is requested for a saved price or liquidity revision
- **THEN** exactly one v1 or v2 candidate computed from its original stored identity fields SHALL match its key and identify its version
- **AND** neither or both candidate matches SHALL produce an explicit integrity failure rather than infer a version from derivation text.

#### Scenario: Resubmit a legacy fact without silently duplicating it
- **WHEN** the upgraded write path receives the same canonical component tuple and identity lineage as an existing recognized v1 price or liquidity fact
- **THEN** it SHALL report an explicit legacy-version conflict and leave saved rows and references unchanged
- **AND** a different actual asset/venue-or-pool tuple whose legacy joined dimensions collide SHALL remain eligible for its distinct v2 write.
