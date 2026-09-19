# risk

- **Responsibility:** synchronous assessment of validated point-in-time token risk facts into `BLOCK`, `WATCH_ONLY` or `ALLOW`.
- **Owned data:** none in the first slice; no `risk` schema exists. The immutable facts and decision are copied into signal-owned evidence.
- **Public API:** `RiskApi` accepts an asset, explicit cutoff, manipulation-flag count, lifecycle, exact liquidity and evidence version, and returns the decision with normalized facts and reasons.
- **Allowed dependencies:** `kernel::api`, `marketdata::api`.
- **First-slice rule:** two or more manipulation flags, lifecycle before `DISCOVERY`, or liquidity below USD `30000.00000000` is `BLOCK`; one flag is `WATCH_ONLY`; otherwise validated `DISCOVERY` or later evidence is `ALLOW`.
- **Invariants:** assessment is pure and synchronous, liquidity uses exact scale 8, no machine clock is read, unknown/blank evidence versions fail fast and the result preserves its point-in-time cutoff.
- **Non-goals:** enrichment providers, durable risk history, signal scoring, wallet performance and outcome evaluation.
- **Main tests:** exact `ALLOW`, `WATCH_ONLY` and `BLOCK` boundaries, invalid evidence, module boundaries and end-to-end preservation of risk evidence in the immutable signal snapshot.
