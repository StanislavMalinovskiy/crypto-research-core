# R1 offline research procedure

## Prospective general routing after the process reform

These general rules apply only to new work after the verified workflow reform. All earlier grants, E1/E2 amendments, frozen protocol/holdout rules, census/probe attempts, budgets, stops, receipts and evidence below remain governed by their original contracts. No retroactive reclassification, renewed source access or census continuation is authorized.

A bounded free or already approved-budget research probe without holdout access, outcome calculations or evidence admission uses a short description in this existing procedure: question, source, period, cash/request/byte/time/storage limits, recording location and stop conditions. Preserve cumulative resource accounting and produce a final receipt including gaps, errors and actual usage. UNKNOWN, incomplete coverage and negative findings are valid exploratory outcomes, never admitted evidence or D1/P1 passage.

For future probes meeting all those conditions, this prospective block's OpenSpec applicability and dependency/effect-based checks replace the historical universal stricter-rule, stage-change/first-run and all-code complete-gate prerequisites below. Those prerequisites do not require a separate stage change or a full repository gate merely for such a qualified isolated probe. This replacement applies only to that future probe scope; authoritative D1/P1 work and every earlier grant, amendment, frozen run and evidence record retain their original rules unchanged.

OpenSpec remains required for D1/P1 admission, system behavior or accepted requirements; reuse an existing change covering that exact scope. Classify changed guarantees and consequences, rather than calling every research tool critical. Architecture, material uncertainty and critical guarantees require Architect; critical and substantive research-rule changes require independent review regardless of author. Existing holdout, point-in-time, exact-arithmetic, accepted-evidence, migration and secret protections remain.

Choose checks by actual dependencies/effects. Isolated tools use useful tool tests, receipt/source/budget/secret checks and diff checks, with meaningful regression RED for executable bugfixes or critical behavior. Documentation uses facts, consistency, links and requirements without artificial RED. Full integrity/Maven/OpenSpec/doctor/diff gate is required before each Java/test/build/DB/dependency/shared-runtime-instruction commit; mixed work uses the union. Report unrun/failed/skipped checks honestly and rerun affected checks after checked-input edits. No automatic Maven follows from an isolated probe. This paragraph does not change any historical exception below.

| Field | Value |
|---|---|
| Status | `OWNER-APPROVED 2026-10-01` (`OD-4` of the [R1 protocol](R1_RESEARCH_PROTOCOL.md)); blocking until protocol freeze |
| Version | `0.3.1-draft`, 2026-10-01 (content of `0.3.0-draft`, `OWNER-APPROVED 2026-10-01`; this version records the approval only) |
| Scope | Every script, export, query or computation producing or preparing R1 (D1/P1) evidence |

This procedure is blocking: the owner approved it on 2026-10-01, but no R1 data work starts until the protocol is frozen at `1.0.0` and the stage's own OpenSpec change authorizes it. It adds no exception to the selected [agent workflow](../AGENT_WORKFLOW_MULTIAGENT.md) or the [agent guide](../../AGENTS.md); where they differ, the stricter rule applies. Any relaxation of governance needs its own explicit owner approval.

## 1. Stage changes and runs

- One OpenSpec change per stage: one for D1 (field-availability inventory, extraction and gate) and one for P1 (selection replication, validation, holdout and report). The Architect classifies each change's risk under the workflow's trigger list; no blanket exemption and no blanket tier is assumed.
- Within a stage change, approved scripts run as several bounded runs. Each run uses code already reviewed in that change and stays within the ceilings the change declares.
- Preconditions for the first run of a stage: protocol frozen with its freeze record entry; owner approval of this procedure and the `OD-2` budget; the stage change at PLAN_READY naming sources, query/export versions, ceilings and the scripts it may create.

## 2. OpenSpec applicability and tests

- Every script that extracts, transforms, reconstructs, selects, prices, simulates or computes statistics for R1 belongs to the stage change. "Experiment" or "exploration" is not an exemption.
- Every calculation has behavioral tests written first (RED), with fixtures of known expected values, including status precedence, loss-of-exit evidence, `U-CONS`, the unknown-share comparison and the bootstrap procedure. The documentation RED exception granted for the R1 protocol does not extend to any of this work.
- Outputs not produced by the stage change's reviewed code are `EXPLORATORY` and cannot be R1 evidence.

## 3. Locations

