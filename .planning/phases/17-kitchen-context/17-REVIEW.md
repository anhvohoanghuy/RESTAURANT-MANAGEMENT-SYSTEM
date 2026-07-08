---
phase: 17-kitchen-context
reviewed: 2026-07-08T00:00:00Z
depth: standard
files_reviewed: 43
files_reviewed_list:
  - src/main/java/com/example/feat1/DDD/kitchen_context/domain/model/KitchenItemStatus.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/domain/model/KitchenDomainException.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/domain/port/KitchenSettleTriggerPublisher.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/domain/port/KitchenTicketStatusChangedPublisher.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/application/event/KitchenTicketStatusChangedEvent.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/application/dto/AdvanceItemStatusRequest.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/application/dto/KitchenItemResponse.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/application/dto/KitchenBoardItemResponse.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketCreationService.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceService.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenBoardService.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/entity/KitchenTicketEntity.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/entity/KitchenTicketItemEntity.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/entity/KitchenTicketItemToppingSnapshot.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/entity/KitchenProcessedEventEntity.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketRepository.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketItemRepository.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenProcessedEventRepository.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenKafkaTopicConfig.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/OrderConfirmedKafkaConsumerConfig.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenSettleTriggerProducerConfig.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenTicketStatusChangedProducerConfig.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/OrderConfirmedListener.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/KafkaKitchenSettleTriggerPublisher.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/KafkaKitchenTicketStatusChangedPublisher.java
  - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/presentation/KitchenController.java
  - src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java
  - src/main/java/com/example/feat1/DDD/order_context/application/event/OrderConfirmedEvent.java
  - src/main/java/com/example/feat1/DDD/order_context/application/OrderConfirmationService.java
  - src/main/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionService.java
  - src/main/java/com/example/feat1/DDD/order_context/domain/port/OrderEventPublisher.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/KafkaOrderEventPublisher.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/TicketStatusChangedListener.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaProducerConfig.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/TicketStatusChangedKafkaConsumerConfig.java
  - src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceServiceTest.java
  - src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketCreationServiceTest.java
  - src/test/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenKafkaProducerConfigTest.java
  - src/test/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/OrderConfirmedKafkaConsumerConfigTest.java
  - src/test/java/com/example/feat1/DDD/kitchen_context/integration/KitchenIntegrationTest.java
  - src/test/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionServiceTest.java
  - src/test/java/com/example/feat1/DDD/order_context/application/OrderConfirmationServiceTest.java
  - src/test/java/com/example/feat1/DDD/order_context/infrastructure/config/TicketStatusChangedKafkaConsumerConfigTest.java
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: issues_found
---

# Phase 17: Code Review Report

**Reviewed:** 2026-07-08
**Depth:** standard
**Files Reviewed:** 43
**Status:** issues_found

## Summary

Reviewed the new `kitchen_context` bounded context (domain model, application services,
JPA entities/repositories, Kafka producer/consumer configs, adapters, controller) plus the
`order_context` extensions (`KitchenStatusProjectionService`, `TicketStatusChangedListener` and
its consumer config), against the Phase 15/16 consumer/producer patterns and the reused inventory
`SettleTriggerEvent` contract.

The core high-risk mechanics are sound and I found no BLOCKER. Specifically I verified:

- **Race prevention (D-03):** `KitchenTicketItemRepository.lockByOrderIdAndItemId` takes a
  `PESSIMISTIC_WRITE` lock *before* the forward-only status check, so a concurrent second advance
  serializes and re-reads the already-mutated status → the `QUEUED→PREPARING` settle trigger fires
  at most once. Correct.
- **Cross-context contract reuse:** kitchen imports (never redeclares) inventory's
  `SettleTriggerEvent`; `totalLines = ticket.getItems().size()` equals the order-line count, which
  is exactly what `InventoryReservationSettlementService` compares against
  (`settledCount >= event.totalLines()`), and inventory has its own dual idempotency guard
  (eventId ledger + per-`(orderId,orderLineId)` settlement row) so a duplicate kitchen trigger is
  safely absorbed. Consistent.
