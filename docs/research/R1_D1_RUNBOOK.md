# R1 D1 inventory runbook

## Scope

This runbook covers local validation of the [candidate inventory](../../tools/research/r1/inventory-candidates.json), including section 1a's verified correction in the active [D1 tasks](../../openspec/changes/establish-r1-d1-data-gate/tasks.md), and the separately owner-authorized exploratory command below. The [field inventory](R1_D1_FIELD_INVENTORY.md) retains source evidence/limitations. Inventory validation is not a source run; only the explicit exploratory section allocates a provider command after its tests and review.

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

The original frozen synthetic-fixture suite and candidate-evidence suite are implemented; run them separately:

```powershell
node --test tools/research/r1/test/inventory.test.cjs
node --test tools/research/r1/test/inventory-candidates.test.cjs
```

Builder's candidate correction recorded assertion-based RED (8 executed, 6 behavioral failures), GREEN 8/8 plus the original 41/41, and `tests_changed_after_red = false`. Sections 1/1a received fresh APPROVE and Main's complete gate; evidence remains in active tasks. Confirmatory source planning is incomplete; only the separate exploratory section below now allocates new implementation/tests and a reviewed sample. Main owns later confirmatory complete gates: integrity/Docker preflight, `mvnw.cmd clean verify`, both Node commands, strict all-item validation, doctor and diff check.

## Existing source evidence and owner decisions

The [provider research note](../notes/SOLANA_PROVIDER_SPIKE_RESEARCH_2026-09-27.md) and active design section 4a retain Alchemy B's 12/12 historical reads and six repeat matches for one pool/two vaults/two slots, plus sampled SQD/public RPC reconciliation. B stayed `INCONCLUSIVE`, its pool layout incomplete and formal S3 unfulfilled. These facts support candidates without establishing full-envelope CLMM/depth, mint transitions, exact SOL/USD or visibility coverage. Dune is not connected. Helius key-name presence is not tested entitlement.

Official Alchemy/SQD/Helius retention publications were inspected on 2026-10-01 and are linked with their dates in the field inventory. They did not establish the applicable local chain-data export-retention permission; keep it unverified. The owner selected `C:\crypto-research-evidence\r1-d1` and explicitly declined Alchemy PAYG D1 spending on 2026-10-01: "Не разрешаю пока". New PAYG calls and paid-run PLAN_READY remain prohibited until a future explicit owner decision. Historical paid access and exhausted A/B permission do not authorize new calls. These local validation commands create no extractor or provider probe.

## Before the first confirmatory D1 source run

Section 2 remains blocked pending a new Architect PLAN with `reason = CONTRACT_CHANGED`. Complete these prerequisites in updated active artifacts before dependent tooling or any schema probe/source run:

1. Resolve exact selected source/query/export versions, supported retention, zero-paid access and complete filtered candidate-wallet/token-pool population/query manifests. Record exact tips/SOL-USD/observed visibility sources or `UNAVAILABLE`; preserve PAYG denial and all frozen field/gate requirements. Retained historical Alchemy facts do not authorize new account reads.
2. Record the already owner-selected `C:\crypto-research-evidence\r1-d1`, exact lossless formats, runtime/libraries and reviewed scripts. Its parent exists; its new directory was not created during PLAN. Recheck free space before the run; the inspection recorded `124262658048` bytes. Do not reuse an earlier spike's permission.
3. Declare finite cash/request/received-byte/wall-clock/storage ceilings and per-request timeouts/retry counts, with cumulative accounting across all runs. OD-2 caps remain USD 100, 14 days, 10 GB evidence and 30 GB free space; checkpoint at 80 percent of any ceiling and stop at 100 percent.
4. Obtain updated strict-valid `PLAN_READY`, establish behavioral RED for source-specific calculations, and obtain the fresh Reviewer's APPROVE of the inventory and run scripts plus Main's complete pre-run gate. Recheck all first-run preconditions in offline procedure section 1.
5. Allocate exact bounded run commands and receipt/manifest checks in that PLAN. No extractor or probe command is currently allocated by this runbook.

### Proposed sample and the full-stage boundary

The owner limits the future R1 8.1 sample solely to volume/cost feasibility for the complete six-month filtered candidate-wallet and token-pool workload, including discovery, all required related histories and the extraction tail. Design 4c proposes at most eighteen fixed month/pricing-form windows and eighteen transaction receipts for size accounting: 36 total provider attempts including retries (zero new Alchemy, 18 public RPC, 18 SQD), 30-second timeout, 64 MiB received, 100 MiB evidence, two hours and USD 0. Exact population/query/window versions, terms and verified zero billable bounds remain unresolved; no executable command or new run permission is provided.

A sample estimates only filtered records/requests/bytes/storage/time/cash, with transparent expansion and missing components; it does not establish reconstruction, depth coverage, 200-trade agreement or D1 passage. Cost all candidate wallets and their relevant token pools under frozen R1 rules, not post-D1 ranked winners or all blocks. Do not omit pool activity between wallet trades, transfers/clustering/mint/price evidence, independent checks, overlapping selector charges, discovery or the tail. Unknown finite bounds prevent extraction. If any complete bound exceeds OD-2, stop before spending/full extraction and return the measured scope/estimate/excess with a concrete budget or narrower-experiment owner decision. PAYG denial applies independently. If only a sample is completed, D1 remains `INCONCLUSIVE`.

