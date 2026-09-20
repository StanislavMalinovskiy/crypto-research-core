# План реализации

**Обновлено:** 20 сентября 2026 года

## Назначение

Это короткая рабочая карта от текущего состояния репозитория до решения, основанного на исследовательских данных. Она отвечает на три вопроса: на каком этапе находится проект, какая крупная задача выполняется сейчас и какие задачи ожидаются дальше.

Источники имеют разные обязанности:

- [Roadmap](ROADMAP.md) определяет продуктовые гипотезы, долгосрочные возможности и направление.
- Этот Delivery Plan определяет этапы, порядок крупных рабочих пакетов и их статус.
- [DELIVERY_PLAN_FIXES](DELIVERY_PLAN_FIXES.md) — companion remediation-карта и traceability по итогам двух внешних аудитов 2026-09-20; определяет пакеты F0–F9 до реальных данных и decision support.
- [OpenSpec changes](../openspec/changes/) содержат требования, дизайн и подробные чекбоксы активной работы.
- [ADRs](adr/README.md) фиксируют принятые архитектурные решения.
- [Main specs](../openspec/specs/) описывают принятое проверяемое поведение.
- Код и тесты подтверждают фактически работающую реализацию.

Задачи ниже являются направляющей картой, а не неизменным обязательством или железным расписанием. По мере реализации, исследования провайдеров, получения данных и обнаружения новых зависимостей задачи могут добавляться, разделяться, объединяться, переставляться или откладываться. Такое изменение должно быть внесено в план явно; детали активной реализации остаются в соответствующем OpenSpec change, а принятые ADR и main specs не переписываются планом молча.

Допустимые статусы: `Done`, `Current`, `Next`, `Planned`, `Deferred`.

## Текущая позиция

- **Текущий этап:** Этап 3 — Реальные данные Solana (исполняется через remediation-пакеты [DELIVERY_PLAN_FIXES](DELIVERY_PLAN_FIXES.md)).
- **Текущая работа:** завершить F1 — провести owner-run capability-specific provider spikes 3.1–3.6 и выбрать primary live transport, historical source и необходимые specialized sources; design-контракт готов, но change остаётся активным на 4/10 задач.
- **Завершённая подготовка:** F0 синхронизировал документы; F2 core storage принят и архивирован — Flyway V5–V9, immutable raw/price/liquidity/USD/universe storage, lineage constraints и bounded batch paths реализованы.
- **Следующая операционная работа:** F3 — bounded ingestion выбранного transport с явными timeout, rate, concurrency, finite retry policies, gap recovery и минимальной operational visibility. До допуска live data F3 также добавляет forward migrations для token decimals, provider-visible/modeled availability и явного quality provenance.
- **Условие перехода к этапу 4:** реальные текущие и исторические Solana observations воспроизводятся с raw lineage, trusted observation time, parser identity и видимыми gaps без повторных доменных эффектов (F3.6).

## Сводка этапов

| Этап | Статус | Результат |
|---|---|---|
| 1. Архитектурный фундамент | Done | Воспроизводимая модульная основа и обязательные quality gates готовы к бизнес-разработке |
| 2. Первый вертикальный срез | Done | Один сигнал на записанных данных измеряется от raw input до воспроизводимого отчёта |
| 3. Реальные данные Solana | Current | Текущие и исторические данные проходят проверенный путь без скрытых пропусков |
| 4. Аналитика `risk` и `wallet` | Planned | Сигналы получают point-in-time сведения о токенах и кошельках |
| 5. Сигналы и их оценка | Planned | Семейства сигналов получают сопоставимые cost-aware outcomes без смещений |
| 6. Исследовательское решение | Planned | Evidence Report обосновывает углубление, изменение, продление или остановку направления |
| 6A. Decision support и forward shadow | Planned | Подтверждённые результаты превращаются в advisory feed без исполнения сделок (F8) |
| 7. Исполнение | Deferred | Paper/live рассматриваются только после доказательств и отдельного safety design |

## Этап 1 — Архитектурный фундамент

**Цель:** создать сопровождаемую и проверяемую основу без преждевременной бизнес-реализации.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 1.1 | Done | Создать один Maven-модуль, один Spring Boot JAR и шесть проверяемых Spring Modulith boundaries. |
| 1.2 | Done | Зафиксировать архитектурные, persistence, transaction, concurrency, operations и testing contracts. |
| 1.3 | Done | Ввести chain-aware identity и воспроизводимость времени, чисел, ordering, provenance и seed. |
| 1.4 | Done | Реализовать первое append-only хранилище raw market-data с точной lineage и идемпотентным retry/conflict. |
| 1.5 | Done | Закрепить локальный и CI quality gate, test-integrity preflight и проверяемые repository conventions. |
| 1.6 | Done | Упростить агентную разработку: DEFAULT без подагентов, MULTIAGENT только после ручного включения. |
| 1.7 | Done | Подтвердить удалённый gate для точного checkpoint, required check основной ветки и передать Current этапу 2. |

