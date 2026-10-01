## Context

See proposal.md for motivation. The accepted `solana-data-contract` spec already fixes point-in-time universe membership, historical ranges with warm-up separation and the 180-day envelope; `research-reproducibility` fixes provenance and exact arithmetic; `signal-evaluation` implements only a `1h` outcome. Links are avoided in this change's artifacts because their directory moves at archive. The active A1+A2 change `version-market-facts-and-split-evidence` modifies `marketdata-storage`, `recorded-market-replay`, `signal-evaluation` and `research-reproducibility` and is implemented separately. No application code, migration or wallet analytics exists for copier research, and no provider has been selected.

## Goals / Non-Goals

**Goals:**

- A dated, versioned protocol that an independent reviewer can apply before any return is seen to decide what counts as supported, negative or inconclusive (F5 exit condition).
- An explicit order (update, owner approval, fresh review, gate, freeze, then data work), freeze lifecycle and sourced proposed owner values, so that no value is silently assumed.

**Non-Goals:**

- Implementing, scheduling or approving D1/P1 work, purchases, scripts or provider runs.
- Choosing statistical constants, latency ranges, budgets or portfolio bounds on the owner's behalf.

## Decisions

### Classification: CORE_RISK, documentation only

All twelve MULTIAGENT triggers were assessed. The protocol defines point-in-time admissibility of wallet labels and splits (TR-06), outcome value semantics including `-100%`, `UNPRICED` and cost modelling (TR-07), economic trade identity and FIFO ordering (TR-08), gap and missing-source handling for research evidence (TR-09) and the integrity of research evidence itself (TR-11). These are methodology-level matches, but the protocol is the controlling rule set for future research evidence, so they are credible matches and select CORE_RISK. TR-01..TR-05, TR-10 and TR-12 do not match: no persistence, transaction, concurrency, retry, migration, module boundary or secret is changed. Lower tiers were rejected because NORMAL/CONTRACT would route an integrity-defining preregistration to same-author review. Documentation-only work stays with Architect; review is a fresh Reviewer with the full CI matrix.

### Location and language

The protocol lives in `docs/research/R1_RESEARCH_PROTOCOL.md`, in English, because it is binding methodology rather than a Russian-language note. The alternative, keeping it inside this change's `design.md`, was rejected: the change moves to the archive while the protocol must stay at a stable, citable path. The replan note receives one Russian pointer line.

### New capability instead of modifying research-reproducibility

The obligations are added as a new `research-protocol` capability. Modifying `research-reproducibility` or `signal-evaluation` would overlap the active A1+A2 deltas on the same specs. The new capability constrains R1 evidence and reports, not existing application behavior.

### Freeze record outside the protocol

A self-hash inside the protocol is impossible without an exclusion rule. The freeze record is a separate file created at freeze, holding commit and SHA-256 of the protocol bytes at that commit; later entries cover the D1 calibration amendment and holdout freeze. The protocol and specs avoid links to active change directories, whose paths move at archive.

### Proposed owner values versus the post-D1 whitelist

On the owner's 2026-10-01 direction, the Architect proposes a minimal concrete value for each owner decision `OD-1`..`OD-8`, marked `PROPOSED` with its source (accepted document, Roadmap draft constant, replan note, or explicit reasoning). None is measured or derived from returns, and freeze requires owner approval of each. The primary metric, success thresholds and wallet-selection rules are fixed at `1.0.0`. The `1.1.0` amendment may refine only the enumerated whitelist: `C-1` horizon tolerances within `[default, 2 x default]`, `C-2` venue reserve-input availability (pricing forms fixed), and `C-3` the measured latency number in the fixed modeled-availability rule. Every other change starts a new experiment. Alternative considered: leaving values as open placeholders; rejected by the owner because a concrete minimal proposal is faster to approve or adjust.

### Offline procedure as a separate document

`OD-4` is a blocking procedure rather than a protocol parameter, so it lives in `docs/research/R1_OFFLINE_RESEARCH_PROCEDURE.md`, keeping the protocol bounded. It never relaxes the workflow. It uses one OpenSpec change per stage (D1, P1) with several bounded runs of reviewed scripts. Main's complete gate runs on any code change and at stage closure; a receipt-only commit needs `git diff --check` and the repository conventions test; each run checks budget, manifest and reproducibility; RED-first behavioral tests cover every calculation. A per-run change was rejected as heavier without adding evidence. Raw exports stay outside the git working tree, so no ignore-configuration change is needed.

### Methodology corrections adopted by the owner (0.3.0-draft)

