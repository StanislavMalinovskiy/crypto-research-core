## Context

See [proposal.md](proposal.md) for motivation. The repository started with a generated application under `io.cryptoresearch.crypto_research_core`, a partial dependency set, no module descriptors, one minimal configuration file and no architecture tests. The current architecture is summarized in `docs/ARCHITECTURE.md`; accepted decisions live in ADRs, behavior contracts live in OpenSpec, and code plus tests provide implementation evidence.

## Goals / Non-Goals

**Goals:**

- Make the repository compile and verify on Java 25 through the checked-in Maven Wrapper.
- Represent all eight vertical modules as explicit Spring Modulith boundaries before business code exists.
- Prove application startup, health and Flyway behavior on real PostgreSQL.
- Make forbidden dependency and documentation drift fail repeatable verification.
- Make data, transaction, event, worker and concurrency ownership explicit before the first business table or background job.

**Non-Goals:**

- Define kernel value objects, business APIs, tables, provider ports or events.
- Implement governance gates or any later Roadmap phase.
- Add caching, messaging, reactive programming, ORM, business endpoints or observability infrastructure beyond health.

## Decisions

### Keep one application root and package-based modules

Move the application entry point to `io.cryptoresearch` and declare each direct business subpackage with an explicit module descriptor. Declare an `api` named interface for future contracts, even though bootstrap adds no API types. The allowed-dependency arrays mirror `docs/ARCHITECTURE.md` exactly.

Alternative considered: Maven modules. Rejected because the Roadmap requires one Maven module and package verification provides the required boundary with less build complexity.

### Assign authority by responsibility rather than one priority ladder

Roadmap owns product goals and sequencing; ADRs own accepted architecture decisions and rationale; Architecture summarizes the current accepted state; Tech Stack owns allowed technologies and versions; archived OpenSpec specs own accepted observable behavior; active changes own proposed or partially implemented deltas; and code plus tests show actual implementation. An accepted ADR must update Architecture. An active change does not redefine current behavior until it is implemented, verified and archived. A mismatch between code and a main spec is a defect or an explicitly tracked migration.

Alternative considered: retain a single ordered list. Rejected because unrelated documents would appear to override one another outside their responsibility.

### Lock every cross-module dependency to a named API

Use the exact acyclic graph in `docs/ARCHITECTURE.md`. All edges, including stable kernel concepts, target `module::api`. Keep a descriptor-contract test in addition to `ApplicationModules.verify()` so empty modules cannot hide a wrong allowed-dependency declaration.

Alternative considered: adopt an illustrative replacement DAG immediately. Rejected because it changes accepted directions without an implementing use case or ADR; future vertical slices may propose individual edge changes with evidence.

### Use the core Modulith runtime only

Use the Spring Modulith core starter plus test support. Do not activate the JDBC event publication registry before a business change defines durable events and their tables.

Alternative considered: retain the JDBC event starter from the generated POM. Rejected for bootstrap because it introduces unused runtime behavior and schema concerns.

### Add only Actuator to production scope

Actuator is the only new production dependency and is required for the health contract. Remove the unused Prometheus registry from the bootstrap dependency set; metrics export can return in a dedicated operations change. Existing MVC, JDBC, validation, PostgreSQL and Flyway dependencies remain within the accepted baseline.

Alternative considered: expose a custom health controller. Rejected because it would create application behavior and duplicate the platform health mechanism.

### Give every table one owner and use module PostgreSQL schemas

Use environment-backed datasource defaults for local development. Every business table, SQL statement, repository and row mapper has exactly one owning module. Data-owning modules use their own PostgreSQL schema; `kernel` never owns a schema, and `governance` gets one only when it owns durable state. Flyway keeps one global ordered migration history, while each business migration lives under its owning-module path.

Do not create schemas or a no-op versioned migration in bootstrap. The first business migration establishes only the schema and objects its module actually needs. Cross-module SQL, repository reuse and entity sharing are forbidden. Immutable projections, public APIs, defined events and ADR-approved analytical read models are the allowed alternatives.

