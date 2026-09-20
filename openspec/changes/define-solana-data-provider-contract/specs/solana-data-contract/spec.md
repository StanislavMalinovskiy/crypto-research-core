# Spec Delta

## Purpose

Defines the normative Solana data contract that every real Solana ingestion, normalization and storage implementation in this repository must satisfy before mass real-data recording begins: finality, identity, time, universe, derivation, quality, research ranges, capacity and provider-selection rules.

## ADDED Requirements

### Requirement: Finalized-only stable admission

The system SHALL admit raw Solana evidence into stable domain storage only after the evidence has reached the `finalized` commitment level at the trusted adapter boundary. Provisional (`processed` or `confirmed`) evidence SHALL NOT enter stable domain storage; a provisional transport journal requires its own approved change.

#### Scenario: Confirmed evidence is not admitted
- **WHEN** a Solana provider adapter receives transaction evidence whose slot has not reached `finalized` commitment
- **THEN** the evidence SHALL NOT be written to stable domain storage
- **AND** no normalized fact, price observation or liquidity observation SHALL be derived from it

#### Scenario: Finalized evidence is admissible
- **WHEN** Solana evidence is confirmed to be in a `finalized` slot
- **THEN** the evidence SHALL become admissible to stable storage with its identity, payload and lineage intact

### Requirement: Parser-independent Solana event locator

The system SHALL identify every raw Solana observation by chain identity, transaction signature value and a canonical event locator derived from the transaction's complete instruction structure: outer instruction index, inner-instruction stack path and, for multi-leg operations, the leg ordinal within that instruction. The locator SHALL NOT depend on the provider, parser implementation or parser version, and changing a persisted locator grammar SHALL require an approved forward migration.

#### Scenario: Same event keeps identity across providers
- **WHEN** the same blockchain swap event is delivered by two different providers or parsed by two parser versions
- **THEN** both raw observations SHALL carry the same canonical event locator
- **AND** the raw observations remain distinguishable by provider while the normalized fact does not

#### Scenario: Filtered position is never identity
- **WHEN** an event position arrives as a provider-specific filter result index
- **THEN** it SHALL NOT be used as or derived into the canonical event locator

### Requirement: Trusted observation time

The system SHALL record system observation time from the trusted application UTC clock at the adapter boundary and SHALL keep chain slot time, provider event time, provider-visible time, system received time, stable admission time and durable ingestion time as distinct recorded facts. Modeled availability time for historical replay SHALL be a versioned model and SHALL NOT replace the actual live observation time.

#### Scenario: Provider timestamp is not observation time
- **WHEN** a provider payload contains its own event timestamp
- **THEN** the system observation time SHALL be recorded from the trusted application clock
- **AND** the provider timestamp SHALL be retained as lineage with its source identified

#### Scenario: Backfill does not fabricate live freshness
- **WHEN** historical evidence is ingested during backfill
- **THEN** its actual ingestion time SHALL be recorded separately from its versioned modeled availability time
- **AND** point-in-time computations SHALL use the availability semantics declared by the active research protocol

### Requirement: Point-in-time token universe

The system SHALL determine token universe membership only from facts available at the inclusion moment, recording discovery source, eligibility rule, inclusion time and every exclusion with its time and reason. Universe construction SHALL NOT use current survival status, current liquidity or any other future knowledge.

#### Scenario: Dead token remains in universe
- **WHEN** a token included in the universe at its inclusion moment later dies or loses liquidity
- **THEN** the token SHALL remain in the universe with its exclusion recorded
- **AND** its outcomes SHALL remain measurable in research results

#### Scenario: Future knowledge cannot select tokens
- **WHEN** a universe snapshot is built for a historical evaluation range
- **THEN** membership SHALL be derivable exclusively from evidence dated at or before each token's inclusion moment

### Requirement: Separated derivation facts

The system SHALL store raw transaction evidence, chain-native swap quantities, token decimals, native price observations, USD conversion facts and pool/venue liquidity observations as separate recorded facts, each with source, observation time, quality and confidence. Missing inputs SHALL produce an explicit quality status rather than a fabricated value.

