# Architecture

## Architectural style

Crypto Research Core is a synchronous, imperative Spring Modulith modular monolith. It is one Git repository, one Maven module, one deployable JAR and one PostgreSQL database. The same JAR may run as one or more instances with different workload roles. Logical modules and runtime roles are neither Maven modules nor independently owned services.

The application package root is `io.cryptoresearch`. Business and technical code is organized inside vertical modules. Top-level technical packages such as `domain`, `service`, `repository`, `persistence`, `provider`, `controller`, `util` and `common` are forbidden.

## Application modules

| Module | Responsibility | Documentation |
|---|---|---|
| `kernel` | Stable chain-aware value concepts shared by module APIs | [kernel](modules/kernel.md) |
| `marketdata` | Raw events, normalization, observed prices, provider adapters and raw replay | [marketdata](modules/marketdata.md) |
| `risk` | Token risk facts and decisions | [risk](modules/risk.md) |
| `wallet` | Point-in-time wallet performance and scoring | [wallet](modules/wallet.md) |
| `signal` | Versioned detection, candidates, scoring, reasoning and immutable decision-time signal snapshots | [signal](modules/signal.md) |
| `evaluation` | Point-in-time valuation, outcomes, evaluation replay, aggregation and Evidence Reports | [evaluation](modules/evaluation.md) |

## Allowed dependency directions

This is the complete baseline DAG. Every allowed edge targets a named `api` interface; everything not listed as allowed is forbidden.

| Module | Allowed dependencies | Forbidden direct dependencies |
|---|---|---|
| `kernel` | — | `marketdata`, `risk`, `wallet`, `signal`, `evaluation` |
| `marketdata` | `kernel::api` | `risk`, `wallet`, `signal`, `evaluation` |
| `risk` | `kernel::api`, `marketdata::api` | `wallet`, `signal`, `evaluation` |
| `wallet` | `kernel::api`, `marketdata::api` | `risk`, `signal`, `evaluation` |
| `signal` | `kernel::api`, `marketdata::api`, `risk::api`, `wallet::api` | `evaluation` |
| `evaluation` | `kernel::api`, `marketdata::api`, `signal::api` | `risk`, `wallet` |

Any change to this graph requires an ADR and updated module documentation before code changes. Cycles and cross-module access to internal packages are forbidden.

The graph preserves these directional invariants: `marketdata` never knows about signal detection or evaluation; `risk` and `wallet` remain independent peers; `signal` cannot write market-data storage; and `evaluation` consumes the immutable signal snapshot rather than later mutable risk or wallet state.

## Boundary pressure points

`kernel` contains only stable network-aware identities and value concepts that genuinely cross module contracts: `ChainId`, `AssetId`, `WalletAddress`, `TransactionId`, `EventId` and `BlockPosition`. `ChainId` is exact, case-sensitive customary CAIP-2 and therefore identifies a network, not only a protocol family; valid networks do not require a predefined constant, closed registry or `ChainFamily`. `EventId` is transaction-scoped and its canonical locator is parser-independent. A future EVM adapter derives that locator from the ordinal in the complete `receipt.logs` sequence, never from a filtered position or by trusting an RPC `logIndex` as identity. `BlockPosition` means Solana slot or EVM block number and remains distinct from block hash and Solana block height. Kernel has no business process, persistence, provider integration, generic repository, shared DTO collection, utility package or dependency on another module.

The MVP has no transaction signing, order submission, PAPER/LIVE execution module or executable governance gate. A dedicated approved change and ADR must define safety gates before any execution path is introduced; no placeholder module is reserved in advance.

`marketdata` is the sole owner of observed prices, price snapshots, their source, observation time and quality. `evaluation` selects admissible point-in-time observations through `marketdata::api` and owns valuation, friction and outcome rules, but never creates a competing market-price store.

Raw replay and evaluation replay are separate. `marketdata` replays recorded inputs through normalization; real provider ingestion and gap recovery remain future work. `evaluation` deterministically evaluates immutable decision-time signal snapshots against recorded market observations. `signal::api` carries accepted evidence without exposing risk or wallet implementation DTOs.

## Inter-module interaction

- Use synchronous public module APIs when the caller requires an immediate answer.
- Public contracts use ordinary stable Java values, collections and domain types. Reactive, provider-specific and persistence execution types must not cross module boundaries.
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
- Each data-owning module uses its own PostgreSQL schema. `kernel` never has a schema. `marketdata` owns raw events, normalized swaps and immutable dataset snapshots; `signal` owns candidates and accepted-signal snapshots; `evaluation` owns run manifests, ENTRY outcomes and report evidence. `risk` and `wallet` have no schema yet. Schemas are namespace boundaries, not database-role security isolation.
- Physical migration files live below `db/migration/<module>/`. Flyway uses one ordered history for the database; the first real object of a module introduces its schema.
- Allowed data exchange is a public API, immutable projection, defined event, or an analytical read model explicitly approved by ADR.
- Persistence tools are selected by operation shape:

| Operation | Required approach |
|---|---|
| Simple aggregate CRUD | Spring Data JDBC repository, only when aggregate load/save semantics fit |
| Projection, explicit query, upsert or targeted write | `JdbcClient` with visible SQL |
| High-volume write | `JdbcTemplate` or prepared JDBC batch inside the owning module |
| DDL | Flyway migration only |
| Cross-module analytical read | Separately approved read-model design or ADR |

