## 1. Lifecycle contracts and service scaffold

- [x] 1.1 Add failing unit tests for lifecycle request validation, `LifecycleSnapshot`, assertion, digest-verification, and structured-error serialization; verify the focused `securt-kit-playground` tests fail for the missing contracts
- [x] 1.2 Implement the lifecycle DTOs and `PlaygroundLifecycleService` scaffold, wire it into `PlaygroundEngine`, and verify the contract tests pass without changing existing CRUD/Digest behavior
- [x] 1.3 Add tests proving lifecycle requests reject `row_digest`, arbitrary fields, arbitrary table input, and unsupported tamper targets; verify each invalid request produces a 400-class response and executes no mutation

## 2. Preflight readiness

- [x] 2.1 Add preflight tests for datasource resolution, `digest_user` allow-list, required JDBC columns, encrypted `name`/`phone`, `phone` to `row_digest` rule, JDBC mode, and active `SECURT_SKIP`; verify each missing prerequisite is reported separately
- [x] 2.2 Implement non-mutating preflight using datasource metadata and resolved core registries, including selected-datasource normalization; verify ready and not-ready unit scenarios pass without inserting data
- [x] 2.3 Expose `GET /api/lifecycle/preflight.json` through `PlaygroundDispatcher` with authentication and datasource handling consistent with existing APIs; verify dispatcher tests cover authorized success, not-ready data, and malformed datasource input

## 3. Lifecycle database operations

- [x] 3.1 Add service tests for fixed `digest_user` insert, query, update, and verify operations plus not-found handling; verify SQL uses parameterized values and update never accepts `row_digest`
- [x] 3.2 Implement ordinary insert/update/business-read paths and common snapshot assembly, including explicit pass/fail assertions for plaintext equality, ciphertext difference, non-empty digest, and changed phone/digest after update; verify focused service tests pass
- [x] 3.3 Add a regression test demonstrating that a normal SELECT is not accepted as a raw snapshot, then implement `/* SECURT_SKIP */` raw SELECT and verify raw and business views follow distinct query paths
- [x] 3.4 Add tamper tests for `row_digest` and `phone`, including detection and non-detection outcomes; implement skip-comment tamper SQL with server-generated values and verify only approved targets can be changed
- [x] 3.5 Implement digest verification exception capture and inverted tamper-test semantics, preserving error category/detail; verify intact verify passes, ordinary invalid-digest read fails, detected tamper passes, and undetected tamper fails

## 4. Dispatcher API and integration coverage

- [x] 4.1 Map insert, query, update, verify, and tamper lifecycle POST routes in `PlaygroundDispatcher`; verify method checks, JSON validation, authentication, HTTP status, and `ApiResponse` wrapping in dispatcher tests
- [x] 4.2 Add Boot2 interceptor integration coverage for the complete lifecycle and verify plaintext insert produces ciphertext plus digest, normal query decrypts, update changes protected values, and tamper triggers FAIL_FAST
- [x] 4.3 Add equivalent Boot3 lifecycle integration coverage and verify it passes under the Java 17/Boot3 Maven profile
- [x] 4.4 Add multi-datasource integration coverage for preflight and operations on each configured datasource; verify datasource selection is honored and unknown datasource ids are rejected

## 5. Lifecycle-first browser UI

- [x] 5.1 Extend static-resource tests with failing assertions for the lifecycle header, six step controls, operation panel, three evidence views, reset control, and collapsed “more tests” area
- [x] 5.2 Replace `index.html` tab-first markup with the lifecycle-first semantic structure while retaining login, datasource, Monitor, SQL, Complex, and Multi-DS controls; verify static-resource structure tests pass
- [x] 5.3 Refactor `app.js` into a browser-local lifecycle state machine with preflight, transition guards, record-id propagation, per-step snapshots, reset, datasource isolation, loading/error states, and tamper confirmation; verify deterministic state transitions through extracted pure-function tests or an equivalent JavaScript test harness
- [x] 5.4 Implement field-oriented snapshot and assertion rendering with expandable raw JSON, ensuring failed calls preserve inputs and prior snapshots; verify rendering tests cover passed, failed, digest-failure, and tamper-detected responses
- [x] 5.5 Redesign `style.css` for desktop navigation/content layout, three evidence columns, status indicators, and narrow-screen stacking; verify at desktop and narrow viewport widths that controls remain usable and no evidence content is clipped
- [x] 5.6 Move existing SQL, Complex, and Multi-DS interactions into the collapsed secondary area and verify their existing API calls and Monitor link still work

## 6. Documentation and final verification

- [x] 6.1 Update Playground documentation and relevant test-app READMEs with the six-step workflow, prerequisites, three evidence views, reset semantics, datasource behavior, and “more tests” location; verify all referenced paths and configuration keys exist
- [x] 6.2 Run `mvn -pl securt-kit-playground -am test` and verify all module and dependency tests pass
- [x] 6.3 Run the focused Boot2, Boot3, and multi-datasource lifecycle integration suites under their required Maven profiles and verify all lifecycle scenarios pass
- [x] 6.4 Perform a browser smoke test of `/playground/` through all six steps on one single-datasource app and one multi-datasource app, verifying the three evidence views, expected tamper detection, responsive layout, retained secondary tests, and absence of browser console errors
