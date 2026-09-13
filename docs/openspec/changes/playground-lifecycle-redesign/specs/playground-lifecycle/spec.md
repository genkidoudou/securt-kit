## Purpose

定义测试人员在 Playground 中逐步验证单条业务数据的加密写入、摘要生成、查询解密、敏感字段更新、完整性校验和篡改检测时可依赖的页面及 API 行为。

## ADDED Requirements

### Requirement: Lifecycle preflight exposes readiness
The system SHALL expose lifecycle readiness for the selected datasource before lifecycle operations are enabled, including whether `digest_user` is allowed, required fields exist, `name` and `phone` are encrypted, and `phone` produces `row_digest`.

#### Scenario: Datasource is ready
- **WHEN** the selected datasource and encryptor configuration satisfy every lifecycle prerequisite
- **THEN** preflight MUST report the lifecycle as ready and the first step MUST be executable

#### Scenario: Required configuration is missing
- **WHEN** a table, field, encryption rule, digest rule, or datasource required by the lifecycle is unavailable
- **THEN** preflight MUST report each missing prerequisite and affected lifecycle steps MUST remain disabled

### Requirement: UI presents an ordered single-record lifecycle
The playground UI SHALL make the default experience an ordered lifecycle for one `digest_user` record with the steps insert, query-and-decrypt, update, query-again, verify-digest, and tamper-and-verify.

#### Scenario: New lifecycle starts
- **WHEN** a ready Playground is opened or its browser-local lifecycle is reset
- **THEN** insert MUST be executable and later steps MUST remain unavailable until a record has been inserted

#### Scenario: Insert establishes lifecycle identity
- **WHEN** insert succeeds
- **THEN** the UI MUST retain the generated record id for subsequent steps without requiring the user to enter it again

#### Scenario: User revisits completed step
- **WHEN** the user selects an earlier completed step
- **THEN** the UI MUST display the snapshot captured for that step

#### Scenario: Lifecycle is reset
- **WHEN** the user resets the lifecycle
- **THEN** the UI MUST clear its browser-local record id, step states, and snapshots without deleting or modifying the database record

### Requirement: Lifecycle operations return a three-view snapshot
Each successful lifecycle operation SHALL return a consistent snapshot containing the step, result status, record id, user-facing message, request plaintext, raw database row, normal business row, digest verification data, assertions, and structured error data where applicable.

#### Scenario: Insert snapshot proves encryption and digest generation
- **WHEN** the user inserts valid plaintext `name`, `phone`, `age`, and `email`
- **THEN** the snapshot MUST show raw `name` and `phone` values different from their plaintext values, a non-empty `row_digest`, and business `name` and `phone` values equal to the submitted plaintext

#### Scenario: Query snapshot proves decryption
- **WHEN** the user queries the intact lifecycle record through the normal application path
- **THEN** the snapshot MUST show the raw encrypted row separately from the correctly decrypted business row

#### Scenario: Operation assertion fails
- **WHEN** an operation completes at the database level but an expected encryption, decryption, digest, or value comparison is false
- **THEN** the lifecycle result MUST be failed and MUST identify the failed assertion

### Requirement: Sensitive-field update regenerates protected values
The lifecycle SHALL permit updates to business fields but MUST NOT permit the client to directly set `row_digest`.

#### Scenario: Phone is updated
- **WHEN** the user updates `phone` through the lifecycle update operation
- **THEN** the raw encrypted phone and `row_digest` MUST differ from the values captured after insert, and a subsequent business query MUST return the new plaintext phone

#### Scenario: Client submits digest field
- **WHEN** a lifecycle insert or update request attempts to provide `row_digest`
- **THEN** the system MUST reject the request and MUST generate the digest only through configured integrity processing

### Requirement: Raw reads bypass transformation
Raw database snapshots SHALL be obtained through an explicitly bypassed query and MUST represent stored database values rather than values transformed by the normal encryption interceptor.

