## Context

See [proposal.md](proposal.md). [Frozen R1](../../../docs/research/R1_RESEARCH_PROTOCOL.md) `1.0.0`, freeze record entry 1 and the owner-approved [offline procedure](../../../docs/research/R1_OFFLINE_RESEARCH_PROCEDURE.md) govern one D1 change. Accepted `research-protocol` already requires a reviewed field inventory before bulk extraction. There are no R1 tools yet. Existing `tools/spikes/provider-feasibility/` demonstrates dependency-free CommonJS tools with `node:test`; installed Node is `v20.18.0`. Application code and tests remain outside this slice.

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

Overlap check: `openspec list --json` at PLAN found no active changes. This change adds only `r1-data-inventory`; it does not modify accepted `research-protocol`.

## Goals / Non-Goals

The current PLAN_READY covers only tasks in section 1 and their review/documentation/gate evidence. It is implementation-ready for the offline inventory slice; it is not PLAN_READY for any source data run. Keep the stage active after that slice. Section 2 requires `reason = CONTRACT_CHANGED` PLAN with named sources, versions and ceilings before any dependent tooling or run. This implements the frozen procedure's stage sequence without inventing source certainty.

No provider requests, schema probes, raw-data reads, paid access, output-directory selection, reconstruction, economic-trigger counts, gate computation, calibration or outcomes belong to the current Builder scope. No change to frozen protocol or freeze entries is allocated.

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

RPC/archive for the independent 200-trade stratified check, exact SOL/USD, mint freeze and venue pricing inputs remains unidentified. Existing paid Alchemy spike authorization is exhausted and does not authorize a D1 probe or reuse as complete D1 evidence. No source is currently selected.

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
