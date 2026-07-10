---
phase: 18-order-and-order-item-cancellation-with-compensation-release-
plan: 03
subsystem: inventory_context
tags: [kafka, compensation, idempotency, reservation-release, cancel-05]
dependency-graph:
  requires:
    - 18-01 (OrderCancelledEvent contract, order.events.order-cancelled-topic property)
  provides:
    - InventoryReservationReleaseService (reservation release compensation for cancelled order lines)
    - RESERVATION_RELEASE audit movement type
    - ReservationStatus.RELEASED terminal state
    - InventoryLineReleaseEntity / InventoryLineReleaseRepository (per-line release ledger)
    - OrderCancelledInventoryListener + OrderCancelledKafkaConsumerConfig (orders.cancelled consumer, inventory side)
  affects:
    - InventoryReservationSettlementService (completion guard widened to settledCount + releasedCount)
tech-stack:
  added: []
  patterns:
    - "Ledger-insert-last-in-same-tx idempotency idiom (not the isolated InventoryLedgerWriter REQUIRES_NEW writer)"
    - "Dual idempotency guard: eventId ledger + per-(orderId,orderLineId) ledger row, plus a per-line skip inside the loop for overlapping redeliveries"
    - "Re-resolve recipe per line via OrderLineLookupPort + RecipeRequirementResolver, never read StockReservationEntity.getLines()"
    - "Lock reservation row before ingredient balance rows, ascending-ingredientId order (deadlock-free, matches settlement)"
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryLineReleaseEntity.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/repository/InventoryLineReleaseRepository.java
    - src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationReleaseService.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/adapter/OrderCancelledInventoryListener.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/OrderCancelledKafkaConsumerConfig.java
    - src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationReleaseServiceTest.java
  modified:
    - src/main/java/com/example/feat1/DDD/inventory_context/domain/model/InventoryMovementType.java (append RESERVATION_RELEASE)
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/StockReservationEntity.java (append ReservationStatus.RELEASED)
    - src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationSettlementService.java (widen completion guard to settledCount + releasedCount)
    - src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationSettlementServiceTest.java (inject new InventoryLineReleaseRepository mock)
    - src/main/java/com/example/feat1/DDD/inventory_context/domain/model/InventoryDomainException.java (add releaseReservationMissing/releaseOrderLineMissing factories)
decisions:
  - "Release-completion guard sets ReservationStatus.RELEASED whenever settledCount + releasedCount >= totalLines, exactly as the plan/pattern map specifies (no extra composite-state branching for a mixed settled/released scenario) — whichever service (settlement or release) processes the last remaining line wins the final status write."
  - "Per-event idempotency short-circuit checks 'every cancelled line already released'; the per-line loop additionally skips any individual already-released line so a partially-overlapping redelivery cannot double-decrement."
metrics:
  duration: "~1h"
  completed: 2026-07-10
---

# Phase 18 Plan 03: Inventory reservation-release compensation consumer Summary

Added the Inventory-side compensation consumer that releases held stock reservations for
cancelled order lines by re-resolving each line's recipe and decrementing `reservedQuantity`
only (never `quantityOnHand`) — the exact structural inverse of `InventoryReservationSettlementService`.

## What was built

1. **Enums + per-line release ledger** — `InventoryMovementType.RESERVATION_RELEASE` (appended),
   `StockReservationEntity.ReservationStatus.RELEASED` (appended), and
   `InventoryLineReleaseEntity`/`InventoryLineReleaseRepository` (table
   `inventory_line_releases`, unique `(order_id, order_line_id)`) as the structural inverse of the
   existing settlement ledger. `InventoryReservationSettlementService`'s flip-to-SETTLED
   completion guard was widened to `settledCount + releasedCount >= totalLines` so a mixed
   settled/released order still reaches a terminal reservation state.

2. **`InventoryReservationReleaseService`** (built TDD RED → GREEN) — `@Transactional
   onOrderCancelled(OrderCancelledEvent event)`: dual idempotency (eventId ledger + "every
   cancelled line already released" pre-check, plus a per-line skip inside the loop for
   partially-overlapping redeliveries); locks the reservation row before ingredient balance rows
   in ascending-ingredientId order; re-resolves each cancelled line's recipe via
   `OrderLineLookupPort` + `RecipeRequirementResolver` (never reads
   `StockReservationEntity.getLines()`); decrements `reservedQuantity` only with a non-negative
   clamp; writes a `RESERVATION_RELEASE` audit movement per ingredient with `actorId = null`;
   records `InventoryLineReleaseEntity` per line; flips the reservation to `RELEASED` when
   `settledCount + releasedCount >= totalLines`; and inserts the idempotency-ledger row LAST in
   the same transaction. A missing reservation/order-line throws (routed to retry then DLT); a
   non-HELD reservation is a benign no-op.

