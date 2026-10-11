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

Before any segment provider operation, one owned atomic transition SHALL reserve that segment's entire finite request, received/subscription/retained-byte, RPC/CU/cost and configured-duration allowances against the same durable deployment allocation and existing account-period ledger. Cumulative deployment allowances SHALL be independently enforced across restart and segments. Conservative reservations SHALL remain distinct from measured committed use, unknown unflushed use and UNKNOWN actual billing; they SHALL NOT be refunded after crash, abort or shorter completion. Within an enrolled allocation, manual/new-name/new-session starts SHALL use the same admission guard and SHALL NOT bypass exhausted authority. A changed deployment name, configuration, authority label or reported period reset SHALL NOT create a new allocation or reopen its terminal stop.

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

One deployment SHALL own at most one capture segment at a time through a committed expiring PostgreSQL lease. No successor SHALL start until the preceding coordinator and writer have actually exited and its admitted tail/terminal facts are sealed against later writes. Only a verified expected natural segment end strictly before the deployment's global end, within its remaining count/resources, and after successful bounded derivation SHALL permit an already-authorized successor. Owner stop, security/protocol/unknown/provider terminal failure, writer/fence/overflow/recovery failure, resource exhaustion and incomplete closing SHALL remain terminal. This is a narrow prospective exception for the new enrolled wrapper, not reopening a terminal collector session or retroactive legacy permission. Restart and every nonoverlapping segment boundary SHALL retain explicit TX and STATE gaps, preserving unknown boundaries and unproved continuity. Existing finite within-segment recovery SHALL NOT turn current state into recovered historical state.

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
