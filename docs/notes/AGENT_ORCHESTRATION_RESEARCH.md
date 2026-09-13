# Исследование агентного конвейера

## Статус

Дата фиксации: 2026-09-13.

Это отдельный backlog идей из гайдов и пользовательского опыта. Он не является действующей архитектурой, OpenSpec change, обязательным процессом или подтверждением поддержки конкретной настройки текущей версией Codex.

Ни одно предложение из этого документа пока не внедрено. Текущий процесс остаётся в docs/AGENT_WORKFLOW.md. Позже каждый пункт следует отдельно принять, отклонить, отложить или проверить экспериментом.

## Цель исследования

Рассмотреть агентный процесс, который:

- работает от specification;
- экономит контекст и токены;
- не допускает бесконечных споров и рекурсивного fan-out;
- сохраняет независимость code, tests и review;
- уважает границы Spring Modulith;
- поддерживает worktree isolation;
- допускает постепенную автоматизацию без преждевременной сложности.

## 1. Оркестрация

### 1.1. Обсуждение плана несколькими агентами

Кандидатный процесс:

1. Несколько агентов независимо предлагают или критикуют план.
2. Они проходят ограниченный итеративный цикл и пытаются прийти к консенсусу.
3. Manager читает их переписку и выбирает итоговый план.
4. Написание кода начинается только по выбранному плану.

Риск: без ограничений агенты способны спорить часами, расходуя токены и вычисления без достаточного прироста качества.

Кандидатное ограничение:

- ввести max_round;
- нормальный предел — две или три итерации;
- после превышения лимита остановить цикл и эскалировать пользователю;
- не повторять обсуждение бесконечно при отсутствии сходимости.

Точный механизм max_round ещё не выбран. Это может быть правило Orchestrator, состояние workflow или внешний hook. Не предполагается, что max_round является готовым ключом Codex config.

### 1.2. Ограничение глубины подагентов

Сохранённый кандидат:

~~~toml
[agents]
max_threads = 6
max_depth = 1
~~~

Предлагаемый смысл max_depth = 1: главный агент может создавать помощников, но помощники не создают собственных помощников.

Ожидаемый эффект:

- нет рекурсивного fan-out;
- меньше потери контекста;
- предсказуемее расход токенов;
- проще понять ownership задачи.

Перед применением нужно проверить актуальные имена, поддержку и семантику параметров в установленной версии Codex. Текущий проект сохраняет лимит четырёх подагентов.

### 1.3. Единая точка общения с пользователем

Кандидатный инвариант:

> Orchestrator — единственная роль, которая общается с пользователем напрямую.

Остальные агенты возвращают структурированные результаты Orchestrator. Он отвечает за маршрутизацию, сводку и эскалацию.

Более строгий вариант:

> Orchestrator не судит реализацию и не читает файлы. Он маршрутизирует работу по вердиктам агентов и кодам возврата gates.

Этот вариант конфликтует с текущей ролью Architect, который самостоятельно проверяет контекст и принимает инженерные решения. Его следует рассматривать как альтернативную модель, а не принятое уточнение.

### 1.4. Spec-driven decomposition и worktree isolation

Предлагаемая основа:

- единица работы формулируется через specification;
- задача декомпозируется на независимые части;
- параллельные пишущие агенты работают в отдельных Git worktrees;
- в одном worktree остаётся один основной writer;
- integration выполняется после стабильных результатов отдельных веток.

## 2. Context-Bounded Agents

### 2.1. Ограничение по модулю

Для крупного Spring Modulith modular monolith рассматривается привязка Worker к конкретному модулю или пакету.

Пример:

- Worker читает и меняет только принадлежащий ему модуль;
- межмодульный доступ разрешён только через module::api;
- если нужен новый API или DTO другого модуля, Worker формулирует задачу владельцу того модуля;
- Worker не создаёт прямую зависимость от implementation package соседнего модуля.

Ожидаемый эффект:

- меньше нарушений инкапсуляции;
- меньше циклических зависимостей;
- яснее ownership;
- меньше нерелевантного контекста.

Это кандидатная стратегия. Она отличается от текущего решения не ограничивать функциональность ролей через MCP, skills и permissions.

### 2.2. Ограниченный контекст Tester

