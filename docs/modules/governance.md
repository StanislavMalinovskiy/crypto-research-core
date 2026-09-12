# governance

- **Responsibility:** execution modes (`RESEARCH`, `PAPER`, `LIVE`), dangerous-capability gates, policy decisions and audit semantics when execution work exists.
- **Owned data:** a future `governance` schema may own execution-mode and auditable policy-decision state; bootstrap creates neither schema nor table.
- **Public API:** synchronous gate decisions; exact contracts are TBD for the governance change.
- **Allowed dependencies:** `kernel::api`.
- **Published/consumed events:** TBD; no event schema is fixed by bootstrap.
- **Invariants:** default-deny for real execution; decisions are auditable; no provider-specific types in contracts.
- **Non-goals:** global orchestration, general security, shared validation, all cross-cutting concerns, order execution, strategy scoring and premature LIVE/PAPER behavior.
- **Main tests:** LIVE rejection, mode transitions, decision auditability and module boundaries.
