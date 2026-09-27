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
- **WHEN** a MULTIAGENT task is CORE_RISK or a bugfix, or Architect selects RED_REQUIRED for another NORMAL or CONTRACT task
- **THEN** Builder SHALL derive meaningful tests from the active requirement and observe targeted behavioral RED before implementation
- **AND** the named test SHALL execute and fail at its expected behavioral assertion
- **AND** compilation, discovery, configuration, startup or infrastructure failure SHALL NOT count as RED
- **AND** Builder SHALL record test paths, exact command, requirement/acceptance-criterion, failing assertion and expected/actual before freeze, then implement and run targeted GREEN
- **AND** content hashes and pre-implementation-diff evidence solely for RED/test freeze SHALL NOT be required.

#### Scenario: Frozen behavioral evidence
- **WHEN** Builder establishes valid RED
- **THEN** the establishing tests, expectations, fixtures, discovery and runtime configuration SHALL remain frozen through GREEN
- **AND** establishing tests SHALL NOT be weakened, skipped, narrowed or changed after valid RED
- **AND** a necessary establishing-test change SHALL require confirmed `TEST_SPEC_ERROR`, current-reviewer `REPAIR` with `requires_new_red = true` and new valid behavioral RED
- **AND** BUILD_DONE SHALL report the assertion, expected/actual and `tests_changed_after_red`, and the current reviewer SHALL verify that final tests still encode the intended behavior
- **AND** production behavior SHALL NOT recognize test artifacts to satisfy expectations.

#### Scenario: Suspected test defect
- **WHEN** Builder identifies a post-freeze conflict between an establishing test and the controlling requirement
- **THEN** Builder SHALL preserve the test and report the exact conflict to the current reviewer
- **AND** that reviewer SHALL authorize a confirmed test correction or return an unresolved technical dispute for escalation
- **AND** semantic contract changes SHALL return to Architect with `reason = CONTRACT_CHANGED` before review reopens.

#### Scenario: No behavioral test needed
- **WHEN** a non-bugfix NORMAL or CONTRACT task has Architect-recorded `RED_NOT_REQUIRED` and a concrete reason
- **THEN** the author SHALL record applicable existing verification
- **AND** no artificial failure or pre-implementation diff SHALL be created merely as process evidence
- **AND** this exception SHALL NOT apply to CORE_RISK or any bugfix.

#### Scenario: Independent review ownership
- **WHEN** a MULTIAGENT task is ready for review
- **THEN** NORMAL and CONTRACT SHALL use the same Architect thread for review, including a CONTRACT delta intended to change an accepted normative spec
- **AND** any CORE_RISK change SHALL use a fresh Reviewer in a new thread; this condition takes precedence
- **AND** an accepted normative-spec change alone SHALL NOT require fresh review when no TR trigger exists
- **AND** NORMAL/CONTRACT review SHALL report only touched CI, while CORE_RISK SHALL report the full invariant matrix with evidence and justified non-applicability

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
- **AND** after `APPROVE` on CONTRACT/CORE_RISK, Architect SHALL close non-semantic documentation and tasks without archiving; NORMAL SHALL omit DOCS_CLOSE and archive
- **AND** Main SHALL independently run test-integrity preflight, complete Maven verification, strict all-item OpenSpec validation, doctor and diff checking for NORMAL/CONTRACT/CORE_RISK before DONE or any archive
- **AND** a failed required check SHALL block completion without narrowing or skipping it.

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

#### Scenario: Guard cannot disable itself
- **WHEN** the complete repository verification sequence starts for DEFAULT or a non-TRIVIAL MULTIAGENT task, or CI runs
- **THEN** test-integrity inspection SHALL execute outside and before the Maven lifecycle whose configuration it inspects
- **AND** a detected bypass SHALL stop verification before `clean verify`
- **AND** the stable quality-gate job SHALL run the same independent preflight before Maven without a conditional or continue-on-error escape
- **AND** the strictly TRIVIAL local two-check gate SHALL NOT change the complete Maven lifecycle, CI command or integrity inspection.

