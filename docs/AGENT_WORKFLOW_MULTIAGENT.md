# MULTIAGENT: lean supervised workflow v1

## Purpose and activation

This is the normative workflow when `.codex/config.toml` contains the exact Boolean setting
`[agents].enabled = true` at task start. Main coordinates the workflow, communicates with the user, and is the
only role that spawns project subagents. Subagents never spawn other subagents.

Coordination uses native Codex project agents from `.codex/agents/**`, or `codex queue` when the user has
already created named Codex sessions. Orca and `orca-cli` are outside this workflow and must not be invoked
unless the user explicitly requests Orca in the current task.

The workflow minimizes agent turns while retaining independent review for integrity-sensitive work. Main
reports to the user at major phase boundaries or blockers, not after every internal action.

## Roles and benchmark configurations

```text
Main                  gpt-5.6-terra / medium
Architect             gpt-5.6-terra / high
Builder B             gpt-5.6-terra / medium
Builder C             gpt-5.6-luna / max
Fresh Reviewer        gpt-5.6-terra / high
Escalation            gpt-5.6-sol / high
```

During a B-vs-C benchmark, Builder is the only model variable: B always uses `builder_terra`, and C always
uses `builder_luna`. The contract, base state, workflow, evidence requirements, reviewer routing, checks, and
repair limit remain identical. Risk-based Builder selection is allowed only after the benchmark policy is
chosen.

## Main

Main owns PROCESS, ROUTING, FINAL GATE, and ESCALATION. Main:

- sets the preliminary task risk as ROUTINE, STANDARD, or CORE_RISK;
- invokes Architect for planning and dispatches one bounded capsule per phase;
- invokes the configured Builder after `PLAN_READY`;
- routes ROUTINE/STANDARD review back to the same Architect thread;
- creates a fresh Reviewer thread for CORE_RISK;
- permits two ordinary repair passes without separate approval and routes a Reviewer-authorized third repair;
- invokes Sol High only for a bounded escalation trigger;
- independently runs the complete final gate and returns `DONE`;
- does not redesign, implement, or duplicate normal review work;
- reruns a claimed RED only when Reviewer returns `red_suspect = true`.

## Architect

Architect owns WHAT, WHY, PLAN, applicable invariants, test impact, and documentation. In PLAN, Architect:

- reads the required project sources and inspects existing code and tests;
- creates or updates the active OpenSpec proposal, delta specs, design, and tasks;
- defines acceptance behavior and a bounded change budget;
- records applicable `CORE_INVARIANTS.md` IDs with controlling sources;
- may upgrade Main's preliminary risk but never downgrade it, then records effective `risk` and
  `test_mode = RED_REQUIRED | RED_NOT_REQUIRED`;
- returns `PLAN_READY` only after strict validation and a testable contract.

Documentation-only work stays with Architect and skips Builder. For ROUTINE/STANDARD work, the same Architect
thread later performs full review. For CORE_RISK, Architect does not review implementation; a fresh Reviewer
owns contract conformance, code, tests, evidence, and invariants.

In REVIEW, Architect is read-only and must not modify any file.

After `APPROVE`, Architect may update only completion status, task checkboxes, evidence links, and
non-semantic documentation. If a requirement, scenario, acceptance criterion, scope, or design decision must
change, Architect does not edit it. The workflow returns to:

```text
PLAN_READY
reason = CONTRACT_CHANGED
```

The semantic change is made in a new planning pass and the applicable review reopens.

## Builder

Builder combines Developer and Tester. It owns HOW, TESTS, CODE, targeted GREEN, and bounded repairs in one
continuous thread.

For `RED_REQUIRED`, Builder:

1. writes the smallest meaningful tests before production implementation;
2. runs the exact targeted command;
3. accepts RED only when the named test executes and fails at the expected behavioral assertion;
4. records compact RED evidence and freezes the establishing tests;
5. implements without waiting for a Main RED turn;
6. runs targeted GREEN and verifies that the test hash is unchanged;
7. returns `BUILD_DONE`.

