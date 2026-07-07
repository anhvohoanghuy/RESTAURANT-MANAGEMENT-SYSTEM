---
phase: 15-kafka-event-consumers-for-ordercreated-and-payment-events-wi
verified: 2026-07-07T00:00:00Z
status: passed
score: 19/19 must-haves verified
has_blocking_gaps: false
overrides_applied: 0
re_verification:
  previous_status: none
  note: initial verification
deferred:
  - truth: "Payment/Table Kafka producers migrated off the legacy Jackson-2 JsonSerializer"
    addressed_in: "future fix (out of scope; payments consumer deferred by D-07)"
    evidence: "deferred-items.md 15-01 — pre-existing Instant-serialization defect in OTHER bounded contexts, not caused by this phase; payments consumer explicitly out of scope (D-07)"
---

# Phase 15: Kafka event consumers — order-confirmation saga Verification Report

**Phase Goal:** Add Kafka consumer infrastructure and an order-confirmation saga: an order is created in `PENDING_CONFIRMATION`, Inventory consumes `OrderCreated`, verifies availability (`available = on_hand − reserved`) and reserves (never negative) or rejects, then publishes a result event Order Context consumes to reach `CONFIRMED`/`REJECTED`. Idempotent (processed-events ledger, eventId) with `DefaultErrorHandler` + DLT. Deduction and `payments.events` consumer deferred.
**Verified:** 2026-07-07
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Order submission creates order in PENDING_CONFIRMATION synchronously | ✓ VERIFIED | `OrderSubmissionService.submit` L58 `setStatus(PENDING_CONFIRMATION)`; `OrderEntity` L37 default `PENDING_CONFIRMATION`; `OrderStatus` enum has SUBMITTED+PENDING_CONFIRMATION+CONFIRMED+REJECTED (SUBMITTED retained for legacy rows) |
| 2 | Order submission publishes OrderCreatedEvent after commit (starts saga) | ✓ VERIFIED | `OrderSubmissionService` L81 `publishAfterCommit(toEvent(...))`, L225-234 `TransactionSynchronization.afterCommit` → `orderEventPublisher.publishOrderCreated` |
| 3 | Shared OrderStockResultEvent contract exists (both contexts serialize/deserialize) | ✓ VERIFIED | `order_context.application.event.OrderStockResultEvent` record with Result enum, Shortfall record, CONFIRMED_TYPE/REJECTED_TYPE; imported by both Inventory service and Order service |
| 4 | Events survive real serde round-trip | ✓ VERIFIED | `EventSerdeRoundTripTest` uses real Jackson-3 `JacksonJsonSerializer`→`JacksonJsonDeserializer` (matches production serde) |
| 5 | Inventory consumes OrderCreated and delegates to reservation service | ✓ VERIFIED | `OrderCreatedListener` `@KafkaListener(topics=orders.created, containerFactory=orderCreatedKafkaListenerContainerFactory)` → `reservationService.onOrderCreated(event)` (thin delegate) |
| 6 | Availability computed as on_hand − reserved on pessimistic-locked rows | ✓ VERIFIED | `InventoryReservationService` L117 `available = getQuantityOnHand().subtract(getReservedQuantity())`; `InventoryStockBalanceRepository.lockByIngredientAndLocation` `@Lock(PESSIMISTIC_WRITE)` |
| 7 | Reserve if sufficient (never negative), else reject with shortfalls | ✓ VERIFIED | L125-152: empty shortfalls → increment `reservedQuantity` + save `StockReservationEntity.held`, emit CONFIRMED; else emit REJECTED with `List.copyOf(shortfalls)` |
| 8 | Required qty resolves dish + selected-topping recipes × line qty, unit-converted; missing recipe → zero, non-blocking | ✓ VERIFIED | `computeRequired`/`accumulateRecipe` L165-222: `menuRecipeCostingPort.findRecipe(DISH/TOPPING_OPTION)`, `UnitConverter.convert`, missing recipe/ingredient/unconvertible → continue (zero) |
| 9 | Idempotent against duplicate OrderCreated (eventId + orderId) | ✓ VERIFIED | L78-95: `existsByEventIdAndConsumerName` OR `reservationRepository.existsByOrderId`, then `saveAndFlush` ledger with `DataIntegrityViolationException` catch; unique `uq_stock_reservation_order(order_id)` |
| 10 | Balance rows locked in canonical sorted order (deadlock-safe) | ✓ VERIFIED | L102 `sortedIngredientIds = required.keySet().stream().sorted().toList()` reused for both check and reserve loops |
| 11 | Result event published only after reservation tx commits | ✓ VERIFIED | L155/231-243 `publishAfterCommit` registers `afterCommit()` synchronization |
| 12 | Inventory publishes OrderStockResultEvent keyed by orderId | ✓ VERIFIED | `KafkaInventoryStockResultPublisher.publishStockResult` → `kafkaTemplate.send(topic, orderId.toString(), event)` implementing port |
| 13 | Order consumes OrderStockResultEvent and delegates to confirmation service | ✓ VERIFIED | `OrderStockResultListener` `@KafkaListener(topics=inventory.order-stock-results)` → `confirmationService.onStockResult(event)` |
| 14 | Idempotent PENDING_CONFIRMATION → CONFIRMED transition | ✓ VERIFIED | `OrderConfirmationService.onStockResult` L41-72: ledger guard + status guard (`!= PENDING_CONFIRMATION` returns), CONFIRMED sets status |
| 15 | REJECTED transition records shortfall reason; terminal | ✓ VERIFIED | L68-71 sets REJECTED + `setRejectionReason(describe(shortfalls))`; `OrderEntity.rejection_reason` column present |
| 16 | Both consumers use ErrorHandlingDeserializer → Jackson-3 JacksonJsonDeserializer | ✓ VERIFIED | Both configs: `VALUE_DESERIALIZER=ErrorHandlingDeserializer`, `ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS=JacksonJsonDeserializer`, TRUSTED_PACKAGES + VALUE_DEFAULT_TYPE + USE_TYPE_INFO_HEADERS=false |
| 17 | AckMode.RECORD + DefaultErrorHandler + DeadLetterPublishingRecoverer (retry then DLT) | ✓ VERIFIED | Both configs: `setAckMode(RECORD)`, `DefaultErrorHandler(new DeadLetterPublishingRecoverer(dlt), FixedBackOff(1000,3))`, `addNotRetryableExceptions(DeserializationException.class)` |
| 18 | NewTopic beans exist for result + both DLT topics | ✓ VERIFIED | `InventoryKafkaTopicConfig`: NewTopic for `inventory.order-stock-results`, `orders.created.DLT`, `inventory.order-stock-results.DLT` |
| 19 | Jackson-3-native serde, no new dependencies added | ✓ VERIFIED | All producer/consumer configs use `JacksonJsonSerializer/Deserializer`; `pom.xml` unchanged since Phase 10 (spring-kafka already present, no new coordinates) |

