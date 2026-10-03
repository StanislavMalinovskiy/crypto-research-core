# MULTIAGENT closure and archive

Read this procedure only before approved DOCS_CLOSE, complete final gate, archive, archive recovery or post-archive work. Ordinary PLAN, BUILD and REVIEW remain in [the MULTIAGENT workflow](../AGENT_WORKFLOW_MULTIAGENT.md); DEFAULT uses its independent [workflow](../AGENT_WORKFLOW.md).

CONTRACT and CORE_RISK use the full procedure. NORMAL uses applicable checks below after appropriate review, without DOCS_CLOSE and without archive, then Main returns DONE. Checkpoint, archive, post-archive and recovery sections apply only when archiving CONTRACT/CORE_RISK. TRIVIAL does not load this procedure and uses its editorial scope checks in the common workflow.

## DOCS_CLOSE

In DOCS_CLOSE after APPROVE, update only completion status, task checkboxes, evidence links, and non-semantic documentation; return APPROVE. If semantics must change, return ESCALATE with reason=CONTRACT_CHANGED to reopen PLAN; only the completed new PLAN may return PLAN_READY. Do not archive during DOCS_CLOSE.

In ARCHIVE, require Main's applicable verification PASS and scoped pre-archive checkpoint. Use the OpenSpec CLI for only the authorized change; return APPROVE or BLOCKED. Main owns exact mutation inspection, post-archive verification, scoped raw-byte restoration if necessary and final DONE. Follow the workflow recovery rules; no broad resets, owner-work loss or reset of cumulative time/resources.

## Complete final gate

Main selects checks by actual dependencies and effects after DOCS_CLOSE for CONTRACT/CORE_RISK, or applicable review for NORMAL. The full gate is required before each commit affecting Java including tests, build configuration, DB/migrations, dependencies or shared runtime launch/operations instructions. Isolated documents/research tools use scoped checks; mixed work uses their union. Later checked-input changes require affected checks again. Run required commands from the repository root. The independent test-integrity preflight must pass before Maven; on Unix, `./mvnw clean verify` is equivalent to the Windows wrapper command:


```powershell
pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1
mvnw.cmd clean verify
openspec validate --all --strict --no-interactive
openspec doctor
git diff --check
```

On Windows, first run `docker version` in the same escalated host access context used for Maven, outside the restricted sandbox. Preflight passes before Maven. Any nonzero required check blocks completion; never narrow or skip it. Route failures by ownership:

- implementation or test issue → same implementing author (Main or assigned Builder), review and applicable checks;
- documentation issue → same documentation author and applicable review; contract issue → Architect PLAN with CONTRACT_CHANGED;
- infrastructure issue or unrelated pre-existing failure → BLOCKED with exact command and cause.

Gate repairs return to the same author and review within cumulative time/resources. Repeated defects, absent verifiable progress or exhausted agreed resources trigger independent cause/approach/remaining-budget/next-result diagnosis. No numeric repair stop or reset on replanning/session replacement; iteration number alone is not an owner decision. Infrastructure retries are not artifact repairs. The gate starts no new budget.

### Compact truthful output

Capture full output through plain shell redirection or an existing gate mechanism, outside a Maven-cleaned directory. Save the exact command's exit code immediately before formatting or searching logs; no new wrapper or state/evidence file is needed.

- PASS: exact command/check, exit code zero, compact available counts and PASS. Test counts include failures, errors and skipped; unavailable counts are stated, never invented.
- FAIL: exact command, captured exit code, failed test/check/plugin, bounded relevant error excerpt and path to full output. List remaining unrun checks.

A required skipped or missing check is not PASS even with exit code zero. Successful command exit alone does not prove required tests ran. Never hide failure or skipped checks behind quiet output, replace the command's status with a formatter's, or narrow the complete Maven gate.

## Checkpoint and archive

After applicable verification PASS, Main records `git status --porcelain=v1`, observed HEAD, real-index identity, exact task paths and prospective archive mutations. Create a pre-archive checkpoint using a temporary Git index and `commit-tree` held by a task-specific ref; leave the live branch and real index unchanged. Add only task-owned content and accepted specs needed by this archive to that temporary index. Exclude owner hunks, hooks and IDE/MCP files. If safe separation of shared paths cannot be proved, return BLOCKED.

Preserve raw-byte snapshots of active-change files and accepted specs using `hash-object -w --no-filters`, retaining blob IDs in the checkpoint tree/ref. Record path existence and SHA-256 beside porcelain status. Inspect attributes and filters: normal add/checkout may transform line endings. Prove the chosen literal-path restoration reproduces recorded hashes without changing the real index. Recheck HEAD and affected path hashes before archive; do not overwrite concurrent edits.

Main supplies the checkpoint to Architect, which runs `openspec archive <change-id> --yes`. Only this active change, its new dated archive copy and accepted specs declared by the delta may change.

## Post-archive verification

Main compares before/after porcelain status and exact path hashes against that allowlist and runs:

```powershell
openspec validate --all --strict --no-interactive
openspec doctor
mvnw.cmd -Dtest=RepositoryConventionsTest test
git diff --check
```

These post-archive checks supplement the applicable pre-archive verification. Main returns DONE only when they pass and the archive diff remains in scope.


## Scoped recovery

On post-archive failure, first verify no concurrent edits to affected paths. Restore only exact paths changed by this archive from the checkpoint's raw bytes and original existence; remove only the new archive copy. Verify pre-archive hashes and the unchanged real index. Never reset-hard, use whole-tree checkout, git clean, stash owner work or hand-reverse accepted-spec patches. If scoped recovery cannot be proved safe, return BLOCKED before destructive action.

Return the restored active change to Architect for correction. Respect author ownership, reopen planning/review for semantic changes and retain repair accounting. Run all applicable checks, create a fresh checkpoint, archive and perform post-checks again. Recovery grants no unlimited retry loop.
