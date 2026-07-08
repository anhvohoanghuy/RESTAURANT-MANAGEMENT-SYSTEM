# Phase 17: Kitchen context - Research

**Researched:** 2026-07-08
**Domain:** Spring Boot 4 / Java 17 DDD bounded-context addition (Kafka producer+consumer pair, JPA aggregate, admin REST endpoints) — no frontend, no AI.
**Confidence:** HIGH (nearly every claim below is `[VERIFIED: codebase]` — read directly from this repository's existing Phase 15/16 code, which is the load-bearing precedent for this phase)

## Summary

Phase 17 adds a **new** `kitchen_context` bounded context that sits between `order_context` and
`inventory_context` in the existing Kafka saga chain. Every structural piece this phase needs
already has a working precedent in this exact codebase from Phases 10, 15, and 16: a
producer/consumer Kafka pair with Jackson-3 native serde, an idempotency ledger entity +
`REQUIRES_NEW` ledger-writer, an after-commit event-publish helper (`TransactionSynchronizationManager`),
a pessimistic-lock repository method, and a PATCH-style admin status-transition endpoint with a
forward-only guard. None of this needs to be invented — it needs to be **copied and retargeted**.

The single most load-bearing, non-obvious finding: **this codebase does NOT enforce Kafka event
DTOs as private to their producing context.** `SettleTriggerEvent` lives in
`inventory_context.application.event` (the context that *consumes* it) and Phase 16's own producer
(the future kitchen context) will need to `import` that exact class across the package boundary —
exactly mirroring how `InventoryKafkaProducerConfig` already imports
`order_context.application.event.OrderStockResultEvent` today to produce it. Event record classes
are shared contracts, not context-private DTOs. This directly resolves canonical-ref requirement
"kitchen MUST produce a `SettleTriggerEvent` that matches Phase 16's contract exactly": kitchen's
producer imports and constructs `com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent`
directly — it does not redeclare an equivalent record.

**Primary recommendation:** Build `kitchen_context` as a structural clone of `order_context` /
`inventory_context`'s Phase-15/16 slice: one new JPA aggregate (`KitchenTicket` + `KitchenTicketItem`),
one new inbound Kafka listener (`OrderConfirmed`) with its own idempotency ledger entity, one
`@Transactional` service that both advances item status AND (on `QUEUED→PREPARING`) publishes the
**existing** `SettleTriggerEvent` after commit, one new outbound Kafka producer
(`KitchenTicketStatusChangedEvent`) also published after commit, and two REST endpoints reusing the
already-secured `/admin/orders/**` route. On the order side: extend `OrderStatus`, add one new
Kafka producer call inside `OrderConfirmationService.onStockResult()` (after-commit, mirroring
`OrderSubmissionService`'s existing pattern), and one new consumer + idempotency ledger mirroring
`OrderStockResultListener`/`OrderKafkaConsumerConfig` verbatim.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `KitchenTicket` aggregate + per-item lifecycle | API / Backend (kitchen_context) | Database / Storage | New bounded context owns fulfillment state exclusively (locked boundary rule) |
| Consume `OrderConfirmed` → create ticket | API / Backend (kitchen_context, Kafka consumer) | — | Async saga step, mirrors `OrderCreatedListener` in inventory_context |
| Publish `OrderConfirmed` | API / Backend (order_context) | — | New producer call inside existing `OrderConfirmationService.onStockResult()` |
| Publish `SettleTriggerEvent` on `QUEUED→PREPARING` | API / Backend (kitchen_context, Kafka producer) | — | Reuses Phase-16 inbound contract; kitchen is a NEW producer of an EXISTING event class |
| Publish ticket-status-changed | API / Backend (kitchen_context, Kafka producer) | — | New producer; order_context is the sole consumer |
| Consume ticket-status-changed → derive `OrderStatus` | API / Backend (order_context, Kafka consumer) | Database / Storage | order_context stays a pure event consumer, never queries kitchen's tables |
| Staff advance endpoint (`PATCH /admin/orders/{orderId}/items/{itemId}/status`) | API / Backend | — | Already-secured route; new controller/service in kitchen_context |
| Kitchen board read endpoint | API / Backend | — | Read-only projection over `KitchenTicket`/`KitchenTicketItem` |
| Auth/RBAC for `/admin/orders/**` | API / Backend (Spring Security) | — | Already implemented (`SecurityConfig`); zero new security code required |

## User Constraints (from CONTEXT.md)

<user_constraints>

### Locked Decisions

- **D-01:** `order_context` publishes a **new `OrderConfirmed` event** when an order transitions to
  `CONFIRMED` (inside `OrderConfirmationService.onStockResult()`, which today only mutates the entity —
  it publishes nothing). The event carries the **full line manifest** (`orderId`, and per line:
  `lineId`, `dishId`, `dishName`, `quantity`, selected toppings) so kitchen builds its `KitchenTicket`
  items **self-contained** — no runtime cross-context lookup. Mirror the existing
  `KafkaOrderEventPublisher` / `OrderEventPublisher` port + `OrderKafkaProducerConfig` pattern.
  Kitchen's `OrderConfirmed` consumer is **idempotent** (processed-events ledger, mirroring the
  established consumer pattern) — one confirmed order creates exactly one ticket.
- **D-02:** A `KitchenTicket` holds one line-item per order line. Each item starts in **`QUEUED`** and
  advances **strictly forward, one step at a time**: `QUEUED → preparing → ready → served →
  completed`. No skipping states, no reverting. `preparing` is therefore a **deliberate staff
  action**, not an automatic on-create state.
- **D-03:** The **settle-trigger event fires exactly once**, on an item's `QUEUED → preparing`
  transition. It MUST match the existing inbound contract
  `SettleTriggerEvent(eventId, eventType, occurredAt, orderId, orderLineId, totalLines)` and be
  published to the topic Phase 16's consumer listens on (default `kitchen.settlement-trigger`).
  Because the transition is forward-only and single-step, the item can never re-enter `preparing`, so
  no second trigger is possible. `totalLines` = the ticket's total item count.
- **D-04:** Extend `OrderStatus` with `PREPARING, READY, SERVED, COMPLETED`. Kitchen publishes a
  **ticket-status-changed event** on each item transition; `order_context` **consumes** it (mirror the
  `OrderStockResultListener` / `OrderKafkaConsumerConfig` consumer pattern) and **derives** order-level
  status from the item states:
  - any item in `preparing` (and not yet all further) → `PREPARING`
  - all items `ready` → `READY`
  - all items `served` → `SERVED`
  - all items `completed` → `COMPLETED`

  The order-side consumer is **idempotent** and only advances status forward (no backward transition).
  Kitchen never writes the Order aggregate directly.
- **D-05:** Provide **two** endpoints:
  1. **Advance (write):** set a single item's status, e.g.
     `PATCH /admin/orders/{orderId}/items/{itemId}/status` with the target state in the body; rejects
     illegal (non-forward / skipping) transitions with a stable error.
  2. **Kitchen board (read):** list active tickets/items for a kitchen display (items not yet
     `completed`), so staff can see the queue rather than mutate blindly.

  `/admin/orders/**` is **already secured** with `hasAnyRole("ADMIN","STAFF")` in `SecurityConfig` —
  no security change is needed for these routes.

**Boundary rule (locked):** Inventory re-resolves recipes itself, so the settle-trigger event stays
thin — kitchen MUST NOT carry ingredient amounts. Kitchen owns fulfillment state; `order_context`
owns order status and is a pure **consumer** of kitchen's status events.

### Claude's Discretion

- Exact event class names, Kafka topic / group-id / DLT names (kitchen owns the new `OrderConfirmed`
  and ticket-status-changed topics; the settle-trigger topic name must match Phase 16's
  `kitchen.settlement-trigger` default).
- `KitchenTicket` / ticket-item entity shape, aggregate persistence, and how order-level status is
  computed/stored on the read side.
- Request/response DTO shapes, exact URL paths, error codes, and the board endpoint's filter/sort.
- Consumer wiring details (error handler, DLT, ack mode) — reuse the Phase 15 Kafka style.

### Deferred Ideas (OUT OF SCOPE)

- Kitchen-board **UI** (only the read API is in scope this phase).
- Multi-station routing, prep-time / throughput analytics, expo screens.
- Reverting an item's status to fix mistakes (forward-only chosen for D-02; revisit if staff need it —
  would require guarding against a second settle-trigger on a re-entered `preparing`).
