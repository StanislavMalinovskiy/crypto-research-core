## 1. Record the reproducibility architecture

- [x] 1.1 Add ADR 0009 for time, arithmetic, provenance and deterministic execution, update the ADR index, and verify it assigns lineage to `marketdata`, `signal` and `evaluation` without changing the six-module DAG.
- [x] 1.2 Add `docs/REPRODUCIBILITY.md` with the exact reproducibility definition, UTC/time-source policy, exact arithmetic rules, provenance manifest, fingerprint/version rules, deterministic ordering and module ownership; verify concrete future field scales and schemas remain deferred to owning changes.
- [x] 1.3 Update `docs/ARCHITECTURE.md`, `docs/TECH_STACK.md`, `docs/TESTING.md`, `AGENTS.md`, `README.md`, `docs/PROJECT_SUMMARY.md`, `docs/ROADMAP.md` and `docs/GLOSSARY.md` with concise links/rules; verify the detailed contract has one owner and active navigation identifies it.
- [x] 1.4 Update the six module pages where reproducibility responsibilities apply; verify `marketdata` owns input lineage/dataset fingerprints, `signal` owns decision-time definition/config evidence, `evaluation` owns run manifests/results, and no module gains a new dependency.

## 2. Add executable repository guardrails

- [x] 2.1 Add a focused `ReproducibilityConventionsTest` with a path-independent scan of production Java sources for the approved implicit wall-clock and unseeded-randomness markers; verify violations report both repository-relative source and marker.
- [x] 2.2 Add positive and negative tests for the source guard using synthetic source text; verify explicit time/reference/seed inputs pass and every configured forbidden marker is detected without adding a static-analysis dependency.
- [x] 2.3 Add a documentation discoverability assertion for the reproducibility contract and ADR; verify required time, numeric, provenance, lineage and ordering sections are present.

## 3. Verify and hand off

- [x] 3.1 Run `mvnw.cmd -DskipITs clean verify`; verify all architecture/repository tests pass and the executable JAR is produced without changing production dependencies.
- [x] 3.2 With Docker available, run `mvnw.cmd clean verify`; verify PostgreSQL 18.6, Flyway and health/readiness integration tests remain green.
- [x] 3.3 Run `openspec validate --all --strict --no-interactive`, `openspec doctor`, `git diff --check` and a resolved dependency-tree inspection; verify specifications, references, formatting and the approved dependency baseline.
- [x] 3.4 Review the proposal, specs, design, ADR, active documentation and tests for scope drift; report Git inventory, reach OpenSpec `all_done`, and leave verify/sync/archive to their separate post-apply workflows without adding domain types, tables, algorithms or infrastructure.
