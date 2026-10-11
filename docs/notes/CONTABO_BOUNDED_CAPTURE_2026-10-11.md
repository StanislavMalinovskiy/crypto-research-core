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
