# Project: Restaurant Management System (backend)

## What This Is

A DDD, Spring Boot 4 backend for restaurant operations, organized into eight bounded contexts (auth/identity, menu, table, order, payment, inventory, kitchen, and a shared transactional outbox). Cross-context workflows are event-driven over Kafka with idempotent consumers and dead-letter handling. Shipped v1.0 (MVP) on 2026-07-15.

## Core Value

A correct, race-safe order lifecycle: an order can be taken, confirmed against real inventory, prepared in the kitchen, paid, and cancelled — with each bounded context staying independent and every cross-context effect flowing through idempotent, at-least-once events rather than synchronous coupling.

## Current State

**Shipped v1.0 MVP** — 19 phases, 50 plans, ~25,700 LOC Java (322 files), 257 tests green.

Tech stack: Java + Spring Boot 4, Jackson 3, Spring Kafka (transactional outbox + DLT), JPA/Hibernate with pessimistic locking, Redis (auth rate limiting/lockout), JWT auth + Google OAuth 2.

Bounded contexts and their state:
- **auth / identity** — local + Google login, JWT access/refresh, email-verify & password-reset tokens, hardening (rate limit, lockout, audit, sessions). ✓
- **menu** — catalog CRUD + public API, order-validation & price-snapshot quoting. ✓
- **table** — catalog + operations (sessions, occupancy, reservations, availability). ✓
- **order** — cart MVP, submission with snapshots, confirmation saga, fulfillment projection, whole/partial cancellation. ✓
- **payment** — checkout, partial payments, refunds, QR placeholders, summaries; first Kafka consumer for auto-refund. ✓
- **inventory** — costing (ingredients/recipes/margins) + management (balances, movements, low-stock), reservation/settlement/release. ✓
- **kitchen** — per-item fulfillment lifecycle, settle-trigger publish, cancel-void consumer. ✓
- **shared/outbox** — durable transactional outbox with per-row publish + retention. ✓

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

### Active (next milestone candidates)

- [ ] Payment history filters (`status`/`method`/`dateFrom`/`dateTo` on `GET /admin/payments`) — backlog 999.1
- [ ] Durable multi-instance outbox claim (intermediate CLAIMING status) — if multi-instance deployment is planned
- [ ] Traceability: keep REQUIREMENTS.md checkboxes in sync at phase close

### Out of Scope (v1.0)

- SMTP/email provider integration (email-verify/reset are backend-token only)
- Real QR payment provider integration (placeholders only)
- Frontend/UI (backend-only project)

## Key Decisions

- Event-driven cross-context saga over Kafka with idempotent ledgers + DLT (not synchronous calls) — ✓ Good
- Transactional outbox for durable event publish (crash-safe between DB commit and Kafka send) — ✓ Good
- Reservation model: `available = on_hand − reserved`, never negative; settle on kitchen prepare; release on cancel — ✓ Good
- Cancellation window = only before kitchen starts (SUBMITTED/PENDING_CONFIRMATION/CONFIRMED); terminal CANCELLED status; automatic event-driven refund — ✓ Good
- Jackson 3 native serdes across all producers/consumers (Boot 4 classpath) — ✓ Good
- Single-instance outbox relay topology for v1.0 (multi-instance dedup deferred) — ⚠️ Revisit if scaling out

## Constraints

- No new dependencies added casually — phases explicitly kept the dependency set stable.
- Full Maven suite must stay green at every phase close.
- Bounded contexts must not reach into each other's aggregates directly — only via ports/events.

---
*Last updated: 2026-07-15 after v1.0 milestone*
