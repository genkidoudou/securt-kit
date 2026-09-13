## Context

See `proposal.md` for motivation and `specs/playground-lifecycle/spec.md` for observable behavior.

`securt-kit-playground` already has a Servlet-independent `PlaygroundEngine`, a path-switching `PlaygroundDispatcher`, generic CRUD/Digest request DTOs, and static HTML/CSS/JavaScript. The four test applications provide thin javax/jakarta Servlet adapters and datasource wiring. The current engine executes JDBC directly; raw query calls the same query path as business reads and only emits `rawHint`, while digest tampering already prefixes SQL with `/* SECURT_SKIP */`.

The module depends on `securt-kit-core`, whose `TableConfigRegistry` and `DigestConfigRegistry` expose resolved encryption and digest rules. `ConfigInitializer.shouldSkipByComment` can verify whether the configured `SECURT_SKIP` token is active. The lifecycle is therefore a JDBC-interceptor demonstration and must report not-ready rather than claim success when that path or its bypass is unavailable.

## Goals / Non-Goals

**Goals:**

- Add a cohesive lifecycle service behind the existing Engine/Dispatcher boundary without coupling the shared module to javax or jakarta Servlet APIs.
- Make backend assertions authoritative so the UI renders evidence instead of inferring security behavior from unrelated responses.
- Preserve the old endpoints during the redesign for secondary tests and compatibility.
- Keep lifecycle session state entirely in the browser; make every backend operation stateless and keyed by datasource plus record id.

**Non-Goals:**

- Generalize lifecycle APIs to arbitrary tables or schemas.
- Demonstrate MYBATIS mode through direct JDBC operations; preflight rejects a configuration that cannot exercise the required JDBC path.
- Add persistence for workflow progress or delete records on reset.
- Change encryption, digest, or failure-policy implementation in core.

## Decisions

### 1. Introduce a focused lifecycle service and DTOs

Add a `PlaygroundLifecycleService` owned by `PlaygroundEngine`, with request DTOs for insert, record operations, update, and tamper plus a common `LifecycleSnapshot` response model. `PlaygroundDispatcher` maps the six `/api/lifecycle/*` routes and continues wrapping data in the existing `ApiResponse`.

The lifecycle service owns fixed `digest_user` SQL, snapshot collection, and assertions. Existing generic CRUD/Digest methods remain available for “more tests” but are not composed by JavaScript.

**Why:** one backend operation can return mutually consistent plaintext, raw, and business views and can classify assertion failures correctly.

**Alternative considered:** orchestrate existing CRUD, raw-query, and digest endpoints in the browser. Rejected because the current raw endpoint is not truly raw, multiple calls can observe different state, and the browser would need security-specific assertion logic.

### 2. Use stateless endpoint-specific operations

Routes:

```text
GET  /api/lifecycle/preflight.json?datasourceId=...
POST /api/lifecycle/insert.json
POST /api/lifecycle/query.json
POST /api/lifecycle/update.json
POST /api/lifecycle/verify.json
POST /api/lifecycle/tamper.json
```

Insert accepts `datasourceId` and the four business fields. Query and verify accept `datasourceId` and `recordId`. Update additionally accepts a server-filtered business-field map. Tamper accepts only an enum target (`row_digest` or `phone`) and `recordId`; the server supplies the tampered value. `name` is excluded because the configured digest is based only on `phone`, so changing `name` cannot reliably prove digest detection.

There is no reset route. JavaScript stores the current datasource, record id, step states, input baselines, and snapshots in memory. Changing datasource or resetting replaces that state and reruns preflight.

**Why:** backend session state would complicate auth sessions, multiple browser tabs, and multi-datasource isolation without adding test value.

**Alternative considered:** a single action endpoint with an `action` discriminator. Rejected because endpoint-specific request validation and tests are clearer for the fixed lifecycle.

### 3. Preflight combines configuration and schema checks

Preflight performs non-mutating checks:

- selected datasource resolves;
- `digest_user` is in `allowed-tables`;
- JDBC metadata contains `id`, `name`, `phone`, `age`, `email`, and `row_digest`;
- `TableConfigRegistry` reports `name` and `phone` encryption for the datasource;
- `DigestConfigRegistry` contains a rule whose source includes `phone` and whose target is `row_digest`;
- `EncryptModeHolder` reports JDBC mode;
- `ConfigInitializer.shouldSkipByComment("/* SECURT_SKIP */ SELECT 1")` is true.

The response includes one result per prerequisite and an aggregate `ready` value. Missing optional secondary-test capabilities do not make lifecycle preflight fail.

**Why:** checking concrete resolved registries is more accurate than parsing YAML in the Playground.

**Alternative considered:** submit a probe record during preflight. Rejected because opening the page must not mutate test data.

### 4. Collect raw and business views through separate explicit SQL paths

