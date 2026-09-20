# Design

## Context

The first slice records a synthetic `recorded-swap-v1` fixture whose payload already contains enrichment (`priceUsd`, `liquidityUsd`) that a raw Solana transaction does not carry. Two external audits confirmed the resulting gaps: no finality/reorg policy, no Solana locator grammar, no availability-time model, no universe rules, no derivation separation, no quality policy, an undersized historical envelope and no capacity model. This design defines the contract that closes those gaps before any mass real-data recording. It implements remediation package F1 of `docs/DELIVERY_PLAN_FIXES.md` and produces no code.

Decisions below marked **[Control-decided]** were delegated to Control by the owner on 2026-09-20 and are explicitly listed for the owner's final audit.

## Goals / Non-Goals

Goals: define every rule a real Solana implementation must satisfy (finality, identity, time, universe, derivation, quality, ranges, capacity, selection); record a doc-verified provider matrix; deliver a ready-to-run spike protocol.

Non-Goals (design-level): no adapter architecture (F3 design), no physical schema (F2 design), no statistical protocol parameters (F5), no choice of provider (owner decision after spikes).

## Decisions

### D1. Finality and reorg policy — finalized-only stable admission **[Control-decided]**

Stable domain storage admits only evidence in `finalized` slots. Rationale: the research horizons are 1h/4h/24h, so finalized latency (expected tens of seconds; must be measured in spike S1) is immaterial to signal validity, while provisional admission would require rollback/canonicality machinery that V1 storage explicitly excludes. Alternatives rejected: `confirmed` admission (reorg risk without research benefit) and a provisional transport journal (a separate future change if a latency-critical family ever justifies it). Consequence: `observedAt`-based freshness budgets must account for finalized propagation, and reconnect gap recovery can rely on slot-stable replay.

### D2. Solana event locator grammar and raw granularity

The canonical locator is derived from the transaction's complete instruction structure:

- outer instruction: `i:<outerIndex>` — 0-based index in `message.instructions`;
- inner (CPI) instruction: `i:<outerIndex>/<innerIndex>` — `innerIndex` is the 0-based index in the complete `meta.innerInstructions` list for that outer instruction, never a filtered or provider-positioned index;
- multi-leg swaps split by a parser from one instruction append `:leg<n>` with a stable 1-based ordinal.

Raw granularity: one full provider transaction payload is stored once per `(chain, transaction, provider)`; individual raw observations reference the transaction payload and carry the locator. Reparse or parser-version changes produce new transformation lineage, never new event identity; changing this grammar requires an approved forward migration. This mirrors the chain-identity spec's EVM rule (complete `receipt.logs` ordinal) for Solana.

### D3. Time and availability model

Recorded as distinct facts: `chainSlotTime` (block time), `providerEventTime` (payload field, lineage only), `providerVisibleTime` (when the provider can report it, if known), `receivedAt` (trusted application UTC clock at the adapter boundary — the authoritative live observation time), `admittedAt` (finalized-admission moment), `ingestedAt` (durable write), and `modeledAvailabilityAt` (versioned model used for backfill and replay). Skew policy: when `|receivedAt − chainSlotTime|` exceeds 60 seconds **[Control-decided: threshold; initial value ≈ two slot times plus propagation margin, revisitable with S1 latency measurements]**, the observation carries a quality flag; provider timestamps never silently become system observation time.

### D4. Point-in-time token universe **[Control-decided: mechanism]**

Universe membership is discovered from watched program events, not from provider search of current survivors: initial watched set is the Pump.fun bonding-curve program, PumpSwap and Raydium AMM v4 (final list confirmed by spike S2 field verification). A token is included at the first observed eligibility event (pool/curve creation or first swap on a watched venue) with discovery source, rule version and inclusion time recorded; exclusions are recorded with time and reason. No liquidity, market-cap or survival floor applies to membership — analysis filters belong to the research protocol, not the universe. Universe snapshots are versioned and fingerprinted like other immutable datasets.

### D5. Derivation separation per venue

