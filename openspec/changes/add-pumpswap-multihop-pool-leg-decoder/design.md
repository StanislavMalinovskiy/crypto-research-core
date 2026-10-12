## Context

See [proposal](proposal.md) and [contract](specs/pumpswap-multihop-pool-leg-decoder/spec.md). Fixed v4 requires `Operation.trader`; V14 publishing, pool metrics and chain-time candles intentionally consume that exact contract. Mapping an aggregator initiator into that field would invent economic ownership. The narrow addition therefore has a separate DTO and API, not a replacement parser or storage migration.

### Planning handoff

- Phase PLAN; `tier=CORE_RISK`, `risk=CORE_RISK`, `risk_triggers=TR-07,TR-08,TR-11`; `reason=CONTRACT_CHANGED`. Exact authoritative pool quantities/price, pool-leg identity and version-qualified reproducibility are changed guarantees; arithmetic or association error could publish fabricated economic facts.
- All TR assessed: TR-01/02/03/04/05 absent (pure computation, no persistence/transaction/concurrency/retry/migration change); TR-06 absent (original clocks retained, no PIT/admission change); TR-09 absent (no provider/recovery); TR-10 absent (existing owning API/DAG); TR-12 absent (no secret/runtime access). This is not a downgrade of another change.
- `test_mode=RED_REQUIRED`: executable financial/proof change. Builder establishes meaningful expected-confirmed vs actual-v4-unsupported behavior on retained examples before implementing v5, plus assertion-level safety negatives; compilation or missing-symbol errors are not RED. A compilable fail-closed v5 test seam may return unchanged v4 dispositions for initial RED; it must not edit v4 or weaken establishing meaning.
- Owner scope: narrow single-own-event pool leg, initiator only, exact SOL or unpriced volume, new version beside immutable v4; all other39 reasons/dense candles/server/collection/spending/push excluded. Fresh independent Reviewer required; Main retains full final gate ownership. No archive permission assumed here.
- Cumulative resource budget: zero provider requests/credits/payments/managed reads or writes; retained local inputs only. Existing decoder bounds remain, bounded chunks can exhaust the 1166-TX offline window without creating a new whole-window runtime API. Full Maven uses isolated tests only. Do not activate managed profile or read secrets.
- Controlling sources: AGENTS, MULTIAGENT TR authority, Testing, Reproducibility and ADR0009, Architecture/module marketdata/ADR0004; accepted `research-reproducibility`, `chain-identity`, `module-boundaries`; active fixed-v4 decoder/persistence/metrics/chain-time contracts; actual API/parser/tests. No nearer source guide exists.
- Affected CI: CI-01 complete dispositions; CI-07 version/pins/bindings/price-basis identity; CI-08 deterministic replay; CI-10 total source/path order; CI-12 owned provider parsing; CI-13 no fabricated degradation; CI-15 inherited work/output bounds. CI-09 doubtful but unchanged: preserve clocks/labels, no historical knowledge claim. TR-07 exact arithmetic applies directly. CI-03/04/05/06/14 not changed: no durable publication, retry/transaction changes.

### Active overlap assessment

`openspec list --json` examined before readiness; no active change owns the new capability. All overlaps are **safe to proceed** by additive separate-v5 scope:

| Existing change | Boundary preserved |
|---|---|
| `add-pumpswap-raw-operation-decoder` | v4 source/API/fingerprint and multi-hop unsupported rule unchanged |
| `add-pumpswap-operation-persistence` | V14 fixed-v4 DTO/version/publisher/readback unchanged; v5 not stored here |
| `add-pumpswap-offline-pool-metrics` | receivedAt v4 metrics unchanged |
| `add-pumpswap-chain-time-signal-diagnostic` | v4 chain-time price/candles/128-source bound unchanged; no dense-window work |
| `add-pumpswap-operator-report` | no existing panels/formatters changed |
| `add-pumpswap-continuous-readiness` | fixed-v4 automatic derivation and runtime authority unchanged |
| `add-bounded-pumpswap-pool-collector`, `add-bounded-capture-monitor` | source capture/monitor/gaps/budgets untouched |
| `preregister-r1-e2-pumpswap-cohort`, `establish-r1-d1-data-gate` | paused research/methodology/evidence/admission untouched |

## Goals / Non-Goals

One observed pool exchange, not a general N-hop router, netted trader history or accepted signal input. No persistence/schema/SQL/dependency, server/provider access, deployment, new collection or old-receipt modification. The new price has intentionally different **pool-net** meaning from v4 user execution amounts; do not feed it implicitly into old candles.