### Requirement: Architect-owned stable risk classification
In MULTIAGENT, Main SHALL decide only strictly TRIVIAL or not TRIVIAL. Architect alone SHALL classify every non-TRIVIAL task using all triggers from the single authoritative CORE_RISK trigger list in `docs/AGENT_WORKFLOW_MULTIAGENT.md`; every other active guidance location SHALL reference that list instead of defining a duplicate. Any matched trigger SHALL require CORE_RISK before evaluating NORMAL versus CONTRACT. Main SHALL retain intake, scope coordination, routing, gate and DONE ownership without evaluating TR triggers or selecting other tiers or Builder risk.

#### Scenario: Risk fixed at planning
- **WHEN** Architect returns `PLAN_READY`
- **THEN** the existing contract/handoff SHALL fix the task tier and `risk`, matched `risk_triggers` or explicit `none`, applicable invariants, test mode and bounded change budget
- **AND** with no TR matches Architect SHALL choose NORMAL for internal work preserving accepted observable behavior, or CONTRACT for observable/public or accepted behavior changes; uncertainty SHALL select the higher tier
- **AND** the existing ROUTINE/STANDARD/CORE_RISK risk field SHALL retain its Builder routing independently of NORMAL versus CONTRACT: small well-understood bounded work uses ROUTINE, broader reasoning uses STANDARD, and every TR match uses CORE_RISK
- **AND** no new state, capsule file or evidence infrastructure SHALL be introduced
- **AND** tier and risk SHALL remain fixed during implementation, review and repairs
- **AND** lowering a previously fixed risk SHALL require explicit owner direction.

#### Scenario: New scope or risk trigger
- **WHEN** an author or reviewer discovers a new risk or scope trigger
- **THEN** Main SHALL return it to Architect with `reason = CONTRACT_CHANGED` and `subreason = RISK_CHANGED`
- **AND** Architect SHALL issue a new `PLAN_READY` before further dependent implementation
- **AND** Main SHALL select a new Builder when the new risk requires one, preserving task repair accounting
- **AND** newly discovered observable-contract changes during NORMAL SHALL return to Architect with CONTRACT_CHANGED before dependent work, establish the required OpenSpec contract and a new PLAN_READY rather than silently staying NORMAL.

### Requirement: Verified and recoverable archive closure
In MULTIAGENT, Main and Architect SHALL close approved CONTRACT and CORE_RISK changes through full verification, a scoped pre-archive Git checkpoint, Architect archive and post-archive checks before Main returns DONE. NORMAL SHALL require the same Main full gate without DOCS_CLOSE, checkpoint or archive; TRIVIAL SHALL use its explicitly bounded lightweight gate. DEFAULT SHALL retain its existing independent closure ownership.

#### Scenario: Gate failure ownership
- **WHEN** a complete final gate fails
- **THEN** a Builder-attributable failure SHALL return to that Builder within the existing repair budget
- **AND** a contract or specification defect SHALL return to Architect planning with `reason = CONTRACT_CHANGED`, while a documentation defect SHALL return to Architect repair
- **AND** infrastructure and unrelated pre-existing failures SHALL return `BLOCKED`; infrastructure retries SHALL NOT consume implementation repairs.

#### Scenario: Successful archive closure
- **WHEN** CONTRACT or CORE_RISK approval closure and the complete final gate pass
- **THEN** Main SHALL record a scoped Git checkpoint and before-archive porcelain status without staging or committing unrelated owner changes
- **AND** Architect SHALL archive only the authorized change using the OpenSpec CLI
- **AND** Main SHALL inspect the exact archive diff and rerun strict all-item validation, doctor, targeted repository conventions and diff checking
- **AND** Main SHALL return DONE only after every post-archive check passes.

#### Scenario: Archive restoration
- **WHEN** a MULTIAGENT post-archive check fails
- **THEN** only exact paths changed by that archive SHALL be restored from the recorded checkpoint and only that new archive copy SHALL be removed
- **AND** active-change bytes, accepted-spec bytes, unrelated owner work and the real Git index SHALL be preserved
- **AND** destructive broad reset, whole-tree checkout, clean, owner-work stash and manual reversal of accepted-spec patches SHALL NOT be used
- **AND** the task SHALL return to Architect for the active-change correction, full verification and archive again without resetting repair accounting
- **AND** inability to prove scoped restoration safe SHALL return `BLOCKED` before destructive action.

