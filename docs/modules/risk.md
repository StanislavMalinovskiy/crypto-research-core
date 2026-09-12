# risk

- **Responsibility:** validated token risk facts and `BLOCK`, `WATCH_ONLY` or `ALLOW` decisions.
- **Owned data:** the future `risk` schema with append-only token risk decisions and chain-specific validated facts; no bootstrap tables.
- **Public API:** synchronous point-in-time risk decision lookup/evaluation; exact contracts are TBD.
- **Allowed dependencies:** `kernel::api`, `marketdata::api`.
- **Published/consumed events:** `RiskDecisionCreated` is a Roadmap candidate; its schema and consumers are TBD.
- **Invariants:** chain-specific JSONB originates from validated domain types; unknown schema versions fail fast; BLOCK candidates remain measurable.
- **Non-goals:** signal scoring, wallet performance and provider ingestion outside risk enrichment.
- **Main tests:** rule matrices, JSONB round trips, point-in-time decisions, provider degradation and module boundaries.
