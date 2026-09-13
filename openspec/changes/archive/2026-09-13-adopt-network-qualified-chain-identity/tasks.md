## 1. Kernel identity conformance

- [x] 1.1 Inspect the existing `ChainId` implementation and retain or minimally correct it so exact case-sensitive customary CAIP-2 values are accepted without trim/lowercase normalization, `SOLANA_MAINNET` is the Solana constant, and no `ChainFamily` or closed network allow-list exists; verify focused unit tests accept Solana Mainnet, Ethereum Mainnet, Base Mainnet, Arbitrum One and an otherwise unknown valid CAIP-2 network while rejecting malformed and surrounding-whitespace input.
- [x] 1.2 Inspect `EventId` and retain its transaction-scoped opaque locator contract; verify focused unit tests preserve exact locators across equal transaction identities and documentation states that a future EVM adapter derives identity from the ordinal in the complete `receipt.logs` sequence rather than an RPC `logIndex` or filtered-result position.

## 2. Network-neutral PostgreSQL storage

- [x] 2.1 Inspect V1 and, only if needed, correct it in place to use `chain_id`, `transaction_value`, `event_locator`, `provider`, `observed_block_position` and `observed_block_hash` with primary key `(chain_id, transaction_value, event_locator, provider)`; verify schema integration tests prove the exact column names/order, CAIP-2 constraint, ordinary unpartitioned table shape, absence of a surrogate key/BRIN/secondary indexes and absence of V2.
- [x] 2.2 Inspect the raw-observation Java/JDBC path and retain or minimally correct its network-neutral binding; verify a stable synthetic `eip155:8453` observation stores and reads through the same application and persistence contract as a Solana observation without an EVM provider, parser or table branch.
- [x] 2.3 Verify PostgreSQL stores equal transaction/event/provider local values independently on Solana Mainnet and Base Mainnet, and stores the same chain event from two providers as two raw observations.
- [x] 2.4 Verify a duplicate submitted after the application clock advances remains one row with its original `ingested_at`; keep all existing equal-retry, conflicting-evidence and concurrent-submission tests green so `ingested_at`, finality and canonicality cannot weaken identity.

## 3. Active documentation

- [x] 3.1 Update only active architecture, Roadmap and `kernel`/`marketdata` module documentation where needed to record the representative CAIP-2 networks, open-network/no-`ChainFamily` policy, canonical EVM `receipt.logs` ordinal rule, parser-version locator stability and stable-input finality boundary; verify archived changes remain byte-for-byte untouched.
- [x] 3.2 Document that V1 remains unpartitioned because adding `ingested_at` to the primary key would break global idempotency, and that surrogate IDs, BRIN and secondary indexes require later evidence; verify active documentation does not present these deferred structures as current behavior.
- [x] 3.3 Run repository convention tests and `git diff --check`; verify Markdown links/export hygiene, six-module boundaries and the exact dependency DAG remain green.

## 4. Acceptance gates

- [x] 4.1 Run `mvnw.cmd -DskipITs clean verify`; verify all unit, architecture and repository-convention tests pass and the executable JAR is created.
- [x] 4.2 Run `mvnw.cmd clean verify` with Docker; verify PostgreSQL 18.6 applies exactly V1 and all schema, synthetic-EVM, multi-network, provider, idempotency, conflict, concurrency, health and readiness scenarios pass.
- [x] 4.3 Run `mvnw.cmd dependency:tree`; verify no new production dependency and none of the forbidden JPA/Hibernate ORM, WebFlux, Reactor, R2DBC, Vert.x or Lombok artifacts are present.
- [x] 4.4 Run `openspec validate adopt-network-qualified-chain-identity --strict --no-interactive`, `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`; mark the apply work complete only when every command succeeds, then leave spec sync and archive as separate follow-up workflow actions.

## 5. Verification follow-up: exact PostgreSQL collation

- [x] 5.1 Apply explicit `COLLATE "C"` to `chain_id`, `transaction_value`, `event_locator` and `provider` in unpublished V1; document that exact raw identity equality is independent of the database default collation and verify the PostgreSQL catalog reports `C` for all four columns.
- [x] 5.2 Extend the storage integration test to persist two otherwise equal raw identities whose valid CAIP-2 references differ only by case; verify both remain distinct while the existing network/provider/idempotency/conflict/concurrency scenarios remain green.
- [x] 5.3 Re-run the complete Maven, dependency, strict OpenSpec, doctor and diff gates; mark this follow-up complete only after PostgreSQL 18.6 applies the corrected V1 successfully, then leave verification, spec sync and archive as separate workflow actions.
