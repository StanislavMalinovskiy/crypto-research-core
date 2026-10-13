## Purpose

Provide a manually invoked local-only screen for committed capture facts and bounded retained native-SOL diagnostics without exposing raw evidence, changing collection or admitting a strategy.

## ADDED Requirements

### Requirement: Default-disabled local-only invocation

The system SHALL provide HTML/script and JSON routes only when the operator-screen flag is explicitly enabled. Enabled startup SHALL require exact loopback127.0.0.1 binding, no forwarded-header trust and both capture flags false; unsafe settings SHALL fail closed. Every screen route SHALL reject non-loopback peers/local addresses, non-local Host, forwarded metadata and cross-origin requests before data reads. JSON requests SHALL require the explicit local-manual header; no CORS/preflight permission, public authentication claim or active collector control SHALL be introduced. Responses SHALL prevent caching, framing and executable injected content.

#### Scenario: Disabled and hostile requests
- **WHEN** the flag is absent/false, or an enabled local route receives a hostile Host/Origin/forwarded header or non-loopback peer
- **THEN** disabled resources/routes SHALL be absent404 and hostile requests SHALL reject403
- **AND** no capture/diagnostic API read or collection action SHALL occur.

#### Scenario: Unsafe enabled startup
- **WHEN** operator-screen is enabled with either capture flag true, wildcard/non-loopback binding or forwarded-header trust
- **THEN** enabled screen startup SHALL reject safely
- **AND** the setting SHALL not silently widen network exposure or start a collector.

### Requirement: Manual explicit independent panels

The screen SHALL offer independent manually submitted session-monitor and bodyless diagnostic requests. Diagnostic inputs SHALL explicitly name session, arbitrary half-open received subinterval, aligned event scope/end and exact chain/pool/mints; no session/window/pool auto-selection, caller K/D or version override SHALL occur. Unknown/repeated/malformed fields, query over4096 UTF-8 bytes or field over256bytes SHALL reject before APIs. Monitor SHALL call its existing public API once; diagnostic SHALL call the retained bridge once without additional reads/retry. Panels SHALL retain independent observation times, explicit absence/failure and INDEPENDENT_PANELS, not imply an atomic combined freeze. One diagnostic SHALL be in flight at most; excess requests SHALL receive429 BUSY without queuing. No load-time requests, automatic poll/scheduler/write/start/stop/retry SHALL occur.

#### Scenario: One explicit received subinterval
- **WHEN** an operator submits a valid diagnostic request for one selected received subinterval
- **THEN** one existing retained bridge result SHALL be projected with normalized request, R/D/K=D/E and original fingerprints
- **AND** the separately refreshed monitor SHALL retain its own observation instant rather than imply a shared snapshot or24h capacity.

#### Scenario: Invalid input and concurrent submit
- **WHEN** input is malformed/repeated, contains a body, or another diagnostic is already running
- **THEN** it SHALL reject400 or429 as applicable without another API invocation
- **AND** the in-flight permit SHALL release on success/failure with no automatic retry.

### Requirement: Payload-free exact trustworthy presentation

HTTP output SHALL whitelist committed monitor state/lease/window/counters/positions, durable disposition and unresolved gap counts/details/truncation, plus retained diagnostic scope/clocks/versions/fingerprints/counts/qualifiers/reconciliation/candles and separate pure/effective statuses/reasons/limitations. It SHALL omit raw/envelope/proof bodies, configuration text, credentials/endpoints/lease-holder, exception objects and individual source-proof exports. All integral/large identity/raw-unit/financial/rational values SHALL serialize as exact canonical strings; instants SHALL use UTC text, optionals null, booleans native. Frontend SHALL render values with textContent/text nodes, without binary-number conversion, financial recomputation or injected markup. Complete JSON SHALL be at most8,000,000 UTF-8 bytes, each projected string at most4096bytes and each fixed resource at most65,536bytes; excess SHALL reject rather than clip required output. Existing monitor first100-detail truncation SHALL remain explicitly labeled and not reduce grouped totals.

#### Scenario: Exact large quantities and malicious text
- **WHEN** source values include9007199254740993, larger raw units, exact rational prices and HTML/script-like text
- **THEN** JSON SHALL retain exact decimal strings and rational numerator/denominator strings
- **AND** browser presentation SHALL treat text as literal data with no script execution or changed diagnostic arithmetic.

#### Scenario: Secret exclusion and output bound
- **WHEN** underlying source contains raw/configuration/proof data or projected output exceeds its byte bound
- **THEN** forbidden data SHALL never enter the response and oversized output SHALL reject422 without partial success
- **AND** no additional data read or field truncation SHALL be used to manufacture a successful response.

### Requirement: Visible uncertainty and nonadmission

Recorded RUNNING/lease validity SHALL not be labeled process/transport health; memory queue occupancy SHALL be UNKNOWN and durable PENDING SHALL be labeled separately. Coverage/loss/billing SHALL preserve UNKNOWN/UNVERIFIED; reservations and measured RPC units SHALL not become invoice/month-fit, and absent qualified tariff SHALL keep model cost UNKNOWN. Diagnostic effective INSUFFICIENT SHALL remain primary when pure TRIGGER coexists with pending/gaps; source statuses and missing-bar evidence SHALL not become clean zero/NO_TRIGGER. Native SOL pool-net candles SHALL be DIAGNOSTIC_ONLY_NOT_ADMITTED, not USD/executable price, accepted ENTRY/risk/evaluation/PnL. Typed bounded-read failures SHALL preserve observed signature/byte EXACT/AT_LEAST/UNKNOWN values; unavailable quantities SHALL be absent UNKNOWN, never guessed zero. Safe static errors SHALL expose no exception text, hide no failure and trigger no fallback/full-scope count/retry.

#### Scenario: Incomplete capture and limited read
- **WHEN** a running retained prefix has pending/gaps, or a bounded read fails after a4097-signature probe or before byte accounting
- **THEN** capture incompleteness and effective INSUFFICIENT or explicit safe read failure SHALL be visible
- **AND**4097 SHALL remain AT_LEAST, unobserved bytes UNKNOWN and process/invoice/strategy acceptance unproved.

### Requirement: Read-only compatibility and separate deployment

The adapter SHALL reuse existing public module APIs without SQL, controller-owned transaction, migration/dependency, provider call or durable mutation. Existing fixed-v4 publication, capture flags/defaults/authority/all resource holds/receipts and accepted signal/evaluation behavior SHALL remain unchanged. The qualified active collector SHALL not be upgraded/restarted during its first24h. This implementation SHALL not install/enable a service or perform managed/provider calls. Any later separately authorized standalone screen process SHALL require both capture flags false, read-only already-V18/no-pending-migration database and proven finite separate resource/port headroom; otherwise only an authorized retained local copy SHALL be used.

#### Scenario: Repeated isolated inspection
- **WHEN** qualified screen requests execute repeatedly against populated isolated V18 PostgreSQL with capture disabled
- **THEN** existing raw/domain rows, clocks, holds, receipts and fixed-v4 readback SHALL remain exactly unchanged
- **AND** launcher/coordinator/provider/accepted-signal/evaluation effects SHALL remain absent.
