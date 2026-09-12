# Architecture

## Architectural style

Crypto Research Core is a synchronous, imperative Spring Modulith modular monolith. It is one Git repository, one Maven module, one deployable JAR and one PostgreSQL database. The same JAR may run as one or more instances with different workload roles. Logical modules and runtime roles are neither Maven modules nor independently owned services.

The application package root is `io.cryptoresearch`. Business and technical code is organized inside vertical modules. Top-level technical packages such as `domain`, `service`, `repository`, `persistence`, `provider`, `controller`, `util` and `common` are forbidden.

## Application modules

| Module | Responsibility | Documentation |
|---|---|---|
| `kernel` | Stable chain-aware value concepts shared by module APIs | [kernel](modules/kernel.md) |
| `governance` | Execution modes and safety gates | [governance](modules/governance.md) |
| `marketdata` | Raw events, normalization, swaps, prices and provider adapters | [marketdata](modules/marketdata.md) |
| `risk` | Token risk facts and decisions | [risk](modules/risk.md) |
| `wallet` | Point-in-time wallet performance and scoring | [wallet](modules/wallet.md) |
| `strategy` | Versioned experiments, candidates, scoring and accepted signals | [strategy](modules/strategy.md) |
| `measurement` | Virtual positions, valuation, friction and outcomes | [measurement](modules/measurement.md) |
| `research` | Replay, backtest, aggregation and Evidence Report | [research](modules/research.md) |

## Allowed dependency directions

This is the complete baseline DAG. Every allowed edge targets a named `api` interface; everything not listed as allowed is forbidden.

| Module | Allowed dependencies | Forbidden direct dependencies |
|---|---|---|
| `kernel` | — | `governance`, `marketdata`, `risk`, `wallet`, `strategy`, `measurement`, `research` |
| `governance` | `kernel::api` | `marketdata`, `risk`, `wallet`, `strategy`, `measurement`, `research` |
| `marketdata` | `kernel::api` | `governance`, `risk`, `wallet`, `strategy`, `measurement`, `research` |
| `risk` | `kernel::api`, `marketdata::api` | `governance`, `wallet`, `strategy`, `measurement`, `research` |
| `wallet` | `kernel::api`, `marketdata::api` | `governance`, `risk`, `strategy`, `measurement`, `research` |
| `strategy` | `kernel::api`, `marketdata::api`, `risk::api`, `wallet::api` | `governance`, `measurement`, `research` |
| `measurement` | `kernel::api`, `marketdata::api`, `strategy::api`, `governance::api` | `risk`, `wallet`, `research` |
| `research` | `marketdata::api`, `risk::api`, `wallet::api`, `strategy::api`, `measurement::api` | `kernel`, `governance` |

Any change to this graph requires an ADR and updated module documentation before code changes. Cycles and cross-module access to internal packages are forbidden.

The graph preserves these directional invariants: `marketdata` never knows about strategy or research; `wallet` never knows about research; `strategy` cannot write market-data storage; `measurement` observes results but does not control strategy; and no lower module depends on `research`.

## Boundary pressure points

`kernel` contains only stable chain-aware identities and value concepts that genuinely cross module contracts, such as chain, asset, wallet, transaction and block references. It has no business process, persistence, provider integration, generic repository, shared DTO collection, utility package or dependency on another module.

`governance` is limited to execution modes, dangerous-capability gates, policy decisions and their audit semantics. It is not a global orchestrator, security umbrella, validation module or home for cross-cutting concerns. Bootstrap gives it only a descriptor and documentation; LIVE/PAPER behavior is introduced only when an execution-related change needs it.

## Inter-module interaction

- Use synchronous public module APIs when the caller requires an immediate answer.
- Public contracts use ordinary Java values, collections and domain types. Reactive, provider-specific, persistence and preview-JDK types must not cross module boundaries.
- Use application events only for completed, low-frequency business facts. Event schemas and exact publishers/consumers remain TBD until their implementing changes.
- High-volume swaps and price ticks stay inside `marketdata`; they are not written to the Spring Modulith Event Publication Registry.
- A module never imports another module's `application`, `domain` or `infrastructure` packages.

## Transaction and event policy

