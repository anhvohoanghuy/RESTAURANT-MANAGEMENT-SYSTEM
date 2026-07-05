---
phase: 09-order-cart-mvp
created: 2026-07-05
status: complete
---

# Phase 09 Discussion Log

## Topic

Define the Order Cart MVP after Menu selection validation and Table Context are ready.

## Decisions

- Order belongs in Order Context for DDD ownership.
- Cart is scoped to authenticated users.
- There is one active cart per user.
- Order Context consumes Menu Context via `MenuQuotePort`.
- Order Context consumes Table Context via `TableValidationPort`.
- Add item merges by `dishId + sorted toppingOptionIds`.
- Quantity is a positive integer.
- Remove line is a separate endpoint.
- Cart response uses stored snapshots.
- Cart reads do not re-quote menu data.
- No checkout/payment/admin cart management in this phase.

## API

- `GET /cart`
- `POST /cart/items`
- `PATCH /cart/items/{lineId}`
- `DELETE /cart/items/{lineId}`
- `DELETE /cart`
