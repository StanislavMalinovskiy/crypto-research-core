## MODIFIED Requirements

### Requirement: Default development workflow
The DEFAULT workflow SHALL use one top-level Control session and one top-level Developer session without the specialized MULTIAGENT phase and status protocol.

#### Scenario: Default responsibility split
- **WHEN** work is performed in DEFAULT
- **THEN** Control SHALL own the active contract, final documentation, stable-diff review and completion decision
- **AND** Developer SHALL own tests and implementation in one bounded pass
- **AND** neither session SHALL be required to use MULTIAGENT statuses, task capsules, phase manifests, threat checks, assignment telemetry or role-routing logs.

#### Scenario: Changed behavior
- **WHEN** DEFAULT work adds or changes observable behavior
- **THEN** Developer SHALL derive meaningful tests from the active requirement and observe a targeted behavioral red before implementing
- **AND** compilation, discovery, configuration or infrastructure failure SHALL NOT count as red
- **AND** after red Developer SHALL NOT weaken, disable, skip or narrow the test or add production behavior that exists only for a test artifact.

#### Scenario: No new behavioral test required
- **WHEN** work changes only documentation, comments, formatting, mechanical configuration, a pure rename or an internally covered refactoring
- **THEN** Developer MAY record that no new behavioral test is needed
- **AND** applicable scope checks SHALL still run, and the full gate SHALL run for integration-triggering changes before each commit.

#### Scenario: Completion and handoff
- **WHEN** Developer finishes a DEFAULT pass
- **THEN** Developer SHALL run relevant targeted green checks and the applicable completion checks
- **AND** the handoff SHALL identify changed files, red and green evidence when applicable, verification results and remaining risks
- **AND** Control SHALL review test meaning before implementation details and independently run the required completion checks; Java including tests, build configuration, database/migrations, dependencies and shared runtime launch/operations instructions require the complete gate before each affected commit. Isolated documentation or research tools use scope checks, mixed work uses their union, and later checked-input changes require affected checks to be rerun.

#### Scenario: Bounded repair
- **WHEN** Control finds repairable defects after the first DEFAULT pass
- **THEN** it SHALL consolidate them into one repair handoff by default
- **AND** any further repair handoff SHALL require the user's explicit decision.

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
- **WHEN** a MULTIAGENT task changes executable critical behavior, fixes an executable defect, or its bounded contract selects RED_REQUIRED
- **THEN** the implementing author SHALL derive meaningful tests from the active requirement and observe targeted behavioral RED before implementation
- **AND** the named test SHALL execute and fail at its expected behavioral assertion
- **AND** compilation, discovery, configuration, startup or infrastructure failure SHALL NOT count as RED
- **AND** Builder SHALL record test paths, exact command, requirement/acceptance-criterion, failing assertion and expected/actual before freeze, then implement and run targeted GREEN
- **AND** content hashes and pre-implementation-diff evidence solely for RED/test freeze SHALL NOT be required.

#### Scenario: Frozen behavioral evidence
- **WHEN** Builder establishes valid RED
- **THEN** the establishing tests, expectations, fixtures, discovery and runtime configuration SHALL remain frozen through GREEN except for the proven synthetic fixture/setup correction below
- **AND** establishing tests SHALL NOT be weakened, skipped or narrowed after valid RED
- **AND** a proven synthetic fixture or test-setup contradiction with an accepted requirement MAY be corrected by Builder without prior owner or Reviewer permission, preserving exact conflict/change/rerun evidence and subsequent independent review; an affected establishing test SHALL receive applicable new meaningful behavioral RED and truthful `tests_changed_after_red`
- **AND** BUILD_DONE SHALL report the assertion, expected/actual and `tests_changed_after_red`, and the current reviewer SHALL verify that final tests still encode the intended behavior
- **AND** production behavior SHALL NOT recognize test artifacts to satisfy expectations.

#### Scenario: Suspected test defect
- **WHEN** Builder identifies a post-freeze conflict between an establishing test and the controlling requirement
- **THEN** Builder SHALL preserve the exact conflict and prior evidence; a proven synthetic fixture/setup error MAY be corrected within the authorized requirement without prior permission
- **AND** an independent Reviewer SHALL subsequently verify any correction did not fit expectations to implementation, weaken assertions, skip/narrow tests, change acceptance criteria, regenerate expected truth from implementation or change accepted raw evidence; unproven errors or requirement ambiguity SHALL receive independent technical diagnosis
- **AND** semantic contract changes SHALL return to Architect with `reason = CONTRACT_CHANGED` before review reopens.

