# Phase 03: Google OAuth 2 login - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-07-04
**Phase:** 03-google-oauth-2-login
**Areas discussed:** Phase shape, OAuth flow, account linking, endpoint contract, Google token storage, signup behavior, profile data, error contract, audience config, domain policy, GSD artifacts

---

## Phase Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Create Phase 03 | Keep Phase 02 local auth complete; Google OAuth is its own clean phase. | yes |
| Reopen Phase 02 | Expand the completed local-auth phase. | |
| Temporary discussion | Discuss without writing GSD artifacts. | |

**User's choice:** Create Phase 03.
**Notes:** Phase name locked as Google OAuth 2 login.

---

## OAuth Flow

| Option | Description | Selected |
|--------|-------------|----------|
| ID token exchange | Client obtains Google ID token; backend verifies it and issues internal tokens. | yes |
| Server redirect flow | Backend handles Google authorization-code redirect/callback. | |
| Support both | Implement both flow families in one phase. | |

**User's choice:** ID token exchange.
**Notes:** Redirect/code flow deferred.

---

## Account Linking

| Option | Description | Selected |
|--------|-------------|----------|
| Reject linking | Avoid auto-linking existing local users. | |
| Auto-link verified email | Link existing local users when Google email verification and authority policy pass. | yes |
| Duplicate blocked | Reject existing email with conflict. | |

**User's choice:** Auto-link verified email.
**Notes:** Follow-up decision narrowed this to Google-authoritative email only.

---

## Auto-link Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Google-authoritative only | Auto-link only `gmail.com` or token with `hd`. | yes |
| Any verified email | Auto-link any `email_verified` token. | |
| Config flag | Strict by default, looser by property. | |

**User's choice:** Google-authoritative only.
**Notes:** Based on Google guidance around third-party email authority.

---

## Endpoint And Tokens

| Option | Description | Selected |
|--------|-------------|----------|
| `POST /auth/google` | Body `{ idToken }`, response existing `AuthResponse`. | yes |
| `POST /auth/login/google` | Provider-specific login path. | |
| `POST /auth/login` | Reuse existing login with public `AuthType`. | |

**User's choice:** `POST /auth/google`.
**Notes:** Do not store Google access or refresh tokens.

---

## Signup, Profile, And Errors

| Decision | Selected |
|----------|----------|
| Auto-register unknown Google users as `USER`. | yes |
| Use Google `name`, fallback to email local-part/full email. | yes |
| Use stable error codes instead of mirroring Google errors. | yes |

---

## Configuration

| Option | Description | Selected |
|--------|-------------|----------|
| Multiple client IDs | Property/env list supports web/mobile/staging/prod. | yes |
| Single client ID | Simpler single-client setup. | |
| Hardcoded dev value | Fast but unsafe. | |

**User's choice:** Multiple client IDs.
**Notes:** No login domain allowlist in this phase.

---

## Deferred Ideas

- Server-side OAuth authorization-code redirect/callback flow.
- Google API access with stored Google access/refresh tokens.
- Domain allowlist or `hd`-required login.
- User-facing account-linking UI and unlinking flow.
