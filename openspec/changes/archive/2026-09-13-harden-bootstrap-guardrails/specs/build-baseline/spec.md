## ADDED Requirements

### Requirement: Java 25 stable baseline
The build SHALL compile, test and run all production and test sources on Java 25 without enabling preview features.

#### Scenario: Stable Java lifecycle
- **WHEN** Maven compiles production and test sources, executes tests or runs the application
- **THEN** no lifecycle phase SHALL pass `--enable-preview`
- **AND** source code that requires a Java preview feature SHALL fail the normal build.

#### Scenario: Exact public API roots
- **WHEN** repository verification inspects public module API source roots
- **THEN** exactly the eight expected business-module `api` roots SHALL be present
- **AND** no unexpected top-level module API root SHALL be accepted.

### Requirement: Minimal production dependency baseline
The bootstrap production dependency graph SHALL contain only approved components used by the implemented runtime baseline.

#### Scenario: Bootstrap dependency inspection
- **WHEN** the resolved production dependency graph is inspected
- **THEN** the unused validation starter SHALL be absent
- **AND** a validation dependency SHALL be introduced only with an approved change that uses it.

## REMOVED Requirements

### Requirement: Java 25 preview baseline

**Reason**: The repository has no implemented preview use case, and stable virtual threads already support the current synchronous blocking-I/O baseline.

**Migration**: Remove preview flags and runtime guidance. A future preview feature requires an approved change that identifies the exact feature, confines it to internal implementation, documents runtime requirements and verifies the JDK upgrade path.
