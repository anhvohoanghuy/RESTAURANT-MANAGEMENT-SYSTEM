# Phase 16: Kitchen Preparing Workflow — Pattern Map

**Mapped:** 2026-07-07
**Files analyzed:** 19 (new/modified)
**Analogs found:** 19 / 19 (every file has a strong, directly-reusable analog; this phase is a
structural clone of Phase 15's saga machinery pointed at a new event/entity pair)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `order_context/domain/model/OrderStatus.java` (add `PREPARING`) | model (enum) | CRUD | same file (modify in place) | exact |
| `order_context/domain/model/OrderLineStatus.java` (NEW enum) | model (enum) | CRUD | `order_context/domain/model/OrderStatus.java` | exact |
| `order_context/infrastructure/entity/OrderLineEntity.java` (add `status` column) | model (JPA entity) | CRUD | same file (modify in place); column-add style from `InventoryStockBalanceEntity.java` | exact |
| `order_context/application/event/OrderLinePreparingEvent.java` (NEW) | event contract (record) | event-driven | `order_context/application/event/OrderStockResultEvent.java` | exact |
| `order_context/domain/port/OrderLinePreparingPublisher.java` (NEW) | port (interface) | event-driven | `inventory_context/domain/port/InventoryStockResultPublisher.java` / `order_context/domain/port/OrderEventPublisher.java` | exact |
| `order_context/infrastructure/adapter/KafkaOrderLinePreparingPublisher.java` (NEW) | adapter (Kafka producer) | event-driven | `inventory_context/infrastructure/adapter/KafkaInventoryStockResultPublisher.java` | exact |
| `order_context/application/OrderLinePreparingService.java` (NEW) | service | request-response + event-driven (after-commit publish) | `order_context/application/OrderSubmissionService.java` (status transition + after-commit publish) | exact |
| `order_context/infrastructure/presentation/OrderLinePreparingController.java` (NEW, or fold into `OrderController`) | controller | request-response | `table_context/infrastructure/presentation/AdminTableController.java` (admin-role-gated REST controller shape); `order_context/infrastructure/presentation/OrderController.java` (Order Context REST conventions) | role-match |
| `order_context/infrastructure/config/OrderLinePreparingKafkaProducerConfig.java` (NEW) | config (Kafka producer) | event-driven | `order_context/infrastructure/config/OrderKafkaProducerConfig.java` | exact |
| `order_context/domain/model/OrderDomainException.java` (add new error codes) | model (exception) | request-response | same file (modify in place) | exact |
| `inventory_context/domain/model/InventoryMovementType.java` (add `CONSUMPTION`) | model (enum) | CRUD | same file (modify in place) | exact |
| `inventory_context/infrastructure/entity/StockReservationEntity.java` (add `SETTLED` to `ReservationStatus`) | model (JPA entity) | CRUD | same file (modify in place — enum-only addition, D-05 forbids touching `ReservationLine`) | exact |
| `inventory_context/infrastructure/repository/StockReservationRepository.java` (add `lockByOrderId`) | repository | CRUD (locked read) | `inventory_context/infrastructure/repository/InventoryStockBalanceRepository.java` (`lockByIngredientAndLocation` — the exact `@Lock(PESSIMISTIC_WRITE)` + `@Query` idiom to copy) | exact |
| `inventory_context/infrastructure/entity/ReservationSettlementEntity.java` (NEW) | model (JPA entity, settlement guard) | CRUD | `inventory_context/infrastructure/entity/InventoryProcessedEventEntity.java` (unique-constraint ledger-row shape) | exact |
| `inventory_context/infrastructure/repository/ReservationSettlementRepository.java` (NEW) | repository | CRUD | `inventory_context/infrastructure/repository/StockReservationRepository.java` | exact |
| `inventory_context/application/InventorySettlementService.java` (NEW) | service | request-response (consumer handler) + CRUD (pessimistic-lock deduction) | `inventory_context/application/InventoryReservationService.java` | exact |
| `inventory_context/application/SettlementLedgerGuard.java` (NEW, `REQUIRES_NEW` helper — WR-01 fix) | service (transactional helper) | CRUD | Code Examples in `16-RESEARCH.md` (no in-repo `REQUIRES_NEW` analog exists yet — this is the first one); structurally mirrors the ledger-insert block in `InventoryReservationService.java:83-95` | partial (pattern documented in RESEARCH, not yet instantiated anywhere in repo) |
| `inventory_context/infrastructure/adapter/OrderLinePreparingListener.java` (NEW) | adapter (thin `@KafkaListener`) | event-driven | `inventory_context/infrastructure/adapter/OrderCreatedListener.java` | exact |
| `inventory_context/infrastructure/config/InventoryLinePreparingConsumerConfig.java` (NEW) | config (Kafka consumer) | event-driven | `inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java` | exact |
| Test: `OrderLinePreparingServiceTest.java` (NEW) | test | unit | `order_context` has no direct service-transition unit test to mirror 1:1 for style; use `InventoryReservationServiceTest.java`'s plain-Mockito/no-Spring-context style | role-match |
| Test: `InventorySettlementServiceTest.java` (NEW) | test | unit | `inventory_context/application/InventoryReservationServiceTest.java` | exact |
| Test: `InventoryLinePreparingConsumerConfigTest.java` (NEW) | test | config unit | `inventory_context/infrastructure/config/InventoryKafkaConsumerConfigTest.java` | exact |
| Test: `EventSerdeRoundTripTest.java` (extend with new method) | test | serde round-trip | same file, add `orderLinePreparingEventSurvivesRoundTrip()` method | exact |

## Pattern Assignments

### `order_context/domain/model/OrderStatus.java` + `OrderLineStatus.java` (model, CRUD)

**Analog:** `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java` (read
in full — 8 lines):
```java
package com.example.feat1.DDD.order_context.domain.model;

public enum OrderStatus {
  SUBMITTED,
  PENDING_CONFIRMATION,
  CONFIRMED,
  REJECTED
}
```
Add `PREPARING` as a new constant (append, do not reorder — `@Enumerated(EnumType.STRING)` on
`OrderEntity.status` means ordinal position is irrelevant but existing string values must not
change). Create `OrderLineStatus` as a sibling file in the same package with the same bare-enum
style (`PENDING`, `PREPARING`).

---

### `order_context/infrastructure/entity/OrderLineEntity.java` (model/JPA entity, CRUD)

**Analog:** same file (modify in place). Current shape (relevant excerpt, lines 22–63):
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
Add a status column following the exact `OrderEntity.status` idiom (see below) — same
`@Enumerated(EnumType.STRING)` + non-null default:
```java
// Mirror OrderEntity.java:35-37 for the enum-column idiom:
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private OrderLineStatus status = OrderLineStatus.PENDING;
```
`ddl-auto=update` (confirmed project-wide, no migration framework) means a plain new `@Column`
addition is sufficient — no Flyway/Liquibase migration file needed, consistent with every other
column added in Phase 14/15.

---

### `order_context/application/event/OrderLinePreparingEvent.java` (event contract)

**Analog:** `src/main/java/com/example/feat1/DDD/order_context/application/event/OrderStockResultEvent.java`
(full file, 26 lines):
```java
package com.example.feat1.DDD.order_context.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderStockResultEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID orderId,
    Result result,
    List<Shortfall> shortfalls) {
  public static final String CONFIRMED_TYPE = "OrderStockConfirmed";
  public static final String REJECTED_TYPE = "OrderStockRejected";

  public enum Result {
    CONFIRMED,
    REJECTED
  }

  public record Shortfall(
      UUID ingredientId, String ingredientName, BigDecimal required, BigDecimal available) {}
}
```
**Copy this exact shape**: top-level `record` with `eventId`/`eventType`/`occurredAt` envelope
fields, a `public static final String TYPE` constant, nested records for structured payload. Per
RESEARCH's Open Question #1 resolution, add a `totalLines` (or `orderLineIds`) field so Inventory
can detect "last line settled" without a synchronous cross-context call — model the per-line
payload after `OrderCreatedEvent.OrderLine`/`OrderTopping` (dish id + selected toppings + line
quantity) so `InventorySettlementService` can re-run recipe resolution scoped to one line.

---

### `order_context/domain/port/OrderLinePreparingPublisher.java` (port)

**Analog:** `src/main/java/com/example/feat1/DDD/inventory_context/domain/port/InventoryStockResultPublisher.java`
(full file, 14 lines):
```java
package com.example.feat1.DDD.inventory_context.domain.port;

import com.example.feat1.DDD.order_context.application.event.OrderStockResultEvent;

public interface InventoryStockResultPublisher {
  void publishStockResult(OrderStockResultEvent event);
}
```
One-method port interface — copy verbatim shape (`publish<Noun>(Event event)`), placed in
`order_context.domain.port` alongside the existing `OrderEventPublisher`:
```java
package com.example.feat1.DDD.order_context.domain.port;
import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
public interface OrderEventPublisher {
  void publishOrderCreated(OrderCreatedEvent event);
}
```

---

### `order_context/infrastructure/adapter/KafkaOrderLinePreparingPublisher.java` (adapter)

**Analog:** `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/adapter/KafkaInventoryStockResultPublisher.java`
(full file, 33 lines):
```java
@Component
@RequiredArgsConstructor
public class KafkaInventoryStockResultPublisher implements InventoryStockResultPublisher {
  private final KafkaTemplate<String, OrderStockResultEvent> orderStockResultKafkaTemplate;

  @Value("${inventory.events.order-stock-results-topic:inventory.order-stock-results}")
  private String topic;

  @Override
  public void publishStockResult(OrderStockResultEvent event) {
    orderStockResultKafkaTemplate.send(topic, event.orderId().toString(), event);
  }
}
```
Copy verbatim: `@Component` + `@RequiredArgsConstructor`, inject the typed `KafkaTemplate<String,
OrderLinePreparingEvent>`, `@Value` topic name with a literal default, `send(topic, orderId.toString(),
event)` keyed by orderId for per-order ordering. New topic name (Claude's Discretion, RESEARCH
recommendation): `orders.line-preparing`.

---

### `order_context/application/OrderLinePreparingService.java` (service — the core new logic)

**Analog:** `src/main/java/com/example/feat1/DDD/order_context/application/OrderSubmissionService.java`
— specifically its status-set + after-commit-publish shape (lines 43–83, 225–237):
```java
@Transactional
public SubmittedOrderResponse submit(UUID userId) {
  ...
  OrderEntity saved = orderRepository.save(order);
  ...
  SubmittedOrderResponse response = toResponse(saved);
  publishAfterCommit(toEvent(response));
  return response;
}
...
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
Also pull the **status-guard idiom** from `OrderConfirmationService.onStockResult` (lines 38–72) —
load entity, check current status, reject/no-op on invalid state, mutate, done (no explicit save
call needed — the entity is JPA-managed inside the `@Transactional` method, dirty-checking flushes
on commit):
```java
@Transactional
public void onStockResult(OrderStockResultEvent event) {
  ...
  Optional<OrderEntity> maybeOrder = orderRepository.findById(event.orderId());
  if (maybeOrder.isEmpty()) { return; }
  OrderEntity order = maybeOrder.get();
  if (order.getStatus() != OrderStatus.PENDING_CONFIRMATION) { return; }
  if (event.result() == Result.CONFIRMED) {
    order.setStatus(OrderStatus.CONFIRMED);
  } else {
    order.setStatus(OrderStatus.REJECTED);
    order.setRejectionReason(describe(event.shortfalls()));
  }
}
```
**New service shape:** load `OrderEntity` (with lines), guard `order.status ∈ {CONFIRMED,
PREPARING}` and target `line.status == PENDING` (D-02), set `line.status = PREPARING`, if
`order.status == CONFIRMED` also set `order.status = PREPARING` (D-01/D-06), build
`OrderLinePreparingEvent` with `totalLines = order.getLines().size()`, `publishAfterCommit(event)`.
Throw `OrderDomainException`-style exceptions (new codes, e.g. `ORDER_NOT_PREPARABLE`,
`ORDER_LINE_ALREADY_PREPARING`) for invalid-state guards, mirroring the existing
`OrderDomainException` static-factory idiom (see below).

---

### `order_context/domain/model/OrderDomainException.java` (new error codes)

**Analog:** same file (modify in place — full file, 53 lines, already read). Copy the exact
static-factory idiom:
```java
public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
...
public static OrderDomainException orderNotFound() {
  return new OrderDomainException(ORDER_NOT_FOUND, "Order was not found", HttpStatus.NOT_FOUND);
}
```
Add e.g. `ORDER_NOT_PREPARABLE` (400), `ORDER_LINE_NOT_FOUND` (404),
`ORDER_LINE_ALREADY_PREPARING` (400) following this exact `CODE constant` + `private constructor` +
`public static factory(...)` triad.

---

### `order_context/infrastructure/presentation/OrderLinePreparingController.java` (controller)

**Analog A (admin-role-gated shape):** `src/main/java/com/example/feat1/DDD/table_context/infrastructure/presentation/AdminTableController.java`
(lines 1–35):
```java
@RestController
@RequestMapping("/admin/tables")
@RequiredArgsConstructor
public class AdminTableController {
  private final TableCatalogService tableCatalogService;

  @PostMapping("/areas")
  public ResponseEntity<DiningAreaResponse> createArea(@RequestBody DiningAreaRequest request) {
    return ResponseEntity.ok(tableCatalogService.createArea(request));
  }
  ...
}
```
**Analog B (Order Context REST conventions, incl. `@AuthenticationPrincipal`):**
`src/main/java/com/example/feat1/DDD/order_context/infrastructure/presentation/OrderController.java`
(full file, 41 lines):
```java
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
  private final OrderSubmissionService orderSubmissionService;

  @PostMapping
  public ResponseEntity<SubmittedOrderResponse> submit(
      @AuthenticationPrincipal CustomUserDetails principal) {
    return ResponseEntity.ok(orderSubmissionService.submit(principal.getId()));
  }
  ...
}
```
**Critical placement constraint (security):** put the new endpoint under
`/admin/orders/**` — already gated `hasAnyRole("ADMIN", "STAFF")` in `SecurityConfig` (see Shared
Patterns below) — **zero `SecurityConfig` changes required**. Do NOT nest under `/orders/**`
(customer-facing, includes plain `USER` role — would let any customer trigger kitchen actions).
Suggested shape: `POST /admin/orders/{orderId}/lines/{lineId}/prepare`, no request body, staff actor
id from `@AuthenticationPrincipal CustomUserDetails principal` if actor traceability on the
movement/event is desired.

---

### `order_context/infrastructure/config/OrderLinePreparingKafkaProducerConfig.java` (Kafka producer config)

**Analog:** `src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaProducerConfig.java`
(full file, 34 lines — copy verbatim, retype for the new event):
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
}
```
**Critical (Pitfall 4 / WR-05):** must define this EXPLICIT bean pair — never rely on Boot's
auto-configured default `KafkaTemplate` (global `application.properties` still sets the Jackson-2
`JsonSerializer`, which cannot serialize the event's `Instant` field).

---

### `inventory_context/domain/model/InventoryMovementType.java` (add `CONSUMPTION`)

**Analog:** same file (modify in place — full file, 34 lines, already read):
```java
public enum InventoryMovementType {
  RECEIPT,
  ADJUSTMENT_IN,
  ADJUSTMENT_OUT,
  WASTE,
  STOCK_COUNT;

  public boolean isInbound() { return this == RECEIPT || this == ADJUSTMENT_IN; }
  public boolean isOutbound() { return this == ADJUSTMENT_OUT || this == WASTE; }
  public boolean isCount() { return this == STOCK_COUNT; }
}
```
Add `CONSUMPTION` as a new constant and include it in `isOutbound()`'s disjunction (it decreases
`quantity_on_hand`, same polarity as `WASTE`/`ADJUSTMENT_OUT` — A3 in RESEARCH flags this should be
a distinct value from `WASTE`/`ADJUSTMENT_OUT` for reporting accuracy).

---

### `inventory_context/infrastructure/entity/StockReservationEntity.java` (add `SETTLED`)

**Analog:** same file (modify in place — full file, 108 lines, already read). Only touch the
enum:
```java
public enum ReservationStatus {
  HELD
}
```
→ add `SETTLED`. **Do NOT touch** `ReservationLine` (embeddable) or the `lines` collection — D-05
explicitly forbids changing the reservation's per-ingredient-quantity shape. Settlement progress
tracking belongs in the new sibling `ReservationSettlementEntity`, not here.

---

### `inventory_context/infrastructure/repository/StockReservationRepository.java` (add `lockByOrderId`)

**Analog for the `@Lock` idiom:** `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/repository/InventoryStockBalanceRepository.java`
(lines 18–26):
```java
/**
 * Acquires a pessimistic write lock on the balance row so concurrent reservers are serialized and
 * cannot drive available stock negative (D-02).
 */
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query(
    "select b from InventoryStockBalanceEntity b "
        + "where b.ingredient.id = :ingredientId and b.locationCode = :loc")
