---
phase: 15-kafka-event-consumers
plan: 04
subsystem: inventory_context
tags: [inventory, kafka, consumer, producer, dlt, poison-pill, saga, wiring]
requires:
  - 15-01 OrderStockResultEvent contract + Jackson-3 phase-wide serde + saga Kafka properties
  - 15-03 InventoryReservationService.onOrderCreated + InventoryStockResultPublisher port
provides:
  - KafkaInventoryStockResultPublisher (the InventoryStockResultPublisher bean — unblocks repo-wide @SpringBootTest context loads)
  - InventoryKafkaProducerConfig (typed ProducerFactory + KafkaTemplate for OrderStockResultEvent)
  - InventoryKafkaConsumerConfig (@EnableKafka ConsumerFactory + container factory + DefaultErrorHandler/DLT)
  - OrderCreatedListener (thin @KafkaListener delegating to the reservation service)
  - InventoryKafkaTopicConfig (NewTopic beans for result + both DLT topics)
affects:
  - 15-06 order-side result consumer reuses these same topic beans (declares no duplicates)
tech-stack:
  added: []
  patterns:
    - "Consumer factory mirroring the producer-config style: ErrorHandlingDeserializer wrapping the Jackson-3 JacksonJsonDeserializer"
    - "USE_TYPE_INFO_HEADERS=false + VALUE_DEFAULT_TYPE + TRUSTED_PACKAGES allow-list for cross-context type-confusion defence"
    - "DefaultErrorHandler + DeadLetterPublishingRecoverer (.DLT) with DeserializationException marked not-retryable"
    - "NewTopic @Beans auto-declared via Boot's auto-configured KafkaAdmin (no reliance on broker auto-create)"
    - "Thin @KafkaListener delegating to a @Transactional application service (zero business logic in the adapter)"
key-files:
  created:
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaProducerConfig.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/adapter/KafkaInventoryStockResultPublisher.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaTopicConfig.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaConsumerConfig.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/adapter/OrderCreatedListener.java
    - src/test/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaConsumerConfigTest.java
  modified: []
decisions:
  - "Used the phase-wide Jackson-3 JacksonJsonSerializer/JacksonJsonDeserializer instead of the plan's Jackson-2 JsonSerializer/JsonDeserializer: the events carry java.time.Instant, which the Jackson-2 serde throws on for this Boot 4 / Jackson 3 classpath (Rule 1 — established in 15-01, directed by upstream context)."
  - "DLT KafkaTemplate also uses the Jackson-3 serializer so a handler-failed (already-deserialized) OrderCreatedEvent — which carries Instant — can be re-serialized onto orders.created.DLT."
  - "DeserializationException classification asserted via handler.removeClassification(...) returning FALSE (not-retryable) rather than a full EmbeddedKafka round-trip; the config test stays broker-free."
requirements: [D-04, D-05, D-10]
metrics:
  duration: ~15m
  completed: 2026-07-07
---

# Phase 15 Plan 04: Inventory Kafka Boundary Wiring Summary

Wired the Inventory Kafka boundary (D-04/D-05/D-10): a result-event producer config + `KafkaInventoryStockResultPublisher` adapter, an `@EnableKafka` consumer config with `ErrorHandlingDeserializer` -> Jackson-3 `JacksonJsonDeserializer` and a `DefaultErrorHandler`/DLT, a one-line `OrderCreatedListener` delegating to the 15-03 reservation service, and `NewTopic` beans so the result topic and both DLTs exist without broker auto-create.

## What Was Built

- **Task 1** — `InventoryKafkaProducerConfig` (typed `ProducerFactory<String, OrderStockResultEvent>` + `KafkaTemplate`, Jackson-3 serializer), `KafkaInventoryStockResultPublisher` (`@Component implements InventoryStockResultPublisher`, sends to `inventory.order-stock-results` keyed by `orderId`), and `InventoryKafkaTopicConfig` (`NewTopic` beans for the result topic + `orders.created.DLT` + `inventory.order-stock-results.DLT`, DLT names derived from the same `@Value` properties as the live topics).
- **Task 2** — `InventoryKafkaConsumerConfig` (`@Configuration @EnableKafka`): `ConsumerFactory` with `enable.auto.commit=false`, `earliest`, `ErrorHandlingDeserializer` wrapping `JacksonJsonDeserializer` (`TRUSTED_PACKAGES` allow-list, forced `VALUE_DEFAULT_TYPE=OrderCreatedEvent`, `USE_TYPE_INFO_HEADERS=false`); a distinctly-named `inventoryDltKafkaTemplate`; `DefaultErrorHandler(DeadLetterPublishingRecoverer, FixedBackOff(1000L, 3L))` with `DeserializationException` not-retryable; `ConcurrentKafkaListenerContainerFactory` with `AckMode.RECORD`. `OrderCreatedListener` is a one-line `@KafkaListener` delegate to `InventoryReservationService.onOrderCreated`.
- **Task 3** — `InventoryKafkaConsumerConfigTest`: broker-free, direct-instantiation. Asserts the consumer factory disables auto-commit and forces the safe deserialization config, the container factory uses `AckMode.RECORD`, and the error handler marks `DeserializationException` not-retryable.

