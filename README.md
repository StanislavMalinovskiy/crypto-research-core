# Crypto Research Core

Стартовый комплект документации и Maven-конфигурации для продолжения разработки в Codex CLI.

## Зафиксированный фундамент

- Java 25 без preview-возможностей.
- Spring Boot 4.1.1.
- Spring Modulith 2.1.1.
- Maven.
- Синхронный Spring MVC.
- Spring Data JDBC и `JdbcClient`; JPA/Hibernate не используются.
- PostgreSQL 18 + Flyway.
- Один Git-репозиторий, один Maven-модуль, один deployable JAR.
- Шесть логических модулей Spring Modulith: `kernel`, `marketdata`, `risk`, `wallet`, `signal`, `evaluation`.

## Документы

| Файл | Назначение |
|---|---|
| [docs/DELIVERY_PLAN.md](docs/DELIVERY_PLAN.md) | Текущий этап, следующий change и укрупнённый путь до Evidence Report |
| [docs/DELIVERY_PLAN_FIXES.md](docs/DELIVERY_PLAN_FIXES.md) | Companion remediation-карта F0–F9 и traceability по итогам внешних аудитов |
| [docs/AGENT_WORKFLOW.md](docs/AGENT_WORKFLOW.md) | Обычный fail-closed DEFAULT workflow: Control + Developer, test-first evidence и полный local gate |
| [docs/AGENT_WORKFLOW_MULTIAGENT.md](docs/AGENT_WORKFLOW_MULTIAGENT.md) | Опциональный supervised MULTIAGENT protocol, загружаемый только после ручного `[agents].enabled = true` |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Продуктовые гипотезы, долгосрочные возможности и приоритеты |
| [docs/PROJECT_SUMMARY.md](docs/PROJECT_SUMMARY.md) | Краткий контекст проекта для новой сессии |
| [docs/TECH_STACK.md](docs/TECH_STACK.md) | Разрешённые технологии и версии |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Текущее сводное состояние архитектуры |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | Runtime, configuration, secrets, health и resource budgets |
| [docs/TESTING.md](docs/TESTING.md) | Уровни тестов и правила выбора test infrastructure |
| [docs/REPRODUCIBILITY.md](docs/REPRODUCIBILITY.md) | UTC, точная арифметика, provenance и deterministic research contract |
| [docs/GLOSSARY.md](docs/GLOSSARY.md) | Развёрнутый глоссарий проекта |
| [docs/modules/README.md](docs/modules/README.md) | Карта документации логических модулей |
| [docs/adr/README.md](docs/adr/README.md) | Журнал архитектурных решений |
| [docs/notes/AGENT_ORCHESTRATION_RESEARCH.md](docs/notes/AGENT_ORCHESTRATION_RESEARCH.md) | Непринятые идеи и вопросы по развитию агентного конвейера |
| [docs/archive/API_SERVICES_ARCHIVE.md](docs/archive/API_SERVICES_ARCHIVE.md) | Архив обзора API и сервисов; не источник истины |
| [docs/archive/PAID_API_ARCHIVE.md](docs/archive/PAID_API_ARCHIVE.md) | Архив платных API; не источник истины |
| [docs/notes/THINK.md](docs/notes/THINK.md) | Черновые идеи и вопросы |
| [docs/notes/EXTERNAL_AUDIT_REVIEW_2026-09-20.md](docs/notes/EXTERNAL_AUDIT_REVIEW_2026-09-20.md) | Оценка Control первого внешнего аудита; non-normative |
| [docs/notes/GLM_5_3_MAX_EXTERNAL_AUDIT_REVIEW_2026-09-20.md](docs/notes/GLM_5_3_MAX_EXTERNAL_AUDIT_REVIEW_2026-09-20.md) | Оценка Control аудита GLM 5.3 MAX; non-normative |

## Как передать контекст Codex CLI

Перед изменением кода Codex следует порядку из [AGENTS.md](AGENTS.md):

1. `AGENTS.md`.
2. `docs/PROJECT_SUMMARY.md`.
3. Relevant OpenSpec change.
4. `docs/modules/<module>.md` for every affected module.
5. Applicable ADRs.
6. Existing code and tests.

Документы имеют разные области ответственности, а не общий линейный приоритет. Правила разрешения конфликтов зафиксированы в `AGENTS.md`. Архивные документы используются только как справочные материалы.

## OpenSpec navigation

- Active changes: see [openspec/changes](openspec/changes/) excluding its `archive/` directory.
- Completed bootstrap: [2026-09-13-bootstrap-modular-foundation](openspec/changes/archive/2026-09-13-bootstrap-modular-foundation/).
- Accepted behavior: see [openspec/specs](openspec/specs/); active change names are intentionally not pinned here because completed changes move to `archive/`.

Текущий storage boundary сохраняет stable-inclusion raw provider evidence с точным CAIP-2 identity, отдельные transaction payloads, normalized swaps, price/liquidity observations, USD-conversion lineage и immutable dataset/universe snapshots. Равный retry не изменяет первую запись, а конфликтующие immutable evidence отклоняются. Сам storage не проверяет finality; F1 provider spikes и selection ещё не завершены, а реальный provider adapter и недостающие live facts принадлежат следующему F3 change. Записанный walking skeleton уже проходит путь raw input → normalized swap → signal snapshot → outcome → reproducible report. PAPER/LIVE execution отсутствует в MVP; любые execution gates потребуют отдельного одобренного change и ADR.

## Java baseline

