# Contabo: бесплатный разбор стоимости 11 октября 2026

## Итог

Диагностика завершена без новых provider calls, расходов, capture или записи в БД. В [29-минутной пробе](CONTABO_BOUNDED_CAPTURE_2026-10-11.md) расход создаёт прежде всего **поштучная загрузка транзакций**, включая повторные загрузки уже известных подписей. База не является доказанной причиной остановки: остановил RPC allowance59 750CU.

Дополнительно обнаружено: официальная **Solana-specific** WSS ставка **0,0002CU/byte**, в200 раз ниже сохранённой общей0,04. Старая authority и её локальный расчёт остаются неизменными. Фактический счёт Alchemy всё ещё **UNKNOWN**; новые числа ниже — documentary scenarios, не dashboard verification.

## Из чего сложился расход

Сессия `a7f5fa35-5460-4305-825a-c5e375c8cd6d`, interval1752.159927s. В retained HTTP envelopes нет null/error results.

| Метод / источник | Сохранённые ответы | Received payload bytes | Модель CU |
|---|---:|---:|---:|
| `getTransaction` | 1468 | 15 317 717 | 58 720 (40/ответ) |
| `getAccountInfo` | 39 | 23 606 | 390 (10/ответ) |
| `getTransactionsForAddress` | 6 | 8 441 543 | 600 (100/ответ) |
| RPC allowance без сохранённого HTTP ответа | не установлен | не установлены | 40 |
| WSS counter | 2084 retained envelopes | 3 217 754 counter bytes | 643,5508 при0,0002/byte |

HTTP receipts объясняют59 710CU; ledger —59 750CU. Дополнительные40CU означают учтённый allowance без retained HTTP result: отправка, фактическое списание и причина не установлены. Нельзя считать его доказанной оплаченной транзакцией.

WSS retained envelopes занимают3 217 609bytes:39 ACK/control envelopes1 638bytes,769 account notifications440 132bytes,917 failed logs554 868bytes,359 successful logs2 220 971bytes. Отличие145bytes от counter не атрибутировано. Не смешиваем WS messages, HTTP responses, уникальные TX и dispositions.

Из1468 TX responses1206 failed (10 516 894bytes) и262 successful (4 800 823bytes). Это942/224 уникальных TX;264/38 повторных responses соответственно. Полный failed raw сохраняется по текущему контракту: `err` из WS notification не равноценен полному raw и не заменяет его.

## Новая документальная ставка — не автоматическое изменение конфигурации

