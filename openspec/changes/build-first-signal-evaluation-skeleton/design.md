## Context

See [proposal.md](proposal.md) for motivation and the two delta specs for observable behavior. Today only `marketdata.raw_chain_events` is durable; `risk`, `signal` and `evaluation` have descriptors and empty public API packages but no business contracts or tables. The accepted DAG already permits the required calls: `signal` may use `marketdata::api` and `risk::api`, while `evaluation` may use `marketdata::api` and `signal::api`. `wallet` is deliberately absent from this slice.

The slice must prove point-in-time correctness and reproducibility, not approximate the final 90-day research product. It therefore uses a finite repository fixture and one signal family, while preserving the ownership and evidence shapes that later provider, family and horizon changes can extend.

## Goals / Non-Goals

**Goals:**

- Exercise real Spring module APIs and real PostgreSQL persistence from raw evidence through a deterministic report.
- Make every decision, price and aggregate traceable to immutable identities, versions, cutoffs and fingerprints.
- Establish the smallest durable objects for normalized market data, signal snapshots and evaluation results without creating shared persistence models.
- Keep retries idempotent and conflicting evidence explicit at each durable boundary.

**Non-Goals:**

- Generalize a batch framework, job scheduler, plugin system or provider abstraction.
- Finalize schemas for later signal families, wallet intelligence, all risk facts, virtual positions or full Evidence Reports.
- Optimize for production ingestion volume; the only indexes introduced serve concrete point-in-time and identity queries in this change.

## Decisions

### Use one exact recorded `LIQUIDITY_SPIKE` scenario

The acceptance fixture contains four stable Solana swap observations for one asset: a one-hour baseline with USD 50,000 liquidity, a decision observation with USD 80,000 liquidity, the first post-decision entry observation at USD 1.00, and the 1h horizon observation at USD 1.20. The decision observation time is the signal availability instant; entry selection requires a strictly later observation. The run cutoff admits the horizon observation, while the detector receives the earlier decision cutoff.

The fixture also supplies validated point-in-time risk facts: zero manipulation flags, `DISCOVERY` lifecycle and the decision-time liquidity. The `risk` module applies the Roadmap's generic `ALLOW` branch synchronously. The risk facts and resulting decision are copied into the immutable signal evidence, so evaluation never asks `risk` for later state.

This scenario produces score 70 from a versioned first-slice scorer: 20 base points, 30 points for meeting relative growth and 20 for meeting absolute growth. Grade is `B`; complete fresh fixture evidence yields confidence `1.0000`. At USD 80,000 entry liquidity the round-trip haircut is `0.04000000`, making the exact gross/net returns `0.20000000` and `0.16000000`.

Alternative considered: start with `SMART_WALLET_BUY`. Rejected because it would force wallet-history ingestion and FIFO scoring before the market/signal/evaluation path is proven. Alternative considered: introduce a fixture-only signal family. Rejected because production behavior must not exist solely for a test artifact.

### Keep orchestration synchronous and cross module only through APIs

`marketdata::api` exposes a recorded replay command/result, immutable normalized market projections, dataset snapshot identity and bounded point-in-time queries. `risk::api` exposes immutable risk facts and a synchronous assessment result. `signal::api` exposes detection input and the immutable accepted snapshot. `evaluation::api` exposes the run request and report result.

The caller performs a visible sequence: replay/finalize dataset, request signal detection for a dataset and decision cutoff, then request evaluation for the accepted signal and run cutoff. `signal` queries `marketdata::api` and invokes `risk::api`; `evaluation` queries `marketdata::api` and consumes only `signal::api`. No public contract exposes JDBC, repository, provider, Jackson or Spring transaction types.

There is no new top-level orchestrator module and no change to dependency directions. A future HTTP, CLI or scheduled adapter may invoke the same APIs but is outside this change.

