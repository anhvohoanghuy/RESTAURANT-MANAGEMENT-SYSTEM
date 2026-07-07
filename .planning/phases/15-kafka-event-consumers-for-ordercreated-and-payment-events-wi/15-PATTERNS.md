# Phase 15: kafka-event-consumers (order-confirmation saga) - Pattern Map

**Mapped:** 2026-07-07
**Files analyzed:** 14 (10 new, 4 modified)
**Analogs found:** 13 / 14 (1 net-new: idempotency ledger, but structural analog exists)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `order_context/domain/model/OrderStatus.java` (mod) | model (enum) | — | `order_context/domain/model/OrderStatus.java` (self) | exact |
| `order_context/application/OrderSubmissionService.java` (mod) | service | request-response | `order_context/application/OrderSubmissionService.java` (self) | exact |
| `order_context/infrastructure/config/OrderKafkaConsumerConfig.java` (new) | config | event-driven | `order_context/infrastructure/config/OrderKafkaProducerConfig.java` | role-match (producer→consumer) |
| `order_context/infrastructure/adapter/OrderStockResultListener.java` (new) | adapter (listener) | event-driven | `order_context/infrastructure/adapter/KafkaOrderEventPublisher.java` | role-match (publisher→listener) |
| `order_context/application/OrderConfirmationService.java` (new) | service | event-driven | `order_context/application/OrderSubmissionService.java` | role-match |
| `order_context/application/event/OrderStockResultEvent.java` (new) | event (record) | event-driven | `order_context/application/event/OrderCreatedEvent.java` | exact |
| `order_context/domain/port/OrderStockResultConsumerPort` (opt, new) | port | event-driven | `order_context/domain/port/OrderEventPublisher.java` | role-match |
| `inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java` (new) | config | event-driven | `order_context/infrastructure/config/OrderKafkaProducerConfig.java` | role-match |
| `inventory_context/infrastructure/config/InventoryKafkaProducerConfig.java` (new) | config | event-driven | `order_context/infrastructure/config/OrderKafkaProducerConfig.java` | exact |
| `inventory_context/infrastructure/adapter/OrderCreatedListener.java` (new) | adapter (listener) | event-driven | `order_context/infrastructure/adapter/KafkaOrderEventPublisher.java` | role-match |
| `inventory_context/infrastructure/adapter/KafkaInventoryStockResultPublisher.java` (new) | adapter (publisher) | event-driven | `order_context/infrastructure/adapter/KafkaOrderEventPublisher.java` | exact |
| `inventory_context/application/InventoryReservationService.java` (new) | service | event-driven / CRUD | `inventory_context/application/InventoryStockService.java` (+`InventoryCostingService`) | role-match |
| `inventory_context/infrastructure/entity/StockReservationEntity.java` (new) + repo | model + repository | CRUD | `inventory_context/infrastructure/entity/InventoryStockMovementEntity.java` + `InventoryStockBalanceRepository` | role-match |
| `*/infrastructure/entity/ProcessedEventEntity.java` (new) + repo | model + repository | CRUD | `inventory_context/infrastructure/entity/InventoryStockBalanceEntity.java` (unique-constraint pattern) + `InventoryStockBalanceRepository` | role-match |
| `inventory_context/infrastructure/entity/InventoryStockBalanceEntity.java` (mod: add `reserved`) | model | — | `inventory_context/infrastructure/entity/InventoryStockBalanceEntity.java` (self) | exact |

---

## Pattern Assignments

### `inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java` (config, event-driven) — NEW

**Analog:** `src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaProducerConfig.java`

The producer config is the structural template (D-05: "mirror existing producer config style"). Keep the same shape: `@Configuration` class, `@Value("${spring.kafka.bootstrap-servers:localhost:9092}")` injected into a factory-producing `@Bean`, `HashMap<String,Object>` props, typed generics. Add `@EnableKafka` and swap producer beans for `ConsumerFactory` + `ConcurrentKafkaListenerContainerFactory` + the `DefaultErrorHandler`/DLT beans.

**Imports + bootstrap-servers injection pattern to mirror** (`OrderKafkaProducerConfig.java` lines 1-26):
```java
package com.example.feat1.DDD.inventory_context.infrastructure.config;
// ...
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class OrderKafkaProducerConfig {
  @Bean
  public ProducerFactory<String, OrderCreatedEvent> orderCreatedProducerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
  }
```

