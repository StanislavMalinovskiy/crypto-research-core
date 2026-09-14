# Работа с Codex-агентами

## Статус и принцип

Architect работает в основной сессии. Он единственный общается с пользователем, выбирает роли, назначает ровно одну фазу, исполняет механические гейты, маршрутизирует обязательные вердикты и отвечает за итог. Подагенты не создаются заранее и не создают собственных подагентов.

Настройки: Architect — `gpt-5.6-sol/high`; Developer — `gpt-5.6-sol/medium`; Tester, Reviewer и Researcher — `gpt-5.6-sol/high`. `ultra` запрещён для всего supervised workflow, потому что автоматическая делегация конфликтует с явным графом ролей. Одновременно разрешены четыре подагента, `max_depth = 1`.

## Канонический протокол статусов

Каждый ответ роли начинается с одной строки `STATUS: <value>`. Неизвестный, отсутствующий или несовместимый с назначенным режимом статус — protocol error; Architect не выводит предполагаемый вердикт из свободного текста.

| Роль или режим | Разрешённые статусы |
|---|---|
| Developer | `SKELETON_READY`, `IMPL_DONE`, `TEST_SUSPECT`, `BLOCKED` |
| Tester | `RED_CANDIDATE`, `EVIDENCE_CANDIDATE`, `SPEC_INCOMPLETE`, `TEST_SUSPECT`, `BLOCKED` |
| Researcher | `RESEARCH_DONE`, `INCONCLUSIVE`, `BLOCKED` |
| Reviewer `THREAT_CHECK` | `THREAT_CHECK_PASSED`, `THREATS_FOUND` |
| Reviewer `ADJUDICATE` | `CODE_WRONG`, `TEST_WRONG`, `SPEC_AMBIGUOUS` |
| Reviewer `AUDIT` | `AUDIT_FAILED`, `APPROVE` |

## Роли

### Architect

- определяет scope и источник контракта;
- решает, нужны ли Researcher и Tester;
- создаёт каждую новую проектную роль только с `fork_turns: "none"` и изолированным task capsule;
- назначает роли, режим Reviewer, writable-path allowlist и один ограниченный pass;
- для CI, security/integrity и agent-workflow changes проводит `THREAT_CHECK` до test-writing и implementation;
- независимо запускает targeted red, phase-manifest verification, targeted green и полный `mvnw.cmd clean verify`;
- ведёт один общий лимит из трёх repair routings;
- после code gate обновляет обязательные OpenSpec/status/docs до финального `AUDIT`;
- не заменяет валидный Reviewer verdict собственным содержательным вердиктом;
- принимает решение о следующем действии и отвечает пользователю.

### Developer

- отдельным вызовом создаёт behavior-free API skeleton до Tester;
- после принятого red реализует production-код по активной спеке;
- по умолчанию пишет только `src/main/**`; другие пути Architect явно перечисляет в task capsule;
- читает тесты и запускает только минимальные targeted Surefire/Failsafe checks;
- не запускает полный Maven gate и не обновляет документацию;
- никогда не меняет тесты, fixtures, snapshots, expected results, test configuration или discovery;
- не добавляет production behavior, оправданное только тестовым артефактом.

### Tester

- пишет минимальные тесты из активного OpenSpec change и согласованного публичного контракта;
- для каждого нового теста указывает capability, requirement, scenario, `TestClass#method` и ожидаемый failing assertion;
- возвращает `RED_CANDIDATE`, но не подтверждает red самостоятельно;
- только в `tests-evidence`, открытой после `AUDIT_FAILED` о недостающем тесте, возвращает `EVIDENCE_CANDIDATE`, если новый тест уже green на неизменённой реализации; этот статус не заменяет initial `RED_CANDIDATE` или implementation green gate;
- после принятого Architect red не изменяет тесты, fixtures, expectations или test configuration;
- возобновляет запись только когда Architect маршрутизирует Reviewer `TEST_WRONG` в `tests-red` либо `AUDIT_FAILED` о недостающем тесте в отдельную `tests-evidence`;
- не изменяет production-код и не запускает полный Maven gate.

### Reviewer

- получает от Architect ровно один режим: `THREAT_CHECK`, `ADJUDICATE` или `AUDIT`;
- работает с `sandbox_mode = "read-only"` и не предлагает ready-to-apply patch;
- в `THREAT_CHECK` до реализации проверяет только self-bypass guard-а, additions/deletions/path case, CI control flow, quoted/folded config, initially-green tests и конфликты фаз/статусов;
- в `ADJUDICATE` отвечает только на переданное code/test/spec противоречие;
- в `AUDIT` проверяет стабильный diff, инварианты, specification alignment и test adequacy;
- возвращает один статус из набора назначенного режима.

Валидный Reviewer verdict обязателен. Architect маршрутизирует его или эскалирует несогласие пользователю, но не заменяет другим содержательным вердиктом. `APPROVE` необходим, но не отменяет failed mechanical gate или невыполненный Definition of Done.

### Researcher

- проверяет внешние изменяемые факты, API и протоколы;
- отделяет подтверждённое от предположений;
- не пишет код или тесты и не принимает финальное решение.

