---
phase: "01"
phase_name: "Menu Context"
plan: "01"
title: "Implement Menu Context vertical slice"
type: "implementation"
wave: 1
depends_on: []
files_modified:
  - "src/main/java/com/example/feat1/DDD/menu_context/**"
  - "src/main/java/com/example/feat1/DDD/auth/infrastructure/security/CustomUserDetails.java"
  - "src/main/java/com/example/feat1/DDD/auth/infrastructure/service/CustomUserDetailsService.java"
  - "src/test/java/com/example/feat1/DDD/menu_context/**"
  - "ARCHITECTURE.md"
  - "TASKS.md"
  - "DECISIONS.md"
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

# Plan 01: Implement Menu Context Vertical Slice

<objective>
Add a new Menu Context that stores hierarchical menu items, filters them by the authenticated user's permission codes, and exposes `GET /menus/me` returning the user's allowed menu tree.
</objective>

<must_haves>
## Truths

- D-01: Place new menu code under `src/main/java/com/example/feat1/DDD/menu_context/`.
- D-02: Use Spring Data JPA persistence for menu items.
- D-03: Permission-bound menu items are visible only to users with the required permission code; unbound items are visible to any authenticated user.
- D-04: Expose `GET /menus/me`, protect it with existing Spring Security, and return DTOs.
- D-05: Return a deterministic tree ordered by `sortOrder` then `label`; do not leak descendants of hidden parents.
- D-06: Add focused tests for tree assembly, permission filtering, and endpoint authorization.
</must_haves>

<threat_model>
## Security Threat Model

### Assets
- Authenticated user's permission set.
- Menu entries and permission bindings.
- Hidden admin or privileged navigation paths.

### Threats And Mitigations
- Unauthorized menu disclosure: filter every permission-bound item by permission code before tree assembly returns DTOs.
- Child leakage under hidden parents: build the returned tree only from visible roots and visible descendants whose parent is also visible.
- Client-side user spoofing: endpoint must be `GET /menus/me` and must not accept a user id request parameter or path variable.
- Role/permission confusion: preserve `ROLE_` authorities for route security and expose permission codes separately for menu filtering.
</threat_model>

<tasks>
## Task 1: Add Menu Domain Model And Repository Contract

type: implementation
files:
- `src/main/java/com/example/feat1/DDD/menu_context/domain/model/MenuItem.java`
- `src/main/java/com/example/feat1/DDD/menu_context/domain/repository/IMenuItemDomainRepository.java`

<read_first>
- `ARCHITECTURE.md`
- `.planning/phases/01-menu-context/01-CONTEXT.md`
- `.planning/phases/01-menu-context/01-PATTERNS.md`
- `src/main/java/com/example/feat1/DDD/identity_context/domain/model/entity/Role.java`
- `src/main/java/com/example/feat1/DDD/identity_context/domain/repository/user/IUserDomainRepository.java`
</read_first>

<action>
- Create package `com.example.feat1.DDD.menu_context.domain.model`.
- Add `MenuItem` with concrete fields: `UUID id`, `UUID parentId`, `String label`, `String path`, `String icon`, `String permissionCode`, `Integer sortOrder`, `boolean active`, and `List<MenuItem> children`.
- Add a constructor or factory that normalizes null `children` to an empty list.
- Create package `com.example.feat1.DDD.menu_context.domain.repository`.
- Add `IMenuItemDomainRepository` with method `List<MenuItem> findAllActiveOrderBySortOrderAndLabel()`.
</action>

<verify>
- Source inspection confirms `MenuItem` has no JPA annotations.
- Source inspection confirms repository contract returns domain `MenuItem`, not JPA entity.
</verify>

<acceptance_criteria>
- `MenuItem.java` contains `class MenuItem`.
- `MenuItem.java` contains fields or accessors for `parentId`, `permissionCode`, `sortOrder`, `active`, and `children`.
- `IMenuItemDomainRepository.java` contains `findAllActiveOrderBySortOrderAndLabel`.
- `MenuItem.java` imports no `jakarta.persistence.*` classes.
</acceptance_criteria>

