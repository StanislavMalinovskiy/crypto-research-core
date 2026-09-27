## MODIFIED Requirements

### Requirement: Executable agent guidance
The repository SHALL provide concise common guidance for source responsibilities, required reading, architecture, OpenSpec, testing, review and verification, plus mode-specific workflow guidance loaded only for the selected mode.

#### Scenario: New agent onboarding
- **WHEN** an agent begins a change from the repository root
- **THEN** it SHALL identify required reading, affected module documentation, Definition of Done and verification commands
- **AND** it SHALL determine DEFAULT or MULTIAGENT before delegating work.

#### Scenario: Default guidance
- **WHEN** DEFAULT is selected
- **THEN** root guidance SHALL route to the concise DEFAULT responsibility and evidence contract in `docs/AGENT_WORKFLOW.md` in addition to common invariants
- **AND** it SHALL NOT require specialized MULTIAGENT roles, phases, statuses, manifests, telemetry or repair routing.

#### Scenario: Multiagent guidance
- **WHEN** MULTIAGENT is selected
- **THEN** the agent SHALL load its separate workflow document and applicable role configuration
- **AND** Main alone SHALL spawn project subagents using native Codex or coordinate user-created sessions through `codex queue`
- **AND** Orca SHALL require an explicit user request in the current task
- **AND** this protocol SHALL NOT authorize implicit subagents in DEFAULT.

#### Scenario: Independent implementation and test ownership
- **WHEN** implementation changes observable behavior in MULTIAGENT
- **THEN** Builder SHALL derive meaningful tests from the active requirement and observe targeted behavioral RED before implementation
- **AND** the named test SHALL execute and fail at its expected behavioral assertion
- **AND** compilation, discovery, configuration, startup or infrastructure failure SHALL NOT count as RED
- **AND** Builder SHALL capture test paths, content hash, exact command, failing assertion and pre-implementation diff, then implement and run targeted GREEN.

#### Scenario: Frozen behavioral evidence
- **WHEN** Builder establishes valid RED
- **THEN** the establishing tests, expectations, fixtures, discovery and runtime configuration SHALL remain frozen through GREEN
- **AND** a confirmed `TEST_SPEC_ERROR` SHALL require current-reviewer `REPAIR` with `requires_new_red = true`, new behavioral RED and a new hash
- **AND** production behavior SHALL NOT recognize test artifacts to satisfy expectations.

#### Scenario: Suspected test defect
- **WHEN** Builder identifies a conflict between a failing test and the active specification
- **THEN** Builder SHALL preserve the test and report the exact conflict to the current reviewer
- **AND** that reviewer SHALL authorize a confirmed test correction or return an unresolved technical dispute for escalation
- **AND** semantic contract changes SHALL return to Architect with `reason = CONTRACT_CHANGED` before review reopens.

#### Scenario: No behavioral test needed
- **WHEN** Architect records `RED_NOT_REQUIRED` and a concrete reason
- **THEN** the author SHALL record applicable existing verification
- **AND** no artificial failure or pre-implementation diff SHALL be created merely as process evidence.

#### Scenario: Independent review ownership
- **WHEN** a MULTIAGENT task is ready for review
- **THEN** the following routing rule SHALL apply, with either fresh-review condition taking precedence over the first condition:

- ROUTINE / STANDARD implementation or non-normative docs → same Architect thread.
- Any CORE_RISK change → fresh Reviewer (new thread).
- Any change to an accepted normative OpenSpec spec → fresh Reviewer, regardless of risk.

- **AND** documentation-only work SHALL stay with Architect and skip Builder
- **AND** the reviewing role SHALL inspect the stable diff, contract, tests, evidence and applicable invariants before one consolidated `APPROVE`, `REPAIR`, `ESCALATE` or `BLOCKED` verdict.

#### Scenario: Bounded repair and escalation
- **WHEN** the current reviewer identifies repairable defects
- **THEN** Main SHALL route implementation or test repairs to the same Builder, followed by `BUILD_DONE` and review
- **AND** documentation repairs SHALL return to the same Architect, followed by review
- **AND** the task SHALL allow two ordinary repairs and a third only when the current reviewer explicitly sets `third_repair_authorized = true`
- **AND** repair SHALL NOT recompute risk, reset the repair budget or introduce a fourth ordinary repair.

#### Scenario: Role status vocabulary
- **WHEN** a project subagent completes an assignment
- **THEN** it SHALL return one role/phase-compatible state from `PLAN_READY`, `BUILD_DONE`, `REPAIR`, `APPROVE`, `BLOCKED`, `ESCALATE`, with final `DONE` owned by Main
- **AND** reason, subreason and escalation verdict SHALL remain attributes rather than additional workflow states
- **AND** a missing, unknown or incompatible status SHALL receive one protocol correction without consuming an artifact repair
- **AND** assignment logging SHALL accept the explicit Luna role names and authorized Architect review, documentation repair and archive phases.

#### Scenario: Verification ownership
- **WHEN** Builder completes implementation
- **THEN** Builder SHALL return `BUILD_DONE` with targeted GREEN and compact evidence
- **AND** Main SHALL rerun claimed RED only when the current reviewer sets `red_suspect = true`
- **AND** after `APPROVE`, Architect SHALL close non-semantic documentation and tasks without archiving
- **AND** Main SHALL independently run test-integrity preflight, complete Maven verification, strict all-item OpenSpec validation, doctor and diff checking before archive
- **AND** a failed required check SHALL block completion without narrowing or skipping it.

