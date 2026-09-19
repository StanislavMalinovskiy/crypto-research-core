## ADDED Requirements

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
