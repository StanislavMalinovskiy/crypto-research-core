# Диаграммы MULTIAGENT workflow

Ненормативное визуальное приложение к [MULTIAGENT workflow](../AGENT_WORKFLOW_MULTIAGENT.md). При любом расхождении действует документ workflow. Детали закрытия описаны в [процедуре закрытия](../agents/close-archive.md).

Как читать: каждая колонка — роль. Сплошная стрелка — передача capsule с фазой; пунктирная — возвращённый `STATUS:`. `loop` — ограниченный бюджет исправлений, `opt` — необязательная ветка. Названия статусов, фаз и ролей оставлены как в workflow, потому что это идентификаторы.

## 1. Маршрутизация: кто берёт задачу

```mermaid
flowchart TD
  I["Main: приём задачи"] --> T{"Строго TRIVIAL?<br/>(решает Main)"}
  T -->|"Да"| TR["Путь TRIVIAL (диаграмма 2)"]
  T -->|"Нет или есть сомнение"| P["Architect PLAN<br/>проверяет TR-01..TR-12"]
  P --> K{"Тир (решает Architect)"}
  K -->|"Нет TR, принятое поведение сохраняется"| N["NORMAL (диаграмма 3)"]
  K -->|"Нет TR, меняется наблюдаемый контракт"| C["CONTRACT (диаграмма 4)"]
  K -->|"Есть TR или обоснованное сомнение"| CR["CORE_RISK (диаграмма 4)"]
  N & C & CR --> R{"Риск для Builder (решает Architect)"}
  R -->|"Только документация"| AD["Architect DOCS, без Builder"]
  R -->|"ROUTINE"| BR["builder_sonnet_routine"]
  R -->|"STANDARD"| BS["builder_sonnet_standard"]
  R -->|"CORE_RISK"| BO["builder_opus"]
```

Кто делает ревью: NORMAL и CONTRACT проверяет тот же поток Architect; любую работу CORE_RISK, включая только документацию, проверяет новый Reviewer, который только читает.

## 2. TRIVIAL

```mermaid
sequenceDiagram
  autonumber
  actor O as Owner
  participant M as Main
  participant A as Architect

  O->>M: Задача и scope
  Note over M: Строго TRIVIAL?<br/>только ненормативный текст → да
  Note over M: Правка формулировок или форматирования<br/>факты и цели ссылок сохранены
  Note over M: git diff --check<br/>mvnw.cmd -Dtest=RepositoryConventionsTest test

  alt Обе проверки прошли
    M-->>O: DONE
  else Найдена исключённая область или сомнение
    M->>A: Capsule · PLAN (дальше как не-TRIVIAL)
  else Сбой инфраструктуры или ранее существовавшая ошибка
    M-->>O: BLOCKED
  end
```

Без субагентов, OpenSpec, ревью, проверки пересечений, DOCS_CLOSE и архивации.

## 3. NORMAL

```mermaid
sequenceDiagram
  autonumber
  actor O as Owner
  participant M as Main
  participant A as Architect
  participant B as Builder (по риску)

  O->>M: Задача и scope
  M->>A: Capsule · PLAN (данные о scope, без оценки риска)
  Note over A: Все TR проверены → нет<br/>тир, риск, инварианты, test_mode<br/>контракт по принятым источникам, бюджет
  A-->>M: STATUS: PLAN_READY (тир и риск зафиксированы)

  alt Код или тесты
    M->>B: Capsule · BUILD
    Note over B: Тесты (RED, если требуется)<br/>реализация, целевой GREEN
    B-->>M: STATUS: BUILD_DONE
  else Только документация
    Note over A: Architect DOCS
  end

  M->>A: REVIEW (тот же поток Architect, затронутые CI)

  loop repair_round 1..2, раунд 3 только с разрешения ревьюера
    A-->>M: STATUS: REPAIR
    M->>B: Capsule · REPAIR (тот же Builder)
    B-->>M: STATUS: BUILD_DONE
    M->>A: REVIEW
  end

  A-->>M: STATUS: APPROVE
  Note over M: Полная финальная проверка

  alt Проверка прошла
    M-->>O: DONE
  else Ошибка автора
    M->>B: REPAIR (тот же бюджет)
  else Ошибка контракта
    M->>A: PLAN (CONTRACT_CHANGED)
  else Сбой инфраструктуры или ранее существовавшая ошибка
    M-->>O: BLOCKED
  end
```

Без OpenSpec, проверки пересечений, DOCS_CLOSE и архивации.

