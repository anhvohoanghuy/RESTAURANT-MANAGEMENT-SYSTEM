# Architecture

## Overview

A restaurant management system: menu catalog, dining tables and reservations, ordering,
kitchen tickets, inventory with recipe-driven stock reservation, and payments.

It is a Spring Boot modular monolith organised into DDD bounded contexts, plus a separate
Vue admin SPA. Contexts share one database and one deployable, but integrate with each
other mostly through Kafka events rather than direct calls — the code is written as if the
contexts might one day be split apart.

Two things dominate the design and are worth understanding before anything else:

1. **A transactional outbox** guarantees that a state change and its event are committed
   together (§ Transactional Outbox).
2. **Consumer-side idempotency ledgers** absorb the duplicate deliveries that at-least-once
   messaging inevitably produces (§ Idempotency and Reliability).

Everything else is ordinary layering.

## Stack

| Concern | Choice |
|---|---|
| Backend | Spring Boot 4.0.6, Java 17, Maven |
| Database | MySQL, `spring.jpa.hibernate.ddl-auto=update` — **no migration tool** |
| Messaging | Kafka (`spring-kafka`), Jackson 3 JSON payloads |
| Cache | Redis (Lettuce) — refresh tokens, login lockout, rate limiting |
| Auth | JWT (jjwt 0.12.5), BCrypt, Google OAuth2 |
| API docs | springdoc-openapi 3.0.3 → `/swagger-ui.html` |
| Admin UI | Vue 3.5, vue-router 5, Vite 8, Vitest 4, TypeScript — no Pinia |
| Format | Spotless + google-java-format, applied at `validate` |

There is no Dockerfile or compose file; MySQL, Redis and Kafka are expected to be running
locally.

## System Shape

```text
admin-ui/  (Vue SPA)  ──HTTP/JSON──▶  Spring Boot app  ──▶ MySQL
                                            │              Redis
                                            └──────────▶  Kafka ─┐
                                            ▲                    │
                                            └────────────────────┘
                                          (contexts talk to each other
                                           over Kafka, in one process)
```

## Package Layout

```text
src/main/java/com/example/feat1/
  Feat1Application.java
  common/exception/            GlobalExceptionHandler
  config/                      RedisConfig, OpenApiConfig, SwaggerAutoLauncher
  DDD/
    auth/                      login, JWT, sessions, recovery, rate limiting
    identity_context/          users, credentials, roles, permissions
    menu_context/              categories, dishes, toppings, recipes
    table_context/             areas, tables, occupancy, sessions, reservations
    order_context/             cart, orders, cancellation, kitchen-status projection
    inventory_context/         ingredients, stock, reservations, costing
    kitchen_context/           kitchen tickets and their status machine
    payment_context/           payments, QR requests, refunds
    shared/outbox/             transactional outbox plumbing
```

Layering inside a context is `application/` → `domain/` → `infrastructure/`, with HTTP
controllers nested under `infrastructure/presentation/`.

The layering is not uniform, and the deviations are load-bearing enough to know about:

- `identity_context` spells it **`infastructure`**. So does its `rolePermision` class. Both
  are original typos preserved by inertia, not variants with meaning.
- `auth`, `order_context`, `kitchen_context` and `payment_context` have **no domain
  aggregates** — their `domain/` holds enums, ports and snapshots, and the JPA entities act
  as de-facto aggregates with the behaviour living in `application/` services.
- `menu_context` and `identity_context` are the only contexts with real domain models.
- `table_context` splits `domain/repository` (persistence) from `domain/port` (integration);
  other contexts do not make that distinction.

## Bounded Contexts

| Context | Owns | HTTP surface |
|---|---|---|
| `auth` | Login (local + Google), JWT issue/rotate, sessions, email verification, password reset, lockout, audit | `AuthController` @ `/auth` |
| `identity_context` | Users, credentials, roles, permissions, registration use case | `UserController` @ `/users` |
| `menu_context` | Categories, dishes, topping groups/options, recipes (BOM) | `AdminMenuController` @ `/admin/menu`, `PublicMenuController` @ `/menus` |
| `table_context` | Dining areas, tables, occupancy, table sessions, reservations | `AdminTableController` @ `/admin/tables`, `PublicTableController` @ `/tables`, `TableOperationController` |
| `order_context` | Cart lifecycle, order submission/confirmation/cancellation, kitchen-status read model | `CartController` @ `/cart`, `OrderController` @ `/orders`, `AdminOrderCancellationController` |
| `inventory_context` | Ingredients, stock balances/movements, reservation→release→settlement, recipe costing | `InventoryController`, `InventoryStockController` |
| `kitchen_context` | Kitchen tickets, status machine, board view | `KitchenController` |
| `payment_context` | Payments, QR payment requests, refunds, auto-refund | `PaymentController` |
| `shared/outbox` | Outbox entity, writer, relay, retention | none |

