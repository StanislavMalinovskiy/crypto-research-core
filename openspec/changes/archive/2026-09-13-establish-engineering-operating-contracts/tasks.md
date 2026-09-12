## 1. Establish authoritative operating documentation

- [x] 1.1 Create `docs/OPERATIONS.md` covering external PostgreSQL deployment, configuration and secrets, liveness/readiness, logging/correlation/cardinality and bounded resource policy; verify every deferred numeric or infrastructure decision is explicitly identified and no future implementation class is prescribed.
- [x] 1.2 Create `docs/TESTING.md` covering unit, module, PostgreSQL Testcontainers, provider-contract, architecture and startup test levels; verify provider fixtures and broad `@ApplicationModuleTest` adoption are required only with the capabilities that need them.
- [x] 1.3 Add the persistence decision matrix, connection-pool admission rule, cross-module SQL prohibition and links to the two new documents in `docs/ARCHITECTURE.md`; verify it remains consistent with ADR 0002 and ADR 0004.
- [x] 1.4 Link the authoritative operating/testing documents from `docs/TECH_STACK.md`, `README.md` and `AGENTS.md`; verify those files remain concise and do not duplicate the detailed policies.
- [x] 1.5 Update active baseline documentation from PostgreSQL 16/16.15 to PostgreSQL 18/18.6 while preserving historical archive evidence; verify current support and image pins are consistent.
- [x] 1.6 Pin `.github/workflows/quality-gate.yml` to `ubuntu-24.04` and update its dependency-free contract test; verify the stable job identity and all existing CI gates remain unchanged.

## 2. Make health and repository contracts executable

- [x] 2.1 Configure the Actuator liveness group with only `livenessState` and the readiness group with `readinessState` plus `db`; verify only the health endpoint family remains exposed and production component details remain hidden.
- [x] 2.2 Change the integration image to `postgres:18.6-alpine` and extend `ApplicationHealthIT` to request aggregate, liveness and readiness endpoints with test-only component visibility; verify all report `UP`, readiness contains `db`, liveness excludes it, the server reports PostgreSQL 18.6 and Flyway has no pending migrations.
- [x] 2.3 Add an isolated PostgreSQL-loss integration test with its own container, bounded polling and unconditional recovery; verify readiness becomes `DOWN`/HTTP 503 while liveness remains `UP` and the healthy smoke test is unaffected.
- [x] 2.4 Extend dependency-free repository convention tests to verify the operating/testing documents and persistence matrix are present and linked; verify the test does not introduce a new library or inspect future business code.

## 3. Verify the implemented contracts

- [x] 3.1 Run `mvnw.cmd -DskipITs clean verify`; verify unit, architecture and repository convention tests pass and the executable JAR is created.
- [x] 3.2 With Docker available, run `mvnw.cmd clean verify`; verify the exact PostgreSQL 18.6 container, Flyway startup and aggregate, liveness and readiness integration assertions pass.
- [x] 3.3 Run `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`; verify specs, references and whitespace are healthy.
- [x] 3.4 Review proposal, specs, design, documentation, configuration and tests for scope drift, report Git inventory without staging/commit/push, reach OpenSpec `all_done`, and leave verify/sync/archive to separate post-apply workflows.
