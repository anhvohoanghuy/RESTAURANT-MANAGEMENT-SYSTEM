---
phase: 09-order-cart-mvp
created: 2026-07-05
status: ready_for_execution
requirements: [ORDER-001, ORDER-002, ORDER-003, ORDER-004, ORDER-005, ORDER-006, ORDER-007]
---

# Phase 09 Context: Order Cart MVP

<domain>
## Phase Boundary

Add authenticated cart management inside Order Context. The cart stores display/price snapshots returned by Menu and Table Context validation services so reads do not drift when menu/table data changes.

In scope:
- Order Context cart aggregate/persistence.
- One active cart per authenticated user.
- Owner-only cart access.
- Cart APIs: `GET /cart`, `POST /cart/items`, `PATCH /cart/items/{lineId}`, `DELETE /cart/items/{lineId}`, `DELETE /cart`.
- Add item by `tableId`, `dishId`, `toppingOptionIds`, and positive `quantity`.
- Merge line item by `dishId + sorted toppingOptionIds`.
- Store dish, topping, table snapshots and totals.
- Use Order Context ports for Menu quote and Table validation.

Out of scope:
- No checkout/order submission.
- No payment.
- No table occupancy/session state.
- No admin cart management.
- No re-quote on cart read.
</domain>

<decisions>
## Locked Decisions

- **D-01:** Order/Cart belongs in Order Context, not Menu Context.
- **D-02:** Order Context defines `MenuQuotePort`; adapter calls `MenuOrderValidationService`.
- **D-03:** Order Context defines `TableValidationPort`; adapter calls `TableCatalogService.validateOrderableTable`.
- **D-04:** Cart is authenticated-user scoped.
- **D-05:** There is one active cart per user.
- **D-06:** Add item merges by `dishId + sorted toppingOptionIds`.
- **D-07:** Quantity must be positive; remove line endpoint is separate.
- **D-08:** Cart response returns full stored display snapshot.
- **D-09:** Cart total comes from stored snapshots and quantity, not re-quoting on read.
- **D-10:** Only owner authenticated user can access cart; no admin cart access in this phase.
</decisions>

<canonical_refs>
## Canonical References

- `.planning/phases/07-menu-order-validation/07-CONTEXT.md` - Menu validation and quote snapshot contract.
- `.planning/phases/08-table-context/08-CONTEXT.md` - Table validation snapshot contract.
- `src/main/java/com/example/feat1/DDD/menu_context/application/MenuOrderValidationService.java` - menu quote service to call from adapter.
- `src/main/java/com/example/feat1/DDD/table_context/application/TableCatalogService.java` - table validator to call from adapter.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/CustomUserDetails.java` - authenticated principal shape.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java` - route protection conventions.
</canonical_refs>
