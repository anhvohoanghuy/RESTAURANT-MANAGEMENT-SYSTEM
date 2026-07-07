---
phase: 15-kafka-event-consumers-for-ordercreated-and-payment-events-wi
reviewed: 2026-07-07T00:00:00Z
depth: standard
files_reviewed: 31
files_reviewed_list:
  - src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java
  - src/main/java/com/example/feat1/DDD/inventory_context/domain/port/InventoryStockResultPublisher.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/adapter/KafkaInventoryStockResultPublisher.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/adapter/OrderCreatedListener.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaProducerConfig.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaTopicConfig.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryProcessedEventEntity.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryStockBalanceEntity.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/StockReservationEntity.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/repository/InventoryProcessedEventRepository.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/repository/InventoryStockBalanceRepository.java
  - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/repository/StockReservationRepository.java
  - src/main/java/com/example/feat1/DDD/order_context/application/OrderConfirmationService.java
  - src/main/java/com/example/feat1/DDD/order_context/application/OrderSubmissionService.java
  - src/main/java/com/example/feat1/DDD/order_context/application/event/OrderStockResultEvent.java
  - src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/OrderStockResultListener.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaConsumerConfig.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaProducerConfig.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/entity/OrderEntity.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/entity/OrderProcessedEventEntity.java
  - src/main/java/com/example/feat1/DDD/order_context/infrastructure/repository/OrderProcessedEventRepository.java
  - src/main/resources/application.properties
  - src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationServiceTest.java
  - src/test/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaConsumerConfigTest.java
  - src/test/java/com/example/feat1/DDD/order_context/EventSerdeRoundTripTest.java
  - src/test/java/com/example/feat1/DDD/order_context/application/OrderConfirmationServiceTest.java
  - src/test/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaConsumerConfigTest.java
  - src/test/java/com/example/feat1/DDD/order_context/integration/OrderSubmissionIntegrationTest.java
  - src/test/resources/application.properties
findings:
  critical: 0
  warning: 5
  info: 5
  total: 10
status: issues_found
---

# Phase 15: Code Review Report

**Reviewed:** 2026-07-07
**Depth:** standard
**Files Reviewed:** 31
**Status:** issues_found

## Summary

Reviewed the Kafka-based order-confirmation saga spanning the order and inventory
bounded contexts. The core mechanics the phase called out are largely sound:
pessimistic locks are acquired in canonical ascending-`ingredientId` order for both
the availability check and the reserve loop (deadlock avoidance holds), the
`ErrorHandlingDeserializer` + non-retryable `DeserializationException` + forced
`VALUE_DEFAULT_TYPE` + `TRUSTED_PACKAGES` allow-list is correctly wired for
poison-pill/type-confusion safety, `AckMode.RECORD` gives at-least-once, and the
Jackson-3 serde round-trips `Instant`/`BigDecimal`/nested records (verified by
`EventSerdeRoundTripTest`). Result publishing is correctly deferred to `afterCommit`.

