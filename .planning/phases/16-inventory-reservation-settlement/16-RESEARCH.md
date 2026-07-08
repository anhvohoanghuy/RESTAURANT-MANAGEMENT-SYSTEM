# Phase 16: Inventory reservation settlement - Research

**Researched:** 2026-07-08
**Domain:** Kafka event-driven inventory settlement (Spring Boot 4 / Jackson 3 / spring-kafka), pure `inventory_context` consumer, JPA pessimistic locking, cross-context read port
**Confidence:** HIGH (stack/config: verified against existing Phase 15 code in this repo; architecture: HIGH — derived directly from reading the actual codebase, not external docs)

## Summary

Phase 16 is a narrow, mechanical extension of the exact machinery Phase 15 already built and shipped: a new Kafka consumer in `inventory_context` that reacts to a settle-trigger event `(eventId, orderId, orderLineId, totalLines)`, re-resolves ONE order line's recipe (dish + selected toppings → ingredients via `UnitConverter`), decrements `quantity_on_hand` and `reserved_quantity` under the same canonical sorted-ascending-ingredient-id pessimistic lock loop `InventoryReservationService` already uses (subtract instead of add), records a `CONSUMPTION` audit movement, and flips `StockReservationEntity` to a new `SETTLED` status when the last line of the order settles. All of the Kafka wiring (typed `ConsumerFactory` + `ErrorHandlingDeserializer` → `JacksonJsonDeserializer`, `AckMode.RECORD`, `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`, `NewTopic` beans), the idempotency-ledger pattern (`InventoryProcessedEventEntity`), and the JPA pessimistic-lock discipline are proven, in-repo, reusable patterns — no new dependencies, no new architectural style.

Three things are genuinely new and need careful design, because the existing code does not support them today: (1) **there is no repository/port to read a single `OrderLineEntity` by id** — `OrderLineEntity` is currently only reachable via `OrderEntity.lines` (lazy, cascade-all), so a new cross-context read port must be added, mirroring the existing `MenuRecipeCostingPort`/`MenuRecipeCostingAdapter` pattern exactly; (2) **`StockReservationEntity` is one row per ORDER with per-ingredient totals aggregated across ALL lines** — it has no per-line breakdown, so "last line settles" cannot be derived from the reservation itself and requires a brand-new per-`(orderId, orderLineId)` settlement-tracking entity, counted against the event's `totalLines`; (3) **the last-line → SETTLED transition is a race** between concurrent settle events for different lines of the same order and needs its own row-level lock (a new `PESSIMISTIC_WRITE` lookup on `StockReservationEntity` by `orderId`), acquired in a fixed order relative to the ingredient-balance locks to avoid deadlock.

