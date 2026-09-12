## Purpose

Provides a reproducible single-artifact build baseline on which later research capabilities can be implemented and verified consistently.

## ADDED Requirements

### Requirement: Reproducible wrapper build
The repository SHALL provide a Maven Wrapper that builds the project as one Maven module and produces one executable JAR.

#### Scenario: Clean verification build
- **WHEN** a developer runs the repository wrapper with `clean verify` on Java 25
- **THEN** Maven SHALL compile and test the project successfully
- **AND** the build SHALL produce one Spring Boot application JAR.

### Requirement: Java 25 preview baseline
The build SHALL compile and test with Java 25 preview support while keeping preview API types out of public module contracts.

#### Scenario: Preview-enabled lifecycle
- **WHEN** Maven compiles production and test sources
- **THEN** both compilation and test execution SHALL enable Java preview features
- **AND** public module API verification SHALL remain independent of preview-only types.

### Requirement: Synchronous application baseline
The deployable application SHALL use synchronous servlet and JDBC programming models and SHALL enable virtual threads for suitable blocking work.

#### Scenario: Baseline configuration inspection
- **WHEN** the application configuration and dependency graph are verified
- **THEN** synchronous web and JDBC support SHALL be present
- **AND** reactive web, reactive database and ORM dependencies SHALL be absent.
