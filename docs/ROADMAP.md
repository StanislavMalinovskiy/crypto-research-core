# Crypto Research Core — Roadmap v7.8

**Версия:** v7.8 (six-module Spring Modulith MVP topology)<br>**Дата:** 13 сентября 2026<br>**Базис:** v7.7 + [ADR 0008](adr/0008-six-module-mvp-topology.md)<br>**Подход:** Solana-first, not Solana-only. MVP остаётся Solana-focused, но core data model, identity, partitioning strategy и provider boundaries сразу проектируются так, чтобы позже безболезненно добавить Base / Arbitrum / EVM-сети.

## Статусы решений в этом документе

- **Current baseline** — уже присутствует в репозитории и подтверждено Maven/OpenSpec verification.
- **Target MVP** — продуктовое направление или предварительная техническая модель, которая требует отдельного approved OpenSpec change до реализации.
- **Deferred** — возможное расширение, которое не принято и требует доказанной необходимости.

Roadmap определяет продуктовые цели, порядок целевых возможностей и исследовательские гипотезы; оперативные стадии и Current/Next принадлежат [Delivery Plan](DELIVERY_PLAN.md). Точные Java-контракты, provider selection, бизнес-схемы, ключи, индексы, partitioning и operational implementation утверждаются только соответствующими OpenSpec changes и, когда меняется архитектурная политика, ADR. Если раздел не помечен как current baseline и не ссылается на принятый ADR/main spec, его технические детали следует читать как target draft, а не как уже принятое решение.
---
## 1. Краткая суть
Crypto Research Core — личная research-платформа на Java 25 + Spring Boot + PostgreSQL для **записи on-chain сигналов и измерения их forward outcome**.
Первая цель — не торговля, а **Evidence Report через 10–12 недель**: какие сигналы имеют реальный edge после реалистичных costs, а какие шумят.
**MVP:** Solana-only по реализации.
**Core model:** chain-ready с первого дня.
Это значит:
- в MVP реализуем Solana providers, Solana normalizers, Solana risk facts;
- в domain используются `ChainId`, `TransactionId`, `EventId`, `BlockPosition`, а БД сохраняет CAIP-2 network identity и отдельный nullable observed block hash;
- Base/EVM потом добавляются через новые provider/normalizer/risk implementations;
- signal/evaluation core не переписывается.
**Главный вопрос MVP:** какие entry signal families на Solana показывают positive expectancy_after_costs на forward 1h/4h/24h windows? И отдельно: какие avoidance signals дают meaningful avoided drawdown?
**Главный принцип:**
```plain text
Core architectural invariants are permanent.
Sophistication is disposable.
Solana is first implementation, not permanent boundary.
Add modules, don't rewrite.
```
---
## 2. Что изменилось от v7.7
| # | Область | v7.7 | v7.8 |
| --- | --- | --- | --- |
| 1 | Module topology | eight empty vertical boundaries | six product-shaped modules: `kernel`, `marketdata`, `risk`, `wallet`, `signal`, `evaluation` |
| 2 | Execution | empty `governance` placeholder | no execution module or contract in MVP; gates require a future approved change |
| 3 | Signal engine | `strategy` module name | `signal` owns detection, scoring, reasoning and decision-time snapshots |
| 4 | Evaluation | separate `measurement` and `research` boundaries | one `evaluation` lifecycle for outcomes, replay and reports |
| 5 | Walking skeleton | provider-first sequence | recorded fixture first; real provider adapter follows separately |
| 6 | Verification | exact eight-module checks | exact six-module names, DAG and named API roots |
**Не меняется:** все measurement-correctness и auditability исправления v7.6; Solana-first MVP; Outcome Tracker как главный value; no auto-trading before validation gates.
---
## 3. Главная философия
```plain text
Не строим бота для торговли.
Строим систему для измерения сигналов.

Не доверяем интуиции 'smart money работает'.
Измеряем outcome и сравниваем signal families.

Не строим multi-chain сразу.
Строим chain-ready core и реализуем Solana как первый adapter.

Не делаем cross-chain identity в MVP.
Но не блокируем его архитектурно.

Add modules, don't rewrite.
Evidence over architecture.
Outcome over signal.
Measurement before money.
```
---
## 4. Архитектурные инварианты
Нельзя сокращать:
1. Domain model of every application module is internal, immutable by default and free from Spring/persistence/provider dependencies.
2. Chain-aware identity с первого дня.
3. Provider abstractions everywhere.
4. Point-in-time first.
5. Cost-aware everywhere.
6. No signing, order submission or PAPER/LIVE execution in MVP; gates are designed before execution is introduced.
7. Future capital and venue policies require their own approved execution change.
8. Token Risk Engine before signal.
9. Signal definition and configuration versioning.
10. Idempotency.
11. Observability from day one.
12. Chain-specific JSONB facts validated by domain sealed types.
13. Spring Modulith boundaries are verified in tests; no cyclic dependencies or imports from another module's `internal` packages.
14. Synchronous module APIs; reactive/provider-specific types never leak into business contracts.
15. Reproducible evidence: UTC/reference time, exact arithmetic, dataset/config/build/algorithm identity, deterministic ordering and seeded randomness.
---
## 5. Solana-first, not Solana-only
**MVP реализация:** `chainId = ChainId.SOLANA_MAINNET` (`solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp`); providers = Helius, Bitquery, DexScreener, GoPlus; normalizers = Pump.fun, PumpSwap, Raydium / selected major DEX programs; risk facts = Solana mint/freeze authority, LP lock, holders, creator/dev facts.
**Core готовность к Base/EVM:** exact customary CAIP-2 network identifiers such as Base Mainnet `eip155:8453` later; providers = Alchemy / Moralis / Chainstack / QuickNode later; normalizers = UniswapV3, Aerodrome, ERC20 Transfer events later; risk facts = EVM contract ownership, proxy, honeypot, tax, blacklist, pause later.
В Phase 1 мы не реализуем Base, но делаем такие domain/DB решения, чтобы Base не потребовал переписывать wallet identity, token identity, swap identity, signal identity, outcome tables, strategy framework, provider layer, normalizer boundary и token risk JSONB validation approach.
---
## 6. Cross-chain identity stance
В MVP wallet identity строго network-scoped: `WalletId = CAIP-2 chain ID + address`. Например, одинаковый local address на Solana Mainnet `solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp` и Base Mainnet `eip155:8453` образует разные wallet records.
Почему так: MVP измеряет сигналы, а не пытается установить, что один человек контролирует кошельки в разных сетях. Cross-chain identity требует bridge-flow analysis, CEX heuristics, timing correlation, labels, attribution и несёт false-positive risk.
**Future Phase C:** если позже понадобится cross-chain wallet linking, добавляется отдельный enrichment layer:
- `wallet_aliases(id, identity_id, chain, wallet_address, confidence, source, evidence, created_at)`
- `cross_chain_identities(identity_id, label, confidence, source, evidence, created_at, updated_at)`
- `cross_chain_identity_members(identity_id, chain, wallet_address, confidence, evidence)`
Это не меняет `wallets`, `wallet_score_history`, `signals`; добавляется поверх chain-scoped wallet identity.
---
## 7. Что упрощается
| Компонент | Full target | v7.7 MVP | Расширение |
| --- | --- | --- | --- |
| Module structure | multiple deployables | 6 Spring Modulith modules, 1 Maven module, 1 JAR | physical split only after measured need |
| Multi-chain | Solana + Base + Arbitrum + CEX | Solana implementation only | Base/EVM adapters later |
| Cross-chain identity | entity-level graph | not MVP | Phase C wallet aliases |
| Backfill | 12 месяцев | 90 дней Solana | expand idempotent |
| Cohort detection | Full Louvain | cohort-light | full cohort after evidence |
| Wallet Intelligence | decay + embeddings | basic + profit_factor | improve after evidence |
| Position Management | 8+ triggers | 4 triggers | expand later |
| Latency stack | LaserStream + VPS | Helius WebSocket | optimize later |
| Providers | 8+ | 4 Solana-related | add Base/EVM later |
| Backtest | walk-forward 9m | 90-day window | later |
| Capital pipeline | parallel funding-bot | deferred | separate project |
---
## 8. Что НЕ упрощается в Target MVP