[Alchemy Compute Unit Costs](https://www.alchemy.com/docs/reference/compute-unit-costs), проверено11 октября2026: раздел **Solana: WebSocket Subscriptions** указывает0,0002CU/byte; generic **Webhooks and Subscription APIs** отдельно указывает0,04. На этой же странице Solana RPC: `getTransaction`40CU, `getAccountInfo`10CU, `getTransactionsForAddress`100CU.

Сохранённая модель дала188 460,16CU/$0,098941584. При Solana-specific ставке: WSS643,5508CU +RPC59 750 = **60 393,5508CU/$0,03170661417** по принятой денежной ставке$0,525/M CU. Это не invoice; исключения тарификации ошибок и реальные billable bytes отдельно не измерены.

Цена не может быть исправлена одной строкой: [CaptureBudget.subscriptionHundredths](../../src/main/java/io/cryptoresearch/marketdata/application/CaptureBudget.java) использует `longValueExact`;7 778 076bytes×0,0002×100 =155 561,52 дробных hundredths, что вызовет исключение. Будущая версия требует явного консервативного округления/представления и регрессий. [Period authority](../../src/main/java/io/cryptoresearch/marketdata/infrastructure/persistence/JdbcCaptureStore.java) включает coefficient и проверяется на равенство: октябрьскую authority нельзя молча заменить, переименовать для обнуления budget или переписать старую evidence.

## Повторы и повторное использование full history

[Collector.transaction](../../src/main/java/io/cryptoresearch/marketdata/application/PumpSwapPoolCollector.java) делает `getTransaction` после pending write каждый раз. Проверка существующего raw выполняется позже в [CaptureEvidenceUseCase](../../src/main/java/io/cryptoresearch/marketdata/application/CaptureEvidenceUseCase.java): она предотвращает дубликаты хранения, но уже не экономит RPC.

Recovery запрашивает `transactionDetails=full`, затем поштучно fetches каждую строку до boundary. `successfulPage` используется для счётчика duplicates, а не пропуска fetch. Accounts читаются один раз на connection cycle с внутрикруговой дедупликацией:13 уникальных accounts×3 cycles =39 reads. Это linkage/current snapshots, не периодический polling.

- 1468 TX responses относятся к1166 уникальным подписям;302 подписи имеют по2 responses. Это потенциальная dedup opportunity, не доказательство уже реализованной безопасной экономии.
- Все600 history rows содержат slot/version/accountKeys/instructions/meta.err/fee/pre/post SOL и token balances/innerInstructions/loadedAddresses. У127 есть LUT;4757/4757 inner instructions имеют stackHeight.
- 407 history rows совпадают с separately fetched TX по полному **семантическому JSON**. Byte-for-byte равенство не доказано; decoder PASS всех600 строк этим не установлен.
- 709 TX responses относятся к этим407 подписям, но нельзя считать все709 лишними: часть history появилась позже первого fetch.
- Temporal union: **262 первых responses после matching history receipt +302 повторных responses =564 responses**,22 560 modeled CU и5 698 954bytes. Остальные904 первых responses не предварялись такой history receipt, включая145 подписей, позже попавших в history.

Temporal SQL сравнивает `history.received_at < getTransaction.response.received_at`, не инструментированный request-start. Serial recovery code дополнительно подтверждает page-before-refetch в своём пути.564 — observed потенциальная reuse opportunity, не гарантированная saving будущей реализации. Нельзя суммировать302 повтора и709 history-covered responses: это двойной счёт.

Raw storage immutable по содержимому: full history row и getTransaction result могут иметь разную точную сериализацию. Будущий reuse сохраняет page/row provenance и старые payloads/fingerprints, проверяет identity/finality/content conflicts и decoder inputs; нельзя ретроактивно канонизировать или перезаписать raw ради совпадения.

## Что это означает для $15

| Условное продолжение наблюдённого темпа на30 дней | CU за это partial окно | $/30 дней | С запасом×2 |
|---|---:|---:|---:|
| Сохранённая модель0,04CU/byte | 188 460,16 | 146,37 | 292,73 |
| Документальная Solana WSS ставка | 60 393,5508 | 46,90 | 93,81 |
| Та же ставка, потенциальный dedup302 responses | 48 313,5508 | 37,52 | 75,04 |
| Та же ставка, потенциальная union564 responses | 37 833,5508 | 29,38 | 58,77 |

Это extrapolation mixed startup/burst/reconnect/recovery окна, **не clean LIVE, не фактический счёт и не representative monthly forecast**. Даже RPC без WSS даёт условные$46,40/month. Исправление коэффициента или dedup отдельно не доказывает fit в $15. Повышение RPC cap не является исправлением стоимости.

## Минимальный следующий scope — предложение, не реализация

### Последующий факт кабинета от владельца

11 октября 2026 владелец сообщил: **82,6K / 28,6M CU**, Remaining **28,5M**, Peak **80,3 / 10K CU/s**, Apps **1/30**, Webhooks **0/100**. Это `OWNER_REPORTED`: точное время снимка и полнота его billing attribution не установлены. Прежние **22,2K CU** были сообщены 10 октября, а не непосредственно перед Contabo-пробой. Разница округлённых показаний составляет примерно **60,4K CU**, близко к documentary scenario **60 393,5508 CU**, но не доказывает тариф, billable bytes или расход именно одной сессии: между показаниями могла быть другая активность. Историческая authority и reservations не изменены; новые provider calls или запуск этим сообщением не разрешены.

Main рекомендует ограниченный repair существующего пути: сохранить WSS, состояния и полный failed raw, без смены провайдера. Потенциальный эффект доказывается qualification; fit в $15 заранее не обещается.

1. Trusted pre-fetch dedup: существующие raw digest/slot/provider/disposition проверяются до network call; новые notifications/page receipts сохраняются, unsupported/pending/conflict не объявляются completed.
2. Повторное использование уже полученных полных history rows с versioned provenance, проверкой decoder inputs/byte-contract и индивидуальным fallback только при необходимости. WSS и account-state coverage не заменять polling; failed raw не убирать. Batch загрузка ещё не полученных LIVE rows — отдельное уточнение будущего плана, не выполненная оптимизация.
3. Versioned pricing с консервативной fractional-hundredths арифметикой и совместимостью прежней period authority/monotonic reservations. Никакого reset/refund старого budget. Семантические изменения требуют точного active OpenSpec scope, meaningful tests и независимого review, а не одобрения этой диагностикой.

**Какое предположение реализации или контракта может быть неверным?** Что history содержит вовремя ровно нужные finalized TX и byte-compatible raw, а значит564 fetch можно безопасно исключить. Показаны только текущие семантические совпадения/поля/receipt timing; index latency, future layouts, boundary/order, immutable conflicts и fallback остаются предметом qualification. Claude независимо обсудил pricing/failed-raw/rounding assumptions; его согласие не заменяет review будущего executable изменения.

Сценарий$29,38 использует только6 уже полученных страниц. Потенциальная экономия proactive batching не оценена; это не доказательство ни fit в $15, ни невозможности уложиться после иной организации загрузки.

### Последующее offline-исправление

11 октября `capture-rpc-reuse-v6` реализован в [существующем change](../../openspec/changes/add-bounded-pumpswap-pool-collector/design.md#offline-rpc-reuse-and-fractional-budget-repair-plan-2026-10-11): trusted same-session dedup, qualified уже полученные history rows и cumulative fractional accounting. Fresh независимый Reviewer дал APPROVE после исправления противоречивого JSON-RPC proof; targeted GREEN — 77 tests/0 failures/errors/skips. Main complete gate PASS: 587 tests (303 unit + 284 PostgreSQL), 0 failures/errors/skips, 18:36; integrity/Docker/strict-all/doctor/working + scoped-cached diff exit0; commit `e9edacb2b96656c40abbe905e7bbb0daebc51a81`, без push. Повторилась известная 30s forced test-JVM shutdown: graceful завершение всех тестовых контекстов не доказано, причина нестабильности продукта из этого не следует. Это не measured savings/$15-fit: proactive LIVE batching не реализован, ставка октября и старые reservations неизменны, нового запуска нет.

## Evidence и проверки

Root `C:\crypto-research-evidence\contabo-cost-breakdown-20261011`: `diagnostic-summary.json`, `cost-breakdown-v2.log`, `history-payload-coverage.log`, `overlap.log`, `temporal-union-v2.log`, соответствующие SQL/exit receipts, Claude review/consensus. Sealed manifest27 files SHA-256 `e6ec41b52ab9d41c6a557f706401c009b93cb45f4fe1f663263b7df5ad197987`; temporal-v2 log SHA-256 `6c7edf78fbbe17e61e52d19eb08dc6cd3214bcd1207ab745dcdd42938efb768f`.

Successful read-only queries завершаются COMMIT/exit0. Две неудачные версии сохранены как **FAIL**: original cost query ORDER BY JSON error; original temporal query statement timeout, несмотря на wrapper exit0. Оптимизированные v2 receipts отдельно PASS; неуспешные проверки не объявлены зелёными. Initial scp stall также записан, owned transport children остановлены; SQL выполнен native SSH, capture не возобновлялся. Финально MainPID0/NRestarts0. Original evidence, receipts и authority не изменены.

Для этой factual записки: факты/арифметика/ссылки/хеши/diff checks; documentation-only RED_NOT_REQUIRED, полный Maven не требуется. Runtime/code/тесты/тариф authority здесь не изменены.

**Следующее разрешённое действие:** короткий offline OpenSpec-план минимального repair scope выше с квалификацией raw/history и pricing compatibility. Новых capture, provider calls, расходов, покупки, смены провайдера или включения permanent collection эта записка не разрешает.
