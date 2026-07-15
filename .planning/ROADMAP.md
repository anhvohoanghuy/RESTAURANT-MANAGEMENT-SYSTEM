# Roadmap: Restaurant Management System

## Milestones

- ✅ **v1.0 MVP (Backend)** — Phases 01–18 (shipped 2026-07-15) → full detail: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- ✅ **v1.1 Admin UI** — Phase 19 (Vue.js admin interface, shipped 2026-07-15)
- 📋 **v1.2 Admin UI Completion** — Phase 20 (fill Group-A UI gaps, planned)

## Phases

<details>
<summary>✅ v1.0 MVP — Backend (Phases 01–18, 50 plans) — SHIPPED 2026-07-15</summary>

- [x] Phase 01: menu-context — menu catalog CRUD + public read API — 2026-06-10
- [x] Phase 02: auth-context-mvp — local register/login, JWT access+refresh, logout, role guards — 2026-07-05
- [x] Phase 03: google-oauth-2-login — Google ID-token login issuing the backend token pair — 2026-07-04
- [x] Phase 04: email-verification-password-reset — backend token APIs (no SMTP) — 2026-07-04
- [x] Phase 06: auth-hardening — rate limits, lockout, audit log, session management — 2026-07-05
- [x] Phase 07: menu-order-validation — service-only validation + price-snapshot quotes — 2026-07-05
- [x] Phase 08: table-context — table catalog, public listing, validation snapshots, dev seed — 2026-07-05
- [x] Phase 09: order-cart-mvp — authenticated Order Context cart with stored snapshots — 2026-07-05
- [x] Phase 10: order-submission-mvp — submit carts to orders + order-created Kafka event — 2026-07-05
- [x] Phase 11: payment-checkout — partial payments, refunds, QR placeholders, summaries, events — 2026-07-06
- [x] Phase 12: table-operations — sessions, occupancy, reservations, availability — 2026-07-06
- [x] Phase 13: inventory-costing — ingredients, cost records, recipe costs, menu margins — 2026-07-06
- [x] Phase 14: inventory-management — stock balances, immutable movements, low-stock reads — 2026-07-07
- [x] Phase 15: kafka order-confirmation saga — reserve stock (never negative) → CONFIRMED/REJECTED; idempotent + DLT — 2026-07-07
- [x] Phase 16: inventory-reservation-settlement — settle held reservation (reserved → on_hand) on trigger — 2026-07-08
- [x] Phase 17: kitchen-context — KitchenTicket per-item fulfillment lifecycle + settle-trigger publish — 2026-07-08
- [x] Phase 17.1: kitchen-hardening (INSERTED) — send-failure logging, advance audit trail, fail-closed projection — 2026-07-09
- [x] Phase 17.2: outbox durability + messaging cleanup (INSERTED) — per-row relay, retention, dead-publisher deletion, env credentials — 2026-07-10
- [x] Phase 18: order & order-item cancellation with compensation — CANCELLED status, inventory release, auto Payment refund, kitchen void — 2026-07-15

</details>

### ✅ v1.1 Admin UI (Complete)

### Phase 19: VueJS admin management interface

**Goal:** A Vue 3 + Vite admin app (separate front-end in this repo) letting ADMIN/STAFF sign in (JWT session), and operate existing backend admin surfaces from one dashboard: menu, tables/table-ops, inventory/costing/stock, payments/refunds, kitchen board, and order cancellation/status. Consumes the Spring Boot API via a typed client; no backend behavior changes except clearly-identified API gaps.
**Requirements:** [ADMIN-UI-001..006]
**Depends on:** Phase 18
**Plans:** 3/3 plans complete

Plans:
- [x] 19-01 — (complete)
- [x] 19-02
- [x] 19-03

> **Reconcile in v1.1 planning:** two orphan hardening phase-directories exist on disk with their *code already merged* into the shipped backend but were never added to this roadmap and collide in numbering with v1.0's 17.2:
> - `17.2-inventory-settlement-idempotency-hardening` (atomic settlement ledger write) — re-number (e.g. 17.4)
> - `17.3-payment-table-kafka-jackson3-serializer-hardening` (payment/table Jackson-3 serde) — keep/verify 17.3

### 📋 v1.2 Admin UI Completion (Planned)

### Phase 20: Complete admin UI

**Goal:** Fill the Group-A UI gaps from the Phase 19 coverage audit (backend endpoints already exist): edit (PUT) forms across Menu (category/dish), Tables (area/table), and Inventory (ingredient); recipe authoring (`PUT /admin/menu/recipes`) + topping group/option management screens (bindings exist, no view); ingredient cost history (`listCosts`) + `recipes/cost` and menu costing reads; auth session management (`GET/DELETE /auth/sessions`, revoke-others); full reservation status transitions (CONFIRMED/NO_SHOW/COMPLETED, not cancel-only); and role-aware affordances (gate ADMIN-only controls for STAFF — subsumes backlog 999.2). Frontend-only; document remaining backend list-endpoint gaps (orders/reservations/admin-menu listing, payment filters 999.1) as follow-ups rather than mocking.
**Requirements:** TBD
**Depends on:** Phase 19
**Plans:** 0 plans

Plans:
- [ ] TBD (run /gsd:plan-phase 20 to break down)

## Progress

| Milestone | Phases | Plans | Status | Shipped |
| --------- | ------ | ----- | ------ | ------- |
| v1.0 MVP (Backend) | 01–18 | 50/50 | ✅ Complete | 2026-07-15 |
| v1.1 Admin UI | 19 | 3/3 | ✅ Complete | 2026-07-15 |
| v1.2 Admin UI Completion | 20 | 0/? | 📋 Planned | — |

## Backlog

### Phase 999.1: Payment history filters (status, method, date range) (BACKLOG)

**Goal:** Add `status`, `method`, `dateFrom`, and `dateTo` filters to `GET /admin/payments` (Payment Context `listPayments`). Deferred from Phase 11 per CONTEXT decision D-33 — currently only `orderId`/`orderUserId` filters plus cursor pagination exist.
**Requirements:** TBD
**Plans:** 0 plans

### Phase 999.2: Role-aware UI affordances in admin-ui (BACKLOG)

**Goal:** Decode/store the authenticated user's role on login and gate ADMIN-only controls in the Vue admin app so STAFF users don't see controls that only 403 reactively. Backend enforces `/admin/**` correctly — frontend UX gap from Phase 19 verification (non-blocking). NOTE: folded into Phase 20 scope; keep here only if Phase 20 drops it.
**Requirements:** TBD
**Plans:** 0 plans

### Phase 999.3: Table reservation with pre-order (đặt bàn giữ chỗ + gọi món trước) (BACKLOG)

**Goal:** Let a customer reserve a table for a future time slot AND attach food items to order in advance (pre-order), so the kitchen/floor can prepare against a booking. Currently reservation (`TableReservationEntity`, Phase 12) holds only table snapshot + customer contact + party size + time window + status, with NO linkage to orders; Order/Cart takes `tableId`/`tableSessionId` but never `reservationId`; and there is no pre-order concept anywhere. Requires BACKEND work (persist pre-order line items against a reservation, rules for holding/confirming/converting the pre-order into a real order when the reservation is seated, interaction with inventory reservation + kitchen) plus FRONTEND. New feature — not covered by Phase 19/20 (which only wire existing admin endpoints).
**Requirements:** TBD
**Plans:** 0 plans
