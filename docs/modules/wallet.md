# wallet

- **Responsibility:** point-in-time wallet history, FIFO closed-trade PnL, scoring, tiers and watchlist decisions.
- **Owned data:** the future `wallet` schema with wallet profiles, append-only score history and current score snapshots; no bootstrap tables.
- **Public API:** synchronous point-in-time wallet score/profile queries; exact contracts are TBD.
- **Allowed dependencies:** `kernel::api`, `marketdata::api`.
- **Published/consumed events:** TBD; bootstrap fixes no wallet event contract.
- **Invariants:** wallet identity is chain-scoped; FIFO matches across venues; open positions do not prove skill; no future data.
- **Non-goals:** cross-chain identity, strategy acceptance and trade execution.
- **Main tests:** FIFO partial fills, cross-venue matching, profit factor, point-in-time replay and tier boundaries.
