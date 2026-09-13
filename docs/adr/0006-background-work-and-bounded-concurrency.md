# ADR 0006: Background work and bounded concurrency

- Status: Accepted
- Date: 2026-09-13

## Context

Polling, ingestion, enrichment, replay and measurement dominate the expected workload. Multiple application instances must not duplicate scheduled work, and virtual threads do not provide admission control or backpressure.

## Decision

Keep one codebase and one deployable JAR. Instances may select workload roles such as `api`, `ingestion` or `evaluation`; roles enable workloads but do not create service ownership or separate artifacts.

Every recurring or recoverable job is idempotent and claims work through PostgreSQL. Short database-local batches may use `FOR UPDATE SKIP LOCKED`. Work that spans remote calls uses a durable expiring lease such as `locked_until`, committed before provider I/O. Claim, completion and retry transitions carry stable idempotency keys.

Virtual threads execute suitable blocking tasks but do not authorize unbounded fan-out. Every provider adapter defines concurrency, rate, timeout and finite transient-retry limits. Queues and batches are bounded, cancellation propagates, and database concurrency is capped by an explicit budget no larger than the connection pool. CPU-bound work uses bounded platform-thread executors.

## Consequences

The same JAR can scale API and worker workloads independently while remaining one modular monolith. PostgreSQL is the initial coordination mechanism; Redis, Kafka and a separate scheduler are not introduced. Operational metrics must expose queue depth, claim latency, saturation, retries and failures when the first worker is implemented.

## Alternatives considered

An in-memory scheduler lock cannot coordinate instances. Unrestricted virtual-thread-per-item fan-out can overwhelm providers and PostgreSQL. A broker or dedicated worker artifact is deferred until measured database-backed coordination is insufficient.
