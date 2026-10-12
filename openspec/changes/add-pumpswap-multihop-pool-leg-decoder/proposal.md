## Why

The immutable Contabo export contains 114 successful transactions that v4 explicitly rejects as `UNSUPPORTED_VARIANT`; offline classification identifies one PumpSwap `multi_hop_swap` pool exchange in each. A pool exchange is useful evidence without pretending to identify the ultimate trader or the complete aggregator route.

## What Changes

- Add a synchronous pure v5 pool-leg decoder alongside unchanged v4, retaining ordinary v4 results and adding separately typed confirmed pool legs.
- Admit only one pinned, same-invocation own Buy/Sell event; multiple events remain explicitly unsupported.
- Keep observed initiator distinct from final trader, which remains undetermined.
- Publish an exact **pool-net SOL/base ratio** only after exact vault-flow/end-point corroboration; otherwise retain confirmed base volume with `PRICE_NOT_CONFIRMED` and no price.
- Reclassify the same immutable exported window and report successful-TX recognition before/after, separately from priced-leg coverage.

## Capabilities

### New Capabilities

- `pumpswap-multihop-pool-leg-decoder`: bounded offline single-event pool exchanges with explicit role, quantity, price-proof and versioned lineage semantics.

### Modified Capabilities

None. The existing active fixed-v4 decoder contract remains unsupported for multi-hop; the new independently versioned API is additive, not a rewrite of that contract.

## Impact

Only `marketdata` API/wiring, a new pure application use case/parser, targeted tests/fixtures and this change's artifacts. No production dependencies, new module edges, SQL, persistence/migrations, server/provider calls, collector run, spending or push. Existing v4 source, fingerprints, V14 consumers, dense-window candles, the other 39 unsupported cases, research tools, raw exports and old receipts are non-goals and remain unchanged.
