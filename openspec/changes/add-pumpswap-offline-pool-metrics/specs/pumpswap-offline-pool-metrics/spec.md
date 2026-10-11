## Purpose

Expose bounded deterministic native execution candles and activity from one immutable retained PumpSwap operation batch for offline diagnostics, without executable pricing, continuity or signal-grade admission claims.

## ADDED Requirements

### Requirement: Exact bounded retained-batch diagnostic source

The system SHALL expose one synchronous read-only diagnostic taking an exact persisted batch key, UTC observation window, observation cutoff and interval of60,300 or3600 seconds. It SHALL resolve only that batch through the existing integrity-checked immutable reader, retaining storage/decoder/schema/canonicalization versions, source pins, original clocks, revision membership and limitations. The first diagnostic algorithm SHALL accept only `pumpswap-storage-v1`, `pumpswap-operations-v4`, `may18-oct07-endpoint-proof-v4` and `length-prefixed-v1`. Unknown batch SHALL return explicit absence; malformed requests, unsupported versions, corrupt source membership or exceeded bounds SHALL fail without a partial report. No caller-submitted operation result, automatic batch merge, latest revision selection, provider call, secret access or write SHALL occur.

The window SHALL be nonempty `[fromInclusive,toExclusive)`, with both endpoints aligned to UTC Unix-epoch interval boundaries, and `toExclusive <= observationCutoff` after cutoff microsecond normalization. It SHALL contain at most1000 bucket positions. Existing source limits of128 transaction derivations,10000 operation revisions,8,000,000 bytes per raw input and64,000,000 aggregate input/derived bytes SHALL remain enforced. Reports SHALL contain at most10000 populated pool/bucket rows and64,000,000 canonical UTF-8 bytes. Database source acquisition SHALL use one independent coherent read-only snapshot with a10-second transaction/query deadline; calculation SHALL follow outside that owned read transaction. Excess work SHALL reject rather than truncate or paginate silently.

#### Scenario: Resolve one exact batch without effects
- **WHEN** an existing supported batch is requested with valid bounded window/cutoff/interval
- **THEN** the result SHALL resolve only its frozen membership and recorded versions
- **AND** raw, capture, derivation, normalized fact, signal and evaluation rows SHALL remain unchanged.

#### Scenario: Reject unavailable or excessive work
- **WHEN** the exact key is unknown, or a request/source/output violates the specified shape, version, integrity or size contract
- **THEN** the system SHALL return absence for the unknown key and explicit failure for the other violations
- **AND** it SHALL produce no partial candle report or durable effect.

### Requirement: Canonical operation ambiguity and complete reconciliation

The system SHALL validate the entire frozen operation set before time filtering. Each operation revision SHALL resolve to exactly one recorded transaction parent and canonical identity `(chain,signature,complete outer/CPI path,leg ordinal)`. More than one distinct revision for one canonical operation SHALL reject the whole diagnostic, even when content agrees or one revision falls outside the window. Equal raw retry already deduplicated by the existing store SHALL not add counts. No provider, session, fee payer, trader grouping or arrival preference SHALL choose between revisions.

Every stored transaction derivation SHALL remain represented by exact revision key, status, reason, original received time and inside/outside-window classification. Every confirmed operation SHALL have an exact revision reference and an included or outside-window disposition; included operations SHALL remain counted even with missing price metadata or a PARTIAL parent. Failed transactions SHALL contribute no operations; non-operation/unsupported/invalid/no-PumpSwap/partial source statuses SHALL not disappear or inflate fully decoded transaction counts. Transaction classification totals SHALL equal all batch transaction derivations; included plus outside-window operation totals SHALL equal all batch operation revisions.

#### Scenario: Same operation has two source revisions
- **WHEN** a pinned batch contains two providers or source bindings for the same canonical operation position
- **THEN** diagnostics SHALL fail explicitly with revision ambiguity rather than count two trades or select one
- **AND** existing immutable batches/revisions SHALL remain readable unchanged.

#### Scenario: Failed and partial evidence remains measurable
- **WHEN** a batch contains failed, non-operation, unsupported and partial transactions together with separately proven operations
- **THEN** all transaction statuses/reasons SHALL reconcile, and only the proven operations SHALL enter activity
- **AND** a partial transaction SHALL remain partial regardless of how many operations it contributes.

### Requirement: Observation-time windows and deterministic candle endpoints

Eligibility and bucketing SHALL use the recorded transaction parent's original trusted `receivedAt`, never source event time, slot, ingestion time, derivation time or current clock. Included operations SHALL satisfy `fromInclusive <= receivedAt < toExclusive` and `receivedAt <= observationCutoff`. Within each `(chain,pool,baseMint,quoteMint,bucketStart)` tuple, endpoint ordering SHALL be ascending received time, exact chain/signature, numeric outer index, lexicographic numeric CPI path with shorter-prefix-first, numeric leg ordinal, then exact revision key. Text comparisons SHALL use unsigned UTF-8 bytewise order. Open/close SHALL select the first/last price-eligible operation under that order; equal high/low ratios SHALL select the earliest operation under the same order. Output rows SHALL sort by exact chain/pool/baseMint/quoteMint and bucketStart. Identities SHALL compare component tuples, not delimiter concatenations.

