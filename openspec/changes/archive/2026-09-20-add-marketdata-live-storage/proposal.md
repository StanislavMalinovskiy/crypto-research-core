# Proposal

## Why

The design-complete but still active `solana-data-contract` change (`define-solana-data-provider-contract`, remediation F1) requires raw transaction payloads stored once per transaction, price, liquidity and USD conversion as separate facts, and point-in-time token universes — none of which the current V1–V4 schema can express (normalized swaps embed enrichment, payloads are stored per event, and no universe or observation tables exist). Its owner-run provider spikes and selection remain outstanding. This change implements the bounded F2 core-storage slice before a live provider adapter is written; full live-storage readiness also requires the explicit F3 forward migrations described below.

## What Changes

- Forward-only Flyway migrations V5–V9 in the `marketdata` schema: `raw_transactions` (one provider payload per chain + transaction + provider with trusted-clock timestamps), `price_observations` (swap-derived native price facts with quote asset and source identity), `liquidity_observations` (pool liquidity facts with source identity), `usd_conversion_facts` (immutable conversions with enforced references to both source price observations and a method version), and `universe_snapshots` + `universe_members` (fingerprinted point-in-time token universes with recorded inclusion and exclusion).
- Idempotent insert-first persistence with immutable-retry equality checks for every new table, following the existing V1/V2 pattern.
- Batched persistence paths: chunked batch insert for raw transactions and dataset snapshot members, and a batched member lookup replacing the per-member finalize query (remediation of the first audit's B7 N+1 finding — internal performance refactoring with no behavior change).
- Point-in-time indexes for the new observation tables under documented query patterns; partitioning explicitly deferred with measured-evidence rationale.
- Volume-shaped Testcontainers verification for the batch paths.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `marketdata-storage`: adds requirements for the new fact tables (raw transaction payload separation, price/liquidity/USD-conversion observations, universe snapshots) and their idempotent, point-in-time behavior.

## Non-Goals

- No ingestion worker, transport adapter, provider client or scheduler (remediation F3).
- No changes to `MarketDataApi` contracts for the new facts; consuming changes (F3/F6) extend the API when consumers exist.
- No token-decimal facts, provider-visible or modeled-availability facts, or complete explicit quality-status/reason provenance. These require F3-owned forward migrations and application contracts before live ingestion can claim full conformance to the active F1 design; V5–V9 are not that completion claim.
- No rewrite of V1–V4 tables or the recorded-replay path; recorded behavior stays as accepted in `recorded-market-replay`.
- No partitioning: deferred until spike-measured volume exists (rationale in design).
- No new production dependencies (`JdbcTemplate` already ships with the accepted Spring JDBC baseline).

## Impact

- Affected module: `marketdata` only (migrations, application use cases, JDBC stores, tests). No module-boundary changes; no cross-module SQL.
- Schema: five forward migrations (V5–V9) under `src/main/resources/db/migration/marketdata/`.
- Documents: `docs/modules/marketdata.md` gains the new owned tables; `docs/DELIVERY_PLAN_FIXES.md` F2 status updated.
