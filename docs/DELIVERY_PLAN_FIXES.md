# План исправлений по результатам внешних аудитов

**Обновлено:** 20 сентября 2026 года
**Статус:** рабочий companion plan к [основному Delivery Plan](DELIVERY_PLAN.md)

## Назначение

Этот документ объединяет подтверждённые выводы двух независимых аудитов:

- [оценка первого внешнего аудита](notes/EXTERNAL_AUDIT_REVIEW_2026-09-20.md);
- [оценка аудита GLM 5.3 MAX](notes/GLM_5_3_MAX_EXTERNAL_AUDIT_REVIEW_2026-09-20.md).

Он отвечает на вопрос: какие исправления и дополнительные рабочие пакеты нужны, чтобы пройти путь от recorded research skeleton до корректной системы реальных Solana-данных, Evidence Report и полезного владельцу decision-support инструмента.

Это не OpenSpec change и не замена подробным требованиям. Каждый пакет, который меняет поведение, схему, dependency или архитектурное решение, реализуется только через отдельный approved OpenSpec change. После принятия последовательности актуальные статусы должны быть перенесены в [Delivery Plan](DELIVERY_PLAN.md), а этот файл должен оставаться картой remediation и traceability.

## Главный вывод

Переписывать модульную архитектуру не требуется. Исправления нужны в четырёх областях:

1. контракт реальных Solana-данных до подключения провайдера;
2. статистическая и point-in-time корректность исследования;
3. operational visibility реального ingestion;
4. слой между Evidence Report и любым PAPER/LIVE execution.

Критический путь:

```plain text
Документы и решения
    -> Solana data contract и provider matrix
    -> schema/storage readiness
    -> bounded ingestion, backfill, gaps и monitoring
    -> point-in-time risk/wallet
    -> production signal/evaluation rules
    -> preregistered Evidence Report
    -> decision support и forward shadow
    -> только затем PAPER/MANUAL/LIVE
```

## Правила выполнения плана

- Не подключать live provider к существующему recorded payload contract без отдельного data design.
- Не записывать массовый real stream до принятия identity, time, finality, universe и capacity решений.
- Не настраивать signal thresholds по outcome-результатам до фиксации statistical protocol.
- Не считать `score` прогнозом доходности и не выдавать advisory action без статуса доказанности family.
- Не вводить signing, order submission или real-money execution в рамках этих исправлений.
- Не требовать Grafana, Redis, Kafka, ClickHouse или новую базу без измеренной необходимости.
- Сохранять один репозиторий, один Maven-модуль, один JAR и одну PostgreSQL database, пока ADR не докажет обратное.
- Выполнять изменения небольшими зависимостными OpenSpec changes; не объединять весь план в одну реализацию.

## Сводка рабочих пакетов

| ID | Приоритет | Пакет | Когда нужен |
|---|---|---|---|
| F0 | Immediate | Синхронизировать документы и устранить противоречия | До следующего implementation change |
| F1 | Blocking | Определить Solana data contract и provider requirements | До завершения Stage 3.1 |
| F2 | Blocking | Подготовить schema/storage к реальным данным | До первой массовой real-data записи |
| F3 | Blocking | Реализовать bounded ingestion, backfill, gaps и monitoring | Для выхода из Stage 3 |
| F4 | Required | Реализовать point-in-time risk и wallet evidence | До production signal families |
| F5 | Required | Пререгистрировать research protocol | До просмотра и настройки performance outcomes |
| F6 | Required | Исправить production signal/evaluation semantics | До Stage 6 Evidence Report |
| F7 | Required | Построить статистически честный Evidence Report | Для research decision |
| F8 | Product gap | Добавить decision support и forward shadow | После положительного/перспективного research decision |
| F9 | Deferred | PAPER/MANUAL/LIVE safety and execution | Только после F8 и отдельных gates |

## F0 — Синхронизация документов

**Цель:** убрать инструкции, которые могут направить агента или разработчика по устаревшему пути.

### Работы

