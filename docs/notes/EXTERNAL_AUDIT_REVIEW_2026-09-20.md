# Оценка внешнего аудита от 20 сентября 2026 года

## Статус документа

Это ненормативный аналитический документ Control по внешнему аудиту репозитория в commit `5922805`. Он не изменяет принятые ADR, main specs, Roadmap, Delivery Plan или текущую реализацию и не является планом разработки. Следующий план должен быть оформлен отдельно после принятия перечисленных ниже решений.

Внешний аудитор не запускал Maven-проверку из-за ограничений своей песочницы. Его замечания оценивались по актуальному коду, main specs и документации того же commit. В рамках этой оценки код и тесты также не изменялись.

## Итоговая позиция

Аудит качественный и выявил реальные разрывы между узким recorded-срезом и будущей системой на реальных Solana-данных. Архитектурный фундамент не требуется переписывать, но переход к live ingestion нельзя выполнять как простое подключение Helius или другого провайдера к существующему `RecordedSwapParser`.

Из 18 основных замечаний:

- полностью подтверждены 11;
- частично подтверждены 7;
- полностью отклонённых замечаний нет;
- отдельные технические утверждения внутри B1, B7 и G10 не доказаны или сформулированы слишком категорично.

Главный вывод: перед записью реального потока нужны явные контракты Solana-данных, времени доступности, finality, event locator, universe и ожидаемого объёма. До оценки эффективности сигналов нужен заранее зафиксированный статистический протокол. Между Evidence Report и любым исполнением нужен отдельный слой поддержки решений и forward shadow-наблюдения.

## Шкала вердиктов

- **Согласен** — замечание подтверждается текущими источниками и должно влиять на план.
- **Согласен частично** — основная проблема существует, но срок, масштаб, формулировка или часть аргумента требуют корректировки.
- **Не согласен** — проблема не подтверждается. На уровне целого пункта таких вердиктов нет.

## Blocking findings

### B1. USD-цена и ликвидность отсутствуют в сырой Solana-транзакции

**Вердикт: согласен частично с формулировкой, полностью согласен с проблемой контракта.**

Подтверждено:

- `RecordedSwapParser` требует готовые `priceUsd` и `liquidityUsd` в recorded payload.
- V2 хранит оба значения как `NOT NULL` в `marketdata.normalized_swaps`.
- Один raw transaction response сам по себе не даёт универсальные, уже нормализованные USD price и point-in-time pool liquidity для всех поддерживаемых venues.
- Текущая модель смешивает собственно swap, ценовое наблюдение и liquidity enrichment в одной записи. Это нельзя автоматически переносить на реального провайдера.
- При текущей модели этап 3.3 действительно зависит от решений, заявленных только в 3.5.

Уточнение аудитору:

- decimals не всегда обязательно читать отдельным запросом из mint account. Официальная структура Solana transaction metadata включает `decimals` внутри `preTokenBalances` и `postTokenBalances`, когда эти поля доступны.
- Утверждение об исторической ликвидности по vault balances в целом направлено верно: стандартный `getAccountInfo` принимает commitment и `minContextSlot`, но не параметр для чтения произвольного прошлого состояния аккаунта. Однако точный способ восстановления зависит от конкретного DEX-протокола и доступного provider payload, поэтому его нельзя утвердить до provider/venue spike.

Необходимое решение: до реального нормализатора разделить или явно связать:

1. raw transaction/provider evidence;
2. нормализованный swap в chain-native quantities;
3. point-in-time price observation;
4. point-in-time liquidity observation;
5. USD conversion source и confidence.

Контракт derivation для Pump.fun/PumpSwap/Raydium должен указывать исходные поля, формулу, slot/time semantics, источник SOL/USD, качество и поведение при неполных данных. Реализацию 3.3 и 3.5 можно оставить отдельными пакетами, но их семантический дизайн должен быть единым.

