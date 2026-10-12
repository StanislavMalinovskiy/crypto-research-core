## Why

The saved Contabo window now yields 114 confirmed multi-hop pool exchanges, but existing candles intentionally accept only fixed-v4 stored batches and user-execution price amounts. Combining those prices with v5 pool-net ratios would mix meanings, while two dense GTA pairs remain unavailable under the old128-source limit.

## What Changes

- Add a pure, opt-in recorded-window candle API selecting the fixed v5 interpretation once per transaction, with its ordinary v4-proven operations and separate multi-hop legs.
- Prove one common `POOL_NET_SOL_PER_BASE` basis from actual attributable pool-vault flows for both types; unknown prices remain counted, never substituted with user/gross amounts.
- Process complete dense saved input through deterministic existing128-source decoder chunks; finite whole-window limits and fail-closed overflow, no raw truncation or silent sampling.
- Add a separate native-SOL diagnostic route reusing the existing adjacent300s/inclusive5% rule, without accepted signal/evaluation writes.
- Qualify the preserved25-minute window and present one table for the three configured pools, including candle coverage and TRIGGER/NO_TRIGGER/INSUFFICIENT counts/reasons.

## Capabilities

### New Capabilities

- `pumpswap-versioned-pool-candle-diagnostic`: version-selected offline pool-net event candles and honest native-SOL pattern diagnosis over complete bounded saved input.

### Modified Capabilities

None. Fixed-v4/v5 decoders, V14 stored-batch candles and original diagnostic contracts retain their behavior and identities.

## Impact

Only additive `marketdata`/`signal` APIs, pure owning use cases/proof/candle calculations, targeted tests and this change's artifacts. Existing module direction `signal → marketdata::api` remains. No new dependency, schema/SQL/persistence, provider/server/managed DB access, collection, expense or push in this task. Other39 unsupported decoder cases, R1/D1, calibration, evaluation/UI and future permanent collection remain outside this change. The owner's later prospective runtime/$50 authority belongs to a separate subsequent task, not permission to activate anything during this offline task.
