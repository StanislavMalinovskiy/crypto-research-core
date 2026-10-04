# Technical audit backlog — 2026-10-04

## Назначение и статус

Ненормативная запись объединённого аудита, перепроверки второго архитектора и последующего read-only анализа текущего кода. Владелец разрешил сохранить этот документ и закоммитить его. Это не разрешение реализовать все перечисленные исправления, не новый OpenSpec change и не изменение принятых требований.

Все пункты ниже — открытые замечания или рекомендации, если явно не указано иное. Код, accepted specs, исторические инструменты и evidence при перепроверке не изменялись. Новые тесты, PostgreSQL и полный Maven gate в рамках анализа не запускались. Исторические результаты тестов из исходного аудита не выдаются за новый запуск.

### Выполненные исправления — 2026-10-05

Описание замечаний ниже сохранено как исходный аудит, а не как утверждение о текущем неисправленном коде. По последующему явному решению владельца поток B1–B7 реализован и прошёл применимые review/gates; change [repair-technical-audit-integrity](../../openspec/changes/archive/2026-10-05-repair-technical-audit-integrity/tasks.md) архивирован CLI. [Фактический post-archive receipt](C:/crypto-research-evidence/r1-e2-path-v1-gate-20261003/main-window-b-postarchive-results.json): native exit 0 для strict OpenSpec, doctor, integrity, convention (33), portable Node (389) и diff; ноль failures/errors/skips, allowlist и соответствие семи добавленных требований PASS. Полный предшествующий Java gate: 201 тест, ноль failures/errors/skips. Финальная фиксация архивного статуса остаётся за Main; push не выполнялся.

- B1/B2 — пункты 1/2: компонентные snapshot/disposition keys и microsecond-нормализация новых versioned запросов, с неизменными историческими fingerprints/readback. Коммит `810aa2db058bbce47bfebf8753771177220fad1f` также включает B3.
- B3 — пункт 6 и дополнение B: Node 24, обязательный portable CI-набор и отдельно явные local-evidence проверки с отказом при отсутствии входов. Реальный hosted GitHub run и полный local historical suite не объявляются выполненными.
- B4 — дополнение A: новая versioned price/liquidity revision identity с раздельными компонентами и typed exclusion equality, точные старые ключи читаются. Оригинальная derivation provenance до 128 символов сохранена; v1/v2 распознаются по saved fields; повтор того же legacy v1 факта даёт явный конфликт, не скрытый дубликат. Без DDL и без конкурентного запуска старых writers. Коммит `92d37f177728447e47092a5fc7c02da5d63bce51`.
- B5 — пункты 3–5 и дополнение C: только новый bounded parser v2, signature/index/path consistency, prevalidation, 60 decoded bytes / 82 encoded characters по pinned layout и один hash страницы. Коммит `433a0ca86bde0cf08ff15552aee55b70f5fd57bd`. Read-only audit пяти сохранённых creation-файлов: 189 127 строк, ноль relationship conflicts, исходные bytes/SHA неизменны. Это не глобальная полнота census, raw replay, deployment proof или admission D1. Исторические v1/monthly/pins не изменены и их ограничения не объявляются исправленными задним числом.
- B6/B7 — пункт 7: prospective parsing/canonicalization/file-transport library для новых инструментов (`f98f66ad478bc5cf82351f046014f0f356264e7c`) и bounded semantic-positive/negative convention checks вместо части дословных формулировок (`d34bd4ed45bb0e6b73e45f8f77b8701b4d4d75e2`). Полезные config/link/independent-review guards сохранены. Это не полный доказатель смысла любого текста и не удаление всех старых helper-зависимостей; исторические инструменты сохранены намеренно.

Неблокирующий остаток финального коллегиального review: `legacyExclusionFingerprintKey` намеренно сохраняет separator-кодирование в fingerprint, в том числе новых snapshots, ради требуемой совместимости. Typed comparisons исправляют полноту и retry, но сами по себе не доказывают однозначность старой fingerprint-сериализации для любых допустимых opaque-компонентов. Будущий отдельный шаг — versioned snapshot fingerprint encoding для новых записей с точным чтением старых; в этом окне его не реализуем и сохранённые fingerprints не меняем. Новая практическая потеря данных или контрпример на сохранённых snapshots этим review не установлены.

