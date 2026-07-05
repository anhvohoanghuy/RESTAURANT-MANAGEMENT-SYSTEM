---
phase: 10-order-submission-mvp
created: 2026-07-05
status: ready_for_execution
requirements: [ORDER-008, ORDER-009, ORDER-010, ORDER-011, ORDER-012, ORDER-013, ORDER-014, ORDER-015]
---

# Phase 10 Context: Order Submission MVP

<domain>
## Phase Boundary

Add submitted order persistence inside Order Context. Phase 09 already lets authenticated users build an active cart with table, dish, topping, quantity, and total snapshots. Phase 10 turns that cart into a submitted order and preserves the same snapshots for historical display.

In scope:
- Submitted order aggregate/persistence in Order Context.
- `POST /orders` submit active cart.
- Read own submitted orders.
- Persist table snapshot on order: `tableId`, `tableCode`, `tableName`, `areaId`, `areaName`.
- Persist line snapshots on order: dish, toppings, unit price, quantity, line total.
- Validate cart is non-empty and has a table before submission.
- Clear active cart after successful submission.
- Publish an `OrderCreated` Kafka event after order persistence succeeds.

Out of scope:
- Payment.
- Kitchen/display fulfillment workflow.
- Kafka consumers/downstream services.
- Table occupancy/session changes.
- Admin order management.
- Order cancellation/refund.
- Re-quote menu data during order creation.
</domain>

<decisions>
## Locked Decisions

- **D-01:** Submitted order belongs in Order Context.
- **D-02:** Order submission copies stored cart snapshots; it does not re-quote Menu Context.
- **D-03:** Order submission copies table snapshot from cart; the order must explicitly persist `tableId`.
- **D-04:** A cart without table cannot be submitted.
- **D-05:** An empty cart cannot be submitted.
- **D-06:** Successful submission clears the active cart.
- **D-07:** User-facing order read APIs are owner-scoped.
- **D-08:** Publish `OrderCreated` to Kafka only after the order is successfully persisted.
- **D-09:** Kafka event payload must be stable and include order id, user id, table snapshot, line snapshots, total, and submitted timestamp.
- **D-10:** Payment, fulfillment, admin order management, Kafka consumers, and table occupancy are deferred.
</decisions>

<canonical_refs>
## Canonical References

- `.planning/phases/09-order-cart-mvp/09-CONTEXT.md` - cart ownership, stored snapshots, and table snapshot contract.
- `.planning/phases/09-order-cart-mvp/09-01-SUMMARY.md` - implemented cart files and behavior.
- `src/main/java/com/example/feat1/DDD/order_context/application/CartService.java` - source cart behavior and response mapping.
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/entity/OrderCartEntity.java` - cart table snapshot fields to copy.
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/entity/OrderCartLineEntity.java` - line snapshot fields to copy.
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/presentation/CartController.java` - authenticated principal pattern for Order Context controllers.
</canonical_refs>

<api_contract>
## API Contract

- `POST /orders`
  - Authenticated.
  - Uses the caller's active cart.
  - Response returns submitted order snapshot.
- `GET /orders`
  - Authenticated.
  - Lists caller's submitted orders.
- `GET /orders/{orderId}`
  - Authenticated.
  - Returns caller-owned order or stable not-found/forbidden behavior.
</api_contract>

<event_contract>
## Kafka Event Contract

- Topic property: `order.events.order-created-topic`, default `orders.created`.
- Event type: `OrderCreated`.
- Publish timing: after submitted order persistence succeeds.
- Payload fields:
  - `eventId`
  - `eventType`
  - `occurredAt`
  - `orderId`
  - `userId`
  - `table`
  - `lines`
  - `total`
</event_contract>
