# Roadmap: feat1

## Overview

The backend first delivered a Restaurant Menu Context for sellable catalog management. The next milestone work stabilizes the existing auth and identity code into a minimal local Auth Context so admin/user protected backend operations have a tested security foundation.

## Phases

- [x] **Phase 01: menu-context** - Add restaurant menu catalog CRUD and public read API. Completed: 2026-06-10
- [x] **Phase 02: auth-context-mvp** - Stabilize local registration, login, JWT access, refresh-token lifecycle, logout revocation, and role-protected route access. (completed 2026-07-05)
- [x] **Phase 03: google-oauth-2-login** - Add Google OAuth 2 ID-token login that issues the existing backend token pair. (completed 2026-07-04)
- [x] **Phase 04: email-verification-password-reset** - Add backend token APIs for email verification and local password reset without SMTP/provider integration. Completed: 2026-07-04
- [x] **Phase 06: auth-hardening** - Add rate limits, local login lockout, auth audit logging, and self-service refresh-session management. Completed: 2026-07-05
- [x] **Phase 07: menu-order-validation** - Add service-only menu selection validation and price snapshot quotes for future Order/Cart flows. Completed: 2026-07-05
- [x] **Phase 08: table-context** - Add dining area/table catalog, active public table listing, table validation snapshot service, and minimal dev seed data. (completed 2026-07-05)
- [x] **Phase 09: order-cart-mvp** - Add authenticated user cart in Order Context using Menu/Table validation ports and stored display snapshots. (completed 2026-07-05)
- [x] **Phase 10: order-submission-mvp** - Submit authenticated carts into orders that persist table/line snapshots and publish an order-created Kafka event. (completed 2026-07-05)
- [x] **Phase 11: payment-checkout** - Add a Payment Context for manual partial payments, refunds, QR payment request placeholders, order payment summaries, and payment events. (completed 2026-07-06)
- [x] **Phase 12: table-operations** - Add Table Sessions, occupancy tracking, and reservations for operational table management. Completed: 2026-07-06
- [x] **Phase 13: inventory-costing** - Add ingredient master data, ingredient costs, recipe cost calculation, and menu margin reads. Completed: 2026-07-06
- [x] **Phase 14: inventory-management** - Add stock-on-hand, inventory movements, and operational stock management APIs. (completed 2026-07-07)
- [x] **Phase 15: kafka-event-consumers — order-confirmation saga** - Order created in PENDING_CONFIRMATION; Inventory reserves stock (never negative) or rejects; result event moves the order to CONFIRMED/REJECTED. Idempotent, DLT, Jackson-3 serde. Completed: 2026-07-07
- [x] **Phase 16: inventory-reservation-settlement** - Inventory settlement consumer converts a held reservation into an actual stock deduction (reserved → on_hand, non-negative) when it receives a settle-trigger event. Pure inventory concern; does not touch order status. (completed 2026-07-08)
- [x] **Phase 17: kitchen-context** - New kitchen bounded context: a KitchenTicket aggregate derived from a confirmed order with a full per-item fulfillment lifecycle (preparing → ready → served → completed) that publishes the settle-trigger event Phase 16 consumes; order status reflects fulfillment via event. (completed 2026-07-08)

## Phase Details

### Phase 01: menu-context

**Goal**: Add a backend Restaurant Menu Context that models categories, dishes, topping groups, topping options, and recipes; provides admin CRUD for catalog management; and exposes a public menu tree containing only active sellable data.
**Depends on**: Nothing (first phase)
**Requirements**: [MENU-001, MENU-002, MENU-003, MENU-004, MENU-005]
**Success Criteria** (what must be TRUE):

  1. Admin users can manage categories, dishes, topping groups, topping options, and recipes under `/admin/menu/**`.
  2. Public clients can call `GET /menus/public` to retrieve the active catalog.
  3. Public responses are category -> dish -> topping group -> topping option trees and exclude inactive or archived sellable data.
  4. Recipes can be stored for dishes and topping options, but recipes are not exposed by the public menu response.
  5. Focused tests cover lifecycle filtering, topping selection validation, recipe line validation, and public response shape.

**Plans**: 1 plan

Plans:

- [x] 01-01: Implement Restaurant Menu Context vertical slice

### Phase 02: auth-context-mvp

