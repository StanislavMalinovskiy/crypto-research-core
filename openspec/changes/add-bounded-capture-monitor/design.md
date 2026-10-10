## Context

See [proposal](proposal.md) and [contract](specs/bounded-capture-monitor/spec.md). Design is required for coherent-read, bounded-query and truthful gap-visibility guarantees, not a new architecture pattern.

Grounding: `docs/DELIVERY_PLAN.md` queue step3 accepts bounded recovery with explicit incomplete intervals. The active collector's `design.md#configured6h-terminal-acceptance-and-explicit-incomplete-coverage-2026-10-10` verifies four reconnects and incomplete TX/STATE coverage, not zero loss. `PumpSwapPoolCaptureIT` deliberately retains RUNNING after closing cannot commit; prior terminal evidence contains expired historical RUNNING rows. `JdbcCaptureStore.status` exposes neither lease nor end and fetches every gap; its existing `summarySnapshot` already obtains payload-free durable counts and raw-linked committed positions. No reconnect defect is being inferred or repaired.

Controlling reads: root `AGENTS.md`, `.codex/config.toml`, architect role, `docs/AGENT_WORKFLOW_MULTIAGENT.md`, `ARCHITECTURE.md`, `REPRODUCIBILITY.md`, `CORE_INVARIANTS.md`, `TESTING.md`, `TECH_STACK.md`, `OPERATIONS.md`, `DELIVERY_PLAN.md`, `docs/modules/marketdata.md`; ADR0004/0005/0009; accepted operational-health/module-boundaries/marketdata-storage/research-reproducibility; active collector proposal/spec/design/tasks, decoder and operation-persistence artifacts. Relevant code/tests: `PumpSwapCapture`, `MarketDataApi`, `RecordedMarketDataService`, `CaptureLedgerUseCase`, `PumpSwapPoolCollector`, `JdbcCaptureStore`, V13, `PumpSwapPoolCaptureTest/IT`. Only root AGENTS applies. Sources retain their respective responsibilities; collector/decoder are verified but unarchived active behavior, not accepted main-spec capabilities.

## Goals / Non-Goals

Goal: one explicit-session, bounded coherent monitor read using existing durable evidence. No automatic polling or changes to accepted Actuator health. No new framework/library, migration, provider invocation, raw repair, gap transition, writer/lease policy or operation-persistence query. This is not full F3 or an alerting/freshness policy.

## Decisions

### One synchronous public projection

Add `Optional<PumpSwapCaptureMonitor.Snapshot> captureMonitor(UUID sessionId)` to `MarketDataApi`; null input fails explicitly. Immutable records in a new API file use Java values only, defensively copied lists, no Jackson/JDBC types. Existing `RecordedMarketDataService` delegates to a new application reader through its established lazy ObjectProvider pattern, preserving selective-context startup. Neither `PumpSwapPoolCollector` nor its public status/replay behavior changes. A missing session returns Optional.empty, not synthetic counters.

Snapshot contains: sessionId, configurationDigest, recordedState/reason, observedAt, startedAt/endsAt/lockedUntil, leaseValid/windowOpen, committed counters (requests/received/WSS/retained/RPC/reserved CU hundredths, raw TX/state/duplicate/conflict/failure counts), disposition-count records, up to three verified pool positions, unresolved grouped counts, totalUnresolved, inconsistentGapStatus, up to100 interval details and detailsTruncated. Unknown position remains optional/explicit UNKNOWN. Detail can reuse immutable `PumpSwapCapture.Gap`. Include fixed labels processActivity=UNVERIFIED, transportActivity=UNVERIFIED, coverage=UNVERIFIED, unreceivedLoss=UNKNOWN, actualBilling=UNKNOWN. Counters are committed local facts/reservations, not unflushed values or invoice. No new USD calculation, rate ratio, freshness threshold or economic value.

A sampled injected Clock instant normalized to microseconds supplies observedAt once at reader entry; it is a diagnostic observation instant, not database visibility/PIT cutoff or a new availability time. Lease-valid is observedAt < lockedUntil; window-open is startedAt <= observedAt < endsAt. These independent booleans never replace recorded state, infer process exit or declare healthy LIVE. Choosing factual booleans avoids an ambiguous new operational state machine; a new process probe or silence-based alarm is deferred.

### Coherent payload-free read, no corrective writes

