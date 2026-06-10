# Architecture

## Overview

`feat1` is structured as a Spring Boot backend with a DDD-inspired organization. The code separates HTTP presentation, application services, domain models, domain repositories, infrastructure repositories, JPA entities, and mappers, although the boundaries are still being stabilized.

The dominant architectural intent is:

1. Controllers receive HTTP requests.
2. Application services or use cases coordinate workflows.
3. Domain models represent business concepts such as users, roles, credentials, and permissions.
4. Domain repository interfaces hide persistence details.
5. Infrastructure repository adapters use Spring Data JPA repositories and mappers to persist domain objects.

## Package Layout

```text
src/main/java/com/example/feat1/
  Feat1Application.java
  config/
    RedisConfig.java
  DDD/
    auth/
      application/
      domain/
      infrastructure/
    identity_context/
      application/
      domain/
      infastructure/
```

Note: the code currently uses `infastructure` in several package paths. This document preserves that spelling when referring to existing code.

## Auth Context

The auth context handles login, token issuance, token refresh, logout, and request authentication.

Key classes:

- `AuthController` exposes `/auth/login`, `/auth/refresh`, and `/auth/logout`.
- `AuthService` dispatches authentication to a provider from `Map<String, IAuthProvider>`.
- `IAuthProvider` is implemented by `LocalAuthProvider` and `GoogleAuthProvider`.
- `JwtProvider` creates and validates JWT access and refresh tokens.
- `JwtProperties` binds `jwt.access-expiration`, `jwt.refresh-expiration`, and `jwt.secret`.
- `TokenSerivce` generates token pairs and contains refresh-token lookup logic.
- `JwtAuthenticationFilter` reads Bearer tokens and sets the Spring Security context.
- `CustomUserDetailsService` loads users and credentials for Spring Security.
- `SecurityConfig` defines route authorization rules and the password encoder.

Current gap: the provider implementations return `null`, refresh/logout methods in `AuthService` are unfinished, and the JWT filter is not currently wired into `SecurityConfig`.

## Identity Context

The identity context owns users, credentials, roles, permissions, and registration.

Key classes:

- `User` is the domain aggregate.
- `Credential`, `Role`, `Permission`, `UserRole`, and `rolePermision` are domain entities.
- `RegisterUserUseCase` creates a user, validates it, assigns roles, saves the user, and saves credentials.
- `UserDomainService` validates user name and email format.
- `UserDomainRepository`, `CredentialRepository`, and `roleDomainRepository` adapt domain repository interfaces to Spring Data repositories.
- `UserMapper` and `CredentialMapper` convert between domain objects and JPA entities.
- JPA entities represent `users`, `credentials`, `roles`, `permissions`, role-permission links, user-role links, and refresh tokens.

Current gap: registration is not exposed by a controller in the current codebase, and `RegisterUserUseCase` is not annotated as a Spring bean.

## Persistence

Persistence is implemented through Spring Data JPA repositories:

- `IUserRepository`
- `ICredentialRepository`
- `IRoleRepository`
- `IPermissionRepository`
- `IRolePermisionRepository`
- `IUserRoleRepository`
- `IRefreshTokenRepository`

The database is configured as MySQL. Hibernate schema update is enabled through `spring.jpa.hibernate.ddl-auto=update`.

## Redis

`RedisConfig` defines a `RedisTemplate<String, Object>` with string key serializers and JSON value serializers. No current code path in the inspected source uses this template directly, so Redis appears prepared for future use rather than central to current behavior.

## Security Model

The intended security model appears to be:

- Public auth endpoints for login and token refresh.
- Role-protected admin and user routes.
- Bearer JWT authentication through `JwtAuthenticationFilter`.
- `BCryptPasswordEncoder` for local credential passwords.
- Roles converted to Spring authorities with the `ROLE_` prefix.

Current mismatch: `SecurityConfig` permits `/api/auth/**`, while the auth controller is mounted at `/auth`. That means the intended public login routes may still require authentication unless the route rules are corrected.

## Data Flow: Login

Intended flow:

1. Client posts credentials to `/auth/login`.
2. `AuthController` calls `AuthService.login`.
3. `AuthService` selects an `IAuthProvider` by `authType`.
4. Provider validates the request and loads the user/credential data.
5. `JwtProvider` creates access and refresh tokens.
6. Response returns `AuthResponse`.

Current status: provider implementations are placeholders and return `null`.

## Data Flow: Authenticated Request

Intended flow:

1. Client sends `Authorization: Bearer <token>`.
2. `JwtAuthenticationFilter` extracts and validates the JWT.
3. The token subject is parsed as the user id.
4. `CustomUserDetailsService` loads the user and credential.
5. Spring Security receives a `UsernamePasswordAuthenticationToken`.

Current status: the filter exists, but no filter registration was found in `SecurityConfig`.

## Data Flow: User Lookup

1. Client calls `GET /users/{id}`.
2. `UserController` calls `UserService.getUserById`.
3. `UserService` calls `IUserDomainRepository.findByIdWithRoles`.
4. `UserDomainRepository` queries JPA through `IUserRepository.findByIdWithRoles`.
5. `UserMapper` converts the entity graph to a domain `User`.

## Architectural Issues To Resolve

- Align packages with file paths, especially `SecurityConfig`.
- Rename `infastructure` to `infrastructure` when the project is ready for a broad package refactor.
- Rename `TokenSerivce` to `TokenService`.
- Decide whether controllers should expose domain objects directly or DTOs only.
- Complete the auth provider contract and token lifecycle.
- Register JWT authentication in the security chain.
- Move secrets and environment-specific configuration out of committed properties.
- Add focused tests for auth, user lookup, mappers, repositories, and security route behavior.

