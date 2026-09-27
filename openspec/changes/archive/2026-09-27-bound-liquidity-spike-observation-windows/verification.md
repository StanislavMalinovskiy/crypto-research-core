# Verification handoff

Status: code, tests and documentation received fresh Reviewer APPROVE; Main's complete final gate, CLI archive and post-archive checks passed. DOCS_CLOSE records 10/10 verified tasks. This is evidence, not a change to the behavioral contract.

Risk remains CORE_RISK, TR-06/TR-07/TR-08/TR-11; test mode RED_REQUIRED. Final production continuation used the selected Sol medium submission only. The explicitly authorized third repair strengthened two tests and established new RED; it did not change the frozen experimental scores or introduce another candidate's implementation.

## Behavioral RED and targeted GREEN

[Repair handoff](C:/bench/f6_2/private/production/repair-3/HANDOFF.md), [completion record](C:/bench/f6_2/private/production/repair-3/completion.json), [RED evidence](C:/bench/f6_2/private/production/repair-3/red-evidence.json), and [test-only RED diff](C:/bench/f6_2/private/production/repair-3/test-only-red.diff) retain the exact commands, assertion evidence and scoped changes.

- RED: `./mvnw.cmd -Dtest=LiquiditySpikeSignalServiceTest test` against unchanged baseline production executed 7 tests, with 6 assertion failures, 0 errors and 0 skipped. Named new failures: `rejectsNullDetectorVersionBeforeAnyCollaboratorInteraction` expected `IllegalArgumentException` but received `NullPointerException`; `rejectsUnrepresentableBaselineLowerBoundWithRepresentableOneHourTarget` expected an exception but none occurred. These are behavioral failures, not compilation or infrastructure failures.
- GREEN: the same unit command passed all 7 tests. Same-context `docker version` passed; `./mvnw.cmd '-Dit.test=LiquiditySpikeObservationWindowsIT,FirstSignalEvaluationIT' verify` passed 75 Surefire and 7 Failsafe tests, all with zero failures, errors or skips. The Failsafe set contains 4 observation-window tests and 3 first-slice tests on real PostgreSQL. See [unit log](C:/bench/f6_2/private/production/repair-3/green-unit.log), [Docker log](C:/bench/f6_2/private/production/repair-3/docker.log) and [targeted integration log](C:/bench/f6_2/private/production/repair-3/green-integration.log).
- The strengthened tests cover null-version rejection, baseline lower-bound underflow, no collaborator interaction on invalid requests, same-transaction Unicode event-locator ordering and a separately rebuilt reversed-input dataset with complete snapshot/fingerprint/row-count comparisons. Existing assertions remain.

## Frozen identity and scope

The [frozen test inventory](C:/bench/f6_2/private/production/repair-3/frozen-red-hashes.json) matches the post-GREEN files. `tests_changed_after_red=false`; the raw fixture is unchanged. SHA-256 values:

| Path | SHA-256 |
|---|---|
| `src/main/java/io/cryptoresearch/signal/application/LiquiditySpikeSignalService.java` | `8C6E133E09F19B07EEC685CB2FA2C4D58F2EEBE50030CEF0B39447D24632773C` |
| `src/test/java/io/cryptoresearch/signal/application/LiquiditySpikeSignalServiceTest.java` | `62235E532F4A45A975D17CAD0D8EB91F5DD2A96BC8EF98FEA73456EFC8469DD6` |
| `src/test/java/io/cryptoresearch/signal/application/LiquiditySpikeObservationWindowsIT.java` | `BFC9A2EAC7A7D7C6A29F5AAA14AEDF00E88482D4F290FDCE64BEB9ECA1B3EEF2` |
| `src/test/java/io/cryptoresearch/FirstSignalEvaluationIT.java` | `59E546A8674C9F3BEFDDF59A3AE38B4951400FF25988ED4BFDC89A255EA9D774` |
| `src/test/resources/fixtures/first-signal-evaluation.json` | `DF405FAB6092C4C13E30EC7A0B0B32F856DA7B5F4791AAFE82BD8524E7D0A16A` |

The production service and `FirstSignalEvaluationIT` are byte-identical to the selected frozen submission. One production file and three test files stay within the design budget. No public API shape, dependency, migration, persistence algorithm, module boundary or runtime/build configuration changed. The handoff contains the applicable invariant evidence; Main confirmed the fresh Reviewer approved both code/tests and documentation before this closure update.

## Completion gates and archive

Main's [full-gate results](C:/bench/f6_2/private/production/final-gate/results.json) record PASS from `2026-09-27T00:57:15.5533998Z` to `00:58:21.6153079Z`: integrity preflight, same-context Docker, `mvnw.cmd clean verify`, strict all-item OpenSpec validation, doctor, working-tree diff check and cached diff check each exited 0. Maven passed 75 Surefire and 48 Failsafe tests with zero failures, errors or skips.

Main created raw-byte checkpoint `a5f7c754e8ea80173f743958e2243ce446f562dc` at `refs/checkpoints/f6_2-prearchive-20260927`; see the [checkpoint manifest](C:/bench/f6_2/private/production/pre-archive/checkpoint.json). After matching HEAD, checkpoint ref, index and all 14 file hashes, `openspec archive bound-liquidity-spike-observation-windows --yes` exited 0: six files moved to this dated archive and two accepted requirements were modified. The authorized 9/10 warning preceded completion of task 4.3; see the [CLI log](C:/bench/f6_2/private/production/archive.log).

Main's [exact mutation inspection](C:/bench/f6_2/private/production/archive-mutation-check.json) confirmed only the 13 expected paths, unchanged raw archive bytes and index, with no restoration needed. [Post-archive results](C:/bench/f6_2/private/production/post-archive/results.json) record strict all-item validation, doctor, `RepositoryConventionsTest` and diff check each exiting 0, completed at `2026-09-27T01:04:36.7934493Z`. Task 4.3 was checked only after these results. F1, F3 and full production F6.2 gap/coverage work remain outside this completed bounded change.