Alternative considered: one large service in `evaluation` reading all tables. Rejected because it would bypass module ownership and make risk/signal state mutable from evaluation. Events were also rejected: this finite immediate workflow needs synchronous results, and high-volume observations must not use the Modulith event registry.

### Persist module-owned evidence with globally ordered Flyway migrations

`marketdata` receives the next global migration and owns normalized swaps plus immutable dataset snapshots and their members. Normalized identity is `(chain_id, transaction_value, event_locator)`; provider is retained as lineage, not identity. A concrete point-in-time query by chain, asset and observation time justifies its ordered index. Snapshot members reference normalized identities and are immutable.

`signal` receives its first schema in the following migration and owns candidates and accepted signals. Candidate deduplication uses chain, asset, family, detector/configuration identity and the explicit decision window. Accepted signal evidence includes source normalized identities, dataset fingerprint, risk evidence and structured scorer reasoning produced from validated domain values.

`evaluation` receives its first schema in the following migration and owns run manifests, 1h ENTRY outcomes and report metadata/content fingerprints. Outcomes are keyed by run, signal and horizon so a later evaluation algorithm can measure the same signal without overwriting historical evidence. The report is rendered from the owned run/outcome rows and immutable signal projection; it does not join another module's tables.

JSONB may be used only for immutable structured evidence created and read through versioned domain mappers; identity, time, exact numeric values, status and query keys remain explicit columns. All SQL, row mappers and migrations remain inside the owning module. No risk or wallet schema is introduced.

Alternative considered: one cross-module `research_results` schema. Rejected by ADR 0004. Alternative considered: keep every object in memory. Rejected because it would not prove transaction ownership, migration safety, restart-safe idempotency or traceability.

### Use exact field-specific numeric and time contracts

Raw token quantities remain integers. USD price uses `NUMERIC(38,18)`, USD liquidity uses `NUMERIC(38,8)`, confidence uses `NUMERIC(5,4)`, and return/friction ratios use `NUMERIC(18,8)`. Java uses `BigInteger`/`BigDecimal`; lossy normalization uses explicit `HALF_EVEN` rounding at the target scale. The fixture values are exactly representable, so its expected values require no rounding.

All authoritative times are UTC `Instant` values persisted as `TIMESTAMPTZ(6)`. Domain calculations receive decision/evaluation instants explicitly. Price selection orders by observation time and then normalized identity. Baseline selection uses the latest eligible observation at or before decision time minus one hour; the current value is the latest eligible observation at or before the decision cutoff. Entry is the earliest observation strictly after signal availability. The 1h price is the deterministic eligible observation at the horizon boundary admitted by the evaluation cutoff. Missing admissible prices create an explicit unpriced outcome rather than suppressing the row.

Alternative considered: use `double` and database defaults for this temporary slice. Rejected because a walking skeleton that violates the permanent reproducibility contract would prove the wrong architecture.

### Fingerprint canonical content with versioned length-prefixed encoding

Dataset, canonical configuration, deterministic IDs and report content use SHA-256 fingerprints formatted as `sha256:<lowercase-hex>`. Before hashing, each value is normalized according to its field contract and encoded as an ordered UTF-8 sequence of versioned field names plus byte-length-prefixed values. Decimals use plain strings at declared scale, instants use canonical UTC text and collections use the documented total order.

The dataset fingerprint includes normalized content, raw identity/digest lineage and transformation version. The configuration fingerprint includes detector, scorer, risk and valuation settings. The report fingerprint excludes presentation timestamps and includes ordered computational content plus run provenance. Changed content or version creates a different identity; repeated or permuted equal content creates the same identity.

Alternative considered: hash arbitrary JSON serialization. Rejected because object-property order and serializer settings are not an adequate canonicalization contract.

### Split transactions by owning state transition

Recorded replay stores raw observations through the existing `marketdata` transaction, then normalizes each accepted raw record in a separate `marketdata` transaction. A parse failure leaves raw evidence durable and creates no partial normalized row. Dataset finalization is one short `marketdata` transaction after all selected members are known.

