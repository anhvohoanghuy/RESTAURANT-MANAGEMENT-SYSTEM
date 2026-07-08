---
phase: 16-inventory-reservation-settlement
plan: 05
subsystem: inventory
tags: [event-driven, kafka, jackson-3, dlt, poison-pill, thin-listener, spring-boot]

# Dependency graph
requires:
  - plan: 16-01
    provides: SettleTriggerEvent (eventId, eventType, occurredAt, orderId, orderLineId, totalLines)
  - plan: 16-04
    provides: InventoryReservationSettlementService.onSettleTrigger(SettleTriggerEvent)
provides:
  - SettleTriggerKafkaConsumerConfig (typed consumer factory + error handler + AckMode.RECORD container factory for SettleTriggerEvent)
  - settle-trigger + settle-trigger.DLT NewTopic beans (InventoryKafkaTopicConfig extension)
  - SettleTriggerListener (thin @KafkaListener delegating to the settlement service)
  - SettleTriggerEvent Jackson-3 serde round-trip coverage
affects: [17-settlement-producer]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "SettleTrigger consumer mirrors Phase 15 InventoryKafkaConsumerConfig bean-for-bean: ErrorHandlingDeserializer -> Jackson-3 JacksonJsonDeserializer, USE_TYPE_INFO_HEADERS=false + fixed VALUE_DEFAULT_TYPE + TRUSTED_PACKAGES allow-list (type-confusion defence)"
    - "@EnableKafka NOT re-declared on the new config — it already lives on InventoryKafkaConsumerConfig for the whole context (IN-03)"
    - "DLT KafkaTemplate reused across consumers via @Qualifier(\"inventoryDltKafkaTemplate\") rather than a second producer bean"
    - "DLT topic name derived from the live-topic @Value property + DLT_SUFFIX, never a divergent literal"
    - "Only DeserializationException non-retryable; missing reservation/order-line left retryable so a settle-before-reserve race self-heals via retry->DLT (T-16-14)"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/SettleTriggerKafkaConsumerConfig.java
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/adapter/SettleTriggerListener.java
    - src/test/java/com/example/feat1/DDD/inventory_context/infrastructure/config/SettleTriggerKafkaConsumerConfigTest.java
  modified:
    - src/main/java/com/example/feat1/DDD/inventory_context/infrastructure/config/InventoryKafkaTopicConfig.java
    - src/test/java/com/example/feat1/DDD/order_context/EventSerdeRoundTripTest.java

key-decisions:
  - "Locked discretionary property keys per plan/RESEARCH Open Q2 so the Phase 17 producer matches: topic kitchen.events.settle-trigger-topic (default kitchen.settlement-trigger), group inventory.settlement.consumer.group-id (default inventory-settlement), TRUSTED_PACKAGES=com.example.feat1.DDD.inventory_context.application.event, VALUE_DEFAULT_TYPE=SettleTriggerEvent"
  - "Config test injects the reused inventoryDltKafkaTemplate from a fresh InventoryKafkaConsumerConfig instance (broker-free) rather than mocking, matching the Phase 15 analog exactly"
  - "SettleTriggerEvent serde case added to the existing EventSerdeRoundTripTest (cross-context import) rather than a new test class, keeping all saga-contract round-trips in one place"

requirements-completed: [D-01, D-05]

# Metrics
duration: ~12min
completed: 2026-07-08
---

# Phase 16 Plan 05: Settlement Kafka Boundary Summary

**The Kafka inbound boundary for settlement: a Phase-15-identical hardened typed consumer factory / DLT error handler / AckMode.RECORD container factory for SettleTriggerEvent, the settle-trigger and .DLT topics derived from one property, a thin SettleTriggerListener delegating solely to InventoryReservationSettlementService.onSettleTrigger, and a Jackson-3 serde round-trip proving the record + Instant survives the wire (D-01/D-05).**

## Performance

- **Duration:** ~12 min
- **Completed:** 2026-07-08
- **Tasks:** 2
- **Files:** 5 (3 created, 2 modified)

