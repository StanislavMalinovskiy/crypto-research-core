## Context

See [proposal](proposal.md) and [contract](specs/pumpswap-versioned-pool-candle-diagnostic/spec.md). Old `PumpSwapChainTimeCandleCalculator` expects fixed-v4 V14 StoredBatch and user execution quantities; `NativeSolPriceRiseDiagnostic` hard-pins that source. Neither can be passed a fabricated StoredBatch for pure v5. v5 returns exact nested ordinary v4 proofs plus114new pool legs on the saved window. A separate pure recorded route avoids persistence/admission and preserves old APIs/build pins.

### Planning handoff

- `tier=CORE_RISK`, `risk=CORE_RISK`, `risk_triggers=TR-06,TR-07,TR-08,TR-11`, `reason=CONTRACT_CHANGED`, `test_mode=RED_REQUIRED`. New knownAt eligibility, exact comparable price, selected source identity/order and whole-window reproducibility are changed guarantees. No downgrade.
- All TR assessed: TR-01/02/03/04/05 absent (no storage/transactions/concurrency/retry/migrations), TR-09 absent (no provider recovery), TR-10 absent (existing signal→marketdata::api, no boundary change), TR-12 absent (no secret/security/runtime work). CI-01/07/08/09/10/12/13/15 apply; other durable atomicity/idempotency CI not changed. Exact arithmetic uses Reproducibility directly.
- Sources read: AGENTS/MULTIAGENT/architect rules, Testing, Reproducibility/ADR0009, Architecture/ADR0004 and marketdata/signal module docs; accepted `research-reproducibility`, `chain-identity`, `module-boundaries`, `signal-evaluation`; current v4/v5/chain-time/diagnostic specs, API/calculators/tests. No new ADR/dependency/infrastructure.
- Owner authorizes implementation/checks/local commits without repeated permission; current phase stays zero network/provider/managed DB/collection/spend/push. Later prospective$50/permanent3pool authority is a separate following task, not merged here. Rare39 untouched, R1/D1 paused. Fresh independent Reviewer and Main full Java gate mandatory; no archive authorized.
- Cumulative work stays local: old evidence/input hashes immutable, new receipts only in `C:/crypto-research-evidence/pumpswap-versioned-candles-20261012-15182bf5659445e29205ca9c6929eba0`; pure APIs plus isolated PostgreSQL for wiring/regression only. No new automatic repair/time caps or reset of earlier resource accounting.

### Active overlaps

`openspec list --json` inspected before readiness. New capability absent in main specs. All overlaps **safe to proceed** with the following explicit boundaries:

| Active change | Preserved boundary |
|---|---|
| `add-pumpswap-multihop-pool-leg-decoder` | unchanged fixed-v5 source/API/fingerprints; consume once per TX |
| `add-pumpswap-raw-operation-decoder` | unchanged fixed-v4 source/proof/user quantities |
| `add-pumpswap-chain-time-signal-diagnostic` | unchanged stored-v4/user-price candles/diagnostic versions and128-source rule |
| `add-pumpswap-operation-persistence` | no new V14 publication/read/SQL or coerced legacy trader |
| `add-pumpswap-offline-pool-metrics`, `add-pumpswap-operator-report` | old observation-time/receivedAt reports unchanged |
| `add-bounded-pumpswap-pool-collector`, `add-bounded-capture-monitor`, `add-pumpswap-continuous-readiness` | no runtime/authority/recovery/monitor/writer change |
| `preregister-r1-e2-pumpswap-cohort`, `establish-r1-d1-data-gate` | no research restart/holdout/outcomes/admission |

## Goals / Non-Goals

One comparable native pool price, full bounded selected saved population, honest12pair result. No generic indicator framework, new signal family/threshold/calibration, permanent storage, runtime/provider work, fees/PnL/trader inference, rare parser repairs or new market completeness guarantee.

## Decisions

