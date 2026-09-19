# signal-evaluation Specification

## Purpose

Defines the first complete research result: one point-in-time `LIQUIDITY_SPIKE` signal, one cost-aware 1h ENTRY outcome and one reproducible evidence report derived from immutable recorded evidence.

## Requirements

### Requirement: Point-in-time liquidity-spike detection
The system SHALL detect `LIQUIDITY_SPIKE` only when the latest eligible decision-time liquidity is at least 50 percent above the deterministic one-hour baseline and the absolute increase is at least USD 10,000, using no observation that became available after the decision cutoff.

#### Scenario: Accept the recorded liquidity spike
- **WHEN** the recorded dataset provides USD 50,000 baseline liquidity and USD 80,000 decision-time liquidity one hour later with complete fresh evidence
- **THEN** the detector SHALL create one candidate for the asset at the declared decision cutoff
- **AND** later outcome observations SHALL not participate in detection.

#### Scenario: Reject insufficient evidence
- **WHEN** the decision-time window has no eligible baseline or either threshold is not met
- **THEN** no accepted `LIQUIDITY_SPIKE` signal SHALL be created
- **AND** the system SHALL not fill the missing evidence with a fabricated fallback.

### Requirement: Candidate-first risk gating
The system SHALL durably identify the signal candidate before risk gating, evaluate risk only from validated evidence available at the decision cutoff, and create an accepted ENTRY signal only for an `ALLOW` decision.

#### Scenario: Accept an allowed candidate
- **WHEN** the candidate has zero manipulation flags, `DISCOVERY` lifecycle, at least USD 30,000 liquidity and otherwise complete decision-time evidence
- **THEN** the point-in-time risk decision SHALL be `ALLOW`
- **AND** the already recorded candidate SHALL transition to accepted
- **AND** exactly one immutable signal snapshot SHALL be created.

#### Scenario: Do not accept a non-allowed candidate
- **WHEN** point-in-time risk evidence produces a decision other than `ALLOW`
- **THEN** the candidate and its decision evidence SHALL remain measurable
- **AND** no accepted ENTRY signal or ENTRY outcome SHALL be created.

### Requirement: Versioned immutable signal snapshot
Every accepted signal SHALL preserve its network-aware asset identity, family, decision instant, dataset fingerprint, source normalized identities, risk evidence, detector/scorer versions, canonical configuration fingerprint, score, grade, confidence and structured reasoning exactly as known at decision time.

#### Scenario: Score the complete first-slice signal
- **WHEN** the configured first-slice detector accepts complete fresh evidence satisfying both liquidity thresholds with risk `ALLOW`
- **THEN** the immutable snapshot SHALL record score `70`, grade `B` and confidence `1.0000`
- **AND** its reasoning SHALL attribute 20 base points, 30 relative-growth points and 20 absolute-growth points
- **AND** no later market, risk or outcome fact SHALL change the snapshot.

#### Scenario: Repeat signal detection
- **WHEN** the same dataset, cutoff, algorithm versions and canonical configuration are evaluated again
- **THEN** the candidate and accepted signal SHALL resolve idempotently to the same identities and snapshot
- **AND** no duplicate candidate or signal SHALL be created.

### Requirement: Point-in-time 1h entry outcome
The system SHALL evaluate the accepted signal at the `1h` horizon using the earliest admissible price observed strictly after the signal became available and the deterministic eligible price at the horizon, without using evidence beyond the evaluation cutoff or silently dropping an unpriced result.

#### Scenario: Measure the recorded 1h outcome
- **WHEN** the first post-decision price is USD 1.00 with USD 80,000 liquidity and the eligible 1h price is USD 1.20
- **THEN** the gross return SHALL be `0.20000000`
- **AND** the USD 50,000-to-below-USD 200,000 liquidity tier SHALL apply a `0.04000000` round-trip friction haircut
- **AND** the net return SHALL be `0.16000000`
- **AND** the outcome SHALL retain both price observations, their source, confidence and observation times.

#### Scenario: Prevent look-ahead entry pricing
- **WHEN** a price observation is at or before the signal availability instant
- **THEN** it SHALL not be selected as the simulated entry price
- **AND** adding a later observation beyond the declared evaluation cutoff SHALL not change the outcome.

#### Scenario: Preserve an unpriced outcome
- **WHEN** no admissible entry or horizon price exists
- **THEN** the signal-horizon result SHALL remain present with an explicit unpriced status and missing-price reason
- **AND** it SHALL not be omitted from report counts or replaced with provider fallback data.

### Requirement: Idempotent reproducible evaluation report
The system SHALL persist an immutable evaluation run and signal-horizon outcome, then produce a deterministic one-family evidence report identified by a content fingerprint and a complete provenance manifest containing build identity, source revision and dirty state, evaluation algorithm version, canonical configuration fingerprint, dataset fingerprint and evaluation cutoff; no seed is required because this slice uses no randomness.

#### Scenario: Produce the first evidence report
- **WHEN** the recorded allowed signal and priced 1h outcome are evaluated with complete provenance
- **THEN** the report SHALL contain one `LIQUIDITY_SPIKE` ENTRY signal, one priced outcome and exact average net return `0.16000000`
- **AND** it SHALL distinguish signal score from measured return
- **AND** every report value SHALL resolve to the immutable signal, outcome, dataset and run evidence.

#### Scenario: Repeat the evaluation run
- **WHEN** the same immutable dataset, signal snapshot, cutoff, build/source identity, algorithm version and canonical configuration are evaluated again
- **THEN** the run, outcome, ordered report content and report fingerprint SHALL be identical
- **AND** no duplicate durable effect SHALL be created.

#### Scenario: Reject incomplete provenance
- **WHEN** mandatory run provenance is missing
- **THEN** the evaluation SHALL fail before publishing a comparable report
- **AND** no incomplete run SHALL be silently grouped with reproducible results.
