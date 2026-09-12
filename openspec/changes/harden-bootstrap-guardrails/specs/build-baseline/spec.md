## MODIFIED Requirements

### Requirement: Java 25 preview baseline
The build SHALL compile, test and run its implementation with Java 25 preview support while every public module API remains compilable on Java 25 without preview enabled.

#### Scenario: Preview-enabled lifecycle
- **WHEN** Maven compiles production and test sources or runs tests
- **THEN** compilation and execution SHALL enable Java preview features
- **AND** packaged application run guidance SHALL state that preview must be enabled.

#### Scenario: Stable public API compilation
- **WHEN** repository verification compiles all Java sources below the eight expected module `api` roots in one compilation task with Java release 25
- **THEN** compilation SHALL succeed without passing `--enable-preview`
- **AND** verification SHALL fail if any expected module `api` root is missing or an unexpected module `api` root is included.

#### Scenario: Compatibility harness proof
- **WHEN** the public API compatibility harness is tested with synthetic sources
- **THEN** a stable Java 25 record SHALL compile successfully
- **AND** a public contract that exposes a Java 25 preview API SHALL fail compilation with actionable diagnostics.

## ADDED Requirements

### Requirement: Minimal production dependency baseline
The bootstrap production dependency graph SHALL contain only approved components used by the implemented runtime baseline.

#### Scenario: Bootstrap dependency inspection
- **WHEN** the resolved production dependency graph is inspected
- **THEN** the unused validation starter SHALL be absent
- **AND** a validation dependency SHALL be introduced only with an approved change that uses it.
