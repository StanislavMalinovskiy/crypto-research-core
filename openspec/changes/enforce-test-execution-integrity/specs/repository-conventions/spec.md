## ADDED Requirements

### Requirement: Test execution integrity
Repository verification SHALL reject committed configuration and Java test-source constructs that mechanically disable, ignore, exclude, retag or narrow the test suite required by the default Maven verification lifecycle.

#### Scenario: Explicitly disabled Java test
- **WHEN** repository convention checks inspect Java test sources
- **THEN** an explicitly disabled or ignored test SHALL fail verification
- **AND** an unconditional literal false assumption used to prevent test execution SHALL fail verification.

#### Scenario: Maven test-selection bypass
- **WHEN** repository convention checks inspect the effective project Maven configuration, including profiles
- **THEN** settings that skip tests or configure Surefire or Failsafe to include, exclude, retag or select only part of the default suite SHALL fail verification
- **AND** unrelated plugin configuration and explicitly targeted local developer commands SHALL remain unaffected.

#### Scenario: Quality-gate test-selection bypass
- **WHEN** repository convention checks inspect the required quality-gate workflow
- **THEN** its Maven verification command SHALL remain the complete clean verification lifecycle
- **AND** committed flags, properties or environment settings that skip or narrow that lifecycle SHALL fail verification.

#### Scenario: Mechanical scope of the guard
- **WHEN** a test expectation or test-specific configuration is semantically weakened without using a recognized disabling or selection mechanism
- **THEN** the mechanical convention gate is not required to infer the intent of that change
- **AND** independent review SHALL remain responsible for detecting the semantic weakening.

#### Scenario: Guard cannot disable itself
- **WHEN** the required repository verification sequence starts
- **THEN** test-integrity inspection SHALL execute outside and before the Maven lifecycle whose configuration it inspects
- **AND** a detected bypass SHALL stop verification before `clean verify`
- **AND** the stable quality-gate job SHALL run the same independent preflight before Maven without a conditional or continue-on-error escape.

### Requirement: Agent phase integrity
The project agent workflow SHALL establish independent, mechanically observed red and green gates and SHALL reject writer-phase changes outside the paths explicitly assigned by Architect.

#### Scenario: Independent red gate
- **WHEN** Tester has authored specification-derived tests and returns a red candidate
- **THEN** Tester SHALL identify the exact command, requirement, scenario, test method and expected failing assertion
- **AND** Architect SHALL run that command against unchanged implementation sources
- **AND** Architect SHALL accept red only when the named test executes and fails for the expected behavioral assertion rather than compilation, infrastructure, discovery or configuration failure.

#### Scenario: Frozen writer phase
- **WHEN** Architect accepts the red gate and assigns implementation to Developer
- **THEN** Architect SHALL record a deterministic manifest containing every relevant repository-relative path and its content hash, including untracked files
- **AND** after the assignment Architect SHALL reject every addition, modification or deletion outside the positive path allowlist in the task capsule
- **AND** generated build output and lifecycle logs MAY be excluded only by exact repository-relative location using the platform's path case semantics.

#### Scenario: Independent green gate
- **WHEN** Developer returns an implementation result
- **THEN** Architect SHALL first verify writer-phase integrity
- **AND** Architect SHALL run the relevant targeted tests and the complete Maven verification lifecycle
- **AND** no Tester verdict SHALL substitute for either mechanical result.

#### Scenario: Green test evidence after audit
- **WHEN** `AUDIT_FAILED` explicitly identifies missing test evidence and the existing implementation already satisfies the requirement
- **THEN** Architect MAY open a new test-evidence repair phase for Tester
- **AND** Tester SHALL return `EVIDENCE_CANDIDATE` with the same requirement/scenario/test/assertion traceability as a red candidate
- **AND** Architect SHALL accept it only after independently observing the targeted command pass and verifying that no implementation path changed.

### Requirement: Closed agent routing protocol
The project agent workflow SHALL use parent-selected phases, closed role-specific status sets, binding Reviewer verdicts and one bounded repair budget.

#### Scenario: Reviewer adjudication
- **WHEN** Architect assigns Reviewer the `ADJUDICATE` mode for an exact code, test and specification conflict
- **THEN** Reviewer SHALL return exactly one of `CODE_WRONG`, `TEST_WRONG` or `SPEC_AMBIGUOUS`
- **AND** unrelated audit findings SHALL not preempt that adjudication.

#### Scenario: Stable-diff audit
- **WHEN** Architect assigns Reviewer the `AUDIT` mode after mechanical gates pass
- **THEN** Reviewer SHALL return exactly one of `AUDIT_FAILED` or `APPROVE`
- **AND** Reviewer SHALL remain mechanically read-only.

#### Scenario: Binding review verdict
- **WHEN** Reviewer returns a protocol-valid verdict in the assigned mode
- **THEN** Architect SHALL route that verdict without replacing it with another substantive verdict
- **AND** disagreement SHALL be escalated to the user
- **AND** `APPROVE` SHALL remain necessary but insufficient when an independent mechanical or Definition of Done gate has failed.

