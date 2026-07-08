---
phase: 16-inventory-reservation-settlement
plan: 03
subsystem: inventory
tags: [ddd, domain-service, recipe-resolution, refactor, spring, java]

# Dependency graph
requires:
  - phase: 15
    provides: InventoryReservationService with inline accumulateRecipe/computeRequired recipe-resolution path
provides:
  - Shared RecipeRequirementResolver domain service (accumulate + resolveForTarget) as the single source of truth for recipe→ingredient base-quantity resolution
  - InventoryReservationService refactored to delegate recipe resolution to the shared resolver (behavior-preserving)
affects: [16-04-settlement, inventory-settlement]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Extract-method to a shared @Service domain collaborator to prevent logic drift between two call sites (reservation vs settlement)"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/inventory_context/domain/service/RecipeRequirementResolver.java
  modified:
    - src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java
    - src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationServiceTest.java

key-decisions:
  - "Annotated RecipeRequirementResolver as @Service + @RequiredArgsConstructor to match the existing domain-service convention (UserDomainService) so it is constructor-autowired"
  - "Kept the verbatim accumulateRecipe body — no change to conversion/scaling/logging semantics — to guarantee behavior preservation"
  - "Wired a REAL resolver (not a mock) in InventoryReservationServiceTest using the existing menu/ingredient mocks, so recipe resolution is still exercised end-to-end through the extracted collaborator"

patterns-established:
  - "Shared recipe-resolution collaborator: accumulate(required, targetType, targetId, qty) is the reusable unit; resolveForTarget(...) is the single-target convenience for per-line callers"

requirements-completed: [D-02]

# Metrics
duration: 6min
completed: 2026-07-08
---

# Phase 16 Plan 03: Shared RecipeRequirementResolver Summary

**Extracted recipe→ingredient base-quantity resolution from InventoryReservationService into a shared RecipeRequirementResolver domain service so reservation (Phase 15) and settlement (Plan 04) resolve identical requirements from one code path (D-02).**

## Performance

- **Duration:** ~6 min
- **Tasks:** 2
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments
- New `RecipeRequirementResolver` (`@Service`) owning the per-target recipe resolution logic verbatim: `menuRecipeCostingPort.findRecipe` → `ingredientRepository.findById` → `UnitConverter.convert` (base unit) → `required.merge(..., BigDecimal::add)`, with all original null-guards and debug logging preserved.
- Added `resolveForTarget(targetType, targetId, qty)` convenience returning a `LinkedHashMap` for single-target settlement callers (Plan 04), delegating to `accumulate` so logic stays in one place.
- Refactored `InventoryReservationService.computeRequired` to delegate to `recipeRequirementResolver.accumulate`; deleted the inline `accumulateRecipe` and dropped the now-unused `MenuRecipeCostingPort` field plus dead imports.
- Phase 15 reservation behavior proven unchanged: `InventoryReservationServiceTest` (6 tests) stays green; full suite (138 tests) green.

## Task Commits

Each task was committed atomically:

1. **Task 1: Extract RecipeRequirementResolver as a shared domain service** - `3ebb0dc` (feat)
2. **Task 2: Refactor InventoryReservationService to delegate to the resolver** - `b599de4` (refactor)

## Files Created/Modified
- `src/main/java/com/example/feat1/DDD/inventory_context/domain/service/RecipeRequirementResolver.java` - New shared resolver: `accumulate(...)` (verbatim extraction) + `resolveForTarget(...)` convenience.
- `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java` - Injects the resolver; `computeRequired` delegates to it; inline `accumulateRecipe` and `MenuRecipeCostingPort` field removed.
- `src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationServiceTest.java` - Constructs a real resolver from existing mocks and passes it into the service constructor (position formerly held by the port).

## Decisions Made
- `@Service` + `@RequiredArgsConstructor` chosen for the resolver to match the existing `UserDomainService` domain-service convention and keep Spring constructor autowiring working.
- `menuRecipeCostingPort` field removed from `InventoryReservationService` because it became unused after the move; `ingredientRepository` retained (still used by `ingredientName`).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plan 04 (single-line settlement) can now resolve a line's recipe via `recipeRequirementResolver.accumulate(...)` / `resolveForTarget(...)` — the same code the reservation path uses, satisfying D-02's "reuse the exact Phase 15 recipe-resolution path".

---
*Phase: 16-inventory-reservation-settlement*
*Completed: 2026-07-08*
