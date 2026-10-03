# PumpSwap transaction-error lexeme audit — 2026-10-03

## Disposition

`COMPLETE` offline audit; native exit0. Current independent Reviewer gives conditional `APPROVE` only for the exact existing hashset below as `EXPLORATORY / KNOWN_LIMITATION_ACCEPTED`, under delegated decisions `0334ceb1-71ce-4244-846d-1bd17cbb262b` and `cee7b3d9-641b-48e5-9452-342ec005c924`. This is not general mapper approval, arbitrary-input correctness, production/source admission or D1 evidence.

The arbitrary-input numeric-rounding repro remains `BLOCK` and unfixed: a numeric lexeme such as `1.0000000000000001` can round to integer1 in the typed parser. The observed zero violations support only these immutable manifest-listed bytes, not all valid RPC data. Every new raw or derived input requires its own new bounded contract and audit; no automatic admission, mapper raw invocation, additional004 smoke, discovery implementation or provider call is granted. Tasks1.29/1.30 remain unchecked; historical STOP and pre-fix smoke remain history.

## Exact audited inputs

All roots are under `C:\crypto-research-evidence\r1-d1\`; each pin identifies that root's existing `manifest.json`. Only its listed raw files were scanned; metadata, unlisted and partial files were excluded. Every listed raw size/SHA-256 and all four manifest pins passed before proof.

| Root | Manifest SHA-256 | Raw files | Raw bytes |
|---|---|---:|---:|
| `exploratory-sqd-v1` | `fc1fe02982ec49885e7553b2e35331c9f5444e2fca5924327bd0e4d4eed74a4a` | 28 | 5962659 |
| `exploratory-sqd-v2` | `5e8dc296b2ce20c9a20e7b9728c202278153516d702bdbbe01762bc45cffb224` | 66 | 317030 |
| `exploratory-helius-h1-v1` | `5cc326892ab985d09d2b7f287beaa28dff41ac7ae8a4ab00f3eab81033671910` | 1 | 224743 |
| `exploratory-helius-v1` | `6e675fce49ddd0c0e3ee6b485e768710be3e634bf3c7db8bc68a4f242d4aa513` | 324 | 3942470434 |
| Total | Exact419-file allowlist | 419 | 3948974866 |

File-proof aggregate SHA-256: `12b95d5476b50660f51f3043fe95943654f6d0bc2552879d99dbd14c2b8063da`.

Existing preholdout query lineage and source/retention limitations are retained, not re-established by this diagnostic. H1/H3 exploratory retention expires respectively `2026-10-17T11:42:35.392Z` / `2026-10-17T13:23:07.856Z`; rights remain `UNVERIFIED`, with no extension or confirmatory OD-2 admission. No holdout, outcome or new source data was read.

## Method and actual result

The current read-only Reviewer designed and supplied the inline diagnostic and independently assessed its result; Main executed it once and captured stdout/native exit. The bounded sequential diagnostic used existing exported `parse(bytes,true)` paired with `parse(bytes,false)` from `exploratory-probe.cjs`. Original numeric lexemes in nonempty error fields were checked without rounding: no decimal point/exponent, nonnegative integer<=u32; InstructionError index additionally<=u8. Unsupported variants remain UNKNOWN and count toward unsupported/ambiguity, not silent exclusion. No new script, parser, state file, raw-root write, code/test edit or repair was performed by the audit.

UTC `2026-10-03T14:50:19.745Z` → `2026-10-03T14:53:13.906Z`; elapsed174160ms, within the30-minute bound. Per-raw64MB/manifest4MB bounds and exact419-file/3948974866-byte total were satisfied; the retained native log is2642bytes, within4MB.

| Audit occurrence counter | Result |
|---|---:|
| JSON records | 1995 |
| Error fields / nonempty errors | 308809 / 73133 |
| Numeric tokens | 130598 |
| Success / supported failed | 235676 / 57465 |
| Unsupported UNKNOWN / unknown ambiguity | 15668 / 15668 |
| Missing error / violations | 0 / 0 |

These are audit occurrences across overlapping retained responses, not unique transactions, verified trades, candidate wallets, coverage or D1 metrics. UNKNOWN is not success or failure. Source/H3 debit remains UNKNOWN; this offline diagnostic made zero provider calls and incurred USD0.

## Review and evidence

The narrow used err-classification exception established behavioral RED10executed/8pass/2assertion failures, then GREEN10/10 mapper and124/124 total Node tests. `tests_changed_after_red=true` covers exactly the current-reviewer-authorized synthetic expectation correction and two appended groups; no establishing change beyond that permission or post-freeze change. Production16/test32 added-or-changed nonblank lines satisfy20/45 caps. The sole additional old4j exception is consumed, without resetting any old repair counter. Conditional Reviewer APPROVE does not remove the arbitrary-input blocker.

Native audit log: `C:\crypto-research-evidence\r1-d1-offline-batch-gate-20261003\main-mapper-err-lexeme-audit.log`; captured `.exit` is0. Log SHA-256: `a7046f8219e0f8e0180b7081fb7d745b0ff29181bef4f40a7a7f05bbef29e5e8`. The immutable manifests retain individual raw proofs; this receipt is documentary evidence, not a new admission state/protocol.

Main owns the complete final integrity/Docker/Maven/Node/strict/doctor/diff gate after DOCS_CLOSE, with native gate logs retained in `C:\crypto-research-evidence\r1-d1-mapper-err-exception-gate-20261003` and actual results reported in Main's handoff/checkpoint. This receipt claims only the already verified targeted review/audit facts, no full-gate PASS. All D1 flags, source-field statuses, section2 tasks, old SQDv3 NOT_RUN and historical mapper dispositions remain unchanged; no archive.