The new proxied application reader owns `@Transactional(propagation=REQUIRES_NEW, readOnly=true, isolation=REPEATABLE_READ, timeout=10)`; transaction-local statement_timeout is at most10s. Read session existence/header, bounded aggregates and interval details in that snapshot with at most8 SQL statements including timeout setup, independent of observation/gap count. No row/advisory locks, retries, lease fences/renewal or collector calls. DB failure propagates a safe explicit read failure; it must not become Optional.empty or zero status. Do not expose datasource/Throwable message/secret details.

Ambient-transaction clarification, 2026-10-11: default REQUIRED can silently join a caller's READ_COMMITTED transaction, so the isolation annotation alone does not establish the required coherent snapshot. Architecture transaction policy and accepted ADR0005 place ownership in the application use case and permit caller participation only when short atomicity is explicitly intended; this diagnostic read has no such atomic-write intent. REQUIRES_NEW therefore suspends any ambient transaction and owns one independent committed read snapshot, including when the caller already uses REPEATABLE_READ. Caller-private uncommitted changes are not monitor evidence. On return, the caller's original transaction/isolation remains active; the monitor does not commit or roll back it. Use the existing service-to-reader proxy, never self-invocation or a repository-owned transaction. Existing CaptureEvidenceUseCase already uses an application-owned REQUIRES_NEW boundary; CaptureLedgerUseCase.status remains untouched. No new framework, pool resizing, concurrent fan-out or global transaction-manager setting is authorized. An ambient caller retains its connection while this one synchronous reader borrows another under existing finite pool admission; exhaustion fails explicitly without fallback or joining the caller. Category remains CORE_RISK -> CORE_RISK, same TR02/CI11/CI14 guarantee and cumulative budget, no requirement relaxation or ownership expansion.

Reuse the exact existing `summarySnapshot` raw identity/provider/slot/digest proof for committed positions and its counter/count logic; it may be called inside this transaction or factored as a read-only helper without changing existing summary behavior. Do not select raw payload, notification, response, account data, envelope payload, configuration JSON, holder or owner authorization into the public projection. Session timing/digest and counts come from existing V13 columns; any configuration required internally remains bounded by the existing launch contract. No operation revision join or implicit latest parser. Keep all SQL marketdata-owned.

Alternative rejected: composing existing captureStatus/replay calls, because they materialize complete gaps/payloads and do not guarantee one monitor snapshot. Existing Actuator readiness remains readinessState+db, and liveness remains process-local; collector uncertainty does not change those groups.

### Complete totals, bounded examples

Exclude only rows with kind = TRANSACTION AND status = RECOVERED_TRANSACTIONS; all others remain unresolved, including unrecognized statuses and contradictory STATE recovery labels. Count all unresolved rows grouped by pool/kind/status independently of details. Derive inconsistentGapStatus from the full grouped set if kind <> TRANSACTION AND status = RECOVERED_TRANSACTIONS, not only detailed examples; retain that interval without correction. Query details with ORDER BY gap_id ASC LIMIT101, publish first100 and set truncation iff overflow exists. IDs establish a total order within each snapshot, not a promise of stable live pagination or recovery chronology. The interval list contains unresolved intervals only; it is not the full episode history. Preserve null boundaries; never infer omitted bounds or recovered state.

At most64 grouped gap counts and32 disposition-status counts (fetch one overflow group then fail), three positions,101 fetched interval rows and a128KiB UTF8 encoded public projection bound. Unexpected cardinality/oversized metadata fails CAPTURE_MONITOR_RESULT_LIMIT without partial publication; no stored evidence is corrected. These limits bound projection work/output, not the exact aggregate denominator. Counts may scan indexed rows for the named finite session under the query/transaction deadline. Reuse existing session/status/gap indexes; no speculative index/migration. If the deadline cannot serve a valid snapshot, return an explicit failure, not fabricated counts. No payload-bearing historical envelope scans or N+1 reads.

## PLAN handoff and risk

Tier/risk CORE_RISK, Builder `builder_sol` (Sol6.1 medium), reason CONTRACT_CHANGED, subreason RISK_CHANGED: new observable coherent gap/lease read contract, separate from persistence scope. Previous/new assessment CORE_RISK -> CORE_RISK, no downgrade. Executable test mode RED_REQUIRED; this PLAN is documentation-only RED_NOT_REQUIRED with factual/requirements/link checks.

