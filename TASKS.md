# Tasks

## Immediate Build And Runtime Stabilization

- [ ] Configure `JAVA_HOME` locally and run `.\mvnw.cmd test`.
- [ ] Fix any compile errors reported by Maven after Java is configured.
- [ ] Remove or relocate `src/Main.java` if it is not intentionally part of the project.
- [ ] Fix `SecurityConfig` package declaration so it is under the Spring Boot component scan path.
- [ ] Align auth route security: either permit `/auth/**` or move controllers under `/api/auth/**`.
- [ ] Register `JwtAuthenticationFilter` in the Spring Security filter chain.
- [ ] Verify Spring Boot 4.0.6 dependency compatibility with the installed Java and Maven environment.

## Authentication

- [ ] Implement `LocalAuthProvider.authenticate`.
- [ ] Implement `GoogleAuthProvider.authenticate` or remove the provider until OAuth is in scope.
- [ ] Implement `AuthService.refreshToken`.
- [ ] Implement `AuthService.logout`.
- [ ] Decide whether `TokenSerivce` is the token lifecycle service and rename it to `TokenService`.
- [ ] Persist refresh tokens when issuing token pairs.
- [ ] Validate refresh-token expiry in addition to JWT signature validity.
- [ ] Add tests for login success, login failure, refresh success, refresh invalid token, and logout.

## User And Identity

- [ ] Expose a registration endpoint if registration is part of the current product scope.
- [ ] Annotate `RegisterUserUseCase` as a Spring bean or wire it through explicit configuration.
- [ ] Fix `UserDomainRepository.findById` so it queries persistence instead of returning `Optional.empty()`.
- [ ] Review `RegisterRequestDto` field naming, especially `Roles`, and normalize Java naming conventions.
- [ ] Decide whether controllers should return domain aggregates or response DTOs.
- [ ] Add validation for duplicate users, missing roles, and unsupported auth providers.
- [ ] Add tests for `UserDomainService`, registration flow, and user lookup by id.

## Domain And Persistence

- [ ] Review UUID generation across domain objects and JPA entities to avoid conflicting id ownership.
- [ ] Review JPA cascade settings for user-role and role-permission relationships.
- [ ] Ensure credential ids are generated or assigned consistently before saving.
- [ ] Add repository tests for `IUserRepository.findByIdWithRoles`, credential lookup, role lookup, and refresh-token lookup.
- [ ] Decide whether mappers should stay static or become injectable components.
- [ ] Audit mapper null handling and Optional usage for consistency.

## Security And Configuration

- [ ] Move datasource password, JWT secret, and Redis password out of `application.properties`.
- [ ] Introduce environment-specific profiles such as `application-local.properties`.
- [ ] Replace placeholder JWT secret with a sufficiently long environment-supplied secret.
- [ ] Disable SQL logging outside local development.
- [ ] Review CSRF strategy based on whether the API is browser-session based or token-only.
- [ ] Add authorization tests for public auth routes, admin routes, user routes, and authenticated fallback routes.

## Code Quality

- [ ] Rename `infastructure` packages to `infrastructure`.
- [ ] Rename `IRolePermisionRepository` and `rolePermision` to use `Permission`.
- [ ] Rename `roleDomainRepository` to `RoleDomainRepository`.
- [ ] Remove unused imports across auth, domain, and mapper classes.
- [ ] Run Spotless after package and naming changes.
- [ ] Add a README once the service can run locally end to end.

## Documentation Follow-Up

- [ ] Add endpoint request and response examples after auth implementations are complete.
- [ ] Add local setup steps after `JAVA_HOME`, MySQL, and Redis expectations are verified.
- [ ] Add database schema notes once entity relationships are confirmed by tests.
- [ ] Keep `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, `TASKS.md`, and `DECISIONS.md` updated when major architecture choices change.

