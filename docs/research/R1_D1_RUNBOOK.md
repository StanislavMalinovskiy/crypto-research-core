# R1 D1 inventory runbook

## Scope

This runbook covers local validation of the [candidate inventory](../../tools/research/r1/inventory-candidates.json), including section 1a's offline candidate correction in the active [D1 tasks](../../openspec/changes/establish-r1-d1-data-gate/tasks.md). The [field inventory](R1_D1_FIELD_INVENTORY.md) records Alchemy, SQD, public RPC and unconnected Dune evidence and limitations. This local validation is not a D1 source data run or an extraction command. No provider call is allocated here.

The [frozen R1 protocol](R1_RESEARCH_PROTOCOL.md), [freeze entry 1](R1_PROTOCOL_FREEZE.md) and owner-approved [offline procedure](R1_OFFLINE_RESEARCH_PROCEDURE.md) govern the later data work. The current implementation uses CommonJS and Node built-ins only; Node `v20.18.0` was observed on 2026-10-01. It needs no npm install, provider account or credentials. Runtime changes before data work require a planning decision under the active [design](../../openspec/changes/establish-r1-d1-data-gate/design.md).

## Validate the local inventory

From the repository root in PowerShell:

```powershell
node --version
node tools/research/r1/inventory-cli.cjs --inventory tools/research/r1/inventory-candidates.json
$LASTEXITCODE
```

The [CLI](../../tools/research/r1/inventory-cli.cjs) accepts exactly `--inventory <path>`. It reads that one file, emits one JSON report line to stdout and writes no files. It makes no provider requests and does not load credentials. The [validator](../../tools/research/r1/inventory.cjs) reports every retained blocker in deterministic order.

| Exit | Classification | Interpretation |
|---|---|---|
| `0` | `INVENTORY_COMPLETE` | Declared inventory prerequisites are structurally complete; claims still require independent review. |
| `2` | `INVENTORY_BLOCKED` | Input is valid, but declared field/source prerequisites remain unresolved. This is the expected candidate result. |
| `1` | `INVENTORY_INVALID` | Fixed error code for malformed/unsupported input, bounds, arguments or file errors; fix the input or invocation under the change. |

Every report, including exit `0`, retains `runAuthorized = false` and `d1Passed = false`. Invalid reports omit a fingerprint and never echo paths, input content or caught exceptions. The historical two-source candidate returned exit `2`, 67 blockers and fingerprint `sha256:dea0c1610d91856990ccd0c7d45eb1943efb275410c8f647ce0df5353f1851f1`. Builder verified the corrected four-source candidate: exit `2`, 67 blockers and new fingerprint `sha256:f9f2fb5450ce37e923892979e5b6a180e7e82fcbfe25ef44c7362fb2c972888f`. Recompute after any input edit; matching blocker counts do not mean matching identities. Selected cost `"0"` sums zero selected sources and supplies no estimate of D1 cost.

## Input, identity and limits

Use the closed schema in the active [delta spec](../../openspec/changes/establish-r1-d1-data-gate/specs/r1-data-inventory/spec.md) and design. Required versions are `r1-d1-inventory-v1`, `r1-d1-inventory-c14n-v1` and protocol `1.0.0`; `freezeEntry` is numeric `1`. A different supported format requires a future contract update; an unsupported canonicalization version returns `UNSUPPORTED_CANONICALIZATION_VERSION` without a fingerprint.

- Input file: at most 1,000,000 bytes, with a bounded read allowing one overflow byte for rejection.
- Collections: at most 32 sources, exactly 12 required fields, at most 16 references per evidence list, 32 source IDs and 32 gaps per field. Duplicate records and unknown properties are rejected.
- Text: IDs at most 64 characters, other text at most 256, evidence claims at most 1,000. URLs must be HTTPS without user information, query or fragment. Keep all annotations free of secrets; valid-input free text contributes to the fingerprint.
- Report: at most 2,048 blockers and 262,144 bytes; bound failures are explicit invalid results, never truncated successful reports.
- Cost: canonical unsigned integer strings in micro-USD; exact sum via `BigInt`. `100000000` is USD 100; a sum of `100000001` produces `COST_ABOVE_CEILING`. Equality provides no permission to spend.

All schema lists are unordered sets. Canonical encoding uses type tags, UTF-8 byte lengths and bytewise ordering; numeric `freezeEntry = 1` encodes as ASCII `n1:1`. Equivalent record/object-key permutations retain identity. Valid content edits, including source references and unselected candidates, change it. A document retrieval date does not supply a missing source/query version.

## Tool verification and inventory review

Run the original frozen synthetic-fixture suite and the new candidate-evidence suite separately. The second command is allocated by section 1a and becomes available when Builder adds the new test file:

```powershell
node --test tools/research/r1/test/inventory.test.cjs
node --test tools/research/r1/test/inventory-candidates.test.cjs
```

