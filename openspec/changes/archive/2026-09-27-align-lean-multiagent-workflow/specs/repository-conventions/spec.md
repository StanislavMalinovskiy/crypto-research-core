## MODIFIED Requirements

### Requirement: Executable agent guidance
The repository SHALL provide concise common guidance for source responsibilities, required reading, architecture, OpenSpec, testing, review and verification, plus mode-specific workflow guidance loaded only for the selected mode.

#### Scenario: New agent onboarding
- **WHEN** an agent begins a change from the repository root
- **THEN** it SHALL identify required reading, affected module documentation, Definition of Done and verification commands
- **AND** it SHALL determine DEFAULT or MULTIAGENT before delegating work.

#### Scenario: Default guidance
- **WHEN** DEFAULT is selected
- **THEN** root guidance SHALL provide the concise DEFAULT responsibility and evidence contract in addition to common invariants
- **AND** it SHALL NOT require specialized MULTIAGENT roles, phases, statuses, manifests, telemetry or repair routing.

#### Scenario: Multiagent guidance
- **WHEN** MULTIAGENT is selected
- **THEN** the agent SHALL load its separate workflow document and applicable role configuration
- **AND** Main alone SHALL spawn project subagents using native Codex or coordinate user-created sessions through `codex queue`
- **AND** Orca SHALL require an explicit user request in the current task
- **AND** this protocol SHALL NOT authorize implicit subagents in DEFAULT.

#### Scenario: Independent implementation and test ownership
- **WHEN** implementation changes observable behavior in MULTIAGENT
- **THEN** Builder SHALL derive meaningful tests from the active requirement and observe targeted behavioral RED before implementation
- **AND** the named test SHALL execute and fail at its expected behavioral assertion
- **AND** compilation, discovery, configuration, startup or infrastructure failure SHALL NOT count as RED
- **AND** Builder SHALL capture test paths, content hash, exact command, failing assertion and pre-implementation diff, then implement and run targeted GREEN.

#### Scenario: Frozen behavioral evidence
- **WHEN** Builder establishes valid RED
- **THEN** the establishing tests, expectations, fixtures, discovery and runtime configuration SHALL remain frozen through GREEN
- **AND** a confirmed `TEST_SPEC_ERROR` SHALL require current-reviewer `REPAIR` with `requires_new_red = true`, new behavioral RED and a new hash
- **AND** production behavior SHALL NOT recognize test artifacts to satisfy expectations.

#### Scenario: Suspected test defect
- **WHEN** Builder identifies a conflict between a failing test and the active specification
- **THEN** Builder SHALL preserve the test and report the exact conflict to the current reviewer
- **AND** that reviewer SHALL authorize a confirmed test correction or return an unresolved technical dispute for escalation
- **AND** semantic contract changes SHALL return to Architect with `reason = CONTRACT_CHANGED` before review reopens.

#### Scenario: No behavioral test needed
- **WHEN** Architect records `RED_NOT_REQUIRED` and a concrete reason
- **THEN** the author SHALL record applicable existing verification
- **AND** no artificial failure or pre-implementation diff SHALL be created merely as process evidence.

#### Scenario: Independent review ownership
- **WHEN** a MULTIAGENT task is ready for review
- **THEN** the following routing rule SHALL apply, with either fresh-review condition taking precedence over the first condition:

- ROUTINE / STANDARD implementation or non-normative docs → same Architect thread.
- Any CORE_RISK change → fresh Reviewer (new thread).
- Any change to an accepted normative OpenSpec spec → fresh Reviewer, regardless of risk.

- **AND** documentation-only work SHALL stay with Architect and skip Builder
- **AND** the reviewing role SHALL inspect the stable diff, contract, tests, evidence and applicable invariants before one consolidated `APPROVE`, `REPAIR`, `ESCALATE` or `BLOCKED` verdict.

#### Scenario: Bounded repair and escalation
- **WHEN** the current reviewer identifies repairable defects
- **THEN** Main SHALL route implementation or test repairs to the same Builder, followed by `BUILD_DONE` and review
- **AND** documentation repairs SHALL return to the same Architect, followed by review
- **AND** the task SHALL allow two ordinary repairs and a third only when the current reviewer explicitly sets `third_repair_authorized = true`
- **AND** repair SHALL NOT recompute risk, reset the repair budget or introduce a fourth ordinary repair.

#### Scenario: Role status vocabulary
- **WHEN** a project subagent completes an assignment
- **THEN** it SHALL return one role/phase-compatible state from `PLAN_READY`, `BUILD_DONE`, `REPAIR`, `APPROVE`, `BLOCKED`, `ESCALATE`, with final `DONE` owned by Main
- **AND** reason, subreason and escalation verdict SHALL remain attributes rather than additional workflow states
- **AND** a missing, unknown or incompatible status SHALL receive one protocol correction without consuming an artifact repair
- **AND** assignment logging SHALL accept the explicit Luna role names and authorized Architect review, documentation repair and archive phases.

#### Scenario: Verification ownership
- **WHEN** Builder completes implementation
- **THEN** Builder SHALL return `BUILD_DONE` with targeted GREEN and compact evidence
- **AND** Main SHALL rerun claimed RED only when the current reviewer sets `red_suspect = true`
- **AND** after `APPROVE`, Architect SHALL close non-semantic documentation and tasks without archiving
- **AND** Main SHALL independently run test-integrity preflight, complete Maven verification, strict all-item OpenSpec validation, doctor and diff checking before archive
- **AND** a failed required check SHALL block completion without narrowing or skipping it.

