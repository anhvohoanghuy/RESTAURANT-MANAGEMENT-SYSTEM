---
phase: 15-kafka-event-consumers
plan: 02
subsystem: inventory_context
tags: [inventory, persistence, jpa, reservation, idempotency, saga]
requires:
  - Phase 14 Inventory stock balances (InventoryStockBalanceEntity)
provides:
  - InventoryStockBalanceEntity.reservedQuantity column
  - InventoryStockBalanceRepository.lockByIngredientAndLocation (PESSIMISTIC_WRITE)
  - StockReservationEntity (unique orderId + per-ingredient reserved lines) + repository
  - InventoryProcessedEventEntity (inventory_processed_events ledger) + repository
affects:
  - Plan 15-03 reservation service (consumes these persistence primitives)
  - Phase 16 reservation settlement (reserved -> on_hand)
tech-stack:
  added: []
  patterns:
    - PESSIMISTIC_WRITE @Lock @Query row locking for concurrency-safe reservation
    - Unique-constraint idempotency ledger (event_id, consumer_name)
    - Unique orderId per-order reservation guard
    - "@ElementCollection @Embeddable per-ingredient reservation lines"
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/StockReservationEntity.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/repository/StockReservationRepository.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryProcessedEventEntity.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/repository/InventoryProcessedEventRepository.java
  modified:
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryStockBalanceEntity.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/repository/InventoryStockBalanceRepository.java
decisions:
  - "Per-ingredient reserved lines modeled as @ElementCollection of @Embeddable ReservationLine (stock_reservation_lines) rather than a separate @OneToMany entity — lines have no identity of their own and are always loaded/settled with the parent reservation."
  - "ReservationStatus enum defined as a nested type on StockReservationEntity with only HELD this phase; Phase 16 extends lifecycle."
metrics:
  duration: ~10m
  completed: 2026-07-07
---

# Phase 15 Plan 02: Inventory Reservation Persistence Layer Summary

Built the Inventory-side reservation persistence primitives for the order-confirmation saga: a running `reservedQuantity` column plus a `PESSIMISTIC_WRITE` lock query on the stock balance (non-negative-available invariant, D-02), a per-order `StockReservationEntity` with a unique `orderId` and per-ingredient reserved lines for Phase-16 settlement (D-03/D-09), and an `inventory_processed_events` idempotency ledger unique on `(event_id, consumer_name)` (D-03).

## What Was Built

- **Task 1** — Added a non-nullable `reserved_quantity` column (precision 18, scale 6, default ZERO) to `InventoryStockBalanceEntity`, mirroring the existing `quantityOnHand` declaration; `available = quantityOnHand - reservedQuantity` is computed by the service (15-03), not stored. Added `lockByIngredientAndLocation(UUID, String)` to `InventoryStockBalanceRepository` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` and a JPQL `@Query`, serializing concurrent reservers on the balance row.
- **Task 2** — Created `StockReservationEntity` (`stock_reservations`, unique `uq_stock_reservation_order` on `order_id`) with a `HELD`-defaulting `ReservationStatus` enum, `createdAt`, and an `@ElementCollection` of `@Embeddable ReservationLine` (ingredientId + reserved base quantity, into `stock_reservation_lines`). Provides a static `held(UUID orderId, Map<UUID,BigDecimal> reservedByIngredient)` factory that builds header + lines. `StockReservationRepository` exposes `existsByOrderId` and `findByOrderId`.
- **Task 3** — Created `InventoryProcessedEventEntity` (`inventory_processed_events`, unique `uq_inventory_processed_event` on `event_id, consumer_name`) with `eventId`, `consumerName`, `processedAt`. Physically distinct table name from the Order context's `order_processed_events` (one datasource / one schema). `InventoryProcessedEventRepository` exposes `existsByEventIdAndConsumerName` for the fast pre-check ahead of the insert+flush idempotency guard.

## Verification

- `./mvnw -q compile` passed after each task.
- `./mvnw -q test` full suite passed (exit 0): H2 `ddl-auto=create-drop` rebuilt the schema with the new column and three new tables (`stock_reservations`, `stock_reservation_lines`, `inventory_processed_events`) with no DDL errors.

## Deviations from Plan

None - plan executed exactly as written.

## Threat Mitigations Applied

- **T-15-02** (negative available via concurrency): `PESSIMISTIC_WRITE` lock query in place — reservers serialize on the balance row.
- **T-15-03** (replayed OrderCreated double-reserves): unique `order_id` on `stock_reservations` plus unique `(event_id, consumer_name)` on `inventory_processed_events`.

## Notes for Downstream Plans

- Plan 15-03 must compute `available = quantityOnHand - reservedQuantity` in the service (not persisted) and use `lockByIngredientAndLocation` inside the reservation transaction.
- The idempotency insert should flush and catch `DataIntegrityViolationException` as a duplicate; `existsByEventIdAndConsumerName` is only a fast pre-check, not the sole guard.
- `StockReservationEntity.held(...)` is the intended construction path for the reservation service.

## Self-Check: PASSED
