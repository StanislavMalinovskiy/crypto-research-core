## Purpose

Defines a finite live PumpSwap transaction and account-state capture boundary with durable raw evidence, explicit missing coverage and no implicit research admission.

## ADDED Requirements

### Requirement: Explicit finite capture session
The collector SHALL remain disabled by default and SHALL require a versioned session configuration with an explicit Solana Mainnet pool/account allowlist, finite time, request, received-byte, retained-byte, concurrency, queue, retry and exact cost limits before connecting. Live launch SHALL require separately recorded owner authorization, verified account entitlements/limits and retention rights. Missing or unknown paid-cost bounds SHALL block paid launch. Every retry and reconnect SHALL consume the original session limits without reset.

#### Scenario: Launch prerequisites are missing
- **WHEN** the account limits, cost bound, pool list or launch authorization is absent
- **THEN** the collector SHALL report BLOCKED_FOR_LAUNCH before connecting
- **AND** documentation or a historical probe budget SHALL NOT substitute for that evidence.

#### Scenario: Resource limit is reached
- **WHEN** a request, subscription delivery, retry or wait cannot fit the remaining configured limits
- **THEN** the collector SHALL stop accepting work and close transport with an explicit limit reason
- **AND** unfinished work SHALL remain counted and distinguishable from completed coverage.

### Requirement: Finalized raw transaction and state capture
The collector SHALL subscribe only to configured pools and their explicitly verified pool/vault/mint accounts at finalized commitment. It SHALL obtain complete transaction evidence for transaction notifications, preserving failed transactions and explicit unavailable responses. Stable raw storage SHALL receive only finalized evidence, with exact provider payload, network identity, slot, provider, trusted received/admitted/ingested times, optional source/provider time and explicit unknown provider-visible time. Raw account snapshots SHALL retain account address, context slot, encoding and exact account data without inventing transaction position or historical reserves.

#### Scenario: Transaction fetch is unavailable or provisional
- **WHEN** a notification cannot produce complete finalized transaction evidence within finite retries
- **THEN** its durable disposition SHALL record UNAVAILABLE or UNVERIFIED_FINALITY and its known identity
- **AND** no provisional transaction SHALL enter stable raw transaction storage.

#### Scenario: State is not transaction-position evidence
- **WHEN** an account notification supplies a finalized context slot without transaction position or write version
- **THEN** that snapshot SHALL retain the actual supplied fields and explicit unknowns
- **AND** it SHALL NOT be labelled the pre/post state of a particular transaction or an executable price/depth fact.

### Requirement: Durable raw-first idempotent capture
The collector SHALL commit raw evidence before interpretation, preserve equal retries without duplicate rows or changed first times, and reject immutable transaction-content conflicts without overwrite. Account-state identity SHALL include exact network, provider, account, context slot and payload digest, retaining distinguishable variants at one slot with an ambiguity status. Session configuration, checkpoint and dispositions SHALL be durable and recovery SHALL not advance the checkpoint beyond committed evidence/dispositions. Provider calls SHALL occur outside short marketdata-owned transactions.

#### Scenario: Crash and equal redelivery
- **WHEN** a process stops after raw commit but before checkpoint completion and the same evidence is redelivered
- **THEN** restart SHALL resolve the original raw record and complete the pending disposition without duplicate domain effects
- **AND** the original payload, identity and first times SHALL remain unchanged.

#### Scenario: Conflicting transaction or state variant
- **WHEN** a known transaction identity has conflicting immutable content or two distinct account payloads share an account and slot
- **THEN** transaction conflict SHALL be explicit without overwrite and both state variants SHALL remain distinguishable
- **AND** no mutable latest-row rule SHALL claim unambiguous transaction-position state.

### Requirement: Explicit bounded interruption recovery
The collector SHALL durably record disconnect, queue overflow, fetch failure and shutdown intervals with affected pools/accounts and known slot/time boundaries. Transaction recovery SHALL use only finite overlap history for the configured pool and verify continuity to the last committed transaction boundary before closing that transaction gap. Missing historical state SHALL remain UNRESOLVED_STATE_GAP even if transaction recovery succeeds. Exhausted pagination, nonadvancing cursors and unproved continuity SHALL remain unresolved rather than claim continuous coverage.

#### Scenario: Transaction backfill succeeds after disconnect
- **WHEN** bounded history reaches the exact previous committed boundary and all returned identities have durable dispositions
- **THEN** only the transaction gap SHALL be marked recovered with its evidence references
- **AND** a current account snapshot SHALL NOT close the historical state gap.

#### Scenario: Queue or recovery capacity is exhausted
- **WHEN** bounded queue capacity or recovery page/time/byte allowance is exhausted
- **THEN** capture SHALL terminate or pause with a durable explicit gap and terminal reason
- **AND** no dropped item or unknown coverage SHALL become zero missing data.

### Requirement: Capture-only quality and accountable status
The collector SHALL expose a synchronous immutable session status including raw transaction/state counts, duplicates, conflicts, failures, unsupported/unknown facts, open gaps and cumulative requests/bytes/time/cost reservations with unknown actual billing labelled unknown. Raw payloads SHALL remain replayable without network access and unsupported May layouts, Token2022, self-CPI, ATA lifecycle and fee attribution SHALL not be interpreted by this slice. Secrets SHALL be absent from persisted evidence, diagnostics and error messages. Capture SHALL NOT generate signals, evaluation outcomes, D1/P1 admission or a production-readiness claim.

#### Scenario: Transport succeeds but economic evidence is unproved
- **WHEN** finalized raw payloads are durably stored but economics or continuity are unverified
- **THEN** status SHALL report only the verified capture scope and explicit unknown/unsupported facts
- **AND** successful transport SHALL NOT imply complete PumpSwap economics, full historic coverage or research admission.

#### Scenario: Offline replay and status inspection
- **WHEN** saved session evidence is inspected without provider access
- **THEN** exact payload hashes, identities, immutable configuration and ordered dispositions SHALL reproduce the recorded capture result
- **AND** inspection SHALL require no secret and SHALL disclose unresolved gaps and unknown actual billing.
