## Purpose

Expose complete bounded retained capture source windows for on-demand selected-v5 candles and a nonadmitted native-SOL diagnostic without changing capture or durable fixed-v4 evidence.

## ADDED Requirements

### Requirement: Explicit complete retained received-time scope

The system SHALL synchronously select one existing capture session and all committed transaction dispositions whose own notification received time lies in the declared half-open received-time scope. It SHALL include every pool binding of each selected signature, deduplicate a signature once, preserve failed raw transactions and count all non-raw-linked statuses separately. It SHALL derive the exact enrolled1–3pool bindings from retained configuration, not caller substitutions. It SHALL preserve original raw payload/digest/slot/provider/session/source/received/admitted/ingested facts and exact notification/history proof references. Missing or contradictory required raw/proof/configuration evidence SHALL reject the whole request. An empty valid scope SHALL remain an explicit empty result. Selection completeness SHALL mean visible retained scope only, never chain/event completeness or zero loss.

#### Scenario: Cross a page boundary without losing failed evidence
- **WHEN** the received scope contains129 distinct raw-linked signatures including failed and unknown-event-time sources and repeated bindings across pools
- **THEN** all129 signatures and their complete bindings SHALL be included exactly once across pages
- **AND** failed/unsupported/unknown-time/non-raw-linked facts SHALL remain measurable rather than filtered to positive operations.

#### Scenario: Reject incompatible retained proof
- **WHEN** a selected raw identity, digest, slot, clock, binding or required history envelope contradicts its retained disposition
- **THEN** the request SHALL fail explicitly with no partial success or mutation
- **AND** another provider/session interpretation SHALL not silently replace it.

### Requirement: Consistent finite read without capture mutation

The system SHALL read configuration, selected identities, statuses, raw/proofs and conservative session gap context through one independent read-only consistent snapshot with a10-second transaction/query bound. It SHALL use deterministic signature bytewise order, payload-free overflow/size checks before hydration and internal pages of at most128 sources; no continuation cursor SHALL escape that snapshot. The whole request SHALL admit at most4096 distinct scoped signatures including non-raw-linked signatures,12288 bindings,24576 referenced proof envelopes and1000 gap records, each payload at most8,000,000UTF-8 bytes, configuration at most1,000,000bytes, combined hydrated source/proof bytes at most64,000,000 and each canonical public result at most64,000,000bytes. The received scope SHALL be at most24h; event scope SHALL retain the existing1000 aligned300s bucket bound. Excess count/bytes/time or incomplete page read SHALL reject, not sample/truncate. Reads SHALL perform no provider work, persistent progress, receipt/cursor update, capture lifecycle transition or outer-transaction participation.

#### Scenario: Concurrent publication and dense input
- **WHEN** a source is committed or a pending disposition resolves after the read snapshot begins, or the snapshot contains4097 scoped signatures
- **THEN** the late change SHALL not alter that snapshot's members/statuses
- **AND**4097 SHALL reject as excess instead of returning4096 as complete.

#### Scenario: Qualify observed limit diagnostics honestly
- **WHEN** bounded enumeration detects4097 signatures, a byte limit, or timeout before complete scope accounting
- **THEN** the failure SHALL identify observed distinct signatures/observed source-proof bytes with independent EXACT, AT_LEAST or UNKNOWN qualifiers and absent unobserved values
- **AND**4097 from an overflow probe SHALL mean AT_LEAST4097, not a fabricated exact full count; unobserved bytes SHALL NOT be guessed zero
- **AND** the failure path SHALL perform no unbounded full-scope COUNT or additional read to finish totals.

### Requirement: Actual read and derivation clocks are not historical availability

Authoritative public times SHALL normalize to UTC microseconds. The result SHALL distinguish database snapshot-observed time R, newly observed derivation time D and event window/end E. The NEW retained/diagnostic requests SHALL have no requested historical K parameter; effective knowledge cutoff SHALL equal actual D. D SHALL be observed after the read and successful on-demand derivation using the injected clock and SHALL be no earlier than R or selected source/proof clocks; a clock behind those facts SHALL reject rather than invent D. New derived knownAt SHALL retain the existing maximum source/derivation rule. A newly committed backfill SHALL not be claimed visible in a prior database snapshot merely because its event/received time is old. Existing pure API historical-cutoff behavior SHALL remain unchanged. Results SHALL remain diagnostic-only/not admitted, with unknown capture coverage and actual read visibility limitations, not verified historic strategy availability.

#### Scenario: Derivation advances after the read snapshot
- **WHEN** the injected clock advances while preliminary on-demand calculation runs
- **THEN** the published descriptor SHALL use actual post-calculation D and K=D rather than the earlier R
- **AND** a clock observation earlier than R/source/proof clocks SHALL reject, not substitute event time, caller input or a fabricated stored revision clock for D.

### Requirement: One selected on-demand interpretation and unchanged pure rule

The retained bridge SHALL supply its verified recorded population to the existing selected `pumpswap-pool-legs-v5` pool-net candle route and existing adjacent300s native-SOL rule. Ordinary and multi-hop prices SHALL retain one exact POOL_NET_SOL_PER_BASE basis and inclusive105/100 comparison. The signal bridge SHALL acquire the source snapshot once through marketdata's public API, then derive outside database transactions, without another SQL read. Its wrapper SHALL preserve the unchanged pure diagnostic result/fingerprint and separately identify an effective status: INSUFFICIENT for any scoped non-raw-linked disposition, capture conflict or unresolved conservative session TRANSACTION/STATE gap context, or inadmissible knowledge; otherwise the existing pure status. A pure TRIGGER SHALL not conceal capture incompleteness. Source selection, configuration digest, clocks, scope, full disposition/proof/gap manifest and versions SHALL participate in the new wrapper fingerprint. Equal frozen input/clocks SHALL reproduce equal results; later independent reads SHALL not claim identical snapshot identity. Existing calculation bounds and fail-closed uncertainties SHALL remain.

#### Scenario: Capture pending is not a clean trigger
- **WHEN** two otherwise adequate bars satisfy5percent growth but one scoped signature is pending or relevant gap context is unresolved
- **THEN** the pure result SHALL remain separately readable under its original identity
- **AND** the new wrapper's effective status SHALL be INSUFFICIENT with counted reasons, not accepted ENTRY or a clean negative.

### Requirement: Adjacent read-only compatibility and nonadmission

The system SHALL leave existing fixed-v4 publication, V14 batches/revisions/receipts, capture configuration/defaults/authority/holds/cursors, old decoder APIs, legacy candles/diagnostics and accepted signal/evaluation behavior unchanged. The new v5 result SHALL be on-demand only, not durable V14 publication. No normalized market snapshot, candidate, accepted signal, risk decision or evaluation outcome SHALL be written. No active collector restart/deploy during its first24h SHALL follow this change. This implementation SHALL add no invocation CLI/endpoint/wrapper/server call or claim an already-working UI route. Activation, scheduler, provider requests, paid calls, screen implementation, pool expansion, rare-format repair, holdout/research admission and execution SHALL remain outside this capability.

#### Scenario: Real PostgreSQL compatibility
- **WHEN** the new route reads a populated V18 capture database, including a running committed prefix and a stopped session
- **THEN** every owning table's durable values, old raw clocks/holds/receipts and existing fixed-v4 readback SHALL remain unchanged
- **AND** capture flags/worker/coordinator behavior and all existing accepted signal/evaluation effects SHALL remain unchanged.
