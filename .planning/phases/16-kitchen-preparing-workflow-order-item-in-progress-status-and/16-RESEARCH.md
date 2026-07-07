# Phase 16: Kitchen Preparing Workflow - Research

**Researched:** 2026-07-07
**Domain:** Spring Boot 4 / Kafka event-driven settlement, extending an existing DDD saga (order-item-level status transition + Inventory stock settlement)
**Confidence:** HIGH (this phase is 95% "extend existing verified patterns"; the one genuinely novel design question — how Inventory knows "last line settled" — is flagged LOW/open below)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Preparing granularity & order status
- **D-01:** Preparing status is at the **order-item (order line) level** — each `OrderLineEntity`
  gains a preparing status; kitchen staff mark individual items as "đang làm". (Matches the phase
  name "order item in-progress status".)
- **D-06:** Add **`OrderStatus.PREPARING`** (order level). The order transitions
  **`CONFIRMED` → `PREPARING`** when its **first** line starts preparing. No `COMPLETED`/`SERVED`
  status in this phase — later lifecycle states are deferred to a future phase.

#### Trigger
- **D-02:** The transition is triggered by a **new staff API endpoint** (e.g.
  `POST /orders/{id}/items/{lineId}/prepare` or a PATCH status), **role-gated** to kitchen/staff,
  and only valid when the order is `CONFIRMED` (or already `PREPARING`) and the target line has not
  yet been settled. The preparing event is published **after commit** (mirrors the Phase 15
  after-commit producer pattern → consumer must be idempotent).

#### Per-item settlement mechanics
- **D-05:** The Phase 15 reservation (`StockReservationEntity`) stores **aggregate** reserved base
  quantity **per ingredient for the whole order**, not per order-line. To settle per item,
  **re-resolve the recipe requirement for that specific `OrderLine` at prepare time**, reusing the
  exact Phase 15 recipe-resolution path (dish + selected toppings → ingredient lines × line
  quantity, unit-converted via the shared `UnitConverter`). Decrement `quantity_on_hand` **and** the
  balance `reserved_quantity` by that line's per-ingredient amounts. Mark the whole reservation
  `SETTLED` only when the **last** line has been prepared/settled. **No change to the Phase 15
  reservation model.** Accepted risk: a recipe edited between confirm and prepare yields a
  slightly different requirement — this is tolerated and **logged**.

#### Settlement correctness & non-negative invariant
- **D-03:** Settlement is **idempotent** and enforces `quantity_on_hand ≥ 0`:
  - Replay / already-settled line → **no-op** (guarded by the processed-events ledger AND a
    per-`(orderId, orderLineId)` settlement guard so a single item cannot be double-deducted).
  - If `quantity_on_hand < reserved` for an ingredient at settle time (should not happen — Phase 15
    guaranteed availability at reserve time) → **clamp `on_hand` to 0**, log/flag the anomaly, do not
    go negative.
  - Missing HELD reservation for the order → route the record to the **DLT** (do not silently
    swallow).

#### Consumer robustness (apply Phase 15 code-review fixes here)
- **D-04:** The **new settlement consumer** applies the fixes surfaced in the Phase 15 review
  (`15-REVIEW.md`):
  - **WR-01:** isolate the idempotency-ledger insert in a **`REQUIRES_NEW`** transaction so a
    duplicate-key collision does not mark the whole business transaction rollback-only.
  - **WR-02 / audit:** record an **`InventoryStockMovementEntity`** (consumption/issue type) for each
    settlement so the actual deduction is auditable via the Phase 14 movement ledger.
  - Applying the same fix retroactively to the Phase 15 consumers is a **separate** hardening task,
    not part of this phase.

### Claude's Discretion
- Exact endpoint shape/verb, event name (`OrderItemPreparingEvent` / `OrderPreparingEvent`) and new
  topic name, consumer `group-id`, retry counts/backoff, DLT topic naming.
- Whether the order line's preparing status is a new enum on `OrderLineEntity` or a boolean/timestamp.
- `ReservationStatus` additions (e.g. `SETTLED`) and how per-line settlement progress is tracked
  (line status vs. a settled-lines set on the reservation).
- Movement type value used for the deduction.

### Deferred Ideas (OUT OF SCOPE)
- Order lifecycle beyond preparing: `READY` / `SERVED` / `COMPLETED` statuses and their events.
- Reservation **release** on order cancel / refund (no cancel flow exists yet).
- Retroactively applying WR-01/WR-02 fixes to the **Phase 15** consumers (separate hardening task).
- Fixing the remaining Phase 15 review items (WR-03 fire-and-forget send, WR-04 `rejection_reason`
  length, WR-05 global Jackson-2 producer serializer).
- Migrating Payment/Table producers to Jackson-3 (pre-existing defect logged in `deferred-items.md`).
- Multi-location stock, supplier reorder automation, consumer scaling/concurrency tuning.
</user_constraints>

## Summary

Phase 16 is a narrow, well-scoped extension of the Phase 15 saga, not a new subsystem. Phase 15
already built every mechanical piece this phase reuses: the recipe-resolution path
(`InventoryReservationService.computeRequired`/`accumulateRecipe`), the pessimistic-lock
sorted-ingredient-id pattern, the after-commit publish pattern, the Jackson-3
(`JacksonJsonSerializer`/`JacksonJsonDeserializer`) Kafka wiring style, the processed-events ledger
idempotency pattern, and the DLT/`DefaultErrorHandler` pattern. This phase's job is to: (1) add a
staff-triggered, role-gated transition at the order-line level in Order Context that publishes a
new event after commit, (2) add a new Inventory consumer that re-runs the Phase-15 recipe
resolution **per line** and converts `reserved` into an actual `on_hand` deduction, recording an
audit movement, and (3) apply the two Phase-15 code-review fixes (WR-01 `REQUIRES_NEW` ledger
insert, WR-02 movement-based audit) directly in the new consumer rather than retrofitting them.

The one design question this research could **not** resolve from existing code is: since
`StockReservationEntity` stores an aggregate per-ingredient reserved quantity for the **whole
order** (no line-count, no per-line breakdown — confirmed by reading the entity, D-05 forbids
changing it), Inventory has no built-in way to know how many order lines exist for an order or
which ones have already settled. This must be resolved by either (a) carrying a line manifest
(total line count or the full line-id set) on the new preparing event, or (b) a new lightweight
Inventory-side settlement-tracking entity that lazily discovers "how many lines total" the first
time it processes a line for an order. See **Open Questions** — this is flagged for the planner to
decide explicitly, it is the single highest-risk design point in the phase.

