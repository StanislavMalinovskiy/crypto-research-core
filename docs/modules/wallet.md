# wallet

- **Responsibility:** point-in-time wallet history, FIFO closed-trade PnL, scoring, tiers and watchlist decisions.
- **Owned data:** the future `wallet` schema with wallet profiles, append-only score history and current score snapshots; no bootstrap tables.
- **Public API:** synchronous point-in-time wallet score/profile queries; exact contracts are TBD.
- **Allowed dependencies:** `kernel::api`, `marketdata::api`.
- **Published/consumed events:** TBD; bootstrap fixes no wallet event contract.
- **Invariants:** wallet identity is chain-scoped; FIFO matches across venues; open positions do not prove skill; calculations use exact arithmetic and an explicit reference time; point-in-time evidence never includes future data.
- **Non-goals:** cross-chain identity, signal acceptance, outcome evaluation and trade execution.
- **Main tests:** FIFO partial fills, cross-venue matching, profit factor, point-in-time history reconstruction and tier boundaries.
