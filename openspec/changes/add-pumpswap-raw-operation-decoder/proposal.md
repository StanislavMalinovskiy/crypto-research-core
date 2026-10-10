## Why

The qualified configured6h collector test is complete, but retained raw transactions are not yet separated into independently attributable operations. The owner authorizes an offline decoder of saved session `484bec54-c92b-438f-9fe0-89f1c8150844` (124 raw TX,75 successful/49 failed,400 state members), without new capture or admission.

## What Changes

- Add a synchronous, pure `marketdata` raw-operation decode boundary returning confirmed operation records and explicit per-transaction/invocation statuses with exact raw lineage.
- Decode supported legacy/v0/v1 JSON, complete instruction/CPI positions and pinned PumpSwap buy/sell/buy_exact_quote_in events, including proven Token2022 transfers and ATA lifecycle; keep multiple traders/operations separate.
- Extend documentary schema support to the exact October7 IDL observed in the saved run: current449 sell and497/500/512/515 buy-family events, v2 fixed roles and pinned ancillary volume/sweep instructions. Preserve May400/448/463 compatibility and proof; multi_hop_swap508 remains explicitly unsupported rather than adding bonding-curve/routing scope.
- Clarify the already authorized narrow Token2022 ATA lifecycle: support exact one-byte InitializeImmutableOwner22 before token-account initialization, with a uniquely proven170-byte Token2022 allocation and complete endpoint/event/transfer corroboration. Fee-bearing/hook/other unsupported extensions, ambiguous account epochs and prefunded allocate/assign remain explicit backlog; nominal transfer arguments never prove principal alone.
- Interpret current512/515 exact-quote-in events under the pinned SDK's post-fee curve-input meaning, proving actual user debit and vault flow independently with exact event/fee/transfer facts. Preserve May463 assertions; no guessed fee formulas, cashback refunds or nominal-transfer-only confirmation.
- Retain unknown fees, unsupported layouts and ambiguous association explicitly; reconcile every input TX and never derive economic truth from transaction-wide trader aggregation or current account snapshots.
- Apply the owner's prospective MVP completion criterion: at least38 of the fixed75 successful saved TX fully recognized through independently proven operations and resolved relevant PumpSwap invocations; partial TX do not inflate recognition. Every other TX has a status/reason; rare unsupported formats may remain documented backlog. Required supported variants and all proof/safety tests remain mandatory.
- Verify the saved window offline after meaningful RED/GREEN and fresh independent project Reviewer approval. Update canonical delivery/current collector status and qualified owner billing observation, without changing budget coefficients or original receipts.

## Current verification status, 2026-10-11

Implementation review is APPROVED for `pumpswap-operations-v4` / `may18-oct07-endpoint-proof-v4`:121 unit and14 isolated PostgreSQL tests passed with zero failures/errors/skips. [Verified evidence and financial repairs](design.md#review-approved-v4-main-gate-and-qualification-pending-2026-10-11) preserve independent actual endpoint corroboration and exclusive same-invocation principal proof. Tasks1.1–1.3 are verified; Main's new complete gate/commit and independent post-gate saved-window qualification remain pending (tasks2.1–2.2). The47/75 Builder diagnostic is not accepted qualification, DONE, archive or admission.

## Capabilities

### New Capabilities

- `pumpswap-raw-operation-decoder`: bounded offline confirmed-operation decoding with immutable source lineage, deterministic identity and explicit incomplete coverage.

### Modified Capabilities

None. Existing recorded replay, collector, accepted normalized storage and research contracts retain their meaning.

## Impact

Only `marketdata` Java API/application/infrastructure parsing and owning tests/fixtures; no new module edge, production dependency, database schema/write or runtime trigger. CORE_RISK: exact financial units, event identity/order and reproducible evidence (TR-07/08/11); RED_REQUIRED and fresh independent Reviewer. Non-goals: collector/provider calls/spend, ongoing collection,12h, recovery repair or WS1011 cause investigation, historical R1 mapper mutation, per-trader/PnL aggregation, USD/liquidity/signal-grade normalization, D1/calibration/P1/outcomes/admission, UI or execution. Canonical sequencing is [Delivery Plan](../../../docs/DELIVERY_PLAN.md); research replan notes remain historical. Visible explicit incomplete intervals with bounded self-recovery are acceptable MVP limitations, not a waiver of financial/PIT/evidence/admission safety or a zero-loss claim.
