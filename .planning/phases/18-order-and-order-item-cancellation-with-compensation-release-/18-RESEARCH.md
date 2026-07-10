# Phase 18: Order & order-item cancellation with compensation - Research

**Researched:** 2026-07-10
**Domain:** Cross-context saga compensation (Order/Inventory/Payment/Kitchen) in a Spring Boot 4 / Kafka / Jackson-3 DDD monolith
**Confidence:** HIGH for existing-pattern replication (outbox, idempotent consumers, per-line settlement inversion); MEDIUM-LOW for the two areas the locked decisions leave open (Kitchen-side ticket invalidation, partial-cancel-of-a-paid-order refund scope) - flagged explicitly below.

## Summary

This phase adds compensating cancellation to a system that already runs three live Kafka sagas
(order-confirmation, kitchen-fulfillment, inventory-settlement) built on a **shared transactional
outbox** + **per-context idempotent-consumer ledger** + **DLT** pattern. There is no existing
generic guidance to apply here - everything must mirror concrete sibling code already in this
repository: `InventoryReservationSettlementService` (the exact inverse of the release operation
this phase needs), `KitchenStatusProjectionService`'s REJECTED-terminal guard (the direct template
for a CANCELLED-terminal guard), and `OrderConfirmationService`/`OrderSubmissionService` (the
outbox-write + status-transition idiom the new cancel service must follow).

Three concrete, codebase-grounded findings shape the plan more than the CONTEXT.md decisions alone
reveal:

1. **`StockReservationEntity` reservation lines are aggregated by ingredient across the WHOLE
   order, not per line.** The Phase 16 settlement service therefore never reads those aggregated
   lines to know "how much to deduct for line X" - it **re-resolves** line X's recipe fresh via
   `RecipeRequirementResolver` + `OrderLineLookupPort` at settlement time. The release path for
   this phase must do exactly the same thing: re-resolve the cancelled line(s)' recipe to know how
   much `reservedQuantity` to give back, never read `StockReservationEntity.lines` directly. This
   is the single most important pattern to replicate, and it means the whole `OrderLineLookupPort`
   + `OrderLineRecipeSnapshot` + `RecipeRequirementResolver` infrastructure is reusable **verbatim**.

2. **Kitchen fulfillment state (`KitchenTicketItemEntity.status`) lives in a separate bounded
   context/transaction from `OrderEntity.status`, connected only by an eventually-consistent Kafka
   projection** (`KitchenStatusProjectionService`). The order only shows `PREPARING` once the
   *projection* has caught up - which can lag the real kitchen-side `PREPARING` write by however
   long the Kafka round-trip takes. Consequently, **checking `OrderEntity.status` alone is not
   sufficient** to race-safely decide "is this item already preparing" for the per-item guard (D-4)
   - a **new synchronous cross-context read** into `KitchenTicketItemEntity` is required at
   cancel-time. This is detailed in Pitfall 1 below; it is the crux of "race-safely" in the phase
   brief.

3. **Payment Context has never consumed a Kafka event before.** All three existing Payment events
   (`PaymentRecorded`/`PaymentRefunded`/`OrderPaymentCompleted`) are publish-only, published via a
   raw `KafkaTemplate.send()` in an `afterCommit` callback - **not** via the transactional outbox,
   and Payment has **no idempotent processed-events ledger table at all**. Making refund
   event-driven per D-3 means building Payment's *first* inbound consumer from scratch: a new
   `payment_processed_events` ledger entity/repository, a new `@KafkaListener` + `DefaultErrorHandler`
   + DLT config (mirroring `TicketStatusChangedKafkaConsumerConfig`), and a refund-triggering method
   that iterates **every** `PaymentEntity` for the order (refunds are scoped per-payment today, not
   per-order) to reach the full "amount already paid."

**Primary recommendation:** Implement cancellation as one outbox-published `OrderCancelledEvent`
(carrying the list of cancelled `orderLineId`s and a `wholeOrder` flag) consumed independently by
three new idempotent consumers - Inventory (release), Payment (auto-refund, gated on `wholeOrder`),
and (recommended addition, see Pitfall 2) Kitchen (ticket/item invalidation) - each following the
exact idempotency, locking, and outbox idioms already proven in Phases 15-17.2. Add
`OrderStatus.CANCELLED` at the end of the enum (never inside the pinned CONFIRMED..COMPLETED
block), extend the two existing REJECTED-terminal guards to also treat CANCELLED as terminal, add
a nullable `cancelledAt` marker to `OrderLineEntity` (not a new line-status enum) for partial
cancel, and add `ReservationStatus.RELEASED` plus a new `InventoryLineReleaseEntity` ledger that
generalizes the existing "count-then-flip" idiom to `settledCount + releasedCount >= totalLines`.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Cancel-window guard (order status check) | API / Backend (order_context) | Kitchen (per-item read) | OrderEntity is the status owner; but the per-item PREPARING truth lives in kitchen_context (see Summary point 2) |
| Ownership check (customer-own) | API / Backend (order_context) | - | Mirrors `OrderRepository.findByIdAndUserId` already used by `OrderSubmissionService.getOrder` |
| Staff/ADMIN any-order authorization | API / Backend (Spring Security route matcher) | - | Mirrors existing `/admin/orders/**` `hasAnyRole("ADMIN","STAFF")` matcher |
| Whole-order cancel transition | API / Backend (order_context) | - | Owns `OrderEntity.status`; publishes outbox event |
| Partial item-cancel + total recompute | API / Backend (order_context) | - | Owns `OrderLineEntity`/`OrderEntity.total` |
| Inventory reservation release | API / Backend (inventory_context, async consumer) | - | Owns `StockReservationEntity`/`InventoryStockBalanceEntity`; mirrors `InventoryReservationSettlementService` |
| Automatic Payment refund | API / Backend (payment_context, async consumer - NEW) | - | Owns `PaymentRefundEntity`; reuses `PaymentService.recordRefund` machinery |
| Kitchen ticket/item invalidation on cancel (recommended, not locked) | API / Backend (kitchen_context, async consumer - NEW) | - | Owns `KitchenTicketItemEntity`; needed to stop staff from advancing an already-cancelled item (Pitfall 2) |
| Database / Storage schema | Database / Storage | - | `spring.jpa.hibernate.ddl-auto=update` - no manual migration scripts in this repo; new columns/tables/entities are auto-created |

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-1 Cancel window**
- An order/item may be cancelled ONLY **before the kitchen starts**: order status `SUBMITTED`,
  `PENDING_CONFIRMATION`, or `CONFIRMED`.
- Once an order/item is `PREPARING` or later (`READY`/`SERVED`/`COMPLETED`), cancel is rejected.
  This keeps compensation simple: nothing has been consumed yet, so only the held reservation must
  be released (no waste accounting, no settled-stock reversal).

**D-2 Who can cancel**
- **Both**:
  - A **customer** may cancel **their OWN** order - early states only - with an ownership check
    (mirror existing order-ownership checks in order_context).
  - **Staff / ADMIN** may cancel **any** order within the cancel window (mirror the existing
    `/admin/**` ADMIN/STAFF authorization).

**D-3 Refund on a paid order**
- **Automatic**: cancelling a paid order automatically triggers a **Payment refund** for the amount
  already paid.
- Delivered event-driven (outbox -> Payment consumer), NOT a synchronous call, mirroring the Phase
  15/16/17 saga pattern. Idempotent (processed-events ledger).