## Task 2: Add JPA Persistence And Mapper

type: implementation
files:
- `src/main/java/com/example/feat1/DDD/menu_context/infrastructure/entity/MenuItemEntity.java`
- `src/main/java/com/example/feat1/DDD/menu_context/infrastructure/repository/JpaMenuItemRepository.java`
- `src/main/java/com/example/feat1/DDD/menu_context/infrastructure/repository/MenuItemDomainRepository.java`
- `src/main/java/com/example/feat1/DDD/menu_context/infrastructure/mapper/MenuItemMapper.java`

<read_first>
- `.planning/phases/01-menu-context/01-PATTERNS.md`
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/entity/UserEntity.java`
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/repository/IUserRepository.java`
- `src/main/java/com/example/feat1/DDD/identity_context/domain/repository/user/UserDomainRepository.java`
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/mapper/UserMapper.java`
</read_first>

<action>
- Create `MenuItemEntity` in package `com.example.feat1.DDD.menu_context.infrastructure.entity`.
- Map it to table `menu_items`.
- Use columns: `id`, `parent_id`, `label`, `path`, `icon`, `permission_code`, `sort_order`, and `active`.
- Create Spring Data repository `JpaMenuItemRepository extends JpaRepository<MenuItemEntity, UUID>`.
- Add method `List<MenuItemEntity> findByActiveTrueOrderBySortOrderAscLabelAsc()`.
- Create `MenuItemMapper` with `toDomain(MenuItemEntity entity)` and `toEntity(MenuItem item)`.
- Create `MenuItemDomainRepository` annotated with `@Repository` that implements `IMenuItemDomainRepository`.
</action>

<verify>
- Repository adapter loads only active menu rows in deterministic order.
- Mapper keeps `parentId` and `permissionCode` unchanged.
</verify>

<acceptance_criteria>
- `MenuItemEntity.java` contains `@Table(name = "menu_items")`.
- `MenuItemEntity.java` contains `@Column(name = "permission_code")`.
- `JpaMenuItemRepository.java` contains `findByActiveTrueOrderBySortOrderAscLabelAsc`.
- `MenuItemDomainRepository.java` implements `IMenuItemDomainRepository`.
- No controller or application service returns `MenuItemEntity`.
</acceptance_criteria>

## Task 3: Add Permission-Aware Menu Query Service

type: implementation
files:
- `src/main/java/com/example/feat1/DDD/menu_context/application/MenuQueryService.java`
- `src/main/java/com/example/feat1/DDD/menu_context/application/dto/MenuItemResponse.java`

<read_first>
- `.planning/phases/01-menu-context/01-CONTEXT.md`
- `.planning/phases/01-menu-context/01-RESEARCH.md`
- `.planning/phases/01-menu-context/01-PATTERNS.md`
- `src/main/java/com/example/feat1/DDD/menu_context/domain/model/MenuItem.java`
- `src/main/java/com/example/feat1/DDD/menu_context/domain/repository/IMenuItemDomainRepository.java`
</read_first>

<action>
- Create `MenuItemResponse` DTO with fields: `UUID id`, `String label`, `String path`, `String icon`, and `List<MenuItemResponse> children`.
- Create `MenuQueryService` annotated with `@Service`.
- Add public method `List<MenuItemResponse> getMenuForPermissions(Set<String> permissionCodes)`.
- Load active items from `IMenuItemDomainRepository.findAllActiveOrderBySortOrderAndLabel()`.
- Treat null `permissionCodes` as `Collections.emptySet()`.
- A menu item is visible when `permissionCode` is null, blank, or present in `permissionCodes`.
- Build the response tree from visible root items where `parentId == null`.
- Attach only visible children whose parent is present in the visible tree.
- Sort siblings by `sortOrder` ascending, then `label` ascending.
</action>

<verify>
- Unit tests cover empty permission set, unbound items, matching permission, missing permission, inactive rows excluded by repository fixture/mock, and hidden parent with visible child.
</verify>

<acceptance_criteria>
- `MenuQueryService.java` contains `getMenuForPermissions(Set<String> permissionCodes)`.
- `MenuItemResponse.java` contains `List<MenuItemResponse> children`.
- A test named `hidesChildWhenParentIsHidden` or equivalent exists.
- A test named `showsUnboundItemsForAuthenticatedUser` or equivalent exists.
- A test named `sortsSiblingsBySortOrderThenLabel` or equivalent exists.
</acceptance_criteria>

## Task 4: Expose Permission Codes On Current User Principal

type: implementation
files:
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/CustomUserDetails.java`
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/service/CustomUserDetailsService.java`
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/repository/IUserRepository.java`

