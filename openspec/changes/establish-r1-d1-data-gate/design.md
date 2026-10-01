## Context

See [proposal.md](proposal.md). [Frozen R1](../../../docs/research/R1_RESEARCH_PROTOCOL.md) `1.0.0`, freeze record entry 1 and the owner-approved [offline procedure](../../../docs/research/R1_OFFLINE_RESEARCH_PROCEDURE.md) govern one D1 change. Accepted `research-protocol` already requires a reviewed field inventory before bulk extraction. Section 1 inventory tools are implemented and independently approved; section 2 remains blocked. Existing `tools/spikes/provider-feasibility/` demonstrates dependency-free CommonJS tools with `node:test`; installed Node is `v20.18.0`. Application code and tests remain outside this slice.

### Fixed classification and handoff

`tier = CORE_RISK`, `risk = CORE_RISK`, `test_mode = RED_REQUIRED`. Meaningful RED targets false readiness, exact cost comparison, lost blockers and fingerprint instability. A fresh Reviewer receives the full CI-01..CI-15 matrix. `tests_changed_after_red` is reported by Builder; no earlier RED/golden test is in scope.

| Trigger | Assessment for the current slice |
|---|---|
| TR-01 | No application persistence semantics; report is stdout only. |
| TR-02 | No database transaction. |
| TR-03 | No concurrent work or locking. |
| TR-04 | No provider retry or durable replay effect. |
| TR-05 | No migration, delete or historical rewrite. |
| TR-06 | Matched: documentary vs measured availability and frozen historical envelope. |
| TR-07 | Matched: authoritative source-cost bounds and USD 100 comparison. |
| TR-08 | Matched: deterministic inventory identity and collection ordering. |
| TR-09 | Matched: explicit source gaps and incomplete coverage affect readiness. |
| TR-10 | No module or API boundary change. |
| TR-11 | Matched: integrity of required-field accounting and reproducible fingerprint. |
| TR-12 | Matched: reject credential-bearing inventory URLs; sanitized diagnostics. |

Applicable invariants: CI-01 (retain all missing facts), CI-02 (list gaps), CI-07 (versioned content identity), CI-09 (historical coverage is not inferred from current state), CI-10 (canonical ordering), CI-12 (provider claims remain in research inventory), CI-13 (no guessed fallback), CI-15 (bounded reads/reports). CI-03/04/05/06 have no durable transition or retry in this slice; CI-08 has no live/replay computation; CI-11 uses one synchronous local validation; CI-14 has no database/module calls. Controlling sources are accepted research specs, ADR 0009, Reproducibility, Operations and frozen R1. Reviewer verifies each applicability claim, not merely this summary.

Overlap check: initial PLAN found no other active changes; correction on 2026-10-01 with `openspec list --json` found only this change, now 6/16 tasks complete after adding section 1a. No overlapping change requires resolution. This change adds only `r1-data-inventory`; it does not modify accepted `research-protocol`. Classification remains fixed; section 4d allocates only the offline candidate correction with new meaningful RED, never source execution or a RED waiver.

## Goals / Non-Goals

The initial PLAN_READY covered section 1, now verified. The correction PLAN_READY covers only tasks 1.7–1.10 in section 1a: offline candidate-input correction and separate frozen tests. It is not PLAN_READY for any source data run. Keep the stage active after that slice. Section 2 requires `reason = CONTRACT_CHANGED` PLAN with named sources, versions and ceilings before any dependent tooling or run. This implements the frozen procedure's stage sequence without inventing source certainty.

No provider requests, schema probes, new data extraction, paid access, reconstruction, economic-trigger counts, gate computation, calibration or outcomes belong to the current Builder scope. Read-only inspection of retained evidence informed this PLAN correction; the owner has selected the external output location below. No change to frozen protocol or freeze entries is allocated.

## Decisions

### 1. Runtime, files and finite change budget

Reuse CommonJS on the existing Node runtime (`v20.18.0` observed 2026-10-01), using built-in `fs`, `crypto`, `node:test` and `node:assert/strict` only. No npm installation, SDK, package manifest, dependency upgrade, Parquet or DuckDB. This uses the repository's existing research-tool pattern without a production dependency. Runtime version is recorded by later run receipts; a different runtime is a planning decision before its first data run.

