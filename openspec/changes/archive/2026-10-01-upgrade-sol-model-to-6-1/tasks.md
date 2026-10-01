## 1. Model upgrade

- [x] 1.1 Update only Sol model expectations in RepositoryConventionsTest and establish a behavioral failure against the old configuration with the named routing test.
- [x] 1.2 Update active Sol configuration, role descriptions and guidance; verify the routing test passes and baseline comparison preserves all effort values and unrelated settings.

## 2. Verification and closure

- [x] 2.1 Review the bounded diff and run test-integrity preflight, Docker preflight, complete Maven verification, strict OpenSpec validation, doctor and diff checks. CLI archive and post-archive checks follow successful verification.

## Verification evidence

- Owner authorized Main to perform this task directly without subagents; the configured workflow switch was preserved.
- RED: `mvnw.cmd -Dtest=RepositoryConventionsTest#projectAgentConfigurationMatchesClosedRoutingProtocol test`, exit 1; the executed test expected no violations and observed six old-model violations. No compile or infrastructure failure.
- GREEN: `mvnw.cmd -Dtest=RepositoryConventionsTest test`, exit 0; 37 tests, no failures/errors/skips. Establishing expectations remained unchanged after RED.
- Full gate: integrity preflight, `docker version`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, `git diff --check`: exit 0. Maven ran 96 unit tests and 48 integration tests, no failures/errors/skips; strict validation passed 15/15 items.
- Direct diff review and comparison to pre-task working files confirmed model substitutions only in configuration/guidance; all efforts and Luna files were preserved. The real Git index remained unchanged, preserving owner-staged Claude integration.
