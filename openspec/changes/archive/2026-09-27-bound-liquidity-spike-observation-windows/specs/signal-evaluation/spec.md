## MODIFIED Requirements

### Requirement: Point-in-time liquidity-spike detection
The system SHALL detect `LIQUIDITY_SPIKE` only when the latest eligible decision-time liquidity is at least 50 percent above the deterministic one-hour baseline and the absolute increase is at least USD 10,000, using no observation that became available after the decision cutoff. New detection calls SHALL use the fixed policy `liquidity-spike-v2`. The system SHALL normalize the request's authoritative instants to UTC microsecond precision before validating or deriving time windows, and SHALL define `C` as the normalized decision cutoff and `T = C - 60 minutes`. The normalized `windowStart` SHALL equal `T`.

Baseline eligibility SHALL use `observedAt` in the inclusive window `[T - 5 minutes, T]`; current eligibility SHALL use `observedAt` in the inclusive window `[C - 1 minute, C]`. Both selections SHALL use the requested asset, immutable dataset fingerprint and decision cutoff. Each selection SHALL choose the greatest observation in the ascending total order `(observedAt, chain identity, transaction value, event locator)`, with textual keys compared bytewise in UTF-8, matching the existing market-data ordering. Provider, source event time, input arrival order and source iteration order SHALL NOT replace that order or change eligibility.

The minimum evidence SHALL be one eligible observation in each window, with two distinct normalized identities and two distinct observation timestamps. If that evidence is absent or either growth threshold fails, the system SHALL return no candidate and no accepted signal and SHALL create no durable signal effects. It SHALL NOT widen windows, interpolate liquidity or fabricate fallback evidence. Unsupported detector versions, a mismatched normalized `windowStart`, or time bounds that cannot be represented SHALL cause explicit validation failure before market-data queries, risk assessment or signal writes.

This policy SHALL represent an endpoint comparison whose actual observation interval is between 59 and 65 minutes inclusive. The windows SHALL express observation-time recency only; they SHALL NOT be represented as proof of recent source events, sustained growth, continuous hourly coverage or absence of provider gaps. The existing caller responsibility for comparable asset/dataset liquidity evidence SHALL remain; this policy SHALL NOT introduce cross-venue or cross-pool aggregation.

#### Scenario: Accept the recorded liquidity spike
- **WHEN** a valid `liquidity-spike-v2` request's recorded dataset provides USD 50,000 baseline liquidity and USD 80,000 decision-time liquidity one hour later with complete fresh evidence
- **THEN** the detector SHALL create one candidate for the asset at the declared decision cutoff
- **AND** later outcome observations SHALL not participate in detection.

#### Scenario: Reject insufficient evidence
- **WHEN** either observation window has no eligible endpoint or either growth threshold is not met
- **THEN** no candidate or accepted `LIQUIDITY_SPIKE` signal SHALL be created
- **AND** no durable signal effects SHALL occur
- **AND** the system SHALL not fill the missing evidence with a fabricated fallback.

#### Scenario: Include all four exact window boundaries
- **WHEN** otherwise eligible baseline observations occur exactly at `T - 5 minutes` or `T`, or current observations occur exactly at `C - 1 minute` or `C`
- **THEN** each observation SHALL be eligible for its corresponding window
- **AND** the latest eligible observation in each window SHALL be selected.

#### Scenario: Exclude observations immediately outside the windows
- **WHEN** an observation occurs one microsecond before or after either window's inclusive bounds
- **THEN** it SHALL be ineligible for that window even if its liquidity would satisfy both growth thresholds
- **AND** the system SHALL not widen either window to use it.

#### Scenario: Reject stale baseline despite sufficient growth
- **WHEN** the dataset has an eligible current observation but every baseline observation predates `T - 5 minutes`
- **THEN** the result SHALL contain no candidate and no accepted signal
- **AND** no durable signal effects SHALL occur even if an older baseline would satisfy both thresholds.

#### Scenario: Reject stale current despite sufficient growth
- **WHEN** the dataset has an eligible baseline but its latest observation at or before `C` predates `C - 1 minute`
- **THEN** the result SHALL contain no candidate and no accepted signal
- **AND** no durable signal effects SHALL occur even if that older current value would satisfy both thresholds.

#### Scenario: Resolve equal observation timestamps deterministically
- **WHEN** multiple eligible observations have the latest timestamp in an endpoint window and differ in transaction value or event locator
- **THEN** the greatest normalized identity under the declared bytewise order SHALL select the endpoint
- **AND** reordering raw submission, changing provider arrival order, or using distinct Unicode keys SHALL not change the selected identity and resulting signal evidence.