**Consumer-specific additions** (from RESEARCH.md Pattern 1 & 2 — use verbatim): `@EnableKafka` on the class; `ConsumerFactory<String, OrderCreatedEvent>` with `ErrorHandlingDeserializer` wrapping `JsonDeserializer`, `JsonDeserializer.TRUSTED_PACKAGES = "com.example.feat1.DDD.order_context.application.event"`, `USE_TYPE_INFO_HEADERS=false` + `VALUE_DEFAULT_TYPE=OrderCreatedEvent`; container factory with `AckMode.RECORD`; `DefaultErrorHandler(new DeadLetterPublishingRecoverer(dltTemplate), new FixedBackOff(1000L, 3L))` with `DeserializationException` marked not-retryable. The DLT `KafkaTemplate<String,Object>` bean mirrors the producer-factory idiom above (same `ProducerConfig.*` keys).

**Event package note:** `OrderCreatedEvent` lives in `order_context.application.event` (see its FQN in the publisher import) — the inventory consumer must reference that exact package in `TRUSTED_PACKAGES` / `VALUE_DEFAULT_TYPE`.

---

### `inventory_context/infrastructure/config/InventoryKafkaProducerConfig.java` (config, event-driven) — NEW

**Analog:** `OrderKafkaProducerConfig.java` (lines 1-33) — **exact structural copy**, retyped to the result event.

Copy the file verbatim, rename to `InventoryKafkaProducerConfig`, change generic type `OrderCreatedEvent` → `OrderStockResultEvent`, rename beans (`orderStockResultProducerFactory`, `orderStockResultKafkaTemplate`). Same two-bean shape (`ProducerFactory` + `KafkaTemplate`), same `StringSerializer`/`JsonSerializer` pair (D-05 symmetry — keep Jackson-2 `org.springframework.kafka.support.serializer.JsonSerializer`).

---

### `inventory_context/infrastructure/adapter/KafkaInventoryStockResultPublisher.java` (adapter/publisher, event-driven) — NEW

**Analog:** `src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/KafkaOrderEventPublisher.java` — **exact structural copy**.

**Full pattern to copy** (`KafkaOrderEventPublisher.java` lines 1-22):
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
}
```
Adapt: implement a new `InventoryStockResultPublisher` domain port (mirror `OrderEventPublisher` — see below), inject `KafkaTemplate<String, OrderStockResultEvent>`, `@Value("${inventory.events.order-stock-results-topic:inventory.order-stock-results}")`, and key the message by `event.orderId().toString()` (same keying idiom → preserves per-order ordering).

**Port to mirror** — `order_context/domain/port/OrderEventPublisher.java` (lines 1-8): a one-method interface in `domain/port`. Create `inventory_context/domain/port/InventoryStockResultPublisher` with `void publishStockResult(OrderStockResultEvent event)`.

---

### `inventory_context/infrastructure/adapter/OrderCreatedListener.java` (adapter/listener, event-driven) — NEW

**Analog:** `KafkaOrderEventPublisher.java` (adapter package + `@Component` + `@RequiredArgsConstructor` idiom).

There is **no existing `@KafkaListener` in the codebase** (produce-only system). Use RESEARCH.md's thin-adapter rule: the listener is a one-line delegate to `InventoryReservationService`. Mirror the adapter conventions from `KafkaOrderEventPublisher` (`@Component`, `@RequiredArgsConstructor`, constructor-injected collaborator, `infrastructure/adapter` package):
```java
@Component
@RequiredArgsConstructor
public class OrderCreatedListener {
  private final InventoryReservationService reservationService;

