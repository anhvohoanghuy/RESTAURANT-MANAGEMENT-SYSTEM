# Phase 15: kafka-event-consumers (order-confirmation saga) - Research

**Researched:** 2026-07-07
**Domain:** Spring Kafka consumers, event-driven saga, stock reservation, idempotency
**Confidence:** HIGH (stack verified against local m2 + spring-kafka 4.0.5 jar and official docs; a few Jackson-generation nuances flagged as assumptions)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Order is created in status **`PENDING_CONFIRMATION`** (new `OrderStatus` value; today the enum has only `SUBMITTED`). Not final until saga completes. Existing after-commit `OrderCreated` producer starts the saga.
- **D-08:** `POST /orders` returns the order in `PENDING_CONFIRMATION` **synchronously**; `CONFIRMED`/`REJECTED` reached **asynchronously**, read via `GET /orders/{id}`.
- **D-02:** Stock is **NEVER negative**. Inventory checks `available = on_hand − reserved ≥ required` atomically. Sufficient → create reservation (increment `reserved`). Insufficient → reject. No allow-negative path.
- **D-09:** Reservation model — Inventory gains a **`reserved` quantity** concept (column on the stock balance row and/or a per-order reservation record keyed by `orderId`). `available = on_hand − reserved`. Settled in Phase 16. This phase only creates/holds reservations.
- **D-06:** Required quantity per order = for each `OrderCreatedEvent` line, resolve dish **and** selected topping options to recipe ingredient lines (Phase 01/13 links) × line `quantity`, unit-converted via shared `UnitConverter` (Phase 14). A line whose dish/topping has no recipe/ingredient link contributes **zero** requirement (logged), does not block confirmation.
- **D-10:** After commit, Inventory publishes a **result event** — `OrderStockConfirmed` or `OrderStockRejected` (with shortfall detail) — on a new topic (e.g. `inventory.order-stock-results`). Order Context **consumes** it, transitions `PENDING_CONFIRMATION` → `CONFIRMED`/`REJECTED`.
- **D-11:** Insufficient stock → order transitions to **`REJECTED`** (terminal), carrying the reason (which ingredient(s) short). No cart-restore, no staff-review path.
- **D-03:** A **processed-events ledger** keyed by `eventId` guards **both** consumers against double-processing under at-least-once. Reservation creation additionally keyed by `orderId` (unique), so a replayed `OrderCreated` cannot double-reserve.
- **D-04:** Spring Kafka **`DefaultErrorHandler` with fixed retries + a Dead Letter Topic** (`<topic>.DLT`) on each consumer.
- **D-05:** Add consumer config mirroring existing per-context producer config style: `@EnableKafka`, `ConsumerFactory`, `ConcurrentKafkaListenerContainerFactory`, consumer `group-id`, JSON deserializer with trusted-package/type-mapping. Inventory gains an `OrderCreated` **consumer** + a result-event **producer**; Order gains a result-event **consumer**.
- **D-07:** The `payments.events` consumer is **out of scope**.

### Claude's Discretion
- Exact result-event topic name and schema, reservation storage shape (balance column vs. dedicated reservation table), consumer `group-id` values, retry counts/backoff, DLT topic naming, processed-events ledger granularity.

### Deferred Ideas (OUT OF SCOPE)
- **Phase 16 — Kitchen "đang làm" (preparing):** convert reservation → actual `on_hand` deduction (`reserved` → `on_hand`). The real deduction moment.
- Reservation release on refund / order cancel (no cancel flow exists).
- `payments.events` consumer.
- Multi-location stock, supplier reorder automation, consumer scaling/concurrency tuning.
</user_constraints>

<phase_requirements>
## Phase Requirements

Phase 15 is **not** mapped to any `REQUIREMENTS.md` REQ-IDs (INV-012..021 belong to Phase 14; "Kafka consumers" appears in the v1 Out-of-Scope table as deferred). This phase is driven entirely by the locked `D-0x` decisions above. The planner should trace tasks to `D-0x`, not to REQ-IDs, and (optionally) add a new requirement block to `REQUIREMENTS.md` describing the order-confirmation saga.
</phase_requirements>

## Summary

The project is on **Spring Boot 4.0.6 / Spring Kafka 4.0.5 / Java 17 / Maven** — **not** Spring Boot 3 as the task brief stated (verified: `spring-boot-starter-parent` 4.0.6 in `pom.xml`, `spring-kafka-4.0.5.jar` in local m2). This matters: Spring Boot 4 ships **Jackson 3** (`tools.jackson` 3.1.4) as its default JSON stack, and Spring Kafka 4.0 has **deprecated `JsonSerializer`/`JsonDeserializer` (Jackson 2) for removal**, introducing `JacksonJsonSerializer`/`JacksonJsonDeserializer` (Jackson 3) as replacements. However, Jackson 2 (`com.fasterxml.jackson` 2.21.4) is still on the classpath and the existing producers (`OrderKafkaProducerConfig`, `PaymentKafkaProducerConfig`, `TableKafkaProducerConfig`) all use the deprecated Jackson-2 `JsonSerializer` — which still compiles and runs in 4.0.6. **Because those producers are mocked in every test (`@MockitoBean OrderEventPublisher`), the Jackson-2 serializer has never actually executed at runtime in CI** — a latent risk worth a smoke check.

The system today is **produce-only (zero consumers)**. This phase adds the first consumers and wires a cross-context saga: Order creates in `PENDING_CONFIRMATION` → Inventory consumes `OrderCreated`, resolves recipes to ingredient requirements (reusing the exact `MenuRecipeCostingPort.findRecipe` + `UnitConverter` path already used by costing), checks `available = on_hand − reserved`, reserves or rejects, publishes a result event → Order consumes it and transitions to `CONFIRMED`/`REJECTED`. Both consumers are idempotent via a `processed_events` ledger keyed by `eventId`; reservation is additionally unique by `orderId`. Errors go through `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` (`<topic>.DLT`), with `ErrorHandlingDeserializer` wrapping the JSON deserializer to survive poison pills.