Compilation, discovery, configuration, startup, Docker, or another infrastructure failure is not behavioral
RED. Persistence, migration, transaction, locking, retry, concurrency, and idempotency behavior uses real
PostgreSQL through Testcontainers when required by `docs/TESTING.md`.

On Windows under Codex, Builder runs `docker version` and every targeted Docker/Testcontainers command with
escalated host access, outside the restricted sandbox. An in-sandbox `permission denied`, `docker_engine is not
listening`, or Docker discovery timeout triggers one escalated infrastructure retry; it is neither RED nor a
repair round. Docker is unavailable only when same-context escalated `docker version` fails.

Compact RED evidence contains only:

- changed test paths;
- test content hash after RED;
- exact targeted command;
- failing test and expected/actual assertion;
- Git diff captured before production implementation.

The pre-implementation diff is required only for `RED_REQUIRED`. Raw logs, timestamps, phase IDs, and a
separate production-tree manifest are not required.

For `RED_NOT_REQUIRED`, Builder records Architect's reason and the existing verification, creates no
artificial failure, and does not capture a pre-implementation diff merely for process evidence.

After RED, Builder must not weaken, skip, narrow, retag, reconfigure, regenerate, relocate, or otherwise change
the frozen test, expectation, fixture, snapshot, discovery, or runtime configuration. A test change requires a
Reviewer-authorized `REPAIR` with `requires_new_red = true`, followed by new RED evidence and a new hash.

Builder does not update OpenSpec or documentation and does not run the complete repository gate.

## Review routing

```text
ROUTINE or STANDARD -> same Architect thread
CORE_RISK           -> fresh reviewer thread
```

CORE_RISK includes persistence, transactions, idempotency, concurrency, locking, retry, duplicate
suppression, ordering, reproducible identity, migration, point-in-time semantics, provider reconnect,
degradation, data loss, recovery, gaps, cross-module boundaries, security, and integrity controls.

Reviewer receives the approved contract, applicable invariants, change budget, stable diff, tests, test mode,
and compact RED/GREEN evidence. Review starts with correctness, point-in-time integrity, idempotency,
transaction semantics, data-loss risk, and test adequacy before style.

Before `APPROVE`, Reviewer reports every applicable invariant as:

```text
Invariant | Applicable | Evidence | Verdict
```

`Not applicable` requires a reason. Reviewer returns exactly one consolidated verdict: `APPROVE`, `REPAIR`,
`ESCALATE`, or `BLOCKED`. If RED evidence is suspicious, Reviewer also sets `red_suspect = true`; only then
does Main rerun the claimed RED.

## States and attributes

Only these workflow states are used:

```text
PLAN_READY
BUILD_DONE
REPAIR
APPROVE
BLOCKED
ESCALATE
DONE
```

Details are attributes, not additional states:

```text
risk = ROUTINE | STANDARD | CORE_RISK
test_mode = RED_REQUIRED | RED_NOT_REQUIRED
tests_changed_after_red = true | false | not_applicable
blocked_reason = TEST_SPEC_ERROR | CONTRACT_ERROR | INFRASTRUCTURE | OTHER
reviewer = SAME_ARCHITECT | FRESH_TERRA_HIGH
requires_new_red = true | false
repair_round = 1 | 2 | 3
third_repair_authorized = true | false
reason = CONTRACT_CHANGED | OTHER
red_suspect = true | false
```

Each subagent response begins with `STATUS: <state>`. A missing, unknown, or role-incompatible state is a
protocol error and is corrected once without consuming the artifact repair pass.

## Stable write policy and change budget

Architect may write the active `openspec/changes/<change>/**`, task-scoped `docs/**`, and `README.md` when
required. Architect may not write production code, tests, accepted main specs outside an authorized
sync/archive phase, `AGENTS.md`, `.codex/**`, `pom.xml`, runtime/build configuration, governance documents, or
accepted ADR decisions unless the user explicitly authorized that exact change.

Builder may write only implementation and test paths within the accepted contract. Unless explicitly
approved, Builder must not change architecture, module dependency direction, dependencies, unrelated modules,
public contracts, migrations, fallback behavior, or refactor beyond the bounded implementation.

