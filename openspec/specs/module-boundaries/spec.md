# Module Boundaries Specification

## Purpose

Defines the business-oriented module boundaries that prevent later capabilities from creating cyclic or implementation-level coupling.

## Requirements

### Requirement: Six logical modules
The application SHALL expose exactly the logical modules `kernel`, `marketdata`, `risk`, `wallet`, `signal` and `evaluation` beneath the `io.cryptoresearch` package root.

#### Scenario: Module model discovery
- **WHEN** the application module model is discovered from the application root
- **THEN** all six required modules SHALL be present
- **AND** no removed, unexpected or top-level technical module SHALL be present.

### Requirement: Allowed dependency graph
The module model SHALL enforce the dependency directions defined in `docs/ARCHITECTURE.md`, with every cross-module access restricted to a declared `api` named interface. The baseline SHALL allow: `kernel` to depend on none; `marketdata` on `kernel::api`; `risk` and `wallet` on `kernel::api` and `marketdata::api`; `signal` on `kernel::api`, `marketdata::api`, `risk::api` and `wallet::api`; and `evaluation` on `kernel::api`, `marketdata::api` and `signal::api`.

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

### Requirement: MVP execution exclusion
The MVP architecture SHALL contain no module or public contract for signing transactions, submitting orders or enabling PAPER or LIVE execution.

#### Scenario: Execution boundary review
- **WHEN** the MVP module model and active architecture documentation are inspected
- **THEN** execution and governance gates SHALL be absent from the implemented module topology
- **AND** any future execution capability SHALL require a dedicated approved change before implementation.

### Requirement: Price and replay responsibility ownership
The active module architecture SHALL assign each market-price and replay responsibility to one module without duplicate ownership.

#### Scenario: Price responsibility review
- **WHEN** price responsibilities are inspected
- **THEN** `marketdata` SHALL own observed prices, price snapshots, sources, quality and observation time
- **AND** `evaluation` SHALL own point-in-time price selection, valuation, friction and outcome rules without owning a second market-price store.

#### Scenario: Replay responsibility review
- **WHEN** replay responsibilities are inspected
- **THEN** `marketdata` SHALL own replay of raw ingestion inputs and gap recovery
- **AND** `evaluation` SHALL own deterministic evaluation replay from immutable signal snapshots and recorded inputs.

### Requirement: Point-in-time evaluation isolation
Evaluation SHALL derive outcomes from the immutable signal snapshot recorded at decision time rather than consulting later mutable risk or wallet state.

#### Scenario: Evaluation dependency review
- **WHEN** module dependencies and evaluation inputs are inspected
- **THEN** `evaluation` SHALL depend on `signal::api` and SHALL not depend on `risk::api` or `wallet::api`
- **AND** the signal snapshot SHALL not expose internal risk or wallet implementation DTOs.