Production-код, тесты и упакованный JAR используют стабильный Java 25 API без `--enable-preview`:

```bash
java -jar target/crypto-research-core-0.0.1-SNAPSHOT.jar
```

Preview-функция может быть включена только отдельным OpenSpec change и superseding ADR с указанием точного JEP, причины, runtime-флага и JDK upgrade verification.

## Локальная PostgreSQL для разработки

Корневой `compose.yaml` запускает только PostgreSQL 18.6 для локальной разработки. Приложение остаётся host-процессом и использует Flyway при старте; Compose не создаёт application schema и не запускает JAR.

Необязательно создайте собственный игнорируемый `.env` из безопасного примера и измените только локальные значения:

```powershell
Copy-Item .env.example .env
```

Запуск базы и проверка её статуса:

```powershell
docker compose up -d --wait postgres
docker compose ps postgres
```

С настройками по умолчанию приложение подключается без дополнительных переменных:

```powershell
.\mvnw.cmd spring-boot:run
```

Если в `.env` изменены порт, имя базы или credentials, перед стартом приложения задайте соответствующие host-side настройки. Например:

```powershell
$env:CRYPTO_RESEARCH_DB_URL = "jdbc:postgresql://localhost:55432/crypto_research"
$env:CRYPTO_RESEARCH_DB_USERNAME = "crypto_research"
$env:CRYPTO_RESEARCH_DB_PASSWORD = "crypto_research"
.\mvnw.cmd spring-boot:run
```

Значение `POSTGRES_HOST_PORT` должно совпадать с портом в `CRYPTO_RESEARCH_DB_URL`; `POSTGRES_DB`, `POSTGRES_USER` и `POSTGRES_PASSWORD` соответствуют имени базы, `CRYPTO_RESEARCH_DB_USERNAME` и `CRYPTO_RESEARCH_DB_PASSWORD`. Инициализационные переменные образа PostgreSQL применяются только к пустому volume. Изменение credentials для существующего volume требует явного изменения роли/пароля внутри PostgreSQL либо разрушительного локального reset.

Обычная остановка удаляет контейнер и сеть, но сохраняет named volume и данные:

```powershell
docker compose down
```

**Разрушительный локальный reset — удаляет named volume и все данные этой локальной базы:**

```powershell
docker compose down --volumes
```

После reset следующий `docker compose up -d --wait postgres` создаст пустую базу, а приложение повторно применит Flyway migrations. Volume принадлежит Docker на конкретной рабочей станции и не синхронизируется с другими компьютерами.

## Постоянная внешняя PostgreSQL

Для явного подключения к общей PostgreSQL используется профиль `managed`. Он не активируется автоматически и не использует локальные Compose credentials как fallback. На каждой рабочей станции создайте игнорируемый файл из безопасного шаблона:

```powershell
Copy-Item config/application-managed-secrets.example.properties config/application-managed-secrets.properties
notepad config/application-managed-secrets.properties
```

Заполните в нём ровно три обязательных значения: `CRYPTO_RESEARCH_MANAGED_DB_URL`, `CRYPTO_RESEARCH_MANAGED_DB_USERNAME` и `CRYPTO_RESEARCH_MANAGED_DB_PASSWORD`. Приложение настраивает datasource этой общей identity, а Flyway наследует тот же datasource без отдельных managed-настроек. Поэтому выбранный пользователь должен иметь и runtime-права приложения, и DDL-права, необходимые для Flyway migrations. JDBC URL должен указывать на PostgreSQL 18 и явно задавать TLS `sslmode`; предпочтителен `verify-full` с доверенным CA. `require` допустим только как временный режим, поскольку он шифрует соединение, но не проверяет личность сервера.

Пароль, ранее отправленный в чат или другой внешний канал, перед использованием необходимо сменить. При ротации общей identity оператор сначала меняет credential на сервере, затем синхронно обновляет username/password в локальных файлах всех рабочих станций до следующего запуска. Значения из локального файла нельзя добавлять в Git, вставлять в issue, commit message, логи или запросы агентам. Файл хранится открытым текстом, поэтому доступ к нему должен быть ограничен средствами рабочей станции.

Запуск из Maven:

```powershell
$env:SPRING_PROFILES_ACTIVE = "managed"
.\mvnw.cmd spring-boot:run
```

Запуск собранного JAR:

```powershell
java -jar target/crypto-research-core-0.0.1-SNAPSHOT.jar --spring.profiles.active=managed
```

Профиль активируется только явно через `managed`. Если файл отсутствует или любое из трёх обязательных значений пусто, startup завершается ошибкой без перехода на локальную базу. Успешный startup означает, что Flyway с общей datasource identity применил или проверил migrations. После запуска проверьте readiness:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
```

Ответ должен иметь `status` = `UP`. До загрузки реальных данных отдельно подтвердите PostgreSQL 18, корректные server-side grants, firewall, автоматические backups и тестовое восстановление. Сам профиль `managed` эти внешние гарантии не создаёт.

## Проверка изменений

Локальный обязательный gate:

```bash
pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1
mvnw.cmd clean verify
openspec validate --all --strict --no-interactive
openspec doctor
```

GitHub Actions выполняет тот же контракт для push и pull request в стабильном job `quality-gate`. Maven-отчёты сохраняются в workflow artifact `maven-test-reports`.

Workflow в репозитории не включает branch protection автоматически. После отдельно разрешённого checkpoint и push необходимо дождаться первого успешного remote `quality-gate`; затем администратор репозитория назначает этот check обязательным в ruleset основной ветки.