### Requirement: Active-change overlap check before PLAN_READY
In MULTIAGENT CONTRACT and CORE_RISK, Architect SHALL run `openspec list` before PLAN_READY, inspect the affected specs of other active changes and record overlaps in the PLAN_READY handoff as `none` or each change with a disposition of `resolve first`, `safe to proceed` or `blocked`.

#### Scenario: Planning with other active changes
- **WHEN** Architect prepares a CONTRACT or CORE_RISK PLAN_READY handoff
- **THEN** Architect SHALL identify other active changes touching the same specs and record `none` or each overlapping change with its disposition
- **AND** Architect SHALL resolve a `resolve first` overlap before PLAN_READY and SHALL NOT issue PLAN_READY while an overlap is `blocked`
- **AND** TRIVIAL and NORMAL SHALL NOT require this overlap check.

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
- **THEN** each row of the existing instruction-diet audit rule ledger SHALL have a concrete retained, relocated or explicitly reconciled disposition in the active design
- **AND** a duplicate rendering MAY be removed only while the complete obligation remains reachable
- **AND** moving a safety rule SHALL NOT reduce its scope, approval requirements or evidence standard unless an explicit owner-authorized normative change records the exact changed rule, resulting location and reason
- **AND** workflow-simplification SHALL preserve instruction-diet routing and all safeguards except the expressly authorized tier, review, RED evidence, closure-applicability and telemetry changes.

### Requirement: Conditional closure procedure
MULTIAGENT SHALL keep ordinary PLAN, BUILD and REVIEW in its existing workflow and SHALL load `docs/agents/close-archive.md` only for DOCS_CLOSE, complete final gate, archive, archive recovery or post-archive work.

#### Scenario: Ordinary phase read path
- **WHEN** Main or an assigned role performs ordinary PLAN, BUILD or REVIEW
- **THEN** it SHALL NOT be required to read the closure procedure
- **AND** the common workflow SHALL retain Main's gate ownership, closure order and the mandatory trigger for loading the procedure before closure work
- **AND** risk classification, phase/role routing, RED/freeze, repair accounting, review independence and lost-session recovery SHALL remain in the existing workflow.

#### Scenario: Closure read path
- **WHEN** approved CONTRACT/CORE_RISK work enters DOCS_CLOSE or any later closure operation, or NORMAL enters its complete final gate
- **THEN** the responsible Main or Architect SHALL load the closure procedure before acting
- **AND** NORMAL SHALL load only the existing procedure's applicable full-gate guidance, without DOCS_CLOSE or archive
- **AND** CONTRACT/CORE_RISK SHALL retain all existing full-gate commands, ownership routes, raw-byte checkpoint, concurrent-edit checks, CLI archive, exact mutation inspection, post-checks and scoped recovery protections unchanged
- **AND** TRIVIAL SHALL NOT load the closure procedure or perform DOCS_CLOSE/archive
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
- **AND** where an OpenSpec change exists, Architect SHALL update verified task checkboxes in DOCS_CLOSE after APPROVE; NORMAL SHALL have no artificial checkboxes or DOCS_CLOSE
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

#### Scenario: OpenSpec skills do not invent low-risk changes
- **WHEN** MULTIAGENT routing identifies TRIVIAL or NORMAL
- **THEN** generic propose/apply skill entry wording SHALL NOT require a dummy OpenSpec change or select an unrelated active change
- **AND** applicable existing Main or Architect/Builder responsibility SHALL govern that task instead
- **AND** the actual change-based skill operations and DEFAULT behavior SHALL remain unchanged.

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
- **AND** convention checks SHALL reconcile owner-authorized tier, review and semantic-freeze changes through valid behavioral RED, while continuing to reject missing CORE_RISK fresh review, changed routing, weakened test execution, broken required archive order and missing checkpoint/recovery safeguards
- **AND** checks SHALL verify active guidance and delta requirements before archive without depending on the main spec already being synchronized.

## ADDED Requirements

### Requirement: Strictly trivial direct editing
In MULTIAGENT, Main SHALL directly edit only clearly non-normative text/docs with no source code, config, scripts, OpenSpec, ADR, workflow/skills, normative docs, API/path/code rename or behavioral meaning change. Main SHALL decide only strictly TRIVIAL or not TRIVIAL and SHALL route every exclusion or uncertainty to Architect before dependent work.

