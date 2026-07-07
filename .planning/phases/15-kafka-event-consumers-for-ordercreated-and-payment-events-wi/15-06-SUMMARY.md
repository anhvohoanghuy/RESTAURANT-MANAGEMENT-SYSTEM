---
phase: 15-kafka-event-consumers
plan: 06
subsystem: api
tags: [kafka, spring-kafka, jackson3, saga, order, consumer, dlt, event-driven]

# Dependency graph
requires:
  - phase: 15-kafka-event-consumers
    plan: 01
    provides: OrderStockResultEvent contract, saga Kafka properties, Jackson-3 serde decision
  - phase: 15-kafka-event-consumers
    plan: 05
    provides: OrderConfirmationService.onStockResult (idempotent, status-guarded transition)
provides:
  - Order-side Kafka consumer config (typed ConsumerFactory + container factory + DLT error handler)
  - Thin OrderStockResultListener delegating to OrderConfirmationService (closes the saga loop)
  - Broker-free OrderKafkaConsumerConfig wiring test
affects: [15-verification]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Order consumer mirrors the producer idiom: @EnableKafka typed ConsumerFactory + ErrorHandlingDeserializer -> Jackson-3 JacksonJsonDeserializer"
    - "DefaultErrorHandler + DeadLetterPublishingRecoverer with DeserializationException classified not-retryable -> <topic>.DLT (poison-pill safety)"
    - "Thin @KafkaListener adapter delegating verbatim to an idempotent application service"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaConsumerConfig.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/adapter/OrderStockResultListener.java
    - src/test/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaConsumerConfigTest.java
  modified:
    - .planning/phases/15-kafka-event-consumers-for-ordercreated-and-payment-events-wi/deferred-items.md

key-decisions:
  - "Delegated to the Jackson-3 JacksonJsonDeserializer (not the plan's legacy JsonDeserializer), matching the phase-wide producer serde; the Jackson-2 deserializer cannot handle the event's Instant field on this Boot 4 classpath"
  - "Full @SpringBootTest suite is deferred, not fixed: it needs 15-04's InventoryStockResultPublisher adapter (a parallel wave-3 worktree not yet merged into this base)"

requirements-completed: [D-04, D-05, D-10]

# Metrics
duration: 15min
completed: 2026-07-07
---

# Phase 15 Plan 06: Order Result-Event Consumer Summary

**Wired the Order-side Kafka consumer — a typed `OrderKafkaConsumerConfig` (@EnableKafka, ErrorHandlingDeserializer -> Jackson-3 JacksonJsonDeserializer, AckMode.RECORD, DefaultErrorHandler + DLT) and a thin `OrderStockResultListener` delegating to `OrderConfirmationService` — closing the order-confirmation saga end-to-end with poison-pill/DLT safety.**

## Performance

- **Duration:** ~15 min
- **Tasks:** 3
- **Files:** 3 created, 1 modified (deferred-items log)

## Accomplishments
- `OrderKafkaConsumerConfig` (D-04/D-05): typed `ConsumerFactory<String, OrderStockResultEvent>` (group `order-stock-result`, `earliest`, auto-commit off), value `ErrorHandlingDeserializer` wrapping the Jackson-3 `JacksonJsonDeserializer` with trusted package `com.example.feat1.DDD.order_context.application.event`, forced `VALUE_DEFAULT_TYPE`, and `USE_TYPE_INFO_HEADERS=false` (T-15-07 mitigation).
- Distinct DLT wiring: `orderDltKafkaTemplate` bean + `orderStockResultErrorHandler` = `DefaultErrorHandler(DeadLetterPublishingRecoverer, FixedBackOff(1000ms, 3))` with `DeserializationException` classified not-retryable (routes straight to `inventory.order-stock-results.DLT` — T-15-08/T-15-09). All bean names distinct from the inventory config to avoid collisions; no `NewTopic` beans re-declared (owned by 15-04).
- `orderStockResultKafkaListenerContainerFactory` with `AckMode.RECORD`.
- `OrderStockResultListener` (D-10): `@Component` `@KafkaListener` one-line delegate to `confirmationService.onStockResult(event)` — no business logic in the adapter.
- Broker-free `OrderKafkaConsumerConfigTest` (3 tests): asserts auto-commit off, ErrorHandlingDeserializer -> JacksonJsonDeserializer, the three T-15-07 mitigation properties, DLT template + error handler presence, and container `AckMode.RECORD`.

## Task Commits

