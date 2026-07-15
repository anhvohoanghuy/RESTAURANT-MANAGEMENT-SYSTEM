# Roadmap: Restaurant Management System

## Milestones

- ✅ **v1.0 MVP (Backend)** — Phases 01–18 (shipped 2026-07-15) → full detail: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- 🚧 **v1.1 Admin UI** — Phase 19 (Vue.js admin interface, in progress)

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

### 🚧 v1.1 Admin UI (In Progress)

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

## Progress

| Milestone | Phases | Plans | Status | Shipped |
| --------- | ------ | ----- | ------ | ------- |
| v1.0 MVP (Backend) | 01–18 | 50/50 | ✅ Complete | 2026-07-15 |
| v1.1 Admin UI | 19 | 3/3 | ✅ Complete | 2026-07-15 |


## Backlog

### Phase 999.1: Payment history filters (status, method, date range) (BACKLOG)

**Goal:** Add `status`, `method`, `dateFrom`, and `dateTo` filters to `GET /admin/payments` (Payment Context `listPayments`). Deferred from Phase 11 per CONTEXT decision D-33 — currently only `orderId`/`orderUserId` filters plus cursor pagination exist. Surfaced during retroactive verification of Phase 11.
**Requirements:** TBD
**Plans:** 0 plans

Plans:

- [ ] TBD (promote with /gsd:review-backlog when ready)

### Phase 15: Kafka event consumers — order-confirmation saga

**Goal:** Add Kafka consumer infrastructure and an order-confirmation saga: an order is created in `PENDING_CONFIRMATION`, Inventory consumes the `OrderCreated` event, verifies ingredient availability (`available = on_hand − reserved`) and **reserves** stock if sufficient (never negative) or rejects, then publishes a result event that Order Context consumes to move the order to `CONFIRMED` or `REJECTED`. Idempotent (processed-events ledger, eventId) with `DefaultErrorHandler` + Dead Letter Topic. Actual stock deduction (reserved → on_hand) is deferred to Phase 16; the `payments.events` consumer is out of scope.
**Requirements**: Driven by locked decisions D-01..D-11 (no REQUIREMENTS.md IDs mapped)
**Depends on:** Phase 14
**Plans:** 6/6 plans complete

Plans:

- [x] 15-01-PLAN.md — Order lifecycle (PENDING_CONFIRMATION) + shared OrderStockResultEvent contract + saga config + serde test (D-01/D-08/D-10)
- [x] 15-02-PLAN.md — Inventory reservation persistence: reserved column + lock query, StockReservationEntity, processed-events ledger (D-02/D-03/D-09)
- [x] 15-03-PLAN.md — InventoryReservationService: requirement resolution, availability check, reserve/reject, after-commit publish (D-02/D-03/D-06/D-09/D-10/D-11)
- [x] 15-04-PLAN.md — Inventory Kafka wiring: producer + consumer config (ErrorHandlingDeserializer/DLT) + listener (D-04/D-05/D-10)
- [x] 15-05-PLAN.md — OrderConfirmationService: idempotent status-guarded transition + order-context ledger (D-03/D-10/D-11)
- [x] 15-06-PLAN.md — Order Kafka consumer config + listener, full saga suite green (D-04/D-05/D-10)

### Phase 16: Inventory reservation settlement

**Goal:** Add an Inventory settlement consumer that converts a held reservation into an **actual** stock deduction (`reserved` → `on_hand` decreases, never negative) when it receives a settle-trigger event carrying `(orderId, orderLineId, totalLines)`. Inventory re-resolves each order line's recipe to per-ingredient base quantities (reuse the Phase 15 resolution path), deducts under a pessimistic lock with a non-negative clamp, records a CONSUMPTION audit movement, marks the reservation `SETTLED` when the last line settles, and is idempotent (eventId ledger + per-`(orderId, orderLineId)` guard, WR-01 `REQUIRES_NEW` isolation) with a DLT on a missing reservation. **Pure inventory concern — does NOT create the trigger and does NOT touch order status.** The producer of the settle-trigger event is the Phase 17 kitchen context; until then the consumer is exercised via unit/slice tests.
**Requirements**: Driven by 16-CONTEXT.md decisions (D-03/D-04/D-05 from the original discussion) — no formal REQ IDs
**Depends on:** Phase 15
**Plans:** 5/5 plans complete