- **Order-status forward-only rank guard:** `deriveTargetStatus` + `FULFILLMENT_RANK` only advances
  the order, REJECTED is short-circuited as terminal, and out-of-order snapshots cannot regress the
  order. Logic traced clean.
- **IDOR / access control:** `/admin/orders/**` is `hasAnyRole("ADMIN","STAFF")` in `SecurityConfig`
  (confirmed), and the dual-key lock query enforces that `itemId` must belong to `orderId`, closing
  the cross-order item-reach threat. The integration test covers anonymous/USER denial.
- **Idempotency + poison-pill/DLT wiring:** ledger pre-check + `saveAndFlush` + catch-`DIVE`,
  `ErrorHandlingDeserializer` with forced type / trusted-package allow-list, `USE_TYPE_INFO_HEADERS=false`,
  not-retryable deserialization → DLT, `AckMode.RECORD`. All present and mirror Phase 16.

Remaining findings are robustness/quality issues, not correctness failures.

## Warnings

### WR-01: Fire-and-forget Kafka send makes the settle-trigger guarantee "at most once," and drops failures silently

**File:** `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/KafkaKitchenSettleTriggerPublisher.java:19-21`
(same pattern in `KafkaKitchenTicketStatusChangedPublisher.java:21-24`)

**Issue:** `KitchenTicketAdvanceService` documents the settle trigger as published "exactly once,"
and the lock+forward-only mechanism does guarantee it is never published *more* than once. But the
publish is deferred to `afterCommit`, and the adapter calls `settleTriggerKafkaTemplate.send(...)`
and ignores the returned `CompletableFuture`. If the broker send fails (broker down, timeout,
serialization error) — or the JVM dies after the DB commit but before/inside the `afterCommit`
callback — the settle trigger is lost with no exception surfaced, no retry, and no log. Downstream,
inventory then never settles that order line: the reservation for it stays held indefinitely and
the stock is never deducted, silently diverging kitchen/inventory state. So the real guarantee is
*at most once*, not *exactly once*, and failures are unobservable. (This mirrors the pre-existing
`KafkaOrderEventPublisher` fire-and-forget style, so it is a known architecture-wide tradeoff — but
the settlement loop makes the consequence material here.)

**Fix:** At minimum observe the send result so failures are not silent; ideally back the settle
trigger with the same durability guarantee inventory expects (transactional outbox / retry).
```java
settleTriggerKafkaTemplate.send(settleTriggerTopic, event.orderId().toString(), event)
    .whenComplete((result, ex) -> {
      if (ex != null) {
        log.error("Settle trigger publish FAILED order={} line={} — reservation may never settle",
            event.orderId(), event.orderLineId(), ex);
        // TODO: enqueue for retry / outbox replay
      }
    });
```

### WR-02: Authenticated actor is discarded — no audit trail of who advanced a kitchen item

**File:** `src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceService.java:38-39`
(caller: `KitchenController.java:38` passes `principal.getId()`)

**Issue:** `advance(...)` accepts `UUID actorId` (wired from the authenticated `CustomUserDetails`),
but the parameter is never read anywhere in the method body. For a staff-operated state-machine
mutation, the identity of the operator is exactly the kind of thing that should be recorded, and
the inventory context already models an `actorId` on stock movements. As written the value is
silently dropped: the parameter is either dead weight or a missing audit record.

**Fix (recommend):** Either persist the actor on the transition (e.g., an `advancedBy`/movement/audit
column, matching `InventoryStockMovementEntity.setActorId`), or, if audit is intentionally out of
scope for this phase, drop the parameter from the service signature so the discard is not silent.

### WR-03: Rank guard's `-1` default lets a non-fulfillment order status be overwritten with a fulfillment status

**File:** `src/main/java/com/example/feat1/DDD/order_context/application/KitchenStatusProjectionService.java:95-101`

