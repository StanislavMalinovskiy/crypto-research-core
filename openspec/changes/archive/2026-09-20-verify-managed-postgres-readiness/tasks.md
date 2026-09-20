## 1. Safe Baseline

- [x] 1.1 Confirm `config/application-managed-secrets.properties` is ignored using `git check-ignore -v` and confirm it is absent from tracked/status output without opening, printing, copying or parsing the file.
- [x] 1.2 Record that no behavioral red is required because this change adds no behavior, code, schema or test contract; verify the existing accepted `database-bootstrap` and `operational-health` specs cover managed startup, Flyway and readiness.
- [x] 1.3 With `SPRING_PROFILES_ACTIVE` cleared, run `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1` and `mvnw.cmd clean verify`; verify all automated PostgreSQL work uses Testcontainers and the packaged JAR is produced.

## 2. Managed PostgreSQL Smoke

- [x] 2.1 Start the packaged JAR from the repository root with only the `managed` profile and a dedicated local HTTP port, without placing any connection value in the command; verify startup completes and sanitized startup evidence confirms Flyway validation/application rather than a local-database fallback.
- [x] 2.2 Poll `/actuator/health/readiness` with a finite timeout, verify the HTTP response reports `status=UP`, and stop the application process in guaranteed cleanup; do not publish raw logs, connection metadata or secret values.
- [x] 2.3 Verify the smoke run did not invoke a provider or load business observations, and hand off only the sanitized result, command shape, timestamp, Flyway outcome and readiness outcome to Control.

## 3. Operator Evidence

- [x] 3.1 Obtain a dated non-secret operator confirmation that the shared server is PostgreSQL 18.6, TLS is enabled and the shared identity has the required current Flyway and application privileges; network-access policy is outside this change and is not an acceptance criterion.
- [x] 3.2 Obtain a dated non-secret operator confirmation of the automatic backup mechanism and retention policy plus one successful restore into a disposable target with an integrity check; verify no live database was overwritten and no host, credential or certificate path is committed.

## 4. Control Acceptance and Closure

- [x] 4.1 Control reviews the stable diff and both evidence sets, then updates `docs/OPERATIONS.md` with a dated sanitized Stage 3.0 acceptance record; verify no secret, host, username or environment-specific certificate path is present.
- [x] 4.2 Control updates `docs/DELIVERY_PLAN.md` only after every prior task is complete: mark 3.0 `Done`, 3.1 `Current`, refresh the current/next narrative, and verify provider selection remains a separate future change.
- [x] 4.3 Run `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, `git diff --check` and `git diff --cached --check`; verify every command passes and the managed secrets file was not consumed by automated tests.
- [x] 4.4 Verify OpenSpec reports all tasks complete, archive `verify-managed-postgres-readiness`, and rerun strict validation and doctor without committing or pushing unless the user separately requests it.
