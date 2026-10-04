## Why

The verified 2026-10-04 audit found ambiguous tuple comparisons, inconsistent timestamp normalization and missing portable research-tool CI coverage. Repair these guarantees in priority order during the owner's bounded work window without rewriting historical evidence or delaying the independent R1-E2/D1 critical path.

## What Changes

- B1: typed snapshot completeness/revision/exclusion comparisons; retain exact existing snapshot fingerprint serialization.
- B2: normalize authoritative versioned request instants before validation, identity and queries, while retaining stored manifest readback.
- B3: Node 24 portable offline/synthetic CI with explicitly separate local-evidence tests.
- B4: new explicit version for unambiguous price/liquidity revision dimension encoding, with old revision lookup preserved; typed persisted-exclusion comparisons.
- B5/B6: a new versioned census admission parser and helper library, signature/content validation, pre-decode layout bounds and one page hash; read-only historical relationship audit. Historical pinned tools remain untouched.
- B7: replace brittle wording assertions with semantic process checks while preserving configuration, reference and independent-review guards.

## Capabilities

### New Capabilities

- `research-tool-integrity`: prospective versioned census admission and offline historical audit with immutable source evidence.

### Modified Capabilities

- `marketdata-storage`: explicitly component-safe snapshot dispositions and independently versioned revision dimensions with immutable historical compatibility.
- `research-reproducibility`: versioned request normalization and recorded-version readback.
- `ci-quality-gate`: mandatory portable Node research verification and explicit local-evidence separation.

## Impact

Affected application modules: marketdata and signal, their existing PostgreSQL tests, research tooling and repository/CI checks. No production dependencies, module/DAG changes, database rewrite, provider calls, paid access, census rerun, outcome analysis or holdout access. Calibration/P1 are outside the owner's 12-hour window. Uncompleted work remains explicitly open at STOP, not falsely archived as complete.
