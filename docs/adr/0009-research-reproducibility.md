# ADR 0009: Research reproducibility baseline

- Status: Accepted
- Date: 2026-09-13

## Context

The project exists to produce auditable evidence about on-chain signals. Results cannot be compared honestly if they depend on the machine clock, binary floating-point artifacts, unordered iteration, unrecorded randomness, mutable datasets or an unidentified code/configuration version.

These semantics must be established before chain identities, quantities, signal snapshots and evaluation persistence are implemented.

## Decision

Adopt the repository-wide [research reproducibility contract](../REPRODUCIBILITY.md).

A reproducible computation uses an immutable, fingerprinted point-in-time dataset and records its code/build revision, algorithm version, canonical configuration fingerprint, cutoff and random seed. Identical evidence inputs must produce identical ordered domain results regardless of worker scheduling.

Authoritative timestamps use UTC `Instant` semantics. Domain and application code receives `Clock` or a reference instant explicitly. Authoritative financial quantities use raw integer units or exact decimal arithmetic with explicit precision, scale and rounding at every lossy boundary; binary floating point is not authoritative.

Every order-sensitive operation defines a total order and stable tie-break. Randomized research receives and records a seed.

Ownership remains aligned with ADR 0008: `marketdata` owns input lineage and dataset fingerprints, `signal` owns decision-time definition/configuration evidence, and `evaluation` owns run manifests, outcomes and reports while referencing immutable owned evidence. This decision changes no module dependency.

## Consequences

Future feature changes must define field-specific precision/scale, canonical encoding, fingerprint algorithm and persisted provenance together with the data they introduce. Missing mandatory provenance makes a run incomplete rather than silently comparable.

Repository verification rejects a focused set of implicit clock and unseeded-randomness entry points. Behavioral tests remain responsible for proving real algorithms deterministic; the source guard is not a semantic analyzer.

Exact arithmetic and provenance storage add implementation cost, but they prevent results that cannot be audited or reproduced. No current data migration is required because business persistence does not exist.

## Alternatives considered

Best-effort reproducibility without fingerprints was rejected because mutable inputs cannot be identified. A single global numeric scale was rejected because token units, prices and ratios have different ranges. Byte-identical reports were rejected because presentation and operational metadata are not computational evidence.
