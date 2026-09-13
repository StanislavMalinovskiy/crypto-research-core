# Crypto Research Core agent guide

Crypto Research Core is a research-first crypto signal evaluation system. The current deployment is one synchronous Spring Modulith modular monolith: one repository, one Maven module, one JAR and one PostgreSQL database.

## Source responsibilities

There is no universal document priority. Use each source only for its responsibility:

- `docs/ROADMAP.md`: product hypotheses, long-term capabilities and directional priorities.
- `docs/DELIVERY_PLAN.md`: operational stage sequence, current and next work, and stage exit outcomes.
- `docs/adr/*`: accepted architectural decisions and rationale.
- `docs/ARCHITECTURE.md`: current consolidated architecture.
- `docs/TECH_STACK.md`: allowed technologies and versions.
- `docs/REPRODUCIBILITY.md`: time, numeric, provenance and deterministic-result rules.
- `openspec/specs/*`: accepted observable system behavior.
- `openspec/changes/*`: proposed or partially implemented changes, not yet current behavior.
- Code and tests: evidence of the implementation that actually runs.
- `docs/PROJECT_SUMMARY.md` and `docs/GLOSSARY.md`: navigation and terminology.
- `docs/notes/*` and `docs/archive/*`: non-normative ideas and history.

An accepted or superseding ADR must update `ARCHITECTURE.md`; an inconsistency is a documentation defect. An active OpenSpec change becomes current behavior only after implementation, verification and archive. Code that differs from a main spec is a defect or an explicitly tracked migration. A Roadmap update cannot silently override an accepted ADR. Record unresolved conflicts in the active change and handoff.

## Required reading before code changes

1. Read `AGENTS.md`.
2. Read `docs/PROJECT_SUMMARY.md`.
3. Read the relevant OpenSpec change.
4. Read `docs/modules/<module>.md` for every affected module.
5. Read applicable ADRs.
6. Inspect existing code and tests before editing.

Read `docs/OPERATIONS.md` for runtime/configuration work and `docs/TESTING.md` when choosing or changing a test level.
Read `docs/REPRODUCIBILITY.md` before introducing time-dependent logic, financial values, datasets, signals, evaluations or reports.

Also read the nearest module-level `AGENTS.md` before changing that module, if one exists. A nearer file may add module-specific rules but may not relax this root contract.

## Optional Codex subagents

The primary agent acts as Architect and is the only role that talks to the user, spawns subagents, routes verdicts and owns the final result. Project-scoped Developer, Tester, Reviewer and Researcher roles are described in `docs/AGENT_WORKFLOW.md`. Subagents never spawn other subagents; `.codex/config.toml` enforces `max_depth = 1`.

Before every implementation task, the Architect must assess test impact. When compilation requires a new public contract, Developer first returns a behavior-free `SKELETON_READY`; Tester then owns new or changed behavioral test evidence and normally establishes a failing test from the active OpenSpec change before Developer implements production behavior. Developer never changes or bypasses tests, fixtures, expected results, test configuration or discovery, and never adds production behavior that exists only for a test artifact. Reviewer is read-only and always checks invariants, specification alignment and test adequacy.

Use Tester for observable behavior, bug fixes, public APIs, schemas or migrations, persistence or idempotency, parsers or normalization, financial and point-in-time logic, and provider contracts. Architect may record `TEST_NOT_NEEDED` for documentation, comments, formatting, mechanical configuration, pure renames, or internal refactoring already covered by unchanged tests.

Prefer one writer per file set. Give each subagent a concise task capsule with goal, scope, phase, repair round, relevant sources, constraints, acceptance criteria, checks and expected output. Reuse the same agent for corrections. Architect owns one limit of at most three repair routings; subagents must not create nested retry loops. `TEST_SUSPECT` goes to Reviewer for `TEST_WRONG`, `CODE_WRONG` or `SPEC_AMBIGUOUS`; it is not itself a repair round. Tester owns the full Maven quality gate for implementation tasks; Architect runs it when Tester is legitimately omitted. Architect owns required OpenSpec and documentation updates after the code gate and before completion. Lifecycle events are recorded locally in `.codex-logs/subagents.jsonl` by project hooks.

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
- The MVP has no signing, order submission, PAPER/LIVE execution or executable governance gates. Any execution capability requires a dedicated approved change and ADR before code is added.
- Keep one repository, one Maven module, one deployable JAR and one PostgreSQL database until an ADR backed by measured evidence says otherwise.

## Java and Spring rules

