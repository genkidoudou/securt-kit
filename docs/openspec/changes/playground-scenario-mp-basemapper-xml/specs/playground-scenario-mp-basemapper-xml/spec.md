## Purpose

Defines Playground complex-query scenario runs that encrypt plaintext parameters through MyBatis-Plus BaseMapper or XML mappers, while keeping the SQL workbench as an explicit non-encrypting bypass tool.

## ADDED Requirements

### Requirement: Scenario run uses MyBatis encryption path for simple equality
When an authenticated client runs `single-eq` with a plaintext phone parameter against a prepared `user` table under MYBATIS (or JDBC with MP still wired), the plain evidence path MUST execute via MyBatis-Plus BaseMapper/Wrapper so EncryptInterceptor encrypts the bind value before the database comparison. Cipher evidence MAY use skip-comment re-query with an explicitly encrypted value.

#### Scenario: Plaintext phone hits ciphertext row via run API
- **WHEN** the operator runs scenario `single-eq` with phone plaintext that matches a stored encrypted phone under the configured strategy
- **THEN** `plainRows` is non-empty for that match when seed data exists
- **AND** `cipherRows` presents encrypted phone values that differ from the plaintext presentation when encryption is active
- **AND** `sqlMeta` identifies the Wrapper/BaseMapper plain path and current encrypt mode

### Requirement: Complex scenarios use XML mapper statements
Scenarios that require joins, column aliases, table aliases, or SQL functions on cipher columns MUST execute their plain path through Mapper XML (or equivalent mapped statement), not ad-hoc JDBC string concatenation for the business evidence column.

#### Scenario: Join scenario returns three evidence parts via mapped SQL
- **WHEN** an authenticated client runs `join-user-orders` with valid parameters
- **THEN** the plain path uses a mapped statement over `user` and `orders`
- **AND** the response includes `plainRows`, `cipherRows`, and `sqlMeta`

#### Scenario: Function scenario states limitation
- **WHEN** `func-on-cipher` is executed
- **THEN** `sqlMeta` includes a limitation explaining that plaintext function semantics are not guaranteed on ciphertext
- **AND** zero rows MAY still be a successful response

### Requirement: LIKE scenario documents handler semantics
The `like-phone` scenario MUST document LikePatternHandler semantics in `sqlMeta` and MUST use BaseMapper/Wrapper (or mapped LIKE) for the plain path so encryption behavior matches the configured handler.

#### Scenario: LIKE documents handler semantics
- **WHEN** `like-phone` is executed
- **THEN** `sqlMeta` describes the effective LIKE matching behavior for encrypted columns

### Requirement: SQL workbench remains a bypass tool without literal auto-encrypt
`POST /api/scenarios/sql-run.json` without skip MUST continue to execute the submitted SELECT as raw JDBC (or equivalent) without rewriting string literals for field encryption. Under MYBATIS mode the system MUST surface a Chinese note (UI and/or `sqlMeta.note`) that plaintext literal conditions usually will not match encrypted columns and that operators should use scenario run or skip-plus-cipher SQL.

#### Scenario: Non-skip sql-run does not auto-encrypt literals
- **WHEN** the operator executes SQL containing a plaintext equality on an encrypted column without SECURT_SKIP
- **THEN** the system does not rewrite that literal to ciphertext for encryption
- **AND** the response includes guidance that scenario run or skip-plus-cipher should be used for encrypted-column matches under MYBATIS

#### Scenario: Skip sql-run still prepends token when enabled
- **WHEN** `useSkip` is true and skip-comment is enabled
- **THEN** the executed SQL includes the skip token when it was not already present

### Requirement: Dual-host Boot2 and Boot3 parity
Boot2 and Boot3 test applications MUST both register a scenario runner that implements the BaseMapper-plus-XML split for the fixed scenario catalog and MUST each have at least one automated smoke covering `single-eq` run structure (and plaintext hit when seed data is prepared).

#### Scenario: Both hosts expose runnable catalog
- **WHEN** playground is enabled on Boot2 or Boot3 with MyBatis-Plus mappers present
- **THEN** scenario list includes `single-eq`, `join-user-orders`, `column-alias`, `table-alias`, `like-phone`, and `func-on-cipher` as available when encrypt config for `user.phone` is present
