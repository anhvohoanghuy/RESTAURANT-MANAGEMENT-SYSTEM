---
phase: 03-google-oauth-2-login
status: passed
verified: 2026-07-07
requirements: [AUTH-011]
score: 6/6 success criteria verified
---

# Verification: Phase 03 Google OAuth 2 Login

## Result

Passed.

**Goal:** Add Google OAuth 2 login through backend verification of Google ID tokens, preserving the existing internal JWT access/refresh-token lifecycle and local auth behavior.

Retroactive goal-backward verification against the actual implemented code. Every ROADMAP success criterion is delivered and covered by passing tests.

## Success Criteria Coverage

1. **PASS** — Public clients can call `POST /auth/google` with `{ "idToken": "..." }`.
   - `AuthController.google(...)` maps `POST /auth/google` to `AuthService.login(...)` with `AuthType.GOOGLE` and the `idToken` as the OAuth token (`AuthController.java:104-124`, `toGoogleAuthRequest` at `:266-270`). Request DTO `GoogleLoginRequest(String idToken)` exists (`GoogleLoginRequest.java`).

2. **PASS** — Backend verifies signature, issuer, expiry, configured audience, subject, email, and email verification before issuing internal tokens.
   - `SpringGoogleIdTokenVerifier` uses `NimbusJwtDecoder.withJwkSetUri(...)` against Google's JWK set for signature/expiry, checks issuer against `accounts.google.com` variants, verifies `aud` membership from `google.oauth.client-ids`, and requires non-blank `sub`/`email` plus `email_verified=true` (`SpringGoogleIdTokenVerifier.java:36-69`). Accepted audiences come from `application.properties:20` (`google.oauth.client-ids=${GOOGLE_OAUTH_CLIENT_IDS:}`), supporting multiple comma-separated IDs. Stable error codes `GOOGLE_TOKEN_INVALID` and `GOOGLE_EMAIL_UNVERIFIED` are raised (D-09). Production path does not call Google tokeninfo.

3. **PASS** — Existing Google credentials log in the mapped user without storing Google access or refresh tokens.
   - `OAuth2AuthenticationStrategy.authenticate(...)` looks up the credential by provider + `sub`; when present it loads the user with roles and issues the backend token pair, saving nothing (`OAuth2AuthenticationStrategy.java:54-68`). `Credential.createOAuth(...)` persists only `userId`, `provider`, and `providerUserId` — the `Credential` entity has no field for Google access/refresh tokens (`Credential.java:36-43`), satisfying D-03. Test `existingGoogleCredentialLogsInMappedUser` asserts no user/credential save occurs.

4. **PASS** — New verified Google users are auto-registered with `USER`.
   - `registerUser(...)` creates the user, marks email verified, looks up the `USER` role, assigns it, and saves user + Google credential (`OAuth2AuthenticationStrategy.java:101-118`). Test `validGoogleTokenCreatesUserCredentialAndReturnsBackendTokenPair` asserts the created user has exactly role `USER` and a Google credential with null password hash.

5. **PASS** — Existing local users are auto-linked only when Google is authoritative for the email (`gmail.com` or `hd` claim present).
   - `GoogleOAuth2IdentityProviderStrategy.canAutoLink(...)` returns true only for `@gmail.com` emails or a non-blank hosted-domain (`hd`) claim (`GoogleOAuth2IdentityProviderStrategy.java:31-35`). `linkExistingUser(...)` rejects non-linkable emails with `GOOGLE_EMAIL_NOT_LINKABLE` before saving any credential (`OAuth2AuthenticationStrategy.java:80-99`). Tests `verifiedGmailAddressLinksExistingLocalUser` and `nonAuthoritativeExistingEmailIsNotAutoLinked` cover both branches (D-12, D-13).

6. **PASS** — Focused tests cover create, login, auto-link, rejected non-authoritative link, controller mapping, and HTTP integration.
   - `GoogleAuthProviderTest` (5 tests): create, existing-credential login, gmail auto-link, rejected non-authoritative link, missing/invalid/unverified token errors.
   - `AuthControllerTest.googleLoginUsesGoogleAuthTypeAndIdToken`: verifies `/auth/google` maps to `AuthType.GOOGLE` with the id token.
   - `AuthFlowIntegrationTest.googleLoginAutoRegistersUserAndBackendTokenWorksForProfile`: HTTP `/auth/google` returns the backend token pair, persists user + Google credential, and the access token works against `/users/me`. Verifier is mocked (`@MockitoBean GoogleIdTokenVerifier`), so no Google network or Redis dependency.

## Requirement Coverage

- `AUTH-011`: Google OAuth 2 ID-token login — implemented and covered by `GoogleAuthProviderTest`, `OAuth2AuthenticationStrategyTest`, `AuthControllerTest#googleLoginUsesGoogleAuthTypeAndIdToken`, and `AuthFlowIntegrationTest#googleLoginAutoRegistersUserAndBackendTokenWorksForProfile`.

## Automated Verification

- Command: `./mvnw -o test -Dtest='GoogleAuthProviderTest,OAuth2AuthenticationStrategyTest,AuthControllerTest'`
  - Result: 15 tests, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS. (Java 17.0.19)
- Command: `./mvnw -o test -Dtest='AuthFlowIntegrationTest#googleLoginAutoRegistersUserAndBackendTokenWorksForProfile'`
  - Result: 1 test, 0 failures, 0 errors. BUILD SUCCESS.

## Key Link Verification

| From | To | Via | Status |
| ---- | -- | --- | ------ |
| `AuthController` | `AuthService` | `POST /auth/google` -> `toGoogleAuthRequest` -> `AuthType.GOOGLE` | WIRED |
| `AuthService` | `GoogleAuthProvider` | `authProviderMap.get("GOOGLE")` bean lookup | WIRED |
| `GoogleAuthProvider` | `OAuth2AuthenticationStrategy` + `GoogleOAuth2IdentityProviderStrategy` | constructor injection, delegated `authenticate` | WIRED |
| `GoogleOAuth2IdentityProviderStrategy` | `GoogleIdTokenVerifier` | `verify(token)` | WIRED |
| `OAuth2AuthenticationStrategy` | credential/user/role repos, `TokenSerivce` | create/link/login + token issuance | WIRED |

## Notes / Deviations

- **Architectural deviation (accepted):** The plan described the account create/login/link logic living directly in `GoogleAuthProvider`. The implementation instead extracts a reusable `OAuth2AuthenticationStrategy` plus a per-provider `OAuth2IdentityProviderStrategy` (Google implementation), with `GoogleAuthProvider` as a thin delegator. This is a stronger design (future providers reuse the flow) and fully satisfies every success criterion; error codes are generated as `<PROVIDER>_TOKEN_REQUIRED` / `_EMAIL_UNVERIFIED` / `_EMAIL_NOT_LINKABLE`, which resolve to the required `GOOGLE_*` codes for this provider. Not a gap.
- `GoogleUserInfo` DTO exists alongside the interface `GoogleIdTokenVerifier`, both as planned.
- Google verifier is production-capable (`SpringGoogleIdTokenVerifier`) and mockable (interface mocked in all tests); no test reaches Google or Redis.
- User setup: real client IDs must be configured via `GOOGLE_OAUTH_CLIENT_IDS` before use outside tests (see `03-USER-SETUP.md`).

## Verdict

Phase 03 satisfies the Google OAuth 2 login goal, all six ROADMAP success criteria, and requirement `AUTH-011`. Overall: PASS.
