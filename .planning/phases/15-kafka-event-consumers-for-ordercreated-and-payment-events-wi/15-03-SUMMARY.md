---
phase: 15-kafka-event-consumers
plan: 03
subsystem: inventory_context
tags: [inventory, saga, reservation, idempotency, concurrency, domain-logic]
requires:
  - 15-01 OrderStockResultEvent saga-result contract
  - 15-02 reservation persistence (balance lock, StockReservationEntity, processed-events ledger)
  - Existing costing recipe-resolution path (MenuRecipeCostingPort, UnitConverter, IngredientRepository)
provides:
  - InventoryStockResultPublisher outbound port (publishStockResult)
  - InventoryReservationService.onOrderCreated saga handler (idempotent, atomic, deadlock-safe, after-commit publish)
affects:
  - 15-04 inventory Kafka listener (thin delegate to onOrderCreated)
  - 15-05 InventoryStockResultPublisher Kafka adapter
tech-stack:
  added: []
  patterns:
    - Idempotency via ledger saveAndFlush + DataIntegrityViolationException guard (insert forces flush under GenerationType.UUID @Id)
    - Canonical sorted-ingredientId pessimistic lock order for concurrent-reserver deadlock avoidance
    - After-commit publish via TransactionSynchronization (mirrors OrderSubmissionService)
    - Reuse of shared costing recipe-resolution + UnitConverter with uncosted/zero tolerance
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/inventory_context/domain/port/InventoryStockResultPublisher.java
    - src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java
    - src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationServiceTest.java
  modified: []
decisions:
  - "Idempotency ledger uses saveAndFlush (not save) so the INSERT flushes immediately and a concurrent-duplicate unique violation surfaces as DataIntegrityViolationException inside the guarded block rather than at commit."
  - "Shortfall ingredientName resolved from the locked balance's ingredient when present, else via IngredientRepository lookup (covers the missing-balance shortfall case)."
  - "Required-quantity map iterated in ascending-UUID order for both the availability-check and reserve loops so every concurrent reserver acquires row locks in the same sequence."
requirements: [D-02, D-03, D-06, D-09, D-10, D-11]
metrics:
  duration: ~15m
  completed: 2026-07-07
---

# Phase 15 Plan 03: Inventory Reservation Saga Handler Summary

Implemented the core reservation saga domain logic: `InventoryReservationService.onOrderCreated` consumes an `OrderCreatedEvent`, resolves per-ingredient required quantities through the shared costing recipe path with base-unit conversion, checks `available = onHand - reserved` under pessimistic locks acquired in canonical sorted order, reserves-or-rejects atomically and idempotently, and publishes the `OrderStockResultEvent` only after commit through a new outbound port.

## What Was Built

- **Task 1** — `InventoryStockResultPublisher` (one-method outbound port mirroring `OrderEventPublisher`) and `InventoryReservationService` (`@Service @RequiredArgsConstructor`). `onOrderCreated` (`@Transactional`) implements the five-step saga:
  1. Idempotency guard — `existsByEventIdAndConsumerName(eventId, "inventory-order-created")` + `existsByOrderId(orderId)` fast pre-checks, then `saveAndFlush` the ledger row inside a `try/catch (DataIntegrityViolationException)` so a concurrent duplicate returns cleanly (D-03 / T-15-03).
  2. `computeRequired` — for each order line, resolve the DISH recipe and each selected topping's TOPPING_OPTION recipe via `menuRecipeCostingPort.findRecipe`, convert each recipe-line quantity to the ingredient base unit (`IngredientRepository.findById().getBaseUnit()` + `UnitConverter.convert`) and multiply by the order line quantity. Missing recipe / null ingredient link / unknown ingredient / unconvertible unit contribute ZERO and are logged, never thrown (D-06 / T-15-06).
  3. Canonical ascending-UUID iteration order used for both the availability-check and reserve loops (D-11 / T-15-11); each ingredient row is acquired via `lockByIngredientAndLocation` (PESSIMISTIC_WRITE).
  4. Reserve only when all ingredients are sufficient — increment `reservedQuantity` on the locked rows (available stays `>= 0`, D-02 / T-15-02) and persist `StockReservationEntity.held(orderId, required)` (D-09); otherwise build a REJECTED event carrying per-ingredient shortfalls.
  5. `publishAfterCommit` (copied from `OrderSubmissionService`) emits the result only after the transaction commits (D-10 / T-15-04).
- **Task 2** — `InventoryReservationServiceTest` (JUnit 5 + AssertJ + plain Mockito, no Spring context). Tests run outside an active transaction so `publishAfterCommit` invokes the publisher synchronously and the emitted event is captured with `ArgumentCaptor`.

## Verification

- `./mvnw -q compile` passed after Task 1.
- `./mvnw -Dtest=InventoryReservationServiceTest test`: **6/6 green** covering reserve-and-confirm, reject-with-shortfall (no reservation/balance mutation), dish+topping resolution with missing-recipe-as-zero, both idempotency guards (zero side effects), and canonical ascending-ingredientId lock order (`InOrder`).

## Task Commits

1. **Task 1: reservation saga handler + stock-result publisher port** — `518e051` (feat)
2. **Task 2: unit-test the reservation handler** — `7d1d67b` (test)

## Deviations from Plan

None - plan executed exactly as written.

## Threat Mitigations Applied

- **T-15-02** (negative available): reserve only when all ingredients sufficient; `reservedQuantity` incremented on PESSIMISTIC_WRITE-locked rows.
- **T-15-03** (replay double-reserve): ledger `saveAndFlush` + unique-constraint catch + `existsByOrderId` guard in the same transaction.
- **T-15-04** (spurious CONFIRMED on rollback): `publishAfterCommit` emits only after commit.
- **T-15-06** (malformed line poison-pill): missing recipe / null ingredient / unconvertible unit tolerated as zero, never throws.
- **T-15-11** (lock-ordering deadlock): canonical sorted-ingredientId lock order for check and reserve loops.

## Notes for Downstream Plans

- 15-04 listener must be a thin delegate calling `InventoryReservationService.onOrderCreated(OrderCreatedEvent)` — all business logic lives in this service.
- 15-05 must supply the Kafka adapter implementing `InventoryStockResultPublisher.publishStockResult`; without a bean the context will not wire this service.
- The idempotency consumer name is the public constant `InventoryReservationService.CONSUMER_NAME` (`"inventory-order-created"`).

## Self-Check: PASSED
