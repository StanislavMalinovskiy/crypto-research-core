## Purpose

Defines outcome-blind, reproducible validation of the R1 D1 field inventory and the mandatory boundary between an inventory and authorization to extract historical evidence.

## ADDED Requirements

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
