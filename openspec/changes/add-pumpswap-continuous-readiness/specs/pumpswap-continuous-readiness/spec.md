## Purpose

Defines a default-disabled finite deployment workload that preserves cumulative capture authority and resumes bounded raw-to-operation derivation without claiming production or research admission.

## ADDED Requirements

### Requirement: Explicit disabled-default finite workload

Automatic orchestration SHALL require an explicit enabled setting and a complete immutable deployment configuration containing a stable deployment/allocation identity, existing provider/account period and pool scope, cumulative resource allowances, a global UTC end, finite segment/count limits, and finite derivation work/wait limits. Disabled startup SHALL create no workload, provider operation or readiness metadata read/write; ordinary datasource/Flyway foundation startup SHALL remain unchanged. Enabled configuration SHALL NOT itself constitute new owner expense or activation authority. Missing, contradictory or changed frozen authority SHALL fail before provider work, without secret disclosure.

#### Scenario: Ordinary disabled startup
- **WHEN** the application starts without explicitly enabling the new workload
- **THEN** no readiness worker, provider call or readiness metadata access SHALL occur
- **AND** existing foundation startup and independently configured legacy/manual capture SHALL retain their contracts.

#### Scenario: Explicit enabled invalid configuration
- **WHEN** enabled orchestration lacks a finite end, cumulative allowance, stable allocation or required existing launch evidence
- **THEN** it SHALL fail safely before provider work
- **AND** no default provider, invented usage baseline or new expense authority SHALL be substituted.

### Requirement: Durable conservative cumulative admission

Before any segment provider operation, one owned atomic transition SHALL reserve that segment's entire finite request, received/subscription/retained-byte, RPC/CU/cost and configured-duration allowances against the same durable deployment allocation and existing account-period ledger. Cumulative deployment allowances SHALL be independently enforced across restart and segments. Conservative reservations SHALL remain distinct from measured committed use, unknown unflushed use and UNKNOWN actual billing; they SHALL NOT be refunded after crash, abort or shorter completion. Within an enrolled allocation, manual/new-name/new-session starts SHALL use the same admission guard and SHALL NOT bypass exhausted authority. A changed deployment name, configuration, authority label or reported period reset alone SHALL NOT create a new allocation or reopen its terminal stop; only the explicit immutable monthly or manual same-period receipts below SHALL permit their bounded distinct child, never reopen the old allocation.

#### Scenario: Crash before local accounting flush
- **WHEN** a segment admits provider work and then crashes before its local counters are committed
- **THEN** its original whole-segment reservations and committed counters SHALL survive restart without becoming zero
- **AND** retry of that exact segment SHALL not reserve twice or obtain a replacement allowance.

#### Scenario: Concurrent or renamed admission
- **WHEN** independent instances or a manual start attempt to admit segments under the same enrolled provider allocation, including changed session/deployment/authority labels
- **THEN** database fencing and uniqueness SHALL admit at most the authorized owned segment and enforce unchanged cumulative reservations
- **AND** conflicts or insufficient capacity SHALL fail without partial reservation/session linkage or provider work.

#### Scenario: Manual start races enrollment
- **WHEN** a manual start and enrollment target the same validated provider allocation concurrently
- **THEN** both SHALL acquire the same bounded transaction-scoped provider admission lock before checking the binding or taking admission row locks
- **AND** the binding check SHALL use a fresh READ_COMMITTED statement after lock acquisition, so enrollment-first rejects manual bypass and manual-first enrollment accounts for committed prior authority without resetting usage
- **AND** lock timeout SHALL fail without retry, partial reservation/session linkage or provider work.

#### Scenario: Capture start inside caller transaction
- **WHEN** public collector start or internal enrolled start is invoked with any actual caller transaction, including READ_COMMITTED or REPEATABLE_READ
- **THEN** it SHALL reject with `CAPTURE_REQUIRES_NO_TRANSACTION` before ledger, worker or provider work
- **AND** caller commit/rollback semantics SHALL remain intact without suspension or independent recommit; this prospectively replaces former ambient-start behavior, while ordinary nonambient V13 legacy capture remains unchanged
- **AND** direct manual ledger admission under non-READ_COMMITTED isolation SHALL reject before reservation/session/provider work rather than use a stale binding snapshot.

