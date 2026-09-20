# Spec Delta

## ADDED Requirements

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