## Decisions

1. **Additive API:** `MarketDataApi.decodePumpSwapPoolLegs(PumpSwapPoolLegs.Request)` delegates to `DecodePumpSwapPoolLegsUseCase`. `PumpSwapPoolLegs.Result` retains the exact original `PumpSwapOperations.Result` as `v4`, plus separate v5 transaction/invocation dispositions and `PoolLeg` records. No new leg uses `PumpSwapOperations.Operation.trader`. Existing `RecordedMarketDataService` uses the established lazy `ObjectProvider` wiring pattern, without changing existing constructors/old calls.
2. **Pure parser:** new `PumpSwapMultiHopPoolLegParser` owns bounded indexed-key/path/event/transfer parsing. Reuse existing public SHA/path helpers and call the unchanged v4 decoder; do not extract/change its private utilities. Strict duplicate JSON detection, complete resolved keys and numeric CPI paths remain required. Request binds 1–3 distinct target pools to exact base/quote vault/mint/program roles; prove those roles from raw balances and instruction membership, not from configuration alone. Known unrelated pools remain explicit unsupported scope, never silently counted as target exchange.
3. **Single event:** exact instruction bytes `2b644913e9f66f94` plus two u64 args (24 bytes), pinned fixed16 roles and same-parent self-CPI transport. Count all own Buy/Sell execution events of this invocation before selecting; >1, including duplicate identical transport, yields `MULTIPLE_OWN_EVENTS`. Decode exactly pinned Buy/Sell layouts and dynamic `ix_name` (observed buy multi-hop508 bytes, sell449), with complete end-of-frame consumption. Do not infer meaning from length alone. Observe instruction initiator role0 and event user separately; neither becomes an ultimate trader.
4. **Base proof before price:** event direction/amount must match attributed base-vault transfer net flow and actual pre/post raw amount. Both endpoints must prove pool ownership, mint/program/decimals; unsupported Token2022 hook/fee/unknown-touch semantics are not assumed classic. Any other transaction touch of a vault must be completely accounted for; otherwise attribution cannot be made. No new ATA/lifecycle repair support for the rare39 cases.
5. **Quote proof and exact price:** preserve event gross/curve/user quote facts and actual signed quote-vault delta separately. Recognized classic/Token2022 transfers touching quote vault must reconcile completely, including proved buyback payout and other attributed deductions. Complete whole-TX endpoint flow must equal source pre/post change, with attribution to this invocation (reject ambiguity rather than allocate another leg's amount). WSOL must have nine proved decimals. `poolNetQuoteLamports = abs(attributed quoteVaultDelta)` and `price = poolNetQuoteLamports * 10^baseDecimals / (baseAmount * 10^9)`, reduced by GCD, positive integers only; label `POOL_NET_SOL_PER_BASE`. No rounding needed. If quote proof is incomplete but base proof remains valid: no ratio, `PRICE_NOT_CONFIRMED` plus specific reason. If event/base proof contradicts: unsupported, not degraded success.
6. **Complete overlay and identity:** overlay only precisely matched v4 `multi_hop_swap/UNSUPPORTED_VARIANT` invocation positions. v4 non-multi invocation outcomes remain authoritative; an orphan/other unresolved invocation prevents DECODED. Pool leg identity uses chain/signature/full invocation path/leg ordinal0, with event/proof/raw digests as lineage. v5 decoder/schema versions are `pumpswap-pool-legs-v5` / `single-own-event-pool-net-sol-v1`; canonicalization `length-prefixed-v1` with a new domain, total chain/signature/provider/session and numeric-path ordering, target bindings/proof/roles/price basis/pins included. Preserve actual source clocks and original v4 fingerprint, not an amended v4 report. Price absence and reasons participate in identity. Do not claim equal fingerprints for different batch membership.

### Exact implementation files

New:
- `src/main/java/io/cryptoresearch/marketdata/api/PumpSwapPoolLegs.java`
- `src/main/java/io/cryptoresearch/marketdata/application/DecodePumpSwapPoolLegsUseCase.java`
- `src/main/java/io/cryptoresearch/marketdata/infrastructure/PumpSwapMultiHopPoolLegParser.java`
- `src/test/java/io/cryptoresearch/marketdata/application/PumpSwapPoolLegDecoderTest.java`
- `src/test/java/io/cryptoresearch/marketdata/application/PumpSwapPoolLegDecoderIT.java` (small public-API wiring/no-write test, isolated PostgreSQL only)
- `src/test/resources/fixtures/pumpswap-pool-legs/` four bounded exact recorded transaction fixtures with metadata and documentary IDL fixture; no secrets.

Changed additively:
- `src/main/java/io/cryptoresearch/marketdata/api/MarketDataApi.java`
- `src/main/java/io/cryptoresearch/marketdata/application/RecordedMarketDataService.java`

After independent APPROVE, Architect updates only this change's evidence/tasks and assigned facts-only Delivery Plan/module status. No accepted spec synchronization or archive is assumed.

### Retained evidence and proof examples

Input `C:/crypto-research-evidence/saved-contabo-a7f5-20261012-27954a6225584c1e87e94b5cb3aa80da/saved-input.json`, SHA256 `0e4ca5add9855ed5808c452edd343e4138e93a2cecc717c754324f218d3160a9`; original 1166=224 successful+942 failed, v4 fully decoded57/224. October IDL `C:/crypto-research-evidence/pumpswap-decoder-484bec54-20261010/resumed-october-idl.json`, SHA256 `b7d8c57a4d9c4dd0109a9ab893052352333252d4eedced10ac091d4d66cab89b`. Both immutable.

| Routing | Real signature | Observed quote-vault equation (lamports) |
|---|---|---|
| FLASHX8, sell | `2hoQFkPB7m89TbdUNf5SJH9MQ5z9gsFwiS9t86Z1XJWEyPuJebxqfJHSduDX6VysCMEw3f5VA5GVLcqsBmY4RxXs` | -(1219183586 user output +304948 buyback)=-1219488534 |
| 6Vo3245, buy | `4XkRY4T9gjYVfEQUx5sXov716ETqKAWqBVQ5J9XKV4QwiwvcGKmejj47NtXwm86DynJh98BpR1Ehdvg5eHTdRmYF` | 2500000000-624688=2499375312 |
| term9YP, buy | `3jUcxe4fVEYvq7qEnLTnpRf4SfKo8PxdWciEQriLGbHEG7rzEygGmnx7NHaTroSfZmUmvucEeJicKHuxb3u6wWKV` | 499950000-124925=499825075 |
| Direct, buy | `2ZUcg9YBVsUarEHeLCWABNbGEKLL9GBoze7TM5yA2uGja5qZuANuXDSzCgTupG6GyUDYPmvXj6QwApwp7FxEsED2` | 1750000000-437281=1749562719 |

These are observed equations to prove with raw/event/complete transfer endpoints in tests, not hard-coded parser truth or guaranteed prices for all114. Full signatures and raw digests survive fixture extraction. Three target pools remain GTA `5iSVs31h7HMdGGmyUvzmHDsYwV7cydD6CZJTbqzCtLii`, RTM `EBNUexqpB5WjKXJsfm3ruFt7zb7ZbXHZGR7YdhYuvuyy`, PumpSwap `FDtu6W24K2iyW9Ce9NsTyVSmSGjxj2hHrTkKc2xFTxSL`; no new pool enrollment.

### Verification

- RED/GREEN: `./mvnw.cmd -Dtest=PumpSwapPoolLegDecoderTest,PumpSwapRawOperationDecoderTest test`; real all4 routing cases, exact base/price equations and unchanged v4 assertions. Add negatives before implementation: multiple identical/conflicting own events, wrong parent/program/user/pool roles, malformed bytes, bad digest/identity, failed TX, missing quote endpoints/decimals/unknown touch, conflicting base endpoints, amount/fee mismatch, repeated/permuted input, limits and unresolved second invocation.
- Scoped API/PG: `./mvnw.cmd -Dit.test=PumpSwapPoolLegDecoderIT,PumpSwapRawOperationDecoderIT,PumpSwapOperationPersistenceIT,PumpSwapChainTimeCandlesIT -DskipTests=false verify` with isolated local PostgreSQL; verify no new persistent rows or v4 consumer change.
- Offline full saved qualification in deterministic <=128-source chunks through the pure API, fresh output under `C:/crypto-research-evidence/pumpswap-multihop-20261012-ac993dd6d57946f2b259ede3fb03e7ec`; no server access. Reconcile 1166/224/942, repeat exact results; report actual v5 fully-decoded percentage vs57/224=25.45%, priced/unpriced legs, all114attempts and remaining39reasons. Do not promise171/224 before evidence.
- Fresh independent review checks assumptions and proof equations, not merely counts; no self-approval. Builder targeted checks do not replace Main full gate.
- Before Java commit Main runs integrity preflight, Docker preflight, `./mvnw.cmd clean verify`, `openspec validate --all --strict`, `openspec doctor`, `git diff --check` and `git diff --cached --check`. Save exact logs/exits. No push.

## Risks / Trade-offs

- Event user/gross amount mistaken for final trader/price → distinct DTO fields, exact pool-net label, absent ultimate trader and independent proof negatives.
- Single-event confirmation mistaken for complete route → no route/user/PnL/fees/coverage claim; multiple own events unsupported.
- Transaction-wide delta allocated to one invocation incorrectly → complete vault ledger and source endpoints, reject unknown touches; price absent where proof incomplete.
- Duplicated new parser helpers drift → limited new logic and pinned fixtures; immutable v4 regressions, no shared-helper refactor in this scope.
- More decoded TX mistaken for better candles/signals → new pure report only; old V14/candles untouched and admission remains excluded.

## Migration Plan

No schema/runtime activation. Add opt-in API; existing consumers continue v4. Rollback stops calling the new API without changing prior rows or receipts. Acceptance is offline confirmation/report plus independent review and applicable final checks, not deployment or archive.

## Final verification 2026-10-12

Fresh independent Reviewer **APPROVE** after one blocking discriminator repair, `red_suspect=false`; implementation/targeted verification and tasks1.1–1.3/2.1–2.2 verified. Main full gate and independent offline repeat **PASS**:652tests(358unit+294integration),0failures/errors/skips, exit0,18m44s. Integrity/Docker/all-item strict/doctor/working+prestage cached diff exit0; checked-source inputs unchanged. Scoped commit **PENDING**; composite2.3 stays unchecked until the actual commit. Active/unarchived, not accepted/admitted or deployed.

| Same immutable window | Fixed v4 | Additive v5 |
|---|---:|---:|
| Raw TX / explicit successes / failures |1166 /224 /942|unchanged|
| Fully decoded successful TX |57/224 =25.45%|171/224 =76.34%|
| Successful TX unsupported |153|39|
| New confirmed target-pool legs |0|114|
| New priced / unpriced legs |0 /0|114 /0|
| Original ordinary v4 operations |74|74, preserved separately|
| NON_OPERATION / NO_PUMPSWAP |3 /11|unchanged|

All114 prior unknown variants are actually confirmed, not a forced target. All942failed sources emit no operations/legs. The39rare reasons remain19 `INVOCATION_LAYOUT`,11 `ATA_CREATION_UNPROVED`,6 `IMMUTABLE_TOUCH_UNPROVED`,2 `TOKEN_ENDPOINT_UNPROVED`,1 `IMMUTABLE_CLOSE_UNPROVED`. The denominator224 is successful TX, not a proven swap denominator; actual chain coverage/trader/fees/signal readiness remain unqualified.

New records separately retain observed initiator/event user; ultimateTrader is absent/UNDETERMINED. The event SYSTEM base-account placeholder is not a trader account; actual intermediate counterpart is proven from observed hop roles/token state. Exact WSOL9 quote-vault delta, including observed buyback, yields GCD-reduced `POOL_NET_SOL_PER_BASE`, not a final-user price. Missing quote proof has a tested confirmed-base/PRICE_NOT_CONFIRMED branch, although all114 actual new legs are priced. Multiple own events remain unsupported. No implicit V14/old-candle consumer integration or rare39 repairs.

### Evidence and requirement mapping

Evidence root: `C:/crypto-research-evidence/pumpswap-multihop-20261012-ac993dd6d57946f2b259ede3fb03e7ec`.

- `qualification-final-02.json`, SHA256 `1485589f3b4cc10f75613afc1fc60217136199a608019eb5bf877530e9c05712`, exit0; source SHA256 `0e4ca5add9855ed5808c452edd343e4138e93a2cecc717c754324f218d3160a9`. All1166rows,10bounded chunks, actual pins/source hashes/repeat/permutation equality retained. Old raw/receipts untouched.
- Pure `PoolLegQualification.java`, SHA256 `45a7283f31ce4c7d0611cf34ef95db051158b6ba2377f13b38e9a158deca5c64`; `VerifyPoolLegQualification.cjs`/`qualification-validation-final.log/.exit=0` compare original dispositions,114exact ratios,39unchanged reasons and old/source hashes. Main repeat uses fresh CREATE_NEW output, no DB/network/secrets.
- `behavioral-red.md`/`unit-red-01.log/.exit`:141tests/16expected assertion failures/0errors/skips; old122PASS. Real all4routes expected confirmed vs unsupported; unpriced branch expected one leg vs zero. Compilation/setup not RED.
- `fixture-correction.md`/`fixture-correction-red.log/.exit`: proven synthetic duplicate-event placement fixed to the same parent, preserving MULTIPLE_OWN_EVENTS assertion;1test/1expected failure/0errors/skips then GREEN. `tests_changed_after_red=true` records the correction and stricter regressions; original raw unchanged.
- `discriminator-repair-red.md`/`discriminator-red.log/.exit`: independent R1 found unknown non-Sell frame admitted as Buy;5tests/2expected failures/0errors/skips then exact pinned Buy/Sell discriminator gate and targeted rerun. Prior receipts retained.
- `targeted-final-02.log/.exit=0`: `./mvnw.cmd "-Dtest=PumpSwapPoolLegDecoderTest,PumpSwapRawOperationDecoderTest" "-Dit.test=PumpSwapPoolLegDecoderIT,PumpSwapRawOperationDecoderIT,PumpSwapOperationPersistenceIT,PumpSwapChainTimeCandlesIT" -DskipTests=false verify`;151unit(29new+122old),22PG(1newAPI+1olddecoder+13persistence+7candles), all0failures/errors/skips. Disposable PostgreSQL18.6, unchanged whole-owned-table counts and v4 consumers.
- `native-review-final.md` records native independent final Reviewer APPROVE, independently checked retained fixture/source hashes, exact vault equations/ratios, dispositions and v4 equality; `red_suspect=false`. Exact stable code/test hashes are in `build-handoff.md`. Its earlier final-review-pending sentence is Builder handoff history, superseded only by the received independent approval.
- Main final logs: `main-full-maven-01.log/.exit=0` and `main-test-counts.log` verify652tests/0failures/errors/skips,18m44s; `main-integrity`, `main-docker`, `main-openspec-strict`, `main-doctor`, `main-working-diff`, `main-cached-diff-prestage` exits0. `main-input-comparison.log/.exit=0` proves checked source inputs unchanged. These are full Main gate evidence, not a relabeled targeted run.
- Main fresh `qualification-main-03.json`, SHA256 `e0012641e50e4d620689353669659e9129adf4d4eec0aaf3bae33ad46b66c1e5`, pure harness/validator exits0, reproduces all counts/proofs. The preserved first raw-byte comparison exited1: JSON object-key order differs only within the10chunk pins maps across JVMs. Complete parsed-object deep equality, including all1166rows/pins/proofs/fingerprints, passes in `main-repeat-semantic-comparison.log/.exit=0`; common sorted semantic SHA256 `4229a95768a00fb1e4036b69be230d8a891bb2dfa7f569b431de4ff3a90e820c`. Original byte receipts and the mismatch remain preserved; no raw-byte equality claim or receipt rewrite. Native Reviewer independently confirms computational reproducibility and no blocker, `red_suspect=false`.

| Requirement | Verified evidence |
|---|---|
| Additive bounded offline version | digest/identity/bounds/permutation unit tests; selective-context/public-API IT; unchanged nestedv4 equality on10chunks |
| One own same-invocation event | four `realRoutingExamplesConfirmExactPoolNetAndPreserveV4` cases; exact discriminator RED/GREEN; same-parent duplicate/conflicting event negatives |
| Initiator is not ultimate trader | real fixtures retain initiator/event user while ultimateTrader absent/UNDETERMINED |
| Exact pool-net SOL or unpriced volume | four real vault equations,114ratio checks and `quoteProofFaultsKeepProvenBaseWithoutPrice` |
| Complete successful-TX coverage |1166status reconciliation,57→171over224,942failed/zero legs, partial-second-invocation test and39unchanged reason identities |

Affected/doubtful CI-01/07/08/09/10/12/13/15 and TR-07 retain planning applicability. Review's assumption question: could one event/gross quantity prove the executed quote amount or ultimate trader? Actual complete transfer/vault proof, separate pool-net fields and unknown ultimateTrader prevent that inference. Targeted GREEN alone was not Main full-gate PASS; the separate full-gate evidence above now establishes it.

Следующее разрешённое действие: scoped commit without push after final staged/docs checks. Any later v5 candle consumer needs a bounded new contract; dense-window candles and39rare cases deferred. No collector/server/read/spend/activation/archive/R1 authority.
