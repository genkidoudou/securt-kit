## Context

See `proposal.md` for motivation. Today:

- `MonitorEngine.querySql` runs two JDBC queries: normal (expects interceptor decrypt for plain) and `/* SECURT_SKIP */` (cipher). Under `mode=MYBATIS`, JDBC interceptors passthrough, so both views show ciphertext.
- `MonitorOpsFacade.rowVerify` runs `SELECT *` without decrypting encrypted digest sources, so digest of ciphertext ≠ stored digest.
- Batch jobs already call strategies directly after `SECURT_SKIP` reads — that pattern is the model to reuse.

## Goals / Non-Goals

**Goals:**

- Mode-agnostic plain/cipher SQL views and correct row verify under MYBATIS.
- Shared helper to decrypt configured columns on a `Map`/row using `TableCache` + `StrategyCache` / `FieldCryptoService`.
- Keep JDBC mode user-visible behavior equivalent (plain ≠ cipher for encrypted columns).

**Non-Goals:**

- Re-enabling JDBC interceptors under MYBATIS.
- Decrypting SQL expression aliases that cannot map to configured field names.
- Changing MyBatis `EncryptInterceptor` or Playground person CRUD.
- Reworking batch job internals (already OK).

## Decisions

### 1. Post-read decryption helper in Monitor

- **Choice:** Add something like `MonitorResultDecryptor.decryptConfiguredFields(tableHint, datasourceId, rows)` used by SQL query and row verify.
- **Why:** Same semantics as batch; no channel coupling.
- **Alternative:** Turn JDBC interceptors back on for Monitor only — rejected (dual-encrypt / mode exclusivity).

### 2. SQL cipher path = raw read; plain path = decrypt(raw)

- **Choice:** Prefer always reading raw (skip-comment or non-intercepting connection), set `cipherRows` from that, clone and decrypt into `plainRows`. Optionally collapse today’s double-query into one raw query + decrypt when mode is MYBATIS or always for consistency.
- **Why:** Guarantees cipher is authoritative DB content; plain is derived.
- **Alternative:** Keep dual query in JDBC mode for interceptor-based plain — acceptable only if outcomes match; prefer single code path for less drift.

### 3. Table resolution for `SELECT * FROM user`

- **Choice:** Infer primary table from parsed SELECT (existing parse utilities) or from the first configured table matching result column names; document that simple single-table SELECT is the supported happy path.
- **Why:** Monitor SQL is already constrained to SELECT; complex joins can decrypt only columns whose labels match configured field names across known tables (best-effort).
- **Assumption:** First version targets single-table queries; multi-table only decrypts unambiguous field-name matches.

### 4. Row verify decrypt sources only

- **Choice:** Decrypt configured encrypted columns that appear in digest `source-fields` (and optionally all encrypted columns for display in `row`) before digest compute/verify. Do not treat digest target columns as ciphertext to decrypt unless configured as encrypt fields.
- **Why:** Digests are over plaintext sources.

### 5. Failure policy

- **Choice:** Per-field decrypt failure follows encryptor `failure-policy` / leave original value and mark verify failed when digest mismatches — do not abort entire SQL result set for one bad cell if policy is fallback; log warn.
- Align with existing strategy usage in batch where practical.

## Risks / Trade-offs

- [Alias / join ambiguity] → Mitigation: only decrypt when column label equals a configured field name; document limitation.
- [JDBC mode behavior change if switching to always raw+decrypt] → Mitigation: tests for both modes asserting plain/cipher split.
- [Performance double work] → Single raw query preferred over two round-trips.

## Migration Plan

1. Ship helper + wire SQL query + row verify.
2. Add unit tests with `mode=MYBATIS` (mock/registry config + in-memory ciphertext rows).
3. Update help text for SQL/验签 under MYBATIS.
4. No config migration required.

## Open Questions

None material for v1; multi-table join decryption remains best-effort as documented above.
