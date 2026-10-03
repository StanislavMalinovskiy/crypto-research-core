## Purpose

Defines outcome-blind, reproducible validation of the R1 D1 field inventory and the mandatory boundary between an inventory and authorization to extract historical evidence.

## ADDED Requirements

### Requirement: Separately authorized exploratory public sample
Under the owner's 2026-10-03 authorization and offline-procedure section 11, a fixed zero-paid public SQD probe SHALL produce only availability, field-presence, sample-coverage and volume/cost feasibility outputs labelled `EXPLORATORY`, `d1Evidence = false` and `d1Passed = false`. It SHALL NOT compute returns, PnL, profitability, rankings, selection, strategy outcomes or reconstruction; change frozen methodology/calibration/thresholds; promote inventory fields; or claim full-envelope completeness. Meaningful behavioral RED, targeted GREEN, the two inventory suites, integrity preflight, strict all-item validation, doctor, diff check and ONE fresh independent review SHALL precede source requests. The owner's localized exception SHALL omit the full Maven/Docker gate for this probe only and SHALL NOT relax authoritative D1/P1 closure or permit archive.

#### Scenario: Exploratory probe proceeds while D1 remains blocked
- **WHEN** design 4f's targeted checks and independent review pass but full-D1 population/retention/cost prerequisites remain unknown
- **THEN** only the fixed bounded exploratory public sample SHALL be executable
- **AND** its result SHALL retain false D1 flags, explicit unknowns and unchecked full-D1 tasks.

### Requirement: Exploratory holdout and payload admission guard
The probe SHALL exclude every raw fetch, count and inspection of `[2026-08-31T00:00:00Z, 2026-09-29T00:00:00Z)`. It SHALL use only design 4f's six fixed preholdout timestamp anchors, validated header-first single-slot requests and three fixed program filters; no previous raw sample SHALL be read. Every returned header SHALL match the admitted safe slot, timestamp and hash before any raw retention or row count. Missing/unsafe/forbidden time, extra blocks, malformed/partial/oversized responses SHALL produce an explicit incomplete sample with no retained raw body or published rows from that response. Logs/receipts SHALL contain only safe accounting/fixed error codes, never provider error bodies or secrets.

#### Scenario: Provider returns a holdout block
- **WHEN** a requested preholdout slot response includes a timestamp on September 28 or another forbidden/missing/mismatched header
- **THEN** the response SHALL be rejected before raw-file retention and row counting
- **AND** only its attempted-request/received-byte/error accounting SHALL remain, with no fallback or retry.

### Requirement: Exploratory finite budget and replay provenance
The fixed exploratory query/summary/configuration versions SHALL be `exploratory-sqd-v1`, `exploratory-counts-v1` and `exploratory-config-c14n-v1`. The Node-built-in-only probe SHALL bound aggregate execution to USD 0, 30 minutes, 80 attempts, 25,000,000 received bytes, 50,000,000 retained bytes, 2,000,000 bytes/response, 30 seconds/request, two seconds between starts, concurrency one and retries zero. It SHALL reserve response/disk capacity before requests, retain 30,000,000,000 bytes free, record 80-percent checkpoints and stop unfinished work at 100 percent. Files SHALL be exclusive outside git; prior output SHALL NOT be overwritten/resumed. The manifest SHALL identify exact selectors, exclusion, limits, code/runtime/source/query versions, raw SHA-256, safe gaps and unresolved strata. Offline replay SHALL verify raw hashes and reproduce the deterministic summary hash without network or writes. Full-D1 upper bounds SHALL remain unknown unless supported; sensitivities SHALL explicitly identify assumed populations/workload and omitted components.

#### Scenario: Finite sample reaches its byte ceiling
- **WHEN** remaining received/disk capacity cannot reserve the next response or a request times out
- **THEN** further requests SHALL stop with explicit incomplete accounting
- **AND** neither ceiling resets, automatic retry, paid fallback nor a D1 success claim SHALL occur.

#### Scenario: Replay detects changed raw evidence
- **WHEN** a manifest-listed raw file has changed or is missing
- **THEN** offline replay SHALL return a fixed integrity error without a successful summary
- **AND** a byte-identical admitted manifest SHALL reproduce the original deterministic sample-summary hash without re-fetching data.

