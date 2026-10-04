## 1. B1 and B2 snapshot identity and authoritative time

- [x] 1.1 Add meaningful collision/missing-disposition and microsecond regressions plus literal historical fingerprint/readback assertions; record executed behavioral RED command, assertion and expected/actual before implementation.
- [x] 1.2 Replace snapshot canonical/revision/exclusion comparison keys with component records, retain legacy fingerprint strings, normalize new scope/cutoff and versioned detection/risk/window instants; verify `mvnw.cmd -Dit.test=VersionedSnapshotStorageIT,VersionedSignalEvidenceIT verify` and optional pure finalizer test GREEN, with tests_changed_after_red disclosed.
- [x] 1.3 Obtain one fresh independent B1/B2 review verifying semantic test meaning, historical literal fingerprint compatibility and affected/doubtful CI; preserve final verdict and blocking assumptions.

## 2. B3 portable Node CI and first integration checkpoint

- [x] 2.1 Separate portable synthetic and explicit local-evidence tests without changing pinned collectors; verify `node --test tools/research/r1/test/*.test.cjs` on Node 24 without workstation evidence and explicit local suite fails when required input is missing.
- [x] 2.2 Set CI Node 24, mandatory portable test command and retained Node results while preserving existing gate; verify workflow/check consistency and portable suite failure propagates nonzero exit.
- [ ] 2.3 Main integrates reviewed B1-B3 worktrees, executes integrity preflight, `mvnw.cmd clean verify`, strict all-item OpenSpec, doctor, Node portable checks and `git diff --check`; record exact results and make scoped B1-B3 commit, no push.

## 3. B4 revision encoding and persisted exclusion equality

- [ ] 3.1 Add meaningful RED for colliding price/liquidity dimension tuples, old revision exact-key readback and exclusion retry conflict; record failing assertion before implementation.
- [ ] 3.2 Introduce explicit v2 component encoding for new price/liquidity revision writes and typed persisted exclusion comparison, preserving original non-null derivation values and full 128-character capacity without prefix/schema changes, leaving swap/USD and historical rows unchanged; after the B4 CONTRACT_CHANGED refinement establish price/liquidity 117/128-character boundary and legacy resubmission RED, add exact saved-field version recognition with an explicit same-legacy-fact conflict guard and actual component comparison, disclose superseded-prefix/silent-duplication assertion changes and tests_changed_after_red, then verify `mvnw.cmd -Dit.test=MarketFactStorageIT,VersionedSnapshotStorageIT verify` GREEN, exact provenance/equal v2 retries/null rejection, explicit v2 write results, unique v1/v2 recognition/unmatched rejection, no legacy duplicate, colliding-but-distinct tuple admission and old-version identity/readback tests.
- [ ] 3.3 Obtain fresh independent critical review; Main runs full gate on exact integrated state and scoped B4 commit, with no push or old-key rewrite.

## 4. B5 new parser and read-only historical relationship audit

- [ ] 4.1 Implement only a new versioned admission parser with regression evidence for signature/index and same-path conflicts, prevalidated page transitions, layout-derived encoded/decoded limits and once-per-page digest; verify new synthetic parser tests and exact supported-layout derivation from pinned IDL, not guessed limits.
- [ ] 4.2 Add bounded read-only historical creation audit with valid multi-path and conflicting synthetic cases; verify named tests and one offline invocation receipt with counts, input/output hashes, coverage and unchanged source bytes, no census rerun or admission.
- [ ] 4.3 Obtain independent integrity review, Main required full gate and scoped B5 commit; explicitly leave unsupported or unproven layout/coverage BLOCKED rather than PASS.

## 5. B6 prospective helper library

- [ ] 5.1 Extract separately versioned parsing/canonicalization/fake-tested bounded transport helpers for new parser/tools only; verify primitive parity, duplicate-field/integer/order checks, fake-transport bounds and unchanged historical source hashes with portable Node tests.
- [ ] 5.2 Review helper integrity and stable diff; Main required full gate and scoped B6 commit, preserving all historical imports/pins and avoiding unused infrastructure or new network runs.

## 6. B7 semantic convention checks

