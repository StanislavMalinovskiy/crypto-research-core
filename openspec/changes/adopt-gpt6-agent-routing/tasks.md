## 1. Contract and documentation

- [x] 1.1 Record the GPT-6 routing, review ownership, bounded evidence protocol and cleanup scope in proposal, design and delta; verify with `openspec validate adopt-gpt6-agent-routing --strict --no-interactive`.
- [x] 1.2 Update current workflow/root/README guidance and directly affected links; verify model/routing search and Markdown convention checks without rewriting historical accepted changes.

## 2. Executable routing

- [x] 2.1 Derive focused logger and repository governance assertions from the delta, observe expected behavioral RED before changing executable routing, and record test hashes plus pre-implementation diff.
- [x] 2.2 Migrate default and role model/effort settings, rename `builder_terra` to `builder_sol`, add the explicit Luna max option, and restrict Architect to PLAN/DOCS_CLOSE; verify the exact role map and unchanged mode switch.
- [x] 2.3 Update logger role/phase/status validation and relevant governance checks; prove frozen-test GREEN using `pwsh -NoProfile -File .codex/scripts/log-agent-activity.tests.ps1` and `mvnw.cmd -Dtest=RepositoryConventionsTest test`.

## 3. User documentation and obsolete artifacts

- [x] 3.1 Verify the workstation default is `gpt-6-sol / medium`, changing only these defaults if necessary, and record the effective project/workstation agreement.
- [x] 3.2 Remove only the user-authorized obsolete old-model benchmark artifacts; record exact removed paths and verify application regression tests and historical accepted changes are retained.
- [x] 3.3 Update the relevant Notion guidance to the final routing and reread the updated pages to verify agreement with repository documentation.

## 4. Review and completion

- [x] 4.1 Obtain fresh Sol medium review of the stable diff, contract, behavioral evidence and applicable invariants; complete any bounded repairs and record the consolidated approval.
- [x] 4.2 After approval, close documentation and independently run `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, host-context `docker version`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, and `git diff --check`; record exact results or blockers.
- [ ] 4.3 Synchronize the approved repository-conventions delta and archive only after implementation and verification, then rerun strict validation and verify final documentation links.

Evidence and pending completion checks are recorded in [verification](verification.md).
Task 4.3 is partially complete: specification sync and post-sync validation passed; archive remains pending.
