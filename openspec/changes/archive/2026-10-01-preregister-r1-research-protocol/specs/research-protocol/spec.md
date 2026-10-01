## Purpose

Defines the preregistration obligations that any wallet copier or early multi-wallet research evidence must satisfy to be reported as R1 evidence: versioned freeze before fitting, point-in-time copier-replicable selection, explicit outcome statuses, conservative treatment of unknown outcomes, adequate sources, realistic costs, finite capital, exact statistics and stop rules.

## ADDED Requirements

### Requirement: Versioned protocol freeze before outcome-bearing work
R1 research SHALL be governed by a versioned, dated protocol document and SHALL follow this order: protocol update, owner approval of every proposed value together with the owner's holdout attestation, final fresh independent review, the complete repository gate, protocol freeze at version `1.0.0`, and only then separately authorized data work. No R1 data work SHALL start before that freeze and before the owner has approved the offline research procedure defining stage changes, script location, checks, budget, OpenSpec applicability boundary, receipts, secrets handling and review. No outcome-bearing quantity (return, PnL, hit rate, expectancy, drawdown, profit factor or a ranking derived from them, including copier-replicated returns used for wallet selection) SHALL be computed before the protocol freeze, the passed D1 data gate and the recorded calibration amendment. The primary metric, success thresholds and wallet-selection rules SHALL be fixed in the `1.0.0` freeze. Each freeze SHALL be recorded outside the protocol file with the UTC date, protocol version, git commit, SHA-256 of the protocol file bytes at that commit, approved owner decisions, the holdout attestation and the review reference. The protocol SHALL NOT be frozen while any proposed value lacks owner approval.

#### Scenario: Draft protocol cannot authorize extraction
- **WHEN** the protocol status is `DRAFT` or any proposed value lacks owner approval
- **THEN** no R1 data extraction or outcome-bearing computation SHALL be treated as R1 evidence
- **AND** the protocol SHALL NOT be recorded as frozen.

#### Scenario: Offline procedure not approved
- **WHEN** the protocol is frozen but the owner has not approved the offline research procedure
- **THEN** no R1 data work SHALL start.

#### Scenario: Holdout attestation unavailable
- **WHEN** the owner cannot attest that wallet or token returns for the proposed holdout period were not viewed
- **THEN** the holdout SHALL be moved or shrunk to an attestable period before freeze
- **AND** the protocol SHALL NOT be frozen with the unattested holdout.

#### Scenario: Report cites frozen protocol
- **WHEN** an R1 report is produced
- **THEN** it SHALL cite the protocol version and freeze record entry whose commit and SHA-256 match the protocol text used
- **AND** a report without a matching freeze record entry SHALL be classified `EXPLORATORY`.

### Requirement: Controlled amendments and single-shot holdout
After protocol freeze, the only permitted amendment within the same experiment SHALL refine the pre-enumerated data-quality and data-availability values (horizon price tolerance windows, supported venues with their reserve-input availability, and the modeled-availability offset, which has a frozen default and may only be refined to a later value within its declared upper bound) within their declared bounds, from D1 measurements that contain no outcome-bearing quantity. Any other change, including hypotheses, primary metric, success thresholds, wallet-selection rules, grid, statuses, cost-model forms, latency and entry-wait scenarios, unknown-outcome rules, clustering rules, portfolio bounds, statistical procedure, baselines, D1 gate thresholds, range dates or stop rules, SHALL start a new experiment identifier with a holdout no earlier experiment of that family has evaluated. Before holdout evaluation, the analysis code commit, canonical configuration fingerprint, dataset manifest fingerprint and seeds SHALL be recorded; a defect correction before any holdout outcome is viewed SHALL require a behavioral test and a new holdout-freeze entry with the new commit. At most one technical rerun of an interrupted holdout evaluation SHALL be permitted. After any holdout outcome has been viewed, no rule, code, configuration, input or threshold change SHALL be followed by another holdout evaluation in the same experiment, and the holdout SHALL NOT be extended.

