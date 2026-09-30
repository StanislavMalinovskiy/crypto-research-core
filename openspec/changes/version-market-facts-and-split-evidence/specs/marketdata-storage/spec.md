## MODIFIED Requirements

### Requirement: Price observations are separate facts

The system SHALL store swap-derived price observations as facts separate from normalized swaps, each carrying the chain, asset, quote asset, venue, source event identity, native price with explicit scale, trade notional in the quote asset, observation time, block position and provider lineage. Existing event-key records SHALL retain their historical identity and read behavior. New versioned price observations SHALL retain an immutable source-observation reference, derivation version, exact content and revision identity, so distinct valid revisions for one canonical source event can coexist. Equal retries of one revision SHALL be idempotent; the same revision identity with different immutable content SHALL be rejected. Point-in-time queries SHALL return only observations within the requested window at or before the cutoff in a deterministic total order. A new versioned selection SHALL use only explicitly selected revisions whose declared availability is admissible at its knowledge cutoff.

#### Scenario: Price observation retry is idempotent
- **WHEN** the same price observation is recorded twice from the same source event
- **THEN** the second record SHALL resolve to the stored observation without duplication.

#### Scenario: Point-in-time window excludes later observations
- **WHEN** price observations are queried for a window ending at a cutoff and an observation was observed after that cutoff
- **THEN** the later observation SHALL NOT appear in the result
- **AND** equal-time observations SHALL be ordered by their immutable source identity.

#### Scenario: Retain a corrected price revision
- **WHEN** a new derivation version produces a different price from the same canonical event
- **THEN** both immutable revisions SHALL remain addressable by their exact revision identities
- **AND** an existing snapshot SHALL continue to return the revision it selected.

### Requirement: Liquidity observations are separate facts

The system SHALL store pool liquidity observations as facts separate from swaps and prices, each carrying the chain, asset, pool address, source event identity, liquidity value with explicit scale, observation time, block position and provider lineage. Existing event-key records SHALL retain their historical identity and read behavior. New versioned liquidity observations SHALL retain immutable source-observation and derivation identities, exact content and a revision identity, be idempotent per equal revision, and reject conflicting content for the same revision without overwrite. Distinct valid revisions for one canonical event, asset and pool SHALL coexist. Point-in-time queries SHALL be deterministically ordered and a new versioned selection SHALL include only explicitly selected, cutoff-admissible revisions.

#### Scenario: Liquidity observation retry is idempotent
- **WHEN** the same liquidity observation is recorded twice from the same source event
- **THEN** the second record SHALL resolve to the stored observation without duplication.

#### Scenario: Liquidity is queryable point-in-time
- **WHEN** liquidity observations are queried for an asset within a window at or before a cutoff
- **THEN** only observations inside the window SHALL be returned in deterministic order.

#### Scenario: Retain a corrected pool revision
- **WHEN** a second valid derivation of one canonical event, asset and pool is recorded
- **THEN** it SHALL receive a distinct revision identity
- **AND** the first revision and snapshots selecting it SHALL remain unchanged.

### Requirement: USD conversion facts are immutable derivations

The system SHALL store USD conversion facts as separate records referencing the converted native price observation and the quote price observation used, with the conversion method version and computed value at explicit scale. Existing event-key conversion facts and their source references SHALL retain their historical read behavior. Each new versioned USD fact SHALL pin the exact immutable revisions of both source prices, its method version and its own revision identity; an event key alone SHALL NOT substitute for either source revision. Equal retry SHALL resolve to the stored fact, while conflicting immutable content for the same revision SHALL be rejected without overwrite. A conversion based on different price revisions or a different method SHALL coexist as a distinct revision.

#### Scenario: Conversion retry is idempotent
- **WHEN** the same USD conversion is recorded twice with identical evidence
- **THEN** the second store SHALL resolve to the stored conversion fact.

#### Scenario: Conflicting conversion is rejected
- **WHEN** a USD conversion with a different value is recorded for an already-stored source identity and the same revision identity
- **THEN** the store SHALL reject it explicitly.

#### Scenario: Pin both source price revisions
- **WHEN** a versioned USD conversion is recorded with converted and quote price revision identities
- **THEN** each reference SHALL resolve to that exact immutable price revision
- **AND** a missing or mismatched revision on either side SHALL prevent publication of the USD fact.

#### Scenario: Preserve alternative conversion lineage
- **WHEN** a valid conversion uses a different converted price revision, quote price revision or conversion method
- **THEN** it SHALL have distinguishable immutable lineage
- **AND** an earlier USD revision and any snapshot selecting it SHALL remain unchanged.

## ADDED Requirements

### Requirement: Versioned fact admission and deterministic selection

New normalized swap, price, liquidity and USD fact revisions SHALL retain canonical event identity separately from immutable source-observation and derivation identity. A versioned selection SHALL pin the exact revision for every included fact, use a declared deterministic selection rule and total order, and reject an ambiguous event-only reference rather than silently choose the newest provider or derivation. The owning market-data finalizer SHALL independently verify complete disposition of visible canonical fact keys in the declared scope within its freeze transaction; caller-supplied revision references alone SHALL NOT establish completeness. Each visible key SHALL have exactly one selected admissible revision or one explicit evidence-supported exclusion allowed by the declared policy. Facts with unknown or modeled availability SHALL be identified as such and SHALL NOT be represented as verified realtime availability.

#### Scenario: Distinguish source observations and derivations
- **WHEN** two providers or two derivation versions supply valid facts for one canonical event
- **THEN** their source and revision identities SHALL remain distinguishable
- **AND** neither fact SHALL overwrite the other or be silently promoted as the preferred revision.

#### Scenario: Reject an ambiguous revision request
- **WHEN** a new selection names only a canonical event that has multiple eligible revisions
- **THEN** selection SHALL fail explicitly before publishing a snapshot
- **AND** it SHALL NOT choose a revision by insertion order, provider arrival order or a mutable latest-row rule.

#### Scenario: Preserve unknown historical availability
- **WHEN** a legacy fact has no proven realtime availability instant
- **THEN** its historical content SHALL remain readable
- **AND** a new realtime point-in-time selection SHALL NOT silently treat its source event time or ingestion timestamp as verified availability.
