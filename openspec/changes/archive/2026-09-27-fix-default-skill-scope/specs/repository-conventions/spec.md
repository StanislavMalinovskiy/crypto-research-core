## MODIFIED Requirements

### Requirement: Local skill routing and project authority
The seven repository-local OpenSpec skills SHALL retain their distinct operations with one-sentence descriptions beginning `Use when`, while preserving useful non-conflicting selection, scope, artifact, merge and validation safeguards.

#### Scenario: Optional explore examples
- **WHEN** explore is loaded
- **THEN** long conversation and diagram examples SHALL be available through an optional `references/examples.md` in that skill
- **AND** mandatory write authorization, scope, context discovery and artifact rules SHALL remain in the skill body
- **AND** no safety rule SHALL exist only in an optional example.

#### Scenario: Builder task completion
- **WHEN** Builder finishes or verifies an implementation task in MULTIAGENT
- **THEN** Builder SHALL report completion evidence without changing OpenSpec task checkboxes or documentation
- **AND** Architect SHALL update verified task checkboxes in DOCS_CLOSE after APPROVE
- **AND** partial, deferred or unverified behavior SHALL NOT be marked complete.

#### Scenario: Archive remains project controlled
- **WHEN** the archive skill is invoked for this project
- **THEN** archive mutation SHALL use the authorized OpenSpec CLI operation after the selected workflow's project approval and complete verification gate in both DEFAULT and MULTIAGENT
- **AND** MULTIAGENT SHALL additionally require its closure procedure and checkpoint, while DEFAULT Control SHALL retain its own existing gate and archive ownership without importing MULTIAGENT protocol
- **AND** manual directory moves, skip-sync choices or generic confirmation of incomplete work SHALL NOT bypass these requirements
- **AND** selection, scope, collision, metadata, delta consistency and truthful result safeguards SHALL remain effective.

#### Scenario: Warnings are not archive authorization
- **WHEN** verification finds only warnings or no issues
- **THEN** the report SHALL preserve actionable findings and state the applicable project review verdict or verification status
- **AND** it SHALL NOT confer archive authorization or replace project approval and required gates
- **AND** missing required behavior, scenario evidence or failed required checks SHALL NOT be downgraded into a non-blocking warning.

#### Scenario: Explicit authorization survives generic planning text
- **WHEN** the owner has explicitly authorized a bounded action or a phase transition
- **THEN** generic propose wording SHALL NOT revoke that authorization or demand a second authorization merely because planning was invoked
- **AND** propose SHALL perform only planning during its own operation; Main SHALL control later authorized transitions in MULTIAGENT, and Control SHALL own planning and coordinate later authorized Developer implementation in DEFAULT under its existing workflow
- **AND** a planning-only request SHALL stop without implementation in either mode, at PLAN_READY only in MULTIAGENT and after presenting the planning artifacts in DEFAULT without importing MULTIAGENT phases or statuses
- **AND** unresolved intent, expanded scope, silence or answers to discovery questions SHALL NOT be treated as new authorization.