- [ ] 6.1 Replace only brittle exact process wording assertions with equivalent-positive and missing/contradictory-protection-negative checks; retain config/link/independent-review guards and verify meaningful behavioral regression evidence plus `mvnw.cmd -Dtest=RepositoryConventionsTest test` GREEN.
- [ ] 6.2 Review retained process protection and full gate on exact integrated state; make scoped B7 commit with no product-test weakening or unrelated workflow edits.

## 7. Closure or deadline handoff

- [ ] 7.1 After applicable APPROVE, Architect records verified tasks, source/evidence and unresolved items without semantic changes; verify factual results and links against saved outputs.
- [ ] 7.2 If all implementation requirements are verified within owner window, Main full final gate/checkpoint then authorized CLI archive and post-checks; verify strict OpenSpec, doctor, convention and relevant Node tests plus diff checks, preserving path-scoped recovery.
- [ ] 7.3 At 2026-10-05T09:42:41+05:00 stop implementation regardless of partial progress and provide factual commits/checks/open B backlog for Main's single A+B owner report; verify no unfinished task is marked complete or archive claimed.

## Verified B1-B3 checkpoint status — 2026-10-04

DOCS_CLOSE after B1/B2 fresh independent APPROVE and B3 ordinary review APPROVE by Main, who did not author B3. Only tasks 1.1-1.3 and 2.1-2.2 are checked. Task 2.3 has reviewed integration and a passed gate, but remains open until the scoped commit actually exists. B4 is under the recorded contract-refinement repair; B5 repair and B6/B7 completion are not claimed. This change is active, not archived.

- B1/B2 evidence: [Builder handoff](C:/crypto-research-evidence/r1-e2-path-v1-gate-20261003/builder-window-b12-handoff.txt) and [fresh independent APPROVE](C:/crypto-research-evidence/r1-e2-path-v1-gate-20261003/reviewer-window-b12-final.md). Executed RED: nine expected behavioral failures, zero errors/skips; targeted GREEN: 27 integration plus 92 unit tests, zero failures/errors/skips. Pre-final-RED test corrections and `tests_changed_after_red=true` were disclosed; no establishing tests changed after the final RED. The literal historical fingerprint and exact saved-manifest readback passed. Persisted exclusion retry repair belongs to B4 and is not covered by this approval.
- B3 evidence: [Main's ordinary review](C:/crypto-research-evidence/r1-e2-path-v1-gate-20261003/main-window-b3-review.md). Node 24 portable checks executed against a clean Git-LF checkout without workstation evidence: 229 passed, zero failures/skips. Four explicit local missing-input tests failed as expected with zero skips; this is fail-closed validation, not regression RED. A failure fixture returned and retained exit 23. The actual historical local suite and hosted GitHub artifact action were not run; neither is claimed PASS. Known Windows CRLF versus immutable LF pin differences remain recorded, with historical source hashes unchanged.
- Exact integration: [B1/B2 blob record](C:/crypto-research-evidence/r1-e2-path-v1-gate-20261003/main-window-b12-integration-blobs.json) and [B3 blob record](C:/crypto-research-evidence/r1-e2-path-v1-gate-20261003/main-window-b3-integration-blobs.json) identify all eleven author paths. Main verified the reviewed, root-integrated and gated Git blobs match; unrelated Stream A files were excluded from the isolated integration gate.
- Main gate: [captured exits](C:/crypto-research-evidence/r1-e2-path-v1-gate-20261003/main-window-b123-gate-results.json), run in `audit-integration-20261004` from 22:48:45 to 22:50:49 +05:00. Docker, integrity preflight, full Maven, portable Node, strict all-item OpenSpec, doctor, working-tree diff and cached diff checks all exited 0. [Maven output](C:/crypto-research-evidence/r1-e2-path-v1-gate-20261003/main-window-b123-maven.log): 92 unit plus 87 integration tests, zero failures/errors/skips. [Node output](C:/crypto-research-evidence/r1-e2-path-v1-gate-20261003/main-window-b123-node.log): 229 passed, zero failures/cancelled/skips/todo. Later modified checked inputs require their affected checks again.

Следующее разрешённое действие: Main rechecks affected OpenSpec/doctor/diff inputs after this status-only closure, creates the scoped B1-B3 plus approved B-plan commit without unrelated Stream A work or push, and continues the separately assigned B4/B5 work within the unchanged owner deadline.
