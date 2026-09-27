# F3.4 Builder experiment results

Status: **four first submissions and four first assessments complete; production BLOCKED / OWNER_DECISION**. This is a non-normative report under the [frozen experiment protocol](F3_4_BUILDER_EXPERIMENT_PROTOCOL.md). It records existing judgments without rescoring or changing the rubric. No replacement assessments, production adoption, full production gate, archive or commit occurred. The active product change (task navigation after archive: `openspec/changes/archive/2026-09-27-add-recorded-replay-operational-telemetry/tasks.md`) remains 0/7 tasks complete.

The external [owner-facing results report](C:/bench/f3_4/RESULTS.md) contains the Russian-language handoff and retained artifact index.

## Frozen results

The [blind audit](C:/bench/f3_4/private/blind-audit.md) and its [freeze record](C:/bench/f3_4/private/blind-audit-freeze.json) establish score validity and adoption restrictions. The [assignment](C:/bench/f3_4/private/assignment.json) and [revealed results joined to dispatch](C:/bench/f3_4/private/revealed-results.json) supply the model/effort mapping; [submission telemetry](C:/bench/f3_4/private/submission-telemetry.json) supplies wall time. Authored results below are operator executions of candidate-authored tests; independent results are the separate hidden suite, not authored coverage credit.

| Submission / anonymous ID | Requested model / effort | Raw score / 100 | Audit disposition | Authored tests passed / total | Independent probes | Candidate wall seconds |
|---|---|---:|---|---:|---:|---:|
| A / `submission-7c91a2b4` | `gpt-6-luna / xhigh` | 92.5 | VALID; no scored integrability veto | 8/8 | 20/20 | 956.5258834 |
| B / `submission-d36f802a` | `gpt-6-sol / low` | 95 | VALID; no scored integrability veto | 5/5 | 20/20 | 314.2566597 |
| C / `submission-9ab047e1` | `gpt-6-sol / medium` | 92.5 | VALID score; evidence-provenance adoption veto | 7/7 | 20/20 | 696.95394 |
| D / `submission-2e58c60d` | `gpt-6-luna / max` | 92.5 | ATTRIBUTION_INVALID; excluded from ranking; submitted tests red | 5/6 | 20/20 | 1130.7363651 |

Among valid scorecards B has the highest score; A and C tie. D retains its raw score without a replacement total or rank. These are results for one bounded task and one attempt per setting, not a general model ranking. Scoring success grants no production review approval.

The frozen rubric allocates 50 points to product behavior, 20 to candidate-authored tests, 20 to preservation/scope and 10 to handoff evidence. All raw awards use the allowed zero/half/full values, respect category maxima and sum correctly. The audit retained these distinctions:

- A: the authored log test throws inside `AppenderBase.append`, whereas the independent emission-boundary oracle overrides `doAppend`. T2 addresses the coverage gap; H2 separately addresses the affirmative handoff claim that the supplementary test confirms throwing-sink isolation. The audit retained this bounded interpretation without rescoring.
- B: one T2 deduction addresses authored log-boundary coverage; no duplicate handoff deduction or objective arithmetic/provenance contradiction was found.
- C: raw events 32/34/36/38/49 establish RED, hashes/status, first production write, GREEN, then diff-file creation. Event 34 reports that the proposed empty production diff does not exist; event 49 creates that file and the test diff after GREEN. The handoff describes those files as pre-implementation captures. Earlier status and hashes independently support genuine behavioral RED and unchanged establishing tests. The defect concerns the claimed provenance of the diff artifacts; it does not establish fabricated RED or intent. The valid 92.5 score and adoption veto remain separate.
- D: T2/D2 already attributes the wrong Optional-value assertion to the authored-test shortfall, with an H1 zero-deduction cross-reference. H1/D3 then deducts another 2.5 solely because that same assertion leaves the selector red. The handoff truthfully reports BLOCKED and supplies the red log; no independent handoff omission supports that second deduction. This violates the frozen single-primary attribution rule. No substantive regrade was performed. Independently, the submitted suite has one failure and blocks adoption as delivered.

## Evidence freeze and disclosed incidents

[Raw scorecards](C:/bench/f3_4/private/raw-scorecards-freeze.json) were frozen at `2026-09-27T09:32:31.1994836Z`, before mapping reveal. The [blind audit freeze](C:/bench/f3_4/private/blind-audit-freeze.json) followed at `2026-09-27T09:38:06.2023083Z`, also with `model_mapping_revealed=false`, `scoring_complete=true` and `no_replacements=true`. It records the raw-freeze SHA-256 `7D3BC24FB88C3CFA70E70CA419D790F214B69223739C3EB27F5D624E7E99E3E7` and audit SHA-256 `B7056C03C7028222A0D3E16C18E9BAC41BC68B08249B4A5534EEEDDE1282CDE6`, plus each prompt and raw scorecard identity. The mapping was revealed after those judgments were frozen.

