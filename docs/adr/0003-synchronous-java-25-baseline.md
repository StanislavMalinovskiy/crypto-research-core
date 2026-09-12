# ADR 0003: Synchronous Java 25 baseline

- Status: Superseded by [ADR 0007](0007-stable-java-25-baseline.md)
- Date: 2026-09-13

This record preserves the original bootstrap decision. ADR 0007 replaces its preview-enabled portion while retaining the synchronous programming model.

## Context

The workload is dominated by blocking database and provider I/O, while module contracts must remain simple and independent of transport libraries.

## Decision

Use Java 25, Spring MVC and synchronous imperative module APIs. Enable preview compilation and tests, but isolate preview and JDK-specific concurrency APIs inside implementation packages. Use virtual threads for suitable blocking I/O and bounded platform threads for CPU-bound work.

## Consequences

`Mono`, `Flux`, R2DBC, Vert.x futures and provider async types do not appear in module contracts. WebSocket callbacks remain adapter details. Packaged applications using preview bytecode must start with `--enable-preview`.