| TR | Assessment of concrete changed guarantee |
|---|---|
| 01 persistence | No durable semantics or mutation changes. |
| 02 transactions | MATCH: independently read fields must form one coherent committed snapshot under concurrent capture. |
| 03 concurrency/locking | No worker/lock/concurrency policy change; visibility is handled by TR02. |
| 04 idempotency/retry | No retry/domain-effect change; new read performs no mutation/retry loop. |
| 05 migrations/data loss | No migration/deletion/repair; existing evidence unchanged. |
| 06 PIT | No research decision/admission or availability cutoff; observation time is diagnostic only. |
| 07 finance/exact arithmetic | Existing integer counters retained without new calculation, rounding, expense authority or billing claim. |
| 08 identity/ordering | MATCH: deterministic detail truncation needs immutable total gap-ID order. No durable identity change. |
| 09 provider gaps/recovery | MATCH: complete unresolved denominator and stale-session visibility must not falsely imply continuity/recovery/activity. Recovery behavior unchanged. |
| 10 module boundaries | Existing DAG, marketdata ownership and synchronous named API unchanged. |
| 11 reproducibility/integrity | MATCH: a public projection must faithfully preserve committed counts, verified positions and unknowns without mixed/partial output. |
| 12 security/secrets | Existing prohibition preserved, no new secret/config/endpoint/security boundary; payload/holder omission and safe failures are regression checks. |

Affected/doubtful CI: CI01 raw-linked committed-position proof; CI02 counted unknown/gap loss must not disappear; CI10 total ordering/truncation; CI11 concurrent visibility; CI12 owning provider boundary; CI13 no fabricated health/fallback; CI14 owning read transaction/no provider I/O; CI15 bounded work/waits. CI03-06 durable retry/conflict/atomic-write guarantees unchanged and regression-tested with collector suite, not redesigned. CI07 no durable algorithm identity introduced; diagnostic label fields describe versions without research identity. CI08 research historical/realtime behavior unchanged. CI09 no decisions/availability/admission route; diagnostic observedAt is not PIT evidence. CI11 is doubtful until a real concurrent PostgreSQL read proves coherent publication.

### Overlaps (openspec list checked before readiness)

- `add-pumpswap-operation-persistence`: SAFE TO PROCEED SEQUENTIALLY ONLY. Shared `MarketDataApi`/`RecordedMarketDataService` cannot have concurrent ownership. Main dispatches monitor implementation only after persistence Builder releases those paths. Preserve persistence changes, qualification/RED evidence and unchecked tasks; no storage completion implied by dispatch.
- `add-bounded-pumpswap-pool-collector`: SAFE TO PROCEED. New separate capability, read-only JdbcCaptureStore addition; no existing public status/replay, recovery/source/lease/budget/migration/old artifact changes. No collector Builder is assigned concurrent store writes.
- `add-pumpswap-raw-operation-decoder`: SAFE TO PROCEED. No parser/decoder/results/qualification change; API additions preserved.
- `establish-r1-d1-data-gate` and `preregister-r1-e2-pumpswap-cohort`: SAFE TO PROCEED, PAUSED scope/evidence/holdout/outcomes/admission untouched.

### Exact bounded ownership

Builder may edit only:

- `src/main/java/io/cryptoresearch/marketdata/api/MarketDataApi.java`
- new `src/main/java/io/cryptoresearch/marketdata/api/PumpSwapCaptureMonitor.java`
- `src/main/java/io/cryptoresearch/marketdata/application/RecordedMarketDataService.java`
- new `src/main/java/io/cryptoresearch/marketdata/application/ReadCaptureMonitorUseCase.java`
- `src/main/java/io/cryptoresearch/marketdata/infrastructure/persistence/JdbcCaptureStore.java` (new read projection/helper only; existing writer/recovery semantics frozen)
- new `src/test/java/io/cryptoresearch/marketdata/application/CaptureMonitorTest.java`
- new `src/test/java/io/cryptoresearch/marketdata/application/CaptureMonitorIT.java`

Reuse existing test helpers read-only. No existing test changes, migrations, dependencies/build/config, collector/adapter/launcher, raw parser, research tools, accepted specs or documentation. Builder is not alone: preserve others and do not revert their changes. Architect owns this new active artifact set; docs later only by Main's exact assignment. Never edit task checkboxes in BUILD.