#### Scenario: Raw read of encrypted fields
- **WHEN** a lifecycle snapshot is collected for a record with encrypted `name` and `phone`
- **THEN** the raw view MUST contain stored encrypted values while the business view MUST contain decrypted values

#### Scenario: Raw marker without bypass is insufficient
- **WHEN** an API response only labels a result as raw but the query was not explicitly bypassed
- **THEN** that result MUST NOT be treated or reported as a valid raw database snapshot

### Requirement: Digest verification distinguishes expected outcomes
The lifecycle SHALL expose a structured digest verification result and SHALL interpret a verification failure according to the active test step.

#### Scenario: Intact record verifies
- **WHEN** the verify-digest step reads an untampered lifecycle record
- **THEN** digest verification MUST pass and the step MUST be successful

#### Scenario: Ordinary read detects invalid digest
- **WHEN** an invalid digest is encountered outside tamper-and-verify
- **THEN** the operation MUST fail and surface the configured verification failure

#### Scenario: Tamper test detects invalid digest
- **WHEN** tamper-and-verify intentionally alters raw `phone` or `row_digest` and the subsequent normal read triggers the configured verification failure
- **THEN** the tamper-and-verify step MUST be reported as a successful detection test

#### Scenario: Tamper is not detected
- **WHEN** the intentionally altered record is read without the configured digest failure being triggered
- **THEN** the tamper-and-verify step MUST fail with an assertion explaining that tampering was not detected

### Requirement: Lifecycle APIs enforce fixed security boundaries
Lifecycle APIs MUST operate only on `digest_user`, MUST accept only server-defined business fields, MUST limit tamper targets to `phone` and `row_digest`, and MUST use parameterized values for database operations.

#### Scenario: Request attempts arbitrary table or column
- **WHEN** a lifecycle request supplies an arbitrary table name or unsupported column
- **THEN** the system MUST reject it without executing client-defined SQL

#### Scenario: Request attempts unsupported tamper target
- **WHEN** a tamper request names a target other than a server-approved digest or encrypted field
- **THEN** the system MUST reject the request

#### Scenario: User initiates tamper
- **WHEN** the user selects the tamper action in the UI
- **THEN** the UI MUST require explicit confirmation before sending the mutation request

### Requirement: Datasource changes isolate lifecycle state
The lifecycle SHALL run against the selected datasource and SHALL prevent a record id or snapshot from one datasource being reused as the active lifecycle state of another datasource.

#### Scenario: User changes datasource
- **WHEN** the selected datasource changes
- **THEN** the UI MUST clear the active lifecycle state and run preflight for the newly selected datasource

#### Scenario: Application has one datasource
- **WHEN** only one datasource is available
- **THEN** the UI MUST display it as fixed context and MUST NOT require the user to select it

### Requirement: Secondary tests remain available
The UI SHALL preserve access to SQL, fixed Complex actions, and Multi-DS comparison in a secondary “more tests” area without making them lifecycle steps.

#### Scenario: User opens more tests
- **WHEN** the user opens the secondary test area
- **THEN** SQL navigation, supported fixed Complex actions, and Multi-DS comparison MUST remain accessible

#### Scenario: Default page is displayed
- **WHEN** the Playground main page first loads
- **THEN** the lifecycle workflow MUST be the primary content and secondary tests MUST NOT occupy the default main workflow

### Requirement: Lifecycle UI remains usable across viewport sizes
The UI SHALL show ordered step navigation, one current-step operation area, and the three-view result in both desktop and narrow layouts.

#### Scenario: Desktop viewport
- **WHEN** the available viewport is wide
- **THEN** steps MUST appear beside the main content and the three result views MUST be presented as columns

#### Scenario: Narrow viewport
- **WHEN** the available viewport is narrow
- **THEN** steps MUST remain navigable above the operation area and result views MUST stack vertically without losing content
