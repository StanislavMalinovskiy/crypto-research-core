# kernel

- **Responsibility:** only stable, network-aware identities and value concepts that genuinely cross module contracts.
- **Owned data:** none. `kernel` never owns a PostgreSQL schema, table or repository.
- **Public API:** immutable `ChainId`, `AssetId`, `WalletAddress`, `TransactionId`, `EventId` and `BlockPosition`; `ChainId.SOLANA_MAINNET` is the customary CAIP-2 identifier `solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp`.
- **Allowed dependencies:** none.
- **Published/consumed events:** none defined.
- **Invariants:** immutable; chain is never null and preserves exact case-sensitive customary CAIP-2 network identity; asset, wallet and transaction values remain opaque and exact; an event is scoped to a transaction and its canonical locator is stable across providers/parser versions; changing persisted locator grammar requires an approved change and forward migration; block position is non-negative and distinct from optional block hash/height evidence; natural ordering is a deterministic technical tie-break; no Spring, persistence, provider or execution-framework types occur in public values.
- **Non-goals:** business processes, orchestration, persistence, HTTP/provider integration, shared DTO collections, base services, generic repositories, validation dumping grounds and utilities.
- **Main tests:** CAIP-2 construction/validation and reference case sensitivity, Solana/Base network and category distinction, event scoping/locator access, numeric block-position ordering, deterministic identity ordering and module boundary verification.
