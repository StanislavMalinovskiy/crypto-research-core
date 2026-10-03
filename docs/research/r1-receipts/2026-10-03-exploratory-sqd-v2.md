# Exploratory SQD v2 terminal receipt — 2026-10-03

`EXPLORATORY / INCOMPLETE`; `d1Evidence = false`; `d1Passed = false`. This receipt records the single owner-authorized design4g attempt under [procedure section 11](../R1_OFFLINE_RESEARCH_PROCEDURE.md#11-exploratory-probe--owner-authorized-2026-10-03). It supplies no confirmatory D1 passage, field promotion or archive authorization. The protocol, freeze entry1, thresholds, calibration, holdout exclusion and budgets remain unchanged. [V1 evidence](2026-10-03-exploratory-sqd-v1.md) is preserved.

## Commands and provenance

Main ran the public command once after targeted gates and ONE fresh independent Reviewer APPROVE, then replayed offline:

```powershell
node tools/research/r1/exploratory-probe-v2-cli.cjs --enable-public --output C:\crypto-research-evidence\r1-d1\exploratory-sqd-v2
node tools/research/r1/exploratory-probe-v2-cli.cjs --replay C:\crypto-research-evidence\r1-d1\exploratory-sqd-v2\manifest.json
```

Both exited2 with `INCOMPLETE`. Replay returned `code = null` and matching deterministic summary/manifest hashes: the exit records incompleteness, not integrity failure. No further provider run, retry, overwrite or resume occurred. The single-attempt authorization is exhausted.

| Provenance/accounting | Observed value |
|---|---|
| UTC start / end | `2026-10-03T08:09:33.785Z` / `2026-10-03T08:11:52.735Z` |
| Elapsed | 138,949ms; UTC endpoints span138,950ms |
| Runtime / source tree | Node `v24.19.0`; HEAD `4b20d83da833f1b0045a8b90c53daa2c533f49d3`; dirty tree recorded |
| Source / query / summary | `sqd-public-solana-mainnet` / `exploratory-sqd-v2` / `exploratory-counts-v2` |
| Identity / projection | `exploratory-config-c14n-v2` / `exploratory-workload-sensitivity-v1` |
| Protocol / freeze | `1.0.0` / entry1 |
| Requests / responses | 70 attempts: 66 HTTP200, four HTTP529 |
| Received / retained disk | 317,682bytes / 371,714bytes |
| Actual cost / retries / concurrency | USD0; `cashMicrousd = "0"`; retries0; concurrency1 |
| Limits reached / checkpoints | None / none |

Configured ceilings: USD0; 120minutes; 1,000attempts; 1,000,000,000 received bytes; 1,100,000,000 disk bytes; 16,000,000bytes/response; 45seconds/request; two-second start spacing; 30,000,000,000 remaining free bytes. No checkpoint or cap caused this terminal result.

The immutable external root contains the exact queries, configuration, admitted raw-file hashes, script hashes and provenance in `manifest.json`; no chain payload is copied into git. Endpoint: `https://portal.sqd.dev/datasets/solana-mainnet/`. Manifest API identity: SQD Portal API `1.0.0`, retrieved2026-10-03, OpenAPI77881bytes, SHA-256 `0af342338e732a39e4251c31d45a82dfb264ebe80d1316bec6d70852f461129a`. Dataset revision, full-D1 retention and continued free capacity remain unverified.

## Terminal coverage

Four predetermined ten-minute UTC windows begin at midnight on April1, June1, July1 and August1. Successful census pages are partial header evidence, not complete continuous profile coverage. All eight timestamp resolvers returned200; seven boundary-header requests succeeded.

| Window | Successful census pages / headers | Terminal HTTP529 request |
|---|---:|---|
| April1 | 7 / 220 | Census slots410196171–410196202 |
| June1 | 43 / 1,375 | Census slots423480283–423480314 |
| July1 | 1 / 32 | Census slots429980997–429981028 |
| August1 | 0 / 0 | Start header slot436454957; resolver succeeded |

Each failed request charged163 received bytes; all four error bodies were discarded. Their cause is unresolved. The receipt makes no historical-source-absence, overload or quota claim. No own cap was reached, and no automatic fallback/retry was authorized.

No A/B/C payload request occurred: A's continuous six-program inputs, B's continuous Jito-transfer inputs and C's sparse rich-size samples were all unmeasured. The summary's zero transaction/instruction/fee/transfer counters describe unexecuted work, not measured zero activity or zero availability. Recognized-swap shares, fee/tip inputs, mint/account proxies, rich bytes/transaction and continuous payload coverage are unknown. No returns, rankings, reconstruction or outcomes were calculated.

The entire `[2026-08-31T00:00:00Z, 2026-09-29T00:00:00Z)` remained excluded from requests, raw retention and counts. Successful headers do not establish venue coverage, historical state, SOL/USD or observed visibility.

## Sensitivity and integrity

`fullD1UpperBound = null`. Exact hypothetical target duration is `15652920`seconds. Low/central/high assumptions retain candidate fractions1/100,1/10,1; ancillary multipliers1,3,10; hypothetical tariffs0,1,10USD/decimalGB. Every projected byte/request/runtime/cash value and ceiling comparison remains null because no complete A/B window or populated C denominator was measured. Assumptions are not measured populations, tariffs or spending approval. Task1.18's numeric measurement objective remains unmet.

Unknowns include candidate-wallet/token/pool population and mapping, discovery, subscriptions, other venues, related histories, account/tick/bin state, independent checks, SOL/USD, observed visibility, base/priority fee separation, other tip mechanisms, historical Jito labels, retention/capacity and tail/regime variation. Actual USD0 is only this attempt's cash cost; Alchemy PAYG remains prohibited.

| Integrity identity | SHA-256 |
|---|---|
| Manifest file | `5e8dc296b2ce20c9a20e7b9728c202278153516d702bdbbe01762bc45cffb224` |
| Deterministic summary fingerprint | `00ecd15a25eae7d51a8aaff7caa42ad05cd4da22b4e00d8a07e2fbb5e31c0a0d` |
| Summary file | `f3d90da6115ecfa66cfda04cfb95372590f2f3d3888a02418ac238d06601552b` |
| Configuration fingerprint | `ff6f9f077793c5bae8a1d793820ff4a8a6ebb69c0dd2b371934e0b7492201912` |

Independent terminal review verified all retained hashes and exact disk accounting: 66raw files/317,030bytes plus three metadata files/54,684bytes equals371,714bytes. Offline replay reproduced the same hashes/status. Main's credential-pattern scan of69files found zero hits; this bounded scan is not a universal secret-format guarantee. The owned Node process terminated.

## Verification and task disposition

Main evidence folder: `C:\crypto-research-evidence\r1-d1-exploratory-v2-preflight-20261003`. `repair2-main-{v2-tests,v1-tests,inventory-tests,candidate-tests,integrity,strict,doctor,diff,cached-diff}.log` and corresponding `.exit` files all record exit0. Exact targeted commands were `node --test tools/research/r1/test/exploratory-probe-v2.test.cjs`, `node --test tools/research/r1/test/exploratory-probe.test.cjs`, `node --test tools/research/r1/test/inventory.test.cjs` and `node --test tools/research/r1/test/inventory-candidates.test.cjs`: 12+19+41+8=80tests, zero failures/skips. Integrity command: `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`. Strict validation: `openspec validate --all --strict --no-interactive`, 15/15. `openspec doctor`, `git diff --check` and `git diff --cached --check` passed. `main-public-run.log` and `main-replay.log` plus `.exit` record exit2.

Original v2 RED executed nine behavioral assertion failures before semantic freeze. Authorized repair2 added three regressions: 12executed, nine passed/three failed; immutable instruction/transaction conflicts expected `RESPONSE_INVALID`, actual `null`; projection target expected `15652920`, actual `417426879009`. New freeze followed meaningful RED, then GREEN80. `tests_changed_after_red = true` for only these authorized additions; the original77 assertions were preserved. Repair rounds1/2 remain consumed; no budget reset is implied.

ONE fresh independent Reviewer (`exploratory_v2_review`) returned terminal APPROVE with the full CI-01..CI-15 evidence/applicability matrix and `red_suspect = false`. Read-only Claude peer result `a75d0cf1` agreed with the honest incomplete disposition; it was a delegated future scope decision, not by itself a passed plan/review/gate or immediate run permission.

`docker version` and `mvnw.cmd clean verify` were NOT RUN under the exact owner-approved procedure-section11 exception for this exploratory probe only. No complete Maven/D1 gate PASS is claimed. Tasks1.15–1.17 are verified;1.18 remains unchecked despite the completed attempt, replay and receipt. Tasks2.1–2.6 remain unchecked; the D1 change stays active and cannot be archived.
