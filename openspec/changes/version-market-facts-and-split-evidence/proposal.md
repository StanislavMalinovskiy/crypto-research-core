## Why

The current evaluation requires the signal and run to name one dataset fingerprint. A dataset frozen at the signal decision cannot contain facts that arrive for the later `1h` outcome, while a dataset frozen later can make decision evidence depend on the future. The current event-key-only normalized, price, liquidity and USD stores also cannot retain a corrected derivation or second provider's evidence for one canonical event without an immutable conflict. These two defects meet at the dataset member: a reproducible decision and outcome must pin exact fact revisions.

## What Changes

- Introduce append-only, revision-addressable normalized swap, price, liquidity and USD facts in `marketdata`, with explicit source lineage, exact content, equal-retry handling and conflicting same-revision rejection. A USD fact pins the exact converted and quote price revisions.
- Add versioned, bounded immutable market-data snapshots whose members pin exact fact revisions. Within the freeze transaction, independently verify complete coverage of visible canonical facts in the declared asset/window/fact-kind scope: each is included through exactly one revision or explicitly excluded with policy-permitted reason/evidence; omissions reject publication. The canonical fingerprint and manifest retain scope, cutoffs, availability/exclusion policy, ordered members/exclusions and coverage counts. Separate decision and later evaluation snapshots may be shared by a bounded batch; the limit is not a wallet-history limit.
- Introduce a new signal/evaluation evidence contract: the accepted signal retains only decision evidence; the `1h` evaluation run retains that same decision identity and a separately frozen later evaluation identity in dedicated decision/evaluation fingerprint columns of new owned v2 tables. Existing `dataset_fingerprint` columns retain only their legacy meaning. Entry and horizon selection uses only eligible facts in the evaluation snapshot. No after-cutoff fact rewrites the signal or an earlier run.
- Preserve legacy V1–V9 rows, snapshots, signal IDs, run IDs, outcomes, reports, exact readback and existing fingerprint algorithms. New identities and policies are explicitly versioned; migrations are forward-only and additive. Compatibility is proved against persisted pre-migration evidence, not inferred from a fresh fixture alone.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `marketdata-storage`: versioned fact identity and exact USD price-revision lineage.
- `recorded-market-replay`: revision-addressed normalized replay and immutable, bounded selection snapshots.
- `signal-evaluation`: distinct decision and evaluation evidence for chronological `1h` outcomes, with legacy replay compatibility.
- `research-reproducibility`: versioned dual-evidence provenance and stable replay after late commit or backfill.

## Impact

Affected modules are `marketdata`, `signal` and `evaluation`, through their existing named public APIs. `kernel` identities and the six-module dependency DAG remain unchanged. Future implementation needs module-owned forward Flyway migrations after V9, PostgreSQL integration tests, versioned API and fingerprint/read branches, and synchronized affected Architecture/ADR/module/Reproducibility/Testing documentation at the accepted change. No new production dependency, database, provider adapter, live ingestion, gap recovery, wallet/copier strategy, numerical trading policy, horizon other than `1h`, PAPER/LIVE execution or general multi-provider arbitration is included. The manual minutes/hours/days operating game remains a research hypothesis; acceptable entry delay is family-specific and must be measured outside this change.
