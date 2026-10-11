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

### Requirement: Raw-only supported transaction versions and narrow unsupported failures
The collector SHALL request maxSupportedTransactionVersion1 with finalized/json for live getTransaction and full history recovery, and SHALL preserve exact supported legacy/v0/v1 raw JSON under existing admission/identity/time/hash rules without requiring v0 ALT fields or interpreting v1 economics. A known higher-version failure SHALL become a durable per-TX FETCH_FAILURE with explicit UNSUPPORTED_TRANSACTION_VERSION gap/diagnostic only under the strict recognition policy below; all other errors SHALL retain their existing terminal/retry policy. Behavioral policy SHALL participate in immutable configuration/source identity, without rewriting old raw evidence.

#### Scenario: Supported v1 raw payload has no ALT fields
- **WHEN** finalized v1 result has existing required slot/signatures/message-object/meta.err shape, including transactionConfig but no ALT fields
- **THEN** exact version/JSON/meta.err SHALL be preserved as successful or failed-on-chain raw evidence with unchanged hash/identity/time rules
- **AND** missing ALT fields SHALL NOT cause unsupported classification or imply normalized economics.

#### Scenario: Known future version cannot be fetched
- **WHEN** getTransaction HTTP200 passes existing secret/UTF8/JSONRPC2.0/matching-ID/exclusive-error guards and exact integral code-32015 with the complete observed unsupported-version message template containing matching canonical decimal N tokens greater than requested ceiling1
- **THEN** the single fenced writer SHALL retain exact safe error/notification identity and atomically commit FETCH_FAILURE with an explicit UNSUPPORTED_TRANSACTION_VERSION TX gap/diagnostic before continuing unrelated transactions/account notifications
- **AND** no raw transaction, successful checkpoint, retry/reconnect/automatic ceiling increase or recovered-gap claim SHALL be fabricated.

#### Scenario: Unsupported-version resemblance is not recognition
- **WHEN** code alone, another method/HTTP status, altered/free-form message, claimed N<=1, mismatched version tokens, malformed response/identity or security/protocol/unknown failure occurs
- **THEN** the response SHALL NOT qualify for nonterminal unsupported degradation
- **AND** its existing strict failure policy and secret-safe evidence handling SHALL remain effective.

#### Scenario: Recovery reaches boundary with unsupported transaction
- **WHEN** scoped overlap reaches the exact successful boundary but a specific getTransaction has a recognized higher-version failure
- **THEN** its episode transaction gap SHALL remain explicitly partial/unresolved while bounded current capture MAY continue
- **AND** missing established boundary, invalid/nonadvancing history, whole-page RPC error or capacity/writer/lease failure SHALL remain blocking and STATE/unrelated gaps SHALL NOT be closed.

#### Scenario: New owner authority continues toward a bounded six-hour result
- **WHEN** the recorded standing owner authority permits Main-managed fresh windows after reviewed repairs until one successful configured6h
- **THEN** each prospective window SHALL preserve original cumulative account/time/storage/resource authority and old immutable session/terminal evidence, with startup/ACK/recovery inside its original<=21,600s end
- **AND** a complete stability/evidence analysis MAY report explicit incomplete coverage but SHALL NOT imply full chain coverage/admission, extend the window, grant12h or cause an in-code terminal restart.

### Requirement: Deadline-safe recovery and fenced bounded closing
Recovery SHALL recheck its original episode/window deadline, writer health and latched interruption after blocking work and before queued recovered-gap transitions, completion publication or clearing the episode deadline, including unknown-boundary branches. Diagnostic summaries SHALL use bounded coherent durable projections without repeatedly materializing all payload-bearing dispositions; optional diagnostics SHALL NOT delay primary terminal persistence or authorize work after expiry. Closing SHALL disable new provider admission and finish only already-admitted bounded tail/accounting and terminal gaps/status under the current valid fence, preserving original terminal cause and all conservative resource authority. Closing SHALL respect the existing timeoutSeconds+1 drain bound and existing10s operation/30s lease limits, including natural configured-window end; no timeout/window/budget increase or expired-lease reacquisition SHALL be introduced.

#### Scenario: Blocking summary or callback crosses recovery deadline
- **WHEN** recovery reaches or passes its original deadline or an interruption is latched while summary/read/write work is pending, including a pool with unknown boundary
- **THEN** subsequent completion or recovered-gap transition SHALL recheck that condition in the owning execution path and SHALL NOT publish successful completion, clear/extend the deadline or close a gap based on post-expiry work
- **AND** it SHALL retain the original explicit terminal cause/unresolved interval without mutating already valid committed evidence.

