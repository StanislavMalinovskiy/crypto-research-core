## ADDED Requirements

### Requirement: Portable offline research verification and explicit local evidence checks
The CI gate SHALL use Node major 24 and execute repository-owned offline or synthetic research tests without provider access, secrets or workstation evidence paths. It SHALL retain named test-result evidence and fail when mandatory research checks fail or are not executed. Checks requiring external historical evidence SHALL form an explicitly local separate set that reports missing required inputs as failure, not silent success or skip.

#### Scenario: Verify on a clean hosted checkout
- **WHEN** the gate runs without `C:\crypto-research-evidence` or provider credentials
- **THEN** every mandatory portable research test SHALL execute against repository or synthetic inputs
- **AND** a failing portable test SHALL fail the quality gate while its result output remains available.

#### Scenario: Local historical evidence is missing
- **WHEN** an explicitly local historical-evidence suite is requested and a required saved input is absent
- **THEN** it SHALL fail explicitly naming the missing input
- **AND** portable synthetic checks SHALL not be substituted as proof of historical evidence.