## Accomplishments
- `SettleTriggerKafkaConsumerConfig` (`@Configuration`, no `@EnableKafka` per IN-03) with three distinctly-named beans mirroring the orderCreated triplet, typed for `SettleTriggerEvent`:
  - `settleTriggerConsumerFactory` — `ENABLE_AUTO_COMMIT=false`, `AUTO_OFFSET_RESET=earliest`, key `StringDeserializer`, value `ErrorHandlingDeserializer` wrapping the Jackson-3 `JacksonJsonDeserializer` with `USE_TYPE_INFO_HEADERS=false`, `VALUE_DEFAULT_TYPE=SettleTriggerEvent`, `TRUSTED_PACKAGES=com.example.feat1.DDD.inventory_context.application.event` (type-confusion mitigation T-16-12).
  - `settleTriggerErrorHandler` — `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` over the reused `@Qualifier("inventoryDltKafkaTemplate")`, `FixedBackOff(1000,3)`, only `DeserializationException` non-retryable (poison-pill T-16-13; missing-reservation left retryable T-16-14/D-05).
  - `settleTriggerKafkaListenerContainerFactory` — `AckMode.RECORD`.
- `InventoryKafkaTopicConfig` extended with a constructor-injected `settleTriggerTopic` `@Value` and two `NewTopic` beans (`settleTriggerTopic()`, `settleTriggerDltTopic()`), the DLT name built from the live-topic value + `DLT_SUFFIX`.
- `SettleTriggerListener` (`@Component`, `@RequiredArgsConstructor`): a single thin `@KafkaListener` delegating to `settlementService.onSettleTrigger(event)` with zero business logic, using the locked topic/group properties and `containerFactory = "settleTriggerKafkaListenerContainerFactory"`.
- Tests: `SettleTriggerKafkaConsumerConfigTest` (3, broker-free) asserting the hardening props and `DeserializationException` non-retryable classification; `EventSerdeRoundTripTest` gained a `SettleTriggerEvent` case (with a non-null `Instant`) — 4 total. Full suite 156 green (was 152 at Plan 04).

## Task Commits

Each task was committed atomically:

1. **Task 1: SettleTriggerKafkaConsumerConfig + settle-trigger/DLT topics + config test** — `70a8192` (feat)
2. **Task 2: SettleTriggerListener + serde round-trip case** — `eb43102` (feat)

## Files Created/Modified
- `.../infrastructure/config/SettleTriggerKafkaConsumerConfig.java` — typed consumer factory / DLT error handler / AckMode.RECORD container factory for SettleTriggerEvent (created).
- `.../infrastructure/config/InventoryKafkaTopicConfig.java` — settle-trigger + .DLT NewTopic beans from one @Value property (modified).
- `.../infrastructure/adapter/SettleTriggerListener.java` — thin delegating @KafkaListener (created).
- `.../infrastructure/config/SettleTriggerKafkaConsumerConfigTest.java` — broker-free wiring/hardening test (created).
- `.../order_context/EventSerdeRoundTripTest.java` — SettleTriggerEvent Jackson-3 round-trip case (modified).

## Decisions Made
- Discretionary property keys locked exactly as the plan/RESEARCH Open Q2 specified so the Phase 17 producer will match the consumer's topic, group, trusted packages, and default type.
- The config test constructs a real `InventoryKafkaConsumerConfig` only to obtain the reused `inventoryDltKafkaTemplate` bean, staying broker-free and mirror-faithful to the Phase 15 analog.

## Deviations from Plan
None — plan executed exactly as written. Spotless reformatted the two new main-source files on the first build (whitespace/line-wrap only, no logic change); this is the project's standard pre-commit formatting, not a deviation.

## Threat Surface Scan
No new trust-boundary surface beyond the plan's threat register. The single boundary (Kafka bytes -> deserialization) is mitigated exactly as planned:
- T-16-12 (type confusion): `USE_TYPE_INFO_HEADERS=false` + fixed `VALUE_DEFAULT_TYPE` + `TRUSTED_PACKAGES` allow-list.
- T-16-13 (poison pill): `ErrorHandlingDeserializer` -> non-retryable `DeserializationException` routed to the DLT.
- T-16-14 (partition block on missing reservation): `FixedBackOff(1000,3)` then DLT; missing reservation intentionally retryable.
- T-16-SC: no dependency added.

## Known Stubs
None. The Kafka boundary is fully wired; the settle-trigger producer is intentionally out of scope (Phase 17).

## Issues Encountered
None. Both target tests and the full `./mvnw test` suite (156 tests) passed green.

## User Setup Required
None — no external service configuration and no dependencies added.

## Next Phase Readiness
- The settlement service is now reachable over Kafka with Phase-15-equivalent hardening and DLT routing.
- Phase 17 can implement the settle-trigger producer against the locked property keys (topic `kitchen.events.settle-trigger-topic`, default `kitchen.settlement-trigger`) and the `SettleTriggerEvent` contract, confident the serde round-trips.
- No blockers.

## Self-Check: PASSED

---
*Phase: 16-inventory-reservation-settlement*
*Completed: 2026-07-08*