## 4. CONTRACT и CORE_RISK

```mermaid
sequenceDiagram
  autonumber
  actor O as Owner
  participant M as Main
  participant A as Architect
  participant B as Builder (по риску)
  participant R as Reviewer (новый, только чтение)
  participant E as Escalation (новый, только чтение)

  O->>M: Задача и scope
  M->>A: Capsule · PLAN
  Note over A: TR-01..TR-12, тир, риск<br/>инварианты, test_mode<br/>активный OpenSpec change (strict valid)<br/>проверка пересечений через openspec list
  A-->>M: STATUS: PLAN_READY (тир и риск зафиксированы)

  M->>B: Capsule · BUILD
  Note over B: RED на поведенческой проверке<br/>семантическая заморозка тестов<br/>реализация, целевой GREEN
  B-->>M: STATUS: BUILD_DONE (tests_changed_after_red)

  M->>R: Capsule · REVIEW
  Note over R: CORE_RISK → новый Reviewer, полная матрица CI-01..CI-15<br/>CONTRACT → вместо него тот же поток Architect

  loop repair_round 1..2, раунд 3 только с разрешения ревьюера
    R-->>M: STATUS: REPAIR
    M->>B: Capsule · REPAIR (тот же Builder или тот же Architect для документации)
    B-->>M: STATUS: BUILD_DONE
    M->>R: REVIEW
  end

  opt Неразрешённый спор, дефект после раунда 3 или нет безопасного исправления
    R-->>M: STATUS: ESCALATE
    M->>E: Capsule · CHALLENGE (один ограниченный вопрос)
    E-->>M: verdict = REPAIR | REPLAN | APPROVE | OWNER_DECISION
    Note over M: REPAIR → автору (остаток бюджета)<br/>REPLAN → Architect PLAN<br/>OWNER_DECISION → BLOCKED, вопрос владельцу
  end

  R-->>M: STATUS: APPROVE
  M->>A: DOCS_CLOSE
  Note over A: Статус завершения, проверенные чекбоксы<br/>ссылки на доказательства, только несемантические правки документации
  A-->>M: DOCS_CLOSE завершён
  Note over M: Полная финальная проверка → checkpoint
  M->>A: ARCHIVE (OpenSpec CLI)
  A-->>M: Архивация завершена
  Note over M: Пост-проверки
  M-->>O: DONE
```

## 5. Статусы

В workflow ровно семь состояний. Причины вроде `CONTRACT_CHANGED` и вердикты вроде `REPLAN` — это атрибуты, а не состояния.

```mermaid
stateDiagram-v2
  state review <<choice>>

  [*] --> PLAN_READY : Architect PLAN
  PLAN_READY --> BUILD_DONE : Builder BUILD
  PLAN_READY --> review : Architect DOCS (только документация)
  BUILD_DONE --> review : Ревью

  review --> APPROVE
  review --> REPAIR
  review --> ESCALATE
  review --> BLOCKED : TEST_SPEC_ERROR и другие причины

  REPAIR --> BUILD_DONE : исправление Builder
  REPAIR --> review : исправление документации Architect

  ESCALATE --> PLAN_READY : CONTRACT_CHANGED или REPLAN
  ESCALATE --> REPAIR : вердикт REPAIR
  ESCALATE --> APPROVE : вердикт APPROVE
  ESCALATE --> BLOCKED : вердикт OWNER_DECISION

  BLOCKED --> PLAN_READY : решение владельца
  APPROVE --> DONE : проверка и закрытие Main
  DONE --> [*]
```

## 6. Кто что может делать

| Роль | Фазы | Что пишет | Что возвращает |
|---|---|---|---|
| Owner | Намерение, scope, снижение риска | — | Решения |
| Main | Приём, правка TRIVIAL, маршрутизация, финальная проверка, checkpoint, пост-проверки | Только текст TRIVIAL | DONE |
| Architect | PLAN, DOCS, REVIEW (NORMAL/CONTRACT), REPAIR, DOCS_CLOSE, ARCHIVE | Активный change, документация задачи | PLAN_READY, APPROVE, REPAIR, ESCALATE, BLOCKED |
| Builder | BUILD, REPAIR | Реализация и тесты в рамках контракта | BUILD_DONE, BLOCKED |
| Reviewer | REVIEW (CORE_RISK) | Только чтение | APPROVE, REPAIR, ESCALATE, BLOCKED |
| Escalation | CHALLENGE | Только чтение | Атрибут verdict |
