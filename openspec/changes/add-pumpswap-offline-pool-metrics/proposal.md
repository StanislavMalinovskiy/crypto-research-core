## Why

Delivery queue step5 needs an inspectable analytical view of already retained PumpSwap operations before signal-grade normalization is justified. Exact pinned-batch native execution candles and activity can provide that first local result without inventing USD prices, pool liquidity, complete coverage or historical decision availability.

## What Changes

- Add one synchronous, read-only marketdata API for one exact persisted operation batch, a bounded observation window and supported candle interval.
- Compute deterministic per-pool native execution OHLC ratios and raw-unit activity, with explicit missing-price metadata, complete transaction/operation reconciliation and immutable revision references.
- Reject ambiguous canonical-operation revisions rather than choosing a provider/parser or double-counting; retain batch limitations and label every result historical diagnostic/incomplete coverage.
- Add pure behavioral and isolated PostgreSQL public-API/no-write/compatibility tests. No schema, dependency, runtime activation or existing detector changes.

## Capabilities

### New Capabilities

- `pumpswap-offline-pool-metrics`: bounded revision-pinned historical diagnostic candles/activity over retained operations, without market-fact or signal admission.

### Modified Capabilities

None. Existing operation decoding/storage/readback, capture/monitor, normalized market facts and signal/evaluation requirements remain unchanged.

## Impact

Only marketdata API records/delegation and a bounded read/aggregate use case plus owning tests; exact Builder paths and verification are in design/tasks. Reuse the verified V14 pinned-batch reader and original V5 observation clocks. One JAR/database, module DAG, Java25 stable baseline and current dependencies remain unchanged; no ADR or migration is required.

Non-goals: batch merging, arbitrary result import or revision selector, provider/account reads, collector/coordinator changes, live/paid collection, managed DB mutation/migration, deployment, pool expansion, executable pricing, USD conversion, reserves/liquidity valuation, PnL/fees allocation, ranking/outcomes, new signal families or signal publication, R1/D1/P1/holdout access/admission, archive or push. Main dispatches Builder only after current readiness gate/commit/factual closure releases shared paths.
