## Context

See `proposal.md` (Why) and the approved design at `docs/superpowers/specs/2026-09-10-select-function-decrypt-design.md`.

Today SELECT items that are bare columns populate `FieldEncryptorInfoDto`; function / CAST / TRIM visitors in `FieldParseParseExpressionVisitor` are no-ops. JDBC `ResultSetDecryptingProxy` and MYBATIS `ResultDecryptHelper` only decrypt when that list matches result labels or entity/Map keys. Monitor `MonitorResultDecryptor` matches `label == configured field name` only.

Constraint: shared parse → shared crypto; do not re-enable JDBC under MYBATIS; do not rewrite user SQL.

## Goals / Non-Goals

**Goals:**
- Recursive select-expression column extraction with alias / normalized expression / JDBC ordinal matching
- JDBC + MYBATIS + Monitor all decrypt function return values via mapped source fields
- Tests and docs covering IFNULL, TRIM/CAST, multi-column first-wins, literal default, bare-column regression

**Non-Goals:**
- In-DB plaintext semantics for `UPPER`/`LIKE`/`SUBSTRING`
- Correct dual-column decrypt for `CONCAT(enc1, enc2)`
- Changing WHERE placeholder encryption rules (keep consistent extraction style only)
- Handling functions inside `SELECT *` expansion

## Decisions

1. **Parse-first, no SQL rewrite**  
   Extend select expression visitors to collect nested columns and emit one `FieldEncryptorInfoDto` per select item (first source column).  
   *Alt rejected:* runtime trial-decrypt of every cell (false positives); whitelist rewrite of IFNULL only (too narrow).

2. **`columnName` = alias else normalized expression**  
   Normalize by collapsing whitespace; match case-insensitively and with whitespace stripped.  
   *Alt rejected:* require AS always (breaks existing SQL).

3. **Optional `resultColumnIndex` on DTO**  
   Prefer explicit 1-based index aligned with select item order for JDBC fallback when labels diverge from parser `toString()`.  
   *Alt:* rely only on list order without field — weaker and harder to debug.

4. **Multi-column → first source**  
   Locked by product decision; document and test.  
   *Alt rejected:* try-all strategies until success (nondeterministic across strategies).

5. **Monitor reuses parse mappings**  
   After raw read, decrypt using parse-derived label→source map, then existing field-name fallback.  
   *Alt rejected:* duplicate Monitor-only AST walk with different rules.

6. **Failure policy unchanged**  
   Literal defaults and transformed ciphertext that fail decrypt keep original values via existing `FieldCryptoService` / failure policy.

## Risks / Trade-offs

- [Driver label ≠ parser text] → normalize + strip spaces + JDBC ordinal fallback  
- [UPPER/etc. on ciphertext fails decrypt] → FALLBACK + docs; not a bug for this change  
- [CONCAT multi-encrypt wrong plaintext] → first-column rule explicit; do not advertise as correct semantics  
- [MYBATIS Map key quirks] → index normalized expression keys; entity path relies on source property names

## Migration Plan

- Pure library behavior expansion; no config schema change required.  
- Deploy with existing `securtkit.encryptor` tables/fields.  
- Rollback: revert the change; prior non-decrypt of function columns returns.  
- Update CAPABILITY-GAPS / MYBATIS-MODE §8 / Monitor help in the same release.

## Open Questions

- Exact set of JSQLParser expression node types beyond `Function` / `TrimFunction` / `CastExpression` / `TranscodingFunction` to wire in the first PR — implementers should cover nodes needed for IFNULL/COALESCE/NVL/TRIM/CAST in targeted tests; add more visitors if a failing test surfaces an unhandled node without expanding product scope.