- Обновить Roadmap: Helius оставить кандидатом до завершения selection; убрать старую обязательность Helius WebSocket, Grafana-specific DoD, устаревшие сроки/бюджет и package tree с обязательным `internal`.
- Согласовать Roadmap с текущими модулями, схемами V1–V4, Delivery Plan и Tech Stack.
- Зафиксировать текущую семантику `WATCH_ONLY`: first slice не создаёт accepted ENTRY; будущий `OBSERVE`/shadow behavior требует отдельного решения.
- Исправить Glossary: точный case-sensitive CAIP-2 `ChainId`, битую ссылку на удалённый legacy archive и misleading current-use пометки у будущих терминов.
- Уточнить Operations: operator-managed backup уже существует, repository-owned production deployment/runbook остаётся будущей работой.
- Уточнить, что V4 поддерживает только `1h`; `4h/24h` требуют forward migration.
- До Stage 5 решить, нужен ли отдельный `strategy_experiment_id` или достаточно version/config/run identities.
- Добавить в основной Delivery Plan новые пакеты research protocol и decision support после их принятия.

### Условие завершения

Roadmap, Delivery Plan, Architecture, Tech Stack, Operations, Project Summary, Glossary и main specs не дают взаимоисключающих указаний о текущем состоянии, provider selection, `WATCH_ONLY`, observability, horizons или execution.

## F1 — Solana data contract и выбор провайдера

**Цель:** сначала определить нужные системе данные и только потом выбирать provider.

### F1.1 Provider-consumer matrix

Для каждого signal family, risk fact, wallet metric и outcome horizon зафиксировать:

- необходимые raw и normalized факты;
- live и historical coverage;
- минимальную историческую глубину;
- freshness/latency;
- finality;
- source/quality/provenance;
- rate limits, retention, terms и стоимость;
- единицу тарификации, период сброса квоты, hard/soft limits, overage/throttling behavior и способ получить текущее потребление;
- failure, retry и fallback semantics.

Матрица должна как минимум явно сравнить:

- Helius JSON-RPC/WebSocket и доступные платные LaserStream-возможности;
- Alchemy Yellowstone gRPC и его ограниченное historical replay;
- Triton Dragon's Mouth/Fumarole, включая persistent cursor и replay window;
- Chainstack Yellowstone gRPC;
- SQD как кандидата для исторических данных — только после проверки фактического Solana dataset, полей, глубины, полноты и условий использования;
- Bitquery, DexScreener и GoPlus как специализированные источники enrichment/risk/market facts, а не предполагаемую замену полного Solana transport.

Helius Free разрешено использовать только для проверки реально доступных ему JSON-RPC/standard WebSocket/API-свойств. По состоянию на 2026-09-20 этот тариф не предоставляет mainnet LaserStream gRPC и `transactionSubscribe`, поэтому он не доказывает production reconnect/replay transport. Возможности, тарифы и replay windows перепроверяются по официальной документации на дату решения. Ни наличие API в документации, ни бесплатный ключ сами по себе не доказывают пригодность.

### F1.2 Finality и reorg policy

Выбрать один контракт:

- stable admission только после `finalized`;
- provisional ingestion с rollback/reconciliation;
- отдельный provisional transport journal и stable domain admission.

Измерить фактическую latency выбранного варианта. Не переносить provisional evidence в V1 storage без отдельного approved contract.

### F1.3 Raw granularity и Solana event locator

Определить:

- что является raw observation: transaction, instruction или decoded event;
- как хранится полный provider payload без ненужного дублирования;
- grammar для outer/inner instruction, CPI и multi-leg swaps;
- provider-independent locator;
- reparse/parser-version behavior и forward migration policy.

### F1.4 Time и availability semantics

Разделить:

- chain/source event time;
- provider-visible time;
- trusted system received/observed time;
- stable admission time;
- durable ingestion time;
- signal publication time;
- modeled availability для historical replay.

`observedAt` для live должен фиксироваться trusted application `Clock` на adapter boundary, а не слепо браться из provider payload. Зафиксировать допустимый clock skew и поведение при нарушении.

### F1.5 Point-in-time token universe

Определить discovery source, eligibility rules, venue/program scope, inclusion/exclusion time и причины. Universe не должен строиться из сегодняшних выживших токенов или фильтров, использующих будущее знание.

### F1.6 Swap, price, liquidity и USD derivation

Отделить или явно связать:

