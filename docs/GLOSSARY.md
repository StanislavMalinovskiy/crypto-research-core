# Crypto Research Core — Wiki терминов
**Версия:** 1.2
**Дата:** 12 сентября 2026
**Формат:** пояснительный глоссарий, включающий текущие и исторические термины проекта.
> **Важно:** области ответственности нормативных источников и правила конфликтов определены в [AGENTS.md](../AGENTS.md). Историческая multi-module структура вынесена в [архив v5](archive/LEGACY_V5_ARCHITECTURE_MAPPING.md). Термины будущих возможностей не являются спецификацией реализации без локальной пометки Target или Deferred и соответствующего OpenSpec change.
---
## 1. Общие термины проекта
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Smart Money Platform | Личная платформа для поиска, проверки и мониторинга торговых гипотез по on-chain данным. | Весь проект |
| v7.7 | Текущая версия плана: synchronous Spring Modulith modular monolith, research-first, measurement-first, chain-ready core, real execution only through gates. | Roadmap — source of truth |
| Research-first | Сначала исследуем и проверяем гипотезы, а не сразу торгуем реальными деньгами. | Философия проекта |
| Production-core | Ядро системы пишется качественно сразу: домен, БД, ingestion, backtest, paper, observability. | Архитектура |
| Alpha | Торговое преимущество: сигнал или закономерность, которая потенциально даёт прибыль. | Стратегии, research |
| Alpha-гипотеза | Предположение, что конкретный сигнал может зарабатывать. | Strategy experiments |
| Disposable alpha | Торговые гипотезы можно менять и удалять, если они не прошли проверку. | Философия v5 |
| Core is permanent | Ядро должно быть стабильным и переиспользуемым для разных стратегий. | Архитектурный принцип |
| Execution is gated | Реальное исполнение сделок запрещено, пока будущий governance change не определит и не проверит необходимые политики. | Target governance capability |
| Capital is protected | Любой сигнал проходит через risk/capital manager до позиции. | Capital Manager |
| Data first | Сначала качественные данные, потом стратегии. | Roadmap |
| Research second | После данных проверяются гипотезы. | Roadmap |
| Paper third | После backtest стратегия проверяется в реальном времени без денег. | Paper Trading |
| Execution later | Реальные сделки — не цель MVP, а поздний этап. | Execution Readiness |
| Legal gate before money | Перед реальными деньгами проверяются юридические и venue-ограничения. | Legal / Venue Gate |
---
## 2. Режимы исполнения и gates
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| ExecutionMode | Целевой режим работы: research, paper или live; точный контракт пока не утверждён. | Future governance change |
| BACKTEST | Проверка стратегии на исторических данных. | Backtest Engine |
| PAPER | Торговля на виртуальном капитале в реальном времени. | Paper Trading |
| ALERT_ONLY | Система только показывает сигнал, но не исполняет сделку. | Dashboard, alerts |
| MANUAL_CONFIRM | Сделка возможна только после ручного подтверждения. | Будущий semi-auto mode |
| LIVE | Реальное автоматическое или полуавтоматическое исполнение. | Только после gates |
| Gate | Контрольная точка, которая разрешает или запрещает следующий этап. | Validation pipeline |
| Legal Gate | Проверка юридической допустимости реального исполнения. | Перед real money |
| Capital Gate | Проверяет, можно ли рисковать капиталом по этому сигналу. | Capital Manager |
| MANUAL_ONLY | Можно исполнять только вручную после подтверждения. | Semi-auto execution |
| LIVE_ALLOWED | Реальное исполнение разрешено. В MVP должно быть недоступно. | Поздний этап |
---
## 3. Данные, Solana и on-chain
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| On-chain data | Данные, которые видны в блокчейне: транзакции, swaps, кошельки, токены. | Data Layer |
| Solana | Блокчейн, с которого начинается проект. | Ingest, smart money |
| Wallet | Адрес кошелька в блокчейне. | Wallet Intelligence |
| Token | Криптоактив/монета в сети. | Token Intelligence |
| Swap | Обмен одного актива на другой через DEX/AMM. | `swaps` table |
| Signature | Уникальный идентификатор транзакции в Solana. | Logs, idempotency |
| Block slot | Порядковая позиция блока/слота в Solana. | Ordering, indexes |
| Program ID | Адрес smart contract/program в Solana. | Parsers, filters |
| Pump.fun | Площадка/протокол запуска мемкоинов на Solana. | Ingest source |
| PumpSwap | AMM/обменный слой, связанный с pump.fun экосистемой. | Swap parsing |
| DEX | Децентрализованная биржа. | Solana trading |
| AMM | Автоматический маркет-мейкер, пул ликвидности вместо стакана заявок. | DEX, CLMM |
| Liquidity | Доступная ликвидность токена/пула; влияет на slippage. | Token Risk |
| Holders | Количество держателей токена. | Token metrics |
| Top-10 holders | Доля токена у 10 крупнейших держателей. | Risk filter |
| Dev wallet | Кошелёк создателя/команды токена. | Rug risk |
| Creator wallet | Кошелёк, создавший токен. | Token analysis |
| Mint authority | Право создавать новые токены. Если активно — риск. | Token Risk |
| Freeze authority | Право замораживать token accounts. Если активно — риск. | Token Risk |
| Raw event | Сырое сообщение от провайдера до нормализации. | Ingest |
| Normalized event | Приведённое к внутреннему формату событие. | Domain pipeline |
| Immutable raw data | Сырые данные сохраняются без изменения, чтобы можно было перепроверить парсинг. | Data architecture |
| Backfill | Загрузка исторических данных за прошлый период. | Data Layer |
| Incremental backfill | Дозагрузка истории для новых кошельков/токенов. | Ingest |
| Data freshness | Насколько свежие данные в системе. | Observability |
---
## 4. Провайдеры и внешние источники
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Provider | Внешний источник данных или сервис. | Provider API |
| Provider abstraction | Интерфейс, который скрывает конкретного поставщика данных. | Архитектура |
| Helius | Провайдер Solana API / событий. | Real-time ingest |
| Bitquery | Источник исторических и аналитических blockchain data. | Backfill |
| DexScreener | Источник рыночных данных по токенам и пулам. | Market data |
| Birdeye | Источник цен, метрик токенов и кошельков на Solana. | Token/wallet intelligence |
| GoPlus | Security/risk API для токенов. | Token Risk |
| Arkham | Источник labels/entity attribution по кошелькам. | Enrichment |
| Nansen | Источник smart money labels/cohorts. | Enrichment/candidates |
| Dune | Платформа SQL-аналитики по blockchain data. | External datasets |
| BlockchainStreamProvider | Интерфейс для real-time blockchain событий. | Provider API |
| HistoricalIngestProvider | Интерфейс для исторической загрузки данных. | Provider API |
| MarketDataProvider | Интерфейс для цен, ликвидности и market metrics. | Provider API |
| RiskDataProvider | Интерфейс для risk/security фактов по токенам. | Provider API |
| WalletLabelProvider | Интерфейс для labels кошельков. | Enrichment |
| ExecutionProvider | Интерфейс для будущей оценки/исполнения swaps. | Later execution |
| Circuit Breaker | Защита от постоянно падающего провайдера. | Resilience |
| Retry | Повторный запрос при временной ошибке. | Provider decorator |
| Timeout | Максимальное время ожидания ответа. | Provider decorator |
| Bulkhead | Ограничение параллельных вызовов к провайдеру. | Provider decorator |
| Rate limit | Лимит запросов к API. | Provider control |
---
## 5. База данных и storage
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| PostgreSQL | Основная реляционная БД проекта. | Persistence |
| Flyway | Управление миграциями БД. | Persistence |
| Redis | **Deferred:** возможный cache/queue/state storage; сейчас не выбран. | Future infrastructure |
| Partitioning | **Target decision:** разделение high-volume таблицы после обоснования объёма и query patterns. | Owning persistence change |
| BRIN index | Компактный индекс для больших таблиц с упорядоченными данными. | `swaps.ts`, slot |
| JSONB | JSON-данные в PostgreSQL с возможностью индексации. | Reasoning, evidence |
| Materialized view | Сохранённый результат тяжёлого SQL-запроса. | Wallet stats |
| `wallets` | Таблица кошельков. | Persistence |
| `tokens` | Таблица токенов. | Persistence |
| `swaps` | Таблица обменов. | Core data |
| `token_metrics` | Исторические метрики токенов. | Token Intelligence |
| `wallet_scores` | Текущее качество кошельков. | Wallet Intelligence |
| `wallet_score_history` | История скоринга кошельков для point-in-time backtest. | Backtest correctness |
| `signals` | Созданные торговые/аналитические сигналы. | Signal Aggregation |
| `signal_reasoning` | Объяснение причин сигнала. | Explainability |
| `paper_trades` | **Historical/deferred name:** возможные виртуальные сделки paper trading; текущая схема не утверждена. | Future PAPER change |
| `paper_fills` | **Historical/deferred name:** возможные детали виртуального исполнения; текущая схема не утверждена. | Future PAPER change |
| `backtest_runs` | **Target draft name:** метаданные запусков backtest; текущая схема не утверждена. | Future research change |
| `capital_events` | **Target draft name:** события capital policy; текущая схема не утверждена. | Future governance/measurement change |
| `system_state` | **Target draft name:** технический прогресс процессов; ownership и схема не утверждены. | Future owning-module change |
| `owner_clusters` | **Historical/deferred name:** возможные кластеры связанных кошельков; не MVP schema. | Future wallet research |
| `risk_filter_decisions` | **Historical/deferred name:** прежнее имя решений risk filters. | Future risk change |
| `strategy_experiments` | Версионированные research-гипотезы. | Research module |
| `execution_simulations` | **Historical/deferred name:** прежняя модель расчётных издержек исполнения. | Future measurement change |
| `venue_policy_decisions` | **Historical/deferred name:** прежнее имя решений legal/venue gate. | Future governance change |
| Idempotency | Повторный запуск не должен создавать дубликаты или ломать состояние. | Ingest, backfill |
| `ON CONFLICT DO NOTHING` | SQL-паттерн для безопасной вставки без дублей. | Persistence |
---
## 6. Smart money и кошельки
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Smart Money | Кошельки/участники, которые потенциально торгуют лучше среднего. | Wallet Intelligence |
| Smart wallet | Конкретный кошелёк, который система считает потенциально сильным. | Scoring |
| Wallet Intelligence | Слой анализа кошельков: PnL, alpha, decay, tier. | Phase 4 |
| Wallet profile | Профиль кошелька: активность, поведение, история. | Domain |
| Wallet score | Числовая оценка качества кошелька. | Scoring |
| Wallet tier | Категория кошелька: CORE, STRONG, PROMISING, SEED, NOISE. | Strategy filters |
| CORE wallet | Самые сильные кошельки после проверки. | Signal source |
| STRONG wallet | Хорошие кошельки, но слабее CORE. | Signal source |
| PROMISING wallet | Перспективные, но пока мало данных. | Watchlist |
| SEED wallet | Кандидат, ещё не подтверждённый forward alpha. | Discovery |
| NOISE wallet | Шумовой или плохой кошелёк. | Filtered out |
| Discovery | Поиск кандидатов в smart wallets. | Wallet Intelligence |
| Seed discovery | Первичный поиск кандидатов. В v5 не является source of truth. | Candidate generation |
| External list | Список кошельков из Nansen/Arkham/Dune и т.п. | Enrichment / candidates |
| Early Winner Finder | Поиск кошельков, которые рано покупали успешные токены. | Candidate generator |
| Exit Master Finder | Поиск кошельков, которые хорошо выходили из позиций. | Candidate generator |
| Triangulated discovery | Старый v4-подход: пересечение нескольких источников кандидатов. | Reworked in v5 |
| CandidateWalletProfiler | Профилирует кошельки-кандидаты. | Wallet Intelligence |
| PointInTimeWalletProfiler | Строит профиль кошелька только на данных, доступных на момент T. | Backtest correctness |
| ForwardAlphaEvaluator | Проверяет, была ли прибыль после обнаружения кошелька/сигнала. | Wallet validation |
| WalletGraduationService | Повышает/понижает кошелёк по tier после проверки. | Scoring lifecycle |
| Realized PnL | Уже зафиксированная прибыль/убыток по закрытым сделкам. | Wallet metrics |
| Unrealized PnL | Нереализованная прибыль/убыток по открытым позициям. | Wallet metrics |
| Forward alpha | Доходность после момента сигнала, а не задним числом. | Главная проверка smart money |
| Decay | Затухание ценности старого сигнала/кошелька со временем. | Wallet scoring |
| Half-life | Период, за который вес старого сигнала уменьшается примерно вдвое. | Decay scoring |
| Survivorship bias | Ошибка: выбирать только победителей прошлого и думать, что они предскажут будущее. | Backtest risk |
| Selection on future return | Ошибка, когда кошелёк выбирается по будущему результату, который в реальности был неизвестен. | Discovery risk |
---
## 7. Кластеры, cohorts и связи кошельков
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Owner cluster | Группа кошельков, которые, вероятно, связаны одним владельцем/сущностью. | Intent, clustering |
| Wallet cluster | Любая группа связанных кошельков. | Clustering |
| Cohort | Группа кошельков с похожим поведением, не обязательно один владелец. | Cohort signals |
| Cohort signal | Сигнал от группы кошельков, действующих синхронно. | Strategy Framework |
| Co-trading graph | Граф кошельков, которые часто покупают одни и те же токены в близкое время. | Wallet clustering |
| Funding graph | Граф переводов/пополнений между кошельками. | Owner detection |
| Common token graph | Связь кошельков через общие токены. | Similarity detection |
| Temporal correlation | Синхронность действий по времени. | Cohort detection |
| Funding relationship | Связь кошельков через переводы средств. | Owner cluster |
| Internal transfer | Перевод внутри одного владельца/кластера, не реальный trading signal. | Intent Classifier |
| ClusterEvidence | Факты, подтверждающие связь кошельков. | Owner clusters |
| CohortAccumulationStrategy | Стратегия, ищущая накопление токена группой кошельков. | Future strategies |
| Insider cluster | Группа кошельков, связанная с создателем/инсайдерами токена. | Token Risk |
| Deployer-funded wallets | Кошельки, профинансированные создателем токена. | Manipulation detection |
---
## 8. Token risk и manipulation detection
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Token Intelligence | Сбор фактов по токену: liquidity, holders, price, volume, authorities. | Phase 3 |
| Token Risk Engine | Решает, насколько токен опасен для сигнала. | Phase 3 |
| TokenRiskScore | Числовая оценка риска токена. | Risk pipeline |
| TokenRiskDecision | Решение по токену: ALLOW, WATCH_ONLY, BLOCK. | Token Risk |
| ALLOW | Токен можно пропустить дальше. | Risk decision |
| WATCH_ONLY | Токен можно наблюдать, но не торговать. | Research / alerts |
| BLOCK | Сигнал по токену блокируется. | Risk decision |
| Risk flag | Конкретный признак риска. | Domain |
| Low liquidity | Мало ликвидности, высокая вероятность плохого исполнения. | Risk filter |
| High concentration | Большая доля токена у малой группы держателей. | Risk filter |
| Honeypot | Токен, который можно купить, но сложно/нельзя продать. | Security check |
| Rug pull | Сценарий, когда создатель/инсайдеры выводят ликвидность или обваливают цену. | Token Risk |
| Liquidity pull | Вывод ликвидности из пула. | Rug risk |
| Wash trading | Искусственный объём через сделки между связанными кошельками. | Manipulation detection |
| Sniper | Кошелёк/бот, покупающий токен в первые секунды/блоки запуска. | Insider/sniper detection |
| Early snipers | Ранние покупатели сразу после запуска токена. | Token Risk |
| Holder growth quality | Качество роста числа holders: реальные новые держатели или связанные кошельки. | Token Risk |
| Creator previous rugs | История плохих запусков у creator wallet. | Risk filter |
| Abnormal volume pattern | Странный объём, похожий на манипуляцию. | Manipulation detection |
| Authority revoked | Права mint/freeze отключены. Обычно снижает риск. | Token Risk |
| Authority active | Права mint/freeze активны. Обычно повышает риск. | Token Risk |
| Recent launch | Недавно запущенный токен. Риск выше. | Risk flag |
---
## 9. Intent Classifier
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Intent | Предполагаемая причина действия кошелька. | Intent Classifier |
| Intent Classifier | Классифицирует swap: реальный вход, арбитраж, rebalance, transfer и т.п. | Phase 4 / strategy filter |
| REAL_ENTRY | Настоящий торговый вход в позицию. | Strategy input |
| HEDGE | Сделка для хеджирования другой позиции. | Intent |
| REBALANCE | Перераспределение портфеля, не alpha-сигнал. | Intent |
| INTERNAL_TRANSFER | Внутреннее движение между связанными кошельками. | Intent |
| ARBITRAGE | Быстрая сделка ради ценовой разницы. | Intent |
| LIQUIDITY_TEST | Маленькая пробная покупка для теста ликвидности. | Intent |
| Intent confidence | Уверенность классификатора в типе intent. | Intent output |
| Context | Данные вокруг события: кошелёк, токен, кластер, недавние swaps. | StrategyContext |
| False positive | Ложный сигнал, который выглядит полезным, но на деле шум. | Quality metrics |
| Noise | Данные/события без полезного торгового смысла. | Filtering |
---
## 10. Стратегии и сигналы
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Strategy | Правило/алгоритм, который генерирует сигнал. | Strategy Framework |
| Strategy Framework | Единый механизм подключения и запуска стратегий. | Phase 6 |
| Entry strategy | Стратегия входа в позицию. | Strategy API |
| Exit strategy | Стратегия выхода из позиции. | Exit Engine |
| Strategy plugin | Target concept: отдельная реализация стратегии внутри модуля `strategy`; отдельного Maven-модуля нет. | `strategy` |
| Strategy version | Версия стратегии, чтобы сравнивать результаты корректно. | Research |
| StrategyConfig | Конфигурация стратегии. | Strategy API |
| StrategyConfigSnapshot | Снимок настроек на момент backtest/paper. | Reproducibility |
| StrategyExperiment | Исследовательская гипотеза с версией, конфигом и статусом. | Research module |
| Hypothesis | Описание предположения, которое проверяет стратегия. | Research |
| SmartWalletRadarV1 | Первая стратегия: сигнал от сильных кошельков после фильтров. | MVP strategy |
| StrategyContext | Контекст для entry strategy. | Strategy API |
| ExitContext | Контекст для exit strategy. | Exit API |
| StrategySignal | Сигнал от стратегии с причиной и confidence. | Domain |
| SignalCandidate | Кандидат в сигнал до risk/capital filters. | Strategy pipeline |
| Signal | Финальный сигнал после агрегации и фильтров. | Signal Aggregation |
| Signal side | Направление: BUY/SELL/EXIT и т.п. | Domain |
| Confidence | Уверенность системы в сигнале. | Signal scoring |
| SignalReason | Объяснение, почему сигнал появился. | Explainability |
| Signal Reasoning | Структурированное объяснение факторов, рисков и аналогов. | `signal_reasoning` |
| ReasoningFactor | Один фактор в объяснении сигнала. | Signal Reasoning |
| Signal Aggregator | Склеивает сигналы от разных стратегий и считает итоговую уверенность. | Phase 7 |
| Deduplication | Удаление дублей сигналов по одному токену/стратегии. | Signal pipeline |
| Conflict detection | Поиск конфликтов: одна стратегия BUY, другая EXIT. | Signal Aggregator |
| Crowding penalty | Штраф за слишком очевидный/переполненный сигнал. | Signal scoring |
| Risk penalty | Штраф за риск токена/ликвидности/концентрации. | Signal scoring |
| Historical analogs | Похожие прошлые сигналы и их результат. | Signal Reasoning |
| Dry-run | Стратегия работает в реальном времени, но только логирует решения. | Strategy validation |
---
## 11. Backtest и validation
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Backtest | Проверка стратегии на истории. | Phase 9 |
| Backtest Engine | Модуль исторического тестирования. | `backtest` |
| Point-in-time backtest | Backtest, который использует только данные, доступные на тот момент. | Anti-bias |
| Cost-aware backtest | Backtest с учётом издержек исполнения. | Execution Simulator |
| Walk-forward | Тестирование по периодам: обучили/выбрали на прошлом, проверили на будущем. | Validation |
| Out-of-sample | Проверка на данных, не использованных при выборе стратегии. | Validation |
| Sample size | Количество сделок/сигналов в тесте. | Statistical quality |
| Regime | Рыночный режим: бычий, медвежий, мемкоин-сезон, низкая волатильность и т.п. | Backtest analysis |
| Expectancy | Ожидаемый результат сделки: средний плюс/минус с учётом вероятностей. | Главная метрика |
| Expectancy after costs | Expectancy после slippage, fees, latency, MEV и failed tx. | Main gate |
| Win-rate | Процент прибыльных сделок. В v5 вторичная метрика. | Metrics |
| Profit factor | Отношение суммарной прибыли к суммарному убытку. | Backtest metrics |
| Max drawdown | Максимальная просадка капитала. | Risk metric |
| Time to recovery | Сколько времени стратегия восстанавливается после просадки. | Risk metric |
| Median ROI | Медианная доходность сделки; защищает от искажения одной удачной сделкой. | Metrics |
| Avg win | Средняя прибыльная сделка. | Expectancy |
| Avg loss | Средняя убыточная сделка. | Expectancy |
| P95 loss | Убыток в худших 5% случаев. | Tail risk |
| Tail risk | Риск редких, но больших потерь. | Risk analysis |
| Sharpe ratio | Доходность с поправкой на волатильность. | Validation |
| Paper/backtest deviation | Насколько paper trading отличается от backtest. | Reality check |
| Strategy gate | Условие допуска стратегии на следующий этап. | Validation Gates |
| Reproducibility | Повторный запуск теста даёт тот же результат. | Backtest quality |
---
## 12. Execution Simulator и издержки
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Execution Simulator | Моделирует, как сделка исполнилась бы в реальности. | Phase 8 |
| Slippage | Разница между ожидаемой и фактической ценой исполнения. | Execution cost |
| Latency | Задержка между сигналом и фактическим исполнением. | Execution Simulator |
| Failed transaction | Транзакция не исполнилась/не попала в блок. | Execution risk |
| MEV | Извлечение прибыли валидаторами/ботами за счёт порядка транзакций. | Execution risk |
| MEV haircut | Штраф в модели на потенциальный MEV-ущерб. | Simulator |
| Sandwich attack | MEV-сценарий: бот покупает до тебя и продаёт после тебя, ухудшая твою цену. | Execution risk |
| Priority fee | Дополнительная комиссия за приоритет транзакции. | Solana execution |
| Jito tip | Чаевые/плата за попадание в более выгодный execution path на Solana. | Later execution |
| Partial fill | Сделка исполнилась не полностью. | Execution Simulator |
| Fill price | Цена, по которой сделка считается исполненной. | Paper/backtest |
| Paper fill | Виртуальное исполнение сделки в paper mode. | Paper Trading |
| Execution friction | Все издержки исполнения вместе: slippage, fees, latency, failed tx, MEV. | Cost model |
| ExecutionCostAggregator | Собирает все издержки в один итоговый cost. | Execution Simulator |
| Route quality | Качество маршрута swap через DEX/aggregator. | Future execution |
| Jupiter | Solana swap aggregator. Может использоваться позже для execution/quotes. | Future execution |
| Jupiter Ultra | Более advanced execution/route слой Jupiter. | Future execution |
| Yellowstone gRPC | Быстрый streaming-интерфейс Solana данных. | Future low-latency ingest |
| LaserStream | Helius low-latency stream; потенциально позже. | Future optimization |
| Latency-war | Гонка за миллисекунды против профессиональных ботов. | Anti-goal for MVP |
---
## 13. Paper Trading, позиции и выходы
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Paper Trading | Проверка стратегии на виртуальных деньгах в real-time. | Phase 12 |
| Virtual capital | Виртуальный капитал для paper trading. | Capital Manager |
| Paper position | Виртуальная открытая позиция. | Paper Trading |
| Paper PnL | Виртуальная прибыль/убыток. | Dashboard |
| Position | Открытая или закрытая сделка по токену. | Domain |
| Position size | Размер позиции. | Capital Manager |
| Entry plan | План входа в позицию. | Domain |
| Exit plan | План выхода из позиции. | Domain |
| Exit Engine | Модуль выхода из позиций. | Phase 10 |
| Hard stop-loss | Жёсткий выход при достижении убытка. | Exit policy |
| Take profit | Фиксация прибыли при достижении цели. | Exit policy |
| Partial take-profit | Продажа части позиции при прибыли. | Exit policy |
| Trailing stop | Stop-loss, который двигается вслед за ростом цены. | Exit policy |
| Time stop | Выход, если позиция слишком долго не даёт результата. | Exit policy |
| Liquidity collapse exit | Выход при резком ухудшении ликвидности. | Exit policy |
| SmartWalletExitPolicy | Выход при признаках выхода сильных кошельков. | Exit Engine |
| CohortExitPolicy | Выход при признаках выхода группы кошельков. | Exit Engine |
| Staged exit | Выход частями, а не одной сделкой. | Exit logic |
| Exit problem | Главная проблема copy trading: вход скопировать проще, чем правильный выход. | Strategy risk |
| Open position | Открытая позиция. | Paper/live |
| Closed position | Закрытая позиция. | PnL |
---
## 14. Risk, Capital Manager и портфель
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Risk Filter | Проверка, отсекающая опасные сигналы до позиции. | `risk` |
| Risk Filter Pipeline | Цепочка risk filters. | Phase 7/12 |
| RiskContext | Контекст для risk filter. | Risk API |
| MinLiquidityFilter | Фильтр минимальной ликвидности. | Risk pipeline |
| MinHoldersFilter | Фильтр минимального числа holders. | Risk pipeline |
| Top10ConcentrationFilter | Фильтр концентрации у крупнейших holders. | Risk pipeline |
| DevOwnershipFilter | Фильтр доли dev/creator кошельков. | Risk pipeline |
| HoneypotFilter | Блокирует потенциальные honeypot-токены. | Risk pipeline |
| MintAuthorityFilter | Проверяет mint authority. | Risk pipeline |
| Capital Manager | Модуль, который решает размер позиции и ограничения риска. | Phase 11 |
| PositionSizer | Рассчитывает размер позиции. | Capital Manager |
| Risk per trade | Максимальный риск на одну сделку. | Sizing |
| DailyLossCircuitBreaker | Останавливает торговлю при дневном лимите убытка. | Capital Manager |
| Circuit breaker | Автоматическая остановка при опасном состоянии. | Risk control |
| SectorExposureMonitor | Следит, чтобы портфель не был перегружен одним сектором. | Capital Manager |
| Sector exposure | Доля капитала в одном секторе/нарративе. | Portfolio risk |
| Venue-specific pause | Остановка торговли на конкретной площадке. | Capital/Venue risk |
| Strategy-specific pause | Остановка конкретной стратегии. | Capital Manager |
| OpenPositionsTracker | Отслеживает открытые позиции. | Capital Manager |
| CapitalState | Текущее состояние капитала. | Domain |
| VirtualCapitalState | Состояние виртуального капитала. | Paper Trading |
| RealCapitalState | Состояние реального капитала. В MVP не используется. | Future live |
| ApprovalDecision | Решение: можно ли открыть позицию и каким размером. | Capital Manager |
| Capital event | Запись решения или изменения капитала. | Audit trail |
---
## 15. Архитектура Java / Spring Modulith
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Java 25 | Текущая версия Java проекта. Stable virtual threads используются для подходящего blocking I/O; preview-функции не включены. | Current baseline |
| Spring Boot 4.1.1 | Текущая стабильная версия Spring Boot, совместимая с Java 25. | Current baseline |
| Spring MVC | Синхронный REST/API и control-plane поверх virtual threads; WebFlux не используется. | Application framework |
| Vert.x / WebFlux / Reactor | Не используются в проекте. Прикладная модель синхронная и императивная; транспортный WebSocket callback изолирован внутри adapter. | Explicitly excluded |
| Maven single-module | Один Maven-модуль, один deployable JAR и восемь логических Spring Modulith application modules. | Current build structure |
| Module-local `domain` package | Чистый домен внутри owning vertical module, без Spring/JPA/provider dependencies. | Architecture |
| Module-owned persistence | Каждый вертикальный модуль владеет своими repositories, SQL, Flyway migrations и RowMapper; общего persistence-модуля нет. | Data access |
| Module-owned provider adapters | Выбранные будущими changes adapters принадлежат owning-модулям; наружу выставляются только domain-neutral contracts. | DIP |
| Application root | `io.cryptoresearch.CryptoResearchApplication` запускает единый Spring Boot JAR. | Current application |
| Record | Immutable data carrier в Java. | Domain objects |
| Sealed interface | Ограниченная иерархия типов. | Domain modeling |
| Value object | Доменный тип для значения: адрес, сумма, score и т.п. | Domain |
| ArchUnit | Тесты архитектурных правил. | Module boundaries |
| Constructor injection | Внедрение зависимостей через конструктор. | Spring style |
| Spring Data JDBC | Предсказуемый доступ к БД без JPA magic. | Persistence |
| JPA | ORM, которую проект намеренно не использует. | Anti-pattern |
| Spring Modulith application events | Используются только для завершённых низкочастотных business facts; high-volume swaps остаются внутри `marketdata`. | Module integration |
| Domain event | Событие внутри системы: SwapPersisted, SignalCreated и т.п. | Event-driven modules |
| Composition root | Корень Spring Boot application, а не отдельный Maven-модуль или top-level `app` package. | `io.cryptoresearch` |
| DIP | Dependency Inversion Principle: бизнес зависит от интерфейсов, не от vendor-классов. | Architecture |
| SRP | Single Responsibility Principle: модуль/класс делает одну понятную вещь. | Code design |
| KISS | Не усложнять без необходимости. | Engineering principle |
| YAGNI | Не делать то, что пока не нужно. | MVP discipline |
---
## 16. Observability и эксплуатация
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Observability | Видимость состояния системы через метрики, логи, health checks. | Actuator health currently; more is Target/Deferred |
| Metrics | Числовые показатели системы. | Target operational capability |
| Prometheus | **Deferred:** возможная платформа сбора метрик; сейчас не подключена. | Future observability change |
| Grafana | **Deferred:** возможная платформа дашбордов; сейчас не подключена. | Future observability change |
| Actuator | Spring Boot endpoints для health/metrics. | App |
| Health check | Проверка живости и готовности системы. | Monitoring |
| Structured logs | Логи в JSON/структурированном формате. | Debugging |
| Correlation ID | Идентификатор для отслеживания события по всему pipeline. | Logs |
| Alert | Уведомление о проблеме. | Monitoring |
| Queue size | Размер очереди сообщений. | Pipeline health |
| Backpressure | Давление на систему, когда входящий поток быстрее обработки. | Ingest |
| Provider latency | Задержка ответа внешнего API. | Provider dashboard |
| Provider failure rate | Доля ошибок внешнего провайдера. | Monitoring |
| Data freshness seconds | Возраст последних данных. | Dashboard |
| Signal rate | Количество сигналов за период. | Strategy monitoring |
| Rejection breakdown | Разбор причин отклонения сигналов. | Risk analytics |
| Runbook | Инструкция, что делать при аварии. | Operations |
| Backup | Резервная копия данных. | Production hardening |
| Graceful shutdown | Корректная остановка с сохранением очередей/батчей. | Operations |
---
## 17. Юридические и площадочные термины
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| NAPP | Регулятор криптоактивов в Узбекистане. | Legal discussion |
| VASP | Crypto service provider: лицензированный провайдер криптоуслуг. | Legal gate |
| Licensed provider | Площадка/сервис с лицензией в Узбекистане. | Compliance |
| Unlicensed foreign exchange | Иностранная криптоплощадка без лицензии в Узбекистане. | Legal risk |
| CEX | Централизованная биржа. | Binance, Bybit, OKX |
| DEX | Децентрализованная биржа. | Hyperliquid/DeFi context |
| KYC | Проверка личности клиента. | Exchanges, banks |
| AML | Anti-money laundering: контроль против отмывания средств. | Banks, compliance |
| STR | Suspicious transaction report: сообщение о подозрительной операции. | Banking risk |
| On/off ramp | Ввод/вывод денег между fiat и crypto. | Banking/legal |
| Binance Global | Международная Binance-платформа; юридически не то же самое, что локальный лицензированный канал. | Legal discussion |
| [Coinpay.uz](http://coinpay.uz/) | Пример локального криптосервиса/канала, обсуждавшегося как более легальный путь. | Crypto on/off ramp |
| IBKR | Interactive Brokers; традиционный регулируемый брокер. | TradFi option |
| TradFi | Традиционные финансовые рынки: акции, ETF, опционы, фьючерсы. | Alternative strategies |
| Hyperliquid | On-chain/perp venue; технически доступно, но юридически требует осторожности. | Possible future strategy |
| Polymarket | Prediction market; обсуждался как рискованный research-направление. | Alternative strategy |
| Prop firm | Компания, дающая капитал трейдерам после challenge/evaluation. | Alternative discussion |
| Legal risk | Риск нарушения правил/закона/регуляторных требований. | Future execution governance |
| Banking risk | Риск вопросов от банка при вводе/выводе средств. | Legal discussion |
| Source of funds | Происхождение средств, которое могут попросить объяснить. | AML/KYC |
| Compliance score | Условная будущая оценка юридической чистоты стратегии/venue. | Deferred governance idea |
---
## 18. Альтернативные стратегии из обсуждения
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Funding-rate delta-neutral | Стратегия заработка на funding rates при нейтральной позиции к цене. | Alternative strategy |
| Funding rate | Периодическая плата между long/short в perpetual futures. | Perps |
| Delta-neutral | Позиция, где движение цены базового актива минимально влияет на PnL. | Funding strategy |
| Perpetual futures / perps | Бессрочные фьючерсы. | Crypto derivatives |
| Hedge | Позиция, уменьшающая риск основной позиции. | Intent / strategies |
| CLMM | Concentrated Liquidity Market Maker: ликвидность в выбранном ценовом диапазоне. | Alternative DeFi strategy |
| DLMM | Dynamic Liquidity Market Maker. | Alternative DeFi strategy |
| Market making | Предоставление ликвидности и заработок на spread/fees. | CLMM/DLMM |
| Impermanent loss | Потеря LP относительно простого holding из-за движения цены. | LP strategies |
| Options wheel | Продажа cash-secured puts и covered calls. | IBKR strategy |
| Premium selling | Продажа опционной премии. | Options strategy |
| Micro-futures | Малые фьючерсные контракты, например MES/MNQ. | IBKR / TradFi |
| Trend-following | Стратегия следования за трендом. | TradFi strategy |
| Prediction market | Рынок ставок на исход событий. | Polymarket |
| Cross-market arbitrage | Арбитраж между связанными рынками. | Polymarket/Betting |
| Sports CLV | Betting-подход: искать value через closing line value. | Alternative discussion |
| CLV | Closing Line Value: насколько твоя ставка лучше финальной линии. | Betting |
| DeFi yield | Доходность в DeFi-протоколах. | Alternative strategy |
| Pendle PT | Principal Token в Pendle; инструмент fixed-yield DeFi. | Alternative strategy |
| Ethena sUSDe | DeFi yield asset; обсуждался как не такой выгодный в 2026. | Alternative strategy |
---
## 19. Типичные ошибки и анти-паттерны
| Термин | Простое объяснение | Где используется |
| --- | --- | --- |
| Overfitting | Стратегия слишком подогнана под прошлые данные. | Backtest risk |
| Look-ahead bias | Backtest использует информацию из будущего. | Point-in-time rule |
| Survivorship bias | Анализ только выживших/победителей. | Discovery risk |
| Data leakage | Будущие или запрещённые данные попали в модель/тест. | Backtest quality |
| Alpha decay | Сигнал перестаёт работать со временем. | Wallet scoring |
| Crowded trade | Слишком много участников торгуют один и тот же сигнал. | Signal penalty |
| Latency trap | Попытка соревноваться скоростью там, где у solo-разработчика нет преимущества. | Anti-goal |
| Manual DexScreener watching | Ручное наблюдение графиков вместо системного data pipeline. | Anti-pattern |
| Real trading too early | Запуск денег до backtest/paper/legal gates. | Major risk |
| JPA magic | Непредсказуемое ORM-поведение в high-data проекте. | Avoided |
| LLM in critical path | LLM принимает торговые решения в реальном времени. | Avoided |
| Expensive APIs too early | Покупка дорогих API до доказанного edge. | MVP discipline |
| Full auto execution early | Автоматическая торговля до proof of edge. | Avoided |
| Multi-chain from day one | Подключение многих сетей до проверки первой гипотезы. | Avoided |
---
## 20. Мини-словарь сокращений
| Сокращение | Расшифровка | Смысл |
| --- | --- | --- |
| PnL | Profit and Loss | Прибыль/убыток |
| ROI | Return on Investment | Доходность на вложенный капитал |
| APY | Annual Percentage Yield | Годовая доходность с compounding |
| APR | Annual Percentage Rate | Годовая ставка без compounding |
| DD | Drawdown | Просадка |
| WR | Win-rate | Доля прибыльных сделок |
| TP | Take Profit | Фиксация прибыли |
| SL | Stop Loss | Ограничение убытка |
| LP | Liquidity Provider | Поставщик ликвидности |
| CEX | Centralized Exchange | Централизованная биржа |
| DEX | Decentralized Exchange | Децентрализованная биржа |
| API | Application Programming Interface | Интерфейс для программного доступа |
| WS | WebSocket | Постоянное соединение для real-time данных |
| RPC | Remote Procedure Call | Вызов методов удалённого сервиса/blockchain node |
| TTL | Time To Live | Время жизни cache-записи |
| UTC | Coordinated Universal Time | Единый часовой пояс для хранения времени |
| KYC | Know Your Customer | Проверка клиента |
| AML | Anti-Money Laundering | Антиотмывочный контроль |
| VASP | Virtual Asset Service Provider | Провайдер услуг с виртуальными активами |
| MVP | Minimum Viable Product | Минимальная полезная версия |
---
## 21. Короткая карта проекта
| Вопрос | Короткий ответ | Где смотреть |
| --- | --- | --- |
| Что строим? | Research-first trading intelligence platform. | Sections 1–5 |
| С чего начинаем? | Solana Smart Money как первая alpha-гипотеза. | Strategy / Wallet Intelligence |
| Что главное проверить? | Есть ли forward alpha после всех издержек. | Backtest + Paper |
| Когда реальные деньги? | Только после backtest, paper, legal/venue/capital gates. | Validation Gates |
| Что не делаем сначала? | Auto-trading, expensive APIs, latency-war, multi-chain. | Anti-patterns |
| Что останется, если smart money не выгорит? | Универсальное research-core ядро для других стратегий. | Architecture |
