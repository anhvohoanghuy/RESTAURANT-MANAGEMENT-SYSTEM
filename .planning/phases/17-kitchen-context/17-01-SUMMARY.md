---
phase: 17-kitchen-context
plan: 01
subsystem: api
tags: [kafka, order-context, event-driven, spring-transaction]

# Dependency graph
requires:
  - phase: 15-kafka-event-consumers-for-ordercreated-and-payment-events-wi
    provides: OrderConfirmationService.onStockResult idempotent CONFIRMED/REJECTED transition, OrderCreatedEvent line-manifest shape to clone
  - phase: 16-inventory-reservation-settlement
    provides: SettleTriggerEvent contract that a later 17-plan will produce toward
provides:
  - OrderStatus extended with PREPARING, READY, SERVED, COMPLETED (load-bearing declaration order, before REJECTED)
  - OrderConfirmedEvent record (self-contained confirmed-order line manifest) in order_context.application.event
  - OrderEventPublisher.publishOrderConfirmed port method + KafkaOrderEventPublisher adapter implementation + orders.confirmed topic wiring
  - OrderConfirmationService publishes exactly one OrderConfirmedEvent after commit on the CONFIRMED transition
affects: [17-02, 17-03, 17-04, 17-05, 17-06, 17-07]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "publishAfterCommit via TransactionSynchronizationManager.registerSynchronization (copied verbatim from OrderSubmissionService) — no outbox table, event only leaves after the DB transaction actually commits"
    - "Dual KafkaTemplate fields of different generic parameter type on one @Component, disambiguated by Spring's generic-aware injection (matches the field's declared ResolvableType against each @Bean factory method's declared return type) — no @Qualifier needed"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/order_context/application/event/OrderConfirmedEvent.java
  modified:
    - src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java
    - src/main/java/com/example/feat1/DDD/order_context/domain/port/OrderEventPublisher.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/KafkaOrderEventPublisher.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaProducerConfig.java
    - src/main/java/com/example/feat1/DDD/order_context/application/OrderConfirmationService.java
    - src/test/java/com/example/feat1/DDD/order_context/application/OrderConfirmationServiceTest.java

key-decisions:
  - "OrderConfirmedEvent drops pricing fields (basePrice/unitPrice/lineTotal/toppingsTotal/additionalPrice) that OrderCreatedEvent carries — kitchen only needs dish/topping identity plus quantity, per D-01"
  - "OrderStatus keeps REJECTED as the last enum value (terminal rejection branch); PREPARING/READY/SERVED/COMPLETED inserted between CONFIRMED and REJECTED with a code comment pinning the order as load-bearing for plan 17-07's rank guard"

patterns-established:
  - "publishAfterCommit(event) private helper per publisher-owning service: no-op passthrough if no active transaction sync, else registerSynchronization(afterCommit -> publish)"

requirements-completed: [D-01, D-04]

# Metrics
duration: 27min
completed: 2026-07-08
---

# Phase 17 Plan 01: Order-side OrderConfirmed producer + OrderStatus fulfillment values Summary

**order_context now publishes a self-contained OrderConfirmedEvent (full per-line manifest) after transaction commit on the CONFIRMED transition, and OrderStatus carries the four new fulfillment values kitchen will later drive.**

## Performance

- **Duration:** 27 min
- **Started:** 2026-07-08T14:40:15+07:00 (prior commit baseline)
- **Completed:** 2026-07-08T15:07:12+07:00
- **Tasks:** 3 (Task 3 executed as TDD: RED then GREEN)
- **Files modified:** 6 (1 new, 5 modified)

