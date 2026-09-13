## 1. Core SELECT parse

- [x] 1.1 Implement recursive column extraction in select expression visitors (`Function` / `TrimFunction` / `CastExpression` / `TranscodingFunction` and any node needed for IFNULL/COALESCE/NVL) and verify a unit parse test registers `user.phone` for `SELECT IFNULL(phone,'2') FROM user`
- [x] 1.2 Set result `columnName` to alias when present, else whitespace-normalized expression; set optional `resultColumnIndex`; verify alias vs no-alias parse tests
- [x] 1.3 For multi-column expressions take the first resolvable source column; verify `CONCAT(phone, id_card)` maps to the first encrypted column

## 2. JDBC decrypt path

- [x] 2.1 Enhance `ResultSetDecryptingProxy` matching (label, whitespace-normalized label, ordinal fallback, existing table.column) and verify IFNULL ciphertext cell decrypts under JDBC-style ResultSet wrap test
- [x] 2.2 Verify IFNULL NULL→literal default retains the literal under failure policy; bare `SELECT phone` regression still decrypts

## 3. MYBATIS decrypt path

- [x] 3.1 Extend `ResultDecryptHelper` name index for normalized expression labels and verify Map/entity results decrypt for function projections (source property / Map key)
- [x] 3.2 Verify MYBATIS-mode unit/integration coverage for IFNULL with and without alias where the module already tests decrypt

## 4. Monitor dual view

- [x] 4.1 Feed select-item decrypt mappings into Monitor post-decrypt (`querySql` / `MonitorResultDecryptor`) with field-name fallback retained; verify `plainRows` ≠ `cipherRows` for IFNULL projection on encrypted data
- [x] 4.2 Confirm row-verify path unchanged with existing Monitor row-verify tests still passing

## 5. Docs and polish

- [x] 5.1 Update `CAPABILITY-GAPS.md` and `MYBATIS-MODE-DESIGN.md` §8 for result-side function decrypt vs in-DB plaintext semantics; verify wording matches scenarios
- [x] 5.2 Update Monitor help text for function projections; smoke-check SQL parse / parameter-encrypt tools still work
