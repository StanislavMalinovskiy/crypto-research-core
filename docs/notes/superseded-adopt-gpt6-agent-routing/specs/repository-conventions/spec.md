## MODIFIED Requirements

### Requirement: Executable agent guidance
The repository SHALL provide concise common guidance for source responsibilities, required reading, architecture, OpenSpec, testing, review and verification, plus mode-specific workflow guidance loaded only for the selected mode.

#### Scenario: New agent onboarding
- **WHEN** an agent begins a change from the repository root
- **THEN** it SHALL identify the required reading order, affected module documentation, Definition of Done and verification commands
- **AND** it SHALL determine DEFAULT or MULTIAGENT before delegating work.

#### Scenario: Default guidance
- **WHEN** DEFAULT is selected
- **THEN** root guidance SHALL provide only the concise DEFAULT responsibility and evidence contract in addition to common project invariants
- **AND** it SHALL NOT require the specialized MULTIAGENT role, phase, status, manifest, telemetry or repair-routing protocol.

#### Scenario: Multiagent guidance
- **WHEN** MULTIAGENT is selected
- **THEN** the agent SHALL load the separate MULTIAGENT workflow document and applicable role configuration
- **AND** the supervised protocol SHALL remain unavailable as implicit permission to spawn roles in DEFAULT
- **AND** Main alone SHALL spawn project subagents using native Codex, or coordinate user-created Codex sessions through `codex queue`
- **AND** Orca SHALL require an explicit user request in the current task.

#### Scenario: Independent implementation and test ownership
- **WHEN** an implementation change requires new observable behavior in MULTIAGENT
- **THEN** Builder SHALL derive meaningful tests from the active requirement and observe targeted behavioral RED before implementation
- **AND** the named test SHALL execute and fail at the expected behavioral assertion
- **AND** compilation, discovery, configuration, startup or infrastructure failure SHALL NOT count as RED
- **AND** Builder SHALL record the test paths and hash, exact command, failing assertion and pre-implementation diff before implementing and running targeted GREEN.

#### Scenario: Frozen behavioral evidence
- **WHEN** Builder has established valid RED
- **THEN** Builder SHALL preserve the establishing tests, expectations, fixtures, discovery and runtime configuration through GREEN
- **AND** a necessary test correction SHALL require Reviewer `REPAIR` with `requires_new_red = true` followed by new RED evidence and a new hash
- **AND** production behavior SHALL NOT recognize a test artifact to satisfy an expectation.

#### Scenario: Suspected test defect
- **WHEN** Builder identifies a conflict between a failing test and the active specification
- **THEN** Builder SHALL preserve the test and report the exact conflict to Reviewer
- **AND** Reviewer SHALL return a consolidated `REPAIR` with `requires_new_red = true` for a confirmed test correction, or `ESCALATE` or `BLOCKED` for an unresolved contract conflict
- **AND** semantic contract changes SHALL reopen Architect planning and the applicable review.

#### Scenario: No behavioral test needed
- **WHEN** Architect classifies the change as `RED_NOT_REQUIRED` with a concrete reason
- **THEN** Builder SHALL record that reason and applicable existing verification
- **AND** it SHALL NOT create an artificial failure or pre-implementation diff solely for process evidence.

#### Scenario: Independent review ownership
- **WHEN** a MULTIAGENT task is ready for review at any risk level
- **THEN** Main SHALL start a fresh Reviewer thread separate from Architect and Builder
- **AND** Reviewer SHALL inspect the stable diff, contract, test meaning, evidence and applicable invariants before returning `APPROVE`, `REPAIR`, `ESCALATE` or `BLOCKED`
- **AND** Architect SHALL perform PLAN and DOCS_CLOSE only
- **AND** documentation-only work SHALL skip Builder and still receive Reviewer review.

#### Scenario: Bounded repair and escalation
- **WHEN** Reviewer identifies repairable defects
- **THEN** Builder MAY perform two ordinary repair rounds and exactly one further round only with Reviewer `third_repair_authorized = true`
- **AND** each repair SHALL receive review
- **AND** an unresolved blocker after round three SHALL escalate without another ordinary repair
- **AND** Main SHALL use the read-only Escalation role only for a bounded unresolved contract, invariant, data-loss, transaction, concurrency, migration, security or architecture question, or disagreement on a release blocker.

#### Scenario: Role status vocabulary
- **WHEN** a project subagent finishes a MULTIAGENT assignment
- **THEN** it SHALL return exactly one role/phase-compatible status from `PLAN_READY`, `BUILD_DONE`, `REPAIR`, `APPROVE`, `BLOCKED` or `ESCALATE`
- **AND** Main SHALL own final `DONE`
- **AND** an unknown, missing or role/phase-incompatible status SHALL receive one protocol correction without consuming an artifact repair
- **AND** assignment logging SHALL accept the current Builder role names and SHALL reject an Architect REVIEW dispatch.

#### Scenario: Verification ownership
- **WHEN** Builder completes a MULTIAGENT implementation pass
- **THEN** Builder SHALL run targeted GREEN and return `BUILD_DONE` with compact evidence
- **AND** Main SHALL rerun claimed RED only when Reviewer marks `red_suspect = true`
- **AND** after Reviewer approval Architect SHALL close documentation without changing contract semantics
- **AND** Main SHALL independently run the complete final gate before `DONE`, with test-integrity preflight before Maven
- **AND** a nonzero required check SHALL block completion without narrowing or skipping the check.

## ADDED Requirements

### Requirement: GPT-6 agent model routing
The project SHALL configure its default model as `gpt-6-sol` with `medium` reasoning and SHALL use the declared GPT-6 role mapping in MULTIAGENT without changing the task's manually selected workflow mode.

#### Scenario: Fixed role mapping
- **WHEN** Main dispatches a non-Builder project role
- **THEN** Architect SHALL use `gpt-6-sol / high`
- **AND** Reviewer SHALL use `gpt-6-sol / medium`
- **AND** Escalation SHALL use `gpt-6-sol / high`.

#### Scenario: Risk-based Builder selection
- **WHEN** Architect returns a testable plan with effective risk
- **THEN** ROUTINE and STANDARD implementation SHALL default to `builder_luna` using `gpt-6-luna / xhigh`
- **AND** Main MAY explicitly select `builder_luna_max` using `gpt-6-luna / max` and record the reason in the capsule
- **AND** CORE_RISK implementation SHALL use `builder_sol` with `gpt-6-sol / medium`
- **AND** Architect MAY upgrade Main's preliminary risk but SHALL NOT downgrade it.

#### Scenario: Current guidance consistency
- **WHEN** the routing migration is completed
- **THEN** current project configuration, roles, executable policy checks and workflow documentation SHALL agree on the GPT-6 mapping and separate Reviewer ownership
- **AND** old-model benchmark results SHALL NOT be presented as current routing evidence
- **AND** application regression coverage SHALL remain intact.
