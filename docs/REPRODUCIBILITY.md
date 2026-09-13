# Research reproducibility contract

This document owns the detailed repository-wide rules for deterministic research evidence. Product goals remain in the [Roadmap](ROADMAP.md), structural ownership in [Architecture](ARCHITECTURE.md), and the accepted rationale in [ADR 0009](adr/0009-research-reproducibility.md).

## Reproducibility definition

A computational result is reproducible when all of the following inputs are identical:

- immutable dataset snapshot and its fingerprint;
- point-in-time cutoff;
- application build and source revision;
- algorithm identifier and version;
- canonical configuration and its fingerprint;
- explicit random seed when randomness participates.

Given those inputs, ordered domain results must be identical. Worker count, virtual-thread scheduling, provider-batch completion order, elapsed time, log order and presentation timestamps must not change the computational result. Reproducibility does not require byte-identical logs or report packaging.

## UTC and time sources

- Authoritative event, observation, processing, decision, cutoff and evaluation timestamps use `Instant` semantics in UTC.
- Source event time, system observation time and processing time are distinct facts when more than one exists.
- Domain and application calculations receive `Clock` or an explicit reference `Instant`; they do not read the machine clock implicitly.
- The application composition root may provide `Clock.systemUTC()`. Tests use a fixed or controlled clock.
- Local date/time and time zones are presentation or scheduling concepts, never replacements for an authoritative instant.
- A point-in-time computation may consume only observations available at or before its declared cutoff.

Direct `Instant.now()`, local/offset/zoned `now()` calls and `System.currentTimeMillis()` are forbidden in production module sources. A legitimate new boundary needs an approved change rather than an inline suppression.

## Exact numeric policy

- Chain-native quantities preserve raw integer units and decimals metadata when the source provides them.
- Authoritative prices, costs, PnL, ratios, scores and converted quantities use `BigDecimal`, PostgreSQL `NUMERIC` or another explicitly exact representation.
- Binary `float` and `double` are not authoritative financial representations and cannot be persisted or published as financial facts.
- Every division, unit conversion or other lossy operation declares its `MathContext`, precision and `RoundingMode`.
- Every persistence and public-contract boundary declares scale and rounding when information can be lost.
- Comparison and equality rules must not rely accidentally on `BigDecimal` representation scale.
- Binary floating point is allowed only for non-authoritative telemetry or an unavoidable library boundary followed immediately by a documented exact conversion.

There is no global project scale. The owning change derives each field's precision and scale from source ranges, required accuracy and database/query evidence, then versions that decision with the algorithm or schema contract.

## Run provenance manifest

Every persisted research or evaluation run must resolve a provenance manifest containing at least:

| Evidence | Required meaning |
|---|---|
| Build identity | Application version/build identifier |
| Source identity | VCS revision and whether the source tree was dirty |
| Algorithm identity | Stable identifier and version |
| Configuration identity | Fingerprint of canonical configuration |
| Dataset identity | Fingerprint of the immutable dataset snapshot |
| Point-in-time boundary | Data cutoff used by the computation |
| Randomness | Explicit seed when randomness participates |

Missing mandatory provenance marks the run incomplete. It must not be silently grouped or compared with reproducible runs.

## Dataset fingerprint and lineage

- A dataset fingerprint identifies content, not merely a query string or mutable table range.
- Fingerprints are algorithm-qualified and include a canonicalization/version identifier.
- The first owning persistence change selects the canonical byte encoding and digest algorithm and records both as versioned data contracts.
- Re-normalizing identical raw inputs with the same transformation version and canonical order must reproduce the fingerprint.
- Any changed raw input, transformation version, canonicalization version or digest algorithm produces distinguishable lineage.
- Normalized observations retain provider/source identity, raw-input identity and normalization/transformation version.
- Historical fingerprints and lineage records are immutable; new versions do not rewrite old evidence.

## Deterministic ordering and randomness

- Every order-sensitive SQL query has an explicit total `ORDER BY`.
- Inputs originating from sets, maps, provider batches or concurrent completion are canonically sorted before order-sensitive reduction or publication.
- Every primary sort key has a stable tie-break derived from immutable identity.
- A persisted randomized computation receives and records a seed. A retry of the same run reuses that seed.
- `Math.random()`, `ThreadLocalRandom.current()`, default random-generator lookup and zero-argument `Random` construction are forbidden in production module sources.
- Parallelism may change throughput, never result ordering or values.

## Module ownership

| Module | Reproducibility responsibility |
|---|---|
| `kernel` | May later define stable shared time/value concepts; owns no run or dataset data |
| `marketdata` | Raw and normalized input lineage, provider identity, transformation version, immutable dataset snapshots and fingerprints |
| `risk` | Point-in-time risk evidence exposed through its API |
| `wallet` | Point-in-time wallet evidence exposed through its API |
| `signal` | Algorithm/definition and configuration version plus immutable decision-time evidence snapshot |
| `evaluation` | Run provenance manifest, cutoff, valuation/outcome semantics, deterministic replay and reproducible reports |

`evaluation` references immutable signal and market-data evidence through public APIs. It does not read `risk` or `wallet` tables or current state. No shared provenance table bypasses module ownership.

## Persistence and transaction boundaries

- A future dataset snapshot transaction belongs to a `marketdata` application use case.
- A future run-manifest/result transaction belongs to an `evaluation` application use case.
- Neither module writes the other's tables; references use stable identities and fingerprints.
- Provider I/O does not occur inside these database transactions.
- Exact atomicity, DDL, constraints and idempotency are specified by the feature change that first introduces the persisted object.

## Verification strategy

- Repository convention tests reject the configured implicit time and randomness entry points and prove the guard with positive and negative source fixtures.
- Pure calculations use fixed clocks/reference instants, exact expected decimals and explicit seeds.
- Ordering tests permute logically identical input order and expect identical ordered results.
- Dataset tests change one input or transformation version and expect distinguishable fingerprints.
- Evaluation replay tests use recorded inputs and compare complete ordered domain results and provenance.
- PostgreSQL precision, scale and ordering behavior is verified with Testcontainers when persistence is introduced.

## Deferred implementation choices

This architecture stage intentionally does not choose Java domain type names, table layouts, column precision/scale, canonical serialization, digest algorithm, report format or seed-generation policy. Each owning OpenSpec change must make the relevant choice explicit and preserve the requirements above.
