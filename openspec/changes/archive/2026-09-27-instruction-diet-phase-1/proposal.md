## Why

The current agent guidance repeatedly loads orientation and archive procedures during ordinary work, while generic OpenSpec skills conflict with project ownership and closure gates. Phase 1 reduces that repeated context through a small router and one conditional closure reference, preserving every normative obligation and the existing workflow.

## What Changes

- Replace the root guide with a short router (at most 150 lines) using real repository paths, retained source responsibilities, unconditional boundaries and task-impact reading triggers. Make `docs/PROJECT_SUMMARY.md` optional unless onboarding, product orientation or the task requires it.
- Narrow OpenSpec context reads to the affected change/spec, affected modules and applicable ADRs, with Architecture, Testing, Operations and Reproducibility selected by impact.
- Move only DOCS_CLOSE, full-gate, archive, archive-recovery and post-archive detail from the MULTIAGENT workflow into `docs/agents/close-archive.md`; retain ordinary planning, building, review, risk, repair and session recovery in the existing workflow.
- Shorten the seven local skill descriptions to one `Use when ...` sentence. Move long explore examples to one optional reference. Keep all seven skills and their useful safeguards.
- Resolve Builder checkbox ownership, manual archive mutation, warning-based archive readiness, and generic revocation of explicit owner authorization.
- Preserve three Builder entry points and identical normative bodies, with a minimal extension of existing convention checks. Keep Main's context bounded while preserving access to exact evidence and existing durable sources.
- Add only top-level `model_post_turn_compact_threshold_percent = 60` to Codex configuration as an experiment whose actual behavior and efficiency will be checked after implementation.

Non-goals: implementation in this PLAN task; additional phase/protocol files, path aliases, shared-skill frameworks, new roles/skills, new state/evidence infrastructure, ArchUnit or archive helpers, model/effort changes, risk/repair/RED/freeze/review/archive safety changes, production behavior, dependency changes, database migrations or module-boundary changes. Existing owner work remains untouched.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: preserve executable agent guidance through conditional real-path references, enforce Builder-body consistency, reconcile local skill behavior with project authority, and bound Main context handling and the single compaction experiment.

## Impact

No business module, runtime API, production dependency, transaction or database is affected. Changes are limited to agent documentation, seven repository-local skills, the Architect prompt, existing repository-convention tests, the single Codex setting and this active change. The exact file allowlist, complete audit-ledger disposition, six reading traces, estimates and RED strategy are in `design.md`. Main specs remain unchanged until a separately authorized verified CLI archive.
