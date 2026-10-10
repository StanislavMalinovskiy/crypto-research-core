## Purpose

Persist bounded decoded PumpSwap evidence as immutable source-linked revisions with complete transaction dispositions, deterministic batch membership and explicit versioned readback, without market-data admission.

## ADDED Requirements

### Requirement: Derive only from verified retained raw evidence

The system SHALL expose a synchronous decode-and-persist boundary taking a finite recorded raw batch, derive its dispositions and operations through the supported verified decoder, and verify each source against an existing immutable raw transaction by exact chain, signature and provider. Exact UTF-8 payload, digest, slot, source time and received/admitted/ingested times SHALL match retained storage after microsecond normalization. Missing or contradictory raw evidence SHALL reject the entire batch. Caller-supplied decoded results SHALL NOT substitute for derivation. The existing pure decode boundary SHALL remain write-free.

#### Scenario: Reject invented or mismatched lineage
- **WHEN** a batch source has no retained raw row or differs in exact payload, digest, identity, slot or any original source/observation clock
- **THEN** persistence SHALL fail explicitly without publishing any batch, disposition or operation
- **AND** retained raw/capture evidence SHALL remain unchanged.

#### Scenario: Persist independently decoded evidence
- **WHEN** all recorded sources match retained evidence and the supported decoder completes successfully
- **THEN** the persisted result SHALL equal its decoded semantic content and retain decoder/schema/canonicalization versions and pinned source hashes
- **AND** no network, secret read, provider fetch or current account lookup SHALL occur.

### Requirement: Complete transaction and invocation dispositions

Each distinct submitted source SHALL have exactly one immutable transaction derivation and a batch membership reference, preserving source metadata, transaction version, status, reason, optional network fee and every invocation disposition. Each transaction derivation SHALL seal its exact ordered operation revision set and count, including zero; another publication SHALL NOT add or remove operations under that existing revision. Failed, non-operation, unsupported, invalid, no-PumpSwap and partial statuses SHALL remain addressable. Only independently confirmed operations returned by the decoder SHALL be stored as operations; failed transactions SHALL have none. Batch scope limitations SHALL remain explicit.

#### Scenario: Preserve the qualified saved window
- **WHEN** the hash-verified saved 484bec54 batch is decoded and persisted using the verified v4 decoder
- **THEN** readback SHALL reconcile all124 transaction derivations as47 DECODED,49 FAILED,9 NON_OPERATION,19 UNSUPPORTED,0 PARTIAL and68 separate operations
- **AND** all49 failed transactions SHALL have no operations, and saved collector gaps/loss/fee limitations SHALL remain visible without a completeness claim.

#### Scenario: Preserve partial and empty evidence
- **WHEN** a supported synthetic batch contains partial, invalid, no-PumpSwap or ancillary-only results
- **THEN** those transaction and invocation statuses/reasons SHALL survive exact readback, with separately confirmed partial operations retained only where the decoder permits them
- **AND** an empty bounded batch SHALL be reproducibly stored with zero members and operations.

### Requirement: Separate canonical operation identity from immutable revisions

Canonical operation identity SHALL contain exact chain, signature, complete outer/CPI instruction path and leg ordinal, independently of provider, session, decoder version, report fingerprint or limitation labels. Transaction derivation identity SHALL bind exact source lineage and decoder/schema/source-pin/storage-canonicalization versions. An operation revision SHALL bind canonical operation identity to its transaction derivation. Batch/report limitations, input order and batch grouping SHALL NOT enter transaction or operation revision identity. Content digests SHALL be separate from revision identity and cover all immutable derivation values under versioned length-prefixed SHA-256 encoding.

#### Scenario: Regroup or relabel equal input
- **WHEN** the same sources are permuted, equally duplicated, split into other bounded batches or given different report limitations
- **THEN** their transaction and operation revision identities SHALL remain equal without additional derivation rows
- **AND** changed limitations or membership SHALL produce a distinguishable immutable batch manifest.

#### Scenario: Retain another parser or source revision
- **WHEN** a later separately supported decoder/schema/source-pin version or distinct provider/session source binding derives one canonical operation
- **THEN** its distinguishable immutable revision SHALL coexist with the earlier revision under the same canonical operation identity
- **AND** exact earlier batch/revision reads SHALL remain unchanged rather than selecting the latest interpretation.

### Requirement: Atomic idempotent conflict-safe publication

One bounded batch publication SHALL commit all transaction derivations, operations, manifest and ordered membership in one short marketdata-owned transaction after pure decoding. Sequential and concurrent equal retry SHALL resolve identical durable rows and preserve first persistence times. Equality SHALL compare complete immutable fields as well as digests; same revision identity with differing content SHALL fail explicitly without overwrite. Missing raw lineage, a member/operation conflict or mid-batch storage failure SHALL roll back all new rows for that publication.

#### Scenario: Concurrent equal retries
- **WHEN** two independent database transactions publish the same bounded decoded batch concurrently
- **THEN** they SHALL return equal manifests/revisions with one durable copy of each row
- **AND** the original raw values and first derivation/batch persistence times SHALL remain unchanged.

#### Scenario: Conflict or late chunk failure
- **WHEN** publication encounters differing immutable content under an existing revision key, even if a supplied digest matches, or fails after an earlier write chunk
- **THEN** it SHALL reject the publication and roll back all its new members/derivations/operations/manifest
- **AND** previously committed batches and raw evidence SHALL remain intact.

### Requirement: Bounded exact versioned readback without admission

Writes SHALL retain the existing decoder bounds of at most128 distinct transaction sources,8,000,000 UTF-8 bytes per raw result,64,000,000 aggregate input/output bytes and10,000 confirmed operations. Derived-row writes SHALL use chunks of at most1,000 and finite transaction/query waits. Reads SHALL require an exact batch identity, return its recorded versions, ordered revision membership, complete dispositions, exact numeric/proof/fee facts and limitations, and reject corrupt or excess stored membership rather than truncate. Unknown batch identity SHALL return explicit absence. Storage SHALL use a forward-only owning migration without rewriting legacy evidence, and SHALL NOT populate normalized market facts, snapshots, signals or evaluations or claim verified realtime availability, D1/P1 admission or complete economics.

#### Scenario: Read a pinned older batch
- **WHEN** an exact batch identity is read after another source or decoder revision has been persisted
- **THEN** readback SHALL return only that batch's frozen ordered membership and recorded versions/fingerprints
- **AND** exact integer values, signed source facts, proof paths and unknown fee facts SHALL survive without floating-point conversion or new normalization.

#### Scenario: Excess work and compatibility
- **WHEN** a bound is exceeded or stored membership/content cannot be verified
- **THEN** the request SHALL fail explicitly without partial publication or truncated success
- **AND** forward migration and derived publication SHALL preserve preexisting raw/capture/recorded market/signal/evaluation rows and fingerprints.