#### Scenario: No behavioral test needed
- **WHEN** documentation-only work, including critical-guarantee documentation, or other eligible work has recorded `RED_NOT_REQUIRED` and a concrete reason
- **THEN** the author SHALL record applicable existing verification
- **AND** no artificial failure or pre-implementation diff SHALL be created merely as process evidence
- **AND** documentation SHALL use facts, consistency, links and requirements; substantive research-rule changes SHALL retain independent review, and executable critical behavior or executable bugfixes SHALL retain applicable meaningful regression RED.

#### Scenario: Independent review ownership
- **WHEN** a MULTIAGENT task is ready for review
- **THEN** ordinary NORMAL and CONTRACT work SHALL use appropriate existing review; when Architect participated and is eligible, its same thread MAY review
- **AND** any CORE_RISK change SHALL use a fresh Reviewer in a new thread; this condition takes precedence
- **AND** an accepted normative-spec change alone SHALL NOT require fresh review when no TR trigger exists
- **AND** review SHALL report affected and doubtful invariants with concrete evidence in existing artifacts; untouched matrices SHALL NOT be repeated in every message

- **AND** ordinary documentation MAY be authored by Main; Architect SHALL handle its assigned documentation; explicitly authorized instruction changes MAY be implemented by the assigned Builder
- **AND** the reviewing role SHALL inspect the stable diff, contract, tests, evidence and applicable invariants before one consolidated `APPROVE`, `REPAIR`, `ESCALATE` or `BLOCKED` verdict.

#### Scenario: Bounded repair and escalation
- **WHEN** the current reviewer identifies repairable defects
- **THEN** Main SHALL route implementation or test repairs to the same Builder, followed by `BUILD_DONE` and review
- **AND** documentation repairs SHALL return to their same author, followed by applicable review
- **AND** repair count SHALL NOT impose an absolute stop; repeated defects, absence of verifiable progress or exhaustion of agreed resources SHALL trigger independent diagnosis of cause, proposed approach, remaining budget and next-result criterion
- **AND** replanning, task renaming or session replacement SHALL NOT reset cumulative time, cost or resource use; technical repairs within authority SHALL NOT require an owner decision solely because of iteration number.

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
- **AND** Main SHALL select checks by actual dependencies and effects; the complete integrity-preflight/Maven/strict-all-validation/doctor/diff gate SHALL run before every commit affecting Java including tests, build configuration, database/migrations, dependencies or shared runtime launch/operations instructions; isolated documents/research tools SHALL use scoped checks, mixed scope SHALL use the union
- **AND** a failed required check SHALL block completion without narrowing or skipping it.

### Requirement: GPT-6 agent model routing
The project SHALL configure Main as `gpt-6.1-sol / medium` and SHALL use deterministic GPT-6 role routing in MULTIAGENT without changing the manually selected workflow mode.

#### Scenario: Fixed role mapping
- **WHEN** Main dispatches a non-Builder project role
- **THEN** Architect SHALL use `gpt-6.1-sol / high`
- **AND** fresh Reviewer SHALL use `gpt-6.1-sol / medium`
- **AND** fresh Escalation SHALL use `gpt-6.1-sol / high`.

#### Scenario: Risk-based Builder selection
- **WHEN** Main dispatches Builder after ordinary classification or applicable Architect `PLAN_READY`
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
- **WHEN** the complete repository verification sequence is required by actual integration impact, or CI runs
- **THEN** test-integrity inspection SHALL execute outside and before the Maven lifecycle whose configuration it inspects
- **AND** a detected bypass SHALL stop verification before `clean verify`
- **AND** the stable quality-gate job SHALL run the same independent preflight before Maven without a conditional or continue-on-error escape
- **AND** scope-specific local checks SHALL NOT change or narrow the complete Maven lifecycle, required CI command or independent integrity inspection.

### Requirement: Bounded technical escalation
MULTIAGENT technical escalation SHALL use a fresh read-only role, return one bounded verdict and preserve author ownership and cumulative agreed time/resource accounting.

