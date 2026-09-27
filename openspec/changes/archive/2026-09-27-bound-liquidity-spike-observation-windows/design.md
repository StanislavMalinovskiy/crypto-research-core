## Context

See [proposal.md](proposal.md) for motivation and the [delta](specs/signal-evaluation/spec.md) for the behavioral contract. The owner approved bringing the recorded-data portion of F6.2 forward, with a five-minute baseline tolerance, one-minute current bound and two-endpoint coverage. This does not finish production F6.2 or unblock F1/F3.

`LiquiditySpikeSignalService.detect` currently queries baseline observations from `Instant.EPOCH` through `windowStart`, then current observations from `windowStart` through the decision cutoff. `latest` takes the last item from the market-data API's ordered result. `DetectionRequest` already contains detector version, configuration fingerprint and dataset identity; candidate identity already incorporates the detector version. No new storage field is needed.

Controlling sources: `docs/DELIVERY_PLAN_FIXES.md` F6.2, `openspec/specs/signal-evaluation/spec.md`, `docs/modules/signal.md`, `docs/modules/marketdata.md`, `docs/ARCHITECTURE.md`, `docs/REPRODUCIBILITY.md`, `docs/TESTING.md`, and ADRs 0004, 0005, 0008 and 0009. The active delta records the intentional migration from accepted behavior; existing accepted specs are not edited during implementation.

## Goals / Non-Goals

**Goals:** enforce the approved windows before writes, retain total ordering and immutable provenance, distinguish the new policy by version, and prove both rejection and successful publication through meaningful tests.

**Non-goals:** change public request shapes, market-data storage/query implementation, migrations, dependencies, framework configuration, module boundaries, financial thresholds, risk/scoring rules, evaluation/report semantics or transaction ownership. Do not build a scheduler, transport gap model or venue/pool aggregation. No new infrastructure or production dependency is needed.

## Decisions

### Normalize and validate before querying

Keep the existing UTC microsecond normalization for request and risk cutoffs. After normalization, validate the exact supported detector string `liquidity-spike-v2`, require `windowStart == decisionCutoff.minusSeconds(3600)`, and derive all endpoint bounds before calling `MarketDataApi`. Null/blank/old/future/whitespace-modified detector versions, mismatched targets and unrepresentable bounds fail with `IllegalArgumentException` before market-data queries, risk assessment or persistence. Existing validation of other fields remains.

Queries retain the requested asset, dataset fingerprint and decision cutoff. Baseline bounds are `[C - 3900s, C - 3600s]`; current bounds are `[C - 60s, C]`. Both are inclusive. Arithmetic uses explicit request instants, never a machine clock. Do not clamp extreme dates or silently adjust the supplied window. A bounded query is preferable to fetching all history and filtering in `signal`: it expresses eligibility at the existing owning boundary and avoids the prior unbounded historical time range. These time bounds do not establish a new maximum row count.

### Preserve the existing market-data total order

`JdbcNormalizedMarketDataStore` returns observations ordered by `observed_at`, `chain_id COLLATE "C"`, `transaction_value COLLATE "C"`, `event_locator COLLATE "C"`. Select the final element of that ordered API result. Do not re-sort using Java's natural identity/string comparator: UTF-16 order can differ from PostgreSQL UTF-8 bytewise order for opaque Unicode keys. Unit doubles must honor the API contract; PostgreSQL evidence must establish ties with distinct transaction/locator keys, including a Unicode ordering discriminator.

Require both endpoints and distinct normalized identities/timestamps. Missing evidence or unmet thresholds returns the existing empty `DetectionResult` before candidate persistence. Do not add a new rejection journal or status. The existing candidate-first risk path remains unchanged once qualifying endpoint evidence exists.

### Represent a fixed policy by a new detector version

Use `liquidity-spike-v2` as the sole supported version for new detection, covering the fixed target, windows, endpoint coverage and selection rule. Retain the current request fields, caller configuration fingerprint and canonical fingerprint encoding. Since candidate identity already includes detector version, the transition obtains distinct identities without changing the hashing algorithm or adding API configuration fields.

An arbitrary version label cannot authorize executing a different policy. Explicitly reject unsupported versions rather than silently running v2 under a v1 or unknown identity. Existing accepted-signal lookup remains unfiltered by supported detection versions. Previously persisted snapshots retain their original bytes/values, IDs and versions; there is no migration or reinterpretation. Historical v1 re-execution is not supported by the new detector; reproducing that algorithm requires its recorded source revision.