<read_first>
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/CustomUserDetails.java`
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/service/CustomUserDetailsService.java`
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java`
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/JwtAuthenticationFilter.java`
- `src/main/java/com/example/feat1/DDD/identity_context/domain/model/entity/Role.java`
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/repository/IUserRepository.java`
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/mapper/UserMapper.java`
</read_first>

<action>
- Add `Set<String> permissionCodes` to `CustomUserDetails`.
- Preserve existing `roles` and `getAuthorities()` behavior so route checks using `ROLE_` continue to work.
- In `CustomUserDetailsService.loadUserById(UUID id)`, load user data with roles and permissions, not just basic user fields.
- Prefer `userDomainRepository.findByIdWithRoles(id)` over `findById(id)` if that method fetches role permissions.
- If `IUserRepository.findByIdWithRoles` does not fetch `rolePermissions.permission`, update its JPQL to join fetch permissions through role permissions.
- Build `permissionCodes` from `user.getRoles().stream().flatMap(role -> role.getPermissionsCode().stream())`.
</action>

<verify>
- Existing role-based authorization still returns `ROLE_USER` and `ROLE_ADMIN` authorities for user roles.
- `CustomUserDetails` exposes permission codes separately from granted authorities.
</verify>

<acceptance_criteria>
- `CustomUserDetails.java` contains `Set<String> permissionCodes`.
- `CustomUserDetailsService.java` calls `findByIdWithRoles(id)` or an equivalent method that fetches roles and permissions.
- `CustomUserDetailsService.java` maps role permission codes into `permissionCodes`.
- `CustomUserDetails.getAuthorities()` still prefixes role names with `ROLE_`.
</acceptance_criteria>

## Task 5: Add Protected Current-User Menu Endpoint

