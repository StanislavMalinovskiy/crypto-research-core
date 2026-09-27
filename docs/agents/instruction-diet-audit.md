# Instruction diet audit

Non-normative audit, 2026-09-27. This report proposes changes; it does not change the agent contract. The only authorized write is this file. No OpenSpec, role, skill, configuration, source, test, telemetry, or accepted specification was changed. Verification below is inspection evidence, not a claim that the repository gate ran.

The best reduction is to replace repeated full-workflow reads with a small router and phase-specific references. The root guide already has fewer than 150 lines, so a line limit alone would achieve little. Preserve the risk classifier, ownership, test freeze, repair budget, independent review, complete gate, and scoped archive recovery. Move their detailed procedures to one controlling location each and require the relevant procedure at the point of use.

## Scope and method

Inspected `AGENTS.md`, `.codex/config.toml`, all six `.codex/agents/*.toml`, all seven `.agents/skills/*/SKILL.md`, `openspec/config.yaml`, `docs/PROJECT_SUMMARY.md`, both workflow documents, `docs/CORE_INVARIANTS.md`, relevant testing guidance, and enforcement code. The referenced core protocol is `docs/AGENT_WORKFLOW_MULTIAGENT.md` plus `docs/CORE_INVARIANTS.md`; there is no separate file named `AGENT_WORKFLOW_MULTIAGENT_CORE_PROTOCOL.md`. Only the root `AGENTS.md` was found in this checkout; retain the nearest-guide rule for future module guides.

Sizes are raw on-disk bytes and `ceil(bytes / 4)`, not tokenizer measurements. UTF-8 Russian text, TOML metadata, CRLF, repeated prompts, conversation inheritance, caching, tool wrappers and generated CLI instructions can change actual usage. A file listed in a catalog is not necessarily loaded in full. Repeated source reads can cost additional context even if the bytes are already present.

The current switch is exact Boolean `enabled = true`: MULTIAGENT. This audit does not change it. Pre-existing unrelated change deletions and a superseded-change notes directory were observed and preserved. Do not derive cleanup authority from this report.

## What loads, and when

| Layer | Current loading mechanism | Audit consequence |
|---|---|---|
| Root `AGENTS.md` | Supplied as project instructions in this session | Count once per fresh agent context; do not claim an additional disk read is new information. |
| `.codex/config.toml` | Runtime settings plus mandated mode read | Settings configure routing; the complete text need not be injected as prose. |
| Selected role TOML | Selected role's `developer_instructions` supplied to its agent | Only the selected role, not all six, belongs in a per-agent estimate. Whole-file measurements slightly overstate injected text. |
| Skill catalog | Names, descriptions and locations available in the session | Seven repo descriptions cost about 313 estimated tokens; external installed skill catalogs are outside this report's savings. |
| Skill body | Read when invoked/selected | The seven bodies are not an automatic startup cost. Do not claim removing 20,000 skill tokens saves that much on every task. |
| Summary and selected workflow | Root mandates reads | MULTIAGENT loads the whole 4,586-token workflow, including archive recovery during early planning/build work. |
| Active artifacts, affected module docs, applicable ADRs, code/tests | Root and role mandate relevance-based reads | Mostly justified; avoid all-module reads and repeated reads of unchanged artifacts within one phase. |
| Testing, operations, reproducibility | Root has conditional triggers | Correct shape, but whole-file reads and duplicated protocol sections inflate cost. |
| OpenSpec context | `openspec/config.yaml` and CLI instructions | Adds broader mandatory reads of Roadmap, Architecture, Tech Stack and `docs/modules/` before planning/applying; broadens the root's affected-module rule. |
| Core invariants | Role and workflow mandate applicable invariants | The list is a checklist derived from authorities, not a replacement or an independent risk trigger list. |

### Measured inventory

| File | Bytes | Estimated tokens |
|---|---:|---:|
| `AGENTS.md` | 12,352 | 3,088 |
| `.codex/config.toml` | 472 | 118 |
| `docs/PROJECT_SUMMARY.md` | 5,584 | 1,396 |
| `docs/AGENT_WORKFLOW.md` | 4,168 | 1,042 |
| `docs/AGENT_WORKFLOW_MULTIAGENT.md` | 18,343 | 4,586 |
| `docs/CORE_INVARIANTS.md` | 7,738 | 1,935 |
| `docs/TESTING.md` | 10,520 | 2,630 |
| `docs/OPERATIONS.md` | 11,682 | 2,921 |
| `docs/REPRODUCIBILITY.md` | 8,402 | 2,101 |
| `docs/ARCHITECTURE.md` | 15,839 | 3,960 |
| `.codex/agents/architect.toml` | 3,779 | 945 |
| `.codex/agents/builder_luna_xhigh.toml` | 4,253 | 1,064 |
| `.codex/agents/builder_luna_max.toml` | 4,248 | 1,062 |
| `.codex/agents/builder_sol.toml` | 4,259 | 1,065 |
| `.codex/agents/reviewer.toml` | 3,395 | 849 |
| `.codex/agents/escalation.toml` | 1,809 | 453 |
| `.agents/skills/openspec-apply-change/SKILL.md` | 8,423 | 2,106 |
| `.agents/skills/openspec-archive-change/SKILL.md` | 10,731 | 2,683 |
| `.agents/skills/openspec-explore/SKILL.md` | 19,369 | 4,843 |
| `.agents/skills/openspec-propose/SKILL.md` | 14,071 | 3,518 |
| `.agents/skills/openspec-sync-specs/SKILL.md` | 12,554 | 3,139 |
| `.agents/skills/openspec-update-change/SKILL.md` | 7,502 | 1,876 |
| `.agents/skills/openspec-verify-change/SKILL.md` | 7,799 | 1,950 |

All seven skill bodies total 80,449 bytes, approximately 20,115 tokens when rounded per file. All six roles total 21,743 bytes, approximately 5,438 tokens per-file rounded. These are repository inventory totals, not a single-agent loading claim. The three Builder instruction bodies are identical; only metadata/model routing differs. Retain three entry points because routing pins model/effort, and share the body through an explicit required reference rather than assuming TOML supports includes.

Aggregate rounding gives 20,113 tokens for skill bodies and 5,436 for roles. Additional measured conditional reads: Roadmap 59,649 bytes / 14,913 tokens; Tech Stack 7,743 / 1,936; OpenSpec config 1,480 / 370; all module docs 16,591 / 4,148 aggregate tokens (4,150 per-file rounded). The broad OpenSpec-context bundle can therefore add 25,326 tokens for Roadmap + Architecture + Tech Stack + all module docs, before active artifacts. Treat that as an upper interpretation of the ambiguous `docs/modules/` read, not a measured actual load. Affected module reads cost less: kernel 529, marketdata 1,116, risk 320, wallet 243, signal 1,151, evaluation 656, module index 135.

The fourteen non-repository skill descriptions exposed in this session total 4,935 bytes, about 1,234 tokens; the seven repository descriptions total 1,247 bytes, about 312 aggregate tokens (313 per-file rounded). Names, paths and catalog wrappers add unmeasured overhead. External skills cannot be removed by a repository-only refactor and are retained in both budget columns below. An on-disk skill absent from the exposed catalog is not counted as injected context.

## Findings and priorities