#### Scenario: Post-viewing rule change
- **WHEN** validation results have been computed and a selection threshold is changed
- **THEN** the changed analysis SHALL run under a new experiment identifier
- **AND** the original experiment's results SHALL be reported unchanged.

#### Scenario: Calibration amendment cannot change selection or thresholds
- **WHEN** the post-D1 calibration amendment is prepared
- **THEN** it SHALL change only the enumerated data-quality and data-availability values within their bounds
- **AND** a proposed change to the primary metric, a success threshold or a wallet-selection rule SHALL require a new experiment identifier.

#### Scenario: Calibration needs outcome data
- **WHEN** a calibration value cannot be refined without an outcome-bearing quantity
- **THEN** the value SHALL keep its frozen default
- **AND** it SHALL NOT be calibrated on outcomes.

#### Scenario: Holdout technical rerun allowed
- **WHEN** a holdout evaluation is interrupted, its inputs (code commit, configuration fingerprint, dataset manifest fingerprint, seeds) are unchanged and frozen, and no outcome output of the interrupted attempt was viewed
- **THEN** at most one technical rerun SHALL be permitted
- **AND** both attempts SHALL be recorded in the run receipt.

#### Scenario: Defect correction before holdout viewing
- **WHEN** an implementation defect is corrected after the holdout freeze and before any holdout outcome is viewed
- **THEN** a new holdout-freeze entry with the new code commit SHALL be recorded before evaluation
- **AND** the correction SHALL have a behavioral test and a deviation-log entry.

#### Scenario: Holdout rerun after viewing
- **WHEN** any outcome output of a holdout attempt was viewed or any frozen input differs
- **THEN** a further holdout evaluation SHALL NOT be reported under the same experiment identifier.

### Requirement: Copier return after observation lag and modeled entry
The copier hypothesis SHALL measure the net return of the copier's own position from a modeled entry at the trigger availability instant plus the declared entry lag, valued with the venue-specific cost model. The entry wait window SHALL run from the computed copier entry instant, not from the signal, for the declared maximum entry wait, and entry SHALL use the earliest admissible executable price in that window. Without one, the outcome SHALL be `NOT_ENTERED` only when point-in-time evidence covering the whole window proves entry infeasible, and otherwise `ENTRY_UNKNOWN` with reason `ENTRY_WAIT_EXPIRED`. The source wallet's fill price, PnL or holding period SHALL NOT be reported as the copier's return.

#### Scenario: Source wallet profit is not copier return
- **WHEN** a selected wallet buys a token and later sells at a profit
- **THEN** the copier outcome SHALL be computed from the copier's modeled entry after the declared lag and costs
- **AND** a zero-lag source-fill result SHALL appear only as the labelled unattainable reference baseline.

#### Scenario: Entry wait bound with missing quotes
- **WHEN** no admissible executable entry price exists between the computed copier entry instant and the end of the maximum entry wait, and part of that window lacks historical quote evidence
- **THEN** the outcome SHALL be `ENTRY_UNKNOWN` with reason `ENTRY_WAIT_EXPIRED` and SHALL count in the unknown share
- **AND** a later price SHALL NOT be used as the entry.

#### Scenario: Proven entry infeasibility
- **WHEN** point-in-time reserve and route evidence covers the whole wait window and shows liquidity below the minimum throughout
- **THEN** the outcome SHALL be `NOT_ENTERED` with that reason
- **AND** it SHALL NOT count in the unknown share.