**D-4 Partial cancel (cancel a few items)**
- Only items **not yet PREPARING** can be cancelled.
- Cancelling item(s) **releases their held Inventory reservation** (`reserved -> available`) for
  exactly those lines and **recomputes the order total**.
- Items already PREPARING/settled are NOT cancellable in this phase (blocked, not force-cancelled).

### Claude's Discretion
- Exact new endpoint paths/verbs, DTO shapes, and whether whole-order cancel is modelled as
  "cancel all remaining cancellable items" vs a distinct order-level transition - planner decides,
  consistent with existing order_context conventions.
- Event names/payloads for the cancel -> inventory-release and cancel -> payment-refund messages -
  follow existing `OrderCreated`/settle-trigger naming conventions.
- Whether per-item cancel introduces an item-level `CANCELLED` state or a soft `cancelledAt` marker
  on the line - planner decides based on current OrderEntity/line model.

### Specific Ideas / Constraints
- New terminal order status `CANCELLED` on `OrderStatus` (currently: SUBMITTED,
  PENDING_CONFIRMATION, CONFIRMED, PREPARING, READY, SERVED, COMPLETED, REJECTED - no CANCELLED
  today). Respect the load-bearing forward-only fulfillment-rank ordering comment in
  `OrderStatus.java`; CANCELLED must be handled as terminal like REJECTED (see
  `KitchenStatusProjectionService` REJECTED-is-terminal guard as the analog).
- Inventory reservation release is the inverse of the Phase 16 settlement path (`reserved ->
  available`, non-negative clamp, idempotent, audit movement). Reuse the shared recipe/requirement
  resolution where applicable.
- Payment Context already has a manual refund capability (Phase 11) - reuse it; this phase only
  adds the automatic trigger.
- All new consumers idempotent via the established processed-events ledger + DLT.

