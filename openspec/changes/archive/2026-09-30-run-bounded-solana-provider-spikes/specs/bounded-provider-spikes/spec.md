## Purpose

Defines finite, operator-enabled Alchemy research probes whose raw receipts and explicit limitations can inform provider selection without admitting data to production storage.

## ADDED Requirements

### Requirement: Explicit and isolated provider execution

The spike tool SHALL make no provider call without explicit operator enablement, valid Alchemy configuration, a fresh dedicated evidence root and a passing local budget preflight. Its LIVE transaction subscription SHALL use only the three declared watched Solana programs at finalized commitment. It SHALL keep provider credentials out of command arguments, stdout, logs, manifests and diagnostic errors.

#### Scenario: Disabled or invalid launch
- **WHEN** enablement, configuration, evidence-root isolation or budget preflight is absent or invalid
- **THEN** the tool SHALL make zero provider calls
- **AND** it SHALL report a bounded, secret-safe local reason without modifying prior evidence.

#### Scenario: Watched finalized delivery
- **WHEN** the live experiment is explicitly enabled with valid configuration
- **THEN** it SHALL subscribe to both successful and failed non-vote transactions involving Pump.fun bonding curve, PumpSwap or Raydium AMM v4 at finalized commitment
- **AND** it SHALL preserve and count failed transactions separately from successful transactions
- **AND** its manifest SHALL record the exact filter, program IDs, source/tool identity and start conditions.

### Requirement: Shared finite experiment budget

The initial A/B experiments SHALL share one recorded estimated-spend ceiling of US$3. The owner-authorized single final A attempt SHALL use a separate fresh root and the same US$3 local estimated-spend and 15,000,000,000 received-byte ceilings; the earlier A/B debit SHALL remain immutable and SHALL be reported separately alongside the final A debit, never erased or silently included in the new root's budget. Each live/replay experiment SHALL stop before its own cumulative received application bytes exceed 15,000,000,000. Each experiment SHALL enforce its declared finite wall, request, response and evidence-storage limits before new work, preserve debit and terminal state on failure, and SHALL never resume or overwrite the earlier stopped S1 evidence. Local spend estimates SHALL be labeled estimates rather than actual invoices or a guaranteed provider billing ceiling.

#### Scenario: Ceiling prevents further work
- **WHEN** the next bounded reservation would cross any applicable local ceiling
- **THEN** the new request or stream work SHALL NOT start
- **AND** the tool SHALL record the used debit, stop reason, unfinished work and immutable evidence already received.

#### Scenario: Earlier evidence remains separate
- **WHEN** either new experiment starts or terminates
- **THEN** the original `alchemy-s1` smoke and smoke-retry receipts and their stopped ledger SHALL remain unchanged
- **AND** the new experiment's root, counters and provenance SHALL be independently inspectable.

#### Scenario: Single final A root is fresh and exact
- **WHEN** the owner-authorized final A attempt is requested
- **THEN** the CLI SHALL require the explicit canonical root `C:/crypto-research-evidence/provider-a-final-1` as a fresh direct child of the approved evidence parent and SHALL reject any already existing root, even an empty one, before creating evidence or making a provider call
- **AND** it SHALL reject an omitted root, the earlier `provider-ab-1` root, path aliases, symlinks, junctions, nested paths and traversal escapes rather than silently falling back
- **AND** it SHALL leave the earlier A/B receipts, ledger and B behavior unchanged.

### Requirement: Bounded finalized live and replay observations

The live experiment SHALL count 60 minutes only while it is connected and receiving current finalized evidence after catch-up. It SHALL report replay/catch-up, stalls and disconnects separately. After live observation, it SHALL make two separate finalized slot-only `from_slot` subscriptions from finalized-tip offsets of 9,000 and 60,000 slots, with no transaction filter or transaction subscription. Each probe SHALL stop after the first returned slot updates, 60 seconds or 10,000,000 received bytes, whichever occurs first. A replay response SHALL demonstrate only access to the returned slot sample, not transaction replay or complete recovery of the intervening range.

#### Scenario: Clean live time
- **WHEN** catch-up, a disconnect, inactivity or replay occurs during the experiment
- **THEN** that interval SHALL contribute zero clean LIVE time
- **AND** the final report SHALL distinguish achieved clean time from elapsed wall time and provide latency and traffic distributions with sample counts.

