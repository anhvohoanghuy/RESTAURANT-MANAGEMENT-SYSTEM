# Phase 01: Menu Context - Patterns

## Pattern Mapping Complete

This file captures local code patterns the executor must follow while implementing the Menu Context.

## Package And Layering Pattern

Use the existing DDD-inspired package shape:

- Domain model: `src/main/java/com/example/feat1/DDD/{context}/domain/model/...`
- Domain repository interface: `src/main/java/com/example/feat1/DDD/{context}/domain/repository/...`
- Application service/use case: `src/main/java/com/example/feat1/DDD/{context}/application/...`
- Infrastructure entity/repository/mapper/presentation: `src/main/java/com/example/feat1/DDD/{context}/infrastructure/...`

Existing analogs:

- `src/main/java/com/example/feat1/DDD/identity_context/domain/model/entity/Role.java`
- `src/main/java/com/example/feat1/DDD/identity_context/domain/repository/user/IUserDomainRepository.java`
- `src/main/java/com/example/feat1/DDD/identity_context/domain/repository/user/UserDomainRepository.java`
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/repository/IUserRepository.java`

## Persistence Pattern

Use Spring Data JPA repositories and a domain-facing adapter. The adapter should map JPA entities into domain objects before the application service receives them.

Existing analog:

- `UserDomainRepository` delegates to `IUserRepository` and maps with `UserMapper`.
- `CredentialRepository` delegates to `ICredentialRepository` and maps with `CredentialMapper`.

Recommended menu equivalents:

- `IMenuItemDomainRepository`
- `MenuItemDomainRepository`
- `IMenuItemRepository` or `JpaMenuItemRepository`
- `MenuItemMapper`

## Security Principal Pattern

Current `CustomUserDetails` contains:

- `UUID id`
- `String email`
- `String password`
- `Set<String> roles`

Current `getAuthorities()` returns only `ROLE_` authorities. Menu filtering needs permission codes, so implementation must either:

- extend `CustomUserDetails` with `Set<String> permissionCodes`, or
- query the current user with roles and permissions inside the menu query path.

The cleaner path for this phase is extending `CustomUserDetails` with permission codes while preserving the existing `ROLE_` authorities.

Existing analogs:

- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/CustomUserDetails.java`
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/service/CustomUserDetailsService.java`
- `src/main/java/com/example/feat1/DDD/identity_context/domain/model/entity/Role.java`

## Controller Pattern

Existing controllers use constructor injection and Spring MVC annotations:

- `AuthController` uses `@RestController`, `@RequestMapping`, `@PostMapping`, and `ResponseEntity`.
- `UserController` exposes user lookup through a presentation package.

Recommended endpoint:

- `GET /menus/me`
- Use `@AuthenticationPrincipal CustomUserDetails principal`.
- Return `ResponseEntity<List<MenuItemResponse>>`.

## Test Pattern

The project already has:

- H2 test dependency.
- Spring Security test dependency.
- `src/test/resources/application.properties`.

Add focused tests before relying on full application integration because auth flows are still being stabilized.

