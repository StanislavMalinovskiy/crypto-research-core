## ADDED Requirements

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

## MODIFIED Requirements

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
