# Legacy v5 architecture mapping

> **Archive notice:** this document preserves a superseded multi-module mapping for historical context only. It has no normative force and must not be used to derive current package, Maven module or dependency boundaries. See [Architecture](../ARCHITECTURE.md) for the current eight-module Spring Modulith design.

## Старые слои A/B/C и план v5

| Старый слой | Что значил раньше | Что это было в v5 | Какие модули предлагались в v5 | Какие сервисы / провайдеры обсуждались |
| --- | --- | --- | --- | --- |
| **A** | Базовые блокчейн-данные, ingest, raw stream, RPC, WebSocket, история транзакций | **Data Layer / Ingest Layer** | `provider-api`, `provider-helius`, `provider-bitquery`, `ingest-solana`, `persistence`, часть `app` | **Helius**, **Bitquery**, позже для EVM: **Alchemy / Chainstack / Moralis** |
| **B** | Обогащение: цены, ликвидность, пары, holders, token metadata, parsed/enriched data | **Token Intelligence + Token Risk facts** | `token-intelligence`, `token-risk`, часть `provider-dexscreener`, `provider-birdeye`, `provider-goplus` | **DexScreener**, **Birdeye**, **GoPlus**, часть **Helius DAS/metadata**, иногда **CoinGecko** |
| **C** | Labels, smart money analytics, attribution, high-level interpretation | **Wallet Intelligence + Strategy + Decision Layer** | `wallet-intelligence`, `wallet-clustering`, `strategy-api`, `strategy-plugins`, `signal-aggregation`, `risk`, `capital`, `execution-simulator`, `paper-trading`, `venue-gate`, `research` | **Arkham**, **Nansen** как enrichment, плюс собственная логика scoring / intent / reasoning / capital rules |

The current design replaces these technical Maven modules with package-based vertical modules: `kernel`, `governance`, `marketdata`, `risk`, `wallet`, `strategy`, `measurement` and `research`.
