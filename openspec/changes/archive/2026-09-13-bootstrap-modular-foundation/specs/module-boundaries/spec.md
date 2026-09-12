## Purpose

Defines the business-oriented module boundaries that prevent later capabilities from creating cyclic or implementation-level coupling.

## ADDED Requirements

### Requirement: Eight logical modules
The application SHALL expose exactly the logical modules `kernel`, `governance`, `marketdata`, `risk`, `wallet`, `strategy`, `measurement` and `research` beneath the `io.cryptoresearch` package root.

#### Scenario: Module model discovery
- **WHEN** the application module model is discovered from the application root
- **THEN** all eight required modules SHALL be present
- **AND** no top-level technical module SHALL be present.

### Requirement: Allowed dependency graph
The module model SHALL enforce the dependency directions defined in `docs/ARCHITECTURE.md`, with every cross-module access restricted to a declared `api` named interface. The baseline SHALL allow: `kernel` to depend on none; `governance` and `marketdata` on `kernel::api`; `risk` and `wallet` on `kernel::api` and `marketdata::api`; `strategy` on `kernel::api`, `marketdata::api`, `risk::api` and `wallet::api`; `measurement` on `kernel::api`, `marketdata::api`, `strategy::api` and `governance::api`; and `research` on the named APIs of `marketdata`, `risk`, `wallet`, `strategy` and `measurement`.

#### Scenario: Boundary verification
- **WHEN** automated module verification runs
- **THEN** the declared dependency graph SHALL be valid
- **AND** undeclared directions, cyclic dependencies and access to another module's implementation packages SHALL fail verification.

### Requirement: Module descriptors
Every logical module SHALL have an explicit descriptor, and every exposed API package SHALL be a named public interface.

#### Scenario: Descriptor inspection
- **WHEN** module metadata is inspected
- **THEN** each required module SHALL declare its allowed dependencies
- **AND** each module API package SHALL be explicitly named for cross-module use
- **AND** an automated test SHALL lock the descriptor values to the documented graph even before business imports exist.
