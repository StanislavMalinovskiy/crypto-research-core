## 1. Record the six-module architecture

- [x] 1.1 Add a superseding ADR for the six-module MVP topology, update ADR 0001 and the ADR index to point to it, and verify the modular-monolith deployment decision remains accepted while the old eight-module count is clearly historical.
- [x] 1.2 Update `docs/ARCHITECTURE.md` with the exact six-module responsibility/DAG table, execution exclusion, price ownership, replay ownership and immutable signal-snapshot rule; verify `evaluation` has no dependency on `risk::api` or `wallet::api`.
- [x] 1.3 Update `docs/PROJECT_SUMMARY.md`, `docs/ROADMAP.md`, `docs/GLOSSARY.md`, `README.md`, `AGENTS.md` and `openspec/config.yaml` to the six-module vocabulary and fixture-first next-change sequence; verify active guidance does not present governance, strategy, measurement or research as current modules and no archived OpenSpec file changes.
- [x] 1.4 Replace the eight module pages with exactly `kernel`, `marketdata`, `risk`, `wallet`, `signal` and `evaluation` plus their index; verify each page covers responsibility, owned data, public API, dependencies, events, invariants, non-goals and tests, with price/replay ownership stated only in its owning module.

## 2. Simplify the executable Modulith skeleton

- [x] 2.1 Move the empty `strategy` descriptor/API packages to `signal`, move the empty `measurement` descriptor/API packages to `evaluation`, and remove the empty `governance` and `research` descriptor/API packages; verify exactly six module roots and six `api` roots remain and no business Java type is introduced.
- [x] 2.2 Set each of the six `@ApplicationModule.allowedDependencies` declarations to the approved DAG and keep every `api` package as a named interface; verify all cross-module allow-list entries use `module::api` and `kernel` depends on none.
- [x] 2.3 Update `CryptoResearchApplicationTests` to assert exactly six discovered names and the exact six-entry dependency map while retaining `ApplicationModules.verify()`; verify removed or unexpected top-level modules cannot satisfy the assertions.
- [x] 2.4 Update repository convention tests to require the exact six public API roots and current module vocabulary in active architecture/navigation files while excluding historical archives from current-topology assertions; verify Markdown link/export hygiene still covers all tracked documentation.

## 3. Verify and hand off the architectural change

- [x] 3.1 Run `mvnw.cmd -DskipITs clean verify`; verify Spring Modulith discovery, exact dependency-map, exact API-root, documentation and stable-Java tests pass and one executable JAR is produced.
- [x] 3.2 With Docker available, run `mvnw.cmd clean verify`; verify PostgreSQL 18.6, Flyway, aggregate health, liveness, readiness and database-loss integration tests remain green after the topology change.
- [x] 3.3 Run `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`; verify all active specs and references are valid and active-path searches find no removed module presented as current.
- [x] 3.4 Review proposal, specs, design, ADRs, active documentation, descriptors and tests for scope drift; report Git inventory, reach OpenSpec `all_done`, and leave verify/sync/archive to separate post-apply workflows without implementing public APIs, data or business behavior.