#### Scenario: Terminal deployment budget
- **WHEN** any cumulative resource allowance, global end or finite segment count is exhausted
- **THEN** the deployment SHALL retain an explicit durable terminal reason and stop admitting provider work
- **AND** restart, segment replacement or a new name SHALL NOT reopen it; renewed authority SHALL require a separate owner decision, not an automatic reset or team-created reopen API.

### Requirement: Fenced finite segment handoff and explicit gaps

One deployment SHALL own at most one capture segment at a time through a committed expiring PostgreSQL lease. No automatic successor SHALL start until the preceding coordinator and writer have actually exited and its admitted tail/terminal facts are sealed against later writes. Only a verified expected natural segment end strictly before the deployment's global end, within its remaining count/resources, and after successful bounded derivation SHALL permit an already-authorized automatic successor within that deployment. Owner stop, security/protocol/unknown/provider terminal failure, writer/fence/overflow/recovery failure, resource exhaustion and incomplete closing SHALL remain terminal for that allocation; a distinct explicit manual same-period child SHALL require the separate receipt below and actual owned-process exit, not reinterpret failure as natural completion. This is a narrow prospective exception for the new enrolled wrapper, not reopening a terminal collector session or retroactive legacy permission. Restart and every nonoverlapping segment boundary SHALL retain explicit TX and STATE gaps, preserving unknown boundaries and unproved continuity. Existing finite within-segment recovery SHALL NOT turn current state into recovered historical state.

#### Scenario: Expected natural segment boundary
- **WHEN** a segment reaches its original configured end and its valid writer closes/seals successfully while the deployment remains eligible
- **THEN** the wrapper SHALL retain that terminal session and only then admit a distinct reserved successor
- **AND** the boundary TX/STATE intervals SHALL remain explicitly incomplete rather than claim continuous coverage.

#### Scenario: Terminal failure or global expiry
- **WHEN** a segment ends for another reason, cannot prove writer exit/sealing, or reaches the deployment global end or cumulative time limit
- **THEN** no successor SHALL be admitted automatically
- **AND** the original cause, unfinished evidence and conservative reservation SHALL remain observable.

#### Scenario: Resume unfinished segment under valid authority
- **WHEN** another instance acquires an expired deployment/segment lease for the exact unfinished session while its original segment/global end and resources still permit work
- **THEN** resumed work SHALL retain original configuration, clocks, reservations and explicit restart TX/STATE gaps under a new valid fence
- **AND** stale holders SHALL NOT admit a new page, perform provider/capture writes, reserve authority, advance cursor/receipts, seal, clear gaps, complete work or admit a successor; an expired window or unsealed past-end session SHALL remain explicitly terminal/incomplete rather than invent natural completion.

### Requirement: Bounded post-seal fixed-version derivation

After sealing, the workload SHALL deterministically page every distinct retained raw-linked transaction source in that segment through the fixed verified decoder and existing immutable operation publication contract, outside any capture/database transaction. Source binding SHALL be derived from stored session/disposition and exact V5 raw identity, payload/digest/slot and original source/received/admitted/ingested clocks; derivation time SHALL NOT replace observation time. Every failed, unsupported, non-operation, partial or invalid decoder disposition SHALL remain persisted, including zero operations. No arbitrary decoded-result import, provider refetch, raw repair, newest-parser selection or normalized market admission SHALL occur. There is no within-segment latency guarantee.

#### Scenario: Crash between publication and cursor advance
- **WHEN** a bounded page commits derivations but the process stops before its durable cursor/completion transition
- **THEN** resume SHALL reconstruct the same source page and reuse idempotent publication without duplicate rows, changed first times or altered original clocks
- **AND** the cursor SHALL advance only after exact stored-batch membership/version verification.

#### Scenario: Lease expires during an already-admitted page
- **WHEN** a precise sealed source page was admitted under the current holder/generation/unexpired lease and original finite derivation allowance, but that holder loses its lease during decode or publication
- **THEN** it MAY finish only that same deterministic immutable fixed-v4 publication as an uncredited orphan batch under unchanged raw/full-content verification
- **AND** it SHALL NOT admit another page or change provider/capture work, reservation, cursor/receipts, seal, gaps, completion or successor authority
- **AND** the current holder SHALL own admission and idempotent republication of the identical page before a fenced progress transition, preserving exact membership, original clocks, first persisted times and batch key; the orphan batch alone SHALL NOT establish progress or coverage.

