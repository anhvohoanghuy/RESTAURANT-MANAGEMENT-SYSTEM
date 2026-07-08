---
phase: 17-kitchen-context
plan: 04
subsystem: infra
tags: [kafka, spring-kafka, kitchen-context, event-driven, jackson-3]

# Dependency graph
requires:
  - phase: 17-kitchen-context (plan 02)
    provides: KitchenItemStatus enum used in the per-item status snapshot
  - phase: 16-inventory-settlement
    provides: the existing inventory SettleTriggerEvent contract and kitchen.settlement-trigger topic default consumed by the Phase-16 settlement consumer
provides:
  - KitchenTicketStatusChangedEvent record (full per-item status snapshot)
  - KitchenSettleTriggerPublisher / KitchenTicketStatusChangedPublisher domain ports
  - KitchenSettleTriggerProducerConfig / KitchenTicketStatusChangedProducerConfig producer wiring
  - KafkaKitchenSettleTriggerPublisher / KafkaKitchenTicketStatusChangedPublisher adapters
affects: [17-05 (kitchen ticket advance service), order_context (status-changed consumer, future plan)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Cross-context event reuse: import an existing bounded context's event record rather than redeclaring it, to guarantee field/type parity with its consumer"
    - "Broker-free producer config test: instantiate @Configuration classes directly (no Spring context) and assert serializer/bean wiring"

key-files:
  created:
    - src/main/java/com/example/feat1/DDD/kitchen_context/application/event/KitchenTicketStatusChangedEvent.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/domain/port/KitchenSettleTriggerPublisher.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/domain/port/KitchenTicketStatusChangedPublisher.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenSettleTriggerProducerConfig.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenTicketStatusChangedProducerConfig.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/KafkaKitchenSettleTriggerPublisher.java
    - src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/KafkaKitchenTicketStatusChangedPublisher.java
    - src/test/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenKafkaProducerConfigTest.java
  modified: []

key-decisions:
  - "SettleTriggerEvent is imported directly from inventory_context.application.event, never redeclared in kitchen_context, guaranteeing field/type parity with the Phase-16 consumer (T-17-08)"
  - "KitchenTicketStatusChangedEvent carries the FULL per-item status snapshot (List<ItemStatus>) rather than a single-item delta, so order_context can derive aggregate status with no cross-context lookup"

patterns-established:
  - "Two-producer-per-context shape: separate ProducerFactory/KafkaTemplate bean pairs per event type, each with its own @Value topic-property default"
  - "Publisher port + Kafka adapter pair per outbound event, keeping the future advance service (17-05) decoupled from Kafka wiring"

requirements-completed: [D-03, D-04]

# Metrics
duration: 12min
completed: 2026-07-08
---

# Phase 17 Plan 04: Kitchen Outbound Kafka Producers Summary

**Two kitchen-context Kafka producers behind small ports: one republishing the existing inventory `SettleTriggerEvent` (imported, not cloned) to `kitchen.settlement-trigger`, one publishing a new `KitchenTicketStatusChangedEvent` full per-item snapshot to `kitchen.ticket-status-changed`.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-07-08T08:04:00Z
- **Completed:** 2026-07-08T08:16:31Z
- **Tasks:** 2/2 completed
- **Files modified:** 8 created

## Accomplishments
- `KitchenTicketStatusChangedEvent` record with nested `ItemStatus(orderLineId, status)` carrying the full per-item snapshot
- `KitchenSettleTriggerPublisher` port importing inventory's `SettleTriggerEvent` (no local copy)
- `KitchenTicketStatusChangedPublisher` port for the new status-changed event
- Two producer configs (`KitchenSettleTriggerProducerConfig`, `KitchenTicketStatusChangedProducerConfig`) each with a `ProducerFactory`/`KafkaTemplate` bean pair using `StringSerializer` key + `JacksonJsonSerializer` value
- Two adapters (`KafkaKitchenSettleTriggerPublisher`, `KafkaKitchenTicketStatusChangedPublisher`) each keying sends by `event.orderId().toString()` and reading their topic from a `@Value` property with the correct default
- Broker-free `KitchenKafkaProducerConfigTest` (4 tests) asserting serializer wiring and non-null KafkaTemplate beans for both configs

## Task Commits

Each task was committed atomically:

1. **Task 1: KitchenTicketStatusChangedEvent record + two publisher ports** - `56ad579` (feat)
2. **Task 2: Two producer configs + two adapters** - `5c5d92b` (feat)

**Plan metadata:** (final commit made by executor after this summary)

## Files Created/Modified
- `src/main/java/com/example/feat1/DDD/kitchen_context/application/event/KitchenTicketStatusChangedEvent.java` - full per-item status snapshot event record
- `src/main/java/com/example/feat1/DDD/kitchen_context/domain/port/KitchenSettleTriggerPublisher.java` - port for the imported inventory SettleTriggerEvent
- `src/main/java/com/example/feat1/DDD/kitchen_context/domain/port/KitchenTicketStatusChangedPublisher.java` - port for the new status-changed event
- `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenSettleTriggerProducerConfig.java` - producer bean pair for SettleTriggerEvent
- `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenTicketStatusChangedProducerConfig.java` - producer bean pair for KitchenTicketStatusChangedEvent
- `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/KafkaKitchenSettleTriggerPublisher.java` - adapter publishing to `kitchen.settlement-trigger` (property key `kitchen.events.settle-trigger-topic`)
- `src/main/java/com/example/feat1/DDD/kitchen_context/infrastructure/adapter/KafkaKitchenTicketStatusChangedPublisher.java` - adapter publishing to `kitchen.ticket-status-changed` (property key `kitchen.events.ticket-status-changed-topic`)
- `src/test/java/com/example/feat1/DDD/kitchen_context/infrastructure/config/KitchenKafkaProducerConfigTest.java` - broker-free config test, 4 test methods

## Decisions Made
- Imported `com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent` directly in both the port and producer config rather than redeclaring it, per the plan's locked contract requirement (T-17-08 mitigation).
- Used the plan's specified topic-property defaults verbatim: `${kitchen.events.settle-trigger-topic:kitchen.settlement-trigger}` (matches the Phase-16 consumer's default) and `${kitchen.events.ticket-status-changed-topic:kitchen.ticket-status-changed}`.
- No `NewTopic`/DLT beans were declared in this plan (that belongs to `KitchenKafkaTopicConfig`, out of this plan's scope per the file list) — avoided any risk of a duplicate `settleTriggerTopic()` bean colliding with `InventoryKafkaTopicConfig`.

## Deviations from Plan

None - plan executed exactly as written. Spotless (the project's formatting plugin) auto-reformatted two of the newly-written files during the Maven build (whitespace/line-wrap only, no semantic change) — this is expected tooling behavior, not a deviation.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required. Kafka bootstrap servers default to `localhost:9092` as already established project-wide.

## Next Phase Readiness
- Plan 17-05 (kitchen ticket advance service) can now inject `KitchenSettleTriggerPublisher` and `KitchenTicketStatusChangedPublisher` and call `publishAfterCommit` after each item advance, exactly as the pattern map's `KitchenTicketAdvanceService` section describes.
- No blockers. Full Maven test suite (161 tests) passes after this plan's changes.

---
*Phase: 17-kitchen-context*
*Completed: 2026-07-08*

## Self-Check: PASSED

All 8 created source/test files verified present on disk; all 3 commit hashes (56ad579, 5c5d92b, plus this file's own commit) verified present in git log.