Следующие пункты являются целевыми продуктовыми ограничениями. Они не входят автоматически в текущий bootstrap и реализуются отдельными changes.

| Компонент | Почему сохраняется |
| --- | --- |
| Chain-aware identity | иначе Base/EVM потом потребует болезненную миграцию |
| Execution exclusion | MVP не содержит signing/order submission; gates проектируются отдельным change до PAPER/LIVE |
| Append-only `wallet_score_history` | point-in-time queries |
| Append-only `signal_outcomes` | главный value MVP |
| Оценка необходимости Postgres partitioning | решение по каждой high-volume таблице принимается до её первой production migration |
| Provider interfaces | новый провайдер = новый adapter |
| Signal definition versioning | изменение config = новая версия |
| Token Risk Engine | иначе outcome загрязнён scam/rug токенами |
| Cost-aware Outcome Tracker | иначе measurements слишком оптимистичны |
| Signal Reasoning JSONB | forensic readability |
| Idempotency | повторные backfill/ingest не ломают БД |
| `virtual_positions` | measurement отдельно от trading |
| `profit_factor` | win-rate alone = self-deception |
| JSONB validation through sealed types | чтобы risk facts не стали неконтролируемой схемой |
---
## 9. Стек и статус решений

### Current baseline

- **Language/runtime:** Java 25; records, sealed types, pattern matching, switch expressions, virtual threads and other production-ready Java 25 features where appropriate
- **Preview:** disabled in the current baseline; any future feature requires a dedicated OpenSpec change and superseding ADR
- **Framework:** Spring Boot 4.1.1 + Spring MVC, synchronous imperative model
- **Modularity:** Spring Modulith 2.1.1
- **Concurrency:** stable virtual threads for suitable blocking I/O; all future fan-out must be bounded
- **БД:** PostgreSQL 18, verified against 18.6; Flyway owns the implemented `marketdata`, `signal` and `evaluation` schemas, while `risk` has no schema and no table is partitioned
- **Persistence:** Spring Data JDBC / JdbcClient, not JPA or R2DBC
- **Build:** Maven Wrapper; Maven 3.9.x baseline, one Maven module
- **Migrations:** Flyway version managed by Spring Boot dependency management
- **Observability:** Spring Boot Actuator health only
- **Test:** Testcontainers, Spring Modulith Test and JUnit version managed by Spring Boot
- **Not used:** Spring WebFlux, Reactor application pipelines, Vert.x, R2DBC

### Target MVP

- Chain-aware kernel identities, idempotent module-owned persistence and the deterministic recorded `LIQUIDITY_SPIKE` path from raw evidence through a `1h` outcome and reproducible report are delivered. Live providers, wallet analytics and broader research remain target work.
- Candidate Solana providers include Helius, Bitquery, DexScreener and GoPlus. Each provider requires coverage/limit/terms validation, an OpenSpec design and approval of any new production dependency. An ADR is needed only if the provider boundary or general architecture changes.
- A concrete high-volume table may be partitioned by its owning migration only after volume, retention and query-pattern evidence is documented. Changing the general persistence strategy requires an ADR.
- Structured logging, correlation metadata and operational metrics are later increments, not bootstrap completion criteria.

### Deferred

- Redis and Caffeine are not selected. Any cache, broker, additional database, deployable or external observability platform requires measured need and an approved OpenSpec change; an ADR is required when the architectural baseline changes.
- Prometheus and Grafana infrastructure are not part of the current repository baseline.
- Alchemy, Moralis, Chainstack, Birdeye, Arkham, Jupiter and Jito remain provider candidates, not approved integrations.