#### Scenario: Other parser revision is not current progress
- **WHEN** a retained derivation or batch has a parser, endpoint-proof or storage revision different from `pumpswap-operations-v4`, `may18-oct07-endpoint-proof-v4` or `pumpswap-storage-v1`
- **THEN** it SHALL NOT satisfy this workload's receipt, cursor or completion reconciliation
- **AND** the current fixed-v4 path SHALL retain separate immutable revision identities without modifying older content or accepting arbitrary injected results.

#### Scenario: Late and duplicate raw sources
- **WHEN** raw/checkpoint commits arrive in another order before sealing or one signature occurs under multiple configured pools
- **THEN** deterministic sealed-source selection SHALL retain each distinct session/chain/provider/signature once with a stable complete source reference
- **AND** final raw-linked-source and persisted-disposition reconciliation SHALL detect omission rather than skip a later lower-slot source or lose failed input.

#### Scenario: Derivation cannot complete within allowance
- **WHEN** a page exceeds existing decoder bounds, a stored source is contradictory, publication fails, or configured finite page/deadline allowance is insufficient
- **THEN** the workload SHALL retain an explicit failure/backlog reason, committed progress and remaining source count without advancing over missing evidence
- **AND** it SHALL not admit a successor or report complete derivation from counts alone.

### Requirement: Bounded observation and local-only readiness proof

Existing exact session monitor behavior SHALL remain unchanged, including independent coherent read-only snapshot, at most100 ordered gap details, truthful full counts, lease-as-fact, secret/payload exclusion and explicit UNKNOWN/UNVERIFIED fields. Workload observations SHALL separately expose secret-free deployment terminal/progress/reservation facts without claiming provider health, invoice accuracy, zero loss, full F3 or admission. All workload waits, polling, page counts and resource work SHALL be finite and cancellation SHALL propagate. Shutdown SHALL stop provider admission and retain or explicitly fail already-admitted work under valid fences without releasing ownership before actual worker exit.

#### Scenario: Local readiness verification
- **WHEN** accelerated synthetic loopback transport and isolated PostgreSQL tests plus one several-minute real-wall-clock segment/restart test pass
- **THEN** the result SHALL establish only the tested local orchestration, restart, budget, provenance, derivation and shutdown contract
- **AND** it SHALL NOT authorize live/paid activation, managed migration, deployment, pool expansion, increased expense, R1/D1/P1 or research admission.

#### Scenario: Preserved monitor and legacy evidence
- **WHEN** existing session monitor, raw evidence, V14 revisions and legacy fingerprints are read after forward migration and readiness transitions
- **THEN** their recorded content and accepted semantics SHALL remain unchanged, apart from separately permitted new capture facts in new enrolled sessions
- **AND** old terminal receipts, gaps and unknown coverage/billing SHALL not be rewritten as healthy continuous operation.

### Requirement: Explicit prospective same-period authority amendment

An explicitly owner-authorized prospective amendment SHALL bind the exact retained period authority and new authority in an immutable versioned receipt before new provider admission. For the authorized October transition it SHALL preserve the original account period/end, opening and other-activity identity, USD/CU rate, original authority JSON, session/deployment configurations and every prior reservation/known-overage counter; only the effective ceiling MAY increase to at most $50 and the new WSS coefficient SHALL be 0.0002 CU/byte. Amendment admission SHALL be atomic under the existing provider and period locks, reject active workloads/expired periods/conflicting predecessors, and SHALL NOT refund, rename, reset or reprice historical evidence. Legacy configuration serialization/readback SHALL remain unchanged.

#### Scenario: Authorized October transition
- **WHEN** the exact authorized amendment is applied to the inactive matching retained October period
- **THEN** its receipt and effective ceiling SHALL commit together while original authority, counters and historical evidence remain unchanged
- **AND** through the reviewed V16-aware admission/store path new reservations SHALL match the exact amended authority; that path SHALL reject a fresh former-authority or renamed-period reservation.

