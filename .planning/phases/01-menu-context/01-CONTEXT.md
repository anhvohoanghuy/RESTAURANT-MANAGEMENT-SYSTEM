# Phase 01: Menu Context - Context

**Gathered:** 2026-06-10
**Status:** Ready for planning
**Source:** GSD fallback context from `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, `DECISIONS.md`, `TASKS.md`, and source inspection

<domain>
## Phase Boundary

Build a new Menu Context for the Spring Boot backend. The feature provides a permission-aware navigation menu API for authenticated users.

In scope:
- Add menu domain model and JPA persistence.
- Represent parent/child menu hierarchy.
- Bind menu entries to existing permission codes where needed.
- Return the current user's allowed menu tree through a protected endpoint.
- Add tests around filtering, tree assembly, and endpoint access.

Out of scope:
- Frontend rendering.
- Admin CRUD for managing menu records.
- A broad package rename from `infastructure` to `infrastructure`.
- Refresh token, logout, OAuth, and registration work unless directly needed by tests.
</domain>

<decisions>
## Implementation Decisions

### Bounded Context Placement
- **D-01:** Add menu code under `src/main/java/com/example/feat1/DDD/menu_context/` and mirror the existing DDD-inspired layering: domain, application, infrastructure, presentation.

### Persistence Strategy
- **D-02:** Use Spring Data JPA and MySQL-compatible entities, matching the existing persistence approach.

### Permission Binding
- **D-03:** A menu item may reference a permission code. If the permission code is null or blank, the item is visible to any authenticated user. If the permission code is present, it is visible only when the authenticated user's roles expose that permission code.

### API Shape
- **D-04:** Expose a read endpoint for the current user's menu, preferably `GET /menus/me`, protected by the existing JWT security filter. Return DTOs from this endpoint, not domain aggregates.

### Hierarchy Rules
- **D-05:** Return a stable tree ordered by a numeric sort order and then by label for deterministic responses. If a parent is not visible, its children must not leak through the returned tree.

### Testing Scope
- **D-06:** Include unit tests for tree assembly and permission filtering. Include a controller/security test proving anonymous calls are rejected and authenticated users receive filtered menus.

### the agent's Discretion
- Exact DTO class names.
- Whether the use case is named `GetCurrentUserMenuUseCase`, `MenuQueryService`, or similar.
- Seed data mechanism for local development.
</decisions>

<canonical_refs>
## Canonical References

Downstream agents MUST read these before planning or implementing.

### Project Context
- `PROJECT_CONTEXT.md` - current stack, implemented features, and known gaps.
- `ARCHITECTURE.md` - package layout and DDD-style layering.
- `DECISIONS.md` - accepted and pending architecture decisions.
- `TASKS.md` - existing stabilization backlog.

### Source Areas
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java` - current authorization rules and JWT filter wiring.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/JwtAuthenticationFilter.java` - current authenticated principal setup.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/CustomUserDetails.java` - available current-user details and authorities.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/service/CustomUserDetailsService.java` - user loading for security.
- `src/main/java/com/example/feat1/DDD/identity_context/domain/model/entity/Permission.java` - existing permission domain model.
- `src/main/java/com/example/feat1/DDD/identity_context/domain/model/entity/Role.java` - existing role and permission relationship.
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/repository/IUserRepository.java` - current role-fetching query pattern.
</canonical_refs>

<specifics>
## Specific Ideas

- Use `MenuItem` as the domain object and `MenuItemEntity` as the JPA entity.
- Useful fields: `id`, `parentId`, `label`, `path`, `icon`, `permissionCode`, `sortOrder`, `active`.
- The endpoint should not expose inactive menu items.
- The response should be recursive: each menu DTO may include `children`.
- Tests can use H2 and Spring Security test support already declared in `pom.xml`.
</specifics>

<deferred>
## Deferred Ideas

- Admin endpoints to create, update, reorder, and disable menu entries.
- Role-specific menu overrides beyond permission filtering.
- Caching menu trees in Redis.
- UI-specific localization of menu labels.
</deferred>

---

*Phase: 01-menu-context*
*Context gathered: 2026-06-10 via GSD fallback plan-phase*
