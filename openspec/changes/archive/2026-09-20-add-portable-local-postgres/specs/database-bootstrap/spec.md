## ADDED Requirements

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