**Primary recommendation:** Add `OrderStatus.PREPARING` and an `OrderLineStatus` enum
(`PENDING`/`PREPARING`) on `OrderLineEntity`; add a staff-only endpoint under the existing
`/admin/orders/**` security matcher (already `hasAnyRole("ADMIN","STAFF")` — zero `SecurityConfig`
changes needed) that flips line status, flips order status on the first line, and publishes an
`OrderLinePreparingEvent` after commit (mirroring `OrderSubmissionService.publishAfterCommit`).
Add a new `InventorySettlementService` (mirrors `InventoryReservationService` exactly: ledger
guard with `REQUIRES_NEW` insert, per-line recipe re-resolution, pessimistic-lock deduction,
`InventoryStockMovementEntity` audit row, reservation-settlement tracking) plus a thin
`@KafkaListener` delegate and a consumer/producer/topic config trio mirroring
`InventoryKafkaConsumerConfig`/`OrderKafkaProducerConfig`/`InventoryKafkaTopicConfig` verbatim in
style. No new Maven dependencies — nothing to run through the package-legitimacy gate.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Staff "mark line preparing" trigger + role gate | API / Backend (Order Context) | — | Order Context owns order/line lifecycle state and the staff-facing endpoint surface (matches existing `/admin/orders/**` pattern) |
| Order/line status transition (`CONFIRMED`→`PREPARING`) | API / Backend (Order Context) | — | Same aggregate (`OrderEntity`/`OrderLineEntity`) already owns `OrderStatus`; Phase 15 established this as Order Context's job |
| Preparing-event publication (after commit) | API / Backend (Order Context) | Database (outbox-less, direct Kafka send) | Mirrors `OrderSubmissionService`/`InventoryReservationService` after-commit pattern; event is the cross-context integration point |
| Preparing-event consumption + settlement decision | API / Backend (Inventory Context) | — | Inventory is the stock authority (explicit locked decision); it alone decides deduction/clamp/DLT |
| Recipe re-resolution per line | API / Backend (Inventory Context) | Database (`MenuRecipeCostingPort` reads) | Reuses the exact Phase 15 `InventoryReservationService` resolution path — must live in Inventory since it already depends on `MenuRecipeCostingPort`/`UnitConverter`/`IngredientRepository` |
| Stock deduction (`on_hand`, `reserved`) + non-negative clamp | Database / Storage (via `InventoryStockBalanceEntity` + `PESSIMISTIC_WRITE`) | API / Backend (service enforces clamp logic) | Same balance table and lock strategy Phase 15/14 already use; the invariant is enforced in the service layer over a locked row |
| Audit trail of the deduction | Database / Storage (`InventoryStockMovementEntity`) | — | Phase 14's immutable movement ledger is the established audit mechanism (WR-02) |
| Idempotency / duplicate-delivery guard | Database / Storage (processed-events ledger + new settlement-guard table) | API / Backend (guard logic) | Mirrors Phase 15's `InventoryProcessedEventEntity` unique-constraint pattern, extended for the WR-01 `REQUIRES_NEW` fix |
| DLT / poison-pill handling | API / Backend (Kafka consumer config) | — | Identical `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` pattern as `InventoryKafkaConsumerConfig` |

## Standard Stack

### Core (all already on the classpath — verified against the running build)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-parent | 4.0.6 `[VERIFIED: mvn help:evaluate]` | Framework baseline | Already the project's parent; phase-wide constraint from Phase 15 |
| spring-kafka | 4.0.5 `[VERIFIED: mvn help:evaluate -Dexpression=spring-kafka.version]` | Kafka producer/consumer, `JacksonJsonSerializer`/`JacksonJsonDeserializer`, `DefaultErrorHandler`, `DeadLetterPublishingRecoverer` | Already used identically by Phase 15; native Jackson-3 serde avoids the `Instant` serialization gap Phase 15 hit with the legacy Jackson-2 `JsonSerializer` |
| Jackson 3 (`tools.jackson`) | bundled with Boot 4 parent `[VERIFIED: SecurityConfig.java imports tools.jackson.databind.ObjectMapper]` | Event serde | Confirmed in use (`SecurityConfig` already imports `tools.jackson.databind.ObjectMapper`); Jackson-2 `jackson-datatype-jsr310` is NOT on the classpath, so `Instant`-bearing events must use the Jackson-3 native serde, exactly as 15-04's deviation log documents |
| Lombok | (project-managed) | `@Getter/@Setter/@RequiredArgsConstructor` on entities/services | Consistent with every existing entity/service in the codebase |
| JUnit 5 + AssertJ + Mockito | (project-managed, no version pin found needed) | Unit tests for the new service, mirroring `InventoryReservationServiceTest` style (plain Mockito, no Spring context) | Established test style for the exact class this phase's new service mirrors |

**No new dependencies are required or recommended.** This matches the locked decision
("No new dependencies") and CONTEXT.md's explicit "reuse Phase 15 Kafka wiring style."

### Package Legitimacy Audit

**Not applicable — this phase installs zero external packages.** No `slopcheck` / registry
verification run was needed; skip condition explicitly met (code/config-only phase reusing the
existing dependency set).

## Architecture Patterns

### System Architecture Diagram

