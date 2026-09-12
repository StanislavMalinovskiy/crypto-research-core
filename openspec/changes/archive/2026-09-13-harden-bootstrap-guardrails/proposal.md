## Why

The bootstrap implementation is sound, but active planning documents still mix the verified runtime baseline with target MVP infrastructure and historical architecture. The build also enables Java preview without an implemented use case and needs enforceable protection against legacy ORM coordinates before business development begins.

## What Changes

- Separate current, target and deferred technology statements in the Roadmap and Tech Stack, correct the package root and replace transient README change references with durable navigation.
- Make idempotency an acceptance criterion of every persistence change and place the storage foundation before provider ingestion in the planned sequence.
- Move the legacy v5 module mapping out of the active Glossary and locally label any retained historical or deferred database terms.
- Replace the premature preview-enabled lifecycle with a stable Java 25 baseline for all production and test sources while retaining exact module API-root verification.
- Supersede the preview ADR, document that preview requires a future evidence-backed change, and remove preview runtime guidance.
- Remove the unused validation starter from the production dependency baseline.
- Extend transitive Maven Enforcer exclusions to legacy JPA and Hibernate coordinates without blocking Bean Validation providers as a category.

Non-goals:

- No business functionality, provider integration, database tables or migrations.
- No change to the eight-module dependency DAG, named interfaces, runtime roles, transaction policy or production architecture.
- No new production dependency, deployable, cache, broker, database or observability infrastructure. One superseding ADR records the Java baseline correction.
- No Git staging, commit or push during implementation; repository checkpointing is a separate user-authorized operation.

Affected application modules: none. The change operates on repository-wide documentation, build configuration and architecture tests only.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: strengthen active-document consistency, legacy terminology separation and transitive forbidden-dependency enforcement.
- `build-baseline`: replace the unused preview-enabled lifecycle with a stable Java 25 lifecycle and keep the production dependency baseline minimal.

## Impact

- Documentation: `README.md`, `docs/ROADMAP.md`, `docs/TECH_STACK.md`, `docs/GLOSSARY.md` and a historical document under `docs/archive/`.
- Build: `pom.xml` removes one unused starter and expands existing Enforcer patterns.
- Tests: repository convention coverage keeps portable, exact API-root discovery while the normal compiler becomes the repository-wide preview guard.
- OpenSpec: two delta specs update existing main capabilities; no runtime API, module boundary or database behavior changes.
