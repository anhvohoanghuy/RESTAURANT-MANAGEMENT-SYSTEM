---
phase: "01"
phase_name: "Menu Context"
plan: "01"
title: "Implement Restaurant Menu Context vertical slice"
type: "implementation"
wave: 1
depends_on: []
files_modified:
  - "src/main/java/com/example/feat1/DDD/menu_context/**"
  - "src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java"
  - "src/test/java/com/example/feat1/DDD/menu_context/**"
autonomous: true
requirements:
  - "MENU-001"
  - "MENU-002"
  - "MENU-003"
  - "MENU-004"
  - "MENU-005"
requirements_addressed:
  - "MENU-001"
  - "MENU-002"
  - "MENU-003"
  - "MENU-004"
  - "MENU-005"
---

# Plan 01: Implement Restaurant Menu Context Vertical Slice

<objective>
Replace the prior permission-navigation menu direction with a Restaurant Menu Context that manages categories, dishes, topping groups, topping options, and recipes, then exposes a public active menu tree.
</objective>

<must_haves>
## Truths

- D-01: Place new restaurant menu code under `src/main/java/com/example/feat1/DDD/menu_context/`.
- D-02: Persist category, dish, topping group, topping option, recipe, and recipe line data through Spring Data JPA.
- D-03: Use lifecycle status values `ACTIVE`, `INACTIVE`, and `ARCHIVED`.
- D-04: Public API returns only active sellable data.
- D-05: Public API response excludes recipes.
- D-06: Recipes can target either dishes or topping options.
- D-07: Admin CRUD uses `/admin/menu/**`; public read uses `GET /menus/public`.
</must_haves>

<tasks>
## Task 1: Add Domain And Persistence

Add domain models, lifecycle enums, JPA entities, and repositories for menu categories, dishes, topping groups, topping options, recipes, and recipe lines.

## Task 2: Add Application Service

Add catalog management methods and public menu query assembly. Enforce topping group min/max validation and recipe line validation.

## Task 3: Add Controllers

Add admin catalog endpoints under `/admin/menu/**` and public read endpoint `GET /menus/public`. Permit only that public read endpoint in `SecurityConfig`.

## Task 4: Add Focused Tests

Cover active lifecycle filtering, topping group validation, recipe line validation, dish/topping recipes, and public response shape excluding recipes.
</tasks>

<verification>
## Verification Steps

1. Run focused tests: `mvn -Dtest=MenuCatalogServiceTest,PublicMenuControllerTest test`.
2. Run full test suite: `mvn test`.
3. Confirm `SecurityConfig` permits `GET /menus/public` and keeps `/admin/**` role-protected.
4. Confirm public DTOs do not contain recipe fields.
</verification>

<success_criteria>

- `MENU-001` is satisfied by domain models under `menu_context/domain/model`.
- `MENU-002` is satisfied by JPA entities and repositories for the six catalog tables.
- `MENU-003` is satisfied by `AdminMenuController`.
- `MENU-004` is satisfied by `PublicMenuController` and `MenuCatalogService.getPublicMenu`.
- `MENU-005` is satisfied by recipe target support and public DTO exclusion.
</success_criteria>