```
 Staff client
     │  POST /admin/orders/{orderId}/lines/{lineId}/prepare   (role: ADMIN/STAFF)
     ▼
 ┌─────────────────────────── Order Context ───────────────────────────┐
 │  OrderLinePreparingController                                       │
 │        │                                                            │
 │        ▼                                                            │
 │  OrderLinePreparingService (@Transactional)                         │
 │    1. load OrderEntity + OrderLineEntity, guard: order in           │
 │       {CONFIRMED, PREPARING}; line.status == PENDING                │
 │    2. line.status = PREPARING                                       │
 │    3. if order.status == CONFIRMED -> order.status = PREPARING      │
 │    4. publishAfterCommit(OrderLinePreparingEvent)                   │
 └──────────────────────────────┬────────────────────────────────────--┘
                                 │  Kafka topic: orders.line-preparing
                                 │  (Jackson-3 JacksonJsonSerializer)
                                 ▼
 ┌───────────────────────── Inventory Context ──────────────────────────┐
 │  OrderLinePreparingListener (@KafkaListener, thin delegate)           │
 │        │                                                              │
 │        ▼                                                              │
 │  InventorySettlementService (@Transactional)                          │
 │    1. idempotency: processedEventRepository.existsBy(...)             │
 │       + REQUIRES_NEW ledger insert (WR-01 fix)                        │
 │    2. settlement-guard: exists settlement for (orderId, orderLineId)? │
 │       -> no-op if already settled (double-deduct guard)               │
 │    3. load StockReservationEntity by orderId; missing -> DLT          │
 │       (throw, do not swallow)                                         │
 │    4. re-resolve recipe for THIS line only (reuse                     │
 │       InventoryReservationService.computeRequired-style logic,        │
 │       scoped to one dish + its selected toppings x line quantity)     │
 │    5. for each ingredient (sorted ascending id, same lock-order        │
 │       discipline as Phase 15):                                        │
 │         lock InventoryStockBalanceEntity (PESSIMISTIC_WRITE)           │
 │         on_hand -= need   (clamp at 0, log anomaly if need > on_hand)  │
 │         reserved -= need  (clamp at 0)                                │
 │         record InventoryStockMovementEntity (CONSUMPTION type, WR-02)  │
 │    6. record settlement-guard row for (orderId, orderLineId)           │
 │    7. if all lines for the order now settled -> reservation.status    │
 │       = SETTLED                                                       │
 └────────────────────────────────────────────────────────────────────--┘
```

### Recommended Project Structure (new files only — existing DDD layout unchanged)
```
src/main/java/com/example/feat1/DDD/
├── order_context/
│   ├── domain/model/
│   │   └── OrderLineStatus.java                      # NEW enum: PENDING, PREPARING
│   ├── application/
│   │   ├── OrderLinePreparingService.java             # NEW — transition + after-commit publish
│   │   └── event/OrderLinePreparingEvent.java         # NEW — event contract (mirrors OrderStockResultEvent style)
│   └── infrastructure/
│       ├── presentation/OrderLinePreparingController.java   # NEW staff endpoint (or fold into OrderController)
│       └── config/OrderLinePreparingKafkaProducerConfig.java # NEW producer config (mirrors OrderKafkaProducerConfig)
└── inventory_context/
    ├── application/
    │   └── InventorySettlementService.java             # NEW — mirrors InventoryReservationService structure
    ├── infrastructure/
    │   ├── entity/
    │   │   └── ReservationSettlementEntity.java         # NEW — per-(orderId, orderLineId) settlement guard
    │   ├── repository/ReservationSettlementRepository.java # NEW
    │   ├── adapter/OrderLinePreparingListener.java       # NEW thin @KafkaListener delegate
    │   └── config/
    │       ├── InventoryLinePreparingConsumerConfig.java # NEW (mirrors InventoryKafkaConsumerConfig)
    │       └── InventoryLinePreparingTopicConfig.java     # NEW NewTopic beans (or extend InventoryKafkaTopicConfig)
```