  @KafkaListener(
      topics = "${order.events.order-created-topic:orders.created}",
      groupId = "inventory-order-created",
      containerFactory = "orderCreatedKafkaListenerContainerFactory")
  public void onOrderCreated(OrderCreatedEvent event) {
    reservationService.onOrderCreated(event); // no business logic here
  }
}
```
(Anti-pattern to avoid, per RESEARCH.md: no reservation logic inside the listener.)

---

### `inventory_context/application/InventoryReservationService.java` (service, event-driven/CRUD) — NEW

**Analogs:** `InventoryStockService.java` (atomic balance mutation, `@Transactional`, non-negative guard) **and** `InventoryCostingService.java` (recipe-port resolution + `UnitConverter`).

**Service skeleton + non-negative guard pattern** — from `InventoryStockService.java` (lines 32-97). Copy: `@Service @RequiredArgsConstructor`, constructor-injected repositories, `@Transactional` write method, `QUANTITY_SCALE = 6` + `scale()` helper, and the outbound-sufficiency check idiom (adapt "on-hand" to `available = on_hand − reserved`):
```java
} else if (type.isOutbound()) {
  if (current.compareTo(baseQuantity) < 0) {
    throw InventoryDomainException.stockInsufficient();   // never-negative gate
  }
  ...
```

**Recipe → ingredient resolution pattern** — from `InventoryCostingService.java` (lines 108-115, 160-197). Reuse the exact `MenuRecipeCostingPort.findRecipe(RecipeTargetType, targetId)` + `UnitConverter.convert(...)` path (D-06). Key excerpts:
```java
// lines 109-114 — resolve a recipe for a target
RecipeCostingSnapshot recipe =
    menuRecipeCostingPort
        .findRecipe(targetType, targetId)
        .orElseThrow(() -> new EntityNotFoundException("Recipe not found"));
```
```java
// lines 177-182 — per-line unit conversion, tolerant of unconvertible/unlinked lines
if (line.ingredientId() == null) { /* no link → contributes zero (D-06) */ }
BigDecimal converted;
try {
  converted = UnitConverter.convert(line.quantity(), line.unit(), cost.get().getCostUnit());
} catch (InventoryDomainException exception) { /* skip / zero */ }
```
For reservation, resolve **both** `RecipeTargetType.DISH` (per `line.dishId()`) and `RecipeTargetType.TOPPING_OPTION` (per each `selectedToppings[].toppingOptionId()`), multiply by `line.quantity()`, convert to the ingredient base unit, and accumulate per `ingredientId`. A line whose dish/topping has no recipe/ingredient link contributes **zero** (log, do not block) — mirrors `InventoryCostingService`'s `uncosted(...)` tolerance.

**After-commit publish pattern** — from `OrderSubmissionService.java` (lines 225-237). Copy verbatim into this service so the result event is emitted only after the reservation commits (D-10):
```java
private void publishAfterCommit(OrderStockResultEvent event) {
  if (!TransactionSynchronizationManager.isSynchronizationActive()) {
    resultPublisher.publishStockResult(event);
    return;
  }
  TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override public void afterCommit() { resultPublisher.publishStockResult(event); }
      });
}
```

**Idempotency guards** (D-03) — from RESEARCH.md Pattern 3: ledger `tryRecord(eventId, "inventory-order-created")` insert (catch `DataIntegrityViolationException` → false) + `reservations.existsByOrderId(...)` short-circuit, both inside the same `@Transactional`.

**Pessimistic lock** (D-02) — add a `@Lock(LockModeType.PESSIMISTIC_WRITE)` query to `InventoryStockBalanceRepository` (see repo section below).

---

### `order_context/application/OrderConfirmationService.java` (service, event-driven) — NEW

**Analog:** `OrderSubmissionService.java` (`@Service @RequiredArgsConstructor`, `@Transactional`, `OrderRepository` usage).

**Order lookup + mutation pattern** — from `OrderSubmissionService.java` (lines 92-98, 56-83). Load via `OrderRepository`, mutate status. RESEARCH.md gives the exact idempotent + status-guarded transition:
```java
@Transactional
public void onStockResult(OrderStockResultEvent event) {
  if (!ledger.tryRecord(event.eventId(), "order-stock-result")) return;
  OrderEntity order = orderRepository.findById(event.orderId()).orElse(null);
  if (order == null || order.getStatus() != OrderStatus.PENDING_CONFIRMATION) return; // guard
  order.setStatus(event.result() == Result.CONFIRMED
      ? OrderStatus.CONFIRMED : OrderStatus.REJECTED);
  if (event.result() == Result.REJECTED) order.setRejectionReason(describe(event.shortfalls()));
}
```
**Note:** `OrderRepository` currently exposes only `findByUserIdOrderBySubmittedAtDesc` and `findByIdAndUserId` (lines 9-13). Add a `findById` usage (inherited from `JpaRepository`) — no new signature needed. `OrderEntity` has no `rejectionReason` column yet; adding one is a small entity mod mirroring the nullable-column style in `OrderEntity` (lines 42-58).

---

### `order_context/infrastructure/config/OrderKafkaConsumerConfig.java` (config, event-driven) — NEW

**Analog:** `OrderKafkaProducerConfig.java` — same as the inventory consumer config. Typed to `OrderStockResultEvent`, `TRUSTED_PACKAGES = "com.example.feat1.DDD.order_context.application.event"` (result event lives in order context), `groupId "order-stock-result"`, its own `DefaultErrorHandler` + DLT template. See the inventory-consumer-config assignment above for the full excerpt (identical structure).

---

### `order_context/infrastructure/adapter/OrderStockResultListener.java` (adapter/listener, event-driven) — NEW

**Analog:** `KafkaOrderEventPublisher.java` (adapter conventions) + the `OrderCreatedListener` shape above. One-line delegate to `OrderConfirmationService.onStockResult(event)`; `containerFactory` bean name from `OrderKafkaConsumerConfig`; `groupId "order-stock-result"`.

---

### `order_context/application/event/OrderStockResultEvent.java` (event record) — NEW

**Analog:** `src/main/java/com/example/feat1/DDD/order_context/application/event/OrderCreatedEvent.java` — **exact structural copy**.

**Record + nested-record + `TYPE` constant pattern** (`OrderCreatedEvent.java` lines 1-40):
```java
public record OrderCreatedEvent(
    UUID eventId, String eventType, Instant occurredAt,
    UUID orderId, UUID userId, OrderTable table,
    List<OrderLine> lines, BigDecimal total, Instant submittedAt) {
  public static final String TYPE = "OrderCreated";
  public record OrderTable(...) {}
  public record OrderLine(...) {}
  public record OrderTopping(...) {}
}
```
Follow RESEARCH.md's shape: top-level `eventId/eventType/occurredAt/orderId`, a `Result { CONFIRMED, REJECTED }` enum, a nested `Shortfall` record, and `CONFIRMED_TYPE`/`REJECTED_TYPE` string constants — mirroring the `TYPE` constant convention. Place in `order_context.application.event` (shared, so both contexts import it; the consumer's `TRUSTED_PACKAGES` must list this package).

---

### `inventory_context/infrastructure/entity/StockReservationEntity.java` (+ repository) (model + repository, CRUD) — NEW

**Analog:** `InventoryStockMovementEntity.java` (entity conventions) + `InventoryStockBalanceEntity.java` (unique-constraint) + `InventoryStockBalanceRepository.java`.

**Entity conventions to copy** — `InventoryStockMovementEntity.java` (lines 26-40) and `InventoryStockBalanceEntity.java` (lines 23-47): `@Getter @Setter @Entity @Table`, `@Id @GeneratedValue(strategy = GenerationType.UUID) UUID id`, `@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(...)` for the ingredient link, `BigDecimal ... precision = 18, scale = 6`, `Instant createdAt`. For the per-order unique guard (D-03), copy the unique-constraint idiom from `InventoryStockBalanceEntity` (lines 26-31):
```java
@Table(
    name = "stock_reservations",
    uniqueConstraints =
        @UniqueConstraint(name = "uq_stock_reservation_order",
            columnNames = {"order_id"}))