| Priority | Finding and evidence | Proposed treatment |
|---|---|---|
| 1 | Archive skill's step 5 manually moves the directory; steps 2–4 allow confirmed incomplete work and archive without sync. Project workflow instead requires APPROVE, DOCS_CLOSE, full gate, checkpoint, CLI archive and post-checks. | Make the project close procedure controlling. Remove the incompatible generic completion path from this project's archive entry point; preserve store/path/merge safeguards in references. |
| 1 | Apply skill tells the implementer to update task checkboxes, while all Builder roles forbid documentation/OpenSpec writes. Its fluid-workflow language also conflicts with fixed phases. | Builder returns evidence; Architect updates verified tasks in DOCS_CLOSE. Scope changes route to PLAN. Keep skill mechanics subordinate to author ownership. |
| 1 | Verify skill allows heuristic coverage and “ready for archive” with warnings; this is weaker than explicit invariant evidence and zero failed required checks. | Keep heuristic discovery as review assistance only. A missing required scenario or failed gate cannot be downgraded to a suggestion. |
| 1 | Propose invalidates implementation authorization in the invoking request; update requires confirmation for every artifact; explore requires a new separate confirmation even after explicit scoped capture authorization. | Preserve planning-only/exploration write boundaries, but remove blanket revocation of explicit owner authorization. Main controls phase transitions; ask only for unresolved intent or new scope. A user asking only to propose still authorizes planning only. |
| 2 | Root, workflow, roles, Testing and Core Invariants repeat risk routing, RED meaning, freeze, Docker handling and repair rules. | One reference per procedure, short mandatory role pointer, compact phase capsule. Do not copy the full source into a capsule. |
| 2 | Every MULTIAGENT agent reads archive restoration, final-gate routing, all role models and telemetry regardless of phase. | Common protocol contains mode/authority/state rules only; PLAN, BUILD, REVIEW, CLOSE and ARCHIVE procedures load on demand. |
| 2 | OpenSpec context mandates a broad documentation bundle; `docs/modules/` is ambiguous about whether every module is required. | Replace with affected-module and trigger-based routing; preserve full source responsibilities and controlling constraints. |
| 2 | Explore is 4,843 estimated tokens, with long illustrative conversations and diagrams. Shared store selection is repeated in seven skills. | Move examples to an optional reference; put roots/stores/schema/path mechanics in one required shared reference. |
| 2 | `RepositoryConventionsTest` tests literal instruction clauses and source paths. Naively relocating text fails tests even if semantics survive. | A future authorized change must update discovery/enforcement tests with behavioral RED; do not bypass the tests or leave duplicate text solely to satisfy string matches. |
| 3 | Explore says to use `root.path` “returned above” after list commands, without the explicit root-resolution call used by propose. Optional continue/new skills are referenced but absent from this repo catalog. | Reuse common `openspec context --json` root resolution; keep availability checks and direct CLI fallback. Do not assume optional workflows are installed. |
| 3 | Skill Bash tool hints and Unix `mv`/`mkdir` samples do not match this Windows task environment. | Use tool-neutral command recipes with a Windows note; never let a metadata hint override actual tool availability or mutation safety. |
| 3 | PROJECT_SUMMARY carries volatile current-stage/version details alongside orientation. Historical benchmark prose is not routing authority. | Load summary for onboarding/product direction; load Delivery Plan for current work. Keep routing in the selected workflow. Do not assert the current summary is stale without a source conflict. |

No new evidence shows that the project's model choices or versions are stale. Generated skill metadata (`generatedBy: 1.13.0`) alone is not evidence of staleness. The concrete drift is between generic skill actions and the project contract.

## Proposed destinations

These names denote proposed references, not files created by this audit. Existing authoritative architecture, ADR, test, operations and reproducibility documents remain authoritative. Avoid a second competing architectural policy document.

| Alias | Proposed location and responsibility |
|---|---|
| R | `AGENTS.md`: small mandatory router, source map, hard boundary reminders and invariant IDs |
| P0 | `docs/agents/protocol/common.md`: mode, authority, states, capsule, write ownership and repair accounting |
| PP | `docs/agents/protocol/plan.md`: sole risk trigger table, contract, overlaps and PLAN_READY |
| PB | `docs/agents/protocol/build.md`: specification-derived RED, pre-freeze validation, freeze, targeted GREEN and Builder return |
| PR | `docs/agents/protocol/review.md`: independent routing, evidence, invariant dispositions, verdicts and test-error authorization |
| PC | `docs/agents/protocol/close.md`: DOCS_CLOSE and exact complete gate with ownership routing |
| PA | `docs/agents/protocol/archive.md`: checkpoint, CLI archive, allowlist, post-checks and raw-byte recovery |
| PE | `docs/agents/protocol/escalation.md`: bounded challenge, verdict mapping and remaining budget |
| PD | `docs/AGENT_WORKFLOW.md`: DEFAULT only; no MULTIAGENT protocol imported |
| CI | `docs/CORE_INVARIANTS.md`: all existing CI definitions and source links, no risk definitions |
| OS | `.agents/skills/references/openspec-common.md`: roots/stores, schema/status paths, context/rules, selection and artifact mechanics |
| SP/SU/SE/SA/SV/SS/SC | Respective propose/update/explore/apply/verify/sync/archive entry points and their `references/` operation guides |
| A/T/O/N | Existing `docs/ARCHITECTURE.md`, `docs/TESTING.md`, `docs/OPERATIONS.md`, `docs/REPRODUCIBILITY.md`, respectively |
| TS/M/D | Existing `docs/TECH_STACK.md`, affected `docs/modules/<module>.md`, and applicable accepted ADRs |

The workflow index should link the procedures; it must not instruct all phases to read them all. Keep one canonical risk trigger table, moved only by an explicit normative change. Short role prompts retain identity, model/effort, write limits, phase limit and output states, then require P0 plus the current phase reference. Never make safety-critical references merely “optional background.”

### Candidate root router (under 150 lines)

The following is a proposed shape, not a ready-to-apply policy patch. It relies on the destination references above being complete and validated first. A byte budget of at most 3,600 UTF-8 bytes, roughly 900 estimated tokens, is the target for the final router; measure the implementation rather than assuming this illustrative text meets that budget.

