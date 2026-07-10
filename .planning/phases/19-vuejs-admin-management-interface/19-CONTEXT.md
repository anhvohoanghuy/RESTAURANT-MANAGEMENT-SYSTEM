# Phase 19: VueJS admin management interface - Context

**Gathered:** 2026-07-10
**Status:** Ready for planning
**Source:** User request + backend endpoint survey

<domain>
## Phase Boundary

Build a separate VueJS admin interface for existing Spring Boot backend operations. The phase should create the frontend app and wire it to current REST endpoints without changing backend behavior unless a compile/runtime integration gap is discovered.

</domain>

<decisions>
## Implementation Decisions

### D-01: Frontend stack
- Use Vue 3 + Vite + TypeScript in a new `admin-ui/` directory.
- Use `vue-router` for authenticated/admin routes.
- Use `@lucide/vue` for navigation/actions icons.
- Use custom CSS/components instead of introducing a large UI framework.

### D-02: Auth/session
- Login uses `POST /auth/login`.
- Store access token and refresh token in client state with local persistence for local development.
- Every API call goes through one shared fetch wrapper that attaches `Authorization: Bearer`.
- Repeated 401 returns the user to `/login` with a session-expired message.

### D-03: Admin shell
- First authenticated screen is the admin workbench, not a landing page.
- Sidebar modules: Overview, Menu, Tables, Inventory, Orders, Kitchen, Payments.
- The interface favors dense tables, filter bars, drawers/modals, and predictable destructive confirmations.

### D-04: Backend API scope
- Integrate existing backend endpoints:
  - `/admin/menu/**`
  - `/admin/tables/**`, `/admin/table-sessions/**`
  - `/admin/inventory/**`, `/admin/menu/recipes/cost`, `/admin/menu/costing`
  - `/admin/payments/**`, `/admin/orders/**`
  - `/admin/orders/kitchen-board`
- Do not fake unsupported backend capabilities as complete.

### D-05: Known gap
- Payment history status/method/date filters are known backend follow-up scope in Phase 999.1.
- The UI may show disabled or documented filter controls but must not imply those filters work until backend support exists.

### D-06: Verification
- Add frontend build/typecheck verification.
- Add focused tests for API client/auth guard where practical.
- Provide a manual smoke path against the Spring Boot backend.
</decisions>

<canonical_refs>
## Canonical References

### GSD
- `.planning/ROADMAP.md` - Phase 19 goal and success criteria.
- `.planning/phases/19-vuejs-admin-management-interface/19-UI-SPEC.md` - UI design contract.

### Backend Controllers
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/presentation/AuthController.java` - login/refresh/logout/session endpoints.
- `src/main/java/com/example/feat1/DDD/menu_context/infrastructure/presentation/AdminMenuController.java` - menu admin endpoints.
- `src/main/java/com/example/feat1/DDD/table_context/infrastructure/presentation/AdminTableController.java` - dining area/table catalog endpoints.
- `src/main/java/com/example/feat1/DDD/table_context/infrastructure/presentation/TableOperationController.java` - table session/occupancy/reservation endpoints.
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/presentation/InventoryController.java` - ingredient/costing endpoints.
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/presentation/InventoryStockController.java` - stock/movement endpoints.
- `src/main/java/com/example/feat1/DDD/payment_context/infrastructure/presentation/PaymentController.java` - payment/refund/payment history endpoints.
- `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/presentation/KitchenController.java` - kitchen board/status endpoint.
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/presentation/AdminOrderCancellationController.java` - admin cancellation endpoints.
</canonical_refs>

<specifics>
## Specific Ideas

- Use `VITE_API_BASE_URL` with default `http://localhost:8080`.
- Keep IDs monospaced and truncated in dense tables.
- Avoid optimistic UI for payment, refund, inventory movement, cancellation, and kitchen status changes.
- Prefer reusable list page primitives for loading/error/empty/table/action states.
</specifics>

<deferred>
## Deferred Ideas

- Full role/permission administration UI.
- Real payment provider configuration.
- Payment status/method/date filters until Phase 999.1 backend support exists.
- Generated OpenAPI client from `/v3/api-docs`.
</deferred>

---

*Phase: 19-vuejs-admin-management-interface*
*Context gathered: 2026-07-10*