**Score:** 19/19 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `order_context/domain/model/OrderStatus.java` | 4-value enum | ✓ VERIFIED | SUBMITTED, PENDING_CONFIRMATION, CONFIRMED, REJECTED |
| `order_context/application/event/OrderStockResultEvent.java` | shared saga contract | ✓ VERIFIED | Record + Result enum + Shortfall + TYPE constants |
| `order_context/application/OrderConfirmationService.java` | idempotent status-guarded handler | ✓ VERIFIED | 90 lines, ledger + status guard |
| `order_context/infrastructure/entity/OrderProcessedEventEntity.java` | order idempotency ledger | ✓ VERIFIED | unique (event_id, consumer_name) |
| `order_context/infrastructure/config/OrderKafkaConsumerConfig.java` | consumer factory + DLT | ✓ VERIFIED | ErrorHandlingDeserializer + Jackson-3 + DLT |
| `order_context/infrastructure/adapter/OrderStockResultListener.java` | thin @KafkaListener | ✓ VERIFIED | one-line delegate |
| `inventory_context/application/InventoryReservationService.java` | reservation saga handler | ✓ VERIFIED | 248 lines, atomic/idempotent/deadlock-safe/after-commit |
| `inventory_context/domain/port/InventoryStockResultPublisher.java` | outbound port | ✓ VERIFIED | publishStockResult |
| `inventory_context/infrastructure/adapter/KafkaInventoryStockResultPublisher.java` | port impl | ✓ VERIFIED | @Component, keyed send (resolves 15-06 deferred bean gap) |
| `inventory_context/infrastructure/entity/InventoryStockBalanceEntity.java` | reservedQuantity | ✓ VERIFIED | reserved_quantity NOT NULL default 0 |
| `inventory_context/infrastructure/entity/StockReservationEntity.java` | per-order reservation | ✓ VERIFIED | unique uq_stock_reservation_order(order_id) |
| `inventory_context/infrastructure/entity/InventoryProcessedEventEntity.java` | inventory ledger | ✓ VERIFIED | unique (event_id, consumer_name) |
| `inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java` | consumer + DLT | ✓ VERIFIED | ErrorHandlingDeserializer + Jackson-3 + DLT |
| `inventory_context/infrastructure/config/InventoryKafkaTopicConfig.java` | NewTopic beans | ✓ VERIFIED | 3 NewTopic beans |
| `inventory_context/infrastructure/adapter/OrderCreatedListener.java` | thin @KafkaListener | ✓ VERIFIED | one-line delegate |

### Key Link Verification