### Requirement: Point-in-time copier-replicable wallet selection
Wallet selection SHALL rank candidates by copier-replicable performance on permitted prior history only: each qualifying historical trigger buy replicated as a copier position with the frozen entry lag, entry rules, venue cost model and status rules, valued at the primary horizon so that open and unrealized positions are included, with unknown outcomes treated under the conservative scenario. The evaluated interval SHALL never be part of the selection history. Ranking by the source wallets' own closed-trade metrics SHALL be at most a prefilter or a reported comparison variant and SHALL NOT be eligible for holdout selection. Identity exclusion criteria (program-derived and program accounts, documented venue, aggregator and bridge accounts, mass-service criteria) SHALL be frozen at `1.0.0` and SHALL NOT use performance. Public address labels SHALL be used only when the label source and exact version are saved, the label content hash is recorded in the manifest, and the label's availability before the relevant selection cutoff or `knownAt` is confirmed; any other label SHALL NOT be used. Every wallet score, label and cluster version SHALL carry `knownAt`, and selection at cutoff `c` SHALL use only versions with `knownAt <= c`, fixed within an evaluated interval. Ranges SHALL be declared before freeze with an embargo at least as long as the longest horizon plus its tolerance. A trial ledger SHALL record every evaluated combination, including every ranking formula, prefilter and clustering variant.

#### Scenario: Retroactive winner selection
- **WHEN** a wallet performs well over a period that includes an evaluated interval
- **THEN** it SHALL NOT be treated as selected before that interval
- **AND** selection SHALL be reproducible from the frozen rule, history cutoff and dataset manifest.

#### Scenario: Source success unreachable at copier lag
- **WHEN** a wallet's own closed trades are profitable but its replicated copier positions after the frozen lag and costs are not
- **THEN** its selection score SHALL reflect the replicated copier positions
- **AND** an own-profit-factor ranking SHALL appear only as a comparison variant in the trial ledger.

#### Scenario: Label published after the selection cutoff is not used
- **WHEN** a public exchange or service label for an address first became available after the selection cutoff
- **THEN** that label SHALL NOT be used in that selection or its clustering
- **AND** a label without a saved source version or manifest content hash SHALL NOT be used at all.

#### Scenario: Late label version
- **WHEN** a wallet label version has `knownAt` after a selection cutoff
- **THEN** that version SHALL NOT participate in the selection at that cutoff.

### Requirement: Coordination clustering distinguishes linkage from indicators
Clustering SHALL merge wallets only on presumed linkage (a direct transfer between them, or a shared first SOL funder that is not a mass-service identity); such evidence proves a transaction link, not common ownership, and its effect SHALL be reported through sensitivity variants. Mass-service identities (exchanges, bridges, faucets, payment services and similar) SHALL be identified by a versioned identity rule frozen at `1.0.0`, using public labels only under the label conditions of wallet selection, with any numeric funding threshold labelled as a heuristic, and SHALL NOT cause merging. The first SOL funder SHALL be the sender of the earliest inbound SOL transfer observed inside the permitted history window; a wallet with activity or SOL balance in the window before that transfer SHALL be marked `FIRST_FUNDER_UNKNOWN` and SHALL NOT be merged on the shared-funder rule. Same-slot, same-bundle or near-simultaneous trading SHALL be treated as coordination indicators, not linkage. Early multi-wallet results SHALL be reported for the declared clustering variants.

#### Scenario: Mass-service funder does not merge
- **WHEN** two wallets share a first SOL funder identified as a mass-service identity
- **THEN** they SHALL NOT be merged on that basis
- **AND** they SHALL count as separate buyers unless presumed linkage exists.

#### Scenario: Funding before the history window
- **WHEN** a wallet trades in the permitted history window before its first observed inbound SOL transfer
- **THEN** it SHALL be marked `FIRST_FUNDER_UNKNOWN`
- **AND** it SHALL be merged only on a direct transfer, not on a shared funder.

#### Scenario: Co-occurrence alone
- **WHEN** two wallets repeatedly buy in the same slot without presumed linkage
- **THEN** they SHALL be merged only in the declared indicator-based sensitivity variant.

### Requirement: Economic trade reconstruction and position statuses
R1 evidence SHALL reconstruct one economic trade per trader action from route legs using net owned-token deltas, SHALL NOT count intermediate route tokens as purchases or sum route-leg volume, and SHALL preserve raw units with decimals, fees, tips where observable, venues, trader identity and source visibility. Trades that are not fully reconstructed and lots with unknown basis, open remainder, transfer-out or partial fills SHALL carry explicit counted statuses and SHALL NOT be assigned guessed values.

