# Оценка внешнего аудита GLM 5.3 MAX от 20 сентября 2026 года

## Статус документа

Это ненормативная оценка Control по второму внешнему аудиту репозитория в commit `5922805`. Документ дополняет [оценку первого внешнего аудита](EXTERNAL_AUDIT_REVIEW_2026-09-20.md), но не изменяет ADR, main specs, Roadmap, Delivery Plan, OpenSpec или реализацию.

Цель документа — независимо проверить каждый вывод GLM 5.3 MAX, отделить новые проблемы от повторов первого аудита, исправить фактические ошибки и подготовить ответ аудитору. Общий план доработки должен быть оформлен отдельно после объединения результатов обоих аудитов.

## Итоговая позиция

GLM подтвердил основную картину первого аудита: фундамент системы качественный, но между recorded research skeleton и практическим инструментом отсутствуют несколько обязательных контрактов. Особенно ценны четыре уточнения:

1. выбор провайдера должен начинаться с матрицы потребителей данных;
2. 90-дневный backfill несовместим с одновременным 90-дневным wallet warm-up и полноценным 90-дневным evaluation window;
3. point-in-time integrity требует политики доверия provider timestamps;
4. одиночный swap нельзя автоматически считать качественной и исполняемой ценой в среде с wash trading и низкой ликвидностью.

Оценка 13 основных findings:

- полностью согласен — 10;
- согласен частично — 2;
- не согласен — 1.

Операционные пункты оценены отдельно: один принят как будущий deployment guardrail, один уже является осознанным решением, один добавляет обязательное правило для provider adapter.

## Фактические поправки к аудиту

Перед оценкой содержания необходимо исправить несколько неточностей отчёта:

- в репозитории находятся девять принятых ADR `0001`–`0009`, а не пять;
- GoPlus уже документирует `Token Security API for Solana`; считать сервис только EVM-ориентированным нельзя;
- Helius документирует holder count/token holder APIs, а Bitquery — Solana balances/holders и DEX history. Это не доказывает пригодность бесплатных тарифов, историческую полноту или point-in-time корректность, но опровергает утверждение, что holder data «очевидно» не покрывается кандидатами;
- risk evidence отклонённого кандидата уже сохраняется в `signal.signal_candidates`, а не только в `signal.accepted_signals`;
- в текущем рабочем дереве нет незакоммиченного удаления `docs/archive/LEGACY_V5_ARCHITECTURE_MAPPING.md`: удаление уже является commit `5922805`. Текущий новый untracked-файл — оценка первого аудита;
- после удаления archive-файла в `docs/GLOSSARY.md` действительно осталась битая ссылка, и это отдельный documentation defect.

## BLOCKING FINDINGS

### BL-1. Alerts и decision support отсутствуют в плане

**Вердикт: согласен.**

Это подтверждает G1 первого аудита. Текущая цепочка заканчивается Evidence Report и переходит к deferred execution. Реализованный JAR не имеет бизнес-триггера, API, CLI, scheduler, digest или канала уведомлений. Actuator health не является пользовательским интерфейсом.

Проблема не в том, что alerts нужны прямо сейчас. Проблема в отсутствии отдельного этапа между исследовательским решением и PAPER/LIVE. Без него успешное исследование не превращается в инструмент, который объясняет владельцу:

- что произошло;
- насколько свежи и полны данные;
- какой risk decision получен;
- доказана ли signal family;
- следует наблюдать, рассматривать вход, избегать или рассматривать выход;
- когда рекомендация устаревает или отменяется.

Нужен отдельный этап `Decision support and forward shadow`, не содержащий signing или order submission.

### BL-2. Provider selection выполняется без матрицы требований данных

**Вердикт: согласен с проблемой; часть аргументов аудитора устарела.**

Текущий Stage 3.1 говорит о coverage, limits, terms и failure semantics, но не перечисляет потребности downstream consumers. Слово `coverage` слишком общее: один provider может отлично покрывать transactions и не давать воспроизводимую историческую liquidity series или holder series.

