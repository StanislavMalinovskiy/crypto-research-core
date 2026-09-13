## Why

The current baseline already stores exact CAIP-2 network identifiers and uses network-qualified raw-observation keys, but several multi-network invariants remain implicit or incompletely exercised. Before provider adapters exist, the project needs one explicit conformance contract for Solana, Base, Ethereum and Arbitrum identities, EVM event locators, and the limits imposed by global idempotency on the first PostgreSQL schema.

## What Changes

- Strengthen the accepted `chain-identity` contract with representative Solana and EVM network identifiers while preserving exact, case-sensitive CAIP-2 values without trimming or case normalization.
- State that the kernel remains open to valid CAIP-2 networks and does not introduce `ChainFamily` or a closed network registry.
- Define the future canonical EVM event locator as the ordinal within the complete `receipt.logs` sequence rather than trusting a provider-specific or filtered RPC `logIndex` value.
- Make parser-version-independent locator stability an explicit acceptance condition for future adapters and normalizers.
- Strengthen the `marketdata-storage` contract with a synthetic EVM observation scenario, exact multi-network/provider identity behavior, and the exact physical V1 primary key.
- State explicitly that finality and canonicality are admission/provenance concerns, not identity components, and that V1 accepts only stable input without determining finality itself.
- Keep V1 unpartitioned: `ingested_at` remains outside the primary key so redelivery at a different ingestion time cannot bypass idempotency; no surrogate identifier, BRIN index or secondary index is introduced.
- Make database identity equality independent of the database default collation by applying deterministic bytewise `C` collation to every textual primary-key column.
- Add conformance tests and documentation checks for guarantees not already covered by the implemented baseline. Existing compliant Java, SQL and JDBC behavior is verified rather than rewritten.

Non-goals: Helius or other provider integrations, WebSocket ingestion, normalization, swaps, finality history, reorg handling, partitioning, new dependencies, module or DAG changes, and signal, evaluation, risk or wallet business logic.

Affected modules: `kernel` and `marketdata` only.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `chain-identity`: make the open CAIP-2 network model and canonical EVM transaction-event locator rule explicitly testable.
- `marketdata-storage`: make generic EVM persistence, stable-input identity exclusions, exact bytewise key equality and the unpartitioned global-idempotency key explicitly testable.

## Impact

- Planning and later conformance work affects `kernel::api` identity tests, market-data PostgreSQL integration tests and active architecture/module documentation.
- V1 may be corrected directly only because it has not been published or applied to a shared/non-disposable environment; no V2 migration is created.
- The existing six-module topology, allowed dependency DAG, synchronous APIs, transaction boundary and production dependency set remain unchanged.