1. **One v5 interpretation:** new pure `MarketDataApi.pumpSwapVersionedPoolCandles(PumpSwapVersionedPoolCandles.Request)` accepts recorded sources,1–3existing bindings, exact decoderVersion, aligned from/to, `derivationObservedAt` and K. Reject non-v5 selection and competing source interpretations for one chain/signature before time filtering. Sort complete distinct sources; call unchanged v5 in chunks<=128. Within each v5 result consume ordinary nested-v4 operations plus new legs exactly once by chain/signature/full path/ordinal; duplicates/conflicts fail. Canonical operation-position references remain parser-independent; reproducible content resolves through the report/version/raw/proof tuple explained below, not a physical chunk fingerprint. Global report fingerprint excludes chunk packaging so128/64/1chunk sizes have equal computational result. No second standalone-v4 merge or latest revision selection.
2. **Common pool-net proof:** existing v5 legs retain their already-proven POOL_NET_SOL_PER_BASE basis. New `PumpSwapOrdinaryPoolNetProof` derives the same basis for independently v4-confirmed target-pool operations: bind invocation accounts/event/proof transfer paths to the requested pool/vault tuple and actual token states, reconstruct all actual vault mutations through bounded complete indexed/CPI instructions, reconcile pre/post raw balances and prefix validity, then attribute only each operation's distinct transfer paths. Multiple operations sharing a pool may be priced only from uniquely partitioned flows whose sum reconciles endpoints; unknown external mutation/hook/path/unit attribution yields unpriced, never whole-TX delta allocated per operation. Base signed own flow must match original confirmed base amount/direction. Quote WSOL9 signed own net has the corresponding direction; ratio is `abs(ownQuoteFlow)*10^baseDecimals/(baseAmount*10^9)` reduced by GCD. Event/user gross fees remain original facts, not replacement price. No old parser/helper refactor.
3. **Whole-window bounds:**4096distinct TX/64MB raw aggregate/8MB perTX/10000operations and populated bars/1000timepositions/64MB canonical report; chosen to include observed1166population and721/1137dense pairs with headroom. Existing per-chunk decoder/path/data limits unchanged. Preflight whole input before parsing/output, no silent trim. Report references include raw digests/positions/selected version/proof references, not complete raw payloads or invented persistent revision keys/times.
4. **Event and derivation clocks:** raw blockTime only; verify sourceTime agreement, exactslot/index and cross-source position conflicts. New per-TX/operation derivedKnownAt=max(original raw received/admitted/ingested, explicit microsecond derivationObservedAt). Supplied derivationObservedAt must be>=all source raw clocks; preserve it across repeat, record its actual capture in new receipt. K includes derivation for offline qualification (K2030 retained for comparable retrospective semantics); never label it a historical persisted/commit time. Events known afterK excluded explicitly. Sorted sparse300s OHLC uses old event-time/index/path ordering and exact ratios but a new version/source identity. No carried prices.
5. **Uncertainty:** preserve all1166TX outcomes. Successful UNSUPPORTED/PARTIAL relevant invocations touching target pool/vault in complete resolved raw keys create `UNRESOLVED_POOL_EVIDENCE` for their blockTime bucket. If membership/key or event time cannot be resolved, conservatively report scope/time uncertainty for affected requested groups, not a clean bar. Preserve3NON_OPERATION/11NO_PUMPSWAP;942failed emit no operations and do not taint executed prices. Out-of-window known events do not taint a pair. No repair of the39rare reasons. Unknown source completeness/gaps remain global limitations, not zero loss or automatic fabricated positive/negative result.
6. **Reuse rule, not fabricated old report:** add small `VersionedNativeSolPriceRiseDiagnostic` and immutable new signal DTO/API. Existing `NativeSolPriceRiseDiagnostic.java` remains byte-unchanged; new route uses the same three-line exact inclusive105/100 comparator and same adjacent300s adequacy rule, with comparative boundary tests proving equivalence. No generic engine/refactor. New SignalApi request carries one recorded-window input, pool and E; evaluator invokes owning marketdata API for `[E-600,E)` with same sources/version/bindings/derivation/K. It validates returned source/request/fingerprint identity; no direct marketdata implementation imports. INSUFFICIENT includes unpriced/index/time/knowledge/membership/relevant unresolved evidence. New versions `pumpswap-pool-net-candles-v1` / `native-sol-pool-net-price-rise-v1`, separate from old native-sol-price-rise-v1. Output normalized request descriptors/source fingerprint, not copied raw secrets/payloads.

