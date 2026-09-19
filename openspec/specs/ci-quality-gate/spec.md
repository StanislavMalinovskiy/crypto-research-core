# CI Quality Gate Specification

## Purpose

Provides a reproducible automated gate that evaluates every proposed repository change with the same build, architecture, PostgreSQL and OpenSpec checks required locally.

## Requirements

### Requirement: Automated repository verification
The repository SHALL define one CI quality gate for pull requests and pushed commits that uses Java 25 and repository-owned verification entry points.

#### Scenario: Successful quality gate
- **WHEN** the workflow evaluates a commit on a supported GitHub-hosted runner
- **THEN** it SHALL run the Maven Wrapper `clean verify` lifecycle with Docker available
- **AND** it SHALL run strict OpenSpec validation and the OpenSpec doctor
- **AND** the gate SHALL succeed only when every required command succeeds.

#### Scenario: Guardrail failure
- **WHEN** a Maven, architecture, PostgreSQL integration or OpenSpec check fails
- **THEN** the quality gate SHALL report a non-successful conclusion
- **AND** no fallback command SHALL bypass the failed check.

### Requirement: Durable CI evidence
The quality gate SHALL preserve Maven unit and integration test reports produced by its run, including failed runs where reports exist.

#### Scenario: Test report publication
- **WHEN** Maven produces Surefire or Failsafe reports
- **THEN** the workflow SHALL upload them as a CI artifact even if a preceding verification step failed
- **AND** missing report files SHALL not conceal the original build result.

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