- Wallet selection ranks by copier-replicable performance on prior history (frozen lag, entry rules, cost model, horizon valuation that marks open losers, unknowns under `U-CONS`). Own closed-trade profit factor is a comparison variant only, because it measures source success that may be unreachable at copier lag and ignores open losing remainders. This extends D1 data needs to the whole envelope.
- `TERMINAL_NO_EXIT` is judged in the valuation window, so a post-entry rug counts as `-100%`. A fixed status precedence and a recorded loss-of-exit check before any unknown status prevent provable losses from becoming `UNPRICED`. A maximum entry wait bounds entry.
- Excluding unknowns can manufacture a paper edge. Support therefore must survive `U-CONS`, in which strategy unknowns are `-100%` and baseline unknowns are excluded, a specific preregistered stress scenario rather than a guaranteed bound (refined in `0.4.0-draft`). The unknown-share comparison against `B-RANDOM`/`B-LIQ` with a fixed absolute margin is a blocking bias flag. Alternative considered: imputing unknowns from known outcomes; rejected because it assumes unknowns resemble knowns, which is the bias being guarded against.
- A decoded trade table lacks reserve/depth history, so D1 starts with a field-availability inventory, and a preregistered 90 percent depth-coverage rule decides between proceeding and D1 failure. The budget is a ceiling, not an estimate.
- Clustering merges only on presumed linkage (renamed in `0.4.0-draft`); mass-service funders never merge, and co-occurrence is an indicator used in a sensitivity variant. The statistical procedure (bootstrap units, p-value, joint baseline-difference draws, Holm) is specified exactly, and a single-test power statement from a declared non-outcome variance assumption shows the minimum sample is a floor, not a power guarantee.
- The holdout requires an owner attestation and is single-shot with respect to rules; a technical rerun is allowed only with identical frozen inputs, no viewed output and both attempts receipted. The 4.5 percent effect bar is an owner-chosen economic threshold; the earlier stop-distance derivation was dropped because `R1-E1` has no stops.

### Owner decisions and result semantics (0.4.0-draft)

- Scope option (a): a bounded pilot. Budgets, the 100-position/30-cluster floor and the 4.5 percent bar are unchanged and no larger threshold is added; small true effects will often end `INCONCLUSIVE` (`UNDERPOWERED`). Alternative considered: raising the floor to the single-test power requirement (about 1,650 positions); rejected by the owner as outside the bounded pilot.
- Holdout attestation: owner-attested on 2026-10-01 for 2026-08-31 to 2026-09-28, including no use of that period to choose rules; copied into the freeze-record template. Other values stayed `PROPOSED` in `0.4.0-draft`.
- `NOT_SUPPORTED` means evidence against the chosen effect: adequate data and an upper one-sided 95 percent bound below 4.5 percent on the known-outcome metric. A failed support condition with an interval still admitting 4.5 percent is `INCONCLUSIVE` (`UNDERPOWERED`). Failure reasons are recorded as multi-valued codes so that portfolio, baseline, outlier, `U-CONS`, bias and unknown-share failures stay visible. Validation distinguishes `NOT_SUPPORTED_IN_VALIDATION` from `INCONCLUSIVE` (`VALIDATION_UNDERPOWERED` or `DATA_INSUFFICIENT`).
- `U-CONS` is described as a specific preregistered stress scenario, not a guaranteed bound, because excluding baseline unknowns is not in general favorable to baselines. Its failure blocks support without proving the absence of an edge.
- A shared funder or direct transfer is presumed linkage (a transaction link, not proven common ownership); the more-than-50-funded-wallets service rule is a heuristic.
- The entry wait runs from the computed `t_entry`. Missing quotes give `ENTRY_UNKNOWN`, which counts in the unknown share and bias flag but is not stressed in `U-CONS`, since no position or capital exists to stress. Proven infeasibility gives `NOT_ENTERED`.
- Power figures describe one test against zero; joint power across all conditions is lower.
- Only `SUPPORTED_FOR_SHADOW` makes S1 the default next step, as a further test; `INCONCLUSIVE` needs a separate justified owner decision for S1.

### Review repair round 1 (0.6.0-draft)

