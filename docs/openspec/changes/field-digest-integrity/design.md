## Context

See `proposal.md` for motivation. Securt-Kit already provides YAML-driven field encrypt/decrypt on JDBC (`SimpleInterceptor*`) and MyBatis (`EncryptInterceptor` + helpers), sharing `FieldEncryptorProperties`, `StrategyCache`, and `FieldCryptoService`. Digest must plug into those write/read paths without a second encryptor config tree.

Authoritative prior design: `docs/superpowers/specs/2026-09-05-field-digest-integrity-design.md` and plan `docs/superpowers/plans/2026-09-05-field-digest-integrity.md`.

**Observed codebase state:** Core digest stack (`DigestService`, `DigestSqlRewriter`, `DigestWriteSupport`, `DigestReadSupport`, registry, HMAC strategy) and MyBatis hooks are largely present; Boot2 IT / USAGE polish and P2 RELOAD edge hardening remain the primary apply focus.

## Goals / Non-Goals

**Goals:**
- One config model + one `DigestService` facade for JDBC and MyBatis
- Plaintext-in / digest-out on write; optional verify after decrypt on read
- Safe single-table SQL rewrite; fail closed on bad digest config at startup

**Non-Goals:**
- Blind-index / equality-search HASH (separate future capability)
- JOIN result verification, BATCH completeness, monitor UI trial digests
- Moving field encryption from setter-time to execute-time solely for digest ordering aesthetics

## Decisions

1. **Extend `FieldEncryptorStrategy` instead of a separate `DigestStrategy`**  
   - Rationale: one cache, one Spring bean story, Java 8 `default` methods keep encrypt-only strategies unchanged.  
   - Alternative rejected: parallel digest interface (duplicate registration + RELOAD still needs crypto).

2. **Built-in HMAC-SHA256 digest-only strategy + `digest-hmac-key`**  
   - Rationale: integrity needs keyed MAC; register via `StrategyCache.registerStrategy` (no no-arg ctor).  
   - Alternative: reuse AES encrypt strategy for digest (weaker / wrong abstraction).

3. **JDBC: rewrite SQL before `prepareStatement`; bind digests before execute**  
   - Rationale: JDBC prepare locks SQL shape; encrypt-on-`setString` remains — preserve plaintext map for digest input (logical plaintext-before-ciphertext).  
   - Alternative: encrypt-on-execute refactor (large regression risk).

4. **MyBatis: compute digests first, then rewrite BoundSql only for computed targets**  
   - Rationale: `SKIP` must not leave unbound `?`.  
   - Alternative: rewrite all targets then skip bind (placeholder mismatch bug).

5. **`partial-update` default `RELOAD`**  
   - Rationale: partial entity updates still refresh integrity; INSERT missing sources fail (`INSERT cannot RELOAD`).  
   - Alternative: default `SKIP` (silent stale digests).

6. **Verify-on-read default off; mismatch policies align with `failure-policy`**  
   - Rationale: migration-friendly; `FAIL_FAST` → `DigestMismatchException`.

## Risks / Trade-offs

- **[Risk] Multi-row INSERT appended parameter indexes inaccurate** → Mitigate: skip multi-row rewrite (warn) or fix index list before relying on batch VALUES.  
- **[Risk] RELOAD WHERE uses encrypted stored params incorrectly** → Mitigate: WHERE binds use post-encrypt stored values; sources for digest use plaintext map (covered by write-support tests).  
- **[Risk] Dirty working tree / Mode config co-commits** → Mitigate: apply commits stay scoped to digest files; validate with focused Maven modules.  
- **[Trade-off] No JOIN verify** → Integrity limited to single-table result paths in v1.  
- **[Trade-off] Digest column must exist in schema** → Document; rewrite only helps SQL shape, not DDL.

## Migration Plan

1. Add digest column(s) to tables (nullable initially).  
2. Configure `digest-*` + rules; set `digest-hmac-key` for HMAC.  
3. Deploy with `digest-verify-on-read: false`; backfill digests via app writes or offline job.  
4. Enable verify-on-read with `FALLBACK`, then tighten to `FAIL_FAST` when clean.  
5. Rollback: disable digest rules / verify flags; leave columns unused (no encryptor rollback required).

## Open Questions

- Whether Boot2 IT should cover both JDBC and MYBATIS modes in one class or split (does not change requirements; choose during apply based on existing test harness).
