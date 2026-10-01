# Crypto Research Core agent guide

Crypto Research Core is a research-first crypto signal evaluation system: one synchronous Spring Modulith modular monolith, one repository, one Maven module, one deployable JAR and one PostgreSQL database.

## Source responsibilities

Use each source for its responsibility; there is no universal document priority:

- `docs/ROADMAP.md`: product hypotheses, long-term capabilities and directional priorities.
- `docs/DELIVERY_PLAN.md`: operational stages, current/next work and exit outcomes.
- `docs/adr/*`: accepted architectural decisions and rationale.
- `docs/ARCHITECTURE.md`: current consolidated architecture.
- `docs/TECH_STACK.md`: allowed technologies and versions.
- `docs/REPRODUCIBILITY.md`: time, numeric, provenance and deterministic-result rules.
- `openspec/specs/*`: accepted observable behavior; `openspec/changes/*`: proposed or partially implemented behavior.
- Code and tests: evidence of the implementation that actually runs.
- `docs/PROJECT_SUMMARY.md` and `docs/GLOSSARY.md`: navigation and terminology; summary reading is conditional only as specified below.
- `docs/notes/*` and `docs/archive/*`: non-normative ideas and history.

An accepted or superseding ADR must update Architecture; inconsistency is a documentation defect. An active change becomes current behavior only after implementation, verification and archive. Code differing from a main spec is a defect or an explicitly tracked migration. Roadmap cannot silently override an accepted ADR. Record unresolved conflicts in the active change and handoff.

## Required reading

Read this guide and determine the selected workflow before work. Before the affected decision or edit, read the active proposal, delta specs, design and tasks when the selected route requires a change, affected accepted specs, applicable ADRs, relevant code/tests, and affected module documents and nearest module-level AGENTS.md. A nearer guide may add rules but cannot relax this contract. Module documents are `docs/modules/kernel.md`, `docs/modules/marketdata.md`, `docs/modules/risk.md`, `docs/modules/wallet.md`, `docs/modules/signal.md` and `docs/modules/evaluation.md`; read only those affected.

In MULTIAGENT, strictly TRIVIAL and Architect-classified NORMAL require no OpenSpec; CONTRACT and CORE_RISK require an active change before implementation. Follow the selected workflow's tier boundaries and TR-first assessment, never a file-type shortcut. DEFAULT retains its requirement for an active change for behavior, dependency, schema or architectural changes. Create changes with the installed OpenSpec CLI; never hand-create generated workflow skills or `.openspec.yaml`. Requirements use normative, testable SHALL with WHEN/THEN/AND scenarios. Run strict validation before declaring a change ready.

Use the applicable local skill: [.agents/skills/openspec-propose/SKILL.md](.agents/skills/openspec-propose/SKILL.md) for a new plan, [update](.agents/skills/openspec-update-change/SKILL.md) for existing planning artifacts, [apply](.agents/skills/openspec-apply-change/SKILL.md) for implementation, [explore](.agents/skills/openspec-explore/SKILL.md) for investigation, [verify](.agents/skills/openspec-verify-change/SKILL.md) for verification, [sync](.agents/skills/openspec-sync-specs/SKILL.md) for explicitly authorized accepted-spec synchronization, and [archive](.agents/skills/openspec-archive-change/SKILL.md) for authorized verified archive.

## Task routing

These reads are mandatory before the affected decision, not optional background. Resolve missing or ambiguous controlling references before dependent work.

| Task impact | Required source and rules |
|---|---|
| Onboarding, product orientation or explicit relevance | Read `docs/PROJECT_SUMMARY.md` only for these purposes; `docs/ROADMAP.md` for product hypotheses and `docs/DELIVERY_PLAN.md` for stages/current work |
| Java implementation, technology, version or dependency decision | `docs/TECH_STACK.md`: Java preferences, stable baseline, technology prohibitions and dependency approval |
| Structure, module/API/package boundaries or dependency direction | `docs/ARCHITECTURE.md`: complete allowed/forbidden graph and API rules; affected module docs and ADRs |
| Persistence, SQL, schema, index, migration, transaction, event, background or concurrency work | `docs/ARCHITECTURE.md`: ownership, persistence, transaction/event, background and bounded concurrency rules |
| Test selection, execution, changed checks or review | `docs/TESTING.md`: smallest meaningful level, architecture verification, real PostgreSQL, RED/freeze, review priorities, complete verification and CI responsibilities |
| Runtime, configuration, provider operations or secrets | `docs/OPERATIONS.md`: configuration, secrets, budgets, health and operational boundaries |
| Time, financial values, identity, ordering, datasets, signals, evaluations or reports | `docs/REPRODUCIBILITY.md`: complete time/numeric/identity/provenance/determinism rules, plus affected accepted specs |
| Planning applicability, affected implementation or review | `docs/CORE_INVARIANTS.md`: apply every affected invariant with controlling sources; justify non-applicability, never replace definitions with summaries |

