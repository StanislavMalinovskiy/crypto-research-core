## Purpose

Present one bounded operator-facing JSON report and brief human summary of independently requested retained capture facts and exact saved-batch native diagnostics, without claiming common provenance, historical coherence or admission.

## ADDED Requirements

### Requirement: Explicit independent diagnostic panels

The system SHALL synchronously accept exactly one capture-session identifier and one exact batch/window/cutoff/interval metrics request. It SHALL obtain only the existing capture monitor and existing integrity-checked pinned metrics result, with no extra raw/batch read, re-decode, automatic selection or merge. Malformed input SHALL reject before either read. Each successful report SHALL identify `pumpswap-operator-report-v1`, the requested session and normalized metrics request. Monitor and metrics panels SHALL have distinct `MONITOR_PRESENT`/`MONITOR_ABSENT` and `METRICS_PRESENT`/`METRICS_ABSENT` statuses; absent panels SHALL be null, not fabricated zero-valued facts. A read failure, unsupported/corrupt source or formatting failure SHALL fail the entire report explicitly without successful partial output or retry.

#### Scenario: Both explicit inputs exist
- **WHEN** a valid request names a retained session and supported immutable metrics batch
- **THEN** the report SHALL include those two exact panels and existing decoder statuses/reasons from the metrics panel
- **AND** it SHALL perform no third raw read, provider call or durable mutation.

#### Scenario: Independent absence
- **WHEN** either named component or both components are unknown
- **THEN** each absent component SHALL retain its distinct absence status and null panel while an existing other component remains inspectable
- **AND** no zero counts, replacement key/session or overall health/completion conclusion SHALL be fabricated.

#### Scenario: Invalid request or failed read
- **WHEN** input is malformed or either component read fails instead of returning absence
- **THEN** invalid input SHALL cause no component read, and a read failure SHALL reject the report with a safe explicit failure
- **AND** no partial report, provider fallback or unbounded retry SHALL occur.

### Requirement: No inferred association or historical coherence

Every report SHALL state `association=REQUESTED_TOGETHER_UNVERIFIED` and `consistency=INDEPENDENT_PANELS`, including when identifiers, times, pools or counts appear to agree. The monitor SHALL retain its original observation instant, recorded state and independently calculated lease/window facts. Metrics SHALL retain its normalized observation window/cutoff, immutable source versions/revisions and fingerprint. The system SHALL NOT infer capture-session membership, whole-session derivation completion, common database snapshot, coverage denominator or historical decision availability from these panels. No report-level authoritative freeze time or dataset fingerprint SHALL be invented. Equal component values and equal request SHALL produce identical renderings; later monitor observation/state changes SHALL be permitted without changing the same frozen metrics meaning or fingerprint.

#### Scenario: Unrelated session and batch
- **WHEN** a caller pairs an existing session with an existing batch from unrelated evidence
- **THEN** both panels SHALL remain separate with unverified association rather than claim lineage or reconcile their counts
- **AND** apparent matching time/pool/transaction counts SHALL not upgrade that association.

#### Scenario: Capture changes between panel reads
- **WHEN** a committed capture transition or observation clock advances between separately requested panels or successive reports
- **THEN** the monitor SHALL describe only its own coherent observation and metrics SHALL retain the exact frozen batch/request interpretation
- **AND** the report SHALL not claim global atomic or historical coherence, stable whole-report identity across time, or historical process activity.

### Requirement: Lossless bounded JSON and brief summary

The report SHALL return JSON plus a human summary without writing a file, logging the report or activating any trigger. JSON SHALL include the complete existing safe monitor/metrics records for present panels, distinct statuses, request, version and association/consistency labels. Format v1 SHALL encode all integral fields as canonical decimal strings, including financial quantities, fees and rational numerator/denominator; booleans and nulls SHALL retain their types, and instants SHALL use canonical UTC text. Object fields SHALL have deterministic unsigned UTF-8 key ordering, arrays SHALL retain the source contract's order, and arbitrary strings SHALL be JSON-escaped. No binary-floating or rounded decimal rendering SHALL replace exact native financial evidence.

