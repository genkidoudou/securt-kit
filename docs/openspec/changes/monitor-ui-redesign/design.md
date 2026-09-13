## Context

See `proposal.md` for motivation. Product decisions are recorded in `docs/superpowers/specs/2026-09-07-monitor-ui-redesign-design.md`; visual reference is `docs/superpowers/mockups/monitor-redesign/approach-3-tabs.html`.

Current implementation: `securt-kit-monitor` serves static UI from `support/http/resources/` via Boot2/Boot3 `MonitorStatViewServlet`. Ops APIs from change `monitor-ops-console` already cover config, digest, row verify, batch preview/apply, and SELECT dual-view. The gap is front-end IA and presentation, not domain APIs.

Constraints:
- Keep Servlet + static HTML/CSS/JS stack (no SPA framework introduction)
- Prefer zero API contract changes
- Do not merge with playground

## Goals / Non-Goals

**Goals:**
- Ship approach-3 UI: workbench home, restyled tabs, merged SQL sub-modes, batch step gating
- Reuse existing `/api/*` from the browser
- Keep CSS absolute asset paths under `/monitor/...` (match current pattern)

**Non-Goals:**
- Sidebar shell (approach 1) or scene wizard shell (approach 2)
- New overview API unless front-end composition proves insufficient during apply (default: do not add)
- Backend domain refactors unrelated to UI wiring

## Decisions

### D1: Front-end-only change by default
- **Choice**: Rewrite `index.html`, `css/style.css`, `js/app.js` in place; leave Java dispatcher/engine unchanged unless a bug blocks UI.
- **Why**: Specs are UI-observable; APIs already exist.
- **Alternatives**: Add `/api/overview.json` for workbench — deferred; compose `config.json` + `datasources.json` first.

### D2: Keep multi-tab chrome; merge only SQL
- **Choice**: Top tabs remain; SQL parse/encrypt/query become sub-modes inside one tab.
- **Why**: Matches approved approach 3; lowest IA risk while fixing the worst split.
- **Alternatives**: Sidebar (rejected by user).

### D3: Incremental JS migration, not greenfield rewrite if possible
- **Choice**: Rebuild shell/DOM to match mockup; port existing API call helpers tab-by-tab; delete dead bindings as tabs are migrated.
- **Why**: `app.js` is large; wholesale rewrite risks silent regressions.
- **Alternatives**: Full rewrite from scratch — higher risk for same outcome.

### D4: Visual tokens follow mockup with offline fallbacks
- **Choice**: Warm orange primary + teal secondary; optional Google Fonts CDN with system-ui / monospace fallbacks.
- **Why**: Approved mockup; intranet may block CDN.

### D5: Batch gating is UI state over existing jobId
- **Choice**: Enable apply only after successful `preview` response with jobId; clear/disable apply when inputs change.
- **Why**: Matches ops-console safety model without new endpoints.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Missed event bindings when reshaping DOM | Migrate one tab at a time; smoke checklist per tab |
| Relative CSS/JS 404 if paths regress | Keep absolute `/monitor/css|js` like current monitor HTML |
| Font CDN unavailable | CSS font-family fallbacks |
| Workbench summary incomplete from config shape | Map fields defensively; show “—” for missing |

## Migration Plan

1. Replace static resources in `securt-kit-monitor` (single deployable JAR/module consumers pick up on rebuild)
2. No DB or YAML migration required
3. Rollback: revert the three static files (and docs) to previous revision

## Open Questions

- None that block specs/tasks; optional `overview.json` remains explicitly out of default apply scope.
