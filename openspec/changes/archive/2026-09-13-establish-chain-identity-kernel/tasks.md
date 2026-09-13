## 1. Implement the kernel identity API

- [x] 1.1 Add package-private validation for canonical chain slugs and opaque local values; verify null, blank, surrounding-whitespace, unsupported chain characters and control-character inputs fail without normalization.
- [x] 1.2 Add immutable `ChainId` with the canonical slug invariant, `SOLANA` constant and deterministic natural order; verify equal values have value equality/hash equality and independently created Solana identities match the constant.
- [x] 1.3 Add distinct immutable `AssetId`, `WalletAddress` and `TransactionId` values containing a non-null chain and exact opaque local value; verify each type is dependency-free public `kernel::api`, rejects null chain and has no Spring or persistence annotations.
- [x] 1.4 Add immutable `EventId` from non-null `TransactionId` plus opaque event locator and `BlockPosition` from non-null chain plus non-negative `long`; verify Solana slot/EVM block-number semantics, event composition and rejection of missing context or negative positions.
- [x] 1.5 Implement each same-category total order from complete identity evidence; verify sorting is deterministic and equal local values on different chains or transactions remain distinct map/set keys.

## 2. Prove public behavior and boundaries

- [x] 2.1 Add focused kernel unit tests for accepted and rejected chain identifiers; verify every canonical-chain scenario in the delta spec.
- [x] 2.2 Add parameterized or equivalently compact tests for asset, wallet and transaction identities; verify exact preservation, invalid input, explicit null-chain rejection, category separation, equality, hashing and cross-chain distinction.
- [x] 2.3 Add focused tests for event identities and block positions; verify transaction-scoped event locators, cross-transaction distinction, non-negative numeric position and deterministic order.
- [x] 2.4 Extend architecture/public-API evidence only where necessary; verify `ApplicationModules.verify()`, the exact six-module map and stable-Java compilation of all six `api` roots still pass without changing the DAG.

## 3. Update durable documentation

- [x] 3.1 Update `docs/modules/kernel.md`, `docs/modules/marketdata.md`, `docs/ARCHITECTURE.md`, `docs/GLOSSARY.md`, `docs/TESTING.md`, `docs/PROJECT_SUMMARY.md` and `docs/ROADMAP.md`; verify the six-type vocabulary, Solana slot semantics and future raw/normalized identity keys are discoverable while DDL, chain/provider parsing and serialization remain deferred.

## 4. Verify and hand off

- [x] 4.1 Run `mvnw.cmd -DskipITs clean verify`; verify all identity, architecture and repository tests pass and the executable JAR is produced without new dependencies or preview features.
- [x] 4.2 With Docker available, run `mvnw.cmd clean verify`; verify PostgreSQL 18.6, Flyway and health/readiness tests remain green despite this dependency-free kernel change.
- [x] 4.3 Run `openspec validate --all --strict --no-interactive`, `openspec doctor`, `git diff --check` and a resolved dependency-tree inspection; verify specs, references, formatting and the approved dependency baseline.
- [x] 4.4 Review implementation against proposal/spec/design for scope drift, report Git inventory and reach OpenSpec `all_done`; leave verify/sync/archive to their separate post-apply workflows and do not add consumers, DDL, providers or business algorithms.