#### Scenario: Require two distinct endpoints
- **WHEN** the admitted evidence contains fewer than two distinct normalized identities or fewer than two distinct observation timestamps
- **THEN** it SHALL not satisfy the two-endpoint requirement
- **AND** the system SHALL create no candidate or accepted signal from that evidence.

#### Scenario: Preserve observation-time eligibility
- **WHEN** an observation's source event time differs from its `observedAt`
- **THEN** endpoint eligibility SHALL depend on `observedAt` within the declared window and cutoff
- **AND** a favorable source event time SHALL not admit an observation outside those bounds
- **AND** the original source event time SHALL remain provenance without establishing live-event freshness.

#### Scenario: Isolate the asset dataset and decision cutoff
- **WHEN** later observations, observations of another asset, or observations outside the requested immutable dataset would change the apparent liquidity increase
- **THEN** those observations SHALL not participate in endpoint selection
- **AND** the result SHALL depend only on eligible observations of the requested asset within that dataset and the declared cutoff.

#### Scenario: Normalize timestamps before enforcing the one-hour target
- **WHEN** request instants differ below microsecond precision but their normalized values satisfy `windowStart = C - 60 minutes`
- **THEN** the request SHALL use those normalized instants for validation, window bounds, identity and persisted evidence
- **AND** equal normalized requests SHALL produce the same result and immutable identities.

#### Scenario: Reject a mismatched or unrepresentable target
- **WHEN** the normalized `windowStart` differs from `C - 60 minutes`, or a required time bound cannot be represented
- **THEN** detection SHALL fail explicitly before market-data queries, risk assessment or signal writes
- **AND** the system SHALL not silently adjust the request or clamp the window.

#### Scenario: Interpret endpoint coverage without claiming continuity
- **WHEN** two eligible endpoints satisfy the policy but no observations exist between the endpoint windows
- **THEN** that absence alone SHALL not invalidate the endpoint comparison
- **AND** the result SHALL not assert sustained growth, complete hourly coverage or verified absence of provider gaps.

### Requirement: Versioned immutable signal snapshot
Every accepted signal SHALL preserve its network-aware asset identity, family, decision instant, dataset fingerprint, source normalized identities, risk evidence, detector/scorer versions, canonical configuration fingerprint, score, grade, confidence and structured reasoning exactly as known at decision time. New detection SHALL accept only `liquidity-spike-v2`; that immutable version SHALL identify the fixed one-hour target, five-minute baseline tolerance, one-minute current bound and endpoint-selection policy. The existing request shape and caller-supplied configuration fingerprint SHALL remain unchanged. Previously persisted snapshots SHALL retain their stored version and content and SHALL remain readable without re-running detection or being rewritten under the new policy.

#### Scenario: Score the complete first-slice signal
- **WHEN** the configured first-slice detector using `liquidity-spike-v2` accepts complete fresh evidence satisfying both liquidity thresholds with risk `ALLOW`
- **THEN** the immutable snapshot SHALL record score `70`, grade `B` and confidence `1.0000`
- **AND** its reasoning SHALL attribute 20 base points, 30 relative-growth points and 20 absolute-growth points
- **AND** no later market, risk or outcome fact SHALL change the snapshot.

#### Scenario: Repeat signal detection
- **WHEN** the same dataset, cutoff, algorithm versions and canonical configuration are evaluated again
- **THEN** the candidate and accepted signal SHALL resolve idempotently to the same identities and snapshot
- **AND** no duplicate candidate or signal SHALL be created.

#### Scenario: Reject unsupported versions before effects
- **WHEN** new detection requests `liquidity-spike-v1`, an unknown or future detector version, or a null, blank or whitespace-modified detector version
- **THEN** it SHALL fail explicitly before market-data queries, risk assessment or signal writes, even when endpoint evidence would be absent
- **AND** the supplied version SHALL not be silently aliased or normalized to `liquidity-spike-v2`.

#### Scenario: Preserve historical snapshot lookup
- **WHEN** a caller looks up a previously persisted accepted signal carrying `liquidity-spike-v1`
- **THEN** its original identity, version, source evidence, scores, configuration fingerprint and other immutable values SHALL be returned unchanged
- **AND** lookup SHALL not require fresh detection or mutate the old snapshot.

#### Scenario: Record the new fixed policy in reproducible identity
- **WHEN** valid fresh evidence produces a new accepted signal under `liquidity-spike-v2`
- **THEN** its candidate and accepted snapshot SHALL carry the supported detector version and caller configuration fingerprint
- **AND** the new version SHALL distinguish its identity from an otherwise equivalent historical `liquidity-spike-v1` calculation.
