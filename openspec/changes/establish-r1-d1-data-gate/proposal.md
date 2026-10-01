## Why

R1 is frozen at `1.0.0`, but no inventory proves that its required historical fields, export costs and retention permissions are available. D1 must establish those facts before extraction; a decoded trade table cannot establish historical depth or complete executable pricing inputs.

## What Changes

- Establish one D1 stage change with an incremental, outcome-blind inventory contract and an explicit review barrier before extraction.
- First implementation slice: a dependency-free offline inventory validator, candidate inventory and deterministic blocked/readiness report. It performs no provider calls and authorizes no run.
- Distinguish documented capabilities from measured coverage and explicit unavailable/unverified fields; preserve every unresolved prerequisite.
- Require a new Architect PLAN before extraction tooling or data runs, recording confirmed sources, exact query/export versions, retention evidence, output location and finite ceilings within frozen `OD-2`.
- Preserve the frozen full-envelope D1 thresholds; later extraction and gate implementation remain unchecked tasks in this same stage change.

## Capabilities

### New Capabilities

- `r1-data-inventory`: Bounded offline validation and reproducible reporting of required-field availability, source evidence, retention and costs, plus the extraction authorization barrier.

### Modified Capabilities

None. The accepted `research-protocol` and `research-reproducibility` remain controlling; this change implements their inventory obligation without changing frozen methodology.

## Impact

- Research-only files under `tools/research/r1/` and task documentation under `docs/research/`; no application module is modified.
- Reuse the repository's existing Node tooling and built-in test runner; no new package, production dependency, service, database, migration or module edge.
- Non-goals: provider selection for live ingestion, credentials, paid calls, schema probes, bulk extraction in the first slice, reconstruction or gate calculations, calibration edits, P1 outcomes, wallet ranking, execution, and changes to frozen R1/RED/golden evidence.
