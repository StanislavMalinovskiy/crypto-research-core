# R1 protocol freeze record

This is the freeze record required by section 2.3 of the [R1 research protocol](R1_RESEARCH_PROTOCOL.md). It is append-only: later entries are added below and earlier entries are never edited. The protocol does not hash itself; this record pins it.

## Entry 1: protocol freeze `1.0.0`

| Field | Value |
|---|---|
| Protocol | `R1`, experiment `R1-E1` |
| Protocol version | `1.0.0` (`FROZEN`) |
| Freeze date | 2026-10-01 (UTC) |
| Freeze commit | `6e5647e61ba28bbc5bba6042f6dc97a7ffc3930e` |
| Protocol SHA-256 | `sha256:283ea9d5023795fb8d6252a2e8677f3a5893e0d4e7a13c1f8d3cc6ea46b176ba` |
| Offline procedure in force | [R1 offline research procedure](R1_OFFLINE_RESEARCH_PROCEDURE.md) `0.3.1-draft` (content of `0.3.0-draft`) |
| Offline procedure SHA-256 | `sha256:de51ff6445d2ffd82c45b0d8107c09559e8d2061fc25ff99ec77477f660adb08` |
| OpenSpec change | `preregister-r1-research-protocol` |

Recomputation, from the repository root:

```text
git cat-file blob 6e5647e61ba28bbc5bba6042f6dc97a7ffc3930e:docs/research/R1_RESEARCH_PROTOCOL.md | sha256sum
git cat-file blob 6e5647e61ba28bbc5bba6042f6dc97a7ffc3930e:docs/research/R1_OFFLINE_RESEARCH_PROCEDURE.md | sha256sum
```

Both hashes are of the committed blob bytes (LF line endings), not of working-tree bytes after any checkout line-ending conversion.

### Approved owner decisions

| Decision | Content | Date |
|---|---|---|
| Parallel start | R1 drafting in parallel with the A1+A2 change `version-market-facts-and-split-evidence`, for drafting only | 2026-10-01 |
| Documentation RED exception | One-time exception from behavioral RED only for this protocol document and the change `preregister-r1-research-protocol`; risk stays `CORE_RISK`, fresh independent review and Main's complete gate mandatory; every later calculation and implementation requires behavioral tests | 2026-10-01 |
| Scope option (a) | Bounded pilot: budgets, sample floor and 4.5 percent effect bar unchanged; small true effects will often end `INCONCLUSIVE` (`UNDERPOWERED`) | 2026-10-01 |
| `OD-1`..`OD-8` | Every value exactly as written in protocol section 14 | 2026-10-01 |
| `OD-4` | Offline research procedure (content `0.2.0-draft`, then `0.3.0-draft` as `PROC-0.3`) | 2026-10-01 |
| `OD-6a` | Holm family size `m = 2` fixed; a single family opening its holdout is tested at 0.025 | 2026-10-01 |
| `C-3` | Modeled availability offset, always labelled `MODELED` with model version, never a measurement: default 60 s; refined value max(60 s, measured 90th percentile), at most 600 s; above 600 s D1 fails | 2026-10-01 |
| `OD-7a` | Deduplication key (family, configuration, token), 24 h from the last retained `t_avail`, excluded from positions, floor, unknown share and portfolio, same rule for baselines; `DEDUPED` counted and reported separately per family, configuration, interval and scenario for strategy and baselines | 2026-10-01 |
| `ID-LABEL` | Public address labels only with saved source and exact version, manifest content hash, and confirmed availability before the selection cutoff or `knownAt`; otherwise not used | 2026-10-01 |
| `PROC-0.3` | At most one holdout technical rerun; defect correction before holdout viewing recorded as a new holdout-freeze entry | 2026-10-01 |

### Holdout attestation

Owner-attested 2026-10-01: no viewing of wallet or token returns for 2026-08-31 to 2026-09-28 and no use of that period to choose rules.

### Review reference

Fresh independent Reviewer returned APPROVE on protocol `0.7.0-draft` and procedure `0.3.1-draft` after review repair round 1, with the full CI-01..CI-15 matrix PASS or not applicable with reasons and no blocking findings. Protocol `1.0.0` is content-identical to the reviewed `0.7.0-draft` except its header version, status, frozen-date, freeze-record and offline-procedure lines and its `1.0.0` changelog entry.

### Gate reference

Main's complete gate on the reviewed `0.7.0-draft` state:

| Check | Result |
|---|---|
| `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1` | exit 0 |
| `docker version` | 29.8.0 |
| `mvnw.cmd clean verify` | BUILD SUCCESS; surefire 96 run, 0 failures, 0 errors, 0 skipped; failsafe 48 run, 0 failures, 0 errors, 0 skipped |
| `openspec validate --all --strict --no-interactive` | 15/15 passed |
| `openspec doctor` | exit 0 |
| `git diff --check` | exit 0 |

A further complete gate runs on the final state before archive.

## Entry 2: D1 calibration amendment `1.1.0`

Not yet recorded. Per protocol sections 2.3 and 2.4, this entry will record the UTC date, version `1.1.0`, commit, protocol SHA-256 at that commit, each refined whitelist value (`C-1`, `C-2`, `C-3`) with its measurement query/export version and manifest fingerprint, and the review reference.

## Entry 3: holdout freeze

Not yet recorded. Per protocol sections 2.3, 2.5 and 12.4, this entry will record the UTC date, protocol version, analysis code commit, canonical configuration fingerprint, dataset manifest fingerprint, random seeds and review reference. A defect correction before any holdout outcome is viewed adds a new holdout-freeze entry with the new commit; a technical rerun (at most one) records both attempts in the run receipt.
