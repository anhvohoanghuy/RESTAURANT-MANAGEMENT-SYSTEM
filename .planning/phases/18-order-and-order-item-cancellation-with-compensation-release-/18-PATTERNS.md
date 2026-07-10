# Phase 18: Order & order-item cancellation with compensation - Pattern Map

**Mapped:** 2026-07-10
**Files analyzed:** 24 (new + modified, across order_context, inventory_context, payment_context, kitchen_context, shared/outbox)
**Analogs found:** 22 / 24 (2 in "No Analog Found" - Payment's first-ever consumer infra has no in-context precedent; cross-context port is a novel direction)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `order_context/domain/model/OrderStatus.java` (MODIFY: append CANCELLED) | model (enum) | n/a | itself (existing file) | exact |
| `order_context/domain/model/OrderDomainException.java` (MODIFY: new error codes) | model (exception factory) | n/a | itself (existing file) | exact |
| `order_context/domain/port/KitchenItemStatusPort.java` (NEW) | port (cross-context read) | request-response | `inventory_context/domain/port/OrderLineLookupPort.java` | role-match (same "consumer owns the port interface" convention, opposite direction) |
| `kitchen_context/infrastructure/adapter/KitchenItemStatusAdapter.java` (NEW, implements the port above) | adapter | request-response | `order_context/infrastructure/adapter/OrderLineLookupAdapter.java` | exact (dual-key lookup convention) |
| `order_context/application/event/OrderCancelledEvent.java` (NEW) | event (outbox payload) | event-driven | `order_context/application/event/OrderConfirmedEvent.java` / `inventory_context/application/event/SettleTriggerEvent.java` | exact |
| `order_context/application/OrderCancellationService.java` (NEW) | service | CRUD (status transition) + event-driven (outbox publish) | `order_context/application/OrderSubmissionService.java` (outbox-write idiom) + `kitchen_context/application/KitchenTicketAdvanceService.java` (lock-then-transition idiom) | role-match (composite of two analogs) |
| `order_context/infrastructure/repository/OrderRepository.java` (MODIFY: add `lockById`) | repository | CRUD | `inventory_context/infrastructure/repository/StockReservationRepository.lockByOrderId` / `kitchen_context/infrastructure/repository/KitchenTicketItemRepository.lockByOrderIdAndItemId` | exact |
| `order_context/infrastructure/entity/OrderLineEntity.java` (MODIFY: add nullable `cancelledAt`) | model (entity) | CRUD | itself (existing file) - mirrors `OrderEntity.rejectionReason` nullable-descriptive-field idiom | exact |
| `order_context/infrastructure/presentation/OrderController.java` (MODIFY: add cancel endpoints, customer-facing) | controller | request-response | itself (existing file) | exact |
| `order_context/infrastructure/presentation/AdminOrderCancellationController.java` (NEW, or extend an existing admin controller) | controller | request-response | `kitchen_context/infrastructure/presentation/KitchenController.java` (`/admin/orders/{orderId}/items/{itemId}/status` - no class-level security annotation needed, `/admin/orders/**` already `hasAnyRole` in SecurityConfig) | exact |
| `order_context/application/KitchenStatusProjectionService.java` (MODIFY: extend REJECTED-terminal guard to include CANCELLED) | service (consumer, terminal-guard) | event-driven | itself (existing file, line 94 guard) | exact |
| `inventory_context/application/InventoryReservationReleaseService.java` (NEW) | service (consumer) | event-driven | `inventory_context/application/InventoryReservationSettlementService.java` | exact (this IS the structural inverse) |
| `inventory_context/infrastructure/adapter/OrderCancelledInventoryListener.java` (NEW) | adapter (thin Kafka delegate) | event-driven | `inventory_context/infrastructure/adapter/SettleTriggerListener.java` | exact |
| `inventory_context/infrastructure/config/OrderCancelledKafkaConsumerConfig.java` (NEW) | config (Kafka consumer wiring) | event-driven | `inventory_context/infrastructure/config/SettleTriggerKafkaConsumerConfig.java` | exact |
| `inventory_context/infrastructure/entity/InventoryLineReleaseEntity.java` (NEW) | model (ledger entity) | CRUD | `inventory_context/infrastructure/entity/InventoryLineSettlementEntity.java` | exact |
| `inventory_context/infrastructure/repository/InventoryLineReleaseRepository.java` (NEW) | repository | CRUD | `inventory_context/infrastructure/repository/InventoryLineSettlementRepository.java` | exact |
| `inventory_context/infrastructure/entity/StockReservationEntity.java` (MODIFY: add `ReservationStatus.RELEASED`) | model (entity/enum) | n/a | itself (existing file) | exact |
| `inventory_context/domain/model/InventoryMovementType.java` (MODIFY: add `RESERVATION_RELEASE`) | model (enum) | n/a | itself (existing file) | exact |
| `payment_context/infrastructure/entity/PaymentProcessedEventEntity.java` (NEW - Payment's FIRST ledger) | model (ledger entity) | CRUD | `inventory_context/infrastructure/entity/InventoryProcessedEventEntity.java` (cross-context structural twin; `kitchen_context/infrastructure/entity/KitchenProcessedEventEntity.java` is an equally exact second twin) | exact (cross-context, no in-payment precedent) |
| `payment_context/infrastructure/repository/PaymentProcessedEventRepository.java` (NEW) | repository | CRUD | `inventory_context/infrastructure/repository/InventoryProcessedEventRepository.java` | exact |
| `payment_context/infrastructure/adapter/OrderCancelledPaymentListener.java` (NEW - Payment's FIRST consumer) | adapter (thin Kafka delegate) | event-driven | `kitchen_context/infrastructure/adapter/OrderConfirmedListener.java` (simplest thin-delegate consumer template) | role-match (cross-context, no in-payment precedent) |
| `payment_context/infrastructure/config/OrderCancelledPaymentKafkaConsumerConfig.java` (NEW - Payment's FIRST consumer config) | config (Kafka consumer wiring) | event-driven | `order_context/infrastructure/config/TicketStatusChangedKafkaConsumerConfig.java` (cross-context trusted-package idiom - consuming an event class owned by ANOTHER context, exactly like Payment consuming `OrderCancelledEvent`) | exact |
| `payment_context/application/PaymentAutoRefundService.java` (NEW) | service (consumer) | event-driven | `order_context/application/OrderConfirmationService.java` (ledger-pre-check + status-guard + ledger-last-in-tx idiom) reusing `PaymentService.recordRefund` as the business-logic call | role-match (composite: idempotency shape from OrderConfirmationService, refund math from PaymentService itself) |
| `payment_context/infrastructure/entity/PaymentRefundEntity.java` (MODIFY: `actorUserId` becomes nullable) | model (entity) | n/a | `inventory_context/application/InventoryReservationSettlementService.java` line 187 (`movement.setActorId(null)` precedent for "system-triggered, no human actor") | exact (precedent, not a file analog) |
| `kitchen_context/application/KitchenTicketInvalidationService.java` (NEW, recommended per Pitfall 2) | service (consumer) | event-driven | `inventory_context/application/InventoryReservationSettlementService.java` (ledger-pre-check idiom) + `kitchen_context/application/KitchenTicketAdvanceService.java` (lock-then-mutate item idiom) | role-match (composite) |
| `kitchen_context/infrastructure/adapter/OrderCancelledKitchenListener.java` (NEW) | adapter (thin Kafka delegate) | event-driven | `kitchen_context/infrastructure/adapter/OrderConfirmedListener.java` | exact |
| `kitchen_context/infrastructure/config/OrderCancelledKitchenKafkaConsumerConfig.java` (NEW) | config (Kafka consumer wiring) | event-driven | `kitchen_context/infrastructure/config/OrderConfirmedKafkaConsumerConfig.java` (unread but structurally identical to `TicketStatusChangedKafkaConsumerConfig`) | exact |

## Pattern Assignments

### `order_context/application/OrderCancellationService.java` (NEW service)

**Analogs:** `order_context/application/OrderSubmissionService.java` (outbox-write + status-set idiom) and `kitchen_context/application/KitchenTicketAdvanceService.java` (lock-row-first-then-transition idiom).

**Row-locking pattern to add on `OrderRepository`** (mirrors `StockReservationRepository.lockByOrderId`, `src/main/java/.../inventory_context/infrastructure/repository/StockReservationRepository.java` lines 17-23):
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select r from StockReservationEntity r where r.orderId = :orderId")
Optional<StockReservationEntity> lockByOrderId(UUID orderId);
```
Add the same shape to `OrderRepository`: `@Lock(PESSIMISTIC_WRITE) @Query("select o from OrderEntity o where o.id = :id") Optional<OrderEntity> lockById(UUID id);`

**Dual-key IDOR-safe lookup convention** (mirrors `KitchenTicketItemRepository.lockByOrderIdAndItemId`, `src/main/java/.../kitchen_context/infrastructure/repository/KitchenTicketItemRepository.java` lines 23-25) - use for any per-line lock/lookup needed during partial cancel.

**Outbox-write-in-same-tx pattern** (verbatim shape to copy, `OrderSubmissionService.java` lines 82-90):
```java
OrderEntity saved = orderRepository.save(order);
...
outboxWriter.save(
    "ORDER",
    saved.getId(),
    OrderCreatedEvent.TYPE,
    orderCreatedTopic,
    saved.getId().toString(),
    toEvent(response));
```
For cancellation: call `outboxWriter.save("ORDER", order.getId(), OrderCancelledEvent.TYPE, orderCancelledTopic, order.getId().toString(), event)` in the SAME `@Transactional` method as the status/line mutation - never `afterCommit`/`KafkaTemplate.send()` directly (see Anti-Patterns in RESEARCH.md).

**Ownership check idiom** (mirrors `OrderSubmissionService.getOrder`, lines 100-106):
```java
@Transactional(readOnly = true)
public SubmittedOrderResponse getOrder(UUID userId, UUID orderId) {
  return orderRepository
      .findByIdAndUserId(orderId, userId)
      .map(this::toResponse)
      .orElseThrow(OrderDomainException::orderNotFound);
}
```
Customer-cancel path reuses `orderRepository.findByIdAndUserId(orderId, userId)` (already exists on `OrderRepository`, line 12) → `orElseThrow(OrderDomainException::orderNotFound)` (404, not 403 - avoids confirming order existence, IDOR mitigation already used project-wide).

**Status-window guard idiom** (mirrors `OrderConfirmationService.onStockResult`, lines 65-73):
```java
if (order.getStatus() != OrderStatus.PENDING_CONFIRMATION) {
  return;
}
```
For cancel: `if (!EnumSet.of(SUBMITTED, PENDING_CONFIRMATION, CONFIRMED).contains(order.getStatus())) { throw OrderDomainException.cancelWindowClosed(); }` (D-1).

**Cross-context race-safety read** - new port to add, modeled directly on `OrderLineLookupPort`/`OrderLineLookupAdapter` (see below), but pointing the OTHER direction (order_context reads kitchen truth). Call the port INSIDE the same locked transaction, immediately before committing cancellation (RESEARCH Pitfall 1).

---

### `order_context/domain/port/KitchenItemStatusPort.java` (NEW port) + adapter in kitchen_context

**Analog:** `inventory_context/domain/port/OrderLineLookupPort.java` (full file, 16 lines) - copy the exact "consuming context owns the interface, producing context implements the adapter" convention:
```java
// Source: src/main/java/.../inventory_context/domain/port/OrderLineLookupPort.java
public interface OrderLineLookupPort {
  Optional<OrderLineRecipeSnapshot> findLine(UUID orderId, UUID orderLineId);
}
```
New port (owned by order_context, package `order_context.domain.port`):
```java
public interface KitchenItemStatusPort {
  Optional<KitchenItemStatus> findStatus(UUID orderId, UUID orderLineId);
  // or bulk: Map<UUID, KitchenItemStatus> findStatuses(UUID orderId);
}
```

**Adapter analog** (`order_context/infrastructure/adapter/OrderLineLookupAdapter.java`, full file, 39 lines) - copy the `@Component` + `@Transactional(readOnly = true)` + dual-key-lookup-then-map-to-narrow-snapshot shape:
```java
@Component
@RequiredArgsConstructor
public class OrderLineLookupAdapter implements OrderLineLookupPort {
  private final OrderLineRepository orderLineRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<OrderLineRecipeSnapshot> findLine(UUID orderId, UUID orderLineId) {
    return orderLineRepository
        .findByOrder_IdAndId(orderId, orderLineId)
        .map(line -> new OrderLineRecipeSnapshot(...));
  }
}
```
New adapter lives in `kitchen_context.infrastructure.adapter.KitchenItemStatusAdapter`, implements `order_context`'s port, backed by `KitchenTicketItemRepository` keyed on `orderLineId` (already has `ticket.orderId` join per `lockByOrderIdAndItemId`'s query shape - add a plain non-locking `findByTicket_OrderIdAndOrderLineId` or similar read query).

---

### `inventory_context/application/InventoryReservationReleaseService.java` (NEW - the inverse of settlement)

**Analog:** `inventory_context/application/InventoryReservationSettlementService.java` (full file, 225 lines) - this is THE structural template; copy the shape end-to-end and invert two things: (a) decrement `reservedQuantity` ONLY, never `quantityOnHand`; (b) movement type `RESERVATION_RELEASE` instead of `CONSUMPTION`.

**Idempotency guard idiom** (lines 88-96, copy verbatim shape, substitute a new `InventoryLineReleaseRepository.existsByOrderIdAndOrderLineId`):
```java
if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)
    || lineReleaseRepository.existsByOrderIdAndOrderLineId(orderId, orderLineId)) {
  log.debug("Skipping already-released trigger eventId={} orderId={} orderLineId={}", ...);
  return;
}
```
Use idiom 1 (ledger-insert-last-in-same-tx, majority pattern) per RESEARCH Pattern 4 recommendation, NOT the isolated `InventoryLedgerWriter.tryInsert` REQUIRES_NEW idiom (that is settlement-only; release is a pure same-database JPA write with no non-transactional side effect to protect).

**Re-resolve, never read aggregated lines** (lines 106-113, verbatim reuse - zero modification needed):
```java
OrderLineRecipeSnapshot line =
    orderLineLookupPort
        .findLine(orderId, orderLineId)
        .orElseThrow(() -> InventoryDomainException.releaseOrderLineMissing(orderId, orderLineId));
Map<UUID, BigDecimal> required = resolveLineRequirements(line); // same private method, copy verbatim
```

**Lock reservation, then balances in ascending-ingredientId order, clamp non-negative** (lines 115-190, invert the on-hand decrement out):
```java
StockReservationEntity reservation =
    reservationRepository.lockByOrderId(orderId)
        .orElseThrow(() -> InventoryDomainException.releaseReservationMissing(orderId));
if (reservation.getStatus() != ReservationStatus.HELD) { return; } // benign redelivery
...
for (UUID ingredientId : sortedIngredientIds) {
  ...
  BigDecimal newReserved = balance.getReservedQuantity().subtract(need);
  if (newReserved.compareTo(BigDecimal.ZERO) < 0) { newReserved = BigDecimal.ZERO; } // clamp, log.warn
  balance.setReservedQuantity(scale(newReserved));
  // do NOT touch balance.setQuantityOnHand(...) - that's the settlement-only step
  ...
  movement.setMovementType(InventoryMovementType.RESERVATION_RELEASE); // NEW enum value
  movement.setActorId(null); // system-triggered, no human actor (same precedent as line 187)
}
```

**Generalized count-then-flip completion guard** (lines 199-205, widen the denominator per RESEARCH Pattern 2):
```java
InventoryLineReleaseEntity release = new InventoryLineReleaseEntity();
release.setOrderId(orderId);
release.setOrderLineId(orderLineId);
release.setReleasedAt(now);
lineReleaseRepository.save(release);

long settledCount = lineSettlementRepository.countByOrderId(orderId);
long releasedCount = lineReleaseRepository.countByOrderId(orderId);
if (settledCount + releasedCount >= event.totalLines()) {
  reservation.setStatus(ReservationStatus.RELEASED); // for pure whole-order-cancel (0% settled, 100% released)
  // NOTE: mixed case (some settled, some released) should stay HELD until the settlement service's
  // OWN flip-to-SETTLED check (also widened per Pattern 2) fires on the last remaining line.
}
```

---

### `inventory_context/infrastructure/entity/InventoryLineReleaseEntity.java` (NEW)

**Analog:** `inventory_context/infrastructure/entity/InventoryLineSettlementEntity.java` (full file, 45 lines) - copy verbatim, rename fields:
```java
@Entity
@Table(
    name = "inventory_line_releases",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_inventory_line_release", columnNames = {"order_id", "order_line_id"}))
public class InventoryLineReleaseEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "order_id", nullable = false) private UUID orderId;
  @Column(name = "order_line_id", nullable = false) private UUID orderLineId;
  @Column(name = "released_at", nullable = false) private Instant releasedAt;
}
```
Repository analog (`InventoryLineSettlementRepository.java`, 14 lines - copy verbatim, rename methods):
```java
public interface InventoryLineReleaseRepository extends JpaRepository<InventoryLineReleaseEntity, UUID> {
  boolean existsByOrderIdAndOrderLineId(UUID orderId, UUID orderLineId);
  long countByOrderId(UUID orderId);
}
```

---

### `inventory_context/infrastructure/adapter/OrderCancelledInventoryListener.java` (NEW thin delegate)

**Analog:** `inventory_context/infrastructure/adapter/SettleTriggerListener.java` (full file, 29 lines) - copy the "thin Kafka adapter, zero business logic" shape verbatim:
```java
@Component
@RequiredArgsConstructor
public class OrderCancelledInventoryListener {
  private final InventoryReservationReleaseService releaseService;

  @KafkaListener(
      topics = "${order.events.order-cancelled-topic:orders.cancelled}",
      groupId = "${inventory.release.consumer.group-id:inventory-release}",
      containerFactory = "orderCancelledInventoryKafkaListenerContainerFactory")
  public void onOrderCancelled(OrderCancelledEvent event) {
    releaseService.onOrderCancelled(event);
  }
}
```

---

### `inventory_context/infrastructure/config/OrderCancelledKafkaConsumerConfig.java` (NEW)

**Analog:** `inventory_context/infrastructure/config/SettleTriggerKafkaConsumerConfig.java` (full file, 97 lines) - copy bean-for-bean, substituting event class/topic/group-id. Key excerpt (consumer factory props, lines 46-66):
```java
@Bean
public ConsumerFactory<String, OrderCancelledEvent> orderCancelledInventoryConsumerFactory(
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
    @Value("${inventory.release.consumer.group-id:inventory-release}") String groupId) {
  Map<String, Object> props = new HashMap<>();
  props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
  props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
  props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
  props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
  props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
  props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
  props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
  props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES,
      "com.example.feat1.DDD.order_context.application.event"); // CROSS-CONTEXT: event class lives in order_context
  props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, OrderCancelledEvent.class.getName());
  props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
  return new DefaultKafkaConsumerFactory<>(props);
}
```
DLT wiring (lines 68-95, copy verbatim, only rename beans and `@Qualifier`):
```java
@Bean
public DefaultErrorHandler orderCancelledInventoryErrorHandler(
    @Qualifier("inventoryDltKafkaTemplate") KafkaTemplate<String, Object> dlt) {
  DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dlt);
  DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
  handler.addNotRetryableExceptions(DeserializationException.class);
  return handler;
}
```
(Reuses the EXISTING `inventoryDltKafkaTemplate` bean already registered by `InventoryKafkaConsumerConfig` - do not create a new DLT template inside inventory_context, only Payment needs a brand-new one since it has none today.)

---

### `payment_context/infrastructure/config/OrderCancelledPaymentKafkaConsumerConfig.java` (NEW - Payment's FIRST consumer, no in-context precedent)

**Analog:** `order_context/infrastructure/config/TicketStatusChangedKafkaConsumerConfig.java` (full file, 122 lines) - THIS is the best analog specifically because it is ALSO a cross-context consumer (order_context consuming an event class owned by kitchen_context), so its `TRUSTED_PACKAGE` + dedicated-DLT-template pattern maps directly onto Payment consuming `OrderCancelledEvent` (owned by order_context). Copy the WHOLE file shape including the dedicated DLT `KafkaTemplate` bean (lines 81-89) since Payment has NO existing DLT template to reuse (unlike Inventory/Kitchen, which already have one from prior phases):
```java
@Bean
public KafkaTemplate<String, Object> orderCancelledPaymentDltKafkaTemplate(
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
  Map<String, Object> props = new HashMap<>();
  props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
  props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
  props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
  return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
}
```
`@EnableKafka` note: `TicketStatusChangedKafkaConsumerConfig` declares `@EnableKafka` itself (line 47) because it is order_context's FIRST/only such config needing it at the time; Payment likewise has NO existing `@EnableKafka`-bearing config (`PaymentKafkaProducerConfig.java` is producer-only, no `@EnableKafka`) - so this NEW config MUST declare `@Configuration @EnableKafka` itself (do not assume it exists elsewhere in payment_context, unlike inventory_context/kitchen_context where a sibling config already carries it).

---

### `payment_context/infrastructure/adapter/OrderCancelledPaymentListener.java` (NEW - Payment's FIRST consumer)

**Analog:** `kitchen_context/infrastructure/adapter/OrderConfirmedListener.java` (full file, 29 lines) - simplest possible thin-delegate template, also cross-context (kitchen consuming order_context's event):
```java
@Component
@RequiredArgsConstructor
public class OrderCancelledPaymentListener {
  private final PaymentAutoRefundService autoRefundService;

