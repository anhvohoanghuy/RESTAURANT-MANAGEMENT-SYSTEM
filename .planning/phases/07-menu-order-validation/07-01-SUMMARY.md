---
phase: 07-menu-order-validation
plan: 07-01
status: complete
completed: 2026-07-05
requirements: [MENU-006, MENU-007]
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/menu_context/application/MenuOrderValidationService.java
    - src/main/java/com/example/feat1/DDD/menu_context/application/dto/MenuSelectionDtos.java
    - src/main/java/com/example/feat1/DDD/menu_context/domain/model/MenuDomainException.java
    - src/test/java/com/example/feat1/DDD/menu_context/application/MenuOrderValidationServiceTest.java
---

# Summary: Implement Service-Only Menu Order Validation And Quote Snapshot

## Completed

- Added `MenuOrderValidationService` as an application service with no HTTP endpoint.
- Added service input DTO for `dishId + List<toppingOptionId>`.
- Added quote DTO with dish snapshot, selected topping snapshots, toppings total, and total price.
- Added `MenuDomainException` with stable menu error codes.
- Enforced strict validation:
  - dish exists and is active.
  - category exists and is active.
  - selected topping options exist and are active.
  - selected options belong to a group under the selected dish.
  - each topping group satisfies min/max selections.
- Added focused tests for valid quote calculation and each planned failure category.

## Verification

- `mvnw.cmd -Dtest=MenuOrderValidationServiceTest test`: passed.
- `mvnw.cmd test`: passed.
- Result: 65 tests, 0 failures, 0 errors.

## Deviations

- Duplicate topping option IDs are de-duplicated because Phase 07 explicitly does not support topping quantities.
- No controller or public HTTP contract was added, per phase boundary.