```markdown
# Crypto Research Core agent router
Research-first; one synchronous Java modular monolith, Maven module, JAR and PostgreSQL database.
Package io.cryptoresearch; modules kernel, marketdata, risk, wallet, signal, evaluation.
Reference aliases: P0/PP/PB/PR/PC/PA mean docs/agents/protocol/{common,plan,build,review,close,archive}.md.
Domain aliases: A/T/O/N mean docs/{ARCHITECTURE,TESTING,OPERATIONS,REPRODUCIBILITY}.md.
M means docs/modules/<affected-module>.md; D means applicable docs/adr/*.md.
Skill aliases SE/SS mean the explore/sync skill's references/ operation guide.

## Authority and mode
Owner intent and authorization control scope; preserve unrelated owner work.
Read .codex/config.toml once: exact [agents].enabled = true selects MULTIAGENT.
Otherwise use DEFAULT; never edit the switch or change mode implicitly.
MULTIAGENT: load docs/agents/protocol/common.md and the assigned phase reference.
DEFAULT: load docs/AGENT_WORKFLOW.md; no project subagents.
Native Codex only; Orca requires an explicit current-task request.
Models and phase ownership come from the selected workflow; only Main spawns.

## Source responsibilities
ROADMAP: hypotheses; DELIVERY_PLAN: current sequence; ADRs: accepted decisions.
ARCHITECTURE: current architecture; TECH_STACK: baseline technologies.
REPRODUCIBILITY: time, exact numbers, provenance and determinism.
openspec/specs: accepted behavior; openspec/changes: proposed behavior.
Code/tests: running evidence; PROJECT_SUMMARY/GLOSSARY: navigation.
notes/archive: non-normative history. No universal document precedence.
Resolve source conflicts in the active contract; ADR changes update ARCHITECTURE.

## Before editing
Read active contract, affected module docs and nearest AGENTS, applicable ADRs, code/tests.
Nearest guidance may add rules, not weaken these requirements.
Behavior/dependency/schema/architecture changes require an active CLI-created OpenSpec change.
PLAN_READY requires strict validation and resolved active-change overlap evidence.
No implementation, normative sync, archive or writes outside assigned phase and scope.

## Hard invariants (load definitions and controlling sources when applicable)
CI-01 loss; CI-02 gaps; CI-03 equal retry; CI-04 conflict; CI-05 duplicates.
CI-06 atomicity; CI-07 identity; CI-08 replay equivalence; CI-09 point-in-time.
CI-10 ordering; CI-11 concurrency meaning; CI-12 provider boundaries.
CI-13 honest degradation; CI-14 owned transactions; CI-15 bounded work.
Definitions: docs/CORE_INVARIANTS.md; PP is the sole TR risk authority.
Financial precision and secrets require N and O even without dedicated CI IDs.
Module API edges only; no shared/cross-module persistence or provider I/O in transactions.
No execution/signing or unapproved architecture/dependency/preview changes.

## Task routing
| Task | Required skill/procedure and extra sources |
| New contract | propose + PP; affected accepted specs, M, D |
| Revise contract | update + PP; existing artifact graph |
| Explore/provider spike | explore + SE; provider M, O/N when relevant |
| Implement module behavior | apply + PB; M, T; A/N by impact |
| SQL/migration/persistence | PB; A, T, O, N; real PostgreSQL |
| Review | verify + PR; fixed triggers, CI subset, controlling sources |
| Close after approval | PC; verified task evidence and complete gate |
| Sync accepted specs only | sync + SS; explicitly authorized scope and independent review |
| Archive | archive + PA; gate PASS and scoped checkpoint first |

Use PROJECT_SUMMARY for onboarding; DELIVERY_PLAN for stage questions.
Use O for runtime/configuration/security and provider resource budgets.
Use N for time, financial facts, datasets, signals, evaluations and reports.
Use T when selecting/changing tests; required completion checks stay mandatory.
Only verified work gets checked off. Never weaken establishing tests after freeze.
Report exact blocked commands/causes; a failed required check cannot be waived by prose.
Preserve relative links, English code comments/names, and exclude secrets/IDE state.
```

### Skill entry points and references

Each proposed description is one sentence. Entry points load OS plus their operation guide; examples, output samples and rare store details should live in references and load only when used. Keep a short store-detection rule in OS so rare details cannot be missed when relevant.

| Skill | Proposed description | Required operation references |
|---|---|---|
| propose | Use when creating a new, testable OpenSpec contract for an authorized scope. | OS, PP, SP artifact dependency closure |
| update | Use when revising existing OpenSpec planning artifacts without changing implementation. | OS, PP, SU coherence and existing-path rules |
| explore | Use when investigating a question or provider spike before committing to implementation. | OS, SE read-only discovery and scoped capture rules |
| apply | Use when implementing an approved OpenSpec contract in the assigned Builder phase. | OS, PB, SA task selection and progress |
| verify | Use when reviewing implementation evidence against an OpenSpec contract. | OS, PR, SV requirement/scenario mapping |
| sync | Use when an authorized operation must merge delta specs into accepted specs without archive. | OS, SS merge and retirement safeguards |
| archive | Use when closing a verified change after approval, full-gate PASS and a scoped checkpoint. | OS, PA, SC CLI/store details |

Merge common selection/root/status/context machinery, not all seven workflows. Keep propose versus update because creation and existing-only revision have different permissions. Keep explore because discovery does not authorize code. Share review evidence with verify; keep project approval authority in PR. Share sync semantics with archive but use CLI archive in the project flow. Delete redundant output demonstrations and generic encouragement from entry points; retain one optional examples file where useful. Do not delete retirement, immutable-merge, test-freeze or recovery edge cases to hit a token target.

## Six task traces

These traces distinguish necessary context from context avoidable in a particular phase. “Ordinary” does not imply low risk: Architect still checks every TR trigger. A code review request alone does not authorize implementation or a full close workflow.

| Task | Current instruction path | Proposed path and preserved checks |
|---|---|---|
| Ordinary bounded module change | Root → config → summary → full MULTIAGENT → Architect → propose/status context → broad docs; then Builder role → apply → all artifacts/M/T/CI; same Architect review unless accepted-spec delta or CORE_RISK. | R + P0 + PP + SP + affected artifacts/M/D/code/tests; Builder adds PB/SA/T; review adds PR and applicable CI. Keep overlap scan, fixed risk, behavioral RED/GREEN and final gate. Load A only as needed to establish boundaries; do not omit it for architectural impact. |
| Database migration | Same base, plus migration/module docs, A/T/O/N, test infrastructure and likely accepted-spec delta. | PP assesses TR-05 and all other triggers; PB loads migration/Testcontainers procedure, real PostgreSQL, owning schema/path and query-backed indexes; PR checks CI-01/03/04/05/06 and all additionally affected invariants; fresh Reviewer. No archive mechanics during Builder phase. |
| CORE_RISK persistence repair | Whole workflow repeatedly supplies freeze, retries, risk and archive rules; Builder Sol plus Reviewer and CI list. | Fixed plan/TR evidence → PB + owning persistence contract → PR; real concurrent/sequential retry and rollback evidence. Preserve CI-03/04/05 distinctions, identity, database uniqueness and unchanged hash; new risk returns to PLAN, remaining task-wide repairs do not reset. |
| Provider spike | Explore alone adds 4,843 tokens and catalog/context/spec inventories; summary/provider contract/M/O/N may be relevant. | SE + focused provider change/accepted contracts + provider M/O and N when comparing timestamps/prices/evidence. Read-only discovery requires no code gate; a spike that writes an adapter or changes behavior needs PLAN. TR-09/TR-12 apply when recovery/secrets behavior changes, not merely because a discussion mentions a provider. Never invent fallback evidence. |
| Stable code review | Root + full workflow + Reviewer/Architect + verify + CI + artifacts, diff, tests and RED/GREEN. | PR + review capsule + relevant CI definitions/sources + stable diff/tests. No propose/explore/archive guide. Fresh Reviewer for CORE_RISK or normative accepted-spec change; applicable invariants get concrete verdicts, excluded IDs get reasons. Read-only review never silently runs apply. |
| OpenSpec close | Root/workflow + Architect + archive body, possibly sync body with conflicting generic move/confirmation path. | PC after APPROVE → Main complete gate → PA checkpoint → Architect CLI archive → Main scope/post-checks. SS only if explicit sync work requires semantic merge guidance. Preserve raw hashes/index and exact recovery; no new semantics in DOCS_CLOSE. |

Concrete source examples for these traces: an ordinary signal-domain change reads `docs/modules/signal.md`, `openspec/specs/signal-evaluation/spec.md` and ADR 0008 topology; a marketdata migration adds `openspec/specs/marketdata-storage/spec.md`, `openspec/specs/database-bootstrap/spec.md`, ADR 0002 JDBC and ADR 0004 ownership. An evaluation persistence repair additionally uses `docs/modules/evaluation.md`, ADR 0005 transactions and ADR 0009 reproducibility. Provider discovery can start at active `openspec/changes/define-solana-data-provider-contract/` and `docs/modules/marketdata.md`, adding ADR 0006 for proposed bounded background work; existing provider spike tasks require owner credentials and potentially paid tiers, and S1 includes at least 24 hours of evidence, so a provider spike cannot be budgeted as an ordinary implementation pass. Review follows the actual changed capability, for example `signal-evaluation` with ADRs 0005/0009. Close follows the selected active delta and its corresponding accepted spec, adding `openspec/specs/repository-conventions/spec.md` for the post-archive convention contract. These examples do not require every named ADR on every task.