Plans:
**Wave 1**

- [x] 16-01-PLAN.md — Foundation: settle-trigger event, CONSUMPTION/SETTLED enums, line-settlement entity/repo, lockByOrderId (wave 1)
- [x] 16-02-PLAN.md — Cross-context OrderLineLookup port/snapshot + order-side adapter/repository (wave 1)
- [x] 16-03-PLAN.md — Extract shared RecipeRequirementResolver from InventoryReservationService (wave 1)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 16-04-PLAN.md — Settlement service + REQUIRES_NEW ledger writer: re-resolve, subtract+clamp, CONSUMPTION movement, SETTLED, idempotency (wave 2)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 16-05-PLAN.md — Kafka boundary: consumer config, settle-trigger/DLT topics, thin listener, serde round-trip (wave 3)

### Phase 17: Kitchen context

**Goal:** Introduce a new `kitchen_context` bounded context that owns fulfillment. A `KitchenTicket` aggregate is created when the context consumes `OrderConfirmed`; it holds a per-item fulfillment lifecycle (**preparing → ready → served → completed**). A staff endpoint under `/admin/orders/**` (ADMIN/STAFF) advances an item's status; on the preparing transition the context publishes the settle-trigger event `(orderId, orderLineId, totalLines)` that Phase 16 consumes to deduct stock. Order status reflects fulfillment (e.g. `CONFIRMED → PREPARING`) **via event**, not by kitchen mutating the Order aggregate. This keeps order-taking (order_context), fulfillment (kitchen_context), and stock (inventory_context) as clean, separate boundaries.
**Requirements**: Driven by CONTEXT decisions [D-01, D-02, D-03, D-04, D-05] (no formal REQ IDs — see 17-CONTEXT.md)
**Depends on:** Phase 16
**Plans:** 7/7 plans complete

Plans:

**Wave 1**