- Reservation **release** on order cancel/refund (no cancel flow exists yet — carried from Phase 16).
- Retro-applying Phase 15 review items (WR-03/04/05) and migrating Payment/Table producers to Jackson-3.

</user_constraints>

## Phase Requirements

<phase_requirements>

No formal `REQ-ID`s exist for this phase (confirmed against `.planning/REQUIREMENTS.md` — it has no
`KITCHEN-*` section; Phase 17 is a roadmap-only phase whose requirements are the locked decisions
above, exactly like Phase 16). The planner should treat D-01..D-05 as the requirement set.

| ID (informal) | Description | Research Support |
|----|-------------|------------------|
| D-01 | `OrderConfirmed` published by order_context, consumed idempotently by kitchen, self-contained line manifest | See "The OrderConfirmed event" + "Publisher extension" sections below |
| D-02 | `KitchenTicket`/item forward-only lifecycle `QUEUED→PREPARING→READY→SERVED→COMPLETED` | See "KitchenTicket aggregate shape" section |
| D-03 | Settle-trigger fires exactly once on `QUEUED→PREPARING`, matches existing `SettleTriggerEvent` contract | See "Reusing SettleTriggerEvent" section |
| D-04 | `OrderStatus` extended; order_context derives status from kitchen's ticket-status-changed event, forward-only, idempotent | See "Ticket-status-changed event + order-side derivation" section |
| D-05 | Two staff endpoints under `/admin/orders/**` (advance write + board read), already secured | See "Staff REST endpoints" section |

</phase_requirements>

## Standard Stack

No new libraries are needed or permitted (CONTEXT.md: "No new dependencies"). Everything below is
already on the classpath `[VERIFIED: pom.xml]`.

### Core (already present, reused as-is)

| Library | Version | Purpose | Why Standard (for this phase) |
|---------|---------|---------|--------------------------------|
| `spring-boot-starter-parent` | 4.0.6 `[VERIFIED: pom.xml]` | Boot 4 / Jackson 3 baseline | Already governs every other context |
| `spring-kafka` | (Boot-managed, no explicit version pin) `[VERIFIED: pom.xml]` | Producer/consumer wiring | Identical pattern to Phases 10/15/16 |
| `spring-boot-starter-data-jpa` | (Boot-managed) `[VERIFIED: pom.xml]` | `KitchenTicket`/`KitchenTicketItem` persistence | Same ORM style as every other context |
| `org.springframework.kafka.support.serializer.JacksonJsonSerializer` / `JacksonJsonDeserializer` | Jackson 3 native (bundled with spring-kafka on Boot 4) `[VERIFIED: pom.xml + code]` | Event serde | The established "phase-wide Jackson-3 native serde" — legacy Jackson-2 `JsonSerializer`/`JsonDeserializer` cannot handle `java.time.Instant` on this classpath (no `jackson-datatype-jsr310` present) |
| `lombok` | (Boot-managed) `[VERIFIED: pom.xml]` | `@Getter`/`@Setter`/`@RequiredArgsConstructor` on entities/services | Used on every existing entity/service |
| Spring Security (`hasAnyRole`) | (Boot-managed) `[VERIFIED: SecurityConfig.java]` | `/admin/orders/**` RBAC | Already configured — zero new security code |

### Package Legitimacy Audit

**Not applicable — this phase installs no external packages.** `pom.xml` requires zero new
dependencies; every class/library referenced above is already resolved on the existing classpath.
slopcheck / registry verification is skipped per the gate's own scope ("whenever this phase installs
external packages") — none are installed here.

## Architecture Patterns

### System Architecture Diagram

```
                 ┌────────────────────┐
                 │   order_context    │
                 │ OrderConfirmation  │
                 │      Service       │
                 └─────────┬──────────┘
                           │ (1) onStockResult() transitions
                           │     PENDING_CONFIRMATION → CONFIRMED
                           │ (2) publishAfterCommit(OrderConfirmedEvent)
                           ▼
                 topic: orders.confirmed
                           │
                           ▼
                 ┌────────────────────┐
                 │  kitchen_context   │
                 │ OrderConfirmed     │   consumes, idempotent (ledger)
                 │    Listener        │──────────────┐
                 └────────────────────┘              │ creates
                                                      ▼
                                          ┌───────────────────────┐
                                          │     KitchenTicket     │
                                          │  + KitchenTicketItem  │  (QUEUED per line)
                                          │      (JPA aggregate)  │
                                          └───────────┬───────────┘
                                                      │
             PATCH /admin/orders/{orderId}/items/{itemId}/status
                     (ADMIN/STAFF, already secured)
                                                      │
                                                      ▼
                                     ┌────────────────────────────┐
                                     │ KitchenTicketAdvanceService │
                                     │ - lock ticket item row      │
                                     │ - validate forward-only     │
                                     │ - if QUEUED→PREPARING:      │
                                     │   publish SettleTriggerEvent│
                                     │ - always: publish           │
                                     │   TicketStatusChangedEvent  │
                                     └───────┬──────────────┬──────┘
                                             │(after commit)│(after commit)
                     topic: kitchen.settlement-trigger      topic: kitchen.ticket-status-changed
                                             │                              │
                                             ▼                              ▼
                                ┌─────────────────────────┐   ┌──────────────────────────┐
                                │   inventory_context      │   │      order_context       │
                                │ SettleTriggerListener     │   │ TicketStatusChanged      │
                                │ (Phase 16 — unchanged)    │   │    Listener              │
                                └─────────────────────────┘   └───────────┬──────────────┘
                                                                          │ derives OrderStatus
                                                                          │ (forward-only, idempotent)
                                                                          ▼
                                                               OrderEntity.status updated
                                                          (PREPARING/READY/SERVED/COMPLETED)

    GET /admin/orders/kitchen-board  →  KitchenBoardService  →  reads KitchenTicket/Item directly
    (read-only projection, no event involved)
```

