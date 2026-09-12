# Testing strategy

Tests prove the smallest meaningful contract at the cheapest suitable level. They follow module ownership and never bypass a public module boundary merely to make setup easier.

## Test levels

| Level | Use for | Infrastructure |
|---|---|---|
| Unit | Immutable values, calculations and pure domain rules | JUnit only |
| Module | Application use cases and one module's Spring wiring | `@ApplicationModuleTest` when a real use case exists |
| PostgreSQL integration | SQL, repositories, Flyway, locking and PostgreSQL-specific behavior | Testcontainers PostgreSQL |
| Provider contract | Parsing and normalization against preserved provider responses | Introduced with the first approved provider |
| Architecture | Module DAG, named APIs, dependency and repository conventions | Spring Modulith and dependency-free repository tests |
| Startup smoke | Application, PostgreSQL, Flyway and Actuator wiring | One small `@SpringBootTest` plus Testcontainers |

Do not make pure domain tests start Spring or Docker. Do not replace PostgreSQL-specific tests with H2 or repository mocks. Broad module-test scaffolding and provider fixtures are deferred until the corresponding use cases and provider contracts exist.

## Database and health isolation

- Each destructive database-availability scenario owns an isolated container and application context.
- Negative readiness checks restore paused infrastructure in a `finally` block and use bounded polling rather than an unbounded wait.
- The healthy startup smoke test remains independent from failure-path tests.
- PostgreSQL integration tests assert the exact supported server version and Flyway state when version-specific behavior matters.

## Maven lifecycle

- Surefire runs unit, architecture and repository convention tests.
- Failsafe runs `*IT` integration tests, including PostgreSQL startup and health behavior.
- `mvnw.cmd -DskipITs clean verify` is the fast local structural gate.
- `mvnw.cmd clean verify` is the complete gate and requires Docker.
- CI publishes Surefire and Failsafe reports even when verification fails.

Every change adds tests with its behavior. A test checkbox is complete only after the relevant command has actually passed; an unavailable Docker engine must be reported rather than hidden.
