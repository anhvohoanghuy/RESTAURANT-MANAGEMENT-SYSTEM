# Phase 01: Menu Context - Research

## Research Complete

The existing project is a Spring Boot 4 / Java 17 backend with Spring Security, JPA, MySQL, and a DDD-inspired package structure. A Menu Context should follow the current style instead of introducing a new framework.

## Recommended Approach

1. Model menus as a simple adjacency list.
   - Store `parent_id` on each menu item.
   - Query active rows, sort them, and assemble the tree in application code.
   - This keeps the first slice simple and avoids recursive SQL or closure tables.

2. Bind visibility to permission codes.
   - Existing roles already carry permissions in the identity domain model.
   - Menu filtering can compare each menu item's optional `permissionCode` against the current user's authorities or permission collection.
   - Public-to-authenticated menu items should use a null permission binding.

3. Expose a current-user menu endpoint.
   - `GET /menus/me` avoids accepting an arbitrary user id from clients.
   - The controller can use the Spring Security principal, then delegate to an application service.
   - Return DTOs, not entities or domain aggregates.

4. Keep CRUD out of the first phase.
   - Seed data or repository fixtures are enough to prove the read/filter path.
   - Admin management can be a follow-up phase once the menu model is stable.

## Codebase Considerations

- `SecurityConfig` already protects non-auth routes and registers `JwtAuthenticationFilter`.
- `CustomUserDetails` and role/permission mapping should be inspected during implementation to decide whether permission codes are best read from authorities or from the loaded domain user.
- Existing packages use `infastructure` in the identity context. Do not rename that during this phase.
- `pom.xml` already includes H2 and Spring Security test dependencies, so focused tests can be added without new dependencies.

## Risks

- Current authentication setup may still have unresolved compile or runtime issues. Menu endpoint tests should isolate where possible.
- If `Role` permissions are not fully fetched by the existing query, filtering by permission may require updating fetch joins.
- Menu tree assembly must avoid leaking children whose parent is hidden.