type: implementation
files:
- `src/main/java/com/example/feat1/DDD/menu_context/infrastructure/presentation/MenuController.java`
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java`

<read_first>
- `.planning/phases/01-menu-context/01-CONTEXT.md`
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java`
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/CustomUserDetails.java`
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/presentation/AuthController.java`
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/presentation/UserController.java`
- `src/main/java/com/example/feat1/DDD/menu_context/application/MenuQueryService.java`
- `src/main/java/com/example/feat1/DDD/menu_context/application/dto/MenuItemResponse.java`
</read_first>

<action>
- Create `MenuController` in package `com.example.feat1.DDD.menu_context.infrastructure.presentation`.
- Add `@RestController` and `@RequestMapping("/menus")`.
- Add `@GetMapping("/me")`.
- Use `@AuthenticationPrincipal CustomUserDetails principal`.
- Call `menuQueryService.getMenuForPermissions(principal.getPermissionCodes())`.
- Return `ResponseEntity<List<MenuItemResponse>>`.
- Do not add `/menus/**` to `permitAll`; it must remain authenticated under `.anyRequest().authenticated()`.
</action>

<verify>
- Anonymous requests to `GET /menus/me` are denied by Spring Security.
- Authenticated requests do not accept any user id from the client.
</verify>

<acceptance_criteria>
- `MenuController.java` contains `@RequestMapping("/menus")`.
- `MenuController.java` contains `@GetMapping("/me")`.
- `MenuController.java` contains `@AuthenticationPrincipal CustomUserDetails`.
- `SecurityConfig.java` does not contain `.requestMatchers("/menus/**").permitAll()`.
</acceptance_criteria>

## Task 6: Add Focused Tests

type: test
files:
- `src/test/java/com/example/feat1/DDD/menu_context/application/MenuQueryServiceTest.java`
- `src/test/java/com/example/feat1/DDD/menu_context/infrastructure/presentation/MenuControllerTest.java`

<read_first>
- `pom.xml`
- `src/test/resources/application.properties`
- `src/test/java/com/example/feat1/Feat1ApplicationTests.java`
- `src/main/java/com/example/feat1/DDD/menu_context/application/MenuQueryService.java`
- `src/main/java/com/example/feat1/DDD/menu_context/infrastructure/presentation/MenuController.java`
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/CustomUserDetails.java`
</read_first>

<action>
- Add `MenuQueryServiceTest` as a unit test using a fake or mocked `IMenuItemDomainRepository`.
- Cover at least these cases: unbound item visible, matching permission visible, missing permission hidden, child hidden when parent hidden, sibling ordering by `sortOrder` then `label`.
- Add `MenuControllerTest` using Spring MVC test support.
- Verify anonymous `GET /menus/me` is denied.
- Verify authenticated principal with permission code `MENU_VIEW_ADMIN` receives only menu entries allowed for that permission set.
</action>

<verify>
- Run `.\mvnw.cmd -Dtest=MenuQueryServiceTest,MenuControllerTest test`.
- Run `.\mvnw.cmd test` if the focused command passes and Java is configured.
</verify>

<acceptance_criteria>
- `MenuQueryServiceTest.java` contains tests for visible, hidden, child-leak prevention, and sorting cases.
- `MenuControllerTest.java` contains a denied anonymous request assertion for `GET /menus/me`.
- `MenuControllerTest.java` contains an authenticated request assertion for `GET /menus/me`.
- Focused test command exits with code 0 in a Java-configured shell.
</acceptance_criteria>

## Task 7: Update Project Documentation

type: docs
files:
- `ARCHITECTURE.md`
- `TASKS.md`
- `DECISIONS.md`

<read_first>
- `ARCHITECTURE.md`
- `TASKS.md`
- `DECISIONS.md`
- `.planning/phases/01-menu-context/01-CONTEXT.md`
</read_first>

<action>
- Add a Menu Context section to `ARCHITECTURE.md` that mentions `GET /menus/me`, `MenuQueryService`, and permission-code filtering.
- Add completed or active task notes to `TASKS.md` for Menu Context read/filter API.
- Add an accepted decision to `DECISIONS.md`: menu visibility is permission-code based, unbound menu items are visible to authenticated users, and admin menu CRUD is deferred.
</action>

<verify>
- Documentation references only implemented endpoint and classes.
- Documentation keeps admin CRUD and Redis caching as deferred, not shipped.
</verify>

<acceptance_criteria>
- `ARCHITECTURE.md` contains `Menu Context`.
- `TASKS.md` contains `GET /menus/me`.
- `DECISIONS.md` contains `permission-code based`.
- Docs do not claim admin menu CRUD exists.
</acceptance_criteria>
</tasks>

<verification>
## Verification Steps

1. Run `.\mvnw.cmd -Dtest=MenuQueryServiceTest,MenuControllerTest test`.
2. Run `.\mvnw.cmd test`.
3. Confirm `GET /menus/me` is not listed under `permitAll` in `SecurityConfig`.
4. Confirm `CustomUserDetails.getAuthorities()` still emits `ROLE_` authorities for roles.
5. Confirm every `MENU-001` through `MENU-005` requirement appears in this plan frontmatter and success criteria.
</verification>

<success_criteria>

- `MENU-001` is satisfied by `MenuItem` and `IMenuItemDomainRepository`.
- `MENU-002` is satisfied by `MenuItemEntity`, `JpaMenuItemRepository`, mapper, and repository adapter.
- `MENU-003` is satisfied by `GET /menus/me`.
- `MENU-004` is satisfied by `CustomUserDetails.permissionCodes` and `MenuQueryService.getMenuForPermissions`.
- `MENU-005` is satisfied by `MenuQueryServiceTest` and `MenuControllerTest`.
- The implementation does not require frontend work, admin CRUD, Redis caching, or a broad package rename.
</success_criteria>

