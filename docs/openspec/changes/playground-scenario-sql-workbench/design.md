## Context

See `proposal.md` (Why) and the approved design at `docs/superpowers/specs/2026-09-12-playground-scenario-sql-workbench-design.md`.

Today Playground complex-query tab lists fixed scenarios and runs them via `PlaygroundScenarioRunner` into three evidence panels. There is no in-page seed table browser and no ad-hoc SELECT executor. Boot2 seed tables are `user` and `orders` with encrypt strategy appending `(加密)`.

Constraints: reuse Playground auth; do not depend on Monitor; keep `scenarios/run.json` stable; only SELECT.

## Goals / Non-Goals

**Goals:**
- Dual always-visible table previews via seed-preview API + UI
- Scenario sample params / example SQL metadata from runner/catalog
- sql-run with conservative SQL guardrails and optional SECURT_SKIP
- Wire UI editor + evidence mapping

**Non-Goals:**
- Table row editing; arbitrary write SQL; replacing Monitor SQL workspace

## Decisions

1. **Preview via SECURT_SKIP JDBC read in playground engine/helper**  
   Prefer engine-owned preview using DataSource from locator, not MP entities, so ciphertext form is stable.  
   *Alt:* decrypt preview — rejected (misleads SQL authors writing cipher predicates).

2. **sql-run validation is conservative**  
   Ban any `;`; require SELECT after stripping block/line comments; ban write/DDL heads.  
   *Alt:* full SQL parser allowlist — higher cost, defer.

3. **Example SQL lives primarily in MpPlaygroundScenarioRunner.list()**  
   Enrich descriptors when listing; catalog baseline may carry static samples as fallback when runner absent (then scenarios already unavailable).  
   *Alt:* only frontend hardcode — drifts from server seed.

4. **Skip UX mapping**  
   Spec-defined: skip → cipher=`rows`, plain=same rows + note, sql=`sqlMeta`.  
   *Alt:* hide plain on skip — leaves empty panel.

5. **Limits**  
   Default 50, hard cap 100 for preview and sql-run truncation.

## Risks / Trade-offs

- [Large tables slow UI] → limit + truncate flag  
- [Users think writes allowed] → UI copy “只读 SELECT”  
- [skip disabled + useSkip] → clear 400/503 style error  
- [H2 quoted `"user"` vs dialect] → keep existing quote style from boot SQL

## Migration Plan

- Deploy library + boot runners together for sample SQL richness.  
- Old clients ignore new catalog fields.  
- Rollback: revert module; remove new routes from dispatcher.

## Open Questions

- Exact default limit (50 vs 20) can be tuned in implementation without changing requirements as long as ≤100 hard cap.
