---
phase: 13-inventory-costing
plan: 13-01
status: complete
completed: 2026-07-06
requirements:
  [INV-001, INV-002, INV-003, INV-004, INV-005, INV-006, INV-007, INV-008, INV-009, INV-010, INV-011]
---

# Summary 13-01: Inventory Costing Foundation

## Delivered

- Added a new Inventory Context for ingredient master data and ingredient cost records.
- Added admin/staff inventory APIs for creating, updating, archiving, listing, and costing ingredients.
- Extended Menu recipe lines with optional `ingredientId` while preserving existing free-text recipe lines.
- Added a cross-context recipe-costing port so Inventory calculates costs from Menu recipe snapshots.
- Added deterministic unit conversion for supported simple units and explicit uncosted reasons for missing/unsupported data.
- Added admin/staff costing reads for recipe cost and active menu margin summaries.
- Preserved public menu, order, payment, and stock behavior.

## Implementation Notes

- Ingredient costs use the latest effective cost record at calculation time.
- Missing ingredient links, inactive ingredients, missing costs, and unsupported conversion are surfaced in costing responses instead of guessed.
- Public menu responses remain free of recipe, cost, ingredient-cost, and margin fields.
- Costing does not deduct stock or mutate sell prices/order totals.

## Verification

- Full Maven suite passed on 2026-07-06 with Java 17:
  - `mvnw.cmd test`
- Focused coverage added for:
  - inventory costing service calculations and validation.
  - inventory costing integration endpoints.
  - recipe line backward compatibility.