### Pattern 1: Thin listener delegating to a `@Transactional` service (reuse verbatim)
**What:** `@KafkaListener` methods contain zero business logic; they call one method on an
application service.
**When to use:** Every Kafka consumer in this codebase (established convention, code-review
called out compliance as a strength in 15-REVIEW.md).
**Example:**
```java
// Source: src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/adapter/OrderCreatedListener.java
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
Apply identically for `OrderLinePreparingListener` → `InventorySettlementService.onLinePreparing`.

### Pattern 2: After-commit publish via `TransactionSynchronizationManager`
**What:** Register a `TransactionSynchronization` whose `afterCommit()` calls the publisher port;
if no transaction is active (e.g. in a unit test with no `@Transactional` wrapper), publish
inline.
**When to use:** Any state transition that must publish an event **only if the DB write actually
committed** (avoids "phantom CONFIRMED" / "phantom PREPARING" if a downstream check throws after
the status flip).
**Example (copy from `OrderSubmissionService`/`InventoryReservationService`, identical in both):**
```java
private void publishAfterCommit(OrderLinePreparingEvent event) {
  if (!TransactionSynchronizationManager.isSynchronizationActive()) {
    linePreparingPublisher.publish(event);
    return;
  }
  TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          linePreparingPublisher.publish(event);
        }
      });
}
```
**Known residual gap (inherited, not this phase's job to fix):** WR-02 in 15-REVIEW.md documents
that a crash between commit and Kafka send permanently strands the entity in its pre-event state
with no re-emission path. The locked decision explicitly defers "retroactively applying WR-01/
WR-02 fixes to the Phase 15 consumers" — but this phase's OWN new producer/consumer inherits the
same after-commit-publish structural gap unless a lightweight relay is added. Treat as an accepted,
documented tradeoff (same as Phase 15), not a regression — do not scope-creep an outbox into this
phase.

### Pattern 3: Recipe re-resolution reused per line (the phase's core novel logic)
**What:** `InventoryReservationService.computeRequired`/`accumulateRecipe` resolves a **whole
order's** ingredient requirements by iterating every line. Phase 16 needs the **same conversion
logic** (`MenuRecipeCostingPort.findRecipe` → `UnitConverter.convert` → base-unit quantity ×
line quantity) but scoped to **one line only**.
**When to use:** In `InventorySettlementService`, extract (or duplicate, if extraction is judged
too invasive to Phase-15 code) the per-line accumulation logic:
```java
// Adapted from InventoryReservationService.accumulateRecipe (15-03), scoped to ONE line's
// dish + selected toppings instead of an entire order's lines.
private Map<UUID, BigDecimal> computeRequiredForLine(OrderLinePreparingEvent.Line line) {
  Map<UUID, BigDecimal> required = new LinkedHashMap<>();
  accumulateRecipe(required, RecipeTargetType.DISH, line.dishId(), line.quantity());
  for (var topping : line.selectedToppings()) {
    accumulateRecipe(required, RecipeTargetType.TOPPING_OPTION, topping.toppingOptionId(), line.quantity());
  }
  return required;
}
// accumulateRecipe body: identical to InventoryReservationService (same missing-recipe/
// null-ingredient/unconvertible-unit -> zero-contribution, log, never throw semantics — D-06
// established this tolerance and there is no reason to change it for settlement).
```
**Recommendation:** Prefer extracting the shared `accumulateRecipe` logic into a package-private
or public static helper (e.g. a `RecipeResolution` utility in `inventory_context.domain.service`)
that both `InventoryReservationService` and `InventorySettlementService` call, rather than
copy-pasting the method body. This avoids the two services silently drifting apart on recipe
semantics. If the planner judges this too risky to touch Phase-15 code (locked decision says "no
Phase 15 model change" but does not forbid a refactor-extraction of pure logic), duplicating the
method with a code comment cross-referencing the original is an acceptable, lower-risk fallback —
flag the tradeoff explicitly in the plan.

### Pattern 4: Non-negative clamp with anomaly logging (new to this phase, but same guard style as Phase 14/15)
**What:** Phase 15's availability check makes it "should not happen" that `on_hand < reserved` at
settlement time, but the locked decision explicitly requires defending against it anyway.
**Example (new code, following the existing `scale()`/`BigDecimal` conventions in
`InventoryStockService`/`InventoryReservationService`):**
```java
BigDecimal onHand = balance.getQuantityOnHand();
BigDecimal reserved = balance.getReservedQuantity();
BigDecimal newOnHand = onHand.subtract(need);
BigDecimal newReserved = reserved.subtract(need);
if (newOnHand.compareTo(BigDecimal.ZERO) < 0) {
  log.warn(
      "Settlement anomaly: on_hand would go negative for ingredient {} (on_hand={}, need={}) "
          + "— clamping to zero", ingredientId, onHand, need);
  newOnHand = BigDecimal.ZERO;
}
if (newReserved.compareTo(BigDecimal.ZERO) < 0) {
  newReserved = BigDecimal.ZERO; // defensive; reserved should track exactly what was held
}
balance.setQuantityOnHand(scale(newOnHand));
balance.setReservedQuantity(scale(newReserved));
```

### Anti-Patterns to Avoid
- **Reusing `existsByEventIdAndConsumerName` + `saveAndFlush` + `catch (DataIntegrityViolationException) { return; }` unchanged:** this is the exact WR-01 defect. The locked decision requires the fix in the NEW consumer — isolate the ledger insert in a `@Transactional(propagation = Propagation.REQUIRES_NEW)` helper bean method so a duplicate-key collision cannot mark the outer settlement transaction rollback-only. See Code Examples below for the concrete fix shape.
- **Forgetting to record `InventoryStockMovementEntity` for the deduction (WR-02):** the locked decision explicitly requires this; do not treat the deduction as "just decrement the balance columns."
- **Modifying `StockReservationEntity`'s existing columns/embeddable (`ReservationLine`) to add per-line tracking:** explicitly forbidden by D-05 ("No change to the Phase 15 reservation model"). Adding a **new sibling entity/table** for settlement tracking is fine and is the recommended approach — this is not the same as changing the reservation model.
- **Gating the new endpoint under a URL not covered by an existing `SecurityConfig` matcher:** don't hand-roll a new `@PreAuthorize` annotation-based mechanism (the codebase uses zero `@PreAuthorize` anywhere — 100% path-based gating in `SecurityConfig`). Placing the new endpoint under `/admin/orders/**` requires zero `SecurityConfig` changes; placing it anywhere else requires a new `requestMatchers(...).hasAnyRole(...)` line.
- **Using the global default `KafkaTemplate`/`spring.kafka.producer.value-serializer` for the new preparing-event producer:** `application.properties:26` still sets the Jackson-2
  `org.springframework.kafka.support.serializer.JsonSerializer` globally (WR-05, un-fixed, deferred). Any producer that relies on Boot's auto-configured default `KafkaTemplate` for an
  `Instant`-bearing event will fail at runtime. Always build an explicit `ProducerFactory`/`KafkaTemplate` bean pair with `JacksonJsonSerializer`, exactly like `OrderKafkaProducerConfig`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Recipe → ingredient quantity resolution | A new dish/topping-to-ingredient resolver | Reuse (or extract-and-share) `InventoryReservationService`'s `accumulateRecipe`/`UnitConverter.convert` logic | Already handles missing-recipe/null-ingredient/unconvertible-unit tolerance correctly (D-06); duplicating divergent logic risks the settlement quantity silently disagreeing with the reservation quantity |
| Kafka poison-pill / type-confusion defense | Custom deserialization try/catch | `ErrorHandlingDeserializer` wrapping `JacksonJsonDeserializer` with `TRUSTED_PACKAGES` + forced `VALUE_DEFAULT_TYPE` + `USE_TYPE_INFO_HEADERS=false` | Exact pattern already proven in `InventoryKafkaConsumerConfig`; reinventing it risks reintroducing T-15-07/T-15-08 class vulnerabilities |
| Idempotent duplicate-delivery handling | A new dedup mechanism (e.g., Redis SETNX) | The existing JPA unique-constraint + ledger-table pattern (`InventoryProcessedEventEntity`-style), fixed with `REQUIRES_NEW` per WR-01 | Matches the "no new dependencies" constraint and the established pattern; Redis is unused for this purpose anywhere in the codebase |
| Non-negative stock enforcement | A CHECK constraint or trigger at the DB level | Application-level clamp + `PESSIMISTIC_WRITE` lock, exactly as `InventoryStockService.recordMovement` and `InventoryReservationService` already do | Consistent with `ddl-auto=update` (no migration framework); a DB constraint would require an explicit migration path this project doesn't have |
| Audit trail of stock changes | A new "settlement log" table with bespoke schema | `InventoryStockMovementEntity` (Phase 14's existing immutable movement ledger) with a new movement-type value | WR-02 explicitly calls for reusing this exact mechanism; it already supports `referenceType`/`referenceId`/`actorId` fields suited to tagging the settlement source |

**Key insight:** almost nothing in this phase should be built from scratch — the phase is
"instantiate the same three patterns (thin listener, after-commit publish, ledger-guarded
idempotent service) that Phase 15 already validated, pointed at a new event and a new
deduction target." The only genuinely new mechanism is the settlement-progress tracker (see Open
Questions), and even that should be built as a small sibling table using the same JPA/entity
conventions as everything else, not a new technology.

## Common Pitfalls

### Pitfall 1: WR-01 recurrence if the ledger-insert pattern is copy-pasted unchanged
**What goes wrong:** Copying `InventoryReservationService`'s exact
`try { saveAndFlush(ledger) } catch (DataIntegrityViolationException) { return; }` idiom into the
new settlement service reproduces the exact defect 15-REVIEW.md flagged: a genuine concurrent
duplicate marks the JPA transaction rollback-only, so the "clean skip" instead throws
`UnexpectedRollbackException` out to the listener, forcing a retry before the fast pre-check
short-circuits on the second attempt.
**Why it happens:** `JpaTransactionManager` marks a transaction rollback-only as soon as a
`DataIntegrityViolationException` surfaces during flush inside an active `@Transactional` method,
regardless of whether the exception is caught.
**How to avoid:** Isolate the ledger insert into a separate bean method annotated
`@Transactional(propagation = Propagation.REQUIRES_NEW)`, called from the outer settlement method.
A duplicate-key failure in the inner (REQUIRES_NEW) transaction rolls back only that inner
transaction; the outer transaction remains clean and can commit normally on the fast pre-check
path taken by the caller after the inner call signals "already exists."
**Warning signs:** Any test observing `UnexpectedRollbackException` on the first concurrent
duplicate, or logs showing a "skip" message immediately followed by a retry/redelivery of the same
event.

### Pitfall 2: "Last line" detection race / no built-in line-count knowledge
**What goes wrong:** Two lines of the same order settle concurrently in separate transactions;
both read "N-1 of N lines settled" (correct count not yet including the other's in-flight commit)
and neither marks the reservation `SETTLED`, or both read stale state and both attempt the
`SETTLED` transition redundantly (harmless if idempotent, but signals a design gap if it silently
never fires).
**Why it happens:** `StockReservationEntity` has no line/manifest information (confirmed in
source); Inventory has no independent way to know the order's total line count. If detection is
implemented as "count settlement-guard rows for orderId, compare against N" without locking
either the reservation row or the settlement-guard table during the count, two concurrent
settlements race on the same read.
**How to avoid:** Acquire a `PESSIMISTIC_WRITE` lock on the `StockReservationEntity` row (new
repository method, e.g. `lockByOrderId`) at the point of inserting the settlement-guard row and
checking "is this the last line" — the same discipline as the ingredient-balance locks. Compute
the expected total line count from data carried on the event (see Open Questions) rather than
inferring it, so the "am I last" check is deterministic under lock.
**Warning signs:** A reservation that never transitions to `SETTLED` even after all lines are
independently confirmed prepared in Order Context; or duplicate `SETTLED` writes racing (harmless
but a code smell indicating the check-then-act isn't atomic).

### Pitfall 3: Recipe drift between confirm-time and prepare-time
**What goes wrong:** A recipe is edited (ingredient added/removed/quantity changed) between order
confirmation (Phase 15 reservation) and kitchen preparation (Phase 16 settlement). The settlement
deduction no longer matches the original reservation amount for that line, so the sum of
per-line settlements across the whole order may not exactly equal the original aggregate reserved
quantity — potentially leaving a small residual `reserved_quantity` on the balance after the
reservation is marked `SETTLED`, or clamping/negative-adjacent math if the new recipe requires
more than what remains reserved.
**Why it happens:** By design (D-05: "Accepted risk... tolerated and logged") — Inventory
re-resolves the CURRENT recipe at prepare time rather than storing an immutable per-line snapshot
at confirm time.
**How to avoid:** Cannot be "avoided" per the locked decision — must be logged. Recommend logging
at WARN when the per-ingredient decrement differs from what a fresh full-order recomputation would
imply, and ensure the final `reserved_quantity` is force-zeroed (not left as a small dangling
residual) once the reservation is marked `SETTLED` — i.e., when settling the LAST line, clamp
`reserved_quantity` to exactly zero for every ingredient touched by that order's reservation, not
just subtract the freshly-computed amount, so drift cannot leave a permanent phantom reservation.
**Warning signs:** `reserved_quantity` staying non-zero for an ingredient after its reservation is
`SETTLED`; `listLowStock`/`getStock` reads showing unexplained persistently-reserved quantity for
ingredients whose orders have all completed.

### Pitfall 4: Global Jackson-2 producer serializer landmine (WR-05, still present)
**What goes wrong:** `application.properties:26` still sets
`spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer`
(Jackson-2). Any new producer that relies on Boot's auto-configured default `KafkaTemplate`
instead of an explicit bean will throw at runtime serializing the new event's `Instant` field.
**Why it happens:** Pre-existing defect from Phase 15, explicitly deferred (not this phase's job
to fix, per CONTEXT.md's Deferred Ideas), but it is a live landmine for any *new* producer added in
this phase.
**How to avoid:** Always define an explicit `ProducerFactory<String, OrderLinePreparingEvent>` +
`KafkaTemplate` bean pair with `JacksonJsonSerializer`, exactly mirroring
`OrderKafkaProducerConfig`. Never inject/autowire the default `KafkaTemplate` for this event.
**Warning signs:** A serialization exception mentioning `Instant` or "no serializer found" at the
first real publish attempt if a plan step accidentally uses `@Autowired KafkaTemplate<String, Object>` without qualifying the new bean.

### Pitfall 5: Endpoint/role gate mismatch — "staff" is not a distinct Spring Security role
**What goes wrong:** A plan or implementation assumes a dedicated `KITCHEN` role exists.
**Why it happens:** The codebase's roles are `USER`, `ADMIN`, `STAFF` only (confirmed in
`SecurityConfig`); there is no `KITCHEN` role anywhere.
**How to avoid:** Gate the new endpoint with `hasAnyRole("ADMIN", "STAFF")`, consistent with every
other admin-surface endpoint (`/admin/orders/**`, `/admin/inventory/**`, `/admin/tables/**` all use
this same pair). Placing the new route under `/admin/orders/**` (already so-gated) avoids touching
`SecurityConfig` at all — this is the path of least risk.
**Warning signs:** A new distinct role introduced (`KITCHEN`) with no corresponding user-provisioning story; or a 403 in manual testing because the new path isn't covered by any existing `requestMatchers` and falls through to the catch-all `.requestMatchers("/admin/**").hasRole("ADMIN")` (which excludes `STAFF`) or `.anyRequest().authenticated()` (which excludes both, allowing any authenticated `USER`).

## Code Examples

### WR-01 fix: `REQUIRES_NEW` idempotency insert (the concrete pattern the new consumer must apply)
```java
// New helper — isolates the ledger insert so a duplicate-key violation cannot poison the
// outer settlement transaction (fixes the exact defect documented in 15-REVIEW.md WR-01).
@Service
@RequiredArgsConstructor
public class SettlementLedgerGuard {
  private final InventoryProcessedEventRepository processedEventRepository;

  /** @return true if this call recorded a NEW ledger row; false if it was already processed. */
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
      return false; // this inner (REQUIRES_NEW) transaction rolls back cleanly; caller's
                     // outer transaction is unaffected and can proceed to commit normally.
    }
  }
}

