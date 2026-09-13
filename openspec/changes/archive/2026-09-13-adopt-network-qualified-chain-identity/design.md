## Context

See [proposal.md](proposal.md) for motivation. The current main specs, kernel values and V1 migration already implement exact CAIP-2 identity and the renamed physical columns introduced by the archived `establish-idempotent-marketdata-storage` change. The remaining gap is explicit conformance: representative EVM networks, the source of a canonical EVM log locator, open-network behavior, synthetic EVM persistence and the relationship between PostgreSQL partitioning and global idempotency are not all asserted directly.

`kernel` owns shared identity semantics and has no persistence. `marketdata` owns `marketdata.raw_chain_events`, its Flyway migration, JDBC adapter and PostgreSQL tests. The existing application use case remains the transaction boundary; this change introduces no provider I/O or cross-module call.

## Goals / Non-Goals

**Goals:**

- Turn the accepted multi-network identity assumptions into executable conformance checks before provider adapters depend on them.
- Keep one network-neutral Java and PostgreSQL model for Solana and EVM observations.
- Preserve the global idempotency key while documenting why time-range partitioning cannot be added mechanically.
- Distinguish already compliant implementation from genuinely missing tests or documentation during apply.

**Non-Goals:**

- Implement a CAIP registry, chain-family hierarchy, provider adapter or EVM decoder.
- Calculate EVM or Solana locators in production code before an adapter/normalizer change owns that behavior.
- Add finality state/history, reorg reconciliation, realtime ingestion, normalized events or business logic.
- Add a migration version, partition, index, dependency, module or DAG edge.

## Decisions

### 1. `ChainId` remains exact CAIP-2 text and open to new networks

The existing CAIP-2 syntax boundary remains authoritative: lowercase namespace, colon and case-sensitive reference. Construction never trims, lowercases or aliases input. `SOLANA_MAINNET` is the only required convenience constant; Ethereum Mainnet, Base Mainnet and Arbitrum One are exercised as ordinary values (`eip155:1`, `eip155:8453`, `eip155:42161`) to prove that constants do not form an allow-list.

No `ChainFamily` or closed enum is introduced. Protocol-family behavior belongs to a future capability that demonstrates a real need; adding it to identity now would couple equality to an incomplete registry.

Alternative considered: constants or an enum for every supported network would make typo discovery convenient but would wrongly reject valid networks unknown at build time.

### 2. Kernel preserves opaque locators; adapters derive canonical locators

`EventId` remains `TransactionId + opaque locator`. The kernel validates value hygiene and equality but does not understand Solana instruction paths or EVM receipts.

For a future EVM adapter, the locator is derived from the ordinal in the complete ordered `receipt.logs` collection. An RPC `logIndex` can be inconsistent in some provider/filter contexts and therefore is evidence to validate, not the direct identity source. A filtered result position is never canonical. Parser version remains provenance; reparsing the same receipt log must reproduce the same locator.

No locator calculator is added in this change. The conformance contract becomes an acceptance criterion for the adapter/normalizer change that first interprets receipts.

Alternative considered: using RPC `logIndex` directly is simpler but makes durable identity depend on provider response semantics rather than the transaction receipt sequence.

### 3. V1 keeps one explicit network-neutral physical model

The required physical names remain:

| Meaning | V1 column |
|---|---|
| Exact CAIP-2 network | `chain_id` |
| Opaque transaction-local value | `transaction_value` |
| Canonical transaction-scoped locator | `event_locator` |
| Source | `provider` |
| Ordered Solana slot or EVM block number | `observed_block_position` |
| Optional independent hash evidence | `observed_block_hash` |

The primary key remains exactly `(chain_id, transaction_value, event_locator, provider)`. Java input and JDBC binding stay network-neutral. A synthetic Base observation is enough to prove EVM compatibility without creating an EVM provider or parser.

All four textual primary-key columns use explicit `COLLATE "C"`. PostgreSQL permits nondeterministic collations whose equality can ignore distinctions such as case, while the kernel identity contract preserves exact supplied text. A local deterministic bytewise collation makes primary-key equality independent of the database default and keeps Java/PostgreSQL identity semantics aligned. This is an identity constraint, not a presentation-locale choice.

V1 may be edited directly only while it has not been published or applied to a shared/non-disposable environment. Apply must inspect the existing migration first and change it only if it differs; it must not create V2 for this conformance change.

Alternative considered: separate Solana/EVM tables duplicate identity, retry and evidence semantics before their workloads demonstrate different storage requirements.

### 4. Stable inclusion is an admission precondition, not identity

V1 receives historical, finalized or otherwise confirmed stable evidence under the approved caller contract. Storage does not query a chain or decide finality/canonicality. These concepts do not enter the raw key, because changing confirmation state must not create a second blockchain-event identity.

A provisional or reorg-sensitive source requires a separate change defining state transitions, reconciliation and history before it can call this storage boundary.

Alternative considered: embedding finality in identity would turn lifecycle state into duplicate identity and make reconciliation ambiguous.

### 5. V1 remains unpartitioned to preserve global deduplication

PostgreSQL requires partition-key columns to participate in unique and primary-key constraints on a partitioned table. `RANGE (ingested_at)` would therefore force `ingested_at` into the key; retries observed at different times could then create multiple rows. V1 remains an ordinary table, and `ingested_at` remains immutable evidence outside identity.

No surrogate key, BRIN index or secondary index has a demonstrated query pattern. A later partitioning change must provide a separate global-deduplication design without weakening the natural identity.

Alternative considered: time partitioning now would optimize an unmeasured workload while changing correctness semantics.

### 6. Apply is conformance-first and gap-driven

Apply starts by comparing Java, V1, JDBC and existing tests with the delta specs. Already conforming implementation is retained. Work is limited to missing test cases and active documentation unless inspection finds a real mismatch. This avoids rewriting the verified insert-first idempotency and conflict/concurrency behavior.

Verification uses pure kernel tests for the four representative CAIP-2 networks and open-network/case behavior, plus PostgreSQL Testcontainers tests for exact schema/PK/collation, synthetic EVM storage, Solana/Base local-value separation, case-only network distinction, provider separation and unchanged duplicate/conflict/concurrency semantics. No production dependency is added.

## Risks / Trade-offs

- [The future EVM locator grammar is constrained before an adapter exists] → Constrain only the canonical source sequence and stability invariant; leave string encoding to the adapter change, which must preserve the spec.
- [V1 has already run in disposable Testcontainers] → Disposable execution does not publish the migration; freeze V1 only after a shared/non-disposable application.
- [An active change may appear to redo archived work] → Proposal, tasks and handoff identify already conforming behavior and require gap-driven edits only.
- [An unpartitioned table may eventually grow large] → Preserve correctness now and introduce partitioning only with workload evidence and a global-deduplication model.

## Migration Plan

1. Inspect the existing kernel, V1 and JDBC contracts; retain every already compliant element.
2. If V1 differs from the specified names or key, correct V1 in place before publication; do not add V2.
3. Add the missing conformance tests and update active documentation.
4. Run the full Maven lifecycle against a clean PostgreSQL 18.6 Testcontainers database and strict OpenSpec validation.
5. After verification, sync the two modified main specs and archive the change.

There is no production data migration or runtime rollback because the schema is not published to a shared/non-disposable environment. Once published, all later schema corrections are forward-only migrations.