### Canonical position versus reproducible content identity

Accepted `chain-identity` keeps a canonical blockchain event locator independent of provider/parser/version; Reproducibility separately binds immutable source and transformation provenance. Accordingly, `PumpSwapVersionedPoolCandles.Operation.identity` is only the `pool-exchange-position-v1` canonical position reference over chain, signature, full outer/inner invocation path and leg ordinal. `Price.operationIdentity` and candle member IDs resolve that position to exactly one operation inside the enclosing report. They are **not** standalone content/revision/admission IDs and are not used to merge interpretations across reports or to claim persisted revision identity.

The concrete content/provenance mapping is the tuple of `Result.selectedInputFingerprint`, selected decoder in `Result.request.decoderVersion`, `Result.algorithmVersion`/`canonicalizationVersion`, `Result.pinnedSourceHashes`, the resolved operation's position/rawDigest/sourceReference/eventDigest/proofDigest and complete value fields, and the enclosing `Result.fingerprint`. The selected-input fingerprint binds sorted source records/raw digests/clocks/dispositions and pinned decoder hashes; the report fingerprint additionally binds the normalized request, complete ordered operations/values/proofs, candles, uncertainties and reconciliation. The one-interpretation-per-chain/signature and duplicate-position guards make each position unambiguous within that report. Changes to content, proof, pinned transformation or request are distinguished by their report content identity without changing the stable blockchain position reference. Physical decoder chunk packaging is excluded from both computational identities. This clarifies existing fields/accepted separation only: no new ID, acceptance rule, persistence, or code change.

### Exact files

New production:
- `src/main/java/io/cryptoresearch/marketdata/api/PumpSwapVersionedPoolCandles.java`
- `src/main/java/io/cryptoresearch/marketdata/application/CalculatePumpSwapVersionedPoolCandlesUseCase.java`
- `src/main/java/io/cryptoresearch/marketdata/application/PumpSwapVersionedPoolCandleCalculator.java`
- `src/main/java/io/cryptoresearch/marketdata/infrastructure/PumpSwapOrdinaryPoolNetProof.java`
- `src/main/java/io/cryptoresearch/signal/api/PumpSwapVersionedNativePriceSignal.java`
- `src/main/java/io/cryptoresearch/signal/application/VersionedNativeSolPriceRiseDiagnostic.java`

Additive wiring only: existing `marketdata/api/MarketDataApi.java`, `marketdata/application/RecordedMarketDataService.java`, `signal/api/SignalApi.java`, `signal/application/LiquiditySpikeSignalService.java` (full `src/main/java/io/cryptoresearch/` prefix). Preserve existing constructors through ObjectProvider lazy-field pattern.

New tests:
- `src/test/java/io/cryptoresearch/marketdata/application/PumpSwapVersionedPoolCandlesTest.java`
- `src/test/java/io/cryptoresearch/signal/application/VersionedNativeSolPriceRiseDiagnosticTest.java`
- `src/test/java/io/cryptoresearch/VersionedPoolCandleDiagnosticIT.java` (small actual API wiring/default-disabled/unchanged owned-table counts, isolated PostgreSQL18.6)

Reuse immutable existing exact real fixtures under `src/test/resources/fixtures/pumpswap-pool-legs/`; add only bounded ordinary/multi-operation source-derived synthetic fixtures within `fixtures/pumpswap-versioned-candles/` if needed. Never rewrite existing fixtures. After APPROVE, only this change/evidence and facts-only Delivery/marketdata/signal docs are assigned closure paths. No accepted-spec/archive edits.

### Verification and saved source

