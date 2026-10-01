## Why

The replan note's R1 package and remediation F5 require the wallet copier and early multi-wallet research to be preregistered before any parameter fitting or return viewing; otherwise D1/P1 results can become post-hoc rationalization, leak future knowledge into wallet selection or hide missing outcomes. On 2026-10-01 the owner decided to start drafting R1 now, in parallel with the active A1+A2 change `version-market-facts-and-split-evidence`, overriding the note's "after A1/A2" order for drafting only.

## What Changes

- Adds the versioned R1 research protocol document `docs/research/R1_RESEARCH_PROTOCOL.md` (`1.0.0`, `FROZEN` 2026-10-01; content-identical to the reviewed `0.7.0-draft` apart from version and status lines): hypotheses H1 (copier) and H2 (early independent-buyer group), operating envelope B as a proposed envelope, availability and `knownAt` rules, walk-forward splits with embargo and an owner-attested untouched holdout, copier-replicable wallet selection on preceding history only with own-wallet metrics as a comparison only, linkage-based coordination clustering that never merges on mass-service funders, route reconstruction and FIFO statuses, point-in-time universe, outcome statuses with a fixed precedence, a maximum entry wait from the computed entry instant distinguishing `ENTRY_UNKNOWN` from proven `NOT_ENTERED`, a valuation-window `TERMINAL_NO_EXIT` rule, the `U-CONS` stress scenario and an unknown-share bias flag, a field-availability inventory and preregistered depth rule for D1, venue-specific cost model, finite-capital portfolio simulation, an exact statistical procedure with a single-test power statement, preregistered baselines, result classes that separate evidence against the effect (`NOT_SUPPORTED`) from underpowered `INCONCLUSIVE` results with multi-valued failure reasons, S1 transition rules, stop rules, a single-shot holdout with a bounded technical rerun, and report lineage.
- Defines the mandatory order (protocol update, owner approval of proposed values and holdout attestation, final fresh review, complete gate, freeze, then authorized data work) and the freeze lifecycle: protocol freeze before D1 extraction with primary metric, success thresholds and wallet-selection rules fixed, a D1 calibration amendment restricted to an enumerated data-quality/availability whitelist, a code/configuration/dataset holdout freeze, and a separate freeze record.
- Records Architect-proposed minimal values for owner decisions `OD-1`..`OD-8`, each with its source; on 2026-10-01 the owner approved every value without adjustment (`OWNER-APPROVED 2026-10-01`).
- Adds the blocking offline research procedure (`OWNER-APPROVED 2026-10-01`, content `0.3.0-draft`, recorded in `0.3.1-draft`) `docs/research/R1_OFFLINE_RESEARCH_PROCEDURE.md` (`OD-4`): one OpenSpec change per stage (D1, P1) with bounded runs, preconditions, OpenSpec applicability boundary, script/receipt/export locations, checks, budget watchdog, receipts, blinding, secrets and review.
- Owner decisions of 2026-10-01 are recorded: the parallel start; a one-time behavioral-RED documentation exception for this protocol change only (risk stays CORE_RISK and every later calculation or implementation requires behavioral tests); adoption of external methodology feedback incorporated in `0.3.0-draft` and `0.4.0-draft`; scope option (a), a bounded pilot with unchanged budgets, sample floor and 4.5 percent effect bar, which will often leave small effects `INCONCLUSIVE`; the owner's personal holdout attestation for 2026-08-31 to 2026-09-28; and owner approval of all `OD-1`..`OD-8` values and the offline procedure (2026-10-01). The protocol stays `DRAFT` until freeze, and data work stays unauthorized until freeze and its own stage change.
- Introduces the `research-protocol` capability so that the preregistration obligations on any evidence claiming to be R1 evidence become accepted, reviewable requirements after archive.
- Adds a one-line pointer from the replan note to the draft protocol.

## Capabilities

### New Capabilities

- `research-protocol`: preregistration, freeze, point-in-time copier-replicable selection, clustering, outcome-status, unknown-outcome, source-adequacy, cost, portfolio, statistical, baseline, stop-rule and lineage obligations for R1 wallet copier and multi-wallet research evidence.

### Modified Capabilities

(none; `research-reproducibility` and `signal-evaluation` are not modified, avoiding overlap with the active A1+A2 delta specs)

## Non-Goals

- No application code, tests, Flyway migration, schema, public API, module-boundary or dependency change; affected modules: none in this change. Future application work for wallet analytics (`wallet`), additional horizons (`evaluation`) or signal families (`signal`) needs its own change.
- No data purchase, provider run, offline script, export or return computation; those require protocol freeze and the owner's budget and offline-procedure decisions.
- No measured latency and no value fitted or derived from returns: owner-approved values and repair-round clarifications (2026-10-01) are sourced choices.
- No change to accepted specs, ADRs, Architecture, Reproducibility, Testing, module documents, Roadmap or Delivery Plan.
- No signing, order submission or PAPER/LIVE execution.

## Impact

- New documentation: `docs/research/R1_RESEARCH_PROTOCOL.md` and `docs/research/R1_OFFLINE_RESEARCH_PROCEDURE.md`; at freeze, `docs/research/R1_PROTOCOL_FREEZE.md` (created 2026-10-01, pinning freeze commit and SHA-256).
- Pointer line in `docs/notes/ARCHITECTURE_AND_RESEARCH_REPLAN_2026-09-30.md`.
- Future D1/P1 work and any F5-related change must cite a frozen protocol version. The A1+A2 change remains independent; R1 references its dual decision/evaluation evidence only as a future precondition for application-computed outcomes.
