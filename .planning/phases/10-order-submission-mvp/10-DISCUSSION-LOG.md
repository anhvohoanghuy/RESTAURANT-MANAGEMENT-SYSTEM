---
phase: 10-order-submission-mvp
created: 2026-07-05
status: complete
---

# Phase 10 Discussion Log

## Topic

After Phase 09, cart already stores `tableId` and table snapshot. The next phase should submit the cart into a real order and preserve that table snapshot on the order.

## Decisions

- Create a new phase: `order-submission-mvp`.
- Scope is submitted order persistence and user-owned read APIs.
- `POST /orders` submits the authenticated user's active cart.
- Order stores `tableId`, table code/name, and area id/name.
- Order stores line item snapshots from cart.
- No menu re-quote during submission.
- Empty cart and cart without table are invalid.
- Successful submission clears the cart.
- Successful submission publishes an `OrderCreated` Kafka event for future consumers.
- Payment, fulfillment, admin order management, and table occupancy are deferred.

## Notes

- Phase 10 is intentionally not a payment or kitchen workflow phase.
- Table session/occupancy still belongs outside this phase because Table Context currently models catalog only.
- Kafka consumers are intentionally deferred; this phase defines and publishes the event only.
