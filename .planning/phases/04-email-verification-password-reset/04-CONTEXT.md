# Phase 04: email-verification-password-reset - Context

**Gathered:** 2026-07-04
**Status:** Ready for execution

<domain>
## Phase Boundary

Add backend-only email verification and password reset flows. The backend owns token generation, token hashing, validation, single-use consumption, and user/credential state changes. Actual SMTP or provider delivery is out of scope; notifications go through a replaceable port.

In scope:
- Email verification request and consume endpoints.
- Password forgot and reset endpoints for local credentials.
- `emailVerified` and `emailVerifiedAt` profile state.
- Single-use hashed action tokens for `EMAIL_VERIFICATION` and `PASSWORD_RESET`.
- Notification port/fake behavior for tests and later SMTP/provider integration.

Out of scope:
- SMTP, MailHog, SendGrid, Mailgun, or any real email provider.
- Blocking login or protected routes for unverified users.
- Account recovery UI.
- Password reset for OAuth-only accounts.

</domain>

<decisions>
## Implementation Decisions

### API Contract
- **D-01:** `POST /auth/email/verification/request` accepts `{ "email": "..." }` and returns `202 Accepted`.
- **D-02:** `POST /auth/email/verify` accepts `{ "token": "..." }` and returns `204 No Content`.
- **D-03:** `POST /auth/password/forgot` accepts `{ "email": "..." }` and returns `202 Accepted` generically.
- **D-04:** `POST /auth/password/reset` accepts `{ "token": "...", "newPassword": "..." }` and returns `204 No Content`.
- **D-05:** Public API responses never include raw verification or reset tokens.

### Token Policy
- **D-06:** Store only token hashes in the database.
- **D-07:** Raw tokens are passed once to `EmailNotificationPort`.
- **D-08:** Email verification tokens expire after 24 hours by default.
- **D-09:** Password reset tokens expire after 15 minutes by default.
- **D-10:** Verification and reset tokens are single-use.

### Account Behavior
- **D-11:** Registration creates local users with `emailVerified=false`.
- **D-12:** Registration requests an email verification notification after successful local registration.
- **D-13:** Unverified users may still log in and use protected routes in this phase.
- **D-14:** Google-authenticated verified emails mark the user email as verified when creating or linking an account.
- **D-15:** Password reset changes only local credentials.
- **D-16:** OAuth-only users do not receive a password reset token.
- **D-17:** Successful password reset revokes all active refresh tokens for that user.

### Errors
- **D-18:** Invalid verification tokens return `EMAIL_VERIFICATION_TOKEN_INVALID`.
- **D-19:** Expired verification tokens return `EMAIL_VERIFICATION_TOKEN_EXPIRED`.
- **D-20:** Invalid reset tokens return `PASSWORD_RESET_TOKEN_INVALID`.
- **D-21:** Expired reset tokens return `PASSWORD_RESET_TOKEN_EXPIRED`.
- **D-22:** Invalid reset passwords return `PASSWORD_RESET_PASSWORD_INVALID`.

</decisions>

<canonical_refs>
## Canonical References

- `.planning/ROADMAP.md` - Phase 04 goal and success criteria.
- `.planning/REQUIREMENTS.md` - AUTH-012 through AUTH-014.
- `.planning/phases/02-auth-context-mvp/02-CONTEXT.md` - Local auth and refresh-token source-of-truth decisions.
- `.planning/phases/03-google-oauth-2-login/03-CONTEXT.md` - Google account creation/linking behavior.
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/presentation/AuthController.java` - Auth API surface.
- `src/main/java/com/example/feat1/DDD/auth/TokenSerivce.java` - Refresh-token issuance and revocation.
- `src/main/java/com/example/feat1/DDD/identity_context/domain/model/entity/Credential.java` - Local credential password change behavior.
- `src/main/java/com/example/feat1/DDD/identity_context/infastructure/presentation/UserController.java` - Profile response.

</canonical_refs>

<deferred>
## Deferred Ideas

- Real SMTP/provider delivery.
- Email templates and frontend account recovery UI.
- Blocking unverified accounts from login or protected APIs.

</deferred>

---

*Phase: 04-email-verification-password-reset*
*Context gathered: 2026-07-04*
