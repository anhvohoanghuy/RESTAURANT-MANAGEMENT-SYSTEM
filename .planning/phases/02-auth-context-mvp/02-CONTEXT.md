# Phase 02: auth-context-mvp - Context

**Gathered:** 2026-07-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Turn the existing auth and identity scaffolding into a tested local authentication MVP for the Spring Boot backend. This phase covers public local registration, username/password login, JWT access tokens, refresh-token persistence and rotation, logout revocation, self/profile access, and role-based protection for existing backend routes.

In scope:
- Public registration creates user, credential, and default role data atomically.
- Local username/password login returns an access token and refresh token.
- JWT access tokens carry identity, roles, permissions, issued-at, expiration, token id, and token type claims.
- Refresh tokens are stored in a database table as the source of truth and cached in Redis for validation.
- Refresh requests rotate refresh tokens and revoke the previous token.
- Logout revokes the active refresh token.
- Protected routes enforce `ADMIN` and `USER` access through Spring Security.
- Focused tests cover registration, login, refresh, logout, JWT filtering, and protected route access.

Out of scope:
- Google/OAuth login.
- Password reset and email verification.
- Full role/permission admin UI.
- Order, cart, payment, inventory costing, SKU/variant generation, or recipe exposure changes.

</domain>

<decisions>
## Implementation Decisions

### Refresh-Token Policy
- **D-01:** Support multi-session refresh tokens. A single user can have multiple active refresh tokens, one per device/browser/session.
- **D-02:** Configure Redis through Docker for local/dev usage and use Redis as a soft cache for refresh-token validation.
- **D-03:** Keep the database refresh-token table as the source of truth. If Redis misses a refresh token, validate against the database; if the database token is still valid, cache it back into Redis.
- **D-04:** Treat Redis outages as degraded cache behavior, not as the authority. Refresh/logout should still make the validity decision from the database and log/cache-degraded behavior for operational visibility.
- **D-05:** Rotate refresh tokens on every successful `/auth/refresh`. A successful refresh returns a new access token and a new refresh token, and the previous refresh token is revoked or deleted from both Redis and the database token state.
- **D-06:** Logout revokes the submitted refresh token by removing it from Redis and revoking or deleting it in the database source of truth.
- **D-07:** Reuse of a rotated or revoked refresh token is treated as a token-theft signal. When detected, revoke all active refresh tokens for that user across Redis and the database, forcing re-login on all sessions.

### the agent's Discretion
- API contract, registration/default-role details, error response shape, and cleanup boundary were not discussed in this pass. Downstream planning should use the roadmap, requirements, existing code, and conservative Spring Boot API conventions unless the user runs another discuss pass for those areas.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Planning Scope
- `.planning/ROADMAP.md` - Phase 02 goal, dependencies, and success criteria.
- `.planning/REQUIREMENTS.md` - AUTH-001 through AUTH-010 and out-of-scope boundaries.
- `.planning/STATE.md` - Current milestone and phase status.
- `.planning/phases/01-menu-context/01-CONTEXT.md` - Prior route/security assumptions, especially `/admin/**` and public menu route context.

### Project Architecture And Existing Decisions
- `ARCHITECTURE.md` - Auth and identity context architecture, data flow, and known gaps.
- `DECISIONS.md` - Accepted stack decisions and pending auth decisions.
- `PROJECT_CONTEXT.md` - Current implementation state, security risks, and verification status.