The alternative of configurable tolerances would require parameter transport and fingerprint validation. It is outside this fixed-policy slice. The chosen five-minute/one-minute limits are initial engineering choices, not empirical optimum claims; future changes require a new version and contract.

### Preserve ownership and transactions

Production changes stay in `signal.application`. `signal` reads market-data and risk through their public APIs before its existing owned transitions. `SignalCandidateTransitions` continues to own short candidate creation and completion transactions, and `JdbcSignalPersistence` remains unchanged. No provider I/O, new transaction, cross-module SQL, persistence rewrite or retry/concurrency algorithm is introduced.

### Verification and behavioral evidence

`test_mode = RED_REQUIRED`. Write specification-derived tests and perform the necessary existing test-call migration from `liquidity-spike-v1` to v2 before establishing RED. Preserve all existing assertions, recorded raw fixture bytes, quantities, thresholds, score, outcome and report expectations. An expected algorithm-version change is not permission to weaken a regression. Historical-lookup tests deliberately retain persisted v1 values.

Prepare all planned unit and PostgreSQL tests before RED. At least one named new unit behavior test must execute on the old production implementation and fail at its intended assertion, such as accepting a stale endpoint or accepting an unsupported version. Compilation/discovery/startup/SQL setup failures do not establish RED. Record exact command, named assertion with expected/actual values, all establishing test paths and content hashes, and the production-free diff. After RED, freeze those tests and fixtures; do not weaken, skip, narrow or reconfigure them. Integration RED is not required: the expected unit failure stops Maven before Failsafe, so execute the PostgreSQL checks at GREEN after implementation. A test/spec conflict follows the current-reviewer `TEST_SPEC_ERROR` procedure with new RED and hash when authorized.

Unit evidence covers all window bounds and one-microsecond exclusions, requested asset/dataset/cutoff query parameters, stale/missing endpoints, both thresholds, version rejection before collaborator calls, normalized target validation, representability and retained time-axis semantics. Ordered API doubles may test the signal logic but cannot substitute for PostgreSQL ordering or durable-state evidence.

PostgreSQL integration evidence uses isolated Testcontainers and the real public detection path to prove stale/missing/invalid requests produce no new candidates or accepted signals, valid boundary/tie selections preserve source evidence, and equal replay preserves exact immutable snapshots without duplicate rows. Verify historical v1 lookup using a pre-existing snapshot arranged through signal-owned test setup; do not enable a legacy detector branch for the test. Preserve the full first-slice evaluation regression under v2, including sub-microsecond normalization, risk rejection and equal retry.

Planned targeted commands after separate implementation authorization: use the unit command for behavioral RED before production edits, then run both commands for GREEN:

```powershell
./mvnw.cmd -Dtest=LiquiditySpikeSignalServiceTest test
./mvnw.cmd -Dit.test=LiquiditySpikeObservationWindowsIT,FirstSignalEvaluationIT verify
```

Use PowerShell-safe quoting for comma-containing Maven arguments if required by the shell. First establish Docker availability with `docker version` in the same host execution context as every integration command. Builder owns targeted RED/GREEN and test-hash evidence; Main owns the full final gate and subsequent archive workflow. No tests or implementation are executed by this planning phase.

## Risk, Invariants and Bounded Budget

Architect classification under `docs/AGENT_WORKFLOW_MULTIAGENT.md`: **CORE_RISK**, matched **TR-06** (point-in-time eligibility), **TR-07** (which financial evidence participates), **TR-08** (selection order/versioned identity), **TR-11** (reproducible algorithm identity). The other triggers do not add scope: persistence/transaction/retry algorithms and schemas remain unchanged; there is no new concurrency, recovery, module boundary, secret or security work. Fresh Reviewer is mandatory for both CORE_RISK and the accepted normative spec amendment.