#### Scenario: Qualified binary activation and disabled rollback
- **WHEN** an amended period is activated or its application binary is rolled back
- **THEN** enabled capture SHALL use only the reviewed V16-aware binary after old owned capture processes/workers have actually stopped; rollback to a pre-V16 binary SHALL leave capture disabled
- **AND** pre-V16 or mixed-version enabled admission SHALL remain unsupported and forbidden; preserving original authority JSON and increasing the effective ceiling SHALL NOT be represented as a database-enforced rejection of legacy or arbitrary privileged SQL.

#### Scenario: Equal repeat and conflict
- **WHEN** the same immutable amendment is repeated or a different predecessor/content is supplied
- **THEN** equal repeat SHALL produce no additional reservation/receipt and conflicting input SHALL fail atomically before provider work
- **AND** concurrent reservation/amendment, arithmetic overflow, active workload or expired period SHALL NOT cause partial authority or lost counters.

#### Scenario: Expired unleased legacy session does not fabricate closure
- **WHEN** first amendment admission examines retained RUNNING sessions under the existing provider advisory lock, period row lock and deterministic session row locks
- **THEN** it SHALL use one PostgreSQL clock reading after those locks, inside the same transaction, to classify the rows; only an unlinked legacy manual row with both original `ends_at` and `locked_until` less than or equal to that reading MAY cease blocking the amendment
- **AND** a future original end, unexpired lease, any continuous-segment linkage or provider RUNNING deployment SHALL still reject first amendment admission atomically
- **AND** ignored legacy rows, RUNNING state, configurations, raw, receipts and reservations/overage SHALL remain unchanged; their closing SHALL remain UNKNOWN, not successful retirement, recovery or reopened work.

#### Scenario: Forward V16 preserves historical evidence
- **WHEN** populated V9/V13/V14/V15 evidence is migrated forward to V16 or complete-schema application tests start
- **THEN** successful migration counts/lists/table inventory SHALL reflect the one additive amendment table while every historical raw/authority/config/fingerprint/clock remains unchanged by migration
- **AND** original no-write, row-equality, foreign-key/index/collation and exact financial assertions SHALL remain active; updating schema setup SHALL NOT fabricate new expected economic evidence.

### Requirement: Authorized finite activation and observable budget stop

Activation under the newer explicit owner decision SHALL still require finite configured segments/global end/count/request/byte/CU/retention/derivation limits derived from the remaining account-period authority and observed workload. No calendar reset, new segment identity, restart or terminal stop SHALL grant new budget; a distinct period SHALL require the explicit versioned continuation below under an applicable owner grant. The default-off boundary, immutable old evidence, failed raw, fixed-version derivation, explicit gaps and UNKNOWN billing SHALL remain. Actual terminal budget/resource/time reasons SHALL produce a secret-free durable operator alert and truthful terminal observation after bounded cleanup. Local marker/journal notification SHALL NOT be represented as externally delivered notification without delivery evidence.

#### Scenario: Budget stop while application remains healthy
- **WHEN** capture stops at a cumulative authority/resource/time limit but the Spring process remains alive
- **THEN** its durable reason and bounded resource summary SHALL be observable through the operator alert/terminal path independently of readiness UP
- **AND** no automatic retry, successor, refund or new month allocation SHALL occur; failure to read final state SHALL remain explicit UNKNOWN.

#### Scenario: Twenty-four-hour reconciliation or early terminal result
- **WHEN** authorized activation reaches24h or stops earlier
- **THEN** the retained report SHALL compare timestamped dashboard observations and local committed counters for the same account period, distinguish estimates/reservations/other activity/invoice and record actual elapsed duration
- **AND** incomplete time, absent dashboard charge, rounded totals, unresolved gaps and unknown loss SHALL NOT be reported as zero usage, full coverage or verified monthly fit.

### Requirement: Explicit immutable new-period continuation

An explicitly configured `continuous-period-continuation-v1` request under an applicable owner monthly grant SHALL bind the exact predecessor deployment/configuration digest, exact successor configuration, next-period start/end and explicit timestamped source-labelled opening/other-activity baseline. Its owned atomic admission SHALL require the predecessor to be durably STOPPED, its old account period actually ended according to PostgreSQL time after locks, no live predecessor lease or active provider workload, and a genuinely distinct next period starting at the retained preceding period end. It SHALL create one immutable receipt, separate period and new deployment without changing any predecessor configuration, state/reason, raw, receipts, reservation or overage. Unknown baseline, dates, units or prior retention SHALL fail closed; zero SHALL be allowed only as an explicitly supplied known value. Current fixed decoder/provider/pool scope SHALL remain unchanged.

