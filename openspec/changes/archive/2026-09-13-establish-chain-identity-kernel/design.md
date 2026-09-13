## Context

See [proposal.md](proposal.md). The six-module DAG already exposes `kernel::api` to every business module, but that API currently contains only a package descriptor. The first fixture-based market-data flow will need identities before provider payloads, persistence keys and signal evidence can be modeled consistently.

The implementation must obey the stable Java 25 and reproducibility contracts, keep `kernel` dependency-free, and avoid deciding Solana decoding or database representation prematurely.

## Goals / Non-Goals

**Goals:**

- Provide the smallest strongly typed identity vocabulary needed by future module APIs and the first market-data schema.
- Prevent accidental equality across chains and accidental substitution across identity categories.
- Supply an explicit platform-independent technical order suitable for reproducible tie-breaks.
- Keep construction invariants local, immutable and testable without Spring.

**Non-Goals:**

- Validate base58, checksum, length or other chain-specific syntax.
- Model token quantities, decimals, prices, block hashes, provider payloads or chain-specific event-locator semantics.
- Define persistence converters, JSON formats, database columns, REST contracts or a runtime chain registry.
- Introduce a shared service, generic public identifier hierarchy or ownership of durable data.

## Decisions

### 1. Use ordinary Java records in `kernel::api`

Add `ChainId`, `AssetId`, `WalletAddress`, `TransactionId`, `EventId` and `BlockPosition` as public records under `io.cryptoresearch.kernel.api`. Records provide immutable state and value equality without Lombok, Spring annotations or framework coupling.

`AssetId`, `WalletAddress` and `TransactionId` contain `ChainId chain` and an opaque `String value`. `EventId` contains a non-null `TransactionId transactionId` and an opaque `String value` locator, so chain and transaction context cannot be omitted. `BlockPosition` contains `ChainId chain` and a non-negative `long value`.

Separate types are preferred over one `ChainScopedId` carrying a category enum because Java signatures then prevent passing a wallet where an asset is required. A public common marker interface is rejected: it adds no current behavior and would encourage APIs to erase the useful category distinction.

### 2. Represent chains as an extensible canonical slug

`ChainId` wraps a strict lowercase ASCII slug matching `[a-z][a-z0-9-]*` and exposes a `SOLANA` constant. Construction validates rather than silently lowercasing or trimming input. A record is preferred over an enum so a future chain can be represented without changing a closed kernel enumeration; a runtime registry is unnecessary because support policy belongs to future feature changes.

The slug is a stable logical identity, not a CAIP identifier or provider name. Adopting a broader external naming standard is deferred until a real multi-chain integration demonstrates the need.

### 3. Preserve local identities as validated opaque strings

Asset, wallet, transaction and event-locator values are non-null, non-blank, have no leading/trailing whitespace and contain no ISO control characters. Otherwise their exact case and content are preserved. Every chain-bearing type rejects a null chain explicitly. A package-private helper may centralize the repeated invariant without becoming public API.

Chain-specific adapters remain responsible for base58/checksum/length validation. Kernel-level decoding was rejected because it would either depend on chain libraries or make a supposedly generic shared module change with every provider rule.

### 4. Model block position, block height and block hash separately

`BlockPosition` means the ordered position used to locate a block: Solana slot or EVM block number. It is not a generic string reference and it does not represent Solana `blockHeight` or a block hash. The value is a non-negative Java `long` ordered numerically within its chain; a chain that requires a wider numeric range needs a later explicit compatibility change.

Solana documents `getBlock` as taking a slot while returning `blockHeight`, `blockhash` and `parentSlot` separately; confirmed transaction responses also carry their containing `slot`. Therefore future market-data naming uses `block_position` for the slot/block number and a separate nullable `block_hash`. See the official [Solana network RPC reference](https://solana.com/docs/tools/surfpool/rpc/network) and [RPC JSON structures](https://solana.com/docs/rpc/json-structures).

An opaque `BlockReference(String)` was rejected because it would permit slot, height and hash to be stored under the same semantic name.

### 5. Define equality and ordering only over identity evidence

Record equality and hashing include the complete record category and identity evidence. Each type implements `Comparable` of its own category. `ChainId` orders by its ASCII slug; opaque chain-scoped values order by chain and then `String.compareTo`; `EventId` orders by transaction and then locator; `BlockPosition` orders by chain and then numeric value.

This order is a technical reproducibility order, not market, chronological or chain-native block order. Locale-aware comparison and record `toString()` are not canonical serialization and must not be used for dataset fingerprints. Canonical persistence and fingerprint encoding remain owned by their introducing changes.

### 6. Constrain the first market-data persistence identity without adding DDL

The later `establish-idempotent-marketdata-storage` change owns the migration and physical SQL types. Its raw provider-observation model must include the conceptual fields `chain`, `transaction_id`, `event_id`, `block_position`, nullable `block_hash`, nullable `source_event_time`, `observed_at`, `provider`, `payload`, `payload_hash`, `parser_version` and `ingested_at`.

Raw idempotency uses `(chain, transaction_id, event_id, provider)` because providers are independently observed sources. A normalized blockchain event or swap uses `(chain, transaction_id, event_id)` because two providers can report the same chain fact; provider remains lineage. Whether the physical table repeats the chain column already carried by `TransactionId` is a persistence mapping decision, but it must enforce consistency rather than accept contradictory chain values.

This change records the semantic identity contract only. Column types, JSON representation, indexes, foreign keys, schema migration and parser behavior remain tasks of the storage change.

### 7. Keep the change transaction-free and dependency-free

The records contain no persistence annotations or Spring components. `kernel` continues to own no schema, tables, repositories, application use cases or transactions. No production dependency, Flyway migration, configuration property or module edge changes.

### 8. Verify contract and architecture together

Focused unit tests cover accepted/rejected chain slugs, explicit null-chain rejection, opaque-value preservation, category separation, event composition, non-negative/numeric block positions, same-chain equality, cross-chain distinction, hashing and deterministic sorting. Existing repository tests continue to compile all six public API roots without preview, assert the exact module map and run `ApplicationModules.verify()`.

## Risks / Trade-offs

- **[Opaque validation accepts a chain-invalid value]** → The first owning adapter validates chain syntax before constructing an identity; kernel protects shared semantics, not provider wire rules.
- **[Public names become expensive to rename]** → Use the storage-oriented vocabulary already required by the first walking skeleton and keep the initial surface to six records.
- **[Technical ordering is mistaken for domain ordering]** → Document it explicitly as a reproducibility tie-break only; block chronology remains a future domain decision.
- **[A future identity standard needs a different external encoding]** → Add versioned adapters/serialization at the owning boundary without changing equality evidence silently.

## Migration Plan

1. Add the six records and internal validation helper to `kernel::api`.
2. Add focused unit tests and rerun architecture/public-API guards.
3. Update active kernel, architecture, glossary and testing documentation.
4. Run skip-IT and Docker-backed Maven verification, strict OpenSpec validation, then sync and archive.

Rollback removes the new records, tests and documentation references. No consumers or persisted data exist in this change, so no data or compatibility migration is required.