### Requirement: Complete required-field accounting
The inventory SHALL account for every frozen R1 section 8.1 field with explicit source references, covered dates, granularity, gaps, cost and retention evidence. It SHALL distinguish `CONFIRMED`, `DOCUMENTED`, `UNVERIFIED` and `UNAVAILABLE` field status. `CONFIRMED` SHALL denote an author-supplied, evidenced assertion subject to independent review, not a measured D1 availability result or a fact certified by the validator. Documentary capability SHALL NOT be reported as measured coverage. Missing, duplicate, unknown or malformed required-field records SHALL produce `INVENTORY_INVALID`; valid but unresolved fields SHALL produce `INVENTORY_BLOCKED` with every blocking reason retained in deterministic order.

#### Scenario: Trade table without historical depth
- **WHEN** trade legs are documented but historical reserve/depth inputs remain unverified
- **THEN** the report SHALL include the reserve/depth blocker
- **AND** it SHALL NOT present the trade table as proof of depth coverage or a passed D1 gate.

#### Scenario: Missing and duplicate fields
- **WHEN** a required-field record is missing or appears twice
- **THEN** validation SHALL return `INVENTORY_INVALID`
- **AND** no extraction or successful inventory classification SHALL be emitted.

#### Scenario: Multiple unresolved prerequisites
- **WHEN** a valid inventory has unknown source cost, unconfirmed retention and an unavailable field
- **THEN** the report SHALL retain all three categories of blockers
- **AND** it SHALL NOT discard unavailable fields.

### Requirement: Exact bounded source cost and retention
Each selected source SHALL identify its source version, query/export version, dated evidence and explicit retention permission for reproducible local exports. Unknown cost or retention SHALL block inventory completion. Selected-source cost upper bounds SHALL be exact nonnegative integer micro-USD strings, summed without binary floating-point arithmetic and compared with the approved D1 USD 100 ceiling. An estimate over the ceiling SHALL return an explicit budget blocker before any spending; equality SHALL NOT authorize spending or bypass the remaining prerequisites.

#### Scenario: Exact spending ceiling
- **WHEN** selected-source upper bounds sum to `100000000` micro-USD
- **THEN** the cost comparison SHALL record that the declared bounds do not exceed the USD 100 ceiling
- **AND** a sum of `100000001` SHALL record `COST_ABOVE_CEILING`.

#### Scenario: Unknown retention
- **WHEN** a selected source documents available fields but its export-retention permission is unconfirmed
- **THEN** the report SHALL be `INVENTORY_BLOCKED`
- **AND** access to that source SHALL NOT be treated as retention permission.

### Requirement: Evidenced offline candidate correction
The candidate inventory SHALL include unselected `alchemy-solana-account-archive` and `solana-public-rpc` alongside the existing Dune and SQD candidates, linking historical account state to reserve/depth and mint-state fields and independent raw receipts to transaction fields. Its evidence SHALL distinguish the retained Alchemy B 12/12 bounded matrix and sampled SQD/public RPC agreement from unfulfilled formal S3 and full-envelope D1 requirements. Dune SHALL remain explicitly unconnected. Source costs and query/source versions SHALL remain unknown, retention unverified and full-envelope dates unset until supported by evidence; no required field SHALL become `CONFIRMED` from these samples. Updating candidate input SHALL NOT alter the offline validator schema or authorize provider calls.

#### Scenario: Historical state candidate is retained without false readiness
- **WHEN** the offline candidate input incorporates the retained Alchemy B matrix and sampled independent receipt agreement
- **THEN** Alchemy SHALL be present and linked to reserve/depth and mint-state fields with the measured sample scope and unresolved layout/transition limits retained
- **AND** public RPC SHALL be present as the independent transaction candidate, all sources SHALL remain unselected, and validation SHALL report `INVENTORY_BLOCKED` with `runAuthorized = false` and `d1Passed = false`.

