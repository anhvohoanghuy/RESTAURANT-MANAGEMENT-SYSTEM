# Phase 16: Inventory reservation settlement - Pattern Map

**Mapped:** 2026-07-08
**Files analyzed:** 17 new/modified source files + 5 test files
**Analogs found:** 20 / 22

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `inventory_context/application/event/SettleTriggerEvent.java` (NEW) | model (event contract) | event-driven | `order_context/application/event/OrderStockResultEvent.java` | exact |
| `inventory_context/domain/model/InventoryMovementType.java` (MODIFY: add `CONSUMPTION`) | model (enum) | CRUD | itself (existing file) | exact |
| `inventory_context/infrastructure/entity/StockReservationEntity.java` (MODIFY: add `SETTLED`) | model (entity) | CRUD | itself (existing file) | exact |
| `inventory_context/infrastructure/entity/InventoryLineSettlementEntity.java` (NEW) | model (entity) | CRUD | `InventoryProcessedEventEntity.java` | role-match |
| `inventory_context/infrastructure/repository/InventoryLineSettlementRepository.java` (NEW) | repository | CRUD | `InventoryProcessedEventRepository.java` / `StockReservationRepository.java` | exact |
| `inventory_context/infrastructure/repository/StockReservationRepository.java` (MODIFY: add `lockByOrderId`) | repository | CRUD | `InventoryStockBalanceRepository.lockByIngredientAndLocation` (same file family) | exact |
| `inventory_context/domain/port/OrderLineLookupPort.java` (NEW) | service (port interface) | request-response | `MenuRecipeCostingPort.java` | exact |
| `inventory_context/domain/snapshot/OrderLineRecipeSnapshot.java` (NEW) | model (snapshot record) | request-response | `RecipeCostingSnapshot.java` / `MenuCostingDishSnapshot.java` | exact |
| `order_context/infrastructure/adapter/OrderLineLookupAdapter.java` (NEW) | service (cross-context adapter) | request-response | `MenuRecipeCostingAdapter.java` | exact |
| `order_context/infrastructure/repository/OrderLineRepository.java` (NEW) | repository | CRUD | `OrderCartLineRepository.java` (same package, closest sibling) | role-match |
| `inventory_context/domain/service/RecipeRequirementResolver.java` (NEW, extracted) | service (domain logic) | transform | `InventoryReservationService.accumulateRecipe`/`computeRequired` (lines 165-222, extracted from) | exact |
| `inventory_context/application/InventoryReservationService.java` (MODIFY: refactor to call extracted resolver) | service | CRUD/event-driven | itself (existing file) | exact |
| `inventory_context/application/InventoryReservationSettlementService.java` (NEW) | service | event-driven / CRUD | `InventoryReservationService.java` (whole-order reserve saga) | exact |
| `inventory_context/application/InventoryLedgerWriter.java` (NEW bean, WR-01 fix) | service (idempotency helper) | CRUD | `InventoryReservationService.onOrderCreated` ledger-insert block (lines 83-95) — the ANTI-pattern to invert | exact (as anti-pattern) |
| `inventory_context/infrastructure/adapter/SettleTriggerListener.java` (NEW) | controller (Kafka listener) | event-driven | `OrderCreatedListener.java` | exact |
| `inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java` (MODIFY: new beans) or new `SettleTriggerKafkaConsumerConfig.java` | config | event-driven | itself (existing file, same class) | exact |
| `inventory_context/infrastructure/config/InventoryKafkaTopicConfig.java` (MODIFY: new `NewTopic` beans) | config | event-driven | itself (existing file, same class) | exact |
| `src/test/.../InventoryReservationSettlementServiceTest.java` (NEW) | test | — | `InventoryReservationServiceTest.java` | exact |
| `src/test/.../InventoryLedgerWriterTest.java` (NEW) | test | — | `InventoryReservationServiceTest.java` (idempotency-block assertions) | role-match |
| `src/test/.../SettleTriggerKafkaConsumerConfigTest.java` (NEW) | test | — | `InventoryKafkaConsumerConfigTest.java` | exact |
| `src/test/.../OrderLineLookupAdapterTest.java` (NEW) | test | — | none — first adapter test of its kind (see No Analog Found) | no-analog |
| `src/test/.../EventSerdeRoundTripTest.java` (MODIFY: add `SettleTriggerEvent` case) | test | — | itself (existing file) | exact |

## Pattern Assignments

### `inventory_context/application/event/SettleTriggerEvent.java` (model, event-driven)

**Analog:** `src/main/java/com/example/feat1/DDD/order_context/application/event/OrderStockResultEvent.java` (whole file, 26 lines)

**Full analog** (this is the entire small file — copy this record shape verbatim):
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