- chain-native swap quantities;
- token decimals;
- native price;
- USD conversion;
- pool/venue liquidity;
- source, observed time и confidence.

Для каждого поддержанного venue описать inputs и формулы. Неполные данные должны давать явный quality/status, а не fabricated fallback.

### F1.7 Price/liquidity quality

Определить minimum notional, pool depth, venue quality, outlier policy, suspicious/wash evidence, cross-source consistency и confidence degradation. Одиночный swap не считается автоматически исполняемой рыночной ценой.

### F1.8 Исторические диапазоны

Разделить warm-up, training/exploration, validation и holdout periods. Если wallet lookback остаётся 90 дней и evaluation window — 90 дней, общий backfill должен быть около 180 дней или больше. Альтернатива — заранее сократить один из периодов.

### F1.9 Capacity envelope

Оценить events/day, payload bytes/day, retention, backfill size, snapshot frequency, dataset membership, indexes и query patterns. Отдельно спрогнозировать размер backup, длительность backup/restore, WAL growth и допустимые RPO/RTO. Решения о partitioning, storage shape и backup strategy принимаются по этим данным, а не по предположениям.

Разделить данные по восстановимости:

- provider-reloadable raw evidence считается восстановимым только при доказанных retention, replay, cost, terms и неизменности нужного payload; одного предположения «это есть в блокчейне» недостаточно;
- normalized/derived facts можно пересчитать только при сохранённых exact raw evidence, parser/algorithm versions и configuration;
- dataset snapshots и membership, fingerprints, run manifests/configuration, risk/signal/wallet history, outcomes, reports и operator metadata считать невосполнимыми и защищать независимо от raw retention.

Ежедневный полный `pg_dump` допустим как текущая маломасштабная baseline, но F1 обязан определить измеримые пороги перехода к иной схеме: время/размер backup, restore time, объём базы и RPO/RTO. Для выросшего объёма выбрать подходящую комбинацию selective logical backup, physical backup, WAL/PITR, snapshots и off-host/object retention; не продолжать full dump по инерции.

### F1.10 Provider spike и selection

На одинаковом bounded sample проверить общие возможности кандидатов, а transport/history-specific свойства — отдельными capability-specific spikes. Проверить missing fields, ordering, finality, duplicates, reconnect, replay window, длительный disconnect, rate limiting, quota exhaustion, terms и free/paid limits. Бесплатный или ограниченный тариф не может подтвердить capability, которой на нём нет. Выбрать:

- primary live provider/transport;
- historical/backfill source;
- дополнительные источники только для непокрытых фактов;
- допустимое поведение при недоступности;
- наблюдаемый способ обнаружить приближение и фактическое исчерпание квоты;
- запрет на подмену отсутствующих данных вымышленными значениями.

### Условие завершения

Для каждого обязательного факта известны источник, историческая глубина, time/finality semantics, quality и failure behavior. Provider выбран на проверяемом sample, а не по общему маркетинговому coverage.

## F2 — Schema и storage readiness

**Цель:** подготовить модель хранения до массового live stream/backfill.

### Работы

- Спроектировать marketdata representation для raw evidence, normalized swaps, price observations, liquidity observations и enrichment lineage.
- Выпустить только forward Flyway migrations; не переписывать V1–V4.
- Сохранить raw-first transaction separation, immutable retries и replayability.
- Определить idempotent identities для provider raw evidence и normalized domain facts.
- Добавлять indexes только под задокументированные point-in-time, gap, replay и backfill queries.
- По capacity evidence выбрать batching, partitioning/retention и способ представления dataset snapshots.
- Устранить N+1/member-by-member путь для больших dataset snapshots либо доказать bounded scope, при котором он безопасен.
- Проверить migrations и query behavior на PostgreSQL 18.6 через Testcontainers и volume-shaped test data.

### Условие завершения

Схема принимает ожидаемый объём без нарушения identity, replay, point-in-time и module ownership. Есть benchmark/estimate для backfill, snapshot и основных queries; массовый provider stream ещё не требуется для доказательства.

## F3 — Ingestion, backfill, gaps и operational visibility

**Цель:** получить реальные данные без скрытых пропусков и повторных эффектов.

