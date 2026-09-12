## MODIFIED Requirements

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

## ADDED Requirements

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