```

**Repository conventions to copy** — `InventoryStockBalanceRepository.java` (lines 10-22): `interface ... extends JpaRepository<Entity, UUID>` with derived query methods. Add `boolean existsByOrderId(UUID orderId)` and any `findByOrderId`.

---

### `*/infrastructure/entity/ProcessedEventEntity.java` (+ repository) (model + repository, CRUD) — NEW

**Analog:** `InventoryStockBalanceEntity.java` (unique-constraint entity) + `InventoryStockBalanceRepository.java` (derived queries). No existing idempotency table — this is the one net-new concept, but its persistence shape is standard.

**Structure** (RESEARCH.md Code Examples, aligned to repo entity conventions): `@Getter @Setter @Entity @Table` with `@UniqueConstraint(columnNames = {"event_id", "consumer_name"})`, `@Id @GeneratedValue(strategy = GenerationType.UUID) UUID id`, `UUID eventId`, `String consumerName`, `Instant processedAt`. Per DDD boundaries (RESEARCH.md Open Q #2) prefer **one table per context** — an inventory copy and an order copy, each in its context's `infrastructure/entity` + `infrastructure/repository`. Repository exposes the `tryRecord`/insert used by both services (catch `DataIntegrityViolationException` → duplicate).

---

### `inventory_context/infrastructure/entity/InventoryStockBalanceEntity.java` (mod: add `reserved`) (model) — MODIFY

**Analog:** self (lines 46-53). Add a `reserved` column mirroring the existing `quantityOnHand` column declaration exactly:
```java
@Column(name = "quantity_on_hand", nullable = false, precision = 18, scale = 6)
private BigDecimal quantityOnHand = BigDecimal.ZERO;
```
→ add:
```java
@Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 6)
private BigDecimal reservedQuantity = BigDecimal.ZERO;
```
`available = quantityOnHand − reservedQuantity`. Also add the pessimistic-lock query to `InventoryStockBalanceRepository` (RESEARCH.md Pattern 4), mirroring the existing `@Query` in that repo (lines 18-22):
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select b from InventoryStockBalanceEntity b "
     + "where b.ingredient.id = :ingredientId and b.locationCode = :loc")
Optional<InventoryStockBalanceEntity> lockByIngredientAndLocation(UUID ingredientId, String loc);
```