> **Budget note:** provider prices and limits below are planning assumptions from April 2026. Verify current vendor terms before purchase; they are not architectural invariants.
| Component | Cost/мес |
| --- | --- |
| Helius Developer | \$49 |
| DexScreener, GoPlus, Bitquery | \$0 free tiers |
| Cloud VPS optional | \$0–30 |
| Backup storage | \$5–10 |
| **Минимум** | **\$54/мес** |
| **Recommended** | **\$84/мес** |
---
## 10. Архитектура и package structure — Spring Modulith
### 10.1 Deployment model
- one Git repository;
- one Maven module;
- one Spring Boot 4.1.1 application;
- one deployable JAR;
- one PostgreSQL database;
- six logical Spring Modulith application modules;
- no microservices in MVP.
Spring Modulith modules are package-based business boundaries, not separate Maven modules. Physical splitting is allowed only when independent scaling, isolation or deployment becomes a measured requirement.
### 10.2 Application modules
| Module | Responsibility |
| --- | --- |
| `kernel` | Stable value objects: `ChainId`, `AssetId`, `WalletAddress`, `TransactionId`, `EventId`, `BlockPosition` |
| `marketdata` | Providers, raw observations, normalization, swaps, observed prices, source/quality facts and raw replay |
| `risk` | Token risk facts and BLOCK / WATCH_ONLY / ALLOW decisions |
| `wallet` | FIFO PnL, point-in-time scoring, tiers and watchlist |
| `signal` | Detection, scoring, reasoning and immutable decision-time signal snapshots |
| `evaluation` | Point-in-time price selection, valuation, friction, outcomes, deterministic evaluation replay and Evidence Report |
### 10.3 Package tree
```plain text
crypto-research-core
├── pom.xml
└── src/main/java/io/cryptoresearch
    ├── CryptoResearchApplication.java
    ├── kernel
    ├── marketdata
    │   ├── api
    │   └── internal
    │       ├── domain
    │       ├── application
    │       ├── ingest
    │       └── infrastructure
    │           ├── persistence
    │           └── provider
    ├── risk
    │   ├── api
    │   └── internal
    ├── wallet
    │   ├── api
    │   └── internal
    ├── signal
    │   ├── api
    │   └── internal
    │       ├── detector
    │       ├── scoring
    │       └── persistence
    └── evaluation
        ├── api
        └── internal
            ├── replay
            └── report
```
There are no top-level `domain`, `persistence`, `provider` or `observability` modules. Each vertical module owns its domain model, repositories, database access and external adapters. Observability is implemented inside the owning module and composed at application level.
### 10.4 Allowed dependencies
The Roadmap fixes the product direction: data collection stays below signal detection and evaluation, `risk` and `wallet` remain independent inputs to `signal`, and `evaluation` consumes the immutable decision-time signal snapshot without depending on later risk or wallet state. The current exact allowed/forbidden DAG is maintained in [Architecture](ARCHITECTURE.md) and enforced by Spring Modulith descriptors and tests. Changing an edge requires an ADR; a Roadmap edit cannot silently override an accepted architecture decision.

Exposed contracts live in named `api` interfaces. Internal packages, repositories, persistence entities and provider adapters never cross module boundaries. Fixture replay and online processing reuse the same pure signal/risk evaluation rules.
### 10.5 Synchronous programming model
Application and module APIs are synchronous and imperative:
- Spring MVC, not WebFlux;
- Spring Data JDBC / JdbcClient, not R2DBC or JPA;
- no Reactor `Mono` / `Flux`, Vert.x `Future` or provider-specific async types in module contracts;
- blocking HTTP/RPC/database calls execute on virtual threads;
- parallel fan-out to independent providers remains bounded and uses only stable Java APIs approved by the implementing change;
- stable context propagation mechanisms may be selected by the implementing change when correlation metadata is introduced;
- records, sealed interfaces, pattern matching and switch expressions are preferred where they improve the model;
- preview APIs are not enabled; introducing one requires an explicit architecture and build-baseline decision.
WebSocket providers may use callbacks at the transport boundary because the protocol is event-driven. The adapter immediately writes events to a bounded internal queue; downstream application processing remains synchronous, batched and protected by backpressure.
### 10.6 Module communication
Use direct synchronous API calls when the caller needs an immediate answer: current risk decision, wallet score or point-in-time price.
Use Spring application events only for completed low-frequency business facts such as `RiskDecisionCreated`, `SignalAccepted`, `CandidateRejected`, `OutcomeCompleted` and `ExperimentCompleted`.
Do not put every swap or price tick into Spring Modulith Event Publication Registry. The high-volume ingest path stays inside `marketdata`; durable module events are reserved for business transitions to avoid database write amplification.
---
## 11. Chain-aware domain model
Implemented kernel types: `ChainId`, `AssetId`, `WalletAddress`, `TransactionId`, `EventId`, `BlockPosition`.
Универсальный event identity:
```plain text
TransactionId(chain + opaque transaction value) + opaque event locator
```
Solana mapping: `signature -> TransactionId.value`, future canonical instruction/inner-instruction ordinal grammar -> `EventId.locator`, `slot -> BlockPosition.value`; `blockHeight` and `blockhash` remain separate facts.
Base/EVM mapping later: `transaction_hash -> TransactionId.value`, canonical ordinal inside the complete `receipt.logs` sequence -> `EventId.locator`, `block_number -> BlockPosition.value`; a provider-filter result index is never event identity, and block hash remains separate.
The same blockchain event keeps one locator across providers, parser implementations and parser versions. Changing persisted locator grammar requires a separate approved change and forward migration.
---
## 12. Domain types — minimal
Sealed interfaces / enums: `SignalFamilyType`, `EntrySignalFamily`, `AvoidanceSignalFamily`, `TokenRiskDecision`, `ExecutionMode`, `VenueDecisionType`, `TradeSide`, `OutcomeHorizon`, `WalletTier`, `ExitReason`, `ChainSpecificRiskType`, `SignalGrade`, `PriceSourceType`, `PricingStatus`.
Records: `Signal`, `SignalReasoning`, `TokenRiskFacts`, `CommonTokenRiskFacts`, `SolanaTokenRiskFacts`, `PricePoint`, `WalletScore`, `EntryOutcome`, `AvoidanceOutcome`, `VirtualPosition`, `MarketSnapshot`, `NormalizedSwapEvent`, `RawChainEvent`.
Future placeholder: `EvmTokenRiskFacts`.
---
## 13. БД схема — chain-ready минимальный набор

**Status: Target MVP draft.** Список ниже фиксирует исследовательские потребности, но не утверждает окончательные table ownership, DDL, keys, indexes или partitioning. Каждый объект проектируется и проверяется в отдельном change owning-модуля с учётом [ADR 0004](adr/0004-module-data-ownership.md).