  @KafkaListener(
      topics = "${order.events.order-cancelled-topic:orders.cancelled}",
      groupId = "${payment.order-cancelled.consumer.group-id:payment-order-cancelled}",
      containerFactory = "orderCancelledPaymentKafkaListenerContainerFactory")
  public void onOrderCancelled(OrderCancelledEvent event) {
    autoRefundService.onOrderCancelled(event);
  }
}
```

---

### `payment_context/infrastructure/entity/PaymentProcessedEventEntity.java` (NEW - Payment's FIRST idempotency ledger)

**Analog:** `inventory_context/infrastructure/entity/InventoryProcessedEventEntity.java` (full file, 46 lines) - copy verbatim, rename table/class (the `kitchen_processed_events` table is an equally valid twin - all three sibling ledgers are byte-identical in shape):
```java
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
Repository analog (`InventoryProcessedEventRepository.java`, 12 lines - copy verbatim):
```java
public interface PaymentProcessedEventRepository extends JpaRepository<PaymentProcessedEventEntity, UUID> {
  boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
```

---

### `payment_context/application/PaymentAutoRefundService.java` (NEW)

**Analog (idempotency/status-guard shape):** `order_context/application/OrderConfirmationService.java` (full file, 163 lines) - copy the pre-check + business-logic + ledger-last idiom (lines 57-97):
```java
@Transactional
public void onOrderCancelled(OrderCancelledEvent event) {
  if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
    return;
  }
  if (!event.wholeOrder()) {
    return; // D-3/D-6: auto-refund gated strictly on whole-order cancel (Pitfall 3 / Open Q1)
  }
  // ... business logic (refund iteration below) ...
  recordProcessed(event.eventId()); // ledger row LAST, same transaction
}
```