- JPA/Hibernate, R2DBC and automatic schema mutation are forbidden.
- Cross-module SQL, joins, repository reuse, persistence entities and row mappers are forbidden. Another module uses the owner's API, immutable projection, defined event or approved read model.
- The connection pool is database admission control. Exact pool sizes, timeouts and batch limits are selected from measured workload evidence, not bootstrap guesses.
- Tables and keys use kernel identities: CAIP-2-network-scoped addresses, transaction-scoped events and ordered block positions. Raw provider observations are identified by `(chain_id, transaction_value, event_locator, provider)`; all four textual key columns use explicit deterministic bytewise `C` collation so exact equality is independent of the database default. `ingested_at`, finality and canonicality remain outside identity. Normalized blockchain events omit provider from identity while retaining it as lineage. The same local values on different networks, including case-only CAIP-2 reference differences, remain distinct.
- V1 raw storage admits only stable-inclusion historical/finalized evidence by caller contract and does not verify finality itself. Pending, pre-confirmed or otherwise provisional ingestion requires a separate approved finality/reorg change before a provider adapter may submit it.
- A parser implementation or parser-version change must preserve the canonical locator of the same blockchain event. Changing the persisted locator grammar requires a separate approved change and forward migration; historical identities are never silently reinterpreted.
- Raw observation persistence derives `sha256:<lowercase-hex>` from exact UTF-8 payload bytes, normalizes instants to PostgreSQL microseconds and uses insert-first `ON CONFLICT DO NOTHING RETURNING`. Equal retries keep the first record; conflicting immutable evidence fails without overwrite. V1 is an ordinary unpartitioned table with only its natural primary-key index: adding `ingested_at` to that key would let later retries bypass global idempotency. No surrogate ID, BRIN or provider transport is part of this storage boundary.
- V2 normalized swaps use exact integer quantities, `NUMERIC(38,18)` price, `NUMERIC(38,8)` liquidity and `NUMERIC(5,4)` confidence. Their sole secondary index implements the chain/asset/observation-time query. Immutable dataset snapshots contain a deterministic ordered membership and a versioned length-prefixed SHA-256 content fingerprint.
- V5–V9 add the accepted F2 core-storage boundary: one complete provider payload per `(chain_id, transaction_value, provider)`, separate immutable price and liquidity observations, USD conversion facts with database-enforced lineage to both source price observations, and fingerprinted point-in-time universe snapshots. High-volume writes and snapshot membership use bounded 1,000-row batches; the tables remain unpartitioned until provider spikes supply measured volume evidence. This boundary is not yet live-ready: F3 must add forward-only token-decimal, provider-visible/modeled-availability and explicit quality-provenance facts before a provider adapter admits live data.
- V3 signal persistence commits the deduplicated candidate before risk gating, then stores the decision and optional accepted snapshot in a separate module transaction. V4 evaluation persistence atomically owns the complete run manifest, one run-scoped `1h` ENTRY outcome and its deterministic report. Neither migration introduces cross-module SQL or a risk schema.
- `IF NOT EXISTS` and `CREATE OR REPLACE` are used only when migration semantics make rerun-safe behavior intentional. Indexes require a documented query pattern.

See [ADR 0002](adr/0002-spring-data-jdbc.md), [ADR 0004](adr/0004-module-data-ownership.md) and the [operating contract](OPERATIONS.md).

## Research reproducibility policy

The same immutable dataset snapshot, cutoff, build/source revision, algorithm version, canonical configuration and seed must produce the same ordered domain result regardless of concurrency or completion order. Authoritative timestamps use UTC `Instant` semantics and domain/application logic receives `Clock` or a reference instant explicitly. Authoritative financial values use raw integer units or exact decimal arithmetic with explicit precision, scale and rounding; binary floating point is not authoritative.

`marketdata` owns input lineage and dataset fingerprints, `signal` owns definition/configuration identity and decision-time evidence, and `evaluation` owns run manifests, outcomes and reports. Order-sensitive queries and computations define a total order and stable tie-break. The detailed contract is [Research reproducibility](REPRODUCIBILITY.md), accepted by [ADR 0009](adr/0009-research-reproducibility.md).

## Concurrency policy

Module APIs remain synchronous. Virtual threads are preferred for suitable blocking HTTP/RPC/database tasks, but never serve as admission control. Each provider adapter has an explicit concurrency limit, rate limit, timeout and finite retry policy for transient errors. Queues and batches are bounded; cancellation is propagated; ingestion and jobs are idempotent. Database concurrency is limited by a deliberate budget no larger than connection-pool capacity, regardless of virtual-thread count. CPU-bound work uses bounded platform-thread executors. Preview features are disabled by [ADR 0007](adr/0007-stable-java-25-baseline.md). WebSocket callbacks hand work to bounded queues for synchronous batch processing.

## Background work

One JAR may run with workload roles such as `api`, `ingestion` or `evaluation`; a role enables workloads, not a new service boundary. Every recurring or recoverable job is idempotent and uses PostgreSQL-backed claiming across instances. Short database-local work may claim batches with `FOR UPDATE SKIP LOCKED`; work spanning remote I/O uses an expiring lease such as `locked_until`. Claims are committed before provider calls, and retries cannot create duplicate domain effects.

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
- [ADR 0007: Stable Java 25 baseline](adr/0007-stable-java-25-baseline.md)
- [ADR 0008: Six-module MVP topology](adr/0008-six-module-mvp-topology.md)
- [ADR 0009: Research reproducibility baseline](adr/0009-research-reproducibility.md)
- [Module documentation](modules/README.md)
- [Operating contract](OPERATIONS.md)
- [Testing strategy](TESTING.md)
- [Research reproducibility](REPRODUCIBILITY.md)
- [Roadmap](ROADMAP.md)
- [Tech stack](TECH_STACK.md)