Budget: no paid/provider work, no managed DB, isolated local CPU/PostgreSQL18.6 only. Prior engineering elapsed time UNKNOWN remains cumulative across both changes/session replacement; resource reservation/billing unknowns are untouched, not reset. No numeric repair ceiling. Repeated defect/stall/exhaustion requires independent technical cause/approach/remaining-budget/next-result diagnosis. No genuine owner choice blocks this scope; long-run/deployment/expansion authority remains outside it.

## Verification strategy

Meaningful unit RED: exact lease/window boundary facts, preserved RUNNING/STOPPED, valid lease cannot claim healthy transport, immutable sorted/truncated output and explicit bounds. Meaningful real PostgreSQL RED: absent session vs DB failure; TX recovered/STATE and unknown/null gaps still unresolved, contradictory STATE RECOVERED_TRANSACTIONS beyond100 details remains counted with inconsistentGapStatus;101 details vs full denominator; concurrent transition between component reads yields a single snapshot; repeated read changes no rows/counters/lease; payload-free reads and zero provider calls; output/group overflow and finite timeout fail explicitly. Add an ambient READ_COMMITTED regression through the real public API/proxied reader: bounded latches arrange a separate transaction's commit after the monitor's first data read and before later component reads; assert one wholly pre-transition snapshot, never mixed fields. A companion caller-private uncommitted-change case must prove exclusion from the monitor and that the caller resumes with its original active transaction/isolation and controls its own rollback. Test fixtures/hooks may observe the physical monitor read-only REPEATABLE_READ boundary without adding diagnostic SQL to production's <=8 statements. Annotation/reflection or mocked transaction-manager tests alone are not this behavioral RED. Preserve named assertions/expected/actual and tests_changed_after_red; compile/setup/Docker failures are not RED.

Targeted commands after host `docker version`:

`mvnw.cmd -Dtest=CaptureMonitorTest,PumpSwapPoolCaptureTest -DskipITs test`

`mvnw.cmd -Dit.test=CaptureMonitorIT,RawTransactionStorageIT -Dtest=CaptureMonitorTest,PumpSwapPoolCaptureTest -Dsurefire.failIfNoSpecifiedTests=false verify`

Verification-efficiency amendment, 2026-10-11: Main authorizes the smallest meaningful Builder PostgreSQL selection, CaptureMonitorIT and RawTransactionStorageIT, retaining the existing CaptureMonitorTest/PumpSwapPoolCaptureTest unit selection. The unmodified 85-test PumpSwapPoolCaptureIT suite costs approximately10 minutes and will run unchanged in Main's mandatory stable-union `mvnw.cmd clean verify` before commit; it is not selected, waived or claimed PASS from targeted output. All new meaningful monitor RED and PG coherence (including ambient caller), bounds, no-write/provider/payload and verified-position checks remain required. No observable semantics, frozen assertion, code ownership, CORE_RISK classification, cumulative budget or independent review change follows. Current scope adds only the new read projection/helper and freezes existing collector writer/recovery behavior; no concrete additional named collector case is identified for the Builder target. If an implementation change concretely affects an existing collector behavior, report that effect and select the minimal affected case names before targeted completion, without editing old tests or replacing Main's full suite.

Tests run isolated postgres:18.6-alpine with fixed/controlled Clock, collector disabled/no provider credentials. Read-only SQL/query assertions must not start a collector or select payload-bearing rows. Legacy collector regressions remain unchanged and required in Main's full gate. No managed/saved evidence mutation or fresh live qualification required for this read contract.

Fresh independent Reviewer is mandatory. Main may reuse the same Builder sequentially and commission one fresh Reviewer for the stable union of persistence+monitor; reviewer must separately assess each contract/RED/GREEN and persistence's full saved124-TX qualification. One APPROVE applies only if both are individually verified; otherwise retain separate blockers/status. Architect checks each change's verified tasks only after that applicable APPROVE. Main may run one full gate for the stable union and make one scoped product commit, no push, provided no checked input changes afterward; later changes rerun affected checks. Review/monitor dispatch does not mark persistence complete or waive its qualification.

