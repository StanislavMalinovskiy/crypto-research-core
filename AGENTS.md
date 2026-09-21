# Crypto Research Core agent guide

Crypto Research Core is a research-first crypto signal evaluation system. The current deployment is one synchronous Spring Modulith modular monolith: one repository, one Maven module, one JAR and one PostgreSQL database.

## Source responsibilities

Use each source only for its responsibility; there is no universal document priority:

- `docs/ROADMAP.md`: product hypotheses, long-term capabilities and directional priorities.
- `docs/DELIVERY_PLAN.md`: operational stage sequence, current and next work, and stage exit outcomes.
- `docs/adr/*`: accepted architectural decisions and rationale.
- `docs/ARCHITECTURE.md`: current consolidated architecture.
- `docs/TECH_STACK.md`: allowed technologies and versions.
- `docs/REPRODUCIBILITY.md`: time, numeric, provenance and deterministic-result rules.
- `openspec/specs/*`: accepted observable system behavior.
- `openspec/changes/*`: proposed or partially implemented behavior.
- Code and tests: evidence of the implementation that actually runs.
- `docs/PROJECT_SUMMARY.md` and `docs/GLOSSARY.md`: navigation and terminology.
- `docs/notes/*` and `docs/archive/*`: non-normative ideas and history.

An accepted or superseding ADR must update `ARCHITECTURE.md`; an inconsistency is a documentation defect. An active OpenSpec change becomes current behavior only after implementation, verification and archive. Code that differs from a main spec is a defect or an explicitly tracked migration. A Roadmap update cannot silently override an accepted ADR. Record unresolved conflicts in the active change and handoff.

## Required reading before changes

1. Read `AGENTS.md` and `docs/PROJECT_SUMMARY.md`.
2. Read the relevant OpenSpec change.
3. Read `docs/modules/<module>.md` and the nearest module-level `AGENTS.md` for every affected module.
4. Read applicable ADRs.
5. Inspect existing code and tests before editing.

Read `docs/OPERATIONS.md` for runtime or configuration work and `docs/TESTING.md` when choosing or changing a test level. Read `docs/REPRODUCIBILITY.md` before introducing time-dependent logic, financial values, datasets, signals, evaluations or reports. A nearer `AGENTS.md` may add rules but cannot relax this root contract.

## Agent workflow mode

Project-agent transport is fixed to native Codex. Use project roles from `.codex/agents/**` for native
subagents and `codex queue` for user-created Codex sessions. Do not invoke Orca, `orca-cli`, Orca
orchestration/run/worker commands, or start the Orca application unless the user explicitly says to use Orca
in the current task. Generic requests for agents, subagents, multi-agent work, supervision, coordination, or
periodic progress updates are not permission to use Orca.

Determine the mode once at task start from `.codex/config.toml`:

- If `[agents].enabled = true` is the exact boolean setting, use `MULTIAGENT` and load [docs/AGENT_WORKFLOW_MULTIAGENT.md](docs/AGENT_WORKFLOW_MULTIAGENT.md) plus the applicable `.codex/agents/*.toml` role configuration.
- If the setting is `false`, missing or invalid, use fail-closed `DEFAULT`, do not spawn project subagents, and load [docs/AGENT_WORKFLOW.md](docs/AGENT_WORKFLOW.md).

Never edit the switch, spawn a project subagent or silently change mode because a task appears risky. You may recommend MULTIAGENT, but only the user may enable it. The selected mode remains fixed for the task unless the user explicitly restarts or continues it after changing the setting.

In DEFAULT, Control owns the active contract, planning, final documentation, stable-diff review, completion decision and archive. Developer owns specification-derived tests and implementation in one bounded pass, including behavioral red when behavior changes, targeted green, the complete local gate and a concise evidence handoff. Neither session is required to use the specialized MULTIAGENT phase, status, manifest, capsule, telemetry or role-routing protocol. Control sends one consolidated repair by default; any further repair is a user decision.

## Architecture rules

- Package root: `io.cryptoresearch`.
- Modules: `kernel`, `marketdata`, `risk`, `wallet`, `signal`, `evaluation`.
- Follow the complete allowed/forbidden dependency graph in `docs/ARCHITECTURE.md`; every cross-module edge targets `module::api`.
- Each module owns its domain, use cases, persistence, provider adapters, migrations and tests.
- Every table, SQL statement, repository and row mapper has exactly one owning module. Cross-module SQL and repository reuse are forbidden.
- Never create top-level `domain`, `service`, `repository`, `persistence`, `provider`, `controller`, `util` or `common` packages.
- No cyclic module dependencies or imports of another module's implementation packages.
- Module APIs are synchronous and must not expose persistence, provider or reactive execution types.
- Changing module boundaries or dependency directions requires an accepted ADR and updated module docs first.
- The MVP has no signing, order submission, PAPER/LIVE execution or executable governance gates. Execution requires a dedicated approved change and ADR.
- Keep one repository, one Maven module, one deployable JAR and one PostgreSQL database until an evidence-backed ADR says otherwise.

## Java and Spring rules