Core tables:
- `marketdata.raw_chain_events(chain_id, transaction_value, event_locator, provider, observed_block_position, observed_block_hash nullable, source_event_time nullable, observed_at, payload, payload_hash, parser_version, ingested_at)` is the implemented first storage boundary; primary identity is `(chain_id, transaction_value, event_locator, provider)`, `chain_id` is exact case-sensitive customary CAIP-2, payload is exact append-only `TEXT`, digest is qualified SHA-256, times use microsecond precision, and only the primary-key index exists; partitioning and secondary indexes require later measured evidence. V1 is restricted by caller contract to stable-inclusion historical/finalized evidence and does not verify finality; provisional ingestion requires an approved finality/reorg change
- `wallets(chain, address, first_seen, last_active, source, basic_profile), PK(chain, address)`
- `tokens(chain, address, symbol, decimals, created_at, creator_wallet), PK(chain, address)`
- `swaps(chain_id, transaction_value, event_locator, wallet_address, token_address, side, amount_tokens, amount_native, amount_usd, ts, observed_block_position, observed_block_hash nullable, block_time, venue, program_or_contract, raw_source)`, unique normalized identity `(chain_id, transaction_value, event_locator)`; physical DDL remains for the owning storage change
- `token_metrics(chain, token_address, ts, liquidity_usd, holders, top10_pct, dev_pct, volume_24h, price, venue), PK(chain, token_address, ts), PARTITION BY RANGE(ts)`
- `token_prices(chain, token_address, ts, price_usd, price_native, liquidity_usd, source, venue, confidence NUMERIC(4,2)), PK(chain, token_address, ts), PARTITION BY RANGE(ts)`
- `token_risk_decisions(chain, token_address, asof_ts, decision, common_factors JSONB, chain_specific_factors JSONB, risk_schema_version), PK(chain, token_address, asof_ts)`
Wallet intelligence:
- `wallet_score_history(chain, wallet_address, calculated_at, trade_count, win_rate, profit_factor, avg_win_pct, avg_loss_pct, recent_activity_days, tier, factors JSONB), PK(chain, wallet_address, calculated_at), PARTITION BY RANGE(calculated_at)`
- `wallet_scores(chain, wallet_address, latest_calculated_at, trade_count, win_rate, profit_factor, tier, updated_at), PK(chain, wallet_address)`
Signals/outcomes:
- `signal_candidates(candidate_id, chain, strategy_experiment_id, family_type, family, token_address, detected_at, dedup_bucket, pre_gate_score, confidence, risk_decision, rejection_reason, reasoning JSONB, status), UNIQUE(chain, token_address, family, strategy_experiment_id, dedup_bucket)`
- `signals(signal_id, candidate_id, strategy_experiment_id, chain, family_type, family, token_address, ts, score INT, grade VARCHAR(6), confidence NUMERIC(4,2), reasoning_id, status), indexes(chain, family_type, family, ts), (chain, token_address, ts)`
- `signal_candidate_outcomes(candidate_id, horizon, pricing_status, pricing_confidence, return_net_pct, max_drawdown_pct, measured_at), PK(candidate_id, horizon)`
- `signal_reasoning(signal_id, factors JSONB, risks JSONB, evidence JSONB, chain_context JSONB)`
- `signal_outcomes_entry(signal_id, horizon, chain, price_at_signal, price_at_horizon, price_source, pricing_status, pricing_confidence, return_gross_pct, return_net_pct, return_native_pct, max_drawdown_pct, max_profit_pct, exit_reason, virtual_position_id), PK(signal_id, horizon)`
- `signal_outcomes_avoidance(signal_id, horizon, chain, price_at_signal, price_at_horizon, price_source, pricing_status, pricing_confidence, avoided_drawdown_pct, avoided_drawdown_native_pct, max_loss_in_window_pct), PK(signal_id, horizon)`
- `virtual_positions(id, chain, signal_id, entry_price, entry_ts, status, exit_price, exit_ts, exit_reason, gross_return_pct, net_return_pct, return_native_pct, friction_haircut_applied_pct)`
Strategy/execution/system:
- `strategy_experiments(id, name, version, hypothesis, config JSONB, status, created_at, completed_at)`
- `strategy_dry_run_decisions(id, strategy_id, chain, context_snapshot JSONB, would_signal, ts)`
- `execution_mode_state(id, current_mode, updated_at, updated_by_reason)`
- `venue_decisions(id, chain, signal_id, venue, requested_mode, decision, reason, ts)`
- `capital_events(id, ts, event_type, amount, reason, snapshot JSONB)`
- `system_state(key, value JSONB)`
---
## 14. Partitioning strategy
**Status: Target MVP hypothesis, not current baseline.** Для каждой high-volume таблицы owning change должен обосновать объём, retention и query patterns до выбора partitioning. Предварительный вариант для оценки: `swaps PARTITION BY RANGE(ts)`, `token_metrics PARTITION BY RANGE(ts)`, `token_prices PARTITION BY RANGE(ts)`, `wallet_score_history PARTITION BY RANGE(calculated_at)`.
`token_prices` retention policy: keep high-resolution snapshots for the 90-day MVP Evidence window.<br>Expected scale example: 1,000 tokens × 5-minute snapshots ≈ 288k rows/day, ≈26M rows/90d, manageable with monthly partitions and focused indexes.<br>After Evidence Report, apply downsampling or cold archival if storage/query cost becomes meaningful.
Phase B note: при добавлении Base/EVM оценить `LIST(chain_id)` partitioning with `RANGE(ts)` subpartitions или отдельные monthly partitions с chain-aware indexes.
Причина: Solana и Base имеют разную плотность событий, cadence и semantics (`slot` vs `block_number/log_index`).
Decision rule: не мигрировать заранее. Оценить после того, как Base/EVM data volume \>= 20–30% Solana volume, query plans начинают сканировать лишние partitions, или maintenance/vacuum становится uneven.
---
## 15. JSONB risk schema validation
`token_risk_decisions` stores `common_factors JSONB`, `chain_specific_factors JSONB`, `risk_schema_version`.
Rule: every JSONB payload must be created from validated domain types: `CommonTokenRiskFacts`, `SolanaTokenRiskFacts`, `EvmTokenRiskFacts`.
MVP: `chain_specific_factors = serialized SolanaTokenRiskFacts`, `risk_schema_version = solana-risk-v1`.
Phase B: `chain_specific_factors = serialized EvmTokenRiskFacts`, `risk_schema_version = evm-risk-v1`.
Validation: serialize only through domain mappers; deserialize in tests/reports through sealed type registry; unknown schema version fails fast; no ad-hoc JSONB writes from random services.
---
## 16. Provider abstractions
**Status: Target MVP candidates.** Exact ports and implementations are not approved by bootstrap; each appears only in its implementing change.

