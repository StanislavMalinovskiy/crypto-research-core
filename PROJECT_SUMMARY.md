<!-- Экспортировано из Notion 2026-09-12. Исходная страница: https://app.notion.com/p/3557a744d2df80fca758da841906fd26?pvs=204 -->

# Crypto Research Core — короткий обзор
1. Мы строим не торгового бота, а research-систему для проверки on-chain сигналов.
2. Первая цель — не заработать сразу, а понять, какие сигналы реально дают edge.
3. MVP делаем Solana-first, но архитектура сразу готовится под Base/Arbitrum/EVM.
4. Архитектура: один синхронный modular monolith — Java 25, Spring Boot 4.1.1, Spring MVC, Spring Modulith 2.1.1, Spring Data JDBC, Maven, PostgreSQL, Redis, Flyway, Prometheus/Grafana.
5. Восемь вертикальных модулей (`kernel`, `governance`, `marketdata`, `risk`, `wallet`, `strategy`, `measurement`, `research`) владеют своими domain, persistence и provider adapters; система сохраняет immutable raw events, а затем формирует swaps, token metrics, wallet activity и risk facts.
6. Все события храним chain-aware: `chain + tx_hash + event_index`.
7. Кошельки оцениваем по истории: trade count, win-rate, profit factor, avg win/loss.
8. Для PnL кошельков используем понятную методологию: FIFO, closed trades, cross-venue matching.
9. Токены проходят Risk Engine: `BLOCK`, `WATCH_ONLY`, `ALLOW`.
10. `BLOCK` не создаёт entry-сигнал или позицию, но кандидат и его shadow outcome сохраняются для проверки качества Risk Engine; `WATCH_ONLY` ограничивает score максимум 69.
11. Сигналы делятся на семьи: Smart Wallet Buy, Multi Wallet Buy, Liquidity Spike, Holder Growth, Token Risk Alert.
12. Каждый сигнал получает `score` 0–100, `grade`, `confidence` и подробный `reasoning`.
13. `score` — это не прогноз прибыли, а предварительная оценка качества сигнала.
14. Реальная проверка делается через Outcome Tracker по 1h/4h/24h и другим горизонтам.
15. Entry price считаем только после момента, когда сигнал реально был доступен системе.
16. Цены для outcomes берём по прозрачной Price Source Strategy и храним snapshots.
17. Если токен умер или ликвидность исчезла, это не удаляется из отчёта, чтобы не было survivorship bias.
18. Виртуальные позиции учитывают friction, liquidity cap, stop loss, take profit и time stop.
19. Backtest должен использовать только данные, доступные на тот момент, без look-ahead bias.
20. Итог MVP — Evidence Report: какие signal families стоит углублять, какие отбросить, и есть ли смысл идти к paper/live trading.
