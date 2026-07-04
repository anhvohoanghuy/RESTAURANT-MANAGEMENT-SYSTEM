# Phase 04: email-verification-password-reset - Discussion Log

> Decisions are captured in CONTEXT.md. This log preserves alternatives considered.

**Date:** 2026-07-04
**Phase:** 04-email-verification-password-reset

## Phase Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Email verify/reset | Add email verification and password reset after auth MVP. | Yes |
| Role admin API | Add role/permission management APIs. | |
| Auth hardening | Add rate limit, lockout, audit, and device/session management. | |

## Delivery Mode

| Option | Description | Selected |
|--------|-------------|----------|
| Token API only | Backend token APIs plus notification port/fake; no SMTP provider. | Yes |
| SMTP dev | Add SMTP and local MailHog/Docker. | |
| Provider ready | Add provider implementation such as SendGrid/Mailgun. | |

## Verification Rule

| Option | Description | Selected |
|--------|-------------|----------|
| Allow but mark | Store verification state without blocking login/use. | Yes |
| Block local login | Reject local login until verified. | |
| Block protected use | Allow login but block protected APIs until verified. | |

## Token Exposure

| Option | Description | Selected |
|--------|-------------|----------|
| Port only | Public APIs never return raw tokens; tests capture via notification port. | Yes |
| Return in dev | Return tokens in dev/test only. | |
| Always return token | Return token in public API response. | |
