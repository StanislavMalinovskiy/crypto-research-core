## Context

See proposal.md. The starting worktree was clean at `cc1c791`; `[agents].enabled = true` remains unchanged. The owner explicitly authorizes this change to be planned, implemented, self-reviewed and closed by one agent without subagents. This task-local exception is not a new workflow rule.

Risk: CORE_RISK (owner decision); risk_triggers: TR-11, workflow/test-integrity rules. Test mode: RED_REQUIRED. No application modules, transaction boundaries, dependencies or infrastructure change.

## Goals / Non-Goals

Add exactly the three requirements in the delta and minimal mechanical presence checks. No new roles, states, scripts, evidence formats or model-routing policy. No changes to DEFAULT or other active changes.

## Decisions

- Keep exactly three ADDED requirements, one scenario each. The RED requirement explicitly qualifies the older suspected-test-defect referral for pre-freeze mistakes; accepted frozen-test behavior and semantic-contract escalation remain unchanged. Update corresponding prose locally rather than replacing the large existing guidance requirement.
- Put overlap and session recovery in the MULTIAGENT workflow; put the RED reason rule beside frozen-test guidance in TESTING. Architect gets one overlap instruction; all three Builder roles get one RED instruction and a minimal qualification of the existing test-defect sentence.
- Add three tests to the existing RepositoryConventionsTest using short token presence checks in the specified documents and roles. No full-text matching or dependency on a pre-archived accepted spec. Observe expected missing-rule assertion failures before documentation/role edits, then freeze this test file.
- Session replacement is dispatched only by Main; it does not let Builder become Reviewer or reset repair count. Existing role/model/effort and independent review restrictions continue to apply.

## PLAN_READY overlap handoff

`openspec list` and active delta paths inspected before implementation:

- `adopt-gpt6-agent-routing` touches `repository-conventions`: **safe to proceed** for this additive change. Its old routing delta disagrees with the newer accepted lean workflow and must not be archived unchanged; this task neither applies nor edits it. The three added requirement names are disjoint from its requirements. Future archive of that older change requires reconciliation against accepted guidance.
- `add-recorded-replay-operational-telemetry`, `harden-evaluation-report-retry-semantics`, `define-solana-data-provider-contract`: no shared spec; left untouched.

## Risks / Trade-offs

- Mechanical text checks cannot prove agents follow the rules: self-review checks meaning and alignment; no semantic parser is introduced.
- Self-review is not fresh independent review: record the owner's task-specific exception and do not claim otherwise.
- Pre-freeze correction could be mistaken for permission to change requirements: explicitly retain CONTRACT_CHANGED for contract defects and TEST_SPEC_ERROR after freeze.

## Migration Plan

Run test-first RED, verify per-test reasons, freeze, then edit docs/roles and run targeted GREEN. Self-review the stable diff under the owner's exception. Run integrity preflight, full Maven verify with Docker, strict all-item validation, doctor and diff check. Record a path-scoped raw-byte Git checkpoint with a temporary index before CLI archive; keep the branch and live index unchanged. Run strict validation, doctor, RepositoryConventionsTest and diff check after archive. If needed, restore only archive-touched paths via Git from the checkpoint and remove only this new archive copy. Never use reset-hard or touch unrelated changes.
