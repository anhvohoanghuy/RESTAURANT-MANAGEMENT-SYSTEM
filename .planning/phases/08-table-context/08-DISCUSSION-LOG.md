---
phase: 08-table-context
created: 2026-07-05
status: complete
---

# Phase 08 Discussion Log

## Topic

Analyze the next domain phase after Menu Order Validation and decide how table-related data should be prepared before Order/Cart.

## Options Considered

### Next Context
- Order Cart MVP
- Table Context
- Menu admin polish

Decision: Table Context as Phase 08; defer Order Cart MVP to Phase 09.

### Table Scope
- Table Catalog MVP
- Table sessions and occupancy
- Reservation/booking

Decision: Table Catalog MVP.

### Table Model
- `DiningArea + DiningTable`
- Table only
- Restaurant/Branch/Area/Table

Decision: `DiningArea + DiningTable`.

### Lifecycle
- Catalog status only
- Operational statuses
- Hybrid catalog plus operational state

Decision: Catalog status only: `ACTIVE`, `INACTIVE`, `ARCHIVED`.

### Public/Admin API
- Admin CRUD plus public active listing
- Admin only
- Public only

Decision: Admin CRUD/archive plus `GET /tables/public`.

### Table Identity
- Stable unique `code`
- UUID only
- Human name only

Decision: Stable unique `code`; UUID remains internal.

### Capacity
- Optional but positive if present
- Required
- No capacity field

Decision: Optional but positive if present.

### Order Integration
- Service-only validator returns table snapshot
- Direct Order Context DB lookup
- Public validation endpoint

Decision: Service-only validator returns table snapshot for future Order Context.

### Seed Policy
- Minimal dev seed
- No seed
- Production fixture seed

Decision: Minimal dev/test seed only.

## Notes

- Order belongs in Order Context to keep DDD boundaries clear.
- Phase 09 should define its own ports for menu quotes and table validation instead of owning Menu/Table catalog behavior.
- Table sessions, reservations, and QR generation are intentionally deferred because the next useful step is order/cart attachment, not live dining-room operations.
