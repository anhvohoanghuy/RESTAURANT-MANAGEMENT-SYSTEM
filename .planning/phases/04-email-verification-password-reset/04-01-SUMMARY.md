---
phase: 04-email-verification-password-reset
plan: 01
tags: [auth, email-verification, password-reset, jwt, jpa]
status: complete
completed: 2026-07-04
---

# Phase 04 Summary: Email Verification And Password Reset

## Completed

- Added backend-only email verification and password reset APIs under `/auth/**`.
- Added `emailVerified` and `emailVerifiedAt` user/profile state.
- Added `auth_action_tokens` persistence for hashed single-use `EMAIL_VERIFICATION` and `PASSWORD_RESET` tokens.
- Added `EmailNotificationPort` and no-op adapter so raw tokens are emitted through a replaceable boundary, not public responses.
- Local registration now requests a verification notification.
- Google create/link flows mark verified Google emails as verified.
- Password reset changes only local credentials and revokes active refresh tokens after success.

## Verification

- `.\mvnw.cmd test`
- Result: 38 tests, 0 failures, 0 errors, build success.

## Notes

- No SMTP or external provider integration was added.
- Unverified users are still allowed to log in and use protected APIs in this phase.
- Public API responses never return raw verification or reset tokens.