### Reviewer invariant subsets by risk category

These are starting subsets, not exhaustive exemptions. Architect assesses all twelve triggers once; Reviewer adds any invariant affected by the actual diff and escalates new risk/scope. Preserve a compact list of excluded CI IDs with a reason; do not silently omit them. Financial/secrets obligations remain direct source obligations until an authorized change adds CI IDs.

| Trigger | Minimum review starting set | Additional controlling evidence |
|---|---|---|
| TR-01 persistence | CI-01,03,04,05,06 | Owned schema/repositories, canonical equality, uniqueness and durable rollback |
| TR-02 transactions | CI-06,14 | Application boundary, atomic failure, no provider I/O |
| TR-03 concurrency/locking | CI-03,04,05,11,15 | Real transactions, races, locks and bounded waits |
| TR-04 idempotency/retry | CI-03,04,05,11,15 | Equal and conflicting retry; no duplicate outcomes |
| TR-05 migration/data loss | CI-01,03,04,05,06 | Forward migration, constraints, preservation and real PostgreSQL |
| TR-06 point-in-time | CI-08,09 | N and accepted cutoff/admission semantics |
| TR-07 financial/exact arithmetic | Direct N; add CI-07/09/10/11 where affected | Units, precision, scale, rounding and boundary tests |
| TR-08 identity/ordering | CI-07,10,11 | Chain-aware canonical identities and immutable tie-break |
| TR-09 provider gaps/recovery | CI-01,02,12,13,15 | Gap observability, provenance, failure/fallback and bounded recovery |
| TR-10 module boundaries | CI-12,14 | Accepted ADR, A/M and `ApplicationModules.verify()` |
| TR-11 reproducibility/integrity | CI-01,07,08,09,10,11 | Manifest, dataset lineage/cutoff/version/seed and equivalent replay |
| TR-12 security/secrets | Direct O; other affected CI IDs | Secret handling, configuration, access and failure semantics |

## Complete rule relocation ledger

The ledger maps every distinct obligation in the audited root, roles, seven skill bodies and referenced workflow/core protocol. Repeated clauses are grouped by meaning; a row listing multiple clauses retains each clause. “Move” means retain the complete rule, including qualifications, at the named destination. “Delete duplicate” removes only a repeated rendering. “Replace conflict” is a proposed policy reconciliation requiring explicit authorization; it is not permission to ignore the current contract. Examples/frontmatter are accounted for separately. Domain sources linked from these rules remain controlling; this audit does not rewrite their substantive requirements.

### Root guide

| Current rule/location | New location or explicit deletion reason |
|---|---|
| Opening research-first synchronous monolith; one repo/module/JAR/database | R summary; A/D retain full architecture |
| Source responsibilities: Roadmap, Delivery Plan, ADR, Architecture, Tech Stack, Reproducibility, accepted/active OpenSpec, code/tests, summary/glossary, notes/archive; no universal priority | R source map; full source registry in P0 |
| Accepted/superseding ADR updates Architecture; inconsistency is defect | A/D and PP conflict handling |
| Active change becomes current only after implement/verify/archive; main-spec divergence defect or tracked migration | PP and PA; R active/accepted distinction |
| Roadmap cannot override ADR; unresolved conflicts in change and handoff | PP conflict handling and R source map |
| Read root + summary before changes | R already loaded; summary becomes onboarding/product trigger, removing unconditional reread as navigation-only duplication |
| Read relevant active change | R; PP/PB/PR concrete artifact inputs |
| Read affected module docs and nearest AGENTS; nearer rules cannot relax root | R and M; preserve future nested-guide discovery |
| Read applicable ADRs; inspect code/tests before editing | R and PP/PB |
| Operations for runtime/config; Testing for test levels; Reproducibility for time/financial/data/signal/evaluation/report work | R task routing to O/T/N |
| Native Codex/project roles/queue; no Orca unless explicit current-task request; generic agents request insufficient | R and P0; remove repeated copies only |
| Default Sol/medium; model does not choose mode; workflow owns routing | P0 and `.codex/config.toml`; short R pointer |
| Exact true selects MULTIAGENT+applicable role; false/missing/invalid fail-closed DEFAULT | R; config parser/convention checks retained |
| Never edit switch or change mode for risk; only owner enables; mode fixed unless explicit restart/continue after switch change | R and P0 |
| DEFAULT Control owns contract/planning/docs/review/completion/archive; Developer tests+implementation+red/green/full local gate | PD; delete DEFAULT duplication from MULTIAGENT startup |
| DEFAULT no specialized protocol; one consolidated repair, further repair owner decision | PD |
| Package root and exactly six named modules | R summary and A; convention tests |
| Complete allowed/forbidden dependency DAG; cross edges only module::api | A/M/D; R boundary reminder |
| Each module owns domain/use cases/persistence/adapters/migrations/tests | A/M |
| One owner per table/SQL/repository/mapper; no cross-module SQL/reuse | A/M and CI-12/14 |
| No top-level domain/service/repository/persistence/provider/controller/util/common packages | A package layout; convention check candidate |
| No cycles or another module's implementation imports | A/M and module verification |
| Synchronous APIs; no persistence/provider/reactive types | A/M and CI-12 |
| Boundary/direction changes need accepted ADR+module docs first | PP and A/D; R reminder |
| No signing/order submission/PAPER/LIVE/executable governance gates; execution requires approved change+ADR | A/D and PP; R reminder |
| Java 25, no preview except exact feature approved in change+superseding ADR | TS/D and PP; R reminder |
| Immutable objects, records, constructor injection, small cohesive classes, explicit errors, genuinely closed sealed types, Optional for absence | TS Java conventions; remove repeated generic advice from root only |
| Synchronous MVC, Data JDBC, JdbcClient | TS/A |
| Virtual threads suitable blocking I/O; bounds on provider concurrency/rate, DB, batches, queues, timeouts/retries; bounded platform executors for CPU | O/A and CI-15; PB when relevant |
| Inject Clock/reference Instant; no direct machine time | N and reproducibility conventions |
| Exact raw units/decimals with precision/scale/rounding; no float/double financial facts | N; R financial source trigger |
| Total order/stable tie-break; explicit recorded research seed | N and CI-10/11 |
| Banned technology/dependency list and no microservices/Maven modules | TS/A and Maven Enforcer; PP approval rules |
| No silent production dependency add/upgrade; design records; outside-baseline explicit approval | PP/TS/D |
| Flyway only schema owner; automatic mutation off | A/O; migration PB reference |
| Owned migration path; schema per data owner; kernel none; first real object before schema | A/M and migration PB reference |
| Chain-aware identity and point-in-time rules | N/accepted identity specs; CI-07/09 |
| IF NOT EXISTS/CREATE OR REPLACE only when migration-correct | A migration procedure |
| Index requires documented query, no speculation | A/M migration procedure and PR |
| Real PostgreSQL/Testcontainers migrations | T/PB |
| Behavior/dependency/schema/architecture change requires active change | R/PP |
| Installed CLI scaffolds changes; no hand-created metadata/generated skills | OS/SP/PP |
| Read proposal/deltas/design/tasks before apply; checkbox only after verification | PB/SA inputs; PC owns verified checkbox mutation |
| Testable SHALL with WHEN/THEN/AND | PP and OpenSpec artifact rules |
| Strict validate before ready; archive only after implementation+verification | PP/PA |
| @Transactional on owning application use case, not controller/repository | A/M and CI-14 |
| No provider I/O transaction; no other module table mutation | CI-14/A |
| Bounded batch market data; low-frequency completed facts for Modulith events; durable delivery separate ADR/change | A/D/O; PP for change approval |
| One JAR multiple roles; recurring work idempotent and PostgreSQL-claimed across instances | A/O, CI-03/05/15 |
| Build/source, algorithm/config, fingerprint, cutoff and seed in runs | N and CI-07/08/09/10 |
| Smallest module boundary tests; unit/domain, Modulith/module, Testcontainers/Postgres; architecture verify; T/O | T/PB/PR |
| DEFAULT behavioral assertion RED before code; compile/discovery/config/startup/infra not RED | PD/T; shared definition PB for MULTIAGENT |
| No weakening/disabling/skipping/narrowing RED test, fitting expectation to code or production test-artifact behavior | PD/PB/PR |
| No-new-test exceptions for docs/comments/format/mechanical config/rename/internally covered refactor | T/PP test_mode decision; checks still required |
| Four exact root gate commands; Unix wrapper equivalence | PC/PD; workflow adds git diff --check |
| Windows host Docker preflight/retry; sandbox error not downtime or repair; unavailable only same-context docker version failure | O Docker recipe required by PB/PC; PR checks evidence |
| Exact skipped/blocked command+cause; no completion with failed required check | R/PC/PD |
| CI stable quality-gate/reports; branch protection admin only after authorized push+first green | PC/O; no implied push authorization |
| DoD active artifacts/task alignment; ownership/API/DAG; behavior+migration tests; gates or explicit blocker | PC/PR; blocker reporting does not authorize DONE |
| DoD docs/relative links/no export markup; no unrelated changes/secrets/IDE committed | R/PC and convention checks |
| Correctness/PIT/idempotency/bias before style | PR |
| Reject hidden coupling/shared persistence/API bypass | PR and CI-12/14 |
| Reject future leakage/silent outcome loss/fabricated fallback/unbounded concurrency | PR and CI-01/09/13/15 |
| Review transactions/failure/migration/indexes | PR applicable checklist |
| Dependency/boundary changes blocked absent OpenSpec+ADR approval | PP/PR |
| Reproducible tests+concrete output; comments/naming English | T/PR and R |

