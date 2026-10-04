# Market Data Storage Specification

## Purpose

Provides an append-only, module-owned record of raw provider observations so later normalization and replay operate on durable, identity-safe evidence.

## Requirements

### Requirement: Module-owned raw observation storage
The system SHALL persist raw chain observations in a `marketdata`-owned PostgreSQL schema with explicit `chain_id`, `transaction_value`, `event_locator`, `provider`, `observed_block_position`, optional `observed_block_hash`, optional source event time, observation time, exact payload, algorithm-qualified payload digest, parser version and ingestion time fields. The schema SHALL NOT retain the ambiguous legacy names `chain`, `transaction_id`, `event_id`, `block_position` or `block_hash`.

#### Scenario: Persist complete replay evidence
- **WHEN** a valid raw provider observation is accepted
- **THEN** all supplied identity, provenance, chain-position, time and payload evidence SHALL be durably stored in one transaction
- **AND** the stored observation SHALL be readable without consulting the provider.

#### Scenario: Preserve distinct block evidence
- **WHEN** an observation supplies an ordered block position and a block hash
- **THEN** the position SHALL be stored as a non-negative numeric value
- **AND** the block hash SHALL remain separate optional evidence rather than being substituted for the position.

#### Scenario: Expose only the canonical physical identity names
- **WHEN** the first market-data migration is applied
- **THEN** the raw-observation table SHALL use the explicit network, transaction-value, event-locator and observed-block column names
- **AND** none of the ambiguous legacy identity or block column names SHALL exist.

### Requirement: Raw provider-observation identity
The system SHALL identify a raw observation by the complete tuple of exact CAIP-2 network identity, opaque transaction value, canonical transaction-scoped event locator and provider. The same storage model SHALL accept every supported network without a network-specific table or identity branch. Finality and canonicality SHALL remain outside identity; V1 SHALL admit only historical, finalized or otherwise confirmed stable input under the supplying caller's contract.

#### Scenario: Repeat one provider observation
- **WHEN** the same provider submits the same transaction event more than once
- **THEN** every submission SHALL resolve to the same stored raw identity
- **AND** no additional row SHALL be created.

#### Scenario: Preserve independent provider evidence
- **WHEN** two providers submit observations for the same chain transaction event
- **THEN** the observations SHALL be stored as distinct raw records
- **AND** provider identity SHALL remain provenance for later normalization.

#### Scenario: Preserve equal local values on different networks
- **WHEN** observations use the same transaction value, event locator and provider on Solana Mainnet and Base Mainnet
- **THEN** both observations SHALL be stored as distinct raw records
- **AND** each exact CAIP-2 network identifier SHALL remain part of durable identity.

#### Scenario: Persist a synthetic EVM observation
- **WHEN** stable synthetic evidence for `eip155:8453` supplies an opaque transaction value, canonical receipt-log locator, provider and observed EVM block position
- **THEN** it SHALL be stored and read through the same raw-observation contract used for Solana
- **AND** no EVM-specific persistence schema or provider implementation SHALL be required.

#### Scenario: Keep finality outside identity
- **WHEN** stable evidence is admitted after finality or canonicality has been determined by an approved caller contract
- **THEN** its raw identity SHALL remain `(chain_id, transaction_value, event_locator, provider)`
- **AND** storage SHALL not claim to calculate finality or canonicality itself.

### Requirement: Unpartitioned global raw-observation identity
The V1 raw-observation table SHALL remain unpartitioned and SHALL use exactly `(chain_id, transaction_value, event_locator, provider)` as its natural primary key. Each textual primary-key column SHALL use explicit deterministic bytewise `C` collation so exact identity equality remains case-sensitive and independent of the database default collation. `ingested_at`, finality and canonicality SHALL NOT participate in identity, and V1 SHALL NOT introduce a surrogate identifier, BRIN index or secondary index.

#### Scenario: Inspect the V1 physical identity
- **WHEN** V1 is applied to an empty PostgreSQL database
- **THEN** `marketdata.raw_chain_events` SHALL be an ordinary unpartitioned table whose primary-key columns appear exactly in the documented order
- **AND** all four textual primary-key columns SHALL report `C` collation
- **AND** it SHALL have no surrogate key, BRIN index or secondary index.

#### Scenario: Preserve case-only identity differences in PostgreSQL
- **WHEN** two raw observations have equal transaction value, event locator and provider but valid CAIP-2 network references that differ only by case
- **THEN** PostgreSQL SHALL store them as two distinct raw identities
- **AND** neither identity SHALL be normalized or rejected as a duplicate by collation semantics.

#### Scenario: Redeliver at another ingestion time
- **WHEN** equal evidence for one raw identity is redelivered after the application clock advances
- **THEN** `ingested_at` SHALL not create a second identity
- **AND** the original row and its first ingestion time SHALL remain unchanged.

### Requirement: Idempotent duplicate acceptance
The system SHALL treat a repeated raw identity with equal immutable chain and payload evidence as an idempotent success without modifying the first stored observation.

#### Scenario: Retry an unchanged observation
- **WHEN** an observation is retried with the same raw identity, block evidence, source event time, exact payload and parser version
- **THEN** the operation SHALL report that the observation already exists
- **AND** the original observation time and ingestion time SHALL remain unchanged.

### Requirement: Conflicting duplicate rejection
The system SHALL reject a repeated raw identity whose immutable chain or payload evidence differs from the stored observation.