## Verification

- `./mvnw -q compile` passed after Task 1 and Task 2.
- `./mvnw -q -Dtest=InventoryKafkaConsumerConfigTest test`: **3/3 green**.
- `./mvnw -q test` (full suite): **135/135 green, 0 failures, 0 errors**. Providing the `InventoryStockResultPublisher` bean (`KafkaInventoryStockResultPublisher`) restored every `@SpringBootTest` context load that was RED while the port had no implementation.

## Task Commits

1. **Task 1: inventory result-event producer + publisher adapter + topics** — `e4de143` (feat)
2. **Task 2: inventory OrderCreated consumer config + thin listener** — `efde792` (feat)
3. **Task 3: broker-free wiring test for inventory consumer config** — `42b1295` (test)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Used Jackson-3 serde instead of the plan's Jackson-2 `JsonSerializer`/`JsonDeserializer`**
- **Found during:** Task 1 (before writing the producer config).
- **Issue:** The plan's Task 1/Task 2 (and the older RESEARCH decision Q1) named the legacy Jackson-2 `org.springframework.kafka.support.serializer.JsonSerializer`/`JsonDeserializer` for "producer/consumer symmetry (D-05)". But 15-01 already established (and the upstream wave context re-confirmed) that those Jackson-2 serdes **throw** on `java.time.Instant` on this Spring Boot 4 / Jackson 3 classpath (no `jackson-datatype-jsr310` present, no new dependencies allowed). Both `OrderCreatedEvent` (consumed) and `OrderStockResultEvent` (produced) carry `Instant` fields, so the Jackson-2 path would fail at first real publish/consume. The order-side producer was already migrated to `JacksonJsonSerializer` in 15-01, making Jackson-3 the actual phase-wide serde — using Jackson-2 here would also break producer/consumer symmetry.
- **Fix:** Used `JacksonJsonSerializer` (producer + DLT template) and `ErrorHandlingDeserializer` -> `JacksonJsonDeserializer` (consumer), with the JacksonJsonDeserializer config constants (`TRUSTED_PACKAGES`, `VALUE_DEFAULT_TYPE`, `USE_TYPE_INFO_HEADERS`). All threat-model mitigations (T-15-07 type-confusion, T-15-08 poison-pill, T-15-09 DLT preservation) are preserved — the Jackson-3 deserializer exposes the same trusted-packages/default-type/ignore-headers knobs.
- **Files modified:** `InventoryKafkaProducerConfig.java`, `InventoryKafkaConsumerConfig.java`.
- **Verification:** Full suite 135/135 green; `EventSerdeRoundTripTest` (15-01) already proves both events round-trip through this Jackson-3 serde family.
- **Committed in:** `e4de143` (producer), `efde792` (consumer).

**Total deviations:** 1 auto-fixed (1 bug). No scope creep — same beans, same topics, same DLT/poison-pill behavior; only the serde generation changed to the one that actually works on this stack.

## Threat Mitigations Applied

- **T-15-07** (malicious `__TypeId__` -> arbitrary class): `USE_TYPE_INFO_HEADERS=false` + forced `VALUE_DEFAULT_TYPE=OrderCreatedEvent` + `TRUSTED_PACKAGES` allow-list (`...order_context.application.event`).
- **T-15-08** (poison-pill blocks partition): `ErrorHandlingDeserializer` + `DefaultErrorHandler` routes deserialization failures to `orders.created.DLT`; `DeserializationException` marked not-retryable.
- **T-15-09** (lost failed messages): `DeadLetterPublishingRecoverer` preserves failed records + exception headers on `.DLT`; both DLT topics pre-declared via `NewTopic` beans.

## Notes for Downstream Plans

- 15-06 (order-side result consumer) must reuse the topic beans in `InventoryKafkaTopicConfig` and declare **no** duplicate `NewTopic` beans for `inventory.order-stock-results` / `inventory.order-stock-results.DLT`.
- The phase-wide Kafka serde is Jackson-3 (`JacksonJsonSerializer`/`JacksonJsonDeserializer`); 15-06's consumer must use `JacksonJsonDeserializer` with `com.example.feat1.*` trusted packages, not the legacy Jackson-2 classes.
- The DLT template is a distinctly-named `inventoryDltKafkaTemplate` (`@Qualifier`) because multiple `KafkaTemplate` beans exist app-wide.

## Self-Check: PASSED
