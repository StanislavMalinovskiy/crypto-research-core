## Why

The structural modular-monolith baseline is verified, but several operating decisions still live only as review guidance. Before domain types and persistence arrive, the repository needs executable health semantics and one authoritative place for persistence usage, secrets, logging and test-level rules.

## What Changes

- Add `docs/OPERATIONS.md` for deployment topology, configuration and secrets, health semantics, logging/correlation/cardinality and resource-budget rules.
- Add `docs/TESTING.md` for the project test levels and when PostgreSQL Testcontainers or module tests are required.
- Record a persistence decision matrix for Spring Data JDBC, `JdbcClient`, JDBC batch operations and Flyway, while preserving the ban on cross-module SQL.
- Configure separate Actuator liveness and readiness groups: liveness is process-local, while readiness includes PostgreSQL; providers do not participate in liveness.
- Extend the PostgreSQL integration smoke test to verify aggregate health, liveness and database-backed readiness.
- Add an isolated negative-path integration test proving database loss makes readiness `DOWN`/HTTP 503 while liveness remains `UP`.
- Advance the current PostgreSQL baseline from 16.15 to the latest supported 18.6 release and verify the exact official Testcontainers image with Flyway before business data exists.
- Pin the existing GitHub Actions quality-gate runner to `ubuntu-24.04` to reduce environment drift.
- Link the operating contracts from Architecture, Tech Stack, README and agent guidance without duplicating their detailed content.

Non-goals:

- No business functionality, domain type, provider, worker, scheduler, read model, database migration or module-boundary change.
- No empty `@ConfigurationProperties`, observability, resilience or worker abstractions.
- No new production dependency, Prometheus/OpenTelemetry exporter, Docker Compose, Kubernetes or production container image.
- No fixed pool size, timeout, retry count, worker metric name or provider fixture before a measured workload exists.
- No bulk introduction of `@ApplicationModuleTest` before modules have use cases.

Affected application modules: none. These are repository-wide engineering and runtime-foundation contracts.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `operational-health`: distinguish process liveness from PostgreSQL-backed readiness and verify both probes.
- `repository-conventions`: require documented persistence, configuration/secrets, logging and testing contracts before business implementation.

## Impact

- Documentation: new `docs/OPERATIONS.md` and `docs/TESTING.md`, with concise links and summaries in existing foundation documents.
- Configuration: Actuator health-group membership only; no new endpoint family is exposed.
- Tests: the healthy smoke test and an isolated database-loss test use `postgres:18.6-alpine` to verify server version, Flyway, aggregate health and both probe states.
- Automation: the existing quality gate uses an explicit Ubuntu 24.04 runner image.
- Runtime architecture, module DAG, database schema and dependency graph remain unchanged.
