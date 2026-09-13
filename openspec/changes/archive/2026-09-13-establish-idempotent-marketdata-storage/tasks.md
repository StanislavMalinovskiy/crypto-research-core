## 1. First market-data migration

- [x] 1.1 Add `db/migration/marketdata/V1__create_marketdata_raw_chain_events.sql` with the `marketdata` schema, exact columns, composite primary key and non-null/length/hash/non-negative checks from the design; verify the migration contains no `IF NOT EXISTS`, partitioning or secondary speculative index.
- [x] 1.2 Update the startup integration assertion from zero migrations to exactly V1 applied with none pending, and verify the test also observes the `marketdata.raw_chain_events` table on PostgreSQL 18.6.

## 2. Raw observation model and application boundary

- [x] 2.1 Implement internal immutable market-data input and stored-evidence types using `kernel::api` identities; verify unit tests reject null, blank, oversized, control-character and cross-chain-inconsistent evidence.
- [x] 2.2 Implement exact UTF-8 SHA-256 fingerprinting with the `sha256:` prefix and deterministic `Instant` truncation to microseconds; verify positive and boundary unit tests without Spring or Docker.
- [x] 2.3 Implement an internal persistence port plus a constructor-injected, `@Transactional` application use case with `INSERTED`/`ALREADY_PRESENT` outcomes and an explicit conflict failure; verify focused unit tests cover delegation, first-ingestion time from an injected fixed `Clock` and error propagation.
- [x] 2.4 Implement the schema-qualified `JdbcClient` adapter using insert-first `ON CONFLICT DO NOTHING RETURNING` followed by complete-key read and immutable-evidence comparison; verify no Spring Data aggregate repository, cross-module SQL or caller-supplied digest is introduced.

## 3. PostgreSQL behavior verification

- [x] 3.1 Add a market-data PostgreSQL integration test that starts through Flyway and verifies table ownership, column types, primary-key order and check constraints on the exact `postgres:18.6-alpine` image.
- [x] 3.2 Verify first insert and exact readback of identity, block/hash, nullable source time, observation time, raw payload, derived digest, parser version and ingestion time.
- [x] 3.3 Verify equal retry returns `ALREADY_PRESENT`, keeps one row and preserves the original observation/ingestion times, while the same chain event from another provider creates a distinct row.
- [x] 3.4 Verify every immutable-evidence difference produces an explicit conflict and leaves the first row unchanged; also exercise direct SQL constraint rejection for invalid hash format and negative block position.
- [x] 3.5 Verify concurrent equal submissions from separate transactions converge on one stored row and deterministic success outcomes without an application-level lock.

## 4. Active documentation and specification context

- [x] 4.1 Update `docs/modules/marketdata.md`, `docs/ARCHITECTURE.md`, `docs/TECH_STACK.md`, `docs/TESTING.md`, `docs/PROJECT_SUMMARY.md`, `docs/ROADMAP.md` and durable README navigation to describe the implemented raw-storage boundary without presenting normalization, provider ingestion, partitioning or secondary indexes as current behavior.
- [x] 4.2 After implementation is verified, revise the main `database-bootstrap` Purpose so it describes the ongoing Flyway/PostgreSQL contract rather than claiming that business tables have not yet been introduced; verify the purpose remains consistent with this change's delta before spec sync.
- [x] 4.3 Run repository convention tests and `git diff --check`; verify tracked Markdown links/export hygiene, module boundaries and the six-module exact dependency map remain green.

## 5. Acceptance gates

- [x] 5.1 Run `mvnw.cmd -DskipITs clean verify`; verify all unit, architecture and repository-convention tests pass and the executable JAR is created.
- [x] 5.2 Run `mvnw.cmd clean verify` with Docker; verify Flyway V1, all raw-storage Testcontainers scenarios, health/readiness integration tests and PostgreSQL 18.6 pass.
- [x] 5.3 Run `mvnw.cmd dependency:tree`; verify no new production dependency and none of the forbidden JPA/Hibernate ORM, WebFlux, Reactor, R2DBC, Vert.x or Lombok artifacts are present.
- [x] 5.4 Run `openspec validate establish-idempotent-marketdata-storage --strict --no-interactive`, `openspec validate --all --strict --no-interactive` and `openspec doctor`; verify every check passes before marking all implementation tasks complete.

## 6. Verification review corrections

- [x] 6.1 Enforce the documented limits as UTF-8 byte budgets in Java and SQL, and verify PostgreSQL accepts maximum-width ASCII and multibyte composite keys while rejecting a value one byte over budget.
- [x] 6.2 Split the oversized raw-storage integration test into focused schema and persistence test classes, keep every Java class below 200 lines, and verify scenario coverage is unchanged.
- [x] 6.3 Re-run `mvnw.cmd -DskipITs clean verify`, `mvnw.cmd clean verify`, strict OpenSpec validation, OpenSpec doctor and `git diff --check`; mark complete only when every command succeeds with Docker available.

## 7. Network identity and evidence-contract corrections

- [x] 7.1 Replace logical chain-slug validation with exact, case-sensitive customary CAIP-2 validation, add `SOLANA_MAINNET`, expose the event component as `locator`, and verify focused kernel tests cover Solana/Base network distinction, reference case sensitivity and malformed/non-qualified rejection.
- [x] 7.2 Rename the V1 and JDBC physical contract to `chain_id`, `transaction_value`, `event_locator`, `observed_block_position` and `observed_block_hash`; update the key byte budget and strengthen the direct-SQL payload whitespace constraint, then verify schema metadata contains no ambiguous legacy columns.
- [x] 7.3 Extend PostgreSQL integration coverage to accept valid CAIP-2 identifiers, reject malformed/non-network-qualified identifiers and persist equal local transaction/event/provider values separately on Solana Mainnet and Base Mainnet.
- [x] 7.4 Update active kernel/market-data/architecture/Roadmap/specification documentation with the stable-inclusion admission gate, parser-independent locator invariant, migration rule and canonical EVM `receipt.logs` ordinal; verify no current document presents storage as a finality verifier or log-filter index as event identity.
- [x] 7.5 Run `mvnw.cmd -DskipITs clean verify`, `mvnw.cmd clean verify`, `mvnw.cmd dependency:tree`, strict change/all OpenSpec validation, OpenSpec doctor and `git diff --check`; mark complete only when every gate succeeds with PostgreSQL 18.6.

## 8. Final verification-review clarifications

- [x] 8.1 Document why time-range partitioning cannot preserve the current global raw-observation identity without a separate deduplication model; correct the Roadmap's future PostgreSQL partitioning notation to use `chain_id` and descriptive subpartition wording; clarify the shared/non-disposable V1 freeze point and the locator behavior actually covered by current unit tests.
- [x] 8.2 Verify that this clarification changes no Java, SQL or dependency contract; run `mvnw.cmd -DskipITs clean verify`, `mvnw.cmd clean verify`, `mvnw.cmd dependency:tree`, strict change/all OpenSpec validation, OpenSpec doctor and `git diff --check`, and mark complete only when every gate succeeds with PostgreSQL 18.6.