- Use Java 25 without preview features. Do not add `--enable-preview` or use a preview API without an approved OpenSpec change and superseding ADR that names and justifies the exact feature.
- Prefer immutable objects, records for data carriers, constructor injection, small cohesive classes and explicit exception handling.
- Use sealed types only for genuinely closed hierarchies. Prefer `Optional` over nullable return values where absence is expected.
- Use synchronous Spring MVC, Spring Data JDBC and `JdbcClient`.
- Use virtual threads for suitable blocking I/O. Bound fan-out and queues; use bounded platform-thread executors for CPU-bound work.
- Domain and application logic receives `Clock` or an explicit reference `Instant`; direct machine-clock reads are forbidden.
- Authoritative amounts, prices, costs, PnL, ratios and scores use raw integer units or exact decimal arithmetic with explicit precision, scale and rounding. Do not use `float` or `double` for financial facts.
- Order-sensitive work defines a total order and stable tie-break; randomized research receives and records an explicit seed.
- Treat virtual threads as execution, not admission control: bound provider concurrency, rate, database access, batches, timeouts and retries.
- Do not add JPA/Hibernate, WebFlux, Reactor, R2DBC, Vert.x, Kafka, Redis, Lombok, microservices or Maven modules.
- Do not add or upgrade a production dependency silently. Document the need in the OpenSpec design and obtain explicit approval when it is outside the accepted baseline.

## PostgreSQL and Flyway rules

- Flyway is the only schema owner; disable all automatic schema mutation mechanisms.
- Put each business migration under the owning module's migration path below `src/main/resources/db/migration/`.
- Use a PostgreSQL schema per data-owning module; `kernel` never owns a schema. Introduce a schema only with its first real owned object.
- Preserve chain-aware identities and point-in-time data rules from the Roadmap.
- Use `IF NOT EXISTS` or `CREATE OR REPLACE` only when correct for the migration model.
- Add an index only with a documented query pattern. Do not add speculative indexes.
- Verify migrations against real PostgreSQL through Testcontainers.

## OpenSpec workflow

- Any behavior, dependency, schema or architectural change needs an active change under `openspec/changes/` before implementation.
- Create changes with the installed OpenSpec CLI; do not hand-create generated workflow skills or `.openspec.yaml` metadata.
- Read proposal, delta specs, design and tasks before applying. Keep task checkboxes current as work is verified.
- Requirements use normative, testable `SHALL` statements and `WHEN`/`THEN`/`AND` scenarios.
- Run strict validation before declaring a change ready. Archive only after implementation and verification are complete.

## Transactions, events and background work

- Put `@Transactional` on the owning module's application use case, not controllers or repositories.
- Do not keep a database transaction open across provider I/O or mutate another module's tables.
- Keep high-volume market data in bounded batch pipelines and tables. Reserve Modulith events for low-frequency completed facts; durable delivery needs a separate ADR/change.
- One JAR may run multiple workload roles. Recurring work must be idempotent and claimed through PostgreSQL so multiple instances do not duplicate it.
- Reproducible runs identify build/source revision, algorithm/configuration version, dataset fingerprint, cutoff and seed as defined in `docs/REPRODUCIBILITY.md`.

## Testing and verification

Tests must cover the smallest meaningful behavior at the owning module boundary. Use unit tests for pure domain behavior, Spring Modulith tests for module integration, and Testcontainers for PostgreSQL/Flyway behavior. Every architecture-affecting change must keep `ApplicationModules.verify()` green.

Follow the detailed test-level ownership in `docs/TESTING.md` and runtime, secret, health, logging and resource rules in `docs/OPERATIONS.md`.

Required commands from the repository root:

```bash
./mvnw clean verify
openspec validate --all --strict --no-interactive
openspec doctor
```

On Windows, `mvnw.cmd clean verify` is equivalent. Report skipped or blocked checks with the exact command and cause. Do not claim completion when a required check has not run successfully.

GitHub Actions runs the same contract in the stable `quality-gate` job for pushes and pull requests. Surefire and Failsafe reports are published as the `maven-test-reports` workflow artifact when files exist. Repository files define the job but cannot enable branch protection: after an authorized push and the first successful remote run, a repository administrator must require `quality-gate` in the primary-branch ruleset.

## Definition of Done

- The active OpenSpec artifacts match the implemented scope and completed task checkboxes.
- Code respects module ownership, synchronous contracts and dependency directions.
- Tests cover the new behavior and PostgreSQL migrations where applicable.
- Required verification commands pass, or the handoff explicitly states the environmental blocker.
- Documentation and relative links are updated; no tool-export-specific markup remains.
- No unrelated user changes, generated secrets or local IDE state are committed.

## Code review rules

- Review correctness, point-in-time integrity, idempotency and measurement bias before style.
- Reject hidden cross-module coupling, shared persistence models and bypasses of public APIs.
- Reject future-data leakage, silent outcome loss, fabricated provider fallback data and unbounded concurrency.
- Verify transaction ownership, failure behavior, migration safety and query-backed indexes.
- Treat new dependencies and architectural boundary changes as blocking unless the OpenSpec design and ADR explicitly approve them.
- Require reproducible tests and concrete verification output; comments and naming must be in English.
