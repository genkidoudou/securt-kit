## Purpose

Defines Playground dual-mode person CRUD via explicit field crypto, plus a MyBatis-Plus complex-query tab that runs fixed scenarios and returns plain, cipher, and SQL evidence columns.

## ADDED Requirements

### Requirement: Person preflight does not require JDBC mode
The person desk preflight MUST NOT fail solely because encrypt mode is MYBATIS. Preflight MUST still verify table presence, allow-list, encrypted fields, digest rule, and skip-comment readiness. The current encrypt mode MAY be reported as informational.

#### Scenario: MYBATIS mode can be ready
- **WHEN** encrypt mode is MYBATIS and `playground_person` prerequisites other than JDBC mode are satisfied
- **THEN** person preflight reports ready (or does not fail on a jdbc-mode hard check)

#### Scenario: Missing encrypt fields still fails
- **WHEN** required encrypted fields or digest rules for `playground_person` are missing
- **THEN** person preflight reports not ready with a clear Chinese message

### Requirement: Person writes and reads work in both encrypt modes
Person create and update MUST persist configured sensitive fields as ciphertext and maintain `row_digest` without relying on JDBC interceptor enablement. Business list/query views MUST return decrypted sensitive fields. Raw/original views MUST return database ciphertext using skip-comment reads.

#### Scenario: Create under MYBATIS yields cipher in raw view
- **WHEN** encrypt mode is MYBATIS and an operator creates a person with plaintext phone and id_card
- **THEN** the raw/original view shows ciphertext for those fields
- **AND** the business view shows the plaintext values

#### Scenario: Create under JDBC does not double-encrypt
- **WHEN** encrypt mode is JDBC and an operator creates a person
- **THEN** sensitive columns are stored encrypted exactly once relative to the configured strategy
- **AND** business and raw views still differ for those columns

### Requirement: Playground exposes person and complex-query tabs
The Playground UI MUST provide two primary tabs: person maintenance and complex query. Person maintenance keeps its existing maintenance-desk layout. Complex query MUST present a scenario list, parameter form, run action, and three result panels.

#### Scenario: Dual tabs present
- **WHEN** an authenticated user opens `/playground/`
- **THEN** both 人员维护 and 复杂查询 tabs are available

### Requirement: Scenario catalog API
The system SHALL expose an authenticated API listing fixed complex-query scenarios with identifiers, titles, parameter schemas, and availability flags. Unavailable scenarios MUST include a Chinese reason (for example missing runner, tables, or encrypt config).

#### Scenario: List scenarios
- **WHEN** an authenticated client calls the scenarios list API
- **THEN** the response includes at least `single-eq`, `join-user-orders`, `column-alias`, `table-alias`, `like-phone`, and `func-on-cipher`

#### Scenario: Missing runner marks unavailable
- **WHEN** no scenario runner is registered in the host application
- **THEN** listed scenarios are marked unavailable with an explanatory message
- **AND** the complex-query tab remains reachable without crashing

### Requirement: Scenario run returns three evidence columns
Running a scenario MUST return `plainRows`, `cipherRows`, and `sqlMeta` for the selected datasource. `sqlMeta` MUST include encrypt mode and SQL or wrapper explanation text. Cipher rows MUST reflect skip-comment or otherwise undecrypted database values for encrypted columns when data exists.

#### Scenario: Equality scenario returns three parts
- **WHEN** an authenticated client runs `single-eq` with valid parameters against a prepared `user` table
- **THEN** the response includes `plainRows`, `cipherRows`, and `sqlMeta`
- **AND** for matching encrypted phone columns, plain and cipher presentations differ when encryption is active

#### Scenario: Join scenario uses user and orders
- **WHEN** an authenticated client runs `join-user-orders` with valid parameters
- **THEN** the run uses the existing `user` and `orders` tables
- **AND** the response includes the three evidence parts

### Requirement: LIKE and function scenario semantics
The `like-phone` scenario MUST document LikePatternHandler semantics in `sqlMeta`. The `func-on-cipher` scenario MUST include a limitation note in `sqlMeta` and MAY succeed with zero rows when function-on-ciphertext cannot match plaintext expectations.

#### Scenario: LIKE documents handler semantics
- **WHEN** `like-phone` is executed
- **THEN** `sqlMeta` describes the effective LIKE matching behavior for encrypted columns

#### Scenario: Function scenario states limitation
- **WHEN** `func-on-cipher` is executed
- **THEN** `sqlMeta` includes a limitation explaining that plaintext function semantics are not guaranteed on ciphertext

### Requirement: Alias scenarios are supported
The catalog MUST include column-alias and table-alias scenarios that execute queries using aliases on `user` (and related columns) and still return the three evidence parts with explanation in `sqlMeta`.

#### Scenario: Column alias run structure
- **WHEN** `column-alias` is executed successfully
- **THEN** the response includes `plainRows`, `cipherRows`, and `sqlMeta` describing the alias usage

#### Scenario: Table alias run structure
- **WHEN** `table-alias` is executed successfully
- **THEN** the response includes the three evidence parts