**Primary recommendation:** Extract the private per-target recipe-resolution logic (`accumulateRecipe`/`computeRequired`) out of `InventoryReservationService` into a small shared domain service so both the Phase 15 whole-order reservation path and the new Phase 16 single-line settlement path call the *same* code (not a copy) — this is what "reuse the exact Phase 15 recipe-resolution path" should mean structurally. Add `SETTLED` to `StockReservationEntity.ReservationStatus`, add `CONSUMPTION` to `InventoryMovementType`, add a new `OrderLineLookupPort`/adapter pair for the cross-context read, add a new `InventoryLineSettlementEntity` (unique on `order_id, order_line_id`) that serves both as the double-settlement guard and the last-line counter, and apply WR-01 (`REQUIRES_NEW` ledger insert) from day one in the new consumer instead of copying the pre-fix idiom.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Settle-trigger event consumption (Kafka listener) | API/Backend (inventory_context infra) | — | Mirrors `OrderCreatedListener` — thin `@KafkaListener` delegating to a `@Transactional` service |
| Recipe re-resolution for one order line | API/Backend (inventory_context domain/application) | Database (menu recipe read via `MenuRecipeCostingPort`) | Inventory owns recipe interpretation; menu context is the data source via existing port |
| Order line read (dishId, quantity, selected toppings) | API/Backend (order_context infra, new adapter) | — | Order context owns its own entity; inventory reads it through a new port (anti-corruption layer), never directly via JPA across contexts |
| Stock balance decrement (on_hand, reserved) | Database / Storage (inventory_context, `InventoryStockBalanceEntity`) | API/Backend (pessimistic-lock loop in application service) | Same balance table Phase 14/15 already own; lock discipline lives in the application service |
| CONSUMPTION audit movement | Database / Storage (`InventoryStockMovementEntity`) | API/Backend (written directly by the new settlement service, not via `InventoryStockService.recordMovement`) | Movement table is shared/reused; the *service method* is not reusable (see Pitfall: Don't Reuse `recordMovement`) |
| Reservation SETTLED transition + last-line detection | API/Backend (inventory_context application) | Database (`StockReservationEntity` + new settlement-tracking table) | Business rule owned by inventory; needs a new row lock to make the count-then-flip atomic |
| Idempotency (eventId ledger + per-line guard) | Database / Storage (`InventoryProcessedEventEntity` + new entity) | API/Backend (`REQUIRES_NEW` bean method) | Same ledger pattern as Phase 15, fixed per WR-01 |
| DLT routing on missing reservation | API/Backend (Kafka error-handler config) | — | `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`, same as Phase 15 |

## Phase Requirements

> No formal `REQ-XX` IDs exist for this phase (ROADMAP.md §Phase 16: "Driven by 16-CONTEXT.md decisions ... no formal REQ IDs"). The CONTEXT.md decision IDs (D-01..D-06) serve as the requirement identifiers the planner should map plans against.

| ID | Description | Research Support |
|----|-------------|------------------|
| D-01 | Inbound settle-trigger event `(eventId, orderId, orderLineId, totalLines)`, Jackson-3 serde, no ingredient amounts carried | See "Event Contract" pattern below; mirrors `OrderCreatedEvent`/`OrderStockResultEvent` record style |
| D-02 | Re-resolve single line's recipe at settle time; subtract under sorted-ingredient-id lock; log recipe drift | Reuse `InventoryReservationService.accumulateRecipe` logic (lines 183-222) — recommend extracting to shared service; reuse sorted-lock loop (lines 100-122) |
| D-03 | Non-negative clamp on `on_hand`, log anomaly | New logic — no existing clamp helper; `InventoryStockService.recordMovement` throws instead of clamping (do not reuse that method) |
| D-04 | Mark reservation `SETTLED` only on last line via `totalLines` count | New `InventoryLineSettlementEntity` + new locked reservation lookup — `StockReservationEntity` has no per-line manifest (confirmed by reading the entity) |
| D-05 | Idempotency: eventId ledger + per-line guard; missing HELD reservation → DLT | Reuse `InventoryProcessedEventEntity` pattern; DLT wiring mirrors `InventoryKafkaConsumerConfig` exactly |
| D-06 | Apply WR-01 (`REQUIRES_NEW` ledger insert) and WR-02 (audit movement) from Phase 15 review | See `15-REVIEW.md` WR-01/WR-02 verbatim below; apply the fix pattern directly, do not copy the pre-fix idiom |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-kafka | Managed by `spring-boot-starter-parent:4.0.6` BOM [VERIFIED: pom.xml:8] | `@KafkaListener`, `ConsumerFactory`, `DefaultErrorHandler`, `DeadLetterPublishingRecoverer` | Already used identically by `InventoryKafkaConsumerConfig` (Phase 15) — same wiring style must be mirrored, not reinvented |
| Jackson 3 (`JacksonJsonDeserializer`/`JacksonJsonSerializer`) | Managed by Spring Boot 4 BOM [VERIFIED: pom.xml:8, used at InventoryKafkaConsumerConfig.java:25-26] | Kafka message serde with native `Instant`/record support | Established Phase 15 decision: legacy Jackson-2 `JsonDeserializer` cannot handle `java.time.Instant` on this Boot 4/Jackson 3 classpath (no `jackson-datatype-jsr310` on the classpath) |
| Spring Data JPA (`@Lock(PESSIMISTIC_WRITE)`) | Managed by Boot 4 BOM | Row-level locking for balance and (new) reservation rows | Exact mechanism `InventoryStockBalanceRepository.lockByIngredientAndLocation` already uses (repository.java:22-26) |
| Lombok | Managed by Boot 4 BOM | `@Getter`/`@Setter`/`@RequiredArgsConstructor` | Used on every entity/service in this codebase — convention, not choice |

**No new dependencies required** — this matches CONTEXT.md's explicit constraint and is verified by inspecting `pom.xml`: spring-kafka, Jackson 3, and Spring Data JPA are already present and already used for the identical pattern in Phase 15.

**Installation:** None. All required libraries are already declared in `pom.xml` and used by `com.example.feat1.DDD.inventory_context.infrastructure.config.InventoryKafkaConsumerConfig`.

**Version verification:** `spring-boot-starter-parent` pinned at `4.0.6` in `pom.xml:8` [VERIFIED: pom.xml]. spring-kafka/Jackson versions are BOM-managed (not separately declared) — a `mvn dependency:tree` run in this environment did not resolve (no network access to confirm exact transitively-resolved versions), so the specific spring-kafka/Jackson patch versions are **[ASSUMED]** to match the CONTEXT.md-stated "spring-kafka 4.0.5 / Jackson 3" based on training-data BOM knowledge for Boot 4.0.6 — the planner should not need this confirmed further since no new dependency is being added and Phase 15's identical wiring already compiles/runs against this exact BOM.

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AssertJ + Mockito + JUnit 5 | Managed by `spring-boot-starter-test` | Unit/slice tests | Used by `InventoryReservationServiceTest` and `InventoryKafkaConsumerConfigTest` — same pattern applies to the new settlement service/consumer tests |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| New `InventoryLineSettlementEntity` for last-line counting | Adding a `settledLines` counter column directly on `StockReservationEntity` | A counter column is simpler but loses the per-line audit trail (which `orderLineId`s settled, when) and cannot double as the idempotency guard on `(orderId, orderLineId)` — CONTEXT.md D-05 explicitly wants a per-line guard, so the dedicated entity satisfies both D-04 and D-05 with one table |
| Reusing `InventoryStockService.recordMovement()` for the CONSUMPTION movement | Writing `InventoryStockMovementEntity` directly in the new settlement service | `recordMovement()` acquires its own single-ingredient lock via `findByIngredient_IdAndLocationCode` (no lock) and throws `stockInsufficient()` rather than clamping — incompatible with the multi-ingredient sorted-lock-then-clamp semantics this phase needs (see Pitfalls) |

## Package Legitimacy Audit

No external packages are being installed in this phase — all libraries used (spring-kafka, Jackson 3, Spring Data JPA, Lombok, JUnit/Mockito/AssertJ) are already present in `pom.xml` and already exercised by Phase 15 code. The Package Legitimacy Gate is not applicable.

**Packages removed due to slopcheck [SLOP] verdict:** none — no packages considered.
**Packages flagged as suspicious [SUS]:** none.

## Architecture Patterns

### System Architecture Diagram

```
                     ┌──────────────────────────────┐
                     │   kitchen_context (Phase 17)  │
                     │  publishes settle-trigger      │
                     │  (eventId, orderId,            │
                     │   orderLineId, totalLines)      │
                     └───────────────┬───────────────┘
                                     │ Kafka topic: <settle-trigger-topic>
                                     ▼
                     ┌──────────────────────────────┐
                     │ SettleTriggerListener (new)   │  inventory_context/infrastructure/adapter
                     │  @KafkaListener → delegate    │
                     └───────────────┬───────────────┘
                                     ▼
        ┌────────────────────────────────────────────────────────┐
        │ InventoryReservationSettlementService (new, @Transactional) │
        │                                                          │
        │  1. Idempotency fast-check: ledger + line-settlement     │
        │     guard exists? → return (already processed)           │
        │  2. Ledger insert in REQUIRES_NEW bean method (WR-01)     │
        │  3. Read order line via OrderLineLookupPort (new port) ──┼──► order_context
        │     → dishId, quantity, selectedToppings                 │    (new adapter reads
        │  4. Re-resolve recipe: DISH + each TOPPING_OPTION         │     OrderLineEntity via
        │     via shared RecipeRequirementResolver (extracted)      │     a new repository)
        │     → Map<ingredientId, requiredQty>            ─────────┼──► menu_context
        │                                                          │    (MenuRecipeCostingPort,
        │  5. Load StockReservationEntity by orderId,               │     reused unchanged)
        │     PESSIMISTIC_WRITE (new lock method) — verify HELD;    │
        │     missing → throw (routes to DLT after retries)         │
        │  6. Sorted-ascending-ingredientId loop:                   │
        │     lock balance row, subtract required qty from          │
        │     reserved_quantity AND quantity_on_hand,                │
        │     clamp on_hand ≥ 0 (log anomaly if clamped)             │
        │  7. Persist one InventoryStockMovementEntity               │
        │     (CONSUMPTION) per ingredient (WR-02)                   │
        │  8. Insert InventoryLineSettlementEntity                   │
        │     (orderId, orderLineId) — unique guard + counter row    │
        │  9. Count settled lines for orderId; if == totalLines,     │
        │     reservation.status = SETTLED                           │
        └────────────────────────────────────────────────────────┘
                                     │
                                     ▼ (on failure after retries, or
                                        missing-reservation exception)
                     ┌──────────────────────────────┐
                     │  <settle-trigger-topic>.DLT   │
                     └──────────────────────────────┘
```

### Recommended Project Structure
```
src/main/java/com/example/feat1/DDD/inventory_context/
├── domain/
│   ├── model/
│   │   └── InventoryMovementType.java          # add CONSUMPTION
│   ├── port/
│   │   └── OrderLineLookupPort.java            # NEW — cross-context read port
│   ├── snapshot/
│   │   └── OrderLineRecipeSnapshot.java         # NEW — dishId/quantity/toppingOptionIds
│   └── service/
│       └── RecipeRequirementResolver.java       # NEW — extracted from InventoryReservationService
├── application/
│   ├── InventoryReservationService.java         # UNCHANGED (Phase 15) — refactor to use extracted resolver
│   └── InventoryReservationSettlementService.java  # NEW — settlement business logic
├── application/event/
│   └── SettleTriggerEvent.java                  # NEW — inbound event record (eventId, orderId, orderLineId, totalLines)
├── infrastructure/
│   ├── adapter/
│   │   └── SettleTriggerListener.java           # NEW — thin @KafkaListener
│   ├── config/
│   │   ├── InventoryKafkaConsumerConfig.java    # extend: new ConsumerFactory/ContainerFactory bean(s)
│   │   └── InventoryKafkaTopicConfig.java       # extend: new NewTopic + DLT beans
│   ├── entity/
│   │   ├── StockReservationEntity.java          # add SETTLED to ReservationStatus
│   │   └── InventoryLineSettlementEntity.java   # NEW — (order_id, order_line_id) guard + counter
│   └── repository/
│       ├── StockReservationRepository.java      # add lockByOrderId (PESSIMISTIC_WRITE)
│       └── InventoryLineSettlementRepository.java # NEW

src/main/java/com/example/feat1/DDD/order_context/
└── infrastructure/
    ├── adapter/
    │   └── OrderLineLookupAdapter.java           # NEW — implements OrderLineLookupPort
    └── repository/
        └── OrderLineRepository.java              # NEW — JpaRepository<OrderLineEntity, UUID> (does not exist today)
```

### Pattern 1: Extract recipe-resolution into a shared domain service (recommended refactor)
**What:** `InventoryReservationService.computeRequired`/`accumulateRecipe` (lines 165-222) are `private` instance methods scoped to whole-order resolution. CONTEXT.md D-02 says the new service must reuse "the exact Phase 15 recipe-resolution path." Copy-pasting the private methods would violate DRY and risk drift (recipe-resolution bugs fixed in one place, not the other).
**When to use:** Any time two application services need identical per-target (DISH/TOPPING_OPTION) recipe-to-ingredient resolution logic.
**Example (recommended shape, not existing code):**
```java
// Source: derived from InventoryReservationService.java:183-222 (existing, private)
package com.example.feat1.DDD.inventory_context.domain.service;

public class RecipeRequirementResolver {
  private final MenuRecipeCostingPort menuRecipeCostingPort;
  private final IngredientRepository ingredientRepository;

  /** Resolves required base quantity per ingredient for ONE target (dish or topping option). */
  public void accumulate(
      Map<UUID, BigDecimal> required, RecipeTargetType targetType, UUID targetId, int qty) {
    // identical body to InventoryReservationService.accumulateRecipe
  }
}
```
`InventoryReservationService` (Phase 15, whole-order) and the new `InventoryReservationSettlementService` (Phase 16, single-line) both depend on this resolver. This is a refactor of existing Phase 15 code — flag it explicitly in the plan since it touches a file the code review already flagged issues in (WR-01/WR-02 both live in `InventoryReservationService.java`); keep the refactor mechanical (extract-method) to avoid reopening those findings.

### Pattern 2: Cross-context read port (mirrors `MenuRecipeCostingPort`)
**What:** `inventory_context` needs `dishId`, `quantity`, and `selectedToppings` for one `OrderLineEntity` by id. No such read path exists today — `OrderLineEntity` is only reachable via `OrderEntity.lines` (`@OneToMany(mappedBy = "order", fetch = LAZY)`, confirmed at `OrderLineEntity.java:31-33` and `OrderEntity.java:66-71`), and no `OrderLineRepository` exists in `order_context/infrastructure/repository/` (confirmed: only `OrderCartLineRepository`, `OrderCartRepository`, `OrderProcessedEventRepository`, `OrderRepository` exist).
**When to use:** Any cross-bounded-context read. This codebase's established convention (see `MenuRecipeCostingPort` domain/port interface in `inventory_context`, implemented by `MenuRecipeCostingAdapter` in `menu_context/infrastructure/adapter/`) is: **the consuming context defines the port; the owning context implements the adapter.**
**Example:**
```java
// Source: pattern from MenuRecipeCostingPort.java + MenuRecipeCostingAdapter.java
// NEW FILE: inventory_context/domain/port/OrderLineLookupPort.java
package com.example.feat1.DDD.inventory_context.domain.port;

public interface OrderLineLookupPort {
  Optional<OrderLineRecipeSnapshot> findLine(UUID orderId, UUID orderLineId);
}

// NEW FILE: inventory_context/domain/snapshot/OrderLineRecipeSnapshot.java
public record OrderLineRecipeSnapshot(
    UUID orderLineId, UUID dishId, int quantity, List<UUID> selectedToppingOptionIds) {}

// NEW FILE: order_context/infrastructure/adapter/OrderLineLookupAdapter.java
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
Note the `orderId` parameter defends against an `orderLineId` UUID collision belonging to a *different* order (defense-in-depth; the settle-trigger event nominally always carries a consistent pair, but validating both closes a theoretical cross-order data leak).

### Pattern 3: Reservation lock ordering to prevent deadlock across concurrent line settlements
**What:** Two lines of the *same* order can settle concurrently (kitchen may mark two items "preparing" close together). If both lines share an ingredient, and each transaction locks (a) the reservation row and (b) the ingredient balance rows in different orders, a classic lock-ordering deadlock is possible.
**When to use:** Any time settlement needs both a reservation-level lock (for the last-line/SETTLED check) and ingredient-level locks (for the balance decrement) within the same transaction.
**Example (recommended order — consistent across ALL settlement transactions):**
```java
// 1. Lock the reservation row FIRST (single row, cheap, and needed anyway to verify HELD).
StockReservationEntity reservation = reservationRepository
    .lockByOrderId(orderId)                 // NEW: PESSIMISTIC_WRITE, mirrors lockByIngredientAndLocation
    .orElseThrow(() -> missingReservation(orderId)); // → routes to DLT (D-05)
if (reservation.getStatus() != ReservationStatus.HELD) {
  // already SETTLED (or future non-HELD state) — treat as already-processed, not an error
  return;
}

// 2. THEN lock ingredient balances in canonical ascending-ingredientId order
//    (identical discipline to InventoryReservationService.java:100-122).
List<UUID> sortedIngredientIds = required.keySet().stream().sorted().toList();
for (UUID ingredientId : sortedIngredientIds) {
  InventoryStockBalanceEntity balance =
      balanceRepository.lockByIngredientAndLocation(ingredientId, DEFAULT_LOCATION)
          .orElseThrow(...); // should exist — it existed at reserve time
  // subtract, clamp, see Pattern 4
}
```
Locking the single reservation row before the (possibly overlapping) set of ingredient balance rows, and *always* acquiring ingredient locks in the same sorted order, gives a total lock order across all concurrent settlement transactions for the same order — the same deadlock-avoidance discipline Phase 15 already established for the reserve path (see `InventoryReservationService.java:100` comment "(3) Canonical iteration order... so concurrent reservers acquire row locks in the same sequence").

### Pattern 4: Subtract-and-clamp on the balance (mirror the add loop, invert direction)
**What:** Phase 15's reserve loop (`InventoryReservationService.java:126-133`) does `entity.setReservedQuantity(scale(entity.getReservedQuantity().add(required.get(ingredientId))))`. Settlement inverts this and additionally touches `quantityOnHand`.
**Example:**
```java
// Source: pattern inverted from InventoryReservationService.java:126-133
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
// no explicit balanceRepository.save() needed — entity is already managed (dirty checking),
// same as IN-02 review note on the Phase 15 code; do not add a redundant save() call.
```
CONTEXT.md D-03 only mandates clamping `on_hand`; clamping `reserved_quantity` too is a reasonable and safer discretionary addition (prevents `reserved_quantity` drifting negative from recipe-drift between confirm and settle) — flag this as a discretionary decision for the plan/discuss step if not already locked.

### Pattern 5: WR-01-safe idempotency ledger insert (apply the fix, don't copy the bug)
**What:** Phase 15's `InventoryReservationService.onOrderCreated` (lines 83-95) does `saveAndFlush` + catch `DataIntegrityViolationException` inline in the `@Transactional` business method — `15-REVIEW.md` WR-01 identifies this as leaving the transaction rollback-only on a genuine concurrent duplicate. D-06 explicitly requires the *fixed* pattern in the new consumer.
**Example:**
```java
// Source: fix pattern prescribed by 15-REVIEW.md WR-01 (lines 96-100)
@Service
@RequiredArgsConstructor
public class InventoryReservationSettlementService {

  private final InventoryLedgerWriter ledgerWriter; // separate bean, see below

  @Transactional
  public void onSettleTrigger(SettleTriggerEvent event) {
    if (alreadyProcessed(event)) { return; }         // fast pre-check (cheap, no lock)
    if (!ledgerWriter.tryInsert(event.eventId(), CONSUMER_NAME)) {
      return; // concurrent duplicate — ledger transaction committed/rolled back cleanly, no poison
    }
    // ... business logic in THIS (outer) transaction, unaffected by the ledger's own commit/rollback
  }
}

@Component
public class InventoryLedgerWriter {
  private final InventoryProcessedEventRepository repo;

  /** Runs in its own transaction so a duplicate-key failure here never poisons the caller's tx. */
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

### Pattern 6: Event contract (D-01)
```java
// NEW FILE: inventory_context/application/event/SettleTriggerEvent.java
// (or order/kitchen-shared event package — Claude's Discretion per CONTEXT.md; recommend colocating
// under inventory_context/application/event since inventory OWNS the inbound contract, mirroring how
// order_context owns OrderCreatedEvent even though inventory_context is the consumer of it)
package com.example.feat1.DDD.inventory_context.application.event;

public record SettleTriggerEvent(
    UUID eventId,
    UUID orderId,
    UUID orderLineId,
    int totalLines) {
  public static final String TYPE = "SettleTrigger"; // discretionary but consistent with
                                                       // OrderCreatedEvent.TYPE / OrderStockResultEvent
                                                       // *_TYPE constants used elsewhere
}
```
No `Instant` field is strictly required by D-01, but every other event record in this codebase (`OrderCreatedEvent.occurredAt`, `OrderStockResultEvent`) carries one for observability/tracing — recommend adding `Instant occurredAt` as a discretionary addition consistent with codebase convention, even though not explicitly required.

### Anti-Patterns to Avoid
- **Calling `InventoryStockService.recordMovement()` per ingredient inside the settlement loop:** that method acquires its own lock via `findByIngredient_IdAndLocationCode` (no `@Lock`) independent of the sorted-lock loop, and throws `InventoryDomainException.stockInsufficient()` on shortage instead of clamping. Mixing that lock acquisition into the same transaction as the sorted `lockByIngredientAndLocation` calls the settlement service also needs would double-lock the row (harmless with the same PESSIMISTIC_WRITE lock re-entrant within one tx, but semantically confusing) and would throw instead of clamp on the exact D-03 scenario this phase must handle gracefully. Write `InventoryStockMovementEntity` rows directly.
- **Deriving "last line" from `StockReservationEntity.lines`:** that collection holds per-ingredient TOTALS for the whole order (aggregated across all lines by `computeRequired`), not a per-line manifest — there is no way to tell "has orderLineId X settled" from it. This is precisely why CONTEXT.md D-04 calls for a new tracking entity; do not attempt to infer settlement completeness from the reservation's `ReservationLine` list.
- **Treating "missing HELD reservation" as a poison pill (non-retryable, immediate DLT):** unlike a deserialization failure, a missing reservation could legitimately be a transient ordering issue (the settle-trigger arrives before the `OrderCreated`-triggered reservation commit is visible, if topics/consumer groups race). Let it flow through the default retryable path (3× `FixedBackOff(1000, 3)`, same as Phase 15) so genuine races self-heal, and only land on the DLT after retries are exhausted — do not add this exception to `addNotRetryableExceptions`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Kafka poison-pill safety | Custom try/catch deserialization guard | `ErrorHandlingDeserializer` wrapping `JacksonJsonDeserializer` (already in `InventoryKafkaConsumerConfig`) | Proven Phase 15 pattern; a hand-rolled guard would duplicate `USE_TYPE_INFO_HEADERS`/`TRUSTED_PACKAGES`/`VALUE_DEFAULT_TYPE` hardening already solved once |
| Dead-letter routing | Manual "catch and republish" logic in the listener | `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` (bean-level, declarative) | Same as Phase 15; keeps the listener a thin delegate with zero error-handling logic |
| Recipe → ingredient resolution | Reimplementing DISH/TOPPING_OPTION → ingredient/unit-conversion logic for the single-line case | Extract `InventoryReservationService.accumulateRecipe` into a shared `RecipeRequirementResolver` (Pattern 1) | Two independent implementations of the same non-trivial unit-conversion/recipe-lookup logic will drift; CONTEXT.md D-02 explicitly demands "the exact" path |
| Idempotent Kafka consumption | Ad-hoc dedup logic per consumer | The `InventoryProcessedEventEntity` ledger pattern + `REQUIRES_NEW`-isolated insert (Pattern 5) | Already solved, and the WR-01 fix is specified precisely — apply it, don't re-derive it |

**Key insight:** Almost nothing in this phase is a novel technical problem — it is careful reuse-with-adaptation of Phase 15's exact mechanisms. The engineering risk is entirely in the two structural gaps Phase 15 didn't need to solve (per-line order data access, per-line settlement counting against an order-level reservation) — get those two entities/ports right and the rest is copy-the-pattern.

## Common Pitfalls

### Pitfall 1: Recipe changing between confirm (reserve) and settle
**What goes wrong:** The recipe resolved at settle time may differ from what was resolved at reserve time (ingredient added/removed/quantity changed on the dish or topping recipe between order confirmation and kitchen "preparing").
**Why it happens:** Recipes are live, mutable menu data (`RecipeEntity`/`RecipeLineEntity`); nothing snapshots them at order-line creation time.
**How to avoid:** CONTEXT.md D-02 explicitly accepts this risk ("tolerated and logged") — implement it as an accepted tradeoff: log at WARN/INFO with orderId/orderLineId/old-vs-new context when practical, but do not block settlement on drift detection (no comparison-to-original-reservation-line values is required or even fully possible, since the reservation stores per-ingredient totals for the *whole order*, not per-line).
**Warning signs:** Sum of settled amounts across all lines of an order not matching the original total `reserved_quantity` recorded at confirm time — expected and acceptable per the accepted risk, but worth a follow-up reconciliation metric if this becomes operationally important later (not in scope now).

### Pitfall 2: Concurrent settlement of multiple lines of the same order — lock ordering
**What goes wrong:** Two lines of the same order settle concurrently; if their ingredient sets overlap and each transaction acquires balance locks and the reservation lock in different orders, a deadlock is possible (classic ABBA lock-ordering issue).
**Why it happens:** Concurrent Kafka partitions/consumer threads (or redelivery racing with an in-flight process) processing two `SettleTriggerEvent`s for the same `orderId` at once.
**How to avoid:** Lock the reservation row (single row, `lockByOrderId`) *before* acquiring ingredient balance locks, and always acquire ingredient balance locks in canonical ascending-`ingredientId` order (same discipline as `InventoryReservationService.java:100-122`) — see Pattern 3.
**Warning signs:** Intermittent `deadlock detected` exceptions from the JPA/JDBC layer under concurrent settlement load; test for this explicitly with a concurrency integration test if the phase's validation budget allows.

### Pitfall 3: "Last line" detection with `totalLines` when lines settle out of order
**What goes wrong:** If line 2 of 2 settles before line 1, a naive "is this the Nth event I've seen" counter (rather than a persisted per-line set) would misfire, or a race between the two nearly-simultaneous settlements could see stale counts and neither one flips the reservation to SETTLED.
**Why it happens:** Kafka delivers per-partition ordering guarantees only within a partition/key; if the settle-trigger is keyed by `orderLineId` rather than `orderId`, two lines of the same order can land on different partitions and be consumed concurrently, in any order.
**How to avoid:** Never track "have I seen event #N of totalLines" positionally — instead, persist one `InventoryLineSettlementEntity` row per `(orderId, orderLineId)` (idempotent insert, unique constraint), and after each successful insert, `count(*)` rows for that `orderId` **while holding the reservation's `PESSIMISTIC_WRITE` lock** (Pattern 3) so the count-then-compare-to-`totalLines`-then-flip is atomic against concurrent settlements of sibling lines.
**Warning signs:** Reservation never reaches SETTLED even though all lines have individually processed successfully — indicates the count-and-flip wasn't done under a lock that serializes concurrent siblings.

### Pitfall 4: Per-`(orderId, orderLineId)` double-settlement guard vs. the eventId ledger — these are not redundant
**What goes wrong:** Treating the eventId ledger as sufficient and skipping the per-line guard (or vice versa) leaves a gap: the eventId ledger only catches *exact* redelivery of the same Kafka record; it does not protect against a hypothetical future producer bug that mints a *new* `eventId` for a logically-duplicate settle-trigger of the same `(orderId, orderLineId)` (e.g., a kitchen-side retry that re-publishes with a fresh id after a timeout, unaware the first publish actually succeeded).
**Why it happens:** Two independent failure modes — infra-level redelivery (same event, same id) vs. application-level duplicate intent (different event, same business meaning) — need two independent guards.
**How to avoid:** Implement both, as CONTEXT.md D-05 requires: fast pre-check on both `existsByEventIdAndConsumerName` and the new `existsByOrderIdAndOrderLineId`, and let the same `InventoryLineSettlementEntity` row double as (a) the durable per-line idempotency guard and (b) the last-line counter — one table serves two purposes since D-04 and D-05 both key on the same `(orderId, orderLineId)` pair.
**Warning signs:** A line double-deducted only under the specific scenario of a same-payload-different-eventId duplicate — hard to reproduce without a deliberate test that inserts two settle-trigger events with different `eventId`s but identical `(orderId, orderLineId)`.

### Pitfall 5: Non-negative clamp semantics — clamp per-ingredient, not per-event
**What goes wrong:** If a multi-ingredient line settlement clamps only the *last* ingredient processed, or aborts the whole transaction on the first negative-would-occur ingredient, the movement audit trail becomes inconsistent (some ingredients decremented correctly, one clamped, remaining ingredients of the same line never processed).
**Why it happens:** A naive `if (anyIngredientWouldGoNegative) throw` implementation (copying `InventoryStockService.recordMovement`'s `stockInsufficient()` throw behavior) would roll back the entire settlement — but CONTEXT.md D-03 requires this to be non-blocking ("should not happen... clamp on_hand to 0 and log the anomaly; never go negative"), i.e., settlement must always succeed and clamp per-ingredient independently, never throw for insufficient stock.
**How to avoid:** Apply the clamp inline within the same sorted-lock loop, per ingredient, as shown in Pattern 4 — no exception path for "stock insufficient" exists in this settlement flow (that's a Phase 14 concept for manual movements, not applicable here).
**Warning signs:** A test asserting settlement rejects/rolls back on insufficient on-hand would indicate a misunderstanding of D-03 — the correct behavior is "always succeeds, clamps and logs."

### Pitfall 6: How inventory reaches order-line data — cross-context read, not event payload
**What goes wrong:** A tempting shortcut is to have the Phase 17 kitchen producer enrich the settle-trigger event with `dishId`/`selectedToppings` directly (avoiding the need for a new port) — but CONTEXT.md D-01 explicitly forbids this ("Inventory re-resolves the line's recipe itself — the event does not carry ingredient amounts (kitchen must not duplicate inventory's recipe knowledge)") and the "Specific Ideas" section reinforces "kitchen event stays thin."
**Why it happens:** It looks like less work for Phase 16 if Phase 17 just includes the data. But it inverts the dependency (kitchen would need to know about inventory recipe-resolution details) and violates the explicit boundary decision.
**How to avoid:** Build the `OrderLineLookupPort`/adapter (Pattern 2) so inventory reads `dishId`, `quantity`, and `selectedToppings` itself, keyed only by `(orderId, orderLineId)` from the thin event.
**Warning signs:** Any plan task that proposes adding fields to the settle-trigger event contract beyond `(eventId, orderId, orderLineId, totalLines)` should be flagged against D-01.

## Code Examples

### Adding SETTLED to the reservation status enum
```java
// Modify: StockReservationEntity.java:44-46
public enum ReservationStatus {
  HELD,
  SETTLED
}
```

### Adding CONSUMPTION to the movement type enum
```java
// Modify: InventoryMovementType.java
public enum InventoryMovementType {
  RECEIPT,
  ADJUSTMENT_IN,
  ADJUSTMENT_OUT,
  WASTE,
  STOCK_COUNT,
  CONSUMPTION; // NEW — settlement of a reservation into an actual deduction (Phase 16)

  public boolean isInbound() { return this == RECEIPT || this == ADJUSTMENT_IN; }
  public boolean isOutbound() {
    return this == ADJUSTMENT_OUT || this == WASTE || this == CONSUMPTION; // add CONSUMPTION
  }
  public boolean isCount() { return this == STOCK_COUNT; }
}
```
Note: the settlement service does not call `isOutbound()`/`isInbound()` directly (it does not go through `InventoryStockService.recordMovement`), but classifying `CONSUMPTION` correctly keeps the enum's own semantics consistent for any future code (e.g., reporting) that filters movements by `isOutbound()`.

### New locked reservation lookup (mirrors `lockByIngredientAndLocation`)
```java
// Add to StockReservationRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select r from StockReservationEntity r where r.orderId = :orderId")
Optional<StockReservationEntity> lockByOrderId(UUID orderId);
```

### New settlement-tracking entity
```java
// NEW FILE: inventory_context/infrastructure/entity/InventoryLineSettlementEntity.java
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
  // getters/setters via Lombok, same convention as every other entity in this package
}
```
```java
// NEW FILE: InventoryLineSettlementRepository.java
public interface InventoryLineSettlementRepository
    extends JpaRepository<InventoryLineSettlementEntity, UUID> {
  boolean existsByOrderIdAndOrderLineId(UUID orderId, UUID orderLineId);
  long countByOrderId(UUID orderId);
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| Reservation = terminal state (Phase 15 scope) | Reservation = intermediate state; settlement consumer converts HELD → SETTLED (this phase) | Phase 16 (this phase) | `ReservationStatus` becomes a real 2-state (soon 3-state, once release-on-cancel lands later) lifecycle instead of a single fixed value |
| Fire-and-forget movement recording only via `InventoryStockService.recordMovement` (Phase 14 admin/staff API) | A second, parallel movement-writing path (the settlement service) that writes `InventoryStockMovementEntity` directly, bypassing `recordMovement`'s single-lock/throw-on-insufficient semantics | Phase 16 (this phase) | Two code paths now write to `inventory_stock_movements` — document this bifurcation clearly in the plan/summary so a future reader does not assume `recordMovement` is the only writer |

**Deprecated/outdated:** Nothing existing is deprecated by this phase — it is purely additive.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Exact transitively-resolved spring-kafka/Jackson patch versions ("spring-kafka 4.0.5") match Boot 4.0.6's BOM | Standard Stack | Low — no new dependency is added; Phase 15 already compiles/runs against whatever the BOM resolves to, so this phase inherits the same (working) versions regardless of the exact number |
| A2 | `SettleTriggerEvent` should live in `inventory_context.application.event` (inventory owns the inbound contract it defines) rather than a shared/neutral package | Architecture Patterns, Pattern 6 | Low — a naming/location choice only; CONTEXT.md leaves topic/event-class location to Claude's Discretion, and Phase 17 (the producer) will need to depend on whatever record type inventory defines regardless of package |
| A3 | Clamping `reserved_quantity` to zero (not just `on_hand`) is a safe, recommended addition beyond the literal D-03 wording | Pattern 4 | Low-medium — if the planner/discuss step disagrees, `reserved_quantity` could theoretically go negative on sufficiently large recipe drift; flag for explicit confirmation during planning/discussion if not already settled |
| A4 | The settle-trigger topic/consumer-group/DLT names proposed (`kitchen.settlement-trigger`, `inventory-settlement` group id) are illustrative, not fixed | Pattern 6, Code Examples | None — CONTEXT.md explicitly leaves these to Claude's Discretion; the planner should pick concrete names and the Phase 17 producer must match them |

## Open Questions

1. **Should the reservation lock (`lockByOrderId`) be acquired even when the reservation is not found (missing-reservation DLT case)?**
   - What we know: `lockByOrderId` returning `Optional.empty()` naturally handles "no reservation exists" without a separate existence check.
   - What's unclear: whether a *non-HELD* (already `SETTLED`) reservation found by `lockByOrderId` should be treated as "already fully settled — likely a duplicate/redelivery, skip silently" vs. an error condition.
   - Recommendation: treat `SETTLED` as a benign already-processed signal (return early, no DLT), since the per-`(orderId, orderLineId)` guard is the authoritative duplicate check; a `SETTLED` reservation encountered again just means all lines already settled and this event is redundant.

2. **Exact settle-trigger topic name, DLT name, and consumer group id.**
   - What we know: CONTEXT.md explicitly defers these to Claude's Discretion; Phase 17 (the producer) does not exist yet, so there is no existing name to match.
   - What's unclear: the property-key naming convention the planner should adopt (existing convention: `<context>.events.<name>-topic`, e.g. `order.events.order-created-topic`).
   - Recommendation: adopt `kitchen.events.settle-trigger-topic` (default `kitchen.settlement-trigger`) as the property key/topic-name pair, consumer group `inventory.settlement.consumer.group-id` (default `inventory-settlement`) — mirrors `inventory.order-created.consumer.group-id` exactly. Phase 17's plan must read these same property names.

## Environment Availability

This phase is code/config-only (new consumer, entities, repository, port/adapter) with no live-broker dependency in its own test suite (mirrors Phase 15: `spring.kafka.listener.auto-startup=false` in `src/test/resources/application.properties`, and `InventoryKafkaConsumerConfigTest` instantiates the config class directly with no Spring context or broker). No external tool/service audit needed beyond what Phase 15 already established.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Kafka broker | Live end-to-end settle-trigger flow (Phase 17 integration, not this phase) | Not required for this phase | — | Unit/slice tests only, per CONTEXT.md "Dependency note" — the Phase 17 producer does not exist yet |
| MySQL (via `mysql-connector-j`) | Entity persistence, JPA locking tests | ✓ (already used by every other phase) | — | — |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** Kafka broker — exercised via slice tests only (no live broker needed), consistent with the phase's explicit scope (producer lands in Phase 17).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + AssertJ (via `spring-boot-starter-test`, already in `pom.xml`) |
| Config file | none dedicated — `src/test/resources/application.properties` sets `spring.kafka.listener.auto-startup=false` for any test that does load a Spring context |
| Quick run command | `mvn test -Dtest=InventoryReservationSettlementServiceTest,SettleTriggerListenerTest,InventoryKafkaConsumerConfigTest` (pattern mirrors existing `InventoryReservationServiceTest`/`InventoryKafkaConsumerConfigTest`) |
| Full suite command | `mvn test` (full Maven suite — 138 tests passing as of Phase 15 completion per STATE.md) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| D-02 | Single-line recipe re-resolution + sorted-lock subtract | unit | `mvn test -Dtest=InventoryReservationSettlementServiceTest#singleLineRecipeReResolvedAndDeducted` | ❌ Wave 0 |
| D-03 | Non-negative clamp on `on_hand` (and `reserved_quantity`) when settlement would go negative | unit | `mvn test -Dtest=InventoryReservationSettlementServiceTest#clampsOnHandToZeroAndLogsAnomaly` | ❌ Wave 0 |
| D-04 | Reservation flips to SETTLED only when count of settled lines == totalLines | unit | `mvn test -Dtest=InventoryReservationSettlementServiceTest#marksSettledOnlyWhenLastLineSettles` | ❌ Wave 0 |
| D-04 | Concurrent/out-of-order settlement of 2 lines does not double-flip or under-flip | unit (simulated via two sequential calls with mocked lock semantics; true concurrency needs an integration test if budget allows) | `mvn test -Dtest=InventoryReservationSettlementServiceTest#outOfOrderLastLineStillFlipsExactlyOnce` | ❌ Wave 0 |
| D-05 | eventId ledger + per-`(orderId, orderLineId)` guard both prevent double-deduction | unit | `mvn test -Dtest=InventoryReservationSettlementServiceTest#duplicateEventIdSkips` and `#duplicateOrderLineSkipsEvenWithNewEventId` | ❌ Wave 0 |
| D-05 | Missing HELD reservation routes to DLT (not silently swallowed) | slice (exception propagation from service; DLT routing itself covered by config-level test, mirrors `InventoryKafkaConsumerConfigTest`) | `mvn test -Dtest=InventoryReservationSettlementServiceTest#missingReservationThrowsForDltRouting` | ❌ Wave 0 |
| D-06 | Ledger insert isolated in `REQUIRES_NEW` bean; concurrent duplicate does not poison the business transaction | unit | `mvn test -Dtest=InventoryLedgerWriterTest#concurrentDuplicateRollsBackIndependently` | ❌ Wave 0 |
| D-06 | CONSUMPTION movement recorded per settlement | unit | `mvn test -Dtest=InventoryReservationSettlementServiceTest#recordsConsumptionMovementPerIngredient` | ❌ Wave 0 |
| D-01 | Kafka wiring: `ErrorHandlingDeserializer` → `JacksonJsonDeserializer`, `AckMode.RECORD`, non-retryable deserialization exceptions | slice (broker-free config test, mirrors `InventoryKafkaConsumerConfigTest` exactly) | `mvn test -Dtest=SettleTriggerKafkaConsumerConfigTest` | ❌ Wave 0 |
| D-01 | Event serde round-trip (Jackson-3, if `Instant` field added per discretionary Pattern 6 note) | unit | `mvn test -Dtest=EventSerdeRoundTripTest` (extend existing class, mirrors its `OrderCreatedEvent`/`OrderStockResultEvent` cases) | ✓ exists (extend) |

### Sampling Rate
- **Per task commit:** the targeted `-Dtest=...` command for the class(es) touched by that task.
- **Per wave merge:** `mvn test -Dtest='*Inventory*,*SettleTrigger*'` (broaden to the whole inventory_context + new event package).
- **Phase gate:** Full suite green (`mvn test`) before `/gsd:verify-work`.

### Wave 0 Gaps
- [ ] `src/test/java/.../inventory_context/application/InventoryReservationSettlementServiceTest.java` — covers D-02/D-03/D-04/D-05/D-06 (new file, mirrors `InventoryReservationServiceTest` structure/mocking style exactly)
- [ ] `src/test/java/.../inventory_context/application/InventoryLedgerWriterTest.java` — covers D-06 (WR-01 isolation) if `InventoryLedgerWriter` is extracted as its own bean; alternatively fold into the settlement service test if the bean method lives inside the same service class
- [ ] `src/test/java/.../inventory_context/infrastructure/config/SettleTriggerKafkaConsumerConfigTest.java` — covers D-01 wiring (new file, mirrors `InventoryKafkaConsumerConfigTest` exactly, broker-free)
- [ ] `src/test/java/.../order_context/infrastructure/adapter/OrderLineLookupAdapterTest.java` — covers the new cross-context port/adapter (new file; no existing equivalent since this port is new — closest analogue is `MenuRecipeCostingAdapter`, which has no dedicated test file today, so this may be the first adapter test of its kind in the repo — worth noting as a slight departure from precedent, or verify via the existing `OrderSubmissionIntegrationTest`-style integration test if unit-testing a thin JPA-repository-backed adapter is judged low-value)
- [ ] Extend `src/test/java/.../order_context/EventSerdeRoundTripTest.java` with a `SettleTriggerEvent` case if an `Instant` field is added (Pattern 6 discretionary note)

*(No framework install needed — JUnit 5/Mockito/AssertJ are already configured and exercised by 138 passing tests as of Phase 15.)*

## Security Domain

`security_enforcement` is not set in `.planning/config.json` — treated as enabled per default.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V2 Authentication | No | This phase adds no HTTP endpoint (explicitly out of scope — staff endpoint moved to Phase 17); the consumer has no caller-facing auth surface |
| V3 Session Management | No | No session-bearing surface introduced |
| V4 Access Control | No | No endpoint; internal Kafka consumer only, not reachable by end users |
| V5 Input Validation | Yes | Kafka payload deserialization hardening: `ErrorHandlingDeserializer` + `JacksonJsonDeserializer` with `USE_TYPE_INFO_HEADERS=false`, a fixed `VALUE_DEFAULT_TYPE`, and a `TRUSTED_PACKAGES` allow-list — same pattern already established in `InventoryKafkaConsumerConfig.java:59-66`, must be replicated identically for the new `SettleTriggerEvent` consumer factory |
| V6 Cryptography | No | No cryptographic operation in this phase |

### Known Threat Patterns for {stack}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|----------------------|
| Malicious `__TypeId__` Kafka header causing deserialization type confusion | Tampering/Spoofing | `JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS=false` + fixed `VALUE_DEFAULT_TYPE` + `TRUSTED_PACKAGES` allow-list (established Phase 15 pattern at `InventoryKafkaConsumerConfig.java:59-66`) — apply identically to the new consumer factory |
| Poison-pill payload (malformed JSON, unexpected schema) blocking the partition | Denial of Service | `ErrorHandlingDeserializer` wraps deserialization failures as a recoverable `DeserializationException`, classified non-retryable, routed straight to the DLT by `DeadLetterPublishingRecoverer` — same wiring, new topic |
| Business-level double-processing / replay causing double stock deduction | Tampering (of business state, not transport) | Idempotency ledger (`InventoryProcessedEventEntity`) + new per-`(orderId, orderLineId)` guard (Pitfall 4) |
| Cross-context read exposing more order data than needed | Information Disclosure (internal, low severity — same trust boundary/process) | `OrderLineLookupPort` returns a narrow `OrderLineRecipeSnapshot` (only `dishId`, `quantity`, `selectedToppingOptionIds`) rather than the full `OrderLineEntity`/`OrderEntity` graph — mirrors `MenuRecipeCostingPort`'s narrow snapshot-record convention |

## Sources

### Primary (HIGH confidence — read directly in this session)
- `pom.xml` — confirms `spring-boot-starter-parent:4.0.6`, no new dependency needed
- `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java` — recipe-resolution logic (165-222), sorted-lock reserve loop (100-133), WR-01 idiom to avoid (83-95), after-commit publish pattern (231-243)
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/StockReservationEntity.java` — confirms one-row-per-order, no per-line manifest, `ReservationStatus` enum to extend
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryStockBalanceEntity.java` + `InventoryStockBalanceRepository.java` — `lockByIngredientAndLocation` pessimistic-lock pattern to mirror
- `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryStockService.java` + `InventoryStockMovementEntity.java` — confirms `recordMovement()` is NOT reusable for this phase's clamp semantics
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java` + `InventoryKafkaTopicConfig.java` — exact Kafka wiring template to mirror
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryProcessedEventEntity.java` — idempotency ledger shape
- `src/main/java/com/example/feat1/DDD/order_context/application/event/OrderCreatedEvent.java` + `OrderLineEntity.java` + `OrderLineToppingSnapshot.java` + `OrderEntity.java` — confirms no `OrderLineRepository` exists; `OrderLineEntity` only reachable via `OrderEntity.lines`
- `src/main/java/com/example/feat1/DDD/menu_context/infrastructure/adapter/MenuRecipeCostingAdapter.java` + `.../domain/port/MenuRecipeCostingPort.java` — the cross-context port/adapter convention to replicate
- `.planning/phases/15-.../15-REVIEW.md` — WR-01 (rollback-only fix, lines 80-100), WR-02 (audit movement gap, lines 102-119)
- `.planning/phases/16-inventory-reservation-settlement/16-CONTEXT.md` — locked decisions D-01..D-06
- `.planning/STATE.md`, `.planning/ROADMAP.md` §Phase 16/17 — phase boundary and no-formal-REQ-IDs confirmation
- `src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationServiceTest.java` + `InventoryKafkaConsumerConfigTest.java` — existing test conventions to mirror

### Secondary (MEDIUM confidence)
- None — all findings in this research were verified by direct code inspection; no WebSearch was needed since this is a self-contained internal-architecture extension of existing, already-reviewed code.

### Tertiary (LOW confidence)
- Exact spring-kafka/Jackson 3 patch versions (A1 in Assumptions Log) — inferred from CONTEXT.md's stated "spring-kafka 4.0.5" without independent registry verification (no network access in this session; not load-bearing since no new dependency is added).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependency; verified via `pom.xml` and existing Phase 15 usage
- Architecture: HIGH — derived from direct reading of every relevant existing entity/service/config/repository file plus the Phase 15 review findings
- Pitfalls: HIGH — each pitfall is grounded in a specific, cited gap found by reading the actual code (missing `OrderLineRepository`, reservation's lack of per-line manifest, `recordMovement`'s incompatible lock/throw semantics, WR-01's documented bug)

**Research date:** 2026-07-08
**Valid until:** 30 days (stable internal codebase; no fast-moving external dependency risk since no new libraries are introduced) — re-verify sooner only if Phase 17's actual event contract or topic naming diverges from the discretionary defaults proposed here
