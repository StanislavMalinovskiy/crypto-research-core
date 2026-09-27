## Context

See [proposal](proposal.md) for motivation and [delta](specs/recorded-market-replay/spec.md) for the complete observable contract. `RecordedMarketDataService.replay` currently stores raw observations, invokes the separately transactional normalizer, and returns ordered `NORMALIZED` / `NORMALIZATION_FAILED` items. Its existing normalization catch handles `IllegalArgumentException` and `NormalizationConflictException`; raw-storage and other runtime failures escape. There is no production telemetry instrumentation. Actuator supplies Micrometer, and SLF4J is already available.

Controlling sources are [F3.4](../../../docs/DELIVERY_PLAN_FIXES.md#f34-минимальный-monitoring), [marketdata](../../../docs/modules/marketdata.md), [Architecture](../../../docs/ARCHITECTURE.md), [Operations](../../../docs/OPERATIONS.md), [Testing](../../../docs/TESTING.md), [Reproducibility](../../../docs/REPRODUCIBILITY.md), accepted `recorded-market-replay`, and ADRs [0005](../../../docs/adr/0005-transactions-and-events.md), [0006](../../../docs/adr/0006-background-work-and-bounded-concurrency.md), [0008](../../../docs/adr/0008-six-module-mvp-topology.md), [0009](../../../docs/adr/0009-research-reproducibility.md). Existing `FirstSignalEvaluationIT` proves raw-first behavior, failure retention and equal replay. There is no nearer module AGENTS.md.

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

Product risk is **STANDARD**, `risk_triggers=none`, `test_mode=RED_REQUIRED`. The work needs coordinated counter, summary and failure-path reasoning, rather than a mechanical configuration edit. Classification uses only [the workflow trigger authority](../../../docs/AGENT_WORKFLOW_MULTIAGENT.md#architect-planning-and-risk).

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