---

### `order_context/domain/model/OrderStatus.java` (mod) (enum) — MODIFY

**Analog:** self (lines 1-5). Currently `SUBMITTED` only. Add `PENDING_CONFIRMATION`, `CONFIRMED`, `REJECTED` (D-01). Stored `EnumType.STRING` (`OrderEntity.java` line 35-37) so existing `SUBMITTED` rows are unaffected; read paths must tolerate `SUBMITTED`.

---

### `order_context/application/OrderSubmissionService.java` (mod) (service) — MODIFY

**Analog:** self. **One-line change** at line 58: `order.setStatus(OrderStatus.SUBMITTED)` → `order.setStatus(OrderStatus.PENDING_CONFIRMATION)` (D-01). The existing after-commit publish (lines 81, 225-237) already starts the saga — no change to publish wiring. Optionally default `OrderEntity.status` (entity line 37) to `PENDING_CONFIRMATION` too.

---

## Shared Patterns

### After-commit event publish
**Source:** `OrderSubmissionService.java` lines 225-237 (`publishAfterCommit` + `TransactionSynchronizationManager`)
**Apply to:** `InventoryReservationService` (publish result after reservation commit). Do NOT publish inside the transaction (RESEARCH.md anti-pattern).

### Kafka producer factory + KafkaTemplate bean pair
**Source:** `OrderKafkaProducerConfig.java` lines 18-32
**Apply to:** `InventoryKafkaProducerConfig` (result-event producer) and the DLT `KafkaTemplate` beans inside both consumer configs.

### Publisher adapter (port + `@Component` + `KafkaTemplate.send(topic, key, event)`)
**Source:** `KafkaOrderEventPublisher.java` lines 10-21 + port `OrderEventPublisher.java`
**Apply to:** `KafkaInventoryStockResultPublisher` (+ new `InventoryStockResultPublisher` port). Key every message by `orderId.toString()` for per-order ordering.

### `@Transactional` service with constructor-injected repositories
**Source:** `InventoryStockService.java` lines 32-45 / `OrderSubmissionService.java` lines 34-44
**Apply to:** `InventoryReservationService`, `OrderConfirmationService`.

### Non-negative stock invariant
**Source:** `InventoryStockService.java` lines 75-80 (`stockInsufficient()` guard) + `InventoryDomainException`
**Apply to:** reservation availability check (`available = on_hand − reserved ≥ required`).

### Recipe resolution + unit conversion
**Source:** `InventoryCostingService.java` lines 108-114 (`menuRecipeCostingPort.findRecipe`) + 177-182 (`UnitConverter.convert`); `MenuRecipeCostingPort.java`; `RecipeCostingSnapshot.java`; `RecipeTargetType { DISH, TOPPING_OPTION }`
**Apply to:** `InventoryReservationService.computeRequired(...)`. Reuse verbatim; do not re-query recipes.

### JPA entity conventions
**Source:** `InventoryStockMovementEntity.java` lines 26-77 + `InventoryStockBalanceEntity.java` lines 23-63
**Apply to:** `StockReservationEntity`, `ProcessedEventEntity` (Lombok `@Getter/@Setter`, UUID `@GeneratedValue`, `@UniqueConstraint`, `BigDecimal precision=18 scale=6`, `Instant` timestamps).

