## Purpose

Makes long-lived human and AI-assisted development safer by turning dependency and documentation conventions into repeatable verification checks.

## ADDED Requirements

### Requirement: Forbidden dependency enforcement
The Maven verification lifecycle SHALL reject JPA, Hibernate, WebFlux, Reactor, R2DBC and Vert.x dependencies anywhere in the resolved dependency graph.

#### Scenario: Dependency policy verification
- **WHEN** the project dependency graph is checked during `verify`
- **THEN** any forbidden dependency SHALL fail the build
- **AND** allowed synchronous JDBC and servlet dependencies SHALL remain unaffected.

### Requirement: Markdown hygiene
Repository verification SHALL inspect tracked Markdown documentation for tool-export-specific markers and broken relative file links.

#### Scenario: Documentation verification
- **WHEN** repository convention tests scan Markdown files
- **THEN** tool-export-specific markers SHALL be absent
- **AND** every relative Markdown file link SHALL resolve to an existing repository path.

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
