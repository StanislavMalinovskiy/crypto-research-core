## Why

Recorded replay already returns normalized and failed item results, but operators have no aggregate attempt counters or bounded structured completion diagnostics. This small F3.4 preparation slice makes the existing recorded path observable before live ingestion without changing its research or storage semantics.

## What Changes

- Add process-local Micrometer counters for recorded replay invocations and attempted items, with a fixed outcome vocabulary.
- Emit one constant-schema SLF4J summary when a non-null replay invocation completes or aborts with a runtime exception, including partial progress on abort.
- Count repeated processing as operational attempts, independently of durable domain deduplication.
- Keep diagnostics best-effort: telemetry runtime failures do not replace the replay result or original replay exception.

Non-goals: live-provider ingestion, provider selection, gap detection, freshness, quotas, alerts, HTTP/CLI/scheduled triggers, endpoint exposure, exporters, logging configuration, new dependencies, persistence or transaction changes, retry policies, migrations, financial calculations, research reports, identities, ordering, and module-boundary changes. This change does not complete F3.4 monitoring or the F3 data-quality gate.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `recorded-market-replay`: add fixed-cardinality operational counters and bounded replay summaries while retaining existing results and failure behavior.

## Impact

Only the `marketdata` module's recorded replay application implementation and focused unit tests change. Existing Actuator/Micrometer and SLF4J dependencies supply the implementation; public module records, database stores, schema, runtime configuration, and endpoint exposure remain unchanged. Existing raw-first, conflict, retry and deterministic replay tests remain regression evidence.
