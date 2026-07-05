---
phase: 06-auth-hardening
created: 2026-07-05
status: accepted
---

# Phase 06 Discussion Log

## Decision Summary

The phase hardens the existing auth implementation with operational safeguards around the already-built local auth, Google OAuth, refresh token, logout, email verification, and password reset flows.

## Accepted Plan

- Rate limit auth-sensitive endpoints using Redis TTL counters.
- Lock local accounts after repeated failed login attempts using Redis TTL locks.
- Persist audit events in DB for security-sensitive auth outcomes.
- Store refresh-token session metadata in DB.
- Expose user-owned session listing and revocation endpoints.

## Deferred Items

- MFA/TOTP.
- SMTP/provider delivery.
- Domain allowlists.
- Admin session management.
