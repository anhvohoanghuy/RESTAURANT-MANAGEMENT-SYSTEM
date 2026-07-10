---
phase: 18-order-and-order-item-cancellation-with-compensation-release-
plan: 05
subsystem: order-context
tags: [spring-mvc, spring-security, rest, order-cancellation, integration-test]

# Dependency graph
requires:
  - phase: 18-02
    provides: "OrderCancellationService (cancelOrder/cancelOrderLines, nullable-userId customer/staff split), OrderCancellationDtos (CancelOrderLinesRequest, OrderCancellationResponse)"
provides:
  - "POST /orders/{orderId}/cancel and /orders/{orderId}/items/{lineId}/cancel on OrderController (customer, ownership-checked by the service)"
  - "New AdminOrderCancellationController: POST /admin/orders/{orderId}/cancel and /admin/orders/{orderId}/items/{lineId}/cancel (staff/ADMIN, any order in window)"
  - "OrderCancellationIntegrationTest proving the full authorization matrix end-to-end through Spring Security"
affects: [18-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Partial-cancel-by-path-lineId: the single-line REST variant wraps the path lineId into a one-element CancelOrderLinesRequest before delegating to the same cancelOrderLines service method used for a (future) multi-line body variant"
    - "Admin controller convention reused verbatim from kitchen_context's KitchenController: @RestController @RequestMapping(\"/admin/orders\"), no class-level security annotation, relies entirely on the existing /admin/orders/** hasAnyRole(\"ADMIN\",\"STAFF\") SecurityConfig matcher"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/presentation/AdminOrderCancellationController.java
    - src/test/java/com/example/feat1/DDD/order_context/integration/OrderCancellationIntegrationTest.java
  modified:
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/presentation/OrderController.java

key-decisions:
  - "The partial-cancel HTTP surface (POST /orders/{orderId}/items/{lineId}/cancel and its /admin equivalent) takes the line id from the path only, wrapping it as a single-element CancelOrderLinesRequest(List.of(lineId)) rather than accepting a body — the plan left the exact request shape ('match the DTO Plan 02 defined') at the executor's discretion, and Plan 02's CancelOrderLinesRequest(List<UUID> lineIds) already supports this without a new DTO"
  - "No length-capped optional reason string was added to either cancel request — the plan flagged it as optional/at-discretion, and neither OrderCancellationService nor OrderCancellationDtos (Plan 02) accepts one, so adding it would have required an out-of-scope service change"
  - "Integration test authenticates a STAFF principal via a directly-constructed CustomUserDetails + UsernamePasswordAuthenticationToken (mirrors KitchenIntegrationTest's staff() helper) instead of registering a STAFF account through /auth/register, since the registration flow only issues the USER role; the customer-owned and IDOR paths still go through real /auth/register + JWT to prove the full authenticated flow"

patterns-established: []

requirements-completed: [CANCEL-02, CANCEL-03, CANCEL-04]

# Metrics
duration: 30min
completed: 2026-07-10
---

# Phase 18 Plan 05: Order Cancellation REST Endpoints + Authorization Integration Test Summary

**Customer cancel endpoints on OrderController plus a new AdminOrderCancellationController under /admin/orders/**, both wired to Plan 02's OrderCancellationService, proven end-to-end by a 5-case MockMvc integration test covering own-order success, IDOR-safe 404, staff/ADMIN any-order cancel, non-staff rejection, and the cancel-window guard.**

## Performance

- **Duration:** ~30 min
- **Completed:** 2026-07-10T05:04:45Z
- **Tasks:** 2
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments
- `OrderController` gained `POST /orders/{orderId}/cancel` (whole-order) and `POST /orders/{orderId}/items/{lineId}/cancel` (single-line partial), both resolving `principal.getId()` and delegating to `OrderCancellationService`'s customer path, which is IDOR-safe (`findByIdAndUserId` → 404 for non-owner) with no service-layer change needed.
- New `AdminOrderCancellationController` (`@RequestMapping("/admin/orders")`, no class-level security annotation, mirroring `KitchenController`'s established convention) exposes the same two operations for staff/ADMIN with `userId = null`, hitting the service's no-ownership-check path for any order in the cancel window. `SecurityConfig` was verified unchanged — the existing `/admin/orders/**` `hasAnyRole("ADMIN","STAFF")` matcher already covers the new routes.
- New `OrderCancellationIntegrationTest` (5 tests, MockMvc + H2, outbox relay disabled by the test profile) proves: (1) a customer cancelling their own in-window order succeeds, the order reaches `CANCELLED`, and a PENDING `OrderCancelled` outbox row is persisted for it; (2) a customer cancelling another user's order gets `404 ORDER_NOT_FOUND` and the target order is left untouched; (3) a STAFF principal cancels an order it does not own via `/admin/orders/{orderId}/cancel` and succeeds; (4) the same route rejects a plain authenticated USER with `403` and an anonymous caller with `401`; (5) an order force-set to `PREPARING` (past the customer cancel window) is rejected with `400 ORDER_CANCEL_WINDOW_CLOSED`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Customer + admin cancel endpoints** - `9c65f62` (feat)
2. **Task 2: End-to-end authorization integration test** - `25878a9` (test)

## Files Created/Modified
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/presentation/OrderController.java` - Added `cancel` and `cancelLine` endpoints, injecting `OrderCancellationService`
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/presentation/AdminOrderCancellationController.java` - New controller, staff/ADMIN cancel routes under `/admin/orders/**`
- `src/test/java/com/example/feat1/DDD/order_context/integration/OrderCancellationIntegrationTest.java` - New integration test, 5 tests covering the full authorization matrix + window guard

## Decisions Made
- Wrapped the path `lineId` into a single-element `CancelOrderLinesRequest` for the partial-cancel routes rather than introducing a new request-body DTO, since Plan 02's `CancelOrderLinesRequest(List<UUID> lineIds)` already fits without modification and the plan explicitly left the exact shape at the executor's discretion.
- Skipped the optional length-capped reason string entirely (plan marked it "at discretion"); adding it would have required a Plan 02 service/DTO change out of this plan's scope.
- Used a directly-constructed `CustomUserDetails`/`UsernamePasswordAuthenticationToken` for the STAFF test principal (matching `KitchenIntegrationTest`'s established `staff()` helper) rather than extending the registration flow to issue a STAFF role, keeping the test focused on the authorization matrix rather than role-provisioning plumbing.

## Deviations from Plan

None - plan executed exactly as written. Both acceptance criteria items ("SecurityConfig is unchanged" and "endpoints wire to Plan 02 service methods") were verified directly: `SecurityConfig.java` was read but not edited, and both controllers call `OrderCancellationService.cancelOrder`/`cancelOrderLines` exactly as defined in Plan 02.

## Issues Encountered

None. Focused test suite (`OrderCancellationIntegrationTest`, 5/5) and the full Maven suite (243/243, 0 failures/errors) both passed on the first run after the GREEN implementation. A project auto-formatter reformatted whitespace in `AdminOrderCancellationController.java` and the test file after creation, matching the pattern already noted in Plan 02's summary; no semantic changes.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 06 (whatever remaining Phase 18 scope depends on the REST surface) can now rely on both customer and staff/ADMIN cancel endpoints being live, tested, and routed correctly through the existing `SecurityConfig` RBAC.
- No blockers identified.

---
*Phase: 18-order-and-order-item-cancellation-with-compensation-release-*
*Completed: 2026-07-10*

## Self-Check: PASSED
