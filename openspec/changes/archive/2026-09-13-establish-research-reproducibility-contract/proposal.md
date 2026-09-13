## Why

Signal evaluation is only credible when the same point-in-time inputs, code and configuration produce the same computational result. The contract must be fixed before chain identities, amounts, prices, signals and evaluation records acquire incompatible time, numeric or provenance semantics.

## What Changes

- Define reproducibility as deterministic output for the same dataset snapshot, code/build identity, algorithm version, configuration and seed.
- Require UTC `Instant` semantics and injected `Clock` rather than implicit wall-clock reads in domain and application logic.
- Define exact numeric rules for token amounts, prices, PnL and ratios, including explicit precision, scale and rounding; prohibit binary floating point for authoritative financial values.
- Require research/evaluation provenance to identify code/build/commit, algorithm and configuration versions, data cutoff, dataset fingerprint and optional seed.
- Require deterministic ordering and explicit tie-break rules for computations whose inputs are not inherently ordered.
- Extend repository verification with portable guards against implicit production clock and unseeded randomness entry points.
- Add a focused architecture document and ADR, and link them from active navigation and module guidance.

## Capabilities

### New Capabilities

- `research-reproducibility`: Defines observable time, numeric, provenance and deterministic-execution guarantees for research and evaluation.

### Modified Capabilities

- `repository-conventions`: Adds automated source-level guards for implicit time and randomness in production module code.

## Impact

- **Affected modules:** policy applies to `kernel`, `marketdata`, `risk`, `wallet`, `signal` and `evaluation`; this change modifies documentation and repository tests only, not their package boundaries or public APIs.
- **Affected files:** architecture/agent/module documentation, a new reproducibility contract and ADR, OpenSpec specs, and repository convention tests.
- **Dependencies and infrastructure:** none added or changed.
- **Non-goals:** no Java domain types, database tables or migrations; no algorithms, signals, providers, fixtures or reports; no schema/precision constants chosen for future fields; no module-DAG, deployment, transaction or runtime-role changes.