**Goal**: Turn the existing auth and identity scaffolding into a tested local authentication MVP with registration, login, JWT access tokens, refresh-token persistence, logout revocation, and role-based protection for existing backend routes.
**Depends on**: Phase 1
**Requirements**: [AUTH-001, AUTH-002, AUTH-003, AUTH-004, AUTH-005, AUTH-006, AUTH-007, AUTH-008, AUTH-009, AUTH-010]
**Success Criteria** (what must be TRUE):

  1. Public users can register locally through a controller endpoint that creates user, credential, and default role records atomically.
  2. Local login validates username/password and returns a usable access token plus refresh token without exposing password hashes.
  3. Refresh-token validation checks JWT validity, token type, stored token state, and expiry metadata before issuing a new token pair or access token according to the chosen policy.
  4. Logout revokes the submitted refresh token and reuse fails with a consistent auth error response.
  5. Authenticated users can call a self/profile endpoint and protected routes enforce `ADMIN`/`USER` access through Spring Security.
  6. Focused tests cover registration, login, refresh, logout, JWT filter behavior, and protected route authorization.

**Plans**: 1 plan

Plans:

- [x] 02-01: Implement Auth Context MVP vertical slice

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 01. menu-context | 1/1 | Complete | 2026-06-10 |
| 02. auth-context-mvp | 1/1 | Complete    | 2026-07-05 |
| 03. google-oauth-2-login | 1/1 | Complete | 2026-07-04 |
| 04. email-verification-password-reset | 1/1 | Complete | 2026-07-04 |
| 06. auth-hardening | 1/1 | Complete | 2026-07-05 |
| 07. menu-order-validation | 1/1 | Complete | 2026-07-05 |
| 08. table-context | 1/1 | Complete    | 2026-07-05 |
| 09. order-cart-mvp | 1/1 | Complete    | 2026-07-05 |
| 10. order-submission-mvp | 1/1 | Complete    | 2026-07-05 |
| 11. payment-checkout | 1/1 | Complete | 2026-07-06 |
| 12. table-operations | 1/1 | Complete | 2026-07-06 |
| 13. inventory-costing | 1/1 | Complete | 2026-07-06 |
| 14. inventory-management | 1/1 | Complete   | 2026-07-07 |
| 15. kafka-event-consumers — order-confirmation saga | 6/6 | Complete | 2026-07-07 |
| 16. inventory-reservation-settlement | 5/5 | Complete   | 2026-07-08 |
| 17. kitchen-context | 7/7 | Complete    | 2026-07-08 |

### Phase 03: Google OAuth 2 login

**Goal:** Add Google OAuth 2 login through backend verification of Google ID tokens, preserving the existing internal JWT access/refresh-token lifecycle and local auth behavior.
**Requirements**: [AUTH-011]
**Depends on:** Phase 2
**Success Criteria** (what must be TRUE):

  1. Public clients can call `POST /auth/google` with `{ "idToken": "..." }`.
  2. The backend verifies Google ID token signature, issuer, expiry, configured audience, subject, email, and email verification before issuing internal tokens.
  3. Existing Google credentials log in the mapped user without storing Google access or refresh tokens.
  4. New verified Google users are auto-registered with `USER`.
  5. Existing local users are auto-linked only when Google is authoritative for the email (`gmail.com` or `hd` claim present).
  6. Focused tests cover create, login, auto-link, rejected non-authoritative link, controller mapping, and HTTP integration.

**Plans:** 1/1 plans complete

Plans:

- [x] 03-01: Implement Google OAuth 2 ID-token login

### Phase 04: email-verification-password-reset

**Goal:** Add backend-only email verification and password reset token flows that persist verification state, reset local credentials safely, and expose notification ports for future email delivery integration.
**Requirements**: [AUTH-012, AUTH-013, AUTH-014]
**Depends on:** Phase 2, Phase 3
**Success Criteria** (what must be TRUE):

  1. Public clients can request email verification and password reset without receiving raw tokens in API responses.
  2. Verification and reset tokens are stored as hashes, expire, and are single-use.
  3. Email verification marks users as verified while allowing unverified users to keep logging in.
  4. Password reset only changes local credentials and revokes active refresh tokens after success.
  5. Google-created or Google-linked verified emails mark the user email as verified.
  6. Focused unit, controller, and integration tests cover token issue, consume, expiry, verification, reset, and notification-port behavior.

**Plans:** 1/1 plans complete

Plans:

- [x] 04-01: Implement email verification and password reset token APIs

### Phase 06: auth-hardening

