# Contabo: итог одной ограниченной пробы 11 октября 2026

## Результат

**PARTIAL / INCONCLUSIVE, не полный час PASS.** Сервис развернут, PostgreSQL на том же сервере работает, данные сохраняются. Запуск остановился на локальном RPC allowance **59 750 CU** через **29 мин 12 с**, не на месячном account spending limit $15. Ожидаемая активность не вместилась в унаследованный лимит этой попытки; это недостаток оценки плана для наблюдённого burst/recovery, а не доказательство отказа Alchemy. Простое повышение allowance не устраняет риск стоимости.

Эта записка — ненормативный factual receipt, не новая инструкция запуска и не production acceptance.

## Разрешённый и выполненный scope

Владелец разрешил ровно одну ручную ~часовую пробу на Contabo: прежние три pinned PumpSwap pools, применение managed V14/V15, расходы в пределах Alchemy $15. Permanent capture, автозапуск, новая попытка, расширение pools, R1, push и archive не разрешены. Никакой второй попытки не выполнялось; внутренние два reconnect были частью этого же запуска.

- Ubuntu26.04.1, стабильная Java25.0.4.1, PostgreSQL18.6. Отдельный service user, secrets600; ключи в отчёт не включены.
- HEAD `6c5eb0491d7eb7c273066b7dea7a8d7077a8def8`; неизменённый JAR SHA-256 `4d5e4e6bd3d507dfd66af9bdfceec125ee9e1e28101b98111c3df1d8bcb57e15`.
- Existing Main gate:558 tests (294 unit +264 integration),0 failures/errors/skips. Новая executable реализация для deployment не добавлялась; packaged-input integrity PASS291.
- Свежий backup восстановлен в disposable DB, restore exit0. Backup SHA-256 `c81712259bdd857ef9975e6d8c4ab0c64028ed0f0d42489030bd2713c6dcadde`; после проверки только disposable DB удалена. Managed Flyway V1–V15 success; migration-only readiness UP.
- TLS осталось `sslmode=require`: шифрование, но не доказанный hostname-authenticated `verify-full`.
- Session `a7f5fa35-5460-4305-825a-c5e375c8cd6d`, configuration `capture-provenance-v5`, manual mode; continuous disabled. UTC configured start `2026-10-11T11:08:56.091468Z`, end `12:08:56.091468Z`; terminal `11:38:08.251395Z`, interval1752.159927s. Это configured capture interval с reconnect/recovery, не непрерывный clean LIVE.

## Что сохранено и что неизвестно

| Проверка | Фактический результат |
|---|---|
| Raw транзакции | 1166 уникальных:224 successful,942 failed,0 unclassified meta |
| Версии raw TX | legacy6, v0 61, v1 1099 |
| Dispositions | 1323 уникальных подписей:1166 retained raw и157 PENDING без подтверждённого raw |
| Дубликаты / конфликты | 302 повторных transaction observations,0 conflicts; states duplicates0 |
| Состояния | 808; observed slots455629416–455637376 |
| Recovery | 2 attempts/2 reconnect successes;6 history pages,262 newly durable,108 recovery duplicates |
| Coverage | 6 unresolved transaction intervals +9 unresolved state intervals;3 transaction intervals отмечены recovered |
| Integrity сохранённого | 0 raw TX link/digest errors,0 state link/digest errors,0 envelope digest errors |
| Requests / трафик | 1658 requests;27 000 620 received bytes, из них3 217 754 WSS bytes |
| Рост | 61 494 485 retained evidence bytes; raw DB measurement вырос с61 421 247 до96 966 335 bytes (+35 545 088) |

Неполные интервалы — не количество доказанно потерянных транзакций. Два WS close1011 (`11:09:23.268475Z`, `11:31:51.710443Z`) имеют **cause unresolved**: provider/client источник ошибки не доказан. Первый initial boundary не доказан; recovery не превращает всё окно в gap-free историю.

Manual collector сохранил raw, но автоматически не публиковал V14 decoded-operation batches. Эти числа не являются decoder quality, normalized admission, API operator-report invocation или готовностью сигналов.

## База и остановка

8911 измерений смешанной durable writer работы: p50 **3,877 ms**, p95 **11,619 ms**, max **770,959 ms** (nearest rank). Максимальная mailbox601/1000 items и2 112 068/16 000 000 bytes. Это не latency отдельного SQL INSERT и не benchmark максимальной пропускной способности БД; прежняя задержка ~5с не воспроизведена как типичная запись в этой пробе.

Terminal reason `CAPTURE_RESOURCE_LIMIT`, RPC counter5 975 000 hundredths CU. После terminal оператор остановил JVM: `MainPID=0`, listener18080 отсутствует, `NRestarts=0`, unit static/not enabled. `ExecMainStatus=143` после SIGTERM честно записан как операторская остановка, не clean-zero exit и не доказанный crash. Оставшегося owned collector нет.

## Стоимость: оценка, не invoice

Локально188 460,16CU ≈ **$0,098941584**, включая некалиброванную оценку WSS bytes. Фактический dashboard/invoice за эту пробу **UNKNOWN**, отсутствие наблюдённого charge не равно нулю.

