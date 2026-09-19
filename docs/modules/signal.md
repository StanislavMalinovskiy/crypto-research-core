# signal

- **Responsibility:** cutoff-safe `LIQUIDITY_SPIKE` detection, candidate-first risk gating and immutable accepted ENTRY signal evidence for the first slice.
- **Owned data:** `signal.signal_candidates` and `signal.accepted_signals`. Candidates have an explicit deduplication identity, status and version/fingerprint evidence; accepted signals retain source normalized identities, decision-time risk facts, exact score/confidence and versioned JSONB reasoning.
- **Public API:** `SignalApi` accepts a dataset fingerprint, asset, decision window/cutoff, validated risk facts and detector/scorer/configuration identities; it returns immutable candidate and accepted-signal snapshots and supports accepted-signal lookup.
- **Allowed dependencies:** `kernel::api`, `marketdata::api`, `risk::api`, `wallet::api`; the implemented path uses only the first three.
- **Transactions:** market-data reads happen before writes. A short signal transaction commits or resolves `DETECTED`; risk assessment runs outside it; a second signal transaction atomically records `REJECTED` or `ACCEPTED` and the optional accepted snapshot.
- **First-slice rule:** latest decision-time liquidity must be at least 1.50 times the deterministic one-hour baseline and at least USD `10000.00000000` higher. `ALLOW` produces score `70`, grade `B`, confidence `1.0000` and factors `20 + 30 + 20`.
- **Invariants:** public window, decision and risk cutoffs are normalized to microseconds before validation, identity, querying and persistence; no observation after the decision cutoff participates; risk evidence must match the selected asset, cutoff and decision-time liquidity; equal detection is idempotent and immutable conflicts fail; signal never writes or directly reads another module's tables.
- **Non-goals:** other signal families, wallet-derived scoring, provider ownership, shadow outcomes, statistical edge claims, virtual positions and execution.
- **Main tests:** threshold boundaries, no-evidence behavior, later-data exclusion, risk rejection, candidate-first durability, immutable scoring/lineage, equal retry and PostgreSQL V3 ownership/constraints.
