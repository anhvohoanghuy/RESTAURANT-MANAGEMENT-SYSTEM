---
phase: 18-order-and-order-item-cancellation-with-compensation-release-
plan: 06
subsystem: kitchen-context
tags: [ddd, kafka-consumer, idempotency, pessimistic-locking, defense-in-depth, order-cancellation, tdd]

# Dependency graph
requires:
  - phase: 18-01
    provides: "OrderCancelledEvent outbox contract (eventId, orderId, wholeOrder, cancelledLineIds, totalLines) + orders.cancelled topic property"
  - phase: 18-02
    provides: "KitchenTicketItemRepository (shared repository, extended in this plan with an orderLineId-keyed lock query)"
provides:
  - "KitchenItemStatus.CANCELLED terminal enum value + forward-only advance-guard rejection of any advance from/to CANCELLED"
  - "KitchenTicketItemRepository.lockByOrderIdAndOrderLineId(UUID, UUID) dual-key PESSIMISTIC_WRITE lock query"
  - "KitchenTicketInvalidationService: guarded, idempotent void of a cancelled line's still-QUEUED kitchen item"
  - "OrderCancelledKitchenListener + OrderCancelledKitchenKafkaConsumerConfig wiring orders.cancelled into the kitchen context with poison-pill/DLT protection"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Ledger-last-in-tx idempotency (idiom 1, mirrors KitchenTicketCreationService): pre-check the processed-events ledger cheaply, do the guarded work, then save the ledger row LAST in the same @Transactional method so a concurrent-duplicate unique violation rolls back the whole transaction (including any item voids already applied) instead of leaking a partially-processed cancellation"
    - "Lock-then-guarded-mutate per line (mirrors KitchenTicketAdvanceService): lockByOrderIdAndOrderLineId before checking status, so a concurrent staff advance of the same item is serialized against the void instead of racing it"
    - "Switch-based (not ordinal-based) forward-only transition guard: appending a new terminal enum value at the END of an ordinal-documented enum still requires an explicit exhaustive-switch case, since Java enum switches have no default fallthrough for new constants"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketInvalidationService.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/OrderCancelledKitchenListener.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/OrderCancelledKitchenKafkaConsumerConfig.java
    - src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketInvalidationServiceTest.java
  modified:
    - src/main/java/com/example/feat1/DDD/kitchen_context/domain/model/KitchenItemStatus.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketItemRepository.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceService.java
    - src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceServiceTest.java

key-decisions:
  - "CANCELLED appended as the LAST enum value (after COMPLETED), preserving the existing QUEUED->PREPARING->READY->SERVED->COMPLETED ordinal-ordering comment; the advance-guard rejection is enforced by an explicit `case CANCELLED -> false;` branch in the exhaustive switch, not by ordinal position — the plan explicitly warned ordinal placement alone does not make the guard reject a CANCELLED source state"
  - "Regression assertion for 'CANCELLED cannot be advanced' was added to the existing KitchenTicketAdvanceServiceTest (new test advancingFromVoidedCancelledIsRejected) rather than the new KitchenTicketInvalidationServiceTest, since the invariant belongs to the advance service's own transition guard, not the invalidation service"
  - "Reused kitchen's existing kitchenDltKafkaTemplate bean (declared in OrderConfirmedKafkaConsumerConfig) via @Qualifier injection in the new OrderCancelledKitchenErrorHandler bean, per the plan's explicit instruction not to create a second DLT KafkaTemplate"

patterns-established:
  - "TDD RED/GREEN split for a brand-new consumer service: RED commit is the full behavior-complete test file referencing a not-yet-existing service class (compile-failure RED, since Mockito mocks require the class to exist); GREEN commit adds only the service implementation, no test changes"

requirements-completed: [CANCEL-08]

# Metrics
duration: 25min
completed: 2026-07-10
---

# Phase 18 Plan 06: Kitchen Cancellation Compensation (D-7 Defensive Void) Summary

**Kitchen-side OrderCancelled consumer that voids a cancelled line's still-QUEUED kitchen item to a new terminal CANCELLED status (never touching anything already >= PREPARING), closing the cancel-vs-advance race that would otherwise let staff advance a released line into a rogue SettleTrigger stock double-decrement.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-07-10T04:55:00Z (approx, worktree spawn)
- **Completed:** 2026-07-10T05:05:15Z
- **Tasks:** 3
- **Files modified:** 8 (4 created, 4 modified)

