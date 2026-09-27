---
name: openspec-archive-change
description: Use when archiving a verified OpenSpec change through project closure gates.
allowed-tools: Bash(openspec:*)
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.13.0"
---

Archive a completed change in the experimental workflow.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Once selected, treat `--store <id>` as sticky for the rest of the workflow. Every unscoped example of those commands below is shorthand: before running it, append the flag. For example, run `openspec status --change "<name>" --json --store "<id>"`, not the unscoped form shown below. Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

`<capability-path>` is the spec directory relative to `specs/` (for example, `user-auth` or `identity/user-auth`). Preserve the full path from each delta spec when resolving its main spec.

**Input**: Optionally specify a change name. If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Steps**

1. **Select the change**

   If a name is provided, use it. Otherwise:
   - Infer from conversation context if the user mentioned a change
   - Auto-select if only one active change exists
   - If ambiguous, run `openspec list --json` to get available changes and ask the user to select one

   When prompting, show only active changes (not already archived).
   Include the schema used for each change if available.

   Always announce: "Using change: <name>" and how to override (e.g., `$openspec-archive-change (Codex) or /openspec-archive-change (other agents) <other>`).

   **Load current archive inputs before the existing archive checks:**

   After resolving the selected change and planning root, run:
   ```bash
   openspec instructions archive --change "<name>" --json
   ```
   Keep the same selected-root flags on this command. This lookup is advisory and
   optional: it only supplies extra prompt inputs, so it must never block archiving.
   If it exits non-zero or returns invalid JSON — for example on an older CLI that
   does not support this command yet — continue the archive workflow with no
   context and no operation guidance. Do not report an error and do not stop.

   A successful response may omit both optional fields. Treat `context` as a
   required prompt-level input: read and consider it, and apply relevant project
   facts, conventions, and constraints. Treat `operationGuidance` as optional
   additive advice: read and consider every entry, and follow entries that are
   applicable and compatible with the built-in archive workflow.

   Keep both fields separate from built-in steps, explicit user choices, resolved
   paths, CLI checks, and command contracts. If context conflicts with one of those
   controlling inputs, report the conflict and preserve the controlling value. If
   guidance is inapplicable or conflicts with a controlling input, do not follow it
   and explain why. Do not infer replacement paths, skipped prompts, or flags from
   either field, and do not copy their text verbatim into specs, change artifacts,
   or archive summaries unless the user separately asks for it. These are
   prompt-level behavior contracts, not enforceable checks.

2. **Check project authority and completion**

   Follow the selected project workflow. In MULTIAGENT, Main supplies APPROVE,
   completed DOCS_CLOSE, complete final-gate PASS and the scoped pre-archive
   checkpoint; Architect acts only in assigned ARCHIVE. Only in MULTIAGENT, read
   [docs/agents/close-archive.md](../../../docs/agents/close-archive.md) only before
   this closure work. DEFAULT Control uses docs/AGENT_WORKFLOW.md for its own
   review, final documentation, complete gate and archive ownership; it does not
   load the MULTIAGENT closure procedure or acquire its checkpoint/phase protocol.
   No generic confirmation, warning, manual move or skip-sync choice overrides
   these prerequisites.

   Run `openspec status --change "<name>" --json`. Use its `schemaName`,
   `planningHome`, `changeRoot`, `artifactPaths` and `actionContext` for
   scope and paths. Check the artifact graph: `done` or deliberately `skipped`
   artifacts satisfy their declared requirements; incomplete required artifacts
   block archive. Read the resolved tasks artifact and count complete and
   incomplete tasks. Incomplete required tasks block archive. If no tasks artifact
   exists, report that fact without inventing one; all applicable project
   implementation and verification evidence remains required.

