# Источник данных для первой интеграции

**Дата:** 7 октября 2026 года. Решение владельца: Alchemy PAYG — основной источник для первой разработки; Helius Free — дополнительный. Покупок и новых платных вызовов не разрешено. Operational acceptance и production-primary пока не доказаны.

R1-E2/D1 — **PAUSED**. Фактический [D1 INSUFFICIENT](R1_E2_D1_COMPLETION_RESULT_2026-10-07.md), commit `e386884`, не меняется; H1/H2 INCONCLUSIVE/data insufficient, не проверялись. Calibration `1.1.0`/P1 отложены до перезапуска владельцем. Frozen protocol, raw, census, receipts и unchecked tasks сохраняются; дальнейшие Helius R1 credits не выделены.

Порядок: ограниченные PumpSwap pools, живые finalized tx/state subscriptions → PostgreSQL raw → gaps/quality → сигналы/evaluation → advisory UI без торговли. [Первый change](../../openspec/changes/add-bounded-pumpswap-pool-collector/proposal.md) планирует только collector. После 2–4 недель накопления данных возможен предварительный анализ; это не срок готовности продукта или доказательство edge. May layouts, Token2022, self-CPI, ATA lifecycle и комиссии остаются unsupported/UNKNOWN до отдельной проверки.

Официальные опубликованные условия, проверенные 2026-10-07:

- Alchemy [full address history](https://www.alchemy.com/docs/chains/solana/solana-api-endpoints/get-transactions-for-address): до 100 TX/request, 100 CU/request; [getTransaction](https://www.alchemy.com/docs/chains/solana/solana-api-endpoints/get-transaction): 40 CU. [Account Archive](https://www.alchemy.com/docs/solana/account-archive) описывает historical state, но прежняя B-проба INCONCLUSIVE не доказывает полного покрытия.
- [PAYG pricing](https://www.alchemy.com/pricing): $0.525/млн CU, опубликованные 300 RPS/10,000 CU/s; gRPC от $75/TB. [WebSocket](https://www.alchemy.com/docs/reference/solana-subscription-api-endpoints) тарифицируется по payload, [прочие subscriptions](https://www.alchemy.com/docs/reference/compute-unit-costs) — 0.04 CU/byte ($21/decimal GB). Это разные расходы, не измеренный счёт аккаунта.
- [Helius plans](https://www.helius.dev/docs/billing/plans): Developer $49, 10M credits, 50 RPC/s, enhanced WSS; mainnet gRPC только Business $499, не планируется. [Parsed Events](https://www.helius.dev/docs/parsed-events) сменяет legacy Enhanced, доступен FREE по 10 credits/request, поддерживает Token2022 transfers; полнота PumpSwap экономики этим не доказана.

При P pools, t TX/pool/day, H history pages/day и B WSS bytes/day: RPC≈P×t+H+control; CU≈40×P×t+100×H+0.04×B+control. t/B/control неизвестны. Условные 1,000 history pages/day = $1.575/30 дней; 1GB WSS/day = $630/30 дней. Отдельно gRPC 1GB/day = $2.25/30 дней; $100/month ≈44.44GB/day до RPC/storage. Это сценарии, не достаточность бюджета. Начальный proposal: до 3 pools, рост после замера. При дорогом WSS рассмотреть отдельное RPC polling решение с явными потерями freshness/state; транспорт молча не менять.

Account cap/alerts **UNVERIFIED**: доступа/evidence нет. [Account spending cap](https://www.alchemy.com/docs/how-to-set-usage-limits-and-alerts-for-your-account) не защищает gRPC. До платного запуска нужны sanitized фактические настройки/права хранения, новый конечный бюджет и client byte/time/cost stops. Прежние $3/15GB израсходованы одной попыткой, нового разрешения нет. Operations обновляется с проверенной реализацией; здесь будущие условия, не выполненная настройка.
