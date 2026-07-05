---
phase: 06-auth-hardening
created: 2026-07-05
status: active
requirements: [AUTH-015, AUTH-016, AUTH-017, AUTH-018]
---

# Phase 06 Context: Auth Hardening

## Confirmed Scope

- Add Redis-backed auth rate limits.
- Add local login lockout after repeated failed attempts.
- Persist security audit events for auth-sensitive actions.
- Extend refresh-token persistence with session metadata.
- Add self-service session APIs for the current authenticated user.

## Explicitly Out Of Scope

- MFA/TOTP.
- Admin session management.
- SMTP/provider email delivery.
- New auth response shape.
- Generic OAuth endpoint changes.

## Policy Decisions

- Login rate limit: 5 attempts per minute per username and IP.
- Local account lockout: 15 minutes after 5 failed local login attempts.
- Recovery rate limit: 3 requests per hour per email plus IP guard.
- Google OAuth rate limit: IP-level guard.
- Redis outage fails open for limit checks and lockout guards, with logging.
- Audit writes are best effort and should not block normal auth flows.
- Session revocation writes are critical and must persist.

## Stable Error Codes

- `RATE_LIMIT_EXCEEDED`
- `ACCOUNT_LOCKED`
