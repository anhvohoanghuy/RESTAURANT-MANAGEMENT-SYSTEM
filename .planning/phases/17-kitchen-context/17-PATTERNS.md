# Phase 17: Kitchen context - Pattern Map

**Mapped:** 2026-07-08
**Files analyzed:** 24 (new/modified)
**Analogs found:** 24 / 24 (every file group has a same-repo precedent; RESEARCH.md's own "Code
Examples" section already names most of them — this file adds exact line-numbered excerpts and
extends coverage to files RESEARCH.md only listed by path)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `kitchen_context/domain/model/KitchenItemStatus.java` | model (enum) | request-response | `order_context/domain/model/OrderStatus.java` | role-match |
| `kitchen_context/domain/model/KitchenDomainException.java` | model (exception) | request-response | `order_context/domain/model/OrderDomainException.java` | exact |
| `kitchen_context/infrastructure/entity/KitchenTicketEntity.java` | model (JPA aggregate root) | CRUD | `order_context/infrastructure/entity/OrderEntity.java` | exact |
| `kitchen_context/infrastructure/entity/KitchenTicketItemEntity.java` | model (JPA child entity) | CRUD | `order_context/infrastructure/entity/OrderLineEntity.java` | exact |
| `kitchen_context/infrastructure/entity/KitchenTicketItemToppingSnapshot.java` | model (`@Embeddable`) | CRUD | `order_context/infrastructure/entity/OrderLineToppingSnapshot.java` | exact |
| `kitchen_context/infrastructure/entity/KitchenProcessedEventEntity.java` | model (idempotency ledger) | event-driven | `order_context/infrastructure/entity/OrderProcessedEventEntity.java` | exact |
| `kitchen_context/infrastructure/repository/KitchenTicketRepository.java` | model (repository) | CRUD | `order_context/infrastructure/repository/OrderRepository.java` (JpaRepository, no special methods) | role-match |
| `kitchen_context/infrastructure/repository/KitchenTicketItemRepository.java` (with `lockById`) | model (repository, pessimistic lock) | CRUD | `inventory_context/infrastructure/repository/StockReservationRepository.java` | exact |
| `kitchen_context/infrastructure/repository/KitchenProcessedEventRepository.java` | model (repository) | event-driven | `order_context/infrastructure/repository/OrderProcessedEventRepository.java` | exact |
| `kitchen_context/application/event/KitchenTicketStatusChangedEvent.java` | model (event record) | event-driven | `order_context/application/event/OrderCreatedEvent.java` (record shape) + `inventory_context/.../SettleTriggerEvent.java` (header fields) | role-match |
| `kitchen_context/application/KitchenTicketCreationService.java` (consumes `OrderConfirmed`) | service | event-driven | `order_context/application/OrderConfirmationService.java` | exact |
| `kitchen_context/application/KitchenTicketAdvanceService.java` (advance + publish SettleTrigger + status-changed) | service | event-driven + request-response | `inventory_context/application/InventoryReservationSettlementService.java` (lock+ledger+publish shape) + `table_context/application/TableOperationService.updateReservationStatus` (forward-only PATCH shape) | role-match |
| `kitchen_context/application/KitchenBoardService.java` (read-only board query) | service | request-response | `order_context/application/OrderSubmissionService.listOrders` (read-only `@Transactional` list query) | role-match |
| `kitchen_context/application/KitchenLedgerWriter.java` (optional, only if REQUIRES_NEW needed) | service | event-driven | `inventory_context/application/InventoryLedgerWriter.java` | exact |
| `kitchen_context/infrastructure/adapter/OrderConfirmedListener.java` | controller (Kafka listener) | event-driven | `order_context/infrastructure/adapter/OrderStockResultListener.java` | exact |
| `kitchen_context/infrastructure/config/OrderConfirmedKafkaConsumerConfig.java` | config | event-driven | `order_context/infrastructure/config/OrderKafkaConsumerConfig.java` | exact |
| `kitchen_context/infrastructure/config/KitchenSettleTriggerProducerConfig.java` (imports inventory's event) | config | event-driven | `inventory_context/infrastructure/config/InventoryKafkaProducerConfig.java` | exact |
| `kitchen_context/infrastructure/config/KitchenTicketStatusChangedProducerConfig.java` | config | event-driven | `order_context/infrastructure/config/OrderKafkaProducerConfig.java` | exact |
| `kitchen_context/infrastructure/config/KitchenKafkaTopicConfig.java` | config | event-driven | `inventory_context/infrastructure/config/InventoryKafkaTopicConfig.java` | exact |
| `kitchen_context/infrastructure/presentation/KitchenController.java` (PATCH advance + GET board) | controller (REST) | request-response | `table_context/infrastructure/presentation/TableOperationController.java` | exact |
| `order_context/application/event/OrderConfirmedEvent.java` (NEW) | model (event record) | event-driven | `order_context/application/event/OrderCreatedEvent.java` | exact |
| `order_context/domain/port/OrderEventPublisher.java` (ADD method) | model (port) | event-driven | itself (extend) — shape mirrors `publishOrderCreated` | exact |
| `order_context/infrastructure/adapter/KafkaOrderEventPublisher.java` (ADD field+method) | controller (adapter) | event-driven | itself (extend) | exact |
| `order_context/infrastructure/config/OrderKafkaProducerConfig.java` (ADD bean pair) | config | event-driven | itself (extend) | exact |
| `order_context/application/OrderConfirmationService.java` (ADD publish) | service | event-driven | itself (extend) + `order_context/application/OrderSubmissionService.java` (`publishAfterCommit` helper to copy) | exact |
| `order_context/domain/model/OrderStatus.java` (ADD 4 values) | model (enum) | request-response | itself (extend) | exact |
| `order_context/infrastructure/adapter/TicketStatusChangedListener.java` (NEW) | controller (Kafka listener) | event-driven | `order_context/infrastructure/adapter/OrderStockResultListener.java` | exact |
| `order_context/infrastructure/config/TicketStatusChangedKafkaConsumerConfig.java` (NEW) | config | event-driven | `order_context/infrastructure/config/OrderKafkaConsumerConfig.java` | exact |
| `order_context/application/KitchenStatusProjectionService.java` (NEW, or method on `OrderConfirmationService`) | service | event-driven | `order_context/application/OrderConfirmationService.java` (ledger+status-guard shape) — extended with a rank/ordinal forward-only guard (no exact precedent; see "No close analog" note below) | role-match |

## Pattern Assignments

### `kitchen_context/domain/model/KitchenItemStatus.java` (model/enum)

**Analog:** `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java` (lines 1-8)

```java
package com.example.feat1.DDD.order_context.domain.model;

public enum OrderStatus {
  SUBMITTED,
  PENDING_CONFIRMATION,
  CONFIRMED,
  REJECTED
}
```
Copy this bare-enum shape for `KitchenItemStatus { QUEUED, PREPARING, READY, SERVED, COMPLETED }`.
Per Pitfall 3 in RESEARCH.md, add a code comment pinning declaration order as load-bearing (used by
a `rank()`/ordinal forward-only guard), since this codebase has no existing example of an
ordinal-based multi-step guard to copy verbatim — that guard shape is new (see the closing "No Analog
Found" section).

---

### `kitchen_context/domain/model/KitchenDomainException.java` (model/exception)

**Analog:** `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderDomainException.java` (full file, 53 lines)

```java
package com.example.feat1.DDD.order_context.domain.model;

import com.example.feat1.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class OrderDomainException extends AppException {
  public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";

  public OrderDomainException(String code, String message, HttpStatus status) {
    super(code, message, status);
  }

  public static OrderDomainException orderNotFound() {
    return new OrderDomainException(ORDER_NOT_FOUND, "Order was not found", HttpStatus.NOT_FOUND);
  }
}
```

Base class (`AppException`, `src/main/java/com/example/feat1/common/exception/AppException.java`, full 22 lines):
```java
public class AppException extends RuntimeException {
  private final String code;
  private final HttpStatus status;

  public AppException(String code, String message, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }
  public String getCode() { return code; }
  public HttpStatus getStatus() { return status; }
}
```
`KitchenDomainException` needs static factories such as `ticketNotFound()`, `itemNotFound()`,
`transitionInvalid()` (mirror `TableDomainException.reservationStatusInvalid()` naming style — see
Pattern 6 below) each with a stable `KITCHEN_*` code constant and the correct `HttpStatus`
(`NOT_FOUND` for lookups, `BAD_REQUEST`/`409 CONFLICT`-style for illegal transitions — this codebase
uses `BAD_REQUEST` for `reservationStatusInvalid`, so match that unless CONFLICT is explicitly
preferred).

---

### `kitchen_context/infrastructure/entity/KitchenTicketEntity.java` + `KitchenTicketItemEntity.java` (JPA aggregate)

**Analog (aggregate root, one row per order):** `order_context/infrastructure/entity/OrderEntity.java` (full file, 73 lines)
```java
@Getter
@Setter
@Entity
@Table(name = "orders")
public class OrderEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status = OrderStatus.PENDING_CONFIRMATION;
  ...
  @OneToMany(
      mappedBy = "order",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<OrderLineEntity> lines = new ArrayList<>();
}
```

**Analog (child entity with independent PK, `@ManyToOne` back-reference):**
`order_context/infrastructure/entity/OrderLineEntity.java` (lines 22-63):
```java
@Getter
@Setter
@Entity
@Table(name = "order_lines")
public class OrderLineEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private OrderEntity order;

  @Column(name = "dish_id", nullable = false)
  private UUID dishId;
  ...
  @Column(nullable = false)
  private int quantity;
  ...
}
```

**Decision baked into RESEARCH.md's Open Question #2 (do not re-litigate):** model
`KitchenTicketItemEntity` as a full `@Entity` with `@ManyToOne` to `KitchenTicketEntity` — exactly
like `OrderLineEntity` → `OrderEntity` — NOT as an `@Embeddable`/`@ElementCollection` (unlike
`StockReservationEntity.ReservationLine`), because each item needs an independently lockable,
URL-addressable `{itemId}` and its own status column.

**Embeddable child-of-child (toppings snapshot) analog:**
`order_context/infrastructure/entity/OrderLineToppingSnapshot.java` (full file, 28 lines):
```java
@Getter
@Setter
@Embeddable
public class OrderLineToppingSnapshot {
  @Column(name = "topping_group_id", nullable = false)
  private UUID toppingGroupId;
  @Column(name = "topping_group_name", nullable = false)
  private String toppingGroupName;
  @Column(name = "topping_option_id", nullable = false)
  private UUID toppingOptionId;
  @Column(name = "topping_option_name", nullable = false)
  private String toppingOptionName;
  @Column(name = "additional_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal additionalPrice;
}
```
And its `@ElementCollection` wiring on the parent line entity
(`order_context/infrastructure/entity/OrderLineEntity.java` lines 47-50):
```java
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "order_line_toppings", joinColumns = @JoinColumn(name = "line_id"))
@OrderColumn(name = "sort_order")
private List<OrderLineToppingSnapshot> selectedToppings = new ArrayList<>();
```
`KitchenTicketItemToppingSnapshot` should copy this shape verbatim (rename table to e.g.
`kitchen_ticket_item_toppings`, join column `item_id`).

**Status enum + column on the item entity** — mirror `OrderEntity`'s status field style:
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private KitchenItemStatus status = KitchenItemStatus.QUEUED;
```

---

### `kitchen_context/infrastructure/entity/KitchenProcessedEventEntity.java` + `KitchenProcessedEventRepository.java` (idempotency ledger)

**Analog:** `order_context/infrastructure/entity/OrderProcessedEventEntity.java` (full file, 45 lines)
```java
@Getter
@Setter
@Entity
@Table(
    name = "order_processed_events",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_order_processed_event",
            columnNames = {"event_id", "consumer_name"}))
public class OrderProcessedEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "consumer_name", nullable = false)
  private String consumerName;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;
}
```
And its repository (`order_context/infrastructure/repository/OrderProcessedEventRepository.java`, full 11 lines):
```java
public interface OrderProcessedEventRepository
    extends JpaRepository<OrderProcessedEventEntity, UUID> {
  boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
```
Rename table to `kitchen_processed_events` — this is the **3rd** physically distinct ledger table in
the codebase (`order_processed_events`, `inventory_processed_events`, `kitchen_processed_events`);
never reuse an existing table name across contexts (each context has its own datasource/schema
assumption per RESEARCH.md).

---

### `kitchen_context/infrastructure/repository/KitchenTicketItemRepository.java` (pessimistic lock)

**Analog:** `inventory_context/infrastructure/repository/StockReservationRepository.java` (full file, 25 lines)
```java
public interface StockReservationRepository extends JpaRepository<StockReservationEntity, UUID> {

  boolean existsByOrderId(UUID orderId);

  Optional<StockReservationEntity> findByOrderId(UUID orderId);

  /**
   * Acquires a pessimistic write lock on the reservation row so concurrent settlements of the same
   * order are serialized and cannot double-settle a held reservation (D-04).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from StockReservationEntity r where r.orderId = :orderId")
  Optional<StockReservationEntity> lockByOrderId(UUID orderId);
}
```
Add an equivalent `lockById(UUID itemId)` (or `lockByIdAndTicket_OrderId(...)` — see the IDOR note
below) on `KitchenTicketItemRepository`, `@Lock(LockModeType.PESSIMISTIC_WRITE)` +
`@Query("select i from KitchenTicketItemEntity i where i.id = :id")`. This closes Pitfall 1 (double
settle-trigger race) — acquire the lock BEFORE the forward-only status check, in the same
`@Transactional` method that mutates and publishes.

**IDOR defense (dual-key lookup) analog:** `order_context/infrastructure/adapter/OrderLineLookupAdapter.java` (lines 23-27):
```java
@Override
@Transactional(readOnly = true)
public Optional<OrderLineRecipeSnapshot> findLine(UUID orderId, UUID orderLineId) {
  return orderLineRepository
      .findByOrder_IdAndId(orderId, orderLineId)
      .map(...);
}
```
The kitchen advance service's `PATCH /admin/orders/{orderId}/items/{itemId}/status` MUST verify
`{itemId}` belongs to a ticket for `{orderId}` — either via a repository method keyed by both
(`findByIdAndTicket_OrderId`) or by loading the ticket by `orderId` first and checking item
membership — never look up the item by its bare PK alone.

---

### `kitchen_context/application/event/KitchenTicketStatusChangedEvent.java` (new event record)

**Analog for record/header shape:** `inventory_context/application/event/SettleTriggerEvent.java` (full file, 20 lines)
```java
public record SettleTriggerEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID orderId,
    UUID orderLineId,
    int totalLines) {
  public static final String TYPE = "SettleTrigger";
}
```
**Analog for line-manifest shape (per-item payload):** `order_context/application/event/OrderCreatedEvent.java` (lines 8-39):
```java
public record OrderCreatedEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID orderId,
    UUID userId,
    OrderTable table,
    List<OrderLine> lines,
    BigDecimal total,
    Instant submittedAt) {
  public static final String TYPE = "OrderCreated";

  public record OrderLine(
      UUID lineId, UUID dishId, String dishName, BigDecimal basePrice,
      List<OrderTopping> selectedToppings, BigDecimal toppingsTotal,
      BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}
}
```
Per RESEARCH.md's Open Question #1 recommendation, shape
`KitchenTicketStatusChangedEvent(eventId, eventType, occurredAt, orderId, ticketId, List<ItemStatus> items)`
where `ItemStatus(orderLineId, status)` is a full per-item snapshot (mirrors the "self-contained
manifest" philosophy of `OrderCreatedEvent`/D-01), letting `order_context` derive the aggregate order
status purely from this one event without any cross-context lookup.

---

### `order_context/application/event/OrderConfirmedEvent.java` (NEW event, mirrors OrderCreatedEvent's line manifest)

**Analog:** same `OrderCreatedEvent.java` shown above (full record + nested `OrderLine`/`OrderTopping`
records, lines 1-40). Reuse `OrderLine`/`OrderTopping` field shape as-is for the manifest
(`lineId, dishId, dishName, quantity, selectedToppings`) per D-01 — drop pricing fields the kitchen
doesn't need, or keep them for parity; either is acceptable since kitchen only reads dish/topping
identity fields.

---

### `order_context/domain/port/OrderEventPublisher.java` + `KafkaOrderEventPublisher.java` + `OrderKafkaProducerConfig.java` (extend for `OrderConfirmed`)

**Port (full file, 8 lines) — add a sibling method:**
```java
package com.example.feat1.DDD.order_context.domain.port;

import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;

public interface OrderEventPublisher {
  void publishOrderCreated(OrderCreatedEvent event);
  // ADD: void publishOrderConfirmed(OrderConfirmedEvent event);
}
```

**Adapter (full file, 22 lines) — add a 2nd `KafkaTemplate` field + method:**
```java
@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher implements OrderEventPublisher {
  private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

  @Value("${order.events.order-created-topic:orders.created}")
  private String orderCreatedTopic;

  @Override
  public void publishOrderCreated(OrderCreatedEvent event) {
    kafkaTemplate.send(orderCreatedTopic, event.orderId().toString(), event);
  }
  // ADD: a 2nd `KafkaTemplate<String, OrderConfirmedEvent>` field, a 2nd `@Value` topic
  //      (`${order.events.order-confirmed-topic:orders.confirmed}`), and `publishOrderConfirmed`
  //      following the exact same one-line `.send(topic, orderId.toString(), event)` shape.
}
```

**Producer config (full file, 33 lines) — add a 2nd `ProducerFactory`/`KafkaTemplate` bean pair:**
```java
@Configuration
public class OrderKafkaProducerConfig {
  @Bean
  public ProducerFactory<String, OrderCreatedEvent> orderCreatedProducerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
  }
  @Bean
  public KafkaTemplate<String, OrderCreatedEvent> orderCreatedKafkaTemplate(
      ProducerFactory<String, OrderCreatedEvent> orderCreatedProducerFactory) {
    return new KafkaTemplate<>(orderCreatedProducerFactory);
  }
  // ADD an identically-shaped bean pair retyped to OrderConfirmedEvent, bean names
  // orderConfirmedProducerFactory / orderConfirmedKafkaTemplate.
}
```

---

### `order_context/application/OrderConfirmationService.java` (ADD publish call)

**Analog (itself, current state, full file, 90 lines) — the exact insertion point:**
```java
@Transactional
public void onStockResult(OrderStockResultEvent event) {
  // ... idempotency guard (lines 40-53), load + status guard (lines 55-63) unchanged ...
  if (event.result() == Result.CONFIRMED) {
    order.setStatus(OrderStatus.CONFIRMED);
    // ADD: publishAfterCommit(toOrderConfirmedEvent(order));
  } else {
    order.setStatus(OrderStatus.REJECTED);
    order.setRejectionReason(describe(event.shortfalls()));
  }
}
```
`OrderConfirmationService` currently has NO `OrderEventPublisher` field — add it via the constructor
(the class already uses `@RequiredArgsConstructor`, so adding a `private final OrderEventPublisher
orderEventPublisher;` field is enough).

**`publishAfterCommit` helper to copy verbatim** — `order_context/application/OrderSubmissionService.java` (lines 225-237):
```java
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
Retype to `OrderConfirmedEvent` / `publishOrderConfirmed`. A second independent copy of this exact
shape exists at `table_context/application/TableOperationService.java` (lines 565-577,
`publishAfterCommit(TableOperationEvent event)`) — confirming this is a load-bearing two-for-two
convention, not a one-off to deviate from.

Required imports to add: `org.springframework.transaction.support.TransactionSynchronization` and
`org.springframework.transaction.support.TransactionSynchronizationManager` (both already imported in
`OrderSubmissionService.java` lines 31-32).

---

### `kitchen_context/application/KitchenTicketCreationService.java` (consumes `OrderConfirmed`, idempotent)

**Analog:** `order_context/application/OrderConfirmationService.java` (full file, 90 lines) — the
inline idempotency-ledger + load pattern (no pessimistic lock needed here since ticket-creation is a
straightforward existence check per RESEARCH.md's Pattern 4 recommendation):
```java
@Service
@RequiredArgsConstructor
public class OrderConfirmationService {
  static final String CONSUMER_NAME = "order-stock-result";
  private final OrderProcessedEventRepository processedEventRepository;
  private final OrderRepository orderRepository;

  @Transactional
  public void onStockResult(OrderStockResultEvent event) {
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
      return;
    }
    // ... load + status-guard + apply verdict ...
  }
}
```
`KitchenTicketCreationService.onOrderConfirmed(OrderConfirmedEvent event)` follows this shape 1:1:
ledger check → insert+flush ledger → build `KitchenTicketEntity` + all `KitchenTicketItemEntity`
rows in ONE pass from `event.lines()` (Pitfall 4 — never add items later) → save.
Consumer name constant e.g. `"kitchen-order-confirmed"`.

---

### `kitchen_context/application/KitchenTicketAdvanceService.java` (advance + settle-trigger + status-changed publish)

**Analog for lock-then-mutate-then-publish shape:** `inventory_context/application/InventoryReservationSettlementService.java` (lines 77-104, the idempotency+lock preamble):
```java
@Transactional
public void onSettleTrigger(SettleTriggerEvent event) {
  ...
  StockReservationEntity reservation =
      reservationRepository
          .lockByOrderId(orderId)
          .orElseThrow(() -> InventoryDomainException.settlementReservationMissing(orderId));
  if (reservation.getStatus() != ReservationStatus.HELD) {
    log.debug("Reservation for order {} already settled — skipping line {}", orderId, orderLineId);
    return;
  }
  ...
}
```
Kitchen's advance service should lock the item row FIRST (`kitchenTicketItemRepository.lockById(itemId)`)
before checking `isValidTransition(item.getStatus(), target)` — closing Pitfall 1 exactly as this
service closes its own double-settle race.

**Analog for the forward-only PATCH shape + stable error:** `table_context/application/TableOperationService.java` (lines 237-265):
```java
@Transactional
public TableReservationResponse updateReservationStatus(
    UUID reservationId, UpdateReservationStatusRequest request, UUID actorUserId) {
  TableReservationEntity reservation =
      reservationRepository
          .findById(reservationId)
          .orElseThrow(TableDomainException::reservationNotFound);
  ReservationStatus target = request == null ? null : request.status();
  if (!isValidTransition(reservation.getStatus(), target)) {
    throw TableDomainException.reservationStatusInvalid();
  }
  reservation.setStatus(target);
  ...
  TableReservationEntity saved = reservationRepository.save(reservation);
  publishAfterCommit(event(TableOperationEvent.RESERVATION_STATUS_CHANGED, ...));
  return toReservationResponse(saved);
}
```
And its `isValidTransition` helper (lines 445-459) — the single-step forward-only `switch`:
```java
private boolean isValidTransition(ReservationStatus current, ReservationStatus target) {
  if (target == null || current == null) {
    return false;
  }
  return switch (current) {
    case PENDING -> target == ReservationStatus.CONFIRMED || target == ReservationStatus.CANCELLED;
    case CONFIRMED -> target == ReservationStatus.SEATED || ... ;
    case SEATED -> target == ReservationStatus.COMPLETED;
    case CANCELLED, NO_SHOW, COMPLETED -> false;
  };
}
```
Kitchen's `isValidTransition(KitchenItemStatus current, KitchenItemStatus target)` is a strict
single-step chain (`QUEUED->PREPARING->READY->SERVED->COMPLETED`, terminal `COMPLETED`), simpler than
the table example's branching (no cancel/no-show side-branches) — use the same `switch` shape,
one `case` per status, only the immediate-next status legal.

**Publish points (2 per advance call, after commit):** once the transition is confirmed valid, (a)
if `current == QUEUED && target == PREPARING`, build and `publishAfterCommit` the (cross-context,
imported not redeclared) `SettleTriggerEvent` — see the config pattern below; (b) always
`publishAfterCommit` the `KitchenTicketStatusChangedEvent` with the full per-item snapshot. Use the
verbatim `publishAfterCommit` helper shape from `OrderSubmissionService`/`TableOperationService`
(shown above) — one private helper per event type, or a single overloaded/generic helper if the
service publishes to two different `EventPublisher` ports.

---

### `kitchen_context/application/KitchenBoardService.java` (read-only board query)

**Analog:** `order_context/application/OrderSubmissionService.java` (lines 85-90):
```java
@Transactional(readOnly = true)
public List<SubmittedOrderResponse> listOrders(UUID userId) {
  return orderRepository.findByUserIdOrderBySubmittedAtDesc(userId).stream()
      .map(this::toResponse)
      .toList();
}
```
Kitchen board: `@Transactional(readOnly = true)` method querying `KitchenTicketItemRepository` (or
`KitchenTicketRepository`) for items where `status != COMPLETED`, mapped to a board DTO — same
`stream().map(...).toList()` shape.

---

### `kitchen_context/infrastructure/adapter/OrderConfirmedListener.java` (thin Kafka listener)

**Analog:** `order_context/infrastructure/adapter/OrderStockResultListener.java` (full file, 28 lines)
```java
@Component
@RequiredArgsConstructor
public class OrderStockResultListener {
  private final OrderConfirmationService confirmationService;

  @KafkaListener(
      topics = "${inventory.events.order-stock-results-topic:inventory.order-stock-results}",
      groupId = "${order.stock-result.consumer.group-id:order-stock-result}",
      containerFactory = "orderStockResultKafkaListenerContainerFactory")
  public void onStockResult(OrderStockResultEvent event) {
    confirmationService.onStockResult(event);
  }
}
```
Copy verbatim shape: one `@Component` + `@RequiredArgsConstructor`, one `@KafkaListener` method that
is a ONE-LINE delegate to the service (Anti-Pattern in RESEARCH.md: no business logic here). Topic
property e.g. `${order.events.order-confirmed-topic:orders.confirmed}`, group-id e.g.
`${kitchen.order-confirmed.consumer.group-id:kitchen-order-confirmed}`, container factory
`orderConfirmedKafkaListenerContainerFactory`.

`order_context/infrastructure/adapter/TicketStatusChangedListener.java` (NEW, order-side) mirrors this
same shape, delegating to the new order-side projection service.

---

### `kitchen_context/infrastructure/config/OrderConfirmedKafkaConsumerConfig.java` + `TicketStatusChangedKafkaConsumerConfig.java` (consumer wiring)

**Analog:** `order_context/infrastructure/config/OrderKafkaConsumerConfig.java` (full file, 121 lines)
— copy the ENTIRE shape (consumer factory, DLT template, error handler, container factory beans),
retyped and renamed:
```java
private static final String TRUSTED_PACKAGE = "com.example.feat1.DDD.order_context.application.event";

@Bean
public ConsumerFactory<String, OrderStockResultEvent> orderStockResultConsumerFactory(
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
    @Value("${order.stock-result.consumer.group-id:order-stock-result}") String groupId) {
  Map<String, Object> props = new HashMap<>();
  props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
  props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
  props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
  props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
  props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
  props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
  props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
  props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, TRUSTED_PACKAGE);
  props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, OrderStockResultEvent.class.getName());
  props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
  return new DefaultKafkaConsumerFactory<>(props);
}

@Bean
public KafkaTemplate<String, Object> orderDltKafkaTemplate(
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) { ... }

@Bean
public DefaultErrorHandler orderStockResultErrorHandler(
    @Qualifier("orderDltKafkaTemplate") KafkaTemplate<String, Object> orderDltKafkaTemplate) {
  DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(orderDltKafkaTemplate);
  DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
  handler.addNotRetryableExceptions(DeserializationException.class);
  return handler;
}

@Bean
public ConcurrentKafkaListenerContainerFactory<String, OrderStockResultEvent>
    orderStockResultKafkaListenerContainerFactory(
        ConsumerFactory<String, OrderStockResultEvent> orderStockResultConsumerFactory,
        DefaultErrorHandler orderStockResultErrorHandler) {
  ConcurrentKafkaListenerContainerFactory<String, OrderStockResultEvent> factory =
      new ConcurrentKafkaListenerContainerFactory<>();
  factory.setConsumerFactory(orderStockResultConsumerFactory);
  factory.setCommonErrorHandler(orderStockResultErrorHandler);
  factory.getContainerProperties().setAckMode(AckMode.RECORD);
  return factory;
}
```
**Naming collision guard (Pitfall 5):** name every new bean with a `kitchen`-prefix
(`kitchenDltKafkaTemplate`, `orderConfirmedConsumerFactory`,
`orderConfirmedKafkaListenerContainerFactory`) — NEVER reuse the bare `dltKafkaTemplate` /
`consumerFactory` names that collide with `orderDltKafkaTemplate` / `inventoryDltKafkaTemplate`
already in the app context. Same rule applies to `TicketStatusChangedKafkaConsumerConfig` on the
order side (e.g. `ticketStatusChangedDltKafkaTemplate`).

---

### `kitchen_context/infrastructure/config/KitchenSettleTriggerProducerConfig.java` (cross-context event reuse)

**Analog:** `inventory_context/infrastructure/config/InventoryKafkaProducerConfig.java` (full file, 45 lines)
```java
package com.example.feat1.DDD.inventory_context.infrastructure.config;

import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent; // cross-context import

@Configuration
public class InventoryKafkaProducerConfig {
  @Bean
  public ProducerFactory<String, OrderStockResultEvent> orderStockResultProducerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
  }
  @Bean
  public KafkaTemplate<String, OrderStockResultEvent> orderStockResultKafkaTemplate(
      ProducerFactory<String, OrderStockResultEvent> orderStockResultProducerFactory) {
    return new KafkaTemplate<>(orderStockResultProducerFactory);
  }
}
```
Kitchen's producer config is a structural clone, importing
`com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent` **directly** — do NOT
redeclare a copy of this record in `kitchen_context`. Bean names e.g.
`settleTriggerProducerFactory` / `settleTriggerKafkaTemplate` (already free — not used elsewhere;
`inventory_context`'s own producer beans for this event don't exist because inventory only
*consumes* `SettleTriggerEvent`, never produces it).

**`SettleTriggerEvent` contract to construct (full file, 20 lines) — kitchen MUST match exactly:**
```java
public record SettleTriggerEvent(
    UUID eventId, String eventType, Instant occurredAt,
    UUID orderId, UUID orderLineId, int totalLines) {
  public static final String TYPE = "SettleTrigger";
}
```
Publish call shape (mirrors every other producer's one-liner):
```java
kafkaTemplate.send(
    settleTriggerTopic, // ${kitchen.events.settle-trigger-topic:kitchen.settlement-trigger}
    orderId.toString(),
    new SettleTriggerEvent(UUID.randomUUID(), SettleTriggerEvent.TYPE, Instant.now(),
        orderId, orderLineId, ticket.getItems().size()));
```

---

### `kitchen_context/infrastructure/config/KitchenKafkaTopicConfig.java` (NewTopic beans, own topics ONLY)

**Analog:** `inventory_context/infrastructure/config/InventoryKafkaTopicConfig.java` (full file, 65 lines)
```java
@Configuration
public class InventoryKafkaTopicConfig {
  private static final String DLT_SUFFIX = ".DLT";
  private final String orderStockResultsTopic;
  ...
  public InventoryKafkaTopicConfig(
      @Value("${inventory.events.order-stock-results-topic:inventory.order-stock-results}")
          String orderStockResultsTopic,
      ...
      @Value("${kitchen.events.settle-trigger-topic:kitchen.settlement-trigger}")
          String settleTriggerTopic) { ... }

  @Bean
  public NewTopic orderStockResultsTopic() {
    return TopicBuilder.name(orderStockResultsTopic).partitions(1).replicas(1).build();
  }
  @Bean
  public NewTopic settleTriggerTopic() {
    return TopicBuilder.name(settleTriggerTopic).partitions(1).replicas(1).build();
  }
  @Bean
  public NewTopic settleTriggerDltTopic() {
    return TopicBuilder.name(settleTriggerTopic + DLT_SUFFIX).partitions(1).replicas(1).build();
  }
}
```
**Anti-pattern flagged in RESEARCH.md:** `settleTriggerTopic()` / `settleTriggerDltTopic()` beans are
ALREADY declared here — `KitchenKafkaTopicConfig` must declare `NewTopic` beans ONLY for its two
genuinely new topics (`orders.confirmed` + `.DLT`, `kitchen.ticket-status-changed` + `.DLT`), NOT a
second `settleTriggerTopic()` bean.

---

### `kitchen_context/infrastructure/presentation/KitchenController.java` (PATCH advance + GET board)

**Analog:** `table_context/infrastructure/presentation/TableOperationController.java` (lines 1-32, 98-105 — imports + the PATCH `.../status` method)
```java
@RestController
@RequiredArgsConstructor
public class TableOperationController {
  private final TableOperationService tableOperationService;
  ...
  @PatchMapping("/admin/tables/reservations/{reservationId}/status")
  public ResponseEntity<TableReservationResponse> updateReservationStatus(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID reservationId,
      @RequestBody UpdateReservationStatusRequest request) {
    return ResponseEntity.ok(
        tableOperationService.updateReservationStatus(reservationId, request, principal.getId()));
  }

  @GetMapping("/admin/tables/occupancy")
  public ResponseEntity<List<TableOccupancyResponse>> listOccupancy() {
    return ResponseEntity.ok(tableOperationService.listOccupancy());
  }
}
```
`KitchenController` mirrors this exactly:
```java
@PatchMapping("/admin/orders/{orderId}/items/{itemId}/status")
public ResponseEntity<KitchenItemResponse> advanceItemStatus(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable UUID orderId,
    @PathVariable UUID itemId,
    @RequestBody AdvanceItemStatusRequest request) {
  return ResponseEntity.ok(
      kitchenTicketAdvanceService.advance(orderId, itemId, request, principal.getId()));
}

@GetMapping("/admin/orders/kitchen-board")
public ResponseEntity<List<KitchenBoardItemResponse>> kitchenBoard() {
  return ResponseEntity.ok(kitchenBoardService.board());
}
```
No `@PreAuthorize`/security annotation needed on the class or methods — `/admin/orders/**` is already
`hasAnyRole("ADMIN","STAFF")` in `SecurityConfig.java` (verified at lines 61-62:
`.requestMatchers("/admin/payments", "/admin/payments/**", "/admin/orders/**")
.hasAnyRole("ADMIN", "STAFF")`).

---

### order_context ticket-status-changed projection (forward-only guard, order side)

**Closest analog (ledger + status-guard shape to copy the SKELETON from, not the guard logic itself):**
`order_context/application/OrderConfirmationService.java` (full file shown above) — the
ledger-check → load → EQUALITY status-guard → apply pattern. This is a **role-match, not exact**:
the existing guard is a single fixed-transition equality check
(`order.getStatus() != OrderStatus.PENDING_CONFIRMATION`), whereas the new consumer needs a
**multi-step, monotonic rank comparison** across 5 possible current states — no existing consumer in
this codebase has that shape yet (see "No Analog Found" below). Reuse the ledger/idempotency
preamble verbatim; the rank-guard body is new per RESEARCH.md's Pitfall 3 recommendation:
```java
private static final Map<OrderStatus, Integer> FULFILLMENT_RANK =
    Map.of(OrderStatus.CONFIRMED, 0, OrderStatus.PREPARING, 1, OrderStatus.READY, 2,
           OrderStatus.SERVED, 3, OrderStatus.COMPLETED, 4);
// only apply incoming status if newRank > currentRank, and order.getStatus() is not REJECTED
```

## Shared Patterns

### Idempotent Kafka consumer (ledger-guarded)
**Source:** `order_context/application/OrderConfirmationService.java` (lines 40-53)
**Apply to:** `KitchenTicketCreationService` (OrderConfirmed consumer), order-side ticket-status-changed
projection service
```java
if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
  return;
}
try {
  XProcessedEventEntity ledger = new XProcessedEventEntity();
  ledger.setEventId(event.eventId());
  ledger.setConsumerName(CONSUMER_NAME);
  ledger.setProcessedAt(Instant.now());
  processedEventRepository.saveAndFlush(ledger);
} catch (DataIntegrityViolationException duplicate) {
  return;
}
```

### After-commit publish (no outbox table)
**Source:** `order_context/application/OrderSubmissionService.java` (lines 225-237); confirmed
independently at `table_context/application/TableOperationService.java` (lines 565-577)
**Apply to:** EVERY new publish point this phase (`OrderConfirmedEvent`, `SettleTriggerEvent`,
`KitchenTicketStatusChangedEvent`)
```java
private void publishAfterCommit(XEvent event) {
  if (!TransactionSynchronizationManager.isSynchronizationActive()) {
    xEventPublisher.publishX(event);
    return;
  }
  TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          xEventPublisher.publishX(event);
        }
      });
}
```

### Pessimistic row lock before check-then-mutate
**Source:** `inventory_context/infrastructure/repository/StockReservationRepository.java` (lines 21-23)
**Apply to:** `KitchenTicketItemRepository.lockById(UUID itemId)` — acquired BEFORE the forward-only
transition check inside `KitchenTicketAdvanceService`, in the same `@Transactional` method
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select i from KitchenTicketItemEntity i where i.id = :id")
Optional<KitchenTicketItemEntity> lockById(UUID id);
```

### Kafka poison-pill-safe consumer wiring
**Source:** `order_context/infrastructure/config/OrderKafkaConsumerConfig.java` (full file — see excerpt above)
**Apply to:** `OrderConfirmedKafkaConsumerConfig`, `TicketStatusChangedKafkaConsumerConfig`
`ErrorHandlingDeserializer` + `JacksonJsonDeserializer` + `TRUSTED_PACKAGES` + `VALUE_DEFAULT_TYPE` +
`USE_TYPE_INFO_HEADERS=false`, `DefaultErrorHandler` with `FixedBackOff(1000L, 3L)` and
`addNotRetryableExceptions(DeserializationException.class)`, `AckMode.RECORD`.

### Stable domain error codes
**Source:** `order_context/domain/model/OrderDomainException.java` (full file); base class
`common/exception/AppException.java` (full file)
**Apply to:** `KitchenDomainException` — one `public static final String KITCHEN_*` code constant +
one static factory per error case, extending `AppException(code, message, HttpStatus)`.

### Forward-only PATCH `.../status` endpoint
**Source:** `table_context/application/TableOperationService.java` (lines 237-265, 445-459) +
`table_context/infrastructure/presentation/TableOperationController.java` (lines 98-105)
**Apply to:** `KitchenController.advanceItemStatus` + `KitchenTicketAdvanceService.advance`
Load → `isValidTransition(current, target)` `switch` → throw stable error if illegal → mutate →
save → `publishAfterCommit(...)` → return response, exactly as `updateReservationStatus` does.

## No Analog Found

| File / Concern | Role | Data Flow | Reason |
|---|---|---|---|
| Multi-step monotonic rank/ordinal forward-only guard (order-side ticket-status-changed projection) | service (guard logic) | event-driven | No existing consumer in this codebase guards more than one fixed transition by equality (`OrderConfirmationService`'s `!= PENDING_CONFIRMATION` check is single-transition, not ranked). RESEARCH.md's Pitfall 3 explicitly flags this as genuinely new — planner should write a `FULFILLMENT_RANK` map or `rank()` enum method per the RESEARCH.md recommendation rather than search further for a copy-paste source. |
| Per-item snapshot in a single outbound event carrying an aggregate's full child-collection state (`KitchenTicketStatusChangedEvent.items[]`) | model (event record) | event-driven | Closest precedents (`OrderCreatedEvent`, `SettleTriggerEvent`) each carry either a full order manifest (created-once) or a single-line trigger (routing-only) — neither is "republish full current state of a mutable child collection on every single-child change." This is RESEARCH.md's Open Question #1; the shape is a judgment call, not a literal copy. |

## Conventions

Convention derivation could not run: `node bin/gsd-tools.cjs verify conventions --derive` (invoked
both scoped to `src/main/java/com/example/feat1/DDD` and repo-wide) returned
`{ "skipped": true, "reason": "no-readable-files", "axes": [] }` in both cases — the shared
deterministic module's file-reader does not recognize this project's `.java` sources as an analyzable
axis-derivation target. No `## Conventions` table could be produced this session.

**Manual observation (not tool-derived, offered as a substitute since the tool skipped):** every file
read this session is 100% consistent on: file naming (`PascalCase.java` matching the public type),
identifier casing (`camelCase` fields/methods, `UPPER_SNAKE_CASE` constants), export style (one public
top-level type per file, package-private helper types nested inside), and import style (explicit
per-class imports, no wildcard `import ...*;`, `java.*` imports before third-party before
`com.example.feat1.*`, alphabetized within each group). No repo-wide contested hotspot equivalent to
the plugin's own CJS↔SDK dual-resolver split was found in this Java codebase — treat the above as a
single dominant convention (effectively 100% share) rather than a contested one, since it was
observed directly in every one of the ~20 files read for this pattern map with zero deviation.

## Metadata

**Analog search scope:** `src/main/java/com/example/feat1/DDD/order_context/**`,
`.../inventory_context/**`, `.../table_context/**`, `.../auth/infrastructure/security/**`,
`.../common/exception/**`
**Files scanned (read in full or targeted range):** 24 —
`SettleTriggerEvent.java`, `StockReservationEntity.java`, `OrderConfirmationService.java`,
`OrderCreatedEvent.java`, `OrderEventPublisher.java`, `KafkaOrderEventPublisher.java`,
`OrderKafkaProducerConfig.java`, `OrderStockResultListener.java`, `OrderKafkaConsumerConfig.java`,
`OrderStatus.java`, `OrderProcessedEventEntity.java`, `OrderProcessedEventRepository.java`,
`OrderEntity.java`, `OrderLineEntity.java`, `OrderSubmissionService.java`,
`StockReservationRepository.java`, `InventoryLedgerWriter.java`, `OrderDomainException.java`,
`AppException.java`, `SecurityConfig.java` (excerpt), `InventoryKafkaProducerConfig.java`,
`InventoryKafkaTopicConfig.java`, `InventoryReservationSettlementService.java`,
`OrderLineLookupAdapter.java`, `OrderLineToppingSnapshot.java`, `TableOperationService.java`
(3 excerpts), `TableOperationController.java`
**Pattern extraction date:** 2026-07-08
