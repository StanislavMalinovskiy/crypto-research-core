# Работа с Codex-агентами

## Статус и принцип

Architect работает в основной сессии. Он единственный общается с пользователем, выбирает роли, маршрутизирует результаты и отвечает за итог. Подагенты не создаются заранее и не создают собственных подагентов.

Настройки: Architect — `gpt-5.6-sol/high`; Developer и Tester — `gpt-5.6-sol/medium`; Reviewer — `gpt-5.6-sol/high`; Researcher — `gpt-5.6-sol/medium`. Все роли наследуют MCP, skills и разрешения основной сессии. Одновременно разрешены четыре подагента, глубина — один уровень.

## Роли

### Architect

- определяет scope и источник контракта;
- решает, нужны ли Researcher и Tester;
- запускает роли, маршрутизирует вердикты и считает раунды;
- после code gate обновляет обязательные OpenSpec/status/docs до verify и archive;
- принимает итоговое решение и отвечает пользователю.

### Developer

- отдельным вызовом создаёт behavior-free API skeleton до Tester;
- после red gate реализует production-код по активной спеке;
- по умолчанию пишет только `src/main/**`; другие пути Architect явно перечисляет в task capsule;
- читает тесты и запускает только минимальные targeted Surefire/Failsafe checks;
- не запускает полный `mvnw clean verify` и не обновляет документацию;
- возвращает `SKELETON_READY`, `IMPL_DONE`, `TEST_SUSPECT` или `BLOCKED`.

Developer никогда не меняет тесты, fixtures, snapshots, expected results или test configuration; не отключает и не исключает тесты через annotations, assumptions, Maven/CI/profile settings или аналогичный механизм. Production branch, fallback или special case должны следовать из спеки либо реального domain/runtime case, а не из тестового артефакта.

### Tester

- пишет тесты из активного OpenSpec change и публичного контракта;
- до реализации подтверждает ожидаемое падение нового теста;
- после реализации подтверждает green без ослабления assertions и запускает полный Maven quality gate;
- не изменяет production-код.

### Reviewer

- работает только на чтение и не предлагает готовый patch;
- сначала проверяет blocking-инварианты проекта;
- затем сверяет specification, tests, code и documentation;
- возвращает один точный вердикт.

### Researcher

- проверяет внешние изменяемые факты, API и протоколы;
- отделяет подтверждённое от предположений;
- не пишет код или тесты и не принимает финальное решение.

## Когда Tester обязателен

Tester нужен для нового или изменённого observable behavior, bug fix, public API, schema/migration, persistence/idempotency, parser/normalization, финансовой или point-in-time логики и provider contract.

`TEST_NOT_NEEDED` допустим для документации, комментариев, форматирования, механической конфигурации, чистого rename или внутреннего refactoring, уже полностью покрытого неизменными тестами. Причину фиксирует Architect.

## Источник тестового контракта

Для незавершённой задачи Tester читает `proposal.md`, delta specs, `design.md`, `tasks.md` и согласованные public API signatures активного change. `openspec/specs/` описывает только уже принятое поведение и недостаточен для ещё не архивированного change.

До Tester Architect отдельно вызывает Developer для skeleton, если новый публичный контракт нужен для компиляции. Тест создаётся из уже согласованных signatures и до реализации должен упасть по ожидаемой причине. После `TESTS_RED_CONFIRMED` Developer реализует поведение, затем Tester подтверждает `TESTS_GREEN_CONFIRMED` полным Maven gate.

## Рабочий цикл

```text
Architect -> Researcher? -> contract
                            |
                    Developer API skeleton?
                            |
                    Tester -> red gate
                            |
                    Developer -> implementation
                            |
                    Tester -> green gate
                            |
                    Reviewer -> verdict
                            |
                    Architect -> result
```

## Вердикты и маршрутизация

| Вердикт | Действие Architect |
|---|---|
| `TEST_SUSPECT` | передать узкое противоречие Reviewer; раунд пока не считать |
| `CODE_WRONG` | вернуть тому же Developer, раунд +1 |
| `TEST_WRONG` | вернуть тому же Tester, раунд +1 |
| `AUDIT_FAILED` | вернуть тому же Developer, раунд +1 |
| `SPEC_AMBIGUOUS` | остановиться и спросить пользователя |
| `BLOCKED` | остановиться с точной причиной или после третьего repair round |
| `APPROVE` | завершить задачу после обязательных checks |

Счётчик один и принадлежит Architect: максимум три повторных маршрутизации исправления. Developer выполняет один ограниченный pass на вызов и не запускает внутренний цикл «до зелёного». Инфраструктурная диагностика не считается repair round. Reviewer не использует промежуточный вердикт `APPROVE WITH CHANGES`.

## Task capsule

```text
Goal:
Scope:
Phase: skeleton | tests-red | implementation | tests-green | review
Repair round: 0 | 1 | 2 | 3
Active OpenSpec change:
Relevant sources and files:
Writable paths:
Constraints:
Acceptance criteria:
Checks to run:
Expected verdict or response:
```

Обычно один агент владеет одним набором изменяемых файлов. Параллельно выполняются независимые read-heavy задачи; одновременные write-lanes требуют непересекающегося scope или отдельных worktrees.

## Журнал подагентов

Проектные хуки `SubagentStart` и `SubagentStop` добавляют JSONL-записи в `.codex-logs/subagents.jsonl`. Запись содержит время, событие, session/turn/agent identifiers, роль, модель и permission mode. Промпты, ответы и transcript paths не записываются.

Лог локальный и исключён из Git. По `agent_id` видно пару start/stop, по `session_id` и `turn_id` — к какой основной работе относился запуск. `SessionStart` записывает технический маркер в `.codex-logs/hooks.jsonl`: его наличие подтверждает, что конфигурация загружена.

Hooks загружаются при старте сессии и не применяются задним числом. После добавления или изменения hook-файлов нужно открыть новую сессию, проверить точное определение через `/hooks` и доверить его; изменение файла меняет hash и требует повторного доверия.
