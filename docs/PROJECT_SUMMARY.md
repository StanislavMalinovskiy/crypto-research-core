# Crypto Research Core — короткий обзор
1. Мы строим не торгового бота, а research-систему для проверки on-chain сигналов.
2. Первая цель — не заработать сразу, а понять, какие сигналы реально дают edge.
3. MVP делаем Solana-first, но архитектура сразу готовится под Base/Arbitrum/EVM.
4. Архитектура: один синхронный modular monolith — Java 25, Spring Boot 4.1.1, Spring MVC, Spring Modulith 2.1.1, Spring Data JDBC, Maven, PostgreSQL и Flyway. Redis, Caffeine и внешняя observability-инфраструктура отложены до отдельного обоснованного change.
5. Шесть вертикальных модулей (`kernel`, `marketdata`, `risk`, `wallet`, `signal`, `evaluation`) владеют своими domain, persistence и adapters; `marketdata` хранит raw/normalized evidence и immutable dataset snapshots, `signal` — candidates и accepted decision-time snapshots, `evaluation` — run manifests, outcomes и reports. `risk` первого среза остаётся чистым синхронным API без собственной схемы.
6. Идентичность событий network-aware: `ChainId` хранит точный case-sensitive CAIP-2 (`SOLANA_MAINNET = solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp`), `TransactionId = network + opaque transaction value`, `EventId = transaction + canonical opaque locator`; raw provider observations additionally различаются по provider, а нормализованные события — нет.
7. Кошельки оцениваем по истории: trade count, win-rate, profit factor, avg win/loss.
8. Для PnL кошельков используем понятную методологию: FIFO, closed trades, cross-venue matching.
9. Токены проходят Risk Engine: `BLOCK`, `WATCH_ONLY`, `ALLOW`.
10. `BLOCK` и `WATCH_ONLY` не создают accepted ENTRY-сигнал или позицию: кандидат и его risk-evidence сохраняются (текущий first slice); shadow outcomes отклонённых кандидатов и будущая `WATCH_ONLY`-семантика — работы remediation (F4.3, F6.6).
11. Сигналы делятся на семьи: Smart Wallet Buy, Multi Wallet Buy, Liquidity Spike, Holder Growth, Token Risk Alert.
12. Каждый сигнал получает `score` 0–100, `grade`, `confidence` и подробный `reasoning`.
13. `score` — это не прогноз прибыли, а предварительная оценка качества сигнала.
14. Реальная проверка делается через Outcome Tracker; реализован горизонт `1h`, `4h/24h` — будущая работа (F6.7, потребует forward-миграции схемы).
15. Entry price считаем только после момента, когда сигнал реально был доступен системе.
16. Цены для outcomes берём по прозрачной Price Source Strategy и храним snapshots.
17. Если токен умер или ликвидность исчезла, это не удаляется из отчёта, чтобы не было survivorship bias.
18. Виртуальные позиции учитывают friction, liquidity cap, stop loss, take profit и time stop.
19. Backtest должен использовать только данные, доступные на тот момент, без look-ahead bias.
20. Итог MVP — Evidence Report: какие signal families стоит углублять, какие отбросить, и есть ли смысл идти к paper/live trading.
21. Исследовательский результат считается воспроизводимым только при зафиксированных dataset fingerprint/cutoff, build/commit, algorithm/config version, deterministic ordering и seed; время хранится как UTC `Instant`, финансовая арифметика является точной.
22. Реализованный первый срез намеренно узок: записанный Solana fixture доказывает deterministic `LIQUIDITY_SPIKE` → risk gate → `1h` ENTRY outcome → one-family report. Он не подключает провайдера, не реализует wallet analytics или execution и не доказывает статистический edge.
23. F2 core storage принят: Flyway V5–V9 добавляют отдельные raw transaction payloads, price/liquidity observations, USD conversion lineage и point-in-time token universes с immutable retry, bounded batches и PostgreSQL volume/boundary evidence. Это ещё не live ingestion.
24. Текущая работа — незавершённый F1 provider selection (4/10): owner-run spikes и выбор live/history sources. После него F3 добавляет adapter, gaps/monitoring и недостающие live facts до первой массовой записи.
25. Порядок работ до реальных данных и decision support определён remediation-картой [DELIVERY_PLAN_FIXES](DELIVERY_PLAN_FIXES.md) по итогам двух внешних аудитов 2026-09-20.