Stored as separate facts with own source/time/quality/confidence: raw transaction evidence; chain-native swap quantities (raw integer units); token decimals (from transaction `preTokenBalances`/`postTokenBalances` when present, else mint account read recorded as separate evidence); native price observation (derived from balance deltas on the venue's pricing path); USD conversion fact (token→SOL price from the venue path times a separately recorded SOL/USD observation from a designated SOL/USDC pool — never a constant); pool/venue liquidity observation (pool account state at slot when observable). Venue specifics:

- Pump.fun bonding curve: buy/sell against the curve; price from SOL/token balance deltas; liquidity = curve SOL and token reserves valued via the same observation.
- PumpSwap: CPMM pools; price and liquidity from pool reserves.
- Raydium AMM v4: same approach; CLMM pools are recorded with partial-quality liquidity until a later change defines concentrated-range handling.

Historical pool state at an arbitrary slot is not available from plain `getAccountInfo`; the matrix therefore treats at-slot state queries (documented by Alchemy) and subscription-captured account states as spike questions (S3). Missing inputs produce explicit quality status, never fabricated values.

### D6. Price and liquidity quality policy v1 **[Control-decided: thresholds]**

Minimum admissible notional for a signal/evaluation-grade price observation: USD 100. Single-swap observations are never automatically executable prices. Suspicious-pattern handling in v1: self-trade (same wallet on both sides) marks the observation non-admissible; deviation greater than 10x from the previous admissible observation within the same venue flags the observation for degraded confidence; cross-source disagreement greater than 20% (when two sources exist) degrades confidence. Wash-trade classifiers beyond self-trade detection are deferred; raw facts needed by future classifiers are recorded now. Pool-depth admissibility rules are deferred until S3 evidence shows what depth is historically observable, and are then versioned into the policy. Thresholds are initial conservative values for memecoin data (USD 100 excludes dust; 10x/20% flag extreme deviation and disagreement), versioned with the policy and revisitable with S2/F5 evidence without silent changes.

### D7. Historical research ranges **[Control-decided: envelope]**

Target envelope: 180 days = 90-day wallet warm-up + 90-day evaluation window. The evaluation window's exploratory/validation split is owned by the F5 research protocol, not by this contract. Documented fallback if provider retention, completeness or cost cannot support 180 days: shorten the wallet lookback to 45 days (135-day envelope) as an explicit, versioned decision — never a silent reduction. Wallet metrics for any signal must be computable entirely from trades completed before the evaluation range begins.

### D8. Capacity envelope and recoverability classes

Planning estimates (to be replaced by measured evidence from S1/S2): watched-program transactions ≈ 1–3M/day; raw payload ≈ 2–5 KB/transaction → 2–15 GB/day; 180-day envelope → 0.4–2.7 TB raw worst case. These numbers justify: (a) F2 must decide payload trimming/compression, batching and partitioning against measured volume before mass recording; (b) full-payload retention for all watched programs may be unaffordable and the spike must measure the real distribution. Recoverability classes: **irreplaceable** — dataset fingerprints, universe snapshots, run manifests, risk/wallet history, outcomes, reports (off-host protection from first appearance); **re-derivable** — normalized facts (recomputable from retained raw evidence plus parser/algorithm versions); **provider-reloadable** — raw evidence (reloadable only with documented provider retention, replay window, cost and terms). Daily full `pg_dump` remains the baseline until either backup time exceeds 30 minutes or database size exceeds 50 GB **[Control-decided: thresholds; initial values sized to keep the daily full dump viable on the current VPS while bounding restore time, revisitable after the first measured backups]**, after which the backup strategy moves to class-selective logical backup plus WAL/PITR with a restore drill at near-projected volume. Per-class RPO/RTO values are declared by the F3.5 deployment guardrails work when operational roles exist.

### D9. Provider-consumer capability matrix (documentation-verified 2026-09-20)

Downstream consumers map to matrix rows as follows (per-consumer quota, billing-unit, quota-reset, overage and usage-telemetry fields are deliberately deferred to S1/S2 evidence: they are tier properties that vendor documentation does not reliably state, and the spike protocol records them per provider — see D10):

| Downstream consumer | Required facts and minimum depth | Matrix rows depended on |
|---|---|---|
| Raw replay and lineage | full transaction payloads with complete instruction structure, ≥180 days | live finalized stream; historical instruction ranges |
| Entry/avoidance outcomes (1h/4h/24h) | admissible price and liquidity observations at decision and horizon times | pool liquidity series; price/liquidity quality policy |
| `LIQUIDITY_SPIKE` | pool liquidity series, near-real-time, freshness-bound baseline | pool liquidity series |
| `HOLDER_GROWTH` | holder count series, ≥4h practical depth, snapshot availability time | holder count series |
| `SMART_WALLET_BUY` / `MULTI_WALLET_BUY` | complete cross-venue swaps over the 90-day wallet lookback | historical instruction ranges; normalized derivation |
| Token risk facts | authorities, concentration, LP state at decision time | risk facts; at-slot account state |
| Token universe | point-in-time pool/curve creation events from watched programs | live stream + historical ranges (watched programs) |

| Capability need | Helius | Alchemy | Triton DM | Chainstack | SQD Portal | Bitquery | DexScreener | GoPlus |
|---|---|---|---|---|---|---|---|---|
| Live finalized tx stream (watched programs) | JSON-RPC/WS on Free; LaserStream gRPC paid only (Free lacks mainnet gRPC/`transactionSubscribe`) | Yellowstone gRPC; 48h replay (marketing) | Yellowstone gRPC; `from_slot` replay within retained window; `SubscribeReplayInfo` | Yellowstone gRPC (docs moved; unverified) | not a live transport | GraphQL streams (verify) | not a transport | not a transport |
| Historical instruction ranges (180d) | historical APIs documented; limits/depth need spike | archival claims; need spike | replay window only (hours) | unverified | arbitrary slot ranges, programId filters, JSONL batch semantics, reorg handling (HTTP 409) | DEX history documented; quirks need spike | no historical feed | n/a |
| At-slot / historical account state | not documented | `getAccountInfo`/`getTokenAccountsByOwnerAtSlot` at any slot (documented, "exclusive") | no | unverified | not offered | balances documented | n/a | n/a |
| Pool liquidity series | not documented as series | derivable via at-slot state | derivable via account subscriptions | unverified | derivable from instruction data only | pair data documented | current snapshots only | n/a |
| Holder count series | token holder APIs documented; reproducibility needs spike | not documented | no | unverified | not offered | holder-oriented queries documented | holders shown (current) | n/a |
| Risk facts (authorities, security) | RPC methods | RPC methods | RPC unary methods | RPC methods (unverified) | not offered | security-oriented queries documented (verify) | n/a | Solana Token Security API documented; fields/TTL/history need spike |
| Free-tier proof value | proves JSON-RPC/WS only | limited free credits (pricing unverified) | requires paid (pricing unverified) | requires paid (unverified) | Public Portal 20 req/10s — usable for sampling | free tier exists (pricing unverified) | free public API | free tier exists (pricing unverified) |

Pricing and tier cells above are planning assumptions as of 2026-09-20 without named pricing sources; the Helius Free limitation (no mainnet LaserStream gRPC, no `transactionSubscribe`) reflects the owner's dated verification in `docs/DELIVERY_PLAN_FIXES.md` and must be re-verified against the Helius pricing page at S1. Every pricing cell is re-verified on the vendor pricing page at spike time before any purchase.

Sources verified 2026-09-20: docs.triton.one (Dragon's Mouth gRPC subscriptions: filters, commitment levels, finalized-ancestor quirk, replay window, ping/pong, excluded programs, Deshred paid beta); alchemy.com/solana (gRPC 48h replay, at-slot state queries, historical token balances); docs.sqd.dev Solana Portal quickstart (arbitrary ranges, instruction filters, batch continuation, reorg semantics, rate limits); helius.dev token APIs and historical data pages (via audit review); docs.bitquery.io, docs.dexscreener.com, docs.gopluslabs.io (via audit review). Chainstack documentation URL was not reachable at verification time and stays unverified until S1/S2. Documentation claims do not substitute for spikes; every matrix cell used in selection must be spike-confirmed on the tier that will be purchased.

### D10. Spike protocol (owner actions; no API keys available to this change)

- **S1 Live transport spike** (Yellowstone-compatible provider, paid tier where the capability requires it): subscribe to watched-program transactions for ≥24h at `finalized`; record connection stability, finalized latency distribution, disconnect/reconnect behavior with `from_slot` replay and duplicate rate, throughput, quota consumption and exhaustion behavior. Exit: measured latency + gap semantics evidence.
- **S2 Historical completeness spike** (SQD Public Portal for sampling, then Dedicated for the full range estimate): pull watched-program instructions for sample slots across the 180-day envelope; verify completeness against chain spot-checks, field availability (inner instructions, balance deltas, token balances), batch-continuation and reorg behavior; estimate full-envelope cost.
- **S3 At-slot state spike** (Alchemy): `getTokenAccountsByOwnerAtSlot`/`getAccountInfo` at slot for sample pool accounts; verify historical liquidity reconstruction feasibility, limits and cost.
- **S4 Risk facts spike** (GoPlus Solana + plain RPC): field availability, provenance, TTL, historical access for risk facts.
- **S5 Holder series spike** (Helius token APIs and/or Bitquery): historical holder-count availability, point-in-time reproducibility, limits and cost.

Each spike produces an evidence note (date, tier, commands, measured numbers) stored under `docs/notes/` and referenced by the provider-selection decision. A capability absent from a tier is recorded as unverified for that tier; it is never compensated with fabricated data.

## Module ownership and transaction boundaries (contract-level)

`marketdata` owns transport adapters, raw transaction payloads and observations, normalized swaps, price/liquidity observations, USD conversion facts, universe snapshots and gap/operational state. `risk` owns risk facts and decisions, consuming market-data evidence only through `marketdata::api`. Provider I/O never occurs inside a database transaction; the future ingestion flow is: adapter receive → bounded queue → short batched raw-persistence transaction → separate normalization/derivation transactions (existing first-slice pattern). No cross-module SQL; no new module boundaries.

## Verification strategy

This change is design-only: validated by `openspec validate define-solana-data-provider-contract --strict`, the repository convention tests and the full local gate. The requirements become testable when F2/F3 implement them: each `solana-data-contract` requirement maps to at least one Testcontainers-backed test in those changes (admission gating, locator stability across providers via fixtures, timestamp separation, universe point-in-time queries, derivation separation, quality-policy admissibility, range declarations, capacity/backup documentation checks, selection-evidence linkage).

## Risks / Trade-offs

- [Finalized-only adds latency to freshness budgets] → measure in S1; horizons are hours, not seconds; freshness thresholds account for it.
- [180-day envelope may exceed provider retention/cost limits] → documented fallback (45-day lookback) is explicit and versioned; S2 measures before commitment.
- [At-slot state queries are single-provider capabilities] → matrix records them as provider-specific; the contract keeps liquidity observations source-tagged so a provider change cannot silently rewrite history.
- [Capacity estimates are assumptions] → F2 refuses mass recording until S1/S2 replace them with measurements; thresholds for backup evolution are explicit.
- [Chainstack unverified] → cannot be selected without its own spike; matrix cell stays unverified.
- [CLMM liquidity not fully modeled] → recorded partial-quality rather than approximated silently; later change may complete it.

## Migration Plan

No runtime migration in this change. Follow-on changes consume this contract: F2 (`marketdata` live-storage readiness) creates forward migrations for the fact separation; F3 (bounded ingestion) implements transport, time model, universe and gap machinery; F5 declares ranges; the owner executes S1–S5 and records the provider decision. Rollback of this change is a documentation revert; nothing else depends on it until F2/F3 are created.

## Open Questions

- Final watched venue program list (confirm in S2; Raydium CLMM inclusion may move to a later change).
- SOL/USD source pool selection (S1/S2 evidence; versioned with the derivation contract).
- Whether Helius historical transaction APIs are competitive with SQD for the 180-day envelope (S2 comparison).
- Chainstack capability verification (needs a reachable doc page or a key).
- Exact quality-policy thresholds after real distributions are observed (revisited by F5/F6 with versioned changes).
- Signal publication time semantics (F1.4 residual; owned by the F8 decision-feed design when publication exists).
- Pool-depth admissibility rule in quality policy v1 (deferred until S3 evidence on historical depth observability).
- Per-class RPO/RTO values (owned by F3.5 deployment guardrails).