### Requirement: Filtered-envelope feasibility before spending
An R1 8.1 feasibility sample SHALL produce only volume/cost feasibility evidence for the complete frozen six-month extraction envelope, including its tail, filtered to outcome-blind candidate wallets and relevant pools of their tokens under the frozen universe rules. Estimates SHALL account for cohort/pool discovery, all required related histories, independent checks, overlaps, requests, received/retained bytes, throughput, tariffs and finite retries. All-block rates or post-D1 ranked winners SHALL NOT substitute for that workload. Unknown population, source version, retention, tariff or upper-bound evidence SHALL remain blocking. A complete bound above any applicable OD-2 resource ceiling SHALL stop work before spending/full extraction and return the concrete scope, estimate, excess and budget/narrower-experiment decision to the owner. The sample SHALL NOT demonstrate D1 coverage or passage, change frozen thresholds or authorize a provider call before a new PLAN_READY and mandatory review/gate/run preconditions.

#### Scenario: Filtered workload differs from complete blocks
- **WHEN** only broad program/full-block samples exist and the complete candidate-wallet/token-pool population or related-state volume is unknown
- **THEN** the filtered whole-envelope estimate SHALL remain unresolved
- **AND** an all-block byte-rate extrapolation SHALL NOT be reported as its complete cost bound.

#### Scenario: Full workload exceeds the approved ceiling
- **WHEN** the full filtered-envelope estimate including discovery, tail and ancillary evidence exceeds an applicable OD-2 ceiling
- **THEN** work SHALL stop before spending or full extraction with the estimate, ceiling excess and precise owner decision recorded
- **AND** R1 dates, universe and thresholds SHALL NOT be silently narrowed to fit the sample.

### Requirement: Vault inputs preserve transaction state and missing-depth counts
Later reserve reconstruction SHALL use raw vault-account balances immediately before/after the transaction, with full transaction identity, account/mint/pool mapping and exact integer amounts. Trader balances, slot-wide archive states or transaction aggregate deltas SHALL NOT be silently treated as intermediate leg reserves. Post-state SHALL NOT become evidence for a pre-transaction decision. Vault totals without required historical ticks/bins SHALL NOT establish CLMM/DLMM executable depth. The later full-envelope gate SHALL retain all candidate-pool trigger events in the frozen R1 8.2 denominator and explicitly count unavailable-depth shares by venue, month and reason; availability SHALL meet the unchanged >=90 percent rule or cost-modelled D1 SHALL be `INCONCLUSIVE/data insufficient`.

#### Scenario: Vault sample lacks CLMM or DLMM state
- **WHEN** pre/post vault balances are available but historical ticks/bins needed for a venue's pricing form are missing
- **THEN** the affected triggers SHALL remain counted as missing depth in the complete denominator
- **AND** neither their removal nor a sample's apparent coverage SHALL establish the frozen depth gate.

### Requirement: Exact field source or explicit unavailability
Next-run admission SHALL state an exact supported source with version, cutoff/time semantics, covered dates, cost and retention evidence for each of tips, SOL/USD and measured visibility latency, or SHALL record `UNAVAILABLE`. Unobservable tips SHALL NOT be assumed zero; non-exact display or after-cutoff prices SHALL NOT supply authoritative SOL/USD; historical block/retrieval times SHALL NOT be fabricated into measured visibility. The frozen C-3 default SHALL remain labelled `MODELED` with its version and SHALL NOT be presented as an observed source or a relaxed D1 threshold.

#### Scenario: Three fields lack supported sources
- **WHEN** no admitted historical tip attribution, exact point-in-time SOL/USD feed or observed source-visibility evidence is available
- **THEN** all three next-run source dispositions SHALL be `UNAVAILABLE` with their missing evidence retained
- **AND** the existing modeled default or documentary candidates SHALL NOT turn the inventory into a complete measured result.

### Requirement: Deterministic inventory identity
Every structurally valid inventory report SHALL identify inventory schema and canonicalization versions and a SHA-256 content fingerprint. Equivalent field, source and evidence-list ordering SHALL produce identical ordered classifications, blockers and fingerprint. Changes to accepted inventory content SHALL produce distinguishable identity. The only supported canonicalization version SHALL be `r1-d1-inventory-c14n-v1`; another nonempty string version SHALL produce `INVENTORY_INVALID` with fixed code `UNSUPPORTED_CANONICALIZATION_VERSION` and no fingerprint. Supporting a different version SHALL require an approved contract update. The JSON numeric `freezeEntry` value `1` SHALL encode as the exact ASCII bytes `n1:1`; its string form SHALL be rejected. Documentary references SHALL record their URL, retrieval date and known content version or an explicit unknown version; missing version evidence SHALL remain visible.