`order_context` is the hub: it publishes the events that drive inventory and kitchen, and
consumes their results back.

## Context Integration Map

### Synchronous — in-process ports and adapters

Used where a decision must be made inside an open transaction and an eventually-consistent
projection would be wrong.

| Caller | Port | Resolves to |
|---|---|---|
| order | `MenuQuotePort` | menu — price/validate lines at submission |
| order | `TableValidationPort` | table — validate the table session |
| order | `KitchenItemStatusPort` | kitchen — read live item status while cancelling |
| inventory | `OrderLineLookupPort` | order — re-resolve a line during settlement |
| menu | `MenuRecipeCostingPort` | menu implements inventory's costing port |
| payment | `OrderPaymentLookupPort` | order |
| table | `TableSessionValidationPort` | table implements order's port |

`KitchenItemStatusPort` is the important one: cancellation reads kitchen status
*synchronously, inside the lock*, because a stale projection could refund food already being
cooked.

### Asynchronous — Kafka

| Producer | Topic | Consumers | Via outbox? |
|---|---|---|---|
| order | `orders.created` | inventory | yes |
| order | `orders.confirmed` | kitchen | yes |
| order | `orders.cancelled` | inventory, kitchen, payment | yes |
| inventory | `inventory.order-stock-results` | order | yes |
| kitchen | `kitchen.ticket-status-changed` | order | **no** — direct |
| kitchen | `kitchen.settlement-trigger` | inventory | **no** — direct |
| payment | `payments.events` | *(none)* | no — direct |
| table | `tables.events` | *(none)* | no — direct |

Topic names come from config keys (`order.events.*`, `inventory.events.*`,
`payment.events.topic`, `table.events.topic`, …), most with inline `@Value` defaults.

