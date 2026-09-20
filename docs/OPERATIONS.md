# Operating contract

This document owns repository-wide runtime and operational rules. Product sequencing belongs to [Roadmap](ROADMAP.md), structural decisions to [Architecture](ARCHITECTURE.md), and detailed test selection to [Testing](TESTING.md).

## Runtime topology

- The current deployable is one application JAR connected to one external PostgreSQL 18 database instance.
- The root `compose.yaml` provides a development-only Docker Compose lifecycle for the external PostgreSQL 18.6 process; it does not run or package the application.
- Each workstation owns an independent Compose-managed named volume. That volume persists across ordinary container recreation, does not synchronize between workstations and is deleted only by an explicitly destructive reset.
- Docker is required for the local Compose lifecycle and for Testcontainers in local verification and CI. Testcontainers remains isolated from the persistent developer volume and is not a production-database requirement.
- A production database should be a separately operated managed or dedicated PostgreSQL service. Application and database lifecycle, storage and backups must not be coupled accidentally.
- Selection and operation of shared managed PostgreSQL belongs to a separate Stage 3 decision; local Compose neither synchronizes research data nor substitutes for that service.
- Production Docker Compose, Kubernetes, application container images, repository-owned backup/restore procedures and multi-region deployment remain deferred until a deployment change defines them. Operator-managed backups of the shared managed PostgreSQL already exist and are accepted as Stage 3.0 evidence below; evolving that backup strategy with measured capacity thresholds is owned by F1.9 of the remediation plan.

## Configuration and secrets

- Deployment secrets enter through environment injection or a deployment secret store. A workstation may use the explicitly selected `managed` profile and its ignored external properties file; secrets never receive committed defaults.
- Provider keys, database credentials and tokens must not appear in Git, logs, health details or exception messages.
- Local defaults are allowed only for non-secret development configuration. The existing local database credentials are development conveniences, not production values.
- `.env.example` contains development-only Compose defaults. Developer-local `.env` variants remain untracked; when their port or credentials differ, the matching `CRYPTO_RESEARCH_DB_URL`, `CRYPTO_RESEARCH_DB_USERNAME` and `CRYPTO_RESEARCH_DB_PASSWORD` values must be supplied to the host application.
- PostgreSQL image initialization variables affect only an empty volume. Changing a local environment file does not rewrite credentials in an existing cluster; alter the database explicitly or use the documented destructive reset.
- New provider or production-only settings use typed configuration and fail fast when mandatory values are absent.
- Bean Validation or another validation dependency is added only with the real configuration or input contract that uses it; empty configuration classes are forbidden.

### Managed PostgreSQL workstation profile

- `managed` is an explicit profile. Without it, the application keeps the local Compose defaults and does not read the external secrets file.
- With `managed`, `config/application-managed-secrets.properties` is a required external import. Missing or blank mandatory settings fail startup without falling back to local development credentials.
- `config/application-managed-secrets.example.properties` is the only tracked template and contains placeholders only. The populated counterpart is explicitly ignored, remains outside the JAR and is recreated separately on each workstation.
- The ignored file defines exactly `CRYPTO_RESEARCH_MANAGED_DB_URL`, `CRYPTO_RESEARCH_MANAGED_DB_USERNAME` and `CRYPTO_RESEARCH_MANAGED_DB_PASSWORD`. The application datasource uses that identity and Flyway inherits the same datasource without separate managed settings.
- The shared identity requires both the application's runtime privileges and the schema/DDL privileges needed by Flyway. Server-side role creation and grants are operator responsibilities and should be revisited before broader or unattended deployment.
- The JDBC URL declares TLS explicitly. `sslmode=verify-full` with a trusted CA is preferred. `sslmode=require` is transitional because it encrypts traffic without authenticating the server.
- A credential disclosed in chat, logs, an issue or another external channel is rotated before use. Secret values are never pasted into agent prompts, commands intended for evidence capture, documentation or test reports.
- Rotate the shared credential on the server and update each workstation's ignored file before its next application start; obsolete Flyway-specific entries are not used and should be removed locally by the operator.
- Start the profile with `SPRING_PROFILES_ACTIVE=managed`. Successful startup confirms Flyway validation/application; `/actuator/health/readiness` must then report `UP` before useful work begins.
- Profile wiring alone does not prove the external PostgreSQL version or backup/restore viability. Network-access policy is operator-owned and is not a repository acceptance criterion.