**New file shape** (per D-01 fields + codebase convention of an `Instant occurredAt` and a `TYPE`/`*_TYPE` constant, mirrored from `OrderCreatedEvent.TYPE`):
```java
package com.example.feat1.DDD.inventory_context.application.event;

import java.time.Instant;
import java.util.UUID;

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
Do NOT add ingredient amounts to this record — D-01 and Pitfall 6 in RESEARCH.md explicitly forbid it (inventory re-resolves the recipe itself).

---

### `inventory_context/domain/model/InventoryMovementType.java` (model, CRUD) — MODIFY

**Analog:** itself, current state (34 lines, full file read above)

**Current enum + classifiers** (lines 7-33):
```java
public enum InventoryMovementType {
  RECEIPT,
  ADJUSTMENT_IN,
  ADJUSTMENT_OUT,
  WASTE,
  STOCK_COUNT;

  public boolean isInbound() {
    return this == RECEIPT || this == ADJUSTMENT_IN;
  }

  public boolean isOutbound() {
    return this == ADJUSTMENT_OUT || this == WASTE;
  }

  public boolean isCount() {
    return this == STOCK_COUNT;
  }
}
```
**Change:** add `CONSUMPTION` to the enum list and to `isOutbound()`:
```java
public enum InventoryMovementType {
  RECEIPT,
  ADJUSTMENT_IN,
  ADJUSTMENT_OUT,
  WASTE,
  STOCK_COUNT,
  CONSUMPTION;

  public boolean isInbound() { return this == RECEIPT || this == ADJUSTMENT_IN; }
  public boolean isOutbound() {
    return this == ADJUSTMENT_OUT || this == WASTE || this == CONSUMPTION;
  }
  public boolean isCount() { return this == STOCK_COUNT; }
}
```
Note (RESEARCH "Code Examples"): the settlement service never calls `isOutbound()`/`isInbound()` itself (it writes `InventoryStockMovementEntity` rows directly, not via `InventoryStockService.recordMovement`) — this classification is kept correct only for future reporting code that filters by these predicates.

---

### `inventory_context/infrastructure/entity/StockReservationEntity.java` (model, CRUD) — MODIFY

**Analog:** itself, current state (full file, 107 lines, read above)

**Current status enum** (lines 44-46):
```java
public enum ReservationStatus {
  HELD
}
```
**Change:** `HELD, SETTLED`. No other structural change to this entity — do NOT attempt to derive "last line settled" from `.lines` (see Anti-Patterns below); `lines` holds whole-order per-ingredient totals, not a per-line manifest.

**Table/entity conventions to preserve** (lines 32-41, 67-86): `@Getter @Setter @Entity`, `@Table` with a named `@UniqueConstraint`, `@Id @GeneratedValue(strategy = GenerationType.UUID)`, `@Enumerated(EnumType.STRING)` for the status column, and a static factory method (`held(...)`) rather than a public no-arg constructor + setters at the call site — mirror this factory-method convention if `InventoryLineSettlementEntity` needs one (optional, since it's a flat row with no nested collection).

---

### `inventory_context/infrastructure/entity/InventoryLineSettlementEntity.java` (NEW model, CRUD)

**Analog:** `InventoryProcessedEventEntity.java` (whole file, 46 lines, read above) — closest existing "guard/ledger row" shape.

**Full analog to copy the shape from**:
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
**New entity shape** (RESEARCH "Code Examples" section, verified consistent with the analog's conventions):
```java
@Entity
@Table(name = "inventory_line_settlements",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_inventory_line_settlement", columnNames = {"order_id", "order_line_id"}))
public class InventoryLineSettlementEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "order_id", nullable = false)
  private UUID orderId;
  @Column(name = "order_line_id", nullable = false)
  private UUID orderLineId;
  @Column(name = "settled_at", nullable = false)
  private Instant settledAt;
  // @Getter @Setter via Lombok, same convention as every entity in this package
}
```
This single table serves BOTH the D-05 per-line idempotency guard (`existsByOrderIdAndOrderLineId`) AND the D-04 last-line counter (`countByOrderId`) — do not split into two tables/mechanisms (RESEARCH Pitfall 4).

---

### `inventory_context/infrastructure/repository/InventoryLineSettlementRepository.java` (NEW repository, CRUD)

**Analog:** `InventoryProcessedEventRepository.java` (query-method style) and `StockReservationRepository.java` (whole file, read above):
```java
public interface StockReservationRepository extends JpaRepository<StockReservationEntity, UUID> {
  boolean existsByOrderId(UUID orderId);
  Optional<StockReservationEntity> findByOrderId(UUID orderId);
}
```
**New repository shape:**
```java
public interface InventoryLineSettlementRepository
    extends JpaRepository<InventoryLineSettlementEntity, UUID> {
  boolean existsByOrderIdAndOrderLineId(UUID orderId, UUID orderLineId);
  long countByOrderId(UUID orderId);
}
```

---

### `inventory_context/infrastructure/repository/StockReservationRepository.java` (MODIFY, add `lockByOrderId`)

**Analog:** `InventoryStockBalanceRepository.lockByIngredientAndLocation` (lines 18-26, whole file read above):
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
**New method to add to `StockReservationRepository`** (imports needed: `jakarta.persistence.LockModeType`, `org.springframework.data.jpa.repository.Lock`, `org.springframework.data.jpa.repository.Query`):
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select r from StockReservationEntity r where r.orderId = :orderId")
Optional<StockReservationEntity> lockByOrderId(UUID orderId);
```
Lock this row FIRST, before the sorted ingredient-balance locks, per Pattern 3 in RESEARCH.md (deadlock-avoidance total lock order).

