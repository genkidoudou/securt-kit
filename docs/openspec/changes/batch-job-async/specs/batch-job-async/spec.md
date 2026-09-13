## Purpose

Defines Monitor async batch-job behavior for large-table encrypt/decrypt/sign: sample preview, dual job stores, pause/resume/cancel, keyset chunked apply, and partial-success outcomes.

## ADDED Requirements

### Requirement: Sample preview without full row snapshot
The system SHALL provide an authenticated batch preview that returns only a bounded sample of before/after row projections plus an estimated matching row count. Preview MUST NOT persist a full matched-row change set for later synchronous apply, and MUST NOT mutate business table rows.

#### Scenario: Preview returns sample and estimate
- **WHEN** an authenticated client requests batch preview for `op` in `{encrypt, decrypt, sign}` with a valid table and WHERE
- **THEN** the response includes sample before/after rows limited by configured sample size
- **AND** the response includes `totalEstimated` for the matching filter
- **AND** no business rows are updated

#### Scenario: Preview rejects invalid filter
- **WHEN** preview is requested with an empty WHERE (and empty-where is not enabled) or multi-statement SQL
- **THEN** the API returns a client error
- **AND** no job is created for apply

### Requirement: Async job creation with hard row limit
The system SHALL create an asynchronous batch job from the same table/WHERE/op constraints used for preview. Job creation MUST reject filters whose estimated row count exceeds the configured `max-rows` (default 20000). Creating a job MUST schedule background execution without requiring the HTTP request to perform all updates.

#### Scenario: Create job under limit
- **WHEN** an authenticated client creates a batch job and `totalEstimated` is less than or equal to `max-rows`
- **THEN** the API returns a `jobId` and an initial non-terminal status
- **AND** updates proceed asynchronously outside that request's critical path

#### Scenario: Create job over hard limit
- **WHEN** an authenticated client creates a batch job and `totalEstimated` exceeds `max-rows`
- **THEN** the API returns a client error
- **AND** no job is started

### Requirement: Keyset chunked execution without full-table cache
The worker SHALL process matching rows in primary-key keyset chunks (`pk > cursor ORDER BY pk LIMIT chunkSize`) and MUST NOT load the entire matching result set into the job store as before/after snapshots. Read and write paths used for raw ciphertext/digest values MUST use the configured skip-comment prefix so interceptor re-encryption does not corrupt already-computed values.

#### Scenario: Chunk advances cursor
- **WHEN** a running job processes a non-final chunk successfully
- **THEN** the job cursor advances to the highest primary key processed in that chunk
- **AND** progress counters (`processed`, `succeeded`, and/or `failed`) increase accordingly

#### Scenario: Job completes when no more rows
- **WHEN** a running job's next keyset page returns no rows
- **THEN** the job reaches a terminal success-class status (`SUCCEEDED` if failed count is zero, otherwise `PARTIAL`)

### Requirement: Pause resume and cancel controls
The system SHALL allow authenticated operators to pause, resume, and cancel a non-terminal job. Pause and cancel MUST take effect at chunk boundaries so the persisted cursor remains consistent. Resume MUST continue from the stored cursor without reprocessing already-succeeded rows under normal keyset semantics.

#### Scenario: Pause after chunk
- **WHEN** an operator requests pause while a job is `RUNNING`
- **THEN** the job enters a pausing intermediate state if needed and becomes `PAUSED` after the current chunk finishes
- **AND** the stored cursor reflects rows already processed

#### Scenario: Resume from paused
- **WHEN** an operator resumes a `PAUSED` job
- **THEN** the job returns to a runnable state and continues from the stored cursor

#### Scenario: Cancel stops further writes
- **WHEN** an operator cancels a running or paused job
- **THEN** after the current chunk (if any) the job becomes `CANCELLED`
- **AND** no further chunks are written
- **AND** rows already updated remain updated

### Requirement: Row failures yield partial success
The system SHALL record per-row update failures up to a configured failure-detail limit and MUST continue processing remaining rows. A job that finishes all pages with one or more row failures MUST end as `PARTIAL`, not `FAILED`. `FAILED` is reserved for fatal conditions that prevent continuation (for example missing primary key resolution after start, worker crash, or store unavailable).

#### Scenario: Mixed row failures
- **WHEN** some row updates fail and others succeed during execution
- **THEN** failed primary keys and messages are retained up to the configured detail limit
- **AND** processing continues for subsequent rows
- **AND** if all pages complete, the terminal status is `PARTIAL`

### Requirement: Dual job store modes
The system SHALL support configurable job persistence modes `memory` and `database`, defaulting to `memory`. Memory mode MAY discard jobs on process restart. Database mode MUST persist jobs and failure details in target-database system tables and MUST survive process restart for `PAUSED` jobs. On monitor startup in database mode, any leaked `RUNNING` / `PAUSING` / `CANCELING` jobs MUST be normalized to `PAUSED`.

#### Scenario: Default memory store
- **WHEN** monitor batch store is not overridden
- **THEN** jobs are stored in process memory
- **AND** a process restart makes prior job ids unavailable

#### Scenario: Database store resume after restart
- **WHEN** store mode is `database` and a job was `PAUSED` before process restart
- **THEN** after restart the job remains queryable by id
- **AND** an operator can resume it from the stored cursor

#### Scenario: Leaked running normalized on startup
- **WHEN** store mode is `database` and a job was `RUNNING` when the process died
- **THEN** on next startup that job is set to `PAUSED`
- **AND** further progress requires an explicit resume or cancel

### Requirement: Job status polling API
The system SHALL expose an authenticated status API for a job id returning status, progress counters, estimated total, and recent failure details sufficient for UI polling.

#### Scenario: Poll running job
- **WHEN** an authenticated client requests status for an existing job id
- **THEN** the response includes status, `processed`, `succeeded`, `failed`, and `totalEstimated`

#### Scenario: Unknown job
- **WHEN** an authenticated client requests status for an unknown or expired memory job id
- **THEN** the API returns a client error

### Requirement: UI drives async job lifecycle
The Monitor batch UI MUST present sample preview results, start an async job, poll progress, and expose pause/resume/cancel controls according to job status. When multiple tables are selected, the UI MUST track an independent job per table rather than retaining only the last job id.

#### Scenario: Multi-table independent jobs
- **WHEN** an operator starts batch operations for more than one table
- **THEN** each table has its own job id and progress presentation

### Requirement: Legacy sync apply is not the primary path
The system MUST NOT require the legacy synchronous full-snapshot `apply` flow for the async batch UI. If the legacy apply endpoint remains temporarily, it MUST either reject with guidance to use the jobs API or be clearly documented as deprecated for small compatibility cases only.

#### Scenario: Primary UI does not depend on full snapshot apply
- **WHEN** an operator completes sample preview and confirms execution in the Monitor UI
- **THEN** execution is started via the async jobs API
- **AND** the UI does not wait for a single HTTP call to update all matched rows
