## MODIFIED Requirements

### Requirement: Provider-independent normalized swap identity

The system SHALL normalize a supported recorded Solana swap into a provider-independent canonical event identified by exact chain, transaction value and canonical event locator, while retaining raw-observation identity, provider, transformation version, block position, source time, observation time and exact numeric market evidence as lineage. Existing event-key normalized records SHALL retain their historical identity, conflict and read behavior. New normalized facts SHALL additionally have immutable revision identities tied to source observation and derivation version, so distinct valid provider or transformation revisions for one canonical event coexist without changing its event locator. Equal replay of one revision SHALL be idempotent; differing immutable content for that same revision SHALL fail explicitly without overwrite.

#### Scenario: Normalize one recorded swap
- **WHEN** a supported raw swap payload is replayed
- **THEN** the normalized event SHALL preserve its chain-scoped asset and wallet identities, side, exact quantities, USD price, USD liquidity, venue and authoritative times
- **AND** its canonical event identity SHALL omit provider while its revision lineage SHALL retain the contributing provider and raw identity.

#### Scenario: Replay equal normalized evidence
- **WHEN** the same raw dataset and transformation version are replayed again
- **THEN** the normalized facts SHALL resolve to the same identities and values
- **AND** no duplicate normalized revision SHALL be created.

#### Scenario: Detect conflicting normalization
- **WHEN** one legacy normalized identity or one new revision identity is produced with immutable normalized evidence different from its existing value
- **THEN** replay SHALL fail that observation with an explicit conflict
- **AND** the existing normalized fact SHALL remain unchanged.

#### Scenario: Preserve another valid derivation
- **WHEN** the same canonical event is normalized from another provider observation or transformation version
- **THEN** the new valid revision SHALL remain addressable alongside the first
- **AND** an earlier snapshot SHALL continue to resolve the revision it selected.

## ADDED Requirements

### Requirement: Immutable revision-member evidence snapshots

The system SHALL finalize a versioned market-data snapshot under a declared scope of chain, assets, applicable pool/venue filters, event/observation windows, fact kinds, knowledge cutoff and versioned availability/exclusion policy. Within the same freeze transaction, it SHALL independently enumerate visible canonical fact keys in that scope and require each key to have exactly one selected admissible revision or exactly one explicit, policy-permitted exclusion with a reason and supporting immutable evidence. Omitted keys, duplicate or contradictory dispositions, out-of-scope members and unsupported exclusions SHALL reject finalization atomically. Completeness SHALL refer to visible stored facts within the declared scope, not unproven provider or chain-wide coverage. A valid snapshot SHALL contain at most 10,000 covered canonical keys, counting inclusions and exclusions; enumeration and writes SHALL use chunks of at most 1,000 and reject overflow without truncation. Each included member SHALL have been committed and visible to the transaction and satisfy the cutoff/availability policy. The snapshot SHALL persist the scope, ordered revision membership, ordered exclusions and evidence, coverage counts and a canonical fingerprint covering those values, selection/policy versions, cutoff, availability status and exact member content or its immutable digest. A caller SHALL be able to reuse one snapshot for multiple signals in a bounded decision batch. Reads by snapshot identity SHALL use its stored manifest, not recompute a mutable query or include a later commit. A verified realtime decision's knowledge cutoff SHALL be captured with the freeze and SHALL NOT be supplied retrospectively; an earlier modeled historical cutoff SHALL be explicitly labeled as modeled. The versioned snapshot SHALL not alter legacy dataset fingerprints or member readback.

#### Scenario: Reject an omitted visible fact
- **WHEN** an older and a newer liquidity fact are committed and visible inside the declared asset/window scope
- **AND** the caller supplies a revision for only the older fact and no valid exclusion for the newer canonical key
- **THEN** finalization SHALL reject the incomplete selection
- **AND** no snapshot header, members or exclusion manifest SHALL be published.

#### Scenario: Record a permitted exclusion
- **WHEN** every visible canonical fact key in scope has one selected admissible revision or one evidence-supported exclusion allowed by the declared policy
- **THEN** finalization SHALL persist the complete included/excluded coverage manifest atomically
- **AND** scope, policy, exclusion reasons/evidence and counts SHALL participate in the snapshot fingerprint and equal-retry comparison.

#### Scenario: Reject contradictory or unsupported coverage
- **WHEN** a visible canonical key is both included and excluded, has two selected revisions, or is excluded using a reason not justified by the policy and evidence
- **THEN** finalization SHALL reject the selection without partial publication
- **AND** a caller-preference reason SHALL NOT silently suppress an otherwise admissible newer observation.

#### Scenario: Freeze visible members before a late commit
- **WHEN** a fact write is uncommitted when decision-snapshot finalization selects members, then commits with an earlier observation or admission timestamp
- **THEN** that fact SHALL remain absent from all later reads by the frozen snapshot identity
- **AND** the snapshot fingerprint and ordered member list SHALL remain unchanged.

#### Scenario: Pin exact revisions and total order
- **WHEN** one canonical event has two valid revisions and the caller finalizes a snapshot selecting one
- **THEN** later reads SHALL return only that selected revision in canonical order
- **AND** changing the selected revision, member content, rule version or cutoff SHALL produce distinguishable lineage and fingerprint.

#### Scenario: Reject unavailable or unresolved members
- **WHEN** a requested revision is missing, uncommitted, outside the declared cutoff or has availability that the declared policy cannot admit
- **THEN** finalization SHALL fail without publishing a partial snapshot
- **AND** it SHALL not substitute another revision or fabricate an availability instant.

#### Scenario: Reject a retrospective realtime claim
- **WHEN** a caller requests a verified realtime decision selection using a knowledge cutoff earlier than its actual snapshot freeze
- **THEN** finalization SHALL reject that claim before publishing a snapshot
- **AND** a historical modeled selection SHALL remain distinguishable from verified realtime evidence.

#### Scenario: Enforce bounded finalization
- **WHEN** a versioned snapshot request names more than 10,000 references or exclusions, or the declared scope contains more than 10,000 visible canonical keys
- **THEN** finalization SHALL reject it without publishing a partial snapshot or narrowing the scope
- **AND** a valid 1,001-member request SHALL retain all members through bounded chunked writes.

#### Scenario: Equal finalization and concurrent conflict
- **WHEN** equal finalizations or concurrent attempts publish one snapshot identity
- **THEN** they SHALL resolve to one complete immutable snapshot when all canonical content is equal
- **AND** differing content for that identity SHALL fail explicitly without a partial member set.

#### Scenario: Preserve legacy snapshot readback
- **WHEN** a pre-migration dataset snapshot is read after the new revision tables are introduced
- **THEN** its original ordered members, exact observation values and fingerprint SHALL be returned unchanged
- **AND** the reader SHALL not infer a new revision or rehash it under the new canonicalization version.