---

### `inventory_context/domain/port/OrderLineLookupPort.java` + `domain/snapshot/OrderLineRecipeSnapshot.java` (NEW port/snapshot, request-response)

**Analog:** `domain/port/MenuRecipeCostingPort.java` (whole file, 14 lines, read above):
```java
package com.example.feat1.DDD.inventory_context.domain.port;

import com.example.feat1.DDD.inventory_context.domain.snapshot.MenuCostingDishSnapshot;
import com.example.feat1.DDD.inventory_context.domain.snapshot.RecipeCostingSnapshot;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuRecipeCostingPort {
  Optional<RecipeCostingSnapshot> findRecipe(RecipeTargetType targetType, UUID targetId);
  List<MenuCostingDishSnapshot> listActiveDishes();
}
```
**New port shape** (mirrors the "narrow interface, narrow snapshot record, `Optional` for not-found" convention):
```java
package com.example.feat1.DDD.inventory_context.domain.port;

public interface OrderLineLookupPort {
  Optional<OrderLineRecipeSnapshot> findLine(UUID orderId, UUID orderLineId);
}

// domain/snapshot/OrderLineRecipeSnapshot.java
public record OrderLineRecipeSnapshot(
    UUID orderLineId, UUID dishId, int quantity, List<UUID> selectedToppingOptionIds) {}
```
**Convention established here:** the CONSUMING context (`inventory_context`) defines the port + snapshot in its own `domain/port`/`domain/snapshot` packages; the OWNING context (`order_context`) implements the adapter. This is the exact same split `MenuRecipeCostingPort` (defined in `inventory_context`) / `MenuRecipeCostingAdapter` (implemented in `menu_context`) already uses.

---

### `order_context/infrastructure/adapter/OrderLineLookupAdapter.java` (NEW adapter, request-response)

**Analog:** `menu_context/infrastructure/adapter/MenuRecipeCostingAdapter.java` (whole file, 61 lines, read above):
```java
@Component
@RequiredArgsConstructor
public class MenuRecipeCostingAdapter implements MenuRecipeCostingPort {
  private final RecipeDomainRepository recipeRepository;
  private final DishRepository dishRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<RecipeCostingSnapshot> findRecipe(RecipeTargetType targetType, UUID targetId) {
    return recipeRepository
        .findByTarget(targetType, targetId)
        .map(recipe -> new RecipeCostingSnapshot(
            recipe.getTargetType(), recipe.getTargetId(), recipe.getName(),
            recipe.getLines().stream().map(this::toLine).toList()));
  }
  // ...
}
```
**New adapter shape:**
```java
package com.example.feat1.DDD.order_context.infrastructure.adapter;

@Component
@RequiredArgsConstructor
public class OrderLineLookupAdapter implements OrderLineLookupPort {
  private final OrderLineRepository orderLineRepository; // NEW repository

  @Override
  @Transactional(readOnly = true)
  public Optional<OrderLineRecipeSnapshot> findLine(UUID orderId, UUID orderLineId) {
    return orderLineRepository.findByOrder_IdAndId(orderId, orderLineId)
        .map(line -> new OrderLineRecipeSnapshot(
            line.getId(),
            line.getDishId(),
            line.getQuantity(),
            line.getSelectedToppings().stream()
                .map(OrderLineToppingSnapshot::getToppingOptionId)
                .toList()));
  }
}
```
Passing BOTH `orderId` and `orderLineId` to the finder (not just `orderLineId`) is a deliberate defense-in-depth choice — closes a theoretical cross-order UUID-collision leak (RESEARCH Pattern 2 note).

**Underlying entity fields to read** — `OrderLineEntity.java` (lines 26-63, read above): `id`, `dishId` (line 36), `quantity` (line 59), `selectedToppings` (`List<OrderLineToppingSnapshot>`, `@ElementCollection(fetch = EAGER)`, line 47-50). `OrderLineToppingSnapshot.java` (whole file, 29 lines) has `toppingOptionId` (line 20-21) as the field to extract.

---

### `order_context/infrastructure/repository/OrderLineRepository.java` (NEW repository, CRUD)

**Analog:** `order_context/infrastructure/repository/OrderCartLineRepository.java` (closest sibling in the same package — read its query-method style) — no `OrderLineRepository` exists today; `OrderLineEntity` is currently reachable only through `OrderEntity.lines` (confirmed by RESEARCH). New file:
```java
package com.example.feat1.DDD.order_context.infrastructure.repository;

public interface OrderLineRepository extends JpaRepository<OrderLineEntity, UUID> {
  Optional<OrderLineEntity> findByOrder_IdAndId(UUID orderId, UUID id);
}
```

