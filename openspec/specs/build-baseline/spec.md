# Build Baseline Specification

## Purpose

Provides a reproducible single-artifact build baseline on which later research capabilities can be implemented and verified consistently.

## Requirements

### Requirement: Reproducible wrapper build
The repository SHALL provide a Maven Wrapper that checksum-verifies its pinned Maven distribution, builds the project as one Maven module and produces one executable JAR.

#### Scenario: Clean verification build
- **WHEN** a developer runs the repository wrapper with `clean verify` on Java 25
- **THEN** Maven SHALL compile and test the project successfully
- **AND** the build SHALL produce one Spring Boot application JAR.

#### Scenario: Wrapper distribution verification
- **WHEN** the Wrapper downloads the configured Maven distribution
- **THEN** it SHALL verify the archive against the repository-pinned SHA-256 value
- **AND** a checksum mismatch SHALL stop the build before Maven executes.

### Requirement: Java 25 stable baseline
The build SHALL compile, test and run all production and test sources on Java 25 without enabling preview features.

#### Scenario: Stable Java lifecycle
- **WHEN** Maven compiles production and test sources, executes tests or runs the application
- **THEN** no lifecycle phase SHALL pass `--enable-preview`
- **AND** source code that requires a Java preview feature SHALL fail the normal build.

#### Scenario: Exact public API roots
- **WHEN** repository verification inspects public module API source roots
- **THEN** exactly the six expected business-module `api` roots SHALL be present
- **AND** no removed or unexpected top-level module API root SHALL be accepted.

### Requirement: Synchronous application baseline
The deployable application SHALL use synchronous servlet and JDBC programming models and SHALL enable virtual threads for suitable blocking work.

#### Scenario: Baseline configuration inspection
- **WHEN** the application configuration and dependency graph are verified
- **THEN** synchronous web and JDBC support SHALL be present
- **AND** reactive web, reactive database and ORM dependencies SHALL be absent.

### Requirement: Minimal production dependency baseline
The bootstrap production dependency graph SHALL contain only approved components used by the implemented runtime baseline.

#### Scenario: Bootstrap dependency inspection
- **WHEN** the resolved production dependency graph is inspected
- **THEN** the unused validation starter SHALL be absent
- **AND** a validation dependency SHALL be introduced only with an approved change that uses it.

### Requirement: Controlled platform upgrades
The repository SHALL document supported platform versions and require controlled verification for build tool, framework, database and test-container upgrades.

#### Scenario: Framework or toolchain update
- **WHEN** Java, Maven, Spring Boot or Spring Modulith is upgraded
- **THEN** the change SHALL update the relevant pinned version or supported range
- **AND** it SHALL complete the full Maven and strict OpenSpec verification lifecycle
- **AND** Spring library versions SHALL remain managed by the applicable Boot or Modulith BOM unless an approved change records a compatibility or security exception.

#### Scenario: PostgreSQL test image update
- **WHEN** the supported PostgreSQL patch level is updated
- **THEN** the Testcontainers image SHALL use an exact reviewed minor tag rather than a floating major tag
- **AND** PostgreSQL/Flyway integration verification SHALL succeed before the update is accepted.
