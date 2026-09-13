## Purpose

在 Playground 复杂查询页常驻展示 user/orders 演示数据，并提供场景示例 SQL 与只读 SELECT 执行（含可选 SECURT_SKIP），方便对照写 SQL 并查看三栏证据。

## ADDED Requirements

### Requirement: Dual-table seed preview is always available on complex-query tab
The Playground complex-query UI MUST present two persistent read-only lists for demonstration tables `user` and `orders`. The system SHALL expose `GET /api/scenarios/seed-preview.json` that returns `userRows`, `ordersRows`, an `encryptNote`, and applied row `limits`. Preview reads MUST use skip-comment raw reads when skip-comment is enabled so listed values match database ciphertext form. Default row limit MUST be at most 50 with a hard cap of 100. The UI MUST load preview when entering the complex-query tab (and when datasource changes) and MUST provide a refresh control.

#### Scenario: Seed preview returns both tables
- **WHEN** an authenticated client calls seed-preview with a configured playground datasource
- **THEN** the response MUST include array fields `userRows` and `ordersRows` and a non-empty `encryptNote`

#### Scenario: UI shows two table lists
- **WHEN** the user opens the complex-query tab
- **THEN** the page MUST show distinct containers for user and orders preview data

### Requirement: Scenario catalog includes sample params and example SQL
When a scenario runner is registered, each available scenario descriptor MUST include `sampleParams`, `exampleSqlPlain`, `exampleSqlCipher`, and `sampleHint` suitable for the boot demo seed (or explicit empty maps/strings only when not applicable). Selecting a scenario in the UI MUST populate parameter inputs from `sampleParams` and MUST load `exampleSqlPlain` into the SQL editor by default, with a control to switch to `exampleSqlCipher`.

#### Scenario: Catalog exposes sample fields for single-eq
- **WHEN** scenarios are listed with a registered runner
- **THEN** the `single-eq` entry MUST include a sample phone param and non-empty plain/cipher example SQL strings

#### Scenario: Selecting scenario loads plain example SQL
- **WHEN** the user selects a scenario that has `exampleSqlPlain`
- **THEN** the SQL editor MUST contain that plain example SQL

### Requirement: Read-only SQL execution with optional SECURT_SKIP
The system SHALL expose `POST /api/scenarios/sql-run.json` accepting `sql`, `useSkip`, and optional `datasourceId`. The system MUST reject empty SQL, statements containing `;`, statements that are not SELECT after stripping leading comments, and statements whose head is a write/DDL verb. When `useSkip` is true and skip-comment is enabled, the system MUST prepend the configured skip token comment if missing; when `useSkip` is true but skip-comment is disabled, the system MUST fail with a clear error. Successful runs MUST return `rows` (truncated to the configured cap) and `sqlMeta` including the executed SQL, encrypt mode, skipped flag, and truncated flag. Existing `POST /api/scenarios/run.json` behavior MUST remain unchanged.

#### Scenario: Valid SELECT succeeds
- **WHEN** the client posts a single `SELECT` without semicolons
- **THEN** the API MUST return success with `rows` and `sqlMeta`

#### Scenario: Multi-statement rejected
- **WHEN** the SQL contains `;`
- **THEN** the API MUST respond with HTTP 400-class failure and a Chinese error message

#### Scenario: Write statement rejected
- **WHEN** the SQL starts with `UPDATE` (ignoring leading comments/case)
- **THEN** the API MUST reject the request

#### Scenario: useSkip prepends skip comment when enabled
- **WHEN** `useSkip=true`, skip-comment is enabled, and SQL lacks the skip token
- **THEN** `sqlMeta.sql` MUST include the skip comment prefix and `sqlMeta.skipped` MUST be true

### Requirement: SQL run results map into evidence panels
After a successful sql-run, the UI MUST update evidence panels: when skipped, cipher panel shows `rows`, SQL panel shows `sqlMeta`, and plain panel shows the same rows with a clear note that values are SECURT_SKIP originals without result decryption; when not skipped, plain panel shows `rows` and SQL panel shows `sqlMeta`. Running a fixed scenario MUST continue to populate plain/cipher/sqlMeta as today.

#### Scenario: Skip run fills cipher and annotated plain
- **WHEN** the user executes SQL with SECURT_SKIP checked and the call succeeds
- **THEN** the cipher evidence MUST show returned rows and the plain evidence MUST indicate skip/original semantics

#### Scenario: Scenario run still fills three panels
- **WHEN** the user runs a fixed scenario successfully
- **THEN** plainRows, cipherRows, and sqlMeta MUST all be present in the evidence area
