## Context

See [proposal.md](proposal.md) for motivation. The current application starts PostgreSQL 18.6 with Flyway but has no shared migration or durable business data. `marketdata` is the sole owner of raw provider observations and may depend only on `kernel::api`; ADR 0004 requires its first real object to introduce the `marketdata` schema, ADR 0005 puts the transaction on the application use case, and the reproducibility contract requires this first persistence change to select a payload fingerprint contract. Because V1 persists chain identity, this change also corrects `kernel::api` from a logical chain slug to exact CAIP-2 network identity before that contract is consumed by business functionality.

The change has no provider adapter yet. Tests and the later fixture walking skeleton submit recorded raw observation values directly through the internal application boundary.

## Goals / Non-Goals

**Goals:**

- Establish one forward-only migration and one append-only raw observation table owned entirely by `marketdata`.
- Make redelivery and conflicting evidence deterministic under concurrent PostgreSQL transactions.
- Preserve sufficient exact input, lineage and time evidence for later normalization and replay.
- Keep the storage boundary independent of a particular provider SDK or transport.
- Establish network-qualified identity, stable-inclusion admission and parser-independent locator invariants before provider adapters exist.

**Non-Goals:**

- Parse, normalize or interpret provider payloads.
- Store normalized swaps, prices, dataset snapshots, signals or outcomes.
- Expose raw observations through `marketdata::api` or HTTP.
- Select partitioning, retention, secondary indexes, batch ingestion, claiming or worker policies before workload evidence exists.
- Verify provider finality, handle reorganizations, or implement Solana/EVM locator calculators.

## Decisions

### 1. One first migration creates the module schema and raw table

`db/migration/marketdata/V1__create_marketdata_raw_chain_events.sql` creates `marketdata` and `marketdata.raw_chain_events`. Flyway keeps one database-wide version history; the module directory expresses ownership, not a second Flyway instance.

The table uses this physical contract:

| Column | PostgreSQL shape | Meaning |
|---|---|---|
| `chain_id` | `VARCHAR(41)`, not null | Exact customary CAIP-2 network identity |
| `transaction_value` | `VARCHAR(512)`, not null | Opaque network-local transaction value |
| `event_locator` | `VARCHAR(256)`, not null | Canonical opaque locator within the transaction |
| `provider` | `VARCHAR(64)`, not null | Canonical source identifier |
| `observed_block_position` | `BIGINT`, not null, non-negative check | Observed Solana slot or EVM block number |
| `observed_block_hash` | `VARCHAR(256)`, nullable | Independent observed chain evidence when available |
| `source_event_time` | `TIMESTAMPTZ(6)`, nullable | Provider/chain event instant when known |
| `observed_at` | `TIMESTAMPTZ(6)`, not null | First observation instant supplied at the boundary |
| `payload` | `TEXT`, not null | Exact submitted JSON text, without database canonicalization |
| `payload_hash` | `VARCHAR(71)`, not null, format check | `sha256:` plus 64 lowercase hex characters |
| `parser_version` | `VARCHAR(128)`, not null | Version of the provider payload contract used at ingestion |
| `ingested_at` | `TIMESTAMPTZ(6)`, not null | First successful persistence instant from the application clock |

The composite primary key is `(chain_id, transaction_value, event_locator, provider)`. It is also the only index in this change: identity lookup is the demonstrated query pattern, and partitioning or speculative secondary indexes have no evidence yet. `chain_id` is ASCII by CAIP-2 grammar and has the standard maximum of 41 characters/bytes. Java and SQL enforce UTF-8 byte budgets of 512, 256 and 64 bytes for the other key components. The complete variable key payload is therefore bounded to 873 bytes, avoiding a mismatch where a valid multibyte Java value exceeds PostgreSQL's B-tree index-tuple capacity. The migration uses neither `IF NOT EXISTS` nor a placeholder table because Flyway version history is the rerun mechanism.

PostgreSQL requires every partition-key column to participate in each unique or primary-key constraint on a partitioned table. Partitioning `raw_chain_events` by `RANGE (ingested_at)` would therefore force `ingested_at` into the uniqueness constraint. Because retries can have different ingestion times, that shape would break the current global duplicate identity `(chain_id, transaction_value, event_locator, provider)`. Partitioning remains deferred until an approved change defines a separate global-deduplication model that preserves this identity rather than weakening it.

