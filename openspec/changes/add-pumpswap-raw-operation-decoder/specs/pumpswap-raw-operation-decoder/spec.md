## Purpose

Separate independently proven PumpSwap operations from retained finalized raw transactions without network access, database mutation, economic aggregation or signal-grade admission, preserving exact lineage and explicit incomplete coverage.

## ADDED Requirements

Verification note (non-normative,2026-10-11): stable v4 has [independent financial/wiring APPROVE, Main450-test gate and post-gate47/75 qualification/reproduction PASS](../../design.md#implemented-and-verified-unarchived-2026-10-11),0 failures/errors/skips; code commit `0b066b1b87bd69e5045497b87369a9e8cff61148` is complete/not pushed. The active change remains unarchived, not an accepted main-spec capability. The requirements below, including independent actual endpoint proof and38/75 recognition, are unchanged.

### Requirement: Bounded offline input and complete dispositions

The system SHALL synchronously decode a finite recorded batch with trusted source metadata and exact transaction-result UTF-8 text, verify its raw digest/chain/signature/slot/session lineage, and return one explicit status/reason for every distinct input transaction. Failed transactions SHALL emit no confirmed operations. Missing or malformed error/finality/identity/version evidence SHALL remain unsupported or invalid, not success. Decoder execution SHALL perform no network, secret access or database mutation and SHALL not require current account state.

#### Scenario: Reconcile the saved run
- **WHEN** the preserved484bec54 batch contains124 distinct raw transactions with75 explicit successful and49 failed transaction results
- **THEN** all124 SHALL have source-linked dispositions and all49 failed transactions SHALL have no confirmed economic operations
- **AND** successful TX with unsupported or ambiguous operations SHALL remain counted with specific reasons rather than omitted or assumed decoded.

#### Scenario: Reject tampered or excessive input
- **WHEN** a raw digest or declared identity disagrees with exact payload evidence, or configured decoder work/output limits are exceeded
- **THEN** the batch SHALL return an explicit integrity/limit failure without a successful truncated report
- **AND** no raw, capture or historical research evidence SHALL change.

### Requirement: Canonical separate operation identity

The system SHALL preserve legacy/v0/v1 transaction versions, resolve complete static and explicitly supplied loaded account keys as applicable to each actual JSON shape, and derive operation identity from chain/transaction plus complete outer/CPI instruction path and leg ordinal. Provider/filter position, signer/fee payer and transaction-wide trader aggregation SHALL NOT substitute for actual operation/trader identity. Unproved stack paths or associations SHALL produce explicit unresolved records.

#### Scenario: Multiple traders and operations remain separate
- **WHEN** one successful transaction contains two independently proven PumpSwap operations, including the same trader or different traders
- **THEN** two canonically positioned records SHALL remain distinct with their actual roles and exact amounts
- **AND** repeated event transport frames SHALL not duplicate one operation or reuse a transfer as two executed legs.

#### Scenario: Resolve versions without invented address tables
- **WHEN** supported legacy/v0/v1 raw JSON provides complete indexed instruction/account evidence
- **THEN** the decoder SHALL use that observed structure and retain the version in lineage
- **AND** missing loaded keys, invalid indexes or missing/jumping CPI depth SHALL remain explicit unsupported evidence, never guessed from current accounts.

### Requirement: Pinned same-invocation execution proof

Confirmed operations SHALL require an explicit successful transaction, recognized PumpSwap variant, exact pinned discriminator/layout, source-linked account roles and a uniquely associated same-invocation execution event with consistent token transfer evidence. The May sell400/buy448/buy_exact_quote_in463 and October7 sell449/buy497/buy_v2 500/buy_exact_quote_in512/buy_exact_quote_in_v2 515 self-CPI schemas SHALL be supported only under their respective documentary pins and exact field/string/role checks, not length alone. Named v1 core/remaining roles and v2 fixed17 roles SHALL be validated under the applicable pinned source. Contradictory, duplicate conflicting, unknown or truncated frames SHALL remain explicit without partial fabricated operations. Documentary schema evidence SHALL NOT establish program deployment history. Multi-hop routing SHALL remain unsupported rather than treated as an ordinary buy.

#### Scenario: Validate supported exact event variants
- **WHEN** a successful buy, sell or buy_exact_quote_in invocation contains the pinned exact self-CPI frame and consistent declared pool/user/mint/account/transfer evidence
- **THEN** one operation SHALL retain exact source-frame lineage, canonical position, variant, trader, pool, asset sides and integer executed amounts
- **AND** mismatched roles, extra/truncated bytes, invalid bool/string or same-parent conflicting events SHALL reject confirmation with a reason.

#### Scenario: Current v2 fees are retained rather than fabricated payouts
- **WHEN** a pinned v2 invocation/event proves principal and actual same-invocation transfers but declares protocol/creator fees retained in the pool with documented zero protocol-recipient keys
- **THEN** principal confirmation SHALL use the v2 role/flow semantics rather than v1 recipient-payout equations
- **AND** retained/source-declared fees and signed virtual reserves SHALL remain separate facts, not fabricated transfers, zero expenses or executable prices.

#### Scenario: Current exact-input events prove actual debit separately
- **WHEN** a pinned current512 buy_exact_quote_in or515 buy_exact_quote_in_v2 frame declares quote_amount_in X, post-fee user_quote_amount_in E and exact LP/protocol/creator fees L/P/C
- **THEN** confirmed executed user quote debit SHALL equal independently corroborated X and satisfy X=E+L+P+C, with v1 vault inflow agreeing with quote_amount_in_with_lp_fee and E+L, or v2 vault inflow X minus independently proven buyback
- **AND** buyback SHALL remain a component of protocol fees, E SHALL remain separate event-declared evidence, and wrong flows/recipients, unproved cashback/refunds or unsupported deductions SHALL prevent confirmation
- **AND** nominal instruction/transfer amounts alone SHALL NOT prove principal, and the May463 contract SHALL retain its existing meaning.

### Requirement: Ancillary invocation proof without trade inflation

Exact pinned volume-init/close and protocol/creator sweep invocations SHALL retain explicit non-operation dispositions only after discriminator/account-role/same-parent event and applicable lifecycle/transfer proof. They SHALL NOT create economic trade operations or double-count accumulated fee payouts as current-trade income. Unknown, malformed, contradictory or ambiguously associated ancillary evidence SHALL remain unsupported/partial and SHALL NOT be silently ignored.

#### Scenario: Ancillary-only and mixed transactions
- **WHEN** a transaction contains only proven ancillary invocations, or proven ancillary plus an independently proven trade
- **THEN** the ancillary invocations SHALL be retained as non-operations and SHALL NOT count as trades or independently satisfy recognized-TX acceptance
- **AND** the mixed transaction MAY count only if its economic invocations and ancillary evidence are fully resolved; an unresolved relevant invocation SHALL leave it partial.

### Requirement: Token program and lifecycle evidence remain exact

The system SHALL distinguish classic SPL and Token2022 program identities and support only explicitly decoded applicable transfer/lifecycle variants. ATA creation/initialization/closure SHALL repair missing balance endpoints only with actual correctly ordered same-transaction proof of account/mint/owner/program and relevant transfers; otherwise absent balances SHALL remain unknown. Token2022 extension fees or changed transfer semantics SHALL NOT be inferred from classic SPL rules.

The narrow Token2022 lifecycle SHALL additionally support a uniquely proven170-byte System CreateAccount allocation assigned to Token2022, exact one-byte InitializeImmutableOwner22 with exactly the matching account, and exact applicable InitializeAccount1/16/18 under the same correctly linked ATA parent, ordered allocation/22/initialization before first use. Missing post-state SHALL additionally require proven Close9 after last use; present endpoints SHALL corroborate roles and exact raw-unit flows. The decoder SHALL retain full lifecycle references and SHALL NOT confirm principal from nominal transfer arguments alone. Larger/unknown extension layouts, fee/hook semantics, reallocation, prefunded Allocate/Assign and ambiguous repeated account epochs SHALL remain explicitly unsupported;170-byte allocation SHALL NOT be described as proof that a mint has no extensions.

For this non-native170-byte path, the decoder SHALL corroborate endpoint amount change against all proven ordinary token inflows/outflows touching that account in the complete transaction, including transfers outside PumpSwap. A missing pre amount SHALL mean zero only with the unique proven non-native initialization; a missing post amount SHALL mean zero only with its proved non-native close. Unknown token account touches, extension/hook effects, inconsistent balances or native170 layouts SHALL remain unsupported. Global conservation SHALL corroborate endpoints only and SHALL NOT allocate another invocation's or router's transfer to PumpSwap principal.

#### Scenario: Proven transient ATA
- **WHEN** a correctly ordered create/initialize/use/close sequence proves a transient token account's role and applicable token-program semantics
- **THEN** the operation SHALL carry those instruction references and the proven endpoint meaning
- **AND** missing creation/initialization/closure, changed owner/mint/program or unrelated lifecycle SHALL leave attribution incomplete rather than zero-fill it.

#### Scenario: Prove the narrow ImmutableOwner endpoint
- **WHEN** a successful transaction contains the unique ordered Token2022 ATA allocation170/InitializeImmutableOwner22/initialization/use sequence, applicable close proof and complete consistent endpoint evidence
- **THEN** its missing endpoint MAY be completed only for that proved account/mint/owner/program lifecycle with all exact instruction references retained
- **AND** an operation SHALL still require the independent same-invocation pinned event and consistent exact transfer/endpoint proof before confirmation.

#### Scenario: Reject unproved extension or lifecycle semantics
- **WHEN** ImmutableOwner22 has extra bytes/accounts, wrong account/program, missing or later initialization ordering, wrong allocation size, conflicting endpoint facts, fee/hook or unsupported extension evidence, or unproved repeated epochs/prefunded Allocate/Assign
- **THEN** the affected invocation SHALL remain explicit unsupported or partial with its reason and SHALL NOT yield an independently confirmed operation
- **AND** its transaction SHALL NOT inflate saved-window recognition merely because nominal transfer amounts match an event.

### Requirement: Exact amounts and explicit fee uncertainty

Amounts SHALL use exact integers, decimals only when observed or schema-proven, and fee facts SHALL distinguish event-declared values, uniquely attributed transfer flows and transaction-wide network fees. Unknown cashback/buyback/tips/extension expenses or shared transaction fees SHALL remain unknown, never zero-filled, proportionally split or called complete operation costs. Independent confirmed principal amounts MAY coexist with incomplete fee attribution, but an unproved principal SHALL not be confirmed. The decoder SHALL publish no USD price, liquidity, PnL, ranking or admissible market snapshot.

#### Scenario: Shared and unknown expenses
- **WHEN** principal execution is proven but a fee recipient/transfer path or allocation of the transaction-wide network fee is unproved
- **THEN** the confirmed operation SHALL expose incomplete fee status with the missing reason and source-declared amounts separately
- **AND** the result SHALL not describe those amounts as zero or fully attributable net economics.

### Requirement: Deterministic reproducible derivation

Every result SHALL identify decoder/schema/canonicalization versions, pinned source hashes and exact raw lineage with original source/received/admitted/ingested times kept distinct. Repeated equal input SHALL reproduce ordered statuses/operations and fingerprints; order SHALL be total by chain/transaction/path/leg, independent of batch submission order. Equal duplicate raw inputs SHALL deduplicate with lineage retained; conflicting same raw identity SHALL fail explicitly without selecting a preferred copy. Source bytes and legacy recorded/research behavior SHALL remain unchanged; capture gaps/unreceived loss SHALL not disappear from scope qualifications.

#### Scenario: Replay and conflict
- **WHEN** equal input batches are replayed or permuted
- **THEN** ordered semantic results and content fingerprints SHALL match
- **AND** a changed input/version SHALL produce distinguishable lineage, while conflicting copies of one raw identity SHALL produce explicit conflict rather than overwrite.

### Requirement: Bounded saved-window recognition acceptance

Saved-window MVP qualification SHALL require at least38 fully recognized transactions from the fixed75 successful transactions, with all124 distinct transaction statuses/reasons reconciled and all49 failed transactions emitting no confirmed operations. A fully recognized transaction SHALL contain at least one independently proven operation and SHALL have all relevant PumpSwap invocations resolved: economic invocations to proven operations, pinned ancillary invocations to proven non-operation dispositions under the preceding requirements. Partial, unsupported, ambiguous, ancillary-only, no-PumpSwap or duplicate input SHALL NOT inflate that numerator. Explicit unknown fee attribution MAY coexist with recognized principal only as permitted above; recognition SHALL NOT imply complete fees, complete collector coverage, PIT/outcome admission or production readiness. Required pinned variants and proof tests SHALL remain mandatory; rare unsupported formats SHALL remain explicit deferred limitations.

#### Scenario: Fixed majority boundary
- **WHEN** the saved batch has38 fully recognized successful transactions and all remaining inputs have explicit statuses/reasons
- **THEN** the majority recognition criterion SHALL pass as38/75, subject to all other proof/integrity/reproducibility checks
- **AND**37 fully recognized transactions SHALL fail this completion criterion without changing the denominator or inventing operations.

#### Scenario: Partial transaction cannot inflate coverage
- **WHEN** a successful transaction has one proven operation but another relevant PumpSwap invocation has unsupported or ambiguous execution evidence
- **THEN** its proven operation MAY remain separately visible with exact lineage
- **AND** the transaction SHALL remain partial and SHALL NOT count as fully recognized, even if counting it would reach38.
