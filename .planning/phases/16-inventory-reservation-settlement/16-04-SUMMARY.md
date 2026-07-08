---
phase: 16-inventory-reservation-settlement
plan: 04
subsystem: inventory
tags: [event-driven, pessimistic-lock, idempotency, requires-new, saga, spring-boot, tdd]

# Dependency graph
requires:
  - plan: 16-01
    provides: SettleTriggerEvent, InventoryMovementType.CONSUMPTION, ReservationStatus.SETTLED, InventoryLineSettlementEntity/Repository, StockReservationRepository.lockByOrderId
  - plan: 16-02
    provides: OrderLineLookupPort + OrderLineRecipeSnapshot cross-context read
  - plan: 16-03
    provides: RecipeRequirementResolver (accumulate/resolveForTarget) shared recipe resolution
provides:
  - InventoryLedgerWriter (REQUIRES_NEW-isolated idempotency-ledger insert, WR-01)
  - InventoryReservationSettlementService.onSettleTrigger (single-line settlement business logic, D-02..D-06)
  - InventoryDomainException settlement factory methods (settlementReservationMissing, settlementOrderLineMissing)
affects: [16-05, settlement-kafka-boundary, inventory-settlement]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "REQUIRES_NEW ledger-insert in a distinct bean so a concurrent-duplicate rollback stays isolated from the business transaction (WR-01)"
    - "Inverted reserve saga: subtract reserved + on_hand with a non-negative clamp instead of add, never throwing for underflow (D-03)"
    - "CONSUMPTION InventoryStockMovementEntity built directly (not via recordMovement) to bypass its throw/independent-lock behavior (WR-02)"
    - "Reservation-row-first, ascending-ingredientId total lock order to keep all settlement transactions deadlock-free"
    - "Count-then-flip under the reservation lock: SETTLED only when countByOrderId == totalLines (D-04)"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryLedgerWriter.java
    - src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationSettlementService.java
    - src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryLedgerWriterTest.java
    - src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationSettlementServiceTest.java
  modified:
    - src/main/java/com/example/feat1/DDD/inventory_context/domain/model/InventoryDomainException.java

key-decisions:
  - "Added settlementReservationMissing/settlementOrderLineMissing factories to InventoryDomainException (plan-authorized) rather than throwing a plain exception, matching the existing static-factory convention; left out of any non-retryable set so a settle-before-reserve race retries then DLTs (D-05)"
  - "Missing balance row for a required ingredient is logged and skipped (never thrown) — settlement remains non-throwing end to end, consistent with the D-03 clamp philosophy"
  - "Reused RecipeRequirementResolver.resolveForTarget for the DISH then accumulate for each TOPPING_OPTION so settlement resolves identical requirements to the reserve path (D-02, no logic duplication)"

requirements-completed: [D-02, D-03, D-04, D-05, D-06]

# Metrics
duration: ~18min
completed: 2026-07-08
---

# Phase 16 Plan 04: Settlement Business Logic Summary

**The core settlement path: a WR-01-safe REQUIRES_NEW idempotency-ledger writer plus InventoryReservationSettlementService that re-resolves one order line's recipe, locks reservation-then-balances, subtracts reserved+on_hand with a non-negative clamp, writes a CONSUMPTION audit movement directly, guards idempotency two ways, and flips the reservation to SETTLED on the last line.**

## Performance

- **Duration:** ~18 min
- **Completed:** 2026-07-08
- **Tasks:** 2 (Task 2 split RED/GREEN)
- **Files modified:** 5 (4 created, 1 modified)

## Accomplishments
- `InventoryLedgerWriter` (`@Component`): `tryInsert(eventId, consumerName)` annotated `@Transactional(propagation = REQUIRES_NEW)` returns true on save and false (no throw) on `DataIntegrityViolationException`, isolating the ledger insert so a concurrent duplicate never marks the settlement transaction rollback-only (WR-01 / D-06).
- `InventoryReservationSettlementService.onSettleTrigger` implementing the full sequence:
  1. two independent idempotency guards — eventId ledger AND per-(orderId,orderLineId) row (D-05, both non-redundant);
  2. isolated REQUIRES_NEW ledger insert via `InventoryLedgerWriter` (WR-01);
  3. single-line recipe re-resolution via the shared `RecipeRequirementResolver` (D-02);
  4. reservation row locked FIRST, then ingredient balances in ascending-ingredientId order (deadlock-free);
  5. subtract on_hand AND reserved with a per-ingredient non-negative clamp that logs an anomaly and never throws (D-03);
  6. one CONSUMPTION `InventoryStockMovementEntity` written directly per ingredient with `referenceType="ORDER_LINE"`, `referenceId=orderLineId` (WR-02 / D-06);
  7. per-line `InventoryLineSettlementEntity` insert;
  8. reservation flipped to SETTLED only when `countByOrderId >= totalLines` (D-04); benign early-return on an already-SETTLED reservation (Open Q1); a missing reservation/order-line throws to route retry then DLT (D-05).
