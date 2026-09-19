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

### Requirement: Concise project delivery map
The repository SHALL maintain one concise top-level delivery plan that identifies source boundaries, the current project position, the ordered outcome-oriented stages, the next planned work and each stage's exit result without duplicating detailed product, architecture or change specifications.

#### Scenario: New-session delivery orientation
- **WHEN** a human or AI agent opens the delivery plan in a new session
- **THEN** the document SHALL identify the current stage, current work, next planned change and next business change
- **AND** it SHALL show the route from architecture foundation through an evidence-based research decision
- **AND** execution SHALL remain explicitly deferred until the required evidence and safety design exist.

#### Scenario: Appropriate planning detail
- **WHEN** the delivery plan describes a stage
- **THEN** it SHALL use only the statuses `Done`, `Current`, `Next`, `Planned` or `Deferred`
- **AND** it SHALL describe goals, outcomes, major change groups and exit results rather than Java types, database objects, libraries or algorithms
- **AND** it SHALL link to the authoritative Roadmap, ADR, main spec or active change instead of copying their detailed decisions.

#### Scenario: Delivery position maintenance
- **WHEN** an OpenSpec change is archived or project priority explicitly changes
- **THEN** the delivery plan's current and next position SHALL be reviewed and updated when affected
- **AND** ordinary task progress SHALL remain owned by the relevant OpenSpec change rather than being mirrored as percentages in the delivery plan
- **AND** plan concision SHALL remain an editorial review concern rather than a new automated line-count or CI gate.

### Requirement: Agent workflow mode selection
The repository SHALL use `DEFAULT` unless the user has manually enabled project subagents, and SHALL use `MULTIAGENT` only after that explicit opt-in.

#### Scenario: Default mode
- **WHEN** a task starts and `[agents].enabled` is `false`, missing or invalid
- **THEN** the task SHALL use `DEFAULT`
- **AND** no project subagent SHALL be spawned.

#### Scenario: Multiagent mode
- **WHEN** a task starts and the user has manually set `[agents].enabled = true`
- **THEN** the task SHALL use `MULTIAGENT`
- **AND** the supervised project roles MAY be used as needed.

#### Scenario: No automatic mode change
- **WHEN** an agent considers that MULTIAGENT would provide additional assurance
- **THEN** it MAY recommend that mode and explain why
- **AND** it SHALL NOT edit `[agents].enabled`, spawn a project subagent or silently change the current task's mode.

### Requirement: Default development workflow
The DEFAULT workflow SHALL use one top-level Control session and one top-level Developer session without the specialized MULTIAGENT phase and status protocol.

#### Scenario: Default responsibility split
- **WHEN** work is performed in DEFAULT
- **THEN** Control SHALL own the active contract, final documentation, stable-diff review and completion decision
- **AND** Developer SHALL own tests and implementation in one bounded pass
- **AND** neither session SHALL be required to use MULTIAGENT statuses, task capsules, phase manifests, threat checks, assignment telemetry or role-routing logs.

#### Scenario: Changed behavior
- **WHEN** DEFAULT work adds or changes observable behavior
- **THEN** Developer SHALL derive meaningful tests from the active requirement and observe a targeted behavioral red before implementing
- **AND** compilation, discovery, configuration or infrastructure failure SHALL NOT count as red
- **AND** after red Developer SHALL NOT weaken, disable, skip or narrow the test or add production behavior that exists only for a test artifact.

#### Scenario: No new behavioral test required
- **WHEN** work changes only documentation, comments, formatting, mechanical configuration, a pure rename or an internally covered refactoring
- **THEN** Developer MAY record that no new behavioral test is needed
- **AND** existing applicable checks and the complete completion gate SHALL still run.

#### Scenario: Completion and handoff
- **WHEN** Developer finishes a DEFAULT pass
- **THEN** Developer SHALL run relevant targeted green checks and the complete local gate
- **AND** the handoff SHALL identify changed files, red and green evidence when applicable, verification results and remaining risks
- **AND** Control SHALL review test meaning before implementation details and independently run the required completion gates.

#### Scenario: Bounded repair
- **WHEN** Control finds repairable defects after the first DEFAULT pass
- **THEN** it SHALL consolidate them into one repair handoff by default
- **AND** any further repair handoff SHALL require the user's explicit decision.

### Requirement: Executable agent guidance
The repository SHALL provide concise common guidance for source responsibilities, required reading, architecture, OpenSpec, testing, review and verification, plus mode-specific workflow guidance loaded only for the selected mode.

#### Scenario: New agent onboarding
- **WHEN** an agent begins a change from the repository root
- **THEN** it SHALL identify the required reading order, affected module documentation, Definition of Done and verification commands
- **AND** it SHALL determine DEFAULT or MULTIAGENT before delegating work.

