# R1 D1 field inventory

## Status and controlling sources

Document checked 2026-10-03. The active [D1 change](../../openspec/changes/establish-r1-d1-data-gate/proposal.md) preserves the verified offline inventory and separately authorized exploratory probe under design 4f and [procedure section 11](R1_OFFLINE_RESEARCH_PROCEDURE.md#11-exploratory-probe--owner-authorized-2026-10-03). The [actual exploratory receipt](r1-receipts/2026-10-03-exploratory-sqd-v1.md) records a USD-0 incomplete sample and successful offline integrity replay. Frozen inventory/D1 prerequisites below remain unchanged; no inventory field is promoted and no measured D1 pass is claimed. Earlier spike receipts retain their original limited scope.

The machine-readable input is [inventory-candidates.json](../../tools/research/r1/inventory-candidates.json). Its closed schema and canonical encoding are defined by the active [design](../../openspec/changes/establish-r1-d1-data-gate/design.md) and [delta spec](../../openspec/changes/establish-r1-d1-data-gate/specs/r1-data-inventory/spec.md). Use the [runbook](R1_D1_RUNBOOK.md) to validate it locally.

| Item | Current value |
|---|---|
| Schema | `r1-d1-inventory-v1` |
| Canonicalization | `r1-d1-inventory-c14n-v1` |
| Protocol / freeze entry | `1.0.0` / numeric `1` |
| Required UTC envelope | `[2026-04-01T00:00:00Z, 2026-09-29T04:02:00Z)` |
| Candidate / selected sources | 4 / 0, verified by Builder |
| Classification / CLI exit | `INVENTORY_BLOCKED` / `2`, verified by Builder |
| Default report | `r1-d1-inventory-report-v2`: accounting/source admission is separate from measured sufficiency |
| Accounting / availability | `accountingComplete = false` / `DECLARED_INCOMPLETE_UNMEASURED` |
| Admission/accounting blockers | 5: four `FIELD_ACCOUNTING_INCOMPLETE` plus `SOURCE_SELECTION_MISSING` |
| Availability diagnostics | All67 retained, deterministically ordered; no field shortfall is erased |
| Selected-source upper bound | `"0"` micro-USD: the sum over zero selected sources; no D1 cost estimate exists |
| Authorization / measured D1 pass | `runAuthorized = false`, `d1Passed = false` |
| Inventory fingerprint | `sha256:f9f2fb5450ce37e923892979e5b6a180e7e82fcbfe25ef44c7362fb2c972888f` |

The initial two-candidate snapshot passed local validation with exit 2, 67 blockers and fingerprint `sha256:dea0c1610d91856990ccd0c7d45eb1943efb275410c8f647ce0df5353f1851f1`. That is historical section 1 evidence, not the corrected input's identity. The fingerprint includes unselected candidates, annotations and dated references; it does not identify an extracted dataset or prove source truth. Recompute the report after input edits.

Design4h's reviewed default correction preserves input schema/canonicalization/candidate bytes and fingerprint. All12 fields must explicitly account for source references, unknown dates/granularity and unavailable reasons. Accounting completion does not require fabricated measured coverage, but `INVENTORY_COMPLETE` still requires selected-source version/evidence/retention/exact-cost guards. The current candidate's unaccounted fields are liquidity-events, sol-usd, tips and visibility-latency; no source is selected. All seven availability-code families remain diagnostics, and frozen R1 8.2/8.3 alone judge sufficiency. C-3's60-second `MODELED` default is not observed visibility.

4h verification: five new behavioral assertion failures established RED, then five new plus80 existing tests passed with fail0/skip0. The current Reviewer authorized exactly seven inventory and one candidate expectation corrections before edit with `requires_new_red=true`; `tests_changed_after_red=true` records only those authorized corrections, and no test changed after the new freeze. Fresh independent full-CI review APPROVE/red_suspect=false still covers this independent correction. Tasks1.19–1.22 are verified: Main's complete gate passed integrity/Docker/Maven96unit+76IT (fail0/error0/skip0,01:40min), Node100/100 (fail0/skip0), strict15/15, doctor and diff/cached-diff, all exit0. Logs/exits remain under `C:\crypto-research-evidence\r1-d1-offline-batch-gate-20261003\main-{integrity,docker,maven,node,strict,doctor,diff,cached-diff}.log/.exit`. The subsequent mapper-specific blocker does not invalidate4h; no source promotion, measured D1 pass or archive.

## Offline PumpSwap mapping dependency — stopped, unapproved checkpoint

Final delegated peer acknowledgment `83f0dbd0-5fae-4c73-abe4-422825bd4b20` attributes STOP to the contradiction in its f682 decision (unchanged eight tests versus primitive err→UNKNOWN), not a new code defect after a last attempt or universal disproof of the mapper. The known missing-err admission blocker remains unresolved; any future mapping revisit requires a separate owner-approved task, not current repair permission.

Design4j's separate research-only [mapper](../../tools/research/r1/pumpswap-mapper.cjs) is stopped `BLOCKED / UNAPPROVED`, with source/tests/logs retained as a checkpoint, not a passed dependency. Its implemented scope covers declared trader/token/pool-vault roles, transaction-owned token deltas and classic-SPL transfers, but unresolved transaction-error admission prevents completion. Pinned upstream IDL/SPL sources remain documentary references, not deployed-program proof; applicability remains `DECLARED_UNVERIFIED`. No field status, candidate input, source admission or D1 authorization is promoted.

The one earlier authorized task-local repair established two behavioral RED failures for accepted Mainnet CAIP2 and ambiguous/null-signature ordering, then GREEN8/8 mapper tests and100/100 total Node tests. The prior full-CI APPROVE and passing gate do not support final mapper closure: missing `transactions.err` is admitted and `undefined !== null` fabricates `FAILED_TRANSACTION`. Before any last-exception edit/new RED, Builder found the frozen test at line68 expects primitive err='synthetic' to mean FAILED_TRANSACTION, contradicting delegated owner f682's primitive→UNKNOWN rule and simultaneous requirement to preserve all eight tests. Main applied that decision's terminal condition: STOP4j, no further repair, TEST_SPEC_ERROR permission request, peer return or semantic replanning. Zero last-exception code/test edits and zero new RED occurred; the conditional final-code004 smoke was NOT RUN because Reviewer APPROVE was unsatisfied. Earlier authorized chain/permutation test-change evidence and288-added/two-path checkpoint remain retained, not final approval.

Main executed the sole admitted v1 `004.raw` development smoke once, exit0:537582 bytes, original raw SHA-256 `c1595f12107d76dd511df1e12dc4189868d434b73038ce025ff013a02e1b602f` and manifest hash unchanged, slot410195947/timestamp1775001600. Historical pre-fix result `OFFLINE_MAPPING_PARTIAL` contains101 invocation records (not verified swaps),472 transfer records,60 owned-delta groups and568 diagnostics; facts hash `sha256:1291827356fe73204626ed96f5def6f5437f08e8848377c4fba55e3aacaaaeab`. Its failed labels are checkpoint output, not universally verified transaction failures, given the unresolved err guard. Original full output/exit remain at `C:\crypto-research-evidence\r1-d1-offline-batch-gate-20261003\main-mapper-004-smoke.log` / `main-mapper-004-smoke.exit`; no final-code report exists. This is `OFFLINE_DEVELOPMENT`, not D1 evidence; all three evidence/pass/authorization flags remain false. Task1.28 records historical initial RED only, not final contract verification;1.29/1.30 stay unchecked.4h1.19–1.22 remain independently complete. No provider call, deployment proof, price/outcome reconstruction or archive follows; Helius planning waits for batch closure/separate commits/clean tree.

## Evidence and candidate sources

Official pages and retained local evidence were checked on 2026-10-01. The initial input's reference time was `2026-10-01T09:43:29Z`; new references record their actual check time. Retrieval time is not a source/query/export version. Source/query versions and cost bounds remain unknown, all sources remain unselected and retention remains `UNVERIFIED`. Dated terms are documentary versions, not dataset or query versions.

| Candidate | Official documentary evidence | What remains unresolved |
|---|---|---|
| `dune-solana-dex` | [Dune Solana DEX schema](https://docs.dune.com/data-catalog/curated/dex-trades/solana/solana-dex-trades) describes route segments, vault addresses, transaction/instruction positions and UTC block timestamps. Raw quantities use `UINT256`; display quantities and USD fields use `DOUBLE`. | Dune is not connected. Full-envelope coverage, grouping, reserve history and exact pricing remain unverified. Display fields cannot supply authoritative exact financial inputs. |
| `sqd-solana-portal` | [SQD Solana schema](https://docs.sqd.dev/en/data/solana/solana-mainnet) documents Portal genesis history and transaction/instruction/pre-post balance fields; legacy v2 retains about 30 days. Retained historical samples were independently reconciled as described below. | Full-envelope completeness, venue reconstruction, historical state and visibility remain unverified. Balance changes alone do not establish transfers, tips, exact fee decomposition or freeze transitions. |
| `alchemy-solana-account-archive` | [Alchemy Account Archive](https://www.alchemy.com/docs/solana/account-archive) documents history since July 2025, no pruning and `getAccountInfo` at or before a requested slot. The retained paid B matrix collected 12/12 account reads. | Formal S3 remains unfulfilled. One sampled pool/vault matrix is not full historical pool state, validated CLMM ticks, mint transitions or executable entry/exit coverage. |
| `solana-public-rpc` | Official [getTransaction](https://solana.com/docs/rpc/http/gettransaction) and [getBlock](https://solana.com/docs/rpc/http/getblock) describe raw receipt interfaces. Existing independent public RPC receipts reconcile measured SQD samples. | Endpoint/query version, repeatable historical access limits and the required 200 venue/month-stratified trades remain unverified. RPC receipts do not supply historical account bytes by themselves. |

[Dune SQL API terms](https://dune.com/sql-api-terms), dated 2026-05-13, describe export data-point accounting. They do not establish the intended complete-envelope cost or permission to retain this research export. Those prerequisites stay unverified.

[SQD pricing](https://docs.sqd.dev/en/portal/pricing), checked 2026-10-01, publishes a USD 99/month Starter plan but says billing is not live and paid plans use early access. That published price is neither confirmed available access nor a complete D1 upper bound. Applicable access and local-retention terms must be verified before source admission. Published plans and prices can change.

### Retained measured scope

The [provider research note](../notes/SOLANA_PROVIDER_SPIKE_RESEARCH_2026-09-27.md), especially S2/S3, Research disposition and later A/B results, distinguishes earlier incomplete formal stages from later receipts:

- Alchemy B: one PumpSwap pool and two vaults at slots `429644638` and `429644639`, repeated twice: 12 collected reads, six matching repeat comparisons and matching requested context slots. Vault balances reconcile with the sampled child transaction's independent SQD/public RPC receipts. Pool layout remained undecodable/incomplete; B outcome was `INCONCLUSIVE`. This supersedes the old absence of historical receipts, without passing formal S3/F1 or D1.
- SQD/public RPC: historical samples reconcile full signatures, transaction metadata, flattened instructions and balance facts. Filtered ordinals differ in some samples; canonical joins use full signatures. The ten-slot old Raydium tail has header/parent checks but lacks a full transaction-payload cross-check. Recent PumpSwap HTTP 529 and full-envelope gaps remain unresolved.
- Local setting names establish configured Alchemy settings and a Helius key name, without exposing values. B establishes working Alchemy access for its September matrix; current Helius usability/entitlement was not tested. Prior PAYG dashboard values and locally estimated spike debit do not establish a current invoice or D1 cost forecast.

Read-only SHA-256 checks matched the note's B `raw.bin`/`summary.json`, sampled SQD `006.raw` and independent RPC `002.raw`. The exact paths and digests are retained in active design section 4a; no receipts were rewritten and no new provider call was made.

### Local export-retention admission

| Source / checked publication | Observation and unresolved permission |
|---|---|
| [Alchemy terms](https://legal.alchemy.com/), 2025-06-27 | Internal licensed use and authorized service downloads are described. The inspected terms do not explicitly establish local RPC-export retention for this reproduction use; applicable account/order terms remain unverified. |
| [SQD Cloud terms](https://cloud.sqd.dev/terms.pdf), undated publication | A software-use license is described. Applicability to public Portal and permission to retain exported chain data remain unverified. |
| [Helius terms](https://www.helius.dev/terms), 2026-09-28 | Subscription features and documentation copies are described. Persisted RPC-export permission and current plan entitlement remain unverified; Helius is an unselected alternative, not one of the four input candidates. |

These are observations from official pages retrieved 2026-10-01, not a conclusion that retention is forbidden. Provider privacy retention and customer-submitted-data ownership clauses do not establish the required permission for retained chain-data exports. Keep `UNVERIFIED` until applicable evidence resolves it.

### Helius H2 exploratory disposition — 2026-10-03

The [actual H3/H4 receipt](r1-receipts/2026-10-03-helius-h3.md) records one EXPLORATORY/INCOMPLETE/RECEIVED_LIMIT run:305527unique transactions,75complete/33censored streams,13fully completed/23censored addresses and valid partial replay/H4 with matching hash. Time-budget PASS is not data sufficiency; completed-address sensitivities are censor-biased/not population bounds, actual H3 credit debit remains unknown and fullD1UpperBound=null. This supersedes historical H3-prelaunch status below, without changing candidate selection, field availability, retentionUNVERIFIED, false flags/fingerprints or section2.

The [H1 receipt](r1-receipts/2026-10-03-helius-h1.md) records the one completed Free entitlement page after independent APPROVE/Main complete gate:1call/1000signatures,979success/21failed/0unknown,224743received bytes,232491retained bytes. Cursor observed, history incomplete; no full transaction/coverage/field promotion. Replay exit0/same semantic hash; the later direct owner dashboard text (recorded11:51:04UTC) reconciles account debit10/remaining999990Free, not an invoice or autoscaling inspection. Historical immutable manifest/summary actualCredits=null/hashes stay unchanged. Dashboard admission is now resolved, but H3 implementation/review/gate/preflight remains required; H2 rights remainUNVERIFIED. No existing candidate input/fingerprint/false flag is changed.

H5's separate [Helius candidate snapshot](../../tools/research/r1/inventory-helius-candidates.json) and [synthetic test](../../tools/research/r1/test/inventory-helius.test.cjs) received fresh independent combined APPROVE; Main's complete H5 gate passed integrity/Docker/Maven172/Node111/strict15/doctor/bothdiffs, all8exits0 (fixed H1 gate directory h5-main-* logs/exits).207/450added lines/two new paths,3behavioral RED failures→GREEN3/3 and111/111 total verify task1.37. Its unselected documentary candidate yields exit2/INVENTORY_BLOCKED,67availability diagnostics/5accounting-source blockers and fingerprint `sha256:5c7a940b19c84a1d9ccfb99366b27c6f01fd9e2bc849c4b73063b91c88e7f4b3`; no CONFIRMED field, coverage/source/retention admission or true D1/run flag. The old f9f2 four-source input and frozen tests remain unchanged. Vault pre/post balances are documentary reserve inputs only, no CLMM ticks/DLMM bins; exact base/priority split remains unresolved. Prospective200checks are not a run allocation.

[Helius Terms](https://www.helius.dev/terms), publication2026-09-28, were rechecked2026-10-03 for [design4k](../../openspec/changes/establish-r1-d1-data-gate/design.md#4k-helius-free-wallet-history-source--bounded-future-h1h6). Persisted chain-export rights/account applicability remain `UNVERIFIED`, not confirmed or forbidden. Delegated decision `e928ceb5-6768-45d9-a454-e26ef87f78ed` permits bounded local H1/H3 EXPLORATORY raw retention only: fixed external roots,14days after each run's UTC end, expiry in manifest/receipt, USD0, no resale/publication, holdout or secrets. The [procedure section11 amendment](R1_OFFLINE_RESEARCH_PROCEDURE.md#helius-free-access-amendment--2026-10-03) preserves CORE_RISK RED/freeze/fresh full-CI review/Main complete gate; it grants no legal OD-2 retention confirmation, measured entitlement, source selection, field promotion or D1 evidence. No Helius call or credential read occurred in this documentation work. Old four-source input/f9f2 fingerprint and all availability diagnostics/false flags remain unchanged; H5's separate unselected candidate snapshot is not yet established by this note.

## Required fields

Every row's historical coverage is unknown (`coveredFrom = coveredTo = null`). All rows have known unresolved gaps. `DOCUMENTED` records a capability described by a reference. `UNVERIFIED` records an unresolved prerequisite. `CONFIRMED`, when later used, means an author-supplied, evidenced assertion for independent review; the validator checks its structure, not measured availability or source truth. No initial row is `CONFIRMED` or `UNAVAILABLE`.

| Field ID | Status | Candidate mapping and remaining evidence |
|---|---|---|
| `trade-legs` | `DOCUMENTED` | Dune/SQD/public RPC candidates; sampled raw agreement exists, but full-envelope grouping/reconstruction remains unmeasured. |
| `spl-transfers` | `UNVERIFIED` | SQD/public RPC candidates; explicit transfer decoding and envelope completeness unverified. |
| `sol-transfers` | `UNVERIFIED` | SQD/public RPC candidates; individual transfer and fee/tip attribution unverified. |
| `base-priority-fees` | `UNVERIFIED` | SQD/public RPC candidates; exact fee separation unverified. |
| `tips` | `UNVERIFIED` | Independent tip accounts/attribution source unidentified. |
| `reserves-depth` | `UNVERIFIED` | SQD/public RPC vault-account pre/post transaction balances are the planned reserve-input route; Alchemy is retained prior evidence with new PAYG calls denied. Historical CLMM ticks/DLMM bins, curve state and complete trigger coverage remain unverified. |
| `liquidity-events` | `UNVERIFIED` | Venue-specific addition/removal decoding unresolved. |
| `mint-decimals-freeze` | `UNVERIFIED` | Alchemy/SQD candidates; authoritative mint decoding and historical freeze transitions unresolved. |
| `executable-entry-exit` | `UNVERIFIED` | Alchemy/SQD candidates for state inputs; executable pricing and loss-of-exit reconstruction remain unverified. |
| `sol-usd` | `UNVERIFIED` | Exact historical SOL/USD source unidentified. |
| `block-time` | `DOCUMENTED` | Dune/SQD/public RPC candidates; sampled header agreement exists, not full-envelope timestamp completeness. |
| `visibility-latency` | `UNVERIFIED` | Measured source visibility latency unavailable; no live receipt observations. |

The corrected report retains 67 blockers: unconfirmed fields, absent selected suppliers, incomplete coverage, gaps, missing granularity/evidence and unknown documentary versions. Source-admission blockers apply when a source is selected; unselected-source costs and permissions remain visibly unknown rather than becoming zero-cost claims. Public RPC is now an evidenced independent receipt candidate, not proof of the complete 200-trade check.

## Confirmatory next decision

The owner selected `C:\crypto-research-evidence\r1-d1`; the exploratory child now exists as recorded in the receipt. C: had `124262658048` free bytes during the earlier inspection; this is historical, not a current free-space guarantee. No path decision remains outstanding. On 2026-10-01 the owner explicitly declined Alchemy PAYG D1 spending: "Не разрешаю пока". No new PAYG call or paid-run PLAN_READY is permitted until a future explicit owner decision. Earlier A/B authorizations are exhausted.

Offline candidate correction (tasks 1.7–1.10) is verified, including behavioral RED, unchanged frozen tests, fresh APPROVE and Main's gate. Confirmatory source admission remains blocked; the distinct exploratory authorization is stated below. A later confirmatory source-admission PLAN must resolve applicable retention, selected source/query versions, zero-paid access, exact filtered population/query/sample definitions and complete workload bounds before its implementation or source run.

Active design sections 4c/4e restrict the future sample solely to volume/cost feasibility under R1 8.1: at most eighteen fixed filtered windows across six months/three frozen pricing forms and eighteen receipts for size accounting, zero new Alchemy calls, at most 18 public RPC and 18 SQD attempts including retries; 64 MiB received, 100 MiB evidence, two hours and USD 0 cash. These pending ceilings do not authorize a run. The previous paid-account/USD 3 draft is withdrawn. Existing historical receipts may be reused read-only; no paid fallback or unbounded expansion is permitted.

## Complete filtered-envelope estimation plan

Estimate every frozen warm-up/evaluation range and the tail through `2026-09-29T04:02:00Z` for all outcome-blind R1 5.1 candidate wallets and the relevant pools of their token universe. Do not substitute a ranked top-50 watchlist, currently surviving tokens, already supported venues or all-block throughput. A complete cohort/token/pool discovery manifest with versions, cutoff-safe identities and counts is not yet available; discovery overhead must itself be costed.

Use six calendar-month/venue strata, with a frozen filtered query/window/population manifest. Record filtered event density, response sizes, provider calls, elapsed time and observed variation; expand to the complete population/durations, accounting for duplicate selectors, ancillary transfer/clustering/mint/state/price histories, full raw-receipt checks and the extraction tail. Deduplicate semantic facts by canonical identity while charging all queried/received bytes. Document conservative finite row/request/received/retained/time/cash bounds; missing populations, strata or required fields stay unknown. Broad program sample rates and one compression ratio do not establish a filtered upper bound.

Compare the cumulative complete estimate against unchanged OD-2: USD 100, 14 days, 10 GB evidence and 30 GB minimum free. If any bound exceeds a ceiling, stop before spending/full extraction and return a concrete owner packet: population/scope, estimate/missing components, ceiling/excess, required budget or proposed narrower experiment. Unknown bounds also block extraction. No numeric complete filtered estimate exists yet. PAYG denial applies even if an estimate is below USD 100; zero-paid source runs still require new PLAN_READY, review, gate and procedure preconditions.

## Transaction-vault reconstruction and depth denominator

Planned reserve inputs are exact raw vault-account balances BEFORE/AFTER the transaction, joined by full signature, vault address, mint and versioned pool mapping. Resolve RPC account indices using static plus loaded keys. Retained B/SQD/public RPC reconciliation proves these inputs for one transaction only. Trader deltas and parent/child slot states are not general transaction snapshots; multiple writes, multi-leg intermediate states, account creation/closure and missing metadata remain explicit. Post-state cannot price a pre-transaction decision. Entry/horizon/loss-of-exit evidence also needs intervening pool swaps/liquidity/state changes.

Vault totals alone do not supply CLMM tick/DLMM bin liquidity. Without required supported historical state, count those triggers as missing depth; do not remove them from the complete candidate-pool denominator. The later full gate reports missing counts/shares by venue/month/reason and applies exactly frozen R1 8.2: >=90 percent of trigger events must be on venues with available reserve/depth inputs, otherwise `INCONCLUSIVE/data insufficient`. The feasibility sample measures neither that share nor D1 passage.

## Tips, SOL/USD and visibility: next-run source disposition

The verified JSON still marks these fields `UNVERIFIED`; it is unchanged in this planning slice. For the next source-run admission, no exact supported source is established, so all three are explicitly `UNAVAILABLE` rather than silently omitted:

| Field | Documentary candidate / current missing evidence |
|---|---|
| Tips — `UNAVAILABLE` | [Jito documentation](https://docs.jito.wtf/lowlatencytxnsend/) describes tip-account transfers and `getTipAccounts`. No method was called. Historical list/version/hash, CPI/separate-transaction attribution, non-Jito limitations, coverage and terms are not admitted. Unknown tips are not zero. |
| SOL/USD — `UNAVAILABLE` | [Pyth historical-data documentation](https://docs.pyth.network/price-feeds/core/use-historical-price-data) describes timestamp queries. No source was selected or called. Exact feed/source/query version, integer mantissa/exponent, publication/availability selection, full dates/gaps, cost and retained-export rights remain missing. No display DOUBLE or after-cutoff quote may substitute. |
| Observed visibility latency — `UNAVAILABLE` | No supported historical first-visible receipt series exists. Block time and retrospective retrieval do not measure it. Frozen C-3's 60-second default remains versioned `MODELED`, never observed latency, calibration or a relaxed inventory/gate criterion. |

Primary documentary claims above were checked 2026-10-01; those pages do not establish the missing exact source admission. Machine-readable status changes need later authorized Builder allocation/review and must preserve existing frozen tests. Safe next work is offline reconciliation of already retained receipt/count metadata and exact term/query/population prerequisites; a reviewed estimator PLAN with behavioral RED is needed before new calculated R1 evidence.

Confirmatory source-specific tooling and runs require a separate updated `PLAN_READY`, behavioral RED, reviewed inventory/scripts and Main's mandatory complete gate. No source is selected in the inventory by this document. A sample cannot replace the frozen full-envelope 90 percent availability thresholds, 200-trade/95 percent/zero-double-count cross-check, venue gap limit, provenance or rerun hashes. If only a sample is completed, D1 remains `INCONCLUSIVE`. Section 2 stays incomplete; offline correction cannot close or archive D1.

## Exploratory terminal evidence — owner-authorized 2026-10-03

The [design4g v2](../../openspec/changes/establish-r1-d1-data-gate/design.md#4g-exploratory-v2--continuous-lightweight-windows-and-separate-rich-size-samples) tools were implemented/reviewed and Main executed the one public attempt plus matching offline replay: [v2 terminal receipt](r1-receipts/2026-10-03-exploratory-sqd-v2.md). Status is EXPLORATORY/INCOMPLETE, USD0, 70attempts/317682received bytes; all eight resolvers returned200, but three census requests and the August start header returned529. No A/B/C profiles were reached and numerical workload projections remain null; this is not zero activity, unavailable history or a D1 failure. No own cap was reached. Tasks1.15–1.17 are verified;1.18 remains unchecked. No inventory field, threshold or methodology is promoted/changed; the full-D1 bound remains unknown and v1 is preserved. Do not rerun v2 under its exhausted single-attempt authorization.

Design 4f supersedes the old pending sample ceilings only for a distinct `EXPLORATORY` sample: fixed April 1/May 1/June 1/July 1/August 1/August 28 timestamp anchors, one validated finalized slot each, Pump.fun/PumpSwap/Raydium AMM v4 program queries on the free unauthenticated SQD Portal. Public [development/bounded evaluation](https://sqd.dev/developers/) access was checked 2026-10-03; formal full-D1 retention/capacity remains unknown. No existing raw receipt is reused. The entire August 31–September 28 period is excluded from requests, raw retention and counts.

The preceding design4f selectors/ceilings describe the completed v1 attempt, not a new action. Design4g v2 used its separately reviewed 120-minute/1,000-attempt/1-GB-received/1.1-GB-disk ceilings, with no cap reached. Its four HTTP529 error bodies were discarded, leaving causes unresolved. Successful partial census headers supply no A/B/C field-availability measurement. No full-D1 bound, coverage, inventory promotion, calibration or result is inferred. The localized no-full-Maven exception does not apply to confirmatory section 2.
