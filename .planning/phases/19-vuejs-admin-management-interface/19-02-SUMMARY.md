---
phase: 19-vuejs-admin-management-interface
plan: 02
subsystem: ui
tags: [vue3, vite, typescript, vue-router, admin-ui, rest-client]

# Dependency graph
requires:
  - phase: 19-01
    provides: Vue 3 + Vite + TypeScript scaffold, auth store/login flow, protected router, admin shell layout (sidebar/topbar), shared fetch wrapper with 401 refresh retry, initial DataTable/StatusBadge/EmptyState stubs
provides:
  - Typed API wrappers (menuApi, tablesApi, inventoryApi, paymentsApi, kitchenApi, ordersApi) covering every admin endpoint named in 19-CONTEXT.md D-04
  - Real per-module pages (Menu, Tables, Inventory, Payments, Kitchen, Orders) with loading/error/empty states, dense tables, and create/update/archive/action forms wired to the live backend
  - Reusable form/action primitives: Modal, ConfirmDialog, Toolbar, GapNotice, plus an error-tone EmptyState and a status-aware StatusBadge
  - A `knownGaps` registry surfacing backend gaps (admin category/dish listing, reservation listing, payment status/method/date filters, global order search) as explicit UI notices instead of silently faking them
affects: [19-03, future backend work closing backlog 999.1 or adding admin list endpoints]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "DataTable renders columns as {key,label,mono} with per-column `cell-<key>` scoped slots for badges/formatting/actions, replacing the 19-01 string[]-column generic table"
    - "Modal + ConfirmDialog pair used for every create/edit form and every destructive action (archive, cancel, refund) across all six modules"
    - "knownGaps registry (api/modules.ts) + GapNotice component used to document backend capability gaps in-UI rather than hiding or mocking them"
    - "Session-local result lists (reservations created, order cancellations) used where the backend has create/act endpoints but no corresponding list endpoint"

key-files:
  created:
    - admin-ui/src/components/Modal.vue
    - admin-ui/src/components/ConfirmDialog.vue
    - admin-ui/src/components/Toolbar.vue
    - admin-ui/src/components/GapNotice.vue
    - admin-ui/src/lib/format.ts
  modified:
    - admin-ui/src/api/modules.ts
    - admin-ui/src/components/DataTable.vue
    - admin-ui/src/components/EmptyState.vue
    - admin-ui/src/components/StatusBadge.vue
    - admin-ui/src/style.css
    - admin-ui/src/views/MenuView.vue
    - admin-ui/src/views/TablesView.vue
    - admin-ui/src/views/InventoryView.vue
    - admin-ui/src/views/PaymentsView.vue
    - admin-ui/src/views/KitchenView.vue
    - admin-ui/src/views/OrdersView.vue

key-decisions:
  - "Menu view reads GET /menus/public (the public active-menu tree) for its dish listing because no admin list-categories/list-dishes endpoint exists; documented as a knownGaps entry rather than treated as complete parity with the admin write surface."
  - "Topping groups/options and recipe editing have working typed API functions (menuApi.createToppingGroup/createToppingOption/getRecipe) but no dedicated editor UI in this pass — called out in-page as a follow-up, not hidden."
  - "Tables reservations have no list endpoint; created reservations are kept in a session-local ref and rendered in a table, cleared on reload — documented via knownGaps.tables."
  - "Orders module has no order-listing endpoint at all; the page is action-only (cancel order / cancel line by ID) with a convenience panel of order IDs pulled from the kitchen board, plus a session-local log of cancellation results."
  - "Payments status/method/date filter controls are rendered but disabled, matching D-05 in 19-CONTEXT.md (backlog 999.1)."

patterns-established:
  - "Per-column scoped-slot DataTable: `<template #cell-foo=\"{ row, value }\">` overrides default text rendering for badges, money, dates, and action buttons."
  - "Every mutating action (create, archive, advance, cancel, refund) goes through apiFetch via a typed *Api module — no view calls fetch/apiFetch directly."

requirements-completed: [ADMIN-UI-002, ADMIN-UI-003, ADMIN-UI-006]

# Metrics
duration: ~45min
completed: 2026-07-15
---

# Phase 19 Plan 02: Admin Module Views & Operational Primitives Summary