| From | To | Via | Status |
|------|----|----|--------|
| OrderSubmissionService.submit | OrderStatus.PENDING_CONFIRMATION | setStatus | ✓ WIRED |
| OrderSubmissionService | orderEventPublisher.publishOrderCreated | afterCommit | ✓ WIRED |
| OrderCreatedListener.onOrderCreated | InventoryReservationService.onOrderCreated | delegation | ✓ WIRED |
| InventoryReservationService | InventoryStockBalanceRepository.lockByIngredientAndLocation | PESSIMISTIC_WRITE, sorted order | ✓ WIRED |
| InventoryReservationService.computeRequired | MenuRecipeCostingPort.findRecipe + UnitConverter.convert | recipe reuse | ✓ WIRED |
| InventoryReservationService | InventoryStockResultPublisher.publishStockResult | afterCommit | ✓ WIRED |
| KafkaInventoryStockResultPublisher | inventory.order-stock-results | kafkaTemplate.send(topic, orderId, event) | ✓ WIRED |
| OrderStockResultListener.onStockResult | OrderConfirmationService.onStockResult | delegation | ✓ WIRED |
| OrderConfirmationService.onStockResult | OrderRepository.findById + setStatus guarded by PENDING_CONFIRMATION | status transition | ✓ WIRED |

### Locked-Decision Coverage (D-01..D-11)

| Decision | Status | Evidence |
|----------|--------|----------|
| D-01 order created PENDING_CONFIRMATION | ✓ | OrderStatus + OrderEntity default + submit |
| D-02 stock never negative (available=on_hand−reserved) | ✓ | reservation service L117-118, pessimistic lock |
| D-03 processed-events ledger by eventId (both consumers) | ✓ | Inventory + Order ProcessedEventEntity, unique (event_id,consumer_name) |
| D-04 DefaultErrorHandler + DLT | ✓ | both consumer configs, DeadLetterPublishingRecoverer |
| D-05 @EnableKafka consumer factory + container factory + group-id + typed JSON deser | ✓ | both consumer configs |
| D-06 required = dish + topping recipes × qty, unit-converted, missing→zero | ✓ | computeRequired/accumulateRecipe |
| D-07 payments.events consumer out of scope | ✓ | no payment consumer added |
| D-08 POST /orders synchronous PENDING; final state async | ✓ | submit returns PENDING; listener transitions later |
| D-09 reserved quantity + per-order reservation for Phase-16 settlement | ✓ | reservedQuantity column + StockReservationEntity.held |
| D-10 result event published, Order consumes → CONFIRMED/REJECTED | ✓ | publisher + OrderStockResultListener + confirmation service |
| D-11 insufficient → REJECTED terminal with reason | ✓ | confirmation service rejection path + rejectionReason |

### Behavioral Spot-Checks

Skipped live-broker execution by design: the saga's test suite runs broker-free (`spring.kafka.listener.auto-startup=false`) and the parent reports the full suite green at 138/0/0. Handler logic is unit-covered (`InventoryReservationServiceTest`, `OrderConfirmationServiceTest`), serde by `EventSerdeRoundTripTest`, and wiring by broker-free `InventoryKafkaConsumerConfigTest` / `OrderKafkaConsumerConfigTest`. Each saga link was verified statically above.

### Deferred Items

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | Payment/Table Kafka producers still use legacy Jackson-2 JsonSerializer | Future dedicated fix (out of scope) | deferred-items.md 15-01 — pre-existing Instant-serialization defect in OTHER bounded contexts; payments consumer explicitly deferred (D-07). Not caused by or blocking Phase 15. |
| 2 | 15-06 worktree context-load failure (missing InventoryStockResultPublisher bean) | Resolved by wave-3 merge | `KafkaInventoryStockResultPublisher` @Component now present on master; was a pre-merge worktree artifact only. Full suite green post-merge. |

### Anti-Patterns Found

None blocking. No unreferenced TBD/FIXME/XXX debt markers in phase files. Listeners are thin delegates (no logic leak). No stub returns, no hardcoded-empty rendering. The two deferred items are documented, out-of-scope, and non-goal-blocking.

### Gaps Summary

No goal-blocking gaps. The end-to-end order-confirmation saga is fully present and wired in code across both bounded contexts:
PENDING_CONFIRMATION order + OrderCreated publish → Inventory consumer → pessimistic-locked availability (on_hand−reserved) → reserve/reject with idempotency → after-commit result publish → Order consumer → idempotent status-guarded CONFIRMED/REJECTED. Kafka wiring on both sides matches D-04/D-05 (ErrorHandlingDeserializer → Jackson-3 native serde, AckMode.RECORD, DefaultErrorHandler + DeadLetterPublishingRecoverer, NewTopic beans). No new dependencies (pom.xml unchanged since Phase 10). All 11 locked decisions honored; D-07 (payments consumer) correctly out of scope.

Recommendation (non-blocking, follow-up): a live-broker end-to-end smoke test (produce OrderCreated → observe CONFIRMED, and an under-stock order → REJECTED with reason) would exercise the real message round-trip that broker-free tests cannot; and payment/table producers should be migrated to Jackson-3 in a dedicated fix before those flows publish `Instant`-bearing events at runtime.

---

_Verified: 2026-07-07_
_Verifier: Claude (gsd-verifier)_