## Когда Tester обязателен

Tester нужен для нового или изменённого observable behavior, bug fix, public API, schema/migration, persistence/idempotency, parser/normalization, финансовой или point-in-time логики, provider contract и test-execution tooling.

`TEST_NOT_NEEDED` допустим для документации, комментариев, форматирования, механической конфигурации, чистого rename или внутреннего refactoring, уже полностью покрытого неизменными тестами. Причину фиксирует Architect.

## Red gate и трассировка

Для незавершённой задачи Tester читает `proposal.md`, delta specs, `design.md`, `tasks.md` и согласованные public API signatures активного change. Main specs описывают только уже принятое поведение и недостаточны для ещё не архивированного change.

Если новый публичный контракт нужен для компиляции, Architect сначала отдельным вызовом получает от Developer `SKELETON_READY`. Tester затем возвращает `RED_CANDIDATE` с точной командой, requirement/scenario, тестовым методом и ожидаемым assertion. Architect выполняет команду на неизменённом implementation source. Red принимается только когда названный тест запущен и упал по ожидаемому assertion; compilation, discovery, configuration, startup, Docker или другая infrastructure failure red-гейт не подтверждает.

`SPEC_INCOMPLETE` возвращается Architect. Он может привести однозначный ответ только из accepted ADR, main spec или уже согласованного change artifact и обновить контракт, потратив repair round. Если требуется продуктовое решение, результат повторяется или общий лимит исчерпан, Architect останавливается и спрашивает пользователя.

Если `AUDIT_FAILED` требует только недостающий тест, а accepted behavior уже реализован, Architect открывает отдельную фазу `tests-evidence`, не red-фазу. Только в `tests-evidence` Tester возвращает `EVIDENCE_CANDIDATE` с requirement/scenario/test/assertion trace и точной targeted-командой. Architect независимо подтверждает green и через phase manifest проверяет, что implementation paths не менялись. `TEST_WRONG` по-прежнему направляется в `tests-red` для нового `RED_CANDIDATE`; evidence-путь не заменяет первоначальный red или основной implementation green gate.

## Phase manifest

Перед каждой пишущей фазой Architect создаёт snapshot в пути вне репозитория:

```powershell
$phaseManifest = Join-Path ([System.IO.Path]::GetTempPath()) "crypto-research-phase-$PID.json"
pwsh -NoProfile -File .codex/scripts/phase-manifest.ps1 -Command Snapshot -RepositoryRoot . -ManifestPath $phaseManifest
```

После возврата писателя Architect проверяет положительный allowlist из task capsule:

```powershell
pwsh -NoProfile -File .codex/scripts/phase-manifest.ps1 -Command Verify -RepositoryRoot . -ManifestPath $phaseManifest -AllowPath 'src/main/**'
```

Exit `0` означает, что изменены только разрешённые пути; exit `3` печатает запрещённые `ADDED`, `MODIFIED` или `DELETED` paths; exit `2` означает некорректный input или manifest. Snapshot включает имена и SHA-256 содержимого tracked и untracked файлов, поэтому замечает добавления и повторное изменение уже dirty-файла. В текущем одномодульном репозитории исключаются только корневые `.git`, `target` и `.codex-logs`; вложенный каталог с именем `target` остаётся защищённым. Writer-фазы последовательны.

После принятого red Tester-файлы заморожены. `TEST_WRONG` завершает текущую фазу и открывает `tests-red`; `AUDIT_FAILED` о недостающем тесте открывает отдельную `tests-evidence`. В обоих случаях Architect делает новый snapshot и возвращает тест тому же Tester.

## Рабочий цикл

```text
Architect -> Researcher? -> contract
                            |
             high-risk workflow change?
                            |
             Reviewer(THREAT_CHECK) -> plan/tests repair?
                            |
                    Developer -> skeleton?
                            |
                    Tester -> RED_CANDIDATE
                            |
                    Architect -> targeted red + snapshot
                            |
                    Developer -> implementation
                            |
                    Architect -> manifest + targeted green
                            |
              TEST_SUSPECT? -> Reviewer(ADJUDICATE)
                            |
                    Architect -> OpenSpec/docs
                            |
                    Architect -> test-integrity preflight -> clean verify
                            |
                    Architect -> Reviewer(AUDIT)
                            |
                    Architect -> audit-result marker
```

## Маршрутизация и лимит

| Статус | Действие Architect |
|---|---|
| `THREAT_CHECK_PASSED` | продолжить к skeleton/Tester red/implementation согласно test-impact assessment |
| `THREATS_FOUND` | исправить OpenSpec design или planned tests до writer-фазы, раунд +1 |
| `RED_CANDIDATE` | независимо выполнить targeted red; при успехе открыть implementation-фазу |
| `EVIDENCE_CANDIDATE` | только в post-`AUDIT_FAILED` test-evidence repair независимо подтвердить targeted green и неизменность implementation paths |
| `SPEC_INCOMPLETE` | уточнить только из accepted sources, раунд +1, либо сразу спросить пользователя |
| `TEST_SUSPECT` | передать узкое противоречие Reviewer в `ADJUDICATE`; раунд пока не считать |
| `CODE_WRONG` | вернуть тому же Developer, раунд +1 |
| `TEST_WRONG` | открыть `tests-red` для того же Tester, раунд +1 |
| `SPEC_AMBIGUOUS` | остановиться и спросить пользователя |
| `AUDIT_FAILED` | вернуть указанному владельцу дефекта, раунд +1; для недостающего теста открыть `tests-evidence` |
| `BLOCKED` | остановиться с точной причиной |
| `APPROVE` | завершить только после всех независимых checks и Definition of Done |

