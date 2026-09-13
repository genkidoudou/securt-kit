## 1. Schema and test-app configuration

- [x] 1.1 Add `playground_person` DDL (id, name, phone, id_card, age, row_digest) to Boot2/Boot3 and multi-DS test initializers/schema scripts; verify tables exist after app init
- [x] 1.2 Update each Playground-enabled `application.yml`: encrypt `phone`/`id_card` on `playground_person`, digest `source-fields: [phone, id_card]` → `row_digest`, whitelist `playground_person`; verify config loads without startup errors
- [x] 1.3 Update `securtkit.playground.allowed-tables` (module defaults + test overrides) to include `playground_person`; verify readiness can see the table as allowed

## 2. Person service and APIs

- [x] 2.1 Add person request/response DTOs (`datasourceId`, filters, `view`, create/update fields) and wire `PlaygroundPersonService` for fixed table `playground_person`; verify unit tests can construct requests
- [x] 2.2 Implement create/update/delete through normal interceptor connection; verify create stores encrypted phone/id_card and non-empty `row_digest`, and client-supplied digest is ignored or rejected
- [x] 2.3 Implement `list` with `view=business` (decrypt) and `view=raw` (`SECURT_SKIP`); verify business plaintext equals written values and raw ciphertext differs
- [x] 2.4 Implement filters for `name` (equality), `phone`, `idCard` (plaintext → encrypted equality); verify hit/miss unit or service tests
- [x] 2.5 Register `/api/person/list|create|update|delete.json` in `PlaygroundDispatcher` / engine; verify routes respond with `ApiResponse` envelope
- [x] 2.6 Add person readiness/preflight (table, whitelist, encrypt, digest) and map validation/not-found errors to clear Chinese messages; verify missing-config and missing-id failure cases

## 3. Maintenance UI

- [x] 3.1 Replace lifecycle wizard `index.html`/CSS/JS with maintenance desk: filters, business/raw toggle, table, create/edit modal, delete confirm; verify default `/playground/` shows maintenance UI not lifecycle steps
- [x] 3.2 Wire UI to person APIs including datasource select and readiness banner; verify create → business list plaintext → raw list ciphertext → filter by phone/idCard → update → delete refresh
- [x] 3.3 Disable edit/delete in raw view in the UI; verify mutate actions are blocked until returning to business view

## 4. Cleanup, docs, and tests

- [x] 4.1 Remove or unhook lifecycle UI dependencies; delete or mark unused `/api/lifecycle/*` if low cost; verify no default navigation reaches the wizard
- [x] 4.2 Replace lifecycle-focused playground unit/IT coverage with person CRUD scenarios (encrypt at rest, dual views, filters, delete); verify `securt-kit-playground` tests and relevant Boot2/Boot3 ITs pass
- [x] 4.3 Update `docs/PLAYGROUND.md` and related test README pointers to describe person CRUD + dual views; verify docs match the shipped UI
- [x] 4.4 Mark product design `docs/superpowers/specs/2026-09-10-playground-person-crud-design.md` status as implemented (or equivalent) after apply completes; verify status line is updated
