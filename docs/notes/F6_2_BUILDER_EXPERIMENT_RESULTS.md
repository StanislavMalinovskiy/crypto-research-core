# F6.2 Builder experiment results

Non-normative evidence note, 2026-09-27. This records four frozen first submissions and their original independent scorecards, with the separately verified bounded production continuation below. It does not change the product contract, model-routing policy or scoring rubric. See the [operator protocol](F6_2_BUILDER_EXPERIMENT_PROTOCOL.md) for the procedure and limits.

## Frozen results

All four scorecards were frozen at `2026-09-27T00:38:55.6738376Z` before Main revealed the assignment. The [freeze record](C:/bench/f6_2/private/all-scorecards-freeze.json) has SHA-256 `92A6EA84DAE728711A683907E98DBB31C0A2B77BC39A1F40A6C0D8357A459AAE` and records `mapping_revealed=false` at that milestone. The mapping below was supplied by Main after the freeze. Original scorecards and scores remain unchanged.

| Candidate | Assigned Builder | Original score / 100 | First submission | Independent supplementary checks | Wall time |
|---|---|---:|---|---|---:|
| A | gpt-6-luna / max | 95 | Implementation delivered | 36 passed | 20m 37s |
| B | gpt-6-luna / xhigh | 57 | Tests only; blocked on its own frozen invalid test request, no implementation | 20 passed, 16 failed | 13m 26s |
| C | gpt-6-sol / medium | 95 | Implementation delivered | 36 passed | 4m 53s |
| D | gpt-6-sol / low | 98 | Implementation delivered | 36 passed | 4m 45s |

Raw scorecards: [A](C:/bench/f6_2/private/inline-review/sessions/inline-a/last-message.txt), [B](C:/bench/f6_2/private/inline-review/sessions/inline-b/last-message.txt), [C](C:/bench/f6_2/private/inline-review/sessions/inline-c/last-message.txt), [D](C:/bench/f6_2/private/inline-review/sessions/inline-d/last-message.txt). All report incomplete integration status; a score is not production approval. For B, the 20 passing supplementary controls largely preserve existing behavior and do not indicate delivery of v2. Its independent behavioral failures include stale endpoint admission and unsupported versions.

Each score came from a fresh Sol high read-only session receiving the same frozen rubric and evidence categories inline. The earlier file-reading attempt for A was infrastructure-invalid because its commands were blocked by policy; it obtained no source content and issued no grade. It is retained separately and is not part of the score comparison. The common inline delivery freeze is `DC536EAF214308A49A1BEDCB101135269F9A48C8F67B7132ED57902A7F973769`.

## Interpretation and scorecard caveats

The practical outcome on this task is that A, C and D delivered implementations passing the independent 36-case suite, while B stopped before implementation. This is one task and one original attempt per variant; it is not a general model ranking or evidence that the three-point difference between 95 and 98 is robust.

Several scorecard judgments do not consistently follow the agreed division of work:

- A, C and D lost autonomy/closure points for missing module documentation, unchecked tasks or the full repository gate. Those are Architect/Main responsibilities in the frozen Builder assignment, so their absence is a production-completion limitation, not by itself a Builder failure.
- C also lost a handoff point because full-gate evidence was absent, although Main owns that gate. Targeted Builder verification and the complete repository gate are distinct obligations.
- D received full authored-test coverage credit with references that combine its submitted tests and independent supplementary probes. Independent tests establish implementation behavior; they do not establish that the Builder authored the same coverage. C's scorecard distinguished those sources more strictly.

The original numeric results are preserved rather than retrospectively reweighted. No corrected score or statistical winner is claimed. Authored-test weaknesses, diff-check findings and actual implementation defects remain meaningful observations where supported; these caveats do not turn all missing evidence into a pass.

## Resource measurements

The [measurement record](C:/bench/f6_2/private/first-submission-measurements.json) retains final `turn.completed` usage counters verbatim. Input/cache/output/reasoning fields are reported separately; they are not estimates of unique source volume or added together as disjoint categories. All four reported cache-write input counters are zero, no run expired, and verified infrastructure exclusions are zero.

| Candidate | Input tokens | Cached input tokens | Output tokens | Reasoning output tokens |
|---|---:|---:|---:|---:|
| A | 4,339,887 | 4,154,624 | 60,523 | 36,282 |
| B | 1,705,574 | 1,568,768 | 39,351 | 24,439 |
| C | 933,730 | 865,664 | 11,545 | 2,021 |
| D | 1,040,996 | 968,704 | 10,422 | 690 |

Dollar cost is **UNKNOWN** for all four: no actual billing evidence was supplied. Wall times are rounded from the recorded seconds and are not part of the 100-point score. The host dependency cache was warm/shared; fresh source copies and fresh sessions provided procedural separation, not a security boundary or identical cold-cache performance. Requested model/effort settings were accepted by CLI smoke headers, but effective backend routing is not independently attested by CLI JSON metadata.

## Production continuation

Candidate C is the sole production source because the owner selected Sol medium before the results were known, not because its score won. No code or tests from A, B or D are adopted. The first-submission artifacts and scores remain frozen independently of subsequent production work.

Main reports that the explicitly authorized final repair round 3 strengthened C's tests with new behavioral RED and GREEN evidence while production code and `FirstSignalEvaluationIT` remained byte-identical to frozen C. The three test-file hashes and raw fixture hash match their post-repair freeze. The fresh Reviewer returned APPROVE for code, repaired test coverage and documentation. Main's [full-gate results](C:/bench/f6_2/private/production/final-gate/results.json) record every required check passing, including 75 Surefire and 48 Failsafe tests with no failures, errors or skips. The bounded change is [archived with 10/10 verified tasks](../../openspec/changes/archive/2026-09-27-bound-liquidity-spike-observation-windows/verification.md); [post-archive checks](C:/bench/f6_2/private/production/post-archive/results.json) also passed. This later repair and completion do not change C's original score or authored-test assessment, and do not complete F1, F3 or full production F6.2 coverage.
