## Context

See [proposal.md](proposal.md). The current eight-module topology consists only of Spring Modulith package descriptors and named `api` package descriptors; no business Java types, tables, migrations or public contracts need data migration. The exact topology is independently locked by `ApplicationModules.verify()`, an expected-name assertion and an expected dependency-map assertion.

## Goals / Non-Goals

**Goals:**

- Make the implemented Modulith topology match the smallest coherent MVP product flow.
- Preserve explicit named interfaces and exact automated verification while reducing the number of boundaries.
- Give price observations, valuation and both forms of replay unambiguous single owners.
- Preserve architectural history in archived changes and superseding ADR records.

**Non-Goals:**

- Define public domain types, method signatures, events, persistence mappings or transactions.
- Implement any provider, fixture pipeline, signal rule, risk rule, wallet calculation, evaluation algorithm or report.
- Introduce execution abstractions, governance placeholders or a second database ownership model.
- Change dependencies, runtime infrastructure, health behavior or the one-JAR deployment model.

## Decisions

### 1. Use six product-shaped modules

The current topology is replaced by:

| Module | Responsibility | Allowed dependencies |
| --- | --- | --- |
| `kernel` | Stable chain-aware identities and cross-module value concepts | none |
| `marketdata` | Provider inputs, immutable raw observations, normalization, observed prices and raw replay | `kernel::api` |
| `risk` | Point-in-time token risk facts and decisions | `kernel::api`, `marketdata::api` |
| `wallet` | Point-in-time wallet history, performance and observation | `kernel::api`, `marketdata::api` |
| `signal` | Detection, scoring, reasoning and immutable decision-time signal snapshots | `kernel::api`, `marketdata::api`, `risk::api`, `wallet::api` |
| `evaluation` | Point-in-time valuation, outcomes, evaluation replay, aggregation and reproducible reports | `kernel::api`, `marketdata::api`, `signal::api` |

Keeping eight empty boundaries was rejected because `governance` has no MVP behavior, `strategy` names a broader concept than the signal engine, and `measurement` plus `research` share one MVP lifecycle: evaluate recorded signals. Collapsing `risk` or `wallet` into `signal` was rejected because they own distinct facts, calculations and future reuse. Collapsing everything into one module was rejected because it would remove enforceable data-flow direction.

### 2. Keep `api` as the only cross-module surface

Every one of the six modules retains a root descriptor and an `api` named interface. `kernel` values will therefore be introduced under `kernel::api` by the later chain-identity change. No public type is created by this structural change.

Placing kernel values directly in the module root was rejected because it would create a special visibility convention and weaken uniform dependency declarations.

### 3. Remove governance until execution has an approved capability

The descriptor and module documentation for `governance` are removed from the active topology. Active architecture rules explicitly prohibit signing, order submission and PAPER/LIVE execution. A future execution change must define its gates and decide whether a new governance boundary is justified before any executable path exists.

Keeping an empty future-facing module was rejected under YAGNI: a textual and tested exclusion is sufficient while there is no execution code.

### 4. Rename strategy to signal and merge measurement/research into evaluation

The empty `strategy` descriptor and named interface move to `signal`. The empty `measurement` descriptor and named interface provide the history-preserving basis for `evaluation`; the empty `research` descriptors are removed after their documentation is merged into the evaluation contract. No compatibility adapter is needed because none of these packages contains a public Java type.

The active module documentation becomes exactly six pages plus its index. Historical names remain only in archives or locally marked history.

### 5. Separate observation ownership from valuation

`marketdata` owns observed prices, snapshots, source identity, observation time and data-quality facts. `evaluation` queries immutable market-data observations through `marketdata::api`, selects the admissible point-in-time observation and owns valuation, friction and outcome semantics. It never persists a competing market-price history.

This prevents both modules from becoming authoritative for the same fact while allowing evaluation to explain which observation and rule produced an outcome.

### 6. Separate raw replay from evaluation replay

`marketdata` owns replay and recovery of recorded provider inputs through normalization. `evaluation` owns deterministic re-evaluation of immutable signal snapshots against recorded point-in-time observations. Neither replay crosses into the other's tables or implementation packages.

`evaluation` deliberately does not depend on `risk` or `wallet`: the `signal` contract eventually carries an immutable decision-time snapshot that contains the accepted evidence without exposing those modules' internal DTOs. This protects replay from later state and look-ahead bias.

### 7. Preserve exact executable topology checks

The architecture test continues to run `ApplicationModules.verify()`, asserts exactly six discovered module names and compares every descriptor's `allowedDependencies` with the documented map. Repository convention tests update the exact expected API-root set to the same six names and reject removed roots.

Relying only on Modulith cycle/access verification was rejected because it would not detect an accidentally missing expected module or a new top-level module with no illegal imports.

### 8. Supersede the topology decision without rewriting history

Create a new ADR for the six-module MVP topology and mark the eight-module-count sentence in ADR 0001 as superseded by it; retain ADR 0001's modular-monolith deployment decision. Update only active documentation, main specs and `openspec/config.yaml`. Archived OpenSpec changes are immutable evidence of the earlier accepted baseline.

Editing archived bootstrap artifacts was rejected because it would erase the sequence of architectural decisions.

## Risks / Trade-offs

- **[Evaluation grows too broad]** → Keep its responsibility limited to evaluation lifecycle and data; split it later only if independent change cadence, ownership or scaling is measured.
- **[Removed names survive in active documentation]** → Search active paths explicitly and extend repository convention tests while excluding historical archives from current-topology assertions.
- **[Future execution is added without gates]** → Keep an explicit MVP execution exclusion in Architecture, AGENTS and the module-boundaries main spec; require a dedicated change and ADR before execution.
- **[Merged evaluation reads current risk or wallet state]** → Enforce the DAG without those dependencies and require immutable decision-time signal snapshots in the future signal contract.
- **[Package moves obscure history]** → Prefer version-control-aware moves for retained empty descriptors and document deleted empty boundaries in the new ADR.

## Migration Plan

1. Update the accepted topology record, active architecture documents and module pages to define the six-module DAG and execution exclusion.
2. Move the empty `strategy` skeleton to `signal`, move the empty `measurement` skeleton to `evaluation`, and remove empty `governance` and `research` descriptors.
3. Update exact module-name, dependency-map and API-root tests before running Modulith verification.
4. Update active OpenSpec context and main delta specs; do not touch archived changes.
5. Run skip-IT and Docker-backed Maven verification, strict OpenSpec validation and active-terminology/link checks.

Rollback before business implementation restores the four prior empty descriptor pairs, the eight-module documentation and the earlier exact maps. There is no database, serialized-data or external API migration.