Alternatives considered:

- A `public`-schema table hides ownership and contradicts ADR 0004.
- A synthetic numeric key would still require the natural unique constraint and would not improve the current access pattern.
- `JSONB` is useful for field queries but does not preserve the exact submitted text. Queryable parsed projections belong to normalization, so the raw evidence stays `TEXT`.

### 2. Kernel identity is network-qualified with exact CAIP-2 semantics

`ChainId` stores customary CAIP-2 text exactly: namespace `[-a-z0-9]{3,8}`, one colon, and reference `[-_a-zA-Z0-9]{1,32}`. The namespace remains lowercase while the reference is case-sensitive and is never normalized. `SOLANA_MAINNET` is `solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp`; Base Mainnet is representable as `eip155:8453`. This makes network identity explicit rather than treating all Solana or EVM networks as one logical chain.

The application input composes `TransactionId`, transaction-scoped `EventId` and `BlockPosition` from `kernel::api`, plus provider and payload evidence. Construction rejects a mismatch between the top-level network and any nested identity network. Persistence flattens these values to the explicit `chain_id`, `transaction_value` and `event_locator` columns; it does not introduce duplicate domain identity classes or nullable implicit network context.

Provider and parser identifiers are small validated internal values: non-null, non-blank, no surrounding whitespace or ISO control characters. Optional block hash follows the same opaque-value rules when present. Exact maximum lengths are UTF-8 byte budgets enforced consistently in Java and SQL and documented with the migration; PostgreSQL `VARCHAR` bounds remain an additional character limit.

Alternatives considered: a lowercase logical slug cannot distinguish mainnet, testnet or devnet; normalizing the CAIP-2 reference would corrupt identifiers such as Solana's case-sensitive base58-derived reference; passing loose strings through the application boundary would permit inconsistent network context.

### 3. Idempotency is insert-first and conflict-aware

The owning application service starts one `@Transactional` operation and calls an internal persistence port. The JDBC adapter uses `INSERT ... ON CONFLICT DO NOTHING RETURNING ...` rather than a race-prone select-before-insert sequence.

- A returned row means `INSERTED`.
- If the insert loses the unique-key race, the adapter reads the committed row by its complete primary key.
- Equal immutable evidence means `ALREADY_PRESENT`; no stored value is updated.
- Different observed block position/hash, normalized `source_event_time`, exact `payload` or `parser_version` raises an explicit identity-conflict exception.

`observed_at` and `ingested_at` describe the first accepted observation and are deliberately not conflict fields. A later delivery may have a later observation timestamp, but append-only semantics retain the first values. There is no duplicate counter or last-seen mutation in this change.

At PostgreSQL `READ COMMITTED`, a unique-key conflict waits for the competing insert and the following select receives a new statement snapshot. This makes concurrent equal submissions converge on one row and conflicting submissions converge on one stored row plus one explicit failure.

Alternatives considered:

- `ON CONFLICT DO UPDATE` would violate immutable evidence and hide provider inconsistencies.
- Blind `DO NOTHING` would silently accept conflicting payloads.
- An application lock would duplicate PostgreSQL concurrency control and would not protect other instances.

### 4. Payload fingerprints use exact UTF-8 bytes and algorithm qualification

The application derives `sha256:<lowercase-hex>` from the exact Java payload string encoded as UTF-8. Callers cannot inject or override the digest. The table stores both the exact text and the digest, so replay can verify integrity without depending on JSON key ordering or PostgreSQL serialization.

The `sha256:` prefix is the algorithm qualification required by the reproducibility contract. A future digest or canonicalization change adds a new explicit contract; it never reinterprets historical values.

Alternatives considered: hashing `JSONB` output risks version-dependent serialization, while storing a bare hex value leaves the algorithm implicit.

### 5. Time precision is normalized once at the application boundary

All supplied `Instant` values and the injected UTC `Clock` value are truncated, never rounded, to microseconds before comparison or persistence. This matches PostgreSQL `TIMESTAMPTZ(6)` and avoids an insert/read mismatch caused by nanoseconds. The application composition root supplies the clock; tests use a fixed clock. Provider I/O does not participate in the transaction.

Alternatives considered: silently relying on the JDBC driver/database conversion makes idempotency comparisons dependent on driver behavior.

### 6. Persistence uses existing Spring JDBC infrastructure