- Source `C:/crypto-research-evidence/saved-contabo-a7f5-20261012-27954a6225584c1e87e94b5cb3aa80da/saved-input.json`, SHA256 `0e4ca5add9855ed5808c452edd343e4138e93a2cecc717c754324f218d3160a9`:1166/224success/942failed, v5 selected171decoded/39unsupported/3nonoperation/11noPump,114new priced legs+74ordinary operations across all observed pools. New result narrows only to explicit3configured pool bindings and reports all source statuses;74 is not target-pool operation count.
- Fixed window2026-10-11T11:10:00Z..11:35:00Z, E11:20/25/30/35 on that same date for each configured GTA/RTM/Pumpswap. Preserve original25min/event clocks/gap caveats. Full selected input used, including out-of-window source dispositions. Table populated/priced bars, ordinary/multi legs, unpriced/uncertainty counts and4diagnostic statuses perpool. No positive trigger target.
- Old6h `C:/crypto-research-evidence/pumpswap-decoder-484bec54-20261010/saved-input.json`, SHA256 `2a934f9fecf1e8a8dab1ccfbe8c34e96ed1d51148d2b4b02080f688cdcdf1f00`: compatibility only, not another collection/history package. Existing old receipts/fingerprints/source files unchanged.
- Meaningful RED: compiled tests on dense>128 complete-input requirement and actual ordinary pool-net-vs-user difference/mixed price ratio; expected entire counts/exact pool net vs old refusal/wrong price. Add real multi examples plus same-pool multiple invocations/no TXdelta doubleallocation, chunk128/64/1/permutation duplicates/conflicts, quote uncertainty, missing decimals/time/index, invalid/overflow bounds, knownAt/K, unresolved target successful and failed/nonoperation controls, exact105/100 boundary and old complete-result fingerprint regression. No compile/setup failure asRED; freeze establishing meaning.
- Targeted `./mvnw.cmd "-Dtest=PumpSwapVersionedPoolCandlesTest,VersionedNativeSolPriceRiseDiagnosticTest,PumpSwapPoolLegDecoderTest,PumpSwapRawOperationDecoderTest,PumpSwapChainTimeCandlesTest,NativeSolPriceRiseDiagnosticTest" test`; isolated API `./mvnw.cmd "-Dit.test=VersionedPoolCandleDiagnosticIT,PumpSwapChainTimeCandlesIT,NativeSolPriceRiseDiagnosticIT" -DskipTests=false verify`. Logs/exits/counts preserved.
- New pure qualification receipt validates full1166dispositions/171selecteddecoded/942zero-operationfailures/39unchanged reasons, all12pairs/threepool table/common price lineage, no oldreceipt overwrite. Repeat semantic equality/global fingerprints and chunk-size independence; do not assume byte-identical JSON packaging. Reviewer checks false assumptions in pool-flow allocation, unknown close/time and source selection.
- Main complete Java gate before scoped commit: test-integrity preflight, docker version, `./mvnw.cmd clean verify`, `openspec validate --all --strict`, doctor, working/cached diff; source hashes rechecked. No push during this task. Fresh independent Reviewer before DOCS_CLOSE/Main gate, no self-approval.

## Risks / Trade-offs

- Pool-net quote includes retained fees and differs from userexecution → one explicit basis/newversion, no trader/PnL claim.
- Multiple same-vault calls or partial raw could change close → complete ledger/exclusive paths; uncertainty/unpriced makes diagnosis insufficient.
- Larger raw window misread asunbounded ingestion →4096/64MB wholelimits and unchanged128chunks, no runtime/network.
- Offline derived knownAt backdated → explicit actual derivationObservedAt/lateK/retrospective label, no invented storedrows/clocks.
- More recognized operations mistaken for completeness/edge → fixed development window, honest39unknowns/gaps and no positive trigger acceptance.

## Migration Plan

## Reviewed offline result, 2026-10-12

Implemented/targeted-qualified with fresh independent Reviewer APPROVE (`red_suspect=false`); Main full gate, independent repeat and scoped commit **PENDING**. Tasks1.1–1.3/2.1–2.2 verified,2.3 unchecked. No archive/admission/activation.

