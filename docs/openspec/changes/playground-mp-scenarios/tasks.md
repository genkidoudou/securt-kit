## 1. Person dual-mode (P0)

- [x] 1.1 Add failing tests that person preflight stays ready under MYBATIS when other checks pass, and create/list plain≠raw under both modes; verify the focused playground tests fail before implementation
- [x] 1.2 Implement explicit encrypt+digest write helpers and raw+decrypt business reads (skip-comment writes/reads) in `PlaygroundPersonService`; verify unit tests pass without requiring JDBC interceptor encryption
- [x] 1.3 Soften or remove the hard `jdbc-mode` preflight failure while still exposing current mode as info; verify MYBATIS ready and missing-field not-ready cases
- [x] 1.4 Add Boot2 (and Boot3 if profile-ready) IT coverage for person create/list under JDBC and MYBATIS configs; verify plaintext business view and ciphertext raw view

## 2. Scenario API and UI shell (P0)

- [x] 2.1 Introduce scenario DTOs, `PlaygroundScenarioRunner` SPI, list/run APIs on Engine/Dispatcher; verify dispatcher tests for auth, missing runner unavailable catalog, and run-without-runner error
- [x] 2.2 Add Playground UI dual tabs (人员维护 | 复杂查询) with scenario list/params/run placeholders and three result panels; verify static-resource tests assert tab and three-panel markers
- [x] 2.3 Wire optional runner injection from host configuration without adding MP compile dependency to `securt-kit-playground`; verify module compiles without mybatis-plus on the playground classpath

## 3. MP runner core scenarios (P1)

- [x] 3.1 Implement Boot2 `PlaygroundScenarioRunner` with `single-eq`, `join-user-orders`, and `like-phone` using MP against `user`/`orders`, returning plain/cipher/sqlMeta; verify boot2 tests assert three-part responses when seed data exists
- [x] 3.2 Implement cipher evidence via skip-comment (or equivalent) and document LikePatternHandler semantics in `sqlMeta` for LIKE; verify cipher columns differ from plain for encrypted fields on happy-path rows
- [x] 3.3 Connect complex-query UI to list/run APIs and render three panels; verify resource or smoke assertions that run payload fields are referenced in JS

## 4. Alias, function, Boot3 / multi-DS (P2)

- [x] 4.1 Add `column-alias` and `table-alias` scenarios with sqlMeta explanations; verify structural tests for three-part responses
- [x] 4.2 Add `func-on-cipher` with `sqlMeta.limitation` and allow zero-row success; verify limitation field is present
- [x] 4.3 Port or share the runner for Boot3 (and multi-DS if in scope) and verify at least one scenario run smoke per host
- [x] 4.4 Update `docs/PLAYGROUND.md` for dual tabs, dual-mode person desk, and scenario catalog; verify documented paths and config keys exist

## 5. Final verification

- [x] 5.1 Run `mvn -pl securt-kit-playground,securt-kit-test-boot2 -am "-Dmaven.test.skip=false" test` (or project-equivalent) and verify playground/scenario-related tests pass
