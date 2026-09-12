# strategy

- **Responsibility:** versioned experiments, detectors, candidates, family-specific scoring, reasoning and accepted signals.
- **Owned data:** the future `strategy` schema with experiments, candidates, signals and reasoning; no bootstrap tables.
- **Public API:** synchronous evaluation and immutable accepted-signal views; exact contracts are TBD.
- **Allowed dependencies:** `kernel::api`, `marketdata::api`, `risk::api`, `wallet::api`.
- **Published/consumed events:** `SignalAccepted` and `CandidateRejected` are Roadmap candidates; schemas and consumers are TBD.
- **Invariants:** candidates are recorded before gating; strategy version is mandatory; deduplication has an explicit window; scores are not profit predictions; strategy never writes market-data tables.
- **Non-goals:** outcome valuation, real execution and provider ownership.
- **Main tests:** family rules, score/grade boundaries, risk-gate interaction, deduplication and reproducibility.
