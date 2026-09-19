# Database Bootstrap Specification

## Purpose

Establishes the ongoing PostgreSQL and Flyway contract that applies module-owned schema changes and proves them against the actual database engine.

## Requirements

### Requirement: External PostgreSQL configuration
The application SHALL obtain its PostgreSQL connection URL and credentials from external configuration with documented local-development defaults.

#### Scenario: Runtime database configuration
- **WHEN** database environment variables are supplied at startup
- **THEN** the application SHALL connect using those values
- **AND** it SHALL not require source-code changes for another PostgreSQL instance.

### Requirement: Flyway-owned schema evolution
Flyway SHALL be the only component authorized to mutate the database schema, and the first versioned migration SHALL introduce the `marketdata` schema together with its first real durable object.

#### Scenario: Empty bootstrap database
- **WHEN** the application starts against an empty supported PostgreSQL database
- **THEN** Flyway SHALL create the `marketdata` schema and raw-observation table through one versioned migration
- **AND** the table SHALL use the CAIP-2 network-qualified identity key `(chain_id, transaction_value, event_locator, provider)`
- **AND** no ORM or application startup code SHALL mutate the schema.

### Requirement: Real database verification
The Maven verification lifecycle SHALL validate database startup, applied migration state and raw-observation constraints against an isolated PostgreSQL Testcontainers instance.

#### Scenario: Integration verification
- **WHEN** `clean verify` runs with a working Docker engine
- **THEN** an isolated PostgreSQL 18.6 container SHALL start and apply the first versioned migration exactly once
- **AND** the application context SHALL connect successfully and confirm that Flyway has no pending migrations.

### Requirement: Portable local PostgreSQL lifecycle
The repository SHALL provide a documented local-development lifecycle that starts the exact supported PostgreSQL 18.6 database on any workstation with Docker Compose, uses development defaults compatible with the application, and preserves that workstation's database across ordinary stop and container-recreation operations.

#### Scenario: Start on a new workstation
- **WHEN** a developer clones the repository on a workstation with Docker Compose and starts the documented database service
- **THEN** the service SHALL run the exact supported PostgreSQL 18.6 image
- **AND** it SHALL become healthy with the documented local database name and credentials
- **AND** the application SHALL connect through its documented local defaults and let Flyway apply every pending migration.

#### Scenario: Preserve local data
- **WHEN** the developer stops or recreates the local database service without requesting a destructive reset
- **THEN** the database SHALL reuse repository-defined persistent storage
- **AND** previously committed PostgreSQL data and Flyway migration history SHALL remain available.

#### Scenario: Reset local data explicitly
- **WHEN** the developer intentionally invokes the documented destructive-reset command
- **THEN** the command SHALL clearly identify that the local database volume and its data will be removed
- **AND** the next start SHALL initialize an empty database for Flyway to migrate.

### Requirement: Local database isolation and configuration safety
The local-development database SHALL be reachable only through the developer workstation's loopback interface by default, SHALL allow documented local port and credential overrides without source changes, and SHALL keep developer-local environment files and non-development credentials out of version control.

#### Scenario: Default network exposure
- **WHEN** the local database service publishes PostgreSQL to the host
- **THEN** the published endpoint SHALL bind to loopback rather than every host interface
- **AND** no repository default SHALL expose the database to the LAN or Internet.

#### Scenario: Workstation-specific override
- **WHEN** a workstation needs a different local port or development credential
- **THEN** the developer SHALL be able to supply the value through ignored local environment configuration
- **AND** the repository SHALL provide a safe example containing development values only
- **AND** changing initialization credentials for an existing volume SHALL be documented as requiring an explicit database update or destructive local reset.

#### Scenario: Use another workstation
- **WHEN** the repository is used on a second workstation
- **THEN** that workstation SHALL create or reuse its own local persistent database volume
- **AND** the local lifecycle SHALL NOT claim to synchronize data between workstations or replace a future managed research database.

### Requirement: Explicit external PostgreSQL profile
The repository SHALL provide an explicitly selected external-PostgreSQL runtime profile that obtains one JDBC endpoint, username and password from a required workstation-local secrets file and uses that connection identity for both the application datasource and Flyway, while preserving the existing local Compose defaults when that profile is not selected.

#### Scenario: Start against configured external PostgreSQL
- **WHEN** the external-PostgreSQL profile is selected with a complete ignored secrets file
- **THEN** the application datasource SHALL use the configured JDBC endpoint and credentials
- **AND** Flyway SHALL use the same datasource connection identity without requiring separate migration settings
- **AND** no source-code change SHALL be required between workstations.

#### Scenario: External profile is incomplete
- **WHEN** the external-PostgreSQL profile is selected but its secrets file or any mandatory connection setting is absent
- **THEN** application startup SHALL fail before accepting useful work
- **AND** it SHALL NOT silently fall back to the local Compose endpoint or development credentials.

#### Scenario: Continue using portable local PostgreSQL
- **WHEN** the external-PostgreSQL profile is not selected
- **THEN** the existing loopback-only local Compose defaults SHALL remain effective
- **AND** the external secrets file SHALL NOT be required.

### Requirement: External database secret-file safety
The repository SHALL provide a tracked placeholder-only example for external PostgreSQL settings, SHALL ignore the corresponding populated workstation-local file, and SHALL keep that local file out of the packaged application and automated test database lifecycle.

#### Scenario: Prepare a workstation
- **WHEN** a developer prepares external PostgreSQL access on a new workstation
- **THEN** the developer SHALL have a documented example containing no real host, username, password or certificate path
- **AND** the populated local file SHALL be ignored by version control.

#### Scenario: Run automated verification
- **WHEN** the Maven verification lifecycle runs
- **THEN** database integration tests SHALL continue to use isolated PostgreSQL Testcontainers
- **AND** they SHALL NOT read, require or connect to the workstation-local external database configuration.