#### Scenario: Bounded technical escalation
- **WHEN** a technical dispute has no bounded safe resolution, a defect repeats, progress stalls or agreed time/resources are exhausted
- **THEN** Main SHALL dispatch a fresh read-only Sol High Escalation agent for one exact question
- **AND** it SHALL return exactly one `verdict` attribute from `REPAIR`, `REPLAN`, `APPROVE`, `OWNER_DECISION`
- **AND** `REPAIR` SHALL use the existing author and remaining agreed time and resource budget, `REPLAN` SHALL reopen Architect planning with `reason = CONTRACT_CHANGED` and `subreason = ESCALATION_REPLAN`, `APPROVE` SHALL resume approval closure, and `OWNER_DECISION` SHALL return a blocked owner decision
- **AND** owner intent or scope ambiguity SHALL go directly to the owner or expressly designated delegate; goal, spending, holdout and accepted critical constraints remain their authority, and no temporary session SHALL become a permanent delegate.

### Requirement: Architect-owned stable risk classification
In MULTIAGENT, Main SHALL classify ordinary tasks and MAY implement small local changes within accepted requirements and authority. Architect SHALL participate for architectural decisions, material ambiguity or critical-guarantee changes. Classification SHALL use the concrete guarantee changed and consequences of error against the single authoritative trigger list in `docs/AGENT_WORKFLOW_MULTIAGENT.md`; other active guidance SHALL reference it. Mentioning identity, retry or reproducibility alone SHALL NOT determine CORE_RISK. Credible unresolved uncertainty about a critical guarantee SHALL receive Architect assessment and independent review rather than an unsupported low-risk classification.

#### Scenario: Risk fixed at planning
- **WHEN** Main establishes an ordinary bounded contract or Architect returns `PLAN_READY`
- **THEN** the existing contract/handoff SHALL fix the task tier and `risk`, matched `risk_triggers` or explicit `none`, applicable invariants, test mode and bounded change budget
- **AND** with no affected critical guarantee the classifier SHALL choose NORMAL for internal work preserving accepted observable behavior, or CONTRACT for observable/public or accepted behavior changes; uncertainty SHALL select the higher tier
- **AND** the existing ROUTINE/STANDARD/CORE_RISK risk field SHALL retain its Builder routing independently of NORMAL versus CONTRACT: small well-understood bounded work uses ROUTINE, broader reasoning uses STANDARD, and each affected critical guarantee uses CORE_RISK
- **AND** no new state, capsule file or evidence infrastructure SHALL be introduced
- **AND** tier and risk SHALL remain recorded during implementation, review and repairs; reconsideration SHALL state old/new categories, reason and specific evidence
- **AND** lowering a previously recorded risk SHALL require independent Reviewer confirmation; changing accepted critical constraints SHALL require the owner or expressly appointed delegate.

#### Scenario: New scope or risk trigger
- **WHEN** an author or reviewer discovers new evidence affecting risk, scope or accepted behavior
- **THEN** Main SHALL update the bounded contract before dependent work, using Architect when its participation criteria apply, with `reason = CONTRACT_CHANGED` and `subreason = RISK_CHANGED` when applicable
- **AND** the required planning and OpenSpec contract SHALL be ready before further dependent implementation; Architect SHALL issue `PLAN_READY` when Architect planning applies
- **AND** Main SHALL select a new Builder when the new risk requires one, preserving task repair accounting
- **AND** newly discovered observable-contract changes during NORMAL SHALL establish the required OpenSpec contract and appropriate classification before dependent work rather than silently staying NORMAL.

### Requirement: Verified and recoverable archive closure
In MULTIAGENT, Main and Architect SHALL close approved CONTRACT and CORE_RISK changes through applicable verification, a scoped pre-archive Git checkpoint, Architect archive and post-archive checks before Main returns DONE. NORMAL and TRIVIAL SHALL use checks selected by actual scope without DOCS_CLOSE, checkpoint or archive. Java including Java tests, build configuration, database/migrations, dependencies and shared runtime launch/operations instructions SHALL require the complete gate before each affected commit; mixed scope SHALL use the union of checks. DEFAULT SHALL retain its existing independent closure ownership.