#### Scenario: Input permutation
- **WHEN** the same valid records are supplied in a different collection or object-key order
- **THEN** their fingerprint and ordered report SHALL be identical
- **AND** changing a source query/export version SHALL change the fingerprint.

#### Scenario: Unsupported canonicalization version
- **WHEN** an otherwise valid inventory supplies a nonempty canonicalization version other than `r1-d1-inventory-c14n-v1`
- **THEN** validation SHALL return `INVENTORY_INVALID` with `UNSUPPORTED_CANONICALIZATION_VERSION`
- **AND** it SHALL NOT emit a fingerprint for that unsupported version.

#### Scenario: Numeric freeze reference
- **WHEN** the accepted inventory has the JSON numeric `freezeEntry` value `1`
- **THEN** canonicalization SHALL encode that value as the exact ASCII bytes `n1:1`
- **AND** replacing it with the string `"1"` SHALL produce `INVENTORY_INVALID`.

### Requirement: Offline validation boundary
The validator SHALL read only its explicitly supplied inventory, bound input to 1,000,000 bytes, 32 sources, 12 required fields and 16 evidence references per source or field, and emit a bounded report without filesystem writes, credential loading, provider calls or data extraction. It SHALL reject unknown schema properties, URLs containing user information or query strings, malformed input, and input limits before publishing a successful report. Diagnostics SHALL use fixed codes and SHALL NOT echo input values, file content, credentials, provider error bodies or stack traces.

#### Scenario: Oversized or unsafe input
- **WHEN** the inventory exceeds a declared bound or contains an authenticated URL
- **THEN** the CLI SHALL exit with an invalid-input result and a fixed diagnostic code
- **AND** it SHALL make zero provider calls and zero filesystem writes.

### Requirement: Inventory readiness is separate from extraction authorization
A syntactically valid inventory SHALL be `INVENTORY_COMPLETE` only when every required field is confirmed over the frozen extraction envelope and all selected-source version, retention and cost prerequisites are present. Every report SHALL retain `runAuthorized = false` and `d1Passed = false`, including complete inventories. Before any D1 data run, the inventory SHALL receive the change's mandatory review and an updated Architect PLAN_READY SHALL name confirmed sources, exact query/export versions, reviewed scripts, output location and finite run ceilings. Bulk extraction SHALL follow inventory review. Frozen D1 gate thresholds, calibration and outcome-bearing work SHALL remain separately guarded by the accepted R1 protocol.

#### Scenario: Complete inventory does not permit extraction
- **WHEN** every inventory prerequisite is confirmed
- **THEN** the report SHALL be `INVENTORY_COMPLETE` with `runAuthorized = false` and `d1Passed = false`
- **AND** no data run SHALL start from that report alone.
- **AND** its confirmed claims SHALL remain subject to independent evidence review rather than being reported as measured D1 coverage.

#### Scenario: Initial implementation slice
- **WHEN** only the offline inventory slice has been implemented and reviewed
- **THEN** extraction and full D1 gate tasks SHALL remain incomplete
- **AND** the D1 stage change SHALL NOT be archived as complete.

#### Scenario: Retained provider samples
- **WHEN** prior provider receipts prove historical account reads or independent transaction agreement only for a bounded sample
- **THEN** source admission SHALL preserve that measured evidence and its exact sampled scope
- **AND** it SHALL NOT infer full-envelope depth, mint-state, visibility, retention or cost completeness from those receipts.

#### Scenario: Source-admission sample is not a D1 pass
- **WHEN** an updated reviewed plan permits only finite source-admission samples and the frozen full-envelope gates remain unmeasured
- **THEN** the D1 conclusion SHALL remain `INCONCLUSIVE`
- **AND** no sample result SHALL replace the frozen 200-trade check, full-envelope thresholds, full-stage task completion or archive prerequisites.

#### Scenario: Owner declines PAYG spending
- **WHEN** the owner has explicitly declined Alchemy PAYG D1 spending
- **THEN** source-run planning SHALL preserve that denial and allocate only verified zero-paid sample options until a future explicit owner spending decision
- **AND** retained paid spike receipts SHALL NOT be treated as permission for new paid calls or a paid-run PLAN_READY.
