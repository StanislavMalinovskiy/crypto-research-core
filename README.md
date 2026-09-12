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
| [docs/ROADMAP.md](docs/ROADMAP.md) | Главный документ и источник истины по архитектуре и этапам |
| [docs/PROJECT_SUMMARY.md](docs/PROJECT_SUMMARY.md) | Краткий контекст проекта для новой сессии |
| [docs/TECH_STACK.md](docs/TECH_STACK.md) | Зафиксированный технологический стек |
| [docs/WIKI.md](docs/WIKI.md) | Развёрнутые продуктовые и исследовательские заметки |
| [docs/API_SERVICES_ARCHIVE.md](docs/API_SERVICES_ARCHIVE.md) | Архив обзора API и сервисов; не источник истины |
| [docs/PAID_API_ARCHIVE.md](docs/PAID_API_ARCHIVE.md) | Архив платных API; не источник истины |
| [docs/THINK.md](docs/THINK.md) | Черновые идеи и вопросы |

## Как передать контекст Codex CLI

В начале работы попросите Codex сначала прочитать этот файл, затем:

1. `docs/PROJECT_SUMMARY.md`
2. `docs/ROADMAP.md`
3. `docs/TECH_STACK.md`

`ROADMAP.md` имеет приоритет при расхождениях. Архивные документы используются только как справочные материалы.

## Первый технический шаг

1. Поместить `pom.xml` и каталог `docs/` в корень Java-проекта.
2. Сгенерировать Maven Wrapper.
3. Создать минимальное приложение и восемь корневых пакетов модулей.
4. Добавить тест `ApplicationModules.of(...).verify()`.
5. Поднять PostgreSQL через Testcontainers и выполнить первую миграцию Flyway.
6. Только после проверки границ начать реализацию `marketdata` vertical slice.

## Java preview

Сборка, тесты и `spring-boot:run` настроены с `--enable-preview`. Для запуска упакованного JAR этот флаг также обязателен:

```bash
java --enable-preview -jar target/crypto-research-core-0.0.1-SNAPSHOT.jar
```

