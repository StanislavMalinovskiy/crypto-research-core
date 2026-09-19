## 1. Establish the portable-database contract

- [x] 1.1 Add `LocalDevelopmentDatabaseContractTest` (or an equivalently focused repository-level test) covering the exact PostgreSQL tag, PostgreSQL 18 volume target, named persistence, loopback-only port, healthcheck, development defaults, ignored local environment files and required documentation; run `.\mvnw.cmd -Dtest=LocalDevelopmentDatabaseContractTest test` and record a valid red at the expected missing-contract assertion before implementation.

## 2. Add the local PostgreSQL lifecycle

- [x] 2.1 Add root `compose.yaml` with one `postgres` service using `postgres:18.6-alpine`, `pg_isready`, configurable development values, `127.0.0.1` host binding and a named volume mounted at `/var/lib/postgresql`; verify `docker compose config --quiet` succeeds and the rendered model contains no application container, init script, bind mount or all-interface PostgreSQL publication.
- [x] 2.2 Add a tracked safe environment example and ignore developer-local environment files without hiding the example; verify the example matches application defaults and `git check-ignore .env` succeeds while `git check-ignore .env.example` does not.

## 3. Document operation and boundaries

- [x] 3.1 Add concise README commands for database start, status, application startup, routine stop and clearly labelled destructive reset, including the matching application URL override when the host port changes; verify every documented command and relative link against the implemented files.
- [x] 3.2 Update `docs/OPERATIONS.md`, `docs/TECH_STACK.md` and the affected current-work wording in `docs/DELIVERY_PLAN.md` so development Compose is current, Testcontainers remains test-only, each workstation owns independent local data, and shared managed PostgreSQL remains a separate Stage 3 decision; verify no text claims local-volume synchronization, production Compose support or a new application module.

## 4. Prove the lifecycle and complete the gate

- [x] 4.1 Run `.\mvnw.cmd -Dtest=LocalDevelopmentDatabaseContractTest test` to green, then use a fresh isolated Compose project name and non-default host port to run `docker compose up -d --wait postgres`, verify `pg_isready`, create a disposable persistence marker, run `docker compose down`, start again and verify the marker remains; clean up only that isolated project with `docker compose down --volumes` in a `finally` path and record the commands/results.
- [x] 4.2 Run `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `.\mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`; verify all checks pass, update checkboxes only from observed evidence, and hand off changed files, valid red/green evidence, Compose smoke evidence and remaining risks without adding production dependencies, schema changes or business-module code.