### F3.1 Bounded live ingestion

- Явные concurrency, rate, queue, batch, timeout и finite retry limits.
- Provider I/O вне database transaction.
- PostgreSQL-backed claiming для recurring/recoverable jobs.
- Raw persistence до normalization.
- Secret-safe HTTP logging: API key, query string и provider payload не попадают в logs/exceptions.

### F3.2 Gap detection и recovery

- Хранить last stable position и provider cursor/state.
- На reconnect обнаруживать missed ranges.
- Идемпотентно восстанавливать диапазон.
- Сохранять gap windows со status и reason, а не скрывать их; минимальные причины включают reconnect, provider outage, local failure, rate limit и `PROVIDER_QUOTA_EXHAUSTED`.
- Остановка или отбрасывание данных из-за квоты всегда открывает явный gap; успешный reconnect сам по себе его не закрывает.
- Закрывать gap только после доказанного replay/backfill полного диапазона. При восстановлении из другого provider сохранять source/provenance и проверять эквивалентность identity/coverage.
- Помечать datasets/outcomes, пересекающие unresolved gaps.

### F3.3 Historical backfill

- Использовать принятые universe и historical ranges.
- Разделять actual ingestion time и modeled historical availability.
- Доказать restart/retry idempotency, отсутствие дубликатов и видимую неполноту.
- Не запускать wallet/signal research до достаточного warm-up coverage.

### F3.4 Минимальный monitoring

Без обязательной Grafana обеспечить:

- last successful ingest и lag/freshness;
- queue saturation и provider failures;
- использованную/оставшуюся provider quota, время её сброса, текущий burn rate, прогноз расхода до конца billing period и ожидаемую дату исчерпания;
- warning/critical notifications до исчерпания и отдельный actionable alert при hard stop; если provider не отдаёт usage telemetry, вести консервативный локальный счётчик и явно маркировать его точность;
- parse/normalization failure counts;
- coverage и unresolved gaps;
- bounded-cardinality structured logs;
- детерминированный data-quality report;
- хотя бы одно actionable notification при остановке ingestion или критическом lag/gap.

### F3.5 Deployment и operations guardrails

- До постоянного запуска JAR на VPS определить localhost bind, firewall allowlist или authenticated proxy/private network.
- Не публиковать health/readiness наружу по умолчанию.
- Сохранить одну datasource/Flyway identity как текущее осознанное упрощение; вернуться к разделению ролей перед broader unattended deployment.
- Предпочитать TLS `verify-full`; `require` считать переходным вариантом.
- Связать backup policy с классами восстановимости и capacity thresholds из F1.9, а не применять один режим ко всей базе.
- Невосполнимые research artifacts защищать off-host с первого момента их появления; для reloadable raw отдельно определить retention/cold archive или осознанно принятый повторный backfill.
- Зафиксировать RPO/RTO для каждого класса и продолжать restore drills, проверяющие не только открытие базы, но и critical manifests, snapshots, histories, outcomes и reports.
- До превышения установленного порога оставить ежедневный полный `pg_dump` как baseline; после порога перейти на выбранную volume-appropriate схему и доказать восстановление на близком к прогнозному объёме.

### F3.6 Data-quality gate

Провести длительный прогон и доказать freshness, bounded resource use, replay, reconnect recovery, parse quality, visible gaps и отсутствие повторных domain effects. Отдельно принудительно или детерминированно симулировать предупреждение и остановку по квоте: monitoring должен заранее показать depletion forecast, ingestion — открыть причинный gap, а recovery — заполнить и закрыть его только после полного replay/backfill.

### Условие завершения

Current и historical Solana data воспроизводятся с raw lineage, trusted observation time, parser identity и видимыми gaps. Остановка или деградация ingestion не остаётся незаметной.

## F4 — Point-in-time risk и wallet evidence

**Цель:** производить реальные decision-time facts вместо synthetic caller inputs.

### F4.1 Risk fact producers

- Определить Pump.fun/PumpSwap/Raydium lifecycle states и transitions.
- Производить common и Solana-specific facts с source, cutoff, version, freshness и quality.
- Явно обрабатывать missing/unknown facts.
- Не использовать current provider state для исторического risk decision.

