## Why

The owner asked to remove obsolete, non-normative agent and experiment notes from the working tree. One of them, `docs/agents/instruction-diet-audit.md`, is still named by the accepted `repository-conventions` rule-preservation scenario ("the existing instruction-diet audit rule ledger"), so deleting it without a spec amendment would leave an accepted requirement pointing at an absent artifact.

## What Changes

- Delete the retired `docs/notes/superseded-adopt-gpt6-agent-routing/` tree (`.openspec.yaml`, README, proposal, design, tasks, verification and `specs/repository-conventions/spec.md`).
- Delete the old Builder experiment notes `docs/notes/F3_4_BUILDER_EXPERIMENT_PROTOCOL.md`, `docs/notes/F3_4_BUILDER_EXPERIMENT_RESULTS.md`, `docs/notes/F6_2_BUILDER_EXPERIMENT_PROTOCOL.md` and `docs/notes/F6_2_BUILDER_EXPERIMENT_RESULTS.md`.
- Delete `docs/agents/instruction-diet-audit.md` from the working tree; it remains retrievable from git history at commit `e7593814afef6ce47dc46c41e20628eebe50a85c`.
- Delete the unreferenced screenshots `docs/notes/img.png` and `docs/notes/img_1.png`.
- Delete the idea backlogs `docs/notes/THINK.md` and `docs/notes/AGENT_ORCHESTRATION_RESEARCH.md` and remove their two rows from the README document table.
- Amend the accepted rule-preservation scenario so it names the historical ledger by its immutable git location instead of an in-tree file, preserving the obligation unchanged.

Non-goals: no change to any other note, research document, image (`img_2.png`, `img_3.png` stay), archived OpenSpec record, tool, convention test, workflow rule, role configuration, Java code, dependency or module boundary. Affected modules: none (repository documentation and governance spec only).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: the "Task-scoped instruction disclosure" rule-preservation scenario references the instruction-diet audit ledger by its git-history location and states that its absence from the working tree does not waive the disposition obligation.

## Impact

Documentation-only. Files removed under `docs/notes/**` and `docs/agents/`, two README table rows removed, and one accepted-spec scenario reworded through the delta. Archived OpenSpec records that mention the removed paths in code-span text stay unchanged as history. No runtime, schema, test, configuration or dependency impact.