**Primary recommendation:** Mirror the existing Jackson-2 producer style for all new serdes (use `ErrorHandlingDeserializer` → `JsonDeserializer` with `TRUSTED_PACKAGES`/`VALUE_DEFAULT_TYPE`), keep every `@KafkaListener` a thin adapter that delegates to a `@Transactional` application service, do the ledger-insert + reservation + balance update in **one** DB transaction, publish result events **after commit**, and test the service methods directly under H2 with `spring.kafka.listener.auto-startup=false` — no live broker, no EmbeddedKafka required.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Set order initial status `PENDING_CONFIRMATION` + publish `OrderCreated` | Order Context / application (`OrderSubmissionService`) | — | Order owns its lifecycle; publish already lives here (after-commit) |
| Consume `OrderCreated`, resolve requirements, reserve stock | Inventory Context / infrastructure (listener) → application service | Menu Context (recipe port) | Inventory is the stock authority (D-10 specifics) |
| Resolve dish/topping → ingredient lines | Menu Context (`MenuRecipeCostingPort`) | Inventory (`UnitConverter`) | Reuse the exact costing resolution path; do not duplicate |
| Publish `OrderStockConfirmed`/`Rejected` result event | Inventory Context / infrastructure (producer) | — | Inventory emits the saga result |
| Consume result event, transition order status | Order Context / infrastructure (listener) → application service | — | Order owns status transitions; reacts to Inventory's verdict |
| Idempotency ledger | Shared pattern, one table per bounded context or one shared table | DB unique constraint | At-least-once redelivery guard |
| Error handling / DLT | Infrastructure config (both contexts) | Kafka broker (DLT topics) | Cross-cutting consumer concern |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.springframework.kafka:spring-kafka` | 4.0.5 (managed by Boot 4.0.6 BOM) | `@KafkaListener`, `ConsumerFactory`, container factory, `DefaultErrorHandler`, `DeadLetterPublishingRecoverer`, `JsonDeserializer`, `ErrorHandlingDeserializer` | Already the project's Kafka library; producers use it today |
| Spring Data JPA (Boot 4.0.6) | managed | `processed_events` ledger, reservation table, pessimistic lock (`@Lock`) | Already used everywhere in the codebase |
| `UnitConverter` (in-repo) | Phase 14 | recipe-unit → base-unit conversion for required qty | Shared domain service, reuse verbatim |
| `MenuRecipeCostingPort` (in-repo) | Phase 13 | `findRecipe(RecipeTargetType, targetId)` → ingredient lines | Reuse the exact costing resolution path |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.springframework.kafka:spring-kafka-test` | 4.0.5 | `EmbeddedKafka` broker for wiring tests | OPTIONAL — only if you want one end-to-end listener smoke test. Not required; not currently a dependency. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Deprecated `JsonSerializer`/`JsonDeserializer` (Jackson 2) | `JacksonJsonSerializer`/`JacksonJsonDeserializer` (Jackson 3) | Jackson-3 classes are the non-deprecated future, but the **existing producers emit Jackson-2 `__TypeId__` headers**. Mixing a Jackson-2 producer with a Jackson-3 consumer risks type-header/behavior mismatch. Staying on Jackson-2 keeps producer↔consumer symmetric and honors D-05 ("mirror existing"). Migrating **all** Kafka serdes to Jackson 3 is a clean follow-up phase, not this one. |
| `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` (blocking retry, `.DLT`) | `@RetryableTopic` (non-blocking retry, `-retry`/`-dlt` topics) | D-04 explicitly chose the blocking `DefaultErrorHandler` + `.DLT`. `@RetryableTopic` creates extra topics and reorders delivery — out of scope. |
| Pessimistic lock (`SELECT … FOR UPDATE`) on balance row | Optimistic `@Version` + retry | Pessimistic is simpler to reason about for a strict non-negative invariant under concurrency; optimistic needs a retry loop. Either is acceptable (Claude's discretion). Recommend pessimistic for correctness clarity. |
| Dedicated `stock_reservations` table keyed by `orderId` **plus** `reserved` column on balance | `reserved` column only | Column-only cannot represent per-order reservations for Phase-16 settlement or the unique-by-`orderId` idempotency guard. Recommend **both**: a `reserved` running total on the balance row (fast `available` computation) and a `stock_reservations` row per order (settlement + idempotency). |

**Installation:** No new dependencies required for the core path (`spring-kafka` already present). `spring-kafka-test` is optional and only if you add an EmbeddedKafka smoke test:
```xml
<!-- OPTIONAL, test scope only -->
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka-test</artifactId>
  <scope>test</scope>
</dependency>
```

**Version verification (performed this session):**
- `spring-boot-starter-parent` = **4.0.6** (`pom.xml` line: `<version>4.0.6</version>`). [VERIFIED: pom.xml]
- `spring-kafka` resolved to **4.0.5** (`~/.m2/repository/org/springframework/kafka/spring-kafka/4.0.5/`). [VERIFIED: local m2]
- Jackson 2 = **2.21.4**, Jackson 3 = **3.1.4** both present in m2. [VERIFIED: local m2]
- `spring-kafka-4.0.5.jar` contains **both** deprecated `JsonSerializer`/`JsonDeserializer` (Jackson-2 `com.fasterxml.jackson.databind.ObjectMapper`, confirmed via `javap`) **and** new `JacksonJsonSerializer`/`JacksonJsonDeserializer`. [VERIFIED: jar contents + javap]
- `JsonDeserializer` deprecation note: *"since 4.0 in favor of JacksonJsonDeserializer for Jackson 3 … subject to removal."* [CITED: docs.spring.io/spring-kafka/api/.../JsonDeserializer.html]

## Package Legitimacy Audit

No new **external** packages are introduced. All Kafka/JPA classes come from `spring-kafka` (already a direct dependency) and the Spring Boot 4.0.6 BOM. The only optional addition is `spring-kafka-test`, published by the same `org.springframework.kafka` group as the existing dependency.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `org.springframework.kafka:spring-kafka` | Maven Central | 8+ yrs | very high | github.com/spring-projects/spring-kafka | n/a (already a dependency) | Approved — in use |
| `org.springframework.kafka:spring-kafka-test` (optional) | Maven Central | 8+ yrs | very high | github.com/spring-projects/spring-kafka | n/a (same group as core) | Approved if EmbeddedKafka test added |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

*slopcheck targets npm/PyPI hallucination vectors and is not applicable to first-party Spring Maven artifacts from an already-present group. No new third-party Maven coordinates are proposed.*

## Architecture Patterns

### System Architecture Diagram

```
POST /orders (sync)
      |
      v
[OrderSubmissionService @Transactional]
  - save Order (status = PENDING_CONFIRMATION)   <-- D-01 change (was SUBMITTED)
  - clear cart
  - afterCommit -> publish OrderCreated  ------------------+
      |                                                    |
      v (HTTP 200: order in PENDING_CONFIRMATION)          |  topic: orders.created
  client polls GET /orders/{id}                            |
                                                           v
                                        [Inventory @KafkaListener: OrderCreated]
                                         group-id: inventory-order-created
                                         value: ErrorHandlingDeserializer->JsonDeserializer
                                                           |
                                                           v
                              [InventoryReservationService @Transactional]
                                 1. ledger insert(eventId)  --unique--> dup? skip (idempotent)
                                 2. for each line:
                                      resolve DISH recipe  (MenuRecipeCostingPort.findRecipe)
                                      resolve each TOPPING_OPTION recipe
                                      qty * line.quantity, UnitConverter -> base unit
                                      accumulate required[ingredientId]   (no recipe => 0, log)
                                 3. lock balance rows (PESSIMISTIC_WRITE)
                                    available = on_hand - reserved
                                    all(required <= available)?
                                       yes -> reserved += required ; create stock_reservation(orderId)
                                       no  -> collect shortfall
                                 4. afterCommit -> publish result event ----+
                                                           |                |
                    (deserialize/handler failure)          |                | topic: inventory.order-stock-results
                    DefaultErrorHandler (FixedBackOff)      |                v
                    -> retries -> DeadLetterPublishingRecoverer  [Order @KafkaListener: OrderStockResult]
                    -> orders.created.DLT                                    group-id: order-stock-result
                                                                            |
                                                                            v
                                              [OrderConfirmationService @Transactional]
                                                 1. ledger insert(eventId) --unique--> dup? skip
                                                 2. load order; guard: status == PENDING_CONFIRMATION
                                                 3. CONFIRMED (reserved held) | REJECTED (+reason)
                                                 (failure) -> inventory.order-stock-results.DLT
```

### Component Responsibilities
| File (new/modified) | Context | Responsibility |
|---------------------|---------|----------------|
| `order_context/domain/model/OrderStatus.java` (mod) | Order | add `PENDING_CONFIRMATION`, `CONFIRMED`, `REJECTED` |
| `order_context/application/OrderSubmissionService.java` (mod) | Order | set initial status `PENDING_CONFIRMATION` (line 58 change) |
| `order_context/infrastructure/config/OrderKafkaConsumerConfig.java` (new) | Order | `@EnableKafka`, `ConsumerFactory<String,OrderStockResultEvent>`, container factory, error handler + DLT template |
| `order_context/infrastructure/adapter/OrderStockResultListener.java` (new) | Order | thin `@KafkaListener` → delegates to confirmation service |
| `order_context/application/OrderConfirmationService.java` (new) | Order | `@Transactional` ledger + status transition |
| `inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java` + `...ProducerConfig` (new) | Inventory | consumer for `OrderCreated`, producer for result event |
| `inventory_context/infrastructure/adapter/OrderCreatedListener.java` (new) | Inventory | thin `@KafkaListener` → delegates to reservation service |
| `inventory_context/application/InventoryReservationService.java` (new) | Inventory | resolve requirements, reserve, publish result after commit |
| `inventory_context/domain/port/MenuRecipeCostingPort` (reuse) | Inventory | `findRecipe(DISH/TOPPING_OPTION, id)` |
| `*/infrastructure/entity/ProcessedEventEntity.java` + repo (new) | shared/both | idempotency ledger, unique `event_id` |
| `inventory_context/infrastructure/entity/StockReservationEntity.java` + repo (new) | Inventory | per-order reservation, unique `order_id` |
| `InventoryStockBalanceEntity.java` (mod) | Inventory | add `reserved` column (default 0) |

### Recommended Project Structure
Follow the established DDD layout exactly (`domain` / `application` / `infrastructure(adapter|config|presentation)`). Consumer configs live in each context's `infrastructure/config` (mirror `OrderKafkaProducerConfig`). Listeners live in `infrastructure/adapter` (mirror `KafkaOrderEventPublisher`). Business logic lives in `application` services — listeners must not contain reservation/transition logic.

### Pattern 1: Consumer config mirroring the existing producer config
**What:** One `@Configuration` per context defining a typed `ConsumerFactory` + `ConcurrentKafkaListenerContainerFactory`, with `ErrorHandlingDeserializer` wrapping `JsonDeserializer`, trusted packages, and a default value type.
**When to use:** Both new consumers.
```java
// Source pattern: docs.spring.io/spring-kafka/reference/kafka/serdes.html + existing OrderKafkaProducerConfig
@Configuration
@EnableKafka
public class InventoryKafkaConsumerConfig {

  @Bean
  ConsumerFactory<String, OrderCreatedEvent> orderCreatedConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "inventory-order-created");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // container manages acks
    // Poison-pill safety: wrap the JSON deserializer so a bad payload is recoverable, not fatal.
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
    props.put(JsonDeserializer.TRUSTED_PACKAGES,
        "com.example.feat1.DDD.order_context.application.event");
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderCreatedEvent.class.getName());
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false); // ignore producer __TypeId__, force local type
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent>
      orderCreatedKafkaListenerContainerFactory(
          ConsumerFactory<String, OrderCreatedEvent> cf,
          DefaultErrorHandler orderCreatedErrorHandler) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent>();
    factory.setConsumerFactory(cf);
    factory.setCommonErrorHandler(orderCreatedErrorHandler);
    // AckMode.RECORD (or BATCH) so offset commits after a successful, committed handler run.
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    return factory;
  }
}
```
**Note on `USE_TYPE_INFO_HEADERS=false` + `VALUE_DEFAULT_TYPE`:** the producer's Jackson-2 `JsonSerializer` writes a `__TypeId__` header carrying the fully-qualified producing-package class name. Because `OrderCreatedEvent` lives in the **order_context** package but is deserialized in the **inventory_context**, relying on the header type would require the class to exist at that exact FQN (it does, same module) — but forcing the local default type + ignoring headers is the most robust and avoids `TRUSTED_PACKAGES` surprises. If you keep headers on, you MUST add the producing package to `TRUSTED_PACKAGES`. Both events here are shared classes in the same JAR, so either works; **recommend ignore-headers + default-type** for clarity. [CITED: docs.spring.io/spring-kafka JsonDeserializer]

### Pattern 2: DefaultErrorHandler + DLT beans (D-04)
```java
// Source: docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html
@Bean
KafkaTemplate<String, Object> dltKafkaTemplate(
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
  Map<String, Object> props = new HashMap<>();
  props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
  props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
  props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
  return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
}

@Bean
DefaultErrorHandler orderCreatedErrorHandler(KafkaTemplate<String, Object> dltKafkaTemplate) {
  // publishes failed record to "<originalTopic>.DLT" on the same partition
  var recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate);
  // 3 retries, 1s apart, then recover to DLT
  var backOff = new FixedBackOff(1000L, 3L);
  var handler = new DefaultErrorHandler(recoverer, backOff);
  // Deserialization exceptions are NOT retryable -> straight to DLT (poison-pill safety)
  handler.addNotRetryableExceptions(
      org.springframework.kafka.support.serializer.DeserializationException.class);
  return handler;
}
```
`DeadLetterPublishingRecoverer`'s default destination resolver sends to `<originalTopicName>.DLT`, same partition. [VERIFIED: docs.spring.io/spring-kafka DeadLetterPublishingRecoverer + web search of official docs] The `-dlt`/`-retry` suffix convention belongs to `@RetryableTopic`, **not** to this path — D-04's `.DLT` is the correct default here.

### Pattern 3: Idempotent, atomic handler (ledger + reservation in one TX)
```java
@Service
@RequiredArgsConstructor
public class InventoryReservationService {
  private final ProcessedEventRepository ledger;
  private final StockReservationRepository reservations;
  private final InventoryStockBalanceRepository balances;
  private final MenuRecipeCostingPort recipePort;
  private final IngredientRepository ingredientRepository;
  private final InventoryStockResultPublisher resultPublisher; // publishes AFTER commit

  @Transactional
  public void onOrderCreated(OrderCreatedEvent event) {
    // 1. Idempotency: unique insert; if already present, this event was handled -> stop.
    if (!ledger.tryRecord(event.eventId(), "inventory-order-created")) {
      return; // duplicate delivery (at-least-once)
    }
    // 2. Second guard: a reservation already exists for this order -> do not double-reserve.
    if (reservations.existsByOrderId(event.orderId())) {
      return;
    }
    // 3. Compute required per ingredient (reuse costing resolution path).
    Map<UUID, RequiredQty> required = computeRequired(event); // uses recipePort + UnitConverter
    // 4. Lock balance rows, check available, reserve or reject.
    List<Shortfall> shortfalls = new ArrayList<>();
    for (var e : required.entrySet()) {
      var balance = balances.lockByIngredient(e.getKey())      // SELECT ... FOR UPDATE
          .orElse(null);
      BigDecimal available = available(balance);               // on_hand - reserved
      if (balance == null || available.compareTo(e.getValue().qty()) < 0) {
        shortfalls.add(new Shortfall(e.getKey(), e.getValue().qty(), available));
      }
    }
    ResultEvent result;
    if (shortfalls.isEmpty()) {
      for (var e : required.entrySet()) {
        var balance = balances.lockByIngredient(e.getKey()).orElseThrow();
        balance.setReserved(balance.getReserved().add(e.getValue().qty())); // never makes available < 0
      }
      reservations.save(StockReservationEntity.held(event.orderId(), required));
      result = ResultEvent.confirmed(event.orderId());
    } else {
      result = ResultEvent.rejected(event.orderId(), shortfalls);
    }
    publishAfterCommit(result); // TransactionSynchronization.afterCommit, mirrors OrderSubmissionService
  }
}
```
Ledger `tryRecord` = attempt insert of a unique `event_id`; catch `DataIntegrityViolationException` → return false. Doing the insert **inside** the same transaction as the reservation guarantees the ledger row and the reservation commit together (D-03). Because ack is per-record and the publish is after-commit, a crash between DB commit and offset commit merely redelivers → ledger catches it. [ASSUMED for exact `tryRecord` impl; pattern is standard]

### Pattern 4: Pessimistic lock for non-negative invariant (D-02)
```java
public interface InventoryStockBalanceRepository extends JpaRepository<InventoryStockBalanceEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from InventoryStockBalanceEntity b "
       + "where b.ingredient.id = :ingredientId and b.locationCode = :loc")
  Optional<InventoryStockBalanceEntity> lockByIngredientAndLocation(UUID ingredientId, String loc);
}
```
Under H2 (test) and MySQL (prod) `SELECT … FOR UPDATE` serializes concurrent reservers on the same ingredient row, so `on_hand − reserved` cannot go negative under concurrency. Optionally add a DB-level guard (`CHECK (reserved <= quantity_on_hand)`) as defense-in-depth; H2 `MODE=MySQL` supports column check constraints.

### Anti-Patterns to Avoid
- **Business logic inside `@KafkaListener`:** keep the listener a one-line delegate; put reservation/transition logic in a `@Transactional` service so it is directly unit-testable without Kafka.
- **Publishing the result event inside the DB transaction (before commit):** publish **after commit** (mirror `OrderSubmissionService.publishAfterCommit`) so a rolled-back reservation never emits a spurious "confirmed".
- **Relying on `__TypeId__` header type across contexts without trusted packages:** either ignore headers + force default type, or add the producing package to `TRUSTED_PACKAGES`. A missing trusted package throws and poison-pills the partition.
- **Auto-commit offsets (`enable.auto.commit=true`):** breaks at-least-once + idempotency reasoning. Let the container commit after a successful record (AckMode.RECORD).
- **A bare `JsonDeserializer` without `ErrorHandlingDeserializer`:** a single malformed message becomes an unrecoverable poison pill that blocks the partition forever (DefaultErrorHandler cannot recover a deserialization failure that happens *before* the listener).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Retry with backoff on consume failure | custom try/catch + sleep loop | `DefaultErrorHandler(recoverer, FixedBackOff)` | Handles offset seeks, retry accounting, recovery correctly |
| Dead letter routing | manual "send to error topic" code | `DeadLetterPublishingRecoverer` | Correct `.DLT` naming, partition preservation, exception headers |
| Poison-pill / deserialization failure survival | manual byte parsing / skip logic | `ErrorHandlingDeserializer` wrapping `JsonDeserializer` | Converts deser failure into a recoverable record → DLT |
| JSON (de)serialization of events | hand-rolled ObjectMapper wiring | `JsonSerializer`/`JsonDeserializer` (as producers already do) | Type headers, trusted packages, symmetry with producers |
| Recipe → ingredient resolution | new query logic | `MenuRecipeCostingPort.findRecipe` + `UnitConverter` | Identical to costing; D-06 requires the same conversion factors |
| Concurrency-safe non-negative decrement | app-level compare-and-set | JPA `@Lock(PESSIMISTIC_WRITE)` (SELECT FOR UPDATE) | DB serializes concurrent reservers on the row |
| Offset/ack management | manual commitSync | container `AckMode.RECORD` + `enable.auto.commit=false` | Container commits only after a committed, successful handler |

**Key insight:** Every hard part of a Kafka consumer (retry, DLT, poison-pill, offset commit timing) is a solved, first-party Spring Kafka feature. The only genuinely new domain code here is the reservation math and the idempotency ledger — everything else is wiring existing beans.

## Runtime State Inventory

> This is primarily a feature-add phase, but it changes an existing enum default and introduces new Kafka topics, so a light inventory applies.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `OrderEntity.status` defaults to `SUBMITTED` (entity line 37); any orders already persisted in MySQL carry `SUBMITTED`. The enum is stored `EnumType.STRING`. Adding new values does not break existing rows, but existing `SUBMITTED` orders will never flow through the new saga. | Decide whether legacy `SUBMITTED` orders are backfilled/ignored. Recommend: leave historical rows as-is; only new orders use `PENDING_CONFIRMATION`. New code must tolerate a `SUBMITTED` status on read (D-08 read path). Confirm `GET /orders` DTO/enum handling. |
| Live service config | New Kafka topics `inventory.order-stock-results`, `orders.created.DLT`, `inventory.order-stock-results.DLT` must exist. Prod broker may not auto-create topics (`auto.create.topics.enable`). | Add topic config properties (mirror existing `order.events.*` keys) and either enable auto-create or add a `NewTopic` `@Bean`/admin step. Document broker requirement. |
| OS-registered state | None — no OS-level registrations involved. | None — verified: no schedulers/services reference this. |
| Secrets/env vars | `KAFKA_BOOTSTRAP_SERVERS` already exists (application.properties line 24). New consumer group-ids and topic names should follow the same `${ENV:default}` pattern. | Add new `${...}`-backed properties; no new secrets. |
| Build artifacts | None — no packaging/rename changes. | None. |

**The key question — what runtime state still holds the old shape after a repo update?** Existing persisted orders (status `SUBMITTED`) and any pre-created Kafka topics. Both are addressed above; nothing else carries stale state.

## Common Pitfalls

### Pitfall 1: Consumer containers try to reach `localhost:9092` during tests
**What goes wrong:** Adding `@KafkaListener` beans makes Spring start consumer containers on context load. In `@SpringBootTest` with no broker, they endlessly retry connecting to `localhost:9092`, flooding logs and occasionally slowing/flaking tests.
**Why it happens:** Listener containers auto-start by default; the current test suite never had consumers.
**How to avoid:** Add `spring.kafka.listener.auto-startup=false` to `src/test/resources/application.properties`. Tests then call the `@Transactional` service methods **directly** (the recommended strategy). For a rare wiring test, use `@EmbeddedKafka` and start containers explicitly.
**Warning signs:** `Connection to node -1 could not be established` warnings during unrelated tests.

### Pitfall 2: Trusted-packages / cross-context deserialization failure
**What goes wrong:** Consumer throws `IllegalArgumentException: The class 'com.example...OrderCreatedEvent' is not in the trusted packages`, poison-pilling the partition.
**Why it happens:** `JsonDeserializer` honors the producer's `__TypeId__` header but blocks untrusted packages by default.
**How to avoid:** Set `USE_TYPE_INFO_HEADERS=false` + `VALUE_DEFAULT_TYPE`, or set `TRUSTED_PACKAGES` to the event's package (`...order_context.application.event`). Wrap in `ErrorHandlingDeserializer` so even a genuine bad type routes to DLT instead of blocking.
**Warning signs:** Same offset retried forever; DLT empty (because failure precedes the error handler unless `ErrorHandlingDeserializer` is used).

### Pitfall 3: Double-processing on consumer rebalance
**What goes wrong:** A rebalance (or crash between DB commit and offset commit) redelivers a record; without idempotency, stock is reserved twice.
**Why it happens:** Kafka is at-least-once; the producer also publishes `OrderCreated` after commit (existing behavior), reinforcing at-least-once downstream.
**How to avoid:** `processed_events` unique `event_id` ledger (D-03) **plus** `stock_reservations` unique `order_id`. Insert ledger row in the same transaction as the reservation. Order-side transition additionally guards `status == PENDING_CONFIRMATION` before moving.
**Warning signs:** `reserved` grows beyond a single order's requirement; duplicate reservation rows.

### Pitfall 4: Transaction boundary — DB commit vs offset commit
**What goes wrong:** Handler commits the DB transaction, then the app crashes before the offset commits → the record is redelivered.
**Why it happens:** DB transaction and Kafka offset are two separate resources (no XA here).
**How to avoid:** Embrace at-least-once + idempotency (above). Keep ack after the handler returns (AckMode.RECORD). Do **not** attempt Kafka transactions / exactly-once for this phase (out of scope, adds broker requirements). Publish result event **after commit** so a rollback never emits a result.
**Warning signs:** Occasional duplicate result events (harmless — order-side ledger + status guard absorb them).

### Pitfall 5: Latent Jackson-2 serializer risk (never exercised in CI)
**What goes wrong:** Producers use the deprecated Jackson-2 `JsonSerializer`, but every test mocks the publisher, so serialization has never actually run under Spring Boot 4 / Jackson 3 defaults. A runtime serialization issue could surface only in production.
**Why it happens:** Boot 4 defaults to Jackson 3; the Jackson-2 path relies on `com.fasterxml.jackson` 2.21.4 still being on the classpath (it is).
**How to avoid:** Add at least one test that actually serializes+deserializes an `OrderCreatedEvent` and the new result event through the real `JsonSerializer`/`JsonDeserializer` (a plain serde round-trip test, no broker needed). This closes the gap cheaply.
**Warning signs:** `NoClassDefFoundError` / `ClassNotFoundException` for `com.fasterxml.jackson.*` at first real publish.

## Code Examples

### Idempotency ledger entity
```java
// New shared pattern; place per-context or as one shared table.
@Entity
@Table(name = "processed_events",
    uniqueConstraints = @UniqueConstraint(name = "uq_processed_event",
        columnNames = {"event_id", "consumer_name"}))
public class ProcessedEventEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "event_id", nullable = false) private UUID eventId;
  @Column(name = "consumer_name", nullable = false) private String consumerName;
  @Column(name = "processed_at", nullable = false) private Instant processedAt;
}
```
Keying by `(event_id, consumer_name)` lets the same physical event id be independently tracked per consumer if you ever share ids; here ids are unique per publish so `event_id` alone would also work.

### Result event record (single topic, D-10/D-11)
```java
public record OrderStockResultEvent(
    UUID eventId, String eventType, Instant occurredAt,
    UUID orderId, Result result, List<Shortfall> shortfalls) {
  public enum Result { CONFIRMED, REJECTED }
  public record Shortfall(UUID ingredientId, String ingredientName,
      BigDecimal required, BigDecimal available) {}
  public static final String CONFIRMED_TYPE = "OrderStockConfirmed";
  public static final String REJECTED_TYPE  = "OrderStockRejected";
}
```
One topic + a `result` discriminator is simpler than two topics and keeps ordering per `orderId` (use `orderId` as the message key, as existing publishers do).

### Order-side transition (idempotent + status-guarded)
```java
@Transactional
public void onStockResult(OrderStockResultEvent event) {
  if (!ledger.tryRecord(event.eventId(), "order-stock-result")) return;
  OrderEntity order = orderRepository.findById(event.orderId()).orElse(null);
  if (order == null || order.getStatus() != OrderStatus.PENDING_CONFIRMATION) return; // guard
  order.setStatus(event.result() == Result.CONFIRMED ? OrderStatus.CONFIRMED : OrderStatus.REJECTED);
  if (event.result() == Result.REJECTED) order.setRejectionReason(describe(event.shortfalls()));
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Spring Kafka 3.x `JsonSerializer`/`JsonDeserializer` (Jackson 2) | `JacksonJsonSerializer`/`JacksonJsonDeserializer` (Jackson 3) | Spring Kafka 4.0 (Boot 4.0) | Old classes deprecated **for removal**; still functional while Jackson 2 stays on classpath |
| `SeekToCurrentErrorHandler` / `ErrorHandler` (pre-2.8) | `DefaultErrorHandler` + `CommonErrorHandler` | Spring Kafka 2.8+ | `DefaultErrorHandler` is the single blocking-retry handler; older handlers removed |
| ZooKeeper-based brokers | KRaft-only (Kafka 4.0 clients) | Kafka 4.0 (bundled with spring-kafka 4.0) | Runtime/broker concern only; no code impact for this phase |

**Deprecated/outdated:**
- `org.springframework.kafka.support.serializer.JsonSerializer` / `JsonDeserializer` — deprecated for removal since 4.0. This phase intentionally keeps using them for producer↔consumer symmetry; migrating all serdes to `JacksonJson*` is a recommended future phase.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Keeping Jackson-2 `JsonSerializer`/`JsonDeserializer` (matching existing producers) is preferable to introducing Jackson-3 `JacksonJson*` for the new consumers this phase | Standard Stack / Alternatives | If the team wants zero-deprecation code, they'd migrate all serdes to Jackson 3 now (bigger blast radius, touches shipped producers) |
| A2 | Existing Jackson-2 producers actually serialize correctly at runtime under Boot 4 (never exercised — publishers mocked in all tests) | Pitfall 5 | A real serialization failure would surface only in production; mitigated by adding a serde round-trip test |
| A3 | `USE_TYPE_INFO_HEADERS=false` + `VALUE_DEFAULT_TYPE` is the safest cross-context deserialization config | Pattern 1 | If kept `true` without trusted-packages, deserialization poison-pills the partition |
| A4 | Reservation modeled as `reserved` column on balance **plus** a `stock_reservations` table keyed by `orderId` (both) | Alternatives / Component map | Column-only would block Phase-16 settlement and the unique-by-order idempotency guard |
| A5 | Idempotency ledger insert done in the same DB transaction as the reservation (no Kafka transactions / exactly-once) | Pattern 3 / Pitfall 4 | Exactly-once would need broker transaction support and is out of scope; at-least-once + idempotency is the intended design (D-03) |
| A6 | Prod broker either auto-creates topics or topics are pre-declared; DLT topics `<topic>.DLT` exist | Runtime State Inventory | Missing DLT topic → recoverer fails to publish; error handler then blocks |
| A7 | `spring.kafka.listener.auto-startup=false` in test properties is the right way to keep consumers from dialing a non-existent broker | Validation Architecture / Pitfall 1 | If wrong, tests emit connection-retry noise (non-fatal) |

## Open Questions

> **Status: RESOLVED at planning time (Phase 15 plans 15-01..15-06).** All four questions below were decided during plan authoring; resolutions are baked into the plan tasks.

1. **Migrate all Kafka serdes to Jackson 3 now, or defer?** — RESOLVED: **defer.** Use Jackson-2 `JsonSerializer`/`JsonDeserializer` for producer/consumer symmetry (A1, D-05). A future phase migrates producer + consumer to Jackson 3 together.

2. **One shared `processed_events` table or one per bounded context?** — RESOLVED: **one table per context, with physically distinct table names** — `inventory_processed_events` (Plan 15-02) and `order_processed_events` (Plan 15-05), each keyed `(event_id, consumer_name)`. This is mandatory, not optional: the app has ONE datasource / ONE JPA persistence unit, so two `@Entity` classes mapping the same physical table name would fail DDL/startup.

3. **Legacy `SUBMITTED` orders in prod DB — backfill or ignore?** — RESOLVED: **ignore.** Only new orders enter the saga; `OrderConfirmationService` guards on `status == PENDING_CONFIRMATION`, so legacy `SUBMITTED` rows are untouched and read paths still tolerate them (D-08).

4. **Broker topic provisioning strategy (auto-create vs `NewTopic` beans).** — RESOLVED: **`NewTopic` beans.** `InventoryKafkaTopicConfig` (Plan 15-04) declares `NewTopic` `@Bean`s for the result topic `inventory.order-stock-results` plus both DLTs `orders.created.DLT` and `inventory.order-stock-results.DLT`, auto-declared via Boot's auto-configured `KafkaAdmin` from `spring.kafka.bootstrap-servers`. The order-side consumer (Plan 15-06) reuses those same topic beans and declares no duplicates. This removes any reliance on broker `auto.create.topics.enable`. (Reconciles assumption A6.)

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Kafka broker (`localhost:9092`) | Runtime consume/produce | Not verified (dev machine) | — | Tests need **none** (auto-startup=false + direct service calls) |
| MySQL 3306 | Prod persistence (ledger, reservations) | Not verified | — | H2 (`MODE=MySQL`) in tests, already configured |
| H2 | Test persistence | ✓ (test dep + `src/test/resources/application.properties`) | Boot 4.0.6 managed | — |
| `spring-kafka` 4.0.5 | All consumer code | ✓ | 4.0.5 | — |
| `spring-kafka-test` (EmbeddedKafka) | Optional wiring test only | ✗ (not a dependency) | — | Test service methods directly (recommended) — no EmbeddedKafka needed |

**Missing dependencies with no fallback:** none for tests. A live Kafka broker is required for the feature to function in a running environment, but is **not** required to build or test this phase.
**Missing dependencies with fallback:** live broker at dev/test time → not needed (test the `@Transactional` services directly).

## Validation Architecture

> `.planning/config.json` is absent → `nyquist_validation` treated as enabled.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test 4.0.6 + Mockito (`@MockitoBean`) + H2 |
| Config file | `src/test/resources/application.properties` (H2, `MODE=MySQL`, `ddl-auto=create-drop`) |
| Quick run command | `./mvnw -q -Dtest=InventoryReservationServiceTest test` |
| Full suite command | `./mvnw test` |

### Phase Requirements (D-0x) → Test Map
| Decision | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| D-01/D-08 | New order persisted as `PENDING_CONFIRMATION`; `POST /orders` returns it | integration (MockMvc, H2) | `./mvnw -Dtest=OrderSubmissionIntegrationTest test` | exists (extend) |
| D-06 | Requirement resolution via recipe port + `UnitConverter` (topping + dish, missing recipe = 0) | unit | `./mvnw -Dtest=InventoryReservationServiceTest test` | ❌ Wave 0 |
| D-02/D-09 | Reserve when available; `available = on_hand − reserved`; never negative; reject when short | unit + integration | `./mvnw -Dtest=InventoryReservationServiceTest test` | ❌ Wave 0 |
| D-03 | Duplicate `OrderCreated` (same eventId) does not double-reserve; duplicate result does not re-transition | unit | `./mvnw -Dtest=InventoryReservationServiceTest,OrderConfirmationServiceTest test` | ❌ Wave 0 |
| D-10/D-11 | Result event drives `PENDING_CONFIRMATION`→`CONFIRMED`/`REJECTED` with reason | unit + integration | `./mvnw -Dtest=OrderConfirmationServiceTest test` | ❌ Wave 0 |
| D-04 | Error-handler/DLT wiring (bean present; not-retryable deser exception) | unit (bean config) or optional EmbeddedKafka | `./mvnw -Dtest=InventoryKafkaConsumerConfigTest test` | ❌ Wave 0 |
| Pitfall 5 | Event serde round-trips through real `JsonSerializer`/`JsonDeserializer` | unit | `./mvnw -Dtest=EventSerdeRoundTripTest test` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** the specific new `*ServiceTest` for that task.
- **Per wave merge:** `./mvnw test` (full suite — currently 119 tests, must stay green).
- **Phase gate:** full suite green before `/gsd:verify-work`.

### Wave 0 Gaps
- [ ] `src/test/resources/application.properties` — add `spring.kafka.listener.auto-startup=false`
- [ ] `InventoryReservationServiceTest` — D-02/D-03/D-06/D-09 (reserve/reject/idempotency/resolution)
- [ ] `OrderConfirmationServiceTest` — D-03/D-10/D-11 (transition + idempotency + status guard)
- [ ] `EventSerdeRoundTripTest` — closes Pitfall 5 (real serializer/deserializer)
- [ ] (optional) EmbeddedKafka smoke test for listener wiring — only if `spring-kafka-test` is added
- [ ] Extend `OrderSubmissionIntegrationTest` for `PENDING_CONFIRMATION` default (D-01/D-08)

## Security Domain

> `security_enforcement` config absent → treated as enabled.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Consumers are internal Kafka listeners, not HTTP endpoints. `POST /orders`/`GET /orders` auth already enforced by existing Spring Security (owner-only, `@AuthenticationPrincipal`). |
| V3 Session Management | no | No new sessions. |
| V4 Access Control | partial | No new HTTP routes. Ensure `GET /orders/{id}` still enforces owner-only when reading async `CONFIRMED`/`REJECTED` state (existing rule covers this). |
| V5 Input Validation | **yes** | Event payloads are **untrusted input** at the deserialization boundary. Controls: `TRUSTED_PACKAGES` / forced default type, `ErrorHandlingDeserializer` (poison-pill → DLT), null/empty line handling, non-negative quantity checks reused from `UnitConverter`/reservation logic. |
| V6 Cryptography | no | No new crypto; do not hand-roll any. |

### Known Threat Patterns for Spring Kafka consumers
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malicious/oversized `__TypeId__` header → arbitrary class instantiation | Tampering / Elevation | `TRUSTED_PACKAGES` allow-list or `USE_TYPE_INFO_HEADERS=false` + fixed `VALUE_DEFAULT_TYPE` |
| Poison-pill message blocks partition (DoS) | Denial of Service | `ErrorHandlingDeserializer` + `DefaultErrorHandler` → `.DLT`; deser exception marked not-retryable |
| Replayed event double-reserves stock | Tampering | Idempotency ledger (unique `event_id`) + unique `order_id` reservation (D-03) |
| Spurious "confirmed" from a rolled-back reservation | Tampering | Publish result event **after commit** only |
| Negative stock via concurrent reservers | Tampering | `SELECT … FOR UPDATE` (pessimistic lock) + optional DB `CHECK` constraint |

## Sources

### Primary (HIGH confidence)
- Local build reality: `pom.xml` (Boot 4.0.6, spring-kafka managed, Java 17), `~/.m2` (spring-kafka 4.0.5, Jackson 2.21.4 + Jackson 3.1.4), `javap` of `spring-kafka-4.0.5.jar` (deprecated Jackson-2 serializers + new `JacksonJson*` both present).
- In-repo code: `OrderKafkaProducerConfig`, `KafkaOrderEventPublisher`, `OrderSubmissionService` (after-commit publish), `OrderCreatedEvent`, `OrderStatus`, `InventoryStockService`, `InventoryStockBalanceEntity/Repository`, `UnitConverter`, `MenuRecipeCostingPort`/`RecipeCostingSnapshot`, existing tests (`@MockitoBean` publisher pattern, H2 test properties).
- [docs.spring.io/spring-kafka/api/.../JsonDeserializer.html] — deprecation-for-removal note, static constants (`TRUSTED_PACKAGES`, `TYPE_MAPPINGS`, `VALUE_DEFAULT_TYPE`, `USE_TYPE_INFO_HEADERS`).
- [docs.spring.io/spring-kafka/reference/kafka/serdes.html] — `JacksonJson*` replacements + config properties.
- [docs.spring.io/spring-kafka/api/.../DeadLetterPublishingRecoverer.html] — default `<topic>.DLT` destination.
- [docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html] — `DefaultErrorHandler` + recoverer wiring.

### Secondary (MEDIUM confidence)
- WebSearch (official docs cross-referenced) — `.DLT` vs `-dlt` suffix distinction (DefaultErrorHandler+DLPR vs `@RetryableTopic`); `autoStartup` on annotation/factory; `spring.kafka.listener.auto-startup`.

### Tertiary (LOW confidence)
- None relied upon for load-bearing claims.

## Metadata

**Confidence breakdown:**
- Standard stack / versions: HIGH — verified against local m2 + jar bytecode + official API docs.
- Architecture / patterns: HIGH — reuses proven in-repo patterns (after-commit publish, `@Transactional` services, `UnitConverter`, recipe port) + first-party Spring Kafka beans.
- Jackson 2 vs 3 nuance: MEDIUM — deprecation confirmed; the exact cross-generation header behavior and the untested-serializer risk (A2) are flagged, mitigated by a proposed serde round-trip test.
- Pitfalls: HIGH — standard, well-documented Kafka consumer failure modes.

**Research date:** 2026-07-07
**Valid until:** ~2026-08-07 (30 days; spring-kafka 4.0.x is current/stable, but confirm no 4.0.x → 4.1 bump before planning if delayed — Boot 4.1.0 already present in m2).