A reader can trace the primary use case (order confirmed → ticket created → staff advances an item →
stock settles → order status reflects fulfillment) purely by following the arrows above.

### Recommended Project Structure

Mirrors `order_context`/`inventory_context` exactly (`[VERIFIED: codebase directory listing]`):

```
src/main/java/com/example/feat1/DDD/kitchen_context/
├── domain/
│   ├── model/
│   │   ├── KitchenItemStatus.java          # enum: QUEUED, PREPARING, READY, SERVED, COMPLETED
│   │   └── KitchenDomainException.java     # extends AppException, stable error codes
│   └── snapshot/                            # (only if a cross-context read port is added later; none needed now)
├── application/
│   ├── KitchenTicketCreationService.java    # consumes OrderConfirmed → creates KitchenTicket (idempotent)
│   ├── KitchenTicketAdvanceService.java      # advance-item-status use case + SettleTrigger + status-changed publish
│   ├── KitchenBoardService.java              # read-only board query
│   ├── KitchenLedgerWriter.java               # REQUIRES_NEW idempotency-ledger writer (mirrors InventoryLedgerWriter)
│   ├── dto/
│   │   └── KitchenDtos.java                   # AdvanceItemStatusRequest, KitchenBoardItemResponse, etc.
│   └── event/
│       └── KitchenTicketStatusChangedEvent.java  # NEW event this context owns/produces
├── infrastructure/
│   ├── entity/
│   │   ├── KitchenTicketEntity.java
│   │   ├── KitchenTicketItemEntity.java
│   │   ├── KitchenTicketItemToppingSnapshot.java   # @Embeddable, mirrors OrderLineToppingSnapshot
│   │   └── KitchenProcessedEventEntity.java         # idempotency ledger for the OrderConfirmed consumer
│   ├── repository/
│   │   ├── KitchenTicketRepository.java
│   │   ├── KitchenTicketItemRepository.java
│   │   └── KitchenProcessedEventRepository.java
│   ├── adapter/
│   │   ├── OrderConfirmedListener.java              # @KafkaListener, thin delegate
│   │   └── KafkaSettleTriggerPublisher.java          # implements a small port, publishes inventory's SettleTriggerEvent
│   ├── config/
│   │   ├── KitchenKafkaTopicConfig.java              # NewTopic beans for orders.confirmed + .DLT, kitchen.ticket-status-changed + .DLT
│   │   ├── OrderConfirmedKafkaConsumerConfig.java    # mirrors InventoryKafkaConsumerConfig (OrderCreated) exactly
│   │   └── KitchenKafkaProducerConfig.java            # 2 ProducerFactory/KafkaTemplate pairs: SettleTriggerEvent (cross-import) + KitchenTicketStatusChangedEvent
│   └── presentation/
│       └── KitchenController.java                     # PATCH advance + GET board, under /admin/orders/**
```

New files needed in `order_context` (extending, not replacing, existing files):
```
order_context/application/event/OrderConfirmedEvent.java        # NEW, sibling of OrderCreatedEvent
order_context/domain/port/OrderEventPublisher.java               # ADD publishOrderConfirmed(OrderConfirmedEvent)
order_context/infrastructure/adapter/KafkaOrderEventPublisher.java # ADD 2nd KafkaTemplate field + method
order_context/infrastructure/config/OrderKafkaProducerConfig.java  # ADD 2nd ProducerFactory/KafkaTemplate bean pair
order_context/application/OrderConfirmationService.java            # ADD publishAfterCommit(...) call + TransactionSynchronization* imports
order_context/domain/model/OrderStatus.java                        # ADD PREPARING, READY, SERVED, COMPLETED
order_context/infrastructure/adapter/TicketStatusChangedListener.java   # NEW, mirrors OrderStockResultListener
order_context/infrastructure/config/TicketStatusChangedKafkaConsumerConfig.java  # NEW, mirrors OrderKafkaConsumerConfig
order_context/application/KitchenStatusProjectionService.java (or extend OrderConfirmationService)  # NEW idempotent derive-and-apply logic
```

### Pattern 1: Reusing an existing cross-context event class for a NEW producer

**What:** `SettleTriggerEvent` already exists in `inventory_context.application.event`
(`[VERIFIED: codebase]`, exact fields `(eventId, eventType, occurredAt, orderId, orderLineId,
totalLines)`, `TYPE = "SettleTrigger"`). Kitchen becomes a NEW producer of this SAME class — it does
not declare its own copy.
**When to use:** Whenever a locked contract from an earlier phase specifies "MUST match the existing
event" (D-03).
**Example (established precedent this pattern is copied from — inventory already does this for a
different event):**
```java
// Source: src/main/java/.../inventory_context/infrastructure/config/InventoryKafkaProducerConfig.java
package com.example.feat1.DDD.inventory_context.infrastructure.config;

import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent; // CROSS-CONTEXT IMPORT

@Configuration
public class InventoryKafkaProducerConfig {
  @Bean
  public ProducerFactory<String, OrderStockResultEvent> orderStockResultProducerFactory(...) { ... }
}
```
Kitchen's new `KitchenKafkaProducerConfig` follows the identical shape, importing
`com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent` instead.

### Pattern 2: Idempotent Kafka consumer with a dedicated ledger entity

**What:** Every existing consumer (`OrderCreatedListener`, `SettleTriggerListener`,
`OrderStockResultListener`) guards against replay with a unique `(event_id, consumer_name)` row,
checked with `existsByEventIdAndConsumerName` then inserted with `saveAndFlush` inside a
try/catch on `DataIntegrityViolationException`.
**When to use:** Kitchen's `OrderConfirmed` consumer AND order_context's new ticket-status-changed
consumer both need their OWN ledger table — physically distinct per context (the codebase already
has 2: `order_processed_events`, `inventory_processed_events`; add
`kitchen_processed_events` as the 3rd).
**Example:**
```java
// Source: src/main/java/.../order_context/application/OrderConfirmationService.java (existing)
if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
  return;
}
try {
  OrderProcessedEventEntity ledger = new OrderProcessedEventEntity();
  ledger.setEventId(event.eventId());
  ledger.setConsumerName(CONSUMER_NAME);
  ledger.setProcessedAt(Instant.now());
  processedEventRepository.saveAndFlush(ledger);
} catch (DataIntegrityViolationException duplicate) {
  return; // concurrent delivery already inserted it
}
```
For any handler that ALSO writes business state that must never be poisoned by a concurrent-duplicate
ledger insert, use the isolated `REQUIRES_NEW` writer instead (see Pattern 4 below) — this simple
inline version is sufficient for `OrderConfirmationService`-style handlers that don't hold a
pessimistic lock across the ledger write.

### Pattern 3: Publish-after-commit (NOT an outbox table)