Optional<InventoryStockBalanceEntity> lockByIngredientAndLocation(UUID ingredientId, String loc);
```
Current `StockReservationRepository` (full file, 14 lines):
```java
public interface StockReservationRepository extends JpaRepository<StockReservationEntity, UUID> {
  boolean existsByOrderId(UUID orderId);
  Optional<StockReservationEntity> findByOrderId(UUID orderId);
}
```
Add, following the exact same `@Lock(PESSIMISTIC_WRITE)` + `@Query` idiom (Pitfall 2 in RESEARCH —
needed to make "am I the last line" check atomic):
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select r from StockReservationEntity r where r.orderId = :orderId")
Optional<StockReservationEntity> lockByOrderId(UUID orderId);
```

---

### `inventory_context/infrastructure/entity/ReservationSettlementEntity.java` (NEW — settlement guard)

**Analog:** `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryProcessedEventEntity.java`
(full file, 46 lines — copy the unique-constraint ledger idiom):
```java
@Getter
@Setter
@Entity
@Table(
    name = "inventory_processed_events",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_inventory_processed_event",
            columnNames = {"event_id", "consumer_name"}))
public class InventoryProcessedEventEntity {
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
New entity: unique `(order_id, order_line_id)` constraint (the D-03 per-line double-settlement
guard), plus a `settledAt` timestamp. Table name suggestion: `reservation_settlements`.

---

### `inventory_context/application/InventorySettlementService.java` (NEW — the core new logic)

**Analog:** `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java`
(full file, 249 lines, already read in full). Copy these four sub-patterns directly:

**(a) Imports/class shape** (lines 1–69):
```java
@Service
@RequiredArgsConstructor
public class InventoryReservationService {
  private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);
  public static final String CONSUMER_NAME = "inventory-order-created";
  private static final int QUANTITY_SCALE = 6;
  private static final String DEFAULT_LOCATION = InventoryStockBalanceEntity.DEFAULT_LOCATION;

  private final InventoryProcessedEventRepository processedEventRepository;
  private final StockReservationRepository reservationRepository;
  private final InventoryStockBalanceRepository balanceRepository;
  private final IngredientRepository ingredientRepository;
  private final MenuRecipeCostingPort menuRecipeCostingPort;
  private final InventoryStockResultPublisher stockResultPublisher;