| Item | Location | Committed |
|---|---|---|
| Scripts and their tests | `tools/research/r1/` | Yes, after review |
| Run receipts and export manifests (small text) | `docs/research/r1-receipts/` | Yes |
| Raw exports, decoded data, intermediate and analytic files | Owner-chosen local directory outside the git working tree, recorded by path in each receipt | Never |
| Credentials | Environment variables or the OS credential store, outside the repository | Never |

The scripts' language, runtime and libraries are declared in the D1 change design and approved under the [Tech Stack](../TECH_STACK.md) dependency rules before first use. They are research tooling, not production dependencies, and are never imported by the application. Offline Parquet/DuckDB is used only if approved there. PostgreSQL stays authoritative for application evidence; nothing reads another module's tables.

## 4. Checks

| Event | Required checks |
|---|---|
| Any code change (scripts, tests) | Behavioral tests after verified RED; review under the workflow; Main's complete gate |
| Each run | Budget pre-check and post-accounting against the ceilings; export manifest with per-file SHA-256; reproducibility: a rerun from the same manifest reproduces identical output hashes (on a declared sample run for large exports); secret scan of the receipt and logs |
| Receipt-only commit | `git diff --check` and `mvnw.cmd -Dtest=RepositoryConventionsTest test` |
| Stage closure | Main's complete gate and the workflow's review and closure |

## 5. Budget and watchdog

- Every run declares its cash, request, byte, wall-clock and storage ceilings within `OD-2`, with finite per-request timeouts and a finite retry count.
- Spend and counts accumulate across runs; a checkpoint is taken at 80 percent of any ceiling and work stops at 100 percent with an `INCONCLUSIVE` disposition for unfinished parts.
- The field-availability inventory precedes bulk extraction; if it shows costs above the ceiling, work stops before spending for an owner decision.
- No automatic retry beyond the declared count, no unbounded pagination or polling, no paid call outside the approved sources.

## 6. Receipts

Each run's receipt records: UTC start and end, git commit and dirty state, protocol version and freeze entry, change name, exact command, source and query/export version, request count, bytes, actual cost, row counts, ranges, export manifest with SHA-256 per file, errors and gaps, the local data path and the operator. Interrupted runs and their reruns are both recorded.

## 7. Blinding and holdout

- Before the D1 gate passes and the `1.1.0` calibration is recorded, code that computes outcome-bearing quantities is not run on extracted data; D1 runs report only availability, coverage, status and cost measurements.
- Holdout evaluation requires the holdout freeze entry. A defect correction before any holdout outcome is viewed requires a behavioral test, a deviation-log entry and a new holdout-freeze entry with the new code commit. At most one technical rerun is allowed, and only if (a) inputs are identical and frozen (analysis code commit, configuration fingerprint, dataset manifest fingerprint, seeds), (b) no outcome output of the interrupted attempt was viewed, and (c) both attempts are recorded in the receipt. What is prohibited is changing rules, code, configuration or inputs after viewing results.

## 8. Secrets and access

Use read-only, least-privilege credentials scoped to the approved sources. Never print, log or commit them; redact receipts. Rotate a credential immediately if it is exposed, and record the incident in the deviation log.

## 9. Review

Each stage change follows the workflow's review routing; CORE_RISK work is reviewed by a fresh independent Reviewer and closed only after Main's complete gate.

## 10. Changelog

| Version | Date | Change |
|---|---|---|
| `0.1.0-draft` | 2026-10-01 | Initial proposed procedure |
| `0.2.0-draft` | 2026-10-01 | One change per stage with bounded runs, proportionate checks, holdout technical rerun rule |
| `0.2.1-draft` | 2026-10-01 | Owner approval of `0.2.0-draft` recorded; no semantic change |
| `0.3.0-draft` | 2026-10-01 | Review repair round 1: at most one holdout technical rerun; defect correction before any holdout outcome is viewed recorded as a new holdout-freeze entry (aligned with protocol `0.6.0-draft`) |
| `0.3.1-draft` | 2026-10-01 | Owner approval of `0.3.0-draft` content recorded; no semantic change |

## 11. Exploratory probe — owner-authorized 2026-10-03

This appended rule is effective from the owner's 2026-10-03 authorization for bounded, zero-paid exploratory availability/volume/cost work. It is outside confirmatory `R1-E1`: the preceding `0.3.1-draft` procedure blob and its freeze-record hash remain the governing frozen procedure for authoritative D1/P1. This addition neither amends that freeze nor supplies D1 calibration or evidence.