No hard BLOCKER was proven — the idempotency mechanism is eventually correct because
Kafka redelivers identical bytes (same `eventId`) and the ledger unique constraint plus
pessimistic locks prevent double-reservation. However, several correctness-of-mechanism
and durability defects are worth fixing before this ships as a reliable saga: the
duplicate-catch idiom leaves the JPA transaction rollback-only (so the documented "clean
skip" actually throws and relies on retry), the result event has a lost-on-crash
durability gap that permanently strands an order in `PENDING_CONFIRMATION`, Kafka sends
are fire-and-forget with no failure handling, and the `rejection_reason` column can
overflow on multi-ingredient shortages.

Convention rule packs (JS/TS) do not apply — all sources are `.java`/`.properties`, so
they skip gracefully (D-05). No CONVENTION-tier findings emitted.

## Warnings

### WR-01: Duplicate-catch leaves the transaction rollback-only — documented "clean skip" instead throws

**File:** `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java:83-95`
**Also:** `src/main/java/com/example/feat1/DDD/order_context/application/OrderConfirmationService.java:44-53`
**Issue:** Both services do `saveAndFlush(ledger)` inside a `try` and catch
`DataIntegrityViolationException`, then `return` to "treat as a replay." With
`JpaTransactionManager`, a constraint violation surfaced during flush marks the
underlying Hibernate transaction **rollback-only**. Catching the exception and returning
normally does not produce a clean commit — when the `@Transactional` method returns,
Spring attempts to commit a rollback-only transaction and throws
`UnexpectedRollbackException`. So on a genuine concurrent duplicate the handler does not
"skip silently" as the comment claims; it throws out to the listener, the
`DefaultErrorHandler` retries, and only on the *retry* does the fast pre-check
(`existsByEventIdAndConsumerName`) short-circuit cleanly. Net outcome is eventually
correct (self-healing via redelivery), but the primary idempotency path emits spurious
errors/retries on first collision and behaves differently from its own documentation.
**Fix:** Isolate the ledger insert so its failure does not poison the outer transaction —
e.g. move the insert+catch into a separate bean method annotated
`@Transactional(propagation = Propagation.REQUIRES_NEW)`, or pre-insert the ledger row in
its own transaction before opening the reservation transaction. Then the duplicate path
returns on a clean, committable transaction.

### WR-02: Result-event durability gap strands orders in PENDING_CONFIRMATION on crash

**File:** `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java:154-155,231-243`
**Issue:** The `OrderStockResultEvent` is published in `afterCommit`. If the process
crashes (or the broker is unreachable) after the reservation transaction commits but
before/while the Kafka send completes, the result event is lost. Because the reservation
row and ledger row are already committed, a Kafka redelivery of the same `OrderCreated`
hits the idempotency guard (`existsByOrderId` / `existsByEventIdAndConsumerName`) and is
skipped — the result is **never re-emitted**. The order remains in
`PENDING_CONFIRMATION` permanently with stock reserved, and the terminal
`OrderConfirmationService` never fires. This is the classic dual-write gap between the DB
commit and the message send.
**Fix:** Use a transactional outbox (persist the outbound event in the same transaction,
relay it separately) so the result is derivable from committed state. If "no new
dependencies" forbids an outbox library, at minimum persist a lightweight pending-result
row in the reservation transaction and add a relay/scheduled republisher, and document the
residual risk explicitly. If the after-commit-only approach is an accepted phase tradeoff,
record it as a known limitation.

### WR-03: Fire-and-forget Kafka send — failures silently dropped, not logged

**File:** `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/adapter/KafkaInventoryStockResultPublisher.java:29-31`
**Issue:** `orderStockResultKafkaTemplate.send(...)` returns a `CompletableFuture` that is
ignored. An async send failure (broker down, serialization error, timeout) is neither
logged nor surfaced — it is silently swallowed. Combined with WR-02, a failed send after a
committed reservation produces an invisible stranded order with no operational signal.
**Fix:** Attach a callback and log/alert on failure, e.g.
`template.send(...).whenComplete((res, ex) -> { if (ex != null) log.error("Failed to publish stock result for order {}", event.orderId(), ex); })`.
Apply the same treatment to the order-side publisher for consistency.

### WR-04: `rejection_reason` can overflow the default column length on multi-ingredient shortages

**File:** `src/main/java/com/example/feat1/DDD/order_context/application/OrderConfirmationService.java:74-89`
**Also:** `src/main/java/com/example/feat1/DDD/order_context/infrastructure/entity/OrderEntity.java:39-40`
**Issue:** `describe()` concatenates one clause per shortfall
(`"<name> (required X.XXXXXX, available Y.YYYYYY)"`, roughly 40-60 chars each) into
`rejectionReason`, which maps to `@Column(name = "rejection_reason")` with no explicit
length — Hibernate defaults to `VARCHAR(255)`. An order rejected for more than ~4-5 short
ingredients produces a string exceeding 255 chars. On the production MySQL datasource this
either truncates or (in strict mode) throws a data-truncation `DataIntegrityViolation`,
failing the status transition — the transaction rolls back, the record retries, and the
order can end up on the DLT while stuck in `PENDING_CONFIRMATION`.
**Fix:** Bound the reason string (truncate to a safe length with an ellipsis, or cap the
number of listed shortfalls) and/or widen the column (`@Column(length = 1024)` or
`columnDefinition = "TEXT"`). Truncate defensively in `describe()` regardless of column
width.

### WR-05: Global `spring.kafka.producer.value-serializer=JsonSerializer` (Jackson 2) contradicts the phase's Jackson-3 decision

**File:** `src/main/resources/application.properties:26`
**Issue:** The phase repeatedly justifies using the Jackson-3 `JacksonJsonSerializer`
because the legacy Jackson-2 `JsonSerializer` cannot serialize `java.time.Instant` on this
Boot 4 / Jackson 3 classpath (no `jackson-datatype-jsr310`). Yet the global default
producer value-serializer is still `org.springframework.kafka.support.serializer.JsonSerializer`
(the Jackson-2 class). The explicit `ProducerFactory`/`KafkaTemplate` beans in this phase
build their own prop maps and are unaffected, but Boot's auto-configured default
`KafkaTemplate` inherits this setting — any current or future publisher that uses the
default template to emit an `Instant`-bearing event will fail at runtime with the exact
serialization error the phase went out of its way to avoid. This is a latent landmine that
directly conflicts with the phase's own serde rationale (line is pre-existing context but
lives in a changed file and interacts dangerously with the new producers).
**Fix:** Align the global default with the phase decision — set
`spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer`,
or remove the global override so each context supplies its own serializer explicitly.