```
New service additionally injects `InventoryStockMovementRepository` (WR-02 audit),
`ReservationSettlementRepository` (new guard), and `SettlementLedgerGuard` (WR-01 fix helper).

**(b) Recipe re-resolution, scoped to one line** (lines 158–222,
`computeRequired`/`accumulateRecipe` — reuse verbatim or duplicate with a cross-reference comment
per RESEARCH Pattern 3; the missing-recipe/null-ingredient/unconvertible-unit → zero-contribution,
log, never-throw tolerance must be preserved identically):
```java
private void accumulateRecipe(
    Map<UUID, BigDecimal> required, RecipeTargetType targetType, UUID targetId, int orderLineQuantity) {
  if (targetId == null) { return; }
  Optional<RecipeCostingSnapshot> recipe = menuRecipeCostingPort.findRecipe(targetType, targetId);
  if (recipe.isEmpty()) { log.debug(...); return; }
  for (RecipeCostingSnapshot.Line line : recipe.get().lines()) {
    UUID ingredientId = line.ingredientId();
    if (ingredientId == null) { continue; }
    Optional<IngredientEntity> ingredient = ingredientRepository.findById(ingredientId);
    if (ingredient.isEmpty()) { log.debug(...); continue; }
    String baseUnit = ingredient.get().getBaseUnit();
    BigDecimal converted;
    try {
      converted = UnitConverter.convert(line.quantity(), line.unit(), baseUnit);
    } catch (InventoryDomainException unconvertible) { log.debug(...); continue; }
    BigDecimal contribution = converted.multiply(BigDecimal.valueOf(orderLineQuantity));
    required.merge(ingredientId, contribution, BigDecimal::add);
  }
}
```

**(c) Sorted-ingredient-id pessimistic-lock loop** (lines 100–133 — SAME canonical ascending-UUID
iteration order for lock acquisition, deadlock avoidance, reused verbatim but this time
**subtracting** instead of adding, and clamping at zero per Pattern 4 in RESEARCH):
```java
List<UUID> sortedIngredientIds = required.keySet().stream().sorted().toList();
for (UUID ingredientId : sortedIngredientIds) {
  Optional<InventoryStockBalanceEntity> balance =
      balanceRepository.lockByIngredientAndLocation(ingredientId, DEFAULT_LOCATION);
  ...
}
```

**(d) Idempotency guard + `publishAfterCommit`-equivalent** — the fast pre-check / insert+flush /
catch-`DataIntegrityViolationException` idiom (lines 76–95) is the exact WR-01 defect; DO NOT copy
it unmodified — apply the `SettlementLedgerGuard.tryRecord(...)` `REQUIRES_NEW` fix instead (see
Shared Patterns → WR-01 below). The `publishAfterCommit`/`TransactionSynchronizationManager`
pattern (lines 231–243) does NOT apply symmetrically here — Inventory's settlement service is a
**consumer**, not a producer; no outbound publish is required per the current decisions (D-01..D-06
name no result event for settlement). Confirm with the plan whether a settlement-result event is
in-scope; if not, omit this half of the pattern.

**New method shape (`onLinePreparing(OrderLinePreparingEvent event)`):**
1. fast idempotency pre-check (`processedEventRepository.existsByEventIdAndConsumerName`)
2. `settlementLedgerGuard.tryRecord(eventId, CONSUMER_NAME)` — REQUIRES_NEW, no-op if duplicate
3. `reservationRepository.lockByOrderId(orderId)` (PESSIMISTIC_WRITE) — missing → **throw** (routes
   to DLT, per D-03's "do not silently swallow")
4. `reservationSettlementRepository.existsByOrderIdAndOrderLineId(...)` — no-op if already settled
5. `computeRequiredForLine(event.line())` (per-line recipe resolution, see (b))
6. sorted-ingredient-id lock loop (see (c)), decrement `on_hand` and `reserved`, clamp at zero + log
   anomaly (Pattern 4), record `InventoryStockMovementEntity` (WR-02, see below)
7. save a `ReservationSettlementEntity` row for `(orderId, orderLineId)`
8. count settlement rows for the order vs. `event.totalLines()`; if all settled, set
   `reservation.status = SETTLED` and force every touched ingredient's residual `reserved_quantity`
   contribution to zero (Pitfall 3 — drift tolerance)

---

### `inventory_context/application/SettlementLedgerGuard.java` (NEW — WR-01 fix)

**Source of the fix shape:** `16-RESEARCH.md` "Code Examples" section (verbatim, reproduced here
since no in-repo analog exists yet — this is the FIRST `REQUIRES_NEW` usage in the codebase):
```java
@Service
@RequiredArgsConstructor
public class SettlementLedgerGuard {
  private final InventoryProcessedEventRepository processedEventRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean tryRecord(UUID eventId, String consumerName) {
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
The **defective** idiom this fixes (do not copy unmodified) is
`InventoryReservationService.java:83-95`'s inline try/catch inside the SAME `@Transactional`
method — see Shared Patterns below for the full defect analysis.

---

### `inventory_context/infrastructure/adapter/OrderLinePreparingListener.java` (thin listener)

**Analog:** `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/adapter/OrderCreatedListener.java`
(full file — same shape as `OrderStockResultListener.java`, both read in full):
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
Copy verbatim: `@Component` + `@RequiredArgsConstructor`, one `@KafkaListener` method, one-line
delegate to the service, zero business logic in the listener itself (established convention,
called out as a strength in `15-REVIEW.md`).

---

### `inventory_context/infrastructure/config/InventoryLinePreparingConsumerConfig.java` (Kafka consumer config)

**Analog:** `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java`
(full file, 112 lines, already read in full). Copy verbatim, retyped for
`OrderLinePreparingEvent` and a new `TRUSTED_PACKAGES` value (`com.example.feat1.DDD.order_context.application.event` —
same package as the source event, no change needed since the new event lives in the same package):
```java
@Configuration
@EnableKafka
public class InventoryKafkaConsumerConfig {
  @Bean
  public ConsumerFactory<String, OrderCreatedEvent> orderCreatedConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
      @Value("${inventory.order-created.consumer.group-id:inventory-order-created}") String groupId) {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
    props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.example.feat1.DDD.order_context.application.event");
    props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, OrderCreatedEvent.class.getName());
    props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, Object> inventoryDltKafkaTemplate(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @Bean
  public DefaultErrorHandler orderCreatedErrorHandler(
      @Qualifier("inventoryDltKafkaTemplate") KafkaTemplate<String, Object> dlt) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dlt);
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    handler.addNotRetryableExceptions(DeserializationException.class);
    return handler;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent>
      orderCreatedKafkaListenerContainerFactory(
          ConsumerFactory<String, OrderCreatedEvent> orderCreatedConsumerFactory,
          DefaultErrorHandler orderCreatedErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(orderCreatedConsumerFactory);
    factory.setCommonErrorHandler(orderCreatedErrorHandler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    return factory;
  }
}
```
Give the new DLT `KafkaTemplate` bean a **distinctly-named** bean (e.g.
`inventoryLinePreparingDltKafkaTemplate`) — multiple `KafkaTemplate<String, Object>` beans already
coexist app-wide (documented as W-4 in Phase 15), so bean-name collision must be avoided.

---

### Test files

**`OrderLinePreparingServiceTest.java`** — style analog:
`src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationServiceTest.java`
(setUp block, lines 48–70 — plain Mockito, no Spring context, `mock(Repo.class)` + stub defaults +
`when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0))` echo-save idiom).

**`InventorySettlementServiceTest.java`** — direct analog, same file as above, full structure
(mock every repository/port dependency, stub the happy-path resolution chain, assert on captured
saved/published arguments via `ArgumentCaptor`).

**`InventoryLinePreparingConsumerConfigTest.java`** — direct analog:
`src/test/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaConsumerConfigTest.java`
(full file, 80 lines — broker-free, direct `new InventoryKafkaConsumerConfig()` instantiation,
asserts config-property map values and `handler.removeClassification(DeserializationException.class)
== false`).

**`EventSerdeRoundTripTest.java`** (extend) — direct analog: same file (26–70 read), add a new
`@Test orderLinePreparingEventSurvivesRoundTrip()` method following the exact `roundTrip(value,
type)` helper + `JacksonJsonSerializer`/`JacksonJsonDeserializer` try-with-resources idiom.

## Shared Patterns

### Thin Kafka listener → transactional service delegate
**Source:** `inventory_context/infrastructure/adapter/OrderCreatedListener.java`,
`order_context/infrastructure/adapter/OrderStockResultListener.java`
**Apply to:** `OrderLinePreparingListener` (new)
```java
@Component
@RequiredArgsConstructor
public class OrderCreatedListener {
  private final InventoryReservationService reservationService;

  @KafkaListener(
      topics = "${order.events.order-created-topic:orders.created}",
      groupId = "${inventory.order-created.consumer.group-id:inventory-order-created}",
      containerFactory = "orderCreatedKafkaListenerContainerFactory")
  public void onOrderCreated(OrderCreatedEvent event) {
    reservationService.onOrderCreated(event);
  }
}
```

### After-commit publish via `TransactionSynchronizationManager`
**Source:** `order_context/application/OrderSubmissionService.java:225-237`,
`inventory_context/application/InventoryReservationService.java:231-243` (identical in both)
**Apply to:** `OrderLinePreparingService.publishAfterCommit(...)`
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
**Known residual gap (inherited, not this phase's job to fix):** a crash between commit and Kafka
send permanently strands the entity in its pre-event state with no re-emission path (documented
WR-02-adjacent gap in `15-REVIEW.md`). Accepted, documented tradeoff — do not scope-creep an
outbox into this phase.

### WR-01 fix: isolate the idempotency-ledger insert in `REQUIRES_NEW`
**Source:** the defective idiom in `inventory_context/application/InventoryReservationService.java:83-95`
(copied below), fixed per `16-RESEARCH.md`'s Code Examples section.
```java
// THE DEFECT (do not copy unmodified into the new consumer):
if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)
    || reservationRepository.existsByOrderId(orderId)) {
  return;
}
try {
  InventoryProcessedEventEntity ledger = new InventoryProcessedEventEntity();
  ledger.setEventId(eventId);
  ledger.setConsumerName(CONSUMER_NAME);
  ledger.setProcessedAt(Instant.now());
  processedEventRepository.saveAndFlush(ledger);   // <-- inside the OUTER @Transactional method
} catch (DataIntegrityViolationException duplicate) {
  return; // BUG: JpaTransactionManager already marked the outer tx rollback-only by this point
}
```
**Apply to:** `InventorySettlementService.onLinePreparing(...)` via the new
`SettlementLedgerGuard.tryRecord(eventId, consumerName)` bean method
(`@Transactional(propagation = Propagation.REQUIRES_NEW)`) — see the Pattern Assignment above for
the full corrected shape. This is THE defining structural change this phase's consumer must make
relative to its Phase 15 analog.

### WR-02 fix: record an audit movement for every settlement deduction
**Source:** `inventory_context/application/InventoryStockService.java:99-113` (the exact
movement-recording shape to mirror, adapted for a recipe-resolved quantity instead of an
operator-entered one):
```java
InventoryStockMovementEntity movement = new InventoryStockMovementEntity();
movement.setIngredient(ingredient);
movement.setLocationCode(DEFAULT_LOCATION);
movement.setMovementType(type);
movement.setQuantity(scale(quantity));
movement.setUnit(UnitConverter.normalizeUnit(request.unit()));
movement.setBaseQuantityDelta(delta);
movement.setBaseUnit(baseUnit);
movement.setResultingBalance(resulting);
movement.setReferenceType(blankToNull(request.referenceType()));
movement.setReferenceId(request.referenceId());
movement.setActorId(actorId);
movement.setCreatedAt(now);
movementRepository.save(movement);
```
**Apply to:** every ingredient touched during `InventorySettlementService`'s settlement loop —
`movementType = InventoryMovementType.CONSUMPTION` (new value), `referenceType =
"ORDER_LINE_SETTLEMENT"`, `referenceId = orderLineId`, `baseQuantityDelta = need.negate()`.

### Path-based role gating (100% of the codebase, zero `@PreAuthorize` usage anywhere)
**Source:** `auth/infrastructure/security/SecurityConfig.java:61-62` (already covers the target
path — no `SecurityConfig` edit needed if the new endpoint is placed correctly):
```java
.requestMatchers("/admin/payments", "/admin/payments/**", "/admin/orders/**")
.hasAnyRole("ADMIN", "STAFF")
```
**Apply to:** `OrderLinePreparingController` — place under `/admin/orders/**`. There is no
`KITCHEN` role in this codebase; roles are exactly `USER`, `ADMIN`, `STAFF`
(`SecurityConfig.java:50,54,62,64,74,78`).

### Recipe re-resolution (dish + toppings → ingredient base quantities)
**Source:** `inventory_context/application/InventoryReservationService.java:158-222`
(`computeRequired`/`accumulateRecipe`) — the exact conversion chain
(`MenuRecipeCostingPort.findRecipe` → `UnitConverter.convert` → `× orderLineQuantity`) that both
the original per-order resolution and the new per-line resolution must share. **Recommendation**
(RESEARCH Open Question #2): extract into a shared `RecipeResolver` domain-service helper used by
both `InventoryReservationService` and `InventorySettlementService`, to avoid the two computations
silently drifting. If judged too invasive to touch Phase-15 code, duplicate with an explicit
cross-reference comment — an accepted, lower-risk fallback per RESEARCH.

### Pessimistic-lock, sorted-ingredient-id deduction loop with non-negative clamp
**Source:** `inventory_context/application/InventoryReservationService.java:100-133` (lock
acquisition order) + `16-RESEARCH.md` Pattern 4 (clamp logic, new to this phase):
```java
List<UUID> sortedIngredientIds = required.keySet().stream().sorted().toList();
...
BigDecimal newOnHand = onHand.subtract(need);
if (newOnHand.compareTo(BigDecimal.ZERO) < 0) {
  log.warn("Settlement anomaly: on_hand would go negative for ingredient {} ... clamping to zero", ...);
  newOnHand = BigDecimal.ZERO;
}
```
**Apply to:** `InventorySettlementService`'s deduction loop — same ascending-UUID iteration order
as the reservation service (deadlock avoidance), same `@Lock(PESSIMISTIC_WRITE)` repository method
(`InventoryStockBalanceRepository.lockByIngredientAndLocation`, already exists, no change needed).

## No Analog Found

None — every file in this phase's scope has at least a role-match analog in the existing
codebase (this phase is explicitly ~95% "extend existing verified patterns" per RESEARCH's
confidence assessment). The one item without a **direct, already-instantiated** in-repo example is
`SettlementLedgerGuard`'s `@Transactional(propagation = Propagation.REQUIRES_NEW)` usage — the
*pattern* is fully specified (in `16-RESEARCH.md`'s Code Examples, corroborated by direct reading of
the defect it fixes), but no existing service in this codebase currently uses `REQUIRES_NEW`
anywhere; this will be the first. Flagged for extra planner/reviewer attention, not because the
pattern is unclear, but because there is no existing test asserting `REQUIRES_NEW` semantics to
copy from (RESEARCH's Wave-0 gap table already calls for a new
`InventorySettlementIntegrationTest` with a real `PlatformTransactionManager` for this reason).

## Conventions

convention derivation skipped (no-readable-files — the shared `bin/lib/conventions.cjs` deriver
only walks JS/TS-family files (`/\.(c|m)?[jt]sx?$/`); this project is a pure Java/Maven/Spring
codebase (`src/main/java/**/*.java`), so both a repo-wide run and a `--scope
src/main/java/com/example/feat1/DDD/inventory_context` run return an empty corpus by design, not
by error. No 4-axis JS/TS convention table applies here.)

Java-specific conventions observed directly by reading the analog files above (not derived by the
tool, but consistent across every file read in this mapping):
- **File naming:** PascalCase matching the public type (`OrderConfirmationService.java`,
  `InventoryStockBalanceEntity.java`) — 100% consistent, no exceptions found.
- **Identifier casing:** camelCase fields/methods, PascalCase types, `SCREAMING_SNAKE_CASE`
  `static final` constants (`CONSUMER_NAME`, `QUANTITY_SCALE`, `DEFAULT_LOCATION`) — 100%
  consistent across every service/entity read.
- **Export style:** N/A (Java has no module export-style axis analogous to JS/TS
  named-vs-default-vs-`module.exports`).
- **Import style:** fully-qualified explicit imports, one type per line, no wildcard imports,
  static imports only in test files (`import static org.assertj.core.api.Assertions.assertThat;`)
  — 100% consistent across every file read.

**Contested hotspots (author's choice):** none applicable in this Java codebase. For reference (per
the shared-tool's own design), the prototype intentional-contested split this deriver is built to
surface elsewhere in the GSD plugin's own source is the **CJS↔SDK dual resolver**
(`bin/lib/**` is CJS `module.exports`/`require`; `sdk/src/**` is ESM `export`/`import`) — each half
is internally consistent per-directory, contested only when compared repo-wide. This phase's Java
files have no analogous split: every file in every context (`order_context`, `inventory_context`,
`table_context`, etc.) uses one uniform import/export idiom, so there is no "match the local
directory's style" ambiguity for the planner to resolve here.

## Metadata

**Analog search scope:** `src/main/java/com/example/feat1/DDD/order_context/**`,
`src/main/java/com/example/feat1/DDD/inventory_context/**`,
`src/main/java/com/example/feat1/DDD/table_context/infrastructure/presentation/**` (admin
controller shape), `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/**`
(role-gating), `src/test/java/com/example/feat1/DDD/{order_context,inventory_context}/**`
**Files scanned:** 24 read in full or targeted excerpt (all cited above with line numbers)
**Pattern extraction date:** 2026-07-07
