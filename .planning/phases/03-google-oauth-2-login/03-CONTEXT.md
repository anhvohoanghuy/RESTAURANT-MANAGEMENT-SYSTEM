# Phase 03: Google OAuth 2 login - Context

**Gathered:** 2026-07-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Add Google OAuth 2 login to the existing backend auth context. Clients obtain a Google ID token and send it to the backend. The backend verifies the Google identity, creates or links a Google credential when allowed, and returns the same internal access/refresh token response used by local auth.

In scope:
- Public `POST /auth/google` accepting `{ "idToken": "..." }`.
- Google ID-token verification for signature, issuer, expiry, configured audiences, subject, email, and `email_verified`.
- Backend-issued access/refresh token pair using the existing refresh-token database and Redis cache behavior.
- Auto-register new Google users as `USER`.
- Auto-link an existing local user only when Google is authoritative for the email.
- Focused unit, controller, and integration tests with mocked Google verification.

Out of scope:
- Server-side authorization-code redirect/callback flow.
- Storing Google access tokens or Google refresh tokens.
- Calling Google APIs on behalf of the user.
- Domain allowlists or `hd`-required login.
- Password reset, email verification, or account-linking UI.

</domain>

<decisions>
## Implementation Decisions

### OAuth Flow
- **D-01:** Use Google ID-token exchange. Frontend/mobile obtains the Google ID token and posts it to the backend.
- **D-02:** Do not implement server-side redirect/callback authorization-code flow in this phase.
- **D-03:** Do not persist Google access tokens or Google refresh tokens. The app continues using its own access/refresh token lifecycle.

### Public API Contract
- **D-04:** Add `POST /auth/google`.
- **D-05:** Request body is `{ "idToken": "..." }`.
- **D-06:** Response body is the existing `AuthResponse`: `{ accessToken, refreshToken, tokenType, accessExpiresIn, refreshExpiresIn }`.

### Verification Policy
- **D-07:** Verify Google token signature, issuer, expiry, `aud`, `sub`, `email`, and `email_verified`.
- **D-08:** Configure one or more accepted Google client IDs through property/env, for example `google.oauth.client-ids`.
- **D-09:** Stable error codes are required: `GOOGLE_TOKEN_REQUIRED`, `GOOGLE_TOKEN_INVALID`, `GOOGLE_EMAIL_UNVERIFIED`, and `GOOGLE_EMAIL_NOT_LINKABLE`.

### Account Creation And Linking
- **D-10:** If a Google credential already exists, log in that user.
- **D-11:** If no user exists for the Google email, auto-register a new user with role `USER`.
- **D-12:** If a local user exists for the Google email, auto-link only when the Google email is authoritative: the email ends with `@gmail.com` or the token contains an `hd` hosted-domain claim.
- **D-13:** If an existing email is not linkable under D-12, reject with `GOOGLE_EMAIL_NOT_LINKABLE`.
- **D-14:** New Google user display name uses the Google `name` claim, falling back to the email local-part or full email.

### the agent's Discretion
- Choose the concrete Spring Security JWT verification API that best fits the existing dependencies, but keep it behind a verifier interface so tests do not call Google.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Planning Scope
- `.planning/ROADMAP.md` - Phase 03 goal, dependency, and success criteria.
- `.planning/REQUIREMENTS.md` - `AUTH-011` requirement and traceability.
- `.planning/STATE.md` - Current milestone context.
- `.planning/phases/02-auth-context-mvp/02-CONTEXT.md` - Existing local auth and refresh-token decisions that must stay unchanged.

### Existing Auth And Identity Code
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/presentation/AuthController.java` - Public auth route contract.
- `src/main/java/com/example/feat1/DDD/auth/application/AuthService.java` - Auth provider delegation.
- `src/main/java/com/example/feat1/DDD/auth/application/auth_service/auth_provider/GoogleAuthProvider.java` - Existing Google provider placeholder.
- `src/main/java/com/example/feat1/DDD/auth/TokenSerivce.java` - Internal token-pair issuance.
- `src/main/java/com/example/feat1/DDD/identity_context/application/usecase/RegisterUserUseCase.java` - Current local registration pattern.
- `src/main/java/com/example/feat1/DDD/identity_context/domain/repository/credential/ICredentialDomainRepository.java` - Credential lookup and persistence seam.
- `src/main/java/com/example/feat1/DDD/identity_context/domain/repository/user/IUserDomainRepository.java` - User lookup and persistence seam.

### External References
- `https://developers.google.com/identity/gsi/web/guides/verify-google-id-token` - Google guidance for server-side ID-token verification.
- `https://developers.google.com/identity/protocols/oauth2/web-server` - Google web-server OAuth flow reference, used here only to keep redirect/code flow out of this phase.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AuthService` already delegates by `AuthType` to provider beans named `LOCAL` and `GOOGLE`.
- `GoogleAuthProvider` already exists as the correct provider extension point.
- `TokenSerivce.generateAccessToken(User)` already creates the app token pair and persists refresh-token state.
- `RegisterUserUseCase`, role seed, and repositories already support creating users with default `USER` role.

### Established Patterns
- Public auth endpoints live under `/auth/**`.
- App errors use `AppException` and JSON `{ code, message, timestamp }`.
- Tests should mock external dependencies; Redis is a cache and should not be required for auth integration tests.

### Integration Points
- `POST /auth/google` maps to `AuthRequest(AuthType.GOOGLE, ..., idToken)`.
- Google verifier returns normalized claims to `GoogleAuthProvider`.
- Provider creates/links `Credential(AuthProvider.GOOGLE, sub)` and then calls `TokenSerivce.generateAccessToken`.

</code_context>

<specifics>
## Specific Ideas

- Use multiple accepted audiences so web, mobile, staging, and prod clients can share the backend.
- Auto-link only Google-authoritative email to reduce takeover risk for non-Google-managed third-party email addresses.

</specifics>

<deferred>
## Deferred Ideas

- Server-side OAuth authorization-code redirect/callback flow.
- Domain allowlist or `hd`-required login.
- Storing Google access/refresh tokens to call Google APIs.
- User-facing account-linking UI and unlinking flow.

</deferred>

---

*Phase: 03-google-oauth-2-login*
*Context gathered: 2026-07-04*