#### Scenario: Gate failure ownership
- **WHEN** a required final check fails
- **THEN** a Builder-attributable failure SHALL return to that Builder within the cumulative authorized time/resource budget
- **AND** a contract or specification defect SHALL return to Architect planning with `reason = CONTRACT_CHANGED`, while a documentation defect SHALL return to Architect repair
- **AND** infrastructure and unrelated pre-existing failures SHALL return `BLOCKED`; infrastructure retries SHALL NOT consume implementation repairs.

#### Scenario: Successful archive closure
- **WHEN** CONTRACT or CORE_RISK approval closure and all applicable final checks pass
- **THEN** Main SHALL record a scoped Git checkpoint and before-archive porcelain status without staging or committing unrelated owner changes
- **AND** Architect SHALL archive only the authorized change using the OpenSpec CLI
- **AND** Main SHALL inspect the exact archive diff and rerun strict all-item validation, doctor, targeted repository conventions and diff checking
- **AND** Main SHALL return DONE only after every post-archive check passes.

#### Scenario: Archive restoration
- **WHEN** a MULTIAGENT post-archive check fails
- **THEN** only exact paths changed by that archive SHALL be restored from the recorded checkpoint and only that new archive copy SHALL be removed
- **AND** active-change bytes, accepted-spec bytes, unrelated owner work and the real Git index SHALL be preserved
- **AND** destructive broad reset, whole-tree checkout, clean, owner-work stash and manual reversal of accepted-spec patches SHALL NOT be used
- **AND** the task SHALL return to Architect for the active-change correction, applicable verification and archive again without resetting cumulative resource accounting
- **AND** inability to prove scoped restoration safe SHALL return `BLOCKED` before destructive action.

### Requirement: Verified RED reason before test freeze
In MULTIAGENT, Builder SHALL freeze tests only after verifying that each failing test fails for the requirement-derived reason. Builder SHALL record the requirement/acceptance-criterion, expected and actual result for each failing test in the RED evidence before freeze and repeat that evidence in BUILD_DONE. Before freeze, a wrong target, wrong assertion or setup error SHALL be corrected and RED rerun without reviewer permission; this pre-freeze correction is the exception to the suspected-test-defect referral, not permission to change the contract. After freeze, proven synthetic fixture/setup errors MAY be corrected without prior owner or Reviewer permission, preserving contradiction/change/rerun evidence and subsequent independent review; no acceptance criterion, assertion strength or accepted raw evidence SHALL change.

#### Scenario: Establishing valid RED before freezing tests
- **WHEN** Builder inspects a failing test before freeze
- **THEN** Builder SHALL verify and record its requirement/acceptance-criterion, expected and actual result before accepting RED and freezing the test
- **AND** a failure unrelated to the requirement SHALL be corrected and RED rerun before freeze without reviewer permission
- **AND** BUILD_DONE SHALL repeat the per-test RED reason evidence, while any post-freeze correction SHALL disclose `tests_changed_after_red`, use applicable new meaningful behavioral RED for an establishing-test change and receive subsequent independent review; unresolved requirement ambiguity SHALL receive technical diagnosis.

### Requirement: Lost Builder or Reviewer session recovery
In MULTIAGENT, if the same Builder or Reviewer session cannot be resumed, Main SHALL start a fresh session of the same role and configured model/effort, pass the contract, current diff, RED/GREEN evidence, open review items and remaining cumulative time/resource budget, preserve the repair history and resource usage and disclose the session replacement in the handoff. Session loss alone SHALL NOT require an owner decision.

#### Scenario: Resuming work after session loss
- **WHEN** Main cannot resume the assigned Builder or Reviewer session
- **THEN** Main SHALL replace it with a fresh session of the same role and configured model/effort and supply the contract, current diff, RED/GREEN evidence, open review items and remaining cumulative time/resource budget
- **AND** the repair history and resource usage and Reviewer independence SHALL be preserved and the handoff SHALL state that the session was replaced
- **AND** session loss alone SHALL NOT be routed to an owner decision.

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
- **THEN** the active design SHALL record a compact exact rule-change ledger with owner authority and resulting location; unchanged obligations in the historical instruction-diet audit at commit `e7593814afef6ce47dc46c41e20628eebe50a85c` SHALL remain reachable at their existing mandatory controlling sources
- **AND** a duplicate rendering MAY be removed only while the complete obligation remains reachable
- **AND** moving a safety rule SHALL NOT reduce its scope, approval requirements or evidence standard unless an explicit owner-authorized normative change records the exact changed rule, resulting location and reason
- **AND** workflow-simplification SHALL preserve instruction-diet routing and all safeguards except exact owner-authorized changes recorded in that ledger.

