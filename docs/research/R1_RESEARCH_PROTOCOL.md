# R1 research protocol: wallet copier and early multi-wallet hypotheses

| Field | Value |
|---|---|
| Protocol | `R1` (experiment `R1-E1`) |
| Version | `1.0.0` |
| Status | `FROZEN` 2026-10-01 (UTC). All owner-decision values `OD-1`..`OD-8` are `OWNER-APPROVED 2026-10-01` without adjustment, and the holdout attestation is owner-attested (2026-10-01). The review repair round 1 clarifications (section 14.1) are `OWNER-APPROVED 2026-10-01`, with the owner's public-label condition (section 5.1). Data work may start only as section 2.2 step 6 states: under the approved offline procedure and its own stage OpenSpec change; outcome-bearing work follows the section 2.3 lifecycle. |
| Drafted | 2026-10-01 |
| Frozen | 2026-10-01 (UTC) |
| Freeze record | `docs/research/R1_PROTOCOL_FREEZE.md`, created in the commit following the freeze commit; it holds the freeze commit SHA and the SHA-256 of this file's bytes at that commit (section 2.3) |
| Offline procedure | [R1 offline research procedure](R1_OFFLINE_RESEARCH_PROCEDURE.md) (`OWNER-APPROVED 2026-10-01`; in force from protocol freeze `1.0.0`) |
| Remediation link | Implements [F5](../DELIVERY_PLAN_FIXES.md#f5--пререгистрированный-research-protocol) for the copier and multi-wallet families named in the [replan note](../notes/ARCHITECTURE_AND_RESEARCH_REPLAN_2026-09-30.md#r1-протокол-до-подбора-параметров) |

## 1. Status, authority and boundaries

1.1 This document preregisters how the historical wallet/copier and early multi-wallet research (D1 data gate and P1 pilot) is designed, measured, judged and stopped. Once frozen it is the controlling methodology for any evidence that claims to be R1 evidence. It does not change accepted application behavior. Accepted specifications, accepted ADRs, [Reproducibility](../REPRODUCIBILITY.md) and [Core invariants](../CORE_INVARIANTS.md) remain authoritative for the application; a conflict between them and this protocol is a protocol defect that must be recorded and resolved before freeze.

1.2 Owner decisions of 2026-10-01:

- Drafting R1 starts now, in parallel with the active A1+A2 change `version-market-facts-and-split-evidence`. This overrides the replan note's order "R1 after implemented and verified A1/A2" for drafting this protocol only. It does not authorize data purchase, provider runs, offline scripts, application code, schema changes or viewing any returns.
- A one-time documentation exception from behavioral RED is granted only for this protocol document and its OpenSpec change `preregister-r1-research-protocol`. Risk remains `CORE_RISK`; a fresh independent Reviewer and Main's complete gate remain mandatory. The exception is not a precedent: every later calculation and implementation (D1/P1 scripts, code, reconstruction, selection, outcomes, statistics) requires behavioral tests.
- The Architect proposes concrete minimal values for every owner decision (section 14), each with its source; the owner approves or adjusts them before freeze.
- Approval, 2026-10-01: the owner approved every `OD-1`..`OD-8` value exactly as written in `0.4.0-draft` section 14, and the offline procedure `0.2.0-draft` (`OD-4`), without adjustment. This approval does not freeze the protocol and does not authorize data work, which still requires freeze and its own stage change.
- The offline research procedure (`OD-4`) is mandatory and blocking: no data work starts until it is approved.
- The primary metric, success thresholds and wallet-selection rules are fixed at the `1.0.0` freeze, before any return is viewed. After D1 only the enumerated data-quality and data-availability values of section 2.4 may be refined.
- External methodology feedback adopted by the owner is incorporated in `0.3.0-draft` and `0.4.0-draft` (changelog).
- Scope option (a), a bounded pilot: budgets, the sample floor and the 4.5 percent effect bar stay unchanged, and no larger threshold is introduced. This pilot will often leave a small true effect `INCONCLUSIVE` (`UNDERPOWERED`) rather than confirm or refute it.
- Holdout attestation: the owner personally attests that they have not viewed wallet or token returns for 2026-08-31 to 2026-09-28 and have not used that period to choose rules (section 4.1).

1.3 Nothing in this protocol authorizes signing, order submission, PAPER/LIVE execution, new production dependencies, a second authoritative store or cross-module SQL. PostgreSQL remains authoritative for application decision snapshots, runs, outcomes and reports. Offline research artifacts are research evidence only and follow the approved offline procedure.

1.4 Value labels. `PROPOSED` marks an Architect-proposed owner decision (`OD-n`); it becomes `OWNER-APPROVED (date)` only by explicit owner approval, and the protocol cannot be frozen while any value is not approved. Since 2026-10-01 every `OD-n` value and every section 14.1 clarification is `OWNER-APPROVED 2026-10-01`. `D1_CALIBRATION C-n` marks one of the enumerated values that may be refined after D1 under section 2.4. No value in this protocol is measured, fitted or derived from returns; each states its source: an accepted document, a Roadmap draft constant, the replan note, an owner choice, or explicit reasoning.

## 2. Sequence, versioning, blinding and freeze

### 2.1 Outcome-bearing quantities

An outcome-bearing quantity is any return, PnL, hit rate, expectancy, drawdown, profit factor or ranking derived from them, computed for a copier or multi-wallet rule, a wallet, a parameter configuration or a baseline over any period of the envelope, including copier-replicated returns used for wallet selection (section 5). Such quantities may be computed only after the D1 gate passes and the `1.1.0` calibration is recorded, and only by the frozen rules; selection-history quantities are selection inputs, not R1 results.

### 2.2 Mandatory order

1. Update this protocol and the offline procedure (`DRAFT`).
2. Owner approves or adjusts every `PROPOSED` value; adjusted values are recorded by the Architect. Done 2026-10-01: every value approved without adjustment; the holdout attestation (section 4.1) was given on 2026-10-01.
3. Final fresh independent review of the protocol and procedure.
4. Main's complete gate passes.
5. Protocol freeze `1.0.0` with a freeze record (section 2.3).
6. Only then may separately authorized data work start, under the approved procedure.

No step may be skipped or reordered. Data work started before step 5 is not R1 evidence.

### 2.3 Lifecycle and freeze record

| Stage | Version | Allowed | Forbidden |
|---|---|---|---|
| Draft | `0.x.y-draft` | Methodology edits, owner decisions, review | Any data work, any outcome-bearing quantity |
| Protocol freeze | `1.0.0` | D1 field-availability inventory, extraction and gate under the approved procedure and budget | Outcome-bearing quantities |
| D1 calibration amendment | `1.1.0` | Refining only the section 2.4 whitelist from outcome-blind D1 measurements | Every other change |
| Development folds | `1.1.0` | Selection-history replication and validation-fold outcomes; configuration selection by section 10.4 | Any holdout access |
| Holdout freeze | unchanged | One holdout evaluation with the frozen code, configuration, dataset manifest and seeds; at most one technical rerun under section 12.4; a section 2.5 defect correction only before any holdout outcome is viewed, recorded as a new holdout-freeze entry | Any rule, configuration or input change; any other code change |
| Closure | unchanged | Result classification and failure codes under section 12 | Re-evaluating the holdout under the same experiment ID |

The freeze record is a separate file, `docs/research/R1_PROTOCOL_FREEZE.md`, created at protocol freeze and extended at the D1 calibration amendment and the holdout freeze. Each entry records UTC date, protocol version, the git commit containing that exact protocol text, the SHA-256 of the protocol file bytes at that commit in `sha256:<lowercase-hex>` form, approved owner decisions with dates, the holdout attestation (template value: "owner-attested 2026-10-01: no viewing of wallet or token returns for 2026-08-31 to 2026-09-28 and no use of that period to choose rules"), and the review reference. The holdout entry additionally records the analysis code commit, canonical configuration fingerprint, dataset manifest fingerprint and random seeds. The protocol does not hash itself.

### 2.4 D1 calibration whitelist

The `1.1.0` amendment may change only these values, only within the stated bounds, and only from measurements containing no outcome-bearing quantity (observation density, gap distributions, reserve-input availability, source visibility latency). Each refinement records its measurement query/export version and manifest fingerprint.

| ID | Value | Fixed at `1.0.0` | Refinable in `1.1.0` |
|---|---|---|---|
| `C-1` | Horizon price tolerance windows | Defaults `1h: 5 min`, `4h: 20 min`, `24h: 2 h` after the nominal horizon | Each value within `[default, 2 x default]`, from observation-density measurements |
| `C-2` | Supported venues and per-venue reserve/depth input availability | Pricing form per venue type (constant-product reserves, concentrated-liquidity ticks, bonding curve); the section 8.2 depth rule | Which venues have the required inputs; venues without them are `UNSUPPORTED_VENUE` or `COST_UNMODELED`, counted |
| `C-3` | Modeled availability offset for historical evidence | Rule: modeled availability = block time + modeled offset; default offset 60 s (`OWNER-APPROVED 2026-10-01`, section 14.1); always labelled `MODELED` with the availability-model version, never reported as a measurement | Offset = max(60 s, measured 90th-percentile source visibility latency), at most 600 s; a measured value above 600 s fails D1 (`DATA_INSUFFICIENT`) |

Everything else is frozen at `1.0.0` and cannot change in `1.1.0`, including hypotheses, primary metric, success thresholds, wallet-selection rules and grid, latency scenarios, entry size, entry wait and liquidity rules, statuses and their precedence, the conservative unknown scenario and bias rule, cost-model forms, clustering rules, portfolio bounds, statistical procedure and constants, baselines, D1 gate thresholds, range dates and stop rules. A whitelist value that cannot be refined without outcome-bearing data keeps its `1.0.0` default.

### 2.5 Amendments and deviations

- Before protocol freeze, edits create a new draft version with a changelog line.
- After protocol freeze, any change outside section 2.4 is a new experiment ID (`R1-E2`, ...) with a holdout that no earlier experiment has seen, after its own review and freeze; the earlier experiment is reported unchanged.
- Correcting an implementation defect so that code conforms to the frozen rules is not an amendment; it requires a behavioral test and a deviation-log entry. After the holdout freeze it is allowed only before any holdout outcome is viewed and requires a new holdout-freeze entry with the new analysis code commit (and any changed configuration or manifest fingerprint); the holdout evaluation then runs on that entry.
- A holdout period is single-use for a hypothesis family.
- Every unplanned action affecting data, code, configuration or result is entered in a deviation log in the report. An undisclosed or result-affecting deviation reclassifies the affected result as `EXPLORATORY` (section 12).

## 3. Hypotheses, primary metric and operating envelope

### 3.1 Operating envelope B

The proposed operating envelope is a signal acted upon within minutes, hours or days by manual entry, using observable live liquidity. It is a proposed envelope, not a found edge and not a speed guarantee. The research does not compete in a latency race with validators, bundle tips or snipers; entry lags below the envelope lower bound (`OD-1`) are outside the analysis. A measurement horizon does not imply an acceptable entry lag of the same length.

### 3.2 H1: timely copy of selected wallets (copier)

- Selection: wallets chosen by the frozen point-in-time, copier-replicable selection rule (section 5).
- Trigger: a `RECONSTRUCTED` economic buy of an eligible universe token by a selected wallet with quote notional at least the trigger minimum (`OD-7`).
- Copier entry instant: `t_entry = t_avail + entry lag`, where `t_avail` is the trigger's availability instant (section 4.3) and the entry lag comes from the latency scenario (`OD-1`).
- Measured quantity: net return of the copier's own position from its modeled entry (section 7.3) to each measurement horizon, after the venue-specific cost model (section 9). The source wallet's fill price, PnL or holding period is never the copier's return.
- Null hypothesis: in the untouched holdout, the copier's mean net return is not above zero and not above the preregistered baselines (section 12).

### 3.3 H2: early independent-buyer group (multi-wallet)

- Trigger: at least `K` distinct independent selected wallets buy the same eligible token within a rolling window `W`; the trigger instant is the availability instant of the buy completing the group.
- Independence: wallets merged by the primary clustering variant (section 5.7) count as one buyer.
- Measured quantity and null hypothesis: as in H1, from the group trigger.

### 3.4 Primary metric and conservative bound

For each family, the primary metric is the mean net return per copier position in USD terms, at the primary horizon (`OD-8`), under the primary latency scenario and entry size (`OD-1`) and the venue-specific cost model, over known outcomes (`PRICED` and `TERMINAL_NO_EXIT`). USD as primary and SOL as secondary denomination follow Roadmap section 22.1. A support conclusion must also hold under the conservative unknown-outcome scenario `U-CONS` (section 7.5). Everything else is secondary or sensitivity and cannot by itself support a hypothesis.

### 3.5 Excluded from this experiment

Liquidity, holder-growth and risk families, avoidance families and any execution path are outside `R1-E1`. A later version adding an avoidance family reports ENTRY and avoidance results separately and never mixes them. `LIQUIDITY_SPIKE` remains a technical recorded fixture, not an R1 hypothesis.

## 4. Time, availability and splits

### 4.1 Ranges (`OD-5`, `OWNER-APPROVED 2026-10-01`) and holdout attestation

| Range | UTC interval (start inclusive, end exclusive) | Reads |
|---|---|---|
| Warm-up | 2026-04-01 to 2026-06-30 (90 days) | Selection inputs only |
| Validation fold 1 | 2026-06-30 to 2026-07-30 | Outcomes after D1 gate |
| Validation fold 2 | 2026-07-30 to 2026-08-29 | Outcomes after D1 gate |
| Embargo | 2026-08-29 to 2026-08-31 | Nothing evaluated |
| Holdout | 2026-08-31 to 2026-09-28 (28 days) | Only after holdout freeze |

The envelope is the accepted 180 days of 90-day warm-up plus 90-day evaluation (accepted Solana data contract, F1.8). Positions entered near an interval end are valued with data up to their horizon and tolerance, so extraction extends to 2026-09-29T04:02:00Z (2 min entry wait plus 24 h horizon plus the largest calibrated tolerance); that extension is never an entry interval. If D1 shows the sources cannot cover the envelope, the hypotheses are `INCONCLUSIVE`; changing dates requires a new experiment before any outcome is computed.

Holdout attestation: on 2026-10-01 the owner personally attested that they have not viewed wallet or token returns for 2026-08-31 to 2026-09-28 and have not used that period to choose rules. The attestation is copied into the freeze record. If the holdout dates change before freeze, a new attestation is required for the new period. General market awareness of a calendar period cannot be fully blinded and is stated as a limitation. If the holdout yields insufficient observations, the result is `INCONCLUSIVE`; the holdout is never extended after any holdout outcome has been viewed.

### 4.2 Embargo and walk-forward selection

Each fold and the holdout use a wallet selection made at the interval start `s` from history `[max(warm-up start, s - 90 days), s - 2 days)`. A replicated history position (section 5.2) counts only if its valuation instant plus tolerance precedes the history end. The two-day embargo exceeds the longest horizon (24 h) plus its largest tolerance (2 h, at most 4 h after calibration), so no evaluated-interval information reaches the selection. Every trigger, including those ending `NOT_ENTERED` or `ENTRY_UNKNOWN`, is attributed by its computed `t_entry` and is generated by the selection in force at its `t_avail`. A trigger whose `t_avail` and `t_entry` fall in different intervals, or either falls in the embargo, warm-up or outside the envelope's entry intervals, is excluded as `BOUNDARY_CROSSING` and counted, never evaluated in either interval.

### 4.3 Availability instants

- Each trade, transfer, price, reserve and wallet fact carries chain/source event time and an availability instant. Where live receipt time is unknown, availability is modeled under `C-3` and labelled `MODELED` with the availability-model version (offset value and whether it is the 60 s default or a refined value); it is a modeled offset, never a measurement. Real receipt time is labelled `OBSERVED_LIVE`.
- `MODELED` and `OBSERVED_LIVE` evidence are never silently merged in one statistic; reports present them separately or state the explicit combination rule and its sensitivity.
- A computation with cutoff `c` reads only facts whose availability instant is at or before `c`. A later correction or backfill does not change an earlier decision; it may only produce a new versioned run.

### 4.4 Wallet score and label knownAt

Every wallet score, label, cluster assignment and service-identity version carries `knownAt`, the earliest instant at which every input was available and the computation could have completed. Selection at cutoff `c` uses only versions with `knownAt <= c`. Within one evaluated interval, selections are fixed at the interval start, consistent with the accepted rule that wallet metrics for a signal derive only from trades completed before its evaluation range begins.

### 4.5 Canonical event order

All same-instant ties use one total order: slot, transaction index within the block, outer instruction index, inner-instruction stack path, then leg ordinal, matching the accepted Solana event-locator grammar. Facts derived from a transaction (reserve states, balances, transfers) take that transaction's position. Instants are compared first and this order breaks ties. It governs the earliest admissible entry and exit, the buy completing an H2 group, FIFO lot order, the deduplication anchor (section 10.1) and every other order-sensitive step.

## 5. Wallet selection (`OD-7`, `OWNER-APPROVED 2026-10-01`)

5.1 Candidate pool. Wallets with `RECONSTRUCTED` trades in eligible universe tokens inside the permitted history. Program-derived addresses, router and aggregator authorities, protocol-owned accounts and mass-service accounts (section 5.7) are excluded by identity rules whose criteria are frozen at `1.0.0`, never by performance: (1) addresses off the ed25519 curve and executable program accounts; (2) authority, fee, vault and pool accounts named in the official program documentation or interface definitions of supported venues, aggregators and bridges; (3) the section 5.7 mass-service criteria. Public address labels (for example exchange or service labels) may be used only when all of these hold: the label source and its exact version are saved; the label content hash is recorded in the manifest; and the label's availability before the relevant decision (selection cutoff or `knownAt`) is confirmed, so a label known only later is never applied retroactively. A label failing any condition is not used. Labels are identity rules only, never performance-based, and the frozen criteria still apply. The resulting identity list is computed outcome-blind in D1, versioned with `knownAt` and hashed in the manifest; criteria cannot change after `1.0.0`.

5.2 Primary ranking: copier-replicable performance. For each candidate, every qualifying trigger buy in the permitted history is replicated as a copier position with the frozen primary entry lag, entry size, liquidity minimum, impact cap, entry wait, venue cost model and status rules, and valued at the primary horizon; valuation at the horizon marks open and unrealized positions, so a source wallet's unsold losing remainder still counts against it. The wallet score is the mean net return of its replicated positions under `U-CONS` (section 7.5), so poorly priced histories cannot rank well by omission. The evaluated interval is never part of the history.

5.3 Prefilter and ranking. A wallet is eligible when its replicated entered positions and recency meet one prefilter grid point, which reuses only the activity parts of the Roadmap section 19 draft tiers:

| Prefilter grid point | Replicated entered positions in history | Last trade before cutoff |
|---|---|---|
| `STRONG_ACTIVITY` | at least 20 | within 14 days |
| `PROMISING_ACTIVITY` | at least 10 | within 30 days |

Eligible wallets with a positive score are ranked by score, then by replicated position count, then by wallet address bytes ascending; up to 50 form the selection (fewer if fewer qualify; an empty selection yields zero positions, counted). Fifty is a reasoning choice: enough co-buying wallets for H2 while remaining a watchlist a person can follow.

5.4 Comparison variant `OWN_PF`. Ranking by the wallets' own closed-trade metrics with the full Roadmap section 19 tier thresholds (win rate, profit factor) is computed in validation as a comparison only. It measures source-wallet success that may be unreachable at copier lag and ignores open losing remainders, so it is never eligible for holdout selection.

5.5 Trial ledger. Every ranking formula, prefilter and variant evaluated, including `OWN_PF`, is a ledger entry (section 10.3).

5.6 Data consequence. Copier replication over the selection history requires the same transfers, prices, reserves or depth and status evidence over the warm-up and earlier fold periods as over evaluated intervals; D1 covers the whole envelope (section 8).

5.7 Coordination clustering. Clustering uses permitted history only and is versioned with `knownAt`.

- First SOL funder: the sender of the earliest inbound SOL transfer to the wallet observed inside the permitted history window (canonical order, section 4.5). If the wallet shows any transaction or nonzero SOL balance in the window before that transfer, its real first funding predates the window; it is marked `FIRST_FUNDER_UNKNOWN` and never merged on the shared-funder rule (only on a direct transfer). This under-merges, which the clustering variants make visible.
- Mass-service identities: exchange, bridge, faucet, payment and similar service addresses, identified by criteria frozen at `1.0.0` (section 5.1): bridge and program accounts from official documentation, public labels only under the section 5.1 label conditions, plus a heuristic treating any address that was first SOL funder of more than 50 distinct wallets in the permitted history as a service. The threshold is a heuristic, not a proven classification. Mass-service funders never cause merging.
- Presumed linkage (merges wallets): a direct SOL or token transfer between the two wallets, or a shared first SOL funder that is not a mass-service identity. Such evidence proves a transaction link, not common ownership; merging on it is a preregistered presumption, and its effect is reported through the clustering variants.
- Coordination indicators (do not merge on their own): same-slot or same-bundle co-occurrence and near-simultaneous trading. Bots on popular tokens also produce these.

Clustering variants: `V0` no merging; `V1` presumed linkage only (primary for H2); `V2` presumed linkage plus wallets co-occurring in the same slot or bundle on at least three distinct tokens. H2 results are reported for all three variants. A full wallet graph is built only if this sensitivity shows that it is necessary.

## 6. Trade reconstruction and position accounting

### 6.1 One economic trade per user action

- A transaction's route legs (aggregator hops, multi-pool routes, intermediate tokens) are reconstructed into one economic trade per trader: net token deltas of the trader's owned accounts. Intermediate tokens with zero net delta are not purchases; route-leg volume is never summed as trade volume.
- Quote assets are SOL (including wrapped SOL), USDC and USDT; other quote assets yield `UNSUPPORTED_VENUE`. USD conversion uses a point-in-time SOL/USD source declared in the D1 change, with lineage.
- Trader identity is the wallet whose owned token balances change, not a router, program or fee payer acting for it; disagreement is an ambiguity status.
- Every trade preserves raw integer amounts with decimals, transfers, base and priority fees, tips where observable, each leg's pool and venue, trader identity and source visibility (which source supplied it, decoded or raw, and whether it was cross-checked against raw receipts).
- Arithmetic is exact with declared precision and rounding, per [Reproducibility](../REPRODUCIBILITY.md).

### 6.2 Reconstruction statuses

| Status | Meaning |
|---|---|
| `RECONSTRUCTED` | Net deltas, quote leg, trader and fees determined |
| `AMBIGUOUS_ROUTE` | Several consistent interpretations or unclear trader |
| `MISSING_LEG` | A required leg or transfer is absent from the source |
| `UNSUPPORTED_VENUE` | A leg uses a venue or quote asset without declared support |
| `DECIMALS_UNKNOWN` | Mint decimals cannot be established point-in-time |

Only `RECONSTRUCTED` trades trigger signals or enter selection. All other statuses are counted per source, venue and period and are never silently dropped or guessed.

### 6.3 FIFO positions and cashflows

FIFO lots are kept per chain, wallet and token across venues (Roadmap section 19.1) for the `OWN_PF` comparison and source-wallet descriptions. Lot and position statuses:

| Status | Meaning |
|---|---|
| `CLOSED` | Lot fully sold through reconstructed trades |
| `PARTIALLY_CLOSED` | Part sold; remainder open |
| `OPEN_REMAINDER` | Open at the computation cutoff |
| `UNKNOWN_BASIS` | Acquired by transfer or unreconstructed path without known cost |
| `TRANSFERRED_OUT` | Left the wallet without a trade |
| `PARTIAL_FILL` | The observed trade filled less than the routed intent, where detectable |

Unknown basis is never set to zero or to a later market price. Closed-trade metrics use known-basis quantities only and report known-basis coverage. Open exposure is informational and is not evidence of skill; observed wallet PnL is not treated as realized skill.

## 7. Universe and outcomes

### 7.1 Point-in-time universe

The universe contains every SPL token with at least one `RECONSTRUCTED` swap against a supported quote asset on a supported venue (`C-2`), from its first such swap, including later rejected, shadow, illiquid and dead tokens. Exclusions and gaps carry time and reason and are counted. Current survival, liquidity or market capitalization never selects members.

### 7.2 Measurement horizons

Horizons `1h`, `4h` and `24h` are measured from the copier entry instant. They are measurement horizons, not promised holding periods; there are no stop, take-profit or time-stop exits in `R1-E1`. The application currently implements only the `1h` outcome; `4h` and `24h` require a future forward-migration contract before the application computes them, and research artifacts computing them remain research evidence. The valuation window of a horizon runs from the nominal horizon instant to the end of its tolerance (`C-1`); the horizon value is the earliest admissible executable sell value in that window.

### 7.3 Entry

The entry wait window runs from the computed `t_entry` (trigger availability plus the scenario's entry lag), not from the signal, to `t_entry` plus the maximum entry wait (`OD-1`). Entry uses the earliest instant in that window with an admissible executable buy that passes the route, liquidity-minimum and impact-cap rules. Without such an instant:

- `NOT_ENTERED` (proven infeasibility): point-in-time reserve and route evidence covers the whole wait window and shows no route, liquidity below the minimum or modeled impact above the cap, with that reason;
- `ENTRY_UNKNOWN` (reason `ENTRY_WAIT_EXPIRED`): part of the wait window lacks the historical quote or reserve evidence needed to decide. This is an unknown, not an infeasibility.

A later price is never used as the entry. Exit valuation is the side-aware executable sell value for the whole position, not a mid price.

### 7.4 Outcome statuses and precedence

Statuses are assigned in this order; the first matching rule wins (the two entry statuses are mutually exclusive):

| Order | Status | Rule | Return |
|---|---|---|---|
| 1 | `NOT_ENTERED` | Entry proven infeasible in the wait window (section 7.3) | None; counted as missed |
| 1 | `ENTRY_UNKNOWN` | Entry undecidable in the wait window for lack of historical quote evidence (section 7.3) | None; counted as unknown |
| 2 | `TERMINAL_NO_EXIT` | Positive evidence that the position cannot be realized in the valuation window, with zero recovery, regardless of whether an exit existed earlier | `-100%` |
| 3 | `PRICED` | An admissible executable sell value exists in the valuation window | Measured net return (a near-total loss with positive recovery stays `PRICED`) |
| 4 | `INCONCLUSIVE` | An unresolved data gap or source failure overlaps the position's window | None |
| 5 | `UNPRICED` | No admissible valuation and no positive loss-of-exit evidence | None |

Positive loss-of-exit evidence is point-in-time chain evidence that, throughout the valuation window, every supported pool of the token had zero executable output for the position (for example reserves drained by a liquidity removal, or no pool remaining), or that the token cannot be transferred by any holder (for example a mint-wide transfer restriction). Evidence must apply to any holder, because the copier's account is hypothetical; account-specific evidence about the source wallet does not count. This covers the common pattern where an exit existed after entry and liquidity disappeared before the horizon. Before any position is classified `INCONCLUSIVE` or `UNPRICED`, the loss-of-exit check is performed and its result recorded (`NO_EVIDENCE` or `LOOKUP_UNAVAILABLE`), so a provable loss cannot fall into an unknown status. Missing data alone is never evidence of loss and never yields `-100%`, and no outcome is silently dropped. Every status is counted per family, configuration, horizon and scenario; outcomes touched by gaps also carry a gap flag.

### 7.5 Unknown outcomes: conservative scenario and bias flag

Unknown positions are `UNPRICED` and `INCONCLUSIVE`. The unknown share is the count of `UNPRICED`, `INCONCLUSIVE` and `ENTRY_UNKNOWN` divided by all retained, attributed triggered signals of the strategy or baseline (including `NOT_ENTERED`; excluding `DEDUPED` and `BOUNDARY_CROSSING`, sections 10.1 and 4.2). `ENTRY_UNKNOWN` counts in the unknown share and in the bias-flag comparison because it depends on data density, but it is not stressed as `-100%` in `U-CONS`: no position was opened, so there is no return or deployed capital to stress, and assigning one would invent a trade.

- Known-outcome metric: the primary metric of section 3.4.
- `U-CONS`: a specific preregistered stress scenario, not a status change and not a mathematically guaranteed bound. For the tested strategy, every unknown position (`UNPRICED`, `INCONCLUSIVE`) is assigned a net return of `-100%`; for baselines, unknown positions are excluded. Excluding baseline unknowns is not in general the most favorable treatment for the baselines, because their unknown trades could have been more profitable; it is simply the declared stress. Every support condition of section 12.1 (sign, Holm test, baseline differences, minimum effect, outlier test) is recomputed under `U-CONS` and must also hold. Example: 90 known positions at `+4.5%` and 10 unknown positions give `-5.95%` under `U-CONS`, so support is blocked. A `U-CONS` failure blocks support but does not by itself prove the absence of a real edge.
- Maximum unknown share: if the strategy's unknown share at the primary horizon exceeds the maximum (`OD-6`), the result is `INCONCLUSIVE`.
- Unknown-share bias flag: the unknown shares (including `ENTRY_UNKNOWN`) of the strategy, of `B-RANDOM` (pooled over replications) and of `B-LIQ` (pooled) are reported side by side, each with the number of signals. If the strategy's share exceeds either baseline's share by more than the bias margin (`OD-6`, absolute percentage points), the bias flag is set and blocks `SUPPORTED_FOR_SHADOW`.
- Best-case and worst-case bounds that treat all unknowns as recovered or lost are also reported as sensitivity.

## 8. Data gate (D1)

### 8.1 First deliverable: field-availability inventory

Before any bulk extraction or spending beyond small schema probes, D1 delivers a field-availability inventory: for each required field, which candidate source provides it, over which dates of the envelope, at what granularity, with what known gaps, at what cost (compute, export or API plan) and under what retention terms. Required fields: trade legs with pool addresses; SPL and SOL transfers to and from candidate wallets; base fees, priority fees and tips; pool reserves or depth over time (directly or reconstructable, for example from pool-vault pre/post token balances in raw transactions); liquidity add/remove events; mint decimals and freeze authority state; executable entry and exit inputs per venue; SOL/USD price; block time and source visibility latency. A decoded trade table alone, such as one that documents per-leg rows and pool addresses, does not provide reserve or depth history, and a trade cross-check does not close that gap.

The inventory is reviewed under the D1 change before extraction. If the cost of the required fields exceeds the `OD-2` ceiling, work stops before spending and the owner decides on a new budget or a narrower experiment. The `OD-2` amount is a spending ceiling, not a cost estimate; source costs are unknown until the inventory.

### 8.2 Depth rule (preregistered)

Over the whole envelope, if venues with available reserve or depth inputs (`C-2`) cover at least 90 percent of the trigger events of the candidate pool (counted from trades, without outcomes), the cost-modelled analysis proceeds and uncovered events are `UNPRICED` with reason `COST_UNMODELED`, counted in the unknown share. Otherwise D1 fails for cost-modelled analysis: hypotheses are `INCONCLUSIVE/data insufficient`, and any analysis restricted to the synthetic `B0` cost is reported only as `EXPLORATORY` with the missing depth stated as a study limitation; it can never support a hypothesis.

### 8.3 Gate thresholds

The D1 gate passes only if the inventory is complete, the depth rule passes and all thresholds below (`OD-6`, `OWNER-APPROVED 2026-10-01`) are met, measured without outcome-bearing quantities over the whole envelope. They are fixed at `1.0.0` and never relaxed after extraction.

| Criterion | Threshold |
|---|---|
| Stratified raw-receipt cross-check of reconstruction | 200 trades stratified by venue and month; at least 95 percent agree; zero double-counted volume |
| `RECONSTRUCTED` share of candidate-pool swaps | at least 90 percent |
| Entry and primary-horizon price input availability for trigger events | at least 90 percent |
| Loss-of-exit lookup availability (pool state or liquidity events) for trigger tokens | at least 90 percent |
| Unresolved gaps per supported venue | at most 2 percent of envelope time, each listed with range and cause |
| Point-in-time universe, service identities and wallet labels | Reproducible from the manifest: a rerun gives identical hashes |
| Manifest | Hashes, query/export version, row counts, ranges and provenance present |

The thresholds are reasoning choices for a bounded first pilot: high enough that missing data cannot dominate a result, not tuned to any data. A failed gate yields `INCONCLUSIVE/data insufficient`, never "no edge".

## 9. Research execution and cost model

9.1 The model is research-only and venue-specific. Each venue type has a fixed pricing form (section 2.4, `C-2`). Components: entry lag, entry wait, entry size, measured reserves or depth, route, price impact and slippage, pool/LP fees, base and priority fees, tips, partial fills, infeasible entry or exit, and capacity (largest entry size before the mean net return changes sign, reported as sensitivity).

9.2 When a venue's required inputs are unavailable at the decision instant, the outcome is `UNPRICED` with reason `COST_UNMODELED` (after the loss-of-exit check); it is not costed with a flat haircut in primary analysis.

9.3 The first-slice `3%/4%/5%` liquidity-tier haircut ([evaluation module](../modules/evaluation.md)) is synthetic baseline `B0` only, labelled as such, never presented as a measured trade price.

9.4 Latency, wait and size scenarios (`OD-1`, `OWNER-APPROVED 2026-10-01`) are listed in section 14. They are assumptions to be replaced by measured lag from forward shadow (S1), not measurements.

## 10. Portfolio simulation and statistics

### 10.1 Finite-capital portfolio

Signal deduplication (`OWNER-APPROVED 2026-10-01`, section 14.1): within each family and configuration, a trigger on a token whose `t_avail` is less than 24 hours after the `t_avail` of the last retained trigger on the same token is a duplicate. The window is anchored on retained triggers in canonical order, regardless of their entry status. Duplicates are counted as `DEDUPED` and are not positions: they enter neither the primary metric, the sample floor, the unknown-share numerator or denominator, nor the portfolio. Baselines apply the same rule. `DEDUPED` triggers are counted and reported separately per family, configuration, interval and scenario, for the strategy and for every baseline (section 13).

Every primary result is also simulated per family as a finite-capital research portfolio with the bounds of `OD-3`: capital, position size, maximum concurrent positions, one open position per token, cluster exposure cap, a drawdown halt, missed orders when capital or limits block entry (`MISSED_CAPITAL`, counted), and the actual D1 data spend allocated pro rata over the evaluated days. Two variants are run. In the known-outcome portfolio, an unknown position occupies capital and a concurrency slot from entry to the end of its valuation window and is then released at 0 percent return. In the `U-CONS` portfolio it is released at `-100%`. `ENTRY_UNKNOWN` and `NOT_ENTERED` signals open no position in either variant. Sensitivity also shows results without the drawdown halt and at the other entry sizes. The simulation never signs or places orders.

### 10.2 Statistical procedure

- Unit: one copier position. Metric `m`: mean net return per position (known outcomes, or `U-CONS`).
- Token cluster: one token (one mint) is one cluster. The sample floor's cluster count, the effective sample size and the token bootstrap unit all count distinct tokens with at least one position in the evaluated set.
- Bootstrap: percentile cluster bootstrap with 10,000 resamples and a recorded seed, run in two schemes: resampling tokens with replacement (all positions on a drawn token enter together) and resampling UTC days with replacement. Each scheme's p-value is `(1 + count(m* <= 0)) / (10,000 + 1)`; the larger p-value of the two schemes is used, and the lower of the two lower bounds.
- Baseline difference: each baseline value `b` is the mean over its 100 replications of the replication's mean net return. The difference is `d = m - b`. In each bootstrap draw, tokens (or days) are drawn from the union of those appearing in strategy or baseline positions, and `m*`, `b*` and `d* = m* - b*` are recomputed from the positions in the draw. The baseline condition is a lower one-sided 95 percent bound of `d*` above zero for `B-RANDOM` and for `B-LIQ`, using the lower bound of the two schemes. Baseline and effect conditions are conjunctive requirements on each family and are not further adjusted.
- Holm-Bonferroni with family size `m = 2` fixed at preregistration (`OWNER-APPROVED 2026-10-01`, section 14.1) at one-sided level 0.05. If both families open their holdout, the smaller p-value must be at most 0.025 and the larger at most 0.05 to reject both; testing stops at the first non-rejection. If only one family opens its holdout, its level is 0.025, because the family size is set by the preregistered hypotheses, not by which families reach the holdout. If none opens, no test is run.
- Effective sample size: the number of distinct tokens (token clusters). Results are also shown by cohort, wallet, cluster, size and lag, with complete exclusion counts.

### 10.3 Multiple testing and trial ledger

A trial ledger records every evaluated combination of family, prefilter, ranking formula (including `OWN_PF`), clustering variant, horizon and scenario, including failed and abandoned ones, and its count is reported. Validation selects at most one configuration per family; the holdout tests only those, at the primary horizon and scenario, with the Holm procedure above. Validation results are labelled exploratory.

### 10.4 Configuration selection rule

- Eligible configurations: exactly the preregistered copier-replicable grid of `OD-7` at the primary horizon, primary entry lag and primary entry size: for H1 the prefilter points `STRONG_ACTIVITY` and `PROMISING_ACTIVITY`; for H2 those two points with `W` in {5 min, 60 min}, `K = 3` and clustering `V1`. `OWN_PF`, other clustering variants, horizons and scenarios are never eligible.
- Validation adequacy, per configuration over both validation folds pooled: D1 passed (common to all configurations); at least 100 positions and 30 distinct tokens; unknown share at most 10 percent; no unknown-share bias flag against `B-RANDOM` or `B-LIQ` computed for that configuration in validation; no budget stop.
- Selection set: adequate configurations whose lower one-sided 95 percent bound of the primary metric is above zero both under the known-outcome metric and under `U-CONS`. Each bound is the lower of the token-scheme and day-scheme bounds (section 10.2).
- Selection: from the selection set, the configuration with the highest known-outcome lower bound; ties break by fewer grid deviations from `STRONG_ACTIVITY`, `K = 3`, `W = 5 min`, then by the lexicographically smallest configuration identifier.
- Empty selection set: the family does not open its holdout and is classified under section 12.2 as `NOT_SUPPORTED_IN_VALIDATION` or `INCONCLUSIVE`.

### 10.5 Outlier dependence

Report the primary metric with the five largest positive contributing tokens removed, and concentration by token, wallet and cluster. Heavy-tailed token returns make a result carried by a handful of tokens unreliable; if the point estimate is not above zero without those five tokens, the result cannot be `SUPPORTED_FOR_SHADOW`.

### 10.6 Minimum detectable effect

The minimum sample (100 positions, 30 token clusters) is a floor, not a power guarantee. Under a stated assumption not taken from outcome data, a per-position net-return standard deviation of 50 percent and an intra-token correlation of 0.3: mean cluster size 100 / 30 = 3.33, design effect 1 + 2.33 x 0.3 = 1.70, effective size about 59, standard error about 6.5 percent. Under these assumptions, a positive mean tested against zero at 80 percent power is detectable from about 18 percent at the Holm first-step level 0.025 and about 16 percent at 0.05, well above the 4.5 percent effect bar. These figures are approximate power for that single test only; they are not the probability of passing all protocol conditions jointly (baselines, `U-CONS`, outlier, bias and portfolio conditions), and actual joint power is lower. Detecting 4.5 percent in that single test at 80 percent power would need about 970 effective positions, about 1,650 positions at that cluster size; the required size depends on the true dispersion and dependence. Consistent with the owner's bounded-pilot decision, no larger threshold is added: a result at the floor can support only a large effect, and a smaller true effect will often end `INCONCLUSIVE` (`UNDERPOWERED`). Reports state this.

## 11. Preregistered baselines

| ID | Baseline |
|---|---|
| `B-RANDOM` | 100 seeded random wallet sets of equal size from the same point-in-time candidate pool and prefilter |
| `B-LIQ` | 100 seeded replications of random entries into universe tokens matched by liquidity bucket and entry hour |
| `B-REGIME` | Market regime reference over the same windows: SOL and an equal-weight universe reference |
| `B-ZEROLAG` | Zero-lag entry at the source wallet's fill; an unattainable reference showing lag decay, never a result |
| `B0` | First-slice synthetic flat haircut, labelled synthetic |

Baselines use the same universe, statuses and precedence, entry wait, cost model, entry size, portfolio rules and deduplication as the tested families, and report their unknown shares. P1 reuses these definitions unchanged.

## 12. Result classes and stop rules

### 12.1 Support conditions and failure reasons

Each condition is evaluated on the holdout. Conditions marked `*` must hold under the known-outcome metric and again under `U-CONS`.

| Failure code | Condition |
|---|---|
| `D1_FAILED` | D1 gate and depth rule passed |
| `SAMPLE_BELOW_FLOOR` | At least 100 positions and 30 distinct tokens (token clusters, section 10.2), after deduplication and interval attribution |
| `UNKNOWN_SHARE_ABOVE_MAX` | Unknown share at most the maximum (`OD-6`) |
| `BIAS_FLAG` | No unknown-share bias flag |
| `HOLM_NOT_REJECTED` | Holm-adjusted one-sided test with `m = 2` rejects (section 10.2) `*` |
| `NO_ADVANTAGE_B_RANDOM` | Baseline-difference lower bound above zero against `B-RANDOM` `*` |
| `NO_ADVANTAGE_B_LIQ` | Baseline-difference lower bound above zero against `B-LIQ` `*` |
| `EFFECT_BELOW_BAR` | Point estimate at least the 4.5 percent minimum effect `*` |
| `TOP_TOKEN_DEPENDENCE` | Positive point estimate without the five largest contributing tokens `*` |
| `U_CONS_FAILURE` | Set in addition when a `*` condition holds under the known-outcome metric but fails under `U-CONS` |
| `PORTFOLIO_NOT_POSITIVE` | Portfolio net return after data cost above zero, in the known-outcome and the `U-CONS` portfolio (section 10.1) `*` |
| `DRAWDOWN_HALT` | Portfolio drawdown halt not triggered, in the known-outcome and the `U-CONS` portfolio `*` |

Data are adequate when `D1_FAILED`, `SAMPLE_BELOW_FLOOR`, `UNKNOWN_SHARE_ABOVE_MAX` and `BIAS_FLAG` are all absent and no budget stop occurred. For every result other than `SUPPORTED_FOR_SHADOW`, the report records every failure code that applies (multi-valued), together with the portfolio results. A poor portfolio result is its own reason and is never reduced to, inferred from or hidden by the mean-return interval.

### 12.2 Result classes

The first matching class applies:

| Order | Class | Condition |
|---|---|---|
| 1 | `EXPLORATORY` | Result affected by a protocol deviation, restricted to synthetic costs, or produced outside the frozen path |
| 2 | `SUPPORTED_FOR_SHADOW` | Every condition of section 12.1 holds |
| 3 | `INCONCLUSIVE` (reason `DATA_INSUFFICIENT`, or `BUDGET_STOP`) | Data are not adequate |
| 4 | `NOT_SUPPORTED` | Data are adequate and the upper one-sided 95 percent bound of the known-outcome primary metric (the higher of the two bootstrap schemes) is below 4.5 percent: evidence against the chosen economic effect |
| 5 | `INCONCLUSIVE` (reason `UNDERPOWERED`) | Data are adequate, support conditions are not all met, and that upper bound is at least 4.5 percent, so the interval still admits the chosen effect |

`NOT_SUPPORTED` is judged on the known-outcome metric only. A `U-CONS` failure blocks support but does not by itself prove the absence of a real edge; with an upper bound still admitting 4.5 percent the result is `INCONCLUSIVE` (`UNDERPOWERED`) with `U_CONS_FAILURE` recorded. Under the bounded-pilot decision this outcome is expected to be common for small true effects.

Validation, when no configuration meets section 10.4 (holdout stays untouched in both cases):

- `NOT_SUPPORTED_IN_VALIDATION`: the set of adequate configurations (section 10.4) is non-empty and every configuration in it has an upper one-sided 95 percent bound of the known-outcome metric (the higher of the two schemes) below 4.5 percent: validation evidence against the effect.
- `INCONCLUSIVE` with reason `VALIDATION_UNDERPOWERED`: the adequate set is non-empty and some configuration in it has that upper bound at or above 4.5 percent.
- `INCONCLUSIVE` with reason `DATA_INSUFFICIENT`: the adequate set is empty.

Failure codes are recorded per eligible configuration, including inadequate ones.

The 4.5 percent minimum effect is an owner choice (`OWNER-APPROVED 2026-10-01`) of an economically meaningful mean net effect per position after modeled costs; it is not derived from data or from any stop distance, since `R1-E1` has no stop rules.

### 12.3 Next step after a result

- `SUPPORTED_FOR_SHADOW`: prospective forward shadow (S1) is the default next step, only as a further test; it is not a found edge or evidence of stable future returns.
- `INCONCLUSIVE`: does not lead to S1 automatically. Moving to S1 is a separate, justified owner decision; shadow may also lack sample, and no claim that an edge was found is allowed.
- `NOT_SUPPORTED` or `NOT_SUPPORTED_IN_VALIDATION`: no S1 for that family under `R1-E1`; a changed hypothesis is a new experiment.
- `EXPLORATORY`: no confirmatory claim and no S1 basis.

### 12.4 Stop rules and holdout evaluation

- D1 gate or depth-rule failure stops the affected hypothesis as `INCONCLUSIVE`; narrow the hypothesis or extend data under a new experiment, never tune thresholds.
- Reaching any budget ceiling (`OD-2`), or an inventory cost above it, stops work at the next consistent checkpoint as `INCONCLUSIVE`, preserving all evidence.
- A family without a validation configuration meeting section 10.4 does not open its holdout, whether classified `NOT_SUPPORTED_IN_VALIDATION` or `INCONCLUSIVE`.
- Holdout evaluation is single-shot with respect to rules: after any holdout outcome has been viewed, no rule, code, configuration, input or threshold may change and be followed by another holdout evaluation in the same experiment. At most one technical rerun after an interrupted attempt is allowed, and only if (a) its inputs are identical and frozen (analysis code commit, configuration fingerprint, dataset manifest fingerprint, seeds), (b) no outcome output of the interrupted attempt was viewed, and (c) both attempts are recorded in the run receipt. Otherwise the experiment closes with what was produced, or as `INCONCLUSIVE`.
- A result-affecting deviation stops confirmatory claims for the affected result (`EXPLORATORY`).

## 13. Report lineage

Every R1 report records provider/raw and decoded availability, availability class (`OBSERVED_LIVE` or `MODELED` with model version), transformation, policy, configuration and build versions, cutoffs, universe and service-identity versions, interval boundaries, status and exclusion counts with loss-of-exit check results, `DEDUPED` and `BOUNDARY_CROSSING` counts per family, configuration, interval and scenario for the strategy and every baseline, the `C-3` availability-model version and offset, public-label sources with versions, content hashes and availability confirmation, unknown shares of the strategy and every baseline, `U-CONS` results, result class with its reason and every applicable failure code, portfolio results, clustering variants, trial ledger size, seeds, cost-model versions, data cost, field-availability inventory reference, freeze record entries, deviation log and complete provenance as required by [Reproducibility](../REPRODUCIBILITY.md). Results with real receipt time and with modeled availability are never silently combined.

## 14. Owner decisions (all values `OWNER-APPROVED 2026-10-01`; holdout owner-attested 2026-10-01)

| ID | Parameter | Value | Justification and source | Status |
|---|---|---|---|---|
| `OD-1` | Envelope lower bound | 1 minute entry lag | Reasoning: excludes sub-minute latency races (replan note R1) while keeping "minutes" of envelope B | `OWNER-APPROVED 2026-10-01` |
| `OD-1` | Entry-lag scenarios after `t_avail` | Primary 5 min; sensitivity 1 min, 15 min, 60 min | Reasoning: manual reaction on a minutes scale; spans the envelope's minutes-to-hour range; replaced by S1 measurements, never by fitting | `OWNER-APPROVED 2026-10-01` |
| `OD-1` | Maximum entry wait after `t_entry` | 2 minutes from the computed `t_entry`; beyond it `ENTRY_UNKNOWN` (`ENTRY_WAIT_EXPIRED`) when quote evidence is missing, or `NOT_ENTERED` when infeasibility is proven | Reasoning: keeps each scenario's effective lag below the next scenario (1 to 3 min, 5 to 7 min, 15 to 17 min) so scenarios do not overlap | `OWNER-APPROVED 2026-10-01` |
| `OD-1` | Entry size | Primary USD 500; sensitivity USD 100 and USD 2,000 | Roadmap section 20 draft `SMART_WALLET_BUY` USD 500 size reused as the copier notional; sensitivity shows capacity | `OWNER-APPROVED 2026-10-01` |
| `OD-1` | Minimum live pool liquidity at entry | USD 50,000; sensitivity USD 10,000 | First-slice friction tier boundary (evaluation module, Roadmap section 21); USD 10,000 is the Roadmap `LIQUIDITY_SPIKE` absolute constant | `OWNER-APPROVED 2026-10-01` |
| `OD-1` | Maximum modeled entry impact | 5 percent; above it `NOT_ENTERED` | Roadmap section 21 highest friction tier as an upper cost anchor | `OWNER-APPROVED 2026-10-01` |
| `OD-2` | D1 spending ceiling | USD 100 cash; 14 days wall-clock; 10 GB local evidence with at least 30 GB free; checkpoint at 80 percent of any ceiling, hard stop at 100 percent | A ceiling, not a cost estimate; actual source costs are unknown until the field-availability inventory. Storage reuses the owner-approved F1 evidence cap; cash and time are a reasoning-based minimum | `OWNER-APPROVED 2026-10-01` |
| `OD-2` | Prospective shadow ceiling | USD 50 per month for at most 60 days, decided again at S1 planning | Reasoning: finite separate ceiling as the replan note recommends | `OWNER-APPROVED 2026-10-01` |
| `OD-2` | Allowed sources | Sources identified by the field-availability inventory that provide the required fields within the ceiling and whose terms permit local retention of exports for reproduction; candidates include a decoded DEX-trade table (Dune `dex_solana.trades`, named in the replan note) and raw transactions or account state from an RPC or archive source for reserves, transfers and the cross-check | Replan note D1 section; adequacy decided by section 8 | `OWNER-APPROVED 2026-10-01` |
| `OD-3` | Portfolio bounds | Capital USD 10,000 per family; position size equals entry size; at most 20 concurrent positions; one open position per token; at most 3 concurrent positions triggered by one cluster; halt new entries at 25 percent drawdown from peak | Reasoning: capital / USD 500 = 20 positions; cluster cap limits correlated exposure to 15 percent; halt bounds a research portfolio, not a trading policy | `OWNER-APPROVED 2026-10-01` |
| `OD-4` | Offline procedure | [R1 offline research procedure](R1_OFFLINE_RESEARCH_PROCEDURE.md) | Replan note open question 4; blocking | `OWNER-APPROVED 2026-10-01` |
| `OD-5` | Ranges and folds | Section 4.1 dates; two 30-day validation folds; 2-day embargo; 28-day holdout (owner-attested 2026-10-01) | Accepted 180-day envelope; most recent period as holdout; embargo exceeds 24 h horizon plus tolerance | `OWNER-APPROVED 2026-10-01` |
| `OD-6` | Statistical constants | One-sided level 0.05 with Holm; 10,000 bootstrap resamples; minimum 100 positions and 30 token clusters; minimum effect 4.5 percent; remove top 5 tokens; maximum unknown share 10 percent; unknown-share bias margin 2 percentage points; D1 thresholds and depth-rule coverage of 90 percent (section 8) | Test level conventional (reasoning); 4.5 percent is an owner-chosen economically meaningful effect; bias margin is one fifth of the allowed unknown share (reasoning); others reasoning for a bounded pilot; section 10.6 states power | `OWNER-APPROVED 2026-10-01` |
| `OD-6` | Power assumption | Per-position standard deviation 50 percent, intra-token correlation 0.3 | Stated assumption, not taken from outcome data; used only for the section 10.6 statement | `OWNER-APPROVED 2026-10-01` |
| `OD-7` | Selection and grid | Copier-replicable `U-CONS` score ranking (section 5.2); prefilter `STRONG_ACTIVITY`/`PROMISING_ACTIVITY`; up to 50 wallets with positive score; 90-day lookback; H1 trigger notional at least USD 500; H2 `K = 3`, `W` in {5 min, 60 min}, clustering `V1` (presumed linkage); heuristic mass-service funder threshold of more than 50 funded wallets; public labels only with saved source and version, manifest content hash and confirmed availability before the decision; dedup 24 h; no exit rules; `OWN_PF` comparison only | Roadmap sections 19 and 20 draft activity and trigger constants; 60 min reflects envelope B hours; 50 wallets and the service threshold are reasoning (the threshold is a heuristic); grid of 2 H1 and 4 H2 configurations | `OWNER-APPROVED 2026-10-01` |
| `OD-8` | Primary horizon | `4h` for both families; `1h` and `24h` secondary | Reasoning: at the 5 min primary lag, `1h` is dominated by entry timing, while `24h` adds regime drift; `4h` is the middle declared horizon | `OWNER-APPROVED 2026-10-01` |
| Attestation | Holdout not viewed | `OWNER-ATTESTED 2026-10-01`: no viewing of wallet or token returns for 2026-08-31 to 2026-09-28 and no use of that period to choose rules | Owner's personal attestation, section 4.1 | `OWNER-ATTESTED 2026-10-01` |

### 14.1 Review repair round 1 clarifications (owner-approved 2026-10-01)

| ID | Parameter | Value | Justification | Status |
|---|---|---|---|---|
| `OD-6a` | Holm family size | `m = 2` fixed at preregistration; a single family reaching holdout is tested at 0.025 | Family size follows the preregistered hypotheses, not validation outcomes, so the test level cannot depend on validation results; clarifies the approved "0.05 with Holm" | `OWNER-APPROVED 2026-10-01` |
| `C-3` | Default modeled availability offset and bound | Modeled offset, labelled `MODELED` with model version, never a measurement: default 60 s; refined value max(60 s, measured 90th percentile), at most 600 s; above 600 s D1 fails | 60 s is a conservative assumption at the envelope's 1-minute lower bound, not a measurement; refinement may only make availability later, never earlier; above 600 s visibility would exceed twice the 5-minute primary lag and dominate the scenario | `OWNER-APPROVED 2026-10-01` |
| `OD-7a` | Deduplication scope and key | Key family, configuration and token; 24 h from the last retained trigger's `t_avail`, regardless of entry status; duplicates excluded from positions, sample floor, unknown share and portfolio; same rule for baselines; `DEDUPED` counted and reported separately per family, configuration, interval and scenario for strategy and baselines (section 10.1) | The approved "dedup 24 h" fixes the window but not key, anchor or scope; a signal-level rule keeps the metric, unknown share and portfolio on one position set and cannot select entries by outcome | `OWNER-APPROVED 2026-10-01` |
| `ID-LABEL` | Public address labels | Allowed only with saved source and exact version, manifest content hash, and confirmed availability before the selection cutoff or `knownAt`; otherwise not used (section 5.1) | Owner decision replacing the round 1 proposal to exclude all third-party labels | `OWNER-APPROVED 2026-10-01` |
| `PROC-0.3` | Offline procedure `0.3.0-draft` content | At most one holdout technical rerun; defect correction recorded as a new holdout-freeze entry | Consistency with protocol sections 2.3, 2.5 and 12.4 | `OWNER-APPROVED 2026-10-01` |

The replan note's open question 3 (frozen member-list capacity) belongs to the A1+A2 change, not to R1.

## 15. Changelog

| Version | Date | Change |
|---|---|---|
| `0.1.0-draft` | 2026-10-01 | Initial draft under the owner's parallel-start decision |
| `0.2.0-draft` | 2026-10-01 | Owner decisions recorded: one-time RED documentation exception, proposed values for `OD-1`..`OD-8`, blocking offline procedure, mandatory order, D1 calibration whitelist |
| `0.3.0-draft` | 2026-10-01 | Owner-adopted external methodology feedback: copier-replicable wallet ranking with `OWN_PF` as comparison only; valuation-window loss-of-exit rule, status precedence and maximum entry wait; `U-CONS` conservative bound and unknown-share bias flag; field-availability inventory and preregistered depth rule; mass-service funders, linkage versus indicators and clustering variants; exact statistical procedure and minimum-detectable-effect statement; holdout attestation and technical rerun rule; 4.5 percent relabelled as an owner-chosen effect |
| `0.4.0-draft` | 2026-10-01 | Owner decisions: bounded pilot (scope option a) with unchanged budgets, floor and effect bar; holdout owner-attested. Result classes with `NOT_SUPPORTED` only on adequate data with the upper bound below 4.5 percent, `INCONCLUSIVE` reasons (`UNDERPOWERED`, `VALIDATION_UNDERPOWERED`, `DATA_INSUFFICIENT`, `BUDGET_STOP`), multi-valued failure codes and a validation-stage distinction; power wording limited to the single test; `U-CONS` described as a stress scenario; presumed linkage and heuristic service threshold; entry wait from computed `t_entry` with `ENTRY_UNKNOWN` versus `NOT_ENTERED`; S1 transition rules |
| `0.5.0-draft` | 2026-10-01 | Owner approval recorded: every `OD-1`..`OD-8` value of `0.4.0-draft` and the offline procedure `0.2.0-draft` approved without adjustment; statuses changed from `PROPOSED` to `OWNER-APPROVED 2026-10-01`. No semantic or numeric change; status stays `DRAFT` until freeze |
| `0.6.0-draft` | 2026-10-01 | Review repair round 1: eligible grid, per-configuration validation adequacy, selection set with both lower bounds and named scheme, non-empty adequate set for `NOT_SUPPORTED_IN_VALIDATION`; Holm `m = 2` (`PROPOSED`); token cluster = token; signal deduplication scope (`PROPOSED`); interval attribution by `t_entry` with `BOUNDARY_CROSSING`; known-outcome and `U-CONS` portfolio variants both gating; `C-3` default and bound (`PROPOSED`); identity criteria frozen at `1.0.0` without third-party labels; first-SOL-funder definition; at most one holdout technical rerun; defect correction as a new holdout-freeze entry; canonical event order; account-independent loss-of-exit evidence; extraction tail includes the entry wait |
| `0.7.0-draft` | 2026-10-01 | Owner decisions on round 1 items: Holm `m = 2`, `C-3` (as a labelled modeled offset), deduplication with separate `DEDUPED` reporting and procedure `0.3.0-draft` content approved; public address labels allowed only with saved source and version, manifest content hash and confirmed availability before the decision, replacing the blanket exclusion |
| `1.0.0` | 2026-10-01 | `FROZEN`. Content-identical to the reviewed `0.7.0-draft` except the header version, status, frozen-date, freeze-record and offline-procedure lines and this changelog entry; no rule, value or wording of any rule changed |