Сохранённые ограничения: deployment/coverage/candidate/D1 выводы не расширены, unsupported данные не приняты, новые census или сетевые прогоны этим исправлением не запускались. Реальный D1 продолжает отдельный поток A. Статус raw индекса после archive отличается; semantic index равен HEAD, staged/conflict entries не изменены, rollback не требовался. Подробности и честное разделение actual checks от непройденных исторических/hosted проверок — в архивных tasks и receipt.

Оснований переделывать модульный монолит аудит не установил. Направления зависимостей, владение SQL и миграциями, application-транзакции, точная финансовая арифметика и разделение decision/evaluation evidence не дали подтверждённых существенных архитектурных нарушений в проверенном объёме.

## Семь исходных замечаний и итоговая оценка

### 1. Высокий приоритет: коллизии составных snapshot-ключей

**Вердикт: согласен; исправлять первым.**

В [VersionedSnapshotStore.java](../../src/main/java/io/cryptoresearch/marketdata/application/VersionedSnapshotStore.java) метод `Fact.canonicalKey()` склеивает компоненты через `|`. Непрозрачные идентификаторы допускают этот символ.

Разные пары при одинаковых остальных компонентах:

```text
transaction = a|b, locator = c
transaction = a,   locator = b|c
```

дают одинаковый ключ. [VersionedSnapshotFinalizer.java](../../src/main/java/io/cryptoresearch/marketdata/application/VersionedSnapshotFinalizer.java) использует его для группировки видимых фактов, подсчёта покрытия и проверки dispositions. Поэтому включение только одного из двух разных фактов может пройти как полный snapshot; включение обоих может ошибочно отклоняться как несколько revisions одного объекта.

В исходном аудите сообщалось о воспроизведении через настоящий finalizer. Повторная проверка исходников подтверждает этот путь. Потеря уже сохранённых реальных данных не установлена. Отсутствие разделителя в обычных Solana signatures не отменяет общего принятого контракта идентификаторов.

Рекомендуемое исправление: структурные ключи с отдельными компонентами; проверить canonical, revision-reference и exclusion keys. Регрессии должны доказать отказ при пропуске любого из двух фактов и успешное включение обоих.

### 2. Средний приоритет: неодинаковая нормализация времени

**Вердикт: согласен; это не только ошибочный отказ.**

`VersionedSnapshotFinalizer` усекает historical knowledge cutoff до микросекунд, но `canonicalScope()` сохраняет четыре исходные временные границы без такого усечения. Fingerprint использует их исходный текст.

Следствия:

- корректный после нормализации пограничный scope может отклоняться;
- эквивалентные после нормализации scope могут получать разные fingerprints и manifests;
- [VersionedLiquiditySpikeSignalService.java](../../src/main/java/io/cryptoresearch/signal/application/VersionedLiquiditySpikeSignalService.java) сравнивает ненормализованные decision/risk/window instants с frozen snapshot и может отклонять эквивалентный запрос.

Утечка будущих данных не доказана. Однако нарушается требование [Reproducibility](../REPRODUCIBILITY.md) нормализовать authoritative instants до validation, computation и identity.

Рекомендуемое исправление: нормализация на входе versioned use cases до сравнений, запросов и вычисления fingerprints; регрессии на sub-microsecond значения и эквивалентные запросы.

### 3. Средний приоритет: частичное изменение census при отказе

**Вердикт: дефект v1 подтверждён; конкретный путь исправлен в monthly.**

В [e2-census.cjs](../../tools/research/r1/e2-census.cjs) `applyAdmitted()` изменяет часть состояния до проверки общего лимита diagnostic keys. Raw к этому моменту уже опубликован. Исходный synthetic-контрпример сообщал `INSTRUCTION_LIMIT`, `decodedInstructions=501`, stream instructions `1002` и последующий `INTEGRITY_ERROR` при replay.

