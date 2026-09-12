# ADR 0007: Stable Java 25 baseline

- Status: Accepted
- Date: 2026-09-13
- Supersedes: [ADR 0003](0003-synchronous-java-25-baseline.md)

## Context

The bootstrap enabled Java preview compilation before any production use case required a preview feature. Virtual threads, which support the current blocking-I/O model, are stable in Java 25. Keeping preview enabled would add build, launch and JDK-upgrade obligations without implemented value.

## Decision

Use Java 25 without preview features across production compilation, test compilation, test execution and application launch. Keep the synchronous Spring MVC and JDBC programming model from ADR 0003. Use stable virtual threads for suitable blocking I/O and bounded platform threads for CPU-bound work.

A future preview feature requires an approved OpenSpec change and a superseding ADR. The decision must identify the exact JEP and API, demonstrate why stable Java is insufficient, keep preview types out of module contracts, document the required runtime flag and include migration verification for every JDK update.

## Consequences

The normal Maven lifecycle rejects preview usage anywhere in the repository, so a separate public-API compiler harness is unnecessary. Packaged applications run without `--enable-preview`. Preview cannot be introduced as a local implementation convenience or by silently changing Maven flags.

## Alternatives considered

Keeping preview enabled behind internal APIs preserves access to `StructuredTaskScope`, but the project does not use it and its API can change between JDK releases. A marker-based public API scan is weaker and more complex than compiling the complete project on the stable language and API surface.
