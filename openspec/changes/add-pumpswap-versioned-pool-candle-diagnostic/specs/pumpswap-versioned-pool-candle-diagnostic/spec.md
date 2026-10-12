## Purpose

Calculate complete bounded saved-window pool-net SOL candles and a diagnostic native-price pattern under one selected decoder interpretation without mixing price meanings or admitting historical trading evidence.

## ADDED Requirements

### Requirement: Complete bounded selected-version input

The system SHALL synchronously accept immutable recorded sources, explicit1–3pool bindings, exact selected decoder `pumpswap-pool-legs-v5`, aligned event window and explicit derivation observation/knowledge instants. It SHALL verify complete source digests/identity and select exactly one source interpretation per chain/signature, preserving all successful/failed/unsupported/non-operation dispositions. Equal duplicates SHALL deduplicate; conflicting copies or alternative provider/session interpretations of the same transaction SHALL reject instead of latest-version selection. Whole-window bounds SHALL be4096distinct TX,8,000,000 UTF-8 bytes per raw result,64,000,000 aggregate raw bytes,10000total operations/populated buckets,1000time-bucket positions and64,000,000 canonical output bytes. Processing SHALL use decoder batches of at most128sources without truncating, sampling or omitting raw based on result. All overflow/integrity failure SHALL reject without partial success. This route SHALL perform no network, secrets or database access/writes, and existing APIs/receipts SHALL retain their original meaning.

#### Scenario: Dense window crossing old batch bound
- **WHEN** a valid selected window contains721 or1137raw transactions within the declared whole-window bounds
- **THEN** every distinct source SHALL be classified and all eligible operations considered across bounded decoder chunks
- **AND** the old128-source API SHALL remain unchanged; neither chunk boundaries nor input permutation SHALL change global operation identities/counts/candles/fingerprint.

#### Scenario: Reject ambiguous interpretations
- **WHEN** the same chain/signature has different source bindings, raw content or selected versions
- **THEN** the request SHALL fail explicitly rather than merge old/new interpretations
- **AND** equal duplicate input SHALL not double-count ordinary operations or multi-hop legs.

### Requirement: One exact pool-net price basis

All priced ordinary and multi-hop operations SHALL use `POOL_NET_SOL_PER_BASE`: absolute independently attributable WSOL quote-vault flow in lamports divided by the exact confirmed base exchange, with source-proven decimals. Ordinary user execution amounts and event gross/user amounts SHALL NOT substitute for that flow. Each ordinary operation's own transfer paths and invocation roles SHALL establish its base/quote attribution; complete transaction-wide vault ledger SHALL reconcile actual pre/post amounts and all other touches. Multiple operations sharing a vault SHALL each own distinct transfer evidence; their independent contributions SHALL sum to actual endpoints, never each receive the whole-transaction delta. Unknown units, flow, hook or attribution SHALL preserve confirmed volume/counts with absent price and a reason. Contradictory source identity/base/execution evidence SHALL fail or remain unsupported, not priced. Prices SHALL be reduced positive integer ratios and comparisons exact cross multiplication; no USD, final-trader, complete-fee or executable-price inference SHALL follow.

#### Scenario: Ordinary and multi-hop price semantics agree
- **WHEN** a v4-proven ordinary operation and a v5 multi-hop leg have identical proven pool-net quote/base flows but different user/gross quantities
- **THEN** their new price ratios SHALL be equal under the same declared pool-net basis
- **AND** old user-price candles/fingerprints SHALL remain unchanged.

#### Scenario: Two invocations touch one vault
- **WHEN** two confirmed ordinary operations touch the same pool vault within one transaction
- **THEN** each priced operation SHALL use only its uniquely attributed invocation flow with complete endpoint reconciliation
- **AND** missing/nonunique attribution SHALL remain counted unpriced, not reuse the full transaction delta for both.

### Requirement: Event time, knowledge and total chronological order

