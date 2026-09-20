## Context

See `proposal.md` for motivation. The repository already has an explicit `managed` profile, one datasource identity inherited by Flyway, a required ignored secrets file, PostgreSQL/TLS URL validation and database-backed readiness. The accepted specs already cover this behavior, while `docs/DELIVERY_PLAN.md` still keeps Stage 3.0 open because no production-like operational evidence has been recorded.

The shared database is external mutable state. Starting the packaged application may apply pending V1-V4 migrations, but must not load business data. The user/operator exclusively owns VDS administration, credentials, network-access policy and backup/restore operations. The ignored secrets file remains opaque to agents and automated tests.

## Goals / Non-Goals

**Goals:**

- Prove that the exact packaged JAR starts through the `managed` profile, completes Flyway startup and reports bounded `readiness=UP` against the shared database.
- Combine that repository-side smoke evidence with dated, non-secret operator evidence for PostgreSQL 18.6, TLS, grants, automatic backups and a successful restore drill.
- Leave a concise operational acceptance record and move Stage 3 from database preparation to provider selection only after every acceptance item is present.
- Preserve the normal Maven/CI boundary: all automated database tests use isolated Testcontainers and never consume workstation secrets.

**Non-Goals:**

- Administering the VDS, changing roles, network-access policy, TLS certificates, backup jobs or credentials.
- Evaluating or approving the operator's chosen public/private database access model; that security decision remains outside this stage-exit check.
- Reading, printing, copying, parsing or validating the content of `config/application-managed-secrets.properties` outside Spring Boot startup.
- Adding scripts, endpoints, production code, migrations, dependencies or tests solely to inspect the shared database.
- Loading provider or research data, deploying a continuously running application instance, or selecting a Solana provider.
- Replacing recurring operational monitoring; this is a dated stage-exit acceptance check.

## Decisions

### 1. Reuse the packaged application as the database smoke probe

After the normal clean gate builds the JAR, Developer starts that JAR from the repository root with only the `managed` profile and a dedicated local HTTP port. Spring Boot reads the fixed ignored file, Flyway validates or applies pending migrations through the configured datasource, and a bounded poll checks `/actuator/health/readiness` for `UP`. The process is always stopped in cleanup.

This is preferred over `psql`, a custom verification endpoint or a new repository script because it exercises the real deployable and existing startup contract without another credential path or dependency. Startup must not invoke providers or insert business observations.

### 2. Keep secrets opaque throughout evidence collection

No agent command opens the populated file or exports its values. Commands, captured evidence and documentation contain no JDBC URL, host, username, password or certificate path. The only permissible repository check on the populated path is metadata-level confirmation that Git ignores it; application startup itself may consume it through Spring configuration.

This is preferred over parsing the file in PowerShell because a second secret-handling path creates avoidable disclosure risk.

### 3. Split acceptance between executable and operator-owned evidence

Developer supplies executable evidence for the clean local gate, packaged-JAR startup, Flyway completion inferred from successful startup, and bounded readiness. The user/operator supplies a dated non-secret statement confirming:

- exact PostgreSQL server version 18.6;
- the shared identity can complete current Flyway migrations and normal application access;
- automatic backups have a stated retention policy; and
- one backup was restored into a disposable target and checked successfully.

Control accepts the combined evidence, updates final documentation and archives the change. This is preferred over having an agent inspect or change server configuration because the user explicitly retains that security boundary.

### 4. Do not mark Stage 3.0 complete on partial evidence

`docs/DELIVERY_PLAN.md` remains `Current` until both evidence sets are complete. Once accepted, Control records the verification date and non-secret conclusions in `docs/OPERATIONS.md`, marks 3.0 `Done`, changes 3.1 to `Current`, and updates the plan's current/next narrative. Missing backup or restore evidence is a blocker, not a documentation caveat.

### 5. Preserve module and transaction boundaries

No logical module is affected and no new transaction is introduced. Flyway remains the sole schema owner and uses its existing startup transaction behavior. The smoke run performs no provider I/O and no application-owned business write; only pending versioned Flyway migrations may mutate the shared database.

## Risks / Trade-offs

- [The first managed startup applies forward-only migrations to shared state] → Run the complete isolated gate first, use only the accepted packaged JAR, verify the operator has a usable backup, and never invoke Flyway clean or ad-hoc rollback.
- [A successful point-in-time check can become stale] → Record the date and treat backups and restore readiness as recurring operator duties rather than permanent guarantees.
- [Application startup output could contain connection metadata] → Do not publish raw logs; report only sanitized outcomes and never include the JDBC URL or credentials in evidence.
- [Readiness proves connectivity, not backup correctness] → Require separate operator evidence before changing the delivery status.
- [One shared identity retains DDL authority at runtime] → Accept the documented Stage 3 trade-off; role separation remains a future decision and is not introduced here.

## Migration Plan

1. Run the required local gate with `managed` inactive so Testcontainers remains the only automated database target.
2. Confirm at metadata level that the populated secrets path is ignored, without reading it.
3. Start the packaged JAR with `managed` on a dedicated local port, poll readiness with a finite timeout, record sanitized success/failure and stop the process in all cases.
4. If startup fails, stop without cleaning or editing the shared database; diagnose from sanitized errors and keep Stage 3.0 `Current`.
5. Obtain the dated operator evidence. Restore testing uses a disposable target and never overwrites the live database.
6. After Control review, update final operations and delivery documentation, rerun documentation/OpenSpec checks and archive the change.

Rollback is documentary: if any evidence is withdrawn or invalidated before archive, leave 3.0 `Current`. Applied Flyway migrations are not rolled back by this change.