Every consumer is a thin delegate to an application service. Consumer configuration is
uniform and deliberately hardened: `ErrorHandlingDeserializer`, auto-commit off,
`AckMode.RECORD` (offset commits only after the handler's transaction commits),
`FixedBackOff(1s, 3)` then `DeadLetterPublishingRecoverer` to `<topic>.DLT`, and
deserialization failures routed straight to the DLT. Type headers are disabled and a
`VALUE_DEFAULT_TYPE` + trusted-packages allow-list is forced, so a hostile `__TypeId__`
header cannot instantiate arbitrary classes.

## Transactional Outbox

`DDD/shared/outbox/` exists to close the **dual-write gap**: contexts used to publish in an
`afterCommit` hook, so a crash between commit and send dropped the event silently.

```text
business tx ──┬── mutate state
              └── OutboxWriter.save(...)      @Transactional(MANDATORY)
                      ↓ ONE COMMIT
       outbox_events row, status=PENDING
                      ↓
  OutboxRelay.poll()  @Scheduled(fixedDelay = outbox.relay.delay-ms, default 1s)
       claimPending(100)  ──▶  OutboxRowPublisher.publish(row)   @Transactional per row
                                  send → await broker ack → mark SENT
                                  failure → attempts++, stays PENDING
                                  attempts = 10 → FAILED
```

Design points that are easy to break if you don't know them:

- `OutboxWriter` is `MANDATORY` on purpose — writing an event outside a transaction fails
  fast rather than reintroducing the dual-write gap.
- `OutboxRelay.poll()` is **deliberately not `@Transactional`**, and delegates to an
  *injected* `OutboxRowPublisher` (never self-invocation) so each row commits independently.
- Publishing is **send-then-mark**: the broker ack is awaited inside the row's transaction
  before it flips to `SENT`.
- The payload is stored JSON, republished with a plain `StringSerializer`. This is
  byte-identical to a direct typed send only because consumers disable type headers. Do not
  re-enable type headers without revisiting the relay.

`claimPending` uses `FOR UPDATE SKIP LOCKED` (MySQL 8+; it must never run against H2, hence
the test-profile gate). **SKIP LOCKED does not make this multi-instance safe** — since
`poll()` is not transactional, a claimed row's lock releases when the method returns and the
row stays `PENDING` until its own publish transaction. The topology is single-instance
today, and if a second instance is ever added, what bounds duplicate publishes is
at-least-once + the consumer ledgers, *not* this query.

## Core Sagas

### Order → reservation → confirm/reject

```text
submit  [tx] save order PENDING_CONFIRMATION + outbox(OrderCreatedEvent)
        → orders.created
inventory.onOrderCreated  [tx]
        ledger pre-check → resolve recipe requirements → lock balances in
        ascending-UUID order (deadlock avoidance) → sufficient? reserve : shortfalls
        → outbox(OrderStockResultEvent) → recordProcessed LAST
        → inventory.order-stock-results
order.onStockResult  [tx]
        ledger pre-check → guard status == PENDING_CONFIRMATION
        CONFIRMED → status CONFIRMED + outbox(OrderConfirmedEvent) → orders.confirmed
        REJECTED  → status REJECTED + rejectionReason   ← TERMINAL, no retry, no cart restore
kitchen.onOrderConfirmed → build ticket + all items in one pass from the event manifest
```

Rejection is terminal by design (D-11). Kitchen builds every item in one pass because its
item-count invariants depend on items never being appended later.

### Kitchen advance → settlement

`QUEUED → PREPARING → READY → SERVED → COMPLETED`; `CANCELLED` and `COMPLETED` are terminal.
The row is locked *before* the status check, which closes a double-settle-trigger race.

Only the `QUEUED → PREPARING` transition emits `SettleTriggerEvent`. Settlement decrements
both `reservedQuantity` and `quantityOnHand`, writes `CONSUMPTION` movements, and flips the
reservation to `SETTLED` only once `settled + released >= totalLines` — so a mixed
settled/released order cannot strand a permanently-`HELD` reservation.

### Cancellation → three-way fan-out

One `OrderCancelledEvent` fans out to three independent consumer groups: inventory releases
the reservation, kitchen voids the ticket, payment auto-refunds.

Two guards matter: an IDOR pre-check (`findByIdAndUserId`) runs *before* locking, and on the
whole-order path, if any active line already reached `PREPARING` the cancel is rejected
outright with no mutation and no publish (CR-02) — rather than refunding food already in
preparation.

## Idempotency and Reliability

Delivery is at-least-once end to end; duplicates are expected and absorbed, not prevented.

Each context has its own ledger table — `order_processed_events`,
`inventory_processed_events`, `kitchen_processed_events`, `payment_processed_events` — each
with a unique constraint on `(event_id, consumer_name)`.

The pattern, used by 7 of 8 handlers:

1. Cheap pre-check `existsByEventIdAndConsumerName(...)` → return on replay.
2. Do the business work.
3. `recordProcessed(eventId)` **last, in the same transaction**.

Recording last is the point (CR-01/WR-01). A concurrent duplicate's unique violation rolls
back the *whole* transaction, Kafka redelivers, and the pre-check absorbs the replay. The
earlier `REQUIRES_NEW` pre-commit marker could mark an event processed and then lose the
business work — stranding an order forever.

The one survivor of the old pattern is `InventoryReservationSettlementService`, which is safe
only because a second guard (the per-`(orderId, orderLineId)` settlement row) backs it up.

Beyond the ledgers there are business-level guards: `existsByOrderId` on reservations and
tickets, per-line settlement/release rows, and status guards.

## Security Model

`SecurityConfig` (in `auth/infrastructure/security/`) — stateless, CSRF off, BCrypt, JSON
401/403 handlers, and `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`.

Matcher order (first match wins):

| Paths | Access |
|---|---|
| `POST /auth/{register,login,google,refresh,logout,email/*,password/*}` | public |
| swagger + `/v3/api-docs/**` | public — unconditionally, see Debt |
| `/auth/sessions**` | USER, ADMIN, STAFF |
| `/cart/**`, `/orders/**` | USER, ADMIN, STAFF |
| `GET /menus/public`, `/tables/public**` | public |
| `/admin/payments/**`, `/admin/orders/**` | ADMIN, STAFF |
| `/admin/inventory/**`, `/admin/menu/recipes/cost`, `/admin/menu/costing` | ADMIN, STAFF |
| `/admin/tables/*` occupancy/sessions/reservations, `/admin/table-sessions/**` | ADMIN, STAFF |
| `/admin/**` | **ADMIN only** — backstop after the STAFF carve-outs above |
| `/users/**` | USER, ADMIN, STAFF |
| anything else | authenticated |

The ordering is deliberate: STAFF carve-outs are enumerated before the `/admin/**`
ADMIN-only catch-all.

**Roles come from the database on every request**, not from the token: the filter validates
the JWT, then `CustomUserDetailsService.loadUserById` fetches roles and `CustomUserDetails`
adds the `ROLE_` prefix. The token's `roles`/`permissions` claims are never read by the
backend — the admin UI decodes them for display only. This makes revocation immediate.

`RoleEnum` declares ADMIN, USER, MANAGER, STAFF; the seeder creates only ADMIN, USER, STAFF.

Tokens: access 15 min, refresh 7 days (`jwt.access-expiration`, `jwt.refresh-expiration`).
The filter requires token type `ACCESS`, so a refresh token cannot be used as a bearer
credential. Refresh rotation detects reuse two ways — user-id mismatch and
already-revoked — and both revoke the user's entire token family.

Redis caches refresh tokens but the **database is the source of truth**; `RedisRefreshTokenCache`
catches every Redis error and falls back to the DB.

## Persistence

Spring Data JPA over MySQL, schema managed by `ddl-auto=update` — there is no Flyway or
Liquibase, so schema history lives only in entity classes.

Concurrency control worth knowing:

- Stock balances are locked `PESSIMISTIC_WRITE` in **ascending UUID order** to avoid
  deadlocks between concurrent reservations.
- Settlement locks the reservation row first, then balances, in the same ascending order.
- Order cancellation locks the order before reading kitchen status.

## Redis

Three real consumers, all in `auth`:

| Consumer | Purpose | On Redis outage |
|---|---|---|
| `RedisRefreshTokenCache` | refresh-token lookups | falls back to DB (fails open, correct) |
| `LoginLockoutService` | failed-login lockout window | degrades permissive |
| `AuthRateLimitService` | fixed-window rate limiting | degrades permissive |

Only `RedisRefreshTokenCache` uses the custom `RedisTemplate<String, Object>` from
`RedisConfig`; the other two use Boot's auto-configured `StringRedisTemplate`.

## Admin UI

`admin-ui/` — Vue 3 SFCs with `<script setup lang="ts">`, Composition API, no state library.

```text
src/
  main.ts        restoreSession() BEFORE mount, so the router guard sees a live session
  api/           client.ts (transport), auth.ts, modules.ts (per-context APIs + types)
  stores/auth.ts reactive session singleton
  router/        route table + one beforeEach guard
  layouts/       AdminLayout — sidebar + topbar shell
  views/         10 route components
  components/    Modal, ConfirmDialog, DataTable, EmptyState, StatusBadge, GapNotice, Toolbar
  lib/           format.ts, recipe.ts (pure form→payload transform)
  style.css      all global styles, no CSS framework
```

`apiFetch` in `api/client.ts` is the whole transport: base URL from `VITE_API_BASE_URL`
(default `http://localhost:8080`), bearer injection, and a **401 → refresh → retry-once**
flow that clears the session with a user-facing message when refresh fails. Errors surface as
`ApiError { status, code? }`.

`stores/auth.ts` persists only the tokens to `localStorage`; roles are re-derived by decoding
the JWT payload (`decodeRoles`) — **no signature verification**, because it is presentational
only and the backend enforces. `isAdmin` is a computed over those roles.

Role gating has three layers: the route guard (`meta.adminOnly`, currently only the recipe
route), `v-if="isAdmin"` on mutating controls in `MenuView` and `TablesView`, and the backend
as the only real enforcement.

`modules.ts` declares a `knownGaps` map that renders missing-backend notices in the UI via
`GapNotice.vue` rather than mocking absent endpoints.

Tests (Vitest, 33 across 5 files) cover logic only — transport, store, router guards, recipe
transform. **There are no component or view tests.**

## Scheduled Jobs

`@EnableScheduling` appears exactly once, on `OutboxConfig`. All three scheduled methods live
in the outbox package:

| Job | Key | Default |
|---|---|---|
| `OutboxRelay.poll` | `outbox.relay.delay-ms` | 1s, batch 100 |
| `OutboxRetentionJob.sweepSentRetention` | `outbox.retention.delay-ms` | 1h |
| `OutboxRetentionJob.surfaceFailedRows` | `outbox.retention.failed-check-delay-ms` | 1h |

Retention (`outbox.retention.ttl-days`, default 7) cuts on **`created_at`, not `sent_at`** —
`created_at` is always populated and monotonic. It filters strictly on `status='SENT'`, so an
in-flight `PENDING` or poison `FAILED` row is never silently deleted. `surfaceFailedRows` is
log-only: there is no Micrometer/actuator on the classpath, which was an explicit choice.

None of these keys appear in `application.properties`; they rely on inline defaults. The
`enabled` flags appear only in test properties, set to `false`.

## Architectural Debt

Ordered roughly by consequence.

1. **Password reset and email verification do not reach the user.** The only
   `EmailNotificationPort` implementation is `NoOpEmailNotificationAdapter`, which just logs.
   Real tokens are minted and never delivered — so password reset is non-functional end to
   end, and reset tokens sit in application logs.
2. **`jwt.secret` has a committed default** (`change-me-…`) and fails *open*: if `JWT_SECRET`
   is unset in production the app starts happily on a public secret.
3. **Kitchen, payment and table still publish directly**, not through the outbox — they carry
   the exact dual-write gap the outbox was built to close. The sharpest case is
   `kitchen.settlement-trigger`, whose own failure log reads *"reservation may never settle"*;
   settlement is the hop that actually decrements stock.
4. **`payment_context` reaches into `order_context`'s JPA repository** directly
   (`OrderPaymentLookupAdapter` imports `order_context.infrastructure.repository.OrderRepository`),
   bypassing order's application layer. The worst coupling in the codebase.
5. **`orders.cancelled` has no `NewTopic` bean and no `.DLT` bean**, unlike every other live
   topic — its three consumers have DLT handlers pointing at a topic nothing declares. Verify
   against `auto.create.topics.enable`.
6. **auth ↔ identity_context is a circular dependency** (auth imports identity heavily;
   identity imports `RefreshTokenEntity` and `CustomUserDetails` back).
7. **`CustomUserDetails` — an infrastructure class — is a de-facto shared kernel**, imported by
   all eight business contexts. It should be a `shared` value type.
8. **URL namespaces bleed across contexts**: kitchen serves `/admin/orders/*`, payment serves
   `/orders/*` and `/admin/orders/*`, inventory serves `/admin/menu/*`.
9. **Event classes in `application/event` are the wire contract**, imported raw by consumers.
   There is no published-language module, so a producer can break a consumer at compile time.
   `SettleTriggerEvent` is the inverted case: it lives in inventory but is published by kitchen.
10. **Swagger is public unconditionally** — no profile or env guard on the springdoc matchers.
11. **`payments.events` and `tables.events` are write-only dead ends** — no consumers, no topic
    beans, and no send-failure logging at all.
12. **No schema migrations.** `ddl-auto=update` cannot express renames, backfills or
    destructive changes, and will not survive a real production change.
13. **`MANAGER` is declared in `RoleEnum` but never seeded** and appears in no matcher.
14. **`JwtProvider.extractRole` is broken** — it reads the `roles` claim as `String` while it is
    written as a `List`. It has zero callers, so it is latent, not live. Delete or fix it before
    someone wires it up.
15. **No component or view tests** in `admin-ui` — the two largest views (`TablesView` 814 lines,
    `MenuView` 660) are untested.
16. **Naming**: `TokenSerivce`, `identity_context/infastructure`, `rolePermision` are all typos
    propagated into imports; renaming needs a coordinated refactor.

---

*Verified against the codebase on 2026-07-16 (post Phase 20). Where this document states design
intent, it reflects javadoc in the source, which is unusually thorough — read
`OutboxRelay`, `OutboxRowPublisher` and `OutboxEventRepository.claimPending` before changing
any messaging code.*
