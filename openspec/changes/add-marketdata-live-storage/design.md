# Design

## Context

The accepted `solana-data-contract` requires fact separation that V1–V4 cannot express. This change adds forward-only storage for those facts plus the batched persistence paths remediation F2 demands. See the F1 change (`define-solana-data-provider-contract`) for the contract rationale and `docs/DELIVERY_PLAN_FIXES.md` F2 for scope.

## Goals / Non-Goals

Goals: schema and idempotent repositories for raw transaction payloads, price/liquidity/USD-conversion observations and point-in-time universes; batched raw and snapshot-member persistence; volume-shaped verification.

Non-Goals: ingestion wiring (F3), API surface for new facts (consuming changes), partitioning (deferred), any change to recorded-replay behavior.

## Decisions

### D1. Table identities and shapes (migrations V5–V8)

All keys use deterministic bytewise `C` collation like V1/V2; instants are `TIMESTAMPTZ(6)`; exact decimals follow the F1 scales (price 18, liquidity 8, confidence 4).

- **V5 `marketdata.raw_transactions`** — PK `(chain_id, transaction_value, provider)`. Columns: `payload TEXT NOT NULL`, `payload_hash` (`sha256:<64hex>` check), `observed_block_position BIGINT NOT NULL`, `observed_block_hash` nullable, `source_event_time` nullable, `received_at NOT NULL` (trusted clock), `admitted_at NOT NULL`, `ingested_at NOT NULL`, `parser_version` nullable. Payload size check mirrors V1's UTF-8 budget. One payload per transaction per provider; event-scoped observations stay in `raw_chain_events` (untouched).
- **V6 `marketdata.price_observations`** — PK `(chain_id, transaction_value, event_locator)`. Columns: `asset_address`, `quote_asset_address` (wrapped-SOL mint for SOL-paired, USDC mint for direct pairs), `venue`, `price NUMERIC(38,18)` (asset price in quote asset), `trade_notional_quote NUMERIC(38,8)`, `observed_block_position`, `observed_at`, `confidence NUMERIC(5,4)`, `provider`, `source_event_time` nullable. Secondary index `(chain_id, asset_address, observed_at, transaction_value, event_locator)` for point-in-time queries.
- **V7 `marketdata.liquidity_observations`** — PK `(chain_id, asset_address, pool_address, transaction_value, event_locator)`. Columns: `liquidity_quote_asset`, `liquidity_usd NUMERIC(38,8)`, `base_reserve NUMERIC(78,0)`, `quote_reserve NUMERIC(78,0)` (raw integer units), `observed_block_position`, `observed_at`, `confidence`, `provider`, `source_event_time` nullable. Secondary index `(chain_id, asset_address, observed_at, pool_address, transaction_value, event_locator)`.
- **V8 `marketdata.usd_conversion_facts`** — PK `(chain_id, transaction_value, event_locator)` (the converted price observation's source identity), with `asset_address` as a data column. Other columns: `usd_quote_transaction_value`/`usd_quote_event_locator` (the SOL/USD observation used), `price_usd NUMERIC(38,18)`, `method_version`, `computed_at`.
- **V8 `marketdata.universe_snapshots`** — `snapshot_id VARCHAR(71) PK` (sha256 fingerprint), `fingerprint UNIQUE`, `rule_version`, `cutoff`. **`marketdata.universe_members`** — PK `(snapshot_id, chain_id, asset_address)`; columns: `member_ordinal`, `discovery_source`, `eligibility_rule_version`, `inclusion_time`, `exclusion_time` nullable, `exclusion_reason` nullable; check `exclusion_time IS NULL OR exclusion_time > inclusion_time`. Point-in-time membership is a PK-range query filtered by inclusion/exclusion times — no additional index at MVP volume (documented query: one snapshot at a time).

USD conversions of SOL itself are price observations of the wrapped-SOL mint quoted in USDC (documented convention; no synthetic asset rows).

### D2. Idempotency pattern (unchanged from V1/V2)

Insert-first `ON CONFLICT … DO NOTHING RETURNING 1`; on conflict, re-read the stored row and compare immutable evidence; equal retry returns the stored record, mismatch throws the module's conflict exception. Batch paths resolve per-row outcomes the same way via post-batch re-read.

### D3. Batched persistence

`JdbcTemplate.batchUpdate` (sanctioned by the architecture's persistence policy for high-volume writes) with chunk size 1,000: raw transaction batch insert, dataset snapshot member insert (replacing the per-row loop in `JdbcNormalizedMarketDataStore.storeSnapshot`), and a new `findAll(identities)` batched member lookup used by `RecordedMarketDataService.finalizeDataset` (replacing the per-member `find` — remediation of audit finding B7). Chunks keep statement batches bounded.

### D4. Partitioning deferred (explicit decision)

New tables stay unpartitioned with only the documented indexes above. Rationale: volume numbers are planning assumptions until spikes S1/S2 measure them; premature partitioning would violate the architecture's evidence rule. Revisit trigger: measured insertion/query volume from real ingestion exceeding the documented per-table thresholds in the F1 capacity estimate (0.4–2.7 TB envelope estimate). The decision is recorded here and in `docs/modules/marketdata.md`.

### D5. Ownership, transactions and time

All new tables, SQL, repositories and migrations are owned by `marketdata`. New application use cases (`StoreRawTransactionUseCase`, `RecordMarketFactUseCase`, `FinalizeUniverseSnapshotUseCase`) carry `@Transactional`; repositories never start business transactions; no provider I/O exists yet. `received_at` comes from the injected `Clock` at the caller boundary per the F1 time model; storage records it verbatim. Observation records reject values whose decimal scale exceeds the persisted column scale, so no silent database rounding can make conflicting evidence compare equal.

## Risks / Trade-offs

- [Schema exists before live writers] → intentional (F2 before F3 per the remediation order); Testcontainers ITs are the writers until F3.
- [Batch chunk size is an assumption] → bounded and configurable constant; measured by the volume test; not load-bearing for correctness.
- [Universe PK without time dimension] → point-in-time filtering is a scan of one snapshot's members; acceptable at universe sizes (<100k tokens); revisited with real volume.

## Migration Plan

Forward-only Flyway V5–V8; V1–V4 untouched; no data backfill (no real data yet). Rollback is a reverted migration file set before any production application — after mass recording, forward-only policy applies.

## Verification Strategy

Red-first Testcontainers ITs per requirement (idempotent retry, conflict rejection, point-in-time windows, universe inclusion/exclusion, batch correctness including internal duplicates), a volume-shaped IT (10,000 raw transactions through the batch path asserting complete, idempotent storage), the existing suite staying green (recorded behavior unchanged), `ApplicationModules.verify()`, and the full four-command gate. The N+1 member-batch change is internally covered refactoring — existing `FirstSignalEvaluationIT` remains the behavioral guard; no new behavioral test is required for it.

## Open Questions

- Whether `raw_chain_events` gains a foreign key to `raw_transactions` once F3 writes both (decided in F3's change; V1 stays untouched here).
- Exact quality-flag column shape on observations (JSONB vs typed columns) when the F6 quality policy lands.
