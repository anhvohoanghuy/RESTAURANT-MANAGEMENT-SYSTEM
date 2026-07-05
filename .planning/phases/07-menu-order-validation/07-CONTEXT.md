---
phase: 07-menu-order-validation
created: 2026-07-05
status: ready_for_planning
requirements: [MENU-006, MENU-007]
---

# Phase 07 Context: Menu Order Validation

<domain>
## Phase Boundary

Add service-only validation and quote logic inside Menu Context so a future Order/Cart Context can ask whether a dish selection is orderable and what price snapshot should be stored.

In scope:
- Validate a dish selection from `dishId + List<toppingOptionId>`.
- Return a quote/snapshot for valid selections.
- Enforce active/orderable category, dish, and topping option rules.
- Enforce topping group `minSelections` and `maxSelections`.
- Throw stable menu domain exceptions for invalid selections.

Out of scope:
- No HTTP endpoint in this phase.
- No Order/Cart aggregate.
- No payment, checkout, inventory deduction, or order persistence.
- No topping quantity support.
- No UI-specific multi-error validation result.
</domain>

<decisions>
## Locked Decisions

### API Boundary
- **D-01:** Implement application/domain service only. Do not expose a new public HTTP API in Phase 07.

### Input Contract
- **D-02:** The service receives `dishId + List<toppingOptionId>`. Callers do not provide topping group IDs.

### Output Contract
- **D-03:** Valid selections return a price quote/snapshot containing dish identity/name/base price, selected topping identity/name/group/additional price, and total price.
- **D-04:** The quote is intended to become the source data for future order item price snapshots so historical orders do not drift when menu prices change.

### Validation Policy
- **D-05:** Validate strictly by the selected dish's topping groups.
- **D-06:** Category and dish must be active/orderable.
- **D-07:** Every selected topping option must exist, be active/orderable, and belong to a topping group under the selected dish.
- **D-08:** Every topping group under the dish must satisfy `minSelections <= selected count <= maxSelections`.

### Error Policy
- **D-09:** Invalid selections throw stable menu domain exceptions instead of returning `{ valid: false }`.
- **D-10:** Stable error codes should include:
  - `MENU_DISH_NOT_ORDERABLE`
  - `MENU_TOPPING_NOT_ORDERABLE`
  - `MENU_TOPPING_NOT_IN_DISH`
  - `MENU_TOPPING_GROUP_REQUIRED`
  - `MENU_TOPPING_GROUP_LIMIT_EXCEEDED`
</decisions>

<code_context>
## Existing Menu Context

- `src/main/java/com/example/feat1/DDD/menu_context/application/MenuCatalogService.java` already assembles active public menu trees and validates catalog entity existence.
- `src/main/java/com/example/feat1/DDD/menu_context/domain/model/ToppingGroup.java` already validates `0 <= minSelections <= maxSelections`.
- Existing repository ports/adapters separate application logic from JPA repositories.
- Current public menu DTOs already expose dish base price and topping additional price.
</code_context>

<canonical_refs>
## Canonical References

- `.planning/phases/01-menu-context/01-CONTEXT.md` - original Menu Context boundary and decisions.
- `.planning/phases/01-menu-context/01-SUMMARY.md` - implemented Menu Context files and current shape.
- `src/main/java/com/example/feat1/DDD/menu_context/application/MenuCatalogService.java` - current application service pattern.
- `src/main/java/com/example/feat1/DDD/menu_context/domain/model/ToppingGroup.java` - existing selection range rule.
- `src/main/java/com/example/feat1/DDD/menu_context/application/dto/MenuDtos.java` - current menu price DTO shape.
</canonical_refs>

<deferred>
## Deferred Ideas

- HTTP quote/validation endpoint for frontend/mobile.
- Topping option quantities such as extra cheese x2.
- Full Order/Cart Context.
- Multi-error validation responses for UI form highlighting.
</deferred>
