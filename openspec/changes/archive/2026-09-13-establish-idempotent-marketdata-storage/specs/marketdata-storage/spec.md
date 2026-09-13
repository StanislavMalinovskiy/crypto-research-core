## Purpose

Provides an append-only, module-owned record of raw provider observations so later normalization and replay operate on durable, identity-safe evidence.

## ADDED Requirements

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
The system SHALL identify a raw observation by the complete tuple of exact CAIP-2 network identity, opaque transaction value, canonical transaction-scoped event locator and provider.

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
