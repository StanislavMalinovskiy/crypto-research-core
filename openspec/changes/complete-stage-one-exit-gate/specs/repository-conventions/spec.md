## MODIFIED Requirements

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