- Configuration selection uses one eligible grid, per-configuration validation adequacy and a selection set requiring both lower bounds above zero, each the lower of the two bootstrap schemes. `NOT_SUPPORTED_IN_VALIDATION` needs a non-empty adequate set wholly below 4.5 percent; an empty adequate set is `INCONCLUSIVE` (`DATA_INSUFFICIENT`).
- Holm family size is fixed at `m = 2` so the test level does not depend on which families reach the holdout (owner-approved 2026-10-01). Alternative: `m` equal to the number of families opening the holdout; rejected because validation results would then set the test level.
- Counting units: a token cluster is one token. Signal deduplication is a family-level rule keyed by family, configuration and token, anchored on retained triggers and applied before every count (scope owner-approved 2026-10-01, with separate `DEDUPED` reporting). Triggers are attributed by `t_entry`, and triggers crossing intervals or the embargo are counted as `BOUNDARY_CROSSING` and not evaluated. Both portfolio variants gate support; the known-outcome portfolio releases unknown positions at 0 percent after their valuation window.
- `C-3` gets a 60 s default and a one-sided refinement bound, so `t_avail` is defined without measurement (owner-approved 2026-10-01 as an explicitly labelled `MODELED` offset). Identity criteria are frozen at `1.0.0`; the round 1 proposal to exclude all third-party labels was replaced by the owner's conditional rule below. The first SOL funder is defined inside the permitted history window, with `FIRST_FUNDER_UNKNOWN` when funding predates it.
- Owner decisions on these items (2026-10-01): Holm `m = 2` approved; `C-3` approved as a modeled offset always labelled `MODELED` with model version; deduplication approved with `DEDUPED` counts reported separately per family, configuration, interval and scenario for strategy and baselines; procedure `0.3.0-draft` content approved; public address labels allowed only with saved source and exact version, manifest content hash and confirmed availability before the selection cutoff or `knownAt`.
- Consistency: at most one holdout technical rerun everywhere; a defect correction before holdout viewing needs a new holdout-freeze entry; one canonical event order based on the accepted event-locator grammar; loss-of-exit evidence must apply to any holder.

### Owner approval (0.5.0-draft)

On 2026-10-01 the owner approved every `OD-1`..`OD-8` value exactly as written in `0.4.0-draft` section 14 and the offline procedure `0.2.0-draft`, without adjustment. The labels change from `PROPOSED` to `OWNER-APPROVED 2026-10-01` with no semantic or numeric change. The protocol remains `DRAFT` until the final fresh review, Main's complete gate and freeze; the procedure stays blocking, and data work remains unauthorized until freeze and its own stage change.

### Behavioral evidence: one-time documentation exception

CORE_RISK requires `RED_REQUIRED` and documentation-only CORE_RISK has no general waiver; a methodology document has no executable behavior. On 2026-10-01 the owner granted a one-time documentation exception from behavioral RED only for this protocol document and this change. Risk stays CORE_RISK; a fresh independent Reviewer and Main's complete gate remain mandatory. The exception is not a precedent: all later calculations and implementation (D1/P1 scripts, code, outcomes) require behavioral tests. Verification for this change: strict OpenSpec validation, repository conventions (links and markers), fresh full-CI review and Main's complete gate.

## Risks / Trade-offs

- [Draft is read as authorization] → Status was `DRAFT` until the `1.0.0` freeze on 2026-10-01; the frozen protocol still authorizes data work only through the approved offline procedure and its own stage change (section 2.2), with explicit non-authorization in section 1 and the spec's freeze requirement.
- [Public labels leak later knowledge] → Labels are used only with saved source and version, manifest content hash and confirmed availability before the decision; otherwise they are dropped.
- [Proposed values are mistaken for measured or fitted ones] → Each carries its source and approval status (`OWNER-APPROVED 2026-10-01`), and the protocol states none is measured or derived from returns.
- [Calendar period is known in general market terms] → Owner holdout attestation before freeze, moving or shrinking the holdout if it cannot be given; the residual limitation is stated in the protocol.
- [`U-CONS` and the floor sample make support rare] → Accepted and stated (section 10.6): the pilot can confirm only large effects; smaller effects end `NOT_SUPPORTED` or `INCONCLUSIVE` rather than being fitted.
- [Historical depth is unavailable at acceptable cost] → The inventory and depth rule surface this before spending and classify it as D1 failure or an `EXPLORATORY` limitation.
- [A1+A2 changes evidence identities used by later application runs] → R1 rules are stated at research-evidence level and require citing provenance, without depending on A1+A2 column or API names.
- [Parallel edit of the replan note by the A1+A2 worktree] → One inserted line near the top; merge conflict is trivial and visible.
- [Too strict a support rule yields only inconclusive results] → Accepted: an honest `INCONCLUSIVE` is preferred to fitted thresholds; a new version can narrow hypotheses before data viewing.

## Migration Plan

None. No schema, code or runtime change.

## Open Questions

None. `OD-1`..`OD-8`, the round 1 clarifications, the procedure content and the holdout attestation were owner-approved or attested on 2026-10-01.