В [e2-monthly-census.cjs](../../tools/research/r1/e2-monthly-census.cjs) `prepare()` рассчитывает объединённые diagnostics и проверяет лимиты до публикации raw и `apply()`. Это устраняет именно этот поздний отказ. Не следует расширять вывод до утверждения о полной файловой транзакционности всех публикаций.

Рекомендация: старую v1 не переписывать; сохранить известное ограничение. Для будущих версий тестировать не только отказ, но и отсутствие частичного изменения состояния и публикации страницы при отказе prevalidation.

### 4. Средний приоритет: противоречивое соответствие signature и индекса

**Вердикт: реальный пробел проверки источника; простой поиск повторов signature недостаточен.**

`e2-census.cjs` проверяет уникальность transaction index, но не обратное соответствие signature транзакции. Monthly использует тот же pinned admission parser. Противоречивый ответ может представить одну signature под разными индексами и породить разные creation identities.

Повтор signature в `creations.jsonl` сам по себе допустим: одна транзакция может содержать несколько creation instructions по разным paths. Проверять нужно:

- `signature → (slot, transactionIndex)`;
- согласованность immutable фактов для `signature + numeric instruction path`;
- согласованность представленных transaction contents.

Существующий offline mint reducer проверяет конфликт immutable фактов для signature+path, но это не доказывает согласованность между разными paths.

Рекомендация: отдельная read-only проверка исторических данных по указанным отношениям и соответствующая защита в новой версии parser. Не объявлять такую историческую проверку уже выполненной.

### 5. Средний приоритет: чрезмерное синхронное base58-декодирование

**Вердикт: реальный дефект вычислительного ограничения.**

`e2-census.cjs` допускает для instruction data до 64 000 000 base58-символов и синхронно декодирует их через растущий `BigInt`. HTTP timeout не прерывает такой CPU-bound цикл. Monthly наследует этот parser.

Аргумент «обычная транзакция маленькая» не защищает от повреждённого или противоречивого ответа источника. Максимальная нагрузка в перепроверке не запускалась; повреждение сохранённых данных этим замечанием не установлено.

Рекомендация: отдельный небольшой предел до декодирования, выведенный из поддерживаемых layouts и кодирования, с regression на oversized input. Не принимать произвольные «2 КБ» без проверки соответствия контракту. Исправлять в новой версии инструмента.

### 6. Практически высокий приоритет: Node-тестов нет в CI

**Вердикт: согласен; повышение Node и одна команда недостаточны без переносимого набора.**

[quality-gate.yml](../../.github/workflows/quality-gate.yml) устанавливает Node 22, но запускает integrity preflight, Maven и OpenSpec без research Node tests. Maven также не запускает их. В исходном аудите сообщалось о локальных 157 прошедших тестах; это исторический результат, не результат текущей перепроверки.

Несколько инструментов требуют Node 24 либо 24/25. Кроме того, часть тестов читает локальную evidence вне Git; они не могут автоматически работать на чистом GitHub runner.

Рекомендация: выбрать поддерживаемую Node baseline, включить переносимые offline/synthetic tests в обязательный CI и сохранять результаты. Отделить их от явно локальных проверок исторической evidence; отсутствие обязательного входа нельзя скрывать молчаливым skip.

### 7. Долг сопровождения: helpers и текстовые convention-проверки

**Вердикт: согласен; плановый рефакторинг, не причина менять архитектуру.**

[e2-path-probe.cjs](../../tools/research/r1/e2-path-probe.cjs) и `e2-census.cjs` импортируют helpers из исторических probes и закрепляют SHA целых файлов. Изменение несвязанного поведения collector меняет зависимость и может блокировать инструмент по integrity.

[RepositoryConventionsTest.java](../../src/test/java/io/cryptoresearch/RepositoryConventionsTest.java) при проверке содержит 1468 строк и сохраняет часть дословных проверок инструкций. Некоторые проверки уже допускают перефразирование; нельзя утверждать, что весь класс проверяет только точный текст. Простое разбиение класса не устранит смысловую связанность.

