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

## Kernel identity tests

- Construct identity values as pure unit tests without Spring or Docker.
- Cover canonical chain slugs, exact opaque-value preservation, explicit null-chain rejection and invalid whitespace/control characters.
- Prove that identical local values on different chains and event locators under different transactions remain distinct keys.
- Prove deterministic same-category sorting, including numeric `BlockPosition` ordering.
- Keep chain-specific base58/checksum parsing in future adapter contract tests rather than kernel tests.

## Database and health isolation

- Each destructive database-availability scenario owns an isolated container and application context.
- Negative readiness checks restore paused infrastructure in a `finally` block and use bounded polling rather than an unbounded wait.
- The healthy startup smoke test remains independent from failure-path tests.
- PostgreSQL integration tests assert the exact supported server version and Flyway state when version-specific behavior matters.
- Raw market-data storage tests assert exact CAIP-2 network validation, physical column names, absence of ambiguous legacy names, primary-key order, check constraints, cross-network distinction, exact readback, immutable retry semantics and concurrent uniqueness behavior against PostgreSQL rather than a repository mock.
- Storage concurrency tests invoke the transactional application boundary from separate threads so each submission owns a real database transaction.

## Maven lifecycle

- Surefire runs unit, architecture and repository convention tests.
- Failsafe runs `*IT` integration tests, including PostgreSQL startup and health behavior.
- `mvnw.cmd -DskipITs clean verify` is the fast local structural gate.
- `mvnw.cmd clean verify` is the complete gate and requires Docker.
- CI publishes Surefire and Failsafe reports even when verification fails.

Every change adds tests with its behavior. A test checkbox is complete only after the relevant command has actually passed; an unavailable Docker engine must be reported rather than hidden.

## Reproducibility tests

- Time-dependent rules use fixed or controlled clocks/reference instants; tests never depend on the current machine time.
- Exact arithmetic tests assert precision, scale and rounding at lossy boundaries.
- Determinism tests permute equivalent input and completion order and expect identical ordered domain results.
- Randomized research tests inject and record a seed, then reproduce the same result with it.
- Dataset lineage tests prove identical canonical inputs reproduce a fingerprint and changed input or transformation versions do not.
- Evaluation replay tests assert the complete result and provenance contract from [Research reproducibility](REPRODUCIBILITY.md).
