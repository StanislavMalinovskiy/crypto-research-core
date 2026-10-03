## Why

R1 is frozen at `1.0.0`. The repository already retains measured SQD/public RPC reconciliation and Alchemy PAYG historical-account receipts, but those bounded samples do not prove the complete required-field inventory, full-envelope coverage, export costs or retention permissions. D1 must establish those facts before bulk extraction; a decoded trade table or one successful reserve sample cannot establish historical depth or complete executable pricing inputs.

## What Changes

- Establish one D1 stage change with an incremental, outcome-blind inventory contract and an explicit review barrier before extraction.
- First implementation slice: a dependency-free offline inventory validator, candidate inventory and deterministic blocked/readiness report. It performs no provider calls and authorizes no run.
- Distinguish documented capabilities from measured coverage and explicit unavailable/unverified fields; preserve every unresolved prerequisite.
- Reuse the retained provider evidence for source admission: Alchemy Account Archive, SQD and independent public RPC are evidenced candidates; Dune is not connected. The owner-selected evidence path is `C:\crypto-research-evidence\r1-d1`. The owner declined Alchemy PAYG D1 spending for now on 2026-10-01; no new paid call or provider execution is authorized by this correction.
- Next implementation slice: update only the offline candidate JSON and add separate behavioral regression tests for those evidenced candidates and preserved unresolved boundaries. Do not modify the frozen validator tests or implementation.
- Restrict the proposed R1 8.1 sample to volume/cost feasibility for the complete filtered six-month candidate-wallet and token-pool envelope, including the extraction tail and discovery overhead. Stop before spending if the estimate exceeds OD-2; unknown bounds remain blocked. Alchemy PAYG is denied; no provider calls precede a new implementation-ready PLAN and required review/gate/run preconditions.
- Plan reserve inputs from vault-account balances immediately before/after each transaction, retaining missing tick/bin-state venues in the full frozen 8.2 trigger denominator. Classify tips, exact SOL/USD and measured visibility latency as `UNAVAILABLE` for next-run admission until an exact supported source is evidenced; do not alter the existing candidate JSON or frozen thresholds in this planning slice.
- Require a new Architect PLAN before extraction tooling or data runs, recording confirmed sources, exact query/export versions, retention evidence, output location and finite ceilings within frozen `OD-2`.
- Preserve the frozen full-envelope D1 thresholds; later extraction and gate implementation remain unchecked tasks in this same stage change.
- Owner-authorized 2026-10-03 next slice: implement and run a distinct `EXPLORATORY` public SQD probe under offline-procedure section 11, with behavioral RED/GREEN and one fresh independent review, targeted checks instead of a full Maven gate for this probe only. Exclude the entire August 31–September 28 period even from raw fetches/counts. Measure fixed preholdout samples and explicit cost sensitivities/unknowns without completing inventory, D1, calibration or confirmatory extraction prerequisites.

## Capabilities

### New Capabilities

- `r1-data-inventory`: Bounded offline validation and reproducible reporting of required-field availability, source evidence, retention and costs, plus the extraction authorization barrier.

### Modified Capabilities

None. The accepted `research-protocol` and `research-reproducibility` remain controlling; this change implements their inventory obligation without changing frozen methodology.

## Impact

- Research-only files under `tools/research/r1/` and task documentation under `docs/research/`; no application module is modified.
- Reuse the repository's existing Node tooling and built-in test runner; no new package, production dependency, service, database, migration or module edge.
- Non-goals: provider selection for live ingestion, credentials, paid calls, confirmatory source runs or bulk extraction in this slice, reconstruction or gate calculations, calibration edits, P1 outcomes, wallet ranking, execution, and changes to frozen R1/RED/golden evidence.