## Agent workflow mode

Project transport is native Codex: project roles in `.codex/agents/**`, or `codex queue` for user-created sessions. Under Claude Code, the equivalent transport is native Claude Code project subagents in `.claude/agents/**` with the same roles, phases and statuses; only model names differ, as mapped in the selected workflow. Do not invoke Orca, `orca-cli`, Orca orchestration/run/worker commands or the Orca application unless the user explicitly requests Orca in the current task. Generic agent, delegation, supervision or progress requests do not grant that permission.

The project default is `gpt-6.1-sol` with `medium` reasoning (Claude Code: `claude-opus-5-5` with `medium` effort in `.claude/settings.json`). Model choice does not choose mode; the selected workflow owns role routing. Determine mode once at task start from `.codex/config.toml`; Claude Code uses the same switch:

- Exact Boolean `[agents].enabled = true`: use MULTIAGENT and load [docs/AGENT_WORKFLOW_MULTIAGENT.md](docs/AGENT_WORKFLOW_MULTIAGENT.md) plus the applicable `.codex/agents/*.toml` role configuration, or `.claude/agents/*.md` under Claude Code.
- `false`, missing or invalid: fail-closed DEFAULT; do not spawn project subagents; load [docs/AGENT_WORKFLOW.md](docs/AGENT_WORKFLOW.md).

Never edit the switch, spawn a project subagent or silently change mode because a task seems risky. Only the owner may enable MULTIAGENT; you may recommend it. Mode remains fixed unless the owner explicitly restarts or continues after changing the setting. DEFAULT uses its own Control/Developer responsibilities and evidence, without importing specialized MULTIAGENT phases, statuses, manifests, capsules, telemetry or role routing.

## Nonnegotiable boundaries

- Package root is `io.cryptoresearch`; modules are `kernel`, `marketdata`, `risk`, `wallet`, `signal`, `evaluation`. Follow Architecture's entire allowed/forbidden graph and module ownership rules; each cross-module edge targets `module::api`. No cyclic dependencies, implementation imports, shared persistence or cross-module SQL/repository reuse.
- Each module owns its domain, use cases, persistence, provider adapters, migrations and tests. Business migrations live under the owning module's path below `src/main/resources/db/migration/`; follow Architecture's full Flyway/schema/first-object rules.
- Keep one repository, Maven module, JAR and PostgreSQL database until an evidence-backed ADR changes that. Boundary/direction changes require an accepted ADR and updated module docs first.
- The MVP has no signing, order submission, PAPER/LIVE execution or executable governance gates. Execution requires a dedicated approved change and ADR.
- Java 25 has no preview features. Any exact preview API requires an approved change and superseding ADR. Do not silently add or upgrade production dependencies; document them in the design and obtain explicit approval outside the accepted baseline.
- Reject future-data leakage, silent outcome loss, fabricated provider fallback data and unbounded concurrency. Domain details and complete prohibitions are in the mandatory sources above.
- Preserve unrelated owner changes. Do not overwrite owner work, generated secrets or local IDE state.

## Completion

Active artifacts and verified task checkboxes must match implemented scope. Code must respect module ownership, synchronous contracts and dependency directions. Tests cover new behavior and PostgreSQL migrations where applicable. Update documentation and relative links; remove tool-export markup. Comments and naming are English. Commit no unrelated changes, secrets or local IDE state.

Apply the selected workflow's verification and closure responsibilities. MULTIAGENT Builder reports evidence without editing OpenSpec/docs; for CONTRACT/CORE_RISK, Architect checks verified tasks only in DOCS_CLOSE after APPROVE. NORMAL omits DOCS_CLOSE/archive; strictly TRIVIAL permits only Main's bounded non-normative text edits. DEFAULT Control owns its final documentation. Never mark partial, deferred or unverified behavior complete; archive only after implementation and verification.

Except for MULTIAGENT strictly TRIVIAL, required checks include test-integrity preflight, complete Maven verification, strict all-item OpenSpec validation and doctor; use the selected workflow's exact gate commands and ownership. TRIVIAL requires `git diff --check` and `mvnw.cmd -Dtest=RepositoryConventionsTest test`; DEFAULT and every other tier retain the complete gate. Read Testing before execution, including Windows host Docker preflight/retry rules. Report every skipped or blocked check with exact command and cause; never claim completion while a required check has not passed.
