## Purpose

定义 Playground 人员维护台：对 `playground_person` 做增删改查，并在业务解密视图与数据库原始密文视图之间切换，以验证敏感字段加密落库行为。

## ADDED Requirements

### Requirement: Person table schema and protection rules
The system SHALL provide a `playground_person` table with columns `id`, `name`, `phone`, `id_card`, `age`, and `row_digest`. `phone` and `id_card` MUST be configured as encrypted fields. `row_digest` MUST be maintained from source fields `phone` and `id_card`. Clients MUST NOT be able to set `row_digest` through create or update APIs.

#### Scenario: Protected columns on write
- **WHEN** a person is created or updated with plaintext `phone` and `id_card`
- **THEN** the persisted row MUST store encrypted `phone` and `id_card` values and a non-empty `row_digest` derived from those fields

#### Scenario: Client cannot set digest
- **WHEN** a create or update request includes a client-supplied `row_digest`
- **THEN** the system MUST ignore that value or reject the request and MUST NOT persist the client-supplied digest as authoritative

### Requirement: Person CRUD APIs
The Playground SHALL expose person create, update, delete, and list endpoints under `/api/person/*` that operate only on `playground_person` for the selected datasource.

#### Scenario: Create returns business plaintext
- **WHEN** the user creates a person with valid `name`, `phone`, `id_card`, and `age`
- **THEN** the response MUST succeed and a subsequent business list MUST show the same plaintext values for those fields

#### Scenario: Update changes protected values
- **WHEN** the user updates `phone` or `id_card` for an existing person
- **THEN** a subsequent raw list MUST show different ciphertext for the changed field, `row_digest` MUST change, and a business list MUST show the new plaintext

#### Scenario: Delete removes the row
- **WHEN** the user deletes a person by `id`
- **THEN** subsequent business and raw lists MUST NOT include that `id`

#### Scenario: Missing id on mutate
- **WHEN** update or delete targets an unknown `id`
- **THEN** the API MUST fail with a clear message and MUST NOT report success

### Requirement: Business and raw list views
The list API SHALL accept `view=business` or `view=raw`. Business view MUST return decrypted `phone` and `id_card`. Raw view MUST return database-stored ciphertext for those fields without applying decryption.

#### Scenario: Business view decrypts
- **WHEN** the user lists persons with `view=business` after a successful create
- **THEN** `phone` and `id_card` in each row MUST equal the plaintext values that were written

#### Scenario: Raw view shows ciphertext
- **WHEN** the user lists the same persons with `view=raw`
- **THEN** `phone` and `id_card` MUST differ from the plaintext values and MUST match the values stored in the database

#### Scenario: Digest visible in both views
- **WHEN** a person with a generated digest is listed in either view
- **THEN** `row_digest` MUST be present and non-empty

### Requirement: Conditional query filters
The list API SHALL support optional filters `name`, `phone`, and `idCard`. Name filtering MUST match the plaintext `name` column (at least equality). Phone and id-card filters MUST perform exact match against encrypted storage using the plaintext values supplied by the client.

#### Scenario: Filter by phone hits
- **WHEN** the user lists with `phone` equal to a stored person's plaintext phone
- **THEN** the result MUST include that person

#### Scenario: Filter by wrong phone misses
- **WHEN** the user lists with a phone value that no person has
- **THEN** the result MUST be empty for that filter

#### Scenario: Filter by id card hits
- **WHEN** the user lists with `idCard` equal to a stored person's plaintext id card
- **THEN** the result MUST include that person

#### Scenario: Combined filters
- **WHEN** the user supplies multiple filters
- **THEN** the result MUST include only rows that satisfy every supplied filter

### Requirement: Maintenance UI replaces lifecycle wizard
The default Playground page SHALL be a person maintenance UI with datasource selection, business/raw view toggle, filter form, person table, and create/edit dialog. Lifecycle wizard steps MUST NOT be the default experience.

#### Scenario: Default page is maintenance desk
- **WHEN** an authenticated user opens `/playground/`
- **THEN** the page MUST show person filters, the person table, and create affordance rather than ordered lifecycle steps

#### Scenario: Toggle raw disables mutate actions
- **WHEN** the user switches to the raw view
- **THEN** edit and delete actions MUST be disabled or blocked in the UI until the user returns to the business view

#### Scenario: Delete requires confirmation
- **WHEN** the user requests delete in the business view
- **THEN** the UI MUST ask for confirmation before calling the delete API

### Requirement: Readiness and error reporting
The system SHALL surface readiness problems for `playground_person` (missing table, whitelist, encryption, or digest configuration) and SHALL return clear Chinese failure messages for validation and operational errors via the standard API response envelope.

#### Scenario: Missing configuration
- **WHEN** the selected datasource lacks required table, whitelist, encryption, or digest setup for `playground_person`
- **THEN** the UI MUST show a top-level readiness warning and mutating operations MUST fail with readable messages

#### Scenario: Validation failure
- **WHEN** create or update omits a required field or supplies an invalid age
- **THEN** the API MUST fail without persisting a partial success
