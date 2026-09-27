## Why

The current MULTIAGENT process applies OpenSpec, archive preparation and broad review evidence to low-risk internal work, and uses cryptographic test-freeze evidence where semantic review is the actual safeguard. Introduce owner-authorized task tiers to reduce that overhead while keeping all CORE_RISK triggers, independent review, complete gates and archive recovery protections.

## What Changes

- Add a strictly bounded Main-only TRIVIAL text route; all other work still goes through Architect planning and trigger assessment.
- Separate procedural tiers NORMAL, CONTRACT and CORE_RISK from the unchanged ROUTINE/STANDARD/CORE_RISK Builder routing. Evaluate all existing TR triggers before selecting a non-TRIVIAL tier.
- Exempt TRIVIAL and NORMAL from OpenSpec creation, overlap checks, DOCS_CLOSE and archive. Retain complete OpenSpec closure for CONTRACT and CORE_RISK.
- Use same-Architect review and touched-invariant evidence for NORMAL/CONTRACT; require fresh Reviewer and the full invariant matrix for CORE_RISK. Remove the separate fresh-review condition based solely on an accepted normative-spec change.
- Require valid RED for CORE_RISK and every bugfix; let Architect justify other CONTRACT test modes and select useful NORMAL tests. Replace only RED/freeze hashes and pre-implementation-diff evidence with semantic freeze and explicit assertion/expected/actual/change reporting.
- Make assignment telemetry opt-in for benchmark/debug/explicit measurement; retain compact, truthful gate summaries and full captured output without a new wrapper.
- Reconcile active guidance, role bodies, applicable skill routing and existing convention checks through a traceable rule-change ledger.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: tiered MULTIAGENT procedure, semantic test freeze, review scope, conditional archive and gate-output contract.

## Impact

Repository governance and `RepositoryConventionsTest` only; no product module, API, persistence, transaction, dependency or infrastructure changes. Exact files and rule changes are in `design.md`.

This task itself is CORE_RISK because workflow and test-integrity rules are affected (TR-11). The initial PLAN-only request stopped before implementation; the owner subsequently authorized apply. Behavioral convention RED, targeted GREEN and fresh Reviewer APPROVE are recorded in `design.md`; full-gate and archive completion remain separate obligations.

Non-goals: changing DEFAULT, model routing/effort, `.codex/config.toml`, TR definitions, repair/escalation/status semantics, worktree requirements, archived history, production code, scripts, CI configuration or new state/evidence infrastructure. Preserve the completed instruction-diet routes except where the newly authorized semantics expressly supersede them. Archive/checkpoint/recovery hashes remain mandatory and unchanged.