### Deferred Ideas (OUT OF SCOPE)
None explicitly deferred in CONTEXT.md beyond the D-1 boundary itself (PREPARING+ items/orders are
out of scope for this phase's compensation - no waste accounting, no settled-stock reversal).
</user_constraints>

<phase_requirements>
## Phase Requirements

> Phase 18 is not part of the original v1 `REQUIREMENTS.md` (added post-v1 on 2026-07-10 per
> `STATE.md`). No formal `REQ-XX` IDs exist yet; the mnemonic IDs below are assigned here for
> traceability between this research and the plan.

| ID | Description | Research Support |
|----|-------------|------------------|
| CANCEL-01 | Cancel window guard: SUBMITTED/PENDING_CONFIRMATION/CONFIRMED only, never PREPARING+ | `OrderStatus.java` enum + forward-only rank map in `KitchenStatusProjectionService`; Pitfall 1 (race-safe per-item check needs a NEW cross-context port, not just `OrderEntity.status`) |
| CANCEL-02 | Customer-own + staff/ADMIN authorization | `SecurityConfig` `/orders/**` vs `/admin/orders/**` role matchers; `OrderRepository.findByIdAndUserId` ownership idiom |
| CANCEL-03 | Whole-order cancel endpoint | `OrderController`/`OrderSubmissionService` conventions; recommend `POST /orders/{orderId}/cancel` + `POST /admin/orders/{orderId}/cancel` |
| CANCEL-04 | Partial item-cancel endpoint (non-PREPARING items only) with total recompute | `OrderLineEntity` has no status field today - recommend nullable `cancelledAt`; total recompute = sum of non-cancelled `lineTotal` |
| CANCEL-05 | Inventory reservation release (idempotent) | `InventoryReservationSettlementService` is the exact structural inverse to copy; `StockReservationEntity.ReservationStatus` needs a new `RELEASED` value; new `InventoryLineReleaseEntity` mirrors `InventoryLineSettlementEntity` |
| CANCEL-06 | Automatic event-driven Payment refund on a paid order | `PaymentService.recordRefund` is reusable; Payment has NO existing consumer/ledger infra - must be built from scratch (Summary point 3); scope ambiguity flagged in Open Questions |
| CANCEL-07 | CANCELLED terminal status + state-machine/idempotency guards | `KitchenStatusProjectionService`'s REJECTED-terminal check (`order.getStatus() == OrderStatus.REJECTED`) is the literal line to extend; `OrderConfirmationService`'s status-guard (`order.getStatus() != PENDING_CONFIRMATION`) is the pattern for a new cancel-guard |
</phase_requirements>

## Standard Stack

No new external dependencies are needed or recommended. This phase is 100% additive Java/Spring
code inside the existing Maven module, reusing:

### Core (already in the project - no version changes needed)
| Library | Version (observed) | Purpose | Why Standard (for this phase) |
|---------|---------|---------|--------------|
| Spring Boot / Spring Data JPA | (project-pinned, Boot 4-line, Jackson 3) | Entities, repositories, `@Transactional` | Every existing context uses this; new entities (`InventoryLineReleaseEntity`, `PaymentProcessedEventEntity`) are plain `@Entity` classes, auto-DDL'd |
| Spring Kafka (`spring-kafka`) | (project-pinned) | New `@KafkaListener` consumers | Mirrors `TicketStatusChangedKafkaConsumerConfig`/`SettleTriggerKafkaConsumerConfig` exactly |
| Jackson 3 (`tools.jackson.databind.ObjectMapper`, `JacksonJsonSerializer`/`JacksonJsonDeserializer`) | (project-pinned, per Phase 17.2 migration) | Event (de)serialization | ALL producers/consumers in this codebase were migrated off Jackson-2 in quick task `260710-eqh` (2026-07-10) - any new Payment producer/consumer MUST use `JacksonJsonSerializer`/`JacksonJsonDeserializer`, never the legacy Jackson-2 `JsonSerializer`, or it will hit the exact latent bug that task fixed |
| Lombok | (project-pinned) | `@Getter`/`@Setter`/`@RequiredArgsConstructor` on new entities/services | Consistent with every existing class in this codebase |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Reusing `PaymentService.recordRefund` from a new consumer | A dedicated `PaymentAutoRefundService` that writes `PaymentRefundEntity` directly, bypassing `recordRefund`'s idempotency-key/overpay checks | Rejected: `recordRefund` already encodes the "refund cannot exceed the original payment amount" invariant (PAY-009); duplicating that logic in a new path risks drift. Call `recordRefund` per `PaymentEntity` with a deterministic idempotency key instead. |
| `cancelledAt` marker on `OrderLineEntity` | A new `OrderLineStatus` enum (`ACTIVE`/`CANCELLED`) | Both are valid (CONTEXT.md explicitly leaves this to the planner). `cancelledAt` is recommended because it (a) mirrors the existing nullable-descriptive-field idiom already used for `OrderEntity.rejectionReason`, (b) needs no new enum/transition-guard code, and (c) naturally supports "when was this cancelled" audit display. |

**Installation:** None - no `pom.xml` changes required.

**Version verification:** N/A (no new external packages). Package Legitimacy Audit is therefore
not applicable to this phase.

## Package Legitimacy Audit

**Not applicable.** This phase introduces zero new third-party dependencies. All new code is
first-party Java added to existing `order_context`, `inventory_context`, `payment_context`, and
(recommended) `kitchen_context` modules, reusing the project's existing Spring Boot / Spring Kafka
/ Jackson-3 / Lombok stack already declared in `pom.xml`.

## Architecture Patterns

### System Architecture Diagram

```
                        HTTP (customer, own order)              HTTP (staff/ADMIN, any order)
                     POST /orders/{id}/cancel                POST /admin/orders/{id}/cancel
                     POST /orders/{id}/items/{lid}/cancel     POST /admin/orders/{id}/items/{lid}/cancel
                                  |                                        |
                                  v                                        v
                     +----------------------------------------------------------+
                     |          order_context: OrderCancellationService          |
                     |  1. lock OrderEntity row (NEW: OrderRepository.lockById)  |
                     |  2. ownership check (customer path only)                 |
                     |  3. window guard: status in {SUBMITTED,PENDING_CONF,      |
                     |     CONFIRMED}                                           |
                     |  4. per-item guard (partial only): synchronous read of   |
                     |     KitchenTicketItemEntity.status via NEW cross-context |
                     |     port (order_context consumer -> kitchen_context)     |
                     |  5. mark line(s) cancelledAt / whole-order -> CANCELLED  |
                     |  6. recompute order.total from non-cancelled lines       |
                     |  7. outboxWriter.save(OrderCancelledEvent) -- SAME tx    |
                     +----------------------------------------------------------+
                                  |
                                  |  outbox_events row (PENDING) --> OutboxRelay (scheduled poll)
                                  v
                     Kafka topic: orders.cancelled  (published verbatim by OutboxRowPublisher)
                                  |
        +-------------------------+-------------------------+--------------------------+
        v                                                     v                          v
+----------------------+                          +-----------------------+   +--------------------------+
| inventory_context     |                          | payment_context (NEW  |   | kitchen_context (NEW,     |
| ReservationRelease-   |                          | consumer + ledger)    |   | recommended)              |
| Listener/Service       |                          | AutoRefundListener/   |   | TicketInvalidation-       |
|  - idempotency: ledger |                          | Service                |   | Listener/Service          |
|    + InventoryLine-    |                          |  - idempotency:        |   |  - idempotency: new       |
|    ReleaseEntity        |                          |    payment_processed_ |   |    kitchen_processed_     |
|  - re-resolve each      |                          |    events (NEW TABLE) |   |    events row             |
|    cancelled line's     |                          |  - only acts when     |   |  - for each cancelled     |
|    recipe (reuse         |                          |    event.wholeOrder   |   |    lineId still QUEUED,   |
|    RecipeRequirement-    |                          |    == true (Open Q)  |   |    remove/mark item;      |
|    Resolver +           |                          |  - iterate ALL         |   |    NEVER touch an item    |
|    OrderLineLookupPort) |                          |    PaymentEntity for   |   |    already >= PREPARING   |
|  - decrement reserved-  |                          |    the order, refund   |   |    (defense-in-depth,     |
|    Quantity ONLY (never |                          |    each unrefunded     |   |    mirrors REJECTED/      |
|    on_hand); clamp >= 0 |                          |    remainder via       |   |    rank-guard idiom)      |
|  - write RESERVATION_   |                          |    PaymentService.     |   +--------------------------+
|    RELEASE audit         |                          |    recordRefund with a |
|    movement (NEW type)  |                          |    deterministic       |
|  - flip reservation to  |                          |    idempotencyKey       |
|    RELEASED (whole) or  |                          +-----------------------+
|    leave HELD, tracking |
|    settledCount +       |
|    releasedCount vs      |
|    totalLines (partial) |
+----------------------+
```

### Recommended Project Structure (additions only)

```
src/main/java/com/example/feat1/DDD/
├── order_context/
│   ├── application/
│   │   ├── OrderCancellationService.java          # NEW - whole-order + partial cancel logic
│   │   └── event/OrderCancelledEvent.java          # NEW - outbox payload
│   ├── domain/
│   │   ├── model/OrderStatus.java                  # MODIFY - append CANCELLED
│   │   ├── model/OrderDomainException.java         # MODIFY - new error codes
│   │   └── port/KitchenItemStatusPort.java          # NEW - order_context consumes kitchen truth
│   └── infrastructure/
│       ├── entity/OrderLineEntity.java              # MODIFY - add nullable cancelledAt (+ optional cancelReason)
│       ├── repository/OrderRepository.java          # MODIFY - add lockById (PESSIMISTIC_WRITE)
│       └── presentation/OrderController.java        # MODIFY - add cancel endpoints (customer + admin variants, or a shared admin controller)
├── inventory_context/
│   ├── application/
│   │   └── InventoryReservationReleaseService.java # NEW - mirrors InventoryReservationSettlementService
│   ├── domain/model/InventoryMovementType.java      # MODIFY - add RESERVATION_RELEASE
│   └── infrastructure/
│       ├── entity/StockReservationEntity.java       # MODIFY - add ReservationStatus.RELEASED
│       ├── entity/InventoryLineReleaseEntity.java   # NEW - mirrors InventoryLineSettlementEntity
│       ├── adapter/OrderCancelledListener.java      # NEW - thin @KafkaListener delegate
│       └── config/OrderCancelledKafkaConsumerConfig.java # NEW - mirrors SettleTriggerKafkaConsumerConfig
├── payment_context/
│   ├── application/PaymentAutoRefundService.java    # NEW
│   ├── infrastructure/
│   │   ├── entity/PaymentProcessedEventEntity.java  # NEW - Payment's FIRST idempotency ledger
│   │   ├── entity/PaymentRefundEntity.java           # MODIFY - actorUserId becomes nullable (system refunds)
│   │   ├── repository/PaymentProcessedEventRepository.java # NEW
│   │   ├── adapter/OrderCancelledPaymentListener.java # NEW
│   │   └── config/OrderCancelledPaymentKafkaConsumerConfig.java # NEW
└── kitchen_context/                                  # RECOMMENDED (see Pitfall 2), not locked by CONTEXT.md
    ├── application/KitchenTicketInvalidationService.java # NEW
    └── infrastructure/adapter/OrderCancelledKitchenListener.java # NEW
```

### Pattern 1: Re-resolve, never read the aggregated reservation

**What:** `StockReservationEntity.lines` stores reserved quantity **aggregated by ingredient across
the whole order** (see `StockReservationEntity.held(orderId, reservedByIngredient)` -
`Map<UUID ingredientId, BigDecimal>`, no `orderLineId` dimension at all). There is no way to read
"how much of ingredient X came from line Y" out of that map once multiple lines share an
ingredient. `InventoryReservationSettlementService` solves this by **re-deriving** the exact
per-line requirement fresh, at settlement time, via the same recipe-resolution code the original
reservation used.

**When to use:** Any time inventory needs to act on a *subset* of an order's lines (settlement,
and now release).

**Example (existing settlement code to mirror - `InventoryReservationSettlementService.java`):**
```java
// Source: src/main/java/.../inventory_context/application/InventoryReservationSettlementService.java
OrderLineRecipeSnapshot line =
    orderLineLookupPort
        .findLine(orderId, orderLineId)
        .orElseThrow(() -> InventoryDomainException.settlementOrderLineMissing(orderId, orderLineId));
Map<UUID, BigDecimal> required = resolveLineRequirements(line); // dish recipe + each topping's recipe

StockReservationEntity reservation =
    reservationRepository.lockByOrderId(orderId)
        .orElseThrow(() -> InventoryDomainException.settlementReservationMissing(orderId));
// ... iterate required in ascending-ingredientId order, decrement reserved (+ on_hand for settlement)
```
**For release:** identical shape, but decrement `reservedQuantity` ONLY (never
`quantityOnHand`), and write a new `RESERVATION_RELEASE` movement type instead of `CONSUMPTION`.
`OrderLineLookupPort`/`OrderLineRecipeSnapshot`/`RecipeRequirementResolver` need **zero
modification** - a soft-cancelled line (`cancelledAt` set but row still present) still resolves
correctly through `OrderLineRepository.findByOrder_IdAndId`, since that query has no status filter.

