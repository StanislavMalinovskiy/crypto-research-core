## Why

Stage 2 needs one thin but honest research path before adding live providers or broad signal catalogs. The repository can preserve raw observations, but it cannot yet replay recorded evidence into normalized market facts, create an immutable signal, measure a forward outcome, or produce a reproducible result that proves the six-module architecture works end to end.

## What Changes

- Add deterministic replay of a recorded Solana fixture through existing raw-observation storage into provider-independent normalized swaps, point-in-time price/liquidity observations and an immutable dataset snapshot.
- Add the first deliberately narrow signal path for `LIQUIDITY_SPIKE`: compare only evidence available at the decision cutoff, obtain a point-in-time `ALLOW` decision from `risk`, persist the candidate before acceptance, and persist an immutable accepted-signal snapshot with versioned detector/scorer configuration and complete lineage.
- Add one `1h` ENTRY evaluation path that selects admissible post-decision prices, applies the documented Solana liquidity-tier friction with exact arithmetic, persists an idempotent outcome and produces a deterministic one-family evidence report with a complete run provenance manifest.
- Add module-owned Flyway migrations and synchronous public APIs for the new durable boundaries without changing the accepted module dependency graph.
- Add fixture-driven unit, Modulith and PostgreSQL integration coverage proving deterministic replay, no look-ahead, ownership, idempotency and identical report output across repeated runs.

Non-goals:

- No live or historical provider adapter, WebSocket/RPC client, scheduler, background worker, gap recovery or external network access.
- No wallet scoring, wallet persistence, `SMART_WALLET_BUY`, `MULTI_WALLET_BUY`, `HOLDER_GROWTH`, `TOKEN_RISK_ALERT` or multi-family aggregation.
- No full token-risk provider integration or durable risk history; the slice implements only the generic point-in-time decision needed to prove the accepted `ALLOW` path.
- No 4h/24h horizons, virtual-position lifecycle, stop loss, take profit, time stop, terminal-liquidity policy, rejected-candidate outcomes or production calibration.
- No HTTP/CLI report endpoint, UI, execution, paper/live trading, provider fallback, fabricated market data, partitioning or speculative indexes.
- No new production dependency, application module, Maven module, deployable or change to the accepted module DAG.

## Capabilities

### New Capabilities

- `recorded-market-replay`: deterministic replay of recorded raw Solana observations into normalized market facts and a fingerprinted immutable dataset snapshot owned by `marketdata`.
- `signal-evaluation`: one versioned `LIQUIDITY_SPIKE` decision-time signal, one point-in-time 1h ENTRY outcome and one reproducible evidence report across the accepted module APIs.

### Modified Capabilities

None. Existing storage, identity, module-boundary and reproducibility requirements remain unchanged and constrain the new capabilities.

## Impact

Affected modules are `marketdata`, `risk`, `signal` and `evaluation`; `kernel` identities are reused unchanged and `wallet` remains untouched. `marketdata` gains normalized replay data and dataset snapshot ownership, `risk` gains a small synchronous point-in-time assessment API without a schema, `signal` gains its first module-owned durable candidate/snapshot objects, and `evaluation` gains its first run/outcome/report objects. PostgreSQL receives Flyway-owned migrations under the three data-owning modules. The application remains one synchronous Java 25 Spring Modulith JAR using the existing PostgreSQL database and current dependency set.
