## MODIFIED Requirements

### Requirement: Concise project delivery map
The repository SHALL maintain one concise top-level delivery plan that identifies source boundaries, the current project position, the ordered outcome-oriented stages, the next planned work and each stage's exit result without duplicating detailed product, architecture or change specifications. A completed stage SHALL transition to `Done` and the next stage to `Current` only after the completed stage's exit evidence is satisfied.

#### Scenario: New-session delivery orientation
- **WHEN** a human or AI agent opens the delivery plan in a new session
- **THEN** the document SHALL identify the current stage, current work, next planned change and next business change
- **AND** it SHALL show the route from architecture foundation through an evidence-based research decision
- **AND** execution SHALL remain explicitly deferred until the required evidence and safety design exist.

#### Scenario: Appropriate planning detail
- **WHEN** the delivery plan describes a stage
- **THEN** it SHALL use only the statuses `Done`, `Current`, `Next`, `Planned` or `Deferred`
- **AND** it SHALL describe goals, outcomes, major change groups and exit results rather than Java types, database objects, libraries or algorithms
- **AND** it SHALL link to the authoritative Roadmap, ADR, main spec or active change instead of copying their detailed decisions.

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
