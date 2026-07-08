---
phase: 17-kitchen-context
plan: 07
subsystem: api
tags: [kafka, jackson-3, spring-kafka, ddd, order_context, kitchen_context]

# Dependency graph
requires:
  - phase: 17-kitchen-context (17-01)
    provides: OrderStatus with PREPARING/READY/SERVED/COMPLETED (pinned ordinal)
  - phase: 17-kitchen-context (17-04)
    provides: KitchenTicketStatusChangedEvent (full per-item snapshot) + KitchenItemStatus
provides:
  - Order-side Kafka consumer wiring for kitchen.ticket-status-changed (poison-pill-safe)
  - KitchenStatusProjectionService deriving order status from kitchen's per-item snapshot
  - FULFILLMENT_RANK forward-only, idempotent, replay-safe guard closing the D-04 loop
affects: [17-kitchen-context (remaining verification plans), future order-status consumers]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Cross-context TRUSTED_PACKAGES pinned to the producing context's event package (kitchen_context.application.event) when consuming another bounded context's event"
    - "FULFILLMENT_RANK map-based monotonic forward-only status guard for multi-step (5-state) status derivation, reusing the existing ledger/idempotency preamble shape"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/TicketStatusChangedKafkaConsumerConfig.java
    - src/main/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionService.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/TicketStatusChangedListener.java
    - src/test/java/com/example/feat1/DDD/order_context/infrastructure/config/TicketStatusChangedKafkaConsumerConfigTest.java
    - src/test/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionServiceTest.java
  modified: []

key-decisions:
  - "Order-side status derivation uses allMatch/anyMatch over the item snapshot: ANY item still at PREPARING sets the order to PREPARING (order isn't ready while one item lags); ALL items must reach READY/SERVED/COMPLETED for the order to advance to that stage."
  - "No explicit orderRepository.save() call after order.setStatus(target) -- mirrors OrderConfirmationService's existing pattern of relying on @Transactional JPA dirty-checking to persist the change only when it actually occurs."
  - "KitchenItemStatus.ordinal() reused directly for per-item rank comparison since its declaration order is already documented as load-bearing (17-04); a separate rank map was only introduced for OrderStatus (FULFILLMENT_RANK) per the plan's explicit interface."

patterns-established:
  - "Multi-step monotonic rank guard (FULFILLMENT_RANK) for order-status projections driven by another context's event, reusable by any future consumer needing a >1-step forward-only status guard."

requirements-completed: [D-04]

# Metrics
duration: 5min
completed: 2026-07-08
---

# Phase 17 Plan 07: Order-side kitchen fulfillment projection Summary

**Order_context now derives its aggregate order status from kitchen's KitchenTicketStatusChangedEvent per-item snapshot via a FULFILLMENT_RANK forward-only, idempotent guard, closing the D-04 fulfillment loop without ever letting kitchen write OrderEntity directly.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-07-08T15:23:00+07:00
- **Completed:** 2026-07-08T15:26:12+07:00
- **Tasks:** 2 completed
- **Files modified:** 5 created (2 main config/service/listener classes split across 3 files + 2 test files)

## Accomplishments
- Poison-pill-safe order-side Kafka consumer (`TicketStatusChangedKafkaConsumerConfig`) trusting the cross-context `kitchen_context.application.event` package, with distinctly-prefixed bean names to avoid app-wide collisions
- `KitchenStatusProjectionService` derives the order's aggregate status from the FULL per-item snapshot: any item still PREPARING keeps/sets PREPARING; all items READY/SERVED/COMPLETED advance to that stage
- `FULFILLMENT_RANK` map enforces a strictly-forward guard so replayed or out-of-order deliveries can never regress the order, and REJECTED orders (terminal, D-11) are never touched
- Idempotent via the existing `order_processed_events` ledger (insert+flush, catch unique-violation), consumer identity `kitchen-status-projection`
- `TicketStatusChangedListener` is a one-line `@KafkaListener` delegate, keeping all logic in the service

## Task Commits

Each task was committed atomically:

1. **Task 1: TicketStatusChangedKafkaConsumerConfig** - `3feaa78` (feat)
2. **Task 2: KitchenStatusProjectionService + thin listener** - TDD cycle:
   - RED: `d998eb5` (test) - failing test written against the not-yet-existing service (compile-fail RED)
   - GREEN: `8bfa59c` (feat) - service + listener implemented, all 9 tests pass

## TDD Gate Compliance

Task 2 (`tdd="true"`) followed the mandatory RED -> GREEN sequence:
- RED gate commit: `d998eb5` (`test(17-07): add failing test for KitchenStatusProjectionService`) - verified failing (compile error: `KitchenStatusProjectionService` did not exist) before any implementation was written
- GREEN gate commit: `8bfa59c` (`feat(17-07): implement KitchenStatusProjectionService and thin listener`) - all 9 tests pass after implementation
- No REFACTOR commit was needed; the initial implementation required no cleanup after GREEN

## Files Created/Modified
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/TicketStatusChangedKafkaConsumerConfig.java` - poison-pill-safe consumer wiring, trusts `kitchen_context.application.event`, distinctly-prefixed beans
- `src/main/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionService.java` - idempotent, rank-guarded, forward-only order-status projection from the kitchen per-item snapshot
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/TicketStatusChangedListener.java` - one-line `@KafkaListener` delegate
- `src/test/java/com/example/feat1/DDD/order_context/infrastructure/config/TicketStatusChangedKafkaConsumerConfigTest.java` - broker-free config wiring assertions
- `src/test/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionServiceTest.java` - 9 tests covering derivation rules, rank-guard non-regression, REJECTED terminal state, and idempotency

## Decisions Made
- Status derivation precedence: ANY item PREPARING -> PREPARING (even if others are further along but not all reached the same stage); ALL items must reach READY/SERVED/COMPLETED for the order to advance there. This matches the plan's explicit D-04 rule and the natural "order isn't ready until every item is" semantics.
- No explicit `orderRepository.save()` call — consistent with the existing `OrderConfirmationService` pattern that relies on JPA dirty-checking inside the `@Transactional` method; the guard already ensures `setStatus` is only called when the value actually changes.
- Reused `KitchenItemStatus.ordinal()` directly for per-item rank comparisons (its declaration order was already pinned as load-bearing in plan 17-04), avoiding a redundant rank map on the kitchen side.

## Deviations from Plan

None - plan executed exactly as written. All interfaces (KitchenTicketStatusChangedEvent, KitchenItemStatus, OrderStatus with pinned ordering, OrderProcessedEventRepository/OrderRepository) were imported/reused as specified, with no redeclaration of cross-context types.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- D-04's consumer half is complete: order_context now reflects kitchen fulfillment purely by event, forward-only and idempotently, with REJECTED orders untouched and kitchen never mutating OrderEntity directly.
- Full Maven suite green (179 tests, 0 failures/errors) after this plan, including the new 12 tests (3 config + 9 service).
- No blockers for subsequent Phase 17 verification/wrap-up plans.

---
*Phase: 17-kitchen-context*
*Completed: 2026-07-08*