Signal detection first records or resolves the candidate in a short `signal` transaction. Risk assessment and market-data reads occur outside that write transaction. A second `signal` transaction records the immutable decision and accepted snapshot. This preserves a measurable candidate if the gate rejects it and avoids a deep transaction spanning modules.

Evaluation performs market/signal reads before its owning write. One short `evaluation` transaction inserts or resolves the run, outcome and report evidence atomically. Equal retries return the existing result; a deterministic identity with different immutable evidence is an explicit conflict. No transaction contains provider I/O.

Alternative considered: one transaction around the entire vertical slice. Rejected because it obscures ownership, couples failures and cannot evolve safely toward remote provider work.

### Verify the slice at pure, module and PostgreSQL levels

Developer first adds only the inert API data carriers/interfaces needed for tests to compile; these types provide no behavior, bean or persistence path. Before functional implementation, specification-derived tests are added and a targeted acceptance test must execute and fail at the expected missing-behavior assertion rather than at compilation, discovery, context startup or Docker setup.

Pure tests cover canonical encoding/fingerprints, exact risk/scoring/friction arithmetic, threshold boundaries, time selection and report ordering. Module tests cover API-only dependency use and immutable snapshot isolation. Testcontainers tests apply every migration and exercise the recorded fixture twice, in permuted input order, asserting raw-first lineage, physical ownership, no look-ahead, idempotent row counts, exact 1h values and identical report fingerprints. Negative tests cover parse failure, normalization conflict, insufficient detection evidence, non-ALLOW gating, missing prices and incomplete run provenance.

The existing `ApplicationModules.verify()` and repository convention tests remain unchanged and green. No test connects to the persistent developer Compose volume.

### Add no dependency or infrastructure component

Implementation uses Java 25, existing Spring/JDBC/Flyway/Jackson capabilities and PostgreSQL 18.6 already present in the build. The recorded fixture is repository test data; it is not a provider adapter or runtime fallback. No `pom.xml` dependency change is expected.

## Risks / Trade-offs

- [The first slice appears to validate signal profitability] → Report language and APIs distinguish score from measured return and label the single fixture as architectural evidence, not statistical edge.
- [Schemas become overfit to one family] → Persist stable identities, lineage and provenance explicitly, but keep family-specific calculations/versioned evidence narrow; later families receive their own reviewed extensions.
- [A full vertical slice is too large for one pass] → Implement in ownership order with targeted gates after each module and keep wallet, providers, extra horizons and lifecycle exits out of scope.
- [Future observations leak into detection] → Separate dataset cutoff from decision cutoff, enforce cutoff predicates in market-data APIs and include a later observation in the fixture specifically to prove exclusion.
- [Retry creates duplicate candidates, runs or outcomes] → Derive identities from canonical immutable inputs and verify equal retry plus conflict behavior against PostgreSQL.
- [JSONB hides unstable or unvalidated data] → Version typed mappers, keep searchable/authoritative fields relational and round-trip every evidence version in tests.
- [The repository already has staged work from the completed database change] → Developer must preserve the Git index, edit only the active change scope and report this baseline separately in the handoff.

## Migration Plan

1. Establish compile-only public contract seams and observe the valid targeted behavioral red before adding functional behavior.
2. Add market-data normalization, point-in-time queries, snapshot persistence and its migration; verify replay and fingerprint behavior against PostgreSQL.
3. Add the pure risk decision and signal candidate/snapshot flow with its migration; verify cutoff isolation and candidate-first idempotency.
4. Add evaluation price selection, exact friction/outcome/report flow with its migration; run the complete fixture twice and verify identical evidence.
5. Update affected architecture/module documentation and run the complete repository gate.

Rollback removes the new APIs and code and rolls back only in a disposable development/test database. Applied production-like Flyway migrations are never edited or deleted; a forward migration is required once these versions have been applied outside disposable environments. No existing raw observation is rewritten.
