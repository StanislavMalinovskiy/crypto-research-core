## Why

The bootstrap implementation is sound, but active planning documents still mix the verified runtime baseline with target MVP infrastructure and historical architecture. The build also needs enforceable, platform-independent checks that public module APIs remain Java 25 stable and that legacy ORM coordinates cannot enter the transitive dependency graph before business development begins.

## What Changes

- Separate current, target and deferred technology statements in the Roadmap and Tech Stack, correct the package root and replace transient README change references with durable navigation.
- Make idempotency an acceptance criterion of every persistence change and place the storage foundation before provider ingestion in the planned sequence.
- Move the legacy v5 module mapping out of the active Glossary and locally label any retained historical or deferred database terms.
- Compile all eight module API source sets together on Java 25 without `--enable-preview`, with positive and negative tests proving the compiler harness detects preview leakage.
- Keep the normal Maven lifecycle preview-enabled while removing the unused validation starter from the production dependency baseline.
- Extend transitive Maven Enforcer exclusions to legacy JPA and Hibernate coordinates without blocking Bean Validation providers as a category.

Non-goals:

- No business functionality, provider integration, database tables or migrations.
- No change to the eight-module dependency DAG, named interfaces, runtime roles, transaction policy or production architecture.
- No new production dependency, ADR, deployable, cache, broker, database or observability infrastructure.
- No Git staging, commit or push.

Affected application modules: none. The change operates on repository-wide documentation, build configuration and architecture tests only.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-conventions`: strengthen active-document consistency, legacy terminology separation and transitive forbidden-dependency enforcement.
- `build-baseline`: make public API compatibility executable without preview and keep the production dependency baseline minimal.

## Impact

- Documentation: `README.md`, `docs/ROADMAP.md`, `docs/TECH_STACK.md`, `docs/GLOSSARY.md` and a historical document under `docs/archive/`.
- Build: `pom.xml` removes one unused starter and expands existing Enforcer patterns.
- Tests: repository convention coverage gains a Java compiler-based public API compatibility harness and portable API source discovery.
- OpenSpec: two delta specs update existing main capabilities; no runtime API, module boundary or database behavior changes.