### Kafka topic/config properties
**Source:** `application.properties` lines 24-30 (`spring.kafka.bootstrap-servers`, `order.events.order-created-topic`, `payment.events.topic`, `table.events.topic`)
**Apply to:** new `inventory.events.order-stock-results-topic`, consumer `group-id`s, and DLT topics — same `${ENV:default}` idiom. Add `spring.kafka.listener.auto-startup=false` to `src/test/resources/application.properties` (RESEARCH.md Pitfall 1).

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `@KafkaListener` adapters (`OrderCreatedListener`, `OrderStockResultListener`) | adapter/listener | event-driven | System is produce-only today — **zero existing consumers**. Structure derived from the publisher-adapter conventions + RESEARCH.md Patterns 1-3. This is the only genuinely new wiring; all beans are first-party Spring Kafka. |
| `ProcessedEventEntity` (idempotency ledger) | model | CRUD | No idempotency ledger exists. Persistence shape follows the standard JPA entity + unique-constraint conventions (analog above); the concept itself is net-new. |

---

## Conventions

> Deterministic derivation tool `gsd-tools.cjs verify conventions --derive` **skipped** (`no-readable-files` under sandbox). Conventions below are observed from the 14 files read (majority-vote, high consistency).

| Axis | Dominant | Share | Entropy | Status |
|------|----------|-------|---------|--------|
| File-name casing | `PascalCase.java` (one public type per file, name = type) | 100% | low | named contract |
| Identifier casing | `camelCase` fields/methods, `UPPER_SNAKE` constants, `PascalCase` types | ~100% | low | named contract |
| Export/type style | Records for events/DTOs/snapshots; Lombok `@Getter/@Setter` entities; `@Service`/`@Component` + `@RequiredArgsConstructor` for beans (constructor injection) | ~100% | low | named contract |
| Import style | Fully-qualified, one-per-line, **no wildcard imports**, alphabetically ordered (`com.*`, `jakarta.*`, `java.*`, `lombok.*`, `org.*`) | 100% | low | named contract |

**Additional locked conventions (single-directory contracts):**
- **Package layout:** DDD `domain/{model,port,service,snapshot,repository}` + `application/{,dto,command,query,event}` + `infrastructure/{adapter,config,entity,repository,presentation,mapper}`. Consumer configs → `infrastructure/config`; listeners → `infrastructure/adapter`; services → `application`; events → `application/event`; ports → `domain/port`.
- **Entities:** `jakarta.persistence.*`, `@Id @GeneratedValue(strategy = GenerationType.UUID) UUID id`, `@Column(precision = 18, scale = 6)` for quantities, `@Enumerated(EnumType.STRING)` for enums, `@UniqueConstraint(name = "uq_...", columnNames = {...})`.
- **Repositories:** `interface X extends JpaRepository<Entity, UUID>` with Spring-Data derived queries (`findByIngredient_IdAnd...`) and `@Query` only when needed.
- **Kafka config:** `@Value("${spring.kafka.bootstrap-servers:localhost:9092}")` injected per factory bean; Jackson-2 `org.springframework.kafka.support.serializer.JsonSerializer/JsonDeserializer` (D-05 symmetry — not Jackson-3 `JacksonJson*`).
- **Testing:** JUnit 5 + AssertJ + Mockito plain `mock(...)` (no Spring context) for service unit tests; `when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0))` idiom (`InventoryStockServiceTest` lines 30-45); `@MockitoBean` for publishers in integration tests.

**Contested hotspots (author's choice):** None material in the touched Java subtree — the DDD/Java files are internally consistent per-directory (single dominant style across `order_context` and `inventory_context`). Note the plugin-repo prototype split for reference: **CJS↔SDK dual resolver** (`bin/lib/**` is CJS `module.exports`/`require`; `sdk/src/**` is ESM `export`/`import`) is the canonical intentional-contested split — each half internally consistent per-directory, contested only repo-wide. It does not apply to this Java project; here, match the directory's local style (which is uniform).

---

## Metadata

**Analog search scope:** `src/main/java/com/example/feat1/DDD/{order_context,inventory_context}`, `src/main/resources/application.properties`, `src/test/java/.../inventory_context`
**Files scanned:** ~20 (14 read in full)
**Pattern extraction date:** 2026-07-07