**What:** This codebase has **no outbox table**. Every cross-context event publish after a state
change uses `TransactionSynchronizationManager.registerSynchronization(...).afterCommit(...)`,
falling back to a synchronous publish only if no transaction synchronization is active (e.g. called
outside a `@Transactional` method, which should not happen in practice here).
**When to use:** Every NEW publish point in this phase: `OrderConfirmationService` publishing
`OrderConfirmedEvent`, and kitchen's advance service publishing both `SettleTriggerEvent` and
`KitchenTicketStatusChangedEvent`.
**Example (verbatim existing pattern, already used twice in this codebase):**
```java
// Source: src/main/java/.../order_context/application/OrderSubmissionService.java (existing, lines 225-237)
private void publishAfterCommit(OrderCreatedEvent event) {
  if (!TransactionSynchronizationManager.isSynchronizationActive()) {
    orderEventPublisher.publishOrderCreated(event);
    return;
  }
  TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          orderEventPublisher.publishOrderCreated(event);
        }
      });
}
```
`table_context.application.TableOperationService` has an identical private helper
(`publishAfterCommit(TableOperationEvent event)`, line 565) — this is a two-for-two established
convention, not a one-off.

### Pattern 4: `REQUIRES_NEW` ledger writer as a separate `@Component` (WR-01 fix)

**What:** `InventoryLedgerWriter` is a standalone `@Component` (not a private method) whose single
method is `@Transactional(propagation = Propagation.REQUIRES_NEW)`. This exists ONLY because a
private/self-invoked method would bypass the Spring transaction proxy and share the caller's
transaction — defeating the isolation.
**When to use:** Kitchen's advance service needs this if it locks a row AND writes a ledger AND must
tolerate a concurrent-duplicate ledger insert without poisoning the whole business transaction —
i.e., if kitchen adopts the same "ledger + business state in one transaction" shape as
`InventoryReservationSettlementService`. If instead kitchen's ledger check happens BEFORE acquiring
any lock (like `OrderConfirmationService` does), the simpler inline try/catch (Pattern 2) is
sufficient and this component is unnecessary. **Recommendation:** use the simple inline form for the
`OrderConfirmed`-consumer (ticket creation is a straightforward "does this ticket exist yet" check),
matching `OrderConfirmationService`'s style rather than `InventoryReservationSettlementService`'s.
**Example:**
```java
// Source: src/main/java/.../inventory_context/application/InventoryLedgerWriter.java (existing)
@Component
@RequiredArgsConstructor
public class InventoryLedgerWriter {
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean tryInsert(UUID eventId, String consumerName) {
    try {
      InventoryProcessedEventEntity ledger = new InventoryProcessedEventEntity();
      ledger.setEventId(eventId);
      ledger.setConsumerName(consumerName);
      ledger.setProcessedAt(Instant.now());
      processedEventRepository.saveAndFlush(ledger);
      return true;
    } catch (DataIntegrityViolationException duplicate) {
      return false;
    }
  }
}
```

### Pattern 5: Pessimistic row lock before mutating shared state

**What:** `StockReservationRepository.lockByOrderId` uses
`@Lock(LockModeType.PESSIMISTIC_WRITE)` on a `findBy...` query method — the standard Spring Data JPA
mechanism (no manual `EntityManager` calls).
**When to use:** Kitchen's advance-item-status service MUST lock the `KitchenTicketItem` row (or the
parent `KitchenTicketEntity` row) before checking-then-writing its status, to close the race where
two concurrent PATCH requests for the SAME item both read `QUEUED` and both attempt the
`QUEUED→PREPARING` transition (this is THE double-settle-trigger landmine — see Common Pitfalls).
**Example:**
```java
// Source: src/main/java/.../inventory_context/infrastructure/repository/StockReservationRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select r from StockReservationEntity r where r.orderId = :orderId")
Optional<StockReservationEntity> lockByOrderId(UUID orderId);
```
Kitchen's repository needs an equivalent `lockById(UUID itemId)` (or lock the parent ticket, locking
all its items in one row-lock if items are an `@ElementCollection`/`@OneToMany` — see aggregate-shape
discussion below) on `KitchenTicketItemRepository`.

### Pattern 6: PATCH status-transition endpoint with forward-only guard, stable error

**What:** `TableOperationService.updateReservationStatus` is the closest existing precedent for
"PATCH .../status with a target enum in the body, validate the transition, throw a stable domain
error if illegal."
**Example:**
```java
// Source: src/main/java/.../table_context/application/TableOperationService.java (lines 237-248)
@Transactional
public TableReservationResponse updateReservationStatus(
    UUID reservationId, UpdateReservationStatusRequest request, UUID actorUserId) {
  TableReservationEntity reservation =
      reservationRepository.findById(reservationId)
          .orElseThrow(TableDomainException::reservationNotFound);
  ReservationStatus target = request == null ? null : request.status();
  if (!isValidTransition(reservation.getStatus(), target)) {
    throw TableDomainException.reservationStatusInvalid();
  }
  reservation.setStatus(target);
  ...
}
```
```java
// Source: src/main/java/.../table_context/infrastructure/presentation/TableOperationController.java (line 98)
@PatchMapping("/admin/tables/reservations/{reservationId}/status")
public ResponseEntity<TableReservationResponse> updateReservationStatus(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable UUID reservationId,
    @RequestBody UpdateReservationStatusRequest request) { ... }
```
Kitchen's `PATCH /admin/orders/{orderId}/items/{itemId}/status` controller method should be shaped
identically, with `@AuthenticationPrincipal CustomUserDetails principal` available for
`actorId`/audit even though D-05 does not require storing it.

### Anti-Patterns to Avoid

- **Business logic in the `@KafkaListener` method:** every existing listener
  (`OrderCreatedListener`, `SettleTriggerListener`, `OrderStockResultListener`) is a one-line
  delegate to a `@Transactional` service method. Kitchen's `OrderConfirmedListener` and
  order_context's ticket-status-changed listener must follow this exactly — no ledger/business logic
  in the `@Component` adapter class itself.
- **Kitchen mutating `OrderEntity` directly:** explicitly forbidden by the locked boundary rule.
  Kitchen must publish an event; only `order_context`'s own consumer may call
  `orderRepository.save(...)` on an `OrderEntity`.
- **order_context querying kitchen's tables to compute status:** the "derives" language in D-04 must
  be satisfied purely from the ticket-status-changed event's payload (see Open Questions below for
  the exact payload shape needed) — not via a cross-context JPA query or a new read port back into
  kitchen_context.
- **Redeclaring `kitchen.settlement-trigger` / `.DLT` as `NewTopic` beans:** these are ALREADY
  declared in `InventoryKafkaTopicConfig` (`settleTriggerTopic()` / `settleTriggerDltTopic()` beans,
  `[VERIFIED: InventoryKafkaTopicConfig.java]`). Kitchen's new `KitchenKafkaTopicConfig` must declare
  `NewTopic` beans ONLY for its two genuinely new topics (`orders.confirmed`,
  `kitchen.ticket-status-changed`, plus their `.DLT`s) — redeclaring the settle-trigger topic bean a
  second time with the same name is harmless to Kafka itself (idempotent `TopicBuilder`) but is
  needless duplication and drifts if the property default ever changes in only one place.
- **Raw `enum.ordinal()` comparison for the forward-only `OrderStatus` guard without a code comment
  pinning declaration order:** see Common Pitfalls — declaration order becomes silently
  load-bearing.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Kafka replay/duplicate-delivery protection | A custom in-memory dedup cache or "check if ticket exists" as the only guard | The established `(event_id, consumer_name)` ledger-table pattern (Pattern 2/4 above) | Ticket-exists-check alone is insufficient if the SAME `OrderConfirmed` event is redelivered after the ticket-creation transaction partially failed later in the same handler; the ledger is the durable, tested guard already proven across 3 consumers |
