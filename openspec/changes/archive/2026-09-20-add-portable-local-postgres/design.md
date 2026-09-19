## Context

See [proposal.md](proposal.md) for motivation. The application already defaults to `jdbc:postgresql://localhost:5432/crypto_research`, Flyway owns schema evolution, and integration tests use `postgres:18.6-alpine` through Testcontainers. The missing piece is a repository-owned lifecycle for a developer database that survives ordinary container replacement.

No application module owns this change. It introduces no transaction boundary, table, migration, runtime API or Java dependency. PostgreSQL remains an external process; application startup remains responsible for running Flyway.

## Goals / Non-Goals

**Goals:**

- Give every workstation the same short database start, status, stop and reset workflow.
- Preserve local data by default while making destructive deletion explicit.
- Match the exact PostgreSQL image already verified by integration tests.
- Keep the database private to the local host and keep workstation secrets untracked.
- Make the boundary between local development storage and a future shared managed database unmistakable.

**Non-Goals:**

- Operate or select a cloud database, synchronize workstation volumes, or define production backup and recovery.
- Run the application itself in Compose or change the one-JAR deployment model.
- Add database initialization SQL, duplicate Flyway, or change current schemas and data ownership.

## Decisions

### Use one root Docker Compose service for development PostgreSQL

Add a root `compose.yaml` with a single `postgres` service using the exact `postgres:18.6-alpine` tag. The application continues to run from the host through Maven, the IDE or the packaged JAR. Compose is approved as a local-development tool only; production remains a separately operated external PostgreSQL service.

Alternative considered: document a long `docker run` command. Rejected because command drift, container naming and volume attachment would differ between workstations. Alternative considered: provision a shared cloud database now. Rejected because Stage 2 uses recorded fixtures, while remote access, cost, backup and security decisions belong to the Stage 3 deployment/provider work.

### Persist PostgreSQL 18 data in a named volume

Mount a Compose-managed named volume at `/var/lib/postgresql`. The official PostgreSQL image changed its version-specific `PGDATA` and declared volume contract for PostgreSQL 18; mounting the parent path preserves the version directory and supports later major-version migration. Do not use a host bind mount or commit database files.

Ordinary `stop`, `down` and container recreation retain the named volume. Documentation labels `docker compose down --volumes` as destructive and separates it from routine commands.

Alternative considered: mount `/var/lib/postgresql/data`, the historical path for PostgreSQL 17 and below. Rejected because it is not the PostgreSQL 18 image volume contract. Alternative considered: a repository-relative data directory. Rejected because host permissions, filesystem semantics and accidental Git exposure vary across Windows, macOS and Linux.

### Keep defaults compatible and exposure local

The service initializes `crypto_research` with the existing development username and password defaults, publishes the configurable host port on `127.0.0.1` only, and uses `pg_isready` for container health. A tracked `.env.example` shows only non-production development values; `.env` and agreed local variants are ignored.

The documentation states that PostgreSQL image initialization variables apply only to an empty volume. Changing credentials in `.env` does not rewrite an existing database; the developer must alter the database deliberately or perform the explicitly destructive local reset. If the host port changes, the application connection URL must be overridden consistently.

Alternative considered: publish on all interfaces for convenience. Rejected because it exposes a development database beyond the workstation. Alternative considered: hard-code a nonstandard port. Rejected because the existing application default is already `5432`; an override handles collisions without changing the common path.

### Preserve Flyway and Testcontainers ownership

Compose creates only the PostgreSQL cluster, development role and database. It mounts no initialization scripts and creates no application schema. Flyway remains the only schema owner when the application starts. Testcontainers remains the isolated database mechanism for Maven tests, so verification never depends on or mutates a developer's persistent volume.

Repository-level tests first assert the intended Compose, ignore and documentation contract. Developer verification then validates the rendered Compose configuration and performs a smoke start under an isolated Compose project name and port, cleaning up only that isolated verification volume in a `finally` path. The complete Maven and OpenSpec gates remain unchanged.

Alternative considered: run normal tests against the persistent developer database. Rejected because test order and prior workstation state would make results non-reproducible and could damage developer data.

## Risks / Trade-offs

- [A developer assumes local volumes synchronize between computers] → Documentation explicitly says each Docker daemon owns independent data and that shared research storage is a later managed service.
- [A changed `.env` password appears ineffective] → Document first-initialization semantics and the explicit update/reset choices.
- [Port 5432 is already occupied] → Provide a port override and require the matching application URL override.
- [A reset command deletes valuable local research data] → Keep reset separate from routine commands, label it destructive and never invoke it in the default verification project.
- [The PostgreSQL patch image changes] → Keep the exact tag aligned with `docs/TECH_STACK.md` and Testcontainers through a later reviewed upgrade.

## Migration Plan

1. Add a failing repository-contract test derived from the delta requirements.
2. Add `compose.yaml`, safe environment example and ignore rules; make the targeted test pass.
3. Update README, Operations and Tech Stack to document local lifecycle, persistence, isolation and the future managed-database boundary.
4. Validate the rendered Compose model, then smoke-test it with an isolated Compose project name and non-default port; remove only that verification project's containers and volume.
5. Run the complete repository verification gate. Existing manually created local databases are not migrated automatically; developers may keep using external configuration or opt into the Compose lifecycle.

Rollback removes the development Compose files and documentation. It does not remove an existing named volume automatically; a developer deletes that local data only through an explicit Docker volume operation.