Условное продолжение ровно этого mixed partial burst/recovery темпа на30 дней даёт278,79M CU / **$146,37**, с двукратным запасом **$292,73**. Это не репрезентативный monthly прогноз clean LIVE и не фактический счёт. Но использовать прежнее ~$2,75/month или обещать fit в $15 по текущим данным нельзя. WSS pricing model не откалибрована: в предыдущем6h тесте локальные ~47k CU превышали owner-reported месячный прирост (22,2k total минус362 baseline) более чем вдвое; причина расхождения не доказана.

Account-period reservations сохранены монотонно:673 694 592 hundredths CU, без refund/reset. Это консервативное резервирование полномочий, не actual usage/billing. Новая попытка зарезервировала37 087 304 hundredths CU (~$0,194708346 maximum); monthly ceiling2 857 142 800 hundredths соответствует $15 при принятой ставке $0,525/M CU.

Остаток ledger позволяет58 следующих allocations такого же размера, не бесконечную continuous работу; [reservePeriod](../../src/main/java/io/cryptoresearch/marketdata/infrastructure/persistence/JdbcCaptureStore.java) увеличивает reservation без refund, а [continuous ledger](../../src/main/java/io/cryptoresearch/marketdata/application/ContinuousCaptureLedgerUseCase.java) резервирует новые segments отдельно. Для будущего activation требуется спланировать конечное распределение budget либо отдельно принять reconciliation policy. Refund/reconciliation сейчас не реализованы и этой пробой не добавлялись; это ограничение существующей модели, не новый установленный дефект.

## Evidence

Внешний неизменяемый evidence root: `C:\crypto-research-evidence\contabo-one-hour-20261011`. Основные файлы:

- `prelaunch-receipt.md`, `prelaunch-review.md`, `gate-provenance.json`, `jar-content-integrity.log` — проверенное состояние до provider calls.
- `backup-java-staging.log/.exit`, `migration-readiness-stop.log/.exit` — backup/Java/migration/stop; exits0.
- `terminal-measurement.json` — фактическая итоговая сводка; SHA-256 `279819d4ae83c0bc296fb2a0a5131516b1d372e81e857655dff928523e7edbb2`.
- `terminal-read-only-audit.log/.exit` — DB counters, classifications, integrity, account reservations и process state; SHA-256 log `7023f649d7a9d9cb8ed91d82118f77a4d50a69f4f3a4689bd8867c0432dc0d5a`.
- `terminal-journal.log/.exit` — writer timing и diagnostics; SHA-256 log `4c5be854c1f986588c606aa1103a34bd2a4e8da815e62f8b126767440c28e0cb`.
- `capture-configuration.json` — фактически использованный immutable configuration; SHA-256 `ade7a96023ae7d7eaf73a51c714f48e9cbc0a2bfcd9a9c0e4d15e304747a66a5`.
- `owned-process-stop.log/.exit`, `final-time-resource-check.log/.exit` — отсутствие owned process и свободное место после остановки.

Старые raw/receipts, LOCAL_ONLY evidence, owner work и R1 PAUSED не изменены. Полный час, actual billing, полная coverage и экономичность постоянного сбора не доказаны; старые active changes не архивированы этой пробой.

**Следующее разрешённое действие:** бесплатный offline-разбор стоимости по сохранённым данным: отдельно RPC/history/recovery и модель WSS, без новых запросов или запуска. Следующий capture или permanent collection — только по отдельному решению после сверки billing и пересмотра стоимости/лимитов, без автоматического увеличения scope.

## Однократная read-only выгрузка и локальная диагностика 12 октября

После прямого отдельного разрешения владельца выполнена ровно одна managed READ ONLY REPEATABLE READ транзакция: только session `a7f5fa35-5460-4305-825a-c5e375c8cd6d` и exact-linked raw/dispositions/gaps.1166 уникальных TX =224 successful +942 failed совпали с аудитом; хеши, admission links и исходные clocks проверены независимо. Соединение закрыто,0 writes/migrations/Alchemy calls/collector starts/retries/push. Новый внешний root: `C:/crypto-research-evidence/saved-contabo-a7f5-20261012-27954a6225584c1e87e94b5cb3aa80da`; `saved-input.json` SHA256 `0e4ca5add9855ed5808c452edd343e4138e93a2cecc717c754324f218d3160a9`,46092405bytes. Старый immutable root/receipts, runtime authority и исходные данные не менялись.

Тот же decoder/candles/native-SOL diagnostic выполнен в disposable PostgreSQL18.6/V1–V15, без managed profile и provider I/O. Все1166 источников классифицированы в10 disjoint <=128-TX chunks:57 DECODED/942 FAILED/153 UNSUPPORTED/3 NON_OPERATION/11 NO_PUMPSWAP;74 операции всего,57 операций в трёх configured pools и17 в прочих пулах внутри этих транзакций (не новые subscriptions).57/224 successful TX fully decoded —25,45%, не доказательство majority coverage. Причины153 UNSUPPORTED:114 UNSUPPORTED_VARIANT,19 INVOCATION_LAYOUT,11 ATA_CREATION_UNPROVED,6 IMMUTABLE_TOUCH_UNPROVED,2 TOKEN_ENDPOINT_UNPROVED,1 IMMUTABLE_CLOSE_UNPROVED. Не каждый unknown вариант обязательно является свапом; исправление алгоритма этим запуском не выполнялось.

