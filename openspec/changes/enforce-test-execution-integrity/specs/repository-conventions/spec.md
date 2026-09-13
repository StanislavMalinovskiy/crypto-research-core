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

## MODIFIED Requirements

### Requirement: Executable agent guidance
The repository SHALL provide concise root and role-specific guidance that defines source responsibilities, conflict resolution, required reading, architectural constraints, OpenSpec workflow, review rules, verification commands and bounded repair routing.

#### Scenario: New agent onboarding
- **WHEN** an agent begins a code change from the repository root
- **THEN** it SHALL be able to identify the required reading order and affected module documentation
- **AND** it SHALL have explicit Definition of Done and verification commands.

#### Scenario: Independent implementation and test ownership
- **WHEN** an implementation change requires new observable behavior
- **THEN** the Developer SHALL create any required API skeleton before the Tester derives tests from the specification
- **AND** the Developer SHALL not create, change, disable, exclude or otherwise narrow tests, fixtures, expected results or test configuration
- **AND** the Developer SHALL not add production behavior that exists only for a particular test input.

#### Scenario: Suspected test defect
- **WHEN** the Developer concludes that a failing test contradicts an exact specification statement
- **THEN** the Developer SHALL return `TEST_SUSPECT` without changing or bypassing the test
- **AND** only the Reviewer SHALL classify the conflict as `CODE_WRONG`, `TEST_WRONG` or `SPEC_AMBIGUOUS`.

#### Scenario: Bounded repair routing
- **WHEN** review or verification returns a repairable failure
- **THEN** the Architect SHALL own one shared repair-round counter for that task
- **AND** the task SHALL be escalated after three unsuccessful repair routings rather than starting another autonomous repair round.

#### Scenario: Verification ownership
- **WHEN** the Developer checks an implementation pass
- **THEN** it SHALL run only the targeted Surefire or Failsafe tests needed for that pass
- **AND** the Tester, or the Architect when no Tester is assigned, SHALL own the complete Maven verification lifecycle.
