## Purpose

Provide bounded reproducible blockchain-event-time candles and a read-only native-SOL price-pattern diagnostic over one retained PumpSwap operation batch, without historical-availability, complete-coverage or accepted trading-signal claims.

## ADDED Requirements

### Requirement: Explicit immutable batch and independent time cutoffs

The system SHALL expose a synchronous read-only candle request naming one immutable stored batch, an aligned nonempty UTC event window `[fromInclusive,toExclusive)` and independent knowledge cutoff K. The first version SHALL use300-second Unix-epoch-aligned buckets, at most1000 bucket positions,128 transactions,10000 operations/populated output rows and64,000,000 canonical output bytes. `toExclusive <= K`; authoritative instants SHALL normalize to microseconds before validation/computation. The source SHALL use the existing integrity-checked reader with one independent read-only coherent database snapshot and10-second transaction/query bounds; calculation SHALL occur after that read. Unknown batch SHALL return explicit absence; malformed/unsupported/corrupt/excessive inputs SHALL fail without partial success, network access or writes.

The first version SHALL accept only storage `pumpswap-storage-v1`, decoder `pumpswap-operations-v4`, schema `may18-oct07-endpoint-proof-v4` and canonicalization `length-prefixed-v1`; whole-batch canonical revision ambiguity SHALL reject before time filtering. It SHALL not automatically re-decode, merge batches or select a latest revision.

Per-operation `knownAt` SHALL be the maximum of original raw received/admitted/ingested times and its transaction-revision, operation-revision and batch persistence times. Eligibility SHALL require `knownAt <= K`; timestamps SHALL NOT be backdated to event time or capture time. Every frozen operation SHALL reconcile to included or an explicit excluded reason, and every transaction status SHALL remain represented. Missing historical knowledge SHALL NOT be interpreted as a negative price pattern or zero trading.

#### Scenario: Late knowledge cannot enter an earlier decision
- **WHEN** an operation has event time before the event window end but any required availability/persistence time after K
- **THEN** that operation SHALL not contribute prices, volumes or signal endpoints at K
- **AND** the result SHALL disclose unavailable knowledge rather than substitute event time as availability.

#### Scenario: Exact source remains read-only and bounded
- **WHEN** a supported known batch and valid request are read, or the source is unknown/corrupt/over-limit
- **THEN** a supported source SHALL resolve its exact membership, unknown source SHALL return absence and invalid work SHALL reject explicitly
- **AND** no capture, raw, derivation, normalized fact, candidate, accepted-signal or evaluation row SHALL change.

### Requirement: Raw verified block time and honest chronological ordering

Event time SHALL come only from integral epoch-seconds `blockTime` in the exact retained parent raw JSON and SHALL agree with the stored source event time when present. Missing/null event time SHALL retain `MISSING_EVENT_TIME` exclusion; contradictory raw identity/slot/digest/source time or malformed event time SHALL fail explicitly. Slot interpolation, current time, received time, decoder publication time and wrapper enumeration SHALL NOT supply missing block time. A valid raw `transactionIndex` SHALL be retained as source-reported metadata, not independently verified chain evidence. One `(chain,slot,index)` referring to different signatures, or one signature with contradictory slot/index/time, SHALL reject. An absent index SHALL remain explicit ordering uncertainty.

Supplied indices SHALL be exact nonnegative integers representable as signed64-bit values; malformed, negative or overflowing values SHALL fail rather than be truncated, rounded or relabelled absent.

Operations SHALL group by exact `(chain,pool,baseMint,quoteMint)` tuples and event bucket. The total order SHALL be `(eventTime,slot,index-presence,index,signature,outerIndex,numeric innerPath,legOrdinal,revisionKey)`; present indices sort before missing indices, absent index has no invented numeric value, text uses unsigned UTF-8 byte order and numeric path prefixes sort shorter first. Equal high/low prices SHALL use the earliest ordered operation. Missing-index signature tie-break SHALL remain deterministic but SHALL NOT be labelled proven execution order. Even present indices SHALL be labelled provider-reported, not independent chain verification. Empty buckets SHALL remain absent; no carry-forward or interpolation SHALL occur.

#### Scenario: Receipt time differs from block time
- **WHEN** a transaction was received in another bucket from its raw blockTime
- **THEN** the new candle SHALL use blockTime while preserving received and knownAt separately
- **AND** existing observation-time metrics and fingerprints SHALL remain unchanged.

#### Scenario: Same-time transactions and missing ordering evidence
- **WHEN** input order changes or operations share event time, slot or CPI prefixes
- **THEN** the declared total order SHALL yield identical endpoints and fingerprints
- **AND** missing transactionIndex SHALL remain an observable uncertainty, not a synthetic block position.

### Requirement: Exact observed native-SOL candles and complete limitations

Only groups whose quote mint is exact WSOL `So11111111111111111111111111111111111111112` SHALL produce native-SOL prices. Decimals SHALL resolve to source-proven token-balance/mint metadata through the retained decoder operation/raw lineage, including known WSOL decimals9; mint names, assumed defaults and raw amount ratios SHALL NOT replace that proof. Raw amounts SHALL remain exact integers; missing/ambiguous base/quote decimals, including unproved Token-2022 metadata, SHALL retain amounts/counts with unpriced reasons. Contradictory known decimals within a group or a known WSOL decimal other than9 SHALL fail. Price SHALL be reduced positive rational `quoteRaw * 10^baseDecimals / (baseRaw * 10^quoteDecimals)`; authoritative comparisons SHALL use exact cross multiplication. Raw base/quote activity sums SHALL not mix groups; quote SOL volume SHALL use exact decimal division by10^9 only with proven WSOL units. A convenience decimal price SHALL be separately labelled scale18/HALF_EVEN rendering and SHALL not drive comparisons or fingerprints in place of the rational.

