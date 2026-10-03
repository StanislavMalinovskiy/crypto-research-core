## Context

See proposal.md for motivation. Mode is MULTIAGENT (`[agents].enabled = true`). Every deletion target is tracked by git and documentation-only. Reference evidence gathered in PLAN with `git grep -F` over all tracked files plus a working-tree grep of untracked owner files, `.claude/**`, `.codex/**` and `.agents/**`:

- `docs/notes/superseded-adopt-gpt6-agent-routing/`, the four F3_4/F6_2 Builder experiment notes and `docs/notes/img.png` / `img_1.png`: referenced only among the deleted files and as code-span text in archived records (`2026-09-27-instruction-diet-phase-1/design.md`, `2026-09-27-add-recorded-replay-operational-telemetry/design.md`, `2026-09-30-run-bounded-solana-provider-spikes/design.md`). None of these are Markdown links.
- `docs/agents/instruction-diet-audit.md`: archived code-span text in `2026-09-27-instruction-diet-phase-1/design.md` and `2026-09-27-workflow-simplification/design.md`, plus the accepted `repository-conventions` scenario "Rule-preservation review" ("the existing instruction-diet audit rule ledger"). The file has one commit, `e7593814afef6ce47dc46c41e20628eebe50a85c`, and is byte-identical at HEAD.
- `docs/notes/THINK.md` and `docs/notes/AGENT_ORCHESTRATION_RESEARCH.md`: README.md document-table rows only.
- `src/test/**`: no convention test names any target. `RepositoryConventionsTest` validates relative Markdown links in README.md (and other root documents), so the two README rows must be removed together with the files.
- `openspec/specs/**`: only the repository-conventions scenario above.

## Goals / Non-Goals

**Goals:** remove exactly the owner-approved set; keep README links resolvable; keep the accepted rule-preservation obligation intact by pointing it at an immutable git location.

**Non-Goals:** editing archived OpenSpec records, the out-of-scope notes (`EXTERNAL_AUDIT_REVIEW_2026-09-20.md`, `GLM_5_3_MAX_EXTERNAL_AUDIT_REVIEW_2026-09-20.md`, `ARCHITECTURE_AND_RESEARCH_REPLAN_2026-09-30.md`, `SOLANA_PROVIDER_SPIKE_RESEARCH_2026-09-27.md`, `img_2.png`, `img_3.png`), `docs/DELIVERY_PLAN_FIXES.md`, `tools/**`, `.codex/scripts/**`, tests, workflow documents or role configurations.

## Decisions

- **Classification: CONTRACT, risk ROUTINE, risk_triggers none.** Deleting the audit leaves an accepted scenario referencing an absent in-tree artifact, so an accepted-spec text change is required; NORMAL cannot modify accepted specs. Mixed scope takes the higher tier for the whole task. TR-01..TR-12 were each assessed: no persistence, transaction, concurrency, idempotency, migration/data, point-in-time, financial, identity/ordering, provider, module-boundary, research reproducibility/integrity or security surface is touched. TR-11 specifically is not matched: no research dataset, receipt, protocol or evidence referenced by an accepted spec, ADR or research document is removed, and every removed file remains in git history.
- **Spec amendment preserves the obligation (chosen) rather than generalizing it.** Alternative considered: rewrite the scenario to require a disposition for "each affected existing normative rule" without the ledger. Rejected because it changes the evidence standard of a safety rule, which the same scenario forbids without explicit owner authorization. Naming the immutable commit keeps the exact ledger and adds an explicit no-waiver scenario.
- **Archive records stay untouched.** Their mentions are historical code-span text, not links, and archives are immutable records; dangling historical mentions are accepted.
- **Deletions use `git rm`** so the index reflects them; unrelated owner work (modified R1 research docs, `openspec/changes/establish-r1-d1-data-gate/**`, untracked `tools/research/r1/exploratory-probe*.cjs`, intent-to-add `docs/notes/MULTIAGENT_WORKFLOW_DIAGRAMS.md`) is not staged, edited or reverted. Nothing is committed.
- **Ownership and routing:** documentation-only, so Architect performs DOCS and Builder is skipped; same-Architect REVIEW (CONTRACT, no CORE_RISK); Architect DOCS_CLOSE and CLI archive (which syncs the delta) after APPROVE; Main runs the complete final gate. No module, transaction boundary or dependency is affected.
- **Test mode: RED_NOT_REQUIRED.** Not a bugfix and not CORE_RISK. No executable behavior changes and no convention test encodes the reworded scenario or the deleted paths, so a behavioral RED cannot be meaningful. Coverage comes from `RepositoryConventionsTest` (README relative-link resolution and repository conventions), strict OpenSpec validation and a final reference grep.
- **Core invariants:** CI-01..CI-15 not applicable; they govern runtime outcomes, evidence, provider, time, ordering, concurrency and transactions, none of which this change touches. CI-01 (no silent evidence loss) was considered: removed notes are not runtime or research evidence and remain in git history.

## Change budget

Deletions of exactly the 16 tracked files listed in proposal.md; README.md limited to removing the two table rows; this change's artifacts. No other edits.

## Verification

- `git diff --check`
- `mvnw.cmd -Dtest=RepositoryConventionsTest test` with surefire counts confirming execution
- `openspec validate remove-obsolete-agent-notes --strict` and, in Main's gate, the strict all-item validation and doctor
- `git grep -n -F` for each deleted path name outside `openspec/changes/archive/**` and this change, expecting no hits other than this change's artifacts and the amended spec text
- `git status --short` showing the owner's unrelated work unchanged

## Overlap

`openspec list`: only `establish-r1-d1-data-gate` is active; it touches `r1-data-inventory` only. Overlap with `repository-conventions`: none.

## Risks / Trade-offs

- [Archived records mention removed paths] → Accepted as immutable history; mentions are code spans and break no link check.
- [Future agents cannot find the ledger in the tree] → The amended scenario names the exact commit and forbids treating absence as a waiver.
