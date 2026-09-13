# signal

- **Responsibility:** versioned signal definitions, detectors, candidates, family-specific scoring, reasoning and immutable accepted-signal snapshots.
- **Owned data:** the future `signal` schema with versioned definitions/configuration fingerprints, candidates, signals, reasoning and immutable decision-time evidence snapshots; exact tables are approved with their implementing changes.
- **Public API:** synchronous detection/evaluation commands and immutable accepted-signal snapshots; exact contracts are defined by a later signal change.
- **Allowed dependencies:** `kernel::api`, `marketdata::api`, `risk::api`, `wallet::api`.
- **Published/consumed events:** `SignalAccepted` and `CandidateRejected` are Roadmap candidates; schemas, timing and consumers remain TBD.
- **Invariants:** candidates are recorded before acceptance; algorithm/definition version and canonical configuration fingerprint are mandatory; time, exact arithmetic, ordering/tie-break and seed semantics follow the reproducibility contract; deduplication has an explicit window; scores are not profit predictions; an accepted snapshot contains only evidence available at its cutoff without exposing internal risk or wallet DTOs; signal never writes market-data tables.
- **Non-goals:** observed-price ownership, outcome valuation, evaluation replay, reporting, real execution and provider ownership.
- **Main tests:** family rules, score/grade boundaries, risk and wallet evidence handling, immutable snapshot completeness, deduplication and reproducibility.