**Typed REST wrappers for every admin endpoint (menu, tables/table-ops, inventory/stock, payments/refunds, kitchen board, order cancellation) plus six fully wired module pages (dense tables, create/archive/advance/cancel forms, modal+confirm-dialog primitives) replacing the 19-01 generic endpoint-grid scaffold.**

## Performance

- **Duration:** ~45 min (estimated)
- **Completed:** 2026-07-15
- **Tasks:** 3 (as planned)
- **Files modified/created:** 16 (5 created, 11 modified, 1 deleted)

## Accomplishments

- Read every backend admin controller and DTO referenced in 19-CONTEXT.md (`AdminMenuController`, `AdminTableController`, `TableOperationController`, `InventoryController`, `InventoryStockController`, `PaymentController`, `KitchenController`, `AdminOrderCancellationController`) to build accurate TypeScript request/response types instead of guessing shapes.
- Replaced the 19-01 `ModulePage.vue` generic "endpoint list + one sample GET" scaffold with six real pages, each independently fetching, rendering, and acting on live data.
- Added Modal/ConfirmDialog/Toolbar/GapNotice primitives and extended DataTable/EmptyState/StatusBadge so every module follows the same create-form, destructive-confirm, filter-toolbar, and backend-gap-notice patterns from `19-UI-SPEC.md`.
- Surfaced four real backend gaps explicitly in the UI (admin category/dish listing, reservation listing, payment filters, global order search) instead of mocking them as working.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add reusable admin primitives** - `d5e4f4b` (feat)
2. **Task 2: Wire module API functions** - `ab9af4c` (feat)
3. **Task 3: Build module pages** - `728dc4a` (feat)

**Plan metadata:** (this commit, docs: complete plan)

_Note: because this is a tightly coupled Vue refactor (pages depend on both the new primitives and the new API layer), the three task commits are logically atomic per plan task, but the working tree only reaches a fully green `npm run build`/`npm run test` state once all three have landed — verified at final HEAD below._

## Files Created/Modified

- `admin-ui/src/api/modules.ts` - Typed `menuApi`/`tablesApi`/`inventoryApi`/`paymentsApi`/`kitchenApi`/`ordersApi` wrappers + `knownGaps` registry (replaces the old endpoint-metadata/sample-read scaffold)
- `admin-ui/src/components/DataTable.vue` - Column objects `{key,label,mono}` with per-column scoped slots, `rowKey` prop
- `admin-ui/src/components/EmptyState.vue` - Added `tone="error"` variant with retry button
- `admin-ui/src/components/StatusBadge.vue` - Added `status` prop with a backend-status→tone lookup table
- `admin-ui/src/components/Modal.vue` - New: Teleported dialog with Escape-to-close, used by every create/edit form
- `admin-ui/src/components/ConfirmDialog.vue` - New: Modal-based destructive confirmation (archive/cancel/refund)
- `admin-ui/src/components/Toolbar.vue` - New: filters/actions flex layout for module page headers
- `admin-ui/src/components/GapNotice.vue` - New: renders a `knownGaps` entry as a warning-toned notice panel
- `admin-ui/src/lib/format.ts` - New: `formatMoney`, `formatPercent`, `formatDateTime`, `truncateId`, `newIdempotencyKey`, `messageOf`
- `admin-ui/src/style.css` - Added modal/toolbar/tag-chip/field-grid/danger-button/empty-state CSS
- `admin-ui/src/views/MenuView.vue` - Categories (tag chips + archive), dishes table (from public menu tree) with new/archive, menu-costing table
- `admin-ui/src/views/TablesView.vue` - Occupancy dashboard (open/close/cancel session, set state), area/table CRUD, reservation create/seat/cancel
- `admin-ui/src/views/InventoryView.vue` - Stock table (low-stock toggle, new ingredient, archive, add cost), record-movement form, recent-movements table
- `admin-ui/src/views/PaymentsView.vue` - Filtered/paginated payment history, record-payment and record-refund forms, disabled gap filters
- `admin-ui/src/views/KitchenView.vue` - Active-ticket board with single-step "Advance to `<next>`" action
- `admin-ui/src/views/OrdersView.vue` - Cancel-order/cancel-line forms with confirm dialogs, session-local result log, kitchen-board order-ID convenience panel
- `admin-ui/src/views/ModulePage.vue` - **Deleted**: superseded by the six dedicated pages above

