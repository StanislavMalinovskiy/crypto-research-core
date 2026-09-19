## Context

See [proposal.md](proposal.md) for motivation. The default configuration embeds development-only Compose fallbacks and accepts overrides through environment variables. There is no explicit fail-closed path for a persistent external database. The solution must remain one Spring Boot JAR with one PostgreSQL database, add no dependency, leave Testcontainers isolated, and never copy the disclosed credential into a tracked repository file or command. The operator has explicitly chosen one shared database identity for application access and Flyway at this stage.

## Goals / Non-Goals

**Goals:**

- Give every workstation one obvious ignored file to populate.
- Make selection of the external database explicit and fail closed.
- Configure the datasource once and let Flyway use the same connection identity.
- Preserve the zero-configuration local Compose path.
- Keep secrets outside Git, the JAR, logs and automated tests.

**Non-Goals:**

- Provisioning roles, grants, TLS certificates, firewall rules, backups or the PostgreSQL server.
- Testing connectivity to the user's shared database from Maven or CI.
- Adding a deployment mechanism, secret manager, provider integration, schema migration or production dependency.
- Declaring Stage 3.0 complete; backup/restore and network controls still need independent operational evidence.

## Decisions

### Use an explicit `managed` Spring profile

Add `application-managed.yaml` and require the operator to activate `managed`. The profile imports a fixed external file and binds datasource and Flyway settings without development fallbacks. Missing imports or unresolved mandatory values therefore stop startup instead of accidentally connecting to local Compose.

The default `application.yaml` remains unchanged for ordinary local development and Testcontainers. A globally optional import was rejected because a typo or missing file could silently leave the application on development defaults.

### Use a native external properties file

The populated file is `config/application-managed-secrets.properties`; `.gitignore` names it explicitly. The tracked `config/application-managed-secrets.example.properties` contains only descriptive placeholders. Spring's native configuration import is used, avoiding a dotenv parser or another production dependency.

The imported file supplies only `CRYPTO_RESEARCH_MANAGED_DB_URL`, `CRYPTO_RESEARCH_MANAGED_DB_USERNAME` and `CRYPTO_RESEARCH_MANAGED_DB_PASSWORD`. The profile maps them to the datasource, and Flyway uses that datasource through Spring Boot's normal configuration. The tracked example defines those three fields. The populated ignored file is operator-owned: implementation and tests never read, rewrite or expose it.

An ordinary `.env` file was rejected because Spring Boot does not natively treat it as process environment and Compose already gives `.env` a different local-database responsibility. Putting `spring.datasource.*` directly in an unprofiled file was rejected because it makes accidental external connection easier.

### Use one database identity for datasource and Flyway

The datasource and Flyway use one configured database identity. This avoids duplicated settings and extra role administration while the project remains an operator-run research system. Flyway remains the sole application-managed DDL path, but the shared identity necessarily retains the permissions needed to apply migrations. Splitting migration and runtime roles was rejected for this stage as unnecessary operational complexity and may be introduced later through a separate approved change.

No module owns this configuration; it belongs to the application composition root. Transaction boundaries, module-owned SQL and migrations are unchanged.

### Keep TLS policy explicit in the JDBC URL

The example requires an explicit `sslmode` in the JDBC URL and recommends certificate-verifying `verify-full`. `require` may be used only as a documented transitional setting when the server has not supplied a trusted CA path; it encrypts transport but does not verify server identity. Certificate distribution is outside this change.

### Verify configuration without touching shared data

A configuration-focused test establishes that a complete synthetic three-value secrets file under the `managed` profile configures the datasource while Flyway inherits the same connection, and that missing required configuration fails rather than using local defaults. Existing PostgreSQL integration tests continue to use Testcontainers. Repository checks verify the real filename is ignored and the example contains no real connection values.

Because this changes startup behavior for an explicit profile, Developer records a valid behavioral red for the managed-profile configuration test before implementing the profile. The complete repository gate remains mandatory.

## Risks / Trade-offs

- [A copied secret file remains plaintext on each workstation] -> Restrict local filesystem access, never sync it through Git, and migrate to a deployment secret store when application hosting is introduced.
- [Activating the wrong profile can target the wrong database] -> Require explicit `managed` activation, use unmistakable variable names, and document a connection-identity check before any data work.
- [The running application identity has the DDL authority required by Flyway] -> Accept this for the current operator-run research stage, keep credentials local and revisit role separation before broader or unattended deployment.
- [TLS `require` does not authenticate the server] -> Prefer `verify-full` and track certificate setup as external operational work before production-like ingestion.
- [The external server may not satisfy PostgreSQL 18, backup or firewall requirements] -> Do not treat successful application startup as completion of Stage 3.0; verify those controls separately.

## Migration Plan

1. Add the tracked example, explicit ignore rule and blank ignored local file without any real values.
2. Add the `managed` profile and tests using synthetic credentials only.
3. Update operations, testing and startup documentation.
4. The operator rotates the disclosed password, keeps the one connection identity in the ignored file, removes obsolete Flyway-specific entries if present, and starts with the explicit profile.
5. Verify PostgreSQL 18, Flyway state, readiness and identity without inserting provider data; separately prove backup/restore and network restrictions.

Rollback consists of stopping the application, deactivating the `managed` profile and using the unchanged local Compose workflow. Removing the ignored file does not affect tracked code or the external database.