---

### `inventory_context/domain/service/RecipeRequirementResolver.java` (NEW, extracted domain service, transform)

**Analog / extraction source:** `InventoryReservationService.java` lines 165-222 (`computeRequired` + `accumulateRecipe`, read above in full):
```java
private Map<UUID, BigDecimal> computeRequired(OrderCreatedEvent event) {
  Map<UUID, BigDecimal> required = new LinkedHashMap<>();
  if (event.lines() == null) { return required; }
  for (OrderCreatedEvent.OrderLine line : event.lines()) {
    int quantity = line.quantity();
    accumulateRecipe(required, RecipeTargetType.DISH, line.dishId(), quantity);
    if (line.selectedToppings() != null) {
      for (OrderCreatedEvent.OrderTopping topping : line.selectedToppings()) {
        accumulateRecipe(required, RecipeTargetType.TOPPING_OPTION, topping.toppingOptionId(), quantity);
      }
    }
  }
  return required;
}

private void accumulateRecipe(
    Map<UUID, BigDecimal> required, RecipeTargetType targetType, UUID targetId, int orderLineQuantity) {
  if (targetId == null) { return; }
  Optional<RecipeCostingSnapshot> recipe = menuRecipeCostingPort.findRecipe(targetType, targetId);
  if (recipe.isEmpty()) {
    log.debug("No recipe for {} {} — contributes zero required stock", targetType, targetId);
    return;
  }
  for (RecipeCostingSnapshot.Line line : recipe.get().lines()) {
    UUID ingredientId = line.ingredientId();
    if (ingredientId == null) { continue; }
    Optional<IngredientEntity> ingredient = ingredientRepository.findById(ingredientId);
    if (ingredient.isEmpty()) {
      log.debug("Ingredient {} not found — contributes zero required stock", ingredientId);
      continue;
    }
    String baseUnit = ingredient.get().getBaseUnit();
    BigDecimal converted;
    try {
      converted = UnitConverter.convert(line.quantity(), line.unit(), baseUnit);
    } catch (InventoryDomainException unconvertible) {
      log.debug("Cannot convert {} {} to base unit {} for ingredient {} — contributes zero",
          line.quantity(), line.unit(), baseUnit, ingredientId);
      continue;
    }
    BigDecimal contribution = converted.multiply(BigDecimal.valueOf(orderLineQuantity));
    required.merge(ingredientId, contribution, BigDecimal::add);
  }
}
```
**Extraction plan:** move `accumulateRecipe`'s body verbatim into a new `RecipeRequirementResolver` domain-service class (constructor-injected `MenuRecipeCostingPort` + `IngredientRepository`, same `@RequiredArgsConstructor` convention), exposing something like `void accumulate(Map<UUID,BigDecimal> required, RecipeTargetType targetType, UUID targetId, int qty)`. `InventoryReservationService.computeRequired` (whole-order, iterates `event.lines()`) and the new `InventoryReservationSettlementService` (single-line) both call this one shared method — do not copy-paste the body into the new service. This is a mechanical extract-method refactor; do not touch surrounding WR-01/WR-02-flagged code in the same file beyond this extraction (RESEARCH Pattern 1 caveat).

---

### `inventory_context/application/InventoryReservationSettlementService.java` (NEW service, event-driven/CRUD)

**Analog:** `InventoryReservationService.java` (whole file, 249 lines, read above) — the whole-order reserve saga this settlement saga inverts.

**Imports pattern to mirror** (lines 1-37):
```java
import com.example.feat1.DDD.inventory_context.domain.model.InventoryDomainException;
import com.example.feat1.DDD.inventory_context.domain.port.MenuRecipeCostingPort;
import com.example.feat1.DDD.inventory_context.domain.service.UnitConverter;
import com.example.feat1.DDD.inventory_context.domain.snapshot.RecipeCostingSnapshot;
import com.example.feat1.DDD.inventory_context.infrastructure.entity.*;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
```

**Sorted-ascending pessimistic-lock loop to invert** (lines 100-133, subtract instead of add — RESEARCH Pattern 3/4):
```java
List<UUID> sortedIngredientIds = required.keySet().stream().sorted().toList();
Map<UUID, InventoryStockBalanceEntity> lockedBalances = new LinkedHashMap<>();
for (UUID ingredientId : sortedIngredientIds) {
  BigDecimal need = required.get(ingredientId);
  Optional<InventoryStockBalanceEntity> balance =
      balanceRepository.lockByIngredientAndLocation(ingredientId, DEFAULT_LOCATION);
  // ... (settlement: subtract from BOTH reservedQuantity and quantityOnHand, clamp >= 0, see below)
}
```

