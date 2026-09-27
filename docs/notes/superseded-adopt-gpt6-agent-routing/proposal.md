## Why

The project still configures GPT-5.6 roles and a superseded Builder comparison although the user has selected a GPT-6 workflow. The accepted agent-guidance specification also describes an older split Developer/Tester protocol that conflicts with the current combined Builder workflow.

## What Changes

- Set the project default to GPT-6 Sol medium and route Architect to Sol high, ordinary Builder to Luna xhigh with an explicit max option, CORE_RISK Builder to Sol medium, Reviewer to Sol medium, and Escalation to Sol high.
- Use a separate fresh Reviewer for every risk level; keep Architect focused on planning and documentation.
- Reconcile accepted agent guidance through a delta specification with the lean Builder, evidence, repair and final-gate contract.
- Rename the old Sol-replaced Builder role to `builder_sol` and update executable role/status validation and relevant regression tests.
- Remove obsolete old-model benchmark results within the user-authorized cleanup scope and update current repository and Notion guidance. Preserve application regression tests and historical accepted changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: GPT-6 model routing, separate review ownership, combined Builder evidence, bounded repair and canonical role validation.

## Impact

Affected paths are project Codex configuration and roles, agent logging/routing regression checks, current workflow documentation and the repository-conventions specification. Main also verifies the workstation default, updates the corresponding Notion pages, and performs authorized benchmark cleanup. No application module, dependency, database schema, transaction, runtime API or production behavior changes. No architectural ADR is required because the application architecture is unchanged.

Non-goals are changing the manually selected workflow switch, starting Orca, changing provider/research work, deleting application tests, or reinterpreting archived evidence as current model results.
