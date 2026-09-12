## MODIFIED Requirements

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

## ADDED Requirements

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
