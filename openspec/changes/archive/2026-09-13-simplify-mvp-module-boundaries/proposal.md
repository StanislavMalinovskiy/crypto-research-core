## Why

The eight-module bootstrap separates responsibilities that the MVP has not yet proved need independent lifecycle boundaries. Simplifying the empty skeleton now creates a smaller, product-shaped dependency graph before public Java contracts, business tables or provider integrations make the change expensive.

## What Changes

- **BREAKING (architecture):** replace the eight logical modules with exactly six: `kernel`, `marketdata`, `risk`, `wallet`, `signal` and `evaluation`.
- Remove the empty `governance` module. The MVP prohibits signing, order submission and PAPER/LIVE execution; executable governance gates return only in a dedicated change before execution is introduced.
- Rename the empty `strategy` module to `signal`, matching its responsibility for detection, scoring, reasoning and immutable accepted-signal snapshots.
- Merge the empty `measurement` and `research` modules into `evaluation`, which owns point-in-time valuation, outcomes, replay, aggregation and reproducible reports.
- Adopt the exact six-module DAG: `kernel` depends on none; `marketdata` on `kernel::api`; `risk` and `wallet` on `kernel::api` and `marketdata::api`; `signal` on `kernel::api`, `marketdata::api`, `risk::api` and `wallet::api`; `evaluation` on `kernel::api`, `marketdata::api` and `signal::api`.
- Keep one explicit `api` named interface for every module and preserve exact module-name and dependency-map verification alongside `ApplicationModules.verify()`.
- Clarify price ownership: `marketdata` owns observed prices, snapshots, sources and market-data quality; `evaluation` selects point-in-time observations and owns valuation, friction and outcome rules without duplicating market-price storage.
- Clarify replay ownership: `marketdata` owns raw ingestion replay and gap recovery; `evaluation` owns deterministic signal-evaluation replay from immutable recorded inputs.
- Update active architecture, Roadmap, summary, glossary, module documentation, agent guidance, OpenSpec context and the accepted modular-monolith ADR. Archived changes remain unchanged historical evidence.

Non-goals:

- No public Java value types, business API methods, events, algorithms, providers, fixtures, business tables or migrations.
- No implementation of signal detection, risk assessment, wallet analysis, valuation, replay, reporting or execution.
- No production dependencies, infrastructure components, deployables or database changes.
- No edit to archived OpenSpec changes and no weakening of exact module verification.

Affected application modules: `kernel`, `marketdata`, `risk`, `wallet`, and the replaced `governance`, `strategy`, `measurement` and `research` skeleton modules.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `module-boundaries`: replace the exact eight-module topology and dependency allow-list with the exact six-module MVP topology.
- `build-baseline`: require exactly six stable public API roots instead of eight.
- `operational-health`: update startup terminology so the foundation does not refer to the removed `strategy` module.

## Impact

- Java skeleton: remove empty governance descriptors, rename the empty strategy descriptor/API packages to signal, and replace empty measurement/research descriptors/API packages with evaluation.
- Architecture tests: retain Modulith verification and update exact module and dependency maps to six modules.
- Active documentation and OpenSpec project context: use the six-module vocabulary, execution prohibition, pricing boundary and replay boundary consistently.
- ADR 0001: supersede its eight-module count with the accepted six-module MVP decision while preserving the one-repository, one-module, one-JAR modular-monolith baseline.
- Runtime behavior, PostgreSQL schema, Flyway state, health endpoints and dependency graph outside Modulith metadata remain unchanged.
