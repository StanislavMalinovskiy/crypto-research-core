## MODIFIED Requirements

### Requirement: Stable required-check handoff
The repository SHALL expose a stable quality-gate job identity, document how repository administrators make it a required primary-branch check, and require remote evidence for the exact pushed checkpoint before an architecture stage that depends on this gate is declared complete. If the remote run or required-check setting cannot be verified or enforced, the stage SHALL remain current and the exact external blocker SHALL be reported.

#### Scenario: Branch protection configuration
- **WHEN** an administrator configures protection for the primary branch
- **THEN** the documented stable job identity SHALL be selectable as a required status check
- **AND** the documentation SHALL distinguish repository workflow configuration from the external GitHub setting.

#### Scenario: Close Stage 1 with remote evidence
- **WHEN** Stage 1 has a pushed checkpoint and is proposed as complete
- **THEN** the remote `quality-gate` run for that exact commit SHALL have a successful conclusion
- **AND** `quality-gate` SHALL be configured as a required status check for the primary branch
- **AND** the verified commit identity and repository setting SHALL be reported in the completion evidence.

#### Scenario: Remote gate cannot be confirmed
- **WHEN** access, credentials or platform support prevents verification or enforcement of the remote gate
- **THEN** Stage 1 SHALL remain `Current`
- **AND** the exact external blocker and the unverified condition SHALL be reported without weakening or bypassing the gate.
