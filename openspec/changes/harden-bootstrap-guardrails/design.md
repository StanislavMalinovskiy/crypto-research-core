## Context

See [proposal.md](proposal.md) for motivation. The verified bootstrap already provides one Java 25 Spring Modulith application, eight named module APIs, exact dependency-map verification, PostgreSQL/Testcontainers integration coverage and archived main specifications. The remaining inconsistencies are repository-wide: planning documents describe some target components as current, the API preview check depends on Windows path syntax and two text markers, and Maven does not exclude legacy ORM coordinates.

This change affects no application module. Data ownership, transaction boundaries, the module DAG and runtime behavior remain governed by the accepted ADRs and `docs/ARCHITECTURE.md`; no table, transaction, event or provider adapter is introduced.

## Goals / Non-Goals

**Goals:**

- Make current, target and deferred statements unambiguous across active planning documents.
- Turn the public API preview boundary into a real Java 25 compilation contract.
- Keep the bootstrap production dependency graph minimal and strengthen transitive ORM exclusions.
- Remove active legacy architecture terminology without losing historical information.
- Leave a verified OpenSpec baseline ready for `establish-chain-identity-kernel`.

**Non-Goals:**

- Reconsidering module dependencies, PostgreSQL schema ownership, runtime roles or event semantics.
- Selecting providers, creating partitioned tables or provisioning observability/cache infrastructure.
- Implementing chain identities, ingestion, normalization, replay or governance gates.
- Staging or committing the worktree.

## Decisions

### 1. Classify planning statements by lifecycle state

The Roadmap and Tech Stack will use three explicit meanings:

- **Current baseline**: present in the repository and verified by the current Maven/OpenSpec lifecycle.
- **Target MVP**: planned capability or infrastructure that still requires an approved OpenSpec change and its acceptance evidence.
- **Deferred**: optional work that requires a demonstrated need and is not part of current delivery.

Activation gates are proportional rather than universal:

- A cache, broker, additional database, deployable, external observability platform or general partitioning-policy change requires measured need, an approved OpenSpec change and an ADR when it changes the architectural baseline.
- A concrete provider requires coverage, limits and terms validation, an OpenSpec design, and explicit approval for any new production dependency. An ADR is required only when the provider boundary or general architecture changes.
- Partitioning a concrete high-volume table belongs to the owning module migration and requires volume, retention and query-pattern evidence. An ADR is required only when the general persistence strategy changes.

This avoids both treating future infrastructure as installed and demanding an ADR for every ordinary adapter or table. The alternative, one global activation formula, was rejected because its ceremony is disproportionate for local module decisions.

### 2. Make Roadmap sequencing persistence-safe

The Roadmap will distinguish the completed technical bootstrap from target MVP phases. Its immediate change sequence will be:

1. `harden-bootstrap-guardrails`
2. `establish-chain-identity-kernel`
3. `establish-idempotent-marketdata-storage`
4. `add-solana-marketdata-ingestion`
5. `normalize-and-store-solana-swaps`
6. `add-marketdata-replay-and-gap-recovery`

Idempotency is also a mandatory acceptance criterion for every change that writes or mutates persisted data. It is not a late standalone repair. Provider selection remains inside its owning change and does not become part of this hardening change.

### 3. Keep README navigation durable

README will point readers to `openspec/changes/` for active changes, to the dated bootstrap archive for completed bootstrap evidence, and to the next planned business change. It will not name the hardening change as currently active, so archiving does not immediately stale the document.

### 4. Verify public APIs through the Java compiler

One test compilation task will include every `api/**/*.java` source below exactly these roots: `kernel`, `governance`, `marketdata`, `risk`, `wallet`, `strategy`, `measurement` and `research`. Source discovery will compare normalized `Path` components rather than separators.

The compiler task will use the running JDK compiler with:

- `--release 25`;
- `-proc:none`;
- the test runtime classpath;
- a dedicated `@TempDir` output directory;
- no `--enable-preview` option.

Failures will report source, line, column, diagnostic code and localized message. Harness tests will compile a synthetic stable record successfully and reject a synthetic public contract exposing `StructuredTaskScope`. The existing marker scan remains only a fast additional check and becomes path-separator independent.

Compiling the complete project a second time without preview was rejected: internal implementation is explicitly allowed to use preview features, so that would enforce the wrong boundary. Marker scanning alone was rejected because any finite name list can miss new preview APIs and previously produced a vacuous green result on Linux.

### 5. Harden the existing Maven dependency policy

The unused `spring-boot-starter-validation` dependency will be removed. Maven Enforcer keeps `searchTransitive=true` and adds `javax.persistence:*` and `org.hibernate:*` alongside existing Jakarta and modern Hibernate exclusions. The latter group does not match `org.hibernate.validator`, so a later approved Bean Validation use remains possible.

No Maven Invoker fixture or intentionally broken POM will be added. The configured patterns, successful Enforcer execution and resolved dependency tree provide proportionate evidence for this repository guardrail.

### 6. Archive legacy terminology with local status labels

The v5 layer-to-module mapping will move intact to a clearly non-normative file in `docs/archive/`. Active Glossary architecture text will describe the eight current Spring Modulith modules. Terms such as `paper_trades`, `paper_fills`, `owner_clusters` and `venue_policy_decisions` may remain only where their individual entry or section says historical or deferred; an opening disclaimer alone is insufficient.

## Risks / Trade-offs

- **[Compiler diagnostics vary slightly across JDK vendors]** → Assert compilation outcome and presence of actionable diagnostics, not an exact full message string.
- **[The synthetic negative fixture could fail for a reason other than preview status]** → Use a valid Java 25 public signature whose only forbidden property is the preview API and assert a preview-related diagnostic.
- **[Static terminology checks can create false positives in historical references]** → Exclude `docs/archive/` from current-architecture assertions and permit locally marked historical/deferred entries.
- **[Roadmap edits could erase useful product detail]** → Reclassify and relocate content instead of mass rewriting; preserve future targets as target or deferred material.
- **[Docker availability can change between runs]** → Record the exact full verification result; do not archive if the current required Maven lifecycle does not complete successfully.

## Migration Plan

1. Update active documentation and move the legacy mapping into the archive while preserving relative links.
2. Add compiler-based compatibility tests and make the marker scan portable.
3. Remove the unused dependency and extend existing Enforcer exclusions.
4. Run static documentation/dependency inspections, the full Maven lifecycle and strict OpenSpec validation.
5. Verify, sync and archive the change only after every required check succeeds.

Rollback is a normal source revert because this change creates no data migration or runtime state. Git staging and commit remain a separate user-authorized operation after showing worktree and `.agents` inventories.