| Invariant | Applicability and required evidence |
|---|---|
| CI-01 | Applicable: absent endpoint evidence yields the declared empty result, never a fabricated signal; existing accepted snapshots remain readable. |
| CI-02 | Not applicable to implementation: no provider gap/recovery model exists in this slice; endpoint coverage makes no gap-free claim. |
| CI-03 | Applicable regression: repeated identical v2 detection yields the same snapshots and counts. |
| CI-04 | Applicable regression guard: preserve existing immutable conflict handling; no persistence equality code changes. |
| CI-05 | Applicable regression: v2 retry produces no duplicate candidate/accepted rows. |
| CI-06 | Applicable guard: rejected preconditions produce zero writes; existing owned atomic transitions remain unchanged. |
| CI-07 | Applicable: fixed behavioral policy has explicit v2 identity; configuration and dataset fingerprints remain in snapshots/identity. |
| CI-08 | Applicable boundary: recorded replay uses the declared observation-time semantics consistently; no live/history availability equivalence is claimed before F3. |
| CI-09 | Applicable: exact inclusive windows, normalized cutoff and dataset isolation exclude future observations. |
| CI-10 | Applicable: timestamp/bytewise-key total order, PostgreSQL tie evidence and submission-order invariance. |
| CI-11 | Not directly changed: no concurrency execution path is introduced; existing gate regressions remain required. |
| CI-12 | Applicable guard: only owning synchronous APIs are used; no provider types or implementation imports cross modules. |
| CI-13 | Applicable: missing evidence never widens windows or supplies replacements; limitations remain explicit. |
| CI-14 | Applicable guard: existing signal-owned transaction boundaries persist without provider I/O or cross-module writes. |
| CI-15 | Applicable to query time ranges: finite baseline/current windows and no retry/wait mechanism; no unsupported row-count bound claim. |

Apply `docs/REPRODUCIBILITY.md` directly for TR-07: existing exact liquidity arithmetic and scale rules remain unchanged. Review must provide concrete evidence for every applicable invariant and explain non-applicability.

Bounded author-owned paths:

- **Builder production: at most 2 files.** `src/main/java/io/cryptoresearch/signal/application/LiquiditySpikeSignalService.java` and, only if needed for cohesive pure window policy, one new file `src/main/java/io/cryptoresearch/signal/application/LiquiditySpikeObservationPolicy.java`.
- **Builder tests: at most 3 files.** `src/test/java/io/cryptoresearch/signal/application/LiquiditySpikeSignalServiceTest.java`, new `src/test/java/io/cryptoresearch/signal/application/LiquiditySpikeObservationWindowsIT.java`, and `src/test/java/io/cryptoresearch/FirstSignalEvaluationIT.java`. Keep `src/test/resources/fixtures/first-signal-evaluation.json` unchanged.
- **Architect documentation:** this active change tree and, during separately assigned documentation work, `docs/modules/signal.md` only. Completion must state the remaining F6.2 live gap/coverage limitations.
- **Excluded writes:** all other production/test paths, accepted main specs before authorized archive/sync, migrations, build/runtime configuration, dependencies, governance, ADRs and other active changes. A necessary expansion returns to PLAN with its reason; it is not silently absorbed.

Review and repairs retain the task-wide workflow budget: two ordinary passes and at most one third explicitly authorized by the current reviewer; replanning does not reset it. Risk becomes fixed only at PLAN_READY.

## Risks / Trade-offs

- [Sparse observations suppress detections] → Return no signal when either window is empty; measure data availability later without automatically relaxing this policy.
- [Two points resemble continuous coverage] → State endpoint-only semantics and the actual 59–65 minute interval; production gap qualification remains F3/F6.2 work.
- [Recent observation contains old source data] → Preserve the `observedAt` axis and source-time lineage without advertising live-event freshness.
- [Same asset spans incomparable venues or pools] → Retain the existing caller/data-contract limitation; no new aggregation or implicit venue policy is introduced.
- [Version transition breaks old detection callers] → Require explicit v2 calls, retain old snapshot lookup, and migrate repository test callers before RED without changing financial expectations.

## Migration Plan

There is no database migration. After separate implementation authorization, update the detector and its in-repository test callers together, verify targeted behavior, obtain fresh review and complete Main's repository gate before accepting the change. Consumers must use the supported version and normalized one-hour target. Retain historical v1 snapshots unchanged.

If deployment is reverted to the previous build, do not relabel or delete v2 snapshots. Reproduce v1 and v2 runs only with their recorded implementation revisions and version identities. No automatic data rollback, archive or deployment is part of this planning task.
