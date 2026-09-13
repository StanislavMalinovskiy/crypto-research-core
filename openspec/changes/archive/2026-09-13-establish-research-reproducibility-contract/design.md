## Context

See [proposal.md](proposal.md). The six-module skeleton already enforces point-in-time dependency direction, but the repository has no single executable contract for time sources, exact arithmetic, run provenance, dataset identity or deterministic ordering. No business types or tables exist, so these semantics can be established without migration.

## Goals / Non-Goals

**Goals:**

- Establish one durable reproducibility contract before chain and financial value types are introduced.
- Assign provenance responsibilities without adding module dependencies or shared persistence.
- Turn the most dangerous implicit nondeterminism entry points into a portable Maven guard.
- Keep field-specific precision, schemas and serialization formats owned by the changes that introduce them.

**Non-Goals:**

- Implement a run manifest, dataset hasher, clock bean, random generator, money type or report.
- Select signal algorithms, prices, horizons, database columns or field precision/scale values.
- Promise byte-identical logs, database-generated identifiers or presentation metadata.
- Add dependencies, migrations, public APIs, module roots, runtime roles or worker abstractions.

## Decisions

### 1. Define reproducibility over computational evidence

A run is reproducible when the same immutable dataset snapshot, source revision/build identity, algorithm version, canonical configuration and seed produce the same ordered domain result. Wall-clock duration, worker scheduling, log order and generated report timestamps are operational metadata and are not part of result equivalence.

This definition is preferred over “same command gives similar metrics” because statistical similarity cannot audit a signal decision. Byte-identical whole reports were rejected because packaging timestamps and presentation metadata are not domain evidence.

### 2. Use UTC instants and explicit time sources

Persisted event, observation, decision and cutoff times use `Instant` semantics in UTC. Source event time, provider/system observation time and processing time remain distinct concepts rather than one ambiguous timestamp. Domain and application logic receives `Clock` or an explicit reference `Instant`; it never calls `Instant.now()`, local-date/time `now()` methods or `System.currentTimeMillis()` directly.

The application composition root may construct `Clock.systemUTC()` and inject it. Tests use fixed or controlled clocks. Local dates and zones are presentation/scheduling concerns and cannot replace an authoritative instant.

Allowing implicit clocks behind utility wrappers was rejected because it hides the same nondeterminism. Globally freezing time was rejected because it couples independent tests and workers.

### 3. Keep authoritative financial arithmetic exact

Chain-native quantities preserve raw integer units and decimals metadata where available. Calculated prices, costs, PnL, ratios and scores use `BigDecimal`/PostgreSQL `NUMERIC` or another explicitly exact representation. Every division, unit conversion or lossy persistence/public boundary declares `MathContext`, scale and `RoundingMode`; equality and comparison rules must not depend accidentally on `BigDecimal` scale.

The owning feature change selects and documents concrete field precision/scale from provider range and query evidence. Binary `float`/`double` remains acceptable only for non-authoritative telemetry or external-library boundaries that immediately convert with a documented rule; it cannot be stored or published as a financial fact.

A single global scale was rejected because token units, prices and ratios have different ranges. Silent default rounding was rejected because it changes results across code paths.

### 4. Record an algorithm-qualified provenance graph

Future persisted evaluation runs identify at least:

- application/build version and source revision, including whether the source tree was dirty;
- algorithm identifier and version;
- canonical configuration fingerprint;
- dataset fingerprint and point-in-time cutoff;
- explicit random seed when randomness participates.

Fingerprints are algorithm-qualified and include a canonicalization/version identifier. The exact canonical byte encoding and digest algorithm are selected by the first owning persistence change and become versioned data contracts; changing either produces distinct lineage.

`marketdata` owns raw/normalized input lineage, provider identity, normalization version and dataset snapshots. `signal` owns signal-definition/configuration version and the immutable decision-time evidence snapshot. `evaluation` owns evaluation run provenance, outcome semantics and reproducible reports while referencing, not copying or mutating, the owned evidence. `kernel` may later host stable value concepts but owns no run data. `risk` and `wallet` expose point-in-time evidence only through their APIs and do not become evaluation dependencies.

One global provenance table was rejected because it would have no clear module owner. Hashing only a database query string was rejected because mutable rows could produce a different dataset under the same query.

### 5. Make ordering deterministic before reduction or publication

Any order-sensitive query uses an explicit total `ORDER BY`. In-memory collections that originate from sets, maps, parallel completion or provider batches are canonically sorted before order-sensitive reduction or output. Every primary comparison has a stable tie-break derived from immutable identity. Algorithms with randomness receive and record a seed; retries reuse the same seed for the same run.

Depending on database natural order, hash iteration order or virtual-thread completion order was rejected because all are intentionally unspecified.

### 6. Add a narrow source guard without a new analysis dependency

`ReproducibilityConventionsTest` will scan production Java sources beneath `io.cryptoresearch` using normalized source text and reject direct occurrences of known implicit wall-clock and unseeded-random entry points. Keeping the focused guard separate prevents the existing general repository test from gaining a second responsibility. The initial denylist covers direct zero-argument `now()` calls on authoritative Java time types, `System.currentTimeMillis()`, `Math.random()`, `ThreadLocalRandom.current()`, default random-generator lookup and zero-argument `Random` construction.

The guard reports file and marker, is path-separator independent and permits explicit `Clock`, reference instants and seeded random construction. A future legitimate exception requires an approved change that narrows or documents the boundary; silent inline suppression is not added.

A new static-analysis dependency was rejected because a small focused guard covers the present empty baseline. The test does not attempt full semantic analysis; module tests must still prove deterministic behavior when algorithms appear.

### 7. Make the contract discoverable and single-owned

Create `docs/REPRODUCIBILITY.md` as the detailed operating contract and ADR 0009 as the durable decision rationale. `ARCHITECTURE.md`, `TECH_STACK.md`, `TESTING.md`, `AGENTS.md`, README, Project Summary, Roadmap, Glossary and the affected module pages link to or summarize the contract without duplicating its full rules.

### 8. Preserve module and transaction ownership

This change adds no transaction. Future run-manifest/result writes are owned by an `evaluation` application use case; future dataset snapshot writes are owned by `marketdata`. Neither may write the other's tables, and provider I/O remains outside database transactions. Exact atomicity is defined by the feature change that introduces persistence.

## Risks / Trade-offs

- **[Text guard has false positives or misses aliases]** → Keep it deliberately narrow, test positive/negative fixture strings and rely on behavior tests for real algorithms.
- **[Reproducibility metadata becomes incomplete]** → Treat missing mandatory provenance as an incomplete run rather than silently comparable evidence.
- **[Canonicalization changes invalidate hashes]** → Store canonicalization and digest identifiers and create new lineage instead of rewriting prior fingerprints.
- **[Exact arithmetic is slower]** → Prefer correctness for authoritative research values; optimize only with measured evidence and equivalence tests.
- **[A broad evaluation run spans module data]** → Reference immutable API-owned snapshots and fingerprints; do not create cross-module SQL or atomic transactions.

## Migration Plan

1. Add ADR 0009 and `docs/REPRODUCIBILITY.md`, then update active navigation and affected module invariants.
2. Add the focused repository source guard and its positive/negative tests without production code.
3. Run skip-IT and Docker-backed Maven verification plus strict OpenSpec validation.
4. Sync the new capability and repository-convention delta, then archive the change.

Rollback removes the documentation and guard and restores navigation. No runtime data, API or schema migration exists.