| Poison-pill / bad-payload handling | Custom try/catch around manual `ObjectMapper.readValue` | `ErrorHandlingDeserializer` wrapping `JacksonJsonDeserializer`, `USE_TYPE_INFO_HEADERS=false` + `TRUSTED_PACKAGES` + `VALUE_DEFAULT_TYPE`, `DefaultErrorHandler.addNotRetryableExceptions(DeserializationException.class)` | Exact copy-paste template exists 3 times (`InventoryKafkaConsumerConfig`, `SettleTriggerKafkaConsumerConfig`, `OrderKafkaConsumerConfig`) — do not deviate |
| Topic auto-creation fallback | Relying on broker `auto.create.topics.enable` | Explicit `NewTopic` `@Bean`s via `TopicBuilder` | `InventoryKafkaTopicConfig`'s own Javadoc states this is intentional so a broker without auto-create still works |
| Forward-only state-machine validation | Ad hoc `if/else` chains scattered across the controller | A single `isValidTransition(current, target)` helper colocated with the service (mirrors `TableOperationService.isValidTransition`) | Centralizes the D-02/D-04 "no skipping, no reverting" rule in one testable method per side (kitchen item transitions AND order-status derivation each need their own copy of this shape) |
| Admin RBAC for `/admin/orders/**` | New `SecurityConfig` rule | Nothing — already `hasAnyRole("ADMIN","STAFF")` | Explicitly confirmed already in place; touching `SecurityConfig` for this phase is out of scope |

**Key insight:** Every "hard part" of this phase (idempotency, poison-pill safety, after-commit
publish, pessimistic locking, forward-only guards) already has a working, tested implementation
somewhere in this exact repository. The research risk for this phase is not "does a good pattern
exist" — it's "did the planner correctly copy the RIGHT existing pattern instance for each new piece
instead of inventing a new shape."

## Common Pitfalls

### Pitfall 1: Double settle-trigger from a race on the SAME item

**What goes wrong:** Two concurrent `PATCH .../items/{itemId}/status` requests (staff double-tap, or
a retried HTTP call) both read the item as `QUEUED`, both pass the forward-only check, and both
publish `SettleTriggerEvent` — violating D-03's "fires exactly once."
**Why it happens:** A naive `@Transactional` service that does `findById` (no lock) → check status →
mutate → save has a read-then-write race window between two transactions at default (READ_COMMITTED)
isolation.
**How to avoid:** Lock the row with `@Lock(LockModeType.PESSIMISTIC_WRITE)` (Pattern 5) BEFORE the
status check, inside the same transaction that will mutate and publish. The second concurrent
transaction blocks until the first commits, then re-reads the NOW-`PREPARING` row and correctly
rejects the transition as illegal (item is no longer `QUEUED`).
**Warning signs:** A test that fires two concurrent advance calls for the same item and asserts
`SettleTriggerListener`/the settlement side only ever sees ONE trigger for that `(orderId,
orderLineId)` pair. (Phase 16 already has a durable SECOND guard — the
`inventory_line_settlements` unique `(order_id, order_line_id)` constraint — as defense in depth, but
kitchen should not rely solely on that; D-03 assigns kitchen the "fires exactly once" responsibility.)

### Pitfall 2: Publishing before the transaction commits

**What goes wrong:** If `SettleTriggerEvent`/`KitchenTicketStatusChangedEvent`/`OrderConfirmedEvent`
are published synchronously mid-transaction (not after commit) and the transaction later rolls back
(e.g. a later step in the same method throws), a phantom event fires for a state change that never
persisted — inventory would settle stock for an item that, from the DB's perspective, never actually
transitioned.
**Why it happens:** It is easy to just call `kafkaTemplate.send(...)` directly inside the
`@Transactional` method body instead of registering an after-commit callback.
**How to avoid:** Use Pattern 3 (`publishAfterCommit`) for EVERY new publish point in this phase,
exactly as `OrderSubmissionService` and `TableOperationService` already do. This is not optional
stylistic consistency — it is the only rollback-safety mechanism this codebase has (no outbox table).
**Warning signs:** Any `kafkaTemplate.send(...)` or `xxxEventPublisher.publish...(...)` call that is
NOT wrapped in the `TransactionSynchronizationManager` helper.

### Pitfall 3: `OrderStatus` backward-transition / silent overwrite guard