**Subtract-and-clamp inversion** (new logic, RESEARCH Pattern 4, inverted from the add at lines 126-133):
```java
BigDecimal need = required.get(ingredientId);
BigDecimal newReserved = entity.getReservedQuantity().subtract(need);
BigDecimal newOnHand = entity.getQuantityOnHand().subtract(need);
if (newReserved.compareTo(BigDecimal.ZERO) < 0) {
  log.warn("Reserved quantity would go negative for ingredient {} (order {}); clamping to 0",
      ingredientId, orderId);
  newReserved = BigDecimal.ZERO;
}
if (newOnHand.compareTo(BigDecimal.ZERO) < 0) {
  log.warn("On-hand would go negative for ingredient {} (order {}); clamping to 0 (D-03)",
      ingredientId, orderId);
  newOnHand = BigDecimal.ZERO;
}
entity.setReservedQuantity(scale(newReserved));
entity.setQuantityOnHand(scale(newOnHand));
entity.setLastMovementAt(Instant.now());
entity.setUpdatedAt(Instant.now());
// no explicit balanceRepository.save() needed — entity is managed (dirty checking), same as the
// existing reserve loop at InventoryReservationService.java:132.
```
Apply the clamp PER INGREDIENT inline in the loop — never throw/abort on insufficient stock (RESEARCH Pitfall 5; this is the opposite of `InventoryStockService.recordMovement`'s throw behavior).

**Reservation lock + last-line flip** (new logic, RESEARCH Pattern 3):
```java
StockReservationEntity reservation = reservationRepository
    .lockByOrderId(orderId)
    .orElseThrow(() -> missingReservation(orderId)); // uncaught -> retried, then DLT (D-05)
if (reservation.getStatus() != StockReservationEntity.ReservationStatus.HELD) {
  return; // already SETTLED — benign duplicate/redelivery signal, not an error (Open Q1)
}
// ... deduct balances, write movement, insert InventoryLineSettlementEntity ...
long settledCount = lineSettlementRepository.countByOrderId(orderId);
if (settledCount >= event.totalLines()) {
  reservation.setStatus(StockReservationEntity.ReservationStatus.SETTLED);
}
```
Lock the reservation row FIRST, then acquire ingredient balance locks in sorted order — same total lock order across all concurrent settlement transactions (deadlock avoidance).

**CONSUMPTION movement — write directly, do NOT call `recordMovement`:**
See the Anti-Pattern note under Shared Patterns below; build `InventoryStockMovementEntity` per ingredient directly using the field-setting style at `InventoryStockService.java` lines 99-113 as the shape template, but with `movementType = InventoryMovementType.CONSUMPTION`, `referenceType = "ORDER_LINE"` (or similar), `referenceId = orderLineId`.

---

### `inventory_context/application/InventoryLedgerWriter.java` (NEW, WR-01-safe idempotency insert)

**Anti-pattern analog (DO NOT COPY):** `InventoryReservationService.onOrderCreated` lines 83-95 — the pre-fix idiom:
```java
try {
  InventoryProcessedEventEntity ledger = new InventoryProcessedEventEntity();
  ledger.setEventId(eventId);
  ledger.setConsumerName(CONSUMER_NAME);
  ledger.setProcessedAt(Instant.now());
  processedEventRepository.saveAndFlush(ledger);
} catch (DataIntegrityViolationException duplicate) {
  log.debug("Concurrent duplicate OrderCreated eventId={} orderId={} — skipping", eventId, orderId);
  return;
}
```
This leaves the OUTER `@Transactional` business method rollback-only on a genuine concurrent duplicate (WR-01 finding in `15-REVIEW.md`).

**Required fix shape** (RESEARCH Pattern 5, `15-REVIEW.md` WR-01 prescription):
```java
@Service
@RequiredArgsConstructor
public class InventoryReservationSettlementService {
  private final InventoryLedgerWriter ledgerWriter; // separate bean

  @Transactional
  public void onSettleTrigger(SettleTriggerEvent event) {
    if (alreadyProcessed(event)) { return; } // cheap pre-check, no lock
    if (!ledgerWriter.tryInsert(event.eventId(), CONSUMER_NAME)) {
      return; // concurrent duplicate — isolated tx rolled back cleanly, no poison
    }
    // ... business logic in THIS (outer) transaction, unaffected by the ledger tx's own outcome
  }
}

@Component
public class InventoryLedgerWriter {
  private final InventoryProcessedEventRepository repo;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean tryInsert(UUID eventId, String consumerName) {
    try {
      InventoryProcessedEventEntity ledger = new InventoryProcessedEventEntity();
      ledger.setEventId(eventId);
      ledger.setConsumerName(consumerName);
      ledger.setProcessedAt(Instant.now());
      repo.saveAndFlush(ledger);
      return true;
    } catch (DataIntegrityViolationException duplicate) {
      return false; // this REQUIRES_NEW transaction rolls back cleanly and independently
    }
  }
}
```
The idempotency ledger row shape itself is unchanged — reuse `InventoryProcessedEventEntity`/`InventoryProcessedEventRepository` as-is; only the insertion call site changes to isolate it in its own `REQUIRES_NEW` bean method.

---

### `inventory_context/infrastructure/adapter/SettleTriggerListener.java` (NEW controller, event-driven)

**Analog:** `OrderCreatedListener.java` (whole file, 27 lines, read above):
```java
package com.example.feat1.DDD.inventory_context.infrastructure.adapter;

import com.example.feat1.DDD.inventory_context.application.InventoryReservationService;
import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
**New listener shape** (thin delegate, zero business logic — RESEARCH "anti-pattern" note baked into the analog's own javadoc):
```java
@Component
@RequiredArgsConstructor
public class SettleTriggerListener {
  private final InventoryReservationSettlementService settlementService;

  @KafkaListener(
      topics = "${kitchen.events.settle-trigger-topic:kitchen.settlement-trigger}",
      groupId = "${inventory.settlement.consumer.group-id:inventory-settlement}",
      containerFactory = "settleTriggerKafkaListenerContainerFactory")
  public void onSettleTrigger(SettleTriggerEvent event) {
    settlementService.onSettleTrigger(event);
  }
}
```
Topic/group-id names are Claude's Discretion (CONTEXT.md); RESEARCH Open Question 2 recommends `kitchen.events.settle-trigger-topic` / `kitchen.settlement-trigger` and `inventory.settlement.consumer.group-id` / `inventory-settlement`, mirroring the exact property-key convention `inventory.order-created.consumer.group-id` already uses.

---

### `inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java` (MODIFY: new beans) — config

**Analog:** itself, current state (whole file, 112 lines, read above). Copy the SAME structure for a second, distinct `ConsumerFactory<String, SettleTriggerEvent>` + `DefaultErrorHandler` + `ConcurrentKafkaListenerContainerFactory` triplet (new bean names, e.g. `settleTriggerConsumerFactory`, `settleTriggerErrorHandler`, `settleTriggerKafkaListenerContainerFactory`) — do not reuse the `OrderCreatedEvent`-typed beans for a different event type.

**Consumer factory pattern to mirror** (lines 47-68):
```java
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
```
For `SettleTriggerEvent`, set `TRUSTED_PACKAGES` to `com.example.feat1.DDD.inventory_context.application.event` (or the trusted-packages wildcard `com.example.feat1.*` per D-01) and `VALUE_DEFAULT_TYPE` to `SettleTriggerEvent.class.getName()`.

**Error handler + DLT template** (lines 86-96):
```java
@Bean
public DefaultErrorHandler orderCreatedErrorHandler(
    @Qualifier("inventoryDltKafkaTemplate") KafkaTemplate<String, Object> dlt) {
  DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dlt);
  DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
  handler.addNotRetryableExceptions(DeserializationException.class);
  return handler;
}
```
Per RESEARCH Anti-Pattern note: do NOT add "missing HELD reservation" to `addNotRetryableExceptions` — only `DeserializationException` (poison-pill) should be non-retryable; a missing reservation should retry (3x FixedBackOff) then land on the DLT naturally.

The existing `inventoryDltKafkaTemplate` bean (lines 76-84) can likely be REUSED as-is (it's already a generic `KafkaTemplate<String, Object>` with the Jackson-3 serializer) — no new DLT producer template bean is needed unless a distinct bean name is preferred for clarity.

---

### `inventory_context/infrastructure/config/InventoryKafkaTopicConfig.java` (MODIFY: new `NewTopic` beans) — config

**Analog:** itself, current state (whole file, 51 lines, read above):
```java
@Configuration
public class InventoryKafkaTopicConfig {
  private static final String DLT_SUFFIX = ".DLT";
  private final String orderStockResultsTopic;
  private final String orderCreatedTopic;

