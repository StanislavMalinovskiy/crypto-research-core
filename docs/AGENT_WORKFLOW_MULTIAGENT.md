# MULTIAGENT: lean GPT-6 workflow

## Purpose and activation

This is the normative workflow when `.codex/config.toml` contains exact Boolean `[agents].enabled = true` at task start. Main owns intake, scope, classification of ordinary work, routing, applicable final checks and DONE. Main alone spawns project subagents; subagents never spawn subagents. Mode remains fixed for the task. Use native Codex project roles or `codex queue`; Claude Code uses the equivalent native roles. Do not invoke Orca unless the user explicitly requests Orca in the current task. DEFAULT retains its separate Control/Developer workflow.

The owner or an explicitly appointed delegate decides changes to the goal, allowed expenses, holdout methodology and accepted critical constraints. Do not designate a temporary session as a permanent delegate. Technical decisions inside authorized boundaries stay with the team.

## Task tiers and Main's direct-edit boundary

Main may classify ordinary work and perform small bounded local changes within accepted requirements and owner authority. Strictly TRIVIAL remains clearly non-normative wording/formatting with factual claims and link/path targets preserved: no source, configuration, scripts, OpenSpec, ADRs, workflow/skills, normative docs, API/path/code rename or changed behavioral meaning. It uses facts, consistency, links and `git diff --check`, with no subagents, OpenSpec, review, overlap check, DOCS_CLOSE or archive and no automatic Maven.

For other work, assess the concrete guarantee changed and consequences against all TR triggers below. A word such as identity, retry or reproducibility, or a file's location, is not a trigger by itself. Architect participates for architecture, material ambiguity or critical-guarantee changes; credible unresolved critical uncertainty requires Architect before dependent work. With no critical trigger, NORMAL preserves accepted observable behavior and CONTRACT changes observable/public or accepted behavior. Uncertainty between those two selects CONTRACT; mixed scope takes the highest applicable tier.

NORMAL uses the existing accepted-source handoff: scope, test mode, affected/doubtful invariants, checks and budget. It skips OpenSpec, overlap checks, DOCS_CLOSE and archive. CONTRACT and CORE_RISK system/requirement changes require an active OpenSpec change. A bounded free/approved-budget probe without holdout, outcomes or admission uses an existing procedure description and receipt, not a dummy change. D1/P1 admission, system behavior or accepted requirement changes require OpenSpec; reuse an existing exact-scope change. UNKNOWN, partial coverage or negative probe findings do not admit data.

## Roles and model routing

| Role or fixed risk | Model / effort | Project role | Claude Code model / effort | Claude Code role |
|---|---|---|---|---|
| Main | gpt-6.1-sol / medium | Main session | claude-opus-5-5 / medium | Main session |
| Architect | gpt-6.1-sol / high | architect | claude-opus-5-5 / high | architect |
| ROUTINE Builder | gpt-6-luna / xhigh | builder_luna_xhigh | claude-sonnet-5-5 / medium | builder_sonnet_routine |
| STANDARD Builder | gpt-6-luna / max | builder_luna_max | claude-sonnet-5-5 / medium | builder_sonnet_standard |
| CORE_RISK Builder | gpt-6.1-sol / medium | builder_sol | claude-opus-5-5 / medium | builder_opus |
| Fresh Reviewer | gpt-6.1-sol / medium | reviewer | claude-opus-5-5 / medium | reviewer |
| Fresh Escalation | gpt-6.1-sol / high | escalation | claude-opus-5-5 / high | escalation |

Under Claude Code, Luna maps to Sonnet and Sol maps to Opus wherever this workflow names a model; `.claude/settings.json` limits concurrent subagents to 4 and spawn depth to 1, and every role file denies the Agent tool. Reviewer and Escalation additionally deny file-editing tools.


Builder risk routing is independent of NORMAL versus CONTRACT. The classifier records ROUTINE for small well-understood bounded work or STANDARD for broader reasoning; critical guarantees select CORE_RISK. Model, effort, concurrency and sandbox configuration remain unchanged. Ordinary documents may be authored by Main; Architect handles assigned documentation. Builder may edit documentation/instructions only when the bounded owner-authorized assignment names those paths.

## Architect planning and risk

Architect owns WHAT, WHY and PLAN when its participation is required, reading the impact-selected controlling sources, relevant code/tests and affected accepted/active specs. Main's ordinary classification does not require a separate Architect PLAN. Architect returns PLAN_READY only for an implementation-ready testable contract; CONTRACT/CORE_RISK additionally require strictly valid artifacts.

Before PLAN_READY for CONTRACT and CORE_RISK, run `openspec list`, inspect other active changes touching the same specs, and record overlaps in the handoff: `none`, or each with `resolve first`, `safe to proceed` or `blocked`. Resolve unsafe overlaps before ready. NORMAL and TRIVIAL need no overlap check.

The following is the single authoritative trigger list. A matched changed critical guarantee selects CORE_RISK; assess semantics and consequences, not keywords.

CORE_RISK triggers: persistence semantics and the remaining numbered categories below.