- A probe reports only source availability, field presence, coverage of its declared sample, rows/bytes/requests/storage, throughput and cost feasibility. Every output and receipt is `EXPLORATORY`, with `d1Evidence = false` and `d1Passed = false`. Returns, PnL, profitability, hit rates, wallet/token rankings, selection, strategy reconstruction and outcomes are forbidden. Probe samples cannot establish whole-envelope completeness or promote inventory fields to `CONFIRMED`.
- No request, raw fetch, count or other inspection may touch `[2026-08-31T00:00:00Z, 2026-09-29T00:00:00Z)`, conservatively excluding all of September 28 as well. Select windows before execution without outcomes or current survival. Resolve and validate timestamp/slot bounds before requesting payloads; specify finite server-side ranges. Validate every returned block's timestamp and slot before retaining payloads or counting any rows. Missing time, out-of-range or forbidden-period data stops that sample; discard the response without retaining its raw payload or publishing its counts. Never use an unbounded endpoint or a current-head query to discover historical sample ranges.
- The active stage change names the exact public source, query version, scripts, runtime, external output path and finite aggregate cash/request/received-byte/disk/wall-clock/timeouts/retries/concurrency ceilings. Alchemy PAYG remains prohibited. No subscription, authenticated/paid endpoint, key loading, redirect, paid fallback or limit circumvention is permitted. Free shared service refusal is an explicit gap, never permission to purchase or fabricate data.
- Before the first request, require meaningful behavioral RED and targeted GREEN of the probe safety/reporting contract and ONE fresh independent review of the stable combined procedure/plan/tool diff. Risk remains `CORE_RISK`; that review retains its full CI-01..CI-15 matrix. Under this exact owner-approved exception, targeted probe tests, the two existing inventory suites, test-integrity preflight, strict all-item OpenSpec validation, doctor and `git diff --check` replace the complete Maven/Docker gate for this probe only. This exception explicitly overrides the stricter-rule sentence above, sections 4 and 9, and the corresponding non-TRIVIAL full-gate prerequisite only for these exploratory scripts/runs/receipts. It changes neither repository CI nor authoritative D1/P1 code/run/closure gates; it grants no RED waiver or archive permission.
- Keep sequential requests, finite budgets, cumulative pre/post accounting and 80-percent checkpoints/100-percent stops. Retain only admitted public payloads outside git with per-file SHA-256 and exact query/configuration versions; replay the manifest offline to verify identical deterministic sample-summary hashes. Record unsuccessful/partial attempts and unresolved strata. No secrets or provider error bodies enter logs, receipts or git. Public-development access is not a formal full-D1 retention or capacity confirmation; preserve those unknowns.
- Work proceeds autonomously within the reviewed bounds. Owner decisions are required for spending, holdout access or methodological changes; none is authorized by a successful sample. Full-D1 cost sensitivities must state their assumptions and unknown candidate-wallet/token/pool populations, discovery, ancillary state/checks and tail costs. Unknowns never become zero or a proven full-D1 upper bound.

| Authorization | Effective scope |
|---|---|
| Owner, 2026-10-03: add Exploratory probe rule and continue to an actual free sample | This section only; outcome-blind, holdout-excluding, finite zero-paid probe with targeted tests and one independent review. Frozen methodology, calibration, thresholds, completeness and freeze records are unchanged. |

### Helius Free access amendment — 2026-10-03

The owner's relayed `owner-next-task-helius-plan.md` and delegated decision `e928ceb5-6768-45d9-a454-e26ef87f78ed` authorize only [design4k](../../openspec/changes/establish-r1-d1-data-gate/design.md#4k-helius-free-wallet-history-source--bounded-future-h1h6)'s bounded H1 entitlement and H3 wallet-history steps after their reviewed implementation and required gates. For these steps only, the preceding public-only/no-authentication/no-key-loading restriction permits Helius Free `getTransactionsForAddress` on the fixed mainnet host. At request time only, transport may read the named `CRYPTO_HELIUS_API_KEY` from ignored `config/application-managed-secrets.properties` with a bounded1MB read and use it in memory for authentication. No key, authenticated URL, provider error body or credential-bearing command enters logs, output, identities, receipts or git. Replay/selection/forecast never load credentials. This is also a narrowly scoped exception to section3's credential-location rule, not permission to edit configuration or disclose secrets.