**Analog (refund iteration + amount math):** `payment_context/application/PaymentService.java` `summarizeTotals` (lines 239-267) for "amount already paid" computation, and `recordRefund` (lines 107-143) as the REUSED business call (do not duplicate its overpay-guard logic - RESEARCH "Don't Hand-Roll" table):
```java
// New service calls the EXISTING PaymentService.recordRefund per payment, per RESEARCH Code Examples:
List<PaymentEntity> payments = paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
for (PaymentEntity payment : payments) {
  BigDecimal alreadyRefunded = payment.getRefunds().stream()
      .map(PaymentRefundEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
  BigDecimal remaining = payment.getAmount().subtract(alreadyRefunded);
  if (remaining.compareTo(BigDecimal.ZERO) > 0) {
    String idempotencyKey = "auto-cancel-" + event.orderId() + "-" + payment.getId();
    paymentService.recordRefund(null /* system actor, D-A5 */, payment.getId(),
        new RecordRefundRequest(remaining, idempotencyKey, "Order cancelled"));
  }
}
```
`recordRefund`'s OWN existing dedup guard (`refundRepository.findByPayment_IdAndIdempotencyKey`, `PaymentService.java` lines 115-119) is a SECOND independent idempotency layer beneath the new `payment_processed_events` ledger - do not remove or bypass it.

