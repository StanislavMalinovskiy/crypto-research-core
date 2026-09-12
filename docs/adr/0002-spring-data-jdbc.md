# ADR 0002: Spring Data JDBC and JdbcClient

- Status: Accepted
- Date: 2026-09-13

## Context

The system is data-intensive, needs explicit SQL behavior and must preserve clear aggregate and module ownership without ORM session semantics.

## Decision

Use Spring Data JDBC and `JdbcClient` with PostgreSQL. Flyway exclusively owns schema evolution. Do not use JPA/Hibernate, R2DBC or automatic schema mutation.

## Consequences

Persistence code and mappings remain inside the owning business module. SQL behavior is explicit and testable against PostgreSQL with Testcontainers. New indexes require a real query pattern and migrations remain forward-only.
