<!-- Экспортировано из Notion 2026-09-12. Исходная страница: https://app.notion.com/p/29f7a744d2df80088cd4fb8ceb297a50?pvs=204 -->

# Tech stack — current baseline
**Обновлено:** 12 сентября 2026
## Runtime and framework
- **Java:** 25
- **Spring Boot:** 4.1.1
- **Spring Modulith:** 2.1.1
- **Web framework:** Spring MVC, synchronous imperative model
- **Persistence:** Spring Data JDBC / JdbcClient
- **Build:** Maven Wrapper; Maven 3.9.x baseline; one Maven module and one deployable JAR
- **Migrations:** Flyway version managed by Spring Boot dependency management
## Java 25 model
Use modern Java 25 features where they reduce complexity:
- records for immutable data carriers and value objects;
- sealed interfaces for closed domain hierarchies;
- pattern matching and switch expressions;
- virtual threads for blocking HTTP/RPC/database work;
- `StructuredTaskScope` for bounded parallel calls to independent providers;
- `ScopedValue` for immutable task context where appropriate;
- preview features enabled in Maven compile and test configuration.
Preview and JDK-specific types must stay inside internal implementation packages. They must not leak into domain records, module APIs or provider ports.
## Synchronous boundary
- No Spring WebFlux, Reactor application pipelines, R2DBC or Vert.x.
- Module contracts return ordinary values, collections and domain types, not `Mono`, `Flux`, `Future` or provider-specific async types.
- WebSocket adapters may use callbacks internally because the transport is event-driven.
- Callback events are transferred to bounded queues and processed synchronously in batches on virtual threads.
- CPU-bound work uses bounded platform-thread executors rather than unbounded virtual-thread concurrency.
## Modularity
One modular monolith with eight Spring Modulith application modules:
`kernel`, `governance`, `marketdata`, `risk`, `wallet`, `strategy`, `measurement`, `research`.
Each module owns its domain, persistence and provider adapters. Cross-module access is allowed only through named public interfaces. Module structure and cycles are verified in tests.
## Infrastructure
- **Database:** PostgreSQL 16 with monthly partitioning for high-volume time-series tables
- **Cache:** Redis 7 + Caffeine
- **Observability:** Micrometer, Prometheus, Grafana, structured JSON logs, correlation ID
- **Tests:** Testcontainers, Spring Modulith Test, ArchUnit, JUnit managed by Spring Boot
## Phase 1 scope
- Telegram integration is not included.
- Results are persisted to database tables and inspected through SQL/Grafana.
- LIVE execution is physically blocked by `governance`.
- Solana provider: Helius.
- Historical, market and risk providers are selected only after current plan and data-coverage validation.
- Arkham/Nansen are optional attribution enrichment, not the source of truth for wallet performance.
- EVM providers are deferred until the Solana Evidence Report justifies Phase B.
References: [Spring Boot 4.1.1 requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Spring Modulith 2.1.1](https://spring.io/projects/spring-modulith/).