Each populated bucket SHALL report its exact start/end, included operation references and endpoint references. Buckets with zero included operations SHALL be absent, with an explicit sparse-output convention; no empty zero-volume candle, interpolated price or carry-forward endpoint SHALL be fabricated. Deterministic equal-time ordering SHALL not be described as actual Solana execution chronology.

#### Scenario: Boundary and original clock selection
- **WHEN** an operation is received exactly at a bucket start or window end, or has favorable source time but received time outside the window
- **THEN** start-boundary receipt SHALL enter its bucket, window-end receipt SHALL be outside, and favorable source time SHALL not change eligibility
- **AND** cutoff normalization and permutation SHALL preserve the same references and values.

#### Scenario: Same-time ties and missing buckets
- **WHEN** price-eligible operations share received time but differ in numeric instruction paths, or a bucket has no observed operation
- **THEN** declared total order SHALL choose stable endpoints independently of input ordering
- **AND** no endpoint or zero-trading/continuity claim SHALL be supplied for the empty bucket.

### Requirement: Exact native activity and execution ratios

Activity SHALL report total/buy/sell operation counts and exact summed base/quote raw units per populated bucket and pool, with no cross-pool, cross-mint or cross-chain summation. Supported buy variants SHALL mean trader base receipt/quote debit, and sell variants trader base debit/quote receipt under the recorded decoder proof. Counts SHALL preserve multiple independently proven legs, including the same trader. These gross observed flows SHALL not be labeled net economics, liquidity or complete volume.

For each operation with both observed decimals, the execution ratio SHALL equal `(quoteAmount * 10^baseDecimals)/(baseAmount * 10^quoteDecimals)` as a positive reduced integer numerator/denominator; exact high/low comparison SHALL use integer cross multiplication. No rounded decimal, binary floating point, division MathContext or assumed decimals SHALL establish authoritative OHLC. Within one included pool/mint tuple, conflicting observed base or quote decimals SHALL reject the diagnostic. Missing decimals SHALL retain activity but record explicit unpriced operation references/reasons; OHLC SHALL cover only price-eligible operations and be entirely absent when none qualify. Eligible/unpriced counts SHALL reconcile to included count. Event-declared, transfer-attributed and transaction-wide fee facts SHALL remain separate source evidence; unknown expenses SHALL not become zero or be allocated across operations.

#### Scenario: Exact ratio comparison and independent units
- **WHEN** two ratios are closer than a rounded decimal representation distinguishes, or raw amounts exceed binary-floating precision
- **THEN** exact rational comparison SHALL select the correct high/low and exact integer sums SHALL remain unchanged
- **AND** no USD/spot/executable price, reserve valuation, fee-adjusted PnL or cross-quote volume SHALL be inferred.

#### Scenario: Missing or conflicting decimals
- **WHEN** one operation lacks decimals, or included observations assert contradictory decimals for one pool/mint tuple
- **THEN** the missing-decimal operation SHALL remain in activity with explicit unpriced status, while contradictory decimals SHALL fail the whole diagnostic
- **AND** missing metadata SHALL not be zero-filled or guessed.

### Requirement: Versioned incomplete diagnostic interpretation

Every report SHALL identify `pumpswap-pool-metrics-v1`, `length-prefixed-v1`, normalized request, exact batch/source versions and pins, selected revision references, reconciliation, ordered output and original batch limitations in an algorithm-qualified SHA-256 fingerprint using versioned length-prefixed UTF-8 fields. Equal request and immutable source SHALL reproduce exact ordered content/fingerprint without using processing time; changed source/request/version SHALL have distinguishable identity. Published diagnostic records SHALL be immutable and payload/secret-free.

Results SHALL explicitly state `HISTORICAL_DIAGNOSTIC`, `OBSERVED_BATCH_ONLY`, `coverage=INCOMPLETE`, `unreceivedLoss=UNKNOWN`, `fees=INCOMPLETE` and `signalReadiness=NOT_ESTABLISHED`. The observation cutoff SHALL not assert that decoding, stored revisions or quality evidence were available at that historical decision instant. Source gaps, unsupported operations and missing observations SHALL remain limitations; absence of gap metadata SHALL mean unknown coverage, never complete continuity. The system SHALL not infer pool liquidity from event-declared/virtual reserves or context-slot account snapshots, synthesize USD conversion/risk facts, publish normalized snapshots/signals/evaluations or claim D1/P1 admission, R1 progress or statistical edge. Legacy accepted behavior and historical raw/research evidence SHALL remain unchanged.

#### Scenario: Reproducible diagnostic without signal admission
- **WHEN** the same frozen batch/request is analyzed again after current time, later captures or other revisions advance
- **THEN** its report and fingerprint SHALL remain identical with historical diagnostic limitations
- **AND** it SHALL not create or substitute evidence for an accepted liquidity-spike signal or decision/evaluation snapshot.

#### Scenario: Collector gap or unavailable economic input
- **WHEN** retained limitations disclose unresolved capture gaps, unknown expenses or absent admissible USD/liquidity/quality/risk evidence
- **THEN** native activity SHALL remain inspectable with those limitations and NOT_ESTABLISHED signal readiness
- **AND** no fabricated continuity, zero cost, risk ALLOW or fallback market fact SHALL be introduced.
