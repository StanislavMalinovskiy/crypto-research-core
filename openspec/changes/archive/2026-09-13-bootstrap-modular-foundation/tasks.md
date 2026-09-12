## 1. Repository guidance and documentation

- [x] 1.1 Organize the existing documentation under `docs/`, preserve source content, and verify all required architecture, ADR and module pages exist.
- [x] 1.2 Add root `AGENTS.md` and OpenSpec project context, then verify the agent guide is under 8 KiB and names the required reading and verification workflow.
- [x] 1.3 Verify all Markdown relative file links resolve and no tool-export-specific markers remain.
- [x] 1.4 Replace the document-priority ladder with responsibility-based authority and conflict rules; verify root guidance stays below 8 KiB.
- [x] 1.5 Publish the complete allowed/forbidden module DAG and explicit `kernel`/`governance` boundaries; verify module documentation links remain valid.
- [x] 1.6 Add accepted ADRs for table/schema ownership, transaction/event semantics and multi-instance background concurrency; verify the ADR index and Architecture summary agree.

## 2. Maven and application baseline

- [x] 2.1 Normalize the application package root to `io.cryptoresearch` and verify production compilation discovers the application entry point.
- [x] 2.2 Configure the Maven Wrapper lifecycle for Java 25 preview, one executable JAR, Failsafe integration tests and forbidden dependency enforcement; verify the effective dependency tree contains no banned stack.
- [x] 2.3 Configure synchronous PostgreSQL/Flyway defaults, virtual threads and health exposure; verify configuration remains externally overridable.

## 3. Spring Modulith boundaries

- [x] 3.1 Add explicit descriptors for all eight logical modules and verify exactly those modules are discovered.
- [x] 3.2 Add named `api` interface descriptors without placeholder business classes and verify the documented dependency graph with `ApplicationModules.verify()`.
- [x] 3.3 Restrict every cross-module edge to a named `api` interface and add a test that locks descriptor values to the documented DAG.

## 4. Database and health foundation

- [x] 4.1 Remove the no-op versioned migration and update the integration test to verify healthy Flyway initialization with zero applied versioned migrations.
- [x] 4.2 Add a PostgreSQL Testcontainers integration test that starts the application, verifies the empty Flyway baseline and confirms Actuator health reports `UP`.

## 5. Repository verification

- [x] 5.1 Add repository convention tests for Markdown hygiene, relative links and public API preview leakage; verify the focused tests pass.
- [x] 5.2 Run `openspec validate --all --strict --no-interactive`, `openspec doctor` and the generated OpenSpec verify workflow; record their actual results.
- [x] 5.3 Run `./mvnw clean verify` with Java 25 and Docker, verify the executable JAR is produced, and record any environmental blocker exactly rather than declaring completion.

## Verification notes

- Java: Corretto 25.0.4.1; Maven Wrapper: 3.9.16.
- `mvnw.cmd clean test`: passed; 4 unit/architecture/repository tests passed.
- `mvnw.cmd clean verify -DskipITs`: passed; Enforcer rules passed and the executable JAR was produced.
- `mvnw.cmd clean verify`: passed; 4 focused tests and 1 PostgreSQL Testcontainers integration test passed, Flyway validated zero versioned migrations, health reported `UP`, and the executable JAR was produced.
- Docker Desktop 26.1.4 provided the Linux engine; Testcontainers 2.0.5 ran PostgreSQL 16.15.
- `openspec validate --all --strict --no-interactive`: 1 passed, 0 failed.
- `openspec doctor`: OpenSpec root and references healthy.
- Generated `openspec-verify-change` workflow: 17/17 tasks and 15/15 requirements covered; no critical issues or warnings.