Builder owns at most six new files and 800 handwritten nonblank lines including tests under `tools/research/r1/`: `inventory.cjs`, `inventory-cli.cjs`, `inventory-candidates.json`, `test/inventory.test.cjs` and at most two synthetic JSON fixtures under `test/fixtures/`. No edits to existing tools or `src/**`. Architect later owns at most `docs/research/R1_D1_FIELD_INVENTORY.md` and `docs/research/R1_D1_RUNBOOK.md`, plus this active change. The candidate JSON is an implementation input with documentary references, not measured R1 evidence.

Alternative: introduce Python/SQL/Parquet tooling now. Rejected for the current slice because local JSON validation needs no new runtime or package and extraction format is unresolved.

### 2. Closed inventory schema `r1-d1-inventory-v1`

Top-level keys only: `schemaVersion`, `canonicalizationVersion`, `protocolVersion`, `freezeEntry`, `envelope`, `sources`, `fields`. Fixed versions: `r1-d1-inventory-v1`, `r1-d1-inventory-c14n-v1`, `1.0.0`; freeze entry is the JSON numeric integer `1`, not the string `"1"`. A supplied nonempty string canonicalization version other than `r1-d1-inventory-c14n-v1` returns `INVENTORY_INVALID` with fixed code `UNSUPPORTED_CANONICALIZATION_VERSION` and no fingerprint; a missing or malformed version remains a schema error. Supporting a new canonicalization version requires a future contract update before implementation. Envelope is the exact UTC half-open `[2026-04-01T00:00:00Z, 2026-09-29T04:02:00Z)` from R1 4.1, including the maximum extraction tail. No decimal numeric JSON tokens represent cost.

Source records have only `id`, `selected`, `sourceVersion`, `queryVersion`, `costUpperMicrousd`, `retention`, `evidence`. ID is ASCII kebab-case of at most 64 characters; `selected` Boolean. Versions are bounded nonempty text or `null` for unknown. Cost is a canonical unsigned integer string (at most 18 digits) or `null`; calculate with `BigInt` only. `retention` has only `status` (`CONFIRMED`, `UNVERIFIED`, `FORBIDDEN`) and `evidence` (same reference list as below). Unknown source versions, query versions, cost or retention produce blockers for selected sources; unselected candidates remain explicitly present without inflating selected cost.

Field records have only `id`, `status`, `sourceIds`, `coveredFrom`, `coveredTo`, `granularity`, `gaps`, `evidence`. Exactly these 12 IDs: `trade-legs`, `spl-transfers`, `sol-transfers`, `base-priority-fees`, `tips`, `reserves-depth`, `liquidity-events`, `mint-decimals-freeze`, `executable-entry-exit`, `sol-usd`, `block-time`, `visibility-latency`. All records exist even when unavailable. `sourceIds` is a unique list of existing source IDs (empty permitted only for `UNVERIFIED`/`UNAVAILABLE`). A confirmed field requires at least one selected source. UTC coverage values are canonical instants or `null`; unknown/incomplete envelope coverage blocks completion. `granularity` is bounded nonempty text or `null`; `gaps` is a bounded text list (at most 32 entries), empty means no known gap, not proof of completeness. Every field requires at least one evidence reference unless `UNVERIFIED`/`UNAVAILABLE`; no confirmed field may have missing granularity or evidence. Source availability is a manually supplied claim subject to independent inventory review, not certified by the validator.

`CONFIRMED` means the inventory author has supplied evidence supporting the field/source/retention assertion for independent review. It is not a validator-certified fact, measured availability percentage or D1 gate result. The validator checks the declared claim's structure and prerequisites only; even twelve confirmed fields can yield only inventory completion, with `runAuthorized = false` and `d1Passed = false`.

