# R1 D1 field inventory

## Status and controlling sources

Document checked 2026-10-01. The active [D1 change](../../openspec/changes/establish-r1-d1-data-gate/proposal.md) now allocates an offline correction to include retained provider evidence, not a source run or measured D1 pass. The [frozen protocol](R1_RESEARCH_PROTOCOL.md) `1.0.0`, [freeze record](R1_PROTOCOL_FREEZE.md) entry 1 and [offline procedure](R1_OFFLINE_RESEARCH_PROCEDURE.md) remain controlling. No new provider query, schema probe or extraction has run in this slice. Earlier spike receipts are described below with their limited measured scope.

The machine-readable input is [inventory-candidates.json](../../tools/research/r1/inventory-candidates.json). Its closed schema and canonical encoding are defined by the active [design](../../openspec/changes/establish-r1-d1-data-gate/design.md) and [delta spec](../../openspec/changes/establish-r1-d1-data-gate/specs/r1-data-inventory/spec.md). Use the [runbook](R1_D1_RUNBOOK.md) to validate it locally.

| Item | Current value |
|---|---|
| Schema | `r1-d1-inventory-v1` |
| Canonicalization | `r1-d1-inventory-c14n-v1` |
| Protocol / freeze entry | `1.0.0` / numeric `1` |
| Required UTC envelope | `[2026-04-01T00:00:00Z, 2026-09-29T04:02:00Z)` |
| Candidate / selected sources | 4 / 0, verified by Builder |
| Classification / CLI exit | `INVENTORY_BLOCKED` / `2`, verified by Builder |
| Blocking reasons | 67, retained and deterministically ordered in the corrected report |
| Selected-source upper bound | `"0"` micro-USD: the sum over zero selected sources; no D1 cost estimate exists |
| Authorization / measured D1 pass | `runAuthorized = false`, `d1Passed = false` |
| Inventory fingerprint | `sha256:f9f2fb5450ce37e923892979e5b6a180e7e82fcbfe25ef44c7362fb2c972888f` |

The initial two-candidate snapshot passed local validation with exit 2, 67 blockers and fingerprint `sha256:dea0c1610d91856990ccd0c7d45eb1943efb275410c8f647ce0df5353f1851f1`. That is historical section 1 evidence, not the corrected input's identity. The fingerprint includes unselected candidates, annotations and dated references; it does not identify an extracted dataset or prove source truth. Recompute the report after input edits.

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

## Next decision

The owner selected `C:\crypto-research-evidence\r1-d1`; the parent exists, the new directory has not been created. C: had `124262658048` free bytes during inspection; recheck before any run. No path decision remains outstanding. On 2026-10-01 the owner explicitly declined Alchemy PAYG D1 spending: "Не разрешаю пока". No new PAYG call or paid-run PLAN_READY is permitted until a future explicit owner decision. Earlier A/B authorizations are exhausted.

Offline candidate correction (tasks 1.7–1.10) is verified, including behavioral RED, unchanged frozen tests, fresh APPROVE and Main's gate. Current authorized work refines only planning/docs. A later source-admission PLAN must resolve applicable retention, selected source/query versions, zero-paid access, exact filtered population/query/sample definitions and complete workload bounds before implementation or a source run.

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

Source-specific tooling and runs require a separate updated `PLAN_READY`, behavioral RED, reviewed inventory/scripts and Main's mandatory gate. No source is selected by this document. A sample cannot replace the frozen full-envelope 90 percent availability thresholds, 200-trade/95 percent/zero-double-count cross-check, venue gap limit, provenance or rerun hashes. If only a sample is completed, D1 remains `INCONCLUSIVE`. Section 2 stays incomplete; offline correction cannot close or archive D1.
