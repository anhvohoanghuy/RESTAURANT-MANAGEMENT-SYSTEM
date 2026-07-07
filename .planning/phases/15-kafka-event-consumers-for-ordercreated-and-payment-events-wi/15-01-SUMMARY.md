---
phase: 15-kafka-event-consumers
plan: 01
subsystem: api
tags: [kafka, spring-kafka, jackson3, saga, order, serde, event-driven]

# Dependency graph
requires:
  - phase: 10-order-submission
    provides: OrderCreatedEvent contract, OrderKafkaProducerConfig, after-commit publish wiring
provides:
  - Extended OrderStatus lifecycle (SUBMITTED, PENDING_CONFIRMATION, CONFIRMED, REJECTED)
  - Orders now created in PENDING_CONFIRMATION with a nullable rejection_reason column
  - Shared OrderStockResultEvent saga-result contract (Result enum + Shortfall record + type constants)
  - Saga Kafka properties (result topic + inventory/order consumer group-ids)
  - Test-side spring.kafka.listener.auto-startup=false
  - Serde round-trip coverage proving both saga events survive real Jackson-3 spring-kafka serde
affects: [15-02-inventory-reservation, 15-03-order-confirmation, 15-04-inventory-consumer, 15-06-order-consumer]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Boot 4 / Jackson 3 Kafka serde via spring-kafka JacksonJsonSerializer/JacksonJsonDeserializer (tools.jackson, native java.time)"
    - "Shared cross-context event record with static TYPE constants + nested Result enum / Shortfall record"
    - "Broker-free serde round-trip test as the serialization contract guard"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/order_context/application/event/OrderStockResultEvent.java
    - src/test/java/com/example/feat1/DDD/order_context/EventSerdeRoundTripTest.java
  modified:
    - src/main/java/com/example/feat1/DDD/order_context/domain/model/OrderStatus.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/entity/OrderEntity.java
    - src/main/java/com/example/feat1/DDD/order_context/application/OrderSubmissionService.java
    - src/main/java/com/example/feat1/DDD/order_context/infrastructure/config/OrderKafkaProducerConfig.java
    - src/main/resources/application.properties
    - src/test/resources/application.properties
    - src/test/java/com/example/feat1/DDD/order_context/integration/OrderSubmissionIntegrationTest.java

key-decisions:
  - "Kept SUBMITTED in OrderStatus so historical EnumType.STRING rows stay valid"
  - "Placed shared OrderStockResultEvent in order_context.application.event; both contexts import it and consumer TRUSTED_PACKAGES will list this exact package"
  - "Migrated order Kafka serde to the Jackson-3 JacksonJsonSerializer because the legacy Jackson-2 JsonSerializer cannot serialize Instant on the Boot 4 classpath (no jackson-datatype-jsr310, and no new deps allowed this phase)"

patterns-established:
  - "Jackson-3 (JacksonJsonSerializer/JacksonJsonDeserializer) is the phase-wide Kafka serde; downstream consumers must use JacksonJsonDeserializer with trusted packages"
  - "Every saga event contract is guarded by a broker-free serde round-trip test"

requirements-completed: [D-01, D-08, D-10, D-11]

# Metrics
duration: 40min
completed: 2026-07-07
---

# Phase 15 Plan 01: Order-side Saga Foundation Summary

**Extended the order status lifecycle to PENDING_CONFIRMATION, added the shared OrderStockResultEvent saga contract, and proved both saga events round-trip through the Boot 4 / Jackson-3 spring-kafka serde (fixing a latent Instant-serialization bug in the order producer).**

## Performance

- **Duration:** ~40 min
- **Started:** 2026-07-07T12:58Z (worktree base)
- **Completed:** 2026-07-07T13:37:37+07:00
- **Tasks:** 3
- **Files modified:** 9 (2 created, 7 modified)

## Accomplishments
- OrderStatus now carries SUBMITTED, PENDING_CONFIRMATION, CONFIRMED, REJECTED; new orders default to PENDING_CONFIRMATION (D-01/D-08) and OrderEntity gained a nullable `rejection_reason` column.
- Shared `OrderStockResultEvent` saga-result contract created (Result enum, Shortfall record, CONFIRMED_TYPE/REJECTED_TYPE constants) for both inventory and order contexts (D-10/D-11).
- Saga Kafka properties registered (result topic + inventory/order consumer group-ids); tests disable Kafka listener auto-startup (RESEARCH Pitfall 1).
- Serde round-trip test proves OrderCreatedEvent and both OrderStockResultEvent variants survive real spring-kafka serde, closing RESEARCH Pitfall 5 — and exposed/fixed that the order producer could not serialize Instant on this stack.

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend order status lifecycle, default to PENDING_CONFIRMATION** - `33b9f64` (feat)
2. **Task 2: OrderStockResultEvent contract + saga Kafka properties** - `7644392` (feat)
3. **Task 3: Serde round-trip test + PENDING_CONFIRMATION integration assertions (+ Rule 1 producer fix)** - `469f39f` (test)

