## Implementation and review evidence

Recorded on 2026-09-27 from Builder and Main handoffs. Risk is `CORE_RISK`; test mode is `RED_REQUIRED` for executable routing. A fresh `gpt-6-sol / medium` Reviewer returned `APPROVE` without findings and with `red_suspect = false`. No repair round was required.

| Check | Result |
|---|---|
| `openspec validate adopt-gpt6-agent-routing --strict --no-interactive` | Passed |
| `pwsh -NoProfile -File .codex/scripts/log-agent-activity.tests.ps1` | RED: Architect REVIEW dispatch expected exit 2, observed exit 0 and persisted assignment state; GREEN after routing change |
| `mvnw.cmd -Dtest=RepositoryConventionsTest test` | RED: old model map failed the new routing assertion, 22 tests, 1 failure, 0 errors; GREEN: 22 tests, 0 failures, 0 errors, 0 skipped |
| Current documentation/model search | No obsolete model routing in current guidance; historical accepted changes retained |
| Workstation default | Existing `gpt-6-sol / medium` verified; no workstation configuration edit needed |
| Product scope | Application source and application regression scenarios unchanged; only agent-policy portions of the repository convention test changed |
| Notion synchronization | Main published and reread five affected pages: agent guidance, summary, tech stack, ideas and core; current model routing matched, obsolete model/benchmark claims were absent, all 29 core numbered sections and API page mentions were retained |

Frozen SHA-256 values, independently reread during DOCS_CLOSE:

- `.codex/scripts/log-agent-activity.tests.ps1`: `F9EEBDABED1AB8B1899A5ECBC2897471F3A58752F5BEC28C994BC200CD795035`.
- `src/test/java/io/cryptoresearch/RepositoryConventionsTest.java`: `0E48A27E6D2E04DE1503D505603DB87B4DA78A96FA5D2D6041B522CF16E2BFE7`.

The ignored local pre-implementation diff is `.codex-logs/adopt-gpt6-routing-pre-implementation.diff`. Builder confirmed both establishing tests retained these hashes through GREEN. Review confirmed CI-15's bounded work/repair contract; CI-01 through CI-14 are outside this configuration and governance change's domain scope.

## Obsolete benchmark cleanup

The tracked obsolete draft `docs/notes/LEAN_MULTIAGENT_WORKFLOW_DRAFT.md` was removed; Git history retains its committed content. Seven ignored artifacts were moved from `.codex-logs/benchmark-results/evaluation-persistence/` to the recovery directory below:

```text
C:/Users/stasm/AppData/Local/Temp/crypto-old-model-benchmark-a7ad1b2f43424c2885819958b34023f9/evaluation-persistence/
```

Exact filenames under both prefixes:

- `pre-run-a-baseline.patch`
- `run-a-artifacts.zip`
- `run-a-score.md`
- `run-b-artifacts.zip`
- `run-b-baseline.patch`
- `run-b-preparation.md`
- `run-b-score.md`

The seven recovery files were present when DOCS_CLOSE recorded this manifest. Unrelated user-deleted hooks and archive pages remain untouched. README navigation was adjusted for the already-deleted archive pages.

## Complete final gate and specification sync

Main independently reported the following successful checks after review and documentation closure:

| Command | Result |
|---|---|
| `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1` | Passed before Maven |
| `docker version` in host-access context | Passed after starting the initially stopped Docker Desktop |
| `.\mvnw.cmd clean verify` | Exit 0; 64 unit tests and 44 integration tests, 108 total; zero failures, errors or skips; elapsed 1 minute 4 seconds |
| `openspec validate --all --strict --no-interactive` | All 14 items passed |
| `openspec doctor` | Passed |
| `git diff --check` | Passed |

Main synchronized the approved delta into `openspec/specs/repository-conventions/spec.md`: one modified requirement, Executable agent guidance, and one added requirement, GPT-6 agent model routing. Existing scenario names were preserved. After synchronization, strict specification validation passed for all 11 specs, all-item validation passed for all 14 items, and `.\mvnw.cmd -Dtest=RepositoryConventionsTest test` passed with 22 tests and zero failures, errors or skips.

An initial unprefixed targeted `mvnw.cmd` invocation failed PowerShell command resolution; the corrected `.\mvnw.cmd` invocation passed. That shell invocation error was not a test failure. The earlier Docker availability issue is resolved.

## Pending completion

Task 4.3 remains unchecked because archive awaits the user's pending choice. Specification synchronization and its verification are complete. No archive or final `DONE` is claimed by this record.
