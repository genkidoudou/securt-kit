## 1. Module scaffold

- [x] 1.1 Create `securt-kit-playground` Maven module (Java 8, no Servlet API) and add it to parent `pom.xml` / boot2 profile; verify `mvn -pl securt-kit-playground -am "-Dmaven.test.skip=true" package` succeeds
- [x] 1.2 Add `PlaygroundProperties` (`enabled`, `path`, `auth-enabled`, `allowed-tables`) with prefix `securtkit.playground` and verify defaults match design (`path=/playground`, auth false)
- [x] 1.3 Implement `PlaygroundExchange` / `PlaygroundDispatcher` / `PlaygroundResourceLoader` mirroring monitor support classes and verify static `index.html` is served from dispatcher unit or smoke test

## 2. Engine APIs (P0 CRUD)

- [x] 2.1 Implement `PlaygroundEngine` with `resolve(datasourceId)` and allow-list checks; verify disallowed table returns error
- [x] 2.2 Implement `GET /api/meta.json` and `GET /api/datasources.json`; verify JSON shape includes allowed tables and datasource list
- [x] 2.3 Implement CRUD insert/update/query JSON APIs against allow-listed tables via resolved DataSource; verify insert+query round-trip on H2 in a focused test or boot2 manual smoke checklist

## 3. Starter wiring

- [x] 3.1 Add `PlaygroundStatViewServlet` + auto-configuration to `securt-kit-starter-boot2` (javax) gated by `securtkit.playground.enabled`; verify bean registers when enabled
- [x] 3.2 Add jakarta equivalent to `securt-kit-starter-boot3`; verify module compiles on Java 17 profile

## 4. UI tabs (P0–P1)

- [x] 4.1 Ship playground `index.html` / css / js with top bar + CRUD tab wired to APIs; verify page loads at `/playground/` in `securt-kit-test-boot2`
- [x] 4.2 Implement Digest tab + `POST /api/digest/demo.json` actions (insert, update-phone, tamper, verify-read) and optional `raw-query`; verify demo flow against `digest_user` when digest config is present
- [x] 4.3 Add SQL tab deep-link to `/monitor`; verify link target is correct from playground UI

## 5. Multi-DS and Complex (P2)

- [x] 5.1 Support datasource dropdown when multiple DataSources / locator bean present; verify `securt-kit-dy-datasource-test-boot2` can switch ids
- [x] 5.2 Implement `POST /api/complex/run.json` with fixed actions ported from dy Controllers; verify unknown action is rejected and single-DS apps get unsupported error for multi-ds-only actions
- [x] 5.3 Enable playground YAML in dy-boot2/boot3 and boot3 single-DS test apps; verify each README documents `/playground/`

## 6. Docs and polish (P3)

- [x] 6.1 Update root/docs INDEX and four test module READMEs with playground enablement; verify links resolve
- [x] 6.2 Mark `docs/superpowers/specs/2026-09-06-playground-ui-design.md` status as implemented after P2 smoke; verify status line matches reality
