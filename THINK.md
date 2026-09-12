<!-- Экспортировано из Notion 2026-09-12. Исходная страница: https://app.notion.com/p/34d7a744d2df804298abefe8d6460807?pvs=204 -->

# Think — идеи и направления

## 2. Crypto Market Inefficiency Radar
Это более широкий вариант твоей Smart Money Platform.
**Идея:** система ищет не только smart wallets, а разные аномалии:
- необычный рост liquidity;
- рост holders без wash-trading признаков;
- расхождение цены между DEX/CEX;
- крупные переводы на биржи;
- wallet accumulation;
- funding anomalies;
- open interest anomalies;
- volume/liquidity divergence;
- suspicious token risk;
- creator/dev wallet activity.
То есть не одна стратегия, а **радар рыночных неэффективностей**.
Архитектура похожа:
```plain text
Data Providers
→ Normalization
→ Feature Store
→ Anomaly Detection
→ Strategy Candidates
→ Backtest
→ Paper Trading
→ Alerts
```
Это выгоднее, потому что если Smart Money гипотеза не сработает, система не умирает. Ты просто добавляешь другую гипотезу.
# Какой модуль я бы поставил первым
Не Smart Money.
Я бы начал так:
## MVP-1: Market Inefficiency Radar + Paper Journal
Сначала без торговли.
Собираешь:
- prices;
- volume;
- liquidity;
- funding rates;
- token metadata;
- wallet activity;
- suspicious activity;
- signals;
- manual decision journal.
И каждый сигнал получает оценку:
```plain text
signal_type
confidence
risk_score
expected_edge
time_horizon
capital_required
reasoning
result_after_1h
result_after_24h
result_after_7d
```
Через 1–2 месяца у тебя будет база:
- какие сигналы вообще работают;
- какие только выглядят красиво;
- где больше всего шума;
- где есть шанс на edge.
Потом выбираешь одну стратегию для paper trading.
---
# Сравнение направлений
| Направление | Потенциал | Риск | Требуемый капитал | Подходит тебе |
| --- | --- | --- | --- | --- |
| Smart Money Solana | высокий | очень высокий | низкий/средний | да |
| Funding/Basis | средний/высокий | средний | средний/высокий | очень да |
| CLMM Optimizer | средний/высокий | высокий | средний | да |
| Token Risk Engine | косвенный | низкий/средний | низкий | очень да |
| Whale Flow Radar | средний | средний | средний | да |
| Pure AI chart bot | низкий | высокий | любой | нет |
| Memecoin sniper | высокий | экстремальный | любой | не советую |
---
# Мой честный ответ
**Да, Smart Money Platform может стать прибыльной.**
Но я бы не ставил цель “через 6 месяцев она стабильно даст \$500/мес с \$500 капитала”.
Я бы ставил цель иначе:
```plain text
За 3 месяца:
доказать или убить 2–3 alpha-гипотезы.

За 6 месяцев:
найти 1 стратегию с положительной expectancy после costs на paper.

За 9–12 месяцев:
осторожно перейти к manual/semi-auto execution маленьким капиталом.

После доказанного edge:
увеличивать капитал или превращать платформу в продукт/сигнальный сервис.
```
Самый выгодный вариант для тебя:
> **Crypto Alpha Research Platform**, где Smart Money — один модуль, Funding/Basis — второй, Token Risk — третий, а всё проходит через единое ядро: data → scoring → backtest → paper → risk → journal.
Это ближе к настоящему quant/research-подходу и намного устойчивее, чем просто “ИИ-агент торгует криптой”.