JSON SHALL contain at most64,000,000 UTF-8 bytes and the summary at most16,384 UTF-8 bytes. The existing source cardinality/read deadlines and metrics bounds SHALL remain unchanged. Exceeding either rendered bound SHALL reject without truncation, dropped references or successful partial report. The summary SHALL identify explicit requested inputs, panel absence/presence, association/consistency, monitor observation/state/reason/lease/window/counters/unresolved totals/detail truncation/inconsistency, and metrics fingerprint/window/interval/status reconciliation/buy/sell/eligible/unpriced/populated-candle/pool counts and diagnostic limitations when their panel is present. It SHALL NOT reproduce every candle/reference or create new financial aggregates. Input-derived summary strings SHALL be escaped so controls cannot hide or inject summary lines.

#### Scenario: Large exact values and escaped text
- **WHEN** the inputs contain integers above binary-floating precision, rational endpoints, Unicode or quotes/control characters
- **THEN** JSON SHALL preserve the exact values as strings and safely escape text, and summary text SHALL retain truthful labels without control-line injection
- **AND** permuting equivalent map insertion order SHALL not change either rendering.

#### Scenario: Rendered output bound
- **WHEN** either JSON exceeds64,000,000 bytes or summary exceeds16,384 bytes after UTF-8 encoding and escaping
- **THEN** the system SHALL reject the entire report explicitly
- **AND** it SHALL not shorten required fields, references, limitations or source counts to create a successful report.

### Requirement: Existing unknowns and no expanded authority

The metrics summary SHALL label the panel `observation-time activity` and explicitly retain `DETERMINISTIC_NOT_ONCHAIN_CHRONOLOGY`, not market/price candles. JSON SHALL preserve slot, sourceTime, receivedAt and transaction/instruction position only where the existing public panel already supplies them, including absent optional values; it SHALL NOT invent missing fields or add reads to reconstruct chronology. Verified chain-time/slot/transaction-position market chronology remains a future prerequisite before any signal use of these diagnostics; this report SHALL NOT claim that prerequisite is satisfied.

Both renderings SHALL preserve monitor gap totals, first100 ordered details, truncation and inconsistent-state-recovery facts without inventing recovered states or complete continuity. Committed local measured counters and conservative reservations SHALL remain distinct from UNKNOWN actual billing; lease validity SHALL not imply process existence, healthy transport or completion. Metrics SHALL preserve all stored transaction statuses/reasons, immutable references and `HISTORICAL_DIAGNOSTIC`, `OBSERVED_BATCH_ONLY`, coverage INCOMPLETE, loss UNKNOWN, fees INCOMPLETE and signal readiness NOT_ESTABLISHED. Sparse missing buckets SHALL not mean zero trading. Native ratios/activity SHALL not become USD, liquidity, executable prices, risk, ranking, signals, outcomes or admission.

The operation SHALL expose no raw/envelope payload, authenticated endpoint, credential, configuration proof, exception object or lease-holder secret, and SHALL perform no provider/worker/startup/configuration access, database write, migration or capture action. Existing APIs, health/replay telemetry, expense authority and historical evidence SHALL remain unchanged. No live/paid/managed/deploy/pool/budget expansion, R1/holdout/research restart, push or archive authority SHALL follow.

#### Scenario: Recovered transaction receipt is later than chain evidence
- **WHEN** retained transaction references include sourceTime or slot and receivedAt records later catch-up receipt
- **THEN** the report SHALL retain the existing values and observation-time bucket/order interpretation unchanged, with the explicit non-chronology label
- **AND** it SHALL not relabel observation-time activity as verified market candles, synthesize unavailable clocks/positions or claim signal readiness.

#### Scenario: Incomplete capture and unknown economics
- **WHEN** the monitor has unknown activity/billing, contradictory or truncated gap details and the metrics batch has unsupported/failed/partial evidence
- **THEN** JSON SHALL retain the exact source facts and summary SHALL distinguish complete totals from bounded details and unknowns
- **AND** neither rendering SHALL introduce PASS, complete coverage, zero expenses, risk ALLOW or signal readiness.

#### Scenario: Repeated inspection has no effects
- **WHEN** the report is requested repeatedly with capture disabled and no provider credentials
- **THEN** existing source rows and legacy API behavior SHALL remain unchanged
- **AND** inspection SHALL not start collection, repair gaps, invoke a decoder/publisher, access managed settings or introduce an endpoint/CLI/background loop.
