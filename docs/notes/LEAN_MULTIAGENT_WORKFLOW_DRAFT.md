# Lean multi-agent workflow draft

## Status

This note preserves the final v1 benchmark workflow. It is non-normative and does not replace
`docs/AGENT_WORKFLOW.md`, `docs/AGENT_WORKFLOW_MULTIAGENT.md`, `AGENTS.md`, or the current
`.codex/config.toml` mode switch.

## Responsibility model

```text
Architect = WHAT + WHY + PLAN + REVIEW
Builder   = HOW + CODE + TESTS
Main      = PROCESS + CONTROL + ESCALATION
```

Only Builder writes production code and tests. Architect owns the contract and documentation, Reviewer owns
acceptance review, and Main owns routing, the independent final gate, and escalation. Main does not duplicate
Builder or Reviewer work. A role never treats its own statement that tests passed as completion evidence.

## Candidate configurations

### B: balanced

```text
Main                    gpt-5.6-terra / medium
Architect + Reviewer    gpt-5.6-terra / high
Developer + Tester      gpt-5.6-terra / medium
Escalation              gpt-5.6-sol / high, only when triggered
```

### C: lower-cost builder

```text
Main                    gpt-5.6-terra / medium
Architect + Reviewer    gpt-5.6-terra / high
Developer + Tester      gpt-5.6-luna / max
Escalation              gpt-5.6-sol / high, only when triggered
```

The two configurations use the same contract, workflow, invariants, gates, repair limit, and escalation
rules. The Builder model is the only planned experimental variable. During the benchmark, configuration B
always uses Terra Medium and configuration C always uses Luna Max, regardless of task risk. Risk-based model
routing is considered only after the benchmark.

## Main

Main owns process and control, not architecture or implementation.

- Set preliminary task risk before writer work starts.
- Dispatch phases and provide bounded task capsules.
- Validate the active OpenSpec change before implementation.
- Execute the complete final gate independently after review and documentation are complete.
- Route review findings and decide whether escalation is required.
- Keep workflow telemetry and the final completion decision.
- Do not write architecture documents, production code, or tests during the normal workflow.
- Do not independently redesign or re-review the implementation unless an escalation rule explicitly
  requires Main to adjudicate a bounded disagreement.
- Rerun a claimed RED only when Reviewer reports `red_suspect = true`.

## Architect + Reviewer

The Architect owns `WHAT`, `WHY`, the implementation plan, documentation coherence, and ROUTINE/STANDARD
review. The role uses `workspace-write` under one stable path policy; Main does not construct or verify a
per-task documentation allowlist.

Architect may write:

- the active `openspec/changes/<change>/**` tree;
- `docs/**` within the accepted task scope;
- `README.md` when the accepted task requires it.

Architect may not write:

- `src/main/**`, including migrations and production resources;
- `src/test/**`, fixtures, snapshots, and test configuration;
- `openspec/specs/**` outside a separate authorized sync or archive phase;
- `AGENTS.md`, `.codex/**`, `pom.xml`, or runtime/build configuration;
- governance documents or accepted ADR decisions unless the task explicitly authorizes that change.

Responsibilities:

- Create or update the OpenSpec proposal, delta specification, design, and tasks.
- Define acceptance behavior without prescribing a test-only implementation.
- Treat Main's risk as preliminary: Architect may upgrade it, never downgrade it.
- Classify test impact in the accepted contract as `RED_REQUIRED` or `RED_NOT_REQUIRED`, with a concise
  rationale.
- Record applicable core invariants and the implementation change budget.
- For ROUTINE and STANDARD work, review the stable production and test diff after Builder returns
  `BUILD_DONE`, including test quality, RED validity when required, unchanged frozen-test hashes, and targeted
  GREEN evidence.
- For CORE-RISK work, hand the contract directly to a fresh Reviewer and do not review the implementation.
- After `APPROVE`, update task checkboxes, evidence links, completion status, and non-semantic documentation.
- Never change production code or tests while resolving review findings.

## Test-impact routing

Architect records `test_mode` before Builder work starts:

```text
Observable behavior changes                              -> RED_REQUIRED
Pure refactoring or an internally covered mechanical edit -> RED_NOT_REQUIRED
Documentation-only work                                  -> RED_NOT_REQUIRED, no Builder phase
```

`RED_NOT_REQUIRED` includes a concrete reason and names the existing verification that protects the change.
It does not permit an observable behavior change to bypass behavioral RED. Builder may return `BLOCKED` with
`blocked_reason = TEST_SPEC_ERROR` or `CONTRACT_ERROR`, but may not downgrade `RED_REQUIRED` unilaterally.
Documentation-only work remains with Architect and proceeds directly to validation and Main's final gate.

## Developer + Tester

The Builder owns `HOW`, tests, production code, and bounded repairs. Test authoring and implementation remain
separate phases in one persistent agent thread.

