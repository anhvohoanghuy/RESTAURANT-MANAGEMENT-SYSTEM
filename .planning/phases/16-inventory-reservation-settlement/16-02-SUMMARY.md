---
phase: 16-inventory-reservation-settlement
plan: 02
subsystem: api
tags: [ddd, hexagonal, port-adapter, spring-data-jpa, cross-context, inventory, order]

# Dependency graph
requires:
  - phase: 15
    provides: MenuRecipeCostingPort/MenuRecipeCostingAdapter cross-context read convention (analog copied)
provides:
  - OrderLineLookupPort (inventory_context read port for one order line's recipe fields)
  - OrderLineRecipeSnapshot (narrow snapshot: orderLineId, dishId, quantity, toppingOptionIds)
  - OrderLineRepository (order_context JPA repo keyed by orderId + orderLineId)
  - OrderLineLookupAdapter (order_context adapter implementing the port)
affects: [inventory-reservation, settlement, recipe-re-resolution]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Cross-context read: consuming context (inventory) owns port + snapshot; owning context (order) implements adapter over its own JPA repo"
    - "Narrow snapshot mapping to avoid full-entity leak across bounded contexts"
    - "Repository query keyed by both parent id and child id (findByOrder_IdAndId) for cross-order collision defense"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/inventory_context/domain/port/OrderLineLookupPort.java
    - src/main/java/com/example/feat1/DDD/inventory_context/domain/snapshot/OrderLineRecipeSnapshot.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/repository/OrderLineRepository.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/OrderLineLookupAdapter.java
    - src/test/java/com/example/feat1/DDD/order_context/infrastructure/adapter/OrderLineLookupAdapterTest.java
  modified: []

key-decisions:
  - "Mirrored MenuRecipeCostingPort/Adapter exactly: port + snapshot in inventory_context, adapter in order_context"
  - "Snapshot exposes only recipe-relevant fields (no OrderLineEntity/OrderEntity graph) per T-16-03"
  - "Repository keys on BOTH orderId and orderLineId (findByOrder_IdAndId) per T-16-04"

patterns-established:
  - "Cross-context anti-corruption read via port defined in consumer, adapter in owner"
  - "Narrow record snapshots for cross-context data transfer"

requirements-completed: [D-02]

# Metrics
duration: 12min
completed: 2026-07-08
---

# Phase 16 Plan 02: Cross-Context OrderLine Lookup Summary

**Cross-context read path (OrderLineLookupPort + adapter) letting inventory_context re-resolve one order line's recipe fields by (orderId, orderLineId) via a narrow snapshot, mirroring the MenuRecipeCostingPort convention.**

## Performance

- **Duration:** ~12 min
- **Completed:** 2026-07-08
- **Tasks:** 2
- **Files modified:** 5 (all created)

## Accomplishments
- Defined `OrderLineLookupPort.findLine(orderId, orderLineId)` and narrow `OrderLineRecipeSnapshot` record in inventory_context (consuming side owns the port).
- Added `OrderLineRepository.findByOrder_IdAndId` — the first repository able to read a single OrderLine by id (previously reachable only via `OrderEntity.lines`).
- Implemented `OrderLineLookupAdapter` in order_context: `@Transactional(readOnly = true)`, maps the entity to the narrow snapshot, keyed by both ids.
- Verified via a Mockito unit test covering both the mapping case and the empty case; full suite green with no regression.

## Task Commits

Each task was committed atomically:

1. **Task 1: Define OrderLineLookupPort and OrderLineRecipeSnapshot** - `6bbb56d` (feat)
2. **Task 2 (RED): Failing OrderLineLookupAdapter test** - `a08d193` (test)
3. **Task 2 (GREEN): Implement OrderLineRepository + OrderLineLookupAdapter** - `2194012` (feat)

_TDD task 2 followed RED (test) → GREEN (feat). No refactor commit needed._

## Files Created/Modified
- `.../inventory_context/domain/port/OrderLineLookupPort.java` - Cross-context read port (Optional return, both ids).
- `.../inventory_context/domain/snapshot/OrderLineRecipeSnapshot.java` - Narrow record: orderLineId, dishId, quantity, selectedToppingOptionIds.
- `.../order_context/infrastructure/repository/OrderLineRepository.java` - JpaRepository with `findByOrder_IdAndId`.
- `.../order_context/infrastructure/adapter/OrderLineLookupAdapter.java` - Port implementation mapping entity → snapshot.
- `.../order_context/infrastructure/adapter/OrderLineLookupAdapterTest.java` - Mockito test (mapping + empty).

## Decisions Made
None beyond the plan — followed the MenuRecipeCostingPort/Adapter analog as specified.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## TDD Gate Compliance
Task 2 (`tdd="true"`): RED commit `a08d193` (test) preceded GREEN commit `2194012` (feat). RED confirmed by compilation failure (adapter/repository absent); GREEN confirmed by `Tests run: 2, Failures: 0`.

## Threat Surface Scan
No new network endpoints, auth paths, or trust-boundary surface introduced. Threat mitigations applied as planned:
- T-16-03 (Information Disclosure): snapshot exposes only recipe-relevant fields.
- T-16-04 (Spoofing/Tampering): lookup keyed by both orderId and orderLineId.
- T-16-SC: no dependency added.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Inventory can now re-resolve a single order line's recipe from a thin event via `OrderLineLookupPort`.
- Ready for downstream reservation/settlement plans that consume this port.

---
*Phase: 16-inventory-reservation-settlement*
*Completed: 2026-07-08*
