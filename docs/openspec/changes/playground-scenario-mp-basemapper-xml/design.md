## Context

See `proposal.md` for motivation. Product design: `docs/superpowers/specs/2026-09-12-playground-scenario-mp-basemapper-xml-design.md`.

Observed:

- Boot2/Boot3 already depend on MyBatis-Plus; `UserEntityMapper` / `OrderEntityMapper` / `UserOrderMapper.xml` exist.
- `MpPlaygroundScenarioRunner` already uses QueryWrapper for `single-eq` / `like-phone`, but join and several scenarios still lean on JDBC assembly; SQL workbench `sql-run` is raw JDBC and correctly does not encrypt literals.
- Operators confuse「执行 SQL」with「运行场景」and expect plaintext literals to encrypt under MYBATIS.

## Goals / Non-Goals

**Goals:**

- Split scenario plain path: BaseMapper/Wrapper for simple; dedicated XML mapper for complex.
- Keep cipher column via skip + explicit encrypt.
- Keep SQL workbench; add explicit MYBATIS guidance.
- Parity on Boot2 and Boot3.

**Non-Goals:**

- Literal auto-encrypt inside `sql-run`.
- Moving person desk to MP.
- New demo tables.

## Decisions

### 1. New `PlaygroundScenarioMapper` + XML
- **Choice:** Dedicated mapper for `table-alias`, `column-alias`, `join-user-orders`, `func-on-cipher` (and optional helpers).
- **Why:** Keeps scenario SQL centralized; avoids overloading `UserOrderMapper` test methods.
- **Alternative:** Extend `UserOrderMapper` only — rejected for coupling.

### 2. Simple scenarios stay on `UserEntityMapper`
- **Choice:** `single-eq` and `like-phone` via `QueryWrapper` / `LambdaQueryWrapper` on BaseMapper.
- **Why:** Matches “simple CRUD/conditions use Plus BaseMapper”; already proven by `MybatisModeQueryWrapperTest` patterns.

### 3. Cipher evidence remains JDBC skip
- **Choice:** After MP plain query, skip-comment select with `FieldCryptoService.encrypt` for equality/LIKE cores.
- **Why:** Authoritative ciphertext without depending on interceptor decrypt on the same connection path.

### 4. Dynamic cipher sample SQL
- **Choice:** Build `exampleSqlCipher` with `FieldCryptoService.encrypt` instead of hard-coded `(加密)`.
- **Why:** Strategy suffix may vary; samples must match runtime strategy.

### 5. SQL workbench note, not encrypt rewrite
- **Choice:** `sqlMeta.note` + UI hint; no playground-module literal rewriter.
- **Why:** Preserves workbench as bypass; matches user confirmation to keep「执行 SQL」.

## Risks / Trade-offs

- [Risk] Wrapper params not encrypted if interceptor missing → Mitigation: IT under real Boot app with starter; assert hit on known ciphertext.
- [Risk] Alias type collisions Boot2/3 → Mitigation: IT properties pin `type-aliases-package`.
- [Risk] Users still click「执行 SQL」 → Mitigation: Chinese UI + note.

## Migration Plan

1. Add Boot2 mapper/XML; rewire Runner.
2. Mirror Boot3.
3. UI/docs/IT.
4. Rollback: revert Runner to prior QueryWrapper/JDBC assembly; remove new mapper files.

## Open Questions

None material; XML statement shapes may be refined during apply within the listed scenarioIds.
