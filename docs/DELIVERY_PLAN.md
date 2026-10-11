# План реализации

**Обновлено:** 11 октября 2026 года

## Назначение

Это короткая рабочая карта от текущего состояния репозитория до решения, основанного на исследовательских данных. Она отвечает на три вопроса: на каком этапе находится проект, какая крупная задача выполняется сейчас и какие задачи ожидаются дальше.

Источники имеют разные обязанности:

- [Roadmap](ROADMAP.md) определяет продуктовые гипотезы, долгосрочные возможности и направление.
- Этот Delivery Plan определяет этапы, порядок крупных рабочих пакетов и их статус.
- [DELIVERY_PLAN_FIXES](DELIVERY_PLAN_FIXES.md) — companion remediation-карта и traceability по итогам двух внешних аудитов 2026-09-20; определяет пакеты F0–F9 до реальных данных и decision support.
- [OpenSpec changes](../openspec/changes) содержат требования, дизайн и подробные чекбоксы работы, для которой выбранный workflow требует change; применимость определяют [DEFAULT](AGENT_WORKFLOW.md) и [MULTIAGENT](AGENT_WORKFLOW_MULTIAGENT.md).
- [ADRs](adr/README.md) фиксируют принятые архитектурные решения.
- [Main specs](../openspec/specs) описывают принятое проверяемое поведение.
- Код и тесты подтверждают фактически работающую реализацию.

Задачи ниже являются направляющей картой, а не неизменным обязательством или железным расписанием. По мере реализации, исследования провайдеров, получения данных и обнаружения новых зависимостей задачи могут добавляться, разделяться, объединяться, переставляться или откладываться. Такое изменение должно быть внесено в план явно; детали активной реализации ведутся по выбранному workflow, а принятые ADR и main specs не переписываются планом молча.

Допустимые статусы: `Done`, `Current`, `Next`, `Planned`, `Deferred`, `Paused` (явная остановка до решения владельца).

## Текущая позиция

