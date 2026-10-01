## 1. Offline inventory slice — current implementation scope

- [x] 1.1 Builder establishes behavioral RED for the inventory requirements with synthetic fixtures, an importable minimal module scaffold and `node --test tools/research/r1/test/inventory.test.cjs`; records each executed assertion, expected/actual and requirement before semantic freeze. Missing-module or CLI-discovery failures do not count as RED.
- [x] 1.2 Builder implements closed schema validation, complete required-field blockers, exact micro-USD ceiling comparison and versioned canonical fingerprint under the six-file/800-line design budget; verifies all requirement-derived tests with the same targeted command and reports `tests_changed_after_red`.
- [x] 1.3 Builder implements the bounded read-only CLI and candidate inventory using dated official documentary references, zero selected sources and explicit unresolved status; verifies CLI exits, input/report caps, safe diagnostics, no calls/writes and `runAuthorized = false`/`d1Passed = false` in targeted tests.
- [x] 1.4 Architect in DOCS delivers `docs/research/R1_D1_FIELD_INVENTORY.md` and `docs/research/R1_D1_RUNBOOK.md` describing the candidate facts, limitations and next decision with links to the input/schema; checks every claim against the dated primary references and verifies relative links without running source data work.
- [x] 1.5 Fresh Reviewer checks the stable inventory-slice diff, RED/GREEN assertion meaning, candidate-source truth, change budget and full CI-01..CI-15 matrix; verifies consolidated APPROVE before any continued source work.
- [x] 1.6 Main runs `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `docker version`, `mvnw.cmd clean verify`, `node --test tools/research/r1/test/inventory.test.cjs`, `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`; records exits/counts. Inventory-slice completion retains the active D1 change and incomplete section 2; it is not D1 passage or archive authorization.

## 2. Source admission, extraction and measured D1 gate — blocked pending new PLAN

The following tasks are stage obligations, not instructions to the current Builder. Architect must return a new `PLAN_READY` with `reason = CONTRACT_CHANGED` and strictly valid updated implementation scenarios before dependent work. Missing commercial terms, source versions and output location are real blockers to this section; they do not block local tooling/tests in section 1.

- [ ] 2.1 Architect resolves inventory blockers and confirmed source/access/retention/cost evidence, exact source/query/export versions, owner-selected external output path and per-run/cumulative request/byte/time/storage/retry ceilings within OD-2; verifies the updated plan with strict OpenSpec validation and explicit source/run readiness in the handoff.
- [ ] 2.2 Risk-routed Builder establishes new behavioral RED and implements only the source-specific scripts/decoders/gate calculations allocated by that new plan; verifies targeted GREEN with exact commands from that plan, preserving section 1 frozen tests.
- [ ] 2.3 Fresh Reviewer approves the field inventory and reviewed run scripts before extraction; Main passes and retains the complete pre-run gate. Verify all offline procedure section 1 preconditions in the runbook before the first run, with no implicit approval from inventory classification.
- [ ] 2.4 Perform only the reviewed bounded D1 runs with receipts and SHA-256 export manifests, finite cumulative accounting, 80 percent checkpoints and 100 percent stops; verify exact commands, UTC ranges, source versions, budgets, secret scan and no outcome-bearing quantities in receipts.
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