3. **Assess every declared delta and mutation scope**

   Use only `artifactPaths.specs.existingOutputPaths` from status as delta
   sources. If absent or empty, report no delta specs; infer none from other
   artifacts. Otherwise compare each full capability path with
   `<planningHome.root>/openspec/specs/<capability-path>/spec.md` using the
   resolved root, and summarize every addition, modification, removal and rename.
   Preserve explicit authorized scope; a mismatch or ambiguous selection stops
   dependent mutation. Honor cancellation. No separate agent-driven sync or
   concurrent background sync runs while CLI archive moves the active change.

   Before any CLI main-spec mutation, run
   `openspec instructions specs --change "<name>" --json` once with the same
   selected-root flags. Require exit zero and valid artifact-instruction JSON;
   failure stops before mutation. Omitted `rules` in a valid response means no
   additional rules. Apply returned rules to the content/form of main specs;
   never use them to change paths, archive guidance, CLI behavior or authorization,
   and never copy their text into output. Retain this rule snapshot for the
   post-mutation consistency check. Separately authorized standalone sync keeps
   its existing merge/retirement/validation safeguards.

   Inspect the proposed archive target under `planningHome.changesDir`:
   preserve an existing `YYYY-MM-DD-` change-name prefix, otherwise use the
   current date once. Stop on a collision; do not overwrite an existing archive.
   Preserve `.openspec.yaml` and all change artifacts. In MULTIAGENT, Main must
   confirm the checkpoint's exact mutation scope, unchanged HEAD/index and
   concurrent-edit checks immediately before archive. In DEFAULT, Control
   verifies authorized scope and its existing completion gate before archive.

4. **Perform only the authorized CLI archive**

   With all project prerequisites satisfied, run:

   ```bash
   openspec archive "<name>" --yes
   ```

   Keep the selected-root/store flags where applicable. The CLI owns spec sync
   and archive movement. Do not manually create/move the archive directory or
   bypass spec synchronization. A nonzero CLI result is a blocker, not a success.

5. **Verify exact results through project ownership**

   In MULTIAGENT, Main inspects exact path mutations and runs all required
   post-archive checks from the closure procedure. In DEFAULT, Control owns
   result inspection and the verification required by its own workflow.
   In either mode, compare every declared capability, including
   capabilities not mentioned in CLI output, against the pre-mutation delta:

   - ADDED requirements are present.
   - MODIFIED requirements contain the intended descriptions/scenarios, with
     surviving unmentioned scenarios intact.
   - REMOVED requirements are absent. Any authorized capability retirement
     matches its declared scope; do not silently leave an empty Requirements
     section or treat a deliberately retained capability as deleted.
   - RENAMED requirements exist under the new name and no longer under the old.

   A mismatch, missing required check or failed check blocks completion. In
   MULTIAGENT, use Main's scoped raw-byte recovery after concurrent-edit checks
   and return to its existing author/planning/review routes without resetting
   repair budget. In DEFAULT, report the exact partial mutation and return to
   Control under its existing review/repair rules; do not import the MULTIAGENT
   checkpoint or recovery protocol. In either mode, never hand-reverse accepted
   specs or conceal partial CLI mutation; preserve unrelated owner work.

6. **Display a truthful summary**

   Name the selected change, schema, actual archive location, exact spec sync
   result and every remaining warning/blocker. Say specs synced only after the
   capability comparison and required post-checks pass. Report no-delta scope
   explicitly. In MULTIAGENT, Architect returns its assigned APPROVE/BLOCKED
   status and Main alone returns DONE. In DEFAULT, Control reports the outcome
   under its own completion contract, without specialized statuses. Never claim
   successful completion from directory movement alone.

**Guardrails**

- Announce the selected change; ask when selection or intent is ambiguous.
- Preserve artifact graph, resolved paths, selected store and exact authorized scope.
- Optional advisory archive-input lookup may fail without blocking; status,
  spec-rule lookup, CLI mutation and required project checks may not.
- Missing required behavior, evidence, artifacts or tasks blocks archive;
  generic warning confirmation grants no bypass.
- Only the authorized CLI synchronizes and archives; no manual move or skip-sync.
- Do not run archive while any separate spec sync is still in flight.
- Preserve metadata, collision checks and a truthful account of partial failure.
- Apply relevant context and report conflicts; consider every advisory guidance
  entry and explain inapplicability without replacing controlling project rules.
- Artifact rules constrain written specs, not operation guidance or CLI contracts.
- Never copy context, operation guidance or artifact-rule text into output files.