- **Offline RPC-reuse v6 — Done bounded:** [верифицированный scoped repair](../openspec/changes/add-bounded-pumpswap-pool-collector/design.md#offline-v6-repair-final-verified-status-2026-10-11) получил native Reviewer final APPROVE,77 targeted tests PASS и NEW Main complete gate PASS587 (303 unit+284 PG,0 failures/errors/skips),18:36; integrity/Docker/strict24/24/doctor/working+scoped cached diff exit0,15 gate inputs unchanged, code commit `e9edacb2b96656c40abbe905e7bbb0daebc51a81`, без push. Trusted pre-fetch dedup и reuse уже полученных полных history rows сохраняют exact raw/source receipts; cumulative fractional accounting не меняет October authority/reservations и не включает новую ставку.8.1–8.4 verified;3.3/4.4 unchecked. Recurring30s aggregate test-JVM graceful-cleanup caveat остаётся UNVERIFIED, не proven product cause. Активный change не архивирован; live savings/$15 fit не измерены, нового запуска/деплоя/автосбора нет. Следующее разрешённое действие: scoped-проверки итоговых документов и docs-only commit.

- **Offline-разбор стоимости 11 октября — Done:** [декомпозиция сохранённой Contabo-пробы](notes/CONTABO_COST_BREAKDOWN_2026-10-11.md) без новых provider calls. Текущая официальная Solana WSS ставка0,0002 CU/byte отличается от старого immutable model0,04 в200 раз; отдельный documentary scenario даёт~$0,0317 за mixed partial окно, не invoice. Подтверждены302 повторных getTransaction responses; переиспользование уже доступных полных history rows вместе с dedup могло исключить564 responses/22560CU без двойного счёта. Последующий v6 repair реализован/qualified offline (выше);564 responses остаются hypothetical opportunity, не измеренная экономия или доказанный fit в$15. WSS, полный failed raw, states и explicit gaps сохраняются. Нового запуска/смены monthly authority/permanent activation нет.
- **Серверная проба 11 октября — завершена, PARTIAL / INCONCLUSIVE:** отдельное разрешение владельца prospectively разрешило deployment на Contabo, managed V14/V15 и ровно один ручной ~часовой запуск прежних трёх pools в пределах Alchemy $15. Backup restore и миграции проверены; сбор остановился через29м12с на унаследованном RPC allowance59750CU, не на месячном лимите. Сохранены1166 raw TX (224 successful/942 failed) и808 states;157 PENDING,6 transaction/9 state incomplete intervals остаются явными. Process завершён, автозапуск/повтор/permanent capture не включены. [Фактический итог и evidence](notes/CONTABO_BOUNDED_CAPTURE_2026-10-11.md): полный час не PASS; actual billing UNKNOWN, локальная оценка ~$0,099, месячный mixed-partial projection не подтверждает fit в $15. Прежние LOCAL_ONLY/no-activation записи ниже описывают предыдущие bounded scopes, а не отменяют это отдельное разрешение; R1 PAUSED, pools/push/archive/admission не расширены.
- **Решение владельца 7 октября:** R1-E2/D1 — `PAUSED`; factual `D1 INSUFFICIENT` (`e386884`) не меняется. Calibration `1.1.0`/P1 отложены до явного перезапуска и прежних prerequisites. Protocol/raw/census/receipts и unchecked tasks сохраняются; новые Helius R1 credits, загрузки и ремонты не выделены.
- **Текущий результат:** bounded PumpSwap collector реализован и проверен; [квалифицированный configured6h результат](../openspec/changes/add-bounded-pumpswap-pool-collector/design.md#configured6h-terminal-acceptance-and-explicit-incomplete-coverage-2026-10-10), документальный commit `ff267c1`, — PASS только ограниченного operational soak:124 raw TX/400 states, четыре reconnect, неполное explicit coverage/actual billing UNKNOWN. Не continuous gap-free LIVE, production-primary или normalized admission; старые STOP/receipts неизменны.
- **Подтверждённый decoder result:** [offline raw-operation decoder + quality](../openspec/changes/add-pumpswap-raw-operation-decoder/design.md#implemented-and-verified-unarchived-2026-10-11) сохранённого484bec54 реализован/независимо reviewed: Main complete gate450 tests/0 failures/errors/skips; code commit `0b066b1b87bd69e5045497b87369a9e8cff61148`, без push. Независимая post-gate qualification/reproduction PASS:47/75 полностью распознанных успешных TX (62.67%),68 отдельных операций, все124 статуса сверены,49 FAILED без операций/9 NON_OPERATION/19 UNSUPPORTED/0 PARTIAL; unchanged>=38 criterion выполнен. Actual endpoints и собственные event/transfer paths обязательны; fee/rare-format/deployment/gap/loss/billing/admission unknowns остаются явными. Change active/unarchived, не accepted main-spec capability. Ordinary `buy` подтверждён unit pin, но recognized saved examples отсутствуют. Последующее owner continuation prospectively supersedes прежний PLAN-only checkpoint для ограниченных persistence/monitor задач: они реализованы, independently reviewed и завершили Main full gate/commit (ниже). Семишаговая очередь не возобновляет R1/calibration/P1 и не разрешает новый collector run/deployment/расширение pools.

- **Текущая bounded implementation:** [operation persistence](../openspec/changes/add-pumpswap-operation-persistence/design.md#implemented-and-verified-unarchived-2026-10-11) и [capture monitor](../openspec/changes/add-bounded-capture-monitor/design.md#implemented-and-verified-unarchived-2026-10-11) получили fresh independent APPROVE по stable union. Saved qualification03 в isolated PostgreSQL18.6/V14 сверяет все124 статуса/68 операций и полное readback/retry/regroup/label content; storage repair GREEN144 и monitor GREEN61 имеют0 failures/errors/skips. V14 не применялся к managed DB; changes active/unarchived, Main mandatory clean verify PASS483 (266 unit+217 PG, включая unchanged collector85),0 failures/errors/skips; integrity/Docker/strict21/21/doctor/working+scoped-cached diff exit0, product commit `c6fad23544b320426ecd720c2716c670138473f4` без push. Ограниченный пакет Done, не full F3 и не новая live/paid/managed/deployment/pool-expansion/push/archive authority; raw/fees/coverage/loss/billing/PIT/admission limitations и R1 PAUSED сохранены.

- **Завершённый bounded пакет — Done:** [continuous readiness](../openspec/changes/add-pumpswap-continuous-readiness/design.md#implemented-and-verified-unarchived-2026-10-11), fresh independent APPROVE03 и Main fullgate02 PASS521 (272 unit+249 PG),0 failures/errors/skips,17:17; unchanged collector85 PASS536.359s и final-source wall-clock1 PASS216.770s как обычная gate regression. Integrity/Docker/strict23/23/doctor/working+scoped-cached diff exit0,29 hashes unchanged, product commit `cc415c7df747724ab257850c271aef9b83399e9c` без push. Fullgate01 FAIL и review repair1+gate repair1 сохранены. Default-off V15/cumulative budgets/restart/post-seal derivation — LOCAL_ONLY synthetic/isolated PostgreSQL18.6, managed V15 не применялся; active/unarchived/not accepted/not activated, не full F3. Aggregate30s test-JVM shutdown cleanup caveat остаётся nonblocking по independent Reviewer: полная очистка test contexts не доказана, причина отдельных pool delays неизвестна; optional отдельный suite follow-up, без ослабления timeout/assertions. Live/paid/deployment activation не разрешена, R1 PAUSED и coverage/billing/admission limitations сохранены.

- **Завершённый bounded пакет — Done:** [offline pool metrics](../openspec/changes/add-pumpswap-offline-pool-metrics/design.md#final-verified-status-2026-10-11), fresh independent APPROVE, targeted42 PASS и Main complete gate PASS541 (285 unit+256 PG),0 failures/errors/skips,17:34; integrity/Docker/strict24/24/doctor/working+scoped-cached diff exit0,15 hashes unchanged, product commit `32394fab2c0a0c3d4fd6c888da7ae764c3567611` без push. Collector85 PASS536.1s и readiness wall-clock1 PASS216.0s/actual child exit — обычные regressions. Saved124 TX/68 operations (27 buys/41 sells, все68 priced),51 populated300s candles/15 наблюдённых pool/mint groups, exact revisions/repeat/raw/StoredBatch/all-row counts unchanged — не расширение subscriptions. LOCAL_ONLY, active/unarchived/not accepted; native diagnostic не USD/liquidity/signal-readiness/admission. Aggregate30s test-JVM shutdown/Hikari caveat повторился: полная cleanup contexts UNVERIFIED, не доказанная product cause, без изменения timeout/assertions. Нет activation/network/live/managed/paid authority; R1 PAUSED.

- **Завершённый bounded пакет — Done:** [operator report](../openspec/changes/add-pumpswap-operator-report/design.md#final-verified-status-2026-10-11), fresh independent APPROVE,27 unit+23 PG=50 PASS/0 failures/errors/skips и saved qualification02:124 refs/68 operations/51 observation buckets,505905 JSON bytes/1920 summary bytes, repeat/metrics fingerprint/raw/store/all-table counts unchanged. MONITOR_ABSENT честно отражает отсутствие экспортированных capture metadata в isolated DB, не потерю фактических retained records. Два независимых read-only API panels, REQUESTED_TOGETHER_UNVERIFIED/INDEPENDENT_PANELS, observation-time activity/DETERMINISTIC_NOT_ONCHAIN_CHRONOLOGY; не общая snapshot/binding/admission. Main complete gate PASS558 (294 unit+264 PG),0 failures/errors/skips,17:44; integrity/Docker/strict24/24/doctor/working+scoped-cached diff exit0,15 inputs unchanged, commit `339ad26dc7f41b396b26f76d8007e99792cac279` без push. Collector85 PASS535.4s/wall-clock1 PASS216.8s actual child exit; все6 tasks verified, Done bounded API JSON/summary, не фактический screen/runtime trigger; LOCAL_ONLY/unarchived/not accepted, без activation. Metrics Done bounded (product `32394fab`, docs `84dce942`), readiness Done bounded/default-off; R1 PAUSED и cleanup caveat сохранены.

- **Отдельное решение о серверном activation:** [краткий owner decision packet](../openspec/changes/add-pumpswap-continuous-readiness/design.md#решение-владельца-об-activation-только-информационный-пакет) фиксирует неизвестные managed V14/V15/server/shutdown/billing условия и условные estimates, не разрешает deployment/paid run. Это решение не блокирует независимую local/offline metrics реализацию.

- **Подтверждённая подготовка к исследованию:** A1+A2 реализованы, проверены и [архивированы](../openspec/changes/archive/2026-10-01-version-market-facts-and-split-evidence/tasks.md): versioned market facts и отдельные decision/evaluation snapshots доступны через публичные API; V10–V12 сохраняют legacy V1–V9 evidence. [R1 protocol](research/R1_RESEARCH_PROTOCOL.md) для wallet copier и early multi-wallet заморожен как `1.0.0`; [freeze record](research/R1_PROTOCOL_FREEZE.md) закрепляет коммит и SHA-256.
- **Получен фактический вердикт D1/E2:** [итог 7 октября](notes/R1_E2_D1_COMPLETION_RESULT_2026-10-07.md) — `D1 INSUFFICIENT`, H1/H2 `INCONCLUSIVE/data insufficient` (не проверялись,не «нет преимущества»). Текущая полная offline-проверка report3 завершена native2: semantic integrity,budget,recorded timing PASS для300 токенов/18,436GB raw/1 365 027 уникальных TX. Доказано293982 экономических действий,145735 с определёнными attributable fees; это не полный denominator D1. Независимая актуальная сверка200 действий дала200/200 совпадений и ноль двойного объёма в выборке: апрель193/май7,без доказанных июньских/июльских/августовских strata. Полные depth/entry/horizon/exit/gap/PIT и candidate/trigger denominators не доказаны. Независимое финальное ревью APPROVE относится к честному отрицательному вердикту,не admission. Дальнейших загрузок/ремонтов сейчас нет; calibration/P1 отложены. [D1](../openspec/changes/establish-r1-d1-data-gate/tasks.md) и [E2](../openspec/changes/preregister-r1-e2-pumpswap-cohort/tasks.md) остаются active/unarchived из-за невыполненных критериев,а не выполняющегося эксперимента. Сохранённые финальные scoped checks PASS/native0; текущий маршрут — PAUSED R1 и отдельный PLAN первой интеграции; старое review не разрешает новые data calls.
- **Фактическая загрузка E2:** [Протокол](research/R1_E2_RESEARCH_PROTOCOL.md) `1.0.0` FROZEN,commit `9e892d2`,SHA `aa0a0d72…`; фиксированные300 токенов captured:258 COMPLETE/42 CAP,1760 страниц,176000 reserved credits/actual billing UNKNOWN/reported USD0. CAP — незавершённая история при14 страницах,не процент временных gaps. Внешние receipts:`main-d1-completion-source-terminal-1.json`,`main-d1-completion-fullcohort-report-3.json`,`builder-realdata-independent200-receipt-2.json` в `C:\crypto-research-evidence\r1-e2-path-v1-gate-20261003`. Repair6/source-code checkpoint `fbc97b7`/документальный `bfb3a23`; старые STOP/report2 LINEAGE_CHANGED и [первое окно](notes/R1_E2_D1_WINDOW_RESULT_2026-10-05.md) остаются историческими.9.7 вердикт зафиксирован;9.5 unchecked из-за631,310342s monitoring-gap,9.6 unchecked из-за неполной field/whole-strata проверки. Unproved buyback/cashback,Token2022 и один SOURCE_CONFLICT не доказывают отсутствие данных или вину провайдера. Deployment/global earliest/PIT/rights не доказаны; admission=false.
- **Текущий этап:** Этап 3 — Реальные данные Solana (исполняется через remediation-пакеты [DELIVERY_PLAN_FIXES](DELIVERY_PLAN_FIXES.md)).
- **Незакрытый F1:** [исследовательский F1 change](../openspec/changes/archive/2026-09-29-define-solana-data-provider-contract/tasks.md) архивирован с provisional shortlist, но исходные S1–S5/3.6 отложены и не верифицированы; primary provider не выбран. [Bounded Alchemy A/B и финальная A-попытка](notes/SOLANA_PROVIDER_SPIKE_RESEARCH_2026-09-27.md) завершены как исследовательские receipts: оба A `INCONCLUSIVE` с нулём чистого LIVE-времени (финальный остановлен локальным `PROCESSING_LIMIT` в catch-up), B `INCONCLUSIVE/MATRIX_COMPLETE` (12/12 исторических чтений; два vault reserve inputs подтверждены для одного child block, pool layout не декодирован). Финальная A-попытка и разрешённое root-only исправление израсходованы; повторов в этом change нет. Решение о провайдере и фактическая gRPC-стоимость отложены: после прогона dashboard/invoice не были доступны для сверки.
- **Завершённая подготовка:** F0 синхронизировал документы; F2 core storage принят и архивирован — Flyway V5–V9, immutable raw/price/liquidity/USD/universe storage, lineage constraints и bounded batch paths реализованы.
- **Завершённые срезы 27 сентября:** приняты и архивированы [bounded LIQUIDITY_SPIKE windows](../openspec/changes/archive/2026-09-27-bound-liquidity-spike-observation-windows/verification.md), [recorded-replay telemetry](../openspec/changes/archive/2026-09-27-add-recorded-replay-operational-telemetry/tasks.md) и [evaluation-report retry hardening](../openspec/changes/archive/2026-09-27-harden-evaluation-report-retry-semantics/tasks.md). Это завершённые изменения первого среза; production F6.2 и live monitoring F3.4 остаются частично открытыми.
- **Агентная разработка:** приняты instruction diet, workflow safety rules, workflow simplification и [реформа процесса 4 октября](../openspec/changes/archive/2026-10-04-simplify-multiagent-process/tasks.md), commit `815e635`; актуальные роли, tiers, review и gates определяет [выбранный workflow](../AGENTS.md#agent-workflow-mode). Исторические benchmark findings не являются текущими правилами routing; новая реформа не применяется задним числом к старым прогонам и доказательствам.
- **Операционная работа после выбора источника:** F3 — bounded ingestion выбранного transport с явными timeout, rate, concurrency, finite retry policies, gap recovery и минимальной operational visibility. D1 определяет доступность исторических полей для R1; он сам по себе не выбирает production transport и не закрывает F3. До допуска live data F3 также добавляет forward migrations для token decimals, provider-visible/modeled availability и явного quality provenance.
- **Условие перехода к этапу 4:** реальные текущие и исторические Solana observations воспроизводятся с raw lineage, trusted observation time, parser identity и видимыми gaps без повторных доменных эффектов (F3.6).

## Сводка этапов

| Этап | Статус | Результат |
|---|---|---|
| 1. Архитектурный фундамент | Done | Воспроизводимая модульная основа и обязательные quality gates готовы к бизнес-разработке |
| 2. Первый вертикальный срез | Done | Один сигнал на записанных данных измеряется от raw input до воспроизводимого отчёта |
| 3. Реальные данные Solana | Current | Текущие и исторические данные проходят проверенный путь без скрытых пропусков |
| 4. Аналитика `risk` и `wallet` | Planned | Сигналы получают point-in-time сведения о токенах и кошельках |
| 5. Сигналы и их оценка | Planned | Семейства сигналов получают сопоставимые cost-aware outcomes без смещений |
| 6. Исследовательское решение | Planned | Evidence Report обосновывает углубление, изменение, продление или остановку направления |
| 6A. Decision support и forward shadow | Planned | Подтверждённые результаты превращаются в advisory feed без исполнения сделок (F8) |
| 7. Исполнение | Deferred | Paper/live рассматриваются только после доказательств и отдельного safety design |

## Исследовательская последовательность R1

Этапы ниже сохраняют свои выходные условия. R1 сейчас PAUSED; этот файл — canonical operational plan, [research replan30 сентября](notes/ARCHITECTURE_AND_RESEARCH_REPLAN_2026-09-30.md) — историческая записка, не текущая authority. Владелец подтвердил ограниченную очередь:

1. Saved-raw decoder + quality: большинство успешных TX доказанно распознано, все остальные имеют status/reason; редкие форматы в backlog.
2. Persistence операций с parser version, immutable lineage и идемпотентным re-decode: Done bounded: implemented/qualified/reviewed, Main483-test gate PASS/commit `c6fad235`; active/unarchived, не normalized admission. [Verified evidence](../openspec/changes/add-pumpswap-operation-persistence/design.md#implemented-and-verified-unarchived-2026-10-11).
3. Bounded recovery уже достаточно для согласованного MVP с explicit incomplete intervals; минимальный monitor Done bounded: implemented/verified/reviewed, Main483-test gate PASS/commit `c6fad235`, active/unarchived. [Verified evidence](../openspec/changes/add-bounded-capture-monitor/design.md#implemented-and-verified-unarchived-2026-10-11). WS1011 root cause не исследуется, zero-loss goal не ставится; не full F3.
4. [Local continuous readiness](../openspec/changes/add-pumpswap-continuous-readiness/design.md#implemented-and-verified-unarchived-2026-10-11) Done bounded LOCAL_ONLY/default-off: APPROVE03, Main521-test gate PASS/commit `cc415c7d`, active/unarchived/not accepted/not activated; test-suite cleanup caveat сохранён. Реальные bounded long run и deployment остаются только после разрешения владельца, без автоматического расширения caps; не full F3.
   Отдельное последующее разрешение11 октября выполнено только как [одна manual Contabo-проба](notes/CONTABO_BOUNDED_CAPTURE_2026-10-11.md): V14/V15 применены,29м12с до RPC resource stop, PARTIAL/INCONCLUSIVE. Continuous activation, permanent autorun и следующий запуск не разрешены; полный F3/data-quality gate не закрыт.
5. Candles/metrics и первые сигналы на текущих pools с reuse существующих модулей; [offline pool-metrics diagnostic](../openspec/changes/add-pumpswap-offline-pool-metrics/design.md#final-verified-status-2026-10-11) Done bounded: independently APPROVED, Main541-test gate PASS/commit `32394fab`, active/unarchived, не signal-readiness. [Operator report](../openspec/changes/add-pumpswap-operator-report/design.md#final-verified-status-2026-10-11) Done bounded LOCAL_ONLY API JSON/summary: independently APPROVED, Main558-test gate PASS/commit `339ad26d`, active/unarchived/not accepted/not activated, не actual screen. Verified chain-time/slot/transaction-position market chronology до signal use, USD/liquidity/risk/PIT/source-quality и signal bridge не qualified; observation-time buckets не market chronology, catch-up receipt не переносится в chain-time. Далее genuine owner/delegate choices: managed V14/V15/server/budget/activation, qualified USD/liquidity/risk/chronology/PIT/source-quality и signal choice; pools — owner-only. Нет автоматически разрешённых extra offline features/research package/400-state qualification или assumed admission. Wallet/risk и signal bridge — по фактической необходимости с отдельной evidence/admission проверкой.
6. Расширение pools только по разрешению владельца.
7. Evaluation и advisory UI без торговли, с отдельной применимой evidence/admission проверкой.

Допустимая неполнота MVP не отменяет financial/PIT/raw-evidence/admission safety и запрет silent loss/fabricated continuity. Через2–3 дня — checkpoint прогресса/причин задержки, не новая абсолютная остановка. Короткий отчёт владельцу: result / ready / deferred / evidence-risk / next. Предварительный анализ после2–4 недель накопления не обещает готовность продукта/edge. Большой historical scanner, все DEX, полный F3 и gRPC SDK не входят в первый срез.

| Пакет | Статус | Подтверждённый результат или следующий выход |
|---|---|---|
| A1+A2 | Done | [Versioned facts и dual evidence](../openspec/changes/archive/2026-10-01-version-market-facts-and-split-evidence/tasks.md), commit `ad61760`, merge `9c53f61`; legacy compatibility и chronological evaluation проверены. |
| R1 preregistration | Done | [Архивированный протокол](../openspec/changes/archive/2026-10-01-preregister-r1-research-protocol/tasks.md) `1.0.0`, freeze commit `6e5647e`, утверждённая offline-процедура. |
| D1 | Paused | [Фактический вердикт](notes/R1_E2_D1_COMPLETION_RESULT_2026-10-07.md) INSUFFICIENT; H1/H2 INCONCLUSIVE/data insufficient,не tested/no-edge. Report3 и audit2 завершены/финальное factual review APPROVE; whole-envelope denominators/depth/entry/exit/gaps/PIT/strata не доказаны. Change active/unarchived; продолжение только после отдельного решения,не новый автоматический ремонт. |
| R1-E2 / census | Paused | Бounded evidence/result завершены:300 captured258COMPLETE42CAP/1760 страниц/18,436GB raw/176000reservedactualUNKNOWNUSD0; report3 PASS scoped integrity/budget/timing,1 365 027TX; актуальная выборка200/200,апрель193/май7,ноль double-volume в sample.9.7 вердикт записан;9.5 cadence-gap/9.6 whole-coverage unchecked,change active/unarchived. Протокол/старые STOP/census/PIT/rights сохранены; без новой загрузки. |
| R1 calibration | Deferred | Только после фактического D1 PASS записать `1.1.0` для разрешённых `C-1`/`C-2`/`C-3`; критерии и правила отбора остаются frozen. |
| P1 | Deferred | H1/H2 пока INCONCLUSIVE (не проверялись). Pilot не запускать до D1/calibration и применимых frozen validation/holdout условий. |
| S1 | Planned | Prospective forward shadow как дальнейшая проверка по решению R1; `INCONCLUSIVE` требует отдельного обоснованного решения владельца. |
| I1 / F1–F3 | Planned | Обосновать production source/capacity/ingestion измеренными потребностями и оставшимися provider/gap acceptance. |

Каждый data stage получает собственный OpenSpec change. Эта карта не разрешает provider calls, расходы, новые scripts или просмотр outcomes.

## Этап 1 — Архитектурный фундамент

**Цель:** создать сопровождаемую и проверяемую основу без преждевременной бизнес-реализации.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 1.1 | Done | Создать один Maven-модуль, один Spring Boot JAR и шесть проверяемых Spring Modulith boundaries. |
| 1.2 | Done | Зафиксировать архитектурные, persistence, transaction, concurrency, operations и testing contracts. |
| 1.3 | Done | Ввести chain-aware identity и воспроизводимость времени, чисел, ordering, provenance и seed. |
| 1.4 | Done | Реализовать первое append-only хранилище raw market-data с точной lineage и идемпотентным retry/conflict. |
| 1.5 | Done | Закрепить локальный и CI quality gate, test-integrity preflight и проверяемые repository conventions. |
| 1.6 | Done | Упростить агентную разработку: DEFAULT без подагентов, MULTIAGENT только после ручного включения. |
| 1.7 | Done | Подтвердить удалённый gate для точного checkpoint, required check основной ветки и передать Current этапу 2. |

**Условие завершения:** фундамент воспроизводится чистым checkout, все обязательные локальные и удалённые проверки проходят, а основная ветка защищена стабильным required check.

## Этап 2 — Первый вертикальный срез

**Цель:** как можно раньше проверить полный исследовательский путь на записанном входе без сложности реального провайдера.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 2.0 | Done | Добавить переносимую локальную PostgreSQL для разработки, сохранив Testcontainers для тестов и отложив shared managed PostgreSQL до отдельного решения этапа 3. |
| 2.1 | Done | Реализовать и принять review OpenSpec change `build-first-signal-evaluation-skeleton` с точным контрактом одного end-to-end сценария. |
| 2.2 | Done | Пропустить записанный provider fixture через raw replay и нормализовать одно рыночное событие. |
| 2.3 | Done | Создать один версионированный сигнал с неизменяемым decision-time snapshot и provenance. |
| 2.4 | Done | Выбрать допустимую point-in-time цену и измерить один forward outcome без look-ahead. |
| 2.5 | Done | Сформировать детерминированный минимальный отчёт, связывающий input, signal, outcome и run identity. |
| 2.6 | Done | Подтвердить повторяемость полного пути и готовность тех же boundaries принять реальные Solana data. |

**Условие завершения:** одинаковый записанный вход детерминированно создаёт трассируемый сигнал, измеренный результат и одинаковый отчёт без нарушения границ модулей.

## Этап 3 — Реальные данные Solana

**Цель:** заменить записанный вход надёжными текущими и историческими наблюдениями Solana, сохранив проверенный путь.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 3.0 | Done | Настроить постоянную managed PostgreSQL для работы из нескольких мест и подтвердить профиль, секреты, Flyway, PostgreSQL 18.6, TLS и восстановление свежего backup; сетевая политика остаётся ответственностью оператора. |
| 3.1 | Deferred | F1 research и provisional shortlist архивированы, исходные S1–S5/3.6 остаются не верифицированы. Alchemy выбран для первой разработки, production transport acceptance открыт; исторические A/B INCONCLUSIVE не доказывают LIVE или general archive. |
| 3.2 | Done | Bounded raw collector и qualified configured6h soak verified; incomplete coverage/actual billing UNKNOWN. Не full F3, ongoing collection или normalized admission. |
| 3.3 | Done | [Offline PumpSwap raw-operation decoder](../openspec/changes/add-pumpswap-raw-operation-decoder/design.md#implemented-and-verified-unarchived-2026-10-11): v4 implementation/review,450-test Main gate и независимая saved qualification/reproduction PASS,47/75 TX/68 операций; commit `0b066b1`. Change active/unarchived, не accepted main-spec capability. Полная signal-grade normalization остаётся будущей; persistence реализован/квалифицирован/reviewed, Main483-test gate PASS/commit `c6fad235`. |
| 3.4 | Done | [Operation persistence](../openspec/changes/add-pumpswap-operation-persistence/design.md#implemented-and-verified-unarchived-2026-10-11) и [bounded monitor](../openspec/changes/add-bounded-capture-monitor/design.md#implemented-and-verified-unarchived-2026-10-11) bounded packet implemented/verified/reviewed, Main483-test gate PASS/commit `c6fad235`, active/unarchived. Existing bounded recovery не переоткрывается; full F3, long run/deployment/admission не завершены и требуют отдельной authority. |
| 3.5 | Planned | Сохранять observed price snapshots, source/quality facts и необходимые token discovery observations. |
| 3.6 | Planned | Выполнить идемпотентный исторический backfill и доказать отсутствие дубликатов и скрытой потери данных; текущие provider-пробы не реализуют backfill. |
| 3.7 | Planned | Измерить freshness, parse failures, coverage и gaps на длительном прогоне и закрыть data-quality gate этапа. |

**Условие завершения:** текущие и исторические данные воспроизводятся с raw lineage, parser identity и видимыми gaps без повторных доменных эффектов.

## Этап 4 — Аналитика `risk` и `wallet`

**Цель:** добавить point-in-time сведения о риске токена и поведении кошелька, необходимые для обоснованных сигналов.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 4.1 | Planned | Определить версионированные common и Solana-specific risk facts с проверяемой схемой и provenance. |
| 4.2 | Planned | Реализовать воспроизводимые решения `BLOCK`, `WATCH_ONLY`, `ALLOW` и append-only историю risk decisions. |
| 4.3 | Planned | Восстановить сделки кошелька и реализовать FIFO closed-trade PnL с cross-venue matching. |
| 4.4 | Planned | Рассчитать point-in-time wallet metrics, включая profit factor, average win/loss и ограничения выборки. |
| 4.5 | Planned | Ввести версионированный wallet score history, tiers, graduation и обновляемый watchlist. |
| 4.6 | Planned | Подтвердить, что risk и wallet APIs не читают будущее состояние и дают повторяемые snapshots для сигналов. |

**Условие завершения:** сигнальный слой может получить воспроизводимые risk и wallet facts на требуемый момент времени без прямого доступа к чужим таблицам.

## Этап 5 — Сигналы и их оценка

**Цель:** создать и сравнить значимые семейства сигналов без look-ahead, survivorship bias и оптимистичных издержек.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 5.0 | Done | Зафиксирован R1 `1.0.0` для wallet copier и early multi-wallet: гипотезы, grid, диапазоны, min sample, multiplicity policy и decision gates. D1 calibration и исследования остальных families остаются отдельной работой. |
| 5.1 | Planned | Определить versioned signal definitions, configuration identity, candidate journal и правила дедупликации. |
| 5.2 | Planned | Реализовать entry families Smart Wallet Buy, Multi Wallet Buy, Liquidity Spike и Holder Growth. |
| 5.3 | Planned | Реализовать Token Risk Alert как avoidance family и сохранять shadow outcomes отклонённых кандидатов. |
| 5.4 | Planned | Добавить score, grade, confidence и читаемое reasoning из неизменяемого decision-time evidence. |
| 5.5 | Planned | Реализовать virtual positions, bounded liquidity, dynamic friction и согласованные exit rules без реального исполнения. |
| 5.6 | Planned | Измерять entry и avoidance outcomes по согласованным горизонтам, сохраняя no-price и dead-token cases. |
| 5.7 | Planned | Сформировать сопоставимые отчёты по families и доказать одинаковые valuation, cost и data-quality policies. |

**Условие завершения:** каждый принятый и отклонённый кандидат получает подходящий трассируемый outcome, а семейства сравниваются по единой воспроизводимой методике.

## Этап 6 — Исследовательское решение

**Цель:** на накопленных данных решить, стоит ли углублять направление, менять гипотезу, расширять исследование или остановиться.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 6.1 | Planned | Зафиксировать достаточный dataset snapshot, cutoff, fingerprints, build, algorithms, configuration и seeds. |
| 6.2 | Planned | Выполнить детерминированный backtest/replay по families, horizons и релевантным market regimes. |
| 6.3 | Planned | Проверить sample size, expectancy after costs, drawdown, outlier dependence и чувствительность к data quality. |
| 6.4 | Planned | Сформировать воспроизводимый Evidence Report с раздельными entry и avoidance результатами. |
| 6.5 | Planned | Зафиксировать решение: deepen working family, pivot, extend data/research или honest exit. |
| 6.6 | Planned | Создать следующий план только из подтверждённых выводов, отдельно обосновав Base/EVM или более длинный walk-forward. |

**Условие завершения:** Evidence Report позволяет принять и воспроизвести явное решение без подмены отсутствующих доказательств архитектурными ожиданиями.

## Этап 6A — Decision support и forward shadow

**Статус:** Planned. Активируется после положительного или перспективного решения Этапа 6; соответствует пакету F8 в [DELIVERY_PLAN_FIXES](DELIVERY_PLAN_FIXES.md).

**Цель:** превратить подтверждённые результаты в понятный владельцу инструмент без исполнения сделок.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 6A.1 | Planned | Runtime trigger/scheduler с PostgreSQL-backed claiming (F8.1). |
| 6A.2 | Planned | Decision feed: advisory action, evidence status, measured expectancy, expiry/invalidation (F8.2). |
| 6A.3 | Planned | Минимальный канал доставки с dedup, expiry, retry и delivery audit (F8.3). |
| 6A.4 | Planned | Owner positions и position-aware `CONSIDER_EXIT` (F8.4). |
| 6A.5 | Planned | Forward shadow период без денег и сравнение с backtest assumptions (F8.5). |

**Условие завершения:** владелец понимает, что произошло и почему; forward results подтверждают или опровергают применимость backtest; signing и order submission отсутствуют.

## Этап 7 — Исполнение

**Статус этапа:** Deferred.

**Цель:** рассматривать paper и live только после положительного исследовательского решения и отдельного проектирования безопасности.

| ID | Статус | Рабочий пакет |
|---|---|---|
| 7.1 | Deferred | Подтвердить активационные evidence gates: положительные результаты, достаточный capital case и приемлемый risk profile. |
| 7.2 | Deferred | Принять отдельные ADR и OpenSpec design для signing, venue policies, limits, kill switches и audit trail. |
| 7.3 | Deferred | Реализовать paper trading и сравнить его результаты с backtest при тех же версиях и friction assumptions. |
| 7.4 | Deferred | Проверить walk-forward, paper expectancy, drawdown, deviation, operational readiness и manual controls. |
| 7.5 | Deferred | Только после независимого safety review разрешить ограниченный MANUAL_CONFIRM и поэтапный live rollout. |

**Условие активации:** этап 6 дал положительное решение, а отдельный approved change и ADR определили safety gates. До этого signing, order submission и PAPER/LIVE execution отсутствуют.

## Отложенные направления

- Base/EVM implementation и сравнение сетей — только после Solana evidence или доказанной необходимости сравнения.
- Cross-chain wallet identity, bridge-flow и capital rotation — только если межсетевое поведение станет исследовательским bottleneck.
- Более длинный backfill и walk-forward — после положительного или неоднозначного 90-day результата.
- Кэши, брокеры, дополнительные базы, partitioning и внешняя observability infrastructure — только по измеренной нагрузке.
- Funding bot и распределение капитала — отдельный проект после исследовательского и execution gates.

## Правило сопровождения

План пересматривается при архивировании change, завершении этапа, явной смене приоритета или появлении доказанной новой необходимости. Короткие рабочие пакеты и их статусы обновляются здесь; подробный контракт и evidence ведутся по выбранному workflow, а требования и чекбоксы OpenSpec — в change, когда он требуется. Новая задача добавляется тогда, когда без неё нельзя достичь результата этапа или сохранить корректность измерений, а не для фиксации каждой технической подзадачи.