Prepare and start a workstation without putting values in command history or agent prompts:

```powershell
Copy-Item config/application-managed-secrets.example.properties config/application-managed-secrets.properties
notepad config/application-managed-secrets.properties
$env:SPRING_PROFILES_ACTIVE = "managed"
.\mvnw.cmd spring-boot:run
```

Fill only the three named managed database values in the copied file. Do not export them as evidence, print them in diagnostics or pass them to automated tests. The fixed import is required when `managed` is active; no managed profile means the unchanged local Compose path.

### Stage 3.0 acceptance record

The managed PostgreSQL baseline was accepted on 20 September 2026 from sanitized application and operator evidence:

- At `2026-09-20T07:12:14Z`, the packaged JAR started with the explicit `managed` profile, Flyway validated the accepted V1-V4 sequence, readiness returned HTTP 200 with `UP`, and the temporary application process was stopped.
- At `2026-09-20T07:40:53Z`, the operator confirmed PostgreSQL 18.6, TLS 1.3 and the shared identity's required application plus Flyway DDL/DML ownership without changing privileges.
- Automated backups use `pg_dump` custom format with Zstandard compression, run daily at 03:15 `Asia/Tashkent` (22:15 UTC on the previous calendar day) through a systemd timer and retain the five most recent successful copies.
- A fresh backup created at `2026-09-20T07:51:06Z` restored without error into a disposable database. The restored database contained Flyway V1-V4, four `marketdata` tables, two `signal` tables and three `evaluation` tables; only the disposable database was then removed, and the live database remained available.
- At `2026-09-20T07:55:06Z`, the operator confirmed that the server OS and PostgreSQL presentation time zone were aligned to `Asia/Tashkent`, NTP was active and the change required no PostgreSQL restart. This local zone is for operator-facing display and scheduling only; authoritative application timestamps remain UTC `Instant` values persisted as `TIMESTAMPTZ(6)`.
- The populated workstation secret file remained ignored and outside automated tests and captured evidence. No endpoint, host, username, password or certificate path is recorded here.

This is dated stage-exit evidence, not continuous monitoring. Backup success and restore viability remain recurring operator responsibilities, and the operator retains responsibility for the chosen network-access policy.

The repository migration sequence now extends through V9 after the accepted F2 core-storage change. The dated managed-database evidence above intentionally remains V1–V4 because that is what was verified at the time; it must not be rewritten as later evidence. Before any F3 live-data admission, an operator-run managed startup must apply or validate V5–V9 and confirm readiness, while F3 supplies the additional forward migrations required for complete live facts.

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

This slice has no HTTP/CLI/scheduled trigger, live provider connection or production data bootstrap. Its repository fixture is test data only. Running the Maven integration suite creates an isolated Testcontainers PostgreSQL instance and does not read, mutate or require the Compose-managed developer volume or the ignored managed-profile secrets file.

## Logging and telemetry

- Use the Spring Boot and SLF4J baseline; no exporter is currently selected.
- Never log secrets or complete sensitive provider payloads. Redact credentials before constructing log or exception messages.
- Where applicable, correlation fields identify workload, research run, provider, chain and operation.
- Wallet addresses, token addresses and transaction hashes may be diagnostic fields but must not be metric tags because their cardinality is unbounded.
- A worker change defines its actionable latency, saturation, queue-depth, retry and failure measurements. Prometheus, OpenTelemetry and external logging infrastructure require a separate operational change.

## Verification

Operational changes must pass the Maven and OpenSpec commands in [AGENTS.md](../AGENTS.md). PostgreSQL behavior is verified against the exact Testcontainers image documented in [Tech Stack](TECH_STACK.md).
