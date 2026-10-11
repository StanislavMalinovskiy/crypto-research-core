## Why

Saved PumpSwap operations already produce exact observation-time diagnostics, but those buckets are not blockchain-time candles and cannot honestly feed the accepted USD-liquidity detector. Add the smallest offline chain-time candle and native-SOL pattern diagnostic to demonstrate the product path without inventing historical availability, risk or profitability.

## What Changes

- Add a read-only `marketdata` API over one exact immutable stored operation batch: provider `blockTime` event buckets, source-reported slot/transaction index and explicit ordering/coverage limitations, raw-derived time checks and conservative knowledge cutoff.
- Add a separate read-only diagnostic in existing `signal`: `native-sol-price-rise-v1`, comparing two adjacent300s observed candles with exact close-price growth of at least5%. These are proposed uncalibrated engineering defaults, not an accepted investment strategy.
- Return `TRIGGER`, `NO_TRIGGER` or `INSUFFICIENT` with complete reasons, source references/fingerprint and diagnostic-only readiness. Missing/late/unpriced evidence is never no-price/no-activity/zero-return fiction. No positive saved trigger count is required.
- Preserve observation-time metrics/operator reports and existing accepted `liquidity-spike-v2`/evaluation semantics. No normalized facts, candidate/accepted-signal/evaluation writes, migrations, dependencies, new analytics engine or provider activity.

## Capabilities

### New Capabilities

- `pumpswap-chain-time-signal-diagnostic`: Exact event-time observed candles and a cutoff-safe read-only native-SOL price-rise diagnostic, explicitly separate from accepted signal/admission and valuation.

### Modified Capabilities

None. Existing accepted signal-evaluation and active observation-time metrics requirements remain unchanged.

## Impact

Additive ordinary-value APIs and small owning use cases/calculators in `marketdata` and `signal`, using the existing immutable batch reader, exact numeric/fingerprint conventions and allowed `signal → marketdata::api` edge. `evaluation` remains unchanged: its USD-liquidity friction/risk-qualified accepted-signal contract is not available for these diagnostics.

Initial owner authorization covered PLAN only; creation of this change did not itself authorize implementation. On2026-10-11 the owner's subsequent “Делай дальше и не останавливайся” authorized implementation of this exact plan. Implementation and saved-data qualification received fresh independent APPROVE; targeted35 tests passed, and all six tasks are verified under3.2. Main gate02 PASS622 (329 unit+293 PostgreSQL), zero failures/errors/skips/exit0; Done bounded — implemented and verified; no commit SHA is asserted by this status record. See [dated verified implementation status](design.md#implemented-and-reviewed-main-gate-pending-2026-10-12). Scope remains saved data/local isolated tests, zero provider calls/spend, no collector/deployment/limit/rate change, R1/holdout/outcomes/admission, push or archive. Final gate PASS is not accepted signal admission or permission for runtime activation.