Candidate provider APIs: `BlockchainStreamProvider`, `ChainRpcProvider`, `SolanaRpcProvider`, `EvmRpcProvider`, `HistoricalIngestProvider`, `MarketDataProvider`, `RiskDataProvider`, `WalletLabelProvider`, `ExecutionProvider`, `JitoTipFloorProvider`.
Candidate MVP implementations: Helius, Bitquery and DexScreener inside `marketdata`; GoPlus inside `risk`.
Future Base/EVM implementations stay inside the owning vertical module, for example `marketdata.internal.infrastructure.provider.alchemy` and `risk.internal.infrastructure.provider.evm.goplus`.
Rules: domain-neutral DTOs where possible; chain-specific fields stay in implementations; provider stale data lowers confidence or blocks signal; provider failures never produce fake data.
---
## 17. Ingest and normalizers
Common synchronous application pipeline:
```plain text
WebSocket callback → bounded queue → virtual-thread batch consumer → RawChainEvent persistence → ChainSpecificNormalizer → NormalizedSwapEvent → module APIs
```
The WebSocket callback is transport infrastructure only. Domain processing is imperative; no reactive types cross the adapter boundary.
MVP Solana normalizers: `PumpFunParser`, `PumpSwapParser`, `RaydiumParser`.
Future EVM normalizers: `UniswapV3SwapParser`, `AerodromeSwapParser`, `ERC20TransferParser`.
`NormalizedSwapEvent`: `chain_id`, `transaction_value`, `event_locator`, `wallet`, `token`, `side`, `amount_tokens`, `amount_native`, `amount_usd`, `ts`, `observed_block_position`, optional `observed_block_hash`, `venue`, `program_or_contract`.
---
## 18. Token Risk Engine
Common risk facts: `liquidity_usd`, `holders`, `top10_pct`, `dev_pct`, `age`, `volume_24h`, `price`.
Solana risk facts — MVP: `mint_authority_live`, `freeze_authority_live`, `lp_not_locked`, `lp_lock_short`, `dev_history_rugged`, `high_top_holder_concentration`.
EVM risk facts — Phase B: `contract_verified`, `proxy_upgradeable`, `owner_privileges`, `buy_tax`, `sell_tax`, `honeypot`, `blacklist_function`, `pause_function`, `max_tx_limit`, `ownership_renounced`.
MVP decision logic: ≥2 manipulation flags → BLOCK; 1 flag → WATCH_ONLY; lifecycle PRE_LAUNCH/EARLY → BLOCK; liquidity \< \$30K → BLOCK; 0 flags + lifecycle ≥ DISCOVERY → ALLOW.
---
## 19. Wallet Intelligence
Point-in-time minimal scoring + watchlist generation.
Metrics: `trade_count_90d`, `win_rate_90d`, `profit_factor`, `avg_win_pct`, `avg_loss_pct`, `recent_activity_days`, `total_volume_usd_90d`.
Tier assignment:
- STRONG: trade_count \>= 20, win_rate \>= 0.55, profit_factor \>= 1.2, recent_activity \<= 14d
- PROMISING: trade_count \>= 10, win_rate \>= 0.50, profit_factor \>= 1.0, recent_activity \<= 30d
- NOISE: everything else
Wallet identity is chain-scoped: `chain + wallet_address`. Cross-chain wallet linking is not MVP.
---
## 19.1 Wallet PnL Methodology
Wallet scoring must be point-in-time and reproducible.
MVP PnL method:
- use FIFO inventory per `chain + wallet + token`;
- match buy/sell across all normalized venues, not within one venue;
- Pump.fun buy may be closed by PumpSwap or Raydium sell;
- partial sells close oldest open lots first;
- `win_rate` and `profit_factor` use closed trades only;
- open positions are excluded from `win_rate` / `profit_factor`;
- open exposure may be stored as informational metric, not as proof of skill.
PnL denomination:
- primary wallet scoring metric: USD PnL;
- secondary metric: native/SOL PnL where price data is reliable;
- both must be calculated from point-in-time prices only.
Rule: no future prices, no post-hoc labels, no venue-specific blind spots.
---
## 20. Signal Families + Journal
ENTRY families:
1. SMART_WALLET_BUY — wallet ∈ STRONG buys token X with size \>= \$500.
2. MULTI_WALLET_BUY — \>= 3 wallets from watchlist buy same token in 5min window.
3. LIQUIDITY_SPIKE — token liquidity grew \>= 50% in 1h with \>= \$10K added.
4. HOLDER_GROWTH — holders grew \>= 30% in 4h, holder count \>= 200.
AVOIDANCE families:
1. TOKEN_RISK_ALERT — previously ALLOWED token gets new manipulation flag.
MVP detectors operate on `chainId = ChainId.SOLANA_MAINNET`, but `signals` table and `Signal` domain remain network-aware.
For every detection: persist `signal_candidates` before risk gating; assign `family_type`; link mandatory `strategy_experiment_id`; deduplicate by `chain + token + family + strategy_experiment_id + dedup_bucket`, where the bucket/window is explicit per family. For accepted candidates, persist `signals` and `signal_reasoning`. BLOCK candidates remain measurable through `signal_candidate_outcomes` but never create positions.
---
## 20.1 Signal Scoring Model v1
Every ENTRY signal receives:
- `score`: integer 0–100, pre-outcome signal quality estimate
- `grade`: derived from score, presentation-only in MVP
- `confidence`: decimal 0.00–1.00, data completeness / reliability
`score` is not a profitability prediction.<br>`score` helps rank and compare signals before outcome is known.<br>Actual edge is validated only by Outcome Tracker.
MVP scoring approach:
- each signal family has its own scorer;
- score is computed from simple additive factors;
- MVP avoids fixed global weights;
- all score components are persisted in `signal_reasoning.factors`;
- weight calibration is deferred until Phase 9+ after Evidence Report.
Risk gate interaction:
- `BLOCK`: candidate and shadow outcome are persisted, but no ENTRY signal or virtual position is created;
- `WATCH_ONLY`: ENTRY score is capped at 69;
- `ALLOW`: normal scoring.
Rejected-candidate outcomes are reported separately and never mixed with tradable ENTRY outcomes.
Grade mapping:
- `A+`: 90–100
- `A`: 80–89
- `B`: 70–79
- `C`: 60–69
- `REJECT`: \<60
Grade is for readability only in MVP.<br>Stored `grade` is an immutable snapshot derived from `score` at signal creation time.<br>Grade does not trigger trading, paper trading, or capital decisions.
Confidence rules:
- all required data sources are fresh and available → `1.00`;
- partial data → lower confidence;
- stale real-time chain data \>5 minutes → confidence capped at `0.50`;
- stale market/risk data → provider-specific TTL and field criticality rules;
- required provider failure → block signal or lower confidence, depending on field criticality.
Phase 9+ calibration:
- correlate score components with `expectancy_R` per signal family;
- adjust factor weights based on measured contribution;
- introduce stricter decision thresholds only for paper trading.
---
## 21. Position Management + ExecutionSimulator
Purpose: measurement only, not real trading.
VirtualPositionTracker creates virtual position for accepted ENTRY signals; AVOIDANCE signals do not create positions.
Exit triggers: stop loss \<= -30%; take profit \>= +100%; time stop \>= 7 days; max hold \>= 30 days.
Dynamic friction:
| Liquidity tier | Round-trip haircut |
| --- | --- |
| \>= \$200K | 3% |
| \$50K–\$200K | 4% |
| \< \$50K | 5% |
Phase B/EVM requires separate friction profile.
---
## 22. Outcome Tracker
ENTRY outcome: for each ENTRY signal and horizon, get virtual position, determine exit price via rules, calculate gross return, subtract dynamic friction, persist in `signal_outcomes_entry`.
ENTRY metrics: count, win_rate, avg_return_net_usd, expectancy_R, max_drawdown, distribution, secondary native/SOL return.
AVOIDANCE outcome: for each AVOIDANCE signal and horizon, get price at signal, find min price in window, calculate avoided drawdown, persist in `signal_outcomes_avoidance`.
AVOIDANCE metrics: count, hit_rate, avg_avoided_drawdown_usd, max_avoided_drawdown, secondary native/SOL drawdown.
Entry and avoidance metrics are never mixed.<br>Outcome rows are never silently dropped because of missing price; pricing status must explain every incomplete valuation.
---
## 22.1 Price Source & Outcome Valuation Policy
Outcome prices are point-in-time and reproducible.
Primary price source:
- internal `token_prices` snapshots derived from normalized swaps / pool state across supported Solana venues.
Fallback price sources:
- `token_metrics` snapshots;
- DexScreener / external market data only as fallback and marked in outcome evidence.
Every outcome price stores: `price_usd`, `price_native`, source, observed time, confidence, and liquidity at pricing time.<br>Backtest must never use future market data.
Valuation policy:
- primary Evidence Report metric: USD net return after dynamic costs;
- secondary metric: native/SOL return;
- if USD and native disagree, report both instead of hiding the difference.
Dead-token / no-liquidity policy:
- exit triggers are evaluated chronologically;
- if stop loss, take profit or time stop was executable before token death, use that earlier exit price;
- if liquidity becomes zero or token becomes non-tradable before horizon and no earlier executable exit exists, mark `TERMINAL_NO_LIQUIDITY` and return = `-100%`;
- if price is temporarily unavailable but liquidity exists, mark `UNPRICED_PENDING` / low confidence, never drop the outcome row.
This prevents survivorship bias in Evidence Report.
---
## 23. Backtest 90d + Evidence Report
BacktestRunner: iterates through 90-day `swaps` chronologically; evaluates detectors using only data available before event time; uses `wallet_score_history` for point-in-time wallet score; generates signals; applies virtual position + ExecutionSimulator; aggregates outcomes by family.
Evidence Report includes entry families breakdown, avoidance families breakdown, per-horizon breakdown, outcome distribution, pricing confidence/source summary, and a separate rejected-candidate outcome analysis that validates whether Risk Engine filters add value. Final recommendation: deepen / pivot / extend observation / honest exit.
MVP decision gates:
- proceed to Phase 9 if entry family expectancy_R \>= 0.15R after dynamic costs, sample size is not tiny, and outcome is not driven by one outlier trade;
- if only avoidance works, build defensive overlay, not trading strategy;
- if nothing works, extend data or pivot.
---
## 24. Delivery sequence