## Info

### IN-01: `OrderStatus.SUBMITTED` appears unused / legacy

**File:** `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java:4`
**Issue:** New orders default to `PENDING_CONFIRMATION` (`OrderEntity:37`,
`OrderSubmissionService:58`) and the saga only transitions to `CONFIRMED`/`REJECTED`.
`SUBMITTED` is never assigned in the reviewed flow, suggesting dead/legacy state.
**Fix:** Confirm whether any other flow produces `SUBMITTED`; if not, remove it or document
its intended use to avoid ambiguity in status guards.

### IN-02: Redundant `balanceRepository.save(entity)` on an already-managed entity

**File:** `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java:130-132`
**Issue:** The balance entity was loaded via `lockByIngredientAndLocation` in the same
transaction and is JPA-managed; mutating `reservedQuantity` is persisted by dirty checking
at flush. The explicit `save(entity)` is a no-op call. Not a bug, but misleading about
persistence semantics.
**Fix:** Drop the `save` call, or keep it only if a defensive re-attach is intended and say
so in a comment.

### IN-03: `@EnableKafka` declared on two configuration classes

**File:** `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java:44`
**Also:** `src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaConsumerConfig.java:52`
**Issue:** `@EnableKafka` is idempotent when declared multiple times, so this is harmless,
but the annotation is conventionally applied once (app root or a single Kafka config).
Duplicating it invites confusion about where listener infrastructure is enabled.
**Fix:** Consider consolidating `@EnableKafka` onto a single config class or the main
application class.

### IN-04: Unit tests exercise the publish path synchronously, not after-commit

**File:** `src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationServiceTest.java:85,97`
**Issue:** The tests call `service.onOrderCreated(...)` with no active transaction, so
`TransactionSynchronizationManager.isSynchronizationActive()` is `false` and
`publishAfterCommit` publishes inline. The after-commit ordering guarantee (publish only
after the reservation commits, D-10) is therefore not actually asserted by any test.
**Fix:** Add a test that runs within a transaction (or wraps a `TransactionTemplate`) and
verifies no publish occurs until commit, to lock in the after-commit contract.

### IN-05: DLT topics fixed at `partitions(1)` while source partition count is unmanaged

**File:** `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaTopicConfig.java:42-49`
**Issue:** `orders.created.DLT` and `inventory.order-stock-results.DLT` are declared with a
single partition, but the live `orders.created` topic's partition count is not managed here.
`DeadLetterPublishingRecoverer`'s default resolver targets the same partition number as the
original record; when the DLT has fewer partitions it falls back to letting Kafka choose, so
this works, but the mismatch means DLT records lose partition co-location with the source and
relies on the fallback behavior.
**Fix:** Match DLT partition counts to their source topics, or document the intentional
single-partition DLT design.

---

_Reviewed: 2026-07-07_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