### RED_REQUIRED tests phase

1. Read the accepted OpenSpec contract, applicable invariants, and approved plan.
2. Write the smallest meaningful tests, including PostgreSQL integration tests when required.
3. Run the exact targeted command and accept only a failure at the expected behavioral assertion as RED.
4. Record compact RED evidence, freeze the establishing tests, and continue directly to implementation.

The RED evidence records:

- changed test paths;
- content hash after RED;
- exact command;
- failing test and expected/actual assertion;
- the Git diff captured before production implementation.

Raw logs, timestamps, phase IDs, and a separate production-tree manifest are not required. The pre-
implementation diff must be preserved because the later working tree cannot reconstruct that uncommitted
state. This diff is required only for `RED_REQUIRED`. After RED, the named tests, expectations, fixtures,
discovery, and test configuration are frozen.

### RED_NOT_REQUIRED verification phase

For pure refactoring or an internally covered mechanical edit, Builder records the Architect's rationale,
runs the named existing targeted checks against the pre-change state when meaningful, implements the bounded
change, and reruns the same checks. No artificial failing test is created. Documentation-only work skips
Builder entirely. `RED_NOT_REQUIRED` does not require a pre-implementation diff.

### Implementation phase

1. For `RED_REQUIRED`, continue after recording RED evidence. For `RED_NOT_REQUIRED`, continue only under the
   recorded rationale and verification plan.
2. Modify production paths within the approved change budget.
3. Run targeted GREEN checks, verify the frozen-test hash when `RED_REQUIRED`, and return `BUILD_DONE` with
   concise evidence.
4. Do not modify frozen tests, expectations, fixtures, discovery, or test configuration.
5. Apply bounded production repairs routed from Reviewer findings. Two ordinary repair rounds are available
   without separate approval; a third requires explicit Reviewer authorization.

If a frozen test is wrong, Builder returns `BLOCKED` with `blocked_reason = TEST_SPEC_ERROR`. Reviewer may
issue `REPAIR` with `requires_new_red = true`; the replacement test then needs new RED evidence and a new
hash. This does not require Sol escalation. Production repair does not reopen tests automatically.

## Workflow states and attributes

The workflow uses only these states:

```text
PLAN_READY
BUILD_DONE
REPAIR
APPROVE
BLOCKED
ESCALATE
DONE
```

Risk, test handling, review routing, and failure details are result attributes rather than additional states:

```text
risk = ROUTINE | STANDARD | CORE_RISK
test_mode = RED_REQUIRED | RED_NOT_REQUIRED
tests_changed_after_red = true | false | not_applicable
blocked_reason = TEST_SPEC_ERROR | CONTRACT_ERROR | INFRASTRUCTURE | OTHER
reviewer = SAME_ARCHITECT | FRESH_TERRA_HIGH
requires_new_red = true | false
reason = CONTRACT_CHANGED | OTHER
```

## Core invariants

Use the stable invariant IDs and controlling-source links in `docs/CORE_INVARIANTS.md`. The checklist
consolidates existing project rules; it does not silently supersede an ADR, OpenSpec requirement, architecture
rule, or reproducibility rule.

Before `APPROVE`, Reviewer reports every applicable invariant using evidence rather than a blanket checkbox:

```text
Invariant | Applicable | Evidence | Verdict
```

## Change budget

Every Builder capsule states writable paths, frozen paths, accepted public contracts, expected scope, and
prohibited expansion. Unless explicitly approved, Builder must not:

- change architecture or module dependency direction;
- add or upgrade dependencies;
- modify unrelated modules;
- add a migration or change a public API;
- weaken, skip, narrow, or reconfigure tests;
- introduce fallback behavior absent from the specification;
- refactor beyond what the bounded implementation requires.

If the approved contract is insufficient, Builder returns `BLOCKED` with
`blocked_reason = CONTRACT_ERROR` instead of expanding scope.

## Review policy

For ROUTINE and STANDARD work, the same Architect thread performs the full review. Main sends a review capsule
that contains requirements, applicable invariants, stable diff, tests, the test-impact decision, and applicable
compact RED/GREEN evidence, without Builder advocacy or implementation rationale. Reviewer checks test meaning
before production details and does not infer correctness merely from a green command. In REVIEW, Architect
does not modify any file.

A fresh `gpt-5.6-terra / high` review thread is required for every CORE-RISK task and whenever Main classifies
a task as complex because it affects:

- persistence, transaction boundaries, or idempotency;
- concurrency, locking, retries, or duplicate suppression;
- migrations or schema ownership;
- ordering, reproducible identity, or point-in-time semantics;
- provider reconnect, degradation, data loss, recovery, or gap handling;
- cross-module contracts or architecture boundaries;
- security or integrity controls.