#### Scenario: Slow optional diagnostics cannot expire terminal ownership
- **WHEN** an original terminal reason requires bounded closing while storage and the current unexpired lease are available
- **THEN** new provider admission SHALL cease and known admitted accounting/tail/gaps plus fenced terminal status SHALL take priority over optional full summaries, with valid ownership maintained only through the original bounded drain
- **AND** unavailable diagnostics or a secondary closing failure SHALL remain distinct from the original cause without a fence bypass, fabricated committed summary or renewed window.

#### Scenario: Natural configured-window end persists already-admitted facts
- **WHEN** the original configured6h end arrives with already-admitted unflushed counters or notification tail and a valid current holder
- **THEN** bounded cleanup SHALL retain those known facts and commit the original time-limit terminal status through its valid fence without a post-end provider call or claim-based renewal of capture admission
- **AND** cleanup SHALL NOT discard admitted request/RPC facts merely because normal admission has ended or treat unfinished cleanup as a successful6h result.

#### Scenario: Storage or fence cannot complete closing
- **WHEN** the drain limit, database failure or lost/stolen/expired lease prevents terminal persistence
- **THEN** the collector SHALL stop owned admission/transport/work where bounded mechanisms permit and expose unfinished durable state and an explicit unknown tail under retained reservation
- **AND** it SHALL NOT reacquire expired ownership, rewrite historical evidence, publish healthy completion or automatically restart.

### Requirement: Compact verified recovery provenance and atomic bounded admitted-tail batches
New recovery provenance SHALL retain each exact full history response once in existing durable envelopes and reference its exact row through versioned page digest/index/exact-row digest/identity/slot/received-time evidence. References SHALL be verified before dependent pending/retry work, preserving legacy readback, finalized raw conflict semantics and explicit gaps. Legacy policies SHALL retain actual getTransaction acquisition; only the explicitly versioned trusted-reuse policy defined below SHALL permit already retained finalized raw/full-history evidence instead. Recovery and PENDING selection SHALL use bounded payload-free identity projections rather than repeated full-session payload reads. Closing MAY batch already-admitted tail under existing queue/byte/time/fence limits, but SHALL retain atomic immutable equality/conflict/accounting semantics and expose unfinished remainder without fabricated STOPPED or complete coverage.

#### Scenario: History page supplies multiple recovery identities
- **WHEN** a bounded full history page supplies identities before the exact known boundary
- **THEN** its exact envelope SHALL be retained before verified compact row references, without copying the full page into each disposition or reserializing row digest input
- **AND** raw identity/time/conflict checks, strict errors, recovery deadline and exact episode-gap closure SHALL remain unchanged, with individual getTransaction acquisition retained for legacy policies or the explicit bounded fallback below.

#### Scenario: Reference evidence is missing or inconsistent
- **WHEN** page kind/session/digest, row index/exact digest/identity/slot or received time cannot be verified for a new reference
- **THEN** dependent work SHALL fail explicitly without fallback provider admission, fabricated raw evidence or successful recovery
- **AND** legacy opaque notifications and immutable historical sessions SHALL remain readable unchanged.

#### Scenario: Closing batch fails or crosses its valid fence or deadline
- **WHEN** an admitted-tail batch encounters conflict, invalid identity, resource failure, mid-batch storage error, expired/stolen ownership or original drain expiry
- **THEN** that owning transaction SHALL roll back its partial tail/counter/gap/status effects and preserve the original cause, conservative reservation and explicit unfinished remainder
- **AND** equal retry/state ambiguity/first times SHALL remain unchanged, with no paid drain operation, timeout/cap increase or false terminal completion.

#### Scenario: Local synthetic configured-window soak precedes another paid attempt
- **WHEN** the repaired source is evaluated through free fake WS/RPC, real PostgreSQL and controlled configured21600s with recurring disconnects and realistic delayed page/tail work
- **THEN** it SHALL demonstrate continued bounded recovery/capture and natural-end accounting/fenced closing under original limits, with verified provenance and explicit incomplete STATE/null coverage
- **AND** synthetic success SHALL NOT substitute for independent review, a new Main gate or actual live6h acceptance.

### Requirement: Versioned trusted pre-fetch raw reuse
Only prospective `capture-rpc-reuse-v6` sessions SHALL permit avoiding an individual finalized fetch by verified existing committed raw evidence. Trust SHALL require a successful capture-admission disposition and exact configured chain/provider/signature/slot, recomputed raw digest, complete supported raw, consistent on-chain success/failure and valid original clocks. Raw identity alone SHALL NOT prove capture finality. Lookup and fenced finish SHALL stay bounded under original writer/operation/lease/resource limits. Incoming notification/reference evidence and per-pool membership SHALL remain durable; reuse SHALL preserve exact existing raw, first source/observation clocks and explicit gaps. Older policies SHALL retain their previous acquisition/readback behavior.

