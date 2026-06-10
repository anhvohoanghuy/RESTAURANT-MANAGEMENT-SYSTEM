# Project Context

## Summary

`feat1` is a Spring Boot backend focused on identity and authentication. The codebase is organized around a DDD-style package structure with an `auth` area for login, JWTs, refresh tokens, and security, plus an `identity_context` area for users, credentials, roles, permissions, and registration.

The application entry point is `src/main/java/com/example/feat1/Feat1Application.java`. The build is Maven-based through `pom.xml` and targets Java 17.

## Current Stack

- Java 17
- Spring Boot 4.0.6
- Spring Web
- Spring Security
- Spring OAuth2 Client
- Spring Data JPA
- MySQL connector
- Redis support through Spring Data Redis and Lettuce pooling
- JJWT 0.12.5 for JWT creation and validation
- Lombok
- Maven Wrapper
- Spotless with Google Java Format

## Runtime Configuration

Runtime settings live in `src/main/resources/application.properties`.

Observed configuration areas:

- MySQL datasource points at a local `mydb` database.
- Hibernate is configured with `spring.jpa.hibernate.ddl-auto=update`.
- SQL logging is enabled.
- JWT access and refresh expirations are configured under the `jwt.*` prefix.
- Redis host, port, timeout, and pool settings are configured under `spring.data.redis.*`.

Security-sensitive values are currently present directly in `application.properties`. Treat them as local-development placeholders and move them to environment-specific configuration before using this application outside a local environment.

## Main Features Present

- `POST /auth/login` delegates to `AuthService.login`.
- `POST /auth/refresh` delegates to `AuthService.refreshToken`.
- `POST /auth/logout` delegates to `AuthService.logout`.
- `GET /users/{id}` returns a domain `User` through `UserService`.
- JWT generation and validation are implemented in `JwtProvider`.
- Refresh token persistence is represented by `RefreshTokenEntity` and `IRefreshTokenRepository`.
- User, role, permission, credential, and user-role JPA entities exist under `identity_context/infastructure/entity`.
- Domain repositories adapt JPA repositories into domain-facing repository interfaces.

## Current Implementation State

The codebase is in an early implementation state. Several important flows are scaffolded but not complete:

- `AuthService.refreshToken` returns `null`.
- `AuthService.logout` is empty.
- `LocalAuthProvider.authenticate` returns `null`.
- `GoogleAuthProvider.authenticate` returns `null`.
- `UserDomainRepository.findById` returns `Optional.empty()`.
- `RegisterUserUseCase` is not annotated as a Spring bean.
- `SecurityConfig` is physically under `com/example/feat1/...` but declares package `com.example.config`.
- `SecurityConfig` permits `/api/auth/**`, while `AuthController` exposes `/auth/**`.
- `JwtAuthenticationFilter` exists but is not registered in the security filter chain.
- Package and class names include spelling inconsistencies such as `infastructure` and `TokenSerivce`.
- `src/Main.java` is an IntelliJ starter class outside the Spring Boot package structure and is not part of the application architecture.

## Verification Status

Attempted command:

```bash
.\mvnw.cmd test
```

Result: the command did not reach Maven because `JAVA_HOME` is not configured in this shell.

Because tests could not run, compile and runtime status should be treated as unverified. Code inspection also shows likely compile or runtime blockers, including mismatched security packages, unfinished service methods, and JWT/domain method calls that should be checked once Java is configured.

## Primary Risks

- Authentication endpoints may not work end to end because providers and refresh/logout methods are incomplete.
- Security rules may not apply as intended due to route mismatch and package mismatch.
- JWT authorization may not run because the JWT filter is not wired into the filter chain.
- Local database, Redis, password, and JWT secret values are hardcoded in properties.
- Repository methods and mapper behavior are partially implemented and may break user lookup or authentication.
- Domain and infrastructure package naming inconsistencies will make navigation and future refactors harder.

