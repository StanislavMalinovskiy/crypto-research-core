# Testing strategy

Tests prove the smallest meaningful contract at the cheapest suitable level. They follow module ownership and never bypass a public module boundary merely to make setup easier.

## Test levels

| Level | Use for | Infrastructure |
|---|---|---|
| Unit | Immutable values, calculations and pure domain rules | JUnit only |
| Module | Application use cases and one module's Spring wiring | `@ApplicationModuleTest` when a real use case exists |
| PostgreSQL integration | SQL, repositories, Flyway, locking and PostgreSQL-specific behavior | Testcontainers PostgreSQL |
| Provider contract | Parsing and normalization against preserved provider responses | Introduced with the first approved provider |
| Architecture | Module DAG, named APIs, dependency and repository conventions | Spring Modulith and dependency-free repository tests |
| Startup smoke | Application, PostgreSQL, Flyway and Actuator wiring | One small `@SpringBootTest` plus Testcontainers |

Do not make pure domain tests start Spring or Docker. Do not replace PostgreSQL-specific tests with H2 or repository mocks. Broad module-test scaffolding and provider fixtures are deferred until the corresponding use cases and provider contracts exist.

## Kernel identity tests

- Construct identity values as pure unit tests without Spring or Docker.
- Cover exact customary CAIP-2 networks, including representative Solana/EVM and unknown valid identifiers, reference case sensitivity, exact opaque-value preservation, explicit null-chain rejection and invalid whitespace/control characters.
- Prove that identical local values on different chains and event locators under different transactions remain distinct keys.
- Prove deterministic same-category sorting, including numeric `BlockPosition` ordering.
- Keep chain-specific base58/checksum parsing in future adapter contract tests rather than kernel tests.

## Database and health isolation

- Each destructive database-availability scenario owns an isolated container and application context.
- `ManagedPostgresConfigurationTest` uses a synthetic `.invalid` endpoint and a temporary three-value secrets file to verify explicit profile selection, one datasource identity inherited by Flyway without separate credentials, fail-closed missing settings, TLS enforcement and unchanged local defaults without opening a database connection.
- Maven and CI do not activate the `managed` profile and never read or connect through `config/application-managed-secrets.properties`; all PostgreSQL behavior remains isolated in Testcontainers.
- Negative readiness checks restore paused infrastructure in a `finally` block and use bounded polling rather than an unbounded wait.
- The healthy startup smoke test remains independent from failure-path tests.
- PostgreSQL integration tests assert the exact supported server version and Flyway state when version-specific behavior matters. Market-data batch tests cross the 1,000-row chunk boundary with 1,001 members and prove canonical completeness, idempotent retry, duplicate absence and rollback when the second chunk fails.
- Raw market-data storage tests assert exact CAIP-2 network validation, physical column names, absence of ambiguous legacy names, unpartitioned table shape, primary-key order and explicit `C` collation, absence of surrogate/secondary indexes, check constraints, synthetic EVM persistence, cross-network/provider/case distinction, exact readback, immutable retry semantics and concurrent uniqueness behavior against PostgreSQL rather than a repository mock.
- Storage concurrency tests invoke the transactional application boundary from separate threads so each submission owns a real database transaction.

## Maven lifecycle

- Surefire runs unit, architecture and repository convention tests.
- Failsafe runs `*IT` integration tests, including PostgreSQL startup and health behavior.
- `mvnw.cmd -DskipITs clean verify` is the fast local structural gate.
- `mvnw.cmd clean verify` is the complete gate and requires Docker.
- CI publishes Surefire and Failsafe reports even when verification fails.
- GitHub Actions uses the stable `quality-gate` job and the same required local contract. Repository files cannot enable branch protection; after an authorized push and first successful remote run, an administrator must require `quality-gate` in the primary-branch ruleset.
- Every architecture-affecting change keeps `ApplicationModules.verify()` green.

### Windows Codex Docker access

Codex's restricted Windows sandbox cannot open Docker Desktop named pipes even when Docker is healthy. Every
Docker/Testcontainers command must therefore run with escalated host access. Run `docker version` first in the
same escalated context, then run the targeted integration command or complete Maven gate there. Do not diagnose
Docker as unavailable from an in-sandbox `permission denied`, `docker_engine is not listening`, or discovery
timeout: retry once outside the restricted sandbox. Only a failed same-context escalated `docker version` is a
Docker availability blocker. The context retry is infrastructure handling, not an artifact repair.

