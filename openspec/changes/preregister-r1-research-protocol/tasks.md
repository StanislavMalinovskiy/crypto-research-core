## 1. Draft protocol and procedure

- [ ] 1.1 Author `docs/research/R1_RESEARCH_PROTOCOL.md` (`0.7.0-draft`) covering every `research-protocol` requirement, with sourced values for `OD-1`..`OD-8`, and verify by a requirement-to-section trace in the fresh review
- [ ] 1.2 Author the blocking procedure `docs/research/R1_OFFLINE_RESEARCH_PROCEDURE.md` (one change per stage with bounded runs, script location, checks, budget, OpenSpec boundary, receipts, holdout technical rerun, secrets, review) and verify the protocol references it
- [ ] 1.3 Add the R1 pointer line to `docs/notes/ARCHITECTURE_AND_RESEARCH_REPLAN_2026-09-30.md` and verify `git diff --check` and `mvnw.cmd -Dtest=RepositoryConventionsTest test` pass
- [ ] 1.4 Verify `openspec validate --all --strict --no-interactive` passes with this change and the A1+A2 change active

## 2. Owner approval and review (in this order)

- [ ] 2.1 Record the owner's 2026-10-01 one-time documentation exception from behavioral RED, limited to this protocol change, with CORE_RISK, fresh review and the complete gate retained; verify it appears in protocol section 1.2 and design.md and that the offline procedure requires behavioral tests for all later calculations
- [ ] 2.2 Owner approves or adjusts every `PROPOSED` value and the offline procedure (owner approved all `OD-1`..`OD-8` values of `0.4.0-draft` and the procedure `0.2.0-draft` without adjustment on 2026-10-01, recorded in protocol sections 1.2 and 14 and procedure `0.2.1-draft`; the owner approved the review repair round 1 clarifications (protocol section 14.1, with the conditional public-label rule) and procedure `0.3.0-draft` content on 2026-10-01; the holdout attestation for 2026-08-31 to 2026-09-28 was owner-attested on 2026-10-01 and is recorded in protocol sections 1.2 and 4.1; a new attestation is needed only if the dates change); Architect records the approved values with dates through a new PLAN (`reason = CONTRACT_CHANGED`) and verify no value remains `PROPOSED`
- [ ] 2.3 Final fresh independent Reviewer review of the protocol and procedure with the full CI-01..CI-15 matrix returns APPROVE; verify the review reference

## 3. Checks and freeze

- [ ] 3.1 Main runs the complete gate: `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`; verify all pass
- [ ] 3.2 Set protocol version `1.0.0`, status `FROZEN`, and create `docs/research/R1_PROTOCOL_FREEZE.md` with UTC date, commit, SHA-256 of the protocol bytes, approved decisions, holdout attestation and review reference; verify the SHA-256 by recomputing it from that commit
- [ ] 3.3 Fresh Reviewer confirms the freeze record matches the reviewed text and returns APPROVE

## 4. Closure

- [ ] 4.1 DOCS_CLOSE and CLI archive under the closure procedure, after Main's gate on the frozen state; verify `openspec/specs/research-protocol/spec.md` exists after archive. Data work may start only after freeze, under its own change.