3. **Kafka wiring** — `OrderCancelledInventoryListener` (thin `@KafkaListener` delegate on
   `orders.cancelled`) + `OrderCancelledKafkaConsumerConfig` (bean-for-bean copy of
   `SettleTriggerKafkaConsumerConfig`: `ErrorHandlingDeserializer` + Jackson-3
   `JacksonJsonDeserializer`, `TRUSTED_PACKAGES` pinned to `order_context.application.event`,
   `DeadLetterPublishingRecoverer` reusing the existing `inventoryDltKafkaTemplate` bean — no new
   DLT template — `FixedBackOff(1000L, 3L)` with `DeserializationException` non-retryable).

## Verification

- `./mvnw -q -Dtest=InventoryReservationReleaseServiceTest test` — 11/11 pass (GREEN)
- `./mvnw -q -Dtest=InventoryReservationSettlementServiceTest test` — still green after the widened
  completion guard
- Full Maven suite: 226/226 tests pass, 0 failures, 0 errors (53 test classes)
- `grep -n setQuantityOnHand` on `InventoryReservationReleaseService.java` returns only a
  Javadoc/comment mention — the method is never called
- `OrderCancelledKafkaConsumerConfig` contains no new `KafkaTemplate` DLT bean (reuses
  `@Qualifier("inventoryDltKafkaTemplate")`) and no legacy Jackson-2
  `org.springframework.kafka.support.serializer.JsonDeserializer` usage

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Injected `InventoryLineReleaseRepository` into
`InventoryReservationSettlementService`'s test constructor**
- **Found during:** Task 1
- **Issue:** Widening `InventoryReservationSettlementService`'s completion guard (per the task
  action) added a new `final` constructor field (`@RequiredArgsConstructor`), which broke
  `InventoryReservationSettlementServiceTest`'s manual constructor call — a compile error.
- **Fix:** Added a `lineReleaseRepository` mock to the test with a default
  `countByOrderId(any()) -> 0L` stub so pre-existing settlement-only test behavior is unchanged.
- **Files modified:** `src/test/java/.../InventoryReservationSettlementServiceTest.java`
- **Commit:** 26d0a4d

**2. [Rule 3 - Blocking] Added `InventoryDomainException.releaseReservationMissing` /
`releaseOrderLineMissing` factory methods**
- **Found during:** Task 2
- **Issue:** `InventoryDomainException.java` is not in the plan's `files_modified` list, but the
  release service (mirroring settlement's `settlementReservationMissing`/
  `settlementOrderLineMissing`) needs its own release-specific exception factories to compile and
  to route a missing reservation/order-line to the DLT correctly.
- **Fix:** Added `RELEASE_RESERVATION_MISSING`/`RELEASE_ORDER_LINE_MISSING` codes and two factory
  methods, structurally identical to the settlement equivalents, left OUT of the Kafka
  non-retryable set (same rationale: a transient cancel-before-reserve ordering race self-heals via
  retry).
- **Files modified:** `src/main/java/.../InventoryDomainException.java`
- **Commit:** f221407 (RED phase)

None of the auto-fixes required architectural changes; both were compile-time necessities
directly caused by the planned work in this plan's own tasks.

## TDD Gate Compliance

Task 2 (`tdd="true"`) followed the full RED → GREEN cycle:
- RED: commit `f221407` — `test(18-03): add failing test for InventoryReservationReleaseService
  (TDD RED)` — a stub service was added (fields + no-op method) so the test compiled; 7/11
  assertions failed as expected.
- GREEN: commit `63b336b` — `feat(18-03): implement InventoryReservationReleaseService (TDD
  GREEN)` — full implementation; 11/11 assertions pass.
- No REFACTOR commit was needed (the GREEN implementation required no further cleanup).

## Self-Check: PASSED

All created files exist on disk and all four task commits are present in `git log`.
