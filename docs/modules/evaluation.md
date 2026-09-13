# evaluation

- **Responsibility:** point-in-time price selection, valuation, friction, virtual positions, entry/avoidance outcomes, deterministic evaluation replay, aggregation and reproducible Evidence Reports.
- **Owned data:** the future `evaluation` schema with run provenance manifests, virtual positions, append-only outcomes and report metadata; it references dataset/signal fingerprints and never duplicates market-price observations; exact tables are approved with their implementing changes.
- **Public API:** synchronous evaluation-run commands, outcome queries and report results; exact contracts are defined by later changes.
- **Allowed dependencies:** `kernel::api`, `marketdata::api`, `signal::api`.
- **Published/consumed events:** `OutcomeCompleted` and `ExperimentCompleted` are Roadmap candidates; exact schemas, timing and delivery semantics remain TBD.
- **Invariants:** evaluation uses the immutable signal snapshot recorded at decision time, never later mutable risk or wallet state; every persisted run records build/source revision, algorithm/configuration identity, dataset fingerprint, cutoff and seed when used; point-in-time price selection references market-data observations; no look-ahead; exact arithmetic and total ordering are explicit; entry and avoidance metrics never mix; missing prices never silently drop rows; dead tokens remain outcomes; repeated evaluation of the same evidence produces the same ordered result.
- **Non-goals:** provider ingestion, raw-input replay, observed-price storage, signal detection, risk/wallet recalculation, mutable operational state and live order execution.
- **Main tests:** deterministic fixture replay, chronological exits, friction tiers, no-liquidity outcomes, point-in-time price-source evidence, horizon idempotency and reproducible report aggregation.