**Goal:** Harden the auth surface with Redis-backed rate limiting and local account lockout, persistent audit events, refresh-session metadata, and self-service session revocation APIs.
**Requirements**: [AUTH-015, AUTH-016, AUTH-017, AUTH-018]
**Depends on:** Phase 2, Phase 3, Phase 4
**Success Criteria** (what must be TRUE):

  1. Local login, Google OAuth, and recovery endpoints enforce the configured rate-limit buckets and return `RATE_LIMIT_EXCEEDED` when exceeded.
  2. Local accounts are locked for 15 minutes after 5 failed login attempts and return `ACCOUNT_LOCKED` while locked.
  3. Auth-sensitive events are persisted with type, outcome, user id when available, principal, IP, user agent, reason, and timestamp.
  4. Refresh-token records store session metadata and update last-used information during refresh.
  5. Authenticated users can list active sessions, revoke one owned session, and revoke all other sessions without exposing raw refresh-token values.
  6. Focused unit and integration tests cover limiter/lockout behavior, audit persistence, session ownership, and endpoint contracts.

**Plans:** 1/1 plans complete

Plans:

- [x] 06-01: Implement auth hardening

### Phase 07: menu-order-validation

**Goal:** Add a Menu Context application/domain service that validates order-bound dish selections from `dishId + List<toppingOptionId>` and returns immutable price snapshot quotes for future Order/Cart flows, without exposing a new public HTTP API in this phase.
**Requirements**: [MENU-006, MENU-007]
**Depends on:** Phase 1
**Success Criteria** (what must be TRUE):

  1. A service accepts `dishId` plus a list of selected topping option IDs and performs no HTTP/controller work in this phase.
  2. The service verifies the category and dish are active/orderable.
  3. The service verifies every selected topping option exists, is active, and belongs to a topping group under the selected dish.
  4. The service enforces every topping group's `minSelections` and `maxSelections`.
  5. Valid selections return a quote containing dish snapshot, selected topping snapshots, base price, additional topping price, and total price.
  6. Invalid selections throw stable menu domain errors such as `MENU_DISH_NOT_ORDERABLE`, `MENU_TOPPING_NOT_ORDERABLE`, `MENU_TOPPING_NOT_IN_DISH`, `MENU_TOPPING_GROUP_REQUIRED`, and `MENU_TOPPING_GROUP_LIMIT_EXCEEDED`.
  7. Focused tests cover valid quote calculation and each validation failure category.

**Plans:** 1/1 plans complete

Plans:

- [x] 07-01: Implement service-only menu order validation and quote snapshot

### Phase 08: table-context

**Goal:** Add a Table Context catalog for dining areas and dining tables with admin CRUD/archive, public active table listing, minimal dev seed data, and a service-only table validator for future Order Context flows.
**Requirements**: [TABLE-001, TABLE-002, TABLE-003, TABLE-004, TABLE-005, TABLE-006, TABLE-007]
**Depends on:** Auth/security baseline
**Success Criteria** (what must be TRUE):

  1. The backend models `DiningArea` and `DiningTable` as a Table Context catalog, without table sessions, occupancy, reservations, or branch/restaurant scoping in this phase.
  2. Admin users can create, update, list, fetch, and archive dining areas and dining tables under `/admin/tables/**`.
  3. Public clients can call `GET /tables/public` and receive an area -> active tables tree that excludes inactive or archived areas/tables.
  4. Dining table `code` is stable and unique for QR/display usage; UUID remains the internal identifier.
  5. Capacity is optional, but any provided capacity must be positive.
  6. A service-only validator returns a stable table snapshot for future Order Context and fails with `TABLE_NOT_ORDERABLE` or `TABLE_AREA_NOT_ORDERABLE`.
  7. Minimal dev/test seed data can create sample areas and tables safely without becoming required production fixture data.

**Plans:** 1/1 plans complete

Plans:

- [x] 08-01: Implement Table Context catalog, public listing, validator, and dev seed

### Phase 09: order-cart-mvp