- `InventoryDomainException` gained `settlementReservationMissing` and `settlementOrderLineMissing` factories following the existing convention.
- 12 new unit tests (10 settlement + 2 ledger) all green; full suite 152 tests green with no regression.

## Task Commits

Each task was committed atomically:

1. **Task 1: InventoryLedgerWriter — REQUIRES_NEW idempotency insert (WR-01)** - `bbe59f0` (feat, test+impl)
2. **Task 2 (RED): failing settlement-service behavior tests** - `9168b07` (test)
3. **Task 2 (GREEN): implement InventoryReservationSettlementService** - `4844be5` (feat)

## Files Created/Modified
- `.../application/InventoryLedgerWriter.java` - REQUIRES_NEW-isolated idempotency ledger writer (created).
- `.../application/InventoryReservationSettlementService.java` - Settlement business logic (created).
- `.../application/InventoryLedgerWriterTest.java` - Mockito tests: true on save, false on duplicate without throwing (created).
- `.../application/InventoryReservationSettlementServiceTest.java` - Mockito tests for all behavior-block cases (created).
- `.../domain/model/InventoryDomainException.java` - Added two settlement exception factories (modified).

## Decisions Made
- Settlement exception factories added to `InventoryDomainException` (plan-authorized "distinct type if preferred"), classified `HttpStatus.CONFLICT`, intentionally NOT registered as non-retryable so transient ordering races self-heal via retry→DLT (D-05).
- A missing balance row for a required ingredient is logged and skipped rather than thrown, keeping the settlement path non-throwing consistent with the D-03 clamp intent.

## Deviations from Plan

### Auto-added defensive functionality

**1. [Rule 2 - Missing critical functionality] Non-throwing skip on a missing balance row**
- **Found during:** Task 2
- **Issue:** The plan's subtract-clamp loop assumed a locked balance is always present; a missing balance (`lockByIngredientAndLocation` empty) would NPE, violating the D-03 "settlement never throws" invariant.
- **Fix:** Guard the empty case — `log.warn` and `continue` (skip that ingredient), never throw.
- **Files modified:** `InventoryReservationSettlementService.java`
- **Commit:** `4844be5`

## TDD Gate Compliance
- Task 1: written test-first (RED via compile failure) then implemented (GREEN), committed atomically as a single `feat` commit `bbe59f0` (test + impl together).
- Task 2: RED commit `9168b07` (`test`) preceded GREEN commit `4844be5` (`feat`). RED confirmed by compilation failure (service absent); GREEN confirmed by `Tests run: 12, Failures: 0`.

## Threat Surface Scan
No new network endpoints, auth paths, or trust-boundary surface beyond the plan's threat register. Mitigations applied as planned:
- T-16-06 (replay/double-deduction): two idempotency guards.
- T-16-07 (rollback-only poison): WR-01 REQUIRES_NEW ledger insert.
- T-16-08 (negative-stock invariant): per-ingredient clamp, never throws.
- T-16-09 (deadlock): reservation-first, ascending-ingredientId lock order.
- T-16-10 (missing audit): CONSUMPTION movement per ingredient.
- T-16-11 (poison/stuck): missing reservation throws → retry → DLT (not added to non-retryable set).

## Issues Encountered
None. All target tests and the full `./mvnw test` suite (152 tests) passed green.

## User Setup Required
None - no external service configuration required. No dependencies added.

## Next Phase Readiness
- Settlement business logic is complete and unit-proven; Plan 05 can wire the Kafka `SettleTriggerListener` as a thin delegate to `onSettleTrigger` and configure retry/DLT.
- No blockers.

## Self-Check: PASSED

---
*Phase: 16-inventory-reservation-settlement*
*Completed: 2026-07-08*
