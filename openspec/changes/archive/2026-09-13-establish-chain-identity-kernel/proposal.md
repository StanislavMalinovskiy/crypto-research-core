## Why

The first market-data walking skeleton needs stable identifiers that cannot accidentally equate the same opaque address or transaction value across different chains. Defining the minimal identity contract in `kernel::api` now prevents every later module from inventing incompatible wrappers while keeping chain-specific parsing and business behavior out of the shared kernel.

## What Changes

- Introduce immutable public identity values for chains, assets, wallets, transactions, transaction events and ordered block positions.
- Define canonical chain identifiers and strict non-blank opaque identifier handling without embedding provider SDK or persistence types.
- Make equality, ordering and opaque-value preservation deterministic and safe for use in public module contracts and reproducible evidence.
- Define the identity shape that the first market-data persistence change must use for raw provider observations and provider-independent normalized events, without creating DDL here.
- Add focused unit and architecture tests for construction, invalid input, cross-chain distinction and stable public-API compatibility.
- Update the kernel, architecture, glossary and testing documentation with the implemented identity vocabulary.

## Capabilities

### New Capabilities

- `chain-identity`: Defines the stable chain-aware identity values shared by the six module APIs.

### Modified Capabilities

None.

## Impact

- **Affected module:** `kernel` only; future modules may consume these values through the existing `kernel::api` edge, but no consumer is implemented by this change.
- **Affected code:** new ordinary Java records/value types in `io.cryptoresearch.kernel.api`, focused unit tests, and concise architecture/module/glossary/test documentation updates.
- **Dependencies and infrastructure:** none added or changed; no Spring annotations, provider SDKs, persistence APIs, tables, Flyway migrations or configuration.
- **Non-goals:** no Solana RPC/base58/checksum validation, token metadata, amounts/prices, persisted raw events, signals, provider adapters, cross-chain conversion, chain registry service, business use cases, tables, migrations or changes to the six-module DAG.
