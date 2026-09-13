## MODIFIED Requirements

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
