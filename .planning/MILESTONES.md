# Milestones

## v1.0 MVP (Shipped: 2026-07-15)

**Phases completed:** 19 phases, 50 plans, 76 tasks (+ 1 backlog item deferred to v1.1)
**Timeline:** 2026-04-24 → 2026-07-15 · **311 commits** · ~25,700 LOC Java across 322 files
**Final test suite:** 257 tests green, 0 failures.

A DDD, Spring Boot 4 / Kafka restaurant-management backend spanning eight bounded contexts (auth/identity, menu, table, order, payment, inventory, kitchen, plus a shared transactional outbox).

**Key accomplishments:**

- **Auth & Identity** — Local registration/login with JWT access + refresh-token lifecycle, logout revocation, and role-protected routes; Google OAuth 2 ID-token login reusing the backend token pair; backend email-verification & password-reset token APIs; and hardening (Redis-backed rate limiting, login lockout, audit logging, self-service session management).
- **Menu & Ordering** — Restaurant Menu catalog CRUD + public active-menu API; service-only menu-order validation with price-snapshot quotes; an authenticated Order Context cart MVP; and order submission persisting table/line snapshots with an order-created event.
- **Tables** — Dining-area/table catalog with public active listing and validation snapshots, plus Table Operations (sessions, occupancy, reservations, availability, order-session linkage) and events.
- **Payments** — Checkout with manual partial payments, refunds, QR payment-request placeholders, per-order payment summaries, and payment events.
- **Inventory** — Costing (ingredient master data, cost records, recipe cost calculation, menu margins) and Management (stock-on-hand balances, immutable movements, atomic non-negative balance updates, low-stock reads, admin/staff APIs).
- **Event-driven saga & compensation (Kafka)** — Order-confirmation reservation saga (PENDING_CONFIRMATION → CONFIRMED/REJECTED under pessimistic lock), inventory settlement on kitchen prepare (reserved → on_hand), a new Kitchen Context owning a per-item fulfillment lifecycle, a durable transactional outbox with retention, and full order/order-item cancellation with cross-context compensation (inventory reservation release + automatic event-driven Payment refund + kitchen void), all idempotent with DefaultErrorHandler + DLT.

**Engineering discipline:** every phase closed with a goal-backward verifier + code review; Phase 18's review caught and fixed 3 cross-file blocker bugs (projection NPE, over-broad whole-order cancel refund, movement-type misclassification) before ship, each with a regression test.

**Known deferred items (acknowledged at close — non-blocking):**
- Backlog **Phase 999.1** — Payment history filters (`status`/`method`/`dateFrom`/`dateTo` on `GET /admin/payments`), deferred from Phase 11 (D-33).
- **17.2-VERIFICATION.md** minor gap (multi-instance outbox duplicate-publish) — already resolved doc-only in quick task `260710-eqh`; single-instance topology unaffected, downstream idempotency ledgers bound the impact.
- Two completed quick tasks (`260710-e78`, `260710-eqh`) lack status frontmatter — cosmetic only (both landed with commits `0825bc2` / `ab09ab1`).

---
