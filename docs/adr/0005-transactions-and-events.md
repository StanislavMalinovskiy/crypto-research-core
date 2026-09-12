# ADR 0005: Transaction boundaries and event semantics

- Status: Accepted
- Date: 2026-09-13

## Context

Synchronous module calls, database writes and future background work need explicit atomicity and delivery semantics. Spring Modulith does not choose those boundaries for the application.

## Decision

The owning module's application use case starts the business transaction. Controllers and repositories do not start independent business transactions, and a module never calls another module's repository. A synchronous public API call may join a transaction only when the dependency DAG permits it and short cross-module atomicity is intentional. Provider I/O, rate-limit waits and other long operations run outside database transactions. Post-commit work uses an explicitly selected after-commit mechanism.

High-frequency swaps, ticks and normalized market data use owned tables, bounded queues and batch pipelines. Rare completed business facts may use Spring Modulith events with documented synchronous or after-commit semantics. Guaranteed delivery requires a later ADR and change for a JDBC event registry or outbox. Publishing every swap through an event publication registry is forbidden.

## Consequences

Transactions remain short and have one clear owner. Cross-module workflows favor idempotent state transitions over deep atomic call chains. Each event contract must document publisher, consumer, timing, retry behavior and idempotency before implementation.

## Alternatives considered

Repository-owned transactions hide business atomicity. One large transaction around provider calls exhausts connections and couples failure domains. Using durable Modulith publication for the ingestion stream creates avoidable write amplification.
