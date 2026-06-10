# Phase 01: Menu Context - Research

## Research Complete

The existing project is a Spring Boot 4 / Java 17 backend with Spring Security, Spring Data JPA, MySQL runtime configuration, and H2 test configuration. The Restaurant Menu Context can be implemented with the existing stack and DDD-inspired package layout.

## Recommended Approach

1. Model the catalog explicitly.
   - Categories contain dishes.
   - Dishes contain topping groups.
   - Topping groups contain topping options.
   - Recipes attach separately to either a dish or a topping option.

2. Keep lifecycle filtering simple.
   - Store lifecycle status as an enum string.
   - Query public data by `ACTIVE`.
   - Keep admin archive operations as status changes rather than hard deletes.

3. Keep public API recipe-free.
   - Public menu DTOs should include prices and topping choices.
   - Recipe lines remain admin/internal data because they expose operational kitchen details.

4. Use existing route security.
   - `/admin/**` already requires `ROLE_ADMIN`.
   - Add a narrow public permit rule for `GET /menus/public`.

## Codebase Considerations

- Existing packages contain a misspelled `infastructure` in identity context; new menu context can use the conventional `infrastructure` package without renaming existing code.
- The Maven wrapper script has a local PowerShell issue, but Maven can be run directly from the cached wrapper distribution when `JAVA_HOME` points to `C:\Users\chinh\.jdks\ms-17.0.19`.
- Tests should avoid pulling Redis/security context unless they specifically need it.

## Risks

- Full application context tests may be affected by unrelated auth/Redis configuration.
- Spring Data derived queries over relationships should use explicit nested property names such as `Category_Id` and `ToppingGroup_Id`.
