## Purpose

Defines stable chain-aware identities that module contracts can exchange without confusing equal-looking values from different chains or identity categories.

## ADDED Requirements

### Requirement: Canonical chain identity
The system SHALL represent a chain with a non-empty canonical lowercase ASCII identifier whose equality and ordering are deterministic.

#### Scenario: Accept a canonical chain identifier
- **WHEN** a chain identifier starts with a lowercase ASCII letter and contains only lowercase ASCII letters, digits or hyphens
- **THEN** the identifier SHALL be accepted without changing its value
- **AND** independently constructed instances with the same value SHALL compare equal.

#### Scenario: Reject a non-canonical chain identifier
- **WHEN** a chain identifier is null, blank, contains surrounding whitespace, uppercase letters or unsupported characters
- **THEN** construction SHALL fail immediately
- **AND** no partially valid identity SHALL be returned.

### Requirement: Chain-scoped identity categories
The system SHALL expose distinct chain-scoped identities for assets, wallets and transactions, each composed of a non-null chain identity and an opaque chain-local value.

#### Scenario: Preserve an opaque local value
- **WHEN** a valid chain-scoped identity is created with a non-blank local value
- **THEN** the chain and local value SHALL be available unchanged
- **AND** the kernel SHALL not apply provider-specific decoding, checksum or address rules.

#### Scenario: Reject an invalid local value
- **WHEN** a chain-scoped identity receives a null, blank, control-character-bearing or surrounding-whitespace local value
- **THEN** construction SHALL fail immediately
- **AND** the invalid value SHALL not be normalized silently.

### Requirement: Cross-chain distinction
Chain-scoped identity equality SHALL include both the chain and the exact chain-local value.

#### Scenario: Equal local values on different chains
- **WHEN** two identities of the same category contain the same local value but different chain identities
- **THEN** they SHALL compare as different identities
- **AND** using them as set or map keys SHALL retain both entries.

#### Scenario: Equal identities on the same chain
- **WHEN** two identities of the same category contain equal chain and local values
- **THEN** they SHALL compare equal
- **AND** their hash values SHALL be equal.

### Requirement: Identity category separation
Asset, wallet and transaction identities SHALL remain distinct public contract types even when their chain and local values are identical.

#### Scenario: Exchange an identity through a module API
- **WHEN** a public module contract declares one identity category
- **THEN** a value from another identity category SHALL not be substitutable implicitly
- **AND** no generic string or provider-specific type SHALL be required at the module boundary.

### Requirement: Transaction event identity
The system SHALL identify an event within a transaction by combining a non-null transaction identity with a non-blank opaque event locator.

#### Scenario: Distinguish events in one transaction
- **WHEN** two events have the same transaction identity and different event locators
- **THEN** they SHALL compare as different event identities
- **AND** the kernel SHALL preserve each locator without assigning provider-specific meaning to it.

#### Scenario: Distinguish matching locators across transactions
- **WHEN** the same event locator occurs under different transaction identities or chains
- **THEN** the resulting event identities SHALL remain distinct
- **AND** no nullable or implicit chain context SHALL participate in equality.

### Requirement: Ordered block position
The system SHALL represent a block position as a non-null chain identity plus a non-negative integer position with numeric ordering within that chain.

#### Scenario: Represent a Solana or EVM position
- **WHEN** a Solana slot or EVM block number is within the supported non-negative range
- **THEN** it SHALL be preserved as the block position value
- **AND** a negative position or null chain SHALL fail construction immediately.

#### Scenario: Keep block position and block hash distinct
- **WHEN** market data records both an ordered block position and a block hash
- **THEN** the position SHALL represent Solana slot or EVM block number
- **AND** the block hash SHALL remain separate optional evidence rather than being encoded into the position.

### Requirement: Deterministic technical ordering
Each identity category SHALL provide a total technical order: chain-scoped opaque identities by chain then exact local value, event identities by transaction then locator, and block positions by chain then numeric position.

#### Scenario: Order identities for reproducible output
- **WHEN** identities of one category are sorted after arriving in different iteration or completion orders
- **THEN** they SHALL produce the same order from their complete identity evidence
- **AND** the ordering SHALL not depend on locale, provider behavior or process scheduling.

### Requirement: Market-data persistence identity shape
Future market-data persistence SHALL distinguish raw provider observations from provider-independent normalized blockchain events without redefining kernel identities.

#### Scenario: Persist a raw provider observation
- **WHEN** a provider reports an event within a transaction
- **THEN** the raw observation identity SHALL include chain, transaction identity, event identity and provider
- **AND** its block position and optional block hash SHALL remain separate fields.

#### Scenario: Normalize reports from multiple providers
- **WHEN** two providers report the same blockchain event
- **THEN** the normalized event identity SHALL use chain, transaction identity and event identity without provider
- **AND** provider identity SHALL remain provenance rather than changing the normalized blockchain identity.