**Issue:** `currentRank = FULFILLMENT_RANK.getOrDefault(order.getStatus(), -1)` returns `-1` for any
status not in the map (`SUBMITTED`, `PENDING_CONFIRMATION`). Since every derived target rank is
`>= 0`, the guard `targetRank <= currentRank` never trips for those states, so the projection would
happily jump such an order straight to `PREPARING`/`READY`/etc., skipping `CONFIRMED` entirely. The
current happy-path invariant (a ticket exists only after the order is `CONFIRMED`) means this is not
reachable today, but the guard is meant to be the defensive backstop against redelivery/reordering,
and here it fails open rather than closed for unexpected input.

**Fix:** Only project when the order is already at a known fulfillment rank, e.g.:
```java
int currentRank = FULFILLMENT_RANK.getOrDefault(order.getStatus(), -1);
if (currentRank < 0 || targetRank <= currentRank) {
  return; // never advance an order that isn't in a fulfillment-eligible state
}
```

## Info

### IN-01: Asymmetric duplicate handling — a same-order OrderConfirmed under a new eventId goes to the DLT instead of being absorbed

**File:** `src/main/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketCreationService.java:41-63`

**Issue:** A redelivered event with the *same* `eventId` is absorbed by the ledger, and a concurrent
duplicate ledger insert is swallowed. But a second `OrderConfirmed` for the same `orderId` under a
*different* `eventId` passes the ledger check and then hits the `uq_kitchen_ticket_order` unique
constraint on `kitchenTicketRepository.save(ticket)`, throwing an uncaught
`DataIntegrityViolationException` that retries 3× and lands on `orders.confirmed.DLT` rather than
being recognized as a benign duplicate. Given order_context's status guard this should not happen in
practice, so the unique constraint is a valid backstop — but the handling is inconsistent with the
otherwise idempotent design and produces noisy DLT traffic.

**Fix:** Catch the constraint violation around the ticket save (or pre-check via the already-declared
`existsByOrderId`) and treat "ticket already exists for this order" as an idempotent no-op.

### IN-02: Dead repository methods

**File:** `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketRepository.java:10-12`

**Issue:** `existsByOrderId(UUID)` and `findByOrderId(UUID)` are declared but never called anywhere
in `src/main` or `src/test`. `existsByOrderId` is the natural pre-check that IN-01 wants; `findByOrderId`
has no consumer at all.

**Fix:** Wire `existsByOrderId` into the creation guard (see IN-01) or remove both to avoid dead
surface area.

### IN-03: Pessimistic lock query locks across a join

**File:** `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/repository/KitchenTicketItemRepository.java:23-25`

**Issue:** `lockByOrderIdAndItemId` filters on `i.ticket.orderId`, which forces Hibernate to join
`kitchen_tickets`; a `SELECT ... FOR UPDATE` over a join locks rows from the joined table too (DB
dependent) and on some engines is rejected without a `FOR UPDATE OF` clause. Functionally correct on
PostgreSQL, but it widens the lock footprint to the parent ticket beyond what the comment implies
("lock on the item row").

**Fix (recommend):** Lock by `i.id` alone and validate `orderId` after load, or add a lock scope hint
so only the item row is locked.

### IN-04: Advance service's after-commit deferral path is untested

**File:** `src/test/java/com/example/feat1/DDD/kitchen_context/application/KitchenTicketAdvanceServiceTest.java:52-74`

**Issue:** The tests run without an active `TransactionSynchronizationManager`, so they only exercise
the synchronous fallback branch of `publishSettleTriggerAfterCommit` / `publishStatusChangedAfterCommit`.
The production path — deferral to `afterCommit` — is never asserted, unlike `OrderConfirmationServiceTest`
which explicitly verifies "registered, not run mid-transaction, runs on afterCommit."

**Fix:** Add a test mirroring `OrderConfirmationServiceTest.confirmedResultRegistersPublishAsAfterCommitSynchronizationNotMidTransaction`
(init synchronization, assert not published mid-tx, drive `afterCommit`, assert published once).

---

_Reviewed: 2026-07-08_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
