## Context

See [proposal](proposal.md) for motivation and [delta](specs/recorded-market-replay/spec.md) for the complete observable contract. At planning, `RecordedMarketDataService.replay` stored raw observations, invoked the separately transactional normalizer, and returned ordered `NORMALIZED` / `NORMALIZATION_FAILED` items without telemetry instrumentation. Its normalization catch handled `IllegalArgumentException` and `NormalizationConflictException`; raw-storage and other runtime failures escaped. Actuator supplies Micrometer, and SLF4J is already available. The approved implementation preserves that behavior while adding the diagnostics below.

Controlling sources, expressed as repository-root paths so they remain valid references after archive, are `docs/DELIVERY_PLAN_FIXES.md` section F3.4, `docs/modules/marketdata.md`, `docs/ARCHITECTURE.md`, `docs/OPERATIONS.md`, `docs/TESTING.md`, `docs/REPRODUCIBILITY.md`, accepted `recorded-market-replay`, and ADRs `docs/adr/0005-transactions-and-events.md`, `docs/adr/0006-background-work-and-bounded-concurrency.md`, `docs/adr/0008-six-module-mvp-topology.md`, `docs/adr/0009-research-reproducibility.md`. Existing `FirstSignalEvaluationIT` proves raw-first behavior, failure retention and equal replay. There is no nearer module AGENTS.md.

## Goals / Non-Goals

**Goals:** observe the current recorded path with four fixed counter series and one bounded summary; make success, handled item failure and invocation abort distinguishable; preserve business behavior even if diagnostic code raises a runtime exception.

**Non-Goals:** no new replay/domain statuses or exception policy, registry/exporter configuration, global logging format, endpoint exposure, timers, durations, shared mutable per-call state, durable metrics, asynchronous work or new dependencies. The proposal's wider exclusions apply.

## Decisions

1. **Ownership and placement.** Instrument the existing `marketdata` application orchestration. Inject the Boot-provided Micrometer registry and use SLF4J structured key/value logging. New module-internal helpers are optional; no class name, constructor signature or helper decomposition is an acceptance criterion. Do not add telemetry types to `MarketDataApi`. Alternatives rejected: persistence-layer instrumentation would conflate attempts with inserts; adding a global event bus or exporter is unnecessary.
2. **Accounting.** Keep integer counts local to the invocation. Increment attempted when starting each observation, before raw construction/storage. Only an obtained existing item result increments its matching item counter. Normal completion, including handled normalization failures and empty input, increments `outcome=completed`; an escaping business runtime exception increments `outcome=aborted`. Summary fields explain the at-most-one unclassified attempted item on abort. Null dataset rejection remains before instrumentation. Process-local counters intentionally increase on equal replay and reset with the process/registry; they do not claim unique durable facts. Registering zero-valued series eagerly or lazily is immaterial; observations are evaluated as deltas with absent untouched series treated as zero.
3. **Failure isolation.** Contain runtime exceptions only at metric registration/increment and summary emission boundaries. Never swallow, wrap, classify anew or replace a business exception. Attempt the summary even after metric-sink failure; perform no retry, compensating write, fallback or recursive error log. Fatal JVM errors are outside the contract. This is best-effort telemetry, not a transaction or an exact delivery guarantee. A diagnostic sink that blocks indefinitely is outside the accepted baseline; this change introduces no queue, timeout thread or asynchronous logging configuration.
4. **Logging.** Use INFO, constant message and the exact six-field schema in the delta. Application diagnostics contain only literals and integer counts. Logger name, event time and normal framework metadata are not newly defined application fields. Structured emission does not require JSON-console configuration. No input-derived diagnostic dimensions, correlation IDs or throwable attachment are introduced.
5. **Transactions and dependencies.** Keep existing raw-store and normalization use cases and their calls intact; no transaction annotations or repository changes. No new production dependency or infrastructure is needed. Micrometer's non-authoritative count representation is allowed by Reproducibility; no financial representation changes.

## Risk classification and bounded change budget

Product risk is **STANDARD**, `risk_triggers=none`, `test_mode=RED_REQUIRED`. The work needs coordinated counter, summary and failure-path reasoning, rather than a mechanical configuration edit. Classification uses only the workflow trigger authority in `docs/AGENT_WORKFLOW_MULTIAGENT.md`, section Architect planning and risk.

| Trigger | Assessment for this bounded product change |
|---|---|
| TR-01 Persistence | No SQL, storage semantics or durable telemetry. |
| TR-02 Transactions | Existing transaction owners, call boundaries and annotations remain unchanged. |
| TR-03 Concurrency/locking | No executors, locks or new scheduling; per-call counts are local and existing registry APIs own counter mechanics. |
| TR-04 Idempotency/retry | No retry policy or durable dedup changes; metrics count invocations as diagnostics. |
| TR-05 Migrations/data loss | No migrations, deletion or mutation of stored evidence. |
| TR-06 Point-in-time | No authoritative clocks, cutoffs, freshness or admission changes. |
| TR-07 Financial arithmetic | Only operational integer counts; no financial inputs or calculations. |
| TR-08 Identity/ordering | No domain IDs, sorting or result-order changes. |
| TR-09 Provider recovery | Recorded inputs only; no provider gaps/reconnect/recovery. |
| TR-10 Module boundaries | All changes remain inside the existing module application boundary. |
| TR-11 Reproducibility/integrity | Diagnostic counts/logs remain outside canonical research evidence, fingerprints and acceptance decisions; existing evidence semantics are preserved. |
| TR-12 Security/secrets | No new exposure, credential handling, payload logging, redaction policy or security mechanism; only literal/count fields use the existing log sink. |