#### Scenario: Historical ledger outside the working tree
- **WHEN** an agent performs rule-preservation review and `docs/agents/instruction-diet-audit.md` is absent from the working tree
- **THEN** it SHALL read the ledger from commit `e7593814afef6ce47dc46c41e20628eebe50a85c`
- **AND** the absence of the working-tree file SHALL NOT waive retention of unchanged safety obligations or the compact disposition of exact owner-authorized changes.

### Requirement: Conditional closure procedure
MULTIAGENT SHALL keep ordinary PLAN, BUILD and REVIEW in its existing workflow and SHALL load `docs/agents/close-archive.md` only for DOCS_CLOSE, complete final gate, archive, archive recovery or post-archive work.

#### Scenario: Ordinary phase read path
- **WHEN** Main or an assigned role performs ordinary PLAN, BUILD or REVIEW
- **THEN** it SHALL NOT be required to read the closure procedure
- **AND** the common workflow SHALL retain Main's gate ownership, closure order and the mandatory trigger for loading the procedure before closure work
- **AND** risk classification, phase/role routing, RED/freeze, repair accounting, review independence and lost-session recovery SHALL remain in the existing workflow.

#### Scenario: Closure read path
- **WHEN** approved CONTRACT/CORE_RISK work enters DOCS_CLOSE or any later closure operation, or NORMAL enters an integration-required complete final gate
- **THEN** the responsible Main or Architect SHALL load the closure procedure before acting
- **AND** NORMAL SHALL load only the existing procedure's applicable full-gate guidance, without DOCS_CLOSE or archive
- **AND** CONTRACT/CORE_RISK SHALL retain all full-gate commands when integration impact requires them, and all applicable scoped checks, ownership routes, raw-byte checkpoint, concurrent-edit checks, CLI archive, exact mutation inspection, post-checks and scoped recovery protections unchanged
- **AND** TRIVIAL SHALL NOT load the closure procedure or perform DOCS_CLOSE/archive
- **AND** DEFAULT SHALL retain its responsibility contract and apply the shared scope-based check selection without importing MULTIAGENT protocol.

### Requirement: Local skill routing and project authority
The seven repository-local OpenSpec skills SHALL retain their distinct operations with one-sentence descriptions beginning `Use when`, while preserving useful non-conflicting selection, scope, artifact, merge and validation safeguards.

#### Scenario: Optional explore examples
- **WHEN** explore is loaded
- **THEN** long conversation and diagram examples SHALL be available through an optional `references/examples.md` in that skill
- **AND** mandatory write authorization, scope, context discovery and artifact rules SHALL remain in the skill body
- **AND** no safety rule SHALL exist only in an optional example.

#### Scenario: Builder task completion
- **WHEN** Builder finishes or verifies an implementation task in MULTIAGENT
- **THEN** Builder SHALL report completion evidence without changing OpenSpec task checkboxes, and SHALL edit documentation/instructions only when the bounded owner-authorized assignment explicitly includes those paths
- **AND** where an OpenSpec change exists, Architect SHALL update verified task checkboxes in DOCS_CLOSE after APPROVE; NORMAL SHALL have no artificial checkboxes or DOCS_CLOSE
- **AND** partial, deferred or unverified behavior SHALL NOT be marked complete.

#### Scenario: Archive remains project controlled
- **WHEN** the archive skill is invoked for this project
- **THEN** archive mutation SHALL use the authorized OpenSpec CLI operation after the selected workflow's project approval and applicable verification gate in both DEFAULT and MULTIAGENT
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
- **THEN** generic planning, update, exploration, apply, verification, sync or archive wording SHALL NOT revoke that authorization or demand a second authorization merely because planning was invoked
- **AND** propose SHALL perform only planning during its own operation; Main SHALL control later authorized transitions in MULTIAGENT, and Control SHALL own planning and coordinate later authorized Developer implementation in DEFAULT under its existing workflow
- **AND** a planning-only request SHALL stop without implementation in either mode, at PLAN_READY only in MULTIAGENT and after presenting the planning artifacts in DEFAULT without importing MULTIAGENT phases or statuses
- **AND** unresolved intent, expanded scope, silence or answers to discovery questions SHALL NOT be treated as new authorization.

