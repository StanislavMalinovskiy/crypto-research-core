## 1. Documentation correction

- [x] 1.1 Finalize the local-skill delta; compare all five scenarios against the accepted requirement and confirm only archive mode scope and planning ownership/stop wording differ.
- [x] 1.2 Remove the final empty line only from `openspec/changes/archive/2026-09-27-instruction-diet-phase-1/specs/repository-conventions/spec.md`; verify its diff has no semantic changes and `git diff --check` passes.

## 2. Verification and review

- [x] 2.1 Run `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `mvnw.cmd -Dtest=RepositoryConventionsTest test`, and `openspec validate fix-default-skill-scope --strict --no-interactive`; obtain fresh Reviewer approval of the bounded documentation diff.

After APPROVE, Architect records verified checkboxes in DOCS_CLOSE. Main owns the complete gate (`pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `docker version`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, `git diff --check`), checkpoint and post-checks. Only the authorized CLI archive may synchronize the accepted spec; follow the existing closure procedure when that phase is assigned.
