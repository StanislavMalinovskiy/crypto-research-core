## Why

The project now needs to connect from multiple workstations to one persistent external PostgreSQL instance without copying credentials into tracked configuration or silently using the local Compose defaults. At the current research stage, the operator has chosen one database identity for both application access and Flyway to keep workstation and server setup simple.

## What Changes

- Add an explicit `managed` runtime profile that imports one required workstation-local secrets file outside the packaged application.
- Add a tracked placeholder-only example and create an ignored local file for the operator to fill with one external JDBC endpoint, username and password shared by the datasource and Flyway.
- Keep the default local Compose workflow unchanged when the `managed` profile is not active.
- Fail startup when the `managed` profile is selected but its secrets file or mandatory settings are absent.
- Document startup, TLS expectations, credential rotation and verification without recording real infrastructure values.
- Add repository checks that prove the real secrets file is ignored and that no credential value is committed.
- Do not connect automated tests to the shared database or place the previously disclosed password anywhere in the repository.

Non-goals are provisioning or administering PostgreSQL, rotating the already disclosed credential, configuring server firewall/backup/TLS infrastructure, deploying the application, adding a provider, changing schemas or migrations, and completing the whole Stage 3.0 infrastructure gate.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `database-bootstrap`: distinguish the portable local database defaults from an explicitly selected external-database profile and require one ignored secret-bearing connection configuration shared by the datasource and Flyway.

## Impact

- Affected repository surfaces: Spring Boot configuration, `.gitignore`, a tracked secrets template, one ignored operator-owned secrets file, configuration/contract tests, and operating documentation.
- Affected modules: none; this is composition-root and repository operations work only.
- Public Java APIs, module boundaries, dependency directions, database objects and Flyway migrations remain unchanged.
- No production dependency or additional infrastructure component is introduced.
