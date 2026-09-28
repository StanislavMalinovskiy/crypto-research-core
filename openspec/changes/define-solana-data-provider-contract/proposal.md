# Proposal

## Why

The recorded-fixture slice proved the architecture and reproducibility contract, but the repository defines no contract for real Solana data. Two external audits (2026-09-20, see `docs/notes/EXTERNAL_AUDIT_REVIEW_2026-09-20.md` and `docs/notes/GLM_5_3_MAX_EXTERNAL_AUDIT_REVIEW_2026-09-20.md`) confirmed that connecting a live provider to the existing recorded payload contract without a data design would compromise identity, point-in-time integrity and research validity. Provider selection (Delivery Plan 3.1, remediation package F1) must start from downstream consumer requirements, not from general provider coverage claims.

## What Changes

- Introduces the normative Solana data contract as a new capability: finalized-only stable admission, a parser-independent Solana event-locator grammar, a five-facet time model, point-in-time token universe rules, separation of raw swap evidence from price, liquidity and USD derivation, a price/liquidity quality policy, historical research ranges with warm-up separation, and a capacity/backup recoverability model.
- Records a documentation-verified provider-consumer capability matrix covering transport classes (Helius JSON-RPC/WebSocket with paid LaserStream, Alchemy Yellowstone gRPC, Triton Dragon's Mouth, Chainstack Yellowstone, SQD Portal) and specialized sources (Bitquery, DexScreener, GoPlus), each with a verification date and a spike-required flag. The owner has selected Alchemy PAYG for the bounded S1 experiment and reports locally configured credentials; this is not the final provider-selection decision.
- Defines the capability-specific spike protocol required before provider selection. On 2026-09-27 the owner authorized preparation and execution of an isolated Alchemy S1 transport spike, subject to confirmed resource budgets: at least six hours of actual live finalized observation, forced five-minute and one-hour disconnects, and a ten-hour wall-clock limit (36,000 seconds). The owner explicitly increased only the former eight-hour wall-clock ceiling; all other budgets remain unchanged.
- Records the delegated default decisions (chosen by Control per owner instruction): finalized-only stable admission; a 180-day historical envelope (90-day warm-up + 90-day evaluation) with a documented fallback of shortened wallet lookback.
- Produces no production code, no schema migration, no production dependency and no provider selection.

## Capabilities

### New Capabilities

- `solana-data-contract`: normative requirements that any real Solana data ingestion, normalization and storage implementation in this repository must satisfy before mass real-data recording begins. Implemented by the future storage-readiness and ingestion changes (remediation F2/F3).

### Modified Capabilities

(none — existing specs are unchanged by this proposal; the future F2/F3 changes will modify `marketdata-storage` and add ingestion capabilities when implementing this contract)

## Non-Goals

- No production adapter, transport client or provider integration is implemented or selected.
- No database schema is created or migrated (forward migrations belong to F2).
- No ingestion worker, scheduler or monitoring is implemented (F3).
- No changes to module boundaries or the dependency DAG of `docs/ARCHITECTURE.md`.
- No new production dependencies; provider choice is deferred until spike evidence exists.
- S1 preparation and execution use the confirmed D10 budgets. The owner's subsequent autonomous-work authorization also permits the bounded, zero-paid S2-S5 investigation in D11 and preparation of the evidence-dependent decision in task 3.6. It does not increase Alchemy budgets, authorize purchases or admit live evidence into production. D10's later live-evidence replan adds current V1 reader compatibility and one explicit smoke retry using only the original allowance's remainder, with original evidence retained and renewed testing/review gates. D11 does not otherwise change S1; incomplete spikes do not complete F1 or authorize archive.

## Impact

- Affected modules (future implementers, no code change now): `marketdata` owns transport adapters, raw/normalized/price/liquidity storage and universe recording under this contract; `risk` consumes the derivation and quality rules for point-in-time fact production.
- Documents: this change is remediation package F1 of `docs/DELIVERY_PLAN_FIXES.md`; Delivery Plan 3.1 exit criteria reference its artifacts.
- The owner approved local evidence storage capped at 10 GB with at least 30 GB free space and continuation under the bounded smoke/full ceilings recorded in D10. Dashboard evidence may be supplied later; its absence does not prevent offline preparation but prevents a complete S1 verdict. Credentials stay local and must never appear in captured evidence. D11 bounds additional public probes within that existing storage allowance; missing access or capability evidence remains explicit in S2-S5 and the final provider decision.
- D11's manual-continuation addendum permits one reviewed, finite selection of previously unexecuted public requests after the first stopped batch, preserving its evidence and original aggregate ceilings/deadline. It changes no helper behavior or capability acceptance and does not restart S1.