#### Scenario: Multi-hop aggregator route
- **WHEN** a transaction routes SOL to an intermediate token and then to the target token
- **THEN** it SHALL produce one economic buy of the target token
- **AND** the intermediate token SHALL NOT appear as a purchase and the volume SHALL NOT be double counted.

#### Scenario: Transfer without cost basis
- **WHEN** a wallet receives tokens by transfer without a known acquisition cost
- **THEN** the lot SHALL have status `UNKNOWN_BASIS`
- **AND** closed-trade metrics SHALL exclude it and report known-basis coverage.

### Requirement: Point-in-time universe and loss-of-exit outcome statuses
The R1 universe SHALL include every asset eligible at its inclusion instant, including later rejected, shadow, illiquid and dead assets, with counted exclusions and gaps. Outcome statuses SHALL be assigned in the declared precedence `NOT_ENTERED` or `ENTRY_UNKNOWN`, then `TERMINAL_NO_EXIT`, `PRICED`, `INCONCLUSIVE`, `UNPRICED`. An outcome SHALL be `TERMINAL_NO_EXIT` with return `-100%` when positive point-in-time evidence shows the position cannot be realized in the valuation window (horizon instant through tolerance end) with zero recovery, regardless of whether an exit existed earlier. Before an outcome is classified `INCONCLUSIVE` or `UNPRICED`, the loss-of-exit check SHALL be performed and its result recorded. Missing data SHALL NOT be evidence of loss, and no outcome SHALL be silently dropped. Measurement horizons `1h`, `4h` and `24h` SHALL be measured from the copier entry instant with declared per-horizon tolerance windows.

#### Scenario: Post-entry rug
- **WHEN** an exit route existed after entry but positive evidence shows the pool reserves were drained and no supported pool yields any output for the position throughout the valuation window
- **THEN** the outcome SHALL be `TERMINAL_NO_EXIT` with return `-100%`
- **AND** it SHALL NOT be classified `UNPRICED` or `INCONCLUSIVE`.

#### Scenario: Missing price is not a total loss
- **WHEN** no admissible valuation exists for a horizon and the loss-of-exit check finds no positive evidence
- **THEN** the outcome SHALL be `UNPRICED` or `INCONCLUSIVE` with the check result recorded
- **AND** it SHALL be counted and SHALL NOT be recorded as `-100%`.

#### Scenario: Partial recovery
- **WHEN** an admissible executable sell value above zero exists in the valuation window
- **THEN** the outcome SHALL be `PRICED` with the measured net return even if it is close to `-100%`.

### Requirement: Conservative unknown-outcome bound and unknown-share bias flag
R1 SHALL report the known-outcome metric and SHALL also compute the preregistered stress scenario `U-CONS`, in which each unknown (`UNPRICED` or `INCONCLUSIVE`) position of the tested strategy is assigned `-100%` and baseline unknowns are excluded, without changing statuses and without claiming a mathematically guaranteed bound. `ENTRY_UNKNOWN` outcomes SHALL count in the unknown share and the bias-flag comparison but SHALL NOT be stressed in `U-CONS`, because no position was opened. A support conclusion SHALL require every support condition (sign, Holm test, baseline differences, minimum effect and outlier test) to hold under both the known-outcome metric and `U-CONS`; a `U-CONS` failure SHALL block support but SHALL NOT by itself be reported as proof that no edge exists. The unknown shares of the strategy and of each comparison baseline SHALL be reported side by side; a strategy share exceeding either random or liquidity-matched baseline share by more than the frozen bias margin SHALL set a bias flag that blocks support. A strategy unknown share above the frozen maximum SHALL yield `INCONCLUSIVE`.

#### Scenario: Support blocked under conservative scenario
- **WHEN** 90 known positions average `+4.5%` net and 10 positions are unknown
- **THEN** the `U-CONS` mean SHALL be `-5.95%`
- **AND** the hypothesis SHALL NOT be classified `SUPPORTED_FOR_SHADOW`.

