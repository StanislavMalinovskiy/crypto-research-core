## Why

The archived F1 research left Alchemy live/replay suitability inconclusive and the historical account-state probe unexecuted after a local debit-validation stop. Two finite experiments can collect the missing observations while preserving the earlier terminal receipts and the accepted barrier against selecting an unverified production source.

## What Changes

- Add one explicitly enabled Alchemy PAYG experiment that measures 60 clean minutes of finalized watched-program LIVE delivery after catch-up, including failed transactions as a separate count. Probe two bounded finalized slot-only `from_slot` offsets. Each replay stops on its first returned slots; it does not claim transaction recovery or complete interval reconciliation.
- Correct the local legacy debit-validation incompatibility and run the existing 12-read historical account-state matrix at two finalized slots, with repeat reads of one PumpSwap pool and two vaults.
- Apply a shared estimated $3 spending ceiling to both new experiments, a 15 GB received-byte ceiling to the live/replay experiment, finite request/time/evidence limits, secret-safe diagnostics and distinct evidence roots. Record measured traffic, lag, failed share, local compression, an offline SQD reserve-input comparison, and a provider-independent recovery design in the decision note before synchronizing delivery/status documents.
- After the first A receipt stopped without clean LIVE, permit exactly one final A-only attempt in a fresh immutable evidence root through a root-handling-only correction. Retain the first A/B receipts and their incomplete evidence; do not repeat B or treat the already observed slot-only replays as verified recovery. End this change with a factual terminal disposition, including an inconclusive result when required measurements cannot be obtained.

## Capabilities

### New Capabilities

- `bounded-provider-spikes`: Operator-visible safety, evidence and outcome contract for the two isolated Alchemy research experiments.

### Modified Capabilities

None. The accepted `solana-data-contract` requirement for evidence-based production selection remains unchanged.

## Impact

Only `tools/spikes/alchemy-s1/**`, the active change artifacts, `docs/notes/SOLANA_PROVIDER_SPIKE_RESEARCH_2026-09-27.md`, and factual delivery/navigation documentation are affected. The `marketdata` module is relevant as the eventual consumer but no production module, Java API, database, migration, dependency, runtime configuration, accepted ADR or accepted spec is changed by implementation. No purchase, Chainstack probe, F3 ingestion, full S1 recovery certification, S2/S4/S5 completion or production-primary selection is included.