### Roles and core protocol

| Current rule/location | New location or explicit deletion reason |
|---|---|
| All role name/description/model/effort/sandbox metadata | Keep in each role TOML; no invented include mechanism |
| All roles no subagents; only allowed states; STATUS prefix | Minimal role stubs + P0; preserve exact per-role allowlist |
| Architect assigned phases only; no phase expansion | Architect stub + P0 |
| Architect PLAN WHAT/WHY/PLAN, sources/code/tests, active artifacts | PP |
| Sole risk classifier; twelve TR definitions; matched trigger ⇒ CORE_RISK, none assessed+ROUTINE/STANDARD reason | PP, single definition; CI and role stubs link it |
| Applicable invariants/controlling sources/test_mode/bounded budget; strictly valid testable ready contract | PP |
| openspec list + active same-spec overlaps; none/disposition, resolve first/block unsafe | PP |
| Risk fixed; only owner can lower; new risk/scope CONTRACT_CHANGED/RISK_CHANGED before dependent work | P0/PP; phase references point to this single rule |
| Same Architect routine/standard/non-normative; fresh Reviewer CORE_RISK or accepted-spec change including delta; fresh precedence | P0/PR; keep normative-delta qualification |
| Architect DOCS skips Builder; REVIEW stable diff/tests/evidence/budget/invariants and N/A reasons | P0/PR |
| Consolidated verdict, no APPROVE WITH CHANGES; third repair explicit flag; TEST_SPEC_ERROR requires new RED/hash; red_suspect only suspicious | PR |
| Architect REPAIR own docs then PLAN_READY for review; never self-approve repair | P0/PR; a subsequent eligible review remains a separate phase |
| DOCS_CLOSE after APPROVE status/checkbox/evidence/non-semantic only, return APPROVE; semantic ⇒ PLAN via ESCALATE; no archive yet | PC |
| Architect ARCHIVE requires full gate+checkpoint; CLI only authorized change; Main mutation/post/recovery/DONE | PA |
| Architect writes active change/task docs/README only; forbidden source/tests/specs/config/governance/ADR except exact authorized scope | P0 role write matrix; Architect stub reminder |
| Three Builders HOW/CODE/TESTS one BUILD/REPAIR pass; no redesign/budget expansion | PB + each Builder stub |
| Builders read active artifacts/applicable CI/M/code/tests first | PB |
| RED_REQUIRED smallest meaningful test first, PostgreSQL when required; behavioral assertion vs invalid RED classes | PB/T |
| RED record test paths/hash/command/named assertion/preimplementation diff; continue without Main RED turn | PB |
| Validate requirement/expected/actual before freeze and repeat in BUILD_DONE; fix wrong target/assertion/setup and rerun before freeze without approval | PB; preserve distinction from post-freeze defect |
| RED_NOT_REQUIRED Architect reason+named existing checks; no artificial RED or ceremonial diff | PP/PB |
| Minimal implementation, targeted GREEN, verify frozen hash; no full gate Builder | PB; Main PC ownership |
| Frozen tests/expectations/fixtures/snapshots/discovery/runtime cannot weaken/skip/narrow/retag/reconfigure/regenerate/relocate; no production test recognition | PB/PR |
| Contract defect or frozen test defect ⇒ BLOCKED exact conflict/CONTRACT_ERROR or TEST_SPEC_ERROR | PB/PR |
| Reviewer-authorized test correction only requires_new_red + new valid RED/hash | PR/PB |
| Builders no OpenSpec/docs; dependencies/migrations/contracts/architecture/fallback/unrelated work only if accepted contract | P0/PB; SA removes conflicting checkbox instruction |
| BUILD_DONE changed files/test_mode/RED/GREEN/tests_changed_after_red/risks | PB return schema |
| Reviewer independent fresh thread, no planning participation, read-only/no patch | Reviewer stub + PR |
| Reviewer contract/transaction/rollback/Postgres uniqueness+locking/sequential+concurrent retry/order/PIT/identity/loss/boundary/migration/bounds/tests as applicable | PR/CI and T |
| Reviewer validates RED sequence with preimplementation diff/hash, legitimate exemption, red_suspect rerun; no sandbox-only Docker blocker | PR |
| Reviewer lead blocking findings, single owner+bounded repair; round three flag or escalate; survivor after third escalates | PR/P0 |
| Reviewer semantic changes reopen PLAN; docs same Architect/code same Builder; owner ambiguity direct, technical bounded escalation | P0/PR/PE |
| Escalation exact question, contract/CI/diff/tests/evidence, no restart/redesign/scope expansion | PE + Escalation stub |
| Escalation read-only/no broad patch; no reclassification; owner intent direct | PE |
| Escalation verdict REPAIR→REPAIR; REPLAN→ESCALATE CONTRACT_CHANGED/ESCALATION_REPLAN; APPROVE→closure; OWNER_DECISION→BLOCKED | PE/P0; attributes do not add workflow states |
| Two ordinary repairs + current-reviewer-authorized third; no fourth, no reset by replan/escalation; every repair reviewed | P0; keep authority flag in PR |
| Session loss fresh same role/model with contract/diff/evidence/items/remaining budget; preserve independence/report replacement; no automatic owner decision | P0 recovery |
| Exhausted substantive defect/technical dispute/unbounded unsafe repair ⇒ fresh Sol High challenge; earlier unresolved source question allowed | PE |
| Escalation cannot grant capacity; no remaining round ⇒ owner; owner ambiguity bypasses technical escalation | PE/P0 |
| Main intake/scope/routing/full gate/DONE only, no implementation/redesign/normal duplicate review/risk classification | P0 |
| Main phase-boundary/blocker communication; owner intent/scope/downgrade | P0 |
| Role model table; pinned Luna efforts; docs skip Builder; historical benchmarks not routing | P0 and role/config metadata |
| Canonical seven states; Main alone DONE; invalid status one correction no artifact repair | P0 |
| Attribute enum risk/triggers/test_mode/tests_changed_after_red/reason/subreason/blocked_reason/requires_new_red/repair_round/third_repair_authorized/red_suspect | P0 schema, all values retained |
| Capsule goal/scope, phase, risk/triggers, test_mode/reason, change/scenarios, invariants/sources, budget/paths, reads/checks/evidence, repairs, return route; self-contained no history | P0; capsule contains IDs/links and evidence, not repeated manuals |
| No raw logs/timestamps/phase IDs/separate production manifest required | PB; avoid reintroducing ceremonial evidence |
| Workflow diagram and explanation of author-completion/semantic vs nonsemantic/infra edges | Optional P0 diagram; normative transitions in P0/PC/PA preserved |
| Full gate after DOCS_CLOSE: integrity script, clean verify, strict all validate, doctor, diff --check; Docker preflight first | PC exact recipe |
| Any nonzero blocks; no skip/narrow; Builder failure consumes next repair then review+full gate; docs defect REPAIR/PLAN; infra/preexisting BLOCKED | PC/P0 |
| Pre-archive status/HEAD/index identity/path list/mutations; temporary index+commit-tree+task ref; live branch/index unchanged | PA |
| Only task-owned+declared accepted specs; exclude owner hunks/hooks/IDE/MCP; unsafe separation BLOCKED | PA |
| Raw blobs hash-object -w --no-filters; path existence/SHA256/status; attributes/filters and literal restoration proof | PA |
| Recheck HEAD/affected hashes before archive; never overwrite concurrent edits | PA |
| Architect openspec archive <id> --yes; only active tree/new dated archive/declared specs mutate | PA |
| Main exact before/after allowlist then strict all validate, doctor, RepositoryConventionsTest, diff --check; DONE only after pass | PA |
| Failure concurrent-edit check, restore exact raw bytes/existence, remove only new archive, verify hashes+real index | PA |
| No reset-hard/whole-tree checkout/clean/stash owner work/manual reverse accepted patch; unsafe recovery BLOCKED before destructive action | PA |
| Restored active change correction by owner, semantic planning/review, full gate/fresh checkpoint/archive/post-checks; no retry-budget reset | PA/P0 |
| Telemetry dispatch/return and available counters unless log writes prohibited; preserve formats/aggregation; allowlists not authority; test quality separate | P0 optional execution reference; no telemetry on this read-only task |
| DEFAULT test-impact/development/review/complete-gate rules | PD retained; no duplicate MULTIAGENT import |
| CORE_INVARIANTS derived not replacing ADR/A/N/M/specs; conflicts resolved before approval | CI intro; R/PR pointer |
| Apply affected invariants, no silent omission; Invariant/Applicable/Evidence/Verdict, N/A reason, no blanket evidence | CI/PR |
| CI trigger crosswalk only; no second trigger list; routing/new-risk rules duplicated there | Keep crosswalk; delete duplicate routing text in favor of P0/PP link |
| CI-01 accepted inputs/candidates/outcomes/failures measurable or explicit terminal; no silent loss | CI-01 unchanged |
| CI-02 reconnect/retry/backfill/degradation/recovery gaps explicit/observable | CI-02 unchanged |
| CI-03 same canonical/immutable retry same durable effect/evidence | CI-03 unchanged |
| CI-04 identity match insufficient; changed immutable content explicit conflict | CI-04 unchanged |
| CI-05 sequential+concurrent identity/uniqueness, no duplicate owned outcome | CI-05 unchanged |
| CI-06 one short application transaction, rollback leaves valid committed state | CI-06 unchanged |
| CI-07 result-changing parameters canonical identity or immutable version/fingerprint | CI-07 unchanged |
| CI-08 equivalent replay/forward semantics for same evidence/cutoff/version/config/seed unless accepted distinction | CI-08 unchanged |
| CI-09 cutoff-admissible evidence; no later observation rewriting past decisions | CI-09 unchanged |
| CI-10 immutable total tie-break independent of input/concurrency/DB/provider order | CI-10 unchanged |
| CI-11 concurrency changes throughput, not values/identity/order/count/evidence | CI-11 unchanged |
| CI-12 provider payload/client/pagination/retry/fallback behind owning API | CI-12 unchanged |
| CI-13 no fabricated evidence/weakened freshness/finality/hidden failure; explicit degradation+provenance | CI-13 unchanged |
| CI-14 owning application transaction, not controller/repository, no provider I/O/cross tables | CI-14 unchanged |
| CI-15 explicit concurrency/DB/rate/retry/backoff/batch/queue/poll/wait bounds including failures | CI-15 unchanged |