#### Scenario: Default guidance
- **WHEN** DEFAULT is selected
- **THEN** root guidance SHALL provide only the concise DEFAULT responsibility and evidence contract in addition to common project invariants
- **AND** it SHALL NOT require the specialized MULTIAGENT role, phase, status, manifest, telemetry or repair-routing protocol.

#### Scenario: Multiagent guidance
- **WHEN** MULTIAGENT is selected
- **THEN** the agent SHALL load the separate MULTIAGENT workflow document and applicable role configuration
- **AND** the supervised protocol SHALL remain unavailable as implicit permission to spawn roles in DEFAULT.

#### Scenario: Independent implementation and test ownership
- **WHEN** an implementation change requires new observable behavior in MULTIAGENT
- **THEN** Developer SHALL create any required API skeleton before Tester derives tests from the specification
- **AND** Developer SHALL not create, change, disable, exclude or otherwise narrow tests, fixtures, expected results or test configuration
- **AND** after Architect accepts red, Tester SHALL not change tests, fixtures, expectations or test configuration unless Reviewer returns `TEST_WRONG` and Architect starts a new `tests-red` phase, or an `AUDIT_FAILED` verdict explicitly assigns missing test evidence and Architect starts a distinct `tests-evidence` phase
- **AND** Developer SHALL not add production behavior that exists only for a particular test input.

#### Scenario: Suspected test defect
- **WHEN** Developer in MULTIAGENT concludes that a failing test contradicts an exact specification statement
- **THEN** Developer SHALL return `TEST_SUSPECT` without changing or bypassing the test
- **AND** only Reviewer in `ADJUDICATE` mode SHALL classify the conflict as `CODE_WRONG`, `TEST_WRONG` or `SPEC_AMBIGUOUS`.

#### Scenario: Role status vocabulary
- **WHEN** a project subagent finishes a MULTIAGENT assignment
- **THEN** it SHALL return exactly one status allowed for its role and assigned mode in the canonical routing contract
- **AND** Researcher SHALL return `RESEARCH_DONE`, `INCONCLUSIVE` or `BLOCKED`
- **AND** Tester SHALL use `EVIDENCE_CANDIDATE` only in an Architect-opened post-audit test-evidence repair phase
- **AND** an unknown, missing or mode-incompatible status SHALL be treated as a protocol error rather than inferred by Architect
- **AND** Reviewer in `THREAT_CHECK` SHALL return only `THREAT_CHECK_PASSED` or `THREATS_FOUND`.

#### Scenario: Verification ownership
- **WHEN** Developer checks an implementation pass in MULTIAGENT
- **THEN** Developer SHALL run only the targeted Surefire or Failsafe tests needed for that pass
- **AND** Architect SHALL own the red command, writer-phase manifest check and complete Maven verification lifecycle.

### Requirement: Test execution integrity
Repository verification SHALL reject committed configuration and Java test-source constructs that mechanically disable, ignore, exclude, retag or narrow the test suite required by the default Maven verification lifecycle.

#### Scenario: Explicitly disabled Java test
- **WHEN** repository convention checks inspect Java test sources
- **THEN** an explicitly disabled or ignored test SHALL fail verification
- **AND** an unconditional literal false assumption used to prevent test execution SHALL fail verification.

#### Scenario: Maven test-selection bypass
- **WHEN** repository convention checks inspect the effective project Maven configuration, including profiles
- **THEN** settings that skip tests or configure Surefire or Failsafe to include, exclude, retag or select only part of the default suite SHALL fail verification
- **AND** unrelated plugin configuration and explicitly targeted local developer commands SHALL remain unaffected.

#### Scenario: Quality-gate test-selection bypass
- **WHEN** repository convention checks inspect the required quality-gate workflow
- **THEN** its Maven verification command SHALL remain the complete clean verification lifecycle
- **AND** committed flags, properties or environment settings that skip or narrow that lifecycle SHALL fail verification.

#### Scenario: Mechanical scope of the guard
- **WHEN** a test expectation or test-specific configuration is semantically weakened without using a recognized disabling or selection mechanism
- **THEN** the mechanical convention gate is not required to infer the intent of that change
- **AND** independent review SHALL remain responsible for detecting the semantic weakening.

#### Scenario: Guard cannot disable itself
- **WHEN** the required repository verification sequence starts
- **THEN** test-integrity inspection SHALL execute outside and before the Maven lifecycle whose configuration it inspects
- **AND** a detected bypass SHALL stop verification before `clean verify`
- **AND** the stable quality-gate job SHALL run the same independent preflight before Maven without a conditional or continue-on-error escape.

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

### Requirement: Implicit nondeterminism guard
Repository verification SHALL reject unapproved implicit wall-clock reads and unseeded randomness entry points in production module source code.

#### Scenario: Production source verification
- **WHEN** the Maven repository-convention tests inspect production sources beneath the application module root
- **THEN** direct machine-clock and unseeded-randomness entry points SHALL fail verification
- **AND** explicitly supplied time sources, reference instants and seeded random sources SHALL remain allowed.
