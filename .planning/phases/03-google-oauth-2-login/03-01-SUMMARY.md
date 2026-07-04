---
phase: 03-google-oauth-2-login
plan: 01
subsystem: auth
tags: [google-oauth, jwt, spring-security, jpa]
requires:
  - phase: 02-auth-context-mvp
    provides: Local auth, internal JWT token pair, refresh-token lifecycle, roles, and global error contract.
provides:
  - Google ID-token login endpoint.
  - Mockable Google token verifier abstraction.
  - Google credential create/link behavior.
  - Tests for Google login provider, controller mapping, and HTTP profile flow.
affects: [auth, identity, security]
tech-stack:
  added: []
  patterns: [Provider-based social auth, verifier abstraction for external identity tokens]
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/auth/application/dto/GoogleLoginRequest.java
    - src/main/java/com/example/feat1/DDD/auth/application/auth_service/google/GoogleIdTokenVerifier.java
    - src/main/java/com/example/feat1/DDD/auth/application/auth_service/google/GoogleUserInfo.java
    - src/main/java/com/example/feat1/DDD/auth/infrastructure/google/SpringGoogleIdTokenVerifier.java
    - src/test/java/com/example/feat1/DDD/auth/application/auth_service/auth_provider/GoogleAuthProviderTest.java
  modified:
    - src/main/java/com/example/feat1/DDD/auth/application/auth_service/auth_provider/GoogleAuthProvider.java
    - src/main/java/com/example/feat1/DDD/auth/infrastructure/presentation/AuthController.java
    - src/test/java/com/example/feat1/DDD/auth/integration/AuthFlowIntegrationTest.java
key-decisions:
  - "Use Google ID-token exchange, not server-side redirect/code flow."
  - "Auto-link existing local users only when Google is authoritative for the email."
  - "Do not store Google access or refresh tokens."
patterns-established:
  - "External identity verification is behind an application interface and mocked in tests."
  - "Social login providers issue the same backend AuthResponse and reuse refresh-token persistence."
requirements-completed: [AUTH-011]
duration: 45min
completed: 2026-07-04
---

# Phase 03: Google OAuth 2 Login Summary

**Google ID-token login now issues the existing backend access/refresh token pair without changing local auth.**

## Performance

- **Duration:** 45 min
- **Started:** 2026-07-04T15:24:00+07:00
- **Completed:** 2026-07-04T16:09:00+07:00
- **Tasks:** 4 completed
- **Files modified:** 20+

## Accomplishments

- Added `POST /auth/google` with `{ "idToken": "..." }` and the existing token response shape.
- Implemented Google ID-token verification with configurable accepted audiences.
- Implemented Google credential login, auto-register, and strict auto-link behavior.
- Added provider, controller, and integration tests; full Maven suite passes.

## Task Commits

No commits were created during this execution.

## Files Created/Modified

- `src/main/java/com/example/feat1/DDD/auth/application/auth_service/auth_provider/GoogleAuthProvider.java` - Implements Google login, registration, and linking policy.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/google/SpringGoogleIdTokenVerifier.java` - Verifies Google ID token signature and claims using Spring Security JWT support.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/presentation/AuthController.java` - Adds `POST /auth/google`.
- `src/test/java/com/example/feat1/DDD/auth/application/auth_service/auth_provider/GoogleAuthProviderTest.java` - Covers provider policy branches.
- `src/test/java/com/example/feat1/DDD/auth/integration/AuthFlowIntegrationTest.java` - Covers HTTP Google login and `/users/me`.

## Decisions Made

None beyond `03-CONTEXT.md`; implementation followed the captured plan.

## Deviations from Plan

- Added `03-USER-SETUP.md` so external Google client ID setup is explicit.
- Hardened `CustomUserDetailsService` to tolerate OAuth credentials without password hashes.

## Issues Encountered

- PowerShell requires quoting comma-separated Maven `-Dtest` values.
- GSD `gsd-sdk.ps1` was blocked by execution policy, so `.cmd` was used.

## User Setup Required

External Google client IDs must be configured before using the endpoint outside tests. See `03-USER-SETUP.md`.

## Next Phase Readiness

Future phases can add account-linking UI, unlinking, domain allowlists, or server-side OAuth authorization-code flow without changing this endpoint contract.

---
*Phase: 03-google-oauth-2-login*
*Completed: 2026-07-04*