Старое6h окно `[2026-10-10T05:50Z,11:50Z)` сравнивается с partial29m12s серверной сессией; новые fixed300s пары берутся только внутри полного event-интервала `[2026-10-11T11:10Z,11:35Z)`. Это разные длительности/coverage, не сравнение темпа или стратегии. Все successful и failed pool-bound TX сохраняются в каждом pair input. Новые observed bars — уникальные bucket identities из вычисленных полных pair reports, не объединённый candle report всей сессии.

| Пул |6h: операции / свечи|Contabo: операции пула|Свечи в вычисленных новых окнах|6h: TRIGGER / NO_TRIGGER / INSUFFICIENT|Новые: TRIGGER / NO_TRIGGER / INSUFFICIENT|Не вычислены новые пары|
|---|---:|---:|---:|---:|---:|---:|
|GTA — `5iSVs31h…`|38 /22|53|2|0 /11 /60|0 /1 /1|2|
|RTM — `EBNUexqp…`|5 /4|2|1|0 /0 /71|0 /0 /4|0|
|Pumpswap — `FDtu6W24…`|4 /4|2|1|0 /0 /71|0 /0 /4|0|

Два поздних GTA pair input содержат721/1137 TX: целиком `UNAVAILABLE_LIMIT` при неизменном128-TX API, без обрезки/слияния.10/12 вычисленных пар дали0 TRIGGER/1 NO_TRIGGER/9 INSUFFICIENT; отсутствие соседней наблюдённой свечи не доказывает отсутствие торгов. Один NO_TRIGGER относится только к распознанным observed endpoints;153 unsupported TX и явные gaps запрещают вывод о полном рынке/преимуществе. K2030 ретроспективен; local publication clocks фактические, не backdated. Raw/clocks неизменны; diagnostic reads не пишут normalized/accepted-signal/evaluation rows.157 PENDING,6 unresolved transaction и9 unresolved state интервалов сохранены, unseen loss UNKNOWN; не «15 потерянных транзакций». `GAP_COVERAGE_NOT_BOUND` остаётся явным.

Таблица сравнивает functional outputs API, не рыночную активность. Два ограничения действуют одновременно:153 unsupported successful TX и невозможность проверить наиболее плотные GTA окна. Полнота свечей и надёжность open/close UNVERIFIED; гипотеза «пулы слишком тихие» не подтверждена/не опровергнута.57/224 — доля полностью декодированных successful транзакций, не доказанная доля сделок. Перед выводами о сигнале нужны и покрытие декодера, и полный масштабируемый candle input. Дополнительное обсуждение с Claude по приведённым цифрам (без чтения кода/артефактов) подтвердило эту интерпретацию; оно не заменяет native independent review и не разрешает новую реализацию. Следующий офлайн-разбор сначала группирует114 случаев по discriminator/outer program и принадлежности к configured pools, без отбора по цене/исходам.

Fresh independent Reviewer APPROVE подтверждает execution/integrity/table, не admission/готовность сигналов. Scoped tests154 unit +23 PG,0 failures/errors/skips,exit0;8 exporter safety assertions и5 финальных harness assertions PASS; `qualification-run-02.exit=0`. Внешняя обвязка сначала упала до Docker на ошибочном равенстве raw и notification clocks; meaningful real-clock RED→GREEN исправил только это предположение, сохранив обе пары clocks. `tests_changed_after_red=true` для добавленных exact-preservation assertions. Первый error/log/exit сохранён; exact старый source snapshot не сохранён, его recorded SHA и differently hashed copy честно разделены. Код продукта/старые тесты/receipts не менялись. Полный Maven gate не заявляется: repository Java/config/integration inputs не изменены.

Полные локальные receipts: [comparison](C:/crypto-research-evidence/saved-contabo-a7f5-20261012-27954a6225584c1e87e94b5cb3aa80da/comparison.md) и [qualification summary](C:/crypto-research-evidence/saved-contabo-a7f5-20261012-27954a6225584c1e87e94b5cb3aa80da/qualification-run-02/qualification-summary.json), SHA256 summary `b5d489494d26623020b831ec8b8030a92270cf95a8af3ad2cf5b26b1922313b6`. Старый baseline SHA256 `20b8264934edfb6026571961f191cb34a21ca0a66040cfba21bf49c779dcf255` неизменен.

**Следующее разрешённое действие:** bounded offline-диагностика114 неизвестных вариантов по сохранённому raw, без нового доступа/расходов; изменение декодера и масштабируемый многопакетный candle path требуют собственного bounded implementation contract/review. Повторное чтение серверной БД, новый сбор, тариф/лимиты, push/archive и R1 этим результатом не разрешены.