Every change adds tests with its behavior. A test checkbox is complete only after the relevant command has actually passed; an unavailable Docker engine must be reported rather than hidden.

## Agent-assisted development

Review correctness, point-in-time integrity, idempotency and measurement bias before style. Verify transaction ownership, failure behavior, migration safety and query-backed indexes. Reject hidden cross-module coupling, shared persistence models and bypasses of public APIs; reject future-data leakage, silent outcome loss, fabricated provider fallback data and unbounded concurrency. New dependencies and architectural boundary changes are blocking unless OpenSpec and an ADR explicitly approve them. Require reproducible tests and concrete verification output; comments and naming must be in English.

Select verification by actual dependencies and effects across documentation, research tools, Java, configuration, database and integration. Document-only changes use facts, consistency, links and requirements, without executable RED or automatic Maven; isolated research tools use useful tool tests and source/receipt/budget checks. Isolation depends on dependencies/effects, not filename or extension. Mixed scope uses the union.

The complete gate is required before every commit affecting Java including Java tests, build configuration, database/migrations, dependencies or shared runtime launch/operations instructions. Its independent integrity preflight precedes `mvnw.cmd clean verify`, followed by strict all-item OpenSpec validation, doctor and diff checks. The selected workflow owns execution; Builder targeted checks never substitute for a required complete gate. CI retains its full Maven/integrity lifecycle. Later changes to checked inputs require affected checks again. Record exact selected checks and reasons; unrun, failed, skipped or missing checks are not PASS.

