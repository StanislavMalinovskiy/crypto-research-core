## Why

Bounded reconnect and explicit incomplete intervals already work for the agreed MVP. Operators still lack a compact coherent read that distinguishes recorded `RUNNING` from an expired lease or ended window without loading replay payloads or every gap.

## What Changes

- Add one synchronous immutable, payload-free capture monitor snapshot for an explicitly named session.
- Expose recorded state/reason, lease/window facts, existing durable counters/disposition counts/committed positions and complete unresolved-gap counts with at most100 interval details and explicit truncation.
- Keep observation facts separate from process/provider health, continuity, freshness, billing and admission claims; perform no corrective writes or provider work.

## Capabilities

### New Capabilities

- `bounded-capture-monitor`: bounded coherent read-only visibility of retained capture sessions and explicit incomplete intervals.

### Modified Capabilities

None. Operational health, existing capture status/replay and operation-persistence semantics remain unchanged.

## Impact

Only marketdata API/application/persistence projection and owning tests, using existing Java25/JdbcClient/PostgreSQL baseline. No migration, dependency, module edge or architecture decision. Shared API/service paths may be implemented only after the operation-persistence Builder releases them; this proposal neither changes that active contract nor claims its qualification/review complete.

Owner continuation authorizes Main-managed implementation after valid PLAN_READY. It grants no paid/live run, managed database mutation, deployment, pool expansion, R1 restart or admission. Non-goals: collector/reconnect repair, WS1011 cause investigation, zero loss, raw repair/backfill, new parser support, operation-version auto-selection, HTTP/Actuator endpoints, scheduler, exporter, alert infrastructure or full F3. Planning edits only this new active change; no archive.
