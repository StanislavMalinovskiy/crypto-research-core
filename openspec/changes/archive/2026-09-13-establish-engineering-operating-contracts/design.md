## Context

See [proposal.md](proposal.md). The repository already enforces module ownership, PostgreSQL/Flyway integration and a CI quality gate. `application.yaml` exposes health with probes enabled, but it does not define group membership, and the current integration test checks only aggregate health. Persistence, secret, logging and test-level guidance needs stable ownership before domain work starts.

## Goals / Non-Goals

**Goals:**

- Give runtime operations and test strategy one authoritative document each.
- Make liveness process-local and readiness dependent on the mandatory PostgreSQL database.
- Prevent misuse of aggregate repositories for high-volume persistence.
- Turn secret and observability review guidance into executable repository rules where practical.

**Non-Goals:**

- Implement providers, background work, business persistence, analytical read models or application-module use cases.
- Select numeric pool, timeout, batch, retry or rate limits before workload measurements exist.
- Add configuration objects, metric exporters, logging libraries, test frameworks or production dependencies.
- Define production packaging or orchestration beyond one JAR with an external PostgreSQL database.

## Decisions

### 1. Split operating and testing documentation by responsibility

Create `docs/OPERATIONS.md` for runtime topology, configuration/secrets, health, logging and resource budgets. Create `docs/TESTING.md` for the test pyramid and verification ownership. `ARCHITECTURE.md` keeps a concise persistence matrix and links to both; `TECH_STACK.md`, README and `AGENTS.md` link rather than duplicate detailed rules.

A single broad architecture appendix was rejected because runtime operators and test authors need different navigation paths. Module-level copies were rejected because the policies are repository-wide.

### 2. Select persistence tools by operation shape

Use Spring Data JDBC repositories only for aggregates whose load/save lifecycle matches aggregate semantics. Use `JdbcClient` for projections, explicit queries, upserts and targeted changes. Use `JdbcTemplate` or prepared JDBC batches for high-volume writes. Flyway remains the only DDL owner.

Every implementation remains inside its owning module. Cross-module SQL is forbidden; an analytical read model requires a later explicit design or ADR. Implementing such a model is outside this change.

### 3. Treat the connection pool as database admission control

Virtual-thread count does not define safe database concurrency. The connection pool is an upper admission boundary and database work must be bounded relative to it. Exact pool sizes, connection/statement/transaction timeouts and batch sizes remain deferred until the first measured workload; no placeholder values are added.

### 4. Keep secrets external and configuration typed when it becomes real

Secret values come from environment injection or a deployment secret store and never receive committed defaults. Future provider and production-only settings use typed configuration with fail-fast validation. No empty configuration classes or validation dependency are created now; they arrive with the first real contract.

### 5. Define explicit health groups without exposing internals in production

Keep only the `health` endpoint family exposed. Configure liveness to include only `livenessState`. Configure readiness to include `readinessState` and `db`; Flyway failure already prevents successful application startup. External providers are excluded from liveness and readiness until a future capability proves a different readiness requirement.

The healthy integration test requests `/actuator/health`, `/actuator/health/liveness` and `/actuator/health/readiness`. Test-only health component visibility proves that readiness contains `db` and liveness does not, while production responses keep component details hidden.

A separate integration-test class owns its own PostgreSQL container for the negative path. It first proves both probes are healthy, temporarily stops the database container, polls readiness within a finite deadline until it returns `DOWN`/HTTP 503, and verifies liveness remains `UP`. A `finally` block restarts the same container, without asserting a pool-recovery duration that belongs to future resource-budget tuning. Isolation and the independent healthy smoke test prove that the destructive phase does not affect the baseline; per-request timeouts and bounded polling avoid timing-sensitive fixed sleeps and unbounded network waits.

### 6. Establish logging and cardinality rules without an exporter

Use the existing Spring Boot/SLF4J baseline. Logs exclude secrets and complete sensitive payloads. Applicable correlation fields are workload, research run, provider, chain and operation. Addresses and transaction hashes are diagnostic fields, not metric dimensions. Concrete worker metrics and any Prometheus/OpenTelemetry exporter belong to the change that introduces a worker or operational backend.

### 7. Match test cost to the behavior under test

Pure domain rules use unit tests. Module use cases use `@ApplicationModuleTest` when such use cases exist. PostgreSQL SQL, repositories, migrations and locking use Testcontainers. Architecture tests keep the DAG enforceable, and one full startup integration test verifies database/Flyway/health wiring. Provider fixture contracts begin with the first provider; this change creates none.

### 8. Adopt PostgreSQL 18.6 before business data exists

Set PostgreSQL major 18 as the current supported database family and pin integration verification to the official `postgres:18.6-alpine` image. PostgreSQL 18.6 is the current supported minor release, and selecting it before business schemas or deployed data exist avoids carrying an immediate major-version migration into the first vertical slice.

The integration test asserts the server reports `18.6` and Flyway remains healthy. Historical OpenSpec archives keep their original 16.15 evidence; only active baseline documentation and executable verification change. Digest pinning remains deferred under the existing controlled image-update policy.

### 9. Pin the CI runner release

Change the existing quality gate from `ubuntu-latest` to `ubuntu-24.04`. The explicit LTS runner reduces unreviewed operating-system drift while retaining GitHub-hosted Docker support. Action and JDK updates remain controlled separately.

## Risks / Trade-offs

- **[Readiness restarts traffic when PostgreSQL is unavailable]** → This is intentional because the application cannot provide its required persistence behavior without PostgreSQL; liveness remains independent to avoid restart loops.
- **[Policy-only rules drift before examples exist]** → Link them from agent guidance and require each future OpenSpec change to choose the applicable persistence and test level.
- **[Deferred numeric budgets allow unsafe defaults later]** → The first workload change must record measured limits before enabling concurrency; this change explicitly forbids treating virtual threads as admission control.
- **[Test-only component visibility differs from production output]** → Use it solely to verify group membership; production health details remain hidden.
- **[PostgreSQL 18 changes default image data layout]** → Testcontainers owns the ephemeral container layout; production storage design remains a later deployment concern and must follow the official PostgreSQL 18 image guidance.
- **[Database-loss test can be timing-sensitive]** → Isolate it in its own class, use short test-only database validation timeouts, bounded polling and unconditional container restoration; never share its container with the healthy smoke test.

## Migration Plan

1. Update the active PostgreSQL baseline to 18.6 and prove the exact image against the empty Flyway baseline.
2. Add the two policy documents and update concise navigation/architecture references.
3. Configure health group membership and extend the existing Testcontainers smoke test.
4. Run skip-IT and Docker-backed Maven verification plus strict OpenSpec checks.
5. Verify, sync and archive the change before domain implementation.

Rollback removes the new documentation links and explicit health groups, and restores the aggregate-only smoke test. There is no database or production-state migration.
