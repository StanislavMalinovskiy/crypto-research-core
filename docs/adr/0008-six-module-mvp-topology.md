# ADR 0008: Six-module MVP topology

- Status: Accepted
- Date: 2026-09-13
- Supersedes: the eight-module topology portion of [ADR 0001](0001-modular-monolith.md)

## Context

The bootstrap introduced eight empty Spring Modulith modules before their public contracts, persistence or independent lifecycles existed. For the MVP, governance has no executable behavior, strategy is actually signal detection, and measurement plus research form one evaluation lifecycle. Keeping those speculative boundaries would add coordination and naming cost without protecting a proven separation.

## Decision

Keep the modular-monolith deployment shape from ADR 0001 and use exactly six logical modules: `kernel`, `marketdata`, `risk`, `wallet`, `signal` and `evaluation`.

The complete dependency DAG is:

- `kernel`: none;
- `marketdata`: `kernel::api`;
- `risk`: `kernel::api`, `marketdata::api`;
- `wallet`: `kernel::api`, `marketdata::api`;
- `signal`: `kernel::api`, `marketdata::api`, `risk::api`, `wallet::api`;
- `evaluation`: `kernel::api`, `marketdata::api`, `signal::api`.

Every cross-module dependency targets a named `api` interface. Automated tests retain exact module-name and dependency-map assertions in addition to `ApplicationModules.verify()`.

`marketdata` owns observed prices, price snapshots, sources, observation time, quality and raw-input replay. `evaluation` owns point-in-time price selection, valuation, friction, outcomes, deterministic signal-evaluation replay and reports; it does not own a second market-price store. Evaluation consumes an immutable decision-time snapshot from `signal::api` and does not depend on current `risk` or `wallet` state.

The MVP contains no transaction signing, order submission, PAPER/LIVE execution or governance module. Execution and its safety gates require a separate approved change and ADR before implementation.

## Consequences

The package model is smaller while preserving explicit data-flow direction. `risk` and `wallet` remain independent because their facts and lifecycles differ. `evaluation` is intentionally cohesive around evaluating recorded signals; it can be split later only when independent change cadence, ownership or scaling is demonstrated.

The removed packages contain no public Java types or durable data, so no compatibility or database migration is required. Archived OpenSpec changes remain historical evidence of the original eight-module bootstrap.

## Alternatives considered

Keeping all eight modules preserves the bootstrap layout but fixes unproven boundaries. Merging risk and wallet into signal hides independently useful facts and encourages a large signal module. Removing all module boundaries loses enforceable ownership and dependency direction.
