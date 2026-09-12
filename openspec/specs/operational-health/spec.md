# Operational Health Specification

## Purpose

Provides a minimal observable startup contract so operators and automated checks can distinguish a running foundation from a failed application.

## Requirements

### Requirement: Application context startup
The configured application SHALL start a Spring context against a migrated PostgreSQL database without requiring any business provider credentials.

#### Scenario: Foundation startup
- **WHEN** the application starts with a reachable PostgreSQL database
- **THEN** the application context SHALL become ready
- **AND** no market-data, strategy or execution integration SHALL be invoked.

### Requirement: Health endpoint
The application SHALL expose aggregate, liveness and readiness Actuator health information while leaving unrelated management endpoints unexposed by default.

#### Scenario: Healthy application
- **WHEN** a client requests the aggregate health endpoint after startup
- **THEN** the endpoint SHALL return a successful HTTP response
- **AND** the reported aggregate status SHALL be `UP`.

#### Scenario: Process liveness
- **WHEN** an orchestrator requests the liveness probe
- **THEN** the probe SHALL report only process-local application availability
- **AND** PostgreSQL and external providers SHALL not participate in the liveness group.

#### Scenario: Database-backed readiness
- **WHEN** an orchestrator requests the readiness probe after startup
- **THEN** the probe SHALL include application readiness and PostgreSQL health
- **AND** the probe SHALL report `UP` only while the application is ready to use its required database.

#### Scenario: Database becomes unavailable
- **WHEN** PostgreSQL becomes unavailable after the application has started
- **THEN** the readiness probe SHALL report `DOWN` with HTTP 503
- **AND** the process-local liveness probe SHALL remain `UP` with a successful HTTP response.
