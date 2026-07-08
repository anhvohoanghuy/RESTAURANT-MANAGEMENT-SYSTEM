---
phase: 16-inventory-reservation-settlement
plan: 01
subsystem: inventory
tags: [jpa, hibernate, pessimistic-lock, event-contract, idempotency, spring-boot]

# Dependency graph
requires:
  - phase: 15-inventory-reservation
    provides: StockReservationEntity, InventoryMovementType, StockReservationRepository, inventory idempotency ledger pattern
provides:
  - SettleTriggerEvent inbound event contract (routing/identity only, no recipe data)
  - CONSUMPTION outbound InventoryMovementType
  - ReservationStatus.SETTLED lifecycle value
  - InventoryLineSettlementEntity with named (order_id, order_line_id) unique constraint
  - InventoryLineSettlementRepository (existsByOrderIdAndOrderLineId, countByOrderId)
  - StockReservationRepository.lockByOrderId PESSIMISTIC_WRITE lookup
affects: [16-02, 16-03, settlement-service, kafka-boundary, inventory-settlement]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single settlement table serves both per-line idempotency guard (D-05) and last-line counter (D-04)"
    - "PESSIMISTIC_WRITE @Lock + @Query lookup mirroring InventoryStockBalanceRepository.lockByIngredientAndLocation"
    - "Inbound event carries only identity/routing; consumer re-resolves recipe data (D-01)"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/inventory_context/application/event/SettleTriggerEvent.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryLineSettlementEntity.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/repository/InventoryLineSettlementRepository.java
  modified:
    - src/main/java/com/example/feat1/DDD/inventory_context/domain/model/InventoryMovementType.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/StockReservationEntity.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/repository/StockReservationRepository.java

key-decisions:
  - "SettleTriggerEvent carries no ingredient/recipe fields; inventory re-resolves the recipe itself (D-01, RESEARCH Pitfall 6)"
  - "One inventory_line_settlements table backs both the D-05 double-settlement guard and the D-04 last-line counter"
  - "Last-line completion is detected via countByOrderId, not by inspecting per-ingredient reservation lines (RESEARCH Anti-Pattern)"

patterns-established:
  - "Named DB unique constraint as durable per-line idempotency guard (uq_inventory_line_settlement)"
  - "Reservation-row PESSIMISTIC_WRITE lock to serialize concurrent settlements of one order"

requirements-completed: [D-01, D-04, D-05, D-06]

# Metrics
duration: ~12min
completed: 2026-07-08
---

# Phase 16 Plan 01: Settlement Data-Layer Foundation Summary

**Additive inventory-settlement data layer: SettleTriggerEvent inbound contract, CONSUMPTION/SETTLED enum values, InventoryLineSettlementEntity + repository, and a PESSIMISTIC_WRITE reservation lookup — no behavior change to Phase 15.**

## Performance

- **Duration:** ~12 min
- **Completed:** 2026-07-08
- **Tasks:** 2
- **Files modified:** 6 (3 created, 3 modified)

## Accomplishments
- SettleTriggerEvent record carrying exactly (eventId, eventType, occurredAt, orderId, orderLineId, totalLines) plus TYPE constant, with no recipe data (D-01)
- InventoryMovementType.CONSUMPTION added and classified outbound; isInbound/isCount unchanged (D-06)
- ReservationStatus extended to { HELD, SETTLED } with no other entity change (D-04)
- InventoryLineSettlementEntity with named unique (order_id, order_line_id) constraint serving both the idempotency guard and last-line counter (D-04/D-05)
- InventoryLineSettlementRepository exposing existsByOrderIdAndOrderLineId + countByOrderId
- StockReservationRepository.lockByOrderId acquiring a PESSIMISTIC_WRITE lock (D-04)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add settle-trigger event contract and enum extensions** - `b80e0fa` (feat)
2. **Task 2: Add per-line settlement entity/repository and reservation pessimistic lookup** - `4189ca7` (feat)

## Files Created/Modified
- `.../application/event/SettleTriggerEvent.java` - Inbound settle-trigger event contract (created)
- `.../infrastructure/entity/InventoryLineSettlementEntity.java` - Per-(orderId,orderLineId) idempotency + last-line counter (created)
- `.../infrastructure/repository/InventoryLineSettlementRepository.java` - existsByOrderIdAndOrderLineId + countByOrderId (created)
- `.../domain/model/InventoryMovementType.java` - Added CONSUMPTION outbound value (modified)
- `.../infrastructure/entity/StockReservationEntity.java` - ReservationStatus += SETTLED (modified)
- `.../infrastructure/repository/StockReservationRepository.java` - Added lockByOrderId PESSIMISTIC_WRITE (modified)

## Decisions Made
None beyond plan — all three key decisions (no recipe on event, single settlement table, count-based last-line detection) were specified in the plan and followed as written.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. Both target tests (EventSerdeRoundTripTest, InventoryStockIntegrationTest) and the full `./mvnw test` suite passed green. Kafka broker connection warnings in the integration-test log are pre-existing expected noise (no broker in the test environment) and do not affect context load or results.

## User Setup Required
None - no external service configuration required. No dependencies added.

## Next Phase Readiness
- Data-layer types, columns, and queries are in place to unblock the settlement service (Plan 04) and Kafka boundary (Plan 05).
- No blockers.

---
*Phase: 16-inventory-reservation-settlement*
*Completed: 2026-07-08*
