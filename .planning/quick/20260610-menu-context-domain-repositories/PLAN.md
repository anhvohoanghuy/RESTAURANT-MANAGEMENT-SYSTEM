---
slug: menu-context-domain-repositories
status: complete
created: 2026-06-10
---

# Quick Task: Menu Context Domain Repositories

## Objective

Refactor Restaurant Menu Context so repository contracts live in the domain layer and application services depend on those contracts, not Spring Data JPA repositories.

## Tasks

- Add domain repository interfaces for category, dish, topping group, topping option, and recipe.
- Add infrastructure adapters implementing those interfaces using existing JPA repositories.
- Add an entity-domain mapper in infrastructure.
- Refactor `MenuCatalogService` and tests to use domain repository ports.
- Verify application layer no longer imports `menu_context.infrastructure.*`.
- Run full Maven test suite.
