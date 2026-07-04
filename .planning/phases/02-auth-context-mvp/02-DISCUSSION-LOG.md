# Phase 02: auth-context-mvp - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-07-04
**Phase:** 02-auth-context-mvp
**Areas discussed:** Refresh-token policy

---

## Refresh-Token Policy

| Option | Description | Selected |
|--------|-------------|----------|
| Single-session per user | Each user has one active refresh token. New login revokes the old token. Simpler for MVP. | |
| Multi-session per user | Multiple devices/browsers can stay logged in independently. Logout revokes the current token. | Yes |
| Agent decide | Let the agent choose the simplest policy from requirements and code context. | |

**User's choice:** Multi-session per user.
**Notes:** User requested Redis configuration through Docker for refresh-token storage/cache. Refresh tokens must also be stored in a separate database table as the source of truth. If Redis does not contain a refresh token, the system should check the database and cache the token again when it is valid.

---

## Refresh Rotation

| Option | Description | Selected |
|--------|-------------|----------|
| Rotate refresh token every refresh | Refresh success revokes the old refresh token and returns a new access token plus a new refresh token. | Yes |
| Keep refresh token until expiry | Refresh only returns a new access token and keeps the existing refresh token. | |
| Rotate only near expiry | Refresh token changes only when close to expiration. | |

**User's choice:** Rotate refresh token every refresh.
**Notes:** The previous refresh token should be revoked or deleted from Redis and database state when a new refresh token is issued.

---

## Reuse Detection

| Option | Description | Selected |
|--------|-------------|----------|
| Revoke all sessions for the user | Treat reuse of a rotated/revoked token as a token-theft signal and revoke every active refresh token for the user. | Yes |
| Only reject that token | Reject the reused token while leaving other sessions alive. | |
| Agent decide | Let the agent choose a balanced MVP policy. | |

**User's choice:** Revoke all sessions for the user.
**Notes:** Any session/device using that user's refresh tokens should be forced to log in again after reuse is detected.

---

## Redis Authority

| Option | Description | Selected |
|--------|-------------|----------|
| Soft cache, database fallback | Redis is a cache. On Redis miss or temporary failure, use the database source of truth and repopulate Redis when valid. | Yes |
| Redis required for refresh/logout | Refresh/logout fail when Redis is unavailable. | |
| DB-only first, Docker Redis prepared | Configure Redis but do not put it in the refresh-token validation path yet. | |

**User's choice:** Soft cache, database fallback.
**Notes:** Redis outages should be handled as degraded cache behavior. Database state remains authoritative for refresh-token validity and revocation.

---

## the agent's Discretion

- API prefix and endpoint naming were not discussed beyond existing code context.
- Registration default-role policy was not discussed.
- Auth error response format was not discussed.
- Naming/refactor cleanup boundary was not discussed.

## Deferred Ideas

- Google/OAuth login.
- Password reset and email verification.
- Full role/permission admin UI.
