<!-- Экспортировано из Notion 2026-09-12. Исходная страница: https://app.notion.com/p/34d7a744d2df809a9cbad9e0037bc4b3?pvs=204 -->

# Crypto Research Core — Roadmap v7.7

**Версия:** v7.7 (Spring Modulith + synchronous Java 25 baseline)<br>**Дата:** 12 сентября 2026<br>**Базис:** v7.6 + approved modular architecture and Maven build<br>**Подход:** Solana-first, not Solana-only. MVP остаётся Solana-focused, но core data model, identity, partitioning strategy и provider boundaries сразу проектируются так, чтобы позже безболезненно добавить Base / Arbitrum / EVM-сети.
---
## 1. Краткая суть
Crypto Research Core — личная research-платформа на Java 25 + Spring Boot + PostgreSQL для **записи on-chain сигналов и измерения их forward outcome**.
Первая цель — не торговля, а **Evidence Report через 10–12 недель**: какие сигналы имеют реальный edge после реалистичных costs, а какие шумят.
**MVP:** Solana-only по реализации.
**Core model:** chain-ready с первого дня.
Это значит:
- в MVP реализуем Solana providers, Solana normalizers, Solana risk facts;
- в domain и БД сразу есть `chain`, `tx_hash`, `event_index`, `block_height`;
- Base/EVM потом добавляются через новые provider/normalizer/risk implementations;
- research/outcome/strategy core не переписывается.
**Главный вопрос MVP:** какие entry signal families на Solana показывают positive expectancy_after_costs на forward 1h/4h/24h windows? И отдельно: какие avoidance signals дают meaningful avoided drawdown?
**Главный принцип:**
```plain text
Core architectural invariants are permanent.
Sophistication is disposable.
Solana is first implementation, not permanent boundary.
Add modules, don't rewrite.
```
---
## 2. Что изменилось от v7.6
| # | Область | v7.6 | v7.7 |
| --- | --- | --- | --- |
| 1 | Architecture | single-module layered monolith with 9 technical packages | Spring Modulith 2.1.1 modular monolith with 8 vertical application modules |
| 2 | Build | Gradle Wrapper | Maven Wrapper; one Maven module and one deployable JAR |
| 3 | Programming model | virtual threads mentioned, framework model implicit | synchronous imperative Spring MVC + Spring Data JDBC on virtual threads |
| 4 | Java 25 | preview APIs only mentioned generally | modern Java 25 features are the default; preview features enabled and isolated behind internal boundaries |
| 5 | Ownership | top-level provider/persistence/domain technical layers | each business module owns its domain, persistence and provider adapters |
| 6 | Delivery | future module split left open | one repository, one process and one database until evidence requires physical separation |
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
6. ExecutionMode gating.
7. CapitalGate / VirtualCapitalPolicy wraps accepted entry signals.
8. Token Risk Engine before signal.
9. Strategy versioning.
10. Idempotency.
11. Observability from day one.
12. Chain-specific JSONB facts validated by domain sealed types.
13. Spring Modulith boundaries are verified in tests; no cyclic dependencies or imports from another module's `internal` packages.
14. Synchronous module APIs; reactive/provider-specific types never leak into business contracts.
---
## 5. Solana-first, not Solana-only
**MVP реализация:** `chain = SOLANA`; providers = Helius, Bitquery, DexScreener, GoPlus; normalizers = Pump.fun, PumpSwap, Raydium / selected major DEX programs; risk facts = Solana mint/freeze authority, LP lock, holders, creator/dev facts.
**Core готовность к Base/EVM:** `chain = BASE / ARBITRUM / ETHEREUM` later; providers = Alchemy / Moralis / Chainstack / QuickNode later; normalizers = UniswapV3, Aerodrome, ERC20 Transfer events later; risk facts = EVM contract ownership, proxy, honeypot, tax, blacklist, pause later.
В Phase 1 мы не реализуем Base, но делаем такие domain/DB решения, чтобы Base не потребовал переписывать wallet identity, token identity, swap identity, signal identity, outcome tables, strategy framework, provider layer, normalizer boundary и token risk JSONB validation approach.
---
## 6. Cross-chain identity stance
В MVP wallet identity строго chain-scoped: `WalletId = chain + address`. Пример: `SOLANA:7xKX...` и `BASE:0x123...` — это разные wallet records.
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
| Module structure | multiple deployables | 8 Spring Modulith modules, 1 Maven module, 1 JAR | physical split only after measured need |
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
## 8. Что НЕ упрощается
| Компонент | Почему сохраняется |
| --- | --- |
| Chain-aware identity | иначе Base/EVM потом потребует болезненную миграцию |
| ExecutionMode + VenueGate + CapitalGate | защита от случайного live |
| Append-only `wallet_score_history` | point-in-time queries |
| Append-only `signal_outcomes` | главный value MVP |
| Postgres partitioning | дёшево сейчас, дорого потом |
| Provider interfaces | новый провайдер = новый adapter |
| Strategy versioning | изменение config = новая версия |
| Token Risk Engine | иначе outcome загрязнён scam/rug токенами |
| Cost-aware Outcome Tracker | иначе measurements слишком оптимистичны |
| Signal Reasoning JSONB | forensic readability |
| Idempotency | повторные backfill/ingest не ломают БД |
| `virtual_positions` | measurement отдельно от trading |
| `profit_factor` | win-rate alone = self-deception |
| JSONB validation through sealed types | чтобы risk facts не стали неконтролируемой схемой |
---
## 9. Стек
- **Language/runtime:** Java 25; records, sealed types, pattern matching, switch expressions, virtual threads and other production-ready Java 25 features where appropriate
- **Preview:** Structured Concurrency and other selected preview features enabled in Maven; preview types stay behind internal abstractions
- **Framework:** Spring Boot 4.1.1 + Spring MVC, synchronous imperative model
- **Modularity:** Spring Modulith 2.1.1
- **Concurrency:** virtual threads for blocking I/O; bounded `StructuredTaskScope` fan-out for independent provider calls
- **БД:** PostgreSQL 16 с monthly partitioning
- **Cache:** Redis 7 + Caffeine
- **Persistence:** Spring Data JDBC / JdbcClient, not JPA or R2DBC
- **Build:** Maven Wrapper; Maven 3.9.x baseline, one Maven module
- **Migrations:** Flyway version managed by Spring Boot dependency management
- **Observability:** Micrometer + Prometheus + Grafana + Logback JSON + correlation ID
- **Test:** Testcontainers, Spring Modulith Test, ArchUnit, JUnit version managed by Spring Boot
- **Not used:** Spring WebFlux, Reactor application pipelines, Vert.x, R2DBC
- **MVP Providers:** Helius WebSocket/RPC, Bitquery, DexScreener, GoPlus
- **Future Providers:** Alchemy/Moralis/Chainstack for Base/EVM, Birdeye, Arkham, Jupiter, Jito
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
- eight logical Spring Modulith application modules;
- no microservices in MVP.
Spring Modulith modules are package-based business boundaries, not separate Maven modules. Physical splitting is allowed only when independent scaling, isolation or deployment becomes a measured requirement.
### 10.2 Application modules
| Module | Responsibility |
| --- | --- |
| `kernel` | Stable value objects: Chain, TokenId, WalletId, EventId, BlockRef, money and time abstractions |
| `governance` | ExecutionMode, VenueGate, CapitalGate; LIVE is physically blocked in MVP |
| `marketdata` | Providers, raw events, normalization, swaps, tokens, prices, metrics and stream-gap recovery |
| `risk` | Token risk facts and BLOCK / WATCH_ONLY / ALLOW decisions |
| `wallet` | FIFO PnL, point-in-time scoring, tiers and watchlist |
| `strategy` | Strategy experiments, detectors, candidates, scoring, reasoning and accepted signals |
| `measurement` | Virtual positions, friction, price valuation and all outcome types |
| `research` | Point-in-time backtest, replay, aggregation and Evidence Report |
### 10.3 Package tree
```plain text
crypto-research-core
├── pom.xml
└── src/main/java/com/yourorg/crc
    ├── CryptoResearchApplication.java
    ├── kernel
    ├── governance
    │   ├── api
    │   └── internal
    │       ├── domain
    │       ├── application
    │       └── infrastructure
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
    ├── strategy
    │   ├── api
    │   └── internal
    │       ├── detector
    │       ├── scoring
    │       └── persistence
    ├── measurement
    │   ├── api
    │   └── internal
    └── research
        ├── api
        └── internal
            ├── backtest
            └── report
```
There are no top-level `domain`, `persistence`, `provider` or `observability` modules. Each vertical module owns its domain model, repositories, database access and external adapters. Observability is implemented inside the owning module and composed at application level.
### 10.4 Allowed dependencies
```plain text
kernel       → none
governance   → kernel
marketdata   → kernel
risk         → kernel, marketdata::api
wallet       → kernel, marketdata::api
strategy     → kernel, marketdata::api, risk::api, wallet::api
measurement  → kernel, marketdata::api, strategy::api, governance::api
research     → public APIs of marketdata, risk, wallet, strategy, measurement
```
Rules:
- every module has `package-info.java` with `@ApplicationModule`;
- exposed contracts live in an `api` package marked with `@NamedInterface("api")`;
- `internal` packages are never imported by another module;
- repositories and persistence entities are not public module contracts;
- no cyclic module dependencies;
- `ApplicationModules.of(CryptoResearchApplication.class).verify()` is a mandatory architecture test;
- each module has focused `@ApplicationModuleTest` integration tests;
- backtest and online processing reuse the same pure strategy/risk evaluation code.
### 10.5 Synchronous programming model
Application and module APIs are synchronous and imperative:
- Spring MVC, not WebFlux;
- Spring Data JDBC / JdbcClient, not R2DBC or JPA;
- no Reactor `Mono` / `Flux`, Vert.x `Future` or provider-specific async types in module contracts;
- blocking HTTP/RPC/database calls execute on virtual threads;
- `StructuredTaskScope` is used for bounded parallel fan-out to independent providers;
- `ScopedValue` may carry immutable task context such as correlation metadata;
- records, sealed interfaces, pattern matching and switch expressions are preferred where they improve the model;
- preview APIs are enabled in Maven compile/test configuration but hidden behind internal project abstractions.
WebSocket providers may use callbacks at the transport boundary because the protocol is event-driven. The adapter immediately writes events to a bounded internal queue; downstream application processing remains synchronous, batched and protected by backpressure.
### 10.6 Module communication
Use direct synchronous API calls when the caller needs an immediate answer: current risk decision, wallet score or point-in-time price.
Use Spring application events only for completed low-frequency business facts such as `RiskDecisionCreated`, `SignalAccepted`, `CandidateRejected`, `OutcomeCompleted` and `ExperimentCompleted`.
Do not put every swap or price tick into Spring Modulith Event Publication Registry. The high-volume ingest path stays inside `marketdata`; durable module events are reserved for business transitions to avoid database write amplification.
---
## 11. Chain-aware domain model
Core types: `Chain`, `ChainFamily`, `ChainAddress`, `TokenId`, `WalletId`, `TransactionId`, `EventId`, `BlockRef`.
Универсальный event identity:
```plain text
chain + tx_hash + event_index
```
Solana mapping: `signature -> tx_hash`, `instruction index / inner instruction index -> event_index`, `slot -> block_height`.
Base/EVM mapping later: `transaction_hash -> tx_hash`, `log_index -> event_index`, `block_number -> block_height`.
---
## 12. Domain types — minimal
Sealed interfaces / enums: `SignalFamilyType`, `EntrySignalFamily`, `AvoidanceSignalFamily`, `TokenRiskDecision`, `ExecutionMode`, `VenueDecisionType`, `TradeSide`, `OutcomeHorizon`, `WalletTier`, `ExitReason`, `ChainSpecificRiskType`, `SignalGrade`, `PriceSourceType`, `PricingStatus`.
Records: `Signal`, `SignalReasoning`, `TokenRiskFacts`, `CommonTokenRiskFacts`, `SolanaTokenRiskFacts`, `PricePoint`, `WalletScore`, `EntryOutcome`, `AvoidanceOutcome`, `VirtualPosition`, `MarketSnapshot`, `NormalizedSwapEvent`, `RawChainEvent`.
Future placeholder: `EvmTokenRiskFacts`.
---
## 13. БД схема — chain-ready минимальный набор
Core tables:
- `raw_chain_events(chain, tx_hash, event_index, block_height, observed_at, provider, payload JSONB, payload_hash, parser_version, ingested_at), PK(chain, tx_hash, event_index, provider), PARTITION BY RANGE(observed_at)`; append-only, payload is never overwritten
- `wallets(chain, address, first_seen, last_active, source, basic_profile), PK(chain, address)`
- `tokens(chain, address, symbol, decimals, created_at, creator_wallet), PK(chain, address)`
- `swaps(chain, tx_hash, event_index, wallet_address, token_address, side, amount_tokens, amount_native, amount_usd, ts, block_height, block_time, venue, program_or_contract, raw_source), PK(chain, tx_hash, event_index), PARTITION BY RANGE(ts)`
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
MVP: `swaps PARTITION BY RANGE(ts)`, `token_metrics PARTITION BY RANGE(ts)`, `token_prices PARTITION BY RANGE(ts)`, `wallet_score_history PARTITION BY RANGE(calculated_at)`.
`token_prices` retention policy: keep high-resolution snapshots for the 90-day MVP Evidence window.<br>Expected scale example: 1,000 tokens × 5-minute snapshots ≈ 288k rows/day, ≈26M rows/90d, manageable with monthly partitions and focused indexes.<br>After Evidence Report, apply downsampling or cold archival if storage/query cost becomes meaningful.
Phase B note: при добавлении Base/EVM оценить миграцию к `PARTITION BY LIST(chain) SUBPARTITION BY RANGE(ts)` или отдельным monthly partitions с chain-aware indexes.
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
Provider API: `BlockchainStreamProvider`, `ChainRpcProvider`, `SolanaRpcProvider`, `EvmRpcProvider`, `HistoricalIngestProvider`, `MarketDataProvider`, `RiskDataProvider`, `WalletLabelProvider`, `ExecutionProvider`, `JitoTipFloorProvider`.
MVP implementations: `marketdata.internal.infrastructure.provider.helius`, `marketdata.internal.infrastructure.provider.bitquery`, `marketdata.internal.infrastructure.provider.dexscreener`, `risk.internal.infrastructure.provider.goplus`.
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
`NormalizedSwapEvent`: `event_id`, `chain`, `tx_hash`, `event_index`, `wallet`, `token`, `side`, `amount_tokens`, `amount_native`, `amount_usd`, `ts`, `block_height`, `venue`, `program_or_contract`.
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
MVP detectors operate on `chain = SOLANA`, but `signals` table and `Signal` domain include chain.
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
## 24. Phase breakdown
| Phase | Срок | Cumulative | Суть |
| --- | --- | --- | --- |
| 1. Foundation + Gates | 1.5–2.5 нед | 2.5 нед | Maven project, Spring Modulith boundaries, DB, chain-aware kernel, gates |
| 2. Data Layer | 2 нед | 4.5 нед | Solana providers, normalizer, 90-day backfill |
| 3. Token Risk Engine | 1 нед | 5.5 нед | Solana risk facts, common risk model |
| 4. Wallet Intelligence | 2 нед | 7.5 нед | Basic scoring + profit_factor |
| 5. Signal Families + Journal | 1.5 нед | 9 нед | 4 entry + 1 avoidance |
| 6. Position + Simulator | 1 нед | 10 нед | virtual_positions, 4 triggers |
| 7. Outcome Tracker | 1 нед | 11 нед | forward outcomes |
| 8. Backtest + Evidence Report | 1.5 нед | 12.5 нед | first decision report |
Primary commitment: 10–12 weeks. Conservative expectation with chain-ready overhead: up to 12.5 weeks.
---
## 25. Phase 1 — Foundation + Gates
Goal: project поднимается, БД готова, live физически невозможен, domain уже chain-ready.
Реализовать: Maven Wrapper single-module; Spring Boot 4.1.1; Spring Modulith 2.1.1; 8 application modules with explicit allowed dependencies; synchronous Spring MVC / Spring Data JDBC model; Java 25 virtual threads and isolated preview configuration; chain-aware kernel (`Chain`, `TokenId`, `WalletId`, `TransactionId`, `EventId`, `BlockRef`); Flyway migrations with partitions/indexes/chain-aware constraints, including `raw_chain_events`, `signal_candidates` and mandatory strategy-version linkage; Docker Compose; ExecutionMode state; VenueGate; CapitalGate; provider interfaces; Modulith verification tests; ArchUnit; Actuator; structured logging.
DoD: Maven build passes; infra starts; health OK; `ApplicationModules.verify()` passes; module graph has no cycles; internal packages are not imported cross-module; LIVE is blocked by code; chain-aware tables exist; domain packages have no Spring/persistence/provider dependencies.
---
## 26. Phase 2 — Data Layer
Goal: clean reproducible Solana swap data.
Реализовать: Helius WebSocket + RPC; Bitquery historical backfill; DexScreener market data; GoPlus risk data; append-only raw event persistence before normalization; Solana normalizers; SwapPersister with `ON CONFLICT (chain, tx_hash, event_index)`; Token discovery; Sol price service; `token_prices` snapshots; 90-day full backfill for selected Solana programs; coverage/gaps dashboard.
DoD: 24h stream without unresolved critical gaps; reconnect gap recovery tested; 90-day Solana backfill complete; raw payload and parser version available for replay; no duplicates; parse error \<1%; coverage/gaps visible in Grafana; data freshness \<60s; price snapshots available for outcome windows.
---
## 26.1 Stream Gap Detection & Recovery
Goal: avoid silent loss of real-time events.
Policy:
- persist last processed Solana slot/block height in `system_state`;
- on reconnect, compare last processed slot with current slot;
- backfill missed slot range through RPC / historical provider;
- deduplicate recovered events by `chain + tx_hash + event_index`;
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
DoD: 50–200 STRONG wallets if data supports it; FIFO cross-venue closed-trade PnL implemented; profit_factor calculated; open positions excluded from win_rate/profit_factor; false high-winrate wallets classified as NOISE if profit_factor poor; Redis watchlist updated every 6h.
---
## 29. Phase 5 — Signal Families + Journal
Goal: generate and record comparable signal families.
Реализовать: SmartWalletBuy, MultiWalletBuy, LiquiditySpike, HolderGrowth, TokenRiskAlert detectors; SmartWalletBuyScorer, MultiWalletBuyScorer, LiquiditySpikeScorer, HolderGrowthScorer, TokenRiskAlertScorer; SignalAggregator; SignalReasoningBuilder; strategy experiments versioning.
DoD: 4 entry + 1 avoidance detectors active; every detection persists a versioned candidate; accepted signals persist with chain and `strategy_experiment_id`; dedup windows are explicit per family; BLOCK candidates receive shadow outcomes; valid `score` 0–100 and derived `grade`; WATCH_ONLY ENTRY signals capped at 69; reasoning JSONB readable; family_type separation works.
---
## 30. Phase 6 — Position + ExecutionSimulator
Goal: create realistic measurement layer.
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
B2. EVM normalizers: implement UniswapV3SwapParser, AerodromeSwapParser, ERC20TransferParser. Mapping: transaction_hash -\> tx_hash, log_index -\> event_index, block_number -\> block_height, contract_address -\> program_or_contract.
B3. EVM token risk facts: contract_verified, proxy_upgradeable, owner_privileges, honeypot, buy_tax, sell_tax, blacklist, pause, max_tx, ownership_renounced.
B4. EVM friction model: do not reuse Solana haircut blindly. Include gas cost, pool slippage, MEV risk, DEX route quality, token tax, venue-specific issues.
B5. Reuse research core: same signals table, outcomes, virtual positions, strategy experiments, Evidence Report structure.
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
- Не пропускать ExecutionMode/VenueGate/CapitalGate.
- Не пропускать partitioning.
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
Строим measurement system.

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
Measurement before money.
```
---
## 40. Итоговое позиционирование
**Crypto Research Core v7.7** — synchronous Spring Modulith measurement system: Java 25 + Spring Boot 4.1.1 + Spring Modulith 2.1.1 + PostgreSQL, built as one Maven module and one deployable JAR.
MVP реализует Solana и через 10–12 недель, с conservative buffer до 12.5 недель, должен дать Evidence Report:
- какие entry signal families на Solana имеют real forward edge after dynamic costs;
- какие avoidance signals дают meaningful avoided drawdown;
- стоит ли углублять Smart Money;
- стоит ли pivot в liquidity/holder/risk direction;
- стоит ли позже добавить Base/EVM.
Base/EVM потом добавляются как новые providers + normalizers + chain-specific risk facts.
Core не переписывается, потому что с первого дня заложены `chain`, `tx_hash`, `event_index`, chain-aware PK, chain-specific provider boundary, common research/outcome layer, JSONB validation through sealed domain types, future partitioning migration strategy, future wallet aliasing path.
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