Populated buckets SHALL return observed OHLC with exact operation/revision/raw lineage, activity counts, knownAt, missing-price/time/order dispositions and sparse conventions. Results SHALL identify `OBSERVED_BATCH_ONLY`, coverage `INCOMPLETE`, unseen loss `UNKNOWN`, fees `INCOMPLETE`, readiness `DIAGNOSTIC_ONLY_NOT_ADMITTED` and no guaranteed chain completeness/executable price/valuation. Unavailable capture gap evidence SHALL remain `GAP_COVERAGE_NOT_BOUND`; batch limitations SHALL be preserved, not converted into zero gaps. Fingerprints SHALL include exact batch/source versions, normalized request, all behavioral parameters, ordered references/dispositions/limitations and algorithm/canonicalization versions.

#### Scenario: Missing decimals or non-SOL quote
- **WHEN** otherwise retained operations lack price metadata or use another quote mint
- **THEN** each operation SHALL retain a counted unpriced or unsupported-quote reason without invented conversion
- **AND** native-SOL prices and volumes SHALL not mix assets or hide incomplete coverage.

#### Scenario: Immutable diagnostic repeats
- **WHEN** identical frozen source and normalized request are processed after unrelated captures or in another input order
- **THEN** the ordered candle report and versioned fingerprint SHALL remain identical
- **AND** no missing bucket, fee, liquidity, USD or continuity SHALL be fabricated.

### Requirement: Separate native-SOL price-rise diagnostic

The existing signal module SHALL add a separate read-only `native-sol-price-rise-v1` diagnostic naming one exact batch/pool/base/quote, event end E and knowledge cutoff K. E SHALL be300s-aligned, `E <= K`; it SHALL request exactly the two adjacent windows `[E-600s,E-300s)` and `[E-300s,E)` through the owning market-data API. Both buckets SHALL end by K, and every selected endpoint SHALL satisfy conservative knowledge eligibility. A versioned exact5% engineering threshold SHALL compare current observed close with prior close by rational cross multiplication, inclusive at1.05. A configurable caller-supplied threshold or automatic calibration SHALL NOT occur in this version.

The output SHALL distinguish `TRIGGER` for the observed threshold met, `NO_TRIGGER` for adequate observable endpoints below threshold, and `INSUFFICIENT` for absent/unknown batch, missing bucket/price/time/order or knowledge. Both buckets SHALL have at least one priced operation and no otherwise selected unpriced/missing-index operation; excluded future-knowledge or missing-event-time operations that could affect the requested group SHALL produce insufficiency rather than a confident no-trigger. Full-source statuses/reasons and incomplete coverage SHALL accompany any result. No positive saved trigger count SHALL be required. These statuses SHALL describe an observed native-price pattern, not risk-qualified ENTRY, profitability, historical realtime freshness or full-market absence.

#### Scenario: Exact boundary and negative result
- **WHEN** adequate eligible closes are1 and1.05 SOL, or the current close is exactly below1.05
- **THEN** the former SHALL report TRIGGER and the latter NO_TRIGGER using exact arithmetic
- **AND** neither SHALL create candidate, accepted signal, outcome or recommendation.

#### Scenario: Missing evidence differs from no trigger
- **WHEN** either adjacent bucket, price, ordering or cutoff-eligible knowledge is insufficient
- **THEN** the result SHALL report INSUFFICIENT with the specific reasons
- **AND** it SHALL not widen windows, carry prices, return NO_TRIGGER or fabricate USD/liquidity/risk/fees.

### Requirement: Retrospective diagnostic is not accepted signal or evaluation

Every signal diagnostic SHALL expose event end E and knowledge cutoff K separately, `decisionKnowledgeAt=K`, diagnostic-only readiness and whether all relevant derivations were already available at E. K later than E SHALL remain retrospective analysis at K, not a backdated historical decision at E. No report SHALL assert production PIT admission solely because a conservative availability filter passed. New identities SHALL be algorithm-qualified content fingerprints, not accepted signal IDs or admissible dataset identities. Existing accepted liquidity-spike/risk rules, observation-time metrics, operator report, signal/evaluation APIs and persisted historical records SHALL retain their original meaning.

#### Scenario: Old event pattern becomes readable after derivation
- **WHEN** an October10 event batch was decoded/persisted on October11 and is analyzed with K on October11
- **THEN** its pattern SHALL be labelled retrospective at K with historical-at-E availability unavailable
- **AND** it SHALL not claim a signal could have been traded or evaluated on October10.

#### Scenario: Existing detector and valuation cannot consume diagnostic substitutes
- **WHEN** a native-SOL diagnostic lacks qualified USD liquidity/risk/evaluation inputs
- **THEN** no accepted liquidity-spike signal, risk ALLOW, score, net return or valuation SHALL be fabricated
- **AND** existing signal/evaluation regression behavior SHALL remain unchanged.
