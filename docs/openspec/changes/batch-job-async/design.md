## Context

See `proposal.md` for motivation. Product design already approved at `docs/superpowers/specs/2026-09-10-batch-job-async-design.md`.

Observed today in `securt-kit-monitor`:

- `BatchJobStore` holds full `List<RowChange>` with before/after; `MAX_ROWS = 500`
- `MonitorOpsFacade.batchPreview` loads up to 501 rows into memory and stores the job
- `batchApply` updates row-by-row on the request thread with `SECURT_SKIP`
- UI (`app.js` batch section) expects preview `jobId` + sync apply; multi-select can keep only the last job id

Constraints: reuse `BatchGuard`, primary-key resolution, skip-comment, field strategies, and digest service; H2 + MySQL for database store DDL; default store remains memory for local demos.

## Goals / Non-Goals

**Goals:**

- Replace sync full-snapshot apply with sample preview + in-process async worker
- Implement both `MemoryBatchJobStore` and `JdbcBatchJobStore` behind one interface
- Support pause / resume / cancel at chunk boundaries; PARTIAL on row failures
- Update Monitor UI + APIs; verify ~10k rows on boot2 seed data

**Non-Goals:**

- Multi-instance lease / competing workers
- Million-row throughput tuning
- Multi-table single job
- Reworking data-init pages

## Decisions

### 1. JobStore interface + two implementations
- **Choice:** `BatchJobStore` API for create/get/updateStatus/appendFailure/listFailures; `memory` default, `database` required this change.
- **Alternatives:** memory-only (rejected — user requires DB mode); DB-only (rejected — default remains memory for demos).

### 2. In-process worker, chunk-boundary control
- **Choice:** single monitor-owned executor; pause/cancel flags checked after each keyset chunk.
- **Alternatives:** sync chained HTTP chunks (rejected — brittle); external queue (out of scope).

### 3. Keyset pagination on resolved PK
- **Choice:** `WHERE (userWhere) AND pk > ? ORDER BY pk LIMIT N`; assume single sortable PK column (same as today).
- **Alternatives:** OFFSET (poor at 10k+); temporary ID table (heavier).

### 4. Preview no longer creates applyable full jobs
- **Choice:** preview returns sample + count only; `POST /api/batch/jobs.json` creates the runnable job.
- **Alternatives:** keep preview-as-job token (confusing with async model).

### 5. Database tables on target DataSource
- **Choice:** `securtkit_monitor_batch_job` + `securtkit_monitor_batch_failure`; `ensureSchema()` for H2/MySQL; startup normalizes leaked RUNNING/PAUSING/CANCELING → PAUSED.
- **Alternatives:** sidecar DB (extra config); file store (weak for resume).

### 6. Legacy `apply.json`
- **Choice:** deprecate; return 400 with message pointing to jobs API (or keep tiny compatibility only if tests still need it briefly).
- **Alternatives:** keep dual paths long-term (maintenance cost).

### 7. Configuration under `securtkit.monitor.batch`
- Defaults: `store=memory`, `max-rows=20000`, `chunk-size=200`, `sample-size=20`, `max-failure-records=1000`, `memory-ttl-ms=3600000`.

## Risks / Trade-offs

- [Risk] Empty WHERE on huge tables → Mitigation: keep BatchGuard empty-where rejection; hard `max-rows` on create.
- [Risk] Database DDL permission missing → Mitigation: clear error; operators switch to `memory`.
- [Risk] Memory mode loses jobs on restart → Mitigation: documented; UI shows store mode.
- [Risk] Sign/encrypt compute differs from old preview path → Mitigation: reuse existing strategy/digest helpers from facade.
- [Risk] Multi-DS routing incomplete for batch → Mitigation: same datasource resolution as current monitor ops; document limitation if locator missing.

## Migration Plan

1. Ship APIs + worker behind Monitor; UI switches to jobs flow.
2. Deprecate sync apply in docs and API response.
3. Operators with existing bookmarks to apply: follow error guidance to recreate via jobs.
4. Rollback: revert monitor module; no business-schema migration except optional drop of system tables if database mode was enabled.

## Open Questions

None material for this phase; dialect quirks for MySQL vs H2 DDL can be resolved during implementation within H2+MySQL scope.
