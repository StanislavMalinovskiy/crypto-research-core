# Tech stack and decision status

**Обновлено:** 13 сентября 2026

This document records allowed technologies and their decision state. Product sequencing belongs to [Roadmap](ROADMAP.md), current architecture to [Architecture](ARCHITECTURE.md), and accepted architectural rationale to [ADRs](adr/README.md).

## Current baseline

### Runtime and framework

- **Java:** 25.
- **Spring Boot:** 4.1.1.
- **Spring Modulith:** 2.1.1.
- **Web framework:** Spring MVC with a synchronous imperative model.
- **Persistence:** Spring Data JDBC and `JdbcClient`.
- **Database:** PostgreSQL 18; one database instance and no business schema in bootstrap.
- **Migrations:** Flyway managed by Spring Boot dependency management.
- **Build:** Maven Wrapper with checksum-verified Maven 3.9.16, one Maven module and one deployable JAR.
- **Health:** Spring Boot Actuator health endpoint.
- **Tests:** JUnit, Spring Modulith Test and PostgreSQL Testcontainers managed by the existing BOMs.
- **Specification workflow:** OpenSpec 1.13.0 in CI; generated skills remain owned by the installed CLI.

### Java 25 model

- Use records for immutable data carriers and value objects.
- Use sealed interfaces only for genuinely closed domain hierarchies.
- Use virtual threads for suitable blocking I/O, with explicit admission and resource limits.
- Maven compilation, tests and `spring-boot:run` use stable Java 25 without `--enable-preview`.
- A preview feature requires an approved OpenSpec change and superseding ADR naming the exact JEP, need, internal boundary, runtime flag and JDK-upgrade verification.

### Synchronous boundary

- Module contracts return ordinary values, collections and domain types, not reactive or provider-specific async types.
- WebSocket callbacks may exist only inside transport adapters and must hand work to bounded processing.
- CPU-bound work uses bounded platform-thread executors.
- JPA/Hibernate ORM, Spring WebFlux, Reactor application pipelines, R2DBC, Vert.x and Lombok are forbidden.

### Modularity and persistence ownership

The application is one modular monolith with `kernel`, `governance`, `marketdata`, `risk`, `wallet`, `strategy`, `measurement` and `research`. Cross-module access uses only named public interfaces. Each data-owning module owns its SQL, repositories, row mappers and Flyway migrations as defined by [ADR 0004](adr/0004-module-data-ownership.md).

## Target MVP

The following are planned directions, not installed bootstrap components:

- chain-aware kernel identities;
- idempotent module-owned market-data storage;
- Solana provider adapters, normalization and replay;
- table-specific partitioning where the owning change demonstrates volume, retention and query-pattern need;
- structured logging, correlation metadata and workload-specific operational metrics;
- governance gates before PAPER/LIVE execution.

A concrete provider requires coverage, rate-limit and commercial-terms validation, an OpenSpec design and explicit approval for any new production dependency. An ADR is required only when its introduction changes the provider boundary or general architecture.

A concrete high-volume table may introduce partitioning in its owning migration with documented evidence. An ADR is required only for a change to the general persistence strategy.

## Deferred

- Redis and Caffeine.
- Prometheus and Grafana infrastructure.
- Kafka or another broker.
- Additional databases, Maven modules, deployables or microservices.
- EVM providers and multi-chain implementations before the Solana evidence justifies them.
- Arkham/Nansen attribution enrichment and execution providers.

A cache, broker, additional database, deployable, external observability platform or general partitioning-policy change requires measured need and an approved OpenSpec change. It also requires an ADR when it changes the architectural baseline.

## Supported versions and upgrades

- Java major 25 is the only supported runtime and build range (`[25,26)`). A new major requires an approved change and full verification.
- Maven is invoked through the Wrapper only. Update its 3.9.16 distribution URL and SHA-256 atomically, then run a clean Wrapper build.
- Spring Boot 4.1.1 remains the parent and owns Spring Framework, Spring Data, Flyway and other managed versions. A direct override requires an approved compatibility or security exception.
- Spring Modulith 2.1.1 remains an explicitly imported BOM compatible with the Boot baseline. Boot and Modulith upgrades require a reviewed change and must be verified together.
- PostgreSQL major 18 is the supported runtime family. Integration tests use the exact `postgres:18.6-alpine` image; patch/tag updates require PostgreSQL startup, Flyway and full Maven verification.
- OpenSpec CI uses 1.13.0. CLI and generated-skill upgrades are reviewed together and require strict validation of all specs.
- Container images use reviewed exact minor tags. Digest pinning is deferred until platform selection and image-update automation are defined.

Every platform, framework, build-tool or test-container update must run `mvnw.cmd clean verify`, strict OpenSpec validation and OpenSpec doctor before acceptance. A successful dependency cache never substitutes for these checks.

## Provider candidates

Helius, Bitquery, DexScreener and GoPlus are Target MVP candidates, not approved integrations. Alchemy, Moralis, Chainstack, Birdeye, Arkham, Jupiter and Jito are deferred candidates. Current limits, coverage and terms must be verified before selection or purchase.

## References

- [Operating contract](OPERATIONS.md)
- [Testing strategy](TESTING.md)
- [Spring Boot 4.1.1 requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Modulith](https://spring.io/projects/spring-modulith/)
