# kernel

- **Responsibility:** only stable, chain-aware identities and value concepts that genuinely cross module contracts.
- **Owned data:** none. `kernel` never owns a PostgreSQL schema, table or repository.
- **Public API:** candidates include `ChainId`, `AssetId`, `WalletAddress`, `TransactionHash` and `BlockReference`; exact types are defined by a dedicated kernel change.
- **Allowed dependencies:** none.
- **Published/consumed events:** none defined.
- **Invariants:** immutable by default; no Spring, persistence, provider or execution-framework types in public/domain values; identity remains chain-aware.
- **Non-goals:** business processes, orchestration, persistence, HTTP/provider integration, shared DTO collections, base services, generic repositories, validation dumping grounds and utilities.
- **Main tests:** value invariants when types are introduced; module boundary verification.