Reviewer and Escalation are read-only.

## Repair and escalation

Reviewer returns one consolidated repair set per review. Builder has two ordinary `REPAIR` passes available
without separate approval; Main tracks them as `repair_round = 1` and `repair_round = 2`. If a blocker remains,
Reviewer may authorize exactly one third repair by returning `REPAIR` with `repair_round = 3` and
`third_repair_authorized = true`. This authorization does not require a user turn. Each repair is followed by
review. A blocker that remains after the third repair returns `ESCALATE`; it never opens an unbounded loop.

Reviewer may escalate before the third repair when it cannot state a bounded safe repair or when the issue is
an unresolved contract, invariant, data-loss, transaction, concurrency, migration, security, or architecture
dispute. Builder never starts repair round 3 without the explicit Reviewer attribute.

Main invokes the read-only Sol High `escalation` agent only when:

- a contract/design blocker cannot be resolved from accepted sources;
- the same substantive defect survives all three repair passes;
- Main and Reviewer disagree on a release blocker;
- transaction, concurrency, migration, or invariant semantics remain ambiguous;
- credible data-loss, recovery, security, or architecture-boundary risk remains.

Sol receives one exact question plus the contract, invariants, diff, tests, and evidence. It does not restart
the project, edit files, or propose an unrelated redesign.

## Task capsule

Each new subagent starts without conversation history and receives a concise, self-contained capsule:

```text
Goal:
Phase: PLAN | BUILD | REVIEW | REPAIR | DOCS_CLOSE | CHALLENGE
Risk:
Test mode:
Active OpenSpec change:
Contract and scenario IDs:
Applicable invariants:
Change budget:
Files to read:
Checks to run:
Evidence supplied:
Expected status:
```

Use the same Architect thread for ROUTINE/STANDARD review and the same Builder thread for all repair passes.
CORE_RISK review always starts in a fresh Reviewer thread.

## End-to-end flow

```text
Main classifies risk
  -> Architect PLAN
  -> PLAN_READY
  -> documentation-only: Architect completes docs and skip Builder
  -> otherwise Builder performs RED when required, implementation, and targeted GREEN
  -> BUILD_DONE
  -> ROUTINE/STANDARD: same Architect reviews
  -> CORE_RISK: fresh Reviewer reviews
  -> APPROVE | REPAIR | ESCALATE | BLOCKED
  -> Builder may perform two ordinary repair rounds, each followed by review
  -> Reviewer may authorize one third repair, followed by review
  -> a blocker remaining after repair round 3 escalates to Sol High
  -> after APPROVE Architect performs DOCS_CLOSE
  -> Main independently runs the complete final gate
  -> DONE
```

## Complete final gate

Main runs from the repository root:

```powershell
pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1
mvnw.cmd clean verify
openspec validate --all --strict --no-interactive
openspec doctor
git diff --check
```

On Windows under Codex, Main first runs `docker version` with escalated host access and runs
`mvnw.cmd clean verify` in that same host-access context. A named-pipe failure observed only inside the
restricted sandbox must be retried outside it and does not consume a repair round.

Any nonzero exit blocks `DONE`. Report infrastructure blockers exactly; never narrow or skip required checks.
Route a final-gate failure by ownership:

- implementation or test issue -> Builder `REPAIR`;
- documentation or contract issue -> Architect;
- infrastructure issue -> `BLOCKED`.

An implementation or test failure in the final gate consumes the next available repair round. Rounds 1 and 2
need no separate approval. If both are exhausted, Reviewer decides whether to authorize round 3 or escalate.
After round 3, any remaining substantive blocker escalates to Sol High. No new workflow status is introduced.

## Benchmark telemetry

Main records dispatch/return timing and available token counters with
`.codex/scripts/log-agent-activity.ps1`. Record aggregate wall time and usage across Main, Architect, Builder,
Reviewer, repair, and Sol escalation. Score authored test quality separately from behavioral correctness,
including meaningful RED, real infrastructure, deterministic concurrency control, durable-state assertions,
rollback, retry, and resistance to controlled test mutations.
