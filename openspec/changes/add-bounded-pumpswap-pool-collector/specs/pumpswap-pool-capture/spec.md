## Purpose

Defines a finite live PumpSwap transaction and account-state capture boundary with durable raw evidence, explicit missing coverage and no implicit research admission.

## ADDED Requirements

### Requirement: Explicit finite capture session
The collector SHALL remain disabled by default and SHALL require a versioned session configuration with an explicit Solana Mainnet pool/account allowlist, finite time, request, received-byte, retained-byte, concurrency, queue, retry and exact cost limits before connecting. Live launch SHALL require recorded owner authorization, configured account/billing-period limits and retention rights; OWNER_REPORTED settings SHALL remain distinguishable from independently observed settings. Missing or unknown paid-cost bounds or opening current-period usage SHALL block paid launch. Every retry, reconnect and replacement session SHALL consume the original authorization-period limits without reset. Whole-window subscription and RPC/control reservations SHALL be durably acquired atomically before provider work, with exact decimal rates and no downward rounding of cost limits. Reported calendar reset SHALL NOT automatically authorize a new window or erase unreconciled prior usage.

#### Scenario: Launch prerequisites are missing
- **WHEN** the account limits, cost bound, opening usage baseline, pool/account linkage or launch authorization is absent
- **THEN** the collector SHALL report BLOCKED_FOR_LAUNCH before connecting
- **AND** documentation or a historical probe budget SHALL NOT substitute for that evidence.

#### Scenario: Resource limit is reached
- **WHEN** a request, subscription delivery, retry or wait cannot fit the remaining configured limits
- **THEN** the collector SHALL stop accepting work and close transport with an explicit limit reason
- **AND** unfinished work SHALL remain counted and distinguishable from completed coverage.

#### Scenario: Shared monthly authority survives session replacement
- **WHEN** a session retries, crashes, is replaced, or reaches the reported billing reset while current-period usage or reservations remain unreconciled
- **THEN** the collector SHALL retain the cumulative account-period ledger and stop before work exceeding either configured exact cost or CU authority
- **AND** unknown prior or other account usage SHALL NOT become zero and a new session identifier SHALL NOT grant expense authority.

#### Scenario: Subscription allowance includes RPC and billing uncertainty
- **WHEN** the authorized first window is configured with 20,000,000 billable WSS bytes and 150,000 RPC/control CU at 0.04 CU/byte and $0.525 per million CU
- **THEN** the collector SHALL reserve 950,000 CU and exact $0.49875 against the remaining account-period authority before connecting
- **AND** every retry/control/history call and known received byte SHALL consume its allocation, with conservative in-flight/shutdown reservations retained and unobserved provider-side billed excess explicitly unknown rather than claimed impossible.

### Requirement: Finalized raw transaction and state capture
The collector SHALL subscribe only to configured pools and their explicitly verified pool/vault/mint accounts at finalized commitment. It SHALL obtain complete transaction evidence for transaction notifications, preserving failed transactions and explicit unavailable responses. Stable raw storage SHALL receive only finalized evidence, with exact provider payload, network identity, slot, provider, trusted received/admitted/ingested times, optional source/provider time and explicit unknown provider-visible time. Raw account snapshots SHALL retain account address, context slot, encoding and exact account data without inventing transaction position or historical reserves.

#### Scenario: Supplied linkage proof is inconsistent
- **WHEN** supplied finalized pool/vault/mint metadata lacks a source/version, exact payload digest, context slot or mapping fields, conflicts with the allowlist, or budgeted current reads conflict with declared program ownership or supported vault mint/authority metadata
- **THEN** that pool SHALL remain blocked before subscriptions with the specific unresolved linkage reason
- **AND** program ownership alone, guessed pool-layout offsets or unsupported token metadata SHALL NOT substitute for the supplied mapping proof.

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

#### Scenario: Actual reconnect preserves the interruption episode
- **WHEN** an authorized transient interruption occurs inside the original finite session
- **THEN** the collector SHALL preserve admitted frames/accounting/receive clocks and commit episode-specific TX/STATE gaps and frozen committed boundaries through its bounded writer before reconnect work
- **AND** it SHALL revalidate linkage, obtain durable subscription ACKs and verify finite overlap continuity to those exact boundaries without replacing them with newer callback checkpoints or closing earlier unrelated fetch gaps.

#### Scenario: Unavailable fetch is not a successful recovery checkpoint
- **WHEN** a transaction disposition is unavailable, pending, fetch-failed, provisional or conflicting
- **THEN** it SHALL NOT advance the successful recovery boundary, which SHALL reference committed CAPTURED or FAILED_TRANSACTION raw evidence
- **AND** verified recovery SHALL close only its exact episode gap IDs/interval, leaving older unrelated null/fetch gaps and historical sessions unchanged.

#### Scenario: Reconnect allowance and heartbeat are finite
- **WHEN** the current configuration permits reconnects and transport liveness is observed
- **THEN** the collector SHALL allow at most20 total reconnect attempts and at most3 consecutive attempts, resetting only the consecutive count after at least300s stable acknowledged connection, with minimum1s/3s backoff and one ping each30s requiring its matching pong within10s
- **AND** heartbeat/control traffic, retries, drain, ACKs and recovery SHALL consume the original request/byte/time/CU/cost limits, with no renewed session/window or reset total count.

#### Scenario: Permanent or terminal failure cannot reconnect
- **WHEN** security/authentication/TLS/protocol/linkage/conflict/unknown failure, writer/lease/overflow failure, owner stop or a resource/time/recovery limit occurs
- **THEN** the collector SHALL stop with its explicit cause and unresolved coverage where applicable
- **AND** it SHALL NOT reconnect, retry a terminal operation or grant another launch automatically.