#### Scenario: OpenSpec skills do not invent low-risk changes
- **WHEN** MULTIAGENT routing identifies TRIVIAL or NORMAL
- **THEN** generic OpenSpec skill entry wording SHALL NOT require a dummy OpenSpec change or select an unrelated active change
- **AND** applicable existing Main or Architect/Builder responsibility SHALL govern that task instead
- **AND** the actual change-based artifact, selection, roots/stores, merge, retirement, CLI archive and mutation-safety safeguards SHALL remain effective; DEFAULT SHALL retain its session structure with shared scope-check wording.

### Requirement: Strictly trivial direct editing
In MULTIAGENT, Main SHALL perform ordinary bounded local changes within accepted requirements and owner authority. Strictly TRIVIAL editorial work SHALL remain distinguishable from behavior/requirement changes, but a non-TRIVIAL label alone SHALL NOT require a separate Architect. Architectural decisions, material ambiguity and critical-guarantee changes SHALL receive Architect participation and critical changes SHALL receive independent review regardless of author.

#### Scenario: Eligible editorial correction
- **WHEN** a task changes only clearly non-normative wording or formatting without changing meaning, claims, link/path targets or any excluded file type
- **THEN** Main SHALL perform the edit directly without subagents, OpenSpec, review, overlap check, DOCS_CLOSE or archive
- **AND** Main SHALL return DONE only after the applicable facts, consistency, links and diff checks pass; a document-only change SHALL NOT require automatic Maven.

#### Scenario: Trivial scope no longer proven
- **WHEN** an exclusion, mixed semantic scope or uncertainty appears before completion
- **THEN** Main SHALL reassess actual scope and guarantees, routing to Architect when its participation criteria apply before dependent work
- **AND** a failed check SHALL NOT expand authorized scope or be hidden as success; a bounded technical repair within existing authority MAY proceed without repeated owner permission.

### Requirement: Tier-specific work and test-first scope
MULTIAGENT NORMAL SHALL use a bounded accepted-source contract without OpenSpec, overlap check, DOCS_CLOSE or archive. Main SHALL classify ordinary work and MAY make small local changes; Architect SHALL participate when architecture, material ambiguity or critical guarantees require it. CONTRACT and CORE_RISK requirement/system changes SHALL use OpenSpec and protected archive with applicable verification. A bounded approved-budget probe without holdout, outcomes or evidence admission SHALL use an existing procedure description and receipt rather than a separate change.

#### Scenario: Internal accepted behavior
- **WHEN** the classifier has assessed the concrete changed guarantees and finds none critical and the task preserves accepted observable behavior
- **THEN** the NORMAL route SHALL use a bounded contract tied to existing accepted sources and useful appropriate tests without a new contract/state/evidence file
- **AND** a bugfix restoring that accepted behavior SHALL require valid behavioral RED before implementation
- **AND** a bugfix changing accepted behavior SHALL be CONTRACT instead, unless a TR trigger requires CORE_RISK.

#### Scenario: Red requirement precedence
- **WHEN** the classifier chooses test mode for non-TRIVIAL work
- **THEN** executable critical-guarantee changes and executable bugfixes SHALL use applicable meaningful behavioral RED; documentation-only work SHALL use RED_NOT_REQUIRED
- **AND** other CONTRACT work SHALL use recorded RED_REQUIRED or RED_NOT_REQUIRED with a concrete reason
- **AND** other NORMAL work SHALL use appropriate useful tests with the reason recorded in its existing test-mode handoff
- **AND** all valid establishing RED SHALL invoke semantic freeze regardless of tier
- **AND** critical-guarantee documentation SHALL use facts, consistency, links and requirements, with independent review for substantive research-rule changes; artificial executable failure SHALL NOT be required.