## ADDED Requirements

### Requirement: Task-scoped instruction disclosure
Repository guidance SHALL expose a root router of at most 150 lines using real repository paths without aliases, and SHALL preserve every existing normative rule through an unconditional reminder or a mandatory impact-triggered reference to its complete controlling rule.

#### Scenario: Routing before editing
- **WHEN** an agent plans or applies a task
- **THEN** it SHALL read the affected OpenSpec change and accepted specs, the affected module documentation and nearest module guide, and applicable ADRs, and inspect relevant code and tests before editing
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
- **THEN** each row of the existing instruction-diet audit rule ledger SHALL have a concrete retained, relocated or explicitly reconciled disposition in the active design
- **AND** a duplicate rendering MAY be removed only while the complete obligation remains reachable
- **AND** moving a safety rule SHALL NOT reduce its scope, approval requirements or evidence standard.

### Requirement: Conditional closure procedure
MULTIAGENT SHALL keep ordinary PLAN, BUILD and REVIEW in its existing workflow and SHALL load `docs/agents/close-archive.md` only for DOCS_CLOSE, complete final gate, archive, archive recovery or post-archive work.

#### Scenario: Ordinary phase read path
- **WHEN** Main or an assigned role performs ordinary PLAN, BUILD or REVIEW
- **THEN** it SHALL NOT be required to read the closure procedure
- **AND** the common workflow SHALL retain Main's gate ownership, closure order and the mandatory trigger for loading the procedure before closure work
- **AND** risk classification, phase/role routing, RED/freeze, repair accounting, review independence and lost-session recovery SHALL remain in the existing workflow.

#### Scenario: Closure read path
- **WHEN** approved work enters DOCS_CLOSE or any later closure operation
- **THEN** the responsible Main or Architect SHALL load the closure procedure before acting
- **AND** all existing full-gate commands, ownership routes, raw-byte checkpoint, concurrent-edit checks, CLI archive, exact mutation inspection, post-checks and scoped recovery protections SHALL remain mandatory
- **AND** DEFAULT SHALL retain its own existing gate and responsibility contract without importing MULTIAGENT protocol.

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
- **THEN** archive mutation SHALL use the authorized OpenSpec CLI operation after the project approval, closure, full gate and checkpoint
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
- **AND** propose SHALL perform only planning during its own phase and Main SHALL control any later authorized transition under the selected workflow
- **AND** a PLAN-only request SHALL stop at PLAN_READY without implementation
- **AND** unresolved intent, expanded scope, silence or answers to discovery questions SHALL NOT be treated as new authorization.

### Requirement: Builder entry-point equivalence
The three existing Builder entry points SHALL retain their fixed model/effort metadata and behaviorally identical normative instruction bodies.

#### Scenario: Detecting instruction drift
- **WHEN** repository conventions inspect the three Builder role configurations
- **THEN** equal normative bodies SHALL pass independently of the expected metadata differences
- **AND** a missing, empty or diverging normative body in any one entry point SHALL fail verification
- **AND** normalization SHALL ignore only line-ending differences, not remove clauses or reorder instructions.

#### Scenario: Relocated safeguard verification
- **WHEN** convention checks inspect required instructions after relocation
- **THEN** they SHALL accept the authorized explicit root/workflow/closure reference chain with all obligations present
- **AND** a missing target, missing mandatory route or omitted existing safety obligation SHALL fail
- **AND** relocating clauses SHALL NOT disable the existing mode, risk, review, RED/freeze, repair or archive-order checks.

### Requirement: Bounded Main context
Main SHALL coordinate using bounded summaries and source paths when those suffice, without replacing durable contract and verification truth with conversational memory.

#### Scenario: Deep inspection handoff
- **WHEN** a decision requires deep reading of large source files, raw logs, reports or a complete diff
- **THEN** Main SHALL route that inspection to the appropriate existing role and receive bounded findings with evidence paths
- **AND** Main SHALL retain permission to inspect exact relevant excerpts needed for routing, blockers, the final gate or archive mutation inspection
- **AND** summary size SHALL NOT excuse omitted failures, invariant evidence or required checks.

#### Scenario: Durable truth after compaction
- **WHEN** context is summarized, compacted or a session is replaced
- **THEN** current truth SHALL remain recoverable from existing OpenSpec artifacts, code/tests and existing evidence sources
- **AND** no new state, capsule or evidence file SHALL be introduced solely for context management
- **AND** existing self-contained handoff and session-recovery responsibilities SHALL remain unchanged.

### Requirement: Bounded compaction experiment
This change SHALL add only top-level `model_post_turn_compact_threshold_percent = 60` to the project Codex configuration and SHALL treat that value as an experiment rather than a proven optimum.

#### Scenario: Configuration boundary
- **WHEN** the compaction setting is added
- **THEN** its value SHALL be the integer `60` at the top level
- **AND** `[agents].enabled`, `model_auto_compact_token_limit`, model routing and effort, `tool_output_token_limit` and `compact_prompt` SHALL remain unchanged
- **AND** unsupported-setting evidence SHALL be reported without substituting another compaction control.

#### Scenario: Measuring the experiment
- **WHEN** implementation evaluates context efficiency
- **THEN** it SHALL distinguish on-disk byte estimates, effective configuration support and observed runtime compaction behavior
- **AND** any observed efficiency result SHALL use available existing evidence and state its workload and limitations
- **AND** absence of an observed compaction event SHALL NOT be reported as proof of successful runtime tuning.

