## Why

The managed PostgreSQL profile is implemented, but Stage 3.0 still lacks evidence that the application can use the operator-provisioned database and that recoverable storage is ready before real Solana ingestion. Closing that operational gap now prevents provider work from being built on an unverified persistent store.

## What Changes

- Run the packaged application with the explicit `managed` profile and the operator-owned ignored secrets file, then confirm successful Flyway validation/application and `readiness=UP` without reading, printing or copying secret values.
- Record non-secret acceptance evidence for the supported PostgreSQL version, required grants, automated backup policy and one successful restore drill; the user/operator remains solely responsible for server administration and supplies that evidence.
- Keep Maven and CI isolated on Testcontainers; no automated test or agent command may connect to the shared database.
- Update the operational documentation and Delivery Plan only after all repository and operator evidence is complete, marking Stage 3.0 `Done` and provider selection 3.1 `Current`.
- Do not change production code, database schema, module boundaries, dependencies, database credentials or server configuration.

## Capabilities

### New Capabilities

None. This change verifies and records an already accepted runtime contract.

### Modified Capabilities

None. The accepted `database-bootstrap` and `operational-health` requirements already define managed startup, Flyway ownership and database-backed readiness. This change therefore opts out of delta specs.

## Impact

- Affected repository areas: `docs/OPERATIONS.md`, `docs/DELIVERY_PLAN.md` and this OpenSpec change.
- Affected systems: the existing packaged application and the operator-provisioned shared PostgreSQL instance are exercised once for acceptance.
- Affected modules: none; no module API, persistence owner or runtime boundary changes.
- Dependencies and architecture: unchanged.
- External responsibility: PostgreSQL version, grants, backup retention and restore evidence are supplied by the user/operator; agents do not administer the VDS or inspect secret values. Network-access policy remains an operator-owned decision outside this change's acceptance criteria.