**What goes wrong:** Kafka does not guarantee in-order delivery across partitions for different keys,
and even same-key ordering can be violated by retries/DLT-then-manual-replay. If the order-side
consumer blindly applies whatever status the event says, a late-arriving `PREPARING` event
(reprocessed after a `READY` event already landed) would regress the order backward.
**Why it happens:** `OrderConfirmationService.onStockResult` today only guards a SINGLE fixed
transition (`PENDING_CONFIRMATION → CONFIRMED`) via an equality check
(`order.getStatus() != OrderStatus.PENDING_CONFIRMATION`). The NEW consumer must guard a
**multi-step, monotonic** sequence (`CONFIRMED→PREPARING→READY→SERVED→COMPLETED`), which needs an
ORDERING comparison, not an equality check.
**How to avoid:** Do not rely on raw `OrderStatus.ordinal()` unless the enum's declared order is
explicitly documented as load-bearing (a code comment on `OrderStatus.java` stating "declaration
order after CONFIRMED is semantically ordered — do not reorder" is the minimum safeguard). Safer:
define an explicit `private static final Map<OrderStatus, Integer> FULFILLMENT_RANK` (or a
`rank()` method on the enum) covering only `{CONFIRMED:0, PREPARING:1, READY:2, SERVED:3,
COMPLETED:4}`, and only apply the incoming status if `newRank > currentRank`. Also guard that the
order is not `REJECTED` (terminal, unrelated branch) before applying any fulfillment status.
**Warning signs:** A test asserting that redelivering an earlier ticket-status-changed event AFTER a
later one has already been applied does NOT regress `OrderEntity.status`.

### Pitfall 4: `totalLines` mismatch between kitchen and Phase 16's expectation

**What goes wrong:** D-03 defines `totalLines` = "the ticket's total item count." Phase 16's
`InventoryReservationSettlementService` uses `event.totalLines()` as the flip-to-`SETTLED` threshold
(`settledCount >= event.totalLines()`) — it must equal the count of DISTINCT order lines in the
ORIGINAL `OrderCreated`/reservation, not the kitchen ticket's item count filtered by any later state.
**Why it happens:** If `KitchenTicket` is built from `OrderConfirmed`'s line manifest and one ticket
item exists per order line (D-02: "one line-item per order line"), `ticket.getItems().size()` is
naturally equal to the reservation's line count — but only if kitchen creates ALL items up-front from
the full manifest at ticket-creation time (not lazily/partially). Confirm this invariant explicitly
when building the ticket.
**How to avoid:** Build the full `KitchenTicketItem` list from `OrderConfirmedEvent.lines()` in ONE
pass at ticket-creation time (mirroring how `StockReservationEntity.held(...)` builds all
`ReservationLine`s in one pass from the resolved recipe map). Never add/remove ticket items after
creation.
**Warning signs:** A test creating a ticket with N lines and asserting every subsequent
`SettleTriggerEvent.totalLines()` published for that ticket equals N, matching the reservation's line
count from the same order.

### Pitfall 5: Consumer group / topic property collisions across 3 contexts now publishing/consuming similar-sounding events

**What goes wrong:** This phase adds a 3rd Kafka producer/consumer pair (kitchen) on top of the
existing order↔inventory pair. Reusing group-id or bean names between contexts (e.g. naming a
`KafkaTemplate<String, Object>` bean `dltKafkaTemplate` without a qualifying prefix) causes Spring
ambiguous-bean-resolution failures at startup.
**Why it happens:** `OrderKafkaConsumerConfig`'s own Javadoc already flags this exact issue
(`orderDltKafkaTemplate`, explicitly named to avoid colliding with `inventoryDltKafkaTemplate`).
**How to avoid:** Name kitchen's DLT `KafkaTemplate<String, Object>` bean
`kitchenDltKafkaTemplate` and its consumer-factory/container-factory beans with a `kitchen`-prefixed
name (e.g. `orderConfirmedConsumerFactory`, `orderConfirmedKafkaListenerContainerFactory`) — never
reuse a bare name like `dltKafkaTemplate` or `consumerFactory`.
**Warning signs:** Spring context fails to start with `NoUniqueBeanDefinitionException` or
`@Qualifier` mismatch.

## Code Examples

### Kitchen's `SettleTriggerEvent` producer config (new file, cross-context import)

```java
// New file: kitchen_context/infrastructure/config/KitchenSettleTriggerProducerConfig.java
// Structural copy of InventoryKafkaProducerConfig, retyped — reuses the EXISTING event class.
package com.example.feat1.DDD.kitchen_context.infrastructure.config;

import com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent; // cross-context
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

@Configuration
public class KitchenSettleTriggerProducerConfig {
  @Bean
  public ProducerFactory<String, SettleTriggerEvent> settleTriggerProducerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
  }

  @Bean
  public KafkaTemplate<String, SettleTriggerEvent> settleTriggerKafkaTemplate(
      ProducerFactory<String, SettleTriggerEvent> settleTriggerProducerFactory) {
    return new KafkaTemplate<>(settleTriggerProducerFactory);
  }
}
```
The publishing call:
```java
kafkaTemplate.send(
    settleTriggerTopic, // ${kitchen.events.settle-trigger-topic:kitchen.settlement-trigger} — SAME property key as InventoryKafkaTopicConfig
    orderId.toString(), // key = orderId, matching every other producer in this saga
    new SettleTriggerEvent(
        UUID.randomUUID(), SettleTriggerEvent.TYPE, Instant.now(),
        orderId, orderLineId, ticket.getItems().size()));
```

### `OrderConfirmationService` — inserting the new publish (extends existing file)

```java
// Extends src/main/java/.../order_context/application/OrderConfirmationService.java
@Transactional
public void onStockResult(OrderStockResultEvent event) {
  // ... existing idempotency guard + load + status-guard unchanged ...
  if (event.result() == Result.CONFIRMED) {
    order.setStatus(OrderStatus.CONFIRMED);
    publishAfterCommit(toOrderConfirmedEvent(order)); // NEW — mirrors OrderSubmissionService's own helper
  } else {
    order.setStatus(OrderStatus.REJECTED);
    order.setRejectionReason(describe(event.shortfalls()));
  }
}

private void publishAfterCommit(OrderConfirmedEvent event) {
  if (!TransactionSynchronizationManager.isSynchronizationActive()) {
    orderEventPublisher.publishOrderConfirmed(event);
    return;
  }
  TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          orderEventPublisher.publishOrderConfirmed(event);
        }
      });
}
```
Note: `OrderConfirmationService` currently has NO `OrderEventPublisher` dependency injected — add it
via the constructor (Lombok `@RequiredArgsConstructor` already generates this once the field is
added).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Exact new class/topic/group-id names (`OrderConfirmedEvent`, `orders.confirmed`, `KitchenTicketStatusChangedEvent`, `kitchen.ticket-status-changed`, `kitchen-order-confirmed` group id, etc.) — CONTEXT.md leaves these to Claude's discretion | Architecture Patterns / Project Structure | Low — purely a naming choice, easy to rename later; no functional risk |
| A2 | Recommendation that the ticket-status-changed event carries a full per-item status snapshot (not just the single changed item) so order_context can derive aggregate status without querying kitchen | Open Questions #1 | Medium — if the planner instead has kitchen compute+send only the derived order-level status directly, D-04's "order_context derives" language is weakened (order becomes a passive setter, not a deriver); either approach is functionally viable but changes which side owns the derivation logic. Needs explicit sign-off. |
| A3 | `KitchenTicketItem` locked via `@Lock(PESSIMISTIC_WRITE)` on the item row (not the parent ticket row) is sufficient to prevent the double-settle-trigger race, assuming items are a separate `@Entity` (not an `@ElementCollection`) so they have their own lockable PK | Common Pitfalls #1, Project Structure | Medium — if the planner instead models `KitchenTicketItem` as an `@ElementCollection`/`@Embeddable` (like `OrderLineToppingSnapshot`), there is no per-item row to lock independently; the whole `KitchenTicketEntity` row would need locking instead, which is still correct but changes the repository method signature (`lockById` on ticket, not on item) |
| A4 | `spring-boot-starter-test`'s absence from `pom.xml` is compensated by Boot 4's modular test starters (`webmvc-test`, `security-test`, `security-oauth2-client-test`) transitively providing JUnit 5 / AssertJ / Mockito | Validation Architecture | Low — this is Boot 4's documented test-starter modularization; if wrong, `mvn test` would already be failing for every other phase, which it is not (STATE.md confirms 156 passing tests as of Phase 16) |

## Open Questions

1. **What exact payload does the ticket-status-changed event carry, so order_context can "derive"
   status without querying kitchen?**
   - What we know: D-04 requires order_context to derive PREPARING/READY/SERVED/COMPLETED from
     "the item states" and explicitly forbids kitchen mutating the Order aggregate. D-01 already
     established the precedent that a self-contained payload (full line manifest) is preferred over
     forcing the consumer to look anything up.
   - What's unclear: Whether "the item states" means (a) the ticket-status-changed event carries a
     full per-item status snapshot (`ticketId, orderId, items: [{orderLineId, status}, ...]`) every
     time ANY item changes, letting order_context recompute the aggregate purely from that one
     event, or (b) kitchen computes the aggregate order-level status itself and the event just says
     "set order status to X" (a thinner event, but then "derives" is really "kitchen decides, order
     applies" — arguably violating the spirit of D-04's boundary rule that order_context "derives").
   - Recommendation: Option (a) — full per-item snapshot in every event — most faithfully implements
     "order_context derives" (kitchen never computes an order-level verdict) and mirrors D-01's
     "self-contained, no cross-context lookup" philosophy. This is a planning decision, not a research
     gap; flag for `/gsd:discuss-phase` confirmation or explicit planner judgment call if not already
     locked.

2. **Does `KitchenTicketItem` need its own primary-key-bearing JPA `@Entity` (supporting an
   independent `@Lock`), or can it be an `@ElementCollection`/`@Embeddable` like
   `OrderLineToppingSnapshot`/`StockReservationEntity.ReservationLine`?**
   - What we know: Existing per-line collections in this codebase (`OrderLineToppingSnapshot`,
     `StockReservationEntity.ReservationLine`) are `@Embeddable` element collections, not separate
     entities — because they are never individually locked/queried outside their parent aggregate.
   - What's unclear: `KitchenTicketItem` IS individually mutated (each PATCH targets exactly one
     item) and needs its own stable, externally-addressable id (`{itemId}` in the URL path) and its
     own lock for the double-settle-trigger race (Pitfall 1) — both point toward a full `@Entity`
     (like `OrderLineEntity`, which IS its own entity with a `@ManyToOne` back to `OrderEntity`), not
     an embeddable.
   - Recommendation: Model `KitchenTicketItemEntity` as a full `@Entity` with `@ManyToOne` to
     `KitchenTicketEntity` (mirroring `OrderLineEntity`'s relationship to `OrderEntity` exactly, not
     `StockReservationEntity`'s embeddable-collection shape), since it needs an independent, lockable,
     URL-addressable identity that `OrderLineToppingSnapshot`/`ReservationLine` never needed.

## Environment Availability

No new external tool/service dependency is introduced by this phase — it reuses the already-running
MySQL + Kafka + Redis stack this project already depends on.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| MySQL (dev) | `KitchenTicket`/`KitchenTicketItem` persistence | Not probed (dev-only, developer's local instance per `application.properties`) | — | Tests run against H2 in-memory (`spring.jpa.hibernate.ddl-auto=create-drop`), same as every other context — no new fallback needed |
| Kafka broker | New producer/consumer wiring | Not probed (dev-only; `spring.kafka.listener.auto-startup=false` in test properties means tests never require a live broker) `[VERIFIED: src/test/resources/application.properties]` | — | Unit/config tests instantiate Kafka config classes directly with no Spring context (`[VERIFIED: SettleTriggerKafkaConsumerConfigTest.java]`) — a live broker is never required for this phase's test suite |

**Missing dependencies with no fallback:** None — this phase adds zero new environment
dependencies; the existing Kafka/MySQL/H2 setup fully covers it.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + AssertJ (via Boot 4's modular `webmvc-test`/`security-test` starters `[VERIFIED: pom.xml]`) |
| Config file | `src/test/resources/application.properties` — H2 in-memory MySQL-mode datasource, `ddl-auto=create-drop`, `spring.kafka.listener.auto-startup=false` |
| Quick run command | `./mvnw test -Dtest=KitchenTicketAdvanceServiceTest` (single class, seconds) |
| Full suite command | `./mvnw test` (156 tests passing as of Phase 16 per STATE.md) |

### Established two-tier test style (this codebase's actual convention, not a generic recommendation)

1. **Pure unit tests, no Spring context** — every `@Service`/`@Component` (e.g.
   `InventoryReservationSettlementServiceTest`) is instantiated directly with `mock(...)`
   repositories/ports via `@BeforeEach`, asserting exact business-logic outcomes (balances,
   idempotency short-circuits, exception cases). Kafka config classes (e.g.
   `SettleTriggerKafkaConsumerConfigTest`) are ALSO instantiated directly (`new
   SettleTriggerKafkaConsumerConfig()`), asserting bean property values (deserializer class,
   ack mode, `TRUSTED_PACKAGES`, not-retryable classification) — no live broker, no `@SpringBootTest`.
2. **`@SpringBootTest` + `@AutoConfigureMockMvc` + H2** — for controller/end-to-end flows (e.g.
   `InventoryStockIntegrationTest`), using
   `.with(user("staff").roles("STAFF"))` (Spring Security test support) to simulate RBAC, and
   `spring.kafka.listener.auto-startup=false` so listener beans exist but never actually poll a
   broker during the test run.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| D-01 | `OrderConfirmed` published exactly once per CONFIRMED transition, after commit | unit | `./mvnw test -Dtest=OrderConfirmationServiceTest` | ❌ Wave 0 (extend existing file) |
| D-01 | Kitchen's `OrderConfirmed` consumer creates exactly one `KitchenTicket` per order (idempotent replay) | unit | `./mvnw test -Dtest=KitchenTicketCreationServiceTest` | ❌ Wave 0 |
| D-02 | Item forward-only transitions; illegal (skip/revert) transitions rejected | unit | `./mvnw test -Dtest=KitchenTicketAdvanceServiceTest` | ❌ Wave 0 |
| D-03 | `SettleTriggerEvent` published exactly once on `QUEUED→PREPARING`, matches Phase 16 field shape | unit | `./mvnw test -Dtest=KitchenTicketAdvanceServiceTest` | ❌ Wave 0 |
| D-03 | Concurrent double-advance on the same item cannot double-publish the trigger | unit (Mockito `verify(..., times(1))` after simulating the lock) or integration | `./mvnw test -Dtest=KitchenTicketAdvanceServiceTest` | ❌ Wave 0 |
| D-04 | `OrderStatus` extended; order-side consumer derives status forward-only, idempotent | unit | `./mvnw test -Dtest=TicketStatusChangedListenerTest` / new order-side service test | ❌ Wave 0 |
| D-05 | `PATCH /admin/orders/{orderId}/items/{itemId}/status` — ADMIN/STAFF only, illegal transition → stable error | integration (`@SpringBootTest` + MockMvc) | `./mvnw test -Dtest=KitchenIntegrationTest` | ❌ Wave 0 |
| D-05 | `GET` kitchen-board endpoint — lists non-completed items, ADMIN/STAFF only | integration | `./mvnw test -Dtest=KitchenIntegrationTest` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** targeted `./mvnw test -Dtest=<ClassName>`
- **Per wave merge:** `./mvnw test` (full suite)
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `KitchenTicketCreationServiceTest.java` — covers D-01
- [ ] `KitchenTicketAdvanceServiceTest.java` — covers D-02/D-03
- [ ] New assertions in `OrderConfirmationServiceTest.java` (extend, do not replace) — covers D-01 publish side
- [ ] `TicketStatusChangedListenerTest.java` / order-side derivation service test — covers D-04
- [ ] `KitchenIntegrationTest.java` (MockMvc, mirrors `InventoryStockIntegrationTest` shape) — covers D-05
- [ ] `KitchenKafkaProducerConfigTest.java` / `OrderConfirmedKafkaConsumerConfigTest.java` /
      `TicketStatusChangedKafkaConsumerConfigTest.java` — broker-free wiring tests mirroring
      `SettleTriggerKafkaConsumerConfigTest.java`
- Framework install: none — all test infrastructure (JUnit 5, Mockito, AssertJ, H2, MockMvc, Spring
  Security test support) is already present and used by 15+ existing test classes.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No — new change | Already enforced globally by `JwtAuthenticationFilter` (unchanged) |
| V3 Session Management | No | Unchanged; JWT stateless, no session state introduced |
| V4 Access Control | Yes | `/admin/orders/**` already `hasAnyRole("ADMIN","STAFF")` in `SecurityConfig` — verified present, no change needed. The advance/board endpoints MUST be added under this exact path prefix, not a sibling path Spring Security wouldn't match. |
| V5 Input Validation | Yes | The advance endpoint's target-status body must be validated against the `KitchenItemStatus` enum (invalid/unknown string → 400, not a 500) and the forward-only transition check (Pattern 6) — mirror `TableDomainException.reservationStatusInvalid()`'s stable-error-code shape via a new `KitchenDomainException` |
| V6 Cryptography | No | No new secrets/crypto surface in this phase |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malicious/forged `__TypeId__` Kafka header attempting type confusion on deserialization | Tampering | Already-established pattern: `USE_TYPE_INFO_HEADERS=false` + fixed `VALUE_DEFAULT_TYPE` + `TRUSTED_PACKAGES` allow-list on every new consumer config (mirror `SettleTriggerKafkaConsumerConfig` exactly) |
| Poison-pill payload blocking a partition indefinitely | Denial of Service | `ErrorHandlingDeserializer` + `DefaultErrorHandler.addNotRetryableExceptions(DeserializationException.class)` routing straight to `.DLT` (established pattern, copy verbatim) |
| Staff/admin privilege confusion (STAFF performing an action intended ADMIN-only, or vice versa) | Elevation of Privilege | Not applicable here — D-05 explicitly grants BOTH roles equal access to both endpoints; no differential RBAC needed within `/admin/orders/**` for this phase |
| IDOR — advancing an item belonging to a DIFFERENT order via a mismatched `{orderId}`/`{itemId}` pair in the URL | Tampering / Information Disclosure | Mirror `OrderLineLookupAdapter`'s "keyed by BOTH orderId and orderLineId (cross-order collision defense)" pattern: the advance service MUST verify the `{itemId}` actually belongs to the ticket for `{orderId}` in the path, not just look up the item by its own PK alone |

## Sources

### Primary (HIGH confidence — direct codebase reads, this session)

- `src/main/java/com/example/feat1/DDD/inventory_context/application/event/SettleTriggerEvent.java` — exact contract record
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaTopicConfig.java` — topic bean conventions, settle-trigger default topic name
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/SettleTriggerKafkaConsumerConfig.java` + `SettleTriggerKafkaConsumerConfigTest.java` — consumer wiring + its broker-free test style
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/adapter/SettleTriggerListener.java` — thin-listener pattern
- `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationSettlementService.java` + its test — REQUIRES_NEW ledger, pessimistic lock ordering, clamp-to-zero, totalLines/last-line detection
- `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryLedgerWriter.java` — REQUIRES_NEW isolated ledger-writer component
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaProducerConfig.java` — proof of established cross-context event-class reuse (imports `order_context.application.event.OrderStockResultEvent`)
- `src/main/java/com/example/feat1/DDD/order_context/application/OrderConfirmationService.java` — exact insertion point for `OrderConfirmed` publish
- `src/main/java/com/example/feat1/DDD/order_context/application/OrderSubmissionService.java` (lines 184-238) — `publishAfterCommit` pattern, `toEvent` mapping style
- `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java`, `OrderEntity.java`, `OrderLineEntity.java`, `OrderLineToppingSnapshot.java`, `OrderProcessedEventEntity.java`, `OrderProcessedEventRepository.java` — entity/enum shapes to mirror
- `src/main/java/com/example/feat1/DDD/order_context/domain/port/OrderEventPublisher.java`, `infrastructure/adapter/KafkaOrderEventPublisher.java`, `infrastructure/config/OrderKafkaProducerConfig.java` — publisher port/adapter/config to extend
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/OrderStockResultListener.java`, `infrastructure/config/OrderKafkaConsumerConfig.java` — consumer pattern to mirror twice (kitchen's OrderConfirmed consumer; order's ticket-status-changed consumer)
- `src/main/java/com/example/feat1/DDD/order_context/application/event/OrderCreatedEvent.java`, `OrderStockResultEvent.java` — line-manifest record shape to reuse for `OrderConfirmedEvent`
- `src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/OrderLineLookupAdapter.java`, `domain/port/OrderLineLookupPort.java` — cross-context port/adapter convention + IDOR-style dual-key lookup
- `src/main/java/com/example/feat1/DDD/table_context/application/TableOperationService.java` (lines 237-265, 565-578) — PATCH-status-transition precedent + a SECOND independent `publishAfterCommit` implementation confirming the convention
- `src/main/java/com/example/feat1/DDD/table_context/infrastructure/presentation/TableOperationController.java` (line 98) — PATCH `.../status` controller shape
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/repository/StockReservationRepository.java` — `@Lock(PESSIMISTIC_WRITE)` precedent
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/StockReservationEntity.java`, `InventoryLineSettlementEntity.java` — aggregate/child-record entity shapes, unique-constraint conventions
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java` — confirms `/admin/orders/**` already `hasAnyRole("ADMIN","STAFF")`
- `src/main/java/com/example/feat1/DDD/identity_context/application/dto/RoleEnum.java` — role set (`ADMIN/USER/MANAGER/STAFF`)
- `src/main/java/com/example/feat1/common/exception/AppException.java`, `GlobalExceptionHandler.java` — stable-error-code exception convention
- `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderDomainException.java` — concrete example of the stable-error-code pattern
- `src/main/resources/application.properties`, `src/test/resources/application.properties` — no Flyway (Hibernate `ddl-auto=update` in dev, `create-drop` on H2 in test); Kafka bootstrap/topic property conventions
- `pom.xml` — Spring Boot 4.0.6, Java 17, `spring-kafka`, no explicit test-starter dependency (Boot 4 modular test starters), zero Flyway dependency
- `src/test/java/.../inventory_context/application/InventoryReservationSettlementServiceTest.java`, `.../infrastructure/config/SettleTriggerKafkaConsumerConfigTest.java`, `.../inventory_context/integration/InventoryStockIntegrationTest.java` — the 3 concrete test styles in active use

### Secondary (MEDIUM confidence)

- None — no external web sources were needed; this phase is 100% "reuse existing internal
  conventions," and every claim was verifiable directly against the codebase.

### Tertiary (LOW confidence)

- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new libraries; every class/config referenced was read directly from
  the repository this session.
- Architecture: HIGH for everything with a direct precedent (Kafka wiring, idempotency ledger,
  after-commit publish, PATCH-status pattern, DDD layout); MEDIUM for the two genuinely novel design
  choices flagged in Open Questions (exact ticket-status-changed payload shape; entity-vs-embeddable
  for `KitchenTicketItem`) since these have no exact precedent to copy, only closely analogous ones.
- Pitfalls: HIGH — all 5 pitfalls are derived from either an explicit Javadoc warning already in the
  codebase (WR-01 REQUIRES_NEW, DLT bean-naming collision) or a directly observed structural gap
  (no existing multi-step forward-only status guard; no existing per-item pessimistic lock).

**Research date:** 2026-07-08
**Valid until:** No expiry driver — this research is entirely internal-codebase-derived (not
version/ecosystem-dependent); it remains valid as long as `order_context`/`inventory_context`'s
Phase 15/16 code is not itself refactored. Re-verify only if Phase 15/16 files change before Phase 17
planning begins.