Alternative considered: one `public` schema. Rejected because module schemas make ownership visible at low cost in a single PostgreSQL database. They are namespace boundaries, not security isolation; separate database roles are not introduced.

### Put transactions at application use-case boundaries

The owning module's application use case starts the transaction. Controllers and repositories do not create business transactions, and a module never calls another module's repository. Synchronous public API calls may participate only when the dependency graph permits them and short atomicity is intentional. Provider I/O and other long blocking calls happen outside database transactions. Post-commit work uses an explicit after-commit mechanism; deep cross-module atomic transactions are avoided.

High-volume market events use bounded batch pipelines and tables, not Spring Modulith event publication. Rare completed business facts may use Modulith events. Durable delivery requires a later JDBC registry/outbox decision; no such dependency is added now.

### Coordinate background work through roles and database claiming

Keep one codebase and one JAR, but allow multiple instances to select workload roles such as `api`, `ingestion` or `measurement`. Any recurring or recoverable job must be idempotent and claim work through PostgreSQL. Use short `FOR UPDATE SKIP LOCKED` claims for database-local work and durable leases for work that continues across provider calls; never hold a database transaction open during remote I/O.

Alternative considered: an in-memory scheduler lock. Rejected because it cannot coordinate multiple instances. A broker is deferred until database claiming is measured and insufficient.

### Treat virtual threads as execution, not admission control

Every provider adapter defines a concurrency limit, rate limit, timeout and retry classification. Queues and batches are bounded. Database concurrency is capped below or at the connection-pool capacity, independent of the number of virtual threads. Retry is finite, cancellation-aware and limited to transient failures; ingestion and jobs require idempotency keys.

Alternative considered: unrestricted thread-per-item fan-out. Rejected because virtual threads do not provide backpressure or protect provider and database capacity.

### Verify startup and migration in one PostgreSQL integration test

Run an application on a random port against a Testcontainers PostgreSQL instance. Verify the health response, Flyway initialization and absence of applied versioned migrations. Keep module-boundary and repository-convention tests independent of Docker so their failures stay focused.

Alternative considered: use an in-memory database for fast context tests. Rejected because H2 would add a dependency and would not validate PostgreSQL/Flyway behavior.

### Enforce repository rules in Maven verification

Use Maven Enforcer to require Java 25 and ban forbidden dependency families transitively. Use a JUnit repository-convention test for tool-export markers and relative Markdown file links. Configure Failsafe for `*IT` tests so database integration runs in `verify`.

Alternative considered: informal shell-only checks. Rejected because long-lived AI-assisted development needs the same checks in local and CI builds.

## Risks / Trade-offs

- [Docker is unavailable] -> Unit and architecture checks can still run, but full `verify` remains explicitly blocked and must not be reported as complete.
- [Java 25 or dependency artifacts are unavailable] -> Preserve exact Maven output and do not downgrade the required baseline.
- [An empty API named interface feels premature] -> Limit it to package metadata; no placeholder Java class or invented contract is added.
- [Preview compilation applies to the whole module] -> Public API tests and review rules prevent preview-only types from crossing module boundaries.
- [Module schemas add naming and migration discipline] -> Keep one database, one Flyway history and no separate database roles; introduce each schema only with its first owned table.
- [Runtime roles can drift toward service boundaries] -> Roles select workloads only; they do not create new artifacts, repositories or databases.

## Migration Plan

1. Move the application and tests from the invalid generated package to `io.cryptoresearch`.
2. Update the Maven lifecycle and minimal configuration.
3. Add module and named-interface descriptors.
4. Keep the Flyway location free of versioned migrations and add layered verification tests.
5. Run strict OpenSpec checks and Maven verification. If verification fails, keep the change active and record the exact environmental or code cause.

Rollback is a normal source revert before any production deployment; bootstrap creates no business database object and therefore needs no destructive database rollback.

## Open Questions

- Exact public API types and low-frequency event schemas remain TBD for the business changes that introduce them.
- Cache and metrics-export infrastructure remain deferred until measured load or operational requirements justify separate changes and ADRs.
