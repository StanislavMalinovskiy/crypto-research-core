## Context

The accepted `repository-conventions` requirement and `RepositoryConventionsTest` pin `gpt-6-sol`. Current Codex defaults and four Sol role files use the same ID. Owner-staged Claude integration in `AGENTS.md` and `docs/AGENT_WORKFLOW_MULTIAGENT.md` must be preserved.

## Goals / Non-Goals

**Goals:** Keep active Codex configuration, guidance, tests and the routing contract consistent with `gpt-6.1-sol` at existing efforts.

**Non-Goals:** Change task routing, effort, Luna or Claude models, workflow mode, other settings, historical records, or application behavior.

## Decisions

- Replace only current Sol model IDs and the four Sol role description labels. Keep requirement and scenario names, all other requirement clauses, and the active staged Claude text. This is a narrow migration of an accepted model mapping.
- In `RepositoryConventionsTest`, first change only expected Sol IDs and matching failure text; run the named convention test to establish behavioral RED against old configuration. Freeze those assertions, then update configuration and active guidance for GREEN.
- The owner explicitly authorized Main to perform this bounded task directly without subagents. The configured workflow switch remains unchanged. No application module owns these files, no transaction boundary is involved, and no production dependency or infrastructure is introduced.

## Scope assessment

CONTRACT, ROUTINE: the accepted model mapping changes. No TR-01 through TR-12 trigger applies: this changes agent model metadata, not persistence, transactions, concurrency, retry, migrations, point-in-time decisions, financial arithmetic, identity, provider recovery, module boundaries, research integrity or security. Existing effort and safeguards remain unchanged. The active `version-market-facts-and-split-evidence` change affects different capabilities; safe to proceed independently. Main performs direct implementation and review under the owner's task-specific authorization, without delegating or changing configured mode. Test mode is RED_REQUIRED for the changed executable routing expectation; existing assertions provide the coverage.

## Risks / Trade-offs

- Existing sessions may retain their starting model until restarted → verify the files and routing contract; the next session uses the configured model.
- Concurrent owner edits to staged guidance may be overwritten → edit the current working files narrowly and compare the preexisting index hunks before handoff.
- Literal test expectations may still mention the old ID → run the focused convention test and the complete required gate.
