# ADR 0004: Module data ownership and PostgreSQL schemas

- Status: Accepted
- Date: 2026-09-13

## Context

Package boundaries do not prevent a module from querying another module's tables. A durable modular monolith needs an explicit physical naming and ownership policy before its first business migration.

## Decision

Every table, SQL statement, repository, row mapper and migration has exactly one owning module. Another module cannot access that table directly through SQL, joins, repositories or persistence entities.

Each module that owns durable business data uses a same-named PostgreSQL schema. `kernel` never owns a schema. `governance` receives one only when real governance state is introduced. Flyway owns all schema changes through one database-wide ordered history; migrations are grouped under `db/migration/<module>/` and introduce a module schema together with its first real object.

Modules exchange data through named public APIs, immutable projections or defined events. An analytical cross-module read model requires a separate ADR naming its owner, refresh semantics and permitted source access.

## Consequences

Ownership is visible in SQL names and migration layout without adding databases or roles. PostgreSQL schemas are namespace boundaries, not security isolation. Cross-module reporting requires an explicit projection or read-model decision rather than an accidental join. The bootstrap creates no empty schemas or placeholder migration.

## Alternatives considered

A single `public` schema is simpler initially but makes accidental cross-module SQL harder to detect. Separate databases or roles would add operational cost without providing value at the current stage.
