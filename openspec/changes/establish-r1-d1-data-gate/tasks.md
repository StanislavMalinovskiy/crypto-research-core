## 1. Offline inventory slice — current implementation scope

- [x] 1.1 Builder establishes behavioral RED for the inventory requirements with synthetic fixtures, an importable minimal module scaffold and `node --test tools/research/r1/test/inventory.test.cjs`; records each executed assertion, expected/actual and requirement before semantic freeze. Missing-module or CLI-discovery failures do not count as RED.
- [x] 1.2 Builder implements closed schema validation, complete required-field blockers, exact micro-USD ceiling comparison and versioned canonical fingerprint under the six-file/800-line design budget; verifies all requirement-derived tests with the same targeted command and reports `tests_changed_after_red`.
- [x] 1.3 Builder implements the bounded read-only CLI and candidate inventory using dated official documentary references, zero selected sources and explicit unresolved status; verifies CLI exits, input/report caps, safe diagnostics, no calls/writes and `runAuthorized = false`/`d1Passed = false` in targeted tests.
- [x] 1.4 Architect in DOCS delivers `docs/research/R1_D1_FIELD_INVENTORY.md` and `docs/research/R1_D1_RUNBOOK.md` describing the candidate facts, limitations and next decision with links to the input/schema; checks every claim against the dated primary references and verifies relative links without running source data work.
- [x] 1.5 Fresh Reviewer checks the stable inventory-slice diff, RED/GREEN assertion meaning, candidate-source truth, change budget and full CI-01..CI-15 matrix; verifies consolidated APPROVE before any continued source work.
- [x] 1.6 Main runs `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `docker version`, `mvnw.cmd clean verify`, `node --test tools/research/r1/test/inventory.test.cjs`, `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`; records exits/counts. Inventory-slice completion retains the active D1 change and incomplete section 2; it is not D1 passage or archive authorization.

## 1a. Offline candidate correction — next bounded implementation scope

- [x] 1.7 Builder establishes assertion-based RED in new `tools/research/r1/test/inventory-candidates.test.cjs` against the current candidate JSON for Alchemy/public RPC presence, historical-state field linkage and truthful retained sample evidence; records expected/actual and freezes the new tests. Preserve the original 41-test file unchanged.
- [x] 1.8 Builder updates only `tools/research/r1/inventory-candidates.json` under design section 4d: four unselected candidates, retained measured-scope claims and dated primary terms; leaves costs/versions/full-envelope coverage unknown, retention unverified and all fields below CONFIRMED. Runs both exact Node commands and CLI, records exits/counts/new fingerprint and `tests_changed_after_red` for the new frozen tests.
- [x] 1.9 Architect in DOCS corrects only the two R1 D1 research documents for the new candidate input and known owner path; fresh Reviewer approves the stable correction with full CI-01..CI-15 matrix and RED/GREEN evidence. No provider execution, extraction or full D1 approval is included.
- [x] 1.10 Main runs the complete project gate plus both Node commands and candidate CLI; after APPROVE Architect checks only verified correction tasks in DOCS_CLOSE. Keep section 2 unchecked and the change active; full-stage archive remains forbidden.

## 2. Source admission, extraction and measured D1 gate — blocked pending new PLAN

The following tasks are stage obligations, not instructions to the current Builder. Architect must return a new `PLAN_READY` with `reason = CONTRACT_CHANGED` and strictly valid updated implementation scenarios before dependent work. The owner has selected `C:\crypto-research-evidence\r1-d1`; access is evidenced for bounded prior Alchemy/SQD/public RPC samples. Alchemy PAYG D1 spending is explicitly not authorized by the owner's 2026-10-01 decision. Applicable export-retention terms, source/query versions and defensible cost bounds remain unresolved. Only verified zero-paid sample options may be planned until a future explicit spending decision. This PLAN correction is not a run authorization.

- [ ] 2.1 Architect admits retained Alchemy B/SQD/public RPC evidence with its limited scope, resolves inventory blockers and applicable retention/cost evidence, preserves the owner's PAYG denial, records exact source/query/export versions and per-run/cumulative request/byte/time/storage/retry ceilings within OD-2 at the owner-selected external path; verifies the updated plan with strict OpenSpec validation and explicit source/run readiness. Allocate only verified zero-paid options unless a future explicit owner decision permits spending. If only a bounded admission sample is allocated, full-envelope extraction remains a separate later PLAN and D1 remains `INCONCLUSIVE`.
- [ ] 2.2 Risk-routed Builder establishes new behavioral RED and implements only the source-specific scripts/decoders/gate calculations allocated by that new plan; verifies targeted GREEN with exact commands from that plan, preserving section 1 frozen tests.
- [ ] 2.3 Fresh Reviewer approves the field inventory and reviewed run scripts before extraction; Main passes and retains the complete pre-run gate. Verify all offline procedure section 1 preconditions in the runbook before the first run, with no implicit approval from inventory classification.
- [ ] 2.4 Perform only the reviewed bounded D1 runs with receipts and SHA-256 export manifests, finite cumulative accounting, 80 percent checkpoints and 100 percent stops; verify exact commands, UTC ranges, source versions, budgets, secret scan and no outcome-bearing quantities in receipts. Source-admission samples retain their sample status and do not complete the full-envelope run obligation.
- [ ] 2.5 Apply frozen R1 8.2/8.3 gates over the complete envelope without changed thresholds and record measured evidence or explicit `INCONCLUSIVE/DATA_INSUFFICIENT` or `BUDGET_STOP`; verify the 200-trade stratified check, depth/reconstruction/input/lookup coverage, venue gaps, provenance and identical rerun hashes using commands allocated by the updated plan.
- [ ] 2.6 Fresh Reviewer reviews full D1 evidence and implementation; after APPROVE, Architect performs authorized DOCS_CLOSE with only verified task checks, Main completes its final gate/checkpoint, Architect archives through CLI and Main runs required post-checks. Verify all closure evidence under the phase-specific workflow; calibration `1.1.0` and P1 remain later work.

## Verified inventory-slice evidence — 2026-10-01

Recorded from Builder's final handoff, fresh Reviewer's APPROVE and Main's gate handoff. This completion status covers section 1 only; section 2 remains unimplemented and unchecked. No D1 source data run, measured gate pass or archive is claimed.

| Verification | Command / evidence | Result |
|---|---|---|
| Behavioral RED | `node --test tools/research/r1/test/inventory.test.cjs` | Exit 1; 41 tests executed, 16 behavioral assertion failures. |
| Targeted GREEN | Same exact Node command | Exit 0; 41/41 tests passed. `tests_changed_after_red = false`; frozen test remained unchanged. |
| Candidate validation | `node tools/research/r1/inventory-cli.cjs --inventory tools/research/r1/inventory-candidates.json` | Exit 2; `INVENTORY_BLOCKED`, 67 blockers, zero selected sources, `runAuthorized = false`, `d1Passed = false`. Selected cost `"0"` is not a D1 estimate. |
| Candidate identity | Inventory fingerprint | `sha256:dea0c1610d91856990ccd0c7d45eb1943efb275410c8f647ce0df5353f1851f1` |
| Task documentation | [Field inventory](../../../docs/research/R1_D1_FIELD_INVENTORY.md), [runbook](../../../docs/research/R1_D1_RUNBOOK.md) | Dated official claims checked 2026-10-01; 18 relative targets verified, exit 0. |
| Independent review | Fresh Reviewer, complete CI-01..CI-15 evidence/applicability matrix | APPROVE of the inventory slice. |
| Test-integrity preflight | `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1` | Exit 0. |
| Windows Docker preflight | `docker version` | Exit 0; Docker 29.8 reported. |
| Complete Maven verification | `mvnw.cmd clean verify` | Exit 0; 96 unit + 76 IT, zero failures/errors/skips. |
| All-item OpenSpec validation | `openspec validate --all --strict --no-interactive` | Exit 0; 15/15 items passed. |
| OpenSpec doctor | `openspec doctor` | Exit 0. |
| Diff whitespace | `git diff --check` | Exit 0. |

Main's recorded gate precedes this non-semantic DOCS_CLOSE bookkeeping. Main retains ownership of the complete final gate after DOCS_CLOSE. Keep this D1 change active; only a new source-admission PLAN can allocate section 2 work, and full-stage closure/archive remains conditional on its evidence.

## Verified candidate-correction evidence — 2026-10-01

Recorded from Builder's final handoff, fresh Reviewer's APPROVE and Main's complete gate after the preceding DOCS_CLOSE; covers tasks 1.7–1.10. Section 2 remains unchecked. No provider run, full D1 pass or archive is claimed.

| Verification | Command / evidence | Result |
|---|---|---|
| New behavioral RED | `node --test tools/research/r1/test/inventory-candidates.test.cjs` | Exit 1; 8 tests executed, 6 behavioral assertion failures. |
| New targeted GREEN | Same exact candidate-test command | Exit 0; 8/8 passed, `tests_changed_after_red = false`. |
| Original frozen suite | `node --test tools/research/r1/test/inventory.test.cjs` | Exit 0; 41/41 passed; original test unchanged. |
| Corrected candidate CLI | `node tools/research/r1/inventory-cli.cjs --inventory tools/research/r1/inventory-candidates.json` | Exit 2; `INVENTORY_BLOCKED`, 67 blockers, four unselected sources, both authorization/pass flags false; selected cost `"0"` is not a D1 estimate. |
| Corrected candidate identity | Inventory fingerprint | `sha256:f9f2fb5450ce37e923892979e5b6a180e7e82fcbfe25ef44c7362fb2c972888f`; replaces the historical input identity for the corrected file only. |
| Terms / documentation review | Two research docs, dated official references, retained sample scope and owner PAYG denial | Fresh Reviewer APPROVE, full CI-01..CI-15 pass, `red_suspect = false`, zero repair rounds; 26 planning/documentation relative targets checked, exit 0. |
| Main integrity / Docker preflight | `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`; `docker version` | Both exit 0. |
| Main complete Maven verification | `mvnw.cmd clean verify` | Exit 0; 96 unit + 76 IT, zero failures/errors/skips. |
| Main Node verification | `node --test tools/research/r1/test/inventory-candidates.test.cjs`; `node --test tools/research/r1/test/inventory.test.cjs` | Both exit 0; 8/8 candidate and 41/41 original tests passed. |
| Main candidate integration | `node tools/research/r1/inventory-cli.cjs --inventory tools/research/r1/inventory-candidates.json` | Child exit 2; `INVENTORY_BLOCKED`, 67 blockers, corrected fingerprint above, `runAuthorized = false`, `d1Passed = false`. |
| Main all-item OpenSpec / doctor / diff | `openspec validate --all --strict --no-interactive`; `openspec doctor`; `git diff --check` | All exit 0; strict validation 15/15. |
