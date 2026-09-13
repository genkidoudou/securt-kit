## 1. Catalog & DTOs

- [x] 1.1 Extend `ScenarioDescriptor` (and any list serialization) with `sampleParams`, `exampleSqlPlain`, `exampleSqlCipher`, `sampleHint`; verify existing scenario list tests still pass with null/empty defaults
- [x] 1.2 Add request/response DTOs for seed-preview and sql-run (`sql`, `useSkip`, `datasourceId`; `userRows`/`ordersRows`/`encryptNote`/`limits`; `rows`/`sqlMeta`); verify they serialize in a unit or dispatcher smoke test

## 2. Engine: seed-preview & sql-run

- [x] 2.1 Implement seed-preview in `PlaygroundEngine` (SECURT_SKIP when enabled, default limit ≤50, hard cap 100 for `user`/`orders`); verify unit/integration test returns both arrays and `encryptNote`
- [x] 2.2 Implement SELECT-only sql-run validation (reject empty, `;`, non-SELECT after leading comments, write/DDL heads); verify tests cover accept SELECT and reject `;` / `UPDATE`
- [x] 2.3 Implement useSkip prepend of configured skip token when enabled, and clear error when useSkip but skip-comment disabled; verify `sqlMeta.sql` / `skipped` and error path
- [x] 2.4 Wire `GET /api/scenarios/seed-preview.json` and `POST /api/scenarios/sql-run.json` in `PlaygroundDispatcher` with same auth as other scenario APIs; verify dispatcher tests for 200/400 paths; confirm `scenarios/run.json` unchanged

## 3. Boot runners: sample metadata

- [x] 3.1 Enrich boot2/boot3 `MpPlaygroundScenarioRunner.list()` with sampleParams and plain/cipher example SQL per design table (single-eq, join, alias, like, func); verify `GET /api/scenarios.json` includes non-empty samples for `single-eq`

## 4. UI

- [x] 4.1 Add dual always-visible user/orders preview panels + refresh + encryptNote on complex-query tab; verify static markup/ids exist and preview loads on tab enter / datasource change
- [x] 4.2 Add SQL editor, plain/cipher example toggles, SECURT_SKIP checkbox, execute button; on scenario select load `sampleParams` + `exampleSqlPlain`; verify selection populates editor
- [x] 4.3 Map sql-run results into evidence panels per spec (skip vs non-skip); keep scenario run filling all three panels; verify UI behavior via manual smoke or existing JS test hooks if present

## 5. Docs & verification

- [x] 5.1 Update `docs/PLAYGROUND.md` with dual-table preview and sql-run/SECURT_SKIP notes; verify section is discoverable from INDEX or existing Playground links
- [x] 5.2 Run playground module tests (and relevant boot smoke if available); verify seed-preview, sql-run guardrails, and catalog sample fields pass
