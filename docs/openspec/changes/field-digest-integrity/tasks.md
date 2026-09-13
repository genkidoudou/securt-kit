## 1. Baseline verification (existing core / mybatis)

- [x] 1.1 Run `mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" test` and verify digest-related unit tests pass (`DigestConfig*`, `HmacSha256*`, `DigestSqlRewriter*`, `DigestService*`, `DigestWriteSupport*`, `DigestReadSupport*`)
- [x] 1.2 Run `mvn -pl securt-kit-mybatis -am "-Dmaven.test.skip=false" "-Dtest=DigestParamHelperTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` and verify 3/3 pass
- [x] 1.3 Spot-check startup validation scenarios (target encrypted conflict, blank source fields) still fail init via `DigestConfigRegistryTest` and verify tests remain green

## 2. Boot2 schema and configuration

- [x] 2.1 Add `row_digest` column to Boot2 test schema / DDL used by encryptor IT tables (quote `"user"` on H2 if needed) and verify schema loads without SQL errors
- [x] 2.2 Add digest YAML to Boot2 test config (`digest-strategy`, `digest-hmac-key`, `digest-partial-update: RELOAD`, table `digest` rule for phone and/or id_card → `row_digest`) and verify app context still starts

## 3. Boot2 integration tests

- [x] 3.1 Add `DigestIntegrityIT` (package alongside existing Boot2 tests): INSERT without `row_digest` persists non-null digest; verify with DB read of ciphertext row / digest column
- [x] 3.2 Cover UPDATE phone-only with `RELOAD`: after update, digest matches recomputation from full plaintext sources; verify assertion fails if RELOAD skipped
- [x] 3.3 Cover `verify-on-read=true` + tampered `row_digest`: `FAIL_FAST` throws `DigestMismatchException`; optionally assert `FALLBACK` does not throw; verify with `mvn -pl securt-kit-test-boot2 -am "-Dmaven.test.skip=false" "-Dtest=DigestIntegrityIT" "-Dsurefire.failIfNoSpecifiedTests=false" test`

## 4. Documentation

- [x] 4.1 Update `docs/USAGE.md` with digest configuration example including `digest-hmac-key` and table `digest` list; verify doc section is linked or discoverable from `docs/INDEX.md` if needed
- [x] 4.2 Update `docs/superpowers/specs/2026-09-05-field-digest-integrity-design.md` status to reflect completion after IT/docs land; verify status line matches reality

## 5. P2 hardening (optional but recommended before archive)

- [x] 5.1 Trim digest `source-fields` entries at startup (or reject untrimmed) and add/adjust registry test; verify blank-adjacent `" phone "` behavior is defined and tested
- [x] 5.2 Confirm multi-row INSERT rewrite remains skipped or indexes are fixed; verify with unit test that multi-row VALUES does not bind wrong digest indexes
- [x] 5.3 If time permits, add one RELOAD happy-path unit/IT with real Connection SELECT of missing encrypted column; verify decrypt+merge digest correctness