До выбора провайдера нужна матрица минимум по следующим измерениям:

| Потребитель | Требуемые факты | Историческая глубина | Freshness/latency | Point-in-time требование |
|---|---|---|---|---|
| Raw/replay | transaction, instruction/CPI, balances, slot, finality | backfill + replay retention | bounded | exact payload и stable locator |
| Price/outcomes | native/USD price, source, confidence, executable liquidity | warm-up + evaluation horizons | horizon-dependent | no future revisions |
| `LIQUIDITY_SPIKE` | pool/venue liquidity series | baseline + evaluation | near-real-time | freshness-bound baseline |
| `HOLDER_GROWTH` | holder count series, owner dedup rules | минимум 4h, практически дольше | bounded | snapshot availability time |
| Risk | authorities, concentration, LP state, lifecycle, manipulation evidence | decision-time history | signal-time | immutable provenance |
| Wallet | complete cross-venue swaps/transfers and prices | lookback + evaluation window | bounded | no future trades/prices |
| Gaps/quality | slot coverage, reconnect ranges, parse/failure facts | весь dataset | operational | unresolved windows visible |

Уточнения по текущим кандидатам:

- публичная документация Helius заявляет holder count/token holder access и historical transaction facilities, но конкретная историческая holder series, тарифные лимиты и воспроизводимость всё равно требуют spike;
- Bitquery документирует Solana DEX history, balances и holder-oriented queries, но coverage, dataset quirks, retention, price semantics и стоимость должны проверяться на нашей выборке;
- DexScreener public API отдаёт current pair price/liquidity snapshots. Публичный reference не подтверждает готовый исторический snapshot feed, поэтому историю, вероятно, придётся собирать самим или брать из другого источника;
- GoPlus документирует Solana Token Security API, но точные поля, provenance, TTL, historical access и пригодность для наших risk rules нельзя предполагать.

Следовательно, вывод GLM о необходимости матрицы полностью верен, а вывод о заведомой неспособности кандидатов покрыть holder/risk data — нет.