`loadRawRow` prefixes its SELECT with `/* SECURT_SKIP */`; `loadBusinessRow` uses an ordinary SELECT. Both use the selected datasource, a fixed table and columns, and a parameterized id.

Lifecycle insert and update run through ordinary SQL so securt-kit performs encryption and digest generation. Tamper uses skip-comment SQL and a server-selected fixed column. A transaction is used for each primary mutation; post-mutation snapshot reads occur after commit so they exercise the same path users will test manually.

**Why:** the distinction is observable and testable, and reuses the configured bypass already used by digest tampering.

**Alternative considered:** unwrap the interceptor datasource or introduce a second raw datasource. Rejected because it adds driver-specific coupling and duplicate datasource configuration.

### 5. Make snapshot assertions explicit values

`LifecycleSnapshot` includes:

- `step`, `status`, `recordId`, and `message`;
- `requestPlaintext`, `rawDatabaseRow`, and `businessRow`;
- `digestVerification` with status, source fields, digest field, and failure detail;
- ordered `assertions`, each with id, expected, actual summary, pass/fail, and message;
- structured `error` with category and detail.

The service marks a snapshot `PASSED` only when all assertions for that step pass. A successful SQL update with unchanged encryption evidence is therefore a failed lifecycle step.

Tamper has inverted success semantics: catching the configured digest verification failure produces a passed `tamper-detected` assertion; a normal read produces a failed `tamper-detected` assertion. The service catches the verification exception inside this test action and does not weaken core failure policy.

**Why:** a stable assertion model lets the page explain exactly which guarantee was or was not observed.

**Alternative considered:** infer pass/fail from `ApiResponse.success`. Rejected because transport/database success is not proof of encryption or integrity behavior.

### 6. Replace tab-first UI with a browser state machine

`index.html` defines:

- header context and controls;
- six-step navigation;
- one operation panel whose form changes with the active step;
- three evidence cards with optional raw JSON details;
- a collapsed “more tests” section containing the existing SQL, Complex, and Multi-DS controls.

`app.js` keeps a state object with prerequisites, active step, record id, original/latest plaintext, and snapshots by step. Transition guards derive which steps are executable. Failed operations preserve all prior state and inputs.

Desktop CSS uses a navigation/content grid and three evidence columns. A media query moves navigation above content with horizontal overflow and stacks evidence cards.

**Why:** explicit state transitions prevent invalid sequences and make a single record’s history understandable.

**Alternative considered:** retain operation tabs and add more result panels. Rejected because it does not establish the lifecycle as the primary mental model.

### 7. Test the service separately from real interceptor integration

Unit tests in `securt-kit-playground` cover route validation, DTO shape, preflight failure reporting, transition-independent service behavior, fixed tamper targets, and static-resource structure. Integration tests in Boot2 and Boot3 use their configured interceptor datasource to prove actual ciphertext, decryption, digest regeneration, and fail-fast tamper detection. Multi-datasource tests prove datasource selection and state isolation.

**Why:** plain H2 unit tests cannot prove interceptor behavior, while testing all validation only through boot applications would be slow and difficult to diagnose.

## Risks / Trade-offs

- **[Risk] Global core registries may fall back from an unknown datasource id to default configuration** → Normalize datasource ids consistently with existing managers and make preflight compare the selected datasource’s resolved rule set explicitly.
- **[Risk] Skip-comment configuration is disabled or customized** → Preflight verifies the exact `SECURT_SKIP` marker before enabling lifecycle operations and reports a concrete configuration error.
- **[Risk] Digest FAIL_FAST throws before a business row can be returned** → Convert the exception to structured verification evidence at the lifecycle boundary; preserve the original category and message.
- **[Risk] Existing consumers rely on old tabs or endpoints** → Keep old API routes and controls in “more tests” during this change; removal would require a separate deprecation change.
- **[Risk] Generated ciphertext could be nondeterministic** → Assertions compare ciphertext to plaintext and before/after evidence, not a fixed cipher value.
- **[Trade-off] Browser refresh loses progress** → Accepted because lifecycle state is a disposable manual-test aid; database records remain available for a new flow.
- **[Trade-off] Lifecycle is fixed to `digest_user`** → Accepted to keep assertions precise and prevent the page from becoming a generic database editor.

## Migration Plan

1. Add lifecycle DTOs/service and unit tests without changing the default page.
2. Add Dispatcher routes and Boot2 integration coverage for preflight and the complete lifecycle.
3. Add equivalent Boot3 and multi-datasource integration coverage.
4. Replace static page structure, styling, and browser logic; retain old actions under “more tests”.
5. Update Playground documentation and run module plus Boot2/Boot3 verification.

Rollback consists of restoring the previous static resources and leaving the additive lifecycle routes unused. Existing CRUD, Digest, Complex, datasource, authentication, and Monitor-link routes remain available throughout.