Event buckets SHALL use only exact retained integral `blockTime`, agreeing with sourceTime when supplied, normalized UTC microsecond request boundaries and300-second epoch-aligned intervals. Raw received/admitted/ingested clocks and explicit `derivationObservedAt` SHALL remain distinct; new derived knownAt SHALL be their maximum. Derivation time SHALL NOT precede the original source clocks and SHALL NOT impersonate a stored revision/batch time. Only knownAt<=K SHALL participate; later K SHALL be explicitly retrospective, not historical-at-E availability. Missing event time/index, incompatible index/slot/signature or conflicting decimals SHALL remain explicit or fail as applicable. Total order SHALL be eventTime, slot, index-presence/index, signature in unsigned UTF-8 order, numeric full invocation path, leg ordinal and version-qualified source reference. Empty buckets SHALL remain absent, not synthetic zero/carry-forward bars. Equal extrema SHALL retain the earliest operation in that order.

#### Scenario: Same-slot ordering and late derivation
- **WHEN** input order differs, source indexes are missing, or new derivation was observed after event end E
- **THEN** technical order/fingerprints SHALL remain deterministic with explicit missing-index uncertainty
- **AND** retrospective analysis at K SHALL not claim the derived prices were available at E.

### Requirement: Counted unresolved target-pool evidence

The report SHALL reconcile every source to selected dispositions and every confirmed operation to included/excluded/priced/unpriced counts and exact lineage. A successful unsupported/partial relevant invocation whose raw account/vault evidence touches a target pool SHALL mark its corresponding event bucket uncertain; inability to prove pool membership or event time SHALL remain conservatively unknown. Proven failed transactions SHALL emit no operations and SHALL NOT taint executed price merely because their keys mention a pool. Proven NON_OPERATION/NO_PUMPSWAP SHALL not be relabeled missing swaps. Existing collector coverage/gaps remain incomplete/unknown; absent capture evidence SHALL not become zero loss. Reports SHALL preserve origin/proof versions and limits, with no accepted-data/signal/valuation claim.

#### Scenario: Unknown swap could change the close
- **WHEN** an unresolved successful target-pool invocation is present inside either diagnostic bucket, even alongside two priced endpoints
- **THEN** uncertainty SHALL remain counted and the diagnostic SHALL be INSUFFICIENT
- **AND** an unresolved membership/time SHALL not silently become a clean negative result.

### Requirement: Native-SOL diagnostic reuses existing rule

The separate read-only diagnostic SHALL consume new candles only through the owning marketdata API and retain the existing two adjacent300-second intervals and inclusive5% exact-close rule. It SHALL return TRIGGER only for adequate current/prior prices satisfying current/prior>=105/100, NO_TRIGGER only for adequate below-threshold prices, and INSUFFICIENT for missing bars/prices/index/time/knowledge or unresolved relevant pool evidence. It SHALL neither widen windows nor require positive saved triggers. Algorithm/source versions and explicit E/K/derivation time SHALL identify this pool-net route separately from original user-price diagnostics. No candidate, accepted signal, risk result, evaluation or database effect SHALL occur.

#### Scenario: Exact boundary and unchanged old route
- **WHEN** complete pool-net closes are1 and1.05, or exactly below1.05
- **THEN** the new route SHALL return respectively TRIGGER and NO_TRIGGER by exact arithmetic
- **AND** old diagnostic boundary behavior/result fingerprints SHALL remain unchanged; unknown evidence instead SHALL produce INSUFFICIENT.

### Requirement: Same-window offline qualification

Qualification SHALL use the immutable1166-TX Contabo export, unchanged224success/942failed and three configured pools. The full aligned interval `[2026-10-11T11:10:00Z,2026-10-11T11:35:00Z)` SHALL yield sparse300s candles and four adjacent-bucket diagnostics per pool ending11:20/25/30/35. The report SHALL give one per-pool table with confirmed/priced/unpriced operations, populated/priced bars, uncertainty and TRIGGER/NO_TRIGGER/INSUFFICIENT counts/reasons. All12pairs, including previously dense-limit pairs, SHALL be processed completely or explicitly fail under whole-window bounds; no artificial positive outcome SHALL be an acceptance condition. Rare39decoder dispositions and old raw/receipts SHALL remain unchanged. Saved124-TX six-hour evidence SHALL serve unchanged compatibility regression only, not an expanded research comparison.

#### Scenario: Reproduce the saved diagnostic
- **WHEN** identical immutable inputs, selected version/bindings and supplied timestamps are repeated or processed with another permitted chunk size
- **THEN** all source/operation/candle/pair computational facts and versioned fingerprints SHALL match
- **AND** the output SHALL remain an observed retrospective diagnostic, not strategy performance or accepted historical evidence.
