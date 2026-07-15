# Phase 20: Complete admin UI - Context

**Gathered:** 2026-07-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Fill the Group-A UI gaps identified by the Phase 19 coverage audit so the Vue admin app (`admin-ui/`) can fully manage the existing backend admin surfaces. In scope: edit (PUT) forms for Menu (category/dish), Tables (area/table), and Inventory (ingredient); recipe authoring (`PUT /admin/menu/recipes`) + topping group/option management; ingredient cost history (`listCosts`) + `recipes/cost` and menu costing reads; auth session management (`GET/DELETE /auth/sessions`, revoke-others); full reservation status transitions (CONFIRMED/NO_SHOW/COMPLETED, not cancel-only); and role-aware affordances (gate ADMIN-only controls for STAFF — subsumes backlog 999.2).

**Fixed boundary:** Frontend-only where the backend endpoint already exists. Backend list-endpoint gaps (admin order listing, reservation listing, admin category/dish listing, payment filters) are NOT built here — they stay documented as follow-ups (see Deferred). No backend behavior changes.
</domain>

<decisions>
## Implementation Decisions

### Edit form UX (discussed)
- **D-01:** Edit uses a **shared create+edit modal** — reuse the existing `admin-ui/src/components/Modal.vue`. One form component per entity handles both create and edit; on edit it opens pre-filled with the row's current values. Keeps consistency with the existing create flow and avoids duplicated form code. Applies to the simple entities: Menu category/dish, Table area/table, Inventory ingredient.
- **D-02:** **Recipe authoring gets a dedicated screen/section (NOT the shared modal).** Recipe (a dish → many ingredient rows with quantity + unit) and topping group/option management involve nested/repeating rows that are too complex for the create+edit modal. Build them as their own view/route (e.g. a recipe builder with add/remove ingredient rows). Simple forms stay in modals (D-01); recipe/topping are the deliberate exception. Two patterns is acceptable here.

### Claude's Discretion (unselected areas — sensible defaults, planner/researcher may refine)
- **Backend-gap scope:** Phase stays **frontend-only**. The missing backend list endpoints (admin order listing, reservation listing, admin category/dish listing that would surface INACTIVE/ARCHIVED items, payment status/method/date filters) are NOT added in this phase — keep/extend the `knownGaps` registry entries and defer to backlog (999.1 for payment filters; new backlog items for the list endpoints). Do not mock them as if complete.
- **Recipe/topping depth:** Build a **full** recipe builder (dish → ingredient rows with quantity + unit, using the existing ingredient list) and **first-class** topping group/option management (the `createToppingGroup`/`createToppingOption`/`archiveToppingOption` bindings already exist in `modules.ts` but no view calls them). Wire `GET /admin/menu/recipes` (existing `getRecipe` binding) for pre-fill and `GET /admin/menu/recipes/cost` + existing `/admin/menu/costing` for cost display.
- **Role gating:** Prefer decoding the user's role from the **JWT access-token claims** client-side (no extra round-trip) IF the token carries a role/authorities claim — the researcher must verify this against how the backend issues tokens; fall back to a lightweight profile/me read only if the claim is absent. Gate ADMIN-only controls by **hiding** them for STAFF (cleaner than disabled+tooltip). ADMIN-only areas confirmed via `SecurityConfig.java`'s `/admin/**` matcher (menu/table/inventory CRUD); STAFF-allowed areas (table-ops, payments, kitchen, order cancellation) keep full controls.
- **Post-save behavior, validation copy, exact layout** of forms — planner/implementer discretion, following the shared error/loading/empty-state conventions already established in 19-02.
</decisions>

<specifics>
## Specific Ideas