DEFAULT retains its Control/Developer sessions and existing requirement-derived behavioral red/freeze contract under [its workflow](AGENT_WORKFLOW.md). In MULTIAGENT, Main classifies ordinary work against the [single trigger authority](AGENT_WORKFLOW_MULTIAGENT.md#architect-planning-and-risk); Architect participates for architecture, material ambiguity or critical guarantees. Assess concrete changed guarantees and consequences, not words or paths.

Executable critical-guarantee changes and executable bugfixes use applicable meaningful regression RED_REQUIRED. Other CONTRACT work records a concrete test-mode reason; other NORMAL work uses useful appropriate tests in the existing handoff. Documentation-only work, including critical-guarantee documentation, uses RED_NOT_REQUIRED and independent review for substantive research-rule changes. No artificial executable failure or process-only diff is required.

Before freeze, verify each failing test's requirement/acceptance-criterion, named assertion, expected and actual in RED evidence; repeat in BUILD_DONE. Fix a wrong target, wrong assertion or setup error and rerun RED without reviewer permission. Compilation, discovery, configuration, startup, Docker or other infrastructure failures are not RED. After valid RED, semantic freeze protects establishing test meaning, not implementation helpers that share a source file. Voluntary valid RED invokes the same freeze.

After freeze, do not weaken, disable, skip, narrow, retag, relocate or regenerate establishing tests, expectations, fixtures, snapshots, discovery or runtime settings, fit truth to implementation, change acceptance criteria or accepted raw evidence, or add production behavior for a test artifact. A proven synthetic fixture/setup contradiction with an accepted requirement may be corrected without prior owner or Reviewer permission. Preserve exact accepted-source conflict, correction and rerun evidence, report `tests_changed_after_red`, and establish applicable new meaningful behavioral RED for an establishing-test change. Subsequent independent review must verify the correction preserves intended behavior. An unproven error or requirement ambiguity receives independent technical diagnosis (`TEST_SPEC_ERROR`); a contract defect reopens Architect PLAN. RED/freeze hashes and process-only pre-implementation snapshots are unnecessary; stable review diffs, archive/checkpoint/recovery hashes and integrity guards remain.

Critical-guarantee review uses a fresh Reviewer independent of author, including Main/Architect. Ordinary NORMAL/CONTRACT uses appropriate existing review, with same-thread Architect review only when it participated and is eligible. Review affected/doubtful CI and substantive assumptions against controlling sources; answer “Какое предположение реализации или контракта может быть неверным?”. Distinguish blocking defects from optional improvements. Main reruns RED only when `red_suspect = true`. Same-author repairs receive review; there is no numeric repair stop. Cumulative time/resources persist across replanning/renaming/session replacement; repeated defects, stalled progress or resource exhaustion trigger independent diagnosis of cause, approach, remaining budget and next result.

Read [the closure procedure](agents/close-archive.md) only before approved DOCS_CLOSE, a required complete final gate, archive, recovery or post-archive work. NORMAL uses only applicable integration gate guidance, without DOCS_CLOSE/archive; TRIVIAL does not load it. CONTRACT/CORE_RISK retain Architect checkbox closure, Main's checkpoint, CLI archive, exact mutation inspection, post-checks and scoped restoration. Builder edits documentation/instructions only when explicitly included in its bounded owner-authorized paths, never task checkboxes. DEFAULT keeps its own closure ownership. Accepted specs may differ from proposed behavior before archive; the active delta records that migration.

## Reproducibility tests

- Time-dependent rules use fixed or controlled clocks/reference instants; tests never depend on the current machine time.
- Exact arithmetic tests assert precision, scale and rounding at lossy boundaries.
- Determinism tests permute equivalent input and completion order and expect identical ordered domain results.
- Randomized research tests inject and record a seed, then reproduce the same result with it.
- Dataset lineage tests prove identical canonical inputs reproduce a fingerprint and changed input or transformation versions do not.
- Evaluation replay tests assert the complete result and provenance contract from [Research reproducibility](REPRODUCIBILITY.md).

## First recorded signal slice

`FirstSignalEvaluationIT` uses an isolated `postgres:18.6-alpine` Testcontainers database and the repository fixture at `src/test/resources/fixtures/first-signal-evaluation.json`. The legacy cases exercise V1–V4 public behavior through the forward-migrated schema: raw-first replay, dataset finalization, risk gating, immutable `LIQUIDITY_SPIKE`, exact `1h` valuation and deterministic report publication. The contract covers equal retry, immutable conflict, parse failure, cutoff isolation, input permutation, priced and unpriced outcomes, complete provenance, exact rows/scales/indexes and the absence of a risk schema. It never connects to the persistent developer Compose volume.

Pure tests own the risk matrix, signal threshold boundaries, strict post-decision/pre-horizon entry, horizon selection and all three friction tiers. PostgreSQL regressions also prove that sub-microsecond source, decision and evaluation cutoffs normalize consistently before retry comparison, durable identity and published provenance. Fixture validation rejects missing, extra, reordered-semantic or inexact numeric content before the integration scenario is constructed. This fixture proves architecture and determinism only; it is not provider fallback data or evidence of statistical edge.

## Versioned market evidence and compatibility

`LegacyMarketEvidenceV9IT` freezes literal populated V1–V9 durable values with Flyway stopped at V9: snapshot membership/fingerprint, accepted signal, priced and unpriced run/outcome IDs, ordered report content and fingerprints. `VersionedMarketMigrationIT` migrates that populated fixture forward and compares the same exact values. This is an explicitly legacy compatibility oracle, separate from the versioned chronological behavior. No expected value is regenerated by the v2 implementation.

`FirstSignalEvaluationIT` also exercises the versioned public route: a decision snapshot frozen before later price facts, an accepted signal pinned to it, a distinct evaluation snapshot, and exact `1h` priced arithmetic. `MarketFactStorageIT` verifies two valid revisions of one canonical fact and rejects same-revision content conflict. `VersionedSnapshotStorageIT` covers independent scope completeness, justified exclusions, fingerprint sensitivity, concurrent/equal retry including a forced PostgreSQL serialization failure, 1,001-member chunks, rollback, late commit/backfill isolation, the 10,001st visible key and exact 20,000/20,001 visible-revision boundaries. `VersionedSignalEvidenceIT` checks stable accepted decision/source revisions after future facts. `VersionedEvaluationStorageIT` checks the two required run fingerprints, exact selected revisions, priced and unpriced outcomes, atomic retry/conflict and report manifests. PostgreSQL tests also verify V10–V12 schema ownership, exact USD price-revision foreign keys and legacy column preservation. See [Research reproducibility](REPRODUCIBILITY.md).