Builder records new assertion-based RED/GREEN and `tests_changed_after_red`, preserving the original 41-test file; local tests are not an R1 data run. Initial section 1 already received APPROVE and Main's gate; its evidence remains in active tasks. The correction requires its own fresh Reviewer, stable input/documentary claims, frozen new tests and full CI-01..CI-15 matrix. Main then runs the complete repository gate: test-integrity preflight, Docker preflight, `mvnw.cmd clean verify`, both Node commands, strict all-item OpenSpec validation, doctor and `git diff --check`. Correction review/gate remain pending.

## Existing source evidence and owner decisions

The [provider research note](../notes/SOLANA_PROVIDER_SPIKE_RESEARCH_2026-09-27.md) and active design section 4a retain Alchemy B's 12/12 historical reads and six repeat matches for one pool/two vaults/two slots, plus sampled SQD/public RPC reconciliation. B stayed `INCONCLUSIVE`, its pool layout incomplete and formal S3 unfulfilled. These facts support candidates without establishing full-envelope CLMM/depth, mint transitions, exact SOL/USD or visibility coverage. Dune is not connected. Helius key-name presence is not tested entitlement.

Official Alchemy/SQD/Helius retention publications were inspected on 2026-10-01 and are linked with their dates in the field inventory. They did not establish the applicable local chain-data export-retention permission; keep it unverified. The owner selected `C:\crypto-research-evidence\r1-d1` and explicitly declined Alchemy PAYG D1 spending on 2026-10-01: "Не разрешаю пока". New PAYG calls and paid-run PLAN_READY remain prohibited until a future explicit owner decision. Historical paid access and exhausted A/B permission do not authorize new calls. These local validation commands create no extractor or provider probe.

## Before the first D1 source run

Section 2 remains blocked pending a new Architect PLAN with `reason = CONTRACT_CHANGED`. Complete these prerequisites in updated active artifacts before dependent tooling or any schema probe/source run:

1. Resolve selected sources, exact source/query/export versions, independently reviewable field claims, applicable access/retention terms and defensible zero-paid cost bounds. Preserve the owner's PAYG denial; no paid run is eligible without a future explicit owner spending decision. Admit public RPC and retained historical Alchemy evidence only for their evidenced/reviewed scope; define supported venue inputs and layouts.
2. Record the already owner-selected `C:\crypto-research-evidence\r1-d1`, exact lossless formats, runtime/libraries and reviewed scripts. Its parent exists; its new directory was not created during PLAN. Recheck free space before the run; the inspection recorded `124262658048` bytes. Do not reuse an earlier spike's permission.
3. Declare finite cash/request/received-byte/wall-clock/storage ceilings and per-request timeouts/retry counts, with cumulative accounting across all runs. OD-2 caps remain USD 100, 14 days, 10 GB evidence and 30 GB free space; checkpoint at 80 percent of any ceiling and stop at 100 percent.
4. Obtain updated strict-valid `PLAN_READY`, establish behavioral RED for source-specific calculations, and obtain the fresh Reviewer's APPROVE of the inventory and run scripts plus Main's complete pre-run gate. Recheck all first-run preconditions in offline procedure section 1.
5. Allocate exact bounded run commands and receipt/manifest checks in that PLAN. No extractor or probe command is currently allocated by this runbook.

### Proposed sample and the full-stage boundary

Design section 4c now proposes a finite sample using verified zero-paid options only: at most nine cases, 18 transactions, 36 total provider attempts including retries (zero new Alchemy, 18 public RPC, 18 SQD), 30-second request timeout, 64 MiB received, 100 MiB evidence, two hours and USD 0 cash. The previous 96-account/USD 3 draft is withdrawn. Exact sources/versions/layouts, terms, zero billable worst-case reservations and reviewed implementation remain prerequisites. This is not a run command or authorization. Reuse retained account receipts read-only; do not fall back to paid reads or unbounded tick/history expansion. Retain prior spike accounting separately and preserve cumulative OD-2 accounting for future D1 attempts.

A sample can test layout decodeability and receipt agreement; it cannot satisfy continuous whole-envelope thresholds or the 200 venue/month-stratified trades. If only sampled evidence is collected, D1 remains `INCONCLUSIVE` and another PLAN is required before full extraction/gate implementation. Do not silently narrow the frozen experiment to sampled periods or treat current-state observations as historical transitions.

Later receipts belong under `docs/research/r1-receipts/`; raw/intermediate/analytic data stay outside git. Receipts record the exact command, UTC start/end/ranges, git state, source/query versions, protocol/freeze entry, requests, bytes, actual cost, row counts, errors/gaps, operator, local path and per-file SHA-256 manifest. Interrupted attempts and reruns are both recorded. Each reviewed run needs budget pre/post accounting, deterministic rerun checks and a secret scan of receipts/logs under offline procedure sections 4–6.

Only measured evidence can establish the unchanged R1 sections 8.2/8.3 gate over the complete envelope. Unfinished evidence on budget exhaustion is `INCONCLUSIVE`, not evidence of no edge. Outcome-bearing quantities remain blocked until measured D1 passage and calibration `1.1.0`; holdout additionally follows its freeze/rerun rules. After sections 1 and 1a, keep the D1 change active and section 2 unchecked. Final closure requires full-stage evidence, independent APPROVE and authorized workflow closure/archive phases.
