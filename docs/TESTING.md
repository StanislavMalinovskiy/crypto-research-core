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
- Cover exact customary CAIP-2 networks, including representative Solana/EVM and unknown valid identifiers, reference case sensitivity, exact opaque-value preservation, explicit null-chain rejection and invalid whitespace/control characters.
- Prove that identical local values on different chains and event locators under different transactions remain distinct keys.
- Prove deterministic same-category sorting, including numeric `BlockPosition` ordering.
- Keep chain-specific base58/checksum parsing in future adapter contract tests rather than kernel tests.

## Database and health isolation

- Each destructive database-availability scenario owns an isolated container and application context.
- `ManagedPostgresConfigurationTest` uses synthetic `.invalid` endpoints and a temporary secrets file to verify profile selection, separate runtime/Flyway identities, fail-closed missing settings and unchanged local defaults without opening a database connection.
- Maven and CI do not activate the `managed` profile and never read or connect through `config/application-managed-secrets.properties`; all PostgreSQL behavior remains isolated in Testcontainers.
- Negative readiness checks restore paused infrastructure in a `finally` block and use bounded polling rather than an unbounded wait.
- The healthy startup smoke test remains independent from failure-path tests.
- PostgreSQL integration tests assert the exact supported server version and Flyway state when version-specific behavior matters.
- Raw market-data storage tests assert exact CAIP-2 network validation, physical column names, absence of ambiguous legacy names, unpartitioned table shape, primary-key order and explicit `C` collation, absence of surrogate/secondary indexes, check constraints, synthetic EVM persistence, cross-network/provider/case distinction, exact readback, immutable retry semantics and concurrent uniqueness behavior against PostgreSQL rather than a repository mock.
- Storage concurrency tests invoke the transactional application boundary from separate threads so each submission owns a real database transaction.

## Maven lifecycle

- Surefire runs unit, architecture and repository convention tests.
- Failsafe runs `*IT` integration tests, including PostgreSQL startup and health behavior.
- `mvnw.cmd -DskipITs clean verify` is the fast local structural gate.
- `mvnw.cmd clean verify` is the complete gate and requires Docker.
- CI publishes Surefire and Failsafe reports even when verification fails.

Every change adds tests with its behavior. A test checkbox is complete only after the relevant command has actually passed; an unavailable Docker engine must be reported rather than hidden.

## Agent-assisted development

The selected workflow mode does not change test levels or the complete Maven lifecycle. Fail-closed `DEFAULT` uses top-level Control and Developer sessions; manually enabled `MULTIAGENT` uses the separate supervised role protocol.

When observable behavior changes in DEFAULT, Developer derives a meaningful test from the active requirement and runs it before implementation. Red is valid only when the named test executes and fails at the expected behavioral assertion; compilation, discovery, configuration, startup, Docker or another infrastructure failure is not red. After valid red, the establishing test must not be weakened, disabled, skipped or narrowed, and production code must not recognize a fixture, profile, known test value or other test artifact.

Documentation, comments, formatting, mechanical configuration, a pure rename or internally covered refactoring may require no new behavioral test, but applicable targeted checks and the complete gate still run. The independent integrity preflight remains before Maven in both modes, and explicit targeted developer commands never substitute for `mvnw.cmd clean verify` at completion.

## Reproducibility tests

- Time-dependent rules use fixed or controlled clocks/reference instants; tests never depend on the current machine time.
- Exact arithmetic tests assert precision, scale and rounding at lossy boundaries.
- Determinism tests permute equivalent input and completion order and expect identical ordered domain results.
- Randomized research tests inject and record a seed, then reproduce the same result with it.
- Dataset lineage tests prove identical canonical inputs reproduce a fingerprint and changed input or transformation versions do not.
- Evaluation replay tests assert the complete result and provenance contract from [Research reproducibility](REPRODUCIBILITY.md).

## First recorded signal slice

`FirstSignalEvaluationIT` uses an isolated `postgres:18.6-alpine` Testcontainers database and the repository fixture at `src/test/resources/fixtures/first-signal-evaluation.json`. It applies V1-V4 and exercises the public APIs from raw-first replay through dataset finalization, risk gating, immutable `LIQUIDITY_SPIKE`, exact `1h` valuation and deterministic report publication. The contract covers equal retry, immutable conflict, parse failure, cutoff isolation, input permutation, priced and unpriced outcomes, complete provenance, exact rows/scales/indexes and the absence of a risk schema. It never connects to the persistent developer Compose volume.

Pure tests own the risk matrix, signal threshold boundaries, strict post-decision/pre-horizon entry, horizon selection and all three friction tiers. PostgreSQL regressions also prove that sub-microsecond source, decision and evaluation cutoffs normalize consistently before retry comparison, durable identity and published provenance. Fixture validation rejects missing, extra, reordered-semantic or inexact numeric content before the integration scenario is constructed. This fixture proves architecture and determinism only; it is not provider fallback data or evidence of statistical edge.
