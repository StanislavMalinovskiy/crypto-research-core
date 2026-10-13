## Why

**Current LOCAL qualification, 2026-10-13:** [fresh independent APPROVE](design.md#reviewed-local-qualification-2026-10-13), exact14 hashes, targeted30 and unchanged regression99/0failures/errors/skips/exits0. Main full gate02 PASS798/0failures/errors/skips/all721inputs unchanged; all5tasks verified. Qualified JAR copied to external evidence only; scoped commit/push `68c6b92be647664373e0bd3729bc55edbb546c34` verified/local+origin identical; LinuxCI38353522644 SUCCESS798/70suites/0failures/errors/skips and portableNode744PASS/0failed/cancelled/skipped/todo/exit0. Bridge APIs only/not deployed; the adjacent screen is separately LOCAL verified with Main full gate01 PASS810/74suites/0failures/errors/skips and scoped commit-push pending, not a working production UI or current capture interruption.

The qualified v5 pool-net candles/native-SOL diagnostic accepts recorded batches, while continuous capture still publishes fixed-v4 evidence. The first screen needs an explicit bounded on-demand route from retained capture facts without changing capture or claiming accepted strategy/evaluation evidence.

## What Changes

- Add a synchronous marketdata-owned read-only retained-window source boundary: one session, declared received-time scope, exact enrolled pool bindings, consistent bounded raw/proof/disposition snapshot.
- Derive selected `pumpswap-pool-legs-v5` on demand through existing versioned candles and existing native-SOL diagnostic, with explicit actual read time and actual post-calculation derivation time D; the NEW bridge uses K=D only, no historical-cutoff request branch.
- Add a signal facade that carries capture incompleteness and returns an effective diagnostic status separately from the unchanged pure diagnostic. No second source read or double-counted v4/v5 interpretation.
- Hard limits reject dense/invalid/inconsistent reads, never truncate; observed signature/byte diagnostics explicitly qualify EXACT/AT_LEAST/UNKNOWN rather than invent full counts. Existing fixed-v4 publisher, V14 rows/receipts, capture cursors/defaults/authority and legacy APIs remain unchanged.

## Capabilities

### New Capabilities

- `pumpswap-retained-window-diagnostic`: retained-source snapshot and nonadmitted on-demand diagnostic bridge.

### Modified Capabilities

None. Accepted marketdata-storage/signal-evaluation requirements remain unchanged; no completed offline change is expanded.

## Impact

Only additive marketdata/signal APIs, application use cases, one marketdata SQL reader and isolated tests; exact paths/contract in [design](design.md). No migration, dependency, module edge, endpoint, scheduler, runtime configuration, current deployment or capture interruption. Owner standing scope plus Main's explicit on-demand clarification authorizes planning; Main owns BUILD/activation separately.

No active collector restart/deploy during its first24h; implementation provides programmatic APIs only, not an invocation CLI/endpoint/wrapper or already-working UI. Any later standalone use is a separate operational step under [design's restrictions](design.md).

Non-goals: new durable v5 publication, accepted ENTRY, USD/liquidity/risk bypass, evaluation/outcomes, screen implementation, R1/holdout/admission, rare39 repair, pool expansion, trading, paid/provider/server calls, new queue/engine/registry or capacity/month-fit claims.
