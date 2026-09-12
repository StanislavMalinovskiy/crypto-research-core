## Why

The repository has an initial Maven file and notes, but its package layout, module boundaries, database bootstrap and verification workflow do not yet form a durable foundation for AI-assisted development. Establishing a small verified baseline now prevents business capabilities from growing across accidental architectural boundaries.

## What Changes

- Normalize documentation, project instructions and the `io.cryptoresearch` package root.
- Replace the single document-priority ladder with explicit source responsibilities and conflict rules.
- Provide a Maven Wrapper build for Java 25 preview compilation and one executable Spring Boot 4.1.1 JAR.
- Declare the eight Roadmap-defined Spring Modulith modules, their exact dependency DAG and named public boundaries.
- Record durable policies for module-owned PostgreSQL schemas, transactions, events, background work and bounded concurrency.
- Configure synchronous Spring MVC, Spring Data JDBC, PostgreSQL, Flyway and virtual threads without implementing business behavior.
- Keep the bootstrap free of placeholder versioned migrations until a business module owns real schema, while verifying Flyway initialization on PostgreSQL.
- Add Actuator health plus automated context, module-boundary and PostgreSQL/Testcontainers verification.
- Enforce the absence of JPA, WebFlux, Reactor and R2DBC dependencies and validate repository documentation conventions.

Non-goals: provider integrations, ingestion, domain entities, strategy logic, scoring, outcome calculation, business REST APIs, Redis, messaging infrastructure, microservices or additional Maven modules.

Affected modules: `kernel`, `governance`, `marketdata`, `risk`, `wallet`, `strategy`, `measurement` and `research` receive descriptors only; no module receives business implementation.

## Capabilities

### New Capabilities

- `build-baseline`: Reproducible Java 25 preview Maven build and executable application baseline.
- `module-boundaries`: Verifiable Spring Modulith module declarations, dependency DAG and named API rules.
- `database-bootstrap`: PostgreSQL configuration and empty Flyway baseline verification on a real container.
- `operational-health`: Application context and Actuator health verification.
- `repository-conventions`: Automated dependency and Markdown checks plus explicit architectural governance for long-lived agent work.

### Modified Capabilities

None. The project has no existing OpenSpec capability specifications.

## Impact

The change affects the root Maven build, application/configuration packages, test foundation, documentation, ADRs and Codex/OpenSpec instructions. It adds Actuator as the only new production dependency required by the requested health capability and adds test-scoped Testcontainers support. It does not expose a business API, create database schemas or apply versioned migrations.
