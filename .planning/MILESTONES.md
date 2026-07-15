# Milestones

## v1.0 MVP — Backend (Shipped: 2026-07-15)

**Scope:** Phases 01–18 (backend). Phase 19 (Vue.js admin UI) carried forward to v1.1.
**Phases completed:** 18 backend phases, 50 plans, 76 tasks.
**Timeline:** 2026-04-24 → 2026-07-15 · ~26k LOC Java.
**Backend test suite:** green (257 tests at Phase 18 close).

A DDD, Spring Boot 4 / Kafka restaurant-management **backend** spanning eight bounded contexts (auth/identity, menu, table, order, payment, inventory, kitchen, plus a shared transactional outbox).

**Key accomplishments:**

- **Auth & Identity** — Local register/login with JWT access + refresh lifecycle, logout revocation, role-protected routes; Google OAuth 2 login; email-verification & password-reset token APIs; hardening (Redis rate limiting, lockout, audit log, session management).
- **Menu & Ordering** — Menu catalog CRUD + public API; service-only order validation with price-snapshot quotes; authenticated cart MVP; order submission with table/line snapshots + order-created event.
- **Tables** — Table catalog + operations (sessions, occupancy, reservations, availability, order-session linkage) and events.
- **Payments** — Checkout with partial payments, refunds, QR placeholders, order summaries, and payment events; Payment's first Kafka consumer (auto-refund on cancel).
- **Inventory** — Costing (ingredients, recipe costs, margins) + Management (balances, immutable movements, non-negative guards, low-stock reads).
- **Event-driven saga & compensation (Kafka)** — Order-confirmation reservation saga, inventory settlement on kitchen prepare, a Kitchen Context with per-item fulfillment lifecycle, a durable transactional outbox (per-row relay + retention), and full order/order-item cancellation with cross-context compensation (inventory release + auto refund + kitchen void) — all idempotent with DefaultErrorHandler + DLT.

**Engineering discipline:** every phase closed with a goal-backward verifier + code review; Phase 18's review caught and fixed 3 cross-file blocker bugs before ship, each with a regression test.

**Known deferred items at close: 3** (see STATE.md → Deferred Items)
- Backlog **999.1** — Payment history filters on `GET /admin/payments` (from Phase 11 D-33).
- **17.2-VERIFICATION.md** minor gap (multi-instance outbox duplicate-publish) — already resolved doc-only in quick task `260710-eqh`; single-instance topology unaffected.
- Two completed quick tasks (`260710-e78`, `260710-eqh`) lack status frontmatter — cosmetic.

**Carried to v1.1:**
- **Phase 19 — Vue.js admin management interface** (in progress: 1/3 plans; 19-01 done, 19-02/03 pending).
- **Orphan hardening phase-docs to reconcile:** the collaborator's `17.2-inventory-settlement-idempotency-hardening` and `17.3-payment-table-kafka-jackson3-serializer-hardening` directories exist on disk with their **code already merged** into the shipped backend (e.g. `InventoryLedgerWriter`, payment/table Jackson-3 serde, OpenAPI/Swagger), but were never added to ROADMAP.md and collide in numbering with this milestone's 17.2 (outbox durability). Re-number and fold into the roadmap during v1.1 planning.

---
