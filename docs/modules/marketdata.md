# marketdata

- **Responsibility:** provider adapters, immutable raw events, normalization, swaps, tokens, prices, metrics and stream-gap recovery.
- **Owned data:** the future `marketdata` schema and its `raw_chain_events`, `tokens`, `swaps`, `token_metrics`, `token_prices` and relevant operational state; no business tables in bootstrap.
- **Public API:** synchronous, domain-neutral access to normalized and point-in-time market data; exact contracts are TBD for the first market-data change.
- **Allowed dependencies:** `kernel::api`.
- **Published/consumed events:** high-volume swaps and ticks remain internal; low-frequency completed facts are TBD.
- **Invariants:** persist raw input before normalization; chain-aware idempotency; no fabricated provider data; gaps are visible and recoverable; other modules never read market-data tables directly.
- **Non-goals:** signal generation, wallet scoring, risk decisions and EVM implementation in MVP.
- **Main tests:** parser fixtures, idempotent persistence, gap recovery, provider failure behavior and PostgreSQL integration.