#### Scenario: Receive conflicting evidence for one identity
- **WHEN** an existing raw identity is submitted with a different block position, block hash, source event time, exact payload or parser version
- **THEN** the operation SHALL fail with an explicit identity-conflict result
- **AND** the stored record SHALL not be overwritten or partially changed.

### Requirement: Versioned payload integrity
The system SHALL preserve the exact UTF-8 payload text and an algorithm-qualified SHA-256 digest derived by the application from those exact bytes.

#### Scenario: Read stored payload evidence
- **WHEN** a persisted observation is read back
- **THEN** its payload SHALL equal the exact submitted text
- **AND** its digest SHALL equal `sha256:` followed by the lowercase hexadecimal SHA-256 value of the submitted UTF-8 bytes.

#### Scenario: Reject an untrusted digest
- **WHEN** a caller supplies a payload without supplying a digest
- **THEN** the application SHALL derive the digest itself
- **AND** persistence SHALL not depend on a caller-provided hash value.

### Requirement: Validated and UTC-normalized evidence
The system SHALL reject incomplete, malformed, non-network-qualified or internally inconsistent observation evidence and SHALL persist authoritative times as UTC instants at PostgreSQL microsecond precision.

#### Scenario: Reject inconsistent chain identity
- **WHEN** the transaction, event or block-position evidence does not resolve to the observation chain
- **THEN** the observation SHALL be rejected before persistence
- **AND** no row SHALL be created.

#### Scenario: Persist supported timestamp precision
- **WHEN** an observation contains valid instants with finer-than-microsecond precision
- **THEN** the persisted values SHALL use a documented deterministic microsecond normalization
- **AND** duplicate comparison SHALL use that same normalized representation.

#### Scenario: Reject an invalid persisted network identity
- **WHEN** direct persistence attempts to store a malformed or non-network-qualified chain identifier
- **THEN** PostgreSQL SHALL reject the row
- **AND** no raw observation SHALL be stored.

### Requirement: Stable-inclusion admission boundary
V1 raw storage SHALL be used only for historical or finalized evidence whose chain inclusion is stable according to the supplying adapter's approved contract. The storage API SHALL NOT claim to verify finality itself, and an adapter capable of submitting provisional evidence SHALL NOT be introduced before an approved finality and reorg-handling change.

#### Scenario: Store stable historical evidence
- **WHEN** an approved caller submits historical or finalized evidence satisfying the V1 admission precondition
- **THEN** storage SHALL preserve it using the normal append-only semantics
- **AND** storage SHALL not infer or fabricate an independent finality result.

#### Scenario: Propose provisional ingestion
- **WHEN** a provider integration can submit pending, pre-confirmed or otherwise provisional evidence
- **THEN** that integration SHALL require an approved finality and reorg-handling change before using V1 storage
- **AND** the current storage contract SHALL not be presented as safe for provisional ingestion.

### Requirement: Parser-independent event locator
The canonical event locator of a blockchain event SHALL remain stable across providers, parser implementations and parser-version changes. A change to the persisted locator grammar SHALL require a separate approved change and a forward migration.

#### Scenario: Reparse one blockchain event
- **WHEN** the same raw blockchain event is processed by a new parser implementation or version
- **THEN** it SHALL retain the same canonical event locator
- **AND** parser version SHALL remain immutable provenance rather than changing event identity.

### Requirement: Raw transaction payload stored once

The system SHALL store one complete provider transaction payload per chain, transaction value and provider in `marketdata`-owned storage, with the trusted-clock received time, stable admission time, durable ingestion time and provider event time recorded as distinct facts. An equal retry SHALL resolve to the stored record without changing its recorded times, and a conflicting payload for the same identity SHALL be rejected explicitly without overwrite.

#### Scenario: Equal retry keeps first record
- **WHEN** the same provider transaction payload is stored twice with identical evidence
- **THEN** the second store SHALL resolve to the first record
- **AND** the first record's received, admitted and ingested times SHALL remain unchanged

#### Scenario: Conflicting payload is rejected
- **WHEN** a different payload is stored for an already-stored chain, transaction and provider identity
- **THEN** the store SHALL reject it explicitly without overwriting the original payload

#### Scenario: Batch insert is idempotent
- **WHEN** a bounded batch of transaction payloads containing internal duplicates and already-stored identities is persisted in one call
- **THEN** each distinct identity SHALL be stored exactly once
- **AND** every batch member SHALL resolve to a stored record without conflict

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

### Requirement: Point-in-time token universe snapshots

The system SHALL store token universe snapshots as fingerprinted immutable snapshots whose members record discovery source, eligibility rule version, inclusion time and optional exclusion time with reason. Universe membership at an instant SHALL be derivable from a snapshot: a member is included when its inclusion time is at or before the instant and its exclusion, when present, is after the instant. Snapshot members SHALL be persisted with their canonical ordinal, and an equal snapshot retry SHALL resolve to the stored snapshot without duplication.

#### Scenario: Excluded token is not a member after exclusion
- **WHEN** a universe member was excluded at a recorded time with a reason and membership is evaluated at a later instant
- **THEN** the token SHALL NOT be a member at that instant
- **AND** the exclusion record with its reason SHALL remain queryable

#### Scenario: Equal snapshot retry is idempotent
- **WHEN** the same universe snapshot is finalized twice
- **THEN** the second finalization SHALL resolve to the stored snapshot with identical fingerprint and membership

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