### F4.2 Durable risk history

- Модуль `risk` владеет append-only decisions и своей Flyway schema.
- Signal candidate сохраняет immutable копию использованного decision-time evidence.
- Сохранять историю `BLOCK`, `WATCH_ONLY`, `ALLOW`, включая отклонённые candidates.

### F4.3 WATCH_ONLY semantics

Рекомендуемое направление: `WATCH_ONLY` не создаёт actionable ENTRY, но может создавать измеримый `OBSERVE`/shadow record и outcome. Решение должно быть принято отдельным spec и синхронизировано во всех документах.

### F4.4 Wallet analytics без leakage

- Восстанавливать cross-venue swaps и FIFO closed-trade PnL.
- Использовать только факты, доступные на calculation cutoff.
- Отделить wallet warm-up/training от signal evaluation window.
- Не подбирать tier thresholds и оценивать SMART_WALLET_BUY на одном и том же периоде.

### Условие завершения

Risk и wallet APIs возвращают воспроизводимые point-in-time snapshots; history и provenance полны, а сигнал не читает чужие таблицы или будущее состояние.

## F5 — Пререгистрированный research protocol

**Цель:** не позволить результату исследования стать post-hoc рационализацией.

Протокол фиксируется и получает version/fingerprint до performance-driven настройки signal families.

### Обязательные части

- Primary hypotheses, entry/avoidance families и horizons.
- Ограниченная configuration/threshold grid.
- Warm-up, exploratory, validation, holdout и optional walk-forward ranges.
- Point-in-time universe и matched control/baseline.
- Minimum raw и effective sample size.
- Confidence/uncertainty method с clustering по token и market wave/time bucket.
- Multiple-comparison policy.
- Outlier-dependence test и robust summaries.
- Missing, stale, unresolved-gap, dead-token и terminal-liquidity policy.
- Entry-lag scenarios.
- Position-size, friction и slippage sensitivity.
- Criteria `deepen`, `pivot`, `extend` и `stop`.
- Запрет менять protocol version после просмотра holdout без нового experiment.

### Условие завершения

Независимый reviewer может до запуска evaluation однозначно определить, какой результат считается положительным, отрицательным или недостаточным.

## F6 — Production signal и evaluation semantics

**Цель:** заменить first-slice constants полноценными, но всё ещё research-only правилами.

### F6.1 Trigger и dedup

Для каждой family определить event/schedule trigger, cadence, dedup bucket, cooldown, re-arm и material-change semantics. Replay и forward mode должны давать одинаковые candidates при одинаковых inputs.

### F6.2 LIQUIDITY_SPIKE baseline

Определить target instant, bounded baseline window, maximum age, minimum coverage, gap behavior и deterministic selection. Старое наблюдение за несколько дней не может считаться one-hour baseline.

### F6.3 Signal quality и confidence

- Включить price/liquidity quality facts и manipulation evidence.
- Применять provider-specific freshness/TTL.
- Снижать confidence или блокировать решение при критически неполных данных.
- Не превращать first-slice score `70/B/1.0000` в production calibration.

### F6.4 Entry и horizon admissibility

- Ввести versioned entry lag/reaction scenarios.
- Использовать entry только после полной availability + lag.
- Для каждого horizon определить допустимое price tolerance window.
- Поздняя сделка за пределами tolerance не считается ценой номинального горизонта.

### F6.5 Friction и executable valuation

- Учитывать notional/position size и depth.
- Использовать side-aware conservative price/impact.
- Учитывать entry и exit liquidity, venue fees, base/priority fees.
- Запускать sensitivity scenarios даже до полной simulator model.

### F6.6 Outcomes без survivorship bias

- Добавить `TERMINAL_NO_LIQUIDITY = -100%` по утверждённой policy.
- Сохранять `UNPRICED`/pending/missing outcomes и причины.
- Не считать среднюю только по surviving priced outcomes без явного coverage.
- Сохранять shadow outcomes rejected/WATCH candidates отдельно.
- Помечать outcomes, затронутые data gaps.

### F6.7 Дополнительные horizons и positions

- Добавить `4h/24h` только forward Flyway migration с tests.
- Реализовать virtual positions и exit rules как measurement, не как trading advice.
- Не смешивать entry и avoidance reports.

