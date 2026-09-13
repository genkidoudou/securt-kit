## 1. Shared decrypt helper

- [x] 1.1 Add a Monitor helper that, given datasource id + optional table hint + row maps, decrypts values whose column labels match configured encrypt fields; verify unit tests cover encrypted vs non-encrypted columns
- [x] 1.2 Document/implement single-table happy path and best-effort label matching for ambiguous selects; verify non-matching labels are left unchanged

## 2. SQL query dual view

- [x] 2.1 Change `querySql` to obtain raw/cipher rows then derive `plainRows` via the helper (prefer one raw read when practical); verify under simulated MYBATIS mode plain ≠ cipher for encrypted fields
- [x] 2.2 Keep skip-comment / raw-read failure messaging clear when cipher cannot be guaranteed; verify disabled skip-comment still surfaces a readable error if still required by the chosen raw path
- [x] 2.3 Add/adjust Monitor tests so JDBC and MYBATIS configurations both expose correct dual views for a simple `SELECT` on an encrypted table

## 3. Row verify

- [x] 3.1 Before digest compute/verify, decrypt configured encrypted digest source fields on the loaded row; verify intact ciphertext+digest row passes under MYBATIS-style (no JDBC decrypt) reads
- [x] 3.2 Keep missing-rule and mismatch failure behavior; verify tampered digest still fails

## 4. Docs and help

- [x] 4.1 Update Monitor help / project docs text that currently implies SQL decrypt requires JDBC mode; verify MYBATIS + SQL/验签 guidance matches new behavior
- [x] 4.2 Smoke-check SQL parse and parameter-encrypt tools still work unchanged
