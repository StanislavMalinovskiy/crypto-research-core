## Why

Development happens from multiple computers, but the repository currently provides only ephemeral Testcontainers databases and assumes that a developer has independently provisioned PostgreSQL at `localhost:5432`. A repository-owned local database lifecycle is needed so every workstation can start the same supported PostgreSQL version while preserving that workstation's data across ordinary container restarts.

## What Changes

- Add a development-only Docker Compose service for the exact supported `postgres:18.6-alpine` image, bound to host loopback and backed by a named Docker volume mounted at the PostgreSQL 18 persistence path.
- Keep the existing application connection defaults aligned with the Compose database, while allowing host port and development credentials to be overridden through uncommitted environment configuration.
- Document deterministic start, readiness, application startup, stop and explicit destructive-reset commands, including the distinction between a persistent local database and a future shared managed research database.
- Ignore developer-local environment files while providing a safe example containing development defaults only.
- Add repository-level verification for the portable database contract and exercise the Compose configuration against Docker without replacing the existing Testcontainers integration tests.

Non-goals:

- No shared cloud database, remote access, data synchronization, backup/restore service or production deployment.
- No application container image, Kubernetes configuration or containerization of the Spring Boot JAR.
- No database schema, Flyway migration, application-module, module-DAG, runtime API or production-dependency change.
- No exposure of PostgreSQL to the LAN or Internet and no committed production credentials.

Affected application modules: none. This is repository-level local development infrastructure and documentation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `database-bootstrap`: add a portable, persistent and locally isolated PostgreSQL development lifecycle that remains compatible with external runtime configuration and Flyway ownership.

## Impact

Expected implementation touches repository-root Compose/environment-ignore files, local-development documentation and repository convention tests. Docker Compose becomes an approved local-development tool only; PostgreSQL remains external to the application process, Testcontainers remains the verification database, and production database operation remains separately managed and deferred.