The adapter uses `JdbcClient` with explicit schema-qualified SQL and constructor injection. This is a targeted insert/read workload, not aggregate CRUD, so a Spring Data JDBC repository would add misleading aggregate semantics. No production dependency or infrastructure component is added.

The persistence port, row mapping and SQL stay below `marketdata`; `marketdata::api` remains unchanged. Other modules cannot access this table or internal application types.

### 7. Finality is an admission contract, not a storage claim

V1 accepts only stable-inclusion historical/finalized evidence by caller contract. The storage API has no finality parameter and does not query a chain or infer finality. A future provider adapter that can emit pending, pre-confirmed or otherwise provisional evidence cannot call this boundary until a dedicated finality/reorg change defines state transitions, rollback/reconciliation and tests.

This is deliberately a process and adapter-boundary gate rather than a misleading database check: finality is chain- and source-specific and cannot be proven from the stored row alone.

### 8. Event locator is canonical and parser-independent

`EventId` exposes its opaque component as `locator`. Providers and parser versions do not participate in normalized event identity. For Solana, a future adapter/normalizer change will define the canonical instruction/inner-instruction locator; for EVM, it will use the canonical ordinal within `receipt.logs`, not a provider's filtered-result position. Those calculators are outside this change.

A parser implementation or parser-version change cannot change the locator of the same blockchain event. Any future need to change the persisted grammar requires its own approved OpenSpec change and a forward migration; V1 identities are never silently reinterpreted.

### 9. SQL payload validation protects direct writes

The application uses Java `String.isBlank()` as the complete boundary validation. PostgreSQL additionally rejects empty or whitespace-only payloads, including line-break and tab-only text, through a POSIX-space check. This keeps direct SQL from bypassing the meaningful minimum while exact Unicode blank semantics remain owned by the Java boundary.

### 10. Verification is layered

- Pure unit tests cover CAIP-2 validation/case sensitivity, network consistency, locator preservation and transaction scoping, digest calculation and deterministic microsecond normalization without Spring or Docker. Parser-independent locator stability remains an acceptance constraint for future adapter and normalizer changes.
- Focused market-data PostgreSQL integration tests apply Flyway and separately prove exact schema names, absence of legacy columns, CAIP-2 constraints, network-level distinction, key-size boundaries and storage insert/readback/idempotency/conflict/concurrency behavior. Each test class remains below the repository's 200-line guideline.
- The startup health integration test changes from an empty Flyway assertion to exactly one applied migration and no pending migration.
- Existing `ApplicationModules.verify()`, exact dependency-map and repository convention tests must remain green.
- Full acceptance uses `mvnw.cmd clean verify`, strict OpenSpec validation and OpenSpec doctor with PostgreSQL 18.6.

## Risks / Trade-offs

- [Raw `TEXT` is not directly queryable as structured JSON] → Keep raw evidence exact; add a normalized/queryable representation only with a proven query contract.
- [The natural primary key can be wide] → It is the only required lookup and uniqueness path; measure before introducing a surrogate key or hash identity.
- [One-row insert/read is not the eventual ingestion throughput design] → This change establishes semantics first; a later ingestion change may add bounded JDBC batching while preserving identical outcomes.
- [A parser-version change cannot rewrite an existing raw identity] → Raw evidence is immutable; re-normalization records its own transformation version rather than editing the source observation.
- [Provider may correct previously published evidence] → Surface the correction as a conflict for explicit reconciliation instead of silently destroying the first observation.
- [Storage cannot prove chain finality from row data] → Restrict callers to stable-inclusion evidence and require a separate finality/reorg design before any provisional adapter.
- [A future parser may prefer a different locator] → Treat persisted locator grammar as immutable identity and require a forward migration for an approved change.

## Migration Plan

1. Add the forward-only V1 migration while the baseline has no business schema or durable data.
2. Deploy the application; Flyway creates the module schema and table before the context becomes ready.
3. Verify one applied migration, no pending migrations and all storage scenarios on a clean PostgreSQL 18.6 instance.
4. Do not edit V1 after it is published or applied to a shared/non-disposable environment. Any correction after that point uses a new forward migration.

Rollback of application code may leave the unused table in place; it must not drop evidence automatically. Because this is the first empty-baseline migration, destructive cleanup is allowed only in disposable local/test databases, never as an application rollback step.
