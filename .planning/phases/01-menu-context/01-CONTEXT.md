# Phase 01: Menu Context - Context

**Gathered:** 2026-06-10
**Status:** Ready for execution
**Source:** User-provided redesign plan and source inspection

<domain>
## Phase Boundary

Build a Restaurant Menu Context for the Spring Boot backend. This phase is a sales catalog slice, not a permission-aware navigation menu.

In scope:
- Add menu category, dish, topping group, topping option, recipe, and recipe line models.
- Persist catalog data through Spring Data JPA.
- Use lifecycle states `ACTIVE`, `INACTIVE`, and `ARCHIVED`.
- Expose admin endpoints under `/admin/menu/**`.
- Expose public/client read endpoint `GET /menus/public`.
- Ensure public responses include only active sellable data and never include recipes.

Out of scope:
- Order, cart, payment, inventory costing, yield, waste, and stock deduction.
- Generating SKUs or variants from topping combinations.
- Frontend rendering.
</domain>

<decisions>
## Implementation Decisions

### Bounded Context Placement
- **D-01:** Add restaurant menu code under `src/main/java/com/example/feat1/DDD/menu_context/`.

### Catalog Model
- **D-02:** Model dishes as belonging to categories. Model configurable toppings as groups under a dish and options under each group.

### Lifecycle
- **D-03:** Use `ACTIVE`, `INACTIVE`, and `ARCHIVED`. Public menu queries only return active categories, active dishes, and active topping options.

### Recipe Ownership
- **D-04:** Recipes attach to either a dish or a topping option through `targetType` and `targetId`. Recipe lines contain ingredient, quantity, unit, and sort order.

### API Shape
- **D-05:** Public read uses `GET /menus/public`. Admin writes use `/admin/menu/**` so existing `/admin/**` security covers them.

### Public Data Contract
- **D-06:** Public response shape is category -> dishes -> topping groups -> topping options. It excludes recipes and internal recipe lines.
</decisions>

<canonical_refs>
## Canonical References

- `pom.xml` - Spring Boot, JPA, web, security, H2 test dependencies.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java` - route authorization rules.
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/entity/UserEntity.java` - local JPA style.
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/presentation/UserController.java` - controller style.
</canonical_refs>

<specifics>
## Specific Ideas

- Tables: `menu_categories`, `dishes`, `topping_groups`, `topping_options`, `recipes`, `recipe_lines`.
- Dish pricing is `basePrice`.
- Topping option pricing is `additionalPrice`.
- Future order pricing can calculate `basePrice + selected topping additionalPrice`, but that is not implemented in this phase.
</specifics>
