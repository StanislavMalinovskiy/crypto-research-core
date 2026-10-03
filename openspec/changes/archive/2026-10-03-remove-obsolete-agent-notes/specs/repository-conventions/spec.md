## MODIFIED Requirements

### Requirement: Task-scoped instruction disclosure
Repository guidance SHALL expose a root router of at most 150 lines using real repository paths without aliases, and SHALL preserve every existing normative rule through an unconditional reminder or a mandatory impact-triggered reference to its complete controlling rule.

#### Scenario: Routing before editing
- **WHEN** an agent plans or applies a task
- **THEN** it SHALL read the affected OpenSpec change when the selected route requires one, applicable accepted specs, affected module documentation and nearest module guide, and applicable ADRs, and inspect relevant code and tests before editing
- **AND** MULTIAGENT TRIVIAL/NORMAL SHALL NOT require creating or loading a nonexistent change; DEFAULT SHALL retain its current OpenSpec policy
- **AND** it SHALL load `docs/PROJECT_SUMMARY.md` only for onboarding, product orientation or explicit task relevance
- **AND** root and OpenSpec context SHALL NOT require all module documents or broad Roadmap, Architecture and Tech Stack reads for every task.

#### Scenario: Domain impacts select controlling sources
- **WHEN** task impact concerns structure, module boundaries, persistence, transactions, events or concurrency
- **THEN** the relevant Architecture rules SHALL be required before the affected decision
- **AND** Testing SHALL be required for test selection or verification changes, Operations for runtime, configuration, provider operations or secrets, and Reproducibility for time, financial values, identity, ordering, datasets, signals, evaluations or reports
- **AND** technology, dependency or Java implementation decisions SHALL require the applicable Tech Stack rules
- **AND** a missing or ambiguous controlling reference SHALL be resolved before dependent work, rather than treated as permission to omit a rule.

#### Scenario: Rule-preservation review
- **WHEN** root obligations are relocated
- **THEN** each row of the historical instruction-diet audit rule ledger, retained in git history as `docs/agents/instruction-diet-audit.md` at commit `e7593814afef6ce47dc46c41e20628eebe50a85c`, SHALL have a concrete retained, relocated or explicitly reconciled disposition in the active design
- **AND** a duplicate rendering MAY be removed only while the complete obligation remains reachable
- **AND** moving a safety rule SHALL NOT reduce its scope, approval requirements or evidence standard unless an explicit owner-authorized normative change records the exact changed rule, resulting location and reason
- **AND** workflow-simplification SHALL preserve instruction-diet routing and all safeguards except the expressly authorized tier, review, RED evidence, closure-applicability and telemetry changes.

#### Scenario: Historical ledger outside the working tree
- **WHEN** an agent performs rule-preservation review and `docs/agents/instruction-diet-audit.md` is absent from the working tree
- **THEN** it SHALL read the ledger from commit `e7593814afef6ce47dc46c41e20628eebe50a85c`
- **AND** the absence of the working-tree file SHALL NOT waive, narrow or replace the per-row disposition obligation.