// Caller (InventorySettlementService.onLinePreparing, @Transactional):
if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
  return; // fast pre-check, same as Phase 15
}
if (!settlementLedgerGuard.tryRecord(eventId, CONSUMER_NAME)) {
  return; // concurrent duplicate — clean skip, no UnexpectedRollbackException risk
}
// ... proceed with settlement in the OUTER transaction ...
```
**Source of the underlying bug analysis:**
`.planning/phases/15-kafka-event-consumers-for-ordercreated-and-payment-events-wi/15-REVIEW.md`
(WR-01), corroborated by direct reading of
`src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java:83-95`.

### WR-02 fix: recording the audit movement for a settlement deduction
```java
// Mirrors InventoryStockService.recordMovement's movement-recording shape (Phase 14), but the
// "quantity" is the recipe-resolved base quantity, not an operator-entered quantity+unit.
InventoryStockMovementEntity movement = new InventoryStockMovementEntity();
movement.setIngredient(ingredient);
movement.setLocationCode(InventoryStockBalanceEntity.DEFAULT_LOCATION);
movement.setMovementType(InventoryMovementType.CONSUMPTION); // NEW enum value — see Open Questions
movement.setQuantity(need);              // base-unit quantity consumed
movement.setUnit(ingredient.getBaseUnit());
movement.setBaseQuantityDelta(need.negate());
movement.setBaseUnit(ingredient.getBaseUnit());
movement.setResultingBalance(newOnHand);
movement.setReferenceType("ORDER_LINE_SETTLEMENT");
movement.setReferenceId(orderLineId);
movement.setActorId(null); // system-triggered, not an operator action — or carry the staff
                           // actor id through the event if traceability to the staff member matters
