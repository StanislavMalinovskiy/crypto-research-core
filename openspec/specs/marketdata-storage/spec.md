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

The system SHALL store swap-derived price observations as facts separate from normalized swaps, each carrying the chain, asset, quote asset, venue, source event identity, native price with explicit scale, trade notional in the quote asset, observation time, block position and provider lineage. Storage SHALL be idempotent per source event identity, and point-in-time queries SHALL return only observations within the requested window at or before the cutoff in a deterministic total order.

#### Scenario: Price observation retry is idempotent
- **WHEN** the same price observation is recorded twice from the same source event
- **THEN** the second record SHALL resolve to the stored observation without duplication

#### Scenario: Point-in-time window excludes later observations
- **WHEN** price observations are queried for a window ending at a cutoff and an observation was observed after that cutoff
- **THEN** the later observation SHALL NOT appear in the result
- **AND** equal-time observations SHALL be ordered by their immutable source identity

### Requirement: Liquidity observations are separate facts

The system SHALL store pool liquidity observations as facts separate from swaps and prices, each carrying the chain, asset, pool address, source event identity, liquidity value with explicit scale, observation time, block position and provider lineage, idempotent per source event identity and queryable point-in-time with deterministic ordering.

#### Scenario: Liquidity observation retry is idempotent
- **WHEN** the same liquidity observation is recorded twice from the same source event
- **THEN** the second record SHALL resolve to the stored observation without duplication

#### Scenario: Liquidity is queryable point-in-time
- **WHEN** liquidity observations are queried for an asset within a window at or before a cutoff
- **THEN** only observations inside the window SHALL be returned in deterministic order

### Requirement: USD conversion facts are immutable derivations

The system SHALL store USD conversion facts as separate records referencing the converted native price observation and the quote price observation used, with the conversion method version and computed value at explicit scale. An equal retry SHALL resolve to the stored fact, and a conflicting conversion for the same source identity SHALL be rejected without overwrite.

#### Scenario: Conversion retry is idempotent
- **WHEN** the same USD conversion is recorded twice with identical evidence
- **THEN** the second record SHALL resolve to the stored conversion fact

#### Scenario: Conflicting conversion is rejected
- **WHEN** a USD conversion with a different value is recorded for an already-stored source identity
- **THEN** the store SHALL reject it explicitly

### Requirement: Point-in-time token universe snapshots

The system SHALL store token universe snapshots as fingerprinted immutable snapshots whose members record discovery source, eligibility rule version, inclusion time and optional exclusion time with reason. Universe membership at an instant SHALL be derivable from a snapshot: a member is included when its inclusion time is at or before the instant and its exclusion, when present, is after the instant. Snapshot members SHALL be persisted with their canonical ordinal, and an equal snapshot retry SHALL resolve to the stored snapshot without duplication.

#### Scenario: Excluded token is not a member after exclusion
- **WHEN** a universe member was excluded at a recorded time with a reason and membership is evaluated at a later instant
- **THEN** the token SHALL NOT be a member at that instant
- **AND** the exclusion record with its reason SHALL remain queryable

#### Scenario: Equal snapshot retry is idempotent
- **WHEN** the same universe snapshot is finalized twice
- **THEN** the second finalization SHALL resolve to the stored snapshot with identical fingerprint and membership