#### Scenario: Eligible editorial correction
- **WHEN** a task changes only clearly non-normative wording or formatting without changing meaning, claims, link/path targets or any excluded file type
- **THEN** Main SHALL perform the edit directly without subagents, OpenSpec, review, overlap check, DOCS_CLOSE or archive
- **AND** Main SHALL return DONE only after `git diff --check` and `mvnw.cmd -Dtest=RepositoryConventionsTest test` both pass with the required tests actually executed.

#### Scenario: Trivial scope no longer proven
- **WHEN** an exclusion, mixed semantic scope or uncertainty appears before completion
- **THEN** Main SHALL stop direct edits and route the non-TRIVIAL task to Architect without evaluating TR triggers itself
- **AND** a failed lightweight check SHALL NOT authorize source/config/policy repairs by Main or be hidden as success.

### Requirement: Tier-specific work and test-first scope
MULTIAGENT SHALL omit OpenSpec, overlap checks, DOCS_CLOSE and archive for NORMAL, while preserving Architect planning, risk-routed Builder implementation, same-Architect review and Main's complete final gate. CONTRACT and CORE_RISK SHALL require OpenSpec and their complete protected archive sequence.

#### Scenario: Internal accepted behavior
- **WHEN** Architect has assessed all TR triggers as absent and the task preserves accepted observable behavior
- **THEN** the NORMAL route SHALL use a bounded contract tied to existing accepted sources and useful appropriate tests without a new contract/state/evidence file
- **AND** a bugfix restoring that accepted behavior SHALL require valid behavioral RED before implementation
- **AND** a bugfix changing accepted behavior SHALL be CONTRACT instead, unless a TR trigger requires CORE_RISK.

#### Scenario: Red requirement precedence
- **WHEN** Architect chooses test mode for non-TRIVIAL work
- **THEN** every CORE_RISK task and every bugfix SHALL use RED_REQUIRED
- **AND** other CONTRACT work SHALL use Architect-selected RED_REQUIRED or RED_NOT_REQUIRED with a concrete reason
- **AND** other NORMAL work SHALL use appropriate useful tests with the reason recorded in its existing test-mode handoff
- **AND** all valid establishing RED SHALL invoke semantic freeze regardless of tier
- **AND** a documentation-only CORE_RISK task SHALL NOT silently waive RED_REQUIRED.

#### Scenario: Examples do not bypass triggers
- **WHEN** a task concerns telemetry, logging, refactoring, tests, non-security configuration or source comments
- **THEN** Architect SHALL assess all TR triggers before considering NORMAL
- **AND** any matched trigger or unresolved credible trigger uncertainty SHALL select CORE_RISK
- **AND** an observable/public or accepted contract change with no trigger SHALL select CONTRACT
- **AND** mixed scope SHALL receive the highest applicable tier.

### Requirement: Opt-in assignment measurement
MULTIAGENT SHALL use the existing assignment-activity logging script only for benchmark, debug or explicitly requested measurement, not as a mandatory ordinary-development step.

#### Scenario: Ordinary development handoff
- **WHEN** a task has no benchmark, debug or explicit measurement purpose
- **THEN** dispatch and return logging through `log-agent-activity.ps1` SHALL NOT be required
- **AND** the ordinary contract, review, required verification evidence and truthful handoff SHALL remain required.

### Requirement: Compact truthful gate results
Main SHALL report bounded PASS/FAIL results without changing gate ownership, required commands or failure handling, and SHALL capture full command output using plain shell redirection or an existing mechanism without new workflow infrastructure.

#### Scenario: Passed required check
- **WHEN** a required command actually completes successfully and all required checks execute
- **THEN** Main SHALL report the exact command/check, exit status, compact available counts and PASS
- **AND** test counts SHALL include failures, errors and skipped counts where applicable
- **AND** unavailable counts SHALL NOT be fabricated and skipped required checks SHALL NOT be called PASS.

#### Scenario: Failed or missing required check
- **WHEN** a required command fails or a required check is skipped or absent despite exit zero
- **THEN** Main SHALL report the exact command and captured exit code, identify the failed test/check/plugin or missing check, provide a bounded relevant error excerpt and the path to full output
- **AND** Main SHALL disclose unrun remaining checks and withhold DONE
- **AND** output formatting SHALL NOT replace the command exit status, hide failures or introduce test-selection/skip flags into the full gate.
