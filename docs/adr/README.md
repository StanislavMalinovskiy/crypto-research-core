# Architecture Decision Records

ADRs capture durable architectural decisions. A new or superseding ADR is required before changing module boundaries, dependency directions, persistence technology, concurrency model, deployment shape or core infrastructure.

| ADR | Status | Decision |
|---|---|---|
| [0001](0001-modular-monolith.md) | Accepted | One Spring Modulith modular monolith |
| [0002](0002-spring-data-jdbc.md) | Accepted | Spring Data JDBC and `JdbcClient` |
| [0003](0003-synchronous-java-25-baseline.md) | Accepted | Synchronous Java 25 baseline |
| [0004](0004-module-data-ownership.md) | Accepted | One table owner and PostgreSQL schema per data-owning module |
| [0005](0005-transactions-and-events.md) | Accepted | Application-owned transactions and tiered event mechanisms |
| [0006](0006-background-work-and-bounded-concurrency.md) | Accepted | Runtime roles, PostgreSQL claiming and bounded concurrency |