For CORE-RISK, the original Architect does not return after implementation. The fresh reviewer owns contract
conformance and the complete code, test, applicable RED/GREEN evidence, and invariant review.

The fresh reviewer is read-only and receives only the approved contract, applicable invariants, stable diff,
tests, the test-impact decision, and RED/GREEN evidence. It returns `APPROVE`, a consolidated `REPAIR`, or
`ESCALATE`.

Reviewer returns one consolidated set of findings per review. Builder has two ordinary repair passes without
separate approval, each followed by review. If a blocker remains, Reviewer may authorize exactly one third
repair without a user turn. If that repair fails, or Reviewer cannot define a bounded safe repair, the issue
escalates to Sol High. The workflow does not open an unbounded repair loop.

After `APPROVE`, Architect may update only completion status, task checkboxes, evidence links, and
non-semantic documentation. If Architect discovers that normative requirements, scenarios, acceptance
criteria, scope, or design decisions must change, it does not edit them. It returns `PLAN_READY` with
`reason = CONTRACT_CHANGED`; the semantic change is made in a new planning pass and the applicable review is
reopened. Main's final gate runs only after permitted documentation updates are complete.

## Sol escalation

Main may start a fresh `gpt-5.6-sol / high` Architect + Reviewer only for a bounded challenge when one of these
conditions holds:

- `BLOCKED` with a contract or design reason cannot be resolved from accepted project sources;
- the same substantive defect survives all three repair passes;
- Main and Terra Reviewer disagree on a release-blocking issue;
- an invariant violation or ambiguous transaction/concurrency/migration semantic remains;
- the change presents a credible data-loss, recovery, security, or architecture-boundary risk.

The Sol challenge is read-only. It receives one exact question plus the controlling contract, invariants,
diff, tests, and evidence. If its verdict requires planning changes, Main may open a separate documentation-
only repair phase under Architect's stable path policy. Sol never edits production code or tests.

## Risk routing

```text
ROUTINE
  Documentation, DTOs, mapping, local parsing, pure refactoring.
  Documentation-only work skips Builder. After benchmark policy is selected, other work may use Luna Max;
  Builder self-certifies RED when behavior changes, and same-thread Architect review is sufficient.

STANDARD
  Bounded business behavior with no core integrity trigger.
  Builder uses Terra Medium by default; Builder self-certifies RED, and same-thread Architect review is
  normally sufficient.

CORE-RISK
  Persistence, idempotency, concurrency, retry, ordering, migration, point-in-time,
  reconnect, data gaps, or recovery.
  Builder uses Terra Medium by default; fresh Terra High review is required.

VERY-HIGH / DISPUTE
  Credible data loss, architecture ownership change, unresolved invariant conflict, or reviewer disagreement.
  Main invokes bounded Sol High challenge.
```

An agent may raise risk but may not downgrade the risk class selected before implementation.

## End-to-end flow

```text
Main -> classify risk and define capsule
  -> Architect writes contract, plan, invariants, change budget, and RED_REQUIRED/RED_NOT_REQUIRED decision
  -> Main validates OpenSpec; workflow enters PLAN_READY
  -> documentation-only work skips Builder and proceeds to validation
  -> RED_REQUIRED Builder writes tests, records behavioral RED evidence, and freezes tests
  -> RED_NOT_REQUIRED Builder records rationale and existing verification without inventing a RED
  -> Builder implements and returns BUILD_DONE with targeted GREEN
  -> ROUTINE/STANDARD: original Architect performs the full review
  -> CORE-RISK: fresh Terra High performs contract, code, test, and invariant review
  -> Reviewer returns APPROVE, a consolidated REPAIR, or ESCALATE
  -> Builder may perform two ordinary repair rounds, each followed by review
  -> Reviewer may authorize one third repair, followed by review
  -> a blocker remaining after repair round 3 escalates to Sol High
  -> Sol High challenge only on an escalation trigger
  -> after APPROVE, Architect updates only status, checkboxes, evidence links, and non-semantic documentation
  -> a required semantic change is not edited; it returns PLAN_READY with reason = CONTRACT_CHANGED
  -> Main independently runs the complete final gate and returns DONE
```

If the final gate fails, route an implementation or test issue to Builder `REPAIR`, a documentation or
contract issue to Architect, and an infrastructure issue to `BLOCKED`. The failure consumes the next available
repair round; after two ordinary rounds Reviewer may authorize round 3, and any blocker surviving round 3
escalates to Sol High.

## Evaluation data

For comparison of configurations B and C, record total wall time and aggregate usage across Main, Architect,
Builder, fresh review, repairs, and any Sol escalation. Score authored test quality separately from behavior,
including behavioral RED evidence, real infrastructure, deterministic concurrency control, durable-state
assertions, rollback evidence, retry behavior, and resistance to controlled test mutations.
