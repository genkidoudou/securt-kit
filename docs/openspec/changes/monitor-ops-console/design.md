## Context

See `proposal.md` for motivation. Authoritative product design: `docs/superpowers/specs/2026-09-06-monitor-ops-console-design.md`.

Current monitor stack: `securt-kit-monitor` (`MonitorEngine` + `MonitorDispatcher` + static resources) with thin boot2/boot3 Servlets. Existing APIs cover encrypt/decrypt, parse-sql, encrypt-sql, query-sql, data-init, and a partial `config.json` (tables/fields only—no digest globals/rules). Digest runtime already exists in core (`FieldEncryptorProperties.digest*`, `DigestService`, skip-comment). Playground is a separate module and must stay independent.

## Goals / Non-Goals

**Goals:**
- Extend monitor Engine/UI in place for phases A → C → B → D
- Safe ops defaults: preview-before-apply, SELECT-only query, secret redaction, configured-table allow-list
- Optional MyBatis-Plus TableInfo for PK without hard dependency

**Non-Goals:**
- New Maven module for ops
- YAML write-back from UI; arbitrary DML executor; merging with playground

## Decisions

1. **Extend `securt-kit-monitor` rather than a new module**  
   - Rationale: same mount, auth, and resource model; lowest integration cost.  
   - Alternative rejected: `securt-kit-monitor-ops` split (extra wiring for one UI).

2. **Phased delivery A → C → B → D**  
   - Rationale: config visibility unblocks ops; tools before mutating batch; dual-view last because it depends on skip-comment.  
   - Alternative: big-bang all tabs (higher risk).

3. **Batch jobs: in-memory preview store with TTL + jobId**  
   - Rationale: forces confirm step; avoids trusting client-supplied “proposed values” alone.  
   - Alternative: client resubmits full row diffs only (easier to tamper)—may allow as secondary path with server recompute checksum, but primary path is jobId.

4. **Cipher dual-view via skip-comment (or equivalent skip-decrypt path)**  
   - Rationale: reuses existing interceptor escape hatch; no second physical DataSource required.  
   - Alternative: unwrap raw JDBC URL connection (pool/config fragile).

5. **PK: config map on `MonitorProperties` first**  
   - Rationale: works without MP; matches operator-owned YAML.  
   - TableInfo via reflection/optional dependency; manual input request-scoped only.

6. **Row verify returns JSON business result**  
   - Rationale: FAIL_FAST must not kill the monitor session UX; catch and map to `verified=false`.

## Risks / Trade-offs

- **[Risk] Preview store memory growth / multi-instance** → TTL + max jobs; document sticky session / single instance for apply.  
- **[Risk] WHERE injection** → parameterized bindings + reject multi-statement; no free SQL concatenation of WHERE body without validation.  
- **[Risk] Cipher view needs skip-comment** → clear API error; document in MONITOR-SERVLET / USAGE.  
- **[Trade-off] 500-row apply cap** → safer default; large migrations stay outside monitor.  
- **[Trade-off] Config-before-TableInfo** → YAML overrides entity annotation if both disagree (documented).

## Migration Plan

1. Ship phase A (config) behind existing monitor login—no breaking API removals; additive JSON fields.  
2. Ship C APIs/UI; existing encrypt/decrypt unchanged.  
3. Ship B with defaults denying empty WHERE.  
4. Ship D; query-sql becomes SELECT-only (**behavior change** for anyone relying on non-SELECT via this API—document as intentional hardening).  
Rollback: disable new UI tabs / unused endpoints; revert SELECT-only if needed via flag if discovered necessary during apply (prefer document-only unless tests demand a flag).

## Open Questions

- Exact preview job TTL (suggest 10 minutes) and whether apply re-validates row versions—finalize during implementation without changing specs if kept ≤500 and preview-required.