Any evidence requiring a matched trigger or expanded scope reopens PLAN with `CONTRACT_CHANGED` / `RISK_CHANGED` before dependent implementation. An additive delta to accepted normative `recorded-market-replay` requires a fresh Reviewer regardless of STANDARD risk. Do not self-approve.

Implementation budget: at most **five Java files** total: modify `src/main/java/io/cryptoresearch/marketdata/application/RecordedMarketDataService.java`; optionally add at most two telemetry-only classes under that same application directory; add at most two focused `*Test.java` files under `src/test/java/io/cryptoresearch/marketdata/application/`. All five must directly serve this contract. Existing tests and fixtures are read-only. No changes to APIs, other existing application classes, persistence, resources, dependencies, runtime/build configuration, governance or accepted main specs. Small evidence handoff files are separate from this Java budget. Architect owns only this active change and required non-semantic completion documentation. No unrelated refactor.

## Applicable invariants and verification strategy

| Invariant | Applicability and evidence required |
|---|---|
| CI-01 | Preservation guard: same item results/failure details and original escaping exception; diagnostic failures cannot discard or convert domain outcomes. |
| CI-02 | Not applicable: no provider gaps or recovery. |
| CI-03, CI-04, CI-05 | Preservation guards: equal replay still reaches unchanged storage/normalization contracts; existing PostgreSQL retry/conflict regression remains green. Operational attempt counters are not durable dedup counters. |
| CI-06 | Preservation guard: no transaction edits; existing raw-first/rollback integration evidence remains green. |
| CI-07 | Not applicable to new counters/logs: no behavior-affecting research parameters or canonical identities introduced. |
| CI-08, CI-09 | Not applicable to new telemetry: no historical/live equivalence, selection or cutoff change. Existing replay regression remains required. |
| CI-10 | Preservation guard: return item order unchanged; no sorting introduced. |
| CI-11 | No new concurrent work; invocation-local summary counts and baseline registry usage avoid adding shared per-call state. No new concurrency test is required. |
| CI-12 | Applicable: module ownership, existing API boundary and architecture checks. |
| CI-13 | Applicable: diagnostic failure is explicitly best-effort and never changes domain correctness or creates fallback data. |
| CI-14 | Preservation guard: unchanged use-case transaction ownership and no provider I/O. |
| CI-15 | Applicable: four metric series, one summary per call, fixed fields, no retries, queues or extra loops over stored evidence. |

New tests are unit-level orchestration tests with existing collaborators replaced by bounded fakes/mocks, a fresh simple registry, and captured structured log events. They assert actual registry deltas and emitted field values, not helper calls or source text. Cover R1–R10, raw-before-normalize call order, exact exception identity, absence of subsequent work on abort, and repeated invocations without summary-state leakage. Use synthetic strings; no provider keys or network are needed. Database semantics are not reimplemented with mocks; existing real PostgreSQL integration tests remain the regression authority.

Establish a compiling, executing behavioral RED before production edits, record the exact command/failing assertion, test hashes and pre-implementation diff, then keep establishing tests frozen through targeted GREEN. Test setup must work against the baseline without requiring a not-yet-existing helper type; use existing orchestration and standard framework/registry observation points. Missing classes, constructor mismatches and startup/configuration failures are not RED. Additional tests may be added without weakening frozen tests. Suggested targeted command: `mvnw.cmd -Dtest=*Replay*Telemetry*Test test` with matching focused test filenames; record the actual exact names. No new PostgreSQL test is required for telemetry itself.

Main's production completion gate remains `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, same-host `docker version`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, and `git diff --check`. Builder runs targeted checks, not the full gate. No full-gate claim is made at planning time.

## Risks / Trade-offs

- Best-effort diagnostics can be missing if their sink fails or INFO is disabled; they are unsuitable as authoritative data-quality evidence. The domain result/exception remains the caller's authority.
- No newly exposed metrics endpoint means operational access uses the existing registry/log environment; exposing remote metrics is a separate change.
- Adding constructor dependencies can break test setup; observable contracts do not mandate a helper shape. Baseline-compatible behavioral tests must precede implementation.
- A single summary offers bounded visibility without per-item identity diagnosis; detailed failures remain available through existing replay results.

## Migration Plan

No data or configuration migration. Deploy the ordinary application artifact after verification. Reverting this isolated implementation removes diagnostic emission without changing schema or persisted evidence. Archive the delta only after normal review, documentation closure and the complete gate.

## Implementation and review evidence

