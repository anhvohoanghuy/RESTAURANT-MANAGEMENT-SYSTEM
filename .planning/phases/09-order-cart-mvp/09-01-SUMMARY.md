---
phase: 09-order-cart-mvp
plan: 09-01
subsystem: order_context
tags: [backend, ddd, cart, tests]
status: complete
completed: 2026-07-05
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/order_context/application/CartService.java
    - src/main/java/com/example/feat1/DDD/order_context/application/dto/CartDtos.java
    - src/main/java/com/example/feat1/DDD/order_context/domain/port/MenuQuotePort.java
    - src/main/java/com/example/feat1/DDD/order_context/domain/port/TableValidationPort.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/MenuQuoteAdapter.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/TableValidationAdapter.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/presentation/CartController.java
    - src/test/java/com/example/feat1/DDD/order_context/application/CartServiceTest.java
    - src/test/java/com/example/feat1/DDD/order_context/integration/OrderCartIntegrationTest.java
  modified:
    - src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java
metrics:
  tests: 85
  failures: 0
  errors: 0
---

# Plan 09-01 Summary: Authenticated Order Context Cart MVP

## What Changed

- Added Order Context cart domain/application/infrastructure packages.
- Added cart persistence with one cart per user and line snapshots stored in JPA tables.
- Added `MenuQuotePort` and `TableValidationPort` with adapters to Menu and Table Context services.
- Added authenticated `/cart` APIs:
  - `GET /cart`
  - `POST /cart/items`
  - `PATCH /cart/items/{lineId}`
  - `DELETE /cart/items/{lineId}`
  - `DELETE /cart`
- Added line merge by `dishId + sorted toppingOptionIds`.
- Added stored dish/topping/table snapshots and total calculation from stored line data.
- Added security rule for `/cart/**`.
- Added focused unit and integration tests.

## Commits

| Commit | Description |
|--------|-------------|
| Uncommitted | Implemented in the current workspace without staging/committing because the worktree already contains prior phase changes. |

## Deviations

- No checkout, payment, order submission, admin cart management, or table occupancy/session behavior was introduced.
- `GET /cart` creates an empty active cart for the authenticated user so the client can always render cart state.

## Verification

- Ran targeted Order Cart integration test:
  - `.\mvnw.cmd -Dtest=OrderCartIntegrationTest test`
  - Result: 4 tests passed.
- Ran full Maven test suite:
  - `$env:JAVA_HOME='C:\Users\chinh\.jdks\ms-17.0.19'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd test`
  - Result: `Tests run: 85, Failures: 0, Errors: 0, Skipped: 0`

## Self-Check: PASSED

- Order Context owns cart code and persistence.
- Menu/Table validation is consumed through Order Context ports.
- One active cart per user is enforced.
- Cart APIs are authenticated and owner-scoped.
- Line merge uses dish id plus sorted topping option ids.
- Cart reads use stored snapshots and totals.
- No checkout/payment/order submission was added.
