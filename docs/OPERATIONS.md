# Operating contract

This document owns repository-wide runtime and operational rules. Product sequencing belongs to [Roadmap](ROADMAP.md), structural decisions to [Architecture](ARCHITECTURE.md), and detailed test selection to [Testing](TESTING.md).

## Runtime topology

- The current deployable is one application JAR connected to one external PostgreSQL 18 database instance.
- The root `compose.yaml` provides a development-only Docker Compose lifecycle for the external PostgreSQL 18.6 process; it does not run or package the application.
- Each workstation owns an independent Compose-managed named volume. That volume persists across ordinary container recreation, does not synchronize between workstations and is deleted only by an explicitly destructive reset.
- Docker is required for the local Compose lifecycle and for Testcontainers in local verification and CI. Testcontainers remains isolated from the persistent developer volume and is not a production-database requirement.
- A production database should be a separately operated managed or dedicated PostgreSQL service. Application and database lifecycle, storage and backups must not be coupled accidentally.
- Selection and operation of shared managed PostgreSQL belongs to a separate Stage 3 decision; local Compose neither synchronizes research data nor substitutes for that service.
- Production Docker Compose, Kubernetes, application container images, backup/restore procedures and multi-region deployment remain deferred until a deployment change defines them.

## Configuration and secrets

- Secrets enter through environment injection or a deployment secret store. They never receive committed production defaults.
- Provider keys, database credentials and tokens must not appear in Git, logs, health details or exception messages.
- Local defaults are allowed only for non-secret development configuration. The existing local database credentials are development conveniences, not production values.
- `.env.example` contains development-only Compose defaults. Developer-local `.env` variants remain untracked; when their port or credentials differ, the matching `CRYPTO_RESEARCH_DB_URL`, `CRYPTO_RESEARCH_DB_USERNAME` and `CRYPTO_RESEARCH_DB_PASSWORD` values must be supplied to the host application.
- PostgreSQL image initialization variables affect only an empty volume. Changing a local environment file does not rewrite credentials in an existing cluster; alter the database explicitly or use the documented destructive reset.
- New provider or production-only settings use typed configuration and fail fast when mandatory values are absent.
- Bean Validation or another validation dependency is added only with the real configuration or input contract that uses it; empty configuration classes are forbidden.

## Health semantics

| Endpoint | Meaning | Members |
|---|---|---|
| `/actuator/health` | Aggregate diagnostic health | All registered health contributors |
| `/actuator/health/liveness` | Whether the application process can continue | `livenessState` only |
| `/actuator/health/readiness` | Whether the instance can accept useful work | `readinessState`, `db` |

PostgreSQL is mandatory, so database loss makes readiness `DOWN` while liveness remains independent. Flyway failure prevents successful startup. External providers do not participate in liveness or readiness unless a later approved change demonstrates that an instance must be removed from service when that provider is unavailable.

Only the Actuator health endpoint family is exposed by default. Production responses do not expose component details. Tests may enable component visibility to verify group membership.

## Resource budgets

- Virtual threads reduce the cost of blocking; they do not provide admission control.
- The JDBC connection pool is the upper boundary for concurrent database work. Callers must not create more effective database concurrency than the pool can sustain.
- Provider concurrency, database concurrency, queues, batches, timeouts and retries are bounded by the change that introduces the workload.
- Exact pool sizes, connection/statement/transaction timeouts, batch sizes, retry counts and rate limits remain deferred until the first measured workload provides evidence.
- CPU-bound work uses bounded platform-thread executors.

## Recorded first-slice runtime boundary

The implemented replay, signal and evaluation APIs are synchronous in-process boundaries. Recorded replay commits each raw observation before a separate normalization transaction; no provider I/O occurs in either transaction. Signal candidate recording and completion are separate short `signal` transactions around an out-of-transaction risk assessment. Evaluation reads signal and market projections before atomically writing its own run, outcome and report.

This slice has no HTTP/CLI/scheduled trigger, live provider connection or production data bootstrap. Its repository fixture is test data only. Running the Maven integration suite creates an isolated Testcontainers PostgreSQL instance and does not read, mutate or require the Compose-managed developer volume.

## Logging and telemetry

- Use the Spring Boot and SLF4J baseline; no exporter is currently selected.
- Never log secrets or complete sensitive provider payloads. Redact credentials before constructing log or exception messages.
- Where applicable, correlation fields identify workload, research run, provider, chain and operation.
- Wallet addresses, token addresses and transaction hashes may be diagnostic fields but must not be metric tags because their cardinality is unbounded.
- A worker change defines its actionable latency, saturation, queue-depth, retry and failure measurements. Prometheus, OpenTelemetry and external logging infrastructure require a separate operational change.

## Verification

Operational changes must pass the Maven and OpenSpec commands in [AGENTS.md](../AGENTS.md). PostgreSQL behavior is verified against the exact Testcontainers image documented in [Tech Stack](TECH_STACK.md).
