## Context

See [proposal.md](proposal.md) for motivation. The repository has a complete local Maven/OpenSpec verification lifecycle and a GitHub remote, but no `.github/workflows/` directory. Maven 3.9.16 is URL-pinned without `distributionSha256Sum`, and the integration test currently uses the floating `postgres:16-alpine` tag.

This change affects no application module, module dependency, transaction, database schema or runtime endpoint. Docker is already required by the PostgreSQL integration test; CI adds no production infrastructure.

## Goals / Non-Goals

**Goals:**

- Make the local Definition of Done executable for pull requests and pushed commits.
- Verify the Maven distribution before execution and make test-container updates deliberate.
- Preserve useful test evidence when verification fails.
- Give branch protection a durable, documented check identity.

**Non-Goals:**

- Adding release, deployment, image publishing, coverage thresholds or external analysis services.
- Replacing the Maven Wrapper or adding a second build entry point.
- Changing production dependencies or application behavior.
- Mutating GitHub branch protection from repository code.

## Decisions

### 1. Use one GitHub Actions job as the merge-gate identity

Create `.github/workflows/quality-gate.yml` with a single job whose identifier and display name are both stable and recognizable as `quality-gate`. Trigger it for pull requests and pushes to prevent branch-specific logic from drifting. Add manual dispatch only as a diagnostic convenience if it does not change required behavior.

Use a GitHub-hosted Ubuntu runner because it provides a Docker daemon suitable for Testcontainers. Grant only read access to repository contents, set a finite timeout and use concurrency cancellation for superseded runs on the same ref.

Splitting Maven and OpenSpec into independent required jobs was rejected for the initial repository because it creates multiple branch-protection identities and duplicates setup. Matrix builds were rejected until more than one supported JDK or operating system has a measured compatibility need.

### 2. Use repository-owned verification commands

The job uses Java 25, Node.js 22 and the checked-in Maven Wrapper. It runs:

1. `./mvnw clean verify` with the runner's Docker daemon;
2. `openspec validate --all --strict --no-interactive`;
3. `openspec doctor`.

Install the OpenSpec CLI at the currently generated-skill version (`1.13.0`) rather than `latest`, so CI behavior changes only through a reviewed repository change. Maven dependency caching may use the official Java setup action, but a cache hit never substitutes for verification.

Official GitHub actions are pinned to immutable commit SHAs with a nearby release-version comment. Floating action tags were rejected because they weaken workflow reproducibility and supply-chain review.

### 3. Publish reports without masking failures

The report-upload step runs with an always condition and collects `target/surefire-reports/**` and `target/failsafe-reports/**` with a finite retention period. Absence of files produces a warning rather than replacing the primary command's conclusion. OpenSpec checks also run after a Maven failure so one run exposes both categories of defects, while the job remains failed if either command failed.

### 4. Verify the Maven distribution

Add the lowercase SHA-256 of `apache-maven-3.9.16-bin.zip` to `maven-wrapper.properties` as `distributionSha256Sum`. The Wrapper uses `only-script`, so no wrapper JAR is downloaded and `wrapperSha256Sum` is not applicable. The value must be calculated from or compared with the official Apache-hosted artifact before it is committed.

### 5. Define supported-version and upgrade policy

- Java: exactly major 25, enforced as `[25,26)`; adopt a new major through an approved change.
- Maven: exact Wrapper distribution 3.9.16 with SHA-256; update URL and checksum atomically.
- Spring Boot: exact parent version; Spring Framework/Data/Flyway versions remain BOM-managed unless a documented compatibility or security exception is approved.
- Spring Modulith: exact imported BOM version compatible with the Boot baseline.
- PostgreSQL: major 16 runtime compatibility; integration tests use exact `postgres:16.15-alpine` until a reviewed patch update passes full verification.
- OpenSpec: CI pins 1.13.0 and generated skills remain CLI-generated.

Container digest pinning was rejected for now because an exact official minor tag is sufficient for the current multi-platform developer workflow. A digest may be added later when platform selection and image-update automation are explicit.

### 6. Treat branch protection as an external activation step

README and agent guidance name the `quality-gate` check and explain that a repository administrator must require it in the primary-branch ruleset after the workflow has run at least once. The change does not claim that repository files can enforce GitHub settings and does not use credentials to mutate them.

Repository implementation and local verification complete before any checkpoint or remote action. A single separately authorized checkpoint may then be pushed, the first remote `quality-gate` run must succeed, and only then can an administrator select that observed check for primary-branch protection. Archiving the OpenSpec change proves repository readiness; it does not prove that the remote run or branch-protection setting exists.

### 7. Complete repository-hardening carry-overs in this change

The Roadmap still names `StructuredTaskScope` as if the stable Java 25 baseline used it, although preview was removed by the archived hardening change. Remove that statement without replacing it with another speculative concurrency mechanism; the accepted rule remains bounded virtual-thread execution using stable APIs.

The active Glossary still defines `Venue Gate` and its implementation-oriented collaborators as current concepts. Remove that current architecture presentation, retain only locally marked historical/deferred terminology where useful, and add `venue-gate` to the repository legacy-term guard so it cannot silently return to active documentation. Future execution governance requires its own approved change.

## Risks / Trade-offs

- **[GitHub action SHAs become stale]** → Keep the human-readable release version beside each SHA and update both through a reviewed maintenance change.
- **[Docker or registry outage makes CI unavailable]** → Fail visibly; do not bypass PostgreSQL verification or introduce an H2 fallback.
- **[First branch-protection setup cannot select an unseen check]** → Run the workflow once after a separately authorized push, then configure the documented stable job identity.
- **[Exact PostgreSQL tag ages]** → Review patch updates deliberately and require Flyway/startup verification before merging.
- **[CI cannot prove an external ruleset is enabled]** → Report branch-protection activation as a repository-administration handoff, not as completed source work.

## Migration Plan

1. Pin and verify the Maven distribution checksum and PostgreSQL test image.
2. Add version/upgrade and CI operating guidance.
3. Add the workflow and lightweight repository checks for its durable contract.
4. Run the full local Maven and OpenSpec lifecycle.
5. Complete and archive the repository change after local verification, without staging, committing or pushing as part of apply.
6. After separate authorization, create one coherent Git checkpoint and push it.
7. Observe one successful remote `quality-gate` run.
8. Configure the observed stable check as required primary-branch protection through an administrator action.

Rollback removes the workflow and documentation additions and restores the previous wrapper/image references. No production state or database migration is involved.