Строгий blind-testing вариант:

- Tester не читает src/main;
- тесты выводятся из openspec/specs, публичного API и observable behavior;
- Tester не изменяет существующие тесты и fixtures.

Практичный начальный вариант:

~~~text
Read allowed:
  src/main/java/**/api/**
  src/main/java/**/kernel/**

Read forbidden:
  src/main/java/**/internal/**
~~~

Аргумент: module API уже содержит почти чистый публичный контракт. Tester может компилировать тесты против API, не копируя внутреннюю реализацию.

Перед внедрением нужно проверить реальные package paths и решить, нужен ли физический sandbox, отдельный worktree, snapshot публичного API или только ролевая инструкция.

Исходная рекомендация — начать именно с package-limited варианта как с минимальной правки ролевой конфигурации, получить большую часть ожидаемого эффекта сразу, а API snapshot добавлять только после появления конкретной нехватки информации у Tester.

### 2.3. Явная строка «никогда не делай X»

Предлагается дать каждой роли один явный отрицательный boundary.

| Роль | Кандидатный запрет |
|---|---|
| Orchestrator | Никогда не читай src; маршрутизируй по вердиктам и кодам возврата |
| Researcher | Никогда не пиши код и не принимай итоговое решение; выход — proposal и evidence |
| Developer | Никогда не открывай и не изменяй src/test; не запускай полный Maven verify |
| Reviewer | Никогда не изменяй файлы и не предлагай готовый patch |
| Tester | Никогда не читай внутреннюю реализацию, не изменяй существующие тесты и fixtures |

Аргумент против готового patch от Reviewer: Developer может вставить предложенное исправление без независимого рассуждения, после чего один и тот же reasoning фактически пишет и одобряет код.

Эти запреты пока не приняты. Некоторые конфликтуют с текущей схемой, где Developer пишет тесты, Tester может получить test-authoring задачу, а Architect читает код.

### 2.4. Четыре приоритета как tie-breaker

Рассматривается короткий корневой блок приоритетов для разрешения конфликтов между файлами и ролевыми инструкциями.

Предварительный вариант:

1. Correctness и evidence integrity.
2. Accepted specification.
3. Architecture и ownership.
4. Минимальный согласованный scope.

Точный текст ещё не принят.

## 3. Persistent memory Reviewer

Кандидат: project-scoped persistent memory для Reviewer.

Предлагаемое поведение:

1. Перед review читать память о повторяющихся дефектах и архитектурных нарушениях.
2. После review записывать только новые повторяющиеся паттерны.
3. При командном использовании версионировать project memory в Git.
4. Не сохранять одноразовые findings, сырые логи и недоказанные предположения.

В материалах упоминались scopes:

- user;
- project;
- local.

Перед внедрением нужно подтвердить поддержку и формат memory scopes в текущем Codex, а также проверить, не дублируют ли они ADR, OpenSpec, AGENTS.md и историю review.

## 4. Spec-first testing

### 4.1. Основной тезис

> Тест, написанный от specification, ловит расхождение specification и кода. Тест, написанный от кода, рискует стать перефразированной реализацией.

Отсюда кандидатный принцип:

- тесты проектируются по openspec/specs;
- внутренний production code скрыт от автора теста;
- observable behavior и публичный API остаются доступны;
- внутренний refactoring не должен ломать тест без изменения поведения.

Правило зависимости:

> Тесты зависят от кода, production code никогда не зависит от тестов.

Тесты являются наиболее изменяемым слоем. API следует проектировать так, чтобы тест не зависел от внутренних деталей.

### 4.2. Контракт появляется до тестов

Кандидатный порядок:

~~~text
Spec-Author
  -> proposal.md + API signatures without implementation

Developer
  -> interfaces, records and method stubs
  -> UnsupportedOperationException inside

Tester
  -> tests against the public skeleton
  -> all new tests are red for the expected reason

Developer
  -> implementation
  -> tests become green
~~~

Первый предлагаемый шаг внедрения — gate «новый behavior test действительно красный до реализации».

Дальнейший порядок:

1. Трассировка значимого теста на OpenSpec requirement/scenario.
2. Reviewer с классификацией расхождений.
3. Mutation ratchet.
4. Hidden holdout при появлении критичной исследовательской логики.

### 4.3. Классификация расхождения

Узкий вопрос Reviewer:

> Утверждение specification X: что ему противоречит — тест или реализация?

Допустимые ответы:

- TEST_WRONG — тест противоречит specification;
- CODE_WRONG — реализация противоречит specification;
- SPEC_AMBIGUOUS — specification допускает оба прочтения.

SPEC_AMBIGUOUS считается ценным результатом. Исправляется specification через OpenSpec change, а не угадывается правильная реализация.

### 4.4. Агрессивный критик

Кандидатная инструкция Tester:

> Твоя цель — не подтвердить, что код работает, а доказать, что он ломается на крайних случаях.

Направления проверки:

- null и отсутствующие значения;
- пустые коллекции;
- дубликаты транзакций;
- обрывы сети;
- повторные доставки;
- неправильный порядок;
- недоступная цена или ликвидность;
- частичный provider response.

Конкретный случай применяется только когда разрешён specification и входным контрактом. Нельзя механически добавлять null туда, где типовая система его запрещает.

## 5. Reviewer: invariant audit и logical review

### 5.1. Фаза 1 — blocking invariant audit

Reviewer сначала сверяет diff с отдельным версионируемым checklist инвариантов Crypto Research Core.

Исходный эскиз роли из заметок сохранён ниже как материал для будущей адаптации. Это не готовый Codex agent file: frontmatter, список tools, model и memory требуют отдельной проверки и перевода в актуальный формат.

~~~yaml
---
name: invariant-auditor
description: MUST BE USED after any change touching module boundaries,
  persistence, evidence paths, or numeric calculations. Audits the diff
  against Crypto Research Core invariants. Read-only.
tools: Read, Grep, Glob, Bash
model: opus
memory: project
---

Ты проверяешь diff на соответствие инвариантам Crypto Research Core.
Ты никогда не изменяешь файлы.

Сначала выполни git diff. Проверяй только заданный checklist и в заданном
порядке. По каждой находке верни severity, file:line, номер инварианта и
объяснение. Заверши ровно одним verdict:

AUDIT_PASSED
AUDIT_FAILED

Не комментируй стиль, naming и всё, что уже проверяет ArchUnit.
Перед audit прочитай память о повторяющихся нарушениях. После audit запиши
только новые повторяющиеся patterns.
~~~

#### Evidence integrity

1. Look-ahead: point-in-time query читает состояние позже cutoff.
2. Выдуманные данные: fixture rows, provider fallback или default вместо отсутствующей цены.
3. Тихая потеря outcome: строки отброшены, dead или no-liquidity tokens отфильтрованы.
4. Ослабленные проверки: expectation существующего теста удалён или сужен.

#### Architecture

5. Cross-module SQL или чтение таблицы не владеющим ей модулем.
6. Cross-module dependency в обход module::api.
7. Persistence, provider, reactive или preview-JDK types в public module API.
8. Provider I/O внутри database transaction.

#### Correctness

9. float или double для authoritative financial value.
10. Недетерминированный порядок там, где требуется total order.
11. Нет provenance воспроизводимого прогона: dataset fingerprint, cutoff, algorithm version, configuration fingerprint или seed.

Формат находки:

~~~text
severity
file:line
invariant number
explanation
AUDIT_FAILED
~~~

Если нарушений нет:

~~~text
AUDIT_PASSED
~~~

Исходная идея допускает немедленный возврат при первой blocking-находке. Нумерацию нужно согласовать с будущим каноническим checklist.

Reviewer не комментирует стиль, naming и то, что уже надёжно проверяет ArchUnit.

### 5.2. Фаза 2 — logical review

После успешного audit Reviewer сверяет:

- OpenSpec requirement;
- тест, созданный по requirement;
- реализацию;
- observable behavior.

Вердикты:

- CODE_WRONG;
- TEST_WRONG;
- SPEC_AMBIGUOUS;
- AUDIT_PASSED, если обе фазы успешны.

### 5.3. Дополнительные функции без расширения пятёрки

- invariant-auditor — второй запуск Reviewer с отдельным checklist;
- migration-reviewer — checklist Reviewer для db/migration;
- mvn-runner — утилита, не постоянная роль; Tester может вызывать отдельный runner, чтобы большие build logs не попадали в его контекст;
- docs-writer — отдельная slash-команда на более дешёвой модели вне pipeline;
- spec-author — ответственность Architect или Researcher либо отдельный временный режим, но не автоматически шестая постоянная роль.

Вариант вложенного mvn-runner конфликтует с кандидатом max_depth = 1. При таком ограничении runner должен быть обычной утилитой либо запускаться главным агентом.

Кандидатные отрицательные boundaries дополнительных режимов:

- invariant-auditor никогда не изменяет файлы и не подменяет build verification;
- mvn-runner никогда не изменяет код и только возвращает структурированный результат команд;
- migration-reviewer остаётся read-only режимом Reviewer.

## 6. Маршрутизация вердиктов

Кандидатная таблица:

| Вердикт | Куда |
|---|---|
| CODE_WRONG | Developer, round + 1 |
| TEST_WRONG | Tester, round + 1 |
| SPEC_AMBIGUOUS | Остановить и эскалировать пользователю |
| AUDIT_FAILED | Developer, round + 1 |
| round > 3 | Остановить и эскалировать пользователю |
| NEEDS_FIXTURE | Остановить и эскалировать пользователю |

Потолок rounds рассматривается как обязательный предохранитель автономного цикла.

Схема предполагает, что Orchestrator доверяет структурированным verdicts и exit codes. Нужно отдельно решить, остаётся ли Architect инженерным судьёй или превращается в чистый router.

## 7. Hooks и наблюдаемость

Основной тезис:

> Внешний hook надёжнее поведенческой инструкции модели, потому что срабатывает как часть harness lifecycle.

Рассматриваемые события:

- SubagentStart;
- SubagentStop;
- завершение основной сессии;
- изменение round;
- возврат verdict;
- начало и завершение verification gate.

Желаемый краткий status view:

- текущая задача;
- активная роль;
- текущий round;
- последний verdict;
- кто и что проверяет;
- причина остановки или эскалации.

Цель — за короткий просмотр понимать состояние pipeline без чтения всех transcripts.

Названия событий и hook schema зависят от harness. Пример Claude Code нельзя автоматически переносить в Codex; перед реализацией нужна проверка актуального Codex contract.

## 8. MCP, Git и Docker

Кандидатная инфраструктура:

- MCP предоставляет внешние инструменты;
- Git фиксирует diff и ownership;
- worktrees разделяют concurrent writers;
- Docker и Testcontainers создают воспроизводимую среду;
- агент запускает контейнеры и проверяет свой результат перед отчётом;
- полный build log не обязан попадать в контекст Architect.

mvn-runner можно сделать helper workflow, возвращающим:

- command;
- exit code;
- число прошедших и упавших тестов;
- пути к reports;
- краткую первопричину failure.

Отдельный агент для этого не нужен, пока объём логов не создаёт измеренную проблему.

## 9. Карта тестовой стратегии

| # | Категория | Инструмент | Что закрывает | Когда вводить |
|---:|---|---|---|---|
| 1 | Структурные | Spring Modulith Test | DAG модулей, циклы | Есть |
| 2 | Структурные | ArchUnit | Cross-module SQL, ownership таблиц, module::api, API types, запрещённые зависимости, float/double | Сейчас |
| 3 | Unit | JUnit 5 + AssertJ | Value types, parsers, normalization, arithmetic | Есть |
| 4 | Property | jqwik | FIFO invariants, exact arithmetic, explicit rounding | С wallet |
| 5 | Metamorphic | JUnit | Look-ahead, missing data, determinism при разном числе workers | Сейчас |
| 6 | Integration | Testcontainers PostgreSQL 18 | Idempotency raw и normalized, provenance, provider I/O вне transaction | Следующий подходящий change |
| 7 | Migration | Testcontainers + Flyway | Schema from scratch против incremental, повторное применение, валидность данных | Следующий подходящий change |
| 8 | Contract | Записанная schema + периодическая проверка | Helius format drift, fixture decay, parser_version | С Helius |
| 9 | Failure injection | Stubs, Toxiproxy или WireMock | Видимость provider failures, отсутствие fabricated fallback | С Helius |
| 10 | Pipeline boundaries | JUnit | Bounded queues, batches, retries, отсутствие unbounded fan-out | С ingestion |
| 11 | Approval | Канонический эталон в Git | Drift Evidence Report | С evaluation |
| 12 | Meta: mutation | PIT точечно | Качество тестов wallet, evaluation и risk | Когда появится логика |
| 13 | Meta: holdout | Отдельный Maven profile | Reward hacking агента, разрыв visible и hidden suite | Вместе с первым подходящим agent workflow |

Историческая заметка: строки 2, 5, 6 и 7 считались нужными для establish-idempotent-marketdata-storage. Это не меняет статус выполненного или активного OpenSpec change; применимость нужно проверить по текущему репозиторию.

Новые библиотеки jqwik, PIT, Toxiproxy, WireMock или Jazzer потребуют отдельного обоснования и согласованного OpenSpec change, если их нет в accepted baseline.

## 10. Усиление тестирования по мере роста риска

### До wallet и evaluation

Допустим более мягкий процесс, пока identity и storage проверяются прямыми integration tests и SQL assertions.

### К wallet и evaluation

Перед FIFO, authoritative arithmetic и outcome calculation рассматривается mutation-testing ratchet:

- начать с критичных pure functions;
- зафиксировать baseline;
- не требовать сразу высокий mutation score для всего проекта;
- не позволять score ухудшаться;
- расширять охват вместе с критичной логикой.

Причина: слабый тест здесь может исказить Evidence Report, а не только пропустить локальный defect.

### Дополнительные техники

- Failure Injection / Chaos Testing: Toxiproxy, WireMock или controlled stubs.
- Metamorphic Testing: invariants при преобразовании входов.
- Fuzz Testing: Jazzer для parsers и boundary code.
- Fitness Functions: исполняемые архитектурные и качественные invariants.
- ArchUnit: постоянная проверка архитектуры, не разовая ревизия.
- Holdout suite: скрытая от Implementer часть проверок против reward hacking.

## 11. Внешние Agentic Development Environments

Сохранённые кандидаты:

- Nimbalyst;
- Agent Orchestrator, AO;
- Orca;
- Air by JetBrains;
- CrewAI Studio.

Предварительная карта:

| Потребность | Кандидат |
|---|---|
| Помощь в управлении работой | Agent Orchestrator |
| Самостоятельное управление большой фермой агентов | Orca |
| Java-oriented ADE | Air by JetBrains |
| Визуальные specifications и документация | Nimbalyst |
| Визуальная сборка multi-agent flows | CrewAI Studio |

Названия, актуальность, лицензии, интеграция с Codex, безопасность и зрелость продуктов пока не проверены. Ни один продукт не выбран.

## 12. Вопросы для последующего решения

1. Нужен ли debate или независимые proposals плюс решение Architect дешевле?
2. Где реализовать max_round?
3. Поддерживает ли текущий Codex нужный max_depth?
4. Должен ли Architect читать diff или только маршрутизировать verdicts?
5. Нужен ли Tester blind access, package-limited access или API snapshot?
6. Кто выполняет Spec-Author?
7. Может ли Tester менять ошибочный существующий тест?
8. Где хранить invariant checklist?
9. Даёт ли project memory пользу сверх versioned documentation?
10. Какие hooks нужны без скрытых блокировок и лишних повторов?
11. Когда сложность оправдает внешний orchestrator или ADE?
12. Какие test dependencies достаточно ценны для accepted baseline?

## 13. Предлагаемый порядок рассмотрения

Это гипотеза, не план внедрения:

1. Gate: новый behavior test красный до реализации и зелёный после.
2. Traceability теста на OpenSpec requirement/scenario.
3. Verdicts CODE_WRONG, TEST_WRONG и SPEC_AMBIGUOUS.
4. Ограничение rounds и явная эскалация.
5. Versioned invariant checklist и отдельный audit pass Reviewer.
6. Worktree isolation для concurrent writers.
7. Hook-based status и измерение расхода.
8. Mutation ratchet перед логикой wallet и evaluation.
9. Holdout, failure injection и fuzzing по измеренному риску.
10. Persistent memory и внешний ADE только после подтверждённой необходимости.

Для каждого пункта будущий разбор заканчивается одним статусом:

- ACCEPT;
- REJECT;
- DEFER;
- EXPERIMENT.
