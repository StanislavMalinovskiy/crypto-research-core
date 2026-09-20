# Tasks

## 1. Schema and contracts

- [x] 1.1 Add forward migrations V5–V8 (`raw_transactions`, `price_observations`, `liquidity_observations`, `usd_conversion_facts`, `universe_snapshots`, `universe_members`) under `src/main/resources/db/migration/marketdata/`; verify a Testcontainers migration IT applies V1–V8 cleanly on PostgreSQL 18.6
- [x] 1.2 Add validating domain records and store port interfaces plus `@Transactional` application use cases in `marketdata.application`; verify compilation with `.\mvnw.cmd test-compile`

## 2. Behavioral red

- [x] 2.1 Write Testcontainers ITs for every spec requirement (raw transaction idempotency/conflict/batch, price and liquidity observation idempotency and point-in-time windows, USD conversion idempotency/conflict, universe inclusion/exclusion and snapshot idempotency, volume batch of 10,000 raw transactions) against stub store implementations returning empty results; verify the tests run and fail at the expected behavioral assertions (not at startup or discovery)

## 3. Implementation green

- [x] 3.1 Implement the JDBC stores with insert-first idempotency and batched raw-transaction persistence; verify the new ITs pass
- [x] 3.2 Replace per-row dataset snapshot member insert with chunked batch insert and add batched `findAll` member lookup used by `finalizeDataset`; verify `FirstSignalEvaluationIT` and the full unit suite stay green (internally covered refactoring)
- [x] 3.3 Pass `ApplicationModules.verify()` and the full local gate: `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `.\mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`

## 4. Documentation and status

- [x] 4.1 Update `docs/modules/marketdata.md` with the new owned tables, indexes and the deferred-partitioning decision; verify documentation conventions tests stay green
- [x] 4.2 Record the implemented F2 core-storage status in `docs/DELIVERY_PLAN_FIXES.md`; verify strict change validation `openspec validate add-marketdata-live-storage --strict --no-interactive`

## 5. Independent-review repair

- [x] 5.1 Add behavioral-red PostgreSQL coverage for each missing USD price reference, slash-bearing raw batch identities and exact payload comparison; verify each test fails at its expected behavioral assertion before production repair
- [x] 5.2 Add forward migration V9 enforcing both USD-conversion price references and repair raw batch/equality logic with a typed identity plus exact payload comparison; verify targeted green
- [x] 5.3 Add 1,001-member Testcontainers coverage for dataset lookup/member insertion and universe member insertion, including canonical completeness, idempotent retry, duplicate absence and transactional rollback across the second chunk
- [x] 5.4 Reconcile F1/F2 status and the F3 forward-migration dependency for token decimals, provider-visible/modeled availability and explicit quality provenance; correct the inaccurate V1 payload-budget claim
- [x] 5.5 Pass the complete local gate and report stable Git evidence for independent Control re-review