#### Scenario: Limited replay proof
- **WHEN** a replay offset returns its first finalized slot updates within its bounds
- **THEN** the tool SHALL retain raw slot receipts, requested and returned slots and latency/byte evidence and stop that probe
- **AND** it SHALL NOT mark the complete offset interval recovered or gap-free.

### Requirement: Measured capacity report

The live experiment SHALL report raw protobuf bytes per clean LIVE hour; projected GB/day and TB/30-day month with their extrapolation basis; raw message events and unique watched transactions per hour; per-program counts with overlap and unique-union accounting; failed-transaction share; p50/p95 receive lag with source and sample counts; reconnect, stall and observed gap counts; actual raw-evidence growth and database-growth status; and a local zstd compression ratio with sample scope. Unknown or unsampled values SHALL be marked unavailable rather than zero.

#### Scenario: One-hour sample is reported
- **WHEN** the live experiment ends with retained measurements
- **THEN** the report SHALL distinguish measured hour-rate and raw file sizes from daily/monthly projections, show failed and overlapping-program transactions explicitly, and label database growth not applicable when no database write occurred
- **AND** missing timestamps, disconnected time or unavailable zstd measurement SHALL not be silently included in a percentile or compression claim.

### Requirement: Historical account-state sample

The historical experiment SHALL issue at most the fixed 12 Alchemy `getAccountInfo` reads: one PumpSwap pool and its two vaults at parent slot 429644638 and child slot 429644639, repeated once, with `finalized` commitment, base64 encoding and the explicit historical `slot` parameter. It SHALL retain complete raw response bytes and request identity, distinguish local/protocol/provider failures from valid responses, and compare paired-slot state with independently retained child-block facts. A matching context slot alone SHALL NOT count as proof of historical state or pool liquidity reconstruction.

#### Scenario: Matrix completes
- **WHEN** all 12 responses are available within budget
- **THEN** the evidence report SHALL distinguish repeat equality, account ownership/layout, mint/authority and exact raw amount changes by account and slot
- **AND** every unproved historical or pool-reconstruction claim SHALL remain explicitly unverified.

#### Scenario: Matrix stops early
- **WHEN** access denial, malformed response, budget stop or another terminal error interrupts the matrix
- **THEN** every requested, collected, incomplete and unexecuted cell SHALL retain an explicit status
- **AND** no missing cell SHALL be inferred from a current-state fallback or manufactured data.

#### Scenario: Offline reserve-input comparison
- **WHEN** the historical matrix is assessed alongside already retained SQD transaction evidence
- **THEN** the research report SHALL identify whether pre/post token-balance rows provide exact reserve inputs for the same pool/vault accounts and slots, with raw amounts, account indices, mint and owner where present
- **AND** absent, ambiguous or mismatched rows SHALL remain an explicit limitation rather than a reconstructed reserve.

### Requirement: Honest research disposition

The two experiments SHALL produce a dated evidence note that separates observed sample support, failure, inconclusive results and unverified capabilities. It SHALL preserve the accepted provider-selection barrier: no limited live/replay or historical-state sample SHALL by itself select a production primary, complete F1/F3, authorize mass recording or certify interval completeness.

The note SHALL include a provider-independent recovery design using provider replay and SQD/backfill with source-aware provenance and explicit gap closure, plus a measured-traffic decision rule: below 30 GB/day favors Alchemy as a provisional candidate; 30–55 GB/day requires a separately authorized 6–24-hour LIVE measurement; above 55 GB/day favors Chainstack economically, subject to a confirmed recovery design. These thresholds SHALL NOT substitute for Chainstack testing, provider entitlement, actual cost or verified-primary selection.

#### Scenario: Partial evidence
- **WHEN** either probe stops before its acceptance target or lacks independent corroboration
- **THEN** its report SHALL identify the exact completed checks, missing checks, counters and retained receipt locations
- **AND** the provider decision SHALL retain the missing capability as unverified.

#### Scenario: Traffic tier directs the next investigation
- **WHEN** a measured clean-hour rate is projected to a daily traffic tier
- **THEN** the note SHALL state the tier, projection formula, Alchemy/Chainstack comparison limits and the corresponding next validation step
- **AND** it SHALL not report an untested provider or recovery path as verified.