### Requirement: GPT-6 agent model routing
The project SHALL configure Main as `gpt-6-sol / medium` and SHALL use deterministic GPT-6 role routing in MULTIAGENT without changing the manually selected workflow mode.

#### Scenario: Fixed role mapping
- **WHEN** Main dispatches a non-Builder project role
- **THEN** Architect SHALL use `gpt-6-sol / high`
- **AND** fresh Reviewer SHALL use `gpt-6-sol / medium`
- **AND** fresh Escalation SHALL use `gpt-6-sol / high`.

#### Scenario: Risk-based Builder selection
- **WHEN** Architect returns `PLAN_READY` for implementation
- **THEN** ROUTINE SHALL use `builder_luna_xhigh` with `gpt-6-luna / xhigh`
- **AND** STANDARD SHALL use `builder_luna_max` with `gpt-6-luna / max`
- **AND** CORE_RISK SHALL use `builder_sol` with `gpt-6-sol / medium`
- **AND** separate role files SHALL pin the two Luna efforts.

#### Scenario: Current guidance consistency
- **WHEN** active routing guidance is verified
- **THEN** root guidance, normative workflow documents, configuration and roles SHALL agree on current routing and review ownership
- **AND** executable checks SHALL reject `builder_terra` and `gpt-5.6-terra` only in those active locations
- **AND** historical notes, archives and benchmarks SHALL remain outside that obsolete-routing scan
- **AND** accepted main specs SHALL be synchronized only by the verified archive operation.

## ADDED Requirements

### Requirement: Bounded technical escalation
MULTIAGENT technical escalation SHALL use a fresh read-only role, return one bounded verdict and preserve author ownership and the existing repair budget.

#### Scenario: Bounded technical escalation
- **WHEN** a technical dispute has no bounded safe resolution or survives the authorized repair budget
- **THEN** Main SHALL dispatch a fresh read-only Sol High Escalation agent for one exact question
- **AND** it SHALL return exactly one `verdict` attribute from `REPAIR`, `REPLAN`, `APPROVE`, `OWNER_DECISION`
- **AND** `REPAIR` SHALL use the existing author and remaining repair budget, `REPLAN` SHALL reopen Architect planning with `reason = CONTRACT_CHANGED` and `subreason = ESCALATION_REPLAN`, `APPROVE` SHALL resume approval closure, and `OWNER_DECISION` SHALL return a blocked owner decision
- **AND** owner intent or scope ambiguity SHALL go directly to the owner without technical escalation.

### Requirement: Architect-owned stable risk classification
Architect alone SHALL classify task risk from the single authoritative CORE_RISK trigger list in `docs/AGENT_WORKFLOW_MULTIAGENT.md`; every other active guidance location SHALL reference that list instead of defining a duplicate. Main SHALL own intake, scope coordination, routing, the full gate and DONE without classifying risk.

#### Scenario: Risk fixed at planning
- **WHEN** Architect returns `PLAN_READY`
- **THEN** the capsule SHALL fix `risk`, matched `risk_triggers` or explicit `none`, applicable invariants, test mode and bounded change budget
- **AND** risk SHALL remain fixed during implementation, review and repairs
- **AND** lowering a previously fixed risk SHALL require explicit owner direction.

#### Scenario: New scope or risk trigger
- **WHEN** an author or reviewer discovers a new risk or scope trigger
- **THEN** Main SHALL return it to Architect with `reason = CONTRACT_CHANGED` and `subreason = RISK_CHANGED`
- **AND** Architect SHALL issue a new `PLAN_READY` before further dependent implementation
- **AND** Main SHALL select a new Builder when the new risk requires one, preserving task repair accounting.

### Requirement: Verified and recoverable archive closure
Main and Architect SHALL close approved changes through full verification, a scoped pre-archive Git checkpoint, Architect archive and post-archive checks before Main returns DONE.

#### Scenario: Gate failure ownership
- **WHEN** a complete final gate fails
- **THEN** a Builder-attributable failure SHALL return to that Builder within the existing repair budget
- **AND** a contract or specification defect SHALL return to Architect planning with `reason = CONTRACT_CHANGED`, while a documentation defect SHALL return to Architect repair
- **AND** infrastructure and unrelated pre-existing failures SHALL return `BLOCKED`; infrastructure retries SHALL NOT consume implementation repairs.

#### Scenario: Successful archive closure
- **WHEN** approval closure and the complete final gate pass
- **THEN** Main SHALL record a scoped Git checkpoint and before-archive porcelain status without staging or committing unrelated owner changes
- **AND** Architect SHALL archive only the authorized change using the OpenSpec CLI
- **AND** Main SHALL inspect the exact archive diff and rerun strict all-item validation, doctor, targeted repository conventions and diff checking
- **AND** Main SHALL return DONE only after every post-archive check passes.

#### Scenario: Archive restoration
- **WHEN** a post-archive check fails
- **THEN** only exact paths changed by that archive SHALL be restored from the recorded checkpoint and only that new archive copy SHALL be removed
- **AND** active-change bytes, accepted-spec bytes, unrelated owner work and the real Git index SHALL be preserved
- **AND** destructive broad reset, whole-tree checkout, clean, owner-work stash and manual reversal of accepted-spec patches SHALL NOT be used
- **AND** the task SHALL return to Architect for the active-change correction, full verification and archive again without resetting repair accounting
- **AND** inability to prove scoped restoration safe SHALL return `BLOCKED` before destructive action.