- [x] 17-01-PLAN.md — order_context: publish OrderConfirmed after commit + extend OrderStatus (D-01, D-04)
- [x] 17-02-PLAN.md — kitchen_context foundation: lifecycle enum, JPA aggregate, lockable/dual-key repositories (D-02)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 17-03-PLAN.md — kitchen OrderConfirmed consumer: topic config + idempotent ticket creation (D-01)
- [x] 17-04-PLAN.md — kitchen outbound producers: SettleTrigger (imported) + ticket-status-changed event (D-03, D-04)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 17-05-PLAN.md — kitchen advance service: locked forward-only transition + exactly-once settle-trigger (D-02, D-03)
- [x] 17-07-PLAN.md — order_context projection: derive order status from kitchen snapshot, forward-only + idempotent (D-04)

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 17-06-PLAN.md — staff REST endpoints: PATCH advance + GET kitchen board under /admin/orders/** (D-05)

### Phase 17.2: Outbox durability + messaging cleanup: resolve remaining non-blocking 17.1 review findings (WR-01 outbox wire-format round-trip + integration test, WR-02 relay per-row transaction, WR-03 delete dead publishers, IN-02 outbox retention + FAILED alerting, IN-03 unify reason-length constant, WR-05 explicit fulfillment rank map, WR-06 externalize DB credentials) (INSERTED)

**Goal:** Resolve the remaining non-blocking robustness/hygiene findings deferred from Phase 17.1's review — durable-outbox wire-format round-trip coverage (WR-01), relay per-row transactions to bound duplicate-publish blast radius (WR-02), deletion of dead direct-publish adapters (WR-03), outbox retention + FAILED-row surfacing (IN-02), a unified rejection-reason cap constant (IN-03), an explicit ordinal-free kitchen fulfillment rank map (WR-05), and externalized DB credentials (WR-06). No new features, no new dependencies; the 207-test suite stays green and Phase 17.1's 21 verified truths (incl. the CR-01 transactional-ledger fix) must not regress.
**Requirements**: [WR-01, WR-02, WR-03, IN-02, IN-03, WR-05, WR-06]
**Depends on:** Phase 17
**Plans:** 6/6 plans complete

Plans:

**Wave 1** *(all six plans touch disjoint files — fully parallel)*
- [x] 17.2-01-PLAN.md — WR-01 outbox wire-format round-trip test + mapper alignment (Instant/BigDecimal)
- [x] 17.2-02-PLAN.md — WR-02 relay per-row transaction (OutboxRowPublisher) + sibling-isolation test
- [x] 17.2-03-PLAN.md — IN-02 outbox retention job + FAILED-row surfacing + tests
- [x] 17.2-04-PLAN.md — WR-03 delete dead order/inventory publishers, ports, and producer configs
- [x] 17.2-05-PLAN.md — IN-03 unified rejection-reason constant + WR-05 explicit kitchen rank map
- [x] 17.2-06-PLAN.md — WR-06 externalize DB credentials to env placeholders

### Phase 17.1: kitchen-hardening — Fix Phase 17 review findings: WR-01 add whenComplete callback + error logging to kitchen Kafka publishers so failed sends aren't silently lost; WR-02 persist actorId + timestamp on item advance for audit trail; WR-03 make KitchenStatusProjectionService fail-closed on unknown fulfillment rank; IN-01/02 use existsByOrderId to absorb same-order OrderConfirmed under new eventId instead of DLT (INSERTED)

**Goal:** Close the outstanding robustness/quality debt from the Phase 15 and Phase 17 code reviews across the Kitchen and Inventory/Order bounded contexts — durable messaging (whenComplete + a full transactional outbox for the three saga events), advance audit trail, fail-closed status projection, REQUIRES_NEW ledger idempotency, rejection_reason overflow fix, and the global Jackson-3 serializer switch. No new features, no new dependencies; existing 156-test suite stays green.
**Requirements**: [K-WR-01, K-WR-02, K-WR-03, K-IN-01, K-IN-02, I-WR-01, I-WR-02, I-WR-03, I-WR-04, I-WR-05]
**Depends on:** Phase 17
**Plans:** 7/7 plans complete

Plans:

**Wave 1**
- [x] 17.1-01-PLAN.md — whenComplete send-failure logging on all four kitchen/saga Kafka publishers (K-WR-01, I-WR-03)
- [x] 17.1-02-PLAN.md — kitchen advance audit + same-order dedup + REQUIRES_NEW kitchen ledger writer (K-WR-02, K-IN-01/02, I-WR-01)
- [x] 17.1-03-PLAN.md — order projection fail-closed rank guard + REQUIRES_NEW OrderLedgerWriter (K-WR-03, I-WR-01)
- [x] 17.1-04-PLAN.md — global Jackson-3 value-serializer switch + full-suite regression (I-WR-05)
- [x] 17.1-05-PLAN.md — transactional outbox foundation: entity, SKIP LOCKED repo, in-tx writer, scheduled relay + crash-recovery test (I-WR-02)

**Wave 2** *(blocked on Wave 1)*
- [x] 17.1-06-PLAN.md — order-side saga outbox cutover + OrderLedgerWriter adoption + rejection_reason TEXT/truncation (I-WR-02, I-WR-01, I-WR-04)
- [x] 17.1-07-PLAN.md — inventory-side saga outbox cutover + InventoryLedgerWriter adoption (I-WR-02, I-WR-01)

### Phase 18: Order and order-item cancellation with compensation (release inventory reservation + auto refund)

**Goal:** Add a cancellation capability for both a whole order and individual order items, with cross-context compensation. An order (or item) may be cancelled ONLY before the kitchen starts — while the order is `SUBMITTED`, `PENDING_CONFIRMATION`, or `CONFIRMED` (never once `PREPARING`+); partial cancel is limited to items not yet `PREPARING`. Both a customer (their OWN order, early states, ownership-checked) and staff/ADMIN (any order within the window) can cancel. Cancelling adds a terminal `CANCELLED` order status (and per-item cancel), releases any held Inventory reservation (`reserved → available`) for the cancelled scope, recomputes the order total on partial cancel, and — for a paid order — automatically triggers a Payment refund for the amount already paid via the existing transactional-outbox / idempotent-consumer event pattern (no synchronous cross-context call). The Maven suite stays green; no new dependencies.
**Requirements**: Cancel window guard (SUBMITTED/PENDING_CONFIRMATION/CONFIRMED only); customer-own + staff/ADMIN authorization; whole-order cancel endpoint; partial item-cancel endpoint (non-PREPARING items only) with total recompute; Inventory reservation release on cancel (idempotent); automatic Payment refund on cancel of a paid order (event-driven); CANCELLED terminal status + state-machine/idempotency guards.
**Depends on:** Phase 11 (payment-checkout / refund), Phase 16 (inventory-reservation-settlement), Phase 17 (kitchen status — defines the PREPARING boundary)
**Plans:** 6/6 plans complete

Plans:

**Wave 1**
- [x] 18-01-PLAN.md — Foundation: append CANCELLED status + terminal guards, cancel error codes, OrderLineEntity.cancelledAt, OrderRepository.lockById, OrderCancelledEvent contract + topic (CANCEL-07)

**Wave 2** *(all depend on 18-01; disjoint contexts, fully parallel)*
- [x] 18-02-PLAN.md — order_context cancellation core: KitchenItemStatusPort/adapter + OrderCancellationService (window guard, ownership, race-safe kitchen read, partial recompute, outbox publish) (CANCEL-01/02/04)
- [x] 18-03-PLAN.md — inventory reservation release consumer: inverse-of-settlement service, release ledger/enums, listener + DLT config (CANCEL-05)
- [x] 18-04-PLAN.md — payment auto-refund consumer: Payment's first ledger + consumer, whole-order-gated refund reusing recordRefund (CANCEL-06)

**Wave 3** *(depend on 18-02)*
- [x] 18-05-PLAN.md — REST cancel endpoints (customer + admin) + authorization integration test (CANCEL-02/03/04)
- [x] 18-06-PLAN.md — kitchen ticket void consumer: append CANCELLED status, guarded idempotent void, advance guard, listener + DLT config (CANCEL-08 / D-7)

### Phase 19: VueJS admin management interface

**Goal:** Add a VueJS admin management interface that lets ADMIN/STAFF users sign in, keep the JWT session, and operate the existing backend admin surfaces from one work-focused dashboard: menu catalog, dining tables/table operations, inventory/costing/stock, payment history/refunds, kitchen board, and order cancellation/status workflows. The frontend should live as a separate Vite/Vue app in this repository, consume the Spring Boot API through a typed API client, and avoid backend behavior changes except for clearly identified API gaps.
**Requirements**: [ADMIN-UI-001, ADMIN-UI-002, ADMIN-UI-003, ADMIN-UI-004, ADMIN-UI-005, ADMIN-UI-006]
**Depends on:** Phase 18
**Plans:** 0 plans

**Success Criteria** (what must be TRUE):

  1. ADMIN/STAFF users can log in through the Vue app, persist access/refresh tokens safely in client state/storage, refresh or recover from auth failures, and log out.
  2. The admin dashboard exposes dense, scannable navigation for menu, table operations, inventory/stock, payments, kitchen board, and orders without a marketing/landing page.
  3. Menu, table, inventory, payment, kitchen, and cancellation workflows call the existing backend endpoints with shared error/loading/empty states and role-aware affordances.
  4. The app uses Vue 3 + Vite with a maintainable component/layout structure, typed API boundary, and environment-configured API base URL.
  5. The implementation includes frontend verification for routing/auth guards/API-client behavior plus a documented manual smoke path against the Spring Boot backend.
  6. Backend API gaps discovered while wiring the UI are documented as follow-up items instead of silently mocked as if complete.

Plans:
- [x] TBD (run /gsd-plan-phase 19 to break down) (completed 2026-07-15)