On 2026-09-27, Builder completed the R1–R10 implementation and fresh Reviewer returned APPROVE after authorized repair round 1, with `red_suspect=false`. Fixed classification remains STANDARD, `risk_triggers=none`, `test_mode=RED_REQUIRED`. The three changed Java files fit the five-file budget:

- `src/main/java/io/cryptoresearch/marketdata/application/RecordedMarketDataService.java`
- `src/test/java/io/cryptoresearch/marketdata/application/RecordedMarketDataReplayTelemetryTest.java`
- `src/test/java/io/cryptoresearch/marketdata/application/RecordedReplayTelemetryFailureIsolationTest.java`

Initial RED executed eight tests with eight behavioral failures. Four later GREEN failures exposed fixture defects; Reviewer confirmed TEST_SPEC_ERROR and authorized correction with `requires_new_red=true`. The corrected tests ran against the baseline production service with an empty production diff: 11 tests executed, ten failed at behavioral assertions, zero errors, and one existing INFO-disabled behavior-preservation test passed. Builder recorded the exact assertion evidence and baseline diff in its handoff, which Reviewer accepted.

New RED and targeted GREEN both used `./mvnw.cmd '-Dtest=*Replay*Telemetry*Test' test`. GREEN passed all 11 tests with zero failures, errors or skips. Surefire reports show four tests in `RecordedMarketDataReplayTelemetryTest` and seven in `RecordedReplayTelemetryFailureIsolationTest`. Frozen SHA-256 hashes were unchanged after new RED and match the documentation-close readback:

| Test file | SHA-256 |
|---|---|
| `RecordedMarketDataReplayTelemetryTest.java` | `2A4872A121B5BD10201EFE7D9F89141B7E78F5C2ECDD65F40D801C5CB35A33F0` |
| `RecordedReplayTelemetryFailureIsolationTest.java` | `1F2C8D5A394D5D1778854B78E0FEB1112F5199FBE3D6024B90A8325CF27DA01A` |

Review accepted R1–R10, actual registry/log assertions, original result and exception preservation, dual metric/log sink failure on abort, applicable invariants, and the bounded diff. Existing tests and fixtures remain unchanged. That approval followed repair round 1; documentation recovery below consumes round 2 without resetting the task-wide budget.

Main's complete gate passed on 2026-09-27: same-host `docker version` and `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1` exited zero; `mvnw.cmd clean verify` passed 92 unit tests and 48 integration tests with zero failures, errors or skips (1:03); `openspec validate --all --strict --no-interactive` passed all 13 items; `openspec doctor` and `git diff --check` passed.

Main recorded the scoped readiness checkpoint `e74bc58974017527f6ab26af53ef7b1c21420bf2` at `refs/codex/checkpoints/replay-telemetry-20260927-ready`, including nine raw-byte paths and successful restoration SHA-256 proof. The real index and owner-staged work remained unchanged. Main refreshed it as final checkpoint `7b42f5cd86f397e76225027554ec3aa18e5b64cc` before the first archive attempt.

The first CLI archive added three requirements successfully, and post-archive strict validation (12 items) and doctor passed. However, `mvnw.cmd -Dtest=RepositoryConventionsTest test` ran 33 tests with one failure in `markdownHasNoExportMarkersOrBrokenRelativeLinks`: moving the change invalidated its external documentation links and two historical-note links to active artifacts. Main restored the five active files and accepted spec from the final checkpoint with exact raw-byte hashes and unchanged index, preserving the failed archive copy outside the repository for recovery.

Documentation repair round 2 replaces this design's fragile external relative links with repository-root text references and replaces the two transient links in `docs/notes/F3_4_BUILDER_EXPERIMENT_PROTOCOL.md` and `docs/notes/F3_4_BUILDER_EXPERIMENT_RESULTS.md` with text navigation paths for after archive. The notes' historical findings, behavior, R1–R10 and frozen tests remain unchanged. The current Reviewer approved repair round 2, including scope, invariants and preserved frozen-test hashes; no third repair was needed. Targeted `mvnw.cmd -Dtest=RepositoryConventionsTest test` passed all 33 tests with zero failures, errors or skips. No ordinary repair passes remain; a third requires current-reviewer authorization. This change does not establish broader F3 monitoring, live-data readiness or authoritative data-quality evidence.

After repair round 2, Main reran the complete gate successfully: integrity preflight and same-host Docker passed; `mvnw.cmd clean verify` passed 92 unit tests and 48 integration tests with zero failures, errors or skips (1:06); strict all-item OpenSpec validation passed 13/13; doctor and diff checks passed. Fresh readiness checkpoint `26df7048add0fd85218685ac442d66f56e9e3dc5` at `refs/codex/checkpoints/replay-telemetry-20260927-ready2` retains 11 raw-byte paths with successful restoration proof and excludes owner-staged changes. HEAD and all seven owner-staged file contents and blobs remained unchanged; Git refreshed index stat metadata without changing staged content. Tasks 3.3 and 3.4 are verified again. Main will refresh the final snapshot after this status-only update; the second archive and its post-checks remain pending.