| ID | Trigger |
|---|---|
| TR-01 | Persistence semantics |
| TR-02 | Transactions |
| TR-03 | Concurrency or locking |
| TR-04 | Idempotency or retry |
| TR-05 | Migrations or data loss |
| TR-06 | Point-in-time correctness |
| TR-07 | Financial values or exact arithmetic |
| TR-08 | Identity or ordering |
| TR-09 | Provider gaps, reconnect or recovery |
| TR-10 | Module boundaries |
| TR-11 | Reproducibility or integrity |
| TR-12 | Security or secrets |

Record tier, risk, `risk_triggers = matched triggers | none`, reasons, affected/doubtful [core invariants](CORE_INVARIANTS.md), controlling sources, test mode and cumulative time/resource budget in the existing handoff. Explicit none follows assessment against the whole list. No new state or registry file is needed.

Risk reassessment records previous/new categories, reason and concrete evidence. An independent Reviewer confirms any downgrade; no silent downgrade to save time or bypass checks. Changes to accepted critical constraints remain owner/delegate decisions. New scope returns to Architect PLAN with `reason = CONTRACT_CHANGED`, adding `subreason = RISK_CHANGED` when applicable, before dependent work; a newly discovered observable-contract change in NORMAL requires OpenSpec. Routine repairs do not silently reclassify.

Architect phases remain PLAN, DOCS, REVIEW, REPAIR, DOCS_CLOSE and ARCHIVE. Only Architect issues a new PLAN_READY where its plan is required. Semantic changes outside PLAN reopen planning rather than prematurely declaring readiness.

## Builder and behavioral evidence

Executable critical-guarantee changes and executable bugfixes require applicable meaningful regression RED_REQUIRED. Documentation-only work, including critical-guarantee documentation, uses RED_NOT_REQUIRED: facts, consistency, links and requirements; substantive research-rule changes retain independent review. Other CONTRACT work records test mode and concrete reason; other NORMAL work uses useful appropriate tests. Never fabricate a failure merely to satisfy process.

The implementing author derives the smallest meaningful tests from requirements and accepts RED only when the named test executes and fails at its expected behavioral assertion. Before implementation, record changed test paths, exact command and, for each failing test, requirement/acceptance-criterion, assertion and expected/actual. Verify a wrong target, wrong assertion or setup error before freeze; fix and rerun RED without reviewer permission. After valid RED, semantic freeze protects establishing test meaning, not implementation helpers sharing the source file. Continue directly to implementation and targeted GREEN; BUILD_DONE repeats the RED reason and `tests_changed_after_red`. RED hashes and pre-implementation-diff snapshots are unnecessary; stable review diffs and archive/checkpoint/recovery evidence remain required.

Compilation, discovery, configuration, startup, Docker and other infrastructure failures are not RED. Use real PostgreSQL/Testcontainers when [Testing](TESTING.md) requires persistence, migration, transaction, locking, retry or idempotency behavior. Docker Desktop named pipes can be inaccessible in the restricted sandbox on Windows: run `docker version` and every Docker/Testcontainers command with escalated host access. Retry an in-sandbox denial, discovery timeout or `docker_engine is not listening` once in that host context; this infrastructure retry is not RED or an artifact repair. Docker is unavailable only after same-context host `docker version` fails. Claude Code uses Bash with `dangerouslyDisableSandbox` when sandboxing is enabled.

Do not weaken, disable, skip, narrow, retag, regenerate or relocate establishing tests/expectations/fixtures/snapshots/discovery/runtime settings, fit expected truth to implementation, change acceptance criteria or accepted raw evidence, or add production behavior that recognizes a test artifact. A proven synthetic fixture or setup contradiction with an accepted requirement may be corrected without prior owner or Reviewer permission, including after freeze: preserve the exact accepted-source conflict, change and rerun evidence; disclose `tests_changed_after_red`; establish applicable new meaningful behavioral RED if an establishing test changes. Subsequent independent review verifies the correction preserves the requirement. Unproven errors or requirement ambiguity require independent technical diagnosis (`TEST_SPEC_ERROR`); contract defects reopen PLAN. Voluntary valid RED invokes the same semantic freeze. RED_NOT_REQUIRED still records reason and named applicable checks.

Builder owns HOW, implementation, tests and targeted GREEN in one continuous pass; do not run Main's complete repository gate or edit OpenSpec checkboxes. No dependencies, migrations, public contracts, architecture, fallback or unrelated refactoring beyond the accepted assignment.

## Review routing

Any CORE_RISK change → fresh Reviewer (new thread), independent of its author including Main or Architect. Authors must never approve their own holdout, exact financial arithmetic, accepted evidence, migration or secret-safety changes. Ordinary NORMAL/CONTRACT uses appropriate existing review; an eligible participating Architect may review in its same thread. Do not invent mandatory Architect participation merely to review ordinary work. An accepted normative-spec change alone is not a critical trigger.

