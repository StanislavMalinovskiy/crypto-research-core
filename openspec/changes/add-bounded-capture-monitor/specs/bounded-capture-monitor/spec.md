## Purpose

Provides compact read-only visibility of retained capture-session facts and explicit incomplete intervals without implying healthy transport or complete coverage.

## ADDED Requirements

### Requirement: Explicit coherent read-only session monitor

The system SHALL synchronously return an immutable monitor snapshot for an explicitly named retained session, or explicit absence for an unknown session. Recorded state/reason, configuration digest, configured start/end, lease expiry, durable counters, disposition counts, verified committed positions and unresolved interval counts/details SHALL describe one coherent committed database snapshot owned independently of any caller transaction. Caller-private uncommitted changes SHALL NOT enter this projection, and inspection SHALL NOT commit or roll back the caller's transaction. The response SHALL identify its UTC observation instant at microsecond precision and distinguish observation facts from research evidence. Inspection SHALL perform no durable mutation or provider operation and SHALL require no provider credentials.

#### Scenario: Inspect retained session without provider access
- **WHEN** a caller inspects a known session with provider collection disabled
- **THEN** one immutable coherent snapshot SHALL disclose the retained session facts
- **AND** no raw/envelope payload, authenticated endpoint, secret or lease-holder value SHALL be returned and no lease, counter, gap or evidence row SHALL change.

#### Scenario: Missing session
- **WHEN** the named session is absent
- **THEN** inspection SHALL return explicit absence
- **AND** it SHALL NOT create a session, fabricate zero-valued status or invoke capture.

#### Scenario: Concurrent capture transition
- **WHEN** capture commits a transition while monitor fields are being read
- **THEN** all returned durable fields SHALL come from the same committed database snapshot
- **AND** mixed pre-transition/post-transition counts, gaps or session state SHALL NOT be published.

#### Scenario: Ambient caller transaction
- **WHEN** inspection is invoked inside a caller's READ_COMMITTED transaction and another transaction commits a capture transition between monitor component reads
- **THEN** the response SHALL retain one independent coherent committed snapshot rather than inherit the caller's per-statement visibility
- **AND** caller-private uncommitted changes SHALL remain excluded, and the caller's original transaction and isolation SHALL resume without monitor-driven commit or rollback.

### Requirement: Lease and window facts are not activity or health

The monitor SHALL preserve recorded state/reason and expose lease validity and whether the configured window is open at its observation instant. Lease validity SHALL mean only observation instant strictly before retained lease expiry; window-open SHALL mean configured start less than or equal to observation instant strictly before configured end. A retained RUNNING row with an expired lease or closed window SHALL be explicitly distinguishable without rewriting it. Neither valid lease, RUNNING, an open window nor no known gaps SHALL imply actual process existence, healthy transport, freshness, complete continuity, zero loss, exact WS1011 cause or admission.

#### Scenario: Expired historical RUNNING row
- **WHEN** the retained state is RUNNING and observation time equals or exceeds its lease expiry
- **THEN** recorded RUNNING SHALL remain unchanged and lease-valid SHALL be false
- **AND** the snapshot SHALL identify process/transport activity as UNVERIFIED rather than claim live collection or recorded STOPPED.

#### Scenario: Configured window has ended
- **WHEN** observation time equals or exceeds configured end even if a lease remains valid during closing
- **THEN** window-open SHALL be false independently of lease validity and recorded state
- **AND** no automatic restart, terminal repair, new expense authority or healthy completion SHALL follow.

#### Scenario: Valid lease with incomplete or quiet capture
- **WHEN** a session has a valid lease and open window
- **THEN** those facts SHALL remain distinguishable from UNVERIFIED process/transport activity and coverage
- **AND** absence of recent transactions or known gaps SHALL NOT be fabricated as an outage, healthy freshness or complete coverage.

### Requirement: Bounded truthful incomplete-interval visibility

The monitor SHALL report complete counts of retained unresolved intervals grouped by pool, kind and status, separately from at most100 unresolved interval details ordered by ascending immutable gap identifier. A retained interval SHALL remain unresolved for this projection unless its kind is exactly TRANSACTION and its status is exactly RECOVERED_TRANSACTIONS; unknown statuses SHALL remain visible rather than disappear. A non-TRANSACTION interval with RECOVERED_TRANSACTIONS status SHALL remain unresolved and set an explicit inconsistent-gap-status flag regardless of detail truncation. Details SHALL preserve retained boundaries, reason and times including unknown/null boundaries. More than100 unresolved intervals SHALL set explicit detail truncation without reducing totals or claiming complete details. Reads SHALL use finite row/result/wait bounds and SHALL fail explicitly rather than publish partial or fabricated output when those bounds or database availability prevent a valid snapshot.

#### Scenario: Transaction recovery leaves state gaps
- **WHEN** a transaction gap is RECOVERED_TRANSACTIONS but a STATE or unrelated transaction gap remains unresolved
- **THEN** the unresolved totals/details SHALL retain the latter intervals and exclude only the recovered transaction interval
- **AND** neither recovery nor a current account observation SHALL imply historical state completeness.

#### Scenario: Detail limit and unknown status
- **WHEN** a session has101 unresolved intervals including an unknown status or null boundary
- **THEN** complete grouped counts SHALL include all101 and only the first100 ordered details SHALL be returned with truncation true
- **AND** the unknown status/boundary SHALL remain explicit rather than become recovered, zero or inferred continuity.

#### Scenario: Contradictory state recovery label
- **WHEN** a retained STATE interval is labelled RECOVERED_TRANSACTIONS, including an interval beyond the100-detail limit
- **THEN** its grouped unresolved count SHALL remain visible and inconsistent-gap-status SHALL be true
- **AND** inspection SHALL NOT silently exclude, repair or describe that historical state interval as recovered.

#### Scenario: Read failure
- **WHEN** a monitor query times out, its output exceeds a declared bound or the database is unavailable
- **THEN** inspection SHALL fail with a safe explicit diagnostic and no fabricated snapshot
- **AND** it SHALL NOT retry indefinitely, write repairs or alter the operational cause.

### Requirement: Existing contracts and unknowns remain unchanged

The monitor SHALL preserve existing capture status/replay, collector recovery, expense authority, pure decoding, operation revision reads and aggregate/liveness/readiness behavior. Its counters SHALL remain committed local counters/reservations, not invoice, unflushed callback counts or chain completeness. Actual billing, unreceived loss, transport/process activity and coverage SHALL remain explicitly UNKNOWN or UNVERIFIED where not established. It SHALL NOT select an operation parser revision, generate signals/outcomes, provide admission or expose a new runtime endpoint/automatic monitor loop.

#### Scenario: Monitoring does not expand acceptance
- **WHEN** retained session facts are inspected repeatedly
- **THEN** equal durable facts SHALL remain unchanged apart from observation-time-derived lease/window facts
- **AND** no provider call, operation re-decode, evidence repair, research admission, scheduler or health-group change SHALL occur.
