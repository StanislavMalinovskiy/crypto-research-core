# measurement

- **Responsibility:** virtual positions, friction, executable exits, price valuation and entry/avoidance outcomes.
- **Owned data:** the future `measurement` schema with virtual positions and append-only outcome records; no bootstrap tables.
- **Public API:** synchronous outcome and valuation queries; exact contracts are TBD.
- **Allowed dependencies:** `kernel::api`, `marketdata::api`, `strategy::api`, `governance::api`.
- **Published/consumed events:** `OutcomeCompleted` is a Roadmap candidate; consumed signal facts and exact delivery semantics are TBD.
- **Invariants:** entry and avoidance metrics never mix; missing prices never silently drop rows; dead tokens remain outcomes; costs are explicit; measurement observes outcomes but never controls strategy.
- **Non-goals:** live order execution, signal detection and research aggregation.
- **Main tests:** chronological exits, friction tiers, no-liquidity outcomes, price-source evidence and horizon idempotency.