**Goal:** Add an authenticated user cart inside Order Context that stores immutable menu/table snapshots, uses Menu and Table Context validation through ports, and exposes cart item management APIs without checkout/payment.
**Requirements**: [ORDER-001, ORDER-002, ORDER-003, ORDER-004, ORDER-005, ORDER-006, ORDER-007]
**Depends on:** Phase 7, Phase 8, Auth/security baseline
**Success Criteria** (what must be TRUE):

  1. Order Context owns cart persistence and does not place cart/order logic inside Menu or Table Context.
  2. Authenticated users have one active cart, scoped by user id, and cannot access another user's cart.
  3. Cart APIs exist: `GET /cart`, `POST /cart/items`, `PATCH /cart/items/{lineId}`, `DELETE /cart/items/{lineId}`, and `DELETE /cart`.
  4. Adding an item validates dish/toppings through a `MenuQuotePort` adapter and table through a `TableValidationPort` adapter.
  5. Line items merge by `dishId + sorted toppingOptionIds`; quantity must be a positive integer and line removal is separate from quantity update.
  6. Cart reads return stored display snapshots and totals without re-quoting Menu Context on read.
  7. Focused tests cover owner scoping, merge behavior, quantity validation, item removal/clear, menu/table adapter calls, and API contracts.

**Plans:** 1/1 plans complete

Plans:

- [x] 09-01: Implement authenticated Order Context cart MVP

### Phase 10: order-submission-mvp

**Goal:** Add submitted order persistence and APIs in Order Context so an authenticated user can turn their active cart into an order that preserves table, dish, topping, quantity, and total snapshots, then emits an order-created Kafka event for future consumers.
**Requirements**: [ORDER-008, ORDER-009, ORDER-010, ORDER-011, ORDER-012, ORDER-013, ORDER-014, ORDER-015]
**Depends on:** Phase 9
**Success Criteria** (what must be TRUE):

  1. Order Context introduces submitted order persistence separate from active cart persistence.
  2. `POST /orders` submits the authenticated user's active cart into an order.
  3. Submitted orders persist table snapshot fields: `tableId`, `tableCode`, `tableName`, `areaId`, and `areaName`.
  4. Submitted orders persist immutable line snapshots from the cart, including dish, toppings, unit price, quantity, and line total.
  5. Submitting an empty cart or cart without table fails with stable order error codes.
  6. Successful submission clears the active cart so the user can start a new table/order flow.
  7. Authenticated users can read only their own orders through order read APIs.
  8. A successful order submission publishes an `OrderCreated` Kafka event after the order is persisted, with a stable payload for future consumers.

**Plans:** 1/1 plans complete

Plans:

- [x] 10-01: Implement order submission from cart with table snapshot

### Phase 11: payment-checkout

**Goal:** Add a Payment Context so staff/admin can record manual partial payments and refunds for submitted orders, users can create QR payment request placeholders for future providers, order reads can show payment summaries, and payment events are published for future consumers.
**Requirements**: [PAY-001, PAY-002, PAY-003, PAY-004, PAY-005, PAY-006, PAY-007, PAY-008, PAY-009, PAY-010, PAY-011, PAY-012, PAY-013, PAY-014]
**Depends on:** Phase 10
**Success Criteria** (what must be TRUE):

  1. Payment logic lives in a separate Payment Context and reads orders through ports instead of owning order data.
  2. `STAFF` and `ADMIN` can record manual payments for submitted orders; ordinary users cannot confirm payments.
  3. Manual payments support `CASH`, `BANK_TRANSFER`, and `QR_CODE`, use VND amounts, and require idempotency keys.
  4. Orders support partial payment accounting with `UNPAID`, `PARTIALLY_PAID`, and `PAID` payment status.
  5. Refunds are persisted in a separate `payment_refunds` model/table, attach to a payment record, require idempotency keys, and expose refund summary separately from payment status.
  6. Confirmed payments cannot overpay the submitted order total, and refunds cannot exceed the payment amount.
  7. Users can create QR payment request placeholders that return a future provider payment/redirect URL shape, without real provider integration or auto-confirmation.
  8. Order read APIs enrich responses with payment summary via a port to Payment Context.
  9. Admin/staff can view order-scoped payment/refund history and global payment history with cursor pagination and basic filters.
  10. Successful payment/refund operations publish `PaymentRecorded`, `PaymentRefunded`, and `OrderPaymentCompleted` Kafka events after commit, without adding consumers.

**Plans:** 1/1 plans complete

Plans:

- [x] 11-01: Implement Payment Checkout Context

### Phase 12: table-operations