**Условие завершения:** фундамент воспроизводится чистым checkout, все обязательные локальные и удалённые проверки проходят, а основная ветка защищена стабильным required check.

## Этап 2 — Первый вертикальный срез

**Цель:** как можно раньше проверить полный исследовательский путь на записанном входе без сложности реального провайдера.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 2.0 | Done | Добавить переносимую локальную PostgreSQL для разработки, сохранив Testcontainers для тестов и отложив shared managed PostgreSQL до отдельного решения этапа 3. |
| 2.1 | Done | Реализовать и принять review OpenSpec change `build-first-signal-evaluation-skeleton` с точным контрактом одного end-to-end сценария. |
| 2.2 | Done | Пропустить записанный provider fixture через raw replay и нормализовать одно рыночное событие. |
| 2.3 | Done | Создать один версионированный сигнал с неизменяемым decision-time snapshot и provenance. |
| 2.4 | Done | Выбрать допустимую point-in-time цену и измерить один forward outcome без look-ahead. |
| 2.5 | Done | Сформировать детерминированный минимальный отчёт, связывающий input, signal, outcome и run identity. |
| 2.6 | Done | Подтвердить повторяемость полного пути и готовность тех же boundaries принять реальные Solana data. |

**Условие завершения:** одинаковый записанный вход детерминированно создаёт трассируемый сигнал, измеренный результат и одинаковый отчёт без нарушения границ модулей.

## Этап 3 — Реальные данные Solana

**Цель:** заменить записанный вход надёжными текущими и историческими наблюдениями Solana, сохранив проверенный путь.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 3.0 | Done | Настроить постоянную managed PostgreSQL для работы из нескольких мест и подтвердить профиль, секреты, Flyway, PostgreSQL 18.6, TLS и восстановление свежего backup; сетевая политика остаётся ответственностью оператора. |
| 3.1 | Current | Через F1: построить provider-consumer matrix, зафиксировать Solana data contract (finality, locator, time, universe, derivation, quality, ranges, capacity) и выбрать primary transport/history sources по результатам capability-specific spikes. |
| 3.2 | Next | После F1 selection реализовать F3 bounded real-time ingestion с явными timeout, rate, concurrency и finite retry policies; V5–V9 core storage уже готов, недостающие live facts добавляются только forward migrations. |
| 3.3 | Planned | Добавить provider-specific normalization, parser versioning и replay сохранённых raw payloads. |
| 3.4 | Planned | Обнаруживать reconnect gaps, восстанавливать пропущенные диапазоны и явно отмечать unresolved windows. |
| 3.5 | Planned | Сохранять observed price snapshots, source/quality facts и необходимые token discovery observations. |
| 3.6 | Planned | Выполнить идемпотентный исторический backfill и доказать отсутствие дубликатов и скрытой потери данных. |
| 3.7 | Planned | Измерить freshness, parse failures, coverage и gaps на длительном прогоне и закрыть data-quality gate этапа. |

**Условие завершения:** текущие и исторические данные воспроизводятся с raw lineage, parser identity и видимыми gaps без повторных доменных эффектов.

## Этап 4 — Аналитика `risk` и `wallet`

**Цель:** добавить point-in-time сведения о риске токена и поведении кошелька, необходимые для обоснованных сигналов.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 4.1 | Planned | Определить версионированные common и Solana-specific risk facts с проверяемой схемой и provenance. |
| 4.2 | Planned | Реализовать воспроизводимые решения `BLOCK`, `WATCH_ONLY`, `ALLOW` и append-only историю risk decisions. |
| 4.3 | Planned | Восстановить сделки кошелька и реализовать FIFO closed-trade PnL с cross-venue matching. |
| 4.4 | Planned | Рассчитать point-in-time wallet metrics, включая profit factor, average win/loss и ограничения выборки. |
| 4.5 | Planned | Ввести версионированный wallet score history, tiers, graduation и обновляемый watchlist. |
| 4.6 | Planned | Подтвердить, что risk и wallet APIs не читают будущее состояние и дают повторяемые snapshots для сигналов. |

**Условие завершения:** сигнальный слой может получить воспроизводимые risk и wallet facts на требуемый момент времени без прямого доступа к чужим таблицам.

## Этап 5 — Сигналы и их оценка