#### Scenario: Entry unknown is not stressed
- **WHEN** a strategy signal ends `ENTRY_UNKNOWN`
- **THEN** it SHALL count in the strategy's unknown share and bias-flag comparison
- **AND** it SHALL NOT be assigned `-100%` in `U-CONS`.

#### Scenario: Unknown-share bias flag
- **WHEN** the strategy's unknown share exceeds the random-wallet or liquidity-matched baseline's unknown share by more than the frozen margin
- **THEN** the bias flag SHALL be set and reported
- **AND** the hypothesis SHALL NOT be classified `SUPPORTED_FOR_SHADOW`.

### Requirement: Source adequacy and field-availability inventory
The first D1 deliverable SHALL be a field-availability inventory stating, for every required field (trade legs, transfers, fees and tips, reserves or depth over time, liquidity events, mint decimals and freeze authority, executable entry and exit inputs, SOL/USD price, block time and visibility latency), the source, covered dates, granularity, gaps, cost and retention terms, before bulk extraction. The budget ceiling SHALL be treated as a spending limit, not a cost estimate; inventory costs above it SHALL stop work before spending for an owner decision. Missing historical depth SHALL be handled by the preregistered depth rule: below the frozen coverage of trigger events by venues with reserve or depth inputs, D1 SHALL fail for cost-modelled analysis, and any synthetic-cost analysis SHALL be `EXPLORATORY` with the limitation stated.

#### Scenario: Trades without depth history
- **WHEN** the only historical source provides decoded trades and pool addresses but no reserve or depth history for most trigger events
- **THEN** the depth rule SHALL fail D1 for cost-modelled analysis
- **AND** the hypotheses SHALL be `INCONCLUSIVE/data insufficient` rather than supported by a flat haircut.

#### Scenario: Inventory cost above ceiling
- **WHEN** the inventory shows required fields cost more than the approved ceiling
- **THEN** no further spending SHALL occur until the owner decides.

### Requirement: Venue-specific costs and finite-capital portfolio
Primary R1 results SHALL use a research-only, venue-specific execution model covering lag, entry wait, size, measured depth or reserves, route, price impact and slippage, fees, tips, partial fills, infeasible entry or exit and capacity. A flat liquidity-tier haircut SHALL appear only as a labelled synthetic baseline. Primary results SHALL also be simulated as a finite-capital portfolio with concurrency, deduplication, correlated-exposure, drawdown, missed-order and data-cost accounting, without signing or placing orders, in two variants that both gate support: a known-outcome portfolio in which unknown positions occupy capital until the end of their valuation window and are released at 0 percent, and a `U-CONS` portfolio in which they are released at `-100%`.

#### Scenario: Reserve inputs unavailable
- **WHEN** a venue's required reserve or depth inputs are unavailable at the decision instant and the loss-of-exit check finds no positive evidence
- **THEN** the outcome SHALL be `UNPRICED` with reason `COST_UNMODELED`
- **AND** the primary analysis SHALL NOT substitute the flat haircut.

#### Scenario: Capital constraint blocks entry
- **WHEN** a signal arrives while the capital or exposure limits are exhausted
- **THEN** the portfolio simulation SHALL record a counted `MISSED_CAPITAL` order
- **AND** no transaction SHALL be signed or submitted.