The current stage, next change and seven-stage route to the Evidence Report are maintained in the [Delivery Plan](DELIVERY_PLAN.md). The detailed phases below remain product targets and hypotheses; they do not define active OpenSpec scope or current implementation status.

Primary product horizon: 10–12 weeks. Conservative expectation with chain-ready overhead: up to 12.5 weeks.
---
## 25. Phase 1 — Architecture and data foundation

**Current baseline completed:** Maven Wrapper single-module; Spring Boot 4.1.1; Spring Modulith 2.1.1; six application modules with explicit allowed dependencies; synchronous Spring MVC/Spring Data JDBC baseline; stable Java 25 configuration; PostgreSQL/Flyway/Testcontainers foundation; Actuator health; Maven Enforcer; Modulith verification tests. The implemented recorded-input slice persists raw and normalized swaps, immutable dataset snapshots, candidate-first risk-gated `LIQUIDITY_SPIKE` evidence, exact `1h` outcomes and reproducible reports. Live provider ingestion, wallet analytics, broader signal research and execution remain absent.

The operational change sequence and Current/Next position are owned by the [Delivery Plan](DELIVERY_PLAN.md). Detailed scope and task progress remain in each OpenSpec change.

Any change that introduces or mutates persisted data must include idempotency in its own acceptance criteria. Idempotency is not deferred to a later repair change.

Provider contracts, additional business DDL or indexes, partitioning, managed deployment, structured logging and future execution gates remain target capabilities requiring their own approved changes. No signing, order submission or PAPER/LIVE execution belongs to the MVP topology.