#### Scenario: Incomplete specification
- **WHEN** Tester cannot derive a testable contract from the active change and returns `SPEC_INCOMPLETE`
- **THEN** Architect SHALL cite an unambiguous answer from accepted project sources or escalate the missing product decision to the user
- **AND** every autonomous correction routing SHALL consume the shared repair budget
- **AND** a repeated unresolved result or exhausted budget SHALL be escalated instead of looping.

#### Scenario: Bounded and observable repair routing
- **WHEN** review or verification returns a repairable failure
- **THEN** Architect SHALL own one task-level counter capped at three autonomous repair routings
- **AND** each routing SHALL record the loop, source status, repair owner and round number
- **AND** protocol or infrastructure retries that do not request an artifact repair SHALL not silently consume or reset the budget.

#### Scenario: Deterministic project-agent depth
- **WHEN** Architect starts a project Developer, Tester, Reviewer or Researcher
- **THEN** the effective reasoning mode SHALL not be `ultra`
- **AND** project subagents SHALL not create nested subagents
- **AND** Tester SHALL use high reasoning for the behavioral work for which that role is required.

#### Scenario: Isolated project-role context
- **WHEN** Architect starts a new project Developer, Tester, Reviewer or Researcher
- **THEN** the role SHALL be created with `fork_turns: "none"`
- **AND** Architect SHALL provide a self-contained 200-400 word task capsule naming goal, phase, writable paths, frozen paths, requirement/scenario identifiers, files to read, acceptance checks and expected status
- **AND** parent conversation history and other role reports SHALL NOT be inherited by that new role.

#### Scenario: Early adversarial review
- **WHEN** an active change affects CI, security/integrity controls or the project agent workflow
- **THEN** Architect SHALL assign Reviewer `THREAT_CHECK` before the initial test-writing or implementation phase
- **AND** Reviewer SHALL inspect only guard self-bypass, additions/deletions and path case, CI control flow, quoted or folded configuration, initially green tests and phase/status conflicts
- **AND** Reviewer SHALL return exactly one of `THREAT_CHECK_PASSED` or `THREATS_FOUND`.

#### Scenario: Role and phase telemetry
- **WHEN** Architect dispatches and receives a logical project-role assignment
- **THEN** the machine log SHALL record assignment id, role, phase, start/end time, duration, command summary, returned status and result summary
- **AND** available runtime token counts SHALL be recorded by role and phase
- **AND** token values absent from the runtime SHALL be marked `unavailable` rather than estimated.

#### Scenario: Human-readable subagent activity
- **WHEN** a logical assignment completes
- **THEN** a separate local readable log SHALL contain one line with local start time formatted `dd-MM-yy HH:mm`, Architect's command, the role's returned status and summary, phase, duration and token fields
- **AND** the machine JSONL SHALL remain available as the structured source
- **AND** full prompts, responses and transcript paths SHALL NOT be copied into either assignment record.

## MODIFIED Requirements

### Requirement: Executable agent guidance
The repository SHALL provide concise root and role-specific guidance that defines source responsibilities, conflict resolution, required reading, architectural constraints, OpenSpec workflow, review rules, verification commands, phase integrity and bounded repair routing.

#### Scenario: New agent onboarding
- **WHEN** an agent begins a code change from the repository root
- **THEN** it SHALL be able to identify the required reading order and affected module documentation
- **AND** it SHALL have explicit Definition of Done and verification commands.

#### Scenario: Independent implementation and test ownership
- **WHEN** an implementation change requires new observable behavior
- **THEN** Developer SHALL create any required API skeleton before Tester derives tests from the specification
- **AND** Developer SHALL not create, change, disable, exclude or otherwise narrow tests, fixtures, expected results or test configuration
- **AND** after Architect accepts red, Tester SHALL not change tests, fixtures, expectations or test configuration unless Reviewer returns `TEST_WRONG` and Architect starts a new `tests-red` phase, or an `AUDIT_FAILED` verdict explicitly assigns missing test evidence and Architect starts a distinct `tests-evidence` phase
- **AND** Developer SHALL not add production behavior that exists only for a particular test input.

#### Scenario: Suspected test defect
- **WHEN** Developer concludes that a failing test contradicts an exact specification statement
- **THEN** Developer SHALL return `TEST_SUSPECT` without changing or bypassing the test
- **AND** only Reviewer in `ADJUDICATE` mode SHALL classify the conflict as `CODE_WRONG`, `TEST_WRONG` or `SPEC_AMBIGUOUS`.

#### Scenario: Role status vocabulary
- **WHEN** a project subagent finishes an assignment
- **THEN** it SHALL return exactly one status allowed for its role and assigned mode in the canonical routing contract
- **AND** Researcher SHALL return `RESEARCH_DONE`, `INCONCLUSIVE` or `BLOCKED`
- **AND** Tester SHALL use `EVIDENCE_CANDIDATE` only in an Architect-opened post-audit test-evidence repair phase
- **AND** an unknown, missing or mode-incompatible status SHALL be treated as a protocol error rather than inferred by Architect.
- **AND** Reviewer in `THREAT_CHECK` SHALL return only `THREAT_CHECK_PASSED` or `THREATS_FOUND`.

#### Scenario: Verification ownership
- **WHEN** Developer checks an implementation pass
- **THEN** Developer SHALL run only the targeted Surefire or Failsafe tests needed for that pass
- **AND** Architect SHALL own the red command, writer-phase manifest check and complete Maven verification lifecycle.
