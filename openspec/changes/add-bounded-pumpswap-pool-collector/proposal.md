## Why

The owner paused R1-E2/D1 on 2026-10-07 after the factual D1 INSUFFICIENT result (`e386884`). The next product increment needs a small durable live transaction-and-state collector before quality rules, signals, evaluation and advisory UI can use real evidence.

## What Changes

- Plan one explicitly configured, finite PumpSwap pool set on Solana Mainnet, using Alchemy PAYG as the first development integration and Helius Free only as an additional candidate.
- Collect finalized transaction notifications and pool/vault/mint account-state notifications through WebSocket subscriptions; fetch complete transaction evidence through JSON-RPC and retain exact raw evidence in PostgreSQL.
- Expose durable session limits, dispositions and gaps; bounded reconnect recovery may recover transactions but cannot fabricate missed historical account states.
- Keep all evidence capture-only until a later quality/admission contract. Existing E2 May-layout, Token2022, self-CPI, ATA lifecycle and fee findings guide explicit unsupported statuses, not a promise of complete economics.

## Capabilities

### New Capabilities

- `pumpswap-pool-capture`: Bounded finalized raw transaction/state capture, durable checkpoints and explicit gaps.

### Modified Capabilities

None. Accepted `solana-data-contract`, `marketdata-storage` and `chain-identity` remain controlling. Capture does not confer signal/evaluation admission or verified production-primary status.

## Impact

Only future `marketdata` adapter/application/persistence code, its tests and additive module-owned Flyway migrations. Reuse the existing raw-transaction use case and public module boundaries; no new dependency, service, database, module edge or gRPC SDK is selected. JDK HTTP/WebSocket clients fit the synchronous adapter boundary.

This allocation is PLAN ONLY: no code, provider calls, purchases, paid launch, archive or old evidence mutation. Non-goals: whole-chain discovery, complete historic D1, economic normalization, outcome calculation, calibration/P1, Helius paid tiers, automatic fallback, gRPC, trading and UI implementation. Preliminary analysis after 2–4 weeks of accumulated data is a tentative horizon, not a product delivery estimate or evidence of edge.
