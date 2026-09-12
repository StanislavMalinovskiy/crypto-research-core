# Database Bootstrap Specification

## Purpose

Establishes a PostgreSQL and Flyway baseline that proves schema changes against the actual database engine before business tables are introduced.

## Requirements

### Requirement: External PostgreSQL configuration
The application SHALL obtain its PostgreSQL connection URL and credentials from external configuration with documented local-development defaults.

#### Scenario: Runtime database configuration
- **WHEN** database environment variables are supplied at startup
- **THEN** the application SHALL connect using those values
- **AND** it SHALL not require source-code changes for another PostgreSQL instance.

### Requirement: Flyway-owned schema evolution
Flyway SHALL be the only component authorized to mutate the database schema, and the bootstrap SHALL contain no placeholder versioned migration before a module owns real schema.

#### Scenario: Empty bootstrap database
- **WHEN** the application starts against an empty supported PostgreSQL database
- **THEN** Flyway SHALL initialize successfully without applying a versioned migration
- **AND** no ORM schema mutation SHALL run.

### Requirement: Real database verification
The Maven verification lifecycle SHALL validate database startup and migration behavior against an isolated PostgreSQL Testcontainers instance.

#### Scenario: Integration verification
- **WHEN** `clean verify` runs with a working Docker engine
- **THEN** an isolated PostgreSQL container SHALL start
- **AND** the application context SHALL connect to it and confirm the empty Flyway baseline is healthy.
