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

The selected workflow mode does not change test levels or the complete Maven lifecycle. Fail-closed `DEFAULT` uses top-level Control and Developer sessions; manually enabled `MULTIAGENT` uses the separate supervised role protocol. Only MULTIAGENT strictly TRIVIAL uses the authorized local lightweight gate: `git diff --check` and `mvnw.cmd -Dtest=RepositoryConventionsTest test`, with both checks passing and required tests executed. This does not change CI, DEFAULT or the complete gate for NORMAL/CONTRACT/CORE_RISK.

When observable behavior changes in DEFAULT, Developer derives a meaningful test from the active requirement and runs it before implementation. Red is valid only when the named test executes and fails at the expected behavioral assertion; compilation, discovery, configuration, startup, Docker or another infrastructure failure is not red. After valid red, the establishing test must not be weakened, disabled, skipped or narrowed, and production code must not recognize a fixture, profile, known test value or other test artifact.

In DEFAULT, documentation, comments, formatting, mechanical configuration, a pure rename or internally covered refactoring may require no new behavioral test, but applicable targeted checks and the complete gate still run. In MULTIAGENT the tier/test-mode rules below govern exemptions. The independent integrity preflight remains before the complete Maven gate in both modes, and targeted developer commands never substitute for `mvnw.cmd clean verify` at non-TRIVIAL completion.

In MULTIAGENT, Architect alone assesses all TR triggers first and fixes non-TRIVIAL tier, risk, matched triggers,
invariants and test mode at PLAN_READY using the [single trigger authority](AGENT_WORKFLOW_MULTIAGENT.md#architect-planning-and-risk).
CORE_RISK and every bugfix require RED_REQUIRED. Other CONTRACT work uses Architect-selected RED_REQUIRED or
RED_NOT_REQUIRED with a concrete reason; other NORMAL work uses useful appropriate tests with the reason in
its existing test-mode handoff. A bugfix restoring accepted behavior can be NORMAL; changing accepted behavior
is CONTRACT; any TR match overrides either. RED_NOT_REQUIRED never excuses missing applicable coverage or checks.
Builder owns meaningful requirement-derived tests, required behavioral RED before implementation, semantic freeze
and targeted GREEN. Documentation-only work stays with Architect and skips Builder; CORE_RISK has no docs-only RED waiver.

- NORMAL / CONTRACT → same Architect thread.
- Any CORE_RISK change → fresh Reviewer (new thread).

CORE_RISK takes precedence. Accepted normative-spec changes without a TR trigger use the same Architect review.
NORMAL/CONTRACT review reports only touched CI; CORE_RISK reports the full CI-01..CI-15 matrix with evidence and
reasons for non-applicability. Test-only, logging and config work are not automatic low-risk exemptions.

In MULTIAGENT, tests freeze only after the RED reason is verified, not merely after a red run. For each failing test, Builder records the requirement/acceptance-criterion, expected and actual result in the RED evidence before freeze and repeats it in the BUILD_DONE handoff. For a wrong target, wrong assertion or setup error unrelated to the requirement, fix the test/setup and rerun RED before freeze without reviewer permission. This does not authorize contract changes. After freeze, the existing TEST_SPEC_ERROR rule applies unchanged.

After valid RED, semantic freeze forbids changing, weakening, disabling, skipping or narrowing establishing tests,
expectations, fixtures, snapshots, discovery or runtime behavior. A necessary establishing-test change follows
TEST_SPEC_ERROR: preserve evidence and obtain current-reviewer REPAIR with `requires_new_red = true`, then new
valid behavioral RED. No hashes or pre-implementation-diff snapshots solely for RED/freeze are required; archive,
checkpoint and recovery hashes, stable final review diffs and independent integrity guards are unchanged.
BUILD_DONE repeats each failing assertion and expected/actual and reports `tests_changed_after_red` honestly,
including authorized changes. The current reviewer verifies final tests still encode the intended behavior.
Voluntarily selected RED invokes the same freeze. Main reruns claimed RED only when the reviewer sets
`red_suspect = true`. Repairs return to the same author and retain bounded repair accounting.

In MULTIAGENT, read [the closure procedure](agents/close-archive.md) only before approved DOCS_CLOSE, the complete final gate, archive, archive recovery or post-archive work. NORMAL requires only its full-gate guidance, without DOCS_CLOSE/archive. CONTRACT/CORE_RISK retain Architect documentation ownership, Main's gate and checkpoint, CLI archive, post-checks and scoped restoration. TRIVIAL does not load that procedure. Ordinary PLAN, BUILD and REVIEW use the selected workflow and this testing contract. DEFAULT retains its independent [complete local gate](AGENT_WORKFLOW.md#complete-local-gate). Accepted specs need not match proposed behavior before archive; the active delta records that migration.

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