**Required entity change** - `PaymentRefundEntity.actorUserId` (`PaymentRefundEntity.java` line 45-46) must become nullable:
```java
// BEFORE: @Column(name = "actor_user_id", nullable = false) private UUID actorUserId;
// AFTER:  @Column(name = "actor_user_id") private UUID actorUserId;  // nullable for system refunds
```

---

### `kitchen_context/application/KitchenTicketInvalidationService.java` (NEW, recommended per Pitfall 2)

**Analog (item lock + guarded mutation):** `kitchen_context/application/KitchenTicketAdvanceService.java` lines 37-73 - lock the item row FIRST (mirrors `itemRepository.lockByOrderIdAndItemId`), then apply a guarded mutation only if the item hasn't already progressed:
```java
KitchenTicketItemEntity item =
    itemRepository.lockByOrderIdAndItemId(orderId, itemId).orElseThrow(...);
if (item.getStatus() != KitchenItemStatus.QUEUED) {
  return; // NEVER touch an item already >= PREPARING (defense-in-depth, mirrors rank-guard idiom)
}
// either delete the row (safe direction per orphanRemoval discussion) or set a status if the enum
// gains CANCELLED (append-only, per KitchenItemStatus's own load-bearing ordinal-ordering Javadoc)
```
**Analog (idempotency ledger pre-check):** `inventory_context/application/InventoryReservationSettlementService.java` lines 88-96 shape, substituting `kitchen_processed_events`/`KitchenProcessedEventRepository` (already exists, `KitchenProcessedEventEntity.java` full file above).