Review stable diff, contract, test meaning, evidence, affected/doubtful invariants and budget. Answer “Какое предположение реализации или контракта может быть неверным?” (What implementation or contract assumption could be wrong?). Check substantive assumptions against controlling requirements, including fixture corrections and downgrade evidence. Report affected/doubtful CI with concrete evidence in existing artifacts; justify omissions when their applicability is doubtful, rather than repeating untouched matrices in every message. Separate blocking defects from optional improvements; optional suggestions alone do not force repair. Return one consolidated APPROVE, REPAIR, ESCALATE or BLOCKED; Main reruns RED only on `red_suspect = true`. Reviewer is read-only.

## Repair and escalation

Implementation or test repairs return to the same Builder, then BUILD_DONE and review. Documentation repairs return to the same author and applicable review. There is no numeric repair-count ceiling or fourth-repair prohibition. Preserve cumulative time, expenses and resources across replanning, renaming and session replacement. Repeated defects, absent verifiable progress or exhausted agreed resources require independent diagnosis: cause, proposed approach, remaining budget and next-result criterion. Continue technical repairs inside authorized limits; iteration number alone is not an owner decision.

If the same Builder or Reviewer session cannot be resumed, Main starts a fresh session of the same role and configured model/effort, supplying contract, current diff, RED/GREEN evidence, open review items and remaining cumulative time/resource budget. Preserve repair history and Reviewer independence; disclose that the session was replaced. Session loss alone is not an owner decision.

Fresh read-only Escalation receives one bounded technical question, sources, diff, tests and evidence, without restarting the project. Its attribute is `verdict = REPAIR | REPLAN | APPROVE | OWNER_DECISION`: REPAIR routes to the same author then review; REPLAN maps to ESCALATE with CONTRACT_CHANGED / ESCALATION_REPLAN and Architect PLAN; APPROVE resumes closure; OWNER_DECISION maps to BLOCKED with the concrete missing choice. No new states or reset of cumulative expenses. Owner intent, scope, additional expenses or changed critical constraints require owner/delegate authority.

## States and task capsule

The only states remain `PLAN_READY BUILD_DONE REPAIR APPROVE BLOCKED ESCALATE DONE`. Subagent responses start `STATUS: <state>`; Main alone returns DONE. Correct an incompatible status once without counting it as an artifact repair.

Existing handoffs carry goal/authority, assigned phase, tier/risk/triggers/reason, test mode, accepted sources/active change when required, affected/doubtful invariants, cumulative budget and author paths, files/checks/evidence, open repairs and return route. Attributes such as `tests_changed_after_red`, `requires_new_red`, `red_suspect`, reason and verdict create no new states. Do not add a task registry, capsule/state/evidence file solely for context.

## Stable write policy

Architect may write the active change, assigned docs and README. Accepted specs change only through authorized verified sync/archive. Architect does not write code/tests, AGENTS, role/build/runtime configuration, governance or accepted ADR decisions without explicit exact-path owner authority. Builder writes assigned implementation/tests and explicitly owner-authorized documentation paths; it never marks OpenSpec tasks. Reviewer and Escalation remain read-only. Preserve unrelated owner changes and all application boundaries.

## End-to-end flow

CONTRACT/CORE_RISK: APPROVE → Architect DOCS_CLOSE → Main complete final gate → Main checkpoint → Architect CLI archive → Main post-checks → Main DONE.

The complete final gate applies when actual dependencies/effects require it under [Testing](TESTING.md); isolated documentation/research tools use scoped checks, and mixed scope uses their union. NORMAL has no DOCS_CLOSE/archive and completes after applicable review/checks. TRIVIAL uses its scoped editorial checks.

Read [the closure procedure](agents/close-archive.md) only before DOCS_CLOSE, complete final gate, archive, archive recovery or post-archive work. Ordinary PLAN, BUILD and REVIEW do not load it. NORMAL loads only applicable full-gate guidance when integration requires it. Main retains gate ownership, checkpoint, exact mutation inspection, post-checks and DONE. Required-check failures route to the implementation/test author, documentation/contract author or infrastructure diagnosis; no fabricated PASS or check narrowing.

## Concise reports and durable evidence

Each final report includes status, result, concrete evidence, blockers/material risks and “Следующее разрешённое действие: …”. PLAN_READY and APPROVE finish phases, not the user's goal; Main continues already authorized actions without repeat permission. A real blocker names the missing authority/evidence.

Main may inspect sources directly for ordinary bounded work and route deeper inspection to existing roles when useful. Summaries are navigation; durable truth remains in existing OpenSpec, code/tests, test reports and evidence. No new infrastructure for context management.

Retain full output by plain shell redirection or existing mechanisms outside Maven-cleaned directories; save exact command exit code immediately. PASS names exact command, exit code and available tests/failures/errors/skipped counts; FAIL names check, excerpt and log path, and remaining unrun checks. A required skipped/missing check is not PASS; never substitute a formatter's status. Later checked-input changes invalidate affected results.

## Assignment telemetry

Use `.codex/scripts/log-agent-activity.ps1` only for benchmark, debug or explicitly requested measurement. Existing event formats/allowlists remain; telemetry grants no authority. For the next 2–3 tasks, add first-verifiable-result time, repair returns and later defects to existing final reports. Compare only available similar baseline data, explicitly state unavailable baseline, invent none and do not delay reform closure or add infrastructure.
