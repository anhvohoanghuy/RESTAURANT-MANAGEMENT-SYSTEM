---
phase: 06-auth-hardening
plan: 06-01
status: complete
completed: 2026-07-05
requirements: [AUTH-015, AUTH-016, AUTH-017, AUTH-018]
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/auth/application/AuthRateLimitService.java
    - src/main/java/com/example/feat1/DDD/auth/application/LoginLockoutService.java
    - src/main/java/com/example/feat1/DDD/auth/application/SecurityAuditService.java
    - src/main/java/com/example/feat1/DDD/auth/application/AuthSessionService.java
    - src/main/java/com/example/feat1/DDD/auth/infrastructure/entity/SecurityAuditEventEntity.java
  modified:
    - src/main/java/com/example/feat1/DDD/auth/infrastructure/presentation/AuthController.java
    - src/main/java/com/example/feat1/DDD/auth/TokenSerivce.java
    - src/main/java/com/example/feat1/DDD/auth/infrastructure/entity/RefreshTokenEntity.java
    - src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java
---

# Summary: Implement Auth Hardening

## Completed

- Added Redis-backed auth rate limiting for local login, Google OAuth, and recovery request endpoints.
- Added Redis-backed local login lockout with `ACCOUNT_LOCKED`.
- Added persistent DB audit events with event type, outcome, nullable user id, principal, IP, user agent, reason, and timestamp.
- Extended refresh tokens with session metadata: IP, user agent, and last-used time.
- Added authenticated session APIs:
  - `GET /auth/sessions`
  - `DELETE /auth/sessions/{sessionId}`
  - `POST /auth/sessions/revoke-others`
- Updated auth security matchers so session endpoints require authentication while existing public auth endpoints remain public.
- Added focused unit and integration tests for Phase 06 behavior.

## Verification

- `mvnw.cmd test` passed on 2026-07-05.
- Result: 57 tests, 0 failures, 0 errors.

## Deviations

- Redis-facing integration tests mock rate-limit and lockout services to avoid requiring a real Redis server in the Maven test suite. Redis behavior is covered by focused unit tests with mocked `StringRedisTemplate`.
