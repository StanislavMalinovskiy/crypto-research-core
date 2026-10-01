## MODIFIED Requirements

### Requirement: GPT-6 agent model routing
The project SHALL configure Main as `gpt-6.1-sol / medium` and SHALL use deterministic GPT-6 role routing in MULTIAGENT without changing the manually selected workflow mode.

#### Scenario: Fixed role mapping
- **WHEN** Main dispatches a non-Builder project role
- **THEN** Architect SHALL use `gpt-6.1-sol / high`
- **AND** fresh Reviewer SHALL use `gpt-6.1-sol / medium`
- **AND** fresh Escalation SHALL use `gpt-6.1-sol / high`.

#### Scenario: Risk-based Builder selection
- **WHEN** Architect returns `PLAN_READY` for implementation
- **THEN** ROUTINE SHALL use `builder_luna_xhigh` with `gpt-6-luna / xhigh`
- **AND** STANDARD SHALL use `builder_luna_max` with `gpt-6-luna / max`
- **AND** CORE_RISK SHALL use `builder_sol` with `gpt-6.1-sol / medium`
- **AND** separate role files SHALL pin the two Luna efforts.

#### Scenario: Current guidance consistency
- **WHEN** active routing guidance is verified
- **THEN** root guidance, normative workflow documents, configuration and roles SHALL agree on current routing and review ownership
- **AND** executable checks SHALL reject `builder_terra` and `gpt-5.6-terra` only in those active locations
- **AND** historical notes, archives and benchmarks SHALL remain outside that obsolete-routing scan
- **AND** accepted main specs SHALL be synchronized only by the verified archive operation.
