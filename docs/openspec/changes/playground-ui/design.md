## Context

See `proposal.md` for motivation. Authoritative product design: `docs/superpowers/specs/2026-09-06-playground-ui-design.md`.

Observed patterns to mirror: `securt-kit-monitor` (`MonitorEngine` + `MonitorDispatcher` + static resources) and boot2/boot3 `MonitorStatViewServlet` adapters. Multi-DS sample APIs already exist under `securt-kit-dy-datasource-test-*` Controllers and can be adapted into fixed playground actions.

## Goals / Non-Goals

**Goals:**
- Shared playground module + starter wiring for all four test apps
- Manual demos for CRUD / Digest / Complex / Multi-DS; SQL via monitor deep-link in P0–P1
- Safe defaults: allow-listed tables, fixed complex actions

**Non-Goals:**
- Replacing JUnit
- Merging into monitor UI
- Arbitrary client-supplied SQL in playground APIs

## Decisions

1. **New module `securt-kit-playground` parallel to monitor**  
   - Rationale: different UX (business-table demos vs ops trial).  
   - Alternative rejected: extend MonitorEngine with CRUD tabs (scope sprawl).

2. **Ship inside starter-boot2/3 auto-config**  
   - Rationale: same enablement story as monitor for test apps.  
   - Alternative: test apps depend on playground directly (more boilerplate).

3. **SQL tab deep-links `/monitor` first**  
   - Rationale: avoid duplicating parse/encrypt-sql.  
   - Later may embed iframe if UX requires.

4. **Static HTML/CSS/JS aligned with monitor styling**  
   - Rationale: zero new frontend toolchain in library modules.

5. **Datasource resolution via primary DataSource + optional locator map**  
   - Rationale: single-DS apps need zero extra beans; dy apps provide map/locator.

## Risks / Trade-offs

- **[Risk] Dy complex APIs tightly coupled to sample Controllers** → Extract action handlers into Engine or thin adapters; return unsupported on single-DS.  
- **[Risk] H2 reserved `"user"` table quoting** → Reuse existing quote helpers / allow-list canonical names.  
- **[Risk] Dual Boot modules duplication for Servlet only** → Keep adapters thin like monitor.  
- **[Trade-off] Two UIs (`/monitor` + `/playground`)** → Clear split; document both in README.

## Migration Plan

1. Add module + starter wiring (disabled by default or enabled only in test apps).  
2. Enable in `test-boot2`, verify CRUD.  
3. Roll to boot3, then dy apps with datasource locator.  
4. Update READMEs. Rollback: set `enabled: false`.

## Open Questions

- Exact set of Complex `action` names to port in P2 (join/like/page/batch) — finalize from existing dy Controllers during apply without changing allow-list/security rules.
