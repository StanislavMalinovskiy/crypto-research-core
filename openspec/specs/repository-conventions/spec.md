# Repository Conventions Specification

## Purpose

Makes long-lived human and AI-assisted development safer by turning dependency and documentation conventions into repeatable verification checks.

## Requirements

### Requirement: Forbidden dependency enforcement
The Maven verification lifecycle SHALL reject modern and legacy JPA, Hibernate ORM, WebFlux, Reactor, R2DBC and Vert.x dependencies anywhere in the resolved dependency graph.

#### Scenario: Dependency policy verification
- **WHEN** the project dependency graph is checked during `verify`
- **THEN** modern or legacy JPA and Hibernate ORM coordinates and all other forbidden dependency families SHALL fail the build, including transitive occurrences
- **AND** allowed synchronous JDBC, servlet and future Bean Validation provider dependencies SHALL remain unaffected.

### Requirement: Markdown hygiene
Repository verification SHALL inspect all tracked Markdown documentation for tool-export-specific markers and broken relative file links, and SHALL inspect `docs/GLOSSARY.md` for obsolete architecture presented as current.

#### Scenario: Documentation verification
- **WHEN** repository convention checks scan tracked Markdown files
- **THEN** tool-export-specific markers SHALL be absent
- **AND** every relative Markdown file link SHALL resolve to an existing repository path.

#### Scenario: Active architecture terminology
- **WHEN** repository convention checks scan `docs/GLOSSARY.md`
- **THEN** legacy module names or database entities SHALL not be presented as current
- **AND** any retained legacy module name or database entity SHALL be locally marked as historical or deferred.

### Requirement: Active planning document consistency
Active repository documentation SHALL distinguish the verified current baseline from target MVP components and deferred options without overriding accepted ADRs or main specifications.

#### Scenario: Current and target baseline review
- **WHEN** a developer reads the Roadmap and Tech Stack
- **THEN** current, target and deferred technologies SHALL be distinguishable
- **AND** caches, external observability infrastructure, providers and partitioning SHALL include activation rules proportional to their architectural impact
- **AND** the documented Java package root SHALL be `io.cryptoresearch`.

#### Scenario: Durable change navigation
- **WHEN** an OpenSpec change is archived or a new active change is created
- **THEN** README navigation SHALL remain valid without naming a transient active change
- **AND** the completed bootstrap archive and next planned business change SHALL remain discoverable.

#### Scenario: Persistence change ordering
- **WHEN** the planned market-data changes introduce or modify persistence
- **THEN** idempotency SHALL be an acceptance criterion of each persistence change
- **AND** the storage foundation SHALL precede provider ingestion in the planned change sequence.

### Requirement: Concise project delivery map
The repository SHALL maintain one concise top-level delivery plan that identifies source boundaries, the current project position, ordered outcome-oriented stages, short status-bearing work packages within every stage, the next planned work and each stage's exit result without duplicating detailed product, architecture or change specifications. A completed stage SHALL transition to `Done` and the next stage to `Current` only after the completed stage's exit evidence is satisfied.

#### Scenario: New-session delivery orientation
- **WHEN** a human or AI agent opens the delivery plan in a new session
- **THEN** the document SHALL identify the current stage, current work, next planned change and next business change
- **AND** it SHALL show the completed, current, next and planned work packages inside the stage route
- **AND** it SHALL show the route from architecture foundation through an evidence-based research decision
- **AND** execution SHALL remain explicitly deferred until the required evidence and safety design exist.

#### Scenario: Appropriate planning detail
- **WHEN** the delivery plan describes a stage
- **THEN** it SHALL use only the statuses `Done`, `Current`, `Next`, `Planned` or `Deferred`
- **AND** each work package SHALL remain a short one-to-two-line outcome or change-sized description rather than a detailed implementation task
- **AND** it SHALL describe goals, outcomes, major change groups and exit results rather than Java types, database objects, libraries or algorithms
- **AND** it SHALL link to the authoritative Roadmap, ADR, main spec or active change instead of copying their detailed decisions.

#### Scenario: Adaptive work-package map
- **WHEN** implementation evidence, research findings, provider constraints or newly discovered dependencies reveal necessary work
- **THEN** work packages MAY be added, split, reordered or deferred through an explicit Delivery Plan update
- **AND** the plan SHALL state that its task map is directional rather than an immutable commitment
- **AND** active implementation detail and checkbox progress SHALL remain in the relevant OpenSpec change.

#### Scenario: Delivery position maintenance
- **WHEN** an OpenSpec change is archived or project priority explicitly changes
- **THEN** the delivery plan's current and next position SHALL be reviewed and updated when affected
- **AND** ordinary task progress SHALL remain owned by the relevant OpenSpec change rather than being mirrored as percentages in the delivery plan
- **AND** plan concision SHALL remain an editorial review concern rather than a new automated line-count or CI gate.