**Goal:** Add operational table management on top of the existing Table Context catalog: staff/admin can open and close table sessions, track occupancy state, create and manage reservations, and expose availability without moving order ownership into Table Context.
**Requirements**: [TABLE-008, TABLE-009, TABLE-010, TABLE-011, TABLE-012, TABLE-013, TABLE-014, TABLE-015, TABLE-016]
**Depends on:** Phase 08, Phase 09, Phase 10
**Success Criteria** (what must be TRUE):

  1. Table Context owns operational table sessions separately from the static dining area/table catalog.
  2. Staff/admin can open one active session per table and close/cancel it with stable state transitions.
  3. Table occupancy is derived or maintained as `AVAILABLE`, `OCCUPIED`, `RESERVED`, `CLEANING`, or `OUT_OF_SERVICE`.
  4. Reservations can be created, confirmed, seated, cancelled, no-showed, and completed.
  5. Reservation time windows cannot overlap for the same table in active reservation states.
  6. Order/Cart can optionally reference a table session through a port without moving order logic into Table Context.
  7. Public/staff availability reads expose table availability by time window and party size.
  8. Table operation events are published after commit for future consumers, without adding consumers in this phase.

**Plans:** 1/1 plans complete

Plans:

- [x] 12-01: Implement Table Operations

### Phase 13: inventory-costing

**Goal:** Add a backend Inventory Context for ingredient master data and ingredient costs, link menu recipes to inventory ingredients, and expose admin/staff recipe/menu costing reads without changing public menu, order, payment, or stock behavior.
**Requirements**: [INV-001, INV-002, INV-003, INV-004, INV-005, INV-006, INV-007, INV-008, INV-009, INV-010, INV-011]
**Depends on:** Phase 01, Phase 07, Phase 09, Phase 10, Phase 11
**Success Criteria** (what must be TRUE):

  1. Inventory Context owns ingredient master data and ingredient cost records separately from Menu Context.
  2. Admin/staff can create, update, archive, list, and cost ingredients under `/admin/inventory/**`.
  3. Recipe lines can optionally reference an inventory ingredient while preserving existing free-text recipe behavior.
  4. Recipe cost calculation uses ingredient costs and unit conversion to produce line and total estimated cost.
  5. Missing ingredient links, missing costs, and unsupported unit conversion are surfaced explicitly.
  6. Admin/staff can inspect menu item cost and gross margin while public menu responses remain unchanged.
  7. Costing does not deduct stock, change sell prices, alter order totals, or affect payment behavior in this phase.

**Plans:** 1/1 plans complete

Plans:

- [x] 13-01: Implement Inventory Costing Foundation

### Phase 14: inventory-management

**Goal:** Extend Inventory Context from costing into operational stock management with stock-on-hand balances, inventory movement records, manual receipts/adjustments/waste, and low-stock visibility while keeping automatic order deduction as an explicit later integration decision.
**Requirements**: [INV-012, INV-013, INV-014, INV-015, INV-016, INV-017, INV-018, INV-019, INV-020, INV-021]
**Depends on:** Phase 13
**Success Criteria** (what must be TRUE):

  1. Inventory Context persists stock balances per ingredient for a default stock location.
  2. Staff/admin can record inventory movements for receipts, adjustments, waste, and corrections.
  3. Movement records are immutable audit facts with quantity, unit, reason, reference, actor metadata where available, and timestamp.
  4. Stock-on-hand reads use movement/balance data and expose current quantity per ingredient.
  5. Outbound movements cannot drive stock negative unless an explicit adjustment path is used.
  6. Ingredients can define low-stock thresholds and staff/admin can list low-stock ingredients.
  7. Public menu, order submission, payment, and recipe costing contracts remain backward compatible.
  8. Focused tests cover stock movement validation, balance updates, unit conversion, low-stock reads, and authorization.

**Plans:** 1/1 plans complete

Plans:

- [x] 14-01: Implement Inventory Stock Management Foundation

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
**Plans:** 6 plans

Plans:

**Wave 1** *(all six plans touch disjoint files — fully parallel)*
- [ ] 17.2-01-PLAN.md — WR-01 outbox wire-format round-trip test + mapper alignment (Instant/BigDecimal)
- [ ] 17.2-02-PLAN.md — WR-02 relay per-row transaction (OutboxRowPublisher) + sibling-isolation test
- [ ] 17.2-03-PLAN.md — IN-02 outbox retention job + FAILED-row surfacing + tests
- [ ] 17.2-04-PLAN.md — WR-03 delete dead order/inventory publishers, ports, and producer configs
- [ ] 17.2-05-PLAN.md — IN-03 unified rejection-reason constant + WR-05 explicit kitchen rank map
- [ ] 17.2-06-PLAN.md — WR-06 externalize DB credentials to env placeholders

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