## Accomplishments
- Appended `KitchenItemStatus.CANCELLED` as the enum's LAST value (preserving the load-bearing QUEUED→COMPLETED ordinal-ordering comment) and added the mandatory `case CANCELLED -> false;` branch to `KitchenTicketAdvanceService.isValidTransition`'s exhaustive switch — both fixing the compile break the new enum constant introduced and making a voided item a true terminal state; added a regression test (`advancingFromVoidedCancelledIsRejected`) asserting no advance, save, or publish occurs from `CANCELLED`
- Added `KitchenTicketItemRepository.lockByOrderIdAndOrderLineId(UUID orderId, UUID orderLineId)`, a dual-key `PESSIMISTIC_WRITE` lock query keyed on the inbound event's `orderLineId` (not the kitchen item's own `id`), leaving `lockByOrderIdAndItemId` untouched
- Implemented `KitchenTicketInvalidationService.onOrderCancelled` via strict TDD (RED: 8-test behavior-complete test file against a non-existent class → compile failure; GREEN: service implementation, all 8 green): ledger pre-check, per-cancelled-line lock-then-guarded-mutate (`QUEUED` → `CANCELLED` only; `>= PREPARING` never touched; absent item skipped), ledger row saved LAST in the same transaction
- Wired `OrderCancelledKitchenListener` (thin `@KafkaListener` delegate) + `OrderCancelledKitchenKafkaConsumerConfig` (bean-for-bean analog of `OrderConfirmedKafkaConsumerConfig`: `ErrorHandlingDeserializer` + Jackson-3 `JacksonJsonDeserializer`, trusted package pinned to `order_context.application.event`, `USE_TYPE_INFO_HEADERS=false`, `FixedBackOff(1000, 3)` + non-retryable `DeserializationException` → DLT) onto `orders.cancelled`, reusing kitchen's existing `kitchenDltKafkaTemplate` bean rather than declaring a new one

## Task Commits

Each task was committed atomically:

1. **Task 1: Append CANCELLED status, orderLineId lock query, advance-guard regression** - `c8e580b` (feat)
2. **Task 2: KitchenTicketInvalidationService (guarded, idempotent void)** - RED `ebe6c3d` (test), GREEN `fbe4b1b` (feat)
3. **Task 3: Kafka listener + consumer config for orders.cancelled (kitchen)** - `83fff83` (feat)

_Task 2 is `tdd="true"`; no REFACTOR commit was needed — the GREEN implementation required no follow-up cleanup._

## TDD Gate Compliance

Gate sequence verified in git log for Task 2:
1. RED gate: `ebe6c3d test(18-06): add failing test for KitchenTicketInvalidationService` — compiled against a not-yet-existing `KitchenTicketInvalidationService` class, confirmed failing before implementation.
2. GREEN gate: `fbe4b1b feat(18-06): implement KitchenTicketInvalidationService (guarded, idempotent void)` — all 8 tests pass.
3. No REFACTOR commit required.

## Files Created/Modified
- `src/main/java/com/example/feat1/DDD/kitchen_context/domain/model/KitchenItemStatus.java` - Appended `CANCELLED` as the last value, with Javadoc explaining the switch-based (not ordinal-based) terminal guard
- `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketItemRepository.java` - Added `lockByOrderIdAndOrderLineId(UUID, UUID)`; `lockByOrderIdAndItemId` unchanged
- `src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceService.java` - Added `case CANCELLED -> false;` to the exhaustive transition-guard switch
- `src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceServiceTest.java` - New regression test `advancingFromVoidedCancelledIsRejected`
- `src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketInvalidationService.java` - New `@Service`: `onOrderCancelled(OrderCancelledEvent)`
- `src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketInvalidationServiceTest.java` - New test class, 8 tests, mock-based (`KitchenProcessedEventRepository`, `KitchenTicketItemRepository`)
- `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/OrderCancelledKitchenListener.java` - New thin `@KafkaListener` delegate on `orders.cancelled`
- `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/OrderCancelledKitchenKafkaConsumerConfig.java` - New consumer/container-factory/error-handler beans, reuses `kitchenDltKafkaTemplate`

## Decisions Made
- Kept `KitchenItemStatus.CANCELLED` strictly appended-last per the plan's explicit ordinal-preservation instruction, and enforced non-advanceability via the switch expression (not ordinal comparison), matching the plan's explicit warning that the guard is switch-based
- Placed the advance-guard regression test in the existing `KitchenTicketAdvanceServiceTest` rather than creating a separate test — the invariant under test ("a CANCELLED item cannot be advanced") is owned by `KitchenTicketAdvanceService`, not the new invalidation service
- Followed the plan's explicit "reuse kitchen's existing DLT template" instruction literally: `OrderCancelledKitchenKafkaConsumerConfig` declares no new `KafkaTemplate` bean, injecting `kitchenDltKafkaTemplate` by `@Qualifier`

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Focused test suite (`KitchenTicketInvalidationServiceTest`, 8/8) and the full Maven suite (247/247, 0 failures/errors) passed after the final commit; a project auto-formatter/linter reformatted whitespace and comment wrapping in the newly created/modified files but made no semantic changes.

## User Setup Required

None - no external service configuration required. `kitchen.order-cancelled.consumer.group-id` and `order.events.order-cancelled-topic` both resolve via existing property defaults / the property already present in `application.properties` from Plan 01.

## Next Phase Readiness
- CANCEL-08 (D-7) is closed: the kitchen ticket cancel-vs-advance race now has a defensive backstop independent of Plan 02's synchronous read.
- This is the final plan of Wave 3 / Phase 18; no downstream plans depend on this one within the phase.
- No blockers identified.

---
*Phase: 18-order-and-order-item-cancellation-with-compensation-release-*
*Completed: 2026-07-10*

## Self-Check: PASSED
