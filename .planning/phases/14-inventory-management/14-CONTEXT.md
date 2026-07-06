---
phase: 14-inventory-management
status: planned
created: 2026-07-06
requirements: [INV-012, INV-013, INV-014, INV-015, INV-016, INV-017, INV-018, INV-019, INV-020, INV-021]
---

# Phase 14 Context: Inventory Management

## Goal

Extend the Inventory Context from costing into operational stock management so staff/admin can track stock-on-hand, record stock movements, handle receipts/adjustments/waste, and identify low-stock ingredients.

## Phase Boundary

- Build on Phase 13 ingredients and unit conversion.
- Add stock balances and immutable movement history.
- Use a single default stock location in this phase.
- Keep public menu, order submission, payment, and recipe costing contracts backward compatible.
- Do not implement purchase orders, supplier workflows, or automatic order deduction yet.

## Decisions

### Context Ownership

- Inventory Context owns stock balances, movement history, low-stock thresholds, and movement validation.
- Menu Context remains recipe owner.
- Order Context remains order owner.
- Payment Context remains payment owner.

### Stock Model

- Each active ingredient can have a stock-on-hand balance in the default location.
- Movement records are immutable facts; corrections are represented as additional movements.
- Movement types should include:
  - `RECEIPT`
  - `ADJUSTMENT_IN`
  - `ADJUSTMENT_OUT`
  - `WASTE`
  - `STOCK_COUNT`
- Outbound movements should not make stock negative except through an explicit correction/stock-count path.

### API Scope

- Staff/admin can record and inspect stock operations.
- Suggested APIs:
  - `GET /admin/inventory/stock`
  - `GET /admin/inventory/ingredients/{ingredientId}/stock`
  - `POST /admin/inventory/movements`
  - `GET /admin/inventory/movements`
  - `GET /admin/inventory/low-stock`
- Public clients cannot read stock or movement data.

### Deferred

- Purchase orders and supplier catalog.
- Multi-location or multi-branch stock.
- Automatic recipe-based stock deduction from submitted/paid/fulfilled orders.
- Kafka consumers for inventory events.
- FIFO/weighted-average valuation.

## Canonical References

- `.planning/phases/13-inventory-costing/13-CONTEXT.md` - ingredient/costing boundaries.
- `src/main/java/com/example/feat1/DDD/inventory_context/**` - current Inventory Context structure.
- `src/main/java/com/example/feat1/DDD/menu_context/**` - recipe ownership and costing snapshots.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java` - staff/admin route authorization pattern.

## Risks

- Stock movement and costing can become coupled; keep stock operations separate from sell price and recipe cost reads.
- Unit conversion must remain deterministic and explicit.
- Automatic order deduction needs a separate discussion because deduction timing affects operations: order submit, payment complete, or kitchen fulfillment.