### Pattern 2: Generalize the "count-then-flip" completion guard

**What:** `InventoryReservationSettlementService` flips `StockReservationEntity.status` to
`SETTLED` only when `lineSettlementRepository.countByOrderId(orderId) >= event.totalLines()`
(a durable per-line ledger avoids needing to inspect the aggregated reservation lines to know
"is this the last one"). Once release exists, a line can reach terminal state via **two** paths
(settled for real, or released via cancel) - the completion check must count **both**.

**When to use:** Whole-order cancel (all lines released, no settlement ever happens) is a clean
one-shot `HELD -> RELEASED` transition. Partial-item-cancel leaves the reservation `HELD` (other
lines still headed toward normal settlement) - the eventual "is this reservation fully resolved"
check (if anything downstream needs it) must become:
```java
long settledCount = lineSettlementRepository.countByOrderId(orderId);
long releasedCount = lineReleaseRepository.countByOrderId(orderId);
if (settledCount + releasedCount >= event.totalLines()) {
  reservation.setStatus(ReservationStatus.SETTLED); // or a new "RESOLVED" value if both paths occurred
}
```
**Recommendation:** Add `ReservationStatus.RELEASED` for the pure whole-order-cancel case (100% of
lines released, 0% settled) and leave the mixed case (some released, some later settled) as `HELD`
until the LAST remaining line settles normally through the existing settlement path unmodified -
this avoids inventing a third composite terminal state and keeps the existing settlement service's
flip-to-`SETTLED` logic correct by just widening its denominator check as shown above.

### Pattern 3: Terminal-status guard mirrors REJECTED exactly

**What:** `KitchenStatusProjectionService.onTicketStatusChanged` step (3) already has the exact
line to extend:
```java
// Source: src/main/java/.../order_context/application/KitchenStatusProjectionService.java line 94
if (order.getStatus() == OrderStatus.REJECTED) {
  return;
}
```
**Change to:**
```java
if (order.getStatus() == OrderStatus.REJECTED || order.getStatus() == OrderStatus.CANCELLED) {
  return;
}
```
Additionally, `CANCELLED` must **not** be added to `KitchenStatusProjectionService.FULFILLMENT_RANK`
(exactly like `REJECTED` is absent from it today) - the fail-closed guard
(`currentRank < 0 -> log.warn(...); return;`, the K-WR-03 fix) already refuses to project onto any
status not in that map, so simply omitting CANCELLED from the map is sufficient defense-in-depth
alongside the explicit terminal check above.

`OrderConfirmationService.onStockResult` needs the mirrored guard too (a late `OrderStockResult`
arriving after cancellation must not resurrect `PENDING_CONFIRMATION -> CONFIRMED`):
```java
// Source: line 71, existing guard already blocks anything other than PENDING_CONFIRMATION:
if (order.getStatus() != OrderStatus.PENDING_CONFIRMATION) {
  return;
}
```
This existing guard **already** protects against a stale confirmation resurrecting a cancelled
order (CANCELLED != PENDING_CONFIRMATION), so `OrderConfirmationService` needs **no code change**
- only `KitchenStatusProjectionService` needs the explicit CANCELLED check added, because its
current guard only excludes REJECTED, not "anything not in FULFILLMENT_RANK" at the point where it
would otherwise proceed.

### Pattern 4: Idempotency idiom choice

Two proven idioms exist in this codebase; pick per new consumer:

1. **"Ledger-insert-last-in-same-transaction"** (majority pattern - used by
   `OrderConfirmationService`, `KitchenStatusProjectionService`, `KitchenTicketCreationService`,
   `InventoryReservationService`): pre-check `existsByEventIdAndConsumerName`, do the business
   work, `save()` the ledger row LAST in the SAME `@Transactional` method. A concurrent-duplicate
   unique-constraint violation rolls back the whole transaction; Kafka redelivers and the
   pre-check absorbs the replay.
2. **`InventoryLedgerWriter`-style isolated `REQUIRES_NEW` writer** (used only by
   `InventoryReservationSettlementService`, via `InventoryLedgerWriter.tryInsert`): the ledger row
   is inserted in its OWN suspended transaction immediately, so a concurrent duplicate can be
   detected and absorbed WITHOUT risking poisoning the caller's transaction with a
   `DataIntegrityViolationException` thrown partway through business logic that has already done
   partial (non-transactional) work.

**Recommendation:** Use idiom 1 (majority pattern) for the new Inventory-release, Payment-refund,
and Kitchen-invalidation consumers - it is simpler, requires no new `@Component` bean, and matches
3-of-4 existing consumers. Reach for idiom 2 only if a consumer needs to guarantee the ledger
write is durable before doing something that cannot itself be transactionally rolled back (none of
the new consumers in this phase have that property - refund/release/invalidation are all pure
same-database JPA writes).

### Anti-Patterns to Avoid
- **Reading `StockReservationEntity.lines` to compute a per-line release amount:** the aggregation
  is per-ingredient across the WHOLE order; it has no line dimension. Always re-resolve via
  `RecipeRequirementResolver` (Pattern 1).
- **Trusting `OrderEntity.status` alone as the per-item PREPARING guard:** it is an
  eventually-consistent Kafka **projection** of kitchen_context's true per-item state, not the
  source of truth (see Pitfall 1).
- **Hard-deleting a cancelled `OrderLineEntity` from `order.getLines()`:** `OrderEntity.lines` has
  `orphanRemoval = true`, so removing a line from the list would DELETE the row on flush, losing
  the historical order-line record and (more importantly) silently changing what
  `OrderConfirmationService.toOrderConfirmedEvent()` sends to Kitchen if cancellation happens
  before confirmation completes. Use a soft `cancelledAt` marker instead so the row - and its
  historical price snapshot - survives.
- **Skipping the outbox for the new cancel-compensation events:** Payment's existing publish path
  (`KafkaTemplate.send()` in `afterCommit`) is the OLD, pre-outbox idiom this codebase moved away
  from in Phase 17.1/17.2 specifically because of the crash-between-commit-and-send gap. All THREE
  new consumers in this phase depend on receiving a durably-published event; the cancel service
  MUST use `OutboxWriter.save(...)` in the same transaction as the status/total change, exactly
  like `OrderSubmissionService`/`OrderConfirmationService`/`InventoryReservationService` already do.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| "How much of ingredient X did line Y reserve?" | A new per-line reservation-amount lookup/table | Re-resolve via `RecipeRequirementResolver.resolveForTarget(...)` + `OrderLineLookupPort.findLine(orderId, orderLineId)` | This is the exact mechanism `InventoryReservationSettlementService` already uses for the structurally identical problem (Pattern 1) |