#### Scenario: Explicit successor after ended terminal period
- **WHEN** the qualified new binary receives a valid request after the exact predecessor period end under the standing monthly owner grant
- **THEN** provider admission lock and deterministic owned row locks SHALL serialize one linear receipt/period/enrollment before provider work; the new exact monthly CU authority SHALL be at most$50 with the approved rate and finite work limits
- **AND** old period CU/reservations/overage SHALL remain in that old period, while the successor SHALL use only its explicitly evidenced new-month opening/other activity and new monthly resource allocation
- **AND** its first segment SHALL retain explicit TX/STATE period-boundary gaps without declaring historical state or continuity recovered.

#### Scenario: No renamed or early successor bypass
- **WHEN** an unreceipted renamed deployment/period or same-period child, still-active predecessor, unended period for a monthly continuation, stale parent, altered pool scope or conflicting second successor attempts enrollment
- **THEN** it SHALL reject atomically before provider work without creating/resetting authority or changing the predecessor
- **AND** at most one provider deployment SHALL be RUNNING; multiple historical STOPPED deployments in the same provider/account period SHALL require the explicit immutable same-period replacement receipt below, never an unreceipted alias, and manual starts SHALL still use the enrolled-provider guard.

#### Scenario: Equal receipt and stale predecessor remain distinct
- **WHEN** the exact continuation request is repeated or the former predecessor attempts further admission/writes after continuation
- **THEN** equal repeat SHALL return the original receipt/allocation without a second opening, hold, configuration or segment; the unchanged ordinary fence/window contract SHALL govern any permitted resume of that same unfinished successor
- **AND** the predecessor SHALL remain terminal and stale predecessor admission/write SHALL fail without altering new or old evidence; equal replay SHALL not create automatic provider work.

#### Scenario: Global retained storage survives monthly money separation
- **WHEN** a new period is explicitly admitted
- **THEN** each old unique session's conservative retained allowance SHALL count once, with any positive known retained overrun also preserved; the successor's retained opening SHALL cover that whole provider-history total without adding the same carried amount twice
- **AND** the successor global retained ceiling SHALL equal the preceding ceiling rather than increase/reset with a new month or name; unavailable headroom for even its first finite segment SHALL reject before provider work
- **AND** unchanged old monthly CU and old history SHALL not be represented as a transfer or refund, and remaining physical DB/WAL capacity SHALL remain separately unverified until measured.

#### Scenario: Disabled startup and populated forward V17 compatibility
- **WHEN** continuation is absent/default-disabled or populated V16 data is migrated forward
- **THEN** disabled startup SHALL not read a continuation file/receipt or create provider work, and ordinary legacy V13/V15/V16 paths with no continuation SHALL retain their documented behavior/readback
- **AND** V17 SHALL preserve every old row/configuration/fingerprint/clock while adding the owning receipt and period-scoped/exclusive-running enrollment constraints; pre-V17 enabled mixed-binary admission SHALL remain forbidden/uncertified, not falsely described as physically rejected by all old SQL.

### Requirement: Explicit immutable manual same-period replacement
Only an explicit operator-invoked `continuous-same-period-replacement-v1` request under the retained owner/delegate grant SHALL enroll a new child after qualified v7 durable-inbox implementation. It SHALL bind exact STOPPED predecessor key/configuration digest/nonempty terminal reason, original same-period allocation root key/digest, successor Configuration, carried all-seven Resources/consumed-root-count proof and decision identity. Old deployment/session/configuration/reason/raw/PENDING/gaps/receipts/holds SHALL remain immutable; no automatic error/startup/calendar retry, reopening, refund/reset/repricing or original-window extension SHALL result. Same provider/sorted three pools/proofs/account period/end/effective authority/rates/original global ceilings/end SHALL be preserved; only v6->v7 or equal-v7 policy transition SHALL qualify. Fixed-v4 derivation SHALL remain unchanged.

