## MODIFIED Requirements

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
