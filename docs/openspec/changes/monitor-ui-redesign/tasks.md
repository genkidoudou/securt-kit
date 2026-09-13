## 1. Shell and visual foundation

- [x] 1.1 Rebuild `securt-kit-monitor/.../resources/index.html` shell (dark header, top tabs including Workbench, merged SQL tab placeholder, login/main pages) using approach-3 structure and verify `/monitor/` serves the new HTML title/chrome without 404 on `/monitor/css/style.css` and `/monitor/js/app.js`
- [x] 1.2 Rewrite `css/style.css` to approach-3 tokens (warm orange / teal, workbench cards, result panel, responsive tab scroll) with font fallbacks and verify the page renders without layout collapse at ~375px and desktop widths
- [x] 1.3 Set default active tab to Workbench after login/no-auth entry in `app.js` and verify opening `/monitor/` lands on Workbench instead of crypto

## 2. Workbench

- [x] 2.1 Implement Workbench summary by composing existing `config.json` + `datasources.json` (mode, key flags, table/digest counts as available) and verify the workbench shows non-empty summary when monitor is enabled in `securt-kit-test-boot2`
- [x] 2.2 Wire Workbench shortcuts to switch to SQL (dual-view), batch, and config tabs in-page and verify each shortcut activates the correct tab without leaving `/monitor`

## 3. Migrate tool tabs

- [x] 3.1 Port single-value crypto UI (encrypt/decrypt/sign/verify) onto the new DOM and verify each action still calls the existing APIs and shows success/error in the unified result panel
- [x] 3.2 Port single-row verify tab and verify a configured `digest_user` (or sample table) returns structured result via existing `/api/row/verify.json`
- [x] 3.3 Port configuration tab rendering against existing `/api/config.json` and verify encrypt fields, digest rules, and global flags still display
- [x] 3.4 Port data-init tab and verify encrypt/decrypt init actions still invoke existing data-init endpoints

## 4. SQL merge and batch gating

- [x] 4.1 Merge parse / parameter-encrypt / dual-view query into one SQL tab with sub-mode controls (default dual-view) and verify all three flows work and SQL text survives sub-mode switches in the same session
- [x] 4.2 Add batch step indicator and disable apply until successful preview (jobId); clear/disable apply when scope inputs change; verify apply stays disabled pre-preview and becomes available after successful `/api/batch/preview.json`

## 5. Docs and verification

- [x] 5.1 Update `docs/MONITOR-SERVLET.md` (and INDEX if needed) for Workbench default, SQL merge, and batch step UX; verify docs match the shipped tabs
- [x] 5.2 Mark `docs/superpowers/specs/2026-09-07-monitor-ui-redesign-design.md` status as implemented after smoke and verify the status line reflects reality
- [x] 5.3 Run `mvn -pl securt-kit-monitor -am test` (or project-equivalent monitor tests) and verify `MonitorOpsUnitTest` (and related) still pass with no API regressions
