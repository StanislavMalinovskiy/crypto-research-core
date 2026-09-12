## ADDED Requirements

### Requirement: Explicit persistence access policy
Repository architecture documentation SHALL define how an owning module selects a persistence mechanism and SHALL prohibit direct cross-module data access.

#### Scenario: Persistence mechanism selection
- **WHEN** a module designs a persistence operation
- **THEN** simple aggregate CRUD SHALL use Spring Data JDBC only when aggregate semantics fit
- **AND** projections, explicit queries, upserts and targeted writes SHALL use `JdbcClient`
- **AND** high-volume writes SHALL use prepared JDBC batch operations
- **AND** DDL SHALL remain owned exclusively by Flyway.

#### Scenario: Module data access
- **WHEN** one module needs data owned by another module
- **THEN** it SHALL use the owner's public API, immutable projection, defined event or separately approved analytical read model
- **AND** it SHALL not use the owner's table, SQL, repository, entity or row mapper directly.

### Requirement: Documented engineering operating contracts
The repository SHALL document configuration, secret handling, health, logging and test-level rules before business capabilities depend on them.

#### Scenario: Secret-bearing configuration
- **WHEN** a change introduces a secret or mandatory production setting
- **THEN** the value SHALL come from external configuration or a secret store and SHALL fail fast when required but absent
- **AND** it SHALL not appear in Git, logs or exception messages
- **AND** validation infrastructure SHALL be introduced only with a real configuration or input contract that uses it.

#### Scenario: Logging and measurement dimensions
- **WHEN** application or workload telemetry is designed
- **THEN** logs SHALL exclude secrets and complete sensitive provider payloads
- **AND** correlation fields SHALL identify workload, run, provider, chain and operation where applicable
- **AND** wallet addresses, token addresses and transaction hashes SHALL not be metric tags.

#### Scenario: Test-level selection
- **WHEN** a change chooses a test level
- **THEN** pure rules SHALL use unit tests and module use cases SHALL use module-scoped tests when they exist
- **AND** SQL, repositories and migrations SHALL use PostgreSQL Testcontainers
- **AND** architecture verification and a small full-startup smoke test SHALL remain part of the Maven lifecycle.