Счётчик один и принадлежит Architect: максимум три автономных repair routings на задачу. Перед передачей исправления Architect выполняет:

```powershell
pwsh -NoProfile -File .codex/scripts/log-repair-routing.ps1 -Loop AUDIT -SourceStatus AUDIT_FAILED -RepairOwner Tester,Developer,Architect -Round 1
```

Команда добавляет в `.codex-logs/repair-routings.jsonl` одну JSONL-запись с UTC timestamp, `loop`, `source_status`, `repair_owner` и общим `round`. Лог содержит только метаданные маршрутизации, исключён из Git и допускает конкурентные append-вызовы без потери записей. Infrastructure retry и исправление синтаксически некорректного статуса не являются artifact repair, но не могут использоваться для повторного запроса более удобного содержательного вердикта. Повторный protocol error эскалируется.

## Task capsule

Каждый новый проектный агент создаётся с `fork_turns: "none"`. Capsule содержит 200-400 слов, самодостаточен для одного pass и не включает историю пользовательского обсуждения или отчёты других ролей. Для correction используется тот же агент; ему передаётся только новый bounded delta.

```text
Goal:
Phase: research | threat-check | skeleton | tests-red | tests-evidence | implementation | adjudicate | audit
Writable paths:
Frozen paths:
Requirement/scenario IDs:
Files to read:
Acceptance checks:
Expected status:

Optional bounded metadata:
Scope:
Reviewer mode: none | THREAT_CHECK | ADJUDICATE | AUDIT
Repair round: 0 | 1 | 2 | 3
Active OpenSpec change:
Constraints:
Checks to run:
```

Один агент владеет одним набором изменяемых файлов. Параллельно выполняются только независимые read-heavy задачи; writer-фазы идут последовательно, если не используются отдельно утверждённые worktrees.

## Полный verification gate

Test-integrity preflight выполняется вне Maven и раньше него, поэтому проверяемые Maven skip/selection settings не могут отключить сам guard:

```powershell
pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1
mvnw.cmd clean verify
openspec validate --all --strict --no-interactive
openspec doctor
```

На Unix второй вызов эквивалентен `./mvnw clean verify`. Любой ненулевой exit останавливает последовательность; `clean verify` не запускается после failed preflight.

## Журнал подагентов

Проектные хуки `SubagentStart` и `SubagentStop` добавляют низкоуровневые lifecycle-записи в `.codex-logs/subagents.jsonl`. Для каждой логической команды Architect использует отдельный assignment id и фиксирует dispatch до вызова роли:

```powershell
$assignmentId = [guid]::NewGuid().ToString('N')
pwsh -NoProfile -File .codex/scripts/log-agent-activity.ps1 -Action Dispatch -AssignmentId $assignmentId -Role tester -Phase tests-red -Summary 'Write requirement-derived red tests'
```

После ответа роли Architect записывает её канонический статус, короткий результат и фактические token counters, если runtime их предоставил:

```powershell
pwsh -NoProfile -File .codex/scripts/log-agent-activity.ps1 -Action Return -AssignmentId $assignmentId -Status RED_CANDIDATE -Summary 'Named test fails at the expected assertion' -InputTokens 1200 -OutputTokens 240 -TotalTokens 1440
```

Если token counters недоступны, параметры не передаются и логгер пишет `unavailable`, не оценку. JSONL получает `AssignmentDispatch`/`AssignmentReturn` с ролью, фазой, UTC start/end и `duration_ms`. Отдельный `.codex-logs/subagents-readable.log` получает одну завершённую строку вида:

```text
15-09-26 23:23 | Architect -> Tester: Write requirement-derived red tests | Tester -> Architect: STATUS: RED_CANDIDATE; Named test fails at the expected assertion | phase=tests-red | duration=00:08:41 | tokens: input=1200, cached_input=unavailable, output=240, reasoning=unavailable, total=1440
```

Для экспорта уже существующего JSONL используется:

```powershell
pwsh -NoProfile -File .codex/scripts/export-subagent-log.ps1
```

Старые lifecycle-пары получают точную длительность. Старые follow-up stop без записанного dispatch честно получают `duration=unavailable`. Полные prompts, responses и transcript paths не копируются; сохраняются только однострочные summary длиной до 400 символов.

Architect отдельно фиксирует repair routing с полями `loop`, `source_status`, `repair_owner` и `round`. Лог локальный и исключён из Git. Hooks загружаются при старте сессии и не применяются задним числом; после изменения hook-файлов новая сессия проверяет и доверяет точное определение через `/hooks`.
