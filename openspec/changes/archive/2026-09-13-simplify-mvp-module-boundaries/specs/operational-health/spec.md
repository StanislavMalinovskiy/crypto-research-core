## MODIFIED Requirements

### Requirement: Application context startup
The configured application SHALL start a Spring context against a migrated PostgreSQL database without requiring any business provider credentials.

#### Scenario: Foundation startup
- **WHEN** the application starts with a reachable PostgreSQL database
- **THEN** the application context SHALL become ready
- **AND** no market-data provider, signal processing or execution integration SHALL be invoked.
