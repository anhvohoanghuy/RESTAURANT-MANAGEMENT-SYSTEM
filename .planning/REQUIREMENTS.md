# Requirements: feat1

**Defined:** 2026-06-10
**Core Value:** Restaurant operators can manage a sellable menu catalog, and clients can read the active menu tree.

## v1 Requirements

### Restaurant Menu Context

- [x] **MENU-001**: The backend introduces a Restaurant Menu Context with domain objects for menu categories, dishes, topping groups, topping options, recipes, and recipe lines.
- [x] **MENU-002**: The persistence layer stores menu catalog data in relational tables compatible with Spring Data JPA and MySQL: `menu_categories`, `dishes`, `topping_groups`, `topping_options`, `recipes`, and `recipe_lines`.
- [x] **MENU-003**: The application exposes admin CRUD endpoints for catalog management, including lifecycle changes for categories, dishes, and topping options.
- [x] **MENU-004**: The application exposes a public/client read endpoint that returns only active categories, active dishes, active topping options, and their topping groups as a category -> dish -> topping tree.
- [x] **MENU-005**: Recipes are managed separately for dishes and topping options and are excluded from the public menu response.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Order/cart/payment | This phase is catalog management only. |
| Inventory costing | Recipe lines store ingredient, quantity, and unit only. |
| SKU/variant generation | Topping choices are modeled as topping groups and options. |
| Public recipe exposure | Recipes are admin/internal catalog data. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| MENU-001 | Phase 1 | Complete |
| MENU-002 | Phase 1 | Complete |
| MENU-003 | Phase 1 | Complete |
| MENU-004 | Phase 1 | Complete |
| MENU-005 | Phase 1 | Complete |

**Coverage:**
- v1 requirements: 5 total
- Mapped to phases: 5
- Unmapped: 0

---
*Requirements defined: 2026-06-10*
*Last updated: 2026-06-10 for Restaurant Menu Context*