Main full gate before Java commit: `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `docker version`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, `git diff --check`, `git diff --cached --check`. Targeted Builder GREEN is not this gate. No automatic archive.

## Risks / Trade-offs

- Expired lease does not prove process exit -> independent facts plus activity UNVERIFIED, never mutation/STOPPED inference.
- No known gaps does not prove complete chain coverage -> coverage/loss remain unverified/unknown even with zero retained unresolved intervals.
- Aggregate latency may grow with retained finite-session evidence -> fixed projection statements/row limits and10s deadline; explicit failure, no N+1/new index without renewed PLAN.
- 100 details are not complete intervals -> full grouped denominator and truncation; existing explicit replay remains separate, not called by monitoring.
- Shared API edits may collide -> Main enforces sequential path release and stable union review/gate.

## Migration Plan

No schema/deployment action. Additive API/read implementation only after Main dispatch. Rollback of uncommitted code must preserve concurrent owner work; existing raw/capture/operation rows remain untouched. Runtime long run/deployment remains separately authorized future work.

## Implemented and verified, unarchived, 2026-10-11

Status: seven-path implementation, meaningful RED/targeted GREEN and fresh independent review complete; Main complete gate PASS483 tests (266 unit+217 PG, including unchanged collector85),0 failures/errors/skips; integrity/Docker/strict21/21/doctor/working+scoped-cached diff exit0. Scoped34-file product commit `c6fad23544b320426ecd720c2716c670138473f4` is verified, not pushed. No migration, managed deployment, accepted main-spec synchronization or archive. Latest owner continuation prospectively supersedes the previous next-PLAN-only checkpoint for these bounded storage/monitor implementations, not paid/live/managed/run/deployment/pool-expansion/push/R1/admission authority.

`MarketDataApi.captureMonitor(UUID)` delegates through lazy ObjectProvider to the independent proxied REQUIRES_NEW, read-only REPEATABLE_READ reader. Real public-API PG regression first exposed REQUIRED's mixed concurrent counts/private uncommitted reason; final implementation preserves one committed snapshot and resumes the original caller connection/transaction/isolation without committing or rolling it back. Missing session is explicit absence; read/transaction failures are sanitized, not fabricated zero counts. Recorded RUNNING/STOPPED, lease/window booleans and microsecond observedAt remain separate from fixed activity/transport/coverage UNVERIFIED and billing/unreceived-loss UNKNOWN.

Final projection uses seven physical statements,101 fetched details/100 published ordered by gap ID,64 gap groups/32 disposition groups/three positions and128KiB UTF8 output bound. Full unresolved totals exclude only exact TRANSACTION+RECOVERED_TRANSACTIONS; contradictory STATE status beyond100 details remains counted and flagged. Unknown statuses/null boundaries survive. Existing exact raw identity/provider/slot/digest position proof is reused; writer/status/replay/summary methods are unchanged. Full marketdata row fingerprints remain unchanged on repeat read; no payload/holder/secret output or provider/collector/decoder action. Real locked-table read fails safely under the10s deadline. Complete finite-session aggregates may scan indexed rows under deadline; result bounds do not claim bounded historical denominator or complete coverage.

Evidence root `C:/crypto-research-evidence/bounded-capture-monitor-20261011`: `build-done.md`, `red-evidence.md` and preserved RED logs; unit-green-01 exit0,46 units; targeted-green-04 exit0,46 units+15 PG=61 (8 monitor+7 raw PG),0 failures/errors/skips. tests_changed_after_red=true records additive tests/source-proven setup corrections without weakened establishing meaning. The actual final monitor PG count is8, not the earlier provisional9. Unmodified collector85 PG tests are not selected or claimed PASS by this target; Main complete clean verify subsequently passed, as recorded below.

Fresh independent `/root/storage_monitor_review` APPROVE for each change on stable union is recorded in `C:/crypto-research-evidence/pumpswap-operation-persistence-20261011/main-independent-review-01.md`; red_suspect=false. Main's saved124/68 qualification03 also rechecks storage after shared API/service monitor additions, not implied by monitor tests. Cumulative engineering UNKNOWN and paid/provider0 carry forward. Architect final DOCS_CLOSE checks all4 tasks after actual Main gate/commit PASS. Evidence `C:/crypto-research-evidence/pumpswap-operation-persistence-20261011/main-gate-01/result.md` records exact checks/full11min31s run; later cached-check-scoped-01/product-commit exits0 and product-commit.log prove the report's then-pending staging/commit, observed HEAD `c6fad23544b320426ecd720c2716c670138473f4`. No process existence/freshness/zero-loss/WS1011-cause/full-F3/admission inference, live launch or R1 restart follows.