### Requirement: Exact statistical procedure, baselines, result classes and stop rules
R1 SHALL use the preregistered statistical procedure: a percentile cluster bootstrap with recorded seed over tokens and over UTC days, using the more conservative scheme; one-sided p-values for mean net return above zero with Holm-Bonferroni at family size `m = 2` fixed at preregistration, so a single family reaching the holdout is tested at half the overall level; baseline differences computed as the strategy mean minus the mean of the baseline replications' means, with bootstrap draws taken jointly over the union of strategy and baseline clusters. Reports SHALL state the approximate single-test power for the minimum sample under a declared variance assumption not taken from outcome data, SHALL state that joint power across all protocol conditions is lower, and the minimum sample SHALL be a floor, not a power guarantee. R1 SHALL compare each hypothesis with preregistered baselines using the same universe, statuses, cost model and portfolio rules, and SHALL classify each hypothesis only as `SUPPORTED_FOR_SHADOW`, `NOT_SUPPORTED`, `NOT_SUPPORTED_IN_VALIDATION`, `INCONCLUSIVE` (with a reason) or `EXPLORATORY` under frozen criteria. `NOT_SUPPORTED` SHALL require adequate data (D1 passed, sample floor met, unknown share within the maximum, no bias flag) and an upper one-sided 95 percent bound of the known-outcome primary metric below the minimum effect; when support fails but that bound still admits the minimum effect, the result SHALL be `INCONCLUSIVE` with reason `UNDERPOWERED`. Every applicable failure reason (including portfolio result, drawdown halt, baseline advantage, top-token dependence, `U-CONS` failure, bias flag and unknown share) SHALL be recorded separately for every non-supported result, and a portfolio failure SHALL NOT be reduced to the mean-return interval. Eligible configurations SHALL be exactly the preregistered copier-replicable grid at the primary horizon, lag and size; validation adequacy SHALL be judged per configuration (D1 passed, sample floor, unknown share within the maximum, no bias flag, no budget stop); selection SHALL be restricted to adequate configurations whose known-outcome and `U-CONS` lower bounds (each the lower of the two bootstrap schemes) are both above zero. When no configuration qualifies, the family SHALL be `NOT_SUPPORTED_IN_VALIDATION` only if the adequate set is non-empty and every configuration in it has an upper bound below the minimum effect, and otherwise `INCONCLUSIVE` (`VALIDATION_UNDERPOWERED` for a non-empty adequate set, `DATA_INSUFFICIENT` for an empty one); its holdout SHALL NOT be opened. A failed D1 gate, failed depth rule or exhausted budget SHALL yield `INCONCLUSIVE`, never a no-edge claim, and gate criteria SHALL NOT be relaxed after extraction. `SUPPORTED_FOR_SHADOW` SHALL make prospective forward shadow the default next step only as a further test; an `INCONCLUSIVE` result SHALL NOT lead to forward shadow without a separate, justified owner decision, and no result SHALL be reported as a found edge.

#### Scenario: Data gate failure
- **WHEN** the D1 coverage or reconstruction criteria fixed at freeze are not met
- **THEN** the affected hypothesis SHALL be `INCONCLUSIVE/data insufficient`
- **AND** no threshold SHALL be relaxed to pass the gate.

#### Scenario: Validation evidence against the effect
- **WHEN** no configuration enters the selection set, the adequate set is non-empty and every adequate configuration's upper bound is below the minimum effect
- **THEN** the family SHALL be `NOT_SUPPORTED_IN_VALIDATION`
- **AND** its holdout SHALL remain unevaluated.

#### Scenario: Insufficient validation
- **WHEN** no configuration enters the selection set and either some adequate configuration's upper bound admits the minimum effect or no configuration is adequate
- **THEN** the family SHALL be `INCONCLUSIVE` with reason `VALIDATION_UNDERPOWERED` or `DATA_INSUFFICIENT` respectively
- **AND** its holdout SHALL remain unevaluated.

#### Scenario: Selection requires both lower bounds
- **WHEN** an adequate configuration has a known-outcome lower bound above zero but a `U-CONS` lower bound at or below zero
- **THEN** it SHALL NOT be selected for the holdout.

#### Scenario: Single family reaches holdout
- **WHEN** only one family opens its holdout
- **THEN** its one-sided test SHALL use level 0.025 under the fixed family size `m = 2`.

#### Scenario: Evidence against the chosen effect
- **WHEN** holdout data are adequate, support fails and the upper one-sided 95 percent bound of the known-outcome metric is below the minimum effect
- **THEN** the result SHALL be `NOT_SUPPORTED`
- **AND** every applicable failure reason SHALL be recorded.

#### Scenario: Underpowered result
- **WHEN** holdout data are adequate, support fails and the upper bound still admits the minimum effect
- **THEN** the result SHALL be `INCONCLUSIVE` with reason `UNDERPOWERED`
- **AND** it SHALL NOT be reported as evidence that no edge exists.