Each evidence reference has only `url`, `retrievedAt`, `version`, `claim` (bounded nonempty text). HTTPS URL has no userinfo, query or fragment; UTC `retrievedAt` is explicit, not obtained from the machine clock. `version` is bounded text or `null`; an unknown documentary revision remains a blocker for confirmation. Claims have at most 1,000 characters, other text at most 256; no arbitrary properties or embedded credential fields. Arrays are bounded: at most 32 sources, exactly 12 fields, at most 16 references per evidence list, at most 32 source IDs per field. Duplicate semantic records/references reject. Unknown properties reject. Invalid JSON, invalid dates and noncanonical money strings reject with fixed codes.

### 3. Report and canonical encoding

Pure `validateInventory(input)` returns a fixed report or a sanitized invalid result; module loading succeeds before behavior exists so RED is an assertion failure, never a missing-module failure. Valid output has only `schemaVersion`, `canonicalizationVersion`, `classification`, `fingerprint`, `selectedCostUpperMicrousd` (string or `null`), `blockers`, `runAuthorized: false`, `d1Passed: false`. Invalid output uses fixed classification/code, no input values or stack. A blocker is `{ code, fieldId }` or `{ code, sourceId }` with validated IDs, or `{ code }`. Include every blocker and sort by ASCII code then source/field ID. Classification is `INVENTORY_BLOCKED` with blockers, otherwise `INVENTORY_COMPLETE`; malformed structure is `INVENTORY_INVALID`. Cost > `100000000` adds `COST_ABOVE_CEILING`; equality does not add that blocker.

Fingerprint the complete normalized inventory, including documentary claims and unselected candidates. Canonicalization v1 recursively encodes null, Boolean and string using type tags and UTF-8 byte-length-prefixed values; the only permitted numeric value, `freezeEntry`, has numeric tag `n`, canonical ASCII decimal payload `1`, and exact byte encoding `n1:1` (tag, payload byte length, colon, payload). A string `"1"` is neither accepted as `freezeEntry` nor interchangeable with that numeric encoding; arbitrary numbers are outside the schema. Objects sort keys bytewise and encode key/value pairs; all schema lists are semantically unordered sets and sort their element encodings bytewise. Duplicate collection elements reject. Prefix schema/canonicalization version in the encoding; SHA-256 is `sha256:<lowercase-hex>`. No `JSON.stringify` key-order dependency, locale sort or financial `Number` conversion. Equivalent input permutation is invariant; changing any valid source, supported version, date, status, cost, note, gap or evidence changes identity. Unsupported canonicalization versions are rejected without an identity. Arrays with semantically sequential data are outside this schema.

CLI accepts exactly one `--inventory <path>` argument pair, rejects unknown options and files larger than 1,000,000 bytes using a limited read (limit plus one overflow byte). It emits one JSON line to stdout: exit 0 `INVENTORY_COMPLETE`, exit 2 `INVENTORY_BLOCKED`, exit 1 `INVENTORY_INVALID`. Never print the path, original content or caught exception. Read only that file; do not load secrets, make network calls or write files. At most 2,048 blockers; cap violations reject, never truncate silently. Output at most 262,144 bytes, failing with fixed `REPORT_LIMIT` if violated. Filesystem metadata and read errors use fixed codes.

### 4. Initial source evidence, checked 2026-10-01

These are documentary candidates only; retrieval date is not a dataset/query version and is not measured coverage. Seed every unconfirmed field/source prerequisite honestly and expect `INVENTORY_BLOCKED`.

