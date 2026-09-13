## 1. Job model and stores

- [x] 1.1 Add batch job domain types (status enum, job record, failure record) and `BatchJobStore` interface; verify compile and a minimal unit test constructs a READY job
- [x] 1.2 Implement `MemoryBatchJobStore` (TTL, create/get/update, failure cap) with unit tests for expire, status transition helpers, and failure-detail limit
- [x] 1.3 Implement `JdbcBatchJobStore` with H2/MySQL `ensureSchema` for `securtkit_monitor_batch_job` / `_failure`, CRUD, and startup normalization of leaked RUNNING/PAUSING/CANCELING → PAUSED; verify with H2 unit/IT tests including resume-after-reload
- [x] 1.4 Wire store selection from `securtkit.monitor.batch.store` (default `memory`) on Monitor properties/auto-config; verify wrong/missing mode falls back or fails clearly in a config test

## 2. Preview and job APIs

- [x] 2.1 Change `batchPreview` to sample-only + `totalEstimated` (no full-row job snapshot); verify unit/facade tests assert sample size bound, no mutation, and absence of applyable full row list
- [x] 2.2 Add create-job API that rejects when estimate > `max-rows` and schedules work; verify over-limit returns 400 and under-limit returns `jobId`
- [x] 2.3 Add status / pause / resume / cancel endpoints on `MonitorDispatcher`; verify method routing, auth, and status payload fields (`processed`, `succeeded`, `failed`, `totalEstimated`)
- [x] 2.4 Deprecate legacy `apply.json` (guide error or documented compatibility only); verify UI path and primary tests no longer require sync full-snapshot apply

## 3. Batch worker

- [x] 3.1 Implement keyset chunk loader + encrypt/decrypt/sign compute reusing existing strategy/digest helpers; verify chunk SQL uses skip-comment and advances cursor in unit tests with mocked JDBC or H2
- [x] 3.2 Implement pause/resume/cancel at chunk boundaries and PARTIAL vs SUCCEEDED/FAILED rules; verify pause keeps cursor, cancel stops further writes, mixed row failures → PARTIAL
- [x] 3.3 Hook worker executor into Monitor lifecycle (start on create, recover PAUSED jobs only on explicit resume); verify no double-run of the same job id in concurrent create tests

## 4. Monitor UI

- [x] 4.1 Update batch UI to show sample preview + estimate, start async job, and poll status; verify static/resource or JS-facing tests cover progress fields rendering
- [x] 4.2 Add pause / resume / cancel controls gated by status; verify buttons enable/disable rules for RUNNING/PAUSED/terminal states
- [x] 4.3 Fix multi-table selection to keep independent job ids/progress per table; verify selecting two tables does not drop the first job id

## 5. Capacity verification and docs

- [x] 5.1 Add monitor/boot2 coverage that runs an async job against ~10k `user` rows (seed or test insert) to a terminal status; verify completion under `max-rows` with progress monotonicity
- [x] 5.2 Document `securtkit.monitor.batch.*`, store modes, and deprecated apply path in Monitor/USAGE-related docs; verify referenced keys match `MonitorProperties`
- [x] 5.3 Run `mvn -pl securt-kit-monitor,securt-kit-test-boot2 -am test` (or project-equivalent focused suite) and verify batch-related tests pass