#### Scenario: Portfolio failure recorded separately
- **WHEN** the mean-return conditions hold but the portfolio drawdown halt is triggered
- **THEN** support SHALL be blocked and `DRAWDOWN_HALT` SHALL be recorded as a failure reason
- **AND** the report SHALL show the portfolio result separately from the mean-return interval.

#### Scenario: Inconclusive result and forward shadow
- **WHEN** a hypothesis result is `INCONCLUSIVE`
- **THEN** forward shadow SHALL NOT start automatically
- **AND** starting it SHALL require a separate, justified owner decision without any claim that an edge was found.

#### Scenario: Underpowered floor sample
- **WHEN** a report is produced at or near the minimum sample
- **THEN** it SHALL state the approximate single-test detectable effect under the declared variance assumption and that joint power is lower
- **AND** it SHALL state when that effect exceeds the minimum effect bar.

### Requirement: Counting units, deduplication, interval attribution and event order
Every gating count (sample floor, effective sample size, unknown share, bootstrap units and portfolio) SHALL use the same units: one token (mint) SHALL be one token cluster; triggers SHALL be deduplicated per family, configuration and token within 24 hours of the last retained trigger's availability instant, regardless of entry status, with duplicates counted as `DEDUPED`, excluded from positions, the sample floor, the unknown-share numerator and denominator and the portfolio, and reported separately per family, configuration, interval and scenario for the strategy and every baseline; every trigger, including non-entries, SHALL be attributed by its computed entry instant, and a trigger whose availability and entry instants fall in different intervals, or either of which falls in the embargo, the warm-up or outside the envelope's entry intervals, SHALL be counted as `BOUNDARY_CROSSING` and not evaluated. Same-instant ties SHALL use one canonical total order (slot, transaction index, outer instruction index, inner-instruction stack path, leg ordinal) for entry and exit selection, group completion, FIFO lots and deduplication.

#### Scenario: Duplicate trigger
- **WHEN** a second trigger on the same token for the same family and configuration becomes available 3 hours after a retained trigger
- **THEN** it SHALL be counted as `DEDUPED`
- **AND** it SHALL NOT enter the primary metric, sample floor, unknown share or portfolio
- **AND** it SHALL appear in the separately reported `DEDUPED` count for its family, configuration, interval and scenario.

#### Scenario: Trigger crossing an interval boundary
- **WHEN** a trigger becomes available in the last minutes of validation fold 2 and its computed entry instant falls in the embargo
- **THEN** it SHALL be counted as `BOUNDARY_CROSSING`
- **AND** it SHALL NOT be evaluated in any interval.

#### Scenario: Same-slot group completion
- **WHEN** two selected wallets' buys of one token fall in the same slot and either could complete an H2 group
- **THEN** the buy earlier in the canonical order SHALL complete the group.

### Requirement: Report lineage and availability classes
Every R1 report SHALL record source and decoded availability, availability class (`OBSERVED_LIVE` or `MODELED` with the availability-model version and offset, never presented as a measurement), `DEDUPED` and `BOUNDARY_CROSSING` counts, public-label sources with versions and content hashes, transformation, policy, configuration and build versions, cutoffs, universe and service-identity versions, split and fold boundaries, status and exclusion counts with loss-of-exit check results, unknown shares of the strategy and every baseline, `U-CONS` results, clustering variants, trial ledger size, seeds, cost-model versions, data cost, field-availability inventory reference, freeze record entries and complete provenance. Results based on real receipt time and on modeled availability SHALL NOT be silently combined.

#### Scenario: Mixed availability evidence
- **WHEN** a report includes outcomes with observed live receipt time and outcomes with modeled availability
- **THEN** it SHALL present them separately or state an explicit combination rule with its sensitivity
- **AND** each outcome SHALL retain its availability class.

#### Scenario: Modeled availability offset
- **WHEN** historical evidence uses the default or refined modeled availability offset
- **THEN** the report SHALL label it `MODELED` with the model version and offset value
- **AND** SHALL NOT describe it as measured receipt time.
