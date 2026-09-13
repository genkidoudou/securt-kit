## Context

See `proposal.md` for motivation and `specs/sql-workspace-ui/spec.md` for observable requirements.

Monitor uses static HTML, CSS, and JavaScript from `securt-kit-monitor/src/main/resources/support/http/resources/`. The current SQL tab contains three sibling panels controlled by `sqlModes`; `sqlModes.switch` copies the previous mode's input into the next mode, while the query and output containers expand with their contents. Existing feature objects (`sqlQuery`, `sqlParser`, and `sqlEncryptor`) already own API calls and result rendering, so the layout can change without altering backend behavior.

The approved visual reference is `docs/superpowers/mockups/sql-page-redesign/index.html`.

## Goals / Non-Goals

**Goals:**

- Introduce a two-level SQL workspace while retaining existing operation objects and API bindings
- Bound result-area height and use local scrolling
- Isolate drafts and rendered results among query, parse, and encrypt modes
- Keep the desktop workspace compact and preserve usability on narrow screens

**Non-Goals:**

- Changing SQL parsing, query, encryption, pagination, authentication, or datasource semantics
- Adding a frontend framework or external dependency
- Redesigning other Monitor tabs
- Consolidating the three datasource selections into shared mutable state

## Decisions

### D1: Add a primary view controller around the existing mode panels

- **Choice**: Replace the current three-button SQL subtab row with primary 查询/修改 controls. Keep `sqlQueryPanel` as the query view and wrap `sqlParsePanel` plus `sqlEncryptPanel` in a modification view with its own secondary controls.
- **Why**: This models the approved information architecture while allowing current panel IDs and feature-specific event bindings to remain intact.
- **Alternative**: Create separate top-level Monitor tabs for query and modification. Rejected because it fragments SQL tools and adds navigation noise.

### D2: Preserve state through persistent DOM, not copied values

- **Choice**: Hide inactive panels with CSS while leaving their inputs and result nodes mounted. Replace the current cross-mode input-copy behavior in `sqlModes.switch` with visibility and active-button updates only. Track the active primary view and active modification mode independently.
- **Why**: Each input element already naturally retains its own value. Removing assignment is simpler and prevents silent draft overwrite.
- **Alternative**: Maintain a JavaScript draft store and rehydrate fields on every switch. Rejected because persistent DOM already provides the required state with less synchronization risk.

### D3: Reflow existing controls without changing IDs

- **Choice**: Move datasource radios, query pagination, inputs, actions, and result elements into toolbar/pane wrappers, but retain every ID consumed by `app.js`.
- **Why**: Existing query, parse, encrypt, clear, copy, datasource, and result-rendering code can continue to operate without API or selector migration.
- **Alternative**: Rename elements to mirror the new hierarchy and update all bindings. Rejected because it adds regression risk without user-visible value.

### D4: Bound content with CSS work areas

- **Choice**: Give query results and modification outputs viewport-aware fixed/max heights with `overflow: auto`; use sticky table headers where applicable. Desktop modification panes use a two-column grid with min-width safeguards.
- **Why**: Local scrolling addresses the long-page problem while keeping input controls nearby.
- **Alternative**: Collapse result sections into accordions. Rejected because it adds clicks and does not help simultaneous input/output comparison.

### D5: Use one responsive breakpoint at 900px

- **Choice**: Above 900px, modification input and result panes are side by side. At or below 900px, panes stack and use bounded local result heights.
- **Why**: This matches the approved prototype and avoids narrow two-column editors.
- **Alternative**: Preserve two columns down to tablet widths with horizontal scrolling. Rejected because SQL text and result tables become difficult to read.

### D6: Keep query dual-view rendering intact

- **Choice**: Preserve the existing plaintext/decrypted and ciphertext result output, placing both inside the bounded query result workspace.
- **Why**: Dual-view is existing Monitor behavior and is useful for encryption diagnostics; this change reorganizes presentation rather than reducing functionality.
- **Alternative**: Add another toggle between plaintext and ciphertext. Deferred because it introduces new state and was not part of the approved prototype.

## Risks / Trade-offs

- **Nested containers may clip controls or results at unusual viewport heights** → Use `min-height: 0`, viewport-aware `clamp()` dimensions, and test desktop plus short/narrow viewports.
- **Existing result renderers may inject wrappers that bypass intended overflow rules** → Apply bounding styles at stable existing result container IDs and inspect all success and empty-result states.
- **Removing SQL draft copying changes current behavior** → This is intentional and explicitly covered by the new independent-state requirement; verify each mode retains its own draft.
- **Sticky headers can fail if overflow is applied to the wrong ancestor** → Make the direct result-table workspace the scrolling ancestor and validate with enough rows to overflow.
- **Datasource radio groups may wrap in the compact toolbar** → Allow toolbar wrapping while keeping validation messages associated with their original group.

## Migration Plan

1. Restructure only the SQL section of `index.html`, retaining all existing IDs.
2. Add SQL workspace, toolbar, split-pane, bounded-result, and responsive styles to `style.css`.
3. Update `sqlModes` to control primary/secondary visibility without copying input values.
4. Run existing monitor tests and perform browser smoke checks for query, parse, parameter encryption, clear/copy, independent drafts, internal scrolling, and 900px responsive behavior.
5. Deploy through the normal `securt-kit-monitor` artifact rebuild; no database, configuration, or API migration is required.

Rollback consists of reverting the SQL-specific changes in the three static resource files.
