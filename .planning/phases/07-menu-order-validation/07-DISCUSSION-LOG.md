---
phase: 07-menu-order-validation
created: 2026-07-05
status: complete
---

# Phase 07 Discussion Log

## Topic

Analyze the next Menu Context phase after Phase 01 catalog foundation.

## Options Considered

### Next Menu Direction
- Order Validation
- Admin Ops
- Public Menu API

Decision: Order Validation.

### Exposure Boundary
- Menu quote/validation HTTP API
- Application service only
- Order draft API

Decision: Application service only.

### Valid Selection Output
- Validation + price quote
- Validation only
- Rich order item draft

Decision: Validation + price quote/snapshot.

### Invalid Selection Handling
- Throw stable domain exceptions
- Return validation result object
- Hybrid

Decision: Throw stable domain exceptions.

### Topping Validation Strictness
- Strict by dish groups
- Loose optional only
- Strict with duplicate quantities

Decision: Strict by dish groups.

### Service Input Shape
- `dishId + List<toppingOptionId>`
- Grouped selection
- Option IDs with quantity

Decision: `dishId + List<toppingOptionId>`.

## Notes

- Phase 07 intentionally avoids a public HTTP API so future Order/Cart Context can consume the service directly.
- Snapshot quote output is still required so future order items can persist historical price/name data.
- Topping quantities are deferred because the current Menu Context model supports option selection but not per-option quantity.