#### Scenario: Advance after exit evidence
- **WHEN** every exit condition of the current stage has verified evidence
- **THEN** the Delivery Plan SHALL mark that stage `Done` and the next stage `Current`
- **AND** it SHALL identify the first planned work of the new current stage
- **AND** it SHALL not retain a completed checkpoint or archived change as current work.

### Requirement: Agent workflow mode selection
The repository SHALL use `DEFAULT` unless the user has manually enabled project subagents, and SHALL use `MULTIAGENT` only after that explicit opt-in.

#### Scenario: Default mode
- **WHEN** a task starts and `[agents].enabled` is `false`, missing or invalid
- **THEN** the task SHALL use `DEFAULT`
- **AND** no project subagent SHALL be spawned.

#### Scenario: Multiagent mode
- **WHEN** a task starts and the user has manually set `[agents].enabled = true`
- **THEN** the task SHALL use `MULTIAGENT`
- **AND** the supervised project roles MAY be used as needed.

#### Scenario: No automatic mode change
- **WHEN** an agent considers that MULTIAGENT would provide additional assurance
- **THEN** it MAY recommend that mode and explain why
- **AND** it SHALL NOT edit `[agents].enabled`, spawn a project subagent or silently change the current task's mode.

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
- **AND** existing applicable checks and the complete completion gate SHALL still run.

#### Scenario: Completion and handoff
- **WHEN** Developer finishes a DEFAULT pass
- **THEN** Developer SHALL run relevant targeted green checks and the complete local gate
- **AND** the handoff SHALL identify changed files, red and green evidence when applicable, verification results and remaining risks
- **AND** Control SHALL review test meaning before implementation details and independently run the required completion gates.

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

### Requirement: GPT-6 agent model routing
The project SHALL configure Main as `gpt-6-sol / medium` and SHALL use deterministic GPT-6 role routing in MULTIAGENT without changing the manually selected workflow mode.

#### Scenario: Fixed role mapping
- **WHEN** Main dispatches a non-Builder project role
- **THEN** Architect SHALL use `gpt-6-sol / high`
- **AND** fresh Reviewer SHALL use `gpt-6-sol / medium`
- **AND** fresh Escalation SHALL use `gpt-6-sol / high`.

#### Scenario: Risk-based Builder selection
- **WHEN** Architect returns `PLAN_READY` for implementation
- **THEN** ROUTINE SHALL use `builder_luna_xhigh` with `gpt-6-luna / xhigh`
- **AND** STANDARD SHALL use `builder_luna_max` with `gpt-6-luna / max`
- **AND** CORE_RISK SHALL use `builder_sol` with `gpt-6-sol / medium`
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
- **WHEN** the required repository verification sequence starts
- **THEN** test-integrity inspection SHALL execute outside and before the Maven lifecycle whose configuration it inspects
- **AND** a detected bypass SHALL stop verification before `clean verify`
- **AND** the stable quality-gate job SHALL run the same independent preflight before Maven without a conditional or continue-on-error escape.

### Requirement: Durable architecture policies
The repository SHALL record accepted decisions for table ownership and PostgreSQL schemas, transaction and event semantics, background work coordination and bounded blocking concurrency before business implementation begins.

#### Scenario: Architecture policy review
- **WHEN** an architecture-affecting change is reviewed
- **THEN** every table SHALL have one owning module and every transaction SHALL have one owning application use case
- **AND** background work SHALL use idempotent database-backed claiming across instances
- **AND** virtual-thread workloads SHALL have explicit provider, database, queue, timeout and retry limits.

### Requirement: Explicit persistence access policy
Repository architecture documentation SHALL define how an owning module selects a persistence mechanism and SHALL prohibit direct cross-module data access.

#### Scenario: Persistence mechanism selection
- **WHEN** a module designs a persistence operation
- **THEN** simple aggregate CRUD SHALL use Spring Data JDBC only when aggregate semantics fit
- **AND** projections, explicit queries, upserts and targeted writes SHALL use `JdbcClient`
- **AND** high-volume writes SHALL use prepared JDBC batch operations
- **AND** DDL SHALL remain owned exclusively by Flyway.

#### Scenario: Module data access
- **WHEN** one module needs data owned by another module
- **THEN** it SHALL use the owner's public API, immutable projection, defined event or separately approved analytical read model
- **AND** it SHALL not use the owner's table, SQL, repository, entity or row mapper directly.

### Requirement: Documented engineering operating contracts
The repository SHALL document configuration, secret handling, health, logging and test-level rules before business capabilities depend on them.

#### Scenario: Secret-bearing configuration
- **WHEN** a change introduces a secret or mandatory production setting
- **THEN** the value SHALL come from external configuration or a secret store and SHALL fail fast when required but absent
- **AND** it SHALL not appear in Git, logs or exception messages
- **AND** validation infrastructure SHALL be introduced only with a real configuration or input contract that uses it.

