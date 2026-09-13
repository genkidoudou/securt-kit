## Context

See `proposal.md` for motivation. Product design: `docs/superpowers/specs/2026-09-10-playground-mp-scenarios-design.md`.

Observed:

- `PlaygroundPersonService` hard-checks `EncryptModeHolder.isJdbc()` in preflight; writes/reads rely on JDBC interceptors.
- Boot2/Boot3 already use MyBatis-Plus and have `user` / `orders` with encrypt fields configured.
- `securt-kit-playground` intentionally avoids Servlet/MP compile coupling; SPI registration from host apps is the established pattern (similar to datasource locator).

## Goals / Non-Goals

**Goals:**

- Mode-agnostic person CRUD via explicit crypto + skip writes / raw+decrypt reads.
- Scenario runner SPI + MP implementation in test apps; three-column run results.
- Dual-tab UI without removing the person desk.

**Non-Goals:**

- Moving person CRUD to MP.
- New demo tables.
- Guaranteeing plaintext semantics for SQL functions on ciphertext.

## Decisions

### 1. Person: explicit crypto, not interceptor-dependent
- **Choice:** `FieldCryptoService` (+ digest) on write with `SECURT_SKIP`; business read = skip + decrypt.
- **Why:** Works for JDBC and MYBATIS; avoids double-encrypt under JDBC.
- **Alternative:** Branch only for MYBATIS — rejected (two paths drift).

### 2. Complex queries: MP in test modules via SPI
- **Choice:** `PlaygroundScenarioRunner` interface in playground; Boot2/3 implement with MP Mapper/Wrapper/XML.
- **Why:** Keeps playground free of MP dependency; proves real MYBATIS/JDBC host stacks.
- **Alternative:** Add MP to playground module — rejected (version matrix / coupling).

### 3. Cipher column via skip-comment re-query
- **Choice:** After plain MP query, run equivalent skip-comment select (or shared SQL with skip) for `cipherRows`.
- **Alternative:** Decrypt-only clone for plain — still need authoritative cipher source.

### 4. Reuse `user` + `orders`
- **Choice:** No new tables; rely on existing seed / LargeTestDataSeeder where present.
- **Risk:** Empty tables → empty evidence; scenarios still return structure + messages.

### 5. Function scenario is a boundary demo
- **Choice:** Always attach `sqlMeta.limitation`; zero rows allowed as success.
- **Why:** Matches product decision that functions on cipher are not guaranteed.

### 6. Phased delivery inside one change
- P0 person dual-mode + tab shell + API stubs  
- P1 core MP scenarios + three panels  
- P2 alias/function + Boot3/multi-DS  

## Risks / Trade-offs

- [Risk] JDBC mode MP query already decrypted while skip path fails → Mitigation: require skip-comment enablement in scenario availability checks.
- [Risk] Column alias breaks decrypt mapping → Mitigation: map aliases back to configured columns in runner; document in sqlMeta.
- [Risk] LIKE exact-match default surprises users → Mitigation: surface handler semantics in sqlMeta.
- [Risk] Double encrypt if person write forgets skip under JDBC → Mitigation: tests for both profiles; central write helper.

## Migration Plan

1. Ship person dual-mode first (existing person UI keeps working).
2. Add complex tab; host without runner shows unavailable states.
3. Register Boot2 runner, then Boot3 / multi-DS.
4. Rollback: revert playground + test runner beans; no schema migration beyond existing tables.

## Open Questions

None material; exact MP SQL shape per scenario can be chosen during P1 within the listed scenarioIds.