**Deferred-items log:** `cb868cb` (docs)

## Files Created/Modified
- `OrderStatus.java` - Added PENDING_CONFIRMATION, CONFIRMED, REJECTED (kept SUBMITTED).
- `OrderEntity.java` - Default status PENDING_CONFIRMATION; new nullable `rejection_reason` column.
- `OrderSubmissionService.java` - submit() sets PENDING_CONFIRMATION.
- `OrderStockResultEvent.java` (new) - Shared saga-result event record.
- `OrderKafkaProducerConfig.java` - Value serializer switched to Jackson-3 JacksonJsonSerializer.
- `application.properties` - Result topic + inventory/order consumer group-id properties.
- `test/resources/application.properties` - `spring.kafka.listener.auto-startup=false`.
- `EventSerdeRoundTripTest.java` (new) - Broker-free serde round-trip for both saga events.
- `OrderSubmissionIntegrationTest.java` - Asserts POST /orders and the persisted read yield PENDING_CONFIRMATION.

## Decisions Made
- Kept `SUBMITTED` in the enum to preserve historical `EnumType.STRING` rows (RESEARCH Runtime State Inventory).
- Shared `OrderStockResultEvent` lives in `order_context.application.event`; downstream consumers must trust this exact package.
- Adopted the Jackson-3 `JacksonJsonSerializer`/`JacksonJsonDeserializer` as the phase-wide Kafka serde (see deviation below).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Order Kafka producer could not serialize `Instant`**
- **Found during:** Task 3 (serde round-trip test)
- **Issue:** The plan named the legacy Jackson-2 `org.springframework.kafka.support.serializer.JsonSerializer`. The project runs on Spring Boot 4 / Jackson 3 (`tools.jackson`) and has no `jackson-datatype-jsr310` on the classpath, so that Jackson-2 serializer throws `InvalidDefinitionException: Java 8 date/time type java.time.Instant not supported` — meaning `OrderCreatedEvent` (and the new result event) could not actually publish. This is precisely the untested-serializer gap (RESEARCH Pitfall 5 / assumption A2) the test was written to catch. The phase threat model forbids adding new dependencies, so adding the jsr310 module was not an option.
- **Fix:** Switched the order producer and the serde test to spring-kafka's Jackson-3 `JacksonJsonSerializer`/`JacksonJsonDeserializer` (already shipped in spring-kafka 4.0.5, native java.time support, no new dependency). The test now round-trips through the same serializer family the producer uses.
- **Files modified:** `OrderKafkaProducerConfig.java`, `EventSerdeRoundTripTest.java`
- **Verification:** `EventSerdeRoundTripTest` (3 tests) passes; full suite 122/122 green.
- **Committed in:** `469f39f` (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Necessary for correctness — the saga's entire Kafka wire path depends on serializable events. Establishes Jackson-3 serde as the phase-wide pattern (downstream consumer plans 15-04/15-06 must use `JacksonJsonDeserializer`). No scope creep.

## Issues Encountered
- Payment and Table producers use the same broken Jackson-2 `JsonSerializer` (latent `Instant` bug). Out of scope (pre-existing, other bounded contexts) — logged to `deferred-items.md` for a follow-up migration.

## Deferred Issues
See `deferred-items.md`: payment/table Kafka producers still on the Jackson-2 serializer and would fail to serialize their `Instant` fields at runtime.

## User Setup Required
None - no external service configuration required. New Kafka topic/group-id properties default via `${ENV:default}` and need no local setup.

## Next Phase Readiness
- Contract + config layer complete: status enum, shared result event, saga properties, and proven Jackson-3 serde are in place for the Wave-2/3 plans.
- Downstream consumers (15-04/15-06) must use `JacksonJsonDeserializer` with `com.example.feat1.*` trusted packages to match the producer serde.

---
*Phase: 15-kafka-event-consumers-for-ordercreated-and-payment-events-wi*
*Completed: 2026-07-07*
