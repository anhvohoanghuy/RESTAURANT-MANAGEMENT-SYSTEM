# Phase 01: Menu Context - Patterns

## Pattern Mapping Complete

## Package And Layering Pattern

Use the existing DDD-inspired package shape:

- Domain model: `src/main/java/com/example/feat1/DDD/menu_context/domain/model/...`
- Application service and DTOs: `src/main/java/com/example/feat1/DDD/menu_context/application/...`
- Infrastructure entity/repository/presentation: `src/main/java/com/example/feat1/DDD/menu_context/infrastructure/...`

## Persistence Pattern

Use domain repository ports behind the application service. Spring Data JPA repositories stay in infrastructure and are hidden behind adapter classes.

Domain ports:

- `MenuCategoryDomainRepository`
- `DishDomainRepository`
- `ToppingGroupDomainRepository`
- `ToppingOptionDomainRepository`
- `RecipeDomainRepository`

Infrastructure adapters:

- `MenuCategoryDomainRepositoryAdapter`
- `DishDomainRepositoryAdapter`
- `ToppingGroupDomainRepositoryAdapter`
- `ToppingOptionDomainRepositoryAdapter`
- `RecipeDomainRepositoryAdapter`

Entities should use explicit table names:

- `MenuCategoryEntity` -> `menu_categories`
- `DishEntity` -> `dishes`
- `ToppingGroupEntity` -> `topping_groups`
- `ToppingOptionEntity` -> `topping_options`
- `RecipeEntity` -> `recipes`
- `RecipeLineEntity` -> `recipe_lines`

Use `@Enumerated(EnumType.STRING)` for lifecycle and recipe target enums.

Application services must not import `menu_context.infrastructure.*`; they depend on domain models and domain repository interfaces only.

## API Pattern

Existing controllers use constructor injection, `@RestController`, `@RequestMapping`, and `ResponseEntity`.

Endpoints for this phase:

- `GET /menus/public`
- `POST /admin/menu/categories`
- `PUT /admin/menu/categories/{id}`
- `DELETE /admin/menu/categories/{id}` for archive
- `POST /admin/menu/dishes`
- `PUT /admin/menu/dishes/{id}`
- `DELETE /admin/menu/dishes/{id}` for archive
- `POST /admin/menu/topping-groups`
- `POST /admin/menu/topping-options`
- `DELETE /admin/menu/topping-options/{id}` for archive
- `PUT /admin/menu/recipes`
- `GET /admin/menu/recipes?targetType=...&targetId=...`

## Test Pattern

Use focused tests:

- Plain unit tests for domain validation.
- Mocked repository tests for application service public tree assembly.
- Standalone MockMvc tests for controller response shape.

Avoid full Spring context tests for this phase unless needed; unrelated Redis/security setup can obscure catalog failures.