- Reuse the shared primitives from Phase 19: `Modal.vue`, `ConfirmDialog.vue`, `DataTable.vue`, `Toolbar.vue`, `EmptyState.vue`, `StatusBadge.vue`, `GapNotice.vue`, and `src/lib/format.ts`.
- Reservation status control currently hardcodes `status: 'CANCELLED'` (`admin-ui/src/views/TablesView.vue:328`) — replace with a full status selector (CONFIRMED / NO_SHOW / COMPLETED / CANCELLED) driven by the existing reservation status-update endpoint.
- Keep every new API call bound in `admin-ui/src/api/modules.ts` (typed), matching the existing per-module `menuApi`/`tablesApi`/`inventoryApi`/… convention.
</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Coverage gaps this phase closes
- `.planning/phases/19-vuejs-admin-management-interface/19-VERIFICATION.md` — Phase 19 goal-backward result + the role-affordances gap.
- `.planning/phases/19-vuejs-admin-management-interface/19-01-SUMMARY.md`, `19-02-SUMMARY.md`, `19-03-SUMMARY.md` — what the scaffold, views/components, and tests already provide.

### Frontend surface (what exists to extend)
- `admin-ui/src/api/modules.ts` — typed per-module API + `knownGaps` registry; several bindings exist but are uncalled (`createToppingGroup`, `createToppingOption`, `archiveToppingOption`, `getRecipe`, `listCosts`).
- `admin-ui/src/views/*.vue` — the six module views to extend (Menu, Tables, Inventory, Payments, Kitchen, Orders).
- `admin-ui/src/stores/auth.ts` — auth store (login/logout only today; add role decode + session management here).
- `admin-ui/src/components/Modal.vue`, `ConfirmDialog.vue`, `DataTable.vue`, `Toolbar.vue` — reusable primitives (D-01 reuses Modal).
- `admin-ui/README.md` — local setup, env vars, manual smoke path.

### Backend endpoints to wire (already exist)
- `src/main/java/com/example/feat1/DDD/**/infrastructure/presentation/*Controller.java` — Menu (`AdminMenuController`: PUT categories/dishes, topping-groups/options, PUT recipes, GET recipes/cost, GET costing), Tables (`AdminTableController`: PUT areas/tables; `TableOperationController`: reservation status), Inventory (`InventoryController`/`InventoryStockController`: PUT ingredient, GET ingredient costs), Auth sessions (`GET/DELETE /auth/sessions`, revoke-others).
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java` — authoritative role matrix (`/admin/**` = ADMIN; table-ops/payments/kitchen/order-cancel = ADMIN or STAFF). Verify JWT role claim here for role gating.

### Project
- `.planning/PROJECT.md` — v1.2 goal (Active), tech stack, constraints.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Modal.vue` + shared form pattern (D-01): one form component, create-or-edit via pre-fill.
- Uncalled-but-existing API bindings: `createToppingGroup`, `createToppingOption`, `archiveToppingOption`, `getRecipe`, `listCosts` — wiring views to these is a large part of this phase (no new API layer needed for those).
- `format.ts`, `StatusBadge.vue`, `DataTable.vue`, `Toolbar.vue`, `EmptyState.vue`, `GapNotice.vue` from Phase 19.

### Established Patterns
- Typed per-module API in `modules.ts`; views consume it with shared loading/error/empty states.
- `knownGaps` registry surfaces backend gaps in the UI via `GapNotice.vue` — extend, don't mock.
- Vite build runs `vue-tsc` typecheck; tests via `vitest` (17 tests currently green).

### Integration Points
- New PUT/edit calls added to `modules.ts` then wired into the six views.
- Role claim decode added in `auth.ts`; consumed by views to hide ADMIN-only controls for STAFF.
- Recipe/topping = new dedicated view(s) + router entries (D-02), following the AdminLayout child-route pattern in `router/index.ts`.
</code_context>

<deferred>
## Deferred Ideas

- **Backend list endpoints** (own backend phase/backlog, NOT this phase): admin order listing/search, reservation listing (GET), admin category/dish listing incl. INACTIVE/ARCHIVED. Until these exist the UI keeps `knownGaps` notices.
- **Payment status/method/date filters** — backlog 999.1 (backend `GET /admin/payments` extension).
- **Role-aware affordances as a standalone item** — backlog 999.2 is folded into this phase's scope; drop 999.2 from backlog once Phase 20 ships it.
</deferred>

---

*Phase: 20-complete-admin-ui-edit-forms-menu-tables-inventory-recipe-au*
*Context gathered: 2026-07-15*
