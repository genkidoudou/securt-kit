## Purpose

Defines the Monitor ops console behavior for full encrypt/digest config visibility, primary-key resolution, single-value and single-row crypto/digest tools, batch preview-then-apply jobs, and SELECT dual-view SQL—kept separate from the playground demo UI.

## ADDED Requirements

### Requirement: Config overview exposes encrypt and digest rules
The system SHALL expose via the authenticated monitor config API a complete view of field-encryptor settings including per-table encrypt fields/strategies, per-table digest rules, and digest-related global flags. Secret material such as HMAC keys MUST NOT be returned in plaintext; the API MAY return a boolean indicating whether a key is configured.

#### Scenario: Config includes digest rules without leaking secrets
- **WHEN** an authenticated client calls `GET /api/config.json`
- **THEN** the response includes table field encrypt entries and digest rule entries (source fields, target field) plus global digest flags
- **AND** any HMAC key value is omitted or replaced by a configured flag rather than the secret itself

#### Scenario: Config shows mode and skip-comment
- **WHEN** an authenticated client calls `GET /api/config.json`
- **THEN** the response includes encryptor mode and skip-comment enablement information used by dual-view SQL

### Requirement: Primary key resolution order
The system SHALL resolve a table's primary-key column in this order: monitor `table-primary-keys` configuration, then MyBatis-Plus TableInfo when available on the classpath, then an optional request/UI-supplied column for the current operation only. A manually supplied column MUST NOT be persisted to configuration by this feature.

#### Scenario: Configured primary key wins
- **WHEN** `securtkit.monitor.table-primary-keys` defines a column for table `user`
- **THEN** primary-key resolution for `user` returns that column with source `config`

#### Scenario: Manual id column is ephemeral
- **WHEN** neither config nor TableInfo yields a primary key and the client supplies `idColumn` on a verify or batch request
- **THEN** the operation uses that column for the request
- **AND** the value is not written back into monitor configuration

#### Scenario: Missing primary key rejects mutating ops
- **WHEN** a batch apply or row verify requires a primary key and none can be resolved
- **THEN** the API returns a client error instructing the operator to configure or supply `idColumn`

### Requirement: Single-value digest sign and verify
The system SHALL provide an authenticated API to generate a digest signature over plaintext source values and to verify a digest against plaintext source values using configured digest strategies.

#### Scenario: Sign plaintext sources
- **WHEN** an authenticated client posts `action=sign` with plaintext source field values for a configured digest rule
- **THEN** the response returns the computed digest value

#### Scenario: Verify mismatch
- **WHEN** an authenticated client posts `action=verify` with plaintext sources and an incorrect expected digest
- **THEN** the response indicates verification failure without requiring a page-level session crash

### Requirement: Single-row digest verification
The system SHALL allow verifying digest integrity for one row identified by table name and primary-key value (with optional explicit id column). The monitor response MUST be a structured JSON result (`verified` true/false and details) even when digest failure policy would throw in normal application reads.

#### Scenario: Verify existing row
- **WHEN** an authenticated client posts table name and id for a digest-configured table
- **THEN** the system loads the row and returns verification outcome with enough detail to diagnose mismatch

### Requirement: Batch preview then apply by primary key
The system SHALL support batch encrypt, decrypt, or digest-sign operations against rows selected by table plus WHERE, using a two-step flow: preview (no writes) then apply (UPDATE by primary key). Apply MUST require a valid preview job (or equivalent tokenized change set). A single apply MUST reject or truncate beyond a hard row limit of 500. Empty WHERE MUST be rejected by default. Multi-statement WHERE/SQL MUST be rejected. By default only tables that already have encrypt or digest configuration are allowed.

#### Scenario: Preview does not mutate
- **WHEN** an authenticated client requests batch preview for `op=encrypt` with a valid WHERE on a configured table
- **THEN** the response includes proposed before/after values and a job identifier
- **AND** no database rows are updated

#### Scenario: Apply after confirm
- **WHEN** an authenticated client applies a still-valid preview job
- **THEN** the system updates matching rows by primary key according to the previewed changes
- **AND** returns counts of succeeded and failed rows when partial failure occurs

#### Scenario: Expired job rejected
- **WHEN** an authenticated client applies an expired or unknown job id
- **THEN** the API returns a client error requiring a new preview

#### Scenario: Empty WHERE rejected by default
- **WHEN** batch preview is requested with an empty WHERE and empty-where is not explicitly enabled
- **THEN** the API returns a client error

### Requirement: SELECT dual-view query
The system SHALL execute only SELECT statements on the query-sql API. Successful dual-view responses MUST include both ciphertext-oriented rows and decrypted/plain rows for the same query when ciphertext acquisition is available. Non-SELECT statements MUST be rejected. If ciphertext cannot be obtained (for example skip-comment disabled with no alternate path), the API MUST return an explicit error rather than silently returning only plaintext.

#### Scenario: Dual view success
- **WHEN** an authenticated client submits a SELECT query and ciphertext acquisition is available
- **THEN** the response contains `cipherRows` and `plainRows` for the query

#### Scenario: Non-select rejected
- **WHEN** an authenticated client submits an UPDATE or DELETE as query-sql
- **THEN** the API returns a client error stating only SELECT is allowed

#### Scenario: Cipher view unavailable
- **WHEN** ciphertext acquisition is not possible for the environment
- **THEN** the API returns an explicit error describing the missing prerequisite

### Requirement: Parameter-encrypted SQL remains available
The system SHALL continue to provide (and MAY enhance display of) SQL with encrypted parameter literals for operator inspection without executing that SQL as DML through the query executor.

#### Scenario: Encrypt-sql preview
- **WHEN** an authenticated client posts SQL for parameter encryption
- **THEN** the response returns SQL text with encrypted values suitable for inspection

### Requirement: Monitor remains separate from playground
The enhanced monitor ops console MUST remain mounted under the monitor path and MUST NOT merge its UI into the playground module. Playground continues to serve demo CRUD/complex flows independently.

#### Scenario: Independent mounts
- **WHEN** both monitor and playground are enabled
- **THEN** operators can use `/monitor` ops features without requiring playground pages for config overview, batch apply, or SELECT dual-view
