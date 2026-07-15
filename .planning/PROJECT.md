# Project: Restaurant Management System

## What This Is

A DDD, Spring Boot 4 backend for restaurant operations across eight bounded contexts (auth/identity, menu, table, order, payment, inventory, kitchen, and a shared transactional outbox), with cross-context workflows event-driven over Kafka using idempotent consumers + DLT. Shipped **v1.0 (backend MVP)** on 2026-07-15. A **Vue.js admin UI (Phase 19)** is the in-progress v1.1 front-end layer over these backend admin surfaces.

## Core Value

A correct, race-safe order lifecycle: take an order, confirm it against real inventory, prepare it in the kitchen, pay, and cancel — with each bounded context independent and every cross-context effect flowing through idempotent, at-least-once events rather than synchronous coupling.

## Current State

**Shipped v1.0 backend MVP** — 18 backend phases, 50 plans, ~26k LOC Java, 257 tests green.
**v1.1 in progress** — Phase 19 Vue.js admin interface (1/3 plans complete).

Tech stack: Java + Spring Boot 4, Jackson 3, Spring Kafka (transactional outbox + DLT), JPA/Hibernate with pessimistic locking, Redis (auth rate limiting/lockout), JWT + Google OAuth 2, OpenAPI/Swagger. Front end (v1.1): Vue.js admin.

## Requirements

### Validated (v1.0)

- ✓ Local auth (register/login/JWT/refresh/logout/role guards) — v1.0
- ✓ Google OAuth 2 login — v1.0
- ✓ Email verification + password reset (backend tokens) — v1.0
- ✓ Auth hardening (rate limit, lockout, audit, sessions) — v1.0
- ✓ Menu catalog + public API + order validation/quoting — v1.0
- ✓ Table catalog + operations — v1.0
- ✓ Order cart, submission, confirmation saga, cancellation w/ compensation — v1.0
- ✓ Payment checkout, refunds, summaries, auto-refund on cancel — v1.0
- ✓ Inventory costing + management + reservation/settlement/release — v1.0
- ✓ Kitchen fulfillment lifecycle + cross-context events — v1.0
- ✓ Transactional outbox durability + idempotent consumers + DLT — v1.0

### Active (v1.1)

- [ ] Phase 19 — Vue.js admin management interface (auth/session, dashboard, menu, tables, inventory, payments/refunds, kitchen board, order cancellation flows)
- [ ] Reconcile orphan hardening phase-docs (17.2 inventory-settlement idempotency, 17.3 payment/table Jackson-3) into the roadmap with non-colliding numbers — code already merged
- [ ] Backlog 999.1 — payment history filters on `GET /admin/payments`
- [ ] Durable multi-instance outbox claim (intermediate CLAIMING status) — if multi-instance deployment is planned

### Out of Scope (v1.0)

- SMTP/email provider integration (email-verify/reset are backend-token only)
- Real QR payment provider integration (placeholders only)

## Key Decisions

- Event-driven cross-context saga over Kafka with idempotent ledgers + DLT (not synchronous calls) — ✓ Good
- Transactional outbox for durable event publish — ✓ Good
- Reservation model: `available = on_hand − reserved`, never negative; settle on kitchen prepare; release on cancel — ✓ Good
- Cancellation window = only before kitchen starts; terminal CANCELLED status; automatic event-driven refund — ✓ Good
- Jackson 3 native serdes across all producers/consumers — ✓ Good
- Single-instance outbox relay topology for v1.0 (multi-instance dedup deferred) — ⚠️ Revisit if scaling out
- Split v1.0 at backend (01–18); Vue admin UI (Phase 19) is v1.1 — ✓ Good

## Constraints

- No new dependencies added casually.
- Full Maven suite must stay green at every phase close.
- Bounded contexts must not reach into each other's aggregates directly — only via ports/events.

---
*Last updated: 2026-07-15 after v1.0 milestone*