Phase 1 DoD is cumulative across the relevant approved changes; it is not the DoD of `bootstrap-modular-foundation`.
---
## 26. Phase 2 — Data Layer
Goal: clean reproducible Solana swap data.
Начать с recorded provider fixture и тонкого end-to-end path: raw input → normalized swap → immutable signal snapshot → 1h outcome → reproducible report. После подтверждения архитектурного пути отдельный change добавляет реальный Helius adapter; остальные providers выбираются независимо по coverage, limits и terms.
Целевые возможности: append-only raw event persistence before normalization; Solana normalizers; raw idempotency keyed by `chain_id + transaction_value + event_locator + provider`; normalized idempotency keyed by `chain_id + transaction_value + event_locator`; token discovery; observed price snapshots; historical backfill; coverage and gap reporting.
DoD: 24h stream without unresolved critical gaps; reconnect gap recovery tested; 90-day Solana backfill complete; raw payload and parser version available for replay; no duplicates; parse error \<1%; coverage/gaps visible in Grafana; data freshness \<60s; price snapshots available for outcome windows.
---
## 26.1 Stream Gap Detection & Recovery
Goal: avoid silent loss of real-time events.
Policy:
- persist the last processed Solana `BlockPosition` (slot) in owning operational state;
- on reconnect, compare last processed slot with current slot;
- backfill missed slot range through RPC / historical provider;
- deduplicate recovered normalized events by `chain_id + transaction_value + event_locator`;
- if recovery is incomplete, mark affected window as unresolved gap and lower signal confidence;
- run periodic gap audit and expose unresolved gaps in Grafana.
Gap recovery protects forward monitoring; 90-day backtest still relies on historical backfill as source of truth.
---
## 27. Phase 3 — Token Risk Engine
Goal: block obvious dangerous tokens before signals.
MVP Solana rules: liquidity \>= \$30K; lifecycle \>= DISCOVERY; not 2+ manipulation flags; mint/freeze authority checks; top holder concentration check; LP lock check where available.
DoD: token risk decision within 1–3s; append-only decisions; common and chain-specific JSONB validated through domain sealed types; BLOCK/WATCH/ALLOW visible in Grafana.
---
## 28. Phase 4 — Wallet Intelligence
Goal: generate watchlist with basic point-in-time scoring.
Реализовать: CandidateWalletProfiler, BasicWalletScorer, WalletPnlCalculator with FIFO closed-trade methodology, WalletGraduationService, WatchlistService, append-only `wallet_score_history`, hot snapshot `wallet_scores`.
DoD: 50–200 STRONG wallets if data supports it; FIFO cross-venue closed-trade PnL implemented; profit_factor calculated; open positions excluded from win_rate/profit_factor; false high-winrate wallets classified as NOISE if profit_factor poor; watchlist refresh behavior is verified. Storage and caching are selected in a separate approved change; Redis remains deferred.
---
## 29. Phase 5 — Signal Families + Journal
Goal: generate and record comparable signal families.
Реализовать: SmartWalletBuy, MultiWalletBuy, LiquiditySpike, HolderGrowth, TokenRiskAlert detectors; family scorers; SignalAggregator; SignalReasoningBuilder; versioned signal definitions and configuration.
DoD: 4 entry + 1 avoidance detectors active; every detection persists a versioned candidate and immutable decision-time snapshot; accepted signals reference the definition/configuration version; dedup windows are explicit per family; BLOCK candidates receive shadow outcomes; valid `score` 0–100 and derived `grade`; WATCH_ONLY ENTRY signals capped at 69; reasoning JSONB readable; family_type separation works.
---
## 30. Phase 6 — Position + ExecutionSimulator
Goal: create realistic evaluation behavior without real execution.
Реализовать: VirtualPositionTracker, ExitDecisionEngine with 4 triggers, dynamic friction model, `virtual_positions` table integration.
DoD: virtual positions created for ENTRY signals; AVOIDANCE signals do not create positions; friction haircut stored; state transitions valid.
---
## 31. Phase 7 — Outcome Tracker
Goal: measure actual forward outcomes.
Реализовать: ENTRY Outcome Tracker, AVOIDANCE Outcome Tracker, daily reports, Grafana dashboards.
DoD: outcomes measured for all horizons; terminal no-liquidity policy applied; pricing source/status visible; entry and avoidance not mixed; dashboards readable; TOKEN_RISK_ALERT absent from entry report.
---
## 32. Phase 8 — Backtest + Evidence Report
Goal: use 90-day data to decide what to deepen.
Реализовать: BacktestRunner, EvidenceReportBuilder, HTML report, comparison by family/horizon, decision recommendations.
DoD: all families backtested; report reproducible; entry/avoidance sections separated; clear decision: deepen / pivot / extend / honest exit.
---
## 33. Phase B — Future Base/EVM expansion
This is **not MVP**. Add after Solana Evidence Report if there is a reason.
**Realistic effort:** 4–6 weeks.
Includes: new providers, new normalizers, EVM risk facts, Base data coverage dashboards, chain-specific friction profile, initial Base Evidence Report.
B1. Base provider layer: add Alchemy / Moralis / Chainstack / QuickNode; implement EvmRpcProvider, BlockchainStreamProvider for Base logs, HistoricalIngestProvider for Base history.
B2. EVM normalizers: implement UniswapV3SwapParser, AerodromeSwapParser, ERC20TransferParser. Mapping: transaction hash -\> `TransactionId.value`, canonical ordinal inside the complete `receipt.logs` sequence -\> `EventId.locator`, block number -\> `BlockPosition.value`, block hash remains separate, contract address -\> program/contract identity. Provider-filter positions are not locators.
B3. EVM token risk facts: contract_verified, proxy_upgradeable, owner_privileges, honeypot, buy_tax, sell_tax, blacklist, pause, max_tx, ownership_renounced.
B4. EVM friction model: do not reuse Solana haircut blindly. Include gas cost, pool slippage, MEV risk, DEX route quality, token tax, venue-specific issues.
B5. Reuse the signal/evaluation core: same signal snapshots, outcomes, virtual positions, versioned definitions and Evidence Report structure.
B6. Compare chains carefully: do not compare raw win-rate directly. Compare expectancy_R after chain-specific friction, sample size, drawdown distribution, outlier dependency, signal decay, data quality, comparable lifecycle buckets.
B7. Do not do at Base expansion start: no cross-chain wallet identity linking, bridge flow analysis, cross-chain capital rotation, or multi-chain portfolio execution. Those are Phase C topics.
---
## 34. Phase C — Cross-chain intelligence
This is not MVP and not Phase B.
Possible topics: wallet_aliases, cross_chain_identities, bridge-flow tracking, capital rotation between ecosystems, cross-chain narrative flow, cross-chain smart wallet attribution, cross-chain portfolio risk.
Only start Phase C if Solana or Base evidence is positive and cross-chain behavior becomes a bottleneck.
---
## 35. Phase 9+ after Evidence Report
Phase 9 — deepen top family: Smart Wallet → decay scoring / labels / exit quality; Multi Wallet → cohort detection; Liquidity → liquidity intelligence; Holder Growth → sybil filtering; Token Risk Alert → defensive overlay.
Phase 10 — walk-forward backtest: expand to 12 months, train/test windows, regime breakdown, hard gate expectancy_R \>= 0.30R.
Phase 11 — paper trading: tracked_positions, position_exit_triggers, virtual capital, paper-vs-backtest deviation.
Phase 12 — validation gates: walk-forward positive, paper positive, max drawdown acceptable, deviation acceptable, capital sufficient, venue gate passed.
Phase 13 — capital pipeline: separate project, not parallel with MVP.
Phase 14+ — live readiness: Jupiter/Jito for Solana execution, EVM execution provider if Base is chosen later, MANUAL_CONFIRM, phased live.
---
## 36. Outcome cases
Case 1 — Smart Money works: SMART_WALLET_BUY or MULTI_WALLET_BUY positive → deepen wallet/cohort intelligence.
Case 2 — Non-smart-money entry works: LIQUIDITY_SPIKE or HOLDER_GROWTH positive → pivot to working family.
Case 3 — Only avoidance works: TOKEN_RISK_ALERT positive → defensive overlay or risk product.
Case 4 — Nothing works: extend data, change network, or honest exit.
The architecture remains useful because core is measurement-oriented and chain-ready.
---
## 37. Operational discipline
| Week | Milestone |
| --- | --- |
| 2–2.5 | Foundation runs, gates block LIVE, chain-aware identity complete |
| 4.5 | Solana swaps in DB, 90-day backfill complete |
| 5.5 | Token risk decisions populated |
| 7.5 | Wallet scoring with profit_factor |
| 9 | Entry + avoidance signals recorded |
| 10 | virtual_positions and simulator working |
| 11 | first outcomes measured |
| 12–12.5 | Evidence Report generated |
Backup: daily pg_dump, object storage, Git from first commit, `docs/runbook.md`.
OpEx review monthly: Helius limits, storage growth, provider failures, whether Base/EVM expansion is justified.
---
## 38. Anti-patterns
- Не делать multi-chain реализацию в MVP.
- Не делать Base/EVM parser в Phase 1–8.
- Не делать cross-chain wallet linking in MVP.
- Не хардкодить Solana identity в DB.
- Не использовать `signature` как универсальный primary key.
- Не делать множество Maven-модулей или deployables сразу.
- Не делать 12-month backfill в MVP.
- Не делать full cohort Louvain сразу.
- Не делать auto-trading в первые 6 месяцев.
- Не делать JPA, R2DBC, Spring WebFlux или Vert.x в MVP.
- Не делать Kafka/ClickHouse преждевременно.
- Не делать LLM в critical path.
- Не смешивать entry и avoidance metrics.
- Не использовать win_rate без profit_factor.
- Не пропускать Token Risk Engine.
- Не добавлять execution path до отдельного approved change с необходимыми venue/capital/safety gates.
- Не пропускать явную оценку необходимости partitioning до первой migration каждой high-volume таблицы.
- Не начинать funding-bot параллельно с MVP.
- Не сравнивать Solana и Base по raw win-rate.
- Не писать chain-specific JSONB без sealed type validation.
- Не использовать future prices in backtest.
- Не дропать outcome rows из-за отсутствующей цены или умершего токена.
- Не считать PnL только внутри одного venue для Pump.fun lifecycle.
- Не игнорировать live stream gaps после reconnect.
- Не мигрировать partitioning схему заранее без evidence.
- Не использовать бессрочную дедупликацию `chain + token + family` без временного окна и версии стратегии.
- Не удалять BLOCK/rejected candidates из research dataset; измерять их outcomes отдельно.
- Не создавать верхнеуровневые технические модули `domain`, `persistence`, `provider`; инфраструктура принадлежит вертикальному бизнес-модулю.
- Не публиковать каждый swap через durable Spring Modulith events.
---
## 39. Финальная формула
```plain text
Crypto Research Core, not Solana-only bot.

Solana is first implementation.
Core is chain-ready.

Не строим торгового бота.
Строим signal evaluation system.

Не одна гипотеза.
4 entry + 1 avoidance families.

Не constant friction.
Dynamic friction by liquidity tier.

Не win-rate alone.
Win-rate + profit_factor + avg win/loss.

Не смешанные метрики.
Entry and avoidance are separate.

Не survivorship bias.
Dead tokens are outcomes too.

Не multi-chain MVP.
Chain-ready core now, Base adapter later.

Не cross-chain identity now.
wallet_aliases later if evidence justifies.

Add modules, don't rewrite.
Evidence over architecture.
Outcome over signal.
Evaluation before money.
```
---
## 40. Итоговое позиционирование
**Crypto Research Core v7.8** — synchronous six-module Spring Modulith signal evaluation system: Java 25 + Spring Boot 4.1.1 + Spring Modulith 2.1.1 + PostgreSQL, built as one Maven module and one deployable JAR.
MVP реализует Solana и через 10–12 недель, с conservative buffer до 12.5 недель, должен дать Evidence Report:
- какие entry signal families на Solana имеют real forward edge after dynamic costs;
- какие avoidance signals дают meaningful avoided drawdown;
- стоит ли углублять Smart Money;
- стоит ли pivot в liquidity/holder/risk direction;
- стоит ли позже добавить Base/EVM.
Base/EVM потом добавляются как новые providers + normalizers + chain-specific risk facts.
Core не переписывается, потому что с первого дня заложены `ChainId`, `TransactionId`, `EventId`, `BlockPosition`, chain-aware keys, chain-specific provider boundary, common signal/evaluation layer, versioned validation, future partitioning migration strategy и future wallet aliasing path.
**Реалистичная цель:** месяц 3–4 — Evidence Report; месяц 6–8 — углублённая стратегия в paper или pivot; месяц 9–12 — first manual/live launch only if gates passed.
**Honest expected outcome:** это не обещание \$10K/мес. Это controlled research project, который за 10–12 недель должен дать data-driven ответ и защитить от потери времени/капитала на неподтверждённую гипотезу.
## Deferred from v6 / Future Expansion Map
| Deferred item | Когда добавлять | Evidence gate | Уже подготовлено |
| --- | --- | --- | --- |
| Full cohort detection | Если MULTI_WALLET_BUY показывает edge | expectancy_R ≥ 0.15R, достаточно samples | SignalFamily, strategy_experiments, signal_reasoning |
| 12-month backfill | Если 90d Evidence positive/unclear | нужно проверить regimes/walk-forward | idempotent ingest, partitions, system_state |
| Walk-forward 9 months | После positive 90d backtest | top family passed MVP gate | BacktestRunner, wallet_score_history |
| Jupiter/Jito execution | Только после paper validation | paper positive, capital gate passed | ExecutionProvider, VenueGate, ExecutionMode |
| Funding-bot | После core MVP / Phase 12 | нужен capital pipeline | отдельный проект, не core dependency |
| ML ranking | После накопления сигналов | enough labeled outcomes | signal_outcomes, features, reasoning JSONB |
| Cross-chain support | Если Solana evidence positive/unclear или Base выглядит лучше | reason to compare chains | Chain, EventId, provider abstractions |
| Full position management | В Phase 11 paper trading | top strategy selected | virtual_positions, ExitDecisionEngine |
| Arkham/Nansen labels | Если wallet scoring ограничен без labels | labels improve precision | WalletLabelProvider interface |
| LaserStream + Frankfurt VPS | Если latency становится bottleneck | missed opportunities due latency | BlockchainStreamProvider abstraction |