Рекомендация: в следующих версиях выделять parsing/canonicalization/transport helpers с явным контрактом. Сохранять исторические файлы, fingerprints и replay. Convention checks уменьшать по отдельному scope, сохраняя полезные проверки конфигурации, ссылок и независимости review.

## Три дополнительных уточнения текущей перепроверки

Эти пункты записаны заново по текущему коду. Они не выдаются за восстановленные три дополнения из прежнего разговора.

### A. Область неоднозначного кодирования шире snapshot finalizer

[RecordMarketFactUseCase.java](../../src/main/java/io/cryptoresearch/marketdata/application/RecordMarketFactUseCase.java) передаёт склеенные `asset|venue` и `asset|pool` в вычисление revision key. Length-prefix одной уже склеенной строки не восстанавливает границы исходных компонентов. Отдельный content digest обычно превращает такую коллизию в immutable conflict, но корректные разные факты не могут сосуществовать под ошибочно общей revision identity.

[JdbcVersionedSnapshotStore.java](../../src/main/java/io/cryptoresearch/marketdata/infrastructure/persistence/JdbcVersionedSnapshotStore.java) сравнивает saved и expected exclusions через склеенные строки. Нужно проверить весь путь. Не каждая склейка одинаково опасна: пара строго валидированных SHA-256 значений отличается от набора opaque полей.

### B. CI должен обходиться без личной evidence-папки

[e2-monthly-wait-guard-v2.test.cjs](../../tools/research/r1/test/e2-monthly-wait-guard-v2.test.cjs) читает April manifest из `C:\crypto-research-evidence` уже при загрузке модуля теста. [e2-offline-mint-count.test.cjs](../../tools/research/r1/test/e2-offline-mint-count.test.cjs) содержит проверку pinned metadata и строк из той же внешней папки.

Поэтому нельзя просто добавить общий wildcard всех tests в Linux CI и объявить задачу решённой. Нужны явное разделение portable/local checks и синтетические repo-owned входы для переносимых проверок, без подмены исторической evidence.

### C. Хеш всей страницы повторяется для каждого creation

`e2-census.cjs` вызывает `digest(bytes)` для каждого успешного creation. Стоимость растёт как объём страницы, умноженный на число creation records. Monthly наследует этот код.

Рекомендация для новой версии: вычислять hash страницы один раз и передавать его всем records. Реальное замедление на сохранённых данных в текущей перепроверке не измерялось.

## Совместимость и границы исправлений

- Census и tails к моменту этой записи завершены; историческое утверждение «апрельский запуск идёт» больше не описывает текущий статус.
- Не менять pinned v1/helpers, manifests, receipts или старые STOP/results ради исправления будущего поведения.
- Типизированные ключи могут исправить in-memory grouping без изменения исторических digest rules.
- Замена member/exclusion encoding или revision dimension encoding меняет persisted fingerprints/revision keys. Нужны явная версия и сохранение readback старых идентичностей; нельзя молча пересчитать старые evidence и ссылки.
- Миграция БД не обязательна автоматически: её необходимость определяет конкретный совместимый дизайн, а не сам факт исправления Java-ключа.
- После нормализации scope старые manifests с наносекундами должны оставаться читаемыми с исходными fingerprints.
- Эти замечания не означают доказанный вред уже собранной реальной evidence и не переводят provisional census в admitted D1/E2 data.

## Рекомендуемый порядок отдельных работ

1. Snapshot structural keys и регрессии полноты, revisions и exclusions; проверить связанные кодирования и совместимость.
2. Authoritative time normalization в versioned use cases и регрессии эквивалентных запросов.
3. Переносимый offline Node test suite в CI с поддерживаемой Node baseline.
4. Новая версия census/parser: signature/index consistency, предел instruction data и один page hash; отдельно разрешённый read-only audit исторических mappings.
5. Helpers и уменьшение дословной связанности convention tests — отдельный плановый рефакторинг.

Следующее разрешённое действие: Main проверяет и фиксирует текущий архивный статус B, затем продолжает уже разрешённый независимый D1/R1-E2 поток A в его лимитах; calibration/P1 не входят в текущее окно. Исторический список рекомендаций выше не разрешает новые прогоны или изменение принятой evidence.
