# Phase 10 Summary: Order Submission MVP

## Completed

- Added submitted order persistence in Order Context with `orders`, `order_lines`, and topping snapshot persistence.
- Added authenticated order APIs:
  - `POST /orders`
  - `GET /orders`
  - `GET /orders/{orderId}`
- Submission copies the active cart's table snapshot and immutable line snapshots into the order.
- Submission rejects empty carts with `ORDER_CART_EMPTY` and carts without table context with `ORDER_CART_TABLE_REQUIRED`.
- Successful submission clears the active cart.
- Order read APIs are owner-scoped and return `ORDER_NOT_FOUND` for non-owned orders.
- Added `OrderCreated` event payload, publisher port, Kafka adapter, and producer configuration.
- Event publishing is scheduled after transaction commit so events are not emitted for failed persistence.

## Verification

- Added unit coverage for submission snapshot copy, cart clearing, empty/no-table errors, owner scoping, and event payload publishing.
- Added integration coverage for HTTP submit/read, cart clearing, stable errors, owner scoping, and mocked Kafka publisher invocation.
- Full command passed:
  - `mvnw.cmd test`
  - Result: `Tests run: 92, Failures: 0, Errors: 0, Skipped: 0`

## Notes

- No Kafka consumers were introduced in this phase.
- No payment, kitchen workflow, table occupancy, reservation, or inventory behavior was introduced.
