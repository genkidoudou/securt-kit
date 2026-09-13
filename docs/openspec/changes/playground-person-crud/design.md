## Context

See `proposal.md` for motivation. Authoritative product decisions are in `docs/superpowers/specs/2026-09-10-playground-person-crud-design.md`.

Current Playground (`securt-kit-playground`) serves a lifecycle wizard UI backed by `PlaygroundLifecycleService` and `/api/lifecycle/*`, plus legacy `/api/crud/*` routes. Test apps configure encryption/digest on `digest_user`, not a person table with `id_card`. Raw reads already use `/* SECURT_SKIP */` in lifecycle code; that pattern SHOULD be reused for `view=raw`.

Planning artifacts live under `docs/openspec/`; implementation touches `securt-kit-playground` and Boot2/Boot3 (and multi-DS) test modules.

## Goals / Non-Goals

**Goals:**

- Replace the default UI with a single-table maintenance desk for `playground_person`.
- Add `PlaygroundPersonService` + `/api/person/*` with business/raw list semantics.
- Align test-app schema and Securt-Kit field/digest/whitelist config with the new table.
- Keep login, datasource selection, and Monitor link.

**Non-Goals:**

- Lifecycle wizard, tamper demo, evidence panels.
- Pagination, strong id-card format validation, new crypto algorithms.
- Merging Playground into Monitor.
- Reworking unrelated legacy CRUD/Digest/Complex APIs beyond what the new UI needs (may leave unused endpoints).

## Decisions

### 1. New table instead of extending `digest_user`

- **Choice:** `playground_person` with `id_card`.
- **Why:** Clean demo schema; avoid overloading email/name semantics on the old table.
- **Alternative:** Alter `digest_user` — rejected to reduce migration noise and keep old samples intact.

### 2. Dedicated person API namespace

- **Choice:** `/api/person/list|create|update|delete.json`.
- **Why:** Clear contract for the new UI; avoids overloading lifecycle or generic CRUD payloads.
- **Alternative:** Reuse `/api/crud/*` — rejected because table/fields and view semantics differ.

### 3. Raw view via skip-intercept read

- **Choice:** `view=raw` runs SELECT with `SECURT_SKIP` (same family as lifecycle raw reads).
- **Why:** Guarantees ciphertext as stored; already proven in this codebase.
- **Alternative:** Dual decrypt-then-re-encrypt display — rejected as non-authoritative.

### 4. Sensitive filters are equality-only

- **Choice:** Encrypt plaintext `phone`/`idCard` filter values and compare equality to stored ciphertext.
- **Why:** Matches deterministic encryption query behavior; fuzzy match on ciphertext is out of scope.
- **Alternative:** Digest-assisted search — deferred; not required for this demo.

### 5. Lifecycle UI removal; API may linger briefly

- **Choice:** Remove wizard from default static UI; lifecycle API can be deleted in the same change or left unreferenced and cleaned up if low cost.
- **Why:** Spec requires maintenance desk as default; orphaned API is technical debt, not user-facing.
- **Alternative:** Keep wizard as secondary tab — rejected per product decision (full replace).

### 6. Frontend shape

- **Choice:** Single-page HTML/CSS/JS in playground resources: filter bar, view toggle, table, modal form.
- **Why:** Matches existing static asset hosting; no new frontend stack.
- **Alternative:** Multi-tab restore of old playground tools — out of scope for this change.

### 7. Readiness check

- **Choice:** Lightweight preflight for `playground_person` (table allowed, encrypt fields, digest rule) shown as a banner.
- **Why:** Spec requires clear setup failures; mirrors lifecycle preflight pattern without step gating.

## Risks / Trade-offs

- [Config drift across Boot2/Boot3/multi-DS] → Mitigation: checklist task to update every test `application.yml` / initializer that enables Playground.
- [Existing lifecycle IT breaks] → Mitigation: rewrite or replace ITs to person CRUD scenarios; drop lifecycle-only tests.
- [Fuzzy name search ambiguity] → Mitigation: implement equality first; optional `LIKE` only if cheap and documented.
- [Users still open old docs describing wizard] → Mitigation: update `docs/PLAYGROUND.md` (and related README pointers) in tasks.

## Migration Plan

1. Add table DDL in test initializers / schema scripts.
2. Ship person service + APIs + UI behind the same `/playground` path (replace index assets).
3. Update Securt-Kit YAML for encrypt/digest/whitelist.
4. Replace lifecycle-focused tests with person CRUD tests.
5. Rollback: revert playground module + test config commits; `digest_user` data remains untouched.

## Open Questions

None material; deferred items (pagination, format validation) are explicit non-goals.
