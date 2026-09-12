## 1. Pin build inputs

- [x] 1.1 Obtain the SHA-256 of the configured Apache Maven 3.9.16 binary ZIP from the official artifact, add `distributionSha256Sum` to the Wrapper properties, and verify a clean Wrapper invocation succeeds with checksum enforcement.
- [x] 1.2 Replace the floating PostgreSQL Testcontainers image with `postgres:16.15-alpine` and verify the focused integration test starts that exact PostgreSQL patch release and applies Flyway successfully.

## 2. Record supported versions and operations

- [x] 2.1 Add the JDK, Maven, PostgreSQL, Spring Boot, Spring Modulith, OpenSpec, BOM override and container-image upgrade policy to `docs/TECH_STACK.md`; verify every current pin and supported range matches repository configuration.
- [x] 2.2 Update `AGENTS.md` and `README.md` with the CI quality-gate contract, report location and external primary-branch protection handoff; verify the text does not claim repository files configure GitHub settings.
- [x] 2.3 Remove the obsolete `StructuredTaskScope` usage claim from `docs/ROADMAP.md`; verify the Roadmap keeps bounded-concurrency intent without naming an unapproved preview API.
- [x] 2.4 Remove the current `Venue Gate`/`venue-gate` architecture presentation from `docs/GLOSSARY.md`, retain only explicitly historical/deferred references where needed, and extend the repository legacy-term guard to reject `venue-gate` in active documentation.

## 3. Add the automated quality gate

- [x] 3.1 Create `.github/workflows/quality-gate.yml` for pull requests and pushes with read-only permissions, concurrency cancellation, a finite timeout, Java 25, Node.js 22 and Docker-capable Ubuntu execution; pin official actions to reviewed immutable SHAs with release-version comments.
- [x] 3.2 Run `./mvnw clean verify`, install and run OpenSpec 1.13.0 strict validation plus doctor, and upload Surefire/Failsafe reports under an always condition without masking earlier failures; verify the job and display name remain `quality-gate`.
- [x] 3.3 Add a dependency-free repository convention test for the durable workflow contract and verify it detects the stable job identity, Wrapper lifecycle, OpenSpec gates, report publication and absence of a direct system-Maven build command.

## 4. Verify and hand off

- [x] 4.1 Run `mvnw.cmd -DskipITs clean verify` and verify unit, architecture and repository workflow-contract tests pass and the executable JAR is created.
- [x] 4.2 With Docker available, run `mvnw.cmd clean verify` and verify the pinned PostgreSQL container, Flyway, Spring context and Actuator health pass.
- [x] 4.3 Run `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`; verify there are no failed specs, unhealthy references or whitespace errors.
- [x] 4.4 Report `git status --short`, `git diff --stat`, `git diff --cached --stat` and the exact ordered external steps—authorized checkpoint, push, first successful remote `quality-gate`, then administrator branch protection; do not stage, commit, push or claim either remote activation step is complete.
- [x] 4.5 Review proposal, specs, design, workflow and evidence for scope drift, reach OpenSpec `all_done`, and leave verify/sync/archive to their separate post-apply workflows; archive records repository readiness only, while remote CI success remains a Stage 1 exit gate after an authorized push.
