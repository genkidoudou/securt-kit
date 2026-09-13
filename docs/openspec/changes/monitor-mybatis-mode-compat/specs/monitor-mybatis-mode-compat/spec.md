## Purpose

使 Monitor 的 SQL 查询双视图与单行验签在 `mode=MYBATIS` 下仍能正确展示解密明文并完成摘要验签，而不依赖 JDBC 拦截通道。

## ADDED Requirements

### Requirement: SQL query dual view is mode-independent
When Monitor executes a SELECT query for the SQL workspace, the system SHALL return `cipherRows` as values read from the database without ResultSet interceptor decryption, and SHALL return `plainRows` with configured encrypted columns decrypted using the same field strategies as the encryptor configuration. This behavior MUST work when `securtkit.encryptor.mode` is `MYBATIS` and MUST remain correct when the mode is `JDBC`.

#### Scenario: MYBATIS mode distinguishes plain and cipher
- **WHEN** `mode=MYBATIS` and the user queries a table that stores encrypted `phone`
- **THEN** `cipherRows.phone` MUST differ from the original plaintext and `plainRows.phone` MUST equal the original plaintext

#### Scenario: Non-encrypted columns unchanged
- **WHEN** a selected column is not configured as encrypted
- **THEN** its value in `plainRows` MUST equal its value in `cipherRows`

#### Scenario: Skip comment still required for cipher path when interceptor URL is present
- **WHEN** skip-comment is disabled and the deployment still relies on a skip token for the cipher query path
- **THEN** the API MUST fail with a clear message requiring skip-comment (existing guard), or use an equivalent raw-read path that does not depend on interceptor decryption

### Requirement: Row verify decrypts encrypted digest sources first
When Monitor performs single-row digest verification, the system SHALL read the stored row, decrypt any digest source fields that are configured as encrypted, then compute expected digests and compare them to stored digest target fields. Verification MUST succeed for intact rows under `mode=MYBATIS` when digests were produced from plaintext sources at write time.

#### Scenario: Intact encrypted row verifies under MYBATIS
- **WHEN** `mode=MYBATIS`, a row has encrypted digest source fields and a correct `row_digest`
- **THEN** row verify MUST report verified success

#### Scenario: Tampered digest fails under MYBATIS
- **WHEN** the stored digest target does not match the digest of decrypted source fields
- **THEN** row verify MUST report verified failure

#### Scenario: Missing digest rules still rejected
- **WHEN** the table has no digest rules
- **THEN** row verify MUST fail with a clear configuration error

### Requirement: No JDBC channel reactivation for MYBATIS mode
Monitor MUST NOT re-enable JDBC interceptor encryption/decryption for the application datasource solely to support these tools when `mode=MYBATIS`. Result decryption for Monitor tools MUST be applied explicitly after reading raw values.

#### Scenario: MYBATIS mode remains exclusive
- **WHEN** `mode=MYBATIS`
- **THEN** Monitor SQL/验签 success MUST NOT require `EncryptModeHolder.isJdbc() == true`
