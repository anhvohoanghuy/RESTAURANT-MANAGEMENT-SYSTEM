---
phase: 18-order-and-order-item-cancellation-with-compensation-release-
plan: 02
subsystem: order-context
tags: [ddd, jpa, kafka-outbox, pessimistic-locking, cross-context-port, order-cancellation, tdd]

# Dependency graph
requires:
  - phase: 18-01
    provides: "Terminal OrderStatus.CANCELLED, cancel domain error factories (cancelWindowClosed/lineNotCancellable/noCancellableLines/orderNotFound), OrderLineEntity.cancelledAt, OrderRepository.lockById, OrderCancelledEvent outbox contract + topic property"
provides:
  - "KitchenItemStatusPort (order_context-owned) + KitchenItemStatusView narrow before/at-or-after-PREPARING predicate, implemented by kitchen_context's KitchenItemStatusAdapter"
  - "OrderCancellationService: whole-order cancel (cancelOrder) + partial line cancel (cancelOrderLines), both customer- and staff/ADMIN-callable via a nullable userId"
  - "OrderCancellationDtos (CancelOrderLinesRequest, OrderCancellationResponse)"
affects: [18-03, 18-04, 18-05, 18-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Reverse-direction cross-context read port: order_context owns KitchenItemStatusPort, kitchen_context implements the adapter (opposite direction of the existing OrderLineLookupPort/OrderLineLookupAdapter convention) — a synchronous read taken INSIDE the caller's already-locked transaction, not an eventually-consistent projection"
    - "Nullable-actor-id entry-point split: a single @Transactional service method takes a nullable userId — non-null triggers the customer IDOR-safe findByIdAndUserId pre-check before lockById, null (staff/ADMIN) skips straight to lockById, avoiding a 4-method surface for what the plan scoped as 2 entry points"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/order_context/domain/port/KitchenItemStatusPort.java
    - src/main/java/com/example/feat1/DDD/order_context/domain/model/KitchenItemStatusView.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/KitchenItemStatusAdapter.java
    - src/main/java/com/example/feat1/DDD/order_context/application/OrderCancellationService.java
    - src/main/java/com/example/feat1/DDD/order_context/application/dto/OrderCancellationDtos.java
    - src/test/java/com/example/feat1/DDD/order_context/application/OrderCancellationServiceTest.java
  modified:
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketItemRepository.java

key-decisions:
  - "KitchenItemStatusView collapses kitchen_context's 5-value KitchenItemStatus enum down to a 2-value BEFORE_PREPARING/AT_OR_AFTER_PREPARING view with isBeforePreparing()/atOrAfterPreparing() predicates, so order_context never imports kitchen_context's internal enum across the context boundary"
  - "Whole-order cancel always transitions status to CANCELLED and always publishes exactly one OrderCancelledEvent regardless of how many lines are actually cancellable (cancel-remaining-cancellable semantics per D-4), while partial cancel throws noCancellableLines() with zero publish when every requested line is already at/after PREPARING or not found/already cancelled"
  - "order.total is recomputed as the sum of lineTotal over all non-cancelled lines on EVERY successful cancel (whole-order and partial alike), not just partial — this correctly reflects the case where a whole-order cancel leaves some already-PREPARING lines still billable"
  - "A line referenced in a partial-cancel request that does not resolve to an in-window (before-PREPARING) or not-yet-cancelled line is silently excluded from the cancelled set rather than raising a per-line error — the only failure mode is the aggregate noCancellableLines() when the resulting cancellable set is empty"

patterns-established:
  - "TDD RED/GREEN split for a brand-new service class: RED commit bundles the (non-behavioral) DTO records needed for the test to type-check plus the failing/non-compiling test file; GREEN commit adds only the service implementation"

requirements-completed: [CANCEL-01, CANCEL-02, CANCEL-04]

# Metrics
duration: 45min
completed: 2026-07-10
---

# Phase 18 Plan 02: Order Cancellation Core (Cross-Context Kitchen Read + Cancellation Service) Summary

**OrderCancellationService with whole-order and partial-line cancel under a pessimistic order-row lock, gated by a race-safe synchronous cross-context read (new KitchenItemStatusPort) of kitchen item status, recomputing order.total and publishing exactly one OrderCancelledEvent via the transactional outbox per successful cancel.**

## Performance

- **Duration:** 45 min
- **Started:** 2026-07-10T04:39:00Z (approx, worktree spawn)
- **Completed:** 2026-07-10T04:52:59Z
- **Tasks:** 2
- **Files modified:** 7 (6 created, 1 modified)

## Accomplishments
- Added the reverse-direction cross-context read port pair: `KitchenItemStatusPort` (order_context-owned interface) + `KitchenItemStatusView` (narrow before/at-or-after-PREPARING predicate type) + `KitchenItemStatusAdapter` (kitchen_context implementation backed by a new non-locking `KitchenTicketItemRepository.findByTicket_OrderId` read query)
- Implemented `OrderCancellationService` with two `@Transactional` entry points (`cancelOrder`, `cancelOrderLines`), both: resolving + locking the order row (customer ownership pre-check via `findByIdAndUserId` for non-null `userId`, direct `lockById` for staff/ADMIN's `null` userId), enforcing the SUBMITTED/PENDING_CONFIRMATION/CONFIRMED cancel window, reading kitchen truth synchronously inside the locked transaction to exclude any line already at/after PREPARING, setting `cancelledAt` (never removing lines from `order.getLines()`), recomputing `order.total`, and publishing exactly one `OrderCancelledEvent` via `OutboxWriter.save` in the same transaction
- Delivered via strict TDD: RED commit (failing/non-compiling `OrderCancellationServiceTest` + supporting DTOs) followed by GREEN commit (service implementation) — 7 tests covering window rejection, non-owner 404 with no lock leak, whole-order cancel + single publish, partial recompute, PREPARING-line exclusion via the mocked port, all-requested-lines-preparing rejection with no publish, and the staff no-ownership-check path

## Task Commits

Each task was committed atomically:

1. **Task 1: Cross-context kitchen item status read port + adapter** - `a08f2c5` (feat)
2. **Task 2: OrderCancellationService (whole-order + partial) with outbox publish** - RED `4e2a216` (test), GREEN `82f526f` (feat)

_Task 2 is `tdd="true"`; no REFACTOR commit was needed — the GREEN implementation required no follow-up cleanup._

## Files Created/Modified
- `src/main/java/com/example/feat1/DDD/order_context/domain/port/KitchenItemStatusPort.java` - New port, `findStatuses(UUID orderId): Map<UUID, KitchenItemStatusView>`
- `src/main/java/com/example/feat1/DDD/order_context/domain/model/KitchenItemStatusView.java` - New narrow view enum (`BEFORE_PREPARING`, `AT_OR_AFTER_PREPARING`) with predicate methods
- `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/KitchenItemStatusAdapter.java` - New `@Component @Transactional(readOnly = true)` port implementation
- `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketItemRepository.java` - Added non-locking `findByTicket_OrderId(UUID)` read query; `lockByOrderIdAndItemId` unchanged
- `src/main/java/com/example/feat1/DDD/order_context/application/OrderCancellationService.java` - New service: `cancelOrder(userId, orderId)`, `cancelOrderLines(userId, orderId, request)`
- `src/main/java/com/example/feat1/DDD/order_context/application/dto/OrderCancellationDtos.java` - New `CancelOrderLinesRequest`, `OrderCancellationResponse` records
- `src/test/java/com/example/feat1/DDD/order_context/application/OrderCancellationServiceTest.java` - New test class, 7 tests, all mock-based (`OrderRepository`, `KitchenItemStatusPort`, `OutboxWriter`)

## Decisions Made
- Chose a single nullable-`userId` parameter per entry point (2 `@Transactional` methods total, matching the plan's literal instruction) over 4 separate customer/staff methods — `null` means staff/ADMIN (REST-layer `/admin/orders/**` already enforces `hasAnyRole` per the phase's Pattern Map, no service-layer redundancy needed), non-null means customer with the existing IDOR-safe `findByIdAndUserId` pre-check
- Whole-order cancel never throws on an all-PREPARING order (cancels 0 lines, still transitions to CANCELLED, still publishes) per the plan's explicit "prefer cancel-remaining-cancellable" instruction (D-4); only partial cancel throws `noCancellableLines()` when its cancellable subset is empty
- `order.total` recompute runs on every successful cancel path, not gated to partial-only, since a whole-order cancel can legitimately leave PREPARING lines still billable

## Deviations from Plan

None - plan executed exactly as written. Both domain error factories `lineNotCancellable()` and `noCancellableLines()` were available from Plan 01; this plan's implementation uses `noCancellableLines()` for the "zero cancellable lines from a partial-cancel request" case (`lineNotCancellable()` remains available, unused by this plan, for Plan 05's REST layer if a per-line error is later needed there).

## Issues Encountered

None. Focused test suite (`OrderCancellationServiceTest`, 7/7) and the full Maven suite (222/222, 0 failures/errors) passed after the GREEN commit; a project auto-formatter reformatted whitespace/import wrapping in the newly created files but made no semantic changes.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 03 (Inventory reservation release consumer), Plan 04 (Payment auto-refund consumer), and the Kitchen ticket-invalidation consumer can now rely on a stable, tested `OrderCancelledEvent` producer with exactly-once-per-cancel publish semantics.
- Plan 05 (REST endpoints) has both service entry points (`cancelOrder`, `cancelOrderLines`) ready to wire to customer (`/orders/{orderId}/cancel`) and staff/ADMIN (`/admin/orders/{orderId}/cancel`) routes, plus the response/request DTOs.
- No blockers identified.

---
*Phase: 18-order-and-order-item-cancellation-with-compensation-release-*
*Completed: 2026-07-10*
