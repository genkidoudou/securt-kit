## 1. Boot2 Mapper + Runner

- [x] 1.1 Add failing Boot2 IT that `single-eq` run with a prepared plaintext phone returns non-empty `plainRows` and differing cipher phone under MYBATIS; verify the focused IT fails before Runner/XML changes
- [x] 1.2 Add `PlaygroundScenarioMapper` + `PlaygroundScenarioMapper.xml` in Boot2 for `table-alias`, `column-alias`, `join-user-orders`, and `func-on-cipher`; verify module compiles and mapper methods are scanned
- [x] 1.3 Rewire Boot2 `MpPlaygroundScenarioRunner` so `single-eq`/`like-phone` use `UserEntityMapper` Wrapper, complex scenarios call the new XML mapper, cipher column stays skip+encrypt; verify 1.1 IT passes and `func-on-cipher` exposes `sqlMeta.limitation`
- [x] 1.4 Generate `exampleSqlCipher` via `FieldCryptoService.encrypt` (no hard-coded strategy suffix) and keep sampleParams; verify list payload cipher SQL contains the encrypted phone form for the sample plaintext

## 2. Boot3 parity

- [x] 2.1 Port `PlaygroundScenarioMapper` / XML and Runner wiring to Boot3; verify Boot3 compiles with the new mapper on the classpath
- [x] 2.2 Add Boot3 smoke IT for scenario list availability and `single-eq` three-part response (plaintext hit when seed prepared); verify the smoke passes

## 3. SQL workbench guidance

- [x] 3.1 Add `sqlMeta.note` (Chinese) on non-skip `sql-run` under MYBATIS explaining literal SQL does not auto-encrypt; verify unit/dispatcher assertion for the note field
- [x] 3.2 Add Playground UI hint next to「执行 SQL」and assert static-resource / JS markers; verify resource tests pass

## 4. Docs and verification

- [x] 4.1 Update `docs/PLAYGROUND.md` for BaseMapper/XML run path vs SQL workbench bypass; verify documented paths and scenario guidance exist
- [x] 4.2 Mark product design `2026-09-12-playground-scenario-mp-basemapper-xml-design.md` as confirmed/implemented-ready if still pending; verify status line updated
- [x] 4.3 Run focused Boot2/Boot3 playground scenario tests with `-Dmaven.test.skip=false` (install shaded core first if needed) and verify scenario-related tests pass