The [prelaunch global freeze](C:/bench/f3_4/private/global-freeze.json) records source HEAD `5d7a4936271417d2819106a2b0b94878ba42c535`, sanitized baseline `26478b6e7c32c5aa96b65e0e2e606e3802b014b6`, verified manifest closure, a common 2,700-second budget and frozen product selection. [Launch approval](C:/bench/f3_4/private/launch-approval.json) records independent readiness APPROVE. The assignment used seeded Fisher-Yates with seed `1926566624`; candidates ran sequentially A, B, C, D in fresh copies and fresh sessions. Assignment was available to the trusted dispatch runner/operator boundary (`operator_blind=false`), not protected from Main by a security boundary; Main withheld it from the arithmetic/provenance audit until freeze; scoring reviewers were blind.

The [harness incident](C:/bench/f3_4/private/HARNESS_INCIDENT.md) occurred after all submissions were frozen and before scoring. The original hidden scanner included test-output classes, allowing candidate test configuration to register competing mocks. Runtime probing established that intended hidden doubles were bypassed. Initial failures were not attributed to candidates. An independently approved setup-only v2 correction restricted discovery to production code sources, preserved all 20 case bodies/assertions, re-established the baseline's 13 behavioral failures and seven passing controls, and added an isolation probe. Uniform v2 verification was then applied to all four frozen submissions. Original runs and hashes remain retained; candidates received no feedback or reruns. See [v2 approval identities](C:/bench/f3_4/private/harness-v2-approval.json).

The [delivery incident](C:/bench/f3_4/private/DELIVERY_INCIDENT.md) records an oversized request rejected at `turn/start` on `2026-09-27T09:13:16Z`: 1,060,495 characters exceeded the backend ceiling of 1,048,576. It created a thread but no model turn, usage event, answer or scorecard. The [v5 delivery approval](C:/bench/f3_4/private/scoring-v5-approval.json) authorized a common lossless presentation correction, retaining all data fields/values, source/test bodies and raw artifacts while compacting JSON/XML presentation whitespace and checking bounds. All four scorers received that common corrected delivery, unchanged rubric and v2 evidence with the original incident retained. There were **four actual scoring assessments, not eight**: four distinct fresh Sol high threads, each one model turn with no tools or compaction. Technical readiness/harness/delivery reviews were not coding scorecards. No score replacement occurred.

## Timing, usage and limits

The [submission telemetry](C:/bench/f3_4/private/submission-telemetry.json) retains exact start/end offsets, thread IDs and evidence hashes. Its reported usage counters are reproduced here without estimating billing; reasoning output is a reported component, not an additional cost total.

| Submission | Input tokens | Cached input tokens | Output tokens | Reasoning output tokens | Cost |
|---|---:|---:|---:|---:|---|
| A | 2,387,973 | 2,250,496 | 43,580 | 27,309 | UNKNOWN |
| B | 879,861 | 824,320 | 9,332 | 825 | UNKNOWN |
| C | 858,797 | 765,312 | 12,079 | 2,204 | UNKNOWN |
| D | 3,055,504 | 2,918,144 | 49,443 | 31,599 | UNKNOWN |

No billing amount was emitted. Time and token counters do not establish cost. Scorer timing and usage are separately preserved in the raw-scorecard freeze and are not candidate wall time. Shared warm caches and sequential order can affect elapsed time. Same-host fresh copies provide procedural separation, not security isolation; the audit found no observed access to private comparison material. Requested settings and observed CLI headers are dispatch evidence, not independent backend attestation. The disclosed post-submission harness and delivery corrections, single task and single attempt limit the comparison.

## Production disposition

The [preparation authority](C:/bench/f3_4/private/preparation-authority.json) allowed only the frozen `gpt-6-sol / medium` submission to seed production, independent of score, with no mixing or substitution. C's evidence-provenance adoption veto therefore invokes the existing **OWNER_DECISION** guard. B's higher valid score does not authorize adopting B. D's invalid attribution does not authorize rescoring it. Further assessment or production continuation needs owner direction under the unchanged protocol.

Product risk remains **STANDARD**, `risk_triggers=none`, `test_mode=RED_REQUIRED`. Operator integrity risk remains **CORE_RISK**, `risk_triggers=TR-11,TR-12`; this report changes neither classification nor contract. Product source/tests and accepted specs remain untouched by adoption; no fresh production review, full production gate, archive or commit is claimed. Benchmark assessment completion does not complete F3.4 or the broader F3 data-quality gate.