### Skills and OpenSpec context

| Current rule/location | New location or explicit deletion reason |
|---|---|
| All frontmatter name/license/compatibility/metadata | Keep identity/license/version provenance; tool hints reviewed for actual environment, no new authority |
| All seven store discovery/sticky --store supported-command list/local-root fallback/follow-up hints | OS; rare details separate required store reference when store detected |
| All select provided/inferred/sole change; ask ambiguous; announce selected+override | OS; operation filters below retained |
| All schema/status planningHome/changeRoot/artifactPaths/actionContext, full capability path and no guessed names | OS |
| All context/rules constrain, never copied; guidance advisory, report conflicts/inapplicability, preserve user/CLI controls and state, not completion proof | OS; keep apply/archive field-specific behavior |
| Propose planning artifacts only, no code; explicit new user request before apply | SP planning phase boundary retained; replace blanket invalidation of already explicit owner authorization with P0 phase routing |
| Propose understand outcome; kebab name; material scope/behavior/compatibility/acceptance questions; minor recorded assumptions; collision ask | SP/PP |
| Propose context --json root; no root/error stop, init only requested; no cwd/store fallback | OS root resolution |
| Propose config yaml preferred, yml only absent; unreadable/invalid yaml no fallback; independent string context <=51,200 UTF8 bytes, invalid context ignored | OS configuration parsing reference, preserve exact limits |
| Propose configured schema unless explicit request; list workflows at resolved root/selected store then owner chooses | OS/SP |
| Propose CLI scaffold metadata; status graph applyRequires+transitive requires even done; not existence-only | OS/SP |
| Propose instruction/template/context/rules/dependencies/output paths; reread dependencies from disk; delegated artifact skill/command; concrete glob output exists | OS/SP; disk reread retained at write boundary to detect concurrent edits |
| Propose proportional read-only actual target code/tests/config/docs before drafting; greenfield/source-missing limitations; distinguish observation/assumption/proposal/conflicts; no generic investigation task deferral | PP/SP |
| Propose skip_specs never create; conditional skip only own instruction; record/tell/no reconsider; blocked solely by skipped condition may proceed | SP artifact graph reference |
| Propose todo/progress/status after each artifact, required closure only, final status/location/artifacts/skips/ready summary | SP; verbose output sample deleted as redundant |
| Update no code, existing planning only; coherence in every direction; no edits if coherent | SU |
| Update choices top 3–4 recent/schema/status/time/recommended; optional continue/new availability check and CLI fallback | SU selection reference |
| Update concrete existingOutputPaths only, never glob resolvedOutputPath; no new artifacts/files or advancing frontier | SU |
| Update show reason and confirm every artifact/rejection unchanged; substantial rewrite instructions | SU show meaningful changes; replace repetitive confirmation where explicit authorization already covers scope; never treat rejected revision as authorized |
| Update next-step advice only/no implementation; missing artifacts continue fallback; intent change distinct name/new workflow | SU and P0 phase routing |
| Update output revised/rejected/deferred/next command | SU compact result schema |
| Explore read-only thinking, no code/config/schema/template edits; captured artifacts only within confirmed scope | SE |
| Explore separate yes/no before first write/new scope; answers/silence not consent | SE preserves no inferred authorization; replace unnecessary repeat consent when explicit owner instruction already names exact capture |
| Explore curious/adaptive/patient/grounded stance, open threads, no forced scripts/output/ending/briefness | Delete generic personality boilerplate from entry point; optional examples retain useful teaching context |
| Explore inspect facts before asking, limited disclosure, missing/conflicting evidence report, dependent decision order, focused questions, grounded recommendations/no invented intent | SE concise discovery procedure |
| Explore conversational decision record confirmed/default/open; stop questions when clarity enough, allow pivot/defer | SE |
| Explore ASCII-only diagram characters and visualize when useful | Optional SE examples/style reference; no mandatory diagrams |
| Explore list changes and specs; overview --no-scenarios then full relevant scenarios before coverage decisions; root config/context/artifact rules | OS/SE; add explicit root resolution instead of undefined root.path |
| Explore new capture CLI scaffold; requested artifacts only; conditional prerequisites deliberate skip or ask scope expansion; no unrequested nonconditional artifact | SE capture reference |
| Explore status/instructions/dependency/template/delegated skill/concrete glob/existence/status loop; remember skips; scaffold-only stop when only requested | OS/SE |
| Explore existing artifact status paths/read/natural reference; insight→spec/design/proposal/tasks mapping; offer capture/no pressure | SE |
| Explore multiple long example conversations, diagram gallery and optional ending summary | Move to optional examples; delete repeated guardrail renderings, not distinct protections |
| Apply schema/status/instructions apply; all concrete contextFiles; required context/advisory guidance; no state bypass/copy | OS/SA |
| Apply blocked fallback status/instructions or available continue; all_done report; no inferred completion from context | SA |
| Apply show schema/progress/remaining/dynamic instruction; loop minimal tasks; continue until blocked/done/user interrupt | SA/PB within assigned budget |
| Apply immediate complete checkbox only full behavior, never partial/deferred | Replace author conflict: PB evidence → PC Architect verified checkbox write |
| Apply unclear/error/design/out-of-scope/narrow/defer/exception needs surfacing, no silent guess or narrowing | SA/P0 scope escalation; owner intent direct |
| Apply fluid invocation/interleaving/artifact updates | Replace phase-free wording with P0 authorized phase transitions; preserve ability to resume partially implemented change |
| Apply verbose progress/completion/pause templates and congratulations | Delete generic output samples; PB result schema retains all evidence and blockers |
| Verify select only task-bearing changes, schema/In Progress labels | SV/OS |
| Verify status + apply instructions + every available context file | SV/OS |
| Verify completeness task counts/incomplete item, every requirement mapping; correctness scenario/code/test evidence; design/pattern coherence | SV/PR |
| Verify CRITICAL/WARNING/SUGGESTION actionable file refs and summary scorecard; disclose skipped missing artifacts | SV report reference; project PR determines blocking severity |
| Verify keyword/inference likely coverage, prefer lower severity uncertain | Retain discovery heuristic only; PR requires concrete evidence for required behavior and unresolved uncertainty cannot approve |
| Verify ready-for-archive with warnings/all clear | Replace completion claim with PR verdict; PC/PA gates remain necessary |
| Archive active-only selection/status schema; optional instructions archive lookup fail-open invalid/older CLI only for advisory input | SC/OS; failure of actual archive/gate never ignored |
| Archive artifact done/skipped and tasks counts; no tasks warning when absent; warn+confirm incomplete | SC inspection retained; replace permission to complete incomplete change with PC/PA blocker/explicit owner scope resolution |
| Archive only status specs.existingOutputPaths; absent means no inferred deltas; compare every main/delta at store root | SC/SS |
| Archive combined summary and sync/skip/cancel choice; invalid answer ask; no concurrent background sync | SC summary/cancel preserved; project PA controls CLI sync/archive route, no generic bypass of acceptance requirements |
| Archive one valid specs instruction snapshot, nonzero/invalid stop before write, missing rules valid; rules content only/no copying | SS/SC |
| Archive inline sync reuses snapshot and waits; compare all capabilities after, including adds/mods/removes/retire/renames; mismatch stops movement | SS/SC consistency reference; CLI archive PA still controls mutation |
| Archive date no double prefix, collision stop, metadata preservation; manual mkdir/mv | Keep naming/collision/metadata invariant in PA/SC; delete manual mutation recipe conflicting with CLI-only project flow |
| Archive summary schema/location/sync/warnings and no false synced claim | PA/SC exact evidence summary |
| Sync status root/main path; only specs.existingOutputPaths; explicit exact subset never widen; invalid or empty stop before writes/instructions | SS |
| Sync one rule snapshot direct or reuse archive; fail-closed invalid response; omitted rules okay; no root/path/workflow override | SS/OS |
| Sync read delta+main; ADDED upsert, MODIFIED preserve surviving scenarios/body/order, RENAMED from→to, REMOVED whole block | SS semantic merge reference |
| Sync retirement requires removal this run, no remaining blocks, wellformed Purpose, not already empty, no unaccounted sections, retire_capabilities true, real-root containment/no symlink escape | SS exact retirement algorithm; all conditions retained |
| Sync failed retirement no main modification/empty Requirements; report condition/marker; report deleted Purpose and checkout-scoped recovery | SS; project PA safe scoped restoration takes precedence over generic checkout example |
| Sync existing Purpose unchanged; new capability copies delta Purpose or flagged TBD, add Requirements | SS; final project contract must resolve required unfinished content before approval |
| Sync no delta headers in main; complete MODIFIED block; preserve unmentioned content/order; idempotent repeated sync | SS |
| Sync validate --specs selected root; failures no success; summary updates/add/remove/rename/new TBD/retirement | SS; project strict all-item close verification additionally PC/PA |
| Sync format examples and success template | Move to SS optional examples; retain format algorithm in mandatory guide |
| OpenSpec config context baseline and all-docs read list | Baseline pointers R/TS/A; replace broad list with R task/trigger routing |
| Config proposal non-goals/modules/no silent dependency/boundary; specs testable observable SHALL not classes | PP artifact-specific rules unchanged |
| Config design ownership/transaction/verification/dependency+infra justification; tasks incremental architecture/tests/docs/exact checks | PP artifact-specific rules unchanged |
| Config apply preserve unrelated/checkbox after verification; archive strict+Maven before archive | P0/PC/PA; align SA author ownership |

