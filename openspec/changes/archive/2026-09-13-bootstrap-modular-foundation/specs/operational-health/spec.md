## Purpose

Provides a minimal observable startup contract so operators and automated checks can distinguish a running foundation from a failed application.

## ADDED Requirements

### Requirement: Application context startup
The configured application SHALL start a Spring context against a migrated PostgreSQL database without requiring any business provider credentials.

#### Scenario: Foundation startup
- **WHEN** the application starts with a reachable PostgreSQL database
- **THEN** the application context SHALL become ready
- **AND** no market-data, strategy or execution integration SHALL be invoked.

### Requirement: Health endpoint
The application SHALL expose Actuator health information while leaving unrelated management endpoints unexposed by default.

#### Scenario: Healthy application
- **WHEN** a client requests the configured health endpoint after startup
- **THEN** the endpoint SHALL return a successful HTTP response
- **AND** the reported aggregate status SHALL be `UP`.