  public InventoryKafkaTopicConfig(
      @Value("${inventory.events.order-stock-results-topic:inventory.order-stock-results}") String orderStockResultsTopic,
      @Value("${order.events.order-created-topic:orders.created}") String orderCreatedTopic) {
    this.orderStockResultsTopic = orderStockResultsTopic;
    this.orderCreatedTopic = orderCreatedTopic;
  }

  @Bean
  public NewTopic orderStockResultsTopic() {
    return TopicBuilder.name(orderStockResultsTopic).partitions(1).replicas(1).build();
  }

  @Bean
  public NewTopic orderCreatedDltTopic() {
    return TopicBuilder.name(orderCreatedTopic + DLT_SUFFIX).partitions(1).replicas(1).build();
  }
}
```
**Add**: a constructor-injected `settleTriggerTopic` field (`@Value("${kitchen.events.settle-trigger-topic:kitchen.settlement-trigger}")`) and two new `@Bean NewTopic` methods — one for the live topic, one for `<topic> + DLT_SUFFIX` — following the exact same `TopicBuilder.name(...).partitions(1).replicas(1).build()` pattern. The DLT name MUST derive from the same `@Value`-injected property as the live topic (never hardcode a divergent literal), per the existing file's own doc comment.

---

## Shared Patterns

### Kafka wiring (typed ConsumerFactory + ErrorHandlingDeserializer + DLT)
**Source:** `inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java` (full file, lines 1-112)
**Apply to:** `SettleTriggerListener`, the new consumer-factory/error-handler/container-factory beans, `SettleTriggerKafkaConsumerConfigTest`
Mirror exactly: `ErrorHandlingDeserializer` wraps `JacksonJsonDeserializer`; `USE_TYPE_INFO_HEADERS=false` + fixed `VALUE_DEFAULT_TYPE` + `TRUSTED_PACKAGES` allow-list; `AckMode.RECORD`; `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`; `FixedBackOff(1000L, 3L)`; only `DeserializationException` is non-retryable.

### Idempotency ledger + WR-01 isolation
**Source:** `InventoryProcessedEventEntity.java` (entity shape) + RESEARCH Pattern 5 (fix for the anti-pattern at `InventoryReservationService.java:83-95`)
**Apply to:** `InventoryReservationSettlementService`, new `InventoryLedgerWriter` bean
The ledger insert MUST live in a dedicated `@Component` bean method annotated `@Transactional(propagation = Propagation.REQUIRES_NEW)` so a concurrent-duplicate `DataIntegrityViolationException` never poisons the outer business transaction. Combine with a per-`(orderId, orderLineId)` existence check (`InventoryLineSettlementRepository.existsByOrderIdAndOrderLineId`) as a SECOND, independent guard — the two are not redundant (RESEARCH Pitfall 4).

### Sorted-ascending-ingredient-id pessimistic lock (deadlock avoidance)
**Source:** `InventoryReservationService.java` lines 100-133 (comment at line 100-101: "Canonical iteration order (ascending UUID) used for BOTH the availability check and the reserve loop so concurrent reservers acquire row locks in the same sequence")
**Apply to:** `InventoryReservationSettlementService` — same discipline, subtract instead of add; additionally lock the single `StockReservationEntity` row (via new `lockByOrderId`) BEFORE the ingredient-balance locks (RESEARCH Pattern 3) to establish one total lock order across concurrent line settlements of the same order.

### Non-negative clamp (never throw for insufficient stock in settlement)
**Source:** new logic (no existing clamp helper) — explicitly contrasted against `InventoryStockService.recordMovement` lines 76-78 which THROWS `InventoryDomainException.stockInsufficient()`:
```java
if (current.compareTo(baseQuantity) < 0) {
  throw InventoryDomainException.stockInsufficient();
}
```
**Apply to:** `InventoryReservationSettlementService`'s per-ingredient subtract loop — clamp `on_hand` (mandatory, D-03) and `reserved_quantity` (discretionary but recommended, RESEARCH A3) to `BigDecimal.ZERO` inline, log at WARN, and NEVER throw/abort the transaction for insufficient stock (RESEARCH Pitfall 5).

### CONSUMPTION movement — write directly, don't call `recordMovement`
**Source:** `InventoryStockService.recordMovement` (lines 44-116, full method read above) — the field-setting SHAPE to mirror for `InventoryStockMovementEntity` construction (lines 99-113):
```java
InventoryStockMovementEntity movement = new InventoryStockMovementEntity();
movement.setIngredient(ingredient);
movement.setLocationCode(DEFAULT_LOCATION);
movement.setMovementType(type);
movement.setQuantity(scale(quantity));
movement.setUnit(...);
movement.setBaseQuantityDelta(delta);
movement.setBaseUnit(baseUnit);
movement.setResultingBalance(resulting);
movement.setReferenceType(...);
movement.setReferenceId(...);
movement.setActorId(actorId);
movement.setCreatedAt(now);
movementRepository.save(movement);
```
**Apply to:** `InventoryReservationSettlementService` — build one `InventoryStockMovementEntity` per ingredient directly (no `actorId`, since this is system-triggered not staff-triggered; `referenceType`/`referenceId` should point at the order/order-line), `movementType = CONSUMPTION`. Do NOT call `recordMovement()` itself — it acquires its own unlocked `findByIngredient_IdAndLocationCode` lookup and throws on shortage, both incompatible with the sorted-lock-then-clamp semantics this phase requires (RESEARCH "Anti-Patterns to Avoid").

### Cross-context read port convention (consumer defines port, owner implements adapter)
**Source:** `domain/port/MenuRecipeCostingPort.java` + `menu_context/infrastructure/adapter/MenuRecipeCostingAdapter.java` (both full files read above)
**Apply to:** `OrderLineLookupPort` (defined in `inventory_context/domain/port`) + `OrderLineLookupAdapter` (implemented in `order_context/infrastructure/adapter`) — narrow snapshot record, `Optional` return, `@Transactional(readOnly = true)` on the adapter method.

### Domain exception style (if a "missing HELD reservation" exception type is needed)
**Source:** `InventoryDomainException.java` (whole file, 67 lines, read above) — static-factory pattern with a `String` error code constant + `HttpStatus`:
```java
public static InventoryDomainException stockNotFound() {
  return new InventoryDomainException(
      STOCK_NOT_FOUND, "Stock balance was not found", HttpStatus.NOT_FOUND);
}
```
**Apply to:** if the settlement service needs a distinct "missing reservation" exception (rather than reusing a generic `NoSuchElementException` from `orElseThrow`), add a new static factory (e.g. `InventoryDomainException.reservationNotFound()`) following this exact code/message/status convention. Since this exception is caught by the Kafka error handler (routes to retry then DLT), not by an HTTP layer, the `HttpStatus` field is vestigial here but keep it for consistency with `AppException`'s constructor signature.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `src/test/.../order_context/infrastructure/adapter/OrderLineLookupAdapterTest.java` | test | — | `MenuRecipeCostingAdapter` (the closest analog adapter) has no dedicated unit test today — this may be the first thin-JPA-backed-adapter test in the repo. Use `InventoryReservationServiceTest`'s Mockito-mock style as the structural template instead (mock the repository, assert the snapshot mapping), or verify via an existing `OrderSubmissionIntegrationTest`-style integration test if a unit test is judged low value for a pure pass-through adapter. |
| `InventoryReservationSettlementService`'s "concurrent out-of-order last-line settlement" test scenario | test scenario | — | No existing test in this codebase exercises true concurrent pessimistic-lock races (all existing tests mock the repositories, so lock semantics aren't exercised end-to-end); RESEARCH recommends simulating via two sequential calls with mocked lock semantics rather than a true-concurrency test, deferring a true concurrency integration test to validation budget. |

## Conventions

Convention derivation (`node bin/gsd-tools.cjs verify conventions --derive --scope <dir>`) was attempted against `src/main/java/com/example/feat1/DDD/inventory_context` (relative scope, as required by the tool's path sanitizer) and returned:
```json
{ "mode": "derive", "skipped": true, "reason": "no-readable-files", "axes": [] }
```
Convention derivation skipped (the shared `bin/lib/conventions.cjs` corpus walker only recognizes `.js`/`.jsx`/`.ts`/`.tsx`/`.cjs`/`.mjs` files — this entire phase's file set is `.java`, which the tool's `SRC_RE` filter does not match, so no corpus could be collected). The 4-axis table below is therefore derived manually by direct reading (not the deterministic tool), from the same files read above:

| Axis | Dominant | Share | Entropy | Status |
|---|---|---|---|---|
| File-name casing | PascalCase (e.g. `InventoryReservationService.java`, `StockReservationEntity.java`) | ~100% (all `.java` files under `inventory_context`/`order_context` observed) | none | named contract |
| Identifier casing | camelCase (fields/methods), PascalCase (classes/records/enums), CONSTANT_CASE (`static final` constants like `CONSUMER_NAME`, `QUANTITY_SCALE`) | ~100% | none | named contract |
| Export style | One `public class`/`interface`/`enum`/`record` per file, package-private helper types nested as static inner classes (e.g. `ReservationLine` inside `StockReservationEntity`) | ~100% | none | named contract |
| Import style | Explicit fully-qualified single-class imports, no wildcard imports, static imports only in test files (`org.assertj.core.api.Assertions.assertThat`, `org.mockito.Mockito.*`) | ~100% (every file read in this session) | none | named contract |

**Contested hotspots (author's choice):** This codebase does not exhibit the CJS↔ESM dual-resolver split (`bin/lib/**` CJS vs `sdk/src/**` ESM) that the GSD plugin's own repo uses as its prototype intentional-contested-split example — that split is specific to the plugin's tooling repo, not this Java/Spring project. No axis in this Java codebase was found contested below the 70% dominance threshold; all four axes observed above are uniform (100% share, zero entropy) across every file read in this session. If a future convention-drift check surfaces a genuinely contested Java-side axis (e.g., a mix of constructor-injection styles or Lombok annotation ordering), treat it the same way the plugin repo treats its CJS/ESM split: match the local directory's existing style rather than forcing a repo-wide unification, since the two `inventory_context`/`order_context` DDD contexts are already internally consistent independently.

## Metadata

**Analog search scope:** `src/main/java/com/example/feat1/DDD/inventory_context/**`, `src/main/java/com/example/feat1/DDD/order_context/**`, `src/main/java/com/example/feat1/DDD/menu_context/infrastructure/adapter/**`, `src/test/java/com/example/feat1/DDD/inventory_context/**`, `src/test/java/com/example/feat1/DDD/order_context/**`
**Files scanned:** 22 read directly (full or targeted) + repo-wide `find`/`grep` listing of `inventory_context` and `order_context` directory trees
**Pattern extraction date:** 2026-07-08