| Refund calculation for "amount already paid" | A new order-level "total paid" field/table in Payment | `PaymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId)` + per-payment `payment.getRefunds()` sum, exactly as `PaymentService.summarizeTotals` already computes it | `PaymentService` already has this exact totals logic; reuse it (or extract it to a shared method) rather than recomputing independently in the new consumer |
| Idempotent Kafka consumption for Payment | A bespoke dedup mechanism (e.g., a Redis SETNX) | A new `payment_processed_events` table, structurally identical to `order_processed_events`/`inventory_processed_events`/`kitchen_processed_events` | Every other context already solved this identically; Payment is the only context missing it, and only because it never previously consumed |
| Kafka DLT / retry wiring for the 3 new consumers | Custom retry loops or manual dead-lettering | Copy `TicketStatusChangedKafkaConsumerConfig`/`SettleTriggerKafkaConsumerConfig` verbatim (ErrorHandlingDeserializer + JacksonJsonDeserializer + DefaultErrorHandler + DeadLetterPublishingRecoverer + FixedBackOff(1000L, 3L)) | This is a proven, reviewed (Phase 17.1 WR-01/WR-03) pattern; reinventing it risks reintroducing already-fixed bugs (poison-pill blocking, silent send failures) |
| Row-level concurrency control on the shared `OrderEntity` | Optimistic `@Version` retry loops, or no locking at all | A new `@Lock(PESSIMISTIC_WRITE) Optional<OrderEntity> lockById(UUID id)` query on `OrderRepository`, mirroring `StockReservationRepository.lockByOrderId`/`KitchenTicketItemRepository.lockByOrderIdAndItemId` | This codebase's established idiom for saga-critical shared rows is a `@Lock(PESSIMISTIC_WRITE)` + `@Query` method, not `@Version`. Consistency with existing patterns matters more than theoretical optimism here (Pitfall 1). |

**Key insight:** Every piece of business logic this phase needs (recipe re-resolution, payment
totals, idempotent consumption, DLT wiring, terminal-status guards) already exists somewhere in
this codebase in a directly analogous form. The work is almost entirely "invert an existing
service" and "extend an existing guard," not "invent new patterns."

## Common Pitfalls

### Pitfall 1: The per-item PREPARING check races across a bounded-context boundary
**What goes wrong:** A cancel request for order line X is accepted (order status still `CONFIRMED`,
no `cancelledAt` conflict) at the exact moment kitchen staff calls
`PATCH /admin/orders/{orderId}/items/{itemId}/status` to advance that same line's
`KitchenTicketItemEntity` from `QUEUED` to `PREPARING`. `KitchenTicketAdvanceService.advance()`
commits its own transaction and publishes `KitchenTicketStatusChangedEvent` **after commit**;
`KitchenStatusProjectionService` (a separate, asynchronous Kafka consumer in order_context) hasn't
necessarily processed that event yet by the time the cancel request's transaction reads
`OrderEntity.status`. Reading only `OrderEntity.status` at cancel-time can therefore say "still
`CONFIRMED`, no item preparing" when kitchen_context's own database already disagrees.

