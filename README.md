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
| [docs/AGENT_WORKFLOW.md](docs/AGENT_WORKFLOW.md) | Адаптивный пятиролевой процесс работы с Codex-агентами и схема ответственности за тесты |
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
| [docs/archive/LEGACY_V5_ARCHITECTURE_MAPPING.md](docs/archive/LEGACY_V5_ARCHITECTURE_MAPPING.md) | Историческая v5 multi-module mapping; не источник истины |
| [docs/notes/THINK.md](docs/notes/THINK.md) | Черновые идеи и вопросы |

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

Текущий storage boundary сохраняет stable-inclusion raw provider evidence с точным CAIP-2 network identity и payload в `marketdata.raw_chain_events`, принимает равный retry без изменения первой записи и отклоняет конфликтующие immutable evidence. Сам storage не проверяет finality; provisional ingestion требует отдельного finality/reorg change. Следующий walking skeleton использует записанный provider fixture: raw input → normalized swap → signal snapshot → outcome → reproducible report. Реальный provider adapter следует отдельным change. PAPER/LIVE execution отсутствует в MVP; любые execution gates потребуют отдельного одобренного change и ADR.

## Java baseline

Production-код, тесты и упакованный JAR используют стабильный Java 25 API без `--enable-preview`:

```bash
java -jar target/crypto-research-core-0.0.1-SNAPSHOT.jar
```

Preview-функция может быть включена только отдельным OpenSpec change и superseding ADR с указанием точного JEP, причины, runtime-флага и JDK upgrade verification.

## Проверка изменений

Локальный обязательный gate:

```bash
mvnw.cmd clean verify
openspec validate --all --strict --no-interactive
openspec doctor
```

GitHub Actions выполняет тот же контракт для push и pull request в стабильном job `quality-gate`. Maven-отчёты сохраняются в workflow artifact `maven-test-reports`.

Workflow в репозитории не включает branch protection автоматически. После отдельно разрешённого checkpoint и push необходимо дождаться первого успешного remote `quality-gate`; затем администратор репозитория назначает этот check обязательным в ruleset основной ветки.
