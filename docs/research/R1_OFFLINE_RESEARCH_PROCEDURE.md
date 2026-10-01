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