#### Scenario: Same transaction appears again or under another pool
- **WHEN** a new same-session notification matches verified committed CAPTURED or FAILED_TRANSACTION raw, including a successful disposition under another configured pool
- **THEN** the collector SHALL retain the notification and complete that pool's fenced raw-linked disposition without another transaction fetch or duplicate raw row
- **AND** failed raw, original bytes/times, counters and exact successful boundary semantics SHALL remain preserved.

#### Scenario: Identity is present without admissible proof
- **WHEN** a matching raw key has no successful capture-finality proof or its disposition is pending, unavailable, provisional or unsupported
- **THEN** its presence SHALL NOT produce completed/recovered coverage and the original bounded finalized-fetch path SHALL remain applicable
- **AND** raw-before-checkpoint crash retry SHALL preserve immutable storage equality rather than assume finality from the key.

#### Scenario: Supposed reuse proof contradicts evidence
- **WHEN** successful proof has a wrong slot/provider/raw digest, conflicting content/status, corrupted provenance or invalid clocks
- **THEN** the collector SHALL fail explicitly without reusing or overwriting raw or closing a gap
- **AND** a paid refetch SHALL NOT hide the integrity contradiction.

### Requirement: Complete finalized history-row source acquisition
Prospective trusted-reuse sessions SHALL permit a complete supported legacy/v0/v1 row from an already obtained verified finalized/json/full history response to supply raw transaction evidence without an individual fetch. Source proof SHALL bind immutable source-policy version, exact page/row digests and index, configured session/provider/pool/cursor, identity/slot and actual trusted first receive time. The original row span SHALL remain exact source text, not fabricated getTransaction serialization. Required complete transaction/message/meta inputs SHALL be present with version-applicable structure and no invented empty values. Admission SHALL retain a versioned source receipt separately from raw storage identity. This permission SHALL NOT add proactive LIVE history queries, remove failed raw, alter WS/state subscriptions or weaken finite recovery continuity/deadline rules.

#### Scenario: Complete history row has no existing raw
- **WHEN** full verified finalized history supplies a supported complete row with consistent identity and meta success/error
- **THEN** the exact original row SHALL become immutable raw with page-receive time and actual admission/ingestion times, with replayable source receipt and CAPTURED or FAILED_TRANSACTION disposition
- **AND** no individual getTransaction fetch or economic-decoding success claim SHALL be required for that row.

#### Scenario: History agrees semantically with an existing exact raw
- **WHEN** full duplicate-key-rejected history JSON and all immutable slot/source facts equal existing trusted raw but source serialization differs
- **THEN** the new page/row SHALL remain additional provenance without changing or submitting a fabricated byte-equal retry of the original raw
- **AND** existing exact payload/digest/parser/first times SHALL remain unchanged; differing substantive content SHALL be an explicit conflict.

#### Scenario: Full evidence is unavailable or invalid
- **WHEN** a verified row lacks required complete fields, has unsupported source shape or cannot establish finalized admission
- **THEN** the reuse rejection SHALL remain explicit and only the existing bounded finalized transaction-fetch policy MAY independently obtain admissible raw
- **AND** tampered/missing reference evidence, identity contradiction or immutable-content conflict SHALL fail without fallback, fabricated fields or recovered-gap claims.

### Requirement: Conservative cumulative fractional-CU accounting compatibility
New trusted-reuse policy SHALL express subscription accounting in integer hundredths of CU by rounding the exact cumulative bytes-times-rate-times100 upward, then adding exact RPC hundredths. Reservation, admission, observed overruns and closing SHALL use this same cumulative rule; increments SHALL be cumulative-rounded-total differences, not independently rounded fragments. Zero bytes SHALL cost zero and arithmetic overflow SHALL reject further admission. Exact unrounded USD comparison SHALL remain controlling. Legacy0.04 calculations, recorded authority, reserved totals and all original receipts SHALL remain unchanged. A new rate SHALL NOT replace an existing period authority, refund reservations, create a reset through renaming, or authorize runtime activation.

#### Scenario: Fractional rate and split deliveries
- **WHEN** a synthetic prospective authority uses0.0002CU/byte
- **THEN**1,50,51 and7,778,076 cumulative bytes SHALL respectively account for1,1,2 and155,562 hundredths of CU
- **AND** splitting the same received total across callbacks SHALL preserve its total charge and exact cap/overrun behavior.

#### Scenario: Existing October authority cannot be silently repriced
- **WHEN** a prospective session changes a rate in an already reserved account period or would exceed its effective remaining exact money/CU ceiling
- **THEN** admission SHALL reject before provider work without modifying authority or monotonic old reservations
- **AND** a valid new same-rate policy session MAY use remaining authority while preserving legacy results and no automatic launch or refund.
