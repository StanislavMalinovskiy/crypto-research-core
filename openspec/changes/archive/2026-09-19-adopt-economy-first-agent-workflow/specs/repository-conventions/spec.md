## ADDED Requirements

### Requirement: Agent workflow mode selection
The repository SHALL use `DEFAULT` unless the user has manually enabled project subagents, and SHALL use `MULTIAGENT` only after that explicit opt-in.

#### Scenario: Default mode
- **WHEN** a task starts and `[agents].enabled` is `false`, missing or invalid
- **THEN** the task SHALL use `DEFAULT`
- **AND** no project subagent SHALL be spawned.

#### Scenario: Multiagent mode
- **WHEN** a task starts and the user has manually set `[agents].enabled = true`
- **THEN** the task SHALL use `MULTIAGENT`
- **AND** the supervised project roles MAY be used as needed.

#### Scenario: No automatic mode change
- **WHEN** an agent considers that MULTIAGENT would provide additional assurance
- **THEN** it MAY recommend that mode and explain why
- **AND** it SHALL NOT edit `[agents].enabled`, spawn a project subagent or silently change the current task's mode.

### Requirement: Default development workflow
The DEFAULT workflow SHALL use one top-level Control session and one top-level Developer session without the specialized MULTIAGENT phase and status protocol.

#### Scenario: Default responsibility split
- **WHEN** work is performed in DEFAULT
- **THEN** Control SHALL own the active contract, final documentation, stable-diff review and completion decision
- **AND** Developer SHALL own tests and implementation in one bounded pass
- **AND** neither session SHALL be required to use MULTIAGENT statuses, task capsules, phase manifests, threat checks, assignment telemetry or role-routing logs.

#### Scenario: Changed behavior
- **WHEN** DEFAULT work adds or changes observable behavior
- **THEN** Developer SHALL derive meaningful tests from the active requirement and observe a targeted behavioral red before implementing
- **AND** compilation, discovery, configuration or infrastructure failure SHALL NOT count as red
- **AND** after red Developer SHALL NOT weaken, disable, skip or narrow the test or add production behavior that exists only for a test artifact.

#### Scenario: No new behavioral test required
- **WHEN** work changes only documentation, comments, formatting, mechanical configuration, a pure rename or an internally covered refactoring
- **THEN** Developer MAY record that no new behavioral test is needed
- **AND** existing applicable checks and the complete completion gate SHALL still run.

#### Scenario: Completion and handoff
- **WHEN** Developer finishes a DEFAULT pass
- **THEN** Developer SHALL run relevant targeted green checks and the complete local gate
- **AND** the handoff SHALL identify changed files, red and green evidence when applicable, verification results and remaining risks
- **AND** Control SHALL review test meaning before implementation details and independently run the required completion gates.

#### Scenario: Bounded repair
- **WHEN** Control finds repairable defects after the first DEFAULT pass
- **THEN** it SHALL consolidate them into one repair handoff by default
- **AND** any further repair handoff SHALL require the user's explicit decision.

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
- **AND** the supervised protocol SHALL remain unavailable as implicit permission to spawn roles in DEFAULT.

#### Scenario: Independent implementation and test ownership
- **WHEN** an implementation change requires new observable behavior in MULTIAGENT
- **THEN** Developer SHALL create any required API skeleton before Tester derives tests from the specification
- **AND** Developer SHALL not create, change, disable, exclude or otherwise narrow tests, fixtures, expected results or test configuration
- **AND** after Architect accepts red, Tester SHALL not change tests, fixtures, expectations or test configuration unless Reviewer returns `TEST_WRONG` and Architect starts a new `tests-red` phase, or an `AUDIT_FAILED` verdict explicitly assigns missing test evidence and Architect starts a distinct `tests-evidence` phase
- **AND** Developer SHALL not add production behavior that exists only for a particular test input.

#### Scenario: Suspected test defect
- **WHEN** Developer in MULTIAGENT concludes that a failing test contradicts an exact specification statement
- **THEN** Developer SHALL return `TEST_SUSPECT` without changing or bypassing the test
- **AND** only Reviewer in `ADJUDICATE` mode SHALL classify the conflict as `CODE_WRONG`, `TEST_WRONG` or `SPEC_AMBIGUOUS`.

#### Scenario: Role status vocabulary
- **WHEN** a project subagent finishes a MULTIAGENT assignment
- **THEN** it SHALL return exactly one status allowed for its role and assigned mode in the canonical routing contract
- **AND** Researcher SHALL return `RESEARCH_DONE`, `INCONCLUSIVE` or `BLOCKED`
- **AND** Tester SHALL use `EVIDENCE_CANDIDATE` only in an Architect-opened post-audit test-evidence repair phase
- **AND** an unknown, missing or mode-incompatible status SHALL be treated as a protocol error rather than inferred by Architect
- **AND** Reviewer in `THREAT_CHECK` SHALL return only `THREAT_CHECK_PASSED` or `THREATS_FOUND`.

#### Scenario: Verification ownership
- **WHEN** Developer checks an implementation pass in MULTIAGENT
- **THEN** Developer SHALL run only the targeted Surefire or Failsafe tests needed for that pass
- **AND** Architect SHALL own the red command, writer-phase manifest check and complete Maven verification lifecycle.

## REMOVED Requirements

### Requirement: Agent phase integrity
**Reason**: Its phase-manifest and independently routed red/green protocol is specialized MULTIAGENT orchestration, not a universal requirement for ordinary DEFAULT work.

**Migration**: Preserve the existing detailed behavior in `docs/AGENT_WORKFLOW_MULTIAGENT.md` and project role configuration, and replace its DEFAULT coverage with the lightweight test-first evidence and final review requirements above.

### Requirement: Closed agent routing protocol
**Reason**: Closed role statuses, binding subagent verdicts, telemetry and the three-round routing budget apply only when project subagents are explicitly enabled.

**Migration**: Keep the existing protocol in `docs/AGENT_WORKFLOW_MULTIAGENT.md` for `[agents].enabled = true`; DEFAULT uses two top-level sessions and one consolidated repair by default without role statuses.
