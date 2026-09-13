## MODIFIED Requirements

### Requirement: Flyway-owned schema evolution
Flyway SHALL be the only component authorized to mutate the database schema, and the first versioned migration SHALL introduce the `marketdata` schema together with its first real durable object.

#### Scenario: Empty bootstrap database
- **WHEN** the application starts against an empty supported PostgreSQL database
- **THEN** Flyway SHALL create the `marketdata` schema and raw-observation table through one versioned migration
- **AND** the table SHALL use the CAIP-2 network-qualified identity key `(chain_id, transaction_value, event_locator, provider)`
- **AND** no ORM or application startup code SHALL mutate the schema.

### Requirement: Real database verification
The Maven verification lifecycle SHALL validate database startup, applied migration state and raw-observation constraints against an isolated PostgreSQL Testcontainers instance.

#### Scenario: Integration verification
- **WHEN** `clean verify` runs with a working Docker engine
- **THEN** an isolated PostgreSQL 18.6 container SHALL start and apply the first versioned migration exactly once
- **AND** the application context SHALL connect successfully and confirm that Flyway has no pending migrations.