---

## Shared Patterns

### Idempotent-consumer ledger (ALL 3 new consumers)
**Source:** `inventory_context/infrastructure/entity/InventoryProcessedEventEntity.java` (structural template); `order_context/application/OrderConfirmationService.java` lines 59-96 (usage idiom: pre-check → business logic → ledger-insert-LAST-in-same-transaction).
**Apply to:** `InventoryReservationReleaseService`, `PaymentAutoRefundService`, `KitchenTicketInvalidationService`.
```java
if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) { return; }
// ... business work ...
recordProcessed(eventId); // LAST statement in the @Transactional method
```
Use idiom 1 (ledger-last-in-tx) for all three new consumers per RESEARCH Pattern 4 - NOT the isolated `InventoryLedgerWriter`/`REQUIRES_NEW` idiom, which is reserved for settlement's specific non-transactional-side-effect risk.

### Transactional outbox (order_context publish side)
**Source:** `shared/outbox/application/OutboxWriter.java` (full file, 55 lines).
**Apply to:** `OrderCancellationService` - the ONLY new producer this phase introduces. `OutboxWriter.save(...)` runs `Propagation.MANDATORY` (fails fast if called outside an open transaction) - call it from inside the SAME `@Transactional` method that mutates `OrderEntity.status`/`OrderLineEntity.cancelledAt`/`OrderEntity.total`.
```java
outboxWriter.save("ORDER", order.getId(), OrderCancelledEvent.TYPE, orderCancelledTopic,
    order.getId().toString(), event);
```

