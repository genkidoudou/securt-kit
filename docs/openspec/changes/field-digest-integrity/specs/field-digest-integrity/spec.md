## Purpose

Provides configuration-driven row integrity digests over plaintext field values for Securt-Kit encryptor tables, with optional verify-on-read to detect database-side tampering on JDBC and MyBatis channels.

## ADDED Requirements

### Requirement: Digest configuration model
The system SHALL accept table-level digest rules under `securtkit.encryptor.tables[].digest`, each with ordered `source-fields` and a `target-field`. Global defaults MUST include `digest-strategy`, `digest-partial-update` (default `RELOAD`), `digest-verify-on-read` (default `false`), optional `digest-failure-policy`, and `digest-hmac-key` when the built-in HMAC strategy is used. Per-rule overrides for strategy, partial-update, verify-on-read, and failure-policy MUST take precedence over globals.

#### Scenario: Table digest list binds
- **WHEN** a table config includes a digest entry with `source-fields: [phone, id_card]` and `target-field: row_digest`
- **THEN** the system MUST load that rule for the table and preserve source-field order for digest computation

#### Scenario: Global defaults apply when rule omits overrides
- **WHEN** a digest rule omits `partial-update` and `verify-on-read`
- **THEN** the system MUST use the global `digest-partial-update` and `digest-verify-on-read` values

### Requirement: Startup validation for digest rules
The system MUST fail startup (or initialization) when digest configuration is invalid: empty `source-fields` or blank source entries; blank `target-field`; duplicate `target-field` on the same table/datasource; `target-field` also listed as an encrypted field for that table/datasource; missing digest strategy; strategy that does not support digest; or built-in HMAC strategy without a non-blank `digest-hmac-key`.

#### Scenario: Target field cannot also be encrypted
- **WHEN** `target-field` equals an encrypted field name for the same table and datasource
- **THEN** initialization MUST fail with a configuration error

#### Scenario: Blank source field rejected
- **WHEN** `source-fields` contains null, empty, or whitespace-only entries
- **THEN** initialization MUST fail with a configuration error

### Requirement: Plaintext digest computation on write
On INSERT/UPDATE that touches a table with digest rules, the system MUST compute each applicable digest from **plaintext** source values (before ciphertext is what is stored for encrypted columns), using configured source-field order. Digest columns themselves MUST NOT be encrypted by the field-encryptor path.

#### Scenario: INSERT writes digest from plaintext sources
- **WHEN** an INSERT provides all source-field plaintexts for a digest rule
- **THEN** the system MUST compute and persist the digest into `target-field`

#### Scenario: Digest uses configured source order
- **WHEN** source fields are `[phone, id_card]` with values A and B
- **THEN** the digest MUST equal the value produced for that ordered pair and MUST differ if the order of keys in the digest input is swapped

### Requirement: Partial UPDATE policies
For UPDATE statements that do not supply every `source-field` of a rule, the system MUST apply `partial-update`:
- `SKIP`: do not update that digest target for this statement
- `FAIL`: abort the write with an error
- `RELOAD` (default): reload missing source columns from the current row, decrypt encrypted columns to plaintext, merge with provided values, then recompute
For INSERT, `RELOAD` MUST be treated as failure when required sources are missing (no prior row).

#### Scenario: SKIP leaves digest unchanged when sources incomplete
- **WHEN** `partial-update` is `SKIP` and an UPDATE supplies only a subset of source fields
- **THEN** the system MUST NOT write a new value for that rule's `target-field`

#### Scenario: FAIL aborts incomplete UPDATE
- **WHEN** `partial-update` is `FAIL` and any source field is missing from the UPDATE bindings
- **THEN** the system MUST abort the write with an error

#### Scenario: INSERT cannot RELOAD missing sources
- **WHEN** an INSERT is missing a required source field and policy is `RELOAD`
- **THEN** the system MUST fail with an error indicating INSERT cannot RELOAD

### Requirement: Single-table SQL rewrite for missing digest columns
When a single-table INSERT or UPDATE omits a digest `target-field` that will be written, the system MAY rewrite the SQL to append the column and a corresponding parameter placeholder. Multi-table statements or unparseable SQL MUST NOT be rewritten; the system MUST warn and MUST NOT invent unsafe joins.

#### Scenario: Single-table INSERT appends missing target column
- **WHEN** a single-table INSERT lists columns but omits `row_digest` and a digest will be computed for that target
- **THEN** the rewritten SQL MUST include `row_digest` and a bound parameter for the digest value

#### Scenario: Multi-table UPDATE is not rewritten
- **WHEN** an UPDATE involves multiple tables or joins
- **THEN** the system MUST leave SQL unchanged and MUST emit a warning that digest rewrite was skipped

### Requirement: Dual-channel write integration
Digest write behavior MUST apply on both JDBC interceptor mode and MyBatis interceptor mode when encryptor is enabled for that mode, using the same resolved digest rules and plaintext semantics. SQL rewrite for MyBatis MUST only append targets that were actually computed (so `SKIP` MUST NOT leave unbound placeholders).

#### Scenario: MyBatis SKIP does not add unbound placeholders
- **WHEN** MyBatis UPDATE triggers digest handling with `partial-update: SKIP` for a rule whose sources are incomplete
- **THEN** the BoundSql MUST NOT gain an extra `?` for that skipped target without a matching parameter mapping

### Requirement: Optional verify-on-read
When `verify-on-read` is true for a rule, after decrypting result values the system MUST verify the digest against source plaintexts. Failure handling MUST follow the effective failure policy: `FALLBACK` warns and continues; `FAIL_FAST` throws a digest mismatch error; `SKIP` skips verification for that rule. Null digest values MUST warn and MUST NOT fail (migration-friendly). Missing columns needed for verification MUST NOT fail in the first release (debug only).

#### Scenario: FAIL_FAST on mismatch
- **WHEN** verify-on-read is enabled, all required columns are present, and the stored digest does not match recomputation
- **THEN** the system MUST throw a digest mismatch error and MUST NOT silently accept the row as valid

#### Scenario: FALLBACK on mismatch
- **WHEN** verify-on-read is enabled and digest mismatches under `FALLBACK`
- **THEN** the system MUST log a warning and MUST still return decrypted business field values

#### Scenario: Null digest is non-fatal
- **WHEN** verify-on-read is enabled and the digest column value is null
- **THEN** the system MUST warn and MUST NOT treat the row as a hard verification failure
