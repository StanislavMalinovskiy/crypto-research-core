## ADDED Requirements

### Requirement: Active-change overlap check before PLAN_READY
In MULTIAGENT, Architect SHALL run `openspec list` before PLAN_READY, inspect the affected specs of other active changes and record overlaps in the PLAN_READY handoff as `none` or each change with a disposition of `resolve first`, `safe to proceed` or `blocked`.

#### Scenario: Planning with other active changes
- **WHEN** Architect prepares a PLAN_READY handoff
- **THEN** Architect SHALL identify other active changes touching the same specs and record `none` or each overlapping change with its disposition
- **AND** Architect SHALL resolve a `resolve first` overlap before PLAN_READY and SHALL NOT issue PLAN_READY while an overlap is `blocked`.

### Requirement: Verified RED reason before test freeze
In MULTIAGENT, Builder SHALL freeze tests only after verifying that each failing test fails for the requirement-derived reason. Builder SHALL record the requirement/acceptance-criterion, expected and actual result for each failing test in the RED evidence before freeze and repeat that evidence in BUILD_DONE. Before freeze, a wrong target, wrong assertion or setup error SHALL be corrected and RED rerun without reviewer permission; this pre-freeze correction is the exception to the suspected-test-defect referral, not permission to change the contract. After freeze, the existing TEST_SPEC_ERROR rule SHALL remain unchanged.

#### Scenario: Establishing valid RED before freezing tests
- **WHEN** Builder inspects a failing test before freeze
- **THEN** Builder SHALL verify and record its requirement/acceptance-criterion, expected and actual result before accepting RED and freezing the test
- **AND** a failure unrelated to the requirement SHALL be corrected and RED rerun before freeze without reviewer permission
- **AND** BUILD_DONE SHALL repeat the per-test RED reason evidence, while any post-freeze correction SHALL follow the existing TEST_SPEC_ERROR rule.

### Requirement: Lost Builder or Reviewer session recovery
In MULTIAGENT, if the same Builder or Reviewer session cannot be resumed, Main SHALL start a fresh session of the same role and configured model/effort, pass the contract, current diff, RED/GREEN evidence, open review items and remaining repair budget, preserve the repair count and disclose the session replacement in the handoff. Session loss alone SHALL NOT require an owner decision.

#### Scenario: Resuming work after session loss
- **WHEN** Main cannot resume the assigned Builder or Reviewer session
- **THEN** Main SHALL replace it with a fresh session of the same role and configured model/effort and supply the contract, current diff, RED/GREEN evidence, open review items and remaining repair budget
- **AND** the repair count and Reviewer independence SHALL be preserved and the handoff SHALL state that the session was replaced
- **AND** session loss alone SHALL NOT be routed to an owner decision.
