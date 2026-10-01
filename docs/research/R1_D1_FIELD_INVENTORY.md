# R1 D1 field inventory

## Status and controlling sources

Document checked 2026-10-01. This is the initial documentary inventory for the active [D1 change](../../openspec/changes/establish-r1-d1-data-gate/proposal.md), not measured D1 evidence. The [frozen protocol](R1_RESEARCH_PROTOCOL.md) `1.0.0`, [freeze record](R1_PROTOCOL_FREEZE.md) entry 1 and [offline procedure](R1_OFFLINE_RESEARCH_PROCEDURE.md) remain controlling. No provider query, schema probe or extraction has run in this slice.

The machine-readable input is [inventory-candidates.json](../../tools/research/r1/inventory-candidates.json). Its closed schema and canonical encoding are defined by the active [design](../../openspec/changes/establish-r1-d1-data-gate/design.md) and [delta spec](../../openspec/changes/establish-r1-d1-data-gate/specs/r1-data-inventory/spec.md). Use the [runbook](R1_D1_RUNBOOK.md) to validate it locally.

| Item | Current value |
|---|---|
| Schema | `r1-d1-inventory-v1` |
| Canonicalization | `r1-d1-inventory-c14n-v1` |
| Protocol / freeze entry | `1.0.0` / numeric `1` |
| Required UTC envelope | `[2026-04-01T00:00:00Z, 2026-09-29T04:02:00Z)` |
| Candidate / selected sources | 2 / 0 |
| Classification / CLI exit | `INVENTORY_BLOCKED` / `2` |
| Blocking reasons | 67, retained and deterministically ordered |
| Selected-source upper bound | `"0"` micro-USD: the sum over zero selected sources; no D1 cost estimate exists |
| Authorization / measured D1 pass | `runAuthorized = false`, `d1Passed = false` |
| Inventory fingerprint | `sha256:dea0c1610d91856990ccd0c7d45eb1943efb275410c8f647ce0df5353f1851f1` |

The fingerprint identifies the complete inventory, including unselected candidates, annotations and dated references. It does not identify an extracted dataset or prove source truth. A later inventory edit changes the fingerprint; recompute the report rather than reusing this snapshot.

## Evidence and candidate sources

The input records retrieval at `2026-10-01T09:43:29Z`; the official pages below were checked again on 2026-10-01. Retrieval time is not a source/query/export version. Except for the dated Dune terms, documentary revision identifiers are unknown and remain `null`. Both candidate source and query versions, costs and retention permissions remain unresolved.

| Candidate | Official documentary evidence | What remains unresolved |
|---|---|---|
| `dune-solana-dex` | [Dune Solana DEX schema](https://docs.dune.com/data-catalog/curated/dex-trades/solana/solana-dex-trades) describes route segments, vault addresses, transaction/instruction positions and UTC block timestamps. Raw quantities use `UINT256`; display quantities and USD fields use `DOUBLE`. | Full-envelope coverage, economic-trade grouping, full instruction paths, reserve history, exact pricing and cross-checks. Display fields cannot supply authoritative exact financial inputs. |
| `sqd-solana-portal` | [SQD Solana schema](https://docs.sqd.dev/en/data/solana/solana-mainnet) documents slot-addressed Portal history from genesis, instruction paths, integer transaction fees, native/token pre/post balances and token decimals. Its legacy v2 archive retains about 30 days. | Measured completeness, venue reconstruction, historical state and latency. Balance changes do not alone establish transfers, tips or fee decomposition; decimals do not establish mint freeze history. |

[Dune SQL API terms](https://dune.com/sql-api-terms), dated 2026-05-13, describe export data-point accounting. They do not establish the intended complete-envelope cost or permission to retain this research export. Those prerequisites stay unverified.

[SQD pricing](https://docs.sqd.dev/en/portal/pricing), checked 2026-10-01, publishes a USD 99/month Starter plan but says billing is not live and paid plans use early access. That published price is neither confirmed available access nor a complete D1 upper bound. Applicable access and local-retention terms must be verified before source admission. Published plans and prices can change.

## Required fields

Every row's historical coverage is unknown (`coveredFrom = coveredTo = null`). All rows have known unresolved gaps. `DOCUMENTED` records a capability described by a reference. `UNVERIFIED` records an unresolved prerequisite. `CONFIRMED`, when later used, means an author-supplied, evidenced assertion for independent review; the validator checks its structure, not measured availability or source truth. No initial row is `CONFIRMED` or `UNAVAILABLE`.

| Field ID | Status | Candidate mapping and remaining evidence |
|---|---|---|
| `trade-legs` | `DOCUMENTED` | Dune/SQD candidates; route-leg grouping, supported-venue reconstruction and raw-receipt agreement unmeasured. |
| `spl-transfers` | `UNVERIFIED` | SQD candidate; explicit transfer decoding and envelope completeness unverified. |
| `sol-transfers` | `UNVERIFIED` | SQD candidate; individual transfer and fee/tip attribution unverified. |
| `base-priority-fees` | `UNVERIFIED` | SQD candidate; exact fee separation unverified. |
| `tips` | `UNVERIFIED` | Independent tip accounts/attribution source unidentified. |
| `reserves-depth` | `UNVERIFIED` | Historical reserves, ticks and bonding-curve inputs unidentified. |
| `liquidity-events` | `UNVERIFIED` | Venue-specific addition/removal decoding unresolved. |
| `mint-decimals-freeze` | `UNVERIFIED` | SQD candidate for decimals; authoritative mint metadata and freeze history unresolved. |
| `executable-entry-exit` | `UNVERIFIED` | Independent historical executable pricing and loss-of-exit inputs unidentified. |
| `sol-usd` | `UNVERIFIED` | Exact historical SOL/USD source unidentified. |
| `block-time` | `DOCUMENTED` | Dune/SQD candidates; historical timestamp completeness/agreement unmeasured. |
| `visibility-latency` | `UNVERIFIED` | Measured source visibility latency unavailable; no live receipt observations. |

The 67 blockers concern unconfirmed fields, absent selected suppliers, incomplete coverage, gaps, missing granularity/evidence and unknown documentary versions. Source-admission blockers apply when a source is selected; unselected-source costs and permissions remain visibly unknown in the input rather than becoming zero-cost claims. The independent source for R1's 200-trade, venue/month-stratified raw-receipt check is also unidentified.

## Next decision

The next source-admission PLAN must establish evidence for each field, selected source/query/export versions, retention permission and a complete-source cost upper bound; it must also identify the independent raw-receipt source, supported venue decoders, lossless formats and an owner-chosen evidence directory outside git. Declare finite per-run and cumulative ceilings within OD-2, including requests, bytes, timeouts and retries.

Source-specific tooling and runs require that updated `PLAN_READY`, behavioral RED, reviewed inventory/scripts and Main's mandatory gate. No source is selected by this document. The existing Alchemy spike permission does not authorize a D1 probe. Measured D1 thresholds remain in frozen R1 sections 8.2/8.3; this inventory does not compute them. Section 2 of the active change stays incomplete, and completion of the offline slice cannot close or archive D1.
