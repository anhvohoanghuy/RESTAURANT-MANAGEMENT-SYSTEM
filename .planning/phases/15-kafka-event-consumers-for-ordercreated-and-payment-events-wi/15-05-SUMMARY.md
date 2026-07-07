---
phase: 15-kafka-event-consumers
plan: 05
subsystem: order_context
tags: [saga, order, confirmation, idempotency, jpa, transition]
requires:
  - Plan 15-01 (OrderStatus lifecycle, OrderStockResultEvent contract, OrderEntity.rejectionReason)
  - Plan 15-02 (idempotency ledger pattern: unique (event_id, consumer_name) + insert/flush guard)
provides:
  - OrderProcessedEventEntity (order_processed_events ledger) + repository
  - OrderConfirmationService.onStockResult — idempotent, status-guarded saga completion handler
affects:
  - Plan 15-06 (order-side Kafka listener will call OrderConfirmationService.onStockResult)
tech-stack:
  added: []
  patterns:
    - "Unique-constraint idempotency ledger (event_id, consumer_name) mirrored per bounded context"
    - "saveAndFlush + catch DataIntegrityViolationException to surface duplicate INSERT inside the guarded block (GenerationType.UUID defers INSERT to commit without flush)"
    - "Status-guarded terminal transition (only PENDING_CONFIRMATION may move to CONFIRMED/REJECTED)"
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/entity/OrderProcessedEventEntity.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/repository/OrderProcessedEventRepository.java
    - src/main/java/com/example/feat1/DDD/order_context/application/OrderConfirmationService.java
    - src/test/java/com/example/feat1/DDD/order_context/application/OrderConfirmationServiceTest.java
  modified: []
decisions:
  - "Ledger consumer identity constant CONSUMER_NAME = \"order-stock-result\" lives on the service (package-visible) so the 15-06 listener can reuse it if needed."
  - "describe(shortfalls) produces a stable human-readable rejection reason listing ingredient name + required/available; empty/null shortfalls fall back to \"Insufficient stock\"."
requirements-completed: [D-03, D-10, D-11]
metrics:
  duration: ~12m
  completed: 2026-07-07
---

# Phase 15 Plan 05: Order-side Saga Completion Summary

**Implemented the terminal half of the order-confirmation saga: an `order_processed_events` idempotency ledger plus an `OrderConfirmationService.onStockResult` that idempotently and status-safely transitions a `PENDING_CONFIRMATION` order to `CONFIRMED`, or `REJECTED` with a shortfall-derived reason — isolated from Kafka wiring so the transition/idempotency logic is directly unit-tested.**

## What Was Built

- **Task 1** — Created `OrderProcessedEventEntity` mapping the physical table `order_processed_events` with a unique constraint `uq_order_processed_event` on `(event_id, consumer_name)`, structurally identical to the Inventory ledger (15-02) but with a deliberately distinct table name (single datasource / single schema cannot host two entities on the same table). `OrderProcessedEventRepository extends JpaRepository` exposes `existsByEventIdAndConsumerName` as the fast pre-check. Lombok `@Getter/@Setter`, `GenerationType.UUID` id — matching this context's entity conventions.
- **Task 2** — Created `OrderConfirmationService` (`@Service @RequiredArgsConstructor`, injects `OrderProcessedEventRepository` + `OrderRepository`). `@Transactional onStockResult(OrderStockResultEvent)`: (1) idempotency via `existsBy...` pre-check then `saveAndFlush` of the ledger row, catching `DataIntegrityViolationException` as a concurrent replay; (2) loads the order and returns unless status is `PENDING_CONFIRMATION` (guards double-transition and legacy SUBMITTED rows); (3) sets `CONFIRMED`, or `REJECTED` + `describe(shortfalls)` rejection reason. REJECTED is terminal — no republish, cart-restore, or staff-review (D-11).
- **Task 3** — Created `OrderConfirmationServiceTest` (JUnit 5 + AssertJ + plain Mockito, no Spring context), 4 tests: confirmed -> CONFIRMED with null reason; rejected -> REJECTED with a non-blank reason naming the short ingredient; duplicate eventId no-op (`findById`/`saveAndFlush` never invoked); non-PENDING (CONFIRMED) order left unchanged.

## Task Commits

1. **Task 1: order-context processed-events idempotency ledger** — `1cb2b22` (feat)
2. **Task 2: OrderConfirmationService idempotent saga completion** — `0b2f9a8` (feat)
3. **Task 3: OrderConfirmationService unit tests** — `23eb695` (test)

## Verification

- `./mvnw -q compile` passed after Task 1 and Task 2.
- `./mvnw -q -Dtest=OrderConfirmationServiceTest test` — `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.

## Deviations from Plan

None - plan executed exactly as written.

## Threat Mitigations Applied

- **T-15-03** (replayed result re-transitions order): ledger insert of `(eventId, "order-stock-result")` + immediate `saveAndFlush` (forces the DIVE inside the guarded block despite `GenerationType.UUID` deferring INSERT to commit) + the `status == PENDING_CONFIRMATION` guard.
- **T-15-10** (spurious transition of a non-pending/legacy order): any order not in `PENDING_CONFIRMATION` (including legacy `SUBMITTED`) is ignored — proven by the non-PENDING test.
- **T-15-05** (rejectionReason exposure): accepted per plan — reason lists ingredient names/quantities only; GET /orders remains owner-scoped by existing Spring Security.

## Notes for Downstream Plans

- Plan 15-06's order-side Kafka listener should deserialize `OrderStockResultEvent` and call `OrderConfirmationService.onStockResult(event)` — all idempotency/status logic is already inside the service.
- Full-suite green at wave merge requires both `order_processed_events` and `inventory_processed_events` tables to coexist under H2 `ddl-auto=create-drop` with no DDL collision (distinct table names ensure this).

## Self-Check: PASSED

All four created files present and all three task commits (`1cb2b22`, `0b2f9a8`, `23eb695`) verified in git history. `OrderConfirmationServiceTest` 4/4 green.

---
*Phase: 15-kafka-event-consumers-for-ordercreated-and-payment-events-wi*
*Completed: 2026-07-07*
