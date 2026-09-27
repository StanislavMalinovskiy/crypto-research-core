## Why

The recorded `LIQUIDITY_SPIKE` detector currently accepts the latest baseline from any time before `windowStart` and a current observation from anywhere in the decision window, so stale evidence can produce an apparent one-hour liquidity increase. The owner has authorized bringing this bounded part of delivery item F6.2 forward while F1 provider selection remains open, to make the existing recorded-data comparison temporally explicit and reproducible.

## What Changes

- **BREAKING:** Define the one-hour target as `T = C - 60 minutes`, where `C` is the decision cutoff, and require `windowStart = T` after the existing microsecond normalization. Reject inconsistent requests before any durable effect.
- **BREAKING:** Select the latest baseline by `observedAt` from the inclusive interval `[T - 5 minutes, T]`, and the latest current observation from `[C - 1 minute, C]`. Resolve equal timestamps with the existing total normalized-identity order; provider identity does not become a tie-break or a normalized event key.
- Require at least one observation in each window, with two distinct normalized identities and observation timestamps. Missing endpoint evidence produces no candidate or accepted signal; there is no interpolation, fabricated replacement, or automatic widening of either window.
- Keep the existing relative increase of at least 50 percent, absolute increase of at least USD 10,000, candidate-first risk gate, exact arithmetic, scoring and immutable signal evidence.
- **BREAKING:** Introduce the fixed detector policy `liquidity-spike-v2` for new detection calls and reject unsupported detector versions before effects. The fixed window and selection parameters are represented by that version; the request's existing configuration fingerprint remains part of reproducible identity. Previously stored snapshots retain their original values and versions and remain readable.
- Define the result as an endpoint comparison over an actual elapsed interval of 59 to 65 minutes. The five-minute baseline tolerance accommodates observations near the one-hour target; the stricter one-minute current bound limits reliance on old decision-time evidence. These are explicit initial engineering choices, not empirically optimized thresholds.

### Non-goals and interpretation limits

- No provider selection, live adapter, backfill, finality handling, transport gap recovery, or new availability-time model. F1 and F3 remain open.
- No claim of sustained growth or continuous hourly coverage. Two endpoint observations do not prove the absence of unresolved provider gaps; this change does not complete production F6.2.
- Bounds remain on `observedAt`, not `sourceEventTime`. They establish observation-time recency within the existing recorded-data contract, not independently verified freshness of live source events.
- No new cross-venue or cross-pool aggregation or comparability policy. The existing asset/dataset observation contract remains the input boundary; callers remain responsible for supplying comparable liquidity evidence. Same asset alone does not establish economic comparability across venues or pools.
- No changes to signal families, deduplication scheduling, risk rules, score calibration, entry/horizon valuation, report retry behavior, or execution.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `signal-evaluation`: make the liquidity-spike baseline and current observation windows, endpoint coverage, deterministic selection, request validation and detector-version transition explicit while preserving immutable historical signal snapshots.

## Impact

- **Owning module:** `signal`; the detector continues to read bounded observations through `marketdata::api` and consult `risk::api`. Production changes are confined to the signal module.
- **Compatibility:** preserve the shape of `SignalApi.DetectionRequest` and the existing market-data API. Callers must provide the supported detector version and a one-hour `windowStart`; old-version detection calls no longer execute. Historical accepted-signal lookup remains compatible.
- **Storage and architecture:** no migration, schema change, production dependency, module-boundary change, or cross-module SQL. Existing module-owned transactions and immutable persistence remain in place.
- **Verification:** specification-derived behavioral RED and targeted unit/PostgreSQL regression evidence are required for boundary inclusion, stale/missing endpoints, timestamp normalization, deterministic ties, invalid requests, version handling and unchanged first-slice results under the new version. Planning itself does not implement or execute these tests.
- **Documentation:** the active delta and signal module documentation will describe the bounded recorded-data scope and its limits; later completion updates must not mark F1, F3 or all production F6.2 complete.
- **Architect assessment:** `risk = CORE_RISK`; `risk_triggers = TR-06, TR-07, TR-08, TR-11` for point-in-time selection, financial evidence, ordering/identity and reproducibility; `test_mode = RED_REQUIRED`. Detailed invariant applicability and the bounded implementation budget belong in the design. Fresh independent review is required both for this risk and for the intended accepted-spec amendment.