#### Scenario: Examples do not bypass triggers
- **WHEN** a task concerns telemetry, logging, refactoring, tests, non-security configuration or source comments
- **THEN** the classifier SHALL assess concrete changed guarantees and consequences before considering NORMAL
- **AND** an affected critical guarantee SHALL select CORE_RISK; material credible uncertainty SHALL require Architect assessment before proceeding
- **AND** an observable/public or accepted contract change with no trigger SHALL select CONTRACT
- **AND** mixed scope SHALL receive the highest applicable tier.

### Requirement: Bounded Main context
Main SHALL coordinate using bounded summaries and source paths when those suffice, without replacing durable contract and verification truth with conversational memory.

#### Scenario: Deep inspection handoff
- **WHEN** a decision requires deep reading of large source files, raw logs, reports or a complete diff
- **THEN** Main MAY route that inspection to the appropriate existing role when useful, and MAY inspect the sources directly for ordinary bounded work and receive bounded findings with evidence paths
- **AND** Main SHALL retain permission to inspect exact relevant excerpts needed for routing, blockers, the final gate or archive mutation inspection
- **AND** summary size SHALL NOT excuse omitted failures, invariant evidence or required checks.

#### Scenario: Durable truth after compaction
- **WHEN** context is summarized, compacted or a session is replaced
- **THEN** current truth SHALL remain recoverable from existing OpenSpec artifacts, code/tests and existing evidence sources
- **AND** no new state, capsule or evidence file SHALL be introduced solely for context management
- **AND** existing self-contained handoffs and session recovery SHALL preserve cumulative time/resource accounting and independent review.

## ADDED Requirements

### Requirement: Autonomous concise authorized continuation
Every final task or phase report SHALL state status, result, concrete evidence, blockers/material risks and the next allowed action. Existing authorized phases SHALL continue without repeat permission; phase readiness or approval SHALL NOT be represented as the completed user goal.

#### Scenario: Ready phase continues
- **WHEN** PLAN_READY or APPROVE completes an authorized phase
- **THEN** Main SHALL continue the next authorized action
- **AND** the report SHALL include “Следующее разрешённое действие: …” and identify any concrete missing authority or evidence at a real blocker.

### Requirement: Critical review challenges assumptions
Critical-guarantee changes SHALL receive independent review regardless of whether Main, Architect or Builder authored them.

#### Scenario: Independent assumption challenge
- **WHEN** an independent Reviewer reviews critical guarantees or a risk downgrade
- **THEN** it SHALL answer “Какое предположение реализации или контракта может быть неверным?” (What implementation or contract assumption could be wrong?) and check test meaning against controlling requirements
- **AND** it SHALL distinguish blocking defects from optional improvements; optional comments alone SHALL NOT force repair
- **AND** authors SHALL NOT approve their own holdout, exact financial arithmetic, accepted evidence, migration or secret-safety changes.

### Requirement: Bounded exploratory probe applicability
A prospective bounded research probe within free or approved budget, excluding holdout and outcome calculations, SHALL require only a short description in the existing procedure and a final receipt, with checks appropriate to actual tool dependencies and effects.

#### Scenario: Nonconfirmatory probe
- **WHEN** the probe question, source, period, limits, recording location and stop conditions are established
- **THEN** the authorized bounded probe MAY run without a separate OpenSpec change
- **AND** UNKNOWN, incomplete coverage and negative results SHALL remain valid exploratory results, never accepted data or D1/P1 passage
- **AND** no paid use beyond approved budget, holdout access, outcome computation or accepted-evidence mutation SHALL be inferred.

#### Scenario: Evidence admission requires contract
- **WHEN** results are proposed for D1/P1 admission, or system behavior or accepted requirements change
- **THEN** the appropriate OpenSpec contract SHALL be required
- **AND** an existing change covering that exact scope SHALL be reused rather than duplicated
- **AND** previous census/probe rules, attempts, receipts and frozen evidence SHALL remain governed by their original contract, without retroactive reclassification or permission.

### Requirement: Process simplification follow-up measurement
The next two or three tasks after the reform SHALL briefly record time to first verifiable result, repair returns and later-discovered defects in their existing final reports.

#### Scenario: Follow-up without closure delay
- **WHEN** reform completion or a follow-up task is reported
- **THEN** available comparable baseline measurements SHALL be distinguished from unavailable data and SHALL NOT be invented
- **AND** reform closure SHALL NOT wait for future tasks or add measurement infrastructure.
