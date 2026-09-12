## 1. Align active documentation

- [ ] 1.1 Reclassify `docs/ROADMAP.md` and `docs/TECH_STACK.md` into current baseline, target MVP and deferred statements; document proportional activation gates and verify Redis, Caffeine, external observability, providers and partitioning are not presented as installed baseline components.
- [ ] 1.2 Correct the Roadmap package root to `io.cryptoresearch`, split completed bootstrap from target Phase 1, place idempotent storage before ingestion, and verify every persistence change has idempotency as an acceptance criterion.
- [ ] 1.3 Replace the transient README change reference with durable active-change, completed-bootstrap and next-change navigation; verify every resulting relative Markdown link resolves.
- [ ] 1.4 Move the v5 architecture mapping to a clearly non-normative archive document, update active Glossary architecture terminology, and verify legacy module names and old entities are archived or locally marked historical/deferred.

## 2. Enforce Java 25 public API compatibility

- [ ] 2.1 Make API source discovery path-separator independent, require exactly the eight expected module `api` roots, keep the marker scan as an additional check, and verify the repository convention test is non-vacuous on normalized `Path` components.
- [ ] 2.2 Add one Java compiler task for all public API sources using `--release 25`, `-proc:none`, the test classpath and an `@TempDir` output without `--enable-preview`; verify failures report source, line, column, diagnostic code and message.
- [ ] 2.3 Add positive and negative synthetic harness tests and verify a stable record compiles while a public `StructuredTaskScope` signature fails for a preview-related diagnostic.

## 3. Harden the dependency baseline

- [ ] 3.1 Remove the unused validation starter, add `javax.persistence:*` and `org.hibernate:*` to Maven Enforcer while retaining `searchTransitive=true`, and verify existing Jakarta, modern Hibernate, reactive, Vert.x and Lombok exclusions remain intact.
- [ ] 3.2 Run `mvnw.cmd dependency:tree` and verify the resolved tree contains no JPA, Hibernate ORM, WebFlux, Reactor, R2DBC, Vert.x, Lombok or validation starter dependency.

## 4. Verify and finalize

- [ ] 4.1 Run `mvnw.cmd -DskipITs clean verify` and verify all unit, architecture, documentation and public API compatibility tests pass and the executable JAR is created.
- [ ] 4.2 With Docker available, run `mvnw.cmd clean verify` and verify Maven Enforcer, Spring context, Modulith boundaries, PostgreSQL Testcontainers, Flyway and Actuator health all pass.
- [ ] 4.3 Run `openspec validate --all --strict --no-interactive` and `openspec doctor`; verify both complete successfully with no failed specifications or unhealthy references.
- [ ] 4.4 Verify the implementation against proposal, specs, design and tasks; sync both delta specs to main specs and archive `harden-bootstrap-guardrails` only after tasks 4.1-4.3 pass.
- [ ] 4.5 Before requesting any staging permission, report `git status --short`, `git diff --stat`, `git diff --cached --stat` and `rg --files .agents`; do not run `git add`, commit or push as part of this change.