Источники: [Solana RPC JSON structures](https://solana.com/docs/rpc/json-structures), [Solana `getAccountInfo`](https://solana.com/docs/rpc/http/getaccountinfo).

### B2. Finality и reorg не выделены перед real-time ingestion

**Вердикт: согласен.**

Main spec уже запрещает provisional ingestion без отдельного approved change, а Delivery Plan сразу переводит 3.2 к real-time ingestion. Это скрытая обязательная развилка.

До 3.2 нужно выбрать и проверить один из контрактов:

- принимать только `finalized` evidence и принять дополнительную задержку;
- принимать `confirmed`/другое provisional evidence с явной моделью rollback, canonicality и reorg reconciliation;
- разделить provisional transport journal и stable domain admission.

Точное утверждение аудитора о задержке «десятки секунд» не следует фиксировать как инвариант без измерений выбранного provider. Официальная документация подтверждает различие `processed`, `confirmed` и `finalized`, но фактическая latency должна быть измерена.

Источник: [Solana RPC commitment levels](https://solana.com/docs/rpc#configuring-state-commitment).

### B3. Не определена Solana-грамматика event locator и гранулярность raw evidence

**Вердикт: согласен.**

Identity уже зависит от canonical parser-independent locator, но Solana grammar отсутствует. До первой реальной записи нужно определить:

- является ли raw observation полной транзакцией, instruction event или decoded swap event;
- хранится ли полный provider payload один раз или повторяется для каждого нормализуемого события;
- как кодируются outer instruction, inner instruction, CPI depth и несколько swap legs;
- как locator остаётся одинаковым между провайдерами и parser versions;
- как reparse создаёт новую lineage, не меняя identity того же blockchain event.

Без этого смена парсера или провайдера может создать несовместимые identities и потребовать дорогую forward migration.

### B4. Не определены availability time и задержка входа

**Вердикт: согласен.**

Текущая модель различает source event time, observed time и ingestion time, но не определяет правила заполнения `observed_at` для live, reconnect recovery и historical backfill. Также `availableAt` первого сигнала равен decision cutoff, а модель provider, processing и human reaction latency отсутствует.

Нужно отдельно определить:

- chain event time/slot;
- provider-visible time;
- system-received time;
- stable/admitted time после finality policy;
- actual processing time;
- modeled availability time для historical replay;
- signal publication time;
- configurable execution/reaction delay.

Для live нужны фактически измеренные timestamps. Для backfill нельзя подменять availability ни временем текущей загрузки, ни block time с неявной нулевой задержкой. Нужна версионированная консервативная latency model, иначе backtest и forward observation несопоставимы.

### B5. Survivorship bias в текущем шаблоне оценки

**Вердикт: согласен частично.**

Риск подтверждён:

- horizon query допускает самое раннее наблюдение после `1h` вплоть до evaluation cutoff без максимальной допустимой задержки;
- умерший или исчезнувший токен остаётся `UNPRICED`;
- будущая агрегация только priced outcomes систематически завысит результат;
- Roadmap уже требует `TERMINAL_NO_LIQUIDITY = -100%`, но первый срез намеренно не реализует эту политику.

Это не дефект принятого узкого first-slice contract: main spec требует сохранять и считать `UNPRICED`, а один-сигнальный report не заявляет статистический edge. Это блокер перед обобщением evaluation в Stage 5, а не основание переписывать завершённый демонстрационный срез как будто он уже production backtest.

Перед Stage 5 нужны maximum price age, terminal-state policy, unresolved-gap policy и агрегаты, которые явно показывают priced/unpriced/terminal coverage.

### B6. Нет заранее зафиксированного статистического протокола

**Вердикт: согласен.**

Текущие формулировки про `0.15R`, «не маленькую» выборку и отсутствие одного доминирующего выброса недостаточны. До просмотра и настройки performance-результатов реальных сигналов следует зафиксировать:

- primary hypotheses, families и horizons;
- допустимую configuration/threshold grid;
- exploratory, validation и holdout periods;
- out-of-sample или rolling walk-forward процедуру;
- минимальный effective sample size;
- доверительные интервалы с кластеризацией по token и market wave/time bucket;
- matched control/baseline universe;
- multiple-comparison policy;
- missing, stale, dead-token и unresolved-gap policy;
- latency и friction sensitivity;
- критерии deepen, pivot, extend и stop.

Сырые данные можно изучать для проверки схемы и качества. Нельзя до фиксации протокола использовать outcome-результаты для многократной настройки гипотез, а затем выдавать тот же период за независимое доказательство.

### B7. Объём данных не согласован с хранением

**Вердикт: согласен частично.**

Подтверждено:

- snapshot finalization выполняет отдельный lookup для каждого member;
- membership записывается построчно;
- текущая реализация материализует snapshot observations в памяти;
- ожидаемый реальный объём не оценён;
- решение нужно принять до массового backfill и длительного stream.

Не подтверждено:

- утверждение, что V1 уже нарушила Roadmap. Для V1 был явно принят unpartitioned natural-key contract, а Roadmap обозначает partitioning как table-specific решение по измеренному объёму;
- утверждение, что модель «не работает», пока нет оценки событий, payload size, retention, query patterns и benchmark;
- оценка реального потока «на порядки выше» без зафиксированного universe и provider filter.

Тем не менее до 3.2/3.6 нужен capacity envelope: events/day, bytes/day, indexes, retention, expected backfill, snapshot frequency и query shapes. После этого принимаются решения о partitioning, batch ingestion и представлении dataset snapshots. Массовые данные не должны попасть в текущую схему до такой проверки.

## Важные gaps

### G1. Нет слоя поддержки решений

**Вердикт: согласен.**

Delivery Plan действительно переходит от Evidence Report почти сразу к execution. Для конечной цели пользователя нужен отдельный этап `Decision support and forward shadow` между исследовательским решением и исполнением:

- runtime trigger/scheduler;
- actionable feed и история уведомлений;
- deduplication, expiry и acknowledgement;
- действия `OBSERVE`, `CONSIDER_ENTRY`, `AVOID`, `CONSIDER_EXIT`;
- family evidence status и measured expectancy отдельно от score;
- signal rationale, freshness, risk, price/liquidity и invalidation conditions;
- Telegram как возможный первый канал, но только после отдельного выбора;
- tracked owner positions для position-aware exit/risk alerts;
- no-money forward shadow period.

Этот слой не должен превращать исследовательский score в безусловную команду купить или продать.

### G2. Противоречие WATCH_ONLY

**Вердикт: согласен.**

Сейчас существуют несовместимые значения:

- main spec и код: принимается только `ALLOW`; `WATCH_ONLY` не создаёт accepted ENTRY signal;
- Roadmap/Project Summary: `WATCH_ONLY` создаёт ENTRY с cap 69;
- Glossary: наблюдать, но не торговать.

Main spec и код являются текущим принятым поведением first slice. Для будущей системы нужно отдельное решение. Рекомендуемая семантика: `WATCH_ONLY` не создаёт actionable ENTRY, но может создать измеримый research observation/`OBSERVE` alert и shadow outcome. Называть это торговым ENTRY-сигналом не следует.

### G3. Никто не производит risk facts

**Вердикт: согласен.**

Первый срез принимает synthetic validated facts от caller. Это было допустимо для recorded skeleton, но real pipeline должен определить producers, provenance и point-in-time semantics для manipulation flags, lifecycle и liquidity evidence.

Перед реальным запуском signal detector нужны определения Pump.fun/PumpSwap lifecycle transitions, producer common/Solana facts и поведение при неполных фактах. Это скрытая зависимость Stage 3/4, которую следует сделать явной. При этом Stage 3 может собирать данные без запуска real signal decisions.

### G4. LIQUIDITY_SPIKE расходится с будущей семантикой

**Вердикт: согласен частично.**

Подтверждено: baseline выбирается как последнее наблюдение от `Instant.EPOCH` до `windowStart`, поэтому его возраст не ограничен. Фраза main spec «deterministic one-hour baseline» недостаточно точно определяет tolerance и sampling semantics.

Не является текущим дефектом:

- score 70, grade B и confidence 1.0000 намеренно закреплены accepted first-slice spec;
- stale-data cap находится в target Roadmap и не входил в scope первого среза.

Перед production detector нужно определить baseline selection, maximum age, minimum window coverage, freshness, gaps и confidence degradation. First-slice constants не должны автоматически становиться production scoring model.

### G5. Не определены universe и расписание детекции

**Вердикт: согласен.**

Без point-in-time token universe невозможно доказать отсутствие selection bias. Нужны:

- правила token discovery и eligibility;
- inclusion/exclusion timestamps и причины;
- venue/program scope;
- schedule или event trigger каждого detector;
- одинаковая trigger semantics в replay и forward mode;
- explicit dedup bucket per family.

Universe contract должен появиться до исторического backfill, иначе набор исследуемых токенов будет выбран с использованием будущего знания.

### G6. Friction model слишком оптимистична

**Вердикт: согласен.**

Фиксированный haircut первого среза полезен только как доказательство exact arithmetic. Для исследования мемкоинов нужны как минимум:

- position/notional size;
- side-aware executable quote или conservative price impact;
- pool liquidity/depth на входе и выходе;
- venue/protocol fees;
- Solana base и priority fees;
- failed transaction/retry allowance для будущей manual/paper comparability;
- slippage/latency sensitivity scenarios.

Модель должна быть versioned и консервативной. Цена чужой сделки сама по себе не является гарантированной ценой нашего входа.

### G7. Wallet scoring на той же выборке

**Вердикт: согласен.**

Wallet metrics, tier thresholds и signal evaluation нельзя обучать и оценивать на одном и том же окне. Нужны warm-up/training period, point-in-time score history и отдельное forward/holdout evaluation window. Иначе SMART_WALLET_BUY получает leakage и selection bias даже при корректных SQL cutoffs.

### G8. Forward shadow ошибочно связан с signing safety

**Вердикт: согласен частично.**

No-money forward shadow действительно должен появиться до signing ADR и real execution safety. Он проверяет live latency, availability, alerts и расхождение с backtest.

Однако термин `paper trading` в текущем проектном контракте относится к deferred execution stage. Поэтому следует разделить:

- **forward shadow observation** — без order simulation/execution и без капитала, до Stage 7;
- **paper execution simulation** — после отдельного принятого дизайна execution semantics, но всё ещё без денег;
- **manual/live execution** — только после safety gates.

### G9. Противоречие observability

**Вердикт: согласен частично.**

Минимальная operational visibility обязательна для data-quality gate: last successful ingest, lag/freshness, queue saturation, provider failures, parse failures и unresolved gaps с actionable alert при остановке.

Но из этого не следует необходимость немедленно ставить Prometheus/Grafana. Tech Stack корректно откладывает внешнюю observability infrastructure до доказанной необходимости. Roadmap ошибочно называет Grafana конкретным DoD, хотя текущая архитектура требует capability, а не этот продукт.

### G10. Операционные и security-риски

**Вердикт: согласен частично; пункт нужно разделять.**

- **Одна runtime/Flyway identity с DDL:** риск реальный и уже документирован как осознанное упрощение пользователя. Это не незамеченный дефект Stage 3.0. Разделение ролей следует пересмотреть перед unattended/broader deployment, но не возвращать как обязательную текущую задачу без нового решения пользователя.
- **Network policy вне repository acceptance:** это не означает отсутствие защиты; оператор сообщил о UFW, fail2ban и сложном пароле. Репозиторий не управляет сервером. При этом `sslmode=require` остаётся переходным, поскольку не аутентифицирует сервер; `verify-full` предпочтителен.
- **Backup на том же хосте:** аудит этого не доказал. Документация сообщает механизм, retention и restore drill, но не место хранения. Это открытый операторский вопрос, а не установленный факт.
- **Retention 5 copies:** допустимо для текущего раннего MVP, но по мере ценности данных потребуется определить off-host copy, retention tiers и recurring restore drill.
- **Provider key в URL:** адаптера пока нет, поэтому текущего leak нет. Provider change обязан запретить логирование полного URI/query, HTTP debug payloads и exception messages с секретом.

### G11. Дрейф Roadmap

**Вердикт: согласен.**

Roadmap явно маркирует многие детали как target draft, но внутри остаются противоречивые и устаревшие указания: Helius как будто уже выбран, Helius WebSocket как MVP baseline, старый budget/timeline, `internal` package tree и Grafana-specific DoD.

Это опасно именно потому, что AGENTS направляет агентов в Roadmap за product direction. Перед следующей реализацией Roadmap нужно синхронизировать с Delivery Plan и текущей архитектурой, сохранив исторические материалы в archive при необходимости. Provider prices и limits должны оставаться датированными исследовательскими данными, а не нормативными фактами.

## Поймёт ли пользователь, когда наблюдать, входить, избегать или выходить

**Вердикт аудитора подтверждён: сейчас нет.**

Текущая система доказывает исследовательский pipeline, но не является пользовательским decision tool. Score/grade, risk decision, statistical evidence и recommended action пока не разведены в точке потребления.

Будущая модель должна показывать отдельно:

| Поле | Значение |
|---|---|
| Signal score | Качество текущего набора decision-time факторов, не прогноз доходности |
| Data confidence | Полнота, свежесть и надёжность данных |
| Risk decision | Можно ли использовать наблюдение как ENTRY-кандидат |
| Family evidence status | `UNTESTED`, `REJECTED`, `PROMISING`, `VALIDATED` или иной утверждённый набор |
| Measured edge | Out-of-sample expectancy/interval после costs для family/configuration |
| Suggested action | `OBSERVE`, `CONSIDER_ENTRY`, `AVOID`, `CONSIDER_EXIT`, но не автоматическая команда |
| Validity | Время жизни, invalidation reason и условия отмены |

Sell/exit advice невозможно корректно сформировать без tracked position, entry context и отдельно утверждённых exit rules. Outcome exit rules для backtest не должны молча становиться рекомендацией реальному владельцу позиции.

## Необязательные улучшения из аудита

| Предложение | Позиция Control |
|---|---|
| Помечать outcomes, пересекающие unresolved data gaps | Согласен; это часть data-quality и missingness policy, а не косметика. |
| Sensitivity по latency, friction и outliers | Согласен; включить в research protocol и Evidence Report. |
| Показывать decay 1h/4h/24h | Согласен после реализации нескольких горизонтов. |
| Человекочитаемый report раньше Phase 8 | Согласен; минимальная форма нужна для проверки смысла результатов и будущих alerts. |
| Отдельная Flyway identity | Частично; полезно позже, но пользователь осознанно выбрал одну identity для текущего MVP. |
| Регулярный restore drill | Согласен как будущая операционная процедура; периодичность зависит от ценности и темпа накопления данных. |
| Синхронизировать Roadmap до Stage 4 | Согласен; лучше до следующего implementation change, чтобы устаревшие детали не управляли агентом. |

## Оценка рекомендованной последовательности аудитора

1. **Расширить Stage 3.1 — согласен.** Exit должен включать finality, raw granularity/locator, availability semantics, universe, price/liquidity derivation, volume envelope и minimum operational visibility, а не только тариф/coverage провайдера.
2. **Связать 3.3 и 3.5 семантически — согласен.** Не обязательно объединять реализацию в один большой change; сначала нужен общий data contract.
3. **3.2 → 3.4 → 3.6 → 3.7 — согласен с условием.** До первой реальной записи должны быть приняты schema/data contracts и capacity decision. Token universe определяется до backfill. Минимальный stall/gap alert входит в data-quality gate.
4. **Добавить research protocol в конце Stage 3 — согласен по сути.** Жёсткий дедлайн — до просмотра и оптимизации signal outcome performance. Data engineering и quality exploration допустимы раньше.
5. **Stage 4 начать с lifecycle и producers risk facts — согласен.** Wallet warm-up/holdout также обязателен.
6. **Stage 5 и 6 выполнять по зафиксированному протоколу — согласен.**
7. **Добавить decision support и forward shadow между 6 и 7 — согласен.** Forward shadow отделяется от paper execution.
8. **Оставить money execution в Stage 7 — согласен.** Signing, order submission и live остаются deferred до доказательств и отдельного safety review.

## Ответ внешнему аудитору

Спасибо, аудит принят как содержательный и в основном подтверждённый. Он правильно выявил, что завершённый recorded vertical slice доказывает архитектуру и воспроизводимость, но не определяет семантику реального Solana ingestion, статистическую валидность исследования или пользовательский decision-support слой.

Мы полностью принимаем необходимость до live ingestion определить finality/reorg policy, Solana event-locator grammar, raw granularity, availability/backfill time semantics, token universe и detector schedule. Также принимаем необходимость до оценки реального edge зафиксировать статистический протокол, terminal/missing-price policy, realistic latency/friction assumptions, wallet warm-up и out-of-sample validation.

По B1 принимается основная проблема: текущий recorded payload уже содержит enrichment, который нельзя считать непосредственным on-chain фактом. Уточнение: decimals могут присутствовать в Solana transaction metadata, поэтому отдельное чтение mint account не является универсально обязательным. Но USD conversion и point-in-time liquidity всё равно требуют отдельного доказуемого derivation/source contract.

По B5 и G4 текущий код не объявляется дефектным относительно принятого first-slice spec: terminal liquidity, production freshness и calibrated scoring были явными non-goals. Эти пункты становятся обязательными до Stage 5 generalization.

По B7 принимается необходимость capacity model и benchmark до массовой загрузки. При этом существующий unpartitioned V1 был явным принятым решением, а утверждение о гарантированной несостоятельности хранения требует фактической оценки выбранного universe и потока.

По G9 принимается обязательная operational visibility, но не обязательность Grafana: конкретная внешняя observability platform остаётся evidence-driven. По G10 single database identity является осознанным текущим упрощением; предположение о хранении backup на том же хосте не подтверждено имеющимися данными.

Главное продуктовое замечание также принимается: между Evidence Report и execution будет спроектирован decision-support/alerting слой с явным разделением score, data confidence, risk decision, family evidence и suggested action, а также no-money forward shadow validation.

## Подтверждённые пакеты для следующего планирования

Следующий план должен рассмотреть как минимум пять отдельных пакетов решений. Их границы и порядок ещё не являются принятым OpenSpec scope.

1. **Синхронизация планирующих документов:** убрать противоречивые provider, timeline, package и Grafana-specific указания.
2. **Solana data contract и provider selection:** finality, locator, raw granularity, availability, universe, venue scope, enrichment provenance и provider spike.
3. **Storage and ingestion readiness:** capacity envelope, schema evolution, batching/partitioning decision, recovery, gaps и минимальные operational alerts.
4. **Research validity protocol:** holdout/walk-forward, controls, clustering, multiplicity, dead-token/missingness, latency/friction sensitivity и decision gates.
5. **Decision support and forward shadow:** actionable taxonomy, alerts, evidence status, owner positions и проверка поведения без денег до PAPER/LIVE.

Сначала нужно обновить общую последовательность в Roadmap/Delivery Plan, затем оформлять небольшие OpenSpec changes в порядке зависимостей. Подключение конкретного provider adapter не должно предшествовать принятию его data contract.