### Later reserve reconstruction and field admission

The later reviewed reconstruction plan uses exact pool-vault account balances immediately BEFORE/AFTER each transaction, mapped by full signature, complete account keys, vault/mint and versioned pool identity. Trader deltas, aggregate multi-leg deltas and slot-level parent/child archive reads are not interchangeable with those states; post-state cannot price an earlier cutoff. Required intervening swaps/liquidity changes remain in filtered pool history. Vault totals alone cannot reconstruct CLMM ticks/DLMM bins; count their missing depth by venue/month/reason against all candidate-pool triggers, applying the unchanged >=90 percent rule in the later full gate, never in this feasibility sample.

For next-run admission tips, exact SOL/USD and observed historical visibility are `UNAVAILABLE` until exact supported sources are evidenced, as detailed in the field inventory; the current JSON's `UNVERIFIED` statuses/fingerprint are unchanged. Do not substitute zero tips, display floating-point USD, after-cutoff prices or fabricated latency. Frozen C-3 default 60 seconds remains versioned `MODELED` and does not supply measured visibility. No threshold at `1.0.0` changes.

Later receipts belong under `docs/research/r1-receipts/`; raw/intermediate/analytic data stay outside git. Receipts record the exact command, UTC start/end/ranges, git state, source/query versions, protocol/freeze entry, requests, bytes, actual cost, row counts, errors/gaps, operator, local path and per-file SHA-256 manifest. Interrupted attempts and reruns are both recorded. Each reviewed run needs budget pre/post accounting, deterministic rerun checks and a secret scan of receipts/logs under offline procedure sections 4–6.

Only measured evidence can establish the unchanged R1 sections 8.2/8.3 gate over the complete envelope. Unfinished evidence on budget exhaustion is `INCONCLUSIVE`, not evidence of no edge. Outcome-bearing quantities remain blocked until measured D1 passage and calibration `1.1.0`; holdout additionally follows its freeze/rerun rules. After sections 1 and 1a, keep the D1 change active and section 2 unchecked. Final closure requires full-stage evidence, independent APPROVE and authorized workflow closure/archive phases.

## Exploratory public SQD run — owner-authorized 2026-10-03

The [procedure section 11](R1_OFFLINE_RESEARCH_PROCEDURE.md#11-exploratory-probe--owner-authorized-2026-10-03) explicitly permits this separate sample without satisfying the confirmatory prerequisites above. Design 4f fixes every selector/version/limit. Node `v24.19.0` is the observed and planned runtime; no installation/dependency/key is required. Before execution Main verifies behavioral RED/GREEN, both inventory suites, integrity preflight, strict all-item OpenSpec validation, doctor, diff check and ONE fresh independent Reviewer APPROVE of the combined diff/full CI matrix. The owner-approved exception omits Maven/Docker only for this exploratory work.

After those preconditions pass, run once from the repository root:

```powershell
node tools/research/r1/exploratory-probe-cli.cjs --enable-public --output C:\crypto-research-evidence\r1-d1\exploratory-sqd-v1
node tools/research/r1/exploratory-probe-cli.cjs --replay C:\crypto-research-evidence\r1-d1\exploratory-sqd-v1\manifest.json
```

Tools are implemented and reviewed; Main executed the public command once on 2026-10-03 and then offline replay, as documented in the [actual receipt](r1-receipts/2026-10-03-exploratory-sqd-v1.md). Status is `EXPLORATORY/INCOMPLETE`, USD 0; our per-response cap stopped August 28 PumpSwap and left the final Raydium stratum unexecuted. Do not rerun: default is disabled and the existing output directory is rejected without overwrite/resume. The runtime/query are fixed in design 4f: six preholdout single-slot anchors and three program filters, no wallet discovery, prior raw samples, outcome calculations or holdout data. The entire `[2026-08-31T00:00:00Z, 2026-09-29T00:00:00Z)` is forbidden even for raw fetches/counts; unexpected timestamps/slots stop a sample without retaining or counting the body.

Record actual status, per-stratum counts/field presence, complete/unexecuted ranges, UTC/git/script/config/query provenance, per-file SHA-256 and deterministic replay hash; receipt/log secret scan and cumulative accounting must pass before publishing a small receipt in an assigned DOCS phase. Hard ceilings are USD 0, 30 minutes, 80 requests, 25,000,000 received bytes, 50,000,000 disk bytes, 2,000,000/response, 30 seconds/request, two seconds between starts, concurrency 1, retries 0, 30 GB remaining free; checkpoint at 80 percent and stop at 100 percent. No paid Alchemy, keys, redirect/fallback, automatic rerun or threshold change.

Every result is `EXPLORATORY`, `d1Evidence = false`, `d1Passed = false`. Public free access and a sample's presence do not establish full-D1 retention/capacity/coverage. Report full-D1 upper bound as unknown, with transparent same-workload sensitivities and missing candidate-wallet/token/pool population, discovery, ancillary state/checks/tail and September costs explicit. The unchanged inventory and section 2 stay blocked; this run never closes or archives D1.
