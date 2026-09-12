# Crypto Research Core

Стартовый комплект документации и Maven-конфигурации для продолжения разработки в Codex CLI.

## Зафиксированный фундамент

- Java 25, включая preview-возможности за внутренними API.
- Spring Boot 4.1.1.
- Spring Modulith 2.1.1.
- Maven.
- Синхронный Spring MVC.
- Spring Data JDBC и `JdbcClient`; JPA/Hibernate не используются.
- PostgreSQL + Flyway.
- Один Git-репозиторий, один Maven-модуль, один deployable JAR.
- Восемь логических модулей Spring Modulith: `kernel`, `governance`, `marketdata`, `risk`, `wallet`, `strategy`, `measurement`, `research`.

## Документы

| Файл | Назначение |
|---|---|
| [docs/ROADMAP.md](docs/ROADMAP.md) | Продуктовые цели, этапы и приоритеты |
| [docs/PROJECT_SUMMARY.md](docs/PROJECT_SUMMARY.md) | Краткий контекст проекта для новой сессии |
| [docs/TECH_STACK.md](docs/TECH_STACK.md) | Разрешённые технологии и версии |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Текущее сводное состояние архитектуры |
| [docs/GLOSSARY.md](docs/GLOSSARY.md) | Развёрнутый глоссарий проекта |
| [docs/modules/README.md](docs/modules/README.md) | Карта документации логических модулей |
| [docs/adr/README.md](docs/adr/README.md) | Журнал архитектурных решений |
| [docs/archive/API_SERVICES_ARCHIVE.md](docs/archive/API_SERVICES_ARCHIVE.md) | Архив обзора API и сервисов; не источник истины |
| [docs/archive/PAID_API_ARCHIVE.md](docs/archive/PAID_API_ARCHIVE.md) | Архив платных API; не источник истины |
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

## Первый технический шаг

Технический bootstrap описан change `bootstrap-modular-foundation` в `openspec/changes/`.
После его проверки отдельный change `establish-chain-identity-kernel` должен ввести минимальные chain-aware identities. Затем отдельные changes последовательно реализуют первый `marketdata` vertical slice; governance gates откладываются до появления PAPER/LIVE execution.

## Java preview

Сборка, тесты и `spring-boot:run` настроены с `--enable-preview`. Для запуска упакованного JAR этот флаг также обязателен:

```bash
java --enable-preview -jar target/crypto-research-core-0.0.1-SNAPSHOT.jar
```
