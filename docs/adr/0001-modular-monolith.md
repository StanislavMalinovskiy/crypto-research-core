# ADR 0001: Spring Modulith modular monolith

- Status: Accepted for deployment shape; module topology superseded by [ADR 0008](0008-six-module-mvp-topology.md)
- Date: 2026-09-13

## Context

The research platform needs strong business boundaries and one operationally simple deployment while hypotheses and data requirements are still being validated.

## Decision

Use one repository, one Maven module, one Spring Boot application, one deployable JAR and one PostgreSQL database. The original bootstrap defined eight vertical package-based modules with Spring Modulith descriptors and automated `ApplicationModules.verify()` checks. [ADR 0008](0008-six-module-mvp-topology.md) supersedes only that initial topology; the modular-monolith deployment decision remains accepted.

## Consequences

Modules own their domain, use cases, persistence, providers, migrations and tests. Cross-module calls use named public interfaces. Physical separation is deferred until independent scaling, isolation or deployment is a measured requirement.
