## Purpose

Expose independently confirmed single-event PumpSwap multi-hop pool exchanges from immutable recorded transactions without inferring route-wide traders or rewriting earlier decoder evidence.

## ADDED Requirements

### Requirement: Additive bounded offline version

The system SHALL expose a separately versioned synchronous offline decoder alongside fixed v4, accept exact source-linked recorded batches and explicit target-pool bindings, preserve the unmodified v4 result, and return one v5 transaction disposition per distinct source. Input bounds SHALL remain 128 distinct sources, 8,000,000 UTF-8 bytes per raw result, 64,000,000 aggregate input/output bytes, 10,000 instructions/operations and 4,096 decoded bytes per instruction. Excess or conflicting raw evidence SHALL fail closed, not truncate. Execution SHALL perform no network, secret access, database read/write or collector work. Existing raw, receipts, fixed-v4 fingerprints, persistence and candle behavior SHALL remain unchanged.

#### Scenario: Repeat a versioned recorded batch
- **WHEN** identical sources and target-pool bindings are repeated or permuted
- **THEN** v5 ordered dispositions, pool legs and version-qualified fingerprint SHALL repeat exactly
- **AND** the nested v4 result SHALL equal the standalone v4 result on that batch, including its fingerprint.

#### Scenario: Corrupt or excessive input
- **WHEN** source identity, raw digest, immutable duplicate content or a declared bound fails validation
- **THEN** the request SHALL fail explicitly without partial successful output
- **AND** no source data or earlier result SHALL change.

### Requirement: One own same-invocation event

A confirmed pool leg SHALL require explicit successful finalized source evidence, the pinned exact `multi_hop_swap` discriminator/layout and fixed roles, a complete unambiguous instruction path, and exactly one own same-invocation Buy/Sell self-CPI event. The event SHALL bind pool/user/accounts to observed source roles and the target pool's actual base/quote vaults, mints and token programs. The exact event layout SHALL be consumed, including strings, booleans and signed fields; a known length or presence of a pool address alone SHALL NOT prove execution. A pool leg SHALL require a positive event base amount matching same-invocation base transfer flow and complete observed pool-vault pre/post change; unknown attribution, contradictory flow or unproved base endpoints SHALL remain unsupported.

#### Scenario: Direct and aggregator calls
- **WHEN** a retained direct call or a call beneath `FLASHX8`, `6Vo3245` or `term9YP` contains exactly one source-proven target-pool exchange
- **THEN** the decoder SHALL emit one distinct source-linked pool leg with its exact direction, base raw quantity/decimals, pool, vaults, mints, complete invocation/event/transfer paths and documentary pins
- **AND** the outer program SHALL remain observed routing evidence, not a route-wide trader identity.

#### Scenario: Multiple or unassociated own events
- **WHEN** a multi-hop invocation has two or more own Buy/Sell events, even identical ones, or lacks a unique correctly associated own event
- **THEN** it SHALL remain `UNSUPPORTED` with `MULTIPLE_OWN_EVENTS`, `MISSING_OWN_EVENT` or an exact association/layout reason
- **AND** no first-event choice, event deduplication or partial pool-leg confirmation SHALL replace that failure.

### Requirement: Observed initiator is not ultimate trader

Every new pool leg SHALL retain the observed multi-hop invocation's initiator in a dedicated field, separately identify any event-reported user, and explicitly mark the ultimate trader undetermined. Neither transaction fee payer/signers, intermediate token-account owners, event user nor the direction of one pool leg SHALL establish the final buyer/seller or full route. A pool leg SHALL NOT be coerced into a legacy operation requiring a proven trader.

#### Scenario: Aggregator initiator differs from ultimate beneficiary
- **WHEN** a confirmed pool leg appears within an aggregator invocation
- **THEN** its initiator SHALL be taken only from its validated observed invocation role
- **AND** final-trader identity SHALL remain absent with an explicit undetermined status, without assigning an intermediate owner as the GTA buyer.

### Requirement: Exact pool-net SOL price or explicit unpriced volume

A published price SHALL be explicitly labeled `POOL_NET_SOL_PER_BASE`, using the absolute actual WSOL quote-vault change in lamports divided by the exact confirmed base-vault exchange quantity with source-proven decimals. WSOL identity and nine decimals, complete same-invocation quote transfers including observed deductions, and whole-transaction vault endpoint reconciliation SHALL prove that exact quote change belongs to the leg. Event curve/gross amounts, user debit/credit, nominal transfers and net pool amount SHALL remain distinguishable; none SHALL silently replace another. Price SHALL use an exact reduced positive integer ratio, without binary floating point or lossy decimal division. Confirmed base volume with incomplete quote/unit/attribution proof SHALL remain visible with absent price and `PRICE_NOT_CONFIRMED` plus a specific reason; a contradictory execution/event/base proof SHALL remain unsupported rather than a confirmed unpriced leg. No USD, user execution-price, complete fee, liquidity or signal-grade claim SHALL follow.

#### Scenario: Observed buyback changes pool-net amount
- **WHEN** the source proves quote inflow of 499950000 lamports and an attributable buyback outflow of 124925 lamports, with quote-vault pre/post change of 499825075
- **THEN** a confirmed price numerator SHALL use 499825075 lamports with the proved base quantity/decimals
- **AND** neither the event user amount 499700149 nor gross 499950000 SHALL be published as that pool-net price basis.

#### Scenario: Base is proven but quote attribution is incomplete
- **WHEN** one own event and actual base-vault exchange are proven but exact quote endpoints, units or transfer attribution cannot be established
- **THEN** the base pool leg SHALL remain confirmed and counted with `PRICE_NOT_CONFIRMED` and no price
- **AND** guessed reserve differences, gross event amounts or final-user ownership SHALL NOT repair the missing proof.

### Requirement: Complete honest successful-transaction coverage

The v5 disposition SHALL resolve all relevant PumpSwap invocations before counting a successful transaction fully decoded, preserving non-target, non-operation, unsupported, partial and failed evidence. A confirmed but unpriced leg SHALL count as a recognized pool exchange, not a priced trade. Multiple unresolved invocations SHALL leave the transaction partial or unsupported; confirmed operations from v4 SHALL remain separately visible and SHALL NOT be duplicated as pool legs. Offline qualification SHALL reconcile all 1166 original transactions, the unchanged 224 explicit successes and 942 failures, reporting fully decoded successful-TX numerators before/after, separately counting new confirmed/priced/unpriced legs and remaining reasons. The 114 unknown-variant cases SHALL all be attempted without replacing the denominator or forcing an unproved result.

#### Scenario: Same immutable window before and after
- **WHEN** the hash-verified `a7f5fa35` export is qualified with unchanged v4 and new v5 in bounded batches
- **THEN** the baseline SHALL reproduce 57 fully decoded of 224 successes, with all 942 failures emitting no confirmed operations
- **AND** v5 SHALL report its actual fully decoded numerator over those same 224 successes, explicit priced/unpriced pool-leg counts and all remaining reasons; no receipt shall claim complete chain coverage or signal readiness.

#### Scenario: Another unresolved invocation remains
- **WHEN** one successful transaction contains a confirmed target pool leg and another relevant unsupported invocation
- **THEN** the confirmed leg SHALL remain visible with exact lineage
- **AND** the transaction SHALL NOT inflate the fully decoded successful-TX numerator.