**Why it happens:** `OrderEntity.status` (order_context's database) and
`KitchenTicketItemEntity.status` (kitchen_context's database) are two separate aggregates in two
separate transactional boundaries, bridged only by an eventually-consistent Kafka projection.
Locking the `OrderEntity` row (Pattern in "Don't Hand-Roll") closes races WITHIN order_context (two
HTTP/consumer writers of the same `OrderEntity` row) but cannot close a race against a DIFFERENT
context's database that order_context has no lock on.

**How to avoid:** Add a synchronous cross-context read at cancel-time: a NEW port
`order_context.domain.port.KitchenItemStatusPort` (consumer-owned interface, mirroring
`OrderLineLookupPort`'s ownership convention but in the opposite direction) with an adapter
implemented in `kitchen_context.infrastructure.adapter`, exposing something like
`Optional<KitchenItemStatus> findStatus(UUID orderLineId)` (or a bulk `Map<UUID, KitchenItemStatus>
findStatuses(UUID orderId)` for the whole-order case). The cancel service calls this **inside its
own transaction**, immediately before committing the cancellation, and rejects (or excludes, for
partial cancel) any line whose kitchen status is already `>= PREPARING`. This narrows the race
window to the small gap between that read and the cancel transaction's commit - which is the same
residual risk class this codebase already accepts elsewhere (Kafka is at-least-once, not
exactly-once). Fully closing the window would additionally require `KitchenTicketAdvanceService`
to consult order_context before advancing QUEUED->PREPARING - flagged as an Open Question below,
since it is a larger, bidirectional change not clearly implied by the locked decisions.

**Warning signs:** A cancelled order line whose corresponding `KitchenTicketItemEntity` still
shows `PREPARING`+ afterward (data inconsistency between contexts); a `SettleTriggerEvent` firing
for a line that was already released (double-processing an ingredient's reserved quantity).

### Pitfall 2: A cancelled but already-ticketed item can still be advanced by kitchen staff
**What goes wrong:** If an order reaches `CONFIRMED`, `KitchenTicketCreationService` has already
built a `KitchenTicketEntity` with one `QUEUED` `KitchenTicketItemEntity` per line. If a
partial-item-cancel (or whole-order cancel) then cancels that line in order_context only, nothing
in kitchen_context knows the line was cancelled - `KitchenTicketAdvanceService.advance()` has no
concept of "cancelled" and will happily transition `QUEUED -> PREPARING` for it, which fires a
`SettleTriggerEvent` that inventory will process against an ingredient allocation that was already
released.

**Why it happens:** D-4's locked scope only mentions "releases their held Inventory reservation...
and recomputes the order total" - it does not mention notifying Kitchen. This is a genuine gap
between the locked decisions and full system consistency, not an oversight in this research.

**How to avoid:** Recommend the phase ALSO add a Kitchen-side consumer of the cancel event (see
Architecture Diagram) that, for each cancelled `orderLineId`, either deletes the corresponding
`KitchenTicketItemEntity` row (if still `QUEUED`) or marks it in a way `KitchenTicketAdvanceService`
refuses to advance further - guarded so it NEVER touches an item already `>= PREPARING` (mirroring
the REJECTED-terminal / rank-guard idiom used elsewhere). This requires: (a) a new
`kitchen_processed_events`-consuming listener, (b) either a `KitchenItemStatus.CANCELLED` value
added to the enum (note: the enum's ordinal ordering is explicitly load-bearing per its own
Javadoc - append, never insert) or an `orphanRemoval`-safe deletion of the specific item row (a
ticket losing an item breaks the "never add items after creation" invariant only in the append
direction - removing IS safe as long as `KitchenBoardService`/other readers tolerate a variable
item count). **This is not explicitly locked by CONTEXT.md - flag it to the user/planner as a
recommended in-scope addition, not an assumption to silently build.**

### Pitfall 3: Partial-item-cancel of a paid order can create an "overpaid" order with no refund
**What goes wrong:** D-4 (partial cancel) recomputes `order.total` down but says nothing about
refunding the difference. `PaymentService.summarizeTotals` computes `paymentStatus = PAID` once
`paid >= orderTotal`; if `orderTotal` shrinks below `paid` (customer already paid the full original
total, then some items get partial-cancelled), the order will show `PAID` with `remaining = 0`
forever, silently absorbing the difference with no refund and no error.

**Why it happens:** D-3 (refund automation) is worded around "cancelling a paid order" - reading
literally, this describes the WHOLE-order-cancel case, not partial-item-cancel. D-4 has no
refund clause at all.

**How to avoid:** This is a genuine scope gap in the locked decisions, not something this research
can resolve unilaterally - see Open Questions. The SAFEST literal-reading implementation: refund
automation (D-3) fires ONLY on the whole-order `CANCELLED` transition; partial-item-cancel of an
already-paid order is still ALLOWED (per D-4's plain text) but produces no refund, leaving any
resulting overpayment as a known, documented limitation for this phase (candidate for a follow-up
phase/manual staff reconciliation via the existing manual refund endpoint, which remains available
and untouched).

### Pitfall 4: Kafka DLT / poison-pill wiring must be copy-exact, including Jackson-3 serializers
**What goes wrong:** Every new consumer config in this codebase MUST use
`JacksonJsonSerializer`/`JacksonJsonDeserializer` (Jackson 3, `tools.jackson.databind`), never the
legacy Spring Kafka `JsonSerializer`/`JsonDeserializer` (Jackson 2). Phase 15-01 shipped exactly
this mistake for Payment's/Table's existing producers (fixed in quick task `260710-eqh`, 2026-07-10)
because those events carry `Instant` fields that Jackson-2's default serializer cannot handle on
the Boot 4 / Jackson-3 classpath. Since Payment gets its FIRST-EVER consumer in this phase, this
mistake is trivial to repeat if a new Payment Kafka consumer config is copy-pasted from an
outdated online example rather than from `TicketStatusChangedKafkaConsumerConfig`/
`SettleTriggerKafkaConsumerConfig` in this repo.

**Why it happens:** New consumer wiring is boilerplate-heavy and easy to copy from the wrong
source (an older Spring Kafka tutorial, or - ironically - Payment's OWN pre-existing but
now-fixed producer code, if someone looks at `KafkaPaymentEventPublisher`/
`PaymentKafkaProducerConfig` without checking they were already migrated).

**How to avoid:** Copy the consumer-side wiring block-for-block from
`TicketStatusChangedKafkaConsumerConfig.java` (or `SettleTriggerKafkaConsumerConfig`), substituting
only the event class, topic property name, group-id, and trusted package. Verify
`spring.kafka.producer.value-serializer` in `application.properties` (already
`JacksonJsonSerializer` project-wide since the 2026-07-10 fix) is not shadowed by a
per-context override.

### Pitfall 5: `PaymentRefundEntity.actorUserId` is `@Column(nullable = false)` today
**What goes wrong:** An automatic, event-driven refund has no human actor. Calling
`PaymentService.recordRefund(actorUserId, ...)` from the new consumer with a non-null "system"
UUID works but is semantically misleading (audit logs would show a refund "performed by" a
fabricated user). Calling it with `null` will violate the current `nullable = false` constraint
and throw at flush time.

**Why it happens:** `recordRefund` was designed exclusively for the manual staff/ADMIN endpoint
(`POST /admin/payments/{paymentId}/refunds`), which always has an authenticated actor.

**How to avoid:** Make `PaymentRefundEntity.actorUserId` nullable (a one-line entity change,
auto-migrated by `ddl-auto=update`) and pass `null` for system-triggered refunds - this exactly
mirrors the precedent already set by `InventoryStockMovementEntity.setActorId(null)` in
`InventoryReservationSettlementService` with the comment "system-triggered settlement, no human
actor." Do not invent a sentinel UUID; `null` + the existing precedent is more honest and
consistent.

## Code Examples

### Reusable per-line recipe re-resolution (verbatim reuse, no modification needed)
```java
// Source: src/main/java/.../inventory_context/domain/port/OrderLineLookupPort.java (existing, reuse as-is)
public interface OrderLineLookupPort {
  Optional<OrderLineRecipeSnapshot> findLine(UUID orderId, UUID orderLineId);
}

// Source: src/main/java/.../inventory_context/domain/service/RecipeRequirementResolver.java (existing, reuse as-is)
public Map<UUID, BigDecimal> resolveForTarget(
    RecipeTargetType targetType, UUID targetId, int orderLineQuantity) { ... }
```

### New idempotent ledger table (mirrors `InventoryProcessedEventEntity`, needed for Payment)
```java
// Pattern to copy for payment_context.infrastructure.entity.PaymentProcessedEventEntity
@Entity
@Table(
    name = "payment_processed_events",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_payment_processed_event", columnNames = {"event_id", "consumer_name"}))
public class PaymentProcessedEventEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "event_id", nullable = false) private UUID eventId;
  @Column(name = "consumer_name", nullable = false) private String consumerName;
  @Column(name = "processed_at", nullable = false) private Instant processedAt;
}
```

### New consumer config skeleton (mirrors `TicketStatusChangedKafkaConsumerConfig`)
```java
// Substitute: event class = OrderCancelledEvent, topic property = orders.cancelled,
// group-id = "payment-order-cancelled" (Payment's consumer group), trusted package =
// com.example.feat1.DDD.order_context.application.event
@Bean
public ConsumerFactory<String, OrderCancelledEvent> orderCancelledPaymentConsumerFactory(...) {
  Map<String, Object> props = new HashMap<>();
  props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
  props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
  props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES,
      "com.example.feat1.DDD.order_context.application.event");
  props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, OrderCancelledEvent.class.getName());
  props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
  // ... bootstrap servers, group id, StringDeserializer for key, AUTO_OFFSET_RESET=earliest
}
// DefaultErrorHandler: DeadLetterPublishingRecoverer + FixedBackOff(1000L, 3L) +
// addNotRetryableExceptions(DeserializationException.class) -- copy verbatim.
```

### Refund iteration across all payments of a cancelled order
```java
// New payment_context.application.PaymentAutoRefundService, reusing PaymentService.recordRefund
List<PaymentEntity> payments = paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
for (PaymentEntity payment : payments) {
  BigDecimal alreadyRefunded = payment.getRefunds().stream()
      .map(PaymentRefundEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
  BigDecimal remaining = payment.getAmount().subtract(alreadyRefunded);
  if (remaining.compareTo(BigDecimal.ZERO) > 0) {
    String idempotencyKey = "auto-cancel-" + event.orderId() + "-" + payment.getId();
    paymentService.recordRefund(null /* system actor */, payment.getId(),
        new RecordRefundRequest(remaining, idempotencyKey, "Order cancelled"));
  }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Direct `afterCommit` Kafka publish (`KafkaTemplate.send()`) | Transactional outbox (`OutboxWriter.save()` in-tx + `OutboxRelay` scheduled poller) | Phase 17.1/17.2 (2026-07-09/10), applied to Order + Inventory | Payment (`PaymentService`) and Table (`TableOperationService`, out of scope) were NOT migrated - they still use the old direct-publish idiom. This phase's NEW Payment producer-side work (if any is needed - likely not, since refund is triggered by CONSUMING, not publishing, a new event) should still prefer the outbox if Payment publishes anything new; the INBOUND cancel-consumption path is unaffected by Payment's own producer-side legacy status. |
| Inline `saveAndFlush` + catch `DataIntegrityViolationException` idempotency check | `InventoryLedgerWriter` REQUIRES_NEW isolated writer (settlement only) vs. ledger-insert-last-in-tx (everywhere else) | Phase 17.1 (I-WR-01), applied inconsistently - only `InventoryReservationSettlementService` uses the isolated writer | See Pattern 4 above; use the majority "ledger-last" idiom for this phase's new consumers unless a specific isolation need arises |
| Jackson-2 `JsonSerializer`/`JsonDeserializer` | Jackson-3 `JacksonJsonSerializer`/`JacksonJsonDeserializer` (`tools.jackson.databind`) | Quick task `260710-eqh`, 2026-07-10 - ALL producers/consumers project-wide | Any new Kafka wiring in this phase (Payment's first-ever consumer) MUST use the Jackson-3 classes from day one |

**Deprecated/outdated:** None specific to this phase's domain beyond the two rows above.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Refund automation (D-3) applies only to whole-order cancel, not partial-item-cancel | Pitfall 3, Open Questions | If the user actually wants partial refunds on item-cancel of a paid order, the phase under-delivers; needs explicit confirmation before planning locks this in |
| A2 | Kitchen Context should get a new consumer to invalidate/remove ticket items for cancelled lines | Pitfall 2, Architecture Diagram | If out of scope, a cancelled-but-still-QUEUED kitchen item can still be advanced by staff, causing a settlement to fire for stock that was already released (inventory double-decrement, clamped to 0 but audit-incorrect) |
| A3 | `cancelledAt` nullable marker (not a new `OrderLineStatus` enum) is the right model for per-line cancellation | Standard Stack (Alternatives Considered), Architecture Patterns | Low risk - CONTEXT.md explicitly leaves this to the planner's discretion; either model satisfies the locked decisions, this is a recommendation not a requirement |
| A4 | A new synchronous cross-context port (`KitchenItemStatusPort`) from order_context into kitchen_context is required to race-safely evaluate the per-item PREPARING guard, rather than trusting the projected `OrderEntity.status` | Pitfall 1 | If the planner instead trusts `OrderEntity.status` alone, the phase will pass tests written against that assumption but remain vulnerable to the exact cross-context race the phase brief explicitly asked to be investigated |
| A5 | `PaymentRefundEntity.actorUserId` should become nullable for system-triggered refunds, using `null` rather than a sentinel "system" UUID | Pitfall 5 | Low risk - if the planner instead chooses a sentinel UUID, functionally equivalent, just a less honest audit trail; does not block the phase |

## Open Questions (RESOLVED)

> Resolved 2026-07-10 via user discussion → locked in 18-CONTEXT.md: Q1 → D-6 (whole-order-only refund), Q2 → D-7 (kitchen void consumer), Q3 → one-directional KitchenItemStatusPort read (18-02) + kitchen-void backstop (18-06).

1. **Does refund automation (D-3) apply to partial-item-cancel of a paid order, or only
   whole-order cancel?**
   - What we know: D-3's text ("cancelling a paid order automatically triggers a Payment refund
     for the amount already paid") sits under a heading separate from D-4 (partial cancel), and
     D-4's own text only mentions inventory release + total recompute, no refund.
   - What's unclear: whether the omission in D-4 is intentional (partial cancel never refunds,
     even if it drops the order below the already-paid amount) or an oversight that should be
     addressed by prorating a refund for the cancelled items' value.
   - Recommendation: Plan for D-3 to gate strictly on the WHOLE-order `CANCELLED` transition
     (safest literal reading); document the partial-cancel-of-a-paid-order scenario as a known,
     accepted limitation of this phase (manual refund via the existing endpoint remains available)
     unless the user confirms otherwise before planning locks in.

2. **Should Kitchen Context consume the cancel event to invalidate already-created ticket
   items/tickets?**
   - What we know: Neither D-1 through D-4 nor the canonical references mention modifying
     `kitchen_context`. But leaving it unmodified creates the double-processing risk in Pitfall 2.
   - What's unclear: whether this is considered in-scope "compensation" for this phase or a
     follow-up hardening phase (similar to how Phase 16/17 were split for architectural cleanliness).
   - Recommendation: Include it in this phase's plan as a third consumer of the same
     `OrderCancelledEvent` - it is a small, mechanically similar addition (thin listener + guarded
     item removal/mark), and leaving it out reintroduces exactly the kind of cross-context
     inconsistency this codebase's existing terminal-status guards (REJECTED, forward-only rank)
     were built to prevent.

3. **How fully must the cancel-vs-kitchen-advance race (Pitfall 1) be closed?**
   - What we know: A synchronous cross-context read at cancel-time (via a new
     `KitchenItemStatusPort`) narrows the race to a small window; fully eliminating it would
     additionally require `KitchenTicketAdvanceService.advance()` to consult order_context (or a
     locally-replicated "cancelled line" set) before allowing `QUEUED -> PREPARING`.
   - What's unclear: whether the user considers the residual small-window risk acceptable (this
     codebase already accepts similar small windows elsewhere, e.g. Kafka's at-least-once
     delivery) or wants the fully bidirectional closure.
   - Recommendation: Implement the one-directional check (order_context reads kitchen truth before
     cancelling) as the phase's baseline; flag the bidirectional closure as an optional hardening
     follow-up, consistent with how this codebase has repeatedly split "core feature" from
     "hardening" phases (17 -> 17.1 -> 17.2).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java | Compilation/runtime | Yes | OpenJDK 17.0.19 | - |
| Maven (wrapper) | Build/test | Yes | 3.9.14 (via `./mvnw`) | - |
| Kafka broker | New consumers/producers at runtime | Not verified running in this environment (no live broker probe performed - out of scope for static research) | `localhost:9092` default per `application.properties` | Tests run with `spring.kafka.listener.auto-startup=false` and `outbox.relay.enabled=false` (see `src/test/resources/application.properties`), so unit/integration tests do not require a live broker |
| MySQL / H2 | Persistence | H2 confirmed used in tests (outbox relay's `SKIP LOCKED` query is explicitly gated off in tests because "H2 does not support" it, per `OutboxRelay` Javadoc); production DB assumed MySQL per `ddl-auto=update` + `@Lob`/LONGTEXT comments | - | - |

No missing dependencies block this phase; no new external tooling is required.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + AssertJ (Spring Boot Test starter, project-pinned) |
| Config file | none dedicated - default Maven Surefire wiring via `pom.xml` |
| Quick run command | `./mvnw test -Dtest=ClassName` |
| Full suite command | `./mvnw test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CANCEL-01 | Cancel rejected once order status is PREPARING+ | unit | `./mvnw test -Dtest=OrderCancellationServiceTest` | Wave 0 |
| CANCEL-01 | Cancel rejected when kitchen item already PREPARING despite stale order status (race guard) | unit (mocked `KitchenItemStatusPort`) | `./mvnw test -Dtest=OrderCancellationServiceTest` | Wave 0 |
| CANCEL-02 | Customer cannot cancel another user's order (403/404 per existing `ORDER_NOT_FOUND` idiom) | unit | `./mvnw test -Dtest=OrderCancellationServiceTest` | Wave 0 |
| CANCEL-02 | Staff/ADMIN can cancel any order in window | integration | `./mvnw test -Dtest=OrderCancellationIntegrationTest` | Wave 0 |
| CANCEL-03 | Whole-order cancel sets CANCELLED + publishes outbox event in same tx | unit | `./mvnw test -Dtest=OrderCancellationServiceTest` | Wave 0 |
| CANCEL-04 | Partial cancel recomputes total from non-cancelled lines only | unit | `./mvnw test -Dtest=OrderCancellationServiceTest` | Wave 0 |
| CANCEL-05 | Reservation release decrements reserved only, clamps at 0, idempotent on replay | unit | `./mvnw test -Dtest=InventoryReservationReleaseServiceTest` | Wave 0 |
| CANCEL-05 | Release + settlement together satisfy `settledCount + releasedCount >= totalLines` | unit | `./mvnw test -Dtest=InventoryReservationReleaseServiceTest` | Wave 0 |
| CANCEL-06 | Auto-refund idempotent on event replay (payment_processed_events ledger) | unit | `./mvnw test -Dtest=PaymentAutoRefundServiceTest` | Wave 0 |
| CANCEL-06 | Auto-refund iterates all payments of the order, refunds only the unrefunded remainder | unit | `./mvnw test -Dtest=PaymentAutoRefundServiceTest` | Wave 0 |
| CANCEL-07 | KitchenStatusProjectionService never overwrites CANCELLED with a fulfillment status | unit | `./mvnw test -Dtest=KitchenStatusProjectionServiceTest` (extend existing) | Wave 0 (new test cases in existing file) |
| CANCEL-07 | OrderConfirmationService ignores a stale stock-result for an already-CANCELLED order | unit | `./mvnw test -Dtest=OrderConfirmationServiceTest` (extend existing) | Wave 0 (new test case in existing file) |

### Sampling Rate
- **Per task commit:** targeted `./mvnw test -Dtest=ClassName` for the class(es) touched
- **Per wave merge:** `./mvnw test` (full suite - 213 tests passing as of the last recorded run,
  2026-07-10 quick task `260710-eqh`)
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `OrderCancellationServiceTest.java` - new unit test class, mocks `OrderRepository`,
      `KitchenItemStatusPort`, `OutboxWriter` (mirrors `OrderConfirmationServiceTest` structure)
- [ ] `OrderCancellationIntegrationTest.java` - new integration test (mirrors
      `OrderSubmissionIntegrationTest`/`OrderCartIntegrationTest` structure) covering
      customer-own vs staff/ADMIN authorization end-to-end through Spring Security
- [ ] `InventoryReservationReleaseServiceTest.java` - new unit test class (mirrors
      `InventoryReservationSettlementServiceTest` structure closely - same mocks: ledger repo,
      `StockReservationRepository`, `InventoryStockBalanceRepository`,
      `InventoryStockMovementRepository`, `OrderLineLookupPort`, `RecipeRequirementResolver`)
- [ ] `PaymentAutoRefundServiceTest.java` - new unit test class; this is Payment's FIRST consumer
      test, no existing template in `payment_context` - use `OrderConfirmationServiceTest`'s
      overall shape (mock repos + `PaymentService`, assert idempotency ledger + refund calls) as
      the cross-context template instead
- [ ] Extend `KitchenStatusProjectionServiceTest.java` (if it exists) or create it - add cases for
      the CANCELLED-terminal guard
- [ ] Extend `OrderConfirmationServiceTest.java` - add a case for a stock-result arriving after
      the order is already CANCELLED (existing status-guard already handles it; add a regression
      test to lock in the behavior)

## Security Domain

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No (unchanged) | Existing JWT `CustomUserDetails`/`JwtAuthenticationFilter` |
| V3 Session Management | No (unchanged) | N/A |
| V4 Access Control | Yes | Ownership check via `OrderRepository.findByIdAndUserId` (customer path) + Spring Security route matcher `hasAnyRole("ADMIN","STAFF")` on `/admin/orders/**` (staff path) - mirrors existing `PaymentController`/`KitchenController` conventions exactly |
| V5 Input Validation | Yes | Path variables (`orderId`, `lineId`) are UUIDs bound by Spring MVC; no free-text request body needed for cancel (a request body is optional discretion - e.g. an optional cancellation reason string, should be length-capped like `OrderEntity.rejectionReason`) |
| V6 Cryptography | No | N/A - no new secrets/crypto in this phase |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| IDOR - customer cancelling another user's order by guessing/enumerating `orderId` | Tampering / Information Disclosure | `OrderRepository.findByIdAndUserId(orderId, userId)` returning empty -> `OrderDomainException.orderNotFound()` (404, not 403 - avoids confirming the order exists), exactly the existing idiom in `OrderSubmissionService.getOrder` |
| Double-refund via Kafka redelivery | Repudiation / Tampering (financial) | `payment_processed_events` idempotency ledger (NEW) + `PaymentService.recordRefund`'s existing per-payment idempotency-key dedup (`findByPayment_IdAndIdempotencyKey`) - two independent layers, mirroring the dual-guard idiom already used in `InventoryReservationSettlementService` ("Pitfall 4" in that phase's own research) |
| TOCTOU on the cancel-window guard (check-then-act race vs. concurrent order/kitchen state changes) | Tampering | `OrderRepository.lockById` (NEW, `PESSIMISTIC_WRITE`) acquired before the status check within order_context; a synchronous `KitchenItemStatusPort` read for the cross-context portion (Pitfall 1) - residual risk documented, not fully eliminated |
| Over-refund exceeding the original payment amount | Tampering (financial) | Already enforced by `PaymentDomainException.refundExceedsPayment()` inside `PaymentService.recordRefund` - the new auto-refund consumer computes and requests only the unrefunded remainder per payment, and the existing guard is a backstop even if that computation is ever wrong |
| Kafka poison-pill / malformed cross-context event payload | Denial of Service | `ErrorHandlingDeserializer` + `DeserializationException` routed straight to DLT (not retried), exactly as wired in every existing consumer config - copy verbatim (Pitfall 4) |

## Sources

### Primary (HIGH confidence - direct repository inspection)
- `src/main/java/.../order_context/domain/model/OrderStatus.java` - enum + load-bearing rank comment
- `src/main/java/.../order_context/infrastructure/entity/{OrderEntity,OrderLineEntity}.java` - current line/total model
- `src/main/java/.../order_context/application/{OrderSubmissionService,OrderConfirmationService,KitchenStatusProjectionService}.java` - status-transition, outbox-write, and terminal-guard idioms
- `src/main/java/.../order_context/infrastructure/presentation/OrderController.java` + `src/main/java/.../auth/infrastructure/security/SecurityConfig.java` - route/role conventions
- `src/main/java/.../inventory_context/infrastructure/entity/StockReservationEntity.java` + `application/{InventoryReservationService,InventoryReservationSettlementService}.java` + `domain/service/RecipeRequirementResolver.java` - reservation model and the settlement path to invert
- `src/main/java/.../inventory_context/infrastructure/entity/{InventoryStockMovementEntity,InventoryStockBalanceEntity}.java` + `domain/model/InventoryMovementType.java` - audit movement schema
- `src/main/java/.../kitchen_context/infrastructure/entity/{KitchenTicketEntity,KitchenTicketItemEntity}.java` + `application/{KitchenTicketCreationService,KitchenTicketAdvanceService}.java` - per-item PREPARING boundary and event timing (source of Pitfall 1/2)
- `src/main/java/.../payment_context/application/PaymentService.java` + `infrastructure/entity/{PaymentEntity,PaymentRefundEntity}.java` + `infrastructure/adapter/KafkaPaymentEventPublisher.java` - existing refund capability and its (non-outbox) publish path
- `src/main/java/.../shared/outbox/**` - `OutboxWriter`, `OutboxRelay`, `OutboxEventEntity` - the transactional-outbox mechanism
- `src/main/java/.../order_context/infrastructure/config/TicketStatusChangedKafkaConsumerConfig.java` + `inventory_context/.../SettleTriggerListener.java` - consumer/DLT wiring template
- `src/main/resources/application.properties` - `ddl-auto=update` (no manual migrations), Jackson-3 serializer config, existing topic names
- `.planning/phases/18-.../18-CONTEXT.md`, `.planning/REQUIREMENTS.md`, `.planning/STATE.md` - locked decisions and project history

### Secondary (MEDIUM confidence)
None - this research relied entirely on direct primary-source repository inspection; no
WebSearch/Context7 lookups were needed since the phase is 100% internal-pattern replication with
no new external library surface.

### Tertiary (LOW confidence)
None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - zero new dependencies; all reused code read directly from the repository
- Architecture: HIGH for the Inventory-release inversion and terminal-status guard extension
  (directly mirrored from working, tested, reviewed code); MEDIUM for the Payment auto-refund
  consumer shape (first-of-its-kind in this context, extrapolated from patterns used elsewhere)
- Pitfalls: HIGH confidence that the races/gaps described are real (traced through actual
  transaction boundaries and event timing in the existing code); MEDIUM confidence on the
  *recommended* resolutions for Pitfalls 2 and 3, since CONTEXT.md leaves them open (see
  Assumptions Log A1/A2/A4)

**Research date:** 2026-07-10
**Valid until:** 30 days (stable, internal-only research; no external library version drift risk)