Evidence root `C:/crypto-research-evidence/pumpswap-versioned-candles-20261012-15182bf5659445e29205ca9c6929eba0`: `build-handoff.md`, `behavioral-red.md`, `unit-red-03.log/.exit`, `ordinary-target-correction.md`/RED receipt, `targeted-final-02.log/.exit`, `qualification-final-02.json`, `final-validation.json/.log/.exit`, `old124-compatibility.json`, `native-review-main-record.md`. Actual validator is `validate-final.cjs`; the handoff's `final-validation.cjs` typo is disclosed, not a replaced/missing receipt. RED03 executed203tests/24expected assertion failures/0errors/skips,177old tests PASS; RED01/02 setup errors are not RED. `tests_changed_after_red=true`: actual foreign buy+target sell fixture required full foreign retention; exact target vault decrease350476lamports differs from user quote347841. Source-driven correction and new meaningful regression independently approved; original raw unchanged.

Final targeted GREEN **204unit+10isolatedPostgreSQL18.6**,0failures/errors/skips,exit0,53.470s: new APIs/default-off/no-write/constructor compatibility and old regressions.14 immutable pins,10source/8executed class hashes validated. Old124 exact complete fingerprint remains `sha256:c3466b6cf3418277cc230490977fd8284b9720974936d5d1e33430029b3cd792` (47DECODED/49FAILED/68operations), not a new6h analysis.

Final receipt SHA256 **`8c71e1d3a5ed325f111f81df752c05284bfa2ff7b44fd4a9a9844bc8855651b6`**, DOCS_CLOSE checked. Report fingerprint `sha256:2d47f7b5ce10ed165fc14112e93fb09e8f937c93e6990cf70baeff3d20cff2d7`; original source SHA `0e4ca5add9855ed5808c452edd343e4138e93a2cecc717c754324f218d3160a9` unchanged. Actual D=`2026-10-12T13:36:44.964583Z`, K2030: retrospective. All1166/224successful/942failed retained;171DECODED/39UNSUPPORTED/3NON_OPERATION/11NO_PUMPSWAP/942FAILED.188 confirmed references=74ordinary+114multi-hop,171target/in-window/priced and17foreign OUTSIDE_TARGET; operation counts are not different-TX counts.39rare reasons unchanged.

| Pool | Target operations | Ordinary / multi-hop | Priced / unpriced | Populated / priced bars | TRIGGER / NO_TRIGGER / INSUFFICIENT |
|---|---:|---:|---:|---:|---:|
| GTA |167|53 /114|167 /0|5 /5|0 /0 /4|
| RTM |2|2 /0|2 /0|1 /1|0 /0 /4|
| FDtu |2|2 /0|2 /0|1 /1|0 /0 /4|

Full fixed25min window yields7priced bars and all12pairs including dense721/1137inputs. GTA38unresolved successful target-touch facts taint all4pairs: `UNRESOLVED_POOL_EVIDENCE`. RTM/FDtu each4`MISSING_CURRENT_BUCKET`,3also`MISSING_PRIOR_BUCKET` (overlapping reasons). Not12trading opportunities/no-edge proof; recognized prices do not prove a clean close. Builder complete-result/chunk128/64/1/reversed-input/pair repeats PASS; provisional qualification01 predates last duplicate-byte preflight correction, final02 is authoritative. Main independent repeat still PENDING; no byte-identical packaging/full-gate PASS claim.

Requirement/scenario evidence: selected complete input→dense/chunk/duplicate tests and1166dispositions; exact price→real ordinary vault/user difference and exclusive same-vault flows; event/knowledge/order→raw clock/index/D/K tests; unresolved evidence→failed/non-operation controls+12honest results; native rule→105/100 boundary/old fingerprint/API tests; saved qualification→pinned final receipt/validator/three-pool table. Fresh Reviewer independently checked those assumptions, evidence and fixture correction, not only author summary.

Следующее разрешённое действие: Main full gate, independent repeat/source checks and scoped local commit. Subsequent authorized runtime phase is separate; no server/provider/capture/spend/push by this offline result.

## Migration boundary

No persistence migration or activation; add opt-in APIs and retain old endpoints. Rollback stops calling the new route. Completion is verified offline table/evidence, fresh review, full Main gate and local scoped commit; archive and subsequent runtime work remain separate.