### Условие завершения

Каждый candidate получает воспроизводимый outcome или явный статус отсутствия результата. Latency, freshness, terminal cases, friction, quality и gaps учитываются одинаково в replay и forward mode.

## F7 — Evidence Report и research decision

**Цель:** сформировать отчёт, на основании которого можно честно продолжить или остановить направление.

### Report должен показывать

- family/configuration/horizon и полный provenance;
- score отдельно от measured return;
- sample size и effective sample size;
- expectancy after costs и uncertainty interval;
- distribution, drawdown и outlier dependence;
- matched control/baseline comparison;
- priced, unpriced, terminal и gap-affected coverage;
- source/quality/confidence breakdown;
- latency/friction sensitivity;
- entry и avoidance результаты отдельно;
- rejected-candidate analysis;
- protocol deviations, если они были.

### Decision gates

Пороговые значения берутся только из frozen F5 protocol. Итог может быть только:

- `DEEPEN` — family прошла все primary gates;
- `PIVOT` — другая family/avoidance direction выглядит перспективнее;
- `EXTEND` — данных недостаточно, заранее указано чего именно не хватает;
- `STOP` — подтверждённого edge нет.

### Условие завершения

Отчёт воспроизводим, не скрывает missing/terminal cases и не выдаёт exploratory результат за out-of-sample proof.

## F8 — Decision support и forward shadow

**Цель:** превратить подтверждённые результаты в полезный владельцу инструмент без исполнения сделок.

### F8.1 Runtime trigger

Добавить idempotent scheduler/worker role с PostgreSQL-backed claiming. Это не новый deployable и не отдельный сервис.

### F8.2 Decision feed

Каждая запись показывает:

- `OBSERVE`, `CONSIDER_ENTRY`, `AVOID` или `CONSIDER_EXIT`;
- signal family, score и reasoning;
- data confidence/freshness;
- risk decision;
- family evidence status (`UNVALIDATED`, `PROMISING`, `VALIDATED` или утверждённый эквивалент);
- measured out-of-sample expectancy/uncertainty для точной configuration и horizon;
- price/liquidity, assumptions, expiry и invalidation reason.

### F8.3 Delivery channel

Сначала выбрать один минимальный read-only channel: Telegram, digest или API/UI. Нужны deduplication, expiry, retry и delivery audit. Channel failure не должен создавать новый signal.

### F8.4 Owner positions и exits

Position-aware `CONSIDER_EXIT` возможен только после явного ввода/учёта позиции владельца. Measurement exit rules не становятся автоматически торговой рекомендацией.

### F8.5 Forward shadow

Провести период без денег и без orders. Сравнить live availability, alerts, missed signals, reaction latency и realized forward outcomes с backtest assumptions.

### Условие завершения

Владелец понимает, что произошло и почему, но система не подписывает и не отправляет транзакции. Forward results подтверждают или опровергают применимость backtest.

## F9 — PAPER, MANUAL и LIVE

**Статус:** Deferred.

F9 не входит в remediation текущего MVP. Он активируется только после положительного F7, успешного F8 и отдельного safety design.

Требуются отдельные ADR/OpenSpec для signing, venue policies, limits, kill switches, capital constraints, audit trail, PAPER execution semantics, MANUAL_CONFIRM и phased LIVE rollout.

## Рекомендуемая последовательность OpenSpec changes

Названия предварительные; точный scope утверждается перед созданием каждого change.

1. **Документационная синхронизация F0.** Поведение не меняется.
2. **Solana data/provider contract F1.** Requirements, spikes и решение provider; без production adapter.
3. **Marketdata live-schema readiness F2.** Forward migrations и volume evidence.
4. **Bounded provider ingestion F3.1.** Один основной provider, raw-first, без скрытых fallback.
5. **Backfill, gaps and data-quality operations F3.2–F3.6.**
6. **Research protocol F5.** Зафиксировать до performance tuning; может идти параллельно позднему Stage 3.
7. **Point-in-time risk history and producers F4.1–F4.3.**
8. **Wallet analytics with warm-up F4.4.**
9. **Production signal definitions and dedup F6.1–F6.3.**
10. **Cost-aware multi-horizon evaluation F6.4–F6.7.**
11. **Evidence Report and decision gates F7.**
12. **Decision support and forward shadow F8.**
13. **Execution safety F9**, только если предыдущие gates положительны.

