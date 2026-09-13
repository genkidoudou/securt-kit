## Purpose

Provides a shared browser Playground for Securt-Kit test applications so developers can manually exercise field encryption demos (CRUD, digest integrity, complex queries, multi-datasource) without replacing automated JUnit coverage.

## ADDED Requirements

### Requirement: Playground module is mountable on Boot 2 and Boot 3
The system SHALL provide a shared playground implementation without Servlet API dependencies, and Boot 2 / Boot 3 starters SHALL register a thin Servlet adapter that serves the playground at a configurable path (default `/playground`) when enabled.

#### Scenario: Enabled playground serves index
- **WHEN** `securtkit.playground.enabled` is true and the application starts with the corresponding starter
- **THEN** a browser request to the configured playground path MUST return the playground HTML entry page

#### Scenario: Disabled playground is not registered
- **WHEN** `securtkit.playground.enabled` is false or absent as disabled
- **THEN** the playground Servlet MUST NOT be registered (or MUST not expose the playground UI)

### Requirement: Interactive tabs for demo capabilities
The playground UI MUST expose tabs for CRUD, Digest, SQL, Complex, and Multi-DS behaviors described in the product design. SQL tab MAY deep-link to `/monitor` instead of re-implementing SQL parse/encrypt APIs in the first release.

#### Scenario: CRUD insert and query show decrypted application view
- **WHEN** a user inserts a row into an allowed table via the CRUD tab and then queries it through the application datasource path
- **THEN** the response MUST show application-visible (decrypted) field values for configured encryptor fields

#### Scenario: Digest demo can tamper and verify
- **WHEN** a user runs digest demo actions insert, tamper, and verify-read on an allowed digest-enabled table
- **THEN** verify-read MUST report success for an intact digest and MUST surface a verification failure after tamper when verify-on-read policy is fail-fast compatible in that app

#### Scenario: Complex actions are fixed verbs only
- **WHEN** a client calls the complex-run API with an unknown action name
- **THEN** the system MUST reject the request with an error and MUST NOT execute arbitrary SQL from the client

### Requirement: Table allow-list and datasource selection
Playground mutating and querying APIs MUST only operate on configured `allowed-tables`. Multi-datasource applications MUST be able to select a datasource id; single-datasource applications MUST hide or fix the datasource selector to the default.

#### Scenario: Disallowed table is rejected
- **WHEN** an API request targets a table not in `allowed-tables`
- **THEN** the system MUST reject the operation with a clear error

#### Scenario: Unsupported multi-ds action on single-ds app
- **WHEN** a single-datasource test app receives a multi-ds-only complex action
- **THEN** the system MUST return an error indicating the current application does not support that action

### Requirement: JUnit regression remains available
Introducing the playground MUST NOT remove or require deletion of existing JUnit integration tests in the four test modules; playground is an additive manual verification surface.

#### Scenario: Existing test modules still contain automated tests
- **WHEN** this change is implemented
- **THEN** the four test modules MUST continue to keep their JUnit-based regression suites as the CI path