#### Scenario: Logging and measurement dimensions
- **WHEN** application or workload telemetry is designed
- **THEN** logs SHALL exclude secrets and complete sensitive provider payloads
- **AND** correlation fields SHALL identify workload, run, provider, chain and operation where applicable
- **AND** wallet addresses, token addresses and transaction hashes SHALL not be metric tags.

#### Scenario: Test-level selection
- **WHEN** a change chooses a test level
- **THEN** pure rules SHALL use unit tests and module use cases SHALL use module-scoped tests when they exist
- **AND** SQL, repositories and migrations SHALL use PostgreSQL Testcontainers
- **AND** architecture verification and a small full-startup smoke test SHALL remain part of the Maven lifecycle.

### Requirement: Implicit nondeterminism guard
Repository verification SHALL reject unapproved implicit wall-clock reads and unseeded randomness entry points in production module source code.

#### Scenario: Production source verification
- **WHEN** the Maven repository-convention tests inspect production sources beneath the application module root
- **THEN** direct machine-clock and unseeded-randomness entry points SHALL fail verification
- **AND** explicitly supplied time sources, reference instants and seeded random sources SHALL remain allowed.

### Requirement: Bounded technical escalation
MULTIAGENT technical escalation SHALL use a fresh read-only role, return one bounded verdict and preserve author ownership and the existing repair budget.

#### Scenario: Bounded technical escalation
- **WHEN** a technical dispute has no bounded safe resolution or survives the authorized repair budget
- **THEN** Main SHALL dispatch a fresh read-only Sol High Escalation agent for one exact question
- **AND** it SHALL return exactly one `verdict` attribute from `REPAIR`, `REPLAN`, `APPROVE`, `OWNER_DECISION`
- **AND** `REPAIR` SHALL use the existing author and remaining repair budget, `REPLAN` SHALL reopen Architect planning with `reason = CONTRACT_CHANGED` and `subreason = ESCALATION_REPLAN`, `APPROVE` SHALL resume approval closure, and `OWNER_DECISION` SHALL return a blocked owner decision
- **AND** owner intent or scope ambiguity SHALL go directly to the owner without technical escalation.

### Requirement: Architect-owned stable risk classification
Architect alone SHALL classify task risk from the single authoritative CORE_RISK trigger list in `docs/AGENT_WORKFLOW_MULTIAGENT.md`; every other active guidance location SHALL reference that list instead of defining a duplicate. Main SHALL own intake, scope coordination, routing, the full gate and DONE without classifying risk.

#### Scenario: Risk fixed at planning
- **WHEN** Architect returns `PLAN_READY`
- **THEN** the capsule SHALL fix `risk`, matched `risk_triggers` or explicit `none`, applicable invariants, test mode and bounded change budget
- **AND** risk SHALL remain fixed during implementation, review and repairs
- **AND** lowering a previously fixed risk SHALL require explicit owner direction.

#### Scenario: New scope or risk trigger
- **WHEN** an author or reviewer discovers a new risk or scope trigger
- **THEN** Main SHALL return it to Architect with `reason = CONTRACT_CHANGED` and `subreason = RISK_CHANGED`
- **AND** Architect SHALL issue a new `PLAN_READY` before further dependent implementation
- **AND** Main SHALL select a new Builder when the new risk requires one, preserving task repair accounting.

### Requirement: Verified and recoverable archive closure
Main and Architect SHALL close approved changes through full verification, a scoped pre-archive Git checkpoint, Architect archive and post-archive checks before Main returns DONE.

#### Scenario: Gate failure ownership
- **WHEN** a complete final gate fails
- **THEN** a Builder-attributable failure SHALL return to that Builder within the existing repair budget
- **AND** a contract or specification defect SHALL return to Architect planning with `reason = CONTRACT_CHANGED`, while a documentation defect SHALL return to Architect repair
- **AND** infrastructure and unrelated pre-existing failures SHALL return `BLOCKED`; infrastructure retries SHALL NOT consume implementation repairs.

#### Scenario: Successful archive closure
- **WHEN** approval closure and the complete final gate pass
- **THEN** Main SHALL record a scoped Git checkpoint and before-archive porcelain status without staging or committing unrelated owner changes
- **AND** Architect SHALL archive only the authorized change using the OpenSpec CLI
- **AND** Main SHALL inspect the exact archive diff and rerun strict all-item validation, doctor, targeted repository conventions and diff checking
- **AND** Main SHALL return DONE only after every post-archive check passes.

#### Scenario: Archive restoration
- **WHEN** a post-archive check fails
- **THEN** only exact paths changed by that archive SHALL be restored from the recorded checkpoint and only that new archive copy SHALL be removed
- **AND** active-change bytes, accepted-spec bytes, unrelated owner work and the real Git index SHALL be preserved
- **AND** destructive broad reset, whole-tree checkout, clean, owner-work stash and manual reversal of accepted-spec patches SHALL NOT be used
- **AND** the task SHALL return to Architect for the active-change correction, full verification and archive again without resetting repair accounting
- **AND** inability to prove scoped restoration safe SHALL return `BLOCKED` before destructive action.

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
