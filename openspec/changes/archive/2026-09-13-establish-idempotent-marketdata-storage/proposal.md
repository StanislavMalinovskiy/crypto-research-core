## Why

The next vertical slices need a durable, replayable source of provider observations before normalization can be trusted. The repository currently has chain-aware Java values but its logical chain slug cannot distinguish blockchain networks, so the first durable identity must adopt network-qualified CAIP-2 before any data is shared or a provider adapter exists.

## What Changes

- Add the first `marketdata` Flyway migration, creating the module schema and one append-only raw-chain-observation table.
- **BREAKING** Replace logical lowercase chain slugs with case-sensitive customary CAIP-2 network identifiers and expose the canonical Solana Mainnet identity from `kernel::api`.
- **BREAKING** Name the durable identity and block-evidence columns `chain_id`, `transaction_value`, `event_locator`, `observed_block_position` and `observed_block_hash`, removing the earlier ambiguous column names before V1 is shared.
- Define and implement an internal market-data application boundary that stores immutable provider observations using the existing kernel identities.
- Make repeated delivery idempotent: the same raw identity and content is accepted once, while conflicting content for an existing identity is rejected without overwriting evidence.
- Persist ordered block position separately from optional block hash and retain source time, observation time, provider, payload, payload digest, parser version and ingestion time as replay evidence.
- Restrict the V1 admission contract to stable-inclusion historical/finalized evidence without pretending that the storage API verifies finality, and forbid provisional adapters until a finality/reorg change is approved.
- Make the transaction-scoped event locator parser-independent and require a separate approved change plus migration for any persisted locator-grammar change.
- Verify schema ownership, constraints, duplicate behavior, conflict behavior and migration startup against PostgreSQL 18.6 with Testcontainers.
- Update active documentation from the empty database baseline to the first owned market-data object.

Non-goals: provider HTTP/RPC integration, finality verification, reorg handling, Solana/EVM locator calculators, payload normalization, normalized swaps, public REST endpoints, signal or evaluation behavior, partitioning, speculative indexes, cross-module read models, background workers, additional infrastructure, new production dependencies, or changes to the six-module DAG.

Affected modules: `kernel` and `marketdata`; the public kernel identity contract changes before downstream business functionality or durable shared data exists.

## Capabilities

### New Capabilities

- `marketdata-storage`: Append-only raw provider-observation persistence, identity, replay evidence and idempotent conflict semantics owned by `marketdata`.

### Modified Capabilities

- `chain-identity`: Replace logical chain slugs with exact, case-sensitive CAIP-2 network identity and stabilize the persisted transaction event locator contract.
- `database-bootstrap`: Replace the deliberately empty Flyway baseline with the first real module-owned migration and verify its applied state.

## Impact

- Updates `kernel::api` chain validation/constants and its focused identity tests.
- Adds market-data application/domain/infrastructure persistence code and PostgreSQL integration tests inside `io.cryptoresearch.marketdata`.
- Adds the first versioned SQL migration under `db/migration/marketdata/` and updates Flyway verification expectations.
- Updates database, market-data and testing documentation while preserving one Maven module, one JAR and one PostgreSQL database.
- Uses existing Spring JDBC, Flyway, PostgreSQL and Testcontainers dependencies; no dependency, runtime-role or deployment changes are introduced.