## Accomplishments
- `OrderStatus` extended with `PREPARING, READY, SERVED, COMPLETED` (declared between `CONFIRMED` and `REJECTED`, with an explicit load-bearing-order comment for the future 17-07 rank guard)
- New `OrderConfirmedEvent` record (`eventId, eventType, occurredAt, orderId, lines[]`) with nested `OrderConfirmedLine`/`OrderConfirmedTopping`, self-contained (no cross-context lookup needed by kitchen)
- `OrderEventPublisher` port + `KafkaOrderEventPublisher` adapter + `OrderKafkaProducerConfig` extended with a second, independent `OrderConfirmedEvent` producer pair (topic default `orders.confirmed`), leaving the existing `orders.created` path completely untouched
- `OrderConfirmationService.onStockResult` now publishes exactly one `OrderConfirmedEvent` after commit on the `CONFIRMED` branch (via `publishAfterCommit`/`TransactionSynchronizationManager`), and publishes nothing on `REJECTED`, on duplicate/idempotent replay, or when the order is not `PENDING_CONFIRMATION`

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend OrderStatus and create OrderConfirmedEvent record** - `bf5b960` (feat)
2. **Task 2: Extend publisher port, adapter, and producer config for OrderConfirmed** - `91ea650` (feat)
3. **Task 3: Publish OrderConfirmed after commit in OrderConfirmationService** - `fddfd0d` (test, RED) → `2095992` (feat, GREEN)

**Plan metadata:** (this commit, docs: complete plan — see final commit below)

## Files Created/Modified
- `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java` - added PREPARING/READY/SERVED/COMPLETED before REJECTED, with load-bearing-order comment
- `src/main/java/com/example/feat1/DDD/order_context/application/event/OrderConfirmedEvent.java` - new self-contained confirmed-order line manifest record
- `src/main/java/com/example/feat1/DDD/order_context/domain/port/OrderEventPublisher.java` - added `publishOrderConfirmed`
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/KafkaOrderEventPublisher.java` - second `KafkaTemplate<String, OrderConfirmedEvent>` field, `orders.confirmed` topic default, `publishOrderConfirmed` one-liner
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaProducerConfig.java` - added `orderConfirmedProducerFactory`/`orderConfirmedKafkaTemplate` bean pair
- `src/main/java/com/example/feat1/DDD/order_context/application/OrderConfirmationService.java` - injected `OrderEventPublisher`, added `toOrderConfirmedEvent`/`toOrderConfirmedLine` mappers and `publishAfterCommit`, called from the CONFIRMED branch of `onStockResult`
- `src/test/java/com/example/feat1/DDD/order_context/application/OrderConfirmationServiceTest.java` - extended with manifest-content assertion, REJECTED/duplicate/non-pending non-publish assertions, and an after-commit-registration (not mid-transaction) assertion

## Decisions Made
- Dropped pricing fields from the `OrderConfirmedEvent` line/topping manifest (kitchen only needs dish/topping identity + quantity per D-01), matching the plan's explicit interface spec rather than reusing `OrderCreatedEvent.OrderLine`/`OrderTopping` verbatim.
- Two `KafkaTemplate` fields of different generic type coexist on `KafkaOrderEventPublisher` without `@Qualifier` — verified this resolves correctly via Spring's generic-aware autowiring (confirmed by the full Maven test suite run, no `NoUniqueBeanDefinitionException`).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `OrderConfirmedEvent` on topic `orders.confirmed` is ready for kitchen_context's consumer (plan 17-02+) to build `KitchenTicket` aggregates from.
- `OrderStatus` now has the full fulfillment vocabulary (`PREPARING/READY/SERVED/COMPLETED`) the order-side ticket-status-changed projection (later plan) will apply via a forward-only rank guard.
- No blockers identified for downstream kitchen_context plans.

## Self-Check: PASSED

- FOUND: src/main/java/com/example/feat1/DDD/order_context/application/event/OrderConfirmedEvent.java
- FOUND: src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java
- FOUND: src/main/java/com/example/feat1/DDD/order_context/domain/port/OrderEventPublisher.java
- FOUND: src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/KafkaOrderEventPublisher.java
- FOUND: src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaProducerConfig.java
- FOUND: src/main/java/com/example/feat1/DDD/order_context/application/OrderConfirmationService.java
- FOUND commit bf5b960
- FOUND commit 91ea650
- FOUND commit fddfd0d
- FOUND commit 2095992

## TDD Gate Compliance

Task 3 (`tdd="true"`) followed the RED/GREEN cycle: `fddfd0d` (test, RED — compile failure confirming the new constructor/publish shape was required) then `2095992` (feat, GREEN — all 5 tests in `OrderConfirmationServiceTest` pass). No REFACTOR commit was needed (no cleanup required after GREEN).

---
*Phase: 17-kitchen-context*
*Completed: 2026-07-08*