### Kafka DLT / poison-pill wiring (ALL 3 new consumer configs)
**Source:** `order_context/infrastructure/config/TicketStatusChangedKafkaConsumerConfig.java` (full file - the canonical cross-context template) or `inventory_context/infrastructure/config/SettleTriggerKafkaConsumerConfig.java` (same-context template).
**Apply to:** `OrderCancelledKafkaConsumerConfig` (inventory), `OrderCancelledPaymentKafkaConsumerConfig` (payment), `OrderCancelledKitchenKafkaConsumerConfig` (kitchen).
Copy block-for-block: `ErrorHandlingDeserializer` wrapping `JacksonJsonDeserializer` + `TRUSTED_PACKAGES` pinned to `order_context.application.event` (the OWNING context, not the consumer's own package) + `USE_TYPE_INFO_HEADERS=false` + `DeadLetterPublishingRecoverer` + `FixedBackOff(1000L, 3L)` + `addNotRetryableExceptions(DeserializationException.class)`. MUST use Jackson-3 `JacksonJsonSerializer`/`JacksonJsonDeserializer` (`org.springframework.kafka.support.serializer`), never the legacy Jackson-2 `JsonSerializer`/`JsonDeserializer` (RESEARCH Pitfall 4 - this exact mistake was already made once for Payment/Table producers and fixed in quick task `260710-eqh`).

### Terminal-status guard (CANCELLED, mirrors REJECTED)
**Source:** `order_context/application/KitchenStatusProjectionService.java` line 94.
**Apply to:** `KitchenStatusProjectionService.onTicketStatusChanged` (extend the existing check); `OrderStatus.java` (append `CANCELLED` at the END of the enum, never inside the pinned `CONFIRMED..COMPLETED` block, and never add it to `KitchenStatusProjectionService.FULFILLMENT_RANK`, exactly like `REJECTED` today).
```java
if (order.getStatus() == OrderStatus.REJECTED || order.getStatus() == OrderStatus.CANCELLED) {
  return;
}
```
`OrderConfirmationService.onStockResult`'s existing guard (`order.getStatus() != OrderStatus.PENDING_CONFIRMATION`, line 71) ALREADY protects against a stale confirmation resurrecting a cancelled order - no code change needed there, only a regression test.

### Ownership / IDOR-safe 404 (customer cancel path)
**Source:** `order_context/infrastructure/repository/OrderRepository.findByIdAndUserId` (already exists) + `order_context/application/OrderSubmissionService.getOrder` (lines 100-106) + `OrderDomainException.orderNotFound()` (`OrderDomainException.java` lines 49-51).
**Apply to:** `OrderCancellationService`'s customer-cancel entry point.
```java
orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(OrderDomainException::orderNotFound);
```

### Staff/ADMIN authorization (no code annotation needed)
**Source:** `SecurityConfig.java` line 61-62 (`.requestMatchers("/admin/payments", "/admin/payments/**", "/admin/orders/**").hasAnyRole("ADMIN", "STAFF")`) + `KitchenController.java` class Javadoc (lines 20-23: "No class/method security annotation is needed here").
**Apply to:** Any new `/admin/orders/{orderId}/cancel` or `/admin/orders/{orderId}/items/{lineId}/cancel` endpoint - the existing route matcher already covers `/admin/orders/**`, so no `SecurityConfig` change is needed as long as new admin cancel endpoints stay under that prefix.

### System-actor null convention (auto-refund, no human actor)
**Source:** `inventory_context/application/InventoryReservationSettlementService.java` line 187 (`movement.setActorId(null); // system-triggered settlement, no human actor`).
**Apply to:** `PaymentAutoRefundService` calling `paymentService.recordRefund(null, paymentId, request)` after `PaymentRefundEntity.actorUserId` is made nullable - use `null`, never a sentinel "system" UUID (RESEARCH Pitfall 5 / Assumption A5).

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `payment_context` consumer/ledger infrastructure as a WHOLE (config+adapter+entity+repo) | service/adapter/config/model | event-driven | No in-context precedent exists at all - Payment has never consumed Kafka before this phase (RESEARCH Summary point 3). Cross-context analogs from Inventory/Kitchen are used instead (see Pattern Assignments above); this is flagged here because the planner should NOT expect any payment_context file to serve as its own analog. |
| `order_context/domain/port/KitchenItemStatusPort.java` + adapter (the reverse-direction cross-context port) | port/adapter | request-response | No existing port in this codebase reads FROM kitchen_context INTO order_context synchronously (`OrderLineLookupPort` goes the opposite direction: inventory reading order). The pattern to copy is `OrderLineLookupPort`'s STRUCTURE (interface-owned-by-consumer, adapter-owned-by-producer), not a literal analog of the SAME direction - use with the caveat that this is a genuinely new cross-context read path in this codebase (RESEARCH Pitfall 1 / Assumption A4). |

## Conventions

convention derivation skipped (no-readable-files: the shared `bin/gsd-tools.cjs verify conventions --derive` corpus walker only scans JS/TS extensions (`.js/.mjs/.cjs/.jsx/.ts/.mts/.cts/.tsx`); this repository is a 100% Java/Spring Boot codebase with zero files matching that pattern, so the tool correctly reports zero readable files rather than a real error). No 4-axis JS/TS table applies here.

**Manually observed Java conventions from the files read above** (informal, not derived by the shared tool - offered as a substitute since the deterministic tool doesn't cover this stack):
- File-name casing: UpperCamelCase matching the public type (`OrderCancellationService.java`, `InventoryLineReleaseEntity.java`) - 100% consistent across all ~40 files read.
- Identifier casing: lowerCamelCase fields/methods, UPPER_SNAKE_CASE `static final` constants (`CONSUMER_NAME`, `QUANTITY_SCALE`, `DEFAULT_LOCATION`) - 100% consistent.
- Class shape: `@Getter @Setter @Entity` for JPA entities (Lombok, never hand-written accessors); `@Service @RequiredArgsConstructor` for services; `@Component @RequiredArgsConstructor` for thin adapters/ledger-writers - 100% consistent, no variance observed.
- Import style: explicit per-class imports, alphabetically grouped by package prefix, no wildcard imports - 100% consistent across every file read.

**Contested hotspots (author's choice):** none identified within Java-land in this sampling - the codebase is highly uniform. The project DOES, however, carry one well-known intentional contested split outside this phase's scope: the CJS<->SDK dual resolver in the GSD tooling itself (`bin/lib/**` is CJS `module.exports`/`require`; `sdk/src/**` is ESM `export`/`import`) - each half is internally consistent per-directory, contested only when compared repo-wide. That split is unrelated to this phase's Java code but is the canonical example planners/reviewers should recall when a directory's local style differs from a sibling directory's: match the directory's local convention rather than forcing repo-wide uniformity.

## Metadata

**Analog search scope:** `src/main/java/com/example/feat1/DDD/{order_context,inventory_context,kitchen_context,payment_context,shared/outbox}/**` (all four bounded contexts touched by this phase, plus the shared outbox mechanism); `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java` for route/role conventions.
**Files scanned:** ~40 source files read in full or in targeted excerpt (services, entities, repositories, controllers, Kafka consumer/producer configs, ports/adapters, domain exceptions/enums) across all four contexts + shared outbox.
**Pattern extraction date:** 2026-07-10
