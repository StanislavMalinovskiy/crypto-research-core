## 1. Align active documentation

- [x] 1.1 Reclassify `docs/ROADMAP.md` and `docs/TECH_STACK.md` into current baseline, target MVP and deferred statements; document proportional activation gates and verify Redis, Caffeine, external observability, providers and partitioning are not presented as installed baseline components.
- [x] 1.2 Correct the Roadmap package root to `io.cryptoresearch`, split completed bootstrap from target Phase 1, place idempotent storage before ingestion, and verify every persistence change has idempotency as an acceptance criterion.
- [x] 1.3 Replace the transient README change reference with durable active-change, completed-bootstrap and next-change navigation; verify every resulting relative Markdown link resolves.
- [x] 1.4 Move the v5 architecture mapping to a clearly non-normative archive document, update active Glossary architecture terminology, and verify legacy module names and old entities are archived or locally marked historical/deferred.

## 2. Establish the stable Java 25 lifecycle

- [x] 2.1 Mark ADR 0003 as superseded, add a stable Java 25 ADR, and update AGENTS, Architecture, Tech Stack and related active guidance so preview requires a future evidence-backed change.
- [x] 2.2 Remove `--enable-preview` from compiler, Surefire, Failsafe and Spring Boot run configuration; verify no production or test source currently requires preview.
- [x] 2.3 Remove the redundant compiler/marker harness, retain a path-separator-independent check for exactly eight module `api` roots, and verify the focused repository convention tests pass.

## 3. Harden the dependency baseline

- [x] 3.1 Remove the unused validation starter, add `javax.persistence:*` and `org.hibernate:*` to Maven Enforcer while retaining `searchTransitive=true`, and verify existing Jakarta, modern Hibernate, reactive, Vert.x and Lombok exclusions remain intact.
- [x] 3.2 Run `mvnw.cmd dependency:tree` and verify the resolved tree contains no JPA, Hibernate ORM, WebFlux, Reactor, R2DBC, Vert.x, Lombok, `org.springframework.boot:spring-boot-starter-validation`, `org.hibernate.validator:hibernate-validator` or `jakarta.validation:jakarta.validation-api` dependency.

## 4. Verify and finalize

- [x] 4.1 Run `mvnw.cmd -DskipITs clean verify` and verify all unit, architecture and repository convention tests pass without preview and the executable JAR is created.
- [x] 4.2 With Docker available, run `mvnw.cmd clean verify` and verify Maven Enforcer, Spring context, Modulith boundaries, PostgreSQL Testcontainers, Flyway and Actuator health all pass without preview.
- [x] 4.3 Run `openspec validate --all --strict --no-interactive` and `openspec doctor`; verify both complete successfully with no failed specifications or unhealthy references.
- [x] 4.4 Review the implementation against proposal, specs, design and tasks after tasks 4.1-4.3 pass; verify there is no scope drift and leave sync/archive for the separate post-apply workflows.
- [x] 4.5 Before requesting any staging permission, report `git status --short`, `git diff --stat`, `git diff --cached --stat` and `rg --files .agents`; do not run `git add`, commit or push as part of this change.