**Цель:** создать и сравнить значимые семейства сигналов без look-ahead, survivorship bias и оптимистичных издержек.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 5.0 | Planned | Зафиксировать preregistered research protocol (F5) до любой настройки по outcome-результатам: гипотезы, grid, диапазоны, min sample, multiplicity policy, decision gates. |
| 5.1 | Planned | Определить versioned signal definitions, configuration identity, candidate journal и правила дедупликации. |
| 5.2 | Planned | Реализовать entry families Smart Wallet Buy, Multi Wallet Buy, Liquidity Spike и Holder Growth. |
| 5.3 | Planned | Реализовать Token Risk Alert как avoidance family и сохранять shadow outcomes отклонённых кандидатов. |
| 5.4 | Planned | Добавить score, grade, confidence и читаемое reasoning из неизменяемого decision-time evidence. |
| 5.5 | Planned | Реализовать virtual positions, bounded liquidity, dynamic friction и согласованные exit rules без реального исполнения. |
| 5.6 | Planned | Измерять entry и avoidance outcomes по согласованным горизонтам, сохраняя no-price и dead-token cases. |
| 5.7 | Planned | Сформировать сопоставимые отчёты по families и доказать одинаковые valuation, cost и data-quality policies. |

**Условие завершения:** каждый принятый и отклонённый кандидат получает подходящий трассируемый outcome, а семейства сравниваются по единой воспроизводимой методике.

## Этап 6 — Исследовательское решение

**Цель:** на накопленных данных решить, стоит ли углублять направление, менять гипотезу, расширять исследование или остановиться.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 6.1 | Planned | Зафиксировать достаточный dataset snapshot, cutoff, fingerprints, build, algorithms, configuration и seeds. |
| 6.2 | Planned | Выполнить детерминированный backtest/replay по families, horizons и релевантным market regimes. |
| 6.3 | Planned | Проверить sample size, expectancy after costs, drawdown, outlier dependence и чувствительность к data quality. |
| 6.4 | Planned | Сформировать воспроизводимый Evidence Report с раздельными entry и avoidance результатами. |
| 6.5 | Planned | Зафиксировать решение: deepen working family, pivot, extend data/research или honest exit. |
| 6.6 | Planned | Создать следующий план только из подтверждённых выводов, отдельно обосновав Base/EVM или более длинный walk-forward. |

**Условие завершения:** Evidence Report позволяет принять и воспроизвести явное решение без подмены отсутствующих доказательств архитектурными ожиданиями.

## Этап 6A — Decision support и forward shadow

**Статус:** Planned. Активируется после положительного или перспективного решения Этапа 6; соответствует пакету F8 в [DELIVERY_PLAN_FIXES](DELIVERY_PLAN_FIXES.md).

**Цель:** превратить подтверждённые результаты в понятный владельцу инструмент без исполнения сделок.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 6A.1 | Planned | Runtime trigger/scheduler с PostgreSQL-backed claiming (F8.1). |
| 6A.2 | Planned | Decision feed: advisory action, evidence status, measured expectancy, expiry/invalidation (F8.2). |
| 6A.3 | Planned | Минимальный канал доставки с dedup, expiry, retry и delivery audit (F8.3). |
| 6A.4 | Planned | Owner positions и position-aware `CONSIDER_EXIT` (F8.4). |
| 6A.5 | Planned | Forward shadow период без денег и сравнение с backtest assumptions (F8.5). |

**Условие завершения:** владелец понимает, что произошло и почему; forward results подтверждают или опровергают применимость backtest; signing и order submission отсутствуют.

## Этап 7 — Исполнение

**Статус этапа:** Deferred.

**Цель:** рассматривать paper и live только после положительного исследовательского решения и отдельного проектирования безопасности.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 7.1 | Deferred | Подтвердить активационные evidence gates: положительные результаты, достаточный capital case и приемлемый risk profile. |
| 7.2 | Deferred | Принять отдельные ADR и OpenSpec design для signing, venue policies, limits, kill switches и audit trail. |
| 7.3 | Deferred | Реализовать paper trading и сравнить его результаты с backtest при тех же версиях и friction assumptions. |
| 7.4 | Deferred | Проверить walk-forward, paper expectancy, drawdown, deviation, operational readiness и manual controls. |
| 7.5 | Deferred | Только после независимого safety review разрешить ограниченный MANUAL_CONFIRM и поэтапный live rollout. |

**Условие активации:** этап 6 дал положительное решение, а отдельный approved change и ADR определили safety gates. До этого signing, order submission и PAPER/LIVE execution отсутствуют.

## Отложенные направления

- Base/EVM implementation и сравнение сетей — только после Solana evidence или доказанной необходимости сравнения.
- Cross-chain wallet identity, bridge-flow и capital rotation — только если межсетевое поведение станет исследовательским bottleneck.
- Более длинный backfill и walk-forward — после положительного или неоднозначного 90-day результата.
- Кэши, брокеры, дополнительные базы, partitioning и внешняя observability infrastructure — только по измеренной нагрузке.
- Funding bot и распределение капитала — отдельный проект после исследовательского и execution gates.

## Правило сопровождения

План пересматривается при архивировании change, завершении этапа, явной смене приоритета или появлении доказанной новой необходимости. Короткие рабочие пакеты и их статусы обновляются здесь; подробные требования и чекбоксы живут только в OpenSpec. Новая задача добавляется тогда, когда без неё нельзя достичь результата этапа или сохранить корректность измерений, а не для фиксации каждой технической подзадачи.