The linked Testing protocol repeats RED/exemption/risk/review/freeze/gate/archive obligations already mapped to PB/PR/PC/PA. Preserve its test-level table, PostgreSQL isolation/concurrency rules, reproducibility test recipes, fixture limitations and Maven lifecycle in T; do not turn selective protocol loading into weaker test selection. Operations and Reproducibility remain full controlling domain references when triggered, rather than being replaced by CI summaries.

## Enforcement and migration constraints

| Existing enforcement | What it actually establishes | What still needs instructions/review |
|---|---|---|
| `src/test/java/io/cryptoresearch/CryptoResearchApplicationTests.java:24` and `:33` | Modulith verification, six modules and declared dependency graph | Semantic table ownership, provider isolation, no hidden dynamic coupling |
| `pom.xml` Maven Enforcer rules | Java baseline and configured banned dependency coordinates | Unapproved upgrades, architectural rationale, financial arithmetic and every prohibited design pattern |
| `ReproducibilityConventionsTest.java:16` | Scans configured implicit clock/random patterns and tests scanner behavior | Semantic PIT correctness, total ordering, exact finance and every possible nondeterministic API |
| `.codex/scripts/verify-test-integrity.ps1` and its tests; repository convention integrity checks | Configured skip/select/disabled-test patterns, default lifecycle and CI preflight shape | Whether RED was meaningful or tests were secretly narrowed in semantically equivalent syntax |
| `RepositoryConventionsTest.java:171`, `:289`, `:330`, `:337`, `:346`, `:368`, `:411` | Workflow closure text, safety clauses, overlap rule, verified pre-freeze reason, session recovery, ordering and one risk authority | Agent execution, real independent review, actual hash preservation and safe restoration |
| Same test `:252`, `:783`, `:858` | Config/roles/model efforts/status sets/no spawning and mode guidance | Runtime host behavior and owner authorization |
| Same test `:72`, `:585`, `:603` | Relative-link/export-marker and selected topology documentation checks | Truth, completeness and freshness of prose |
| PostgreSQL integration tests in marketdata/signal/evaluation | Existing durable-state/retry/migration contracts exercised against PostgreSQL | New scenarios and changed requirements still need appropriate new evidence |
| OpenSpec strict validation/doctor and `.github/workflows/quality-gate.yml` | Structural contract checks and configured full CI lifecycle | Full behavioral satisfaction, reviewer independence or external branch protection |