#### Scenario: Swap without admissible price
- **WHEN** a normalized swap has chain-native quantities but no admissible USD price observation
- **THEN** the swap fact SHALL be stored with its native quantities
- **AND** an explicit missing-price status SHALL be recorded instead of a fallback USD value

#### Scenario: Liquidity is a distinct observation
- **WHEN** a liquidity value is available for a pool at an observation time
- **THEN** it SHALL be stored as a liquidity observation with its own source and time
- **AND** it SHALL NOT be embedded invisibly into the swap fact as universal truth

### Requirement: Price and liquidity quality policy

The system SHALL apply a versioned price and liquidity quality policy before a market observation becomes admissible for signal and evaluation use. The policy SHALL define minimum trade notional, pool depth requirements, outlier handling, suspicious-trade indicators and cross-source consistency rules. A single swap SHALL NOT be treated as an automatically executable market price, and quality degradation SHALL lower confidence or block admissibility rather than fabricate replacements.

#### Scenario: Dust trade is not a market price
- **WHEN** a swap observation carries a notional below the policy minimum
- **THEN** it SHALL NOT be admissible as a signal-grade or evaluation-grade price observation

#### Scenario: Suspicious observation degrades confidence
- **WHEN** an observation matches a suspicious pattern defined by the policy version
- **THEN** its confidence SHALL be degraded or its admissibility blocked according to that policy version
- **AND** the reason SHALL be recorded with the observation

### Requirement: Historical research ranges

The system SHALL define warm-up, exploratory and evaluation date ranges before wallet analytics and signal evaluation run on real data. Wallet metrics for any signal SHALL be computable entirely from evidence completed before the evaluation window of that signal, and the total historical envelope SHALL satisfy this separation for the declared wallet lookback.

#### Scenario: Warm-up precedes evaluation
- **WHEN** a signal is detected inside the evaluation range
- **THEN** the wallet score used at its decision moment SHALL be derived only from trades completed before the evaluation range begins

#### Scenario: Range declarations are explicit
- **WHEN** research or evaluation runs on real data
- **THEN** the active warm-up, exploratory and evaluation ranges SHALL be declared and versioned with the run provenance

### Requirement: Capacity envelope and recoverability classes

The system SHALL document a measured or estimated capacity envelope (events per day, payload bytes per day, retention, backfill size and query patterns) before mass real-data recording, and SHALL classify persisted data into recoverability classes: provider-reloadable raw evidence, re-derivable normalized facts and irreplaceable research artifacts. Backup and retention policy SHALL be selected per class with declared recovery objectives rather than one uniform mode.

#### Scenario: Irreplaceable artifacts are protected first
- **WHEN** a backup policy is defined for the research database
- **THEN** dataset fingerprints, run manifests, risk and wallet history, outcomes and reports SHALL be classified as irreplaceable and protected independently of raw provider evidence retention

#### Scenario: Reloadability is proven, not assumed
- **WHEN** raw evidence is classified as provider-reloadable
- **THEN** the provider's retention, replay window, cost and terms for that data SHALL be documented as evidence before the classification is relied upon

### Requirement: Evidence-based provider selection

The system SHALL select a primary Solana transport and history source only after the provider-consumer capability matrix is complete and capability-specific spikes verify the required capabilities on real samples. Documentation claims and tiers without the required capability SHALL NOT count as verification, and a missing capability SHALL never be compensated with fabricated data.

#### Scenario: Selection requires spike evidence
- **WHEN** a provider is proposed as the primary transport or history source
- **THEN** the proposal SHALL reference spike results covering the required capabilities on real samples

#### Scenario: Unverified capability stays unverified
- **WHEN** a candidate cannot demonstrate a required capability within its available tier
- **THEN** the capability SHALL be marked unverified for that tier
- **AND** the selection SHALL either use another source for that capability or leave the capability unimplemented