movement.setCreatedAt(Instant.now());
movementRepository.save(movement);
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| Fire-and-forget order-triggered stock deduction (Phase 14 era assumption) | Two-phase saga: reserve-at-confirm (Phase 15), settle-at-prepare (Phase 16) | Phase 15 discussion (revised from Phase 14's original plan) | Stock is never decremented speculatively; `reserved` tracks a hold, `on_hand` tracks the real physical deduction only at the real consumption moment |
| Jackson-2 `JsonSerializer`/`JsonDeserializer` for Kafka events | Jackson-3 native `JacksonJsonSerializer`/`JacksonJsonDeserializer` (`tools.jackson`) | Phase 15 (15-01/15-04, forced by the Boot 4/Jackson 3 classpath lacking `jackson-datatype-jsr310`) | Every new Kafka event/consumer in this codebase (including this phase's) must use the Jackson-3 family, not the Boot-default Jackson-2 classes |

**Deprecated/outdated:** None specific to this phase beyond the still-open WR-03/04/05 items,
explicitly deferred per CONTEXT.md.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `RequestMapping`/`requestMatchers("/admin/orders/**")` is the lowest-risk placement for the new staff endpoint, requiring zero `SecurityConfig` changes | Architecture Patterns / Pitfall 5 | If the planner instead nests it under `/orders/**` (customer-facing, `USER` role included), any authenticated customer could trigger kitchen actions — a real authorization bug, not just a style choice |
| A2 | The best design for "last line settled" is to carry a line manifest / total-line-count on the new event, rather than Inventory querying Order Context synchronously | Open Questions | If Inventory instead makes a synchronous call back to Order Context, this reintroduces the kind of tight coupling the event-driven saga was designed to avoid; if the planner instead invents a wholly different mechanism, verify it does not require modifying `StockReservationEntity` itself (locked D-05) |
| A3 | `InventoryMovementType` should gain a new enum value (e.g. `CONSUMPTION`) for the settlement deduction, distinct from `WASTE`/`ADJUSTMENT_OUT` | Code Examples | This is explicitly Claude's Discretion per CONTEXT.md ("Movement type value used for the deduction") — if the planner reuses an existing value like `ADJUSTMENT_OUT`, downstream reporting/audit queries that filter by movement type for "waste vs. real consumption" would misclassify normal kitchen consumption as a manual adjustment |
| A4 | A brand-new `ReservationSettlementEntity`/table (not modifying `StockReservationEntity`) is the correct way to satisfy both the per-line double-settlement guard (D-03) and the "last line" detection (D-05) | Architecture Patterns, Don't Hand-Roll | If the planner instead adds columns to `StockReservationEntity` or its embeddable `ReservationLine`, that directly contradicts D-05 ("No change to the Phase 15 reservation model") |

**All four items above are flagged because they resolve gaps in the locked CONTEXT.md rather than
restating it — they are reasoned recommendations grounded in the actual entity/security code read
during this research, not verified against an external authority (there is no "official docs" for
an internal DDD saga). Treat them as strong defaults for the planner, not immutable facts.**

## Open Questions

1. **How does Inventory know an order's total line count / which line-ids exist, to detect "last line settled"?**
   - What we know: `StockReservationEntity` has no line-count or line-id manifest (only aggregate
     per-ingredient `reservedQuantity`). Order Context's `OrderEntity.getLines()` has the full list
     but Inventory Context has no direct read access to it (bounded-context isolation — Inventory
     only knows what arrives via Kafka events).
   - What's unclear: Whether the plan should have the new `OrderLinePreparingEvent` carry a
     `totalLines` (or full `orderLineIds` set) field computed by Order Context at publish time, or
     have Inventory lazily learn the total the first time any line is settled and simply trust that
     value for the life of the order (acceptable given orders are not edited after confirmation in
     this codebase's current scope).
   - Recommendation: Carry `totalLines` (an `int`, computed as `order.getLines().size()` at event
     construction — cheap, already-loaded data) on every `OrderLinePreparingEvent`. Inventory's
     settlement service locks the reservation row, counts settlement-guard rows for that order,
     and compares against `totalLines` from the just-processed event (or, more robustly, the max
     `totalLines` value ever seen for that order, in case of a partial-order edge case). This keeps
     Inventory fully event-driven with no new synchronous cross-context call.

2. **Should the recipe-resolution helper (`accumulateRecipe`-equivalent) be extracted into a shared utility, or duplicated?**
   - What we know: `InventoryReservationService.accumulateRecipe` is `private`; extracting it
     requires either making it a static utility, a package-private method on a new shared class, or
     duplicating the ~25-line method into the new service.
   - What's unclear: Whether touching `InventoryReservationService` (even a non-behavioral
     extraction) is within the spirit of "no Phase 15 model change" (that phrase specifically refers
     to the *entity model*, not the service code, but the planner should make an explicit call).
   - Recommendation: Extract into a new `RecipeResolver` domain service (pure function, no
     repository side effects beyond the same `MenuRecipeCostingPort`/`IngredientRepository` reads),
     used by both `InventoryReservationService` (refactored to call it) and
     `InventorySettlementService` (new). This is lower-risk than duplication because recipe-scaling
     bugs would otherwise silently diverge between confirm-time and prepare-time math. If the
     planner prefers a purely additive/non-invasive approach for this phase, duplicating the method
     into `InventorySettlementService` with a code comment cross-referencing the original is an
     acceptable, explicitly-flagged fallback.

3. **Exact new event/topic/group-id names (Claude's Discretion per CONTEXT.md) — no strong technical constraint found, but consistency matters.**
   - What we know: Existing topic naming convention is `<context>.<noun>[.result-noun]`
     (`orders.created`, `inventory.order-stock-results`), and DLTs are `<topic>.DLT`. Consumer
     `group-id` convention is `<context>-<event-noun>` (`inventory-order-created`).
   - What's unclear: Nothing blocking — purely a naming choice.
   - Recommendation: `orders.line-preparing` (topic), `OrderLinePreparingEvent` (Java type / event
     name), `inventory-order-line-preparing` (consumer group-id), `orders.line-preparing.DLT` (DLT
     topic) — consistent with existing naming.

## Environment Availability

Skipped — this phase has no external tool/service dependencies beyond what Phase 15 already
depends on (Kafka broker, already verified reachable/optional via the existing `NewTopic`-bean +
"inert if broker unreachable" pattern; the project's tests run with
`spring.kafka.listener.auto-startup=false` and broker-free direct-instantiation config tests, so no
live broker is required to develop or test this phase).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Mockito (plain, no Spring context for unit tests); `@SpringBootTest`-style integration tests exist elsewhere in the suite for broader flows |
| Config file | `pom.xml` (Boot-managed test starter versions); no separate test-framework config file |
| Quick run command | `./mvnw -q -Dtest=InventorySettlementServiceTest test` (mirrors `InventoryReservationServiceTest` invocation) |
| Full suite command | `./mvnw -q test` (138/138 green as of Phase 15 completion — baseline to preserve) |

### Phase Requirements -> Test Map

No formal `REQUIREMENTS.md` requirement IDs exist for Phase 16 (it postdates the v1
`REQUIREMENTS.md` snapshot — confirmed by grep; the file's "Out of Scope" section explicitly names
"Real stock deduction from orders" as deferred past v1). The table below maps CONTEXT.md's locked
decisions (D-01..D-06) to concrete tests instead, since these are this phase's de facto acceptance
criteria.

| Decision | Behavior | Test Type | Automated Command | File Exists? |
|----------|----------|-----------|-------------------|-------------|
| D-01/D-06 | Line prepare transitions order CONFIRMED->PREPARING on first line; stays PREPARING on subsequent lines | unit | `./mvnw -q -Dtest=OrderLinePreparingServiceTest test` | ❌ Wave 0 |
| D-02 | Endpoint rejects when order not in {CONFIRMED, PREPARING} or line already PREPARING | unit | `./mvnw -q -Dtest=OrderLinePreparingServiceTest test` | ❌ Wave 0 |
| D-05 | Per-line recipe re-resolution decrements on_hand+reserved by the correct converted amount; reservation SETTLED only on last line | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` | ❌ Wave 0 |
| D-03 (idempotent replay) | Replayed event / already-settled line is a no-op with zero side effects | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` | ❌ Wave 0 |
| D-03 (clamp >= 0) | `on_hand < reserved` at settle time clamps to zero and logs, does not go negative | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` | ❌ Wave 0 |
| D-03 (missing reservation -> DLT) | Missing `StockReservationEntity` for the order causes the handler to throw (routed to DLT by the error handler), not silently return | unit + config test | `./mvnw -q -Dtest=InventorySettlementServiceTest,InventoryLinePreparingConsumerConfigTest test` | ❌ Wave 0 |
| D-04 (WR-01 fix) | A concurrent duplicate ledger insert does not throw `UnexpectedRollbackException` from the outer transaction | unit (requires a real `@Transactional` boundary — consider a slice/integration test, plain Mockito cannot fully prove REQUIRES_NEW semantics) | `./mvnw -q -Dtest=InventorySettlementIntegrationTest test` | ❌ Wave 0 |
| D-04 (WR-02 fix) | Settlement records an `InventoryStockMovementEntity` row with the correct signed delta and reference | unit | `./mvnw -q -Dtest=InventorySettlementServiceTest test` | ❌ Wave 0 |
| Kafka wiring | New consumer config disables auto-commit, wraps `JacksonJsonDeserializer`, uses `AckMode.RECORD`, marks `DeserializationException` not-retryable | config unit test | `./mvnw -q -Dtest=InventoryLinePreparingConsumerConfigTest test` | ❌ Wave 0 |
| Event serde | `OrderLinePreparingEvent` round-trips through the real Jackson-3 serializer/deserializer (Instant/BigDecimal/nested records) | unit | `./mvnw -q -Dtest=EventSerdeRoundTripTest test` (extend existing file with a new test method) | ✅ file exists, add method |

### Sampling Rate
- **Per task commit:** run the specific new test class(es) for that task (`-Dtest=ClassName`).
- **Per wave merge:** `./mvnw -q test` (full suite) — must stay green (baseline 138/138 from Phase 15; expect growth as new tests are added).
- **Phase gate:** Full suite green before `/gsd:verify-work`.

### Wave 0 Gaps
- [ ] `OrderLinePreparingServiceTest` — covers D-01/D-02/D-06 (line/order status transitions, guards)
- [ ] `InventorySettlementServiceTest` — covers D-03/D-05 (settlement math, clamp, idempotency, DLT-triggering throw, last-line detection)
- [ ] `InventoryLinePreparingConsumerConfigTest` — broker-free wiring test mirroring `InventoryKafkaConsumerConfigTest`
- [ ] `InventorySettlementIntegrationTest` (or extend an existing `@SpringBootTest`-style test) — the one place plain Mockito cannot prove the WR-01 `REQUIRES_NEW` fix works: needs a real `PlatformTransactionManager` to observe that an inner-transaction duplicate-key rollback does not mark the outer transaction rollback-only
- [ ] Extend `EventSerdeRoundTripTest` with an `OrderLinePreparingEvent` round-trip case (same pattern already present for `OrderCreatedEvent`/`OrderStockResultEvent` — closes the exact gap Phase 15's IN-04 finding flagged: assert the after-commit contract, not just inline synchronous publish, if time permits)
- Framework install: none — all test dependencies already present.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V2 Authentication | No (new) | Already enforced globally via `JwtAuthenticationFilter` (existing, unchanged) |
| V3 Session Management | No | Stateless JWT, `SessionCreationPolicy.STATELESS` (existing, unchanged) |
| V4 Access Control | Yes | Path-based `requestMatchers(...).hasAnyRole("ADMIN","STAFF")` in `SecurityConfig` — reuse the existing `/admin/orders/**` matcher rather than adding a new rule (see Pitfall 5) |
| V5 Input Validation | Yes | Validate `orderId`/`lineId` path variables resolve to an existing order/line before mutating state; validate order/line status guards (D-02) reject invalid-state transitions with a domain exception + appropriate HTTP status, mirroring `OrderDomainException`/`InventoryDomainException` conventions |
| V6 Cryptography | No | Not applicable — no new secrets/crypto in this phase |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|----------------------|
| Kafka poison-pill payload (malformed JSON, wrong type) blocking a partition | Denial of Service | `ErrorHandlingDeserializer` + `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`, `DeserializationException` marked not-retryable — identical to `InventoryKafkaConsumerConfig` |
| Malicious/forged `__TypeId__` Kafka header driving arbitrary class deserialization | Tampering / Elevation of Privilege | `USE_TYPE_INFO_HEADERS=false` + forced `VALUE_DEFAULT_TYPE` + `TRUSTED_PACKAGES` allow-list — identical to Phase 15's T-15-07 mitigation |
| Replayed/duplicate Kafka delivery causing double stock deduction | Repudiation / Tampering (data integrity) | Processed-events ledger (unique `event_id`+`consumer_name`) + REQUIRES_NEW insert (WR-01 fix) + a second per-(orderId, orderLineId) settlement-guard as defense-in-depth (D-03) |
| A non-kitchen-staff user (e.g. plain `USER` role) triggering a kitchen action | Elevation of Privilege | Path-based role gate `hasAnyRole("ADMIN","STAFF")`, verified placement under an already-correctly-gated path prefix (Pitfall 5) |
| Race between two concurrent "mark line preparing" calls for the same line | Tampering (double-transition) | Status guard (`line.status == PENDING` required to transition) inside the same `@Transactional` boundary that performs the update; rely on row-level locking implicit in a normal JPA managed-entity update within one transaction (no explicit pessimistic lock needed here since `OrderLineEntity` isn't contended the way `InventoryStockBalanceEntity` is — but the planner should verify no read-then-write race exists across two separate transactions for the exact same line) |

## Sources

### Primary (HIGH confidence — direct source reading in this repository)
- `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationService.java` — recipe-resolution path, lock ordering, idempotency pattern, after-commit publish
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/StockReservationEntity.java` — reservation shape (no line-count/manifest)
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/entity/InventoryStockBalanceEntity.java` — on_hand/reserved columns
- `src/main/java/com/example/feat1/DDD/inventory_context/application/InventoryStockService.java` + `InventoryStockMovementEntity.java` — Phase 14 movement/audit mechanism
- `src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java`, `OrderEntity.java`, `OrderLineEntity.java` — status model to extend
- `src/main/java/com/example/feat1/DDD/order_context/application/OrderConfirmationService.java`, `OrderSubmissionService.java` — transition + after-commit publish patterns to mirror
- `src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java`, `InventoryKafkaTopicConfig.java`, `order_context/infrastructure/config/OrderKafkaProducerConfig.java` — Kafka wiring to mirror
- `src/main/java/com/example/feat1/DDD/auth/infrastructure/security/SecurityConfig.java` — role-gating mechanism (100% path-based, zero `@PreAuthorize` usage anywhere in the codebase)
- `src/test/java/com/example/feat1/DDD/inventory_context/application/InventoryReservationServiceTest.java`, `infrastructure/config/InventoryKafkaConsumerConfigTest.java`, `order_context/EventSerdeRoundTripTest.java` — test conventions to mirror
- `pom.xml` + `./mvnw help:evaluate -Dexpression=spring-kafka.version` (`[VERIFIED: mvn help:evaluate]`, returned `4.0.5`) and parent version `4.0.6` in `pom.xml`
- `src/main/resources/application.properties` — confirms WR-05 (global Jackson-2 producer serializer) is still present/un-fixed
- `.planning/phases/15-.../15-REVIEW.md` — WR-01/WR-02 defect analysis and fix recommendations (directly actioned in this research)
- `.planning/phases/15-.../15-03-SUMMARY.md`, `15-04-SUMMARY.md` — Phase 15 implementation notes on the recipe-resolution and Kafka-wiring decisions
- `.planning/phases/16-.../16-CONTEXT.md`, `16-DISCUSSION-LOG.md` — locked decisions and discretion areas for this phase
- `.planning/REQUIREMENTS.md`, `.planning/STATE.md`, `.planning/config.json` — confirmed no formal REQ IDs exist yet for Phase 16, no `nyquist_validation`/`security_enforcement` overrides set (both default-enabled)

### Secondary / Tertiary
None used — this phase's research was fully answerable from the existing, very recently-reviewed
Phase 15 codebase and its code-review report; no external library/API research was needed since
"no new dependencies" is a locked constraint and every mechanical pattern already exists in-repo.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every library/version confirmed directly from `pom.xml`/`mvnw help:evaluate`, no external dependency added
- Architecture: HIGH for reused patterns (thin listener, after-commit publish, Kafka wiring, ledger idempotency — all directly read from working, tested Phase 15 code); MEDIUM/LOW for the novel "last line" settlement-tracking design (flagged explicitly in Open Questions/Assumptions as needing planner/user confirmation)
- Pitfalls: HIGH — WR-01/WR-02/WR-05 pitfalls are drawn directly from the Phase 15 code-review report (an authoritative source for this codebase) and corroborated by reading the actual flagged code; the recipe-drift and last-line-race pitfalls are reasoned from direct entity inspection, not speculation

**Research date:** 2026-07-07
**Valid until:** 30 days (internal codebase research tied to the current state of Phase 15's code; re-verify if Phase 15 code changes before Phase 16 planning begins, e.g. if WR-01/WR-02 get retrofitted in the interim)
