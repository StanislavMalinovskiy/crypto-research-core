# Chain Identity Specification

## Purpose

Defines stable chain-aware identities that module contracts can exchange without confusing equal-looking values from different chains or identity categories.

## Requirements

### Requirement: Canonical chain identity
The system SHALL represent a blockchain network by its exact, case-sensitive CAIP-2 identifier in customary representation: a 3-8 character lowercase ASCII namespace, a colon, and a 1-32 character ASCII reference from the CAIP-2 character set. The public kernel contract SHALL expose Solana Mainnet as `solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp` through `SOLANA_MAINNET`, SHALL preserve accepted identifiers without trimming or case normalization, and SHALL remain open to every syntactically valid CAIP-2 network without a closed family or network registry.

#### Scenario: Accept a canonical chain identifier
- **WHEN** `solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp`, `eip155:1`, `eip155:8453` or `eip155:42161` is supplied as a chain identifier
- **THEN** the identifier SHALL be accepted without changing its value
- **AND** independently constructed instances with the same exact value SHALL compare equal.

#### Scenario: Preserve case-sensitive reference identity
- **WHEN** two syntactically valid CAIP-2 identifiers differ only by case in their reference component
- **THEN** both identifiers SHALL preserve their supplied representation
- **AND** they SHALL compare as different network identities.

#### Scenario: Reject a non-canonical chain identifier
- **WHEN** an identifier is null, blank, has no namespace separator, has an invalid namespace or reference, exceeds the CAIP-2 component bounds, or contains surrounding whitespace
- **THEN** construction SHALL fail immediately
- **AND** no partially valid, trimmed or case-normalized identity SHALL be returned.

#### Scenario: Accept a network not known at build time
- **WHEN** a syntactically valid CAIP-2 identifier names a network for which the application declares no constant
- **THEN** the identifier SHALL still be accepted as a network identity
- **AND** construction SHALL not require a chain-family type or closed enumeration.

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
Chain-scoped identity equality SHALL include both the exact CAIP-2 network identity and the exact chain-local value.

#### Scenario: Equal local values on different chains
- **WHEN** two identities of the same category contain the same local value on Solana Mainnet and Base Mainnet
- **THEN** they SHALL compare as different identities
- **AND** using them as set or map keys SHALL retain both entries.

#### Scenario: Equal identities on the same chain
- **WHEN** two identities of the same category contain equal network and local values
- **THEN** they SHALL compare equal
- **AND** their hash values SHALL be equal.

### Requirement: Identity category separation
Asset, wallet and transaction identities SHALL remain distinct public contract types even when their chain and local values are identical.

#### Scenario: Exchange an identity through a module API
- **WHEN** a public module contract declares one identity category
- **THEN** a value from another identity category SHALL not be substitutable implicitly
- **AND** no generic string or provider-specific type SHALL be required at the module boundary.

### Requirement: Transaction event identity
The system SHALL identify an event within a transaction by combining a non-null transaction identity with a non-blank opaque canonical event locator. The locator SHALL be stable for the same blockchain event independently of provider, parser implementation and parser version. For EVM receipt logs, the canonical locator SHALL derive from the log's ordinal within the complete `receipt.logs` sequence rather than directly trusting an RPC `logIndex` field or an ordinal in a filtered result.

#### Scenario: Distinguish events in one transaction
- **WHEN** two events have the same transaction identity and different event locators
- **THEN** they SHALL compare as different event identities
- **AND** the kernel SHALL preserve each locator without assigning provider-specific meaning to it.

#### Scenario: Distinguish matching locators across transactions
- **WHEN** the same event locator occurs under different transaction identities or networks
- **THEN** the resulting event identities SHALL remain distinct
- **AND** no nullable or implicit network context SHALL participate in equality.

#### Scenario: Preserve one locator across parser versions
- **WHEN** the same blockchain event is interpreted by different parser implementations or parser versions
- **THEN** every interpretation SHALL use the same canonical event locator
- **AND** parser version SHALL remain provenance rather than becoming event identity.

#### Scenario: Locate an EVM receipt log
- **WHEN** an EVM transaction receipt contains an ordered `logs` sequence and a provider also supplies an RPC `logIndex`
- **THEN** the canonical event locator SHALL use the log's ordinal in the complete receipt sequence
- **AND** it SHALL not use a provider-filtered position or trust the RPC field as identity without deriving the canonical ordinal.

#### Scenario: Change the persisted locator grammar
- **WHEN** a proposed implementation cannot preserve the current canonical locator grammar
- **THEN** it SHALL require a separate approved change and forward database migration before writing data
- **AND** existing persisted event identities SHALL not be silently reinterpreted.

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
Market-data persistence SHALL distinguish raw provider observations from provider-independent normalized blockchain events without redefining kernel identities. Durable raw identity SHALL flatten the exact CAIP-2 network identity, opaque transaction value, canonical transaction-scoped event locator and provider into explicit fields.

#### Scenario: Persist a raw provider observation
- **WHEN** a provider reports an event within a transaction on a CAIP-2-qualified network
- **THEN** the raw observation identity SHALL include `chain_id`, `transaction_value`, `event_locator` and `provider`
- **AND** its observed block position and optional observed block hash SHALL remain separate evidence fields.

#### Scenario: Normalize reports from multiple providers
- **WHEN** two providers report the same blockchain event
- **THEN** the normalized event identity SHALL use network, transaction value and canonical event locator without provider
- **AND** provider identity SHALL remain provenance rather than changing the normalized blockchain identity.