1. **Task 1: Order result-event consumer config (ErrorHandlingDeserializer + DLT)** - `99a08f9` (feat)
2. **Task 2: OrderStockResultListener thin delegate** - `ad5ba52` (feat)
3. **Task 3: Broker-free config wiring test** - `8411867` (test)
4. **Deferred-items log (cross-wave context failure)** - `abd49b6` (docs)

## Files Created/Modified
- `OrderKafkaConsumerConfig.java` (new) - Consumer factory, DLT template, error handler, container factory.
- `OrderStockResultListener.java` (new) - Thin @KafkaListener delegate.
- `OrderKafkaConsumerConfigTest.java` (new) - Broker-free bean-wiring test.
- `deferred-items.md` - Logged the cross-wave full-context test failure.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Correctness] Delegate deserializer must be the Jackson-3 `JacksonJsonDeserializer`, not the plan's legacy `JsonDeserializer`**
- **Found during:** Task 1
- **Issue:** The plan action and RESEARCH Pattern 1 name the legacy Jackson-2 `org.springframework.kafka.support.serializer.JsonDeserializer`. Plan 15-01 established that this project runs on Spring Boot 4 / Jackson 3 with no `jackson-datatype-jsr310`, so the Jackson-2 serde cannot (de)serialize `Instant` — and `OrderStockResultEvent.occurredAt` is an `Instant`. The producer already emits with the Jackson-3 `JacksonJsonSerializer`; a Jackson-2 consumer deserializer would fail on every message. No new dependency is permitted.
- **Fix:** Used `ErrorHandlingDeserializer` delegating to `org.springframework.kafka.support.serializer.JacksonJsonDeserializer`, with its config constants (`TRUSTED_PACKAGES`, `VALUE_DEFAULT_TYPE`, `USE_TYPE_INFO_HEADERS` — verified present on the class in spring-kafka 4.0.5). Mirrors 15-01's proven serde pattern.
- **Files modified:** `OrderKafkaConsumerConfig.java`
- **Verification:** Compiles; `OrderKafkaConsumerConfigTest` asserts the delegate class is `JacksonJsonDeserializer` and passes 3/3.
- **Committed in:** `99a08f9` (Task 1)

**Total deviations:** 1 auto-fixed (1 correctness). No scope creep — required for the consumer to deserialize the saga event at all.

## Issues Encountered / Deferred Issues

**Full `@SpringBootTest` suite cannot load its ApplicationContext in this isolated worktree (deferred, out of scope).**
- `./mvnw test` reported 135 run, 0 failures, **34 errors** — all "Failed to load ApplicationContext". Root cause: `InventoryReservationService` (merged from 15-03) requires an `InventoryStockResultPublisher` bean whose Kafka adapter is produced by plan **15-04**, a sibling wave-3 plan in a separate parallel worktree not yet merged into this base (`57cc058`).
- The failure spans every full-context integration test across all bounded contexts (auth, table, payment, inventory, order) — contexts 15-06 never touches — and **no error implicates any 15-06 order-consumer bean**, proving it is pre-existing at the worktree base and independent of this plan.
- Not fixed here per the scope boundary: creating a stub publisher would duplicate/collide with 15-04's real adapter on merge. Resolution is automatic when the orchestrator merges the wave-3 worktrees (15-04 + 15-06) together. Logged to `deferred-items.md` (`abd49b6`).
- All broker-free tests pass in isolation, including the new `OrderKafkaConsumerConfigTest` (3/3) and the phase-15 serde round-trip tests.

## Known Stubs
None.

## Threat Flags
None — all serde/DLT surface for the result topic is covered by the plan's existing threat register (T-15-07/08/09), and no new endpoints, auth paths, or schema were introduced.

## User Setup Required
None — topic/group-id properties default via `${ENV:default}`; the consumed topic and its `.DLT` are provisioned by 15-04.

## Self-Check: PASSED

All created files present (`OrderKafkaConsumerConfig.java`, `OrderStockResultListener.java`, `OrderKafkaConsumerConfigTest.java`) and all task commits (`99a08f9`, `ad5ba52`, `8411867`, `abd49b6`) verified in git history. `OrderKafkaConsumerConfigTest` green 3/3 broker-free. Full-suite context-load errors are a documented, deferred cross-wave dependency on 15-04 (not a 15-06 defect).

---
*Phase: 15-kafka-event-consumers-for-ordercreated-and-payment-events-wi*
*Completed: 2026-07-07*