## Decisions Made

- Used `GET /menus/public` as the Menu module's read source since no `GET /admin/menu/categories` or `/dishes` list endpoint exists on the backend; documented as a `knownGaps` entry (inactive/archived items won't appear).
- Scoped Menu module UI to category/dish CRUD + menu-costing reads; left topping-group/option and recipe editing as API-ready-but-not-yet-built (typed functions exist, no dedicated form) to keep the plan's time budget realistic — called out explicitly in-page rather than silently omitted.
- Represented reservations and order-cancellation results as session-local lists (in-memory `ref`, not persisted) because the backend has no corresponding list endpoints — matches D-05's "disabled or documented, never faked" principle from 19-CONTEXT.md.
- Kept payments' status/method/date filter controls visible-but-disabled (per D-05) rather than removing them, so the UI documents the gap in place.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed unused `reactive` import in OrdersView.vue**
- **Found during:** Task 3 build verification (`npm run build`)
- **Issue:** `vue-tsc -b` failed with `TS6133: 'reactive' is declared but its value is never read` after the view was written without any `reactive()` usage.
- **Fix:** Dropped the unused import.
- **Files modified:** `admin-ui/src/views/OrdersView.vue`
- **Verification:** `npm run build` passes cleanly.
- **Committed in:** `728dc4a` (Task 3 commit)

**2. [Rule 2 - Missing critical] Deleted `ModulePage.vue` instead of leaving it as dead code**
- **Trigger:** Task 1 changed `DataTable`'s prop contract from `columns: string[]` to `columns: {key,label,mono}[]`; the old `ModulePage.vue` (superseded by Task 3's dedicated pages) still used the old contract and would fail type-checking if left in place.
- **Fix:** Removed `admin-ui/src/views/ModulePage.vue` and its `getModuleSample`/generic sample-read pattern; confirmed no remaining references via `grep -rn "ModulePage" src`.
- **Files modified:** `admin-ui/src/views/ModulePage.vue` (deleted)
- **Verification:** `npm run build` / `npm run test` pass; `git diff --diff-filter=D` shows only this intentional deletion across the plan's three commits.
- **Committed in:** `d5e4f4b` (Task 1 commit, since the DataTable contract change is what made the old file uncompilable)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 missing-critical/cleanup). No scope creep — both were necessary to keep the build green.

## Issues Encountered

- No admin GET list endpoints exist for menu categories/dishes or table reservations, and no global admin order-listing endpoint exists at all. None of these are bugs in this plan's scope — they are pre-existing backend gaps, now surfaced explicitly via `knownGaps` + `GapNotice` per D-05 rather than worked around with fake data.
- This environment has no running Spring Boot backend, database, or Kafka broker, so the manual "smoke load each module route" verification step could not be executed against live data. Instead: (1) `npm run build` and `npm run test` pass at the final commit, and (2) a local `vite` dev server was started and every view module (`main.ts`, `App.vue`, `router/index.ts`, and all six module views) was fetched and returned HTTP 200 with no Vite compile/transform errors, confirming the app boots and every route's code is syntactically and type-sound. Full data-flow verification against a live backend remains a follow-up for whoever runs this against `VITE_API_BASE_URL`.

## User Setup Required

None - no external service configuration required. To exercise the live data paths, point `VITE_API_BASE_URL` (default `http://localhost:8080`) at a running instance of this repo's Spring Boot backend and sign in with an ADMIN/STAFF account.

## Next Phase Readiness

- All six admin modules (Menu, Tables, Inventory, Payments, Kitchen, Orders) have real routes, real API calls, and documented gap states — ready for 19-03 (or a follow-up polish pass) to add the deferred topping/recipe editor UI, tighten form validation, and/or wire an Overview dashboard once backend summary endpoints exist.
- `admin-ui/src/api/modules.ts` now has full type coverage for every admin endpoint named in 19-CONTEXT.md D-04, so future pages/features can reuse `menuApi`/`tablesApi`/`inventoryApi`/`paymentsApi`/`kitchenApi`/`ordersApi` directly without re-deriving request/response shapes from the Java DTOs.
- No blockers identified for 19-03.

---
*Phase: 19-vuejs-admin-management-interface*
*Completed: 2026-07-15*
