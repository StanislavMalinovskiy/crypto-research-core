# Repository Conventions Specification

## Purpose

Makes long-lived human and AI-assisted development safer by turning dependency and documentation conventions into repeatable verification checks.

## Requirements

### Requirement: Forbidden dependency enforcement
The Maven verification lifecycle SHALL reject modern and legacy JPA, Hibernate ORM, WebFlux, Reactor, R2DBC and Vert.x dependencies anywhere in the resolved dependency graph.

#### Scenario: Dependency policy verification
- **WHEN** the project dependency graph is checked during `verify`
- **THEN** modern or legacy JPA and Hibernate ORM coordinates and all other forbidden dependency families SHALL fail the build, including transitive occurrences
- **AND** allowed synchronous JDBC, servlet and future Bean Validation provider dependencies SHALL remain unaffected.

### Requirement: Markdown hygiene
Repository verification SHALL inspect all tracked Markdown documentation for tool-export-specific markers and broken relative file links, and SHALL inspect `docs/GLOSSARY.md` for obsolete architecture presented as current.

#### Scenario: Documentation verification
- **WHEN** repository convention checks scan tracked Markdown files
- **THEN** tool-export-specific markers SHALL be absent
- **AND** every relative Markdown file link SHALL resolve to an existing repository path.

#### Scenario: Active architecture terminology
- **WHEN** repository convention checks scan `docs/GLOSSARY.md`
- **THEN** legacy module names or database entities SHALL not be presented as current
- **AND** any retained legacy module name or database entity SHALL be locally marked as historical or deferred.

### Requirement: Active planning document consistency
Active repository documentation SHALL distinguish the verified current baseline from target MVP components and deferred options without overriding accepted ADRs or main specifications.

#### Scenario: Current and target baseline review
- **WHEN** a developer reads the Roadmap and Tech Stack
- **THEN** current, target and deferred technologies SHALL be distinguishable
- **AND** caches, external observability infrastructure, providers and partitioning SHALL include activation rules proportional to their architectural impact
- **AND** the documented Java package root SHALL be `io.cryptoresearch`.

#### Scenario: Durable change navigation
- **WHEN** an OpenSpec change is archived or a new active change is created
- **THEN** README navigation SHALL remain valid without naming a transient active change
- **AND** the completed bootstrap archive and next planned business change SHALL remain discoverable.

#### Scenario: Persistence change ordering
- **WHEN** the planned market-data changes introduce or modify persistence
- **THEN** idempotency SHALL be an acceptance criterion of each persistence change
- **AND** the storage foundation SHALL precede provider ingestion in the planned change sequence.

### Requirement: Executable agent guidance
The repository SHALL provide concise root guidance that defines source responsibilities, conflict resolution, required reading, architectural constraints, OpenSpec workflow, review rules and verification commands.

#### Scenario: New agent onboarding
- **WHEN** an agent begins a code change from the repository root
- **THEN** it SHALL be able to identify the required reading order and affected module documentation
- **AND** it SHALL have explicit Definition of Done and verification commands.

### Requirement: Durable architecture policies
The repository SHALL record accepted decisions for table ownership and PostgreSQL schemas, transaction and event semantics, background work coordination and bounded blocking concurrency before business implementation begins.

#### Scenario: Architecture policy review
- **WHEN** an architecture-affecting change is reviewed
- **THEN** every table SHALL have one owning module and every transaction SHALL have one owning application use case
- **AND** background work SHALL use idempotent database-backed claiming across instances
- **AND** virtual-thread workloads SHALL have explicit provider, database, queue, timeout and retry limits.

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
