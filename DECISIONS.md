# Decisions

## Accepted Decisions

### Use Spring Boot And Maven

The project is a Java 17 Spring Boot application managed by Maven. The Maven Wrapper is committed, so local and CI workflows should use `mvnw` or `mvnw.cmd`.

Evidence:

- `pom.xml`
- `mvnw`
- `mvnw.cmd`
- `Feat1Application.java`

### Use A DDD-Inspired Package Structure

The code is organized around domain contexts under `DDD`, with separate auth and identity areas. Domain models, domain repository interfaces, infrastructure repositories, entities, mappers, controllers, and application services are separated by package.

Evidence:

- `src/main/java/com/example/feat1/DDD/auth`
- `src/main/java/com/example/feat1/DDD/identity_context`

### Use JPA/MySQL For Primary Persistence

The project uses Spring Data JPA repositories, JPA entities, and MySQL datasource settings.

Evidence:

- `spring-boot-starter-data-jpa`
- `mysql-connector-j`
- `application.properties`
- repository interfaces extending `JpaRepository`

### Use JWTs For API Authentication

JWT generation and validation are implemented through JJWT. Tokens include the user id as subject, role claims, and access-token permissions.

Evidence:

- `JwtProvider`
- `JwtProperties`
- `TokenType`
- `JwtAuthenticationFilter`

### Support Multiple Authentication Providers

Authentication dispatch is provider-based. `AuthService` selects an `IAuthProvider` from a Spring-injected map keyed by auth type.

Evidence:

- `AuthService`
- `IAuthProvider`
- `LocalAuthProvider`
- `GoogleAuthProvider`
- `AuthType`

## Pending Decisions

### Public API Prefix

Current state is inconsistent: `AuthController` is mounted at `/auth`, while `SecurityConfig` permits `/api/auth/**`.

Decision needed: use `/auth/**` or `/api/auth/**` as the canonical public auth prefix.

### DTOs Versus Domain Objects At API Boundary

`UserController` currently returns `Optional<User>`, exposing the domain aggregate directly.

Decision needed: either keep domain-returning controllers for internal simplicity or introduce response DTOs for API stability.

### Refresh Token Storage Strategy

Refresh token persistence exists through `RefreshTokenEntity` and `IRefreshTokenRepository`, but token creation, persistence, expiry validation, and logout invalidation are not complete.

Decision needed: define whether refresh tokens are single-active-token per user, multi-session, rotated on refresh, or reused until expiry.

### Redis Responsibility

Redis is configured, but no inspected code path uses `RedisTemplate`.

Decision needed: keep Redis for future caching/session/token use, or remove it until there is a concrete runtime responsibility.

### Registration Scope

`RegisterUserUseCase` exists, but no controller exposes it and it is not currently annotated as a Spring bean.

Decision needed: include user registration in the first working API slice or defer it behind admin/user provisioning.

### Package Naming Refactor Timing

The code contains package and class spelling issues such as `infastructure`, `TokenSerivce`, `IRolePermisionRepository`, and `rolePermision`.

Decision needed: fix naming before building more features, or defer until the current auth flow is working.

## Recommended Near-Term Decisions

1. Use `/auth/**` as the immediate route prefix because that matches the current controller mappings.
2. Wire JWT authentication into `SecurityConfig` before adding more protected endpoints.
3. Implement local authentication first and treat Google OAuth as a later provider.
4. Move secrets to environment variables before any deployment or shared environment.
5. Return DTOs from controllers before the public API grows.

