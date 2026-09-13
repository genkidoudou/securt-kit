## Purpose

使 SELECT 投影中被函数、CAST、TRIM 等包裹的加密列，在 JDBC、MYBATIS 与 Monitor 结果侧仍能按源加密字段解密函数返回值，而不改写用户 SQL。

## ADDED Requirements

### Requirement: Function-wrapped select columns are registered for decrypt
When parsing a SELECT statement, for each select item whose expression contains one or more resolvable table columns nested inside functions or casts, the system SHALL register a decrypt mapping for that result column. The mapping MUST use the select alias when present; otherwise it MUST use a whitespace-normalized form of the select-item expression as the result column key. When multiple source columns appear in one select item, the system MUST use the first resolvable source column as the decrypt source (table and column).

#### Scenario: IFNULL without alias registers first encrypted column
- **WHEN** SQL is `SELECT IFNULL(phone,'2') FROM user` and `phone` is a configured encrypt field on `user`
- **THEN** the parse result MUST include a decrypt mapping whose source is `user.phone` and whose result key matches the expression (normalized)

#### Scenario: Alias preferred over expression text
- **WHEN** SQL is `SELECT IFNULL(phone,'2') AS phone FROM user`
- **THEN** the result key MUST be `phone` (the alias)

#### Scenario: Multi-column expression uses first source column
- **WHEN** SQL is `SELECT CONCAT(phone, id_card) FROM user` and both columns are encrypted
- **THEN** the decrypt mapping MUST use the first resolvable encrypted source column in expression order

### Requirement: JDBC and MYBATIS decrypt function return values
When reading query results under JDBC or MYBATIS encrypt mode, the system SHALL decrypt string values for select columns that have a decrypt mapping, using the mapped source table and column strategies. Matching MUST succeed via alias, normalized expression label, and—for JDBC when label matching fails—result column ordinal when available. Decrypt MUST use the shared field crypto path and existing failure policy (retain original value when decrypt fails, including literal defaults from `IFNULL`/`COALESCE`).

#### Scenario: IFNULL ciphertext cell decrypts under JDBC
- **WHEN** `mode=JDBC`, a row stores encrypted `phone`, and the query selects `IFNULL(phone,'2')` returning the ciphertext
- **THEN** the application-visible value MUST equal the plaintext phone

#### Scenario: IFNULL ciphertext cell decrypts under MYBATIS
- **WHEN** `mode=MYBATIS` and the same query returns ciphertext for a non-null phone
- **THEN** the mapped result (entity property or Map key) MUST equal the plaintext phone

#### Scenario: IFNULL default literal retained
- **WHEN** `phone` is NULL and `IFNULL(phone,'2')` returns `'2'`
- **THEN** the visible value MUST remain `'2'`

#### Scenario: Bare column regresses unchanged
- **WHEN** the query selects bare `phone`
- **THEN** decrypt behavior MUST match the existing bare-column path

### Requirement: Monitor dual view decrypts function projections
When Monitor executes a SELECT for the SQL workspace dual view, `plainRows` MUST decrypt function-wrapped encrypted projections using the same select-item decrypt mappings as runtime channels, falling back to label-equals-configured-field-name matching. `cipherRows` MUST remain raw database values. Row verify behavior for configured digest source columns MUST remain unchanged by this capability.

#### Scenario: Monitor plainRows decrypts IFNULL projection
- **WHEN** Monitor queries `SELECT IFNULL(phone,'2') FROM user` against encrypted data
- **THEN** `cipherRows` MUST show ciphertext (or the literal default) and `plainRows` MUST show plaintext when the cell was ciphertext

#### Scenario: Monitor with alias
- **WHEN** Monitor queries `SELECT IFNULL(phone,'2') AS phone FROM user`
- **THEN** `plainRows.phone` MUST equal the plaintext when the stored phone was non-null ciphertext

### Requirement: Semantic boundaries are documented and non-goals enforced
The system MUST NOT rewrite user SQL to unwrap functions. The system MUST NOT claim that in-database plaintext-semantic functions (`UPPER`/`LIKE`/`SUBSTRING` on ciphertext) become correct; decrypt applies only to the function return value. Documentation MUST state the first-source-column rule for multi-column expressions and the FALLBACK behavior when decrypt fails.

#### Scenario: No SQL rewrite
- **WHEN** a function-wrapped select is executed
- **THEN** the SQL sent to the database MUST be the user-provided statement (aside from existing unrelated rewrites such as digest column injection if already configured)

#### Scenario: Transforming ciphertext may fail open per policy
- **WHEN** a function returns a value that is not valid ciphertext for the mapped strategy
- **THEN** the visible value MUST follow the configured failure policy (typically retain the original return value)
