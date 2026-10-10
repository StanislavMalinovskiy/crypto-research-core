## Why

The verified offline decoder separates 68 operations from the saved 484bec54 window, but its results are transient. Durable, source-linked transaction dispositions and immutable operation revisions are needed before any later quality or admission work can refer to exact derivations.

## What Changes

- Add a synchronous bounded decode-and-persist API using the existing verified decoder and already retained raw transaction evidence.
- Preserve every transaction/invocation disposition, exact operation proof and decoder/schema/source-pin lineage in append-only marketdata tables.
- Separate provider-independent canonical operation identity, source-specific derivation revision and immutable batch membership/limitations. Equal re-decode is idempotent; conflicts fail and later versions coexist.
- Read an explicitly named persisted batch/revision without automatic newest-version selection or market-data admission.

## Capabilities

### New Capabilities

- `pumpswap-operation-persistence`: immutable bounded saved-raw derivation storage, complete dispositions and exact versioned readback.

### Modified Capabilities

None. Existing raw storage, pure decoder, capture, market facts, snapshots and research requirements retain their meaning.

## Impact

Only marketdata API/application/persistence, additive V14 Flyway migration and owning tests, plus exact existing compatibility-test migration/schema-list additions assigned in the [design amendment](design.md#source-proven-additive-migration-setup-amendment-2026-10-11). Historical data/fingerprint oracles remain unchanged. No production dependency, module edge, runtime trigger or general architecture decision. CORE_RISK / RED_REQUIRED with fresh independent Reviewer; detailed handoff is in [design](design.md).

Owner's latest instruction to perform further tasks without repeated permission supersedes the earlier decoder documentation's PLAN-only next-step checkpoint prospectively: implementation follows valid PLAN_READY through Main. This grants no new paid capture, deployment, pool expansion, managed database backfill, R1 restart, holdout/outcome work or admission. Qualification uses only immutable saved session `484bec54-c92b-438f-9fe0-89f1c8150844` offline in isolated PostgreSQL; original evidence and all cumulative resource accounting remain unchanged.

Non-goals: new parser support, fee/lifecycle reinterpretation, USD/price/liquidity normalization, signal/evaluation integration, D1/P1, research re-selection, production bootstrap/CLI/scheduler, managed database mutation, collector repair/relaunch, archive or accepted-spec synchronization in PLAN.