- [Dune Solana DEX schema](https://docs.dune.com/data-catalog/curated/dex-trades/solana/solana-dex-trades): per-route-leg records, vault addresses and transaction/instruction positions are documented. Raw quantities are `UINT256`; display amounts/USD and fee fields are `DOUBLE`, requiring independent exact inputs before authoritative financial use. The schema does not establish reserve history, freeze state, full instruction stack paths or visibility latency. Envelope coverage and local retention remain unverified.
- [SQD Solana mainnet](https://docs.sqd.dev/en/data/solana/solana-mainnet): Portal documents genesis history using slots; legacy archive documents only about 30 days. Transaction/instruction and pre/post balance fields are documented. [API reference](https://docs.sqd.dev/en/portal/solana/api) documents finalized-stream and metadata interfaces. These claims do not establish complete reserve/depth reconstruction, mint freeze history, source latency, or an independent raw-receipt cross-check.
- [SQD Portal pricing](https://docs.sqd.dev/en/portal/pricing): published plans are prelaunch; public/testing/Starter allowances are described, but billing is not yet live and paid plans use early access. The published USD 99 Starter amount is a candidate price, not verified available access or a complete-source estimate. Retention permission and applicability require confirmation.
- [Dune SQL API terms](https://dune.com/sql-api-terms) (page version 2026-05-13) meter exports by data points. [Dune pricing](https://dune.com/pricing) did not expose a readable current price table in the inspected response. Record price/access/retention as unresolved; do not infer permission from export availability or circumvent plan limits.

The initial two-source candidate JSON and recorded fingerprint are historical section 1 evidence, not an exhaustive statement of configured access. Section 1a will update that input and report its new fingerprint without rewriting the historical verification record or frozen section 1 tests. No source will be selected. Existing paid Alchemy spike authorization is exhausted and does not authorize a D1 probe. Retained evidence may inform source admission but is not complete D1 evidence. Exact SOL/USD, mint freeze transitions and complete venue pricing inputs remain unconfirmed.

### 4a. Retained evidence correction — inspected 2026-10-01

The non-normative [provider research note](../../../docs/notes/SOLANA_PROVIDER_SPIKE_RESEARCH_2026-09-27.md), especially S2, S3, Research disposition and later A/B results, supplies measured candidates that the initial documentary inventory omitted. Later B evidence supersedes the earlier statement that no historical-account receipt exists; it does not turn formal S3/F1 into a passed stage.

| Candidate | Retained measured scope | Admission limits |
|---|---|---|
| Alchemy PAYG Account Archive | B historical matrix: 12/12 reads collected for one PumpSwap pool and two vaults, two repeats at parent slot `429644638` and child `429644639`; six repeat comparisons match. Recorded context slots match. Vault amounts reconcile with the sampled child's independent receipts. | Formal S3 remains unfulfilled. Pool layout remains undecodable/incomplete; no general pool-state decoder, historical CLMM ticks, mint transition coverage or full-envelope depth result is established. Matrix outcome is `INCONCLUSIVE`, not D1 passage. |
| SQD public historical data | Retained old/midpoint/recent samples and sampled signature/metadata/instruction/balance reconciliation with independent public RPC. Old Raydium ten-slot tail has header/parent continuity checks. | Sampled agreement only. Old tail has no full transaction-payload cross-check; recent PumpSwap HTTP 529, continuations and full-envelope gaps remain unresolved. Filtered ordinals are not canonical identity; join by full signature. |
| Independent public Solana RPC | Retained complete-block and transaction receipts reconcile the measured SQD samples. | Candidate for independent raw receipt verification, not proof of the required 200 stratified trades or complete historical account state. Endpoint/access limits and reproducible adapter/query version still need freezing. |
| Dune / Helius | Dune is not connected. A current local `CRYPTO_HELIUS_API_KEY` setting name exists; only its name was inspected. | No Dune access or current Helius usability/entitlement was tested or inferred. They are alternatives, not selected sources. |

Local Alchemy setting names include `ALCHEMY_API_KEY` and `ALCHEMY_SOLANA_RPC_ENDPOINT`; the September paid B receipts establish working access for that matrix, not an unlimited or renewed spending permission. No secret values were printed. The prior dashboard PAYG baseline and local estimated debit are not a current invoice, price bound or D1 cost estimate. Prior A/B authorizations are exhausted; preserve their receipts and accounting separately from any prospective D1 ledger.

Read-only SHA-256 checks matched the research note:

| Existing external evidence | SHA-256 |
|---|---|
| `C:\crypto-research-evidence\provider-ab-1\b-history\raw.bin` | `4c22a24aab2178eff415710226197159160011fe88d0394ac735f38da5e37a87` |
| `C:\crypto-research-evidence\provider-ab-1\b-history\summary.json` | `49281f6865852feb7c33d75157c1c2db1a4e7caafdd9c7e1b42644ec8e35c4ef` |
| `C:\crypto-research-evidence\provider-s2-sep28b\provider-feasibility\006.raw` | `0cabe2acea6178358ac7c18033fc12c6a4a8eda452479ee984bcd9d4a7869f14` |
| `C:\crypto-research-evidence\provider-s2-rpc-cm-2\provider-feasibility\002.raw` | `7f028de71b26e8f8fa9ca05ee5cef8647863226bf4cef74301deb0a7ff4a6cae` |

Owner-selected future output: `C:\crypto-research-evidence\r1-d1`; its parent exists, this new directory was not created. C: free space was `124262658048` bytes during inspection, exceeding the frozen 30 GB minimum; recheck before any future run. No output-location decision remains outstanding.

### 4b. Primary documentation and retention admission — retrieved 2026-10-01

| Source | Documented capability / terms | Remaining fact |
|---|---|---|
| Alchemy | [Account Archive](https://www.alchemy.com/docs/solana/account-archive) documents account history since July 2025, no pruning and historical `getAccountInfo` at or before a requested slot. [Terms](https://legal.alchemy.com/) dated 2025-06-27 permit internal use within licensed volume and downloads through authorized service features; they prohibit scraping and limit circumvention. | The inspected terms do not explicitly establish retention of RPC exports for this reproducibility use. Applicable account agreement/order terms, current method costs and budget upper bound remain unverified. Generic account bytes are not a validated pool/tick/mint decoder. |
| SQD | [Solana mainnet](https://docs.sqd.dev/en/data/solana/solana-mainnet) documents Portal genesis history. [Pricing](https://docs.sqd.dev/en/portal/pricing) is prelaunch/early access, with allowances and throttling. [Cloud terms](https://cloud.sqd.dev/terms.pdf) describe a software-use license; the inspected publication is undated. | Applicability of Cloud terms to public Portal and permission to retain exported chain data remain unverified. Published plan amounts do not prove available full-envelope capacity or cost. |
| Helius | [Terms](https://www.helius.dev/terms), dated 2026-09-28, describe subscription feature access and documentation copies. | The inspected terms do not explicitly establish persisted RPC-export permission; current key usability, plan entitlement and applicable agreement remain unverified. No Helius call was made. |

These are bounded documentary observations, not a conclusion that retention is forbidden. Do not use customer-submitted-data ownership clauses or provider privacy retention policies as permission to keep chain-data exports. Until applicable terms explicitly support local retention, source retention remains `UNVERIFIED`. Retrieval dates are not source/query versions. Mutable and prelaunch pricing must be reconfirmed before a costed PLAN_READY.

### 4c. Proposed admission sample — pending, not executable

R1 8.1 permits small schema probes before bulk extraction; the offline procedure still requires reviewed scripts and a source/version/ceiling-complete PLAN_READY before the first stage run. On 2026-10-01 the owner explicitly answered Alchemy PAYG D1 spending: "Не разрешаю пока". No new Alchemy PAYG call or paid run PLAN_READY is permitted until a future explicit owner decision. Any prospective sample must use verified zero-paid options; applicable retention, access limits and exact sample/adapter definitions still need resolution before Builder allocation or a run.

Proposed scope: at most nine outcome-blind cases, covering three declared pricing forms (constant-product, concentrated-liquidity, bonding curve) at three fixed early/middle/late cutoffs within the frozen envelope; at most two raw transactions per case (18 total). Freeze concrete programs, pools, ticks, mint accounts, slots, signatures and official layout versions before PLAN_READY. No wallet profitability, future survival or return may select them. The retained PumpSwap case is a prior control, not proof for other forms or replacement for in-envelope cases.

Revised proposed ceilings, including retries: zero new Alchemy account calls, at most 18 public RPC transaction attempts and 18 SQD selector/metadata attempts, 36 provider attempts total, only after zero-paid access is verified; at most one retry per logical request without exceeding those totals; 30-second request timeout; 64 MiB received bytes; 100 MiB retained evidence; two hours wall-clock; USD 0 cash. The earlier USD 3/96-account paid draft is withdrawn. Read-only reuse of existing historical account receipts remains allowed, without enlarging their sampled scope. Validate per-response allocation and zero billable upper bounds before admission; no chargeable fallback, subscription or request may be issued. Checkpoint at 80 percent of nonzero resource ceilings and stop at 100 percent. Missing pool/tick/mint state or transitions remain explicit rather than triggering paid reads or unbounded expansion. No bulk extraction, automatic continuation outside the fixed list or concurrent provider work is proposed.

The sample would inspect historical bytes and layout decodeability, reserve/depth inputs, mint freeze observations, sampled transfer/fee/tip reconstruction and independent raw agreement. It cannot demonstrate mint transitions between unsampled observations, continuous SOL/USD or source visibility latency. Unresolved fields stay visible. Exact SOL/USD and visibility sources remain separate admission questions. If only this sample is implemented, D1 remains `INCONCLUSIVE`; full extraction and frozen gates require another PLAN with measured field inventory, realistic whole-envelope cost/volume bounds and explicit review. Existing sample bytes-per-slot extrapolations are sensitivity calculations, not representative D1 volume forecasts.

### 4d. Implementation-ready offline candidate correction

`reason = CONTRACT_CHANGED`; fixed `tier/risk = CORE_RISK`, `test_mode = RED_REQUIRED`, matched TR-06/07/08/09/11/12 and CI applicability remain as assessed above. No risk is lowered. This is input truth/integrity work with a fresh Reviewer, not source execution. The bounded change budget is two Builder files: edit `tools/research/r1/inventory-candidates.json` and add `tools/research/r1/test/inventory-candidates.test.cjs`, at most 250 additional handwritten nonblank lines in total and at most 12 new behavioral tests. No validator/CLI implementation changes, dependencies, fixture additions, edits to the original frozen test, spike scripts or R1/golden files. Architect owns the four existing active artifacts and, only in assigned DOCS, the two existing research docs.

The corrected input has exactly four source IDs: existing `dune-solana-dex`, `sqd-solana-portal`, plus `alchemy-solana-account-archive` and `solana-public-rpc`. Every source stays `selected: false`, `sourceVersion: null`, `queryVersion: null`, `costUpperMicrousd: null`, `retention.status: UNVERIFIED`. Add dated terms evidence for Alchemy and SQD; use the official URLs in section 4b and the actual observed publication version or `null`. Public RPC documentation is [getTransaction](https://solana.com/docs/rpc/http/gettransaction) and [getBlock](https://solana.com/docs/rpc/http/getblock); neither proves endpoint-wide retention rights or version/cost completeness. Preserve closed schema and credential-free HTTPS URLs. Retained local paths/hashes and measured-scope facts may appear in bounded `claim` text alongside primary documentary references; they are separately attributable local observations, not claims made by the official page. Do not add unsupported schema properties or fictitious public receipt URLs.

Alchemy references document history and honestly state the B matrix's 12/12 reads, six repeat matches, one pool/two vaults/two slots, reconciled sampled vault balances, undecodable pool layout and unfulfilled formal S3. SQD/public RPC references record sampled reconciliation and limitations from section 4a, without claiming full-envelope agreement. Dune claims explicitly say unconnected; replace any blanket assertion that no cross-check has ever occurred with the specific unresolved full-envelope or field scope. No field becomes `CONFIRMED`; all coverage dates remain `null`. Keep trade-legs/block-time `DOCUMENTED`; link public RPC as a candidate to trade-legs, SPL/SOL transfers, fees and block-time while retaining their unresolved semantics. Link Alchemy and SQD to `reserves-depth` and `executable-entry-exit`, and Alchemy to `mint-decimals-freeze`; those fields remain `UNVERIFIED`, with gaps explicitly naming missing CLMM/tick/curve/layout or mint-transition coverage. `sol-usd`, tips and visibility remain unresolved. A successful vault sample is not complete executable pricing.

New tests read the actual candidate file and exercise the existing validator/CLI. Meaningful RED must fail assertions on absent Alchemy/public RPC records, missing reserve/mint candidate linkage or absent bounded-receipt limitations before editing JSON; never fail from missing imports. GREEN checks those facts and preservation of unknown cost/version/retention/full-envelope scope, schema acceptance with `INVENTORY_BLOCKED`, all twelve fields, required-field blockers and false authorization/pass flags. Assert the updated CLI exits 2; zero selected-source cost remains `"0"`, not a D1 estimate. Do not require an invented new exact blocker count or hardcode the old fingerprint. Record the new fingerprint and actual blocker count in handoff; assert valid identity and deterministic rerun if useful. Freeze these new tests after meaningful RED; original tests remain unchanged throughout.

Targeted commands are `node --test tools/research/r1/test/inventory-candidates.test.cjs` and `node --test tools/research/r1/test/inventory.test.cjs`; integration check is `node tools/research/r1/inventory-cli.cjs --inventory tools/research/r1/inventory-candidates.json`. All execute offline and write no provider evidence. Fresh Reviewer reviews full CI and Main retains the complete gate. This allocation remains permitted under the owner's PAYG denial because it makes no provider requests. No section 2 task is completed by it.

### 5. Two review barriers within one stage

First barrier: Builder verifies behavioral RED, implements only the offline slice, reports targeted GREEN, then fresh independent review; Architect documents the inventory without claiming gate passage and Main runs the complete repository gate. This makes the first deliverable reviewable.

Second barrier: return to Architect PLAN before any extraction implementation or data run. Updated artifacts must resolve selected sources/versions/retention/cost, concrete schema probes (if needed), owner-selected external evidence directory, supported venue decoders, exact lossless formats, independently verified raw-receipt source, per-run request/byte/timeout/retry ceilings and cumulative accounting. No contingent extractor or run command is allocated now. A complete inventory validator cannot establish those facts. All sources must fit `OD-2`: USD 100, 14 days, 10 GB evidence, 30 GB free, 80 percent checkpoint and 100 percent stop. Requests/received bytes have additional finite ceilings declared in that later PLAN. Outcomes stay blocked until measured D1 pass and calibration `1.1.0`.

Full D1 criteria remain exactly R1 8.2/8.3: depth inputs for >=90 percent of candidate-pool trigger events over the whole envelope; 200 venue/month-stratified raw receipt checks with >=95 percent agreement and zero doubled volume; >=90 percent reconstructed swaps, entry/primary-horizon input and loss-of-exit lookup availability; <=2 percent unresolved time gaps per supported venue; manifest lineage and identical rerun hashes for universe/identities/labels. No threshold implementation is allocated in the current slice. Failure or exhausted budget gives `INCONCLUSIVE`, never evidence of no edge.

## Risks / Trade-offs

- Unknown historical state/access/retention → blocked inventory; require evidence and replan before a run.
- Published pricing changes → dated references, unknown costs unless currently confirmed, exact upper-bound accounting in later PLAN.
- Documented field mistaken for measured completeness → distinct status, review of claims, `d1Passed = false` in every inventory report.
- Source annotations contain sensitive input → closed schema, credential URL rejection and output only fixed codes/validated IDs; no raw diagnostics.
- Large history needs a different runtime/format → explicit later PLAN rather than extending current file budget or adding packages silently.

## Migration Plan

No application deployment or migration. Add isolated offline tools and tests; no rollback touches existing evidence. Keep the active D1 change partial until section 2 is planned and its verified tasks actually finish. Archive is forbidden after section 1 alone.

## Verification Strategy

Builder: `node --test tools/research/r1/test/inventory.test.cjs`. Behavioral RED must execute assertions for retained blockers, exact ceiling/one-micro-USD overflow, unknown retention, permutation/version sensitivity, complete-inventory false authorization, size bounds and sanitized CLI errors. Use synthetic fixtures only; no remote calls or actual data runs. Preserve frozen tests after valid RED.

Main (after review, not Builder): `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `docker version`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, `git diff --check`, plus the Node targeted command. Required Maven does not discover external Node tests, so both are needed. No check is claimed passed by this plan.
