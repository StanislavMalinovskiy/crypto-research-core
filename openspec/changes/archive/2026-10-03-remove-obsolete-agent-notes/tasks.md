## 1. Architect DOCS: removals

- [x] 1.1 `git rm -r` `docs/notes/superseded-adopt-gpt6-agent-routing/` (all seven tracked files including `.openspec.yaml`); verify the directory is absent and `git status --short` lists only those deletions plus pre-existing owner work.
- [x] 1.2 `git rm` the four F3_4/F6_2 Builder experiment notes, `docs/agents/instruction-diet-audit.md`, `docs/notes/img.png` and `docs/notes/img_1.png`; verify `img_2.png`, `img_3.png` and every out-of-scope note still exist.
- [x] 1.3 `git rm` `docs/notes/THINK.md` and `docs/notes/AGENT_ORCHESTRATION_RESEARCH.md` and remove only their two rows from the README.md document table; verify `git diff README.md` shows exactly two removed lines.

## 2. Verification

- [x] 2.1 Run `git diff --check` and `mvnw.cmd -Dtest=RepositoryConventionsTest test`; verify success and that the surefire report shows the tests executed with zero failures and errors.
- [x] 2.2 Run `openspec validate remove-obsolete-agent-notes --strict`; verify it passes.
- [x] 2.3 Run `git grep -n -F` for each removed path name excluding `openspec/changes/archive/**`; verify the only hits are this change's artifacts and the amended spec text, and that unrelated owner work is unchanged.

## 3. Review and closure

- [x] 3.1 Same-Architect REVIEW of the stable diff, delta spec, budget and touched checks; obtain one consolidated APPROVE or a bounded repair.
- [ ] 3.2 After APPROVE, Architect DOCS_CLOSE, Main complete final gate, checkpoint, Architect CLI archive syncing the `repository-conventions` delta, and Main post-checks per `docs/agents/close-archive.md`; verify the accepted scenario names commit `e7593814afef6ce47dc46c41e20628eebe50a85c`.