Do not remove a human-facing invariant merely because one syntactic check exists. Reduce repeated prose while keeping a short router reminder and an unambiguous reference. Conversely, text-presence tests should not force three full copies of PB forever. A future implementation should test reachability, role/status/routing, unique risk authority and missing-safety-reference failure, while retaining meaningful semantic checks. Executable enforcement changes require an authorized active change and behavioral RED; this audit does not authorize them.

| Proposed enforcement, not currently established by this audit | Benefit and limit |
|---|---|
| ArchUnit or equivalent architecture rules for `@Transactional` placement, forbidden public API types and module-owned repository imports | Catch structural violations before review; adding a test dependency requires the normal approval process, and transaction meaning/provider I/O still needs behavioral inspection. |
| Extend Enforcer for Kafka/Redis exclusions and an explicit approved dependency baseline | Current listed exclusions do not fully encode the root prohibition; additions/upgrades still require contract review and deliberate baseline updates. |
| Migration path/schema ownership convention checks plus real PostgreSQL uniqueness, immutable-conflict and rollback tests | Automate shape and durable outcomes without replacing query-pattern justification or migration review. |
| Scoped archive helper verifies checkpoint existence, real-index identity, raw hashes and mutation allowlist before/after CLI | Make recovery preconditions machine-checkable; no automatic broad restoration or assumed owner-hunk separation. |
| Router/reference reachability and UTF-8 byte-budget script, with phase/role fixture tests | Detect missing safety references and accidental eager loading; do not use a token cap to delete mandatory safeguards. |
| Capsule/status/repair-accounting validation in the dispatch layer | Catch incompatible states and unauthorized round three; cannot establish owner intent or judge the adequacy of RED. |

## Before/after budget model

Current common floor for a fresh MULTIAGENT context is 3,088 root + 118 config + 1,396 summary + 4,586 workflow + 313 repo skill descriptions + 1,234 external descriptions = **10,735 estimated tokens**. This excludes role, loaded skill body, CI, domain docs, active artifacts, code/tests, capsule, system instructions and catalog wrappers. It counts each file once and does not charge all roles or skills to every task. Avoid adding the same mandatory read twice when a tool result already contains it.

Proposed common target is 900 root + 118 config + 200 P0 + 313 repo descriptions + 1,234 unchanged external descriptions = **2,765**. The existing seven skill names remain; no new catalog descriptions are added, and 313 conservatively retains the current cost despite shorter proposed descriptions. Conditional summary remains 1,396 when needed. Role target is 150; phase references are additive: PP 700, PB 750, PR 650, PC 450, PA 1,350; OS 350; streamlined operation guides SP 900, SA 350, SE 800, SV 450, SC 250. These are design budgets, not measured achieved reductions. Rare store, conditional-artifact and retirement references add back cost when relevant. The PA allowance is deliberately larger because safe recovery is detailed. The whole 1,935-token CI file is conservatively retained for build/review estimates; savings from reading complete relevant CI sections are not claimed here.

| Single fresh task/phase example | Current known instruction subtotal | Proposed target subtotal | Reduction in modeled subtotal |
|---|---:|---:|---:|
| Ordinary module BUILD (Luna xhigh, apply, CI, T) | 18,470 | 8,930 | 9,540 (52%) |
| DB migration BUILD (Sol, apply, CI, T/O/N/A) | 27,453 | 17,912 | 9,541 (35%) |
| CORE_RISK persistence REVIEW (Reviewer, verify, CI, T/N/A) | 24,160 | 14,991 | 9,169 (38%) |
| Provider discovery (Architect, explore, O/N) | 21,545 | 9,087 | 12,458 (58%) |
| Bounded stable review (Reviewer, verify, CI) | 15,469 | 6,300 | 9,169 (59%) |
| OpenSpec close/archive (Architect, archive, no separate sync) | 14,363 | 5,315 | 9,048 (63%) |

For reproducibility, ordinary BUILD is `10,735 + 1,064 + 2,106 + 1,935 + 2,630` versus `2,765 + 150 + 750 + 350 + 350 + 1,935 + 2,630`. Migration changes Builder metadata to 1,065 and adds O/N/A to both sides. Persistence review is common + Reviewer + verify + CI + T/N/A versus common + role + PR + OS + SV + CI + T/N/A. Provider is common + Architect + explore + O/N versus common + role + OS + SE + O/N. Bounded review omits T/N/A from persistence review. Close is common + Architect + archive versus common + role + PC + PA + OS + SC. OS is counted once per fresh operation context, not as zero-cost merely because it is shared.

The arithmetic is intentionally a workload model, not a benchmark. Keep domain bytes unchanged unless the proposal explicitly routes a narrower, complete section. Artifact/code/diff/test content and affected module/ADR/spec reads are **X**, added equally to both columns: savings percentage for total context is `(before - after) / (before + X)`, so the percentages above are ceilings when X is excluded. Broad OpenSpec context adds between the relevant-document cost and the 25,326-token all-module interpretation; the table excludes that uncertain expansion and its proposed savings. OpenSpec config/CLI-generated instruction content is also outside the fixed subtotal. Cold three-agent aggregate is a sum of each phase's load, not a context window size; same-thread Architect reuse and platform caching can reduce billed costs. No runtime latency or model-quality improvement is claimed without measurement.

## Proposed implementation order and audit verification

1. Author complete phase/common/OpenSpec references from this ledger under a separately authorized change; resolve generic skill conflicts explicitly.
2. Add reference-resolution and role/phase enforcement coverage without weakening current safety checks; establish required RED before executable behavior changes.
3. Replace root and selected role bodies with mandatory routers; keep model pins and all source/CI/TR identities.
4. Shrink skill entry points and OpenSpec context; move examples and rare mechanics into conditional references.
5. Replay the six traces using recorded file loads and actual tokenizer counters; fail the exercise if any required rule or source is unreachable, any CI is omitted, or scopes/repair/archive safety weaken.
6. Run the full authorized gate and independent review for the accepted normative change before activation.

For this report, validation is limited to source inspection, arithmetic, link/scope checks and the rule ledger. Intentionally not run: `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, and `openspec doctor`. The owner authorized a read-only audit with this single report as its output; a complete implementation/close gate is outside that scope and Maven generates build files. This is a task-scope exception, not an environmental blocker or a repository implementation-complete/archive-ready claim. Read-only `git diff --check` may be used to check the report without implying the full gate passed.