The earlier exploratory Maven/Docker exception does NOT apply to Helius. CORE_RISK meaningful safety RED, semantic freeze, targeted GREEN, fresh independent Reviewer's full CI-01..CI-15 matrix and Main's complete gate remain mandatory. The requested local no-RED/full-CI waiver is unsupported by current roles and not effective. Helius has its own at most two ordinary repairs plus a current-reviewer-authorized third; replanning does not reset it or any stopped SQD/mapper counter. H1 is exactly1attempt/0retry/10reserved credits/16MBreceived/20MBdisk/45srequest/10min/USD0; H3 is at most100000credits/1000all attempts/4GBreceived/4.1GBdisk/64MBresponse/60srequest/nominal120min/USD0 with design4k's approved source/publication timing split below, finite retries, cumulative accounting and exclusive external roots. H3 waits for measured H1 Free entitlement, tariff-compatible isolated dashboard debit and H2 admission. Current Free quota/account safeguards are rechecked before calls; unknown entitlement, unexpected debit or explicit denial stops, never licenses a paid fallback.

[Helius Terms](https://www.helius.dev/terms), published2026-09-28 and checked2026-10-03, do not explicitly establish the intended persisted chain-export right; applicable account/order rights remain `UNVERIFIED`. Decision e928 permits only bounded local H1/H3 EXPLORATORY raw retention in their fixed external roots for14days after each run's UTC end, recorded in manifest/receipt, with no resale/publication, secrets, holdout or paid use. This is a delegated research-scope permission, not legal confirmation or confirmatory OD-2 admission. No automatic renewal: separately scoped operator cleanup removes only that run's expired raw files, retains small sanitized hashes/status receipts and records replay unavailable after expiry. No cleanup platform or cleanup run is authorized here.

All existing holdout/query and reject-before-retain/count safeguards apply unchanged, as do outcome prohibitions, false D1 flags, budget stops and protocol/freeze/C-3/8.2/8.3. Prospective Helius independent200 `getTransaction` receipts require their own later full CORE_RISK plan, reviewed code and complete gate before any call; this amendment allocates no200-call run. No Wallet/Enhanced/DAS/Developer-only method, upgrade, paid endpoint, Alchemy PAYG, redirect, purchase or limit circumvention is authorized. The preceding frozen confirmatory procedure blob is unchanged; no D1 pass or archive follows.

H1-only preflight clarification: delegated f038 accepts the same-day owner's pasted Free0/1M/reset17days text via Claude for exactly the one10-credit H1 call, not as screenshot/direct account or autoscaling verification. This narrowly overrides H1's fresh account-safeguard attestation prerequisite above; H3 still requires fresh post-H1 Free remainder/no-paid-tariff text and reconciled debit. Delegated1756 permits only design4k's bounded fixed-whitelist stat-only cumulative disk reconciliation before key/request, absent directories explicit0, into the existing manifest with independent Main10GB verification; no content read, symlink following, arbitrary scan or state file. All other safeguards/gates/caps remain unchanged.

H3 timing synchronization with approved design4k/delegated6a2882dc/eaf28793: before each actual attempt/retry, elapsed+full pending wait+60000<=6900000ms; no starts at/after115min or after terminal disposition, with5min reserved for publication. H3 elapsed aliases are explicitly PRE_PUBLICATION/prePublicationElapsedMs, not all-write wall-clock. Replay COMPLETE certifies semantic admission/reproduction only and runBudget remains UNMEASURED offline. Main's independent shell launch/all CLI writes/exit clock records nominal120min PASS/OVERRUN in existing main-h3-source.log; OVERRUN retains admitted EXPLORATORY data COMPLETE/H4 use with that label but no overall PASS. Incomplete/affected data remain incomplete/INCONCLUSIVE. No rewrite/deletion/marker/state protocol or source continuation follows; H1, all other safeguards, gates and ceilings remain unchanged. This documentation is not H3 implementation/review/gate/run evidence.

Only design4k's KNOWN_LIMITATION_ACCEPTED (abc5bdb6 corrected68d30d32) qualifies that strict-start claim: elapsed-before-store.free permits one late full request after an unknown capacity delay, potentially beyond115/120min, reserving100credits/60s timeout—not strict zero late starts or a fail-closed time guard; TIME_LIMIT data are INCOMPLETE, unchanged replay checks may reject them and then H4 is not admitted, while all other caps/controls and independent Main budget remain unchanged, with review/gate/source release still pending.

### R1-E2 path diagnostic amendment — owner-authorized 2026-10-03

This publication implements task1.3 of [preregister-r1-e2-pumpswap-cohort](../../openspec/changes/preregister-r1-e2-pumpswap-cohort/design.md), whose executable route is `tools/research/r1/e2-path-probe-cli.cjs`, subject to combined fresh independent review and Main's actual targeted gate before source access. The owner authorized a parallel, outcome-blind path check; Main controls phase transitions. It is separate from the still-draft [E2 protocol](R1_E2_RESEARCH_PROTOCOL.md), not a freeze, census, cohort admission or D1 result. Frozen E1 text, evidence, stops and repair counters remain unchanged.

For this diagnostic alone, the earlier Helius no-Maven/Docker-exception sentence and sections4/9's complete-gate prerequisite are superseded: meaningful safety/changed-behavior RED, semantic freeze, GREEN and ONE fresh independent full CI-01..CI-15 review cover the combined probe plan/code/this amendment/run receipt. Main runs the targeted and combined Node suites, test-integrity preflight, strict all-item OpenSpec validation, doctor and both diff checks; no Maven/Docker gate is required for this probe or for publishing this scoped amendment. Exactly ONE consolidated repair is allowed for the new probe, then terminal stop. No RED/full-CI waiver, role/configuration change, inherited-counter reset or archive permission follows. Later E2 confirmatory methodology/governing-procedure freeze and authoritative D1/P1 retain Main's complete gate.

Access is limited to design's three hash-pinned historical convenience pools and unchanged Helius Free full-mode query over `[1775001600,1782777600)` (`2026-04-01T00:00:00Z` to `2026-06-30T00:00:00Z`, end exclusive). At most three advancing pages per pool/nine actual attempts, zero retries,900 reserved credits,576000000 received bytes,1000000000 new retained bytes,64000000 response bytes,60000ms request,1200000ms nominal whole step,USD0,sequential250ms minimum start spacing apply. Reserve100credits/full allowances before each start; recheck time after capacity work immediately before transport, with15min source cutoff/full60s request reservation and5min publication reserve. Main's independent all-write/exit clock labels PASS/OVERRUN separately from data status. No broader address/date, wallet-history expansion, paid fallback or second public run is authorized.

Delegated decision `0a1b5e93-58e6-437d-b893-531b3bb5cbec` permits this900credit probe without a post-H3 dashboard: conditional960190=`1000000−10reconciledH1−39800reservedH3` assumes no unrelated usage. Last owner-reported Free10/1M text was2026-10-03~11:48–11:51UTC;960190 is NOT a measured current balance/current no-paid-tariff proof. Unrelated usage/autotransition are UNVERIFIED and actual H3 debit UNKNOWN. Manifest/replay retain decision ID, formula, assumptions and reservation; explicit entitlement/refusal/unexpected billing status stops. No invoice/current-Free guarantee or global quota relaxation follows.

Only this reviewed transport may use the unchanged bounded1MB ignored-file reader for `CRYPTO_HELIUS_API_KEY` at request time in memory; replay/default-disabled mode never loads it. No key, authenticated URL/query or provider error body enters output, logs, hashes, receipts or Git. New root is exclusively `C:\crypto-research-evidence\r1-e2\exploratory-path-v1`; gate/log root is `C:\crypto-research-evidence\r1-e2-path-v1-gate-20261003`. Before access Main reconciles physical old/new evidence, copies, temp and uncompressed files plus reservation against50000000000bytes aggregate and30000000000bytes free after reservation; unknown totals block,80% checkpoints/100% stops, no compression substitution or cleanup authorization.

The existing holdout exclusion and reject-before-retain/count rules apply unchanged. Genuine owned-account delta diagnostics and native/SPL payload-presence references are permitted, not payer/router proxies or complete economic reconstruction; unsupported/missing states remain explicit. Every output is EXPLORATORY with D1/cohort/actual-trader/economic-reconstruction flags false and fullD1UpperBound null. Exact fixed seed/source/schema/hash/expiry/quota provenance and offline semantic replay are required. Applicable rights remain UNVERIFIED: bounded local-only raw/derived retention expires no later than2026-10-17T11:42:35.392Z, no resale/publication, old-expiry extension or confirmatory promotion. This exception permits no prices/PnL/returns/ranking, holdout inspection/counts, census sampling, purchase or production work.

### New monthly exploratory census allocation — 2026-10-04

Delegated d04ac86f-c1c5-4570-a3b7-99026dc43a56, confirmed55c9fa41-9ddd-4a17-b42a-f0737f385765, allocates ONLY [design's new monthly contract](../../openspec/changes/preregister-r1-e2-pumpswap-cohort/design.md#new-monthly-exploratory-census--implementation-ready-contract). It is a new April→May→June1–27 sequence in three fresh fixed roots, not resuming any old STOP or rewriting old limits/evidence/expiry. Each month has16000TOTAL actual attempts including480retry starts maximum/4hours including publication/1GBreceived/1.2GBnew retained; combined48000attempts/1440included retries/12hours/3GBreceived/3.6GBnew retained, existing50GB/min30GB/80–100%/Oct10 checkpoint unchanged. HTTP529 only, at most2 identical retries15/45seconds with valid Retry-After and pre-start reservation; exhausted bounds/repeated overload stop the branch, no fallback/automatic repeat.

Question: can the fixed public SQD creation filter scan the advertised April/May/June1–27 historical reference boundaries within measured-density budgets? New tools remain disabled unless explicitly enabled after meaningful RED/GREEN, fresh independent critical review and Main's actual checks. Existing E2 change covers this scope; stronger owner-pasted full gate applies before commit. No paid/keyed source, credentials, holdout/outcomes, sampling or admission. Locations/commands/field-level caps/pins/cumulative preflight/replay are exactly the linked contract, with existing external gate receipts and no new accounting infrastructure. Every output stays EXPLORATORY, partial/UNKNOWN valid; SCAN_COMPLETE is only returned reference-boundary coverage, never chain/global-earliest/PIT/D1/cohort proof. Valid partial new replay exits2 explicitly; old replay discrepancy and task3.3 remain unchanged. All new raw expiry is2026-10-17T11:42:35.392Z, rights UNVERIFIED; no extension, publication, resale or cleanup authorization.

### Remaining May/June observed-wait guard — delegated364927f5,2026-10-04

Explicit delegated Claude decision364927f5 allocates [design's guard-v2 operational repair](../../openspec/changes/preregister-r1-e2-pumpswap-cohort/design.md#guard-v2-operational-repair--delegated364927f52026-10-04), not a personally verified new owner retention election. Frozen v1 and April source/replay evidence stay unchanged: source SCAN_COMPLETE/native0, strict replay INTEGRITY_ERROR/native1 on recorded waits below15000ms. Only new tiny guard module/CLI/tests may enforce observed same-clock retry/spacing deadlines before remaining once-only May/June, bind operational hashes in existing lineage and preserve exact replay/cumulative limits. Repair/meaningful RED/same independent APPROVE deadline05:18UTC; Main stronger full gate/preflight precede release. No tolerance, April repeat, expiry/resource reset, paid/holdout/outcome access or new platform. Deadline failure routes to Main's explicit delegated unguarded-v1 EXPLORATORY fallback choice with known timing failure/no exact replay PASS, never an automatic switch. Separate April semantic-only verification is not allocated here.

### Separate April semantic diagnostic — delegated96823df8,2026-10-04

Delegated96823df8-278e-427b-b205-8b03875acef8 separately allocates ONLY [design's fixed April semantic diagnostic](../../openspec/changes/preregister-r1-e2-pumpswap-cohort/design.md#separate-april-semantic-diagnostic--delegated96823df82026-10-04): one read-only pass, <=16000files/64000000bytes per input file/30minutes and original April wall deadline05:51:30.4079385UTC, engineering/review/gate deadline05:44UTC. Fresh Main-owned gate/april-semantic-diagnostic-v1 receipt <=64MB charges existing retained/cumulative budgets, not an increase. All frozen files/April hashes/expiry and running May source remain untouched; no original replay/network/key/holdout/outcome call. Exact non-timing semantic checks and separately recorded timing FAIL produce only qualified non-admission/native2, never overall strict PASS. Semantic FAIL stops before June for delegate; independent review/Main actual stronger gate/preflight precede the one invocation. Retention election is owner decision relayed by delegate, not directly Main-verified; no recorded expiry change is applied in this scope.
