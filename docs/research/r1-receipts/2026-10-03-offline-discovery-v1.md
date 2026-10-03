# Offline provisional discovery v1 — 2026-10-03

## Terminal disposition

One approved offline invocation stopped honestly at `EXPLORATORY / DISCOVERY_INCOMPLETE`, native exit2, `code=MAPPER_INVALID`, `mapperCode=OUTPUT_LIMIT`, `scanComplete=false`. Published addresses/observations are empty and no semantic result hash was emitted; this is withheld partial output, not measured absence of traders/activity or a reconstructed §5.1 candidate universe.

Current fresh Reviewer found the stop correct under the existing contract, with no discovery implementation defect. Delegated owner `928cab30-5a07-45ca-a142-bd675812a0a0`, confirmed by `1f9fb205-4330-4991-932e-6b8937e25faf`, makes this branch terminal: no second invocation/reproduction, repair, cap increase or decoder change. Implementation APPROVE stands; task1.42 remains unchecked. Old mapper1.29/1.30, SQDv3 NOT_RUN and section2 dispositions remain unchanged; D1 is active, not passed or archivable.

## Exact invocation and failure

Main ran `node tools/research/r1/offline-discovery-cli.cjs --enable-offline` once, UTC `2026-10-03T16:10:49.9648318Z` → `2026-10-03T16:10:50.6903610Z`. The independent timing record reports701ms and time-budget PASS against600000ms, not data/completion PASS. Source HEAD at invocation was `a9f21c7`, dirty=true; a later commit does not change that historical observation.

Failure input: `exploratory-sqd-v1/014.raw`, manifest SHA-256 `fc1fe02982ec49885e7553b2e35331c9f5444e2fca5924327bd0e4d4eed74a4a`; parent and child SHA-256 both `c83d0a757d882d9122080da8d1a8282a12612ca4acb943fa4c81833421a5de95`, parentSize/childLength1025412bytes, offset0, record ordinal13. `OUTPUT_LIMIT` can denote the existing1000-diagnostic or2MB serialized mapper-output cap; the retained report does not distinguish which, and this receipt does not infer it.

Partial work counters: rawFilesRead14/rawBytesRead2398712/inputBytes2911122; childOccurrences11/mapperRows4104; invocationOccurrences209/transferOccurrences1171/tokenStateOccurrences699/nativeBalanceRows429; nonemptyErr48/numericErrTokens94/missingErr0; sourceRecords498/missingPayloads1/skippedRecords482/conflictingEvents0. These are partial processing occurrences, not unique trades, candidates, economic reconstruction, complete coverage or D1 metrics. Zero counted missing errors/conflicts does not certify unread inputs.

The native report is1160bytes, SHA-256 `026e0855979decdbbe9ae6abca33cfcc511f98859981515173d508ec313bcaa1`. Main verified owned process count0, credential-marker count0 and unchanged mapper/helper SHA pins. No provider call, key read, source-root write, holdout access or spending was performed by this offline invocation; retention remains UNVERIFIED with inherited expiry no later than `2026-10-17T11:42:35.392Z`. All authorization/D1/actual-trader flags remain false; fullD1UpperBound/fullD1Fits/reconstructedTradeCount remain null.

## Reviewed implementation and evidence

Same fresh Reviewer full-CI implementation APPROVE/red_suspect=false after consumed repair1:377/650new nonblank lines (core213/CLI9/tests155),0 existing implementation/test changes. Original7 behavioral RED tests plus only2 authorized regressions (nested Helius metadata and Git deadline); tests_changed_after_red=true for those additions, no old assertion weakening/post-freeze change. GREEN9/9 targeted and133/133 total, fail0/skip0.

Main's eight prelaunch gate commands passed: integrity preflight, Docker, `.\mvnw.cmd clean verify` (96unit+76IT, failures/errors/skips0,01:40min), Node133/133, strict15/15, doctor, diff and cached diff, all native exit0. This verifies implementation/run preconditions, not a successful discovery scan or reproduction.

Native evidence directory: `C:\crypto-research-evidence\r1-d1-offline-discovery-v1-gate-20261003`. Run: `main-discovery.log`/`main-discovery.exit`/`main-discovery-timing.log`; gates: `main-{integrity,docker,maven,node,strict,doctor,diff,cached-diff}.log/.exit`; behavioral evidence: `builder-{red,green,all-green}.log/.exit` and `builder-repair1-{red,green,all-green}.log/.exit`. Owner replies are retained outside the repository in the existing peer session directory; no new admission state/protocol or source permission is created by this receipt.
