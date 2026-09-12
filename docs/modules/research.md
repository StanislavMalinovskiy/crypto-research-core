# research

- **Responsibility:** point-in-time replay, backtest, aggregation and reproducible Evidence Reports.
- **Owned data:** the future `research` schema with run/report metadata justified by its implementing change; no bootstrap tables.
- **Public API:** research-run commands and report results; exact contracts are TBD.
- **Allowed dependencies:** `marketdata::api`, `risk::api`, `wallet::api`, `strategy::api`, `measurement::api`.
- **Published/consumed events:** `ExperimentCompleted` is a Roadmap candidate; consumption of completed outcomes is TBD.
- **Invariants:** no look-ahead; rejected candidates are analyzed separately; results expose sample size, costs, confidence and outlier dependence; no operational module depends on research.
- **Non-goals:** provider ingestion, mutable operational state and live execution.
- **Main tests:** deterministic replay, point-in-time fixtures, family/horizon separation and reproducible report aggregation.
