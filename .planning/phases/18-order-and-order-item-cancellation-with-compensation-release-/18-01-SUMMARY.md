---
phase: 18-order-and-order-item-cancellation-with-compensation-release-
plan: 01
subsystem: order-context
tags: [ddd, jpa, kafka-outbox, pessimistic-locking, order-cancellation]

# Dependency graph
requires:
  - phase: 17
    provides: KitchenStatusProjectionService terminal-guard idiom, order-side fulfillment rank map, kitchen ticket status event consumer
  - phase: 15
    provides: OrderConfirmationService onStockResult PENDING_CONFIRMATION guard, OutboxWriter contract
provides:
  - "Terminal OrderStatus.CANCELLED value appended after REJECTED"
  - "Cancel domain error factories: cancelWindowClosed(), lineNotCancellable(), noCancellableLines()"
  - "OrderLineEntity.cancelledAt nullable soft-cancel marker column"
  - "OrderRepository.lockById(UUID) pessimistic-write row lock"
  - "OrderCancelledEvent outbox contract + order.events.order-cancelled-topic property"
  - "Both terminal guards (KitchenStatusProjectionService, OrderConfirmationService) verified to treat CANCELLED as terminal"
affects: [18-02, 18-03, 18-04, 18-05, 18-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Terminal-status guard pattern extended: REJECTED || CANCELLED both short-circuit KitchenStatusProjectionService.onTicketStatusChanged before any fulfillment-status derivation"
    - "New terminal enum values are appended at the END of OrderStatus and deliberately omitted from FULFILLMENT_RANK so the existing fail-closed rank guard covers them as a second layer"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/order_context/application/event/OrderCancelledEvent.java
  modified:
    - src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java
    - src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderDomainException.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/entity/OrderLineEntity.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/repository/OrderRepository.java
    - src/main/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionService.java
    - src/main/resources/application.properties
    - src/test/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionServiceTest.java
    - src/test/java/com/example/feat1/DDD/order_context/application/OrderConfirmationServiceTest.java

key-decisions:
  - "CANCELLED appended as the last OrderStatus value (after REJECTED), never inside the pinned CONFIRMED..COMPLETED block, and intentionally absent from FULFILLMENT_RANK -- mirrors the existing REJECTED terminal-status idiom exactly"
  - "OrderConfirmationService needed no production code change for T-18-01-02: its existing `status != PENDING_CONFIRMATION` guard already blocks resurrection of a CANCELLED order; only a regression test was added to pin the behavior (threat register disposition: accept)"

patterns-established:
  - "Cross-context outbox event contracts follow the OrderConfirmedEvent/SettleTriggerEvent record shape: eventId, eventType, occurredAt, orderId, ... , public static final String TYPE"

requirements-completed: [CANCEL-07]

# Metrics
duration: 25min
completed: 2026-07-10
---

# Phase 18 Plan 01: Order-Context Cancellation Foundation Summary

**Terminal `OrderStatus.CANCELLED` + dual terminal-status guards (Kitchen projection, stock-result consumer) + `OrderCancelledEvent` outbox contract, laying the shared model/contract foundation Wave 2 cancel/compensation consumers build on.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-07-10T11:20:00Z (approx, worktree spawn)
- **Completed:** 2026-07-10T11:45:00Z
- **Tasks:** 3
- **Files modified:** 8 (1 created, 7 modified)

## Accomplishments
- Appended `OrderStatus.CANCELLED` as the terminal value after `REJECTED`, leaving the pinned `CONFIRMED..COMPLETED` fulfillment-rank ordering untouched
- Added three cancel domain error factories to `OrderDomainException` (`cancelWindowClosed()`, `lineNotCancellable()`, `noCancellableLines()`) reusing the existing stable-error-code idiom
- Added a nullable `cancelledAt` soft-cancel marker to `OrderLineEntity` and a `PESSIMISTIC_WRITE` `lockById(UUID)` query to `OrderRepository`, both mirroring existing analogs (`OrderEntity.rejectionReason`, `StockReservationRepository.lockByOrderId`)
- Created the `OrderCancelledEvent` outbox record (`wholeOrder`, `cancelledLineIds`, `totalLines`) and its `order.events.order-cancelled-topic` property for Wave 2 Inventory/Payment/Kitchen consumers
- Extended `KitchenStatusProjectionService`'s terminal guard to also short-circuit on `CANCELLED`, and locked in the existing `OrderConfirmationService` stock-result guard behavior with a regression test — both resurrection vectors identified in the plan's threat register (T-18-01-01, T-18-01-02) are covered

## Task Commits

Each task was committed atomically:

1. **Task 1: Append CANCELLED status, add cancel error codes, cancelledAt marker, and lockById** - `a5282cf` (feat)
2. **Task 2: Create OrderCancelledEvent contract + topic property** - `429b328` (feat)
3. **Task 3: Extend terminal-status guards for CANCELLED + regression tests** - `eb4fd4d` (feat)

_Note: No TDD tasks in this plan; all three tasks are `type="auto"`._

## Files Created/Modified
- `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java` - Appended `CANCELLED` after `REJECTED`
- `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderDomainException.java` - Added `cancelWindowClosed()`, `lineNotCancellable()`, `noCancellableLines()` factories
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/entity/OrderLineEntity.java` - Added nullable `cancelledAt` (`Instant`) column
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/repository/OrderRepository.java` - Added `lockById(UUID)` with `@Lock(PESSIMISTIC_WRITE)`
- `src/main/java/com/example/feat1/DDD/order_context/application/event/OrderCancelledEvent.java` - New outbox event record (created)
- `src/main/resources/application.properties` - Added `order.events.order-cancelled-topic`
- `src/main/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionService.java` - Extended terminal guard to include `CANCELLED`
- `src/test/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionServiceTest.java` - Added `cancelledOrderIsNeverModified` test
- `src/test/java/com/example/feat1/DDD/order_context/application/OrderConfirmationServiceTest.java` - Added `cancelledOrderIsNotResurrectedByStaleStockResult` regression test

## Decisions Made
- `CANCELLED` deliberately excluded from `FULFILLMENT_RANK` (same treatment as `REJECTED`) so the pre-existing fail-closed rank guard (`currentRank < 0` → skip) is a second layer of protection beyond the explicit terminal-status early return.
- No production change made to `OrderConfirmationService` for T-18-01-02 — its existing `status != PENDING_CONFIRMATION` guard already blocks a stale stock-result from resurrecting a `CANCELLED` order (threat register disposition: `accept`). Only a regression test was added.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Both focused test suites (`OrderConfirmationServiceTest`, `KitchenStatusProjectionServiceTest`) and a full `mvn compile` passed on the first attempt after each task's changes; a project auto-formatter (invoked by the Maven build) reformatted whitespace in a couple of the edited files but made no semantic changes.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Wave 2 plans (Inventory release consumer, Payment auto-refund consumer, Kitchen void consumer) can now compile against the stable `OrderCancelledEvent` class and its topic property.
- Plan 02 (cancel service) has the `CANCELLED` enum value, `cancelledAt` marker, `lockById` pessimistic lock, and all three cancel domain error codes it needs.
- No blockers identified.

---
*Phase: 18-order-and-order-item-cancellation-with-compensation-release-*
*Completed: 2026-07-10*
