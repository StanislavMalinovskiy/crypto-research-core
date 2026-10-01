## Why

R1 is frozen at `1.0.0`. The repository already retains measured SQD/public RPC reconciliation and Alchemy PAYG historical-account receipts, but those bounded samples do not prove the complete required-field inventory, full-envelope coverage, export costs or retention permissions. D1 must establish those facts before bulk extraction; a decoded trade table or one successful reserve sample cannot establish historical depth or complete executable pricing inputs.

## What Changes

- Establish one D1 stage change with an incremental, outcome-blind inventory contract and an explicit review barrier before extraction.
- First implementation slice: a dependency-free offline inventory validator, candidate inventory and deterministic blocked/readiness report. It performs no provider calls and authorizes no run.
- Distinguish documented capabilities from measured coverage and explicit unavailable/unverified fields; preserve every unresolved prerequisite.
- Reuse the retained provider evidence for source admission: Alchemy Account Archive, SQD and independent public RPC are evidenced candidates; Dune is not connected. The owner-selected evidence path is `C:\crypto-research-evidence\r1-d1`. The owner declined Alchemy PAYG D1 spending for now on 2026-10-01; no new paid call or provider execution is authorized by this correction.
- Next implementation slice: update only the offline candidate JSON and add separate behavioral regression tests for those evidenced candidates and preserved unresolved boundaries. Do not modify the frozen validator tests or implementation.
- Propose a finite source-admission sample using only verified zero-paid options after another implementation-ready PLAN and review; Alchemy PAYG requires a future explicit owner decision. Samples cannot substitute for frozen full-envelope D1 thresholds or establish D1 passage.
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
