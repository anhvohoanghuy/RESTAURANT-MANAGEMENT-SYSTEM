---
phase: 13-inventory-costing
status: complete
created: 2026-07-06
requirements: [INV-001, INV-002, INV-003, INV-004, INV-005, INV-006, INV-007, INV-008, INV-009, INV-010, INV-011]
---

# Phase 13 Context: Inventory Costing

## Goal

Add a backend Inventory Costing foundation so operators can maintain ingredient master data and ingredient costs, link menu recipes to ingredients, compute estimated recipe/menu item cost, and inspect menu margin without changing customer-facing prices or deducting stock.

## Phase Boundary

- Create a new Inventory Context for ingredient/cost ownership.
- Keep Menu Context as recipe owner.
- Keep Order/Payment contexts unchanged except for no new dependency.
- Add costing reads for admin/staff use only.
- Do not expose cost data through public menu APIs.
- Do not implement stock on hand, purchase orders, supplier management, or automatic stock deduction in this phase.

## Decisions

### Context Ownership
- Inventory Context owns ingredients, ingredient unit costs, and unit conversion rules.
- Menu Context owns recipes and recipe lines.
- Recipe lines should be extended to optionally reference an Inventory ingredient id while retaining the existing ingredient text/name for backward compatibility.
- Costing should depend on ports/snapshots between Inventory and Menu rather than repository access across contexts.

### Costing Model
- Ingredient has a base unit such as `g`, `ml`, or `pcs`.
- Ingredient costs are stored as cost records with cost per a specified unit, effective timestamp, and optional source/note.
- Current cost is the latest cost record effective at or before the calculation time.
- Recipe costing converts recipe line quantity/unit into ingredient base unit when possible.
- Missing ingredient links, missing current cost, or missing unit conversion should be surfaced in the cost response and, where appropriate, as stable errors for strict calculation endpoints.

### API Scope
- Admin/staff can manage inventory costing data.
- Public clients cannot read costing data.
- Suggested APIs:
  - `POST /admin/inventory/ingredients`
  - `PUT /admin/inventory/ingredients/{id}`
  - `GET /admin/inventory/ingredients`
  - `POST /admin/inventory/ingredients/{id}/costs`
  - `GET /admin/menu/recipes/cost?targetType=&targetId=`
  - `GET /admin/menu/costing`
- Existing `/admin/menu/recipes` request/response remains backward compatible.

### Deferred
- Real-time stock deduction on order submit/payment.
- Inventory movements, stock counts, waste/spoilage, purchase orders, supplier catalog, FIFO/weighted-average costing for stock valuation.
- Cost audit approval workflow.
- Multi-branch inventory locations.
- Kafka consumers for inventory updates.

## Canonical References

- `.planning/ROADMAP.md` - phase ordering and completed Menu/Order/Payment/Table context decisions.
- `.planning/REQUIREMENTS.md` - requirement traceability and out-of-scope updates.
- `src/main/java/com/example/feat1/DDD/menu_context/**` - existing recipe model, recipe endpoints, menu pricing, and DDD patterns.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java` - admin/staff endpoint authorization pattern.
- `src/test/java/com/example/feat1/DDD/menu_context/**` - existing menu and recipe test style.

## Risks

- Recipe lines currently use free-text ingredient names; introducing ingredient ids must preserve backward compatibility.
- Unit conversion can grow complex quickly; Phase 13 should support deterministic simple conversions and clear failures for unsupported units.
- Costing should not mutate sell price or order totals, otherwise it can accidentally affect existing checkout/payment behavior.