- Use Java 25 without preview features. A preview API requires an approved OpenSpec change and superseding ADR naming and justifying the exact feature.
- Prefer immutable objects, records for data carriers, constructor injection, small cohesive classes and explicit exception handling. Use sealed types only for genuinely closed hierarchies and `Optional` where absence is expected.
- Use synchronous Spring MVC, Spring Data JDBC and `JdbcClient`.
- Use virtual threads for suitable blocking I/O, but bound provider concurrency, rate, database access, batches, queues, timeouts and retries. CPU-bound work uses bounded platform-thread executors.
- Domain and application logic receives `Clock` or an explicit reference `Instant`; direct machine-clock reads are forbidden.
- Authoritative amounts, prices, costs, PnL, ratios and scores use raw integer units or exact decimal arithmetic with explicit precision, scale and rounding. Do not use `float` or `double` for financial facts.
- Order-sensitive work defines a total order and stable tie-break; randomized research receives and records an explicit seed.
- Do not add JPA/Hibernate, WebFlux, Reactor, R2DBC, Vert.x, Kafka, Redis, Lombok, microservices or Maven modules.
- Do not silently add or upgrade a production dependency. Document it in the OpenSpec design and obtain explicit approval when outside the accepted baseline.

## PostgreSQL and Flyway rules

- Flyway is the only schema owner; disable automatic schema mutation.
- Put each business migration under the owning module's path below `src/main/resources/db/migration/`.
- Use a PostgreSQL schema per data-owning module; `kernel` never owns a schema. Introduce a schema only with its first real owned object.
- Preserve chain-aware identities and point-in-time data rules from the Roadmap.
- Use `IF NOT EXISTS` or `CREATE OR REPLACE` only when correct for the migration model.
- Add an index only for a documented query pattern; do not add speculative indexes.
- Verify migrations against real PostgreSQL through Testcontainers.

## OpenSpec workflow

- Any behavior, dependency, schema or architectural change needs an active change under `openspec/changes/` before implementation.
- Create changes with the installed OpenSpec CLI; do not hand-create generated workflow skills or `.openspec.yaml` metadata.
- Read proposal, delta specs, design and tasks before applying. Keep task checkboxes current only after verification.
- Requirements use normative, testable `SHALL` statements and `WHEN`/`THEN`/`AND` scenarios.
- Run strict validation before declaring a change ready. Archive only after implementation and verification are complete.

## Transactions, events and background work

- Put `@Transactional` on the owning module's application use case, not controllers or repositories.
- Do not keep a database transaction open across provider I/O or mutate another module's tables.
- Keep high-volume market data in bounded batch pipelines and tables. Reserve Modulith events for low-frequency completed facts; durable delivery needs a separate ADR/change.
- One JAR may run multiple workload roles. Recurring work must be idempotent and claimed through PostgreSQL so multiple instances do not duplicate it.
- Reproducible runs identify build/source revision, algorithm/configuration version, dataset fingerprint, cutoff and seed as defined in `docs/REPRODUCIBILITY.md`.

## Testing and verification

Tests cover the smallest meaningful behavior at the owning module boundary. Use unit tests for pure domain behavior, Spring Modulith tests for module integration and Testcontainers for PostgreSQL/Flyway behavior. Every architecture-affecting change keeps `ApplicationModules.verify()` green. Follow `docs/TESTING.md` and `docs/OPERATIONS.md`.

For changed observable behavior in DEFAULT, Developer derives meaningful tests from the active requirement and observes the targeted test fail at the expected behavioral assertion before implementation. Compilation, discovery, configuration, startup or infrastructure failure is not red. After valid red, do not weaken, disable, skip or narrow the test, change its expectation to fit the code, or add production behavior for a test artifact. Documentation, comments, formatting, mechanical configuration, pure renames and internally covered refactoring may record that no new behavioral test is needed.

Required commands from the repository root:

```powershell
pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1
mvnw.cmd clean verify
openspec validate --all --strict --no-interactive
openspec doctor
```

On Windows under Codex, the restricted execution sandbox cannot access Docker Desktop named pipes. Run every
Docker/Testcontainers command, including targeted integration tests and `mvnw.cmd clean verify`, with escalated
host access. First run `docker version` in the same escalated execution context. An in-sandbox `permission
denied`, Testcontainers `docker_engine is not listening`, or Docker discovery timeout is not evidence that
Docker is down. Retry once with escalated host access; this is an infrastructure execution retry and does not
consume an implementation repair. Report Docker as unavailable only when the same-context escalated
`docker version` fails.

On Unix, `./mvnw clean verify` is equivalent. Report skipped or blocked checks with the exact command and cause; do not claim completion when a required check has not passed. GitHub Actions runs the same contract in the stable `quality-gate` job and publishes Surefire/Failsafe reports when present. Repository files cannot enable branch protection; after an authorized push and first successful remote run, an administrator must require `quality-gate` in the primary-branch ruleset.

## Definition of Done

- Active OpenSpec artifacts match implemented scope and completed task checkboxes.
- Code respects module ownership, synchronous contracts and dependency directions.
- Tests cover new behavior and PostgreSQL migrations where applicable.
- Required verification commands pass, or the handoff states the exact environmental blocker.
- Documentation and relative links are updated; no tool-export-specific markup remains.
- No unrelated user changes, generated secrets or local IDE state are committed.

## Code review rules

- Review correctness, point-in-time integrity, idempotency and measurement bias before style.
- Reject hidden cross-module coupling, shared persistence models and bypasses of public APIs.
- Reject future-data leakage, silent outcome loss, fabricated provider fallback data and unbounded concurrency.
- Verify transaction ownership, failure behavior, migration safety and query-backed indexes.
- Treat new dependencies and architectural boundary changes as blocking unless OpenSpec and an ADR explicitly approve them.
- Require reproducible tests and concrete verification output; comments and naming must be in English.
