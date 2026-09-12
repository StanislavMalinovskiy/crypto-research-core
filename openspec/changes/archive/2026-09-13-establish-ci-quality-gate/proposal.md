## Why

The repository guardrails currently run only when a developer invokes them locally, and the Maven Wrapper download is not checksum-verified. Before business changes begin, pull requests need one reproducible CI gate that exercises the same Java, Docker/PostgreSQL and OpenSpec contracts as local verification.

## What Changes

- Add one GitHub Actions quality-gate workflow for pushes and pull requests using Java 25, the Maven Wrapper and Docker-backed `clean verify`.
- Run strict OpenSpec validation and doctor checks in the same required job.
- Publish Surefire and Failsafe reports even when a test fails.
- Add the Maven 3.9.16 distribution SHA-256 to Wrapper configuration and verify the wrapper remains the only Maven entry point.
- Pin the PostgreSQL Testcontainers image to an exact tested 16.x minor tag and document its controlled update rule.
- Record JDK, Maven, PostgreSQL, Spring Boot, Spring Modulith, BOM override and container-image upgrade policies.
- Complete two repository-hardening carry-overs: remove the obsolete `StructuredTaskScope` baseline claim from the Roadmap, and remove the current `venue-gate` concept from the active Glossary while extending the legacy-term guard.
- Document the external GitHub branch-protection step that makes the stable `quality-gate` job name required; repository files cannot enforce that setting alone.

Non-goals:

- No business functionality, module-boundary change, provider integration, database schema or production dependency.
- No deployment, release, container-image build, coverage threshold, static-analysis platform or third-party CI service.
- No mutation of GitHub repository settings or secrets from this change.
- No Git staging, commit or push.

Affected application modules: none. This change affects repository automation, build integrity, test infrastructure and engineering documentation only.

## Capabilities

### New Capabilities

- `ci-quality-gate`: reproducible GitHub pull-request and push verification with durable test evidence and a stable required-check identity.

### Modified Capabilities

- `build-baseline`: checksum-verify the pinned Maven distribution and define controlled runtime/framework/container upgrade rules.

## Impact

- Automation: a workflow under `.github/workflows/` running the complete Maven and OpenSpec gates.
- Wrapper: `.mvn/wrapper/maven-wrapper.properties` gains the verified distribution checksum.
- Test infrastructure: PostgreSQL image reference changes from a floating major tag to the currently verified minor tag.
- Documentation: `AGENTS.md`, `README.md` and `docs/TECH_STACK.md` describe CI usage, supported versions and the external branch-protection handoff; the Roadmap and Glossary no longer present superseded preview or venue-gate details as current baseline.
- OpenSpec: one new capability and one build-baseline delta; production runtime behavior remains unchanged.