1. The owning module's application use case defines the `@Transactional` boundary; controllers and repositories do not start business transactions.
2. A repository never starts a new business transaction and is never called from another module.
3. A synchronous cross-module API call is allowed only by the DAG and participates in a transaction only when short atomicity is explicitly intended.
4. Provider HTTP/RPC calls, rate-limit waits and other long blocking work run outside database transactions.
5. Post-commit work uses an explicit after-commit mechanism; it must not accidentally execute inside the state-changing transaction.
6. Flows that may later distribute avoid deep cross-module atomic transactions and use idempotent state transitions.

| Flow | Mechanism |
|---|---|
| High-frequency swaps, ticks and normalized market data | Bounded batch pipeline, owned tables and bounded queues |
| Rare completed business facts | Spring Modulith application events with explicit synchronous/after-commit semantics |
| Facts requiring guaranteed delivery | Separate future ADR/change for JDBC event registry or outbox |
| Immediate intra-flow decision | Synchronous public module API |

Every-swap publication through the Spring Modulith Event Publication Registry is forbidden. See [ADR 0005](adr/0005-transactions-and-events.md).

## Persistence policy

- PostgreSQL is the only database in the current stage; Flyway is the sole schema owner.
- Every table has exactly one owning module. That module exclusively owns its aggregates, SQL, repositories, row mappers and migrations; other modules cannot query or join the table directly.
- Each data-owning module uses its own PostgreSQL schema. `kernel` never has a schema; `governance` receives one only with real durable state. Schemas are namespace boundaries, not database-role security isolation.
- Physical migration files live below `db/migration/<module>/`. Flyway uses one ordered history for the database; the first real object of a module introduces its schema.
- Allowed data exchange is a public API, immutable projection, defined event, or an analytical read model explicitly approved by ADR.
- Spring Data JDBC and `JdbcClient` are allowed. JPA/Hibernate, R2DBC and automatic schema mutation are forbidden.
- Tables and keys are chain-aware where the Roadmap requires identity by `chain + address` or `chain + tx_hash + event_index`.
- `IF NOT EXISTS` and `CREATE OR REPLACE` are used only when migration semantics make rerun-safe behavior intentional. Indexes require a documented query pattern.

See [ADR 0004](adr/0004-module-data-ownership.md).

## Concurrency policy

Module APIs remain synchronous. Virtual threads are preferred for suitable blocking HTTP/RPC/database tasks, but never serve as admission control. Each provider adapter has an explicit concurrency limit, rate limit, timeout and finite retry policy for transient errors. Queues and batches are bounded; cancellation is propagated; ingestion and jobs are idempotent. Database concurrency is limited by a deliberate budget no larger than connection-pool capacity, regardless of virtual-thread count. CPU-bound work uses bounded platform-thread executors. Preview APIs remain internal. WebSocket callbacks hand work to bounded queues for synchronous batch processing.

## Background work

One JAR may run with workload roles such as `api`, `ingestion` or `measurement`; a role enables workloads, not a new service boundary. Every recurring or recoverable job is idempotent and uses PostgreSQL-backed claiming across instances. Short database-local work may claim batches with `FOR UPDATE SKIP LOCKED`; work spanning remote I/O uses an expiring lease such as `locked_until`. Claims are committed before provider calls, and retries cannot create duplicate domain effects.

See [ADR 0006](adr/0006-background-work-and-bounded-concurrency.md).

## Scaling policy

Scale the modular monolith vertically and optimize measured database/provider bottlenecks first. Multiple instances of the same JAR may select different workload roles and coordinate through PostgreSQL. A physical code split, another artifact, another database, cache, broker or infrastructure component requires measured evidence, an OpenSpec change and an ADR. Multi-chain implementation and cross-chain identity remain later phases; the core is only chain-ready during the Solana MVP.

## Decisions and detailed contracts

- [ADR index](adr/README.md)
- [ADR 0001: Modular monolith](adr/0001-modular-monolith.md)
- [ADR 0002: Spring Data JDBC](adr/0002-spring-data-jdbc.md)
- [ADR 0003: Synchronous Java 25 baseline](adr/0003-synchronous-java-25-baseline.md)
- [ADR 0004: Module data ownership](adr/0004-module-data-ownership.md)
- [ADR 0005: Transactions and events](adr/0005-transactions-and-events.md)
- [ADR 0006: Background work and bounded concurrency](adr/0006-background-work-and-bounded-concurrency.md)
- [Module documentation](modules/README.md)
- [Roadmap](ROADMAP.md)
- [Tech stack](TECH_STACK.md)