## Ближайшие три задачи

1. Выполнить F0: синхронизировать Roadmap/Delivery Plan/Glossary/Operations без изменения поведения.
2. Оформить F1 как design-first OpenSpec change и построить capability-driven provider-consumer matrix. Включить Helius, Alchemy Yellowstone, Triton Fumarole, Chainstack Yellowstone, SQD и специализированные источники; Helius Free проверяет только доступные ему capability, а не mainnet gRPC/replay.
3. После выбора контракта выполнить F2 до написания live provider adapter.

## Traceability первого аудита

| Finding | Покрытие |
|---|---|
| B1 USD price/liquidity enrichment | F1.6, F2 |
| B2 finality/reorg | F1.2 |
| B3 event locator/raw granularity | F1.3 |
| B4 availability/entry delay | F1.4, F5, F6.4 |
| B5 survivorship bias | F5, F6.6, F7 |
| B6 statistical protocol | F5, F7 |
| B7 storage volume | F1.9, F2 |
| G1 decision support | F8 |
| G2 WATCH_ONLY conflict | F0, F4.3, F8 |
| G3 risk facts producer | F4.1 |
| G4 LIQUIDITY_SPIKE baseline/confidence | F6.2, F6.3 |
| G5 universe/schedule/dedup | F1.5, F6.1 |
| G6 friction | F5, F6.5 |
| G7 wallet same-sample bias | F1.8, F4.4, F5 |
| G8 forward shadow before signing | F8, F9 |
| G9 observability contradiction | F0, F3.4 |
| G10 operations/security | F3.1, F3.5, F3.6 |
| G11 documentation drift | F0 |

## Traceability аудита GLM 5.3 MAX

| Finding | Покрытие |
|---|---|
| BL-1 alerts/decision support | F8 |
| BL-2 provider requirements matrix | F1.1, F1.10 |
| BL-3 observability exit | F0, F3.4, F3.6 |
| BL-4 statistical gates | F5, F7 |
| IMP-1 entry latency | F5, F6.4 |
| IMP-2 horizon staleness | F6.4, F6.6 |
| IMP-3 stale liquidity baseline | F6.2 |
| IMP-4 90d warm-up conflict | F1.8, F4.4, F5 |
| IMP-5 token universe | F1.5 |
| IMP-6 rolling dedup | F6.1 |
| IMP-7 wash trading/price quality | F1.7, F6.3, F7 |
| IMP-8 friction | F5, F6.5 |
| IMP-9 risk history ownership | F4.2; ownership уже определён |
| OPS-A server bind | F3.5 |
| OPS-B plaintext local secret | Текущее принятое ограничение; F3.5 для будущего deployment |
| OPS-C provider timestamps | F1.4 |
| Documentation defects | F0 |
| `strategy_experiment_id` drift | F0, F5 |
| 4h/24h migration | F0, F6.7 |

## Traceability дополнительных находок владельца

| Finding | Важность | Покрытие |
|---|---|---|
| Исчерпание provider quota создаёт скрытый data gap | Высокая | F1.1, F1.10, F3.2, F3.4, F3.6 |
| Ежедневный full `pg_dump` не масштабируется вместе с raw volume | Высокая | F1.9, F3.5, F3.6 |
| Provider shortlist и spikes не отражают transport/replay/history capabilities | Высокая до выбора provider | F1.1, F1.10, ближайшая задача 2 |

## Когда этот план считается выполненным

План закрывается не тогда, когда исправлены документы, а когда:

- real Solana data имеет доказуемые identity, finality, availability, quality и lineage;
- ingestion/backfill не скрывают gaps и operational failures;
- risk, wallet, signal и evaluation используют только point-in-time evidence;
- research protocol зафиксирован до проверки edge;
- Evidence Report учитывает costs, latency, missingness, dead tokens, controls и uncertainty;
- владелец получает понятный advisory feed и forward shadow evidence;
- PAPER/LIVE остаются недоступны до отдельного положительного safety decision.
