## 1. Phase A — Config overview

- [x] 1.1 Extend `ConfigResponse` / `MonitorEngine.getConfig()` with mode, skip-comment, digest globals (strategy, partial-update, verify-on-read, failure-policy, hmacKeyConfigured boolean) and per-table digest rules; verify unit or smoke JSON never contains raw HMAC key
- [x] 1.2 Add `MonitorProperties.tablePrimaryKeys` and `GET /api/primary-key.json` resolving config → TableInfo → none; verify config wins over TableInfo when both exist
- [x] 1.3 Update monitor config Tab UI to render encrypt fields, digest rules, and global flags; verify page shows digest section in `securt-kit-test-boot2`

## 2. Phase C — Single-value and single-row tools

- [x] 2.1 Add `POST /api/digest.json` (`sign`/`verify`) wired through Dispatcher and Engine using configured digest strategy; verify sign returns digest and verify mismatch returns `verified=false`
- [x] 2.2 Add `POST /api/row/verify.json` using PK resolver + row load; verify structured JSON on success/failure without killing the monitor session
- [x] 2.3 Enhance crypto Tab UI for sign/verify and add single-row verify Tab; verify manual smoke against `digest_user`

## 3. Phase B — Batch preview / apply

- [x] 3.1 Implement WHERE validation (reject multi-statement / empty WHERE by default) and configured-table allow-list helpers; verify unit tests cover reject cases
- [x] 3.2 Implement `POST /api/batch/preview.json` with in-memory job store (TTL ~10m, max 500 rows) returning before/after without writes; verify DB unchanged after preview
- [x] 3.3 Implement `POST /api/batch/apply.json` updating by PK from valid jobId with partialSuccess reporting; verify expired jobId returns 400 and apply row count ≤500
- [x] 3.4 Add 刷数作业 Tab (preview table → confirm → apply); verify end-to-end on boot2 H2 for encrypt or sign op

## 4. Phase D — SELECT dual-view and encrypt-sql

- [x] 4.1 Harden `query-sql` to SELECT-only; verify UPDATE/DELETE return client error
- [x] 4.2 Return `cipherRows` + `plainRows` using skip-comment (or equivalent) path; verify explicit error when cipher path unavailable
- [x] 4.3 Enhance SQL query / encrypt-sql UI for dual-view and encrypted-SQL display; verify SELECT dual-view smoke with skip-comment enabled

## 5. Docs and test apps

- [x] 5.1 Document `table-primary-keys`, batch limits, SELECT-only, and skip-comment prerequisite in MONITOR-SERVLET / USAGE (and INDEX if needed); verify links resolve
- [x] 5.2 Enable sample `table-primary-keys` (and skip-comment if missing) in test-boot2 YAML used for monitor smoke; verify config/primary-key APIs work against that app
- [x] 5.3 Mark `docs/superpowers/specs/2026-09-06-monitor-ops-console-design.md` status as implemented after phase D smoke; verify status line matches reality