### Auth And Security Code
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java` - Route authorization rules, stateless session policy, JWT filter registration, and password encoder.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/JwtAuthenticationFilter.java` - Bearer token extraction and Spring Security context integration.
- `src/main/java/com/example/feat1/DDD/auth/application/auth_service/jwt/JwtProvider.java` - JWT generation, validation, expiration config, roles, permissions, and token id claims.
- `src/main/java/com/example/feat1/DDD/auth/application/auth_service/jwt/TokenType.java` - Access/refresh token type enum.
- `src/main/java/com/example/feat1/DDD/auth/application/auth_service/jwt/JwtProperties.java` - JWT property binding.
- `src/main/java/com/example/feat1/DDD/auth/TokenSerivce.java` - Existing token issuance, refresh lookup, and logout behavior to stabilize or rename.
- `src/main/java/com/example/feat1/DDD/auth/application/AuthService.java` - Application auth service delegating login, refresh, and logout.
- `src/main/java/com/example/feat1/DDD/auth/application/auth_service/auth_provider/LocalAuthProvider.java` - Local username/password authentication flow.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/presentation/AuthController.java` - Existing `/auth/login`, `/auth/refresh`, and `/auth/logout` controller.
- `src/main/java/com/example/feat1/DDD/auth/application/dto/AuthRequest.java` - Current auth request contract.
- `src/main/java/com/example/feat1/DDD/auth/application/dto/AuthResponse.java` - Current token response contract.

### Refresh Token Persistence And Redis
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/entity/RefreshTokenEntity.java` - Current `refresh_tokens` table mapping.
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/repository/IRefreshTokenRepository.java` - Refresh-token lookup and delete repository methods.
- `src/main/java/com/example/feat1/config/RedisConfig.java` - Existing RedisTemplate configuration.
- `src/main/resources/application.properties` - Current Redis and JWT runtime properties.
- `src/test/resources/application.properties` - Test datasource and runtime overrides.

### Identity And Registration Code
- `src/main/java/com/example/feat1/DDD/identity_context/application/usecase/RegisterUserUseCase.java` - Existing transactional registration use case.
- `src/main/java/com/example/feat1/DDD/identity_context/application/dto/RegisterRequestDto.java` - Current registration DTO with username, email, password, provider id, login type, and roles.
- `src/main/java/com/example/feat1/DDD/identity_context/application/dto/RoleEnum.java` - Current role enum/default role source.
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/presentation/UserController.java` - Existing user lookup controller.
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/entity/UserEntity.java` - User persistence shape.
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/entity/CredentialEntity.java` - Credential persistence shape.
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/entity/RoleEntity.java` - Role persistence shape.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SecurityConfig` already uses stateless Spring Security, permits `/auth/**` and `/menus/public`, protects `/admin/**` with `ADMIN`, protects `/users/**` with `USER` or `ADMIN`, and registers `JwtAuthenticationFilter`.
- `JwtProvider` already creates signed JWTs with subject, roles, issued-at, expiration, and token id. It should be extended or verified to include token type claims and permission claims exactly as required.
- `RefreshTokenEntity` and `IRefreshTokenRepository` already provide a database-backed refresh-token table and token lookup surface.
- `RedisConfig` already configures Redis infrastructure that can be used for refresh-token cache entries.
- `RegisterUserUseCase`, `LocalAuthProvider`, `AuthService`, and `AuthController` provide the core seams for the vertical auth flow.

### Established Patterns
- The backend uses Java 17, Spring Boot, Maven Wrapper, Spring Security, Spring Data JPA, MySQL, Redis/Lettuce, JJWT, Lombok, and Google Java Format through Spotless.
- Domain contexts live under `src/main/java/com/example/feat1/DDD/`, with auth and identity split into separate contexts.
- Phase 01 placed protected admin writes under `/admin/**`; Phase 02 should keep that route family role-protected.
- Tests should be focused and should use the existing Maven/Spring test setup.

### Integration Points
- `/auth/login`, `/auth/refresh`, and `/auth/logout` connect to `AuthController` and `AuthService`.
- Registration needs an HTTP controller path connected to `RegisterUserUseCase`.
- Refresh-token rotation needs coordinated writes to the database repository and Redis cache.
- Logout needs to revoke the submitted refresh token in both cache and database source-of-truth state.
- Reuse detection needs enough token metadata or persisted state to identify the owning user of a revoked/rotated token and revoke all active sessions for that user.

</code_context>

<specifics>
## Specific Ideas

- Redis is intentionally a cache, not the authority. The database refresh-token table is the source of truth.
- Redis should be configured with Docker as part of this phase so local/dev workflows can run the refresh-token cache path.
- Redis miss should not mean invalid token by itself; it should fall back to database validation and then repopulate Redis when the database token is valid.
- Refresh-token reuse after rotation should be treated as suspicious and should revoke every active refresh token for that user.

</specifics>

<deferred>
## Deferred Ideas

- Google/OAuth login remains out of scope for this local auth MVP.
- Password reset and email verification remain out of scope.
- Full role/permission admin UI remains out of scope.
- Detailed discussion of API prefix, registration role policy, auth error response body, and naming/refactor cleanup was deferred.

</deferred>

---

*Phase: 02-auth-context-mvp*
*Context gathered: 2026-07-04*