#### Scenario: Eligible stopped same-period predecessor admits a distinct child
- **WHEN** a valid explicit request identifies a stopped predecessor with expired lease, no other active provider workload/session, still-open original period/global end and enough original root all-vector/count headroom for a whole first segment
- **THEN** one <=10s owning READ_COMMITTED transaction SHALL acquire provider advisory lock first, deterministic predecessor/root/period/session row locks and one postlock DB time, then atomically create exactly one immutable receipt/new allocation before provider work
- **AND** root means the original allocation of that same period, including one explicitly created by a monthly receipt; monthly money semantics SHALL remain unchanged and actual old process/writer exit SHALL remain separately required operational evidence
- **AND** the child SHALL retain original remaining count/global end, clipped segment time and finite per-segment allowances at most the unchanged remaining root caps (which MAY be lower without refunding old holds), with first-session TX/STATE boundary gaps linked only to known predecessor successful checkpoint, without closing old gaps or fabricating unknown continuity.

#### Scenario: Every conservative carried dimension is preserved once
- **WHEN** replacement opening and its first ordinary segment reservation are computed
- **THEN** requests/received/WSS/retained bytes/RPC hundredths/reserved CU hundredths/configured seconds SHALL each carry at least predecessor/root-held totals and required unique-session conservative totals; known positive overruns SHALL remain held
- **AND** CU SHALL retain same-period reserved+known-overage proof and retained bytes SHALL cover unique sessions across all provider periods, without summing ancestor deployment carry arrays or counting sessions/segments twice
- **AND** enrollment SHALL not add period allowance; first normal segment reservation SHALL consume unchanged remaining deployment/period authority exactly once, and unknown/overflow/reduced carry/exhausted vector or original count/end SHALL reject atomically.

#### Scenario: Replacement and monthly continuation compete for a parent
- **WHEN** either lineage kind attempts a child, including concurrent distinct replacements, replacement-versus-monthly continuation in either ordering or a stale ancestor
- **THEN** both admission paths SHALL check BOTH receipt tables under the same provider lock and admit at most one child of that parent across both kinds
- **AND** exact replay SHALL return its original receipt/allocation before current eligibility checks with no new hold/session/provider work; changed content SHALL conflict and same-period receipt transitions/traversal SHALL be independently bounded by original root maxSegments as well as once-counted segment consumption, including children stopped before first segment reservation.

#### Scenario: Forward V18 and ordinary alias guards
- **WHEN** populated V17 data migrates to V18 and owning enrollment starts
- **THEN** V18 SHALL remove ONLY provider-period deployment uniqueness, retain exclusive RUNNING-provider uniqueness and add the successor-PK/unique-predecessor/root/provider-period-FK replacement receipt with immutable request/digests/decision/original ceilings/end/count/carry proof
- **AND** old rows/configurations/raw/counters/receipts/constraints/readback SHALL remain exact; ordinary/manual/new-name/new-period/same-period unreceipted bypass SHALL still reject before provider work, including a replacement descended from a monthly receipt requiring exact receipt-aware authority lookup
- **AND** disabled rollback/pre-V18 or mixed-binary enabled admission SHALL remain forbidden/uncertified, not claimed physically blocked for arbitrary old or privileged SQL.

#### Scenario: Optional manual file does not authorize startup by itself
- **WHEN** same-period-replacement-file is absent/default-disabled, conflicts with amendment/continuation/manual mode, contains literal/decoded secrets, exceeds1,000,000UTF8bytes or differs from loaded successor configuration
- **THEN** default-disabled startup SHALL read/resolve nothing and invalid enabled input SHALL reject before enrollment/provider work with existing launcher compatibility preserved
- **AND** qualified file enrollment SHALL commit before coordinator start only after required review/Main gate/operational preflight; PLAN/gate/receipt replay SHALL NOT automatically enable or restart the stopped unit.

#### Scenario: Two manual children consume the original allocation over time
- **WHEN** two successive eligible same-period replacements are explicitly admitted with new identities
- **THEN** unique-session carry and consumed segment count SHALL remain once-counted and original global resources/duration/count/end SHALL not regenerate through names
- **AND** old940PENDING/terminal/holds SHALL remain untouched and absent invoice/dashboard/month-fit evidence SHALL stay UNKNOWN, not become a successful24h or future-period result.