Источники: [Helius token APIs](https://www.helius.dev/solana-token-apis), [Helius historical data](https://www.helius.dev/historical-data), [Bitquery Solana API](https://docs.bitquery.io/docs/blockchain/Solana/), [Bitquery DEX trades](https://docs.bitquery.io/docs/blockchain/Solana/solana-dextrades/), [DexScreener API](https://docs.dexscreener.com/api/reference), [GoPlus API overview](https://docs.gopluslabs.io/reference/api-overview).

### BL-3. Невыполнимый observability exit criterion

**Вердикт: согласен частично.**

Противоречие подтверждено: Roadmap называет Grafana в DoD, а Tech Stack откладывает Prometheus/Grafana. Для Stage 3.7 действительно нужны измеримые freshness, parse failures, coverage и gaps, а остановка ingestion не должна оставаться незаметной.

Но отсутствие готового observability change сейчас не делает Stage 3 уже невыполнимым: 3.7 ещё `Planned`, и соответствующее поведение должно проектироваться вместе с ingestion/gap recovery. Блокером является не отсутствие Grafana, а отсутствие минимального operational contract до завершения Stage 3.

Минимум может быть реализован без внешней observability platform:

- module-owned operational state/queries;
- structured logs с bounded cardinality;
- детерминированный health/data-quality report;
- last-success, lag, coverage, parse failure и unresolved-gap counters;
- одно actionable notification при остановке или превышении порога;
- доказательство длительным прогоном.

Конкретный выбор Grafana не должен быть exit criterion без отдельного evidence-backed change.

### BL-4. Статистические гейты не квантифицированы

**Вердикт: согласен.**

Это подтверждает B6 первого аудита. `expectancy_R >= 0.15R` без minimum effective N, uncertainty interval, outlier policy, multiple-testing policy, control group и holdout не создаёт честного gate.

Статистический протокол требуется не просто «на этапе 6 перед backtest», а до просмотра и оптимизации performance outcomes на реальных данных. Допустимо раньше исследовать raw data quality и coverage; недопустимо многократно подбирать signal thresholds по одному периоду, а затем считать его независимой проверкой.

## ВАЖНЫЕ GAPS

### IMP-1. Нереалистичная entry latency

**Вердикт: согласен.**

Это частный и точный вариант B4 первого аудита. Первый admissible observation через одну секунду годится для deterministic fixture, но не для доказательства применимого edge.

Перед Stage 5 нужны versioned latency scenarios:

- system/provider processing delay;
- notification delay;
- manual reaction delay;
- optional future automated reaction delay;
- entry price selection после полного delay;
- sensitivity across several conservative delays.

Первый срез не является дефектным относительно своего accepted spec, но его zero/near-zero-lag assumption нельзя переносить в Evidence Report.

### IMP-2. Нет staleness tolerance для horizon price

**Вердикт: согласен.**

Текущий код может назвать сделку через несколько часов первой ценой горизонта `1h`, если evaluation cutoff это допускает. Для production evaluation необходим versioned admissibility window, например целевой horizon плюс разрешённый tolerance. Точное число нельзя выбирать без анализа trade frequency и data gaps.

При отсутствии цены в admissibility window outcome должен оставаться явным `UNPRICED`, `TERMINAL_NO_LIQUIDITY` или другим утверждённым состоянием, а не использовать произвольно позднюю сделку.

### IMP-3. Неограниченный возраст baseline в LIQUIDITY_SPIKE

**Вердикт: согласен.**

Это подтверждает G4 первого аудита. Запрос от `Instant.EPOCH` до `windowStart` не гарантирует one-hour baseline. Нужны точные правила:

- baseline target instant;
- допустимое окно около target;
- maximum observation age;
- minimum coverage внутри окна;
- поведение при unresolved gap;
- deterministic tie-break.

Предложенный GLM диапазон `[windowStart - 1h, windowStart)` является возможным направлением, но не должен приниматься без определения того, что именно означает `windowStart` в detector schedule.

### IMP-4. 90-day backfill конфликтует с 90-day wallet lookback

**Вердикт: согласен; это важная новая находка.**

Roadmap одновременно задаёт 90 дней backfill, 90-дневные wallet metrics и 90-дневное evaluation window. Для первого сигнала с полноценным 90-дневным point-in-time wallet history требуется warm-up до начала evaluation period. При одном 90-дневном dataset полноценное 90-дневное evaluation window действительно исчезает.

Возможные решения:

- примерно 180 дней данных: 90 дней warm-up + 90 дней evaluation;
- более короткий заранее утверждённый wallet lookback;
- более короткое evaluation window после warm-up;
- отдельные training/warm-up/evaluation ranges большей общей длины.

Выбор зависит от provider retention/cost и должен войти в матрицу Stage 3.1 и research protocol. Нельзя вычислять wallet quality с использованием будущих сделок того же evaluation period.

### IMP-5. Не определён token universe

**Вердикт: согласен.**

Это подтверждает G5 первого аудита. Universe должен быть point-in-time dataset, а не список победителей, полученный текущим provider search. Нужно фиксировать discovery source, eligibility rule, inclusion time, exclusion reason, venue scope и изменения состава.

Фильтр вроде текущей liquidity, market cap или survival status может заранее удалить умершие и плохие токены и создать survivorship/selection bias. Universe contract нужен до backfill.

### IMP-6. Candidate identity и rolling detection могут создавать повторные кандидаты

**Вердикт: согласен частично.**

Риск верен: `windowStart` и `decisionCutoff` входят в identity, поэтому вызов на каждом тике создаст разные candidates для одного длительного экономического события.

Однако текущий first slice выполняет один явный вызов, а Stage 5.1 уже планирует family-specific deduplication. Это не дефект завершённого кода и не обязательно зависимость ingestion Stage 3.2: ingestion может оставаться независимым от signal schedule.

До production detection нужно совместно определить:

- trigger source: event, fixed schedule или hybrid;
- evaluation cadence;
- family-specific bucket;
- cooldown/re-arm semantics;
- material-change threshold;
- stable candidate identity;
- одинаковое поведение replay и forward mode.

### IMP-7. Wash trading и качество цены не учтены

**Вердикт: согласен; это важная новая находка.**

Текущий first slice использует одиночные swap observations. Для real research этого недостаточно: манипулируемая сделка, dust trade, self-trade, outlier route или кратковременная фальшивая ликвидность может одновременно создать сигнал и выгодную outcome price.

До Stage 5 нужны versioned price/liquidity quality facts, например:

- venue/program allowlist и parser quality;
- minimum trade notional;
- pool depth/liquidity at observation time;
- robust aggregation или outlier policy;
- suspicious wallet/self-trade/wash indicators, где доказуемо;
- cross-source or cross-venue consistency;
- executable-side semantics;
- confidence degradation вместо fabricated fallback.

Точный wash-trading classifier не обязателен для первого шага. Обязательны прозрачная quality policy и sensitivity analysis, чтобы одиночный аномальный swap не считался безусловной рыночной ценой.

### IMP-8. Friction занижена для микрокапов

**Вердикт: согласен.**

Это подтверждает G6 первого аудита. Flat 3/4/5 percent является first-slice arithmetic fixture, а не реалистичной execution model. До решения Stage 6 нужны как минимум notional/depth sensitivity, entry/exit liquidity, venue fees, base/priority fees и conservative slippage scenarios.

GLM верно отмечает, что sensitivity analysis дешевле полной execution simulation и должен появиться раньше. Полную модель можно развивать позже, но Evidence Report не должен опираться только на один оптимистичный haircut.

### IMP-9. Не определён владелец append-only risk history

**Вердикт: не согласен с finding в заявленном виде.**

Текущие источники дают последовательную картину:

- first-slice module doc явно называет durable risk history non-goal;
- Delivery Plan 4.2 планирует append-only risk decisions;
- архитектура закрепляет владение risk facts/decisions за модулем `risk`;
- Stage 4 change должен впервые создать `risk` schema и его owned tables;
- текущая V3 сохраняет risk facts/decision и для `REJECTED`, и для `ACCEPTED` candidates в `signal.signal_candidates`; accepted snapshot дополнительно копирует decision-time evidence.

Поэтому владельцем будущей общей истории является `risk`, а `signal` владеет неизменяемой копией фактов, использованных конкретным кандидатом. Это нормальная snapshot boundary, а не конфликт ownership.

Остаётся запланированная работа Stage 4: определить schema, identity, provenance, idempotency и point-in-time query API risk history. Это не незамеченная архитектурная дыра.

## Операционные и security-пункты

### OPS-A. Сетевой bind Actuator/JAR

**Вердикт: согласен частично как с deployment guardrail.**

В конфигурации нет `server.address`, поэтому repository profile сам не ограничивает интерфейс прослушивания. Наружная доступность зависит от места запуска, firewall и deployment parameters. Сейчас VDS используется для PostgreSQL; постоянный application deployment на нём ещё не принят.

До запуска JAR на сервере Operations должен потребовать один из вариантов:

- bind на `127.0.0.1`;
- явный firewall allowlist;
- authenticated reverse proxy/private network в отдельном deployment design.

Утверждение о раскрытии деталей БД преувеличено: Operations уже запрещает production health component details. Но даже общий readiness status не следует публиковать в интернет без решения.

### OPS-B. Plaintext secret file

**Вердикт: согласен с аудитором — это осознанно принятое ограничение.**

Файл ignored, не попадает в JAR и тесты, rotation описана. Для текущего single-owner workstation profile это принято. Перед unattended deployment можно перейти на service manager credentials/secret store отдельным change.

### OPS-C. Доверие provider timestamps

**Вердикт: согласен; это важная новая находка.**

Provider-supplied event time не должен автоматически становиться system `observedAt`. Контракт adapter должен определить:

- `sourceEventTime` — время/оценка из chain/provider payload;
- `receivedAt`/`observedAt` — локальный UTC `Clock` на trusted adapter boundary;
- `ingestedAt` — время durable write;
- provider timestamp/raw field как lineage;
- допустимый skew и поведение при его превышении;
- reconstructed availability для backfill как отдельную versioned model, а не подмена фактического live timestamp.

Это необходимо включить в Stage 3 data contract вместе с finality и gap recovery.

## Score и доказанная доходность

**Вердикт GLM подтверждён.**

На уровне модели разделение сделано правильно: Roadmap прямо запрещает считать score прогнозом прибыли, а main spec требует отличать score от measured return. Entry и avoidance metrics также разделены.

На уровне пользовательского продукта разделение пока не материализовано. У живого сигнала нет представления, где рядом показываются:

- score и его факторы;
- data confidence;
- risk decision;
- статус доказанности family/configuration;
- measured out-of-sample expectancy и uncertainty;
- применённые latency/friction assumptions;
- advisory action и expiry.

Именно это должен закрыть будущий decision-support слой. До появления статистического evidence поле expectancy должно честно показывать `UNVALIDATED`, а не ноль или предполагаемую доходность.

## Необязательные улучшения и документационные замечания

### 1. Project Summary и горизонты

**Вердикт: скорее не дефект.**

Строка про `1h/4h/24h` описывает целевой Outcome Tracker, а следующий пункт явно сообщает, что реализован только `1h`. Формулировку можно сделать ещё яснее при общей синхронизации docs, но прямого ложного заявления о текущей реализации нет.

### 2. Glossary

**Вердикт: частично согласен.**

Подтверждённые дефекты:

- `ChainId` ошибочно назван lowercase, хотя current contract сохраняет точный case-sensitive CAIP-2;
- ссылка на удалённый `LEGACY_V5_ARCHITECTURE_MAPPING.md` стала битой.

Упоминания BRIN и ArchUnit сами по себе допустимы в глоссарии исторических/будущих терминов, но поле `Где используется` нужно маркировать как Target/Historical, чтобы не создавать впечатление текущей реализации.

### 3. Operations и backup

**Вердикт: согласен с наличием неоднозначности.**

Строка 13 говорит, что backup/restore procedures deferred, а ниже зафиксирован реально действующий operator-managed backup/restore. Вероятно, первая строка имела в виду repository-owned production runbook, но написана шире. Это нужно уточнить при синхронизации документации.

### 4. Миграция для 4h/24h

**Вердикт: согласен.**

V4 намеренно ограничивает horizon значением `1h`. Stage 5.6 обязан явно включать forward-only migration и обновление contracts/tests для дополнительных horizons. Нельзя просто изменить Java-код.

### 5. Незакоммиченное удаление archive-файла

**Вердикт: не подтверждено.**

HEAD `5922805` уже является commit удаления. Текущий Git status не содержит это удаление. Реальный дефект — оставшаяся битая ссылка в Glossary.

### 6. `strategy_experiment_id` против version/fingerprint

**Вердикт: согласен частично.**

V3 реализует узкий first slice через detector/scorer versions, configuration fingerprint, dataset fingerprint и cutoffs. Это достаточно для его принятого reproducibility contract. Roadmap описывает более широкую будущую сущность `strategy_experiment_id`.

До Stage 5.1 нужно решить, вводится ли отдельная experiment entity/identity или run/config provenance уже покрывает потребность. Roadmap не должен обещать конкретный столбец до этого решения.

## Оценка рекомендованной последовательности GLM

1. **Provider requirement matrix до 3.1 — согласен.** Она должна включить также finality, raw locator/granularity, availability time, universe, volume envelope и quality evidence из первого аудита.
2. **Минимальный observability contract — согласен по результату.** Его не обязательно реализовывать параллельно с выбором provider, но он обязателен в дизайне ingestion и до Stage 3 exit. Grafana не требуется.
3. **Stage 3 ingestion — согласен с расширением.** До первой массовой записи нужны data/schema contracts; backfill range выбирается с учётом warm-up; provider time не становится system observation time.
4. **Stage 4 risk/wallet — согласен по работе, но ownership risk history уже определён.** Stage 4 должен реализовать, а не заново выбирать владельца.
5. **Stage 5 signal/evaluation contracts — согласен.** Trigger/dedup, latency, horizon tolerance, baseline freshness, price quality и horizon migrations обязательны. Часть price/liquidity quality должна проектироваться раньше в Stage 3 marketdata contract.
6. **Statistical preregistration — согласен, но перенести раньше.** Протокол фиксируется до performance-driven настройки, а не только непосредственно перед Stage 6 backtest.
7. **Decision support между 6 и 7 — согласен.** Digest/read-only API/notification и forward shadow не являются execution.
8. **Stage 7 оставить deferred — согласен.**

## Ответ аудитору GLM 5.3 MAX

Спасибо, аудит принят как сильное дополнение к первой независимой проверке. Особенно полезны provider-consumer matrix, конфликт 90-day wallet lookback с 90-day evaluation dataset, provider timestamp trust boundary и необходимость учитывать wash trading/price quality.

Мы полностью принимаем отсутствие decision-support этапа, неквантифицированный statistical protocol, latency/horizon/baseline смещения, отсутствие point-in-time universe и упрощённую friction model. Эти пункты войдут в общий план до того, как система начнёт делать выводы о real edge.

BL-2 принимается по сути: provider выбирается только после матрицы downstream requirements. При этом актуальные официальные документы показывают, что Helius и Bitquery имеют holder-oriented возможности, а GoPlus имеет Solana Token Security API. Поэтому coverage нельзя считать ни доказанным, ни отсутствующим без targeted spike по исторической глубине, полям, лимитам, terms и provenance.

BL-3 принимается как требование минимальной operational visibility, но не как требование Grafana. Stage 3 можно честно закрыть structured operational state, deterministic reports и actionable notification, если их полнота доказана длительным прогоном.

IMP-9 не подтверждается: future durable risk history принадлежит `risk`, а first-slice V3 уже сохраняет immutable risk decision evidence в каждом завершённом candidate, включая rejected. Stage 4 должен реализовать общую append-only risk history, но ownership conflict отсутствует.

OPS-A будет учтён как deployment guardrail перед постоянным запуском JAR на VPS. OPS-C принимается полностью: provider event time и trusted system observation time должны быть разными полями с явной skew/backfill policy.

Documentation findings принимаются выборочно: lowercase `ChainId`, битая archive-ссылка и неоднозначность Operations являются дефектами; uncommitted deletion не существует в текущем Git state; отдельный `strategy_experiment_id` остаётся будущим design decision, а не дефектом первого среза.

Общий вывод совпадает: архитектуру переписывать не требуется. Необходимо дополнить последовательность работ и контракты данных, исследований, operational monitoring и decision support.

## Новые обязательные входы в общий план

После объединения двух аудитов общий план должен дополнительно включить:

1. provider-consumer capability matrix с бесплатными/платными лимитами и проверяемыми samples;
2. явные warm-up, training, evaluation и holdout ranges вместо одного неоднозначного `90d`;
3. trusted timestamp model для live и versioned availability model для backfill;
4. price/liquidity quality policy с защитой от одиночных аномальных и потенциально wash-traded observations;
5. family trigger, cadence, dedup bucket, cooldown и re-arm semantics;
6. deployment bind/firewall guardrail до запуска application JAR на сервере;
7. документационную очистку до следующего implementation change.

Эти пункты дополняют, а не заменяют пять пакетов, выделенных в оценке первого аудита.
