# R1 offline research procedure

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