#### Scenario: Historical recovery has partial unavailable evidence
- **WHEN** an episode reaches its known boundary but a transaction remains explicitly unavailable after finite availability retries, or a pool had no committed boundary at interruption
- **THEN** its transaction gap SHALL remain unresolved with the exact partial reason and missing identity/boundary
- **AND** bounded current capture MAY continue with explicit incomplete coverage, without treating a quiet pool, null response or new current account read as recovered historical evidence.

#### Scenario: Established history continuity cannot be verified
- **WHEN** a known committed boundary is absent from finite history, cursor/order is invalid or recovery capacity is exhausted
- **THEN** recovery SHALL stop the session with an explicit unresolved episode
- **AND** returned rows alone SHALL NOT authorize a recovered-gap claim.

#### Scenario: Transaction backfill succeeds after disconnect
- **WHEN** bounded history reaches the exact previous committed boundary and all returned identities have durable dispositions
- **THEN** only the transaction gap SHALL be marked recovered with its evidence references
- **AND** a current account snapshot SHALL NOT close the historical state gap.

#### Scenario: Queue or recovery capacity is exhausted
- **WHEN** bounded queue capacity or recovery page/time/byte allowance is exhausted
- **THEN** capture SHALL terminate or pause with a durable explicit gap and terminal reason
- **AND** no dropped item or unknown coverage SHALL become zero missing data.

### Requirement: Database-free callbacks with bounded durable writer
Every WebSocket callback and its accounting, headroom, error and overflow handlers SHALL perform no database read/write, DB-dependent lock wait or durable-future wait. They SHALL use conservative in-memory cumulative admission backed by the original durable whole-window reservation and a separate bounded marketdata writer. Aggregate queued/in-flight payload work SHALL respect configured item/byte bounds. Received bytes and original trusted receive times SHALL remain accountable, including limit-triggering traffic. Validated ACK arrival SHALL remain distinct from durable ACK commit; subscription success and dependent notification application processing SHALL require the exact ACK envelope to be committed. Writer transactions SHALL remain short, lease-fenced and provider-I/O-free. Crash/unflushed accounting SHALL remain explicitly unknown under retained reservation, never reset or refunded.

#### Scenario: Notifications arrive while durable storage is slow
- **WHEN** notification writes are blocked and a valid ACK arrives behind notifications within remaining queue capacity
- **THEN** callbacks SHALL admit and timestamp bounded frames without waiting for database work
- **AND** ACK arrival SHALL NOT publish subscription success before its exact envelope commits or release dependent notification processing before durable ACK.

#### Scenario: Callback queue overflows or writer cannot finish
- **WHEN** item/byte capacity is exhausted, a writer fails or a durable ACK misses its finite shared deadline
- **THEN** callback handling SHALL signal explicit terminal failure and stop transport without database work or automatic retry
- **AND** an independent bounded control/writer path SHALL retain known accounting and durable unresolved gaps where storage/lease permits, otherwise expose an unfinished session/unknown tail without claiming committed coverage.

#### Scenario: Previously reserved authority survives callback crash
- **WHEN** callback accounting has admitted bytes that have not committed before a crash or session replacement
- **THEN** the original period/window reservation and committed accounting SHALL remain preserved
- **AND** unknown unflushed usage SHALL NOT be treated as zero or grant replacement expense authority.

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

### Requirement: Accurate bounded sanitized capture diagnostics
The collector SHALL retain versioned bounded secret-free operation diagnostics and summaries in its existing replayable envelopes. They SHALL identify actual phase, observation time, attempt/episode, stable category, available HTTP/RPC/WS numeric codes, sanitized close/error text or explicit redaction/unavailability, and bounded safe cause/stack evidence. It SHALL distinguish valid null/no-error RPC availability from RPC failure, malformed responses, provisional evidence and failed on-chain transactions. Durable summaries SHALL identify actual disposition counts, committed last positions, cumulative counters/reservations and reconnect/recovery counts separately from local unflushed attempts/tails. It SHALL NOT claim an exact root cause from a category, stack or remote reason alone.

#### Scenario: Null transaction availability is delayed
- **WHEN** finalized getTransaction returns a valid matching result:null with no JSON-RPC error
- **THEN** the collector SHALL permit at most2 delayed availability retries at minimum2s and5s, sharing the existing per-operation retry allowance and all original limits
- **AND** exhaustion SHALL retain UNAVAILABLE and its explicit transaction gap without synthetic raw evidence or a global capture stop solely for null availability.

#### Scenario: RPC error is not a null result
- **WHEN** an HTTP response contains a matching JSON-RPC error or invalid result/error/identity shape
- **THEN** the collector SHALL retain the actual safe error envelope/code or explicit malformed-response category and FETCH_FAILURE where applicable
- **AND** it SHALL NOT classify that response as a successful null/no-error availability result or retry a JSON-RPC error without an explicit allowed policy.

#### Scenario: WebSocket close or nested exception is observed
- **WHEN** a close callback or operational exception is observed
- **THEN** diagnostics SHALL retain the actual available close code and safe allowlisted reason or explicit redaction, plus safe exception classes and stack locations with at most8 cause levels/32 frames/8,192UTF8 bytes per record
- **AND** raw Throwable messages, nested/suppressed sensitive text, authenticated URLs, provider/DB secrets and local secret paths SHALL NOT enter logs, public errors or persisted diagnostics; missing peer codes/causes SHALL remain unavailable.

#### Scenario: Diagnostics cannot be persisted
- **WHEN** diagnostics encounter bounded queue/cap exhaustion, construction failure, unavailable writer or lost lease
- **THEN** the original operational reason and conservative accounting/reservation SHALL remain observable with an explicit unknown undurable tail
- **AND** diagnostics SHALL NOT mask the cause, claim a committed record or start another paid operation/reconnect.
