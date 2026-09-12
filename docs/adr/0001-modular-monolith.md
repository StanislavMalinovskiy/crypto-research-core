# ADR 0001: Spring Modulith modular monolith

- Status: Accepted
- Date: 2026-09-13

## Context

The research platform needs strong business boundaries and one operationally simple deployment while hypotheses and data requirements are still being validated.

## Decision

Use one repository, one Maven module, one Spring Boot application, one deployable JAR and one PostgreSQL database. Define the eight vertical package-based modules in [Architecture](../ARCHITECTURE.md) with Spring Modulith descriptors and automated `ApplicationModules.verify()` checks.

## Consequences

Modules own their domain, use cases, persistence, providers, migrations and tests. Cross-module calls use named public interfaces. Physical separation is deferred until independent scaling, isolation or deployment is a measured requirement.
